const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PORT = 3000;
const WEB_DIR = path.resolve(__dirname, '../web');
const DIST_DIR = path.resolve(__dirname, '../dist');
const PUBLIC_DIR = path.resolve(__dirname, '../public');
const PUBLIC_WEBAPP_DIR = path.resolve(__dirname, '../public/webapp');
const SUPABASE_URL = process.env.SUPABASE_URL || 'https://cppwadedazpbevkfrchg.supabase.co';
const SUPABASE_ANON_KEY = process.env.SUPABASE_ANON_KEY || 'sb_publishable_MI-UdOqfccHVZIHjQq9vrw_IjEzeR7H';
const SUPABASE_SERVICE_ROLE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY || '';

function buildSupabaseHeaders(extra = {}, useServiceRoleIfPresent = true, userJwt = '') {
  const activeKey = (useServiceRoleIfPresent && SUPABASE_SERVICE_ROLE_KEY) ? SUPABASE_SERVICE_ROLE_KEY : SUPABASE_ANON_KEY;
  const hdrs = { 'apikey': activeKey, ...extra };
  if (typeof activeKey === 'string' && activeKey.startsWith('eyJ')) {
    hdrs['Authorization'] = `Bearer ${activeKey}`;
  } else if (typeof userJwt === 'string' && userJwt.startsWith('eyJ')) {
    hdrs['Authorization'] = `Bearer ${userJwt}`;
  }
  return hdrs;
}

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.txt': 'text/plain'
};

const deviceRegistry = new Map();
const recentPushLogs = [];
const PLATFORM_CONFIG_FILE = path.resolve(__dirname, 'platform_config.json');

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const ADMIN_ROLES = new Set(['ADMIN', 'SUPER_ADMIN', 'OWNER_ADMIN', 'MODERATOR', 'SUPPORT_ADMIN', 'ANALYST']);
const SUPER_ADMIN_ROLES = new Set(['SUPER_ADMIN', 'OWNER_ADMIN']);
const rateLimitBuckets = new Map();

function checkRateLimit(req, isPrivileged = false) {
  const ip = String(req?.headers?.['x-forwarded-for'] || req?.socket?.remoteAddress || 'unknown').split(',')[0].trim();
  const key = `${isPrivileged ? 'admin' : 'api'}:${ip}`;
  const now = Date.now();
  const windowMs = 60 * 1000;
  const maxReqs = isPrivileged ? 60 : 180;
  const recent = (rateLimitBuckets.get(key) || []).filter(ts => now - ts < windowMs);
  if (recent.length >= maxReqs) {
    rateLimitBuckets.set(key, recent);
    return false;
  }
  recent.push(now);
  rateLimitBuckets.set(key, recent);
  return true;
}

async function verifyUserAuth(req) {
  const authHeader = String(req?.headers?.['authorization'] || '').trim();
  if (!authHeader.startsWith('Bearer ')) {
    return { ok: false, status: 401, error: 'Missing Bearer JWT in Authorization header.' };
  }
  const token = authHeader.slice(7).trim();
  if (!token || !token.startsWith('eyJ')) {
    return { ok: false, status: 401, error: 'Invalid Bearer JWT format.' };
  }
  try {
    const authResp = await fetch(`${SUPABASE_URL.replace(/\/$/, '')}/auth/v1/user`, {
      headers: {
        'apikey': SUPABASE_ANON_KEY,
        'Authorization': `Bearer ${token}`
      }
    });
    if (!authResp.ok) {
      return { ok: false, status: 401, error: 'Invalid or expired Supabase authentication token.' };
    }
    const user = await authResp.json();
    if (!user || !user.id) {
      return { ok: false, status: 401, error: 'Authenticated user identity could not be resolved.' };
    }
    return { ok: true, user, token };
  } catch (err) {
    return { ok: false, status: 502, error: err?.message || 'Failed verifying user token with Supabase Auth.' };
  }
}

async function verifyAdminAuth(req) {
  const userAuth = await verifyUserAuth(req);
  if (!userAuth.ok) return userAuth;
  const userId = userAuth.user.id;
  const email = String(userAuth.user.email || '').trim().toLowerCase();
  const hdrs = buildSupabaseHeaders({}, true, userAuth.token);

  let aResp;
  try {
    aResp = await fetch(
      `${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/admin_users?user_id=eq.${encodeURIComponent(userId)}&select=user_id,role,is_active&limit=1`,
      { headers: hdrs }
    );
  } catch {
    return { ok: false, status: 503, error: 'Unable to verify administrator role from Supabase.' };
  }

  if (!aResp || !aResp.ok) {
    return { ok: false, status: 503, error: 'Unable to verify administrator role from Supabase.' };
  }

  let aRows;
  try {
    aRows = await aResp.json();
  } catch {
    return { ok: false, status: 503, error: 'Unable to verify administrator role from Supabase.' };
  }

  const aRow = Array.isArray(aRows) ? aRows[0] : aRows;
  const resolvedRole = String(aRow?.role || '').toUpperCase().trim();
  const userMatches = !aRow?.user_id || String(aRow.user_id).toLowerCase() === String(userId).toLowerCase();

  if (!aRow || aRow.is_active !== true || !userMatches || !ADMIN_ROLES.has(resolvedRole)) {
    return { ok: false, status: 403, error: 'Forbidden: Active administrator record required.' };
  }

  return {
    ok: true,
    userId,
    email,
    role: resolvedRole,
    isSuperAdmin: SUPER_ADMIN_ROLES.has(resolvedRole),
    token: userAuth.token
  };
}

function getDefaultPlatformConfigState() {
  return {
    featureFlags: {
      unload: true,
      unfiltered: true,
      what_next: true,
      time_capsule: true,
      double_take: true,
      crossed_paths: true,
      live_stream: true,
      voice_calls: true
    },
    platformConfig: {
      maintenanceMode: false,
      maintenanceMessage: "VYBE is undergoing scheduled performance upgrades. We'll be back shortly!",
      announcementBanner: "Welcome to VYBE 3.0 — Six intentional ways to connect.",
      announcementActive: false,
      announcementLink: "",
      communityGuidelinesUrl: "https://vybe.app/community-guidelines"
    }
  };
}

function loadPlatformConfigState() {
  const defaults = getDefaultPlatformConfigState();
  try {
    if (fs.existsSync(PLATFORM_CONFIG_FILE)) {
      const saved = JSON.parse(fs.readFileSync(PLATFORM_CONFIG_FILE, 'utf8'));
      return {
        featureFlags: { ...defaults.featureFlags, ...(saved.featureFlags || {}) },
        platformConfig: { ...defaults.platformConfig, ...(saved.platformConfig || {}) }
      };
    }
  } catch (err) {
    console.warn('[PlatformConfig] Read warning:', err.message);
  }
  return defaults;
}

function savePlatformConfigState(configState) {
  try {
    const clean = {
      featureFlags: configState.featureFlags || {},
      platformConfig: configState.platformConfig || {}
    };
    fs.writeFileSync(PLATFORM_CONFIG_FILE, JSON.stringify(clean, null, 2), 'utf8');
  } catch (err) {
    console.error('[PlatformConfig] Write error:', err.message);
  }
}

async function patchSupabaseProfile(userId, patchData, callerJwt = '') {
  if (!UUID_RE.test(String(userId || ''))) return false;
  try {
    const url = `${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/profiles?id=eq.${encodeURIComponent(userId)}`;
    const resp = await fetch(url, {
      method: 'PATCH',
      headers: buildSupabaseHeaders({
        'Content-Type': 'application/json',
        'Prefer': 'return=representation'
      }, true, callerJwt),
      body: JSON.stringify(patchData)
    });
    return resp.ok;
  } catch (err) {
    console.warn('[SupabaseProfile] Patch warning:', err.message);
    return false;
  }
}

async function recordSupabaseAuditLog({
  adminId,
  adminEmail,
  action,
  targetType,
  targetId,
  details = {},
  callerJwt = ''
}) {
  try {
    const payload = {
      admin_id: UUID_RE.test(String(adminId || '')) ? adminId : null,
      admin_email: adminEmail || 'admin',
      action: String(action || 'ADMIN_ACTION').slice(0, 100),
      target_type: String(targetType || 'SYSTEM').slice(0, 50),
      target_id: String(targetId || 'UNKNOWN').slice(0, 100),
      details: details || {}
    };
    const resp = await fetch(`${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/admin_audit_logs`, {
      method: 'POST',
      headers: buildSupabaseHeaders({
        'Content-Type': 'application/json',
        'Prefer': 'return=minimal'
      }, true, callerJwt),
      body: JSON.stringify(payload)
    });
    return resp.ok;
  } catch (err) {
    console.warn('[AuditLog] Persist warning:', err.message);
    return false;
  }
}

function getFirebaseServiceAccount() {
  try {
    if (process.env.FIREBASE_SERVICE_ACCOUNT) {
      return JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
    }
    const localSaPath = path.resolve(__dirname, 'firebase-service-account.json');
    if (fs.existsSync(localSaPath)) {
      return JSON.parse(fs.readFileSync(localSaPath, 'utf8'));
    }
  } catch (err) {
    console.warn('[FCM] Service account parse notice:', err.message);
  }
  return null;
}

let cachedAccessToken = null;

async function getGoogleAccessToken(sa) {
  if (!sa || !sa.client_email || !sa.private_key) return null;
  if (cachedAccessToken && Date.now() < cachedAccessToken.expiresAt - 60000) {
    return cachedAccessToken.token;
  }
  try {
    const now = Math.floor(Date.now() / 1000);
    const header = Buffer.from(JSON.stringify({ alg: 'RS256', typ: 'JWT' })).toString('base64url');
    const claimSet = Buffer.from(JSON.stringify({
      iss: sa.client_email,
      scope: 'https://www.googleapis.com/auth/firebase.messaging',
      aud: 'https://oauth2.googleapis.com/token',
      exp: now + 3600,
      iat: now
    })).toString('base64url');
    const sign = crypto.createSign('RSA-SHA256');
    sign.update(`${header}.${claimSet}`);
    const signature = sign.sign(sa.private_key, 'base64url');
    const jwt = `${header}.${claimSet}.${signature}`;

    const res = await fetch('https://oauth2.googleapis.com/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${jwt}`
    });
    const data = await res.json();
    if (data.access_token) {
      cachedAccessToken = {
        token: data.access_token,
        expiresAt: Date.now() + (data.expires_in * 1000)
      };
      return data.access_token;
    }
  } catch (err) {
    console.warn('[FCM] OAuth token warning:', err.message);
  }
  return null;
}

async function relayRealtimeBroadcast(topic, payload) {
  try {
    const rawTopic = topic.replace(/^realtime:/, '');
    const resp = await fetch(`${SUPABASE_URL.replace(/\/$/, '')}/realtime/v1/api/broadcast`, {
      method: 'POST',
      headers: buildSupabaseHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({
        messages: [
          { topic: `realtime:${rawTopic}`, event: 'push', payload },
          { topic: rawTopic, event: 'push', payload }
        ]
      })
    });
    return resp.ok;
  } catch {
    return false;
  }
}

async function getTokensForUser(userId) {
  const normalizedTarget = String(userId || '').toLowerCase();
  const inMem = Array.from(deviceRegistry.values())
    .filter(d => d.is_active && (String(d.userId || '').toLowerCase() === normalizedTarget || String(d.user_id || '').toLowerCase() === normalizedTarget))
    .map(d => d.fcmToken || d.fcm_token)
    .filter(Boolean);
  try {
    const resp = await fetch(`${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/user_device_tokens?user_id=eq.${encodeURIComponent(userId)}&is_active=eq.true&select=fcm_token`, {
      headers: buildSupabaseHeaders()
    });
    if (resp.ok) {
      const rows = await resp.json();
      const dbTokens = (Array.isArray(rows) ? rows : []).map(r => r.fcm_token).filter(Boolean);
      return Array.from(new Set([...inMem, ...dbTokens]));
    }
  } catch {}
  return inMem;
}

async function getAllActiveTokens() {
  const inMem = Array.from(deviceRegistry.values())
    .filter(d => d.is_active)
    .map(d => d.fcmToken || d.fcm_token)
    .filter(Boolean);
  try {
    const resp = await fetch(`${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/user_device_tokens?is_active=eq.true&select=fcm_token&limit=1000`, {
      headers: buildSupabaseHeaders()
    });
    if (resp.ok) {
      const rows = await resp.json();
      const dbTokens = (Array.isArray(rows) ? rows : []).map(r => r.fcm_token).filter(Boolean);
      return Array.from(new Set([...inMem, ...dbTokens]));
    }
  } catch {}
  return inMem;
}

