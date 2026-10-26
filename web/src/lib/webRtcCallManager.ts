import { getWebRtcIceServers, isSupabaseConfigured, supabase } from './supabase';
import {
  fetchProfileById,
  getOrCreateDirectConversation,
  sendDirectMessagePersisted
} from './vybeApi';
import type { ActiveCallSession, VybeUser } from '../types/vybe';

/* eslint-disable @typescript-eslint/no-explicit-any */

export interface CallStreams {
  localStream: MediaStream | null;
  remoteStream: MediaStream | null;
}

function formatDurationClock(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds));
  const mm = String(Math.floor(safe / 60)).padStart(2, '0');
  const ss = String(safe % 60).padStart(2, '0');
  return `${mm}:${ss}`;
}

export class BrowserWebRtcCallController {
  private pc: RTCPeerConnection | null = null;
  private localStream: MediaStream | null = null;
  private remoteStream: MediaStream | null = null;
  private signalChannel: ReturnType<typeof supabase.channel> | null = null;
  private durationTimer: number | null = null;
  private ringingTimeout: number | null = null;
  private pollInterval: number | null = null;
  private frameRelayInterval: number | null = null;
  private currentCall: ActiveCallSession | null = null;
  private currentVideoDeviceIndex = 0;
  private pendingIceCandidates: RTCIceCandidateInit[] = [];
  private processedSignalIds = new Set<string>();
  private loggedCallIds = new Set<string>();
  private captureVideoEl: HTMLVideoElement | null = null;
  private captureCanvasEl: HTMLCanvasElement | null = null;

  constructor(
    private readonly myUserId: string,
    private readonly onSessionChange: (session: ActiveCallSession | null) => void,
    private readonly onStreamsChange: (streams: CallStreams) => void
  ) {}

  private emit(patch?: Partial<ActiveCallSession>) {
    if (patch && this.currentCall) {
      this.currentCall = { ...this.currentCall, ...patch };
    }
    this.onSessionChange(this.currentCall ? { ...this.currentCall } : null);
  }

  private async logCallHistoryOnce(
    call: ActiveCallSession,
    finalStatus: 'COMPLETED' | 'MISSED' | 'DECLINED',
    shouldWriteToDb: boolean
  ): Promise<void> {
    if (!call.id || this.loggedCallIds.has(call.id)) return;
    this.loggedCallIds.add(call.id);
    if (!shouldWriteToDb || !isSupabaseConfigured) return;

    const kindLabel = call.isVideo ? 'Video call' : 'Voice call';
    const dur = Math.max(0, call.durationSeconds);
    const summary =
      finalStatus === 'COMPLETED' && dur > 0
        ? `${kindLabel} • ${formatDurationClock(dur)}`
        : finalStatus === 'DECLINED'
          ? `Declined ${kindLabel.toLowerCase()}`
          : finalStatus === 'MISSED'
            ? `Missed ${kindLabel.toLowerCase()}`
            : `${kindLabel} ended`;

    const metaUrl = `vybe://call-log?type=${call.isVideo ? 'video' : 'voice'}&status=${finalStatus}&duration=${dur}&callId=${call.id}`;

    try {
      const convId = await getOrCreateDirectConversation(this.myUserId, call.peerUser.id);
      const { data: existing } = await supabase
        .from('messages')
        .select('id')
        .eq('conversation_id', convId)
        .ilike('media_url', `%callId=${call.id}%`)
        .limit(1);
      if (existing && existing.length > 0) return;

      await sendDirectMessagePersisted({
        conversationId: convId,
        senderId: call.callerId || this.myUserId,
        text: summary,
        mediaUrl: metaUrl,
        mediaType: 'CALL_LOG'
      });
    } catch {
      // Non-fatal if conversation row cannot be written
    }
  }

