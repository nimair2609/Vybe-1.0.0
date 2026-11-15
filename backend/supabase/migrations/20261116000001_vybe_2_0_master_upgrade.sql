-- ============================================================================
-- VYBE 2.0 — MASTER PRODUCT UPGRADE MIGRATION
-- Migration: 20261116000001_vybe_2_0_master_upgrade.sql
-- Product: VYBE by NR Next (NR Group)
-- ============================================================================
-- STRICT PRODUCTION SAFETY:
--   - 100% additive, idempotent, and non-destructive.
--   - Preserves ALL existing users, profiles, posts, comments, messages,
--     stories, communities, business_profiles, and admin tables.
--   - Compatible with existing deployed v1.x Android & Web clients.
--   - SECURITY DEFINER routines use SET search_path = '' and explicit schemas.
--   - Do NOT execute automatically on production without Founder approval.
-- ============================================================================

-- ============================================================================
-- 1. STORIES 2.0: EDITOR METADATA & SERVER-DEDUPLICATED UNIQUE VIEW COUNTS
-- ============================================================================
ALTER TABLE public.stories
    ADD COLUMN IF NOT EXISTS views_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS crop_rect JSONB DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS transform_json JSONB DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS overlays_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS trim_start_ms INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS trim_end_ms INT DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS audio_track_id TEXT DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS audio_volume DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    ADD COLUMN IF NOT EXISTS story_volume DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    ADD COLUMN IF NOT EXISTS background_gradient TEXT DEFAULT NULL;

ALTER TABLE public.story_views ENABLE ROW LEVEL SECURITY;

-- Lock down direct writes on story_views so all views are validated & deduplicated server-side
REVOKE INSERT, UPDATE, DELETE ON TABLE public.story_views FROM PUBLIC, anon, authenticated;
GRANT SELECT ON TABLE public.story_views TO authenticated;

DROP POLICY IF EXISTS "Story owners and admins read story views" ON public.story_views;
DROP POLICY IF EXISTS "story_views_select_policy" ON public.story_views;
CREATE POLICY "story_views_select_policy"
    ON public.story_views
    FOR SELECT
    TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.stories s
            WHERE s.id = story_views.story_id
              AND (s.author_id = auth.uid() OR public.is_admin(auth.uid()))
        )
    );

CREATE OR REPLACE FUNCTION public.guard_story_views_count_immutable()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_jwt_role TEXT := COALESCE(pg_catalog.current_setting('request.jwt.claim.role', true), '');
    v_caller UUID := auth.uid();
BEGIN
    IF v_jwt_role = 'service_role'
       OR (v_jwt_role = '' AND CURRENT_USER IN ('postgres', 'supabase_admin'))
       OR pg_catalog.current_setting('vybe.trusted_story_view_rpc', true) = 'true'
       OR (v_caller IS NOT NULL AND public.is_admin(v_caller)) THEN
        RETURN NEW;
    END IF;

    IF NEW.views_count IS DISTINCT FROM OLD.views_count THEN
        NEW.views_count := OLD.views_count;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_guard_story_views_count_immutable ON public.stories;
CREATE TRIGGER trg_guard_story_views_count_immutable
    BEFORE UPDATE ON public.stories
    FOR EACH ROW
    EXECUTE FUNCTION public.guard_story_views_count_immutable();

CREATE OR REPLACE FUNCTION public.record_story_view(p_story_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_viewer_id UUID := auth.uid();
    v_story RECORD;
    v_viewer RECORD;
    v_inserted_id UUID;
    v_views_count BIGINT := 0;
BEGIN
    IF v_viewer_id IS NULL THEN
        RETURN pg_catalog.jsonb_build_object(
            'recorded', false,
            'reason', 'UNAUTHENTICATED',
            'views_count', 0
        );
    END IF;

    IF p_story_id IS NULL THEN
        RETURN pg_catalog.jsonb_build_object(
            'recorded', false,
            'reason', 'INVALID_STORY',
            'views_count', 0
        );
    END IF;

    SELECT id, author_id, audience, expires_at, COALESCE(views_count, 0) AS views_count
    INTO v_story
    FROM public.stories
    WHERE id = p_story_id;

    IF v_story.id IS NULL OR v_story.expires_at <= NOW() THEN
        RETURN pg_catalog.jsonb_build_object(
            'recorded', false,
            'reason', 'STORY_UNAVAILABLE',
            'views_count', COALESCE(v_story.views_count, 0)
        );
    END IF;

    IF v_viewer_id = v_story.author_id THEN
        SELECT COUNT(*)::BIGINT INTO v_views_count
        FROM public.story_views sv
        JOIN public.profiles vp ON vp.id = sv.viewer_id
        WHERE sv.story_id = p_story_id
          AND sv.viewer_id <> v_story.author_id
          AND COALESCE(vp.is_banned, FALSE) = FALSE
          AND COALESCE(vp.is_suspended, FALSE) = FALSE
          AND COALESCE(vp.is_deactivated, FALSE) = FALSE
          AND NOT public.is_blocked_between(v_story.author_id, sv.viewer_id);

        RETURN pg_catalog.jsonb_build_object(
            'recorded', false,
            'reason', 'SELF_VIEW',
            'views_count', v_views_count
        );
    END IF;

    SELECT id, is_banned, is_suspended, is_deactivated
    INTO v_viewer
    FROM public.profiles
    WHERE id = v_viewer_id;

    IF v_viewer.id IS NULL
       OR COALESCE(v_viewer.is_banned, FALSE)
       OR COALESCE(v_viewer.is_suspended, FALSE)
       OR COALESCE(v_viewer.is_deactivated, FALSE) THEN
        RETURN pg_catalog.jsonb_build_object(
            'recorded', false,
            'reason', 'VIEWER_UNAVAILABLE',
            'views_count', NULL
        );
    END IF;

    IF public.is_blocked_between(v_story.author_id, v_viewer_id) THEN
        RETURN pg_catalog.jsonb_build_object(
            'recorded', false,
            'reason', 'BLOCKED',
            'views_count', NULL
        );
    END IF;

    IF NOT public.can_view_story(v_story.author_id, v_story.audience, v_story.expires_at, v_viewer_id) THEN
        RETURN pg_catalog.jsonb_build_object(
            'recorded', false,
            'reason', 'FORBIDDEN',
            'views_count', NULL
        );
    END IF;

    PERFORM pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtext(p_story_id::text),
        pg_catalog.hashtext(v_viewer_id::text)
    );

    INSERT INTO public.story_views (story_id, viewer_id, viewed_at)
    VALUES (p_story_id, v_viewer_id, NOW())
    ON CONFLICT (story_id, viewer_id) DO NOTHING
    RETURNING viewer_id INTO v_inserted_id;

    SELECT COUNT(*)::BIGINT INTO v_views_count
    FROM public.story_views sv
    JOIN public.profiles vp ON vp.id = sv.viewer_id
    WHERE sv.story_id = p_story_id
      AND sv.viewer_id <> v_story.author_id
      AND COALESCE(vp.is_banned, FALSE) = FALSE
      AND COALESCE(vp.is_suspended, FALSE) = FALSE
      AND COALESCE(vp.is_deactivated, FALSE) = FALSE
      AND NOT public.is_blocked_between(v_story.author_id, sv.viewer_id);

    PERFORM pg_catalog.set_config('vybe.trusted_story_view_rpc', 'true', true);
    UPDATE public.stories
    SET views_count = v_views_count
    WHERE id = p_story_id;

    RETURN pg_catalog.jsonb_build_object(
        'recorded', v_inserted_id IS NOT NULL,
        'reason', CASE WHEN v_inserted_id IS NOT NULL THEN 'RECORDED' ELSE 'ALREADY_VIEWED' END,
        'views_count', NULL
    );
