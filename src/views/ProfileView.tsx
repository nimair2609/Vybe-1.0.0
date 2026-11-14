import React, { useState, useEffect, useRef } from 'react';
import {
  Settings,
  QrCode,
  Grid,
  Bookmark,
  PlaySquare,
  MapPin,
  Calendar,
  ArrowLeft,
  MessageCircle,
  UserPlus,
  Check,
  ShieldCheck,
  Lock,
  Smartphone,
  Share2,
  Edit3,
  Clock,
  Loader2,
  AlertCircle,
  ExternalLink
} from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { OfficialBadge } from '../components/Badges';
import { FingerHeartReaction } from '../components/FingerHeartReaction';
import { VybeEmptyState } from '../components/VybeDesignSystem';
import { OptimizedImage, VybeAvatar } from '../components/OptimizedImage';
import { LivePulseStatus } from '../types';
import { buildCanonicalWebProfileUrl } from './QrCodeModal';

export const ProfileView: React.FC = () => {
  const {
    currentUser,
    viewedProfileUser,
    viewedProfilePosts,
    profileRouteTarget,
    profileLookupState,
    profileLookupError,
    closeUserProfile,
    posts,
    reels,
    followingIds,
    requestedFollowIds,
    toggleFollowUser,
    startConversationWithUser,
    setSelectedTab,
    openModal,
    openAuthModal,
    openSharedPostModal,
    toggleLikePost,
    setCurrentUserPulse,
    updateMyProfile
  } = useVybe();

  const [activeTab, setActiveTab] = useState<'POSTS' | 'REELS' | 'SAVED'>('POSTS');
  const [showPulseMenu, setShowPulseMenu] = useState(false);
  const [messageError, setMessageError] = useState<string | null>(null);
  const [linkCopied, setLinkCopied] = useState(false);
  const [showEditProfile, setShowEditProfile] = useState(false);
  const [editDisplayName, setEditDisplayName] = useState('');
  const [editBio, setEditBio] = useState('');
  const [editPrivate, setEditPrivate] = useState(false);
  const [isSavingProfile, setIsSavingProfile] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);
  const pulseMenuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!showPulseMenu) return;
    const onOutside = (e: MouseEvent) => {
      if (pulseMenuRef.current && !pulseMenuRef.current.contains(e.target as Node)) {
        setShowPulseMenu(false);
      }
    };
    document.addEventListener('mousedown', onOutside);
    return () => document.removeEventListener('mousedown', onOutside);
  }, [showPulseMenu]);

  // Handle Public Route Loading / Unavailable / Error states
  if (profileRouteTarget && profileLookupState === 'LOADING' && !viewedProfileUser) {
    return (
      <div className="max-w-2xl mx-auto px-4 py-16 text-center space-y-3">
        <Loader2 className="w-8 h-8 animate-spin text-[var(--vybe-accent)] mx-auto" />
        <p className="text-sm font-bold text-[var(--vybe-text)]">Loading VYBE Profile...</p>
        <p className="text-xs text-[var(--vybe-text-secondary)]">
          Fetching @{profileRouteTarget.username || profileRouteTarget.userId} directly from VYBE.
        </p>
      </div>
    );
  }

  if (profileRouteTarget && (profileLookupState === 'NOT_FOUND' || profileLookupState === 'ERROR')) {
    return (
      <div className="max-w-xl mx-auto px-4 py-14 space-y-4">
        <div className="p-8 rounded-3xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-center space-y-3 shadow-sm">
          <AlertCircle className="w-10 h-10 text-amber-500 mx-auto" />
          <h1 className="text-lg font-extrabold text-[var(--vybe-text)]">
            {profileLookupState === 'NOT_FOUND' ? 'Profile Unavailable' : 'Unable to Load Profile'}
          </h1>
          <p className="text-xs text-[var(--vybe-text-secondary)] max-w-sm mx-auto">
            {profileLookupError || 'This VYBE profile could not be found or is no longer active.'}
          </p>
          <div className="pt-2 flex items-center justify-center gap-2">
            <button
              type="button"
              onClick={closeUserProfile}
              className="px-5 py-2.5 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold"
            >
              Back to VYBE Feed
            </button>
          </div>
        </div>
      </div>
    );
  }

  const targetUser = viewedProfileUser || currentUser;

  if (!targetUser) {
    return (
      <div className="max-w-xl mx-auto px-4 py-14">
        <div className="p-8 rounded-3xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-center space-y-4 shadow-sm">
          <Lock className="w-9 h-9 text-[var(--vybe-accent)] mx-auto" />
          <h1 className="text-lg font-extrabold text-[var(--vybe-text)]">Sign in to view your profile</h1>
          <p className="text-xs text-[var(--vybe-text-secondary)] max-w-sm mx-auto">
            Access your posts, saved items, QR code, and synced Android identity.
          </p>
          <button
            type="button"
            onClick={() => openAuthModal('Sign in to view and manage your VYBE profile.')}
            className="px-6 py-2.5 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold"
          >
            Sign In
          </button>
        </div>
      </div>
    );
  }

  const isOwnProfile = Boolean(currentUser && targetUser.id.toLowerCase() === currentUser.id.toLowerCase());
  const isFollowingPeer = followingIds.includes(targetUser.id);
  const isFollowRequested = requestedFollowIds.includes(targetUser.id);
  const isPrivateLocked = Boolean(targetUser.isPrivate && !isOwnProfile && !isFollowingPeer);

  const feedPostsForUser = posts.filter(p => p.author.id.toLowerCase() === targetUser.id.toLowerCase());
  const combinedAuthorPosts = viewedProfilePosts.length > 0 ? viewedProfilePosts : feedPostsForUser;
  const userPosts = isPrivateLocked ? [] : combinedAuthorPosts;
  const userReels = isPrivateLocked
    ? []
    : combinedAuthorPosts.filter(r => r.isVybesReel || r.mediaType === 'REEL');
  const savedPosts = isOwnProfile ? posts.filter(p => p.isSaved) : [];
  const visibleReels = userReels.length > 0 ? userReels : isOwnProfile ? reels.filter(r => r.author.id === targetUser.id) : [];

  const canonicalProfileUrl = buildCanonicalWebProfileUrl(targetUser);
  const androidAppDeepLink = `vybe://profile/${encodeURIComponent(targetUser.id)}?u=${encodeURIComponent(targetUser.username)}`;

  const pulseOptions: LivePulseStatus[] = [
    'Open to chatting',
    'Feeling creative',
    'Listening to music',
    'Looking for gaming partner',
    'Taking quiet time',
    'None'
  ];

  const handleMessagePeer = async () => {
    setMessageError(null);
    const res = await startConversationWithUser(targetUser);
    if (!res.ok) {
      setMessageError(res.error || 'Unable to message this user.');
      return;
    }
    setSelectedTab('INBOX');
  };

  const handleShareProfile = async () => {
    if (navigator.share) {
      try {
        await navigator.share({
          title: `${targetUser.displayName} (@${targetUser.username}) on VYBE`,
          url: canonicalProfileUrl
        });
        return;
      } catch {
        // user cancelled share
      }
    }
    await navigator.clipboard?.writeText(canonicalProfileUrl);
    setLinkCopied(true);
    setTimeout(() => setLinkCopied(false), 2000);
  };

  const openEditDrawer = () => {
    setEditDisplayName(targetUser.displayName);
    setEditBio(targetUser.bio);
    setEditPrivate(Boolean(targetUser.isPrivate));
    setEditError(null);
    setShowEditProfile(true);
  };

  const handleSaveProfileEdit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSavingProfile(true);
    setEditError(null);
    const res = await updateMyProfile({
      display_name: editDisplayName.trim() || targetUser.username,
      bio: editBio.trim(),
      is_private_account: editPrivate
    });
    setIsSavingProfile(false);
    if (!res.ok) {
      setEditError(res.error || 'Could not save profile changes.');
      return;
    }
    setShowEditProfile(false);
  };

  return (
    <div className="w-full max-w-3xl mx-auto pb-28 lg:pb-12">
      {/* Web-to-App Conversion Banner for Public Shared Profiles */}
      {!isOwnProfile && (
        <div className="mx-3 sm:mx-0 mt-3 mb-2 p-3 rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-accent)]/35 flex flex-wrap items-center justify-between gap-2.5 shadow-sm">
          <div className="flex items-center gap-2.5 min-w-0">
            <div className="p-2 rounded-xl bg-[var(--vybe-accent-soft)] text-[var(--vybe-accent)] shrink-0">
              <Smartphone className="w-4 h-4" />
            </div>
            <div className="min-w-0">
              <p className="text-xs font-bold text-[var(--vybe-text)] truncate">
                @{targetUser.username} on VYBE
              </p>
              <p className="text-[11px] text-[var(--vybe-text-secondary)]">
                Open in the VYBE Android app or browse directly on web.
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2 shrink-0">
            <a
              href={androidAppDeepLink}
              className="px-3 py-1.5 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-[11px] font-bold inline-flex items-center gap-1 hover:opacity-95"
            >
              <span>Open in VYBE</span>
              <ExternalLink className="w-3 h-3" />
            </a>
            <a
              href="/download"
              className="px-3 py-1.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[var(--vybe-text)] text-[11px] font-semibold hover:border-[var(--vybe-accent)]"
            >
              Download VYBE
            </a>
          </div>
        </div>
      )}

      {/* Cover Banner */}
      <div className="h-44 sm:h-52 w-full relative bg-gradient-to-br from-[#0F172A] via-[#311B92] to-[#0284C7] overflow-hidden sm:rounded-b-3xl border-b border-[var(--vybe-border)]">
        {targetUser.coverUrl ? (
          <OptimizedImage
            src={targetUser.coverUrl}
            alt="Profile cover"
            aspectRatio="16 / 9"
            eager
            className="w-full h-full object-cover"
          />
        ) : null}
        <div className="absolute inset-0 bg-gradient-to-t from-[var(--vybe-bg)] via-transparent to-black/30" />

        {!isOwnProfile && (
          <button
            type="button"
            onClick={closeUserProfile}
            aria-label="Back from user profile"
            className="absolute top-3.5 left-4 min-w-[44px] min-h-[44px] px-3 rounded-xl bg-black/55 backdrop-blur-md border border-white/15 text-white keep-white hover:bg-black/75 flex items-center gap-1.5 text-xs font-bold shadow-lg transition-colors z-20"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>Back</span>
          </button>
        )}

        <div className="absolute top-3.5 right-4 flex items-center gap-2 z-20">
          <button
            type="button"
            onClick={handleShareProfile}
            title="Share Profile Link"
            aria-label="Share Profile Link"
            className="px-3 py-2 rounded-xl bg-black/55 backdrop-blur-md border border-white/15 text-white keep-white hover:bg-black/75 transition-colors inline-flex items-center gap-1.5 text-xs font-bold"
          >
            {linkCopied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Share2 className="w-3.5 h-3.5" />}
            <span>{linkCopied ? 'Copied' : 'Share'}</span>
          </button>
          {isOwnProfile && (
            <>
              <button
                type="button"
                onClick={() => openModal('QR_CODE')}
                title="Share Profile QR"
                aria-label="Share Profile QR"
                className="p-2.5 rounded-xl bg-black/55 backdrop-blur-md border border-white/15 text-white keep-white hover:bg-black/75 transition-colors"
              >
                <QrCode className="w-4 h-4" />
              </button>
              <button
                type="button"
                onClick={() => openModal('SETTINGS')}
                title="Settings & Preferences"
                aria-label="Settings & Preferences"
                className="p-2.5 rounded-xl bg-black/55 backdrop-blur-md border border-white/15 text-white keep-white hover:bg-black/75 transition-colors"
              >
                <Settings className="w-4 h-4" />
              </button>
            </>
          )}
        </div>
      </div>

      {/* Profile Details Container */}
      <div className="px-4 sm:px-6 -mt-14 relative space-y-4">
        <div className="flex items-end justify-between gap-3 flex-wrap">
          <VybeAvatar
            src={targetUser.avatarUrl}
            name={targetUser.displayName}
            username={targetUser.username}
            isOnline={targetUser.isOnline}
            eager
            className="w-24 h-24 rounded-full border-4 border-[var(--vybe-bg)] shadow-lg"
            textClassName="text-2xl"
          />

          {isOwnProfile ? (
            <div className="flex items-center gap-2 flex-wrap">
              <button
                type="button"
                onClick={openEditDrawer}
                className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-full bg-[var(--vybe-surface)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] text-xs font-semibold text-[var(--vybe-text)] transition-colors"
              >
                <Edit3 className="w-3.5 h-3.5 text-[var(--vybe-accent)]" />
                <span>Edit Profile</span>
              </button>
              <button
                type="button"
                onClick={() => openModal('VERIFICATION_CENTER')}
                className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-full bg-[var(--vybe-surface)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] text-xs font-semibold text-[var(--vybe-text)] transition-colors"
              >
                <ShieldCheck className="w-3.5 h-3.5 text-[var(--vybe-accent)]" />
                <span>Verification</span>
              </button>
              <button
                type="button"
                onClick={() => openModal('SETTINGS')}
                className="px-4 py-2 rounded-full bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold hover:opacity-95 transition-opacity"
              >
                Preferences
              </button>
            </div>
          ) : (
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => toggleFollowUser(targetUser.id)}
                className={`px-4 py-2 rounded-full text-xs font-bold flex items-center gap-1.5 transition-all ${
                  isFollowingPeer
                    ? 'bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-[var(--vybe-text)]'
                    : isFollowRequested
                      ? 'bg-amber-500/15 border border-amber-500/40 text-amber-500'
                      : 'bg-[var(--vybe-accent)] text-white keep-white'
                }`}
              >
                {isFollowingPeer ? (
                  <Check className="w-3.5 h-3.5" />
                ) : isFollowRequested ? (
                  <Clock className="w-3.5 h-3.5" />
                ) : (
                  <UserPlus className="w-3.5 h-3.5" />
                )}
                <span>{isFollowingPeer ? 'Following' : isFollowRequested ? 'Requested' : 'Follow'}</span>
              </button>
              <button
                type="button"
                onClick={handleMessagePeer}
                className="px-4 py-2 rounded-full bg-[var(--vybe-surface)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] text-[var(--vybe-text)] text-xs font-bold flex items-center gap-1.5 transition-colors"
              >
                <MessageCircle className="w-3.5 h-3.5" />
                <span>Message</span>
              </button>
            </div>
          )}
        </div>

        {messageError && (
          <div className="p-3 rounded-2xl bg-red-500/10 border border-red-500/30 text-red-500 text-xs flex items-center justify-between gap-2">
            <span>{messageError}</span>
            <button type="button" onClick={() => setMessageError(null)} className="font-bold hover:underline">
              Dismiss
            </button>
          </div>
        )}

        <div>
          <div className="flex items-center gap-2 flex-wrap">
            <h1 className="text-xl font-extrabold text-[var(--vybe-text)]">{targetUser.displayName}</h1>
            {targetUser.badges?.map(b => (
              <OfficialBadge key={b} badge={b} size="md" />
            ))}
            {targetUser.isPrivate && (
              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[10px] font-semibold text-[var(--vybe-text-secondary)]">
                <Lock className="w-3 h-3" />
                <span>Private</span>
              </span>
            )}
          </div>
          <p className="text-xs text-[var(--vybe-text-secondary)] font-mono mt-0.5">
            @{targetUser.username} · {targetUser.publicVybeId}
          </p>
        </div>

        {/* Live Pulse Pill */}
        <div ref={pulseMenuRef} className="relative inline-block">
          <button
            type="button"
            disabled={!isOwnProfile}
            onClick={() => isOwnProfile && setShowPulseMenu(!showPulseMenu)}
            className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-xs text-[var(--vybe-text-secondary)] hover:border-[var(--vybe-accent)] disabled:hover:border-[var(--vybe-border)] transition-colors"
          >
            <span className="w-2 h-2 rounded-full bg-[#22C55E]" />
            <span>Pulse:</span>
            <strong className="text-[var(--vybe-text)]">{targetUser.livePulse}</strong>
          </button>

          {isOwnProfile && showPulseMenu && (
            <div className="absolute left-0 mt-2 w-60 bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-2xl shadow-2xl p-2 z-40">
              <p className="text-[11px] font-bold text-[var(--vybe-text-secondary)] px-2.5 py-1.5">
                Set your live activity
              </p>
              {pulseOptions.map(p => (
                <button
                  key={p}
                  type="button"
                  onClick={() => {
                    setCurrentUserPulse(p);
                    setShowPulseMenu(false);
                  }}
                  className={`w-full text-left px-2.5 py-1.5 rounded-xl text-xs transition-colors ${
                    targetUser.livePulse === p
                      ? 'bg-[var(--vybe-accent)] text-white keep-white font-bold'
                      : 'text-[var(--vybe-text)] hover:bg-[var(--vybe-elevated)]'
                  }`}
                >
                  {p}
                </button>
              ))}
            </div>
          )}
        </div>

        {targetUser.bio && (
          <p className="text-xs text-[var(--vybe-text-secondary)] leading-relaxed max-w-lg">{targetUser.bio}</p>
        )}

        <div className="flex items-center gap-4 text-xs text-[var(--vybe-text-muted)] flex-wrap">
          {targetUser.location && (
            <span className="flex items-center gap-1">
              <MapPin className="w-3.5 h-3.5 text-[var(--vybe-accent)]" />
              <span>{targetUser.location}</span>
            </span>
          )}
          <span className="flex items-center gap-1">
            <Calendar className="w-3.5 h-3.5" />
            <span>Joined {targetUser.joinedDate}</span>
          </span>
        </div>

        {/* Stats Row */}
        <div className="grid grid-cols-3 gap-2 py-3.5 border-y border-[var(--vybe-border)] text-center bg-[var(--vybe-surface)] rounded-2xl border px-2">
          <div>
            <span className="block text-base font-bold text-[var(--vybe-text)]">
              {isPrivateLocked ? targetUser.postsCount : userPosts.length || targetUser.postsCount}
            </span>
            <span className="text-[11px] text-[var(--vybe-text-secondary)]">Posts</span>
          </div>
          <div>
            <span className="block text-base font-bold text-[var(--vybe-text)]">
              {targetUser.followersCount.toLocaleString()}
            </span>
            <span className="text-[11px] text-[var(--vybe-text-secondary)]">Followers</span>
          </div>
          <div>
            <span className="block text-base font-bold text-[var(--vybe-text)]">
              {targetUser.followingCount.toLocaleString()}
            </span>
            <span className="text-[11px] text-[var(--vybe-text-secondary)]">Following</span>
          </div>
        </div>

        {isPrivateLocked ? (
          <div className="p-8 rounded-3xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-center space-y-2.5">
            <Lock className="w-8 h-8 text-[var(--vybe-accent)] mx-auto" />
            <h2 className="text-sm font-bold text-[var(--vybe-text)]">This account is private</h2>
            <p className="text-xs text-[var(--vybe-text-secondary)] max-w-xs mx-auto">
              Follow @{targetUser.username} to see their photos, reels, and updates once approved.
            </p>
          </div>
        ) : (
          <>
            <div className="flex items-center justify-around border-b border-[var(--vybe-border)]">
              {[
                { id: 'POSTS', label: 'Posts', icon: Grid },
                { id: 'REELS', label: 'Reels', icon: PlaySquare },
                ...(isOwnProfile ? [{ id: 'SAVED', label: 'Saved', icon: Bookmark }] : [])
              ].map(tab => {
                const Icon = tab.icon;
                const isSelected = activeTab === tab.id;
                return (
                  <button
                    key={tab.id}
                    type="button"
                    onClick={() => setActiveTab(tab.id as any)}
                    className={`flex-1 flex items-center justify-center gap-2 py-3 border-b-2 text-xs font-bold transition-colors ${
                      isSelected
                        ? 'border-[var(--vybe-accent)] text-[var(--vybe-accent)]'
                        : 'border-transparent text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)]'
                    }`}
                  >
                    <Icon className="w-4 h-4" />
                    <span>{tab.label}</span>
                  </button>
                );
              })}
            </div>

            {activeTab === 'POSTS' && (
              userPosts.length === 0 ? (
                <VybeEmptyState
                  icon={Grid}
                  title="No posts yet"
                  subtitle={isOwnProfile ? 'Share your first photo, story, or mood post.' : `@${targetUser.username} hasn't posted yet.`}
                  actionLabel={isOwnProfile ? 'Create Post' : undefined}
                  onAction={isOwnProfile ? () => openModal('CREATE_POST') : undefined}
                />
              ) : (
                <div className="grid grid-cols-3 gap-1.5 pt-1">
                  {userPosts.map(p => (
                    <div
                      key={p.id}
                      onClick={() => openSharedPostModal(p.id)}
                      className="aspect-square bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-xl overflow-hidden relative group cursor-pointer"
                    >
                      {p.mediaUrls[0] ? (
                        <OptimizedImage
                          src={p.mediaUrls[0]}
                          alt={p.caption || 'Post'}
                          aspectRatio="1 / 1"
                          className="w-full h-full object-cover group-hover:scale-105 transition-transform"
                        />
                      ) : (
                        <div className="w-full h-full p-2.5 flex items-center justify-center text-center text-[11px] text-[var(--vybe-text-secondary)] line-clamp-4">
                          {p.caption || 'Text post'}
                        </div>
                      )}
                      <div className="absolute bottom-1.5 right-1.5 rounded-full bg-black/55 backdrop-blur-md px-1">
                        <FingerHeartReaction
                          isLiked={p.isLiked}
                          count={p.likesCount}
                          size={15}
                          forceDarkOverlay={true}
                          onToggle={() => toggleLikePost(p.id)}
                          className="h-6 px-1"
                        />
                      </div>
                    </div>
                  ))}
                </div>
              )
            )}

            {activeTab === 'REELS' && (
              visibleReels.length === 0 ? (
                <VybeEmptyState
                  icon={PlaySquare}
                  title="No reels yet"
                  subtitle="Short-form VYBES reels will appear here once published."
                />
              ) : (
                <div className="grid grid-cols-3 gap-1.5 pt-1">
                  {visibleReels.map(r => (
                    <div
                      key={r.id}
                      onClick={() => openSharedPostModal(r.id)}
                      className="aspect-[9/16] bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-xl overflow-hidden relative group cursor-pointer"
                    >
                      <OptimizedImage
                        src={r.mediaUrls[0] || ''}
                        alt={r.caption || 'Reel'}
                        aspectRatio="9 / 16"
                        className="w-full h-full object-cover group-hover:scale-105 transition-transform"
                      />
                      <div className="absolute bottom-1.5 left-1.5 text-[10px] font-bold text-white keep-white bg-black/60 px-1.5 py-0.5 rounded">
                        {r.viewsCount}
                      </div>
                    </div>
                  ))}
                </div>
              )
            )}

            {isOwnProfile && activeTab === 'SAVED' && (
              savedPosts.length === 0 ? (
                <VybeEmptyState
                  icon={Bookmark}
                  title="No saved posts"
                  subtitle="Bookmark posts in the feed to revisit them here."
                />
              ) : (
                <div className="grid grid-cols-3 gap-1.5 pt-1">
                  {savedPosts.map(s => (
                    <div
                      key={s.id}
                      onClick={() => openSharedPostModal(s.id)}
                      className="aspect-square bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-xl overflow-hidden relative group cursor-pointer"
                    >
                      {s.mediaUrls[0] ? (
                        <OptimizedImage
                          src={s.mediaUrls[0]}
                          alt={s.caption || 'Saved post'}
                          aspectRatio="1 / 1"
                          className="w-full h-full object-cover group-hover:scale-105 transition-transform"
                        />
                      ) : (
                        <div className="w-full h-full p-2.5 flex items-center justify-center text-center text-[11px] text-[var(--vybe-text-secondary)] line-clamp-4">
                          {s.caption || 'Saved post'}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )
            )}
          </>
        )}
      </div>

      {/* Edit Own Profile Modal */}
      {isOwnProfile && showEditProfile && (
        <div
          className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4"
          onClick={() => setShowEditProfile(false)}
        >
          <form
            onSubmit={handleSaveProfileEdit}
            onClick={(e) => e.stopPropagation()}
            className="w-full max-w-md bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-3xl p-6 space-y-4 shadow-2xl"
          >
            <h3 className="text-base font-bold text-[var(--vybe-text)]">Edit Profile</h3>
            {editError && (
              <div className="p-2.5 rounded-xl bg-red-500/10 border border-red-500/30 text-red-500 text-xs">
                {editError}
              </div>
            )}
            <div>
              <label className="text-[11px] font-semibold text-[var(--vybe-text-secondary)] block mb-1">
                Display Name
              </label>
              <input
                type="text"
                value={editDisplayName}
                onChange={(e) => setEditDisplayName(e.target.value)}
                required
                className="w-full px-3.5 py-2.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs text-[var(--vybe-text)]"
              />
            </div>
            <div>
              <label className="text-[11px] font-semibold text-[var(--vybe-text-secondary)] block mb-1">
                Bio
              </label>
              <textarea
                rows={3}
                value={editBio}
                onChange={(e) => setEditBio(e.target.value)}
                className="w-full p-3 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs text-[var(--vybe-text)] resize-none"
              />
            </div>
            <label className="flex items-center justify-between p-3 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] cursor-pointer">
              <div>
                <span className="text-xs font-bold text-[var(--vybe-text)] block">Private Account</span>
                <span className="text-[11px] text-[var(--vybe-text-secondary)]">
                  Only approved followers see your posts and reels
                </span>
              </div>
              <input
                type="checkbox"
                checked={editPrivate}
                onChange={(e) => setEditPrivate(e.target.checked)}
                className="accent-[var(--vybe-accent)] w-4 h-4"
              />
            </label>
            <div className="flex items-center justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setShowEditProfile(false)}
                className="px-4 py-2 rounded-xl bg-[var(--vybe-elevated)] text-xs font-semibold text-[var(--vybe-text)]"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={isSavingProfile}
                className="px-5 py-2 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold disabled:opacity-50"
              >
                {isSavingProfile ? 'Saving...' : 'Save Changes'}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};
