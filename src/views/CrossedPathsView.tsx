import React, { useState } from 'react';
import {
  Compass, MapPin, Clock, Send, CheckCircle2, Plus, Shield
} from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { VybeEmptyState } from '../components/VybeDesignSystem';

export const CrossedPathsView: React.FC = () => {
  const { crossedPaths, sendConnectionRequest, openModal } = useVybe();
  const [filterType, setFilterType] = useState<string>('ALL');

  const filtered = crossedPaths.filter(cp => {
    if (filterType === 'ALL') return true;
    return cp.type === filterType;
  });

  return (
    <div className="w-full max-w-3xl mx-auto px-4 py-6 pb-28 lg:pb-12 space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="flex items-center gap-2.5">
          <div className="p-2 rounded-xl bg-[var(--vybe-accent-soft)] text-[var(--vybe-accent)] border border-[var(--vybe-border)]">
            <Compass className="w-5 h-5" />
          </div>
          <div>
            <h1 className="text-xl font-extrabold brand-font text-[var(--vybe-text)]">Crossed Paths</h1>
            <p className="text-xs text-[var(--vybe-text-secondary)]">
              Missed connections from cafes, live gigs, campuses & transit stops.
            </p>
          </div>
        </div>
        <button
          type="button"
          onClick={() => openModal('CREATE_CROSSED_PATH')}
          className="flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold shadow-sm hover:opacity-95 transition-opacity shrink-0"
        >
          <Plus className="w-4 h-4 stroke-[2.5]" />
          <span>Post Encounter</span>
        </button>
      </div>

      {/* Safety Notice */}
      <div className="p-3.5 rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] flex items-start gap-3 text-xs text-[var(--vybe-text-secondary)]">
        <Shield className="w-4 h-4 text-emerald-500 shrink-0 mt-0.5" />
        <p className="leading-relaxed">
          <strong className="text-[var(--vybe-text)]">Privacy Preserved:</strong> Exact GPS coordinates are never displayed. Encounters expire automatically after 72 hours and mutual approval is required before direct messaging.
        </p>
      </div>

      {/* Filter Tabs */}
      <div className="flex items-center gap-2 overflow-x-auto no-scrollbar">
        {[
          { id: 'ALL', label: 'All Encounters' },
          { id: 'MISSED_CONNECTION', label: 'Missed Connections' },
          { id: 'VENUE_EVENT', label: 'Venue & Events' },
          { id: 'BROAD_NEARBY', label: 'Broad Nearby' },
        ].map(tab => (
          <button
            key={tab.id}
            type="button"
            onClick={() => setFilterType(tab.id)}
            className={`px-3.5 py-1.5 rounded-full text-xs font-semibold whitespace-nowrap transition-colors border ${
              filterType === tab.id
                ? 'bg-[var(--vybe-accent)] text-white keep-white border-transparent'
                : 'bg-[var(--vybe-surface)] border-[var(--vybe-border)] text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)]'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Encounters List */}
      {filtered.length === 0 ? (
        <VybeEmptyState
          icon={Compass}
          title="No encounters in this filter"
          subtitle="Share a moment you noticed nearby to spark an intentional connection."
          actionLabel="Post Encounter"
          onAction={() => openModal('CREATE_CROSSED_PATH')}
        />
      ) : (
        <div className="space-y-4">
          {filtered.map(item => (
            <div
              key={item.id}
              className="p-5 rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)]/60 space-y-3 transition-colors"
            >
              <div className="flex items-start justify-between gap-3">
                <div>
                  <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--vybe-accent)] px-2.5 py-0.5 rounded-full bg-[var(--vybe-accent-soft)] border border-[var(--vybe-border)]">
                    {item.type.replace(/_/g, ' ')}
                  </span>
                  <h3 className="font-bold text-[var(--vybe-text)] text-base mt-2">{item.title}</h3>
                </div>
                <div className="flex items-center gap-1 text-[11px] text-[var(--vybe-text-secondary)] shrink-0">
                  <Clock className="w-3.5 h-3.5 text-amber-500" />
                  <span>{item.expiresHoursRemaining}h remaining</span>
                </div>
              </div>

              <p className="text-xs text-[var(--vybe-text)] leading-relaxed">
                &ldquo;{item.description}&rdquo;
              </p>

              <div className="pt-2.5 flex flex-wrap items-center gap-4 text-xs text-[var(--vybe-text-secondary)] border-t border-[var(--vybe-border)]">
                <div className="flex items-center gap-1">
                  <MapPin className="w-3.5 h-3.5 text-[var(--vybe-accent)]" />
                  <span className="font-medium text-[var(--vybe-text)]">{item.broadLocation}</span>
                  {item.venueOrEventName && (
                    <span className="text-[var(--vybe-text-muted)]">({item.venueOrEventName})</span>
                  )}
                </div>
                <span>{item.encounterDate}</span>
                <span>
                  Posted:{' '}
                  <strong className="text-[var(--vybe-text)]">
                    {item.isAnonymous ? 'Anonymous' : item.poster.displayName}
                  </strong>
                </span>
              </div>

              {/* Request Action */}
              <div className="pt-2 flex items-center justify-between gap-3 flex-wrap">
                <span className="text-[11px] text-[var(--vybe-text-muted)]">
                  {item.potentialMatchesCount} potential connection{item.potentialMatchesCount > 1 ? 's' : ''} in area
                </span>
                {item.hasSentRequest ? (
                  <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-emerald-500/12 border border-emerald-500/30 text-emerald-600 dark:text-emerald-400 text-xs font-bold">
                    <CheckCircle2 className="w-4 h-4" />
                    <span>Request Sent ({item.requestStatus})</span>
                  </div>
                ) : (
                  <button
                    type="button"
                    onClick={() => sendConnectionRequest(item.id)}
                    className="flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold hover:opacity-95 transition-opacity"
                  >
                    <Send className="w-3.5 h-3.5" />
                    <span>I Was There · Connect</span>
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
