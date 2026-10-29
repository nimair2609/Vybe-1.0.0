import React, { useState } from 'react';
import { 
  Sparkles, Bell, Shield, Volume2, VolumeX, 
  ChevronDown, Moon, RefreshCw, Feather
} from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { MOOD_DEFINITIONS, MoodType } from '../types';

export const Navbar: React.FC = () => {
  const { 
    currentUser, setCurrentUserMood, openModal, 
    hasAvailableUpdate, isPlayingAudio, currentAudioTrack, 
    togglePlayAudio
  } = useVybe();

  const [showMoodMenu, setShowMoodMenu] = useState(false);

  const currentMoodConfig = currentUser.currentMood ? MOOD_DEFINITIONS[currentUser.currentMood] : null;

  return (
    <header className="sticky top-0 z-40 bg-[#060714]/90 backdrop-blur-xl border-b border-[#262E4A]/60 px-4 py-2.5 transition-all">
      <div className="max-w-7xl mx-auto flex items-center justify-between gap-3">
        
        {/* Brand & Logo */}
        <div className="flex items-center gap-2.5">
          <div className="relative flex items-center justify-center w-9 h-9 rounded-xl bg-gradient-to-br from-[#00C6FF] via-[#8B5CF6] to-[#D946EF] p-[1.5px] shadow-lg shadow-[#00C6FF]/20 group cursor-pointer">
            <div className="w-full h-full bg-[#060714] rounded-[10px] flex items-center justify-center overflow-hidden">
              <img 
                src="/brand/vybe_v_mark.png" 
                alt="VYBE Mark" 
                className="w-5 h-5 object-contain"
                onError={(e) => {
                  (e.target as HTMLElement).style.display = 'none';
                }}
              />
              <span className="font-extrabold text-transparent bg-clip-text bg-gradient-to-r from-[#00C6FF] to-[#D946EF] text-sm">
                V
              </span>
            </div>
          </div>
          <div className="flex flex-col">
            <span className="text-xl font-black tracking-wider brand-font bg-gradient-to-r from-white via-slate-100 to-[#00C6FF] bg-clip-text text-transparent">
              VYBE
            </span>
            <span className="text-[9px] uppercase tracking-widest text-[#8B5CF6] font-semibold -mt-1">
              Social Pulse
            </span>
          </div>
        </div>

        {/* Center: Mood Zone Active Status */}
        <div className="relative">
          <button
            onClick={() => setShowMoodMenu(!showMoodMenu)}
            className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-[#101322] border border-[#262E4A] hover:border-[#8B5CF6]/50 transition-all text-xs font-medium text-gray-200 shadow-sm"
          >
            <span>{currentMoodConfig ? currentMoodConfig.emoji : '✨'}</span>
            <span className="hidden sm:inline">Mood: </span>
            <strong className="text-white">{currentMoodConfig ? currentMoodConfig.title : 'Set Mood'}</strong>
            <ChevronDown className="w-3.5 h-3.5 text-gray-400" />
          </button>

          {showMoodMenu && (
            <div className="absolute left-1/2 -translate-x-1/2 mt-2 w-64 bg-[#101322] border border-[#262E4A] rounded-2xl shadow-2xl p-2 z-50 animate-in fade-in zoom-in-95">
              <div className="px-3 py-2 border-b border-[#262E4A] text-left">
                <p className="text-xs font-bold text-white">How are you feeling right now?</p>
                <p className="text-[10px] text-gray-400">Updates your profile & public vibe</p>
              </div>
              <div className="grid grid-cols-2 gap-1.5 p-1.5">
                {(Object.keys(MOOD_DEFINITIONS) as MoodType[]).map(m => {
                  const item = MOOD_DEFINITIONS[m];
                  const isSelected = currentUser.currentMood === m;
                  return (
                    <button
                      key={m}
                      onClick={() => {
                        setCurrentUserMood(m);
                        setShowMoodMenu(false);
                      }}
                      className={`flex items-center gap-2 p-2 rounded-xl text-left text-xs transition-all ${
                        isSelected 
                          ? 'bg-gradient-to-r ' + item.gradient + ' text-white font-bold shadow-md' 
                          : 'hover:bg-[#1A1F36] text-gray-300'
                      }`}
                    >
                      <span className="text-base">{item.emoji}</span>
                      <span className="truncate">{item.title}</span>
                    </button>
                  );
                })}
              </div>
            </div>
          )}
        </div>

        {/* Right Action Icons */}
        <div className="flex items-center gap-2">
          
          {/* Audio Player Button */}
          {currentAudioTrack && (
            <button
              onClick={() => togglePlayAudio(currentAudioTrack)}
              title={isPlayingAudio ? 'Pause Ambient Sound' : 'Play Sound'}
              className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-full bg-[#101322] border border-[#262E4A] text-xs text-[#00C6FF] hover:border-[#00C6FF]/50 transition-colors"
            >
              {isPlayingAudio ? (
                <>
                  <Volume2 className="w-3.5 h-3.5 animate-pulse" />
                  <span className="hidden md:inline truncate max-w-[100px]">{currentAudioTrack}</span>
                </>
              ) : (
                <VolumeX className="w-3.5 h-3.5 text-gray-400" />
              )}
            </button>
          )}

          {/* Spiritual Mode Shortcut */}
          <button
            onClick={() => openModal('SPIRITUAL_MODE')}
            title="Spiritual Mode & Breathwork"
            className="p-2 rounded-xl bg-[#101322] border border-[#262E4A] text-gray-300 hover:text-[#2DD4BF] hover:border-[#2DD4BF]/50 transition-colors"
          >
            <Feather className="w-4 h-4" />
          </button>

          {/* GitHub Software Updates Button with update indicator */}
          <button
            onClick={() => openModal('GITHUB_UPDATES')}
            title="Software Updates & Releases"
            className={`relative p-2 rounded-xl border transition-all ${
              hasAvailableUpdate 
                ? 'bg-[#8B5CF6]/20 border-[#8B5CF6] text-[#00C6FF] shadow-md shadow-[#8B5CF6]/30 animate-pulse' 
                : 'bg-[#101322] border-[#262E4A] text-gray-300 hover:text-white hover:border-[#8B5CF6]'
            }`}
          >
            <RefreshCw className="w-4 h-4" />
            {hasAvailableUpdate && (
              <span className="absolute -top-1 -right-1 w-2.5 h-2.5 rounded-full bg-[#00C6FF] ring-2 ring-[#060714]" />
            )}
          </button>

          {/* Notifications */}
          <button
            onClick={() => openModal('NOTIFICATIONS')}
            title="Notifications"
            className="relative p-2 rounded-xl bg-[#101322] border border-[#262E4A] text-gray-300 hover:text-white hover:border-[#8B5CF6] transition-colors"
          >
            <Bell className="w-4 h-4" />
            <span className="absolute top-1.5 right-1.5 w-2 h-2 rounded-full bg-[#D946EF]" />
          </button>

          {/* Admin Console Shortcut */}
          <button
            onClick={() => openModal('ADMIN_CONSOLE')}
            title="Admin & Verification Console"
            className="p-2 rounded-xl bg-[#101322] border border-[#262E4A] text-gray-400 hover:text-[#FFB703] hover:border-[#FFB703]/40 transition-colors"
          >
            <Shield className="w-4 h-4" />
          </button>
        </div>

      </div>
    </header>
  );
};
