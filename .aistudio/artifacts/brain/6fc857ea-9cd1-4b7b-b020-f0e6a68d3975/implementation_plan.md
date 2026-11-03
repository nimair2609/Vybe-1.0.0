# VYBE — Complete Product Upgrade & Development Master Plan

Comprehensive upgrade strategy transforming the existing VYBE application into a polished, distinctive, production-grade social networking platform while strictly preserving existing package identity, signing keys, Supabase backend, and working user accounts.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The upgrade preserves your existing Supabase project (`cppwadedazpbevkfrchg`), database schema, user accounts, Android package namespace (`com.example`), and signing credentials. No database resets or mock substitutions will occur.

- **Confirmed Decision 1 (Backend & DB Preservation)**: Retain the configured Supabase backend (`https://cppwadedazpbevkfrchg.supabase.co`) with real-time Phoenix WebSocket push and REST endpoints.
- **Confirmed Decision 2 (Home Screen Reorientation)**: Transform Home from an Instagram-style feed into an original VYBE experience highlighting Unload, Time Capsule, Crossed Paths, Nearby, Explore Zone, Matches, Communities, and Trending. Traditional Feed discovery will reside smoothly in Explore and a top access tab.
- **Confirmed Decision 3 (Messaging Fix Priority)**: Fix the PostgREST RPC parameter mismatch (`p_peer_id`) and conversation-index drop in `sendExtendedDirectMessage` to guarantee reliable, instant message delivery.
- **Confirmed Decision 4 (Global Theme System)**: Implement reactive 3-way theme switching (Dark / Light / System) that propagates instantly across every screen, dialog, and navigation component, persisted in local storage and user profile preferences.

---

## 1. Overview & Core Concept

- **What It Does**: Upgrades VYBE into a premier, original social networking platform emphasizing spontaneous connection (Unload live rooms, Crossed Paths, Time Capsules, Vybe Matches) and creator expression (VYBE short videos, mood-tagged feed, audio/video messaging, verified badges).
- **Target Audience / Persona**: Modern social networkers, creators, and local communities seeking an authentic, privacy-conscious alternative to generic feeds, with real-time audio rooms and location-based discovery.
- **Key Value**: Distinctive visual design (deep cosmic purple/violet on obsidian black), rich real-time interactions, zero mock data, and rock-solid privacy and messaging reliability.

---

## 2. User Experience & Visual Design

### Visual Identity & Theme Tokens
- **Design Language**: Cosmic Obsidian & Royal Amethyst. Clean typography, deep dark canvas, glowing violet accents, soft elevation borders, and subtle glassmorphic surfaces.
- **Color Tokens**:
  - `Background Dark`: `#0A0713` (Obsidian Void)
  - `Surface Dark`: `#140E26` (Deep Amethyst Card)
  - `Surface Dark Elevated`: `#1D1438` (Interactive Surface)
  - `Accent Violet Primary`: `#8A2BE2` / `#9D4EDD`
  - `Accent Glow Secondary`: `#C77DFF` (Highlight & Rings)
  - `Background Light`: `#F7F5FC` (Soft Lilac Canvas)
  - `Surface Light`: `#FFFFFF`
  - `Surface Light Elevated`: `#EDE9FE`
  - `Text Primary Dark`: `#F3EEFF`
  - `Text Secondary Dark`: `#9D93B8`
- **Global Theme Engine**:
  - Immediate dynamic re-theme without activity reload or screen flicker.
  - Covers Android Compose (`VybeTheme`, `MaterialTheme.colorScheme`) and Web Admin (`document.documentElement.classList`).

### Key User Flows

```
┌────────────────────────────────────────────────────────────────────────┐
│                        MAIN BOTTOM NAVIGATION                          │
│                                                                        │
│   [ 1. Home ]       [ 2. Explore ]    [ 3. Vybe ]   [ 4. Inbox ]  [ 5. Profile ]
│        │                   │               │             │              │
│   Original VYBE       Feed Discovery   Short Videos   Real-Time     User Identity
│   Interactive Hub     & Search Hub     & Reactions    Messaging     & Settings
└────────┬───────────────────┬───────────────┬─────────────┬──────────────┬──────┘
         │                   │               │             │              │
 ┌───────┴──────┐      ┌─────┴──────┐  ┌─────┴──────┐ ┌────┴──────┐ ┌─────┴──────┐
 │ • Unload     │      │ • Feed     │  │ • Vertical │ │ • Direct  │ │ • Posts    │
 │ • Capsule    │      │ • Search   │  │   Playback │ │ • Groups  │ │ • Badges   │
 │ • Crossed    │      │ • Creators │  │ • Drag Love│ │ • Media   │ │ • QR Code  │
 │ • Nearby     │      │ • Trending │  │ • Comments │ │ • Typing  │ │ • Privacy  │
 │ • Matches    │      │ • Moods    │  │ • Share    │ │ • Calls   │ │ • Themes   │
 └──────────────┘      └────────────┘  └────────────┘ └───────────┘ └───────────┘
```

