-- ============================================================================
-- VYBE INCREMENTAL SAFE REPAIR, AUTH UPGRADE, PERSISTENT ROOMS & CALL SIGNALING
-- Migration: 20261024000005_vybe_safe_repair_auth_rooms_calls.sql
-- Standalone & Idempotent: Safe to run on top of 0001 (with or without 0004).
-- Preserves all existing tables, users, posts, messages, and RLS policies.
-- ============================================================================

-- 0. ENSURE ENUM VALUES EXIST SAFELY (IF ENUMS ARE PRESENT)
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_type WHERE typname = 'mood_type_enum') THEN
    ALTER TYPE public.mood_type_enum ADD VALUE IF NOT EXISTS 'GENERAL';
    ALTER TYPE public.mood_type_enum ADD VALUE IF NOT EXISTS 'MOTIVATIONAL';
    ALTER TYPE public.mood_type_enum ADD VALUE IF NOT EXISTS 'EMOTIONAL';
    ALTER TYPE public.mood_type_enum ADD VALUE IF NOT EXISTS 'SPIRITUAL';
  END IF;

  IF EXISTS (SELECT 1 FROM pg_type WHERE typname = 'live_pulse_enum') THEN
    ALTER TYPE public.live_pulse_enum ADD VALUE IF NOT EXISTS 'FEELING_CREATIVE';
    ALTER TYPE public.live_pulse_enum ADD VALUE IF NOT EXISTS 'LISTENING_MUSIC';
    ALTER TYPE public.live_pulse_enum ADD VALUE IF NOT EXISTS 'GAMING_PARTNER';
    ALTER TYPE public.live_pulse_enum ADD VALUE IF NOT EXISTS 'QUIET_TIME';
  END IF;
END $$;

-- 1. ENSURE CORE SUPPORTING TABLES & COLUMNS EXIST SAFELY
ALTER TABLE public.profiles
  ADD COLUMN IF NOT EXISTS is_banned BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS is_deactivated BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS is_private_account BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS message_privacy VARCHAR(30) NOT NULL DEFAULT 'EVERYONE',
  ADD COLUMN IF NOT EXISTS story_privacy VARCHAR(30) NOT NULL DEFAULT 'EVERYONE',
  ADD COLUMN IF NOT EXISTS is_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS is_phone_verified BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  ADD COLUMN IF NOT EXISTS live_pulse TEXT NOT NULL DEFAULT 'NONE';

CREATE TABLE IF NOT EXISTS public.blocks (
  blocker_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  blocked_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  PRIMARY KEY (blocker_id, blocked_id)
);
ALTER TABLE public.blocks ENABLE ROW LEVEL SECURITY;

ALTER TABLE public.follows
  ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACCEPTED';

ALTER TABLE public.posts
  ADD COLUMN IF NOT EXISTS is_archived BOOLEAN DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS is_unfiltered BOOLEAN DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS is_vybes_reel BOOLEAN DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS views_count INT DEFAULT 0,
  ADD COLUMN IF NOT EXISTS location_name TEXT,
  ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS music_title TEXT,
  ADD COLUMN IF NOT EXISTS client_upload_id VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS idx_posts_client_upload_id
  ON public.posts (client_upload_id)
  WHERE client_upload_id IS NOT NULL;

ALTER TABLE public.stories
  ADD COLUMN IF NOT EXISTS media_type VARCHAR(20) DEFAULT 'PHOTO',
  ADD COLUMN IF NOT EXISTS audience VARCHAR(30) NOT NULL DEFAULT 'EVERYONE',
  ADD COLUMN IF NOT EXISTS music_title TEXT,
  ADD COLUMN IF NOT EXISTS location_name TEXT,
  ADD COLUMN IF NOT EXISTS text_bg_key VARCHAR(40),
  ADD COLUMN IF NOT EXISTS storage_path TEXT,
  ADD COLUMN IF NOT EXISTS client_upload_id VARCHAR(100),
  ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ DEFAULT (NOW() + INTERVAL '24 hours');

