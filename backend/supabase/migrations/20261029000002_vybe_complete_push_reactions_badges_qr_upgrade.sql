-- ==============================================================================
-- VYBE COMPLETE UPGRADE: DEDUPLICATED PUSH & IN-APP NOTIFICATIONS,
-- 5-REACTION ENGINE, 3 OFFICIAL BADGE FAMILIES, USER ACCEPTANCE & EXPIRY,
-- AND STRICT RLS PRIVILEGE ESCALATION PROTECTION
-- Migration: 20261029000002_vybe_complete_push_reactions_badges_qr_upgrade.sql
-- Safe, additive, idempotent, and non-destructive (preserves all real user data).
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. NOTIFICATIONS & FCM DEVICE TOKENS (DEDUPLICATION + RLS)
-- ------------------------------------------------------------------------------

ALTER TABLE public.notifications
    ADD COLUMN IF NOT EXISTS event_key VARCHAR(160) DEFAULT NULL;

-- Clear duplicate event_keys on any older historical rows before creating unique index
WITH ranked_notifs AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY recipient_id, event_key ORDER BY created_at DESC, id DESC) AS rn
    FROM public.notifications
    WHERE event_key IS NOT NULL
)
UPDATE public.notifications n
SET event_key = NULL
FROM ranked_notifs r
WHERE n.id = r.id AND r.rn > 1;

CREATE UNIQUE INDEX IF NOT EXISTS idx_notifications_recipient_event_key
    ON public.notifications (recipient_id, event_key)
    WHERE event_key IS NOT NULL;

CREATE TABLE IF NOT EXISTS public.user_device_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    fcm_token TEXT NOT NULL,
    device_model VARCHAR(120) DEFAULT 'Android Phone',
    platform VARCHAR(32) DEFAULT 'android',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, fcm_token)
);

CREATE INDEX IF NOT EXISTS idx_user_device_tokens_user_active
    ON public.user_device_tokens (user_id, is_active);

ALTER TABLE public.user_device_tokens ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users manage own device tokens" ON public.user_device_tokens;
CREATE POLICY "Users manage own device tokens"
ON public.user_device_tokens FOR ALL
USING (auth.uid() = user_id OR public.is_admin(auth.uid()))
WITH CHECK (auth.uid() = user_id OR public.is_admin(auth.uid()));

-- ------------------------------------------------------------------------------
-- 2. FIVE OFFICIAL REACTIONS (LOVE 🫰, FLAME 🔥, LAUGH 😂, WOW 😮, SAD 😢)
--    Preserves legacy reaction values (PULSE, SPARK, WAVE) safely.
-- ------------------------------------------------------------------------------

ALTER TABLE public.posts
    ADD COLUMN IF NOT EXISTS love_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS laugh_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS wow_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS sad_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS flame_count INT NOT NULL DEFAULT 0;

-- Drop older check constraints first so legacy rows can be normalized cleanly
ALTER TABLE public.post_reactions
    DROP CONSTRAINT IF EXISTS chk_vybe_four_reaction_types;
ALTER TABLE public.post_reactions
    DROP CONSTRAINT IF EXISTS chk_vybe_five_reaction_types;

-- Temporarily drop reaction triggers while normalizing historical rows so bulk cleanup is fast and silent
DROP TRIGGER IF EXISTS trg_recompute_post_reaction_counts ON public.post_reactions;
DROP TRIGGER IF EXISTS trg_reaction_change ON public.post_reactions;

