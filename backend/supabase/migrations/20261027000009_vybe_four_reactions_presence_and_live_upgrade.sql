-- ====================================================================
-- VYBE MIGRATION 0009: FOUR VYBE REACTIONS (PULSE, SPARK, WAVE, FLAME),
-- ACCURATE ONLINE PRESENCE, LIVE STREAM REALTIME & ZONE ISOLATION
-- Safe, non-destructive upgrade for existing VYBE Supabase projects.
-- ====================================================================

-- 1. NORMALIZE EXISTING REACTIONS TO OFFICIAL 4 VYBE REACTIONS
--    PULSE ⚡ | SPARK ✨ | WAVE 💫 | FLAME 🔥
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = 'public' AND table_name = 'post_reactions'
  ) THEN
    UPDATE public.post_reactions
    SET reaction_type = CASE UPPER(TRIM(COALESCE(reaction_type, 'PULSE')))
      WHEN 'PULSE' THEN 'PULSE'
      WHEN 'SPARK' THEN 'SPARK'
      WHEN 'WAVE' THEN 'WAVE'
      WHEN 'FLAME' THEN 'FLAME'
      WHEN 'UPLIFT' THEN 'SPARK'
      WHEN 'DEEP_VIBE' THEN 'WAVE'
      WHEN 'CALM' THEN 'WAVE'
      WHEN 'HEAT' THEN 'FLAME'
      ELSE 'PULSE'
    END
    WHERE reaction_type IS NULL
       OR reaction_type NOT IN ('PULSE', 'SPARK', 'WAVE', 'FLAME');
  END IF;
END $$;

-- 2. ENFORCE ONE REACTION PER USER PER POST/REEL & VALID REACTION TYPES
CREATE UNIQUE INDEX IF NOT EXISTS idx_post_reactions_unique_post_user
  ON public.post_reactions (post_id, user_id);

CREATE INDEX IF NOT EXISTS idx_post_reactions_post_id
  ON public.post_reactions (post_id);

CREATE INDEX IF NOT EXISTS idx_post_reactions_user_id
  ON public.post_reactions (user_id);

ALTER TABLE public.post_reactions
  DROP CONSTRAINT IF EXISTS chk_vybe_four_reaction_types;

ALTER TABLE public.post_reactions
  ADD CONSTRAINT chk_vybe_four_reaction_types
  CHECK (reaction_type IN ('PULSE', 'SPARK', 'WAVE', 'FLAME'));

-- 3. ADD INDIVIDUAL REACTION BREAKDOWN COUNTERS ON POSTS (USED BY REELS)
ALTER TABLE public.posts
  ADD COLUMN IF NOT EXISTS pulse_count INT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS spark_count INT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS wave_count  INT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS flame_count INT NOT NULL DEFAULT 0;

-- Backfill exact breakdown & total reaction counts from public.post_reactions
UPDATE public.posts p
SET
  pulse_count = COALESCE(agg.pulse_cnt, 0),
  spark_count = COALESCE(agg.spark_cnt, 0),
  wave_count  = COALESCE(agg.wave_cnt, 0),
  flame_count = COALESCE(agg.flame_cnt, 0),
  likes_count = COALESCE(agg.total_cnt, 0)
FROM (
  SELECT
    p2.id AS post_id,
    COUNT(pr.user_id) FILTER (WHERE pr.reaction_type = 'PULSE')::INT AS pulse_cnt,
    COUNT(pr.user_id) FILTER (WHERE pr.reaction_type = 'SPARK')::INT AS spark_cnt,
    COUNT(pr.user_id) FILTER (WHERE pr.reaction_type = 'WAVE')::INT  AS wave_cnt,
    COUNT(pr.user_id) FILTER (WHERE pr.reaction_type = 'FLAME')::INT AS flame_cnt,
    COUNT(pr.user_id)::INT AS total_cnt
  FROM public.posts p2
  LEFT JOIN public.post_reactions pr ON pr.post_id = p2.id
  GROUP BY p2.id
) agg
WHERE p.id = agg.post_id;