CREATE TABLE IF NOT EXISTS public.story_views (
  story_id UUID NOT NULL REFERENCES public.stories(id) ON DELETE CASCADE,
  viewer_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  viewed_at TIMESTAMPTZ DEFAULT NOW(),
  PRIMARY KEY (story_id, viewer_id)
);
ALTER TABLE public.story_views ENABLE ROW LEVEL SECURITY;

CREATE TABLE IF NOT EXISTS public.vybe_matches (
  user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  matched_user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  PRIMARY KEY (user_id, matched_user_id),
  CONSTRAINT no_self_vybe_match CHECK (user_id <> matched_user_id)
);
ALTER TABLE public.vybe_matches ENABLE ROW LEVEL SECURITY;

-- 2. STANDALONE SECURITY HELPER FUNCTIONS (RESOLVES 42883 MISSING FUNCTION ERRORS)
CREATE OR REPLACE FUNCTION public.is_blocked_between(p_user_a UUID, p_user_b UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT CASE
    WHEN p_user_a IS NULL OR p_user_b IS NULL THEN FALSE
    ELSE EXISTS (
      SELECT 1 FROM public.blocks
      WHERE (blocker_id = p_user_a AND blocked_id = p_user_b)
         OR (blocker_id = p_user_b AND blocked_id = p_user_a)
    )
  END;
$$;

CREATE OR REPLACE FUNCTION public.has_block_between(p_user_a UUID, p_user_b UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT public.is_blocked_between(p_user_a, p_user_b);
$$;

CREATE OR REPLACE FUNCTION public.is_admin(p_user_id UUID DEFAULT auth.uid())
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT CASE
    WHEN p_user_id IS NULL THEN FALSE
    ELSE EXISTS (
      SELECT 1 FROM public.profiles
      WHERE id = p_user_id AND role::TEXT IN ('ADMIN', 'MODERATOR')
    )
  END;
$$;

CREATE OR REPLACE FUNCTION public.can_view_author_content(p_viewer_id UUID, p_author_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    CASE
      WHEN p_author_id IS NULL THEN FALSE
      WHEN p_viewer_id IS NOT NULL AND p_viewer_id = p_author_id THEN TRUE
      WHEN p_viewer_id IS NOT NULL AND public.is_blocked_between(p_viewer_id, p_author_id) THEN FALSE
      WHEN EXISTS (
        SELECT 1 FROM public.profiles pr
        WHERE pr.id = p_author_id
          AND COALESCE(pr.is_private_account, FALSE) = FALSE
          AND COALESCE(pr.is_banned, FALSE) = FALSE
          AND COALESCE(pr.is_deactivated, FALSE) = FALSE
      ) THEN TRUE
      WHEN p_viewer_id IS NOT NULL AND EXISTS (
        SELECT 1 FROM public.follows f
        WHERE f.follower_id = p_viewer_id
          AND f.followed_id = p_author_id
          AND COALESCE(f.status, 'ACCEPTED') = 'ACCEPTED'
      ) THEN TRUE
      ELSE FALSE
    END;
$$;

CREATE OR REPLACE FUNCTION public.can_view_profile_content(
  p_author_id UUID,
  p_viewer_id UUID DEFAULT auth.uid()
)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT public.can_view_author_content(p_viewer_id, p_author_id);
$$;

-- 3. PROFILE NULL-USERNAME REPAIR & AUTH TRIGGERS
UPDATE public.profiles
SET username = 'vybe_' || SUBSTRING(REPLACE(id::TEXT, '-', '') FROM 1 FOR 8)
WHERE username IS NULL OR BTRIM(username) = '' OR LOWER(BTRIM(username)) = 'null';

UPDATE public.profiles
SET display_name = COALESCE(
  NULLIF(BTRIM(COALESCE(first_name, '') || ' ' || COALESCE(last_name, '')), ''),
  username
)
WHERE display_name IS NULL OR BTRIM(display_name) = '' OR LOWER(BTRIM(display_name)) = 'null';

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'chk_profiles_username_valid'
  ) THEN
    ALTER TABLE public.profiles
      ADD CONSTRAINT chk_profiles_username_valid
      CHECK (BTRIM(username) <> '' AND LOWER(BTRIM(username)) <> 'null' AND CHAR_LENGTH(BTRIM(username)) >= 3);
  END IF;
END $$;

CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_raw_username TEXT;
  v_clean_username TEXT;
  v_first_name TEXT;
  v_last_name TEXT;
  v_display_name TEXT;
  v_dob DATE;
  v_age INT;
BEGIN
  v_first_name := COALESCE(NULLIF(BTRIM(NEW.raw_user_meta_data->>'first_name'), ''), '');
  v_last_name := COALESCE(NULLIF(BTRIM(NEW.raw_user_meta_data->>'last_name'), ''), '');
  v_raw_username := LOWER(REGEXP_REPLACE(
    COALESCE(
      NULLIF(BTRIM(NEW.raw_user_meta_data->>'username'), ''),
      SPLIT_PART(COALESCE(NEW.email, ''), '@', 1),
      'vybe_' || SUBSTRING(REPLACE(NEW.id::TEXT, '-', '') FROM 1 FOR 8)
    ),
    '[^a-z0-9_.]', '_', 'g'
  ));
  IF CHAR_LENGTH(v_raw_username) < 3 THEN
    v_raw_username := 'vybe_' || SUBSTRING(REPLACE(NEW.id::TEXT, '-', '') FROM 1 FOR 8);
  END IF;

  IF EXISTS (SELECT 1 FROM public.profiles WHERE LOWER(username) = v_raw_username AND id <> NEW.id) THEN
    v_clean_username := v_raw_username || '_' || SUBSTRING(REPLACE(NEW.id::TEXT, '-', '') FROM 1 FOR 4);
  ELSE
    v_clean_username := v_raw_username;
  END IF;

  v_display_name := COALESCE(
    NULLIF(BTRIM(NEW.raw_user_meta_data->>'display_name'), ''),
    NULLIF(BTRIM(v_first_name || ' ' || v_last_name), ''),
    v_clean_username
  );

  BEGIN
    v_dob := NULLIF(BTRIM(NEW.raw_user_meta_data->>'date_of_birth'), '')::DATE;
  EXCEPTION WHEN OTHERS THEN
    v_dob := NULL;
  END;

  IF v_dob IS NOT NULL THEN
    v_age := EXTRACT(YEAR FROM AGE(CURRENT_DATE, v_dob))::INT;
  ELSE
    v_age := NULL;
  END IF;

  INSERT INTO public.profiles (
    id, username, display_name, first_name, last_name, email, phone,
    date_of_birth, age, is_under_18, is_email_verified, is_phone_verified, last_seen_at
  )
  VALUES (
    NEW.id,
    v_clean_username,
    v_display_name,
    NULLIF(v_first_name, ''),
    NULLIF(v_last_name, ''),
    NEW.email,
    COALESCE(NULLIF(BTRIM(NEW.raw_user_meta_data->>'phone'), ''), NEW.phone),
    v_dob,
    v_age,
    COALESCE(v_age < 18, FALSE),
    (NEW.email_confirmed_at IS NOT NULL),
    (NEW.phone_confirmed_at IS NOT NULL),
    NOW()
  )
  ON CONFLICT (id) DO UPDATE SET
    email = COALESCE(EXCLUDED.email, public.profiles.email),
    phone = COALESCE(EXCLUDED.phone, public.profiles.phone),
    first_name = COALESCE(EXCLUDED.first_name, public.profiles.first_name),
    last_name = COALESCE(EXCLUDED.last_name, public.profiles.last_name),
    date_of_birth = COALESCE(EXCLUDED.date_of_birth, public.profiles.date_of_birth),
    age = COALESCE(EXCLUDED.age, public.profiles.age),
    is_email_verified = public.profiles.is_email_verified OR EXCLUDED.is_email_verified,
    is_phone_verified = public.profiles.is_phone_verified OR EXCLUDED.is_phone_verified,
    updated_at = NOW();

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

CREATE OR REPLACE FUNCTION public.handle_auth_user_verified()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  UPDATE public.profiles
  SET
    is_email_verified = (NEW.email_confirmed_at IS NOT NULL) OR is_email_verified,
    is_phone_verified = (NEW.phone_confirmed_at IS NOT NULL) OR is_phone_verified,
    email = COALESCE(NEW.email, email),
    phone = COALESCE(NEW.phone, phone),
    updated_at = NOW()
  WHERE id = NEW.id;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_verified ON auth.users;
CREATE TRIGGER on_auth_user_verified
  AFTER UPDATE OF email_confirmed_at, phone_confirmed_at, email, phone ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.handle_auth_user_verified();

-- 4. SECURE LOGIN IDENTIFIER & USERNAME AVAILABILITY RPCS
CREATE OR REPLACE FUNCTION public.resolve_login_identifier(p_identifier TEXT)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_raw TEXT := BTRIM(COALESCE(p_identifier, ''));
  v_clean TEXT := LOWER(LTRIM(v_raw, '@'));
  v_digits TEXT := REGEXP_REPLACE(v_raw, '[^0-9]', '', 'g');
  v_email TEXT;
  v_phone TEXT;
BEGIN
  IF v_raw = '' THEN
    RETURN jsonb_build_object('found', FALSE);
  END IF;

  IF POSITION('@' IN v_raw) > 1 THEN
    RETURN jsonb_build_object('found', TRUE, 'mode', 'email', 'email', LOWER(v_raw));
  END IF;

  SELECT pr.email, pr.phone
  INTO v_email, v_phone
  FROM public.profiles pr
  WHERE COALESCE(pr.is_banned, FALSE) = FALSE
    AND COALESCE(pr.is_deactivated, FALSE) = FALSE
    AND (
      LOWER(pr.username) = v_clean
      OR LOWER('vybe-' || UPPER(REPLACE(RIGHT(pr.id::TEXT, 8), '_', 'K'))) = v_clean
      OR (LENGTH(v_digits) >= 7 AND REGEXP_REPLACE(COALESCE(pr.phone, ''), '[^0-9]', '', 'g') LIKE '%' || RIGHT(v_digits, 10))
    )
  LIMIT 1;

  IF v_email IS NOT NULL AND BTRIM(v_email) <> '' THEN
    RETURN jsonb_build_object('found', TRUE, 'mode', 'email', 'email', v_email);
  ELSIF v_phone IS NOT NULL AND BTRIM(v_phone) <> '' THEN
    RETURN jsonb_build_object('found', TRUE, 'mode', 'phone', 'phone', v_phone);
  END IF;

  RETURN jsonb_build_object('found', FALSE);
END;
$$;

GRANT EXECUTE ON FUNCTION public.resolve_login_identifier(TEXT) TO anon, authenticated;

CREATE OR REPLACE FUNCTION public.check_username_available(p_username TEXT)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT NOT EXISTS (
    SELECT 1
    FROM public.profiles
    WHERE LOWER(username) = LOWER(LTRIM(BTRIM(COALESCE(p_username, '')), '@'))
  );
$$;

GRANT EXECUTE ON FUNCTION public.check_username_available(TEXT) TO anon, authenticated;

-- 5. ACCURATE POSTS_COUNT TRIGGER & RECONCILIATION
CREATE OR REPLACE FUNCTION public.handle_post_count_change()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_target_author UUID;
BEGIN
  v_target_author := COALESCE(NEW.author_id, OLD.author_id);
  IF v_target_author IS NOT NULL THEN
    UPDATE public.profiles pr
    SET posts_count = (
      SELECT COUNT(*)::INT
      FROM public.posts po
      WHERE po.author_id = v_target_author
        AND COALESCE(po.is_archived, FALSE) = FALSE
    )
    WHERE pr.id = v_target_author;
  END IF;
  RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_post_count_change ON public.posts;
CREATE TRIGGER trg_post_count_change
  AFTER INSERT OR DELETE OR UPDATE OF is_archived ON public.posts
  FOR EACH ROW EXECUTE FUNCTION public.handle_post_count_change();

UPDATE public.profiles pr
SET posts_count = (
  SELECT COUNT(*)::INT
  FROM public.posts po
  WHERE po.author_id = pr.id
    AND COALESCE(po.is_archived, FALSE) = FALSE
);

-- 6. STORY VISIBILITY FIX FOR PUBLIC & VYBE_MATCHES STORIES
CREATE OR REPLACE FUNCTION public.can_view_story(
  p_author_id UUID,
  p_audience TEXT,
  p_expires_at TIMESTAMPTZ,
  p_viewer_id UUID DEFAULT auth.uid()
)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    p_expires_at > NOW()
    AND (p_viewer_id IS NULL OR NOT public.has_block_between(p_author_id, p_viewer_id))
    AND (
      (p_viewer_id IS NOT NULL AND p_author_id = p_viewer_id)
      OR (
        COALESCE(p_audience, 'EVERYONE') IN ('EVERYONE', 'PUBLIC')
        AND public.can_view_profile_content(p_author_id, p_viewer_id)
      )
      OR (
        p_viewer_id IS NOT NULL
        AND COALESCE(p_audience, 'EVERYONE') IN ('VYBE_MATCHES', 'CLOSE_FRIENDS')
        AND EXISTS (
          SELECT 1
          FROM public.vybe_matches vm
          WHERE vm.user_id = p_author_id
            AND vm.matched_user_id = p_viewer_id
        )
      )
    );
$$;

DROP POLICY IF EXISTS "Stories viewable by allowed audience" ON public.stories;
DROP POLICY IF EXISTS "stories_select_policy" ON public.stories;
CREATE POLICY "stories_select_policy"
  ON public.stories FOR SELECT
  USING (public.can_view_story(author_id, audience, expires_at, auth.uid()));

DROP POLICY IF EXISTS "stories_insert_policy" ON public.stories;
CREATE POLICY "stories_insert_policy"
  ON public.stories FOR INSERT
  WITH CHECK (auth.uid() = author_id);

DROP POLICY IF EXISTS "stories_delete_policy" ON public.stories;
CREATE POLICY "stories_delete_policy"
  ON public.stories FOR DELETE
  USING (auth.uid() = author_id);

-- 7. PERSISTENT LIVE ROOMS WITH UNIQUE ROOM_CODE & ANDROID/WEB SCHEMA COMPATIBILITY
ALTER TABLE public.rooms
  ADD COLUMN IF NOT EXISTS room_code TEXT,
  ADD COLUMN IF NOT EXISTS category TEXT,
  ADD COLUMN IF NOT EXISTS topic_tag VARCHAR(80) DEFAULT 'Community Space',
  ADD COLUMN IF NOT EXISTS duration_minutes INT NOT NULL DEFAULT 45,
  ADD COLUMN IF NOT EXISTS max_participants INT NOT NULL DEFAULT 25,
  ADD COLUMN IF NOT EXISTS capacity INT NOT NULL DEFAULT 25,
  ADD COLUMN IF NOT EXISTS is_private BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS joiner_message TEXT DEFAULT 'Welcome to this listening space.',
  ADD COLUMN IF NOT EXISTS scheduled_time TEXT DEFAULT 'Live Now',
  ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ DEFAULT (NOW() + INTERVAL '24 hours');

-- Make legacy rooms columns safely nullable/defaulted so both Android & Web DTOs insert cleanly
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'rooms' AND column_name = 'topic_tag'
  ) THEN
    ALTER TABLE public.rooms ALTER COLUMN topic_tag SET DEFAULT 'Community Space';
    ALTER TABLE public.rooms ALTER COLUMN topic_tag DROP NOT NULL;
  END IF;
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'rooms' AND column_name = 'description'
  ) THEN
    ALTER TABLE public.rooms ALTER COLUMN description SET DEFAULT '';
    ALTER TABLE public.rooms ALTER COLUMN description DROP NOT NULL;
  END IF;
