-- ====================================================================
-- VYBE 3.5: COMMUNITY ZONE, MESSAGING DELETE FOR EVERYONE & PERMISSIONS
-- Product of NR Next | NR Group
-- ====================================================================

-- 1. MESSAGING RLS & RPC FIXES: Permanent Delete for Everyone & Edit Own Messages
DO $$
BEGIN
    DROP POLICY IF EXISTS "Senders can delete own messages" ON public.messages;
    CREATE POLICY "Senders can delete own messages"
        ON public.messages FOR DELETE
        USING (auth.uid() = sender_id);

    DROP POLICY IF EXISTS "Senders can update own messages" ON public.messages;
    CREATE POLICY "Senders can update own messages"
        ON public.messages FOR UPDATE
        USING (auth.uid() = sender_id)
        WITH CHECK (auth.uid() = sender_id);
EXCEPTION WHEN others THEN
    NULL;
END;
$$;

CREATE OR REPLACE FUNCTION public.delete_message_for_everyone(p_message_id UUID)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_conv_id UUID;
BEGIN
    IF auth.uid() IS NULL THEN
        RETURN FALSE;
    END IF;

    DELETE FROM public.messages
    WHERE id = p_message_id
      AND sender_id = auth.uid()
    RETURNING conversation_id INTO v_conv_id;

    IF v_conv_id IS NOT NULL THEN
        UPDATE public.conversations
        SET updated_at = now()
        WHERE id = v_conv_id;
        RETURN TRUE;
    END IF;

    RETURN FALSE;
END;
$$;

GRANT EXECUTE ON FUNCTION public.delete_message_for_everyone(UUID) TO authenticated;

-- 2. COMMUNITIES TABLE
CREATE TABLE IF NOT EXISTS public.communities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    category TEXT NOT NULL DEFAULT 'General',
    avatar_url TEXT,
    banner_url TEXT,
    is_private BOOLEAN DEFAULT FALSE,
    members_count INT DEFAULT 1,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_communities_creator ON public.communities (creator_id);
CREATE INDEX IF NOT EXISTS idx_communities_category ON public.communities (category);
CREATE INDEX IF NOT EXISTS idx_communities_created ON public.communities (created_at DESC);

