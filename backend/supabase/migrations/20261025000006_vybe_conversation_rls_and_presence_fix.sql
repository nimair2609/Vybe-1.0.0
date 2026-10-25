-- ============================================================================
-- VYBE CONVERSATION RLS FIX, REEL & PRESENCE ENHANCEMENTS
-- Migration: 20261025000006_vybe_conversation_rls_and_presence_fix.sql
-- Standalone & Idempotent: Fixes RLS on public.conversations so creators can
-- read their newly created conversations during RETURNING *, and aligns RPCs.
-- ============================================================================

-- 1. Ensure created_by column exists and has proper reference
ALTER TABLE public.conversations
  ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES public.profiles(id);

-- 2. Update Conversations RLS Policy
-- When inserting a conversation, PostgREST requests RETURNING * which runs SELECT.
-- By checking (created_by = auth.uid() OR is_conversation_member(id, auth.uid())),
-- the creator can read the row immediately upon creation before members are added.
DROP POLICY IF EXISTS "Conversation members read conversations" ON public.conversations;
CREATE POLICY "Conversation members read conversations"
  ON public.conversations FOR SELECT
  USING (
    created_by = auth.uid()
    OR public.is_conversation_member(id, auth.uid())
  );

DROP POLICY IF EXISTS "Authenticated users create conversations" ON public.conversations;
CREATE POLICY "Authenticated users create conversations"
  ON public.conversations FOR INSERT
  WITH CHECK (auth.uid() IS NOT NULL);

-- 3. Robust RPC to get or create direct conversations supporting both p_peer_id & p_other_user_id
CREATE OR REPLACE FUNCTION public.get_or_create_direct_conversation(
  p_peer_id UUID DEFAULT NULL,
  p_other_user_id UUID DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_me UUID := auth.uid();
  v_target_peer UUID := COALESCE(p_peer_id, p_other_user_id);
  v_conv_id UUID;
  v_peer_privacy VARCHAR(30);
  v_peer_private BOOLEAN;
  v_is_following BOOLEAN;
BEGIN
  IF v_me IS NULL THEN
    RAISE EXCEPTION 'Authentication required';
  END IF;

  IF v_target_peer IS NULL THEN
    RAISE EXCEPTION 'Target user ID is required';
  END IF;

  IF v_me = v_target_peer THEN
    RAISE EXCEPTION 'Cannot start a direct conversation with yourself';
  END IF;

  -- Block check
  IF EXISTS (
    SELECT 1 FROM public.blocks
    WHERE (blocker_id = v_me AND blocked_id = v_target_peer)
       OR (blocker_id = v_target_peer AND blocked_id = v_me)
  ) THEN
    RAISE EXCEPTION 'Cannot message this user due to block restrictions';
  END IF;

  -- Check existing direct conversation between the two users
  SELECT c.id INTO v_conv_id
  FROM public.conversations c
  JOIN public.conversation_members cm1 ON cm1.conversation_id = c.id AND cm1.user_id = v_me
  JOIN public.conversation_members cm2 ON cm2.conversation_id = c.id AND cm2.user_id = v_target_peer
  WHERE c.is_group = FALSE
  LIMIT 1;

  IF v_conv_id IS NOT NULL THEN
    RETURN v_conv_id;
  END IF;

  -- Privacy validation
  SELECT message_privacy, is_private_account
  INTO v_peer_privacy, v_peer_private
  FROM public.profiles
  WHERE id = v_target_peer;

  IF v_peer_privacy = 'NO_ONE' THEN
    RAISE EXCEPTION 'This user has disabled direct messaging';
  END IF;

  SELECT EXISTS (
    SELECT 1 FROM public.follows
    WHERE follower_id = v_me AND followed_id = v_target_peer AND status = 'ACCEPTED'
  ) INTO v_is_following;

  IF v_peer_privacy = 'FOLLOWING_ONLY' AND NOT v_is_following THEN
    RAISE EXCEPTION 'This user only accepts messages from accounts they follow or approve';
  END IF;

  -- Create conversation with created_by populated
  INSERT INTO public.conversations (is_group, created_by)
  VALUES (FALSE, v_me)
  RETURNING id INTO v_conv_id;

  -- Add both users as conversation members
  INSERT INTO public.conversation_members (conversation_id, user_id)
  VALUES (v_conv_id, v_me), (v_conv_id, v_target_peer)
  ON CONFLICT DO NOTHING;

  RETURN v_conv_id;
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_or_create_direct_conversation(UUID, UUID) TO authenticated;
