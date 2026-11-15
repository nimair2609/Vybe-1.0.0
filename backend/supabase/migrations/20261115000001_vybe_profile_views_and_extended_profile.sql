-- ============================================================================
-- VYBE 2.1 — PERSISTENT PROFILE VIEWS & EXTENDED PROFILE FIELDS MIGRATION
-- Migration: 20261115000001_vybe_profile_views_and_extended_profile.sql
-- ============================================================================
-- STRICT PRODUCTION SAFETY:
--   - Additive, idempotent, non-destructive.
--   - Preserves ALL existing users, profiles, posts, comments, stories, and DMs.
--   - Does NOT execute automatically on production without Founder approval.
-- ============================================================================

-- 1. Extended non-PII profile columns on public.profiles
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS location TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS website TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS interest_tags TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS pinned_post_ids TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS profile_views_count BIGINT NOT NULL DEFAULT 0;

-- Refresh safe public view without exposing PII
CREATE OR REPLACE VIEW public.public_profiles
WITH (security_invoker = true) AS
SELECT
    p.id,
    p.username,
    p.display_name,
    p.first_name,
    p.last_name,
    p.bio,
    p.avatar_url,
    p.cover_url,
    p.profile_video_url,
    p.location,
    p.website,
    p.interest_tags,
    p.pinned_post_ids,
    p.profile_views_count,
    p.role,
    p.is_verified,
    p.is_vip,
    p.is_verified_public_figure,
    p.is_verified_business,
    p.is_worker,
    p.worker_badge_mandatory,
    p.vip_expires_at,
    p.badge_approval_status,
    p.badge_display_status,
    p.badge_type,
    p.current_mood,
    p.live_pulse,
    p.is_spiritual_mode_enabled,
    p.is_private_account,
    p.message_privacy,
    p.is_deactivated,
    p.is_banned,
    p.is_suspended,
    p.followers_count,
    p.following_count,
    p.posts_count,
    p.last_seen_at,
    p.created_at,
    p.updated_at
FROM public.profiles p;

GRANT SELECT ON public.public_profiles TO anon, authenticated;

-- Prevent clients from tampering directly with profile_views_count via REST PATCH
CREATE OR REPLACE FUNCTION public.guard_profile_views_count_immutable()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_jwt_role TEXT := COALESCE(current_setting('request.jwt.claim.role', true), '');
    v_caller UUID := auth.uid();
BEGIN
    IF v_jwt_role = 'service_role'
       OR (v_jwt_role = '' AND current_user IN ('postgres', 'supabase_admin'))
       OR current_setting('vybe.trusted_profile_view_rpc', true) = 'true'
       OR (v_caller IS NOT NULL AND public.is_admin(v_caller)) THEN
        RETURN NEW;
    END IF;

    IF NEW.profile_views_count IS DISTINCT FROM OLD.profile_views_count THEN
        NEW.profile_views_count := OLD.profile_views_count;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_guard_profile_views_count_immutable ON public.profiles;
CREATE TRIGGER trg_guard_profile_views_count_immutable
    BEFORE UPDATE ON public.profiles
    FOR EACH ROW
    EXECUTE FUNCTION public.guard_profile_views_count_immutable();

-- 2. Persistent profile_views ledger table
CREATE TABLE IF NOT EXISTS public.profile_views (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_owner_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    visitor_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT profile_views_no_self_view CHECK (profile_owner_id <> visitor_id)
);

