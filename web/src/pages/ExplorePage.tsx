import React, { useState, useEffect } from 'react';
import { Search, Compass, Sparkles, TrendingUp, Filter, Play, Heart, MessageCircle, X } from 'lucide-react';
import { VybeAvatar, VerifiedBadge, VybeResonanceIcon } from '../components/VybeCoreComponents';
import { MOOD_OPTIONS, matchesMoodFilter } from '../lib/utils';
import { searchUsersAndPosts, fetchFeedPosts, toggleFollowUser } from '../lib/vybeApi';
import type { MoodZone, VybePost, VybeUser } from '../types/vybe';

export const ExplorePage: React.FC<{
  currentUser: VybeUser;
  onOpenUserProfile?: (user: VybeUser) => void;
}> = ({ currentUser, onOpenUserProfile }) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedMood, setSelectedMood] = useState<MoodZone | null>(null);
  const [allPosts, setAllPosts] = useState<VybePost[]>([]);
  const [searchResults, setSearchResults] = useState<{ users: VybeUser[]; posts: VybePost[] } | null>(null);
  const [loading, setLoading] = useState(true);
  const [searching, setSearching] = useState(false);
  const [selectedPost, setSelectedPost] = useState<VybePost | null>(null);

  useEffect(() => {
    const loadExplore = async () => {
      setLoading(true);
      try {
        const res = await fetchFeedPosts(currentUser.id, 0, 60);
        setAllPosts(res);
      } catch {
        // Fallback
      } finally {
        setLoading(false);
      }
    };
    void loadExplore();
  }, [currentUser.id]);

  // Live search debounce
  useEffect(() => {
    const clean = searchQuery.trim();
    if (clean.length < 2) {
      setSearchResults(null);
      setSearching(false);
      return;
    }
    setSearching(true);
    const timer = setTimeout(async () => {
      try {
        const results = await searchUsersAndPosts(clean);
        setSearchResults(results);
      } catch {
        // Fallback
      } finally {
        setSearching(false);
      }
    }, 320);
    return () => clearTimeout(timer);
  }, [searchQuery]);

  const displayedPosts = searchResults
    ? searchResults.posts
    : selectedMood
      ? allPosts.filter((p) => matchesMoodFilter(p, selectedMood))
      : allPosts;

  return (
    <div style={{ maxWidth: '960px', margin: '0 auto', padding: '24px 16px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
      {/* Header & Search */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <Compass size={28} color="var(--vybe-cyan)" />
          <h2 className="vybe-brand-font" style={{ fontSize: '1.6rem', fontWeight: 800, margin: 0 }}>
            Explore & Discover
          </h2>
        </div>

        {/* Search Bar */}
        <div style={{ position: 'relative', width: '100%' }}>
          <Search size={18} color="var(--text-muted)" style={{ position: 'absolute', left: '16px', top: '50%', transform: 'translateY(-50%)' }} />
          <input
            type="text"
            placeholder="Search creators, keywords, moods, or topics..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            style={{
              width: '100%',
              background: 'var(--bg-surface)',
              border: '1px solid var(--border-subtle)',
              borderRadius: 'var(--radius-pill)',
              padding: '12px 18px 12px 44px',
              color: 'var(--text-primary)',
              fontSize: '0.95rem',
              outline: 'none',
              transition: 'border-color 0.2s ease'
            }}
          />
          {searchQuery && (
            <button
              onClick={() => setSearchQuery('')}
              style={{ position: 'absolute', right: '14px', top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer' }}
            >
              <X size={16} />
            </button>
          )}
        </div>

        {/* Mood Zone Chips */}
        <div style={{ display: 'flex', gap: '8px', overflowX: 'auto', paddingBottom: '4px', scrollbarWidth: 'none' }}>
          <button
            onClick={() => setSelectedMood(null)}
            className="vybe-btn"
            style={{
              padding: '6px 14px',
              fontSize: '0.82rem',
              background: selectedMood === null ? 'var(--vybe-gradient)' : 'var(--bg-surface)',
              color: selectedMood === null ? '#fff' : 'var(--text-secondary)',
              border: '1px solid var(--border-subtle)'
            }}
          >
            All Moods
          </button>
          {MOOD_OPTIONS.map((mood) => {
            const isSelected = selectedMood === mood.key;
            return (
              <button
                key={mood.key}
                onClick={() => setSelectedMood(isSelected ? null : mood.key)}
                className="vybe-btn"
                style={{
                  padding: '6px 14px',
                  fontSize: '0.82rem',
                  background: isSelected ? 'var(--bg-elevated)' : 'var(--bg-surface)',
                  color: isSelected ? mood.color : 'var(--text-secondary)',
                  border: isSelected ? `1px solid ${mood.color}` : '1px solid var(--border-subtle)',
                  whiteSpace: 'nowrap'
                }}
              >
                <span>{mood.emoji}</span>
                <span>{mood.label}</span>
              </button>
            );
          })}
        </div>
      </div>

      {/* Search Results - Creators preview */}
      {searchResults && searchResults.users.length > 0 && (
        <div className="vybe-card" style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          <h4 style={{ margin: 0, fontSize: '0.95rem', color: 'var(--text-secondary)' }}>Matching People</h4>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: '12px' }}>
            {searchResults.users.map((u) => (
              <div
                key={u.id}
                onClick={() => onOpenUserProfile?.(u)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '12px',
                  padding: '10px 14px',
                  background: 'var(--bg-elevated)',
                  borderRadius: 'var(--radius-sm)',
                  cursor: 'pointer'
                }}
              >
                <VybeAvatar user={u} size={40} />
                <div style={{ minWidth: 0, flex: 1 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                    <span style={{ fontSize: '0.9rem', fontWeight: 700, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {u.displayName}
                    </span>
                    <VerifiedBadge user={u} />
                  </div>
                  <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>@{u.username}</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Discovery Posts Grid */}
      {loading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: '60px 0', color: 'var(--text-secondary)' }}>
          <p>Discovering VYBE moments...</p>
        </div>
      ) : displayedPosts.length === 0 ? (
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '60px 20px', color: 'var(--text-secondary)', textAlign: 'center' }}>
          <Sparkles size={40} color="var(--vybe-violet)" style={{ marginBottom: '12px' }} />
          <h3 style={{ color: 'var(--text-primary)', marginBottom: '6px' }}>No Content Found</h3>
          <p style={{ fontSize: '0.9rem', maxWidth: '320px' }}>
            {searchQuery ? `No posts matched "${searchQuery}". Try different keywords.` : 'No posts in this mood zone yet.'}
          </p>
        </div>
      ) : (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))',
            gap: '14px'
          }}
        >
          {displayedPosts.map((post) => {
            const mediaUrl = post.mediaUrls[0];
            const isVideo = post.mediaType === 'VIDEO' || post.isVybesReel;

            return (
              <div
                key={post.id}
                onClick={() => setSelectedPost(post)}
                style={{
                  position: 'relative',
                  aspectRatio: isVideo ? '9/16' : '1/1',
                  borderRadius: 'var(--radius-md)',
                  overflow: 'hidden',
                  background: 'var(--bg-surface)',
                  border: '1px solid var(--border-subtle)',
                  cursor: 'pointer'
                }}
              >
                {mediaUrl ? (
                  isVideo ? (
                    <video src={mediaUrl} style={{ width: '100%', height: '100%', objectFit: 'cover' }} muted playsInline />
                  ) : (
                    <img src={mediaUrl} alt={post.caption || 'Post image'} style={{ width: '100%', height: '100%', objectFit: 'cover' }} loading="lazy" />
                  )
                ) : (
                  <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '20px', background: 'var(--bg-elevated)', textAlign: 'center' }}>
                    <p style={{ fontSize: '0.9rem', color: 'var(--text-primary)' }}>{post.caption}</p>
                  </div>
                )}

                {/* Video Badge */}
                {isVideo && (
                  <div style={{ position: 'absolute', top: '10px', right: '10px', background: 'rgba(0,0,0,0.6)', padding: '4px 8px', borderRadius: 'var(--radius-pill)', display: 'flex', alignItems: 'center', gap: '4px', fontSize: '0.75rem', color: '#fff' }}>
                    <Play size={12} fill="#fff" />
                    <span>Reel</span>
                  </div>
                )}

                {/* Bottom Overlay with Author and Likes */}
                <div
                  style={{
                    position: 'absolute',
                    bottom: 0,
                    left: 0,
                    right: 0,
                    padding: '12px 14px',
                    background: 'linear-gradient(to top, rgba(0,0,0,0.85) 0%, transparent 100%)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    color: '#fff'
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <VybeAvatar user={post.author} size={26} />
                    <span style={{ fontSize: '0.8rem', fontWeight: 600 }}>@{post.author.username}</span>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '0.8rem' }}>
                    <VybeResonanceIcon active={post.isLiked} size={16} />
                    <span>{post.likesCount}</span>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Selected Post Modal */}
      {selectedPost && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0,0,0,0.75)',
            zIndex: 90,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '16px'
          }}
          onClick={() => setSelectedPost(null)}
        >
          <div
            style={{
              width: '100%',
              maxWidth: '680px',
              background: 'var(--bg-surface)',
              borderRadius: 'var(--radius-lg)',
              border: '1px solid var(--border-subtle)',
              overflow: 'hidden',
              display: 'flex',
              flexDirection: 'column',
              maxHeight: '90vh'
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '16px 20px', borderBottom: '1px solid var(--border-subtle)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <VybeAvatar user={selectedPost.author} size={36} />
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                    <span style={{ fontWeight: 700, fontSize: '0.95rem' }}>{selectedPost.author.displayName}</span>
                    <VerifiedBadge user={selectedPost.author} />
                  </div>
                  <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>@{selectedPost.author.username}</span>
                </div>
              </div>
              <button onClick={() => setSelectedPost(null)} style={{ background: 'none', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer' }}>
                <X size={20} />
              </button>
            </div>

            <div style={{ flex: 1, overflowY: 'auto' }}>
              {selectedPost.mediaUrls[0] && (
                selectedPost.mediaType === 'VIDEO' || selectedPost.isVybesReel ? (
                  <video src={selectedPost.mediaUrls[0]} controls style={{ width: '100%', maxHeight: '420px', background: '#000' }} />
                ) : (
                  <img src={selectedPost.mediaUrls[0]} alt={selectedPost.caption} style={{ width: '100%', maxHeight: '420px', objectFit: 'contain', background: '#000' }} />
                )
              )}
              {selectedPost.caption && (
                <div style={{ padding: '16px 20px' }}>
                  <p style={{ fontSize: '0.95rem', lineHeight: '1.5', margin: 0 }}>{selectedPost.caption}</p>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
