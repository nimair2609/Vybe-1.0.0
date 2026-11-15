import React, { useState, useMemo, useEffect, useRef } from 'react';
import {
  Search, Sparkles, TrendingUp, Radio, Store,
  Mic, Camera, Compass, Clock, Layers, MapPin, Star,
  Users, X, Loader2, RefreshCw, Check, UserPlus, Play
} from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { OfficialBadge } from '../components/Badges';
import { FingerHeartReaction } from '../components/FingerHeartReaction';
import { VybeAvatar } from '../components/OptimizedImage';
import { VybeEmptyState } from '../components/VybeDesignSystem';
import { filterExploreCommunities } from '../utils/profileValidation';
import { Post, User } from '../types';

type DiscoveryFilter = 'For You' | 'Trending' | 'People' | 'Nearby' | 'Circles' | 'Interests';
type SearchTab = 'All' | 'Posts' | 'People' | 'Circles' | 'Tags';

const DISCOVERY_FILTERS: DiscoveryFilter[] = ['For You', 'Trending', 'People', 'Nearby', 'Circles', 'Interests'];
const SEARCH_TABS: SearchTab[] = ['All', 'Posts', 'People', 'Circles', 'Tags'];
const INDIAN_CITIES = ['All India', 'Mumbai', 'Bengaluru', 'Delhi NCR', 'Goa', 'Hyderabad', 'Kolkata', 'Pune'];
const INTEREST_CATEGORIES = ['Travel', 'Tech', 'Music', 'Fitness', 'Lifestyle', 'Design', 'Food'];

