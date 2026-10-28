-- ====================================================================
-- VYBE 3.0 SECURE INITIAL ADMIN PROVISIONING SCRIPT (V2)
-- Product of NR Next | NR Group
-- Execute this script in the Supabase SQL Editor (Dashboard -> SQL Editor)
-- to elevate 'renukadas083@gmail.com' and 'nrnexthq@gmail.com' to OWNER_ADMIN
-- ====================================================================

-- 1. Ensure columns and tables exist
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS is_suspended BOOLEAN DEFAULT FALSE;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS suspended_until TIMESTAMPTZ;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS suspension_reason TEXT;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS is_banned BOOLEAN DEFAULT FALSE;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS ban_reason TEXT;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS admin_internal_notes TEXT;

DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'admin_sub_role_enum') THEN
        CREATE TYPE admin_sub_role_enum AS ENUM ('OWNER_ADMIN', 'MODERATOR', 'SUPPORT_ADMIN', 'ANALYST');
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS public.admin_users (
    user_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    role admin_sub_role_enum NOT NULL DEFAULT 'MODERATOR',
    permissions JSONB DEFAULT '{"all": true}'::jsonb,
    is_active BOOLEAN DEFAULT TRUE,
    assigned_by UUID REFERENCES auth.users(id),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

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

-- 2. Execute Admin Provisioning for the authorized administrator emails
DO $$
DECLARE
    target_emails TEXT[] := ARRAY['renukadas083@gmail.com', 'nrnexthq@gmail.com'];
    target_email TEXT;
    target_user_record RECORD;
    target_user_id UUID;
BEGIN
    FOREACH target_email IN ARRAY target_emails
    LOOP
        SELECT * INTO target_user_record 
        FROM auth.users 
        WHERE LOWER(email) = LOWER(target_email);

        IF target_user_record.id IS NOT NULL THEN
            target_user_id := target_user_record.id;
            
            -- Confirm email
            UPDATE auth.users
            SET email_confirmed_at = COALESCE(email_confirmed_at, NOW())
            WHERE id = target_user_id;

            -- Elevate profile
            INSERT INTO public.profiles (
                id,
                username,
                display_name,
                email,
                role,
                is_verified,
                is_vip
            ) VALUES (
                target_user_id,
                COALESCE(target_user_record.raw_user_meta_data->>'username', LOWER(REGEXP_REPLACE(SPLIT_PART(target_email, '@', 1), '[^a-zA-Z0-9_]', '', 'g'))),
                COALESCE(target_user_record.raw_user_meta_data->>'display_name', 'VYBE Administrator'),
                target_email,
                'ADMIN',
                TRUE,
                TRUE
            )
            ON CONFLICT (id) DO UPDATE SET
                role = 'ADMIN',
                is_verified = TRUE,
                is_vip = TRUE,
                email = EXCLUDED.email;

            -- Insert into admin_users
            INSERT INTO public.admin_users (
                user_id,
                email,
                role,
                permissions,
                is_active
            ) VALUES (
                target_user_id,
                target_email,
                'OWNER_ADMIN',
                '{"all": true, "can_moderate_content": true, "can_manage_users": true, "can_grant_badges": true, "can_manage_admins": true}'::jsonb,
                TRUE
            )
            ON CONFLICT (user_id) DO UPDATE SET
                role = 'OWNER_ADMIN',
                is_active = TRUE;

            -- Log action
            INSERT INTO public.admin_audit_logs (
                admin_id, admin_email, action, target_type, target_id, details
            ) VALUES (
                target_user_id, target_email, 'PROVISION_OWNER_ADMIN', 'admin_users', target_user_id::text, jsonb_build_object('role', 'OWNER_ADMIN')
            );

            RAISE NOTICE 'SUCCESS: % (UUID: %) elevated to OWNER_ADMIN with full privileges.', target_email, target_user_id;
        ELSE
            RAISE NOTICE 'INFO: % has not signed up in Supabase Auth yet. Run this again after signing up.', target_email;
        END IF;
    END LOOP;
END $$;