1. **Home Screen (Original Experience)**:
   - Header with VYBE logo, notification bell with unread badge, and global search/filter.
   - **Hero Cards Carousel & Interactive Grid**:
     - **Vybe Unload**: Live audio/text stage rooms with real-time listener counters and join actions.
     - **Time Capsule**: Floating countdown cards showing pending and unlocked capsules with recipient avatars.
     - **Crossed Paths**: Radar-style discovery card showing nearby users encountered today with privacy shielding.
     - **Nearby & Communities**: Local community activities and pulse badges.
     - **Vybe Matches**: Mutual connection requests with quick review sheet.
     - **Trending / What's Happening**: Hot tags and topics driving direct discussions.
2. **Explore & Feed**:
   - Mood Filter Chips (General, Happy, Heartbreak, Chill, Healing, Fun).
   - Multi-photo carousel with true photo count indicator (no fake counts).
   - Private appreciation / two-finger love reaction (no public vanity like counts).
3. **Inbox & Messaging (Zero Failure)**:
   - Conversation list sorted stably by recent message timestamp.
   - Direct message delivery with optimistic bubble, instant send, and automatic retry.
   - Fixed RPC method parameters: queries Supabase `rpc/get_or_create_direct_conversation` cleanly with `p_peer_id`.
   - Voice note recording, image/video picker, reply previews, and read receipts.
4. **Admin Portal**:
   - 12 comprehensive management modules: Overview, Users, Verification Requests, Badges (Verified, Gold, Shop, Workers), Reports/Moderation, Content, Ads, Payments & Subscriptions, Analytics (DAU/MAU/Retention), System Health, Push Notifications, and Audit Logs.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Direct Home Screen Transformation vs. Tab Replacement**:
  - *Chosen Approach*: Keep 5-tab bottom navigation (Home, Explore, Vybe, Inbox, Profile), but transform the Home tab into the distinctive VYBE Original Hub. The traditional feed moves to the Explore tab with a quick-switch toggle.
  - *Why*: Establishes VYBE's unique product identity on first app launch rather than mimicking traditional feeds.
- **Decision 2: Messaging Delivery Resilience**:
  - *Chosen Approach*: Fix PostgREST `p_peer_id` serialization in `SupabaseDto.kt`, replace silent index drops with dynamic conversation creation and thread tracking, and maintain Phoenix WebSocket channel subscriptions for instant delivery.
  - *Why*: Solves user issue "new msg not sending" and ensures messages never disappear or freeze on send.
- **Decision 3: Zero Mock Enforcement**:
  - *Chosen Approach*: Connect every screen to real Supabase tables (`conversations`, `messages`, `posts`, `profiles`, `live_rooms`, `time_capsules`, `crossed_paths`, `badges`). When a user has no items, display high-polish empty states with actions instead of fake mock users or comments.
- **Decision 4: Multi-Tier Verification Badges**:
  - *Chosen Approach*: Blue (Verified Public Figure), Gold (VYBE VIP/Premium), Green (VYBE Workers), Purple/Orange (VYBE Shop/Business). Assigned through the Admin Portal and validated via database foreign keys.

---

## 4. Technical Architecture & Data Strategy

