/**
 * VYBE Client-Side Image & Media Compression Engine
 * Compresses images in-browser before upload to Supabase Storage,
 * reducing payload from 5-10MB to 120-250KB (90%+ egress & storage reduction).
 */

export interface CompressionOptions {
  maxWidth?: number;
  maxHeight?: number;
  quality?: number; // 0.0 to 1.0
  format?: 'image/webp' | 'image/jpeg';
}

const DEFAULT_OPTIONS: CompressionOptions = {
  maxWidth: 1440,
  maxHeight: 1440,
  quality: 0.82,
  format: 'image/webp'
};

/**
 * Compress an image file or Data URL to optimized WebP/JPEG data URL or Blob.
 */
export async function compressImageToDataUrl(
  fileOrUrl: File | Blob | string,
  options: CompressionOptions = {}
): Promise<string> {
  const opts = { ...DEFAULT_OPTIONS, ...options };

  return new Promise((resolve, reject) => {
    const img = new window.Image();
    img.crossOrigin = 'anonymous';

    let objectUrl: string | null = null;
    if (typeof fileOrUrl === 'string') {
      img.src = fileOrUrl;
    } else {
      objectUrl = URL.createObjectURL(fileOrUrl);
      img.src = objectUrl;
    }

    img.onload = () => {
      if (objectUrl) URL.revokeObjectURL(objectUrl);

      let { width, height } = img;
      const maxW = opts.maxWidth || 1440;
      const maxH = opts.maxHeight || 1440;

      if (width > maxW || height > maxH) {
        const ratio = Math.min(maxW / width, maxH / height);
        width = Math.round(width * ratio);
        height = Math.round(height * ratio);
      }

      const canvas = document.createElement('canvas');
      canvas.width = width;
      canvas.height = height;
      const ctx = canvas.getContext('2d');

      if (!ctx) {
        resolve(typeof fileOrUrl === 'string' ? fileOrUrl : '');
        return;
      }

      // Smooth scaling
      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = 'high';
      ctx.drawImage(img, 0, 0, width, height);

      // Try preferred format (webp), fall back to jpeg if unsupported
      try {
        const dataUrl = canvas.toDataURL(opts.format || 'image/webp', opts.quality || 0.82);
        resolve(dataUrl);
      } catch {
        const fallbackUrl = canvas.toDataURL('image/jpeg', opts.quality || 0.82);
        resolve(fallbackUrl);
      }
    };

    img.onerror = (err) => {
      if (objectUrl) URL.revokeObjectURL(objectUrl);
      // Fallback: if compression fails, return raw URL or reject
      if (typeof fileOrUrl === 'string') {
        resolve(fileOrUrl);
      } else {
        reject(err);
      }
    };
  });
}

/**
 * Check if browser supports WebP canvas export.
 */
export function isWebPSupported(): boolean {
  if (typeof document === 'undefined') return false;
  const canvas = document.createElement('canvas');
  canvas.width = 1;
  canvas.height = 1;
  return canvas.toDataURL('image/webp').indexOf('data:image/webp') === 0;
}
