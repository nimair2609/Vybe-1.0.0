export type MoodZone =
  | 'GENERAL'
  | 'CHILL'
  | 'FUN'
  | 'MOTIVATIONAL'
  | 'EMOTIONAL'
  | 'SPIRITUAL'
  | 'HEALING';

export type MediaType = 'TEXT' | 'IMAGE' | 'VIDEO' | 'REEL';

export type UserRole =
  | 'USER'
  | 'CREATOR'
  | 'BUSINESS_OWNER'
  | 'VERIFIED_PUBLIC_FIGURE'
  | 'MODERATOR'
  | 'ADMIN';

export interface VybeUser {
  id: string;
  username: string;
  displayName: string;
  firstName: string;
  lastName: string;
  bio: string;
  avatarUrl: string;
  coverUrl: string;
  email: string;
  phone: string;
  dateOfBirth: string;
  isEmailVerified: boolean;
  isPhoneVerified: boolean;
  isVerified: boolean;
  isVip: boolean;
  isVerifiedBusiness: boolean;
  isPrivate: boolean;
  messagePrivacy: 'EVERYONE' | 'MUTUAL_FOLLOWS' | 'NONE';
  role: UserRole;
  followersCount: number;
  followingCount: number;
  postsCount: number;
  livePulse: string;
  createdAtIso: string | null;
  lastSeenAtIso: string | null;
  isOnline: boolean;
}

export interface VybeComment {
  id: string;
  postId: string;
  author: VybeUser;
  content: string;
  createdAtIso: string | null;
}

export interface VybePost {
  id: string;
  author: VybeUser;
  caption: string;
  mediaUrls: string[];
  mediaType: MediaType;
  moodZone: MoodZone | null;
  isUnfiltered: boolean;
  isVybesReel: boolean;
  locationName: string | null;
  musicTitle: string | null;
  likesCount: number;
  commentsCount: number;
  savesCount: number;
  viewsCount: number;
  isLiked: boolean;
  isSaved: boolean;
  createdAtIso: string | null;
  comments: VybeComment[];
}

export interface VybeStory {
  id: string;
  author: VybeUser;
  mediaUrl: string;
  mediaType: 'PHOTO' | 'VIDEO' | 'TEXT';
  caption: string;
  audience: 'EVERYONE' | 'VYBE_MATCHES';
  textBgKey: string | null;
  createdAtIso: string | null;
  expiresAtIso: string | null;
  isViewed: boolean;
}

export interface DirectMessage {
  id: string;
  conversationId: string;
  senderId: string;
  text: string;
  mediaUrl: string | null;
  mediaType: string;
  isDeletedForEveryone: boolean;
  isEdited: boolean;
  isRead: boolean;
  reactions: string[];
  createdAtIso: string | null;
  status: 'SENDING' | 'SENT' | 'FAILED';
}

export interface Conversation {
  id: string;
  isGroup: boolean;
  groupName: string | null;
  peerUser: VybeUser;
  members: VybeUser[];
  messages: DirectMessage[];
  unreadCount: number;
  updatedAtIso: string | null;
}

export type UnloadRoomMode = 'TEXT_ONLY' | 'VOICE_ONLY' | 'VOICE_AND_TEXT';

export interface UnloadRoomMessage {
  id: string;
  roomId: string;
  senderId: string;
  senderName: string;
  isAnonymous: boolean;
  content: string;
  createdAtIso: string | null;
}

export interface UnloadRoom {
  id: string;
  roomCode: string;
  hostId: string;
  hostName: string;
  title: string;
  description: string;
  category: string;
  topicTag?: string;
  mode: UnloadRoomMode;
  durationMinutes: number;
  capacity: number;
  activeMembersCount: number;
  isPrivate: boolean;
  isActive: boolean;
  joinerMessage: string;
  createdAtIso: string | null;
  expiresAtIso: string | null;
}

export type CallSessionState =
  | 'IDLE'
  | 'OUTGOING'
  | 'INCOMING'
  | 'CONNECTING'
  | 'CONNECTED'
  | 'DECLINED'
  | 'MISSED'
  | 'FAILED'
  | 'ENDED';

export interface ActiveCallSession {
  id: string;
  callerId: string;
  calleeId: string;
  peerUser: VybeUser;
  isVideo: boolean;
  state: CallSessionState;
  offerSdp?: string | null;
  answerSdp?: string | null;
  durationSeconds: number;
  isMuted: boolean;
  isCameraOff: boolean;
  isPeerMuted: boolean;
  isPeerCameraOff: boolean;
  statusText: string;
}

export interface VybeNotificationItem {
  id: string;
  type: string;
  title: string;
  message: string;
  actor: VybeUser | null;
  targetId: string | null;
  isRead: boolean;
  createdAtIso: string | null;
}

export interface BusinessShop {
  id: string;
  ownerId: string;
  businessName: string;
  category: string;
  description: string;
  city: string;
  verificationStatus: string;
  createdAtIso: string | null;
}

export interface ModerationReport {
  id: string;
  reporterId: string;
  targetType: string;
  targetId: string;
  reason: string;
  status: string;
  createdAtIso: string | null;
}