END $$;

CREATE OR REPLACE FUNCTION public.sync_room_columns()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.room_code IS NULL OR BTRIM(NEW.room_code) = '' THEN
    NEW.room_code := 'VYB-' || UPPER(SUBSTRING(REPLACE(COALESCE(NEW.id, gen_random_uuid())::TEXT, '-', '') FROM 1 FOR 4))
                     || '-' || UPPER(SUBSTRING(REPLACE(COALESCE(NEW.id, gen_random_uuid())::TEXT, '-', '') FROM 5 FOR 4));
  END IF;
  NEW.category := COALESCE(NULLIF(BTRIM(NEW.category), ''), NULLIF(BTRIM(NEW.topic_tag), ''), 'Community Space');
  NEW.topic_tag := COALESCE(NULLIF(BTRIM(NEW.topic_tag), ''), NEW.category);
  NEW.max_participants := COALESCE(NEW.max_participants, NEW.capacity, 25);
  NEW.capacity := COALESCE(NEW.capacity, NEW.max_participants, 25);
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_sync_room_columns ON public.rooms;
CREATE TRIGGER trg_sync_room_columns
  BEFORE INSERT OR UPDATE ON public.rooms
  FOR EACH ROW EXECUTE FUNCTION public.sync_room_columns();

UPDATE public.rooms
SET
  room_code = COALESCE(NULLIF(BTRIM(room_code), ''), 'VYB-' || UPPER(SUBSTRING(REPLACE(id::TEXT, '-', '') FROM 1 FOR 4)) || '-' || UPPER(SUBSTRING(REPLACE(id::TEXT, '-', '') FROM 5 FOR 4))),
  category = COALESCE(category, topic_tag, 'Community Space')
