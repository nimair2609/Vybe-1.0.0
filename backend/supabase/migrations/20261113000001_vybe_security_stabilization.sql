-- ============================================================================
-- VYBE 2.0 — MASTER SECURITY FIX & EXISTING SYSTEM STABILIZATION MIGRATION
-- Migration: 20261113000001_vybe_security_stabilization.sql
-- Product: VYBE by NR Next (NR Group)
-- ============================================================================
-- STRICT PRODUCTION SAFETY:
--   - Additive, idempotent, and non-destructive.
--   - Preserves ALL existing users, posts, comments, reactions, stories,
--     messages, storage objects, and admin accounts.
--   - Preserves compatibility with deployed Android APK (v1.x) and Web clients.
--   - Do NOT execute automatically on production without explicit approval.
-- ============================================================================

-- ============================================================================
-- 1. PROFILES PII ISOLATION & SAFE PUBLIC ACCESS
-- ============================================================================
-- Strategy:
--   a) Create dedicated RLS-protected `public.profile_private_details` for
--      email, phone, date_of_birth, age, is_under_18, ban_reason,
--      suspension_reason, and admin_internal_notes.
--   b) Backfill from `public.profiles` and keep synchronized via trigger so
--      legacy APK clients (`select=*` on `profiles` for own profile) continue
--      working during rollout.
--   c) Provide `public.public_profiles` view (no PII columns) and
--      `public.get_my_private_profile()` RPC for modernized clients.
--   d) Include commented Stage 2 Cutover script at the bottom to NULL out PII
--      on `public.profiles` once legacy APKs are upgraded.
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.profile_private_details (
    id UUID PRIMARY KEY REFERENCES public.profiles(id) ON DELETE CASCADE,
    email VARCHAR(255),
    phone VARCHAR(30),
    date_of_birth DATE,
    age INT,
    is_under_18 BOOLEAN DEFAULT FALSE,
    ban_reason TEXT,
    suspension_reason TEXT,
    admin_internal_notes TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.profile_private_details ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users and admins view own private details" ON public.profile_private_details;
CREATE POLICY "Users and admins view own private details"
    ON public.profile_private_details
    FOR SELECT
    TO authenticated
    USING (auth.uid() = id OR public.is_admin(auth.uid()));

DROP POLICY IF EXISTS "Users insert own private details" ON public.profile_private_details;
CREATE POLICY "Users insert own private details"
    ON public.profile_private_details
    FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = id OR public.is_admin(auth.uid()));

DROP POLICY IF EXISTS "Users update own private details" ON public.profile_private_details;
CREATE POLICY "Users update own private details"
    ON public.profile_private_details
    FOR UPDATE
    TO authenticated
    USING (auth.uid() = id OR public.is_admin(auth.uid()))
    WITH CHECK (auth.uid() = id OR public.is_admin(auth.uid()));

CREATE OR REPLACE FUNCTION public.guard_profile_private_details_moderation()
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
       OR pg_trigger_depth() > 1
       OR (v_caller IS NOT NULL AND public.is_admin(v_caller)) THEN
        RETURN NEW;
    END IF;

    IF TG_OP = 'INSERT' THEN
        NEW.ban_reason := NULL;
        NEW.suspension_reason := NULL;
        NEW.admin_internal_notes := NULL;
    ELSIF TG_OP = 'UPDATE' THEN
        IF NEW.ban_reason IS DISTINCT FROM OLD.ban_reason
           OR NEW.suspension_reason IS DISTINCT FROM OLD.suspension_reason
           OR NEW.admin_internal_notes IS DISTINCT FROM OLD.admin_internal_notes THEN
            RAISE EXCEPTION 'Forbidden: Only authorized VYBE administrators may modify moderation reasons or notes.';
        END IF;
    END IF;

    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_guard_profile_private_details_moderation ON public.profile_private_details;
CREATE TRIGGER trg_guard_profile_private_details_moderation
    BEFORE INSERT OR UPDATE ON public.profile_private_details
    FOR EACH ROW
    EXECUTE FUNCTION public.guard_profile_private_details_moderation();

-- Backfill existing PII safely without modifying existing `profiles` rows
INSERT INTO public.profile_private_details (
    id, email, phone, date_of_birth, age, is_under_18,
    ban_reason, suspension_reason, admin_internal_notes, updated_at
)
SELECT
    p.id,
    p.email,
    p.phone,
    p.date_of_birth,
    p.age,
    COALESCE(p.is_under_18, FALSE),
    p.ban_reason,
    p.suspension_reason,
    p.admin_internal_notes,
    NOW()
FROM public.profiles p
ON CONFLICT (id) DO UPDATE SET
    email = COALESCE(EXCLUDED.email, public.profile_private_details.email),
    phone = COALESCE(EXCLUDED.phone, public.profile_private_details.phone),
    date_of_birth = COALESCE(EXCLUDED.date_of_birth, public.profile_private_details.date_of_birth),
    age = COALESCE(EXCLUDED.age, public.profile_private_details.age),
    is_under_18 = COALESCE(EXCLUDED.is_under_18, public.profile_private_details.is_under_18),
    ban_reason = COALESCE(EXCLUDED.ban_reason, public.profile_private_details.ban_reason),
    suspension_reason = COALESCE(EXCLUDED.suspension_reason, public.profile_private_details.suspension_reason),
    admin_internal_notes = COALESCE(EXCLUDED.admin_internal_notes, public.profile_private_details.admin_internal_notes),
    updated_at = NOW();

-- Keep profile_private_details synced whenever profiles is inserted/updated
CREATE OR REPLACE FUNCTION public.sync_profile_private_details_from_profiles()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    INSERT INTO public.profile_private_details (
        id, email, phone, date_of_birth, age, is_under_18,
        ban_reason, suspension_reason, admin_internal_notes, updated_at
    ) VALUES (
        NEW.id,
        NEW.email,
        NEW.phone,
        NEW.date_of_birth,
        NEW.age,
        COALESCE(NEW.is_under_18, FALSE),
        NEW.ban_reason,
        NEW.suspension_reason,
        NEW.admin_internal_notes,
        NOW()
    )
    ON CONFLICT (id) DO UPDATE SET
        email = COALESCE(EXCLUDED.email, public.profile_private_details.email),
        phone = COALESCE(EXCLUDED.phone, public.profile_private_details.phone),
        date_of_birth = COALESCE(EXCLUDED.date_of_birth, public.profile_private_details.date_of_birth),
        age = COALESCE(EXCLUDED.age, public.profile_private_details.age),
        is_under_18 = COALESCE(EXCLUDED.is_under_18, public.profile_private_details.is_under_18),
        ban_reason = COALESCE(EXCLUDED.ban_reason, public.profile_private_details.ban_reason),
        suspension_reason = COALESCE(EXCLUDED.suspension_reason, public.profile_private_details.suspension_reason),
        admin_internal_notes = COALESCE(EXCLUDED.admin_internal_notes, public.profile_private_details.admin_internal_notes),
        updated_at = NOW();
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_sync_profile_private_details ON public.profiles;
CREATE TRIGGER trg_sync_profile_private_details
    AFTER INSERT OR UPDATE ON public.profiles
    FOR EACH ROW
    EXECUTE FUNCTION public.sync_profile_private_details_from_profiles();

-- Safe public view (explicitly excludes email, phone, DOB, age, moderation notes)
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