-- Normalize all historical/legacy reaction_type rows (e.g. 'LIKE', 'HEART', lowercase, etc.)
-- so zero rows violate chk_vybe_five_reaction_types
UPDATE public.post_reactions
SET reaction_type = CASE UPPER(BTRIM(COALESCE(reaction_type, 'LOVE')))
    WHEN 'LOVE'      THEN 'LOVE'
    WHEN 'LIKE'      THEN 'LOVE'
    WHEN 'HEART'     THEN 'LOVE'
    WHEN 'CARE'      THEN 'LOVE'
    WHEN 'FLAME'     THEN 'FLAME'
    WHEN 'FIRE'      THEN 'FLAME'
    WHEN 'HEAT'      THEN 'FLAME'
    WHEN 'LAUGH'     THEN 'LAUGH'
    WHEN 'HAHA'      THEN 'LAUGH'
    WHEN 'FUNNY'     THEN 'LAUGH'
    WHEN 'WOW'       THEN 'WOW'
    WHEN 'SURPRISE'  THEN 'WOW'
    WHEN 'UPLIFT'    THEN 'WOW'
    WHEN 'SAD'       THEN 'SAD'
    WHEN 'CRY'       THEN 'SAD'
    WHEN 'CALM'      THEN 'SAD'
    WHEN 'DEEP_VIBE' THEN 'SAD'
    WHEN 'PULSE'     THEN 'PULSE'
    WHEN 'SPARK'     THEN 'SPARK'
    WHEN 'WAVE'      THEN 'WAVE'
    ELSE 'LOVE'
END
WHERE reaction_type IS NULL
   OR reaction_type NOT IN ('LOVE', 'FLAME', 'LAUGH', 'WOW', 'SAD', 'PULSE', 'SPARK', 'WAVE');

-- Remove any duplicate (post_id, user_id) reactions from pre-index tables, keeping newest
WITH ranked_reactions AS (
    SELECT ctid AS row_ctid,
           ROW_NUMBER() OVER (PARTITION BY post_id, user_id ORDER BY created_at DESC NULLS LAST) AS rn
    FROM public.post_reactions
)
DELETE FROM public.post_reactions pr
USING ranked_reactions rr
WHERE pr.ctid = rr.row_ctid AND rr.rn > 1;

CREATE UNIQUE INDEX IF NOT EXISTS idx_post_reactions_unique_post_user
    ON public.post_reactions (post_id, user_id);

ALTER TABLE public.post_reactions
    ADD CONSTRAINT chk_vybe_five_reaction_types
    CHECK (reaction_type IN ('LOVE', 'FLAME', 'LAUGH', 'WOW', 'SAD', 'PULSE', 'SPARK', 'WAVE'));

-- Ensure RLS policies on post_reactions permit all 5 official reactions across devices
ALTER TABLE public.post_reactions ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Authenticated users manage own reactions" ON public.post_reactions;
DROP POLICY IF EXISTS "Users manage own reactions" ON public.post_reactions;
DROP POLICY IF EXISTS "Users insert own reaction" ON public.post_reactions;
DROP POLICY IF EXISTS "Users update own reaction" ON public.post_reactions;
DROP POLICY IF EXISTS "Users delete own reaction" ON public.post_reactions;

CREATE POLICY "Users insert own reaction"
    ON public.post_reactions FOR INSERT
    WITH CHECK (
        auth.uid() = user_id
        AND reaction_type IN ('LOVE', 'FLAME', 'LAUGH', 'WOW', 'SAD', 'PULSE', 'SPARK', 'WAVE')
    );

CREATE POLICY "Users update own reaction"
    ON public.post_reactions FOR UPDATE
    USING (auth.uid() = user_id)
    WITH CHECK (
        auth.uid() = user_id
        AND reaction_type IN ('LOVE', 'FLAME', 'LAUGH', 'WOW', 'SAD', 'PULSE', 'SPARK', 'WAVE')
    );

CREATE POLICY "Users delete own reaction"
    ON public.post_reactions FOR DELETE
    USING (auth.uid() = user_id);

CREATE OR REPLACE FUNCTION public.recompute_post_reaction_counts()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_post_id UUID;
BEGIN
    v_post_id := COALESCE(NEW.post_id, OLD.post_id);

    UPDATE public.posts
    SET likes_count = (SELECT COUNT(*) FROM public.post_reactions WHERE post_id = v_post_id),
        love_count  = (SELECT COUNT(*) FROM public.post_reactions WHERE post_id = v_post_id AND reaction_type = 'LOVE'),
        flame_count = (SELECT COUNT(*) FROM public.post_reactions WHERE post_id = v_post_id AND reaction_type = 'FLAME'),
        laugh_count = (SELECT COUNT(*) FROM public.post_reactions WHERE post_id = v_post_id AND reaction_type IN ('LAUGH', 'PULSE')),
        wow_count   = (SELECT COUNT(*) FROM public.post_reactions WHERE post_id = v_post_id AND reaction_type IN ('WOW', 'SPARK')),
        sad_count   = (SELECT COUNT(*) FROM public.post_reactions WHERE post_id = v_post_id AND reaction_type IN ('SAD', 'WAVE'))
    WHERE id = v_post_id;

    RETURN COALESCE(NEW, OLD);
