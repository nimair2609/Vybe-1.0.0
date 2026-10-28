-- ====================================================================
-- VYBE 3.0 MASTER SECURE ADMIN SYSTEM MIGRATION & PROVISIONING
-- Product of NR Next | NR Group
-- Date: 2026-10-28
-- ====================================================================

-- 1. Create Dedicated Admin Role Enum
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'admin_sub_role_enum') THEN
        CREATE TYPE admin_sub_role_enum AS ENUM ('OWNER_ADMIN', 'MODERATOR', 'SUPPORT_ADMIN', 'ANALYST');
    END IF;
END $$;

-- 2. Add profile administrative columns if not already present
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS is_suspended BOOLEAN DEFAULT FALSE;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS suspended_until TIMESTAMPTZ;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS suspension_reason TEXT;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS is_banned BOOLEAN DEFAULT FALSE;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS ban_reason TEXT;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS admin_internal_notes TEXT;

-- 3. Dedicated Admin Users Table (Zero-Trust Server Authorization)
CREATE TABLE IF NOT EXISTS public.admin_users (
    user_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    role admin_sub_role_enum NOT NULL DEFAULT 'MODERATOR',
    permissions JSONB DEFAULT '{"can_moderate_content": true, "can_manage_users": false, "can_grant_badges": false, "can_manage_admins": false}'::jsonb,
    is_active BOOLEAN DEFAULT TRUE,
    assigned_by UUID REFERENCES auth.users(id),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 4. Verification Requests Table (Public Figure, VIP, Business Store)
CREATE TABLE IF NOT EXISTS public.verification_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    request_type VARCHAR(50) NOT NULL, -- 'VERIFIED_PUBLIC_FIGURE', 'VIP_CREATOR', 'VERIFIED_BUSINESS'
    full_name VARCHAR(150) NOT NULL,
    category VARCHAR(100) NOT NULL,
    identity_document_url TEXT,
    supporting_links JSONB DEFAULT '[]'::jsonb,
    status VARCHAR(30) DEFAULT 'PENDING', -- 'PENDING', 'APPROVED', 'REJECTED', 'MORE_INFO_REQUIRED'
    rejection_reason TEXT,
    reviewed_by UUID REFERENCES auth.users(id),
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 5. User Moderation Actions (Suspensions, Bans, Warnings, Notes)
CREATE TABLE IF NOT EXISTS public.user_moderation_actions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    action_type VARCHAR(50) NOT NULL, -- 'SUSPEND', 'BAN', 'UNBAN', 'WARN', 'NOTE'
    reason TEXT NOT NULL,
    duration_hours INT,
    expires_at TIMESTAMPTZ,
    is_active BOOLEAN DEFAULT TRUE,
    created_by UUID REFERENCES auth.users(id),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 6. Dedicated Admin Audit Logs
CREATE TABLE IF NOT EXISTS public.admin_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    admin_email VARCHAR(255),
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id VARCHAR(100) NOT NULL,
    details JSONB DEFAULT '{}'::jsonb,
    ip_address VARCHAR(45),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ====================================================================
-- SECURITY DEFINER HELPER FUNCTIONS (Strict Search Path)
-- ====================================================================

-- Function to check if calling user is an authorized admin
CREATE OR REPLACE FUNCTION public.is_admin(check_user_id UUID DEFAULT auth.uid())
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF check_user_id IS NULL THEN
        RETURN FALSE;
    END IF;

    -- Check admin_users table first
    IF EXISTS (
        SELECT 1 FROM public.admin_users 
        WHERE user_id = check_user_id AND is_active = TRUE
    ) THEN
        RETURN TRUE;
    END IF;

    -- Fallback to profiles role check
    IF EXISTS (
        SELECT 1 FROM public.profiles 
        WHERE id = check_user_id AND role IN ('ADMIN', 'MODERATOR')
    ) THEN
        RETURN TRUE;
    END IF;

    RETURN FALSE;
END;
$$;

-- Function to check if calling user is Owner Admin
CREATE OR REPLACE FUNCTION public.is_owner_admin(check_user_id UUID DEFAULT auth.uid())
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
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

-- Function to get admin role details
CREATE OR REPLACE FUNCTION public.get_my_admin_role()
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
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

    SELECT role::text INTO prof_role FROM public.profiles WHERE id = curr_id;
    IF prof_role IN ('ADMIN', 'MODERATOR') THEN
        RETURN jsonb_build_object(
            'is_admin', true,
            'role', CASE WHEN prof_role = 'ADMIN' THEN 'OWNER_ADMIN' ELSE 'MODERATOR' END,
            'permissions', '{"all": true}'::jsonb
        );
    END IF;

    RETURN jsonb_build_object('is_admin', false, 'role', 'USER');
END;
$$;

-- ====================================================================
-- ROW LEVEL SECURITY (RLS) FOR ADMIN TABLES
-- ====================================================================

ALTER TABLE public.admin_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.verification_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_moderation_actions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.admin_audit_logs ENABLE ROW LEVEL SECURITY;

-- admin_users RLS:
DROP POLICY IF EXISTS "Admins can view admin users" ON public.admin_users;
CREATE POLICY "Admins can view admin users" 
ON public.admin_users FOR SELECT 
USING (public.is_admin());

DROP POLICY IF EXISTS "Owner Admins can manage admin users" ON public.admin_users;
CREATE POLICY "Owner Admins can manage admin users" 
ON public.admin_users FOR ALL 
USING (public.is_owner_admin());

-- verification_requests RLS:
DROP POLICY IF EXISTS "Users can view their own verification requests" ON public.verification_requests;
CREATE POLICY "Users can view their own verification requests" 
ON public.verification_requests FOR SELECT 
USING (auth.uid() = user_id OR public.is_admin());

DROP POLICY IF EXISTS "Users can submit verification request" ON public.verification_requests;
CREATE POLICY "Users can submit verification request" 
ON public.verification_requests FOR INSERT 
WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "Admins can manage verification requests" ON public.verification_requests;
CREATE POLICY "Admins can manage verification requests" 
ON public.verification_requests FOR UPDATE 
USING (public.is_admin());

-- user_moderation_actions RLS:
DROP POLICY IF EXISTS "Admins can view moderation actions" ON public.user_moderation_actions;
CREATE POLICY "Admins can view moderation actions" 
ON public.user_moderation_actions FOR SELECT 
USING (public.is_admin());

DROP POLICY IF EXISTS "Admins can insert moderation actions" ON public.user_moderation_actions;
CREATE POLICY "Admins can insert moderation actions" 
ON public.user_moderation_actions FOR INSERT 
WITH CHECK (public.is_admin());

-- admin_audit_logs RLS:
DROP POLICY IF EXISTS "Admins can view audit logs" ON public.admin_audit_logs;
CREATE POLICY "Admins can view audit logs" 
ON public.admin_audit_logs FOR SELECT 
USING (public.is_admin());

DROP POLICY IF EXISTS "Admins can insert audit logs" ON public.admin_audit_logs;
CREATE POLICY "Admins can insert audit logs" 
ON public.admin_audit_logs FOR INSERT 
WITH CHECK (public.is_admin());

-- reports table admin access update:
DROP POLICY IF EXISTS "Admins can view and update all reports" ON public.reports;
CREATE POLICY "Admins can view and update all reports" 
ON public.reports FOR ALL 
USING (public.is_admin() OR auth.uid() = reporter_id);

-- ====================================================================
-- SAFE FIRST-ADMIN PROVISIONING STORED PROCEDURE
-- Elevates renukadas083@gmail.com and nrnexthq@gmail.com without data loss
-- ====================================================================

CREATE OR REPLACE FUNCTION public.provision_owner_admin(target_email TEXT)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    target_user_record RECORD;
    target_uid UUID;
BEGIN
    SELECT * INTO target_user_record 
    FROM auth.users 
    WHERE LOWER(email) = LOWER(TRIM(target_email));

    IF target_user_record.id IS NULL THEN
        RETURN jsonb_build_object(
            'success', false, 
            'message', format('User with email %s not found in auth.users. Please sign up first.', target_email)
        );
    END IF;

    target_uid := target_user_record.id;

    -- Confirm email if not already confirmed
    UPDATE auth.users 
    SET email_confirmed_at = COALESCE(email_confirmed_at, NOW())
    WHERE id = target_uid;

    -- Update or create profile with ADMIN role and verified status
    INSERT INTO public.profiles (
        id,
        username,
        display_name,
        email,
        role,
        is_verified,
        is_vip
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

    -- Upsert into admin_users with OWNER_ADMIN role
    INSERT INTO public.admin_users (
        user_id,
        email,
        role,
        permissions,
        is_active
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

    -- Insert into admin_audit_logs
    INSERT INTO public.admin_audit_logs (
        admin_id,
        admin_email,
        action,
        target_type,
        target_id,
        details
    ) VALUES (
        target_uid,
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

-- Auto-provision the known owner emails if already present in auth.users
SELECT public.provision_owner_admin('renukadas083@gmail.com');
SELECT public.provision_owner_admin('nrnexthq@gmail.com');