  async startOutgoingCall(peerUser: VybeUser, isVideo: boolean): Promise<void> {
    if (!isSupabaseConfigured) {
      throw new Error('Configure Supabase credentials to place live calls.');
    }
    if (!navigator.mediaDevices?.getUserMedia) {
      throw new Error('Your browser does not support microphone/camera calling (HTTPS required).');
    }

    await this.cleanup();

    this.localStream = await navigator.mediaDevices.getUserMedia({
      audio: true,
      video: isVideo ? { facingMode: 'user', width: { ideal: 640 }, height: { ideal: 480 } } : false
    });
    this.remoteStream = new MediaStream();
    this.onStreamsChange({ localStream: this.localStream, remoteStream: this.remoteStream });

    this.pc = this.createPeerConnection('');
    this.localStream.getTracks().forEach((track) => {
      this.pc?.addTrack(track, this.localStream!);
    });

    const offer = await this.pc.createOffer({
      offerToReceiveAudio: true,
      offerToReceiveVideo: isVideo
    });
    await this.pc.setLocalDescription(offer);

    const { data: created, error } = await supabase
      .from('call_sessions')
      .insert({
        caller_id: this.myUserId,
        callee_id: peerUser.id,
        is_video: isVideo,
        status: 'RINGING',
        offer_sdp: JSON.stringify(offer)
      })
      .select('*')
      .single();

    if (error || !created?.id) {
      await this.cleanup();
      throw new Error(error?.message || 'Unable to create call session.');
    }

    const callId = String(created.id);
    this.currentCall = {
      id: callId,
      callerId: this.myUserId,
      calleeId: peerUser.id,
      peerUser,
      isVideo,
      state: 'OUTGOING',
      offerSdp: created.offer_sdp,
      durationSeconds: 0,
      isMuted: false,
      isCameraOff: false,
      isPeerMuted: false,
      isPeerCameraOff: false,
      remoteFrameDataUrl: null,
      statusText: 'Ringing…'
    };
    this.emit();

    this.bindIceAndSignaling(callId);

    this.ringingTimeout = window.setTimeout(async () => {
      if (this.currentCall?.id === callId && this.currentCall.state === 'OUTGOING') {
        const snap = { ...this.currentCall };
        await supabase
          .from('call_sessions')
          .update({ status: 'MISSED', ended_at: new Date().toISOString() })
          .eq('id', callId);
        await this.logCallHistoryOnce(snap, 'MISSED', true);
        this.emit({ state: 'MISSED', statusText: 'No answer' });
        window.setTimeout(() => void this.cleanup(), 1500);
      }
    }, 45_000);
  }

  async receiveIncomingCallRow(row: any): Promise<void> {
    if (this.currentCall) return;
    const callId = String(row.id);
    const callerId = String(row.caller_id);
    if (!callerId || callerId === this.myUserId) return;

    const callerProfile =
      (await fetchProfileById(callerId)) || {
        id: callerId,
        username: `user_${callerId.slice(0, 6)}`,
        displayName: 'VYBE Member',
        firstName: '',
        lastName: '',
        bio: '',
        avatarUrl: '',
        coverUrl: '',
        email: '',
        phone: '',
        dateOfBirth: '',
        isEmailVerified: false,
        isPhoneVerified: false,
        isVerified: false,
        isVip: false,
        isVerifiedBusiness: false,
        isPrivate: false,
        messagePrivacy: 'EVERYONE' as const,
        role: 'USER' as const,
        followersCount: 0,
        followingCount: 0,
        postsCount: 0,
        livePulse: 'NONE',
        createdAtIso: null,
        lastSeenAtIso: null,
        isOnline: true
      };

    this.currentCall = {
      id: callId,
      callerId,
      calleeId: this.myUserId,
      peerUser: callerProfile,
      isVideo: Boolean(row.is_video),
      state: 'INCOMING',
      offerSdp: row.offer_sdp || null,
      durationSeconds: 0,
      isMuted: false,
      isCameraOff: false,
      isPeerMuted: false,
      isPeerCameraOff: false,
      remoteFrameDataUrl: null,
      statusText: row.is_video ? 'Incoming Video Call…' : 'Incoming Voice Call…'
    };
    this.emit();
    this.subscribeCallUpdatesOnly(callId);
  }

