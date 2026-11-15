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
  website?: string;
  interestTags?: string[];
  pinnedPostIds?: string[];
  profileViewsCount?: number;
  isFollowedByMe?: boolean;
  isFollowRequestedByMe?: boolean;
  followsMe?: boolean;
  isPrivate?: boolean;
  isBlocked?: boolean;
  role?: string;
  firstName?: string;
  lastName?: string;
  messagePrivacy?: 'EVERYONE' | 'FOLLOWERS' | 'NOBODY';
}

export type VybeReaction = 'LOVE' | 'FLAME' | 'LAUGH' | 'WOW' | 'SAD';

export interface ReactionDetail {
  id: VybeReaction;
  label: string;
  emoji: string;
  color: string;
}

export const VYBE_REACTIONS: ReactionDetail[] = [
  { id: 'LOVE', label: 'VYBE', emoji: '⚡', color: '#8B5CF6' },
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
  createdAtIso?: string;
  likesCount: number;
  isLiked?: boolean;
  userReaction?: VybeReaction;
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
  createdAtIso?: string;
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

export interface StoryCropRect {
  x: number;
  y: number;
  width: number;
  height: number;
  aspect?: string;
}

export interface StoryTransform {
  scale: number;
  rotation: number;
  offsetX: number;
  offsetY: number;
}

export interface StoryOverlay {
  id: string;
  type: 'TEXT' | 'STICKER';
  content: string;
  x: number; // 0..100 percent
  y: number; // 0..100 percent
  color?: string;
  bgColor?: string;
  scale?: number;
  rotation?: number;
}

export interface AudioTrack {
  id: string;
  title: string;
  artist: string;
  album?: string;
  genre?: string;
  durationSec: number;
  audioUrl: string;
  coverUrl?: string;
  usageCount?: number;
  isActive?: boolean;
}

export interface StoryViewerItem {
  viewerId: string;
  username: string;
  displayName: string;
  avatarUrl: string;
  isVerified: boolean;
  viewedAt: string;
}

export interface StoryComment {
  id: string;
  storyId: string;
  author: User;
  content: string;
  createdAtIso: string;
  timestamp: string;
}

export interface Story {
  id: string;
  author: User;
  mediaUrl: string;
  caption: string;
  mediaType: 'PHOTO' | 'VIDEO' | 'TEXT' | 'MUSIC';
  audience: 'Public' | 'Followers' | 'LINKUPS' | 'VYBE Matches' | 'Only Me';
  musicTrackTitle?: string;
  timestamp: string;
  createdAtIso?: string;
  expiresAtIso?: string;
  expiresHoursRemaining: number;
  isViewed: boolean;
  reactionsCount: number;
  isLikedByMe: boolean;
  myReaction?: VybeReaction;
  textBackgroundColor?: string;
  viewsCount?: number;
  commentsCount?: number;
  cropRect?: StoryCropRect;
  transform?: StoryTransform;
  overlays?: StoryOverlay[];
  trimStartMs?: number;
  trimEndMs?: number;
  audioTrackId?: string;
  audioTrackUrl?: string;
  audioVolume?: number;
  storyVolume?: number;
  backgroundGradient?: string;
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
  clientId?: string;
  senderId: string;
  text: string;
  timestamp: string;
  createdAtIso?: string;
  isMe: boolean;
  mediaUrl?: string;
  mediaType?: 'TEXT' | 'IMAGE' | 'AUDIO' | 'LOCATION';
  reactions?: string[];
  replyToText?: string;
  replyToId?: string;
  status?: 'SENDING' | 'SENT' | 'FAILED';
  errorMessage?: string;
}

export interface Conversation {
  id: string;
  peerUser: User;
  lastMessage: string;
  lastMessageTimestamp: string;
  lastMessageCreatedAtIso?: string;
  unreadCount: number;
  messages: DirectMessage[];
  chatTheme?: 'CYBER_LUXE' | 'SUNSET' | 'NEON_VIOLET' | 'EMERALD';
  isMuted?: boolean;
  isGroup?: boolean;
  groupName?: string;
  peerBlocked?: boolean;
}

export interface NotificationItem {
  id: string;
  recipientId: string;
  actorId?: string;
  actorUsername?: string;
  actorDisplayName?: string;
  actorAvatarUrl?: string;
  type: string;
  title: string;
  body: string;
  targetId?: string;
  conversationId?: string;
  isRead: boolean;
  createdAtIso: string;
  timestamp: string;
}

export interface AuthSessionUser {
  id: string;
  email: string;
  accessToken: string;
  refreshToken?: string;
  expiresAt?: number;
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

export type CircleJoinMode = 'PUBLIC' | 'PRIVATE' | 'APPROVAL';
export type CircleMemberRole = 'OWNER' | 'MODERATOR' | 'MEMBER';
export type CircleMemberStatus = 'ACCEPTED' | 'PENDING' | 'REJECTED';

export interface Community {
  id: string;
  creatorId?: string;
  name: string;
  description: string;
  category: string;
  avatarUrl: string;
  bannerUrl: string;
  membersCount: number;
  isJoined: boolean;
  isPending?: boolean;
  joinMode?: CircleJoinMode;
  myRole?: CircleMemberRole;
  rules?: string[];
  tags: string[];
  isVerified?: boolean;
  conversationId?: string;
  isArchived?: boolean;
}

export interface CircleMember {
  communityId: string;
  userId: string;
  role: CircleMemberRole;
  status: CircleMemberStatus;
  joinedAt?: string;
  user: User;
}

export interface CirclePost {
  id: string;
  communityId: string;
  author: User;
  title?: string;
  content: string;
  mediaUrl?: string;
  postKind: 'POST' | 'ANNOUNCEMENT' | 'QUESTION' | 'WIN';
  isPinned: boolean;
  likesCount: number;
  commentsCount: number;
  createdAtIso: string;
  timestamp: string;
}

export interface CirclePollOption {
  id: string;
  label: string;
  votesCount: number;
}

export interface CirclePoll {
  id: string;
  communityId: string;
  creator: User;
  question: string;
  options: CirclePollOption[];
  allowMultiple: boolean;
  endsAtIso?: string;
  myVoteOptionIds: string[];
  createdAtIso: string;
}

export interface CircleEvent {
  id: string;
  communityId: string;
  creator: User;
  title: string;
  description: string;
  eventType: 'ONLINE' | 'IN_PERSON';
  locationOrLink: string;
  startsAtIso: string;
  goingCount: number;
  interestedCount: number;
  myRsvp?: 'GOING' | 'INTERESTED' | 'NOT_GOING';
}

export interface CircleResource {
  id: string;
  communityId: string;
  uploader: User;
  title: string;
  description: string;
  resourceType: 'LINK' | 'FILE' | 'GUIDE' | 'TEMPLATE';
  resourceUrl: string;
  createdAtIso: string;
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

// -------------------------------------------------------------
// VYBE GROW 2.0 Types (100% Supabase-Backed)
// -------------------------------------------------------------
export type FieldVisibility = 'PUBLIC' | 'CONNECTIONS' | 'PRIVATE';

export interface GrowProfile {
  userId: string;
  headline: string;
  currentRole: string;
  companyOrSchool: string;
  location: string;
  skills: string[];
  interests: string[];
  educationJson: Array<{ school: string; degree: string; field: string; period: string }>;
  experienceJson: Array<{ role: string; company: string; period: string; summary: string }>;
  achievements: string[];
  portfolioLinks: Array<{ label: string; url: string }>;
  openToCollaborate: boolean;
  openToMentor: boolean;
  openToHire: boolean;
  isPublicDiscoverable: boolean;
  headlineVisibility: FieldVisibility;
  skillsVisibility: FieldVisibility;
  educationVisibility: FieldVisibility;
  experienceVisibility: FieldVisibility;
  linksVisibility: FieldVisibility;
  user?: User;
  canViewFull?: boolean;
  isConnected?: boolean;
}

export interface GrowRequest {
  id: string;
  senderId: string;
  recipientId: string;
  requestType: 'CONNECT' | 'MENTORSHIP' | 'COLLABORATION';
  note: string;
  status: 'PENDING' | 'ACCEPTED' | 'DECLINED';
  createdAtIso: string;
  sender?: User;
  recipient?: User;
}

export interface GrowthProgramTask {
  id: string;
  programId: string;
  title: string;
  description: string;
  sortOrder: number;
  points: number;
  isCompletedByMe?: boolean;
}

export interface GrowthProgram {
  id: string;
  creatorId: string;
  creator?: User;
  title: string;
  description: string;
  category: string;
  difficulty: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';
  durationDays: number;
  coverUrl?: string;
  participantsCount: number;
  isFeatured: boolean;
  isArchived?: boolean;
  isEnrolled?: boolean;
  progressPercent?: number;
  tasks: GrowthProgramTask[];
}

export interface PersonalHabit {
  id: string;
  userId: string;
  title: string;
  category: string;
  cadence: 'DAILY' | 'WEEKLY';
  targetPerWeek: number;
  currentStreak: number;
  bestStreak: number;
  isArchived: boolean;
  completedToday: boolean;
}

export interface PersonalGoal {
  id: string;
  userId: string;
  title: string;
  description: string;
  category: string;
  targetDate?: string;
  progressPercent: number;
  status: 'ACTIVE' | 'COMPLETED' | 'PAUSED';
  milestones: Array<{ id: string; title: string; done: boolean }>;
}

export interface GrowProject {
  id: string;
  authorId: string;
  author: User;
  title: string;
  summary: string;
  category: string;
  tags: string[];
  mediaUrls: string[];
  demoUrl?: string;
  repoUrl?: string;
  seekingRoles: string[];
  likesCount: number;
  bookmarksCount: number;
  isLikedByMe: boolean;
  isBookmarkedByMe: boolean;
  isArchived?: boolean;
  createdAtIso: string;
}

export type OpportunityCategory =
  | 'INTERNSHIPS'
  | 'FULL_TIME_JOBS'
  | 'PART_TIME_JOBS'
  | 'FREELANCE_GIGS'
  | 'PROJECTS'
  | 'COLLABORATIONS'
  | 'HACKATHONS'
  | 'GRANTS_FELLOWSHIPS'
  | 'RESEARCH'
  | 'MENTORSHIP';

export const OPPORTUNITY_CATEGORIES: Array<{ id: OpportunityCategory; label: string }> = [
  { id: 'INTERNSHIPS', label: 'Internships' },
  { id: 'FULL_TIME_JOBS', label: 'Full-Time Jobs' },
  { id: 'PART_TIME_JOBS', label: 'Part-Time Jobs' },
  { id: 'FREELANCE_GIGS', label: 'Freelance Gigs' },
  { id: 'PROJECTS', label: 'Projects' },
  { id: 'COLLABORATIONS', label: 'Collaborations' },
  { id: 'HACKATHONS', label: 'Hackathons' },
  { id: 'GRANTS_FELLOWSHIPS', label: 'Grants & Fellowships' },
  { id: 'RESEARCH', label: 'Research' },
  { id: 'MENTORSHIP', label: 'Mentorship' }
];

export interface Opportunity {
  id: string;
  posterId: string;
  poster: User;
  title: string;
  organization: string;
  description: string;
  category: OpportunityCategory;
  workMode: 'REMOTE' | 'HYBRID' | 'ON_SITE';
  location?: string;
  compensation?: string;
  deadline?: string;
  applyUrl?: string;
  skillsRequired: string[];
  applicantsCount: number;
  hasApplied?: boolean;
  isBookmarked?: boolean;
  isClosed?: boolean;
  createdAtIso: string;
}

export interface OpportunityApplication {
  id: string;
  opportunityId: string;
  applicantId: string;
  applicant?: User;
  coverNote: string;
  resumeUrl?: string;
  resumeFileName?: string;
  portfolioUrl?: string;
  status: 'SUBMITTED' | 'REVIEWING' | 'SHORTLISTED' | 'ACCEPTED' | 'REJECTED';
  createdAtIso: string;
}

// -------------------------------------------------------------
// Platform Config & Super Admin Types
// -------------------------------------------------------------
export interface PlatformConfigState {
  storiesEnabled: boolean;
  circlesEnabled: boolean;
  growEnabled: boolean;
  opportunitiesEnabled: boolean;
  directMessagesEnabled: boolean;
  voiceRoomsEnabled: boolean;
  crossedPathsEnabled: boolean;
  timeCapsulesEnabled: boolean;
  maintenanceMode: boolean;
  maintenanceBanner: string;
}

export const DEFAULT_PLATFORM_CONFIG: PlatformConfigState = {
  storiesEnabled: true,
  circlesEnabled: true,
  growEnabled: true,
  opportunitiesEnabled: true,
  directMessagesEnabled: true,
  voiceRoomsEnabled: true,
  crossedPathsEnabled: true,
  timeCapsulesEnabled: true,
  maintenanceMode: false,
  maintenanceBanner: ''
};

export interface AdminDashboardMetrics {
  totalUsers: number;
  dauUsers: number;
  wauUsers: number;
  mauUsers: number;
  newUsers24h: number;
  suspendedUsers: number;
  bannedUsers: number;
  totalPosts: number;
  totalStories: number;
  totalComments: number;
  totalLikes: number;
  totalMessages: number;
  totalCircles: number;
  totalOpportunities: number;
  pendingReports: number;
  pendingVerifications: number;
}

export type AdminStaffRole = 'OWNER_ADMIN' | 'SUPER_ADMIN' | 'ADMIN' | 'MODERATOR' | 'SUPPORT_ADMIN' | 'ANALYST';

export interface AdminStaffMember {
  userId: string;
  role: AdminStaffRole;
  isActive: boolean;
  username?: string;
  displayName?: string;
  avatarUrl?: string;
  createdAtIso?: string;
}
