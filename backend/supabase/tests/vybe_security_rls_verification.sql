-- ============================================================================
-- VYBE 2.0 — NEGATIVE & POSITIVE SECURITY VERIFICATION SUITE
-- File: backend/supabase/tests/vybe_security_rls_verification.sql
-- ============================================================================
-- SAFE NON-DESTRUCTIVE VERIFICATION:
--   - Intended for Staging / Branch DB or inside `BEGIN; ... ROLLBACK;` in SQL Editor.
--   - Does NOT modify production rows permanently when wrapped in ROLLBACK.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- CHECK 1: Static Catalog Audit (Run on any DB right after applying migration)
-- ----------------------------------------------------------------------------
SELECT
    n.nspname AS schema_name,
    c.relname AS table_name,
    c.relrowsecurity AS rls_enabled
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public'
  AND c.relkind = 'r'
  AND c.relname IN (
      'profiles', 'profile_private_details', 'admin_users', 'verification_requests',
      'admin_audit_logs', 'audit_logs', 'reports', 'user_device_tokens',
      'conversations', 'conversation_members',
      'messages', 'message_reactions', 'message_receipts', 'business_products',
      'live_streams', 'audience_pool_options', 'double_takes', 'double_take_answers',
      'time_capsules', 'time_capsule_members', 'crossed_path_requests',
      'communities', 'community_members', 'community_post_likes', 'community_post_comments'
  )
ORDER BY c.relname;

-- Expected: Every listed table has rls_enabled = true.

-- Verify legacy 4-arg vulnerable RPC overloads are gone & search_path is pinned
SELECT
    p.proname AS function_name,
    pg_get_function_identity_arguments(p.oid) AS args,
    p.prosecdef AS is_security_definer,
    p.proconfig AS config
FROM pg_proc p
JOIN pg_namespace n ON n.oid = p.pronamespace
WHERE n.nspname = 'public'
  AND p.proname IN (
      'is_admin', 'is_owner_admin', 'get_my_admin_role',
      'provision_owner_admin', 'admin_assign_badge',
      'admin_review_verification_request', 'user_respond_badge_offer',
      'enforce_profile_badge_and_role_security', 'get_or_create_direct_conversation',
      'can_add_conversation_member', 'get_my_private_profile',
      'guard_live_streams_update', 'increment_live_stream_viewer_count'
  )
ORDER BY p.proname;

-- Verify execute grants on provision_owner_admin & admin_assign_badge
SELECT
    routine_name,
    grantee,
    privilege_type
FROM information_schema.role_routine_grants
WHERE specific_schema = 'public'
  AND routine_name IN ('provision_owner_admin', 'admin_assign_badge', 'admin_review_verification_request', 'get_my_private_profile')
ORDER BY routine_name, grantee;

-- ----------------------------------------------------------------------------
-- CHECK 2: Negative Privilege Escalation & Cross-Tenant Tests (Rolled back)
-- Note: Replace UUIDs with two real staging/test user IDs before running.
-- ----------------------------------------------------------------------------
BEGIN;

DO $$
DECLARE
    v_user_a UUID;
    v_user_b UUID;
    v_other_profile_email_count INT := 0;
    v_priv_count INT := 0;
BEGIN
    SELECT id INTO v_user_a FROM public.profiles WHERE role = 'USER' ORDER BY created_at ASC LIMIT 1;
    SELECT id INTO v_user_b FROM public.profiles WHERE role = 'USER' AND id <> v_user_a ORDER BY created_at ASC LIMIT 1;

    IF v_user_a IS NULL OR v_user_b IS NULL THEN
        RAISE NOTICE 'Skipping simulated JWT checks: fewer than 2 standard test profiles present.';
        RETURN;
    END IF;

    -- Simulate authenticated non-admin user A
    PERFORM set_config('role', 'authenticated', true);
    PERFORM set_config('request.jwt.claim.role', 'authenticated', true);
    PERFORM set_config('request.jwt.claim.sub', v_user_a::text, true);

    -- Test A: Non-admin cannot see User B's profile_private_details
    SELECT COUNT(*) INTO v_priv_count
    FROM public.profile_private_details
    WHERE id = v_user_b;
    IF v_priv_count <> 0 THEN
        RAISE EXCEPTION 'SECURITY FAILURE: User A read User B profile_private_details!';
    END IF;

    -- Test B: Non-admin cannot self-elevate role or badges on public.profiles
    BEGIN
        UPDATE public.profiles SET role = 'ADMIN', is_verified = TRUE WHERE id = v_user_a;
        RAISE EXCEPTION 'SECURITY FAILURE: Non-admin User A elevated own role/badge!';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM LIKE 'SECURITY FAILURE:%' THEN RAISE; END IF;
    END;

    -- Test C: Non-admin cannot invoke admin_assign_badge
    BEGIN
        PERFORM public.admin_assign_badge(v_user_b, 'VERIFIED', TRUE, v_user_a);
        RAISE EXCEPTION 'SECURITY FAILURE: Non-admin User A executed admin_assign_badge!';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM LIKE 'SECURITY FAILURE:%' THEN RAISE; END IF;
    END;

    -- Test D: Non-admin cannot invoke provision_owner_admin
    BEGIN
        PERFORM public.provision_owner_admin('attacker@example.com');
        RAISE EXCEPTION 'SECURITY FAILURE: Non-admin User A executed provision_owner_admin!';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM LIKE 'SECURITY FAILURE:%' THEN RAISE; END IF;
    END;

    -- Test E: Public view hides email/phone columns
    SELECT COUNT(*) INTO v_other_profile_email_count
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'public_profiles'
      AND column_name IN ('email', 'phone', 'date_of_birth', 'ban_reason', 'suspension_reason');
    IF v_other_profile_email_count <> 0 THEN
        RAISE EXCEPTION 'SECURITY FAILURE: public_profiles exposes sensitive PII columns!';
    END IF;

    -- Test F: Standard user cannot read another user's FCM device tokens
    PERFORM 1 FROM public.user_device_tokens WHERE user_id = v_user_b;
    IF FOUND THEN
        RAISE EXCEPTION 'SECURITY FAILURE: User A read User B user_device_tokens!';
    END IF;

    -- Test G: Standard reporter cannot UPDATE their own report status to RESOLVED
    INSERT INTO public.reports (id, reporter_id, target_type, target_id, reason, status)
    VALUES ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', v_user_a, 'POST', v_user_b::text, 'RLS verification test', 'OPEN')
    ON CONFLICT DO NOTHING;

    UPDATE public.reports
    SET status = 'RESOLVED'
    WHERE id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
    IF FOUND THEN
        RAISE EXCEPTION 'SECURITY FAILURE: Reporter mutated own report status!';
    END IF;

    RESET ROLE;
    RAISE NOTICE 'ALL NEGATIVE SECURITY ASSERTIONS PASSED (rolling back test transaction).';
END $$;

ROLLBACK;
