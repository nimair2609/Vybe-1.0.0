-- ====================================================================
-- VYBE 3.0 MASTER DATABASE MIGRATION
-- Production PostgreSQL + Supabase Schema with Complete Row Level Security
-- Product of NR Next | NR Group
-- ====================================================================

-- 1. EXTENSIONS
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 2. ENUMS
CREATE TYPE user_role_enum AS ENUM ('USER', 'CREATOR', 'BUSINESS_OWNER', 'VERIFIED_PUBLIC_FIGURE', 'MODERATOR', 'ADMIN');
CREATE TYPE live_pulse_enum AS ENUM ('OPEN_TO_CHAT', 'FOCUSED', 'DEEP_DIVE', 'LOOKING_FOR_ADVENTURE', 'QUIET_MODE', 'NONE');
CREATE TYPE mood_type_enum AS ENUM ('HAPPY', 'SAD', 'PEACEFUL', 'HOPEFUL', 'REFLECTIVE', 'LONELY', 'MOTIVATED', 'GRATEFUL', 'EXCITED', 'HEARTBREAK', 'CHILL', 'HEALING', 'FUN');
CREATE TYPE media_type_enum AS ENUM ('TEXT', 'IMAGE', 'VIDEO', 'REEL');
CREATE TYPE room_mode_enum AS ENUM ('TEXT_ONLY', 'VOICE_AND_TEXT', 'VOICE_ONLY');
CREATE TYPE connection_status_enum AS ENUM ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED');
CREATE TYPE business_verification_status_enum AS ENUM ('PENDING', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'MORE_INFO_REQUIRED');
CREATE TYPE report_status_enum AS ENUM ('OPEN', 'INVESTIGATING', 'RESOLVED', 'DISMISSED');

-- 3. PROFILES
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    username VARCHAR(50) UNIQUE NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    first_name VARCHAR(50),
    last_name VARCHAR(50),
    bio TEXT,
    avatar_url TEXT,
    cover_url TEXT,
    profile_video_url TEXT, -- 5-second maximum profile video banner
    email VARCHAR(255) UNIQUE,
    phone VARCHAR(30),
    date_of_birth DATE,
    age INT,
    is_under_18 BOOLEAN DEFAULT FALSE,
    role user_role_enum DEFAULT 'USER',
    is_verified BOOLEAN DEFAULT FALSE,
    is_vip BOOLEAN DEFAULT FALSE,
    is_verified_public_figure BOOLEAN DEFAULT FALSE,
    is_verified_business BOOLEAN DEFAULT FALSE,
    current_mood mood_type_enum,
    live_pulse live_pulse_enum DEFAULT 'OPEN_TO_CHAT',
    is_spiritual_mode_enabled BOOLEAN DEFAULT FALSE,
    is_private_account BOOLEAN DEFAULT FALSE,
    followers_count INT DEFAULT 0,
    following_count INT DEFAULT 0,
    posts_count INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 4. USER SETTINGS