WHERE room_code IS NULL OR BTRIM(room_code) = '' OR category IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_rooms_room_code_unique ON public.rooms(room_code);
CREATE INDEX IF NOT EXISTS idx_rooms_active_created ON public.rooms(is_active, created_at DESC);

ALTER TABLE public.room_members
  ADD COLUMN IF NOT EXISTS role VARCHAR(30) DEFAULT 'LISTENER';

ALTER TABLE public.room_messages
  ADD COLUMN IF NOT EXISTS content TEXT,
  ADD COLUMN IF NOT EXISTS text TEXT,
  ADD COLUMN IF NOT EXISTS sender_name TEXT,
  ADD COLUMN IF NOT EXISTS sender_alias VARCHAR(80) DEFAULT 'VYBE Member',
  ADD COLUMN IF NOT EXISTS is_anonymous BOOLEAN NOT NULL DEFAULT FALSE;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'room_messages' AND column_name = 'text'
  ) THEN
    ALTER TABLE public.room_messages ALTER COLUMN text DROP NOT NULL;
  END IF;
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'room_messages' AND column_name = 'sender_alias'
  ) THEN
    ALTER TABLE public.room_messages ALTER COLUMN sender_alias DROP NOT NULL;
  END IF;
END $$;

CREATE OR REPLACE FUNCTION public.sync_room_message_columns()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
  NEW.content := COALESCE(NULLIF(NEW.content, ''), NEW.text, '');
  NEW.text := COALESCE(NULLIF(NEW.text, ''), NEW.content, '');
  NEW.sender_name := COALESCE(NULLIF(NEW.sender_name, ''), NEW.sender_alias, 'VYBE Member');
  NEW.sender_alias := LEFT(COALESCE(NULLIF(NEW.sender_alias, ''), NEW.sender_name, 'VYBE Member'), 50);
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_sync_room_message_columns ON public.room_messages;
CREATE TRIGGER trg_sync_room_message_columns
  BEFORE INSERT OR UPDATE ON public.room_messages
  FOR EACH ROW EXECUTE FUNCTION public.sync_room_message_columns();

