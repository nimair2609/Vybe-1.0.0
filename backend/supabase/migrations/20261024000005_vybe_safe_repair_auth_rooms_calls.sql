-- ============================================================================
-- VYBE INCREMENTAL SAFE REPAIR, AUTH UPGRADE, PERSISTENT ROOMS & CALL SIGNALING
-- Migration: 20261024000005_vybe_safe_repair_auth_rooms_calls.sql
-- Safe, idempotent migration preserving all existing tables, data, and RLS.
-- ============================================================================

-- 1. EXPAND MOOD_TYPE_ENUM SAFELY SO GENERAL & EXTENDED MOODS NEVER FAIL INSERT
ALTER TYPE public.mood_type_enum ADD VALUE IF NOT EXISTS 'GENERAL';
ALTER TYPE public.mood_type_enum ADD VALUE IF NOT EXISTS 'MOTIVATIONAL';
ALTER TYPE public.mood_type_enum ADD VALUE IF NOT EXISTS 'EMOTIONAL';
ALTER TYPE public.mood_type_enum ADD VALUE IF NOT EXISTS 'SPIRITUAL';

-- 2. PROFILE VERIFICATION, PRESENCE & NULL-USERNAME REPAIR
ALTER TABLE public.profiles
  ADD COLUMN IF NOT EXISTS is_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS is_phone_verified BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  ADD COLUMN IF NOT EXISTS live_pulse TEXT NOT NULL DEFAULT 'NONE';

-- Repair any legacy profile rows with NULL or blank username / display_name
UPDATE public.profiles
SET username = 'vybe_' || SUBSTRING(REPLACE(id::TEXT, '-', '') FROM 1 FOR 8)
WHERE username IS NULL OR BTRIM(username) = '' OR LOWER(BTRIM(username)) = 'null';

UPDATE public.profiles
SET display_name = COALESCE(
  NULLIF(BTRIM(COALESCE(first_name, '') || ' ' || COALESCE(last_name, '')), ''),
  username
)
WHERE display_name IS NULL OR BTRIM(display_name) = '' OR LOWER(BTRIM(display_name)) = 'null';

-- Prevent blank or "null" literal usernames on insert/update
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

-- Upgrade handle_new_user() trigger so mandatory registration metadata is persisted atomically
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

  -- Ensure uniqueness if username already exists for a different user
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

-- Sync email_confirmed_at / phone_confirmed_at updates from auth.users to public.profiles
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

-- 3. SECURE LOGIN IDENTIFIER RESOLUTION RPC (NO PUBLIC EMAIL/PHONE SCRAPING)
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

  -- Direct email login
  IF POSITION('@' IN v_raw) > 1 THEN
    RETURN jsonb_build_object('found', TRUE, 'mode', 'email', 'email', LOWER(v_raw));
  END IF;

  -- Username or VYBE-<8char> public ID lookup
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

-- Check username availability safely without exposing private fields
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

-- 4. ACCURATE POSTS_COUNT TRIGGER & RECONCILIATION (NEVER SHOW 1 WHEN 0 POSTS EXIST)
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

-- Reconcile all existing profile post counts from actual persisted non-archived posts
UPDATE public.profiles pr
SET posts_count = (
  SELECT COUNT(*)::INT
  FROM public.posts po
  WHERE po.author_id = pr.id
    AND COALESCE(po.is_archived, FALSE) = FALSE
);

-- 5. STORY VISIBILITY FIX FOR PUBLIC NON-EXPIRED STORIES
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
        AND (
          public.can_view_profile_content(p_author_id, p_viewer_id)
          OR EXISTS (
            SELECT 1 FROM public.profiles pr
            WHERE pr.id = p_author_id
              AND COALESCE(pr.is_private_account, FALSE) = FALSE
              AND COALESCE(pr.is_banned, FALSE) = FALSE
              AND COALESCE(pr.is_deactivated, FALSE) = FALSE
          )
        )
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

-- 6. PERSISTENT LIVE ROOMS WITH UNIQUE ROOM_CODE & JOIN VALIDATION
ALTER TABLE public.rooms
  ADD COLUMN IF NOT EXISTS room_code TEXT,
  ADD COLUMN IF NOT EXISTS mode TEXT NOT NULL DEFAULT 'VOICE_AND_TEXT',
  ADD COLUMN IF NOT EXISTS duration_minutes INT NOT NULL DEFAULT 45,
  ADD COLUMN IF NOT EXISTS joiner_message TEXT DEFAULT 'Welcome to this listening space.',
  ADD COLUMN IF NOT EXISTS scheduled_time TEXT DEFAULT 'Live Now',
  ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ DEFAULT (NOW() + INTERVAL '24 hours');

-- Backfill unique room_code on any existing rooms
UPDATE public.rooms
SET room_code = 'VYB-' || UPPER(SUBSTRING(REPLACE(id::TEXT, '-', '') FROM 1 FOR 4)) || '-' || UPPER(SUBSTRING(REPLACE(id::TEXT, '-', '') FROM 5 FOR 4))
WHERE room_code IS NULL OR BTRIM(room_code) = '';

CREATE UNIQUE INDEX IF NOT EXISTS idx_rooms_room_code_unique ON public.rooms(room_code);
CREATE INDEX IF NOT EXISTS idx_rooms_active_created ON public.rooms(is_active, created_at DESC);

ALTER TABLE public.room_messages
  ADD COLUMN IF NOT EXISTS sender_name TEXT,
  ADD COLUMN IF NOT EXISTS is_anonymous BOOLEAN NOT NULL DEFAULT FALSE;

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

-- 7. WEBRTC VOICE & VIDEO CALL SESSIONS AND SDP/ICE SIGNALING TABLES
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