END;
$$;

CREATE TRIGGER trg_recompute_post_reaction_counts
AFTER INSERT OR UPDATE OR DELETE ON public.post_reactions
FOR EACH ROW EXECUTE FUNCTION public.recompute_post_reaction_counts();

-- Backfill reaction breakdown counts across all existing posts
UPDATE public.posts p
SET likes_count = COALESCE(agg.total_cnt, 0),
    love_count  = COALESCE(agg.love_cnt, 0),
    flame_count = COALESCE(agg.flame_cnt, 0),
    laugh_count = COALESCE(agg.laugh_cnt, 0),
    wow_count   = COALESCE(agg.wow_cnt, 0),
    sad_count   = COALESCE(agg.sad_cnt, 0)
FROM (
    SELECT p2.id AS post_id,
           COUNT(pr.user_id)::INT AS total_cnt,
           COUNT(pr.user_id) FILTER (WHERE pr.reaction_type = 'LOVE')::INT AS love_cnt,
           COUNT(pr.user_id) FILTER (WHERE pr.reaction_type = 'FLAME')::INT AS flame_cnt,
           COUNT(pr.user_id) FILTER (WHERE pr.reaction_type IN ('LAUGH', 'PULSE'))::INT AS laugh_cnt,
           COUNT(pr.user_id) FILTER (WHERE pr.reaction_type IN ('WOW', 'SPARK'))::INT AS wow_cnt,
           COUNT(pr.user_id) FILTER (WHERE pr.reaction_type IN ('SAD', 'WAVE'))::INT AS sad_cnt
    FROM public.posts p2
    LEFT JOIN public.post_reactions pr ON pr.post_id = p2.id
    GROUP BY p2.id
) agg
WHERE p.id = agg.post_id;

-- ------------------------------------------------------------------------------
-- 3. THREE OFFICIAL BADGE TYPES, VIP EXPIRY, USER ACCEPTANCE & HIDE CONTROLS
-- ------------------------------------------------------------------------------

ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS is_verified_public_figure BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS is_verified_business BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS is_worker BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS worker_badge_mandatory BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS vip_expires_at TIMESTAMPTZ DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS badge_approval_status VARCHAR(24) DEFAULT 'ACCEPTED',
    ADD COLUMN IF NOT EXISTS badge_display_status VARCHAR(24) DEFAULT 'ACCEPTED',
    ADD COLUMN IF NOT EXISTS badge_type VARCHAR(32) DEFAULT NULL;

CREATE TABLE IF NOT EXISTS public.verification_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    request_type VARCHAR(50) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    category VARCHAR(100) NOT NULL,
    identity_document_url TEXT,
    supporting_links JSONB DEFAULT '[]'::jsonb,
    status VARCHAR(30) DEFAULT 'PENDING',
    rejection_reason TEXT,
    admin_notes TEXT,
    reviewed_by UUID REFERENCES auth.users(id),
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE public.verification_requests
    ADD COLUMN IF NOT EXISTS admin_notes TEXT DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS statement TEXT DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS vip_expires_at TIMESTAMPTZ DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS user_response_status VARCHAR(24) DEFAULT 'PENDING_REVIEW';

CREATE INDEX IF NOT EXISTS idx_verification_requests_user_id ON public.verification_requests(user_id);
CREATE INDEX IF NOT EXISTS idx_verification_requests_status ON public.verification_requests(status);

ALTER TABLE public.verification_requests ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users can view their own verification requests" ON public.verification_requests;
CREATE POLICY "Users can view their own verification requests"
    ON public.verification_requests FOR SELECT
    USING (auth.uid() = user_id OR public.is_admin(auth.uid()));

DROP POLICY IF EXISTS "Users can submit verification request" ON public.verification_requests;
CREATE POLICY "Users can submit verification request"
    ON public.verification_requests FOR INSERT
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "Admins can update verification requests" ON public.verification_requests;
CREATE POLICY "Admins can update verification requests"
    ON public.verification_requests FOR UPDATE
    USING (public.is_admin(auth.uid()));

