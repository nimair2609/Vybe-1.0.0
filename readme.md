# VYBE — Next-Generation Social Network (Android)

**VYBE** is a native Android social platform built with **Kotlin**, **Jetpack Compose (Material 3)**, and a **Supabase** cloud backend (Auth, Postgres with Row-Level Security, Storage, Realtime, and RPCs). It combines expressive mood-driven feeds, short-form Reels, 24-hour Stories with VYBE Matches privacy, real-time Direct Messaging, Voice & Video Calls, Shareable Unload Rooms (`VYB-XXXX-XXXX`), Crossed Paths discovery, Double Take prompts, Time Capsules, Social Predictions, Spiritual Mode, and Verified Business Shops.

---

## Key Features & Recent Upgrades

### 1. Multi-Identifier Authentication, Mandatory Registration & Dual OTP Verification
- **Sign In with Any Identifier**: Log in seamlessly with an **Email Address**, **Phone Number** (E.164 normalized), or **`@username` / VYBE User ID** (resolved server-side via a `SECURITY DEFINER` RPC without exposing private emails or phone numbers to unauthenticated clients).
- **Mandatory Registration Validation**: Enforces First Name, Last Name, unique `@username` (live debounced availability check), Email, Phone Number, non-prefilled Date of Birth picker with **16+ Age Gate**, Password, and Confirm Password.
- **Dual Email + Phone SMS OTP Verification**: Dedicated Step-2 verification screen displaying separate Email OTP and Phone SMS OTP status badges, resend cooldown timers, and authoritative Supabase Auth verification before marking accounts fully verified (`is_email_verified` & `is_phone_verified`).
- **Complete Password Recovery Flow**: Handles `vybe://reset-password` and HTTPS recovery links, establishes recovery sessions (`token_hash` / access token), validates & updates the password via Supabase Auth, and routes cleanly back to Login with the recovered email pre-filled.

### 2. Persisted Post & Story Publishing + Synchronized Shared Feed
- **Strict Persistence Before Display**: Posts, Reels, and Stories appear only after Supabase Storage upload and database row insertion succeed, with clear byte-level upload progress, failure banners, and retry controls.
- **Accurate Post Counters**: Profile `posts_count` is reconciled by a Postgres trigger (`handle_post_count_change`) against actual persisted rows — new accounts start cleanly at `0` posts.
- **Null-Username Protection**: Database constraint (`chk_profiles_username_valid`), backfill migration, and client-side profile completion banner prevent `@null` handles.
- **24-Hour Stories**: Photo, video, and styled text stories with `EVERYONE` and `VYBE_MATCHES` privacy, cross-device `story_views` tracking, vibrant unviewed vs. muted viewed story rings, and automatic 24h expiration filtering.

### 3. Viewport-Aware Video Autoplay & Signature VYBE Design
- **Single-Active-Video Autoplay**: Feed videos autoplay muted when at least **55%** visible in the viewport, pause automatically when scrolled away or backgrounded, and never play multiple videos simultaneously. Reels use dedicated page-settled playback (`SmartReelVideoPlayer`).
- **Signature Non-Heart Resonance Icon & Syne Display Branding**: Custom `Canvas`-drawn **VYBE Pulse-V Spark** resonance icon (`VybeLikeIcon`) across Feed and Reels, paired with bundled **Syne** display typography (`res/font/syne.ttf`).
- **Dynamic Relative Timestamps**: Live-updating local timestamps (`Just now`, `2m`, `45m`, `3h`, `2d`, `2w`, or calendar date) across Feed, Stories, Reels, Rooms, Notifications, and Messages, plus real `"Joined [Month] [Year]"` dates on profiles.

### 4. Shareable Live Rooms (`VYB-XXXX-XXXX`)
- **Persistent Supabase Rooms**: Create public or private Unload Rooms (`TEXT_ONLY`, `VOICE_ONLY`, `VOICE_AND_TEXT`) backed by `rooms`, `room_members`, and `room_messages` tables.
- **Copyable Room ID & Instant Join**: Every room generates a unique human-readable code (`VYB-XXXX-XXXX`) with one-tap copy, Share Sheet support, and **Join by Room ID** bars in both **Unload** and **Explore**.

### 5. Real-Time Messaging, Voice Calls & Video Calls
- **Direct & Group Messaging**: Real 1:1 and group conversations with online presence indicators (`last_seen_at`), media attachments (`chat-media`), location pins, replies, reactions, edits, unsend, and message privacy (`EVERYONE`, `MUTUAL_FOLLOWS`, `NONE`).
- **Voice & Video Calling**: Initiated from Chat headers, Inbox, or Peer Profiles with Supabase `call_sessions` + `call_signals` signaling, global incoming-call banner (**Answer** / **Decline**), runtime `RECORD_AUDIO` + `CAMERA` permission handling, live `MM:SS` call timer, Mute, Speakerphone, Camera On/Off, Front/Back lens switching, and CameraX + peer video streaming.

---

## Tech Stack

| Layer | Technology |
| :--- | :--- |
| **Language** | Kotlin |
| **UI Framework** | Jetpack Compose + Material Design 3 + Bundled Syne Display Font |
| **Architecture** | MVVM (`VybeViewModel` + `StateFlow`) + Repository (`VybeRepository`, `SupabaseRepository`) |
| **Networking & Serialization** | Retrofit 2, OkHttp 4, Moshi (`KotlinJsonAdapterFactory`) |
| **Backend & Storage** | Supabase Auth (Email + SMS OTP), PostgREST, Storage (`posts`, `stories`, `avatars`, `chat-media`), RPCs |
| **Media, Audio & Camera** | Media3 ExoPlayer (`VybeVideoPlayer`, `SmartReelVideoPlayer`), CameraX (`Preview` + `ImageAnalysis`), `AudioRecord`/`AudioTrack` (`VybeCallEngine`), Coil |
| **Maps & Location** | Google Maps Compose (`maps-compose`), Play Services Location & Maps |
| **Testing** | JUnit 4, Kotlin Coroutines Test, Robolectric |

