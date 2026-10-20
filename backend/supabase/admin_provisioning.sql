-- ====================================================================
-- VYBE 3.0 SECURE INITIAL ADMIN PROVISIONING SCRIPT
-- Execute this script in the Supabase SQL Editor to elevate an authorized
-- account to ADMIN status.
-- ====================================================================

-- Replace 'YOUR_ADMIN_EMAIL@domain.com' with the initial authorized administrator email:
DO $$
DECLARE
    target_admin_email TEXT := 'nrnexthq@gmail.com';
    target_user_id UUID;
BEGIN
    SELECT id INTO target_user_id FROM auth.users WHERE email = target_admin_email;

    IF target_user_id IS NOT NULL THEN
        UPDATE public.profiles
        SET 
            role = 'ADMIN',
            is_verified = TRUE,
            is_vip = TRUE
        WHERE id = target_user_id;

        INSERT INTO public.audit_logs (actor_id, action, target_entity, details)
        VALUES (
            target_user_id,
            'PROVISION_FIRST_ADMIN',
            'profiles',
            jsonb_build_object('email', target_admin_email, 'assigned_role', 'ADMIN')
        );

        RAISE NOTICE 'SUCCESS: User % elevated to ADMIN role with Verified VIP badges.', target_admin_email;
    ELSE
        RAISE NOTICE 'NOTICE: User with email % has not signed up yet. Sign up through the app first, then re-run this script.', target_admin_email;
    END IF;
END $$;
