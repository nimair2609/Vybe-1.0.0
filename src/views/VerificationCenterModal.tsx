import React, { useState } from 'react';
import { ShieldCheck, X, Check, Star, ShoppingBag, Shield, Send } from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { OfficialBadge } from '../components/Badges';

export const VerificationCenterModal: React.FC = () => {
  const { closeModal } = useVybe();
  const [selectedBadge, setSelectedBadge] = useState<'VERIFIED_BLUE' | 'VIP_GOLD' | 'SHOP_GOLD' | 'WORKER_GREEN'>('VERIFIED_BLUE');
  const [submitted, setSubmitted] = useState(false);
  const [portfolioLink, setPortfolioLink] = useState('');
  const [reason, setReason] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitted(true);
    setTimeout(() => {
      closeModal();
    }, 2500);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/85 backdrop-blur-xl animate-fade-in">
      <div className="relative w-full max-w-lg max-h-[90vh] overflow-y-auto bg-[#0a0f1d] border border-[#262E4A] rounded-3xl shadow-2xl p-6 text-white no-scrollbar space-y-5">
        
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-[#262E4A]">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-xl bg-[#00C6FF]/10 text-[#00C6FF] border border-[#00C6FF]/30">
              <ShieldCheck className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold brand-font text-white">Verification Center</h2>
              <p className="text-xs text-gray-400">Authentic badge credentials for creators & businesses</p>
            </div>
          </div>
          <button onClick={closeModal} className="p-2 text-gray-400 hover:text-white rounded-lg">
            <X className="w-5 h-5" />
          </button>
        </div>

        {submitted ? (
          <div className="p-8 text-center space-y-3 bg-[#101322] border border-emerald-500/30 rounded-2xl">
            <div className="w-12 h-12 rounded-full bg-emerald-500/20 text-emerald-400 flex items-center justify-center mx-auto">
              <Check className="w-6 h-6 stroke-[3]" />
            </div>
            <h3 className="font-bold text-white text-base">Application Submitted!</h3>
            <p className="text-xs text-gray-300 max-w-xs mx-auto">
              Our review committee will inspect your credentials and notification will appear in your console.
            </p>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            
            {/* Badges Choice */}
            <div className="space-y-2">
              <label className="text-xs font-semibold text-gray-300">Select Desired Badge</label>
              <div className="grid grid-cols-2 gap-2">
                {[
                  { id: 'VERIFIED_BLUE', name: 'VYBE Verified', desc: 'Public identity confirmed', badge: 'VERIFIED_BLUE' },
                  { id: 'VIP_GOLD', name: 'VYBE VIP', desc: 'Prominent creator recognition', badge: 'VIP_GOLD' },
                  { id: 'SHOP_GOLD', name: 'Verified Shop', desc: 'Artisan storefront / store badge', badge: 'SHOP_GOLD' },
                  { id: 'WORKER_GREEN', name: 'VYBE Staff', desc: 'Platform operations team', badge: 'WORKER_GREEN' },
                ].map(b => (
                  <div
                    key={b.id}
                    onClick={() => setSelectedBadge(b.id as any)}
                    className={`p-3 rounded-2xl border cursor-pointer transition-all ${
                      selectedBadge === b.id 
                        ? 'bg-[#101322] border-[#00C6FF] shadow-lg ring-1 ring-[#00C6FF]' 
                        : 'bg-[#060714] border-[#262E4A] hover:border-gray-500'
                    }`}
                  >
                    <div className="flex items-center gap-2">
                      <OfficialBadge badge={b.badge as any} size="sm" />
                      <span className="font-bold text-xs text-white">{b.name}</span>
                    </div>
                    <p className="text-[10px] text-gray-400 mt-1">{b.desc}</p>
                  </div>
                ))}
              </div>
            </div>

            <div>
              <label className="text-xs font-semibold text-gray-300 block mb-1">
                Portfolio, Website or Social Proof Link
              </label>
              <input 
                type="url"
                value={portfolioLink}
                onChange={(e) => setPortfolioLink(e.target.value)}
                placeholder="https://instagram.com/yourhandle or website"
                className="w-full px-3.5 py-2.5 rounded-xl bg-[#060714] border border-[#262E4A] text-xs text-white focus:outline-none focus:border-[#00C6FF]"
              />
            </div>

            <div>
              <label className="text-xs font-semibold text-gray-300 block mb-1">
                Why should your account receive this badge?
              </label>
              <textarea
                rows={3}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="Mention your achievements, audience, or business details..."
                className="w-full p-3.5 rounded-xl bg-[#060714] border border-[#262E4A] text-xs text-white focus:outline-none focus:border-[#00C6FF] resize-none"
              />
            </div>

            <button
              type="submit"
              disabled={!portfolioLink.trim() || !reason.trim()}
              className="w-full py-3 bg-gradient-to-r from-[#00C6FF] to-[#8B5CF6] disabled:opacity-40 text-white font-bold text-xs rounded-xl shadow-lg flex items-center justify-center gap-2"
            >
              <Send className="w-4 h-4" />
              <span>Submit Verification Review</span>
            </button>

          </form>
        )}

      </div>
    </div>
  );
};