-- 4. ATOMIC TRIGGER: SYNC ALL 4 REACTION COUNTS + NOTIFY OWNER (NEVER SELF)
CREATE OR REPLACE FUNCTION public.handle_reaction_change()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_target_post_id UUID;
  v_author_id UUID;
  v_is_reel BOOLEAN;
  v_actor_name TEXT;
  v_symbol TEXT;
  v_label TEXT;
  v_content_kind TEXT;
BEGIN
  v_target_post_id := COALESCE(NEW.post_id, OLD.post_id);

  -- Recompute authoritative breakdown counts for the post/reel
  UPDATE public.posts p
  SET
    pulse_count = (SELECT COUNT(*)::INT FROM public.post_reactions WHERE post_id = v_target_post_id AND reaction_type = 'PULSE'),
    spark_count = (SELECT COUNT(*)::INT FROM public.post_reactions WHERE post_id = v_target_post_id AND reaction_type = 'SPARK'),
    wave_count  = (SELECT COUNT(*)::INT FROM public.post_reactions WHERE post_id = v_target_post_id AND reaction_type = 'WAVE'),
    flame_count = (SELECT COUNT(*)::INT FROM public.post_reactions WHERE post_id = v_target_post_id AND reaction_type = 'FLAME'),
    likes_count = (SELECT COUNT(*)::INT FROM public.post_reactions WHERE post_id = v_target_post_id)
  WHERE p.id = v_target_post_id
  RETURNING p.author_id, (COALESCE(p.is_vybes_reel, FALSE) OR p.media_type::TEXT = 'REEL')
  INTO v_author_id, v_is_reel;

  -- Notify content owner on INSERT or reaction switch UPDATE, NEVER when reacting to own content
  IF (TG_OP = 'INSERT' OR (TG_OP = 'UPDATE' AND OLD.reaction_type IS DISTINCT FROM NEW.reaction_type)) THEN
    IF v_author_id IS NOT NULL AND v_author_id <> NEW.user_id THEN
      SELECT display_name INTO v_actor_name FROM public.profiles WHERE id = NEW.user_id;

      v_label := COALESCE(NEW.reaction_type, 'PULSE');
      v_symbol := CASE v_label
        WHEN 'PULSE' THEN '⚡'
        WHEN 'SPARK' THEN '✨'
        WHEN 'WAVE'  THEN '💫'
        WHEN 'FLAME' THEN '🔥'
        ELSE '⚡'
      END;
      v_content_kind := CASE WHEN COALESCE(v_is_reel, FALSE) THEN 'reel' ELSE 'post' END;

      -- Update existing unread reaction notification for same (recipient, actor, post) or insert fresh one
      UPDATE public.notifications
      SET
        title = v_symbol || ' ' || v_label || ' Reaction',
        message = COALESCE(v_actor_name, 'Someone') || ' reacted with ' || v_label || ' ' || v_symbol || ' to your ' || v_content_kind || '.',
        is_read = FALSE,
        created_at = NOW()
      WHERE recipient_id = v_author_id
        AND actor_id = NEW.user_id
        AND type IN ('LIKE', 'REACTION')
        AND target_id = v_target_post_id::TEXT;

      IF NOT FOUND THEN
        INSERT INTO public.notifications (recipient_id, actor_id, type, title, message, target_id)
        VALUES (
          v_author_id,
          NEW.user_id,
          'REACTION',
          v_symbol || ' ' || v_label || ' Reaction',
          COALESCE(v_actor_name, 'Someone') || ' reacted with ' || v_label || ' ' || v_symbol || ' to your ' || v_content_kind || '.',
          v_target_post_id::TEXT
        );
      END IF;
    END IF;
  END IF;

  IF TG_OP = 'DELETE' THEN
    RETURN OLD;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_reaction_change ON public.post_reactions;
CREATE TRIGGER trg_reaction_change
  AFTER INSERT OR UPDATE OR DELETE ON public.post_reactions
  FOR EACH ROW EXECUTE FUNCTION public.handle_reaction_change();

