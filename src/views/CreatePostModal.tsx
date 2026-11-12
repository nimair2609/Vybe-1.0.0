import React, { useState } from 'react';
import { 
  X, Image, Music, MapPin, Sparkles, Send, PlaySquare, Upload, Loader2 
} from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { MOOD_DEFINITIONS, MoodType } from '../types';
import { compressImageToDataUrl } from '../utils/imageCompressor';

export const CreatePostModal: React.FC = () => {
  const { closeModal, createPost, currentUser } = useVybe();

  const [caption, setCaption] = useState('');
  const [selectedMood, setSelectedMood] = useState<MoodType>(currentUser.currentMood || 'GENERAL');
  const [imageUrl, setImageUrl] = useState('');
  const [locationName, setLocationName] = useState('');
  const [audioTrackTitle, setAudioTrackTitle] = useState('');
  const [isVybesReel, setIsVybesReel] = useState(false);
  const [tagsInput, setTagsInput] = useState('');
  const [isCompressing, setIsCompressing] = useState(false);

  const sampleImages = [
    'https://images.unsplash.com/photo-1514565131-fce0801e5785?auto=format&fit=crop&w=1000&q=80',
    'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?auto=format&fit=crop&w=1000&q=80',
    'https://images.unsplash.com/photo-1506126613408-eca07ce68773?auto=format&fit=crop&w=1000&q=80',
    'https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?auto=format&fit=crop&w=1000&q=80',
  ];

  const handleFileSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    try {
      setIsCompressing(true);
      const optimizedDataUrl = await compressImageToDataUrl(file, {
        maxWidth: 1440,
        maxHeight: 1440,
        quality: 0.82
      });
      setImageUrl(optimizedDataUrl);
    } catch (err) {
      console.warn('Image compression fallback:', err);
      const reader = new FileReader();
      reader.onload = () => setImageUrl(reader.result as string);
      reader.readAsDataURL(file);
    } finally {
      setIsCompressing(false);
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!caption.trim() && !imageUrl) return;

    const tags = tagsInput
      .split(/[\s,#]+/)
      .map(t => t.trim().toLowerCase())
      .filter(Boolean);

    createPost({
      caption: caption.trim(),
      mediaUrls: imageUrl ? [imageUrl] : [],
      mediaType: isVybesReel ? 'REEL' : imageUrl ? 'IMAGE' : 'TEXT',
      isVybesReel,
      moodZone: selectedMood,
      locationName: locationName.trim() || undefined,
      audioTrackTitle: audioTrackTitle.trim() || undefined,
      tags
    });
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-fade-in">
      <div className="relative w-full max-w-xl max-h-[90vh] overflow-y-auto bg-[#101322] border border-[#262E4A] rounded-3xl shadow-2xl p-6 text-white no-scrollbar space-y-5">
        
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-[#262E4A]">
          <div className="flex items-center gap-2">
            <Sparkles className="w-5 h-5 text-[#00C6FF]" />
            <h2 className="text-base font-bold brand-font text-white">Create New VYBE Moment</h2>
          </div>
          <button onClick={closeModal} className="p-2 text-gray-400 hover:text-white rounded-lg">
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
            className="w-full p-3.5 bg-[#060714] border border-[#262E4A] rounded-2xl text-xs text-white placeholder-gray-500 focus:outline-none focus:border-[#8B5CF6] resize-none leading-relaxed"
          />

          {/* Mood Zone Selector */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-gray-300">Choose Mood Frequency</label>
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
                        ? 'bg-gradient-to-r ' + item.gradient + ' text-white font-bold border-transparent shadow' 
                        : 'bg-[#060714] border-[#262E4A] text-gray-300'
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
              <label className="text-xs font-semibold text-gray-300 flex items-center gap-1.5">
                <Image className="w-3.5 h-3.5 text-[#00C6FF]" />
                Media Attachment (Auto-Compressed)
              </label>
              <label className="cursor-pointer text-[11px] font-semibold text-[#00C6FF] hover:text-white flex items-center gap-1 px-2.5 py-1 bg-[#1A1F36] rounded-lg border border-[#262E4A] hover:border-[#00C6FF] transition-all">
                {isCompressing ? (
                  <>
                    <Loader2 className="w-3 h-3 animate-spin text-[#00C6FF]" />
                    <span>Compressing...</span>
                  </>
                ) : (
                  <>
                    <Upload className="w-3 h-3" />
                    <span>Upload Device Photo</span>
                  </>
                )}
                <input
                  type="file"
                  accept="image/*"
                  onChange={handleFileSelect}
                  disabled={isCompressing}
                  className="hidden"
                />
              </label>
            </div>
            <input 
              type="text"
              value={imageUrl}
              onChange={(e) => setImageUrl(e.target.value)}
              placeholder="Or paste direct image URL..."
              className="w-full px-3 py-2 bg-[#060714] border border-[#262E4A] rounded-xl text-xs text-white focus:outline-none focus:border-[#8B5CF6]"
            />
            {imageUrl && (
              <div className="relative w-full max-h-40 rounded-xl overflow-hidden border border-[#262E4A] bg-[#060714] flex items-center justify-center">
                <img src={imageUrl} alt="preview" className="max-h-40 object-contain" />
                <button
                  type="button"
                  onClick={() => setImageUrl('')}
                  className="absolute top-2 right-2 p-1 rounded-full bg-black/70 hover:bg-black text-white text-xs"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              </div>
            )}
            <div className="flex gap-2 pt-1">
              {sampleImages.map((src, i) => (
                <button
                  key={i}
                  type="button"
                  onClick={() => setImageUrl(src)}
                  className="w-12 h-12 rounded-lg overflow-hidden border border-[#262E4A] hover:border-[#00C6FF] transition-all"
                >
                  <img src={src} alt="preset" className="w-full h-full object-cover" />
                </button>
              ))}
            </div>
          </div>

          {/* Audio Tag & Location */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="text-xs font-semibold text-gray-300 flex items-center gap-1 mb-1">
                <Music className="w-3.5 h-3.5 text-[#D946EF]" />
                Music Track Title
              </label>
              <input 
                type="text"
                value={audioTrackTitle}
                onChange={(e) => setAudioTrackTitle(e.target.value)}
                placeholder="e.g. Midnight Drive — Kavinsky"
                className="w-full px-3 py-2 bg-[#060714] border border-[#262E4A] rounded-xl text-xs text-white focus:outline-none focus:border-[#8B5CF6]"
              />
            </div>
            <div>
              <label className="text-xs font-semibold text-gray-300 flex items-center gap-1 mb-1">
                <MapPin className="w-3.5 h-3.5 text-[#FFB703]" />
                Location
              </label>
              <input 
                type="text"
                value={locationName}
                onChange={(e) => setLocationName(e.target.value)}
                placeholder="e.g. Bandra West, Mumbai"
                className="w-full px-3 py-2 bg-[#060714] border border-[#262E4A] rounded-xl text-xs text-white focus:outline-none focus:border-[#8B5CF6]"
              />
            </div>
          </div>

          {/* Tags */}
          <div>
            <label className="text-xs font-semibold text-gray-300 block mb-1">Hashtags</label>
            <input 
              type="text"
              value={tagsInput}
              onChange={(e) => setTagsInput(e.target.value)}
              placeholder="e.g. chillvibes, lofi, mumbai"
              className="w-full px-3 py-2 bg-[#060714] border border-[#262E4A] rounded-xl text-xs text-white focus:outline-none focus:border-[#8B5CF6]"
            />
          </div>

          {/* VYBES Reel toggle */}
          <div className="flex items-center justify-between p-3 rounded-xl bg-[#060714] border border-[#262E4A]">
            <div className="flex items-center gap-2">
              <PlaySquare className="w-4 h-4 text-[#D946EF]" />
              <span className="text-xs font-semibold text-gray-200">Publish as VYBES Reel (Short-Form Video)</span>
            </div>
            <input 
              type="checkbox"
              checked={isVybesReel}
              onChange={(e) => setIsVybesReel(e.target.checked)}
              className="w-4 h-4 accent-[#D946EF]"
            />
          </div>

          {/* Submit */}
          <button
            type="submit"
            className="w-full py-3 bg-gradient-to-r from-[#00C6FF] via-[#8B5CF6] to-[#D946EF] text-white font-bold text-xs rounded-xl shadow-lg hover:opacity-95 transition-opacity flex items-center justify-center gap-2"
          >
            <Send className="w-4 h-4" />
            <span>Publish Vibe Moment</span>
          </button>

        </form>

      </div>
    </div>
  );
};
