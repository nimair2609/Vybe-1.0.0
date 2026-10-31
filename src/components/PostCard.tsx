import React, { useState, useEffect } from 'react';
import {
  MessageCircle, Bookmark, MapPin,
  Music, Volume2, VolumeX, Send, Link2, Share2, X, Lock
} from 'lucide-react';
import { Post, VYBE_REACTIONS, MOOD_DEFINITIONS } from '../types';
import { OfficialBadge } from './Badges';
import { FingerHeartReaction, FingerHeartDoubleTapOverlay, FingerHeartIcon } from './FingerHeartReaction';
import { useVybe } from '../context/VybeContext';
import { formatRelativeTimestamp } from '../utils/timestamp';

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
    toggleSavePost,
    addCommentToPost,
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

  useEffect(() => {
    const timer = setInterval(() => setNowTick(Date.now()), 30_000);
    return () => clearInterval(timer);
  }, []);

  const moodConfig = post.moodZone ? MOOD_DEFINITIONS[post.moodZone] : null;
  const canonicalPostUrl = `${window.location.origin}/post/${encodeURIComponent(post.id)}`;
  const isRestrictedPrivate = Boolean(post.author.isPrivate && post.author.id !== currentUser.id);
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
    <article className="bg-[#101322] border border-[#262E4A]/80 rounded-2xl shadow-xl overflow-hidden transition-all hover:border-[#8B5CF6]/40">
      {/* Post Header — Single clean circular avatar border */}
      <div className="flex items-center justify-between p-4">
        <div className="flex items-center gap-3">
          <div
            onClick={() => openUserProfile(post.author)}
            className="relative shrink-0 cursor-pointer"
          >
            <img
              src={post.author.avatarUrl}
              alt={post.author.displayName}
              className="w-11 h-11 rounded-full object-cover border border-[#8B5CF6]/60"
            />
            {post.author.isOnline && (
              <span className="absolute bottom-0 right-0 w-3 h-3 bg-[#22C55E] border-2 border-[#101322] rounded-full" />
            )}
          </div>
          <div>
            <div className="flex items-center gap-1.5 flex-wrap">
              <span
                onClick={() => openUserProfile(post.author)}
                className="font-bold text-white text-sm hover:underline cursor-pointer"
              >
                {post.author.displayName}
              </span>
              {post.author.badges?.map(b => (
                <OfficialBadge key={b} badge={b} size="sm" />
              ))}
              <span
                onClick={() => openUserProfile(post.author)}
                className="text-xs text-gray-400 font-mono cursor-pointer hover:underline"
              >
                @{post.author.username}
              </span>
            </div>
            <div className="flex items-center gap-2 mt-0.5 text-[11px] text-gray-400">
              <span>{displayTimestamp}</span>
              <span>•</span>
              <span className="font-mono text-[#00C6FF] font-semibold">{post.author.publicVybeId}</span>
              {post.locationName && (
                <>
                  <span>•</span>
                  <span className="flex items-center gap-0.5 text-gray-300">
                    <MapPin className="w-3 h-3 text-[#FFB703]" />
                    <span className="truncate max-w-[120px]">{post.locationName}</span>
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
        <p className="text-sm text-gray-100 leading-relaxed whitespace-pre-line">{post.caption}</p>
        {post.tags?.length > 0 && (
          <div className="flex flex-wrap gap-1.5 mt-2">
            {post.tags.map(tag => (
              <span key={tag} className="text-xs font-medium text-[#00C6FF] hover:underline cursor-pointer">
                #{tag}
              </span>
            ))}
          </div>
        )}
      </div>

      {/* Media Attachment with Safe Double-Tap Finger Heart Burst */}
      {post.mediaUrls?.length > 0 && (
        <FingerHeartDoubleTapOverlay
          onDoubleTap={() => ensurePostLoved(post.id)}
          className="w-full max-h-[500px] bg-[#060714] overflow-hidden flex items-center justify-center"
        >
          {post.mediaType === 'VIDEO' || post.mediaType === 'REEL' ? (
            <video
              src={post.mediaUrls[0]}
              controls
              playsInline
              className="w-full h-full object-cover max-h-[500px]"
            />
          ) : (
            <img
              src={post.mediaUrls[0]}
              alt="Post content"
              className="w-full h-full object-cover max-h-[500px]"
            />
          )}
          {post.isVybesReel && (
            <div className="absolute top-3 right-3 px-2 py-1 rounded-lg bg-black/60 backdrop-blur-md text-[10px] font-bold text-white keep-white uppercase tracking-wider">
              VYBES Reel
            </div>
          )}
        </FingerHeartDoubleTapOverlay>
      )}

      {/* Music Track Banner if attached */}
      {(post.musicAttachment || post.audioTrackTitle) && (
        <div className="px-4 py-2.5 bg-[#060714]/80 border-t border-[#262E4A]/40 flex items-center justify-between">
          <div className="flex items-center gap-2.5 overflow-hidden">
            <div className="p-1.5 rounded-lg bg-[#8B5CF6]/20 text-[#00C6FF]">
              <Music className={`w-4 h-4 ${isCurrentTrackPlaying ? 'animate-bounce' : ''}`} />
            </div>
            <div className="truncate">
              <p className="text-xs font-semibold text-white truncate">
                {post.musicAttachment ? post.musicAttachment.title : post.audioTrackTitle}
              </p>
              {post.musicAttachment?.artist && (
                <p className="text-[10px] text-gray-400 truncate">{post.musicAttachment.artist}</p>
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
            className="p-1.5 rounded-full bg-[#1A1F36] hover:bg-[#262E4A] text-white text-xs transition-colors shrink-0"
          >
            {isCurrentTrackPlaying ? (
              <Volume2 className="w-4 h-4 text-[#00C6FF]" />
            ) : (
              <VolumeX className="w-4 h-4 text-gray-400" />
            )}
          </button>
        </div>
      )}

      {/* Engagement Actions Bar — Uniform h-9 row, optically centered 20px icons, equal spacing */}
      <div className="p-4 border-t border-[#262E4A]/50">
        <div className="flex items-center justify-between min-h-[36px]">
          <div className="flex items-center gap-5 relative">
            {/* 1. Global Finger Heart Reaction */}
            <div
              className="relative flex items-center"
              onMouseEnter={() => setShowReactionMenu(true)}
              onMouseLeave={() => setShowReactionMenu(false)}
            >
              {post.userReaction && post.userReaction !== 'LOVE' ? (
                <button
                  type="button"
                  onClick={() => toggleLikePost(post.id)}
                  className="inline-flex items-center justify-center gap-1.5 h-9 px-1.5 rounded-lg text-xs font-semibold text-[#FF2E93] transition-colors"
                >
                  <span className="w-5 h-5 inline-flex items-center justify-center text-base leading-none">
                    {VYBE_REACTIONS.find(r => r.id === post.userReaction)?.emoji}
                  </span>
                  {post.likesCount > 0 && <span>{post.likesCount.toLocaleString()}</span>}
                </button>
              ) : (
                <FingerHeartReaction
                  isLiked={post.isLiked}
                  count={post.likesCount}
                  onToggle={() => toggleLikePost(post.id)}
                />
              )}

              {showReactionMenu && (
                <div className="absolute bottom-9 left-0 flex items-center gap-2 p-2 bg-[#1A1F36] border border-[#262E4A] rounded-full shadow-2xl z-20 animate-in fade-in slide-in-from-bottom-2">
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
              onClick={() => setShowComments(!showComments)}
              className="inline-flex items-center justify-center gap-1.5 h-9 px-1.5 rounded-lg text-xs font-semibold text-gray-400 hover:text-white transition-colors"
            >
              <span className="w-5 h-5 inline-flex items-center justify-center">
                <MessageCircle className="w-5 h-5" />
              </span>
              {post.commentsCount > 0 && <span>{post.commentsCount}</span>}
            </button>

            {/* 3. Share — Optically centered & aligned on identical baseline */}
            <button
              type="button"
              onClick={() => setShowShareSheet(!showShareSheet)}
              aria-label="Share post"
              className="inline-flex items-center justify-center gap-1.5 h-9 px-1.5 rounded-lg text-xs font-semibold text-gray-400 hover:text-white transition-colors relative"
            >
              <span className="w-5 h-5 inline-flex items-center justify-center">
                <Send className="w-[19px] h-[19px]" />
              </span>
              {post.sharesCount > 0 && <span>{post.sharesCount}</span>}
              {copyFeedback && (
                <span className="absolute -top-7 left-1/2 -translate-x-1/2 px-2 py-0.5 rounded bg-[#00C6FF] text-[#060714] text-[10px] font-bold shadow whitespace-nowrap">
                  Link copied!
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
              post.isSaved ? 'text-[#00C6FF]' : 'text-gray-400 hover:text-white'
            }`}
          >
            <Bookmark className={`w-5 h-5 ${post.isSaved ? 'fill-current' : ''}`} />
          </button>
        </div>

        {/* Reaction Breakdown Pills */}
        {post.reactionsCount && Object.values(post.reactionsCount).some(c => c > 0) && (
          <div className="flex items-center gap-2 mt-3 pt-2.5 border-t border-[#262E4A]/30 text-[11px] text-gray-400">
            <span className="font-medium">Vibes:</span>
            {VYBE_REACTIONS.map(r => {
              const count = post.reactionsCount[r.id] || 0;
              if (count === 0) return null;
              return (
                <span
                  key={r.id}
                  className="flex items-center gap-1 px-2 py-0.5 rounded-full bg-[#060714] border border-[#262E4A]"
                >
                  <span>{r.emoji}</span>
                  <span className="text-gray-300 font-semibold">{count}</span>
                </span>
              );
            })}
          </div>
        )}
      </div>

      {/* Expandable Share Panel (Internal DM + Copy Link + External Share) */}
      {showShareSheet && (
        <div className="p-4 bg-[#060714]/90 border-t border-[#262E4A] space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-white">Share Post</span>
            <button
              type="button"
              onClick={() => setShowShareSheet(false)}
              className="text-gray-400 hover:text-white p-1"
            >
              <X className="w-4 h-4" />
            </button>
          </div>

          {isRestrictedPrivate && (
            <div className="p-2.5 rounded-xl bg-amber-500/10 border border-amber-500/30 text-amber-300 text-xs flex items-center gap-2">
              <Lock className="w-4 h-4 shrink-0" />
              <span>Private account posts cannot be shared via external links.</span>
            </div>
          )}

          <div className="grid grid-cols-2 gap-2">
            <button
              type="button"
              disabled={isRestrictedPrivate}
              onClick={handleCopyLink}
              className="flex items-center justify-center gap-1.5 py-2 px-3 rounded-xl bg-[#101322] border border-[#262E4A] hover:border-[#00C6FF]/60 disabled:opacity-40 text-xs font-semibold text-white transition-colors"
            >
              <Link2 className="w-3.5 h-3.5 text-[#00C6FF]" />
              <span>{copyFeedback ? 'Copied!' : 'Copy Link'}</span>
            </button>
            <button
              type="button"
              disabled={isRestrictedPrivate}
              onClick={handleExternalWebShare}
              className="flex items-center justify-center gap-1.5 py-2 px-3 rounded-xl bg-gradient-to-r from-[#8B5CF6] to-[#00C6FF] disabled:opacity-40 text-xs font-bold text-white keep-white shadow"
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
            className="w-full px-3 py-2 bg-[#101322] border border-[#262E4A] rounded-xl text-xs text-white focus:outline-none focus:border-[#8B5CF6]"
          />

          <div className="space-y-1.5 max-h-40 overflow-y-auto no-scrollbar">
            {conversations.map(conv => {
              const sent = Boolean(sentConvIds[conv.id]);
              return (
                <div
                  key={conv.id}
                  className="flex items-center justify-between p-2 rounded-xl bg-[#101322] border border-[#262E4A]"
                >
                  <div className="flex items-center gap-2.5 overflow-hidden">
                    <img
                      src={conv.peerUser.avatarUrl}
                      alt={conv.peerUser.displayName}
                      className="w-7 h-7 rounded-full object-cover shrink-0"
                    />
                    <div className="truncate">
                      <p className="text-xs font-bold text-white truncate">{conv.peerUser.displayName}</p>
                      <p className="text-[10px] text-gray-400 truncate">@{conv.peerUser.username}</p>
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
                        : 'bg-[#8B5CF6] hover:bg-[#7C3AED] text-white keep-white disabled:opacity-40'
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
        <div className="p-4 bg-[#060714]/90 border-t border-[#262E4A] space-y-3">
          <div className="space-y-2.5 max-h-60 overflow-y-auto no-scrollbar">
            {post.comments?.length === 0 ? (
              <p className="text-xs text-gray-400 py-2 text-center">No comments yet. Start the conversation!</p>
            ) : (
              post.comments?.map(c => (
                <div key={c.id} className="flex items-start gap-2.5 text-xs">
                  <img
                    src={c.author.avatarUrl}
                    alt={c.author.displayName}
                    onClick={() => openUserProfile(c.author)}
                    className="w-7 h-7 rounded-full object-cover shrink-0 mt-0.5 cursor-pointer"
                  />
                  <div className="flex-1 bg-[#101322] border border-[#262E4A] rounded-xl p-2.5">
                    <div className="flex items-center justify-between gap-2">
                      <span
                        onClick={() => openUserProfile(c.author)}
                        className="font-bold text-white cursor-pointer hover:underline"
                      >
                        {c.author.displayName}
                      </span>
                      <div className="flex items-center gap-1.5">
                        <span className="text-[10px] text-gray-400">
                          {formatRelativeTimestamp(c.createdAtIso, c.timestamp, nowTick)}
                        </span>
                        <FingerHeartReaction
                          isLiked={Boolean(c.isLiked)}
                          count={c.likesCount || 0}
                          size={15}
                          onToggle={() => toggleCommentLike(post.id, c.id)}
                          className="h-6 px-1"
                        />
                      </div>
                    </div>
                    <p className="text-gray-200 mt-1 leading-relaxed">{c.content}</p>
                  </div>
                </div>
              ))
            )}
          </div>

          <form onSubmit={handleAddComment} className="flex items-center gap-2 pt-2 border-t border-[#262E4A]">
            <input
              type="text"
              value={commentInput}
              onChange={(e) => setCommentInput(e.target.value)}
              placeholder="Drop a vibe or supportive comment..."
              className="flex-1 px-3 py-2 bg-[#101322] border border-[#262E4A] rounded-xl text-xs text-white focus:outline-none focus:border-[#8B5CF6]"
            />
            <button
              type="submit"
              disabled={!commentInput.trim()}
              className="p-2 bg-gradient-to-r from-[#00C6FF] to-[#8B5CF6] disabled:opacity-40 text-white keep-white rounded-xl shadow transition-opacity"
            >
              <Send className="w-3.5 h-3.5" />
            </button>
          </form>
        </div>
      )}
    </article>
  );
};
