import React, { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Plus,
  MessageCircle,
  Bookmark,
  Share2,
  Flag,
  Camera,
  Image as ImageIcon,
  Film,
  MapPin,
  X,
  RefreshCw
} from 'lucide-react';
import {
  VybeAvatar,
  VerifiedBadge,
  VybeResonanceIcon,
  RelativeTimeText,
  ViewportAutoplayVideo
} from '../components/VybeCoreComponents';
import { MOOD_OPTIONS, matchesMoodFilter } from '../lib/utils';
import {
  addPostComment,
  markStoryViewed,
  publishPostPersisted,
  publishStoryPersisted,
  submitReportOrCopyrightClaim,
  togglePostResonance,
  toggleSavePost
} from '../lib/vybeApi';
import type { MoodZone, VybePost, VybeStory, VybeUser } from '../types/vybe';

export const FeedAndStoriesPage: React.FC<{
  currentUser: VybeUser;
  posts: VybePost[];
  stories: VybeStory[];
  loadingFeed: boolean;
  onRefreshAll: () => Promise<void>;
  onLoadMore: () => Promise<void>;
  hasMorePosts: boolean;
  onDraftDirtyChange: (dirty: boolean) => void;
  externalOpenComposer?: boolean;
}> = ({
  currentUser,
  posts,
  stories,
  loadingFeed,
  onRefreshAll,
  onLoadMore,
  hasMorePosts,
  onDraftDirtyChange,
  externalOpenComposer
}) => {
  const navigate = useNavigate();
  const isGuest = currentUser.id === 'guest_viewer';
  const [selectedMood, setSelectedMood] = useState<MoodZone | null>(null);
  const [showPostComposer, setShowPostComposer] = useState(false);

  const requireAuth = () => {
    if (isGuest) {
      navigate('/login');
      return false;
    }
    return true;
  };

  React.useEffect(() => {
    if (externalOpenComposer) {
      if (isGuest) {
        navigate('/login');
      } else {
        setShowPostComposer(true);
      }
    }
  }, [externalOpenComposer, isGuest, navigate]);
  const [showStoryComposer, setShowStoryComposer] = useState(false);
  const [activeStory, setActiveStory] = useState<VybeStory | null>(null);
  const [openCommentsPostId, setOpenCommentsPostId] = useState<string | null>(null);
  const [commentInput, setCommentInput] = useState('');
  const [reportingPost, setReportingPost] = useState<VybePost | null>(null);
  const [reportReason, setReportReason] = useState('');
  const [toastMsg, setToastMsg] = useState<string | null>(null);

  // Post Composer state
  const [caption, setCaption] = useState('');
  const [postMood, setPostMood] = useState<MoodZone>('GENERAL');
  const [isReel, setIsReel] = useState(false);
  const [isUnfiltered, setIsUnfiltered] = useState(false);
  const [locationName, setLocationName] = useState('');
  const [selectedFiles, setSelectedFiles] = useState<File[]>([]);
  const [publishing, setPublishing] = useState(false);
  const [publishProgress, setPublishProgress] = useState<string | null>(null);
  const [publishError, setPublishError] = useState<string | null>(null);

  // Browser camera capture for Unfiltered / Story
  const [cameraActive, setCameraActive] = useState(false);
  const cameraVideoRef = useRef<HTMLVideoElement | null>(null);
  const cameraStreamRef = useRef<MediaStream | null>(null);

  // Story Composer state
  const [storyCaption, setStoryCaption] = useState('');
  const [storyAudience, setStoryAudience] = useState<'EVERYONE' | 'VYBE_MATCHES'>('EVERYONE');
  const [storyFile, setStoryFile] = useState<File | null>(null);
  const [storyPublishing, setStoryPublishing] = useState(false);
  const [storyError, setStoryError] = useState<string | null>(null);

  const visiblePosts = posts.filter((p) => matchesMoodFilter(p, selectedMood));

  const sortedStories = [...stories].sort((a, b) => Number(a.isViewed) - Number(b.isViewed));

  const startBrowserCamera = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'user', width: { ideal: 1080 } },
        audio: false
      });
      cameraStreamRef.current = stream;
      setCameraActive(true);
      window.setTimeout(() => {
        if (cameraVideoRef.current) cameraVideoRef.current.srcObject = stream;
      }, 60);
    } catch {
      setPublishError('Camera access denied or unavailable in this browser.');
    }
  };

  const stopBrowserCamera = () => {
    cameraStreamRef.current?.getTracks().forEach((t) => t.stop());
    cameraStreamRef.current = null;
    setCameraActive(false);
  };

  const captureCameraSnapshot = () => {
    const video = cameraVideoRef.current;
    if (!video) return;
    const canvas = document.createElement('canvas');
    canvas.width = video.videoWidth || 720;
    canvas.height = video.videoHeight || 960;
    const ctx = canvas.getContext('2d');
    ctx?.drawImage(video, 0, 0, canvas.width, canvas.height);
    canvas.toBlob(
      (blob) => {
        if (blob) {
          const captured = new File([blob], `vybe_raw_${Date.now()}.jpg`, { type: 'image/jpeg' });
          setSelectedFiles([captured]);
          setIsUnfiltered(true);
          stopBrowserCamera();
        }
      },
      'image/jpeg',
      0.9
    );
  };

  const handlePublishPost = async (e: React.FormEvent) => {
    e.preventDefault();
    if (publishing) return;
    if (!caption.trim() && selectedFiles.length === 0) {
      setPublishError('Write a caption or attach media before publishing.');
      return;
    }

    setPublishing(true);
    setPublishError(null);
    try {
      await publishPostPersisted({
        authorId: currentUser.id,
        caption,
        files: selectedFiles,
        moodZone: postMood,
        isUnfiltered,
        isVybesReel: isReel,
        locationName,
        onProgress: setPublishProgress
      });
      // Only clear and refresh AFTER backend persistence succeeds
      setCaption('');
      setSelectedFiles([]);
      setLocationName('');
      setIsReel(false);
      setIsUnfiltered(false);
      setShowPostComposer(false);
      onDraftDirtyChange(false);
      await onRefreshAll();
      setToastMsg('Published to VYBE Feed.');
      window.setTimeout(() => setToastMsg(null), 3200);
    } catch (err: unknown) {
      setPublishError(err instanceof Error ? err.message : 'Failed to publish post.');
    } finally {
      setPublishing(false);
      setPublishProgress(null);
    }
  };

  const handlePublishStory = async (e: React.FormEvent) => {
    e.preventDefault();
    if (storyPublishing) return;
    setStoryPublishing(true);
    setStoryError(null);
    try {
      await publishStoryPersisted({
        authorId: currentUser.id,
        file: storyFile,
        caption: storyCaption,
        audience: storyAudience
      });
      setStoryCaption('');
      setStoryFile(null);
      setShowStoryComposer(false);
      onDraftDirtyChange(false);
      await onRefreshAll();
      setToastMsg('Story published for 24 hours.');
      window.setTimeout(() => setToastMsg(null), 3200);
    } catch (err: unknown) {
      setStoryError(err instanceof Error ? err.message : 'Story upload failed.');
    } finally {
      setStoryPublishing(false);
    }
  };

  const openStory = async (story: VybeStory) => {
    setActiveStory(story);
    if (!story.isViewed) {
      await markStoryViewed(story.id, currentUser.id);
      void onRefreshAll();
    }
  };

  return (
    <div style={{ maxWidth: 660, margin: '0 auto', padding: '16px 14px' }}>
      {toastMsg && (
        <div
          style={{
            marginBottom: 14,
            padding: '10px 14px',
            borderRadius: 12,
            background: 'rgba(0, 230, 118, 0.14)',
            border: '1px solid rgba(0, 230, 118, 0.45)',
            color: '#00E676',
            fontSize: '0.86rem'
          }}
        >
          {toastMsg}
        </div>
      )}

      {/* Separated Composer Trigger Bar (Never overlaps feed content or media controls) */}
      <div
        className="vybe-card"
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 10,
          marginBottom: 16
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, flex: 1 }}>
          <VybeAvatar user={currentUser} size={40} />
          <button
            type="button"
            onClick={() => {
              if (!requireAuth()) return;
              setShowPostComposer(true);
              onDraftDirtyChange(true);
            }}
            style={{
              flex: 1,
              textAlign: 'left',
              background: 'var(--bg-elevated)',
              border: '1px solid var(--border-subtle)',
              borderRadius: 999,
              padding: '10px 16px',
              color: 'var(--text-secondary)',
              cursor: 'pointer',
              fontSize: '0.9rem'
            }}
          >
            {isGuest ? 'Sign in to share your current vybe…' : `Share your current vybe, @${currentUser.username}…`}
          </button>
        </div>
        <button
          type="button"
          className="vybe-btn vybe-btn-primary"
          onClick={() => {
            if (!requireAuth()) return;
            setShowPostComposer(true);
            onDraftDirtyChange(true);
          }}
        >
          <Plus size={16} /> Post
        </button>
        <button
          type="button"
          className="vybe-btn vybe-btn-outline"
          onClick={() => {
            if (!requireAuth()) return;
            setShowStoryComposer(true);
            onDraftDirtyChange(true);
          }}
        >
          + Story
        </button>
      </div>

      {/* 24-Hour Stories Tray */}
      <div className="vybe-card" style={{ marginBottom: 16, padding: '14px 14px 10px' }}>
        <div className="vybe-scroll-row">
          <div
            onClick={() => {
              if (!requireAuth()) return;
              setShowStoryComposer(true);
              onDraftDirtyChange(true);
            }}
            style={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              gap: 6,
              minWidth: 68,
              cursor: 'pointer'
            }}
          >
            <div style={{ position: 'relative' }}>
              <VybeAvatar user={currentUser} size={56} />
              <span
                style={{
                  position: 'absolute',
                  right: -2,
                  bottom: -2,
                  width: 22,
                  height: 22,
                  borderRadius: '50%',
                  background: 'var(--vybe-violet)',
                  color: '#fff',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: 14,
                  fontWeight: 800
                }}
              >
                +
              </span>
            </div>
            <span style={{ fontSize: '0.74rem', color: 'var(--text-secondary)' }}>Your Story</span>
          </div>

          {sortedStories.map((story) => (
            <div
              key={story.id}
              onClick={() => void openStory(story)}
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: 6,
                minWidth: 68,
                cursor: 'pointer'
              }}
            >
              <VybeAvatar
                user={story.author}
                size={54}
                ringMode={story.isViewed ? 'viewed' : 'unviewed'}
              />
              <span
                style={{
                  fontSize: '0.74rem',
                  maxWidth: 68,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                  color: story.isViewed ? 'var(--text-muted)' : 'var(--text-primary)'
                }}
              >
                {story.author.username}
              </span>
            </div>
          ))}
        </div>
      </div>

      {/* Single Unified Mood Filter Bar */}
      <div className="vybe-scroll-row" style={{ marginBottom: 16 }}>
        <button
          type="button"
          className={`vybe-chip ${selectedMood === null ? 'vybe-chip-active' : ''}`}
          onClick={() => setSelectedMood(null)}
        >
          ✨ All Vybes
        </button>
        {MOOD_OPTIONS.map((m) => (
          <button
            key={m.key}
            type="button"
            className={`vybe-chip ${selectedMood === m.key ? 'vybe-chip-active' : ''}`}
            onClick={() => setSelectedMood(m.key)}
          >
            <span>{m.emoji}</span>
            <span>{m.label}</span>
          </button>
        ))}
        <button
          type="button"
          className="vybe-chip"
          onClick={() => void onRefreshAll()}
          title="Refresh Feed"
        >
          <RefreshCw size={14} /> Sync
        </button>
      </div>

      {/* Feed Posts */}
      {loadingFeed && visiblePosts.length === 0 ? (
        <div className="vybe-card" style={{ textAlign: 'center', padding: 36, color: 'var(--text-secondary)' }}>
          Syncing shared VYBE feed…
        </div>
      ) : visiblePosts.length === 0 ? (
        <div className="vybe-card" style={{ textAlign: 'center', padding: 36 }}>
          <p style={{ color: 'var(--text-secondary)', marginBottom: 12 }}>
            No posts in this mood filter yet. Share the first moment!
          </p>
          <button
            type="button"
            className="vybe-btn vybe-btn-primary"
            onClick={() => {
              if (!requireAuth()) return;
              setShowPostComposer(true);
            }}
          >
            Create Post
          </button>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {visiblePosts.map((post) => (
            <article key={post.id} className="vybe-card">
              {/* Post Header */}
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  marginBottom: 12
                }}
              >
                <div
                  onClick={() => navigate(`/profile?user=${post.author.username}`)}
                  style={{ display: 'flex', alignItems: 'center', gap: 10, cursor: 'pointer' }}
                >
                  <VybeAvatar user={post.author} size={40} />
                  <div>
                    <div style={{ fontWeight: 700, fontSize: '0.94rem', display: 'flex', alignItems: 'center' }}>
                      {post.author.displayName}
                      <VerifiedBadge user={post.author} />
                    </div>
                    <div style={{ fontSize: '0.78rem', color: 'var(--text-secondary)' }}>
                      @{post.author.username} • <RelativeTimeText iso={post.createdAtIso} fallback="Just now" />
                      {post.locationName ? ` • 📍 ${post.locationName}` : ''}
                    </div>
                  </div>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  {post.isUnfiltered && (
                    <span
                      className="vybe-badge"
                      style={{ background: 'rgba(0,229,255,0.14)', color: 'var(--vybe-cyan)' }}
                    >
                      RAW
                    </span>
                  )}
                  {post.moodZone && (
                    <span
                      className="vybe-badge"
                      style={{ background: 'rgba(124,77,255,0.18)', color: '#d0bcff' }}
                    >
                      {post.moodZone}
                    </span>
                  )}
                  <button
                    type="button"
                    onClick={() => {
                      if (!requireAuth()) return;
                      setReportingPost(post);
                    }}
                    aria-label="Report or copyright claim"
                    style={{
                      background: 'none',
                      border: 'none',
                      color: 'var(--text-muted)',
                      cursor: 'pointer'
                    }}
                  >
                    <Flag size={15} />
                  </button>
                </div>
              </div>

              {/* Caption */}
              {post.caption && (
                <p style={{ fontSize: '0.95rem', lineHeight: 1.5, marginBottom: 12, whiteSpace: 'pre-wrap' }}>
                  {post.caption}
                </p>
              )}

              {/* Media */}
              {post.mediaUrls.length > 0 && (
                <div style={{ marginBottom: 12, display: 'flex', flexDirection: 'column', gap: 8 }}>
                  {post.mediaUrls.map((url, idx) =>
                    post.mediaType === 'VIDEO' || post.mediaType === 'REEL' || /\.(mp4|webm|mov)(\?|$)/i.test(url) ? (
                      <ViewportAutoplayVideo key={`${post.id}_${idx}`} id={`${post.id}_${idx}`} src={url} />
                    ) : (
                      <img
                        key={`${post.id}_${idx}`}
                        src={url}
                        alt={post.caption || 'VYBE post media'}
                        loading="lazy"
                        style={{
                          width: '100%',
                          maxHeight: 540,
                          objectFit: 'cover',
                          borderRadius: 14,
                          background: '#0f1018'
                        }}
                      />
                    )
                  )}
                </div>
              )}

              {/* Engagement Bar */}
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  paddingTop: 10,
                  borderTop: '1px solid var(--border-subtle)'
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 18 }}>
                  <button
                    type="button"
                    onClick={async () => {
                      if (!requireAuth()) return;
                      await togglePostResonance(post.id, currentUser.id, post.isLiked);
                      await onRefreshAll();
                    }}
                    style={{
                      background: 'none',
                      border: 'none',
                      color: post.isLiked ? 'var(--vybe-cyan)' : 'var(--text-secondary)',
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 6,
                      cursor: 'pointer',
                      fontWeight: 600
                    }}
                    aria-label="Resonate with post"
                  >
                    <VybeResonanceIcon active={post.isLiked} size={22} />
                    <span>{post.likesCount}</span>
                  </button>

                  <button
                    type="button"
                    onClick={() =>
                      setOpenCommentsPostId(openCommentsPostId === post.id ? null : post.id)
                    }
                    style={{
                      background: 'none',
                      border: 'none',
                      color: 'var(--text-secondary)',
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 6,
                      cursor: 'pointer',
                      fontWeight: 600
                    }}
                  >
                    <MessageCircle size={19} />
                    <span>{post.commentsCount}</span>
                  </button>

                  <button
                    type="button"
                    onClick={async () => {
                      const shareUrl = `${window.location.origin}/u/${post.author.username}`;
                      if (navigator.share) {
                        await navigator.share({ title: 'VYBE', text: post.caption, url: shareUrl }).catch(() => undefined);
                      } else {
                        await navigator.clipboard.writeText(shareUrl);
                        setToastMsg('Link copied to clipboard.');
                        window.setTimeout(() => setToastMsg(null), 2200);
                      }
                    }}
                    style={{
                      background: 'none',
                      border: 'none',
                      color: 'var(--text-secondary)',
                      display: 'inline-flex',
                      alignItems: 'center',
                      cursor: 'pointer'
                    }}
                    aria-label="Share post"
                  >
                    <Share2 size={18} />
                  </button>
                </div>

                <button
                  type="button"
                  onClick={async () => {
                    if (!requireAuth()) return;
                    await toggleSavePost(post.id, currentUser.id, post.isSaved);
                    await onRefreshAll();
                  }}
                  style={{
                    background: 'none',
                    border: 'none',
                    color: post.isSaved ? 'var(--vybe-gold)' : 'var(--text-secondary)',
                    cursor: 'pointer'
                  }}
                  aria-label="Save post"
                >
                  <Bookmark size={19} fill={post.isSaved ? '#FFD54F' : 'none'} />
                </button>
              </div>

              {/* Comments Section */}
              {openCommentsPostId === post.id && (
                <div style={{ marginTop: 12, paddingTop: 12, borderTop: '1px solid var(--border-subtle)' }}>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 10 }}>
                    {post.comments.map((c) => (
                      <div key={c.id} style={{ fontSize: '0.85rem' }}>
                        <strong style={{ color: 'var(--vybe-cyan)' }}>@{c.author.username}</strong>{' '}
                        <span>{c.content}</span>{' '}
                        <span style={{ color: 'var(--text-muted)', fontSize: '0.75rem' }}>
                          • <RelativeTimeText iso={c.createdAtIso} />
                        </span>
                      </div>
                    ))}
                  </div>
                  <form
                    onSubmit={async (e) => {
                      e.preventDefault();
                      if (!requireAuth()) return;
                      if (!commentInput.trim()) return;
                      await addPostComment(post.id, currentUser.id, commentInput);
                      setCommentInput('');
                      await onRefreshAll();
                    }}
                    style={{ display: 'flex', gap: 8 }}
                  >
                    <input
                      className="vybe-input"
                      placeholder="Write a comment…"
                      value={commentInput}
                      onChange={(e) => setCommentInput(e.target.value)}
                    />
                    <button type="submit" className="vybe-btn vybe-btn-violet">
                      Send
                    </button>
                  </form>
                </div>
              )}
            </article>
          ))}

          {hasMorePosts && (
            <button
              type="button"
              className="vybe-btn vybe-btn-outline"
              onClick={() => void onLoadMore()}
              style={{ alignSelf: 'center' }}
            >
              Load More Posts
            </button>
          )}
        </div>
      )}

      {/* Separated Post Composer Modal */}
      {showPostComposer && (
        <div className="vybe-modal-backdrop">
          <div className="vybe-modal-panel">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
              <h3 className="vybe-brand-font">Create VYBE Post</h3>
              <button
                type="button"
                onClick={() => {
                  stopBrowserCamera();
                  setShowPostComposer(false);
                  onDraftDirtyChange(false);
                }}
                style={{ background: 'none', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer' }}
              >
                <X size={20} />
              </button>
            </div>

            {publishError && (
              <div
                style={{
                  background: 'rgba(255,77,109,0.15)',
                  color: '#ff8da1',
                  padding: 10,
                  borderRadius: 10,
                  fontSize: '0.84rem',
                  marginBottom: 12
                }}
              >
                {publishError}
              </div>
            )}

            <form onSubmit={handlePublishPost} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <textarea
                className="vybe-textarea"
                rows={4}
                placeholder="What energy are you sharing right now?"
                value={caption}
                onChange={(e) => setCaption(e.target.value)}
              />

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
                <select
                  className="vybe-select"
                  value={postMood}
                  onChange={(e) => setPostMood(e.target.value as MoodZone)}
                >
                  {MOOD_OPTIONS.map((m) => (
                    <option key={m.key} value={m.key}>
                      {m.emoji} {m.label}
                    </option>
                  ))}
                </select>
                <div style={{ position: 'relative' }}>
                  <input
                    className="vybe-input"
                    placeholder="Location (optional)"
                    value={locationName}
                    onChange={(e) => setLocationName(e.target.value)}
                  />
                  <MapPin
                    size={15}
                    style={{ position: 'absolute', right: 12, top: 13, color: 'var(--text-muted)' }}
                  />
                </div>
              </div>

              {cameraActive && (
                <div style={{ borderRadius: 12, overflow: 'hidden', background: '#000' }}>
                  <video ref={cameraVideoRef} autoPlay muted playsInline style={{ width: '100%', maxHeight: 280 }} />
                  <div style={{ display: 'flex', gap: 8, padding: 8, justifyContent: 'center' }}>
                    <button type="button" className="vybe-btn vybe-btn-primary" onClick={captureCameraSnapshot}>
                      Snap Unfiltered Photo
                    </button>
                    <button type="button" className="vybe-btn vybe-btn-outline" onClick={stopBrowserCamera}>
                      Cancel
                    </button>
                  </div>
                </div>
              )}

              {selectedFiles.length > 0 && (
                <div style={{ fontSize: '0.82rem', color: 'var(--vybe-cyan)' }}>
                  Attached ({selectedFiles.length}): {selectedFiles.map((f) => f.name).join(', ')}
                </div>
              )}

              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                <label className="vybe-btn vybe-btn-outline" style={{ cursor: 'pointer' }}>
                  <ImageIcon size={16} /> Photo / Video
                  <input
                    type="file"
                    accept="image/*,video/*"
                    multiple
                    hidden
                    onChange={(e) => setSelectedFiles(Array.from(e.target.files || []))}
                  />
                </label>
                <button type="button" className="vybe-btn vybe-btn-outline" onClick={() => void startBrowserCamera()}>
                  <Camera size={16} /> Raw Camera
                </button>
                <button
                  type="button"
                  className={`vybe-btn ${isReel ? 'vybe-btn-violet' : 'vybe-btn-outline'}`}
                  onClick={() => setIsReel(!isReel)}
                >
                  <Film size={16} /> {isReel ? 'VYBE Reel ✓' : 'Mark as Reel'}
                </button>
              </div>

              {publishProgress && (
                <div style={{ fontSize: '0.82rem', color: 'var(--vybe-cyan)' }}>{publishProgress}</div>
              )}

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 6 }}>
                <button
                  type="button"
                  className="vybe-btn vybe-btn-outline"
                  onClick={() => {
                    stopBrowserCamera();
                    setShowPostComposer(false);
                    onDraftDirtyChange(false);
                  }}
                >
                  Cancel
                </button>
                <button type="submit" className="vybe-btn vybe-btn-primary" disabled={publishing}>
                  {publishing ? 'Publishing…' : 'Publish Post'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Separated Story Composer Modal */}
      {showStoryComposer && (
        <div className="vybe-modal-backdrop">
          <div className="vybe-modal-panel">
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 14 }}>
              <h3 className="vybe-brand-font">Publish 24h Story</h3>
              <button
                type="button"
                onClick={() => {
                  setShowStoryComposer(false);
                  onDraftDirtyChange(false);
                }}
                style={{ background: 'none', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer' }}
              >
                <X size={20} />
              </button>
            </div>
            {storyError && (
              <div style={{ color: 'var(--vybe-danger)', fontSize: '0.84rem', marginBottom: 10 }}>
                {storyError}
              </div>
            )}
            <form onSubmit={handlePublishStory} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <textarea
                className="vybe-textarea"
                rows={3}
                placeholder="Write a story caption or text story…"
                value={storyCaption}
                onChange={(e) => setStoryCaption(e.target.value)}
              />
              <label className="vybe-btn vybe-btn-outline" style={{ cursor: 'pointer' }}>
                <ImageIcon size={16} /> {storyFile ? storyFile.name : 'Choose Photo or Video'}
                <input
                  type="file"
                  accept="image/*,video/*"
                  hidden
                  onChange={(e) => setStoryFile(e.target.files?.[0] || null)}
                />
              </label>
              <select
                className="vybe-select"
                value={storyAudience}
                onChange={(e) => setStoryAudience(e.target.value as 'EVERYONE' | 'VYBE_MATCHES')}
              >
                <option value="EVERYONE">Everyone (Public / Followers)</option>
                <option value="VYBE_MATCHES">VYBE Matches (Close Circle Only)</option>
              </select>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                <button
                  type="button"
                  className="vybe-btn vybe-btn-outline"
                  onClick={() => {
                    setShowStoryComposer(false);
                    onDraftDirtyChange(false);
                  }}
                >
                  Cancel
                </button>
                <button type="submit" className="vybe-btn vybe-btn-primary" disabled={storyPublishing}>
                  {storyPublishing ? 'Uploading…' : 'Publish Story'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Full-Screen Story Viewer */}
      {activeStory && (
        <div className="vybe-modal-backdrop" onClick={() => setActiveStory(null)}>
          <div
            className="vybe-modal-panel"
            style={{ maxWidth: 430, padding: 16, background: '#0b0c14' }}
            onClick={(e) => e.stopPropagation()}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <VybeAvatar user={activeStory.author} size={36} />
                <div>
                  <strong style={{ fontSize: '0.9rem' }}>{activeStory.author.displayName}</strong>
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
                    <RelativeTimeText iso={activeStory.createdAtIso} fallback="Just now" /> • 24h Story
                  </div>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setActiveStory(null)}
                style={{ background: 'none', border: 'none', color: '#fff', cursor: 'pointer' }}
              >
                <X size={20} />
              </button>
            </div>

            {activeStory.mediaType === 'TEXT' || activeStory.mediaUrl.startsWith('vybe://') ? (
              <div
                style={{
                  minHeight: 340,
                  borderRadius: 16,
                  background: 'var(--vybe-gradient)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  padding: 28,
                  textAlign: 'center',
                  fontSize: '1.25rem',
                  fontWeight: 700
                }}
              >
                {activeStory.caption}
              </div>
            ) : activeStory.mediaType === 'VIDEO' ? (
              <video
                src={activeStory.mediaUrl}
                controls
                autoPlay
                playsInline
                style={{ width: '100%', maxHeight: 480, borderRadius: 14 }}
              />
            ) : (
              <img
                src={activeStory.mediaUrl}
                alt={activeStory.caption || 'Story'}
                style={{ width: '100%', maxHeight: 480, objectFit: 'contain', borderRadius: 14 }}
              />
            )}

            {activeStory.caption && activeStory.mediaType !== 'TEXT' && (
              <p style={{ marginTop: 12, fontSize: '0.9rem', textAlign: 'center' }}>{activeStory.caption}</p>
            )}
          </div>
        </div>
      )}

      {/* Report / Copyright Claim Modal */}
      {reportingPost && (
        <div className="vybe-modal-backdrop">
          <div className="vybe-modal-panel">
            <h3 style={{ marginBottom: 10 }}>Report Content or Copyright Claim</h3>
            <textarea
              className="vybe-textarea"
              rows={3}
              placeholder="Describe the safety issue or copyright infringement details…"
              value={reportReason}
              onChange={(e) => setReportReason(e.target.value)}
            />
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 12 }}>
              <button type="button" className="vybe-btn vybe-btn-outline" onClick={() => setReportingPost(null)}>
                Cancel
              </button>
              <button
                type="button"
                className="vybe-btn vybe-btn-danger"
                onClick={async () => {
                  if (!reportReason.trim()) return;
                  await submitReportOrCopyrightClaim({
                    reporterId: currentUser.id,
                    targetType: reportReason.toLowerCase().includes('copyright') ? 'COPYRIGHT' : 'POST',
                    targetId: reportingPost.id,
                    reason: reportReason
                  });
                  setReportReason('');
                  setReportingPost(null);
                  setToastMsg('Report submitted to VYBE Trust & Safety.');
                }}
              >
                Submit Report
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
