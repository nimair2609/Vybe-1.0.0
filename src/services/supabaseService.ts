import { User, Post, Story, PostComment, MoodType, OfficialVybeBadge } from '../types';
import { vybeCache, CACHE_TTL } from '../utils/indexedDbCache';
import { vybeDiagnostics } from '../utils/performanceDiagnostics';

// Dynamic environment variables with production defaults
const metaEnv = (typeof import.meta !== 'undefined' ? (import.meta as any).env : {}) || {};
export const SUPABASE_URL = metaEnv.VITE_SUPABASE_URL || 'https://cppwadedazpbevkfrchg.supabase.co';
export const SUPABASE_ANON_KEY = metaEnv.VITE_SUPABASE_ANON_KEY || 'sb_publishable_MI-UdOqfccHVZIHjQq9vrw_IjEzeR7H';

const headers: Record<string, string> = {
  'apikey': SUPABASE_ANON_KEY,
  ...(SUPABASE_ANON_KEY.startsWith('eyJ') ? { 'Authorization': `Bearer ${SUPABASE_ANON_KEY}` } : {}),
  'Content-Type': 'application/json',
  'Prefer': 'return=representation'
};

// In-flight request deduplication map to prevent parallel duplicate requests
const inFlightRequests = new Map<string, Promise<any>>();

function deduplicatedFetch<T>(key: string, fetcher: () => Promise<T>): Promise<T> {
  if (inFlightRequests.has(key)) {
    vybeDiagnostics.recordDeduplication(key);
    return inFlightRequests.get(key) as Promise<T>;
  }

  const promise = fetcher().finally(() => {
    inFlightRequests.delete(key);
  });

  inFlightRequests.set(key, promise);
  return promise;
}

// -------------------------------------------------------------
// Explicit Column Selections (Eliminating `select=*` Egress Waste)
// -------------------------------------------------------------
const PROFILE_SELECT_COLUMNS = [
  'id',
  'username',
  'display_name',
  'avatar_url',
  'cover_url',
  'bio',
  'is_verified',
  'is_vip',
  'is_worker',
  'is_verified_business',
  'followers_count',
  'following_count',
  'posts_count',
  'current_mood',
  'live_pulse',
  'last_seen_at',
  'created_at',
  'is_private_account',
  'role'
].join(',');

const POST_SELECT_COLUMNS = [
  'id',
  'author_id',
  'caption',
  'media_type',
  'media_url',
  'media_urls',
  'is_vybes_reel',
  'is_unfiltered',
  'likes_count',
  'comments_count',
  'saves_count',
  'shares_count',
  'views_count',
  'mood_zone',
  'love_count',
  'flame_count',
  'laugh_count',
  'wow_count',
  'sad_count',
  'created_at',
  `profiles:author_id(${PROFILE_SELECT_COLUMNS})`,
  'post_media(id,media_url,media_type,display_order)'
].join(',');

const STORY_SELECT_COLUMNS = [
  'id',
  'author_id',
  'media_url',
  'caption',
  'media_type',
  'story_type',
  'created_at',
  'expires_at',
  `profiles:author_id(id,username,display_name,avatar_url,is_verified,is_vip,is_worker,is_verified_business)`
].join(',');

export function mapSupabaseProfileToUser(p: any): User {
  const isVerified = Boolean(p.is_verified || p.is_verified_public_figure);
  const isVip = Boolean(p.is_vip);
  const isWorker = Boolean(p.is_worker);
  const isShop = Boolean(p.is_verified_business);

  const badges: OfficialVybeBadge[] = [];
  if (isVerified) badges.push('VERIFIED_BLUE');
  if (isVip) badges.push('VIP_GOLD');
  if (isWorker) badges.push('WORKER_GREEN');
  if (isShop) badges.push('SHOP_GOLD');

  const avatar = p.avatar_url || `https://api.dicebear.com/7.x/bottts/svg?seed=${encodeURIComponent(p.username || p.id || 'vybe')}`;

  return {
    id: p.id,
    username: p.username || `user_${p.id ? p.id.slice(0, 6) : 'vybe'}`,
    displayName: p.display_name || p.username || 'VYBE Member',
    publicVybeId: `VYBE-${(p.username || (p.id ? p.id.slice(0, 6) : 'MEMBER')).toUpperCase()}`,
    bio: p.bio || 'Exploring live vibes on VYBE.',
    avatarUrl: avatar,
    coverUrl: p.cover_url || 'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=1200&q=80',
    isVerified,
    isVip,
    isVerifiedBusiness: isShop,
    isWorker,
    followersCount: Number(p.followers_count || 0),
    followingCount: Number(p.following_count || 0),
    postsCount: Number(p.posts_count || 0),
    currentMood: (p.current_mood as MoodType) || 'GENERAL',
    livePulse: p.live_pulse === 'OPEN_TO_CHAT' ? 'Open to chatting' : 'Feeling creative',
    isOnline: Boolean(p.last_seen_at && (Date.now() - new Date(p.last_seen_at).getTime() < 3600000 * 48)),
    badges,
    joinedDate: p.created_at ? new Date(p.created_at).toLocaleDateString('en-US', { month: 'short', year: 'numeric' }) : 'Recently',
    isPrivate: Boolean(p.is_private_account),
    role: p.role || 'USER'
  };
}

