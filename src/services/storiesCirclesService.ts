import {
  AudioTrack,
  CircleEvent,
  CircleMember,
  CirclePoll,
  CirclePost,
  CircleResource,
  Community,
  StoryComment,
  StoryViewerItem
} from '../types';
import {
  SUPABASE_URL,
  UUID_REGEX,
  authedFetch,
  buildFallbackMinimalUser,
  formatRelativeTime,
  getOptionalRestHeaders,
  mapSupabaseProfileToUser,
  resolveMediaUrl,
  STORY_PROFILE_COLUMNS
} from './supabaseService';

// ---------------------------------------------------------------------------
// Stories 2.0: Live Audio Tracks Catalog, Views, Viewers, Comments
// ---------------------------------------------------------------------------
export async function fetchAudioTracksCatalog(): Promise<AudioTrack[]> {
  try {
    const hdrs = await getOptionalRestHeaders(false);
    const res = await fetch(
      `${SUPABASE_URL}/rest/v1/audio_tracks?is_active=eq.true&select=id,title,artist,album,genre,duration_sec,stream_url,cover_url,is_active&order=created_at.desc&limit=60`,
      { headers: hdrs }
    );
    if (!res.ok) return [];
    const rows = await res.json();
    if (!Array.isArray(rows)) return [];
    return rows
      .filter((r: any) => Boolean(r?.id && r?.stream_url))
      .map((r: any) => ({
        id: r.id,
        title: r.title || 'Untitled Track',
        artist: r.artist || 'VYBE Artist',
        album: r.album || '',
        genre: r.genre || 'Ambient',
        durationSec: Number(r.duration_sec || 30),
        audioUrl: resolveMediaUrl(r.stream_url),
        coverUrl: r.cover_url ? resolveMediaUrl(r.cover_url) : undefined,
        isActive: r.is_active !== false
      }));
  } catch {
    return [];
  }
}

export async function recordStoryViewRpc(storyId: string): Promise<{
  recorded: boolean;
  viewsCount?: number;
}> {
  if (!UUID_REGEX.test(storyId)) return { recorded: false };
  try {
    const res = await authedFetch(`${SUPABASE_URL}/rest/v1/rpc/record_story_view`, {
      method: 'POST',
      body: JSON.stringify({ p_story_id: storyId })
    });
    if (!res.ok) return { recorded: false };
    const data = await res.json().catch(() => null);
    return {
      recorded: Boolean(data?.recorded),
      viewsCount: typeof data?.views_count === 'number' ? data.views_count : undefined
    };
  } catch {
    return { recorded: false };
  }
}

export async function fetchStoryViewersRpc(storyId: string): Promise<StoryViewerItem[]> {
  if (!UUID_REGEX.test(storyId)) return [];
  try {
    const res = await authedFetch(`${SUPABASE_URL}/rest/v1/rpc/get_story_viewers`, {
      method: 'POST',
      body: JSON.stringify({ p_story_id: storyId })
    });
    if (!res.ok) return [];
    const rows = await res.json();
    if (!Array.isArray(rows)) return [];
    return rows.map((r: any) => ({
      viewerId: r.viewer_id,
      username: r.username || 'vybe_member',
      displayName: r.display_name || r.username || 'VYBE Member',
      avatarUrl: resolveMediaUrl(r.avatar_url || ''),
      isVerified: Boolean(r.is_verified),
      viewedAt: formatRelativeTime(r.viewed_at)
    }));
  } catch {
    return [];
  }
}

export async function fetchStoryComments(storyId: string): Promise<StoryComment[]> {
  if (!UUID_REGEX.test(storyId)) return [];
  try {
    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/story_comments?story_id=eq.${encodeURIComponent(storyId)}&select=id,story_id,author_id,content,created_at,profiles:author_id(${STORY_PROFILE_COLUMNS})&order=created_at.asc&limit=80`
    );
    if (!res.ok) return [];
    const rows = await res.json();
    if (!Array.isArray(rows)) return [];
    return rows.map((r: any) => ({
      id: r.id,
      storyId: r.story_id,
      author: r.profiles ? mapSupabaseProfileToUser(r.profiles) : buildFallbackMinimalUser(r.author_id),
      content: r.content || '',
      createdAtIso: r.created_at || new Date().toISOString(),
      timestamp: formatRelativeTime(r.created_at)
    }));
  } catch {
    return [];
  }
}

export async function submitStoryComment(
  storyId: string,
  authorId: string,
  content: string
): Promise<StoryComment | null> {
  if (!UUID_REGEX.test(storyId) || !UUID_REGEX.test(authorId) || !content.trim()) return null;
  try {
    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/story_comments?select=id,story_id,author_id,content,created_at,profiles:author_id(${STORY_PROFILE_COLUMNS})`,
      {
        method: 'POST',
        body: JSON.stringify({
          story_id: storyId,
          author_id: authorId,
          content: content.trim().slice(0, 500)
        })
      },
      { Prefer: 'return=representation' }
    );
    if (!res.ok) return null;
    const rows = await res.json();
    const r = Array.isArray(rows) ? rows[0] : rows;
    if (!r?.id) return null;
    return {
      id: r.id,
      storyId: r.story_id,
      author: r.profiles ? mapSupabaseProfileToUser(r.profiles) : buildFallbackMinimalUser(authorId),
      content: r.content || content.trim(),
      createdAtIso: r.created_at || new Date().toISOString(),
      timestamp: 'Just now'
    };
  } catch {
    return null;
  }
}

