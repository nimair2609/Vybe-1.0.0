import React, { useState, useId, useRef } from 'react';
import { useVybe } from '../context/VybeContext';

export interface FingerHeartIconProps {
  isLiked: boolean;
  size?: number;
  forceDarkSurface?: boolean;
  forceDarkOverlay?: boolean;
  isLightMode?: boolean;
  className?: string;
}

export const FingerHeartIcon: React.FC<FingerHeartIconProps> = ({
  isLiked,
  size = 22,
  forceDarkSurface = false,
  forceDarkOverlay = false,
  isLightMode,
  className = '',
}) => {
  let contextLightMode = false;
  try {
    const ctx = useVybe();
    contextLightMode = ctx.themeMode === 'light';
  } catch {
    // Allow standalone rendering outside VybeProvider
  }

  const uid = useId().replace(/:/g, '');
  const gradId = `vybe-fh-grad-${uid}`;
  const glowId = `vybe-fh-glow-${uid}`;
  const resolvedLightMode = isLightMode ?? contextLightMode;
  const forceDark = forceDarkSurface || forceDarkOverlay;
  const useDarkOutline = !forceDark && resolvedLightMode;
  const inactiveStroke = useDarkOutline ? '#0F172A' : '#FFFFFF';

  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={className}
      style={
        isLiked
          ? { filter: 'drop-shadow(0 0 6px rgba(255, 46, 147, 0.52))' }
          : undefined
      }
      aria-hidden="true"
    >
      <defs>
        <linearGradient id={gradId} x1="4" y1="2" x2="20" y2="22" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="#FF2E93" />
          <stop offset="52%" stopColor="#EC4899" />
          <stop offset="100%" stopColor="#D946EF" />
        </linearGradient>
        <radialGradient id={glowId} cx="50%" cy="44%" r="52%">
          <stop offset="0%" stopColor="#FF2E93" stopOpacity="0.32" />
          <stop offset="100%" stopColor="#D946EF" stopOpacity="0" />
        </radialGradient>
      </defs>

      {isLiked && <circle cx="12" cy="11" r="10.5" fill={`url(#${glowId})`} />}

      {/* Crown Heart above crossed fingers */}
      <path
        d="M12 9.5C7.4 6.8 5.4 3.4 8.4 1.9C10.2 1.0 11.4 2.3 12 3.5C12.6 2.3 13.8 1.0 15.6 1.9C18.6 3.4 16.6 6.8 12 9.5Z"
        fill={isLiked ? `url(#${gradId})` : 'none'}
        stroke={isLiked ? `url(#${gradId})` : inactiveStroke}
        strokeWidth="1.85"
        strokeLinecap="round"
        strokeLinejoin="round"
      />

      {/* Index fingertip rising behind thumb */}
      <path
        d="M8.7 13.4C8.4 10.8 9.8 9.4 11.8 10.1C12.7 10.5 13.2 11.5 12.8 12.7"
        fill={isLiked ? `url(#${gradId})` : 'none'}
        stroke={isLiked ? `url(#${gradId})` : inactiveStroke}
        strokeWidth="1.85"
        strokeLinecap="round"
        strokeLinejoin="round"
      />

      {/* Crossed thumb + sleek hand silhouette */}
      <path
        d="M6.5 15.8C6.3 13.2 9.4 10.8 13.7 10.6C16.3 10.6 17.5 12.2 16.1 13.9C18.0 14.6 18.4 17.2 17.0 19.6C15.8 21.5 13.7 22.3 11.3 22.3C8.4 22.3 6.3 20.4 6.3 17.5C6.3 16.9 6.4 16.3 6.5 15.8Z"
        fill={isLiked ? `url(#${gradId})` : 'none'}
        stroke={isLiked ? `url(#${gradId})` : inactiveStroke}
        strokeWidth="1.85"
        strokeLinecap="round"
        strokeLinejoin="round"
      />

      {/* Interior thumb-overlap crease line */}
      <path
        d="M8.9 15.1C11.0 13.9 13.4 13.7 15.8 13.9"
        fill="none"
        stroke={isLiked ? 'rgba(255,255,255,0.72)' : inactiveStroke}
        strokeWidth="1.45"
        strokeLinecap="round"
      />
    </svg>
  );
};

