# VYBE — Next-Generation Social Network (Android)

**VYBE** is a native Android social platform built with **Kotlin**, **Jetpack Compose (Material 3)**, and a **Supabase** cloud backend (Auth, Postgres with Row-Level Security, Storage, and RPCs). It combines expressive mood-driven feeds, short-form Reels, 24-hour Stories with VYBE Matches privacy, real-time Direct Messaging, Crossed Paths discovery, Unload Rooms, Double Take prompts, Time Capsules, Social Predictions, Spiritual Mode, and Verified Business Shops.

---

## Key Features

### 1. Authentication, Age Gate & Password Recovery
- **Supabase Email/Password & OTP Authentication** with automatic JWT refresh and persistent local session restoration (`SessionManager`).
- **Strict 16+ Age Gate** enforced during registration (`YYYY-MM-DD` validation).
- **Deep-Link Password Recovery**: Dedicated `vybe://reset-password` and HTTPS callback handling (`MainActivity` + `PasswordRecoveryScreen`) that validates recovery tokens/PKCE hashes, updates the password securely, and returns cleanly to login without silently bypassing password reset.

### 2. Feed, Mood Zones & Media Publishing
- **Unified Mood Zones**: `GENERAL`, `CHILL`, `FUN`, `MOTIVATIONAL`, `EMOTIONAL`, `SPIRITUAL`, and `HEALING`. Untagged posts automatically appear in the General feed.
- **Chunked Byte-Level Upload Progress**: Real-time progress tracking (`ProgressRequestBody`) for photos, carousels, stories, and reels with retry support and `client_upload_id` deduplication to prevent duplicate posts on network retries.
- **Online-First Integrity**: Posts and stories are marked published only after both Supabase Storage upload and database insertion succeed.
- **Reactions, Saves, Comments & View Tracking**: Multi-reaction support (`PURE_ENERGY`, `FIRE`, `SOULFUL`, `MIND_BLOWN`, `PEACE`), bookmarks, threaded comments, and session-deduplicated post view counters (`increment_post_view` RPC).

### 3. VYBE Reels & 24-Hour Stories
- **Short-Form Reels**: Vertical pager with single-instance active video decoding (`SmartReelVideoPlayer` / native playback), mute toggle, progress bar, and double-tap heart animation.
- **Stories & VYBE Matches**: Photo, video, and styled text stories with `EVERYONE` and `VYBE_MATCHES` (curated close-circle) audiences.
- **Cross-Device Story View Sync**: Viewed stories are recorded in `story_views` and sorted behind unviewed stories so seen stories do not reappear as new.

### 4. Privacy, Social Graph & Row-Level Security (RLS)
- **Public & Private Accounts**: Public profiles allow instant follows; Private profiles create `PENDING` follow requests requiring owner approval before posts/stories unlock.
- **Database-Enforced RLS**: Visibility for `posts`, `post_media`, `stories`, `follows`, `vybe_matches`, `conversations`, `messages`, and `notifications` is enforced directly in Postgres RLS policies (including block/ban checks).
- **Safety Controls**: Block user, restrict user, hide conversation behind a hashed PIN, and submit Trust & Safety reports.

### 5. Direct Messaging & Inbox
- **Real Direct Conversations**: Deterministic 1:1 conversation resolution (`get_or_create_direct_conversation` RPC), optimistic message sending with `SENDING` / `SENT` / `FAILED` states, media attachments (`chat-media` bucket), replies, reactions, edits, and unsend.
- **Messaging Privacy Rules**: Supports `EVERYONE`, `MUTUAL_FOLLOWS`, and `NONE`, plus private-account follow enforcement.

### 6. Signature VYBE Experiences
- **Unfiltered Mode**: Raw, zero-filter camera capture feed.
- **Crossed Paths & Google Maps Picker**: Real GPS + interactive Google Maps Compose location picker with reverse geocoding (`Geocoder`) for missed-connection encounters and location tagging.
- **Unload Rooms**: Topic-based supportive audio/chat rooms (including optional anonymous participation).
- **Double Take, Social Predictions & Time Capsules**: Interactive two-person/public prompts, community polls, and time-locked capsules.
- **Spiritual Mode & Wellbeing**: Gratitude journal, daily intentions, and screen-time break reminders.
- **Verified Local Shops**: Business verification applications, shop listings, and community reviews.
- **Over-The-Air GitHub Release Updater**: Checks GitHub Releases (`GitHubUpdater`) for newer semantic versions and guides in-place APK upgrades without losing user sessions.

---

## Tech Stack

