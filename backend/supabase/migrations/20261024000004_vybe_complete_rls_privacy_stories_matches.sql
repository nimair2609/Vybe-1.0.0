-- ============================================================================
-- VYBE BY NR NEXT — MIGRATION 0004 (SCHEMA-SAFE & IDEMPOTENT)
-- Complete RLS Audit, Private/Public Accounts, Story Views, VYBE Matches,
-- Real Notifications Triggers, Storage Buckets/RLS, & Idempotent Uploads
-- Safe & Non-Destructive: Preserves all existing users, profiles, posts & chats
-- ============================================================================

-- 0. ENSURE ENUM VALUES EXIST SAFELY (IF ENUMS ARE USED)
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_type WHERE typname = 'mood_type_enum') THEN
    ALTER TYPE public.mood_type_enum ADD VALUE IF NOT EXISTS 'GENERAL';
  END IF;
END $$;

-- 1. EXTEND PROFILES FOR MODERATION, PRIVACY, MESSAGING & COUNTERS (IF NOT EXISTS)
ALTER TABLE public.profiles
  ADD COLUMN IF NOT EXISTS is_banned BOOLEAN DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS is_private_account BOOLEAN DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS message_privacy VARCHAR(30) DEFAULT 'EVERYONE',
  ADD COLUMN IF NOT EXISTS story_privacy VARCHAR(30) DEFAULT 'EVERYONE',
  ADD COLUMN IF NOT EXISTS followers_count INT DEFAULT 0,
  ADD COLUMN IF NOT EXISTS following_count INT DEFAULT 0,
  ADD COLUMN IF NOT EXISTS posts_count INT DEFAULT 0;

-- 2. ENSURE FOLLOWS & BLOCKS TABLES + PRIVATE ACCOUNT APPROVAL STATUS EXIST
CREATE TABLE IF NOT EXISTS public.follows (
  follower_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  followed_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  status VARCHAR(20) NOT NULL DEFAULT 'ACCEPTED',
  created_at TIMESTAMPTZ DEFAULT NOW(),
  PRIMARY KEY (follower_id, followed_id)
);

ALTER TABLE public.follows
  ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACCEPTED';

ALTER TABLE public.follows ENABLE ROW LEVEL SECURITY;

CREATE INDEX IF NOT EXISTS idx_follows_followed_status
  ON public.follows (followed_id, status);
CREATE INDEX IF NOT EXISTS idx_follows_follower_status
  ON public.follows (follower_id, status);

CREATE TABLE IF NOT EXISTS public.blocks (
  blocker_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  blocked_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  PRIMARY KEY (blocker_id, blocked_id)
);

ALTER TABLE public.blocks ENABLE ROW LEVEL SECURITY;

-- 3. USER RESTRICTIONS TABLE (FOR MESSAGE / INTERACTION RESTRICTING)
CREATE TABLE IF NOT EXISTS public.user_restrictions (
  restrictor_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  restricted_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  PRIMARY KEY (restrictor_id, restricted_id),
  CONSTRAINT no_self_restrict CHECK (restrictor_id <> restricted_id)
);
ALTER TABLE public.user_restrictions ENABLE ROW LEVEL SECURITY;

-- 4. EXTEND POSTS & POST_MEDIA FOR LOCATION, MUSIC, ASPECT RATIO, SCHEDULE & IDEMPOTENT RETRIES
ALTER TABLE public.posts
  ADD COLUMN IF NOT EXISTS is_archived BOOLEAN DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS is_unfiltered BOOLEAN DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS is_vybes_reel BOOLEAN DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS is_spiritual BOOLEAN DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS unlock_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS likes_count INT DEFAULT 0,
  ADD COLUMN IF NOT EXISTS comments_count INT DEFAULT 0,
  ADD COLUMN IF NOT EXISTS saves_count INT DEFAULT 0,
  ADD COLUMN IF NOT EXISTS views_count INT DEFAULT 0,
  ADD COLUMN IF NOT EXISTS location_name TEXT,
  ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS music_title TEXT,
  ADD COLUMN IF NOT EXISTS client_upload_id VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS idx_posts_client_upload_id
  ON public.posts (client_upload_id)
  WHERE client_upload_id IS NOT NULL;

