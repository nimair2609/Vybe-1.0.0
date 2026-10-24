import React, { useEffect, useRef, useState } from 'react';
import { Volume2, VolumeX, Play, Pause, Check, Download, RefreshCw } from 'lucide-react';
import { formatRelativeTimestamp } from '../lib/utils';
import type { PwaUpdateState } from '../lib/pwaUpdater';
import type { VybeUser } from '../types/vybe';

/**
 * Custom Non-Heart VYBE Resonance "Pulse-V Spark" SVG Icon
 * Mirrors Android's `VybeLikeIcon.kt` (strictly non-heart VYBE visual language).
 */
export const VybeResonanceIcon: React.FC<{ active: boolean; size?: number }> = ({
  active,
  size = 22
}) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 28 28"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    style={{
      transform: active ? 'scale(1.12)' : 'scale(1)',
      transition: 'transform 0.18s cubic-bezier(0.34, 1.56, 0.64, 1)'
    }}
    aria-hidden="true"
  >
    <defs>
      <linearGradient id="vybePulseGrad" x1="2" y1="2" x2="26" y2="26" gradientUnits="userSpaceOnUse">
        <stop offset="0%" stopColor="#7C4DFF" />
        <stop offset="52%" stopColor="#FF2E93" />
        <stop offset="100%" stopColor="#00E5FF" />
      </linearGradient>
    </defs>
    {active && (
      <circle cx="14" cy="14" r="12.5" stroke="url(#vybePulseGrad)" strokeWidth="1.4" opacity="0.55" />
    )}
    <path
      d="M5.5 7.5L11.2 20.5L14 13.8L16.8 20.5L22.5 7.5"
      stroke={active ? 'url(#vybePulseGrad)' : 'currentColor'}
      strokeWidth={active ? '2.6' : '2.1'}
      strokeLinecap="round"
      strokeLinejoin="round"
    />
    <path
      d="M14 3.2L15.1 6.2L18.1 7.3L15.1 8.4L14 11.4L12.9 8.4L9.9 7.3L12.9 6.2L14 3.2Z"
      fill={active ? '#00E5FF' : 'none'}
      stroke={active ? '#00E5FF' : 'currentColor'}
      strokeWidth="1.3"
    />
  </svg>
);

export const VybeAvatar: React.FC<{
  user: Pick<VybeUser, 'avatarUrl' | 'displayName' | 'username' | 'isOnline' | 'isVerified' | 'isVip'>;
  size?: number;
  ringMode?: 'none' | 'unviewed' | 'viewed';
  onClick?: () => void;
}> = ({ user, size = 42, ringMode = 'none', onClick }) => {
  const initials = (user.displayName || user.username || 'V')
    .trim()
    .slice(0, 2)
    .toUpperCase();

  const inner = (
    <div
      onClick={onClick}
      style={{
        width: size,
        height: size,
        borderRadius: '50%',
        position: 'relative',
        cursor: onClick ? 'pointer' : 'default',
        flexShrink: 0
      }}
    >
      {user.avatarUrl ? (
        <img
          src={user.avatarUrl}
          alt={user.displayName}
          style={{
            width: '100%',
            height: '100%',
            borderRadius: '50%',
            objectFit: 'cover',
            display: 'block',
            background: '#191b28'
          }}
        />
      ) : (
        <div
          style={{
            width: '100%',
            height: '100%',
            borderRadius: '50%',
            background: 'linear-gradient(135deg, #7C4DFF, #FF2E93)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontWeight: 800,
            fontSize: Math.max(11, Math.floor(size * 0.36)),
            color: '#fff'
          }}
        >
          {initials}
        </div>
      )}
      {user.isOnline && (
        <span
          title="Online"
          style={{
            position: 'absolute',
            right: 0,
            bottom: 0,
            width: Math.max(9, Math.floor(size * 0.24)),
            height: Math.max(9, Math.floor(size * 0.24)),
            borderRadius: '50%',
            background: '#00E676',
            border: '2px solid #09090E'
          }}
        />
      )}
    </div>
  );

  if (ringMode === 'unviewed') {
    return <div className="story-ring-unviewed">{inner}</div>;
  }
  if (ringMode === 'viewed') {
    return <div className="story-ring-viewed">{inner}</div>;
  }
  return inner;
};

