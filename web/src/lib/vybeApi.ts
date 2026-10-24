import { supabase, isSupabaseConfigured } from './supabase';
import {
  generateRoomCode,
  isRecentlyOnline,
  normalizePhoneE164,
  normalizeRoomCode
} from './utils';
import type {
  BusinessShop,
  Conversation,
  DirectMessage,
  MediaType,
  ModerationReport,
  MoodZone,
  UnloadRoom,
  UnloadRoomMessage,
  UnloadRoomMode,
  VybeComment,
  VybeNotificationItem,
  VybePost,
  VybeStory,
  VybeUser
} from '../types/vybe';

/* eslint-disable @typescript-eslint/no-explicit-any */

export function mapProfileRow(row: any): VybeUser {
  const id = String(row?.id || '');
  const rawUsername = String(row?.username || '').trim();
  const safeUsername =
    !rawUsername || rawUsername.toLowerCase() === 'null'
      ? `vybe_${id.replace(/-/g, '').slice(0, 8)}`
      : rawUsername;
  const firstName = String(row?.first_name || '').trim();
  const lastName = String(row?.last_name || '').trim();
  const rawDisplay = String(row?.display_name || '').trim();
  const safeDisplay =
    !rawDisplay || rawDisplay.toLowerCase() === 'null'
      ? `${firstName} ${lastName}`.trim() || safeUsername
      : rawDisplay;
  const lastSeen = row?.last_seen_at || row?.updated_at || null;

  return {
    id,
    username: safeUsername,
    displayName: safeDisplay,
    firstName,
    lastName,
    bio: String(row?.bio || ''),
    avatarUrl: String(row?.avatar_url || ''),
    coverUrl: String(row?.cover_url || ''),
    email: String(row?.email || ''),
    phone: String(row?.phone || ''),
    dateOfBirth: String(row?.date_of_birth || ''),
    isEmailVerified: Boolean(row?.is_email_verified),
    isPhoneVerified: Boolean(row?.is_phone_verified),
    isVerified: Boolean(row?.is_verified),
    isVip: Boolean(row?.is_vip),
    isVerifiedBusiness: Boolean(row?.is_verified_business),
    isPrivate: Boolean(row?.is_private_account),
    messagePrivacy: (row?.message_privacy || 'EVERYONE') as VybeUser['messagePrivacy'],
    role: (row?.role || 'USER') as VybeUser['role'],
    followersCount: Number(row?.followers_count ?? 0),
    followingCount: Number(row?.following_count ?? 0),
    postsCount: Math.max(0, Number(row?.posts_count ?? 0)),
    livePulse: String(row?.live_pulse || 'NONE'),
    createdAtIso: row?.created_at || null,
    lastSeenAtIso: lastSeen,
    isOnline: isRecentlyOnline(lastSeen)
  };
}

export async function resolveLoginIdentifier(identifier: string): Promise<{
  found: boolean;
  mode?: 'email' | 'phone';
  email?: string;
  phone?: string;
}> {
  const trimmed = identifier.trim();
  if (!trimmed) return { found: false };
  if (trimmed.includes('@') && !trimmed.startsWith('@')) {
    return { found: true, mode: 'email', email: trimmed.toLowerCase() };
  }
  if (/^\+?[0-9()\-\s]{8,}$/.test(trimmed) && !/[a-zA-Z]/.test(trimmed)) {
    return { found: true, mode: 'phone', phone: normalizePhoneE164(trimmed) };
  }
  if (!isSupabaseConfigured) return { found: false };

  const { data, error } = await supabase.rpc('resolve_login_identifier', {
    p_identifier: trimmed
  });
  if (error || !data) {
    return { found: false };
  }
  return {
    found: Boolean(data.found),
    mode: data.mode,
    email: data.email,
    phone: data.phone
  };
}

export async function checkUsernameAvailability(username: string): Promise<boolean> {
  const clean = username.trim().replace(/^@/, '').toLowerCase();
  if (clean.length < 3 || clean === 'null') return false;
  if (!isSupabaseConfigured) return true;

  const { data, error } = await supabase.rpc('check_username_available', {
    p_username: clean
  });
  if (!error && typeof data === 'boolean') return data;

  const { data: rows } = await supabase
    .from('profiles')
    .select('id')
    .ilike('username', clean)
    .limit(1);
  return !rows || rows.length === 0;
}

