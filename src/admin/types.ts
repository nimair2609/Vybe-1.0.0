import { OfficialVybeBadge } from '../types';

export type AdminSectionId =
  | 'overview'
  | 'users'
  | 'staff'
  | 'verifications'
  | 'content'
  | 'circles'
  | 'grow'
  | 'audio'
  | 'reports'
  | 'broadcast'
  | 'diagnostics'
  | 'audit'
  | 'settings';

export type AdminThemeMode = 'dark' | 'light';

export interface VerificationApp {
  id: string;
  userId: string;
  username: string;
  displayName: string;
  avatarUrl?: string;
  badge: OfficialVybeBadge;
  category: string;
  proof: string;
  submittedAt: string;
}

export interface AuditRecord {
  id: string;
  action: string;
  admin: string;
  target: string;
  details: string;
  time: string;
}

export interface AdminConfirmRequest {
  title: string;
  description: string;
  confirmLabel: string;
  tone?: 'danger' | 'primary';
  onConfirm: () => Promise<void> | void;
}

export interface AdminToastState {
  message: string;
  tone: 'success' | 'error' | 'info';
}

export interface SectionMetadata {
  title: string;
  group: string;
  subtitle: string;
}

export const ADMIN_SECTION_META: Record<AdminSectionId, SectionMetadata> = {
  overview: {
    title: 'Dashboard Overview',
    group: 'Overview',
    subtitle: 'Live Supabase platform metrics, queue status, and infrastructure health',
  },
  diagnostics: {
    title: 'Egress & System Health',
    group: 'Overview',
    subtitle: 'Real-time client caching telemetry and egress bandwidth conservation',
  },
  users: {
    title: 'Users Directory',
    group: 'User Management',
    subtitle: 'Inspect profiles, manage official badges, and enforce account safety',
  },
  staff: {
    title: 'Staff Roles & Access',
    group: 'User Management',
    subtitle: 'Owner-gated assignment of moderator, support, analyst, and admin permissions',
  },
  verifications: {
    title: 'Verification Queue',
    group: 'User Management',
    subtitle: 'Review creator, VIP, business storefront, and staff badge requests',
  },
  content: {
    title: 'Posts',
    group: 'Content & Safety',
    subtitle: 'Inspect published posts and videos with archive or removal moderation',
  },
  circles: {
    title: 'Circles Moderation',
    group: 'Ecosystem',
    subtitle: 'Oversee Growth Circles privacy modes, archives, and community reports',
  },
  grow: {
    title: 'GROW 2.0 Moderation',
    group: 'Ecosystem',
    subtitle: 'Moderate Growth Programs, showcase projects, opportunities, and listing reports',
  },
  audio: {
    title: 'Stories Audio Catalog',
    group: 'Ecosystem',
    subtitle: 'Manage verified audio tracks available in Stories 2.0 across web and Android',
  },
  reports: {
    title: 'Safety Reports',
    group: 'Content & Safety',
    subtitle: 'Investigate community violation reports and take enforcement actions',
  },
  broadcast: {
    title: 'Push Broadcasts',
    group: 'Communication',
    subtitle: 'Send platform-wide announcements across active web and Android sessions',
  },
  audit: {
    title: 'Audit Logs',
    group: 'System',
    subtitle: 'Immutable administrator action trail recorded in Supabase',
  },
  settings: {
    title: 'Platform Controls',
    group: 'System',
    subtitle: 'Experience feature flags and live environment diagnostics',
  },
};
