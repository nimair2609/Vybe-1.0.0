import React, { useState, useEffect, useRef } from 'react';
import {
  Bell, Shield, Volume2, VolumeX, ChevronDown, Moon, Sun,
  RefreshCw, Feather, PlaySquare, Home, Compass, Sparkles,
  MapPin, Users, MessageCircle, Plus, Settings, FolderKanban, User
} from 'lucide-react';
import { useVybe, MainTab, GrowTab } from '../context/VybeContext';
import { MOOD_DEFINITIONS, MoodType } from '../types';
import { isVerifiedAdminSession } from '../services/supabaseService';
import { VybeBrandMark, VybeModeSwitch } from './VybeDesignSystem';

export const Navbar: React.FC = () => {
  const {
    currentUser, setCurrentUserMood, openModal,
    hasAvailableUpdate, isPlayingAudio, currentAudioTrack,
    togglePlayAudio, themeMode, toggleThemeMode,
    isDbConnected, refreshFromDatabase,
    appMode, setAppMode,
    selectedTab, setSelectedTab,
    growTab, setGrowTab,
    conversations
  } = useVybe();

  const [isRefreshing, setIsRefreshing] = useState(false);
  const [showMoodMenu, setShowMoodMenu] = useState(false);
  const moodMenuRef = useRef<HTMLDivElement | null>(null);

  const currentMoodConfig = currentUser.currentMood ? MOOD_DEFINITIONS[currentUser.currentMood] : null;
  const isGrow = appMode === 'GROW';
  const totalUnread = conversations.reduce((acc, c) => acc + c.unreadCount, 0);

  useEffect(() => {
    if (!showMoodMenu) return;
    const handleOutside = (e: MouseEvent) => {
      if (moodMenuRef.current && !moodMenuRef.current.contains(e.target as Node)) {
        setShowMoodMenu(false);
      }
    };
    document.addEventListener('mousedown', handleOutside);
    return () => document.removeEventListener('mousedown', handleOutside);
  }, [showMoodMenu]);

  const socialNavItems: { tab: MainTab; label: string; icon: React.FC<{ className?: string }>; badge?: number }[] = [
    { tab: 'FEED', label: 'Feed', icon: Home },
    { tab: 'EXPLORE', label: 'Explore', icon: Compass },
    { tab: 'VYBES', label: 'Reels', icon: PlaySquare },
    { tab: 'MOOD_ZONES', label: 'Moods', icon: Sparkles },
    { tab: 'CROSSED_PATHS', label: 'Paths', icon: MapPin },
    { tab: 'COMMUNITY', label: 'Circles', icon: Users },
    { tab: 'INBOX', label: 'Inbox', icon: MessageCircle, badge: totalUnread },
  ];

  const growNavItems: { tab: GrowTab; label: string; icon: React.FC<{ className?: string }> }[] = [
    { tab: 'HOME', label: 'Home', icon: Home },
    { tab: 'CIRCLE', label: 'Circle', icon: Users },
    { tab: 'PROJECTS', label: 'Projects', icon: FolderKanban },
    { tab: 'OPPORTUNITIES', label: 'Opportunities', icon: Sparkles },
    { tab: 'PROFILE', label: 'Profile', icon: User },
  ];

  return (
    <header className="sticky top-0 z-40 bg-[var(--vybe-nav-bg)] backdrop-blur-xl border-b border-[var(--vybe-border)] transition-colors">
      <div className="max-w-7xl mx-auto px-3 sm:px-5 h-14 flex items-center justify-between gap-2 sm:gap-3">
        {/* Left: Brand Mark & Experience Mode Switch */}
        <div className="flex items-center gap-2.5 sm:gap-3.5 shrink-0">
          <VybeBrandMark
            isGrow={isGrow}
            onClick={() => {
              if (isGrow) setGrowTab('HOME');
              else setSelectedTab('FEED');
            }}
          />
          <VybeModeSwitch mode={appMode} onChange={setAppMode} compact />
        </div>

        {/* Center: Desktop Primary Navigation */}
        <nav aria-label="Primary Navigation" className="hidden lg:flex items-center gap-1">
          {!isGrow
            ? socialNavItems.map(item => {
                const Icon = item.icon;
                const isActive = selectedTab === item.tab;
                return (
                  <button
                    key={item.tab}
                    type="button"
                    onClick={() => setSelectedTab(item.tab)}
                    className={`relative flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold transition-colors ${
                      isActive
                        ? 'bg-[var(--vybe-accent-soft)] text-[var(--vybe-accent)] font-bold'
                        : 'text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)] hover:bg-[var(--vybe-elevated)]'
                    }`}
                  >
                    <Icon className="w-4 h-4 shrink-0" />
                    <span>{item.label}</span>
                    {item.badge !== undefined && item.badge > 0 && (
                      <span className="ml-0.5 px-1.5 py-0.2 rounded-full bg-rose-500 text-white keep-white text-[10px] font-bold leading-tight">
                        {item.badge > 9 ? '9+' : item.badge}
                      </span>
                    )}
                  </button>
                );
              })
            : growNavItems.map(item => {
                const Icon = item.icon;
                const isActive = growTab === item.tab;
                return (
                  <button
                    key={item.tab}
                    type="button"
                    onClick={() => setGrowTab(item.tab)}
                    className={`flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl text-xs font-semibold transition-colors ${
                      isActive
                        ? 'bg-[var(--vybe-accent-soft)] text-[var(--vybe-accent)] font-bold'
                        : 'text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)] hover:bg-[var(--vybe-elevated)]'
                    }`}
                  >
                    <Icon className="w-4 h-4 shrink-0" />
                    <span>{item.label}</span>
                  </button>
                );
              })}
        </nav>

        {/* Right: Utilities, Actions & User Profile */}
        <div className="flex items-center gap-1.5 shrink-0">
          {!isGrow && (
            <button
              type="button"
              onClick={() => openModal('CREATE_POST')}
              className="hidden md:inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold shadow-sm hover:opacity-95 transition-opacity"
            >
              <Plus className="w-3.5 h-3.5 stroke-[2.5]" />
              <span>Create</span>
            </button>
          )}

          {!isGrow && (
            <div ref={moodMenuRef} className="relative hidden md:block">
              <button
                type="button"
                onClick={() => setShowMoodMenu(!showMoodMenu)}
                title="Change current mood"
                className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] transition-colors text-xs font-semibold text-[var(--vybe-text-secondary)]"
              >
                {currentMoodConfig ? (
                  <span>{currentMoodConfig.emoji}</span>
                ) : (
                  <Sparkles className="w-3.5 h-3.5 text-[var(--vybe-accent)]" />
                )}
                <span className="text-[var(--vybe-text)] max-w-[84px] truncate">
                  {currentMoodConfig ? currentMoodConfig.title : 'Mood'}
                </span>
                <ChevronDown className="w-3.5 h-3.5 text-[var(--vybe-text-muted)]" />
              </button>

              {showMoodMenu && (
                <div className="absolute right-0 mt-2 w-64 bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-2xl shadow-2xl p-2 z-50">
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
                          type="button"
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

          {/* Live Sync Indicator */}
          <button
            type="button"
            onClick={async () => {
              setIsRefreshing(true);
              await refreshFromDatabase();
              setTimeout(() => setIsRefreshing(false), 700);
            }}
            title={isDbConnected ? 'Synced with live Supabase' : 'Sync with Supabase'}
            className={`hidden xl:flex items-center gap-1.5 px-2.5 py-1.5 rounded-xl border text-xs font-semibold transition-colors ${
              isDbConnected
                ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-500'
                : 'bg-amber-500/10 border-amber-500/30 text-amber-500'
            }`}
          >
            <span className={`w-1.5 h-1.5 rounded-full ${isDbConnected ? 'bg-emerald-500' : 'bg-amber-500'}`} />
            <span>{isRefreshing ? 'Syncing' : (isDbConnected ? 'Live' : 'Sync')}</span>
            <RefreshCw className={`w-3 h-3 ${isRefreshing ? 'animate-spin' : ''}`} />
          </button>

          {currentAudioTrack && (
            <button
              type="button"
              onClick={() => togglePlayAudio(currentAudioTrack)}
              title={isPlayingAudio ? 'Pause Sound' : 'Play Sound'}
              className="p-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[var(--vybe-accent)] transition-colors"
            >
              {isPlayingAudio ? <Volume2 className="w-4 h-4" /> : <VolumeX className="w-4 h-4 text-[var(--vybe-text-muted)]" />}
            </button>
          )}

          {/* Theme Mode Toggle */}
          <button
            type="button"
            onClick={toggleThemeMode}
            aria-label={themeMode === 'dark' ? 'Switch to Light Mode' : 'Switch to Dark Mode'}
            title={themeMode === 'dark' ? 'Switch to Light Mode' : 'Switch to Dark Mode'}
            className="p-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)] transition-colors"
          >
            {themeMode === 'dark' ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
          </button>

          {/* Spiritual Sanctuary */}
          <button
            type="button"
            onClick={() => openModal('SPIRITUAL_MODE')}
            aria-label="Spiritual Sanctuary"
            title="Spiritual Sanctuary"
            className="hidden sm:flex p-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[var(--vybe-text-secondary)] hover:text-teal-500 transition-colors"
          >
            <Feather className="w-4 h-4" />
          </button>

          {/* Notifications */}
          <button
            type="button"
            onClick={() => openModal('NOTIFICATIONS')}
            aria-label="Notifications"
            title="Notifications"
            className="relative p-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)] transition-colors"
          >
            <Bell className="w-4 h-4" />
            {(totalUnread > 0 || hasAvailableUpdate) && (
              <span className="absolute top-1.5 right-1.5 w-2 h-2 rounded-full bg-[var(--vybe-accent)]" />
            )}
          </button>

          {/* Settings */}
          <button
            type="button"
            onClick={() => openModal('SETTINGS')}
            aria-label="Settings"
            title="Settings, Updates & App Links"
            className="relative p-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)] transition-colors"
          >
            <Settings className="w-4 h-4" />
            {hasAvailableUpdate && (
              <span className="absolute -top-0.5 -right-0.5 w-2.5 h-2.5 rounded-full bg-[var(--vybe-accent)]" />
            )}
          </button>

          {isVerifiedAdminSession(currentUser) && (
            <button
              type="button"
              onClick={() => openModal('ADMIN_CONSOLE')}
              aria-label="Admin Console"
              title="Platform Admin Console"
              className="p-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-amber-500 transition-colors"
            >
              <Shield className="w-4 h-4" />
            </button>
          )}

          {/* Profile Avatar Shortcut */}
          <button
            type="button"
            onClick={() => {
              if (isGrow) setGrowTab('PROFILE');
              else setSelectedTab('PROFILE');
            }}
            aria-label="Open profile"
            title={currentUser.displayName}
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

      {/* Compact Secondary Mobile/Tablet Sub-Navigation Strip for Social Workspaces */}
      {!isGrow && (
        <div className="lg:hidden border-t border-[var(--vybe-border)]/70 px-3 py-1.5 overflow-x-auto no-scrollbar">
          <div className="flex items-center gap-1.5 min-w-max">
            {[
              { tab: 'FEED' as MainTab, label: 'Feed', icon: Home },
              { tab: 'VYBES' as MainTab, label: 'Reels', icon: PlaySquare },
              { tab: 'MOOD_ZONES' as MainTab, label: 'Mood Zones', icon: Sparkles },
              { tab: 'CROSSED_PATHS' as MainTab, label: 'Crossed Paths', icon: MapPin },
              { tab: 'COMMUNITY' as MainTab, label: 'Communities', icon: Users },
            ].map(item => {
              const Icon = item.icon;
              const isActive = selectedTab === item.tab;
              return (
                <button
                  key={item.tab}
                  type="button"
                  onClick={() => setSelectedTab(item.tab)}
                  className={`flex items-center gap-1.5 px-3 py-1 rounded-full text-[11px] font-semibold whitespace-nowrap transition-colors ${
                    isActive
                      ? 'bg-[var(--vybe-accent)] text-white keep-white'
                      : 'bg-[var(--vybe-elevated)] text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)]'
                  }`}
                >
                  <Icon className="w-3.5 h-3.5" />
                  <span>{item.label}</span>
                </button>
              );
            })}
          </div>
        </div>
      )}
    </header>
  );
};
