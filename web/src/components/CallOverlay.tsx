import React, { useEffect, useRef, useState } from 'react';
import {
  Phone,
  PhoneOff,
  Video,
  VideoOff,
  Mic,
  MicOff,
  RefreshCcw
} from 'lucide-react';
import { VybeAvatar, VerifiedBadge } from './VybeCoreComponents';
import type { ActiveCallSession } from '../types/vybe';
import type { CallStreams } from '../lib/webRtcCallManager';

export const CallOverlay: React.FC<{
  call: ActiveCallSession | null;
  streams: CallStreams;
  onAccept: () => void;
  onDecline: () => void;
  onEnd: () => void;
  onToggleMute: () => void;
  onToggleCamera: () => void;
  onSwitchCamera: () => void;
}> = ({
  call,
  streams,
  onAccept,
  onDecline,
  onEnd,
  onToggleMute,
  onToggleCamera,
  onSwitchCamera
}) => {
  const localVideoRef = useRef<HTMLVideoElement | null>(null);
  const remoteVideoRef = useRef<HTMLVideoElement | null>(null);
  const remoteAudioRef = useRef<HTMLAudioElement | null>(null);
  const [hasLiveWebRtcVideo, setHasLiveWebRtcVideo] = useState(false);

  const remoteTrackCount = streams.remoteStream?.getVideoTracks().length ?? 0;
  const localTrackCount = streams.localStream?.getVideoTracks().length ?? 0;

  useEffect(() => {
    if (localVideoRef.current && streams.localStream) {
      if (localVideoRef.current.srcObject !== streams.localStream) {
        localVideoRef.current.srcObject = streams.localStream;
      }
      void localVideoRef.current.play().catch(() => {});
    }
    if (remoteVideoRef.current && streams.remoteStream) {
      if (remoteVideoRef.current.srcObject !== streams.remoteStream) {
        remoteVideoRef.current.srcObject = streams.remoteStream;
      }
      void remoteVideoRef.current.play().catch(() => {});
      setHasLiveWebRtcVideo(remoteTrackCount > 0);
    } else {
      setHasLiveWebRtcVideo(false);
    }
    if (remoteAudioRef.current && streams.remoteStream) {
      if (remoteAudioRef.current.srcObject !== streams.remoteStream) {
        remoteAudioRef.current.srcObject = streams.remoteStream;
      }
      void remoteAudioRef.current.play().catch(() => {});
    }
  }, [streams, remoteTrackCount, localTrackCount, call?.state]);

  if (!call) return null;

  const mm = String(Math.floor(call.durationSeconds / 60)).padStart(2, '0');
  const ss = String(call.durationSeconds % 60).padStart(2, '0');
  const hasAnyRemoteVideo =
    call.isVideo &&
    !call.isPeerCameraOff &&
    (hasLiveWebRtcVideo || Boolean(call.remoteFrameDataUrl));

  // Compact Incoming Call Banner at top of viewport
  if (call.state === 'INCOMING') {
    return (
      <div
        style={{
          position: 'fixed',
          top: 16,
          left: '50%',
          transform: 'translateX(-50%)',
          width: 'min(94vw, 440px)',
          zIndex: 100,
          background: 'rgba(18, 19, 28, 0.98)',
          border: '1.5px solid var(--vybe-cyan)',
          borderRadius: 20,
          padding: 16,
          boxShadow: '0 18px 42px rgba(0, 0, 0, 0.65)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 12
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <VybeAvatar user={call.peerUser} size={46} />
          <div>
            <div style={{ fontWeight: 700, display: 'flex', alignItems: 'center' }}>
              {call.peerUser.displayName}
              <VerifiedBadge user={call.peerUser} />
            </div>
            <div style={{ fontSize: '0.8rem', color: 'var(--vybe-cyan)' }}>
              Incoming {call.isVideo ? 'Video' : 'Voice'} Call…
            </div>
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button
            type="button"
            className="vybe-btn"
            style={{ background: '#FF4D6D', color: '#fff', padding: '10px 14px' }}
            onClick={onDecline}
            aria-label="Decline call"
          >
            <PhoneOff size={18} />
          </button>
          <button
            type="button"
            className="vybe-btn"
            style={{ background: '#00E676', color: '#09090E', padding: '10px 14px' }}
            onClick={onAccept}
            aria-label="Answer call"
          >
            {call.isVideo ? <Video size={18} /> : <Phone size={18} />}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 110,
        background: 'radial-gradient(circle at top, #1b1635 0%, #09090e 70%)',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'space-between',
        padding: 24
      }}
    >
      <audio ref={remoteAudioRef} autoPlay playsInline />

      {call.isVideo && !call.isPeerCameraOff && (
        <>
          <video
            ref={remoteVideoRef}
            autoPlay
            playsInline
            onLoadedMetadata={() => setHasLiveWebRtcVideo(true)}
            style={{
              position: 'absolute',
              inset: 0,
              width: '100%',
              height: '100%',
              objectFit: 'cover',
              zIndex: 0,
              display: hasLiveWebRtcVideo ? 'block' : 'none'
            }}
          />
          {!hasLiveWebRtcVideo && call.remoteFrameDataUrl && (
            <img
              src={call.remoteFrameDataUrl}
              alt={`${call.peerUser.displayName} live video`}
              style={{
                position: 'absolute',
                inset: 0,
                width: '100%',
                height: '100%',
                objectFit: 'cover',
                zIndex: 0
              }}
            />
          )}
        </>
      )}

      {call.isVideo && !call.isCameraOff && streams.localStream && (
        <video
          ref={localVideoRef}
          autoPlay
          muted
          playsInline
          style={{
            position: 'absolute',
            top: 24,
            right: 24,
            width: 132,
            height: 184,
            objectFit: 'cover',
            borderRadius: 16,
            border: '2px solid var(--vybe-cyan)',
            background: '#12131c',
            boxShadow: '0 10px 28px rgba(0,0,0,0.55)',
            zIndex: 3
          }}
        />
      )}

      <div style={{ position: 'relative', zIndex: 2, textAlign: 'center' }}>
        <span
          className="vybe-badge"
          style={{
            background: 'rgba(18, 19, 28, 0.78)',
            border: '1px solid rgba(124, 77, 255, 0.45)',
            color: '#fff',
            padding: '6px 14px'
          }}
        >
          {call.isVideo ? 'VYBE HD Video Call' : 'VYBE Voice Call'}
        </span>
        <div
          style={{
            marginTop: 10,
            fontSize: '1.1rem',
            fontWeight: 800,
            color: call.state === 'CONNECTED' ? '#00E676' : '#fff',
            textShadow: '0 2px 10px rgba(0,0,0,0.7)'
          }}
        >
          {call.state === 'CONNECTED' ? `${mm}:${ss}` : call.statusText}
        </div>
        {hasAnyRemoteVideo && (
          <div style={{ marginTop: 4, fontSize: '0.95rem', fontWeight: 700, textShadow: '0 2px 8px rgba(0,0,0,0.8)' }}>
            {call.peerUser.displayName} (@{call.peerUser.username})
          </div>
        )}
        {(call.isPeerMuted || call.isPeerCameraOff) && (
          <div style={{ marginTop: 6, fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
            {call.isPeerMuted ? `${call.peerUser.displayName} muted mic` : ''}{' '}
            {call.isPeerCameraOff ? '• Camera paused' : ''}
          </div>
        )}
      </div>

      {!hasAnyRemoteVideo && (
        <div
          style={{
            position: 'relative',
            zIndex: 2,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 12
          }}
        >
          <VybeAvatar user={call.peerUser} size={108} />
          <div style={{ fontSize: '1.3rem', fontWeight: 800, display: 'flex', alignItems: 'center' }}>
            {call.peerUser.displayName}
            <VerifiedBadge user={call.peerUser} />
          </div>
          <div style={{ color: 'var(--text-secondary)', fontSize: '0.9rem' }}>
            @{call.peerUser.username}
          </div>
        </div>
      )}

      <div
        style={{
          position: 'relative',
          zIndex: 2,
          display: 'flex',
          justifyContent: 'center',
          gap: 16,
          paddingBottom: 16
        }}
      >
        <button
          type="button"
          className="vybe-btn"
          style={{
            width: 54,
            height: 54,
            borderRadius: '50%',
            background: call.isMuted ? '#FF4D6D' : 'rgba(18,19,28,0.75)'
          }}
          onClick={onToggleMute}
          aria-label="Toggle microphone"
        >
          {call.isMuted ? <MicOff size={22} /> : <Mic size={22} />}
        </button>

        {call.isVideo && (
          <>
            <button
              type="button"
              className="vybe-btn"
              style={{
                width: 54,
                height: 54,
                borderRadius: '50%',
                background: call.isCameraOff ? '#FF4D6D' : 'rgba(18,19,28,0.75)'
              }}
              onClick={onToggleCamera}
              aria-label="Toggle camera"
            >
              {call.isCameraOff ? <VideoOff size={22} /> : <Video size={22} />}
            </button>
            <button
              type="button"
              className="vybe-btn"
              style={{ width: 54, height: 54, borderRadius: '50%', background: 'rgba(18,19,28,0.75)' }}
              onClick={onSwitchCamera}
              aria-label="Switch camera"
            >
              <RefreshCcw size={20} />
            </button>
          </>
        )}

        <button
          type="button"
          className="vybe-btn"
          style={{ width: 62, height: 62, borderRadius: '50%', background: '#FF4D6D', color: '#fff' }}
          onClick={onEnd}
          aria-label="End call"
        >
          <PhoneOff size={24} />
        </button>
      </div>
    </div>
  );
};