  async acceptIncomingCall(): Promise<void> {
    if (!this.currentCall || this.currentCall.state !== 'INCOMING') return;
    const call = this.currentCall;
    this.emit({ state: 'CONNECTING', statusText: 'Connecting media…' });

    try {
      this.localStream = await navigator.mediaDevices.getUserMedia({
        audio: true,
        video: call.isVideo ? { facingMode: 'user', width: { ideal: 640 }, height: { ideal: 480 } } : false
      });
      this.remoteStream = new MediaStream();
      this.onStreamsChange({ localStream: this.localStream, remoteStream: this.remoteStream });

      this.pc = this.createPeerConnection(call.id);
      this.localStream.getTracks().forEach((t) => this.pc?.addTrack(t, this.localStream!));

      let answerPayload = '{"v":1,"accepted":true}';
      if (call.offerSdp && call.offerSdp.trim().startsWith('{')) {
        try {
          const parsedOffer = JSON.parse(call.offerSdp) as RTCSessionDescriptionInit;
          if (parsedOffer.type === 'offer' && parsedOffer.sdp) {
            await this.pc.setRemoteDescription(new RTCSessionDescription(parsedOffer));
            await this.flushPendingIceCandidates();
            const answer = await this.pc.createAnswer();
            await this.pc.setLocalDescription(answer);
            answerPayload = JSON.stringify(answer);
          }
        } catch {
          // Cross-platform non-SDP JSON offer (e.g. Android CallEngine); frame relay handles video
        }
      }

      await supabase
        .from('call_sessions')
        .update({
          status: 'CONNECTED',
          answer_sdp: answerPayload,
          started_at: new Date().toISOString()
        })
        .eq('id', call.id);

      await supabase.from('call_signals').insert({
        call_id: call.id,
        sender_id: this.myUserId,
        signal_type: 'ANSWER',
        payload: { accepted: 'true' }
      });

      this.bindIceAndSignaling(call.id);
      this.markConnected();
    } catch (err: any) {
      this.emit({
        state: 'FAILED',
        statusText: err?.message || 'Microphone/Camera permission denied.'
      });
      window.setTimeout(() => void this.cleanup(), 2200);
    }
  }

  async declineIncomingCall(): Promise<void> {
    if (!this.currentCall) return;
    const snap = { ...this.currentCall };
    await supabase.from('call_signals').insert({
      call_id: snap.id,
      sender_id: this.myUserId,
      signal_type: 'DECLINE',
      payload: {}
    });
    await supabase
      .from('call_sessions')
      .update({ status: 'DECLINED', ended_at: new Date().toISOString() })
      .eq('id', snap.id);
    await this.logCallHistoryOnce(snap, 'DECLINED', true);
    await this.cleanup();
  }

  async endActiveCall(): Promise<void> {
    if (!this.currentCall) return;
    const snap = { ...this.currentCall };
    const wasConnected = snap.state === 'CONNECTED' || snap.durationSeconds > 0;
    const nextDbStatus = wasConnected ? 'ENDED' : 'MISSED';
    await supabase.from('call_signals').insert({
      call_id: snap.id,
      sender_id: this.myUserId,
      signal_type: 'HANGUP',
      payload: { durationSeconds: String(snap.durationSeconds) }
    });
    await supabase
      .from('call_sessions')
      .update({ status: nextDbStatus, ended_at: new Date().toISOString() })
      .eq('id', snap.id);
    await this.logCallHistoryOnce(snap, wasConnected ? 'COMPLETED' : 'MISSED', true);
    await this.cleanup();
  }

  toggleMute(): void {
    if (!this.currentCall) return;
    const nextMuted = !this.currentCall.isMuted;
    this.localStream?.getAudioTracks().forEach((t) => {
      t.enabled = !nextMuted;
    });
    this.emit({ isMuted: nextMuted });
    void supabase.from('call_signals').insert({
      call_id: this.currentCall.id,
      sender_id: this.myUserId,
      signal_type: 'MUTE_STATE',
      payload: { muted: String(nextMuted) }
    });
  }

  toggleCamera(): void {
    if (!this.currentCall) return;
    const nextOff = !this.currentCall.isCameraOff;
    this.localStream?.getVideoTracks().forEach((t) => {
      t.enabled = !nextOff;
    });
    this.emit({ isCameraOff: nextOff });
    void supabase.from('call_signals').insert({
      call_id: this.currentCall.id,
      sender_id: this.myUserId,
      signal_type: 'CAMERA_STATE',
      payload: { cameraOff: String(nextOff) }
    });
  }

