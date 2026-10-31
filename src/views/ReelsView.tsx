import React, { useState, useEffect, useRef } from 'react';
import {
  MessageCircle, Share2, Bookmark, Volume2,
  VolumeX, Music, ArrowUp, ArrowDown, Sparkles, Pause
} from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { OfficialBadge } from '../components/Badges';
import { FingerHeartReaction, FingerHeartDoubleTapOverlay } from '../components/FingerHeartReaction';

export const ReelsView: React.FC = () => {
  const {
    reels,
    openUserProfile,
    toggleLikePost,
    ensurePostLoved,
    toggleCommentLike,
    toggleSavePost,
    addCommentToPost
  } = useVybe();

  const [currentIndex, setCurrentIndex] = useState(0);
  const [isMuted, setIsMuted] = useState(false);
  const [isPaused, setIsPaused] = useState(false);
  const [progress, setProgress] = useState(0);
  const [showComments, setShowComments] = useState(false);
  const [commentText, setCommentText] = useState('');

  const videoRef = useRef<HTMLVideoElement | null>(null);
  const progressBarRef = useRef<HTMLDivElement | null>(null);

  const currentReel = reels[currentIndex] || reels[0];
  const mediaUrl = currentReel?.mediaUrls?.[0] || '';
  const isVideoReel = Boolean(
    mediaUrl &&
      (mediaUrl.includes('.mp4') ||
        mediaUrl.includes('.webm') ||
        currentReel?.mediaType === 'VIDEO' ||
        currentReel?.mediaType === 'REEL')
  );

  // Reset progress & play state when switching Reels
  useEffect(() => {
    setProgress(0);
    setIsPaused(false);
  }, [currentIndex, currentReel?.id]);

  // Smooth fallback progress loop for static-image Reels when playing
  useEffect(() => {
    if (!currentReel || isVideoReel || isPaused) return;
    const durationMs = 6000;
    const stepMs = 60;
    const timer = window.setInterval(() => {
      setProgress(prev => (prev + (stepMs / durationMs) * 100) % 100);
    }, stepMs);
    return () => window.clearInterval(timer);
  }, [currentReel?.id, isVideoReel, isPaused]);

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

  const toggleVideoPlayPause = () => {
    const vid = videoRef.current;
    if (vid && isVideoReel) {
      if (vid.paused) {
        vid.play().catch(() => {});
        setIsPaused(false);
      } else {
        vid.pause();
        setIsPaused(true);
      }
    } else {
      setIsPaused(prev => !prev);
    }
  };

  const handleScrub = (e: React.MouseEvent<HTMLDivElement>) => {
    e.stopPropagation();
    const bar = progressBarRef.current;
    if (!bar) return;
    const rect = bar.getBoundingClientRect();
    const fraction = Math.max(0, Math.min(1, (e.clientX - rect.left) / Math.max(1, rect.width)));
    setProgress(fraction * 100);

    const vid = videoRef.current;
    if (vid && vid.duration && Number.isFinite(vid.duration)) {
      vid.currentTime = fraction * vid.duration;
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
    <div className="relative w-full h-[calc(100dvh-124px)] max-w-md mx-auto bg-black rounded-3xl overflow-hidden shadow-2xl border border-[#262E4A]/60 my-2 select-none">
      {/* Instagram-Style Top Horizontal Duration / Progress Bar */}
      <div
        ref={progressBarRef}
        onClick={handleScrub}
        role="progressbar"
        aria-valuenow={Math.round(progress)}
        aria-valuemin={0}
        aria-valuemax={100}
        title="Seek Reel"
        className="absolute top-2.5 inset-x-3.5 z-30 py-1.5 cursor-pointer group"
      >
        <div className="h-[3px] group-hover:h-[5px] w-full rounded-full bg-white/30 overflow-hidden transition-all">
          <div
            className="h-full bg-white rounded-full transition-[width] duration-75 ease-linear"
            style={{ width: `${Math.max(0, Math.min(100, progress))}%` }}
          />
        </div>
      </div>

      {/* Reel Media / Background player with Safe Double-Tap Finger Heart */}
      <FingerHeartDoubleTapOverlay
        onDoubleTap={() => ensurePostLoved(currentReel.id)}
        className="absolute inset-0"
      >
        <div onClick={toggleVideoPlayPause} className="w-full h-full cursor-pointer">
          {isVideoReel ? (
            <video
              key={currentReel.id}
              ref={videoRef}
              src={mediaUrl}
              autoPlay
              loop
              playsInline
              muted={isMuted}
              onTimeUpdate={(e) => {
                const el = e.currentTarget;
                if (el.duration && Number.isFinite(el.duration) && el.duration > 0) {
                  setProgress((el.currentTime / el.duration) * 100);
                }
              }}
              onPause={() => setIsPaused(true)}
              onPlay={() => setIsPaused(false)}
              className="w-full h-full object-cover"
            />
          ) : (
            <img
              src={
                mediaUrl ||
                'https://images.unsplash.com/photo-1514565131-fce0801e5785?auto=format&fit=crop&w=800&q=80'
              }
              alt="Reel content"
              className="w-full h-full object-cover"
            />
          )}
        </div>
        <div className="absolute inset-0 bg-gradient-to-b from-black/35 via-transparent to-black/90 pointer-events-none" />

        {isPaused && (
          <div className="pointer-events-none absolute inset-0 flex items-center justify-center z-20">
            <div className="p-4 rounded-full bg-black/55 backdrop-blur-md text-white keep-white">
              <Pause className="w-8 h-8" />
            </div>
          </div>
        )}
      </FingerHeartDoubleTapOverlay>

      {/* Top Header Controls */}
      <div className="absolute top-6 inset-x-4 flex items-center justify-between z-20">
        <div className="flex items-center gap-2 px-3 py-1 rounded-full bg-black/50 backdrop-blur-md border border-white/10">
          <Sparkles className="w-4 h-4 text-[#00C6FF]" />
          <span className="text-xs font-black tracking-widest text-white keep-white brand-font uppercase">
            VYBES
          </span>
        </div>
        <button
          type="button"
          onClick={() => setIsMuted(!isMuted)}
          className="p-2.5 rounded-full bg-black/50 backdrop-blur-md border border-white/10 text-white keep-white hover:bg-black/80 transition-colors"
        >
          {isMuted ? <VolumeX className="w-4 h-4" /> : <Volume2 className="w-4 h-4 text-[#00C6FF]" />}
        </button>
      </div>

      {/* Right Floating Actions */}
      <div className="absolute right-3 bottom-24 flex flex-col items-center gap-5 z-20">
        {/* Global Finger Heart Reaction */}
        <div className="flex flex-col items-center">
          <FingerHeartReaction
            isLiked={currentReel.isLiked}
            count={currentReel.likesCount}
            forceDarkOverlay={true}
            verticalLayout={true}
            size={28}
            onToggle={() => toggleLikePost(currentReel.id)}
          />
        </div>

        {/* Comments */}
        <button
          type="button"
          onClick={() => setShowComments(!showComments)}
          className="flex flex-col items-center gap-1 group"
        >
          <div className="p-3 rounded-full bg-black/50 backdrop-blur-md border border-white/10 text-white keep-white hover:bg-black/70 transition-transform active:scale-75">
            <MessageCircle className="w-6 h-6" />
          </div>
          <span className="text-[11px] font-bold text-white keep-white shadow-sm">
            {currentReel.commentsCount}
          </span>
        </button>

        {/* Save */}
        <button
          type="button"
          onClick={() => toggleSavePost(currentReel.id)}
          className="flex flex-col items-center gap-1 group"
        >
          <div
            className={`p-3 rounded-full backdrop-blur-md border border-white/10 transition-transform active:scale-75 ${
              currentReel.isSaved
                ? 'bg-[#FFB703] text-[#060714]'
                : 'bg-black/50 text-white keep-white hover:bg-black/70'
            }`}
          >
            <Bookmark className={`w-6 h-6 ${currentReel.isSaved ? 'fill-current' : ''}`} />
          </div>
          <span className="text-[11px] font-bold text-white keep-white shadow-sm">
            {currentReel.savesCount}
          </span>
        </button>

        {/* Share */}
        <button
          type="button"
          onClick={() => {
            navigator.clipboard?.writeText(`${window.location.origin}/post/${encodeURIComponent(currentReel.id)}`);
          }}
          className="p-3 rounded-full bg-black/50 backdrop-blur-md border border-white/10 text-white keep-white hover:bg-black/70 transition-transform active:scale-75"
        >
          <Share2 className="w-6 h-6" />
        </button>
      </div>

      {/* Reel Bottom Details & Audio Marquee */}
      <div className="absolute bottom-4 left-4 right-16 z-20 text-white keep-white space-y-2">
        <div
          onClick={() => openUserProfile(currentReel.author)}
          className="flex items-center gap-2.5 cursor-pointer"
        >
          <img
            src={currentReel.author.avatarUrl}
            alt={currentReel.author.displayName}
            className="w-10 h-10 rounded-full object-cover border-2 border-white/25"
          />
          <div>
            <div className="flex items-center gap-1.5">
              <span className="font-bold text-sm text-white keep-white hover:underline">
                {currentReel.author.displayName}
              </span>
              {currentReel.author.badges?.map(b => (
                <OfficialBadge key={b} badge={b} size="sm" />
              ))}
            </div>
            <span className="text-[11px] text-gray-300 font-mono keep-white">
              @{currentReel.author.username}
            </span>
          </div>
        </div>

        <p className="text-xs text-gray-100 keep-white line-clamp-2 leading-relaxed">
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
      <div className="absolute right-3 top-20 flex flex-col gap-2 z-20">
        <button
          type="button"
          onClick={handlePrev}
          disabled={currentIndex === 0}
          className="p-2 rounded-full bg-black/45 backdrop-blur-md border border-white/10 text-white keep-white hover:bg-black/80 disabled:opacity-30 transition-all"
        >
          <ArrowUp className="w-4 h-4" />
        </button>
        <button
          type="button"
          onClick={handleNext}
          disabled={currentIndex === reels.length - 1}
          className="p-2 rounded-full bg-black/45 backdrop-blur-md border border-white/10 text-white keep-white hover:bg-black/80 disabled:opacity-30 transition-all"
        >
          <ArrowDown className="w-4 h-4" />
        </button>
      </div>

      {/* Reel Comments Drawer */}
      {showComments && (
        <div className="absolute inset-x-0 bottom-0 h-3/5 bg-[#101322]/95 backdrop-blur-xl border-t border-[#262E4A] rounded-t-3xl p-4 z-40 flex flex-col animate-in slide-in-from-bottom">
          <div className="flex items-center justify-between pb-3 border-b border-[#262E4A]">
            <h4 className="text-sm font-bold text-white">Reel Comments ({currentReel.commentsCount})</h4>
            <button
              type="button"
              onClick={() => setShowComments(false)}
              className="text-gray-400 text-xs hover:text-white px-2 py-1"
            >
              ✕
            </button>
          </div>
          <div className="flex-1 overflow-y-auto py-3 space-y-2.5 no-scrollbar">
            {!currentReel.comments || currentReel.comments.length === 0 ? (
              <p className="text-xs text-gray-400 text-center py-4">No comments yet. Be the first!</p>
            ) : (
              currentReel.comments.map(c => (
                <div key={c.id} className="flex items-start justify-between gap-2 p-2.5 rounded-xl bg-[#060714]/80 border border-[#262E4A]">
                  <div className="flex items-start gap-2">
                    <img
                      src={c.author.avatarUrl}
                      alt={c.author.displayName}
                      className="w-7 h-7 rounded-full object-cover shrink-0"
                    />
                    <div>
                      <span className="text-xs font-bold text-white">{c.author.displayName}</span>
                      <p className="text-xs text-gray-300 mt-0.5">{c.content}</p>
                    </div>
                  </div>
                  <FingerHeartReaction
                    isLiked={Boolean(c.isLiked)}
                    count={c.likesCount || 0}
                    size={15}
                    onToggle={() => toggleCommentLike(currentReel.id, c.id)}
                  />
                </div>
              ))
            )}
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
              type="button"
              onClick={() => {
                if (commentText.trim()) {
                  addCommentToPost(currentReel.id, commentText);
                  setCommentText('');
                }
              }}
              className="px-3.5 py-2 rounded-xl bg-[#8B5CF6] text-white keep-white text-xs font-bold"
            >
              Send
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
