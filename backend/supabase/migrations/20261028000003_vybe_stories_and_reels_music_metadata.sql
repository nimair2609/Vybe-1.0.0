-- ============================================================================
-- VYBE Migration 20261028000003: Stories & Reels Licensed Music Metadata
-- Additive, non-destructive upgrade. Preserves all existing Stories and Posts/Reels.
-- Stores provider track IDs & trim/volume metadata without duplicating audio files in Storage.
-- ============================================================================

ALTER TABLE public.stories
  ADD COLUMN IF NOT EXISTS music_track_id TEXT,
  ADD COLUMN IF NOT EXISTS music_provider TEXT,
  ADD COLUMN IF NOT EXISTS music_artist TEXT,
  ADD COLUMN IF NOT EXISTS music_artwork_url TEXT,
  ADD COLUMN IF NOT EXISTS music_stream_url TEXT,
  ADD COLUMN IF NOT EXISTS music_start_ms INTEGER,
  ADD COLUMN IF NOT EXISTS music_duration_ms INTEGER,
  ADD COLUMN IF NOT EXISTS music_volume DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS original_audio_volume DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS music_metadata JSONB;

ALTER TABLE public.posts
  ADD COLUMN IF NOT EXISTS music_track_id TEXT,
  ADD COLUMN IF NOT EXISTS music_provider TEXT,
  ADD COLUMN IF NOT EXISTS music_artist TEXT,
  ADD COLUMN IF NOT EXISTS music_artwork_url TEXT,
  ADD COLUMN IF NOT EXISTS music_stream_url TEXT,
  ADD COLUMN IF NOT EXISTS music_start_ms INTEGER,
  ADD COLUMN IF NOT EXISTS music_duration_ms INTEGER,
  ADD COLUMN IF NOT EXISTS music_volume DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS original_audio_volume DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS music_metadata JSONB;

CREATE INDEX IF NOT EXISTS idx_posts_music_track_id
  ON public.posts (music_track_id)
  WHERE music_track_id IS NOT NULL AND is_archived = FALSE;

CREATE INDEX IF NOT EXISTS idx_stories_music_track_id
  ON public.stories (music_track_id)
  WHERE music_track_id IS NOT NULL;