END;
$$;

REVOKE ALL ON FUNCTION public.record_story_view(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.record_story_view(UUID) TO authenticated;

CREATE OR REPLACE FUNCTION public.get_story_viewers(p_story_id UUID)
RETURNS TABLE (
    viewer_id UUID,
    username VARCHAR(50),
    display_name VARCHAR(100),
    avatar_url TEXT,
    is_verified BOOLEAN,
    viewed_at TIMESTAMPTZ
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_caller UUID := auth.uid();
    v_author_id UUID;
BEGIN
    IF v_caller IS NULL OR p_story_id IS NULL THEN
        RETURN;
    END IF;

    SELECT s.author_id INTO v_author_id
    FROM public.stories s
    WHERE s.id = p_story_id;

    IF v_author_id IS NULL THEN
        RETURN;
    END IF;

    IF v_caller <> v_author_id AND NOT public.is_admin(v_caller) THEN
        RAISE EXCEPTION 'Only the story owner or an administrator can inspect story viewers.';
    END IF;

    RETURN QUERY
    SELECT
        vp.id AS viewer_id,
        vp.username,
        vp.display_name,
        vp.avatar_url,
        COALESCE(vp.is_verified, FALSE) AS is_verified,
        sv.viewed_at
    FROM public.story_views sv
    JOIN public.profiles vp ON vp.id = sv.viewer_id
    WHERE sv.story_id = p_story_id
      AND sv.viewer_id <> v_author_id
      AND COALESCE(vp.is_banned, FALSE) = FALSE
      AND COALESCE(vp.is_suspended, FALSE) = FALSE
      AND COALESCE(vp.is_deactivated, FALSE) = FALSE
      AND NOT public.is_blocked_between(v_author_id, vp.id)
    ORDER BY sv.viewed_at DESC;
END;
$$;

REVOKE ALL ON FUNCTION public.get_story_viewers(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_story_viewers(UUID) TO authenticated;

-- ============================================================================
-- 2. POST VIEW COUNT PRIVACY HARDENING (OWNER & ADMIN ONLY)
-- ============================================================================
CREATE OR REPLACE FUNCTION public.guard_post_views_count_immutable()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_jwt_role TEXT := COALESCE(pg_catalog.current_setting('request.jwt.claim.role', true), '');
    v_caller UUID := auth.uid();
BEGIN
    IF v_jwt_role = 'service_role'
       OR (v_jwt_role = '' AND CURRENT_USER IN ('postgres', 'supabase_admin'))
       OR pg_catalog.current_setting('vybe.trusted_post_view_rpc', true) = 'true'
       OR (v_caller IS NOT NULL AND public.is_admin(v_caller)) THEN
        RETURN NEW;
    END IF;

    IF NEW.views_count IS DISTINCT FROM OLD.views_count THEN
        NEW.views_count := OLD.views_count;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_guard_post_views_count_immutable ON public.posts;
CREATE TRIGGER trg_guard_post_views_count_immutable
    BEFORE UPDATE ON public.posts
    FOR EACH ROW
    EXECUTE FUNCTION public.guard_post_views_count_immutable();

-- Replace permissive post_views insert policy with authenticated non-self, non-blocked recording
DROP POLICY IF EXISTS "Anyone can record post views" ON public.post_views;
REVOKE INSERT, UPDATE, DELETE ON TABLE public.post_views FROM PUBLIC, anon, authenticated;
GRANT SELECT ON TABLE public.post_views TO authenticated;

DROP POLICY IF EXISTS "Users can view impressions on their own posts" ON public.post_views;
CREATE POLICY "Users can view impressions on their own posts"
    ON public.post_views
    FOR SELECT
    TO authenticated
    USING (
        public.is_admin(auth.uid())
        OR EXISTS (
            SELECT 1 FROM public.posts p
            WHERE p.id = post_views.post_id
              AND p.author_id = auth.uid()
        )
    );

-- Safe view masking views_count for non-owners/non-admins
CREATE OR REPLACE VIEW public.public_posts
WITH (security_invoker = true) AS
SELECT
    p.id,
    p.author_id,
    p.caption,
    p.media_type,
    p.mood_zone,
    p.is_unfiltered,
    p.is_vybes_reel,
    p.is_spiritual,
    p.is_archived,
    p.likes_count,
    p.comments_count,
    p.saves_count,
    CASE
        WHEN auth.uid() = p.author_id OR public.is_admin(auth.uid())
            THEN COALESCE(p.views_count, 0)
        ELSE NULL
    END AS views_count,
    p.created_at,
    p.updated_at
FROM public.posts p;

GRANT SELECT ON public.public_posts TO anon, authenticated;

CREATE OR REPLACE FUNCTION public.increment_post_view(p_post_id UUID, p_viewer_id UUID DEFAULT NULL)
RETURNS INT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_caller UUID := COALESCE(auth.uid(), p_viewer_id);
    v_author_id UUID;
    v_new_views INT;
BEGIN
    IF p_post_id IS NULL THEN
        RETURN NULL;
    END IF;

    SELECT p.author_id INTO v_author_id
    FROM public.posts p
    WHERE p.id = p_post_id;

    IF v_author_id IS NULL THEN
        RETURN NULL;
    END IF;

    -- Do not inflate counts for self-views, blocked users, or suspended/banned accounts
    IF v_caller IS NOT NULL THEN
        IF v_caller = v_author_id THEN
            RETURN COALESCE((SELECT views_count FROM public.posts WHERE id = p_post_id), 0);
        END IF;

        IF public.is_blocked_between(v_author_id, v_caller) THEN
            RETURN NULL;
        END IF;

        IF EXISTS (
            SELECT 1 FROM public.profiles vp
            WHERE vp.id = v_caller
              AND (COALESCE(vp.is_banned, FALSE) OR COALESCE(vp.is_suspended, FALSE) OR COALESCE(vp.is_deactivated, FALSE))
        ) THEN
            RETURN NULL;
        END IF;
    END IF;

    INSERT INTO public.post_views (post_id, viewer_id)
    VALUES (p_post_id, v_caller);

    PERFORM pg_catalog.set_config('vybe.trusted_post_view_rpc', 'true', true);
    UPDATE public.posts
    SET views_count = COALESCE(views_count, 0) + 1
    WHERE id = p_post_id
    RETURNING views_count INTO v_new_views;

    -- Return count strictly to author or admin; NULL to other callers
    IF v_caller IS NOT NULL AND (v_caller = v_author_id OR public.is_admin(v_caller)) THEN
        RETURN COALESCE(v_new_views, 1);
    END IF;
    RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION public.get_post_analytics(p_post_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_caller UUID := auth.uid();
    v_post RECORD;
BEGIN
    IF v_caller IS NULL OR p_post_id IS NULL THEN
        RAISE EXCEPTION 'Authentication required to access post analytics.';
    END IF;

    SELECT id, author_id, COALESCE(views_count, 0) AS views_count,
           COALESCE(likes_count, 0) AS likes_count,
           COALESCE(comments_count, 0) AS comments_count,
           COALESCE(saves_count, 0) AS saves_count
    INTO v_post
    FROM public.posts
    WHERE id = p_post_id;

    IF v_post.id IS NULL THEN
        RETURN NULL;
    END IF;

    IF v_caller <> v_post.author_id AND NOT public.is_admin(v_caller) THEN
        RAISE EXCEPTION 'Forbidden: Post analytics are restricted to the post owner and administrators.';
    END IF;

    RETURN pg_catalog.jsonb_build_object(
        'post_id', v_post.id,
        'views_count', v_post.views_count,
        'likes_count', v_post.likes_count,
        'comments_count', v_post.comments_count,
        'saves_count', v_post.saves_count
    );
END;
$$;

REVOKE ALL ON FUNCTION public.get_post_analytics(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_post_analytics(UUID) TO authenticated;

-- ============================================================================
-- 3. GROWTH CIRCLE COMPLETE COMMUNITY SYSTEM
-- ============================================================================
ALTER TABLE public.communities
    ADD COLUMN IF NOT EXISTS join_mode TEXT NOT NULL DEFAULT 'PUBLIC',
    ADD COLUMN IF NOT EXISTS rules TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS tags TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS conversation_id UUID REFERENCES public.conversations(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS is_archived BOOLEAN NOT NULL DEFAULT FALSE;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_communities_join_mode') THEN
        ALTER TABLE public.communities
            ADD CONSTRAINT chk_communities_join_mode CHECK (join_mode IN ('PUBLIC', 'PRIVATE', 'APPROVAL'));
    END IF;
END $$;

-- Sync legacy is_private flag for existing communities
UPDATE public.communities
SET join_mode = CASE WHEN COALESCE(is_private, FALSE) = TRUE THEN 'PRIVATE' ELSE 'PUBLIC' END
WHERE join_mode = 'PUBLIC' AND COALESCE(is_private, FALSE) = TRUE;

ALTER TABLE public.community_posts
    ADD COLUMN IF NOT EXISTS post_type TEXT NOT NULL DEFAULT 'DISCUSSION',
    ADD COLUMN IF NOT EXISTS is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS attachment_url TEXT,
    ADD COLUMN IF NOT EXISTS attachment_label TEXT;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_community_posts_type') THEN
        ALTER TABLE public.community_posts
            ADD CONSTRAINT chk_community_posts_type CHECK (post_type IN ('DISCUSSION', 'ANNOUNCEMENT', 'RESOURCE', 'POLL', 'EVENT'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_community_posts_pinned ON public.community_posts (community_id, is_pinned DESC, created_at DESC);

CREATE OR REPLACE FUNCTION public.is_circle_member(p_community_id UUID, p_user_id UUID DEFAULT auth.uid())
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
    SELECT CASE
        WHEN p_community_id IS NULL OR p_user_id IS NULL THEN FALSE
        ELSE EXISTS (
            SELECT 1 FROM public.communities c
            WHERE c.id = p_community_id AND c.creator_id = p_user_id
        ) OR EXISTS (
            SELECT 1 FROM public.community_members cm
            WHERE cm.community_id = p_community_id
              AND cm.user_id = p_user_id
              AND cm.status = 'ACCEPTED'
        )
    END;
$$;

CREATE OR REPLACE FUNCTION public.is_circle_moderator(p_community_id UUID, p_user_id UUID DEFAULT auth.uid())
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
    SELECT CASE
        WHEN p_community_id IS NULL OR p_user_id IS NULL THEN FALSE
        ELSE EXISTS (
            SELECT 1 FROM public.communities c
            WHERE c.id = p_community_id AND c.creator_id = p_user_id
        ) OR EXISTS (
            SELECT 1 FROM public.community_members cm
            WHERE cm.community_id = p_community_id
              AND cm.user_id = p_user_id
              AND cm.status = 'ACCEPTED'
              AND cm.role IN ('ADMIN', 'MODERATOR')
        )
    END;
$$;

-- Circle Polls
CREATE TABLE IF NOT EXISTS public.community_polls (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    community_id UUID NOT NULL REFERENCES public.communities(id) ON DELETE CASCADE,
    post_id UUID REFERENCES public.community_posts(id) ON DELETE CASCADE,
    creator_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    question TEXT NOT NULL,
    options_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    allow_multiple BOOLEAN NOT NULL DEFAULT FALSE,
    closes_at TIMESTAMPTZ DEFAULT (NOW() + INTERVAL '7 days'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.community_poll_votes (
    poll_id UUID NOT NULL REFERENCES public.community_polls(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    option_index INT NOT NULL CHECK (option_index >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (poll_id, user_id)
);

-- Circle Events & RSVPs
CREATE TABLE IF NOT EXISTS public.community_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    community_id UUID NOT NULL REFERENCES public.communities(id) ON DELETE CASCADE,
    creator_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    event_type TEXT NOT NULL DEFAULT 'ONLINE' CHECK (event_type IN ('ONLINE', 'IN_PERSON', 'HYBRID')),
    location_or_link TEXT NOT NULL DEFAULT '',
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ,
    max_attendees INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.community_event_rsvps (
    event_id UUID NOT NULL REFERENCES public.community_events(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'GOING' CHECK (status IN ('GOING', 'INTERESTED', 'CANCELLED')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, user_id)
);

-- Circle Resources
CREATE TABLE IF NOT EXISTS public.community_resources (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    community_id UUID NOT NULL REFERENCES public.communities(id) ON DELETE CASCADE,
    creator_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    resource_url TEXT NOT NULL DEFAULT '',
    resource_type TEXT NOT NULL DEFAULT 'LINK' CHECK (resource_type IN ('LINK', 'GUIDE', 'FILE', 'VIDEO', 'TOOL')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Circle Content & Member Reports (scoped to Circle Moderators & Platform Admins)
CREATE TABLE IF NOT EXISTS public.community_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    community_id UUID NOT NULL REFERENCES public.communities(id) ON DELETE CASCADE,
    reporter_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    target_type TEXT NOT NULL CHECK (target_type IN ('POST', 'COMMENT', 'MEMBER', 'CIRCLE')),
    target_id TEXT NOT NULL,
    reason TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED')),
    reviewed_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.community_polls ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.community_poll_votes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.community_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.community_event_rsvps ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.community_resources ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.community_reports ENABLE ROW LEVEL SECURITY;

-- Policies for Circle Polls, Events, Resources, Reports
DROP POLICY IF EXISTS "Circle members read polls" ON public.community_polls;
CREATE POLICY "Circle members read polls" ON public.community_polls FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.communities c
            WHERE c.id = community_polls.community_id
              AND (c.join_mode = 'PUBLIC' OR public.is_circle_member(c.id, auth.uid()))
        )
    );

DROP POLICY IF EXISTS "Circle members create polls" ON public.community_polls;
CREATE POLICY "Circle members create polls" ON public.community_polls FOR INSERT TO authenticated
    WITH CHECK (auth.uid() = creator_id AND public.is_circle_member(community_id, auth.uid()));

DROP POLICY IF EXISTS "Circle mods delete polls" ON public.community_polls;
CREATE POLICY "Circle mods delete polls" ON public.community_polls FOR DELETE TO authenticated
    USING (auth.uid() = creator_id OR public.is_circle_moderator(community_id, auth.uid()));

DROP POLICY IF EXISTS "Circle poll votes read" ON public.community_poll_votes;
CREATE POLICY "Circle poll votes read" ON public.community_poll_votes FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.community_polls p
            JOIN public.communities c ON c.id = p.community_id
            WHERE p.id = community_poll_votes.poll_id
              AND (c.join_mode = 'PUBLIC' OR public.is_circle_member(c.id, auth.uid()))
        )
    );

DROP POLICY IF EXISTS "Circle members vote on polls" ON public.community_poll_votes;
CREATE POLICY "Circle members vote on polls" ON public.community_poll_votes FOR INSERT TO authenticated
    WITH CHECK (
        auth.uid() = user_id AND EXISTS (
            SELECT 1 FROM public.community_polls p
            WHERE p.id = community_poll_votes.poll_id
              AND public.is_circle_member(p.community_id, auth.uid())
        )
    );

DROP POLICY IF EXISTS "Circle members update own vote" ON public.community_poll_votes;
CREATE POLICY "Circle members update own vote" ON public.community_poll_votes FOR UPDATE TO authenticated
    USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "Circle events select" ON public.community_events;
CREATE POLICY "Circle events select" ON public.community_events FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.communities c
            WHERE c.id = community_events.community_id
              AND (c.join_mode = 'PUBLIC' OR public.is_circle_member(c.id, auth.uid()))
        )
    );

DROP POLICY IF EXISTS "Circle mods manage events" ON public.community_events;
CREATE POLICY "Circle mods manage events" ON public.community_events FOR ALL TO authenticated
    USING (public.is_circle_moderator(community_id, auth.uid()) OR auth.uid() = creator_id)
    WITH CHECK (auth.uid() = creator_id AND public.is_circle_member(community_id, auth.uid()));

DROP POLICY IF EXISTS "Circle event rsvps select" ON public.community_event_rsvps;
CREATE POLICY "Circle event rsvps select" ON public.community_event_rsvps FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.community_events e
            WHERE e.id = community_event_rsvps.event_id
              AND public.is_circle_member(e.community_id, auth.uid())
        )
    );

DROP POLICY IF EXISTS "Circle members upsert own rsvp" ON public.community_event_rsvps;
CREATE POLICY "Circle members upsert own rsvp" ON public.community_event_rsvps FOR ALL TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (
        auth.uid() = user_id AND EXISTS (
            SELECT 1 FROM public.community_events e
            WHERE e.id = community_event_rsvps.event_id
              AND public.is_circle_member(e.community_id, auth.uid())
        )
    );

DROP POLICY IF EXISTS "Circle resources select" ON public.community_resources;
CREATE POLICY "Circle resources select" ON public.community_resources FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.communities c
            WHERE c.id = community_resources.community_id
              AND (c.join_mode = 'PUBLIC' OR public.is_circle_member(c.id, auth.uid()))
        )
    );

DROP POLICY IF EXISTS "Circle members share resources" ON public.community_resources;
CREATE POLICY "Circle members share resources" ON public.community_resources FOR INSERT TO authenticated
    WITH CHECK (auth.uid() = creator_id AND public.is_circle_member(community_id, auth.uid()));

DROP POLICY IF EXISTS "Circle mods delete resources" ON public.community_resources;
CREATE POLICY "Circle mods delete resources" ON public.community_resources FOR DELETE TO authenticated
    USING (auth.uid() = creator_id OR public.is_circle_moderator(community_id, auth.uid()));

DROP POLICY IF EXISTS "Circle reports read by mods and admins" ON public.community_reports;
CREATE POLICY "Circle reports read by mods and admins" ON public.community_reports FOR SELECT TO authenticated
    USING (
        reporter_id = auth.uid()
        OR public.is_circle_moderator(community_id, auth.uid())
        OR public.is_admin(auth.uid())
    );

DROP POLICY IF EXISTS "Circle members file reports" ON public.community_reports;
CREATE POLICY "Circle members file reports" ON public.community_reports FOR INSERT TO authenticated
    WITH CHECK (auth.uid() = reporter_id);

DROP POLICY IF EXISTS "Circle mods resolve reports" ON public.community_reports;
CREATE POLICY "Circle mods resolve reports" ON public.community_reports FOR UPDATE TO authenticated
    USING (public.is_circle_moderator(community_id, auth.uid()) OR public.is_admin(auth.uid()));

-- Circle Join / Approval / Pin / Chat RPCs
CREATE OR REPLACE FUNCTION public.join_or_request_circle(p_community_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_user UUID := auth.uid();
    v_circle RECORD;
    v_existing RECORD;
    v_target_status TEXT;
BEGIN
    IF v_user IS NULL THEN
        RAISE EXCEPTION 'Authentication required.';
    END IF;

    SELECT id, creator_id, join_mode, is_private, conversation_id
    INTO v_circle
    FROM public.communities
    WHERE id = p_community_id;

    IF v_circle.id IS NULL THEN
        RAISE EXCEPTION 'Circle not found.';
    END IF;

    SELECT status, role INTO v_existing
    FROM public.community_members
    WHERE community_id = p_community_id AND user_id = v_user;

    IF v_existing.status IS NOT NULL THEN
        RETURN pg_catalog.jsonb_build_object(
            'status', v_existing.status,
            'role', v_existing.role
        );
    END IF;

    v_target_status := CASE
        WHEN v_circle.creator_id = v_user THEN 'ACCEPTED'
        WHEN v_circle.join_mode IN ('PRIVATE', 'APPROVAL') OR COALESCE(v_circle.is_private, FALSE) THEN 'PENDING'
        ELSE 'ACCEPTED'
    END;

    INSERT INTO public.community_members (community_id, user_id, role, status)
    VALUES (
        p_community_id,
        v_user,
        CASE WHEN v_circle.creator_id = v_user THEN 'ADMIN' ELSE 'MEMBER' END,
        v_target_status
    )
    ON CONFLICT (community_id, user_id) DO NOTHING;

    IF v_target_status = 'ACCEPTED' AND v_circle.conversation_id IS NOT NULL THEN
        INSERT INTO public.conversation_members (conversation_id, user_id, is_admin)
        VALUES (v_circle.conversation_id, v_user, v_circle.creator_id = v_user)
        ON CONFLICT (conversation_id, user_id) DO NOTHING;
    END IF;

    RETURN pg_catalog.jsonb_build_object(
        'status', v_target_status,
        'role', CASE WHEN v_circle.creator_id = v_user THEN 'ADMIN' ELSE 'MEMBER' END
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.review_circle_member_request(
    p_community_id UUID,
    p_target_user_id UUID,
    p_approve BOOLEAN
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_caller UUID := auth.uid();
    v_conv_id UUID;
BEGIN
    IF v_caller IS NULL OR NOT public.is_circle_moderator(p_community_id, v_caller) THEN
        RAISE EXCEPTION 'Only Circle owners and moderators may review join requests.';
    END IF;

    IF p_approve THEN
        UPDATE public.community_members
        SET status = 'ACCEPTED'
        WHERE community_id = p_community_id AND user_id = p_target_user_id;

        SELECT conversation_id INTO v_conv_id FROM public.communities WHERE id = p_community_id;
        IF v_conv_id IS NOT NULL THEN
            INSERT INTO public.conversation_members (conversation_id, user_id, is_admin)
            VALUES (v_conv_id, p_target_user_id, FALSE)
            ON CONFLICT (conversation_id, user_id) DO NOTHING;
        END IF;

        RETURN pg_catalog.jsonb_build_object('status', 'ACCEPTED');
    ELSE
        DELETE FROM public.community_members
        WHERE community_id = p_community_id AND user_id = p_target_user_id;

        RETURN pg_catalog.jsonb_build_object('status', 'REMOVED');
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.set_circle_member_role(
    p_community_id UUID,
    p_target_user_id UUID,
    p_role TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_caller UUID := auth.uid();
    v_creator_id UUID;
BEGIN
    SELECT creator_id INTO v_creator_id FROM public.communities WHERE id = p_community_id;
    IF v_caller IS NULL OR v_caller <> v_creator_id THEN
        RAISE EXCEPTION 'Only the Circle owner can promote or demote moderators.';
    END IF;

    IF p_role NOT IN ('MODERATOR', 'MEMBER') THEN
        RAISE EXCEPTION 'Invalid Circle role.';
    END IF;

    UPDATE public.community_members
    SET role = p_role
    WHERE community_id = p_community_id AND user_id = p_target_user_id AND status = 'ACCEPTED';

    RETURN pg_catalog.jsonb_build_object('user_id', p_target_user_id, 'role', p_role);
END;
$$;

CREATE OR REPLACE FUNCTION public.toggle_circle_post_pin(p_post_id UUID, p_pinned BOOLEAN)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_caller UUID := auth.uid();
    v_comm_id UUID;
BEGIN
    SELECT community_id INTO v_comm_id FROM public.community_posts WHERE id = p_post_id;
    IF v_comm_id IS NULL OR NOT public.is_circle_moderator(v_comm_id, v_caller) THEN
        RAISE EXCEPTION 'Only Circle moderators can pin or unpin posts.';
    END IF;

    UPDATE public.community_posts
    SET is_pinned = p_pinned, updated_at = NOW()
    WHERE id = p_post_id;
    RETURN p_pinned;
END;
$$;

CREATE OR REPLACE FUNCTION public.get_or_create_circle_chat(p_community_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_caller UUID := auth.uid();
    v_circle RECORD;
    v_conv_id UUID;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'Authentication required.';
    END IF;

    IF NOT public.is_circle_member(p_community_id, v_caller) THEN
        RAISE EXCEPTION 'Only accepted Circle members may join the Circle group chat.';
    END IF;

    SELECT id, name, avatar_url, creator_id, conversation_id
    INTO v_circle
    FROM public.communities
    WHERE id = p_community_id;

    v_conv_id := v_circle.conversation_id;

    IF v_conv_id IS NULL THEN
        INSERT INTO public.conversations (is_group, group_name, group_image_url, created_by)
        VALUES (TRUE, v_circle.name, v_circle.avatar_url, v_circle.creator_id)
        RETURNING id INTO v_conv_id;

        UPDATE public.communities
        SET conversation_id = v_conv_id, updated_at = NOW()
        WHERE id = p_community_id;
    END IF;

    -- Ensure creator and all accepted members belong to the Circle conversation
    INSERT INTO public.conversation_members (conversation_id, user_id, is_admin)
    SELECT v_conv_id, cm.user_id, (cm.role IN ('ADMIN', 'MODERATOR') OR cm.user_id = v_circle.creator_id)
    FROM public.community_members cm
    WHERE cm.community_id = p_community_id AND cm.status = 'ACCEPTED'
    ON CONFLICT (conversation_id, user_id) DO NOTHING;

    INSERT INTO public.conversation_members (conversation_id, user_id, is_admin)
    VALUES (v_conv_id, v_caller, public.is_circle_moderator(p_community_id, v_caller))
    ON CONFLICT (conversation_id, user_id) DO NOTHING;

    RETURN v_conv_id;
END;
$$;

-- ============================================================================
-- 4. VYBE GROW 2.0: PROGRAMS, HABITS/GOALS, PROFESSIONAL PROFILES, PROJECTS & OPPORTUNITIES
-- ============================================================================

-- 4A. Owner-Managed Growth Programs
CREATE TABLE IF NOT EXISTS public.growth_programs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    subtitle TEXT NOT NULL DEFAULT '',
    description TEXT NOT NULL DEFAULT '',
    category TEXT NOT NULL DEFAULT 'Personal Growth',
    duration_days INT NOT NULL DEFAULT 14 CHECK (duration_days > 0 AND duration_days <= 365),
    enrollment_mode TEXT NOT NULL DEFAULT 'PUBLIC' CHECK (enrollment_mode IN ('PUBLIC', 'APPROVAL', 'INVITE_ONLY')),
    is_published BOOLEAN NOT NULL DEFAULT TRUE,
    cover_url TEXT,
    tags TEXT[] NOT NULL DEFAULT '{}',
    members_count INT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.growth_program_members (
    program_id UUID NOT NULL REFERENCES public.growth_programs(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    role TEXT NOT NULL DEFAULT 'MEMBER' CHECK (role IN ('OWNER', 'MENTOR', 'MEMBER')),
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'PENDING', 'COMPLETED', 'REMOVED')),
    current_day INT NOT NULL DEFAULT 1,
    streak_days INT NOT NULL DEFAULT 0,
    progress_pct INT NOT NULL DEFAULT 0 CHECK (progress_pct >= 0 AND progress_pct <= 100),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (program_id, user_id)
);

CREATE TABLE IF NOT EXISTS public.growth_program_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    program_id UUID NOT NULL REFERENCES public.growth_programs(id) ON DELETE CASCADE,
    day_number INT NOT NULL DEFAULT 1,
    title TEXT NOT NULL,
    instructions TEXT NOT NULL DEFAULT '',
    task_type TEXT NOT NULL DEFAULT 'ACTION' CHECK (task_type IN ('ACTION', 'REFLECTION', 'CHECKPOINT', 'RESOURCE')),
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.growth_program_task_logs (
    task_id UUID NOT NULL REFERENCES public.growth_program_tasks(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    reflection_text TEXT NOT NULL DEFAULT '',
    proof_url TEXT,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (task_id, user_id)
);

CREATE OR REPLACE FUNCTION public.sync_growth_program_members_count()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_pid UUID := COALESCE(NEW.program_id, OLD.program_id);
BEGIN
    UPDATE public.growth_programs
    SET members_count = (
        SELECT COUNT(*)::INT
        FROM public.growth_program_members
        WHERE program_id = v_pid AND status IN ('ACTIVE', 'COMPLETED')
    ),
    updated_at = NOW()
    WHERE id = v_pid;
    RETURN COALESCE(NEW, OLD);
END;
$$;

DROP TRIGGER IF EXISTS trg_sync_growth_program_members_count ON public.growth_program_members;
CREATE TRIGGER trg_sync_growth_program_members_count
    AFTER INSERT OR UPDATE OR DELETE ON public.growth_program_members
    FOR EACH ROW EXECUTE FUNCTION public.sync_growth_program_members_count();

-- 4B. Personal Habits & Goals
CREATE TABLE IF NOT EXISTS public.personal_habits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    category TEXT NOT NULL DEFAULT 'Mindfulness',
    frequency TEXT NOT NULL DEFAULT 'DAILY' CHECK (frequency IN ('DAILY', 'WEEKLY')),
    target_per_week INT NOT NULL DEFAULT 7,
    streak_days INT NOT NULL DEFAULT 0,
    best_streak INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.personal_habit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    habit_id UUID NOT NULL REFERENCES public.personal_habits(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    log_date DATE NOT NULL DEFAULT CURRENT_DATE,
    note TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (habit_id, log_date)
);

CREATE TABLE IF NOT EXISTS public.personal_goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    category TEXT NOT NULL DEFAULT 'Career',
    target_date DATE,
    progress_pct INT NOT NULL DEFAULT 0 CHECK (progress_pct >= 0 AND progress_pct <= 100),
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'COMPLETED', 'PAUSED')),
    milestones_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4C. Professional GROW Profiles with Per-Section Privacy (PUBLIC / CONNECTIONS / PRIVATE)
CREATE TABLE IF NOT EXISTS public.grow_profiles (
    user_id UUID PRIMARY KEY REFERENCES public.profiles(id) ON DELETE CASCADE,
    headline TEXT NOT NULL DEFAULT '',
    current_role TEXT NOT NULL DEFAULT '',
    company_or_org TEXT NOT NULL DEFAULT '',
    industry TEXT NOT NULL DEFAULT '',
    location TEXT NOT NULL DEFAULT '',
    bio_professional TEXT NOT NULL DEFAULT '',
    skills TEXT[] NOT NULL DEFAULT '{}',
    interests TEXT[] NOT NULL DEFAULT '{}',
    career_goals TEXT NOT NULL DEFAULT '',
    experience_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    education_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    certifications_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    languages TEXT[] NOT NULL DEFAULT '{}',
    achievements_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    portfolio_links_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    availability_status TEXT NOT NULL DEFAULT 'OPEN_TO_NETWORK' CHECK (
        availability_status IN ('OPEN_TO_NETWORK', 'MENTORING', 'HIRING', 'OPEN_TO_WORK', 'COLLABORATING', 'FOCUSED')
    ),
    availability_modes TEXT[] NOT NULL DEFAULT '{}',
    visibility_role TEXT NOT NULL DEFAULT 'PUBLIC' CHECK (visibility_role IN ('PUBLIC', 'CONNECTIONS', 'PRIVATE')),
    visibility_company TEXT NOT NULL DEFAULT 'PUBLIC' CHECK (visibility_company IN ('PUBLIC', 'CONNECTIONS', 'PRIVATE')),
    visibility_location TEXT NOT NULL DEFAULT 'PUBLIC' CHECK (visibility_location IN ('PUBLIC', 'CONNECTIONS', 'PRIVATE')),
    visibility_education TEXT NOT NULL DEFAULT 'PUBLIC' CHECK (visibility_education IN ('PUBLIC', 'CONNECTIONS', 'PRIVATE')),
    visibility_certifications TEXT NOT NULL DEFAULT 'PUBLIC' CHECK (visibility_certifications IN ('PUBLIC', 'CONNECTIONS', 'PRIVATE')),
    visibility_portfolio TEXT NOT NULL DEFAULT 'PUBLIC' CHECK (visibility_portfolio IN ('PUBLIC', 'CONNECTIONS', 'PRIVATE')),
    visibility_experience TEXT NOT NULL DEFAULT 'PUBLIC' CHECK (visibility_experience IN ('PUBLIC', 'CONNECTIONS', 'PRIVATE')),
    visibility_goals TEXT NOT NULL DEFAULT 'CONNECTIONS' CHECK (visibility_goals IN ('PUBLIC', 'CONNECTIONS', 'PRIVATE')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.grow_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    recipient_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    request_type TEXT NOT NULL CHECK (request_type IN ('CONNECT', 'MENTORSHIP', 'CIRCLE_INVITE')),
    circle_id UUID REFERENCES public.communities(id) ON DELETE CASCADE,
    message TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    responded_at TIMESTAMPTZ
);

CREATE OR REPLACE FUNCTION public.get_visible_grow_profile(p_target_user_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_caller UUID := auth.uid();
    v_gp RECORD;
    v_is_owner BOOLEAN := FALSE;
    v_is_connected BOOLEAN := FALSE;
BEGIN
    IF p_target_user_id IS NULL THEN
        RETURN NULL;
    END IF;

    IF v_caller IS NOT NULL AND public.is_blocked_between(v_caller, p_target_user_id) THEN
        RETURN NULL;
    END IF;

    v_is_owner := (v_caller IS NOT NULL AND (v_caller = p_target_user_id OR public.is_admin(v_caller)));

    IF v_caller IS NOT NULL AND NOT v_is_owner THEN
        v_is_connected := EXISTS (
            SELECT 1 FROM public.follows f1
            WHERE f1.follower_id = v_caller AND f1.followed_id = p_target_user_id
              AND COALESCE(f1.status, 'ACCEPTED') = 'ACCEPTED'
        ) OR EXISTS (
            SELECT 1 FROM public.grow_requests gr
            WHERE gr.status = 'ACCEPTED'
              AND ((gr.sender_id = v_caller AND gr.recipient_id = p_target_user_id)
                OR (gr.sender_id = p_target_user_id AND gr.recipient_id = v_caller))
        );
    END IF;

    SELECT * INTO v_gp FROM public.grow_profiles WHERE user_id = p_target_user_id;
    IF v_gp.user_id IS NULL THEN
        RETURN NULL;
    END IF;

    RETURN pg_catalog.jsonb_build_object(
        'user_id', v_gp.user_id,
        'headline', v_gp.headline,
        'industry', v_gp.industry,
        'bio_professional', v_gp.bio_professional,
        'skills', v_gp.skills,
        'interests', v_gp.interests,
        'languages', v_gp.languages,
        'achievements_json', v_gp.achievements_json,
        'availability_status', v_gp.availability_status,
        'availability_modes', v_gp.availability_modes,
        'current_role', CASE
            WHEN v_is_owner OR v_gp.visibility_role = 'PUBLIC' OR (v_gp.visibility_role = 'CONNECTIONS' AND v_is_connected)
                THEN v_gp.current_role ELSE NULL END,
        'company_or_org', CASE
            WHEN v_is_owner OR v_gp.visibility_company = 'PUBLIC' OR (v_gp.visibility_company = 'CONNECTIONS' AND v_is_connected)
                THEN v_gp.company_or_org ELSE NULL END,
        'location', CASE
            WHEN v_is_owner OR v_gp.visibility_location = 'PUBLIC' OR (v_gp.visibility_location = 'CONNECTIONS' AND v_is_connected)
                THEN v_gp.location ELSE NULL END,
        'education_json', CASE
            WHEN v_is_owner OR v_gp.visibility_education = 'PUBLIC' OR (v_gp.visibility_education = 'CONNECTIONS' AND v_is_connected)
                THEN v_gp.education_json ELSE '[]'::jsonb END,
        'certifications_json', CASE
            WHEN v_is_owner OR v_gp.visibility_certifications = 'PUBLIC' OR (v_gp.visibility_certifications = 'CONNECTIONS' AND v_is_connected)
                THEN v_gp.certifications_json ELSE '[]'::jsonb END,
        'portfolio_links_json', CASE
            WHEN v_is_owner OR v_gp.visibility_portfolio = 'PUBLIC' OR (v_gp.visibility_portfolio = 'CONNECTIONS' AND v_is_connected)
                THEN v_gp.portfolio_links_json ELSE '[]'::jsonb END,
        'experience_json', CASE
            WHEN v_is_owner OR v_gp.visibility_experience = 'PUBLIC' OR (v_gp.visibility_experience = 'CONNECTIONS' AND v_is_connected)
                THEN v_gp.experience_json ELSE '[]'::jsonb END,
        'career_goals', CASE
            WHEN v_is_owner OR v_gp.visibility_goals = 'PUBLIC' OR (v_gp.visibility_goals = 'CONNECTIONS' AND v_is_connected)
                THEN v_gp.career_goals ELSE NULL END,
        'visibility_role', CASE WHEN v_is_owner THEN v_gp.visibility_role ELSE NULL END,
        'visibility_company', CASE WHEN v_is_owner THEN v_gp.visibility_company ELSE NULL END,
        'visibility_location', CASE WHEN v_is_owner THEN v_gp.visibility_location ELSE NULL END,
        'visibility_education', CASE WHEN v_is_owner THEN v_gp.visibility_education ELSE NULL END,
        'visibility_certifications', CASE WHEN v_is_owner THEN v_gp.visibility_certifications ELSE NULL END,
        'visibility_portfolio', CASE WHEN v_is_owner THEN v_gp.visibility_portfolio ELSE NULL END,
        'visibility_experience', CASE WHEN v_is_owner THEN v_gp.visibility_experience ELSE NULL END,
        'visibility_goals', CASE WHEN v_is_owner THEN v_gp.visibility_goals ELSE NULL END,
        'updated_at', v_gp.updated_at
    );
END;
$$;

-- 4D. Project Showcase & Collaboration
CREATE TABLE IF NOT EXISTS public.projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    summary TEXT NOT NULL DEFAULT '',
    description TEXT NOT NULL DEFAULT '',
    category TEXT NOT NULL DEFAULT 'Product',
    stage TEXT NOT NULL DEFAULT 'BUILDING' CHECK (stage IN ('IDEA', 'PROTOTYPE', 'BUILDING', 'LAUNCHED')),
    tech_stack TEXT[] NOT NULL DEFAULT '{}',
    media_urls TEXT[] NOT NULL DEFAULT '{}',
    repo_url TEXT NOT NULL DEFAULT '',
    demo_url TEXT NOT NULL DEFAULT '',
    looking_for_collaborators BOOLEAN NOT NULL DEFAULT FALSE,
    needs_roles TEXT[] NOT NULL DEFAULT '{}',
    likes_count INT NOT NULL DEFAULT 0,
    bookmarks_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.project_collaborators (
    project_id UUID NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    role TEXT NOT NULL DEFAULT 'Contributor',
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED')),
    invited_by UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (project_id, user_id)
);

CREATE TABLE IF NOT EXISTS public.project_interactions (
    project_id UUID NOT NULL REFERENCES public.projects(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    is_liked BOOLEAN NOT NULL DEFAULT FALSE,
    is_bookmarked BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (project_id, user_id)
);

CREATE OR REPLACE FUNCTION public.sync_project_interaction_counts()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_pid UUID := COALESCE(NEW.project_id, OLD.project_id);
BEGIN
    UPDATE public.projects
    SET likes_count = (SELECT COUNT(*)::INT FROM public.project_interactions WHERE project_id = v_pid AND is_liked = TRUE),
        bookmarks_count = (SELECT COUNT(*)::INT FROM public.project_interactions WHERE project_id = v_pid AND is_bookmarked = TRUE)
    WHERE id = v_pid;
    RETURN COALESCE(NEW, OLD);
END;
$$;

DROP TRIGGER IF EXISTS trg_sync_project_interaction_counts ON public.project_interactions;
CREATE TRIGGER trg_sync_project_interaction_counts
    AFTER INSERT OR UPDATE OR DELETE ON public.project_interactions
    FOR EACH ROW EXECUTE FUNCTION public.sync_project_interaction_counts();

-- 4E. Verified-Organization Opportunities Platform
CREATE TABLE IF NOT EXISTS public.opportunities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID REFERENCES public.business_profiles(id) ON DELETE SET NULL,
    posted_by UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    organization_name TEXT NOT NULL,
    category TEXT NOT NULL DEFAULT 'JOB' CHECK (
        category IN ('JOB', 'INTERNSHIP', 'FREELANCE', 'HACKATHON', 'MENTORSHIP', 'WORKSHOP', 'GRANT')
    ),
    work_mode TEXT NOT NULL DEFAULT 'REMOTE' CHECK (work_mode IN ('REMOTE', 'HYBRID', 'ONSITE')),
    location TEXT NOT NULL DEFAULT 'Remote',
    compensation_text TEXT NOT NULL DEFAULT '',
    description TEXT NOT NULL DEFAULT '',
    requirements TEXT NOT NULL DEFAULT '',
    skills_required TEXT[] NOT NULL DEFAULT '{}',
    source_url TEXT NOT NULL DEFAULT '',
    is_verified_org BOOLEAN NOT NULL DEFAULT FALSE,
    status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED', 'EXPIRED')),
    expires_at TIMESTAMPTZ DEFAULT (NOW() + INTERVAL '45 days'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Guard that is_verified_org can never be spoofed client-side without an APPROVED business_profiles record or Admin
CREATE OR REPLACE FUNCTION public.guard_opportunity_verification()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_caller UUID := auth.uid();
    v_has_approved_org BOOLEAN := FALSE;
BEGIN
    IF v_caller IS NOT NULL AND public.is_admin(v_caller) THEN
        RETURN NEW;
    END IF;

    IF NEW.organization_id IS NOT NULL THEN
        SELECT EXISTS (
            SELECT 1 FROM public.business_profiles bp
            WHERE bp.id = NEW.organization_id
              AND bp.owner_id = NEW.posted_by
              AND bp.verification_status = 'APPROVED'
        ) INTO v_has_approved_org;
    END IF;

    NEW.is_verified_org := v_has_approved_org;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_guard_opportunity_verification ON public.opportunities;
CREATE TRIGGER trg_guard_opportunity_verification
    BEFORE INSERT OR UPDATE ON public.opportunities
    FOR EACH ROW EXECUTE FUNCTION public.guard_opportunity_verification();

CREATE TABLE IF NOT EXISTS public.opportunity_applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    opportunity_id UUID NOT NULL REFERENCES public.opportunities(id) ON DELETE CASCADE,
    applicant_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    note TEXT NOT NULL DEFAULT '',
    portfolio_url TEXT NOT NULL DEFAULT '',
    resume_url TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'SUBMITTED' CHECK (
        status IN ('SUBMITTED', 'REVIEWING', 'SHORTLISTED', 'ACCEPTED', 'REJECTED', 'WITHDRAWN')
    ),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (opportunity_id, applicant_id)
);

CREATE TABLE IF NOT EXISTS public.opportunity_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    opportunity_id UUID NOT NULL REFERENCES public.opportunities(id) ON DELETE CASCADE,
    reporter_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    reason TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Enable RLS & Strict Zero-Trust Policies across all VYBE GROW 2.0 tables
ALTER TABLE public.growth_programs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.growth_program_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.growth_program_tasks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.growth_program_task_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.personal_habits ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.personal_habit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.personal_goals ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.grow_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.grow_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.projects ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.project_collaborators ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.project_interactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.opportunities ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.opportunity_applications ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.opportunity_reports ENABLE ROW LEVEL SECURITY;

-- Growth Programs Policies
DROP POLICY IF EXISTS "Published growth programs viewable" ON public.growth_programs;
CREATE POLICY "Published growth programs viewable" ON public.growth_programs FOR SELECT
    USING (is_published = TRUE OR owner_id = auth.uid() OR public.is_admin(auth.uid()));

DROP POLICY IF EXISTS "Users create own growth programs" ON public.growth_programs;
CREATE POLICY "Users create own growth programs" ON public.growth_programs FOR INSERT TO authenticated
    WITH CHECK (auth.uid() = owner_id);

DROP POLICY IF EXISTS "Owners manage own growth programs" ON public.growth_programs;
CREATE POLICY "Owners manage own growth programs" ON public.growth_programs FOR UPDATE TO authenticated
    USING (auth.uid() = owner_id OR public.is_admin(auth.uid()));

DROP POLICY IF EXISTS "Owners delete own growth programs" ON public.growth_programs;
CREATE POLICY "Owners delete own growth programs" ON public.growth_programs FOR DELETE TO authenticated
    USING (auth.uid() = owner_id OR public.is_admin(auth.uid()));

DROP POLICY IF EXISTS "Program members view roster" ON public.growth_program_members;
CREATE POLICY "Program members view roster" ON public.growth_program_members FOR SELECT
    USING (true);

DROP POLICY IF EXISTS "Users join or owners manage program members" ON public.growth_program_members;
CREATE POLICY "Users join or owners manage program members" ON public.growth_program_members FOR ALL TO authenticated
    USING (
        auth.uid() = user_id OR EXISTS (
            SELECT 1 FROM public.growth_programs gp WHERE gp.id = growth_program_members.program_id AND gp.owner_id = auth.uid()
        )
    )
    WITH CHECK (
        auth.uid() = user_id OR EXISTS (
            SELECT 1 FROM public.growth_programs gp WHERE gp.id = growth_program_members.program_id AND gp.owner_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS "Program tasks viewable" ON public.growth_program_tasks;
CREATE POLICY "Program tasks viewable" ON public.growth_program_tasks FOR SELECT USING (true);

DROP POLICY IF EXISTS "Program owners manage tasks" ON public.growth_program_tasks;
CREATE POLICY "Program owners manage tasks" ON public.growth_program_tasks FOR ALL TO authenticated
    USING (EXISTS (SELECT 1 FROM public.growth_programs gp WHERE gp.id = growth_program_tasks.program_id AND gp.owner_id = auth.uid()))
    WITH CHECK (EXISTS (SELECT 1 FROM public.growth_programs gp WHERE gp.id = growth_program_tasks.program_id AND gp.owner_id = auth.uid()));

DROP POLICY IF EXISTS "Program task logs private to owner and program host" ON public.growth_program_task_logs;
CREATE POLICY "Program task logs private to owner and program host" ON public.growth_program_task_logs FOR ALL TO authenticated
    USING (
        auth.uid() = user_id OR EXISTS (
            SELECT 1 FROM public.growth_program_tasks t
            JOIN public.growth_programs gp ON gp.id = t.program_id
            WHERE t.id = growth_program_task_logs.task_id AND gp.owner_id = auth.uid()
        )
    )
    WITH CHECK (auth.uid() = user_id);

-- Personal Habits & Goals (strictly owner-private)
DROP POLICY IF EXISTS "Personal habits private to owner" ON public.personal_habits;
CREATE POLICY "Personal habits private to owner" ON public.personal_habits FOR ALL TO authenticated
    USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "Personal habit logs private to owner" ON public.personal_habit_logs;
CREATE POLICY "Personal habit logs private to owner" ON public.personal_habit_logs FOR ALL TO authenticated
    USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "Personal goals private to owner" ON public.personal_goals;
CREATE POLICY "Personal goals private to owner" ON public.personal_goals FOR ALL TO authenticated
    USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- Professional GROW Profiles
DROP POLICY IF EXISTS "Grow profiles owner only direct read" ON public.grow_profiles;
CREATE POLICY "Grow profiles owner only direct read" ON public.grow_profiles FOR SELECT
    USING (auth.uid() = user_id OR public.is_admin(auth.uid()));

DROP POLICY IF EXISTS "Grow profiles owner upsert" ON public.grow_profiles;
CREATE POLICY "Grow profiles owner upsert" ON public.grow_profiles FOR ALL TO authenticated
    USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "Grow requests participant visibility" ON public.grow_requests;
CREATE POLICY "Grow requests participant visibility" ON public.grow_requests FOR SELECT TO authenticated
    USING (auth.uid() = sender_id OR auth.uid() = recipient_id);

DROP POLICY IF EXISTS "Grow requests sender create" ON public.grow_requests;
CREATE POLICY "Grow requests sender create" ON public.grow_requests FOR INSERT TO authenticated
    WITH CHECK (auth.uid() = sender_id AND sender_id <> recipient_id AND NOT public.is_blocked_between(sender_id, recipient_id));

DROP POLICY IF EXISTS "Grow requests participant update" ON public.grow_requests;
CREATE POLICY "Grow requests participant update" ON public.grow_requests FOR UPDATE TO authenticated
    USING (auth.uid() = recipient_id OR auth.uid() = sender_id);

-- Projects
DROP POLICY IF EXISTS "Projects readable" ON public.projects;
CREATE POLICY "Projects readable" ON public.projects FOR SELECT USING (true);

DROP POLICY IF EXISTS "Projects owner write" ON public.projects;
CREATE POLICY "Projects owner write" ON public.projects FOR ALL TO authenticated
    USING (auth.uid() = owner_id OR public.is_admin(auth.uid()))
    WITH CHECK (auth.uid() = owner_id);

DROP POLICY IF EXISTS "Project collaborators select" ON public.project_collaborators;
CREATE POLICY "Project collaborators select" ON public.project_collaborators FOR SELECT USING (true);

DROP POLICY IF EXISTS "Project collaborators write" ON public.project_collaborators;
CREATE POLICY "Project collaborators write" ON public.project_collaborators FOR ALL TO authenticated
    USING (auth.uid() = user_id OR EXISTS (SELECT 1 FROM public.projects p WHERE p.id = project_collaborators.project_id AND p.owner_id = auth.uid()))
    WITH CHECK (auth.uid() = user_id OR EXISTS (SELECT 1 FROM public.projects p WHERE p.id = project_collaborators.project_id AND p.owner_id = auth.uid()));

DROP POLICY IF EXISTS "Project interactions select" ON public.project_interactions;
CREATE POLICY "Project interactions select" ON public.project_interactions FOR SELECT USING (true);

DROP POLICY IF EXISTS "Project interactions owner write" ON public.project_interactions;
CREATE POLICY "Project interactions owner write" ON public.project_interactions FOR ALL TO authenticated
    USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- Opportunities
DROP POLICY IF EXISTS "Open opportunities readable" ON public.opportunities;
CREATE POLICY "Open opportunities readable" ON public.opportunities FOR SELECT
    USING (
        (status = 'OPEN' AND (expires_at IS NULL OR expires_at > NOW()))
        OR posted_by = auth.uid()
        OR public.is_admin(auth.uid())
    );

DROP POLICY IF EXISTS "Verified or authenticated posters create opportunities" ON public.opportunities;
CREATE POLICY "Verified or authenticated posters create opportunities" ON public.opportunities FOR INSERT TO authenticated
    WITH CHECK (auth.uid() = posted_by);

DROP POLICY IF EXISTS "Posters and admins manage opportunities" ON public.opportunities;
CREATE POLICY "Posters and admins manage opportunities" ON public.opportunities FOR UPDATE TO authenticated
    USING (auth.uid() = posted_by OR public.is_admin(auth.uid()));

DROP POLICY IF EXISTS "Posters and admins delete opportunities" ON public.opportunities;
CREATE POLICY "Posters and admins delete opportunities" ON public.opportunities FOR DELETE TO authenticated
    USING (auth.uid() = posted_by OR public.is_admin(auth.uid()));

DROP POLICY IF EXISTS "Opportunity applications privacy" ON public.opportunity_applications;
CREATE POLICY "Opportunity applications privacy" ON public.opportunity_applications FOR SELECT TO authenticated
    USING (
        auth.uid() = applicant_id
        OR public.is_admin(auth.uid())
        OR EXISTS (SELECT 1 FROM public.opportunities o WHERE o.id = opportunity_applications.opportunity_id AND o.posted_by = auth.uid())
    );

DROP POLICY IF EXISTS "Applicants submit applications" ON public.opportunity_applications;
CREATE POLICY "Applicants submit applications" ON public.opportunity_applications FOR INSERT TO authenticated
    WITH CHECK (auth.uid() = applicant_id);

DROP POLICY IF EXISTS "Applicants or opportunity owners update application status" ON public.opportunity_applications;
CREATE POLICY "Applicants or opportunity owners update application status" ON public.opportunity_applications FOR UPDATE TO authenticated
    USING (
        auth.uid() = applicant_id
        OR EXISTS (SELECT 1 FROM public.opportunities o WHERE o.id = opportunity_applications.opportunity_id AND o.posted_by = auth.uid())
    );

DROP POLICY IF EXISTS "Opportunity reports visibility" ON public.opportunity_reports;
CREATE POLICY "Opportunity reports visibility" ON public.opportunity_reports FOR SELECT TO authenticated
    USING (auth.uid() = reporter_id OR public.is_admin(auth.uid()));

DROP POLICY IF EXISTS "Opportunity reports insert" ON public.opportunity_reports;
CREATE POLICY "Opportunity reports insert" ON public.opportunity_reports FOR INSERT TO authenticated
    WITH CHECK (auth.uid() = reporter_id);

-- Realtime registration for interactive Circle & GROW features
DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'community_polls', 'community_poll_votes', 'community_events',
        'community_event_rsvps', 'community_resources', 'growth_programs',
        'growth_program_members', 'grow_requests', 'projects',
        'opportunities', 'opportunity_applications'
    ]
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
