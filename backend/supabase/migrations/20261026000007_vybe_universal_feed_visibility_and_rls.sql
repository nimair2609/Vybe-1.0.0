-- ============================================================================
-- VYBE UNIVERSAL FEED VISIBILITY, CROSS-USER MEDIA ACCESS & COMPLETE FEED RLS
-- Migration: 20261026000007_vybe_universal_feed_visibility_and_rls.sql
-- Standalone & Idempotent: Ensures ANY user (newly registered users with 0
-- follows, returning accounts, and public feed viewers) can see non-archived
-- public posts, post_media, comments, reactions, and public stories safely,
-- while strictly preserving private account, block, and ownership protections.
-- ============================================================================

-- 1. NORMALIZE NULL FLAGS SO LEGACY POSTS & PROFILES ARE NEVER HIDDEN BY SQL THREE-VALUED LOGIC
ALTER TABLE public.profiles
  ADD COLUMN IF NOT EXISTS is_private_account BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS is_banned BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS is_deactivated BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE public.profiles
SET
  is_private_account = COALESCE(is_private_account, FALSE),
  is_banned = COALESCE(is_banned, FALSE),
  is_deactivated = COALESCE(is_deactivated, FALSE)
WHERE is_private_account IS NULL OR is_banned IS NULL OR is_deactivated IS NULL;

ALTER TABLE public.posts
  ADD COLUMN IF NOT EXISTS is_archived BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS unlock_at TIMESTAMPTZ;

UPDATE public.posts
SET is_archived = FALSE
WHERE is_archived IS NULL;

ALTER TABLE public.post_media
  ADD COLUMN IF NOT EXISTS display_order INT DEFAULT 0,
  ADD COLUMN IF NOT EXISTS storage_path TEXT;

-- 2. UNIVERSAL CONTENT VISIBILITY HELPER FUNCTIONS
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

-- Any viewer (including brand-new users with 0 follows or unauthenticated public feed viewers)
-- can view content from any non-private, non-banned, non-deactivated author unless blocked.
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

CREATE OR REPLACE FUNCTION public.try_parse_uuid(p_input TEXT)
RETURNS UUID
LANGUAGE plpgsql
IMMUTABLE
AS $$
BEGIN
  IF p_input IS NULL OR BTRIM(p_input) = '' THEN
    RETURN NULL;
  END IF;
  RETURN BTRIM(p_input)::UUID;
EXCEPTION WHEN OTHERS THEN
  RETURN NULL;
END;
$$;

-- 3. COMPLETE SELF-CONTAINED RLS POLICIES FOR PROFILES, POSTS, POST_MEDIA, COMMENTS, REACTIONS & SAVES

-- PROFILES
DROP POLICY IF EXISTS "Public profiles are viewable by everyone" ON public.profiles;
CREATE POLICY "Public profiles are viewable by everyone"
  ON public.profiles FOR SELECT
  USING (COALESCE(is_banned, FALSE) = FALSE OR auth.uid() = id);

DROP POLICY IF EXISTS "Users can insert their own profile" ON public.profiles;
DROP POLICY IF EXISTS "Users can insert own profile" ON public.profiles;
CREATE POLICY "Users can insert own profile"
  ON public.profiles FOR INSERT
  WITH CHECK (auth.uid() = id);

DROP POLICY IF EXISTS "Users can update own profile" ON public.profiles;
CREATE POLICY "Users can update own profile"
  ON public.profiles FOR UPDATE
  USING (auth.uid() = id)
  WITH CHECK (auth.uid() = id);

-- POSTS
DROP POLICY IF EXISTS "Public can view active posts" ON public.posts;
DROP POLICY IF EXISTS "Active posts readable by everyone" ON public.posts;
DROP POLICY IF EXISTS "Authorized posts readable" ON public.posts;
CREATE POLICY "Authorized posts readable"
  ON public.posts FOR SELECT
  USING (
    (COALESCE(is_archived, FALSE) = FALSE OR auth.uid() = author_id)
    AND (unlock_at IS NULL OR unlock_at <= NOW() OR auth.uid() = author_id)
    AND public.can_view_author_content(auth.uid(), author_id)
  );

DROP POLICY IF EXISTS "Users can create own posts" ON public.posts;
DROP POLICY IF EXISTS "Users create own posts" ON public.posts;
CREATE POLICY "Users create own posts"
  ON public.posts FOR INSERT
  WITH CHECK (auth.uid() = author_id);

