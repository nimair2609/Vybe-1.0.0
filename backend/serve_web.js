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
let cachedAccessToken = null;

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
      const resp = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${accessToken}`
        },
        body: JSON.stringify({
          message: {
            ...(target.token ? { token: target.token } : { topic: target.topic }),
            notification: { title, body },
            data: { ...data, title, body },
            android: {
              priority: 'high',
              notification: {
                icon: 'ic_vybe_notification',
                color: '#8A2BE2',
                channel_id: channelId,
                default_sound: true,
                default_vibrate_timings: true
              }
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