export function mapSupabasePost(row: any, userMap?: Map<string, User>): Post {
  let authorUser: User;
  if (row.profiles) {
    authorUser = mapSupabaseProfileToUser(row.profiles);
  } else if (userMap && userMap.has(row.author_id)) {
    authorUser = userMap.get(row.author_id)!;
  } else {
    authorUser = {
      id: row.author_id || 'unknown',
      username: 'vybe_creator',
      displayName: 'VYBE Creator',
      publicVybeId: 'VYBE-CREATOR',
      bio: '',
      avatarUrl: `https://api.dicebear.com/7.x/bottts/svg?seed=${row.author_id || 'vybe'}`,
      coverUrl: '',
      isVerified: false,
      isVip: false,
      isVerifiedBusiness: false,
      isWorker: false,
      followersCount: 0,
      followingCount: 0,
      postsCount: 0,
      livePulse: 'Open to chatting',
      isOnline: true,
      badges: [],
      joinedDate: 'Recently'
    };
  }

  const mediaUrls: string[] = [];
  if (Array.isArray(row.post_media) && row.post_media.length > 0) {
    for (const m of row.post_media) {
      if (m.media_url) mediaUrls.push(m.media_url);
    }
  } else if (row.media_url) {
    mediaUrls.push(row.media_url);
  } else if (row.media_urls && Array.isArray(row.media_urls)) {
    mediaUrls.push(...row.media_urls);
  }

  const isVideo = row.media_type === 'VIDEO' || row.media_type === 'REEL' || mediaUrls.some(u => u.includes('.mp4') || u.includes('.webm'));
  const isVybesReel = Boolean(row.is_vybes_reel || row.media_type === 'REEL');

  return {
    id: row.id,
    author: authorUser,
    caption: row.caption || '',
    mediaUrls,
    mediaType: isVybesReel ? 'REEL' : (isVideo ? 'VIDEO' : (mediaUrls.length > 0 ? 'IMAGE' : 'TEXT')),
    timestamp: row.created_at ? new Date(row.created_at).toLocaleDateString() : 'Just now',
    createdAtIso: row.created_at,
    likesCount: Number(row.likes_count || 0),
    commentsCount: Number(row.comments_count || 0),
    savesCount: Number(row.saves_count || 0),
    sharesCount: Number(row.shares_count || 0),
    viewsCount: Number(row.views_count || 0),
    isLiked: false,
    isSaved: false,
    isUnfiltered: Boolean(row.is_unfiltered),
    isVybesReel,
    tags: row.caption ? (row.caption.match(/#[a-z0-9_]+/gi) || []).map((t: string) => t.replace('#', '')) : [],
    moodZone: (row.mood_zone as MoodType) || 'GENERAL',
    comments: [],
    reactionsCount: {
      LOVE: Number(row.love_count || row.likes_count || 0),
      FLAME: Number(row.flame_count || 0),
      LAUGH: Number(row.laugh_count || 0),
      WOW: Number(row.wow_count || 0),
      SAD: Number(row.sad_count || 0)
    }
  };
}

export function mapSupabaseStory(row: any, userMap?: Map<string, User>): Story {
  let authorUser: User;
  if (row.profiles) {
    authorUser = mapSupabaseProfileToUser(row.profiles);
  } else if (userMap && userMap.has(row.author_id)) {
    authorUser = userMap.get(row.author_id)!;
  } else {
    authorUser = {
      id: row.author_id || 'unknown',
      username: 'story_creator',
      displayName: 'Story Creator',
      publicVybeId: 'VYBE-STORY',
      bio: '',
      avatarUrl: `https://api.dicebear.com/7.x/bottts/svg?seed=${row.author_id || 'story'}`,
      coverUrl: '',
      isVerified: false,
      isVip: false,
      isVerifiedBusiness: false,
      isWorker: false,
      followersCount: 0,
      followingCount: 0,
      postsCount: 0,
      livePulse: 'Open to chatting',
      isOnline: true,
      badges: [],
      joinedDate: 'Recently'
    };
  }

  const expiresAt = row.expires_at ? new Date(row.expires_at).getTime() : (Date.now() + 24 * 3600000);
  const remainingHours = Math.max(1, Math.round((expiresAt - Date.now()) / (3600000)));

  return {
    id: row.id,
    author: authorUser,
    mediaUrl: row.media_url || '',
    caption: row.caption || '',
    mediaType: row.media_type === 'VIDEO' ? 'VIDEO' : 'PHOTO',
    audience: 'Public',
    timestamp: row.created_at ? new Date(row.created_at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : 'Today',
    createdAtIso: row.created_at,
    expiresAtIso: row.expires_at,
    expiresHoursRemaining: remainingHours,
    isViewed: false,
    reactionsCount: 0,
    isLikedByMe: false
  };
}

// -------------------------------------------------------------
// 1. Fetch live profiles (Cached via IndexedDB + explicit columns)
// -------------------------------------------------------------
export async function fetchLiveProfiles(options: { forceRefresh?: boolean } = {}): Promise<User[]> {
  const cacheKey = 'live_profiles_v2';

  if (!options.forceRefresh) {
    const cached = await vybeCache.get<User[]>(cacheKey, { allowStale: true });
    if (cached && cached.data && cached.data.length > 0) {
      vybeDiagnostics.recordCacheHit(cacheKey);
      if (!cached.isStale) {
        return cached.data;
      }
    }
  }

  return deduplicatedFetch<User[]>(cacheKey, async () => {
    const startTime = Date.now();
    try {
      const url = `${SUPABASE_URL}/rest/v1/profiles?select=${encodeURIComponent(PROFILE_SELECT_COLUMNS)}&order=created_at.desc&limit=50`;
      const res = await fetch(url, { headers });
      const duration = Date.now() - startTime;

      if (!res.ok) {
        vybeDiagnostics.recordNetworkRequest(url, duration, res.status);
        throw new Error(`HTTP ${res.status}`);
      }

      const data = await res.json();
      vybeDiagnostics.recordNetworkRequest(url, duration, 200, JSON.stringify(data).length);

      if (Array.isArray(data)) {
        const users = data.map(mapSupabaseProfileToUser);
        vybeCache.set(cacheKey, users, CACHE_TTL.PROFILES);
        return users;
      }
    } catch (err) {
      console.warn('[Supabase] Failed to fetch profiles:', err);
    }

    const fallback = await vybeCache.get<User[]>(cacheKey, { allowStale: true });
    return fallback?.data || [];
  });
}

// -------------------------------------------------------------
// 2. Fetch live posts with pagination & explicit columns
// -------------------------------------------------------------
export async function fetchLivePosts(
  userMap?: Map<string, User>,
  options: {
    limit?: number;
    cursorCreatedAt?: string;
    forceRefresh?: boolean;
  } = {}
): Promise<Post[]> {
  const limit = options.limit || 20; // 20 posts per batch instead of 60
  const isFirstPage = !options.cursorCreatedAt;
  const cacheKey = isFirstPage ? `live_posts_first_page_${limit}` : `live_posts_cursor_${options.cursorCreatedAt}_${limit}`;

  if (!options.forceRefresh && isFirstPage) {
    const cached = await vybeCache.get<Post[]>(cacheKey, { allowStale: true });
    if (cached && cached.data && cached.data.length > 0) {
      vybeDiagnostics.recordCacheHit(cacheKey);
      if (!cached.isStale) {
        return cached.data;
      }
    }
  }

  return deduplicatedFetch<Post[]>(cacheKey, async () => {
    const startTime = Date.now();
    try {
      let queryParams = `select=${encodeURIComponent(POST_SELECT_COLUMNS)}&order=created_at.desc&limit=${limit}`;
      if (options.cursorCreatedAt) {
        queryParams += `&created_at=lt.${encodeURIComponent(options.cursorCreatedAt)}`;
      }

      const url = `${SUPABASE_URL}/rest/v1/posts?${queryParams}`;
      const res = await fetch(url, { headers });
      const duration = Date.now() - startTime;

      if (!res.ok) {
        vybeDiagnostics.recordNetworkRequest(url, duration, res.status);
        throw new Error(`HTTP ${res.status}`);
      }

      const data = await res.json();
      vybeDiagnostics.recordNetworkRequest(url, duration, 200, JSON.stringify(data).length);

      if (Array.isArray(data) && data.length > 0) {
        const posts = data.map(row => mapSupabasePost(row, userMap));
        if (isFirstPage) {
          vybeCache.set(cacheKey, posts, CACHE_TTL.POSTS_FEED);
        }
        return posts;
      }
    } catch (err) {
      console.warn('[Supabase] Failed to fetch posts:', err);
    }

    if (isFirstPage) {
      const fallback = await vybeCache.get<Post[]>(cacheKey, { allowStale: true });
      return fallback?.data || [];
    }
    return [];
  });
}

// -------------------------------------------------------------
// 3. Fetch live stories with caching
// -------------------------------------------------------------
export async function fetchLiveStories(
  userMap?: Map<string, User>,
  options: { forceRefresh?: boolean } = {}
): Promise<Story[]> {
  const cacheKey = 'live_stories_v2';

  if (!options.forceRefresh) {
    const cached = await vybeCache.get<Story[]>(cacheKey, { allowStale: true });
    if (cached && cached.data && cached.data.length > 0) {
      vybeDiagnostics.recordCacheHit(cacheKey);
      if (!cached.isStale) {
        return cached.data;
      }
    }
  }

  return deduplicatedFetch<Story[]>(cacheKey, async () => {
    const startTime = Date.now();
    try {
      const url = `${SUPABASE_URL}/rest/v1/stories?select=${encodeURIComponent(STORY_SELECT_COLUMNS)}&order=created_at.desc&limit=25`;
      const res = await fetch(url, { headers });
      const duration = Date.now() - startTime;

      if (!res.ok) {
        vybeDiagnostics.recordNetworkRequest(url, duration, res.status);
        throw new Error(`HTTP ${res.status}`);
      }

      const data = await res.json();
      vybeDiagnostics.recordNetworkRequest(url, duration, 200, JSON.stringify(data).length);

      if (Array.isArray(data) && data.length > 0) {
        const stories = data.map(row => mapSupabaseStory(row, userMap));
        vybeCache.set(cacheKey, stories, CACHE_TTL.STORIES);
        return stories;
      }
    } catch (err) {
      console.warn('[Supabase] Failed to fetch stories:', err);
    }

    const fallback = await vybeCache.get<Story[]>(cacheKey, { allowStale: true });
    return fallback?.data || [];
  });
}

// -------------------------------------------------------------
// 4. Fetch comments for a post (Cached)
// -------------------------------------------------------------
export async function fetchCommentsForPost(
  postId: string,
  userMap?: Map<string, User>
): Promise<PostComment[]> {
  if (!postId) return [];
  const cacheKey = `comments_${postId}`;

  const cached = await vybeCache.get<PostComment[]>(cacheKey, { allowStale: true });
  if (cached && cached.data && cached.data.length > 0 && !cached.isStale) {
    vybeDiagnostics.recordCacheHit(cacheKey);
    return cached.data;
  }

  return deduplicatedFetch<PostComment[]>(cacheKey, async () => {
    try {
      const commentCols = 'id,post_id,author_id,content,created_at,likes_count,profiles:author_id(id,username,display_name,avatar_url,is_verified,is_vip)';
      const url = `${SUPABASE_URL}/rest/v1/comments?post_id=eq.${encodeURIComponent(postId)}&select=${encodeURIComponent(commentCols)}&order=created_at.asc&limit=30`;
      const res = await fetch(url, { headers });

      if (!res.ok) return cached?.data || [];
      const data = await res.json();

      if (Array.isArray(data)) {
        const mapped = data.map(c => {
          const author = c.profiles ? mapSupabaseProfileToUser(c.profiles) : (userMap?.get(c.author_id) || {
            id: c.author_id || 'unknown',
            username: 'user',
            displayName: 'Member',
            publicVybeId: 'VYBE-USER',
            bio: '',
            avatarUrl: `https://api.dicebear.com/7.x/bottts/svg?seed=${c.author_id || 'c'}`,
            coverUrl: '',
            isVerified: false,
            isVip: false,
            isVerifiedBusiness: false,
            isWorker: false,
            followersCount: 0,
            followingCount: 0,
            postsCount: 0,
            livePulse: 'Open to chatting',
            isOnline: false,
            badges: [],
            joinedDate: 'Recently'
          });

          return {
            id: c.id,
            postId: c.post_id,
            author,
            content: c.content || '',
            timestamp: c.created_at ? new Date(c.created_at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : 'Just now',
            createdAtIso: c.created_at,
            likesCount: Number(c.likes_count || 0),
            isLiked: false
          };
        });

        vybeCache.set(cacheKey, mapped, CACHE_TTL.COMMENTS);
        return mapped;
      }
    } catch (err) {
      console.warn('[Supabase] Failed to fetch comments for post:', postId, err);
    }
    return cached?.data || [];
  });
}

// -------------------------------------------------------------
// 5. Submit a real live comment (Invalidates post comment cache)
// -------------------------------------------------------------
export async function submitLiveComment(postId: string, authorId: string, content: string): Promise<any> {
  try {
    const res = await fetch(`${SUPABASE_URL}/rest/v1/comments`, {
      method: 'POST',
      headers,
      body: JSON.stringify({
        post_id: postId,
        author_id: authorId,
        content: content
      })
    });
    if (!res.ok) {
      console.warn('[Supabase] Comment write failed:', res.status);
      return null;
    }
    const data = await res.json();
    // Invalidate local comments cache
    await vybeCache.delete(`comments_${postId}`);
    return Array.isArray(data) ? data[0] : data;
  } catch (err) {
    console.warn('[Supabase] Comment post error:', err);
    return null;
  }
}

// -------------------------------------------------------------
// 6. Create a live post in Supabase
// -------------------------------------------------------------
export async function createLivePost(postData: {
  authorId: string;
  caption: string;
  mediaUrls: string[];
  mediaType: string;
  isVybesReel?: boolean;
  moodZone?: string;
}): Promise<any> {
  try {
    const isReel = postData.isVybesReel || postData.mediaType === 'REEL';
    const isVideo = postData.mediaType === 'VIDEO' || isReel || postData.mediaUrls.some(u => u.includes('.mp4'));

    const postPayload = {
      author_id: postData.authorId,
      caption: postData.caption || '',
      media_type: isReel ? 'REEL' : (isVideo ? 'VIDEO' : (postData.mediaUrls.length > 0 ? 'IMAGE' : 'TEXT')),
      mood_zone: postData.moodZone || 'GENERAL',
      is_vybes_reel: Boolean(isReel),
      is_unfiltered: false,
      likes_count: 0,
      comments_count: 0,
      saves_count: 0,
      views_count: 1
    };

    const res = await fetch(`${SUPABASE_URL}/rest/v1/posts`, {
      method: 'POST',
      headers,
      body: JSON.stringify(postPayload)
    });

    if (!res.ok) {
      console.warn('[Supabase] Post insertion error:', res.status);
      return null;
    }

    const createdRows = await res.json();
    const created = Array.isArray(createdRows) ? createdRows[0] : createdRows;

    // Insert post_media if mediaUrls present
    if (created && created.id && postData.mediaUrls && postData.mediaUrls.length > 0) {
      const mediaInserts = postData.mediaUrls.map((url, idx) => ({
        post_id: created.id,
        media_url: url,
        media_type: url.includes('.mp4') ? 'VIDEO' : 'IMAGE',
        display_order: idx
      }));

      await fetch(`${SUPABASE_URL}/rest/v1/post_media`, {
        method: 'POST',
        headers,
        body: JSON.stringify(mediaInserts)
      }).catch(err => console.warn('[Supabase] Media insert error:', err));
    }

    // Invalidate live posts cache
    await vybeCache.invalidatePrefix('live_posts_');

    return created;
  } catch (err) {
    console.warn('[Supabase] createLivePost exception:', err);
    return null;
  }
}

// -------------------------------------------------------------
// 7. Create a live story in Supabase
// -------------------------------------------------------------
export async function createLiveStory(storyData: {
  authorId: string;
  mediaUrl: string;
  caption?: string;
  mediaType?: string;
}): Promise<any> {
  try {
    const payload = {
      author_id: storyData.authorId,
      media_url: storyData.mediaUrl,
      caption: storyData.caption || '',
      media_type: storyData.mediaType || (storyData.mediaUrl.includes('.mp4') ? 'VIDEO' : 'IMAGE'),
      story_type: storyData.mediaType || 'IMAGE',
      expires_at: new Date(Date.now() + 24 * 3600000).toISOString()
    };

    const res = await fetch(`${SUPABASE_URL}/rest/v1/stories`, {
      method: 'POST',
      headers,
      body: JSON.stringify(payload)
    });

    if (res.ok) {
      const data = await res.json();
      await vybeCache.delete('live_stories_v2');
      return Array.isArray(data) ? data[0] : data;
    }
  } catch (err) {
    console.warn('[Supabase] createLiveStory exception:', err);
  }
  return null;
}

// -------------------------------------------------------------
// 8. Sync Finger Heart reaction count to Supabase posts table
// -------------------------------------------------------------
export async function syncPostLikeCountToSupabase(postId: string, nextLikesCount: number): Promise<void> {
  if (!postId || postId.startsWith('post_')) return;
  try {
    await fetch(`${SUPABASE_URL}/rest/v1/posts?id=eq.${encodeURIComponent(postId)}`, {
      method: 'PATCH',
      headers,
      body: JSON.stringify({
        likes_count: Math.max(0, nextLikesCount)
      })
    });
  } catch (err) {
    console.warn('[Supabase] Reaction sync notice:', err);
  }
}

export function resolveMediaUrl(rawUrl: string): string {
  if (!rawUrl) return '';
  // Legacy Base64 strings: preserve and display directly without modification
  if (rawUrl.startsWith('data:')) return rawUrl;
  // If already full http(s) URL
  if (rawUrl.startsWith('http://') || rawUrl.startsWith('https://')) return rawUrl;
  // If it's a relative storage path (e.g. 'posts/xxx.webp' or 'stories/xxx.webp')
  if (rawUrl.startsWith('posts/') || rawUrl.startsWith('avatars/') || rawUrl.startsWith('banners/') || rawUrl.startsWith('stories/')) {
    return `${SUPABASE_URL}/storage/v1/object/public/${rawUrl}`;
  }
  return rawUrl;
}

function extractUserIdFromJwt(token: string): string | null {
  try {
    const parts = token.split('.');
    if (parts.length < 2) return null;
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
    const jsonStr = typeof atob === 'function' ? atob(padded) : '';
    if (!jsonStr) return null;
    const payload = JSON.parse(jsonStr);
    const sub = String(payload?.sub || '').trim();
    return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(sub) ? sub : null;
  } catch {
    return null;
  }
}

export async function uploadMediaBlobToStorage(
  blob: Blob,
  bucket: 'posts' | 'avatars' | 'banners' | 'stories',
  fileName: string = 'media',
  originalSizeBytes?: number
): Promise<{ publicUrl: string; storagePath: string } | null> {
  const token = localStorage.getItem('vybe_access_token') || localStorage.getItem('vybe_admin_jwt') || '';
  if (!token || !token.startsWith('eyJ')) {
    console.warn('[Supabase Storage] Upload aborted: authenticated user JWT required.');
    return null;
  }

  const uid = extractUserIdFromJwt(token);
  if (!uid) {
    console.warn('[Supabase Storage] Upload aborted: unable to resolve authenticated user UID from JWT.');
    return null;
  }

  const cleanExt = blob.type.includes('webp') ? 'webp' : (blob.type.includes('png') ? 'png' : 'jpg');
  const uniqueName = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}.${cleanExt}`;
  const storagePath = `${uid}/${uniqueName}`;
  const targetUrl = `${SUPABASE_URL}/storage/v1/object/${bucket}/${storagePath}`;

  const uploadHeaders: Record<string, string> = {
    'apikey': SUPABASE_ANON_KEY,
    'Authorization': `Bearer ${token}`,
    'Content-Type': blob.type || 'image/webp',
    'x-upsert': 'true'
  };

  const startTime = Date.now();
  try {
    const res = await fetch(targetUrl, {
      method: 'POST',
      headers: uploadHeaders,
      body: blob
    });

    const duration = Date.now() - startTime;
    if (res.ok) {
      vybeDiagnostics.recordNetworkRequest(targetUrl, duration, res.status, blob.size, 'POST');
      vybeDiagnostics.recordStorageUpload(bucket, blob.size, originalSizeBytes || blob.size);
      const publicUrl = `${SUPABASE_URL}/storage/v1/object/public/${bucket}/${storagePath}`;
      return { publicUrl, storagePath: `${bucket}/${storagePath}` };
    }

    if (res.status === 402) {
      vybeDiagnostics.recordQuotaViolation('Supabase Storage restricted due to exceed_cached_egress_quota.');
    }
  } catch (err) {
    console.warn('[Supabase Storage] Upload error:', err);
  }

  return null;
}

// -------------------------------------------------------------
// Secure Admin Authentication against Supabase Auth & profiles
// -------------------------------------------------------------
export async function adminSignInWithPassword(
  email: string,
  password: string
): Promise<{ success: boolean; token?: string; user?: any; error?: string }> {
  try {
    const authUrl = `${SUPABASE_URL}/auth/v1/token?grant_type=password`;
    const res = await fetch(authUrl, {
      method: 'POST',
      headers: {
        'apikey': SUPABASE_ANON_KEY,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ email: email.trim().toLowerCase(), password })
    });

    if (!res.ok) {
      if (res.status === 402) {
        vybeDiagnostics.recordQuotaViolation('Supabase Auth restricted by cached egress quota.');
        return {
          success: false,
          error: 'Supabase Quota Restriction (402): Egress limit reached on Supabase backend.'
        };
      }
      const errData = await res.json().catch(() => ({}));
      return {
        success: false,
        error: errData.error_description || errData.message || 'Invalid administrator email or password.'
      };
    }

    const data = await res.json();
    const token = data.access_token;
    const user = data.user;

    // Verify active record in admin_users table — fail CLOSED if check fails or lacks active admin role
    const adminRoles = new Set(['ADMIN', 'SUPER_ADMIN', 'OWNER_ADMIN', 'MODERATOR', 'SUPPORT_ADMIN', 'ANALYST']);
    let roleCheckSucceeded = false;
    let isAuthorizedAdmin = false;

    try {
      const adminUserRes = await fetch(
        `${SUPABASE_URL}/rest/v1/admin_users?user_id=eq.${encodeURIComponent(user.id)}&select=user_id,role,is_active&limit=1`,
        {
          headers: {
            'apikey': SUPABASE_ANON_KEY,
            'Authorization': `Bearer ${token}`
          }
        }
      );

      if (adminUserRes.ok) {
        roleCheckSucceeded = true;
        const adminRows = await adminUserRes.json();
        const adminRec = Array.isArray(adminRows) ? adminRows[0] : adminRows;
        const adminRole = String(adminRec?.role || '').toUpperCase().trim();
        const matchesUser = !adminRec?.user_id || String(adminRec.user_id).toLowerCase() === String(user.id).toLowerCase();
        if (adminRec && adminRec.is_active === true && matchesUser && adminRoles.has(adminRole)) {
          isAuthorizedAdmin = true;
        }
      }
    } catch {
      roleCheckSucceeded = false;
    }

    if (!roleCheckSucceeded) {
      return {
        success: false,
        error: 'Unable to verify administrator privileges right now. Sign-in aborted.'
      };
    }

    if (!isAuthorizedAdmin) {
      return {
        success: false,
        error: 'Forbidden: Active administrator record required.'
      };
    }

    localStorage.setItem('vybe_admin_jwt', token);
    localStorage.setItem('vybe_access_token', token);
    localStorage.setItem('vybe_admin_email', email.trim().toLowerCase());
    localStorage.setItem('vybe_admin_user_id', user.id);

    return { success: true, token, user };
  } catch (err: any) {
    return { success: false, error: err.message || 'Network error during authentication.' };
  }
}

export function getStoredAdminSession(): { token: string | null; email: string | null } {
  return {
    token: localStorage.getItem('vybe_admin_jwt'),
    email: localStorage.getItem('vybe_admin_email')
  };
}

export function adminSignOut(): void {
  localStorage.removeItem('vybe_admin_jwt');
  localStorage.removeItem('vybe_admin_email');
  localStorage.removeItem('vybe_admin_user_id');
}

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function getWebAuthHeaders(): Record<string, string> | null {
  const jwt = localStorage.getItem('vybe_access_token') || localStorage.getItem('vybe_admin_jwt') || '';
  if (!jwt || !jwt.startsWith('eyJ')) return null;
  return {
    'apikey': SUPABASE_ANON_KEY,
    'Authorization': `Bearer ${jwt}`,
    'Content-Type': 'application/json'
  };
}

export async function resolveOrCreateWebDirectConversation(
  myUserId: string,
  peerUserId: string
): Promise<string | null> {
  const authHeaders = getWebAuthHeaders();
  if (!authHeaders || !UUID_REGEX.test(peerUserId)) return null;

  try {
    const rpcRes = await fetch(`${SUPABASE_URL}/rest/v1/rpc/get_or_create_direct_conversation`, {
      method: 'POST',
      headers: authHeaders,
      body: JSON.stringify({
        p_peer_id: peerUserId,
        p_other_user_id: peerUserId
      })
    });
    if (rpcRes.ok) {
      const raw = (await rpcRes.text()).trim().replace(/^"|"$/g, '');
      if (UUID_REGEX.test(raw)) return raw;
    }

    if (UUID_REGEX.test(myUserId)) {
      const generatedId = crypto.randomUUID();
      const convRes = await fetch(`${SUPABASE_URL}/rest/v1/conversations`, {
        method: 'POST',
        headers: { ...authHeaders, 'Prefer': 'return=minimal' },
        body: JSON.stringify({
          id: generatedId,
          created_by: myUserId,
          is_group: false
        })
      });
      if (convRes.ok) {
        const memRes = await fetch(`${SUPABASE_URL}/rest/v1/conversation_members`, {
          method: 'POST',
          headers: { ...authHeaders, 'Prefer': 'resolution=ignore-duplicates,return=minimal' },
          body: JSON.stringify([
            { conversation_id: generatedId, user_id: myUserId },
            { conversation_id: generatedId, user_id: peerUserId }
          ])
        });
        if (memRes.ok) return generatedId;
      }
    }
  } catch (err) {
    console.warn('[Supabase] Web conversation resolution notice:', err);
  }
  return null;
}

export async function sendLiveDirectMessage(params: {
  conversationId: string;
  senderId: string;
  peerUserId: string;
  content: string;
}): Promise<{ conversationId: string; messageId?: string; createdAt?: string } | null> {
  const authHeaders = getWebAuthHeaders();
  if (!authHeaders) return null;

  try {
    let resolvedConvId = params.conversationId;
    if (!UUID_REGEX.test(resolvedConvId)) {
      const createdId = await resolveOrCreateWebDirectConversation(params.senderId, params.peerUserId);
      if (!createdId) return null;
      resolvedConvId = createdId;
    }

    const res = await fetch(`${SUPABASE_URL}/rest/v1/messages`, {
      method: 'POST',
      headers: { ...authHeaders, 'Prefer': 'return=representation' },
      body: JSON.stringify({
        conversation_id: resolvedConvId,
        sender_id: params.senderId,
        content: params.content,
        media_type: 'TEXT'
      })
    });
    if (!res.ok) return null;
    const rows = await res.json();
    const row = Array.isArray(rows) ? rows[0] : rows;
    return {
      conversationId: resolvedConvId,
      messageId: row?.id,
      createdAt: row?.created_at
    };
  } catch (err) {
    console.warn('[Supabase] Web sendLiveDirectMessage notice:', err);
    return null;
  }
}

// -------------------------------------------------------------
// Column-Scoped Admin Session & Live Moderation Helpers
// -------------------------------------------------------------
export function isVerifiedAdminSession(_user?: User | null): boolean {
  try {
    const adminJwt = typeof localStorage !== 'undefined' ? (localStorage.getItem('vybe_admin_jwt') || '') : '';
    return adminJwt.startsWith('eyJ');
  } catch {
    return false;
  }
}

function getAdminAuthHeaders(): { headers: Record<string, string>; token: string; adminId: string; adminEmail: string } | null {
  try {
    const token = localStorage.getItem('vybe_admin_jwt') || '';
    if (!token || !token.startsWith('eyJ')) return null;
    const adminId = localStorage.getItem('vybe_admin_user_id') || extractUserIdFromJwt(token) || '';
    const adminEmail = localStorage.getItem('vybe_admin_email') || 'admin';
    return {
      token,
      adminId,
      adminEmail,
      headers: {
        'apikey': SUPABASE_ANON_KEY,
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    };
  } catch {
    return null;
  }
}

export interface LiveVerificationRequestItem {
  id: string;
  userId: string;
  username: string;
  displayName: string;
  avatarUrl: string;
  requestType: string;
  badge: OfficialVybeBadge;
  category: string;
  proof: string;
  status: string;
  submittedAt: string;
}

export interface LiveReportItem {
  id: string;
  reporterId: string;
  reporterUsername: string;
  targetType: string;
  targetId: string;
  reason: string;
  status: string;
  adminNotes: string;
  createdAt: string;
}

export interface LiveAuditLogItem {
  id: string;
  adminId: string;
  adminEmail: string;
  action: string;
  targetType: string;
  targetId: string;
  details: string;
  createdAt: string;
}

export function mapRequestTypeToBadge(requestType: string): OfficialVybeBadge {
  const upper = String(requestType || '').toUpperCase();
  if (upper.includes('WORKER')) return 'WORKER_GREEN';
  if (upper.includes('VIP')) return 'VIP_GOLD';
  if (upper.includes('SHOP') || upper.includes('BUSINESS')) return 'SHOP_GOLD';
  return 'VERIFIED_BLUE';
}

export async function fetchLiveVerificationRequests(): Promise<LiveVerificationRequestItem[]> {
  const auth = getAdminAuthHeaders();
  if (!auth) return [];
  try {
    const selectCols = 'id,user_id,request_type,full_name,category,identity_document_url,supporting_links,status,rejection_reason,created_at,profiles:user_id(id,username,display_name,avatar_url)';
    const resp = await fetch(
      `${SUPABASE_URL}/rest/v1/verification_requests?select=${encodeURIComponent(selectCols)}&order=created_at.desc&limit=100`,
      { headers: auth.headers }
    );
    if (!resp.ok) return [];
    const rows = await resp.json();
    if (!Array.isArray(rows)) return [];
    return rows.map((r: any) => {
      const p = r.profiles || {};
      const links = Array.isArray(r.supporting_links) ? r.supporting_links.filter(Boolean).join(', ') : '';
      const proofParts = [links, r.identity_document_url ? `Document: ${r.identity_document_url}` : ''].filter(Boolean);
      return {
        id: r.id,
        userId: r.user_id,
        username: p.username || 'vybe_member',
        displayName: r.full_name || p.display_name || p.username || 'VYBE Member',
        avatarUrl: p.avatar_url || `https://api.dicebear.com/7.x/bottts/svg?seed=${encodeURIComponent(p.username || r.user_id || 'vybe')}`,
        requestType: r.request_type || 'VERIFIED',
        badge: mapRequestTypeToBadge(r.request_type),
        category: r.category || 'Creator',
        proof: proofParts.join(' • ') || 'Submitted application details',
        status: String(r.status || 'PENDING').toUpperCase(),
        submittedAt: r.created_at ? new Date(r.created_at).toLocaleString() : 'Recently'
      };
    });
  } catch (err) {
    console.warn('[Admin] fetchLiveVerificationRequests notice:', err);
    return [];
  }
}

