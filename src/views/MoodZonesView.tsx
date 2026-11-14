import React, { useState } from 'react';
import { Shield, Check, Plus } from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { MOOD_DEFINITIONS, MoodType } from '../types';
import { PostCard } from '../components/PostCard';
import { VybeEmptyState } from '../components/VybeDesignSystem';

export const MoodZonesView: React.FC = () => {
  const { posts, currentUser, setCurrentUserMood, openModal } = useVybe();
  const [activeZone, setActiveZone] = useState<MoodType>('CHILL');

  const config = MOOD_DEFINITIONS[activeZone];
  const zonePosts = posts.filter(p => {
    if (activeZone === 'GENERAL') return !p.moodZone || p.moodZone === 'GENERAL';
    return p.moodZone === activeZone;
  });

  return (
    <div className="w-full max-w-4xl mx-auto px-4 py-6 pb-28 lg:pb-12 space-y-6">
      {/* Mood Zones Header */}
      <div className="text-center space-y-1.5">
        <h1 className="text-2xl font-extrabold brand-font tracking-tight text-[var(--vybe-text)]">
          Emotional Sanctuaries & Mood Zones
        </h1>
        <p className="text-xs text-[var(--vybe-text-secondary)] max-w-md mx-auto">
          Feeds filtered by what you feel right now. Pure emotional frequency without noisy engagement algorithms.
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
              type="button"
              onClick={() => setActiveZone(m)}
              className={`p-3 rounded-2xl flex flex-col items-center justify-center gap-1.5 transition-all text-center border ${
                isActive
                  ? 'bg-gradient-to-b ' + item.gradient + ' text-white keep-white border-transparent shadow-md scale-[1.03]'
                  : 'bg-[var(--vybe-surface)] border-[var(--vybe-border)] text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)] hover:border-[var(--vybe-accent)]'
              }`}
            >
              <span className="text-2xl">{item.emoji}</span>
              <span className="text-xs font-bold leading-tight">{item.title}</span>
            </button>
          );
        })}
      </div>

      {/* Selected Mood Hero Card */}
      <div className="p-5 sm:p-6 rounded-3xl border border-[var(--vybe-border)] bg-[var(--vybe-surface)] shadow-sm relative overflow-hidden">
        <div className={`absolute inset-0 opacity-15 bg-gradient-to-br ${config.gradient} pointer-events-none`} />
        <div className="relative z-10 flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div className="space-y-1.5 max-w-xl">
            <div className="flex items-center gap-2 flex-wrap">
              <span className="text-2xl">{config.emoji}</span>
              <h2 className="text-xl font-bold text-[var(--vybe-text)] brand-font">{config.title} Zone</h2>
              <span className="px-2.5 py-0.5 rounded-full bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs font-medium text-[var(--vybe-text-secondary)]">
                {zonePosts.length} Active Vibes
              </span>
            </div>
            <p className="text-sm text-[var(--vybe-text)] font-medium leading-relaxed">
              &ldquo;{config.vibeTagline}&rdquo;
            </p>
            <div className="flex items-center gap-2 text-xs text-[var(--vybe-text-secondary)] pt-1">
              <Shield className="w-3.5 h-3.5 text-emerald-500 shrink-0" />
              <span>{config.safetyGuideline}</span>
            </div>
          </div>

          <div className="flex items-center gap-2.5 shrink-0 flex-wrap">
            <button
              type="button"
              onClick={() => setCurrentUserMood(activeZone)}
              className={`inline-flex items-center gap-1.5 px-4 py-2.5 rounded-xl text-xs font-bold transition-all border ${
                currentUser?.currentMood === activeZone
                  ? 'bg-emerald-600 text-white keep-white border-transparent'
                  : 'bg-[var(--vybe-elevated)] text-[var(--vybe-text)] border-[var(--vybe-border)] hover:border-[var(--vybe-accent)]'
              }`}
            >
              {currentUser?.currentMood === activeZone && <Check className="w-3.5 h-3.5" />}
              <span>{currentUser?.currentMood === activeZone ? 'Your Active Mood' : 'Set as My Mood'}</span>
            </button>
            <button
              type="button"
              onClick={() => openModal('CREATE_POST')}
              className="inline-flex items-center gap-1.5 px-4 py-2.5 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold hover:opacity-95 transition-opacity"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Post in {config.title}</span>
            </button>
          </div>
        </div>
      </div>

      {/* Posts in Active Mood Zone */}
      <div className="space-y-4 max-w-xl mx-auto pt-2">
        <h3 className="text-xs font-bold text-[var(--vybe-text-muted)] uppercase tracking-widest">
          Recent {config.title} Moments
        </h3>
        {zonePosts.length === 0 ? (
          <VybeEmptyState
            icon={Plus}
            title={`No ${config.title} moments yet`}
            subtitle={`Share the first post matching the ${config.title} zone.`}
            actionLabel="Be First to Share"
            onAction={() => openModal('CREATE_POST')}
          />
        ) : (
          zonePosts.map(p => <PostCard key={p.id} post={p} />)
        )}
      </div>
    </div>
  );
};
