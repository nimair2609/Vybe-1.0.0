import React, { useState, useRef, useEffect } from 'react';
import {
  X, Sparkles, Send, Music, Image as ImageIcon,
  Video, Type, AlertCircle, Users, CheckCircle2,
  RotateCw, ZoomIn, Scissors, Plus, Trash2, Volume2, Play, Square, Crop
} from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import {
  compressImageToBlob,
  compressImageToDataUrl,
  UPLOAD_COMPRESSION_PRESETS
} from '../utils/imageCompressor';
import { uploadMediaBlobToStorage } from '../services/supabaseService';
import { trackDisposableBlobUrl } from '../utils/cacheManager';
import { StoryCropRect, StoryOverlay } from '../types';

type StoryTabMode = 'PHOTO' | 'VIDEO' | 'TEXT';
type AspectPreset = '9:16' | '4:5' | '1:1';

const MAX_STORY_IMAGE_BYTES = 15 * 1024 * 1024;
const MAX_STORY_VIDEO_BYTES = 50 * 1024 * 1024;

const ASPECT_CROP_MAP: Record<AspectPreset, StoryCropRect> = {
  '9:16': { x: 0, y: 0, width: 1, height: 1, aspect: '9:16' },
  '4:5': { x: 0.05, y: 0.08, width: 0.9, height: 0.84, aspect: '4:5' },
  '1:1': { x: 0.08, y: 0.16, width: 0.84, height: 0.68, aspect: '1:1' }
};

