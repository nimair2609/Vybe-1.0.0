import React, { useState } from 'react';
import { 
  Settings, QrCode, Grid, Bookmark, PlaySquare, 
  MapPin, Calendar, Edit3, Shield, Sparkles 
} from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { OfficialBadge } from '../components/Badges';
import { LivePulseStatus } from '../types';

export const ProfileView: React.FC = () => {
  const { currentUser, posts, reels, openModal, setCurrentUserPulse } = useVybe();
  const [activeTab, setActiveTab] = useState<'POSTS' | 'REELS' | 'SAVED'>('POSTS');
  const [showPulseMenu, setShowPulseMenu] = useState(false);

  const userPosts = posts.filter(p => p.author.id === currentUser.id);
  const savedPosts = posts.filter(p => p.isSaved);

  const pulseOptions: LivePulseStatus[] = [
    'Open to chatting',
    'Feeling creative',
    'Listening to music',
    'Looking for gaming partner',
    'Taking quiet time',
    'None'
  ];

  return (
    <div className="w-full max-w-2xl mx-auto pb-28">
      
      {/* Cover Banner */}
      <div className="h-44 w-full relative bg-[#101322]">
        <img 
          src={currentUser.coverUrl} 
          alt="Cover" 
          className="w-full h-full object-cover"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-[#060714] via-transparent to-transparent" />
        
        {/* Top Header Buttons */}
        <div className="absolute top-4 right-4 flex items-center gap-2">
          <button
            onClick={() => openModal('QR_CODE')}
            title="Share Profile QR"
            className="p-2 rounded-xl bg-black/50 backdrop-blur-md border border-white/10 text-white hover:bg-black/70 transition-colors"
          >
            <QrCode className="w-4 h-4" />
          </button>
          <button
            onClick={() => openModal('SETTINGS')}
            title="Settings & Software Updates"
            className="p-2 rounded-xl bg-black/50 backdrop-blur-md border border-white/10 text-white hover:bg-black/70 transition-colors"
          >
            <Settings className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Profile Details Container */}
      <div className="px-5 -mt-16 relative space-y-4">
        
        {/* Avatar & Action Row */}
        <div className="flex items-end justify-between">
          <div className="relative">
            <img 
              src={currentUser.avatarUrl} 
              alt={currentUser.displayName}
              className="w-24 h-24 rounded-full object-cover border-4 border-[#060714] shadow-2xl"
            />
            {currentUser.isOnline && (
              <span className="absolute bottom-1 right-1 w-4 h-4 rounded-full bg-[#22C55E] border-2 border-[#060714]" />
            )}
          </div>
          
          <div className="flex items-center gap-2">
            <button
              onClick={() => openModal('VERIFICATION_CENTER')}
              className="px-3 py-1.5 rounded-xl bg-[#101322] border border-[#262E4A] hover:border-[#00C6FF]/40 text-xs font-semibold text-gray-200 transition-colors"
            >
              Get Verified
            </button>
            <button
              onClick={() => openModal('SETTINGS')}
              className="px-3.5 py-1.5 rounded-xl bg-gradient-to-r from-[#00C6FF] to-[#8B5CF6] text-white text-xs font-bold shadow hover:opacity-95 transition-all"
            >
              Edit Profile
            </button>
          </div>
        </div>

        {/* Identity & Badges */}
        <div>
          <div className="flex items-center gap-1.5 flex-wrap">
            <h1 className="text-xl font-bold text-white brand-font">{currentUser.displayName}</h1>
            {currentUser.badges?.map(b => (
              <OfficialBadge key={b} badge={b} size="md" />
            ))}
          </div>
          <div className="flex items-center gap-2 text-xs text-gray-400 font-mono mt-0.5">
            <span>@{currentUser.username}</span>
            <span>•</span>
            <span className="text-[#00C6FF] font-semibold">{currentUser.publicVybeId}</span>
          </div>
        </div>

        {/* Live Pulse Selector */}
        <div className="relative">
          <button
            onClick={() => setShowPulseMenu(!showPulseMenu)}
            className="flex items-center gap-2 px-3 py-1 rounded-full bg-[#101322] border border-[#262E4A] text-xs text-gray-300 hover:border-[#8B5CF6]/50 transition-colors"
          >
            <span className="w-2 h-2 rounded-full bg-[#22C55E] animate-ping" />
            <span>Pulse: </span>
            <strong className="text-white">{currentUser.livePulse}</strong>
          </button>

          {showPulseMenu && (
            <div className="absolute left-0 mt-2 w-56 bg-[#101322] border border-[#262E4A] rounded-2xl shadow-2xl p-2 z-40">
              <p className="text-[11px] font-bold text-gray-300 px-2 py-1">Set your live activity</p>
              {pulseOptions.map(p => (
                <button
                  key={p}
                  onClick={() => {
                    setCurrentUserPulse(p);
                    setShowPulseMenu(false);
                  }}
                  className={`w-full text-left px-2.5 py-1.5 rounded-lg text-xs ${
                    currentUser.livePulse === p ? 'bg-[#8B5CF6] text-white font-bold' : 'text-gray-300 hover:bg-[#1A1F36]'
                  }`}
                >
                  {p}
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Bio */}
        <p className="text-xs text-gray-200 leading-relaxed max-w-lg">
          {currentUser.bio}
        </p>

        {/* Meta details */}
        <div className="flex items-center gap-4 text-xs text-gray-400">
          {currentUser.location && (
            <span className="flex items-center gap-1">
              <MapPin className="w-3.5 h-3.5 text-[#00C6FF]" />
              <span>{currentUser.location}</span>
            </span>
          )}
          <span className="flex items-center gap-1">
            <Calendar className="w-3.5 h-3.5 text-gray-500" />
            <span>{currentUser.joinedDate}</span>
          </span>
        </div>

        {/* Stats Row */}
        <div className="grid grid-cols-3 gap-2 py-3 border-y border-[#262E4A]/60 text-center">
          <div>
            <span className="block text-base font-bold text-white">{userPosts.length}</span>
            <span className="text-[11px] text-gray-400">Posts</span>
          </div>
          <div>
            <span className="block text-base font-bold text-white">{currentUser.followersCount.toLocaleString()}</span>
            <span className="text-[11px] text-gray-400">Followers</span>
          </div>
          <div>
            <span className="block text-base font-bold text-white">{currentUser.followingCount.toLocaleString()}</span>
            <span className="text-[11px] text-gray-400">Following</span>
          </div>
        </div>

        {/* Tabs: Posts, Reels, Saved */}
        <div className="flex items-center justify-around border-b border-[#262E4A]/60">
          {[
            { id: 'POSTS', label: 'Posts', icon: Grid },
            { id: 'REELS', label: 'Reels', icon: PlaySquare },
            { id: 'SAVED', label: 'Saved', icon: Bookmark },
          ].map(tab => {
            const Icon = tab.icon;
            const isSelected = activeTab === tab.id;
            return (
              <button
                key={tab.id}
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

        {/* Tab Content Media Grid */}
        <div className="grid grid-cols-3 gap-1.5 pt-2">
          {activeTab === 'POSTS' && userPosts.map(p => (
            <div key={p.id} className="aspect-square bg-[#101322] border border-[#262E4A] rounded-xl overflow-hidden relative group">
              <img 
                src={p.mediaUrls[0] || 'https://images.unsplash.com/photo-1514565131-fce0801e5785?auto=format&fit=crop&w=400&q=80'} 
                alt="Post" 
                className="w-full h-full object-cover group-hover:scale-105 transition-transform"
              />
            </div>
          ))}

          {activeTab === 'REELS' && reels.map(r => (
            <div key={r.id} className="aspect-[9/16] bg-[#101322] border border-[#262E4A] rounded-xl overflow-hidden relative group">
              <img 
                src={r.mediaUrls[0]} 
                alt="Reel" 
                className="w-full h-full object-cover group-hover:scale-105 transition-transform"
              />
              <div className="absolute bottom-1.5 left-1.5 text-[10px] font-bold text-white bg-black/60 px-1.5 py-0.5 rounded">
                {r.viewsCount}
              </div>
            </div>
          ))}

          {activeTab === 'SAVED' && savedPosts.map(s => (
            <div key={s.id} className="aspect-square bg-[#101322] border border-[#262E4A] rounded-xl overflow-hidden relative group">
              <img 
                src={s.mediaUrls[0] || 'https://images.unsplash.com/photo-1506126613408-eca07ce68773?auto=format&fit=crop&w=400&q=80'} 
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