### System Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                        ANDROID APP (Jetpack Compose)                   │
├────────────────────────────────────────────────────────────────────────┤
│  UI Screens:                                                           │
│  [HomeScreen] [ExploreScreen] [ReelsViewerScreen] [InboxScreen] [Profile]
│  [VybeUnloadScreen] [TimeCapsuleScreen] [CrossedPathsScreen] [Admin]   │
├────────────────────────────────────────────────────────────────────────┤
│  State & Presentation:                                                 │
│  [VybeViewModel] ──── StateFlow (UI State, Theme, Auth, Conversations)  │
├────────────────────────────────────────────────────────────────────────┤
│  Data Layer & Coordination:                                            │
│  [VybeRepository]                                                      │
│    ├── [SupabaseRepository] (Rest API, RPC, Auth, Storage)             │
│    ├── [VybeRealtimePushManager] (Phoenix Channels WebSocket)          │
│    ├── [VybeLocalStore] (Room / Encrypted Cache / Theme Prefs)         │
│    └── [VybeNotificationHelper] (FCM HTTP v1 & Foreground Badging)     │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │ HTTPS / WSS
┌────────────────────────────────────┴───────────────────────────────────┐
│                 SUPABASE BACKEND (cppwadedazpbevkfrchg)                │
├────────────────────────────────────────────────────────────────────────┤
│  PostgreSQL + PostgREST:                                               │
│  • tables: profiles, conversations, messages, posts, time_capsules,    │
│            live_rooms, crossed_paths, badges, reports, notifications   │
│  • rpc: get_or_create_direct_conversation(p_peer_id)                  │
│  • realtime: broadcast / presence on user push & chat channels         │
├────────────────────────────────────────────────────────────────────────┤
│  Edge Functions & FCM Gateway:                                         │
│  • push-dispatch (FCM HTTP v1 with HIGH priority & PUBLIC visibility)  │
│  • serve_web.js (Admin Portal & Web App Gateway on Port 3000 -> 8080)   │
└────────────────────────────────────────────────────────────────────────┘
```

### Key Technical Corrections & Implementation Steps

#### Phase 1: Core Messaging & Database Repair
1. **Supabase DTO Alignment**:
   - Update `SupabaseRpcPeerConversationRequest` to contain only `@Json(name = "p_peer_id") val peerId: String`.
   - Update `SupabaseRepository.getOrCreateDirectConversation` to send only `p_peer_id` so PostgREST resolves the RPC cleanly without 404/PGRST202.
2. **VybeRepository Send Reliability**:
   - In `sendExtendedDirectMessage`, if `findConversationIndex == -1`, find by peer user ID or build a local conversation wrapper rather than dropping the message.
   - Maintain optimistic send bubble with instant feedback; auto-retry on network reconnection.
   - Verify reading and delivery receipts update without race conditions.

#### Phase 2: Design System & Theme Engine
1. **Centralized Palette & Tokens**:
   - Modernize `Theme.kt` with premium dark obsidian tones and radiant purple accents.
   - Ensure `isSystemInDarkTheme()`, user preference, and manual overrides immediately update `MaterialTheme.colorScheme`.
   - Update all hardcoded colors across screens to use `MaterialTheme.colorScheme` tokens.
2. **Motion & Visual Polish**:
   - Add spring physics on card touches, ripple feedback on all interactables, and shimmering skeleton loaders.

#### Phase 3: Original Home Screen Experience
1. **Home Layout Overhaul (`HomeScreen.kt`)**:
   - Remove standard Instagram-like vertical feed from Home.
   - Add top header with VYBE branding, Live Pulse status, and Notification trigger.
   - Create interactive hero module for **Vybe Unload** (active live audio/text rooms).
   - Create **Time Capsule** interactive widget (unopened capsules, lock countdown, create button).
   - Create **Crossed Paths & Nearby** interactive radar cards with privacy switches.
   - Create **Vybe Matches** connection drawer.
   - Add **Trending Community Pulse** section.

#### Phase 4: Bottom Navigation & Explore Zone
1. **Navigation Realignment (`MainActivity.kt` & `VybeViewModel.kt`)**:
   - 5 tabs: Home (`HOME`), Explore (`EXPLORE`), Vybe (`REELS`), Inbox (`INBOX`), Profile (`PROFILE`).
   - Feed content seamlessly available inside Explore under a dedicated "Feed" view or top segment.
2. **Vybe Short Videos Polish (`ReelsViewerScreen.kt`)**:
   - Press-hold-drag reaction picker.
   - Pure vertical aspect ratio, sound toggle, comment sheet, share integration.

#### Phase 5: Feature Upgrades (Unload, Capsule, Crossed Paths, Matches)
1. **Vybe Unload (`VybeUnloadScreen.kt`)**:
   - Real-time room creation (Voice, Text, Voice+Text), listener count sync, host controls, report/block.
2. **Time Capsule (`TimeCapsuleScreen.kt`)**:
   - Recipient targeting by username/ID, media attachment, scheduled unlock date/time, secure preview gating.
3. **Crossed Paths (`CrossedPathsScreen.kt`)**:
   - Opt-in location telemetry, fuzzy distance context (never raw GPS coordinates exposed), toggle to disable.
4. **Badges System**:
   - Full support for Blue (Verified), Gold (Premium), Green (Worker), Purple (Shop).

#### Phase 6: Admin Portal Upgrade (`src/views/AdminConsoleModal.tsx`)
1. **Central Control Dashboard**:
   - 12 tabs: Overview stats (DAU, MAU, signups), User Management, Verification requests, Badges assignment, Reports moderation, Content management, Ads, Payments foundation, System health, Push notification manager, and Audit logs.

#### Phase 7: Verification & Build
1. **Compilation & Assembly**:
   - Verify `compile_applet` and `gradle assembleDebug` pass with 0 errors.
   - Test APK installation, launch, messaging flow, and web portal access.
