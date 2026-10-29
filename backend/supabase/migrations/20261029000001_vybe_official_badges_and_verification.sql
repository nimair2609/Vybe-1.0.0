-- ==============================================================================
-- VYBE OFFICIAL BADGES, VERIFICATION WORKFLOW & DEDUPLICATED NOTIFICATIONS
-- Migration: 20261029000001_vybe_official_badges_and_verification.sql
-- ==============================================================================

-- 1. Ensure official badge columns exist on public.profiles
ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS is_worker BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS badge_display_status VARCHAR(20) DEFAULT 'ACCEPTED',
    ADD COLUMN IF NOT EXISTS badge_type VARCHAR(50) DEFAULT NULL;

-- 2. Ensure verification_requests table exists with support for all official badge types
CREATE TABLE IF NOT EXISTS public.verification_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    request_type VARCHAR(50) NOT NULL, -- 'VERIFIED', 'VIP', 'WORKER', 'SHOP', 'VERIFIED_PUBLIC_FIGURE', 'VIP_CREATOR', 'VERIFIED_BUSINESS'
    full_name VARCHAR(150) NOT NULL,
    category VARCHAR(100) NOT NULL,
    identity_document_url TEXT,
    supporting_links JSONB DEFAULT '[]'::jsonb,
    status VARCHAR(30) DEFAULT 'PENDING', -- 'PENDING', 'APPROVED', 'REJECTED', 'MORE_INFO_REQUIRED'
    rejection_reason TEXT,
    admin_notes TEXT,
    reviewed_by UUID REFERENCES auth.users(id),
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Index for speedy admin review queues and user status lookup
CREATE INDEX IF NOT EXISTS idx_verification_requests_user_id ON public.verification_requests(user_id);
CREATE INDEX IF NOT EXISTS idx_verification_requests_status ON public.verification_requests(status);

-- Enable RLS on verification_requests
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

-- 3. Stored Procedure: Admin Assign or Revoke Official Badges
CREATE OR REPLACE FUNCTION public.admin_assign_badge(
    p_target_user_id UUID,
    p_badge_category TEXT, -- 'VERIFIED', 'VIP', 'WORKER', 'SHOP'
    p_is_grant BOOLEAN,
    p_admin_id UUID DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_admin UUID;
    v_target_user RECORD;
    v_badge_name TEXT;
BEGIN
    v_admin := COALESCE(p_admin_id, auth.uid());
    IF v_admin IS NOT NULL AND NOT public.is_admin(v_admin) THEN
        RAISE EXCEPTION 'Unauthorized: only VYBE administrators can assign official badges.';
    END IF;

    SELECT id, username, display_name INTO v_target_user
    FROM public.profiles
    WHERE id = p_target_user_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Target user not found.';
    END IF;

    IF p_badge_category = 'VERIFIED' THEN
        UPDATE public.profiles
        SET is_verified = p_is_grant,
            is_verified_public_figure = p_is_grant,
            badge_type = CASE WHEN p_is_grant THEN 'VERIFIED' ELSE badge_type END,
            badge_display_status = CASE WHEN p_is_grant THEN 'ACCEPTED' ELSE badge_display_status END
        WHERE id = p_target_user_id;
        v_badge_name := 'VYBE Verified (Blue Tick)';
    ELSIF p_badge_category = 'VIP' THEN
        UPDATE public.profiles
        SET is_vip = p_is_grant,
            badge_type = CASE WHEN p_is_grant THEN 'VIP' ELSE badge_type END,
            badge_display_status = CASE WHEN p_is_grant THEN 'ACCEPTED' ELSE badge_display_status END
        WHERE id = p_target_user_id;
        v_badge_name := 'VYBE VIP (Gold Badge)';
    ELSIF p_badge_category = 'WORKER' THEN
        UPDATE public.profiles
        SET is_worker = p_is_grant,
            badge_type = CASE WHEN p_is_grant THEN 'WORKER' ELSE badge_type END,
            badge_display_status = CASE WHEN p_is_grant THEN 'ACCEPTED' ELSE badge_display_status END
        WHERE id = p_target_user_id;
        v_badge_name := 'VYBE Worker (Green Tick)';
    ELSIF p_badge_category = 'SHOP' THEN
        UPDATE public.profiles
        SET is_verified_business = p_is_grant,
            badge_type = CASE WHEN p_is_grant THEN 'SHOP' ELSE badge_type END,
            badge_display_status = CASE WHEN p_is_grant THEN 'ACCEPTED' ELSE badge_display_status END
        WHERE id = p_target_user_id;
        v_badge_name := 'VYBE Verified Shop (Store)';
    ELSE
        RAISE EXCEPTION 'Invalid badge category: %', p_badge_category;
    END IF;

    -- Record audit log if audit table exists
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'admin_audit_logs') THEN
        INSERT INTO public.admin_audit_logs (admin_id, action, target_type, target_id, details)
        VALUES (
            v_admin,
            CASE WHEN p_is_grant THEN 'GRANT_BADGE' ELSE 'REVOKE_BADGE' END,
            'USER',
            p_target_user_id,
            jsonb_build_object('badge_category', p_badge_category, 'badge_name', v_badge_name)
        );
    END IF;

    -- Send notification to user upon granting
    IF p_is_grant THEN
        INSERT INTO public.notifications (
            recipient_id,
            actor_id,
            type,
            title,
            message,
            target_id
        ) VALUES (
            p_target_user_id,
            v_admin,
            'BADGE_APPROVAL',
            '🎉 Badge Granted!',
            'You have been granted the official ' || v_badge_name || '. Manage display preferences in Settings.',
            p_target_user_id::text
        );
    END IF;

    RETURN jsonb_build_object(
        'success', TRUE,
        'user_id', p_target_user_id,
        'badge', p_badge_category,
        'granted', p_is_grant
    );
