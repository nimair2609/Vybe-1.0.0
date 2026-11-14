import React, { createContext, useContext, useState, useEffect, useRef } from 'react';
import { vybeCache } from '../utils/indexedDbCache';
import {
  User,
  Post,
  Story,
  CrossedPathEncounter,
  TimeCapsule,
  UnloadRoom,
  VybeShop,
  Community,
  SpiritualReflection,
  SpiritualIntention,
  Conversation,
  DirectMessage,
  NotificationItem,
  AuthSessionUser,
  MoodType,
  VybeReaction,
  GitHubReleaseInfo,
  LivePulseStatus
} from '../types';
import {
  DEFAULT_GITHUB_REPO,
  fetchGitHubUpdates,
  cleanRepoSlug
} from '../services/githubUpdater';
import {
  getStoredWebSession,
  refreshWebAccessToken,
  signInWithIdentifier,
  signUpWithEmail,
  sendPasswordResetEmail,
  signOutWebSession,
  fetchMyAuthenticatedProfile,
  fetchLiveProfiles,
  fetchProfileByUsernameOrId,
  fetchPostsByAuthor,
  fetchSinglePostById,
  fetchLivePosts,
  fetchLiveStories,
  createLivePost,
  createLiveStory,
  submitLiveComment,
  fetchCommentsForPost,
  syncPostLikeCountToSupabase,
  togglePostReactionInSupabase,
  toggleSavePostInSupabase,
  toggleFollowInSupabase,
  updateProfileInSupabase,
  fetchUserSocialGraph,
  resolveOrCreateWebDirectConversation,
  fetchConversationsForUser,
  fetchMessagesForConversation,
  markConversationReadInSupabase,
  sendLiveDirectMessage,
  uploadMediaBlobToStorage,
  fetchLiveNotifications,
  markNotificationReadInSupabase,
  fetchLiveCommunities,
  toggleJoinCommunityInSupabase,
  fetchLiveUnloadRooms,
  fetchLiveCrossedPaths,
  fetchLiveTimeCapsules,
  fetchLiveShops,
  fetchLiveSpiritualIntentions,
  addSpiritualIntentionInSupabase,
  toggleSpiritualIntentionInSupabase,
  formatChatClock,
  formatRelativeTime
} from '../services/supabaseService';
import { vybeRealtime, RealtimePushPayload, RealtimeDbChangePayload } from '../services/realtimeService';

export type MainTab = 'FEED' | 'VYBES' | 'MOOD_ZONES' | 'EXPLORE' | 'INBOX' | 'CROSSED_PATHS' | 'COMMUNITY' | 'PROFILE';
export type VybeAppMode = 'SOCIAL' | 'GROW';
export type GrowTab = 'HOME' | 'CIRCLE' | 'PROJECTS' | 'OPPORTUNITIES' | 'PROFILE';

export type ModalScreen =
  | 'CREATE_POST'
  | 'CREATE_STORY'
  | 'CREATE_CAPSULE'
  | 'CREATE_CROSSED_PATH'
  | 'SETTINGS'
  | 'ADMIN_CONSOLE'
  | 'SHOP_DETAIL'
  | 'STORY_VIEWER'
  | 'CALL_SCREEN'
  | 'NOTIFICATIONS'
  | 'VERIFICATION_CENTER'
  | 'QR_CODE'
  | 'GITHUB_UPDATES'
  | 'MUSIC_TRACK_DETAIL'
  | 'SPIRITUAL_MODE'
  | 'UNLOAD_ROOM';

const SPIRITUAL_REFLECTIONS_CATALOG: SpiritualReflection[] = [
  {
    id: 'sp_01',
    quote:
      'You have a right to perform your prescribed duty, but you are not entitled to the fruits of action. Never consider yourself the cause of results, nor be attached to inaction.',
    traditionOrAuthor: 'Bhagavad Gita 2.47',
    theme: 'Equanimity & Purpose',
    audioCue: 'Serene Temple Bells'
  },
  {
    id: 'sp_02',
    quote:
      'Silence is not the absence of sound, but the presence of an awakening stillness inside your own chest.',
    traditionOrAuthor: 'Rumi',
    theme: 'Inner Peace',
    audioCue: 'Gentle Himalayan Flute'
  },
  {
    id: 'sp_03',
    quote: 'Peace comes from within. Do not seek it without.',
    traditionOrAuthor: 'Gautama Buddha',
    theme: 'Mindfulness',
    audioCue: 'Singing Bowls'
  }
];

interface CallState {
  isActive: boolean;
  isIncoming: boolean;
  peerUser: User | null;
  isVideo: boolean;
  isMuted: boolean;
  isSpeakerOn: boolean;
  isCameraOff: boolean;
  durationSeconds: number;
}

export interface ProfileRouteTarget {
  username?: string;
  userId?: string;
}

export type LookupState = 'IDLE' | 'LOADING' | 'FOUND' | 'NOT_FOUND' | 'PRIVATE' | 'ERROR';

interface VybeContextType {
  // Authentication & Current User
  authUser: AuthSessionUser | null;
  isAuthChecking: boolean;
  currentUser: User | null;
  showAuthModal: boolean;
  authModalPrompt: string | null;
  authModalInitialMode: 'LOGIN' | 'SIGNUP' | 'RESET';
  openAuthModal: (prompt?: string, mode?: 'LOGIN' | 'SIGNUP' | 'RESET') => void;
  closeAuthModal: () => void;
  requireAuth: (prompt?: string) => boolean;
  signIn: (identifier: string, password: string) => Promise<{ ok: boolean; error?: string }>;
  signUp: (params: {
    email: string;
    password: string;
    username: string;
    displayName: string;
  }) => Promise<{ ok: boolean; needsEmailConfirmation?: boolean; error?: string }>;
  requestPasswordReset: (email: string) => Promise<{ ok: boolean; error?: string }>;
  signOut: () => Promise<void>;
  updateMyProfile: (patch: { display_name?: string; bio?: string; is_private_account?: boolean }) => Promise<{ ok: boolean; error?: string }>;

  // Users & Public Profile Routing
  allUsers: User[];
  viewedProfileUser: User | null;
  viewedProfilePosts: Post[];
  profileRouteTarget: ProfileRouteTarget | null;
  profileLookupState: LookupState;
  profileLookupError: string | null;
  openUserProfile: (user: User) => void;
  navigateToPublicProfile: (target: ProfileRouteTarget) => void;
  closeUserProfile: () => void;
  setCurrentUserMood: (mood: MoodType) => void;
  setCurrentUserPulse: (pulse: LivePulseStatus) => void;

  // Download route
  isDownloadPage: boolean;
  closeDownloadPage: () => void;

  // Navigation & Experience Mode
  appMode: VybeAppMode;
  setAppMode: (mode: VybeAppMode) => void;
  growTab: GrowTab;
  setGrowTab: (tab: GrowTab) => void;
  selectedTab: MainTab;
  setSelectedTab: (tab: MainTab) => void;
  activeModal: ModalScreen | null;
  openModal: (modal: ModalScreen, data?: any) => void;
  closeModal: () => void;
  selectedMoodFilter: MoodType | 'ALL';
  setSelectedMoodFilter: (mood: MoodType | 'ALL') => void;

  // Social Discovery & Graph
  followingIds: string[];
  requestedFollowIds: string[];
  dismissedSuggestionIds: string[];
  toggleFollowUser: (targetUserId: string) => void;
  dismissSuggestedUser: (targetUserId: string) => void;
  restoreDismissedSuggestions: () => void;

  // Posts & Reels & Shared Post Deep Links
  posts: Post[];
  reels: Post[];
  toggleLikePost: (postId: string) => void;
  ensurePostLoved: (postId: string) => void;
  reactToPost: (postId: string, reaction: VybeReaction) => void;
  toggleCommentLike: (postId: string, commentId: string) => void;
  reactToComment: (postId: string, commentId: string, reaction: VybeReaction) => void;
  toggleSavePost: (postId: string) => void;
  addCommentToPost: (postId: string, text: string) => void;
  loadCommentsForPost: (postId: string) => Promise<void>;
  createPost: (postData: Partial<Post>) => void;
  sharePostToConversation: (post: Post, convId: string, note?: string) => void;
  incrementPostShareCount: (postId: string) => void;
  sharedPostModalId: string | null;
  resolvedSharedPost: Post | null;
  sharedPostState: LookupState;
  sharedPostError: string | null;
  openSharedPostModal: (postId: string) => void;
  closeSharedPostModal: () => void;
  hasMorePosts: boolean;
  loadMorePosts: () => Promise<void>;
  isLoadingMorePosts: boolean;

  // Theme
  themeMode: 'dark' | 'light';
  setThemeMode: (mode: 'dark' | 'light') => void;
  toggleThemeMode: () => void;

  // Stories
  stories: Story[];
  activeStoryAuthorId: string | null;
  activeStoryIndex: number;
  openStoryViewer: (authorId: string, index?: number) => void;
  closeStoryViewer: () => void;
  nextStory: () => void;
  prevStory: () => void;
  toggleStoryLike: (storyId: string) => void;
  reactToStory: (storyId: string, reaction: VybeReaction) => void;
  createStory: (storyData: Partial<Story>) => void;

  // Crossed Paths
  crossedPaths: CrossedPathEncounter[];
  sendConnectionRequest: (id: string) => void;
  createCrossedPath: (encounter: Partial<CrossedPathEncounter>) => void;

  // Time Capsules
  timeCapsules: TimeCapsule[];
  createTimeCapsule: (capsule: Partial<TimeCapsule>) => void;

  // Unload Rooms
  unloadRooms: UnloadRoom[];
  selectedUnloadRoom: UnloadRoom | null;
  joinUnloadRoom: (room: UnloadRoom) => void;
  sendUnloadMessage: (roomId: string, text: string, isAnonymous: boolean) => void;

  // Spiritual Mode
  intentions: SpiritualIntention[];
  addIntention: (text: string) => void;
  toggleIntention: (id: string) => void;
  reflections: SpiritualReflection[];

  // Communities
  communities: Community[];
  toggleJoinCommunity: (commId: string) => void;

  // Shops
  shops: VybeShop[];
  selectedShop: VybeShop | null;
  openShopDetail: (shop: VybeShop) => void;

