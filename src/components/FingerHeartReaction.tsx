import React, { useState, useId, useRef } from 'react';
import { useVybe } from '../context/VybeContext';
import { VYBE_REACTIONS, VybeReaction } from '../types';

export interface FingerHeartIconProps {
  isLiked: boolean;
  size?: number;
  forceDarkSurface?: boolean;
  forceDarkOverlay?: boolean;
  isLightMode?: boolean;
  className?: string;
}

/**
 * Proprietary VYBE Universal Reaction Icon (SVG):
 * Sculpted geometric V-shaped heart crest converging into a sharp VYBE "V" apex,
 * fused with an integrated electric energy-pulse waveform and blue-purple aura glow.
 */
export const VybeUniversalReactionIcon: React.FC<FingerHeartIconProps> = ({
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
  const gradId = `vybe-univ-grad-${uid}`;
  const borderGradId = `vybe-univ-border-${uid}`;
  const glowId = `vybe-univ-glow-${uid}`;
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
          ? { filter: 'drop-shadow(0 0 7px rgba(139, 92, 246, 0.58))' }
          : undefined
      }
      aria-hidden="true"
    >
      <defs>
        <linearGradient id={gradId} x1="3" y1="2.5" x2="21" y2="21.5" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="#00C6FF" />
          <stop offset="38%" stopColor="#4F46E5" />
          <stop offset="72%" stopColor="#8B5CF6" />
          <stop offset="100%" stopColor="#A855F7" />
        </linearGradient>
        <linearGradient id={borderGradId} x1="2" y1="2" x2="22" y2="22" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stopColor="#7DD3FC" />
          <stop offset="50%" stopColor="#8B5CF6" />
          <stop offset="100%" stopColor="#D8B4FE" />
        </linearGradient>
        <radialGradient id={glowId} cx="50%" cy="48%" r="55%">
          <stop offset="0%" stopColor="#00C6FF" stopOpacity="0.28" />
          <stop offset="55%" stopColor="#8B5CF6" stopOpacity="0.24" />
          <stop offset="100%" stopColor="#A855F7" stopOpacity="0" />
        </radialGradient>
      </defs>

      {isLiked && <circle cx="12" cy="11.5" r="11.2" fill={`url(#${glowId})`} />}

      {/* Sculpted V-Shaped Heart Crest converging into sharp bottom V apex */}
      <path
        d="M12 21.5L2.9 10.3C1.5 6.9 3.4 2.9 7.2 2.9C9.4 2.9 10.8 4.3 12 6.7C13.2 4.3 14.6 2.9 16.8 2.9C20.6 2.9 22.5 6.9 21.1 10.3L12 21.5Z"
        fill={isLiked ? `url(#${gradId})` : 'none'}
        stroke={isLiked ? `url(#${borderGradId})` : inactiveStroke}
        strokeWidth={isLiked ? '1.15' : '1.9'}
        strokeLinecap="round"
        strokeLinejoin="round"
      />

      {/* Signature VYBE Energy-Pulse Waveform spanning across the V-Heart core */}
      {isLiked && (
        <path
          d="M4.8 11.5H8.4L10.3 7.4L12.5 15.3L14.4 9.4L15.8 11.5H19.2"
          fill="none"
          stroke="rgba(0, 198, 255, 0.45)"
          strokeWidth="2.8"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      )}
      <path
        d="M4.8 11.5H8.4L10.3 7.4L12.5 15.3L14.4 9.4L15.8 11.5H19.2"
        fill="none"
        stroke={isLiked ? '#F8FAFC' : inactiveStroke}
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
};

export const FingerHeartIcon = VybeUniversalReactionIcon;

interface Particle {
  id: number;
  dx: number;
  dy: number;
  size: number;
}

export interface FingerHeartReactionProps {
  isLiked: boolean;
  userReaction?: VybeReaction;
  onReact?: (reaction: VybeReaction) => void;
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
  userReaction,
  onReact,
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
  title = 'VYBE Reaction',
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
  const effectiveReaction: VybeReaction | undefined = userReaction || (isLiked ? 'LOVE' : undefined);
  const isActive = Boolean(effectiveReaction);

  const [animating, setAnimating] = useState(false);
  const [particles, setParticles] = useState<Particle[]>([]);
  const [selectorOpen, setSelectorOpen] = useState(false);
  const [hoveredIdx, setHoveredIdx] = useState<number | null>(null);

