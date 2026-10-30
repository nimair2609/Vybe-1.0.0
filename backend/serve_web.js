const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PORT = Number(process.env.VYBE_WEB_PORT || 3000);
const WEB_DIR = path.resolve(__dirname, '../web');
const SUPABASE_URL = process.env.SUPABASE_URL || 'https://cppwadedazpbevkfrchg.supabase.co';
const SUPABASE_ANON_KEY = process.env.SUPABASE_ANON_KEY || 'sb_publishable_MI-UdOqfccHVZIHjQq9vrw_IjEzeR7H';

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
const OVERRIDE_FILE = path.resolve(__dirname, 'admin_state_override.json');

function loadAdminState() {
  try {
    if (fs.existsSync(OVERRIDE_FILE)) {
      return JSON.parse(fs.readFileSync(OVERRIDE_FILE, 'utf8'));
    }
  } catch (err) {
    console.warn('[AdminState] Read warning:', err.message);
  }
  return { badges: {}, roles: {}, statuses: {}, contentModeration: {}, reports: {}, auditLogs: [] };
}

function saveAdminState(state) {
  try {
    fs.writeFileSync(OVERRIDE_FILE, JSON.stringify(state, null, 2), 'utf8');
  } catch (err) {
    console.error('[AdminState] Write error:', err.message);
  }
}

async function patchSupabaseProfile(userId, patchData) {
  try {
    const url = `${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/profiles?id=eq.${userId}`;
    const resp = await fetch(url, {
      method: 'PATCH',
      headers: {
        'apikey': SUPABASE_ANON_KEY,
        'Authorization': `Bearer ${SUPABASE_ANON_KEY}`,
        'Content-Type': 'application/json',
        'Prefer': 'return=representation'
      },
      body: JSON.stringify(patchData)
    });
    return resp.ok;
  } catch (err) {
    console.warn('[SupabaseProfile] Patch warning:', err.message);
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
    const resp = await fetch(`${SUPABASE_URL.replace(/\/$/, '')}/realtime/v1/api/broadcast`, {
      method: 'POST',
      headers: {
        'apikey': SUPABASE_ANON_KEY,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        messages: [{ topic, event: 'push', payload }]
      })
    });
    return resp.ok;
  } catch {
    return false;
  }
}

