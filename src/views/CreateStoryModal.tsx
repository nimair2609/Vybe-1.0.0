import React, { useState, useRef } from 'react';
import {
  X, Sparkles, Send, Music, Image as ImageIcon,
  Video, Type, AlertCircle, Users
} from 'lucide-react';
import { useVybe } from '../context/VybeContext';

type StoryTabMode = 'PHOTO' | 'VIDEO' | 'TEXT';

const MAX_STORY_IMAGE_BYTES = 15 * 1024 * 1024;
const MAX_STORY_VIDEO_BYTES = 50 * 1024 * 1024;

export const CreateStoryModal: React.FC = () => {
  const { closeModal, createStory } = useVybe();

  const [mode, setMode] = useState<StoryTabMode>('PHOTO');
  const [caption, setCaption] = useState('');
  const [selectedBg, setSelectedBg] = useState('from-[#6366F1] to-[#EC4899]');
  const [audience, setAudience] = useState<'Public' | 'Followers' | 'VYBE Matches'>('Public');
  const [musicTrack] = useState('Midnight Vibe Lo-Fi');
  const [mediaPreviewUrl, setMediaPreviewUrl] = useState<string>('');
  const [validationError, setValidationError] = useState<string | null>(null);
  const [isPublishing, setIsPublishing] = useState(false);

  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const bgGradients = [
    'from-[#6366F1] to-[#EC4899]',
    'from-[#00C6FF] to-[#0072FF]',
    'from-[#F59E0B] to-[#FBBF24]',
    'from-[#10B981] to-[#3B82F6]',
    'from-[#F43F5E] to-[#FB7185]',
    'from-[#060714] to-[#1A1F36]'
  ];

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    setValidationError(null);
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

    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        setMediaPreviewUrl(reader.result);
      }
    };
    reader.onerror = () => {
      setValidationError('Could not read the selected file. Please try another file.');
    };
    reader.readAsDataURL(file);
  };

  const publishWithAudience = (targetAudience: 'Public' | 'Followers' | 'VYBE Matches') => {
    setValidationError(null);

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
      createStory({
        caption: caption.trim(),
        mediaUrl: mode === 'TEXT' ? '' : mediaPreviewUrl,
        mediaType: mode,
        audience: targetAudience,
        textBackgroundColor: mode === 'TEXT' ? selectedBg : undefined,
        musicTrackTitle: musicTrack
      });
    } catch (err: any) {
      setValidationError(err?.message || 'Failed to publish story.');
      setIsPublishing(false);
    }
  };

  const canSubmit = !isPublishing && (mode === 'TEXT' ? caption.trim().length > 0 : Boolean(mediaPreviewUrl));

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-4 bg-black/85 backdrop-blur-md animate-fade-in">
      <div className="relative w-full max-w-sm max-h-[92dvh] flex flex-col bg-[#101322] border border-[#262E4A] rounded-3xl shadow-2xl overflow-hidden text-white">
        {/* Top Header */}
        <div className="flex items-center justify-between px-5 py-3.5 border-b border-[#262E4A] shrink-0">
          <h3 className="font-bold text-sm text-white flex items-center gap-2">
            <Sparkles className="w-4 h-4 text-[#D946EF]" />
            Add to 24h Story
          </h3>
          <button
            type="button"
            onClick={closeModal}
            className="p-1.5 rounded-lg text-gray-400 hover:text-white"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Scrollable Middle Content — Story Preview + Controls */}
        <div className="flex-1 overflow-y-auto px-5 py-4 space-y-3.5 no-scrollbar">
          {/* Mode Switcher: Photo / Video / Text */}
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

          {/* Live Story Preview Card */}
          <div
            className={`relative h-56 sm:h-64 rounded-2xl overflow-hidden flex flex-col justify-between shadow-xl transition-all ${
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
                  <span className="truncate">{musicTrack}</span>
                </div>
              </>
            ) : mediaPreviewUrl ? (
              <>
                {mode === 'VIDEO' ? (
                  <video
                    src={mediaPreviewUrl}
                    controls
                    playsInline
                    className="w-full h-full object-cover"
                  />
                ) : (
                  <img
                    src={mediaPreviewUrl}
                    alt="Story preview"
                    className="w-full h-full object-cover"
                  />
                )}
                <button
                  type="button"
                  onClick={() => fileInputRef.current?.click()}
                  className="absolute bottom-3 right-3 px-3 py-1.5 rounded-xl bg-black/75 text-white keep-white text-xs font-semibold backdrop-blur-md"
                >
                  Change {mode === 'VIDEO' ? 'Video' : 'Photo'}
                </button>
              </>
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
                  className={`w-7 h-7 rounded-full bg-gradient-to-tr ${grad} border-2 ${
                    selectedBg === grad ? 'border-white scale-110' : 'border-transparent'
                  }`}
                />
              ))}
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
              <option value="Followers">Followers</option>
              <option value="VYBE Matches">VYBE Matches</option>
            </select>
          </div>
        </div>

        {/* Pinned Safe-Area Bottom Action Bar — Both Buttons 100% Visible & >= 48px */}
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
