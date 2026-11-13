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
 * to avoid layout shift, async decoding, browser cache reuse, and fallback protection.
 */
export const OptimizedImage: React.FC<OptimizedImageProps> = ({
  src,
  alt,
  fallbackSrc = 'https://images.unsplash.com/photo-1514565131-fce0801e5785?auto=format&fit=crop&w=800&q=80',
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

  useEffect(() => {
    setResolvedSrc(src || fallbackSrc);
    setLoaded(false);
  }, [src, fallbackSrc]);

  const handleLoad = (e: React.SyntheticEvent<HTMLImageElement>) => {
    setLoaded(true);
    if (resolvedSrc) {
      trackAndCacheImage(resolvedSrc).catch(() => {});
    }
    onLoad?.(e);
  };

  const handleError = (e: React.SyntheticEvent<HTMLImageElement>) => {
    if (fallbackSrc && resolvedSrc !== fallbackSrc) {
      setResolvedSrc(fallbackSrc);
    }
    onError?.(e);
  };

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
