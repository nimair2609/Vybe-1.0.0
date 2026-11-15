/**
 * VYBE Netlify Serverless Function: Privileged Admin, Platform Config & Music Catalog API
 * Route parity with backend/src/server.ts & backend/serve_web.js using stateless Supabase storage.
 */

interface NetlifyEvent {
  httpMethod: string;
  path: string;
  headers: Record<string, string>;
  body: string | null;
  queryStringParameters?: Record<string, string>;
}

interface NetlifyResponse {
  statusCode: number;
  headers?: Record<string, string>;
  body: string;
}

const SUPABASE_URL = (process.env.VITE_SUPABASE_URL || process.env.SUPABASE_URL || 'https://cppwadedazpbevkfrchg.supabase.co').replace(/\/$/, '');
const SUPABASE_ANON_KEY = process.env.VITE_SUPABASE_ANON_KEY || process.env.SUPABASE_ANON_KEY || 'sb_publishable_MI-UdOqfccHVZIHjQq9vrw_IjEzeR7H';
const SUPABASE_SERVICE_ROLE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY || '';

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization, apikey, x-admin-email, X-Vybe-Owner-Console',
  'Access-Control-Allow-Methods': 'GET, POST, PATCH, DELETE, OPTIONS',
  'Content-Type': 'application/json',
  'X-Content-Type-Options': 'nosniff',
  'X-Frame-Options': 'DENY',
  'Referrer-Policy': 'no-referrer'
};

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const ADMIN_ROLES = new Set(['ADMIN', 'SUPER_ADMIN', 'OWNER_ADMIN', 'MODERATOR', 'SUPPORT_ADMIN', 'ANALYST']);
const SUPER_ADMIN_ROLES = new Set(['SUPER_ADMIN', 'OWNER_ADMIN']);

const DEFAULT_FEATURE_FLAGS: Record<string, boolean> = {
  unload: true,
  unfiltered: true,
  what_next: true,
  time_capsule: true,
  double_take: true,
  crossed_paths: true,
  live_stream: true,
  voice_calls: true,
  growEcosystem: true,
  growthCircles: true,
  stories2: true,
  unloadRooms: true,
  crossedPaths: true,
  timeCapsules: true,
  spiritualMode: true
};

const DEFAULT_PLATFORM_CONFIG = {
  maintenanceMode: false,
  maintenanceMessage: "VYBE is undergoing scheduled performance upgrades. We'll be back shortly!",
  announcementBanner: 'Welcome to VYBE 2.0 — Meaningful connections, Circles & GROW.',
  announcementActive: false,
  announcementLink: '',
  communityGuidelinesUrl: 'https://vybeonline.netlify.app/community-guidelines'
};

