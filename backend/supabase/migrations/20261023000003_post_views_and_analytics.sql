-- ====================================================================
-- VYBE 3.0 MIGRATION: POST VIEWS & PERSONAL POST ANALYTICS
-- Adds views_count to public.posts, tracks unique/repeat post views,
-- and provides atomic view increment RPC for cross-device feeds.
-- ====================================================================

-- 1. Add views_count to posts table
DO $$ 
BEGIN
    IF NOT EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_schema = 'public' 
          AND table_name = 'posts' 
          AND column_name = 'views_count'
    ) THEN
        ALTER TABLE public.posts ADD COLUMN views_count INT DEFAULT 0;
    END IF;
END $$;

-- 2. Post Views Log Table (tracks impressions per user/anonymous viewer)
CREATE TABLE IF NOT EXISTS public.post_views (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES public.posts(id) ON DELETE CASCADE,
    viewer_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Index for analytics aggregation
CREATE INDEX IF NOT EXISTS idx_post_views_post_id ON public.post_views(post_id);
CREATE INDEX IF NOT EXISTS idx_post_views_created_at ON public.post_views(created_at);

-- 3. Atomic Increment Function
CREATE OR REPLACE FUNCTION public.increment_post_view(p_post_id UUID, p_viewer_id UUID DEFAULT NULL)
RETURNS INT
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    new_views INT;
BEGIN
    -- Record impression in post_views
    INSERT INTO public.post_views (post_id, viewer_id)
    VALUES (p_post_id, p_viewer_id);

    -- Atomically update views_count on the post
    UPDATE public.posts
    SET views_count = COALESCE(views_count, 0) + 1
    WHERE id = p_post_id
    RETURNING views_count INTO new_views;

    RETURN COALESCE(new_views, 1);
END;
$$;

-- 4. Enable Row Level Security on post_views
ALTER TABLE public.post_views ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Anyone can record post views"
ON public.post_views
FOR INSERT
WITH CHECK (true);

CREATE POLICY "Users can view impressions on their own posts"
ON public.post_views
FOR SELECT
USING (
    EXISTS (
        SELECT 1 FROM public.posts
        WHERE public.posts.id = public.post_views.post_id
          AND public.posts.author_id = auth.uid()
    )
);

-- 5. RLS Policy for Post Deletion by Author or Admin
DROP POLICY IF EXISTS "Authors or Admins can delete posts" ON public.posts;
CREATE POLICY "Authors or Admins can delete posts"
ON public.posts
FOR DELETE
USING (
    author_id = auth.uid() 
    OR EXISTS (SELECT 1 FROM public.profiles WHERE id = auth.uid() AND role = 'ADMIN')
);
