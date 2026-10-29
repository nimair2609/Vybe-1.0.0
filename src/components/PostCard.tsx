import React, { useState } from 'react';
import { 
  Heart, MessageCircle, Bookmark, Share2, MapPin, 
  Music, Volume2, VolumeX, Send, Sparkles, MoreHorizontal,
  Flame, Smile, Award
} from 'lucide-react';
import { Post, VYBE_REACTIONS, VybeReaction, MOOD_DEFINITIONS } from '../types';
import { OfficialBadge } from './Badges';
import { useVybe } from '../context/VybeContext';

interface PostCardProps {
  post: Post;
}

export const PostCard: React.FC<PostCardProps> = ({ post }) => {
  const { 
    toggleLikePost, reactToPost, toggleSavePost, 
    addCommentToPost, isPlayingAudio, currentAudioTrack, 
    togglePlayAudio, openModal 
  } = useVybe();

  const [showReactionMenu, setShowReactionMenu] = useState(false);
  const [showComments, setShowComments] = useState(false);
  const [commentInput, setCommentInput] = useState('');
  const [copyFeedback, setCopyFeedback] = useState(false);

  const moodConfig = post.moodZone ? MOOD_DEFINITIONS[post.moodZone] : null;

  const handleShare = () => {
    navigator.clipboard?.writeText(window.location.origin + `#post_${post.id}`);
    setCopyFeedback(true);
    setTimeout(() => setCopyFeedback(false), 2000);
  };

  const handleAddComment = (e: React.FormEvent) => {
    e.preventDefault();
    if (commentInput.trim()) {
      addCommentToPost(post.id, commentInput);
      setCommentInput('');
    }
  };

  const isCurrentTrackPlaying = isPlayingAudio && currentAudioTrack === (post.musicAttachment?.title || post.audioTrackTitle);

  return (
    <article className="bg-[#101322] border border-[#262E4A]/80 rounded-2xl shadow-xl overflow-hidden transition-all hover:border-[#8B5CF6]/40">
      
      {/* Post Header */}
      <div className="flex items-center justify-between p-4">
        <div className="flex items-center gap-3">
          <div className="relative">
            <img 
              src={post.author.avatarUrl} 
              alt={post.author.displayName}
              className="w-11 h-11 rounded-full object-cover border border-[#262E4A]"
            />
            {post.author.isOnline && (
              <span className="absolute bottom-0 right-0 w-3 h-3 bg-[#22C55E] border-2 border-[#101322] rounded-full" />
            )}
          </div>
          <div>
            <div className="flex items-center gap-1.5 flex-wrap">
              <span className="font-bold text-white text-sm hover:underline cursor-pointer">
                {post.author.displayName}
              </span>
              {/* Badges */}
              {post.author.badges?.map(b => (
                <OfficialBadge key={b} badge={b} size="sm" />
              ))}
              <span className="text-xs text-gray-400 font-mono">
                @{post.author.username}
              </span>
            </div>
            <div className="flex items-center gap-2 mt-0.5 text-[11px] text-gray-400">
              <span>{post.timestamp}</span>
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

        {/* Mood Pill */}
        {moodConfig && (
          <div className={`px-2.5 py-1 rounded-full text-xs font-semibold flex items-center gap-1 border border-white/10 bg-gradient-to-r ${moodConfig.gradient} text-white shadow-sm`}>
            <span>{moodConfig.emoji}</span>
            <span className="hidden sm:inline">{moodConfig.title}</span>
          </div>
        )}
      </div>

      {/* Caption & Tags */}
      <div className="px-4 pb-3">
        <p className="text-sm text-gray-100 leading-relaxed whitespace-pre-line">
          {post.caption}
        </p>
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

      {/* Media Attachment */}
      {post.mediaUrls?.length > 0 && (
        <div className="relative w-full max-h-[500px] bg-[#060714] overflow-hidden flex items-center justify-center">
          <img 
            src={post.mediaUrls[0]} 
            alt="Post content" 
            className="w-full h-full object-cover max-h-[500px]"
          />
          {post.isVybesReel && (
            <div className="absolute top-3 right-3 px-2 py-1 rounded-lg bg-black/60 backdrop-blur-md text-[10px] font-bold text-white uppercase tracking-wider">
              VYBES Reel
            </div>
          )}
        </div>
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
                <p className="text-[10px] text-gray-400 truncate">
                  {post.musicAttachment.artist}
                </p>
              )}
            </div>
          </div>
          <button
            onClick={() => togglePlayAudio(
              post.musicAttachment?.title || post.audioTrackTitle || 'Music Track',
              post.musicAttachment?.previewUrl
            )}
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

      {/* Engagement Actions Bar */}
      <div className="p-4 border-t border-[#262E4A]/50">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4 relative">
            
            {/* Reaction / Like Button with Hover Popover */}
            <div 
              className="relative"
              onMouseEnter={() => setShowReactionMenu(true)}
              onMouseLeave={() => setShowReactionMenu(false)}
            >
              <button
                onClick={() => toggleLikePost(post.id)}
                className={`flex items-center gap-1.5 text-xs font-semibold transition-colors ${
                  post.isLiked ? 'text-[#EC4899]' : 'text-gray-400 hover:text-white'
                }`}
              >
                {post.userReaction ? (
                  <span className="text-base">
                    {VYBE_REACTIONS.find(r => r.id === post.userReaction)?.emoji || '🫰'}
                  </span>
                ) : (
                  <Heart className={`w-5 h-5 ${post.isLiked ? 'fill-current' : ''}`} />
                )}
                <span>{post.likesCount}</span>
              </button>

              {/* 5 Reaction Hover Picker */}
              {showReactionMenu && (
                <div className="absolute bottom-8 left-0 flex items-center gap-2 p-2 bg-[#1A1F36] border border-[#262E4A] rounded-full shadow-2xl z-20 animate-in fade-in slide-in-from-bottom-2">
                  {VYBE_REACTIONS.map(reaction => (
                    <button
                      key={reaction.id}
                      onClick={() => {
                        reactToPost(post.id, reaction.id);
                        setShowReactionMenu(false);
                      }}
                      title={reaction.label}
                      className="text-xl hover:scale-125 transition-transform p-1 rounded-full hover:bg-white/10"
                    >
                      {reaction.emoji}
                    </button>
                  ))}
                </div>
              )}
            </div>

            {/* Comments toggle */}
            <button
              onClick={() => setShowComments(!showComments)}
              className="flex items-center gap-1.5 text-xs font-semibold text-gray-400 hover:text-white transition-colors"
            >
              <MessageCircle className="w-5 h-5" />
              <span>{post.commentsCount}</span>
            </button>

            {/* Share */}
            <button
              onClick={handleShare}
              className="flex items-center gap-1.5 text-xs font-semibold text-gray-400 hover:text-white transition-colors relative"
            >
              <Share2 className="w-5 h-5" />
              {copyFeedback && (
                <span className="absolute -top-7 left-1/2 -translate-x-1/2 px-2 py-0.5 rounded bg-[#00C6FF] text-[#060714] text-[10px] font-bold shadow whitespace-nowrap animate-fade-in">
                  Link copied!
                </span>
              )}
            </button>
          </div>

          {/* Bookmark / Save */}
          <button
            onClick={() => toggleSavePost(post.id)}
            className={`p-1.5 rounded-lg transition-colors ${
              post.isSaved ? 'text-[#FFB703]' : 'text-gray-400 hover:text-white'
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
                <span key={r.id} className="flex items-center gap-1 px-2 py-0.5 rounded-full bg-[#060714] border border-[#262E4A]">
                  <span>{r.emoji}</span>
                  <span className="text-gray-300 font-semibold">{count}</span>
                </span>
              );
            })}
          </div>
        )}
      </div>

      {/* Expandable Comments Section */}
      {showComments && (
        <div className="p-4 bg-[#060714]/90 border-t border-[#262E4A] space-y-3">
          
          {/* Comments List */}
          <div className="space-y-2.5 max-h-60 overflow-y-auto no-scrollbar">
            {post.comments?.length === 0 ? (
              <p className="text-xs text-gray-400 py-2 text-center">No comments yet. Start the conversation!</p>
            ) : (
              post.comments?.map(c => (
                <div key={c.id} className="flex items-start gap-2.5 text-xs">
                  <img 
                    src={c.author.avatarUrl} 
                    alt={c.author.displayName}
                    className="w-7 h-7 rounded-full object-cover shrink-0 mt-0.5" 
                  />
                  <div className="flex-1 bg-[#101322] border border-[#262E4A] rounded-xl p-2.5">
                    <div className="flex items-center justify-between">
                      <span className="font-bold text-white">{c.author.displayName}</span>
                      <span className="text-[10px] text-gray-400">{c.timestamp}</span>
                    </div>
                    <p className="text-gray-200 mt-1 leading-relaxed">{c.content}</p>
                  </div>
                </div>
              ))
            )}
          </div>

          {/* Add Comment Input */}
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
              className="p-2 bg-gradient-to-r from-[#00C6FF] to-[#8B5CF6] disabled:opacity-40 text-white rounded-xl shadow transition-opacity"
            >
              <Send className="w-3.5 h-3.5" />
            </button>
          </form>

        </div>
      )}

    </article>
  );
};
