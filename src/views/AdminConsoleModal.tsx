import React, { useState } from 'react';
import { Shield, X, Check, AlertTriangle, Users, FileText, Sparkles, UserCheck } from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { OfficialBadge } from '../components/Badges';

export const AdminConsoleModal: React.FC = () => {
  const { closeModal, allUsers, posts } = useVybe();

  const [pendingBadges, setPendingBadges] = useState([
    { id: 'app_01', user: allUsers[1], requestedBadge: 'VERIFIED_BLUE', notes: 'Indie musician with 25k spotify monthly listeners.' },
    { id: 'app_02', user: allUsers[2], requestedBadge: 'VIP_GOLD', notes: 'Cinematic visual director & featured reel artist.' },
  ]);

  const handleApprove = (id: string) => {
    setPendingBadges(prev => prev.filter(p => p.id !== id));
  };

  const handleReject = (id: string) => {
    setPendingBadges(prev => prev.filter(p => p.id !== id));
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/85 backdrop-blur-xl animate-fade-in">
      <div className="relative w-full max-w-2xl max-h-[90vh] overflow-y-auto bg-[#0a0f1d] border border-[#262E4A] rounded-3xl shadow-2xl p-6 text-white no-scrollbar space-y-6">
        
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-[#262E4A]">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-xl bg-[#FFB703]/10 text-[#FFB703] border border-[#FFB703]/30">
              <Shield className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold brand-font text-white">VYBE Admin Console</h2>
              <p className="text-xs text-gray-400">Platform moderation & verification approvals</p>
            </div>
          </div>
          <button onClick={closeModal} className="p-2 text-gray-400 hover:text-white rounded-lg">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Overview Stats */}
        <div className="grid grid-cols-4 gap-2.5 text-center">
          <div className="p-3 rounded-2xl bg-[#101322] border border-[#262E4A]">
            <span className="block text-lg font-bold text-[#00C6FF]">1,480+</span>
            <span className="text-[10px] text-gray-400">Registered Users</span>
          </div>
          <div className="p-3 rounded-2xl bg-[#101322] border border-[#262E4A]">
            <span className="block text-lg font-bold text-[#8B5CF6]">{posts.length}</span>
            <span className="text-[10px] text-gray-400">Active Moments</span>
          </div>
          <div className="p-3 rounded-2xl bg-[#101322] border border-[#262E4A]">
            <span className="block text-lg font-bold text-[#FFB703]">{pendingBadges.length}</span>
            <span className="text-[10px] text-gray-400">Pending Review</span>
          </div>
          <div className="p-3 rounded-2xl bg-[#101322] border border-[#262E4A]">
            <span className="block text-lg font-bold text-[#22C55E]">100%</span>
            <span className="text-[10px] text-gray-400">System Health</span>
          </div>
        </div>

        {/* Pending Verification Requests */}
        <div className="space-y-3">
          <h3 className="text-xs font-bold text-gray-300 uppercase tracking-wider flex items-center gap-2">
            <UserCheck className="w-4 h-4 text-[#FFB703]" />
            Official Badge Verification Queue
          </h3>

          {pendingBadges.length === 0 ? (
            <p className="text-xs text-gray-400 text-center py-6 bg-[#101322] rounded-2xl border border-[#262E4A]">
              ✓ All verification badge applications have been reviewed!
            </p>
          ) : (
            pendingBadges.map(item => (
              <div 
                key={item.id} 
                className="p-4 rounded-2xl bg-[#101322] border border-[#262E4A] flex flex-col sm:flex-row sm:items-center justify-between gap-3"
              >
                <div className="flex items-center gap-3">
                  <img 
                    src={item.user.avatarUrl} 
                    alt={item.user.displayName}
                    className="w-10 h-10 rounded-full object-cover border border-[#262E4A]"
                  />
                  <div>
                    <div className="flex items-center gap-1.5">
                      <span className="font-bold text-sm text-white">{item.user.displayName}</span>
                      <OfficialBadge badge={item.requestedBadge as any} size="sm" />
                    </div>
                    <span className="text-[11px] text-gray-400 font-mono">@{item.user.username}</span>
                    <p className="text-xs text-gray-300 mt-1">{item.notes}</p>
                  </div>
                </div>

                <div className="flex items-center gap-2 shrink-0">
                  <button
                    onClick={() => handleReject(item.id)}
                    className="px-3 py-1.5 rounded-xl bg-red-500/10 border border-red-500/30 text-red-300 text-xs font-bold hover:bg-red-500/20"
                  >
                    Decline
                  </button>
                  <button
                    onClick={() => handleApprove(item.id)}
                    className="px-3.5 py-1.5 rounded-xl bg-gradient-to-r from-[#00C6FF] to-[#8B5CF6] text-white text-xs font-bold shadow hover:opacity-95"
                  >
                    Grant Badge ✓
                  </button>
                </div>
              </div>
            ))
          )}
        </div>

      </div>
    </div>
  );
};
