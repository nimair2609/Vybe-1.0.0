import React, { createContext, useContext, useState, useEffect } from 'react';
import { 
  User, Post, Story, CrossedPathEncounter, TimeCapsule, 
  UnloadRoom, VybeShop, Community, SpiritualReflection, 
  SpiritualIntention, Conversation, MoodType, VybeReaction, 
  GitHubReleaseInfo 
} from '../types';
import { 
  CURRENT_USER, SEED_USERS, INITIAL_POSTS, INITIAL_STORIES, 
  INITIAL_REELS, INITIAL_CROSSED_PATHS, INITIAL_TIME_CAPSULES, 
  INITIAL_UNLOAD_ROOMS, SPIRITUAL_REFLECTIONS, INITIAL_SHOPS, 
  INITIAL_COMMUNITIES, INITIAL_CONVERSATIONS 
} from '../data/mockData';
import { 
  DEFAULT_GITHUB_REPO, INSTALLED_VERSION, fetchGitHubUpdates, 
  cleanRepoSlug 
} from '../services/githubUpdater';
import {
  fetchLiveProfiles,
  fetchLivePosts,
  fetchLiveStories,
  createLivePost,
  createLiveStory,
  submitLiveComment,
  syncPostLikeCountToSupabase
} from '../services/supabaseService';

export type MainTab = 'FEED' | 'VYBES' | 'MOOD_ZONES' | 'EXPLORE' | 'INBOX' | 'CROSSED_PATHS' | 'COMMUNITY' | 'PROFILE';

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

interface VybeContextType {
  // Current user & Users
  currentUser: User;
  allUsers: User[];
  viewedProfileUser: User | null;
  openUserProfile: (user: User) => void;
  closeUserProfile: () => void;
  setCurrentUserMood: (mood: MoodType) => void;
  setCurrentUserPulse: (pulse: any) => void;

  // Navigation
  selectedTab: MainTab;
  setSelectedTab: (tab: MainTab) => void;
  activeModal: ModalScreen | null;
  openModal: (modal: ModalScreen, data?: any) => void;
  closeModal: () => void;
  selectedMoodFilter: MoodType | 'ALL';
  setSelectedMoodFilter: (mood: MoodType | 'ALL') => void;

  // Posts & Reels
  posts: Post[];
  reels: Post[];
  toggleLikePost: (postId: string) => void;
  ensurePostLoved: (postId: string) => void;
  reactToPost: (postId: string, reaction: VybeReaction) => void;
  toggleCommentLike: (postId: string, commentId: string) => void;
  toggleSavePost: (postId: string) => void;
  addCommentToPost: (postId: string, text: string) => void;
  createPost: (postData: Partial<Post>) => void;
  sharePostToConversation: (post: Post, convId: string, note?: string) => void;
  incrementPostShareCount: (postId: string) => void;
  sharedPostModalId: string | null;
  openSharedPostModal: (postId: string) => void;
  closeSharedPostModal: () => void;

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

  // Conversations & Calls
  conversations: Conversation[];
  selectedConversation: Conversation | null;
  selectConversation: (conv: Conversation | null) => void;
  sendMessage: (convId: string, text: string) => void;
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
  checkForUpdates: (repoToTest?: string) => Promise<void>;
  resetGithubRepo: () => void;
  inAppNotificationBanner: string | null;
  dismissBanner: () => void;

  // Real Database Status & Live Sync
  isDbConnected: boolean;
  refreshFromDatabase: () => Promise<void>;
}

const VybeContext = createContext<VybeContextType | undefined>(undefined);

