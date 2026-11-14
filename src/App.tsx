import React from 'react';
import { X, Lock, Loader2, Smartphone, Download, ExternalLink, WifiOff, AlertTriangle } from 'lucide-react';
import { useVybe } from './context/VybeContext';
import { Navbar } from './components/Navbar';
import { BottomNav } from './components/BottomNav';
import { PostCard } from './components/PostCard';
import { AuthModal } from './components/AuthModal';
import { VybeAvatar } from './components/OptimizedImage';

// Views
import { FeedView } from './views/FeedView';
import { ReelsView } from './views/ReelsView';
import { MoodZonesView } from './views/MoodZonesView';
import { CrossedPathsView } from './views/CrossedPathsView';
import { InboxView } from './views/InboxView';
import { CommunityView } from './views/CommunityView';
import { ProfileView } from './views/ProfileView';
import { ExploreView } from './views/ExploreView';
import { GrowWorkspaceView } from './views/GrowWorkspaceView';

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
    appMode,
    selectedTab,
    activeModal,
    callState,
    acceptCall,
    declineCall,
    sharedPostModalId,
    resolvedSharedPost,
    sharedPostState,
    sharedPostError,
    closeSharedPostModal,
    isDownloadPage,
    closeDownloadPage,
    updateInfo,
    githubRepo,
    isOffline,
    dbError,
    refreshFromDatabase,
    inAppNotificationBanner,
    dismissBanner
  } = useVybe();

  const renderCurrentTab = () => {
    if (isDownloadPage) {
      const apkUrl =
        updateInfo?.downloadUrl ||
        `https://github.com/${githubRepo}/releases/latest`;
      return (
        <div className="max-w-lg mx-auto px-4 py-12 pb-28">
          <div className="p-7 rounded-3xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] shadow-xl text-center space-y-4">
            <div className="w-14 h-14 rounded-2xl bg-[var(--vybe-accent-soft)] text-[var(--vybe-accent)] flex items-center justify-center mx-auto">
              <Smartphone className="w-7 h-7" />
            </div>
            <h1 className="text-xl font-extrabold brand-font text-[var(--vybe-text)]">
              Download VYBE for Android
            </h1>
            <p className="text-xs text-[var(--vybe-text-secondary)] leading-relaxed">
              Get the latest signed VYBE Android APK ({updateInfo?.tagName || 'Latest Release'}) with native notifications, reels, and instant QR scanning.
            </p>
            <div className="flex flex-col gap-2.5 pt-2">
              <a
                href={apkUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="w-full py-3 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold flex items-center justify-center gap-2 hover:opacity-95"
              >
                <Download className="w-4 h-4" />
                <span>{updateInfo?.downloadUrl ? 'Download VYBE APK' : 'View Latest GitHub Release'}</span>
              </a>
              <button
                type="button"
                onClick={closeDownloadPage}
                className="w-full py-2.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs font-semibold text-[var(--vybe-text)]"
              >
                Continue to VYBE Web
              </button>
            </div>
          </div>
        </div>
      );
    }

    if (appMode === 'GROW') {
      return <GrowWorkspaceView />;
    }
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
    <div className="vybe-app-shell min-h-screen bg-[var(--vybe-bg)] text-[var(--vybe-text)] flex flex-col antialiased transition-colors">
      <Navbar />

      {/* Offline / Connection Warning Strip */}
      {(isOffline || dbError) && (
        <div className="bg-amber-500/12 border-b border-amber-500/30 px-4 py-2 text-xs text-amber-600 dark:text-amber-400 flex items-center justify-center gap-2.5">
          {isOffline ? <WifiOff className="w-4 h-4 shrink-0" /> : <AlertTriangle className="w-4 h-4 shrink-0" />}
          <span>{isOffline ? 'You are offline. Live sync paused.' : dbError}</span>
          {!isOffline && (
            <button
              type="button"
              onClick={() => refreshFromDatabase(true)}
              className="font-bold underline ml-1"
            >
              Retry
            </button>
          )}
        </div>
      )}

      {/* In-App Confirmation Banner */}
      {inAppNotificationBanner && (
        <div className="fixed bottom-20 left-1/2 -translate-x-1/2 z-50 px-4 py-2.5 rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] shadow-2xl flex items-center gap-3 text-xs font-semibold text-[var(--vybe-text)] max-w-[92vw]">
          <span className="truncate">{inAppNotificationBanner}</span>
          <button
            onClick={dismissBanner}
            className="text-[var(--vybe-accent)] font-bold hover:underline shrink-0"
          >
            OK
          </button>
        </div>
      )}

      {/* Incoming Call Floating Banner */}
      {callState.isActive && callState.isIncoming && callState.peerUser && (
        <div className="fixed top-16 inset-x-4 z-50 max-w-md mx-auto p-4 rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-accent)] shadow-2xl flex items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <VybeAvatar
              src={callState.peerUser.avatarUrl}
              name={callState.peerUser.displayName}
              username={callState.peerUser.username}
              className="w-10 h-10 rounded-full"
            />
            <div>
              <p className="text-xs font-bold text-[var(--vybe-text)]">
                Incoming {callState.isVideo ? 'Video' : 'Voice'} Call...
              </p>
              <p className="text-[11px] text-[var(--vybe-text-secondary)]">{callState.peerUser.displayName}</p>
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
              className="px-3 py-1.5 rounded-xl bg-emerald-500 hover:bg-emerald-600 text-white keep-white text-xs font-bold shadow"
            >
              Accept
            </button>
          </div>
        </div>
      )}

      <div className="flex-1 w-full">{renderCurrentTab()}</div>

      <BottomNav />

      {/* Shared Post Deep-Link Modal */}
      {sharedPostModalId && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md"
          onClick={closeSharedPostModal}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            className="relative w-full max-w-lg max-h-[90vh] overflow-y-auto bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-3xl shadow-2xl p-4 space-y-3 no-scrollbar"
          >
            <div className="flex items-center justify-between px-2 pb-2 border-b border-[var(--vybe-border)]">
              <h3 className="text-sm font-bold text-[var(--vybe-text)]">Shared Post</h3>
              <div className="flex items-center gap-2">
                <a
                  href={`vybe://post/${encodeURIComponent(sharedPostModalId)}`}
                  className="px-2.5 py-1 rounded-lg bg-[var(--vybe-accent-soft)] text-[var(--vybe-accent)] text-[11px] font-bold inline-flex items-center gap-1"
                >
                  <span>Open in App</span>
                  <ExternalLink className="w-3 h-3" />
                </a>
                <button
                  onClick={closeSharedPostModal}
                  aria-label="Close shared post"
                  className="p-1.5 rounded-full text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)]"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>
            </div>

            {sharedPostState === 'LOADING' ? (
              <div className="p-10 text-center space-y-2">
                <Loader2 className="w-7 h-7 animate-spin text-[var(--vybe-accent)] mx-auto" />
                <p className="text-xs text-[var(--vybe-text-secondary)]">Loading shared post...</p>
              </div>
            ) : !resolvedSharedPost || sharedPostState === 'PRIVATE' || sharedPostState === 'NOT_FOUND' || sharedPostState === 'ERROR' ? (
              <div className="p-6 text-center space-y-2">
                <Lock className="w-8 h-8 text-amber-500 mx-auto" />
                <p className="text-sm font-bold text-[var(--vybe-text)]">Post Unavailable</p>
                <p className="text-xs text-[var(--vybe-text-secondary)]">
                  {sharedPostError || 'This post may have been deleted or belongs to a private account.'}
                </p>
              </div>
            ) : (
              <PostCard post={resolvedSharedPost} />
            )}
          </div>
        </div>
      )}

      {/* Global Web Auth Modal */}
      <AuthModal />

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