export async function fetchProfileById(userId: string): Promise<VybeUser | null> {
  if (!isSupabaseConfigured || !userId) return null;
  const { data, error } = await supabase.from('profiles').select('*').eq('id', userId).maybeSingle();
  if (error || !data) return null;
  return mapProfileRow(data);
}

export async function fetchProfileByUsername(username: string): Promise<VybeUser | null> {
  if (!isSupabaseConfigured || !username) return null;
  const clean = username.trim().replace(/^@/, '');
  const { data, error } = await supabase
    .from('profiles')
    .select('*')
    .ilike('username', clean)
    .maybeSingle();
  if (error || !data) return null;
  return mapProfileRow(data);
}

export async function updatePresenceHeartbeat(userId: string): Promise<void> {
  if (!isSupabaseConfigured || !userId) return;
  await supabase
    .from('profiles')
    .update({ last_seen_at: new Date().toISOString() })
    .eq('id', userId);
}

export async function uploadMediaToBucket(
  bucket: 'posts' | 'stories' | 'avatars' | 'chat-media',
  userId: string,
  file: File,
  onStatus?: (msg: string) => void
): Promise<{ publicUrl: string; storagePath: string }> {
  if (!isSupabaseConfigured) {
    throw new Error('Supabase backend is not configured.');
  }
  const safeExt = (file.name.split('.').pop() || 'bin').toLowerCase().replace(/[^a-z0-9]/g, '');
  const storagePath = `${userId}/${Date.now()}_${crypto.randomUUID().slice(0, 8)}.${safeExt}`;
  onStatus?.(`Uploading ${file.name}…`);

  const { error: uploadErr } = await supabase.storage.from(bucket).upload(storagePath, file, {
    cacheControl: '3600',
    upsert: false,
    contentType: file.type || undefined
  });
  if (uploadErr) {
    throw new Error(`Media upload failed: ${uploadErr.message}`);
  }

  const { data } = supabase.storage.from(bucket).getPublicUrl(storagePath);
  if (!data?.publicUrl) {
    throw new Error('Could not resolve uploaded media URL.');
  }
  return { publicUrl: data.publicUrl, storagePath };
}

export async function fetchFeedPosts(
  currentUserId: string | null,
  page = 0,
  pageSize = 20
): Promise<VybePost[]> {
  if (!isSupabaseConfigured) return [];
  const from = page * pageSize;
  const to = from + pageSize - 1;

  const { data: rows, error } = await supabase
    .from('posts')
    .select(
      `
      *,
      profiles:author_id (*),
      post_media (*),
      comments (*, profiles:author_id (*))
    `
    )
    .eq('is_archived', false)
    .order('created_at', { ascending: false })
    .range(from, to);

  if (error || !rows) return [];

  let likedIds = new Set<string>();
  let savedIds = new Set<string>();
  if (currentUserId && rows.length > 0) {
    const postIds = rows.map((r: any) => r.id);
    const [{ data: reactions }, { data: saves }] = await Promise.all([
      supabase
        .from('post_reactions')
        .select('post_id')
        .eq('user_id', currentUserId)
        .in('post_id', postIds),
      supabase
        .from('saved_posts')
        .select('post_id')
        .eq('user_id', currentUserId)
        .in('post_id', postIds)
    ]);
    likedIds = new Set((reactions || []).map((x: any) => String(x.post_id)));
    savedIds = new Set((saves || []).map((x: any) => String(x.post_id)));
  }

  return rows.map((row: any) => {
    const author = mapProfileRow(row.profiles || { id: row.author_id });
    const sortedMedia = Array.isArray(row.post_media)
      ? [...row.post_media].sort((a, b) => (a.display_order ?? 0) - (b.display_order ?? 0))
      : [];
    const mediaUrls = sortedMedia.map((m: any) => String(m.media_url)).filter(Boolean);
    const comments: VybeComment[] = Array.isArray(row.comments)
      ? row.comments.map((c: any) => ({
          id: String(c.id),
          postId: String(c.post_id),
          author: mapProfileRow(c.profiles || { id: c.author_id }),
          content: String(c.content || ''),
          createdAtIso: c.created_at || null
        }))
      : [];

    return {
      id: String(row.id),
      author,
      caption: String(row.caption || ''),
      mediaUrls,
      mediaType: (row.media_type || (mediaUrls.length > 0 ? 'IMAGE' : 'TEXT')) as MediaType,
      moodZone: (row.mood_zone || null) as MoodZone | null,
      isUnfiltered: Boolean(row.is_unfiltered),
      isVybesReel: Boolean(row.is_vybes_reel) || row.media_type === 'REEL',
      locationName: row.location_name || null,
      musicTitle: row.music_title || null,
      likesCount: Math.max(0, Number(row.likes_count ?? 0)),
      commentsCount: Math.max(comments.length, Number(row.comments_count ?? 0)),
      savesCount: Math.max(0, Number(row.saves_count ?? 0)),
      viewsCount: Math.max(0, Number(row.views_count ?? 0)),
      isLiked: likedIds.has(String(row.id)),
      isSaved: savedIds.has(String(row.id)),
      createdAtIso: row.created_at || null,
      comments
    };
  });
}