export async function reviewVerificationRequestInSupabase(params: {
  requestId: string;
  decision: 'APPROVED' | 'REJECTED' | 'MORE_INFO_REQUIRED';
  notes?: string;
  vipExpiresAt?: string | null;
}): Promise<{ ok: boolean; error?: string }> {
  const auth = getAdminAuthHeaders();
  if (!auth) return { ok: false, error: 'Administrator session required.' };
  if (!UUID_REGEX.test(params.requestId)) return { ok: false, error: 'Invalid verification request ID.' };

  try {
    const resp = await fetch(`${SUPABASE_URL}/rest/v1/rpc/admin_review_verification_request`, {
      method: 'POST',
      headers: auth.headers,
      body: JSON.stringify({
        p_request_id: params.requestId,
        p_decision: params.decision,
        p_notes: params.notes || null,
        p_admin_id: UUID_REGEX.test(auth.adminId) ? auth.adminId : null,
        p_vip_expires_at: params.vipExpiresAt || null
      })
    });
    if (!resp.ok) {
      const errData = await resp.json().catch(() => ({}));
      return { ok: false, error: errData?.message || errData?.error || `RPC rejected (${resp.status}).` };
    }
    return { ok: true };
  } catch (err: any) {
    return { ok: false, error: err?.message || 'Network error reviewing verification request.' };
  }
}

