import React, { useState } from 'react';
import { 
  X, Image, Music, MapPin, Sparkles, Send, Upload, Loader2, CheckCircle2, AlertCircle 
} from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { MOOD_DEFINITIONS, MoodType } from '../types';
import { compressImageToBlob, compressImageToDataUrl } from '../utils/imageCompressor';
import { uploadMediaBlobToStorage } from '../services/supabaseService';

export const CreatePostModal: React.FC = () => {
  const { closeModal, createPost, currentUser } = useVybe();

  const [caption, setCaption] = useState('');
  const [selectedMood, setSelectedMood] = useState<MoodType>(currentUser?.currentMood || 'GENERAL');
  const [imageUrl, setImageUrl] = useState('');
  const [selectedMediaType, setSelectedMediaType] = useState<'IMAGE' | 'VIDEO'>('IMAGE');
  const [selectedBlob, setSelectedBlob] = useState<Blob | null>(null);
  const [blobFileName, setBlobFileName] = useState<string>('photo.webp');
  const [originalSizeBytes, setOriginalSizeBytes] = useState<number | undefined>(undefined);
  const [compressionSavings, setCompressionSavings] = useState<string | null>(null);
  const [locationName, setLocationName] = useState('');
  const [audioTrackTitle, setAudioTrackTitle] = useState('');
  const [tagsInput, setTagsInput] = useState('');
  const [isCompressing, setIsCompressing] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);

  const handleFileSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploadError(null);

    if (file.type.startsWith('video/')) {
      setSelectedMediaType('VIDEO');
      setSelectedBlob(file);
      setOriginalSizeBytes(file.size);
      setBlobFileName(file.name);
      const kb = Math.round(file.size / 1024);
      setCompressionSavings(`Video ready (${kb > 1024 ? `${(kb / 1024).toFixed(1)} MB` : `${kb} KB`})`);
      setImageUrl(URL.createObjectURL(file));
      return;
    }

    setSelectedMediaType('IMAGE');
    try {
      setIsCompressing(true);
      setCompressionSavings(null);
      // Client-side WebP binary compression (90%+ egress reduction)
      const res = await compressImageToBlob(file, {
        maxWidth: 1440,
        maxHeight: 1440,
        quality: 0.82,
        format: 'image/webp'
      });

      setSelectedBlob(res.blob);
      setOriginalSizeBytes(res.originalSizeBytes);
      setBlobFileName(file.name.replace(/\.[^/.]+$/, '') + '.webp');
      
      const origKb = Math.round(res.originalSizeBytes / 1024);
      const newKb = Math.round(res.sizeBytes / 1024);
      const pct = Math.max(0, Math.round(((res.originalSizeBytes - res.sizeBytes) / (res.originalSizeBytes || 1)) * 100));
      setCompressionSavings(`${origKb} KB → ${newKb} KB (-${pct}%) WebP`);

      // Create preview URL
      const previewUrl = URL.createObjectURL(res.blob);
      setImageUrl(previewUrl);
    } catch (err) {
      console.warn('Image compression fallback:', err);
      try {
        const fallbackUrl = await compressImageToDataUrl(file);
        setImageUrl(fallbackUrl);
      } catch {
        const reader = new FileReader();
        reader.onload = () => setImageUrl(reader.result as string);
        reader.readAsDataURL(file);
      }
    } finally {
      setIsCompressing(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!caption.trim() && !imageUrl) return;
    setUploadError(null);

    let finalMediaUrl = imageUrl;

    // If a photo/video blob was selected, upload directly to Supabase storage bucket 'posts'
    if (selectedBlob) {
      setIsUploading(true);
      try {
        const uploadResult = await uploadMediaBlobToStorage(
          selectedBlob,
          'posts',
          blobFileName,
          originalSizeBytes,
          { isPrivate: Boolean(currentUser?.isPrivate) }
        );
        if (!uploadResult?.publicUrl) {
          setUploadError('Could not upload media to cloud storage. Check your connection and try again.');
          return;
        }
        finalMediaUrl = uploadResult.publicUrl;
      } catch (err: any) {
        setUploadError(err?.message || 'Media upload failed. Please retry.');
        return;
      } finally {
        setIsUploading(false);
      }
    }

    const tags = tagsInput
      .split(/[\s,#]+/)
      .map(t => t.trim().toLowerCase())
      .filter(Boolean);

    const inferredMediaType = finalMediaUrl
      ? (selectedMediaType === 'VIDEO' || /\.(mp4|webm|mov|m4v)(\?|$)/i.test(finalMediaUrl) ? 'VIDEO' : 'IMAGE')
      : 'TEXT';

    const ok = await createPost({
      caption: caption.trim(),
      mediaUrls: finalMediaUrl ? [finalMediaUrl] : [],
      mediaType: inferredMediaType,
      isVybesReel: false,
      moodZone: selectedMood,
      locationName: locationName.trim() || undefined,
      audioTrackTitle: audioTrackTitle.trim() || undefined,
      tags
    });
    if (!ok) {
      setUploadError('Post could not be published right now. Please try again.');
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-fade-in"
      onClick={closeModal}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Create New VYBE Moment"
        onClick={(e) => e.stopPropagation()}
        className="relative w-full max-w-xl max-h-[90vh] overflow-y-auto bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-3xl shadow-2xl p-6 text-[var(--vybe-text)] no-scrollbar space-y-5"
      >
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-[var(--vybe-border)]">
          <div className="flex items-center gap-2">
            <Sparkles className="w-5 h-5 text-[var(--vybe-accent)]" />
            <h2 className="text-base font-bold brand-font text-[var(--vybe-text)]">Create New VYBE Moment</h2>
          </div>
          <button
            type="button"
            onClick={closeModal}
            aria-label="Close composer"
            className="p-2 text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)] rounded-lg hover:bg-[var(--vybe-elevated)]"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Caption */}
          <textarea
            rows={3}
            value={caption}
            onChange={(e) => setCaption(e.target.value)}
            placeholder="What emotion or frequency are you experiencing right now?"
            className="w-full p-3.5 bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] rounded-2xl text-xs text-[var(--vybe-text)] placeholder-[var(--vybe-text-muted)] focus:outline-none focus:border-[var(--vybe-accent)] resize-none leading-relaxed"
          />

          {/* Mood Zone Selector */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-[var(--vybe-text-secondary)]">
              Choose Mood Frequency
            </label>
            <div className="grid grid-cols-3 sm:grid-cols-6 gap-1.5">
              {(Object.keys(MOOD_DEFINITIONS) as MoodType[]).map(m => {
                const item = MOOD_DEFINITIONS[m];
                const isSelected = selectedMood === m;
                return (
                  <button
                    key={m}
                    type="button"
                    onClick={() => setSelectedMood(m)}
                    className={`p-2 rounded-xl text-center text-xs transition-all border ${
                      isSelected
                        ? 'bg-gradient-to-r ' + item.gradient + ' text-white keep-white font-bold border-transparent shadow'
                        : 'bg-[var(--vybe-elevated)] border-[var(--vybe-border)] text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)]'
                    }`}
                  >
                    <span className="block text-base">{item.emoji}</span>
                    <span className="text-[10px] truncate block">{item.title}</span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Image URL, File Upload & Quick Picker */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <label className="text-xs font-semibold text-[var(--vybe-text-secondary)] flex items-center gap-1.5">
                <Image className="w-3.5 h-3.5 text-[var(--vybe-accent)]" />
                Media Attachment (Auto-Compressed)
              </label>
              <label className="cursor-pointer text-[11px] font-semibold text-[var(--vybe-accent)] flex items-center gap-1 px-2.5 py-1 bg-[var(--vybe-elevated)] rounded-lg border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] transition-all">
                {isCompressing ? (
                  <>
                    <Loader2 className="w-3 h-3 animate-spin" />
                    <span>Compressing...</span>
                  </>
                ) : (
                  <>
                    <Upload className="w-3 h-3" />
                    <span>Upload Photo / Video</span>
                  </>
                )}
                <input
                  type="file"
                  accept="image/*,video/*"
                  onChange={handleFileSelect}
                  disabled={isCompressing}
                  className="hidden"
                />
              </label>
            </div>
            <input
              type="text"
              value={imageUrl}
              onChange={(e) => {
                setImageUrl(e.target.value);
                setSelectedBlob(null);
                setSelectedMediaType(/\.(mp4|webm|mov|m4v)(\?|$)/i.test(e.target.value) ? 'VIDEO' : 'IMAGE');
              }}
              placeholder="Or paste direct image or video URL..."
              className="w-full px-3 py-2 bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] rounded-xl text-xs text-[var(--vybe-text)] placeholder-[var(--vybe-text-muted)] focus:outline-none focus:border-[var(--vybe-accent)]"
            />
            {imageUrl && (
              <div className="space-y-1.5">
                <div className="relative w-full max-h-40 rounded-xl overflow-hidden border border-[var(--vybe-border)] bg-[var(--vybe-elevated)] flex items-center justify-center">
                  {selectedMediaType === 'VIDEO' ? (
                    <video src={imageUrl} controls className="max-h-40 w-full object-contain" />
                  ) : (
                    <img src={imageUrl} alt="preview" className="max-h-40 object-contain" />
                  )}
                  <button
                    type="button"
                    onClick={() => {
                      setImageUrl('');
                      setSelectedBlob(null);
                      setSelectedMediaType('IMAGE');
                      setCompressionSavings(null);
                    }}
                    className="absolute top-2 right-2 p-1 rounded-full bg-black/70 hover:bg-black text-white keep-white text-xs"
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                </div>
                {compressionSavings && (
                  <div className="flex items-center gap-1.5 text-[11px] text-emerald-600 dark:text-emerald-400 bg-emerald-500/12 border border-emerald-500/25 px-2.5 py-1 rounded-lg">
                    <CheckCircle2 className="w-3.5 h-3.5" />
                    <span>Optimized: {compressionSavings}</span>
                  </div>
                )}
              </div>
            )}
          </div>

          {/* Audio Tag & Location */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="text-xs font-semibold text-[var(--vybe-text-secondary)] flex items-center gap-1 mb-1">
                <Music className="w-3.5 h-3.5 text-[var(--vybe-accent)]" />
                Music Track Title
              </label>
              <input
                type="text"
                value={audioTrackTitle}
                onChange={(e) => setAudioTrackTitle(e.target.value)}
                placeholder="e.g. Midnight Drive — Kavinsky"
                className="w-full px-3 py-2 bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] rounded-xl text-xs text-[var(--vybe-text)] placeholder-[var(--vybe-text-muted)] focus:outline-none focus:border-[var(--vybe-accent)]"
              />
            </div>
            <div>
              <label className="text-xs font-semibold text-[var(--vybe-text-secondary)] flex items-center gap-1 mb-1">
                <MapPin className="w-3.5 h-3.5 text-amber-500" />
                Location
              </label>
              <input
                type="text"
                value={locationName}
                onChange={(e) => setLocationName(e.target.value)}
                placeholder="e.g. Bandra West, Mumbai"
                className="w-full px-3 py-2 bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] rounded-xl text-xs text-[var(--vybe-text)] placeholder-[var(--vybe-text-muted)] focus:outline-none focus:border-[var(--vybe-accent)]"
              />
            </div>
          </div>

          {/* Tags */}
          <div>
            <label className="text-xs font-semibold text-[var(--vybe-text-secondary)] block mb-1">Hashtags</label>
            <input
              type="text"
              value={tagsInput}
              onChange={(e) => setTagsInput(e.target.value)}
              placeholder="e.g. chillvibes, lofi, mumbai"
              className="w-full px-3 py-2 bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] rounded-xl text-xs text-[var(--vybe-text)] placeholder-[var(--vybe-text-muted)] focus:outline-none focus:border-[var(--vybe-accent)]"
            />
          </div>

          {uploadError && (
            <div className="p-2.5 rounded-xl bg-red-500/15 border border-red-500/40 text-red-400 text-xs flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{uploadError}</span>
            </div>
          )}

          {/* Submit */}
          <button
            type="submit"
            disabled={isCompressing || isUploading || (!caption.trim() && !imageUrl)}
            className="w-full py-3 bg-[var(--vybe-accent)] disabled:opacity-50 disabled:cursor-not-allowed text-white keep-white font-bold text-xs rounded-xl shadow-sm hover:opacity-95 transition-opacity flex items-center justify-center gap-2"
          >
            {isUploading ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin" />
                <span>Securing Media to Cloud...</span>
              </>
            ) : isCompressing ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin" />
                <span>Compressing Photo...</span>
              </>
            ) : (
              <>
                <Send className="w-4 h-4" />
                <span>Publish Vibe Moment</span>
              </>
            )}
          </button>

        </form>

      </div>
    </div>
  );
};
