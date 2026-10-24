import React, { useState, useEffect } from 'react';
import {
  User,
  Settings,
  MessageCircle,
  Phone,
  Video,
  Grid,
  Film,
  Bookmark,
  Calendar,
  Lock,
  Edit3,
  X,
  Upload,
  ShieldAlert,
  Check
} from 'lucide-react';
import { VybeAvatar, VerifiedBadge, VybeResonanceIcon } from '../components/VybeCoreComponents';
import {
  fetchFeedPosts,
  fetchProfileById,
  toggleFollowUser,
  checkFollowStatus,
  uploadMediaToBucket,
  submitReportOrCopyrightClaim
} from '../lib/vybeApi';
import { formatJoinedMonthYear, isRecentlyOnline } from '../lib/utils';
import { supabase } from '../lib/supabase';
import type { VybePost, VybeUser } from '../types/vybe';

export const ProfilePage: React.FC<{
  currentUser: VybeUser;
  targetUser?: VybeUser;
  onUpdateCurrentUser: (updated: VybeUser) => void;
  onStartMessageWith: (peer: VybeUser) => void;
  onStartCall: (peer: VybeUser, isVideo: boolean) => void;
  onOpenSettings: () => void;
}> = ({
  currentUser,
  targetUser,
  onUpdateCurrentUser,
  onStartMessageWith,
  onStartCall,
  onOpenSettings
}) => {
  const profile = targetUser || currentUser;
  const isMe = profile.id === currentUser.id;

  const [posts, setPosts] = useState<VybePost[]>([]);
  const [loadingPosts, setLoadingPosts] = useState(true);
  const [activeTab, setActiveTab] = useState<'POSTS' | 'REELS' | 'SAVED'>('POSTS');
  const [followStatus, setFollowStatus] = useState<'NONE' | 'PENDING' | 'ACCEPTED'>('NONE');
  const [isFollowing, setIsFollowing] = useState(false);

  // Edit Profile Modal
  const [showEditModal, setShowEditModal] = useState(false);
  const [editDisplayName, setEditDisplayName] = useState(profile.displayName);
  const [editBio, setEditBio] = useState(profile.bio);
  const [editIsPrivate, setEditIsPrivate] = useState(profile.isPrivate);
  const [editMessagePrivacy, setEditMessagePrivacy] = useState(profile.messagePrivacy);
  const [avatarFile, setAvatarFile] = useState<File | null>(null);
  const [avatarPreview, setAvatarPreview] = useState<string | null>(null);
  const [savingProfile, setSavingProfile] = useState(false);

  // Report Modal
  const [showReportModal, setShowReportModal] = useState(false);
  const [reportReason, setReportReason] = useState('');
  const [reportSubmitted, setReportSubmitted] = useState(false);

  useEffect(() => {
    const loadProfileData = async () => {
      setLoadingPosts(true);
      try {
        const res = await fetchFeedPosts(currentUser.id, 0, 50);
        const userPosts = res.filter((p: VybePost) => p.author.id === profile.id);
        setPosts(userPosts);

        if (!isMe) {
          const status = await checkFollowStatus(currentUser.id, profile.id);
          setFollowStatus(status);
          setIsFollowing(status === 'ACCEPTED');
        }
      } catch {
        // Fallback
      } finally {
        setLoadingPosts(false);
      }
    };
    void loadProfileData();
  }, [profile.id, currentUser.id, isMe]);

  const handleToggleFollow = async () => {
    const nextFollowing = !isFollowing;
    setIsFollowing(nextFollowing);
    setFollowStatus(nextFollowing ? (profile.isPrivate ? 'PENDING' : 'ACCEPTED') : 'NONE');

    try {
      await toggleFollowUser(currentUser.id, profile, !nextFollowing);
    } catch {
      setIsFollowing(!nextFollowing);
    }
  };

  const handleAvatarChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setAvatarFile(file);
    setAvatarPreview(URL.createObjectURL(file));
  };

  const handleSaveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    setSavingProfile(true);
    try {
      let finalAvatarUrl = profile.avatarUrl;
      if (avatarFile) {
        const uploaded = await uploadMediaToBucket(
          'avatars',
          currentUser.id,
          avatarFile,
          () => undefined
        );
        finalAvatarUrl = uploaded.publicUrl;
      }

      const { data, error } = await supabase
        .from('profiles')
        .update({
          display_name: editDisplayName.trim(),
          bio: editBio.trim(),
          avatar_url: finalAvatarUrl,
          is_private_account: editIsPrivate,
          message_privacy: editMessagePrivacy
        })
        .eq('id', currentUser.id)
        .select('*')
        .single();

      if (error) throw error;

      const updatedUser: VybeUser = {
        ...currentUser,
        displayName: editDisplayName.trim(),
        bio: editBio.trim(),
        avatarUrl: finalAvatarUrl,
        isPrivate: editIsPrivate,
        messagePrivacy: editMessagePrivacy
      };
      onUpdateCurrentUser(updatedUser);
      setShowEditModal(false);
    } catch {
      // Handle error
    } finally {
      setSavingProfile(false);
    }
  };

  const handleSubmitReport = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!reportReason.trim()) return;
    try {
      await submitReportOrCopyrightClaim({
        reporterId: currentUser.id,
        targetType: 'USER',
        targetId: profile.id,
        reason: reportReason.trim()
      });
      setReportSubmitted(true);
      setTimeout(() => {
        setShowReportModal(false);
        setReportSubmitted(false);
        setReportReason('');
      }, 1800);
    } catch {
      // Handle error
    }
  };

  const displayedPosts = posts.filter((p) => {
    if (activeTab === 'REELS') return p.isVybesReel || p.mediaType === 'VIDEO';
    if (activeTab === 'SAVED') return p.isSaved;
    return !p.isVybesReel;
  });

  const isOnline = profile.isOnline || isRecentlyOnline(profile.lastSeenAtIso);

  return (
    <div style={{ maxWidth: '860px', margin: '0 auto', padding: '24px 16px', display: 'flex', flexDirection: 'column', gap: '24px' }}>
      {/* Profile Header Card */}
      <div className="vybe-card" style={{ padding: 0, overflow: 'hidden' }}>
        {/* Cover image banner */}
        <div
          style={{
            height: '160px',
            background: profile.coverUrl ? `url(${profile.coverUrl}) center/cover` : 'var(--vybe-gradient)',
            position: 'relative'
          }}
        />

        <div style={{ padding: '0 24px 24px', marginTop: '-46px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', flexWrap: 'wrap', gap: '16px' }}>
            {/* Avatar & Online Badge */}
            <div style={{ position: 'relative' }}>
              <div style={{ border: '4px solid var(--bg-surface)', borderRadius: '50%', background: 'var(--bg-surface)' }}>
                <VybeAvatar user={profile} size={92} />
              </div>
              {isOnline && (
                <span
                  style={{
                    position: 'absolute',
                    bottom: '6px',
                    right: '6px',
                    width: '16px',
                    height: '16px',
                    background: 'var(--vybe-green)',
                    borderRadius: '50%',
                    border: '3px solid var(--bg-surface)'
                  }}
                  title="Active now"
                />
              )}
            </div>

            {/* Profile Action Buttons */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              {isMe ? (
                <>
                  <button onClick={() => setShowEditModal(true)} className="vybe-btn">
                    <Edit3 size={16} />
                    <span>Edit Profile</span>
                  </button>
                  <button onClick={onOpenSettings} className="vybe-btn" title="Settings">
                    <Settings size={18} />
                  </button>
                </>
              ) : (
                <>
                  <button
                    onClick={handleToggleFollow}
                    className={`vybe-btn ${isFollowing ? '' : 'vybe-btn-primary'}`}
                    style={{ minWidth: '110px' }}
                  >
                    {followStatus === 'PENDING' ? 'Requested' : isFollowing ? 'Following' : 'Follow'}
                  </button>

                  <button onClick={() => onStartMessageWith(profile)} className="vybe-btn" title="Message">
                    <MessageCircle size={18} />
                    <span>Message</span>
                  </button>

                  <button onClick={() => onStartCall(profile, false)} className="vybe-btn" title="Voice Call">
                    <Phone size={18} color="var(--vybe-cyan)" />
                  </button>

                  <button onClick={() => onStartCall(profile, true)} className="vybe-btn" title="Video Call">
                    <Video size={18} color="var(--vybe-violet)" />
                  </button>

                  <button
                    onClick={() => setShowReportModal(true)}
                    className="vybe-btn"
                    style={{ padding: '8px' }}
                    title="Report"
                  >
                    <ShieldAlert size={18} color="var(--text-muted)" />
                  </button>
                </>
              )}
            </div>
          </div>

          {/* User Names & Bio */}
          <div style={{ marginTop: '16px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flexWrap: 'wrap' }}>
              <h2 style={{ fontSize: '1.4rem', fontWeight: 800, margin: 0 }}>{profile.displayName}</h2>
              <VerifiedBadge user={profile} />
              {profile.isPrivate && (
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '0.75rem', background: 'rgba(124, 77, 255, 0.15)', color: 'var(--vybe-violet)', padding: '2px 8px', borderRadius: 'var(--radius-pill)', fontWeight: 600 }}>
                  <Lock size={12} />
                  Private
                </span>
              )}
            </div>
            <p style={{ margin: '2px 0 0', color: 'var(--text-muted)', fontSize: '0.9rem' }}>@{profile.username}</p>

            {profile.bio && (
              <p style={{ margin: '12px 0 0', fontSize: '0.95rem', lineHeight: '1.5', color: 'var(--text-primary)', maxWidth: '640px' }}>
                {profile.bio}
              </p>
            )}

            <div style={{ display: 'flex', alignItems: 'center', gap: '16px', marginTop: '14px', fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '5px' }}>
                <Calendar size={15} />
                <span>{formatJoinedMonthYear(profile.createdAtIso)}</span>
              </div>
              <div>
                <span>{isOnline ? 'Active now' : 'Offline'}</span>
              </div>
            </div>
          </div>

          {/* Social Stats: Followers, Following, Posts */}
          <div style={{ display: 'flex', gap: '32px', marginTop: '20px', paddingTop: '16px', borderTop: '1px solid var(--border-subtle)' }}>
            <div>
              <span style={{ fontWeight: 800, fontSize: '1.1rem', color: 'var(--text-primary)' }}>{profile.postsCount}</span>
              <span style={{ marginLeft: '6px', fontSize: '0.85rem', color: 'var(--text-secondary)' }}>Posts</span>
            </div>
            <div>
              <span style={{ fontWeight: 800, fontSize: '1.1rem', color: 'var(--text-primary)' }}>{profile.followersCount}</span>
              <span style={{ marginLeft: '6px', fontSize: '0.85rem', color: 'var(--text-secondary)' }}>Followers</span>
            </div>
            <div>
              <span style={{ fontWeight: 800, fontSize: '1.1rem', color: 'var(--text-primary)' }}>{profile.followingCount}</span>
              <span style={{ marginLeft: '6px', fontSize: '0.85rem', color: 'var(--text-secondary)' }}>Following</span>
            </div>
          </div>
        </div>
      </div>

      {/* Content Tabs */}
      <div style={{ display: 'flex', borderBottom: '1px solid var(--border-subtle)', gap: '8px' }}>
        <button
          onClick={() => setActiveTab('POSTS')}
          className="vybe-btn"
          style={{
            background: 'none',
            borderBottom: activeTab === 'POSTS' ? '2px solid var(--vybe-violet)' : '2px solid transparent',
            borderRadius: 0,
            padding: '10px 18px',
            color: activeTab === 'POSTS' ? 'var(--text-primary)' : 'var(--text-muted)'
          }}
        >
          <Grid size={18} />
          <span>Posts</span>
        </button>

        <button
          onClick={() => setActiveTab('REELS')}
          className="vybe-btn"
          style={{
            background: 'none',
            borderBottom: activeTab === 'REELS' ? '2px solid var(--vybe-violet)' : '2px solid transparent',
            borderRadius: 0,
            padding: '10px 18px',
            color: activeTab === 'REELS' ? 'var(--text-primary)' : 'var(--text-muted)'
          }}
        >
          <Film size={18} />
          <span>Reels</span>
        </button>

        {isMe && (
          <button
            onClick={() => setActiveTab('SAVED')}
            className="vybe-btn"
            style={{
              background: 'none',
              borderBottom: activeTab === 'SAVED' ? '2px solid var(--vybe-violet)' : '2px solid transparent',
              borderRadius: 0,
              padding: '10px 18px',
              color: activeTab === 'SAVED' ? 'var(--text-primary)' : 'var(--text-muted)'
            }}
          >
            <Bookmark size={18} />
            <span>Saved</span>
          </button>
        )}
      </div>

      {/* Posts Grid */}
      {loadingPosts ? (
        <div style={{ padding: '40px 0', textAlign: 'center', color: 'var(--text-secondary)' }}>
          Loading content...
        </div>
      ) : displayedPosts.length === 0 ? (
        <div style={{ padding: '60px 20px', textAlign: 'center', color: 'var(--text-muted)' }}>
          <p>No {activeTab.toLowerCase()} shared yet.</p>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))', gap: '14px' }}>
          {displayedPosts.map((post) => {
            const mediaUrl = post.mediaUrls[0];
            const isVideo = post.mediaType === 'VIDEO' || post.isVybesReel;

            return (
              <div
                key={post.id}
                style={{
                  position: 'relative',
                  aspectRatio: isVideo ? '9/16' : '1/1',
                  borderRadius: 'var(--radius-md)',
                  overflow: 'hidden',
                  background: 'var(--bg-surface)',
                  border: '1px solid var(--border-subtle)'
                }}
              >
                {mediaUrl ? (
                  isVideo ? (
                    <video src={mediaUrl} style={{ width: '100%', height: '100%', objectFit: 'cover' }} muted />
                  ) : (
                    <img src={mediaUrl} alt={post.caption} style={{ width: '100%', height: '100%', objectFit: 'cover' }} loading="lazy" />
                  )
                ) : (
                  <div style={{ width: '100%', height: '100%', padding: '16px', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'var(--bg-elevated)', textAlign: 'center', fontSize: '0.85rem' }}>
                    {post.caption}
                  </div>
                )}
                <div style={{ position: 'absolute', bottom: '10px', right: '12px', display: 'flex', alignItems: 'center', gap: '4px', fontSize: '0.8rem', color: '#fff', textShadow: '0 1px 3px rgba(0,0,0,0.8)' }}>
                  <VybeResonanceIcon active={post.isLiked} size={15} />
                  <span>{post.likesCount}</span>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Edit Profile Modal */}
      {showEditModal && (
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
          onClick={() => !savingProfile && setShowEditModal(false)}
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
              <h3 style={{ margin: 0, fontSize: '1.2rem', fontWeight: 800 }}>Edit Profile</h3>
              <button onClick={() => setShowEditModal(false)} style={{ background: 'none', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer' }}>
                <X size={20} />
              </button>
            </div>

            <form onSubmit={handleSaveProfile} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
              {/* Avatar Uploader */}
              <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                <div style={{ width: '64px', height: '64px', borderRadius: '50%', overflow: 'hidden', background: 'var(--bg-elevated)' }}>
                  <img src={avatarPreview || profile.avatarUrl || '/icons/default-avatar.png'} alt="Preview" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                </div>
                <label className="vybe-btn" style={{ cursor: 'pointer', fontSize: '0.85rem' }}>
                  <Upload size={16} />
                  <span>Change Photo</span>
                  <input type="file" accept="image/*" onChange={handleAvatarChange} style={{ display: 'none' }} />
                </label>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '4px' }}>
                  Display Name
                </label>
                <input
                  type="text"
                  value={editDisplayName}
                  onChange={(e) => setEditDisplayName(e.target.value)}
                  required
                  style={{
                    width: '100%',
                    background: 'var(--bg-elevated)',
                    border: '1px solid var(--border-subtle)',
                    borderRadius: 'var(--radius-sm)',
                    padding: '10px 14px',
                    color: 'var(--text-primary)',
                    fontSize: '0.9rem',
                    outline: 'none'
                  }}
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '4px' }}>
                  Bio
                </label>
                <textarea
                  rows={3}
                  value={editBio}
                  onChange={(e) => setEditBio(e.target.value)}
                  placeholder="Tell the VYBE community about yourself..."
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

              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px', background: 'var(--bg-elevated)', borderRadius: 'var(--radius-sm)' }}>
                <div>
                  <span style={{ display: 'block', fontSize: '0.9rem', fontWeight: 600 }}>Private Account</span>
                  <span style={{ fontSize: '0.78rem', color: 'var(--text-secondary)' }}>Only approved followers can view your posts and stories.</span>
                </div>
                <input
                  type="checkbox"
                  checked={editIsPrivate}
                  onChange={(e) => setEditIsPrivate(e.target.checked)}
                  style={{ width: '18px', height: '18px', accentColor: 'var(--vybe-violet)', cursor: 'pointer' }}
                />
              </div>

              <button type="submit" disabled={savingProfile} className="vybe-btn vybe-btn-primary" style={{ padding: '12px', marginTop: '6px' }}>
                {savingProfile ? 'Saving Changes...' : 'Save Profile'}
              </button>
            </form>
          </div>
        </div>
      )}

      {/* Report Modal */}
      {showReportModal && (
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
          onClick={() => setShowReportModal(false)}
        >
          <div
            style={{
              width: '100%',
              maxWidth: '440px',
              background: 'var(--bg-surface)',
              borderRadius: 'var(--radius-lg)',
              border: '1px solid var(--border-subtle)',
              padding: '24px',
              display: 'flex',
              flexDirection: 'column',
              gap: '14px'
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h3 style={{ margin: 0, fontSize: '1.15rem', fontWeight: 800 }}>Report User</h3>
              <button onClick={() => setShowReportModal(false)} style={{ background: 'none', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer' }}>
                <X size={20} />
              </button>
            </div>

            {reportSubmitted ? (
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '8px', padding: '24px 0' }}>
                <Check size={36} color="var(--vybe-green)" />
                <p style={{ fontWeight: 600, color: 'var(--text-primary)' }}>Report Submitted</p>
                <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>Thank you for keeping VYBE safe.</p>
              </div>
            ) : (
              <form onSubmit={handleSubmitReport} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                <p style={{ margin: 0, fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
                  Please describe the violation or issue regarding @{profile.username}:
                </p>
                <textarea
                  rows={3}
                  placeholder="Harassment, impersonation, spam, or copyright issue..."
                  value={reportReason}
                  onChange={(e) => setReportReason(e.target.value)}
                  required
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
                <button type="submit" disabled={!reportReason.trim()} className="vybe-btn vybe-btn-primary">
                  Submit Report
                </button>
              </form>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