-- Prevent non-admin users from self-granting badges, VIP expiry, or admin roles via profile PATCH!
CREATE OR REPLACE FUNCTION public.enforce_profile_badge_and_role_security()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_caller UUID := auth.uid();
    v_jwt_role TEXT := COALESCE(current_setting('request.jwt.claim.role', true), '');
BEGIN
    -- Allow service_role or authorized SQL SECURITY DEFINER context (when session flag set) or verified Admin
    IF v_jwt_role = 'service_role'
       OR current_setting('vybe.trusted_badge_rpc', true) = 'true'
       OR (v_caller IS NOT NULL AND public.is_admin(v_caller)) THEN
        RETURN NEW;
    END IF;

    -- Block privilege escalation & self-verification by non-admins
    IF NEW.role IS DISTINCT FROM OLD.role
       OR NEW.is_verified IS DISTINCT FROM OLD.is_verified
       OR NEW.is_verified_public_figure IS DISTINCT FROM OLD.is_verified_public_figure
       OR NEW.is_vip IS DISTINCT FROM OLD.is_vip
       OR NEW.vip_expires_at IS DISTINCT FROM OLD.vip_expires_at
       OR NEW.is_verified_business IS DISTINCT FROM OLD.is_verified_business
       OR NEW.is_worker IS DISTINCT FROM OLD.is_worker
       OR NEW.worker_badge_mandatory IS DISTINCT FROM OLD.worker_badge_mandatory
       OR NEW.badge_type IS DISTINCT FROM OLD.badge_type THEN
        RAISE EXCEPTION 'Forbidden: Only authorized VYBE administrators may grant, modify, or revoke official badges or roles.';
    END IF;

    -- Prevent hiding a mandatory worker badge if organizational policy requires display
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
FOR EACH ROW EXECUTE FUNCTION public.enforce_profile_badge_and_role_security();

-- Lock down legacy provision_owner_admin if present so arbitrary clients cannot self-elevate
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_proc p
        JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = 'public' AND p.proname = 'provision_owner_admin'
    ) THEN
        REVOKE EXECUTE ON FUNCTION public.provision_owner_admin(UUID) FROM PUBLIC, anon, authenticated;
    END IF;
EXCEPTION WHEN OTHERS THEN
    NULL;
END $$;