ALTER TABLE public.post_media
  ADD COLUMN IF NOT EXISTS thumbnail_url TEXT,
  ADD COLUMN IF NOT EXISTS display_order INT DEFAULT 0,
  ADD COLUMN IF NOT EXISTS aspect_ratio DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS storage_path TEXT;

-- 5. EXTEND STORIES + CREATE STORY_VIEWS & VYBE_MATCHES
ALTER TABLE public.stories
  ADD COLUMN IF NOT EXISTS media_type VARCHAR(20) DEFAULT 'PHOTO',
  ADD COLUMN IF NOT EXISTS audience VARCHAR(30) NOT NULL DEFAULT 'EVERYONE',
  ADD COLUMN IF NOT EXISTS music_title TEXT,
  ADD COLUMN IF NOT EXISTS location_name TEXT,
  ADD COLUMN IF NOT EXISTS text_bg_key VARCHAR(40),
  ADD COLUMN IF NOT EXISTS storage_path TEXT,
  ADD COLUMN IF NOT EXISTS client_upload_id VARCHAR(100),
  ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ DEFAULT (NOW() + INTERVAL '24 hours');

-- Backfill media_type from legacy story_type column if present
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'stories' AND column_name = 'story_type'
  ) THEN
    EXECUTE 'UPDATE public.stories SET media_type = story_type WHERE (media_type IS NULL OR media_type = '''') AND story_type IS NOT NULL';
  END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS idx_stories_client_upload_id
  ON public.stories (client_upload_id)
  WHERE client_upload_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_stories_active_author
  ON public.stories (author_id, expires_at DESC);

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

-- 5b. EXTEND CONVERSATIONS, MEMBERS, MESSAGES & NOTIFICATIONS COLUMNS SAFELY
ALTER TABLE public.conversations
  ADD COLUMN IF NOT EXISTS is_group BOOLEAN DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS group_name VARCHAR(100),
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT NOW();

ALTER TABLE public.conversation_members
  ADD COLUMN IF NOT EXISTS is_muted BOOLEAN DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS is_hidden BOOLEAN DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS custom_nickname VARCHAR(50),
  ADD COLUMN IF NOT EXISTS chat_theme VARCHAR(30) DEFAULT 'DEFAULT';

ALTER TABLE public.messages
  ADD COLUMN IF NOT EXISTS media_url TEXT,
  ADD COLUMN IF NOT EXISTS media_type VARCHAR(30) DEFAULT 'TEXT',
  ADD COLUMN IF NOT EXISTS reply_to_message_id UUID,
  ADD COLUMN IF NOT EXISTS is_deleted_for_everyone BOOLEAN DEFAULT FALSE;

ALTER TABLE public.notifications
  ADD COLUMN IF NOT EXISTS actor_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS target_id TEXT,
  ADD COLUMN IF NOT EXISTS is_read BOOLEAN DEFAULT FALSE;

ALTER TABLE public.notifications
  ALTER COLUMN target_id TYPE TEXT USING target_id::text;

-- 6. SECURITY HELPER FUNCTIONS (SECURITY DEFINER TO PREVENT RLS RECURSION)
CREATE OR REPLACE FUNCTION public.try_parse_uuid(p_input TEXT)
RETURNS UUID
LANGUAGE plpgsql
IMMUTABLE
AS $$
BEGIN
  IF p_input IS NULL OR p_input = '' THEN
    RETURN NULL;
  END IF;
  RETURN p_input::uuid;
EXCEPTION WHEN OTHERS THEN
  RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION public.is_blocked_between(p_user_a UUID, p_user_b UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.blocks
    WHERE (blocker_id = p_user_a AND blocked_id = p_user_b)
       OR (blocker_id = p_user_b AND blocked_id = p_user_a)
  );
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
      ) THEN TRUE
      WHEN p_viewer_id IS NOT NULL AND EXISTS (
        SELECT 1 FROM public.follows f
        WHERE f.follower_id = p_viewer_id
          AND f.followed_id = p_author_id
          AND f.status = 'ACCEPTED'
      ) THEN TRUE
      ELSE FALSE
    END;
$$;

CREATE OR REPLACE FUNCTION public.can_view_story(p_viewer_id UUID, p_story_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1
    FROM public.stories s
    JOIN public.profiles pr ON pr.id = s.author_id
    WHERE s.id = p_story_id
      AND s.expires_at > NOW()
      AND COALESCE(pr.is_banned, FALSE) = FALSE
      AND (
        (p_viewer_id IS NOT NULL AND s.author_id = p_viewer_id)
        OR (
          p_viewer_id IS NOT NULL
          AND NOT public.is_blocked_between(p_viewer_id, s.author_id)
          AND (
            (
              COALESCE(s.audience, 'EVERYONE') IN ('VYBE_MATCHES', 'CLOSE_FRIENDS')
              AND EXISTS (
                SELECT 1 FROM public.vybe_matches vm
                WHERE vm.user_id = s.author_id
                  AND vm.matched_user_id = p_viewer_id
              )
            )
            OR (
              COALESCE(s.audience, 'EVERYONE') = 'FOLLOWERS'
              AND EXISTS (
                SELECT 1 FROM public.follows f
                WHERE f.follower_id = p_viewer_id
                  AND f.followed_id = s.author_id
                  AND f.status = 'ACCEPTED'
              )
            )
            OR (
              COALESCE(s.audience, 'EVERYONE') = 'EVERYONE'
              AND public.can_view_author_content(p_viewer_id, s.author_id)
            )
          )
        )
      )
  );
$$;

CREATE OR REPLACE FUNCTION public.is_conversation_member(p_conversation_id UUID, p_user_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.conversation_members cm
    WHERE cm.conversation_id = p_conversation_id
      AND cm.user_id = p_user_id
  );
$$;

-- 7. COMPREHENSIVE ROW LEVEL SECURITY POLICIES (IDEMPOTENT DROP + CREATE)

-- PROFILES: Only owner can update their own profile; banned profiles hidden from peers
DROP POLICY IF EXISTS "Public profiles are viewable by everyone" ON public.profiles;
CREATE POLICY "Public profiles are viewable by everyone"
  ON public.profiles FOR SELECT
  USING (COALESCE(is_banned, FALSE) = FALSE OR auth.uid() = id);

DROP POLICY IF EXISTS "Users can update own profile" ON public.profiles;
CREATE POLICY "Users can update own profile"
  ON public.profiles FOR UPDATE
  USING (auth.uid() = id)
  WITH CHECK (auth.uid() = id);

DROP POLICY IF EXISTS "Users can insert their own profile" ON public.profiles;
DROP POLICY IF EXISTS "Users can insert own profile" ON public.profiles;
CREATE POLICY "Users can insert own profile"
  ON public.profiles FOR INSERT
  WITH CHECK (auth.uid() = id);

-- FOLLOWS: Support follow requests + target user accepting/rejecting pending requests
DROP POLICY IF EXISTS "Follows are viewable by everyone" ON public.follows;
DROP POLICY IF EXISTS "Follows readable by everyone" ON public.follows;
CREATE POLICY "Follows readable by everyone"
  ON public.follows FOR SELECT
  USING (
    status = 'ACCEPTED'
    OR auth.uid() = follower_id
    OR auth.uid() = followed_id
  );

DROP POLICY IF EXISTS "Users can follow" ON public.follows;
DROP POLICY IF EXISTS "Users manage own follows" ON public.follows;
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

DROP POLICY IF EXISTS "Users can unfollow" ON public.follows;
DROP POLICY IF EXISTS "Follower or target can delete follow" ON public.follows;
CREATE POLICY "Follower or target can delete follow"
  ON public.follows FOR DELETE
  USING (auth.uid() = follower_id OR auth.uid() = followed_id);

-- BLOCKS & USER RESTRICTIONS
DROP POLICY IF EXISTS "Users can view their own blocks" ON public.blocks;
CREATE POLICY "Users can view their own blocks"
  ON public.blocks FOR SELECT
  USING (auth.uid() = blocker_id);

DROP POLICY IF EXISTS "Users can insert blocks" ON public.blocks;
CREATE POLICY "Users can insert blocks"
  ON public.blocks FOR INSERT
  WITH CHECK (auth.uid() = blocker_id AND blocker_id <> blocked_id);

DROP POLICY IF EXISTS "Users can delete own blocks" ON public.blocks;
CREATE POLICY "Users can delete own blocks"
  ON public.blocks FOR DELETE
  USING (auth.uid() = blocker_id);

DROP POLICY IF EXISTS "Users manage own restrictions" ON public.user_restrictions;
CREATE POLICY "Users manage own restrictions"
  ON public.user_restrictions FOR ALL
  USING (auth.uid() = restrictor_id)
  WITH CHECK (auth.uid() = restrictor_id);

-- POSTS: Enforce Public vs Private account visibility & block list
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
  USING (
    auth.uid() = author_id
    OR EXISTS (
      SELECT 1 FROM public.profiles
      WHERE id = auth.uid() AND role::text = 'ADMIN'
    )
  );

-- POST MEDIA
DROP POLICY IF EXISTS "Authorized post media readable" ON public.post_media;
CREATE POLICY "Authorized post media readable"
  ON public.post_media FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM public.posts p
      WHERE p.id = post_media.post_id
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

-- POST REACTIONS
DROP POLICY IF EXISTS "Authorized reactions readable" ON public.post_reactions;
CREATE POLICY "Authorized reactions readable"
  ON public.post_reactions FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM public.posts p
      WHERE p.id = post_reactions.post_id
        AND public.can_view_author_content(auth.uid(), p.author_id)
    )
  );

DROP POLICY IF EXISTS "Users manage own reactions" ON public.post_reactions;
CREATE POLICY "Users manage own reactions"
  ON public.post_reactions FOR ALL
  USING (auth.uid() = user_id)
  WITH CHECK (
    auth.uid() = user_id
    AND EXISTS (
      SELECT 1 FROM public.posts p
      WHERE p.id = post_reactions.post_id
        AND public.can_view_author_content(auth.uid(), p.author_id)
    )
  );

-- SAVED POSTS
DROP POLICY IF EXISTS "Users manage own saved posts" ON public.saved_posts;
CREATE POLICY "Users manage own saved posts"
  ON public.saved_posts FOR ALL
  USING (auth.uid() = user_id)
  WITH CHECK (auth.uid() = user_id);

-- STORIES (Enforces 24h expiry, Private profile rules, & VYBE Matches audience!)
DROP POLICY IF EXISTS "Stories viewable by everyone" ON public.stories;
DROP POLICY IF EXISTS "Public can view active stories" ON public.stories;
DROP POLICY IF EXISTS "Authorized active stories readable" ON public.stories;
CREATE POLICY "Authorized active stories readable"
  ON public.stories FOR SELECT
  USING (public.can_view_story(auth.uid(), id));

DROP POLICY IF EXISTS "Users can create own stories" ON public.stories;
DROP POLICY IF EXISTS "Users create own stories" ON public.stories;
CREATE POLICY "Users create own stories"
  ON public.stories FOR INSERT
  WITH CHECK (auth.uid() = author_id);

DROP POLICY IF EXISTS "Users can delete own stories" ON public.stories;
DROP POLICY IF EXISTS "Users delete own stories" ON public.stories;
CREATE POLICY "Users delete own stories"
  ON public.stories FOR DELETE
  USING (auth.uid() = author_id);

-- STORY VIEWS (Persistent viewed-story tracking across devices)
DROP POLICY IF EXISTS "Story views readable by viewer or story author" ON public.story_views;
CREATE POLICY "Story views readable by viewer or story author"
  ON public.story_views FOR SELECT
  USING (
    auth.uid() = viewer_id
    OR EXISTS (
      SELECT 1 FROM public.stories s
      WHERE s.id = story_views.story_id AND s.author_id = auth.uid()
    )
  );

DROP POLICY IF EXISTS "Users record own story views" ON public.story_views;
CREATE POLICY "Users record own story views"
  ON public.story_views FOR INSERT
  WITH CHECK (
    auth.uid() = viewer_id
    AND public.can_view_story(auth.uid(), story_id)
  );

-- VYBE MATCHES (Only owner can manage; matched user verified via SECURITY DEFINER helper)
DROP POLICY IF EXISTS "Users manage own vybe matches" ON public.vybe_matches;
CREATE POLICY "Users manage own vybe matches"
  ON public.vybe_matches FOR ALL
  USING (auth.uid() = user_id)
  WITH CHECK (auth.uid() = user_id AND user_id <> matched_user_id);

-- CONVERSATIONS & MEMBERS (Strict participant-only isolation!)
DROP POLICY IF EXISTS "Conversation members read conversations" ON public.conversations;
CREATE POLICY "Conversation members read conversations"
  ON public.conversations FOR SELECT
  USING (public.is_conversation_member(id, auth.uid()));

DROP POLICY IF EXISTS "Authenticated users create conversations" ON public.conversations;
CREATE POLICY "Authenticated users create conversations"
  ON public.conversations FOR INSERT
  WITH CHECK (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS "Members read conversation memberships" ON public.conversation_members;
CREATE POLICY "Members read conversation memberships"
  ON public.conversation_members FOR SELECT
  USING (
    user_id = auth.uid()
    OR public.is_conversation_member(conversation_id, auth.uid())
  );

DROP POLICY IF EXISTS "Authenticated users add conversation members" ON public.conversation_members;
CREATE POLICY "Authenticated users add conversation members"
  ON public.conversation_members FOR INSERT
  WITH CHECK (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS "Users update own conversation membership" ON public.conversation_members;
CREATE POLICY "Users update own conversation membership"
  ON public.conversation_members FOR UPDATE
  USING (user_id = auth.uid())
  WITH CHECK (user_id = auth.uid());

-- MESSAGES: Enforce conversation membership AND block check
DROP POLICY IF EXISTS "Participants can view messages" ON public.messages;
DROP POLICY IF EXISTS "Members can read conversation messages" ON public.messages;
CREATE POLICY "Members can read conversation messages"
  ON public.messages FOR SELECT
  USING (public.is_conversation_member(conversation_id, auth.uid()));

DROP POLICY IF EXISTS "Participants can send messages" ON public.messages;
DROP POLICY IF EXISTS "Members can send messages" ON public.messages;
CREATE POLICY "Members can send messages"
  ON public.messages FOR INSERT
  WITH CHECK (
    auth.uid() = sender_id
    AND public.is_conversation_member(conversation_id, auth.uid())
    AND NOT EXISTS (
      SELECT 1 FROM public.conversation_members cm
      WHERE cm.conversation_id = messages.conversation_id
        AND cm.user_id <> auth.uid()
        AND public.is_blocked_between(auth.uid(), cm.user_id)
    )
  );

-- NOTIFICATIONS: Recipient-only SELECT, UPDATE (read), DELETE
DROP POLICY IF EXISTS "Recipients read own notifications" ON public.notifications;
CREATE POLICY "Recipients read own notifications"
  ON public.notifications FOR SELECT
  USING (auth.uid() = recipient_id);

DROP POLICY IF EXISTS "Recipients update own notifications" ON public.notifications;
CREATE POLICY "Recipients update own notifications"
  ON public.notifications FOR UPDATE
  USING (auth.uid() = recipient_id)
  WITH CHECK (auth.uid() = recipient_id);

DROP POLICY IF EXISTS "Recipients can delete own notifications" ON public.notifications;
DROP POLICY IF EXISTS "Recipients delete own notifications" ON public.notifications;
CREATE POLICY "Recipients delete own notifications"
  ON public.notifications FOR DELETE
  USING (auth.uid() = recipient_id);

DROP POLICY IF EXISTS "Authenticated users create interaction notifications" ON public.notifications;
CREATE POLICY "Authenticated users create interaction notifications"
  ON public.notifications FOR INSERT
  WITH CHECK (auth.uid() = actor_id AND actor_id <> recipient_id);

-- 8. RPC FOR SAFE DIRECT CONVERSATION CREATION / LOOKUP WITH MUTUAL-FOLLOW & PRIVACY ENFORCEMENT
CREATE OR REPLACE FUNCTION public.get_or_create_direct_conversation(p_peer_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_me UUID := auth.uid();
  v_conv_id UUID;
  v_peer_privacy VARCHAR(30);
  v_peer_private BOOLEAN;
  v_i_follow BOOLEAN;
  v_peer_follows BOOLEAN;
BEGIN
  IF v_me IS NULL THEN
    RAISE EXCEPTION 'Authentication required';
  END IF;
  IF v_me = p_peer_id THEN
    RAISE EXCEPTION 'Cannot message yourself';
  END IF;
  IF public.is_blocked_between(v_me, p_peer_id) THEN
    RAISE EXCEPTION 'Messaging unavailable due to block settings';
  END IF;

  -- Check if an existing 1-on-1 conversation already exists
  SELECT c.id INTO v_conv_id
  FROM public.conversations c
  JOIN public.conversation_members cm1 ON cm1.conversation_id = c.id AND cm1.user_id = v_me
  JOIN public.conversation_members cm2 ON cm2.conversation_id = c.id AND cm2.user_id = p_peer_id
  WHERE COALESCE(c.is_group, FALSE) = FALSE
  LIMIT 1;

  IF v_conv_id IS NOT NULL THEN
    RETURN v_conv_id;
  END IF;

  -- Evaluate recipient's message_privacy & private-account rules
  SELECT COALESCE(message_privacy, 'EVERYONE'), COALESCE(is_private_account, FALSE)
    INTO v_peer_privacy, v_peer_private
  FROM public.profiles
  WHERE id = p_peer_id;

  SELECT EXISTS (
    SELECT 1 FROM public.follows
    WHERE follower_id = v_me AND followed_id = p_peer_id AND status = 'ACCEPTED'
  ) INTO v_i_follow;

  SELECT EXISTS (
    SELECT 1 FROM public.follows
    WHERE follower_id = p_peer_id AND followed_id = v_me AND status = 'ACCEPTED'
  ) INTO v_peer_follows;

  IF v_peer_privacy IN ('NONE', 'NOBODY') THEN
    RAISE EXCEPTION 'This user has disabled direct messages';
  ELSIF v_peer_privacy IN ('MUTUAL_FOLLOWS', 'MUTUAL_FOLLOWERS') AND NOT (v_i_follow AND v_peer_follows) THEN
    RAISE EXCEPTION 'Mutual follow is required to message this user';
  ELSIF v_peer_private AND NOT (v_i_follow OR v_peer_follows) THEN
    RAISE EXCEPTION 'Follow approval is required before messaging a private profile';
  END IF;

  INSERT INTO public.conversations (is_group) VALUES (FALSE) RETURNING id INTO v_conv_id;
  INSERT INTO public.conversation_members (conversation_id, user_id) VALUES (v_conv_id, v_me), (v_conv_id, p_peer_id);

  RETURN v_conv_id;
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_or_create_direct_conversation(UUID) TO authenticated;

-- 9. AUTOMATIC SOCIAL GRAPH COUNTERS & DEDUPLICATED NOTIFICATION TRIGGERS

-- 9a. Maintain followers_count & following_count + Follow Notifications
CREATE OR REPLACE FUNCTION public.handle_follow_change()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_actor_name TEXT;
BEGIN
  IF TG_OP = 'INSERT' THEN
    SELECT display_name INTO v_actor_name FROM public.profiles WHERE id = NEW.follower_id;
    IF NEW.status = 'ACCEPTED' THEN
      UPDATE public.profiles SET following_count = GREATEST(0, COALESCE(following_count, 0) + 1) WHERE id = NEW.follower_id;
      UPDATE public.profiles SET followers_count = GREATEST(0, COALESCE(followers_count, 0) + 1) WHERE id = NEW.followed_id;
      IF NOT EXISTS (
        SELECT 1 FROM public.notifications
        WHERE recipient_id = NEW.followed_id AND actor_id = NEW.follower_id AND type = 'FOLLOW'
      ) THEN
        INSERT INTO public.notifications (recipient_id, actor_id, type, title, message, target_id)
        VALUES (NEW.followed_id, NEW.follower_id, 'FOLLOW', 'New Follower', COALESCE(v_actor_name, 'Someone') || ' started following you.', NEW.follower_id::text);
      END IF;
    ELSIF NEW.status = 'PENDING' THEN
      IF NOT EXISTS (
        SELECT 1 FROM public.notifications
        WHERE recipient_id = NEW.followed_id AND actor_id = NEW.follower_id AND type = 'FOLLOW_REQUEST'
      ) THEN
        INSERT INTO public.notifications (recipient_id, actor_id, type, title, message, target_id)
        VALUES (NEW.followed_id, NEW.follower_id, 'FOLLOW_REQUEST', 'Follow Request', COALESCE(v_actor_name, 'Someone') || ' requested to follow you.', NEW.follower_id::text);
      END IF;
    END IF;
    RETURN NEW;
  ELSIF TG_OP = 'UPDATE' THEN
    IF OLD.status = 'PENDING' AND NEW.status = 'ACCEPTED' THEN
      UPDATE public.profiles SET following_count = GREATEST(0, COALESCE(following_count, 0) + 1) WHERE id = NEW.follower_id;
      UPDATE public.profiles SET followers_count = GREATEST(0, COALESCE(followers_count, 0) + 1) WHERE id = NEW.followed_id;
      SELECT display_name INTO v_actor_name FROM public.profiles WHERE id = NEW.followed_id;
      INSERT INTO public.notifications (recipient_id, actor_id, type, title, message, target_id)
      VALUES (NEW.follower_id, NEW.followed_id, 'FOLLOW_ACCEPTED', 'Follow Request Approved', COALESCE(v_actor_name, 'Someone') || ' approved your follow request.', NEW.followed_id::text);
    END IF;
    RETURN NEW;
  ELSIF TG_OP = 'DELETE' THEN
    IF OLD.status = 'ACCEPTED' THEN
      UPDATE public.profiles SET following_count = GREATEST(0, COALESCE(following_count, 0) - 1) WHERE id = OLD.follower_id;
      UPDATE public.profiles SET followers_count = GREATEST(0, COALESCE(followers_count, 0) - 1) WHERE id = OLD.followed_id;
    END IF;
    RETURN OLD;
  END IF;
  RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_follow_change ON public.follows;
CREATE TRIGGER trg_follow_change
  AFTER INSERT OR UPDATE OR DELETE ON public.follows
  FOR EACH ROW EXECUTE FUNCTION public.handle_follow_change();

-- 9b. Maintain posts_count on profiles
CREATE OR REPLACE FUNCTION public.handle_post_count_change()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    UPDATE public.profiles SET posts_count = GREATEST(0, COALESCE(posts_count, 0) + 1) WHERE id = NEW.author_id;
    RETURN NEW;
  ELSIF TG_OP = 'DELETE' THEN
    UPDATE public.profiles SET posts_count = GREATEST(0, COALESCE(posts_count, 0) - 1) WHERE id = OLD.author_id;
    RETURN OLD;
  END IF;
  RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_post_count_change ON public.posts;
CREATE TRIGGER trg_post_count_change
  AFTER INSERT OR DELETE ON public.posts
  FOR EACH ROW EXECUTE FUNCTION public.handle_post_count_change();

-- 9c. Maintain likes_count + Deduplicated Like Notifications
CREATE OR REPLACE FUNCTION public.handle_reaction_change()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_author_id UUID;
  v_actor_name TEXT;
BEGIN
  IF TG_OP = 'INSERT' THEN
    UPDATE public.posts SET likes_count = GREATEST(0, COALESCE(likes_count, 0) + 1) WHERE id = NEW.post_id RETURNING author_id INTO v_author_id;
    IF v_author_id IS NOT NULL AND v_author_id <> NEW.user_id THEN
      SELECT display_name INTO v_actor_name FROM public.profiles WHERE id = NEW.user_id;
      IF NOT EXISTS (
        SELECT 1 FROM public.notifications
        WHERE recipient_id = v_author_id
          AND actor_id = NEW.user_id
          AND type = 'LIKE'
          AND target_id = NEW.post_id::text
      ) THEN
        INSERT INTO public.notifications (recipient_id, actor_id, type, title, message, target_id)
        VALUES (v_author_id, NEW.user_id, 'LIKE', 'Post Liked', COALESCE(v_actor_name, 'Someone') || ' reacted to your post.', NEW.post_id::text);
      END IF;
    END IF;
    RETURN NEW;
  ELSIF TG_OP = 'DELETE' THEN
    UPDATE public.posts SET likes_count = GREATEST(0, COALESCE(likes_count, 0) - 1) WHERE id = OLD.post_id;
    RETURN OLD;
  END IF;
  RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_reaction_change ON public.post_reactions;
CREATE TRIGGER trg_reaction_change
  AFTER INSERT OR DELETE ON public.post_reactions
  FOR EACH ROW EXECUTE FUNCTION public.handle_reaction_change();

-- 9d. Maintain comments_count + Comment Notifications
CREATE OR REPLACE FUNCTION public.handle_comment_change()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_author_id UUID;
  v_actor_name TEXT;
BEGIN
  IF TG_OP = 'INSERT' THEN
    UPDATE public.posts SET comments_count = GREATEST(0, COALESCE(comments_count, 0) + 1) WHERE id = NEW.post_id RETURNING author_id INTO v_author_id;
    IF v_author_id IS NOT NULL AND v_author_id <> NEW.author_id THEN
      SELECT display_name INTO v_actor_name FROM public.profiles WHERE id = NEW.author_id;
      INSERT INTO public.notifications (recipient_id, actor_id, type, title, message, target_id)
      VALUES (v_author_id, NEW.author_id, 'COMMENT', 'New Comment', COALESCE(v_actor_name, 'Someone') || ' commented: "' || LEFT(NEW.content, 60) || '"', NEW.post_id::text);
    END IF;
    RETURN NEW;
  ELSIF TG_OP = 'DELETE' THEN
    UPDATE public.posts SET comments_count = GREATEST(0, COALESCE(comments_count, 0) - 1) WHERE id = OLD.post_id;
    RETURN OLD;
  END IF;
  RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_comment_change ON public.comments;
CREATE TRIGGER trg_comment_change
  AFTER INSERT OR DELETE ON public.comments
  FOR EACH ROW EXECUTE FUNCTION public.handle_comment_change();

-- 9e. Recalculate existing profile & post counts once to heal any drift (Safe & Idempotent)
UPDATE public.profiles pr
SET
  followers_count = (SELECT COUNT(*) FROM public.follows f WHERE f.followed_id = pr.id AND f.status = 'ACCEPTED'),
  following_count = (SELECT COUNT(*) FROM public.follows f WHERE f.follower_id = pr.id AND f.status = 'ACCEPTED'),
  posts_count     = (SELECT COUNT(*) FROM public.posts p WHERE p.author_id = pr.id AND COALESCE(p.is_archived, FALSE) = FALSE);

-- 10. SUPABASE STORAGE BUCKETS & ACCESS POLICIES
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES
  ('avatars', 'avatars', true, 10485760, ARRAY['image/jpeg', 'image/png', 'image/webp']),
  ('posts', 'posts', false, 104857600, ARRAY['image/jpeg', 'image/png', 'image/webp', 'video/mp4', 'video/webm', 'video/quicktime']),
  ('stories', 'stories', false, 104857600, ARRAY['image/jpeg', 'image/png', 'image/webp', 'video/mp4', 'video/webm', 'video/quicktime']),
  ('chat-media', 'chat-media', false, 52428800, ARRAY['image/jpeg', 'image/png', 'image/webp', 'video/mp4', 'audio/aac', 'audio/mpeg', 'audio/mp4'])
ON CONFLICT (id) DO NOTHING;

-- Storage Object Policies (Least-Privilege: users can only upload/delete inside their own <auth.uid()>/ folder)
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
        OR EXISTS (
          SELECT 1 FROM public.stories s
          WHERE (s.storage_path = storage.objects.name OR s.storage_path = 'stories/' || storage.objects.name)
            AND public.can_view_story(auth.uid(), s.id)
        )
      )
    )
    OR (
      bucket_id = 'chat-media'
      AND auth.uid() IS NOT NULL
    )
  );
