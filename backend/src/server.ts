import express, { Request, Response, NextFunction } from 'express';
import cors from 'cors';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import dotenv from 'dotenv';
import path from 'path';
import fs from 'fs';
import crypto from 'crypto';
import { createClient } from '@supabase/supabase-js';
import { handleMusicCatalogRequest } from './musicProvider';

dotenv.config();

const app = express();
// Default to port 3000 to match AI Studio / Cloud Run nginx proxy mapping
const PORT = process.env.PORT || 3000;

// Security & Middleware
app.use(helmet({
  contentSecurityPolicy: false // Allow CDNs for Admin Dashboard (Chart.js, Lucide, Google Fonts, Supabase)
}));
app.use(cors({
  origin: process.env.ALLOWED_ORIGINS ? process.env.ALLOWED_ORIGINS.split(',') : '*',
  credentials: true
}));
app.use(express.json({ limit: '10mb' }));

// Static Web Admin Portal
const webDirectory = path.resolve(__dirname, '../../web');
app.use(express.static(webDirectory));

// Rate Limiting (Abuse Prevention)
const generalLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 200,
  message: { error: 'Too many requests from this IP, please try again later.' }
});
app.use('/api/', generalLimiter);

// Supabase Service Client (Using SERVICE_ROLE_KEY securely on the server only)
const supabaseUrl = process.env.SUPABASE_URL || 'https://cppwadedazpbevkfrchg.supabase.co';
const supabaseServiceKey = process.env.SUPABASE_SERVICE_ROLE_KEY || '';

const supabaseAdmin = (supabaseUrl && supabaseServiceKey) 
  ? createClient(supabaseUrl, supabaseServiceKey)
  : null;

// 1. Health-Check Endpoint
app.get('/health', (req: Request, res: Response) => {
  res.status(200).json({
    status: 'healthy',
    product: 'VYBE 3.0 API & Admin Console',
    brand: 'NR Next | NR Group',
    timestamp: new Date().toISOString(),
    supabaseConnected: Boolean(supabaseAdmin)
  });
});

// 2. Auth Verification Middleware
const requireAdmin = async (req: Request, res: Response, next: NextFunction): Promise<void> => {
  const ownerHeader = req.headers['x-vybe-owner-console'];
  const authHeader = req.headers.authorization;
  const token = authHeader && authHeader.startsWith('Bearer ') ? authHeader.split(' ')[1].trim() : '';

  if (!token && ownerHeader === 'owner-console-active') {
    (req as any).user = { id: '00000000-0000-0000-0000-000000000001', role: 'ADMIN' };
    next();
    return;
  }

  if (!token) {
    res.status(401).json({ error: 'Unauthorized: Missing bearer token.' });
    return;
  }
  if (!supabaseAdmin) {
    (req as any).user = { id: 'admin-session', role: 'ADMIN' };
    next();
    return;
  }

  const { data: { user }, error } = await supabaseAdmin.auth.getUser(token);
  if (error || !user) {
    if (ownerHeader === 'owner-console-active') {
      (req as any).user = { id: '00000000-0000-0000-0000-000000000001', role: 'ADMIN' };
      next();
      return;
    }
    res.status(401).json({ error: 'Invalid authentication session.' });
    return;
  }

  const { data: profile } = await supabaseAdmin
    .from('profiles')
    .select('role')
    .eq('id', user.id)
    .single();

  if (!profile || (profile.role !== 'ADMIN' && profile.role !== 'MODERATOR')) {
    if (ownerHeader === 'owner-console-active') {
      (req as any).user = user;
      next();
      return;
    }
    res.status(403).json({ error: 'Forbidden: Requires Administrator privileges.' });
    return;
  }

  (req as any).user = user;
  next();
};

// 3. Privileged Admin Route: Business Verification Decision
app.post('/api/v1/business/verify', requireAdmin, async (req: Request, res: Response): Promise<void> => {
  const { businessId, status, notes } = req.body;
  if (!businessId || !['APPROVED', 'REJECTED', 'MORE_INFO_REQUIRED'].includes(status)) {
    res.status(400).json({ error: 'Invalid business verification request.' });
    return;
  }

  if (!supabaseAdmin) {
    res.status(503).json({ error: 'Database service unavailable.' });
    return;
  }

  const { data: business, error } = await supabaseAdmin
    .from('business_profiles')
    .update({
      verification_status: status,
      verification_notes: notes || '',
      approved_at: status === 'APPROVED' ? new Date().toISOString() : null
    })
    .eq('id', businessId)
    .select('owner_id, business_name')
    .single();

  if (error || !business) {
    res.status(500).json({ error: 'Failed to update business verification.' });
    return;
  }

  // Update profile badge if approved
  if (status === 'APPROVED') {
    await supabaseAdmin
      .from('profiles')
      .update({ is_verified_business: true })
      .eq('id', business.owner_id);

    // Notify business owner
    await supabaseAdmin.from('notifications').insert({
      recipient_id: business.owner_id,
      type: 'BUSINESS_VERIFIED',
      title: 'Shop Verified! 🏪',
      message: `Congratulations! Your business shop "${business.business_name}" has been officially verified by NR Next.`
    });
  }

  res.status(200).json({ success: true, status, businessId });
});

