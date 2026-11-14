/**
 * VYBE Netlify Serverless Function: Privileged Admin & API Operations
 * Allows static Netlify deployments to execute authenticated administrative actions,
 * user badge management, content moderation, and broadcasts securely at ₹0 budget.
 *
 * Security:
 * - Validates Supabase JWT on every request
 * - Verifies caller holds ADMIN or SUPER_ADMIN role in database
 * - Never exposes service-role credentials to the client browser
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

const SUPABASE_URL = process.env.VITE_SUPABASE_URL || process.env.SUPABASE_URL || 'https://cppwadedazpbevkfrchg.supabase.co';
const SUPABASE_ANON_KEY = process.env.VITE_SUPABASE_ANON_KEY || process.env.SUPABASE_ANON_KEY || 'sb_publishable_MI-UdOqfccHVZIHjQq9vrw_IjEzeR7H';
const SUPABASE_SERVICE_ROLE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY || '';

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization, apikey, x-admin-email',
  'Access-Control-Allow-Methods': 'GET, POST, PATCH, DELETE, OPTIONS',
  'Content-Type': 'application/json',
  'X-Content-Type-Options': 'nosniff',
  'X-Frame-Options': 'DENY',
  'Referrer-Policy': 'no-referrer'
};

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const ADMIN_ROLES = new Set(['ADMIN', 'SUPER_ADMIN', 'OWNER_ADMIN', 'MODERATOR', 'SUPPORT_ADMIN', 'ANALYST']);
const SUPER_ADMIN_ROLES = new Set(['SUPER_ADMIN', 'OWNER_ADMIN']);

async function verifyCallerAdmin(authHeader: string | undefined): Promise<{
  authorized: boolean;
  userId?: string;
  email?: string;
  token?: string;
  role?: string;
  isSuperAdmin?: boolean;
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
    // 1. Verify token with Supabase Auth
    const userRes = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
      headers: {
        'apikey': SUPABASE_ANON_KEY,
        'Authorization': `Bearer ${token}`
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

    // 2. Verify active administrator record in admin_users (fail closed; never accept profiles.role alone)
    const activeAuthKey = SUPABASE_SERVICE_ROLE_KEY || token;
    const roleHeaders: Record<string, string> = {
      'apikey': SUPABASE_SERVICE_ROLE_KEY || SUPABASE_ANON_KEY,
      'Authorization': `Bearer ${activeAuthKey}`
    };

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

    return {
      authorized: true,
      userId,
      email: typeof userData?.email === 'string' ? userData.email.toLowerCase() : 'admin',
      token,
      role: adminRole,
      isSuperAdmin: SUPER_ADMIN_ROLES.has(adminRole)
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
      headers: { ...params.dbHeaders, 'Prefer': 'return=minimal' },
      body: JSON.stringify({
        admin_id: UUID_RE.test(params.adminId) ? params.adminId : null,
        admin_email: params.adminEmail || 'admin',
        action: params.action,
        target_type: params.targetType,
        target_id: params.targetId,
        details: params.details || {}
      })
    });
  } catch {}
}

export async function handler(event: NetlifyEvent): Promise<NetlifyResponse> {
  if (event.httpMethod === 'OPTIONS') {
    return { statusCode: 204, headers: CORS_HEADERS, body: '' };
  }

  const urlPath = event.path.replace(/^\/\.netlify\/functions\/api/, '').replace(/^\/api/, '');
  const authHeader = event.headers.authorization || event.headers.Authorization;

  if (urlPath === '/v1/health' || urlPath === '/health') {
    return {
      statusCode: 200,
      headers: CORS_HEADERS,
      body: JSON.stringify({
        status: 'healthy',
        service: 'VYBE Netlify Serverless API & Admin Gateway',
        environment: 'Netlify Free Tier',
        timestamp: new Date().toISOString()
      })
    };
  }

  // All /v1/admin/* operations require verified admin JWT
  if (urlPath.startsWith('/v1/admin/')) {
    const authCheck = await verifyCallerAdmin(authHeader);
    if (!authCheck.authorized || !authCheck.userId || !authCheck.token) {
      return {
        statusCode: authCheck.statusCode || 401,
        headers: CORS_HEADERS,
        body: JSON.stringify({ error: authCheck.error || 'Unauthorized' })
      };
    }

    // Never fall back to SUPABASE_ANON_KEY as Bearer Authorization for privileged DB calls
    const dbHeaders: Record<string, string> = SUPABASE_SERVICE_ROLE_KEY
      ? {
          'apikey': SUPABASE_SERVICE_ROLE_KEY,
          'Authorization': `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
          'Content-Type': 'application/json',
          'Prefer': 'return=representation'
        }
      : {
          'apikey': SUPABASE_ANON_KEY,
          'Authorization': `Bearer ${authCheck.token}`,
          'Content-Type': 'application/json',
          'Prefer': 'return=representation'
        };

    // 1. Admin badge assignment via audited RPC (no direct unguarded profile patches)
    if (urlPath === '/v1/admin/assign-badge' && event.httpMethod === 'POST') {
      try {
        const body = JSON.parse(event.body || '{}');
        const { userId, badge, enabled } = body;
        if (!userId || !UUID_RE.test(String(userId)) || !badge) {
          return { statusCode: 400, headers: CORS_HEADERS, body: JSON.stringify({ error: 'Valid UUID userId and badge required' }) };
        }

        if (String(authCheck.userId).toLowerCase() === String(userId).toLowerCase()) {
          return { statusCode: 403, headers: CORS_HEADERS, body: JSON.stringify({ error: 'Forbidden: Self-assignment or self-revocation of badges is prohibited.' }) };
        }

        const badgeMap: Record<string, string> = {
          VERIFIED_BLUE: 'VERIFIED',
          VIP_GOLD: 'VIP',
          WORKER_GREEN: 'WORKER',
          SHOP_GOLD: 'SHOP'
        };
        const rpcCategory = badgeMap[String(badge).toUpperCase()];
        if (!rpcCategory) {
          return { statusCode: 400, headers: CORS_HEADERS, body: JSON.stringify({ error: 'Unsupported badge value.' }) };
        }
        if (rpcCategory === 'WORKER' && !authCheck.isSuperAdmin) {
          return { statusCode: 403, headers: CORS_HEADERS, body: JSON.stringify({ error: 'Forbidden: WORKER badge requires Owner / Super Admin.' }) };
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
          return { statusCode: rpcRes.status, headers: CORS_HEADERS, body: JSON.stringify({ error: 'Failed to update badge via audited RPC', details: errText.slice(0, 200) }) };
        }

        return { statusCode: 200, headers: CORS_HEADERS, body: JSON.stringify({ success: true, userId, badge, enabled: Boolean(enabled) }) };
      } catch (err: any) {
        return { statusCode: 500, headers: CORS_HEADERS, body: JSON.stringify({ error: err.message }) };
      }
    }

    // 2. Admin post moderation
    if (urlPath === '/v1/admin/content-action' && event.httpMethod === 'POST') {
      try {
        const body = JSON.parse(event.body || '{}');
        const { postId, action } = body;
        if (!postId || !UUID_RE.test(String(postId))) {
          return { statusCode: 400, headers: CORS_HEADERS, body: JSON.stringify({ error: 'Valid UUID postId required' }) };
        }

        if (action === 'DELETE' || action === 'REMOVE') {
          const delRes = await fetch(`${SUPABASE_URL}/rest/v1/posts?id=eq.${encodeURIComponent(postId)}`, {
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
              targetId: String(postId),
              details: { action }
            });
          }
          return { statusCode: delRes.ok ? 200 : delRes.status, headers: CORS_HEADERS, body: JSON.stringify({ success: delRes.ok, postId }) };
        }

        if (action === 'ARCHIVE' || action === 'HIDE') {
          const patchRes = await fetch(`${SUPABASE_URL}/rest/v1/posts?id=eq.${encodeURIComponent(postId)}`, {
            method: 'PATCH',
            headers: dbHeaders,
            body: JSON.stringify({ is_archived: true })
          });
          if (patchRes.ok) {
            await recordNetlifyAuditLog({
              dbHeaders,
              adminId: authCheck.userId,
              adminEmail: authCheck.email,
              action: `CONTENT_${action}`,
              targetType: 'POST',
              targetId: String(postId),
              details: { action }
            });
          }
          return { statusCode: patchRes.ok ? 200 : patchRes.status, headers: CORS_HEADERS, body: JSON.stringify({ success: patchRes.ok, postId, action }) };
        }

        return { statusCode: 400, headers: CORS_HEADERS, body: JSON.stringify({ error: 'Unsupported content moderation action.' }) };
      } catch (err: any) {
        return { statusCode: 500, headers: CORS_HEADERS, body: JSON.stringify({ error: err.message }) };
      }
    }

    // 3. Admin broadcast push via Supabase push-dispatch Edge Function
    if (urlPath === '/v1/admin/broadcast-push' && event.httpMethod === 'POST') {
      try {
        const body = JSON.parse(event.body || '{}');
        const { title, message, body: msgBody, destination, targetId } = body;
        const resolvedMessage = message || msgBody;
        if (!title || !resolvedMessage) {
          return { statusCode: 400, headers: CORS_HEADERS, body: JSON.stringify({ error: 'title and message are required.' }) };
        }

        const edgeRes = await fetch(`${SUPABASE_URL.replace(/\/$/, '')}/functions/v1/push-dispatch`, {
          method: 'POST',
          headers: {
            'apikey': SUPABASE_ANON_KEY,
            'Authorization': `Bearer ${authCheck.token}`,
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
          return {
            statusCode: edgeRes.status,
            headers: CORS_HEADERS,
            body: JSON.stringify({ error: edgePayload?.error || 'Broadcast dispatch failed.' })
          };
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

        return {
          statusCode: 200,
          headers: CORS_HEADERS,
          body: JSON.stringify({
            success: true,
            ...edgePayload,
            deliveredAt: new Date().toISOString()
          })
        };
      } catch (err: any) {
        return { statusCode: 500, headers: CORS_HEADERS, body: JSON.stringify({ error: err.message }) };
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
        return {
          statusCode: 200,
          headers: CORS_HEADERS,
          body: JSON.stringify({
            success: true,
            activeTokensCount: Array.isArray(rows) ? rows.length : 0,
            fcmConfigured: Boolean(process.env.FIREBASE_SERVICE_ACCOUNT || process.env.FCM_SERVER_KEY),
            realtimeConfigured: true
          })
        };
      } catch (err: any) {
        return { statusCode: 500, headers: CORS_HEADERS, body: JSON.stringify({ error: err.message }) };
      }
    }
  }

  return {
    statusCode: 404,
    headers: CORS_HEADERS,
    body: JSON.stringify({ error: `Not found: ${event.path}` })
  };
}