export const ExploreView: React.FC = () => {
  const {
    currentUser,
    allUsers,
    posts,
    communities,
    shops,
    unloadRooms,
    followingIds,
    requestedFollowIds,
    blockedIds,
    dismissedSuggestionIds,
    searchHistory,
    addSearchQueryToHistory,
    removeSearchQueryFromHistory,
    clearSearchHistory,
    searchExploreRemote,
    toggleFollowUser,
    dismissSuggestedUser,
    restoreDismissedSuggestions,
    toggleJoinCommunity,
    openUserProfile,
    openShopDetail,
    openModal,
    setSelectedTab,
    openSharedPostModal,
    toggleLikePost
  } = useVybe();

  const [search, setSearch] = useState('');
  const [selectedFilter, setSelectedFilter] = useState<DiscoveryFilter>('For You');
  const [selectedSearchTab, setSelectedSearchTab] = useState<SearchTab>('All');
  const [unfilteredOnly, setUnfilteredOnly] = useState(false);
  const [selectedCity, setSelectedCity] = useState('All India');
  const [selectedInterest, setSelectedInterest] = useState('Travel');
  const [nearbyPromptVisible, setNearbyPromptVisible] = useState(false);

  const [isSearchingRemote, setIsSearchingRemote] = useState(false);
  const [remoteSearchError, setRemoteSearchError] = useState<string | null>(null);
  const [remoteUsers, setRemoteUsers] = useState<User[]>([]);
  const [remotePosts, setRemotePosts] = useState<Post[]>([]);
  const [searchRetryNonce, setSearchRetryNonce] = useState(0);
  const searchSeqRef = useRef(0);

  const primaryUnloadRoom = unloadRooms[0];
  const isSearching = search.trim().length > 0;
  const cleanQuery = useMemo(() => search.trim().toLowerCase().replace(/^[@#]+/, ''), [search]);

  useEffect(() => {
    const trimmed = search.trim();
    const seq = ++searchSeqRef.current;

    if (trimmed.length < 2) {
      setIsSearchingRemote(false);
      setRemoteSearchError(null);
      setRemoteUsers([]);
      setRemotePosts([]);
      return;
    }

    setIsSearchingRemote(true);
    setRemoteSearchError(null);

    const timer = window.setTimeout(async () => {
      const res = await searchExploreRemote(trimmed);
      if (seq !== searchSeqRef.current) return;
      setIsSearchingRemote(false);
      if (!res.ok && res.error) {
        setRemoteSearchError(res.error);
      } else {
        setRemoteSearchError(null);
      }
      setRemoteUsers(res.users);
      setRemotePosts(res.posts);
    }, 280);

    return () => {
      window.clearTimeout(timer);
    };
  }, [search, searchRetryNonce]);

  const signatureFeatures = [
    {
      id: 'UNLOAD',
      title: 'UNLOAD',
      subtitle: 'Zero-Record Rooms',
      icon: Mic,
      accent: 'from-[#00C6FF] to-[#3B82F6]',
      action: () => {
        if (primaryUnloadRoom) {
          openModal('UNLOAD_ROOM', primaryUnloadRoom);
        }
      }
    },
    {
      id: 'UNFILTERED',
      title: 'UNFILTERED',
      subtitle: 'Raw Camera Vibes',
      icon: Camera,
      accent: 'from-[#EC4899] to-[#F43F5E]',
      action: () => setUnfilteredOnly(prev => !prev)
    },
    {
      id: 'WHAT_NEXT',
      title: 'WHAT NEXT',
      subtitle: 'Mood Discovery',
      icon: Sparkles,
      accent: 'from-[#8B5CF6] to-[#D946EF]',
      action: () => setSelectedTab('MOOD_ZONES')
    },
    {
      id: 'TIME_CAPSULE',
      title: 'TIME CAPSULE',
      subtitle: 'Future Memories',
      icon: Clock,
      accent: 'from-[#F59E0B] to-[#F97316]',
      action: () => openModal('CREATE_CAPSULE')
    },
    {
      id: 'DOUBLE_TAKE',
      title: 'DOUBLE TAKE',
      subtitle: 'Dual Perspective',
      icon: Layers,
      accent: 'from-[#10B981] to-[#06B6D4]',
      action: () => setUnfilteredOnly(prev => !prev)
    },
    {
      id: 'CROSSED_PATHS',
      title: 'CROSSED PATHS',
      subtitle: 'Real Encounters',
      icon: MapPin,
      accent: 'from-[#6366F1] to-[#00C6FF]',
      action: () => setSelectedTab('CROSSED_PATHS')
    }
  ];

  const calculatedTrending = useMemo(() => {
    const counts: Record<string, number> = {};
    const regex = /#([A-Za-z0-9_]{2,30})/g;
    posts.forEach(p => {
      if ((p as any).isArchived || blockedIds.includes(p.author.id)) return;
      let match;
      while ((match = regex.exec(p.caption || '')) !== null) {
        const tag = match[1].toLowerCase();
        counts[tag] = (counts[tag] || 0) + 1;
      }
      (p.tags || []).forEach(t => {
        const clean = t.replace(/^#/, '').trim().toLowerCase();
        if (clean) counts[clean] = (counts[clean] || 0) + 1;
      });
    });
    return Object.entries(counts)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 8)
      .map(([tag, count]) => ({ tag, count }));
  }, [posts, blockedIds]);

  const featuredCreators = useMemo(() => {
    return allUsers
      .filter(
        u =>
          u.id !== currentUser?.id &&
          !blockedIds.includes(u.id) &&
          !dismissedSuggestionIds.includes(u.id)
      )
      .sort((a, b) => (b.followersCount || 0) - (a.followersCount || 0));
  }, [allUsers, currentUser?.id, blockedIds, dismissedSuggestionIds]);

  const matchedUsers = useMemo(() => {
    if (!cleanQuery) return [];
    const merged = new Map<string, User>();
    for (const u of [...remoteUsers, ...allUsers]) {
      if (blockedIds.includes(u.id)) continue;
      if (
        u.username.toLowerCase().includes(cleanQuery) ||
        u.displayName.toLowerCase().includes(cleanQuery) ||
        (u.bio || '').toLowerCase().includes(cleanQuery) ||
        u.publicVybeId.toLowerCase().includes(cleanQuery)
      ) {
        merged.set(u.id, u);
      }
    }
    return Array.from(merged.values());
  }, [cleanQuery, remoteUsers, allUsers, blockedIds]);

  const matchedPosts = useMemo(() => {
    if (!cleanQuery) return [];
    const merged = new Map<string, Post>();
    for (const p of [...remotePosts, ...posts]) {
      if ((p as any).isArchived || blockedIds.includes(p.author.id)) continue;
      if (unfilteredOnly && !p.isUnfiltered) continue;
      if (
        p.caption.toLowerCase().includes(cleanQuery) ||
        (p.tags || []).some(t => t.toLowerCase().includes(cleanQuery)) ||
        p.author.displayName.toLowerCase().includes(cleanQuery) ||
        p.author.username.toLowerCase().includes(cleanQuery) ||
        (p.locationName || '').toLowerCase().includes(cleanQuery)
      ) {
        merged.set(p.id, p);
      }
    }
    return Array.from(merged.values());
  }, [cleanQuery, remotePosts, posts, blockedIds, unfilteredOnly]);

  const matchedTags = useMemo(() => {
    if (!cleanQuery) return [];
    return calculatedTrending.filter(t => t.tag.includes(cleanQuery));
  }, [cleanQuery, calculatedTrending]);

  const matchedCircles = useMemo(() => {
    if (!cleanQuery) return [];
    return filterExploreCommunities(communities, cleanQuery);
  }, [cleanQuery, communities]);

  const discoveryPosts = useMemo(() => {
    let base = posts.filter(p => !(p as any).isArchived && !blockedIds.includes(p.author.id));
    if (unfilteredOnly) {
      base = base.filter(p => Boolean(p.isUnfiltered));
    }
    if (selectedFilter === 'Trending') {
      return [...base].sort((a, b) => (b.likesCount + b.commentsCount * 2) - (a.likesCount + a.commentsCount * 2));
    }
    if (selectedFilter === 'Nearby') {
      return selectedCity === 'All India'
        ? base.filter(p => Boolean(p.locationName && p.locationName.trim()))
        : base.filter(p => (p.locationName || '').toLowerCase().includes(selectedCity.toLowerCase()));
    }
    if (selectedFilter === 'Interests') {
      return base.filter(
        p =>
          p.caption.toLowerCase().includes(selectedInterest.toLowerCase()) ||
          (p.tags || []).some(t => t.toLowerCase().includes(selectedInterest.toLowerCase()))
      );
    }
    return base;
  }, [posts, blockedIds, unfilteredOnly, selectedFilter, selectedCity, selectedInterest]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (search.trim().length >= 2) {
      addSearchQueryToHistory(search.trim());
    }
  };

  const renderCreatorCard = (creator: User) => {
    const isFollowing = followingIds.includes(creator.id);
    const isRequested = requestedFollowIds.includes(creator.id);
    return (
      <div
        key={creator.id}
        className="p-3.5 rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)]/60 transition-colors flex flex-col justify-between gap-3 relative"
      >
        <button
          type="button"
          onClick={() => dismissSuggestedUser(creator.id)}
          aria-label={`Dismiss @${creator.username}`}
          className="absolute top-2.5 right-2.5 p-1.5 rounded-full text-[var(--vybe-text-muted)] hover:text-[var(--vybe-text)] hover:bg-[var(--vybe-elevated)]"
        >
          <X className="w-3.5 h-3.5" />
        </button>

        <div
          onClick={() => {
            if (isSearching && search.trim().length >= 2) addSearchQueryToHistory(`@${creator.username}`);
            openUserProfile(creator);
          }}
          className="flex items-center gap-3 cursor-pointer pr-6"
        >
          <VybeAvatar
            src={creator.avatarUrl}
            name={creator.displayName}
            username={creator.username}
            isOnline={creator.isOnline}
            className="w-11 h-11 rounded-full shrink-0"
          />
          <div className="min-w-0">
            <div className="flex items-center gap-1">
              <p className="text-xs font-bold text-[var(--vybe-text)] truncate">{creator.displayName}</p>
              {creator.badges?.map(b => (
                <OfficialBadge key={b} badge={b} size="sm" />
              ))}
            </div>
            <p className="text-[11px] text-[var(--vybe-text-secondary)] truncate">
              @{creator.username} · {creator.followersCount.toLocaleString()} followers
            </p>
          </div>
        </div>

        {creator.bio && (
          <p className="text-[11px] text-[var(--vybe-text-secondary)] line-clamp-2">{creator.bio}</p>
        )}

        <div className="flex items-center justify-between gap-2">
          <button
            type="button"
            onClick={() => openUserProfile(creator)}
            className="px-3 py-1.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[11px] font-semibold text-[var(--vybe-text)] hover:border-[var(--vybe-accent)]"
          >
            View Profile
          </button>
          <button
            type="button"
            onClick={() => toggleFollowUser(creator.id)}
            className={`px-3.5 py-1.5 rounded-xl text-[11px] font-bold inline-flex items-center gap-1 transition-colors ${
              isFollowing
                ? 'bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[var(--vybe-text)]'
                : isRequested
                  ? 'bg-amber-500/15 border border-amber-500/40 text-amber-500'
                  : 'bg-[var(--vybe-accent)] text-white keep-white'
            }`}
          >
            {isFollowing ? (
              <Check className="w-3 h-3" />
            ) : isRequested ? (
              <Clock className="w-3 h-3" />
            ) : (
              <UserPlus className="w-3 h-3" />
            )}
            <span>{isFollowing ? 'LINKED' : isRequested ? 'Requested' : 'Follow'}</span>
          </button>
        </div>
      </div>
    );
  };

  const renderPostGrid = (list: Post[]) => (
    <div className="grid grid-cols-2 sm:grid-cols-3 gap-2.5">
      {list.map(p => (
        <div
          key={p.id}
          onClick={() => openSharedPostModal(p.id)}
          className="aspect-square bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-2xl overflow-hidden relative group cursor-pointer"
        >
          {p.mediaUrls[0] ? (
            <img
              src={p.mediaUrls[0]}
              alt={p.caption || 'Explore post'}
              loading="lazy"
              className="w-full h-full object-cover group-hover:scale-105 transition-transform"
            />
          ) : (
            <div className="w-full h-full bg-gradient-to-br from-[#12172B] via-[#1B1436] to-[#080B18] p-4 flex items-center justify-center text-center">
              <p className="text-xs font-semibold text-white/85 keep-white line-clamp-4 leading-relaxed">
                {p.caption || p.author.displayName}
              </p>
            </div>
          )}
          <div className="absolute inset-0 bg-gradient-to-t from-black/85 via-black/15 to-transparent p-3 flex flex-col justify-between">
            <div className="flex items-center justify-between">
              {(p.isVybesReel || p.mediaType === 'REEL' || p.mediaType === 'VIDEO') ? (
                <span className="p-1.5 rounded-full bg-black/50 text-white keep-white">
                  <Play className="w-3 h-3 fill-current" />
                </span>
              ) : <span />}
              <div
                onClick={e => e.stopPropagation()}
                className="rounded-full bg-black/50 backdrop-blur-md px-1.5 py-0.5"
              >
                <FingerHeartReaction
                  isLiked={p.isLiked}
                  count={p.likesCount}
                  size={16}
                  forceDarkOverlay={true}
                  onToggle={() => toggleLikePost(p.id)}
                  className="h-7 px-1"
                />
              </div>
            </div>
            <div className="text-white keep-white text-xs">
              <p className="font-bold truncate text-white keep-white">{p.author.displayName}</p>
              <p className="text-[10px] text-gray-200 keep-white line-clamp-1">{p.caption}</p>
            </div>
          </div>
        </div>
      ))}
    </div>
  );

  return (
    <div className="w-full max-w-4xl mx-auto px-4 py-6 pb-28 lg:pb-12 space-y-6">
      {/* Search Bar */}
      <form onSubmit={handleSearchSubmit} className="relative">
        <Search className="w-4 h-4 text-[var(--vybe-text-muted)] absolute left-4 top-1/2 -translate-y-1/2" />
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search people, #hashtags, posts, reels, or circles..."
          className="w-full pl-11 pr-10 py-3 bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-2xl text-xs text-[var(--vybe-text)] placeholder-[var(--vybe-text-muted)] focus:outline-none focus:border-[var(--vybe-accent)] transition-colors shadow-sm"
        />
        {search && (
          <button
            type="button"
            onClick={() => setSearch('')}
            aria-label="Clear search query"
            className="absolute right-3 top-1/2 -translate-y-1/2 p-1.5 rounded-full text-[var(--vybe-text-muted)] hover:text-[var(--vybe-text)]"
          >
            <X className="w-4 h-4" />
          </button>
        )}
      </form>

      {/* Per-user Local Recent Search History */}
      {searchHistory.length > 0 && (
        <section className="space-y-2" aria-label="Recent Searches">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-xs font-bold text-[var(--vybe-text-secondary)]">Recent Searches</h3>
              <p className="text-[10px] text-[var(--vybe-text-muted)]">Saved locally on this device</p>
            </div>
            <button
              type="button"
              onClick={clearSearchHistory}
              className="text-[11px] font-bold text-[var(--vybe-accent)] hover:underline px-2 py-1"
            >
              Clear All
            </button>
          </div>
          <div className="flex flex-wrap gap-2">
            {searchHistory.map(term => (
              <div
                key={term}
                className="inline-flex items-center gap-1 pl-3 pr-1.5 py-1 rounded-xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-xs text-[var(--vybe-text)]"
              >
                <button
                  type="button"
                  onClick={() => {
                    setSearch(term);
                    addSearchQueryToHistory(term);
                  }}
                  className="hover:text-[var(--vybe-accent)] font-medium"
                >
                  {term}
                </button>
                <button
                  type="button"
                  onClick={() => removeSearchQueryFromHistory(term)}
                  aria-label={`Remove ${term} from recent searches`}
                  className="p-1 text-[var(--vybe-text-muted)] hover:text-[var(--vybe-text)]"
                >
                  <X className="w-3 h-3" />
                </button>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* Prominent 6 Signature Features Header Grid */}
      <section className="space-y-3" aria-label="VYBE Signature Features">
        <div className="flex items-center justify-between">
          <h2 className="text-xs font-extrabold text-[var(--vybe-text)] uppercase tracking-wider flex items-center gap-1.5">
            <Compass className="w-4 h-4 text-[var(--vybe-accent)]" />
            <span>Explore VYBE Signature Zones</span>
          </h2>
          {unfilteredOnly && (
            <button
              type="button"
              onClick={() => setUnfilteredOnly(false)}
              className="text-[11px] font-bold text-[var(--vybe-accent)] hover:underline"
            >
              Showing Unfiltered · Reset
            </button>
          )}
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-3 gap-2.5">
          {signatureFeatures.map(feature => {
            const IconComp = feature.icon;
            const isHighlighted = feature.id === 'UNFILTERED' && unfilteredOnly;
            return (
              <button
                key={feature.id}
                type="button"
                onClick={feature.action}
                className={`p-3.5 rounded-2xl bg-[var(--vybe-surface)] border text-left transition-all hover:-translate-y-0.5 flex items-center gap-3 min-h-[68px] ${
                  isHighlighted
                    ? 'border-[var(--vybe-accent)] ring-1 ring-[var(--vybe-accent)]/40'
                    : 'border-[var(--vybe-border)] hover:border-[var(--vybe-accent)]/60'
                }`}
              >
                <div
                  className={`w-10 h-10 rounded-xl bg-gradient-to-tr ${feature.accent} flex items-center justify-center text-white keep-white shrink-0 shadow-sm`}
                >
                  <IconComp className="w-5 h-5" />
                </div>
                <div className="min-w-0 flex-1">
                  <p className="text-xs font-black tracking-wide text-[var(--vybe-text)] leading-tight break-words">
                    {feature.title}
                  </p>
                  <p className="text-[11px] text-[var(--vybe-text-secondary)] mt-0.5 leading-snug">
                    {feature.subtitle}
                  </p>
                </div>
              </button>
            );
          })}
        </div>
      </section>

      {/* Live UNLOAD Room Preview Banner */}
      {primaryUnloadRoom && (
        <div className="p-4 sm:p-5 rounded-2xl bg-gradient-to-r from-[#D946EF]/15 via-[#8B5CF6]/15 to-[#00C6FF]/15 border border-[var(--vybe-border)] flex flex-wrap sm:flex-nowrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="relative flex items-center justify-center p-3 rounded-xl bg-red-500/15 text-red-500 shrink-0">
              <Radio className="w-6 h-6 animate-pulse" />
            </div>
            <div>
              <div className="flex items-center gap-2 flex-wrap">
                <span className="px-2 py-0.5 rounded-full bg-red-600 text-[10px] font-black uppercase tracking-wider text-white keep-white">
                  LIVE
                </span>
                <h3 className="font-bold text-sm text-[var(--vybe-text)]">{primaryUnloadRoom.title}</h3>
              </div>
              <p className="text-xs text-[var(--vybe-text-secondary)] mt-0.5">
                {primaryUnloadRoom.hostName} · {primaryUnloadRoom.activeListenersCount.toLocaleString()} listeners · {primaryUnloadRoom.topicTag}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={() => openModal('UNLOAD_ROOM', primaryUnloadRoom)}
            className="px-4 py-2 bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold rounded-xl shadow-sm hover:opacity-95 transition-opacity shrink-0"
          >
            Tune In
          </button>
        </div>
      )}

      {/* Search Results Mode vs Discovery Mode */}
      {isSearching ? (
        <div className="space-y-4">
          <div className="flex flex-wrap gap-2">
            {SEARCH_TABS.map(tab => {
              const active = selectedSearchTab === tab;
              return (
                <button
                  key={tab}
                  type="button"
                  onClick={() => setSelectedSearchTab(tab)}
                  className={`px-3.5 py-2 rounded-full text-xs font-bold transition-colors ${
                    active
                      ? 'bg-[var(--vybe-accent)] text-white keep-white'
                      : 'bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)]'
                  }`}
                >
                  {tab}
                </button>
              );
            })}
          </div>

          {isSearchingRemote && (
            <div className="flex items-center gap-2 text-xs text-[var(--vybe-text-secondary)] py-1">
              <Loader2 className="w-4 h-4 animate-spin text-[var(--vybe-accent)]" />
              <span>Searching live VYBE directory...</span>
            </div>
          )}

          {remoteSearchError && (
            <div className="p-3 rounded-2xl bg-red-500/10 border border-red-500/30 flex items-center justify-between gap-2 text-xs text-red-500">
              <span>{remoteSearchError}</span>
              <button
                type="button"
                onClick={() => setSearchRetryNonce(n => n + 1)}
                className="inline-flex items-center gap-1 px-3 py-1 rounded-xl bg-red-500 text-white keep-white font-bold"
              >
                <RefreshCw className="w-3 h-3" />
                <span>Retry</span>
              </button>
            </div>
          )}

          {(selectedSearchTab === 'All' || selectedSearchTab === 'People') && matchedUsers.length > 0 && (
            <div className="space-y-3">
              <h3 className="text-xs font-bold text-[var(--vybe-text-secondary)] uppercase tracking-wider">People</h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {matchedUsers.slice(0, 8).map(renderCreatorCard)}
              </div>
            </div>
          )}

          {(selectedSearchTab === 'All' || selectedSearchTab === 'Tags') && matchedTags.length > 0 && (
            <div className="space-y-3">
              <h3 className="text-xs font-bold text-[var(--vybe-text-secondary)] uppercase tracking-wider">Hashtags</h3>
              <div className="flex flex-wrap gap-2">
                {matchedTags.map(t => (
                  <button
                    key={t.tag}
                    type="button"
                    onClick={() => {
                      addSearchQueryToHistory(`#${t.tag}`);
                      setSearch(`#${t.tag}`);
                    }}
                    className="px-3.5 py-2 rounded-xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] text-xs text-[var(--vybe-text)] flex items-center gap-2"
                  >
                    <span className="font-bold text-[var(--vybe-accent)]">#{t.tag}</span>
                    <span className="text-[10px] text-[var(--vybe-text-muted)]">{t.count} posts</span>
                  </button>
                ))}
              </div>
            </div>
          )}

          {(selectedSearchTab === 'All' || selectedSearchTab === 'Circles') && matchedCircles.length > 0 && (
            <div className="space-y-3">
              <h3 className="text-xs font-bold text-[var(--vybe-text-secondary)] uppercase tracking-wider">Circles</h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {matchedCircles.map(comm => (
                  <div
                    key={comm.id}
                    className="p-3.5 rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] flex items-center justify-between gap-3"
                  >
                    <div className="min-w-0">
                      <h4 className="font-bold text-xs text-[var(--vybe-text)] truncate">{comm.name}</h4>
                      <p className="text-[11px] text-[var(--vybe-text-secondary)] truncate">
                        {comm.category} · {comm.membersCount} members
                      </p>
                    </div>
                    <button
                      type="button"
                      onClick={() => toggleJoinCommunity(comm.id)}
                      className={`px-3.5 py-1.5 rounded-xl text-[11px] font-bold shrink-0 ${
                        comm.isJoined
                          ? 'bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[var(--vybe-text)]'
                          : 'bg-[var(--vybe-accent)] text-white keep-white'
                      }`}
                    >
                      {comm.isJoined ? 'Joined' : 'Join'}
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}

          {(selectedSearchTab === 'All' || selectedSearchTab === 'Posts') && (
            (() => {
              const targetList = matchedPosts;
              if (targetList.length === 0) return null;
              return (
                <div className="space-y-3">
                  <h3 className="text-xs font-bold text-[var(--vybe-text-secondary)] uppercase tracking-wider">
                    Moments
                  </h3>
                  {renderPostGrid(targetList)}
                </div>
              );
            })()
          )}

          {!isSearchingRemote &&
            !remoteSearchError &&
            ((selectedSearchTab === 'All' &&
              matchedUsers.length === 0 &&
              matchedPosts.length === 0 &&
              matchedTags.length === 0 &&
              matchedCircles.length === 0) ||
              (selectedSearchTab === 'People' && matchedUsers.length === 0) ||
              (selectedSearchTab === 'Posts' && matchedPosts.length === 0) ||
              (selectedSearchTab === 'Circles' && matchedCircles.length === 0) ||
              (selectedSearchTab === 'Tags' && matchedTags.length === 0)) && (
              <VybeEmptyState
                icon={Search}
                title={`No matching results for "${search}"`}
                subtitle="Try a different keyword, @username, #hashtag, or Circle."
                actionLabel="Clear Search"
                onAction={() => setSearch('')}
              />
            )}
        </div>
      ) : (
        <div className="space-y-6">
          {/* 6 Discovery Filter Pills */}
          <div className="flex flex-wrap gap-2">
            {DISCOVERY_FILTERS.map(filter => {
              const active = selectedFilter === filter;
              return (
                <button
                  key={filter}
                  type="button"
                  onClick={() => setSelectedFilter(filter)}
                  className={`px-3.5 py-2 rounded-full text-xs font-bold transition-colors ${
                    active
                      ? 'bg-[var(--vybe-accent)] text-white keep-white'
                      : 'bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)]'
                  }`}
                >
                  {filter}
                </button>
              );
            })}
          </div>

          {selectedFilter === 'Nearby' && (
            <div className="p-4 rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] space-y-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <h3 className="text-xs font-bold text-[var(--vybe-text)]">Nearby Safe Discovery</h3>
                  <p className="text-[11px] text-[var(--vybe-text-secondary)]">
                    Browse public posts by city. Exact GPS coordinates are never published.
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setNearbyPromptVisible(v => !v)}
                  className="px-3 py-1.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[11px] font-bold text-[var(--vybe-accent)]"
                >
                  Location Privacy Info
                </button>
              </div>
              {nearbyPromptVisible && (
                <p className="text-[11px] text-[var(--vybe-text-secondary)] bg-[var(--vybe-elevated)] p-2.5 rounded-xl border border-[var(--vybe-border)]">
                  VYBE matches broad city labels on public moments only. Select any city below to filter nearby content safely.
                </p>
              )}
              <div className="flex flex-wrap gap-2">
                {INDIAN_CITIES.map(city => (
                  <button
                    key={city}
                    type="button"
                    onClick={() => setSelectedCity(city)}
                    className={`px-3 py-1.5 rounded-full text-[11px] font-semibold ${
                      selectedCity === city
                        ? 'bg-[var(--vybe-accent)] text-white keep-white'
                        : 'bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[var(--vybe-text-secondary)]'
                    }`}
                  >
                    {city}
                  </button>
                ))}
              </div>
            </div>
          )}

          {selectedFilter === 'Interests' && (
            <div className="flex flex-wrap gap-2">
              {INTEREST_CATEGORIES.map(interest => (
                <button
                  key={interest}
                  type="button"
                  onClick={() => setSelectedInterest(interest)}
                  className={`px-3.5 py-1.5 rounded-full text-xs font-semibold ${
                    selectedInterest === interest
                      ? 'bg-[var(--vybe-accent)] text-white keep-white'
                      : 'bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-[var(--vybe-text-secondary)]'
                  }`}
                >
                  #{interest}
                </button>
              ))}
            </div>
          )}

          {/* Featured Creators / People Filter */}
          {(selectedFilter === 'For You' || selectedFilter === 'Trending' || selectedFilter === 'People') && (
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <h3 className="text-xs font-bold text-[var(--vybe-text-secondary)] uppercase tracking-wider flex items-center gap-1.5">
                  <Users className="w-4 h-4 text-[var(--vybe-accent)]" />
                  <span>Discover Creators</span>
                </h3>
                {dismissedSuggestionIds.length > 0 && (
                  <button
                    type="button"
                    onClick={restoreDismissedSuggestions}
                    className="text-[11px] font-bold text-[var(--vybe-accent)] hover:underline"
                  >
                    Show Hidden Creators
                  </button>
                )}
              </div>
              {featuredCreators.length === 0 ? (
                <VybeEmptyState
                  icon={Users}
                  title="No creator suggestions right now"
                  subtitle={
                    dismissedSuggestionIds.length > 0
                      ? 'Restore previously hidden creators to discover more people.'
                      : 'Check back soon for active creators on VYBE.'
                  }
                  actionLabel={dismissedSuggestionIds.length > 0 ? 'Restore Hidden' : undefined}
                  onAction={dismissedSuggestionIds.length > 0 ? restoreDismissedSuggestions : undefined}
                />
              ) : (
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  {(selectedFilter === 'People' ? featuredCreators : featuredCreators.slice(0, 4)).map(renderCreatorCard)}
                </div>
              )}
            </div>
          )}

          {/* Circles Filter */}
          {selectedFilter === 'Circles' && (
            <div className="space-y-3">
              <h3 className="text-xs font-bold text-[var(--vybe-text-secondary)] uppercase tracking-wider flex items-center gap-1.5">
                <Users className="w-4 h-4 text-[var(--vybe-accent)]" />
                <span>Communities & Circles</span>
              </h3>
              {communities.length === 0 ? (
                <VybeEmptyState
                  icon={Users}
                  title="No Circles available yet"
                  subtitle="Open Community Hub to browse or start a VYBE Circle."
                  actionLabel="Open Community Hub"
                  onAction={() => setSelectedTab('COMMUNITY')}
                />
              ) : (
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  {communities.map(comm => (
                    <div
                      key={comm.id}
                      className="p-4 rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] flex flex-col justify-between gap-3"
                    >
                      <div>
                        <div className="flex items-center justify-between gap-2">
                          <h4 className="font-bold text-sm text-[var(--vybe-text)] truncate">{comm.name}</h4>
                          <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-[var(--vybe-elevated)] text-[var(--vybe-text-secondary)]">
                            {comm.category}
                          </span>
                        </div>
                        {comm.description && (
                          <p className="text-xs text-[var(--vybe-text-secondary)] mt-1 line-clamp-2">{comm.description}</p>
                        )}
                      </div>
                      <div className="flex items-center justify-between">
                        <span className="text-[11px] text-[var(--vybe-text-muted)]">
                          {comm.membersCount.toLocaleString()} members
                        </span>
                        <button
                          type="button"
                          onClick={() => toggleJoinCommunity(comm.id)}
                          className={`px-3.5 py-1.5 rounded-xl text-xs font-bold ${
                            comm.isJoined
                              ? 'bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[var(--vybe-text)]'
                              : 'bg-[var(--vybe-accent)] text-white keep-white'
                          }`}
                        >
                          {comm.isJoined ? 'Joined' : 'Join Circle'}
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* Trending Hashtags */}
          {(selectedFilter === 'For You' || selectedFilter === 'Trending') && (
            <div className="space-y-3">
              <h3 className="text-xs font-bold text-[var(--vybe-text-secondary)] uppercase tracking-wider flex items-center gap-1.5">
                <TrendingUp className="w-4 h-4 text-[var(--vybe-accent)]" />
                <span>Trending Frequencies</span>
              </h3>
              {calculatedTrending.length > 0 ? (
                <div className="flex flex-wrap gap-2">
                  {calculatedTrending.map(t => (
                    <button
                      key={t.tag}
                      type="button"
                      onClick={() => {
                        setSearch(`#${t.tag}`);
                        addSearchQueryToHistory(`#${t.tag}`);
                      }}
                      className="px-3.5 py-2 rounded-xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] text-xs text-[var(--vybe-text)] transition-colors flex items-center gap-2"
                    >
                      <span className="font-bold text-[var(--vybe-accent)]">#{t.tag}</span>
                      <span className="text-[10px] text-[var(--vybe-text-muted)]">
                        {t.count} {t.count === 1 ? 'post' : 'posts'}
                      </span>
                    </button>
                  ))}
                </div>
              ) : (
                <div className="p-3.5 rounded-xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-xs text-[var(--vybe-text-secondary)]">
                  No trending hashtags in loaded posts yet.
                </div>
              )}
            </div>
          )}

          {/* Verified Shops Section */}
          {shops.length > 0 && selectedFilter === 'For You' && (
            <div className="space-y-3">
              <h3 className="text-xs font-bold text-[var(--vybe-text-secondary)] uppercase tracking-wider flex items-center gap-1.5">
                <Store className="w-4 h-4 text-amber-500" />
                <span>Verified Artisan Shops</span>
              </h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {shops.map(shop => (
                  <div
                    key={shop.id}
                    onClick={() => openShopDetail(shop)}
                    className="p-3.5 rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] hover:border-amber-500/60 cursor-pointer flex items-center gap-3 transition-all"
                  >
                    {shop.logoUrl ? (
                      <img
                        src={shop.logoUrl}
                        alt={shop.businessName}
                        className="w-12 h-12 rounded-xl object-cover shrink-0 border border-[var(--vybe-border)]"
                      />
                    ) : (
                      <div className="w-12 h-12 rounded-xl shrink-0 border border-[var(--vybe-border)] bg-[var(--vybe-elevated)] flex items-center justify-center text-amber-500">
                        <Store className="w-5 h-5" />
                      </div>
                    )}
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-1">
                        <h4 className="font-bold text-xs text-[var(--vybe-text)] truncate">{shop.businessName}</h4>
                        <OfficialBadge badge="SHOP_GOLD" size="sm" />
                      </div>
                      <p className="text-[11px] text-[var(--vybe-text-secondary)] line-clamp-1">{shop.category}</p>
                      <span className="inline-flex items-center gap-1 text-[10px] text-amber-500 font-bold mt-0.5">
                        <Star className="w-3 h-3 fill-current" />
                        {shop.rating} ({shop.reviewsCount})
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Visual Exploration Grid */}
          {selectedFilter !== 'People' && selectedFilter !== 'Circles' && (
            <div className="space-y-3">
              <h3 className="text-xs font-bold text-[var(--vybe-text-secondary)] uppercase tracking-wider">
                Explore Moments
              </h3>
              {discoveryPosts.length === 0 ? (
                <VybeEmptyState
                  icon={Search}
                  title="No matching moments"
                  subtitle="Try another filter or explore a different city or interest tag."
                  actionLabel={selectedFilter !== 'For You' || unfilteredOnly ? 'Reset Filters' : undefined}
                  onAction={
                    selectedFilter !== 'For You' || unfilteredOnly
                      ? () => {
                          setSelectedFilter('For You');
                          setUnfilteredOnly(false);
                        }
                      : undefined
                  }
                />
              ) : (
                renderPostGrid(discoveryPosts)
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
};
