import express, { Request, Response, NextFunction } from 'express';
import cors from 'cors';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import dotenv from 'dotenv';
import { createClient } from '@supabase/supabase-js';

dotenv.config();

const app = express();
const PORT = process.env.PORT || 8080;

// Security & Middleware
app.use(helmet());
app.use(cors({
  origin: process.env.ALLOWED_ORIGINS ? process.env.ALLOWED_ORIGINS.split(',') : '*',
  credentials: true
}));
app.use(express.json({ limit: '10mb' }));

// Rate Limiting (Abuse Prevention)
const generalLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 200,
  message: { error: 'Too many requests from this IP, please try again later.' }
});
app.use('/api/', generalLimiter);

// Supabase Service Client (Using SERVICE_ROLE_KEY securely on the server only)
const supabaseUrl = process.env.SUPABASE_URL || '';
const supabaseServiceKey = process.env.SUPABASE_SERVICE_ROLE_KEY || '';

const supabaseAdmin = (supabaseUrl && supabaseServiceKey) 
  ? createClient(supabaseUrl, supabaseServiceKey)
  : null;

// 1. Health-Check Endpoint
app.get('/health', (req: Request, res: Response) => {
  res.status(200).json({
    status: 'healthy',
    product: 'VYBE 3.0 API',
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

  if (!profile || profile.role !== 'ADMIN') {
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
    unlockedCount: toUnlock?.length || 0,
    deletedExpiredCount: toDelete?.length || 0
  });
});

// 5. Global Error Handler
app.use((err: Error, req: Request, res: Response, next: NextFunction) => {
  console.error('[VYBE Server Error]:', err.message);
  res.status(500).json({ error: 'Internal server error occurred.' });
});

app.listen(PORT, () => {
  console.log(`[VYBE 3.0 API] Listening on port ${PORT}`);
});
