import React from 'react';
import { useVybe } from './context/VybeContext';
import { Navbar } from './components/Navbar';
import { BottomNav } from './components/BottomNav';

// Views
import { FeedView } from './views/FeedView';
import { ReelsView } from './views/ReelsView';
import { MoodZonesView } from './views/MoodZonesView';
import { CrossedPathsView } from './views/CrossedPathsView';
import { InboxView } from './views/InboxView';
import { CommunityView } from './views/CommunityView';
import { ProfileView } from './views/ProfileView';
import { ExploreView } from './views/ExploreView';

// Modals
import { GitHubUpdateModal } from './components/GitHubUpdateModal';
import { SettingsModal } from './views/SettingsModal';
import { AdminConsoleModal } from './views/AdminConsoleModal';
import { CallModal } from './views/CallModal';
import { CreatePostModal } from './views/CreatePostModal';
import { CreateStoryModal } from './views/CreateStoryModal';
import { StoryViewerModal } from './views/StoryViewerModal';
import { TimeCapsulesModal } from './views/TimeCapsulesModal';
import { CreateCrossedPathModal } from './views/CreateCrossedPathModal';
import { ShopDetailModal } from './views/ShopDetailModal';
import { SpiritualModal } from './views/SpiritualModal';
import { UnloadModal } from './views/UnloadModal';
import { VerificationCenterModal } from './views/VerificationCenterModal';
import { QrCodeModal } from './views/QrCodeModal';
import { NotificationsModal } from './views/NotificationsModal';

export const App: React.FC = () => {
  const { selectedTab, activeModal, callState, acceptCall, declineCall } = useVybe();

  const renderCurrentTab = () => {
    switch (selectedTab) {
      case 'FEED':
        return <FeedView />;
      case 'VYBES':
        return <ReelsView />;
      case 'MOOD_ZONES':
        return <MoodZonesView />;
      case 'EXPLORE':
        return <ExploreView />;
      case 'CROSSED_PATHS':
        return <CrossedPathsView />;
      case 'INBOX':
        return <InboxView />;
      case 'COMMUNITY':
        return <CommunityView />;
      case 'PROFILE':
        return <ProfileView />;
      default:
        return <FeedView />;
    }
  };

  return (
    <div className="min-h-screen bg-[#060714] text-[#F8FAFC] flex flex-col antialiased">
      
      {/* Top Navbar */}
      <Navbar />

      {/* Incoming Call Floating Banner */}
      {callState.isActive && callState.isIncoming && callState.peerUser && (
        <div className="fixed top-16 inset-x-4 z-50 max-w-md mx-auto p-4 rounded-2xl bg-[#101322] border border-[#00C6FF]/50 shadow-2xl flex items-center justify-between gap-3 animate-bounce">
          <div className="flex items-center gap-3">
            <img 
              src={callState.peerUser.avatarUrl} 
              alt={callState.peerUser.displayName} 
              className="w-10 h-10 rounded-full object-cover border border-[#00C6FF]"
            />
            <div>
              <p className="text-xs font-bold text-white">Incoming {callState.isVideo ? 'Video' : 'Voice'} Call...</p>
              <p className="text-[11px] text-gray-300">{callState.peerUser.displayName}</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={declineCall}
              className="px-3 py-1.5 rounded-xl bg-red-600 hover:bg-red-700 text-white text-xs font-bold"
            >
              Decline
            </button>
            <button
              onClick={acceptCall}
              className="px-3 py-1.5 rounded-xl bg-[#22C55E] hover:bg-emerald-600 text-white text-xs font-bold shadow"
            >
              Accept
            </button>
          </div>
        </div>
      )}

      {/* Main Tab Content */}
      <div className="flex-1 w-full">
        {renderCurrentTab()}
      </div>

      {/* Bottom Floating Navigation Dock */}
      <BottomNav />

      {/* Active Modals */}
      {activeModal === 'GITHUB_UPDATES' && <GitHubUpdateModal />}
      {activeModal === 'SETTINGS' && <SettingsModal />}
      {activeModal === 'ADMIN_CONSOLE' && <AdminConsoleModal />}
      {activeModal === 'CALL_SCREEN' && <CallModal />}
      {activeModal === 'CREATE_POST' && <CreatePostModal />}
      {activeModal === 'CREATE_STORY' && <CreateStoryModal />}
      {activeModal === 'STORY_VIEWER' && <StoryViewerModal />}
      {activeModal === 'CREATE_CAPSULE' && <TimeCapsulesModal />}
      {activeModal === 'CREATE_CROSSED_PATH' && <CreateCrossedPathModal />}
      {activeModal === 'SHOP_DETAIL' && <ShopDetailModal />}
      {activeModal === 'SPIRITUAL_MODE' && <SpiritualModal />}
      {activeModal === 'UNLOAD_ROOM' && <UnloadModal />}
      {activeModal === 'VERIFICATION_CENTER' && <VerificationCenterModal />}
      {activeModal === 'QR_CODE' && <QrCodeModal />}
      {activeModal === 'NOTIFICATIONS' && <NotificationsModal />}

    </div>
  );
};
