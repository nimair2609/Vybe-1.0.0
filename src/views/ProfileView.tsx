import React, { useState, useEffect, useRef } from 'react';
import {
  Settings, QrCode, Grid, Bookmark, PlaySquare,
  MapPin, Calendar, ArrowLeft, MessageCircle, UserPlus, Check, ShieldCheck
} from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { OfficialBadge } from '../components/Badges';
import { FingerHeartReaction } from '../components/FingerHeartReaction';
import { VybeEmptyState } from '../components/VybeDesignSystem';
import { OptimizedImage } from '../components/OptimizedImage';
import { LivePulseStatus } from '../types';

export const ProfileView: React.FC = () => {
  const {
    currentUser,
    viewedProfileUser,
    closeUserProfile,
    posts,
    reels,
    conversations,
    selectConversation,
    setSelectedTab,
    openModal,
    openSharedPostModal,
    toggleLikePost,
    setCurrentUserPulse
  } = useVybe();

  const [activeTab, setActiveTab] = useState<'POSTS' | 'REELS' | 'SAVED'>('POSTS');
  const [showPulseMenu, setShowPulseMenu] = useState(false);
  const [followedPeers, setFollowedPeers] = useState<Record<string, boolean>>({});
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

  const targetUser = viewedProfileUser || currentUser;
  const isOwnProfile = targetUser.id.toLowerCase() === currentUser.id.toLowerCase();
  const isFollowingPeer = Boolean(followedPeers[targetUser.id] ?? targetUser.isFollowedByMe);

  const userPosts = posts.filter(p => p.author.id.toLowerCase() === targetUser.id.toLowerCase());
  const userReels = reels.filter(r => r.author.id.toLowerCase() === targetUser.id.toLowerCase());
  const savedPosts = posts.filter(p => p.isSaved);
  const visibleReels = userReels.length > 0 ? userReels : isOwnProfile ? reels : [];

  const pulseOptions: LivePulseStatus[] = [
    'Open to chatting',
    'Feeling creative',
    'Listening to music',
    'Looking for gaming partner',
    'Taking quiet time',
    'None'
  ];

  const handleMessagePeer = () => {
    const existingConv = conversations.find(
      c => c.peerUser.id.toLowerCase() === targetUser.id.toLowerCase()
    );
    if (existingConv) {
      selectConversation(existingConv);
    }
    setSelectedTab('INBOX');
  };

  return (
    <div className="w-full max-w-3xl mx-auto pb-28 lg:pb-12">
      {/* Cover Banner */}
      <div className="h-44 sm:h-52 w-full relative bg-[var(--vybe-elevated)] overflow-hidden sm:rounded-b-3xl border-b border-[var(--vybe-border)]">
        <OptimizedImage
          src={
            targetUser.coverUrl ||
            'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=1200&q=80'
          }
          alt="Cover"
          aspectRatio="16 / 9"
          eager
          className="w-full h-full object-cover"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-[var(--vybe-bg)] via-transparent to-black/30" />

        {/* Back button */}
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

        {/* Top Header Utility Buttons for Own Profile */}
        {isOwnProfile && (
          <div className="absolute top-3.5 right-4 flex items-center gap-2 z-20">
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
          </div>
        )}
      </div>

      {/* Profile Details Container */}
      <div className="px-4 sm:px-6 -mt-14 relative space-y-4">
        {/* Avatar & Action Row */}
        <div className="flex items-end justify-between gap-3 flex-wrap">
          <div className="relative">
            <OptimizedImage
              src={targetUser.avatarUrl}
              alt={targetUser.displayName}
              width={96}
              height={96}
              eager
              className="w-24 h-24 rounded-full object-cover border-4 border-[var(--vybe-bg)] shadow-lg"
            />
            {targetUser.isOnline && (
              <span className="absolute bottom-1 right-1 w-4 h-4 rounded-full bg-[#22C55E] border-2 border-[var(--vybe-bg)]" />
            )}
          </div>

          {isOwnProfile ? (
            <div className="flex items-center gap-2">
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
                onClick={() =>
                  setFollowedPeers(prev => ({ ...prev, [targetUser.id]: !isFollowingPeer }))
                }
                className={`px-4 py-2 rounded-full text-xs font-bold flex items-center gap-1.5 transition-all ${
                  isFollowingPeer
                    ? 'bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-[var(--vybe-text)]'
                    : 'bg-[var(--vybe-accent)] text-white keep-white'
                }`}
              >
                {isFollowingPeer ? <Check className="w-3.5 h-3.5" /> : <UserPlus className="w-3.5 h-3.5" />}
                <span>{isFollowingPeer ? 'Following' : 'Follow'}</span>
              </button>
              <button
                type="button"
                onClick={handleMessagePeer}
                className="px-4 py-2 rounded-full bg-[var(--vybe-surface)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] text-xs font-bold text-[var(--vybe-text)] flex items-center gap-1.5 transition-colors"
              >
                <MessageCircle className="w-3.5 h-3.5 text-[var(--vybe-accent)]" />
                <span>Message</span>
              </button>
            </div>
          )}
        </div>

        {/* Identity & Badges */}
        <div>
          <div className="flex items-center gap-1.5 flex-wrap">
            <h1 className="text-xl font-extrabold text-[var(--vybe-text)] brand-font">{targetUser.displayName}</h1>
            {targetUser.badges?.map(b => (
              <OfficialBadge key={b} badge={b} size="md" />
            ))}
          </div>
          <div className="flex items-center gap-2 text-xs text-[var(--vybe-text-secondary)] mt-0.5">
            <span>@{targetUser.username}</span>
            <span>·</span>
            <span className="text-[var(--vybe-accent)] font-semibold">{targetUser.publicVybeId}</span>
          </div>
        </div>

        {/* Live Pulse Selector */}
        <div className="relative inline-block" ref={pulseMenuRef}>
          <button
            type="button"
            onClick={() => isOwnProfile && setShowPulseMenu(!showPulseMenu)}
            className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-xs text-[var(--vybe-text-secondary)] hover:border-[var(--vybe-accent)] transition-colors"
          >
            <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
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

        {/* Bio */}
        {targetUser.bio && (
          <p className="text-xs text-[var(--vybe-text-secondary)] leading-relaxed max-w-lg">{targetUser.bio}</p>
        )}

        {/* Meta details */}
        <div className="flex items-center gap-4 text-xs text-[var(--vybe-text-muted)] flex-wrap">
          {targetUser.location && (
            <span className="flex items-center gap-1">
              <MapPin className="w-3.5 h-3.5 text-[var(--vybe-accent)]" />
              <span>{targetUser.location}</span>
            </span>
          )}
          <span className="flex items-center gap-1">
            <Calendar className="w-3.5 h-3.5" />
            <span>{targetUser.joinedDate}</span>
          </span>
        </div>

        {/* Stats Row */}
        <div className="grid grid-cols-3 gap-2 py-3.5 border-y border-[var(--vybe-border)] text-center bg-[var(--vybe-surface)] rounded-2xl border px-2">
          <div>
            <span className="block text-base font-bold text-[var(--vybe-text)]">{userPosts.length}</span>
            <span className="text-[11px] text-[var(--vybe-text-secondary)]">Posts</span>
          </div>
          <div>
            <span className="block text-base font-bold text-[var(--vybe-text)]">
              {(targetUser.followersCount + (isFollowingPeer && !targetUser.isFollowedByMe ? 1 : 0)).toLocaleString()}
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

        {/* Tabs */}
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

        {/* Tab Content Media Grid */}
        {activeTab === 'POSTS' && (
          userPosts.length === 0 ? (
            <VybeEmptyState
              icon={Grid}
              title="No posts yet"
              subtitle={isOwnProfile ? 'Share your first photo, story, or mood post.' : `${targetUser.displayName} hasn't posted yet.`}
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
                  <OptimizedImage
                    src={
                      p.mediaUrls[0] ||
                      'https://images.unsplash.com/photo-1514565131-fce0801e5785?auto=format&fit=crop&w=400&q=80'
                    }
                    alt="Post"
                    aspectRatio="1 / 1"
                    className="w-full h-full object-cover group-hover:scale-105 transition-transform"
                  />
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
                  <img
                    src={r.mediaUrls[0]}
                    alt="Reel"
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
                  <img
                    src={
                      s.mediaUrls[0] ||
                      'https://images.unsplash.com/photo-1506126613408-eca07ce68773?auto=format&fit=crop&w=400&q=80'
                    }
                    alt="Saved"
                    className="w-full h-full object-cover group-hover:scale-105 transition-transform"
                  />
                </div>
              ))}
            </div>
          )
        )}
      </div>
    </div>
  );
};
