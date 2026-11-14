import React, { useState } from 'react';
import { ShieldCheck, X, Check, Send, AlertCircle, Loader2 } from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { OfficialBadge } from '../components/Badges';
import { submitVerificationRequestToSupabase } from '../services/supabaseService';

const BADGE_REQUEST_MAP: Record<
  'VERIFIED_BLUE' | 'VIP_GOLD' | 'SHOP_GOLD' | 'WORKER_GREEN',
  { requestType: 'VERIFIED' | 'VIP' | 'SHOP' | 'WORKER'; category: string }
> = {
  VERIFIED_BLUE: { requestType: 'VERIFIED', category: 'Creator' },
  VIP_GOLD: { requestType: 'VIP', category: 'VIP' },
  SHOP_GOLD: { requestType: 'SHOP', category: 'Business' },
  WORKER_GREEN: { requestType: 'WORKER', category: 'Staff' }
};

export const VerificationCenterModal: React.FC = () => {
  const { closeModal, currentUser, requireAuth } = useVybe();
  const [selectedBadge, setSelectedBadge] = useState<'VERIFIED_BLUE' | 'VIP_GOLD' | 'SHOP_GOLD' | 'WORKER_GREEN'>('VERIFIED_BLUE');
  const [submitted, setSubmitted] = useState(false);
  const [portfolioLink, setPortfolioLink] = useState('');
  const [reason, setReason] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitError(null);
    if (!requireAuth('Sign in to submit a verification application.') || !currentUser) return;
    if (!portfolioLink.trim() || !reason.trim()) return;

    setIsSubmitting(true);
    const meta = BADGE_REQUEST_MAP[selectedBadge];
    const res = await submitVerificationRequestToSupabase({
      userId: currentUser.id,
      requestType: meta.requestType,
      fullName: currentUser.displayName || currentUser.username,
      category: meta.category,
      statement: reason.trim(),
      supportingLink: portfolioLink.trim()
    });
    setIsSubmitting(false);

    if (!res.ok) {
      setSubmitError(res.error || 'Could not submit verification application.');
      return;
    }

    setSubmitted(true);
    setTimeout(() => {
      closeModal();
    }, 2200);
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-fade-in"
      onClick={closeModal}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Verification Center"
        onClick={(e) => e.stopPropagation()}
        className="relative w-full max-w-lg max-h-[90vh] overflow-y-auto bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-3xl shadow-2xl p-6 text-[var(--vybe-text)] no-scrollbar space-y-5"
      >
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-[var(--vybe-border)]">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-xl bg-[var(--vybe-accent-soft)] text-[var(--vybe-accent)] border border-[var(--vybe-border)]">
              <ShieldCheck className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold brand-font text-[var(--vybe-text)]">Verification Center</h2>
              <p className="text-xs text-[var(--vybe-text-secondary)]">
                Authentic badge credentials for creators & businesses
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={closeModal}
            aria-label="Close verification center"
            className="p-2 text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)] hover:bg-[var(--vybe-elevated)] rounded-lg"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {submitted ? (
          <div className="p-8 text-center space-y-3 bg-[var(--vybe-elevated)] border border-emerald-500/30 rounded-2xl">
            <div className="w-12 h-12 rounded-full bg-emerald-500/20 text-emerald-500 flex items-center justify-center mx-auto">
              <Check className="w-6 h-6 stroke-[3]" />
            </div>
            <h3 className="font-bold text-[var(--vybe-text)] text-base">Application Submitted</h3>
            <p className="text-xs text-[var(--vybe-text-secondary)] max-w-xs mx-auto">
              Our review team will inspect your credentials in the VYBE Admin Command Center.
            </p>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            {/* Badges Choice */}
            <div className="space-y-2">
              <label className="text-xs font-semibold text-[var(--vybe-text-secondary)]">
                Select Desired Badge
              </label>
              <div className="grid grid-cols-2 gap-2">
                {[
                  { id: 'VERIFIED_BLUE', name: 'VYBE Verified', desc: 'Public identity confirmed', badge: 'VERIFIED_BLUE' },
                  { id: 'VIP_GOLD', name: 'VYBE VIP', desc: 'Prominent creator recognition', badge: 'VIP_GOLD' },
                  { id: 'SHOP_GOLD', name: 'Verified Shop', desc: 'Artisan storefront badge', badge: 'SHOP_GOLD' },
                  { id: 'WORKER_GREEN', name: 'VYBE Staff', desc: 'Platform operations team', badge: 'WORKER_GREEN' },
                ].map(b => (
                  <div
                    key={b.id}
                    onClick={() => setSelectedBadge(b.id as any)}
                    className={`p-3 rounded-2xl border cursor-pointer transition-all ${
                      selectedBadge === b.id
                        ? 'bg-[var(--vybe-accent-soft)] border-[var(--vybe-accent)] shadow-sm'
                        : 'bg-[var(--vybe-elevated)] border-[var(--vybe-border)] hover:border-[var(--vybe-accent)]/50'
                    }`}
                  >
                    <div className="flex items-center gap-2">
                      <OfficialBadge badge={b.badge as any} size="sm" />
                      <span className="font-bold text-xs text-[var(--vybe-text)]">{b.name}</span>
                    </div>
                    <p className="text-[10px] text-[var(--vybe-text-secondary)] mt-1">{b.desc}</p>
                  </div>
                ))}
              </div>
            </div>

            <div>
              <label className="text-xs font-semibold text-[var(--vybe-text-secondary)] block mb-1">
                Portfolio, Website or Social Proof Link
              </label>
              <input
                type="url"
                value={portfolioLink}
                onChange={(e) => setPortfolioLink(e.target.value)}
                placeholder="https://instagram.com/yourhandle or website"
                className="w-full px-3.5 py-2.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs text-[var(--vybe-text)] placeholder-[var(--vybe-text-muted)] focus:outline-none focus:border-[var(--vybe-accent)]"
              />
            </div>

            <div>
              <label className="text-xs font-semibold text-[var(--vybe-text-secondary)] block mb-1">
                Why should your account receive this badge?
              </label>
              <textarea
                rows={3}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="Mention your achievements, audience, or business details..."
                className="w-full p-3.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs text-[var(--vybe-text)] placeholder-[var(--vybe-text-muted)] focus:outline-none focus:border-[var(--vybe-accent)] resize-none"
              />
            </div>

            {submitError && (
              <div className="p-2.5 rounded-xl bg-red-500/15 border border-red-500/40 text-red-400 text-xs flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{submitError}</span>
              </div>
            )}

            <button
              type="submit"
              disabled={isSubmitting || !portfolioLink.trim() || !reason.trim()}
              className="w-full py-3 bg-[var(--vybe-accent)] disabled:opacity-40 text-white keep-white font-bold text-xs rounded-xl shadow-sm flex items-center justify-center gap-2 hover:opacity-95"
            >
              {isSubmitting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
              <span>{isSubmitting ? 'Submitting Application...' : 'Submit Verification Review'}</span>
            </button>
          </form>
        )}
      </div>
    </div>
  );
};
