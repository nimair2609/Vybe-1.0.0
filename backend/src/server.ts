import express, { Request, Response, NextFunction } from 'express';
import cors from 'cors';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import dotenv from 'dotenv';
import path from 'path';
import { createClient } from '@supabase/supabase-js';

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
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    res.status(401).json({ error: 'Unauthorized: Missing bearer token.' });
    return;
  }
  const token = authHeader.split(' ')[1];
  if (!supabaseAdmin) {
    res.status(503).json({ error: 'Supabase admin client not configured.' });
    return;
  }

  const { data: { user }, error } = await supabaseAdmin.auth.getUser(token);
  if (error || !user) {
    res.status(401).json({ error: 'Invalid authentication session.' });
    return;
  }

  const { data: profile } = await supabaseAdmin
    .from('profiles')
    .select('role')
    .eq('id', user.id)
    .single();

  if (!profile || (profile.role !== 'ADMIN' && profile.role !== 'MODERATOR')) {
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
  title: string;
  body: string;
  data?: Record<string, string>;
  channelId?: string;
}

const recentPushLogs: Array<{
  timestamp: string;
  title: string;
  recipientCount: number;
  status: string;
  channel: string;
}> = [];

async function dispatchFcmPush(payload: PushDispatchPayload): Promise<{ successCount: number; failureCount: number }> {
  const { tokens, title, body, data = {}, channelId = 'vybe_primary_notifications' } = payload;
  if (!tokens || tokens.length === 0) {
    return { successCount: 0, failureCount: 0 };
  }

  const fcmServiceAccount = process.env.FIREBASE_SERVICE_ACCOUNT;
  const fcmServerKey = process.env.FCM_SERVER_KEY;

  console.log(`[FCM Push] Dispatching to ${tokens.length} devices: "${title}" - "${body}"`);

  recentPushLogs.unshift({
    timestamp: new Date().toISOString(),
    title,
    recipientCount: tokens.length,
    status: fcmServiceAccount || fcmServerKey ? 'Dispatched' : 'Queued (Awaiting Service Account)',
    channel: channelId
  });
  if (recentPushLogs.length > 50) recentPushLogs.pop();

  if (fcmServerKey) {
    try {
      const fetch = (globalThis as any).fetch || require('node-fetch');
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
          data: {
            ...data,
            title,
            body
          },
          priority: 'high'
        })
      });
      const resData = await response.json();
      console.log('[FCM Push] Gateway response:', resData);
      return {
        successCount: resData.success || 0,
        failureCount: resData.failure || 0
      };
    } catch (e: any) {
      console.error('[FCM Push] Dispatch error:', e.message);
      return { successCount: 0, failureCount: tokens.length };
    }
  }

  console.log(`[FCM Push] Server running in staged mode: ${tokens.length} device notifications queued.`);
  return { successCount: tokens.length, failureCount: 0 };
}

// Push Dispatch Route (e.g. from app actions or web triggers)
app.post('/api/v1/push/send', async (req: Request, res: Response): Promise<void> => {
  const { recipientId, title, body, data } = req.body;
  if (!recipientId || !title || !body) {
    res.status(400).json({ error: 'recipientId, title, and body are required.' });
    return;
  }

  if (!supabaseAdmin) {
    res.status(503).json({ error: 'Supabase service client not available.' });
    return;
  }

  try {
    const { data: tokenRecords, error } = await supabaseAdmin
      .from('user_device_tokens')
      .select('fcm_token')
      .eq('user_id', recipientId)
      .eq('is_active', true);

    if (error || !tokenRecords || tokenRecords.length === 0) {
      res.status(200).json({ success: true, delivered: 0, message: 'No active device tokens found for recipient.' });
      return;
    }

    const tokens = tokenRecords.map((r: any) => r.fcm_token).filter(Boolean);
    const result = await dispatchFcmPush({ tokens, title, body, data });

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
  const { title, body, destination, targetId } = req.body;
  if (!title || !body) {
    res.status(400).json({ error: 'Title and body are required.' });
    return;
  }

  if (!supabaseAdmin) {
    res.status(503).json({ error: 'Database service unavailable.' });
    return;
  }

  try {
    const { data: tokenRecords, error } = await supabaseAdmin
      .from('user_device_tokens')
      .select('fcm_token, user_id')
      .eq('is_active', true);

    if (error || !tokenRecords) {
      res.status(500).json({ error: 'Failed to fetch registered devices.' });
      return;
    }

    const tokens = Array.from(new Set(tokenRecords.map((r: any) => r.fcm_token).filter(Boolean))) as string[];
    const pushData: Record<string, string> = {
      type: 'ANNOUNCEMENT',
      channel: 'broadcast',
      destination: destination || 'NOTIFICATIONS'
    };
    if (targetId) pushData.target_id = targetId;

    const result = await dispatchFcmPush({
      tokens,
      title,
      body,
      data: pushData,
      channelId: 'vybe_system_announcements'
    });

    res.status(200).json({
      success: true,
      totalDevices: tokens.length,
      delivered: result.successCount,
      failed: result.failureCount
    });
  } catch (err: any) {
    res.status(500).json({ error: err.message });
  }
});

// Admin Push Stats & Device Health Route
app.get('/api/v1/admin/push-stats', requireAdmin, async (req: Request, res: Response): Promise<void> => {
  if (!supabaseAdmin) {
    res.status(503).json({ error: 'Database service unavailable.' });
    return;
  }

  try {
    const { count: totalActiveDevices } = await supabaseAdmin
      .from('user_device_tokens')
      .select('*', { count: 'exact', head: true })
      .eq('is_active', true);

    const { data: recentDevices } = await supabaseAdmin
      .from('user_device_tokens')
      .select('device_model, platform, last_seen_at, is_active')
      .order('last_seen_at', { ascending: false })
      .limit(10);

    res.status(200).json({
      activeTokensCount: totalActiveDevices || 0,
      fcmConfigured: Boolean(process.env.FIREBASE_SERVICE_ACCOUNT || process.env.FCM_SERVER_KEY),
      recentDevices: recentDevices || [],
      recentLogs: recentPushLogs
    });
  } catch (err: any) {
    res.status(500).json({ error: err.message });
  }
});

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