  async switchCameraDevice(): Promise<void> {
    if (!this.localStream || !this.currentCall?.isVideo) return;
    const devices = (await navigator.mediaDevices.enumerateDevices()).filter(
      (d) => d.kind === 'videoinput'
    );
    if (devices.length < 2) return;
    this.currentVideoDeviceIndex = (this.currentVideoDeviceIndex + 1) % devices.length;
    const nextDevice = devices[this.currentVideoDeviceIndex];
    const nextVideoStream = await navigator.mediaDevices.getUserMedia({
      video: { deviceId: { exact: nextDevice.deviceId } }
    });
    const newTrack = nextVideoStream.getVideoTracks()[0];
    if (!newTrack) return;

    const sender = this.pc?.getSenders().find((s) => s.track?.kind === 'video');
    if (sender) {
      await sender.replaceTrack(newTrack);
    }
    this.localStream.getVideoTracks().forEach((old) => {
      old.stop();
      this.localStream?.removeTrack(old);
    });
    this.localStream.addTrack(newTrack);
    if (this.captureVideoEl) {
      this.captureVideoEl.srcObject = this.localStream;
    }
    this.onStreamsChange({ localStream: this.localStream, remoteStream: this.remoteStream });
  }

  private createPeerConnection(callId: string): RTCPeerConnection {
    const pc = new RTCPeerConnection({ iceServers: getWebRtcIceServers() });
    pc.ontrack = (event) => {
      if (!this.remoteStream) {
        this.remoteStream = new MediaStream();
      }
      const incomingTracks =
        event.streams && event.streams[0] ? event.streams[0].getTracks() : [event.track];
      incomingTracks.forEach((track) => {
        if (track && !this.remoteStream!.getTracks().some((t) => t.id === track.id)) {
          this.remoteStream!.addTrack(track);
        }
      });
      this.onStreamsChange({ localStream: this.localStream, remoteStream: this.remoteStream });
    };
    pc.onconnectionstatechange = () => {
      if (pc.connectionState === 'connected') {
        this.markConnected();
      } else if (pc.connectionState === 'failed') {
        this.emit({ statusText: 'Maintaining live media relay…' });
      }
      void callId;
    };
    return pc;
  }

  private async flushPendingIceCandidates() {
    if (!this.pc || !this.pc.currentRemoteDescription) return;
    const queued = [...this.pendingIceCandidates];
    this.pendingIceCandidates = [];
    for (const cand of queued) {
      try {
        await this.pc.addIceCandidate(new RTCIceCandidate(cand));
      } catch {
        // Ignore duplicate/stale candidate
      }
    }
  }

  private markConnected() {
    if (!this.currentCall) return;
    if (this.ringingTimeout) {
      window.clearTimeout(this.ringingTimeout);
      this.ringingTimeout = null;
    }
    if (this.currentCall.state !== 'CONNECTED') {
      this.emit({
        state: 'CONNECTED',
        statusText: this.currentCall.isVideo ? 'HD Video Connected' : 'Voice Call Connected'
      });
    }
    if (!this.durationTimer) {
      this.durationTimer = window.setInterval(() => {
        if (this.currentCall?.state === 'CONNECTED') {
          this.emit({ durationSeconds: this.currentCall.durationSeconds + 1 });
        }
      }, 1000);
    }
    if (this.currentCall.isVideo && !this.frameRelayInterval) {
      this.startCrossPlatformVideoFrameRelay(this.currentCall.id);
    }
  }

  private startCrossPlatformVideoFrameRelay(callId: string) {
    if (this.frameRelayInterval) {
      window.clearInterval(this.frameRelayInterval);
    }
    if (this.localStream) {
      const video = document.createElement('video');
      video.muted = true;
      video.playsInline = true;
      video.autoplay = true;
      video.srcObject = this.localStream;
      void video.play().catch(() => {});
      this.captureVideoEl = video;
      this.captureCanvasEl = document.createElement('canvas');
    }

    this.frameRelayInterval = window.setInterval(async () => {
      if (
        !this.currentCall ||
        this.currentCall.id !== callId ||
        this.currentCall.state !== 'CONNECTED' ||
        !this.currentCall.isVideo ||
        this.currentCall.isCameraOff ||
        !this.captureVideoEl ||
        !this.captureCanvasEl
      ) {
        return;
      }
      const vw = this.captureVideoEl.videoWidth;
      const vh = this.captureVideoEl.videoHeight;
      if (!vw || !vh) return;

      const scale = Math.min(1, 240 / Math.max(vw, vh));
      const tw = Math.max(1, Math.round(vw * scale));
      const th = Math.max(1, Math.round(vh * scale));
      this.captureCanvasEl.width = tw;
      this.captureCanvasEl.height = th;
      const ctx = this.captureCanvasEl.getContext('2d');
      if (!ctx) return;
      ctx.drawImage(this.captureVideoEl, 0, 0, tw, th);
      const dataUrl = this.captureCanvasEl.toDataURL('image/jpeg', 0.45);
      const rawBase64 = dataUrl.includes('base64,') ? dataUrl.split('base64,')[1] : dataUrl;
      if (!rawBase64 || rawBase64.length > 90_000) return;

      try {
        await supabase
          .from('call_signals')
          .delete()
          .eq('call_id', callId)
          .eq('sender_id', this.myUserId)
          .eq('signal_type', 'VIDEO_FRAME');
        await supabase.from('call_signals').insert({
          call_id: callId,
          sender_id: this.myUserId,
          signal_type: 'VIDEO_FRAME',
          payload: { frame: rawBase64 }
        });
      } catch {
        // Non-blocking frame relay
      }
    }, 850);
  }

