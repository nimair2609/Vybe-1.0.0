-- ====================================================================
-- VYBE 3.0: USER DEVICE FCM TOKENS & PUSH NOTIFICATIONS TABLE
-- Product of NR Next | NR Group
-- ====================================================================

CREATE TABLE IF NOT EXISTS public.user_device_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    fcm_token TEXT NOT NULL,
    device_model TEXT,
    platform VARCHAR(20) DEFAULT 'android',
    app_version VARCHAR(20) DEFAULT '1.0.0',
    is_active BOOLEAN DEFAULT TRUE,
    last_seen_at TIMESTAMPTZ DEFAULT NOW(),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uq_user_device_token UNIQUE (user_id, fcm_token)
);

-- Fast lookup indexes
CREATE INDEX IF NOT EXISTS idx_device_tokens_user 
    ON public.user_device_tokens (user_id) 
    WHERE is_active = TRUE;

CREATE INDEX IF NOT EXISTS idx_device_tokens_token 
    ON public.user_device_tokens (fcm_token);

-- Enable RLS
ALTER TABLE public.user_device_tokens ENABLE ROW LEVEL SECURITY;

-- 1. Users can only select their own registered tokens
CREATE POLICY "Users can view own device tokens" 
    ON public.user_device_tokens FOR SELECT 
    USING (auth.uid() = user_id);

-- 2. Users can only register tokens for themselves
CREATE POLICY "Users can insert own device tokens" 
    ON public.user_device_tokens FOR INSERT 
    WITH CHECK (auth.uid() = user_id);

-- 3. Users can update their own tokens (e.g. deactivate on logout or refresh timestamp)
CREATE POLICY "Users can update own device tokens" 
    ON public.user_device_tokens FOR UPDATE 
    USING (auth.uid() = user_id);

-- 4. Users can delete their own tokens
CREATE POLICY "Users can delete own device tokens" 
    ON public.user_device_tokens FOR DELETE 
    USING (auth.uid() = user_id);