-- Secure RPC for authenticated users to read their own private profile fields
CREATE OR REPLACE FUNCTION public.get_my_private_profile()
RETURNS TABLE (
    id UUID,
    email VARCHAR(255),
    phone VARCHAR(30),
    date_of_birth DATE,
    age INT,
    is_under_18 BOOLEAN,
    ban_reason TEXT,
    suspension_reason TEXT,
    updated_at TIMESTAMPTZ
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT
        ppd.id,
        ppd.email,
        ppd.phone,
        ppd.date_of_birth,
        ppd.age,
        ppd.is_under_18,
        ppd.ban_reason,
        ppd.suspension_reason,
        ppd.updated_at
    FROM public.profile_private_details ppd
    WHERE ppd.id = auth.uid();
$$;

REVOKE ALL ON FUNCTION public.get_my_private_profile() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_my_private_profile() TO authenticated;

-- Hide deactivated & banned accounts from public profile directory while preserving self/admin access
DROP POLICY IF EXISTS "Public profiles are viewable by everyone" ON public.profiles;
CREATE POLICY "Public profiles are viewable by everyone"
    ON public.profiles
    FOR SELECT
    USING (
        (COALESCE(is_banned, FALSE) = FALSE AND COALESCE(is_deactivated, FALSE) = FALSE)
        OR auth.uid() = id
        OR public.is_admin(auth.uid())
    );

-- ============================================================================
-- 2. ADMIN PRIVILEGE, BADGE RPC & SEARCH_PATH HARDENING
-- ============================================================================

-- Harden role helper functions with `SET search_path = public, pg_temp`
CREATE OR REPLACE FUNCTION public.is_admin(check_user_id UUID DEFAULT auth.uid())
RETURNS BOOLEAN
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    IF check_user_id IS NULL THEN
        RETURN FALSE;
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.admin_users
        WHERE user_id = check_user_id AND is_active = TRUE
    ) THEN
        RETURN TRUE;
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.profiles
        WHERE id = check_user_id
          AND UPPER(role::text) IN ('ADMIN', 'MODERATOR', 'OWNER_ADMIN', 'SUPER_ADMIN')
    ) THEN
        RETURN TRUE;
    END IF;

    RETURN FALSE;
END;
$$;

