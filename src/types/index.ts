export type MoodType = 'GENERAL' | 'HAPPY' | 'HEARTBREAK' | 'CHILL' | 'HEALING' | 'FUN';

export interface MoodConfig {
  type: MoodType;
  title: string;
  subtitle: string;
  emoji: string;
  accentColor: string;
  gradient: string;
  vibeTagline: string;
  safetyGuideline: string;
}

export const MOOD_DEFINITIONS: Record<MoodType, MoodConfig> = {
  GENERAL: {
    type: 'GENERAL',
    title: 'General',
    subtitle: 'Mixup feed of all community vibes',
    emoji: '🌐',
    accentColor: '#00E5FF',
    gradient: 'from-[#00C6FF] to-[#0072FF]',
    vibeTagline: 'All moods combined. Explore the unfiltered diversity of VYBE.',
    safetyGuideline: 'Open to everyone. Be respectful and mindful of diverse emotions.',
  },
  HAPPY: {
    type: 'HAPPY',
    title: 'Happy',
    subtitle: 'Celebrations, gratitude & wins',
    emoji: '✨',
    accentColor: '#FBBF24',
    gradient: 'from-[#F59E0B] to-[#FBBF24]',
    vibeTagline: 'Share your wins, everyday joys, and wholesome energy.',
    safetyGuideline: 'Celebrate together and keep the vibes uplifting!',
  },
  HEARTBREAK: {
    type: 'HEARTBREAK',
    title: 'Heartbreak',
    subtitle: 'Supportive reflections & solidarity',
    emoji: '💔',
    accentColor: '#818CF8',
    gradient: 'from-[#6366F1] to-[#EC4899]',
    vibeTagline: 'A safe space for honesty, healing tears, and silent company.',
    safetyGuideline: 'Be kind. Hostility or unwanted romantic pressure is prohibited.',
  },
  CHILL: {
    type: 'CHILL',
    title: 'Chill',
    subtitle: 'Lo-fi beats, thoughts & cozy moments',
    emoji: '☕',
    accentColor: '#34D399',
    gradient: 'from-[#10B981] to-[#3B82F6]',
    vibeTagline: 'Low-stimulus zone for slow thoughts, coffee, and calm music.',
    safetyGuideline: 'No loud arguments or controversial debates here.',
  },
  HEALING: {
    type: 'HEALING',
    title: 'Healing & Beginning',
    subtitle: 'Growth, fresh starts & encouragement',
    emoji: '🌱',
    accentColor: '#2DD4BF',
    gradient: 'from-[#14B8A6] to-[#06B6D4]',
    vibeTagline: 'Reset your trajectory. Share milestones and small breakthroughs.',
    safetyGuideline: 'Encouragement only. Vulnerability is respected.',
  },
  FUN: {
    type: 'FUN',
    title: 'Fun',
    subtitle: 'Memes, gaming banter & creative chaos',
    emoji: '🔥',
    accentColor: '#F43F5E',
    gradient: 'from-[#F43F5E] to-[#FB7185]',
    vibeTagline: 'Unfiltered laughter, viral Indian memes, and quick challenges.',
    safetyGuideline: 'Keep it lighthearted; no harassment masked as jokes.',
  },
};

export type OfficialVybeBadge = 'VERIFIED_BLUE' | 'VIP_GOLD' | 'SHOP_GOLD' | 'WORKER_GREEN';

export type LivePulseStatus = 
  | 'Open to chatting'
  | 'Feeling creative'
  | 'Listening to music'
  | 'Looking for gaming partner'
  | 'Taking quiet time'
  | 'None';

export interface User {
  id: string;
  username: string;
  displayName: string;
  publicVybeId: string;
  bio: string;
  avatarUrl: string;
  coverUrl: string;
  isVerified: boolean;
  isVip: boolean;
  isVerifiedBusiness: boolean;
  isWorker: boolean;
  followersCount: number;
  followingCount: number;
  postsCount: number;
  currentMood?: MoodType;
  livePulse: LivePulseStatus;
  isOnline: boolean;
  badges: OfficialVybeBadge[];
  joinedDate: string;
  location?: string;
  isFollowedByMe?: boolean;
}

export type VybeReaction = 'LOVE' | 'FLAME' | 'LAUGH' | 'WOW' | 'SAD';

export interface ReactionDetail {
  id: VybeReaction;
  label: string;
  emoji: string;
  color: string;
}

export const VYBE_REACTIONS: ReactionDetail[] = [
  { id: 'LOVE', label: 'Finger Heart', emoji: '🫰', color: '#EC4899' },
  { id: 'FLAME', label: 'Fire', emoji: '🔥', color: '#F97316' },
  { id: 'LAUGH', label: 'Laugh', emoji: '😂', color: '#FACC15' },
  { id: 'WOW', label: 'Wow', emoji: '😮', color: '#38BDF8' },
  { id: 'SAD', label: 'Sad', emoji: '😢', color: '#818CF8' },
];

export interface PostComment {
  id: string;
  postId: string;
  author: User;
  content: string;
  timestamp: string;
  likesCount: number;
  isLiked?: boolean;
}

