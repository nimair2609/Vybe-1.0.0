-- ====================================================================
-- VYBE 3.0 SUPABASE STORAGE BUCKETS SETUP
-- Configures storage buckets and access policies for avatars, 5s banners,
-- post media, stories, and verified business documents.
-- ====================================================================

-- 1. Create Buckets
INSERT INTO storage.buckets (id, name, public)
VALUES 
    ('avatars', 'avatars', true),
    ('banners', 'banners', true),
    ('posts', 'posts', true),
    ('stories', 'stories', true),
    ('raw_media', 'raw_media', true),
    ('business_docs', 'business_docs', false) -- Private bucket for verification documents
ON CONFLICT (id) DO NOTHING;

-- 2. Storage Policies for Public Buckets
-- Public Read
CREATE POLICY "Public media access" ON storage.objects
FOR SELECT USING (bucket_id IN ('avatars', 'banners', 'posts', 'stories', 'raw_media'));

-- Authenticated Users can upload to avatars
CREATE POLICY "Users can upload their own avatar" ON storage.objects
FOR INSERT WITH CHECK (
    bucket_id = 'avatars' 
    AND auth.role() = 'authenticated'
);

-- Authenticated Users can upload 5-second video/still banners
CREATE POLICY "Users can upload profile banner" ON storage.objects
FOR INSERT WITH CHECK (
    bucket_id = 'banners' 
    AND auth.role() = 'authenticated'
);

-- Authenticated Users can upload posts & stories
CREATE POLICY "Users can upload post media" ON storage.objects
FOR INSERT WITH CHECK (
    bucket_id IN ('posts', 'stories', 'raw_media') 
    AND auth.role() = 'authenticated'
);

-- Business Verification Documents: Owner upload, Admin read
CREATE POLICY "Business owners can upload verification docs" ON storage.objects
FOR INSERT WITH CHECK (
    bucket_id = 'business_docs' 
    AND auth.role() = 'authenticated'
);

CREATE POLICY "Admins can view business verification docs" ON storage.objects
FOR SELECT USING (
    bucket_id = 'business_docs' 
    AND EXISTS (SELECT 1 FROM public.profiles WHERE id = auth.uid() AND role = 'ADMIN')
);
