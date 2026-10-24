import React, { useEffect, useState, useRef, useCallback } from 'react';
import { Routes, Route, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { Sparkles, Download, RefreshCw, X } from 'lucide-react';
import { supabase, isSupabaseConfigured } from './lib/supabase';
import {
  fetchProfileById,
  fetchProfileByUsername,
  fetchFeedPosts,
  fetchActiveStories,
  fetchRooms,
  fetchNotifications,
  fetchConversations,
  updatePresenceHeartbeat,
  searchUsersAndPosts
} from './lib/vybeApi';
import { BrowserWebRtcCallController, type CallStreams } from './lib/webRtcCallManager';
import { usePwaAndSafeUpdates } from './lib/pwaUpdater';
import { NavigationShell } from './components/NavigationShell';
import { CallOverlay } from './components/CallOverlay';
import { AuthScreenWeb, ResetPasswordPageWeb } from './pages/AuthPages';
import { FeedAndStoriesPage } from './pages/FeedAndStoriesPage';
import { ReelsPage } from './pages/ReelsPage';
import { ExplorePage } from './pages/ExplorePage';
import { UnloadRoomsPage } from './pages/UnloadRoomsPage';
import { InboxAndChatPage } from './pages/InboxAndChatPage';
import { NotificationsPage } from './pages/NotificationsPage';
import { ProfilePage } from './pages/ProfilePage';
import { SettingsAndLegalPage } from './pages/SettingsAndLegalPage';
import type {
  ActiveCallSession,
  UnloadRoom,
  VybePost,
  VybeStory,
  VybeUser
} from './types/vybe';

// Wrapper for Profile route that supports ?user=username or ?id=userId query params
const ProfileRouteWrapper: React.FC<{
  currentUser: VybeUser;
  onUpdateCurrentUser: (u: VybeUser) => void;
  onStartCall: (peer: VybeUser, isVideo: boolean) => void;
}> = ({ currentUser, onUpdateCurrentUser, onStartCall }) => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [targetUser, setTargetUser] = useState<VybeUser>(currentUser);
  const [loading, setLoading] = useState(false);

  const queryUsername = searchParams.get('user');
  const queryId = searchParams.get('id');

  useEffect(() => {
    let active = true;
    const resolveTarget = async () => {
      if (!queryUsername && !queryId) {
        setTargetUser(currentUser);
        return;
      }
      if (
        (queryUsername && queryUsername.toLowerCase() === currentUser.username.toLowerCase()) ||
        (queryId && queryId === currentUser.id)
      ) {
        setTargetUser(currentUser);
        return;
      }

      setLoading(true);
      try {
        let fetched: VybeUser | null = null;
        if (queryUsername) {
          fetched = await fetchProfileByUsername(queryUsername);
        } else if (queryId) {
          fetched = await fetchProfileById(queryId);
        }
        if (active && fetched) {
          setTargetUser(fetched);
        }
      } catch {
        // Fallback
      } finally {
        if (active) setLoading(false);
      }
    };

    void resolveTarget();
    return () => {
      active = false;
    };
  }, [queryUsername, queryId, currentUser]);

  if (loading) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '50vh' }}>
        <div className="vybe-spinner" />
      </div>
    );
  }

  return (
    <ProfilePage
      currentUser={currentUser}
      targetUser={targetUser}
      onUpdateCurrentUser={onUpdateCurrentUser}
      onStartMessageWith={(peer) => navigate(`/messages?user=${peer.id}`)}
      onStartCall={onStartCall}
      onOpenSettings={() => navigate('/settings')}
    />
  );
};

