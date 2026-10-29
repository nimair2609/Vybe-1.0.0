import React, { useState, useEffect } from 'react';
import { Feather, X, Wind, Volume2, Plus, Check } from 'lucide-react';
import { useVybe } from '../context/VybeContext';

export const SpiritualModal: React.FC = () => {
  const { closeModal, reflections, intentions, addIntention, toggleIntention } = useVybe();

  const [phase, setPhase] = useState<'Inhale Peace' | 'Hold Stillness' | 'Release Tension' | 'Rest in Calm'>('Inhale Peace');
  const [secondsLeft, setSecondsLeft] = useState(4);
  const [newIntentionInput, setNewIntentionInput] = useState('');

  // 4-4-4-4 Box Breathing cycle
  useEffect(() => {
    const timer = setInterval(() => {
      setSecondsLeft(prev => {
        if (prev > 1) return prev - 1;
        setPhase(current => {
          if (current === 'Inhale Peace') return 'Hold Stillness';
          if (current === 'Hold Stillness') return 'Release Tension';
          if (current === 'Release Tension') return 'Rest in Calm';
          return 'Inhale Peace';
        });
        return 4;
      });
    }, 1000);
    return () => clearInterval(timer);
  }, []);

  const handleAdd = (e: React.FormEvent) => {
    e.preventDefault();
    if (newIntentionInput.trim()) {
      addIntention(newIntentionInput.trim());
      setNewIntentionInput('');
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/85 backdrop-blur-xl animate-fade-in">
      <div className="relative w-full max-w-xl max-h-[90vh] overflow-y-auto bg-[#0a0f1d] border border-[#262E4A] rounded-3xl shadow-2xl p-6 text-white no-scrollbar space-y-6">
        
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-[#262E4A]">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-xl bg-[#2DD4BF]/10 text-[#2DD4BF] border border-[#2DD4BF]/30">
              <Feather className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold brand-font text-white">Spiritual Sanctuary & Breath</h2>
              <p className="text-xs text-gray-400">Box breathing, mindful reflections & intentions</p>
            </div>
          </div>
          <button onClick={closeModal} className="p-2 text-gray-400 hover:text-white rounded-lg">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* 4-4-4-4 Box Breathing Orb Visualizer */}
        <div className="p-6 rounded-2xl bg-[#060714] border border-[#262E4A] text-center space-y-4 relative overflow-hidden">
          <div className="flex items-center justify-center py-6">
            <div className="relative flex items-center justify-center">
              {/* Outer pulsing ring */}
              <div className={`w-36 h-36 rounded-full transition-all duration-1000 flex items-center justify-center ${
                phase === 'Inhale Peace' 
                  ? 'scale-125 bg-gradient-to-tr from-[#00C6FF]/30 to-[#2DD4BF]/40 ring-8 ring-[#2DD4BF]/30'
                  : phase === 'Hold Stillness'
                    ? 'scale-125 bg-gradient-to-tr from-[#8B5CF6]/30 to-[#00C6FF]/40 ring-8 ring-[#8B5CF6]/30'
                    : phase === 'Release Tension'
                      ? 'scale-90 bg-gradient-to-tr from-[#2DD4BF]/20 to-[#060714] ring-4 ring-[#2DD4BF]/20'
                      : 'scale-90 bg-gradient-to-tr from-[#1A1F36] to-[#060714] ring-2 ring-white/10'
              }`}>
                {/* Core counter */}
                <div className="flex flex-col items-center">
                  <span className="text-3xl font-black font-mono text-white">{secondsLeft}</span>
                  <span className="text-[10px] tracking-wider text-gray-300 uppercase font-semibold">Seconds</span>
                </div>
              </div>
            </div>
          </div>

          <div>
            <h3 className="text-base font-bold text-[#2DD4BF] tracking-wide">{phase}</h3>
            <p className="text-xs text-gray-400 mt-0.5">
              Follow the rhythmic 4-4-4-4 breath to reset your nervous system
            </p>
          </div>
        </div>

        {/* Daily Reflection Quote */}
        {reflections[0] && (
          <div className="p-5 rounded-2xl bg-[#101322] border border-[#262E4A] space-y-2">
            <span className="text-[10px] font-bold uppercase tracking-wider text-[#FFB703]">
              Daily Reflection • {reflections[0].theme}
            </span>
            <p className="text-xs text-gray-100 italic leading-relaxed">
              "{reflections[0].quote}"
            </p>
            <p className="text-[11px] text-gray-400 font-semibold text-right">
              — {reflections[0].traditionOrAuthor}
            </p>
          </div>
        )}

        {/* Intentions & Gratitude Tracker */}
        <div className="space-y-3">
          <h4 className="text-xs font-bold text-gray-300 uppercase tracking-wider">
            Daily Intentions & Gratitude
          </h4>

          <form onSubmit={handleAdd} className="flex gap-2">
            <input 
              type="text"
              value={newIntentionInput}
              onChange={(e) => setNewIntentionInput(e.target.value)}
              placeholder="Set a mindful intention for today..."
              className="flex-1 px-3.5 py-2 rounded-xl bg-[#060714] border border-[#262E4A] text-xs text-white focus:outline-none focus:border-[#2DD4BF]"
            />
            <button
              type="submit"
              className="px-3.5 py-2 rounded-xl bg-[#2DD4BF] text-[#060714] font-bold text-xs"
            >
              Add
            </button>
          </form>

          <div className="space-y-2">
            {intentions.map(item => (
              <div 
                key={item.id}
                onClick={() => toggleIntention(item.id)}
                className="flex items-center gap-3 p-3 rounded-xl bg-[#101322] border border-[#262E4A] cursor-pointer hover:border-[#2DD4BF]/50 transition-colors"
              >
                <div className={`w-5 h-5 rounded-lg flex items-center justify-center border transition-all ${
                  item.isFulfilled 
                    ? 'bg-[#2DD4BF] border-[#2DD4BF] text-[#060714]' 
                    : 'border-gray-500 bg-[#060714]'
                }`}>
                  {item.isFulfilled && <Check className="w-3.5 h-3.5 stroke-[3]" />}
                </div>
                <span className={`text-xs flex-1 ${item.isFulfilled ? 'line-through text-gray-400' : 'text-gray-200 font-medium'}`}>
                  {item.text}
                </span>
                <span className="text-[10px] text-gray-500">{item.timestamp}</span>
              </div>
            ))}
          </div>
        </div>

      </div>
    </div>
  );
};
