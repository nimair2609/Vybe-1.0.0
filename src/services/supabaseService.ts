import { User, Post, Story, PostComment, MoodType, OfficialVybeBadge } from '../types';

export const SUPABASE_URL = 'https://cppwadedazpbevkfrchg.supabase.co';
export const SUPABASE_ANON_KEY = 'sb_publishable_MI-UdOqfccHVZIHjQq9vrw_IjEzeR7H';

const headers: Record<string, string> = {
  'apikey': SUPABASE_ANON_KEY,
  ...(SUPABASE_ANON_KEY.startsWith('eyJ') ? { 'Authorization': `Bearer ${SUPABASE_ANON_KEY}` } : {}),
  'Content-Type': 'application/json',
  'Prefer': 'return=representation'
};

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

// 1. Fetch live profiles from Supabase
export async function fetchLiveProfiles(): Promise<User[]> {
  try {
    const res = await fetch(`${SUPABASE_URL}/rest/v1/profiles?select=*&order=created_at.desc&limit=60`, {
      headers
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    if (Array.isArray(data)) {
      return data.map(mapSupabaseProfileToUser);
    }
  } catch (err) {
    console.warn('[Supabase] Failed to fetch profiles:', err);
  }
  return [];
}

// 2. Fetch live posts with joined profiles & post_media
export async function fetchLivePosts(userMap?: Map<string, User>): Promise<Post[]> {
  try {
    const res = await fetch(
      `${SUPABASE_URL}/rest/v1/posts?select=*,profiles:author_id(*),post_media(*)&order=created_at.desc&limit=60`,
      { headers }
    );
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    if (Array.isArray(data) && data.length > 0) {
      return data.map(row => mapSupabasePost(row, userMap));
    }
  } catch (err) {
    console.warn('[Supabase] Failed to fetch posts:', err);
  }
  return [];
}

// 3. Fetch live stories with joined profiles
export async function fetchLiveStories(userMap?: Map<string, User>): Promise<Story[]> {
  try {
    const res = await fetch(
      `${SUPABASE_URL}/rest/v1/stories?select=*,profiles:author_id(*)&order=created_at.desc&limit=40`,
      { headers }
    );
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    if (Array.isArray(data) && data.length > 0) {
      return data.map(row => mapSupabaseStory(row, userMap));
    }
  } catch (err) {
    console.warn('[Supabase] Failed to fetch stories:', err);
  }
  return [];
}

// 4. Fetch comments for a post
export async function fetchCommentsForPost(postId: string, userMap?: Map<string, User>): Promise<PostComment[]> {
  try {
    const res = await fetch(
      `${SUPABASE_URL}/rest/v1/comments?post_id=eq.${encodeURIComponent(postId)}&select=*,profiles:author_id(*)&order=created_at.asc&limit=50`,
      { headers }
    );
    if (!res.ok) return [];
    const data = await res.json();
    if (Array.isArray(data)) {
      return data.map(c => {
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
    }
  } catch (err) {
    console.warn('[Supabase] Failed to fetch comments for post:', postId, err);
  }
  return [];
}

// 5. Submit a real live comment
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
    return Array.isArray(data) ? data[0] : data;
  } catch (err) {
    console.warn('[Supabase] Comment post error:', err);
    return null;
  }
}

// 6. Create a live post in Supabase
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

    return created;
  } catch (err) {
    console.warn('[Supabase] createLivePost exception:', err);
    return null;
  }
}

// 7. Create a live story in Supabase
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
      return Array.isArray(data) ? data[0] : data;
    }
  } catch (err) {
    console.warn('[Supabase] createLiveStory exception:', err);
  }
  return null;
}

// 8. Sync Finger Heart reaction count to Supabase posts table
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
