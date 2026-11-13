import React, { useState } from 'react';
import {
  Bell, Shield, Volume2, VolumeX, ChevronDown,
  Moon, Sun, RefreshCw, Feather, PlaySquare
} from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { MOOD_DEFINITIONS, MoodType } from '../types';
import { isVerifiedAdminSession } from '../services/supabaseService';
import { VybeBrandMark, VybeModeSwitch } from './VybeDesignSystem';

export const Navbar: React.FC = () => {
  const {
    currentUser, setCurrentUserMood, openModal,
    hasAvailableUpdate, isPlayingAudio, currentAudioTrack,
    togglePlayAudio, themeMode, toggleThemeMode,
    isDbConnected, refreshFromDatabase,
    appMode, setAppMode, setSelectedTab, setGrowTab
  } = useVybe();

  const [isRefreshing, setIsRefreshing] = useState(false);
  const [showMoodMenu, setShowMoodMenu] = useState(false);

  const currentMoodConfig = currentUser.currentMood ? MOOD_DEFINITIONS[currentUser.currentMood] : null;
  const isGrow = appMode === 'GROW';

  return (
    <header className="sticky top-0 z-40 bg-[var(--vybe-nav-bg)] backdrop-blur-xl border-b border-[var(--vybe-border)] px-4 py-2.5 transition-colors">
      <div className="max-w-7xl mx-auto flex items-center justify-between gap-2 sm:gap-3">
        {/* Left: Unified Brand Mark */}
        <VybeBrandMark
          isGrow={isGrow}
          onClick={() => {
            if (isGrow) setGrowTab('HOME');
            else setSelectedTab('FEED');
          }}
        />

        {/* Center: Unified Social | Grow Mode Pill Switch */}
        <div className="flex items-center gap-2">
          <VybeModeSwitch mode={appMode} onChange={setAppMode} compact />

          {!isGrow && (
            <div className="relative hidden md:block">
              <button
                onClick={() => setShowMoodMenu(!showMoodMenu)}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] transition-colors text-xs font-semibold text-[var(--vybe-text-secondary)]"
              >
                <span>{currentMoodConfig ? currentMoodConfig.emoji : '✨'}</span>
                <span className="text-[var(--vybe-text)]">{currentMoodConfig ? currentMoodConfig.title : 'Set Mood'}</span>
                <ChevronDown className="w-3.5 h-3.5 text-[var(--vybe-text-muted)]" />
              </button>

              {showMoodMenu && (
                <div className="absolute left-1/2 -translate-x-1/2 mt-2 w-64 bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-2xl shadow-2xl p-2 z-50">
                  <div className="px-3 py-2 border-b border-[var(--vybe-border)] text-left">
                    <p className="text-xs font-bold text-[var(--vybe-text)]">How are you feeling?</p>
                    <p className="text-[11px] text-[var(--vybe-text-secondary)]">Updates your status & feed tone</p>
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
                          className={`flex items-center gap-2 p-2 rounded-xl text-left text-xs transition-colors ${
                            isSelected
                              ? 'bg-[var(--vybe-accent)] text-white keep-white font-bold'
                              : 'hover:bg-[var(--vybe-elevated)] text-[var(--vybe-text-secondary)]'
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
          )}
        </div>

        {/* Right: Clean Utilities + Notifications + Profile Avatar */}
        <div className="flex items-center gap-1.5 sm:gap-2">
          {!isGrow && (
            <button
              onClick={() => setSelectedTab('VYBES')}
              title="Open VYBE Reels"
              className="hidden sm:flex items-center gap-1.5 px-2.5 py-1.5 rounded-full bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs font-semibold text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-accent)] hover:border-[var(--vybe-accent)] transition-colors"
            >
              <PlaySquare className="w-3.5 h-3.5" />
              <span>Reels</span>
            </button>
          )}

          {/* Live Sync Indicator */}
          <button
            onClick={async () => {
              setIsRefreshing(true);
              await refreshFromDatabase();
              setTimeout(() => setIsRefreshing(false), 700);
            }}
            title={isDbConnected ? 'Synced with live backend' : 'Connecting...'}
            className={`hidden lg:flex items-center gap-1.5 px-2.5 py-1.5 rounded-full border text-xs font-semibold transition-colors ${
              isDbConnected
                ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-500'
                : 'bg-amber-500/10 border-amber-500/30 text-amber-500'
            }`}
          >
            <span className={`w-2 h-2 rounded-full ${isDbConnected ? 'bg-emerald-500' : 'bg-amber-500'}`} />
            <span>{isRefreshing ? 'Syncing' : (isDbConnected ? 'Live' : 'Sync')}</span>
            <RefreshCw className={`w-3 h-3 ${isRefreshing ? 'animate-spin' : ''}`} />
          </button>

          {currentAudioTrack && (
            <button
              onClick={() => togglePlayAudio(currentAudioTrack)}
              title={isPlayingAudio ? 'Pause Sound' : 'Play Sound'}
              className="p-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[var(--vybe-accent)] transition-colors"
            >
              {isPlayingAudio ? <Volume2 className="w-4 h-4" /> : <VolumeX className="w-4 h-4 text-[var(--vybe-text-muted)]" />}
            </button>
          )}

          {/* Theme Mode Toggle */}
          <button
            onClick={toggleThemeMode}
            title={themeMode === 'dark' ? 'Switch to Light Mode' : 'Switch to Dark Mode'}
            className="p-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)] transition-colors"
          >
            {themeMode === 'dark' ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
          </button>

          {/* Spiritual Sanctuary */}
          <button
            onClick={() => openModal('SPIRITUAL_MODE')}
            title="Spiritual Sanctuary"
            className="hidden sm:flex p-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[var(--vybe-text-secondary)] hover:text-teal-500 transition-colors"
          >
            <Feather className="w-4 h-4" />
          </button>

          {/* Software Updates */}
          <button
            onClick={() => openModal('GITHUB_UPDATES')}
            title="Updates"
            className={`relative hidden sm:flex p-2 rounded-xl border transition-colors ${
              hasAvailableUpdate
                ? 'bg-[var(--vybe-accent-soft)] border-[var(--vybe-accent)] text-[var(--vybe-accent)]'
                : 'bg-[var(--vybe-elevated)] border-[var(--vybe-border)] text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)]'
            }`}
          >
            <RefreshCw className="w-4 h-4" />
            {hasAvailableUpdate && (
              <span className="absolute -top-1 -right-1 w-2.5 h-2.5 rounded-full bg-[var(--vybe-accent)]" />
            )}
          </button>

          {/* Notifications */}
          <button
            onClick={() => openModal('NOTIFICATIONS')}
            title="Notifications"
            className="relative p-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)] transition-colors"
          >
            <Bell className="w-4 h-4" />
          </button>

          {isVerifiedAdminSession(currentUser) && (
            <button
              onClick={() => openModal('ADMIN_CONSOLE')}
              title="Admin Console"
              className="p-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-amber-500 transition-colors"
            >
              <Shield className="w-4 h-4" />
            </button>
          )}

          {/* Profile Avatar Shortcut */}
          <button
            onClick={() => {
              if (isGrow) setGrowTab('PROFILE');
              else setSelectedTab('PROFILE');
            }}
            title="Profile"
            className="w-8 h-8 rounded-full overflow-hidden border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] transition-colors shrink-0"
          >
            <img
              src={currentUser.avatarUrl}
              alt={currentUser.displayName}
              className="w-full h-full object-cover"
            />
          </button>
        </div>
      </div>
    </header>
  );
};