export const CreateStoryModal: React.FC = () => {
  const { closeModal, createStory, audioTracks } = useVybe();

  const [mode, setMode] = useState<StoryTabMode>('PHOTO');
  const [caption, setCaption] = useState('');
  const [selectedBg, setSelectedBg] = useState('from-[#6366F1] to-[#EC4899]');
  const [audience, setAudience] = useState<'Public' | 'LINKUPS' | 'VYBE Matches'>('Public');

  // Audio catalog selection
  const [selectedAudioId, setSelectedAudioId] = useState<string>('');
  const [previewPlayingId, setPreviewPlayingId] = useState<string | null>(null);
  const [audioVolume, setAudioVolume] = useState<number>(80);
  const [storyVolume, setStoryVolume] = useState<number>(100);
  const previewAudioRef = useRef<HTMLAudioElement | null>(null);

  // Media + Stories 2.0 transform, crop, trim & overlays
  const [mediaPreviewUrl, setMediaPreviewUrl] = useState<string>('');
  const [selectedBlob, setSelectedBlob] = useState<Blob | null>(null);
  const [selectedFileName, setSelectedFileName] = useState<string>('story.webp');
  const [originalSizeBytes, setOriginalSizeBytes] = useState<number | undefined>(undefined);
  const [compressionSavings, setCompressionSavings] = useState<string | null>(null);
  const [aspectPreset, setAspectPreset] = useState<AspectPreset>('9:16');
  const [scale, setScale] = useState<number>(1);
  const [rotationDeg, setRotationDeg] = useState<number>(0);
  const [videoDurationMs, setVideoDurationMs] = useState<number>(15000);
  const [trimStartMs, setTrimStartMs] = useState<number>(0);
  const [trimEndMs, setTrimEndMs] = useState<number>(15000);
  const [overlays, setOverlays] = useState<StoryOverlay[]>([]);
  const [overlayDraftText, setOverlayDraftText] = useState('');
  const [overlayDraftColor, setOverlayDraftColor] = useState('#FFFFFF');
  const [overlayDraftPos, setOverlayDraftPos] = useState<'TOP' | 'CENTER' | 'BOTTOM'>('TOP');

  const [validationError, setValidationError] = useState<string | null>(null);
  const [isPublishing, setIsPublishing] = useState(false);

  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const selectedTrack = audioTracks.find(t => t.id === selectedAudioId);

  useEffect(() => {
    return () => {
      if (previewAudioRef.current) {
        previewAudioRef.current.pause();
        previewAudioRef.current = null;
      }
    };
  }, []);

  const toggleTrackPreview = (trackId: string, streamUrl: string) => {
    if (previewPlayingId === trackId) {
      previewAudioRef.current?.pause();
      setPreviewPlayingId(null);
      return;
    }
    if (previewAudioRef.current) {
      previewAudioRef.current.pause();
    }
    if (!streamUrl) return;
    const audio = new Audio(streamUrl);
    audio.volume = Math.max(0.1, audioVolume / 100);
    audio.onended = () => setPreviewPlayingId(null);
    audio.play().catch(() => setPreviewPlayingId(null));
    previewAudioRef.current = audio;
    setPreviewPlayingId(trackId);
  };

  const bgGradients = [
    'from-[#6366F1] to-[#EC4899]',
    'from-[#00C6FF] to-[#0072FF]',
    'from-[#F59E0B] to-[#FBBF24]',
    'from-[#10B981] to-[#3B82F6]',
    'from-[#F43F5E] to-[#FB7185]',
    'from-[#060714] to-[#1A1F36]'
  ];

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    setValidationError(null);
    setCompressionSavings(null);
    setSelectedBlob(null);
    setOriginalSizeBytes(undefined);
    setScale(1);
    setRotationDeg(0);
    if (!file) return;

    const isImage = file.type.startsWith('image/');
    const isVideo = file.type.startsWith('video/');

    if (mode === 'PHOTO' && !isImage) {
      setValidationError('Please select a valid image file (JPEG, PNG, WebP).');
      return;
    }
    if (mode === 'VIDEO' && !isVideo) {
      setValidationError('Please select a valid video file (MP4, WebM, MOV).');
      return;
    }
    if (isImage && file.size > MAX_STORY_IMAGE_BYTES) {
      setValidationError('Image exceeds the 15 MB story limit.');
      return;
    }
    if (isVideo && file.size > MAX_STORY_VIDEO_BYTES) {
      setValidationError('Video exceeds the 50 MB story limit.');
      return;
    }

    if (isImage) {
      try {
        const res = await compressImageToBlob(file, UPLOAD_COMPRESSION_PRESETS.STORY);
        setSelectedBlob(res.blob);
        setSelectedFileName(file.name.replace(/\.[^/.]+$/, '') + `.${res.extension}`);
        setOriginalSizeBytes(res.originalSizeBytes);

        const origKb = Math.round(res.originalSizeBytes / 1024);
        const newKb = Math.round(res.sizeBytes / 1024);
        const pct = Math.max(0, Math.round(((res.originalSizeBytes - res.sizeBytes) / (res.originalSizeBytes || 1)) * 100));
        setCompressionSavings(`${origKb} KB -> ${newKb} KB (-${pct}%) WebP`);

        const previewObjUrl = URL.createObjectURL(res.blob);
        trackDisposableBlobUrl(previewObjUrl);
        setMediaPreviewUrl(previewObjUrl);
      } catch {
        const fallbackDataUrl = await compressImageToDataUrl(file, UPLOAD_COMPRESSION_PRESETS.STORY);
        setMediaPreviewUrl(fallbackDataUrl);
      }
      return;
    }

    setSelectedBlob(file);
    setSelectedFileName(file.name || 'story_video.mp4');
    setOriginalSizeBytes(file.size);
    const previewObjUrl = URL.createObjectURL(file);
    trackDisposableBlobUrl(previewObjUrl);
    setMediaPreviewUrl(previewObjUrl);
  };

  const handleAddOverlay = () => {
    const text = overlayDraftText.trim();
    if (!text) return;
    const y = overlayDraftPos === 'TOP' ? 0.18 : overlayDraftPos === 'CENTER' ? 0.5 : 0.78;
    setOverlays(prev => [
      ...prev,
      {
        id: `ov_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
        type: 'TEXT',
        text,
        x: 0.5,
        y,
        color: overlayDraftColor,
        scale: 1
      }
    ]);
    setOverlayDraftText('');
  };

  const publishWithAudience = async (targetAudience: 'Public' | 'LINKUPS' | 'VYBE Matches') => {
    setValidationError(null);
    if (previewAudioRef.current) {
      previewAudioRef.current.pause();
      setPreviewPlayingId(null);
    }

    if (mode === 'TEXT' && !caption.trim()) {
      setValidationError('Please enter story text before publishing.');
      return;
    }
    if ((mode === 'PHOTO' || mode === 'VIDEO') && !mediaPreviewUrl) {
      setValidationError(`Please select a ${mode === 'PHOTO' ? 'photo' : 'video'} to publish your story.`);
      return;
    }

    setIsPublishing(true);
    try {
      let resolvedMediaUrl = mode === 'TEXT' ? '' : mediaPreviewUrl;
      if (mode !== 'TEXT' && selectedBlob) {
        const uploadRes = await uploadMediaBlobToStorage(
          selectedBlob,
          'stories',
          selectedFileName,
          originalSizeBytes,
          { isPrivate: targetAudience !== 'Public' }
        );
        if (!uploadRes?.publicUrl) {
          setValidationError('Could not upload story media to cloud storage. Please retry.');
          setIsPublishing(false);
          return;
        }
        resolvedMediaUrl = uploadRes.publicUrl;
      }

      const ok = await createStory({
        caption: caption.trim(),
        mediaUrl: resolvedMediaUrl,
        mediaType: mode,
        audience: targetAudience,
        textBackgroundColor: mode === 'TEXT' ? selectedBg : undefined,
        musicTrackTitle: selectedTrack ? `${selectedTrack.title} · ${selectedTrack.artist}` : undefined,
        audioTrackId: selectedTrack?.id,
        audioTrackUrl: selectedTrack?.streamUrl,
        cropRect: mode !== 'TEXT' ? ASPECT_CROP_MAP[aspectPreset] : undefined,
        transform: mode !== 'TEXT' ? { scale, rotationDeg } : undefined,
        trimStartMs: mode === 'VIDEO' ? trimStartMs : undefined,
        trimEndMs: mode === 'VIDEO' ? trimEndMs : undefined,
        overlays: overlays.length > 0 ? overlays : undefined,
        audioVolume: selectedTrack ? audioVolume / 100 : undefined,
        storyVolume: mode === 'VIDEO' ? storyVolume / 100 : undefined
      });
      if (!ok) {
        setValidationError('Story could not be saved right now. Please try again.');
        setIsPublishing(false);
      }
    } catch (err: any) {
      setValidationError(err?.message || 'Failed to publish story.');
      setIsPublishing(false);
    }
  };

  const canSubmit = !isPublishing && (mode === 'TEXT' ? caption.trim().length > 0 : Boolean(mediaPreviewUrl));

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-4 bg-black/85 backdrop-blur-md animate-fade-in">
      <div className="relative w-full max-w-md max-h-[92dvh] flex flex-col bg-[#101322] border border-[#262E4A] rounded-3xl shadow-2xl overflow-hidden text-white">
        {/* Top Header */}
        <div className="flex items-center justify-between px-5 py-3.5 border-b border-[#262E4A] shrink-0">
          <h3 className="font-bold text-sm text-white flex items-center gap-2">
            <Sparkles className="w-4 h-4 text-[#D946EF]" />
            Create 24h Story
          </h3>
          <button
            type="button"
            onClick={closeModal}
            aria-label="Close story creator"
            className="p-1.5 rounded-lg text-gray-400 hover:text-white"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Scrollable Body */}
        <div className="flex-1 overflow-y-auto px-5 py-4 space-y-3.5 no-scrollbar">
          {/* Mode Switcher */}
          <div className="grid grid-cols-3 gap-1.5 bg-[#060714] p-1 rounded-xl border border-[#262E4A]">
            {([
              { id: 'PHOTO', label: 'Photo', icon: ImageIcon },
              { id: 'VIDEO', label: 'Video', icon: Video },
              { id: 'TEXT', label: 'Text', icon: Type },
            ] as const).map(tab => {
              const IconComp = tab.icon;
              const active = mode === tab.id;
              return (
                <button
                  key={tab.id}
                  type="button"
                  onClick={() => {
                    setMode(tab.id);
                    setMediaPreviewUrl('');
                    setValidationError(null);
                  }}
                  className={`flex items-center justify-center gap-1.5 py-2 rounded-lg text-xs font-bold transition-all ${
                    active
                      ? 'bg-gradient-to-r from-[#8B5CF6] to-[#00C6FF] text-white keep-white shadow'
                      : 'text-gray-400 hover:text-white'
                  }`}
                >
                  <IconComp className="w-3.5 h-3.5" />
                  <span>{tab.label}</span>
                </button>
              );
            })}
          </div>

          {/* Live Story Preview Stage */}
          <div
            className={`relative h-60 rounded-2xl overflow-hidden flex flex-col justify-between shadow-xl transition-all ${
              mode === 'TEXT' ? `bg-gradient-to-tr ${selectedBg} p-5` : 'bg-[#060714] border border-[#262E4A]'
            }`}
          >
            {mode === 'TEXT' ? (
              <>
                <div className="flex items-center justify-between text-xs text-white/85 keep-white">
                  <span className="font-semibold">Story Preview</span>
                  <span className="px-2 py-0.5 rounded-full bg-black/30 text-[10px]">{audience}</span>
                </div>
                <p className="text-center font-bold text-sm text-white keep-white px-2 leading-relaxed">
                  {caption || 'Type your story caption below...'}
                </p>
                <div className="flex items-center gap-1.5 text-[11px] text-white/90 keep-white">
                  <Music className="w-3.5 h-3.5" />
                  <span className="truncate">{selectedTrack ? `${selectedTrack.title} · ${selectedTrack.artist}` : 'No background track'}</span>
                </div>
              </>
            ) : mediaPreviewUrl ? (
              <div className="relative w-full h-full flex items-center justify-center overflow-hidden bg-black">
                {mode === 'VIDEO' ? (
                  <video
                    src={mediaPreviewUrl}
                    controls
                    playsInline
                    onLoadedMetadata={(e) => {
                      const durMs = Math.max(1000, Math.round((e.currentTarget.duration || 15) * 1000));
                      setVideoDurationMs(durMs);
                      setTrimStartMs(0);
                      setTrimEndMs(durMs);
                    }}
                    style={{ transform: `scale(${scale}) rotate(${rotationDeg}deg)` }}
                    className="w-full h-full object-cover transition-transform"
                  />
                ) : (
                  <img
                    src={mediaPreviewUrl}
                    alt="Story preview"
                    style={{ transform: `scale(${scale}) rotate(${rotationDeg}deg)` }}
                    className="w-full h-full object-cover transition-transform"
                  />
                )}
                <button
                  type="button"
                  onClick={() => fileInputRef.current?.click()}
                  className="absolute bottom-3 right-3 z-20 px-3 py-1.5 rounded-xl bg-black/75 text-white keep-white text-xs font-semibold backdrop-blur-md"
                >
                  Change {mode === 'VIDEO' ? 'Video' : 'Photo'}
                </button>
              </div>
            ) : (
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                className="w-full h-full flex flex-col items-center justify-center gap-2 text-gray-400 hover:text-white transition-colors p-4"
              >
                {mode === 'VIDEO' ? (
                  <Video className="w-9 h-9 text-[#00C6FF]" />
                ) : (
                  <ImageIcon className="w-9 h-9 text-[#8B5CF6]" />
                )}
                <span className="text-xs font-bold">
                  Select {mode === 'VIDEO' ? 'Video (MP4/WebM)' : 'Photo (JPG/PNG/WebP)'}
                </span>
                <span className="text-[11px] text-gray-400">
                  Visible to {audience} for 24 hours
                </span>
              </button>
            )}

            {/* Render Overlays on Preview */}
            {overlays.map(ov => (
              <div
                key={ov.id}
                style={{
                  top: `${Math.round(ov.y * 100)}%`,
                  left: '50%',
                  transform: 'translate(-50%, -50%)',
                  color: ov.color || '#FFFFFF'
                }}
                className="absolute z-10 px-2.5 py-1 rounded-lg bg-black/55 backdrop-blur-sm text-xs font-extrabold pointer-events-none shadow"
              >
                {ov.text}
              </div>
            ))}

            <input
              ref={fileInputRef}
              type="file"
              accept={mode === 'VIDEO' ? 'video/*' : 'image/*'}
              onChange={handleFileChange}
              className="hidden"
            />
          </div>

          {/* Gradient Picker for Text Stories */}
          {mode === 'TEXT' && (
            <div className="flex items-center justify-center gap-2 pt-0.5">
              {bgGradients.map((grad, i) => (
                <button
                  key={i}
                  type="button"
                  onClick={() => setSelectedBg(grad)}
                  aria-label={`Select background style ${i + 1}`}
                  className={`w-7 h-7 rounded-full bg-gradient-to-tr ${grad} border-2 ${
                    selectedBg === grad ? 'border-white scale-110' : 'border-transparent'
                  }`}
                />
              ))}
            </div>
          )}

          {/* Stories 2.0 Transform & Crop Controls (Photo/Video) */}
          {mode !== 'TEXT' && mediaPreviewUrl && (
            <div className="p-3 rounded-2xl bg-[#0B0E1D] border border-[#262E4A] space-y-2.5">
              <div className="flex items-center justify-between flex-wrap gap-2">
                <span className="text-[11px] font-bold text-gray-300 flex items-center gap-1.5">
                  <Crop className="w-3.5 h-3.5 text-[#00C6FF]" /> Frame & Transform
                </span>
                <div className="flex items-center gap-1">
                  {(['9:16', '4:5', '1:1'] as AspectPreset[]).map(preset => (
                    <button
                      key={preset}
                      type="button"
                      onClick={() => setAspectPreset(preset)}
                      className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                        aspectPreset === preset ? 'bg-[#8B5CF6] text-white' : 'bg-[#1A1F36] text-gray-400'
                      }`}
                    >
                      {preset}
                    </button>
                  ))}
                  <button
                    type="button"
                    onClick={() => setRotationDeg(prev => (prev + 90) % 360)}
                    className="px-2 py-0.5 rounded bg-[#1A1F36] text-gray-200 text-[10px] font-bold inline-flex items-center gap-1"
                  >
                    <RotateCw className="w-3 h-3" /> {rotationDeg}°
                  </button>
                </div>
              </div>

              <label className="flex items-center gap-2 text-[11px] text-gray-300">
                <ZoomIn className="w-3.5 h-3.5 text-[#8B5CF6] shrink-0" />
                <span className="w-10">Zoom</span>
                <input
                  type="range"
                  min={1}
                  max={2.4}
                  step={0.05}
                  value={scale}
                  onChange={e => setScale(Number(e.target.value))}
                  className="flex-1 accent-[#8B5CF6]"
                />
                <span className="w-9 text-right font-mono">{scale.toFixed(1)}x</span>
              </label>

              {mode === 'VIDEO' && (
                <div className="space-y-1.5 pt-1 border-t border-[#262E4A]">
                  <span className="text-[11px] font-bold text-gray-300 flex items-center gap-1.5">
                    <Scissors className="w-3.5 h-3.5 text-[#D946EF]" /> Trim ({(trimStartMs / 1000).toFixed(1)}s – {(trimEndMs / 1000).toFixed(1)}s)
                  </span>
                  <div className="grid grid-cols-2 gap-2">
                    <input
                      type="range"
                      min={0}
                      max={Math.max(500, trimEndMs - 500)}
                      step={250}
                      value={trimStartMs}
                      onChange={e => setTrimStartMs(Number(e.target.value))}
                      className="accent-[#00C6FF]"
                    />
                    <input
                      type="range"
                      min={Math.min(videoDurationMs, trimStartMs + 500)}
                      max={videoDurationMs}
                      step={250}
                      value={trimEndMs}
                      onChange={e => setTrimEndMs(Number(e.target.value))}
                      className="accent-[#D946EF]"
                    />
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Text / Sticker Overlays */}
          <div className="p-3 rounded-2xl bg-[#0B0E1D] border border-[#262E4A] space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-[11px] font-bold text-gray-300">Stickers & Text Overlays</span>
              <select
                value={overlayDraftPos}
                onChange={e => setOverlayDraftPos(e.target.value as any)}
                className="bg-[#1A1F36] border border-[#262E4A] rounded px-2 py-0.5 text-[10px] text-white"
              >
                <option value="TOP">Top</option>
                <option value="CENTER">Center</option>
                <option value="BOTTOM">Bottom</option>
              </select>
            </div>
            <div className="flex items-center gap-1.5">
              <input
                type="color"
                value={overlayDraftColor}
                onChange={e => setOverlayDraftColor(e.target.value)}
                aria-label="Overlay text color"
                className="w-7 h-7 rounded bg-transparent border-0 cursor-pointer shrink-0"
              />
              <input
                type="text"
                value={overlayDraftText}
                onChange={e => setOverlayDraftText(e.target.value)}
                placeholder="Add floating sticker or text..."
                className="flex-1 px-2.5 py-1.5 bg-[#060714] border border-[#262E4A] rounded-lg text-xs text-white focus:outline-none"
              />
              <button
                type="button"
                onClick={handleAddOverlay}
                disabled={!overlayDraftText.trim()}
                className="p-1.5 rounded-lg bg-[#8B5CF6] text-white disabled:opacity-40"
                aria-label="Add overlay"
              >
                <Plus className="w-4 h-4" />
              </button>
            </div>
            {overlays.length > 0 && (
              <div className="flex flex-wrap gap-1.5 pt-1">
                {overlays.map(ov => (
                  <span
                    key={ov.id}
                    className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-[#1A1F36] text-[10px] text-white"
                  >
                    <span>{ov.text}</span>
                    <button
                      type="button"
                      onClick={() => setOverlays(prev => prev.filter(item => item.id !== ov.id))}
                      aria-label="Remove overlay"
                      className="text-gray-400 hover:text-red-400"
                    >
                      <Trash2 className="w-3 h-3" />
                    </button>
                  </span>
                ))}
              </div>
            )}
          </div>

          {/* Live Audio Catalog & Volume Mix */}
          <div className="p-3 rounded-2xl bg-[#0B0E1D] border border-[#262E4A] space-y-2">
            <div className="flex items-center justify-between gap-2">
              <span className="text-[11px] font-bold text-gray-300 flex items-center gap-1.5">
                <Music className="w-3.5 h-3.5 text-[#00C6FF]" /> VYBE Audio Catalog
              </span>
              {selectedTrack && selectedTrack.streamUrl && (
                <button
                  type="button"
                  onClick={() => toggleTrackPreview(selectedTrack.id, selectedTrack.streamUrl)}
                  className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-[#1A1F36] text-[10px] font-bold text-[#00C6FF]"
                >
                  {previewPlayingId === selectedTrack.id ? <Square className="w-2.5 h-2.5" /> : <Play className="w-2.5 h-2.5" />}
                  <span>{previewPlayingId === selectedTrack.id ? 'Stop' : 'Preview'}</span>
                </button>
              )}
            </div>

            <select
              value={selectedAudioId}
              onChange={e => setSelectedAudioId(e.target.value)}
              className="w-full bg-[#060714] border border-[#262E4A] rounded-lg px-2.5 py-2 text-xs text-white focus:outline-none focus:border-[#8B5CF6]"
            >
              <option value="">No background track</option>
              {audioTracks.map(track => (
                <option key={track.id} value={track.id}>
                  {track.title} — {track.artist} ({track.durationSec}s)
                </option>
              ))}
            </select>

            {audioTracks.length === 0 && (
              <p className="text-[10px] text-gray-400">
                No audio tracks published yet in Supabase catalog. Admins can add tracks in Super Admin.
              </p>
            )}

            {(selectedTrack || mode === 'VIDEO') && (
              <div className="space-y-1.5 pt-1 border-t border-[#262E4A]">
                {selectedTrack && (
                  <label className="flex items-center gap-2 text-[10px] text-gray-300">
                    <Volume2 className="w-3.5 h-3.5 text-[#00C6FF] shrink-0" />
                    <span className="w-16">Track Vol</span>
                    <input
                      type="range"
                      min={0}
                      max={100}
                      value={audioVolume}
                      onChange={e => setAudioVolume(Number(e.target.value))}
                      className="flex-1 accent-[#00C6FF]"
                    />
                    <span className="w-8 text-right">{audioVolume}%</span>
                  </label>
                )}
                {mode === 'VIDEO' && (
                  <label className="flex items-center gap-2 text-[10px] text-gray-300">
                    <Volume2 className="w-3.5 h-3.5 text-[#8B5CF6] shrink-0" />
                    <span className="w-16">Video Vol</span>
                    <input
                      type="range"
                      min={0}
                      max={100}
                      value={storyVolume}
                      onChange={e => setStoryVolume(Number(e.target.value))}
                      className="flex-1 accent-[#8B5CF6]"
                    />
                    <span className="w-8 text-right">{storyVolume}%</span>
                  </label>
                )}
              </div>
            )}
          </div>

          {compressionSavings && !validationError && (
            <div className="p-2 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-300 text-xs flex items-center gap-2">
              <CheckCircle2 className="w-4 h-4 shrink-0 text-emerald-400" />
              <span>Compressed: {compressionSavings}</span>
            </div>
          )}

          {validationError && (
            <div className="p-2.5 rounded-xl bg-red-500/15 border border-red-500/40 text-red-300 text-xs flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0 text-red-400" />
              <span>{validationError}</span>
            </div>
          )}

          <input
            type="text"
            value={caption}
            onChange={(e) => {
              setCaption(e.target.value);
              setValidationError(null);
            }}
            placeholder={mode === 'TEXT' ? 'Share a quick thought or question...' : 'Add an optional caption...'}
            className="w-full px-3.5 py-2.5 bg-[#060714] border border-[#262E4A] rounded-xl text-xs text-white focus:outline-none focus:border-[#8B5CF6]"
          />

          <div className="flex items-center justify-between text-xs text-gray-300">
            <span>Default Audience:</span>
            <select
              value={audience}
              onChange={(e) => setAudience(e.target.value as any)}
              className="bg-[#060714] border border-[#262E4A] rounded-lg px-2.5 py-1.5 text-xs text-white focus:outline-none"
            >
              <option value="Public">Public</option>
              <option value="LINKUPS">LINKUPS</option>
              <option value="VYBE Matches">VYBE Matches</option>
            </select>
          </div>
        </div>

        {/* Bottom Action Bar */}
        <div
          className="shrink-0 px-5 pt-3 pb-[max(0.85rem,env(safe-area-inset-bottom))] bg-[#0B0E1D] border-t border-[#262E4A] grid grid-cols-2 gap-2.5"
        >
          <button
            type="button"
            disabled={!canSubmit}
            onClick={() => publishWithAudience('VYBE Matches')}
            className="min-h-[48px] px-3 rounded-xl bg-[#1A1F36] hover:bg-[#262E4A] border border-[#8B5CF6]/50 disabled:opacity-40 text-xs font-bold text-white flex items-center justify-center gap-1.5 transition-colors"
          >
            <Users className="w-4 h-4 text-[#D946EF] shrink-0" />
            <span className="truncate">Matches</span>
          </button>

          <button
            type="button"
            disabled={!canSubmit}
            onClick={() => publishWithAudience(audience)}
            className="min-h-[48px] px-3 rounded-xl bg-gradient-to-r from-[#00C6FF] via-[#8B5CF6] to-[#D946EF] disabled:opacity-40 text-white keep-white font-bold text-xs shadow-lg flex items-center justify-center gap-1.5 transition-opacity"
          >
            <Send className="w-4 h-4 shrink-0" />
            <span className="truncate">{isPublishing ? 'Sharing...' : 'Share Story'}</span>
          </button>
        </div>
      </div>
    </div>
  );
};