interface Particle {
  id: number;
  dx: number;
  dy: number;
  size: number;
}

export interface FingerHeartReactionProps {
  isLiked: boolean;
  onToggle?: () => void;
  onClick?: (e: React.MouseEvent) => void;
  count?: number;
  showCount?: boolean;
  showZeroCount?: boolean;
  size?: number;
  forceDarkSurface?: boolean;
  forceDarkOverlay?: boolean;
  verticalLayout?: boolean;
  isLightMode?: boolean;
  className?: string;
  countClassName?: string;
  title?: string;
}

export const FingerHeartReaction: React.FC<FingerHeartReactionProps> = ({
  isLiked,
  onToggle,
  onClick,
  count,
  showCount,
  showZeroCount = false,
  size = 22,
  forceDarkSurface = false,
  forceDarkOverlay = false,
  verticalLayout = false,
  isLightMode,
  className = '',
  countClassName = '',
  title = 'Finger Heart Reaction',
}) => {
  let contextLightMode = false;
  try {
    const ctx = useVybe();
    contextLightMode = ctx.themeMode === 'light';
  } catch {
    // Allow standalone rendering
  }

  const resolvedLightMode = isLightMode ?? contextLightMode;
  const forceDark = forceDarkSurface || forceDarkOverlay;
  const [animating, setAnimating] = useState(false);
  const [particles, setParticles] = useState<Particle[]>([]);

  const handleClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    const becomingLiked = !isLiked;
    if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
      try {
        navigator.vibrate(12);
      } catch {
        // Ignore unsupported vibration environments
      }
    }

    if (becomingLiked) {
      setAnimating(true);
      const now = Date.now();
      const burst: Particle[] = [
        { id: now + 1, dx: -14, dy: -28, size: 11 },
        { id: now + 2, dx: 13, dy: -34, size: 12 },
        { id: now + 3, dx: -4, dy: -38, size: 10 },
        { id: now + 4, dx: 16, dy: -24, size: 10 },
      ];
      setParticles((prev) => [...prev, ...burst]);
      window.setTimeout(() => {
        setAnimating(false);
      }, 410);
      window.setTimeout(() => {
        setParticles((prev) => prev.filter((p) => !burst.some((b) => b.id === p.id)));
      }, 460);
    }

    if (onClick) onClick(e);
    if (onToggle) onToggle();
  };

  const shouldDisplayCount =
    showCount !== undefined
      ? showCount
      : count !== undefined && (count > 0 || showZeroCount);

  const formattedCount =
    shouldDisplayCount && count !== undefined
      ? count >= 1_000_000
        ? `${(count / 1_000_000).toFixed(1).replace(/\.0$/, '')}M`
        : count >= 1_000
          ? `${(count / 1_000).toFixed(1).replace(/\.0$/, '')}K`
          : `${count}`
      : null;

  return (
    <button
      type="button"
      onClick={handleClick}
      title={title}
      aria-label={title}
      data-testid="finger-heart-reaction-btn"
      className={`relative ${
        verticalLayout ? 'inline-flex flex-col items-center gap-1' : 'inline-flex items-center gap-1.5'
      } select-none transition-colors focus:outline-none ${className}`}
    >
      {/* Floating mini Finger Heart particles */}
      {particles.map((p) => (
        <span
          key={p.id}
          className="pointer-events-none absolute left-2 top-1 z-20"
          style={{
            animation: 'vybeFingerHeartFloat 430ms cubic-bezier(0.22, 1, 0.36, 1) forwards',
            ['--fh-dx' as string]: `${p.dx}px`,
            ['--fh-dy' as string]: `${p.dy}px`,
          }}
        >
          <FingerHeartIcon isLiked={true} size={p.size} forceDarkSurface={true} />
        </span>
      ))}

      <span
        className={`inline-flex items-center justify-center ${
          verticalLayout ? 'p-3 rounded-full bg-black/50 backdrop-blur-md border border-white/10' : ''
        }`}
        style={{
          transition: 'transform 390ms cubic-bezier(0.34, 1.56, 0.64, 1)',
          transform: animating ? 'scale(1.35) rotate(-8deg)' : 'scale(1)',
        }}
      >
        <FingerHeartIcon
          isLiked={isLiked}
          size={size}
          forceDarkSurface={forceDark}
          isLightMode={resolvedLightMode}
        />
      </span>

      {formattedCount !== null && (
        <span
          className={`text-xs font-semibold tabular-nums transition-all duration-200 ${
            isLiked
              ? 'text-[#FF2E93]'
              : forceDark || !resolvedLightMode
                ? 'text-slate-200 keep-white'
                : 'text-slate-900'
          } ${countClassName}`}
        >
          {formattedCount}
        </span>
      )}
    </button>
  );
};

