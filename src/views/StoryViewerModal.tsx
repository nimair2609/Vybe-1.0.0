import React, { useState, useEffect, useRef } from 'react';
import { X, Music, Send, RotateCcw, Loader2 } from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { FingerHeartReaction } from '../components/FingerHeartReaction';
import { formatRelativeTimestamp } from '../utils/timestamp';

const PHOTO_STORY_DURATION_MS = 5500;

export const StoryViewerModal: React.FC = () => {
  const {
    stories,
    activeStoryIndex,
    closeStoryViewer,
    nextStory,
    prevStory,
    toggleStoryLike,
    openUserProfile,
    setInAppNotificationBanner
  } = useVybe() as any;

  const currentStory = stories[activeStoryIndex] || stories[0];

  const isVideoStory = Boolean(
    currentStory &&
      (currentStory.mediaType === 'VIDEO' ||
        (currentStory.mediaUrl &&
          (/^data:video\//i.test(currentStory.mediaUrl) ||
            /\.(mp4|webm|mov)(\?|$)/i.test(currentStory.mediaUrl))))
  );
  const hasExternalMedia = Boolean(currentStory?.mediaUrl && !currentStory?.textBackgroundColor);

  const [progress, setProgress] = useState(0);
  const [replyText, setReplyText] = useState('');
  const [isMediaReady, setIsMediaReady] = useState(!hasExternalMedia);
  const [isBuffering, setIsBuffering] = useState(false);
  const [mediaError, setMediaError] = useState(false);
  const [retryKey, setRetryKey] = useState(0);
  const [isPressPaused, setIsPressPaused] = useState(false);
  const [isReplyFocused, setIsReplyFocused] = useState(false);

  const videoRef = useRef<HTMLVideoElement | null>(null);
  const mountedAtRef = useRef<number>(Date.now());
  const touchStartYRef = useRef<number | null>(null);

  // Reset playback & readiness whenever active story changes
  useEffect(() => {
    setProgress(0);
    setMediaError(false);
    setIsBuffering(false);
    setIsMediaReady(!hasExternalMedia);
  }, [currentStory?.id, activeStoryIndex, hasExternalMedia, retryKey]);

  const isPlaybackPaused = isPressPaused || isReplyFocused || !isMediaReady || isBuffering || mediaError;

  // Pause/Resume <video> when user holds screen or focuses reply input
  useEffect(() => {
    const vid = videoRef.current;
    if (!vid || !isVideoStory) return;
    if (isPressPaused || isReplyFocused) {
      vid.pause();
    } else if (isMediaReady && !mediaError) {
      vid.play().catch(() => {});
    }
  }, [isPressPaused, isReplyFocused, isMediaReady, mediaError, isVideoStory]);

  // Timer ONLY for Photo / Text stories AFTER media has decoded (Video stories use real <video> duration)
  useEffect(() => {
    if (!currentStory || isVideoStory || isPlaybackPaused) return;
    const stepMs = 50;
    const increment = (stepMs / PHOTO_STORY_DURATION_MS) * 100;

    const timer = window.setInterval(() => {
      setProgress(prev => {
        if (prev + increment >= 100) {
          window.clearInterval(timer);
          nextStory();
          return 100;
        }
        return prev + increment;
      });
    }, stepMs);

    return () => window.clearInterval(timer);
  }, [currentStory?.id, isVideoStory, isPlaybackPaused, nextStory]);

  if (!currentStory) return null;

  const displayTime = formatRelativeTimestamp(currentStory.createdAtIso, currentStory.timestamp);

  // Guard initial 260ms after mount so opening tap never bleeds into next/close
  const handleSafePrev = () => {
    if (Date.now() - mountedAtRef.current < 260) return;
    prevStory();
  };

  const handleSafeNext = () => {
    if (Date.now() - mountedAtRef.current < 260) return;
    nextStory();
  };

  const handleTouchStart = (e: React.TouchEvent) => {
    touchStartYRef.current = e.touches[0]?.clientY ?? null;
    setIsPressPaused(true);
  };

  const handleTouchEnd = (e: React.TouchEvent) => {
    setIsPressPaused(false);
    if (touchStartYRef.current !== null) {
      const endY = e.changedTouches[0]?.clientY ?? touchStartYRef.current;
      if (endY - touchStartYRef.current > 90) {
        closeStoryViewer();
      }
    }
    touchStartYRef.current = null;
  };

  const handleSendReply = (e: React.FormEvent) => {
    e.preventDefault();
    if (!replyText.trim()) return;
    setReplyText('');
    setIsReplyFocused(false);
    if (typeof setInAppNotificationBanner === 'function') {
      setInAppNotificationBanner(`Reply sent to @${currentStory.author.username}`);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/95 backdrop-blur-2xl animate-fade-in select-none"
      onTouchStart={handleTouchStart}
      onTouchEnd={handleTouchEnd}
    >
      <div className="relative w-full max-w-sm h-[92dvh] max-h-[720px] flex flex-col justify-between bg-[#080B18] border border-[#262E4A] rounded-3xl overflow-hidden shadow-2xl">
        {/* Progress Segments */}
        <div className="absolute top-3 inset-x-3 flex items-center gap-1.5 z-30">
          {stories.map((s: any, idx: number) => (
            <div key={s.id} className="h-1 flex-1 bg-white/25 rounded-full overflow-hidden">
              <div
                className="h-full bg-white rounded-full transition-[width] duration-75 ease-linear"
                style={{
                  width: idx < activeStoryIndex ? '100%' : idx === activeStoryIndex ? `${progress}%` : '0%'
                }}
              />
            </div>
          ))}
        </div>

        {/* Story Header */}
        <div className="absolute top-6 inset-x-4 flex items-center justify-between z-30 text-white keep-white">
          <div
            onClick={() => {
              closeStoryViewer();
              openUserProfile(currentStory.author);
            }}
            className="flex items-center gap-2.5 cursor-pointer"
          >
            <img
              src={currentStory.author.avatarUrl}
              alt={currentStory.author.displayName}
              className="w-9 h-9 rounded-full object-cover border border-white/30"
            />
            <div>
              <p className="text-xs font-bold text-white keep-white hover:underline">
                {currentStory.author.displayName}
              </p>
              <div className="flex items-center gap-1.5 text-[10px] text-white/75 keep-white">
                <span>{displayTime}</span>
                <span>•</span>
                <span className="px-1.5 py-0.5 rounded bg-white/15">{currentStory.audience}</span>
              </div>
            </div>
          </div>
          <button
            type="button"
            onClick={closeStoryViewer}
            aria-label="Close story"
            className="p-2 rounded-full bg-black/45 text-white/90 hover:text-white keep-white"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Tap areas for prev / next & press-hold pause */}
        {!mediaError && (
          <div
            className="absolute inset-y-16 inset-x-0 flex z-20"
            onMouseDown={() => setIsPressPaused(true)}
            onMouseUp={() => setIsPressPaused(false)}
            onMouseLeave={() => setIsPressPaused(false)}
          >
            <div onClick={handleSafePrev} className="w-1/3 h-full cursor-pointer" />
            <div onClick={handleSafeNext} className="w-2/3 h-full cursor-pointer" />
          </div>
        )}

        {/* Story Content / Visual */}
        <div
          className={`w-full h-full flex items-center justify-center text-center relative ${
            currentStory.textBackgroundColor
              ? `bg-gradient-to-tr ${currentStory.textBackgroundColor} p-6`
              : 'bg-[#060714]'
          }`}
        >
          {hasExternalMedia ? (
            <>
              {(isBuffering || !isMediaReady) && !mediaError && (
                <div className="absolute inset-0 z-10 flex flex-col items-center justify-center gap-2 bg-black/45">
                  <Loader2 className="w-8 h-8 text-[#00C6FF] animate-spin" />
                  <span className="text-[11px] font-medium text-white/80 keep-white">Loading story...</span>
                </div>
              )}

              {mediaError ? (
                <div className="z-30 flex flex-col items-center justify-center gap-3 px-6">
                  <p className="text-sm font-bold text-white keep-white">Story media unavailable on slow network</p>
                  <button
                    type="button"
                    onClick={() => {
                      setMediaError(false);
                      setIsMediaReady(false);
                      setRetryKey(prev => prev + 1);
                    }}
                    className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-white/15 hover:bg-white/25 text-xs font-bold text-white keep-white"
                  >
                    <RotateCcw className="w-3.5 h-3.5" />
                    <span>Retry Story</span>
                  </button>
                </div>
              ) : isVideoStory ? (
                <video
                  key={`${currentStory.id}_${retryKey}`}
                  ref={videoRef}
                  src={currentStory.mediaUrl}
                  autoPlay
                  playsInline
                  muted={false}
                  onLoadedMetadata={() => {
                    setIsMediaReady(true);
                    setIsBuffering(false);
                  }}
                  onCanPlay={() => {
                    setIsMediaReady(true);
                    setIsBuffering(false);
                  }}
                  onWaiting={() => setIsBuffering(true)}
                  onPlaying={() => {
                    setIsMediaReady(true);
                    setIsBuffering(false);
                  }}
                  onTimeUpdate={(e) => {
                    const el = e.currentTarget;
                    if (el.duration && Number.isFinite(el.duration) && el.duration > 0) {
                      setProgress(Math.min(100, (el.currentTime / el.duration) * 100));
                    }
                  }}
                  onEnded={() => {
                    setProgress(100);
                    nextStory();
                  }}
                  onError={() => setMediaError(true)}
                  className="w-full h-full object-cover"
                />
              ) : (
                <img
                  key={`${currentStory.id}_${retryKey}`}
                  src={currentStory.mediaUrl}
                  alt="Story"
                  onLoad={() => {
                    setIsMediaReady(true);
                    setIsBuffering(false);
                  }}
                  onError={() => setMediaError(true)}
                  className="w-full h-full object-cover"
                />
              )}

              {currentStory.caption && !mediaError && (
                <div className="absolute bottom-24 inset-x-4 px-3.5 py-2 rounded-xl bg-black/60 backdrop-blur-md text-white keep-white text-xs font-medium z-20">
                  {currentStory.caption}
                </div>
              )}
            </>
          ) : (
            <p className="text-xl font-bold text-white keep-white leading-relaxed px-4 shadow-sm">
              "{currentStory.caption}"
            </p>
          )}
        </div>

        {/* Music track indicator if attached */}
        {currentStory.musicTrackTitle && (
          <div className="absolute bottom-16 left-4 z-30 flex items-center gap-1.5 px-3 py-1 rounded-full bg-black/60 backdrop-blur-md text-white keep-white text-[11px]">
            <Music className="w-3.5 h-3.5 text-[#00C6FF] animate-pulse" />
            <span className="truncate max-w-[200px]">{currentStory.musicTrackTitle}</span>
          </div>
        )}

        {/* Bottom Quick Reply + Global Finger Heart Reaction */}
        <form
          onSubmit={handleSendReply}
          className="p-3 bg-black/85 backdrop-blur-md border-t border-white/10 flex items-center gap-2 z-30"
        >
          <input
            type="text"
            value={replyText}
            onFocus={() => setIsReplyFocused(true)}
            onBlur={() => setIsReplyFocused(false)}
            onChange={(e) => setReplyText(e.target.value)}
            placeholder={`Reply to ${currentStory.author.displayName}...`}
            className="flex-1 px-3.5 py-2 bg-[#1A1F36] border border-white/15 rounded-full text-xs text-white keep-white placeholder-gray-400 focus:outline-none focus:border-[#00C6FF]"
          />
          {replyText.trim() && (
            <button
              type="submit"
              className="p-2 rounded-full bg-gradient-to-r from-[#00C6FF] to-[#8B5CF6] text-white keep-white"
            >
              <Send className="w-4 h-4" />
            </button>
          )}
          <FingerHeartReaction
            isLiked={Boolean(currentStory.isLikedByMe)}
            count={currentStory.reactionsCount || 0}
            showCount={(currentStory.reactionsCount || 0) > 0}
            forceDarkOverlay={true}
            size={23}
            onToggle={() => toggleStoryLike(currentStory.id)}
          />
        </form>
      </div>
    </div>
  );
};
