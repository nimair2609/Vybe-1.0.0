import React, { useState } from 'react';
import { 
  Compass, MapPin, Clock, Send, CheckCircle2, 
  Sparkles, Plus, AlertCircle, Eye, Shield 
} from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { CrossedPathEncounter } from '../types';

export const CrossedPathsView: React.FC = () => {
  const { crossedPaths, sendConnectionRequest, openModal } = useVybe();
  const [filterType, setFilterType] = useState<string>('ALL');

  const filtered = crossedPaths.filter(cp => {
    if (filterType === 'ALL') return true;
    return cp.type === filterType;
  });

  return (
    <div className="w-full max-w-2xl mx-auto px-4 py-6 pb-28 space-y-6">
      
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <div className="p-2 rounded-xl bg-[#00C6FF]/10 text-[#00C6FF] border border-[#00C6FF]/20">
              <Compass className="w-5 h-5" />
            </div>
            <h1 className="text-xl font-black brand-font text-white">Crossed Paths</h1>
          </div>
          <p className="text-xs text-gray-400 mt-1">
            Did you cross eyes with someone at a cafe, gig or transit stop? Discover missed connections.
          </p>
        </div>
        <button
          onClick={() => openModal('CREATE_CROSSED_PATH')}
          className="flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl bg-gradient-to-r from-[#00C6FF] to-[#8B5CF6] text-white text-xs font-bold shadow-lg shadow-[#00C6FF]/20 hover:opacity-95 transition-all shrink-0"
        >
          <Plus className="w-4 h-4 stroke-[3]" />
          <span>Post Encounter</span>
        </button>
      </div>

      {/* Safety Notice */}
      <div className="p-3.5 rounded-2xl bg-[#101322] border border-[#262E4A] flex items-start gap-3 text-xs text-gray-300">
        <Shield className="w-4 h-4 text-[#22C55E] shrink-0 mt-0.5" />
        <p className="leading-relaxed">
          <strong>Privacy Preserved:</strong> Exact GPS coordinates are never displayed. Encounters expire automatically after 72 hours and mutual approval is required before direct messaging.
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
            onClick={() => setFilterType(tab.id)}
            className={`px-3 py-1.5 rounded-full text-xs font-semibold whitespace-nowrap transition-colors ${
              filterType === tab.id
                ? 'bg-gradient-to-r from-[#00C6FF] to-[#8B5CF6] text-white shadow'
                : 'bg-[#101322] border border-[#262E4A] text-gray-400 hover:text-white'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Encounters List */}
      <div className="space-y-4">
        {filtered.map(item => (
          <div 
            key={item.id}
            className="p-5 rounded-2xl bg-[#101322] border border-[#262E4A] hover:border-[#00C6FF]/40 shadow-xl space-y-3 transition-all"
          >
            <div className="flex items-start justify-between gap-3">
              <div>
                <span className="text-[10px] font-bold uppercase tracking-wider text-[#00C6FF] px-2 py-0.5 rounded-full bg-[#00C6FF]/10 border border-[#00C6FF]/20">
                  {item.type.replace('_', ' ')}
                </span>
                <h3 className="font-bold text-white text-base mt-1.5">{item.title}</h3>
              </div>
              <div className="flex items-center gap-1 text-[11px] text-gray-400 shrink-0">
                <Clock className="w-3.5 h-3.5 text-[#FFB703]" />
                <span>{item.expiresHoursRemaining}h remaining</span>
              </div>
            </div>

            <p className="text-xs text-gray-200 leading-relaxed">
              "{item.description}"
            </p>

            <div className="pt-2 flex flex-wrap items-center gap-4 text-xs text-gray-400 border-t border-[#262E4A]/60">
              <div className="flex items-center gap-1 text-gray-300">
                <MapPin className="w-3.5 h-3.5 text-[#00C6FF]" />
                <span className="font-medium">{item.broadLocation}</span>
                {item.venueOrEventName && (
                  <span className="text-gray-400">({item.venueOrEventName})</span>
                )}
              </div>
              <div>
                <span>{item.encounterDate}</span>
              </div>
              <div>
                <span>Posted: <strong className="text-gray-300">{item.isAnonymous ? 'Anonymous' : item.poster.displayName}</strong></span>
              </div>
            </div>

            {/* Request Action */}
            <div className="pt-2 flex items-center justify-between">
              <span className="text-[11px] text-gray-400">
                {item.potentialMatchesCount} potential connection{item.potentialMatchesCount > 1 ? 's' : ''} in area
              </span>
              {item.hasSentRequest ? (
                <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-300 text-xs font-bold">
                  <CheckCircle2 className="w-4 h-4" />
                  <span>Request Sent ({item.requestStatus})</span>
                </div>
              ) : (
                <button
                  onClick={() => sendConnectionRequest(item.id)}
                  className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl bg-gradient-to-r from-[#00C6FF] to-[#8B5CF6] text-white text-xs font-bold shadow hover:opacity-95 transition-opacity"
                >
                  <Send className="w-3.5 h-3.5" />
                  <span>I Was There • Connect</span>
                </button>
              )}
            </div>
          </div>
        ))}
      </div>

    </div>
  );
};
