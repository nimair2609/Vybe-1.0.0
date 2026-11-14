import React, { useState, useEffect, useRef } from 'react';
import {
  MessageCircle, Bookmark, MapPin,
  Music, Volume2, VolumeX, Send, Link2, Share2, X, Lock
} from 'lucide-react';
import { Post, VYBE_REACTIONS, MOOD_DEFINITIONS } from '../types';
import { OfficialBadge } from './Badges';
import { FingerHeartReaction, FingerHeartDoubleTapOverlay, FingerHeartIcon } from './FingerHeartReaction';
import { useVybe } from '../context/VybeContext';
import { formatRelativeTimestamp } from '../utils/timestamp';
import { resolveMediaUrl } from '../services/supabaseService';
import { OptimizedImage, VybeAvatar } from './OptimizedImage';

interface PostCardProps {
  post: Post;
}

export const PostCard: React.FC<PostCardProps> = ({ post }) => {
  const {
    currentUser,
    conversations,
    openUserProfile,
    toggleLikePost,
    ensurePostLoved,
    reactToPost,
    toggleCommentLike,
    reactToComment,
    toggleSavePost,
    addCommentToPost,
    loadCommentsForPost,
    sharePostToConversation,
    incrementPostShareCount,
    isPlayingAudio,
    currentAudioTrack,
    togglePlayAudio
  } = useVybe();

  const [showReactionMenu, setShowReactionMenu] = useState(false);
  const [showComments, setShowComments] = useState(false);
  const [showShareSheet, setShowShareSheet] = useState(false);
  const [shareNote, setShareNote] = useState('');
  const [sentConvIds, setSentConvIds] = useState<Record<string, boolean>>({});
  const [commentInput, setCommentInput] = useState('');
  const [copyFeedback, setCopyFeedback] = useState(false);
  const [nowTick, setNowTick] = useState(() => Date.now());
  const videoRef = useRef<HTMLVideoElement>(null);

  useEffect(() => {
    const timer = setInterval(() => setNowTick(Date.now()), 30_000);
    return () => clearInterval(timer);
  }, []);

  // Performance & Egress: auto-pause video when scrolled out of viewport or when tab is hidden
  useEffect(() => {
    const el = videoRef.current;
    if (!el) return;

    const handleVisibility = () => {
      if (document.hidden && el && !el.paused) {
        el.pause();
      }
    };
    document.addEventListener('visibilitychange', handleVisibility);

    if (typeof IntersectionObserver === 'undefined') {
      return () => document.removeEventListener('visibilitychange', handleVisibility);
    }

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting && el && !el.paused) {
            el.pause();
          }
        });
      },
      { threshold: 0.25 }
    );

    observer.observe(el);
    return () => {
      observer.disconnect();
      document.removeEventListener('visibilitychange', handleVisibility);
    };
  }, [post.mediaUrls]);

  const moodConfig = post.moodZone ? MOOD_DEFINITIONS[post.moodZone] : null;
  const canonicalPostUrl = `${window.location.origin}/post/${encodeURIComponent(post.id)}`;
  const isRestrictedPrivate = Boolean(post.author.isPrivate && post.author.id !== currentUser?.id);
  const displayTimestamp = formatRelativeTimestamp(post.createdAtIso, post.timestamp, nowTick);

  const handleCopyLink = async () => {
    if (isRestrictedPrivate) return;
    await navigator.clipboard?.writeText(canonicalPostUrl);
    incrementPostShareCount(post.id);
    setCopyFeedback(true);
    setTimeout(() => setCopyFeedback(false), 2200);
  };

  const handleExternalWebShare = async () => {
    if (isRestrictedPrivate) return;
    if (navigator.share) {
      try {
        await navigator.share({
          title: `Post by @${post.author.username} on VYBE`,
          text: post.caption || `Check out @${post.author.username}'s post on VYBE`,
          url: canonicalPostUrl
        });
        incrementPostShareCount(post.id);
        setShowShareSheet(false);
        return;
      } catch {
        // user cancelled native share
      }
    }
    await handleCopyLink();
  };

  const handleAddComment = (e: React.FormEvent) => {
    e.preventDefault();
    if (commentInput.trim()) {
      addCommentToPost(post.id, commentInput);
      setCommentInput('');
    }
  };

  const isCurrentTrackPlaying =
    isPlayingAudio && currentAudioTrack === (post.musicAttachment?.title || post.audioTrackTitle);

  return (
    <article className="bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-2xl overflow-hidden transition-colors hover:border-[var(--vybe-accent)]/50">
      {/* Post Header — Single clean circular avatar border */}
      <div className="flex items-center justify-between p-4">
        <div className="flex items-center gap-3">
          <div
            onClick={() => openUserProfile(post.author)}
            className="relative shrink-0 cursor-pointer"
          >
            <VybeAvatar
              src={post.author.avatarUrl}
              name={post.author.displayName || post.author.username}
              size={40}
              className="w-10 h-10"
            />
            {post.author.isOnline && (
              <span className="absolute bottom-0 right-0 w-2.5 h-2.5 bg-[#22C55E] border-2 border-[var(--vybe-surface)] rounded-full" />
            )}
          </div>
          <div>
            <div className="flex items-center gap-1.5 flex-wrap">
              <span
                onClick={() => openUserProfile(post.author)}
                className="font-bold text-[var(--vybe-text)] text-sm hover:underline cursor-pointer"
              >
                {post.author.displayName}
              </span>
              {post.author.badges?.map(b => (
                <OfficialBadge key={b} badge={b} size="sm" />
              ))}
              <span
                onClick={() => openUserProfile(post.author)}
                className="text-xs text-[var(--vybe-text-secondary)] cursor-pointer hover:underline"
              >
                @{post.author.username}
              </span>
            </div>
            <div className="flex items-center gap-1.5 mt-0.5 text-[11px] text-[var(--vybe-text-muted)]">
              <span>{displayTimestamp}</span>
              {post.locationName && (
                <>
                  <span>·</span>
                  <span className="flex items-center gap-0.5 text-[var(--vybe-text-secondary)]">
                    <MapPin className="w-3 h-3 text-[var(--vybe-accent)]" />
                    <span className="truncate max-w-[140px]">{post.locationName}</span>
                  </span>
                </>
              )}
            </div>
          </div>
        </div>

        {moodConfig && (
          <div
            className={`px-2.5 py-1 rounded-full text-xs font-semibold flex items-center gap-1 border border-white/10 bg-gradient-to-r ${moodConfig.gradient} text-white keep-white shadow-sm`}
          >
            <span>{moodConfig.emoji}</span>
            <span className="hidden sm:inline">{moodConfig.title}</span>
          </div>
        )}
      </div>

      {/* Caption & Tags */}
      <div className="px-4 pb-3">
        <p className="text-sm text-[var(--vybe-text)] leading-relaxed whitespace-pre-line">{post.caption}</p>
        {post.tags?.length > 0 && (
          <div className="flex flex-wrap gap-1.5 mt-2">
            {post.tags.map(tag => (
              <span key={tag} className="text-xs font-medium text-[var(--vybe-accent)] hover:underline cursor-pointer">
                #{tag}
              </span>
            ))}
          </div>
        )}
      </div>

      {/* Media Attachment with Double-Tap Finger Heart Burst */}
      {post.mediaUrls?.length > 0 && (
        <div className="px-3 pb-1">
          <FingerHeartDoubleTapOverlay
            onDoubleTap={() => ensurePostLoved(post.id)}
            className="w-full max-h-[500px] bg-[var(--vybe-elevated)] rounded-xl overflow-hidden flex items-center justify-center"
          >
            {post.mediaType === 'VIDEO' || post.mediaType === 'REEL' ? (
              <video
                ref={videoRef}
                src={resolveMediaUrl(post.mediaUrls[0])}
                controls
                playsInline
                preload="none"
                className="w-full h-full object-cover max-h-[500px]"
              />
            ) : (
              <OptimizedImage
                src={resolveMediaUrl(post.mediaUrls[0])}
                alt="Post content"
                aspectRatio="4 / 5"
                className="w-full h-full object-cover max-h-[500px]"
              />
            )}
            {post.isVybesReel && (
              <div className="absolute top-3 right-3 px-2 py-1 rounded-lg bg-black/60 backdrop-blur-md text-[10px] font-bold text-white keep-white uppercase tracking-wider">
                VYBE Reel
              </div>
            )}
          </FingerHeartDoubleTapOverlay>
        </div>
      )}

      {/* Music Track Banner if attached */}
      {(post.musicAttachment || post.audioTrackTitle) && (
        <div className="mx-3 mt-2 px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] flex items-center justify-between">
          <div className="flex items-center gap-2.5 overflow-hidden">
            <div className="p-1.5 rounded-lg bg-[var(--vybe-accent-soft)] text-[var(--vybe-accent)]">
              <Music className={`w-4 h-4 ${isCurrentTrackPlaying ? 'animate-bounce' : ''}`} />
            </div>
            <div className="truncate">
              <p className="text-xs font-semibold text-[var(--vybe-text)] truncate">
                {post.musicAttachment ? post.musicAttachment.title : post.audioTrackTitle}
              </p>
              {post.musicAttachment?.artist && (
                <p className="text-[10px] text-[var(--vybe-text-secondary)] truncate">{post.musicAttachment.artist}</p>
              )}
            </div>
          </div>
          <button
            onClick={() =>
              togglePlayAudio(
                post.musicAttachment?.title || post.audioTrackTitle || 'Music Track',
                post.musicAttachment?.previewUrl
              )
            }
            className="p-1.5 rounded-full bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-[var(--vybe-text)] text-xs transition-colors shrink-0"
          >
            {isCurrentTrackPlaying ? (
              <Volume2 className="w-4 h-4 text-[var(--vybe-accent)]" />
            ) : (
              <VolumeX className="w-4 h-4 text-[var(--vybe-text-muted)]" />
            )}
          </button>
        </div>
      )}

      {/* Engagement Actions Bar */}
      <div className="px-4 py-3">
        <div className="flex items-center justify-between min-h-[36px]">
          <div className="flex items-center gap-5 relative">
            {/* 1. Global VYBE Universal Reaction */}
            <div
              className="relative flex items-center"
              onMouseEnter={() => setShowReactionMenu(true)}
              onMouseLeave={() => setShowReactionMenu(false)}
            >
              <FingerHeartReaction
                isLiked={post.isLiked || Boolean(post.userReaction)}
                userReaction={post.userReaction}
                count={post.likesCount}
                onToggle={() => toggleLikePost(post.id)}
                onReact={(rx) => reactToPost(post.id, rx)}
              />

              {showReactionMenu && (
                <div className="absolute bottom-9 left-0 flex items-center gap-2 p-2 bg-[var(--vybe-surface)] border border-[var(--vybe-accent)]/50 rounded-full shadow-2xl z-20 animate-in fade-in slide-in-from-bottom-2">
                  {VYBE_REACTIONS.map(reaction => (
                    <button
                      key={reaction.id}
                      type="button"
                      onClick={() => {
                        reactToPost(post.id, reaction.id);
                        setShowReactionMenu(false);
                      }}
                      title={reaction.label}
                      className="text-xl hover:scale-125 transition-transform p-1.5 rounded-full hover:bg-white/10 inline-flex items-center justify-center"
                    >
                      {reaction.id === 'LOVE' ? (
                        <FingerHeartIcon isLiked={true} size={20} forceDarkOverlay={true} />
                      ) : (
                        reaction.emoji
                      )}
                    </button>
                  ))}
                </div>
              )}
            </div>

            {/* 2. Comment */}
            <button
              type="button"
              onClick={() => {
                const nextOpen = !showComments;
                setShowComments(nextOpen);
                if (nextOpen) loadCommentsForPost(post.id);
              }}
              className="inline-flex items-center justify-center gap-1.5 h-9 px-1.5 rounded-lg text-xs font-semibold text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)] transition-colors"
            >
              <span className="w-5 h-5 inline-flex items-center justify-center">
                <MessageCircle className="w-5 h-5" />
              </span>
              {post.commentsCount > 0 && <span>{post.commentsCount}</span>}
            </button>

            {/* 3. Share */}
            <button
              type="button"
              onClick={() => setShowShareSheet(!showShareSheet)}
              aria-label="Share post"
              className="inline-flex items-center justify-center gap-1.5 h-9 px-1.5 rounded-lg text-xs font-semibold text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)] transition-colors relative"
            >
              <span className="w-5 h-5 inline-flex items-center justify-center">
                <Send className="w-[19px] h-[19px]" />
              </span>
              {post.sharesCount > 0 && <span>{post.sharesCount}</span>}
              {copyFeedback && (
                <span className="absolute -top-7 left-1/2 -translate-x-1/2 px-2 py-0.5 rounded bg-[var(--vybe-accent)] text-white keep-white text-[10px] font-bold shadow whitespace-nowrap">
                  Copied!
                </span>
              )}
            </button>
          </div>

          {/* 4. Bookmark / Save */}
          <button
            type="button"
            onClick={() => toggleSavePost(post.id)}
            aria-label="Save post"
            className={`inline-flex items-center justify-center w-9 h-9 rounded-lg transition-colors ${
              post.isSaved ? 'text-[var(--vybe-accent)]' : 'text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)]'
            }`}
          >
            <Bookmark className={`w-5 h-5 ${post.isSaved ? 'fill-current' : ''}`} />
          </button>
        </div>

        {/* Reaction Breakdown Pills */}
        {post.reactionsCount && Object.values(post.reactionsCount).some(c => c > 0) && (
          <div className="flex items-center gap-2 mt-3 pt-2.5 border-t border-[var(--vybe-border)] text-[11px] text-[var(--vybe-text-secondary)]">
            <span className="font-medium">Vibes:</span>
            {VYBE_REACTIONS.map(r => {
              const count = post.reactionsCount[r.id] || 0;
              if (count === 0) return null;
              return (
                <span
                  key={r.id}
                  className="flex items-center gap-1 px-2 py-0.5 rounded-full bg-[var(--vybe-elevated)] border border-[var(--vybe-border)]"
                >
                  <span className="inline-flex items-center">
                    {r.id === 'LOVE' ? (
                      <FingerHeartIcon isLiked={true} size={13} />
                    ) : (
                      r.emoji
                    )}
                  </span>
                  <span className="text-[var(--vybe-text)] font-semibold">{count}</span>
                </span>
              );
            })}
          </div>
        )}
      </div>

      {/* Expandable Share Panel (Internal DM + Copy Link + External Share) */}
      {showShareSheet && (
        <div className="p-4 bg-[var(--vybe-elevated)] border-t border-[var(--vybe-border)] space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-[var(--vybe-text)]">Share Post</span>
            <button
              type="button"
              onClick={() => setShowShareSheet(false)}
              className="text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)] p-1"
            >
              <X className="w-4 h-4" />
            </button>
          </div>

          {isRestrictedPrivate && (
            <div className="p-2.5 rounded-xl bg-amber-500/10 border border-amber-500/30 text-amber-500 text-xs flex items-center gap-2">
              <Lock className="w-4 h-4 shrink-0" />
              <span>Private account posts cannot be shared via external links.</span>
            </div>
          )}

          <div className="grid grid-cols-2 gap-2">
            <button
              type="button"
              disabled={isRestrictedPrivate}
              onClick={handleCopyLink}
              className="flex items-center justify-center gap-1.5 py-2 px-3 rounded-xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] disabled:opacity-40 text-xs font-semibold text-[var(--vybe-text)] transition-colors"
            >
              <Link2 className="w-3.5 h-3.5 text-[var(--vybe-accent)]" />
              <span>{copyFeedback ? 'Copied!' : 'Copy Link'}</span>
            </button>
            <button
              type="button"
              disabled={isRestrictedPrivate}
              onClick={handleExternalWebShare}
              className="flex items-center justify-center gap-1.5 py-2 px-3 rounded-xl bg-[var(--vybe-accent)] disabled:opacity-40 text-xs font-bold text-white keep-white shadow-sm hover:opacity-95"
            >
              <Share2 className="w-3.5 h-3.5" />
              <span>Share Externally</span>
            </button>
          </div>

          <input
            type="text"
            value={shareNote}
            onChange={(e) => setShareNote(e.target.value)}
            placeholder="Write an optional note for Direct Message..."
            className="w-full px-3 py-2 bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-xl text-xs text-[var(--vybe-text)] placeholder-[var(--vybe-text-muted)] focus:outline-none focus:border-[var(--vybe-accent)]"
          />

          <div className="space-y-1.5 max-h-40 overflow-y-auto no-scrollbar">
            {conversations.map(conv => {
              const sent = Boolean(sentConvIds[conv.id]);
              return (
                <div
                  key={conv.id}
                  className="flex items-center justify-between p-2 rounded-xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)]"
                >
                  <div className="flex items-center gap-2.5 overflow-hidden">
                    <VybeAvatar
                      src={conv.peerUser.avatarUrl}
                      name={conv.peerUser.displayName || conv.peerUser.username}
                      size={28}
                      className="w-7 h-7 shrink-0"
                    />
                    <div className="truncate">
                      <p className="text-xs font-bold text-[var(--vybe-text)] truncate">{conv.peerUser.displayName}</p>
                      <p className="text-[10px] text-[var(--vybe-text-secondary)] truncate">@{conv.peerUser.username}</p>
                    </div>
                  </div>
                  <button
                    type="button"
                    disabled={sent || (isRestrictedPrivate && conv.peerUser.id !== post.author.id)}
                    onClick={() => {
                      sharePostToConversation(post, conv.id, shareNote);
                      setSentConvIds(prev => ({ ...prev, [conv.id]: true }));
                    }}
                    className={`px-3 py-1 rounded-lg text-xs font-bold transition-colors ${
                      sent
                        ? 'bg-emerald-600 text-white keep-white'
                        : 'bg-[var(--vybe-accent)] hover:opacity-95 text-white keep-white disabled:opacity-40'
                    }`}
                  >
                    {sent ? 'Sent' : 'Send'}
                  </button>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Expandable Comments Section */}
      {showComments && (
        <div className="p-4 bg-[var(--vybe-elevated)] border-t border-[var(--vybe-border)] space-y-3">
          <div className="space-y-2.5 max-h-60 overflow-y-auto no-scrollbar">
            {post.comments?.length === 0 ? (
              <p className="text-xs text-[var(--vybe-text-secondary)] py-2 text-center">
                No comments yet. Start the conversation!
              </p>
            ) : (
              post.comments?.map(c => (
                <div key={c.id} className="flex items-start gap-2.5 text-xs">
                  <div onClick={() => openUserProfile(c.author)} className="shrink-0 mt-0.5 cursor-pointer">
                    <VybeAvatar
                      src={c.author.avatarUrl}
                      name={c.author.displayName || c.author.username}
                      size={28}
                      className="w-7 h-7"
                    />
                  </div>
                  <div className="flex-1 bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-xl p-2.5">
                    <div className="flex items-center justify-between gap-2">
                      <span
                        onClick={() => openUserProfile(c.author)}
                        className="font-bold text-[var(--vybe-text)] cursor-pointer hover:underline"
                      >
                        {c.author.displayName}
                      </span>
                      <div className="flex items-center gap-1.5">
                        <span className="text-[10px] text-[var(--vybe-text-muted)]">
                          {formatRelativeTimestamp(c.createdAtIso, c.timestamp, nowTick)}
                        </span>
                        <FingerHeartReaction
                          isLiked={Boolean(c.isLiked || c.userReaction)}
                          userReaction={c.userReaction}
                          count={c.likesCount || 0}
                          size={15}
                          onToggle={() => toggleCommentLike(post.id, c.id)}
                          onReact={(rx) => reactToComment(post.id, c.id, rx)}
                          className="h-6 px-1"
                        />
                      </div>
                    </div>
                    <p className="text-[var(--vybe-text-secondary)] mt-1 leading-relaxed">{c.content}</p>
                  </div>
                </div>
              ))
            )}
          </div>

          <form onSubmit={handleAddComment} className="flex items-center gap-2 pt-2 border-t border-[var(--vybe-border)]">
            <input
              type="text"
              value={commentInput}
              onChange={(e) => setCommentInput(e.target.value)}
              placeholder="Drop a vibe or supportive comment..."
              className="flex-1 px-3 py-2 bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-xl text-xs text-[var(--vybe-text)] placeholder-[var(--vybe-text-muted)] focus:outline-none focus:border-[var(--vybe-accent)]"
            />
            <button
              type="submit"
              disabled={!commentInput.trim()}
              aria-label="Post comment"
              className="p-2 bg-[var(--vybe-accent)] disabled:opacity-40 text-white keep-white rounded-xl shadow-sm hover:opacity-95 transition-opacity"
            >
              <Send className="w-3.5 h-3.5" />
            </button>
          </form>
        </div>
      )}
    </article>
  );
};
