import React, { useState, useEffect } from 'react';
import { ImageOff } from 'lucide-react';
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
 * to avoid layout shift, async decoding, browser cache reuse, and clean fallback without mock stock photos.
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
  const [resolvedSrc, setResolvedSrc] = useState<string>(src || fallbackSrc);
  const [loaded, setLoaded] = useState<boolean>(false);
  const [hasFailed, setHasFailed] = useState<boolean>(!src && !fallbackSrc);

  useEffect(() => {
    const next = src || fallbackSrc;
    setResolvedSrc(next);
    setLoaded(false);
    setHasFailed(!next);
  }, [src, fallbackSrc]);

  const handleLoad = (e: React.SyntheticEvent<HTMLImageElement>) => {
    setLoaded(true);
    setHasFailed(false);
    if (resolvedSrc) {
      trackAndCacheImage(resolvedSrc).catch(() => {});
    }
    onLoad?.(e);
  };

  const handleError = (e: React.SyntheticEvent<HTMLImageElement>) => {
    if (fallbackSrc && resolvedSrc !== fallbackSrc) {
      setResolvedSrc(fallbackSrc);
    } else {
      setHasFailed(true);
    }
    onError?.(e);
  };

  if (hasFailed || !resolvedSrc) {
    return (
      <div
        role="img"
        aria-label={alt}
        style={{
          ...(aspectRatio ? { aspectRatio } : {}),
          ...style
        }}
        className={`${className} flex items-center justify-center bg-[var(--vybe-elevated)] text-[var(--vybe-text-muted)]`}
      >
        <ImageOff className="w-5 h-5 opacity-45" />
      </div>
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

export function getUserInitials(name?: string, username?: string): string {
  const source = (name || username || 'V').trim();
  if (!source) return 'V';
  const parts = source.split(/\s+/).filter(Boolean);
  if (parts.length >= 2) {
    return `${parts[0][0]}${parts[1][0]}`.toUpperCase();
  }
  return source.slice(0, 2).toUpperCase();
}

export interface VybeAvatarProps {
  src?: string | null;
  name?: string;
  username?: string;
  className?: string;
  textClassName?: string;
  isOnline?: boolean;
  eager?: boolean;
}

/**
 * Real-user avatar with automatic monogram fallback (no synthetic/bot avatar URLs).
 */
export const VybeAvatar: React.FC<VybeAvatarProps> = ({
  src,
  name = '',
  username = '',
  className = 'w-10 h-10 rounded-full',
  textClassName = 'text-xs',
  isOnline = false,
  eager = false
}) => {
  const [imgFailed, setImgFailed] = useState(false);

  useEffect(() => {
    setImgFailed(false);
  }, [src]);

  const cleanSrc = (src || '').trim();
  const initials = getUserInitials(name, username);
  const altLabel = name || username || 'VYBE member';

  return (
    <div className={`relative inline-flex items-center justify-center shrink-0 overflow-hidden bg-gradient-to-br from-[#00C6FF]/25 via-[#8B5CF6]/30 to-[#EC4899]/25 border border-[var(--vybe-border)] ${className}`}>
      {cleanSrc && !imgFailed ? (
        <img
          src={cleanSrc}
          alt={altLabel}
          loading={eager ? 'eager' : 'lazy'}
          decoding="async"
          onError={() => setImgFailed(true)}
          className="w-full h-full object-cover"
        />
      ) : (
        <span
          aria-label={altLabel}
          className={`font-extrabold select-none text-[var(--vybe-text)] ${textClassName}`}
        >
          {initials}
        </span>
      )}
      {isOnline && (
        <span
          aria-hidden="true"
          className="absolute bottom-0 right-0 w-2.5 h-2.5 bg-emerald-500 border-2 border-[var(--vybe-surface)] rounded-full"
        />
      )}
    </div>
  );
};