export async function fetchLiveReports(): Promise<LiveReportItem[]> {
  const auth = getAdminAuthHeaders();
  if (!auth) return [];
  try {
    const selectCols = 'id,reporter_id,target_type,target_id,reason,status,admin_notes,created_at,reporter:reporter_id(id,username,display_name)';
    const resp = await fetch(
      `${SUPABASE_URL}/rest/v1/reports?select=${encodeURIComponent(selectCols)}&order=created_at.desc&limit=100`,
      { headers: auth.headers }
    );
    if (!resp.ok) return [];
    const rows = await resp.json();
    if (!Array.isArray(rows)) return [];
    return rows.map((r: any) => ({
      id: r.id,
      reporterId: r.reporter_id,
      reporterUsername: r.reporter?.username ? `@${r.reporter.username}` : (r.reporter_id || 'member'),
      targetType: String(r.target_type || 'POST').toUpperCase(),
      targetId: String(r.target_id || ''),
      reason: r.reason || 'Policy violation report',
      status: String(r.status || 'OPEN').toUpperCase(),
      adminNotes: r.admin_notes || '',
      createdAt: r.created_at ? new Date(r.created_at).toLocaleString() : 'Recently'
    }));
  } catch (err) {
    console.warn('[Admin] fetchLiveReports notice:', err);
    return [];
  }
}

