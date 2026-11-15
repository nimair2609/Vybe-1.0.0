-- ============================================================================
-- VYBE 2.0 — ADDITIONS: OWNER SECURITY, PLATFORM CONFIG, AUDIO CATALOG,
--            STORY COMMENTS, ADMIN METRICS RPC & STORAGE BUCKETS
-- Migration: 20261117000001_vybe_2_0_additions.sql
-- Prerequisite: Execute AFTER 20261116000001_vybe_2_0_master_upgrade.sql
-- ============================================================================

-- ============================================================================
-- 1. OWNER_ADMIN / SUPER_ADMIN AUTHORIZATION HARDENING
-- ============================================================================
CREATE OR REPLACE FUNCTION public.is_owner_admin(p_user_id UUID DEFAULT auth.uid())
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
    SELECT CASE
        WHEN p_user_id IS NULL THEN FALSE
        ELSE EXISTS (
            SELECT 1
            FROM public.admin_users au
            WHERE au.user_id = p_user_id
              AND au.is_active = TRUE
              AND UPPER(COALESCE(au.role, '')) IN ('OWNER_ADMIN', 'SUPER_ADMIN')
        )
    END;
$$;

REVOKE ALL ON FUNCTION public.is_owner_admin(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.is_owner_admin(UUID) TO authenticated, service_role;

-- Update public.is_admin if present to guarantee OWNER_ADMIN & sub-roles are recognized
CREATE OR REPLACE FUNCTION public.is_admin(p_user_id UUID DEFAULT auth.uid())
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
    SELECT CASE
        WHEN p_user_id IS NULL THEN FALSE
        ELSE EXISTS (
            SELECT 1
            FROM public.admin_users au
            WHERE au.user_id = p_user_id
              AND au.is_active = TRUE
              AND UPPER(COALESCE(au.role, '')) IN (
                  'OWNER_ADMIN', 'SUPER_ADMIN', 'ADMIN', 'MODERATOR', 'SUPPORT_ADMIN', 'ANALYST'
              )
        )
    END;
$$;

REVOKE ALL ON FUNCTION public.is_admin(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.is_admin(UUID) TO authenticated, service_role;

-- Prevent ordinary admins or users from escalating themselves or touching OWNER_ADMIN rows
CREATE OR REPLACE FUNCTION public.guard_admin_users_owner_only()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_jwt_role TEXT := COALESCE(pg_catalog.current_setting('request.jwt.claim.role', true), '');
    v_caller UUID := auth.uid();
    v_old_role TEXT := CASE WHEN TG_OP IN ('UPDATE', 'DELETE') THEN UPPER(COALESCE(OLD.role, '')) ELSE '' END;
    v_new_role TEXT := CASE WHEN TG_OP IN ('INSERT', 'UPDATE') THEN UPPER(COALESCE(NEW.role, '')) ELSE '' END;
BEGIN
    IF v_jwt_role = 'service_role'
       OR (v_jwt_role = '' AND CURRENT_USER IN ('postgres', 'supabase_admin')) THEN
        RETURN COALESCE(NEW, OLD);
    END IF;

    IF v_caller IS NULL OR NOT public.is_owner_admin(v_caller) THEN
        RAISE EXCEPTION 'Forbidden: Only an authorized OWNER_ADMIN may modify administrator records or roles.';
    END IF;

    IF TG_OP IN ('UPDATE', 'DELETE') AND OLD.user_id = v_caller THEN
        RAISE EXCEPTION 'Security Policy: Administrators cannot modify or revoke their own admin record.';
    END IF;

    IF TG_OP = 'UPDATE' AND v_old_role IN ('OWNER_ADMIN', 'SUPER_ADMIN') AND v_new_role NOT IN ('OWNER_ADMIN', 'SUPER_ADMIN') THEN
        RAISE EXCEPTION 'Security Policy: Existing OWNER_ADMIN privilege cannot be downgraded via client write.';
    END IF;

    RETURN COALESCE(NEW, OLD);
END;
$$;

DROP TRIGGER IF EXISTS trg_guard_admin_users_owner_only ON public.admin_users;
CREATE TRIGGER trg_guard_admin_users_owner_only
    BEFORE INSERT OR UPDATE OR DELETE ON public.admin_users
    FOR EACH ROW
    EXECUTE FUNCTION public.guard_admin_users_owner_only();

-- ============================================================================
-- 2. GLOBAL PLATFORM CONFIG (STATELESS PERSISTENCE ACROSS NETLIFY & EXPRESS)
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.platform_config (
    id INT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    feature_flags JSONB NOT NULL DEFAULT '{
        "unload": true,
        "unfiltered": true,
        "what_next": true,
        "time_capsule": true,
        "double_take": true,
        "crossed_paths": true,
        "live_stream": true,
        "voice_calls": true,
        "growEcosystem": true,
        "growthCircles": true,
        "stories2": true,
        "unloadRooms": true,
        "crossedPaths": true,
        "timeCapsules": true,
        "spiritualMode": true
    }'::jsonb,
    maintenance_mode BOOLEAN NOT NULL DEFAULT FALSE,
    maintenance_message TEXT NOT NULL DEFAULT 'VYBE is undergoing scheduled performance upgrades. We''ll be back shortly!',
    announcement_banner TEXT NOT NULL DEFAULT 'Welcome to VYBE 2.0 — Meaningful connections, Circles & GROW.',
    announcement_active BOOLEAN NOT NULL DEFAULT FALSE,
    announcement_link TEXT NOT NULL DEFAULT '',
    community_guidelines_url TEXT NOT NULL DEFAULT 'https://vybeonline.netlify.app/community-guidelines',
    updated_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO public.platform_config (id) VALUES (1) ON CONFLICT (id) DO NOTHING;

ALTER TABLE public.platform_config ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "platform_config_public_read" ON public.platform_config;
CREATE POLICY "platform_config_public_read"
    ON public.platform_config FOR SELECT
    USING (true);

DROP POLICY IF EXISTS "platform_config_owner_write" ON public.platform_config;
CREATE POLICY "platform_config_owner_write"
    ON public.platform_config FOR ALL
    TO authenticated
    USING (public.is_owner_admin(auth.uid()) OR public.is_admin(auth.uid()))
    WITH CHECK (public.is_owner_admin(auth.uid()) OR public.is_admin(auth.uid()));

-- ============================================================================
-- 3. LIVE AUDIO TRACKS CATALOG & STORY 2.0 COMMENTS / AUDIO URL
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.audio_tracks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title TEXT NOT NULL,
    artist TEXT NOT NULL,
    album TEXT NOT NULL DEFAULT '',
    genre TEXT NOT NULL DEFAULT 'Ambient',
    duration_sec INT NOT NULL DEFAULT 30 CHECK (duration_sec > 0 AND duration_sec <= 600),
    stream_url TEXT NOT NULL,
    cover_url TEXT DEFAULT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.audio_tracks ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "audio_tracks_read_active" ON public.audio_tracks;
CREATE POLICY "audio_tracks_read_active"
    ON public.audio_tracks FOR SELECT
    USING (is_active = TRUE OR public.is_admin(auth.uid()));

DROP POLICY IF EXISTS "audio_tracks_admin_manage" ON public.audio_tracks;
CREATE POLICY "audio_tracks_admin_manage"
    ON public.audio_tracks FOR ALL
    TO authenticated
    USING (public.is_admin(auth.uid()))
    WITH CHECK (public.is_admin(auth.uid()));

ALTER TABLE public.stories
    ADD COLUMN IF NOT EXISTS audio_track_url TEXT DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS comments_count INT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS public.story_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    story_id UUID NOT NULL REFERENCES public.stories(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    content TEXT NOT NULL CHECK (char_length(trim(content)) >= 1 AND char_length(content) <= 500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_story_comments_story_created
    ON public.story_comments (story_id, created_at ASC);

CREATE OR REPLACE FUNCTION public.sync_story_comments_count()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_sid UUID := COALESCE(NEW.story_id, OLD.story_id);
BEGIN
    UPDATE public.stories
    SET comments_count = (
        SELECT COUNT(*)::INT FROM public.story_comments WHERE story_id = v_sid
    )
    WHERE id = v_sid;
    RETURN COALESCE(NEW, OLD);
END;
$$;

DROP TRIGGER IF EXISTS trg_sync_story_comments_count ON public.story_comments;
CREATE TRIGGER trg_sync_story_comments_count
    AFTER INSERT OR DELETE ON public.story_comments
    FOR EACH ROW EXECUTE FUNCTION public.sync_story_comments_count();

ALTER TABLE public.story_comments ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "story_comments_select_policy" ON public.story_comments;
CREATE POLICY "story_comments_select_policy"
    ON public.story_comments FOR SELECT
    TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.stories s
            WHERE s.id = story_comments.story_id
              AND (
                  s.author_id = auth.uid()
                  OR public.is_admin(auth.uid())
                  OR (
                      NOT public.is_blocked_between(s.author_id, auth.uid())
                      AND NOT public.is_blocked_between(story_comments.author_id, auth.uid())
                      AND public.can_view_story(s.author_id, s.audience, s.expires_at, auth.uid())
                  )
              )
        )
    );

DROP POLICY IF EXISTS "story_comments_insert_policy" ON public.story_comments;
CREATE POLICY "story_comments_insert_policy"
    ON public.story_comments FOR INSERT
    TO authenticated
    WITH CHECK (
        auth.uid() = author_id
        AND EXISTS (
            SELECT 1 FROM public.stories s
            WHERE s.id = story_comments.story_id
              AND (
                  s.author_id = auth.uid()
                  OR (
                      NOT public.is_blocked_between(s.author_id, auth.uid())
                      AND public.can_view_story(s.author_id, s.audience, s.expires_at, auth.uid())
                  )
              )
        )
    );

DROP POLICY IF EXISTS "story_comments_delete_policy" ON public.story_comments;
CREATE POLICY "story_comments_delete_policy"
    ON public.story_comments FOR DELETE
    TO authenticated
    USING (
        auth.uid() = author_id
        OR public.is_admin(auth.uid())
        OR EXISTS (
            SELECT 1 FROM public.stories s
            WHERE s.id = story_comments.story_id AND s.author_id = auth.uid()
        )
    );

-- ============================================================================
-- 4. EXPAND OPPORTUNITIES CATEGORIES TO COVER ALL 10 GROW 2.0 CATEGORIES
-- ============================================================================
DO $$
DECLARE
    v_constraint_name TEXT;
BEGIN
    SELECT con.conname INTO v_constraint_name
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
    WHERE nsp.nspname = 'public'
      AND rel.relname = 'opportunities'
      AND con.contype = 'c'
      AND pg_get_constraintdef(con.oid) ILIKE '%category%';

    IF v_constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE public.opportunities DROP CONSTRAINT %I', v_constraint_name);
    END IF;
END $$;

ALTER TABLE public.opportunities
    ADD CONSTRAINT opportunities_category_check CHECK (
        category IN (
            'JOB', 'INTERNSHIP', 'FREELANCE', 'PROJECT_COLLAB', 'HACKATHON',
            'COMPETITION', 'FELLOWSHIP', 'GRANT', 'MENTORSHIP', 'WORKSHOP'
        )
    );

-- ============================================================================
-- 5. CENTRAL COMMAND CENTER LIVE METRICS RPC (OWNER / ADMIN ONLY)
-- ============================================================================
CREATE OR REPLACE FUNCTION public.get_admin_dashboard_metrics()
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_caller UUID := auth.uid();
    v_jwt_role TEXT := COALESCE(pg_catalog.current_setting('request.jwt.claim.role', true), '');
    v_total_users BIGINT := 0;
    v_dau BIGINT := 0;
    v_wau BIGINT := 0;
    v_mau BIGINT := 0;
    v_new_today BIGINT := 0;
    v_new_week BIGINT := 0;
    v_suspended BIGINT := 0;
    v_banned BIGINT := 0;
    v_verified BIGINT := 0;
    v_vip BIGINT := 0;
    v_official BIGINT := 0;
    v_total_posts BIGINT := 0;
    v_unfiltered BIGINT := 0;
    v_total_likes BIGINT := 0;
    v_total_comments BIGINT := 0;
    v_total_saves BIGINT := 0;
    v_total_stories BIGINT := 0;
    v_total_reports BIGINT := 0;
    v_pending_reports BIGINT := 0;
    v_verification_pending BIGINT := 0;
    v_circle_reports_open BIGINT := 0;
    v_opp_reports_open BIGINT := 0;
    v_circles BIGINT := 0;
    v_circle_posts BIGINT := 0;
    v_growth_programs BIGINT := 0;
    v_projects BIGINT := 0;
    v_opportunities BIGINT := 0;
    v_applications BIGINT := 0;
    v_audio_tracks BIGINT := 0;
    v_active_devices BIGINT := 0;
    v_live_rooms BIGINT := 0;
    v_polls BIGINT := 0;
    v_capsules BIGINT := 0;
    v_double_takes BIGINT := 0;
    v_crossed_paths BIGINT := 0;
BEGIN
    IF v_jwt_role <> 'service_role'
       AND NOT (v_jwt_role = '' AND CURRENT_USER IN ('postgres', 'supabase_admin')) THEN
        IF v_caller IS NULL OR NOT public.is_admin(v_caller) THEN
            RAISE EXCEPTION 'Forbidden: Active administrator verification required.';
        END IF;
    END IF;

    SELECT
        COUNT(*)::BIGINT,
        COUNT(*) FILTER (WHERE last_seen_at >= NOW() - INTERVAL '1 day')::BIGINT,
        COUNT(*) FILTER (WHERE last_seen_at >= NOW() - INTERVAL '7 days')::BIGINT,
        COUNT(*) FILTER (WHERE last_seen_at >= NOW() - INTERVAL '30 days')::BIGINT,
        COUNT(*) FILTER (WHERE created_at >= NOW() - INTERVAL '1 day')::BIGINT,
        COUNT(*) FILTER (WHERE created_at >= NOW() - INTERVAL '7 days')::BIGINT,
        COUNT(*) FILTER (WHERE COALESCE(is_suspended, FALSE) = TRUE)::BIGINT,
        COUNT(*) FILTER (WHERE COALESCE(is_banned, FALSE) = TRUE)::BIGINT,
        COUNT(*) FILTER (WHERE COALESCE(is_verified, FALSE) = TRUE)::BIGINT,
        COUNT(*) FILTER (WHERE COALESCE(is_vip, FALSE) = TRUE)::BIGINT,
        COUNT(*) FILTER (WHERE COALESCE(is_worker, FALSE) = TRUE)::BIGINT
    INTO
        v_total_users, v_dau, v_wau, v_mau, v_new_today, v_new_week,
        v_suspended, v_banned, v_verified, v_vip, v_official
    FROM public.profiles;

    SELECT
        COUNT(*)::BIGINT,
        COUNT(*) FILTER (WHERE COALESCE(is_unfiltered, FALSE) = TRUE)::BIGINT,
        COALESCE(SUM(COALESCE(likes_count, 0)), 0)::BIGINT,
        COALESCE(SUM(COALESCE(comments_count, 0)), 0)::BIGINT,
        COALESCE(SUM(COALESCE(saves_count, 0)), 0)::BIGINT
    INTO v_total_posts, v_unfiltered, v_total_likes, v_total_comments, v_total_saves
    FROM public.posts;

    SELECT COUNT(*)::BIGINT INTO v_total_stories FROM public.stories;
    SELECT COUNT(*)::BIGINT INTO v_total_reports FROM public.reports;
    SELECT COUNT(*)::BIGINT INTO v_pending_reports
    FROM public.reports WHERE UPPER(COALESCE(status, 'OPEN')) IN ('OPEN', 'PENDING', 'INVESTIGATING');
    SELECT COUNT(*)::BIGINT INTO v_verification_pending
    FROM public.verification_requests WHERE UPPER(COALESCE(status, 'PENDING')) = 'PENDING';
    SELECT COUNT(*)::BIGINT INTO v_circle_reports_open
    FROM public.community_reports WHERE UPPER(COALESCE(status, 'OPEN')) = 'OPEN';
    SELECT COUNT(*)::BIGINT INTO v_opp_reports_open
    FROM public.opportunity_reports WHERE UPPER(COALESCE(status, 'OPEN')) = 'OPEN';

    SELECT COUNT(*)::BIGINT INTO v_circles FROM public.communities;
    SELECT COUNT(*)::BIGINT INTO v_circle_posts FROM public.community_posts;
    SELECT COUNT(*)::BIGINT INTO v_growth_programs FROM public.growth_programs;
    SELECT COUNT(*)::BIGINT INTO v_projects FROM public.projects;
    SELECT COUNT(*)::BIGINT INTO v_opportunities FROM public.opportunities;
    SELECT COUNT(*)::BIGINT INTO v_applications FROM public.opportunity_applications;
    SELECT COUNT(*)::BIGINT INTO v_audio_tracks FROM public.audio_tracks WHERE is_active = TRUE;
    SELECT COUNT(*)::BIGINT INTO v_active_devices FROM public.user_device_tokens WHERE is_active = TRUE;
    SELECT COUNT(*)::BIGINT INTO v_live_rooms FROM public.rooms WHERE is_active = TRUE;
    SELECT COUNT(*)::BIGINT INTO v_polls FROM public.audience_pools;
    SELECT COUNT(*)::BIGINT INTO v_capsules FROM public.time_capsules;
    SELECT COUNT(*)::BIGINT INTO v_double_takes FROM public.double_takes;
    SELECT COUNT(*)::BIGINT INTO v_crossed_paths FROM public.crossed_paths;

    RETURN pg_catalog.jsonb_build_object(
        'users', pg_catalog.jsonb_build_object(
            'total', v_total_users,
            'dau', v_dau,
            'wau', v_wau,
            'mau', v_mau,
            'newToday', v_new_today,
            'newWeek', v_new_week,
            'suspended', v_suspended,
            'banned', v_banned,
            'verified', v_verified,
            'vip', v_vip,
            'official', v_official
        ),
        'content', pg_catalog.jsonb_build_object(
            'totalPosts', v_total_posts,
            'unfiltered', v_unfiltered,
            'standardPosts', GREATEST(0, v_total_posts - v_unfiltered),
            'stories', v_total_stories,
            'totalLikes', v_total_likes,
            'totalComments', v_total_comments,
            'totalSaves', v_total_saves,
            'totalShares', 0
        ),
        'moderation', pg_catalog.jsonb_build_object(
            'totalReports', v_total_reports,
            'pendingReports', v_pending_reports,
            'verificationPending', v_verification_pending,
            'circleReportsOpen', v_circle_reports_open,
            'opportunityReportsOpen', v_opp_reports_open
        ),
        'ecosystem', pg_catalog.jsonb_build_object(
            'circles', v_circles,
            'circlePosts', v_circle_posts,
            'growthPrograms', v_growth_programs,
            'projects', v_projects,
            'opportunities', v_opportunities,
            'applications', v_applications,
            'audioTracks', v_audio_tracks
        ),
        'push', pg_catalog.jsonb_build_object(
            'activeDevices', v_active_devices
        ),
        'experiences', pg_catalog.jsonb_build_object(
            'liveRooms', v_live_rooms,
            'polls', v_polls,
            'capsules', v_capsules,
            'doubleTakes', v_double_takes,
            'crossedPaths', v_crossed_paths
        )
    );
END;
$$;

REVOKE ALL ON FUNCTION public.get_admin_dashboard_metrics() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_admin_dashboard_metrics() TO authenticated, service_role;

-- ============================================================================
-- 6. STORAGE BUCKETS ('projects', 'program-covers', 'circle-resources', 'resumes')
-- ============================================================================
INSERT INTO storage.buckets (id, name, public, file_size_limit)
VALUES
    ('projects', 'projects', true, 15728640),
    ('program-covers', 'program-covers', true, 10485760),
    ('circle-resources', 'circle-resources', true, 26214400),
    ('resumes', 'resumes', false, 10485760)
ON CONFLICT (id) DO UPDATE SET
    public = EXCLUDED.public,
    file_size_limit = EXCLUDED.file_size_limit;

-- Public buckets read policy
DROP POLICY IF EXISTS "vybe_2_0_public_buckets_select" ON storage.objects;
CREATE POLICY "vybe_2_0_public_buckets_select"
    ON storage.objects FOR SELECT
    USING (bucket_id IN ('projects', 'program-covers', 'circle-resources'));

-- Public buckets owner upload/update/delete
DROP POLICY IF EXISTS "vybe_2_0_public_buckets_insert" ON storage.objects;
CREATE POLICY "vybe_2_0_public_buckets_insert"
    ON storage.objects FOR INSERT
    TO authenticated
    WITH CHECK (
        bucket_id IN ('projects', 'program-covers', 'circle-resources')
        AND auth.uid()::text = (storage.foldername(name))[1]
    );

DROP POLICY IF EXISTS "vybe_2_0_public_buckets_modify" ON storage.objects;
CREATE POLICY "vybe_2_0_public_buckets_modify"
    ON storage.objects FOR UPDATE
    TO authenticated
    USING (
        bucket_id IN ('projects', 'program-covers', 'circle-resources')
        AND (auth.uid()::text = (storage.foldername(name))[1] OR public.is_admin(auth.uid()))
    );

DROP POLICY IF EXISTS "vybe_2_0_public_buckets_delete" ON storage.objects;
CREATE POLICY "vybe_2_0_public_buckets_delete"
    ON storage.objects FOR DELETE
    TO authenticated
    USING (
        bucket_id IN ('projects', 'program-covers', 'circle-resources')
        AND (auth.uid()::text = (storage.foldername(name))[1] OR public.is_admin(auth.uid()))
    );

-- Private 'resumes' bucket: strict access to applicant, matching opportunity poster, or admin
DROP POLICY IF EXISTS "vybe_2_0_resumes_insert" ON storage.objects;
CREATE POLICY "vybe_2_0_resumes_insert"
    ON storage.objects FOR INSERT
    TO authenticated
    WITH CHECK (
        bucket_id = 'resumes'
        AND auth.uid()::text = (storage.foldername(name))[1]
    );

DROP POLICY IF EXISTS "vybe_2_0_resumes_select" ON storage.objects;
CREATE POLICY "vybe_2_0_resumes_select"
    ON storage.objects FOR SELECT
    TO authenticated
    USING (
        bucket_id = 'resumes'
        AND (
            auth.uid()::text = (storage.foldername(name))[1]
            OR public.is_admin(auth.uid())
            OR EXISTS (
                SELECT 1
                FROM public.opportunity_applications oa
                JOIN public.opportunities o ON o.id = oa.opportunity_id
                WHERE o.posted_by = auth.uid()
                  AND oa.resume_url LIKE '%' || storage.objects.name || '%'
            )
        )
    );

DROP POLICY IF EXISTS "vybe_2_0_resumes_delete" ON storage.objects;
CREATE POLICY "vybe_2_0_resumes_delete"
    ON storage.objects FOR DELETE
    TO authenticated
    USING (
        bucket_id = 'resumes'
        AND (auth.uid()::text = (storage.foldername(name))[1] OR public.is_admin(auth.uid()))
    );

-- Register audio_tracks, story_comments & platform_config in Realtime if available
DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['platform_config', 'audio_tracks', 'story_comments']
    LOOP
        BEGIN
            IF NOT EXISTS (
                SELECT 1 FROM pg_publication_tables
                WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = t
            ) THEN
                EXECUTE format('ALTER PUBLICATION supabase_realtime ADD TABLE public.%I', t);
            END IF;
        EXCEPTION WHEN others THEN
            NULL;
        END;
    END LOOP;
END $$;
