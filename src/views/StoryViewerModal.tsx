import React, { useState, useEffect, useRef } from 'react';
import {
  X, Music, Send, RotateCcw, Loader2, Eye, MessageCircle, Volume2, VolumeX
} from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { FingerHeartReaction } from '../components/FingerHeartReaction';
import { VybeAvatar } from '../components/OptimizedImage';
import { formatRelativeTimestamp } from '../utils/timestamp';
import {
  fetchStoryComments,
  fetchStoryViewersRpc,
  recordStoryViewRpc,
  submitStoryComment
} from '../services/storiesCirclesService';
import { StoryComment, StoryViewerInfo } from '../types';

const PHOTO_STORY_DURATION_MS = 5500;

export const StoryViewerModal: React.FC = () => {
  const {
    stories,
    activeStoryIndex,
    closeStoryViewer,
    nextStory,
    prevStory,
    toggleStoryLike,
    reactToStory,
    openUserProfile,
    setInAppNotificationBanner,
    requireAuth,
    currentUser,
    startConversationWithUser,
    sendMessage
  } = useVybe();

  const currentStory = stories[activeStoryIndex] || stories[0];
  const isStoryOwner = Boolean(currentUser && currentStory && currentUser.id === currentStory.author.id);

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
  const [isMuted, setIsMuted] = useState(false);

  // Stories 2.0 Drawers: Viewers (owner-only) & Persistent Story Comments
  const [liveViewsCount, setLiveViewsCount] = useState<number>(currentStory?.viewsCount || 0);
  const [showViewersSheet, setShowViewersSheet] = useState(false);
  const [viewersList, setViewersList] = useState<StoryViewerInfo[]>([]);
  const [viewersLoading, setViewersLoading] = useState(false);

  const [showCommentsSheet, setShowCommentsSheet] = useState(false);
  const [commentsList, setCommentsList] = useState<StoryComment[]>([]);
  const [commentsLoading, setCommentsLoading] = useState(false);
  const [commentDraft, setCommentDraft] = useState('');
  const [commentSubmitting, setCommentSubmitting] = useState(false);

  const videoRef = useRef<HTMLVideoElement | null>(null);
  const bgAudioRef = useRef<HTMLAudioElement | null>(null);
  const mountedAtRef = useRef<number>(Date.now());
  const touchStartYRef = useRef<number | null>(null);
  const recordedIdsRef = useRef<Set<string>>(new Set());

  // Reset playback & drawers when active story changes
  useEffect(() => {
    setProgress(0);
    setMediaError(false);
    setIsBuffering(false);
    setIsMediaReady(!hasExternalMedia);
    setShowViewersSheet(false);
    setShowCommentsSheet(false);
    setViewersList([]);
    setCommentsList([]);
    setLiveViewsCount(currentStory?.viewsCount || 0);
  }, [currentStory?.id, activeStoryIndex, hasExternalMedia, retryKey]);

  // Record story view in Supabase & update live view count
  useEffect(() => {
    if (!currentStory?.id) return;
    if (recordedIdsRef.current.has(currentStory.id)) return;
    recordedIdsRef.current.add(currentStory.id);
    recordStoryViewRpc(currentStory.id).then(res => {
      if (typeof res.viewsCount === 'number') {
        setLiveViewsCount(res.viewsCount);
      }
    }).catch(() => {});
  }, [currentStory?.id]);

  const isPlaybackPaused =
    isPressPaused ||
    isReplyFocused ||
    showViewersSheet ||
    showCommentsSheet ||
    !isMediaReady ||
    isBuffering ||
    mediaError;

  // Background audio track playback & volume mixing
  useEffect(() => {
    if (bgAudioRef.current) {
      bgAudioRef.current.pause();
      bgAudioRef.current = null;
    }
    if (!currentStory?.audioTrackUrl) return;
    const audio = new Audio(currentStory.audioTrackUrl);
    audio.loop = true;
    const targetVol = currentStory.audioVolume ?? 0.8;
    audio.volume = isMuted ? 0 : Math.min(1, Math.max(0, targetVol));
    bgAudioRef.current = audio;
    if (!isPlaybackPaused) {
      audio.play().catch(() => {});
    }
    return () => {
      audio.pause();
      bgAudioRef.current = null;
    };
  }, [currentStory?.id, currentStory?.audioTrackUrl]);

  useEffect(() => {
    const audio = bgAudioRef.current;
    if (!audio) return;
    const targetVol = currentStory?.audioVolume ?? 0.8;
    audio.volume = isMuted ? 0 : Math.min(1, Math.max(0, targetVol));
    if (isPlaybackPaused) {
      audio.pause();
    } else {
      audio.play().catch(() => {});
    }
  }, [isPlaybackPaused, isMuted, currentStory?.audioVolume]);

  // Pause/Resume <video> & apply volume mix
  useEffect(() => {
    const vid = videoRef.current;
    if (!vid || !isVideoStory) return;
    const vVol = currentStory?.storyVolume ?? 1;
    vid.volume = isMuted ? 0 : Math.min(1, Math.max(0, vVol));
    vid.muted = isMuted || vVol <= 0.01;
    if (isPressPaused || isReplyFocused || showViewersSheet || showCommentsSheet) {
      vid.pause();
    } else if (isMediaReady && !mediaError) {
      vid.play().catch(() => {});
    }
  }, [
    isPressPaused,
    isReplyFocused,
    showViewersSheet,
    showCommentsSheet,
    isMediaReady,
    mediaError,
    isVideoStory,
    isMuted,
    currentStory?.storyVolume
  ]);

  // Timer ONLY for Photo / Text stories AFTER media has decoded
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

  const handleOpenViewers = async () => {
    if (!isStoryOwner) return;
    setShowCommentsSheet(false);
    setShowViewersSheet(true);
    setViewersLoading(true);
    const list = await fetchStoryViewersRpc(currentStory.id);
    setViewersList(list);
    setViewersLoading(false);
  };

  const handleOpenComments = async () => {
    setShowViewersSheet(false);
    setShowCommentsSheet(true);
    setCommentsLoading(true);
    const list = await fetchStoryComments(currentStory.id);
    setCommentsList(list);
    setCommentsLoading(false);
  };

  const handleAddStoryComment = async (e: React.FormEvent) => {
    e.preventDefault();
    const clean = commentDraft.trim();
    if (!clean) return;
    if (!requireAuth('Sign in to comment on stories.') || !currentUser) return;
    setCommentSubmitting(true);
    const res = await submitStoryComment({
      storyId: currentStory.id,
      authorId: currentUser.id,
      content: clean
    });
    setCommentSubmitting(false);
    if (res.ok && res.comment) {
      setCommentsList(prev => [...prev, res.comment!]);
      setCommentDraft('');
    }
  };

  const handleSafePrev = () => {
    if (Date.now() - mountedAtRef.current < 260) return;
    prevStory();
  };

  const handleSafeNext = () => {
    if (Date.now() - mountedAtRef.current < 260) return;
    nextStory();
  };

  const handleTouchStart = (e: React.TouchEvent) => {
    if (showViewersSheet || showCommentsSheet) return;
    touchStartYRef.current = e.touches[0]?.clientY ?? null;
    setIsPressPaused(true);
  };

  const handleTouchEnd = (e: React.TouchEvent) => {
    setIsPressPaused(false);
    if (touchStartYRef.current !== null && !showViewersSheet && !showCommentsSheet) {
      const endY = e.changedTouches[0]?.clientY ?? touchStartYRef.current;
      if (endY - touchStartYRef.current > 90) {
        closeStoryViewer();
      }
    }
    touchStartYRef.current = null;
  };

  const handleSendReply = async (e: React.FormEvent) => {
    e.preventDefault();
    const clean = replyText.trim();
    if (!clean) return;
    if (!requireAuth('Sign in to reply to stories.') || !currentUser) return;
    if (currentStory.author.id === currentUser.id) {
      setReplyText('');
      setIsReplyFocused(false);
      return;
    }

    setReplyText('');
    setIsReplyFocused(false);
    const convRes = await startConversationWithUser(currentStory.author);
    if (!convRes.ok || !convRes.conversation) return;
    await sendMessage(convRes.conversation.id, `[Story reply] ${clean}`);
    setInAppNotificationBanner(`Reply sent to @${currentStory.author.username}`);
  };

  const mediaTransformStyle = currentStory.transform
    ? {
        transform: `scale(${currentStory.transform.scale || 1}) rotate(${currentStory.transform.rotationDeg || 0}deg)`
      }
    : undefined;

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
            <VybeAvatar
              src={currentStory.author.avatarUrl}
              name={currentStory.author.displayName}
              username={currentStory.author.username}
              size={36}
              className="w-9 h-9 border border-white/30"
            />
            <div>
              <p className="text-xs font-bold text-white keep-white hover:underline">
                {currentStory.author.displayName}
              </p>
              <div className="flex items-center gap-1.5 text-[10px] text-white/75 keep-white">
                <span>{displayTime}</span>
                <span>•</span>
                <span className="px-1.5 py-0.5 rounded bg-white/15">
                  {currentStory.audience === 'Followers' ? 'LINKUPS' : currentStory.audience}
                </span>
              </div>
            </div>
          </div>
          <div className="flex items-center gap-1.5">
            {(isVideoStory || currentStory.audioTrackUrl) && (
              <button
                type="button"
                onClick={() => setIsMuted(m => !m)}
                aria-label={isMuted ? 'Unmute story audio' : 'Mute story audio'}
                className="p-2 rounded-full bg-black/45 text-white/90 hover:text-white keep-white"
              >
                {isMuted ? <VolumeX className="w-4 h-4" /> : <Volume2 className="w-4 h-4" />}
              </button>
            )}
            <button
              type="button"
              onClick={closeStoryViewer}
              aria-label="Close story"
              className="p-2 rounded-full bg-black/45 text-white/90 hover:text-white keep-white"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* Tap areas for prev / next & press-hold pause */}
        {!mediaError && !showViewersSheet && !showCommentsSheet && (
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
          className={`w-full h-full flex items-center justify-center text-center relative overflow-hidden ${
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
                  style={mediaTransformStyle}
                  onLoadedMetadata={(e) => {
                    const el = e.currentTarget;
                    if (currentStory.trimStartMs && currentStory.trimStartMs > 0) {
                      el.currentTime = currentStory.trimStartMs / 1000;
                    }
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
                    const startSec = (currentStory.trimStartMs || 0) / 1000;
                    const endSec = currentStory.trimEndMs
                      ? currentStory.trimEndMs / 1000
                      : el.duration || 0;
                    if (endSec > startSec && el.currentTime >= endSec) {
                      setProgress(100);
                      nextStory();
                      return;
                    }
                    const effectiveDur = Math.max(0.5, endSec - startSec);
                    setProgress(Math.min(100, Math.max(0, ((el.currentTime - startSec) / effectiveDur) * 100)));
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
                  style={mediaTransformStyle}
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

          {/* Render Story 2.0 Text/Sticker Overlays */}
          {currentStory.overlays?.map(ov => (
            <div
              key={ov.id}
              style={{
                top: `${Math.round(ov.y * 100)}%`,
                left: `${Math.round(ov.x * 100)}%`,
                transform: 'translate(-50%, -50%)',
                color: ov.color || '#FFFFFF'
              }}
              className="absolute z-20 px-3 py-1 rounded-xl bg-black/55 backdrop-blur-sm text-sm font-extrabold pointer-events-none shadow-lg"
            >
              {ov.text}
            </div>
          ))}
        </div>

        {/* Bottom HUD Row above reply bar: Music badge + Viewers (owner) + Comments drawer trigger */}
        <div className="absolute bottom-16 inset-x-4 z-30 flex items-center justify-between gap-2">
          {currentStory.musicTrackTitle ? (
            <div className="flex items-center gap-1.5 px-3 py-1 rounded-full bg-black/65 backdrop-blur-md text-white keep-white text-[11px] min-w-0">
              <Music className="w-3.5 h-3.5 text-[#00C6FF] animate-pulse shrink-0" />
              <span className="truncate max-w-[160px]">{currentStory.musicTrackTitle}</span>
            </div>
          ) : (
            <div />
          )}

          <div className="flex items-center gap-1.5 shrink-0">
            {isStoryOwner && (
              <button
                type="button"
                onClick={handleOpenViewers}
                aria-label="View story viewers"
                className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-black/65 hover:bg-black/85 text-white keep-white text-[11px] font-bold backdrop-blur-md"
              >
                <Eye className="w-3.5 h-3.5 text-[#00C6FF]" />
                <span>{liveViewsCount}</span>
              </button>
            )}
            <button
              type="button"
              onClick={handleOpenComments}
              aria-label="Open story comments"
              className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-black/65 hover:bg-black/85 text-white keep-white text-[11px] font-bold backdrop-blur-md"
            >
              <MessageCircle className="w-3.5 h-3.5 text-[#D946EF]" />
              <span>{commentsList.length > 0 ? commentsList.length : currentStory.commentsCount || 'Comments'}</span>
            </button>
          </div>
        </div>

        {/* Owner-Only Story Viewers Sheet */}
        {isStoryOwner && showViewersSheet && (
          <div
            onClick={e => e.stopPropagation()}
            className="absolute inset-x-0 bottom-0 h-[55%] z-40 bg-[#101322] border-t border-[#262E4A] rounded-t-3xl p-4 flex flex-col text-white"
          >
            <div className="flex items-center justify-between pb-2.5 border-b border-[#262E4A]">
              <h4 className="text-xs font-extrabold flex items-center gap-1.5">
                <Eye className="w-4 h-4 text-[#00C6FF]" />
                Story Viewers ({liveViewsCount})
              </h4>
              <button
                type="button"
                onClick={() => setShowViewersSheet(false)}
                aria-label="Close viewers sheet"
                className="p-1 text-gray-400 hover:text-white"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="flex-1 overflow-y-auto pt-2 space-y-2 no-scrollbar">
              {viewersLoading ? (
                <div className="flex items-center justify-center py-8 text-xs text-gray-400 gap-2">
                  <Loader2 className="w-4 h-4 animate-spin" /> Loading viewers...
                </div>
              ) : viewersList.length === 0 ? (
                <p className="text-xs text-gray-400 text-center py-8">No viewers recorded yet.</p>
              ) : (
                viewersList.map(v => (
                  <div
                    key={v.viewerId}
                    onClick={() => {
                      closeStoryViewer();
                      openUserProfile({ id: v.viewerId, username: v.username, displayName: v.displayName } as any);
                    }}
                    className="flex items-center justify-between p-2 rounded-xl bg-[#1A1F36] cursor-pointer hover:bg-[#232A47]"
                  >
                    <div className="flex items-center gap-2.5 min-w-0">
                      <VybeAvatar src={v.avatarUrl} name={v.displayName} size={32} className="w-8 h-8 shrink-0" />
                      <div className="min-w-0">
                        <p className="text-xs font-bold truncate">{v.displayName}</p>
                        <p className="text-[10px] text-gray-400 truncate">@{v.username}</p>
                      </div>
                    </div>
                    <span className="text-[10px] text-gray-400 shrink-0">{v.timestamp}</span>
                  </div>
                ))
              )}
            </div>
          </div>
        )}

        {/* Persistent Story Comments Sheet */}
        {showCommentsSheet && (
          <div
            onClick={e => e.stopPropagation()}
            className="absolute inset-x-0 bottom-0 h-[58%] z-40 bg-[#101322] border-t border-[#262E4A] rounded-t-3xl p-4 flex flex-col text-white"
          >
            <div className="flex items-center justify-between pb-2.5 border-b border-[#262E4A]">
              <h4 className="text-xs font-extrabold flex items-center gap-1.5">
                <MessageCircle className="w-4 h-4 text-[#D946EF]" />
                Story Comments
              </h4>
              <button
                type="button"
                onClick={() => setShowCommentsSheet(false)}
                aria-label="Close comments sheet"
                className="p-1 text-gray-400 hover:text-white"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="flex-1 overflow-y-auto py-2 space-y-2 no-scrollbar">
              {commentsLoading ? (
                <div className="flex items-center justify-center py-8 text-xs text-gray-400 gap-2">
                  <Loader2 className="w-4 h-4 animate-spin" /> Loading comments...
                </div>
              ) : commentsList.length === 0 ? (
                <p className="text-xs text-gray-400 text-center py-8">No story comments yet. Start one below!</p>
              ) : (
                commentsList.map(c => (
                  <div key={c.id} className="flex items-start gap-2 p-2 rounded-xl bg-[#1A1F36]">
                    <VybeAvatar src={c.author.avatarUrl} name={c.author.displayName} size={28} className="w-7 h-7 shrink-0" />
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center justify-between gap-2">
                        <span className="text-[11px] font-bold truncate">{c.author.displayName}</span>
                        <span className="text-[10px] text-gray-400 shrink-0">{c.timestamp}</span>
                      </div>
                      <p className="text-xs text-gray-200 mt-0.5 break-words">{c.content}</p>
                    </div>
                  </div>
                ))
              )}
            </div>
            <form onSubmit={handleAddStoryComment} className="pt-2 border-t border-[#262E4A] flex items-center gap-2">
              <input
                type="text"
                value={commentDraft}
                onChange={e => setCommentDraft(e.target.value)}
                placeholder="Write a story comment..."
                className="flex-1 px-3 py-2 rounded-full bg-[#060714] border border-[#262E4A] text-xs text-white focus:outline-none focus:border-[#8B5CF6]"
              />
              <button
                type="submit"
                disabled={commentSubmitting || !commentDraft.trim()}
                aria-label="Submit story comment"
                className="p-2 rounded-full bg-gradient-to-r from-[#8B5CF6] to-[#00C6FF] text-white disabled:opacity-40"
              >
                <Send className="w-3.5 h-3.5" />
              </button>
            </form>
          </div>
        )}

        {/* Bottom Quick DM Reply + Global Finger Heart Reaction */}
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
            placeholder={isStoryOwner ? 'Open Comments above or add a reaction...' : `Reply to ${currentStory.author.displayName}...`}
            className="flex-1 px-3.5 py-2 bg-[#1A1F36] border border-white/15 rounded-full text-xs text-white keep-white placeholder-gray-400 focus:outline-none focus:border-[#00C6FF]"
          />
          {replyText.trim() && (
            <button
              type="submit"
              aria-label="Send story reply"
              className="p-2 rounded-full bg-gradient-to-r from-[#00C6FF] to-[#8B5CF6] text-white keep-white"
            >
              <Send className="w-4 h-4" />
            </button>
          )}
          <FingerHeartReaction
            isLiked={Boolean(currentStory.isLikedByMe || currentStory.myReaction)}
            userReaction={currentStory.myReaction}
            count={currentStory.reactionsCount || 0}
            showCount={(currentStory.reactionsCount || 0) > 0}
            forceDarkOverlay={true}
            size={23}
            onToggle={() => toggleStoryLike(currentStory.id)}
            onReact={(rx) => reactToStory(currentStory.id, rx)}
          />
        </form>
      </div>
    </div>
  );
};