-- 3. COMMUNITY MEMBERS TABLE
CREATE TABLE IF NOT EXISTS public.community_members (
    community_id UUID NOT NULL REFERENCES public.communities(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    role TEXT NOT NULL DEFAULT 'MEMBER' CHECK (role IN ('ADMIN', 'MODERATOR', 'MEMBER')),
    status TEXT NOT NULL DEFAULT 'ACCEPTED' CHECK (status IN ('ACCEPTED', 'PENDING')),
    joined_at TIMESTAMPTZ DEFAULT now(),
    PRIMARY KEY (community_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_community_members_user ON public.community_members (user_id);

-- 4. COMMUNITY POSTS TABLE
CREATE TABLE IF NOT EXISTS public.community_posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    community_id UUID NOT NULL REFERENCES public.communities(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    content TEXT NOT NULL DEFAULT '',
    media_url TEXT,
    likes_count INT DEFAULT 0,
    comments_count INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_community_posts_community_created ON public.community_posts (community_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_community_posts_author ON public.community_posts (author_id);

-- 5. COMMUNITY POST LIKES TABLE
CREATE TABLE IF NOT EXISTS public.community_post_likes (
    post_id UUID NOT NULL REFERENCES public.community_posts(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT now(),
    PRIMARY KEY (post_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_community_post_likes_user ON public.community_post_likes (user_id);

-- 6. COMMUNITY POST COMMENTS TABLE
CREATE TABLE IF NOT EXISTS public.community_post_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES public.community_posts(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_community_comments_post ON public.community_post_comments (post_id, created_at ASC);

-- 7. COUNTER MAINTENANCE TRIGGERS FOR COMMUNITIES
CREATE OR REPLACE FUNCTION public.sync_community_members_count()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_comm_id UUID := COALESCE(NEW.community_id, OLD.community_id);
BEGIN
    UPDATE public.communities
    SET members_count = GREATEST(1, (
        SELECT COUNT(*)::INT
        FROM public.community_members
        WHERE community_id = v_comm_id AND status = 'ACCEPTED'
    )),
    updated_at = now()
    WHERE id = v_comm_id;
    RETURN COALESCE(NEW, OLD);
END;
$$;

DROP TRIGGER IF EXISTS trg_sync_community_members_count ON public.community_members;
CREATE TRIGGER trg_sync_community_members_count
AFTER INSERT OR UPDATE OR DELETE ON public.community_members
FOR EACH ROW EXECUTE FUNCTION public.sync_community_members_count();

CREATE OR REPLACE FUNCTION public.sync_community_post_likes_count()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_post_id UUID := COALESCE(NEW.post_id, OLD.post_id);
BEGIN
    UPDATE public.community_posts
    SET likes_count = (
        SELECT COUNT(*)::INT FROM public.community_post_likes WHERE post_id = v_post_id
    )
    WHERE id = v_post_id;
    RETURN COALESCE(NEW, OLD);
END;
$$;

DROP TRIGGER IF EXISTS trg_sync_community_post_likes_count ON public.community_post_likes;
CREATE TRIGGER trg_sync_community_post_likes_count
AFTER INSERT OR DELETE ON public.community_post_likes
FOR EACH ROW EXECUTE FUNCTION public.sync_community_post_likes_count();

CREATE OR REPLACE FUNCTION public.sync_community_post_comments_count()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_post_id UUID := COALESCE(NEW.post_id, OLD.post_id);
BEGIN
    UPDATE public.community_posts
    SET comments_count = (
        SELECT COUNT(*)::INT FROM public.community_post_comments WHERE post_id = v_post_id
    )
    WHERE id = v_post_id;
    RETURN COALESCE(NEW, OLD);
END;
$$;

DROP TRIGGER IF EXISTS trg_sync_community_post_comments_count ON public.community_post_comments;
CREATE TRIGGER trg_sync_community_post_comments_count
AFTER INSERT OR DELETE ON public.community_post_comments
FOR EACH ROW EXECUTE FUNCTION public.sync_community_post_comments_count();

-- 8. ENABLE ROW LEVEL SECURITY
ALTER TABLE public.communities ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.community_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.community_posts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.community_post_likes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.community_post_comments ENABLE ROW LEVEL SECURITY;

-- 9. RLS POLICIES FOR COMMUNITIES, MEMBERSHIP, POSTS, LIKES & COMMENTS
DO $$
BEGIN
    DROP POLICY IF EXISTS "Public can view active communities" ON public.communities;
    CREATE POLICY "Public can view active communities"
        ON public.communities FOR SELECT
        USING (is_private = false OR auth.uid() = creator_id OR EXISTS (
            SELECT 1 FROM public.community_members cm
            WHERE cm.community_id = communities.id AND cm.user_id = auth.uid()
        ));

    DROP POLICY IF EXISTS "Authenticated users can create communities" ON public.communities;
    CREATE POLICY "Authenticated users can create communities"
        ON public.communities FOR INSERT
        WITH CHECK (auth.uid() = creator_id);

    DROP POLICY IF EXISTS "Community creators can update community" ON public.communities;
    CREATE POLICY "Community creators can update community"
        ON public.communities FOR UPDATE
        USING (auth.uid() = creator_id);

    DROP POLICY IF EXISTS "Community creators can delete community" ON public.communities;
    CREATE POLICY "Community creators can delete community"
        ON public.communities FOR DELETE
        USING (auth.uid() = creator_id);

    -- Community Members Policies
    DROP POLICY IF EXISTS "Members can view community members" ON public.community_members;
    CREATE POLICY "Members can view community members"
        ON public.community_members FOR SELECT
        USING (true);

    DROP POLICY IF EXISTS "Users can join communities" ON public.community_members;
    CREATE POLICY "Users can join communities"
        ON public.community_members FOR INSERT
        WITH CHECK (auth.uid() = user_id OR EXISTS (
            SELECT 1 FROM public.communities c WHERE c.id = community_members.community_id AND c.creator_id = auth.uid()
        ));

    DROP POLICY IF EXISTS "Admins can update community members" ON public.community_members;
    CREATE POLICY "Admins can update community members"
        ON public.community_members FOR UPDATE
        USING (EXISTS (
            SELECT 1 FROM public.communities c WHERE c.id = community_members.community_id AND c.creator_id = auth.uid()
        ) OR EXISTS (
            SELECT 1 FROM public.community_members admin_cm
            WHERE admin_cm.community_id = community_members.community_id
              AND admin_cm.user_id = auth.uid()
              AND admin_cm.role IN ('ADMIN', 'MODERATOR')
              AND admin_cm.status = 'ACCEPTED'
        ));

    DROP POLICY IF EXISTS "Users can leave or admins can manage members" ON public.community_members;
    CREATE POLICY "Users can leave or admins can manage members"
        ON public.community_members FOR DELETE
        USING (auth.uid() = user_id OR EXISTS (
            SELECT 1 FROM public.communities c WHERE c.id = community_members.community_id AND c.creator_id = auth.uid()
        ) OR EXISTS (
            SELECT 1 FROM public.community_members admin_cm
            WHERE admin_cm.community_id = community_members.community_id
              AND admin_cm.user_id = auth.uid()
              AND admin_cm.role IN ('ADMIN', 'MODERATOR')
              AND admin_cm.status = 'ACCEPTED'
        ));

    -- Community Posts Policies
    DROP POLICY IF EXISTS "Members can view community posts" ON public.community_posts;
    CREATE POLICY "Members can view community posts"
        ON public.community_posts FOR SELECT
        USING (EXISTS (
            SELECT 1 FROM public.communities c
            WHERE c.id = community_posts.community_id
              AND (c.is_private = false OR c.creator_id = auth.uid() OR EXISTS (
                  SELECT 1 FROM public.community_members cm
                  WHERE cm.community_id = c.id AND cm.user_id = auth.uid() AND cm.status = 'ACCEPTED'
              ))
        ));

    DROP POLICY IF EXISTS "Users can create community posts" ON public.community_posts;
    CREATE POLICY "Users can create community posts"
        ON public.community_posts FOR INSERT
        WITH CHECK (auth.uid() = author_id AND EXISTS (
            SELECT 1 FROM public.communities c
            WHERE c.id = community_posts.community_id
              AND (c.is_private = false OR c.creator_id = auth.uid() OR EXISTS (
                  SELECT 1 FROM public.community_members cm
                  WHERE cm.community_id = c.id AND cm.user_id = auth.uid() AND cm.status = 'ACCEPTED'
              ))
        ));

    DROP POLICY IF EXISTS "Authors can update own community posts" ON public.community_posts;
    CREATE POLICY "Authors can update own community posts"
        ON public.community_posts FOR UPDATE
        USING (auth.uid() = author_id)
        WITH CHECK (auth.uid() = author_id);

    DROP POLICY IF EXISTS "Authors and admins can delete community posts" ON public.community_posts;
    CREATE POLICY "Authors and admins can delete community posts"
        ON public.community_posts FOR DELETE
        USING (auth.uid() = author_id OR EXISTS (
            SELECT 1 FROM public.communities c WHERE c.id = community_posts.community_id AND c.creator_id = auth.uid()
        ) OR EXISTS (
            SELECT 1 FROM public.community_members admin_cm
            WHERE admin_cm.community_id = community_posts.community_id
              AND admin_cm.user_id = auth.uid()
              AND admin_cm.role IN ('ADMIN', 'MODERATOR')
              AND admin_cm.status = 'ACCEPTED'
        ));

    -- Community Post Likes Policies
    DROP POLICY IF EXISTS "Public can view community post likes" ON public.community_post_likes;
    CREATE POLICY "Public can view community post likes"
        ON public.community_post_likes FOR SELECT
        USING (true);

    DROP POLICY IF EXISTS "Users can like community posts" ON public.community_post_likes;
    CREATE POLICY "Users can like community posts"
        ON public.community_post_likes FOR INSERT
        WITH CHECK (auth.uid() = user_id);

    DROP POLICY IF EXISTS "Users can unlike community posts" ON public.community_post_likes;
    CREATE POLICY "Users can unlike community posts"
        ON public.community_post_likes FOR DELETE
        USING (auth.uid() = user_id);

    -- Community Post Comments Policies
    DROP POLICY IF EXISTS "Public can view community post comments" ON public.community_post_comments;
    CREATE POLICY "Public can view community post comments"
        ON public.community_post_comments FOR SELECT
        USING (true);

    DROP POLICY IF EXISTS "Users can create comments on community posts" ON public.community_post_comments;
    CREATE POLICY "Users can create comments on community posts"
        ON public.community_post_comments FOR INSERT
        WITH CHECK (auth.uid() = author_id);

    DROP POLICY IF EXISTS "Authors can delete own comments" ON public.community_post_comments;
    CREATE POLICY "Authors can delete own comments"
        ON public.community_post_comments FOR DELETE
        USING (auth.uid() = author_id OR EXISTS (
            SELECT 1 FROM public.community_posts cp
            JOIN public.communities c ON c.id = cp.community_id
            WHERE cp.id = community_post_comments.post_id AND c.creator_id = auth.uid()
        ));
EXCEPTION WHEN others THEN
    NULL;
END;
$$;

-- 10. REALTIME PUBLICATION REGISTRATION
DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['messages', 'communities', 'community_members', 'community_posts', 'community_post_comments']
    LOOP
        BEGIN
            IF NOT EXISTS (
                SELECT 1 FROM pg_publication_tables
                WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = t
            ) THEN
                EXECUTE format('ALTER PUBLICATION supabase_realtime ADD TABLE public.%I', t);
            END IF;
        EXCEPTION WHEN others THEN
            NULL;
        END;
    END LOOP;
END;
$$;