CREATE OR REPLACE FUNCTION public.is_owner_admin(check_user_id UUID DEFAULT auth.uid())
RETURNS BOOLEAN
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    IF check_user_id IS NULL THEN
        RETURN FALSE;
    END IF;

    RETURN EXISTS (
        SELECT 1 FROM public.admin_users
        WHERE user_id = check_user_id
          AND role = 'OWNER_ADMIN'
          AND is_active = TRUE
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.get_my_admin_role()
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    curr_id UUID := auth.uid();
    admin_rec RECORD;
    prof_role TEXT;
BEGIN
    IF curr_id IS NULL THEN
        RETURN jsonb_build_object('is_admin', false, 'role', 'NONE');
    END IF;

    SELECT * INTO admin_rec FROM public.admin_users WHERE user_id = curr_id AND is_active = TRUE;
    IF admin_rec.user_id IS NOT NULL THEN
        RETURN jsonb_build_object(
            'is_admin', true,
            'role', admin_rec.role,
            'permissions', admin_rec.permissions,
            'email', admin_rec.email
        );
    END IF;

    SELECT UPPER(role::text) INTO prof_role FROM public.profiles WHERE id = curr_id;
    IF prof_role IN ('ADMIN', 'MODERATOR', 'OWNER_ADMIN', 'SUPER_ADMIN') THEN
        RETURN jsonb_build_object(
            'is_admin', true,
            'role', CASE WHEN prof_role = 'MODERATOR' THEN 'MODERATOR' ELSE 'OWNER_ADMIN' END,
            'permissions', '{"all": true}'::jsonb
        );
    END IF;

    RETURN jsonb_build_object('is_admin', false, 'role', 'USER');
END;
$$;

-- Drop legacy 4-arg vulnerable overloads from 20261029000001
DROP FUNCTION IF EXISTS public.admin_assign_badge(UUID, TEXT, BOOLEAN, UUID);
DROP FUNCTION IF EXISTS public.admin_review_verification_request(UUID, TEXT, TEXT, UUID);

-- Harden provision_owner_admin(TEXT): strictly service_role, DB superuser, or existing OWNER_ADMIN
CREATE OR REPLACE FUNCTION public.provision_owner_admin(target_email TEXT)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_jwt_role TEXT := COALESCE(current_setting('request.jwt.claim.role', true), '');
    v_caller UUID := auth.uid();
    target_user_record RECORD;
    target_uid UUID;
BEGIN
    IF NOT (
        v_jwt_role = 'service_role'
        OR (v_jwt_role = '' AND current_user IN ('postgres', 'supabase_admin'))
        OR (v_caller IS NOT NULL AND public.is_owner_admin(v_caller))
    ) THEN
        RAISE EXCEPTION 'Forbidden: Only service_role or existing OWNER_ADMIN may provision owner administrators.';
    END IF;

    SELECT * INTO target_user_record
    FROM auth.users
    WHERE LOWER(email) = LOWER(TRIM(target_email));

    IF target_user_record.id IS NULL THEN
        RETURN jsonb_build_object(
            'success', false,
            'message', format('User with email %s not found in auth.users.', target_email)
        );
    END IF;

    target_uid := target_user_record.id;

    UPDATE auth.users
    SET email_confirmed_at = COALESCE(email_confirmed_at, NOW())
    WHERE id = target_uid;

    INSERT INTO public.profiles (
        id, username, display_name, email, role, is_verified, is_vip
    ) VALUES (
        target_uid,
        COALESCE(target_user_record.raw_user_meta_data->>'username', LOWER(REGEXP_REPLACE(SPLIT_PART(target_email, '@', 1), '[^a-zA-Z0-9_]', '', 'g'))),
        COALESCE(target_user_record.raw_user_meta_data->>'display_name', 'VYBE Administrator'),
        LOWER(TRIM(target_email)),
        'ADMIN',
        TRUE,
        TRUE
    )
    ON CONFLICT (id) DO UPDATE SET
        role = 'ADMIN',
        is_verified = TRUE,
        is_vip = TRUE,
        email = EXCLUDED.email;

    INSERT INTO public.admin_users (
        user_id, email, role, permissions, is_active
    ) VALUES (
        target_uid,
        LOWER(TRIM(target_email)),
        'OWNER_ADMIN',
        '{"can_moderate_content": true, "can_manage_users": true, "can_grant_badges": true, "can_manage_admins": true, "all": true}'::jsonb,
        TRUE
    )
    ON CONFLICT (user_id) DO UPDATE SET
        role = 'OWNER_ADMIN',
        permissions = '{"can_moderate_content": true, "can_manage_users": true, "can_grant_badges": true, "can_manage_admins": true, "all": true}'::jsonb,
        is_active = TRUE,
        updated_at = NOW();

    INSERT INTO public.admin_audit_logs (
        admin_id, admin_email, action, target_type, target_id, details
    ) VALUES (
        COALESCE(v_caller, target_uid),
        LOWER(TRIM(target_email)),
        'PROVISION_OWNER_ADMIN',
        'admin_users',
        target_uid::text,
        jsonb_build_object('role', 'OWNER_ADMIN', 'status', 'SUCCESS')
    );

    RETURN jsonb_build_object(
        'success', true,
        'user_id', target_uid,
        'email', target_email,
        'role', 'OWNER_ADMIN'
    );
END;
$$;

REVOKE ALL ON FUNCTION public.provision_owner_admin(TEXT) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.provision_owner_admin(TEXT) TO service_role;

-- Harden profile protection trigger against role, badge, approval, and moderation tampering
CREATE OR REPLACE FUNCTION public.enforce_profile_badge_and_role_security()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller UUID := auth.uid();
    v_jwt_role TEXT := COALESCE(current_setting('request.jwt.claim.role', true), '');
BEGIN
    IF v_jwt_role = 'service_role'
       OR (v_jwt_role = '' AND current_user IN ('postgres', 'supabase_admin'))
       OR current_setting('vybe.trusted_badge_rpc', true) = 'true'
       OR (v_caller IS NOT NULL AND public.is_admin(v_caller)) THEN
        RETURN NEW;
    END IF;

    IF NEW.role IS DISTINCT FROM OLD.role
       OR NEW.is_verified IS DISTINCT FROM OLD.is_verified
       OR NEW.is_verified_public_figure IS DISTINCT FROM OLD.is_verified_public_figure
       OR NEW.is_vip IS DISTINCT FROM OLD.is_vip
       OR NEW.vip_expires_at IS DISTINCT FROM OLD.vip_expires_at
       OR NEW.is_verified_business IS DISTINCT FROM OLD.is_verified_business
       OR NEW.is_worker IS DISTINCT FROM OLD.is_worker
       OR NEW.worker_badge_mandatory IS DISTINCT FROM OLD.worker_badge_mandatory
       OR NEW.badge_type IS DISTINCT FROM OLD.badge_type
       OR NEW.badge_approval_status IS DISTINCT FROM OLD.badge_approval_status
       OR NEW.is_banned IS DISTINCT FROM OLD.is_banned
       OR NEW.is_suspended IS DISTINCT FROM OLD.is_suspended
       OR NEW.suspended_until IS DISTINCT FROM OLD.suspended_until
       OR NEW.ban_reason IS DISTINCT FROM OLD.ban_reason
       OR NEW.suspension_reason IS DISTINCT FROM OLD.suspension_reason
       OR NEW.admin_internal_notes IS DISTINCT FROM OLD.admin_internal_notes THEN
        RAISE EXCEPTION 'Forbidden: Only authorized VYBE administrators may grant, modify, or revoke official badges, roles, or moderation flags.';
    END IF;

    IF OLD.is_worker IS TRUE AND OLD.worker_badge_mandatory IS TRUE
       AND NEW.badge_display_status IN ('HIDDEN', 'DECLINED') THEN
        RAISE EXCEPTION 'Organizational policy requires authorized VYBE Worker badges marked mandatory to remain displayed.';
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_enforce_profile_badge_and_role_security ON public.profiles;
CREATE TRIGGER trg_enforce_profile_badge_and_role_security
    BEFORE UPDATE ON public.profiles
    FOR EACH ROW
    EXECUTE FUNCTION public.enforce_profile_badge_and_role_security();

-- Harden admin_assign_badge: never trust caller-supplied p_admin_id under user JWT,
-- prevent self-grant/self-revoke, restrict WORKER badges to OWNER_ADMIN/service_role,
-- and lock execution permissions.
CREATE OR REPLACE FUNCTION public.admin_assign_badge(
    p_target_user_id UUID,
    p_badge_category TEXT,
    p_is_grant BOOLEAN,
    p_admin_id UUID DEFAULT NULL,
    p_vip_expires_at TIMESTAMPTZ DEFAULT NULL,
    p_reason TEXT DEFAULT NULL,
    p_require_acceptance BOOLEAN DEFAULT FALSE,
    p_worker_mandatory BOOLEAN DEFAULT FALSE
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_jwt_role TEXT := COALESCE(current_setting('request.jwt.claim.role', true), '');
    v_admin UUID;
    v_cat TEXT := UPPER(TRIM(COALESCE(p_badge_category, '')));
    v_badge_name TEXT;
    v_approval_state TEXT;
    v_display_state TEXT;
    v_event_key TEXT;
BEGIN
    IF v_jwt_role = 'service_role' OR (v_jwt_role = '' AND current_user IN ('postgres', 'supabase_admin')) THEN
        v_admin := COALESCE(auth.uid(), p_admin_id);
    ELSE
        v_admin := auth.uid();
        IF v_admin IS NULL OR NOT public.is_admin(v_admin) THEN
            RAISE EXCEPTION 'Unauthorized: only VYBE administrators can assign or revoke official badges.';
        END IF;
        IF v_admin = p_target_user_id THEN
            RAISE EXCEPTION 'Forbidden: Self-assignment or self-revocation of official badges is prohibited.';
        END IF;
        IF v_cat = 'WORKER' AND NOT public.is_owner_admin(v_admin) THEN
            RAISE EXCEPTION 'Forbidden: Assigning or revoking WORKER badges requires OWNER_ADMIN privileges.';
        END IF;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM public.profiles WHERE id = p_target_user_id) THEN
        RAISE EXCEPTION 'Target user not found.';
    END IF;

    PERFORM set_config('vybe.trusted_badge_rpc', 'true', true);

    v_approval_state := CASE
        WHEN NOT p_is_grant THEN 'NONE'
        WHEN p_require_acceptance AND NOT (v_cat = 'WORKER' AND p_worker_mandatory) THEN 'PENDING_ACCEPTANCE'
        ELSE 'ACCEPTED'
    END;

    v_display_state := CASE
        WHEN NOT p_is_grant THEN 'ACCEPTED'
        WHEN p_require_acceptance AND NOT (v_cat = 'WORKER' AND p_worker_mandatory) THEN 'HIDDEN'
        ELSE 'ACCEPTED'
    END;

    IF v_cat = 'VERIFIED' THEN
        UPDATE public.profiles
        SET is_verified = p_is_grant,
            is_verified_public_figure = p_is_grant,
            badge_type = CASE WHEN p_is_grant THEN 'VERIFIED' ELSE NULL END,
            badge_approval_status = v_approval_state,
            badge_display_status = v_display_state
        WHERE id = p_target_user_id;
        v_badge_name := 'VYBE Verified (Blue Tick)';
    ELSIF v_cat = 'VIP' THEN
        UPDATE public.profiles
        SET is_vip = p_is_grant,
            vip_expires_at = CASE WHEN p_is_grant THEN p_vip_expires_at ELSE NULL END,
            badge_type = CASE WHEN p_is_grant THEN 'VIP' ELSE NULL END,
            badge_approval_status = v_approval_state,
            badge_display_status = v_display_state
        WHERE id = p_target_user_id;
        v_badge_name := 'VYBE VIP (Gold Badge)';
    ELSIF v_cat = 'SHOP' THEN
        UPDATE public.profiles
        SET is_verified_business = p_is_grant,
            badge_type = CASE WHEN p_is_grant THEN 'SHOP' ELSE NULL END,
            badge_approval_status = v_approval_state,
            badge_display_status = v_display_state
        WHERE id = p_target_user_id;
        v_badge_name := 'VYBE Verified Shop (Gold Badge)';
    ELSIF v_cat = 'WORKER' THEN
        UPDATE public.profiles
        SET is_worker = p_is_grant,
            worker_badge_mandatory = CASE WHEN p_is_grant THEN COALESCE(p_worker_mandatory, FALSE) ELSE FALSE END,
            badge_type = CASE WHEN p_is_grant THEN 'WORKER' ELSE NULL END,
            badge_approval_status = v_approval_state,
            badge_display_status = v_display_state
        WHERE id = p_target_user_id;
        v_badge_name := 'VYBE Worker (Green Tick)';
    ELSE
        PERFORM set_config('vybe.trusted_badge_rpc', 'false', true);
        RAISE EXCEPTION 'Invalid badge category: %', p_badge_category;
    END IF;

    PERFORM set_config('vybe.trusted_badge_rpc', 'false', true);

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'admin_audit_logs') THEN
        INSERT INTO public.admin_audit_logs (admin_id, action, target_type, target_id, details)
        VALUES (
            v_admin,
            CASE WHEN p_is_grant THEN 'GRANT_BADGE' ELSE 'REVOKE_BADGE' END,
            'USER',
            p_target_user_id::text,
            jsonb_build_object(
                'badge_category', v_cat,
                'badge_name', v_badge_name,
                'vip_expires_at', p_vip_expires_at,
                'reason', COALESCE(p_reason, 'Admin badge update')
            )
        );
    END IF;

    v_event_key := 'badge_' || v_cat || '_' || CASE WHEN p_is_grant THEN 'grant_' ELSE 'revoke_' END || EXTRACT(EPOCH FROM NOW())::bigint::text;

    INSERT INTO public.notifications (
        recipient_id, actor_id, type, title, message, target_id, event_key
    ) VALUES (
        p_target_user_id,
        v_admin,
        'BUSINESS_VERIFICATION_UPDATE',
        CASE WHEN p_is_grant THEN 'Official Badge Approved' ELSE 'Official Badge Updated' END,
        CASE
            WHEN p_is_grant AND p_require_acceptance THEN
                'Your ' || v_badge_name || ' has been approved! Open Badges & Verification in Settings to accept and display it.'
            WHEN p_is_grant THEN
                'You have been granted the official ' || v_badge_name || '. You can manage its visibility in Settings.'
            ELSE
                'Your ' || v_badge_name || ' status was revoked by VYBE Trust & Safety.' ||
                CASE WHEN p_reason IS NOT NULL AND LENGTH(TRIM(p_reason)) > 0 THEN ' Reason: ' || p_reason ELSE '' END
        END,
        p_target_user_id::text,
        v_event_key
    )
    ON CONFLICT (recipient_id, event_key) WHERE event_key IS NOT NULL DO NOTHING;

    RETURN jsonb_build_object(
        'success', TRUE,
        'user_id', p_target_user_id,
        'badge', v_cat,
        'granted', p_is_grant,
        'approval_status', v_approval_state,
        'vip_expires_at', p_vip_expires_at
    );