// ---------------------------------------------------------------------------
// Growth Circles 2.0: Creation, Membership, Feed, Polls, Events, Resources, Chat, Reports
// ---------------------------------------------------------------------------
export async function createCircleInSupabase(params: {
  creatorId: string;
  name: string;
  description: string;
  category: string;
  joinMode: 'PUBLIC' | 'PRIVATE' | 'APPROVAL';
  rules: string[];
  tags: string[];
  avatarUrl?: string;
  bannerUrl?: string;
}): Promise<{ ok: boolean; community?: Community; error?: string }> {
  if (!UUID_REGEX.test(params.creatorId)) return { ok: false, error: 'Sign in required.' };
  const payload = {
    creator_id: params.creatorId,
    name: params.name.trim(),
    description: params.description.trim(),
    category: params.category.trim() || 'Growth',
    join_mode: params.joinMode,
    is_private: params.joinMode !== 'PUBLIC',
    rules: params.rules.join('\n'),
    tags: params.tags,
    avatar_url: params.avatarUrl || '',
    banner_url: params.bannerUrl || '',
    members_count: 1
  };
  try {
    let res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/communities`,
      { method: 'POST', body: JSON.stringify(payload) },
      { Prefer: 'return=representation' }
    );
    if (res.status === 400) {
      res = await authedFetch(
        `${SUPABASE_URL}/rest/v1/communities`,
        {
          method: 'POST',
          body: JSON.stringify({
            creator_id: params.creatorId,
            name: params.name.trim(),
            description: params.description.trim(),
            category: params.category.trim() || 'Growth',
            is_private: params.joinMode !== 'PUBLIC',
            avatar_url: params.avatarUrl || '',
            banner_url: params.bannerUrl || '',
            members_count: 1
          })
        },
        { Prefer: 'return=representation' }
      );
    }
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      return { ok: false, error: err?.message || `Failed to create Circle (${res.status}).` };
    }
    const rows = await res.json();
    const row = Array.isArray(rows) ? rows[0] : rows;
    if (!row?.id) return { ok: false, error: 'Circle creation not confirmed.' };

    await authedFetch(
      `${SUPABASE_URL}/rest/v1/community_members?on_conflict=community_id,user_id`,
      {
        method: 'POST',
        body: JSON.stringify({
          community_id: row.id,
          user_id: params.creatorId,
          role: 'ADMIN',
          status: 'ACCEPTED'
        })
      },
      { Prefer: 'resolution=ignore-duplicates,return=minimal' }
    ).catch(() => null);

    return {
      ok: true,
      community: {
        id: row.id,
        creatorId: row.creator_id || params.creatorId,
        name: row.name,
        description: row.description || '',
        category: row.category || params.category,
        avatarUrl: resolveMediaUrl(row.avatar_url || ''),
        bannerUrl: resolveMediaUrl(row.banner_url || ''),
        membersCount: Number(row.members_count || 1),
        isJoined: true,
        isPending: false,
        joinMode: params.joinMode,
        myRole: 'OWNER',
        rules: params.rules,
        tags: params.tags
      }
    };
  } catch (err: any) {
    return { ok: false, error: err?.message || 'Network error creating Circle.' };
  }
}

export async function joinOrRequestCircleRpc(communityId: string): Promise<{
  ok: boolean;
  status?: 'ACCEPTED' | 'PENDING';
  error?: string;
}> {
  if (!UUID_REGEX.test(communityId)) return { ok: false, error: 'Invalid Circle ID.' };
  try {
    const res = await authedFetch(`${SUPABASE_URL}/rest/v1/rpc/join_or_request_circle`, {
      method: 'POST',
      body: JSON.stringify({ p_community_id: communityId })
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      return { ok: false, error: err?.message || `Could not join Circle (${res.status}).` };
    }
    const data = await res.json().catch(() => ({}));
    return {
      ok: true,
      status: String(data?.status || 'ACCEPTED').toUpperCase() === 'PENDING' ? 'PENDING' : 'ACCEPTED'
    };
  } catch (err: any) {
    return { ok: false, error: err?.message || 'Network error joining Circle.' };
  }
}

export async function fetchCircleMembers(communityId: string): Promise<CircleMember[]> {
  if (!UUID_REGEX.test(communityId)) return [];
  try {
    const hdrs = await getOptionalRestHeaders(false);
    const res = await fetch(
      `${SUPABASE_URL}/rest/v1/community_members?community_id=eq.${encodeURIComponent(communityId)}&select=community_id,user_id,role,status,joined_at,profiles:user_id(${STORY_PROFILE_COLUMNS})&order=joined_at.asc&limit=100`,
      { headers: hdrs }
    );
    if (!res.ok) return [];
    const rows = await res.json();
    if (!Array.isArray(rows)) return [];
    return rows.map((r: any) => {
      const roleUpper = String(r.role || 'MEMBER').toUpperCase();
      return {
        communityId: r.community_id,
        userId: r.user_id,
        role: roleUpper === 'OWNER' || roleUpper === 'ADMIN' ? 'OWNER' : roleUpper === 'MODERATOR' ? 'MODERATOR' : 'MEMBER',
        status: String(r.status || 'ACCEPTED').toUpperCase() as any,
        joinedAt: formatRelativeTime(r.joined_at),
        user: r.profiles ? mapSupabaseProfileToUser(r.profiles) : buildFallbackMinimalUser(r.user_id)
      };
    });
  } catch {
    return [];
  }
}

export async function reviewCircleMemberRequestRpc(
  communityId: string,
  targetUserId: string,
  approve: boolean
): Promise<{ ok: boolean; error?: string }> {
  try {
    const res = await authedFetch(`${SUPABASE_URL}/rest/v1/rpc/review_circle_member_request`, {
      method: 'POST',
      body: JSON.stringify({
        p_community_id: communityId,
        p_target_user_id: targetUserId,
        p_approve: approve
      })
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      return { ok: false, error: err?.message || `Action failed (${res.status}).` };
    }
    return { ok: true };
  } catch (err: any) {
    return { ok: false, error: err?.message || 'Network error.' };
  }
}

export async function setCircleMemberRoleRpc(
  communityId: string,
  targetUserId: string,
  role: 'MODERATOR' | 'MEMBER'
): Promise<{ ok: boolean; error?: string }> {
  try {
    const res = await authedFetch(`${SUPABASE_URL}/rest/v1/rpc/set_circle_member_role`, {
      method: 'POST',
      body: JSON.stringify({
        p_community_id: communityId,
        p_target_user_id: targetUserId,
        p_role: role
      })
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      return { ok: false, error: err?.message || `Role update failed (${res.status}).` };
    }
    return { ok: true };
  } catch (err: any) {
    return { ok: false, error: err?.message || 'Network error.' };
  }
}

export async function fetchCirclePosts(communityId: string): Promise<CirclePost[]> {
  if (!UUID_REGEX.test(communityId)) return [];
  try {
    const hdrs = await getOptionalRestHeaders(false);
    let res = await fetch(
      `${SUPABASE_URL}/rest/v1/community_posts?community_id=eq.${encodeURIComponent(communityId)}&select=id,community_id,author_id,title,content,media_url,post_type,is_pinned,likes_count,comments_count,created_at,profiles:author_id(${STORY_PROFILE_COLUMNS})&order=is_pinned.desc,created_at.desc&limit=50`,
      { headers: hdrs }
    );
    if (res.status === 400) {
      res = await fetch(
        `${SUPABASE_URL}/rest/v1/community_posts?community_id=eq.${encodeURIComponent(communityId)}&select=id,community_id,author_id,title,content,media_url,likes_count,comments_count,created_at,profiles:author_id(${STORY_PROFILE_COLUMNS})&order=created_at.desc&limit=50`,
        { headers: hdrs }
      );
    }
    if (!res.ok) return [];
    const rows = await res.json();
    if (!Array.isArray(rows)) return [];
    return rows.map((r: any) => ({
      id: r.id,
      communityId: r.community_id,
      author: r.profiles ? mapSupabaseProfileToUser(r.profiles) : buildFallbackMinimalUser(r.author_id),
      title: r.title || undefined,
      content: r.content || '',
      mediaUrl: r.media_url ? resolveMediaUrl(r.media_url) : undefined,
      postKind: r.post_type === 'ANNOUNCEMENT' ? 'ANNOUNCEMENT' : 'POST',
      isPinned: Boolean(r.is_pinned),
      likesCount: Number(r.likes_count || 0),
      commentsCount: Number(r.comments_count || 0),
      createdAtIso: r.created_at || new Date().toISOString(),
      timestamp: formatRelativeTime(r.created_at)
    }));
  } catch {
    return [];
  }
}

export async function createCirclePostInSupabase(params: {
  communityId: string;
  authorId: string;
  title?: string;
  content: string;
  postKind?: 'DISCUSSION' | 'ANNOUNCEMENT' | 'RESOURCE';
  mediaUrl?: string;
}): Promise<{ ok: boolean; post?: CirclePost; error?: string }> {
  try {
    let res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/community_posts?select=id,community_id,author_id,title,content,media_url,post_type,is_pinned,likes_count,comments_count,created_at,profiles:author_id(${STORY_PROFILE_COLUMNS})`,
      {
        method: 'POST',
        body: JSON.stringify({
          community_id: params.communityId,
          author_id: params.authorId,
          title: (params.title || 'Circle Update').trim(),
          content: params.content.trim(),
          post_type: params.postKind || 'DISCUSSION',
          media_url: params.mediaUrl || null
        })
      },
      { Prefer: 'return=representation' }
    );
    if (res.status === 400) {
      res = await authedFetch(
        `${SUPABASE_URL}/rest/v1/community_posts?select=id,community_id,author_id,title,content,media_url,likes_count,comments_count,created_at,profiles:author_id(${STORY_PROFILE_COLUMNS})`,
        {
          method: 'POST',
          body: JSON.stringify({
            community_id: params.communityId,
            author_id: params.authorId,
            title: (params.title || 'Circle Update').trim(),
            content: params.content.trim(),
            media_url: params.mediaUrl || null
          })
        },
        { Prefer: 'return=representation' }
      );
    }
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      return { ok: false, error: err?.message || 'Failed to publish Circle post.' };
    }
    const rows = await res.json();
    const r = Array.isArray(rows) ? rows[0] : rows;
    return {
      ok: true,
      post: {
        id: r.id,
        communityId: r.community_id,
        author: r.profiles ? mapSupabaseProfileToUser(r.profiles) : buildFallbackMinimalUser(params.authorId),
        title: r.title,
        content: r.content,
        mediaUrl: r.media_url ? resolveMediaUrl(r.media_url) : undefined,
        postKind: r.post_type === 'ANNOUNCEMENT' ? 'ANNOUNCEMENT' : 'POST',
        isPinned: Boolean(r.is_pinned),
        likesCount: 0,
        commentsCount: 0,
        createdAtIso: r.created_at || new Date().toISOString(),
        timestamp: 'Just now'
      }
    };
  } catch (err: any) {
    return { ok: false, error: err?.message || 'Network error.' };
  }
}

