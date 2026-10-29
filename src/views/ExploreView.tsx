import React, { useState } from 'react';
import { Search, Sparkles, TrendingUp, Radio, Store, Play } from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { OfficialBadge } from '../components/Badges';

export const ExploreView: React.FC = () => {
  const { posts, allUsers, shops, openShopDetail } = useVybe();
  const [search, setSearch] = useState('');

  const trendingTags = [
    { tag: 'lofibits', count: '14.2K' },
    { tag: 'mumbairain', count: '28.9K' },
    { tag: 'specialtycoffee', count: '9.4K' },
    { tag: 'indierock', count: '18.1K' },
    { tag: 'mindfulness', count: '31.5K' },
    { tag: 'cyberluxe', count: '7.8K' }
  ];

  const filteredPosts = posts.filter(p => 
    p.caption.toLowerCase().includes(search.toLowerCase()) ||
    p.tags.some(t => t.toLowerCase().includes(search.toLowerCase())) ||
    p.author.displayName.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="w-full max-w-3xl mx-auto px-4 py-6 pb-28 space-y-6">
      
      {/* Search Bar */}
      <div className="relative">
        <Search className="w-4 h-4 text-gray-400 absolute left-4 top-1/2 -translate-y-1/2" />
        <input 
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Explore vibes, music tracks, creators, and artisan shops..."
          className="w-full pl-11 pr-4 py-3 bg-[#101322] border border-[#262E4A] rounded-2xl text-xs text-white placeholder-gray-400 focus:outline-none focus:border-[#00C6FF] transition-all shadow-lg"
        />
      </div>

      {/* Live Stream Banner Studio Preview */}
      <div className="p-5 rounded-2xl bg-gradient-to-r from-[#D946EF]/20 via-[#8B5CF6]/20 to-[#00C6FF]/20 border border-[#D946EF]/30 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="relative flex items-center justify-center p-3 rounded-xl bg-red-500/20 text-red-400">
            <Radio className="w-6 h-6 animate-pulse" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="px-2 py-0.5 rounded-full bg-red-600 text-[10px] font-black uppercase tracking-wider text-white">LIVE</span>
              <h3 className="font-bold text-sm text-white">Late Night Jamming & Chill Chat</h3>
            </div>
            <p className="text-xs text-gray-300 mt-0.5">Maya Sen & 140+ community members listening</p>
          </div>
        </div>
        <button
          onClick={() => alert('Connected to Live Audio Stream Studio!')}
          className="px-4 py-2 bg-gradient-to-r from-[#00C6FF] to-[#8B5CF6] text-white text-xs font-bold rounded-xl shadow-lg shrink-0"
        >
          Tune In
        </button>
      </div>

      {/* Trending Hashtags */}
      <div className="space-y-3">
        <h3 className="text-xs font-bold text-gray-300 uppercase tracking-wider flex items-center gap-1.5">
          <TrendingUp className="w-4 h-4 text-[#00C6FF]" />
          Trending Frequencies
        </h3>
        <div className="flex flex-wrap gap-2">
          {trendingTags.map(t => (
            <button
              key={t.tag}
              onClick={() => setSearch(t.tag)}
              className="px-3.5 py-2 rounded-xl bg-[#101322] border border-[#262E4A] hover:border-[#00C6FF]/40 text-xs text-gray-200 transition-colors flex items-center gap-2"
            >
              <span className="font-bold text-[#00C6FF]">#{t.tag}</span>
              <span className="text-[10px] text-gray-500">{t.count} vibes</span>
            </button>
          ))}
        </div>
      </div>

      {/* Verified Shops Section */}
      <div className="space-y-3">
        <h3 className="text-xs font-bold text-gray-300 uppercase tracking-wider flex items-center gap-1.5">
          <Store className="w-4 h-4 text-[#FFB703]" />
          Verified Artisan Shops & Independent Roasters
        </h3>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          {shops.map(shop => (
            <div
              key={shop.id}
              onClick={() => openShopDetail(shop)}
              className="p-3.5 rounded-2xl bg-[#101322] border border-[#262E4A] hover:border-[#FFB703]/50 cursor-pointer flex items-center gap-3 transition-all"
            >
              <img 
                src={shop.logoUrl} 
                alt={shop.businessName}
                className="w-12 h-12 rounded-xl object-cover" 
              />
              <div className="flex-1">
                <div className="flex items-center gap-1">
                  <h4 className="font-bold text-xs text-white">{shop.businessName}</h4>
                  <OfficialBadge badge="SHOP_GOLD" size="sm" />
                </div>
                <p className="text-[11px] text-gray-400 line-clamp-1">{shop.category}</p>
                <span className="text-[10px] text-amber-400 font-bold">★ {shop.rating} ({shop.reviewsCount})</span>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Visual Exploration Grid */}
      <div className="space-y-3">
        <h3 className="text-xs font-bold text-gray-300 uppercase tracking-wider">
          Explore Moments
        </h3>
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
          {filteredPosts.map(p => (
            <div 
              key={p.id}
              className="aspect-square bg-[#101322] border border-[#262E4A] rounded-2xl overflow-hidden relative group cursor-pointer"
            >
              <img 
                src={p.mediaUrls[0] || 'https://images.unsplash.com/photo-1514565131-fce0801e5785?auto=format&fit=crop&w=400&q=80'} 
                alt={p.caption} 
                className="w-full h-full object-cover group-hover:scale-105 transition-transform"
              />
              <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity p-3 flex flex-col justify-end text-white text-xs">
                <p className="font-bold truncate">{p.author.displayName}</p>
                <p className="text-[10px] text-gray-300 line-clamp-1">{p.caption}</p>
              </div>
            </div>
          ))}
        </div>
      </div>

    </div>
  );
};
