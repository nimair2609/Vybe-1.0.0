import React, { useState } from 'react';
import { Sparkles, Shield, Heart, Plus, Users, Music } from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { MOOD_DEFINITIONS, MoodType } from '../types';
import { PostCard } from '../components/PostCard';

export const MoodZonesView: React.FC = () => {
  const { posts, currentUser, setCurrentUserMood, openModal } = useVybe();
  const [activeZone, setActiveZone] = useState<MoodType>('CHILL');

  const config = MOOD_DEFINITIONS[activeZone];
  const zonePosts = posts.filter(p => {
    if (activeZone === 'GENERAL') return !p.moodZone || p.moodZone === 'GENERAL';
    return p.moodZone === activeZone;
  });

  return (
    <div className="w-full max-w-4xl mx-auto px-4 py-6 pb-28 space-y-6">
      
      {/* Mood Zones Header */}
      <div className="text-center space-y-2">
        <h1 className="text-2xl font-black brand-font tracking-wide text-transparent bg-clip-text bg-gradient-to-r from-white via-slate-100 to-[#00C6FF]">
          Emotional Sanctuaries & Mood Zones
        </h1>
        <p className="text-xs text-gray-400 max-w-md mx-auto">
          Social media filtered by what you actually feel right now. No vanity algorithms. Pure emotional frequency.
        </p>
      </div>

      {/* Mood Selector Tabs Grid */}
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-6 gap-2.5">
        {(Object.keys(MOOD_DEFINITIONS) as MoodType[]).map(m => {
          const item = MOOD_DEFINITIONS[m];
          const isActive = activeZone === m;
          return (
            <button
              key={m}
              onClick={() => setActiveZone(m)}
              className={`p-3 rounded-2xl flex flex-col items-center justify-center gap-1.5 transition-all text-center border ${
                isActive 
                  ? 'bg-gradient-to-b ' + item.gradient + ' text-white border-transparent shadow-lg scale-105' 
                  : 'bg-[#101322] border-[#262E4A] text-gray-300 hover:border-gray-500'
              }`}
            >
              <span className="text-2xl">{item.emoji}</span>
              <span className="text-xs font-bold leading-tight">{item.title}</span>
            </button>
          );
        })}
      </div>

      {/* Selected Mood Hero Card */}
      <div className={`p-6 rounded-3xl border border-white/10 bg-gradient-to-br ${config.gradient}/20 backdrop-blur-xl shadow-2xl relative overflow-hidden`}>
        <div className="relative z-10 flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div className="space-y-1.5 max-w-xl">
            <div className="flex items-center gap-2">
              <span className="text-2xl">{config.emoji}</span>
              <h2 className="text-xl font-bold text-white brand-font">{config.title} Zone</h2>
              <span className="px-2.5 py-0.5 rounded-full bg-white/10 text-xs font-medium text-white">
                {zonePosts.length} Active Vibes
              </span>
            </div>
            <p className="text-sm text-gray-200 font-medium leading-relaxed">
              "{config.vibeTagline}"
            </p>
            <div className="flex items-center gap-2 text-xs text-gray-400 pt-1">
              <Shield className="w-3.5 h-3.5 text-[#22C55E]" />
              <span>{config.safetyGuideline}</span>
            </div>
          </div>

          <div className="flex items-center gap-2.5 shrink-0">
            <button
              onClick={() => setCurrentUserMood(activeZone)}
              className={`px-4 py-2.5 rounded-xl text-xs font-bold transition-all shadow-md ${
                currentUser.currentMood === activeZone 
                  ? 'bg-[#22C55E] text-[#060714]' 
                  : 'bg-white text-[#060714] hover:bg-gray-100'
              }`}
            >
              {currentUser.currentMood === activeZone ? '✓ Your Active Mood' : 'Set as My Mood'}
            </button>
            <button
              onClick={() => openModal('CREATE_POST')}
              className="px-4 py-2.5 rounded-xl bg-gradient-to-r from-[#00C6FF] to-[#8B5CF6] text-white text-xs font-bold shadow-md hover:opacity-95"
            >
              + Post in {config.title}
            </button>
          </div>
        </div>
      </div>

      {/* Posts in Active Mood Zone */}
      <div className="space-y-4 max-w-xl mx-auto pt-2">
        <h3 className="text-xs font-bold text-gray-400 uppercase tracking-widest">
          Recent {config.title} Moments
        </h3>
        {zonePosts.length === 0 ? (
          <div className="p-8 text-center bg-[#101322] border border-[#262E4A] rounded-2xl">
            <p className="text-sm text-gray-300">No moments posted in the {config.title} zone yet.</p>
            <button
              onClick={() => openModal('CREATE_POST')}
              className="mt-3 px-4 py-2 bg-[#8B5CF6] text-white rounded-xl text-xs font-bold"
            >
              Be First to Share
            </button>
          </div>
        ) : (
          zonePosts.map(p => (
            <PostCard key={p.id} post={p} />
          ))
        )}
      </div>

    </div>
  );
};