export async function toggleCirclePostPinRpc(postId: string, pinned: boolean): Promise<boolean> {
  try {
    const res = await authedFetch(`${SUPABASE_URL}/rest/v1/rpc/toggle_circle_post_pin`, {
      method: 'POST',
      body: JSON.stringify({ p_post_id: postId, p_pinned: pinned })
    });
    return res.ok;
  } catch {
    return false;
  }
}

export async function fetchCirclePolls(communityId: string, myUserId?: string): Promise<CirclePoll[]> {
  if (!UUID_REGEX.test(communityId)) return [];
  try {
    const hdrs = await getOptionalRestHeaders(false);
    const res = await fetch(
      `${SUPABASE_URL}/rest/v1/community_polls?community_id=eq.${encodeURIComponent(communityId)}&select=id,community_id,creator_id,question,options_json,allow_multiple,closes_at,created_at,profiles:creator_id(${STORY_PROFILE_COLUMNS}),community_poll_votes(user_id,option_index)&order=created_at.desc&limit=25`,
      { headers: hdrs }
    );
    if (!res.ok) return [];
    const rows = await res.json();
    if (!Array.isArray(rows)) return [];
    return rows.map((p: any) => {
      const optsRaw: string[] = Array.isArray(p.options_json) ? p.options_json : [];
      const votes: Array<{ user_id: string; option_index: number }> = Array.isArray(p.community_poll_votes)
        ? p.community_poll_votes
        : [];
      const myVoteOptionIds = myUserId
        ? votes.filter(v => v.user_id === myUserId).map(v => String(v.option_index))
        : [];
      return {
        id: p.id,
        communityId: p.community_id,
        creator: p.profiles ? mapSupabaseProfileToUser(p.profiles) : buildFallbackMinimalUser(p.creator_id),
        question: p.question || '',
        options: optsRaw.map((label, idx) => ({
          id: String(idx),
          label: typeof label === 'string' ? label : (label as any)?.label || `Option ${idx + 1}`,
          votesCount: votes.filter(v => Number(v.option_index) === idx).length
        })),
        allowMultiple: Boolean(p.allow_multiple),
        endsAtIso: p.closes_at || undefined,
        myVoteOptionIds,
        createdAtIso: p.created_at || new Date().toISOString()
      };
    });
  } catch {
    return [];
  }
}