CREATE TABLE IF NOT EXISTS public.user_settings (
    user_id UUID PRIMARY KEY REFERENCES public.profiles(id) ON DELETE CASCADE,
    theme_mode VARCHAR(20) DEFAULT 'DARK',
    daily_usage_limit_minutes INT DEFAULT 90,
    is_daily_limit_enabled BOOLEAN DEFAULT FALSE,
    break_reminder_minutes INT DEFAULT 45,
    is_break_reminder_enabled BOOLEAN DEFAULT TRUE,
    hidden_chat_pin_hash TEXT, -- bcrypt hash of user's 4-digit PIN for hidden conversations
    spiritual_daily_reminder_time TIME,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 5. NOTIFICATION PREFERENCES
CREATE TABLE IF NOT EXISTS public.notification_preferences (
    user_id UUID PRIMARY KEY REFERENCES public.profiles(id) ON DELETE CASCADE,
    push_direct_messages BOOLEAN DEFAULT TRUE,
    push_post_reactions BOOLEAN DEFAULT TRUE,
    push_comments BOOLEAN DEFAULT TRUE,
    push_time_capsule_unlocks BOOLEAN DEFAULT TRUE,
    push_crossed_paths_requests BOOLEAN DEFAULT TRUE,
    push_unload_room_activity BOOLEAN DEFAULT FALSE,
    push_live_streams BOOLEAN DEFAULT TRUE,
    push_spiritual_reminders BOOLEAN DEFAULT TRUE,
    email_security_alerts BOOLEAN DEFAULT TRUE,
    email_weekly_digest BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 6. FOLLOWS
CREATE TABLE IF NOT EXISTS public.follows (
    follower_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    followed_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (follower_id, followed_id)
);

-- 7. POSTS & POST MEDIA
CREATE TABLE IF NOT EXISTS public.posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    caption TEXT,
    media_type media_type_enum DEFAULT 'TEXT',
    mood_zone mood_type_enum,
    is_unfiltered BOOLEAN DEFAULT FALSE, -- "Back to Real. Back to Us." authentic raw moments
    is_vybes_reel BOOLEAN DEFAULT FALSE, -- Short video stream
    is_spiritual BOOLEAN DEFAULT FALSE,  -- Spiritual Mode reflection
    is_archived BOOLEAN DEFAULT FALSE,
    likes_count INT DEFAULT 0,
    comments_count INT DEFAULT 0,
    saves_count INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.post_media (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES public.posts(id) ON DELETE CASCADE,
    media_url TEXT NOT NULL,
    thumbnail_url TEXT,
    media_type media_type_enum NOT NULL,
    display_order INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 8. COMMENTS
CREATE TABLE IF NOT EXISTS public.comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES public.posts(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 9. POST REACTIONS (Distinctive VYBE Reactions: ⚡ Pure Energy, 💜 Deep Vibe, ✨ Uplift, 🌊 Calm, 🔥 Heat)
CREATE TABLE IF NOT EXISTS public.post_reactions (
    post_id UUID REFERENCES public.posts(id) ON DELETE CASCADE,
    user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    reaction_type VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (post_id, user_id)
);

-- 10. SAVED POSTS
CREATE TABLE IF NOT EXISTS public.saved_posts (
    user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    post_id UUID REFERENCES public.posts(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (user_id, post_id)
);

-- 11. STORIES
CREATE TABLE IF NOT EXISTS public.stories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    media_url TEXT NOT NULL,
    story_type VARCHAR(20) DEFAULT 'PHOTO', -- PHOTO, VIDEO, TEXT, MUSIC
    caption TEXT,
    expires_at TIMESTAMPTZ DEFAULT (NOW() + INTERVAL '24 hours'),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 12. NOTIFICATIONS
CREATE TABLE IF NOT EXISTS public.notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    actor_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    type VARCHAR(50) NOT NULL, -- COMMENT, REACTION, FOLLOW, DM, CROSSED_PATHS, TIME_CAPSULE, UNLOAD_INVITE
    title VARCHAR(150) NOT NULL,
    message TEXT NOT NULL,
    target_id VARCHAR(100),
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 13. CONVERSATIONS & ADVANCED MESSAGING (DMs, Group, Hidden PIN)
CREATE TABLE IF NOT EXISTS public.conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    is_group BOOLEAN DEFAULT FALSE,
    group_name VARCHAR(100),
    group_image_url TEXT,
    created_by UUID REFERENCES public.profiles(id),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.conversation_members (
    conversation_id UUID REFERENCES public.conversations(id) ON DELETE CASCADE,
    user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    is_admin BOOLEAN DEFAULT FALSE,
    nickname VARCHAR(50),
    is_hidden BOOLEAN DEFAULT FALSE, -- Marked as hidden behind user PIN
    is_muted BOOLEAN DEFAULT FALSE,
    joined_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (conversation_id, user_id)
);

CREATE TABLE IF NOT EXISTS public.messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES public.conversations(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    media_url TEXT,
    media_type VARCHAR(30) DEFAULT 'TEXT',
    reply_to_message_id UUID REFERENCES public.messages(id) ON DELETE SET NULL,
    is_deleted_for_everyone BOOLEAN DEFAULT FALSE,
    is_edited BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.message_reactions (
    message_id UUID REFERENCES public.messages(id) ON DELETE CASCADE,
    user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    reaction VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (message_id, user_id)
);

CREATE TABLE IF NOT EXISTS public.message_receipts (
    message_id UUID REFERENCES public.messages(id) ON DELETE CASCADE,
    user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    is_delivered BOOLEAN DEFAULT TRUE,
    is_read BOOLEAN DEFAULT FALSE,
    read_at TIMESTAMPTZ,
    PRIMARY KEY (message_id, user_id)
);

-- 14. VYBE UNLOAD: 24/7 LIVE LISTENING ROOMS
CREATE TABLE IF NOT EXISTS public.rooms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    host_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    title VARCHAR(150) NOT NULL,
    description TEXT NOT NULL,
    topic_tag VARCHAR(50) NOT NULL,
    mode room_mode_enum DEFAULT 'VOICE_AND_TEXT',
    capacity INT DEFAULT 30,
    is_public BOOLEAN DEFAULT TRUE,
    requires_approval BOOLEAN DEFAULT FALSE,
    rules_text TEXT,
    welcome_message TEXT,
    image_url TEXT,
    terms_version VARCHAR(20) DEFAULT 'v3.0',
    is_active BOOLEAN DEFAULT TRUE,
    scheduled_start TIMESTAMPTZ DEFAULT NOW(),
    scheduled_end TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.room_members (
    room_id UUID REFERENCES public.rooms(id) ON DELETE CASCADE,
    user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    is_moderator BOOLEAN DEFAULT FALSE,
    is_approved BOOLEAN DEFAULT TRUE,
    terms_accepted_at TIMESTAMPTZ DEFAULT NOW(),
    joined_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (room_id, user_id)
);

CREATE TABLE IF NOT EXISTS public.room_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES public.rooms(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    sender_alias VARCHAR(50) NOT NULL,
    is_anonymous BOOLEAN DEFAULT FALSE,
    text TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 15. AUDIENCE POLLS / WHAT NEXT
CREATE TABLE IF NOT EXISTS public.audience_pools (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    category VARCHAR(50) NOT NULL,
    audience VARCHAR(30) DEFAULT 'PUBLIC', -- PUBLIC, FOLLOWERS, SELECTED
    closes_at TIMESTAMPTZ NOT NULL,
    total_votes INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.audience_pool_options (
    id SERIAL PRIMARY KEY,
    pool_id UUID NOT NULL REFERENCES public.audience_pools(id) ON DELETE CASCADE,
    option_text TEXT NOT NULL,
    votes_count INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS public.audience_pool_votes (
    pool_id UUID REFERENCES public.audience_pools(id) ON DELETE CASCADE,
    user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    option_id INT NOT NULL REFERENCES public.audience_pool_options(id) ON DELETE CASCADE,
    voted_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (pool_id, user_id) -- Enforces 1 vote per user per poll
);

-- 16. TIME CAPSULES
CREATE TABLE IF NOT EXISTS public.time_capsules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    title VARCHAR(150) NOT NULL,
    recipient_summary VARCHAR(100) NOT NULL,
    sealed_message TEXT NOT NULL,
    media_url TEXT,
    unlock_date DATE NOT NULL,
    expires_at TIMESTAMPTZ, -- 24h disappearance rule after scheduled release
    is_unlocked BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.time_capsule_members (
    capsule_id UUID REFERENCES public.time_capsules(id) ON DELETE CASCADE,
    user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    status VARCHAR(20) DEFAULT 'INVITED', -- INVITED, ACCEPTED, DECLINED
    responded_at TIMESTAMPTZ,
    PRIMARY KEY (capsule_id, user_id)
);

-- 17. DOUBLE TAKE
CREATE TABLE IF NOT EXISTS public.double_takes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    partner_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    prompt_question TEXT NOT NULL,
    category VARCHAR(50) DEFAULT 'Fun',
    creator_answer TEXT,
    partner_answer TEXT,
    is_creator_submitted BOOLEAN DEFAULT FALSE,
    is_partner_submitted BOOLEAN DEFAULT FALSE,
    is_revealed BOOLEAN DEFAULT FALSE,
    is_matched BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 18. CROSSED PATHS (Consent-First Missed Connections)
CREATE TABLE IF NOT EXISTS public.crossed_paths (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    neighborhood_or_venue VARCHAR(150) NOT NULL, -- Broad privacy-friendly area
    description TEXT NOT NULL,
    time_window VARCHAR(50) NOT NULL,
    photo_url TEXT,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    expires_at TIMESTAMPTZ DEFAULT (NOW() + INTERVAL '72 hours'),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.crossed_path_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id UUID NOT NULL REFERENCES public.crossed_paths(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    status connection_status_enum DEFAULT 'PENDING',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 19. VERIFIED BUSINESS SHOPS & REVIEWS
CREATE TABLE IF NOT EXISTS public.business_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    business_name VARCHAR(150) NOT NULL,
    category VARCHAR(80) NOT NULL,
    gstin_or_registration_number VARCHAR(50),
    description TEXT,
    logo_url TEXT,
    cover_image_url TEXT,
    city VARCHAR(80),
    full_address TEXT,
    verification_status business_verification_status_enum DEFAULT 'PENDING',
    verification_notes TEXT,
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.business_products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES public.business_profiles(id) ON DELETE CASCADE,
    name VARCHAR(150) NOT NULL,
    price_formatted VARCHAR(30) NOT NULL,
    description TEXT,
    image_url TEXT,
    in_stock BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.business_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES public.business_profiles(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    rating INT CHECK (rating >= 1 AND rating <= 5),
    review_text TEXT NOT NULL,
    photo_url TEXT,
    owner_response TEXT,
    is_moderated BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 20. SPIRITUAL MODE (Reflections, Intentions, Gratitude)
CREATE TABLE IF NOT EXISTS public.spiritual_intentions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    intention_text TEXT NOT NULL,
    is_fulfilled BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.spiritual_gratitudes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    gratitude_entry TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 21. LIVE STREAMS / GO LIVE
CREATE TABLE IF NOT EXISTS public.live_streams (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    host_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    title VARCHAR(150) NOT NULL,
    visibility VARCHAR(30) DEFAULT 'PUBLIC',
    is_live BOOLEAN DEFAULT TRUE,
    viewer_count INT DEFAULT 0,
    started_at TIMESTAMPTZ DEFAULT NOW(),
    ended_at TIMESTAMPTZ
);

-- 22. MODERATION, REPORTS, BLOCKS & AUDIT LOGS
CREATE TABLE IF NOT EXISTS public.blocks (
    blocker_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    blocked_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (blocker_id, blocked_id)
);

CREATE TABLE IF NOT EXISTS public.reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    target_type VARCHAR(30) NOT NULL, -- USER, POST, ROOM, LIVE_STREAM, REVIEW
    target_id VARCHAR(100) NOT NULL,
    reason TEXT NOT NULL,
    status report_status_enum DEFAULT 'OPEN',
    admin_notes TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    action VARCHAR(100) NOT NULL,
    target_entity VARCHAR(100),
    details JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ====================================================================
-- ROW LEVEL SECURITY (RLS) POLICIES
-- Zero-Trust Security for all user-facing tables
-- ====================================================================

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.notification_preferences ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.follows ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.posts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.post_media ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.post_reactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.saved_posts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.stories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversation_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.rooms ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.room_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.room_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audience_pools ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audience_pool_options ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audience_pool_votes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.time_capsules ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.double_takes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crossed_paths ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.business_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.business_reviews ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.spiritual_intentions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.spiritual_gratitudes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reports ENABLE ROW LEVEL SECURITY;

-- 1. Profiles: Public read, owner update
CREATE POLICY "Public profiles are viewable by everyone" ON public.profiles FOR SELECT USING (true);
CREATE POLICY "Users can insert their own profile" ON public.profiles FOR INSERT WITH CHECK (auth.uid() = id);
CREATE POLICY "Users can update own profile" ON public.profiles FOR UPDATE USING (auth.uid() = id);

-- 2. User Settings & Notification Preferences: Owner only
CREATE POLICY "User settings are private to owner" ON public.user_settings FOR ALL USING (auth.uid() = user_id);
CREATE POLICY "Notification preferences private to owner" ON public.notification_preferences FOR ALL USING (auth.uid() = user_id);

-- 3. Posts: Public can view non-archived; author full access
CREATE POLICY "Public can view active posts" ON public.posts FOR SELECT USING (is_archived = false OR auth.uid() = author_id);
CREATE POLICY "Users can create own posts" ON public.posts FOR INSERT WITH CHECK (auth.uid() = author_id);
CREATE POLICY "Users can update own posts" ON public.posts FOR UPDATE USING (auth.uid() = author_id);
CREATE POLICY "Users can delete own posts" ON public.posts FOR DELETE USING (auth.uid() = author_id);

-- 4. Messages: Only conversation participants can view/send
CREATE POLICY "Participants can view messages" ON public.messages FOR SELECT
USING (EXISTS (SELECT 1 FROM public.conversation_members cm WHERE cm.conversation_id = messages.conversation_id AND cm.user_id = auth.uid()));

CREATE POLICY "Participants can send messages" ON public.messages FOR INSERT
WITH CHECK (auth.uid() = sender_id AND EXISTS (SELECT 1 FROM public.conversation_members cm WHERE cm.conversation_id = messages.conversation_id AND cm.user_id = auth.uid()));

-- 5. Time Capsules: Creator and accepted members only; sealed until unlock_date
CREATE POLICY "Time capsules viewable by creator and accepted members after unlock" ON public.time_capsules FOR SELECT
USING (auth.uid() = creator_id OR (is_unlocked = true AND EXISTS (SELECT 1 FROM public.time_capsule_members tcm WHERE tcm.capsule_id = time_capsules.id AND tcm.user_id = auth.uid() AND tcm.status = 'ACCEPTED')));

CREATE POLICY "Users can create own capsules" ON public.time_capsules FOR INSERT WITH CHECK (auth.uid() = creator_id);

-- 6. Spiritual Mode: Intentions & Gratitudes strictly private to user
CREATE POLICY "Spiritual intentions private to owner" ON public.spiritual_intentions FOR ALL USING (auth.uid() = user_id);
CREATE POLICY "Spiritual gratitudes private to owner" ON public.spiritual_gratitudes FOR ALL USING (auth.uid() = user_id);

-- 7. Audience Pools: Everyone can view; users can vote once
CREATE POLICY "Audience pools viewable by everyone" ON public.audience_pools FOR SELECT USING (true);
CREATE POLICY "Audience pool votes viewable by everyone" ON public.audience_pool_votes FOR SELECT USING (true);
CREATE POLICY "Users can submit one vote per pool" ON public.audience_pool_votes FOR INSERT WITH CHECK (auth.uid() = user_id);

-- 8. Business Verification & Admin Security
CREATE POLICY "Business profiles viewable by everyone" ON public.business_profiles FOR SELECT USING (true);
CREATE POLICY "Business owners can update their own shop profile" ON public.business_profiles FOR UPDATE USING (auth.uid() = owner_id);

-- Ensure non-admins cannot elevate their own role to ADMIN or VERIFIED
CREATE OR REPLACE FUNCTION public.check_profile_elevation()
RETURNS TRIGGER AS $$
BEGIN
    IF (OLD.role <> NEW.role OR OLD.is_verified <> NEW.is_verified OR OLD.is_vip <> NEW.is_vip) THEN
        IF NOT EXISTS (SELECT 1 FROM public.profiles WHERE id = auth.uid() AND role = 'ADMIN') THEN
            RAISE EXCEPTION 'Only administrators can modify verification badges or role status.';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE OR REPLACE TRIGGER trg_check_profile_elevation
BEFORE UPDATE ON public.profiles
FOR EACH ROW EXECUTE FUNCTION public.check_profile_elevation();