END;
$$;

REVOKE ALL ON FUNCTION public.admin_assign_badge(UUID, TEXT, BOOLEAN, UUID, TIMESTAMPTZ, TEXT, BOOLEAN, BOOLEAN) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.admin_assign_badge(UUID, TEXT, BOOLEAN, UUID, TIMESTAMPTZ, TEXT, BOOLEAN, BOOLEAN) TO authenticated, service_role;

-- Harden admin_review_verification_request
CREATE OR REPLACE FUNCTION public.admin_review_verification_request(
    p_request_id UUID,
    p_decision TEXT,
    p_notes TEXT DEFAULT NULL,
    p_admin_id UUID DEFAULT NULL,
    p_vip_expires_at TIMESTAMPTZ DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_jwt_role TEXT := COALESCE(current_setting('request.jwt.claim.role', true), '');
    v_admin UUID;
    v_req RECORD;
    v_decision TEXT := UPPER(TRIM(COALESCE(p_decision, '')));
    v_badge_type TEXT;
    v_notif_title TEXT;
    v_notif_body TEXT;
    v_event_key TEXT;
BEGIN
    IF v_decision NOT IN ('APPROVED', 'REJECTED', 'MORE_INFO_REQUIRED') THEN
        RAISE EXCEPTION 'Invalid verification decision: %', p_decision;
    END IF;

    IF v_jwt_role = 'service_role' OR (v_jwt_role = '' AND current_user IN ('postgres', 'supabase_admin')) THEN
        v_admin := COALESCE(auth.uid(), p_admin_id);
    ELSE
        v_admin := auth.uid();
        IF v_admin IS NULL OR NOT public.is_admin(v_admin) THEN
            RAISE EXCEPTION 'Unauthorized: only VYBE administrators can review badge applications.';
        END IF;
    END IF;

    SELECT * INTO v_req
    FROM public.verification_requests
    WHERE id = p_request_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Verification request not found.';
    END IF;

    IF v_admin IS NOT NULL AND v_req.user_id = v_admin THEN
        RAISE EXCEPTION 'Conflict of interest: users cannot approve or review their own badge applications.';
    END IF;

    v_badge_type := CASE
        WHEN v_req.request_type ILIKE '%WORKER%' THEN 'WORKER'
        WHEN v_req.request_type ILIKE '%VIP%' THEN 'VIP'
        WHEN v_req.request_type ILIKE '%SHOP%' OR v_req.request_type ILIKE '%BUSINESS%' THEN 'SHOP'
        ELSE 'VERIFIED'
    END;

    UPDATE public.verification_requests
    SET status = v_decision,
        rejection_reason = CASE WHEN v_decision = 'REJECTED' THEN p_notes ELSE rejection_reason END,
        admin_notes = COALESCE(p_notes, admin_notes),
        vip_expires_at = CASE WHEN v_decision = 'APPROVED' AND v_badge_type = 'VIP' THEN p_vip_expires_at ELSE vip_expires_at END,
        user_response_status = CASE WHEN v_decision = 'APPROVED' THEN 'PENDING_ACCEPTANCE' ELSE v_decision END,
        reviewed_by = v_admin,
        reviewed_at = NOW(),
        updated_at = NOW()
    WHERE id = p_request_id;

    IF v_decision = 'APPROVED' THEN
        PERFORM public.admin_assign_badge(
            v_req.user_id,
            v_badge_type,
            TRUE,
            v_admin,
            p_vip_expires_at,
            COALESCE(p_notes, 'Approved via verification application review'),
            TRUE
        );
    ELSE
        IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'admin_audit_logs') THEN
            INSERT INTO public.admin_audit_logs (admin_id, action, target_type, target_id, details)
            VALUES (
                v_admin,
                'REVIEW_BADGE_APPLICATION_' || v_decision,
                'VERIFICATION_REQUEST',
                p_request_id::text,
                jsonb_build_object('applicant_id', v_req.user_id, 'badge_type', v_badge_type, 'notes', p_notes)
            );
        END IF;

        v_notif_title := CASE
            WHEN v_decision = 'REJECTED' THEN 'Badge Application Update'
            ELSE 'Badge Application: More Info Needed'
        END;
        v_notif_body := CASE
            WHEN v_decision = 'REJECTED' THEN 'Your ' || v_badge_type || ' badge application was not approved: ' || COALESCE(p_notes, 'Criteria not met.')
            ELSE 'Additional information is required for your ' || v_badge_type || ' badge application: ' || COALESCE(p_notes, 'Please review and update your submission.')
        END;
        v_event_key := 'verif_req_' || p_request_id::text || '_' || v_decision;

        INSERT INTO public.notifications (
            recipient_id, actor_id, type, title, message, target_id, event_key
        ) VALUES (
            v_req.user_id,
            v_admin,
            'BUSINESS_VERIFICATION_UPDATE',
            v_notif_title,
            v_notif_body,
            p_request_id::text,
            v_event_key
        )
        ON CONFLICT (recipient_id, event_key) WHERE event_key IS NOT NULL DO NOTHING;
    END IF;

    RETURN jsonb_build_object(
        'success', TRUE,
        'request_id', p_request_id,
        'status', v_decision,
        'badge_type', v_badge_type
    );
END;
$$;

REVOKE ALL ON FUNCTION public.admin_review_verification_request(UUID, TEXT, TEXT, UUID, TIMESTAMPTZ) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.admin_review_verification_request(UUID, TEXT, TEXT, UUID, TIMESTAMPTZ) TO authenticated, service_role;