export async function publishPostPersisted(params: {
  authorId: string;
  caption: string;
  files?: File[];
  mediaFiles?: File[];
  mediaType?: MediaType;
  moodZone?: MoodZone | null;
  isUnfiltered?: boolean;
  isVybesReel?: boolean;
  locationName?: string | null;
  musicTitle?: string | null;
  onProgress?: (label: string) => void;
}): Promise<string> {
  const clientUploadId = `web_post_${crypto.randomUUID()}`;
  const uploaded: { publicUrl: string; storagePath: string; mediaType: MediaType }[] = [];
  const filesToUpload = params.files || params.mediaFiles || [];

  for (let i = 0; i < filesToUpload.length; i++) {
    const file = filesToUpload[i];
    const isVideo = file.type.startsWith('video/');
    if (typeof params.onProgress === 'function') {
      params.onProgress(`Uploading media ${i + 1} of ${filesToUpload.length}…`);
    }
    const res = await uploadMediaToBucket('posts', params.authorId, file);
    uploaded.push({
      ...res,
      mediaType: params.isVybesReel ? 'REEL' : isVideo ? 'VIDEO' : 'IMAGE'
    });
  }

  const primaryType: MediaType =
    params.isVybesReel
      ? 'REEL'
      : params.mediaType
        ? params.mediaType
        : uploaded.some((u) => u.mediaType === 'VIDEO')
          ? 'VIDEO'
          : uploaded.length > 0
            ? 'IMAGE'
            : 'TEXT';

  if (typeof params.onProgress === 'function') {
    params.onProgress('Saving post to VYBE…');
  }
  const { data: inserted, error: postErr } = await supabase
    .from('posts')
    .insert({
      author_id: params.authorId,
      caption: params.caption.trim(),
      media_type: primaryType,
      mood_zone: params.moodZone || 'GENERAL',
      is_unfiltered: Boolean(params.isUnfiltered),
      is_vybes_reel: Boolean(params.isVybesReel),
      location_name: params.locationName?.trim() || null,
      music_title: params.musicTitle?.trim() || null,
      client_upload_id: clientUploadId
    })
    .select('id')
    .single();

  if (postErr || !inserted?.id) {
    throw new Error(postErr?.message || 'Database insert failed while publishing post.');
  }

  if (uploaded.length > 0) {
    const mediaRows = uploaded.map((item, idx) => ({
      post_id: inserted.id,
      media_url: item.publicUrl,
      media_type: item.mediaType === 'REEL' ? 'VIDEO' : item.mediaType,
      display_order: idx,
      storage_path: item.storagePath
    }));
    const { error: mediaErr } = await supabase.from('post_media').insert(mediaRows);
    if (mediaErr) {
      throw new Error(`Post media metadata failed to save: ${mediaErr.message}`);
    }
  }

  return String(inserted.id);
}

export async function togglePostResonance(postId: string, userId: string, currentlyLiked: boolean): Promise<void> {
  if (currentlyLiked) {
    await supabase.from('post_reactions').delete().eq('post_id', postId).eq('user_id', userId);
  } else {
    await supabase.from('post_reactions').upsert({
      post_id: postId,
      user_id: userId,
      reaction_type: 'PURE_ENERGY'
    });
  }
}

export async function toggleSavePost(postId: string, userId: string, currentlySaved: boolean): Promise<void> {
  if (currentlySaved) {
    await supabase.from('saved_posts').delete().eq('post_id', postId).eq('user_id', userId);
  } else {
    await supabase.from('saved_posts').upsert({ post_id: postId, user_id: userId });
  }
}

export async function addPostComment(postId: string, authorId: string, content: string): Promise<void> {
  const clean = content.trim();
  if (!clean) return;
  const { error } = await supabase.from('comments').insert({
    post_id: postId,
    author_id: authorId,
    content: clean
  });
  if (error) throw new Error(error.message);
}