async function dispatchFcmIfConfigured({ tokens, topic, title, body, data = {}, channelId = 'vybe_activity_high_v2' }) {
  const sa = getFirebaseServiceAccount();
  if (!sa) return { dispatchedFcm: false, count: Math.max(tokens.length, 1) };

  const accessToken = await getGoogleAccessToken(sa);
  if (!accessToken) return { dispatchedFcm: false, count: Math.max(tokens.length, 1) };

  const projectId = sa.project_id || 'vybe-1c966';
  const targets = tokens.length > 0 ? tokens.map(t => ({ token: t })) : (topic ? [{ topic }] : []);
  let ok = 0;

  for (const target of targets) {
    try {
      const resolvedEventKey = String(data.event_key || data.notification_id || `evt_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`);
      const stringData = Object.fromEntries(Object.entries({ ...data, title, body, event_key: resolvedEventKey, channel_id: channelId }).map(([k, v]) => [k, String(v ?? '')]));
      const resp = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${accessToken}`
        },
        body: JSON.stringify({
          message: {
            ...(target.token ? { token: target.token } : { topic: target.topic }),
            data: stringData,
            android: {
              priority: 'high'
            }
          }
        })
      });
      if (resp.ok) ok++;
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

const server = http.createServer(async (req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-Vybe-Owner-Console');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  const cleanUrl = (req.url || '/').split('?')[0];

  if (cleanUrl === '/health') {
    sendJson(res, 200, {
      status: 'healthy',
      server: 'VYBE Admin & Push Gateway',
      timestamp: new Date().toISOString()
    });
    return;
  }

  if (cleanUrl === '/api/v1/push/register-token' && req.method === 'POST') {
    const body = await readJsonBody(req);
    if (!body.userId || !body.fcmToken) {
      sendJson(res, 400, { error: 'userId and fcmToken required' });
      return;
    }
    deviceRegistry.set(body.fcmToken, {
      userId: body.userId,
      fcmToken: body.fcmToken,
      device_model: body.deviceModel || 'Android Phone',
      platform: body.platform || 'android',
      last_seen_at: new Date().toISOString(),
      is_active: true
    });
    sendJson(res, 200, { success: true });
    return;
  }

  if (cleanUrl === '/api/v1/push/send' && req.method === 'POST') {
    const body = await readJsonBody(req);
    const { recipientId, title, body: msgBody, data = {} } = body;
    if (!recipientId || !title || !msgBody) {
      sendJson(res, 400, { error: 'recipientId, title, and body are required.' });
      return;
    }
    const tokens = Array.from(deviceRegistry.values())
      .filter(d => d.is_active && d.userId === recipientId)
      .map(d => d.fcmToken);

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
      data
    });

    recentPushLogs.unshift({
      timestamp: new Date().toISOString(),
      title,
      recipientCount: Math.max(tokens.length, 1),
      status: fcmRes.dispatchedFcm ? 'Dispatched (FCM + Realtime Live)' : 'Dispatched (Realtime Live)',
      channel: 'vybe_activity_high_v2'
    });
    if (recentPushLogs.length > 50) recentPushLogs.pop();

    sendJson(res, 200, { success: true, delivered: fcmRes.count });
    return;
  }

  if (cleanUrl === '/api/v1/admin/broadcast-push' && req.method === 'POST') {
    const body = await readJsonBody(req);
    const { title, body: msgBody, destination = 'NOTIFICATIONS', targetId } = body;
    if (!title || !msgBody) {
      sendJson(res, 400, { error: 'Title and body are required.' });
      return;
    }

    const eventKey = `bcast_${Date.now()}`;
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

    const tokens = Array.from(deviceRegistry.values())
      .filter(d => d.is_active)
      .map(d => d.fcmToken);

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
      recipientCount: Math.max(tokens.length, 1),
      status: fcmRes.dispatchedFcm ? 'Dispatched (FCM + Realtime Live)' : (rtOk ? 'Dispatched (Realtime Live)' : 'Dispatched'),
      channel: 'vybe_broadcast_high_v2'
    });
    if (recentPushLogs.length > 50) recentPushLogs.pop();

    sendJson(res, 200, {
      success: true,
      totalDevices: Math.max(tokens.length, 1),
      delivered: fcmRes.count
    });
    return;
  }

  if (cleanUrl === '/api/v1/admin/push-stats' && req.method === 'GET') {
    const activeDevices = Array.from(deviceRegistry.values()).filter(d => d.is_active);
    sendJson(res, 200, {
      activeTokensCount: activeDevices.length,
      fcmConfigured: Boolean(getFirebaseServiceAccount() || process.env.FCM_SERVER_KEY),
      realtimeConfigured: true,
      recentDevices: activeDevices.slice(0, 10),
      recentLogs: recentPushLogs
    });
    return;
  }

  // 1. GET ALL USERS (MERGED WITH ADMIN OVERRIDES)
  if (cleanUrl === '/api/v1/admin/users' && req.method === 'GET') {
    const state = loadAdminState();
    let users = [];
    try {
      const resp = await fetch(`${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/profiles?select=*&order=created_at.desc&limit=100`, {
        headers: {
          'apikey': SUPABASE_ANON_KEY,
          'Authorization': `Bearer ${SUPABASE_ANON_KEY}`
        }
      });
      if (resp.ok) {
        users = await resp.json();
      }
    } catch (err) {
      console.warn('[AdminUsers] Supabase fetch error:', err.message);
    }

    const merged = users.map(u => {
      const bOverride = state.badges && state.badges[u.id];
      const rOverride = state.roles && state.roles[u.id];
      const sOverride = state.statuses && state.statuses[u.id];

      // Auto-elevate owner emails if not specifically set
      const isOwnerEmail = (u.email === 'renukadas083@gmail.com' || u.email === 'nrnexthq@gmail.com');
      const defaultRole = isOwnerEmail ? 'ADMIN' : (u.role || 'USER');

      const isWorker = bOverride?.is_worker !== undefined ? bOverride.is_worker : Boolean(u.is_worker);
      const isVip = bOverride?.is_vip !== undefined ? bOverride.is_vip : Boolean(u.is_vip);
      const isVerified = bOverride?.is_verified !== undefined ? bOverride.is_verified : Boolean(u.is_verified);
      const isVerifiedBusiness = bOverride?.is_verified_business !== undefined ? bOverride.is_verified_business : Boolean(u.is_verified_business);
      const computedBadge = isWorker ? 'OFFICIAL' : (isVip ? 'VIP' : (isVerified ? 'VERIFIED' : (isVerifiedBusiness ? 'SHOP' : null)));

      return {
        ...u,
        is_verified: isVerified,
        is_vip: isVip,
        is_worker: isWorker,
        is_verified_business: isVerifiedBusiness,
        badge_type: computedBadge,
        role: rOverride || defaultRole,
        is_banned: sOverride?.is_banned !== undefined ? sOverride.is_banned : Boolean(u.is_banned),
        is_suspended: sOverride?.is_suspended !== undefined ? sOverride.is_suspended : Boolean(u.is_suspended),
        ban_reason: sOverride?.ban_reason || u.ban_reason || null,
        suspension_reason: sOverride?.suspension_reason || u.suspension_reason || null,
        suspended_until: sOverride?.suspended_until || u.suspended_until || null
      };
    });

    sendJson(res, 200, { success: true, users: merged, total: merged.length });
    return;
  }

  // 2. ASSIGN / REVOKE BADGE (THREE-TIER BADGE SYSTEM)
  if (cleanUrl === '/api/v1/admin/assign-badge' && req.method === 'POST') {
    const body = await readJsonBody(req);
    const { userId, badgeType, value, username, adminEmail, adminUserId } = body;
    if (!userId || !badgeType) {
      sendJson(res, 400, { error: 'userId and badgeType required' });
      return;
    }

    const normalizedType = badgeType.toLowerCase().trim();

    // Security Check 1: Self-assignment prohibition
    const callerId = adminUserId || req.headers['x-admin-user-id'];
    if (callerId && callerId === userId) {
      sendJson(res, 403, { error: 'Security Policy: Self-assignment of verification badges is strictly forbidden. Another administrator must review and grant badges.' });
      return;
    }

    // Security Check 2: Super Admin authorization required for Green Official badge
    const isSuperAdmin = (
      adminEmail === 'renukadas083@gmail.com' ||
      adminEmail === 'nrnexthq@gmail.com' ||
      req.headers['x-admin-role'] === 'SUPER_ADMIN' ||
      req.headers['x-admin-role'] === 'OWNER_ADMIN'
    );

    if ((normalizedType === 'official' || normalizedType === 'worker') && !isSuperAdmin) {
      sendJson(res, 403, { error: 'Security Policy: VYBE Official badges can only be assigned or revoked by the Super Admin.' });
      return;
    }

    const state = loadAdminState();
    state.badges = state.badges || {};
    state.badges[userId] = state.badges[userId] || {};

    let dbKey = 'is_verified';
    let badgeDisplayLabel = 'VYBE Verified';
    if (normalizedType === 'verified') {
      dbKey = 'is_verified';
      badgeDisplayLabel = 'VYBE Verified (Electric Blue)';
    } else if (normalizedType === 'vip') {
      dbKey = 'is_vip';
      badgeDisplayLabel = 'VYBE VIP (Metallic Gold)';
    } else if (normalizedType === 'official' || normalizedType === 'worker') {
      dbKey = 'is_worker';
      badgeDisplayLabel = 'VYBE Official (Emerald Green)';
    } else if (normalizedType === 'business' || normalizedType === 'shop') {
      dbKey = 'is_verified_business';
      badgeDisplayLabel = 'VYBE Storefront Shop';
    }

    state.badges[userId][dbKey] = Boolean(value);

    state.auditLogs = state.auditLogs || [];
    state.auditLogs.unshift({
      timestamp: new Date().toISOString(),
      action: value ? `GRANT_${normalizedType.toUpperCase()}` : `REVOKE_${normalizedType.toUpperCase()}`,
      userId,
      username: username || 'User',
      adminEmail: adminEmail || (isSuperAdmin ? 'Super Admin' : 'Platform Admin'),
      details: { badgeType: normalizedType, label: badgeDisplayLabel, value: Boolean(value) }
    });
    if (state.auditLogs.length > 200) state.auditLogs.pop();

    saveAdminState(state);

    // Also attempt remote DB patch asynchronously
    patchSupabaseProfile(userId, { [dbKey]: Boolean(value) });

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
        // 1. Live Realtime Relay
        relayRealtimeBroadcast(`vybe_user_push:${userId}`, {
          type: 'BADGE_UPDATE',
          badge_type: badgeType,
          value: Boolean(value),
          recipient_id: userId,
          title: notifTitle,
          body: notifBody
        });

        // 2. FCM Push Notification to Android
        const userTokens = Array.from(deviceRegistry.values())
          .filter(d => d.is_active && d.userId === userId)
          .map(d => d.fcmToken);

        dispatchFcmIfConfigured({
          tokens: userTokens,
          topic: `vybe_user_${String(userId).replace(/[^a-zA-Z0-9-_.~%]/g, '_')}`,
          title: notifTitle,
          body: notifBody,
          data: {
            type: 'BADGE_UPDATE',
            badge_type: badgeType,
            value: String(value)
          },
          channelId: 'vybe_activity_high_v2'
        });

        // 3. Insert into Supabase notifications table
        fetch(`${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/notifications`, {
          method: 'POST',
          headers: {
            'apikey': SUPABASE_ANON_KEY,
            'Authorization': `Bearer ${SUPABASE_ANON_KEY}`,
            'Content-Type': 'application/json',
            'Prefer': 'return=minimal'
          },
          body: JSON.stringify({
            recipient_id: userId,
            actor_id: '00000000-0000-0000-0000-000000000001',
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
      value: Boolean(value),
      userState: state.badges[userId]
    });
    return;
  }

  // 3. SET USER ROLE
  if (cleanUrl === '/api/v1/admin/set-role' && req.method === 'POST') {
    const body = await readJsonBody(req);
    const { userId, role, username, adminEmail } = body;
    if (!userId || !role) {
      sendJson(res, 400, { error: 'userId and role required' });
      return;
    }

    const state = loadAdminState();
    state.roles = state.roles || {};
    state.roles[userId] = role;

    state.auditLogs = state.auditLogs || [];
    state.auditLogs.unshift({
      timestamp: new Date().toISOString(),
      action: 'UPDATE_ROLE',
      userId,
      username: username || 'User',
      adminEmail: adminEmail || 'Owner Admin',
      details: { role }
    });
    if (state.auditLogs.length > 200) state.auditLogs.pop();

    saveAdminState(state);
    patchSupabaseProfile(userId, { role });

    sendJson(res, 200, {
      success: true,
      message: `Role updated to ${role} for ${username || userId}`,
      userId,
      role
    });
    return;
  }

  // 4. SUSPEND / BAN / RESTORE USER STATUS
  if (cleanUrl === '/api/v1/admin/user-status' && req.method === 'POST') {
    const body = await readJsonBody(req);
    const { userId, action, reason, durationHours = 24, username, adminEmail } = body;
    if (!userId || !action) {
      sendJson(res, 400, { error: 'userId and action required' });
      return;
    }

    const state = loadAdminState();
    state.statuses = state.statuses || {};
    state.statuses[userId] = state.statuses[userId] || {};

    let patch = {};
    if (action === 'SUSPEND') {
      const until = new Date(Date.now() + durationHours * 3600 * 1000).toISOString();
      state.statuses[userId].is_suspended = true;
      state.statuses[userId].is_banned = false;
      state.statuses[userId].suspension_reason = reason || `${durationHours}h enforcement`;
      state.statuses[userId].suspended_until = until;
      patch = { is_suspended: true, suspension_reason: reason || `${durationHours}h enforcement`, suspended_until: until };
    } else if (action === 'BAN') {
      state.statuses[userId].is_banned = true;
      state.statuses[userId].is_suspended = false;
      state.statuses[userId].ban_reason = reason || 'Admin account termination';
      patch = { is_banned: true, ban_reason: reason || 'Admin account termination' };
    } else if (action === 'RESTORE') {
      state.statuses[userId].is_banned = false;
      state.statuses[userId].is_suspended = false;
      state.statuses[userId].ban_reason = null;
      state.statuses[userId].suspension_reason = null;
      state.statuses[userId].suspended_until = null;
      patch = { is_banned: false, is_suspended: false, ban_reason: null, suspension_reason: null, suspended_until: null };
    }

    state.auditLogs = state.auditLogs || [];
    state.auditLogs.unshift({
      timestamp: new Date().toISOString(),
      action: `USER_${action}`,
      userId,
      username: username || 'User',
      adminEmail: adminEmail || 'Owner Admin',
      details: { action, reason, patch }
    });
    if (state.auditLogs.length > 200) state.auditLogs.pop();

    saveAdminState(state);
    patchSupabaseProfile(userId, patch);

    sendJson(res, 200, {
      success: true,
      message: `Account status set to ${action} for ${username || userId}`,
      userId,
      action
    });
    return;
  }

  // 5. CONTENT MODERATION ACTION
  if (cleanUrl === '/api/v1/admin/content-action' && req.method === 'POST') {
    const body = await readJsonBody(req);
    const { contentId, action, adminEmail } = body;
    const state = loadAdminState();
    state.contentModeration = state.contentModeration || {};
    state.contentModeration[contentId] = { action, timestamp: new Date().toISOString(), adminEmail };
    saveAdminState(state);
    sendJson(res, 200, { success: true, contentId, action });
    return;
  }

  // 6. REPORT ACTION
  if (cleanUrl === '/api/v1/admin/report-action' && req.method === 'POST') {
    const body = await readJsonBody(req);
    const { reportId, action, notes, adminEmail } = body;
    const state = loadAdminState();
    state.reports = state.reports || {};
    state.reports[reportId] = { action, notes, timestamp: new Date().toISOString(), adminEmail };
    saveAdminState(state);
    sendJson(res, 200, { success: true, reportId, action });
    return;
  }

  // 7. ADS MANAGEMENT
  if (cleanUrl === '/api/v1/admin/ads' && req.method === 'GET') {
    const state = loadAdminState();
    if (!state.ads || state.ads.length === 0) {
      state.ads = [
        {
          id: 'ad_launch_01',
          name: 'VYBE 2027 Genesis Showcase',
          brand: 'NR Next Originals',
          placement: 'FEED_SPONSORED_POST',
          headline: 'Experience the next-generation creator soundscape & live resonance.',
          mediaUrl: 'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=800&q=80',
          ctaText: 'Explore Sounds',
          ctaUrl: 'https://vybe.nrnext.com',
          impressions: 48200,
          targetImpressions: 100000,
          budget: '₹25,000',
          status: 'ACTIVE',
          createdAt: new Date().toISOString()
        },
        {
          id: 'ad_launch_02',
          name: 'Creator VIP Pass Enrollment',
          brand: 'VYBE Studio Pro',
          placement: 'REEL_SPONSORED_VIDEO',
          headline: 'Get official verification, 4K reel uploads, and priority audio tools.',
          mediaUrl: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800&q=80',
          ctaText: 'Claim Creator Pass',
          ctaUrl: 'https://vybe.nrnext.com/vip',
          impressions: 19400,
          targetImpressions: 50000,
          budget: '₹15,000',
          status: 'ACTIVE',
          createdAt: new Date().toISOString()
        }
      ];
      saveAdminState(state);
    }
    sendJson(res, 200, { success: true, ads: state.ads });
    return;
  }

  if (cleanUrl === '/api/v1/admin/ads/create' && req.method === 'POST') {
    const body = await readJsonBody(req);
    const { name, brand, placement, headline, mediaUrl, ctaText, ctaUrl, targetImpressions, budget } = body;
    if (!name || !brand) {
      sendJson(res, 400, { error: 'Campaign name and brand are required.' });
      return;
    }

    const state = loadAdminState();
    state.ads = state.ads || [];
    const newAd = {
      id: `ad_${Date.now()}`,
      name,
      brand,
      placement: placement || 'FEED_SPONSORED_POST',
      headline: headline || '',
      mediaUrl: mediaUrl || 'https://images.unsplash.com/photo-1550745165-9bc0b252726f?w=800&q=80',
      ctaText: ctaText || 'Learn More',
      ctaUrl: ctaUrl || 'https://vybe.nrnext.com',
      impressions: 0,
      targetImpressions: Number(targetImpressions) || 50000,
      budget: budget || '₹10,000',
      status: 'ACTIVE',
      createdAt: new Date().toISOString()
    };
    state.ads.unshift(newAd);
    saveAdminState(state);

    sendJson(res, 200, { success: true, ad: newAd });
    return;
  }

  if (cleanUrl === '/api/v1/admin/ads/toggle' && req.method === 'POST') {
    const body = await readJsonBody(req);
    const { adId } = body;
    const state = loadAdminState();
    state.ads = state.ads || [];
    const ad = state.ads.find(a => a.id === adId);
    if (!ad) {
      sendJson(res, 404, { error: 'Ad campaign not found' });
      return;
    }
    ad.status = ad.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE';
    saveAdminState(state);
    sendJson(res, 200, { success: true, status: ad.status });
    return;
  }

  if (cleanUrl === '/api/v1/admin/ads/delete' && req.method === 'POST') {
    const body = await readJsonBody(req);
    const { adId } = body;
    const state = loadAdminState();
    state.ads = (state.ads || []).filter(a => a.id !== adId);
    saveAdminState(state);
    sendJson(res, 200, { success: true });
    return;
  }

  // 8. AUDIT LOGS
  if (cleanUrl === '/api/v1/admin/audit-logs' && req.method === 'GET') {
    const state = loadAdminState();
    sendJson(res, 200, { success: true, logs: state.auditLogs || [] });
    return;
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
    const bioText = isPrivate ? 'This account is private. Follow in the VYBE app to see their posts and reels.' : (profileRow.bio || 'Official member on VYBE.');
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(`<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${esc(profileRow.display_name || profileRow.username)} (@${esc(profileRow.username)}) • VYBE</title>
    <style>body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#070913;color:#fff;font-family:system-ui,-apple-system,sans-serif;padding:24px}.card{max-width:420px;width:100%;background:#111729;border:1px solid #263252;border-radius:24px;padding:32px;text-align:center}.btn{display:inline-block;margin-top:20px;padding:13px 24px;border-radius:14px;background:linear-gradient(90deg,#2563eb,#7c3aed);color:#fff;text-decoration:none;font-weight:700;font-size:15px}</style></head>
    <body><div class="card"><div style="font-size:12px;letter-spacing:2px;color:#38bdf8;font-weight:800;margin-bottom:14px">VYBE PROFILE</div>
    <h1 style="font-size:22px;margin:0">${esc(profileRow.display_name || profileRow.username)}</h1>
    <div style="color:#94a3b8;font-size:14px;margin-top:4px">@${esc(profileRow.username)}${isPrivate ? ' 🔒 Private' : ''}</div>
    <p style="color:#cbd5e1;font-size:14px;line-height:1.5;margin:16px 0">${esc(bioText)}</p>
    <div style="color:#94a3b8;font-size:12px">${Number(profileRow.followers_count || 0)} Followers • ${Number(profileRow.posts_count || 0)} Posts</div>
    <a class="btn" href="${esc(appDeepLink)}">Open Profile in VYBE App</a></div></body></html>`);
    return;
  }

  let safePath = cleanUrl === '/' || cleanUrl === '' ? '/index.html' : cleanUrl;
  const filePath = path.join(WEB_DIR, safePath);

  if (!filePath.startsWith(WEB_DIR)) {
    res.writeHead(403, { 'Content-Type': 'text/plain' });
    res.end('Forbidden');
    return;
  }

  fs.stat(filePath, (err, stats) => {
    if (err || !stats.isFile()) {
      const fallbackPath = path.join(WEB_DIR, 'index.html');
      fs.readFile(fallbackPath, (err2, data) => {
        if (err2) {
          res.writeHead(404, { 'Content-Type': 'text/plain' });
          res.end('Not Found');
        } else {
          res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
          res.end(data);
        }
      });
      return;
    }

    const ext = path.extname(filePath).toLowerCase();
    const contentType = MIME_TYPES[ext] || 'application/octet-stream';
    res.writeHead(200, { 'Content-Type': contentType });
    fs.createReadStream(filePath).pipe(res);
  });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`[VYBE Web Portal & Push Gateway] Listening on http://0.0.0.0:${PORT}`);
});