export async function updateReportStatusInSupabase(params: {
  reportId: string;
  status: 'OPEN' | 'INVESTIGATING' | 'RESOLVED' | 'DISMISSED';
  adminNotes?: string;
}): Promise<{ ok: boolean; error?: string }> {
  const auth = getAdminAuthHeaders();
  if (!auth) return { ok: false, error: 'Administrator session required.' };
  if (!UUID_REGEX.test(params.reportId)) return { ok: false, error: 'Invalid report ID.' };

  try {
    const resp = await fetch(`${SUPABASE_URL}/rest/v1/reports?id=eq.${encodeURIComponent(params.reportId)}`, {
      method: 'PATCH',
      headers: { ...auth.headers, 'Prefer': 'return=representation' },
      body: JSON.stringify({
        status: params.status,
        ...(params.adminNotes !== undefined ? { admin_notes: params.adminNotes } : {})
      })
    });
    if (!resp.ok) {
      const errData = await resp.json().catch(() => ({}));
      return { ok: false, error: errData?.message || `Report update failed (${resp.status}).` };
    }
    await recordAdminAuditLogToSupabase({
      action: `REPORT_${params.status}`,
      targetType: 'reports',
      targetId: params.reportId,
      details: { status: params.status, adminNotes: params.adminNotes || null }
    });
    return { ok: true };
  } catch (err: any) {
    return { ok: false, error: err?.message || 'Network error updating report.' };
  }
}