| Layer | Technology |
| :--- | :--- |
| **Language** | Kotlin |
| **UI Framework** | Jetpack Compose + Material Design 3 + Adaptive Layouts |
| **Architecture** | MVVM (`VybeViewModel` + `StateFlow`) + Repository (`VybeRepository`, `SupabaseRepository`) |
| **Networking & Serialization** | Retrofit 2, OkHttp 4, Moshi (`KotlinJsonAdapterFactory`) |
| **Backend & Storage** | Supabase Auth, PostgREST, Supabase Storage (`posts`, `stories`, `avatars`, `chat-media`) |
| **Camera & Media** | CameraX (`camera-core`, `camera2`, `camera-lifecycle`, `camera-view`), Android Photo Picker, Coil |
| **Maps & Location** | Google Maps Compose (`maps-compose`), Play Services Location & Maps |
| **Testing** | JUnit 4, Kotlin Coroutines Test, Robolectric |

---

## Project Structure

```text
├── app/
│   ├── build.gradle.kts                  # App module Gradle configuration & dependencies
│   ├── proguard-rules.pro                # R8 / ProGuard keep rules for Moshi DTOs
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml       # Permissions, FileProvider & Auth/Recovery deep links
│       │   ├── java/com/example/
│       │   │   ├── MainActivity.kt       # Edge-to-edge host + deep-link intent routing
│       │   │   ├── data/
│       │   │   │   ├── local/            # SessionManager & VybeLocalStore caches
│       │   │   │   ├── model/            # Domain models (User, Post, Story, Conversation, etc.)
│       │   │   │   ├── remote/           # SupabaseConfig, Auth/REST APIs, DTOs & upload bodies
│       │   │   │   └── repository/       # VybeRepository & SupabaseRepository
│       │   │   ├── ui/
│       │   │   │   ├── VybeApp.kt        # Root navigation & adaptive scaffold
│       │   │   │   ├── VybeViewModel.kt  # Centralized UI state & user actions
│       │   │   │   ├── components/       # Reusable Compose UI, video player, map picker, updater
│       │   │   │   ├── screens/          # Feature screens (Feed, Reels, Stories, Inbox, Auth, etc.)
│       │   │   │   └── theme/            # Material 3 colors, typography & shapes
│       │   │   └── util/                 # MediaUtils, NetworkMonitor, GitHubUpdater
│       │   └── res/                      # Drawables, adaptive launcher icons, strings & XML configs
│       └── test/java/com/example/        # Unit & Robolectric test suites
├── backend/
│   └── supabase/migrations/              # Ordered PostgreSQL schema, RPC & RLS migrations
│       ├── 20261020000001_vybe_master_schema.sql
│       ├── 20261021000002_chat_performance_and_optimistic_messaging.sql
│       ├── 20261023000003_post_views_and_analytics.sql
│       └── 20261024000004_vybe_complete_rls_privacy_stories_matches.sql
├── gradle/libs.versions.toml             # Gradle Version Catalog
└── .env.example                          # Placeholder keys for Secrets Gradle Plugin
```

---

## Setup & Deployment Guide

### 1. Configure Secrets (AI Studio Secrets Panel)
Set the following keys in the **Secrets panel in AI Studio** (backed by `.env` / `.env.example` via the Secrets Gradle Plugin and exposed through `BuildConfig`):

- `SUPABASE_URL` — Your Supabase project URL (e.g., `https://<project-ref>.supabase.co`)
- `SUPABASE_ANON_KEY` — Your Supabase public `anon` JWT key (**never** put `service_role` in the client app)
- `GOOGLE_MAPS_API_KEY` — Google Maps Android SDK key (enables interactive map rendering in the location picker)

### 2. Run Supabase SQL Migrations
In your **Supabase Dashboard -> SQL Editor**, run the SQL files in `/backend/supabase/migrations/` in chronological order (or run `20261024000004_vybe_complete_rls_privacy_stories_matches.sql` if your base tables already exist — it is idempotent and safely adds any missing columns such as `profiles.is_banned`, `stories.media_type`, `posts.client_upload_id`, storage buckets, helper RPCs, and strict RLS policies).

### 3. Configure Supabase Auth Redirect URLs
In **Supabase Dashboard -> Authentication -> URL Configuration -> Redirect URLs**, add:
- `vybe://auth-callback`
- `vybe://reset-password`

### 4. Build & Run Tests
- **Assemble Debug APK**:
  ```bash
  gradle :app:assembleDebug
  ```
- **Run Unit & Robolectric Tests**:
  ```bash
  gradle :app:testDebugUnitTest
  ```

---

## Notes
- **Admin Dashboard / Admin Login**: Currently kept on hold per project roadmap.
- **OTA APK Updates via GitHub**: Increment `versionCode` / `versionName` in `app/build.gradle.kts` (and `INSTALLED_VERSION` in `GitHubUpdater.kt`), build the APK, and attach the `.apk` asset to a published GitHub Release tag (e.g., `v2.1.0`).
