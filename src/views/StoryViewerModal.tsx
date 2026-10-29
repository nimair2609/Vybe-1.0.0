import React, { useState, useEffect } from 'react';
import { X, Heart, Send, Music } from 'lucide-react';
import { useVybe } from '../context/VybeContext';

export const StoryViewerModal: React.FC = () => {
  const { 
    stories, activeStoryIndex, closeStoryViewer, 
    nextStory, prevStory 
  } = useVybe();

  const [progress, setProgress] = useState(0);
  const [replyText, setReplyText] = useState('');
  const [liked, setLiked] = useState(false);

  const currentStory = stories[activeStoryIndex] || stories[0];

  // Auto-advance timer
  useEffect(() => {
    setProgress(0);
    const interval = setInterval(() => {
      setProgress(prev => {
        if (prev >= 100) {
          nextStory();
          return 0;
        }
        return prev + 2;
      });
    }, 100);
    return () => clearInterval(interval);
  }, [activeStoryIndex]);

  if (!currentStory) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/95 backdrop-blur-2xl animate-fade-in select-none">
      <div className="relative w-full max-w-sm h-[92vh] max-h-[700px] flex flex-col justify-between bg-[#101322] border border-[#262E4A] rounded-3xl overflow-hidden shadow-2xl">
        
        {/* Progress Segments */}
        <div className="absolute top-3 inset-x-3 flex items-center gap-1.5 z-20">
          {stories.map((s, idx) => (
            <div key={s.id} className="h-1 flex-1 bg-white/20 rounded-full overflow-hidden">
              <div 
                className="h-full bg-white transition-all duration-100 ease-linear"
                style={{
                  width: idx < activeStoryIndex ? '100%' : idx === activeStoryIndex ? `${progress}%` : '0%'
                }}
              />
            </div>
          ))}
        </div>

        {/* Story Header */}
        <div className="absolute top-6 inset-x-4 flex items-center justify-between z-20 text-white">
          <div className="flex items-center gap-2.5">
            <img 
              src={currentStory.author.avatarUrl} 
              alt={currentStory.author.displayName}
              className="w-9 h-9 rounded-full object-cover border border-white/30"
            />
            <div>
              <p className="text-xs font-bold">{currentStory.author.displayName}</p>
              <div className="flex items-center gap-1.5 text-[10px] text-white/70">
                <span>{currentStory.timestamp}</span>
                <span>•</span>
                <span className="px-1.5 py-0.2 rounded bg-white/20">{currentStory.audience}</span>
              </div>
            </div>
          </div>
          <button 
            onClick={closeStoryViewer}
            className="p-1.5 rounded-full bg-black/40 text-white/80 hover:text-white"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Tap areas for prev / next */}
        <div className="absolute inset-y-16 inset-x-0 flex z-10">
          <div onClick={prevStory} className="w-1/3 h-full cursor-pointer" />
          <div onClick={nextStory} className="w-2/3 h-full cursor-pointer" />
        </div>

        {/* Story Content / Visual */}
        <div className={`w-full h-full flex items-center justify-center p-6 text-center ${
          currentStory.textBackgroundColor 
            ? `bg-gradient-to-tr ${currentStory.textBackgroundColor}` 
            : 'bg-[#060714]'
        }`}>
          {currentStory.mediaUrl && !currentStory.textBackgroundColor ? (
            <img 
              src={currentStory.mediaUrl} 
              alt="Story" 
              className="w-full h-full object-cover"
            />
          ) : (
            <p className="text-xl font-bold text-white leading-relaxed px-4 shadow-sm">
              "{currentStory.caption}"
            </p>
          )}
        </div>

        {/* Music track indicator if attached */}
        {currentStory.musicTrackTitle && (
          <div className="absolute bottom-16 left-4 z-20 flex items-center gap-1.5 px-3 py-1 rounded-full bg-black/60 backdrop-blur-md text-white text-[11px]">
            <Music className="w-3.5 h-3.5 text-[#00C6FF] animate-pulse" />
            <span className="truncate max-w-[200px]">{currentStory.musicTrackTitle}</span>
          </div>
        )}

        {/* Bottom Quick Reply */}
        <div className="p-3 bg-black/80 backdrop-blur-md border-t border-white/10 flex items-center gap-2 z-20">
          <input 
            type="text"
            value={replyText}
            onChange={(e) => setReplyText(e.target.value)}
            placeholder={`Reply to ${currentStory.author.displayName}...`}
            className="flex-1 px-3.5 py-2 bg-[#1A1F36] border border-white/10 rounded-full text-xs text-white focus:outline-none"
          />
          <button
            onClick={() => setLiked(!liked)}
            className={`p-2 rounded-full transition-transform active:scale-75 ${
              liked ? 'text-red-500' : 'text-white/80 hover:text-white'
            }`}
          >
            <Heart className={`w-5 h-5 ${liked ? 'fill-current' : ''}`} />
          </button>
        </div>

      </div>
    </div>
  );
};
