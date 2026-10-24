import type { MoodZone, VybePost, VybeUser } from '../types/vybe';

export const MOOD_OPTIONS: { key: MoodZone; label: string; emoji: string; color: string }[] = [
  { key: 'GENERAL', label: 'General', emoji: '🌐', color: '#00E5FF' },
  { key: 'CHILL', label: 'Chill', emoji: '🌊', color: '#4FC3F7' },
  { key: 'FUN', label: 'Fun', emoji: '⚡', color: '#FFD54F' },
  { key: 'MOTIVATIONAL', label: 'Motivational', emoji: '🔥', color: '#FF7043' },
  { key: 'EMOTIONAL', label: 'Emotional', emoji: '💜', color: '#B388FF' },
  { key: 'SPIRITUAL', label: 'Spiritual', emoji: '✨', color: '#80CBC4' },
  { key: 'HEALING', label: 'Healing', emoji: '🌿', color: '#69F0AE' }
];

export function parseIsoMillis(iso?: string | null): number | null {
  if (!iso) return null;
  const trimmed = iso.trim();
  if (!trimmed || trimmed.toLowerCase() === 'null') return null;
  const ms = Date.parse(trimmed);
  return Number.isNaN(ms) ? null : ms;
}

export function formatRelativeTimestamp(iso?: string | null, nowMillis: number = Date.now()): string {
  const createdMs = parseIsoMillis(iso);
  if (createdMs === null) return '';

  const diffSec = Math.max(0, Math.floor((nowMillis - createdMs) / 1000));
  if (diffSec < 45) return 'Just now';
  if (diffSec < 3600) return `${Math.max(1, Math.floor(diffSec / 60))}m`;
  if (diffSec < 86400) return `${Math.floor(diffSec / 3600)}h`;
  if (diffSec < 604800) return `${Math.floor(diffSec / 86400)}d`;
  if (diffSec < 2592000) return `${Math.floor(diffSec / 604800)}w`;

  const createdDate = new Date(createdMs);
  const nowDate = new Date(nowMillis);
  const sameYear = createdDate.getFullYear() === nowDate.getFullYear();
  return createdDate.toLocaleDateString(undefined, {
    month: 'short',
    day: 'numeric',
    ...(sameYear ? {} : { year: 'numeric' })
  });
}

export function formatMessageTimestamp(iso?: string | null, nowMillis: number = Date.now()): string {
  const createdMs = parseIsoMillis(iso);
  if (createdMs === null) return '';
  const rel = formatRelativeTimestamp(iso, nowMillis);
  const localClock = new Date(createdMs).toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit'
  });
  return rel === 'Just now' || rel.endsWith('m') ? `${rel} • ${localClock}` : `${rel}, ${localClock}`;
}

export function formatJoinedMonthYear(createdAtIso?: string | null): string {
  const ms = parseIsoMillis(createdAtIso);
  if (ms === null) return 'VYBE Member';
  const formatted = new Date(ms).toLocaleDateString(undefined, {
    month: 'long',
    year: 'numeric'
  });
  return `Joined ${formatted}`;
}

export function isRecentlyOnline(lastSeenIso?: string | null, thresholdSec = 180, nowMillis = Date.now()): boolean {
  const ms = parseIsoMillis(lastSeenIso);
  if (ms === null) return false;
  const diffSec = (nowMillis - ms) / 1000;
  return diffSec >= -120 && diffSec <= thresholdSec;
}

export function normalizePhoneE164(raw: string): string {
  const trimmed = raw.trim();
  const digits = trimmed.replace(/[^0-9]/g, '');
  if (!digits) return '';
  if (trimmed.startsWith('+')) return `+${digits}`;
  if (digits.length === 10) return `+1${digits}`;
  return `+${digits}`;
}