  private async handleRemoteSignalRow(sig: any) {
    if (!sig || !this.currentCall) return;
    if (String(sig.sender_id) === this.myUserId) return;
    const sigId = String(sig.id || '');
    const type = String(sig.signal_type || '');

    if (type !== 'VIDEO_FRAME' && sigId) {
      if (this.processedSignalIds.has(sigId)) return;
      this.processedSignalIds.add(sigId);
    }

    if (type === 'ICE_CANDIDATE' && sig.payload?.candidate && this.pc) {
      try {
        const cand = JSON.parse(sig.payload.candidate) as RTCIceCandidateInit;
        if (this.pc.currentRemoteDescription) {
          await this.pc.addIceCandidate(new RTCIceCandidate(cand));
        } else {
          this.pendingIceCandidates.push(cand);
        }
      } catch {
        // Ignore duplicate candidate
      }
    } else if (type === 'ANSWER') {
      this.markConnected();
    } else if (type === 'VIDEO_FRAME' && sig.payload?.frame) {
      const f = String(sig.payload.frame);
      const dataUrl = f.startsWith('data:image') ? f : `data:image/jpeg;base64,${f}`;
      this.emit({ remoteFrameDataUrl: dataUrl, isPeerCameraOff: false });
      this.markConnected();
    } else if (type === 'MUTE_STATE') {
      this.emit({ isPeerMuted: String(sig.payload?.muted) === 'true' });
    } else if (type === 'CAMERA_STATE') {
      this.emit({ isPeerCameraOff: String(sig.payload?.cameraOff) === 'true' });
    } else if (type === 'DECLINE') {
      const snap = { ...this.currentCall };
      await this.logCallHistoryOnce(snap, 'DECLINED', snap.callerId === this.myUserId);
      this.emit({ state: 'DECLINED', statusText: 'Call declined' });
      window.setTimeout(() => void this.cleanup(), 1000);
    } else if (type === 'HANGUP') {
      const snap = { ...this.currentCall };
      const wasConnected = snap.state === 'CONNECTED' || snap.durationSeconds > 0;
      await this.logCallHistoryOnce(
        snap,
        wasConnected ? 'COMPLETED' : 'MISSED',
        snap.callerId === this.myUserId
      );
      this.emit({ state: 'ENDED', statusText: 'Call ended' });
      window.setTimeout(() => void this.cleanup(), 900);
    }
  }

  private async applySessionRowUpdate(row: any, callId: string) {
    if (!row || !this.currentCall || this.currentCall.id !== callId) return;
    if (row.status === 'CONNECTED') {
      if (row.answer_sdp && this.pc && !this.pc.currentRemoteDescription) {
        try {
          const ans = JSON.parse(row.answer_sdp) as RTCSessionDescriptionInit;
          if (ans.type === 'answer' && ans.sdp) {
            await this.pc.setRemoteDescription(new RTCSessionDescription(ans));
            await this.flushPendingIceCandidates();
          }
        } catch {
          // Non-WebRTC answer payload (e.g. Android CallEngine)
        }
      }
      this.markConnected();
    } else if (row.status === 'DECLINED' || row.status === 'ENDED' || row.status === 'MISSED') {
      const snap = { ...this.currentCall };
      const finalKind =
        row.status === 'DECLINED'
          ? 'DECLINED'
          : snap.state === 'CONNECTED' || snap.durationSeconds > 0
            ? 'COMPLETED'
            : 'MISSED';
      await this.logCallHistoryOnce(snap, finalKind, snap.callerId === this.myUserId);
      this.emit({ state: row.status, statusText: `Call ${String(row.status).toLowerCase()}` });
      window.setTimeout(() => void this.cleanup(), 1100);
    }
  }