-- Harden user_respond_badge_offer with search_path and clear session flag
CREATE OR REPLACE FUNCTION public.user_respond_badge_offer(
    p_action TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_act TEXT := UPPER(TRIM(COALESCE(p_action, '')));
    v_prof RECORD;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required.';
    END IF;

    SELECT * INTO v_prof FROM public.profiles WHERE id = v_uid;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Profile not found.';
    END IF;

    IF NOT (COALESCE(v_prof.is_verified, FALSE)
            OR COALESCE(v_prof.is_vip, FALSE)
            OR COALESCE(v_prof.is_verified_business, FALSE)
            OR COALESCE(v_prof.is_worker, FALSE)) THEN
        RAISE EXCEPTION 'No approved official badge is currently assigned to your account.';
    END IF;

    IF COALESCE(v_prof.is_worker, FALSE) AND COALESCE(v_prof.worker_badge_mandatory, FALSE)
       AND v_act IN ('DECLINE', 'HIDE') THEN
        RAISE EXCEPTION 'Mandatory authorized VYBE Worker badges cannot be hidden or declined.';
    END IF;

    PERFORM set_config('vybe.trusted_badge_rpc', 'true', true);

    IF v_act = 'ACCEPT' THEN
        UPDATE public.profiles
        SET badge_approval_status = 'ACCEPTED',
            badge_display_status = 'ACCEPTED'
        WHERE id = v_uid;

        UPDATE public.verification_requests
        SET user_response_status = 'ACCEPTED', updated_at = NOW()
        WHERE user_id = v_uid AND status = 'APPROVED';
    ELSIF v_act = 'DECLINE' THEN
        UPDATE public.profiles
        SET badge_approval_status = 'DECLINED',
            badge_display_status = 'DECLINED'
        WHERE id = v_uid;

        UPDATE public.verification_requests
        SET user_response_status = 'DECLINED', updated_at = NOW()
        WHERE user_id = v_uid AND status = 'APPROVED';
    ELSIF v_act = 'HIDE' THEN
        UPDATE public.profiles
        SET badge_display_status = 'HIDDEN'
        WHERE id = v_uid;
    ELSIF v_act = 'SHOW' THEN
        UPDATE public.profiles
        SET badge_approval_status = 'ACCEPTED',
            badge_display_status = 'ACCEPTED'
        WHERE id = v_uid;
    ELSE
        PERFORM set_config('vybe.trusted_badge_rpc', 'false', true);
        RAISE EXCEPTION 'Invalid badge action: %', p_action;
    END IF;

    PERFORM set_config('vybe.trusted_badge_rpc', 'false', true);

    RETURN jsonb_build_object(
        'success', TRUE,
        'user_id', v_uid,
        'action', v_act
    );
END;
$$;

REVOKE ALL ON FUNCTION public.user_respond_badge_offer(TEXT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.user_respond_badge_offer(TEXT) TO authenticated, service_role;

-- ============================================================================
-- 3. CONVERSATIONS, MEMBERS, MESSAGING & MISSING TABLE RLS
-- ============================================================================

-- Ensure created_by defaults to auth.uid() if omitted by legacy callers
ALTER TABLE public.conversations
    ALTER COLUMN created_by SET DEFAULT auth.uid();

CREATE OR REPLACE FUNCTION public.can_add_conversation_member(
    p_conv_id UUID,
    p_actor_id UUID DEFAULT auth.uid()
)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT CASE
        WHEN p_actor_id IS NULL OR p_conv_id IS NULL THEN FALSE
        ELSE EXISTS (
            SELECT 1 FROM public.conversations c
            WHERE c.id = p_conv_id
              AND (
                  c.created_by = p_actor_id
                  OR (COALESCE(c.is_group, FALSE) = TRUE AND public.is_conversation_member(c.id, p_actor_id))
              )
        )
    END;
$$;

REVOKE ALL ON FUNCTION public.can_add_conversation_member(UUID, UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.can_add_conversation_member(UUID, UUID) TO authenticated, service_role;

DROP POLICY IF EXISTS "Authenticated users create conversations" ON public.conversations;
CREATE POLICY "Authenticated users create conversations"
    ON public.conversations
    FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() IS NOT NULL AND auth.uid() = created_by);

DROP POLICY IF EXISTS "Authenticated users add conversation members" ON public.conversation_members;
CREATE POLICY "Authenticated users add conversation members"
    ON public.conversation_members
    FOR INSERT
    TO authenticated
    WITH CHECK (
        auth.uid() IS NOT NULL
        AND public.can_add_conversation_member(conversation_id, auth.uid())
        AND (
            user_id = auth.uid()
            OR NOT public.is_blocked_between(auth.uid(), user_id)
        )
    );

-- Consolidate get_or_create_direct_conversation (enforce MUTUAL + FOLLOWING_ONLY + NO_ONE + private accounts)
DROP FUNCTION IF EXISTS public.get_or_create_direct_conversation(UUID);

CREATE OR REPLACE FUNCTION public.get_or_create_direct_conversation(
    p_peer_id UUID DEFAULT NULL,
    p_other_user_id UUID DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_me UUID := auth.uid();
    v_target_peer UUID := COALESCE(p_peer_id, p_other_user_id);
    v_conv_id UUID;
    v_peer_privacy VARCHAR(30);
    v_peer_private BOOLEAN;
    v_i_follow BOOLEAN;
    v_peer_follows BOOLEAN;
BEGIN
    IF v_me IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    IF v_target_peer IS NULL THEN
        RAISE EXCEPTION 'Target user ID is required';
    END IF;

    IF v_me = v_target_peer THEN
        RAISE EXCEPTION 'Cannot start a direct conversation with yourself';
    END IF;

    IF public.is_blocked_between(v_me, v_target_peer) THEN
        RAISE EXCEPTION 'Cannot message this user due to block restrictions';
    END IF;

    SELECT c.id INTO v_conv_id
    FROM public.conversations c
    JOIN public.conversation_members cm1 ON cm1.conversation_id = c.id AND cm1.user_id = v_me
    JOIN public.conversation_members cm2 ON cm2.conversation_id = c.id AND cm2.user_id = v_target_peer
    WHERE COALESCE(c.is_group, FALSE) = FALSE
    LIMIT 1;

    IF v_conv_id IS NOT NULL THEN
        RETURN v_conv_id;
    END IF;

    SELECT UPPER(COALESCE(message_privacy, 'EVERYONE')), COALESCE(is_private_account, FALSE)
    INTO v_peer_privacy, v_peer_private
    FROM public.profiles
    WHERE id = v_target_peer;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Recipient profile not found';
    END IF;

    IF v_peer_privacy IN ('NO_ONE', 'NONE', 'NOBODY') THEN
        RAISE EXCEPTION 'This user has disabled direct messaging';
    END IF;

    SELECT EXISTS (
        SELECT 1 FROM public.follows
        WHERE follower_id = v_me AND followed_id = v_target_peer AND status = 'ACCEPTED'
    ) INTO v_i_follow;

    SELECT EXISTS (
        SELECT 1 FROM public.follows
        WHERE follower_id = v_target_peer AND followed_id = v_me AND status = 'ACCEPTED'
    ) INTO v_peer_follows;

    IF v_peer_privacy IN ('MUTUAL', 'MUTUAL_FOLLOWS', 'MUTUAL_FOLLOWERS')
       AND NOT (v_i_follow AND v_peer_follows) THEN
        RAISE EXCEPTION 'Mutual follow is required to message this user';
    END IF;

    IF v_peer_privacy = 'FOLLOWING_ONLY' AND NOT v_i_follow AND NOT v_peer_follows THEN
        RAISE EXCEPTION 'This user only accepts messages from accounts they follow or approve';
    END IF;

    IF v_peer_private AND NOT (v_i_follow OR v_peer_follows) THEN
        RAISE EXCEPTION 'Follow approval is required before messaging a private profile';
    END IF;

    INSERT INTO public.conversations (is_group, created_by)
    VALUES (FALSE, v_me)
    RETURNING id INTO v_conv_id;

    INSERT INTO public.conversation_members (conversation_id, user_id)
    VALUES (v_conv_id, v_me), (v_conv_id, v_target_peer)
    ON CONFLICT DO NOTHING;

    RETURN v_conv_id;
END;
$$;

REVOKE ALL ON FUNCTION public.get_or_create_direct_conversation(UUID, UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_or_create_direct_conversation(UUID, UUID) TO authenticated, service_role;

-- Enable missing RLS on message_reactions, message_receipts, audit_logs
ALTER TABLE public.message_reactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.message_receipts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Members view message reactions" ON public.message_reactions;
CREATE POLICY "Members view message reactions"
    ON public.message_reactions
    FOR SELECT
    TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.messages m
            WHERE m.id = message_reactions.message_id
              AND public.is_conversation_member(m.conversation_id, auth.uid())
        )
    );

DROP POLICY IF EXISTS "Members insert own message reactions" ON public.message_reactions;
CREATE POLICY "Members insert own message reactions"
    ON public.message_reactions
    FOR INSERT
    TO authenticated
    WITH CHECK (
        auth.uid() = user_id
        AND EXISTS (
            SELECT 1 FROM public.messages m
            WHERE m.id = message_reactions.message_id
              AND public.is_conversation_member(m.conversation_id, auth.uid())
        )
    );

DROP POLICY IF EXISTS "Members update own message reactions" ON public.message_reactions;
CREATE POLICY "Members update own message reactions"
    ON public.message_reactions
    FOR UPDATE
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "Members delete own message reactions" ON public.message_reactions;
CREATE POLICY "Members delete own message reactions"
    ON public.message_reactions
    FOR DELETE
    TO authenticated
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "Members view message receipts" ON public.message_receipts;
CREATE POLICY "Members view message receipts"
    ON public.message_receipts
    FOR SELECT
    TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.messages m
            WHERE m.id = message_receipts.message_id
              AND public.is_conversation_member(m.conversation_id, auth.uid())
        )
    );

