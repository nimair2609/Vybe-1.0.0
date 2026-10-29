import React, { useState } from 'react';
import { X, Sparkles, Send, Music } from 'lucide-react';
import { useVybe } from '../context/VybeContext';

export const CreateStoryModal: React.FC = () => {
  const { closeModal, createStory } = useVybe();

  const [caption, setCaption] = useState('');
  const [selectedBg, setSelectedBg] = useState('from-[#6366F1] to-[#EC4899]');
  const [audience, setAudience] = useState<'Public' | 'Followers' | 'VYBE Matches'>('Public');
  const [musicTrack, setMusicTrack] = useState('Midnight Vibe Lo-Fi');

  const bgGradients = [
    'from-[#6366F1] to-[#EC4899]',
    'from-[#00C6FF] to-[#0072FF]',
    'from-[#F59E0B] to-[#FBBF24]',
    'from-[#10B981] to-[#3B82F6]',
    'from-[#F43F5E] to-[#FB7185]',
    'from-[#060714] to-[#1A1F36]'
  ];

  const handlePost = (e: React.FormEvent) => {
    e.preventDefault();
    if (!caption.trim()) return;

    createStory({
      caption: caption.trim(),
      mediaType: 'TEXT',
      audience,
      textBackgroundColor: selectedBg,
      musicTrackTitle: musicTrack
    });
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/85 backdrop-blur-md animate-fade-in">
      <div className="relative w-full max-w-sm bg-[#101322] border border-[#262E4A] rounded-3xl shadow-2xl p-6 text-white space-y-4">
        
        <div className="flex items-center justify-between pb-2 border-b border-[#262E4A]">
          <h3 className="font-bold text-sm text-white flex items-center gap-2">
            <Sparkles className="w-4 h-4 text-[#D946EF]" />
            Add to 24h Story
          </h3>
          <button onClick={closeModal} className="p-1 text-gray-400 hover:text-white">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Live Story Preview Card */}
        <div className={`h-64 rounded-2xl bg-gradient-to-tr ${selectedBg} p-5 flex flex-col justify-between shadow-xl transition-all`}>
          <div className="flex items-center justify-between text-xs text-white/80">
            <span className="font-semibold">Story Preview</span>
            <span className="px-2 py-0.5 rounded-full bg-black/30 text-[10px]">{audience}</span>
          </div>
          <p className="text-center font-bold text-sm text-white px-2 leading-relaxed">
            {caption || 'Type your story caption below...'}
          </p>
          <div className="flex items-center gap-1.5 text-[11px] text-white/90">
            <Music className="w-3.5 h-3.5" />
            <span className="truncate">{musicTrack}</span>
          </div>
        </div>

        {/* Gradient Picker */}
        <div className="flex items-center justify-center gap-2 pt-1">
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

        {/* Form */}
        <form onSubmit={handlePost} className="space-y-3">
          <input 
            type="text"
            value={caption}
            onChange={(e) => setCaption(e.target.value)}
            placeholder="Share a quick thought or question..."
            className="w-full px-3.5 py-2.5 bg-[#060714] border border-[#262E4A] rounded-xl text-xs text-white focus:outline-none focus:border-[#8B5CF6]"
          />

          <div className="flex items-center justify-between text-xs text-gray-300">
            <span>Audience:</span>
            <select
              value={audience}
              onChange={(e) => setAudience(e.target.value as any)}
              className="bg-[#060714] border border-[#262E4A] rounded-lg px-2 py-1 text-xs text-white focus:outline-none"
            >
              <option value="Public">Public</option>
              <option value="Followers">Followers</option>
              <option value="VYBE Matches">VYBE Matches</option>
            </select>
          </div>

          <button
            type="submit"
            disabled={!caption.trim()}
            className="w-full py-2.5 bg-gradient-to-r from-[#00C6FF] to-[#D946EF] disabled:opacity-40 text-white font-bold text-xs rounded-xl shadow-lg flex items-center justify-center gap-2"
          >
            <Send className="w-3.5 h-3.5" />
            <span>Share to Story</span>
          </button>
        </form>

      </div>
    </div>
  );
};
