-- ====================================================================
-- VYBE CENTRAL ADMIN CONTROL CENTER: SCHEMAS, RLS & AUDIT INTEGRATION
-- Product of NR Next | NR Group
-- Date: 2026-10-28
-- ====================================================================

-- 1. CENTRAL APPLICATION SETTINGS TABLE
CREATE TABLE IF NOT EXISTS public.app_settings (
    key VARCHAR(100) PRIMARY KEY,
    value JSONB NOT NULL,
    description TEXT,
    is_public BOOLEAN DEFAULT FALSE,
    updated_by UUID REFERENCES auth.users(id),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Seed core application settings
INSERT INTO public.app_settings (key, value, description, is_public)
VALUES
    ('general', '{"app_name": "VYBE", "brand_tagline": "Back to Real. Back to Us.", "min_supported_version": "3.2.0", "current_version": "3.5.0", "support_email": "support@vybe.nrnext.in", "grievance_email": "grievance@vybe.nrnext.in"}'::jsonb, 'Core brand and version policy', TRUE),
    ('maintenance', '{"is_active": false, "message": "VYBE is currently undergoing scheduled platform upgrades. We will be right back!"}'::jsonb, 'Maintenance mode status', TRUE),
    ('features', '{"reels_enabled": true, "stories_enabled": true, "unload_voice_enabled": true, "crossed_paths_enabled": true, "communities_enabled": true, "spiritual_mode_enabled": true}'::jsonb, 'Feature availability toggles', TRUE),
    ('limits', '{"max_video_upload_seconds": 60, "max_story_hours": 24, "max_file_size_mb": 50}'::jsonb, 'Platform upload and duration limits', TRUE),
    ('announcement', '{"is_active": false, "title": "", "message": "", "cta_url": ""}'::jsonb, 'System announcement banner', TRUE)
ON CONFLICT (key) DO NOTHING;

-- 2. AD CAMPAIGNS & PERFORMANCE TABLE
CREATE TABLE IF NOT EXISTS public.ad_campaigns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    advertiser_name VARCHAR(200) NOT NULL,
    campaign_title VARCHAR(200) NOT NULL,
    placement VARCHAR(50) NOT NULL DEFAULT 'FEED_NATIVE', -- 'FEED_NATIVE', 'REELS_IN_STREAM', 'EXPLORE_BANNER', 'STORIES_INTERSTITIAL'
    creative_url TEXT NOT NULL,
    target_cta_url TEXT NOT NULL,
    cta_button_text VARCHAR(50) DEFAULT 'Learn More',
    daily_budget_inr INT DEFAULT 1000,
    total_budget_inr INT DEFAULT 10000,
    spent_inr INT DEFAULT 0,
    impressions_count BIGINT DEFAULT 0,
    clicks_count BIGINT DEFAULT 0,
    status VARCHAR(30) DEFAULT 'ACTIVE', -- 'ACTIVE', 'PAUSED', 'SCHEDULED', 'ARCHIVED', 'DRAFT'
    start_date TIMESTAMPTZ DEFAULT NOW(),
    end_date TIMESTAMPTZ DEFAULT NOW() + INTERVAL '30 days',
    created_by UUID REFERENCES auth.users(id),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ad_campaigns_status ON public.ad_campaigns (status, placement);

-- 3. AD IMPRESSIONS & CLICKS LOGS
CREATE TABLE IF NOT EXISTS public.ad_interactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id UUID NOT NULL REFERENCES public.ad_campaigns(id) ON DELETE CASCADE,
    user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    interaction_type VARCHAR(20) NOT NULL, -- 'IMPRESSION', 'CLICK'
    placement VARCHAR(50) NOT NULL,
    device_info VARCHAR(100),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ad_interactions_campaign ON public.ad_interactions (campaign_id, interaction_type);

-- 4. API & INTEGRATION HEALTH REGISTRY TABLE
CREATE TABLE IF NOT EXISTS public.api_integrations (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    provider VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    purpose TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'HEALTHY', -- 'HEALTHY', 'DEGRADED', 'MISSING', 'DEPLOYMENT_REQUIRED'
    masked_identifier VARCHAR(100) DEFAULT 'Configured',
    required_fields JSONB DEFAULT '[]'::jsonb,
    documentation_url TEXT,
    setup_instructions TEXT,
    last_ping_at TIMESTAMPTZ DEFAULT NOW(),
    last_ping_status VARCHAR(50) DEFAULT 'OK',
    last_error_message TEXT,
    is_enabled BOOLEAN DEFAULT TRUE,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Seed initial integration status records
INSERT INTO public.api_integrations (id, name, provider, category, purpose, status, masked_identifier, required_fields, documentation_url, setup_instructions)
VALUES
    ('supabase_core', 'Supabase Real-Time Backend', 'Supabase (PostgreSQL)', 'Database & Auth', 'Primary user identity, zero-trust database, instant post/comment streaming and storage', 'HEALTHY', 'cppwadedazpbevkfrchg', '["SUPABASE_URL", "SUPABASE_ANON_KEY"]', 'https://supabase.com/docs', 'Active in project. Client tokens securely refreshed via OAuth & SessionManager.'),
    ('google_maps', 'Google Maps & Places SDK', 'Google Cloud Platform', 'Maps & Geolocation', 'Enables Crossed Paths venue discovery and physical store verification mapping', 'HEALTHY', 'AIzaSy••••••••4xQp', '["GOOGLE_MAPS_API_KEY"]', 'https://developers.google.com/maps', 'Injected at build-time via Secrets plugin and AndroidManifest meta-data.'),
    ('push_fcm', 'Cloud Messaging & Push Alerts', 'Firebase Cloud Messaging / Web Push', 'Notifications & Messaging', 'Delivers real-time notifications for incoming messages, mentions, and urgent community alerts', 'HEALTHY', 'Configured', '["google-services.json", "VAPID_PUBLIC_KEY"]', 'https://firebase.google.com/docs/cloud-messaging', 'Handled through applet notification service and client websocket fallback.'),
    ('email_auth', 'Transactional Auth & Recovery Email', 'Supabase Auth SMTP / NR Next Mail', 'Email & Verification', 'Handles user confirmation emails, password reset links, and login magic links', 'HEALTHY', 'support@vybe.nrnext.in', '["SMTP_HOST", "SMTP_PORT", "SMTP_USER"]', 'https://supabase.com/docs/guides/auth/auth-smtp', 'Default Supabase Auth email service enabled with custom sender address.'),
    ('payment_gateway', 'Payment Processing Gateway', 'Razorpay & Stripe Integration', 'Payments', 'Supports Founding Creator subscriptions, creator tipping, and verified business shop fees', 'DEGRADED', 'rzp_test_••••••••', '["RAZORPAY_KEY_ID", "RAZORPAY_SECRET"]', 'https://razorpay.com/docs', 'Currently operating in sandbox/test mode. Production activation requires merchant KYC.'),
    ('audio_voice', 'Live Voice WebRTC STUN/TURN', 'Google STUN / Ephemeral AudioTrack', 'Audio & Media', 'Powers 24/7 Live Unload ephemeral voice rooms with sub-second latency and zero audio recording', 'HEALTHY', 'stun.l.google.com:19302', '["STUN_SERVER_URL"]', 'https://webrtc.org', 'STUN server configured. Zero-retention privacy policy enforced.'),
    ('ad_network', 'VYBE Privacy-First Ad Engine', 'Internal NR Next Ads', 'Ad & Analytics', 'Distributes native sponsored posts, reels interstitials, and explore banners with privacy guarantees', 'HEALTHY', 'Internal Engine', '["CAMPAIGN_RULES"]', 'https://vybe.nrnext.in/ads', 'Fully integrated campaign distribution engine with fraud-detection rate limits.')
ON CONFLICT (id) DO UPDATE SET
    status = EXCLUDED.status,
    masked_identifier = EXCLUDED.masked_identifier,
    updated_at = NOW();

-- 5. ENABLE ROW LEVEL SECURITY
ALTER TABLE public.app_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ad_campaigns ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ad_interactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.api_integrations ENABLE ROW LEVEL SECURITY;

-- 6. RLS POLICIES FOR APP SETTINGS
DROP POLICY IF EXISTS "Public can view public app settings" ON public.app_settings;
CREATE POLICY "Public can view public app settings"
    ON public.app_settings FOR SELECT
    USING (is_public = TRUE OR public.is_admin(auth.uid()));

DROP POLICY IF EXISTS "Admins can update app settings" ON public.app_settings;
CREATE POLICY "Admins can update app settings"
    ON public.app_settings FOR ALL
    USING (public.is_admin(auth.uid()))
    WITH CHECK (public.is_admin(auth.uid()));

-- 7. RLS POLICIES FOR AD CAMPAIGNS
DROP POLICY IF EXISTS "Public can view active ad campaigns" ON public.ad_campaigns;
CREATE POLICY "Public can view active ad campaigns"
    ON public.ad_campaigns FOR SELECT
    USING (status = 'ACTIVE' OR public.is_admin(auth.uid()));

DROP POLICY IF EXISTS "Admins have full access to ad campaigns" ON public.ad_campaigns;
CREATE POLICY "Admins have full access to ad campaigns"
    ON public.ad_campaigns FOR ALL
    USING (public.is_admin(auth.uid()))
    WITH CHECK (public.is_admin(auth.uid()));

-- 8. RLS POLICIES FOR AD INTERACTIONS
DROP POLICY IF EXISTS "Authenticated users can record ad interactions" ON public.ad_interactions;
CREATE POLICY "Authenticated users can record ad interactions"
    ON public.ad_interactions FOR INSERT
    WITH CHECK (true);

DROP POLICY IF EXISTS "Admins can view ad interactions" ON public.ad_interactions;
CREATE POLICY "Admins can view ad interactions"
    ON public.ad_interactions FOR SELECT
    USING (public.is_admin(auth.uid()));

-- 9. RLS POLICIES FOR API INTEGRATIONS
DROP POLICY IF EXISTS "Admins can view and manage API integrations" ON public.api_integrations;
CREATE POLICY "Admins can view and manage API integrations"
    ON public.api_integrations FOR ALL
    USING (public.is_admin(auth.uid()))
    WITH CHECK (public.is_admin(auth.uid()));