DROP POLICY IF EXISTS "Members insert own message receipts" ON public.message_receipts;
CREATE POLICY "Members insert own message receipts"
    ON public.message_receipts
    FOR INSERT
    TO authenticated
    WITH CHECK (
        auth.uid() = user_id
        AND EXISTS (
            SELECT 1 FROM public.messages m
            WHERE m.id = message_receipts.message_id
              AND public.is_conversation_member(m.conversation_id, auth.uid())
        )
    );

DROP POLICY IF EXISTS "Members update own message receipts" ON public.message_receipts;
CREATE POLICY "Members update own message receipts"
    ON public.message_receipts
    FOR UPDATE
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "Admins view audit logs" ON public.audit_logs;
CREATE POLICY "Admins view audit logs"
    ON public.audit_logs
    FOR SELECT
    TO authenticated
    USING (public.is_admin(auth.uid()));

DROP POLICY IF EXISTS "Admins insert audit logs" ON public.audit_logs;
CREATE POLICY "Admins insert audit logs"
    ON public.audit_logs
    FOR INSERT
    TO authenticated
    WITH CHECK (public.is_admin(auth.uid()));

-- Complete business_products owner CRUD policies
DROP POLICY IF EXISTS "business_products_insert_owner" ON public.business_products;
CREATE POLICY "business_products_insert_owner"
    ON public.business_products
    FOR INSERT
    TO authenticated
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.business_profiles bp
            WHERE bp.id = business_products.business_id
              AND bp.owner_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS "business_products_update_owner" ON public.business_products;
CREATE POLICY "business_products_update_owner"
    ON public.business_products
    FOR UPDATE
    TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.business_profiles bp
            WHERE bp.id = business_products.business_id
              AND bp.owner_id = auth.uid()
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.business_profiles bp
            WHERE bp.id = business_products.business_id
              AND bp.owner_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS "business_products_delete_owner" ON public.business_products;
CREATE POLICY "business_products_delete_owner"
    ON public.business_products
    FOR DELETE
    TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.business_profiles bp
            WHERE bp.id = business_products.business_id
              AND bp.owner_id = auth.uid()
        )
    );

-- ============================================================================
-- 4. EXPLORE ZONE, STORIES & LIVE ZONE RLS HARDENING
-- ============================================================================

-- 4A. Stories: enforce visibility matrix (author block/privacy + story audience)
DROP POLICY IF EXISTS "stories_select_universal_active" ON public.stories;
CREATE POLICY "stories_select_universal_active"
    ON public.stories
    FOR SELECT
    TO anon, authenticated
    USING (
        expires_at > NOW()
        AND public.can_view_author_content(auth.uid(), author_id)
        AND (
            auth.uid() = author_id
            OR UPPER(COALESCE(audience, 'EVERYONE')) = 'EVERYONE'
            OR (
                UPPER(COALESCE(audience, '')) = 'FOLLOWERS'
                AND auth.uid() IS NOT NULL
                AND EXISTS (
                    SELECT 1 FROM public.follows f
                    WHERE f.follower_id = auth.uid()
                      AND f.followed_id = stories.author_id
                      AND COALESCE(f.status, 'ACCEPTED') = 'ACCEPTED'
                )
            )
            OR (
                UPPER(COALESCE(audience, '')) IN ('CLOSE_FRIENDS', 'VYBE_MATCHES', 'FRIENDS')
                AND auth.uid() IS NOT NULL
                AND EXISTS (
                    SELECT 1 FROM public.vybe_matches vm
                    WHERE vm.user_id = stories.author_id
                      AND vm.matched_user_id = auth.uid()
                )
            )
        )
    );

-- 4B. Live Streams: prevent non-hosts from hijacking stream fields or frame injection,
--     while keeping viewer_count join/leave updates compatible with existing Android APKs.
CREATE OR REPLACE FUNCTION public.guard_live_streams_update()
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
       OR (v_caller IS NOT NULL AND (v_caller = OLD.host_id OR public.is_admin(v_caller))) THEN
        RETURN NEW;
    END IF;

    -- Non-host viewers may ONLY adjust viewer_count (clamped safely) for legacy APK compatibility
    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.host_id IS DISTINCT FROM OLD.host_id
       OR NEW.title IS DISTINCT FROM OLD.title
       OR NEW.visibility IS DISTINCT FROM OLD.visibility
       OR NEW.is_live IS DISTINCT FROM OLD.is_live
       OR NEW.started_at IS DISTINCT FROM OLD.started_at
       OR NEW.ended_at IS DISTINCT FROM OLD.ended_at
       OR NEW.category IS DISTINCT FROM OLD.category
       OR NEW.thumbnail_url IS DISTINCT FROM OLD.thumbnail_url
       OR NEW.latest_frame_base64 IS DISTINCT FROM OLD.latest_frame_base64
       OR NEW.last_heartbeat_at IS DISTINCT FROM OLD.last_heartbeat_at THEN
        RAISE EXCEPTION 'Forbidden: Only the live stream host may modify stream metadata, frames, or status.';
    END IF;

    NEW.viewer_count := GREATEST(0, LEAST(COALESCE(NEW.viewer_count, 0), 1000000));
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_guard_live_streams_update ON public.live_streams;
CREATE TRIGGER trg_guard_live_streams_update
    BEFORE UPDATE ON public.live_streams
    FOR EACH ROW
    EXECUTE FUNCTION public.guard_live_streams_update();