export interface MusicAttachment {
  trackId: string;
  title: string;
  artist: string;
  previewUrl?: string;
  coverUrl?: string;
}

export interface Post {
  id: string;
  author: User;
  caption: string;
  mediaUrls: string[];
  mediaType: 'TEXT' | 'IMAGE' | 'VIDEO' | 'REEL';
  timestamp: string;
  likesCount: number;
  commentsCount: number;
  savesCount: number;
  sharesCount: number;
  viewsCount: number;
  isLiked: boolean;
  isSaved: boolean;
  isPinned?: boolean;
  isUnfiltered?: boolean;
  isVybesReel?: boolean;
  tags: string[];
  moodZone?: MoodType;
  comments: PostComment[];
  locationName?: string;
  audioTrackTitle?: string;
  musicAttachment?: MusicAttachment;
  userReaction?: VybeReaction;
  reactionsCount: Record<VybeReaction, number>;
}

export interface Story {
  id: string;
  author: User;
  mediaUrl: string;
  caption: string;
  mediaType: 'PHOTO' | 'VIDEO' | 'TEXT' | 'MUSIC';
  audience: 'Public' | 'Followers' | 'VYBE Matches' | 'Only Me';
  musicTrackTitle?: string;
  timestamp: string;
  expiresHoursRemaining: number;
  isViewed: boolean;
  reactionsCount: number;
  isLikedByMe: boolean;
  textBackgroundColor?: string;
}

export interface CrossedPathEncounter {
  id: string;
  type: 'MISSED_CONNECTION' | 'VENUE_EVENT' | 'BROAD_NEARBY';
  title: string;
  description: string;
  broadLocation: string;
  venueOrEventName?: string;
  encounterDate: string;
  poster: User;
  isAnonymous: boolean;
  expiresHoursRemaining: number;
  potentialMatchesCount: number;
  hasSentRequest: boolean;
  requestStatus: 'PENDING' | 'ACCEPTED' | 'DECLINED';
  tags: string[];
  photoUrl?: string;
}

export interface TimeCapsule {
  id: string;
  title: string;
  sender: User;
  recipientSummary: string;
  sealedMessage: string;
  mediaAttachmentUrl?: string;
  unlockDateFormatted: string;
  unlockTimestampMs: number;
  isUnlocked: boolean;
  createdAtFormatted: string;
  categoryTheme: string;
}

export interface UnloadMessage {
  id: string;
  senderName: string;
  isAnonymous: boolean;
  text: string;
  timestamp: string;
  isMe: boolean;
}

export interface UnloadRoom {
  id: string;
  roomCode: string;
  title: string;
  description: string;
  topicTag: string;
  mode: 'Voice & Text' | 'Voice Only' | 'Text Only';
  activeListenersCount: number;
  isHostOnline: boolean;
  recentMessages: UnloadMessage[];
  hostName: string;
  safetyNotice: string;
}

export interface DirectMessage {
  id: string;
  senderId: string;
  text: string;
  timestamp: string;
  isMe: boolean;
  mediaUrl?: string;
  mediaType?: 'TEXT' | 'IMAGE' | 'AUDIO' | 'LOCATION';
  reactions?: string[];
  replyToText?: string;
}

export interface Conversation {
  id: string;
  peerUser: User;
  lastMessage: string;
  lastMessageTimestamp: string;
  unreadCount: number;
  messages: DirectMessage[];
  chatTheme?: 'CYBER_LUXE' | 'SUNSET' | 'NEON_VIOLET' | 'EMERALD';
  isMuted?: boolean;
}

export interface CatalogItem {
  id: string;
  name: string;
  priceFormatted: string;
  description: string;
  imageUrl: string;
  inStock: boolean;
}

export interface ShopReview {
  id: string;
  author: User;
  ratingStars: number;
  reviewText: string;
  timestamp: string;
  ownerReply?: string;
}

export interface VybeShop {
  id: string;
  businessName: string;
  category: string;
  description: string;
  logoUrl: string;
  coverUrl: string;
  rating: number;
  reviewsCount: number;
  isVerifiedBusiness: boolean;
  openingHours: string;
  locationAddress: string;
  contactPhone: string;
  contactEmail: string;
  catalog: CatalogItem[];
  reviews: ShopReview[];
  followersCount: number;
}

export interface Community {
  id: string;
  name: string;
  description: string;
  category: string;
  avatarUrl: string;
  bannerUrl: string;
  membersCount: number;
  isJoined: boolean;
  tags: string[];
}

export interface SpiritualReflection {
  id: string;
  quote: string;
  traditionOrAuthor: string;
  theme: string;
  audioCue: string;
}

export interface SpiritualIntention {
  id: string;
  text: string;
  timestamp: string;
  isFulfilled: boolean;
}

export interface GitHubReleaseInfo {
  tagName: string;
  name: string;
  body: string;
  htmlUrl: string;
  downloadUrl?: string;
  apkFileName?: string;
  apkFileSize: number;
  isNewerThanInstalled: boolean;
  isOlderThanInstalled: boolean;
  publishedAt?: string;
  latestCommitShortSha?: string;
  latestActionRunNumber?: number;
}