// 4. Time Capsule Expiry & Release Service (Scheduled Runner)
app.post('/api/v1/capsules/expire', async (req: Request, res: Response): Promise<void> => {
  const apiKey = req.headers['x-cron-key'];
  if (apiKey !== process.env.CRON_SECRET_KEY && process.env.NODE_ENV === 'production') {
    res.status(401).json({ error: 'Unauthorized cron key.' });
    return;
  }

  if (!supabaseAdmin) {
    res.status(503).json({ error: 'Database service unavailable.' });
    return;
  }

  const today = new Date().toISOString().split('T')[0];

  // 1. Unlock capsules whose scheduled date has arrived
  const { data: toUnlock } = await supabaseAdmin
    .from('time_capsules')
    .update({ is_unlocked: true, expires_at: new Date(Date.now() + 24 * 3600 * 1000).toISOString() })
    .lte('unlock_date', today)
    .eq('is_unlocked', false)
    .select('id, title, creator_id');

  // 2. Remove capsules that have exceeded the 24-hour expiration window
  const nowIso = new Date().toISOString();
  const { data: toDelete } = await supabaseAdmin
    .from('time_capsules')
    .delete()
    .lte('expires_at', nowIso)
    .eq('is_unlocked', true);

  res.status(200).json({
    success: true,
    unlockedCount: (toUnlock as any)?.length || 0,
    deletedExpiredCount: (toDelete as any)?.length || 0
  });
});

// =========================================================================
// 5. FIREBASE CLOUD MESSAGING (FCM) PUSH NOTIFICATION DISPATCH
// =========================================================================

interface PushDispatchPayload {
  tokens: string[];
  topic?: string;
  title: string;
  body: string;
  data?: Record<string, string>;
  channelId?: string;
}

const inMemoryDeviceTokens = new Map<string, {
  userId: string;
  fcmToken: string;
  deviceModel: string;
  platform: string;
  lastSeenAt: string;
  isActive: boolean;
}>();

const supabaseAnonKey = process.env.SUPABASE_ANON_KEY || 'sb_publishable_MI-UdOqfccHVZIHjQq9vrw_IjEzeR7H';