export const VerifiedBadge: React.FC<{ user: Pick<VybeUser, 'isVerified' | 'isVip'> }> = ({
  user
}) => {
  if (!user.isVerified && !user.isVip) return null;
  return (
    <span
      title={user.isVip ? 'VYBE VIP' : 'Verified on VYBE'}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: 16,
        height: 16,
        borderRadius: '50%',
        background: user.isVip ? '#FFD54F' : '#00E5FF',
        color: '#09090E',
        fontSize: 10,
        fontWeight: 900,
        marginLeft: 4
      }}
    >
      <Check size={11} strokeWidth={3} />
    </span>
  );
};

export const RelativeTimeText: React.FC<{ iso?: string | null; fallback?: string }> = ({
  iso,
  fallback = ''
}) => {
  const [tick, setTick] = useState(() => Date.now());

  useEffect(() => {
    if (!iso) return;
    const id = window.setInterval(() => setTick(Date.now()), 30_000);
    return () => window.clearInterval(id);
  }, [iso]);

  const text = iso ? formatRelativeTimestamp(iso, tick) : fallback;
  return <span>{text || fallback}</span>;
};

// Global coordination so at most ONE video plays across the entire viewport at a time
let currentActiveVideoId: string | null = null;
const videoActivationListeners = new Set<(id: string | null) => void>();

function claimActiveVideo(id: string | null) {
  currentActiveVideoId = id;
  videoActivationListeners.forEach((fn) => fn(id));
}

export const ViewportAutoplayVideo: React.FC<{
  id: string;
  src: string;
  loop?: boolean;
  maxHeight?: number;
}> = ({ id, src, loop = true, maxHeight = 520 }) => {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const [isMuted, setIsMuted] = useState(true);
  const [isPlaying, setIsPlaying] = useState(false);
  const [manuallyPaused, setManuallyPaused] = useState(false);
  const [hasError, setHasError] = useState(false);

  useEffect(() => {
    const el = videoRef.current;
    if (!el) return;

    const prefersReducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches;

    const handleGlobalClaim = (activeId: string | null) => {
      if (activeId !== id && !el.paused) {
        el.pause();
      }
    };
    videoActivationListeners.add(handleGlobalClaim);

    const observer = new IntersectionObserver(
      (entries) => {
        const entry = entries[0];
        if (!entry) return;
        if (entry.isIntersecting && entry.intersectionRatio >= 0.6) {
          if (!manuallyPaused && !prefersReducedMotion) {
            claimActiveVideo(id);
            el.muted = isMuted;
            el.play().catch(() => {
              // Respect browser autoplay policies gracefully without throwing
            });
          }
        } else {
          if (!el.paused) {
            el.pause();
          }
          if (currentActiveVideoId === id) {
            claimActiveVideo(null);
          }
        }
      },
      { threshold: [0, 0.35, 0.6, 0.85] }
    );

    observer.observe(el);

    const onVisibility = () => {
      if (document.visibilityState !== 'visible' && !el.paused) {
        el.pause();
      }
    };
    document.addEventListener('visibilitychange', onVisibility);

    return () => {
      observer.disconnect();
      videoActivationListeners.delete(handleGlobalClaim);
      document.removeEventListener('visibilitychange', onVisibility);
      if (currentActiveVideoId === id) {
        claimActiveVideo(null);
      }
    };
  }, [id, isMuted, manuallyPaused]);

  const togglePlay = () => {
    const el = videoRef.current;
    if (!el) return;
    if (el.paused) {
      setManuallyPaused(false);
      claimActiveVideo(id);
      el.play().catch(() => undefined);
    } else {
      setManuallyPaused(true);
      el.pause();
    }
  };

  const toggleMute = () => {
    const el = videoRef.current;
    const next = !isMuted;
    setIsMuted(next);
    if (el) el.muted = next;
  };

  if (hasError) {
    return (
      <div
        style={{
          padding: 28,
          textAlign: 'center',
          background: '#0f1018',
          borderRadius: 12,
          color: 'var(--text-secondary)',
          fontSize: '0.86rem'
        }}
      >
        Video unavailable or unsupported format.
      </div>
    );
  }

  return (
    <div style={{ position: 'relative', borderRadius: 14, overflow: 'hidden', background: '#000' }}>
      <video
        ref={videoRef}
        src={src}
        muted={isMuted}
        loop={loop}
        playsInline
        preload="metadata"
        onPlay={() => setIsPlaying(true)}
        onPause={() => setIsPlaying(false)}
        onError={() => setHasError(true)}
        onClick={togglePlay}
        style={{
          width: '100%',
          maxHeight,
          objectFit: 'contain',
          display: 'block',
          cursor: 'pointer'
        }}
      />
      <div
        style={{
          position: 'absolute',
          right: 12,
          bottom: 12,
          display: 'flex',
          gap: 8
        }}
      >
        <button
          type="button"
          onClick={togglePlay}
          aria-label={isPlaying ? 'Pause video' : 'Play video'}
          style={{
            width: 34,
            height: 34,
            borderRadius: '50%',
            border: 'none',
            background: 'rgba(9, 9, 14, 0.72)',
            color: '#fff',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: 'pointer'
          }}
        >
          {isPlaying ? <Pause size={16} /> : <Play size={16} />}
        </button>
        <button
          type="button"
          onClick={toggleMute}
          aria-label={isMuted ? 'Unmute video' : 'Mute video'}
          style={{
            width: 34,
            height: 34,
            borderRadius: '50%',
            border: 'none',
            background: 'rgba(9, 9, 14, 0.72)',
            color: '#fff',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: 'pointer'
          }}
        >
          {isMuted ? <VolumeX size={16} /> : <Volume2 size={16} />}
        </button>
      </div>
    </div>
  );
};

