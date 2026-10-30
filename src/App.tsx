import React from 'react';
import { X, Lock } from 'lucide-react';
import { useVybe } from './context/VybeContext';
import { Navbar } from './components/Navbar';
import { BottomNav } from './components/BottomNav';
import { PostCard } from './components/PostCard';

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
  const {
    selectedTab,
    activeModal,
    callState,
    acceptCall,
    declineCall,
    posts,
    reels,
    currentUser,
    sharedPostModalId,
    closeSharedPostModal,
    inAppNotificationBanner,
    dismissBanner
  } = useVybe();

  const resolvedSharedPost = sharedPostModalId
    ? [...posts, ...reels].find(p => p.id === sharedPostModalId) || null
    : null;
  const isSharedPostPrivateBlocked = Boolean(
    resolvedSharedPost &&
      resolvedSharedPost.author.isPrivate &&
      resolvedSharedPost.author.id !== currentUser.id &&
      !resolvedSharedPost.author.isFollowedByMe
  );

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
    <div className="vybe-app-shell min-h-screen bg-[#060714] text-[#F8FAFC] flex flex-col antialiased transition-colors">
      {/* Top Navbar */}
      <Navbar />

      {/* In-App Confirmation Banner */}
      {inAppNotificationBanner && (
        <div className="fixed bottom-20 left-1/2 -translate-x-1/2 z-50 px-4 py-2.5 rounded-2xl bg-[#101322] border border-[#8B5CF6]/50 shadow-2xl flex items-center gap-3 text-xs font-semibold text-white">
          <span>{inAppNotificationBanner}</span>
          <button
            onClick={dismissBanner}
            className="text-[#00C6FF] font-bold hover:underline"
          >
            OK
          </button>
        </div>
      )}

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
              <p className="text-xs font-bold text-white">
                Incoming {callState.isVideo ? 'Video' : 'Voice'} Call...
              </p>
              <p className="text-[11px] text-gray-300">{callState.peerUser.displayName}</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={declineCall}
              className="px-3 py-1.5 rounded-xl bg-red-600 hover:bg-red-700 text-white keep-white text-xs font-bold"
            >
              Decline
            </button>
            <button
              onClick={acceptCall}
              className="px-3 py-1.5 rounded-xl bg-[#22C55E] hover:bg-emerald-600 text-white keep-white text-xs font-bold shadow"
            >
              Accept
            </button>
          </div>
        </div>
      )}

      {/* Main Tab Content */}
      <div className="flex-1 w-full">{renderCurrentTab()}</div>

      {/* Bottom Floating Navigation Dock */}
      <BottomNav />

      {/* Shared Post Deep-Link Modal (/post/:postId or #post_:postId) */}
      {sharedPostModalId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/85 backdrop-blur-md">
          <div className="relative w-full max-w-lg max-h-[90vh] overflow-y-auto bg-[#101322] border border-[#262E4A] rounded-3xl shadow-2xl p-4 space-y-3 no-scrollbar">
            <div className="flex items-center justify-between px-2 pb-2 border-b border-[#262E4A]">
              <h3 className="text-sm font-bold text-white">Shared Post</h3>
              <button
                onClick={closeSharedPostModal}
                className="p-1.5 rounded-full text-gray-400 hover:text-white"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {!resolvedSharedPost || isSharedPostPrivateBlocked ? (
              <div className="p-6 text-center space-y-2">
                <Lock className="w-8 h-8 text-red-400 mx-auto" />
                <p className="text-sm font-bold text-white">Post Unavailable</p>
                <p className="text-xs text-gray-400">
                  {isSharedPostPrivateBlocked
                    ? 'This post belongs to a private account.'
                    : 'This post may have been deleted or the link is invalid.'}
                </p>
              </div>
            ) : (
              <PostCard post={resolvedSharedPost} />
            )}
          </div>
        </div>
      )}

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
