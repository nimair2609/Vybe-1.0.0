import React, { useState } from 'react';
import { 
  Heart, MessageCircle, Share2, Bookmark, Volume2, 
  VolumeX, Music, ArrowUp, ArrowDown, Sparkles 
} from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { OfficialBadge } from '../components/Badges';
import { VYBE_REACTIONS } from '../types';

export const ReelsView: React.FC = () => {
  const { reels, toggleLikePost, toggleSavePost, addCommentToPost } = useVybe();
  const [currentIndex, setCurrentIndex] = useState(0);
  const [isMuted, setIsMuted] = useState(false);
  const [showComments, setShowComments] = useState(false);
  const [commentText, setCommentText] = useState('');

  const currentReel = reels[currentIndex] || reels[0];

  const handleNext = () => {
    if (currentIndex < reels.length - 1) {
      setCurrentIndex(prev => prev + 1);
    }
  };

  const handlePrev = () => {
    if (currentIndex > 0) {
      setCurrentIndex(prev => prev - 1);
    }
  };

  if (!currentReel) {
    return (
      <div className="flex items-center justify-center min-h-[70vh] text-center p-6">
        <p className="text-gray-400 text-sm">No VYBES reels available yet.</p>
      </div>
    );
  }

  return (
    <div className="relative w-full h-[calc(100vh-120px)] max-w-md mx-auto bg-black rounded-3xl overflow-hidden shadow-2xl border border-[#262E4A]/60 my-2">
      
      {/* Reel Media / Background player */}
      <div className="absolute inset-0">
        {(currentReel.mediaUrls[0] && (currentReel.mediaUrls[0].includes('.mp4') || currentReel.mediaUrls[0].includes('.webm') || currentReel.mediaType === 'VIDEO' || currentReel.mediaType === 'REEL')) ? (
          <video 
            src={currentReel.mediaUrls[0]} 
            autoPlay 
            loop 
            playsInline 
            muted={isMuted} 
            className="w-full h-full object-cover" 
          />
        ) : (
          <img 
            src={currentReel.mediaUrls[0] || 'https://images.unsplash.com/photo-1514565131-fce0801e5785?auto=format&fit=crop&w=800&q=80'} 
            alt="Reel content" 
            className="w-full h-full object-cover"
          />
        )}
        <div className="absolute inset-0 bg-gradient-to-b from-black/30 via-transparent to-black/90 pointer-events-none" />
      </div>

      {/* Top Header Controls */}
      <div className="absolute top-4 inset-x-4 flex items-center justify-between z-10">
        <div className="flex items-center gap-2 px-3 py-1 rounded-full bg-black/50 backdrop-blur-md border border-white/10">
          <Sparkles className="w-4 h-4 text-[#00C6FF]" />
          <span className="text-xs font-black tracking-widest text-white brand-font uppercase">VYBES</span>
        </div>
        <button
          onClick={() => setIsMuted(!isMuted)}
          className="p-2.5 rounded-full bg-black/50 backdrop-blur-md border border-white/10 text-white hover:bg-black/80 transition-colors"
        >
          {isMuted ? <VolumeX className="w-4 h-4" /> : <Volume2 className="w-4 h-4 text-[#00C6FF]" />}
        </button>
      </div>

      {/* Right Floating Actions */}
      <div className="absolute right-3 bottom-24 flex flex-col items-center gap-5 z-20">
        
        {/* Like */}
        <button
          onClick={() => toggleLikePost(currentReel.id)}
          className="flex flex-col items-center gap-1 group"
        >
          <div className={`p-3 rounded-full backdrop-blur-md border border-white/10 transition-transform active:scale-75 ${
            currentReel.isLiked ? 'bg-[#EC4899] text-white' : 'bg-black/50 text-white hover:bg-black/70'
          }`}>
            <Heart className={`w-6 h-6 ${currentReel.isLiked ? 'fill-current' : ''}`} />
          </div>
          <span className="text-[11px] font-bold text-white shadow-sm">
            {currentReel.likesCount}
          </span>
        </button>

        {/* Comments */}
        <button
          onClick={() => setShowComments(!showComments)}
          className="flex flex-col items-center gap-1 group"
        >
          <div className="p-3 rounded-full bg-black/50 backdrop-blur-md border border-white/10 text-white hover:bg-black/70 transition-transform active:scale-75">
            <MessageCircle className="w-6 h-6" />
          </div>
          <span className="text-[11px] font-bold text-white shadow-sm">
            {currentReel.commentsCount}
          </span>
        </button>

        {/* Save */}
        <button
          onClick={() => toggleSavePost(currentReel.id)}
          className="flex flex-col items-center gap-1 group"
        >
          <div className={`p-3 rounded-full backdrop-blur-md border border-white/10 transition-transform active:scale-75 ${
            currentReel.isSaved ? 'bg-[#FFB703] text-[#060714]' : 'bg-black/50 text-white hover:bg-black/70'
          }`}>
            <Bookmark className={`w-6 h-6 ${currentReel.isSaved ? 'fill-current' : ''}`} />
          </div>
          <span className="text-[11px] font-bold text-white shadow-sm">
            {currentReel.savesCount}
          </span>
        </button>

        {/* Share */}
        <button
          onClick={() => {
            navigator.clipboard?.writeText(window.location.origin + `#reel_${currentReel.id}`);
          }}
          className="p-3 rounded-full bg-black/50 backdrop-blur-md border border-white/10 text-white hover:bg-black/70 transition-transform active:scale-75"
        >
          <Share2 className="w-6 h-6" />
        </button>

      </div>

      {/* Reel Bottom Details & Audio Marquee */}
      <div className="absolute bottom-4 left-4 right-16 z-10 text-white space-y-2">
        <div className="flex items-center gap-2.5">
          <img 
            src={currentReel.author.avatarUrl} 
            alt={currentReel.author.displayName}
            className="w-10 h-10 rounded-full object-cover border-2 border-white/20"
          />
          <div>
            <div className="flex items-center gap-1.5">
              <span className="font-bold text-sm">{currentReel.author.displayName}</span>
              {currentReel.author.badges?.map(b => (
                <OfficialBadge key={b} badge={b} size="sm" />
              ))}
            </div>
            <span className="text-[11px] text-gray-300 font-mono">@{currentReel.author.username}</span>
          </div>
        </div>

        <p className="text-xs text-gray-100 line-clamp-2 leading-relaxed">
          {currentReel.caption}
        </p>

        {/* Audio Track Tag */}
        {(currentReel.audioTrackTitle || currentReel.musicAttachment) && (
          <div className="flex items-center gap-2 pt-1 text-xs text-[#00C6FF]">
            <Music className="w-3.5 h-3.5 animate-spin" />
            <span className="truncate max-w-[200px]">
              {currentReel.musicAttachment?.title || currentReel.audioTrackTitle}
            </span>
          </div>
        )}
      </div>

      {/* Up/Down Reel Navigation Arrows */}
      <div className="absolute right-3 top-16 flex flex-col gap-2 z-10">
        <button
          onClick={handlePrev}
          disabled={currentIndex === 0}
          className="p-2 rounded-full bg-black/40 backdrop-blur-md border border-white/10 text-white hover:bg-black/80 disabled:opacity-30 transition-all"
        >
          <ArrowUp className="w-4 h-4" />
        </button>
        <button
          onClick={handleNext}
          disabled={currentIndex === reels.length - 1}
          className="p-2 rounded-full bg-black/40 backdrop-blur-md border border-white/10 text-white hover:bg-black/80 disabled:opacity-30 transition-all"
        >
          <ArrowDown className="w-4 h-4" />
        </button>
      </div>

      {/* Reel Comments Drawer */}
      {showComments && (
        <div className="absolute inset-x-0 bottom-0 h-3/5 bg-[#101322]/95 backdrop-blur-xl border-t border-[#262E4A] rounded-t-3xl p-4 z-30 flex flex-col animate-in slide-in-from-bottom">
          <div className="flex items-center justify-between pb-3 border-b border-[#262E4A]">
            <h4 className="text-sm font-bold text-white">Reel Comments ({currentReel.commentsCount})</h4>
            <button onClick={() => setShowComments(false)} className="text-gray-400 text-xs hover:text-white">✕</button>
          </div>
          <div className="flex-1 overflow-y-auto py-3 space-y-2 no-scrollbar">
            <p className="text-xs text-gray-400 text-center py-4">No comments yet. Be the first!</p>
          </div>
          <div className="pt-2 flex items-center gap-2 border-t border-[#262E4A]">
            <input 
              type="text"
              value={commentText}
              onChange={(e) => setCommentText(e.target.value)}
              placeholder="Add a comment..."
              className="flex-1 px-3 py-2 rounded-xl bg-[#060714] border border-[#262E4A] text-xs text-white focus:outline-none focus:border-[#8B5CF6]"
            />
            <button
              onClick={() => {
                if (commentText.trim()) {
                  addCommentToPost(currentReel.id, commentText);
                  setCommentText('');
                }
              }}
              className="px-3 py-2 rounded-xl bg-[#8B5CF6] text-white text-xs font-bold"
            >
              Send
            </button>
          </div>
        </div>
      )}

    </div>
  );
};
