-- ====================================================================
-- VYBE 3.0 SECURE INITIAL ADMIN PROVISIONING SCRIPT
-- Execute this script in the Supabase SQL Editor (Dashboard -> SQL Editor)
-- to elevate the authorized account 'nrnexthq@gmail.com' to ADMIN status
-- with Verified VIP badges, without encountering PostgreSQL error 'P0001'.
-- ====================================================================

-- 1. Ensure the trigger function allows administrative / SQL Editor operations
-- (Prevents PostgreSQL error P0001 when executed by database admin / postgres role)
CREATE OR REPLACE FUNCTION public.check_profile_elevation()
RETURNS TRIGGER AS $$
DECLARE
    jwt_role TEXT;
BEGIN
    IF (OLD.role IS DISTINCT FROM NEW.role OR 
        OLD.is_verified IS DISTINCT FROM NEW.is_verified OR 
        OLD.is_vip IS DISTINCT FROM NEW.is_vip) THEN
        
        -- Check if request is coming from PostgREST / Supabase Client API
        BEGIN
            jwt_role := current_setting('request.jwt.claim.role', true);
        EXCEPTION WHEN OTHERS THEN
            jwt_role := NULL;
        END;

        -- If executed directly by database administrator in SQL Editor / migrations
        -- (where jwt_role is NULL) or via backend service_role, allow elevation
        IF jwt_role IS NULL OR jwt_role = 'service_role' OR current_user IN ('postgres', 'supabase_admin') THEN
            RETURN NEW;
        END IF;

        -- For client-initiated updates (authenticated users via API):
        -- Caller MUST be an authenticated ADMIN
        IF NOT EXISTS (SELECT 1 FROM public.profiles WHERE id = auth.uid() AND role = 'ADMIN') THEN
            RAISE EXCEPTION 'Only administrators can modify verification badges or role status.';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 2. Execute Admin Provisioning for the authorized administrator email
DO $$
DECLARE
    target_admin_email TEXT := 'nrnexthq@gmail.com';
    target_user_record RECORD;
    target_user_id UUID;
    target_username TEXT;
    target_display_name TEXT;
BEGIN
    -- Case-insensitive lookup in auth.users
    SELECT * INTO target_user_record 
    FROM auth.users 
    WHERE LOWER(email) = LOWER(target_admin_email);

    IF target_user_record.id IS NOT NULL THEN
        target_user_id := target_user_record.id;
        
        -- 2a. Confirm email in auth.users so the admin can log in immediately
        UPDATE auth.users
        SET email_confirmed_at = COALESCE(email_confirmed_at, NOW())
        WHERE id = target_user_id;

        -- 2b. Extract metadata or set clean defaults
        target_username := COALESCE(
            target_user_record.raw_user_meta_data->>'username',
            LOWER(REGEXP_REPLACE(SPLIT_PART(target_admin_email, '@', 1), '[^a-zA-Z0-9_]', '', 'g'))
        );
        target_display_name := COALESCE(
            target_user_record.raw_user_meta_data->>'display_name',
            'VYBE Administrator'
        );

        -- 2c. Upsert into public.profiles to guarantee existence and ADMIN role
        INSERT INTO public.profiles (
            id,
            username,
            display_name,
            first_name,
            last_name,
            email,
            role,
            is_verified,
            is_vip
        ) VALUES (
            target_user_id,
            target_username,
            target_display_name,
            COALESCE(target_user_record.raw_user_meta_data->>'first_name', 'Admin'),
            COALESCE(target_user_record.raw_user_meta_data->>'last_name', 'User'),
            target_admin_email,
            'ADMIN',
            TRUE,
            TRUE
        )
        ON CONFLICT (id) DO UPDATE SET
            role = 'ADMIN',
            is_verified = TRUE,
            is_vip = TRUE,
            email = EXCLUDED.email;

        -- 2d. Ensure user settings row exists
        INSERT INTO public.user_settings (user_id)
        VALUES (target_user_id)
        ON CONFLICT (user_id) DO NOTHING;

        -- 2e. Ensure notification preferences row exists
        INSERT INTO public.notification_preferences (user_id)
        VALUES (target_user_id)
        ON CONFLICT (user_id) DO NOTHING;

        -- 2f. Record security audit log
        INSERT INTO public.audit_logs (actor_id, action, target_entity, details)
        VALUES (
            target_user_id,
            'PROVISION_FIRST_ADMIN',
            'profiles',
            jsonb_build_object(
                'email', target_admin_email,
                'user_id', target_user_id,
                'assigned_role', 'ADMIN',
                'is_verified', TRUE,
                'is_vip', TRUE
            )
        );

        RAISE NOTICE 'SUCCESS: Account % (UUID: %) has been confirmed and elevated to ADMIN with Verified VIP badges.', target_admin_email, target_user_id;
    ELSE
        RAISE NOTICE 'NOTICE: User with email % has not signed up in Supabase Auth yet. Please register via the VYBE Android app (or invite via Supabase Auth Dashboard), then re-run this script.', target_admin_email;
    END IF;
END $$;

-- OPTIONAL: If the administrator forgets their password or needs a direct reset via SQL Editor:
-- UPDATE auth.users 
-- SET encrypted_password = crypt('YourNewSecurePassword123!', gen_salt('bf'))
-- WHERE LOWER(email) = 'nrnexthq@gmail.com';