export async function createCirclePollInSupabase(params: {
  communityId: string;
  creatorId: string;
  question: string;
  options: string[];
}): Promise<boolean> {
  try {
    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/community_polls`,
      {
        method: 'POST',
        body: JSON.stringify({
          community_id: params.communityId,
          creator_id: params.creatorId,
          question: params.question.trim(),
          options_json: params.options.map(o => o.trim()).filter(Boolean)
        })
      },
      { Prefer: 'return=minimal' }
    );
    return res.ok;
  } catch {
    return false;
  }
}

export async function voteCirclePollInSupabase(params: {
  pollId: string;
  userId: string;
  optionIndex: number;
}): Promise<boolean> {
  try {
    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/community_poll_votes?on_conflict=poll_id,user_id`,
      {
        method: 'POST',
        body: JSON.stringify({
          poll_id: params.pollId,
          user_id: params.userId,
          option_index: params.optionIndex
        })
      },
      { Prefer: 'resolution=merge-duplicates,return=minimal' }
    );
    return res.ok;
  } catch {
    return false;
  }
}

export async function fetchCircleEvents(communityId: string, myUserId?: string): Promise<CircleEvent[]> {
  if (!UUID_REGEX.test(communityId)) return [];
  try {
    const hdrs = await getOptionalRestHeaders(false);
    const res = await fetch(
      `${SUPABASE_URL}/rest/v1/community_events?community_id=eq.${encodeURIComponent(communityId)}&select=id,community_id,creator_id,title,description,event_type,location_or_link,starts_at,community_event_rsvps(user_id,status),profiles:creator_id(${STORY_PROFILE_COLUMNS})&order=starts_at.asc&limit=30`,
      { headers: hdrs }
    );
    if (!res.ok) return [];
    const rows = await res.json();
    if (!Array.isArray(rows)) return [];
    return rows.map((e: any) => {
      const rsvps: Array<{ user_id: string; status: string }> = Array.isArray(e.community_event_rsvps)
        ? e.community_event_rsvps
        : [];
      const myRsvpRow = myUserId ? rsvps.find(r => r.user_id === myUserId) : undefined;
      return {
        id: e.id,
        communityId: e.community_id,
        creator: e.profiles ? mapSupabaseProfileToUser(e.profiles) : buildFallbackMinimalUser(e.creator_id),
        title: e.title || 'Circle Event',
        description: e.description || '',
        eventType: e.event_type === 'IN_PERSON' ? 'IN_PERSON' : 'ONLINE',
        locationOrLink: e.location_or_link || '',
        startsAtIso: e.starts_at || new Date().toISOString(),
        goingCount: rsvps.filter(r => r.status === 'GOING').length,
        interestedCount: rsvps.filter(r => r.status === 'INTERESTED').length,
        myRsvp: myRsvpRow
          ? myRsvpRow.status === 'GOING'
            ? 'GOING'
            : myRsvpRow.status === 'INTERESTED'
              ? 'INTERESTED'
              : 'NOT_GOING'
          : undefined
      };
    });
  } catch {
    return [];
  }
}