DROP POLICY IF EXISTS "Active rooms viewable by authenticated" ON public.rooms;
DROP POLICY IF EXISTS "rooms_select_policy" ON public.rooms;
CREATE POLICY "rooms_select_policy"
  ON public.rooms FOR SELECT
  USING (is_active = TRUE OR host_id = auth.uid());

DROP POLICY IF EXISTS "rooms_insert_policy" ON public.rooms;
CREATE POLICY "rooms_insert_policy"
  ON public.rooms FOR INSERT
  WITH CHECK (auth.uid() = host_id);

DROP POLICY IF EXISTS "rooms_update_policy" ON public.rooms;
CREATE POLICY "rooms_update_policy"
  ON public.rooms FOR UPDATE
  USING (auth.uid() = host_id)
  WITH CHECK (auth.uid() = host_id);

DROP POLICY IF EXISTS "rooms_delete_policy" ON public.rooms;
CREATE POLICY "rooms_delete_policy"
  ON public.rooms FOR DELETE
  USING (auth.uid() = host_id OR public.is_admin(auth.uid()));

DROP POLICY IF EXISTS "room_members_select_policy" ON public.room_members;
CREATE POLICY "room_members_select_policy"
  ON public.room_members FOR SELECT
  USING (TRUE);

DROP POLICY IF EXISTS "room_members_insert_policy" ON public.room_members;
CREATE POLICY "room_members_insert_policy"
  ON public.room_members FOR INSERT
  WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "room_members_update_policy" ON public.room_members;