export async function fetchActiveStories(currentUserId: string | null): Promise<VybeStory[]> {
  if (!isSupabaseConfigured) return [];
  const nowIso = new Date().toISOString();
  const { data: rows, error } = await supabase
    .from('stories')
    .select('*, profiles:author_id (*)')
    .gt('expires_at', nowIso)
    .order('created_at', { ascending: false })
    .limit(80);

  if (error || !rows) return [];

  let viewedIds = new Set<string>();
  if (currentUserId && rows.length > 0) {
    const { data: views } = await supabase
      .from('story_views')
      .select('story_id')
      .eq('viewer_id', currentUserId)
      .in(
        'story_id',
        rows.map((r: any) => r.id)
      );
    viewedIds = new Set((views || []).map((v: any) => String(v.story_id)));
  }

  return rows.map((row: any) => ({
    id: String(row.id),
    author: mapProfileRow(row.profiles || { id: row.author_id }),
    mediaUrl: String(row.media_url || ''),
    mediaType: ((row.media_type || row.story_type || 'PHOTO') as string).toUpperCase() as VybeStory['mediaType'],
    caption: String(row.caption || ''),
    audience: (row.audience === 'VYBE_MATCHES' ? 'VYBE_MATCHES' : 'EVERYONE') as VybeStory['audience'],
    textBgKey: row.text_bg_key || null,
    createdAtIso: row.created_at || null,
    expiresAtIso: row.expires_at || null,
    isViewed: viewedIds.has(String(row.id))
  }));
}

export async function publishStoryPersisted(params: {
  authorId: string;
  file?: File | null;
  caption: string;
  audience: 'EVERYONE' | 'VYBE_MATCHES';
  textOnlyBg?: string | null;
  onProgress?: (label: string) => void;
}): Promise<string> {
  let mediaUrl = 'vybe://text-story';
  let storagePath: string | null = null;
  let mediaType: 'PHOTO' | 'VIDEO' | 'TEXT' = 'TEXT';

  if (params.file) {
    mediaType = params.file.type.startsWith('video/') ? 'VIDEO' : 'PHOTO';
    params.onProgress?.('Uploading story media…');
    const uploaded = await uploadMediaToBucket('stories', params.authorId, params.file);
    mediaUrl = uploaded.publicUrl;
    storagePath = uploaded.storagePath;
  } else if (!params.caption.trim()) {
    throw new Error('Select a photo/video or write a text story.');
  }

  const expiresAt = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString();
  params.onProgress?.('Publishing story…');

  const { data, error } = await supabase
    .from('stories')
    .insert({
      author_id: params.authorId,
      media_url: mediaUrl,
      media_type: mediaType,
      story_type: mediaType,
      caption: params.caption.trim(),
      audience: params.audience,
      text_bg_key: params.textOnlyBg || 'VIOLET_PULSE',
      storage_path: storagePath,
      client_upload_id: `web_story_${crypto.randomUUID()}`,
      expires_at: expiresAt
    })
    .select('id')
    .single();

  if (error || !data?.id) {
    throw new Error(error?.message || 'Failed to save story.');
  }
  return String(data.id);
}

export async function markStoryViewed(storyId: string, viewerId: string): Promise<void> {
  if (!isSupabaseConfigured || !storyId || !viewerId) return;
  await supabase.from('story_views').upsert({
    story_id: storyId,
    viewer_id: viewerId,
    viewed_at: new Date().toISOString()
  });
}

// ============================================================================
// LIVE UNLOAD ROOMS (`VYB-XXXX-XXXX`)
// ============================================================================

export async function fetchRooms(): Promise<UnloadRoom[]> {
  if (!isSupabaseConfigured) return [];
  const { data: rows, error } = await supabase
    .from('rooms')
    .select('*, profiles:host_id (*), room_members (user_id)')
    .eq('is_active', true)
    .order('created_at', { ascending: false })
    .limit(50);

  if (error || !rows) return [];
  const now = Date.now();

  return rows
    .filter((r: any) => !r.expires_at || Date.parse(r.expires_at) > now)
    .map((r: any) => mapRoomRow(r));
}

