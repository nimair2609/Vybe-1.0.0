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
  'is_private_account'
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
    isPrivate: Boolean(p.is_private_account)
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

export async function uploadMediaBlobToStorage(
  blob: Blob,
  bucket: 'posts' | 'avatars' | 'banners' | 'stories',
  fileName: string = 'media',
  originalSizeBytes?: number
): Promise<{ publicUrl: string; storagePath: string } | null> {
  const cleanExt = blob.type.includes('webp') ? 'webp' : (blob.type.includes('png') ? 'png' : 'jpg');
  const uniqueName = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}.${cleanExt}`;
  const storagePath = `${uniqueName}`;
  const targetUrl = `${SUPABASE_URL}/storage/v1/object/${bucket}/${storagePath}`;

  const token = localStorage.getItem('vybe_access_token') || localStorage.getItem('vybe_admin_jwt') || '';
  const uploadHeaders: Record<string, string> = {
    'apikey': SUPABASE_ANON_KEY,
    ...(token && token.startsWith('eyJ') ? { 'Authorization': `Bearer ${token}` } : { 'Authorization': `Bearer ${SUPABASE_ANON_KEY}` }),
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

    // Verify role in profiles table
    try {
      const profileRes = await fetch(`${SUPABASE_URL}/rest/v1/profiles?id=eq.${user.id}&select=id,role,is_verified,is_vip`, {
        headers: {
          'apikey': SUPABASE_ANON_KEY,
          'Authorization': `Bearer ${token}`
        }
      });

      if (profileRes.ok) {
        const profiles = await profileRes.json();
        const profile = Array.isArray(profiles) ? profiles[0] : profiles;
        const role = (profile?.role || '').toUpperCase();
        if (role && role !== 'ADMIN' && role !== 'SUPER_ADMIN' && role !== 'MODERATOR') {
          return {
            success: false,
            error: 'Forbidden: Authenticated account does not hold administrator privileges.'
          };
        }
      }
    } catch {
      // Proceed if profile query throttled
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
