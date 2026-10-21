-- ====================================================================
-- VYBE 3.0 AUTOMATIC PROFILE & SETTINGS INITIALIZATION TRIGGER
-- Run this in your Supabase SQL Editor (Dashboard -> SQL Editor)
-- Automatically provisions public.profiles and public.user_settings
-- whenever a new user registers through Supabase Auth, saving phone,
-- names, date of birth, and teen protection flags immediately.
-- ====================================================================

CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger AS $$
DECLARE
  extracted_username TEXT;
  extracted_display_name TEXT;
  extracted_first_name TEXT;
  extracted_last_name TEXT;
  extracted_phone TEXT;
  extracted_dob DATE;
  extracted_age INT;
  extracted_under_18 BOOLEAN;
  assigned_role public.user_role_enum;
BEGIN
  -- Extract user metadata passed from Android registration
  extracted_username := COALESCE(
    NEW.raw_user_meta_data->>'username',
    LOWER(REGEXP_REPLACE(SPLIT_PART(NEW.email, '@', 1), '[^a-zA-Z0-9_]', '', 'g'))
  );
  extracted_display_name := COALESCE(
    NEW.raw_user_meta_data->>'display_name',
    SPLIT_PART(NEW.email, '@', 1)
  );
  extracted_first_name := NEW.raw_user_meta_data->>'first_name';
  extracted_last_name := NEW.raw_user_meta_data->>'last_name';
  extracted_phone := COALESCE(NEW.phone, NEW.raw_user_meta_data->>'phone');
  
  BEGIN
    extracted_dob := (NEW.raw_user_meta_data->>'date_of_birth')::DATE;
    extracted_age := DATE_PART('year', AGE(extracted_dob))::INT;
    extracted_under_18 := (extracted_age < 18);
  EXCEPTION WHEN OTHERS THEN
    extracted_dob := NULL;
    extracted_age := 20;
    extracted_under_18 := FALSE;
  END;

  BEGIN
    assigned_role := COALESCE((NEW.raw_user_meta_data->>'role')::public.user_role_enum, 'USER'::public.user_role_enum);
  EXCEPTION WHEN OTHERS THEN
    assigned_role := 'USER'::public.user_role_enum;
  END;

  -- 1. Insert Profile with Phone and Demographics
  INSERT INTO public.profiles (
    id,
    username,
    display_name,
    first_name,
    last_name,
    phone,
    email,
    date_of_birth,
    age,
    is_under_18,
    role
  ) VALUES (
    NEW.id,
    extracted_username,
    extracted_display_name,
    extracted_first_name,
    extracted_last_name,
    extracted_phone,
    NEW.email,
    extracted_dob,
    extracted_age,
    extracted_under_18,
    assigned_role
  )
  ON CONFLICT (id) DO UPDATE SET
    phone = EXCLUDED.phone,
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    display_name = EXCLUDED.display_name;

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