function mapRoomRow(r: any): UnloadRoom {
  const host = mapProfileRow(r.profiles || { id: r.host_id });
  const rawId = String(r.id || '');
  const derivedCode =
    r.room_code ||
    `VYB-${rawId.replace(/[^a-zA-Z0-9]/g, '').slice(0, 4).toUpperCase()}-${rawId.replace(/[^a-zA-Z0-9]/g, '').slice(4, 8).toUpperCase()}`;

  return {
    id: rawId,
    roomCode: String(derivedCode),
    hostId: String(r.host_id),
    hostName: host.displayName,
    title: String(r.title || 'VYBE Room'),
    description: String(r.description || ''),
    category: String(r.category || r.topic_tag || 'Community Space'),
    mode: (r.mode || 'VOICE_AND_TEXT') as UnloadRoomMode,
    durationMinutes: Number(r.duration_minutes ?? 45),
    capacity: Number(r.max_participants ?? r.capacity ?? 25),
    activeMembersCount: Math.max(1, Array.isArray(r.room_members) ? r.room_members.length : 1),
    isPrivate: Boolean(r.is_private),
    isActive: r.is_active !== false,
    joinerMessage: String(r.joiner_message || r.welcome_message || 'Welcome to this VYBE listening space.'),
    createdAtIso: r.created_at || null,
    expiresAtIso: r.expires_at || null
  };
}

export async function createLiveRoom(params: {
  hostId: string;
  title: string;
  description?: string;
  category?: string;
  topicTag?: string;
  hostName?: string;
  mode?: UnloadRoomMode;
  durationMinutes?: number;
  capacity?: number;
  isPrivate?: boolean;
}): Promise<UnloadRoom> {
  const roomCode = generateRoomCode();
  const duration = params.durationMinutes ?? 45;
  const expiresAt = new Date(Date.now() + Math.max(15, duration) * 60_000).toISOString();
  const categoryTag = params.category?.trim() || params.topicTag?.trim() || 'Community Space';

  const { data, error } = await supabase
    .from('rooms')
    .insert({
      room_code: roomCode,
      host_id: params.hostId,
      title: params.title.trim(),
      description: (params.description || '').trim(),
      category: categoryTag,
      topic_tag: categoryTag,
      mode: params.mode || 'VOICE_AND_TEXT',
      duration_minutes: duration,
      max_participants: params.capacity ?? 25,
      capacity: params.capacity ?? 25,
      is_private: Boolean(params.isPrivate),
      is_public: !params.isPrivate,
      is_active: true,
      expires_at: expiresAt
    })
    .select('*, profiles:host_id (*)')
    .single();

  if (error || !data) throw new Error(error?.message || 'Unable to create room.');

  await supabase.from('room_members').upsert({
    room_id: data.id,
    user_id: params.hostId,
    role: 'HOST'
  });

  return mapRoomRow(data);
}

export async function lookupAndJoinRoomByCode(rawCode: string, userId: string): Promise<UnloadRoom> {
  const normalized = normalizeRoomCode(rawCode);
  if (!normalized) throw new Error('Enter a valid Room ID (e.g. VYB-7K4M-92QX).');

  let query = supabase.from('rooms').select('*, profiles:host_id (*), room_members (user_id)');
  if (normalized.startsWith('VYB-')) {
    query = query.eq('room_code', normalized);
  } else {
    query = query.eq('id', normalized);
  }

  const { data: rows } = await query.limit(1);
  let target = rows?.[0];

  if (!target) {
    const { data: activeRows } = await supabase
      .from('rooms')
      .select('*, profiles:host_id (*), room_members (user_id)')
      .eq('is_active', true)
      .limit(80);
    target = (activeRows || []).find((r: any) => {
      const mapped = mapRoomRow(r);
      return mapped.roomCode.toUpperCase() === normalized.toUpperCase() || mapped.id === normalized;
    });
  }

  if (!target) throw new Error(`No active room found for ID "${normalized}".`);
  if (target.is_active === false) throw new Error('This room has ended.');
  if (target.expires_at && Date.parse(target.expires_at) <= Date.now()) {
    throw new Error('This room has expired.');
  }

  const mapped = mapRoomRow(target);
  if (mapped.activeMembersCount >= mapped.capacity && mapped.hostId !== userId) {
    throw new Error(`This room is full (${mapped.capacity}/${mapped.capacity}).`);
  }

  await supabase.from('room_members').upsert({
    room_id: mapped.id,
    user_id: userId,
    role: mapped.hostId === userId ? 'HOST' : 'LISTENER'
  });

  return mapped;
}