  // Conversations & Messaging & Realtime
  conversations: Conversation[];
  isLoadingConversations: boolean;
  dmError: string | null;
  dismissDmError: () => void;
  isRealtimeConnected: boolean;
  selectedConversation: Conversation | null;
  selectConversation: (conv: Conversation | null) => void;
  startConversationWithUser: (peerUser: User) => Promise<{ ok: boolean; conversation?: Conversation; error?: string }>;
  sendMessage: (convId: string, text: string, attachmentFile?: Blob) => Promise<void>;
  retryMessage: (convId: string, messageId: string) => Promise<void>;
  refreshConversations: () => Promise<void>;

  // Notifications
  notifications: NotificationItem[];
  markNotificationRead: (id: string) => void;

  // Calls
  callState: CallState;
  startCall: (peerUser: User, isVideo: boolean) => void;
  acceptCall: () => void;
  declineCall: () => void;
  endCall: () => void;
  toggleCallMute: () => void;
  toggleCallCamera: () => void;

  // Music Player
  currentAudioTrack: string | null;
  isPlayingAudio: boolean;
  togglePlayAudio: (trackTitle: string, previewUrl?: string) => void;

  // GitHub Auto Updater
  githubRepo: string;
  setGithubRepo: (repo: string) => void;
  updateInfo: GitHubReleaseInfo | null;
  isCheckingUpdates: boolean;
  updateError: string | null;
  hasAvailableUpdate: boolean;
  checkForUpdates: (repoToTest?: string, silent?: boolean) => Promise<void>;
  resetGithubRepo: () => void;
  inAppNotificationBanner: string | null;
  dismissBanner: () => void;

  // Real Database Status & Live Sync
  isInitialLoading: boolean;
  isSyncing: boolean;
  isDbConnected: boolean;
  isOffline: boolean;
  dbError: string | null;
  refreshFromDatabase: (forceRefresh?: boolean) => Promise<void>;
}

const VybeContext = createContext<VybeContextType | undefined>(undefined);

