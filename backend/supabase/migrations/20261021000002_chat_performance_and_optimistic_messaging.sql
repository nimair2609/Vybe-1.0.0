-- ====================================================================
-- VYBE 3.0: CHAT PERFORMANCE, INDEXES & REALTIME OPTIMIZATION
-- Product of NR Next | NR Group
-- ====================================================================

-- 1. HIGH-PERFORMANCE CHAT & MESSAGING INDEXES
-- Eliminates table scans during conversation opening and message delivery
CREATE INDEX IF NOT EXISTS idx_messages_conversation_created 
    ON public.messages (conversation_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_messages_sender_created 
    ON public.messages (sender_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_conversation_members_lookup 
    ON public.conversation_members (user_id, conversation_id);

CREATE INDEX IF NOT EXISTS idx_conversation_members_conv 
    ON public.conversation_members (conversation_id, user_id);

-- 2. SOCIAL, FOLLOWS & MODERATION INDEXES
CREATE INDEX IF NOT EXISTS idx_follows_follower 
    ON public.follows (follower_id, followed_id);

CREATE INDEX IF NOT EXISTS idx_follows_followed 
    ON public.follows (followed_id, follower_id);

CREATE INDEX IF NOT EXISTS idx_blocks_blocker 
    ON public.blocks (blocker_id, blocked_id);

CREATE INDEX IF NOT EXISTS idx_blocks_blocked 
    ON public.blocks (blocked_id, blocker_id);

CREATE INDEX IF NOT EXISTS idx_reports_target 
    ON public.reports (target_type, target_id, status);

CREATE INDEX IF NOT EXISTS idx_profiles_username_lower 
    ON public.profiles (LOWER(username));

CREATE INDEX IF NOT EXISTS idx_profiles_phone 
    ON public.profiles (phone);

-- 3. NOTIFICATION & COMMENT PERFORMANCE INDEXES
CREATE INDEX IF NOT EXISTS idx_notifications_recipient_unread 
    ON public.notifications (recipient_id, is_read, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_comments_post_created 
    ON public.comments (post_id, created_at ASC);

CREATE INDEX IF NOT EXISTS idx_posts_author_created 
    ON public.posts (author_id, created_at DESC);

-- 4. ENABLE SUPABASE REALTIME ON HIGH-SPEED TABLES
-- Enables sub-millisecond event streaming for messaging, comments, and in-app notifications
DO $$
BEGIN
    BEGIN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.messages;
    EXCEPTION WHEN duplicate_object THEN
        -- table already in publication
    END;
    BEGIN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.notifications;
    EXCEPTION WHEN duplicate_object THEN
        -- table already in publication
    END;
    BEGIN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.comments;
    EXCEPTION WHEN duplicate_object THEN
        -- table already in publication
    END;
END
$$;

-- 5. RLS POLICIES FOR BLOCKS, REPORTS AND FOLLOWS
ALTER TABLE public.blocks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reports ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view their own blocks" 
    ON public.blocks FOR SELECT 
    USING (auth.uid() = blocker_id);

CREATE POLICY "Users can insert blocks" 
    ON public.blocks FOR INSERT 
    WITH CHECK (auth.uid() = blocker_id);

CREATE POLICY "Users can delete own blocks" 
    ON public.blocks FOR DELETE 
    USING (auth.uid() = blocker_id);

CREATE POLICY "Users can view own reports" 
    ON public.reports FOR SELECT 
    USING (auth.uid() = reporter_id);

CREATE POLICY "Users can submit reports" 
    ON public.reports FOR INSERT 
    WITH CHECK (auth.uid() = reporter_id);

-- Optimized notification delete policy (only recipient can delete their notifications)
CREATE POLICY "Recipients can delete own notifications"
    ON public.notifications FOR DELETE
    USING (auth.uid() = recipient_id);

-- Follows policies
CREATE POLICY "Follows are viewable by everyone" 
    ON public.follows FOR SELECT 
    USING (true);

CREATE POLICY "Users can follow" 
    ON public.follows FOR INSERT 
    WITH CHECK (auth.uid() = follower_id);

CREATE POLICY "Users can unfollow" 
    ON public.follows FOR DELETE 
    USING (auth.uid() = follower_id);
