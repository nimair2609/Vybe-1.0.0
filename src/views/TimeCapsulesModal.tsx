import React, { useState } from 'react';
import { Clock, Lock, Unlock, Plus, X, Send, Sparkles } from 'lucide-react';
import { useVybe } from '../context/VybeContext';

export const TimeCapsulesModal: React.FC = () => {
  const { timeCapsules, createTimeCapsule, closeModal } = useVybe();

  const [isCreating, setIsCreating] = useState(false);
  const [title, setTitle] = useState('');
  const [sealedMessage, setSealedMessage] = useState('');
  const [recipient, setRecipient] = useState('To Myself (1 Year From Now)');
  const [theme, setTheme] = useState('Career Milestone & Dreams');

  const handleCreate = (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !sealedMessage.trim()) return;

    createTimeCapsule({
      title: title.trim(),
      sealedMessage: sealedMessage.trim(),
      recipientSummary: recipient,
      categoryTheme: theme,
      unlockDateFormatted: 'Dec 31, 2027',
      unlockTimestampMs: Date.now() + 1000 * 60 * 60 * 24 * 365,
    });
    setIsCreating(false);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/85 backdrop-blur-xl animate-fade-in">
      <div className="relative w-full max-w-xl max-h-[90vh] overflow-y-auto bg-[#0a0f1d] border border-[#262E4A] rounded-3xl shadow-2xl p-6 text-white no-scrollbar space-y-6">
        
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-[#262E4A]">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-xl bg-[#00C6FF]/10 text-[#00C6FF] border border-[#00C6FF]/30">
              <Clock className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold brand-font text-white">Digital Time Capsules</h2>
              <p className="text-xs text-gray-400">Lock messages to future dates for yourself or friend crews</p>
            </div>
          </div>
          <button onClick={closeModal} className="p-2 text-gray-400 hover:text-white rounded-lg">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Action Toggle */}
        <div className="flex items-center justify-between">
          <span className="text-xs font-bold text-gray-300 uppercase tracking-wider">
            {isCreating ? 'Seal a New Capsule' : 'Your Sealed Memories'}
          </span>
          <button
            onClick={() => setIsCreating(!isCreating)}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-gradient-to-r from-[#00C6FF] to-[#8B5CF6] text-white text-xs font-bold shadow hover:opacity-95"
          >
            {isCreating ? 'View Capsules' : '+ Seal New Capsule'}
          </button>
        </div>

        {isCreating ? (
          <form onSubmit={handleCreate} className="space-y-4 p-5 rounded-2xl bg-[#101322] border border-[#262E4A]">
            <div>
              <label className="text-xs font-semibold text-gray-300 block mb-1">Capsule Title</label>
              <input 
                type="text"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="e.g. Letter to 25yo Me: Keep Chasing Music"
                className="w-full px-3.5 py-2 rounded-xl bg-[#060714] border border-[#262E4A] text-xs text-white focus:outline-none focus:border-[#00C6FF]"
              />
            </div>

            <div>
              <label className="text-xs font-semibold text-gray-300 block mb-1">Recipient</label>
              <input 
                type="text"
                value={recipient}
                onChange={(e) => setRecipient(e.target.value)}
                placeholder="e.g. To Myself, or @priya_k, or College Gang"
                className="w-full px-3.5 py-2 rounded-xl bg-[#060714] border border-[#262E4A] text-xs text-white focus:outline-none focus:border-[#00C6FF]"
              />
            </div>

            <div>
              <label className="text-xs font-semibold text-gray-300 block mb-1">Sealed Message (Hidden until unlocked)</label>
              <textarea
                rows={4}
                value={sealedMessage}
                onChange={(e) => setSealedMessage(e.target.value)}
                placeholder="Write your secret hopes, memories, or promises..."
                className="w-full p-3.5 rounded-xl bg-[#060714] border border-[#262E4A] text-xs text-white focus:outline-none focus:border-[#00C6FF] resize-none"
              />
            </div>

            <button
              type="submit"
              disabled={!title.trim() || !sealedMessage.trim()}
              className="w-full py-2.5 bg-gradient-to-r from-[#00C6FF] to-[#8B5CF6] text-white font-bold text-xs rounded-xl shadow-lg flex items-center justify-center gap-2"
            >
              <Lock className="w-4 h-4" />
              <span>Seal Digital Time Capsule</span>
            </button>
          </form>
        ) : (
          <div className="space-y-3">
            {timeCapsules.map(cap => (
              <div 
                key={cap.id}
                className="p-4 rounded-2xl bg-[#101322] border border-[#262E4A] space-y-2 hover:border-[#00C6FF]/40 transition-all"
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    {cap.isUnlocked ? (
                      <Unlock className="w-4 h-4 text-[#22C55E]" />
                    ) : (
                      <Lock className="w-4 h-4 text-[#FFB703]" />
                    )}
                    <h3 className="font-bold text-sm text-white">{cap.title}</h3>
                  </div>
                  <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${
                    cap.isUnlocked ? 'bg-emerald-500/20 text-emerald-300' : 'bg-amber-500/20 text-amber-300'
                  }`}>
                    {cap.isUnlocked ? 'Unlocked!' : cap.unlockDateFormatted}
                  </span>
                </div>

                <div className="text-[11px] text-gray-400 flex items-center gap-2">
                  <span>Recipient: <strong className="text-gray-200">{cap.recipientSummary}</strong></span>
                  <span>•</span>
                  <span>{cap.categoryTheme}</span>
                </div>

                <div className="p-3 rounded-xl bg-[#060714] border border-[#262E4A] text-xs leading-relaxed text-gray-200">
                  {cap.isUnlocked ? (
                    <p className="italic">"{cap.sealedMessage}"</p>
                  ) : (
                    <div className="flex items-center justify-center py-3 gap-2 text-gray-500 text-xs">
                      <Lock className="w-4 h-4 text-gray-500" />
                      <span>🔒 This message is encrypted & sealed until {cap.unlockDateFormatted}</span>
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}

      </div>
    </div>
  );
};
