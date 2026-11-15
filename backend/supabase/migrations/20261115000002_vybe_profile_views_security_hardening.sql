-- ============================================================================
-- VYBE 2.1.1 — PROFILE VIEWS & EXTENDED PROFILE SECURITY HARDENING
-- Migration: 20261115000002_vybe_profile_views_security_hardening.sql
-- ============================================================================
-- STRICT PRODUCTION SAFETY:
--   - Additive & idempotent hardening on top of 20261115000001.
--   - Enforces SECURITY DEFINER with empty search_path ('') and explicit
--     schema qualification across all profile-view guards and RPCs.
--   - Revokes direct client INSERT/UPDATE/DELETE on public.profile_views so
--     only public.record_profile_view(UUID) can insert deduplicated views.
--   - Adds is_suspended checks for both owner and visitor.
--   - Does NOT execute automatically on production without Founder approval.
-- ============================================================================

-- 1. Lock down direct table writes on public.profile_views
REVOKE INSERT, UPDATE, DELETE ON TABLE public.profile_views FROM PUBLIC, anon, authenticated;
GRANT SELECT ON TABLE public.profile_views TO authenticated;

DROP POLICY IF EXISTS "Authenticated visitors insert non-blocked profile views" ON public.profile_views;

-- Ensure SELECT policy remains strict (owner or active admin only)
DROP POLICY IF EXISTS "Profile owners and admins read own profile views" ON public.profile_views;
CREATE POLICY "Profile owners and admins read own profile views"
    ON public.profile_views
    FOR SELECT
    TO authenticated
    USING (auth.uid() = profile_owner_id OR public.is_admin(auth.uid()));

-- 2. Harden profile_views_count immutability trigger with SET search_path = ''
CREATE OR REPLACE FUNCTION public.guard_profile_views_count_immutable()
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
       OR pg_catalog.current_setting('vybe.trusted_profile_view_rpc', true) = 'true'
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

-- 3. Harden record_profile_view(UUID) RPC with empty search_path and suspension checks
CREATE OR REPLACE FUNCTION public.record_profile_view(p_profile_owner_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_visitor_id UUID := auth.uid();
    v_owner RECORD;
    v_visitor RECORD;
    v_current_count BIGINT := 0;
BEGIN
    IF v_visitor_id IS NULL THEN
        RETURN pg_catalog.jsonb_build_object(
            'recorded', false,
            'reason', 'UNAUTHENTICATED',
            'profile_views_count', 0
        );
    END IF;

    IF p_profile_owner_id IS NULL THEN
        RETURN pg_catalog.jsonb_build_object(
            'recorded', false,
            'reason', 'INVALID_TARGET',
            'profile_views_count', 0
        );
    END IF;

    IF v_visitor_id = p_profile_owner_id THEN
        SELECT COALESCE(profile_views_count, 0) INTO v_current_count
        FROM public.profiles
        WHERE id = p_profile_owner_id;

        RETURN pg_catalog.jsonb_build_object(
            'recorded', false,
            'reason', 'SELF_VIEW',
            'profile_views_count', COALESCE(v_current_count, 0)
        );
    END IF;

    SELECT
        id,
        is_deactivated,
        is_banned,
        is_suspended,
        COALESCE(profile_views_count, 0) AS views_count
    INTO v_owner
    FROM public.profiles
    WHERE id = p_profile_owner_id;

    IF v_owner.id IS NULL
       OR COALESCE(v_owner.is_deactivated, FALSE)
       OR COALESCE(v_owner.is_banned, FALSE)
       OR COALESCE(v_owner.is_suspended, FALSE) THEN
        RETURN pg_catalog.jsonb_build_object(
            'recorded', false,
            'reason', 'TARGET_UNAVAILABLE',
            'profile_views_count', COALESCE(v_owner.views_count, 0)
        );
    END IF;

    SELECT id, is_deactivated, is_banned, is_suspended
    INTO v_visitor
    FROM public.profiles
    WHERE id = v_visitor_id;

    IF v_visitor.id IS NULL
       OR COALESCE(v_visitor.is_deactivated, FALSE)
       OR COALESCE(v_visitor.is_banned, FALSE)
       OR COALESCE(v_visitor.is_suspended, FALSE) THEN
        RETURN pg_catalog.jsonb_build_object(
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
        RETURN pg_catalog.jsonb_build_object(
            'recorded', false,
            'reason', 'BLOCKED',
            'profile_views_count', v_owner.views_count
        );
    END IF;

    -- Serialize concurrent first-visit requests from the same visitor to the same owner
    PERFORM pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtext(v_visitor_id::text),
        pg_catalog.hashtext(p_profile_owner_id::text)
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

        RETURN pg_catalog.jsonb_build_object(
            'recorded', false,
            'reason', 'DEDUPLICATED_24H',
            'profile_views_count', COALESCE(v_current_count, v_owner.views_count)
        );
    END IF;

    INSERT INTO public.profile_views (profile_owner_id, visitor_id)
    VALUES (p_profile_owner_id, v_visitor_id);

    PERFORM pg_catalog.set_config('vybe.trusted_profile_view_rpc', 'true', true);

    UPDATE public.profiles
    SET profile_views_count = GREATEST(
            COALESCE(profile_views_count, 0) + 1,
            (SELECT COUNT(*) FROM public.profile_views WHERE profile_owner_id = p_profile_owner_id)
        ),
        updated_at = NOW()
    WHERE id = p_profile_owner_id
    RETURNING profile_views_count INTO v_current_count;

    RETURN pg_catalog.jsonb_build_object(
        'recorded', true,
        'reason', 'RECORDED',
        'profile_views_count', COALESCE(v_current_count, 1)
    );
END;
$$;

REVOKE ALL ON FUNCTION public.record_profile_view(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.record_profile_view(UUID) TO authenticated;
