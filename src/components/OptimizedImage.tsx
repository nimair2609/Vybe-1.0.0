import React, { useState, useEffect } from 'react';
import { trackAndCacheImage } from '../utils/webImageCache';

export interface OptimizedImageProps extends React.ImgHTMLAttributes<HTMLImageElement> {
  src: string;
  alt: string;
  fallbackSrc?: string;
  width?: number;
  height?: number;
  aspectRatio?: string;
  eager?: boolean;
}

/**
 * Reusable lazy-loaded image component enforcing explicit dimensions/aspect ratios
 * to avoid layout shift, async decoding, browser cache reuse, and clean fallback without stock URLs.
 */
export const OptimizedImage: React.FC<OptimizedImageProps> = ({
  src,
  alt,
  fallbackSrc = '',
  width,
  height,
  aspectRatio,
  eager = false,
  className = '',
  style,
  onLoad,
  onError,
  ...rest
}) => {
  const cleanSrc = (src || '').trim();
  const cleanFallback = (fallbackSrc || '').trim();
  const [resolvedSrc, setResolvedSrc] = useState<string>(cleanSrc || cleanFallback);
  const [loaded, setLoaded] = useState<boolean>(false);
  const [failed, setFailed] = useState<boolean>(false);

  useEffect(() => {
    setResolvedSrc(cleanSrc || cleanFallback);
    setLoaded(false);
    setFailed(false);
  }, [cleanSrc, cleanFallback]);

  const handleLoad = (e: React.SyntheticEvent<HTMLImageElement>) => {
    setLoaded(true);
    setFailed(false);
    if (resolvedSrc) {
      trackAndCacheImage(resolvedSrc).catch(() => {});
    }
    onLoad?.(e);
  };

  const handleError = (e: React.SyntheticEvent<HTMLImageElement>) => {
    if (cleanFallback && resolvedSrc !== cleanFallback) {
      setResolvedSrc(cleanFallback);
    } else {
      setFailed(true);
    }
    onError?.(e);
  };

  if (!resolvedSrc || failed) {
    return (
      <div
        aria-label={alt}
        style={{
          ...(aspectRatio ? { aspectRatio } : {}),
          ...(width ? { width } : {}),
          ...(height ? { height } : {}),
          ...style
        }}
        className={`${className} bg-[var(--vybe-elevated)] flex items-center justify-center text-[var(--vybe-text-muted)]`}
      />
    );
  }

  return (
    <img
      src={resolvedSrc}
      alt={alt}
      width={width}
      height={height}
      loading={eager ? 'eager' : 'lazy'}
      decoding="async"
      onLoad={handleLoad}
      onError={handleError}
      style={{
        ...(aspectRatio ? { aspectRatio } : {}),
        ...style
      }}
      className={`${className} transition-opacity duration-200 ${loaded ? 'opacity-100' : 'opacity-85'}`}
      {...rest}
    />
  );
};

export interface VybeAvatarProps {
  src?: string;
  name?: string;
  username?: string;
  alt?: string;
  size?: number;
  isOnline?: boolean;
  eager?: boolean;
  className?: string;
  textClassName?: string;
}

const AVATAR_GRADIENTS = [
  'from-[#6366F1] to-[#8B5CF6]',
  'from-[#0EA5E9] to-[#2563EB]',
  'from-[#10B981] to-[#059669]',
  'from-[#EC4899] to-[#8B5CF6]',
  'from-[#F59E0B] to-[#EA580C]'
];

function computeInitials(name?: string, username?: string): string {
  const source = (name || username || 'V').trim().replace(/^@/, '');
  if (!source) return 'V';
  const parts = source.split(/\s+/).filter(Boolean);
  if (parts.length >= 2) {
    return `${parts[0][0]}${parts[1][0]}`.toUpperCase();
  }
  return source.slice(0, 2).toUpperCase();
}

export const VybeAvatar: React.FC<VybeAvatarProps> = ({
  src,
  name,
  username,
  alt,
  size = 40,
  isOnline = false,
  eager = false,
  className = '',
  textClassName = 'text-xs'
}) => {
  const cleanSrc = (src || '').trim();
  const [imgFailed, setImgFailed] = useState<boolean>(false);

  useEffect(() => {
    setImgFailed(false);
  }, [cleanSrc]);

  const initials = computeInitials(name, username);
  const gradIdx = (initials.charCodeAt(0) || 0) % AVATAR_GRADIENTS.length;
  const gradientClass = AVATAR_GRADIENTS[gradIdx];
  const label = alt || name || (username ? `@${username}` : 'VYBE member');

  return (
    <div
      className={`relative inline-flex items-center justify-center rounded-full overflow-visible shrink-0 select-none ${className}`}
      style={!className.includes('w-') ? { width: size, height: size } : undefined}
    >
      {cleanSrc && !imgFailed ? (
        <img
          src={cleanSrc}
          alt={label}
          width={size}
          height={size}
          loading={eager ? 'eager' : 'lazy'}
          decoding="async"
          onLoad={() => {
            trackAndCacheImage(cleanSrc).catch(() => {});
          }}
          onError={() => setImgFailed(true)}
          className="w-full h-full rounded-full object-cover bg-[var(--vybe-elevated)]"
        />
      ) : (
        <div
          aria-label={label}
          className={`w-full h-full rounded-full bg-gradient-to-br ${gradientClass} text-white keep-white font-bold flex items-center justify-center ${textClassName}`}
        >
          {initials}
        </div>
      )}
      {isOnline && (
        <span
          aria-label="Online"
          className="absolute bottom-0 right-0 w-2.5 h-2.5 rounded-full bg-emerald-500 border-2 border-[var(--vybe-surface)]"
        />
      )}
    </div>
  );
};