  const holdTimerRef = useRef<number | null>(null);
  const startPointRef = useRef<{ x: number; y: number } | null>(null);
  const scrolledRef = useRef(false);
  const longPressTriggeredRef = useRef(false);
  const barRef = useRef<HTMLDivElement | null>(null);

  const triggerBurst = () => {
    setAnimating(true);
    const now = Date.now();
    const burst: Particle[] = [
      { id: now + 1, dx: -14, dy: -28, size: 11 },
      { id: now + 2, dx: 13, dy: -34, size: 12 },
      { id: now + 3, dx: -4, dy: -38, size: 10 },
      { id: now + 4, dx: 16, dy: -24, size: 10 },
    ];
    setParticles((prev) => [...prev, ...burst]);
    window.setTimeout(() => setAnimating(false), 410);
    window.setTimeout(() => {
      setParticles((prev) => prev.filter((p) => !burst.some((b) => b.id === p.id)));
    }, 460);
  };

  const clearHoldTimer = () => {
    if (holdTimerRef.current !== null) {
      window.clearTimeout(holdTimerRef.current);
      holdTimerRef.current = null;
    }
  };

  const handlePointerDown = (e: React.PointerEvent) => {
    scrolledRef.current = false;
    longPressTriggeredRef.current = false;
    startPointRef.current = { x: e.clientX, y: e.clientY };

    if (onReact) {
      clearHoldTimer();
      holdTimerRef.current = window.setTimeout(() => {
        if (!scrolledRef.current) {
          longPressTriggeredRef.current = true;
          setSelectorOpen(true);
          setHoveredIdx(0);
          if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
            try { navigator.vibrate(18); } catch {}
          }
        }
      }, 360);
    }
  };

  const handlePointerMove = (e: React.PointerEvent) => {
    const start = startPointRef.current;
    if (!start) return;

    if (!longPressTriggeredRef.current) {
      const dist = Math.hypot(e.clientX - start.x, e.clientY - start.y);
      if (dist > 10) {
        // Scroll / pan movement detected -> cancel reaction gesture immediately
        scrolledRef.current = true;
        clearHoldTimer();
      }
      return;
    }

    if (selectorOpen && barRef.current) {
      const rect = barRef.current.getBoundingClientRect();
      if (
        e.clientY >= rect.top - 48 &&
        e.clientY <= rect.bottom + 68 &&
        e.clientX >= rect.left - 16 &&
        e.clientX <= rect.right + 16
      ) {
        const ratio = Math.min(0.999, Math.max(0, (e.clientX - rect.left) / Math.max(1, rect.width)));
        const idx = Math.floor(ratio * VYBE_REACTIONS.length);
        if (idx !== hoveredIdx) {
          setHoveredIdx(idx);
          if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
            try { navigator.vibrate(8); } catch {}
          }
        }
      } else {
        setHoveredIdx(null);
      }
    }
  };

  const handlePointerUp = (e: React.PointerEvent) => {
    clearHoldTimer();
    if (longPressTriggeredRef.current) {
      e.preventDefault();
      e.stopPropagation();
      if (hoveredIdx !== null && VYBE_REACTIONS[hoveredIdx] && onReact) {
        const chosen = VYBE_REACTIONS[hoveredIdx].id;
        if (effectiveReaction !== chosen) triggerBurst();
        onReact(chosen);
      }
      setSelectorOpen(false);
      setHoveredIdx(null);
    }
  };

  const handlePointerCancel = () => {
    clearHoldTimer();
    scrolledRef.current = true;
    setSelectorOpen(false);
    setHoveredIdx(null);
  };

  const handleClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (scrolledRef.current || longPressTriggeredRef.current) {
      longPressTriggeredRef.current = false;
      scrolledRef.current = false;
      return;
    }

    const becomingLiked = !isActive;
    if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
      try { navigator.vibrate(12); } catch {}
    }

    if (becomingLiked) {
      triggerBurst();
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

  const nonDefaultReaction =
    effectiveReaction && effectiveReaction !== 'LOVE'
      ? VYBE_REACTIONS.find((r) => r.id === effectiveReaction)
      : undefined;

  return (
    <div className="relative inline-flex items-center">
      {selectorOpen && onReact && (
        <div
          ref={barRef}
          className={`absolute ${
            verticalLayout ? 'right-14 top-0' : 'bottom-11 left-0'
          } flex items-center gap-1.5 px-2.5 py-1.5 rounded-full border border-[#8B5CF6]/50 bg-[#0B1022]/95 backdrop-blur-xl shadow-2xl z-40 select-none`}
        >
          {VYBE_REACTIONS.map((rx, idx) => {
            const isHovered = hoveredIdx === idx;
            const isSelected = effectiveReaction === rx.id;
            return (
              <button
                key={rx.id}
                type="button"
                onClick={(ev) => {
                  ev.stopPropagation();
                  if (effectiveReaction !== rx.id) triggerBurst();
                  onReact(rx.id);
                  setSelectorOpen(false);
                  setHoveredIdx(null);
                }}
                title={rx.label}
                className={`w-9 h-9 rounded-full flex items-center justify-center transition-transform duration-150 ${
                  isHovered
                    ? 'scale-125 bg-[#8B5CF6]/30'
                    : isSelected
                      ? 'scale-110 bg-[#8B5CF6]/20'
                      : 'hover:scale-110 hover:bg-white/10'
                }`}
              >
                {rx.id === 'LOVE' ? (
                  <VybeUniversalReactionIcon isLiked={true} size={21} forceDarkOverlay={true} />
                ) : (
                  <span className="text-lg leading-none">{rx.emoji}</span>
                )}
              </button>
            );
          })}
        </div>
      )}

      <button
        type="button"
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerCancel={handlePointerCancel}
        onClick={handleClick}
        title={title}
        aria-label={title}
        data-testid="finger-heart-reaction-btn"
        className={`relative ${
          verticalLayout ? 'inline-flex flex-col items-center gap-1' : 'inline-flex items-center gap-1.5'
        } select-none transition-colors focus:outline-none ${className}`}
      >
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
            <VybeUniversalReactionIcon isLiked={true} size={p.size} forceDarkSurface={true} />
          </span>
        ))}

        <span
          className={`inline-flex items-center justify-center ${
            verticalLayout ? 'p-3 rounded-full bg-black/50 backdrop-blur-md border border-white/10' : ''
          }`}
          style={{
            transition: 'transform 390ms cubic-bezier(0.34, 1.56, 0.64, 1)',
            transform: animating ? 'scale(1.34) rotate(-8deg)' : 'scale(1)',
          }}
        >
          {nonDefaultReaction ? (
            <span
              style={{ fontSize: `${Math.round(size * 0.85)}px`, lineHeight: 1 }}
              className="inline-flex items-center justify-center"
            >
              {nonDefaultReaction.emoji}
            </span>
          ) : (
            <VybeUniversalReactionIcon
              isLiked={isActive}
              size={size}
              forceDarkSurface={forceDark}
              isLightMode={resolvedLightMode}
            />
          )}
        </span>

        {formattedCount !== null && (
          <span
            className={`text-xs font-semibold tabular-nums transition-all duration-200 ${
              isActive
                ? 'text-[#8B5CF6]'
                : forceDark || !resolvedLightMode
                  ? 'text-slate-200 keep-white'
                  : 'text-slate-900'
            } ${countClassName}`}
          >
            {formattedCount}
          </span>
        )}
      </button>
    </div>
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
          <VybeUniversalReactionIcon isLiked={true} size={20} forceDarkSurface={true} />
        </span>
      ))}
      <div
        style={{
          animation: 'vybeFingerHeartCenterBurst 620ms cubic-bezier(0.34, 1.56, 0.64, 1) forwards',
        }}
      >
        <VybeUniversalReactionIcon isLiked={true} size={92} forceDarkSurface={true} />
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

  const triggerBurst = () => {
    if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
      try {
        navigator.vibrate([12, 24, 16]);
      } catch {
        // ignore
      }
    }
    setBurstVisible(true);
    onDoubleTap();
    window.setTimeout(() => {
      setBurstVisible(false);
    }, 640);
  };

  const handleTouchEnd = (e: React.TouchEvent) => {
    const target = e.target as HTMLElement | null;
    if (target?.closest('button, a, input, video')) return;

    const now = Date.now();
    if (now - lastTapMsRef.current < 300) {
      e.preventDefault();
      lastTapMsRef.current = 0;
      triggerBurst();
    } else {
      lastTapMsRef.current = now;
    }
  };

  return (
    <div
      className={`relative ${className}`}
      onDoubleClick={(e) => {
        const target = e.target as HTMLElement | null;
        if (target?.closest('button, a, input')) return;
        triggerBurst();
      }}
      onTouchEnd={handleTouchEnd}
    >
      {children}
      <FingerHeartBurstOverlay visible={burstVisible} />
    </div>
  );
};
