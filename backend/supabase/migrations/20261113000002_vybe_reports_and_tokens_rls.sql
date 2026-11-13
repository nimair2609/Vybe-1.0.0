-- ============================================================================
-- VYBE 2.0 — SUPPLEMENTARY RLS HARDENING (REPORTS, DEVICE TOKENS & LEGACY MEDIA)
-- Migration: 20261113000002_vybe_reports_and_tokens_rls.sql
-- Product: VYBE by NR Next (NR Group)
-- ============================================================================
-- STRICT PRODUCTION SAFETY:
--   - Additive, idempotent, non-destructive.
--   - Do NOT execute automatically on production without explicit approval.
-- ============================================================================

-- ============================================================================
-- 1. SPLIT `public.reports` RLS (PREVENT REPORTERS FROM MUTATING REPORT STATUS)
-- ============================================================================
ALTER TABLE public.reports ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Admins can view and update all reports" ON public.reports;
DROP POLICY IF EXISTS "Users can create reports" ON public.reports;
DROP POLICY IF EXISTS "Reporters and admins can view reports" ON public.reports;
DROP POLICY IF EXISTS "Reporters can submit reports" ON public.reports;
DROP POLICY IF EXISTS "Admins can update reports" ON public.reports;
DROP POLICY IF EXISTS "Admins can delete reports" ON public.reports;

CREATE POLICY "Reporters and admins can view reports"
    ON public.reports
    FOR SELECT
    TO authenticated
    USING (
        auth.uid() = reporter_id
        OR public.is_admin(auth.uid())
    );

CREATE POLICY "Reporters can submit reports"
    ON public.reports
    FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = reporter_id);

CREATE POLICY "Admins can update reports"
    ON public.reports
    FOR UPDATE
    TO authenticated
    USING (public.is_admin(auth.uid()))
    WITH CHECK (public.is_admin(auth.uid()));

CREATE POLICY "Admins can delete reports"
    ON public.reports
    FOR DELETE
    TO authenticated
    USING (public.is_admin(auth.uid()));

-- ============================================================================
-- 2. TIGHTEN `public.user_device_tokens` RLS TO STRICT OWNER-ONLY ACCESS
-- ============================================================================
ALTER TABLE public.user_device_tokens ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users manage own FCM tokens" ON public.user_device_tokens;
DROP POLICY IF EXISTS "Users can manage own device tokens" ON public.user_device_tokens;
DROP POLICY IF EXISTS "Owner-only FCM device token access" ON public.user_device_tokens;

CREATE POLICY "Owner-only FCM device token access"
    ON public.user_device_tokens
    FOR ALL
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- ============================================================================
-- 3. PRESERVE LEGACY FLAT-FILENAME `posts` / `stories` READS WHILE KEEPING
--    `<uid>/...` FOLDER CONTENT AUTHOR-SCOPED & `raw_media` STRICT
-- ============================================================================
DROP POLICY IF EXISTS "Authorized viewers read posts and stories media" ON storage.objects;
CREATE POLICY "Authorized viewers read posts and stories media"
    ON storage.objects
    FOR SELECT
    USING (
        (
            bucket_id IN ('posts', 'stories')
            AND (
                auth.uid()::text = (storage.foldername(name))[1]
                OR public.can_view_author_content(auth.uid(), public.try_parse_uuid((storage.foldername(name))[1]))
                OR (
                    (storage.foldername(name))[1] IS NULL
                    AND auth.role() = 'authenticated'
                )
            )
        )
        OR (
            bucket_id = 'raw_media'
            AND (
                auth.uid()::text = (storage.foldername(name))[1]
                OR public.can_view_author_content(auth.uid(), public.try_parse_uuid((storage.foldername(name))[1]))
            )
        )
    );