export async function createCircleEventInSupabase(params: {
  communityId: string;
  creatorId: string;
  title: string;
  description: string;
  eventType: 'ONLINE' | 'IN_PERSON';
  locationOrLink: string;
  startsAtIso: string;
}): Promise<boolean> {
  try {
    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/community_events`,
      {
        method: 'POST',
        body: JSON.stringify({
          community_id: params.communityId,
          creator_id: params.creatorId,
          title: params.title.trim(),
          description: params.description.trim(),
          event_type: params.eventType,
          location_or_link: params.locationOrLink.trim(),
          starts_at: params.startsAtIso
        })
      },
      { Prefer: 'return=minimal' }
    );
    return res.ok;
  } catch {
    return false;
  }
}

export async function rsvpCircleEventInSupabase(params: {
  eventId: string;
  userId: string;
  status: 'GOING' | 'INTERESTED' | 'CANCELLED';
}): Promise<boolean> {
  try {
    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/community_event_rsvps?on_conflict=event_id,user_id`,
      {
        method: 'POST',
        body: JSON.stringify({
          event_id: params.eventId,
          user_id: params.userId,
          status: params.status
        })
      },
      { Prefer: 'resolution=merge-duplicates,return=minimal' }
    );
    return res.ok;
  } catch {
    return false;
  }
}