export async function fetchRoomMessages(roomId: string): Promise<UnloadRoomMessage[]> {
  const { data } = await supabase
    .from('room_messages')
    .select('*, profiles:sender_id (*)')
    .eq('room_id', roomId)
    .order('created_at', { ascending: true })
    .limit(120);

  return (data || []).map((m: any) => ({
    id: String(m.id),
    roomId: String(m.room_id),
    senderId: String(m.sender_id),
    senderName: m.is_anonymous
      ? 'Anonymous Vyber'
      : String(m.sender_name || m.sender_alias || m.profiles?.display_name || 'VYBE Member'),
    isAnonymous: Boolean(m.is_anonymous),
    content: String(m.content || m.text || ''),
    createdAtIso: m.created_at || null
  }));
}

export async function postRoomMessage(params: {
  roomId: string;
  senderId: string;
  senderName: string;
  content: string;
  isAnonymous: boolean;
}): Promise<void> {
  const alias = params.isAnonymous ? 'Anonymous Vyber' : params.senderName;
  const { error } = await supabase.from('room_messages').insert({
    room_id: params.roomId,
    sender_id: params.senderId,
    sender_name: alias,
    sender_alias: alias.slice(0, 50),
    is_anonymous: params.isAnonymous,
    content: params.content.trim(),
    text: params.content.trim()
  });
  if (error) throw new Error(error.message);
}

// ============================================================================
// DIRECT MESSAGING & CONVERSATIONS
// ============================================================================

export async function fetchConversations(currentUserId: string): Promise<Conversation[]> {
  if (!isSupabaseConfigured || !currentUserId) return [];
  const { data: myMemberships } = await supabase
    .from('conversation_members')
    .select('conversation_id')
    .eq('user_id', currentUserId);

  const convIds = (myMemberships || []).map((m: any) => String(m.conversation_id));
  if (convIds.length === 0) return [];

  const [{ data: convRows }, { data: allMemberRows }, { data: messageRows }] = await Promise.all([
    supabase.from('conversations').select('*').in('id', convIds).order('updated_at', { ascending: false }),
    supabase
      .from('conversation_members')
      .select('conversation_id, user_id, profiles:user_id (*)')
      .in('conversation_id', convIds),
    supabase
      .from('messages')
      .select('*')
      .in('conversation_id', convIds)
      .order('created_at', { ascending: true })
      .limit(400)
  ]);

  return (convRows || []).map((c: any) => {
    const cid = String(c.id);
    const members = (allMemberRows || [])
      .filter((m: any) => String(m.conversation_id) === cid && m.profiles)
      .map((m: any) => mapProfileRow(m.profiles));
    const peer = members.find((u) => u.id !== currentUserId) ||
      members[0] || {
        id: 'peer',
        username: 'vyber',
        displayName: c.group_name || 'VYBE Member',
        firstName: '',
        lastName: '',
        bio: '',
        avatarUrl: '',
        coverUrl: '',
        email: '',
        phone: '',
        dateOfBirth: '',
        isEmailVerified: false,
        isPhoneVerified: false,
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
        createdAtIso: null,
        lastSeenAtIso: null,
        isOnline: false
      };

    const msgs: DirectMessage[] = (messageRows || [])
      .filter((m: any) => String(m.conversation_id) === cid)
      .map((m: any) => ({
        id: String(m.id),
        conversationId: cid,
        senderId: String(m.sender_id),
        text: m.is_deleted_for_everyone ? 'Message unsent' : String(m.content || ''),
        mediaUrl: m.is_deleted_for_everyone ? null : m.media_url || null,
        mediaType: String(m.media_type || 'TEXT'),
        isDeletedForEveryone: Boolean(m.is_deleted_for_everyone),
        isEdited: Boolean(m.is_edited),
        isRead: true,
        reactions: [],
        createdAtIso: m.created_at || null,
        status: 'SENT'
      }));

    return {
      id: cid,
      isGroup: Boolean(c.is_group),
      groupName: c.group_name || null,
      peerUser: peer,
      members,
      messages: msgs,
      unreadCount: 0,
      updatedAtIso: c.updated_at || msgs[msgs.length - 1]?.createdAtIso || c.created_at || null
    };
  });
}