export const VybeProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [currentUser, setCurrentUser] = useState<User>(CURRENT_USER);
  const [allUsers, setAllUsers] = useState<User[]>(SEED_USERS);
  const [viewedProfileUser, setViewedProfileUser] = useState<User | null>(null);
  const [previousTabBeforeProfile, setPreviousTabBeforeProfile] = useState<MainTab>('FEED');
  const [isDbConnected, setIsDbConnected] = useState<boolean>(false);
  const [selectedTab, setSelectedTabState] = useState<MainTab>('FEED');
  const [activeModal, setActiveModal] = useState<ModalScreen | null>(null);
  const [selectedMoodFilter, setSelectedMoodFilter] = useState<MoodType | 'ALL'>('ALL');

  const setSelectedTab = (tab: MainTab) => {
    // Selecting PROFILE from bottom navigation strictly opens the logged-in user's own profile
    if (tab === 'PROFILE') {
      setViewedProfileUser(null);
    }
    setSelectedTabState(tab);
  };

  const openUserProfile = (user: User) => {
    if (!user || user.id.toLowerCase() === currentUser.id.toLowerCase()) {
      setViewedProfileUser(null);
      setSelectedTabState('PROFILE');
      return;
    }
    if (selectedTab !== 'PROFILE') {
      setPreviousTabBeforeProfile(selectedTab);
    }
    setViewedProfileUser(user);
    setSelectedTabState('PROFILE');
  };

  const closeUserProfile = () => {
    setViewedProfileUser(null);
    setSelectedTabState(previousTabBeforeProfile || 'FEED');
  };

  // Theme Mode (persisted in localStorage & applied to documentElement)
  const [themeMode, setThemeModeState] = useState<'dark' | 'light'>(() => {
    const saved = localStorage.getItem('vybe_theme_mode');
    return saved === 'light' ? 'light' : 'dark';
  });

  useEffect(() => {
    document.documentElement.dataset.theme = themeMode;
    localStorage.setItem('vybe_theme_mode', themeMode);
  }, [themeMode]);

  const setThemeMode = (mode: 'dark' | 'light') => setThemeModeState(mode);
  const toggleThemeMode = () => setThemeModeState(prev => (prev === 'dark' ? 'light' : 'dark'));

  // Shared Post Deep Link / Preview state
  const [sharedPostModalId, setSharedPostModalId] = useState<string | null>(() => {
    const pathMatch = window.location.pathname.match(/^\/(?:post|p)\/([^/?#]+)/i);
    if (pathMatch?.[1]) return decodeURIComponent(pathMatch[1]);
    const hashMatch = window.location.hash.match(/^#post_(.+)$/i);
    if (hashMatch?.[1]) return decodeURIComponent(hashMatch[1]);
    return null;
  });

  useEffect(() => {
    const handleHashOrPop = () => {
      const pathMatch = window.location.pathname.match(/^\/(?:post|p)\/([^/?#]+)/i);
      if (pathMatch?.[1]) {
        setSharedPostModalId(decodeURIComponent(pathMatch[1]));
        return;
      }
      const hashMatch = window.location.hash.match(/^#post_(.+)$/i);
      if (hashMatch?.[1]) {
        setSharedPostModalId(decodeURIComponent(hashMatch[1]));
      }
    };
    window.addEventListener('hashchange', handleHashOrPop);
    window.addEventListener('popstate', handleHashOrPop);
    return () => {
      window.removeEventListener('hashchange', handleHashOrPop);
      window.removeEventListener('popstate', handleHashOrPop);
    };
  }, []);

  const openSharedPostModal = (postId: string) => setSharedPostModalId(postId);
  const closeSharedPostModal = () => {
    setSharedPostModalId(null);
    if (window.location.hash.startsWith('#post_')) {
      window.history.replaceState(null, '', window.location.pathname + window.location.search);
    } else if (/^\/(?:post|p)\//i.test(window.location.pathname)) {
      window.history.replaceState(null, '', '/');
    }
  };

  // Posts & Reels
  const [posts, setPosts] = useState<Post[]>(INITIAL_POSTS);
  const [reels, setReels] = useState<Post[]>(INITIAL_REELS);

  // Stories (persisted across refresh with automatic 24-hour expiry pruning)
  const [stories, setStories] = useState<Story[]>(() => {
    try {
      const raw = localStorage.getItem('vybe_persisted_stories_v2');
      if (raw) {
        const parsed = JSON.parse(raw) as Story[];
        const now = Date.now();
        const validUserStories = parsed.filter(s => {
          if (s.expiresAtIso) {
            const exp = Date.parse(s.expiresAtIso);
            if (!Number.isNaN(exp)) return exp > now;
          }
          return (s.expiresHoursRemaining ?? 24) > 0;
        });
        if (validUserStories.length > 0) {
          const existingIds = new Set(validUserStories.map(s => s.id));
          return [...validUserStories, ...INITIAL_STORIES.filter(s => !existingIds.has(s.id))];
        }
      }
    } catch {
      // ignore corrupted storage
    }
    return INITIAL_STORIES;
  });

  useEffect(() => {
    try {
      const now = Date.now();
      const userCreated = stories.filter(s => {
        if (!s.id.startsWith('st_')) return false;
        if (s.expiresAtIso) {
          const exp = Date.parse(s.expiresAtIso);
          if (!Number.isNaN(exp) && exp <= now) return false;
        }
        return true;
      });
      localStorage.setItem('vybe_persisted_stories_v2', JSON.stringify(userCreated));
    } catch {
      // ignore quota exceeded for large data URLs
    }
  }, [stories]);

  const [activeStoryAuthorId, setActiveStoryAuthorId] = useState<string | null>(null);
  const [activeStoryIndex, setActiveStoryIndex] = useState<number>(0);

  // Crossed Paths & Capsules
  const [crossedPaths, setCrossedPaths] = useState<CrossedPathEncounter[]>(INITIAL_CROSSED_PATHS);
  const [timeCapsules, setTimeCapsules] = useState<TimeCapsule[]>(INITIAL_TIME_CAPSULES);

  // Unload Rooms
  const [unloadRooms, setUnloadRooms] = useState<UnloadRoom[]>(INITIAL_UNLOAD_ROOMS);
  const [selectedUnloadRoom, setSelectedUnloadRoom] = useState<UnloadRoom | null>(null);

  // Spiritual Mode
  const [intentions, setIntentions] = useState<SpiritualIntention[]>([
    { id: 'int_01', text: 'Practice calm stillness before reacting today', timestamp: 'Today, 8:00 AM', isFulfilled: true },
    { id: 'int_02', text: 'Drink 2L water & take a 15-minute sunset walk', timestamp: 'Today, 9:30 AM', isFulfilled: false }
  ]);
  const reflections = SPIRITUAL_REFLECTIONS;

  // Communities & Shops
  const [communities, setCommunities] = useState<Community[]>(INITIAL_COMMUNITIES);
  const [shops] = useState<VybeShop[]>(INITIAL_SHOPS);
  const [selectedShop, setSelectedShop] = useState<VybeShop | null>(null);

  // Conversations
  const [conversations, setConversations] = useState<Conversation[]>(INITIAL_CONVERSATIONS);
  const [selectedConversation, setSelectedConversation] = useState<Conversation | null>(null);

  // Calls
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

  // Global Audio Controller
  const [currentAudioTrack, setCurrentAudioTrack] = useState<string | null>(null);
  const [isPlayingAudio, setIsPlayingAudio] = useState(false);
  const [audioElement, setAudioElement] = useState<HTMLAudioElement | null>(null);

  // GitHub Auto-Updater State
  const [githubRepo, setGithubRepoState] = useState<string>(() => {
    return localStorage.getItem('vybe_github_repo') || DEFAULT_GITHUB_REPO;
  });
  const [updateInfo, setUpdateInfo] = useState<GitHubReleaseInfo | null>(null);
  const [isCheckingUpdates, setIsCheckingUpdates] = useState<boolean>(false);
  const [updateError, setUpdateError] = useState<string | null>(null);
  const [hasAvailableUpdate, setHasAvailableUpdate] = useState<boolean>(false);
  const [inAppNotificationBanner, setInAppNotificationBanner] = useState<string | null>(null);

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

  const checkForUpdates = async (repoToTest?: string) => {
    const repo = repoToTest ? cleanRepoSlug(repoToTest) : githubRepo;
    setIsCheckingUpdates(true);
    setUpdateError(null);
    try {
      const info = await fetchGitHubUpdates(repo);
      setUpdateInfo(info);
      if (info.isNewerThanInstalled) {
        setHasAvailableUpdate(true);
        setInAppNotificationBanner(`🚀 Update Available: VYBE ${info.tagName} is now ready!`);
      } else {
        setHasAvailableUpdate(false);
      }
    } catch (err: any) {
      setUpdateError(err.message || 'Failed to check GitHub releases.');
    } finally {
      setIsCheckingUpdates(false);
    }
  };

  // Run auto update check silently in background on initial load
  useEffect(() => {
    checkForUpdates();
  }, []);

  // Synchronize state with real Supabase database
  const syncDatabase = async () => {
    try {
      const liveProfiles = await fetchLiveProfiles();
      const userMap = new Map<string, User>();
      if (liveProfiles && liveProfiles.length > 0) {
        liveProfiles.forEach(u => userMap.set(u.id, u));
        setAllUsers(liveProfiles);

        // Pick user matching recognized admin or first profile
        const savedEmail = (localStorage.getItem('vybe_admin_email') || '').toLowerCase();
        const preferredUser = liveProfiles.find(u => 
          (savedEmail && (u.username.toLowerCase().includes('nimai') || u.displayName.toLowerCase().includes('nimai'))) ||
          u.username === 'test_user' ||
          u.username === 'nimair' ||
          u.id === '47650a36-4842-4ce7-a3c6-e706926a71d3'
        );
        if (preferredUser) {
          setCurrentUser(preferredUser);
        } else if (liveProfiles[0]) {
          setCurrentUser(liveProfiles[0]);
        }
      }

      const livePosts = await fetchLivePosts(userMap);
      if (livePosts && livePosts.length > 0) {
        setPosts(livePosts);
        const liveReels = livePosts.filter(p => p.isVybesReel || p.mediaType === 'REEL');
        if (liveReels.length > 0) {
          setReels(liveReels);
        }
      }

      const liveStories = await fetchLiveStories(userMap);
      if (liveStories && liveStories.length > 0) {
        setStories(liveStories);
      }

      setIsDbConnected(true);
    } catch (err) {
      console.warn('[VybeContext] Supabase live sync notice:', err);
    }
  };

  useEffect(() => {
    syncDatabase();
    const timer = setInterval(syncDatabase, 15000);
    return () => clearInterval(timer);
  }, []);

  // Audio Handler
  const togglePlayAudio = (trackTitle: string, previewUrl?: string) => {
    if (isPlayingAudio && currentAudioTrack === trackTitle) {
      audioElement?.pause();
      setIsPlayingAudio(false);
      setCurrentAudioTrack(null);
      return;
    }

    if (audioElement) {
      audioElement.pause();
    }

    const url = previewUrl || 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3';
    const newAudio = new Audio(url);
    newAudio.play().then(() => {
      setIsPlayingAudio(true);
      setCurrentAudioTrack(trackTitle);
      setAudioElement(newAudio);
    }).catch(() => {
      setIsPlayingAudio(true);
      setCurrentAudioTrack(trackTitle);
    });

    newAudio.onended = () => {
      setIsPlayingAudio(false);
      setCurrentAudioTrack(null);
    };
  };

  // Call timer effect
  useEffect(() => {
    let timer: any;
    if (callState.isActive && !callState.isIncoming) {
      timer = setInterval(() => {
        setCallState(prev => ({ ...prev, durationSeconds: prev.durationSeconds + 1 }));
      }, 1000);
    }
    return () => clearInterval(timer);
  }, [callState.isActive, callState.isIncoming]);

  const openModal = (modal: ModalScreen, data?: any) => {
    if (modal === 'SHOP_DETAIL' && data) {
      setSelectedShop(data);
    }
    if (modal === 'UNLOAD_ROOM' && data) {
      setSelectedUnloadRoom(data);
    }
    setActiveModal(modal);
  };

  const closeModal = () => {
    setActiveModal(null);
  };

  // User mood setter
  const setCurrentUserMood = (mood: MoodType) => {
    setCurrentUser(prev => ({ ...prev, currentMood: mood }));
  };

  const setCurrentUserPulse = (pulse: any) => {
    setCurrentUser(prev => ({ ...prev, livePulse: pulse }));
  };

  // Post Actions (synchronized across Feed Posts, Reels, and Supabase)
  const applyPostTransform = (postId: string, updater: (p: Post) => Post) => {
    let updatedLikes: number | null = null;
    setPosts(prev => prev.map(p => {
      if (p.id === postId) {
        const next = updater(p);
        updatedLikes = next.likesCount;
        return next;
      }
      return p;
    }));
    setReels(prev => prev.map(p => {
      if (p.id === postId) {
        const next = updater(p);
        if (updatedLikes === null) updatedLikes = next.likesCount;
        return next;
      }
      return p;
    }));
    if (updatedLikes !== null) {
      syncPostLikeCountToSupabase(postId, updatedLikes);
    }
  };

  const toggleLikePost = (postId: string) => {
    applyPostTransform(postId, p => {
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
        userReaction: isLiked ? (p.userReaction || 'LOVE') : undefined
      };
    });
  };

  const ensurePostLoved = (postId: string) => {
    applyPostTransform(postId, p => {
      if (p.isLiked) return p;
      const nextReactions = {
        ...p.reactionsCount,
        LOVE: (p.reactionsCount?.LOVE || 0) + 1
      };
      return {
        ...p,
        isLiked: true,
        likesCount: p.likesCount + 1,
        reactionsCount: nextReactions,
        userReaction: 'LOVE'
      };
    });
  };

  const reactToPost = (postId: string, reaction: VybeReaction) => {
    applyPostTransform(postId, p => {
      const currentReaction = p.userReaction;
      const nextReaction = currentReaction === reaction ? undefined : reaction;
      const newReactionsCount = { ...p.reactionsCount };

      if (currentReaction) {
        newReactionsCount[currentReaction] = Math.max(0, (newReactionsCount[currentReaction] || 1) - 1);
      }
      if (nextReaction) {
        newReactionsCount[nextReaction] = (newReactionsCount[nextReaction] || 0) + 1;
      }

      return {
        ...p,
        userReaction: nextReaction,
        isLiked: !!nextReaction,
        reactionsCount: newReactionsCount,
        likesCount: Object.values(newReactionsCount).reduce((a, b) => a + b, 0)
      };
    });
  };

  const toggleCommentLike = (postId: string, commentId: string) => {
    const mapComments = (p: Post): Post => {
      if (p.id !== postId) return p;
      return {
        ...p,
        comments: (p.comments || []).map(c => {
          if (c.id !== commentId) return c;
          const nextLiked = !c.isLiked;
          return {
            ...c,
            isLiked: nextLiked,
            likesCount: nextLiked ? (c.likesCount || 0) + 1 : Math.max(0, (c.likesCount || 0) - 1)
          };
        })
      };
    };
    setPosts(prev => prev.map(mapComments));
    setReels(prev => prev.map(mapComments));
  };

  const toggleSavePost = (postId: string) => {
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
  };

  const addCommentToPost = (postId: string, text: string) => {
    if (!text.trim()) return;
    const nowIso = new Date().toISOString();
    const newComment = {
      id: `c_${Date.now()}`,
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

    // Persist live comment to Supabase
    submitLiveComment(postId, currentUser.id, text.trim()).catch(err => {
      console.warn('[Supabase] Comment write notice:', err);
    });
  };

  const incrementPostShareCount = (postId: string) => {
    setPosts(prev => prev.map(p => p.id === postId ? { ...p, sharesCount: (p.sharesCount || 0) + 1 } : p));
  };

  const sharePostToConversation = (post: Post, convId: string, note?: string) => {
    const shareUrl = `${window.location.origin}/post/${encodeURIComponent(post.id)}`;
    const bodyLines = [
      ...(note?.trim() ? [note.trim()] : []),
      `Shared post from @${post.author.username}${post.caption ? `: ${post.caption.slice(0, 140)}` : ''}`,
      shareUrl
    ];
    sendMessage(convId, bodyLines.join('\n'));
    incrementPostShareCount(post.id);
    setInAppNotificationBanner('Post shared via Direct Message');
  };

  const createPost = (postData: Partial<Post>) => {
    const nowIso = new Date().toISOString();
    const newPost: Post = {
      id: `post_${Date.now()}`,
      author: currentUser,
      caption: postData.caption || '',
      mediaUrls: postData.mediaUrls || [],
      mediaType: postData.mediaType || 'TEXT',
      timestamp: 'Just now',
      createdAtIso: nowIso,
      likesCount: 0,
      commentsCount: 0,
      savesCount: 0,
      sharesCount: 0,
      viewsCount: 1,
      isLiked: false,
      isSaved: false,
      tags: postData.tags || [],
      moodZone: postData.moodZone || currentUser.currentMood || 'GENERAL',
      comments: [],
      locationName: postData.locationName,
      audioTrackTitle: postData.audioTrackTitle,
      musicAttachment: postData.musicAttachment,
      reactionsCount: { LOVE: 0, FLAME: 0, LAUGH: 0, WOW: 0, SAD: 0 }
    };

    if (newPost.isVybesReel) {
      setReels(prev => [newPost, ...prev]);
    }
    setPosts(prev => [newPost, ...prev]);
    closeModal();

    // Persist live post to Supabase database
    createLivePost({
      authorId: currentUser.id,
      caption: newPost.caption,
      mediaUrls: newPost.mediaUrls,
      mediaType: newPost.mediaType,
      isVybesReel: newPost.isVybesReel,
      moodZone: newPost.moodZone
    }).then(created => {
      if (created && created.id) {
        setPosts(prev => prev.map(p => p.id === newPost.id ? { ...p, id: created.id } : p));
      }
    }).catch(err => {
      console.warn('[Supabase] Post creation persistence notice:', err);
    });
  };

  // Story Actions (case-insensitive cross-user author matching)
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

  const prevStory = () => {
    setActiveStoryIndex(prev => (prev > 0 ? prev - 1 : 0));
  };

  const toggleStoryLike = (storyId: string) => {
    setStories(prev => prev.map(s => {
      if (s.id !== storyId) return s;
      const nextLiked = !s.isLikedByMe;
      return {
        ...s,
        isLikedByMe: nextLiked,
        reactionsCount: nextLiked ? (s.reactionsCount || 0) + 1 : Math.max(0, (s.reactionsCount || 0) - 1)
      };
    }));
  };

  const createStory = (storyData: Partial<Story>) => {
    const nowMs = Date.now();
    const nowIso = new Date(nowMs).toISOString();
    const expIso = new Date(nowMs + 24 * 60 * 60 * 1000).toISOString();
    const resolvedType = storyData.mediaType || (storyData.mediaUrl ? 'PHOTO' : 'TEXT');
    const newStory: Story = {
      id: `st_${nowMs}`,
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
      textBackgroundColor: storyData.textBackgroundColor || (resolvedType === 'TEXT' ? 'from-[#6366F1] to-[#EC4899]' : undefined),
      musicTrackTitle: storyData.musicTrackTitle
    };
    setStories(prev => [newStory, ...prev]);
    setInAppNotificationBanner('Story published! Visible for 24 hours.');
    closeModal();

    // Persist live story to Supabase
    createLiveStory({
      authorId: currentUser.id,
      mediaUrl: newStory.mediaUrl,
      caption: newStory.caption,
      mediaType: newStory.mediaType
    }).catch(err => {
      console.warn('[Supabase] Story creation persistence notice:', err);
    });
  };

  // Crossed Paths
  const sendConnectionRequest = (id: string) => {
    setCrossedPaths(prev => prev.map(cp => {
      if (cp.id === id) {
        return { ...cp, hasSentRequest: true, requestStatus: 'PENDING' };
      }
      return cp;
    }));
  };

  const createCrossedPath = (encounter: Partial<CrossedPathEncounter>) => {
    const newEncounter: CrossedPathEncounter = {
      id: `cp_${Date.now()}`,
      type: encounter.type || 'MISSED_CONNECTION',
      title: encounter.title || 'Untitled Encounter',
      description: encounter.description || '',
      broadLocation: encounter.broadLocation || 'Mumbai, India',
      venueOrEventName: encounter.venueOrEventName,
      encounterDate: 'Today, Just now',
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

  // Time Capsules
  const createTimeCapsule = (capsule: Partial<TimeCapsule>) => {
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

  // Unload Rooms
  const joinUnloadRoom = (room: UnloadRoom) => {
    setSelectedUnloadRoom(room);
    openModal('UNLOAD_ROOM', room);
  };

  const sendUnloadMessage = (roomId: string, text: string, isAnonymous: boolean) => {
    if (!text.trim()) return;
    const newMsg = {
      id: `um_${Date.now()}`,
      senderName: isAnonymous ? 'Anonymous Listener' : currentUser.displayName,
      isAnonymous,
      text: text.trim(),
      timestamp: 'Just now',
      isMe: true
    };

    setUnloadRooms(prev => prev.map(r => {
      if (r.id === roomId) {
        return {
          ...r,
          recentMessages: [...r.recentMessages, newMsg]
        };
      }
      return r;
    }));

    if (selectedUnloadRoom?.id === roomId) {
      setSelectedUnloadRoom(prev => prev ? {
        ...prev,
        recentMessages: [...prev.recentMessages, newMsg]
      } : null);
    }
  };

  // Spiritual Mode
  const addIntention = (text: string) => {
    if (!text.trim()) return;
    const newIntention = {
      id: `int_${Date.now()}`,
      text: text.trim(),
      timestamp: 'Just now',
      isFulfilled: false
    };
    setIntentions(prev => [newIntention, ...prev]);
  };

  const toggleIntention = (id: string) => {
    setIntentions(prev => prev.map(i => i.id === id ? { ...i, isFulfilled: !i.isFulfilled } : i));
  };

  // Communities
  const toggleJoinCommunity = (commId: string) => {
    setCommunities(prev => prev.map(c => {
      if (c.id === commId) {
        const isJoined = !c.isJoined;
        return {
          ...c,
          isJoined,
          membersCount: isJoined ? c.membersCount + 1 : Math.max(1, c.membersCount - 1)
        };
      }
      return c;
    }));
  };

  // Shops
  const openShopDetail = (shop: VybeShop) => {
    setSelectedShop(shop);
    openModal('SHOP_DETAIL', shop);
  };

  // Chat & Messaging
  const selectConversation = (conv: Conversation | null) => {
    setSelectedConversation(conv);
    if (conv) {
      setConversations(prev => prev.map(c => c.id === conv.id ? { ...c, unreadCount: 0 } : c));
    }
  };

  const sendMessage = (convId: string, text: string) => {
    if (!text.trim()) return;
    const newMsg = {
      id: `dm_${Date.now()}`,
      senderId: currentUser.id,
      text: text.trim(),
      timestamp: 'Just now',
      isMe: true
    };

    setConversations(prev => prev.map(c => {
      if (c.id === convId) {
        return {
          ...c,
          lastMessage: text.trim(),
          lastMessageTimestamp: 'Just now',
          messages: [...c.messages, newMsg]
        };
      }
      return c;
    }));

    if (selectedConversation?.id === convId) {
      setSelectedConversation(prev => prev ? {
        ...prev,
        lastMessage: text.trim(),
        lastMessageTimestamp: 'Just now',
        messages: [...prev.messages, newMsg]
      } : null);
    }
  };

  // Calls
  const startCall = (peerUser: User, isVideo: boolean) => {
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

  const acceptCall = () => {
    setCallState(prev => ({ ...prev, isIncoming: false }));
  };

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

  const endCall = () => {
    declineCall();
  };

  const toggleCallMute = () => {
    setCallState(prev => ({ ...prev, isMuted: !prev.isMuted }));
  };

  const toggleCallCamera = () => {
    setCallState(prev => ({ ...prev, isCameraOff: !prev.isCameraOff }));
  };

  const dismissBanner = () => {
    setInAppNotificationBanner(null);
  };

  return (
    <VybeContext.Provider value={{
      currentUser,
      allUsers,
      viewedProfileUser,
      openUserProfile,
      closeUserProfile,
      setCurrentUserMood,
      setCurrentUserPulse,
      selectedTab,
      setSelectedTab,
      activeModal,
      openModal,
      closeModal,
      selectedMoodFilter,
      setSelectedMoodFilter,
      posts,
      reels,
      toggleLikePost,
      ensurePostLoved,
      reactToPost,
      toggleCommentLike,
      toggleSavePost,
      addCommentToPost,
      createPost,
      sharePostToConversation,
      incrementPostShareCount,
      sharedPostModalId,
      openSharedPostModal,
      closeSharedPostModal,
      themeMode,
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
      selectedConversation,
      selectConversation,
      sendMessage,
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
      isDbConnected,
      refreshFromDatabase: syncDatabase
    }}>
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