  private startCallPollingFallback(callId: string) {
    if (this.pollInterval) {
      window.clearInterval(this.pollInterval);
    }
    this.pollInterval = window.setInterval(async () => {
      if (!this.currentCall || this.currentCall.id !== callId) return;
      try {
        const [{ data: sessRow }, { data: sigRows }] = await Promise.all([
          supabase.from('call_sessions').select('*').eq('id', callId).maybeSingle(),
          supabase
            .from('call_signals')
            .select('*')
            .eq('call_id', callId)
            .order('created_at', { ascending: true })
            .limit(40)
        ]);
        if (sessRow) {
          await this.applySessionRowUpdate(sessRow, callId);
        }
        if (Array.isArray(sigRows)) {
          for (const sig of sigRows) {
            await this.handleRemoteSignalRow(sig);
          }
        }
      } catch {
        // Ignore transient network poll error
      }
    }, 850);
  }

  private subscribeCallUpdatesOnly(callId: string) {
    if (this.signalChannel) {
      void supabase.removeChannel(this.signalChannel);
    }
    this.signalChannel = supabase
      .channel(`web_call_${callId}`)
      .on(
        'postgres_changes',
        { event: 'UPDATE', schema: 'public', table: 'call_sessions', filter: `id=eq.${callId}` },
        async (payload) => {
          await this.applySessionRowUpdate(payload.new, callId);
        }
      )
      .subscribe();
    this.startCallPollingFallback(callId);
  }

  private bindIceAndSignaling(callId: string) {
    if (this.pc) {
      this.pc.onicecandidate = (ev) => {
        if (ev.candidate) {
          void supabase.from('call_signals').insert({
            call_id: callId,
            sender_id: this.myUserId,
            signal_type: 'ICE_CANDIDATE',
            payload: { candidate: JSON.stringify(ev.candidate.toJSON()) }
          });
        }
      };
    }

    if (this.signalChannel) {
      void supabase.removeChannel(this.signalChannel);
    }

    this.signalChannel = supabase
      .channel(`web_call_active_${callId}`)
      .on(
        'postgres_changes',
        { event: 'UPDATE', schema: 'public', table: 'call_sessions', filter: `id=eq.${callId}` },
        async (payload) => {
          await this.applySessionRowUpdate(payload.new, callId);
        }
      )
      .on(
        'postgres_changes',
        { event: 'INSERT', schema: 'public', table: 'call_signals', filter: `call_id=eq.${callId}` },
        async (payload) => {
          await this.handleRemoteSignalRow(payload.new);
        }
      )
      .subscribe();

    this.startCallPollingFallback(callId);
  }

  async cleanup(): Promise<void> {
    if (this.durationTimer) {
      window.clearInterval(this.durationTimer);
      this.durationTimer = null;
    }
    if (this.ringingTimeout) {
      window.clearTimeout(this.ringingTimeout);
      this.ringingTimeout = null;
    }
    if (this.pollInterval) {
      window.clearInterval(this.pollInterval);
      this.pollInterval = null;
    }
    if (this.frameRelayInterval) {
      window.clearInterval(this.frameRelayInterval);
      this.frameRelayInterval = null;
    }
    if (this.captureVideoEl) {
      this.captureVideoEl.srcObject = null;
      this.captureVideoEl = null;
    }
    this.captureCanvasEl = null;
    this.pendingIceCandidates = [];
    this.processedSignalIds.clear();

    if (this.signalChannel) {
      await supabase.removeChannel(this.signalChannel);
      this.signalChannel = null;
    }
    if (this.pc) {
      try {
        this.pc.close();
      } catch {
        // Ignore
      }
      this.pc = null;
    }
    if (this.localStream) {
      this.localStream.getTracks().forEach((t) => t.stop());
      this.localStream = null;
    }
    this.remoteStream = null;
    this.currentCall = null;
    this.onStreamsChange({ localStream: null, remoteStream: null });
    this.onSessionChange(null);
  }
}
