-- =============================================================================
-- VYBE MANUAL APPLICATION DATA RESET SCRIPT (REQUIRES EXPLICIT APPROVAL)
-- =============================================================================
-- WARNING:
--   1. Do NOT place inside `migrations/` and NEVER auto-run on startup/deploy.
--   2. Preserves ALL Supabase Auth accounts (`auth.users`, `auth.identities`),
--      user UUIDs, credentials, `public.admin_users`, and `storage.buckets`.
--   3. Must be explicitly armed by setting the session guard variable below:
--        SET LOCAL vybe.confirm_app_data_reset = 'YES_RESET_APP_DATA_KEEP_AUTH';
-- =============================================================================

BEGIN;

DO $$
BEGIN
  IF COALESCE(current_setting('vybe.confirm_app_data_reset', true), '') <> 'YES_RESET_APP_DATA_KEEP_AUTH' THEN
    RAISE EXCEPTION 'Aborted: Set vybe.confirm_app_data_reset = ''YES_RESET_APP_DATA_KEEP_AUTH'' after explicit approval before running.';
  END IF;
END $$;

-- Helper to delete rows safely across schema variations without failing if optional tables are absent
DO $$
DECLARE
  target_tables text[] := ARRAY[
    -- 1. Feed engagement & analytics leaves
    'public.post_views',
    'public.post_reactions',
    'public.saved_posts',
    'public.comments',
    'public.post_media',
    'public.story_views',

    -- 2. Messaging & WebRTC calls
    'public.message_receipts',
    'public.message_reactions',
    'public.messages',
    'public.conversation_members',
    'public.conversations',
    'public.call_signals',
    'public.call_sessions',

    -- 3. Circle / communities
    'public.community_post_comments',
    'public.community_post_likes',
    'public.community_posts',
    'public.community_members',
    'public.communities',

    -- 4. Rooms, polls, capsules, double takes, crossed paths, live streams
    'public.room_messages',
    'public.room_members',
    'public.rooms',
    'public.audience_pool_votes',
    'public.audience_pool_options',
    'public.audience_pools',
    'public.time_capsule_members',
    'public.time_capsules',
    'public.double_take_answers',
    'public.double_takes',
    'public.crossed_path_requests',
    'public.crossed_paths',
    'public.live_streams',

    -- 5. Grow business & spiritual
    'public.business_reviews',
    'public.business_products',
    'public.business_profiles',
    'public.spiritual_intentions',
    'public.spiritual_gratitudes',

    -- 6. Parent feed content
    'public.posts',
    'public.stories',

    -- 7. Notifications, tokens & social graph
    'public.notifications',
    'public.user_device_tokens',
    'public.follows',
    'public.blocks',
    'public.user_restrictions',
    'public.vybe_matches',

    -- 8. Moderation & audit
    'public.reports',
    'public.verification_requests',
    'public.user_moderation_actions',
    'public.admin_audit_logs',
    'public.audit_logs',

    -- 9. Per-user preference rows (auth remains intact)
    'public.user_settings',
    'public.notification_preferences'
  ];
  tbl text;
BEGIN
  FOREACH tbl IN ARRAY target_tables LOOP
    IF to_regclass(tbl) IS NOT NULL THEN
      EXECUTE format('DELETE FROM %s;', tbl);
    END IF;
  END LOOP;
END $$;

-- Reset mutable profile media/counters while preserving user IDs, usernames & admin roles
UPDATE public.profiles
SET
  posts_count = 0,
  followers_count = 0,
  following_count = 0,
  avatar_url = NULL,
  cover_url = NULL,
  bio = NULL,
  is_verified = FALSE,
  verified_badge_type = NULL,
  updated_at = NOW();

-- Storage objects cleanup inside existing buckets (buckets & RLS policies remain intact)
-- Note: In Supabase Cloud managed storage, prefer emptying via Supabase Storage API or dashboard
-- if direct `storage.objects` DELETE triggers foreign-key / trigger hooks.
DELETE FROM storage.objects
WHERE bucket_id IN (
  'avatars',
  'banners',
  'posts',
  'stories',
  'raw_media',
  'chat-media',
  'business_docs'
);

COMMIT;
