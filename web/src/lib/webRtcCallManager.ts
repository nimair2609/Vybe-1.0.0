import { getWebRtcIceServers, isSupabaseConfigured, supabase } from './supabase';
import { fetchProfileById } from './vybeApi';
import type { ActiveCallSession, VybeUser } from '../types/vybe';

/* eslint-disable @typescript-eslint/no-explicit-any */

export interface CallStreams {
  localStream: MediaStream | null;
  remoteStream: MediaStream | null;
}

export class BrowserWebRtcCallController {
  private pc: RTCPeerConnection | null = null;
  private localStream: MediaStream | null = null;
  private remoteStream: MediaStream | null = null;
  private signalChannel: ReturnType<typeof supabase.channel> | null = null;
  private durationTimer: number | null = null;
  private ringingTimeout: number | null = null;
  private currentCall: ActiveCallSession | null = null;
  private currentVideoDeviceIndex = 0;

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
      video: isVideo ? { facingMode: 'user', width: { ideal: 1280 }, height: { ideal: 720 } } : false
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
      statusText: 'Ringing…'
    };
    this.emit();

    this.bindIceAndSignaling(callId);

    this.ringingTimeout = window.setTimeout(async () => {
      if (this.currentCall?.id === callId && this.currentCall.state === 'OUTGOING') {
        await supabase
          .from('call_sessions')
          .update({ status: 'MISSED', ended_at: new Date().toISOString() })
          .eq('id', callId);
        this.emit({ state: 'MISSED', statusText: 'No answer' });
        window.setTimeout(() => void this.cleanup(), 1800);
      }
    }, 45_000);
  }

  async receiveIncomingCallRow(row: any): Promise<void> {
    if (this.currentCall) return;
    const callId = String(row.id);
    const callerId = String(row.caller_id);
    const callerProfile =
      (await fetchProfileById(callerId)) || {
        id: callerId,
        username: 'vyber',
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
        video: call.isVideo ? { facingMode: 'user', width: { ideal: 1280 }, height: { ideal: 720 } } : false
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
            const answer = await this.pc.createAnswer();
            await this.pc.setLocalDescription(answer);
            answerPayload = JSON.stringify(answer);
          }
        } catch {
          // Android custom SDP compatibility fallback
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
    const callId = this.currentCall.id;
    await supabase
      .from('call_sessions')
      .update({ status: 'DECLINED', ended_at: new Date().toISOString() })
      .eq('id', callId);
    await this.cleanup();
  }

  async endActiveCall(): Promise<void> {
    if (!this.currentCall) return;
    const callId = this.currentCall.id;
    await supabase.from('call_signals').insert({
      call_id: callId,
      sender_id: this.myUserId,
      signal_type: 'HANGUP',
      payload: {}
    });
    await supabase
      .from('call_sessions')
      .update({ status: 'ENDED', ended_at: new Date().toISOString() })
      .eq('id', callId);
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
    this.onStreamsChange({ localStream: this.localStream, remoteStream: this.remoteStream });
  }

  private createPeerConnection(callId: string): RTCPeerConnection {
    const pc = new RTCPeerConnection({ iceServers: getWebRtcIceServers() });
    pc.ontrack = (event) => {
      event.streams[0]?.getTracks().forEach((track) => {
        this.remoteStream?.addTrack(track);
      });
      this.onStreamsChange({ localStream: this.localStream, remoteStream: this.remoteStream });
    };
    pc.onconnectionstatechange = () => {
      if (pc.connectionState === 'connected') {
        this.markConnected();
      } else if (pc.connectionState === 'failed') {
        this.emit({ statusText: 'Reconnecting media…' });
      }
      void callId;
    };
    return pc;
  }

  private markConnected() {
    if (!this.currentCall) return;
    if (this.ringingTimeout) {
      window.clearTimeout(this.ringingTimeout);
      this.ringingTimeout = null;
    }
    if (this.currentCall.state !== 'CONNECTED') {
      this.emit({ state: 'CONNECTED', statusText: 'Encrypted WebRTC audio/video connected' });
    }
    if (!this.durationTimer) {
      this.durationTimer = window.setInterval(() => {
        if (this.currentCall?.state === 'CONNECTED') {
          this.emit({ durationSeconds: this.currentCall.durationSeconds + 1 });
        }
      }, 1000);
    }
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
          const row: any = payload.new;
          if (!this.currentCall || this.currentCall.id !== callId) return;
          if (row.status === 'DECLINED' || row.status === 'ENDED' || row.status === 'MISSED') {
            this.emit({ state: row.status, statusText: `Call ${String(row.status).toLowerCase()}` });
            window.setTimeout(() => void this.cleanup(), 1200);
          }
        }
      )
      .subscribe();
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
          const row: any = payload.new;
          if (!this.currentCall || this.currentCall.id !== callId) return;

          if (row.status === 'CONNECTED') {
            if (row.answer_sdp && this.pc && !this.pc.currentRemoteDescription) {
              try {
                const ans = JSON.parse(row.answer_sdp) as RTCSessionDescriptionInit;
                if (ans.type === 'answer' && ans.sdp) {
                  await this.pc.setRemoteDescription(new RTCSessionDescription(ans));
                }
              } catch {
                // Non-WebRTC answer format
              }
            }
            this.markConnected();
          } else if (row.status === 'DECLINED' || row.status === 'ENDED' || row.status === 'MISSED') {
            this.emit({ state: row.status, statusText: `Call ${String(row.status).toLowerCase()}` });
            window.setTimeout(() => void this.cleanup(), 1200);
          }
        }
      )
      .on(
        'postgres_changes',
        { event: 'INSERT', schema: 'public', table: 'call_signals', filter: `call_id=eq.${callId}` },
        async (payload) => {
          const sig: any = payload.new;
          if (!sig || String(sig.sender_id) === this.myUserId) return;
          const type = String(sig.signal_type || '');
          if (type === 'ICE_CANDIDATE' && sig.payload?.candidate && this.pc) {
            try {
              const cand = JSON.parse(sig.payload.candidate) as RTCIceCandidateInit;
              await this.pc.addIceCandidate(new RTCIceCandidate(cand));
            } catch {
              // Ignore duplicate/late candidate
            }
          } else if (type === 'MUTE_STATE') {
            this.emit({ isPeerMuted: sig.payload?.muted === 'true' });
          } else if (type === 'CAMERA_STATE') {
            this.emit({ isPeerCameraOff: sig.payload?.cameraOff === 'true' });
          } else if (type === 'HANGUP') {
            this.emit({ state: 'ENDED', statusText: 'Call ended' });
            window.setTimeout(() => void this.cleanup(), 900);
          }
        }
      )
      .subscribe();
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
