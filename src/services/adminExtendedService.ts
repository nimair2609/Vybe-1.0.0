import {
  AdminDashboardMetrics,
  AdminStaffMember,
  AdminStaffRole,
  AudioTrack,
  DEFAULT_PLATFORM_CONFIG,
  PlatformConfigState
} from '../types';
import {
  SUPABASE_URL,
  UUID_REGEX,
  getAdminAuthHeaders,
  recordAdminAuditLogToSupabase,
  resolveMediaUrl
} from './supabaseService';

function getApiBase(): string {
  if (typeof window !== 'undefined' && window.location?.origin) {
    return `${window.location.origin}/api/v1`;
  }
  return '/api/v1';
}

export async function fetchAdminIdentity(): Promise<{
  ok: boolean;
  userId?: string;
  role?: AdminStaffRole;
  isOwner?: boolean;
}> {
  const auth = getAdminAuthHeaders();
  if (!auth) return { ok: false };
  try {
    const apiRes = await fetch(`${getApiBase()}/admin/me`, {
      headers: { Authorization: `Bearer ${auth.token}` }
    }).catch(() => null);
    if (apiRes && apiRes.ok) {
      const json = await apiRes.json();
      const role = String(json?.role || 'ADMIN').toUpperCase() as AdminStaffRole;
      return { ok: true, userId: json?.userId || auth.adminId, role, isOwner: Boolean(json?.isOwner || role === 'OWNER_ADMIN') };
    }
  } catch {
    // fallback to Supabase RPC
  }
  try {
    const res = await fetch(
      `${SUPABASE_URL}/rest/v1/admin_users?user_id=eq.${encodeURIComponent(auth.adminId)}&select=user_id,role,is_active&limit=1`,
      { headers: auth.headers }
    );
    if (!res.ok) return { ok: false };
    const rows = await res.json();
    const row = Array.isArray(rows) ? rows[0] : rows;
    if (!row || row.is_active !== true) return { ok: false };
    const role = String(row.role || 'ADMIN').toUpperCase() as AdminStaffRole;
    return { ok: true, userId: row.user_id, role, isOwner: role === 'OWNER_ADMIN' };
  } catch {
    return { ok: false };
  }
}

export async function fetchAdminDashboardMetrics(): Promise<AdminDashboardMetrics> {
  const empty: AdminDashboardMetrics = {
    totalUsers: 0,
    dauUsers: 0,
    wauUsers: 0,
    mauUsers: 0,
    newUsers24h: 0,
    suspendedUsers: 0,
    bannedUsers: 0,
    totalPosts: 0,
    totalStories: 0,
    totalComments: 0,
    totalLikes: 0,
    totalMessages: 0,
    totalCircles: 0,
    totalOpportunities: 0,
    pendingReports: 0,
    pendingVerifications: 0
  };
  const auth = getAdminAuthHeaders();
  if (!auth) return empty;
  try {
    const rpcRes = await fetch(`${SUPABASE_URL}/rest/v1/rpc/get_admin_dashboard_metrics`, {
      method: 'POST',
      headers: auth.headers,
      body: '{}'
    });
    if (rpcRes.ok) {
      const d = await rpcRes.json();
      return {
        totalUsers: Number(d?.total_users || 0),
        dauUsers: Number(d?.dau_users || 0),
        wauUsers: Number(d?.wau_users || 0),
        mauUsers: Number(d?.mau_users || 0),
        newUsers24h: Number(d?.new_users_24h || 0),
        suspendedUsers: Number(d?.suspended_users || 0),
        bannedUsers: Number(d?.banned_users || 0),
        totalPosts: Number(d?.total_posts || 0),
        totalStories: Number(d?.total_stories || 0),
        totalComments: Number(d?.total_comments || 0),
        totalLikes: Number(d?.total_likes || 0),
        totalMessages: Number(d?.total_messages || 0),
        totalCircles: Number(d?.total_circles || 0),
        totalOpportunities: Number(d?.total_opportunities || 0),
        pendingReports: Number(d?.pending_reports || 0),
        pendingVerifications: Number(d?.pending_verifications || 0)
      };
    }
  } catch {
    // fallback to gateway
  }
  try {
    const res = await fetch(`${getApiBase()}/admin/overview-stats`, {
      headers: { Authorization: `Bearer ${auth.token}` }
    });
    if (!res.ok) return empty;
    const d = await res.json();
    return {
      ...empty,
      totalUsers: Number(d?.total_users || d?.usersCount || 0),
      totalPosts: Number(d?.total_posts || d?.postsCount || 0),
      pendingReports: Number(d?.pending_reports || d?.pendingReportsCount || 0),
      pendingVerifications: Number(d?.pending_verifications || d?.pendingVerificationsCount || 0)
    };
  } catch {
    return empty;
  }
}

