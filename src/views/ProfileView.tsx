import React, { useState } from 'react';
import {
  Settings, QrCode, Grid, Bookmark, PlaySquare,
  MapPin, Calendar, ArrowLeft, MessageCircle, UserPlus, Check
} from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { OfficialBadge } from '../components/Badges';
import { FingerHeartReaction } from '../components/FingerHeartReaction';
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

  const targetUser = viewedProfileUser || currentUser;
  const isOwnProfile = targetUser.id.toLowerCase() === currentUser.id.toLowerCase();
  const isFollowingPeer = Boolean(followedPeers[targetUser.id] ?? targetUser.isFollowedByMe);

  const userPosts = posts.filter(p => p.author.id.toLowerCase() === targetUser.id.toLowerCase());
  const userReels = reels.filter(r => r.author.id.toLowerCase() === targetUser.id.toLowerCase());
  const savedPosts = posts.filter(p => p.isSaved);

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
    <div className="w-full max-w-2xl mx-auto pb-28">
      {/* Cover Banner */}
      <div className="h-44 w-full relative bg-[#101322]">
        <img
          src={
            targetUser.coverUrl ||
            'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=1200&q=80'
          }
          alt="Cover"
          className="w-full h-full object-cover"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-[#060714] via-transparent to-transparent" />

        {/* Working Back Button when viewing another user's profile */}
        {!isOwnProfile && (
          <button
            type="button"
            onClick={closeUserProfile}
            aria-label="Back from user profile"
            className="absolute top-3.5 left-4 min-w-[44px] min-h-[44px] px-3 rounded-xl bg-black/60 backdrop-blur-md border border-white/15 text-white keep-white hover:bg-black/80 flex items-center gap-1.5 text-xs font-bold shadow-lg transition-colors z-20"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>Back</span>
          </button>
        )}

        {/* Top Header Buttons for Own Profile */}
        {isOwnProfile && (
          <div className="absolute top-4 right-4 flex items-center gap-2 z-20">
            <button
              type="button"
              onClick={() => openModal('QR_CODE')}
              title="Share Profile QR"
              className="p-2.5 rounded-xl bg-black/50 backdrop-blur-md border border-white/10 text-white keep-white hover:bg-black/70 transition-colors"
            >
              <QrCode className="w-4 h-4" />
            </button>
            <button
              type="button"
              onClick={() => openModal('SETTINGS')}
              title="Settings & Software Updates"
              className="p-2.5 rounded-xl bg-black/50 backdrop-blur-md border border-white/10 text-white keep-white hover:bg-black/70 transition-colors"
            >
              <Settings className="w-4 h-4" />
            </button>
          </div>
        )}
      </div>

      {/* Profile Details Container */}
      <div className="px-5 -mt-16 relative space-y-4">
        {/* Avatar & Action Row */}
        <div className="flex items-end justify-between gap-3">
          <div className="relative">
            <img
              src={targetUser.avatarUrl}
              alt={targetUser.displayName}
              className="w-24 h-24 rounded-full object-cover border-4 border-[#060714] shadow-2xl"
            />
            {targetUser.isOnline && (
              <span className="absolute bottom-1 right-1 w-4 h-4 rounded-full bg-[#22C55E] border-2 border-[#060714]" />
            )}
          </div>

          {isOwnProfile ? (
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => openModal('VERIFICATION_CENTER')}
                className="px-3 py-2 rounded-xl bg-[#101322] border border-[#262E4A] hover:border-[#00C6FF]/40 text-xs font-semibold text-gray-200 transition-colors"
              >
                Get Verified
              </button>
              <button
                type="button"
                onClick={() => openModal('SETTINGS')}
                className="px-3.5 py-2 rounded-xl bg-gradient-to-r from-[#00C6FF] to-[#8B5CF6] text-white keep-white text-xs font-bold shadow hover:opacity-95 transition-all"
              >
                Edit Profile
              </button>
            </div>
          ) : (
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() =>
                  setFollowedPeers(prev => ({ ...prev, [targetUser.id]: !isFollowingPeer }))
                }
                className={`px-3.5 py-2 rounded-xl text-xs font-bold flex items-center gap-1.5 transition-all ${
                  isFollowingPeer
                    ? 'bg-[#101322] border border-[#262E4A] text-white'
                    : 'bg-gradient-to-r from-[#00C6FF] to-[#8B5CF6] text-white keep-white shadow'
                }`}
              >
                {isFollowingPeer ? <Check className="w-3.5 h-3.5" /> : <UserPlus className="w-3.5 h-3.5" />}
                <span>{isFollowingPeer ? 'Following' : 'Follow'}</span>
              </button>
              <button
                type="button"
                onClick={handleMessagePeer}
                className="px-3.5 py-2 rounded-xl bg-[#101322] border border-[#262E4A] hover:border-[#00C6FF]/50 text-xs font-bold text-white flex items-center gap-1.5 transition-colors"
              >
                <MessageCircle className="w-3.5 h-3.5 text-[#00C6FF]" />
                <span>Message</span>
              </button>
            </div>
          )}
        </div>

        {/* Identity & Badges */}
        <div>
          <div className="flex items-center gap-1.5 flex-wrap">
            <h1 className="text-xl font-bold text-white brand-font">{targetUser.displayName}</h1>
            {targetUser.badges?.map(b => (
              <OfficialBadge key={b} badge={b} size="md" />
            ))}
          </div>
          <div className="flex items-center gap-2 text-xs text-gray-400 font-mono mt-0.5">
            <span>@{targetUser.username}</span>
            <span>•</span>
            <span className="text-[#00C6FF] font-semibold">{targetUser.publicVybeId}</span>
          </div>
        </div>

        {/* Live Pulse Selector */}
        <div className="relative">
          <button
            type="button"
            onClick={() => isOwnProfile && setShowPulseMenu(!showPulseMenu)}
            className="flex items-center gap-2 px-3 py-1 rounded-full bg-[#101322] border border-[#262E4A] text-xs text-gray-300 hover:border-[#8B5CF6]/50 transition-colors"
          >
            <span className="w-2 h-2 rounded-full bg-[#22C55E] animate-ping" />
            <span>Pulse: </span>
            <strong className="text-white">{targetUser.livePulse}</strong>
          </button>

          {isOwnProfile && showPulseMenu && (
            <div className="absolute left-0 mt-2 w-56 bg-[#101322] border border-[#262E4A] rounded-2xl shadow-2xl p-2 z-40">
              <p className="text-[11px] font-bold text-gray-300 px-2 py-1">Set your live activity</p>
              {pulseOptions.map(p => (
                <button
                  key={p}
                  type="button"
                  onClick={() => {
                    setCurrentUserPulse(p);
                    setShowPulseMenu(false);
                  }}
                  className={`w-full text-left px-2.5 py-1.5 rounded-lg text-xs ${
                    targetUser.livePulse === p
                      ? 'bg-[#8B5CF6] text-white keep-white font-bold'
                      : 'text-gray-300 hover:bg-[#1A1F36]'
                  }`}
                >
                  {p}
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Bio */}
        <p className="text-xs text-gray-200 leading-relaxed max-w-lg">{targetUser.bio}</p>

        {/* Meta details */}
        <div className="flex items-center gap-4 text-xs text-gray-400">
          {targetUser.location && (
            <span className="flex items-center gap-1">
              <MapPin className="w-3.5 h-3.5 text-[#00C6FF]" />
              <span>{targetUser.location}</span>
            </span>
          )}
          <span className="flex items-center gap-1">
            <Calendar className="w-3.5 h-3.5 text-gray-500" />
            <span>{targetUser.joinedDate}</span>
          </span>
        </div>

        {/* Stats Row */}
        <div className="grid grid-cols-3 gap-2 py-3 border-y border-[#262E4A]/60 text-center">
          <div>
            <span className="block text-base font-bold text-white">{userPosts.length}</span>
            <span className="text-[11px] text-gray-400">Posts</span>
          </div>
          <div>
            <span className="block text-base font-bold text-white">
              {(targetUser.followersCount + (isFollowingPeer && !targetUser.isFollowedByMe ? 1 : 0)).toLocaleString()}
            </span>
            <span className="text-[11px] text-gray-400">Followers</span>
          </div>
          <div>
            <span className="block text-base font-bold text-white">
              {targetUser.followingCount.toLocaleString()}
            </span>
            <span className="text-[11px] text-gray-400">Following</span>
          </div>
        </div>

        {/* Tabs: Posts, Reels, Saved (Saved only on Own Profile) */}
        <div className="flex items-center justify-around border-b border-[#262E4A]/60">
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
                className={`flex items-center gap-2 py-3 border-b-2 text-xs font-bold transition-all ${
                  isSelected
                    ? 'border-[#00C6FF] text-[#00C6FF]'
                    : 'border-transparent text-gray-400 hover:text-gray-200'
                }`}
              >
                <Icon className="w-4 h-4" />
                <span>{tab.label}</span>
              </button>
            );
          })}
        </div>

        {/* Tab Content Media Grid with Finger Heart Reactions */}
        <div className="grid grid-cols-3 gap-1.5 pt-2">
          {activeTab === 'POSTS' &&
            userPosts.map(p => (
              <div
                key={p.id}
                onClick={() => openSharedPostModal(p.id)}
                className="aspect-square bg-[#101322] border border-[#262E4A] rounded-xl overflow-hidden relative group cursor-pointer"
              >
                <img
                  src={
                    p.mediaUrls[0] ||
                    'https://images.unsplash.com/photo-1514565131-fce0801e5785?auto=format&fit=crop&w=400&q=80'
                  }
                  alt="Post"
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

          {activeTab === 'REELS' &&
            (userReels.length > 0 ? userReels : isOwnProfile ? reels : []).map(r => (
              <div
                key={r.id}
                onClick={() => openSharedPostModal(r.id)}
                className="aspect-[9/16] bg-[#101322] border border-[#262E4A] rounded-xl overflow-hidden relative group cursor-pointer"
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

          {isOwnProfile &&
            activeTab === 'SAVED' &&
            savedPosts.map(s => (
              <div
                key={s.id}
                onClick={() => openSharedPostModal(s.id)}
                className="aspect-square bg-[#101322] border border-[#262E4A] rounded-xl overflow-hidden relative group cursor-pointer"
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
      </div>
    </div>
  );
};
