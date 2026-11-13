-- ============================================================================
-- VYBE 2.0 — STANDALONE SAFE ROLLBACK SCRIPT FOR 20261113000001 & 20261113000002
-- Location: backend/supabase/rollbacks/20261113000001_vybe_security_stabilization_rollback.sql
-- ============================================================================
-- NOTE: Kept OUTSIDE `backend/supabase/migrations/` intentionally so Supabase
-- CLI never auto-applies this rollback during forward migrations.
-- Non-destructive to user accounts, posts, comments, stories, and messages.
-- ============================================================================

BEGIN;

-- 1. Storage policies introduced by 20261113000001 / 20261113000002
DROP POLICY IF EXISTS "Chat media conversation peer or admin read" ON storage.objects;
DROP POLICY IF EXISTS "Business docs owner or admin read" ON storage.objects;
DROP POLICY IF EXISTS "Authorized viewers read posts and stories media" ON storage.objects;
DROP POLICY IF EXISTS "Avatars public read" ON storage.objects;
DROP POLICY IF EXISTS "Users delete own media folder" ON storage.objects;
DROP POLICY IF EXISTS "Users update own media folder" ON storage.objects;
DROP POLICY IF EXISTS "Users upload own media folder" ON storage.objects;

-- 2. Reports & Device Token policy restoration (ONLY if rolling back 20261113000002)
DROP POLICY IF EXISTS "Owner-only FCM device token access" ON public.user_device_tokens;
CREATE POLICY "Users manage own FCM tokens"
    ON public.user_device_tokens
    FOR ALL
    USING (auth.uid() = user_id OR public.is_admin(auth.uid()))
    WITH CHECK (auth.uid() = user_id OR public.is_admin(auth.uid()));

DROP POLICY IF EXISTS "Admins can delete reports" ON public.reports;
DROP POLICY IF EXISTS "Admins can update reports" ON public.reports;
DROP POLICY IF EXISTS "Reporters can submit reports" ON public.reports;
DROP POLICY IF EXISTS "Reporters and admins can view reports" ON public.reports;
CREATE POLICY "Admins can view and update all reports"
    ON public.reports
    FOR ALL
    USING (public.is_admin() OR auth.uid() = reporter_id);

-- 3. Live streams update guard & RPCs
DROP TRIGGER IF EXISTS trg_guard_live_streams_update ON public.live_streams;
DROP FUNCTION IF EXISTS public.guard_live_streams_update();
DROP FUNCTION IF EXISTS public.increment_live_stream_viewer_count(UUID, INT);
DROP FUNCTION IF EXISTS public.can_add_conversation_member(UUID, UUID);
DROP FUNCTION IF EXISTS public.get_my_private_profile();
DROP VIEW IF EXISTS public.public_profiles;

-- 4. Private PII sync triggers (keep table or drop safely; no primary profile data lost)
DROP TRIGGER IF EXISTS trg_sync_profile_private_details ON public.profiles;
DROP FUNCTION IF EXISTS public.sync_profile_private_details_from_profiles();
DROP TRIGGER IF EXISTS trg_guard_profile_private_details_moderation ON public.profile_private_details;
DROP FUNCTION IF EXISTS public.guard_profile_private_details_moderation();
DROP TABLE IF EXISTS public.profile_private_details;

COMMIT;
