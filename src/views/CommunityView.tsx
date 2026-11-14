import React, { useState, useMemo } from 'react';
import { Users, Plus, Check, Search } from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { VybeEmptyState } from '../components/VybeDesignSystem';
import { VybeAvatar } from '../components/OptimizedImage';

export const CommunityView: React.FC = () => {
  const { communities, toggleJoinCommunity } = useVybe();
  const [search, setSearch] = useState('');

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return communities;
    return communities.filter(
      c =>
        c.name.toLowerCase().includes(q) ||
        c.category.toLowerCase().includes(q) ||
        c.description.toLowerCase().includes(q)
    );
  }, [communities, search]);

  return (
    <div className="w-full max-w-4xl mx-auto px-4 py-6 pb-28 lg:pb-12 space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-xl bg-[var(--vybe-accent-soft)] text-[var(--vybe-accent)] border border-[var(--vybe-border)]">
              <Users className="w-5 h-5" />
            </div>
            <div>
              <h1 className="text-xl font-extrabold brand-font text-[var(--vybe-text)]">
                VYBE Communities & Clubs
              </h1>
              <p className="text-xs text-[var(--vybe-text-secondary)]">
                Interest spaces for builders, musicians, artists & mindfulness explorers.
              </p>
            </div>
          </div>
        </div>

        <div className="relative w-full sm:w-64">
          <Search className="w-4 h-4 text-[var(--vybe-text-muted)] absolute left-3.5 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Filter communities..."
            className="w-full pl-9 pr-3.5 py-2 bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-xl text-xs text-[var(--vybe-text)] placeholder-[var(--vybe-text-muted)] focus:outline-none focus:border-[var(--vybe-accent)]"
          />
        </div>
      </div>

      {/* Communities Grid */}
      {filtered.length === 0 ? (
        <VybeEmptyState
          icon={Users}
          title="No matching communities"
          subtitle="Try another keyword to explore active VYBE circles."
          actionLabel={search ? 'Clear Filter' : undefined}
          onAction={search ? () => setSearch('') : undefined}
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {filtered.map(comm => (
            <div
              key={comm.id}
              className="rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] overflow-hidden hover:border-[var(--vybe-accent)]/60 transition-all flex flex-col"
            >
              {/* Banner */}
              <div className="h-28 w-full relative">
                {comm.bannerUrl ? (
                  <img
                    src={comm.bannerUrl}
                    alt={comm.name}
                    className="w-full h-full object-cover"
                  />
                ) : (
                  <div className="w-full h-full bg-gradient-to-br from-[#1e1b4b] via-[#312e81] to-[#0c4a6e]" />
                )}
                <div className="absolute inset-0 bg-gradient-to-t from-[var(--vybe-surface)] via-transparent to-transparent" />
              </div>

              {/* Info */}
              <div className="p-4 pt-0 -mt-6 relative flex-1 flex flex-col justify-between">
                <div>
                  <div className="flex items-center justify-between gap-2">
                    <VybeAvatar
                      src={comm.avatarUrl}
                      name={comm.name}
                      size={56}
                      className="w-14 h-14 rounded-2xl border-2 border-[var(--vybe-surface)] shadow-md"
                    />
                    <button
                      type="button"
                      onClick={() => toggleJoinCommunity(comm.id)}
                      className={`inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl text-xs font-bold transition-all ${
                        comm.isJoined
                          ? 'bg-[var(--vybe-elevated)] text-[var(--vybe-text)] border border-[var(--vybe-border)] hover:border-red-500/60 hover:text-red-500'
                          : 'bg-[var(--vybe-accent)] text-white keep-white hover:opacity-95'
                      }`}
                    >
                      {comm.isJoined ? (
                        <>
                          <Check className="w-3.5 h-3.5" />
                          <span>Joined</span>
                        </>
                      ) : (
                        <>
                          <Plus className="w-3.5 h-3.5" />
                          <span>Join Club</span>
                        </>
                      )}
                    </button>
                  </div>

                  <div className="mt-2.5">
                    <h3 className="font-bold text-[var(--vybe-text)] text-base">{comm.name}</h3>
                    <span className="text-[10px] text-[var(--vybe-accent)] font-semibold uppercase tracking-wider">
                      {comm.category} · {comm.membersCount.toLocaleString()} members
                    </span>
                    <p className="text-xs text-[var(--vybe-text-secondary)] mt-1.5 leading-relaxed">
                      {comm.description}
                    </p>
                  </div>
                </div>

                {/* Tags */}
                <div className="flex flex-wrap gap-1.5 mt-4 pt-3 border-t border-[var(--vybe-border)]">
                  {comm.tags.map(t => (
                    <span
                      key={t}
                      className="text-[10px] px-2 py-0.5 rounded-md bg-[var(--vybe-elevated)] text-[var(--vybe-text-secondary)] border border-[var(--vybe-border)]"
                    >
                      #{t}
                    </span>
                  ))}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