-- ------------------------------------------------------------------------------
-- 4. AUTHORIZED ADMIN & USER BADGE RPC FUNCTIONS (WITH AUDIT LOGS & NOTIFICATIONS)
-- ------------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.admin_assign_badge(
    p_target_user_id UUID,
    p_badge_category TEXT, -- 'VERIFIED', 'VIP', 'SHOP', 'WORKER'
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
AS $$
DECLARE
    v_admin UUID;
    v_cat TEXT := UPPER(TRIM(COALESCE(p_badge_category, '')));
    v_badge_name TEXT;
    v_approval_state TEXT;
    v_display_state TEXT;
    v_event_key TEXT;
BEGIN
    v_admin := COALESCE(auth.uid(), p_admin_id);
    IF v_admin IS NULL OR NOT public.is_admin(v_admin) THEN
        RAISE EXCEPTION 'Unauthorized: only VYBE administrators can assign or revoke official badges.';
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
        RAISE EXCEPTION 'Invalid badge category: %', p_badge_category;
    END IF;

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
        recipient_id,
        actor_id,
        type,
        title,
        message,
        target_id,
        event_key
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

CREATE OR REPLACE FUNCTION public.admin_review_verification_request(
    p_request_id UUID,
    p_decision TEXT, -- 'APPROVED', 'REJECTED', 'MORE_INFO_REQUIRED'
    p_notes TEXT DEFAULT NULL,
    p_admin_id UUID DEFAULT NULL,
    p_vip_expires_at TIMESTAMPTZ DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_admin UUID;
    v_req RECORD;
    v_decision TEXT := UPPER(TRIM(COALESCE(p_decision, '')));
    v_badge_type TEXT;
    v_notif_title TEXT;
    v_notif_body TEXT;
    v_event_key TEXT;
BEGIN
    v_admin := COALESCE(auth.uid(), p_admin_id);
    IF v_admin IS NULL OR NOT public.is_admin(v_admin) THEN
        RAISE EXCEPTION 'Unauthorized: only VYBE administrators can review badge applications.';
    END IF;

    SELECT * INTO v_req
    FROM public.verification_requests
    WHERE id = p_request_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Verification request not found.';
    END IF;

    IF v_req.user_id = v_admin THEN
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
        -- Grant the badge in PENDING_ACCEPTANCE state so the applicant explicitly chooses Accept & Display or Decline!
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
            recipient_id,
            actor_id,
            type,
            title,
            message,
            target_id,
            event_key
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

CREATE OR REPLACE FUNCTION public.user_respond_badge_offer(
    p_action TEXT -- 'ACCEPT', 'DECLINE', 'HIDE', 'SHOW'
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
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
        RAISE EXCEPTION 'Invalid badge action: %', p_action;
    END IF;

    RETURN jsonb_build_object(
        'success', TRUE,
        'user_id', v_uid,
        'action', v_act
    );
END;
$$;

-- ------------------------------------------------------------------------------
-- 5. DEDUPLICATED CANONICAL NOTIFICATION TRIGGERS & ACCURATE COMMENT COUNTS
--    Eliminates duplicate Format-1 (DB trigger) vs Format-2 (client insert) rows!
-- ------------------------------------------------------------------------------

ALTER TABLE public.follows
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACCEPTED';

CREATE OR REPLACE FUNCTION public.handle_follow_change()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_actor_name TEXT;
    v_actor_username TEXT;
BEGIN
    IF TG_OP = 'INSERT' THEN
        SELECT display_name, username INTO v_actor_name, v_actor_username FROM public.profiles WHERE id = NEW.follower_id;
        IF NEW.status = 'ACCEPTED' THEN
            UPDATE public.profiles SET following_count = GREATEST(0, COALESCE(following_count, 0) + 1) WHERE id = NEW.follower_id;
            UPDATE public.profiles SET followers_count = GREATEST(0, COALESCE(followers_count, 0) + 1) WHERE id = NEW.followed_id;
            INSERT INTO public.notifications (recipient_id, actor_id, type, title, message, target_id, event_key)
            VALUES (
                NEW.followed_id,
                NEW.follower_id,
                'FOLLOW',
                COALESCE(v_actor_name, 'New Follower'),
                '@' || COALESCE(v_actor_username, 'vyber') || ' started following you.',
                NEW.follower_id::text,
                'follow:' || NEW.follower_id::text || ':' || NEW.followed_id::text
            )
            ON CONFLICT (recipient_id, event_key) WHERE event_key IS NOT NULL DO NOTHING;
        ELSIF NEW.status = 'PENDING' THEN
            INSERT INTO public.notifications (recipient_id, actor_id, type, title, message, target_id, event_key)
            VALUES (
                NEW.followed_id,
                NEW.follower_id,
                'FOLLOW_REQUEST',
                COALESCE(v_actor_name, 'Follow Request'),
                '@' || COALESCE(v_actor_username, 'vyber') || ' requested to follow you.',
                NEW.follower_id::text,
                'follow_request:' || NEW.follower_id::text || ':' || NEW.followed_id::text
            )
            ON CONFLICT (recipient_id, event_key) WHERE event_key IS NOT NULL DO NOTHING;
        END IF;
        RETURN NEW;
    ELSIF TG_OP = 'UPDATE' THEN
        IF OLD.status = 'PENDING' AND NEW.status = 'ACCEPTED' THEN
            UPDATE public.profiles SET following_count = GREATEST(0, COALESCE(following_count, 0) + 1) WHERE id = NEW.follower_id;
            UPDATE public.profiles SET followers_count = GREATEST(0, COALESCE(followers_count, 0) + 1) WHERE id = NEW.followed_id;
            SELECT display_name, username INTO v_actor_name, v_actor_username FROM public.profiles WHERE id = NEW.followed_id;
            INSERT INTO public.notifications (recipient_id, actor_id, type, title, message, target_id, event_key)
            VALUES (
                NEW.follower_id,
                NEW.followed_id,
                'FOLLOW_ACCEPTED',
                COALESCE(v_actor_name, 'Follow Request Approved'),
                '@' || COALESCE(v_actor_username, 'vyber') || ' approved your follow request.',
                NEW.followed_id::text,
                'follow_accepted:' || NEW.followed_id::text || ':' || NEW.follower_id::text
            )
            ON CONFLICT (recipient_id, event_key) WHERE event_key IS NOT NULL DO NOTHING;
        END IF;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        IF OLD.status = 'ACCEPTED' THEN
            UPDATE public.profiles SET following_count = GREATEST(0, COALESCE(following_count, 0) - 1) WHERE id = OLD.follower_id;
            UPDATE public.profiles SET followers_count = GREATEST(0, COALESCE(followers_count, 0) - 1) WHERE id = OLD.followed_id;
        END IF;
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION public.handle_reaction_change()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_author_id UUID;
    v_actor_name TEXT;
    v_actor_username TEXT;
BEGIN
    IF TG_OP = 'INSERT' OR TG_OP = 'UPDATE' THEN
        SELECT author_id INTO v_author_id FROM public.posts WHERE id = NEW.post_id;
        IF v_author_id IS NOT NULL AND v_author_id <> NEW.user_id THEN
            SELECT display_name, username INTO v_actor_name, v_actor_username FROM public.profiles WHERE id = NEW.user_id;
            INSERT INTO public.notifications (recipient_id, actor_id, type, title, message, target_id, event_key)
            VALUES (
                v_author_id,
                NEW.user_id,
                'LIKE',
                '@' || COALESCE(v_actor_username, 'vyber') || ' reacted to your post',
                COALESCE(v_actor_name, 'Someone') || ' reacted to your post.',
                NEW.post_id::text,
                'like:' || NEW.post_id::text || ':' || NEW.user_id::text
            )
            ON CONFLICT (recipient_id, event_key) WHERE event_key IS NOT NULL
            DO UPDATE SET message = EXCLUDED.message, is_read = FALSE;
        END IF;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_reaction_change ON public.post_reactions;
CREATE TRIGGER trg_reaction_change
    AFTER INSERT OR UPDATE OR DELETE ON public.post_reactions
    FOR EACH ROW EXECUTE FUNCTION public.handle_reaction_change();

CREATE OR REPLACE FUNCTION public.handle_comment_change()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_author_id UUID;
    v_actor_username TEXT;
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE public.posts
        SET comments_count = (SELECT COUNT(*) FROM public.comments WHERE post_id = NEW.post_id)
        WHERE id = NEW.post_id
        RETURNING author_id INTO v_author_id;

        IF v_author_id IS NOT NULL AND v_author_id <> NEW.author_id THEN
            SELECT username INTO v_actor_username FROM public.profiles WHERE id = NEW.author_id;
            INSERT INTO public.notifications (recipient_id, actor_id, type, title, message, target_id, event_key)
            VALUES (
                v_author_id,
                NEW.author_id,
                'COMMENT',
                '@' || COALESCE(v_actor_username, 'vyber') || ' commented on your post',
                'Commented: "' || LEFT(NEW.content, 80) || '"',
                NEW.post_id::text,
                'comment:' || NEW.id::text
            )
            ON CONFLICT (recipient_id, event_key) WHERE event_key IS NOT NULL DO NOTHING;
        END IF;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE public.posts
        SET comments_count = (SELECT COUNT(*) FROM public.comments WHERE post_id = OLD.post_id)
        WHERE id = OLD.post_id;
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$;

-- Reconcile comments_count on all existing posts to match real rows in public.comments (zero ghost counts)
UPDATE public.posts p
SET comments_count = (SELECT COUNT(*) FROM public.comments c WHERE c.post_id = p.id);

-- DOWN / REVERSIBLE ROLLBACK REFERENCE (commented for safety):
-- DROP FUNCTION IF EXISTS public.user_respond_badge_offer(TEXT);
-- DROP TRIGGER IF EXISTS trg_enforce_profile_badge_and_role_security ON public.profiles;
-- DROP FUNCTION IF EXISTS public.enforce_profile_badge_and_role_security();
-- DROP INDEX IF EXISTS idx_notifications_recipient_event_key;