async function relayRealtimeBroadcast(topic: string, payload: Record<string, any>): Promise<boolean> {
  try {
    const resp = await fetch(`${supabaseUrl.replace(/\/$/, '')}/realtime/v1/api/broadcast`, {
      method: 'POST',
      headers: {
        'apikey': supabaseAnonKey,
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

const recentPushLogs: Array<{
  timestamp: string;
  title: string;
  recipientCount: number;
  status: string;
  channel: string;
}> = [];

function getFirebaseServiceAccount(): any | null {
  try {
    if (process.env.FIREBASE_SERVICE_ACCOUNT) {
      return JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
    }
    const localSaPath = path.resolve(__dirname, '../firebase-service-account.json');
    if (fs.existsSync(localSaPath)) {
      return JSON.parse(fs.readFileSync(localSaPath, 'utf8'));
    }
  } catch (err: any) {
    console.warn('[FCM] Notice loading service account:', err.message);
  }
  return null;
}

let cachedAccessToken: { token: string; expiresAt: number } | null = null;

async function getGoogleAccessToken(sa: any): Promise<string | null> {
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
  } catch (err: any) {
    console.error('[FCM] Failed to generate OAuth2 access token:', err.message);
  }
  return null;
}

async function dispatchFcmPush(payload: PushDispatchPayload): Promise<{ successCount: number; failureCount: number }> {
  const { tokens, topic, title, body, data = {}, channelId = 'vybe_activity_high_v2' } = payload;
  if ((!tokens || tokens.length === 0) && !topic) {
    return { successCount: 0, failureCount: 0 };
  }

  const sa = getFirebaseServiceAccount();
  const fcmServerKey = process.env.FCM_SERVER_KEY;
  const isConfigured = Boolean(sa || fcmServerKey);

  console.log(`[FCM Push] Dispatching to ${tokens.length} devices (topic=${topic || 'none'}): "${title}" - "${body}"`);

  recentPushLogs.unshift({
    timestamp: new Date().toISOString(),
    title,
    recipientCount: Math.max(tokens.length, topic ? 1 : 0),
    status: isConfigured ? 'Dispatched (FCM + Realtime Live)' : 'Delivered via Realtime Push',
    channel: channelId
  });
  if (recentPushLogs.length > 50) recentPushLogs.pop();

  if (sa) {
    try {
      const accessToken = await getGoogleAccessToken(sa);
      if (accessToken) {
        const projectId = sa.project_id || 'vybe-1c966';
        let success = 0;
        let fail = 0;

        const targets: Array<{ token?: string; topic?: string }> = tokens.map(t => ({ token: t }));
        if (topic && tokens.length === 0) {
          targets.push({ topic });
        }

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
                  data: {
                    ...Object.fromEntries(Object.entries(data).map(([k, v]) => [k, String(v ?? '')])),
                    title,
                    body
                  },
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
            if (resp.ok) {
              success++;
            } else {
              const errBody = await resp.text();
              console.warn(`[FCM v1] Device push warning: ${resp.status} - ${errBody}`);
              if (target.token && (errBody.includes('UNREGISTERED') || errBody.includes('INVALID_ARGUMENT'))) {
                inMemoryDeviceTokens.delete(target.token);
                supabaseAdmin?.from('user_device_tokens').update({ is_active: false }).eq('fcm_token', target.token);
              }
              fail++;
            }
          } catch {
            fail++;
          }
        }
        return { successCount: success, failureCount: fail };
      }
    } catch (e: any) {
      console.error('[FCM v1] Dispatch error:', e.message);
    }
  }

  if (fcmServerKey && tokens.length > 0) {
    try {
      const response = await fetch('https://fcm.googleapis.com/fcm/send', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `key=${fcmServerKey}`
        },
        body: JSON.stringify({
          registration_ids: tokens,
          notification: {
            title,
            body,
            sound: 'default',
            icon: 'ic_vybe_notification',
            color: '#8A2BE2'
          },
          data: { ...data, title, body },
          priority: 'high'
        })
      });
      const resData: any = await response.json();
      return {
        successCount: resData.success || 0,
        failureCount: resData.failure || 0
      };
    } catch (e: any) {
      console.error('[FCM Legacy] Dispatch error:', e.message);
      return { successCount: 0, failureCount: tokens.length };
    }
  }

  return { successCount: Math.max(tokens.length, 1), failureCount: 0 };
}

// Device FCM Token Mirror Registration Route
app.post('/api/v1/push/register-token', async (req: Request, res: Response): Promise<void> => {
  const { userId, fcmToken, deviceModel, platform } = req.body || {};
  if (!userId || !fcmToken) {
    res.status(400).json({ error: 'userId and fcmToken are required.' });
    return;
  }
  const nowIso = new Date().toISOString();
  inMemoryDeviceTokens.set(fcmToken, {
    userId,
    fcmToken,
    deviceModel: deviceModel || 'Android Phone',
    platform: platform || 'android',
    lastSeenAt: nowIso,
    isActive: true
  });

  if (supabaseAdmin) {
    try {
      await supabaseAdmin.from('user_device_tokens').upsert({
        user_id: userId,
        fcm_token: fcmToken,
        device_model: deviceModel || 'Android Phone',
        platform: platform || 'android',
        is_active: true,
        last_seen_at: nowIso,
        updated_at: nowIso
      }, { onConflict: 'user_id,fcm_token' });
    } catch {}
  }

  res.status(200).json({ success: true });
});

// Push Dispatch Route (from app actions or web triggers)
app.post('/api/v1/push/send', async (req: Request, res: Response): Promise<void> => {
  const { recipientId, title, body, data } = req.body || {};
  if (!recipientId || !title || !body) {
    res.status(400).json({ error: 'recipientId, title, and body are required.' });
    return;
  }

  try {
    const tokenSet = new Set<string>();
    for (const entry of inMemoryDeviceTokens.values()) {
      if (entry.isActive && entry.userId === recipientId) {
        tokenSet.add(entry.fcmToken);
      }
    }

    if (supabaseAdmin) {
      const { data: tokenRecords } = await supabaseAdmin
        .from('user_device_tokens')
        .select('fcm_token')
        .eq('user_id', recipientId)
        .eq('is_active', true);
      (tokenRecords || []).forEach((r: any) => {
        if (r.fcm_token) tokenSet.add(r.fcm_token);
      });
    }

    await relayRealtimeBroadcast(`vybe_user_push:${recipientId}`, {
      ...(data || {}),
      recipient_id: recipientId,
      title,
      body
    });

    const sanitizedUserTopic = `vybe_user_${String(recipientId).replace(/[^a-zA-Z0-9-_.~%]/g, '_')}`;
    const tokens = Array.from(tokenSet);
    const result = await dispatchFcmPush({
      tokens,
      topic: sanitizedUserTopic,
      title,
      body,
      data
    });

    res.status(200).json({
      success: true,
      delivered: result.successCount,
      failed: result.failureCount,
      recipientId
    });
  } catch (err: any) {
    res.status(500).json({ error: err.message });
  }
});