CREATE POLICY "room_members_update_policy"
  ON public.room_members FOR UPDATE
  USING (auth.uid() = user_id)
  WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "room_members_delete_policy" ON public.room_members;
CREATE POLICY "room_members_delete_policy"
  ON public.room_members FOR DELETE
  USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "room_messages_select_policy" ON public.room_messages;
CREATE POLICY "room_messages_select_policy"
  ON public.room_messages FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM public.rooms r
      WHERE r.id = room_messages.room_id AND (r.is_active = TRUE OR r.host_id = auth.uid())
    )
  );

DROP POLICY IF EXISTS "room_messages_insert_policy" ON public.room_messages;
CREATE POLICY "room_messages_insert_policy"
  ON public.room_messages FOR INSERT
  WITH CHECK (auth.uid() = sender_id);

-- 8. WEBRTC VOICE & VIDEO CALL SESSIONS AND SDP/ICE SIGNALING TABLES
CREATE TABLE IF NOT EXISTS public.call_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  caller_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  callee_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  conversation_id UUID REFERENCES public.conversations(id) ON DELETE SET NULL,
  is_video BOOLEAN NOT NULL DEFAULT FALSE,
  status TEXT NOT NULL DEFAULT 'RINGING', -- RINGING, CONNECTED, DECLINED, MISSED, ENDED, FAILED
  offer_sdp TEXT,
  answer_sdp TEXT,
  started_at TIMESTAMPTZ,
  ended_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_call_sessions_callee_status ON public.call_sessions(callee_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_call_sessions_caller_status ON public.call_sessions(caller_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS public.call_signals (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  call_id UUID NOT NULL REFERENCES public.call_sessions(id) ON DELETE CASCADE,
  sender_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  signal_type TEXT NOT NULL, -- ICE_CANDIDATE, RENEGOTIATE, MUTE_STATE, CAMERA_STATE, HANGUP, AUDIO_FRAME
  payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_call_signals_call_created ON public.call_signals(call_id, created_at ASC);

ALTER TABLE public.call_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.call_signals ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "call_sessions_participant_select" ON public.call_sessions;
CREATE POLICY "call_sessions_participant_select"
  ON public.call_sessions FOR SELECT
  USING (auth.uid() = caller_id OR auth.uid() = callee_id);

DROP POLICY IF EXISTS "call_sessions_caller_insert" ON public.call_sessions;
CREATE POLICY "call_sessions_caller_insert"
  ON public.call_sessions FOR INSERT
  WITH CHECK (auth.uid() = caller_id AND caller_id <> callee_id AND NOT public.has_block_between(caller_id, callee_id));

DROP POLICY IF EXISTS "call_sessions_participant_update" ON public.call_sessions;
CREATE POLICY "call_sessions_participant_update"
  ON public.call_sessions FOR UPDATE
  USING (auth.uid() = caller_id OR auth.uid() = callee_id)
  WITH CHECK (auth.uid() = caller_id OR auth.uid() = callee_id);

DROP POLICY IF EXISTS "call_signals_participant_select" ON public.call_signals;
CREATE POLICY "call_signals_participant_select"
  ON public.call_signals FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM public.call_sessions cs
      WHERE cs.id = call_signals.call_id
        AND (cs.caller_id = auth.uid() OR cs.callee_id = auth.uid())
    )
  );

DROP POLICY IF EXISTS "call_signals_participant_insert" ON public.call_signals;
CREATE POLICY "call_signals_participant_insert"
  ON public.call_signals FOR INSERT
  WITH CHECK (
    auth.uid() = sender_id
    AND EXISTS (
      SELECT 1 FROM public.call_sessions cs
      WHERE cs.id = call_signals.call_id
        AND (cs.caller_id = auth.uid() OR cs.callee_id = auth.uid())
    )
  );
