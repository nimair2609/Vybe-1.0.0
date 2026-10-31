import React from 'react';
import {
  Home, PlaySquare, Compass, MessageCircle,
  User, Plus, Users
} from 'lucide-react';
import { useVybe, MainTab } from '../context/VybeContext';

export const BottomNav: React.FC = () => {
  const { selectedTab, setSelectedTab, openModal, conversations } = useVybe();

  const totalUnread = conversations.reduce((acc, c) => acc + c.unreadCount, 0);

  const navItems: { tab: MainTab; label: string; icon: React.FC<{ className?: string }> }[] = [
    { tab: 'FEED', label: 'Feed', icon: Home },
    { tab: 'EXPLORE', label: 'Explore', icon: Compass },
    { tab: 'VYBES', label: 'Reels', icon: PlaySquare },
    { tab: 'INBOX', label: 'Inbox', icon: MessageCircle },
    { tab: 'COMMUNITY', label: 'Clubs', icon: Users },
    { tab: 'PROFILE', label: 'Profile', icon: User },
  ];

  return (
    <nav className="fixed bottom-0 inset-x-0 z-40 bg-[#060714]/95 backdrop-blur-2xl border-t border-[#262E4A]/80 py-1.5 px-3">
      <div className="max-w-2xl mx-auto flex items-center justify-around">
        {navItems.map((item, idx) => {
          const Icon = item.icon;
          const isSelected = selectedTab === item.tab;

          // Insert "+ Create" FAB in the middle
          if (idx === 3) {
            return (
              <React.Fragment key="fab_plus">
                <button
                  onClick={() => openModal('CREATE_POST')}
                  className="relative -top-3 p-3 rounded-2xl bg-gradient-to-tr from-[#00C6FF] via-[#8B5CF6] to-[#D946EF] text-white shadow-lg shadow-[#8B5CF6]/40 hover:scale-105 active:scale-95 transition-transform"
                  title="Create Post or Story"
                >
                  <Plus className="w-5 h-5 stroke-[2.5]" />
                </button>

                <button
                  key={item.tab}
                  onClick={() => setSelectedTab(item.tab)}
                  className={`flex flex-col items-center justify-center p-1.5 rounded-xl transition-all ${
                    isSelected ? 'text-[#00C6FF]' : 'text-gray-400 hover:text-gray-200'
                  }`}
                >
                  <div className="relative">
                    <Icon className="w-5 h-5" />
                    {item.tab === 'INBOX' && totalUnread > 0 && (
                      <span className="absolute -top-1 -right-2 px-1.5 py-0.2 rounded-full bg-[#D946EF] text-[10px] text-white font-bold">
                        {totalUnread}
                      </span>
                    )}
                  </div>
                  <span className={`text-[10px] mt-1 ${isSelected ? 'font-bold text-white' : 'font-medium'}`}>
                    {item.label}
                  </span>
                </button>
              </React.Fragment>
            );
          }

          return (
            <button
              key={item.tab}
              onClick={() => setSelectedTab(item.tab)}
              className={`flex flex-col items-center justify-center p-1.5 rounded-xl transition-all ${
                isSelected ? 'text-[#00C6FF]' : 'text-gray-400 hover:text-gray-200'
              }`}
            >
              <div className="relative">
                <Icon className="w-5 h-5" />
                {item.tab === 'INBOX' && totalUnread > 0 && (
                  <span className="absolute -top-1 -right-2 px-1.5 py-0.2 rounded-full bg-[#D946EF] text-[10px] text-white font-bold">
                    {totalUnread}
                  </span>
                )}
              </div>
              <span className={`text-[10px] mt-1 ${isSelected ? 'font-bold text-white' : 'font-medium'}`}>
                {item.label}
              </span>
            </button>
          );
        })}
      </div>
    </nav>
  );
};