function parseInitialPath(): {
  profileTarget: ProfileRouteTarget | null;
  postId: string | null;
  isDownload: boolean;
} {
  if (typeof window === 'undefined') {
    return { profileTarget: null, postId: null, isDownload: false };
  }
  const path = window.location.pathname || '/';
  const hash = window.location.hash || '';

  if (/^\/download\/?$/i.test(path)) {
    return { profileTarget: null, postId: null, isDownload: true };
  }

  const postMatch = path.match(/^\/(?:post|p)\/([^/?#]+)/i);
  if (postMatch?.[1]) {
    return { profileTarget: null, postId: decodeURIComponent(postMatch[1]), isDownload: false };
  }

  const hashPost = hash.match(/^#post_(.+)$/i);
  if (hashPost?.[1]) {
    return { profileTarget: null, postId: decodeURIComponent(hashPost[1]), isDownload: false };
  }

  const userRoute = path.match(/^\/u\/([^/?#]+)/i);
  if (userRoute?.[1]) {
    return {
      profileTarget: { username: decodeURIComponent(userRoute[1]).replace(/^@/, '') },
      postId: null,
      isDownload: false
    };
  }

  const profileRoute = path.match(/^\/profile\/([^/?#]+)/i);
  if (profileRoute?.[1]) {
    const seg = decodeURIComponent(profileRoute[1]).trim();
    const searchU = new URLSearchParams(window.location.search).get('u') || undefined;
    const isUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(seg);
    return {
      profileTarget: isUuid ? { userId: seg, username: searchU } : { username: seg.replace(/^@/, '') },
      postId: null,
      isDownload: false
    };
  }

  const hashProfile = hash.match(/^#vybe_(.+)$/i);
  if (hashProfile?.[1]) {
    return {
      profileTarget: { username: decodeURIComponent(hashProfile[1]).replace(/^@/, '') },
      postId: null,
      isDownload: false
    };
  }

  return { profileTarget: null, postId: null, isDownload: false };
}

export const VybeProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const initialRoute = React.useMemo(() => parseInitialPath(), []);

  // Auth State
  const [authUser, setAuthUser] = useState<AuthSessionUser | null>(() => getStoredWebSession());
  const [isAuthChecking, setIsAuthChecking] = useState<boolean>(true);
  const [currentUser, setCurrentUser] = useState<User | null>(null);
  const [showAuthModal, setShowAuthModal] = useState<boolean>(false);
  const [authModalPrompt, setAuthModalPrompt] = useState<string | null>(null);
  const [authModalInitialMode, setAuthModalInitialMode] = useState<'LOGIN' | 'SIGNUP' | 'RESET'>('LOGIN');

  // Users & Public Profiles
  const [allUsers, setAllUsers] = useState<User[]>([]);
  const [viewedProfileUser, setViewedProfileUser] = useState<User | null>(null);
  const [viewedProfilePosts, setViewedProfilePosts] = useState<Post[]>([]);
  const [profileRouteTarget, setProfileRouteTarget] = useState<ProfileRouteTarget | null>(initialRoute.profileTarget);
  const [profileLookupState, setProfileLookupState] = useState<LookupState>(
    initialRoute.profileTarget ? 'LOADING' : 'IDLE'
  );
  const [profileLookupError, setProfileLookupError] = useState<string | null>(null);
  const [previousTabBeforeProfile, setPreviousTabBeforeProfile] = useState<MainTab>('FEED');

  // Download route
  const [isDownloadPage, setIsDownloadPage] = useState<boolean>(initialRoute.isDownload);

  // Database / Network status
  const [isInitialLoading, setIsInitialLoading] = useState<boolean>(true);
  const [isSyncing, setIsSyncing] = useState<boolean>(false);
  const [isDbConnected, setIsDbConnected] = useState<boolean>(false);
  const [isOffline, setIsOffline] = useState<boolean>(
    typeof navigator !== 'undefined' ? !navigator.onLine : false
  );
  const [dbError, setDbError] = useState<string | null>(null);

  // Navigation
  const [appMode, setAppModeState] = useState<VybeAppMode>('SOCIAL');
  const [growTab, setGrowTabState] = useState<GrowTab>('HOME');
  const [selectedTab, setSelectedTabState] = useState<MainTab>(
    initialRoute.profileTarget ? 'PROFILE' : 'FEED'
  );
  const [activeModal, setActiveModal] = useState<ModalScreen | null>(null);
  const [selectedMoodFilter, setSelectedMoodFilter] = useState<MoodType | 'ALL'>('ALL');

  // Social Graph (populated from Supabase for authenticated user)
  const [followingIds, setFollowingIds] = useState<string[]>([]);
  const [requestedFollowIds, setRequestedFollowIds] = useState<string[]>([]);
  const [dismissedSuggestionIds, setDismissedSuggestionIds] = useState<string[]>(() => {
    try {
      const saved = localStorage.getItem('vybe_dismissed_suggestions_v1');
      if (saved) return JSON.parse(saved);
    } catch {}
    return [];
  });

  useEffect(() => {
    try {
      localStorage.setItem('vybe_dismissed_suggestions_v1', JSON.stringify(dismissedSuggestionIds));
    } catch {}
  }, [dismissedSuggestionIds]);

  // Posts & Reels & Shared Post Modal
  const [posts, setPosts] = useState<Post[]>([]);
  const [reels, setReels] = useState<Post[]>([]);
  const [hasMorePosts, setHasMorePosts] = useState<boolean>(true);
  const [isLoadingMorePosts, setIsLoadingMorePosts] = useState<boolean>(false);
  const lastSyncTimeRef = useRef<number>(Date.now());

  const [sharedPostModalId, setSharedPostModalId] = useState<string | null>(initialRoute.postId);
  const [resolvedSharedPost, setResolvedSharedPost] = useState<Post | null>(null);
  const [sharedPostState, setSharedPostState] = useState<LookupState>(
    initialRoute.postId ? 'LOADING' : 'IDLE'
  );
  const [sharedPostError, setSharedPostError] = useState<string | null>(null);

  // Stories
  const [stories, setStories] = useState<Story[]>([]);
  const [activeStoryAuthorId, setActiveStoryAuthorId] = useState<string | null>(null);
  const [activeStoryIndex, setActiveStoryIndex] = useState<number>(0);

  // Live Domain Features (zero mock arrays)
  const [crossedPaths, setCrossedPaths] = useState<CrossedPathEncounter[]>([]);
  const [timeCapsules, setTimeCapsules] = useState<TimeCapsule[]>([]);
  const [unloadRooms, setUnloadRooms] = useState<UnloadRoom[]>([]);
  const [selectedUnloadRoom, setSelectedUnloadRoom] = useState<UnloadRoom | null>(null);
  const [intentions, setIntentions] = useState<SpiritualIntention[]>([]);
  const reflections = SPIRITUAL_REFLECTIONS_CATALOG;
  const [communities, setCommunities] = useState<Community[]>([]);
  const [shops, setShops] = useState<VybeShop[]>([]);
  const [selectedShop, setSelectedShop] = useState<VybeShop | null>(null);

  // Messaging & Notifications & Realtime
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [isLoadingConversations, setIsLoadingConversations] = useState<boolean>(false);
  const [dmError, setDmError] = useState<string | null>(null);
  const [isRealtimeConnected, setIsRealtimeConnected] = useState<boolean>(false);
  const [selectedConversationId, setSelectedConversationId] = useState<string | null>(null);
  const [selectedConversationPeerId, setSelectedConversationPeerId] = useState<string | null>(null);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);

  // Theme Mode
  const [themeMode, setThemeModeState] = useState<'dark' | 'light'>(() => {
    const saved = localStorage.getItem('vybe_theme_mode');
    return saved === 'light' ? 'light' : 'dark';
  });

  const effectiveThemeMode: 'dark' | 'light' =
    appMode === 'GROW'
      ? localStorage.getItem('vybe_grow_theme_override') === 'dark'
        ? 'dark'
        : 'light'
      : themeMode;

  useEffect(() => {
    document.documentElement.dataset.mode = appMode === 'GROW' ? 'grow' : 'social';
    document.documentElement.dataset.theme = effectiveThemeMode;
    document.documentElement.classList.toggle('dark', effectiveThemeMode === 'dark');
  }, [appMode, effectiveThemeMode]);

  useEffect(() => {
    localStorage.setItem('vybe_theme_mode', themeMode);
  }, [themeMode]);

  const setAppMode = (mode: VybeAppMode) => setAppModeState(mode);
  const setGrowTab = (tab: GrowTab) => {
    setAppModeState('GROW');
    if (tab === 'PROFILE') {
      setViewedProfileUser(null);
      setProfileRouteTarget(null);
    }
    setGrowTabState(tab);
  };
  const setThemeMode = (mode: 'dark' | 'light') => {
    if (appMode === 'GROW') localStorage.setItem('vybe_grow_theme_override', mode);
    setThemeModeState(mode);
  };
  const toggleThemeMode = () => setThemeMode(effectiveThemeMode === 'dark' ? 'light' : 'dark');

  // Calls & Audio
  const [callState, setCallState] = useState<CallState>({
    isActive: false,
    isIncoming: false,
    peerUser: null,
    isVideo: false,
    isMuted: false,
    isSpeakerOn: true,
    isCameraOff: false,
    durationSeconds: 0
  });
  const [currentAudioTrack, setCurrentAudioTrack] = useState<string | null>(null);
  const [isPlayingAudio, setIsPlayingAudio] = useState(false);
  const [audioElement, setAudioElement] = useState<HTMLAudioElement | null>(null);

  // GitHub Updater
  const [githubRepo, setGithubRepoState] = useState<string>(() => localStorage.getItem('vybe_github_repo') || DEFAULT_GITHUB_REPO);
  const [updateInfo, setUpdateInfo] = useState<GitHubReleaseInfo | null>(null);
  const [isCheckingUpdates, setIsCheckingUpdates] = useState<boolean>(false);
  const [updateError, setUpdateError] = useState<string | null>(null);
  const [hasAvailableUpdate, setHasAvailableUpdate] = useState<boolean>(false);
  const [inAppNotificationBanner, setInAppNotificationBanner] = useState<string | null>(null);

  const dismissBanner = () => setInAppNotificationBanner(null);
  const dismissDmError = () => setDmError(null);

  // Auth helpers
  const openAuthModal = (prompt?: string, mode: 'LOGIN' | 'SIGNUP' | 'RESET' = 'LOGIN') => {
    setAuthModalPrompt(prompt || null);
    setAuthModalInitialMode(mode);
    setShowAuthModal(true);
  };

  const closeAuthModal = () => {
    setShowAuthModal(false);
    setAuthModalPrompt(null);
  };

  const requireAuth = (prompt?: string): boolean => {
    if (currentUser && authUser) return true;
    openAuthModal(prompt || 'Sign in with your VYBE account to continue.', 'LOGIN');
    return false;
  };

  // Hydration helper for authenticated user
  const hydrateAuthenticatedUserData = async (session: AuthSessionUser) => {
    vybeCache.setUserId(session.id);

    const [ownProfile, graph, convRes, notifs, capsules, userIntentions] = await Promise.all([
      fetchMyAuthenticatedProfile(session.id, session.accessToken),
      fetchUserSocialGraph(session.id),
      fetchConversationsForUser(session.id),
      fetchLiveNotifications(session.id),
      fetchLiveTimeCapsules(session.id),
      fetchLiveSpiritualIntentions(session.id)
    ]);

    if (ownProfile) {
      setCurrentUser(ownProfile);
      setAllUsers(prev => {
        const exists = prev.some(u => u.id === ownProfile.id);
        return exists ? prev.map(u => (u.id === ownProfile.id ? ownProfile : u)) : [ownProfile, ...prev];
      });
    }

    setFollowingIds(graph.followingIds);
    setRequestedFollowIds(graph.requestedFollowIds);
    setConversations(convRes.conversations);
    if (convRes.error) setDmError(convRes.error);
    setNotifications(notifs);
    setTimeCapsules(capsules);
    setIntentions(userIntentions);

    setPosts(prev =>
      prev.map(p => ({
        ...p,
        isLiked: Boolean(graph.reactionsByPostId[p.id]),
        userReaction: graph.reactionsByPostId[p.id],
        isSaved: graph.savedPostIds.includes(p.id)
      }))
    );
    setReels(prev =>
      prev.map(p => ({
        ...p,
        isLiked: Boolean(graph.reactionsByPostId[p.id]),
        userReaction: graph.reactionsByPostId[p.id],
        isSaved: graph.savedPostIds.includes(p.id)
      }))
    );
  };

  const signIn = async (identifier: string, password: string): Promise<{ ok: boolean; error?: string }> => {
    const res = await signInWithIdentifier(identifier, password);
    if (!res.success || !res.session) {
      return { ok: false, error: res.error || 'Sign in failed.' };
    }
    setAuthUser(res.session);
    if (res.profile) {
      setCurrentUser(res.profile);
    }
    closeAuthModal();
    await hydrateAuthenticatedUserData(res.session);
    setInAppNotificationBanner(`Signed in as @${res.profile?.username || res.session.email}`);
    return { ok: true };
  };

  const signUp = async (params: {
    email: string;
    password: string;
    username: string;
    displayName: string;
  }): Promise<{ ok: boolean; needsEmailConfirmation?: boolean; error?: string }> => {
    const res = await signUpWithEmail(params);
    if (!res.success) {
      return { ok: false, error: res.error };
    }
    if (res.needsEmailConfirmation) {
      return { ok: true, needsEmailConfirmation: true };
    }
    if (res.session) {
      setAuthUser(res.session);
      if (res.profile) setCurrentUser(res.profile);
      closeAuthModal();
      await hydrateAuthenticatedUserData(res.session);
      setInAppNotificationBanner('Welcome to VYBE! Your account is ready.');
    }
    return { ok: true };
  };

  const requestPasswordReset = async (email: string) => {
    const res = await sendPasswordResetEmail(email);
    return { ok: res.success, error: res.error };
  };

  const signOut = async () => {
    vybeRealtime.disconnect(true);
    await signOutWebSession();
    setAuthUser(null);
    setCurrentUser(null);
    setFollowingIds([]);
    setRequestedFollowIds([]);
    setConversations([]);
    setSelectedConversationId(null);
    setSelectedConversationPeerId(null);
    setNotifications([]);
    setTimeCapsules([]);
    setIntentions([]);
    setDmError(null);
    setActiveModal(null);
    setIsRealtimeConnected(false);
    setPosts(prev => prev.map(p => ({ ...p, isLiked: false, isSaved: false, userReaction: undefined })));
    setReels(prev => prev.map(p => ({ ...p, isLiked: false, isSaved: false, userReaction: undefined })));
    setInAppNotificationBanner('Signed out cleanly.');
  };

  const updateMyProfile = async (patch: {
    display_name?: string;
    bio?: string;
    is_private_account?: boolean;
  }): Promise<{ ok: boolean; error?: string }> => {
    if (!currentUser) return { ok: false, error: 'Sign in required.' };
    const res = await updateProfileInSupabase(currentUser.id, patch);
    if (res.ok && res.user) {
      setCurrentUser(res.user);
      setAllUsers(prev => prev.map(u => (u.id === res.user!.id ? res.user! : u)));
      setInAppNotificationBanner('Profile updated.');
    }
    return { ok: res.ok, error: res.error };
  };

  // Initial Auth Session Restore + Database Sync
  const syncDatabase = async (forceRefresh: boolean = false) => {
    lastSyncTimeRef.current = Date.now();
    if (forceRefresh) setIsSyncing(true);
    setDbError(null);

    try {
      let activeSession = getStoredWebSession();
      if (activeSession) {
        const refreshed = await refreshWebAccessToken();
        if (refreshed) activeSession = refreshed;
      }
      setAuthUser(activeSession);
      setIsAuthChecking(false);

      const [liveProfiles, livePosts, liveStories, liveComms, liveRooms, livePaths, liveShops] = await Promise.all([
        fetchLiveProfiles({ forceRefresh }),
        fetchLivePosts(undefined, { limit: 20, forceRefresh }),
        fetchLiveStories(undefined, { forceRefresh }),
        fetchLiveCommunities(activeSession?.id),
        fetchLiveUnloadRooms(),
        fetchLiveCrossedPaths(),
        fetchLiveShops()
      ]);

      setAllUsers(liveProfiles);
      setPosts(livePosts);
      setReels(livePosts.filter(p => p.isVybesReel || p.mediaType === 'REEL'));
      setHasMorePosts(livePosts.length >= 20);
      setStories(liveStories);
      setCommunities(liveComms);
      setUnloadRooms(liveRooms);
      setCrossedPaths(livePaths);
      setShops(liveShops);
      setIsDbConnected(true);

      if (activeSession) {
        await hydrateAuthenticatedUserData(activeSession);
      } else {
        setCurrentUser(null);
      }
    } catch (err: any) {
      setIsDbConnected(false);
      setDbError(err?.message || 'Unable to sync with VYBE backend right now.');
    } finally {
      setIsAuthChecking(false);
      setIsInitialLoading(false);
      setIsSyncing(false);
    }
  };

  useEffect(() => {
    syncDatabase(false);

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        if (Date.now() - lastSyncTimeRef.current > 90_000) {
          syncDatabase(true);
        }
      }
    };
    const handleOnline = () => {
      setIsOffline(false);
      syncDatabase(true);
    };
    const handleOffline = () => setIsOffline(true);

    document.addEventListener('visibilitychange', handleVisibilityChange);
    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);

    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  // Browser URL / History Sync for /u/{username}, /profile/{id}, /post/{id}, /download
  useEffect(() => {
    const handlePopOrHash = () => {
      const parsed = parseInitialPath();
      setIsDownloadPage(parsed.isDownload);
      if (parsed.postId) {
        setSharedPostModalId(parsed.postId);
      } else {
        setSharedPostModalId(null);
      }
      if (parsed.profileTarget) {
        setProfileRouteTarget(parsed.profileTarget);
        setSelectedTabState('PROFILE');
      } else {
        setProfileRouteTarget(null);
      }
    };
    window.addEventListener('popstate', handlePopOrHash);
    window.addEventListener('hashchange', handlePopOrHash);
    return () => {
      window.removeEventListener('popstate', handlePopOrHash);
      window.removeEventListener('hashchange', handlePopOrHash);
    };
  }, []);

  // Resolve Direct Public Profile Route (/u/{username} or /profile/{id}) from Supabase
  useEffect(() => {
    if (!profileRouteTarget) {
      setProfileLookupState('IDLE');
      setProfileLookupError(null);
      return;
    }

    let cancelled = false;
    setProfileLookupState('LOADING');
    setProfileLookupError(null);
    setViewedProfileUser(null);
    setViewedProfilePosts([]);

    fetchProfileByUsernameOrId({
      username: profileRouteTarget.username,
      userId: profileRouteTarget.userId
    }).then(async (res) => {
      if (cancelled) return;
      if (res.status === 'FOUND' && res.user) {
        setViewedProfileUser(res.user);
        setAllUsers(prev => (prev.some(u => u.id === res.user!.id) ? prev : [res.user!, ...prev]));
        const canViewPosts =
          !res.user.isPrivate ||
          (currentUser && res.user.id.toLowerCase() === currentUser.id.toLowerCase()) ||
          followingIds.includes(res.user.id);

        if (canViewPosts) {
          const userPosts = await fetchPostsByAuthor(res.user.id, 30);
          if (!cancelled) setViewedProfilePosts(userPosts);
          setProfileLookupState('FOUND');
        } else {
          setViewedProfilePosts([]);
          setProfileLookupState('FOUND');
        }
      } else if (res.status === 'NOT_FOUND') {
        setProfileLookupState('NOT_FOUND');
        setProfileLookupError('This VYBE profile is unavailable or no longer exists.');
      } else {
        setProfileLookupState('ERROR');
        setProfileLookupError(res.error || 'Could not load profile.');
      }
    });

    return () => {
      cancelled = true;
    };
  }, [profileRouteTarget, currentUser?.id, followingIds.length]);

  // Resolve Shared Post Modal directly from Supabase (/post/{id} or /p/{id})
  useEffect(() => {
    if (!sharedPostModalId) {
      setResolvedSharedPost(null);
      setSharedPostState('IDLE');
      setSharedPostError(null);
      return;
    }

    let cancelled = false;
    const cachedInState = [...posts, ...reels].find(p => p.id === sharedPostModalId) || null;
    if (cachedInState) {
      const blockedByPrivacy = Boolean(
        cachedInState.author.isPrivate &&
          cachedInState.author.id !== currentUser?.id &&
          !followingIds.includes(cachedInState.author.id)
      );
      setResolvedSharedPost(cachedInState);
      setSharedPostState(blockedByPrivacy ? 'PRIVATE' : 'FOUND');
      setSharedPostError(blockedByPrivacy ? 'This post belongs to a private account.' : null);
      return;
    }

    setSharedPostState('LOADING');
    setSharedPostError(null);
    setResolvedSharedPost(null);

    fetchSinglePostById(sharedPostModalId).then(res => {
      if (cancelled) return;
      if (res.status === 'FOUND' && res.post) {
        const blockedByPrivacy = Boolean(
          res.post.author.isPrivate &&
            res.post.author.id !== currentUser?.id &&
            !followingIds.includes(res.post.author.id)
        );
        setResolvedSharedPost(res.post);
        setSharedPostState(blockedByPrivacy ? 'PRIVATE' : 'FOUND');
        setSharedPostError(blockedByPrivacy ? 'This post belongs to a private account.' : null);
      } else if (res.status === 'NOT_FOUND') {
        setSharedPostState('NOT_FOUND');
        setSharedPostError('This post may have been deleted or the link is invalid.');
      } else {
        setSharedPostState('ERROR');
        setSharedPostError(res.error || 'Could not load shared post.');
      }
    });

    return () => {
      cancelled = true;
    };
  }, [sharedPostModalId, currentUser?.id, followingIds.length]);

  // Supabase Realtime Connection (Web <-> Android Instant DMs & Notifications)
  useEffect(() => {
    if (!authUser?.id || !authUser?.accessToken) {
      vybeRealtime.disconnect(true);
      setIsRealtimeConnected(false);
      return;
    }

    const handleRealtimePush = async (payload: RealtimePushPayload) => {
      const pushType = String(payload.type || '').toUpperCase();
      if (pushType === 'TYPING') return;

      if (payload.conversation_id) {
        const convMsgs = await fetchMessagesForConversation(payload.conversation_id, authUser.id);
        const existingExists = conversations.some(c => c.id === payload.conversation_id);
        if (!existingExists) {
          const fresh = await fetchConversationsForUser(authUser.id);
          setConversations(fresh.conversations);
        } else if (convMsgs.length > 0) {
          setConversations(prev =>
            prev.map(c => {
              if (c.id !== payload.conversation_id) return c;
              const isCurrentlyViewing =
                selectedTab === 'INBOX' && selectedConversationId === c.id;
              const latest = convMsgs[convMsgs.length - 1];
              return {
                ...c,
                messages: convMsgs,
                lastMessage: latest?.text || c.lastMessage,
                lastMessageTimestamp: latest?.createdAtIso
                  ? formatRelativeTime(latest.createdAtIso)
                  : c.lastMessageTimestamp,
                unreadCount: isCurrentlyViewing ? 0 : c.unreadCount + 1
              };
            })
          );
        }
      }

      if (payload.body) {
        const isViewingSameThread =
          selectedTab === 'INBOX' &&
          payload.conversation_id &&
          selectedConversationId === payload.conversation_id;
        if (!isViewingSameThread) {
          setInAppNotificationBanner(
            `${payload.actor_display_name || payload.title || 'New activity'}: ${payload.body}`
          );
        }
      }
    };

    const handleDbMessage = async (change: RealtimeDbChangePayload) => {
      const rec = change.record;
      if (!rec?.id || !rec?.conversation_id) return;
      const cid = String(rec.conversation_id);
      const isMe = String(rec.sender_id || '').toLowerCase() === authUser.id.toLowerCase();

      const incomingMsg: DirectMessage = {
        id: rec.id,
        senderId: rec.sender_id,
        text: rec.content || '',
        timestamp: formatChatClock(rec.created_at),
        createdAtIso: rec.created_at,
        isMe,
        mediaUrl: rec.media_url || undefined,
        mediaType: (String(rec.media_type || 'TEXT').toUpperCase() as any),
        status: 'SENT'
      };

      let matchedConv = false;
      setConversations(prev => {
        const idx = prev.findIndex(c => c.id === cid);
        if (idx === -1) return prev;
        matchedConv = true;
        const target = prev[idx];
        if (target.messages.some(m => m.id === incomingMsg.id)) {
          return prev;
        }
        const isViewing = selectedTab === 'INBOX' && selectedConversationId === cid;
        const updated: Conversation = {
          ...target,
          lastMessage: incomingMsg.text || (incomingMsg.mediaUrl ? 'Sent an attachment' : target.lastMessage),
          lastMessageTimestamp: 'Just now',
          lastMessageCreatedAtIso: incomingMsg.createdAtIso || new Date().toISOString(),
          unreadCount: isMe || isViewing ? 0 : target.unreadCount + 1,
          messages: [...target.messages, incomingMsg]
        };
        if (!isMe && isViewing) {
          markConversationReadInSupabase(authUser.id, [incomingMsg.id]);
        }
        return [updated, ...prev.filter((_, i) => i !== idx)];
      });

      if (!matchedConv) {
        const fresh = await fetchConversationsForUser(authUser.id);
        if (fresh.conversations.some(c => c.id === cid)) {
          setConversations(fresh.conversations);
        }
      }
    };

    const handleDbNotification = async (change: RealtimeDbChangePayload) => {
      const rec = change.record;
      if (!rec?.id) return;
      const freshNotifs = await fetchLiveNotifications(authUser.id);
      if (freshNotifs.length > 0) setNotifications(freshNotifs);
    };

    vybeRealtime.connect(authUser.id, authUser.accessToken, {
      onPushEvent: handleRealtimePush,
      onDbMessage: handleDbMessage,
      onDbNotification: handleDbNotification,
      onConnectionChange: setIsRealtimeConnected
    });

    return () => {
      vybeRealtime.disconnect(false);
    };
  }, [authUser?.id, authUser?.accessToken]);

  // GitHub release check
  const setGithubRepo = (newRepo: string) => {
    const cleaned = cleanRepoSlug(newRepo);
    setGithubRepoState(cleaned);
    localStorage.setItem('vybe_github_repo', cleaned);
  };

  const resetGithubRepo = () => {
    setGithubRepo(DEFAULT_GITHUB_REPO);
    setUpdateError(null);
    setUpdateInfo(null);
  };

  const checkForUpdates = async (repoToTest?: string, silent: boolean = false) => {
    const repo = repoToTest ? cleanRepoSlug(repoToTest) : githubRepo;
    setIsCheckingUpdates(true);
    setUpdateError(null);
    try {
      const info = await fetchGitHubUpdates(repo);
      setUpdateInfo(info);
      setHasAvailableUpdate(Boolean(info.isNewerThanInstalled));
      if (info.isNewerThanInstalled && !silent) {
        setInAppNotificationBanner(`VYBE ${info.tagName} release is available.`);
      }
    } catch (err: any) {
      if (!silent) setUpdateError(err.message || 'Failed to check GitHub releases.');
    } finally {
      setIsCheckingUpdates(false);
    }
  };

  useEffect(() => {
    checkForUpdates(undefined, true);
  }, []);

  // Call timer
  useEffect(() => {
    let timer: any;
    if (callState.isActive && !callState.isIncoming) {
      timer = setInterval(() => {
        setCallState(prev => ({ ...prev, durationSeconds: prev.durationSeconds + 1 }));
      }, 1000);
    }
    return () => clearInterval(timer);
  }, [callState.isActive, callState.isIncoming]);

  // Navigation & Profile Actions
  const closeDownloadPage = () => {
    setIsDownloadPage(false);
    if (/^\/download/i.test(window.location.pathname)) {
      window.history.pushState(null, '', '/');
    }
  };

  const setSelectedTab = (tab: MainTab) => {
    if (isDownloadPage) closeDownloadPage();
    if (tab === 'PROFILE') {
      if (!profileRouteTarget) {
        if (!currentUser) {
          openAuthModal('Sign in to view your profile, or open a shared /u/username link.');
          return;
        }
        setViewedProfileUser(null);
        setViewedProfilePosts([]);
      }
    } else {
      setProfileRouteTarget(null);
      setViewedProfileUser(null);
      if (/^\/(?:u|profile)\//i.test(window.location.pathname)) {
        window.history.pushState(null, '', '/');
      }
    }
    setSelectedTabState(tab);
  };

  const openUserProfile = (user: User) => {
    if (!user) return;
    if (currentUser && user.id.toLowerCase() === currentUser.id.toLowerCase()) {
      setProfileRouteTarget(null);
      setViewedProfileUser(null);
      setSelectedTabState('PROFILE');
      if (user.username) {
        window.history.pushState(null, '', `/u/${encodeURIComponent(user.username)}`);
      }
      return;
    }
    if (selectedTab !== 'PROFILE') {
      setPreviousTabBeforeProfile(selectedTab);
    }
    setViewedProfileUser(user);
    setProfileRouteTarget(user.username ? { username: user.username, userId: user.id } : { userId: user.id });
    setSelectedTabState('PROFILE');
    if (user.username) {
      window.history.pushState(null, '', `/u/${encodeURIComponent(user.username)}`);
    } else {
      window.history.pushState(null, '', `/profile/${encodeURIComponent(user.id)}`);
    }
  };

  const navigateToPublicProfile = (target: ProfileRouteTarget) => {
    if (selectedTab !== 'PROFILE') {
      setPreviousTabBeforeProfile(selectedTab);
    }
    setProfileRouteTarget(target);
    setSelectedTabState('PROFILE');
    if (target.username) {
      window.history.pushState(null, '', `/u/${encodeURIComponent(target.username)}`);
    } else if (target.userId) {
      window.history.pushState(null, '', `/profile/${encodeURIComponent(target.userId)}`);
    }
  };

  const closeUserProfile = () => {
    setProfileRouteTarget(null);
    setViewedProfileUser(null);
    setViewedProfilePosts([]);
    setProfileLookupState('IDLE');
    setProfileLookupError(null);
    setSelectedTabState(previousTabBeforeProfile || 'FEED');
    if (/^\/(?:u|profile)\//i.test(window.location.pathname)) {
      window.history.pushState(null, '', '/');
    }
  };

  const openModal = (modal: ModalScreen, data?: any) => {
    const authProtected: ModalScreen[] = [
      'CREATE_POST',
      'CREATE_STORY',
      'CREATE_CAPSULE',
      'CREATE_CROSSED_PATH',
      'VERIFICATION_CENTER',
      'QR_CODE'
    ];
    if (authProtected.includes(modal) && !requireAuth('Sign in to your VYBE account to use this feature.')) {
      return;
    }
    if (modal === 'SHOP_DETAIL' && data) setSelectedShop(data);
    if (modal === 'UNLOAD_ROOM' && data) setSelectedUnloadRoom(data);
    setActiveModal(modal);
  };

  const closeModal = () => setActiveModal(null);

  const openSharedPostModal = (postId: string) => {
    setSharedPostModalId(postId);
    window.history.pushState(null, '', `/post/${encodeURIComponent(postId)}`);
  };

  const closeSharedPostModal = () => {
    setSharedPostModalId(null);
    setResolvedSharedPost(null);
    setSharedPostState('IDLE');
    if (/^\/(?:post|p)\//i.test(window.location.pathname)) {
      window.history.replaceState(null, '', '/');
    } else if (window.location.hash.startsWith('#post_')) {
      window.history.replaceState(null, '', window.location.pathname + window.location.search);
    }
  };

  // Social Follow / Dismiss
  const toggleFollowUser = (targetUserId: string) => {
    if (!requireAuth('Sign in to follow creators and friends on VYBE.')) return;
    if (!currentUser || !targetUserId || targetUserId === currentUser.id) return;

    const targetUser = allUsers.find(u => u.id === targetUserId) || viewedProfileUser;
    const isFollowing = followingIds.includes(targetUserId);
    const isRequested = requestedFollowIds.includes(targetUserId);

    if (isFollowing || isRequested) {
      setFollowingIds(prev => prev.filter(id => id !== targetUserId));
      setRequestedFollowIds(prev => prev.filter(id => id !== targetUserId));
      setAllUsers(prev =>
        prev.map(u =>
          u.id === targetUserId
            ? {
                ...u,
                isFollowedByMe: false,
                isFollowRequestedByMe: false,
                followersCount: isFollowing ? Math.max(0, u.followersCount - 1) : u.followersCount
              }
            : u
        )
      );
      toggleFollowInSupabase({ followerId: currentUser.id, followedId: targetUserId, action: 'UNFOLLOW' });
    } else if (targetUser?.isPrivate) {
      setRequestedFollowIds(prev => [...prev, targetUserId]);
      setAllUsers(prev => prev.map(u => (u.id === targetUserId ? { ...u, isFollowRequestedByMe: true } : u)));
      toggleFollowInSupabase({ followerId: currentUser.id, followedId: targetUserId, action: 'REQUEST' });
    } else {
      setFollowingIds(prev => [...prev, targetUserId]);
      setAllUsers(prev =>
        prev.map(u =>
          u.id === targetUserId
            ? { ...u, isFollowedByMe: true, followersCount: u.followersCount + 1 }
            : u
        )
      );
      toggleFollowInSupabase({ followerId: currentUser.id, followedId: targetUserId, action: 'FOLLOW' });
    }
  };

  const dismissSuggestedUser = (targetUserId: string) => {
    if (!targetUserId) return;
    setDismissedSuggestionIds(prev => (prev.includes(targetUserId) ? prev : [...prev, targetUserId]));
  };

  const restoreDismissedSuggestions = () => setDismissedSuggestionIds([]);

  const setCurrentUserMood = (mood: MoodType) => {
    if (!requireAuth('Sign in to update your live mood.')) return;
    setCurrentUser(prev => (prev ? { ...prev, currentMood: mood } : prev));
    if (currentUser) {
      updateProfileInSupabase(currentUser.id, { current_mood: mood });
    }
  };

  const setCurrentUserPulse = (pulse: LivePulseStatus) => {
    if (!requireAuth('Sign in to update your live pulse status.')) return;
    setCurrentUser(prev => (prev ? { ...prev, livePulse: pulse } : prev));
    if (currentUser) {
      updateProfileInSupabase(currentUser.id, { live_pulse: pulse });
    }
  };

  // Posts & Pagination & Reactions
  const loadMorePosts = async () => {
    if (isLoadingMorePosts || !hasMorePosts) return;
    setIsLoadingMorePosts(true);
    try {
      const oldestPost = posts[posts.length - 1];
      const cursorCreatedAt = oldestPost?.createdAtIso;
      const morePosts = await fetchLivePosts(undefined, { limit: 20, cursorCreatedAt });
      if (!morePosts || morePosts.length === 0) {
        setHasMorePosts(false);
      } else {
        if (morePosts.length < 20) setHasMorePosts(false);
        setPosts(prev => {
          const existingIds = new Set(prev.map(p => p.id));
          return [...prev, ...morePosts.filter(p => !existingIds.has(p.id))];
        });
      }
    } finally {
      setIsLoadingMorePosts(false);
    }
  };

  const applyPostTransform = (postId: string, updater: (p: Post) => Post, nextReaction?: VybeReaction | null) => {
    let updatedLikes: number | null = null;
    setPosts(prev =>
      prev.map(p => {
        if (p.id !== postId) return p;
        const next = updater(p);
        updatedLikes = next.likesCount;
        return next;
      })
    );
    setReels(prev =>
      prev.map(p => {
        if (p.id !== postId) return p;
        const next = updater(p);
        if (updatedLikes === null) updatedLikes = next.likesCount;
        return next;
      })
    );
    setViewedProfilePosts(prev =>
      prev.map(p => (p.id === postId ? updater(p) : p))
    );
    if (resolvedSharedPost?.id === postId) {
      setResolvedSharedPost(prev => (prev ? updater(prev) : prev));
    }
    if (updatedLikes !== null) {
      syncPostLikeCountToSupabase(postId, updatedLikes);
    }
    if (currentUser && nextReaction !== undefined) {
      togglePostReactionInSupabase({ postId, userId: currentUser.id, reactionType: nextReaction });
    }
  };

  const toggleLikePost = (postId: string) => {
    if (!requireAuth('Sign in to react to posts on VYBE.')) return;
    const target = posts.find(p => p.id === postId) || reels.find(p => p.id === postId);
    const willLike = !target?.isLiked;
    applyPostTransform(
      postId,
      p => {
        const isLiked = !p.isLiked;
        const nextLikes = isLiked ? p.likesCount + 1 : Math.max(0, p.likesCount - 1);
        const nextReactions = { ...p.reactionsCount };
        if (isLiked) {
          nextReactions.LOVE = (nextReactions.LOVE || 0) + 1;
        } else if (p.userReaction) {
          nextReactions[p.userReaction] = Math.max(0, (nextReactions[p.userReaction] || 1) - 1);
        } else {
          nextReactions.LOVE = Math.max(0, (nextReactions.LOVE || 1) - 1);
        }
        return {
          ...p,
          isLiked,
          likesCount: nextLikes,
          reactionsCount: nextReactions,
          userReaction: isLiked ? p.userReaction || 'LOVE' : undefined
        };
      },
      willLike ? target?.userReaction || 'LOVE' : null
    );
  };

  const ensurePostLoved = (postId: string) => {
    if (!requireAuth('Sign in to react to posts on VYBE.')) return;
    applyPostTransform(
      postId,
      p => {
        if (p.isLiked) return p;
        return {
          ...p,
          isLiked: true,
          likesCount: p.likesCount + 1,
          reactionsCount: { ...p.reactionsCount, LOVE: (p.reactionsCount?.LOVE || 0) + 1 },
          userReaction: 'LOVE'
        };
      },
      'LOVE'
    );
  };

  const reactToPost = (postId: string, reaction: VybeReaction) => {
    if (!requireAuth('Sign in to react to posts on VYBE.')) return;
    const target = posts.find(p => p.id === postId) || reels.find(p => p.id === postId);
    const nextReaction = target?.userReaction === reaction ? null : reaction;
    applyPostTransform(
      postId,
      p => {
        const currentRx = p.userReaction;
        const resolved = currentRx === reaction ? undefined : reaction;
        const newReactionsCount = { ...p.reactionsCount };
        if (currentRx) newReactionsCount[currentRx] = Math.max(0, (newReactionsCount[currentRx] || 1) - 1);
        if (resolved) newReactionsCount[resolved] = (newReactionsCount[resolved] || 0) + 1;
        return {
          ...p,
          userReaction: resolved,
          isLiked: !!resolved,
          reactionsCount: newReactionsCount,
          likesCount: Object.values(newReactionsCount).reduce((a, b) => a + b, 0)
        };
      },
      nextReaction
    );
  };

  const reactToComment = (postId: string, commentId: string, reaction: VybeReaction) => {
    if (!requireAuth('Sign in to react to comments.')) return;
    const mapComments = (p: Post): Post => {
      if (p.id !== postId) return p;
      return {
        ...p,
        comments: (p.comments || []).map(c => {
          if (c.id !== commentId) return c;
          const prevRx = c.userReaction || (c.isLiked ? 'LOVE' : undefined);
          const nextRx = prevRx === reaction ? undefined : reaction;
          const nextCount =
            nextRx && !prevRx
              ? (c.likesCount || 0) + 1
              : !nextRx && prevRx
                ? Math.max(0, (c.likesCount || 0) - 1)
                : c.likesCount || 0;
          return { ...c, isLiked: Boolean(nextRx), userReaction: nextRx, likesCount: nextCount };
        })
      };
    };
    setPosts(prev => prev.map(mapComments));
    setReels(prev => prev.map(mapComments));
  };

  const toggleCommentLike = (postId: string, commentId: string) => {
    const targetPost = posts.find(p => p.id === postId) || reels.find(p => p.id === postId);
    const targetComment = targetPost?.comments?.find(c => c.id === commentId);
    const existing = targetComment?.userReaction || (targetComment?.isLiked ? 'LOVE' : undefined);
    reactToComment(postId, commentId, existing || 'LOVE');
  };

  const toggleSavePost = (postId: string) => {
    if (!requireAuth('Sign in to save posts to your collection.')) return;
    if (!currentUser) return;
    const target = posts.find(p => p.id === postId) || reels.find(p => p.id === postId);
    const shouldSave = !target?.isSaved;
    const mapSave = (p: Post): Post => {
      if (p.id !== postId) return p;
      const isSaved = !p.isSaved;
      return {
        ...p,
        isSaved,
        savesCount: isSaved ? p.savesCount + 1 : Math.max(0, p.savesCount - 1)
      };
    };
    setPosts(prev => prev.map(mapSave));
    setReels(prev => prev.map(mapSave));
    toggleSavePostInSupabase({ postId, userId: currentUser.id, shouldSave });
  };

  const loadCommentsForPost = async (postId: string) => {
    const fetched = await fetchCommentsForPost(postId);
    if (!fetched) return;
    const attach = (p: Post): Post => (p.id === postId ? { ...p, comments: fetched } : p);
    setPosts(prev => prev.map(attach));
    setReels(prev => prev.map(attach));
    if (resolvedSharedPost?.id === postId) {
      setResolvedSharedPost(prev => (prev ? { ...prev, comments: fetched } : prev));
    }
  };

  const addCommentToPost = (postId: string, text: string) => {
    if (!requireAuth('Sign in to comment on posts.')) return;
    if (!currentUser || !text.trim()) return;
    const nowIso = new Date().toISOString();
    const tempId = `c_${Date.now()}`;
    const newComment = {
      id: tempId,
      postId,
      author: currentUser,
      content: text.trim(),
      timestamp: 'Just now',
      createdAtIso: nowIso,
      likesCount: 0,
      isLiked: false
    };
    const appendComment = (p: Post): Post => {
      if (p.id !== postId) return p;
      return {
        ...p,
        commentsCount: p.commentsCount + 1,
        comments: [newComment, ...(p.comments || [])]
      };
    };
    setPosts(prev => prev.map(appendComment));
    setReels(prev => prev.map(appendComment));
    submitLiveComment(postId, currentUser.id, text.trim()).then(created => {
      if (created?.id) {
        const swapId = (p: Post): Post =>
          p.id !== postId
            ? p
            : { ...p, comments: p.comments.map(c => (c.id === tempId ? { ...c, id: created.id } : c)) };
        setPosts(prev => prev.map(swapId));
        setReels(prev => prev.map(swapId));
      }
    });
  };

  const incrementPostShareCount = (postId: string) => {
    setPosts(prev => prev.map(p => (p.id === postId ? { ...p, sharesCount: (p.sharesCount || 0) + 1 } : p)));
  };

  const createPost = (postData: Partial<Post>) => {
    if (!requireAuth('Sign in to publish posts on VYBE.')) return;
    if (!currentUser) return;
    const nowIso = new Date().toISOString();
    const tempId = `temp_post_${Date.now()}`;
    const newPost: Post = {
      id: tempId,
      author: currentUser,
      caption: postData.caption || '',
      mediaUrls: postData.mediaUrls || [],
      mediaType: postData.isVybesReel ? 'REEL' : postData.mediaType || 'TEXT',
      timestamp: 'Just now',
      createdAtIso: nowIso,
      likesCount: 0,
      commentsCount: 0,
      savesCount: 0,
      sharesCount: 0,
      viewsCount: 1,
      isLiked: false,
      isSaved: false,
      isVybesReel: Boolean(postData.isVybesReel),
      tags: postData.tags || [],
      moodZone: postData.moodZone || currentUser.currentMood || 'GENERAL',
      comments: [],
      locationName: postData.locationName,
      audioTrackTitle: postData.audioTrackTitle,
      musicAttachment: postData.musicAttachment,
      reactionsCount: { LOVE: 0, FLAME: 0, LAUGH: 0, WOW: 0, SAD: 0 }
    };
    if (newPost.isVybesReel) setReels(prev => [newPost, ...prev]);
    setPosts(prev => [newPost, ...prev]);
    closeModal();

    createLivePost({
      authorId: currentUser.id,
      caption: newPost.caption,
      mediaUrls: newPost.mediaUrls,
      mediaType: newPost.mediaType,
      isVybesReel: newPost.isVybesReel,
      moodZone: newPost.moodZone
    }).then(created => {
      if (created?.id) {
        setPosts(prev => prev.map(p => (p.id === tempId ? { ...p, id: created.id } : p)));
        setReels(prev => prev.map(p => (p.id === tempId ? { ...p, id: created.id } : p)));
      } else {
        setInAppNotificationBanner('Post failed to save to Supabase. Please check your connection and try again.');
      }
    });
  };

  // Stories
  const openStoryViewer = (authorId: string, index?: number) => {
    const normalizedAuthor = (authorId || '').toLowerCase();
    const matchedIdx =
      typeof index === 'number' && index >= 0 && index < stories.length
        ? index
        : stories.findIndex(s => (s.author?.id || '').toLowerCase() === normalizedAuthor);
    const safeIdx = matchedIdx >= 0 ? matchedIdx : 0;
    const targetStory = stories[safeIdx];
    if (targetStory) {
      setStories(prev => prev.map((s, idx) => (idx === safeIdx ? { ...s, isViewed: true } : s)));
    }
    setActiveStoryAuthorId(authorId);
    setActiveStoryIndex(safeIdx);
    openModal('STORY_VIEWER');
  };
  const closeStoryViewer = () => {
    setActiveStoryAuthorId(null);
    closeModal();
  };
  const nextStory = () => {
    setActiveStoryIndex(prev => {
      if (prev < stories.length - 1) {
        const nextIdx = prev + 1;
        setStories(list => list.map((s, idx) => (idx === nextIdx ? { ...s, isViewed: true } : s)));
        return nextIdx;
      }
      closeStoryViewer();
      return prev;
    });
  };
  const prevStory = () => setActiveStoryIndex(prev => (prev > 0 ? prev - 1 : 0));

  const reactToStory = (storyId: string, reaction: VybeReaction) => {
    if (!requireAuth('Sign in to react to stories.')) return;
    setStories(prev =>
      prev.map(s => {
        if (s.id !== storyId) return s;
        const prevRx = s.myReaction || (s.isLikedByMe ? 'LOVE' : undefined);
        const nextRx = prevRx === reaction ? undefined : reaction;
        const nextCount =
          nextRx && !prevRx
            ? (s.reactionsCount || 0) + 1
            : !nextRx && prevRx
              ? Math.max(0, (s.reactionsCount || 0) - 1)
              : s.reactionsCount || 0;
        return { ...s, isLikedByMe: Boolean(nextRx), myReaction: nextRx, reactionsCount: nextCount };
      })
    );
  };
  const toggleStoryLike = (storyId: string) => {
    const target = stories.find(s => s.id === storyId);
    const existing = target?.myReaction || (target?.isLikedByMe ? 'LOVE' : undefined);
    reactToStory(storyId, existing || 'LOVE');
  };

  const createStory = (storyData: Partial<Story>) => {
    if (!requireAuth('Sign in to publish stories.')) return;
    if (!currentUser) return;
    const nowMs = Date.now();
    const nowIso = new Date(nowMs).toISOString();
    const expIso = new Date(nowMs + 24 * 60 * 60 * 1000).toISOString();
    const resolvedType = storyData.mediaType || (storyData.mediaUrl ? 'PHOTO' : 'TEXT');
    const tempId = `st_${nowMs}`;
    const newStory: Story = {
      id: tempId,
      author: currentUser,
      mediaUrl: storyData.mediaUrl || '',
      caption: storyData.caption || '',
      mediaType: resolvedType,
      audience: storyData.audience || 'Public',
      timestamp: 'Just now',
      createdAtIso: nowIso,
      expiresAtIso: expIso,
      expiresHoursRemaining: 24,
      isViewed: false,
      reactionsCount: 0,
      isLikedByMe: false,
      textBackgroundColor:
        storyData.textBackgroundColor || (resolvedType === 'TEXT' ? 'from-[#6366F1] to-[#EC4899]' : undefined),
      musicTrackTitle: storyData.musicTrackTitle
    };
    setStories(prev => [newStory, ...prev]);
    closeModal();
    createLiveStory({
      authorId: currentUser.id,
      mediaUrl: newStory.mediaUrl,
      caption: newStory.caption,
      mediaType: newStory.mediaType
    }).then(created => {
      if (created?.id) {
        setStories(prev => prev.map(s => (s.id === tempId ? { ...s, id: created.id } : s)));
        setInAppNotificationBanner('Story published! Visible for 24 hours.');
      } else {
        setInAppNotificationBanner('Story upload could not be confirmed by server.');
      }
    });
  };

  // Crossed Paths, Capsules, Rooms, Intentions, Communities
  const sendConnectionRequest = (id: string) => {
    if (!requireAuth('Sign in to send a connection request.')) return;
    setCrossedPaths(prev =>
      prev.map(cp => (cp.id === id ? { ...cp, hasSentRequest: true, requestStatus: 'PENDING' } : cp))
    );
  };

  const createCrossedPath = (encounter: Partial<CrossedPathEncounter>) => {
    if (!requireAuth('Sign in to post a Crossed Path.')) return;
    if (!currentUser) return;
    const newEncounter: CrossedPathEncounter = {
      id: `cp_${Date.now()}`,
      type: encounter.type || 'MISSED_CONNECTION',
      title: encounter.title || 'Untitled Encounter',
      description: encounter.description || '',
      broadLocation: encounter.broadLocation || 'Nearby',
      venueOrEventName: encounter.venueOrEventName,
      encounterDate: 'Just now',
      poster: currentUser,
      isAnonymous: encounter.isAnonymous ?? true,
      expiresHoursRemaining: 72,
      potentialMatchesCount: 1,
      hasSentRequest: false,
      requestStatus: 'PENDING',
      tags: encounter.tags || []
    };
    setCrossedPaths(prev => [newEncounter, ...prev]);
    closeModal();
  };

  const createTimeCapsule = (capsule: Partial<TimeCapsule>) => {
    if (!requireAuth('Sign in to seal a Time Capsule.')) return;
    if (!currentUser) return;
    const newCap: TimeCapsule = {
      id: `cap_${Date.now()}`,
      title: capsule.title || 'My Future Capsule',
      sender: currentUser,
      recipientSummary: capsule.recipientSummary || 'To Myself',
      sealedMessage: capsule.sealedMessage || '',
      unlockDateFormatted: capsule.unlockDateFormatted || 'In 1 Year',
      unlockTimestampMs: capsule.unlockTimestampMs || Date.now() + 1000 * 60 * 60 * 24 * 365,
      isUnlocked: false,
      createdAtFormatted: 'Today',
      categoryTheme: capsule.categoryTheme || 'Life Reflection'
    };
    setTimeCapsules(prev => [newCap, ...prev]);
    closeModal();
  };

  const joinUnloadRoom = (room: UnloadRoom) => {
    setSelectedUnloadRoom(room);
    openModal('UNLOAD_ROOM', room);
  };

  const sendUnloadMessage = (roomId: string, text: string, isAnonymous: boolean) => {
    if (!requireAuth('Sign in to participate in Unload Rooms.')) return;
    if (!currentUser || !text.trim()) return;
    const newMsg = {
      id: `um_${Date.now()}`,
      senderName: isAnonymous ? 'Anonymous Listener' : currentUser.displayName,
      isAnonymous,
      text: text.trim(),
      timestamp: 'Just now',
      isMe: true
    };
    setUnloadRooms(prev =>
      prev.map(r => (r.id === roomId ? { ...r, recentMessages: [...r.recentMessages, newMsg] } : r))
    );
    if (selectedUnloadRoom?.id === roomId) {
      setSelectedUnloadRoom(prev => (prev ? { ...prev, recentMessages: [...prev.recentMessages, newMsg] } : null));
    }
  };

  const addIntention = async (text: string) => {
    if (!requireAuth('Sign in to save personal intentions.')) return;
    if (!currentUser || !text.trim()) return;
    const saved = await addSpiritualIntentionInSupabase(currentUser.id, text);
    if (saved) {
      setIntentions(prev => [saved, ...prev]);
    }
  };

  const toggleIntention = (id: string) => {
    if (!requireAuth('Sign in to update intentions.')) return;
    const target = intentions.find(i => i.id === id);
    const nextFulfilled = !target?.isFulfilled;
    setIntentions(prev => prev.map(i => (i.id === id ? { ...i, isFulfilled: nextFulfilled } : i)));
    toggleSpiritualIntentionInSupabase(id, nextFulfilled);
  };

  const toggleJoinCommunity = (commId: string) => {
    if (!requireAuth('Sign in to join communities.')) return;
    if (!currentUser) return;
    const target = communities.find(c => c.id === commId);
    const shouldJoin = !target?.isJoined;
    setCommunities(prev =>
      prev.map(c =>
        c.id === commId
          ? {
              ...c,
              isJoined: shouldJoin,
              membersCount: shouldJoin ? c.membersCount + 1 : Math.max(0, c.membersCount - 1)
            }
          : c
      )
    );
    toggleJoinCommunityInSupabase({ communityId: commId, userId: currentUser.id, shouldJoin });
  };

  const openShopDetail = (shop: VybeShop) => {
    setSelectedShop(shop);
    openModal('SHOP_DETAIL', shop);
  };

  // Direct Messaging (100% Supabase-backed, Real Statuses & Retry & Read Receipts)
  const selectedConversation = React.useMemo(() => {
    if (!selectedConversationId && !selectedConversationPeerId) return null;
    return (
      conversations.find(
        c =>
          (selectedConversationId && c.id === selectedConversationId) ||
          (selectedConversationPeerId && c.peerUser.id === selectedConversationPeerId)
      ) || null
    );
  }, [conversations, selectedConversationId, selectedConversationPeerId]);

  const refreshConversations = async () => {
    if (!currentUser) return;
    setIsLoadingConversations(true);
    setDmError(null);
    const res = await fetchConversationsForUser(currentUser.id);
    setConversations(res.conversations);
    if (res.error) setDmError(res.error);
    setIsLoadingConversations(false);
  };

  const selectConversation = (conv: Conversation | null) => {
    if (!conv) {
      setSelectedConversationId(null);
      setSelectedConversationPeerId(null);
      return;
    }
    setSelectedConversationId(conv.id);
    setSelectedConversationPeerId(conv.peerUser.id);
    setDmError(null);

    const incomingIds = conv.messages.filter(m => !m.isMe).map(m => m.id);
    if (currentUser && incomingIds.length > 0) {
      markConversationReadInSupabase(currentUser.id, incomingIds);
    }

    setConversations(prev =>
      prev.map(c =>
        c.id === conv.id || c.peerUser.id === conv.peerUser.id ? { ...c, unreadCount: 0 } : c
      )
    );

    if (currentUser && /^[0-9a-f-]{36}$/i.test(conv.id)) {
      fetchMessagesForConversation(conv.id, currentUser.id).then(freshMsgs => {
        if (freshMsgs.length > 0) {
          setConversations(prev =>
            prev.map(c => {
              if (c.id !== conv.id) return c;
              const failedOrPending = c.messages.filter(m => m.status === 'SENDING' || m.status === 'FAILED');
              const existingIds = new Set(freshMsgs.map(m => m.id));
              return {
                ...c,
                unreadCount: 0,
                messages: [...freshMsgs, ...failedOrPending.filter(m => !existingIds.has(m.id))]
              };
            })
          );
          const unreadServerIds = freshMsgs.filter(m => !m.isMe).map(m => m.id);
          if (unreadServerIds.length > 0) {
            markConversationReadInSupabase(currentUser.id, unreadServerIds);
          }
        }
      });
    }
  };

  const startConversationWithUser = async (
    peerUser: User
  ): Promise<{ ok: boolean; conversation?: Conversation; error?: string }> => {
    if (!requireAuth('Sign in to send direct messages on VYBE.')) {
      return { ok: false, error: 'Sign in required.' };
    }
    if (!currentUser) return { ok: false, error: 'Sign in required.' };
    if (peerUser.id === currentUser.id) return { ok: false, error: 'You cannot message yourself.' };

    setDmError(null);
    const existing = conversations.find(c => c.peerUser.id === peerUser.id);
    if (existing) {
      selectConversation(existing);
      return { ok: true, conversation: existing };
    }

    const rpc = await resolveOrCreateWebDirectConversation(currentUser.id, peerUser.id);
    if (!rpc.conversationId) {
      const msg = rpc.error || 'Cannot open conversation due to recipient privacy settings.';
      setDmError(msg);
      return { ok: false, error: msg };
    }

    const freshMsgs = await fetchMessagesForConversation(rpc.conversationId, currentUser.id);
    const latest = freshMsgs[freshMsgs.length - 1];
    const created: Conversation = {
      id: rpc.conversationId,
      peerUser,
      lastMessage: latest?.text || '',
      lastMessageTimestamp: latest?.createdAtIso ? formatRelativeTime(latest.createdAtIso) : 'Just now',
      lastMessageCreatedAtIso: latest?.createdAtIso || new Date().toISOString(),
      unreadCount: 0,
      messages: freshMsgs
    };

    setConversations(prev => {
      const filtered = prev.filter(c => c.id !== created.id && c.peerUser.id !== peerUser.id);
      return [created, ...filtered];
    });
    setSelectedConversationId(created.id);
    setSelectedConversationPeerId(peerUser.id);
    return { ok: true, conversation: created };
  };

  const sendMessage = async (convId: string, text: string, attachmentFile?: Blob) => {
    if (!requireAuth('Sign in to send messages.')) return;
    if (!currentUser) return;

    const cleanText = text.trim();
    if (!cleanText && !attachmentFile) return;

    const existingConv = conversations.find(
      c => c.id === convId || (selectedConversationPeerId && c.peerUser.id === selectedConversationPeerId)
    );
    const targetPeerId = existingConv?.peerUser.id || selectedConversationPeerId || '';
    if (!targetPeerId) {
      setDmError('Recipient could not be resolved for this conversation.');
      return;
    }

    setDmError(null);
    const clientMsgId = `dm_${Date.now()}_${Math.floor(Math.random() * 1000)}`;
    const nowTime = formatChatClock(new Date().toISOString());

    const optimisticMsg: DirectMessage = {
      id: clientMsgId,
      clientId: clientMsgId,
      senderId: currentUser.id,
      text: cleanText || (attachmentFile ? 'Photo' : ''),
      timestamp: nowTime,
      createdAtIso: new Date().toISOString(),
      isMe: true,
      mediaType: attachmentFile ? 'IMAGE' : 'TEXT',
      status: 'SENDING'
    };

    setConversations(prev => {
      const idx = prev.findIndex(c => c.id === convId || c.peerUser.id === targetPeerId);
      if (idx === -1) return prev;
      const target = prev[idx];
      const updatedConv: Conversation = {
        ...target,
        lastMessage: optimisticMsg.text,
        lastMessageTimestamp: 'Sending...',
        unreadCount: 0,
        messages: [...target.messages, optimisticMsg]
      };
      return [updatedConv, ...prev.filter((_, i) => i !== idx)];
    });

    let uploadedMediaUrl: string | undefined;
    if (attachmentFile) {
      const uploaded = await uploadMediaBlobToStorage(
        attachmentFile,
        'chat-media',
        'dm_attachment.webp',
        attachmentFile.size,
        { isPrivate: true }
      );
      if (!uploaded) {
        const errText = 'Photo upload failed. Retry sending attachment.';
        setDmError(errText);
        setConversations(prev =>
          prev.map(c =>
            c.id === convId || c.peerUser.id === targetPeerId
              ? {
                  ...c,
                  messages: c.messages.map(m =>
                    m.id === clientMsgId ? { ...m, status: 'FAILED', errorMessage: errText } : m
                  )
                }
              : c
          )
        );
        return;
      }
      uploadedMediaUrl = uploaded.publicUrl;
    }

    const serverRes = await sendLiveDirectMessage({
      conversationId: existingConv?.id || convId,
      senderId: currentUser.id,
      peerUserId: targetPeerId,
      content: cleanText || (uploadedMediaUrl ? '📷 Photo' : ''),
      mediaUrl: uploadedMediaUrl,
      mediaType: uploadedMediaUrl ? 'IMAGE' : 'TEXT',
      actorUsername: currentUser.username,
      actorDisplayName: currentUser.displayName,
      actorAvatarUrl: currentUser.avatarUrl
    });

    if (!serverRes.ok || !serverRes.messageId) {
      const errMsg = serverRes.error || 'Message failed to deliver.';
      setDmError(errMsg);
      setConversations(prev =>
        prev.map(c =>
          c.id === convId || c.peerUser.id === targetPeerId
            ? {
                ...c,
                lastMessageTimestamp: 'Failed',
                messages: c.messages.map(m =>
                  m.id === clientMsgId ? { ...m, status: 'FAILED', errorMessage: errMsg } : m
                )
              }
            : c
        )
      );
      return;
    }

    setConversations(prev =>
      prev.map(c => {
        if (c.id !== convId && c.peerUser.id !== targetPeerId) return c;
        const serverId = serverRes.messageId!;
        const filteredDup = c.messages.filter(m => m.id !== serverId || m.id === clientMsgId);
        return {
          ...c,
          id: serverRes.conversationId || c.id,
          lastMessage: optimisticMsg.text,
          lastMessageTimestamp: 'Just now',
          lastMessageCreatedAtIso: serverRes.createdAt || new Date().toISOString(),
          messages: filteredDup.map(m =>
            m.id === clientMsgId
              ? {
                  ...m,
                  id: serverId,
                  mediaUrl: uploadedMediaUrl || m.mediaUrl,
                  timestamp: formatChatClock(serverRes.createdAt),
                  createdAtIso: serverRes.createdAt || m.createdAtIso,
                  status: 'SENT',
                  errorMessage: undefined
                }
              : m
          )
        };
      })
    );

    if (serverRes.conversationId && selectedConversationId === convId) {
      setSelectedConversationId(serverRes.conversationId);
    }
  };

  const retryMessage = async (convId: string, messageId: string) => {
    const conv = conversations.find(c => c.id === convId);
    const failedMsg = conv?.messages.find(m => m.id === messageId && m.status === 'FAILED');
    if (!conv || !failedMsg) return;

    setConversations(prev =>
      prev.map(c =>
        c.id === convId
          ? { ...c, messages: c.messages.filter(m => m.id !== messageId) }
          : c
      )
    );
    await sendMessage(convId, failedMsg.text);
  };

  const sharePostToConversation = async (post: Post, convId: string, note?: string) => {
    const shareUrl = `https://vybeonline.netlify.app/post/${encodeURIComponent(post.id)}`;
    const bodyLines = [
      ...(note?.trim() ? [note.trim()] : []),
      `Shared post from @${post.author.username}${post.caption ? `: ${post.caption.slice(0, 140)}` : ''}`,
      shareUrl
    ];
    await sendMessage(convId, bodyLines.join('\n'));
    incrementPostShareCount(post.id);
    setInAppNotificationBanner('Post shared cleanly via Direct Message.');
  };

  const markNotificationRead = (id: string) => {
    setNotifications(prev => prev.map(n => (n.id === id ? { ...n, isRead: true } : n)));
    markNotificationReadInSupabase(id);
  };

  // Calls & Audio
  const startCall = (peerUser: User, isVideo: boolean) => {
    if (!requireAuth('Sign in to start a call.')) return;
    setCallState({
      isActive: true,
      isIncoming: false,
      peerUser,
      isVideo,
      isMuted: false,
      isSpeakerOn: true,
      isCameraOff: false,
      durationSeconds: 0
    });
    openModal('CALL_SCREEN');
  };
  const acceptCall = () => setCallState(prev => ({ ...prev, isIncoming: false }));
  const declineCall = () => {
    setCallState({
      isActive: false,
      isIncoming: false,
      peerUser: null,
      isVideo: false,
      isMuted: false,
      isSpeakerOn: true,
      isCameraOff: false,
      durationSeconds: 0
    });
    closeModal();
  };
  const endCall = () => declineCall();
  const toggleCallMute = () => setCallState(prev => ({ ...prev, isMuted: !prev.isMuted }));
  const toggleCallCamera = () => setCallState(prev => ({ ...prev, isCameraOff: !prev.isCameraOff }));

  const togglePlayAudio = (trackTitle: string, previewUrl?: string) => {
    if (isPlayingAudio && currentAudioTrack === trackTitle) {
      audioElement?.pause();
      setIsPlayingAudio(false);
      setCurrentAudioTrack(null);
      return;
    }
    if (audioElement) audioElement.pause();
    if (!previewUrl) {
      setIsPlayingAudio(false);
      setCurrentAudioTrack(null);
      setInAppNotificationBanner('Audio preview is unavailable for this track.');
      return;
    }
    const newAudio = new Audio(previewUrl);
    newAudio
      .play()
      .then(() => {
        setIsPlayingAudio(true);
        setCurrentAudioTrack(trackTitle);
        setAudioElement(newAudio);
      })
      .catch(() => {
        setIsPlayingAudio(false);
        setCurrentAudioTrack(null);
      });
    newAudio.onended = () => {
      setIsPlayingAudio(false);
      setCurrentAudioTrack(null);
    };
  };

  return (
    <VybeContext.Provider
      value={{
        authUser,
        isAuthChecking,
        currentUser,
        showAuthModal,
        authModalPrompt,
        authModalInitialMode,
        openAuthModal,
        closeAuthModal,
        requireAuth,
        signIn,
        signUp,
        requestPasswordReset,
        signOut,
        updateMyProfile,
        allUsers,
        viewedProfileUser,
        viewedProfilePosts,
        profileRouteTarget,
        profileLookupState,
        profileLookupError,
        openUserProfile,
        navigateToPublicProfile,
        closeUserProfile,
        setCurrentUserMood,
        setCurrentUserPulse,
        isDownloadPage,
        closeDownloadPage,
        appMode,
        setAppMode,
        growTab,
        setGrowTab,
        selectedTab,
        setSelectedTab,
        activeModal,
        openModal,
        closeModal,
        selectedMoodFilter,
        setSelectedMoodFilter,
        followingIds,
        requestedFollowIds,
        dismissedSuggestionIds,
        toggleFollowUser,
        dismissSuggestedUser,
        restoreDismissedSuggestions,
        posts,
        reels,
        toggleLikePost,
        ensurePostLoved,
        reactToPost,
        toggleCommentLike,
        reactToComment,
        toggleSavePost,
        addCommentToPost,
        loadCommentsForPost,
        createPost,
        sharePostToConversation,
        incrementPostShareCount,
        sharedPostModalId,
        resolvedSharedPost,
        sharedPostState,
        sharedPostError,
        openSharedPostModal,
        closeSharedPostModal,
        hasMorePosts,
        loadMorePosts,
        isLoadingMorePosts,
        themeMode: effectiveThemeMode,
        setThemeMode,
        toggleThemeMode,
        stories,
        activeStoryAuthorId,
        activeStoryIndex,
        openStoryViewer,
        closeStoryViewer,
        nextStory,
        prevStory,
        toggleStoryLike,
        reactToStory,
        createStory,
        crossedPaths,
        sendConnectionRequest,
        createCrossedPath,
        timeCapsules,
        createTimeCapsule,
        unloadRooms,
        selectedUnloadRoom,
        joinUnloadRoom,
        sendUnloadMessage,
        intentions,
        addIntention,
        toggleIntention,
        reflections,
        communities,
        toggleJoinCommunity,
        shops,
        selectedShop,
        openShopDetail,
        conversations,
        isLoadingConversations,
        dmError,
        dismissDmError,
        isRealtimeConnected,
        selectedConversation,
        selectConversation,
        startConversationWithUser,
        sendMessage,
        retryMessage,
        refreshConversations,
        notifications,
        markNotificationRead,
        callState,
        startCall,
        acceptCall,
        declineCall,
        endCall,
        toggleCallMute,
        toggleCallCamera,
        currentAudioTrack,
        isPlayingAudio,
        togglePlayAudio,
        githubRepo,
        setGithubRepo,
        updateInfo,
        isCheckingUpdates,
        updateError,
        hasAvailableUpdate,
        checkForUpdates,
        resetGithubRepo,
        inAppNotificationBanner,
        dismissBanner,
        isInitialLoading,
        isSyncing,
        isDbConnected,
        isOffline,
        dbError,
        refreshFromDatabase: syncDatabase
      }}
    >
      {children}
    </VybeContext.Provider>
  );
};

export const useVybe = () => {
  const context = useContext(VybeContext);
  if (!context) {
    throw new Error('useVybe must be used within a VybeProvider');
  }
  return context;
};