export async function getOrCreateDirectConversation(myId: string, peerId: string): Promise<string> {
  const { data: rpcId, error: rpcErr } = await supabase.rpc('get_or_create_direct_conversation', {
    p_other_user_id: peerId
  });
  if (!rpcErr && rpcId) return String(rpcId);

  const existing = await fetchConversations(myId);
  const match = existing.find((c) => !c.isGroup && c.peerUser.id === peerId);
  if (match) return match.id;

  const { data: created, error: convErr } = await supabase
    .from('conversations')
    .insert({ is_group: false, created_by: myId })
    .select('id')
    .single();
  if (convErr || !created?.id) throw new Error(convErr?.message || 'Unable to start conversation.');

  await supabase.from('conversation_members').insert([
    { conversation_id: created.id, user_id: myId, is_admin: true },
    { conversation_id: created.id, user_id: peerId, is_admin: false }
  ]);
  return String(created.id);
}

export async function sendDirectMessagePersisted(params: {
  conversationId: string;
  senderId: string;
  text?: string;
  content?: string;
  file?: File | null;
  mediaUrl?: string | null;
  mediaType?: string;
}): Promise<string> {
  let mediaUrl = params.mediaUrl || null;
  let mediaType = params.mediaType || 'TEXT';
  const textBody = (params.text ?? params.content ?? '').trim();

  if (params.file) {
    mediaType = params.file.type.startsWith('video/') ? 'VIDEO' : 'IMAGE';
    const up = await uploadMediaToBucket('chat-media', params.senderId, params.file);
    mediaUrl = up.publicUrl;
  }

  const { data: inserted, error } = await supabase
    .from('messages')
    .insert({
      conversation_id: params.conversationId,
      sender_id: params.senderId,
      content: textBody || (mediaType === 'IMAGE' ? 'Shared a photo' : mediaType === 'VIDEO' ? 'Shared a video' : 'Message'),
      media_url: mediaUrl,
      media_type: mediaType
    })
    .select('id')
    .maybeSingle();

  if (error) throw new Error(error.message);

  await supabase
    .from('conversations')
    .update({ updated_at: new Date().toISOString() })
    .eq('id', params.conversationId);

  return inserted?.id ? String(inserted.id) : crypto.randomUUID();
}

// ============================================================================
// DISCOVERY, NOTIFICATIONS, VERIFICATION, COPYRIGHT/REPORTS & ADMIN
// ============================================================================

export async function searchUsersAndPosts(
  query: string,
  limit = 20
): Promise<{
  users: VybeUser[];
  posts: VybePost[];
  shops: BusinessShop[];
}> {
  if (!isSupabaseConfigured) return { users: [], posts: [], shops: [] };
  const q = query.trim().replace(/^@/, '');
  const userReq = q
    ? supabase
        .from('profiles')
        .select('*')
        .or(`username.ilike.%${q}%,display_name.ilike.%${q}%`)
        .limit(limit)
    : supabase.from('profiles').select('*').order('followers_count', { ascending: false }).limit(limit);

  const shopReq = q
    ? supabase.from('business_profiles').select('*').ilike('business_name', `%${q}%`).limit(limit)
    : supabase.from('business_profiles').select('*').limit(limit);

  const postReq = q
    ? supabase
        .from('posts')
        .select(`*, profiles:author_id (*), post_media (*), comments (*, profiles:author_id (*))`)
        .ilike('caption', `%${q}%`)
        .limit(limit)
    : supabase
        .from('posts')
        .select(`*, profiles:author_id (*), post_media (*), comments (*, profiles:author_id (*))`)
        .order('created_at', { ascending: false })
        .limit(limit);

  const [{ data: uRows }, { data: sRows }, { data: pRows }] = await Promise.all([userReq, shopReq, postReq]);

  const mappedPosts: VybePost[] = (pRows || []).map((r: any) => ({
    id: String(r.id),
    author: mapProfileRow(r.profiles || { id: r.author_id }),
    caption: String(r.caption || ''),
    moodZone: (r.mood_zone || 'GENERAL') as MoodZone,
    mediaType: ((r.media_type || 'IMAGE') as string).toUpperCase() as MediaType,
    mediaUrls: (r.post_media || []).map((pm: any) => pm.media_url || ''),
    isUnfiltered: Boolean(r.is_unfiltered),
    isVybesReel: Boolean(r.is_vybes_reel),
    locationName: r.location_name || null,
    musicTitle: r.music_title || null,
    likesCount: Number(r.likes_count ?? r.reactions_count ?? 0),
    commentsCount: Number(r.comments_count ?? (r.comments ? r.comments.length : 0)),
    savesCount: Number(r.saves_count ?? 0),
    viewsCount: Number(r.views_count ?? 0),
    isLiked: false,
    isSaved: false,
    createdAtIso: r.created_at || null,
    comments: []
  }));

  return {
    users: (uRows || []).map(mapProfileRow),
    posts: mappedPosts,
    shops: (sRows || []).map((s: any) => ({
      id: String(s.id),
      ownerId: String(s.owner_id),
      businessName: String(s.business_name || ''),
      category: String(s.category || 'Local Shop'),
      description: String(s.description || ''),
      city: String(s.city || ''),
      verificationStatus: String(s.verification_status || 'PENDING'),
      createdAtIso: s.created_at || null
    }))
  };
}

