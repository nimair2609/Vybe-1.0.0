-- ====================================================================
-- VYBE 3.5: COMMUNITY ZONE, MESSAGING DELETE FOR EVERYONE & PERMISSIONS
-- Product of NR Next | NR Group
-- ====================================================================

-- 1. MESSAGING RLS FIXES: Enable senders to delete and update their own messages for everyone
DO $$
BEGIN
    DROP POLICY IF EXISTS "Senders can delete own messages" ON public.messages;
    CREATE POLICY "Senders can delete own messages"
        ON public.messages FOR DELETE
        USING (auth.uid() = sender_id);

    DROP POLICY IF EXISTS "Senders can update own messages" ON public.messages;
    CREATE POLICY "Senders can update own messages"
        ON public.messages FOR UPDATE
        USING (auth.uid() = sender_id);
EXCEPTION WHEN others THEN
    NULL;
END;
$$;

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

-- 5. COMMUNITY POST COMMENTS TABLE
CREATE TABLE IF NOT EXISTS public.community_post_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES public.community_posts(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_community_comments_post ON public.community_post_comments (post_id, created_at ASC);

-- 6. ENABLE ROW LEVEL SECURITY
ALTER TABLE public.communities ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.community_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.community_posts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.community_post_comments ENABLE ROW LEVEL SECURITY;

-- 7. RLS POLICIES FOR COMMUNITIES
DO $$
BEGIN
    DROP POLICY IF EXISTS "Public can view active communities" ON public.communities;
    CREATE POLICY "Public can view active communities"
        ON public.communities FOR SELECT
        USING (is_private = false OR auth.uid() = creator_id OR EXISTS (
            SELECT 1 FROM public.community_members cm
            WHERE cm.community_id = communities.id AND cm.user_id = auth.uid() AND cm.status = 'ACCEPTED'
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
        WITH CHECK (auth.uid() = user_id);

    DROP POLICY IF EXISTS "Users can leave or admins can manage members" ON public.community_members;
    CREATE POLICY "Users can leave or admins can manage members"
        ON public.community_members FOR DELETE
        USING (auth.uid() = user_id OR EXISTS (
            SELECT 1 FROM public.communities c WHERE c.id = community_members.community_id AND c.creator_id = auth.uid()
        ));

    -- Community Posts Policies
    DROP POLICY IF EXISTS "Members can view community posts" ON public.community_posts;
    CREATE POLICY "Members can view community posts"
        ON public.community_posts FOR SELECT
        USING (true);

    DROP POLICY IF EXISTS "Users can create community posts" ON public.community_posts;
    CREATE POLICY "Users can create community posts"
        ON public.community_posts FOR INSERT
        WITH CHECK (auth.uid() = author_id);

    DROP POLICY IF EXISTS "Authors can update own community posts" ON public.community_posts;
    CREATE POLICY "Authors can update own community posts"
        ON public.community_posts FOR UPDATE
        USING (auth.uid() = author_id);

    DROP POLICY IF EXISTS "Authors and admins can delete community posts" ON public.community_posts;
    CREATE POLICY "Authors and admins can delete community posts"
        ON public.community_posts FOR DELETE
        USING (auth.uid() = author_id OR EXISTS (
            SELECT 1 FROM public.communities c WHERE c.id = community_posts.community_id AND c.creator_id = auth.uid()
        ));

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
        USING (auth.uid() = author_id);
EXCEPTION WHEN others THEN
    NULL;
END;
$$;
