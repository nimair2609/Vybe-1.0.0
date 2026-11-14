import React, { useState, useEffect, useRef } from 'react';
import {
  MessageCircle, Share2, Bookmark, Volume2,
  VolumeX, Music, ArrowUp, ArrowDown, Sparkles, Pause, Play, X
} from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { OfficialBadge } from '../components/Badges';
import { FingerHeartReaction, FingerHeartDoubleTapOverlay } from '../components/FingerHeartReaction';
import { OptimizedImage, VybeAvatar } from '../components/OptimizedImage';
import { resolveMediaUrl } from '../services/supabaseService';

export const ReelsView: React.FC = () => {
  const {
    reels,
    openUserProfile,
    toggleLikePost,
    ensurePostLoved,
    reactToPost,
    toggleCommentLike,
    reactToComment,
    toggleSavePost,
    addCommentToPost,
    loadCommentsForPost,
    incrementPostShareCount,
    setInAppNotificationBanner
  } = useVybe();

  const [currentIndex, setCurrentIndex] = useState(0);
  const [isMuted, setIsMuted] = useState(false);
  const [isPaused, setIsPaused] = useState(false);
  const [isContainerVisible, setIsContainerVisible] = useState(true);
  const [isVideoReady, setIsVideoReady] = useState(false);
  const [progress, setProgress] = useState(0);
  const [showComments, setShowComments] = useState(false);
  const [commentText, setCommentText] = useState('');

  const containerRef = useRef<HTMLDivElement | null>(null);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const progressBarRef = useRef<HTMLDivElement | null>(null);

  const currentReel = reels[currentIndex] || reels[0];
  const rawMediaUrl = currentReel?.mediaUrls?.[0] || '';
  const mediaUrl = resolveMediaUrl(rawMediaUrl);
  const isVideoReel = Boolean(
    mediaUrl &&
      (mediaUrl.includes('.mp4') ||
        mediaUrl.includes('.webm') ||
        mediaUrl.includes('.mov') ||
        currentReel?.mediaType === 'VIDEO' ||
        currentReel?.mediaType === 'REEL')
  );
  const fallbackPosterUrl =
    currentReel?.author?.coverUrl ||
    currentReel?.author?.avatarUrl ||
    '';

  // Visibility-aware playback via IntersectionObserver (pause when scrolled out of view / offscreen)
  useEffect(() => {
    const container = containerRef.current;
    if (!container || typeof IntersectionObserver === 'undefined') return;

    const observer = new IntersectionObserver(
      (entries) => {
        const entry = entries[0];
        const visible = Boolean(entry?.isIntersecting && entry.intersectionRatio >= 0.35);
        setIsContainerVisible(visible);
        if (!visible && videoRef.current && !videoRef.current.paused) {
          videoRef.current.pause();
          setIsPaused(true);
        }
      },
      { threshold: [0, 0.35, 0.7] }
    );

    observer.observe(container);
    return () => observer.disconnect();
  }, [currentReel?.id]);

  // Auto-pause when tab/window is hidden to preserve device battery & bandwidth
  useEffect(() => {
    const handleVis = () => {
      if (document.hidden && videoRef.current && !videoRef.current.paused) {
        videoRef.current.pause();
        setIsPaused(true);
      }
    };
    document.addEventListener('visibilitychange', handleVis);
    return () => document.removeEventListener('visibilitychange', handleVis);
  }, []);

  // Reset progress & thumbnail-first readiness when switching Reels, and release previous media buffer cleanly
  useEffect(() => {
    setProgress(0);
    setIsPaused(false);
    setIsVideoReady(false);

    return () => {
      const vid = videoRef.current;
      if (vid) {
        try {
          vid.pause();
          vid.removeAttribute('src');
          vid.load();
        } catch {}
      }
    };
  }, [currentIndex, currentReel?.id]);

  // Ensure playback pauses immediately if container loses visibility or page pauses
  useEffect(() => {
    const vid = videoRef.current;
    if (!vid || !isVideoReel) return;
    if (!isContainerVisible || isPaused) {
      if (!vid.paused) vid.pause();
    } else if (isContainerVisible && !isPaused && vid.paused && vid.readyState >= 2) {
      vid.play().catch(() => {});
    }
  }, [isContainerVisible, isPaused, isVideoReel]);

  // Smooth fallback progress loop for static-image Reels when playing & visible
  useEffect(() => {
    if (!currentReel || isVideoReel || isPaused || !isContainerVisible) return;
    const durationMs = 6000;
    const stepMs = 60;
    const timer = window.setInterval(() => {
      setProgress(prev => (prev + (stepMs / durationMs) * 100) % 100);
    }, stepMs);
    return () => window.clearInterval(timer);
  }, [currentReel?.id, isVideoReel, isPaused, isContainerVisible]);

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
    <div
      ref={containerRef}
      className="relative w-full h-[calc(100dvh-124px)] max-w-md mx-auto bg-black rounded-3xl overflow-hidden shadow-2xl border border-[#262E4A]/60 my-2 select-none"
    >
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
        <div onClick={toggleVideoPlayPause} className="w-full h-full cursor-pointer relative">
          {isVideoReel ? (
            <>
              {/* Thumbnail-first backdrop visible until video frames are ready */}
              {!isVideoReady && (
                <div className="absolute inset-0 z-0 flex items-center justify-center bg-[#060714]">
                  {fallbackPosterUrl ? (
                    <OptimizedImage
                      src={fallbackPosterUrl}
                      alt={currentReel.author.displayName}
                      eager
                      className="w-full h-full object-cover blur-sm opacity-45 scale-105"
                    />
                  ) : null}
                  <div className="absolute inset-0 flex flex-col items-center justify-center gap-2">
                    <div className="p-3.5 rounded-full bg-black/55 border border-white/15 text-white keep-white">
                      <Play className="w-6 h-6" />
                    </div>
                  </div>
                </div>
              )}
              <video
                key={currentReel.id}
                ref={videoRef}
                src={isContainerVisible ? mediaUrl : undefined}
                poster={fallbackPosterUrl || undefined}
                autoPlay={isContainerVisible && !isPaused}
                loop
                playsInline
                preload={isContainerVisible ? 'metadata' : 'none'}
                muted={isMuted}
                onLoadedData={() => setIsVideoReady(true)}
                onCanPlay={() => setIsVideoReady(true)}
                onTimeUpdate={(e) => {
                  const el = e.currentTarget;
                  if (el.duration && Number.isFinite(el.duration) && el.duration > 0) {
                    setProgress((el.currentTime / el.duration) * 100);
                  }
                }}
                onPause={() => setIsPaused(true)}
                onPlay={() => setIsPaused(false)}
                className={`relative z-10 w-full h-full object-cover transition-opacity duration-200 ${
                  isVideoReady ? 'opacity-100' : 'opacity-0'
                }`}
              />
            </>
          ) : mediaUrl ? (
            <OptimizedImage
              src={mediaUrl}
              alt="Reel content"
              aspectRatio="9 / 16"
              eager
              className="w-full h-full object-cover"
            />
          ) : (
            <div className="w-full h-full bg-gradient-to-br from-[#12172B] via-[#1B1436] to-[#080B18] flex items-center justify-center p-8 text-center">
              <p className="text-base font-bold text-white keep-white leading-relaxed max-w-xs">
                {currentReel.caption || 'VYBE Reel'}
              </p>
            </div>
          )}
        </div>
        <div className="absolute inset-0 bg-gradient-to-b from-black/35 via-transparent to-black/90 pointer-events-none z-10" />

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
        {/* Global VYBE Universal Reaction */}
        <div className="flex flex-col items-center">
          <FingerHeartReaction
            isLiked={currentReel.isLiked || Boolean(currentReel.userReaction)}
            userReaction={currentReel.userReaction}
            count={currentReel.likesCount}
            forceDarkOverlay={true}
            verticalLayout={true}
            size={28}
            onToggle={() => toggleLikePost(currentReel.id)}
            onReact={(rx) => reactToPost(currentReel.id, rx)}
          />
        </div>

        {/* Comments */}
        <button
          type="button"
          onClick={() => {
            const nextOpen = !showComments;
            setShowComments(nextOpen);
            if (nextOpen && currentReel?.id) {
              loadCommentsForPost(currentReel.id);
            }
          }}
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
            const shareUrl = `${window.location.origin}/post/${encodeURIComponent(currentReel.id)}`;
            if (navigator.share) {
              navigator.share({ title: currentReel.author.displayName, url: shareUrl }).catch(() => {});
            } else {
              navigator.clipboard?.writeText(shareUrl);
              setInAppNotificationBanner('Reel link copied to clipboard.');
            }
            incrementPostShareCount(currentReel.id);
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
          <VybeAvatar
            src={currentReel.author.avatarUrl}
            name={currentReel.author.displayName}
            username={currentReel.author.username}
            size={40}
            className="w-10 h-10 border-2 border-white/25"
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
        <div className="absolute inset-x-0 bottom-0 h-3/5 bg-[var(--vybe-surface)]/95 backdrop-blur-xl border-t border-[var(--vybe-border)] rounded-t-3xl p-4 z-40 flex flex-col animate-in slide-in-from-bottom">
          <div className="flex items-center justify-between pb-3 border-b border-[var(--vybe-border)]">
            <h4 className="text-sm font-bold text-[var(--vybe-text)]">
              Reel Comments ({currentReel.commentsCount})
            </h4>
            <button
              type="button"
              onClick={() => setShowComments(false)}
              aria-label="Close comments"
              className="text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)] p-1.5 rounded-lg hover:bg-[var(--vybe-elevated)]"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
          <div className="flex-1 overflow-y-auto py-3 space-y-2.5 no-scrollbar">
            {!currentReel.comments || currentReel.comments.length === 0 ? (
              <p className="text-xs text-[var(--vybe-text-secondary)] text-center py-4">
                No comments yet. Be the first!
              </p>
            ) : (
              currentReel.comments.map(c => (
                <div
                  key={c.id}
                  className="flex items-start justify-between gap-2 p-2.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)]"
                >
                  <div className="flex items-start gap-2">
                    <VybeAvatar
                      src={c.author.avatarUrl}
                      name={c.author.displayName}
                      username={c.author.username}
                      size={28}
                      className="w-7 h-7 shrink-0"
                    />
                    <div>
                      <span className="text-xs font-bold text-[var(--vybe-text)]">{c.author.displayName}</span>
                      <p className="text-xs text-[var(--vybe-text-secondary)] mt-0.5">{c.content}</p>
                    </div>
                  </div>
                  <FingerHeartReaction
                    isLiked={Boolean(c.isLiked || c.userReaction)}
                    userReaction={c.userReaction}
                    count={c.likesCount || 0}
                    size={15}
                    onToggle={() => toggleCommentLike(currentReel.id, c.id)}
                    onReact={(rx) => reactToComment(currentReel.id, c.id, rx)}
                  />
                </div>
              ))
            )}
          </div>
          <div className="pt-2 flex items-center gap-2 border-t border-[var(--vybe-border)]">
            <input
              type="text"
              value={commentText}
              onChange={(e) => setCommentText(e.target.value)}
              placeholder="Add a comment..."
              className="flex-1 px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs text-[var(--vybe-text)] placeholder-[var(--vybe-text-muted)] focus:outline-none focus:border-[var(--vybe-accent)]"
            />
            <button
              type="button"
              onClick={() => {
                if (commentText.trim()) {
                  addCommentToPost(currentReel.id, commentText);
                  setCommentText('');
                }
              }}
              className="px-3.5 py-2 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold"
            >
              Send
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