export async function moderatePostInSupabase(params: {
  postId: string;
  action: 'DELETE' | 'ARCHIVE';
}): Promise<{ ok: boolean; error?: string }> {
  const auth = getAdminAuthHeaders();
  if (!auth) return { ok: false, error: 'Administrator session required.' };
  if (!UUID_REGEX.test(params.postId)) return { ok: false, error: 'Invalid post UUID.' };

  try {
    const isDelete = params.action === 'DELETE';
    const resp = await fetch(`${SUPABASE_URL}/rest/v1/posts?id=eq.${encodeURIComponent(params.postId)}`, {
      method: isDelete ? 'DELETE' : 'PATCH',
      headers: { ...auth.headers, 'Prefer': 'return=minimal' },
      ...(isDelete ? {} : { body: JSON.stringify({ is_archived: true }) })
    });
    if (!resp.ok) {
      const errData = await resp.json().catch(() => ({}));
      return { ok: false, error: errData?.message || `Post moderation failed (${resp.status}).` };
    }
    await recordAdminAuditLogToSupabase({
      action: `POST_${params.action}`,
      targetType: 'posts',
      targetId: params.postId,
      details: { action: params.action }
    });
    return { ok: true };
  } catch (err: any) {
    return { ok: false, error: err?.message || 'Network error moderating post.' };
  }
}

