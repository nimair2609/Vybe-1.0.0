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

export interface CompressedBlobResult {
  blob: Blob;
  mimeType: string;
  extension: string;
  sizeBytes: number;
  originalSizeBytes: number;
  width: number;
  height: number;
}

const DEFAULT_OPTIONS: CompressionOptions = {
  maxWidth: 1440,
  maxHeight: 1440,
  quality: 0.82,
  format: 'image/webp'
};

/**
 * Check if browser supports WebP canvas export.
 */
export function isWebPSupported(): boolean {
  if (typeof document === 'undefined') return false;
  try {
    const canvas = document.createElement('canvas');
    canvas.width = 1;
    canvas.height = 1;
    return canvas.toDataURL('image/webp').indexOf('data:image/webp') === 0;
  } catch {
    return false;
  }
}

/**
 * Compress an image file, blob, or URL to an ultra-compact binary Blob for Supabase Storage.
 */
export async function compressImageToBlob(
  fileOrUrl: File | Blob | string,
  options: CompressionOptions = {}
): Promise<CompressedBlobResult> {
  const opts = { ...DEFAULT_OPTIONS, ...options };
  const originalSize = typeof fileOrUrl === 'object' && 'size' in fileOrUrl ? (fileOrUrl as Blob).size : 0;

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
        if (typeof fileOrUrl === 'object' && fileOrUrl instanceof Blob) {
          resolve({
            blob: fileOrUrl,
            mimeType: fileOrUrl.type || 'image/jpeg',
            extension: fileOrUrl.type.includes('png') ? 'png' : 'jpg',
            sizeBytes: fileOrUrl.size,
            originalSizeBytes: originalSize,
            width: img.width,
            height: img.height
          });
          return;
        }
        reject(new Error('Failed to create canvas 2d context for image compression'));
        return;
      }

      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = 'high';
      ctx.drawImage(img, 0, 0, width, height);

      const targetFormat = (opts.format === 'image/webp' && isWebPSupported()) ? 'image/webp' : 'image/jpeg';
      const ext = targetFormat === 'image/webp' ? 'webp' : 'jpg';

      canvas.toBlob(
        (blob) => {
          if (!blob) {
            reject(new Error('Canvas toBlob conversion failed'));
            return;
          }
          resolve({
            blob,
            mimeType: targetFormat,
            extension: ext,
            sizeBytes: blob.size,
            originalSizeBytes: originalSize || blob.size,
            width,
            height
          });
        },
        targetFormat,
        opts.quality || 0.82
      );
    };

    img.onerror = (err) => {
      if (objectUrl) URL.revokeObjectURL(objectUrl);
      reject(err);
    };
  });
}

/**
 * Compress an image file or Data URL to optimized WebP/JPEG data URL.
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

      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = 'high';
      ctx.drawImage(img, 0, 0, width, height);

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
      if (typeof fileOrUrl === 'string') {
        resolve(fileOrUrl);
      } else {
        reject(err);
      }
    };
  });
}

/**
 * Check if a given string is a raw Base64 data URL.
 */
export function isBase64DataUrl(str: string): boolean {
  return typeof str === 'string' && str.startsWith('data:');
}

/**
 * Estimate size in bytes of a Base64 data URL.
 */
export function estimateBase64SizeBytes(dataUrl: string): number {
  if (!isBase64DataUrl(dataUrl)) return 0;
  const commaIdx = dataUrl.indexOf(',');
  if (commaIdx === -1) return 0;
  const base64Len = dataUrl.length - (commaIdx + 1);
  return Math.round((base64Len * 3) / 4);
}
