import React from 'react';
import {
  Home, Search, MessageCircle, User, Plus,
  Users, FolderKanban, Sparkles
} from 'lucide-react';
import { useVybe, MainTab, GrowTab } from '../context/VybeContext';

export const BottomNav: React.FC = () => {
  const {
    appMode,
    selectedTab, setSelectedTab,
    growTab, setGrowTab,
    openModal, conversations
  } = useVybe();

  const totalUnread = conversations.reduce((acc, c) => acc + c.unreadCount, 0);

  if (appMode === 'GROW') {
    const growItems: { tab: GrowTab; label: string; icon: React.FC<{ className?: string }> }[] = [
      { tab: 'HOME', label: 'Home', icon: Home },
      { tab: 'CIRCLE', label: 'Circle', icon: Users },
      { tab: 'PROJECTS', label: 'Projects', icon: FolderKanban },
      { tab: 'OPPORTUNITIES', label: 'Opportunities', icon: Sparkles },
      { tab: 'PROFILE', label: 'Profile', icon: User },
    ];

    return (
      <nav className="fixed bottom-0 inset-x-0 z-40 bg-[var(--vybe-nav-bg)] backdrop-blur-xl border-t border-[var(--vybe-border)] py-2 px-3 transition-colors">
        <div className="max-w-2xl mx-auto flex items-center justify-around">
          {growItems.map(item => {
            const Icon = item.icon;
            const isSelected = growTab === item.tab;
            return (
              <button
                key={item.tab}
                onClick={() => setGrowTab(item.tab)}
                className={`flex flex-col items-center justify-center min-w-[56px] py-1 px-2 rounded-xl transition-colors ${
                  isSelected
                    ? 'text-[var(--vybe-accent)]'
                    : 'text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)]'
                }`}
              >
                <Icon className="w-5 h-5" />
                <span className={`text-[10px] mt-1 ${isSelected ? 'font-bold' : 'font-medium'}`}>
                  {item.label}
                </span>
              </button>
            );
          })}
        </div>
      </nav>
    );
  }

  const socialLeft: { tab: MainTab; label: string; icon: React.FC<{ className?: string }> }[] = [
    { tab: 'FEED', label: 'Feed', icon: Home },
    { tab: 'EXPLORE', label: 'Explore', icon: Search },
  ];

  const socialRight: { tab: MainTab; label: string; icon: React.FC<{ className?: string }> }[] = [
    { tab: 'INBOX', label: 'Inbox', icon: MessageCircle },
    { tab: 'PROFILE', label: 'Profile', icon: User },
  ];

  const renderSocialBtn = (item: { tab: MainTab; label: string; icon: React.FC<{ className?: string }> }) => {
    const Icon = item.icon;
    const isSelected = selectedTab === item.tab;
    return (
      <button
        key={item.tab}
        onClick={() => setSelectedTab(item.tab)}
        className={`flex flex-col items-center justify-center min-w-[56px] py-1 px-2 rounded-xl transition-colors ${
          isSelected
            ? 'text-[var(--vybe-accent)]'
            : 'text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)]'
        }`}
      >
        <div className="relative">
          <Icon className="w-5 h-5" />
          {item.tab === 'INBOX' && totalUnread > 0 && (
            <span className="absolute -top-1.5 -right-2 px-1.5 py-0.2 rounded-full bg-rose-500 text-[9px] text-white keep-white font-bold leading-tight">
              {totalUnread > 9 ? '9+' : totalUnread}
            </span>
          )}
        </div>
        <span className={`text-[10px] mt-1 ${isSelected ? 'font-bold' : 'font-medium'}`}>
          {item.label}
        </span>
      </button>
    );
  };

  return (
    <nav className="fixed bottom-0 inset-x-0 z-40 bg-[var(--vybe-nav-bg)] backdrop-blur-xl border-t border-[var(--vybe-border)] py-1.5 px-3 transition-colors">
      <div className="max-w-2xl mx-auto flex items-center justify-around">
        {socialLeft.map(renderSocialBtn)}

        <button
          onClick={() => openModal('CREATE_POST')}
          title="Create Post or Story"
          className="w-11 h-11 rounded-full bg-[var(--vybe-accent)] text-white keep-white shadow-md hover:opacity-95 active:scale-95 transition-all flex items-center justify-center"
        >
          <Plus className="w-5 h-5 stroke-[2.5]" />
        </button>

        {socialRight.map(renderSocialBtn)}
      </div>
    </nav>
  );
};