export async function suspendUserInSupabase(params: {
  userId: string;
  action: 'SUSPEND' | 'BAN' | 'RESTORE';
  reason?: string;
  durationHours?: number;
}): Promise<{ ok: boolean; error?: string }> {
  const auth = getAdminAuthHeaders();
  if (!auth) return { ok: false, error: 'Administrator session required.' };
  if (!UUID_REGEX.test(params.userId)) return { ok: false, error: 'Invalid user UUID.' };
  if (auth.adminId && auth.adminId.toLowerCase() === params.userId.toLowerCase()) {
    return { ok: false, error: 'Security Policy: Administrators cannot moderate their own account.' };
  }

  const hours = Math.max(1, Math.min(8760, params.durationHours || 72));
  const until = new Date(Date.now() + hours * 3600 * 1000).toISOString();
  const patch: Record<string, any> =
    params.action === 'BAN'
      ? { is_banned: true, ban_reason: params.reason || 'Safety moderation enforcement' }
      : params.action === 'SUSPEND'
        ? { is_suspended: true, suspension_reason: params.reason || `${hours}h suspension`, suspended_until: until }
        : { is_banned: false, is_suspended: false, ban_reason: null, suspension_reason: null, suspended_until: null };

  try {
    const resp = await fetch(`${SUPABASE_URL}/rest/v1/profiles?id=eq.${encodeURIComponent(params.userId)}`, {
      method: 'PATCH',
      headers: { ...auth.headers, 'Prefer': 'return=representation' },
      body: JSON.stringify(patch)
    });
    if (!resp.ok) {
      const errData = await resp.json().catch(() => ({}));
      return { ok: false, error: errData?.message || `Account moderation failed (${resp.status}).` };
    }

    await fetch(`${SUPABASE_URL}/rest/v1/user_moderation_actions`, {
      method: 'POST',
      headers: { ...auth.headers, 'Prefer': 'return=minimal' },
      body: JSON.stringify({
        user_id: params.userId,
        action_type: params.action === 'RESTORE' ? 'UNBAN' : params.action,
        reason: params.reason || `Admin ${params.action}`,
        duration_hours: params.action === 'SUSPEND' ? hours : null,
        expires_at: params.action === 'SUSPEND' ? until : null,
        is_active: params.action !== 'RESTORE',
        created_by: UUID_REGEX.test(auth.adminId) ? auth.adminId : null
      })
    }).catch(() => null);

    await recordAdminAuditLogToSupabase({
      action: `USER_${params.action}`,
      targetType: 'profiles',
      targetId: params.userId,
      details: { action: params.action, reason: params.reason || null }
    });

    return { ok: true };
  } catch (err: any) {
    return { ok: false, error: err?.message || 'Network error moderating user account.' };
  }
}