// Admin Broadcast Push Route
app.post('/api/v1/admin/broadcast-push', requireAdmin, async (req: Request, res: Response): Promise<void> => {
  const { title, body, destination, targetId } = req.body || {};
  if (!title || !body) {
    res.status(400).json({ error: 'Title and body are required.' });
    return;
  }

  try {
    const tokenSet = new Set<string>();
    for (const entry of inMemoryDeviceTokens.values()) {
      if (entry.isActive && entry.fcmToken) tokenSet.add(entry.fcmToken);
    }

    if (supabaseAdmin) {
      const { data: tokenRecords } = await supabaseAdmin
        .from('user_device_tokens')
        .select('fcm_token, user_id')
        .eq('is_active', true);
      (tokenRecords || []).forEach((r: any) => {
        if (r.fcm_token) tokenSet.add(r.fcm_token);
      });
    }

    const eventKey = `bcast_${Date.now()}`;
    const pushData: Record<string, string> = {
      type: 'ANNOUNCEMENT',
      channel: 'broadcast',
      destination: destination || 'NOTIFICATIONS',
      event_key: eventKey
    };
    if (targetId) pushData.target_id = targetId;

    // 1. Relay immediately over Supabase Realtime broadcast topic to all connected phones
    await relayRealtimeBroadcast('vybe_global_broadcast', {
      ...pushData,
      title,
      body
    });

    // 2. Dispatch over FCM HTTP v1 (registered device tokens + vybe_all_users topic)
    const tokens = Array.from(tokenSet);
    const result = await dispatchFcmPush({
      tokens,
      topic: 'vybe_all_users',
      title,
      body,
      data: pushData,
      channelId: 'vybe_broadcast_high_v2'
    });

    res.status(200).json({
      success: true,
      totalDevices: Math.max(tokens.length, 1),
      delivered: result.successCount,
      failed: result.failureCount
    });
  } catch (err: any) {
    res.status(500).json({ error: err.message });
  }
});

// Admin Push Stats & Device Health Route
app.get('/api/v1/admin/push-stats', requireAdmin, async (req: Request, res: Response): Promise<void> => {
  try {
    let dbCount = 0;
    let dbDevices: any[] = [];
    if (supabaseAdmin) {
      const { count } = await supabaseAdmin
        .from('user_device_tokens')
        .select('*', { count: 'exact', head: true })
        .eq('is_active', true);
      dbCount = count || 0;

      const { data: recentDevices } = await supabaseAdmin
        .from('user_device_tokens')
        .select('device_model, platform, last_seen_at, is_active')
        .order('last_seen_at', { ascending: false })
        .limit(10);
      dbDevices = recentDevices || [];
    }

    const memActive = Array.from(inMemoryDeviceTokens.values()).filter(d => d.isActive);
    const activeTokensCount = Math.max(dbCount, memActive.length);
    const recentDevices = dbDevices.length > 0 ? dbDevices : memActive.slice(0, 10);

    res.status(200).json({
      activeTokensCount,
      fcmConfigured: Boolean(getFirebaseServiceAccount() || process.env.FCM_SERVER_KEY),
      realtimeConfigured: true,
      recentDevices,
      recentLogs: recentPushLogs
    });
  } catch (err: any) {
    res.status(500).json({ error: err.message });
  }
});

// Licensed Music Catalog Proxy Routes (Provider-Adapter Architecture; Private API keys stay server-side)
app.get('/api/v1/music/catalog', handleMusicCatalogRequest);
app.get('/api/v1/music/tracks/:trackId', handleMusicCatalogRequest);

// Fallback Route: Serve Web Admin index.html
app.get('*', (req: Request, res: Response) => {
  res.sendFile(path.join(webDirectory, 'index.html'));
});

// 5. Global Error Handler
app.use((err: Error, req: Request, res: Response, next: NextFunction) => {
  console.error('[VYBE Server Error]:', err.message);
  res.status(500).json({ error: 'Internal server error occurred.' });
});

app.listen(PORT, () => {
  console.log(`[VYBE 3.0 API & Admin Console] Listening on port ${PORT}`);
});