-- Dedicated viewer count RPC for atomic join/leave increments in newer clients
CREATE OR REPLACE FUNCTION public.increment_live_stream_viewer_count(
    p_stream_id UUID,
    p_delta INT DEFAULT 1
)
RETURNS INT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_new_count INT;
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    UPDATE public.live_streams
    SET viewer_count = GREATEST(0, COALESCE(viewer_count, 0) + GREATEST(-1, LEAST(COALESCE(p_delta, 0), 1)))
    WHERE id = p_stream_id AND is_live = TRUE
    RETURNING viewer_count INTO v_new_count;

    RETURN COALESCE(v_new_count, 0);
END;
$$;

REVOKE ALL ON FUNCTION public.increment_live_stream_viewer_count(UUID, INT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.increment_live_stream_viewer_count(UUID, INT) TO authenticated, service_role;

-- 4C. Audience Pool Options: creator-only option insert; trigger maintains votes_count
DROP POLICY IF EXISTS "audience_pool_options_insert_auth" ON public.audience_pool_options;
CREATE POLICY "audience_pool_options_insert_auth"
    ON public.audience_pool_options
    FOR INSERT
    TO authenticated
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.audience_pools ap
            WHERE ap.id = pool_id AND ap.creator_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS "audience_pool_options_update_auth" ON public.audience_pool_options;

-- 4D. Double Take & Double Take Answers: restrict mutations to participants / row owner
DROP POLICY IF EXISTS "double_takes_update_participants" ON public.double_takes;
CREATE POLICY "double_takes_update_participants"
    ON public.double_takes
    FOR UPDATE
    TO authenticated
    USING (auth.uid() = creator_id OR auth.uid() = partner_id)
    WITH CHECK (auth.uid() = creator_id OR auth.uid() = partner_id);

DROP POLICY IF EXISTS "double_take_answers_update_auth" ON public.double_take_answers;
CREATE POLICY "double_take_answers_update_auth"
    ON public.double_take_answers
    FOR UPDATE
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "double_take_answers_delete_own" ON public.double_take_answers;
CREATE POLICY "double_take_answers_delete_own"
    ON public.double_take_answers
    FOR DELETE
    TO authenticated
    USING (auth.uid() = user_id);

-- 4E. Time Capsules & Members: creator-only updates; member rows restricted
DROP POLICY IF EXISTS "time_capsules_update_creator" ON public.time_capsules;
CREATE POLICY "time_capsules_update_creator"
    ON public.time_capsules
    FOR UPDATE
    TO authenticated
    USING (auth.uid() = creator_id)
    WITH CHECK (auth.uid() = creator_id);

DROP POLICY IF EXISTS "time_capsule_members_select_all" ON public.time_capsule_members;
CREATE POLICY "time_capsule_members_select_all"
    ON public.time_capsule_members
    FOR SELECT
    TO authenticated
    USING (
        user_id = auth.uid()
        OR EXISTS (
            SELECT 1 FROM public.time_capsules tc
            WHERE tc.id = time_capsule_members.capsule_id
              AND tc.creator_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS "time_capsule_members_insert_auth" ON public.time_capsule_members;
CREATE POLICY "time_capsule_members_insert_auth"
    ON public.time_capsule_members
    FOR INSERT
    TO authenticated
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.time_capsules tc
            WHERE tc.id = time_capsule_members.capsule_id
              AND tc.creator_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS "time_capsule_members_update_self" ON public.time_capsule_members;
CREATE POLICY "time_capsule_members_update_self"
    ON public.time_capsule_members
    FOR UPDATE
    TO authenticated
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());

-- 4F. Crossed Path Requests: visible only to sender or encounter creator
DROP POLICY IF EXISTS "crossed_path_requests_select_auth" ON public.crossed_path_requests;
CREATE POLICY "crossed_path_requests_select_auth"
    ON public.crossed_path_requests
    FOR SELECT
    TO authenticated
    USING (
        auth.uid() = sender_id
        OR EXISTS (
            SELECT 1 FROM public.crossed_paths cp
            WHERE cp.id = crossed_path_requests.encounter_id
              AND cp.creator_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS "crossed_path_requests_update_owner" ON public.crossed_path_requests;
CREATE POLICY "crossed_path_requests_update_owner"
    ON public.crossed_path_requests
    FOR UPDATE
    TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.crossed_paths cp
            WHERE cp.id = crossed_path_requests.encounter_id
              AND cp.creator_id = auth.uid()
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.crossed_paths cp
            WHERE cp.id = crossed_path_requests.encounter_id
              AND cp.creator_id = auth.uid()
        )
    );

-- ============================================================================
-- 5. COMMUNITIES AUTHORIZATION FIXES (PREVENT SELF-ADMIN & PRIVATE LEAKAGE)
-- ============================================================================

DROP POLICY IF EXISTS "Members can view community members" ON public.community_members;
CREATE POLICY "Members can view community members"
    ON public.community_members
    FOR SELECT
    TO authenticated
    USING (
        auth.uid() = user_id
        OR EXISTS (
            SELECT 1 FROM public.communities c
            WHERE c.id = community_members.community_id
              AND (
                  c.is_private = FALSE
                  OR c.creator_id = auth.uid()
                  OR EXISTS (
                      SELECT 1 FROM public.community_members cm2
                      WHERE cm2.community_id = c.id
                        AND cm2.user_id = auth.uid()
                        AND cm2.status = 'ACCEPTED'
                  )
              )
        )
    );

DROP POLICY IF EXISTS "Users can join communities" ON public.community_members;
CREATE POLICY "Users can join communities"
    ON public.community_members
    FOR INSERT
    TO authenticated
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.communities c
            WHERE c.id = community_members.community_id
              AND c.creator_id = auth.uid()
        )
        OR (
            auth.uid() = user_id
            AND role = 'MEMBER'
            AND (
                status = 'PENDING'
                OR (
                    status = 'ACCEPTED'
                    AND EXISTS (
                        SELECT 1 FROM public.communities c
                        WHERE c.id = community_members.community_id
                          AND c.is_private = FALSE
                    )
                )
            )
        )
    );

DROP POLICY IF EXISTS "Public can view community post likes" ON public.community_post_likes;
CREATE POLICY "Public can view community post likes"
    ON public.community_post_likes
    FOR SELECT
    TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.community_posts cp
            JOIN public.communities c ON c.id = cp.community_id
            WHERE cp.id = community_post_likes.post_id
              AND (
                  c.is_private = FALSE
                  OR c.creator_id = auth.uid()
                  OR EXISTS (
                      SELECT 1 FROM public.community_members cm
                      WHERE cm.community_id = c.id
                        AND cm.user_id = auth.uid()
                        AND cm.status = 'ACCEPTED'
                  )
              )
        )
    );

DROP POLICY IF EXISTS "Users can like community posts" ON public.community_post_likes;
CREATE POLICY "Users can like community posts"
    ON public.community_post_likes
    FOR INSERT
    TO authenticated
    WITH CHECK (
        auth.uid() = user_id
        AND EXISTS (
            SELECT 1 FROM public.community_posts cp
            JOIN public.communities c ON c.id = cp.community_id
            WHERE cp.id = community_post_likes.post_id
              AND (
                  c.is_private = FALSE
                  OR c.creator_id = auth.uid()
                  OR EXISTS (
                      SELECT 1 FROM public.community_members cm
                      WHERE cm.community_id = c.id
                        AND cm.user_id = auth.uid()
                        AND cm.status = 'ACCEPTED'
                  )
              )
        )
    );

DROP POLICY IF EXISTS "Public can view community post comments" ON public.community_post_comments;
CREATE POLICY "Public can view community post comments"
    ON public.community_post_comments
    FOR SELECT
    TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.community_posts cp
            JOIN public.communities c ON c.id = cp.community_id
            WHERE cp.id = community_post_comments.post_id
              AND (
                  c.is_private = FALSE
                  OR c.creator_id = auth.uid()
                  OR EXISTS (
                      SELECT 1 FROM public.community_members cm
                      WHERE cm.community_id = c.id
                        AND cm.user_id = auth.uid()
                        AND cm.status = 'ACCEPTED'
                  )
              )
        )
    );