export async function fetchCircleResources(communityId: string): Promise<CircleResource[]> {
  if (!UUID_REGEX.test(communityId)) return [];
  try {
    const hdrs = await getOptionalRestHeaders(false);
    const res = await fetch(
      `${SUPABASE_URL}/rest/v1/community_resources?community_id=eq.${encodeURIComponent(communityId)}&select=id,community_id,creator_id,title,description,resource_url,resource_type,created_at,profiles:creator_id(${STORY_PROFILE_COLUMNS})&order=created_at.desc&limit=40`,
      { headers: hdrs }
    );
    if (!res.ok) return [];
    const rows = await res.json();
    if (!Array.isArray(rows)) return [];
    return rows.map((r: any) => ({
      id: r.id,
      communityId: r.community_id,
      uploader: r.profiles ? mapSupabaseProfileToUser(r.profiles) : buildFallbackMinimalUser(r.creator_id),
      title: r.title || 'Resource',
      description: r.description || '',
      resourceType: (r.resource_type === 'FILE' || r.resource_type === 'GUIDE' ? r.resource_type : 'LINK') as any,
      resourceUrl: resolveMediaUrl(r.resource_url || ''),
      createdAtIso: r.created_at || new Date().toISOString()
    }));
  } catch {
    return [];
  }
}

export async function addCircleResourceInSupabase(params: {
  communityId: string;
  creatorId: string;
  title: string;
  description: string;
  resourceType: 'LINK' | 'FILE' | 'GUIDE';
  resourceUrl: string;
}): Promise<boolean> {
  try {
    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/community_resources`,
      {
        method: 'POST',
        body: JSON.stringify({
          community_id: params.communityId,
          creator_id: params.creatorId,
          title: params.title.trim(),
          description: params.description.trim(),
          resource_type: params.resourceType,
          resource_url: params.resourceUrl.trim()
        })
      },
      { Prefer: 'return=minimal' }
    );
    return res.ok;
  } catch {
    return false;
  }
}

export async function openCircleChatRpc(communityId: string): Promise<{ conversationId: string | null; error?: string }> {
  try {
    const res = await authedFetch(`${SUPABASE_URL}/rest/v1/rpc/get_or_create_circle_chat`, {
      method: 'POST',
      body: JSON.stringify({ p_community_id: communityId })
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      return { conversationId: null, error: err?.message || `Unable to open Circle chat (${res.status}).` };
    }
    const raw = (await res.text()).trim().replace(/^"|"$/g, '');
    return UUID_REGEX.test(raw) ? { conversationId: raw } : { conversationId: null, error: 'Invalid conversation ID.' };
  } catch (err: any) {
    return { conversationId: null, error: err?.message || 'Network error opening Circle chat.' };
  }
}

export async function submitCircleReportInSupabase(params: {
  communityId: string;
  reporterId: string;
  targetType: 'POST' | 'MEMBER' | 'CIRCLE';
  targetId: string;
  reason: string;
}): Promise<{ ok: boolean; error?: string }> {
  try {
    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/community_reports`,
      {
        method: 'POST',
        body: JSON.stringify({
          community_id: params.communityId,
          reporter_id: params.reporterId,
          target_type: params.targetType,
          target_id: params.targetId,
          reason: params.reason.trim().slice(0, 400)
        })
      },
      { Prefer: 'return=minimal' }
    );
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      return { ok: false, error: err?.message || 'Could not submit report.' };
    }
    return { ok: true };
  } catch (err: any) {
    return { ok: false, error: err?.message || 'Network error.' };
  }
}