export function calculateAgeYears(dobIso: string, today = new Date()): number | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(dobIso.trim())) return null;
  const [y, m, d] = dobIso.split('-').map(Number);
  const birth = new Date(Date.UTC(y, m - 1, d));
  if (
    birth.getUTCFullYear() !== y ||
    birth.getUTCMonth() !== m - 1 ||
    birth.getUTCDate() !== d
  ) {
    return null;
  }
  let age = today.getUTCFullYear() - y;
  const monthDiff = today.getUTCMonth() + 1 - m;
  if (monthDiff < 0 || (monthDiff === 0 && today.getUTCDate() < d)) {
    age--;
  }
  return age;
}

export interface RegistrationInput {
  firstName: string;
  lastName: string;
  username: string;
  email: string;
  phone: string;
  dateOfBirth: string;
  password: string;
  confirmPassword: string;
}

export function validateRegistrationFields(input: RegistrationInput): Record<string, string> {
  const errors: Record<string, string> = {};
  if (input.firstName.trim().length < 2) {
    errors.firstName = 'First name is required (min 2 characters).';
  }
  if (input.lastName.trim().length < 1) {
    errors.lastName = 'Last name is required.';
  }
  const cleanUser = input.username.trim().replace(/^@/, '').toLowerCase();
  if (!/^[a-z0-9_.]{3,28}$/.test(cleanUser) || cleanUser === 'null') {
    errors.username = 'Username must be 3–28 characters (letters, numbers, _ or .).';
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(input.email.trim())) {
    errors.email = 'Enter a valid email address.';
  }
  const e164 = normalizePhoneE164(input.phone);
  if (!/^\+[1-9]\d{7,14}$/.test(e164)) {
    errors.phone = 'Enter a valid phone number with country code (e.g. +14155552671).';
  }
  if (!input.dateOfBirth.trim()) {
    errors.dateOfBirth = 'Date of birth is required.';
  } else {
    const age = calculateAgeYears(input.dateOfBirth);
    if (age === null || age > 120 || age < 0) {
      errors.dateOfBirth = 'Enter a valid date of birth.';
    } else if (age < 16) {
      errors.dateOfBirth = 'You must be at least 16 years old to join VYBE.';
    }
  }
  if (input.password.length < 8) {
    errors.password = 'Password must be at least 8 characters.';
  }
  if (input.password !== input.confirmPassword) {
    errors.confirmPassword = 'Passwords do not match.';
  }
  return errors;
}

const ROOM_ALPHABET = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';

export function generateRoomCode(): string {
  const randomChars = (len: number) => {
    const arr = new Uint8Array(len);
    crypto.getRandomValues(arr);
    return Array.from(arr, (b) => ROOM_ALPHABET[b % ROOM_ALPHABET.length]).join('');
  };
  return `VYB-${randomChars(4)}-${randomChars(4)}`;
}

export function normalizeRoomCode(raw: string): string {
  const trimmed = raw.trim().toUpperCase();
  if (trimmed.includes('-') && trimmed.length > 20) return trimmed.toLowerCase();
  const alnum = trimmed.replace(/[^A-Z0-9]/g, '');
  if (alnum.startsWith('VYB') && alnum.length === 11) {
    return `VYB-${alnum.slice(3, 7)}-${alnum.slice(7, 11)}`;
  }
  if (alnum.length === 8) {
    return `VYB-${alnum.slice(0, 4)}-${alnum.slice(4, 8)}`;
  }
  return trimmed;
}

export function matchesMoodFilter(post: VybePost, filter: MoodZone | null): boolean {
  if (!filter) return true;
  if (filter === 'GENERAL') return !post.moodZone || post.moodZone === 'GENERAL';
  return post.moodZone === filter;
}

export function hasIncompleteProfile(user: VybeUser | null): boolean {
  if (!user) return false;
  const u = (user.username || '').trim().toLowerCase();
  const d = (user.displayName || '').trim().toLowerCase();
  return !u || u === 'null' || u.startsWith('vybe_') || !d || d === 'null';
}