export const FingerHeartBurstOverlay: React.FC<{ visible: boolean }> = ({ visible }) => {
  if (!visible) return null;
  const offsets = [
    { dx: -42, dy: -48 },
    { dx: 44, dy: -46 },
    { dx: -52, dy: -10 },
    { dx: 54, dy: -8 },
    { dx: -18, dy: -64 },
    { dx: 22, dy: -62 },
  ];

  return (
    <div
      className="pointer-events-none absolute inset-0 z-30 flex items-center justify-center"
      data-testid="finger-heart-double-tap-overlay"
    >
      {offsets.map((o, i) => (
        <span
          key={i}
          className="absolute"
          style={{
            animation: 'vybeFingerHeartFloat 560ms cubic-bezier(0.22, 1, 0.36, 1) forwards',
            ['--fh-dx' as string]: `${o.dx}px`,
            ['--fh-dy' as string]: `${o.dy}px`,
          }}
        >
          <FingerHeartIcon isLiked={true} size={20} forceDarkSurface={true} />
        </span>
      ))}
      <div
        style={{
          animation: 'vybeFingerHeartCenterBurst 620ms cubic-bezier(0.34, 1.56, 0.64, 1) forwards',
        }}
      >
        <FingerHeartIcon isLiked={true} size={92} forceDarkSurface={true} />
      </div>
    </div>
  );
};

export interface FingerHeartDoubleTapOverlayProps {
  onDoubleTap: () => void;
  className?: string;
  children: React.ReactNode;
}

export const FingerHeartDoubleTapOverlay: React.FC<FingerHeartDoubleTapOverlayProps> = ({
  onDoubleTap,
  className = '',
  children,
}) => {
  const [burstVisible, setBurstVisible] = useState(false);
  const lastTapMsRef = useRef<number>(0);
  const cleanupTimerRef = useRef<number | null>(null);

  const triggerBurst = () => {
    if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
      try {
        navigator.vibrate(16);
      } catch {
        // Ignore unsupported vibration
      }
    }
    onDoubleTap();
    setBurstVisible(true);
    if (cleanupTimerRef.current) {
      window.clearTimeout(cleanupTimerRef.current);
    }
    cleanupTimerRef.current = window.setTimeout(() => {
      setBurstVisible(false);
    }, 640);
  };

  const handleTouchEnd = () => {
    const now = Date.now();
    if (now - lastTapMsRef.current < 290) {
      lastTapMsRef.current = 0;
      triggerBurst();
    } else {
      lastTapMsRef.current = now;
    }
  };

  return (
    <div
      onDoubleClick={(e) => {
        e.stopPropagation();
        triggerBurst();
      }}
      onTouchEnd={handleTouchEnd}
      className={`relative ${className}`}
    >
      {children}
      <FingerHeartBurstOverlay visible={burstVisible} />
    </div>
  );
};