export async function fetchAdminStaffDirectory(): Promise<AdminStaffMember[]> {
  const auth = getAdminAuthHeaders();
  if (!auth) return [];
  try {
    const res = await fetch(
      `${SUPABASE_URL}/rest/v1/admin_users?select=user_id,role,is_active,created_at,profiles:user_id(username,display_name,avatar_url)&order=created_at.asc&limit=100`,
      { headers: auth.headers }
    );
    if (!res.ok) return [];
    const rows = await res.json();
    if (!Array.isArray(rows)) return [];
    return rows.map((r: any) => ({
      userId: r.user_id,
      role: String(r.role || 'ADMIN').toUpperCase() as AdminStaffRole,
      isActive: Boolean(r.is_active),
      username: r.profiles?.username || undefined,
      displayName: r.profiles?.display_name || r.profiles?.username || 'VYBE Staff',
      avatarUrl: r.profiles?.avatar_url ? resolveMediaUrl(r.profiles.avatar_url) : undefined,
      createdAtIso: r.created_at
    }));
  } catch {
    return [];
  }
}

export async function assignAdminStaffRole(params: {
  targetUserId: string;
  role: AdminStaffRole;
  isActive: boolean;
}): Promise<{ ok: boolean; error?: string }> {
  const auth = getAdminAuthHeaders();
  if (!auth) return { ok: false, error: 'Admin session required.' };
  if (!UUID_REGEX.test(params.targetUserId)) return { ok: false, error: 'Valid user UUID required.' };

  try {
    const gwRes = await fetch(`${getApiBase()}/admin/set-role`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${auth.token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(params)
    }).catch(() => null);
    if (gwRes && gwRes.ok) return { ok: true };
    if (gwRes && gwRes.status === 403) {
      const err = await gwRes.json().catch(() => ({}));
      return { ok: false, error: err?.error || 'Owner Admin permission required.' };
    }
  } catch {
    // fallback directly to Supabase RLS-protected admin_users
  }

  try {
    const res = await fetch(`${SUPABASE_URL}/rest/v1/admin_users?on_conflict=user_id`, {
      method: 'POST',
      headers: { ...auth.headers, Prefer: 'resolution=merge-duplicates,return=minimal' },
      body: JSON.stringify({
        user_id: params.targetUserId,
        role: params.role,
        is_active: params.isActive
      })
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      return { ok: false, error: err?.message || 'Only Owner Admin can modify admin staff roles.' };
    }
    await recordAdminAuditLogToSupabase({
      action: 'ADMIN_ROLE_UPDATED',
      targetType: 'admin_users',
      targetId: params.targetUserId,
      details: params
    });
    return { ok: true };
  } catch (err: any) {
    return { ok: false, error: err?.message || 'Network error assigning role.' };
  }
}

export async function fetchAdminCirclesList(): Promise<any[]> {
  const auth = getAdminAuthHeaders();
  if (!auth) return [];
  try {
    const res = await fetch(
      `${SUPABASE_URL}/rest/v1/communities?select=id,name,category,join_mode,members_count,is_verified,is_archived,created_at&order=created_at.desc&limit=80`,
      { headers: auth.headers }
    );
    return res.ok ? await res.json() : [];
  } catch {
    return [];
  }
}

export async function moderateAdminCircle(params: {
  circleId: string;
  action: 'VERIFY' | 'UNVERIFY' | 'ARCHIVE' | 'RESTORE' | 'DELETE';
}): Promise<{ ok: boolean; error?: string }> {
  const auth = getAdminAuthHeaders();
  if (!auth) return { ok: false, error: 'Admin session required.' };
  try {
    const url = `${SUPABASE_URL}/rest/v1/communities?id=eq.${encodeURIComponent(params.circleId)}`;
    const isDelete = params.action === 'DELETE';
    const patch =
      params.action === 'VERIFY'
        ? { is_verified: true }
        : params.action === 'UNVERIFY'
          ? { is_verified: false }
          : params.action === 'ARCHIVE'
            ? { is_archived: true }
            : { is_archived: false };
    const res = await fetch(url, {
      method: isDelete ? 'DELETE' : 'PATCH',
      headers: { ...auth.headers, Prefer: 'return=minimal' },
      ...(isDelete ? {} : { body: JSON.stringify(patch) })
    });
    if (!res.ok) return { ok: false, error: `Circle action failed (${res.status}).` };
    await recordAdminAuditLogToSupabase({
      action: `CIRCLE_${params.action}`,
      targetType: 'communities',
      targetId: params.circleId
    });
    return { ok: true };
  } catch (err: any) {
    return { ok: false, error: err?.message || 'Network error.' };
  }
}

export async function fetchAdminGrowEntities(): Promise<{
  programs: any[];
  projects: any[];
  opportunities: any[];
}> {
  const auth = getAdminAuthHeaders();
  if (!auth) return { programs: [], projects: [], opportunities: [] };
  try {
    const [progRes, projRes, oppRes] = await Promise.all([
      fetch(`${SUPABASE_URL}/rest/v1/growth_programs?select=id,title,category,members_count,is_published,created_at&order=created_at.desc&limit=40`, { headers: auth.headers }),
      fetch(`${SUPABASE_URL}/rest/v1/projects?select=id,title,category,likes_count,created_at&order=created_at.desc&limit=40`, { headers: auth.headers }),
      fetch(`${SUPABASE_URL}/rest/v1/opportunities?select=id,title,organization_name,category,status,is_verified_org,created_at&order=created_at.desc&limit=40`, { headers: auth.headers })
    ]);
    return {
      programs: progRes.ok ? await progRes.json() : [],
      projects: projRes.ok ? await projRes.json() : [],
      opportunities: oppRes.ok ? await oppRes.json() : []
    };
  } catch {
    return { programs: [], projects: [], opportunities: [] };
  }
}

export async function moderateAdminGrowEntity(params: {
  entityType: 'PROGRAM' | 'PROJECT' | 'OPPORTUNITY';
  entityId: string;
  action: 'CLOSE' | 'DELETE' | 'VERIFY_ORG' | 'UNPUBLISH' | 'PUBLISH';
}): Promise<{ ok: boolean; error?: string }> {
  const auth = getAdminAuthHeaders();
  if (!auth) return { ok: false, error: 'Admin session required.' };
  const table =
    params.entityType === 'PROGRAM'
      ? 'growth_programs'
      : params.entityType === 'PROJECT'
        ? 'projects'
        : 'opportunities';
  try {
    const url = `${SUPABASE_URL}/rest/v1/${table}?id=eq.${encodeURIComponent(params.entityId)}`;
    const isDelete = params.action === 'DELETE';
    const patch =
      params.entityType === 'PROGRAM'
        ? { is_published: params.action === 'PUBLISH' }
        : params.entityType === 'OPPORTUNITY'
          ? params.action === 'CLOSE'
            ? { status: 'CLOSED' }
            : { is_verified_org: params.action === 'VERIFY_ORG' }
          : {};
    const res = await fetch(url, {
      method: isDelete ? 'DELETE' : 'PATCH',
      headers: { ...auth.headers, Prefer: 'return=minimal' },
      ...(isDelete ? {} : { body: JSON.stringify(patch) })
    });
    if (!res.ok) return { ok: false, error: `Moderation failed (${res.status}).` };
    await recordAdminAuditLogToSupabase({
      action: `GROW_${params.entityType}_${params.action}`,
      targetType: table,
      targetId: params.entityId
    });
    return { ok: true };
  } catch (err: any) {
    return { ok: false, error: err?.message || 'Network error.' };
  }
}

export async function fetchAdminAudioTracks(): Promise<AudioTrack[]> {
  const auth = getAdminAuthHeaders();
  if (!auth) return [];
  try {
    const res = await fetch(
      `${SUPABASE_URL}/rest/v1/audio_tracks?select=id,title,artist,album,genre,duration_sec,stream_url,cover_url,is_active&order=created_at.desc&limit=80`,
      { headers: auth.headers }
    );
    if (!res.ok) return [];
    const rows = await res.json();
    return Array.isArray(rows)
      ? rows.map((r: any) => ({
          id: r.id,
          title: r.title,
          artist: r.artist,
          album: r.album || '',
          genre: r.genre || 'Ambient',
          durationSec: Number(r.duration_sec || 30),
          audioUrl: r.stream_url,
          coverUrl: r.cover_url || undefined,
          isActive: Boolean(r.is_active)
        }))
      : [];
  } catch {
    return [];
  }
}

export async function createAdminAudioTrack(track: {
  title: string;
  artist: string;
  genre: string;
  durationSec: number;
  streamUrl: string;
  coverUrl?: string;
}): Promise<{ ok: boolean; error?: string }> {
  const auth = getAdminAuthHeaders();
  if (!auth) return { ok: false, error: 'Admin session required.' };
  try {
    const res = await fetch(`${SUPABASE_URL}/rest/v1/audio_tracks`, {
      method: 'POST',
      headers: { ...auth.headers, Prefer: 'return=minimal' },
      body: JSON.stringify({
        title: track.title.trim(),
        artist: track.artist.trim(),
        genre: track.genre.trim() || 'Ambient',
        duration_sec: Math.max(5, Math.min(600, track.durationSec || 30)),
        stream_url: track.streamUrl.trim(),
        cover_url: track.coverUrl?.trim() || null,
        is_active: true,
        created_by: UUID_REGEX.test(auth.adminId) ? auth.adminId : null
      })
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      return { ok: false, error: err?.message || 'Failed to add audio track.' };
    }
    await recordAdminAuditLogToSupabase({
      action: 'AUDIO_TRACK_CREATED',
      targetType: 'audio_tracks',
      targetId: track.title
    });
    return { ok: true };
  } catch (err: any) {
    return { ok: false, error: err?.message || 'Network error.' };
  }
}

export async function deleteAdminAudioTrack(trackId: string): Promise<boolean> {
  const auth = getAdminAuthHeaders();
  if (!auth || !UUID_REGEX.test(trackId)) return false;
  try {
    const res = await fetch(`${SUPABASE_URL}/rest/v1/audio_tracks?id=eq.${encodeURIComponent(trackId)}`, {
      method: 'DELETE',
      headers: auth.headers
    });
    if (res.ok) {
      await recordAdminAuditLogToSupabase({
        action: 'AUDIO_TRACK_DELETED',
        targetType: 'audio_tracks',
        targetId: trackId
      });
    }
    return res.ok;
  } catch {
    return false;
  }
}

export async function saveAdminPlatformConfig(config: PlatformConfigState): Promise<{ ok: boolean; error?: string }> {
  const auth = getAdminAuthHeaders();
  if (!auth) return { ok: false, error: 'Admin session required.' };
  try {
    const res = await fetch(`${SUPABASE_URL}/rest/v1/platform_config?on_conflict=id`, {
      method: 'POST',
      headers: { ...auth.headers, Prefer: 'resolution=merge-duplicates,return=minimal' },
      body: JSON.stringify({
        id: 1,
        stories_enabled: config.storiesEnabled,
        circles_enabled: config.circlesEnabled,
        grow_enabled: config.growEnabled,
        opportunities_enabled: config.opportunitiesEnabled,
        direct_messages_enabled: config.directMessagesEnabled,
        voice_rooms_enabled: config.voiceRoomsEnabled,
        crossed_paths_enabled: config.crossedPathsEnabled,
        time_capsules_enabled: config.timeCapsulesEnabled,
        maintenance_mode: config.maintenanceMode,
        maintenance_banner: config.maintenanceBanner,
        updated_by: UUID_REGEX.test(auth.adminId) ? auth.adminId : null,
        updated_at: new Date().toISOString()
      })
    });
    if (!res.ok) return { ok: false, error: `Could not save config (${res.status}).` };
    await recordAdminAuditLogToSupabase({
      action: 'PLATFORM_CONFIG_UPDATED',
      targetType: 'platform_config',
      targetId: '1',
      details: config
    });
    return { ok: true };
  } catch (err: any) {
    return { ok: false, error: err?.message || 'Network error.' };
  }
}

export function getDefaultPlatformConfig(): PlatformConfigState {
  return { ...DEFAULT_PLATFORM_CONFIG };
}
