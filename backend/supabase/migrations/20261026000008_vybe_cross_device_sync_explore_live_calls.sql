-- ============================================================================
-- VYBE BY NR NEXT — MIGRATION 000008
-- CROSS-DEVICE STORIES, EXPLORE ZONES, LIVE ZONE, CALLS & CHAT CALL HISTORY
-- ============================================================================
-- Verified against `20261020000001_vybe_master_schema.sql` and all subsequent
-- migrations so every statement is 100% idempotent and error-free.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 0. DEFENSIVE HELPER FUNCTIONS
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.has_block_between(p_user_a UUID, p_user_b UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT CASE
    WHEN p_user_a IS NULL OR p_user_b IS NULL THEN FALSE
    ELSE EXISTS (
      SELECT 1
      FROM public.blocks b
      WHERE (b.blocker_id = p_user_a AND b.blocked_id = p_user_b)
         OR (b.blocker_id = p_user_b AND b.blocked_id = p_user_a)
    )
  END;
$$;

GRANT EXECUTE ON FUNCTION public.has_block_between(UUID, UUID) TO anon, authenticated;

-- ----------------------------------------------------------------------------
-- 1. STORIES & STORY VIEWS: SCHEMA + TRIGGER + CROSS-DEVICE RLS
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.vybe_matches (
  user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  matched_user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (user_id, matched_user_id)
);

ALTER TABLE public.stories
  ADD COLUMN IF NOT EXISTS media_type VARCHAR(20) DEFAULT 'PHOTO',
  ADD COLUMN IF NOT EXISTS story_type VARCHAR(20) DEFAULT 'PHOTO',
  ADD COLUMN IF NOT EXISTS audience VARCHAR(30) DEFAULT 'EVERYONE',
  ADD COLUMN IF NOT EXISTS music_title VARCHAR(180),
  ADD COLUMN IF NOT EXISTS location_name VARCHAR(180),
  ADD COLUMN IF NOT EXISTS text_bg_key VARCHAR(60),
  ADD COLUMN IF NOT EXISTS storage_path TEXT,
  ADD COLUMN IF NOT EXISTS client_upload_id VARCHAR(120);

ALTER TABLE public.stories ALTER COLUMN story_type DROP NOT NULL;

CREATE OR REPLACE FUNCTION public.sync_story_media_type_columns()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
  IF NEW.media_type IS NULL OR btrim(NEW.media_type) = '' THEN
    NEW.media_type := COALESCE(NULLIF(btrim(NEW.story_type), ''), 'PHOTO');
  END IF;
  IF NEW.story_type IS NULL OR btrim(NEW.story_type) = '' THEN
    NEW.story_type := NEW.media_type;
  END IF;
  IF NEW.audience IS NULL OR btrim(NEW.audience) = '' THEN
    NEW.audience := 'EVERYONE';
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_sync_story_media_type ON public.stories;
CREATE TRIGGER trg_sync_story_media_type
  BEFORE INSERT OR UPDATE ON public.stories
  FOR EACH ROW
  EXECUTE FUNCTION public.sync_story_media_type_columns();

ALTER TABLE public.stories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.story_views ENABLE ROW LEVEL SECURITY;

DO $$
DECLARE
  pol RECORD;
BEGIN
  FOR pol IN
    SELECT policyname, tablename
    FROM pg_policies
    WHERE schemaname = 'public'
      AND tablename IN ('stories', 'story_views')
  LOOP
    EXECUTE format('DROP POLICY IF EXISTS %I ON public.%I', pol.policyname, pol.tablename);
  END LOOP;
END $$;

CREATE POLICY "stories_select_universal_active"
  ON public.stories
  FOR SELECT
  TO anon, authenticated
  USING (
    expires_at > NOW()
    AND (auth.uid() IS NULL OR NOT public.has_block_between(author_id, auth.uid()))
    AND (
      COALESCE(audience, 'EVERYONE') = 'EVERYONE'
      OR auth.uid() = author_id
      OR (
        auth.uid() IS NOT NULL
        AND EXISTS (
          SELECT 1 FROM public.vybe_matches vm
          WHERE vm.user_id = stories.author_id
            AND vm.matched_user_id = auth.uid()
        )
      )
    )
  );

CREATE POLICY "stories_insert_own"
  ON public.stories
  FOR INSERT
  TO authenticated
  WITH CHECK (auth.uid() = author_id);

CREATE POLICY "stories_update_own"
  ON public.stories
  FOR UPDATE
  TO authenticated
  USING (auth.uid() = author_id)
  WITH CHECK (auth.uid() = author_id);

CREATE POLICY "stories_delete_own"
  ON public.stories
  FOR DELETE
  TO authenticated
  USING (auth.uid() = author_id);

CREATE POLICY "story_views_select_own_or_author"
  ON public.story_views
  FOR SELECT
  TO authenticated
  USING (
    auth.uid() = viewer_id
    OR EXISTS (
      SELECT 1 FROM public.stories s
      WHERE s.id = story_views.story_id AND s.author_id = auth.uid()
    )
  );

CREATE POLICY "story_views_insert_own"
  ON public.story_views
  FOR INSERT
  TO authenticated
  WITH CHECK (auth.uid() = viewer_id);

CREATE POLICY "story_views_update_own"
  ON public.story_views
  FOR UPDATE
  TO authenticated
  USING (auth.uid() = viewer_id)
  WITH CHECK (auth.uid() = viewer_id);

-- ----------------------------------------------------------------------------
-- 2. LIVE ZONE (`live_streams` & `live_stream_comments`)
-- ----------------------------------------------------------------------------
ALTER TABLE public.live_streams
  ADD COLUMN IF NOT EXISTS category VARCHAR(80) DEFAULT 'Live Vibe',
  ADD COLUMN IF NOT EXISTS thumbnail_url TEXT,
  ADD COLUMN IF NOT EXISTS latest_frame_base64 TEXT,
  ADD COLUMN IF NOT EXISTS last_heartbeat_at TIMESTAMPTZ DEFAULT NOW();

CREATE TABLE IF NOT EXISTS public.live_stream_comments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  stream_id UUID NOT NULL REFERENCES public.live_streams(id) ON DELETE CASCADE,
  author_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  author_name VARCHAR(120) NOT NULL DEFAULT 'VYBE Member',
  content TEXT NOT NULL CHECK (char_length(btrim(content)) BETWEEN 1 AND 500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_live_streams_active ON public.live_streams (is_live, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_live_stream_comments_stream ON public.live_stream_comments (stream_id, created_at ASC);

ALTER TABLE public.live_streams ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.live_stream_comments ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "live_streams_select_all" ON public.live_streams;
DROP POLICY IF EXISTS "live_streams_insert_host" ON public.live_streams;
DROP POLICY IF EXISTS "live_streams_update_authenticated" ON public.live_streams;
DROP POLICY IF EXISTS "live_streams_delete_host" ON public.live_streams;

CREATE POLICY "live_streams_select_all"
  ON public.live_streams FOR SELECT TO anon, authenticated USING (true);

CREATE POLICY "live_streams_insert_host"
  ON public.live_streams FOR INSERT TO authenticated WITH CHECK (auth.uid() = host_id);

CREATE POLICY "live_streams_update_authenticated"
  ON public.live_streams FOR UPDATE TO authenticated USING (true) WITH CHECK (true);

CREATE POLICY "live_streams_delete_host"
  ON public.live_streams FOR DELETE TO authenticated USING (auth.uid() = host_id);

DROP POLICY IF EXISTS "live_stream_comments_select_all" ON public.live_stream_comments;
DROP POLICY IF EXISTS "live_stream_comments_insert_auth" ON public.live_stream_comments;

CREATE POLICY "live_stream_comments_select_all"
  ON public.live_stream_comments FOR SELECT TO anon, authenticated USING (true);

CREATE POLICY "live_stream_comments_insert_auth"
  ON public.live_stream_comments FOR INSERT TO authenticated WITH CHECK (auth.uid() = author_id);

-- ----------------------------------------------------------------------------
-- 3. EXPLORE ZONES: UNLOAD ROOMS, POLLS, DOUBLE TAKE, CAPSULES, CROSSED PATHS, SHOPS
-- ----------------------------------------------------------------------------

-- 3A. Unload Rooms (`rooms`, `room_members`, `room_messages`)
ALTER TABLE public.rooms
  ADD COLUMN IF NOT EXISTS room_code VARCHAR(32),
  ADD COLUMN IF NOT EXISTS category VARCHAR(100) DEFAULT 'Community Space',
  ADD COLUMN IF NOT EXISTS duration_minutes INTEGER DEFAULT 45,
  ADD COLUMN IF NOT EXISTS max_participants INTEGER DEFAULT 25,
  ADD COLUMN IF NOT EXISTS scheduled_time VARCHAR(80) DEFAULT 'Live Now',
  ADD COLUMN IF NOT EXISTS joiner_message TEXT DEFAULT 'Welcome to this VYBE listening space.',
  ADD COLUMN IF NOT EXISTS is_private BOOLEAN DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;

ALTER TABLE public.rooms ALTER COLUMN topic_tag DROP NOT NULL;
ALTER TABLE public.rooms ALTER COLUMN description DROP NOT NULL;

CREATE OR REPLACE FUNCTION public.sync_rooms_compat_columns()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
  IF NEW.topic_tag IS NULL OR btrim(NEW.topic_tag) = '' THEN
    NEW.topic_tag := LEFT(COALESCE(NULLIF(btrim(NEW.category), ''), 'Community'), 50);
  END IF;
  IF NEW.category IS NULL OR btrim(NEW.category) = '' THEN
    NEW.category := NEW.topic_tag;
  END IF;
  IF NEW.description IS NULL THEN
    NEW.description := '';
  END IF;
  IF NEW.capacity IS NULL THEN
    NEW.capacity := COALESCE(NEW.max_participants, 25);
  END IF;
  IF NEW.max_participants IS NULL THEN
    NEW.max_participants := COALESCE(NEW.capacity, 25);
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_sync_rooms_compat ON public.rooms;
CREATE TRIGGER trg_sync_rooms_compat
  BEFORE INSERT OR UPDATE ON public.rooms
  FOR EACH ROW
  EXECUTE FUNCTION public.sync_rooms_compat_columns();

ALTER TABLE public.room_members
  ADD COLUMN IF NOT EXISTS role VARCHAR(30) DEFAULT 'LISTENER';

ALTER TABLE public.room_messages
  ADD COLUMN IF NOT EXISTS sender_name VARCHAR(120) DEFAULT 'VYBE Member',
  ADD COLUMN IF NOT EXISTS content TEXT;

ALTER TABLE public.room_messages ALTER COLUMN text DROP NOT NULL;
ALTER TABLE public.room_messages ALTER COLUMN sender_alias DROP NOT NULL;

CREATE OR REPLACE FUNCTION public.sync_room_message_text_content()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
  IF NEW.content IS NULL OR btrim(NEW.content) = '' THEN
    NEW.content := COALESCE(NEW.text, '');
  END IF;
  IF NEW.text IS NULL OR btrim(NEW.text) = '' THEN
    NEW.text := NEW.content;
  END IF;
  IF NEW.sender_name IS NULL OR btrim(NEW.sender_name) = '' THEN
    NEW.sender_name := COALESCE(NULLIF(btrim(NEW.sender_alias), ''), 'VYBE Member');
  END IF;
  IF NEW.sender_alias IS NULL OR btrim(NEW.sender_alias) = '' THEN
    NEW.sender_alias := LEFT(NEW.sender_name, 50);
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_sync_room_message_content ON public.room_messages;
CREATE TRIGGER trg_sync_room_message_content
  BEFORE INSERT OR UPDATE ON public.room_messages
  FOR EACH ROW
  EXECUTE FUNCTION public.sync_room_message_text_content();

ALTER TABLE public.rooms ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.room_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.room_messages ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "rooms_select_all" ON public.rooms;
DROP POLICY IF EXISTS "rooms_insert_host" ON public.rooms;
DROP POLICY IF EXISTS "rooms_update_host" ON public.rooms;
DROP POLICY IF EXISTS "rooms_delete_host" ON public.rooms;
CREATE POLICY "rooms_select_all" ON public.rooms FOR SELECT TO anon, authenticated USING (true);
CREATE POLICY "rooms_insert_host" ON public.rooms FOR INSERT TO authenticated WITH CHECK (auth.uid() = host_id);
CREATE POLICY "rooms_update_host" ON public.rooms FOR UPDATE TO authenticated USING (auth.uid() = host_id);
CREATE POLICY "rooms_delete_host" ON public.rooms FOR DELETE TO authenticated USING (auth.uid() = host_id);

DROP POLICY IF EXISTS "room_members_select_all" ON public.room_members;
DROP POLICY IF EXISTS "room_members_insert_self" ON public.room_members;
DROP POLICY IF EXISTS "room_members_update_self" ON public.room_members;
DROP POLICY IF EXISTS "room_members_delete_self" ON public.room_members;
CREATE POLICY "room_members_select_all" ON public.room_members FOR SELECT TO anon, authenticated USING (true);
CREATE POLICY "room_members_insert_self" ON public.room_members FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY "room_members_update_self" ON public.room_members FOR UPDATE TO authenticated USING (auth.uid() = user_id);
CREATE POLICY "room_members_delete_self" ON public.room_members FOR DELETE TO authenticated USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "room_messages_select_all" ON public.room_messages;
DROP POLICY IF EXISTS "room_messages_insert_self" ON public.room_messages;
CREATE POLICY "room_messages_select_all" ON public.room_messages FOR SELECT TO anon, authenticated USING (true);
CREATE POLICY "room_messages_insert_self" ON public.room_messages FOR INSERT TO authenticated WITH CHECK (auth.uid() = sender_id);

-- 3B. What Next Polls (`audience_pools`, `audience_pool_options`, `audience_pool_votes`)
ALTER TABLE public.audience_pools
  ADD COLUMN IF NOT EXISTS duration_hours INTEGER DEFAULT 24,
  ADD COLUMN IF NOT EXISTS author_username VARCHAR(80);

ALTER TABLE public.audience_pools ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audience_pool_options ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audience_pool_votes ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "audience_pools_select_all" ON public.audience_pools;
DROP POLICY IF EXISTS "audience_pools_insert_creator" ON public.audience_pools;
DROP POLICY IF EXISTS "audience_pools_delete_creator" ON public.audience_pools;
CREATE POLICY "audience_pools_select_all" ON public.audience_pools FOR SELECT TO anon, authenticated USING (true);
CREATE POLICY "audience_pools_insert_creator" ON public.audience_pools FOR INSERT TO authenticated WITH CHECK (auth.uid() = creator_id);
CREATE POLICY "audience_pools_delete_creator" ON public.audience_pools FOR DELETE TO authenticated USING (auth.uid() = creator_id);

DROP POLICY IF EXISTS "audience_pool_options_select_all" ON public.audience_pool_options;
DROP POLICY IF EXISTS "audience_pool_options_insert_auth" ON public.audience_pool_options;
DROP POLICY IF EXISTS "audience_pool_options_update_auth" ON public.audience_pool_options;
CREATE POLICY "audience_pool_options_select_all" ON public.audience_pool_options FOR SELECT TO anon, authenticated USING (true);
CREATE POLICY "audience_pool_options_insert_auth" ON public.audience_pool_options FOR INSERT TO authenticated WITH CHECK (true);
CREATE POLICY "audience_pool_options_update_auth" ON public.audience_pool_options FOR UPDATE TO authenticated USING (true);

DROP POLICY IF EXISTS "audience_pool_votes_select_all" ON public.audience_pool_votes;
DROP POLICY IF EXISTS "audience_pool_votes_insert_voter" ON public.audience_pool_votes;
CREATE POLICY "audience_pool_votes_select_all" ON public.audience_pool_votes FOR SELECT TO anon, authenticated USING (true);
CREATE POLICY "audience_pool_votes_insert_voter" ON public.audience_pool_votes FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);

CREATE OR REPLACE FUNCTION public.sync_audience_pool_vote_count()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  UPDATE public.audience_pool_options
  SET votes_count = (
    SELECT COUNT(*)::INTEGER FROM public.audience_pool_votes v WHERE v.option_id = NEW.option_id
  )
  WHERE id = NEW.option_id;

  UPDATE public.audience_pools
  SET total_votes = (
    SELECT COUNT(*)::INTEGER FROM public.audience_pool_votes v WHERE v.pool_id = NEW.pool_id
  )
  WHERE id = NEW.pool_id;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_sync_audience_pool_vote_count ON public.audience_pool_votes;
CREATE TRIGGER trg_sync_audience_pool_vote_count
  AFTER INSERT ON public.audience_pool_votes
  FOR EACH ROW
  EXECUTE FUNCTION public.sync_audience_pool_vote_count();

-- 3C. Double Take (`double_takes`, `double_take_answers`)
ALTER TABLE public.double_takes ALTER COLUMN partner_id DROP NOT NULL;
ALTER TABLE public.double_takes
  ADD COLUMN IF NOT EXISTS option_a VARCHAR(160) DEFAULT 'Option A',
  ADD COLUMN IF NOT EXISTS option_b VARCHAR(160) DEFAULT 'Option B',
  ADD COLUMN IF NOT EXISTS is_public BOOLEAN DEFAULT TRUE,
  ADD COLUMN IF NOT EXISTS is_creator_accepted BOOLEAN DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS is_chat_unlocked BOOLEAN DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS public.double_take_answers (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  prompt_id UUID NOT NULL REFERENCES public.double_takes(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  answer_text TEXT NOT NULL,
  is_connected BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (prompt_id, user_id)
);

ALTER TABLE public.double_takes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.double_take_answers ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "double_takes_select_visible" ON public.double_takes;
DROP POLICY IF EXISTS "double_takes_insert_creator" ON public.double_takes;
DROP POLICY IF EXISTS "double_takes_update_participants" ON public.double_takes;
DROP POLICY IF EXISTS "double_takes_delete_creator" ON public.double_takes;

CREATE POLICY "double_takes_select_visible"
  ON public.double_takes FOR SELECT TO anon, authenticated
  USING (
    COALESCE(is_public, true) = true
    OR auth.uid() = creator_id
    OR auth.uid() = partner_id
  );

CREATE POLICY "double_takes_insert_creator"
  ON public.double_takes FOR INSERT TO authenticated WITH CHECK (auth.uid() = creator_id);

CREATE POLICY "double_takes_update_participants"
  ON public.double_takes FOR UPDATE TO authenticated
  USING (auth.uid() = creator_id OR auth.uid() = partner_id OR COALESCE(is_public, true) = true);

CREATE POLICY "double_takes_delete_creator"
  ON public.double_takes FOR DELETE TO authenticated USING (auth.uid() = creator_id);

DROP POLICY IF EXISTS "double_take_answers_select_all" ON public.double_take_answers;
DROP POLICY IF EXISTS "double_take_answers_insert_own" ON public.double_take_answers;
DROP POLICY IF EXISTS "double_take_answers_update_auth" ON public.double_take_answers;
CREATE POLICY "double_take_answers_select_all" ON public.double_take_answers FOR SELECT TO anon, authenticated USING (true);
CREATE POLICY "double_take_answers_insert_own" ON public.double_take_answers FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY "double_take_answers_update_auth" ON public.double_take_answers FOR UPDATE TO authenticated USING (true);

-- 3D. Time Capsules (`time_capsules`, `time_capsule_members`)
ALTER TABLE public.time_capsules
  ADD COLUMN IF NOT EXISTS category_theme VARCHAR(100) DEFAULT 'Future Goal',
  ADD COLUMN IF NOT EXISTS media_type VARCHAR(20) DEFAULT 'NONE',
  ADD COLUMN IF NOT EXISTS unlock_date_label VARCHAR(80),
  ADD COLUMN IF NOT EXISTS is_public_vault BOOLEAN DEFAULT TRUE;

ALTER TABLE public.time_capsules ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.time_capsule_members ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "time_capsules_select_visible" ON public.time_capsules;
DROP POLICY IF EXISTS "time_capsules_insert_creator" ON public.time_capsules;
DROP POLICY IF EXISTS "time_capsules_update_creator" ON public.time_capsules;
CREATE POLICY "time_capsules_select_visible"
  ON public.time_capsules FOR SELECT TO anon, authenticated
  USING (
    COALESCE(is_public_vault, true) = true
    OR auth.uid() = creator_id
    OR EXISTS (
      SELECT 1 FROM public.time_capsule_members m
      WHERE m.capsule_id = time_capsules.id AND m.user_id = auth.uid()
    )
  );
CREATE POLICY "time_capsules_insert_creator"
  ON public.time_capsules FOR INSERT TO authenticated WITH CHECK (auth.uid() = creator_id);
CREATE POLICY "time_capsules_update_creator"
  ON public.time_capsules FOR UPDATE TO authenticated USING (auth.uid() = creator_id OR COALESCE(is_public_vault, true) = true);

DROP POLICY IF EXISTS "time_capsule_members_select_all" ON public.time_capsule_members;
DROP POLICY IF EXISTS "time_capsule_members_insert_auth" ON public.time_capsule_members;
CREATE POLICY "time_capsule_members_select_all" ON public.time_capsule_members FOR SELECT TO authenticated USING (true);
CREATE POLICY "time_capsule_members_insert_auth" ON public.time_capsule_members FOR INSERT TO authenticated WITH CHECK (true);

-- 3E. Crossed Paths (`crossed_paths`, `crossed_path_requests`)
ALTER TABLE public.crossed_paths ALTER COLUMN neighborhood_or_venue DROP NOT NULL;
ALTER TABLE public.crossed_paths ALTER COLUMN time_window DROP NOT NULL;

ALTER TABLE public.crossed_paths
  ADD COLUMN IF NOT EXISTS encounter_type VARCHAR(40) DEFAULT 'MISSED_CONNECTION',
  ADD COLUMN IF NOT EXISTS title VARCHAR(200) DEFAULT 'Crossed Paths Moment',
  ADD COLUMN IF NOT EXISTS broad_location VARCHAR(200) DEFAULT '',
  ADD COLUMN IF NOT EXISTS venue_name VARCHAR(200),
  ADD COLUMN IF NOT EXISTS is_anonymous BOOLEAN DEFAULT TRUE;

ALTER TABLE public.crossed_paths ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.crossed_path_requests ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "crossed_paths_select_active" ON public.crossed_paths;
DROP POLICY IF EXISTS "crossed_paths_insert_own" ON public.crossed_paths;
DROP POLICY IF EXISTS "crossed_paths_delete_own" ON public.crossed_paths;
CREATE POLICY "crossed_paths_select_active"
  ON public.crossed_paths FOR SELECT TO anon, authenticated
  USING (COALESCE(expires_at, NOW() + INTERVAL '1 hour') > NOW());
CREATE POLICY "crossed_paths_insert_own"
  ON public.crossed_paths FOR INSERT TO authenticated WITH CHECK (auth.uid() = creator_id);
CREATE POLICY "crossed_paths_delete_own"
  ON public.crossed_paths FOR DELETE TO authenticated USING (auth.uid() = creator_id);

DROP POLICY IF EXISTS "crossed_path_requests_select_auth" ON public.crossed_path_requests;
DROP POLICY IF EXISTS "crossed_path_requests_insert_own" ON public.crossed_path_requests;
CREATE POLICY "crossed_path_requests_select_auth" ON public.crossed_path_requests FOR SELECT TO authenticated USING (true);
CREATE POLICY "crossed_path_requests_insert_own" ON public.crossed_path_requests FOR INSERT TO authenticated WITH CHECK (auth.uid() = sender_id);

-- 3F. Business Profiles & Business Reviews (`business_profiles`, `business_reviews`)
ALTER TABLE public.business_profiles
  ADD COLUMN IF NOT EXISTS opening_hours VARCHAR(100) DEFAULT '10:00 AM - 9:00 PM',
  ADD COLUMN IF NOT EXISTS contact_phone VARCHAR(40) DEFAULT '',
  ADD COLUMN IF NOT EXISTS contact_email VARCHAR(160) DEFAULT '',
  ADD COLUMN IF NOT EXISTS is_shop_active BOOLEAN DEFAULT TRUE;

ALTER TABLE public.business_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.business_reviews ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.business_products ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "business_profiles_select_all" ON public.business_profiles;
DROP POLICY IF EXISTS "business_profiles_insert_owner" ON public.business_profiles;
DROP POLICY IF EXISTS "business_profiles_update_owner" ON public.business_profiles;
CREATE POLICY "business_profiles_select_all" ON public.business_profiles FOR SELECT TO anon, authenticated USING (true);
CREATE POLICY "business_profiles_insert_owner" ON public.business_profiles FOR INSERT TO authenticated WITH CHECK (auth.uid() = owner_id);
CREATE POLICY "business_profiles_update_owner" ON public.business_profiles FOR UPDATE TO authenticated USING (auth.uid() = owner_id);

DROP POLICY IF EXISTS "business_products_select_all" ON public.business_products;
CREATE POLICY "business_products_select_all" ON public.business_products FOR SELECT TO anon, authenticated USING (true);

DROP POLICY IF EXISTS "business_reviews_select_all" ON public.business_reviews;
DROP POLICY IF EXISTS "business_reviews_insert_author" ON public.business_reviews;
CREATE POLICY "business_reviews_select_all" ON public.business_reviews FOR SELECT TO anon, authenticated USING (true);
CREATE POLICY "business_reviews_insert_author" ON public.business_reviews FOR INSERT TO authenticated WITH CHECK (auth.uid() = author_id);

-- ----------------------------------------------------------------------------
-- 4. VOICE & VIDEO CALLS (`call_sessions`, `call_signals`) & CHAT CALL HISTORY
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.call_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  caller_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  callee_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  conversation_id UUID REFERENCES public.conversations(id) ON DELETE SET NULL,
  is_video BOOLEAN NOT NULL DEFAULT FALSE,
  status VARCHAR(30) NOT NULL DEFAULT 'RINGING',
  offer_sdp TEXT,
  answer_sdp TEXT,
  started_at TIMESTAMPTZ,
  ended_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.call_signals (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  call_id UUID NOT NULL REFERENCES public.call_sessions(id) ON DELETE CASCADE,
  sender_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  signal_type VARCHAR(40) NOT NULL,
  payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_call_signals_call_created ON public.call_signals (call_id, created_at ASC);

ALTER TABLE public.call_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.call_signals ENABLE ROW LEVEL SECURITY;

DO $$
DECLARE
  pol RECORD;
BEGIN
  FOR pol IN
    SELECT policyname, tablename
    FROM pg_policies
    WHERE schemaname = 'public'
      AND tablename IN ('call_sessions', 'call_signals')
  LOOP
    EXECUTE format('DROP POLICY IF EXISTS %I ON public.%I', pol.policyname, pol.tablename);
  END LOOP;
END $$;

CREATE POLICY "call_sessions_select_participants"
  ON public.call_sessions FOR SELECT TO authenticated
  USING (auth.uid() = caller_id OR auth.uid() = callee_id);

CREATE POLICY "call_sessions_insert_caller"
  ON public.call_sessions FOR INSERT TO authenticated
  WITH CHECK (auth.uid() = caller_id);

CREATE POLICY "call_sessions_update_participants"
  ON public.call_sessions FOR UPDATE TO authenticated
  USING (auth.uid() = caller_id OR auth.uid() = callee_id)
  WITH CHECK (auth.uid() = caller_id OR auth.uid() = callee_id);

CREATE POLICY "call_signals_select_participants"
  ON public.call_signals FOR SELECT TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.call_sessions cs
      WHERE cs.id = call_signals.call_id
        AND (cs.caller_id = auth.uid() OR cs.callee_id = auth.uid())
    )
  );

CREATE POLICY "call_signals_insert_participants"
  ON public.call_signals FOR INSERT TO authenticated
  WITH CHECK (
    auth.uid() = sender_id
    AND EXISTS (
      SELECT 1 FROM public.call_sessions cs
      WHERE cs.id = call_signals.call_id
        AND (cs.caller_id = auth.uid() OR cs.callee_id = auth.uid())
    )
  );

CREATE POLICY "call_signals_delete_participants"
  ON public.call_signals FOR DELETE TO authenticated
  USING (auth.uid() = sender_id);

-- Ensure `messages.media_type` supports `CALL_LOG`
ALTER TABLE public.messages
  ALTER COLUMN media_type TYPE VARCHAR(40);

-- ----------------------------------------------------------------------------
-- 5. REALTIME PUBLICATION REGISTRATION FOR CROSS-DEVICE LIVE SYNC
-- ----------------------------------------------------------------------------
DO $$
DECLARE
  tbl TEXT;
BEGIN
  FOREACH tbl IN ARRAY ARRAY[
    'posts',
    'post_media',
    'comments',
    'stories',
    'rooms',
    'room_messages',
    'live_streams',
    'live_stream_comments',
    'audience_pools',
    'audience_pool_votes',
    'double_takes',
    'double_take_answers',
    'time_capsules',
    'crossed_paths',
    'business_profiles',
    'call_sessions',
    'call_signals',
    'messages'
  ]
  LOOP
    IF NOT EXISTS (
      SELECT 1
      FROM pg_publication_tables
      WHERE pubname = 'supabase_realtime'
        AND schemaname = 'public'
        AND tablename = tbl
    ) THEN
      EXECUTE format('ALTER PUBLICATION supabase_realtime ADD TABLE public.%I', tbl);
    END IF;
  END LOOP;
END $$;
