import React, { useRef, useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Film,
  Plus,
  MessageCircle,
  Share2,
  Volume2,
  VolumeX,
  Play,
  X,
  Send,
  Sparkles,
  Music2,
  MapPin
} from 'lucide-react';
import {
  VybeAvatar,
  VerifiedBadge,
  VybeResonanceIcon,
  RelativeTimeText
} from '../components/VybeCoreComponents';
import {
  fetchFeedPosts,
  publishPostPersisted,
  togglePostResonance,
  addPostComment,
  toggleFollowUser
} from '../lib/vybeApi';
import type { VybePost, VybeUser } from '../types/vybe';

export const ReelsPage: React.FC<{
  currentUser: VybeUser;
  onRefreshAll?: () => Promise<void>;
}> = ({ currentUser }) => {
  const navigate = useNavigate();
  const isGuest = currentUser.id === 'guest_viewer';
  const [reels, setReels] = useState<VybePost[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeReelIndex, setActiveReelIndex] = useState(0);
  const [isMuted, setIsMuted] = useState(true);
  const [showCreateReel, setShowCreateReel] = useState(false);
  const [activeCommentsReel, setActiveCommentsReel] = useState<VybePost | null>(null);
  const [commentText, setCommentText] = useState('');
  const [submittingComment, setSubmittingComment] = useState(false);
  const [copiedToast, setCopiedToast] = useState(false);

  // Reel Creation State
  const [reelVideoFile, setReelVideoFile] = useState<File | null>(null);
  const [reelPreviewUrl, setReelPreviewUrl] = useState<string | null>(null);
  const [reelCaption, setReelCaption] = useState('');
  const [reelMusic, setReelMusic] = useState('');
  const [reelLocation, setReelLocation] = useState('');
  const [isUploading, setIsUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [uploadError, setUploadError] = useState<string | null>(null);

  const containerRef = useRef<HTMLDivElement | null>(null);
  const videoRefs = useRef<(HTMLVideoElement | null)[]>([]);

  const loadReels = async () => {
    setLoading(true);
    try {
      const res = await fetchFeedPosts(currentUser.id, 0, 40);
      const reelOnly = res.filter((p: VybePost) => p.isVybesReel || p.mediaType === 'VIDEO');
      setReels(reelOnly);
    } catch {
      // Fallback
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadReels();
  }, [currentUser.id]);

  // Autoplay current visible video, pause others
  useEffect(() => {
    videoRefs.current.forEach((video, idx) => {
      if (!video) return;
      if (idx === activeReelIndex) {
        video.muted = isMuted;
        video.currentTime = 0;
        video.play().catch(() => {
          // Autoplay policy fallback
        });
      } else {
        video.pause();
      }
    });
  }, [activeReelIndex, isMuted, reels]);

  const handleScroll = () => {
    const container = containerRef.current;
    if (!container) return;
    const itemHeight = container.clientHeight;
    if (itemHeight <= 0) return;
    const newIdx = Math.round(container.scrollTop / itemHeight);
    if (newIdx !== activeReelIndex && newIdx >= 0 && newIdx < reels.length) {
      setActiveReelIndex(newIdx);
    }
  };

  const handleToggleLike = async (reel: VybePost) => {
    if (isGuest) {
      navigate('/login');
      return;
    }
    const nextLiked = !reel.isLiked;
    const nextCount = nextLiked ? reel.likesCount + 1 : Math.max(0, reel.likesCount - 1);
    setReels((prev) =>
      prev.map((r) => (r.id === reel.id ? { ...r, isLiked: nextLiked, likesCount: nextCount } : r))
    );
    try {
      await togglePostResonance(reel.id, currentUser.id, reel.isLiked);
    } catch {
      // Revert on error
      setReels((prev) =>
        prev.map((r) => (r.id === reel.id ? { ...r, isLiked: reel.isLiked, likesCount: reel.likesCount } : r))
      );
    }
  };

  const handleAddComment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isGuest) {
      navigate('/login');
      return;
    }
    if (!activeCommentsReel || !commentText.trim()) return;
    const clean = commentText.trim();
    setSubmittingComment(true);
    try {
      await addPostComment(activeCommentsReel.id, currentUser.id, clean);
      const newComment = {
        id: `c_${Date.now()}`,
        postId: activeCommentsReel.id,
        author: currentUser,
        content: clean,
        createdAtIso: new Date().toISOString()
      };
      setReels((prev) =>
        prev.map((r) =>
          r.id === activeCommentsReel.id
            ? {
                ...r,
                commentsCount: r.commentsCount + 1,
                comments: [...r.comments, newComment]
              }
            : r
        )
      );
      setActiveCommentsReel((prev) =>
        prev
          ? {
              ...prev,
              commentsCount: prev.commentsCount + 1,
              comments: [...prev.comments, newComment]
            }
          : null
      );
      setCommentText('');
    } finally {
      setSubmittingComment(false);
    }
  };

  const handleShare = async (reel: VybePost) => {
    const url = `${window.location.origin}/reels?id=${reel.id}`;
    if (navigator.share) {
      try {
        await navigator.share({
          title: `VYBE Reel by @${reel.author.username}`,
          text: reel.caption || 'Watch this reel on VYBE',
          url
        });
        return;
      } catch {
        // Fallback to clipboard
      }
    }
    await navigator.clipboard.writeText(url);
    setCopiedToast(true);
    setTimeout(() => setCopiedToast(false), 2400);
  };

  const handleVideoSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setReelVideoFile(file);
    setReelPreviewUrl(URL.createObjectURL(file));
  };

  const handlePublishReel = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!reelVideoFile) return;
    setIsUploading(true);
    setUploadProgress(15);
    setUploadError(null);

    try {
      await publishPostPersisted({
        authorId: currentUser.id,
        caption: reelCaption.trim(),
        mediaType: 'REEL',
        files: [reelVideoFile],
        moodZone: 'GENERAL',
        isVybesReel: true,
        isUnfiltered: false,
        locationName: reelLocation.trim() || undefined,
        musicTitle: reelMusic.trim() || undefined,
        onProgress: () => setUploadProgress(85)
      });
      setShowCreateReel(false);
      setReelVideoFile(null);
      setReelPreviewUrl(null);
      setReelCaption('');
      setReelMusic('');
      setReelLocation('');
      await loadReels();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Reel upload failed. Please try again.';
      setUploadError(msg);
    } finally {
      setIsUploading(false);
    }
  };

  return (
    <div style={{ position: 'relative', width: '100%', maxWidth: '580px', margin: '0 auto', height: '100dvh', overflow: 'hidden' }}>
      {/* Top Floating Controls */}
      <div
        style={{
          position: 'absolute',
          top: '16px',
          left: '16px',
          right: '16px',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          zIndex: 30
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <span className="vybe-brand-font vybe-gradient-text" style={{ fontSize: '1.4rem', fontWeight: 800 }}>
            VYBES
          </span>
          <span style={{ fontSize: '0.75rem', background: 'rgba(0, 229, 255, 0.15)', color: 'var(--vybe-cyan)', padding: '2px 8px', borderRadius: 'var(--radius-pill)', fontWeight: 700 }}>
            REELS
          </span>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <button
            onClick={() => setIsMuted(!isMuted)}
            className="vybe-btn"
            style={{ padding: '8px', background: 'rgba(0,0,0,0.55)', backdropFilter: 'blur(8px)', border: '1px solid var(--border-subtle)', borderRadius: '50%' }}
            title={isMuted ? 'Unmute' : 'Mute'}
          >
            {isMuted ? <VolumeX size={18} color="#fff" /> : <Volume2 size={18} color="#fff" />}
          </button>
          <button
            onClick={() => setShowCreateReel(true)}
            className="vybe-btn vybe-btn-primary"
            style={{ padding: '8px 14px', fontSize: '0.85rem' }}
          >
            <Plus size={16} />
            <span>Create</span>
          </button>
        </div>
      </div>

      {/* Vertical Reels Container */}
      {loading ? (
        <div style={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--text-secondary)' }}>
          <p>Loading VYBES Reels...</p>
        </div>
      ) : reels.length === 0 ? (
        <div style={{ height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '24px', textAlign: 'center', color: 'var(--text-secondary)' }}>
          <Film size={48} color="var(--vybe-violet)" style={{ marginBottom: '16px' }} />
          <h3 style={{ color: 'var(--text-primary)', marginBottom: '8px' }}>No Reels Yet</h3>
          <p style={{ fontSize: '0.9rem', maxWidth: '300px', marginBottom: '20px' }}>
            Be the first to share an expressive full-screen short video on VYBE.
          </p>
          <button onClick={() => setShowCreateReel(true)} className="vybe-btn vybe-btn-primary">
            <Plus size={16} />
            <span>Post First Reel</span>
          </button>
        </div>
      ) : (
        <div
          ref={containerRef}
          onScroll={handleScroll}
          style={{
            height: '100%',
            overflowY: 'scroll',
            scrollSnapType: 'y mandatory',
            scrollBehavior: 'smooth'
          }}
        >
          {reels.map((reel, idx) => {
            const videoUrl = reel.mediaUrls[0] || '';
            const isPlaying = idx === activeReelIndex;

            return (
              <div
                key={reel.id}
                style={{
                  height: '100dvh',
                  width: '100%',
                  scrollSnapAlign: 'start',
                  position: 'relative',
                  background: '#000',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center'
                }}
              >
                {/* Video element */}
                <video
                  ref={(el) => {
                    videoRefs.current[idx] = el;
                  }}
                  src={videoUrl}
                  loop
                  playsInline
                  muted={isMuted}
                  style={{
                    width: '100%',
                    height: '100%',
                    objectFit: 'cover'
                  }}
                  onClick={() => setIsMuted(!isMuted)}
                />

                {/* Subtle dark gradient overlay at bottom for readable text */}
                <div
                  style={{
                    position: 'absolute',
                    bottom: 0,
                    left: 0,
                    right: 0,
                    height: '240px',
                    background: 'linear-gradient(to top, rgba(0,0,0,0.88) 0%, rgba(0,0,0,0.4) 60%, transparent 100%)',
                    pointerEvents: 'none'
                  }}
                />

                {/* Bottom Left Info: Author, Caption, Music */}
                <div
                  style={{
                    position: 'absolute',
                    bottom: '80px',
                    left: '16px',
                    right: '84px',
                    zIndex: 20,
                    color: '#fff',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '8px'
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    <VybeAvatar user={reel.author} size={38} />
                    <span style={{ fontWeight: 700, fontSize: '0.95rem' }}>{reel.author.displayName}</span>
                    <VerifiedBadge user={reel.author} />
                    <span style={{ color: 'rgba(255,255,255,0.7)', fontSize: '0.8rem' }}>@{reel.author.username}</span>
                  </div>

                  {reel.caption && (
                    <p style={{ fontSize: '0.9rem', lineHeight: '1.4', margin: 0, textShadow: '0 1px 4px rgba(0,0,0,0.8)' }}>
                      {reel.caption}
                    </p>
                  )}

                  {reel.musicTitle && (
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '0.8rem', color: 'var(--vybe-cyan)' }}>
                      <Music2 size={14} />
                      <span style={{ fontWeight: 600 }}>{reel.musicTitle}</span>
                    </div>
                  )}

                  {reel.locationName && (
                    <div style={{ display: 'flex', alignItems: 'center', gap: '5px', fontSize: '0.75rem', color: 'rgba(255,255,255,0.75)' }}>
                      <MapPin size={12} />
                      <span>{reel.locationName}</span>
                    </div>
                  )}
                </div>

                {/* Right Side Action Rail */}
                <div
                  style={{
                    position: 'absolute',
                    bottom: '80px',
                    right: '12px',
                    zIndex: 25,
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    gap: '18px'
                  }}
                >
                  {/* Like / Resonance */}
                  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '4px' }}>
                    <button
                      onClick={() => handleToggleLike(reel)}
                      style={{
                        background: reel.isLiked ? 'rgba(255, 46, 147, 0.25)' : 'rgba(0,0,0,0.5)',
                        border: 'none',
                        borderRadius: '50%',
                        padding: '10px',
                        cursor: 'pointer',
                        color: reel.isLiked ? 'var(--vybe-magenta)' : '#fff',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        backdropFilter: 'blur(8px)',
                        transition: 'transform 0.15s ease'
                      }}
                    >
                      <VybeResonanceIcon active={reel.isLiked} size={24} />
                    </button>
                    <span style={{ fontSize: '0.75rem', color: '#fff', fontWeight: 600 }}>{reel.likesCount}</span>
                  </div>

                  {/* Comments */}
                  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '4px' }}>
                    <button
                      onClick={() => setActiveCommentsReel(reel)}
                      style={{
                        background: 'rgba(0,0,0,0.5)',
                        border: 'none',
                        borderRadius: '50%',
                        padding: '10px',
                        cursor: 'pointer',
                        color: '#fff',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        backdropFilter: 'blur(8px)'
                      }}
                    >
                      <MessageCircle size={22} />
                    </button>
                    <span style={{ fontSize: '0.75rem', color: '#fff', fontWeight: 600 }}>{reel.commentsCount}</span>
                  </div>

                  {/* Share */}
                  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '4px' }}>
                    <button
                      onClick={() => handleShare(reel)}
                      style={{
                        background: 'rgba(0,0,0,0.5)',
                        border: 'none',
                        borderRadius: '50%',
                        padding: '10px',
                        cursor: 'pointer',
                        color: '#fff',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        backdropFilter: 'blur(8px)'
                      }}
                    >
                      <Share2 size={22} />
                    </button>
                    <span style={{ fontSize: '0.75rem', color: '#fff', fontWeight: 600 }}>Share</span>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Share Toast */}
      {copiedToast && (
        <div
          style={{
            position: 'absolute',
            bottom: '40px',
            left: '50%',
            transform: 'translateX(-50%)',
            background: 'rgba(18, 19, 28, 0.95)',
            color: 'var(--vybe-cyan)',
            padding: '10px 18px',
            borderRadius: 'var(--radius-pill)',
            border: '1px solid var(--border-accent)',
            fontSize: '0.85rem',
            fontWeight: 600,
            zIndex: 60,
            backdropFilter: 'blur(12px)'
          }}
        >
          Link copied to clipboard!
        </div>
      )}

      {/* Reel Comments Sheet */}
      {activeCommentsReel && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0,0,0,0.65)',
            zIndex: 80,
            display: 'flex',
            alignItems: 'flex-end',
            justifyContent: 'center'
          }}
          onClick={() => setActiveCommentsReel(null)}
        >
          <div
            style={{
              width: '100%',
              maxWidth: '560px',
              height: '70vh',
              background: 'var(--bg-surface)',
              borderTopLeftRadius: 'var(--radius-lg)',
              borderTopRightRadius: 'var(--radius-lg)',
              border: '1px solid var(--border-subtle)',
              borderBottom: 'none',
              display: 'flex',
              flexDirection: 'column',
              overflow: 'hidden'
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '16px 20px', borderBottom: '1px solid var(--border-subtle)' }}>
              <h4 style={{ margin: 0, fontSize: '1rem', fontWeight: 700 }}>Comments ({activeCommentsReel.commentsCount})</h4>
              <button onClick={() => setActiveCommentsReel(null)} style={{ background: 'none', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer' }}>
                <X size={20} />
              </button>
            </div>

            <div style={{ flex: 1, overflowY: 'auto', padding: '16px 20px', display: 'flex', flexDirection: 'column', gap: '14px' }}>
              {activeCommentsReel.comments.length === 0 ? (
                <p style={{ textAlign: 'center', color: 'var(--text-muted)', fontSize: '0.9rem', marginTop: '40px' }}>
                  No comments yet. Be the first to resonate!
                </p>
              ) : (
                activeCommentsReel.comments.map((c) => (
                  <div key={c.id} style={{ display: 'flex', gap: '12px' }}>
                    <VybeAvatar user={c.author} size={32} />
                    <div style={{ flex: 1 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '2px' }}>
                        <span style={{ fontSize: '0.85rem', fontWeight: 700 }}>{c.author.displayName}</span>
                        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>@{c.author.username}</span>
                        <RelativeTimeText iso={c.createdAtIso} />
                      </div>
                      <p style={{ fontSize: '0.85rem', lineHeight: '1.4', margin: 0, color: 'var(--text-primary)' }}>{c.content}</p>
                    </div>
                  </div>
                ))
              )}
            </div>

            <form onSubmit={handleAddComment} style={{ display: 'flex', gap: '8px', padding: '12px 16px', borderTop: '1px solid var(--border-subtle)', background: 'var(--bg-elevated)' }}>
              <input
                type="text"
                placeholder="Add a comment..."
                value={commentText}
                onChange={(e) => setCommentText(e.target.value)}
                style={{
                  flex: 1,
                  background: 'var(--bg-surface)',
                  border: '1px solid var(--border-subtle)',
                  borderRadius: 'var(--radius-pill)',
                  padding: '8px 16px',
                  color: 'var(--text-primary)',
                  fontSize: '0.9rem',
                  outline: 'none'
                }}
              />
              <button
                type="submit"
                disabled={submittingComment || !commentText.trim()}
                className="vybe-btn vybe-btn-primary"
                style={{ padding: '8px 16px', borderRadius: 'var(--radius-pill)' }}
              >
                <Send size={16} />
              </button>
            </form>
          </div>
        </div>
      )}

      {/* Create Reel Modal */}
      {showCreateReel && (
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
          onClick={() => !isUploading && setShowCreateReel(false)}
        >
          <div
            style={{
              width: '100%',
              maxWidth: '480px',
              background: 'var(--bg-surface)',
              borderRadius: 'var(--radius-lg)',
              border: '1px solid var(--border-subtle)',
              padding: '24px',
              display: 'flex',
              flexDirection: 'column',
              gap: '16px',
              maxHeight: '90vh',
              overflowY: 'auto'
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h3 style={{ margin: 0, fontSize: '1.2rem', fontWeight: 800 }}>Create New Reel</h3>
              <button
                onClick={() => !isUploading && setShowCreateReel(false)}
                style={{ background: 'none', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer' }}
              >
                <X size={20} />
              </button>
            </div>

            {uploadError && (
              <div style={{ padding: '10px 14px', background: 'rgba(255, 77, 109, 0.15)', border: '1px solid var(--vybe-danger)', borderRadius: 'var(--radius-sm)', color: 'var(--vybe-danger)', fontSize: '0.85rem' }}>
                {uploadError}
              </div>
            )}

            <form onSubmit={handlePublishReel} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
              {/* Video upload preview */}
              <div
                style={{
                  height: '240px',
                  borderRadius: 'var(--radius-md)',
                  border: '2px dashed var(--border-subtle)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  position: 'relative',
                  overflow: 'hidden',
                  background: 'var(--bg-elevated)'
                }}
              >
                {reelPreviewUrl ? (
                  <video src={reelPreviewUrl} style={{ width: '100%', height: '100%', objectFit: 'contain' }} controls />
                ) : (
                  <label style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '8px', cursor: 'pointer', color: 'var(--text-secondary)' }}>
                    <Film size={36} color="var(--vybe-cyan)" />
                    <span style={{ fontSize: '0.9rem', fontWeight: 600 }}>Select vertical video file (MP4, WebM)</span>
                    <input type="file" accept="video/mp4,video/webm,video/quicktime" onChange={handleVideoSelect} style={{ display: 'none' }} />
                  </label>
                )}
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '6px' }}>
                  Caption
                </label>
                <textarea
                  rows={3}
                  placeholder="What's this reel about? #vibes #creative"
                  value={reelCaption}
                  onChange={(e) => setReelCaption(e.target.value)}
                  style={{
                    width: '100%',
                    background: 'var(--bg-elevated)',
                    border: '1px solid var(--border-subtle)',
                    borderRadius: 'var(--radius-sm)',
                    padding: '10px 14px',
                    color: 'var(--text-primary)',
                    fontSize: '0.9rem',
                    resize: 'none',
                    outline: 'none'
                  }}
                />
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '4px' }}>
                    Music / Audio Track
                  </label>
                  <input
                    type="text"
                    placeholder="Song • Artist"
                    value={reelMusic}
                    onChange={(e) => setReelMusic(e.target.value)}
                    style={{
                      width: '100%',
                      background: 'var(--bg-elevated)',
                      border: '1px solid var(--border-subtle)',
                      borderRadius: 'var(--radius-sm)',
                      padding: '8px 12px',
                      color: 'var(--text-primary)',
                      fontSize: '0.85rem',
                      outline: 'none'
                    }}
                  />
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '4px' }}>
                    Location
                  </label>
                  <input
                    type="text"
                    placeholder="City, Place"
                    value={reelLocation}
                    onChange={(e) => setReelLocation(e.target.value)}
                    style={{
                      width: '100%',
                      background: 'var(--bg-elevated)',
                      border: '1px solid var(--border-subtle)',
                      borderRadius: 'var(--radius-sm)',
                      padding: '8px 12px',
                      color: 'var(--text-primary)',
                      fontSize: '0.85rem',
                      outline: 'none'
                    }}
                  />
                </div>
              </div>

              {isUploading && (
                <div style={{ marginTop: '8px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8rem', color: 'var(--text-secondary)', marginBottom: '4px' }}>
                    <span>Uploading Reel...</span>
                    <span>{uploadProgress}%</span>
                  </div>
                  <div style={{ width: '100%', height: '6px', background: 'var(--bg-elevated)', borderRadius: '3px', overflow: 'hidden' }}>
                    <div style={{ width: `${uploadProgress}%`, height: '100%', background: 'var(--vybe-gradient)', transition: 'width 0.2s ease' }} />
                  </div>
                </div>
              )}

              <button
                type="submit"
                disabled={isUploading || !reelVideoFile}
                className="vybe-btn vybe-btn-primary"
                style={{ width: '100%', padding: '12px', marginTop: '8px' }}
              >
                {isUploading ? 'Publishing Reel...' : 'Publish Reel'}
              </button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