CREATE INDEX IF NOT EXISTS idx_profile_views_dedupe_lookup
    ON public.profile_views (visitor_id, profile_owner_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_profile_views_owner_recent
    ON public.profile_views (profile_owner_id, created_at DESC);

ALTER TABLE public.profile_views ENABLE ROW LEVEL SECURITY;

-- Strict RLS: only the profile owner or an admin may inspect individual visitor rows
DROP POLICY IF EXISTS "Profile owners and admins read own profile views" ON public.profile_views;
CREATE POLICY "Profile owners and admins read own profile views"
    ON public.profile_views
    FOR SELECT
    TO authenticated
    USING (auth.uid() = profile_owner_id OR public.is_admin(auth.uid()));

-- Strict INSERT policy: authenticated visitor only, no self-view, no blocks/deactivated accounts
DROP POLICY IF EXISTS "Authenticated visitors insert non-blocked profile views" ON public.profile_views;
CREATE POLICY "Authenticated visitors insert non-blocked profile views"
    ON public.profile_views
    FOR INSERT
    TO authenticated
    WITH CHECK (
        auth.uid() IS NOT NULL
        AND auth.uid() = visitor_id
        AND visitor_id <> profile_owner_id
        AND EXISTS (
            SELECT 1 FROM public.profiles owner_prof
            WHERE owner_prof.id = profile_owner_id
              AND COALESCE(owner_prof.is_deactivated, FALSE) = FALSE
              AND COALESCE(owner_prof.is_banned, FALSE) = FALSE
        )
        AND EXISTS (
            SELECT 1 FROM public.profiles visitor_prof
            WHERE visitor_prof.id = auth.uid()
              AND COALESCE(visitor_prof.is_deactivated, FALSE) = FALSE
              AND COALESCE(visitor_prof.is_banned, FALSE) = FALSE
        )
        AND NOT EXISTS (
            SELECT 1 FROM public.blocks b
            WHERE (b.blocker_id = profile_owner_id AND b.blocked_id = auth.uid())
               OR (b.blocker_id = auth.uid() AND b.blocked_id = profile_owner_id)
        )
    );

-- 3. Authoritative SECURITY DEFINER RPC for 24-hour deduplicated profile-view recording
CREATE OR REPLACE FUNCTION public.record_profile_view(p_profile_owner_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_visitor_id UUID := auth.uid();
    v_owner RECORD;
    v_visitor RECORD;
    v_current_count BIGINT := 0;
BEGIN
    IF v_visitor_id IS NULL THEN
        RETURN jsonb_build_object(
            'recorded', false,
            'reason', 'UNAUTHENTICATED',
            'profile_views_count', 0
        );
    END IF;

    IF p_profile_owner_id IS NULL THEN
        RETURN jsonb_build_object(
            'recorded', false,
            'reason', 'INVALID_TARGET',
            'profile_views_count', 0
        );
    END IF;

    IF v_visitor_id = p_profile_owner_id THEN
        SELECT COALESCE(profile_views_count, 0) INTO v_current_count
        FROM public.profiles
        WHERE id = p_profile_owner_id;

        RETURN jsonb_build_object(
            'recorded', false,
            'reason', 'SELF_VIEW',
            'profile_views_count', COALESCE(v_current_count, 0)
        );
    END IF;

    SELECT id, is_deactivated, is_banned, COALESCE(profile_views_count, 0) AS views_count
    INTO v_owner
    FROM public.profiles
    WHERE id = p_profile_owner_id;

    IF v_owner.id IS NULL OR COALESCE(v_owner.is_deactivated, FALSE) OR COALESCE(v_owner.is_banned, FALSE) THEN
        RETURN jsonb_build_object(
            'recorded', false,
            'reason', 'TARGET_UNAVAILABLE',
            'profile_views_count', COALESCE(v_owner.views_count, 0)
        );
    END IF;

    SELECT id, is_deactivated, is_banned
    INTO v_visitor
    FROM public.profiles
    WHERE id = v_visitor_id;

    IF v_visitor.id IS NULL OR COALESCE(v_visitor.is_deactivated, FALSE) OR COALESCE(v_visitor.is_banned, FALSE) THEN
        RETURN jsonb_build_object(
            'recorded', false,
            'reason', 'VISITOR_UNAVAILABLE',
            'profile_views_count', v_owner.views_count
        );
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.blocks b
        WHERE (b.blocker_id = p_profile_owner_id AND b.blocked_id = v_visitor_id)
           OR (b.blocker_id = v_visitor_id AND b.blocked_id = p_profile_owner_id)
    ) THEN
        RETURN jsonb_build_object(
            'recorded', false,
            'reason', 'BLOCKED',
            'profile_views_count', v_owner.views_count
        );
    END IF;

    -- Serialize concurrent first-visit requests from the same visitor to the same owner
    PERFORM pg_advisory_xact_lock(
        hashtext(v_visitor_id::text),
        hashtext(p_profile_owner_id::text)
    );

    IF EXISTS (
        SELECT 1 FROM public.profile_views pv
        WHERE pv.visitor_id = v_visitor_id
          AND pv.profile_owner_id = p_profile_owner_id
          AND pv.created_at > NOW() - INTERVAL '24 hours'
    ) THEN
        SELECT COALESCE(profile_views_count, 0) INTO v_current_count
        FROM public.profiles
        WHERE id = p_profile_owner_id;

        RETURN jsonb_build_object(
            'recorded', false,
            'reason', 'DEDUPLICATED_24H',
            'profile_views_count', COALESCE(v_current_count, v_owner.views_count)
        );
    END IF;

    INSERT INTO public.profile_views (profile_owner_id, visitor_id)
    VALUES (p_profile_owner_id, v_visitor_id);

    PERFORM set_config('vybe.trusted_profile_view_rpc', 'true', true);

    UPDATE public.profiles
    SET profile_views_count = GREATEST(
            COALESCE(profile_views_count, 0) + 1,
            (SELECT COUNT(*) FROM public.profile_views WHERE profile_owner_id = p_profile_owner_id)
        ),
        updated_at = NOW()
    WHERE id = p_profile_owner_id
    RETURNING profile_views_count INTO v_current_count;

    RETURN jsonb_build_object(
        'recorded', true,
        'reason', 'RECORDED',
        'profile_views_count', COALESCE(v_current_count, 1)
    );
END;
$$;

REVOKE ALL ON FUNCTION public.record_profile_view(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.record_profile_view(UUID) TO authenticated;