export const WebUpdateAndInstallBanners: React.FC<{ pwa: PwaUpdateState }> = ({ pwa }) => {
  return (
    <>
      {pwa.updateAvailable && (
        <div
          role="status"
          style={{
            position: 'fixed',
            bottom: 84,
            right: 16,
            left: 16,
            maxWidth: 460,
            marginLeft: 'auto',
            zIndex: 90,
            background: 'rgba(18, 19, 28, 0.96)',
            border: '1px solid var(--vybe-cyan)',
            borderRadius: 16,
            padding: '14px 16px',
            boxShadow: '0 14px 36px rgba(0, 0, 0, 0.55)'
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
            <RefreshCw size={18} color="#00E5FF" />
            <strong style={{ fontSize: '0.92rem' }}>New VYBE Web Update Ready</strong>
          </div>
          <p style={{ fontSize: '0.82rem', color: 'var(--text-secondary)', marginBottom: 12 }}>
            A newer version of VYBE is deployed. Update safely now or continue browsing and apply later.
          </p>
          {pwa.updateError && (
            <p style={{ fontSize: '0.78rem', color: 'var(--vybe-danger)', marginBottom: 8 }}>
              {pwa.updateError}
            </p>
          )}
          <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
            <button type="button" className="vybe-btn vybe-btn-outline" onClick={pwa.dismissUpdateLater}>
              Later
            </button>
            {pwa.updateError ? (
              <button
                type="button"
                className="vybe-btn vybe-btn-primary"
                onClick={() => void pwa.forceSafeRecoveryReload()}
              >
                Safe Recovery Reload
              </button>
            ) : (
              <button
                type="button"
                className="vybe-btn vybe-btn-primary"
                onClick={() => void pwa.applyUpdateNow()}
              >
                Update Now
              </button>
            )}
          </div>
        </div>
      )}

      {pwa.canInstallPwa && (
        <div
          style={{
            margin: '12px 16px 0',
            padding: '10px 14px',
            borderRadius: 14,
            background: 'rgba(124, 77, 255, 0.14)',
            border: '1px solid rgba(124, 77, 255, 0.38)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 12
          }}
        >
          <div style={{ fontSize: '0.84rem' }}>
            <strong>Install VYBE Web App</strong>
            <div style={{ color: 'var(--text-secondary)', fontSize: '0.78rem' }}>
              Launch VYBE full-screen from your home screen or desktop dock.
            </div>
          </div>
          <button
            type="button"
            className="vybe-btn vybe-btn-violet"
            onClick={() => void pwa.triggerInstallPrompt()}
          >
            <Download size={15} /> Install
          </button>
        </div>
      )}
    </>
  );
};