export async function fetchNotifications(userId: string): Promise<VybeNotificationItem[]> {
  if (!isSupabaseConfigured || !userId) return [];
  const { data } = await supabase
    .from('notifications')
    .select('*, profiles:actor_id (*)')
    .eq('recipient_id', userId)
    .order('created_at', { ascending: false })
    .limit(50);

  return (data || []).map((n: any) => ({
    id: String(n.id),
    type: String(n.type || 'SYSTEM'),
    title: String(n.title || 'VYBE Update'),
    message: String(n.message || ''),
    actor: n.profiles ? mapProfileRow(n.profiles) : null,
    targetId: n.target_id ? String(n.target_id) : null,
    isRead: Boolean(n.is_read),
    createdAtIso: n.created_at || null
  }));
}

export async function toggleFollowUser(myId: string, targetUser: VybeUser, currentlyFollowing: boolean): Promise<string> {
  if (currentlyFollowing) {
    await supabase.from('follows').delete().eq('follower_id', myId).eq('followed_id', targetUser.id);
    return 'UNFOLLOWED';
  }
  const nextStatus = targetUser.isPrivate ? 'PENDING' : 'ACCEPTED';
  const { error } = await supabase.from('follows').upsert({
    follower_id: myId,
    followed_id: targetUser.id,
    status: nextStatus
  });
  if (error) throw new Error(error.message);
  return nextStatus;
}

export async function checkFollowStatus(myId: string, targetId: string): Promise<'NONE' | 'PENDING' | 'ACCEPTED'> {
  if (!isSupabaseConfigured || !myId || !targetId) return 'NONE';
  const { data } = await supabase
    .from('follows')
    .select('status')
    .eq('follower_id', myId)
    .eq('followed_id', targetId)
    .maybeSingle();
  if (!data) return 'NONE';
  return data.status === 'PENDING' ? 'PENDING' : 'ACCEPTED';
}

export async function submitReportOrCopyrightClaim(params: {
  reporterId: string;
  targetType: 'USER' | 'POST' | 'ROOM' | 'COPYRIGHT' | 'MESSAGE';
  targetId: string;
  reason: string;
}): Promise<void> {
  const { error } = await supabase.from('reports').insert({
    reporter_id: params.reporterId,
    target_type: params.targetType,
    target_id: params.targetId,
    reason: params.reason.trim()
  });
  if (error) throw new Error(error.message);
}

export async function submitBusinessVerificationRequest(params: {
  ownerId: string;
  businessName: string;
  category: string;
  registrationNumber: string;
  city: string;
  description: string;
}): Promise<void> {
  const { error } = await supabase.from('business_profiles').insert({
    owner_id: params.ownerId,
    business_name: params.businessName.trim(),
    category: params.category.trim(),
    gstin_or_registration_number: params.registrationNumber.trim(),
    city: params.city.trim(),
    description: params.description.trim()
  });
  if (error) throw new Error(error.message);
}

export async function fetchAdminModerationQueue(): Promise<{
  reports: ModerationReport[];
  businesses: BusinessShop[];
}> {
  const [{ data: rRows }, { data: bRows }] = await Promise.all([
    supabase.from('reports').select('*').order('created_at', { ascending: false }).limit(50),
    supabase.from('business_profiles').select('*').order('created_at', { ascending: false }).limit(50)
  ]);

  return {
    reports: (rRows || []).map((r: any) => ({
      id: String(r.id),
      reporterId: String(r.reporter_id),
      targetType: String(r.target_type),
      targetId: String(r.target_id),
      reason: String(r.reason),
      status: String(r.status || 'OPEN'),
      createdAtIso: r.created_at || null
    })),
    businesses: (bRows || []).map((b: any) => ({
      id: String(b.id),
      ownerId: String(b.owner_id),
      businessName: String(b.business_name),
      category: String(b.category),
      description: String(b.description || ''),
      city: String(b.city || ''),
      verificationStatus: String(b.verification_status || 'PENDING'),
      createdAtIso: b.created_at || null
    }))
  };
}