---

## Project Structure

```text
├── app/
│   ├── build.gradle.kts                  # App module Gradle configuration & BuildConfig fields
│   ├── proguard-rules.pro                # R8 / ProGuard keep rules for Moshi DTOs
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml       # Permissions, FileProvider & Auth/Recovery deep links
│       │   ├── java/com/example/
│       │   │   ├── MainActivity.kt       # Edge-to-edge host, deep links, upload & incoming call banners
│       │   │   ├── data/
│       │   │   │   ├── local/            # SessionManager & VybeLocalStore caches
│       │   │   │   ├── model/            # Domain models (User, Post, Story, Conversation, UnloadRoom, etc.)
│       │   │   │   ├── remote/           # SupabaseConfig, Auth/REST/Rooms/Calls APIs & DTOs
│       │   │   │   └── repository/       # VybeRepository & SupabaseRepository
│       │   │   ├── ui/
│       │   │   │   ├── VybeViewModel.kt  # Centralized UI state & async actions
│       │   │   │   ├── components/       # VybeLikeIcon, VybeVideoPlayer, StoryCarousel, banners, dialogs
│       │   │   │   ├── screens/          # Feed, Reels, Stories, Unload, Explore, Inbox, Chat, Call, Auth, Profile
│       │   │   │   └── theme/            # Material 3 colors, Syne + Sans typography & shapes
│       │   │   └── util/                 # TimestampUtils, VybeCallEngine, MediaUtils, NetworkMonitor, GitHubUpdater
│       │   └── res/                      # Drawables, adaptive launcher icons, Syne font, strings & XML configs
│       └── test/java/com/example/        # Unit & Robolectric test suites
├── backend/
│   └── supabase/migrations/              # Ordered PostgreSQL schema, RPC & RLS migrations
│       ├── 20261020000001_vybe_master_schema.sql
│       ├── 20261021000002_chat_performance_and_optimistic_messaging.sql
│       ├── 20261023000003_post_views_and_analytics.sql
│       ├── 20261024000004_vybe_complete_rls_privacy_stories_matches.sql
│       └── 20261024000005_vybe_safe_repair_auth_rooms_calls.sql
├── gradle/libs.versions.toml             # Gradle Version Catalog
└── .env.example                          # Placeholder keys for Secrets Gradle Plugin
```

---

## Setup & Deployment Guide

### 1. Configure Secrets (AI Studio Secrets Panel)
Enter your project credentials in the **Secrets panel in AI Studio** (mapped via `.env` / `.env.example` and the Secrets Gradle Plugin into `BuildConfig` — never hardcode secrets in source code):

- `SUPABASE_URL` — Your Supabase project URL (e.g., `https://<project-ref>.supabase.co`)
- `SUPABASE_ANON_KEY` — Your Supabase public `anon` JWT key (**never** use `service_role` in the Android client)
- `GOOGLE_MAPS_API_KEY` — Google Maps Android SDK key (enables interactive map tiles in the location picker)
- `STUN_SERVER_URL` *(optional)* — Defaults to `stun:stun.l.google.com:19302`
- `TURN_SERVER_URL`, `TURN_USERNAME`, `TURN_CREDENTIAL` *(optional)* — Relay server credentials for NAT-restricted peer call networks

### 2. Run Supabase SQL Migrations
In your **Supabase Dashboard -> SQL Editor**, run the migrations in `/backend/supabase/migrations/` in chronological order. If your existing project already has migrations `0001`–`0004` applied, run:
- `backend/supabase/migrations/20261024000005_vybe_safe_repair_auth_rooms_calls.sql`

This migration is 100% idempotent (`IF NOT EXISTS` / `CREATE OR REPLACE`) and upgrades:
- `profiles` (`is_email_verified`, `is_phone_verified`, `last_seen_at`, `live_pulse`, null-username backfill, and `chk_profiles_username_valid`)
- `resolve_login_identifier(p_identifier)` & `check_username_available(p_username)` secure RPCs
- Reconciled `posts_count` trigger (`handle_post_count_change`) and public/circle story RLS (`can_view_story`)
- Persistent `rooms` (`VYB-XXXX-XXXX`), `room_members`, `room_messages`, `call_sessions`, and `call_signals` tables + RLS policies

### 3. Configure Supabase Auth (Email & Phone SMS OTP + Recovery Redirects)
1. **Redirect URLs** (**Authentication -> URL Configuration -> Redirect URLs**):
   - `vybe://auth-callback`
   - `vybe://reset-password`
2. **Email OTP**: Enable Email confirmations in **Authentication -> Providers -> Email** and include `{{ .Token }}` in the confirmation email template so users receive the 6-digit OTP code.
3. **Phone SMS OTP**: Enable **Authentication -> Providers -> Phone** and configure your SMS provider credentials (Twilio, MessageBird, Vonage, or Textlocal) directly inside the Supabase Dashboard (never in client code).

### 4. Build & Verify
- **Assemble Debug APK**:
  ```bash
  gradle :app:assembleDebug
  ```
- **Run Unit & Robolectric Tests**:
  ```bash
  gradle :app:testDebugUnitTest
  ```
