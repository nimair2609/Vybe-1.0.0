# VYBE 2.0 – Complete Master Product Upgrade
## Architecture Inspection Report & Implementation Plan

---

### Part 1: Architecture Inspection Report

#### 1. Android Application Architecture
- **Package ID:** `com.aistudio.vybe.nzqpvx` (Strictly preserved; verified across `build.gradle.kts`, `AndroidManifest.xml`, and `google-services.json`).
- **Framework & Language:** Kotlin 2.0+ with Jetpack Compose, Material Design 3, Navigation state-driven routing via `Screen` enum and `screenBackStack`.
- **State Management:** `VybeViewModel` managing `MutableStateFlow` streams with lifecycle-aware Compose collection, backing single source of truth in `VybeRepository`.
- **Signing & Keystore:**
  - `app/build.gradle.kts` uses preconfigured signing configs (`release` and `debugConfig`) mapped to keystores (`.ci-keystore` / `debug.keystore`).
  - Strict preservation: No keystore or signingConfig modifications will be performed.

#### 2. Firebase & Push Notification Diagnostics
- **Target Project:** `vybe-1c966`
- **Real Project Number:** `675799521050` (verified in `app/google-services.json`).
- **Root Cause of Notification Failure When App Closed / Device Turn Off:**
  - `app/src/main/res/values/strings.xml` contained hardcoded `gcm_defaultSenderId` set to `701872969956` and `google_app_id` set to `1:701872969956:android:b16340a6dd134786ad40bf`.
  - `VybeApplication.kt` also retained fallback `FirebaseOptions` using `701872969956`.
  - **Result:** Android devices registered for FCM tokens against the wrong sender (`701872969956`), causing FCM dispatch to fail with unregistered token when messages were triggered while the app process was terminated or device was asleep.
  - **Remedy:** Align `strings.xml` and `VybeApplication.kt` with project number `675799521050` and app ID `1:675799521050:android:e80aea6e02d0b1dda47ea3`.

#### 3. Supabase & Database Architecture
- **Backend URL:** `https://cppwadedazpbevkfrchg.supabase.co`
- Real persistent storage for profiles, posts, comments, reactions, stories, follow relationships, verification requests, and administrative events.
- Zero mock data fallback: All data queries interface with live backend endpoints and RPC procedures.

#### 4. Web Application Architecture
- Hosted via Node.js service (`backend/serve_web.js`) proxied through Nginx on port 8080.
- Vite + TypeScript production build (`dist/index.html`).

---

### Part 2: Non-Negotiable Project Protection Rules
1. **No new app:** All upgrades expand the existing codebase (`com.aistudio.vybe.nzqpvx`).
2. **No Firebase replacement or Supabase migration:** Both existing backends remain intact and correctly configured.
3. **No signing or keystore changes:** Keystore files and Gradle signing blocks remain untouched.
4. **No mock data:** Maintain real cross-device persistence.
5. **Brand Preservation:** VYBE brand mark, logo positions, and "AN NR NEXT PRODUCT" parent attribution remain prominent.

---

### Part 3: Specific User Bugfixes & Refinements

1. **FCM Push Notifications:**
   - Correct `gcm_defaultSenderId` in `strings.xml` and `VybeApplication.kt` to `675799521050`.
   - Ensure high-priority notification channels (`vybe_high_priority_channel`) wake the device with heads-up banners, vibration, and custom sound.
2. **User Profile Back Button & Identity Check:**
   - In `UserProfileScreen.kt`, ensure `closeUserProfileOrBack()` always pops the back stack back to the previous screen (e.g. `MainTab.FEED` or `Screen.MAIN`).
   - Fix user profile state resolution so visiting other profiles never confuses current user ownership.
3. **Story Details & Owner Insights:**
   - Polish timestamp layout in story header so text never clips or wraps awkwardly.
   - For own stories, provide a clean bottom drawer showing "Who Saw" (viewer accounts with avatars), total reaction count, and Comment Center.
   - Ensure the universal default reaction across the app is the Korean Two-Finger Heart (`FingerHeartIcon`), styled with a crisp white border and transparent/blank body fill when unselected.