export async function recordAdminAuditLogToSupabase(params: {
  action: string;
  targetType: string;
  targetId: string;
  details?: Record<string, any>;
}): Promise<boolean> {
  const auth = getAdminAuthHeaders();
  if (!auth) return false;
  try {
    const resp = await fetch(`${SUPABASE_URL}/rest/v1/admin_audit_logs`, {
      method: 'POST',
      headers: { ...auth.headers, 'Prefer': 'return=minimal' },
      body: JSON.stringify({
        admin_id: UUID_REGEX.test(auth.adminId) ? auth.adminId : null,
        admin_email: auth.adminEmail,
        action: params.action.slice(0, 100),
        target_type: params.targetType.slice(0, 50),
        target_id: params.targetId.slice(0, 100),
        details: params.details || {}
      })
    });
    return resp.ok;
  } catch {
    return false;
  }
}

export async function fetchLiveAuditLogs(): Promise<LiveAuditLogItem[]> {
  const auth = getAdminAuthHeaders();
  if (!auth) return [];
  try {
    const selectCols = 'id,admin_id,admin_email,action,target_type,target_id,details,created_at';
    const resp = await fetch(
      `${SUPABASE_URL}/rest/v1/admin_audit_logs?select=${encodeURIComponent(selectCols)}&order=created_at.desc&limit=100`,
      { headers: auth.headers }
    );
    if (!resp.ok) return [];
    const rows = await resp.json();
    if (!Array.isArray(rows)) return [];
    return rows.map((r: any) => ({
      id: r.id,
      adminId: r.admin_id || '',
      adminEmail: r.admin_email || 'admin',
      action: r.action || 'ACTION',
      targetType: r.target_type || 'SYSTEM',
      targetId: r.target_id || '',
      details: typeof r.details === 'string' ? r.details : JSON.stringify(r.details || {}),
      createdAt: r.created_at ? new Date(r.created_at).toLocaleString() : 'Recently'
    }));
  } catch (err) {
    console.warn('[Admin] fetchLiveAuditLogs notice:', err);
    return [];
  }
}

