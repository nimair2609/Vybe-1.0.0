# VYBE Application Data Reset — Table & Bucket Inventory

> **SAFETY NOTICE**: This inventory documents an **explicit, manual-only** application data reset procedure.
> It is **NOT** a migration and is **NOT** executed automatically anywhere in CI, Android, or Web codebases.
> All **Supabase Auth accounts (`auth.users`, `auth.identities`, credentials, user UUIDs)** and **`public.admin_users`** are **strictly preserved**.

## 1. Preserved Identity & Configuration Tables (DO NOT DELETE)
| Table / Schema | Preservation Reason |
| :--- | :--- |
| `auth.users` | Core Supabase Auth user IDs, email/phone credentials, password hashes |
| `auth.identities` | OAuth / provider identity bindings |
| `public.admin_users` | Active admin role assignments and authorization gate |
| `public.profiles` (rows kept) | Primary `id` (= `auth.uid()`), `username`, `role`; counters & media URLs are reset to initial state rather than deleting user identity rows |
| `storage.buckets` | Bucket definitions and RLS policies remain completely intact |

## 2. Application Data Tables (Dependency-Ordered Cleanup)
Deleted from child/leaf tables first up to parent content tables:

1. **Feed Analytics & Engagement Leaves**: `post_views`, `post_reactions`, `saved_posts`, `comments`, `post_media`, `story_views`
2. **Messaging & Calls**: `message_receipts`, `message_reactions`, `messages`, `conversation_members`, `conversations`, `call_signals`, `call_sessions`
3. **Circles / Communities**: `community_post_comments`, `community_post_likes`, `community_posts`, `community_members`, `communities`
4. **Live Rooms & Drops**: `room_messages`, `room_members`, `rooms`, `audience_pool_votes`, `audience_pool_options`, `audience_pools`, `time_capsule_members`, `time_capsules`, `double_take_answers`, `double_takes`, `crossed_path_requests`, `crossed_paths`, `live_streams`
5. **Grow & Business / Spiritual**: `business_reviews`, `business_products`, `business_profiles`, `spiritual_intentions`, `spiritual_gratitudes`
6. **Parent Content**: `posts`, `stories`
7. **Notifications & Social Graph**: `notifications`, `user_device_tokens`, `follows`, `blocks`, `user_restrictions`, `vybe_matches`
8. **Moderation & Audit**: `reports`, `verification_requests`, `user_moderation_actions`, `admin_audit_logs`, `audit_logs`
9. **Transient Settings** (optional): `user_settings`, `notification_preferences`

## 3. Supabase Storage Buckets (Object Purge Only — Buckets Preserved)
| Bucket ID | Access | Action on Reset |
| :--- | :--- | :--- |
| `avatars` | Public | Purge objects only via Storage API (or SQL if permitted) after approval |
| `banners` | Public | Purge objects only; preserve bucket & RLS policies |
| `posts` | Public | Purge objects only; preserve bucket & RLS policies |
| `stories` | Public | Purge objects only; preserve bucket & RLS policies |
| `raw_media` | Public | Purge objects only; preserve bucket & RLS policies |
| `chat-media` | Private | Purge objects only; preserve bucket & RLS policies |
| `business_docs` | Private | Purge objects only; preserve bucket & RLS policies |