DROP POLICY IF EXISTS "Users can create comments on community posts" ON public.community_post_comments;
CREATE POLICY "Users can create comments on community posts"
    ON public.community_post_comments
    FOR INSERT
    TO authenticated
    WITH CHECK (
        auth.uid() = author_id
        AND EXISTS (
            SELECT 1 FROM public.community_posts cp
            JOIN public.communities c ON c.id = cp.community_id
            WHERE cp.id = community_post_comments.post_id
              AND (
                  c.is_private = FALSE
                  OR c.creator_id = auth.uid()
                  OR EXISTS (
                      SELECT 1 FROM public.community_members cm
                      WHERE cm.community_id = c.id
                        AND cm.user_id = auth.uid()
                        AND cm.status = 'ACCEPTED'
                  )
              )
        )
    );

-- ============================================================================
-- 6. SUPABASE STORAGE BUCKET & POLICY HARDENING
-- ============================================================================

-- Remove legacy unscoped policies from `storage_setup.sql` and broad chat-media read policy
DROP POLICY IF EXISTS "Public media access" ON storage.objects;
DROP POLICY IF EXISTS "Users can upload their own avatar" ON storage.objects;
DROP POLICY IF EXISTS "Users can upload profile banner" ON storage.objects;
DROP POLICY IF EXISTS "Users can upload post media" ON storage.objects;
DROP POLICY IF EXISTS "Business owners can upload verification docs" ON storage.objects;
DROP POLICY IF EXISTS "Admins can view business verification docs" ON storage.objects;
DROP POLICY IF EXISTS "Avatars public read" ON storage.objects;
DROP POLICY IF EXISTS "Users upload own media folder" ON storage.objects;
DROP POLICY IF EXISTS "Users update own media folder" ON storage.objects;
DROP POLICY IF EXISTS "Users delete own media folder" ON storage.objects;
DROP POLICY IF EXISTS "Authorized viewers read posts and stories media" ON storage.objects;
DROP POLICY IF EXISTS "Business docs owner or admin read" ON storage.objects;
DROP POLICY IF EXISTS "Chat media conversation peer or admin read" ON storage.objects;

-- Recreate strict folder-owner write policies (`<uid>/<filename>`)
CREATE POLICY "Users upload own media folder"
    ON storage.objects
    FOR INSERT
    TO authenticated
    WITH CHECK (
        bucket_id IN ('avatars', 'banners', 'posts', 'stories', 'raw_media', 'business_docs', 'chat-media')
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

CREATE POLICY "Users update own media folder"
    ON storage.objects
    FOR UPDATE
    TO authenticated
    USING (
        bucket_id IN ('avatars', 'banners', 'posts', 'stories', 'raw_media', 'business_docs', 'chat-media')
        AND (storage.foldername(name))[1] = auth.uid()::text
    )
    WITH CHECK (
        bucket_id IN ('avatars', 'banners', 'posts', 'stories', 'raw_media', 'business_docs', 'chat-media')
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

CREATE POLICY "Users delete own media folder"
    ON storage.objects
    FOR DELETE
    TO authenticated
    USING (
        bucket_id IN ('avatars', 'banners', 'posts', 'stories', 'raw_media', 'business_docs', 'chat-media')
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

-- Bucket-scoped SELECT policies
CREATE POLICY "Avatars public read"
    ON storage.objects
    FOR SELECT
    USING (bucket_id IN ('avatars', 'banners'));

CREATE POLICY "Authorized viewers read posts and stories media"
    ON storage.objects
    FOR SELECT
    USING (
        bucket_id IN ('posts', 'stories', 'raw_media')
        AND (
            auth.uid()::text = (storage.foldername(name))[1]
            OR public.can_view_author_content(auth.uid(), public.try_parse_uuid((storage.foldername(name))[1]))
        )
    );

CREATE POLICY "Business docs owner or admin read"
    ON storage.objects
    FOR SELECT
    TO authenticated
    USING (
        bucket_id = 'business_docs'
        AND (
            (storage.foldername(name))[1] = auth.uid()::text
            OR public.is_admin(auth.uid())
        )
    );

CREATE POLICY "Chat media conversation peer or admin read"
    ON storage.objects
    FOR SELECT
    TO authenticated
    USING (
        bucket_id = 'chat-media'
        AND (
            (storage.foldername(name))[1] = auth.uid()::text
            OR public.is_admin(auth.uid())
            OR EXISTS (
                SELECT 1
                FROM public.conversation_members cm1
                JOIN public.conversation_members cm2
                  ON cm1.conversation_id = cm2.conversation_id
                WHERE cm1.user_id = auth.uid()
                  AND cm2.user_id::text = (storage.foldername(name))[1]
            )
        )
    );

-- ============================================================================
-- 7. OPTIONAL STAGE 2 CUTOVER (DO NOT RUN UNTIL LEGACY v1.x APKs RETIRE)
-- ============================================================================
-- Legacy Android APKs read `profiles?select=*` and map `email`, `phone`, `age`,
-- `date_of_birth`, `ban_reason`, `suspension_reason` when hydrating the logged-in
-- user. Run Stage 2 ONLY after Android/Web clients read those fields solely from
-- `public.get_my_private_profile()` / `public.profile_private_details`:
--
-- UPDATE public.profiles
-- SET email = NULL,
--     phone = NULL,
--     date_of_birth = NULL,
--     age = NULL,
--     ban_reason = NULL,
--     suspension_reason = NULL,
--     admin_internal_notes = NULL
-- WHERE email IS NOT NULL OR phone IS NOT NULL OR date_of_birth IS NOT NULL OR age IS NOT NULL;
-- ============================================================================

-- ============================================================================
-- 8. COMMENTED ROLLBACK / DOWN SCRIPT REFERENCE
-- ============================================================================
-- DROP POLICY IF EXISTS "Chat media conversation peer or admin read" ON storage.objects;
-- DROP POLICY IF EXISTS "Business docs owner or admin read" ON storage.objects;
-- DROP POLICY IF EXISTS "Authorized viewers read posts and stories media" ON storage.objects;
-- DROP POLICY IF EXISTS "Avatars public read" ON storage.objects;
-- DROP POLICY IF EXISTS "Users delete own media folder" ON storage.objects;
-- DROP POLICY IF EXISTS "Users update own media folder" ON storage.objects;
-- DROP POLICY IF EXISTS "Users upload own media folder" ON storage.objects;
-- DROP TRIGGER IF EXISTS trg_guard_live_streams_update ON public.live_streams;
-- DROP FUNCTION IF EXISTS public.guard_live_streams_update();
-- DROP FUNCTION IF EXISTS public.increment_live_stream_viewer_count(UUID, INT);
-- DROP FUNCTION IF EXISTS public.can_add_conversation_member(UUID, UUID);
-- DROP FUNCTION IF EXISTS public.get_my_private_profile();
-- DROP VIEW IF EXISTS public.public_profiles;
-- DROP TRIGGER IF EXISTS trg_sync_profile_private_details ON public.profiles;
-- DROP FUNCTION IF EXISTS public.sync_profile_private_details_from_profiles();
-- DROP TRIGGER IF EXISTS trg_guard_profile_private_details_moderation ON public.profile_private_details;
-- DROP FUNCTION IF EXISTS public.guard_profile_private_details_moderation();
-- DROP TABLE IF EXISTS public.profile_private_details;
-- ============================================================================