async function deactivateInvalidToken(badToken) {
  if (!badToken) return;
  const rec = deviceRegistry.get(badToken);
  if (rec) {
    rec.is_active = false;
    deviceRegistry.set(badToken, rec);
  }
  try {
    await fetch(`${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/user_device_tokens?fcm_token=eq.${encodeURIComponent(badToken)}`, {
      method: 'PATCH',
      headers: buildSupabaseHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({ is_active: false, updated_at: new Date().toISOString() })
    });
  } catch {}
}

async function dispatchFcmIfConfigured({ tokens = [], topic, title, body, data = {}, channelId = 'vybe_activity_high_v2' }) {
  const sa = getFirebaseServiceAccount();
  if (!sa) return { dispatchedFcm: false, count: tokens.length };

  const accessToken = await getGoogleAccessToken(sa);
  if (!accessToken) return { dispatchedFcm: false, count: tokens.length };

  const projectId = sa.project_id || 'vybe-social-app';
  const targets = tokens.length > 0 ? tokens.map(t => ({ token: t })) : (topic ? [{ topic }] : []);
  let ok = 0;

  // High-priority data-only Direct-Boot FCM HTTP v1 payload:
  // Wakes closed & locked Android devices via VybeFirebaseMessagingService without duplicate OS + app tray banners.
  const resolvedEventKey = String(data.event_key || data.eventKey || data.notification_id || `evt_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`);
  for (const target of targets) {
    try {
      const stringData = Object.fromEntries(
        Object.entries({ ...data, title, body, event_key: resolvedEventKey, channel_id: channelId })
          .filter(([, v]) => v !== undefined && v !== null)
          .map(([k, v]) => [k, String(v)])
      );
      const resp = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${accessToken}`
        },
        body: JSON.stringify({
          message: {
            ...(target.token ? { token: target.token } : { topic: target.topic }),
            notification: {
              title: String(title),
              body: String(body)
            },
            data: stringData,
            android: {
              priority: 'HIGH',
              ttl: '86400s',
              direct_boot_ok: true,
              notification: {
                title: String(title),
                body: String(body),
                channel_id: channelId || 'vybe_activity_high_v2',
                icon: 'ic_vybe_notification',
                color: '#7B2CBF',
                default_sound: true,
                default_vibrate_timings: true,
                notification_priority: 'PRIORITY_MAX',
                visibility: 'PUBLIC'
              }
            }
          }
        })
      });
      if (resp.ok) {
        ok++;
      } else if (target.token) {
        const errText = await resp.text();
        if (errText.includes('UNREGISTERED') || errText.includes('INVALID_ARGUMENT')) {
          await deactivateInvalidToken(target.token);
        }
      }
    } catch {}
  }
  return { dispatchedFcm: true, count: ok };
}

function readJsonBody(req) {
  return new Promise((resolve) => {
    let raw = '';
    req.on('data', chunk => {
      raw += chunk;
      if (raw.length > 2 * 1024 * 1024) req.destroy();
    });
    req.on('end', () => {
      try {
        resolve(raw ? JSON.parse(raw) : {});
      } catch {
        resolve({});
      }
    });
    req.on('error', () => resolve({}));
  });
}

function sendJson(res, status, obj) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(obj));
}

const ALLOWED_ORIGINS = (process.env.ALLOWED_ORIGINS || '').split(',').map(s => s.trim()).filter(Boolean);

const server = http.createServer(async (req, res) => {
  const reqOrigin = String(req.headers['origin'] || '').trim();
  const corsOrigin = ALLOWED_ORIGINS.length === 0
    ? '*'
    : (ALLOWED_ORIGINS.includes(reqOrigin) ? reqOrigin : ALLOWED_ORIGINS[0]);

  res.setHeader('Access-Control-Allow-Origin', corsOrigin);
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-Vybe-Owner-Console');
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('X-Frame-Options', 'DENY');
  res.setHeader('Referrer-Policy', 'no-referrer');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  const cleanUrl = (req.url || '/').split('?')[0];

  if (cleanUrl === '/health' || cleanUrl === '/api/v1/health') {
    sendJson(res, 200, {
      status: 'healthy',
      server: 'VYBE Admin & Push Gateway',
      timestamp: new Date().toISOString()
    });
    return;
  }

  if (cleanUrl.startsWith('/api/')) {
    const isPrivilegedApi = cleanUrl.startsWith('/api/v1/admin/') || cleanUrl === '/api/v1/push/broadcast';
    if (!checkRateLimit(req, isPrivilegedApi)) {
      sendJson(res, 429, { error: 'Too many requests. Please slow down and try again shortly.' });
      return;
    }
  }

  // Global Zero-Trust Admin Verification Gate for ALL /api/v1/admin/* endpoints and /api/v1/push/broadcast
  let adminAuth = null;
  if (cleanUrl.startsWith('/api/v1/admin/') || cleanUrl === '/api/v1/push/broadcast') {
    adminAuth = await verifyAdminAuth(req);
    if (!adminAuth.ok) {
      sendJson(res, adminAuth.status || 401, { error: adminAuth.error || 'Unauthorized' });
      return;
    }
  }

  if (cleanUrl === '/api/v1/push/register-token' && req.method === 'POST') {
    const authHeader = (req.headers['authorization'] || '').trim();
    if (!authHeader.startsWith('Bearer ')) {
      sendJson(res, 401, { error: 'Authentication required. Missing Bearer JWT in Authorization header.' });
      return;
    }

    // Verify JWT against Supabase Auth to extract authoritative user UUID
    let verifiedUid = null;
    try {
      const authResp = await fetch(`${SUPABASE_URL.replace(/\/$/, '')}/auth/v1/user`, {
        headers: {
          'apikey': SUPABASE_ANON_KEY,
          'Authorization': authHeader
        }
      });
      if (authResp.ok) {
        const u = await authResp.json();
        verifiedUid = u && u.id;
      }
    } catch (authErr) {
      console.warn('[PushRegister] Supabase token verification error:', authErr.message);
    }

    if (!verifiedUid) {
      sendJson(res, 401, { error: 'Invalid or expired Supabase authentication token.' });
      return;
    }

    const body = await readJsonBody(req);
    const fcmToken = (body.fcmToken || body.fcm_token || '').trim();
    const declaredUserId = (body.userId || body.user_id || '').trim();

    if (!fcmToken) {
      sendJson(res, 400, { error: 'fcmToken is required.' });
      return;
    }

    if (declaredUserId && declaredUserId.toLowerCase() !== verifiedUid.toLowerCase()) {
      sendJson(res, 403, { error: 'Registration rejected: authenticated user ID does not match request user ID.' });
      return;
    }

    const nowIso = new Date().toISOString();
    const deviceModel = (body.deviceModel || body.device_model || 'Android Phone').trim().slice(0, 110);
    const platform = (body.platform || 'android').trim().toLowerCase();

    const record = {
      user_id: verifiedUid,
      fcm_token: fcmToken,
      device_model: deviceModel,
      platform: platform,
      is_active: true,
      last_seen_at: nowIso,
      updated_at: nowIso
    };

    // Persist directly to Supabase public.user_device_tokens using user JWT (satisfies RLS auth.uid() = user_id)
    let dbSuccess = false;
    let dbErrorMsg = '';

    try {
      const upsertResp = await fetch(`${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/user_device_tokens?on_conflict=user_id,fcm_token`, {
        method: 'POST',
        headers: {
          'apikey': SUPABASE_ANON_KEY,
          'Authorization': authHeader,
          'Content-Type': 'application/json',
          'Prefer': 'resolution=merge-duplicates,return=representation'
        },
        body: JSON.stringify(record)
      });

      if (upsertResp.ok) {
        dbSuccess = true;
      } else {
        dbErrorMsg = await upsertResp.text();
        // Fallback to service role if available
        if (SUPABASE_SERVICE_ROLE_KEY) {
          const adminResp = await fetch(`${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/user_device_tokens?on_conflict=user_id,fcm_token`, {
            method: 'POST',
            headers: {
              'apikey': SUPABASE_SERVICE_ROLE_KEY,
              'Authorization': `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
              'Content-Type': 'application/json',
              'Prefer': 'resolution=merge-duplicates,return=representation'
            },
            body: JSON.stringify(record)
          });
          if (adminResp.ok) {
            dbSuccess = true;
          }
        }
      }
    } catch (e) {
      dbErrorMsg = e.message;
    }

    if (!dbSuccess) {
      console.warn(`[PushRegister] Database write failed for user=${verifiedUid}: ${dbErrorMsg.slice(0, 200)}`);
      sendJson(res, 500, { error: 'Failed to write device token to Supabase database: ' + dbErrorMsg.slice(0, 150) });
      return;
    }

    // Only update memory cache AFTER successful database persistence
    deviceRegistry.set(fcmToken, {
      userId: verifiedUid,
      user_id: verifiedUid,
      fcmToken,
      fcm_token: fcmToken,
      device_model: deviceModel,
      platform: platform,
      last_seen_at: nowIso,
      is_active: true
    });

    console.log(`[PushRegister] Successfully persisted device token for user=${verifiedUid}`);
    sendJson(res, 200, { success: true, registered: true, user_id: verifiedUid });
    return;
  }

  if (cleanUrl === '/api/v1/push/send' && req.method === 'POST') {
    const authCheck = await verifyUserAuth(req);
    if (!authCheck.ok) {
      sendJson(res, authCheck.status || 401, { error: authCheck.error || 'Unauthorized' });
      return;
    }
    const callerId = authCheck.user.id;
    const body = await readJsonBody(req);
    const recipientId = body.recipientId || body.recipient_id;
    const title = body.title;
    const msgBody = body.body || body.message;
    const rawData = body.data || {};
    const channelId = body.channelId || body.channel_id || rawData.channel_id || 'vybe_activity_high_v2';
    if (!recipientId || !title || !msgBody) {
      sendJson(res, 400, { error: 'recipientId, title, and body are required.' });
      return;
    }

    if (String(recipientId).toLowerCase() !== String(callerId).toLowerCase()) {
      try {
        const blockResp = await fetch(
          `${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/blocks?select=blocker_id&or=(and(blocker_id.eq.${encodeURIComponent(callerId)},blocked_id.eq.${encodeURIComponent(recipientId)}),and(blocker_id.eq.${encodeURIComponent(recipientId)},blocked_id.eq.${encodeURIComponent(callerId)}))&limit=1`,
          { headers: buildSupabaseHeaders({}, true, authCheck.token) }
        );
        if (blockResp.ok) {
          const blockRows = await blockResp.json();
          if (Array.isArray(blockRows) && blockRows.length > 0) {
            sendJson(res, 200, { success: true, skipped: 'blocked', delivered: 0 });
            return;
          }
        }
      } catch {}
    }

    const data = {
      ...rawData,
      type: body.type || rawData.type || 'ACTIVITY',
      event_key: body.eventKey || body.event_key || rawData.event_key || `evt_${Date.now()}`,
      actor_id: callerId,
      ...(body.targetId || body.target_id ? { target_id: body.targetId || body.target_id } : {}),
      ...(body.conversationId || body.conversation_id ? { conversation_id: body.conversationId || body.conversation_id } : {}),
      recipient_id: recipientId
    };
    const tokens = await getTokensForUser(recipientId);

    await relayRealtimeBroadcast(`vybe_user_push:${recipientId}`, {
      ...data,
      recipient_id: recipientId,
      title,
      body: msgBody
    });

    const sanitizedTopic = `vybe_user_${String(recipientId).replace(/[^a-zA-Z0-9-_.~%]/g, '_')}`;
    const fcmRes = await dispatchFcmIfConfigured({
      tokens,
      topic: sanitizedTopic,
      title,
      body: msgBody,
      data,
      channelId
    });

    recentPushLogs.unshift({
      timestamp: new Date().toISOString(),
      title,
      recipientCount: tokens.length,
      status: fcmRes.dispatchedFcm ? 'Dispatched (FCM + Realtime Live)' : 'Dispatched (Realtime Live)',
      channel: channelId
    });
    if (recentPushLogs.length > 50) recentPushLogs.pop();

    sendJson(res, 200, { success: true, delivered: fcmRes.count });
    return;
  }

  if ((cleanUrl === '/api/v1/admin/broadcast-push' || cleanUrl === '/api/v1/push/broadcast') && req.method === 'POST') {
    const body = await readJsonBody(req);
    const title = body.title;
    const msgBody = body.body || body.message;
    const destination = body.destination || 'NOTIFICATIONS';
    const targetId = body.targetId || body.target_id;
    if (!title || !msgBody) {
      sendJson(res, 400, { error: 'Title and body are required.' });
      return;
    }

    const eventKey = body.eventKey || body.event_key || `bcast_${Date.now()}`;
    const pushData = {
      type: 'ANNOUNCEMENT',
      channel: 'broadcast',
      destination,
      event_key: eventKey,
      ...(targetId ? { target_id: targetId } : {})
    };

    const rtOk = await relayRealtimeBroadcast('vybe_global_broadcast', {
      ...pushData,
      title,
      body: msgBody
    });

    const tokens = await getAllActiveTokens();

    const fcmRes = await dispatchFcmIfConfigured({
      tokens,
      topic: 'vybe_all_users',
      title,
      body: msgBody,
      data: pushData,
      channelId: 'vybe_broadcast_high_v2'
    });

    recentPushLogs.unshift({
      timestamp: new Date().toISOString(),
      title,
      recipientCount: tokens.length,
      status: fcmRes.dispatchedFcm ? 'Dispatched (FCM + Realtime Live)' : (rtOk ? 'Dispatched (Realtime Live)' : 'Dispatched'),
      channel: 'vybe_broadcast_high_v2'
    });
    if (recentPushLogs.length > 50) recentPushLogs.pop();

    await recordSupabaseAuditLog({
      adminId: adminAuth.userId,
      adminEmail: adminAuth.email,
      action: 'SYSTEM_BROADCAST',
      targetType: 'PLATFORM',
      targetId: 'ALL_USERS',
      details: { title, destination, eventKey },
      callerJwt: adminAuth.token
    });

    sendJson(res, 200, {
      success: true,
      totalDevices: tokens.length,
      delivered: fcmRes.count
    });
    return;
  }

  if (cleanUrl === '/api/v1/admin/push-stats' && req.method === 'GET') {
    let dbDevices = [];
    try {
      const resp = await fetch(
        `${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/user_device_tokens?is_active=eq.true&select=user_id,device_model,platform,last_seen_at,is_active&order=last_seen_at.desc&limit=100`,
        { headers: buildSupabaseHeaders({}, true, adminAuth.token) }
      );
      if (resp.ok) {
        const rows = await resp.json();
        if (Array.isArray(rows)) dbDevices = rows;
      }
    } catch {}

    const memActive = Array.from(deviceRegistry.values()).filter(d => d.is_active);
    const activeTokensCount = dbDevices.length > 0 ? dbDevices.length : memActive.length;
    const recentDevices = dbDevices.length > 0 ? dbDevices.slice(0, 10) : memActive.slice(0, 10);

    sendJson(res, 200, {
      activeTokensCount,
      fcmConfigured: Boolean(getFirebaseServiceAccount() || process.env.FCM_SERVER_KEY),
      realtimeConfigured: true,
      recentDevices,
      recentLogs: recentPushLogs
    });
    return;
  }

  if (cleanUrl === '/api/v1/admin/fcm-config' && req.method === 'GET') {
    const sa = getFirebaseServiceAccount();
    const activeTokens = await getAllActiveTokens();
    sendJson(res, 200, {
      configured: Boolean(sa),
      projectId: sa?.project_id || (process.env.FIREBASE_PROJECT_ID || 'vybe-social-app'),
      clientEmail: sa?.client_email || 'Not Configured (Standby Mode)',
      activeTokensCount: activeTokens.length
    });
    return;
  }

  if (cleanUrl === '/api/v1/admin/fcm-config' && req.method === 'POST') {
    if (!adminAuth.isSuperAdmin) {
      sendJson(res, 403, { error: 'Forbidden: Only Super Admin / Owner Admin may update FCM configuration.' });
      return;
    }
    if (process.env.ALLOW_DISK_FCM_CONFIG !== 'true') {
      sendJson(res, 403, { error: 'Runtime disk write for FCM credentials is disabled. Configure FIREBASE_SERVICE_ACCOUNT via environment secrets.' });
      return;
    }
    const body = await readJsonBody(req);
    let saObj = null;
    if (body.serviceAccountJson) {
      if (typeof body.serviceAccountJson === 'string') {
        try {
          saObj = JSON.parse(body.serviceAccountJson);
        } catch {
          sendJson(res, 400, { error: 'Invalid JSON string provided.' });
          return;
        }
      } else if (typeof body.serviceAccountJson === 'object') {
        saObj = body.serviceAccountJson;
      }
    } else if (body.project_id && body.private_key) {
      saObj = body;
    }

    if (!saObj || saObj.type !== 'service_account' || !saObj.client_email || !saObj.private_key) {
      sendJson(res, 400, { error: 'Service account must have type=service_account, client_email, and private_key.' });
      return;
    }

    const saPath = path.resolve(__dirname, 'firebase-service-account.json');
    fs.writeFileSync(saPath, JSON.stringify(saObj, null, 2), 'utf8');
    cachedAccessToken = null;

    const testToken = await getGoogleAccessToken(saObj);
    sendJson(res, 200, {
      success: true,
      configured: Boolean(testToken),
      projectId: saObj.project_id || 'vybe-social-app',
      clientEmail: saObj.client_email,
      message: testToken ? 'FCM Service Account verified and actively authenticated!' : 'Saved, but OAuth token minting returned null. Please verify private_key.'
    });
    return;
  }

  // 1. GET ALL USERS (LIVE SUPABASE PROFILES ONLY — PII stripped)
  if (cleanUrl === '/api/v1/admin/users' && req.method === 'GET') {
    let resp;
    try {
      resp = await fetch(
        `${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/profiles?select=id,username,display_name,avatar_url,role,is_verified,is_vip,is_worker,is_verified_business,is_banned,is_suspended,suspended_until,created_at&order=created_at.desc&limit=100`,
        { headers: buildSupabaseHeaders({}, true, adminAuth.token) }
      );
    } catch (err) {
      sendJson(res, 502, { error: 'Failed communicating with Supabase profiles: ' + err.message });
      return;
    }

    if (!resp.ok) {
      sendJson(res, resp.status >= 400 ? resp.status : 502, { error: 'Failed fetching users from Supabase.' });
      return;
    }

    const rawUsers = await resp.json();
    const users = (Array.isArray(rawUsers) ? rawUsers : []).map(u => {
      const isWorker = Boolean(u.is_worker);
      const isVip = Boolean(u.is_vip);
      const isVerified = Boolean(u.is_verified);
      const isVerifiedBusiness = Boolean(u.is_verified_business);
      const computedBadge = isWorker ? 'OFFICIAL' : (isVip ? 'VIP' : (isVerified ? 'VERIFIED' : (isVerifiedBusiness ? 'SHOP' : null)));

      return {
        ...u,
        is_verified: isVerified,
        is_vip: isVip,
        is_worker: isWorker,
        is_verified_business: isVerifiedBusiness,
        badge_type: computedBadge,
        role: u.role || 'USER',
        is_banned: Boolean(u.is_banned),
        is_suspended: Boolean(u.is_suspended),
        suspended_until: u.suspended_until || null
      };
    });

    sendJson(res, 200, { success: true, users, total: users.length });
    return;
  }

  // 2. ASSIGN / REVOKE BADGE (THREE-TIER BADGE SYSTEM)
  if (cleanUrl === '/api/v1/admin/assign-badge' && req.method === 'POST') {
    const body = await readJsonBody(req);
    const { userId, badgeType, value, username } = body;
    if (!userId || !UUID_RE.test(String(userId)) || !badgeType) {
      sendJson(res, 400, { error: 'Valid UUID userId and badgeType required' });
      return;
    }

    const normalizedType = String(badgeType).toLowerCase().trim();
    const callerId = adminAuth.userId;

    // Security Check 1: Self-assignment prohibition
    if (String(callerId).toLowerCase() === String(userId).toLowerCase()) {
      sendJson(res, 403, { error: 'Security Policy: Self-assignment or self-revocation of verification badges is strictly forbidden.' });
      return;
    }

    // Security Check 2: Super Admin authorization required for Green Official badge
    if ((normalizedType === 'official' || normalizedType === 'worker') && !adminAuth.isSuperAdmin) {
      sendJson(res, 403, { error: 'Security Policy: VYBE Official badges can only be assigned or revoked by the Super Admin / Owner Admin.' });
      return;
    }

    let rpcCategory = 'VERIFIED';
    let dbKey = 'is_verified';
    let badgeDisplayLabel = 'VYBE Verified (Electric Blue)';
    if (normalizedType === 'verified') {
      rpcCategory = 'VERIFIED';
      dbKey = 'is_verified';
      badgeDisplayLabel = 'VYBE Verified (Electric Blue)';
    } else if (normalizedType === 'vip') {
      rpcCategory = 'VIP';
      dbKey = 'is_vip';
      badgeDisplayLabel = 'VYBE VIP (Metallic Gold)';
    } else if (normalizedType === 'official' || normalizedType === 'worker') {
      rpcCategory = 'WORKER';
      dbKey = 'is_worker';
      badgeDisplayLabel = 'VYBE Official (Emerald Green)';
    } else if (normalizedType === 'business' || normalizedType === 'shop') {
      rpcCategory = 'SHOP';
      dbKey = 'is_verified_business';
      badgeDisplayLabel = 'VYBE Storefront Shop';
    } else {
      sendJson(res, 400, { error: 'Unsupported badgeType.' });
      return;
    }

    try {
      const rpcResp = await fetch(`${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/rpc/admin_assign_badge`, {
        method: 'POST',
        headers: buildSupabaseHeaders({ 'Content-Type': 'application/json' }, true, adminAuth.token),
        body: JSON.stringify({
          p_target_user_id: userId,
          p_badge_category: rpcCategory,
          p_is_grant: Boolean(value),
          p_admin_id: callerId,
          p_reason: `Admin console ${value ? 'grant' : 'revoke'}`
        })
      });
      if (!rpcResp.ok) {
        const errText = await rpcResp.text().catch(() => '');
        sendJson(res, rpcResp.status === 403 || rpcResp.status === 401 ? rpcResp.status : 502, {
          error: 'Supabase admin_assign_badge RPC failed',
          details: errText.slice(0, 200)
        });
        return;
      }
    } catch (rpcErr) {
      sendJson(res, 502, { error: 'Failed communicating with Supabase badge RPC: ' + rpcErr.message });
      return;
    }

    // DISPATCH PROFESSIONAL IN-APP & PUSH NOTIFICATION TO USER
    try {
      let notifTitle = '';
      let notifBody = '';
      const safeUser = username ? `@${username}` : 'Member';

      if (normalizedType === 'verified') {
        if (value) {
          notifTitle = '🌟 Official Account Verification';
          notifBody = `Congratulations ${safeUser}! Your VYBE profile has been officially verified by NR Next Platform Administration. Your Electric Blue Verification Badge is now active on your profile across all feeds, reels, and comments.`;
        } else {
          notifTitle = 'VYBE Account Update';
          notifBody = `${safeUser}, your Blue Verified status has been revised by Platform Administration.`;
        }
      } else if (normalizedType === 'vip') {
        if (value) {
          notifTitle = '👑 VIP Member Status Awarded';
          notifBody = `Welcome to the elite VYBE VIP Circle, ${safeUser}! You have been granted the official Metallic Gold Crowned VIP Badge with priority discovery and creator privileges.`;
        } else {
          notifTitle = 'VIP Status Notification';
          notifBody = `${safeUser}, your Metallic Gold VIP Creator status has been updated by Platform Administration.`;
        }
      } else if (normalizedType === 'official' || normalizedType === 'worker') {
        if (value) {
          notifTitle = '🛡️ Official Platform Badge Assigned';
          notifBody = `Welcome ${safeUser}! You have been officially recognized with the Emerald Green VYBE Official badge by Super Admin. This emblem confirms your authorized role within VYBE / NR Next operations.`;
        } else {
          notifTitle = 'Official Platform Status Update';
          notifBody = `${safeUser}, your Emerald Green Official platform badge has been revised by Super Admin.`;
        }
      } else if (normalizedType === 'business' || normalizedType === 'shop') {
        if (value) {
          notifTitle = '🏪 Verified Business Shop Approved';
          notifBody = `Congratulations ${safeUser}! Your commercial storefront has been verified. The official Shop badge is now live on your VYBE profile.`;
        } else {
          notifTitle = 'Storefront Status Update';
          notifBody = `${safeUser}, your Verified Business Shop status has been revised by Platform Administration.`;
        }
      }

      if (notifTitle && notifBody) {
        // 1. Live Realtime Relay to phone app
        relayRealtimeBroadcast(`vybe_user_push:${userId}`, {
          type: 'BADGE_UPDATE',
          badge_type: badgeType,
          value: Boolean(value),
          recipient_id: userId,
          title: notifTitle,
          body: notifBody
        });

        // 2. High Priority FCM Push Notification to Android
        const userTokens = await getTokensForUser(userId);

        dispatchFcmIfConfigured({
          tokens: userTokens,
          topic: `vybe_user_${String(userId).replace(/[^a-zA-Z0-9-_.~%]/g, '_')}`,
          title: notifTitle,
          body: notifBody,
          data: {
            type: 'BADGE_UPDATE',
            badge_type: badgeType,
            value: String(value),
            title: notifTitle,
            body: notifBody
          },
          channelId: 'vybe_activity_high_v2'
        });

        // 3. Insert into Supabase notifications table using verified admin token or service role
        fetch(`${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/notifications`, {
          method: 'POST',
          headers: buildSupabaseHeaders({ 'Content-Type': 'application/json', 'Prefer': 'return=minimal' }, true, adminAuth.token),
          body: JSON.stringify({
            recipient_id: userId,
            actor_id: callerId,
            type: 'SYSTEM',
            title: notifTitle,
            message: notifBody,
            is_read: false
          })
        }).catch(() => {});
      }
    } catch (notifErr) {
      console.warn('[BadgeNotif] Warning:', notifErr.message);
    }

    sendJson(res, 200, {
      success: true,
      message: `Badge ${badgeType} ${value ? 'granted to' : 'revoked from'} ${username || userId}`,
      userId,
      badgeType,
      badgeCategory: rpcCategory,
      value: Boolean(value)
    });
    return;
  }

  // 3. SET USER ROLE (Super Admin only, prevents self-mutation, syncs profiles + admin_users + audit log)
  if (cleanUrl === '/api/v1/admin/set-role' && req.method === 'POST') {
    if (!adminAuth.isSuperAdmin) {
      sendJson(res, 403, { error: 'Forbidden: Only Super Admin / Owner Admin may modify platform roles.' });
      return;
    }

    const body = await readJsonBody(req);
    const { userId, role, username } = body;
    if (!userId || !UUID_RE.test(String(userId)) || !role) {
      sendJson(res, 400, { error: 'Valid UUID userId and role required.' });
      return;
    }

    if (String(adminAuth.userId).toLowerCase() === String(userId).toLowerCase()) {
      sendJson(res, 403, { error: 'Security Policy: Administrators cannot modify their own platform role.' });
      return;
    }

    const requestedRole = String(role).toUpperCase().trim();
    const ALLOWED_PROFILE_ROLES = new Set(['USER', 'CREATOR', 'BUSINESS_OWNER', 'VERIFIED_PUBLIC_FIGURE', 'MODERATOR', 'ADMIN']);
    const ADMIN_SUB_ROLE_MAP = {
      OWNER_ADMIN: { profileRole: 'ADMIN', adminSubRole: 'OWNER_ADMIN' },
      SUPER_ADMIN: { profileRole: 'ADMIN', adminSubRole: 'OWNER_ADMIN' },
      ADMIN: { profileRole: 'ADMIN', adminSubRole: 'MODERATOR' },
      SUPPORT_ADMIN: { profileRole: 'MODERATOR', adminSubRole: 'SUPPORT_ADMIN' },
      ANALYST: { profileRole: 'MODERATOR', adminSubRole: 'ANALYST' },
      MODERATOR: { profileRole: 'MODERATOR', adminSubRole: 'MODERATOR' }
    };

    let targetProfileRole = '';
    let targetAdminSubRole = null;
    if (ADMIN_SUB_ROLE_MAP[requestedRole]) {
      targetProfileRole = ADMIN_SUB_ROLE_MAP[requestedRole].profileRole;
      targetAdminSubRole = ADMIN_SUB_ROLE_MAP[requestedRole].adminSubRole;
    } else if (ALLOWED_PROFILE_ROLES.has(requestedRole)) {
      targetProfileRole = requestedRole;
      targetAdminSubRole = null;
    } else {
      sendJson(res, 400, { error: 'Unsupported role specified.' });
      return;
    }

    const dbUpdated = await patchSupabaseProfile(userId, { role: targetProfileRole }, adminAuth.token);
    if (!dbUpdated) {
      sendJson(res, 502, { error: 'Failed updating user role in Supabase database.' });
      return;
    }

    // Keep public.admin_users synchronized with role grant/revocation
    try {
      if (targetAdminSubRole) {
        await fetch(`${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/admin_users?on_conflict=user_id`, {
          method: 'POST',
          headers: buildSupabaseHeaders({
            'Content-Type': 'application/json',
            'Prefer': 'resolution=merge-duplicates,return=minimal'
          }, true, adminAuth.token),
          body: JSON.stringify({
            user_id: userId,
            role: targetAdminSubRole,
            is_active: true,
            assigned_by: adminAuth.userId,
            updated_at: new Date().toISOString()
          })
        });
      } else {
        await fetch(`${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/admin_users?user_id=eq.${encodeURIComponent(userId)}`, {
          method: 'PATCH',
          headers: buildSupabaseHeaders({
            'Content-Type': 'application/json',
            'Prefer': 'return=minimal'
          }, true, adminAuth.token),
          body: JSON.stringify({
            is_active: false,
            updated_at: new Date().toISOString()
          })
        });
      }
    } catch (syncErr) {
      console.warn('[SetRole] admin_users sync notice:', syncErr.message);
    }

    await recordSupabaseAuditLog({
      adminId: adminAuth.userId,
      adminEmail: adminAuth.email,
      action: 'UPDATE_ROLE',
      targetType: 'profiles',
      targetId: userId,
      details: { requestedRole, profileRole: targetProfileRole, adminSubRole: targetAdminSubRole },
      callerJwt: adminAuth.token
    });

    // Broadcast role update & send push
    relayRealtimeBroadcast(`vybe_user_push:${userId}`, {
      type: 'ROLE_UPDATE',
      role: targetAdminSubRole || targetProfileRole,
      recipient_id: userId,
      title: '👑 Account Role Updated',
      body: `Your platform role has been updated to ${targetAdminSubRole || targetProfileRole}.`
    });

    getTokensForUser(userId).then(tokens => {
      dispatchFcmIfConfigured({
        tokens,
        topic: `vybe_user_${String(userId).replace(/[^a-zA-Z0-9-_.~%]/g, '_')}`,
        title: '👑 Account Role Updated',
        body: `Your platform role has been updated to ${targetAdminSubRole || targetProfileRole}.`,
        data: { type: 'ROLE_UPDATE', role: targetAdminSubRole || targetProfileRole },
        channelId: 'vybe_system_high_v2'
      });
    }).catch(() => {});

    sendJson(res, 200, {
      success: true,
      message: `Role updated to ${targetAdminSubRole || targetProfileRole} for ${username || userId}`,
      userId,
      role: targetAdminSubRole || targetProfileRole,
      profileRole: targetProfileRole
    });
    return;
  }

  // 4. SUSPEND / BAN / RESTORE USER STATUS
  if (cleanUrl === '/api/v1/admin/user-status' && req.method === 'POST') {
    const body = await readJsonBody(req);
    const { userId, action, reason, durationHours = 24, username } = body;
    const normalizedAction = String(action || '').toUpperCase().trim();
    const ALLOWED_USER_STATUS_ACTIONS = new Set(['SUSPEND', 'BAN', 'WARN', 'RESTRICT', 'RESTORE']);

    if (!userId || !UUID_RE.test(String(userId)) || !ALLOWED_USER_STATUS_ACTIONS.has(normalizedAction)) {
      sendJson(res, 400, { error: 'Valid UUID userId and action (SUSPEND, BAN, WARN, RESTRICT, RESTORE) required.' });
      return;
    }

    if (String(adminAuth.userId).toLowerCase() === String(userId).toLowerCase()) {
      sendJson(res, 403, { error: 'Security Policy: Administrators cannot moderate their own account status.' });
      return;
    }

    const safeHours = Math.max(1, Math.min(8760, Number(durationHours) || 24));
    const safeReason = String(reason || '').trim();

    let patch = {};
    let expiresAtIso = null;
    if (normalizedAction === 'SUSPEND') {
      expiresAtIso = new Date(Date.now() + safeHours * 3600 * 1000).toISOString();
      patch = { is_suspended: true, suspension_reason: safeReason || `${safeHours}h enforcement`, suspended_until: expiresAtIso };
    } else if (normalizedAction === 'BAN') {
      patch = { is_banned: true, ban_reason: safeReason || 'Admin account termination' };
    } else if (normalizedAction === 'WARN') {
      relayRealtimeBroadcast(`vybe_user_push:${userId}`, {
        type: 'ACCOUNT_WARNING',
        title: '⚠️ Community Guidelines Warning',
        body: `Your account received a moderation notice: ${safeReason || 'Please review VYBE Community Guidelines.'}`
      });
      getTokensForUser(userId).then(tokens => {
        dispatchFcmIfConfigured({
          tokens,
          topic: `vybe_user_${String(userId).replace(/[^a-zA-Z0-9-_.~%]/g, '_')}`,
          title: '⚠️ Community Guidelines Warning',
          body: `Your account received a moderation notice: ${safeReason || 'Please review VYBE Community Guidelines.'}`,
          data: { type: 'ACCOUNT_WARNING', reason: safeReason },
          channelId: 'vybe_system_high_v2'
        });
      }).catch(() => {});
    } else if (normalizedAction === 'RESTRICT') {
      expiresAtIso = new Date(Date.now() + safeHours * 3600 * 1000).toISOString();
      relayRealtimeBroadcast(`vybe_user_push:${userId}`, {
        type: 'ACCOUNT_RESTRICTED',
        title: '🔒 Account Interaction Restricted',
        body: `Your account has been temporarily restricted for ${safeHours}h: ${safeReason || 'Violation of platform rules'}.`
      });
    } else if (normalizedAction === 'RESTORE') {
      patch = { is_banned: false, is_suspended: false, ban_reason: null, suspension_reason: null, suspended_until: null };
    }

    if (normalizedAction === 'BAN' || normalizedAction === 'SUSPEND' || normalizedAction === 'RESTORE') {
      const statusTitle = normalizedAction === 'BAN' ? '🚫 Account Suspended' : (normalizedAction === 'SUSPEND' ? '⏳ Account Temporarily Suspended' : '✅ Account Restored');
      const statusBody = normalizedAction === 'RESTORE' ? 'Your account has been fully restored by Platform Administration.' : `Status notification: ${safeReason || 'Please contact VYBE support.'}`;
      relayRealtimeBroadcast(`vybe_user_push:${userId}`, {
        type: 'USER_STATUS_UPDATE',
        action: normalizedAction,
        reason: safeReason,
        title: statusTitle,
        body: statusBody
      });
      getTokensForUser(userId).then(tokens => {
        dispatchFcmIfConfigured({
          tokens,
          topic: `vybe_user_${String(userId).replace(/[^a-zA-Z0-9-_.~%]/g, '_')}`,
          title: statusTitle,
          body: statusBody,
          data: { type: 'USER_STATUS_UPDATE', action: normalizedAction, reason: safeReason },
          channelId: 'vybe_system_high_v2'
        });
      }).catch(() => {});
    }

    if (Object.keys(patch).length > 0) {
      const dbOk = await patchSupabaseProfile(userId, patch, adminAuth.token);
      if (!dbOk) {
        sendJson(res, 502, { error: 'Failed updating user moderation status in Supabase database.' });
        return;
      }
    }

    // Persist moderation action history in Supabase
    try {
      await fetch(`${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/user_moderation_actions`, {
        method: 'POST',
        headers: buildSupabaseHeaders({
          'Content-Type': 'application/json',
          'Prefer': 'return=minimal'
        }, true, adminAuth.token),
        body: JSON.stringify({
          user_id: userId,
          action_type: normalizedAction === 'RESTORE' ? 'UNBAN' : normalizedAction,
          reason: safeReason || `Admin action: ${normalizedAction}`,
          duration_hours: (normalizedAction === 'SUSPEND' || normalizedAction === 'RESTRICT') ? safeHours : null,
          expires_at: expiresAtIso,
          is_active: normalizedAction !== 'RESTORE',
          created_by: adminAuth.userId
        })
      });
    } catch {}

    await recordSupabaseAuditLog({
      adminId: adminAuth.userId,
      adminEmail: adminAuth.email,
      action: `USER_${normalizedAction}`,
      targetType: 'profiles',
      targetId: userId,
      details: { action: normalizedAction, reason: safeReason || null, durationHours: safeHours },
      callerJwt: adminAuth.token
    });

    sendJson(res, 200, {
      success: true,
      message: `Account status set to ${normalizedAction} for ${username || userId}`,
      userId,
      action: normalizedAction
    });
    return;
  }

  // 5. CONTENT MODERATION ACTION (DELETE / ARCHIVE / RESTORE ON SUPABASE)
  if (cleanUrl === '/api/v1/admin/content-action' && req.method === 'POST') {
    const body = await readJsonBody(req);
    const { contentId, action } = body;
    if (!contentId || !UUID_RE.test(String(contentId))) {
      sendJson(res, 400, { error: 'Valid UUID contentId required.' });
      return;
    }

    const actionUpper = String(action || '').toUpperCase();
    let dbResp = null;
    if (actionUpper === 'DELETE' || actionUpper === 'REMOVE') {
      dbResp = await fetch(`${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/posts?id=eq.${encodeURIComponent(contentId)}`, {
        method: 'DELETE',
        headers: buildSupabaseHeaders({}, true, adminAuth.token)
      });
    } else if (actionUpper === 'ARCHIVE' || actionUpper === 'HIDE') {
      dbResp = await fetch(`${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/posts?id=eq.${encodeURIComponent(contentId)}`, {
        method: 'PATCH',
        headers: buildSupabaseHeaders({ 'Content-Type': 'application/json' }, true, adminAuth.token),
        body: JSON.stringify({ is_archived: true })
      });
    } else if (actionUpper === 'RESTORE' || actionUpper === 'SHOW') {
      dbResp = await fetch(`${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/posts?id=eq.${encodeURIComponent(contentId)}`, {
        method: 'PATCH',
        headers: buildSupabaseHeaders({ 'Content-Type': 'application/json' }, true, adminAuth.token),
        body: JSON.stringify({ is_archived: false })
      });
    } else {
      sendJson(res, 400, { error: 'Unsupported content moderation action.' });
      return;
    }

    if (!dbResp || !dbResp.ok) {
      sendJson(res, dbResp?.status || 502, { error: 'Failed applying content moderation action in Supabase.' });
      return;
    }

    await recordSupabaseAuditLog({
      adminId: adminAuth.userId,
      adminEmail: adminAuth.email,
      action: `POST_${actionUpper}`,
      targetType: 'posts',
      targetId: String(contentId),
      details: { action: actionUpper },
      callerJwt: adminAuth.token
    });

    relayRealtimeBroadcast('vybe_feed_updates', { event: actionUpper === 'RESTORE' || actionUpper === 'SHOW' ? 'POST_RESTORED' : 'POST_REMOVED', postId: contentId });

    sendJson(res, 200, { success: true, contentId, action: actionUpper });
    return;
  }

  // 6a. TRUST & SAFETY REPORTS QUEUE (Live Supabase public.reports)
  if (cleanUrl === '/api/v1/admin/reports' && req.method === 'GET') {
    let resp;
    try {
      resp = await fetch(
        `${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/reports?select=id,reporter_id,target_type,target_id,reason,status,admin_notes,created_at,reporter:reporter_id(id,username,display_name)&order=created_at.desc&limit=100`,
        { headers: buildSupabaseHeaders({}, true, adminAuth.token) }
      );
    } catch (err) {
      sendJson(res, 502, { error: 'Failed communicating with Supabase reports table: ' + err.message });
      return;
    }

    if (!resp.ok) {
      sendJson(res, resp.status >= 400 ? resp.status : 502, { error: 'Failed fetching reports from Supabase.' });
      return;
    }

    const rows = await resp.json();
    sendJson(res, 200, { success: true, reports: Array.isArray(rows) ? rows : [] });
    return;
  }

  // 6. REPORT ACTION (Persisted in Supabase public.reports + audit log)
  if (cleanUrl === '/api/v1/admin/report-action' && req.method === 'POST') {
    const body = await readJsonBody(req);
    const { reportId, action, notes } = body;
    if (!reportId || !UUID_RE.test(String(reportId))) {
      sendJson(res, 400, { error: 'Valid UUID reportId is required.' });
      return;
    }

    const rawAction = String(action || '').toUpperCase().trim();
    const STATUS_MAP = {
      OPEN: 'OPEN',
      INVESTIGATE: 'INVESTIGATING',
      INVESTIGATING: 'INVESTIGATING',
      RESOLVE: 'RESOLVED',
      RESOLVED: 'RESOLVED',
      APPROVE: 'RESOLVED',
      DISMISS: 'DISMISSED',
      DISMISSED: 'DISMISSED',
      REJECT: 'DISMISSED'
    };
    const mappedStatus = STATUS_MAP[rawAction];
    if (!mappedStatus) {
      sendJson(res, 400, { error: 'action must map to OPEN, INVESTIGATING, RESOLVED, or DISMISSED.' });
      return;
    }

    let dbResp = null;
    try {
      dbResp = await fetch(`${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/reports?id=eq.${encodeURIComponent(reportId)}`, {
        method: 'PATCH',
        headers: buildSupabaseHeaders({
          'Content-Type': 'application/json',
          'Prefer': 'return=representation'
        }, true, adminAuth.token),
        body: JSON.stringify({
          status: mappedStatus,
          admin_notes: notes ? String(notes).slice(0, 1000) : null
        })
      });
    } catch (err) {
      sendJson(res, 502, { error: 'Failed communicating with Supabase reports table: ' + err.message });
      return;
    }

    if (!dbResp || !dbResp.ok) {
      sendJson(res, dbResp?.status || 502, { error: 'Failed updating report status in Supabase database.' });
      return;
    }

    await recordSupabaseAuditLog({
      adminId: adminAuth.userId,
      adminEmail: adminAuth.email,
      action: `REPORT_${mappedStatus}`,
      targetType: 'reports',
      targetId: String(reportId),
      details: { status: mappedStatus, notes: notes || null },
      callerJwt: adminAuth.token
    });

    sendJson(res, 200, { success: true, reportId, status: mappedStatus, action: mappedStatus });
    return;
  }

  // 6b. VERIFICATION REQUESTS QUEUE & REVIEW (Persisted via RPC + PII stripped)
  if (cleanUrl === '/api/v1/admin/verification-requests' && req.method === 'GET') {
    try {
      const resp = await fetch(
        `${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/verification_requests?select=id,user_id,request_type,full_name,category,identity_document_url,supporting_links,status,rejection_reason,reviewed_by,reviewed_at,created_at,updated_at,profiles:user_id(id,username,display_name,avatar_url)&order=created_at.desc&limit=100`,
        { headers: buildSupabaseHeaders({}, true, adminAuth.token) }
      );
      if (!resp.ok) {
        sendJson(res, resp.status, { error: 'Failed fetching verification requests from Supabase.' });
        return;
      }
      const requests = await resp.json();
      sendJson(res, 200, { success: true, requests: Array.isArray(requests) ? requests : [] });
    } catch (err) {
      sendJson(res, 502, { error: 'Error communicating with Supabase verification queue: ' + err.message });
    }
    return;
  }

  if (cleanUrl === '/api/v1/admin/verification-requests/review' && req.method === 'POST') {
    const body = await readJsonBody(req);
    const requestId = body.requestId || body.id;
    const decision = String(body.decision || '').toUpperCase().trim();
    const notes = body.notes ? String(body.notes) : null;
    const vipExpiresAt = body.vipExpiresAt || null;

    if (!requestId || !UUID_RE.test(String(requestId))) {
      sendJson(res, 400, { error: 'Valid UUID requestId is required.' });
      return;
    }
    if (!['APPROVED', 'REJECTED', 'MORE_INFO_REQUIRED'].includes(decision)) {
      sendJson(res, 400, { error: 'decision must be APPROVED, REJECTED, or MORE_INFO_REQUIRED.' });
      return;
    }

    try {
      const rpcResp = await fetch(`${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/rpc/admin_review_verification_request`, {
        method: 'POST',
        headers: buildSupabaseHeaders({ 'Content-Type': 'application/json' }, true, adminAuth.token),
        body: JSON.stringify({
          p_request_id: requestId,
          p_decision: decision,
          p_notes: notes,
          p_admin_id: adminAuth.userId,
          p_vip_expires_at: vipExpiresAt
        })
      });

      if (!rpcResp.ok) {
        const errText = await rpcResp.text().catch(() => '');
        const status = (rpcResp.status === 401 || rpcResp.status === 403) ? rpcResp.status : 502;
        sendJson(res, status, {
          error: 'Supabase admin_review_verification_request RPC failed',
          details: errText.slice(0, 200)
        });
        return;
      }

      const result = await rpcResp.json().catch(() => ({ success: true, status: decision }));
      sendJson(res, 200, { success: true, requestId, decision, result });
    } catch (err) {
      sendJson(res, 502, { error: 'Failed communicating with Supabase verification RPC: ' + err.message });
    }
    return;
  }

  // 7. ADS MANAGEMENT (Live Supabase backed when table exists; zero seeded mock campaigns)
  if (cleanUrl === '/api/v1/admin/ads' && req.method === 'GET') {
    try {
      const resp = await fetch(
        `${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/sponsored_campaigns?select=id,name,brand,placement,headline,media_url,cta_text,cta_url,impressions,target_impressions,budget,status,created_at&order=created_at.desc&limit=100`,
        { headers: buildSupabaseHeaders({}, true, adminAuth.token) }
      );
      if (resp.status === 404) {
        sendJson(res, 200, { success: true, ads: [], supported: false });
        return;
      }
      if (!resp.ok) {
        sendJson(res, resp.status >= 400 ? resp.status : 502, { error: 'Failed fetching sponsorship campaigns from Supabase.' });
        return;
      }
      const rows = await resp.json();
      const ads = (Array.isArray(rows) ? rows : []).map(a => ({
        id: a.id,
        name: a.name,
        brand: a.brand,
        placement: a.placement || 'FEED_SPONSORED_POST',
        headline: a.headline || '',
        mediaUrl: a.media_url || '',
        ctaText: a.cta_text || '',
        ctaUrl: a.cta_url || '',
        impressions: Number(a.impressions || 0),
        targetImpressions: Number(a.target_impressions || 0),
        budget: a.budget || '',
        status: String(a.status || 'ACTIVE').toUpperCase(),
        createdAt: a.created_at
      }));
      sendJson(res, 200, { success: true, ads, supported: true });
    } catch (err) {
      sendJson(res, 502, { error: 'Failed communicating with Supabase campaigns table: ' + err.message });
    }
    return;
  }

  if (cleanUrl === '/api/v1/admin/ads/create' && req.method === 'POST') {
    const body = await readJsonBody(req);
    const { name, brand, placement, headline, mediaUrl, ctaText, ctaUrl, targetImpressions, budget } = body;
    if (!name || !brand) {
      sendJson(res, 400, { error: 'Campaign name and brand are required.' });
      return;
    }

    try {
      const resp = await fetch(`${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/sponsored_campaigns`, {
        method: 'POST',
        headers: buildSupabaseHeaders({ 'Content-Type': 'application/json', 'Prefer': 'return=representation' }, true, adminAuth.token),
        body: JSON.stringify({
          name: String(name),
          brand: String(brand),
          placement: placement || 'FEED_SPONSORED_POST',
          headline: headline || '',
          media_url: mediaUrl || null,
          cta_text: ctaText || 'Learn More',
          cta_url: ctaUrl || null,
          impressions: 0,
          target_impressions: Number(targetImpressions) || 0,
          budget: budget || '',
          status: 'ACTIVE'
        })
      });
      if (resp.status === 404) {
        sendJson(res, 501, { error: 'Sponsored campaigns table is not provisioned in Supabase.' });
        return;
      }
      if (!resp.ok) {
        sendJson(res, resp.status >= 400 ? resp.status : 502, { error: 'Failed creating sponsored campaign in Supabase.' });
        return;
      }
      const created = await resp.json();
      const ad = Array.isArray(created) ? created[0] : created;
      await recordSupabaseAuditLog({
        adminId: adminAuth.userId,
        adminEmail: adminAuth.email,
        action: 'AD_CAMPAIGN_CREATE',
        targetType: 'sponsored_campaigns',
        targetId: String(ad?.id || name),
        details: { name, brand },
        callerJwt: adminAuth.token
      });
      sendJson(res, 200, { success: true, ad });
    } catch (err) {
      sendJson(res, 502, { error: 'Failed creating sponsored campaign: ' + err.message });
    }
    return;
  }

  if (cleanUrl === '/api/v1/admin/ads/toggle' && req.method === 'POST') {
    const body = await readJsonBody(req);
    const { adId } = body;
    if (!adId) {
      sendJson(res, 400, { error: 'adId is required.' });
      return;
    }
    try {
      const curResp = await fetch(
        `${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/sponsored_campaigns?id=eq.${encodeURIComponent(adId)}&select=id,status&limit=1`,
        { headers: buildSupabaseHeaders({}, true, adminAuth.token) }
      );
      if (curResp.status === 404) {
        sendJson(res, 501, { error: 'Sponsored campaigns table is not provisioned in Supabase.' });
        return;
      }
      if (!curResp.ok) {
        sendJson(res, curResp.status, { error: 'Failed looking up sponsored campaign.' });
        return;
      }
      const rows = await curResp.json();
      const cur = Array.isArray(rows) ? rows[0] : rows;
      if (!cur) {
        sendJson(res, 404, { error: 'Ad campaign not found' });
        return;
      }
      const nextStatus = String(cur.status || '').toUpperCase() === 'ACTIVE' ? 'PAUSED' : 'ACTIVE';
      const updResp = await fetch(`${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/sponsored_campaigns?id=eq.${encodeURIComponent(adId)}`, {
        method: 'PATCH',
        headers: buildSupabaseHeaders({ 'Content-Type': 'application/json' }, true, adminAuth.token),
        body: JSON.stringify({ status: nextStatus })
      });
      if (!updResp.ok) {
        sendJson(res, updResp.status, { error: 'Failed updating sponsored campaign status.' });
        return;
      }
      sendJson(res, 200, { success: true, status: nextStatus });
    } catch (err) {
      sendJson(res, 502, { error: 'Failed updating sponsored campaign: ' + err.message });
    }
    return;
  }

  if (cleanUrl === '/api/v1/admin/ads/delete' && req.method === 'POST') {
    const body = await readJsonBody(req);
    const { adId } = body;
    if (!adId) {
      sendJson(res, 400, { error: 'adId is required.' });
      return;
    }
    try {
      const delResp = await fetch(`${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/sponsored_campaigns?id=eq.${encodeURIComponent(adId)}`, {
        method: 'DELETE',
        headers: buildSupabaseHeaders({}, true, adminAuth.token)
      });
      if (delResp.status === 404) {
        sendJson(res, 501, { error: 'Sponsored campaigns table is not provisioned in Supabase.' });
        return;
      }
      if (!delResp.ok) {
        sendJson(res, delResp.status, { error: 'Failed deleting sponsored campaign.' });
        return;
      }
      sendJson(res, 200, { success: true });
    } catch (err) {
      sendJson(res, 502, { error: 'Failed deleting sponsored campaign: ' + err.message });
    }
    return;
  }

  // 8. AUDIT LOGS (LIVE SUPABASE admin_audit_logs ONLY)
  if (cleanUrl === '/api/v1/admin/audit-logs' && req.method === 'GET') {
    let resp;
    try {
      resp = await fetch(
        `${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/admin_audit_logs?select=id,admin_id,admin_email,action,target_type,target_id,details,created_at&order=created_at.desc&limit=100`,
        { headers: buildSupabaseHeaders({}, true, adminAuth.token) }
      );
    } catch (err) {
      sendJson(res, 502, { error: 'Failed fetching audit logs from Supabase: ' + err.message });
      return;
    }

    if (!resp.ok) {
      sendJson(res, resp.status >= 400 ? resp.status : 502, { error: 'Failed fetching audit logs from Supabase.' });
      return;
    }

    const rows = await resp.json();
    const logs = (Array.isArray(rows) ? rows : []).map(r => ({
      id: r.id,
      timestamp: r.created_at,
      adminId: r.admin_id || '',
      adminEmail: r.admin_email || 'admin',
      action: r.action || 'ACTION',
      targetType: r.target_type || 'SYSTEM',
      targetId: r.target_id || '',
      details: r.details || {}
    }));
    sendJson(res, 200, { success: true, logs });
    return;
  }

  // 9. OVERVIEW STATS (REAL AGGREGATION FROM SUPABASE ONLY — ZERO SYNTHETIC ESTIMATES)
  if (cleanUrl === '/api/v1/admin/overview-stats' && req.method === 'GET') {
    let totalUsers = 0;
    let newUsersToday = 0;
    let newUsersWeek = 0;
    let bannedCount = 0;
    let suspendedCount = 0;
    let verifiedCount = 0;
    let vipCount = 0;
    let officialCount = 0;
    let dauCount = 0;
    let wauCount = 0;
    let mauCount = 0;
    let totalPosts = 0;
    let reelsCount = 0;
    let unfilteredCount = 0;
    let totalLikes = 0;
    let totalComments = 0;
    let totalShares = 0;
    let totalStories = 0;
    let totalReports = 0;
    let pendingReports = 0;
    let liveRoomsCount = 0;
    let pollsCount = 0;
    let activeDeviceTokens = 0;

    const now = Date.now();
    const oneDayAgo = new Date(now - 24 * 3600 * 1000).toISOString();
    const sevenDaysAgo = new Date(now - 7 * 24 * 3600 * 1000).toISOString();
    const thirtyDaysAgo = new Date(now - 30 * 24 * 3600 * 1000).toISOString();
    const hdrs = buildSupabaseHeaders({}, true, adminAuth.token);
    const base = SUPABASE_URL.replace(/\/$/, '');

    try {
      const pResp = await fetch(`${base}/rest/v1/profiles?select=id,is_verified,is_vip,is_worker,is_banned,is_suspended,created_at,last_seen_at&limit=1000`, { headers: hdrs });
      if (pResp.ok) {
        const users = await pResp.json();
        if (Array.isArray(users)) {
          totalUsers = users.length;
          users.forEach(u => {
            if (u.is_banned) bannedCount++;
            if (u.is_suspended) suspendedCount++;
            if (u.is_verified) verifiedCount++;
            if (u.is_vip) vipCount++;
            if (u.is_worker) officialCount++;

            if (u.created_at) {
              if (u.created_at >= oneDayAgo) newUsersToday++;
              if (u.created_at >= sevenDaysAgo) newUsersWeek++;
            }
            if (u.last_seen_at) {
              if (u.last_seen_at >= oneDayAgo) dauCount++;
              if (u.last_seen_at >= sevenDaysAgo) wauCount++;
              if (u.last_seen_at >= thirtyDaysAgo) mauCount++;
            }
          });
        }
      }
    } catch {}

    try {
      const postResp = await fetch(`${base}/rest/v1/posts?select=id,is_vybes_reel,is_unfiltered,likes_count,comments_count,shares_count&limit=1000`, { headers: hdrs });
      if (postResp.ok) {
        const posts = await postResp.json();
        if (Array.isArray(posts)) {
          totalPosts = posts.length;
          posts.forEach(p => {
            if (p.is_vybes_reel) reelsCount++;
            if (p.is_unfiltered) unfilteredCount++;
            totalLikes += Number(p.likes_count || 0);
            totalComments += Number(p.comments_count || 0);
            totalShares += Number(p.shares_count || 0);
          });
        }
      }
    } catch {}

    try {
      const sResp = await fetch(`${base}/rest/v1/stories?select=id&limit=200`, { headers: hdrs });
      if (sResp.ok) {
        const stories = await sResp.json();
        if (Array.isArray(stories)) totalStories = stories.length;
      }
    } catch {}

    try {
      const repResp = await fetch(`${base}/rest/v1/reports?select=id,status&limit=500`, { headers: hdrs });
      if (repResp.ok) {
        const reps = await repResp.json();
        if (Array.isArray(reps)) {
          totalReports = reps.length;
          pendingReports = reps.filter(r => (r.status || 'OPEN').toUpperCase() === 'OPEN').length;
        }
      }
    } catch {}

    try {
      const [roomsResp, pollsResp, tokResp] = await Promise.all([
        fetch(`${base}/rest/v1/rooms?is_active=eq.true&select=id&limit=200`, { headers: hdrs }).catch(() => null),
        fetch(`${base}/rest/v1/audience_pools?select=id&limit=200`, { headers: hdrs }).catch(() => null),
        fetch(`${base}/rest/v1/user_device_tokens?is_active=eq.true&select=id&limit=1000`, { headers: hdrs }).catch(() => null)
      ]);
      if (roomsResp && roomsResp.ok) {
        const r = await roomsResp.json();
        if (Array.isArray(r)) liveRoomsCount = r.length;
      }
      if (pollsResp && pollsResp.ok) {
        const pl = await pollsResp.json();
        if (Array.isArray(pl)) pollsCount = pl.length;
      }
      if (tokResp && tokResp.ok) {
        const t = await tokResp.json();
        if (Array.isArray(t)) activeDeviceTokens = t.length;
      }
    } catch {}

    const memDeviceCount = Array.from(deviceRegistry.values()).filter(d => d.is_active).length;
    const effectiveDeviceCount = activeDeviceTokens > 0 ? activeDeviceTokens : memDeviceCount;

    sendJson(res, 200, {
      success: true,
      adminRole: adminAuth.role,
      isSuperAdmin: adminAuth.isSuperAdmin,
      stats: {
        users: {
          total: totalUsers,
          dau: dauCount,
          wau: wauCount,
          mau: mauCount,
          newToday: newUsersToday,
          newWeek: newUsersWeek,
          banned: bannedCount,
          suspended: suspendedCount,
          verified: verifiedCount,
          vip: vipCount,
          official: officialCount
        },
        content: {
          totalPosts,
          reels: reelsCount,
          unfiltered: unfilteredCount,
          standardPosts: Math.max(0, totalPosts - reelsCount - unfilteredCount),
          stories: totalStories,
          totalLikes,
          totalComments,
          totalShares
        },
        experiences: {
          liveRooms: liveRoomsCount,
          polls: pollsCount
        },
        moderation: {
          totalReports,
          pendingReports
        },
        push: {
          activeDevices: effectiveDeviceCount,
          recentPushes: recentPushLogs.length
        },
        systemHealth: {
          supabase: { status: 'HEALTHY', url: SUPABASE_URL },
          fcm: { status: getFirebaseServiceAccount() ? 'CONFIGURED' : 'STANDBY', registeredDevices: effectiveDeviceCount },
          realtime: { status: 'LIVE', channel: 'vybe_broadcast_high_v2' },
          gateway: { status: 'ONLINE', uptime: Math.floor(process.uptime()), port: PORT }
        }
      }
    });
    return;
  }

  // 10. CONTENT MANAGEMENT & MODERATION (LIVE SUPABASE POSTS ONLY)
  if (cleanUrl === '/api/v1/admin/content' && req.method === 'GET') {
    let resp;
    try {
      resp = await fetch(
        `${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/posts?select=id,author_id,caption,media_type,media_urls,is_vybes_reel,is_unfiltered,is_archived,likes_count,comments_count,shares_count,created_at,profiles:author_id(username,display_name,avatar_url)&order=created_at.desc&limit=60`,
        { headers: buildSupabaseHeaders({}, true, adminAuth.token) }
      );
    } catch (err) {
      sendJson(res, 502, { error: 'Failed communicating with Supabase posts table: ' + err.message });
      return;
    }

    if (!resp.ok) {
      sendJson(res, resp.status >= 400 ? resp.status : 502, { error: 'Failed fetching posts from Supabase.' });
      return;
    }

    const rawPosts = await resp.json();
    const posts = (Array.isArray(rawPosts) ? rawPosts : []).map(p => {
      const authorObj = p.profiles || {};
      const isHidden = Boolean(p.is_archived);
      return {
        id: p.id,
        author_id: p.author_id,
        author_name: authorObj.display_name || authorObj.username || 'VYBE Member',
        author_username: authorObj.username || 'unknown',
        author_avatar: authorObj.avatar_url || null,
        caption: p.caption || '',
        media_urls: p.media_urls || [],
        media_type: p.media_type || (p.is_vybes_reel ? 'VIDEO' : 'PHOTO'),
        is_vybes_reel: Boolean(p.is_vybes_reel),
        is_unfiltered: Boolean(p.is_unfiltered),
        likes_count: Number(p.likes_count || 0),
        comments_count: Number(p.comments_count || 0),
        shares_count: Number(p.shares_count || 0),
        created_at: p.created_at,
        is_hidden: isHidden,
        moderation_status: isHidden ? 'HIDDEN' : 'APPROVED'
      };
    });

    sendJson(res, 200, { success: true, posts, total: posts.length });
    return;
  }

  // 11. 6 VYBE EXPERIENCES MANAGEMENT (REAL SUPABASE RECORDS ONLY — ZERO SAMPLE ROOMS/POLLS/NUMBERS)
  if (cleanUrl === '/api/v1/admin/experiences' && req.method === 'GET') {
    const configState = loadPlatformConfigState();
    const hdrs = buildSupabaseHeaders({}, true, adminAuth.token);
    const base = SUPABASE_URL.replace(/\/$/, '');

    let activeRooms = [];
    let unfilteredCount = 0;
    let activePolls = [];
    let capsulesCount = 0;
    let doubleTakesCount = 0;
    let dailyPrompt = null;
    let crossedCount = 0;

    try {
      const [rResp, uResp, pollResp, cResp, dtResp, cpResp] = await Promise.all([
        fetch(`${base}/rest/v1/rooms?is_active=eq.true&select=id,title,topic_tag,host_id,created_at,profiles:host_id(username,display_name)&order=created_at.desc&limit=50`, { headers: hdrs }).catch(() => null),
        fetch(`${base}/rest/v1/posts?is_unfiltered=eq.true&select=id&limit=1000`, { headers: hdrs }).catch(() => null),
        fetch(`${base}/rest/v1/audience_pools?select=id,title,category,closes_at,total_votes,status&order=created_at.desc&limit=50`, { headers: hdrs }).catch(() => null),
        fetch(`${base}/rest/v1/time_capsules?select=id&limit=1000`, { headers: hdrs }).catch(() => null),
        fetch(`${base}/rest/v1/double_takes?select=id,prompt_question,created_at&order=created_at.desc&limit=100`, { headers: hdrs }).catch(() => null),
        fetch(`${base}/rest/v1/crossed_paths?select=id&limit=1000`, { headers: hdrs }).catch(() => null)
      ]);

      if (rResp && rResp.ok) {
        const rList = await rResp.json();
        if (Array.isArray(rList)) activeRooms = rList;
      }
      if (uResp && uResp.ok) {
        const uList = await uResp.json();
        if (Array.isArray(uList)) unfilteredCount = uList.length;
      }
      if (pollResp && pollResp.ok) {
        const pList = await pollResp.json();
        if (Array.isArray(pList)) activePolls = pList;
      }
      if (cResp && cResp.ok) {
        const cList = await cResp.json();
        if (Array.isArray(cList)) capsulesCount = cList.length;
      }
      if (dtResp && dtResp.ok) {
        const dtList = await dtResp.json();
        if (Array.isArray(dtList)) {
          doubleTakesCount = dtList.length;
          dailyPrompt = dtList[0]?.prompt_question || null;
        }
      }
      if (cpResp && cpResp.ok) {
        const cpList = await cpResp.json();
        if (Array.isArray(cpList)) crossedCount = cpList.length;
      }
    } catch {}

    sendJson(res, 200, {
      success: true,
      featureFlags: configState.featureFlags,
      experiences: {
        unload: {
          name: "UNLOAD",
          title: "24/7 Live Listening & Voice Lounges",
          accent: "#8B5CF6",
          activeRooms,
          totalActiveRooms: activeRooms.length,
          privacyGuarantee: "Zero audio recording. 100% ephemeral."
        },
        unfiltered: {
          name: "UNFILTERED",
          title: "Raw & Real Moments, Zero Filters",
          accent: "#22C55E",
          snapshotsCount: unfilteredCount,
          unfilteredPrivacy: "No beauty algorithms. Front & back cam authentic reality."
        },
        whatNext: {
          name: "WHAT NEXT",
          title: "Culture Pulse & Decision Polls",
          accent: "#00F0FF",
          polls: activePolls,
          totalActivePolls: activePolls.filter(p => String(p.status || 'ACTIVE').toUpperCase() === 'ACTIVE').length
        },
        timeCapsule: {
          name: "TIME CAPSULE",
          title: "Cryptographically Sealed Memory Vaults",
          accent: "#FFB703",
          sealedCount: capsulesCount,
          privacyGuarantee: "Encrypted memory vaults. Zero staff message inspection."
        },
        doubleTake: {
          name: "DOUBLE TAKE",
          title: "Two Sides, Opposing Perspectives",
          accent: "#EF4444",
          activePromptsCount: doubleTakesCount,
          dailyPrompt
        },
        crossedPaths: {
          name: "CROSSED PATHS",
          title: "Hyperlocal Proximity Radar & Encounters",
          accent: "#3B82F6",
          signalsCount: crossedCount,
          privacyGuarantee: "Broad venue proximity only. Exact GPS coordinates strictly hidden."
        }
      }
    });
    return;
  }

  // 12. TOGGLE EXPERIENCE FEATURE FLAG
  if (cleanUrl === '/api/v1/admin/experiences/toggle' && req.method === 'POST') {
    const body = await readJsonBody(req);
    const { experienceKey, enabled } = body;
    if (!experienceKey) {
      sendJson(res, 400, { error: 'experienceKey required' });
      return;
    }
    const cleanKey = String(experienceKey).trim();
    const configState = loadPlatformConfigState();
    configState.featureFlags = configState.featureFlags || {};
    configState.featureFlags[cleanKey] = Boolean(enabled);
    savePlatformConfigState(configState);

    await recordSupabaseAuditLog({
      adminId: adminAuth.userId,
      adminEmail: adminAuth.email,
      action: `EXPERIENCE_TOGGLE_${cleanKey.toUpperCase()}`,
      targetType: 'PLATFORM_CONFIG',
      targetId: cleanKey,
      details: { experience: cleanKey, enabled: Boolean(enabled) },
      callerJwt: adminAuth.token
    });

    sendJson(res, 200, { success: true, experienceKey: cleanKey, enabled: Boolean(enabled) });
    return;
  }

  // 13. PLATFORM CONFIGURATION (MAINTENANCE MODE, ANNOUNCEMENT)
  if (cleanUrl === '/api/v1/admin/platform-config' && req.method === 'GET') {
    const configState = loadPlatformConfigState();
    sendJson(res, 200, {
      success: true,
      config: configState.platformConfig,
      featureFlags: configState.featureFlags
    });
    return;
  }

  if (cleanUrl === '/api/v1/admin/platform-config' && req.method === 'POST') {
    const body = await readJsonBody(req);
    const { maintenanceMode, maintenanceMessage, announcementBanner, announcementActive, announcementLink } = body;
    const configState = loadPlatformConfigState();
    configState.platformConfig = configState.platformConfig || {};
    if (maintenanceMode !== undefined) configState.platformConfig.maintenanceMode = Boolean(maintenanceMode);
    if (maintenanceMessage !== undefined) configState.platformConfig.maintenanceMessage = String(maintenanceMessage);
    if (announcementBanner !== undefined) configState.platformConfig.announcementBanner = String(announcementBanner);
    if (announcementActive !== undefined) configState.platformConfig.announcementActive = Boolean(announcementActive);
    if (announcementLink !== undefined) configState.platformConfig.announcementLink = String(announcementLink);

    savePlatformConfigState(configState);

    await recordSupabaseAuditLog({
      adminId: adminAuth.userId,
      adminEmail: adminAuth.email,
      action: 'UPDATE_PLATFORM_CONFIG',
      targetType: 'PLATFORM_CONFIG',
      targetId: 'GLOBAL',
      details: { config: configState.platformConfig },
      callerJwt: adminAuth.token
    });

    sendJson(res, 200, { success: true, config: configState.platformConfig });
    return;
  }

  // 14. ADMIN STAFF & ROLES DIRECTORY (Active public.admin_users records only)
  if (cleanUrl === '/api/v1/admin/staff' && req.method === 'GET') {
    let resp;
    try {
      resp = await fetch(
        `${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/admin_users?is_active=eq.true&select=user_id,email,role,is_active,created_at,profiles:user_id(id,username,display_name,avatar_url)&order=created_at.desc&limit=200`,
        { headers: buildSupabaseHeaders({}, true, adminAuth.token) }
      );
    } catch (err) {
      sendJson(res, 502, { error: 'Failed communicating with Supabase admin_users table: ' + err.message });
      return;
    }

    if (!resp.ok) {
      sendJson(res, resp.status >= 400 ? resp.status : 502, { error: 'Failed fetching admin staff from Supabase.' });
      return;
    }

    const rows = await resp.json();
    const staff = (Array.isArray(rows) ? rows : [])
      .filter(r => r.is_active === true)
      .map(r => {
        const p = r.profiles || {};
        const assignedRole = String(r.role || 'MODERATOR').toUpperCase();
        return {
          id: r.user_id || p.id,
          username: p.username || r.email || 'admin',
          display_name: p.display_name || p.username || r.email || 'Administrator',
          email: r.email || '',
          role: assignedRole,
          is_owner: SUPER_ADMIN_ROLES.has(assignedRole),
          avatar_url: p.avatar_url || null,
          created_at: r.created_at
        };
      });

    sendJson(res, 200, { success: true, staff, total: staff.length });
    return;
  }

  // 15. CSV DATA EXPORT FOR AUDIT & COMPLIANCE (Live Supabase data only; PII stripped)
  if (cleanUrl.startsWith('/api/v1/admin/export/')) {
    const type = cleanUrl.replace('/api/v1/admin/export/', '').toLowerCase().trim();
    const csvCell = (v) => `"${String(v ?? '').replace(/"/g, '""')}"`;

    if (type === 'audit-logs' || type === 'audit') {
      let logs = [];
      try {
        const resp = await fetch(
          `${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/admin_audit_logs?select=created_at,action,admin_email,target_type,target_id,details&order=created_at.desc&limit=500`,
          { headers: buildSupabaseHeaders({}, true, adminAuth.token) }
        );
        if (!resp.ok) {
          sendJson(res, resp.status >= 400 ? resp.status : 502, { error: 'Failed exporting audit logs from Supabase.' });
          return;
        }
        const rows = await resp.json();
        if (Array.isArray(rows)) logs = rows;
      } catch (err) {
        sendJson(res, 502, { error: 'Failed communicating with Supabase audit logs: ' + err.message });
        return;
      }

      let csv = 'Timestamp,Action,Admin,Target_Type,Target_ID,Details\n';
      logs.forEach(l => {
        csv += [
          csvCell(l.created_at),
          csvCell(l.action),
          csvCell(l.admin_email),
          csvCell(l.target_type),
          csvCell(l.target_id),
          csvCell(JSON.stringify(l.details || {}))
        ].join(',') + '\n';
      });
      res.writeHead(200, {
        'Content-Type': 'text/csv; charset=utf-8',
        'Content-Disposition': 'attachment; filename="vybe-audit-trail.csv"'
      });
      res.end(csv);
      return;
    }

    if (type === 'users') {
      let users = [];
      try {
        const resp = await fetch(
          `${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/profiles?select=id,username,display_name,role,is_verified,is_vip,is_worker,is_banned,created_at&limit=500`,
          { headers: buildSupabaseHeaders({}, true, adminAuth.token) }
        );
        if (!resp.ok) {
          sendJson(res, resp.status >= 400 ? resp.status : 502, { error: 'Failed exporting users from Supabase.' });
          return;
        }
        const rows = await resp.json();
        if (Array.isArray(rows)) users = rows;
      } catch (err) {
        sendJson(res, 502, { error: 'Failed communicating with Supabase profiles: ' + err.message });
        return;
      }
      let csv = 'ID,Username,Display_Name,Role,Verified,VIP,Official,Banned,Joined\n';
      users.forEach(u => {
        csv += [
          csvCell(u.id),
          csvCell(u.username),
          csvCell(u.display_name),
          csvCell(u.role),
          csvCell(Boolean(u.is_verified)),
          csvCell(Boolean(u.is_vip)),
          csvCell(Boolean(u.is_worker)),
          csvCell(Boolean(u.is_banned)),
          csvCell(u.created_at)
        ].join(',') + '\n';
      });
      res.writeHead(200, {
        'Content-Type': 'text/csv; charset=utf-8',
        'Content-Disposition': 'attachment; filename="vybe-users-directory.csv"'
      });
      res.end(csv);
      return;
    }
  }

  // Canonical Profile QR & Share Fallback Page (/profile/:id or /u/:id)
  if ((cleanUrl.startsWith('/profile/') || cleanUrl.startsWith('/u/')) && req.method === 'GET') {
    const rawSegment = decodeURIComponent(cleanUrl.replace(/^\/(profile|u)\//, '').split('/')[0] || '').trim();
    const parsedUrl = new URL(req.url || '/', 'http://localhost');
    const uHint = (parsedUrl.searchParams.get('u') || parsedUrl.searchParams.get('username') || '').replace(/^@/, '').trim();
    let profileRow = null;
    try {
      if (rawSegment) {
        const isUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(rawSegment);
        const query = isUuid
          ? `id=eq.${encodeURIComponent(rawSegment)}`
          : `username=ilike.${encodeURIComponent(rawSegment.replace(/^@/, ''))}`;
        const pResp = await fetch(`${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/profiles?${query}&select=id,username,display_name,bio,avatar_url,is_private_account,is_verified,is_vip,vip_expires_at,is_verified_business,is_worker,badge_display_status,badge_approval_status,followers_count,posts_count&limit=1`, {
          headers: { 'apikey': SUPABASE_ANON_KEY }
        });
        if (pResp.ok) {
          const rows = await pResp.json();
          profileRow = Array.isArray(rows) && rows.length > 0 ? rows[0] : null;
        }
      }
    } catch {}

    const esc = (s) => String(s || '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
    if (!profileRow) {
      res.writeHead(404, { 'Content-Type': 'text/html; charset=utf-8' });
      res.end(`<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Profile Unavailable • VYBE</title>
      <style>body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#070913;color:#fff;font-family:system-ui,-apple-system,sans-serif;padding:24px}.card{max-width:400px;width:100%;background:#111729;border:1px solid #263252;border-radius:24px;padding:32px;text-align:center}</style></head>
      <body><div class="card"><div style="font-size:38px;margin-bottom:12px">⚠️</div><h1 style="font-size:20px;margin:0 0 8px">Profile Unavailable</h1><p style="color:#94a3b8;font-size:14px;line-height:1.5">This VYBE profile (${esc(uHint ? '@' + uHint : rawSegment)}) could not be found or is no longer active.</p></div></body></html>`);
      return;
    }

    const appDeepLink = `vybe://profile/${encodeURIComponent(profileRow.id)}?u=${encodeURIComponent(profileRow.username || '')}`;
    const isPrivate = Boolean(profileRow.is_private_account);
    const bioText = isPrivate ? 'This account is private. LINK UP in the VYBE app to see their posts and reels.' : (profileRow.bio || 'Official member on VYBE.');
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(`<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${esc(profileRow.display_name || profileRow.username)} (@${esc(profileRow.username)}) • VYBE</title>
    <style>body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#070913;color:#fff;font-family:system-ui,-apple-system,sans-serif;padding:24px}.card{max-width:420px;width:100%;background:#111729;border:1px solid #263252;border-radius:24px;padding:32px;text-align:center}.btn{display:inline-block;margin-top:20px;padding:13px 24px;border-radius:14px;background:linear-gradient(90deg,#2563eb,#7c3aed);color:#fff;text-decoration:none;font-weight:700;font-size:15px}</style></head>
    <body><div class="card"><div style="font-size:12px;letter-spacing:2px;color:#38bdf8;font-weight:800;margin-bottom:14px">VYBE PROFILE</div>
    <h1 style="font-size:22px;margin:0">${esc(profileRow.display_name || profileRow.username)}</h1>
    <div style="color:#94a3b8;font-size:14px;margin-top:4px">@${esc(profileRow.username)}${isPrivate ? ' 🔒 Private' : ''}</div>
    <p style="color:#cbd5e1;font-size:14px;line-height:1.5;margin:16px 0">${esc(bioText)}</p>
    <div style="color:#94a3b8;font-size:12px">${Number(profileRow.followers_count || 0)} LINKUPS • ${Number(profileRow.posts_count || 0)} Posts</div>
    <div style="display:flex;gap:10px;justify-content:center;flex-wrap:wrap;margin-top:20px">
    <a class="btn" style="margin-top:0" href="${esc(appDeepLink)}">Open Profile in VYBE App</a>
    <a class="btn" style="margin-top:0;background:#1e293b;border:1px solid #334155" href="/?web=1#u=${esc(encodeURIComponent(profileRow.username || profileRow.id))}">View on Web</a>
    </div></div></body></html>`);
    return;
  }

  // Canonical Shared Post Fallback Page (/post/:id or /p/:id)
  if ((cleanUrl.startsWith('/post/') || cleanUrl.startsWith('/p/')) && req.method === 'GET') {
    const postId = decodeURIComponent(cleanUrl.replace(/^\/(post|p)\//, '').split('/')[0] || '').trim();
    const esc = (s) => String(s || '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
    let postRow = null;
    try {
      if (postId) {
        const resp = await fetch(`${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/posts?id=eq.${encodeURIComponent(postId)}&select=id,caption,media_urls,media_type,likes_count,comments_count,is_archived,profiles:author_id(username,display_name,is_private_account)&limit=1`, {
          headers: { 'apikey': SUPABASE_ANON_KEY }
        });
        if (resp.ok) {
          const rows = await resp.json();
          postRow = Array.isArray(rows) && rows.length > 0 ? rows[0] : null;
        }
      }
    } catch {}

    const isPrivateOrArchived = postRow && (Boolean(postRow.is_archived) || Boolean(postRow.profiles && postRow.profiles.is_private_account));
    if (!postRow || isPrivateOrArchived) {
      res.writeHead(404, { 'Content-Type': 'text/html; charset=utf-8' });
      res.end(`<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Post Unavailable • VYBE</title>
      <style>body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#070913;color:#fff;font-family:system-ui,-apple-system,sans-serif;padding:24px}.card{max-width:400px;width:100%;background:#111729;border:1px solid #263252;border-radius:24px;padding:32px;text-align:center}</style></head>
      <body><div class="card"><div style="font-size:38px;margin-bottom:12px">🔒</div><h1 style="font-size:20px;margin:0 0 8px">Post Unavailable</h1><p style="color:#94a3b8;font-size:14px;line-height:1.5">This VYBE post is private, archived, or no longer available.</p></div></body></html>`);
      return;
    }

    const author = postRow.profiles || {};
    const appDeepLink = `vybe://post/${encodeURIComponent(postRow.id)}`;
    const firstMedia = Array.isArray(postRow.media_urls) && postRow.media_urls.length > 0 ? postRow.media_urls[0] : '';
    const isVideo = String(postRow.media_type || '').toUpperCase().includes('VIDEO') || String(postRow.media_type || '').toUpperCase().includes('REEL') || /\.(mp4|webm|mov)(\?|$)/i.test(firstMedia);
    const mediaHtml = firstMedia
      ? (isVideo
          ? `<video src="${esc(firstMedia)}" controls playsinline style="width:100%;max-height:360px;border-radius:16px;margin-top:14px;background:#000"></video>`
          : `<img src="${esc(firstMedia)}" alt="VYBE Post" style="width:100%;max-height:360px;object-fit:cover;border-radius:16px;margin-top:14px;background:#000" />`)
      : '';

    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(`<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Post by @${esc(author.username || 'vybe')} • VYBE</title>
    <style>body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#070913;color:#fff;font-family:system-ui,-apple-system,sans-serif;padding:24px}.card{max-width:440px;width:100%;background:#111729;border:1px solid #263252;border-radius:24px;padding:28px;text-align:left}.btn{display:block;text-align:center;margin-top:20px;padding:13px 24px;border-radius:14px;background:linear-gradient(90deg,#2563eb,#7c3aed);color:#fff;text-decoration:none;font-weight:700;font-size:15px}</style></head>
    <body><div class="card"><div style="font-size:11px;letter-spacing:2px;color:#38bdf8;font-weight:800;margin-bottom:10px">SHARED ON VYBE</div>
    <div style="font-weight:700;font-size:16px">${esc(author.display_name || author.username || 'VYBE Creator')} <span style="color:#94a3b8;font-weight:500;font-size:13px">@${esc(author.username || 'vybe')}</span></div>
    ${postRow.caption ? `<p style="color:#e2e8f0;font-size:14px;line-height:1.5;margin:12px 0 0">${esc(postRow.caption)}</p>` : ''}
    ${mediaHtml}
    <div style="color:#94a3b8;font-size:12px;margin-top:14px">🫰 ${Number(postRow.likes_count || 0)} • 💬 ${Number(postRow.comments_count || 0)}</div>
    <div style="display:flex;gap:10px;justify-content:center;flex-wrap:wrap;margin-top:20px">
    <a class="btn" style="margin-top:0;flex:1" href="${esc(appDeepLink)}">Open Post in VYBE App</a>
    <a class="btn" style="margin-top:0;flex:1;background:#1e293b;border:1px solid #334155" href="/?web=1#post_${esc(encodeURIComponent(postRow.id))}">View on Web</a>
    </div></div></body></html>`);
    return;
  }

  const sendStaticFile = (filePath, headersObj) => {
    res.writeHead(200, headersObj);
    if (req.method === 'HEAD') {
      res.end();
      return;
    }
    const stream = fs.createReadStream(filePath);
    stream.on('error', () => {
      if (!res.headersSent) res.writeHead(500);
      res.end();
    });
    stream.pipe(res);
  };

  // 1. APK Direct Download Route
  if (cleanUrl === '/download' || cleanUrl === '/vybe-debug.apk' || cleanUrl === '/app-debug.apk') {
    const apkFile = fs.existsSync(path.join(PUBLIC_DIR, 'vybe-debug.apk'))
      ? path.join(PUBLIC_DIR, 'vybe-debug.apk')
      : path.resolve(__dirname, '../.build-outputs/app-debug.apk');
    if (fs.existsSync(apkFile)) {
      const stat = fs.statSync(apkFile);
      return sendStaticFile(apkFile, {
        'Content-Type': 'application/vnd.android.package-archive',
        'Content-Disposition': 'attachment; filename="vybe-debug.apk"',
        'Content-Length': stat.size
      });
    }
  }

  // 2. Admin Portal Route (/admin, /admin/)
  if (cleanUrl === '/admin' || cleanUrl === '/admin/' || cleanUrl === '/admin/index.html' || cleanUrl === '/admin.html') {
    const distAdmin = path.join(DIST_DIR, 'admin.html');
    if (fs.existsSync(distAdmin)) {
      return sendStaticFile(distAdmin, { 'Content-Type': 'text/html; charset=utf-8' });
    }
    const adminPath = path.join(WEB_DIR, 'index.html');
    if (fs.existsSync(adminPath)) {
      return sendStaticFile(adminPath, { 'Content-Type': 'text/html; charset=utf-8' });
    }
  }

  // Path Traversal & Sensitive Manifest Protection
  let safeDecodedPath = '';
  try {
    safeDecodedPath = decodeURIComponent(cleanUrl).replace(/\\/g, '/');
  } catch {
    res.writeHead(400, { 'Content-Type': 'text/plain' });
    res.end('Bad Request');
    return;
  }

  const rawSegments = String(req.url || '/').split('?')[0].replace(/\\/g, '/').split('/').filter(Boolean);
  const decodedSegments = safeDecodedPath.split('/').filter(Boolean);
  const BLOCKED_MANIFEST_RE = /^(package(-lock)?\.json|tsconfig.*\.json|vite\.config\.[tj]s|netlify.*\.toml|capacitor\.config\.[tj]s|firebase-service-account\.json|admin_state_override\.json|platform_config\.json)$/i;
  if (
    rawSegments.some(seg => seg === '..' || (seg.startsWith('.') && seg !== '.well-known') || BLOCKED_MANIFEST_RE.test(seg)) ||
    decodedSegments.some(seg => seg === '..' || (seg.startsWith('.') && seg !== '.well-known') || BLOCKED_MANIFEST_RE.test(seg))
  ) {
    res.writeHead(403, { 'Content-Type': 'text/plain' });
    res.end('Forbidden');
    return;
  }

  const isSafeWithinDir = (baseDir, candidate) => {
    const rel = path.relative(baseDir, candidate);
    return rel !== '' && !rel.startsWith('..') && !path.isAbsolute(rel);
  };

  const relRequestPath = safeDecodedPath.replace(/^\/+/, '');

  // 3. Static Assets: Check PUBLIC_DIR first (brand icons, badges, etc.)
  const publicCandidate = path.resolve(PUBLIC_DIR, relRequestPath);
  if (isSafeWithinDir(PUBLIC_DIR, publicCandidate) && fs.existsSync(publicCandidate) && fs.statSync(publicCandidate).isFile()) {
    const ext = path.extname(publicCandidate).toLowerCase();
    return sendStaticFile(publicCandidate, { 'Content-Type': MIME_TYPES[ext] || 'application/octet-stream' });
  }

  // 4. Static Assets: Check DIST_DIR (Vite JS/CSS bundles)
  const distCandidate = path.resolve(DIST_DIR, relRequestPath);
  if (isSafeWithinDir(DIST_DIR, distCandidate) && fs.existsSync(distCandidate) && fs.statSync(distCandidate).isFile()) {
    const ext = path.extname(distCandidate).toLowerCase();
    return sendStaticFile(distCandidate, { 'Content-Type': MIME_TYPES[ext] || 'application/octet-stream' });
  }

  // 5. Static Assets: Check WEB_DIR strictly for /admin/* and /badges/* routes
  if (safeDecodedPath.startsWith('/admin/') || safeDecodedPath.startsWith('/badges/')) {
    const webRelPath = safeDecodedPath.replace(/^\/admin\/?/, '').replace(/^\/+/, '');
    const webCandidate = path.resolve(WEB_DIR, webRelPath);
    if (isSafeWithinDir(WEB_DIR, webCandidate) && fs.existsSync(webCandidate) && fs.statSync(webCandidate).isFile()) {
      const ext = path.extname(webCandidate).toLowerCase();
      return sendStaticFile(webCandidate, { 'Content-Type': MIME_TYPES[ext] || 'application/octet-stream' });
    }
  }

  // 6. SPA Route Fallback:
  // If requesting /admin/* fallback to dist/admin.html or web/index.html; otherwise fallback to dist/index.html (VYBE Web App)
  if (cleanUrl.startsWith('/admin')) {
    const distAdmin = path.join(DIST_DIR, 'admin.html');
    if (fs.existsSync(distAdmin)) {
      return sendStaticFile(distAdmin, { 'Content-Type': 'text/html; charset=utf-8' });
    }
    const adminIndex = path.join(WEB_DIR, 'index.html');
    if (fs.existsSync(adminIndex)) {
      return sendStaticFile(adminIndex, { 'Content-Type': 'text/html; charset=utf-8' });
    }
  }

  const appIndex = path.join(DIST_DIR, 'index.html');
  if (fs.existsSync(appIndex)) {
    return sendStaticFile(appIndex, { 'Content-Type': 'text/html; charset=utf-8' });
  }

  const fallbackAdmin = path.join(WEB_DIR, 'index.html');
  if (fs.existsSync(fallbackAdmin)) {
    return sendStaticFile(fallbackAdmin, { 'Content-Type': 'text/html; charset=utf-8' });
  }

  res.writeHead(404, { 'Content-Type': 'text/plain' });
  res.end('Not Found');
});

if (require.main === module) {
  server.listen(PORT, '0.0.0.0', () => {
    console.log(`[VYBE Web Portal & Push Gateway] Listening on http://0.0.0.0:${PORT}`);
  });
}

module.exports = { server, verifyUserAuth, verifyAdminAuth, checkRateLimit };
