-- ====================================================================
-- VYBE 3.0 AUTOMATIC PROFILE & SETTINGS INITIALIZATION TRIGGER
-- Run this in your Supabase SQL Editor (Dashboard -> SQL Editor)
-- Automatically provisions public.profiles and public.user_settings
-- whenever a new user registers through Supabase Auth.
-- ====================================================================

CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger AS $$
DECLARE
  extracted_username TEXT;
  extracted_name TEXT;
  assigned_role public.user_role_enum;
BEGIN
  -- Extract or derive initial profile data
  extracted_username := COALESCE(
    NEW.raw_user_meta_data->>'username',
    LOWER(REGEXP_REPLACE(SPLIT_PART(NEW.email, '@', 1), '[^a-zA-Z0-9_]', '', 'g'))
  );
  extracted_name := COALESCE(
    NEW.raw_user_meta_data->>'display_name',
    SPLIT_PART(NEW.email, '@', 1)
  );
  
  BEGIN
    assigned_role := COALESCE((NEW.raw_user_meta_data->>'role')::public.user_role_enum, 'USER'::public.user_role_enum);
  EXCEPTION WHEN OTHERS THEN
    assigned_role := 'USER'::public.user_role_enum;
  END;

  -- 1. Insert Profile
  INSERT INTO public.profiles (
    id,
    username,
    display_name,
    email,
    role
  ) VALUES (
    NEW.id,
    extracted_username,
    extracted_name,
    NEW.email,
    assigned_role
  )
  ON CONFLICT (id) DO NOTHING;

  -- 2. Insert Default User Settings
  INSERT INTO public.user_settings (user_id)
  VALUES (NEW.id)
  ON CONFLICT (user_id) DO NOTHING;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Drop trigger if already exists and recreate
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;

CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();