4. **Explore & Mood Zones Header Insets:**
   - Adjust `ExploreScreen.kt` and `MoodZonesScreen.kt` top bars to use standard status bar window insets gracefully without pushing content awkwardly offscreen.
5. **Story Post Dialog Layout:**
   - In `StoryCreateDialog.kt`, ensure the media canvas, caption, and bottom action buttons ("Matches" and "Share Story") fit adaptively across all screen heights and aspect ratios using `weight(1f)` scrollable containers and proper navigation bar insets.
6. **Spiritual Mode App Access:**
   - Provide an accessible entry card in `ExploreScreen.kt`, `MoodZonesScreen.kt`, and Home Feed 2.0 discovery leading directly to `Screen.SPIRITUAL_MODE`.
7. **VYBE Reels Timestamp:**
   - Remove timestamp display from the video overlay on VYBE Reels cards.
8. **Feed Post Author Name:**
   - Prominently display author's real full name (`displayName`) instead of `@username` on post headers.
9. **Follow Button Visuals:**
   - Update `VybeFollowButton` in `CommonComponents.kt` to use a sleek single-tone solid accent fill instead of a split gradient that looks like "double filled colour".

---

### Part 4: VYBE 2.0 Feature Ecosystem

#### A. Indian Brand Identity Integration
- Tagline: **CONNECT. EXPRESS. GROW.**
- Origin Statement: **🇮🇳 PROUDLY MADE IN INDIA**
- Global Brand Statement: **BORN IN INDIA. BUILT FOR THE WORLD.**
- Parent Attribution: **AN NR NEXT PRODUCT**
- Subtle, premium tricolour accent ribbon embedded in Splash Screen, Settings About section, and Web footer.

#### B. Home Feed 2.0
- **Three Feed Modes:**
  1. **FOR YOU:** Curated hybrid of friends, trending, and personalized discovery.
  2. **FOLLOWING:** Strict chronological content from linked accounts.
  3. **TRENDING:** Most viral, high-engagement public posts and reels.
- **Stories & Moments:** Unified carousel with unseen status rings, viewer tracking, and audience filtering.
- **Reels Preview Module:** Real video thumbnails with view counts and one-tap jump to Reels.
- **Something New For You:** Discover new creators, active communities, and growth journeys.
- **Feed Controls:** Hide post, not interested, mute creator, report.

#### C. VYBE Circle (Communities & Relationships)
- **Private Circles (My Circles):** Create intimate groups, invite members, share private moments, assign moderators.
- **Public Circles (Discover Circles):** Explore topic-based communities (Tech, Art, Fitness, Music, Wellness, Entrepreneurship) with open discussions.

#### D. VYBE Grow (Self-Expression & Achievement)
- **Personal & Professional Growth Hub:**
  - 7-Day, 21-Day, and 30-Day Guided Habit & Goal Journeys.
  - Daily micro-reflections and streak tracking.
  - Visual badges for completed journeys displayed on profiles.

---

### Part 5: Implementation Sequence & Verification
1. **Phase 1: Configuration & Critical Bugfixes** (FCM sender ID, Profile back navigation, Follow button styling, Reels timestamp removal, Post author display name).
2. **Phase 2: Story & Header Polishing** (Story timestamp layout, Story viewer drawer for own stories, Explore safe insets, StoryCreateDialog responsive layout, Finger Heart white-border blank-fill reaction).
3. **Phase 3: Brand Identity & Spiritual Mode Access** (Splash screen & About updates, Spiritual Mode gateway in Explore & Home Feed).
4. **Phase 4: Home Feed 2.0 & Feed Modes** (Tabs: For You, Following, Trending + Discovery modules).
5. **Phase 5: VYBE Circle & VYBE Grow Modules** (UI screens, view model state, data models, repository integration).
6. **Phase 6: Verification & Compilation** (`compile_applet` clean build, web smoke tests).