END;
$$;

-- 4. Stored Procedure: Admin Review Verification Application
CREATE OR REPLACE FUNCTION public.admin_review_verification_request(
    p_request_id UUID,
    p_decision TEXT, -- 'APPROVED', 'REJECTED', 'MORE_INFO_REQUIRED'
    p_notes TEXT DEFAULT NULL,
    p_admin_id UUID DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_admin UUID;
    v_req RECORD;
    v_badge_type TEXT;
    v_notif_title TEXT;
    v_notif_body TEXT;
BEGIN
    v_admin := COALESCE(p_admin_id, auth.uid());
    IF v_admin IS NOT NULL AND NOT public.is_admin(v_admin) THEN
        RAISE EXCEPTION 'Unauthorized: only VYBE administrators can review verification applications.';
    END IF;

    SELECT * INTO v_req
    FROM public.verification_requests
    WHERE id = p_request_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Verification request not found.';
    END IF;

    v_badge_type := CASE 
        WHEN v_req.request_type ILIKE '%WORKER%' THEN 'WORKER'
        WHEN v_req.request_type ILIKE '%VIP%' THEN 'VIP'
        WHEN v_req.request_type ILIKE '%SHOP%' OR v_req.request_type ILIKE '%BUSINESS%' THEN 'SHOP'
        ELSE 'VERIFIED'
    END;

    UPDATE public.verification_requests
    SET status = p_decision,
        rejection_reason = CASE WHEN p_decision = 'REJECTED' THEN p_notes ELSE rejection_reason END,
        admin_notes = p_notes,
        reviewed_by = v_admin,
        reviewed_at = NOW(),
        updated_at = NOW()
    WHERE id = p_request_id;

    IF p_decision = 'APPROVED' THEN
        PERFORM public.admin_assign_badge(v_req.user_id, v_badge_type, TRUE, v_admin);
        v_notif_title := '🎉 Verification Approved!';
        v_notif_body := 'Your application for VYBE ' || v_badge_type || ' verification has been approved!';
    ELSIF p_decision = 'REJECTED' THEN
        v_notif_title := 'Verification Update';
        v_notif_body := 'Your verification application was not approved at this time: ' || COALESCE(p_notes, 'Requirements not met.');
    ELSE
        v_notif_title := 'Verification: Additional Info Needed';
        v_notif_body := 'Our team needs additional information to review your verification: ' || COALESCE(p_notes, 'Please update your details.');
    END IF;

    INSERT INTO public.notifications (
        recipient_id,
        actor_id,
        type,
        title,
        message,
        target_id
    ) VALUES (
        v_req.user_id,
        v_admin,
        'VERIFICATION_UPDATE',
        v_notif_title,
        v_notif_body,
        p_request_id::text
    );

    RETURN jsonb_build_object(
        'success', TRUE,
        'request_id', p_request_id,
        'status', p_decision
    );
END;
$$;
