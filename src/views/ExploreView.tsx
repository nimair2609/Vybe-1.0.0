import React, { useState, useMemo } from 'react';
import {
  Search, Sparkles, TrendingUp, Radio, Store,
  Mic, Camera, Compass, Clock, Layers, MapPin, Star
} from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { OfficialBadge } from '../components/Badges';
import { FingerHeartReaction } from '../components/FingerHeartReaction';
import { VybeEmptyState } from '../components/VybeDesignSystem';

export const ExploreView: React.FC = () => {
  const {
    posts,
    shops,
    unloadRooms,
    openShopDetail,
    openModal,
    setSelectedTab,
    openSharedPostModal,
    toggleLikePost
  } = useVybe();

  const [search, setSearch] = useState('');
  const [unfilteredOnly, setUnfilteredOnly] = useState(false);

  const primaryUnloadRoom = unloadRooms[0];

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
      action: () => setSelectedTab('VYBES')
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
      if ((p as any).isArchived) return;
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
  }, [posts]);

  const filteredPosts = posts.filter(p => {
    if (unfilteredOnly && !p.isUnfiltered) return false;
    const q = search.trim().toLowerCase();
    if (!q) return true;
    return (
      p.caption.toLowerCase().includes(q) ||
      p.tags.some(t => t.toLowerCase().includes(q)) ||
      p.author.displayName.toLowerCase().includes(q)
    );
  });

  return (
    <div className="w-full max-w-4xl mx-auto px-4 py-6 pb-28 lg:pb-12 space-y-6">
      {/* Search Bar */}
      <div className="relative">
        <Search className="w-4 h-4 text-[var(--vybe-text-muted)] absolute left-4 top-1/2 -translate-y-1/2" />
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Explore vibes, hashtags, creators, and shops..."
          className="w-full pl-11 pr-4 py-3 bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-2xl text-xs text-[var(--vybe-text)] placeholder-[var(--vybe-text-muted)] focus:outline-none focus:border-[var(--vybe-accent)] transition-colors shadow-sm"
        />
      </div>

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

      {/* Live UNLOAD Room Preview Banner (driven strictly from live unloadRooms data) */}
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

      {/* Trending Hashtags */}
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
                onClick={() => setSearch(t.tag)}
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

      {/* Verified Shops Section */}
      {shops.length > 0 && (
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
                <img
                  src={shop.logoUrl}
                  alt={shop.businessName}
                  className="w-12 h-12 rounded-xl object-cover shrink-0 border border-[var(--vybe-border)]"
                />
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
      <div className="space-y-3">
        <h3 className="text-xs font-bold text-[var(--vybe-text-secondary)] uppercase tracking-wider">
          Explore Moments
        </h3>
        {filteredPosts.length === 0 ? (
          <VybeEmptyState
            icon={Search}
            title="No matching moments"
            subtitle="Try adjusting your search keywords or reset filters."
            actionLabel={search || unfilteredOnly ? 'Reset Filters' : undefined}
            onAction={
              search || unfilteredOnly
                ? () => {
                    setSearch('');
                    setUnfilteredOnly(false);
                  }
                : undefined
            }
          />
        ) : (
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-2.5">
            {filteredPosts.map(p => (
              <div
                key={p.id}
                onClick={() => openSharedPostModal(p.id)}
                className="aspect-square bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-2xl overflow-hidden relative group cursor-pointer"
              >
                <img
                  src={
                    p.mediaUrls[0] ||
                    'https://images.unsplash.com/photo-1514565131-fce0801e5785?auto=format&fit=crop&w=400&q=80'
                  }
                  alt={p.caption}
                  loading="lazy"
                  className="w-full h-full object-cover group-hover:scale-105 transition-transform"
                />
                <div className="absolute inset-0 bg-gradient-to-t from-black/85 via-black/15 to-transparent p-3 flex flex-col justify-between">
                  <div className="flex justify-end">
                    <div className="rounded-full bg-black/50 backdrop-blur-md px-1.5 py-0.5">
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
        )}
      </div>
    </div>
  );
};