// Wrapper for Inbox route that supports ?user=userId query param to start/open a DM directly
const InboxRouteWrapper: React.FC<{
  currentUser: VybeUser;
  onStartCall: (peer: VybeUser, isVideo: boolean) => void;
}> = ({ currentUser, onStartCall }) => {
  const [searchParams] = useSearchParams();
  const [initialPeer, setInitialPeer] = useState<VybeUser | null>(null);
  const peerId = searchParams.get('user');

  useEffect(() => {
    let active = true;
    if (!peerId) {
      setInitialPeer(null);
      return;
    }
    void fetchProfileById(peerId).then((peer) => {
      if (active && peer) {
        setInitialPeer(peer);
      }
    });
    return () => {
      active = false;
    };
  }, [peerId]);

  return (
    <InboxAndChatPage
      currentUser={currentUser}
      onStartCall={onStartCall}
      initialPeerUser={initialPeer}
    />
  );
};

export const App: React.FC = () => {
  const navigate = useNavigate();

  // Current session & profile
  const [authChecking, setAuthChecking] = useState(true);
  const [currentUser, setCurrentUser] = useState<VybeUser | null>(null);

  // Global feeds & navigation counts
  const [posts, setPosts] = useState<VybePost[]>([]);
  const [stories, setStories] = useState<VybeStory[]>([]);
  const [loadingFeed, setLoadingFeed] = useState(false);
  const [feedPage, setFeedPage] = useState(0);
  const [hasMorePosts, setHasMorePosts] = useState(true);
  const [suggestedUsers, setSuggestedUsers] = useState<VybeUser[]>([]);
  const [activeRooms, setActiveRooms] = useState<UnloadRoom[]>([]);
  const [unreadMessagesCount, setUnreadMessagesCount] = useState(0);
  const [unreadNotificationsCount, setUnreadNotificationsCount] = useState(0);

  // UI state & Composer coordination
  const [isComposerDirty, setIsComposerDirty] = useState(false);
  const [externalOpenComposer, setExternalOpenComposer] = useState(false);

  // WebRTC Live Calling State
  const [activeCall, setActiveCall] = useState<ActiveCallSession | null>(null);
  const [callStreams, setCallStreams] = useState<CallStreams>({
    localStream: null,
    remoteStream: null
  });
  const callControllerRef = useRef<BrowserWebRtcCallController | null>(null);

  // PWA updater
  const pwa = usePwaAndSafeUpdates({
    hasBusyActivity: Boolean(activeCall || isComposerDirty)
  });

  // 1. Initial auth check & profile hydration
  const hydrateUserFromSession = useCallback(async () => {
    if (!isSupabaseConfigured) {
      // Local fallback profile when demoing offline/unconfigured
      setCurrentUser({
        id: 'local_demo_user',
        username: 'vybe_creator',
        displayName: 'VYBE Demo User',
        firstName: 'VYBE',
        lastName: 'Creator',
        bio: 'Exploring mood-driven connections on VYBE by NR Next.',
        avatarUrl: '',
        coverUrl: '',
        email: 'creator@vybe.social',
        phone: '+15550192834',
        dateOfBirth: '2000-01-01',
        isEmailVerified: true,
        isPhoneVerified: true,
        isVerified: true,
        isVip: true,
        isVerifiedBusiness: false,
        isPrivate: false,
        messagePrivacy: 'EVERYONE',
        role: 'USER',
        followersCount: 128,
        followingCount: 64,
        postsCount: 12,
        livePulse: 'VIOLET',
        createdAtIso: new Date().toISOString(),
        lastSeenAtIso: new Date().toISOString(),
        isOnline: true
      });
      setAuthChecking(false);
      return;
    }

    try {
      const { data: { session } } = await supabase.auth.getSession();
      if (!session?.user) {
        setCurrentUser(null);
        setAuthChecking(false);
        return;
      }

      const profile = await fetchProfileById(session.user.id);
      if (profile) {
        setCurrentUser(profile);
      } else {
        // Construct temporary profile until DB triggers finish
        const email = session.user.email || '';
        const rawUsername = email.split('@')[0] || `user_${session.user.id.slice(0, 6)}`;
        const fallbackUser: VybeUser = {
          id: session.user.id,
          username: rawUsername,
          displayName: session.user.user_metadata?.full_name || rawUsername,
          firstName: session.user.user_metadata?.first_name || '',
          lastName: session.user.user_metadata?.last_name || '',
          bio: '',
          avatarUrl: session.user.user_metadata?.avatar_url || '',
          coverUrl: '',
          email,
          phone: session.user.phone || '',
          dateOfBirth: session.user.user_metadata?.date_of_birth || '',
          isEmailVerified: Boolean(session.user.email_confirmed_at),
          isPhoneVerified: Boolean(session.user.phone_confirmed_at),
          isVerified: false,
          isVip: false,
          isVerifiedBusiness: false,
          isPrivate: false,
          messagePrivacy: 'EVERYONE',
          role: 'USER',
          followersCount: 0,
          followingCount: 0,
          postsCount: 0,
          livePulse: 'NONE',
          createdAtIso: session.user.created_at,
          lastSeenAtIso: new Date().toISOString(),
          isOnline: true
        };
        setCurrentUser(fallbackUser);
      }
    } catch (err) {
      console.error('Session hydration error:', err);
      setCurrentUser(null);
    } finally {
      setAuthChecking(false);
    }
  }, []);

  useEffect(() => {
    void hydrateUserFromSession();

    if (!isSupabaseConfigured) return;

    const { data: { subscription } } = supabase.auth.onAuthStateChange((event) => {
      if (event === 'SIGNED_IN' || event === 'USER_UPDATED' || event === 'TOKEN_REFRESHED') {
        void hydrateUserFromSession();
      } else if (event === 'SIGNED_OUT') {
        setCurrentUser(null);
      }
    });

    return () => {
      subscription.unsubscribe();
    };
  }, [hydrateUserFromSession]);

  // 2. Refresh feed, stories, rooms, suggested users, and unread counts
  const refreshFeedAndStories = useCallback(async () => {
    if (!currentUser) return;
    setLoadingFeed(true);
    try {
      const [feedPosts, storiesRes] = await Promise.all([
        fetchFeedPosts(currentUser.id, 0, 20),
        fetchActiveStories(currentUser.id)
      ]);
      setPosts(feedPosts);
      setStories(storiesRes);
      setFeedPage(0);
      setHasMorePosts(feedPosts.length >= 20);
    } catch (err) {
      console.error('Feed refresh error:', err);
    } finally {
      setLoadingFeed(false);
    }
  }, [currentUser]);

  const loadMorePosts = useCallback(async () => {
    if (!currentUser || !hasMorePosts || loadingFeed) return;
    const nextPage = feedPage + 1;
    try {
      const morePosts = await fetchFeedPosts(currentUser.id, nextPage, 20);
      setPosts((prev) => [...prev, ...morePosts]);
      setFeedPage(nextPage);
      setHasMorePosts(morePosts.length >= 20);
    } catch (err) {
      console.error('Load more posts error:', err);
    }
  }, [currentUser, hasMorePosts, loadingFeed, feedPage]);

  const refreshSidebarAndBadges = useCallback(async () => {
    if (!currentUser) return;
    try {
      const [rooms, notifs, convs, searchRes] = await Promise.all([
        fetchRooms(),
        fetchNotifications(currentUser.id),
        fetchConversations(currentUser.id),
        searchUsersAndPosts('', 10)
      ]);
      setActiveRooms(rooms);
      setUnreadNotificationsCount(notifs.filter((n) => !n.isRead).length);
      setUnreadMessagesCount(
        convs.reduce((acc, c) => acc + (c.unreadCount || 0), 0)
      );
      setSuggestedUsers(
        searchRes.users.filter((u) => u.id !== currentUser.id).slice(0, 5)
      );
    } catch {
      // Non-blocking fallback
    }
  }, [currentUser]);

  useEffect(() => {
    if (currentUser) {
      void refreshFeedAndStories();
      void refreshSidebarAndBadges();
    }
  }, [currentUser, refreshFeedAndStories, refreshSidebarAndBadges]);

  // 3. WebRTC Call Manager & Presence Heartbeat
  useEffect(() => {
    if (!currentUser) return;

    // Presence heartbeat
    void updatePresenceHeartbeat(currentUser.id);
    const heartbeatInterval = window.setInterval(() => {
      void updatePresenceHeartbeat(currentUser.id);
    }, 45_000);

    // WebRTC controller setup
    const controller = new BrowserWebRtcCallController(
      currentUser.id,
      (sess) => setActiveCall(sess),
      (streams) => setCallStreams(streams)
    );
    callControllerRef.current = controller;

    // Listen for incoming calls via Supabase Realtime
    let callChannel: ReturnType<typeof supabase.channel> | null = null;
    if (isSupabaseConfigured) {
      callChannel = supabase
        .channel(`user-calls:${currentUser.id}`)
        .on(
          'postgres_changes',
          {
            event: 'INSERT',
            schema: 'public',
            table: 'call_sessions',
            filter: `callee_id=eq.${currentUser.id}`
          },
          (payload) => {
            const row: any = payload.new;
            if (row && row.status === 'RINGING') {
              void controller.receiveIncomingCallRow(row);
            }
          }
        )
        .subscribe();
    }

    return () => {
      window.clearInterval(heartbeatInterval);
      if (callChannel) {
        void supabase.removeChannel(callChannel);
      }
      void controller.cleanup();
      callControllerRef.current = null;
    };
  }, [currentUser]);

  // 4. Actions
  const handleStartCall = useCallback((peerUser: VybeUser, isVideo: boolean) => {
    if (!callControllerRef.current) {
      alert('Live calling is not initialized yet.');
      return;
    }
    callControllerRef.current.startOutgoingCall(peerUser, isVideo).catch((err) => {
      alert(err.message || 'Unable to place call.');
    });
  }, []);

  const handleLogout = useCallback(async () => {
    if (callControllerRef.current) {
      await callControllerRef.current.cleanup();
    }
    if (isSupabaseConfigured) {
      await supabase.auth.signOut();
    }
    setCurrentUser(null);
    navigate('/login');
  }, [navigate]);

  const handleOpenCreatePost = useCallback(() => {
    navigate('/');
    setExternalOpenComposer(true);
    // Reset trigger after tick so it can be re-triggered
    window.setTimeout(() => setExternalOpenComposer(false), 200);
  }, [navigate]);

  // Loading Screen
  if (authChecking) {
    return (
      <div
        style={{
          minHeight: '100dvh',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          background: 'var(--bg-app)',
          color: 'var(--text-primary)',
          gap: 16
        }}
      >
        <div
          style={{
            width: 54,
            height: 54,
            borderRadius: 16,
            background: 'var(--vybe-gradient)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: '0 8px 24px rgba(124, 77, 255, 0.45)',
            animation: 'pulse 1.8s infinite'
          }}
        >
          <Sparkles size={28} color="#fff" />
        </div>
        <div style={{ textAlign: 'center' }}>
          <h1 className="vybe-brand-font vybe-gradient-text" style={{ fontSize: '1.8rem', margin: 0 }}>
            VYBE
          </h1>
          <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', letterSpacing: '0.15em', marginTop: 4 }}>
            BY NR NEXT
          </p>
        </div>
      </div>
    );
  }

  // Unauthenticated Flow
  if (!currentUser) {
    return (
      <Routes>
        <Route path="/login" element={<AuthScreenWeb onAuthSuccess={hydrateUserFromSession} />} />
        <Route path="/register" element={<AuthScreenWeb onAuthSuccess={hydrateUserFromSession} />} />
        <Route path="/auth" element={<AuthScreenWeb onAuthSuccess={hydrateUserFromSession} />} />
        <Route path="/reset-password" element={<ResetPasswordPageWeb />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    );
  }

  // Authenticated App Shell Flow
  return (
    <>
      {/* PWA Update / Installation Banner */}
      {pwa.updateAvailable && (
        <div
          style={{
            position: 'fixed',
            bottom: '72px',
            right: '20px',
            zIndex: 9999,
            background: 'var(--bg-card)',
            border: '1px solid var(--vybe-violet)',
            borderRadius: 'var(--radius-lg)',
            padding: '12px 16px',
            boxShadow: 'var(--shadow-card)',
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            maxWidth: 360
          }}
        >
          <RefreshCw size={20} color="var(--vybe-cyan)" />
          <div style={{ flex: 1, fontSize: '0.85rem' }}>
            <div style={{ fontWeight: 600 }}>Update available</div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
              A new version of VYBE is ready.
            </div>
          </div>
          <button
            className="vybe-btn vybe-btn-primary"
            style={{ padding: '6px 12px', fontSize: '0.78rem' }}
            onClick={() => void pwa.applyUpdateNow()}
          >
            Update
          </button>
          <button
            onClick={() => pwa.dismissUpdateLater()}
            style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer' }}
          >
            <X size={16} />
          </button>
        </div>
      )}

      {/* Main Navigation Shell */}
      <NavigationShell
        currentUser={currentUser}
        unreadMessagesCount={unreadMessagesCount}
        unreadNotificationsCount={unreadNotificationsCount}
        suggestedUsers={suggestedUsers}
        activeRooms={activeRooms}
        onOpenCreatePost={handleOpenCreatePost}
        onLogout={handleLogout}
      >
        <Routes>
          <Route
            path="/"
            element={
              <FeedAndStoriesPage
                currentUser={currentUser}
                posts={posts}
                stories={stories}
                loadingFeed={loadingFeed}
                onRefreshAll={refreshFeedAndStories}
                onLoadMore={loadMorePosts}
                hasMorePosts={hasMorePosts}
                onDraftDirtyChange={setIsComposerDirty}
                externalOpenComposer={externalOpenComposer}
              />
            }
          />
          <Route
            path="/reels"
            element={<ReelsPage currentUser={currentUser} onRefreshAll={refreshFeedAndStories} />}
          />
          <Route
            path="/explore"
            element={
              <ExplorePage
                currentUser={currentUser}
                onOpenUserProfile={(u) => navigate(`/profile?user=${u.username}`)}
              />
            }
          />
          <Route
            path="/rooms"
            element={<UnloadRoomsPage currentUser={currentUser} />}
          />
          <Route
            path="/messages"
            element={<InboxRouteWrapper currentUser={currentUser} onStartCall={handleStartCall} />}
          />
          <Route
            path="/notifications"
            element={
              <NotificationsPage
                currentUser={currentUser}
                onOpenUserProfile={(u) => navigate(`/profile?user=${u.username}`)}
                onOpenInbox={() => navigate('/messages')}
              />
            }
          />
          <Route
            path="/profile"
            element={
              <ProfileRouteWrapper
                currentUser={currentUser}
                onUpdateCurrentUser={setCurrentUser}
                onStartCall={handleStartCall}
              />
            }
          />
          <Route
            path="/settings"
            element={
              <SettingsAndLegalPage
                currentUser={currentUser}
                onUpdateCurrentUser={setCurrentUser}
                onLogout={handleLogout}
              />
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </NavigationShell>

      {/* Global Voice & Video Call Overlay */}
      <CallOverlay
        call={activeCall}
        streams={callStreams}
        onAccept={() => void callControllerRef.current?.acceptIncomingCall()}
        onDecline={() => void callControllerRef.current?.declineIncomingCall()}
        onEnd={() => void callControllerRef.current?.endActiveCall()}
        onToggleMute={() => callControllerRef.current?.toggleMute()}
        onToggleCamera={() => callControllerRef.current?.toggleCamera()}
        onSwitchCamera={() => void callControllerRef.current?.switchCameraDevice()}
      />
    </>
  );
};