-- 5. SECURE RLS POLICIES FOR POST REACTIONS & NOTIFICATIONS
ALTER TABLE public.post_reactions ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Authorized reactions readable" ON public.post_reactions;
DROP POLICY IF EXISTS "Reactions readable on visible content" ON public.post_reactions;
CREATE POLICY "Reactions readable on visible content"
  ON public.post_reactions FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM public.posts p
      WHERE p.id = post_reactions.post_id
        AND NOT COALESCE(p.is_archived, FALSE)
    )
  );

DROP POLICY IF EXISTS "Users manage own reactions" ON public.post_reactions;
DROP POLICY IF EXISTS "Users insert own reaction" ON public.post_reactions;
DROP POLICY IF EXISTS "Users update own reaction" ON public.post_reactions;
DROP POLICY IF EXISTS "Users delete own reaction" ON public.post_reactions;

CREATE POLICY "Users insert own reaction"
  ON public.post_reactions FOR INSERT
  WITH CHECK (
    auth.uid() = user_id
    AND reaction_type IN ('PULSE', 'SPARK', 'WAVE', 'FLAME')
  );

CREATE POLICY "Users update own reaction"
  ON public.post_reactions FOR UPDATE
  USING (auth.uid() = user_id)
  WITH CHECK (
    auth.uid() = user_id
    AND reaction_type IN ('PULSE', 'SPARK', 'WAVE', 'FLAME')
  );

CREATE POLICY "Users delete own reaction"
  ON public.post_reactions FOR DELETE
  USING (auth.uid() = user_id);

-- Strictly prevent self-notifications at the RLS layer as well
DROP POLICY IF EXISTS "Authenticated users create interaction notifications" ON public.notifications;
CREATE POLICY "Authenticated users create interaction notifications"
  ON public.notifications FOR INSERT
  WITH CHECK (auth.uid() = actor_id AND actor_id <> recipient_id);

-- 6. FIX ONLINE / OFFLINE PRESENCE DEFAULTS & LOGOUT CLEANUP RPC
ALTER TABLE public.profiles
  ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ DEFAULT NULL;

ALTER TABLE public.profiles
  ALTER COLUMN last_seen_at SET DEFAULT NULL;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'profiles' AND column_name = 'live_pulse'
  ) THEN
    ALTER TABLE public.profiles ALTER COLUMN live_pulse SET DEFAULT 'NONE';
  END IF;
END $$;

-- Clear stale last_seen_at older than 2 minutes so inactive accounts immediately show offline
UPDATE public.profiles
SET last_seen_at = NULL
WHERE last_seen_at IS NOT NULL
  AND last_seen_at < (NOW() - INTERVAL '2 minutes');

CREATE OR REPLACE FUNCTION public.mark_user_offline()
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF auth.uid() IS NOT NULL THEN
    UPDATE public.profiles
    SET last_seen_at = NULL
    WHERE id = auth.uid();
  END IF;
END;
$$;

GRANT EXECUTE ON FUNCTION public.mark_user_offline() TO authenticated;

-- 7. ENSURE REALTIME PUBLICATION INCLUDES REACTIONS, LIVE STREAMS & PROFILES
DO $$
DECLARE
  v_tbl TEXT;
BEGIN
  FOREACH v_tbl IN ARRAY ARRAY['post_reactions', 'posts', 'live_streams', 'live_stream_comments', 'profiles', 'notifications']
  LOOP
    IF EXISTS (
      SELECT 1 FROM information_schema.tables
      WHERE table_schema = 'public' AND table_name = v_tbl
    ) AND NOT EXISTS (
      SELECT 1 FROM pg_publication_tables
      WHERE pubname = 'supabase_realtime'
        AND schemaname = 'public'
        AND tablename = v_tbl
    ) THEN
      EXECUTE format('ALTER PUBLICATION supabase_realtime ADD TABLE public.%I', v_tbl);
    END IF;
  END LOOP;
END $$;
