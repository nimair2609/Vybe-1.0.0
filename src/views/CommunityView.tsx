import React from 'react';
import { Users, Plus, Check, Compass, Shield } from 'lucide-react';
import { useVybe } from '../context/VybeContext';

export const CommunityView: React.FC = () => {
  const { communities, toggleJoinCommunity, openModal } = useVybe();

  return (
    <div className="w-full max-w-3xl mx-auto px-4 py-6 pb-28 space-y-6">
      
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <div className="p-2 rounded-xl bg-[#8B5CF6]/10 text-[#8B5CF6] border border-[#8B5CF6]/20">
              <Users className="w-5 h-5" />
            </div>
            <h1 className="text-xl font-black brand-font text-white">VYBE Communities & Clubs</h1>
          </div>
          <p className="text-xs text-gray-400 mt-1">
            Interest spaces for builders, musicians, artists & mindfulness explorers.
          </p>
        </div>
      </div>

      {/* Communities Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {communities.map(comm => (
          <div 
            key={comm.id}
            className="rounded-2xl bg-[#101322] border border-[#262E4A] overflow-hidden shadow-xl hover:border-[#8B5CF6]/40 transition-all flex flex-col"
          >
            {/* Banner */}
            <div className="h-28 w-full relative">
              <img 
                src={comm.bannerUrl} 
                alt={comm.name} 
                className="w-full h-full object-cover"
              />
              <div className="absolute inset-0 bg-gradient-to-t from-[#101322] via-transparent to-transparent" />
            </div>

            {/* Info */}
            <div className="p-4 pt-0 -mt-6 relative flex-1 flex flex-col justify-between">
              <div>
                <div className="flex items-center justify-between">
                  <img 
                    src={comm.avatarUrl} 
                    alt={comm.name}
                    className="w-14 h-14 rounded-2xl object-cover border-2 border-[#101322] shadow-lg"
                  />
                  <button
                    onClick={() => toggleJoinCommunity(comm.id)}
                    className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all shadow ${
                      comm.isJoined 
                        ? 'bg-[#1A1F36] text-gray-300 border border-[#262E4A] hover:bg-red-500/20 hover:text-red-300' 
                        : 'bg-gradient-to-r from-[#00C6FF] to-[#8B5CF6] text-white hover:opacity-95'
                    }`}
                  >
                    {comm.isJoined ? 'Joined ✓' : '+ Join Club'}
                  </button>
                </div>

                <div className="mt-2.5">
                  <h3 className="font-bold text-white text-base">{comm.name}</h3>
                  <span className="text-[10px] text-[#00C6FF] font-semibold uppercase tracking-wider">
                    {comm.category} • {comm.membersCount.toLocaleString()} members
                  </span>
                  <p className="text-xs text-gray-300 mt-1.5 leading-relaxed">
                    {comm.description}
                  </p>
                </div>
              </div>

              {/* Tags */}
              <div className="flex flex-wrap gap-1.5 mt-4 pt-3 border-t border-[#262E4A]/60">
                {comm.tags.map(t => (
                  <span key={t} className="text-[10px] px-2 py-0.5 rounded-md bg-[#060714] text-gray-300 border border-[#262E4A]">
                    #{t}
                  </span>
                ))}
              </div>

            </div>
          </div>
        ))}
      </div>

    </div>
  );
};