DROP POLICY IF EXISTS "Users can update own posts" ON public.posts;
DROP POLICY IF EXISTS "Users update own posts" ON public.posts;
CREATE POLICY "Users update own posts"
  ON public.posts FOR UPDATE
  USING (auth.uid() = author_id)
  WITH CHECK (auth.uid() = author_id);

DROP POLICY IF EXISTS "Users can delete own posts" ON public.posts;
DROP POLICY IF EXISTS "Authors or Admins can delete posts" ON public.posts;
DROP POLICY IF EXISTS "Users delete own posts" ON public.posts;
CREATE POLICY "Users delete own posts"
  ON public.posts FOR DELETE
  USING (auth.uid() = author_id OR public.is_admin(auth.uid()));

-- POST_MEDIA (Fixes empty media array when 000004 was not applied)
DROP POLICY IF EXISTS "Public can view post media" ON public.post_media;
DROP POLICY IF EXISTS "Authorized post media readable" ON public.post_media;
CREATE POLICY "Authorized post media readable"
  ON public.post_media FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM public.posts p
      WHERE p.id = post_media.post_id
        AND (COALESCE(p.is_archived, FALSE) = FALSE OR auth.uid() = p.author_id)
        AND public.can_view_author_content(auth.uid(), p.author_id)
    )
  );

DROP POLICY IF EXISTS "Post author manages post media" ON public.post_media;
CREATE POLICY "Post author manages post media"
  ON public.post_media FOR ALL
  USING (
    EXISTS (
      SELECT 1 FROM public.posts p
      WHERE p.id = post_media.post_id AND p.author_id = auth.uid()
    )
  )
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM public.posts p
      WHERE p.id = post_media.post_id AND p.author_id = auth.uid()
    )
  );

-- COMMENTS
DROP POLICY IF EXISTS "Authorized comments readable" ON public.comments;
CREATE POLICY "Authorized comments readable"
  ON public.comments FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM public.posts p
      WHERE p.id = comments.post_id
        AND public.can_view_author_content(auth.uid(), p.author_id)
    )
  );

DROP POLICY IF EXISTS "Authenticated users insert comments on visible posts" ON public.comments;
CREATE POLICY "Authenticated users insert comments on visible posts"
  ON public.comments FOR INSERT
  WITH CHECK (
    auth.uid() = author_id
    AND EXISTS (
      SELECT 1 FROM public.posts p
      WHERE p.id = comments.post_id
        AND public.can_view_author_content(auth.uid(), p.author_id)
    )
  );

DROP POLICY IF EXISTS "Comment or post author can delete comment" ON public.comments;
CREATE POLICY "Comment or post author can delete comment"
  ON public.comments FOR DELETE
  USING (
    auth.uid() = author_id
    OR EXISTS (
      SELECT 1 FROM public.posts p
      WHERE p.id = comments.post_id AND p.author_id = auth.uid()
    )
  );

-- POST REACTIONS (Pulse-V Resonance)
DROP POLICY IF EXISTS "Authorized reactions readable" ON public.post_reactions;
CREATE POLICY "Authorized reactions readable"
  ON public.post_reactions FOR SELECT
  USING (TRUE);

DROP POLICY IF EXISTS "Authenticated users manage own reactions" ON public.post_reactions;
CREATE POLICY "Authenticated users manage own reactions"
  ON public.post_reactions FOR ALL
  USING (auth.uid() = user_id)
  WITH CHECK (auth.uid() = user_id);

-- SAVED POSTS
DROP POLICY IF EXISTS "Users manage own saved posts" ON public.saved_posts;
CREATE POLICY "Users manage own saved posts"
  ON public.saved_posts FOR ALL
  USING (auth.uid() = user_id)
  WITH CHECK (auth.uid() = user_id);

-- FOLLOWS
DROP POLICY IF EXISTS "Follows are viewable by everyone" ON public.follows;
DROP POLICY IF EXISTS "Follows readable by everyone" ON public.follows;
CREATE POLICY "Follows readable by everyone"
  ON public.follows FOR SELECT
  USING (
    COALESCE(status, 'ACCEPTED') = 'ACCEPTED'
    OR auth.uid() = follower_id
    OR auth.uid() = followed_id
  );

DROP POLICY IF EXISTS "Users insert own follows" ON public.follows;
CREATE POLICY "Users insert own follows"
  ON public.follows FOR INSERT
  WITH CHECK (
    auth.uid() = follower_id
    AND follower_id <> followed_id
    AND NOT public.is_blocked_between(follower_id, followed_id)
  );