function getServiceOrAnonHeaders(bearerJwt?: string): Record<string, string> {
  if (SUPABASE_SERVICE_ROLE_KEY) {
    return {
      apikey: SUPABASE_SERVICE_ROLE_KEY,
      Authorization: `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
      'Content-Type': 'application/json',
      Prefer: 'return=representation'
    };
  }
  if (bearerJwt && bearerJwt.startsWith('eyJ')) {
    return {
      apikey: SUPABASE_ANON_KEY,
      Authorization: `Bearer ${bearerJwt}`,
      'Content-Type': 'application/json',
      Prefer: 'return=representation'
    };
  }
  return {
    apikey: SUPABASE_ANON_KEY,
    ...(SUPABASE_ANON_KEY.startsWith('eyJ') ? { Authorization: `Bearer ${SUPABASE_ANON_KEY}` } : {}),
    'Content-Type': 'application/json'
  };
}

async function verifyCallerAdmin(authHeader: string | undefined): Promise<{
  authorized: boolean;
  userId?: string;
  email?: string;
  token?: string;
  role?: string;
  isSuperAdmin?: boolean;
  isOwner?: boolean;
  statusCode?: number;
  error?: string;
}> {
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return { authorized: false, statusCode: 401, error: 'Missing Bearer authentication token.' };
  }

  const token = authHeader.replace(/^Bearer\s+/i, '').trim();
  if (!token || !token.startsWith('eyJ')) {
    return { authorized: false, statusCode: 401, error: 'Invalid Bearer JWT token format.' };
  }

  try {
    const userRes = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
      headers: {
        apikey: SUPABASE_ANON_KEY,
        Authorization: `Bearer ${token}`
      }
    });

    if (!userRes.ok) {
      return { authorized: false, statusCode: 401, error: 'Supabase authentication failed or token expired.' };
    }

    const userData = await userRes.json();
    const userId = userData?.id;
    if (!userId) {
      return { authorized: false, statusCode: 401, error: 'User identity could not be verified.' };
    }

    const roleHeaders = getServiceOrAnonHeaders(token);
    const adminUserRes = await fetch(
      `${SUPABASE_URL}/rest/v1/admin_users?user_id=eq.${encodeURIComponent(userId)}&select=user_id,role,is_active&limit=1`,
      { headers: roleHeaders }
    );

    if (!adminUserRes.ok) {
      return { authorized: false, statusCode: 503, error: 'Unable to verify administrator role against Supabase.' };
    }

    const aRows = await adminUserRes.json();
    const adminRec = Array.isArray(aRows) ? aRows[0] : aRows;
    const adminRole = String(adminRec?.role || '').toUpperCase().trim();
    const matchesUser = !adminRec?.user_id || String(adminRec.user_id).toLowerCase() === String(userId).toLowerCase();

    if (!adminRec || adminRec.is_active !== true || !matchesUser || !ADMIN_ROLES.has(adminRole)) {
      return { authorized: false, statusCode: 403, error: 'Forbidden: Active administrator record required.' };
    }

    const isSuper = SUPER_ADMIN_ROLES.has(adminRole);
    return {
      authorized: true,
      userId,
      email: typeof userData?.email === 'string' ? userData.email.toLowerCase() : 'admin',
      token,
      role: adminRole,
      isSuperAdmin: isSuper,
      isOwner: isSuper
    };
  } catch (err: any) {
    return { authorized: false, statusCode: 503, error: err.message || 'Unable to verify administrator authorization.' };
  }
}

async function recordNetlifyAuditLog(params: {
  dbHeaders: Record<string, string>;
  adminId: string;
  adminEmail?: string;
  action: string;
  targetType: string;
  targetId: string;
  details?: Record<string, any>;
}): Promise<void> {
  try {
    await fetch(`${SUPABASE_URL}/rest/v1/admin_audit_logs`, {
      method: 'POST',
      headers: { ...params.dbHeaders, Prefer: 'return=minimal' },
      body: JSON.stringify({
        admin_id: UUID_RE.test(params.adminId) ? params.adminId : null,
        admin_email: params.adminEmail || 'admin',
        action: params.action.slice(0, 100),
        target_type: params.targetType.slice(0, 50),
        target_id: params.targetId.slice(0, 100),
        details: params.details || {}
      })
    });
  } catch {}
}

async function loadPlatformConfigFromSupabase(dbHeaders: Record<string, string>) {
  try {
    const res = await fetch(`${SUPABASE_URL}/rest/v1/platform_config?id=eq.1&select=*&limit=1`, {
      headers: dbHeaders
    });
    if (res.ok) {
      const rows = await res.json();
      const row = Array.isArray(rows) ? rows[0] : rows;
      if (row) {
        return {
          featureFlags: { ...DEFAULT_FEATURE_FLAGS, ...(row.feature_flags || {}) },
          config: {
            maintenanceMode: Boolean(row.maintenance_mode),
            maintenanceMessage: row.maintenance_message || DEFAULT_PLATFORM_CONFIG.maintenanceMessage,
            announcementBanner: row.announcement_banner || DEFAULT_PLATFORM_CONFIG.announcementBanner,
            announcementActive: Boolean(row.announcement_active),
            announcementLink: row.announcement_link || '',
            communityGuidelinesUrl: row.community_guidelines_url || DEFAULT_PLATFORM_CONFIG.communityGuidelinesUrl
          }
        };
      }
    }
  } catch {}
  return {
    featureFlags: { ...DEFAULT_FEATURE_FLAGS },
    config: { ...DEFAULT_PLATFORM_CONFIG }
  };
}

function jsonResp(statusCode: number, payload: any): NetlifyResponse {
  return {
    statusCode,
    headers: CORS_HEADERS,
    body: JSON.stringify(payload)
  };
}

export async function handler(event: NetlifyEvent): Promise<NetlifyResponse> {
  if (event.httpMethod === 'OPTIONS') {
    return { statusCode: 204, headers: CORS_HEADERS, body: '' };
  }

  const urlPath = event.path.replace(/^\/\.netlify\/functions\/api/, '').replace(/^\/api/, '');
  const authHeader = event.headers.authorization || event.headers.Authorization;

  if (urlPath === '/v1/health' || urlPath === '/health') {
    return jsonResp(200, {
      status: 'healthy',
      service: 'VYBE Netlify Serverless API & Admin Gateway',
      environment: 'Netlify Serverless',
      timestamp: new Date().toISOString()
    });
  }

  // Public platform configuration endpoint
  if (urlPath === '/v1/platform-config' && event.httpMethod === 'GET') {
    const state = await loadPlatformConfigFromSupabase(getServiceOrAnonHeaders());
    return jsonResp(200, { success: true, config: state.config, featureFlags: state.featureFlags });
  }

  // Public active music catalog endpoint (backed by public.audio_tracks)
  if (urlPath === '/v1/music/catalog' && event.httpMethod === 'GET') {
    try {
      const res = await fetch(
        `${SUPABASE_URL}/rest/v1/audio_tracks?is_active=eq.true&select=id,title,artist,album,genre,duration_sec,stream_url,cover_url,is_active,created_at&order=created_at.desc&limit=200`,
        { headers: getServiceOrAnonHeaders() }
      );
      if (res.status === 404) {
        return jsonResp(200, { success: true, tracks: [] });
      }
      if (!res.ok) {
        return jsonResp(res.status, { error: 'Failed loading audio tracks catalog.' });
      }
      const tracks = await res.json().catch(() => []);
      return jsonResp(200, { success: true, tracks: Array.isArray(tracks) ? tracks : [] });
    } catch (err: any) {
      return jsonResp(500, { error: err.message });
    }
  }

  // All /v1/admin/* operations require verified admin JWT
  if (urlPath.startsWith('/v1/admin/')) {
    const authCheck = await verifyCallerAdmin(authHeader);
    if (!authCheck.authorized || !authCheck.userId || !authCheck.token) {
      return jsonResp(authCheck.statusCode || 401, { error: authCheck.error || 'Unauthorized' });
    }

    const dbHeaders = getServiceOrAnonHeaders(authCheck.token);

    // Identity check
    if (urlPath === '/v1/admin/me' && event.httpMethod === 'GET') {
      return jsonResp(200, {
        success: true,
        userId: authCheck.userId,
        email: authCheck.email,
        role: authCheck.role,
        isOwner: Boolean(authCheck.isOwner),
        isSuperAdmin: Boolean(authCheck.isSuperAdmin)
      });
    }

    // Overview stats via get_admin_dashboard_metrics RPC + fallback
    if (urlPath === '/v1/admin/overview-stats' && event.httpMethod === 'GET') {
      try {
        const rpcRes = await fetch(`${SUPABASE_URL}/rest/v1/rpc/get_admin_dashboard_metrics`, {
          method: 'POST',
          headers: dbHeaders,
          body: '{}'
        });
        if (rpcRes.ok) {
          const stats = await rpcRes.json();
          if (stats && typeof stats === 'object') {
            return jsonResp(200, {
              success: true,
              adminRole: authCheck.role,
              isSuperAdmin: Boolean(authCheck.isSuperAdmin),
              isOwner: Boolean(authCheck.isOwner),
              stats: {
                ...stats,
                push: { activeDevices: stats.push?.activeDevices || 0, recentPushes: 0 },
                systemHealth: {
                  supabase: { status: 'HEALTHY', url: SUPABASE_URL },
                  fcm: { status: process.env.FIREBASE_SERVICE_ACCOUNT ? 'CONFIGURED' : 'STANDBY', registeredDevices: stats.push?.activeDevices || 0 },
                  realtime: { status: 'LIVE', channel: 'vybe_broadcast_high_v2' }
                }
              }
            });
          }
        }
      } catch {}
      return jsonResp(200, {
        success: true,
        adminRole: authCheck.role,
        isSuperAdmin: Boolean(authCheck.isSuperAdmin),
        isOwner: Boolean(authCheck.isOwner),
        stats: {
          users: { total: 0, dau: 0, wau: 0, mau: 0, newToday: 0, newWeek: 0, banned: 0, suspended: 0, verified: 0, vip: 0, official: 0 },
          content: { totalPosts: 0, unfiltered: 0, standardPosts: 0, stories: 0, totalLikes: 0, totalComments: 0, totalSaves: 0, totalShares: 0 },
          moderation: { totalReports: 0, pendingReports: 0, verificationPending: 0 },
          ecosystem: { circles: 0, circlePosts: 0, growthPrograms: 0, projects: 0, opportunities: 0, applications: 0, audioTracks: 0 },
          push: { activeDevices: 0, recentPushes: 0 }
        }
      });
    }

    // Users list
    if (urlPath === '/v1/admin/users' && event.httpMethod === 'GET') {
      const resp = await fetch(
        `${SUPABASE_URL}/rest/v1/profiles?select=id,username,display_name,avatar_url,role,is_verified,is_vip,is_worker,is_verified_business,is_banned,is_suspended,suspended_until,created_at&order=created_at.desc&limit=100`,
        { headers: dbHeaders }
      );
      if (!resp.ok) return jsonResp(resp.status, { error: 'Failed loading users' });
      const rawUsers = await resp.json();
      const users = (Array.isArray(rawUsers) ? rawUsers : []).map((u: any) => ({
        ...u,
        badge_type: u.is_worker ? 'OFFICIAL' : u.is_vip ? 'VIP' : u.is_verified ? 'VERIFIED' : u.is_verified_business ? 'SHOP' : null
      }));
      return jsonResp(200, { success: true, users, total: users.length });
    }

    // Staff directory
    if (urlPath === '/v1/admin/staff' && event.httpMethod === 'GET') {
      const resp = await fetch(
        `${SUPABASE_URL}/rest/v1/admin_users?is_active=eq.true&select=user_id,email,role,is_active,created_at,profiles:user_id(id,username,display_name,avatar_url)&order=created_at.desc&limit=200`,
        { headers: dbHeaders }
      );
      if (!resp.ok) return jsonResp(resp.status, { error: 'Failed loading admin staff' });
      const rows = await resp.json();
      const staff = (Array.isArray(rows) ? rows : []).map((r: any) => {
        const p = r.profiles || {};
        const role = String(r.role || 'MODERATOR').toUpperCase();
        return {
          id: r.user_id || p.id,
          username: p.username || r.email || 'admin',
          display_name: p.display_name || p.username || r.email || 'Administrator',
          email: r.email || '',
          role,
          is_owner: SUPER_ADMIN_ROLES.has(role),
          avatar_url: p.avatar_url || null,
          created_at: r.created_at
        };
      });
      return jsonResp(200, { success: true, staff, total: staff.length });
    }

    // Owner-only role assignment
    if (urlPath === '/v1/admin/set-role' && event.httpMethod === 'POST') {
      if (!authCheck.isSuperAdmin) {
        return jsonResp(403, { error: 'Forbidden: Only the Platform Owner / Super Admin may modify staff roles.' });
      }
      const body = JSON.parse(event.body || '{}');
      const { userId, role, email } = body;
      if (!userId || !UUID_RE.test(String(userId)) || !role) {
        return jsonResp(400, { error: 'Valid UUID userId and role are required.' });
      }
      if (String(authCheck.userId).toLowerCase() === String(userId).toLowerCase()) {
        return jsonResp(403, { error: 'Security Policy: Administrators cannot modify their own role.' });
      }

      const requestedRole = String(role).toUpperCase().trim();
      const ADMIN_MAP: Record<string, { profileRole: string; subRole: string | null }> = {
        OWNER_ADMIN: { profileRole: 'ADMIN', subRole: 'OWNER_ADMIN' },
        SUPER_ADMIN: { profileRole: 'ADMIN', subRole: 'OWNER_ADMIN' },
        ADMIN: { profileRole: 'ADMIN', subRole: 'ADMIN' },
        MODERATOR: { profileRole: 'MODERATOR', subRole: 'MODERATOR' },
        SUPPORT_ADMIN: { profileRole: 'MODERATOR', subRole: 'SUPPORT_ADMIN' },
        ANALYST: { profileRole: 'MODERATOR', subRole: 'ANALYST' },
        USER: { profileRole: 'USER', subRole: null }
      };
      const mapped = ADMIN_MAP[requestedRole] || { profileRole: 'USER', subRole: null };

      const pRes = await fetch(`${SUPABASE_URL}/rest/v1/profiles?id=eq.${encodeURIComponent(userId)}`, {
        method: 'PATCH',
        headers: dbHeaders,
        body: JSON.stringify({ role: mapped.profileRole })
      });
      if (!pRes.ok) {
        return jsonResp(pRes.status, { error: 'Failed updating user role on profile.' });
      }

      if (mapped.subRole) {
        await fetch(`${SUPABASE_URL}/rest/v1/admin_users?on_conflict=user_id`, {
          method: 'POST',
          headers: { ...dbHeaders, Prefer: 'resolution=merge-duplicates,return=minimal' },
          body: JSON.stringify({
            user_id: userId,
            ...(email ? { email: String(email).toLowerCase() } : {}),
            role: mapped.subRole,
            is_active: true,
            assigned_by: authCheck.userId,
            updated_at: new Date().toISOString()
          })
        });
      } else {
        await fetch(`${SUPABASE_URL}/rest/v1/admin_users?user_id=eq.${encodeURIComponent(userId)}`, {
          method: 'PATCH',
          headers: dbHeaders,
          body: JSON.stringify({ is_active: false, updated_at: new Date().toISOString() })
        });
      }

      await recordNetlifyAuditLog({
        dbHeaders,
        adminId: authCheck.userId,
        adminEmail: authCheck.email,
        action: 'UPDATE_ROLE',
        targetType: 'profiles',
        targetId: String(userId),
        details: { requestedRole, profileRole: mapped.profileRole, adminSubRole: mapped.subRole }
      });

      return jsonResp(200, { success: true, userId, role: mapped.subRole || mapped.profileRole });
    }

    // Circles governance
    if (urlPath === '/v1/admin/circles' && event.httpMethod === 'GET') {
      const [cRes, rRes] = await Promise.all([
        fetch(
          `${SUPABASE_URL}/rest/v1/communities?select=id,name,description,category,join_mode,members_count,is_verified,created_at,creator:creator_id(id,username,display_name)&order=created_at.desc&limit=100`,
          { headers: dbHeaders }
        ).catch(() => null),
        fetch(
          `${SUPABASE_URL}/rest/v1/community_reports?select=id,community_id,reporter_id,reason,status,created_at&order=created_at.desc&limit=50`,
          { headers: dbHeaders }
        ).catch(() => null)
      ]);
      const circles = cRes && cRes.ok ? await cRes.json().catch(() => []) : [];
      const circleReports = rRes && rRes.ok ? await rRes.json().catch(() => []) : [];
      return jsonResp(200, {
        success: true,
        circles: Array.isArray(circles) ? circles : [],
        reports: Array.isArray(circleReports) ? circleReports : []
      });
    }

    if (urlPath === '/v1/admin/circles/moderate' && event.httpMethod === 'POST') {
      const body = JSON.parse(event.body || '{}');
      const { communityId, reportId, action } = body;
      const act = String(action || '').toUpperCase();

      if (act === 'RESOLVE_REPORT' && reportId && UUID_RE.test(String(reportId))) {
        const res = await fetch(`${SUPABASE_URL}/rest/v1/community_reports?id=eq.${encodeURIComponent(reportId)}`, {
          method: 'PATCH',
          headers: dbHeaders,
          body: JSON.stringify({ status: 'RESOLVED' })
        });
        await recordNetlifyAuditLog({
          dbHeaders,
          adminId: authCheck.userId,
          adminEmail: authCheck.email,
          action: 'CIRCLE_REPORT_RESOLVED',
          targetType: 'community_reports',
          targetId: String(reportId)
        });
        return jsonResp(res.ok ? 200 : res.status, { success: res.ok });
      }

      if (!communityId || !UUID_RE.test(String(communityId))) {
        return jsonResp(400, { error: 'Valid UUID communityId required.' });
      }

      if (act === 'DELETE') {
        const res = await fetch(`${SUPABASE_URL}/rest/v1/communities?id=eq.${encodeURIComponent(communityId)}`, {
          method: 'DELETE',
          headers: dbHeaders
        });
        await recordNetlifyAuditLog({
          dbHeaders,
          adminId: authCheck.userId,
          adminEmail: authCheck.email,
          action: 'CIRCLE_DELETE',
          targetType: 'communities',
          targetId: String(communityId)
        });
        return jsonResp(res.ok ? 200 : res.status, { success: res.ok });
      }

      if (act === 'VERIFY' || act === 'UNVERIFY') {
        const res = await fetch(`${SUPABASE_URL}/rest/v1/communities?id=eq.${encodeURIComponent(communityId)}`, {
          method: 'PATCH',
          headers: dbHeaders,
          body: JSON.stringify({ is_verified: act === 'VERIFY' })
        });
        await recordNetlifyAuditLog({
          dbHeaders,
          adminId: authCheck.userId,
          adminEmail: authCheck.email,
          action: `CIRCLE_${act}`,
          targetType: 'communities',
          targetId: String(communityId)
        });
        return jsonResp(res.ok ? 200 : res.status, { success: res.ok });
      }

      return jsonResp(400, { error: 'Unsupported Circle moderation action.' });
    }

    // GROW 2.0 moderation
    if (urlPath === '/v1/admin/grow' && event.httpMethod === 'GET') {
      const [progRes, projRes, oppRes, oppRepRes] = await Promise.all([
        fetch(
          `${SUPABASE_URL}/rest/v1/growth_programs?select=id,owner_id,title,category,duration_days,enrollment_mode,is_published,members_count,created_at&order=created_at.desc&limit=80`,
          { headers: dbHeaders }
        ).catch(() => null),
        fetch(
          `${SUPABASE_URL}/rest/v1/projects?select=id,owner_id,title,category,stage,likes_count,created_at&order=created_at.desc&limit=80`,
          { headers: dbHeaders }
        ).catch(() => null),
        fetch(
          `${SUPABASE_URL}/rest/v1/opportunities?select=id,posted_by,title,organization_name,category,work_mode,is_verified_org,status,created_at&order=created_at.desc&limit=80`,
          { headers: dbHeaders }
        ).catch(() => null),
        fetch(
          `${SUPABASE_URL}/rest/v1/opportunity_reports?select=id,opportunity_id,reporter_id,reason,status,created_at&order=created_at.desc&limit=50`,
          { headers: dbHeaders }
        ).catch(() => null)
      ]);
      return jsonResp(200, {
        success: true,
        programs: progRes && progRes.ok ? await progRes.json().catch(() => []) : [],
        projects: projRes && projRes.ok ? await projRes.json().catch(() => []) : [],
        opportunities: oppRes && oppRes.ok ? await oppRes.json().catch(() => []) : [],
        opportunityReports: oppRepRes && oppRepRes.ok ? await oppRepRes.json().catch(() => []) : []
      });
    }

    if (urlPath === '/v1/admin/grow/moderate' && event.httpMethod === 'POST') {
      const body = JSON.parse(event.body || '{}');
      const { entityType, entityId, action } = body;
      if (!entityId || !UUID_RE.test(String(entityId))) {
        return jsonResp(400, { error: 'Valid UUID entityId is required.' });
      }
      const kind = String(entityType || '').toUpperCase();
      const act = String(action || '').toUpperCase();

      if (kind === 'PROGRAM') {
        const res =
          act === 'DELETE'
            ? await fetch(`${SUPABASE_URL}/rest/v1/growth_programs?id=eq.${encodeURIComponent(entityId)}`, {
                method: 'DELETE',
                headers: dbHeaders
              })
            : await fetch(`${SUPABASE_URL}/rest/v1/growth_programs?id=eq.${encodeURIComponent(entityId)}`, {
                method: 'PATCH',
                headers: dbHeaders,
                body: JSON.stringify({ is_published: act === 'PUBLISH' })
              });
        await recordNetlifyAuditLog({
          dbHeaders,
          adminId: authCheck.userId,
          adminEmail: authCheck.email,
          action: `GROW_PROGRAM_${act}`,
          targetType: 'growth_programs',
          targetId: String(entityId)
        });
        return jsonResp(res.ok ? 200 : res.status, { success: res.ok });
      }

      if (kind === 'PROJECT' && act === 'DELETE') {
        const res = await fetch(`${SUPABASE_URL}/rest/v1/projects?id=eq.${encodeURIComponent(entityId)}`, {
          method: 'DELETE',
          headers: dbHeaders
        });
        await recordNetlifyAuditLog({
          dbHeaders,
          adminId: authCheck.userId,
          adminEmail: authCheck.email,
          action: 'GROW_PROJECT_DELETE',
          targetType: 'projects',
          targetId: String(entityId)
        });
        return jsonResp(res.ok ? 200 : res.status, { success: res.ok });
      }

      if (kind === 'OPPORTUNITY') {
        const res =
          act === 'DELETE'
            ? await fetch(`${SUPABASE_URL}/rest/v1/opportunities?id=eq.${encodeURIComponent(entityId)}`, {
                method: 'DELETE',
                headers: dbHeaders
              })
            : await fetch(`${SUPABASE_URL}/rest/v1/opportunities?id=eq.${encodeURIComponent(entityId)}`, {
                method: 'PATCH',
                headers: dbHeaders,
                body: JSON.stringify(
                  act === 'VERIFY'
                    ? { is_verified_org: true }
                    : { status: act === 'OPEN' ? 'OPEN' : 'CLOSED' }
                )
              });
        await recordNetlifyAuditLog({
          dbHeaders,
          adminId: authCheck.userId,
          adminEmail: authCheck.email,
          action: `GROW_OPP_${act}`,
          targetType: 'opportunities',
          targetId: String(entityId)
        });
        return jsonResp(res.ok ? 200 : res.status, { success: res.ok });
      }

      if (kind === 'OPPORTUNITY_REPORT') {
        const res = await fetch(`${SUPABASE_URL}/rest/v1/opportunity_reports?id=eq.${encodeURIComponent(entityId)}`, {
          method: 'PATCH',
          headers: dbHeaders,
          body: JSON.stringify({ status: act === 'DISMISS' ? 'DISMISSED' : 'RESOLVED' })
        });
        return jsonResp(res.ok ? 200 : res.status, { success: res.ok });
      }

      return jsonResp(400, { error: 'Unsupported GROW moderation target/action.' });
    }

    // Audio tracks management
    if (urlPath === '/v1/admin/audio-tracks' && event.httpMethod === 'GET') {
      const res = await fetch(
        `${SUPABASE_URL}/rest/v1/audio_tracks?select=id,title,artist,album,genre,duration_sec,stream_url,cover_url,is_active,created_at&order=created_at.desc&limit=200`,
        { headers: dbHeaders }
      );
      if (res.status === 404) return jsonResp(200, { success: true, tracks: [] });
      if (!res.ok) return jsonResp(res.status, { error: 'Failed loading audio tracks.' });
      const tracks = await res.json().catch(() => []);
      return jsonResp(200, { success: true, tracks: Array.isArray(tracks) ? tracks : [] });
    }

    if (urlPath === '/v1/admin/audio-tracks' && event.httpMethod === 'POST') {
      const body = JSON.parse(event.body || '{}');
      const { title, artist, album, genre, durationSec, streamUrl, coverUrl } = body;
      if (!title || !artist || !streamUrl) {
        return jsonResp(400, { error: 'title, artist, and streamUrl are required.' });
      }
      const res = await fetch(`${SUPABASE_URL}/rest/v1/audio_tracks`, {
        method: 'POST',
        headers: dbHeaders,
        body: JSON.stringify({
          title: String(title).trim(),
          artist: String(artist).trim(),
          album: String(album || '').trim(),
          genre: String(genre || 'Ambient').trim(),
          duration_sec: Math.max(5, Math.min(600, Number(durationSec) || 30)),
          stream_url: String(streamUrl).trim(),
          cover_url: coverUrl ? String(coverUrl).trim() : null,
          is_active: true,
          created_by: authCheck.userId
        })
      });
      if (!res.ok) return jsonResp(res.status, { error: 'Failed creating audio track.' });
      const created = await res.json();
      const track = Array.isArray(created) ? created[0] : created;
      await recordNetlifyAuditLog({
        dbHeaders,
        adminId: authCheck.userId,
        adminEmail: authCheck.email,
        action: 'AUDIO_TRACK_CREATE',
        targetType: 'audio_tracks',
        targetId: String(track?.id || title),
        details: { title, artist }
      });
      return jsonResp(200, { success: true, track });
    }

    if (urlPath === '/v1/admin/audio-tracks' && event.httpMethod === 'DELETE') {
      const id = event.queryStringParameters?.id || JSON.parse(event.body || '{}')?.trackId;
      if (!id || !UUID_RE.test(String(id))) {
        return jsonResp(400, { error: 'Valid UUID trackId is required.' });
      }
      const res = await fetch(`${SUPABASE_URL}/rest/v1/audio_tracks?id=eq.${encodeURIComponent(id)}`, {
        method: 'DELETE',
        headers: dbHeaders
      });
      await recordNetlifyAuditLog({
        dbHeaders,
        adminId: authCheck.userId,
        adminEmail: authCheck.email,
        action: 'AUDIO_TRACK_DELETE',
        targetType: 'audio_tracks',
        targetId: String(id)
      });
      return jsonResp(res.ok ? 200 : res.status, { success: res.ok });
    }

    // Platform config & experience toggles (persisted in Supabase public.platform_config)
    if (urlPath === '/v1/admin/platform-config' && event.httpMethod === 'GET') {
      const state = await loadPlatformConfigFromSupabase(dbHeaders);
      return jsonResp(200, { success: true, config: state.config, featureFlags: state.featureFlags });
    }

    if (urlPath === '/v1/admin/platform-config' && event.httpMethod === 'POST') {
      const body = JSON.parse(event.body || '{}');
      const current = await loadPlatformConfigFromSupabase(dbHeaders);
      const nextConfig = {
        maintenanceMode: body.maintenanceMode !== undefined ? Boolean(body.maintenanceMode) : current.config.maintenanceMode,
        maintenanceMessage: body.maintenanceMessage !== undefined ? String(body.maintenanceMessage) : current.config.maintenanceMessage,
        announcementBanner: body.announcementBanner !== undefined ? String(body.announcementBanner) : current.config.announcementBanner,
        announcementActive: body.announcementActive !== undefined ? Boolean(body.announcementActive) : current.config.announcementActive,
        announcementLink: body.announcementLink !== undefined ? String(body.announcementLink) : current.config.announcementLink,
        communityGuidelinesUrl: current.config.communityGuidelinesUrl
      };
      const nextFlags = body.featureFlags && typeof body.featureFlags === 'object'
        ? { ...current.featureFlags, ...body.featureFlags }
        : current.featureFlags;

      await fetch(`${SUPABASE_URL}/rest/v1/platform_config?on_conflict=id`, {
        method: 'POST',
        headers: { ...dbHeaders, Prefer: 'resolution=merge-duplicates,return=minimal' },
        body: JSON.stringify({
          id: 1,
          feature_flags: nextFlags,
          maintenance_mode: nextConfig.maintenanceMode,
          maintenance_message: nextConfig.maintenanceMessage,
          announcement_banner: nextConfig.announcementBanner,
          announcement_active: nextConfig.announcementActive,
          announcement_link: nextConfig.announcementLink,
          updated_by: authCheck.userId,
          updated_at: new Date().toISOString()
        })
      });

      await recordNetlifyAuditLog({
        dbHeaders,
        adminId: authCheck.userId,
        adminEmail: authCheck.email,
        action: 'UPDATE_PLATFORM_CONFIG',
        targetType: 'platform_config',
        targetId: 'GLOBAL',
        details: nextConfig
      });
      return jsonResp(200, { success: true, config: nextConfig, featureFlags: nextFlags });
    }

    if (urlPath === '/v1/admin/experiences/toggle' && event.httpMethod === 'POST') {
      const body = JSON.parse(event.body || '{}');
      const key = String(body.experienceKey || '').trim();
      if (!key) return jsonResp(400, { error: 'experienceKey required' });
      const current = await loadPlatformConfigFromSupabase(dbHeaders);
      const nextFlags = { ...current.featureFlags, [key]: Boolean(body.enabled) };

      await fetch(`${SUPABASE_URL}/rest/v1/platform_config?on_conflict=id`, {
        method: 'POST',
        headers: { ...dbHeaders, Prefer: 'resolution=merge-duplicates,return=minimal' },
        body: JSON.stringify({
          id: 1,
          feature_flags: nextFlags,
          updated_by: authCheck.userId,
          updated_at: new Date().toISOString()
        })
      });

      await recordNetlifyAuditLog({
        dbHeaders,
        adminId: authCheck.userId,
        adminEmail: authCheck.email,
        action: `EXPERIENCE_TOGGLE_${key.toUpperCase()}`,
        targetType: 'platform_config',
        targetId: key,
        details: { enabled: Boolean(body.enabled) }
      });

      return jsonResp(200, { success: true, experienceKey: key, enabled: Boolean(body.enabled), featureFlags: nextFlags });
    }

    // 1. Admin badge assignment via audited RPC
    if (urlPath === '/v1/admin/assign-badge' && event.httpMethod === 'POST') {
      try {
        const body = JSON.parse(event.body || '{}');
        const userId = body.userId;
        const badge = body.badge || body.badgeType;
        const enabled = body.enabled !== undefined ? body.enabled : body.value;
        if (!userId || !UUID_RE.test(String(userId)) || !badge) {
          return jsonResp(400, { error: 'Valid UUID userId and badge required' });
        }

        if (String(authCheck.userId).toLowerCase() === String(userId).toLowerCase()) {
          return jsonResp(403, { error: 'Forbidden: Self-assignment or self-revocation of badges is prohibited.' });
        }

        const badgeUpper = String(badge).toUpperCase();
        const badgeMap: Record<string, string> = {
          VERIFIED_BLUE: 'VERIFIED',
          VERIFIED: 'VERIFIED',
          VIP_GOLD: 'VIP',
          VIP: 'VIP',
          WORKER_GREEN: 'WORKER',
          WORKER: 'WORKER',
          OFFICIAL: 'WORKER',
          SHOP_GOLD: 'SHOP',
          SHOP: 'SHOP',
          BUSINESS: 'SHOP'
        };
        const rpcCategory = badgeMap[badgeUpper];
        if (!rpcCategory) {
          return jsonResp(400, { error: 'Unsupported badge value.' });
        }
        if (rpcCategory === 'WORKER' && !authCheck.isSuperAdmin) {
          return jsonResp(403, { error: 'Forbidden: WORKER badge requires Owner / Super Admin.' });
        }

        const rpcRes = await fetch(`${SUPABASE_URL}/rest/v1/rpc/admin_assign_badge`, {
          method: 'POST',
          headers: dbHeaders,
          body: JSON.stringify({
            p_target_user_id: userId,
            p_badge_category: rpcCategory,
            p_is_grant: Boolean(enabled),
            p_admin_id: authCheck.userId,
            p_vip_expires_at: typeof body.vipExpiresAt === 'string' && body.vipExpiresAt ? body.vipExpiresAt : null,
            p_reason: `Netlify Admin API ${Boolean(enabled) ? 'grant' : 'revoke'}`,
            p_require_acceptance: false,
            p_worker_mandatory: rpcCategory === 'WORKER'
          })
        });

        if (!rpcRes.ok) {
          const errText = await rpcRes.text().catch(() => '');
          return jsonResp(rpcRes.status, { error: 'Failed to update badge via audited RPC', details: errText.slice(0, 200) });
        }

        return jsonResp(200, { success: true, userId, badge, enabled: Boolean(enabled) });
      } catch (err: any) {
        return jsonResp(500, { error: err.message });
      }
    }

    // 2. Admin post moderation
    if (urlPath === '/v1/admin/content-action' && event.httpMethod === 'POST') {
      try {
        const body = JSON.parse(event.body || '{}');
        const targetPostId = body.postId || body.contentId;
        const action = String(body.action || '').toUpperCase();
        if (!targetPostId || !UUID_RE.test(String(targetPostId))) {
          return jsonResp(400, { error: 'Valid UUID postId required' });
        }

        if (action === 'DELETE' || action === 'REMOVE') {
          const delRes = await fetch(`${SUPABASE_URL}/rest/v1/posts?id=eq.${encodeURIComponent(targetPostId)}`, {
            method: 'DELETE',
            headers: dbHeaders
          });
          if (delRes.ok) {
            await recordNetlifyAuditLog({
              dbHeaders,
              adminId: authCheck.userId,
              adminEmail: authCheck.email,
              action: 'CONTENT_DELETE',
              targetType: 'POST',
              targetId: String(targetPostId),
              details: { action }
            });
          }
          return jsonResp(delRes.ok ? 200 : delRes.status, { success: delRes.ok, postId: targetPostId });
        }

        if (action === 'ARCHIVE' || action === 'HIDE' || action === 'RESTORE' || action === 'SHOW') {
          const isArchived = action === 'ARCHIVE' || action === 'HIDE';
          const patchRes = await fetch(`${SUPABASE_URL}/rest/v1/posts?id=eq.${encodeURIComponent(targetPostId)}`, {
            method: 'PATCH',
            headers: dbHeaders,
            body: JSON.stringify({ is_archived: isArchived })
          });
          if (patchRes.ok) {
            await recordNetlifyAuditLog({
              dbHeaders,
              adminId: authCheck.userId,
              adminEmail: authCheck.email,
              action: `CONTENT_${action}`,
              targetType: 'POST',
              targetId: String(targetPostId),
              details: { action }
            });
          }
          return jsonResp(patchRes.ok ? 200 : patchRes.status, { success: patchRes.ok, postId: targetPostId, action });
        }

        return jsonResp(400, { error: 'Unsupported content moderation action.' });
      } catch (err: any) {
        return jsonResp(500, { error: err.message });
      }
    }

    // 3. Admin broadcast push via Supabase push-dispatch Edge Function
    if (urlPath === '/v1/admin/broadcast-push' && event.httpMethod === 'POST') {
      try {
        const body = JSON.parse(event.body || '{}');
        const { title, message, body: msgBody, destination, targetId } = body;
        const resolvedMessage = message || msgBody;
        if (!title || !resolvedMessage) {
          return jsonResp(400, { error: 'title and message are required.' });
        }

        const edgeRes = await fetch(`${SUPABASE_URL}/functions/v1/push-dispatch`, {
          method: 'POST',
          headers: {
            apikey: SUPABASE_ANON_KEY,
            Authorization: `Bearer ${authCheck.token}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            broadcast: true,
            title,
            message: resolvedMessage,
            destination: destination || 'NOTIFICATIONS',
            targetId
          })
        });

        const edgePayload = await edgeRes.json().catch(() => ({}));
        if (!edgeRes.ok) {
          return jsonResp(edgeRes.status, { error: edgePayload?.error || 'Broadcast dispatch failed.' });
        }

        await recordNetlifyAuditLog({
          dbHeaders,
          adminId: authCheck.userId,
          adminEmail: authCheck.email,
          action: 'SYSTEM_BROADCAST',
          targetType: 'PLATFORM',
          targetId: 'ALL_USERS',
          details: { title, destination: destination || 'NOTIFICATIONS' }
        });

        return jsonResp(200, {
          success: true,
          ...edgePayload,
          deliveredAt: new Date().toISOString()
        });
      } catch (err: any) {
        return jsonResp(500, { error: err.message });
      }
    }

    // 4. Push token telemetry stats for Admin Console
    if (urlPath === '/v1/admin/push-stats' && event.httpMethod === 'GET') {
      try {
        const tokRes = await fetch(
          `${SUPABASE_URL}/rest/v1/user_device_tokens?is_active=eq.true&select=id&limit=1000`,
          { headers: dbHeaders }
        );
        const rows = tokRes.ok ? await tokRes.json().catch(() => []) : [];
        return jsonResp(200, {
          success: true,
          activeTokensCount: Array.isArray(rows) ? rows.length : 0,
          fcmConfigured: Boolean(process.env.FIREBASE_SERVICE_ACCOUNT || process.env.FCM_SERVER_KEY),
          realtimeConfigured: true
        });
      } catch (err: any) {
        return jsonResp(500, { error: err.message });
      }
    }
  }

  return jsonResp(404, { error: `Not found: ${event.path}` });
}