DROP POLICY IF EXISTS "Follower or target can update follow status" ON public.follows;
CREATE POLICY "Follower or target can update follow status"
  ON public.follows FOR UPDATE
  USING (auth.uid() = followed_id OR auth.uid() = follower_id)
  WITH CHECK (auth.uid() = followed_id OR auth.uid() = follower_id);

DROP POLICY IF EXISTS "Follower or target can delete follow" ON public.follows;
CREATE POLICY "Follower or target can delete follow"
  ON public.follows FOR DELETE
  USING (auth.uid() = follower_id OR auth.uid() = followed_id);

-- 4. STORAGE BUCKETS & MEDIA POLICIES FOR SEAMLESS CROSS-PLATFORM FEED VIEWING
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES
  ('avatars', 'avatars', true, 10485760, ARRAY['image/jpeg', 'image/png', 'image/webp']),
  ('posts', 'posts', true, 104857600, ARRAY['image/jpeg', 'image/png', 'image/webp', 'video/mp4', 'video/webm', 'video/quicktime']),
  ('stories', 'stories', true, 104857600, ARRAY['image/jpeg', 'image/png', 'image/webp', 'video/mp4', 'video/webm', 'video/quicktime']),
  ('chat-media', 'chat-media', false, 52428800, ARRAY['image/jpeg', 'image/png', 'image/webp', 'video/mp4', 'audio/aac', 'audio/mpeg', 'audio/mp4'])
ON CONFLICT (id) DO UPDATE
SET public = CASE WHEN storage.buckets.id IN ('avatars', 'posts', 'stories') THEN true ELSE storage.buckets.public END;

DROP POLICY IF EXISTS "Avatars public read" ON storage.objects;
CREATE POLICY "Avatars public read"
  ON storage.objects FOR SELECT
  USING (bucket_id = 'avatars');

DROP POLICY IF EXISTS "Users upload own media folder" ON storage.objects;
CREATE POLICY "Users upload own media folder"
  ON storage.objects FOR INSERT
  TO authenticated
  WITH CHECK (
    bucket_id IN ('avatars', 'posts', 'stories', 'chat-media')
    AND (storage.foldername(name))[1] = auth.uid()::text
  );

DROP POLICY IF EXISTS "Users update own media folder" ON storage.objects;
CREATE POLICY "Users update own media folder"
  ON storage.objects FOR UPDATE
  TO authenticated
  USING (
    bucket_id IN ('avatars', 'posts', 'stories', 'chat-media')
    AND (storage.foldername(name))[1] = auth.uid()::text
  );

DROP POLICY IF EXISTS "Users delete own media folder" ON storage.objects;
CREATE POLICY "Users delete own media folder"
  ON storage.objects FOR DELETE
  TO authenticated
  USING (
    bucket_id IN ('avatars', 'posts', 'stories', 'chat-media')
    AND (storage.foldername(name))[1] = auth.uid()::text
  );

DROP POLICY IF EXISTS "Authorized viewers read posts and stories media" ON storage.objects;
CREATE POLICY "Authorized viewers read posts and stories media"
  ON storage.objects FOR SELECT
  USING (
    (bucket_id = 'posts' AND public.can_view_author_content(auth.uid(), public.try_parse_uuid((storage.foldername(name))[1])))
    OR (
      bucket_id = 'stories'
      AND (
        auth.uid()::text = (storage.foldername(name))[1]
        OR public.can_view_author_content(auth.uid(), public.try_parse_uuid((storage.foldername(name))[1]))
      )
    )
    OR (
      bucket_id = 'chat-media'
      AND auth.uid() IS NOT NULL
    )
  );

-- 5. ENABLE SUPABASE REALTIME FOR LIVE FEED & STORY SYNCHRONIZATION ACROSS ALL USERS
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime') THEN
    IF NOT EXISTS (
      SELECT 1 FROM pg_publication_tables
      WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'posts'
    ) THEN
      ALTER PUBLICATION supabase_realtime ADD TABLE public.posts;
    END IF;
    IF NOT EXISTS (
      SELECT 1 FROM pg_publication_tables
      WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'stories'
    ) THEN
      ALTER PUBLICATION supabase_realtime ADD TABLE public.stories;
    END IF;
  END IF;
END $$;
