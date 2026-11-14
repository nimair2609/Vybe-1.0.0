import {
  User,
  Post,
  Story,
  PostComment,
  MoodType,
  OfficialVybeBadge,
  Conversation,
  DirectMessage,
  Community,
  UnloadRoom,
  CrossedPathEncounter,
  TimeCapsule,
  VybeShop,
  SpiritualIntention,
  NotificationItem,
  AuthSessionUser,
  VybeReaction,
  LivePulseStatus
} from '../types';
import { vybeCache, CACHE_TTL } from '../utils/indexedDbCache';
import { vybeDiagnostics } from '../utils/performanceDiagnostics';

const metaEnv = (typeof import.meta !== 'undefined' ? (import.meta as any).env : {}) || {};
export const SUPABASE_URL = metaEnv.VITE_SUPABASE_URL || 'https://cppwadedazpbevkfrchg.supabase.co';
export const SUPABASE_ANON_KEY =
  metaEnv.VITE_SUPABASE_ANON_KEY || 'sb_publishable_MI-UdOqfccHVZIHjQq9vrw_IjEzeR7H';

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const WEB_ACCESS_TOKEN_KEY = 'vybe_access_token';
const WEB_REFRESH_TOKEN_KEY = 'vybe_refresh_token';
const WEB_EXPIRES_AT_KEY = 'vybe_auth_expires_at';
const WEB_USER_ID_KEY = 'vybe_auth_user_id';
const WEB_USER_EMAIL_KEY = 'vybe_auth_email';

const baseAnonHeaders: Record<string, string> = {
  'apikey': SUPABASE_ANON_KEY,
  ...(SUPABASE_ANON_KEY.startsWith('eyJ') ? { 'Authorization': `Bearer ${SUPABASE_ANON_KEY}` } : {}),
  'Content-Type': 'application/json',
  'Prefer': 'return=representation'
};

const inFlightRequests = new Map<string, Promise<any>>();
function deduplicatedFetch<T>(key: string, fetcher: () => Promise<T>): Promise<T> {
  if (inFlightRequests.has(key)) {
    vybeDiagnostics.recordDeduplication(key);
    return inFlightRequests.get(key) as Promise<T>;
  }
  const promise = fetcher().finally(() => {
    inFlightRequests.delete(key);
  });
  inFlightRequests.set(key, promise);
  return promise;
}

// -------------------------------------------------------------
// JWT & Web Session Helpers
// -------------------------------------------------------------
function decodeJwtPayload(token: string): Record<string, any> | null {
  try {
    const parts = token.split('.');
    if (parts.length < 2) return null;
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
    const jsonStr = typeof atob === 'function' ? atob(padded) : '';
    if (!jsonStr) return null;
    return JSON.parse(jsonStr);
  } catch {
    return null;
  }
}

export function extractUserIdFromJwt(token: string): string | null {
  const payload = decodeJwtPayload(token);
  const sub = String(payload?.sub || '').trim();
  return UUID_REGEX.test(sub) ? sub : null;
}

function isTokenExpiringSoon(token: string, bufferSeconds: number = 60): boolean {
  const payload = decodeJwtPayload(token);
  if (!payload || typeof payload.exp !== 'number') return false;
  return payload.exp * 1000 <= Date.now() + bufferSeconds * 1000;
}

function persistWebSession(authData: {
  access_token?: string;
  refresh_token?: string;
  expires_in?: number;
  expires_at?: number;
  user?: { id?: string; email?: string };
}): AuthSessionUser | null {
  const accessToken = String(authData?.access_token || '').trim();
  if (!accessToken.startsWith('eyJ')) return null;

  const userId = String(authData?.user?.id || extractUserIdFromJwt(accessToken) || '').trim();
  if (!UUID_REGEX.test(userId)) return null;

  const email = String(authData?.user?.email || decodeJwtPayload(accessToken)?.email || '').trim().toLowerCase();
  const refreshToken = String(authData?.refresh_token || '').trim();
  const expiresAt =
    typeof authData?.expires_at === 'number'
      ? authData.expires_at * 1000
      : Date.now() + Number(authData?.expires_in || 3600) * 1000;

  try {
    localStorage.setItem(WEB_ACCESS_TOKEN_KEY, accessToken);
    if (refreshToken) localStorage.setItem(WEB_REFRESH_TOKEN_KEY, refreshToken);
    localStorage.setItem(WEB_EXPIRES_AT_KEY, String(expiresAt));
    localStorage.setItem(WEB_USER_ID_KEY, userId);
    if (email) localStorage.setItem(WEB_USER_EMAIL_KEY, email);
  } catch {
    // ignore storage quota errors
  }

  return {
    id: userId,
    email,
    accessToken,
    refreshToken: refreshToken || undefined,
    expiresAt
  };
}

export function getStoredWebSession(): AuthSessionUser | null {
  try {
    const token = localStorage.getItem(WEB_ACCESS_TOKEN_KEY) || '';
    if (!token.startsWith('eyJ')) return null;
    const id = localStorage.getItem(WEB_USER_ID_KEY) || extractUserIdFromJwt(token) || '';
    if (!UUID_REGEX.test(id)) return null;
    const email = localStorage.getItem(WEB_USER_EMAIL_KEY) || String(decodeJwtPayload(token)?.email || '');
    const refreshToken = localStorage.getItem(WEB_REFRESH_TOKEN_KEY) || undefined;
    const expiresAtRaw = Number(localStorage.getItem(WEB_EXPIRES_AT_KEY) || '0');
    return {
      id,
      email,
      accessToken: token,
      refreshToken,
      expiresAt: expiresAtRaw || undefined
    };
  } catch {
    return null;
  }
}

export function clearWebSessionStorage(): void {
  try {
    localStorage.removeItem(WEB_ACCESS_TOKEN_KEY);
    localStorage.removeItem(WEB_REFRESH_TOKEN_KEY);
    localStorage.removeItem(WEB_EXPIRES_AT_KEY);
    localStorage.removeItem(WEB_USER_ID_KEY);
    localStorage.removeItem(WEB_USER_EMAIL_KEY);
  } catch {
    // ignore
  }
}

let inFlightRefresh: Promise<AuthSessionUser | null> | null = null;

export async function refreshWebAccessToken(): Promise<AuthSessionUser | null> {
  if (inFlightRefresh) return inFlightRefresh;

  inFlightRefresh = (async () => {
    const refreshToken = (() => {
      try {
        return localStorage.getItem(WEB_REFRESH_TOKEN_KEY) || '';
      } catch {
        return '';
      }
    })();

    if (!refreshToken) return null;

    try {
      const res = await fetch(`${SUPABASE_URL}/auth/v1/token?grant_type=refresh_token`, {
        method: 'POST',
        headers: {
          'apikey': SUPABASE_ANON_KEY,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ refresh_token: refreshToken })
      });

      if (!res.ok) {
        if (res.status === 400 || res.status === 401) {
          clearWebSessionStorage();
        }
        return null;
      }

      const data = await res.json();
      return persistWebSession(data);
    } catch {
      return null;
    } finally {
      inFlightRefresh = null;
    }
  })();

  return inFlightRefresh;
}

export async function getValidWebAccessToken(): Promise<string | null> {
  const session = getStoredWebSession();
  if (!session) return null;

  if (isTokenExpiringSoon(session.accessToken, 45)) {
    const refreshed = await refreshWebAccessToken();
    if (refreshed?.accessToken) return refreshed.accessToken;
    if (isTokenExpiringSoon(session.accessToken, 0)) return null;
  }
  return session.accessToken;
}

async function getOptionalRestHeaders(preferRepresentation: boolean = true): Promise<Record<string, string>> {
  const token = await getValidWebAccessToken();
  return {
    'apikey': SUPABASE_ANON_KEY,
    ...(token
      ? { 'Authorization': `Bearer ${token}` }
      : SUPABASE_ANON_KEY.startsWith('eyJ')
        ? { 'Authorization': `Bearer ${SUPABASE_ANON_KEY}` }
        : {}),
    'Content-Type': 'application/json',
    ...(preferRepresentation ? { 'Prefer': 'return=representation' } : {})
  };
}

function getWebAuthHeaders(): Record<string, string> | null {
  try {
    const jwt = localStorage.getItem(WEB_ACCESS_TOKEN_KEY) || '';
    if (!jwt || !jwt.startsWith('eyJ')) return null;
    return {
      'apikey': SUPABASE_ANON_KEY,
      'Authorization': `Bearer ${jwt}`,
      'Content-Type': 'application/json'
    };
  } catch {
    return null;
  }
}

async function authedFetch(
  input: string,
  init: RequestInit = {},
  extraHeaders: Record<string, string> = {}
): Promise<Response> {
  const token = await getValidWebAccessToken();
  if (!token) {
    throw new Error('Authentication required');
  }
  const doFetch = (jwt: string) =>
    fetch(input, {
      ...init,
      headers: {
        'apikey': SUPABASE_ANON_KEY,
        'Authorization': `Bearer ${jwt}`,
        'Content-Type': 'application/json',
        ...extraHeaders,
        ...((init.headers as Record<string, string>) || {})
      }
    });

  let res = await doFetch(token);
  if (res.status === 401) {
    const refreshed = await refreshWebAccessToken();
    if (refreshed?.accessToken) {
      res = await doFetch(refreshed.accessToken);
    }
  }
  return res;
}

function parseSupabaseError(errObj: any, fallback: string): string {
  if (!errObj) return fallback;
  const msg = String(
    errObj?.message || errObj?.error_description || errObj?.msg || errObj?.details || errObj?.hint || ''
  ).trim();
  return msg || fallback;
}

// -------------------------------------------------------------
// Web User Authentication (Email / @username + Password, Sign Up, Recovery)
// -------------------------------------------------------------
export async function resolveLoginIdentifier(identifier: string): Promise<{
  found: boolean;
  mode?: 'email' | 'phone';
  email?: string;
  phone?: string;
}> {
  const raw = identifier.trim();
  if (!raw) return { found: false };
  if (raw.includes('@') && !raw.startsWith('@')) {
    return { found: true, mode: 'email', email: raw.toLowerCase() };
  }
  const normalizedIdentifier = raw.startsWith('@') ? raw.slice(1).trim() : raw;
  if (!normalizedIdentifier) return { found: false };
  try {
    const res = await fetch(`${SUPABASE_URL}/rest/v1/rpc/resolve_login_identifier`, {
      method: 'POST',
      headers: {
        'apikey': SUPABASE_ANON_KEY,
        ...(SUPABASE_ANON_KEY.startsWith('eyJ') ? { 'Authorization': `Bearer ${SUPABASE_ANON_KEY}` } : {}),
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ p_identifier: normalizedIdentifier })
    });
    if (!res.ok) return { found: false };
    const data = await res.json();
    return {
      found: Boolean(data?.found),
      mode: data?.mode,
      email: data?.email ? String(data.email).toLowerCase() : undefined,
      phone: data?.phone ? String(data.phone) : undefined
    };
  } catch {
    return { found: false };
  }
}

export async function signInWithIdentifier(
  identifier: string,
  password: string
): Promise<{ success: boolean; session?: AuthSessionUser; profile?: User; error?: string }> {
  const cleanId = identifier.trim();
  if (!cleanId || !password) {
    return { success: false, error: 'Please enter your email or @username and password.' };
  }

  let targetEmail = '';
  let targetPhone = '';

  if (cleanId.includes('@') && !cleanId.startsWith('@')) {
    targetEmail = cleanId.toLowerCase();
  } else {
    const resolved = await resolveLoginIdentifier(cleanId);
    if (resolved.email) {
      targetEmail = resolved.email;
    } else if (resolved.phone) {
      targetPhone = resolved.phone;
    } else {
      return {
        success: false,
        error: 'No active VYBE account matches that username. Try your registered email address.'
      };
    }
  }

  if (!targetEmail && targetPhone) {
    return {
      success: false,
      error: 'Phone sign-in is disabled on this project. Please sign in with your registered email address or @username.'
    };
  }

  try {
    const res = await fetch(`${SUPABASE_URL}/auth/v1/token?grant_type=password`, {
      method: 'POST',
      headers: {
        'apikey': SUPABASE_ANON_KEY,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ email: targetEmail, password })
    });

    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      return {
        success: false,
        error: parseSupabaseError(data, 'Invalid credentials. Please check your password and try again.')
      };
    }

    const session = persistWebSession(data);
    if (!session) {
      return { success: false, error: 'Authentication response was incomplete. Please try again.' };
    }

    const ownProfile = await fetchMyAuthenticatedProfile(session.id, session.accessToken);
    return {
      success: true,
      session,
      profile: ownProfile || undefined
    };
  } catch (err: any) {
    return { success: false, error: err?.message || 'Network error while signing in.' };
  }
}

export async function signUpWithEmail(params: {
  email: string;
  password: string;
  username: string;
  displayName: string;
}): Promise<{ success: boolean; needsEmailConfirmation?: boolean; session?: AuthSessionUser; profile?: User; error?: string }> {
  const email = params.email.trim().toLowerCase();
  const username = params.username.trim().replace(/^@/, '').toLowerCase().replace(/[^a-z0-9._]/g, '');
  const displayName = params.displayName.trim() || username;

  if (!email || !email.includes('@')) {
    return { success: false, error: 'Enter a valid email address.' };
  }
  if (username.length < 3) {
    return { success: false, error: 'Username must be at least 3 characters (letters, numbers, dots or underscores).' };
  }
  if (params.password.length < 6) {
    return { success: false, error: 'Password must be at least 6 characters.' };
  }

  try {
    const availRes = await fetch(`${SUPABASE_URL}/rest/v1/rpc/check_username_available`, {
      method: 'POST',
      headers: {
        'apikey': SUPABASE_ANON_KEY,
        ...(SUPABASE_ANON_KEY.startsWith('eyJ') ? { 'Authorization': `Bearer ${SUPABASE_ANON_KEY}` } : {}),
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ p_username: username })
    }).catch(() => null);

    if (availRes && availRes.ok) {
      const isAvail = await availRes.json().catch(() => true);
      if (isAvail === false) {
        return { success: false, error: `@${username} is already taken. Choose another username.` };
      }
    }

    const redirectOrigin = typeof window !== 'undefined' ? window.location.origin : 'https://vybeonline.netlify.app';
    const res = await fetch(
      `${SUPABASE_URL}/auth/v1/signup?redirect_to=${encodeURIComponent(`${redirectOrigin}/`)}`,
      {
        method: 'POST',
        headers: {
          'apikey': SUPABASE_ANON_KEY,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          email,
          password: params.password,
          data: {
            username,
            display_name: displayName
          }
        })
      }
    );

    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      return {
        success: false,
        error: parseSupabaseError(data, 'Could not create VYBE account.')
      };
    }

    if (data?.access_token) {
      const session = persistWebSession(data);
      if (session) {
        const ownProfile = await fetchMyAuthenticatedProfile(session.id, session.accessToken);
        return { success: true, session, profile: ownProfile || undefined };
      }
    }

    return {
      success: true,
      needsEmailConfirmation: true
    };
  } catch (err: any) {
    return { success: false, error: err?.message || 'Network error creating account.' };
  }
}

export async function sendPasswordResetEmail(email: string): Promise<{ success: boolean; error?: string }> {
  const cleanEmail = email.trim().toLowerCase();
  if (!cleanEmail || !cleanEmail.includes('@')) {
    return { success: false, error: 'Please enter a valid email address.' };
  }
  try {
    const redirectOrigin = typeof window !== 'undefined' ? window.location.origin : 'https://vybeonline.netlify.app';
    const res = await fetch(
      `${SUPABASE_URL}/auth/v1/recover?redirect_to=${encodeURIComponent(`${redirectOrigin}/`)}`,
      {
        method: 'POST',
        headers: {
          'apikey': SUPABASE_ANON_KEY,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ email: cleanEmail })
      }
    );
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      return { success: false, error: parseSupabaseError(err, 'Could not send recovery email.') };
    }
    return { success: true };
  } catch (err: any) {
    return { success: false, error: err?.message || 'Network error sending password reset.' };
  }
}

export async function signOutWebSession(): Promise<void> {
  const token = (() => {
    try {
      return localStorage.getItem(WEB_ACCESS_TOKEN_KEY) || '';
    } catch {
      return '';
    }
  })();

  clearWebSessionStorage();

  if (token.startsWith('eyJ')) {
    try {
      await fetch(`${SUPABASE_URL}/auth/v1/logout`, {
        method: 'POST',
        headers: {
          'apikey': SUPABASE_ANON_KEY,
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        },
        body: '{}'
      });
    } catch {
      // ignore remote revoke network failures after clearing local storage
    }
  }
}

export async function fetchMyAuthenticatedProfile(
  userId: string,
  explicitToken?: string
): Promise<User | null> {
  const token = explicitToken || (await getValidWebAccessToken());
  if (!token || !UUID_REGEX.test(userId)) return null;

  try {
    const res = await fetch(
      `${SUPABASE_URL}/rest/v1/profiles?id=eq.${encodeURIComponent(userId)}&select=${encodeURIComponent(PROFILE_SELECT_COLUMNS)}&limit=1`,
      {
        headers: {
          'apikey': SUPABASE_ANON_KEY,
          'Authorization': `Bearer ${token}`
        }
      }
    );
    if (!res.ok) return null;
    const rows = await res.json();
    const row = Array.isArray(rows) ? rows[0] : rows;
    return row?.id ? mapSupabaseProfileToUser(row) : null;
  } catch {
    return null;
  }
}

// -------------------------------------------------------------
// Explicit Column Selections (No PII / No `select=*` waste)
// -------------------------------------------------------------
const PROFILE_SELECT_COLUMNS = [
  'id',
  'username',
  'display_name',
  'first_name',
  'last_name',
  'avatar_url',
  'cover_url',
  'bio',
  'is_verified',
  'is_verified_public_figure',
  'is_vip',
  'is_worker',
  'is_verified_business',
  'followers_count',
  'following_count',
  'posts_count',
  'current_mood',
  'live_pulse',
  'last_seen_at',
  'created_at',
  'is_private_account',
  'is_deactivated',
  'message_privacy',
  'role'
].join(',');

const STORY_PROFILE_COLUMNS =
  'id,username,display_name,avatar_url,is_verified,is_verified_public_figure,is_vip,is_worker,is_verified_business,is_private_account';

const POST_SELECT_COLUMNS = [
  'id',
  'author_id',
  'caption',
  'media_type',
  'is_vybes_reel',
  'is_unfiltered',
  'is_archived',
  'likes_count',
  'comments_count',
  'saves_count',
  'views_count',
  'mood_zone',
  'love_count',
  'flame_count',
  'laugh_count',
  'wow_count',
  'sad_count',
  'created_at',
  `profiles:author_id(${PROFILE_SELECT_COLUMNS})`,
  'post_media(id,media_url,media_type,display_order)'
].join(',');

const STORY_SELECT_COLUMNS = [
  'id',
  'author_id',
  'media_url',
  'caption',
  'media_type',
  'story_type',
  'text_bg_key',
  'audience',
  'music_title',
  'created_at',
  'expires_at',
  `profiles:author_id(${STORY_PROFILE_COLUMNS})`
].join(',');

function normalizeLivePulse(raw: any): LivePulseStatus {
  const val = String(raw || '').trim();
  if (!val) return 'None';
  const upper = val.toUpperCase();
  if (upper === 'OPEN_TO_CHAT' || val === 'Open to chatting') return 'Open to chatting';
  if (upper === 'FEELING_CREATIVE' || upper === 'FOCUSED' || val === 'Feeling creative') return 'Feeling creative';
  if (upper === 'LISTENING_MUSIC' || upper === 'LISTENING_TO_MUSIC' || upper === 'DEEP_DIVE' || val === 'Listening to music') return 'Listening to music';
  if (upper === 'GAMING_PARTNER' || upper === 'GAMING' || upper === 'LOOKING_FOR_ADVENTURE' || val === 'Looking for gaming partner') return 'Looking for gaming partner';
  if (upper === 'QUIET_TIME' || upper === 'QUIET_MODE' || val === 'Taking quiet time') return 'Taking quiet time';
  if (upper === 'NONE' || val === 'None') return 'None';
  return 'Open to chatting';
}

export function toDbLivePulse(raw: any): string {
  const val = String(raw || '').trim();
  const upper = val.toUpperCase();
  if (!val || upper === 'NONE' || val === 'None') return 'NONE';
  if (upper === 'OPEN_TO_CHAT' || val === 'Open to chatting') return 'OPEN_TO_CHAT';
  if (upper === 'FEELING_CREATIVE' || val === 'Feeling creative') return 'FEELING_CREATIVE';
  if (upper === 'LISTENING_MUSIC' || upper === 'LISTENING_TO_MUSIC' || val === 'Listening to music') return 'LISTENING_MUSIC';
  if (upper === 'GAMING_PARTNER' || upper === 'GAMING' || val === 'Looking for gaming partner') return 'GAMING_PARTNER';
  if (upper === 'QUIET_TIME' || upper === 'QUIET_MODE' || val === 'Taking quiet time') return 'QUIET_TIME';
  return 'OPEN_TO_CHAT';
}

const LEGACY_LIVE_PULSE_FALLBACK: Record<string, string> = {
  FEELING_CREATIVE: 'FOCUSED',
  LISTENING_MUSIC: 'DEEP_DIVE',
  GAMING_PARTNER: 'LOOKING_FOR_ADVENTURE',
  QUIET_TIME: 'QUIET_MODE'
};

const BASE_MOOD_ENUM_VALUES = new Set([
  'HAPPY',
  'SAD',
  'PEACEFUL',
  'HOPEFUL',
  'REFLECTIVE',
  'LONELY',
  'MOTIVATED',
  'GRATEFUL',
  'EXCITED',
  'HEARTBREAK',
  'CHILL',
  'HEALING',
  'FUN'
]);

function normalizeMessagePrivacy(raw: any): 'EVERYONE' | 'FOLLOWERS' | 'NOBODY' {
  const upper = String(raw || 'EVERYONE').toUpperCase();
  if (upper.includes('NO')) return 'NOBODY';
  if (upper.includes('FOLLOW') || upper.includes('MUTUAL')) return 'FOLLOWERS';
  return 'EVERYONE';
}

export function mapSupabaseProfileToUser(p: any): User {
  const isVerified = Boolean(p.is_verified || p.is_verified_public_figure);
  const isVip = Boolean(p.is_vip);
  const isWorker = Boolean(p.is_worker);
  const isShop = Boolean(p.is_verified_business);

  const badges: OfficialVybeBadge[] = [];
  if (isVerified) badges.push('VERIFIED_BLUE');
  if (isVip) badges.push('VIP_GOLD');
  if (isWorker) badges.push('WORKER_GREEN');
  if (isShop) badges.push('SHOP_GOLD');

  return {
    id: p.id,
    username: p.username || `user_${p.id ? String(p.id).slice(0, 6) : 'vybe'}`,
    displayName: p.display_name || p.username || 'VYBE Member',
    firstName: p.first_name || undefined,
    lastName: p.last_name || undefined,
    publicVybeId: `VYBE-${(p.username || (p.id ? String(p.id).slice(0, 6) : 'MEMBER')).toUpperCase()}`,
    bio: p.bio || '',
    avatarUrl: resolveMediaUrl(p.avatar_url || ''),
    coverUrl: resolveMediaUrl(p.cover_url || ''),
    isVerified,
    isVip,
    isVerifiedBusiness: isShop,
    isWorker,
    followersCount: Number(p.followers_count || 0),
    followingCount: Number(p.following_count || 0),
    postsCount: Number(p.posts_count || 0),
    currentMood: (p.current_mood as MoodType) || 'GENERAL',
    livePulse: normalizeLivePulse(p.live_pulse),
    isOnline: Boolean(p.last_seen_at && Date.now() - new Date(p.last_seen_at).getTime() < 3600000 * 6),
    badges,
    joinedDate: p.created_at
      ? new Date(p.created_at).toLocaleDateString('en-US', { month: 'short', year: 'numeric' })
      : 'Member',
    isPrivate: Boolean(p.is_private_account),
    messagePrivacy: normalizeMessagePrivacy(p.message_privacy),
    role: p.role || 'USER'
  };
}

function buildFallbackMinimalUser(userId?: string, label: string = 'VYBE Member'): User {
  const safeId = userId || 'unknown';
  return {
    id: safeId,
    username: `user_${String(safeId).slice(0, 6)}`,
    displayName: label,
    publicVybeId: `VYBE-${String(safeId).slice(0, 6).toUpperCase()}`,
    bio: '',
    avatarUrl: '',
    coverUrl: '',
    isVerified: false,
    isVip: false,
    isVerifiedBusiness: false,
    isWorker: false,
    followersCount: 0,
    followingCount: 0,
    postsCount: 0,
    livePulse: 'None',
    isOnline: false,
    badges: [],
    joinedDate: 'Member'
  };
}

export function mapSupabasePost(row: any, userMap?: Map<string, User>): Post {
  let authorUser: User;
  if (row.profiles) {
    authorUser = mapSupabaseProfileToUser(row.profiles);
  } else if (userMap && userMap.has(row.author_id)) {
    authorUser = userMap.get(row.author_id)!;
  } else {
    authorUser = buildFallbackMinimalUser(row.author_id, 'VYBE Member');
  }

  const mediaUrls: string[] = [];
  if (Array.isArray(row.post_media) && row.post_media.length > 0) {
    const sorted = [...row.post_media].sort(
      (a, b) => Number(a.display_order || 0) - Number(b.display_order || 0)
    );
    for (const m of sorted) {
      if (m.media_url) mediaUrls.push(resolveMediaUrl(m.media_url));
    }
  } else if (row.media_url) {
    mediaUrls.push(resolveMediaUrl(row.media_url));
  } else if (row.media_urls && Array.isArray(row.media_urls)) {
    mediaUrls.push(...row.media_urls.map((u: string) => resolveMediaUrl(u)));
  }

  const isVideo =
    row.media_type === 'VIDEO' ||
    row.media_type === 'REEL' ||
    mediaUrls.some(u => u.includes('.mp4') || u.includes('.webm'));
  const isVybesReel = Boolean(row.is_vybes_reel || row.media_type === 'REEL');

  return {
    id: row.id,
    author: authorUser,
    caption: row.caption || '',
    mediaUrls,
    mediaType: isVybesReel ? 'REEL' : isVideo ? 'VIDEO' : mediaUrls.length > 0 ? 'IMAGE' : 'TEXT',
    timestamp: row.created_at ? formatRelativeTime(row.created_at) : 'Just now',
    createdAtIso: row.created_at,
    likesCount: Number(row.likes_count || 0),
    commentsCount: Number(row.comments_count || 0),
    savesCount: Number(row.saves_count || 0),
    sharesCount: Number(row.shares_count || 0),
    viewsCount: Number(row.views_count || 0),
    isLiked: false,
    isSaved: false,
    isUnfiltered: Boolean(row.is_unfiltered),
    isVybesReel,
    tags: row.caption ? (row.caption.match(/#[a-z0-9_]+/gi) || []).map((t: string) => t.replace('#', '')) : [],
    moodZone: (row.mood_zone as MoodType) || 'GENERAL',
    comments: [],
    reactionsCount: {
      LOVE: Number(row.love_count || row.likes_count || 0),
      FLAME: Number(row.flame_count || 0),
      LAUGH: Number(row.laugh_count || 0),
      WOW: Number(row.wow_count || 0),
      SAD: Number(row.sad_count || 0)
    }
  };
}

function normalizeStoryAudience(raw: any): Story['audience'] {
  const upper = String(raw || 'EVERYONE').toUpperCase();
  if (upper === 'FOLLOWERS') return 'Followers';
  if (upper === 'VYBE_MATCHES' || upper === 'CLOSE_FRIENDS') return 'VYBE Matches';
  if (upper === 'ONLY_ME') return 'Only Me';
  return 'Public';
}

export function mapSupabaseStory(row: any, userMap?: Map<string, User>): Story {
  let authorUser: User;
  if (row.profiles) {
    authorUser = mapSupabaseProfileToUser(row.profiles);
  } else if (userMap && userMap.has(row.author_id)) {
    authorUser = userMap.get(row.author_id)!;
  } else {
    authorUser = buildFallbackMinimalUser(row.author_id, 'VYBE Member');
  }

  const expiresAt = row.expires_at ? new Date(row.expires_at).getTime() : Date.now() + 24 * 3600000;
  const remainingHours = Math.max(1, Math.round((expiresAt - Date.now()) / 3600000));
  const rawUrl = String(row.media_url || '').trim();
  const normalizedUrl = rawUrl === 'text://story' ? '' : resolveMediaUrl(rawUrl);
  const rawType = String(row.media_type || row.story_type || 'PHOTO').toUpperCase();

  return {
    id: row.id,
    author: authorUser,
    mediaUrl: normalizedUrl,
    caption: row.caption || '',
    mediaType:
      rawType === 'VIDEO'
        ? 'VIDEO'
        : rawType === 'TEXT'
          ? 'TEXT'
          : rawType === 'MUSIC'
            ? 'MUSIC'
            : 'PHOTO',
    audience: normalizeStoryAudience(row.audience),
    musicTrackTitle: row.music_title || undefined,
    textBackgroundColor: row.text_bg_key || undefined,
    timestamp: row.created_at ? formatRelativeTime(row.created_at) : 'Just now',
    createdAtIso: row.created_at,
    expiresAtIso: row.expires_at,
    expiresHoursRemaining: remainingHours,
    isViewed: false,
    reactionsCount: 0,
    isLikedByMe: false
  };
}

export function formatRelativeTime(isoString?: string | null): string {
  if (!isoString) return 'Just now';
  const ts = Date.parse(isoString);
  if (Number.isNaN(ts)) return 'Recently';
  const diffSec = Math.max(0, Math.floor((Date.now() - ts) / 1000));
  if (diffSec < 60) return 'Just now';
  if (diffSec < 3600) return `${Math.floor(diffSec / 60)}m`;
  if (diffSec < 86400) return `${Math.floor(diffSec / 3600)}h`;
  if (diffSec < 604800) return `${Math.floor(diffSec / 86400)}d`;
  return new Date(ts).toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

export function formatChatClock(isoString?: string | null): string {
  if (!isoString) return 'Just now';
  const ts = Date.parse(isoString);
  if (Number.isNaN(ts)) return 'Just now';
  return new Date(ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

// -------------------------------------------------------------
// 1. Fetch live profiles & deep-link profile resolution
// -------------------------------------------------------------
export async function fetchLiveProfiles(options: { forceRefresh?: boolean; throwOnError?: boolean } = {}): Promise<User[]> {
  const cacheKey = 'live_profiles_v3';

  if (!options.forceRefresh) {
    const cached = await vybeCache.get<User[]>(cacheKey, { allowStale: true });
    if (cached && cached.data && cached.data.length > 0) {
      vybeDiagnostics.recordCacheHit(cacheKey);
      if (!cached.isStale) return cached.data;
    }
  }

  return deduplicatedFetch<User[]>(cacheKey, async () => {
    const startTime = Date.now();
    let lastErr: unknown = null;
    try {
      const hdrs = await getOptionalRestHeaders(false);
      const url = `${SUPABASE_URL}/rest/v1/profiles?select=${encodeURIComponent(PROFILE_SELECT_COLUMNS)}&is_deactivated=not.is.true&order=created_at.desc&limit=80`;
      const res = await fetch(url, { headers: hdrs });
      const duration = Date.now() - startTime;

      if (!res.ok) {
        vybeDiagnostics.recordNetworkRequest(url, duration, res.status);
        throw new Error(`HTTP ${res.status}`);
      }

      const data = await res.json();
      vybeDiagnostics.recordNetworkRequest(url, duration, 200, JSON.stringify(data).length);

      if (Array.isArray(data)) {
        const users = data.filter((r: any) => !r.is_deactivated).map(mapSupabaseProfileToUser);
        vybeCache.set(cacheKey, users, CACHE_TTL.PROFILES);
        return users;
      }
    } catch (err) {
      lastErr = err;
      console.warn('[Supabase] Failed to fetch profiles:', err);
    }

    const fallback = await vybeCache.get<User[]>(cacheKey, { allowStale: true });
    if (fallback?.data && fallback.data.length > 0) {
      return fallback.data;
    }
    if (options.throwOnError && lastErr) {
      throw lastErr instanceof Error ? lastErr : new Error('Unable to load VYBE profiles.');
    }
    return [];
  });
}

export async function fetchProfileByUsernameOrId(params: {
  username?: string | null;
  userId?: string | null;
}): Promise<{ status: 'FOUND' | 'NOT_FOUND' | 'ERROR'; user?: User; error?: string }> {
  const cleanUser = params.username ? params.username.trim().replace(/^@/, '') : '';
  const cleanId = params.userId ? params.userId.trim() : '';

  if (!cleanUser && !cleanId) return { status: 'NOT_FOUND' };

  try {
    const hdrs = await getOptionalRestHeaders(false);
    let url = '';
    if (cleanId && UUID_REGEX.test(cleanId)) {
      url = `${SUPABASE_URL}/rest/v1/profiles?id=eq.${encodeURIComponent(cleanId)}&select=${encodeURIComponent(PROFILE_SELECT_COLUMNS)}&limit=1`;
    } else if (cleanUser) {
      url = `${SUPABASE_URL}/rest/v1/profiles?username=ilike.${encodeURIComponent(cleanUser)}&select=${encodeURIComponent(PROFILE_SELECT_COLUMNS)}&limit=1`;
    } else {
      return { status: 'NOT_FOUND' };
    }

    const res = await fetch(url, { headers: hdrs });
    if (!res.ok) {
      return { status: 'ERROR', error: `Unable to load profile (${res.status})` };
    }
    const rows = await res.json();
    const row = Array.isArray(rows) ? rows[0] : rows;
    if (!row || !row.id || row.is_deactivated === true) {
      return { status: 'NOT_FOUND' };
    }
    return { status: 'FOUND', user: mapSupabaseProfileToUser(row) };
  } catch (err: any) {
    return { status: 'ERROR', error: err?.message || 'Network error loading profile.' };
  }
}

export async function fetchPostsByAuthor(authorId: string, limit: number = 30): Promise<Post[]> {
  if (!UUID_REGEX.test(authorId)) return [];
  try {
    const hdrs = await getOptionalRestHeaders(false);
    const url = `${SUPABASE_URL}/rest/v1/posts?author_id=eq.${encodeURIComponent(authorId)}&is_archived=not.is.true&select=${encodeURIComponent(POST_SELECT_COLUMNS)}&order=created_at.desc&limit=${limit}`;
    const res = await fetch(url, { headers: hdrs });
    if (!res.ok) return [];
    const rows = await res.json();
    return Array.isArray(rows) ? rows.map(row => mapSupabasePost(row)) : [];
  } catch {
    return [];
  }
}

export async function fetchSinglePostById(postId: string): Promise<{
  status: 'FOUND' | 'NOT_FOUND' | 'ERROR';
  post?: Post;
  error?: string;
}> {
  const cleanId = postId.trim();
  if (!cleanId) return { status: 'NOT_FOUND' };

  try {
    const hdrs = await getOptionalRestHeaders(false);
    const url = `${SUPABASE_URL}/rest/v1/posts?id=eq.${encodeURIComponent(cleanId)}&select=${encodeURIComponent(POST_SELECT_COLUMNS)}&limit=1`;
    const res = await fetch(url, { headers: hdrs });
    if (!res.ok) {
      return { status: 'ERROR', error: `Unable to load post (${res.status})` };
    }
    const rows = await res.json();
    const row = Array.isArray(rows) ? rows[0] : rows;
    if (!row || !row.id || row.is_archived === true) {
      return { status: 'NOT_FOUND' };
    }
    return { status: 'FOUND', post: mapSupabasePost(row) };
  } catch (err: any) {
    return { status: 'ERROR', error: err?.message || 'Network error loading post.' };
  }
}

// -------------------------------------------------------------
// 2. Fetch live posts with pagination & explicit columns
// -------------------------------------------------------------
export async function fetchLivePosts(
  userMap?: Map<string, User>,
  options: {
    limit?: number;
    cursorCreatedAt?: string;
    forceRefresh?: boolean;
    throwOnError?: boolean;
  } = {}
): Promise<Post[]> {
  const limit = options.limit || 20;
  const isFirstPage = !options.cursorCreatedAt;
  const cacheKey = isFirstPage ? `live_posts_first_page_${limit}` : `live_posts_cursor_${options.cursorCreatedAt}_${limit}`;

  if (!options.forceRefresh && isFirstPage) {
    const cached = await vybeCache.get<Post[]>(cacheKey, { allowStale: true });
    if (cached && cached.data && cached.data.length > 0) {
      vybeDiagnostics.recordCacheHit(cacheKey);
      if (!cached.isStale) return cached.data;
    }
  }

  return deduplicatedFetch<Post[]>(cacheKey, async () => {
    const startTime = Date.now();
    let lastErr: unknown = null;
    try {
      const hdrs = await getOptionalRestHeaders(false);
      let queryParams = `select=${encodeURIComponent(POST_SELECT_COLUMNS)}&is_archived=not.is.true&order=created_at.desc&limit=${limit}`;
      if (options.cursorCreatedAt) {
        queryParams += `&created_at=lt.${encodeURIComponent(options.cursorCreatedAt)}`;
      }

      const url = `${SUPABASE_URL}/rest/v1/posts?${queryParams}`;
      const res = await fetch(url, { headers: hdrs });
      const duration = Date.now() - startTime;

      if (!res.ok) {
        vybeDiagnostics.recordNetworkRequest(url, duration, res.status);
        throw new Error(`HTTP ${res.status}`);
      }

      const data = await res.json();
      vybeDiagnostics.recordNetworkRequest(url, duration, 200, JSON.stringify(data).length);

      if (Array.isArray(data)) {
        const posts = data.map(row => mapSupabasePost(row, userMap));
        if (isFirstPage) {
          vybeCache.set(cacheKey, posts, CACHE_TTL.POSTS_FEED);
        }
        return posts;
      }
    } catch (err) {
      lastErr = err;
      console.warn('[Supabase] Failed to fetch posts:', err);
    }

    if (isFirstPage) {
      const fallback = await vybeCache.get<Post[]>(cacheKey, { allowStale: true });
      if (fallback?.data && fallback.data.length > 0) {
        return fallback.data;
      }
    }
    if (options.throwOnError && lastErr) {
      throw lastErr instanceof Error ? lastErr : new Error('Unable to load VYBE feed posts.');
    }
    return [];
  });
}

// -------------------------------------------------------------
// 3. Fetch live stories
// -------------------------------------------------------------
export async function fetchLiveStories(
  userMap?: Map<string, User>,
  options: { forceRefresh?: boolean; throwOnError?: boolean } = {}
): Promise<Story[]> {
  const cacheKey = 'live_stories_v3';

  if (!options.forceRefresh) {
    const cached = await vybeCache.get<Story[]>(cacheKey, { allowStale: true });
    if (cached && cached.data && cached.data.length > 0) {
      vybeDiagnostics.recordCacheHit(cacheKey);
      if (!cached.isStale) return cached.data;
    }
  }

  return deduplicatedFetch<Story[]>(cacheKey, async () => {
    const startTime = Date.now();
    let lastErr: unknown = null;
    try {
      const hdrs = await getOptionalRestHeaders(false);
      const nowIso = encodeURIComponent(new Date().toISOString());
      const url = `${SUPABASE_URL}/rest/v1/stories?expires_at=gt.${nowIso}&select=${encodeURIComponent(STORY_SELECT_COLUMNS)}&order=created_at.desc&limit=40`;
      const res = await fetch(url, { headers: hdrs });
      const duration = Date.now() - startTime;

      if (!res.ok) {
        vybeDiagnostics.recordNetworkRequest(url, duration, res.status);
        throw new Error(`HTTP ${res.status}`);
      }

      const data = await res.json();
      vybeDiagnostics.recordNetworkRequest(url, duration, 200, JSON.stringify(data).length);

      if (Array.isArray(data)) {
        const stories = data.map(row => mapSupabaseStory(row, userMap));
        vybeCache.set(cacheKey, stories, CACHE_TTL.STORIES);
        return stories;
      }
    } catch (err) {
      lastErr = err;
      console.warn('[Supabase] Failed to fetch stories:', err);
    }

    const fallback = await vybeCache.get<Story[]>(cacheKey, { allowStale: true });
    if (fallback?.data && fallback.data.length > 0) {
      return fallback.data;
    }
    if (options.throwOnError && lastErr) {
      throw lastErr instanceof Error ? lastErr : new Error('Unable to load VYBE stories.');
    }
    return [];
  });
}

// -------------------------------------------------------------
// 4. Fetch & submit comments
// -------------------------------------------------------------
export async function fetchCommentsForPost(
  postId: string,
  userMap?: Map<string, User>
): Promise<PostComment[]> {
  if (!postId || !UUID_REGEX.test(postId)) return [];
  const cacheKey = `comments_${postId}`;

  const cached = await vybeCache.get<PostComment[]>(cacheKey, { allowStale: true });
  if (cached && cached.data && cached.data.length > 0 && !cached.isStale) {
    vybeDiagnostics.recordCacheHit(cacheKey);
    return cached.data;
  }

  return deduplicatedFetch<PostComment[]>(cacheKey, async () => {
    try {
      const hdrs = await getOptionalRestHeaders(false);
      const commentCols = `id,post_id,author_id,content,created_at,profiles:author_id(${STORY_PROFILE_COLUMNS})`;
      const url = `${SUPABASE_URL}/rest/v1/comments?post_id=eq.${encodeURIComponent(postId)}&select=${encodeURIComponent(commentCols)}&order=created_at.asc&limit=50`;
      const res = await fetch(url, { headers: hdrs });

      if (!res.ok) return cached?.data || [];
      const data = await res.json();

      if (Array.isArray(data)) {
        const mapped = data.map(c => {
          const author = c.profiles
            ? mapSupabaseProfileToUser(c.profiles)
            : userMap?.get(c.author_id) || buildFallbackMinimalUser(c.author_id);

          return {
            id: c.id,
            postId: c.post_id,
            author,
            content: c.content || '',
            timestamp: c.created_at ? formatRelativeTime(c.created_at) : 'Just now',
            createdAtIso: c.created_at,
            likesCount: Number(c.likes_count || 0),
            isLiked: false
          };
        });

        vybeCache.set(cacheKey, mapped, CACHE_TTL.COMMENTS);
        return mapped;
      }
    } catch (err) {
      console.warn('[Supabase] Failed to fetch comments for post:', postId, err);
    }
    return cached?.data || [];
  });
}

export async function submitLiveComment(
  postId: string,
  authorId: string,
  content: string
): Promise<any> {
  if (!UUID_REGEX.test(postId) || !UUID_REGEX.test(authorId)) return null;
  try {
    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/comments`,
      {
        method: 'POST',
        body: JSON.stringify({
          post_id: postId,
          author_id: authorId,
          content
        })
      },
      { 'Prefer': 'return=representation' }
    );
    if (!res.ok) return null;
    const data = await res.json();
    await vybeCache.delete(`comments_${postId}`);
    return Array.isArray(data) ? data[0] : data;
  } catch {
    return null;
  }
}

// -------------------------------------------------------------
// 5. Posts & Stories Creation + User Graph Sync (Likes, Saves, Follows)
// -------------------------------------------------------------
export async function createLivePost(postData: {
  authorId: string;
  caption: string;
  mediaUrls: string[];
  mediaType: string;
  isVybesReel?: boolean;
  moodZone?: string;
  locationName?: string;
  audioTrackTitle?: string;
}): Promise<any> {
  if (!UUID_REGEX.test(postData.authorId)) return null;
  try {
    const isReel = postData.isVybesReel || postData.mediaType === 'REEL';
    const isVideo =
      postData.mediaType === 'VIDEO' || isReel || postData.mediaUrls.some(u => u.includes('.mp4'));
    const rawMood = postData.moodZone ? String(postData.moodZone).toUpperCase().trim() : '';

    const postPayload: Record<string, any> = {
      author_id: postData.authorId,
      caption: postData.caption || '',
      media_type: isReel ? 'REEL' : isVideo ? 'VIDEO' : postData.mediaUrls.length > 0 ? 'IMAGE' : 'TEXT',
      mood_zone: rawMood || null,
      is_vybes_reel: Boolean(isReel),
      is_unfiltered: false,
      likes_count: 0,
      comments_count: 0,
      saves_count: 0,
      views_count: 1,
      ...(postData.locationName ? { location_name: postData.locationName } : {}),
      ...(postData.audioTrackTitle ? { music_title: postData.audioTrackTitle } : {})
    };

    let res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/posts`,
      {
        method: 'POST',
        body: JSON.stringify(postPayload)
      },
      { 'Prefer': 'return=representation' }
    );

    if (res.status === 400 && postPayload.mood_zone && !BASE_MOOD_ENUM_VALUES.has(postPayload.mood_zone)) {
      res = await authedFetch(
        `${SUPABASE_URL}/rest/v1/posts`,
        {
          method: 'POST',
          body: JSON.stringify({ ...postPayload, mood_zone: null })
        },
        { 'Prefer': 'return=representation' }
      );
    } else if (res.status === 400 && postPayload.mood_zone) {
      res = await authedFetch(
        `${SUPABASE_URL}/rest/v1/posts`,
        {
          method: 'POST',
          body: JSON.stringify({ ...postPayload, mood_zone: null })
        },
        { 'Prefer': 'return=representation' }
      );
    }

    if (!res.ok) return null;

    const createdRows = await res.json();
    const created = Array.isArray(createdRows) ? createdRows[0] : createdRows;

    if (created?.id && postData.mediaUrls?.length > 0) {
      const mediaInserts = postData.mediaUrls.map((url, idx) => ({
        post_id: created.id,
        media_url: url,
        media_type: url.includes('.mp4') ? 'VIDEO' : 'IMAGE',
        display_order: idx
      }));

      const mediaRes = await authedFetch(
        `${SUPABASE_URL}/rest/v1/post_media`,
        {
          method: 'POST',
          body: JSON.stringify(mediaInserts)
        },
        { 'Prefer': 'return=minimal' }
      );
      if (!mediaRes.ok) {
        console.warn('[Supabase] post_media insert failed:', mediaRes.status);
      }
    }

    await vybeCache.invalidatePrefix('live_posts_');
    return created;
  } catch (err) {
    console.warn('[Supabase] createLivePost exception:', err);
    return null;
  }
}

export async function createLiveStory(storyData: {
  authorId: string;
  mediaUrl: string;
  caption?: string;
  mediaType?: string;
  audience?: Story['audience'];
  textBackgroundColor?: string;
  musicTrackTitle?: string;
}): Promise<any> {
  if (!UUID_REGEX.test(storyData.authorId)) return null;
  try {
    const cleanMediaUrl = String(storyData.mediaUrl || '').trim() || 'text://story';
    const resolvedType = String(
      storyData.mediaType || (cleanMediaUrl.includes('.mp4') ? 'VIDEO' : cleanMediaUrl === 'text://story' ? 'TEXT' : 'PHOTO')
    ).toUpperCase();
    const audienceDb =
      storyData.audience === 'Followers'
        ? 'FOLLOWERS'
        : storyData.audience === 'VYBE Matches'
          ? 'VYBE_MATCHES'
          : storyData.audience === 'Only Me'
            ? 'ONLY_ME'
            : 'EVERYONE';

    const payload: Record<string, any> = {
      author_id: storyData.authorId,
      media_url: cleanMediaUrl,
      caption: storyData.caption || '',
      media_type: resolvedType,
      story_type: resolvedType,
      audience: audienceDb,
      ...(storyData.textBackgroundColor ? { text_bg_key: storyData.textBackgroundColor } : {}),
      ...(storyData.musicTrackTitle ? { music_title: storyData.musicTrackTitle } : {}),
      expires_at: new Date(Date.now() + 24 * 3600000).toISOString()
    };

    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/stories`,
      {
        method: 'POST',
        body: JSON.stringify(payload)
      },
      { 'Prefer': 'return=representation' }
    );

    if (res.ok) {
      const data = await res.json();
      await vybeCache.delete('live_stories_v3');
      return Array.isArray(data) ? data[0] : data;
    }
  } catch (err) {
    console.warn('[Supabase] createLiveStory exception:', err);
  }
  return null;
}

export async function syncPostLikeCountToSupabase(_postId: string, _nextLikesCount: number): Promise<void> {
  // Handled server-side by post_reactions database triggers; direct PATCH from non-authors fails RLS.
}

export async function togglePostReactionInSupabase(params: {
  postId: string;
  userId: string;
  reactionType: VybeReaction | null;
}): Promise<boolean> {
  if (!UUID_REGEX.test(params.postId) || !UUID_REGEX.test(params.userId)) return false;
  try {
    if (!params.reactionType) {
      const res = await authedFetch(
        `${SUPABASE_URL}/rest/v1/post_reactions?post_id=eq.${encodeURIComponent(params.postId)}&user_id=eq.${encodeURIComponent(params.userId)}`,
        { method: 'DELETE' }
      );
      return res.ok;
    }
    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/post_reactions?on_conflict=post_id,user_id`,
      {
        method: 'POST',
        body: JSON.stringify({
          post_id: params.postId,
          user_id: params.userId,
          reaction_type: params.reactionType
        })
      },
      { 'Prefer': 'resolution=merge-duplicates,return=minimal' }
    );
    return res.ok;
  } catch {
    return false;
  }
}

export async function toggleSavePostInSupabase(params: {
  postId: string;
  userId: string;
  shouldSave: boolean;
}): Promise<boolean> {
  if (!UUID_REGEX.test(params.postId) || !UUID_REGEX.test(params.userId)) return false;
  try {
    if (params.shouldSave) {
      const res = await authedFetch(
        `${SUPABASE_URL}/rest/v1/saved_posts?on_conflict=user_id,post_id`,
        {
          method: 'POST',
          body: JSON.stringify({
            user_id: params.userId,
            post_id: params.postId
          })
        },
        { 'Prefer': 'resolution=ignore-duplicates,return=minimal' }
      );
      return res.ok;
    } else {
      const res = await authedFetch(
        `${SUPABASE_URL}/rest/v1/saved_posts?user_id=eq.${encodeURIComponent(params.userId)}&post_id=eq.${encodeURIComponent(params.postId)}`,
        { method: 'DELETE' }
      );
      return res.ok;
    }
  } catch {
    return false;
  }
}

export async function fetchUserSocialGraph(userId: string): Promise<{
  followingIds: string[];
  requestedFollowIds: string[];
  blockedIds: string[];
  reactionsByPostId: Record<string, VybeReaction>;
  savedPostIds: string[];
}> {
  const empty = {
    followingIds: [],
    requestedFollowIds: [],
    blockedIds: [],
    reactionsByPostId: {},
    savedPostIds: []
  };
  if (!UUID_REGEX.test(userId)) return empty;
  const token = await getValidWebAccessToken();
  if (!token) return empty;

  const authH = {
    'apikey': SUPABASE_ANON_KEY,
    'Authorization': `Bearer ${token}`
  };

  try {
    const [followsRes, blocksRes, reactionsRes, savesRes] = await Promise.all([
      fetch(`${SUPABASE_URL}/rest/v1/follows?follower_id=eq.${encodeURIComponent(userId)}&select=followed_id,status`, { headers: authH }),
      fetch(`${SUPABASE_URL}/rest/v1/blocks?blocker_id=eq.${encodeURIComponent(userId)}&select=blocked_id`, { headers: authH }),
      fetch(`${SUPABASE_URL}/rest/v1/post_reactions?user_id=eq.${encodeURIComponent(userId)}&select=post_id,reaction_type`, { headers: authH }),
      fetch(`${SUPABASE_URL}/rest/v1/saved_posts?user_id=eq.${encodeURIComponent(userId)}&select=post_id`, { headers: authH })
    ]);

    const follows = followsRes.ok ? await followsRes.json() : [];
    const blocks = blocksRes.ok ? await blocksRes.json() : [];
    const reactions = reactionsRes.ok ? await reactionsRes.json() : [];
    const saves = savesRes.ok ? await savesRes.json() : [];

    const followingIds: string[] = [];
    const requestedFollowIds: string[] = [];
    if (Array.isArray(follows)) {
      for (const f of follows) {
        if (!f?.followed_id) continue;
        if (String(f.status || 'ACCEPTED').toUpperCase() === 'PENDING') {
          requestedFollowIds.push(f.followed_id);
        } else {
          followingIds.push(f.followed_id);
        }
      }
    }

    const reactionsByPostId: Record<string, VybeReaction> = {};
    if (Array.isArray(reactions)) {
      for (const r of reactions) {
        if (r?.post_id && r?.reaction_type) {
          reactionsByPostId[r.post_id] = String(r.reaction_type).toUpperCase() as VybeReaction;
        }
      }
    }

    return {
      followingIds,
      requestedFollowIds,
      blockedIds: Array.isArray(blocks) ? blocks.map((b: any) => b.blocked_id).filter(Boolean) : [],
      reactionsByPostId,
      savedPostIds: Array.isArray(saves) ? saves.map((s: any) => s.post_id).filter(Boolean) : []
    };
  } catch {
    return empty;
  }
}

export async function toggleFollowInSupabase(params: {
  followerId: string;
  followedId: string;
  action: 'FOLLOW' | 'REQUEST' | 'UNFOLLOW';
}): Promise<boolean> {
  if (!UUID_REGEX.test(params.followerId) || !UUID_REGEX.test(params.followedId)) return false;
  try {
    if (params.action === 'UNFOLLOW') {
      const res = await authedFetch(
        `${SUPABASE_URL}/rest/v1/follows?follower_id=eq.${encodeURIComponent(params.followerId)}&followed_id=eq.${encodeURIComponent(params.followedId)}`,
        { method: 'DELETE' }
      );
      return res.ok;
    }
    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/follows?on_conflict=follower_id,followed_id`,
      {
        method: 'POST',
        body: JSON.stringify({
          follower_id: params.followerId,
          followed_id: params.followedId,
          status: params.action === 'REQUEST' ? 'PENDING' : 'ACCEPTED'
        })
      },
      { 'Prefer': 'resolution=merge-duplicates,return=minimal' }
    );
    return res.ok;
  } catch {
    return false;
  }
}

export async function updateProfileInSupabase(
  userId: string,
  patch: Record<string, any>
): Promise<{ ok: boolean; user?: User; error?: string }> {
  if (!UUID_REGEX.test(userId)) return { ok: false, error: 'Valid user session required.' };
  const normalizedPatch: Record<string, any> = { ...patch };
  if ('live_pulse' in normalizedPatch && normalizedPatch.live_pulse !== undefined) {
    normalizedPatch.live_pulse = toDbLivePulse(normalizedPatch.live_pulse);
  }
  if ('current_mood' in normalizedPatch && typeof normalizedPatch.current_mood === 'string') {
    normalizedPatch.current_mood = normalizedPatch.current_mood.toUpperCase().trim() || null;
  }

  try {
    const endpoint = `${SUPABASE_URL}/rest/v1/profiles?id=eq.${encodeURIComponent(userId)}&select=${encodeURIComponent(PROFILE_SELECT_COLUMNS)}`;
    let res = await authedFetch(
      endpoint,
      {
        method: 'PATCH',
        body: JSON.stringify(normalizedPatch)
      },
      { 'Prefer': 'return=representation' }
    );

    if (res.status === 400 && (normalizedPatch.live_pulse || normalizedPatch.current_mood)) {
      const fallbackPatch: Record<string, any> = { ...normalizedPatch };
      if (fallbackPatch.live_pulse && LEGACY_LIVE_PULSE_FALLBACK[fallbackPatch.live_pulse]) {
        fallbackPatch.live_pulse = LEGACY_LIVE_PULSE_FALLBACK[fallbackPatch.live_pulse];
      }
      if (fallbackPatch.current_mood && !BASE_MOOD_ENUM_VALUES.has(fallbackPatch.current_mood)) {
        fallbackPatch.current_mood = null;
      }
      res = await authedFetch(
        endpoint,
        {
          method: 'PATCH',
          body: JSON.stringify(fallbackPatch)
        },
        { 'Prefer': 'return=representation' }
      );
    }

    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      return { ok: false, error: parseSupabaseError(err, 'Failed to update profile.') };
    }
    const rows = await res.json();
    const row = Array.isArray(rows) ? rows[0] : rows;
    await vybeCache.delete('live_profiles_v3');
    return { ok: true, user: row ? mapSupabaseProfileToUser(row) : undefined };
  } catch (err: any) {
    return { ok: false, error: err?.message || 'Network error updating profile.' };
  }
}

// -------------------------------------------------------------
// 6. Real Direct Messaging, Receipts, & Broadcast Push
// -------------------------------------------------------------
export async function resolveOrCreateWebDirectConversation(
  myUserId: string,
  peerUserId: string
): Promise<{ conversationId: string | null; error?: string }> {
  if (!UUID_REGEX.test(myUserId) || !UUID_REGEX.test(peerUserId)) {
    return { conversationId: null, error: 'Signed-in account required to message this user.' };
  }

  try {
    const rpcRes = await authedFetch(`${SUPABASE_URL}/rest/v1/rpc/get_or_create_direct_conversation`, {
      method: 'POST',
      body: JSON.stringify({
        p_peer_id: peerUserId,
        p_other_user_id: peerUserId
      })
    });

    if (rpcRes.ok) {
      const rawText = (await rpcRes.text()).trim();
      const cleaned = rawText.replace(/^"|"$/g, '');
      if (UUID_REGEX.test(cleaned)) return { conversationId: cleaned };
      try {
        const parsed = JSON.parse(rawText);
        if (typeof parsed === 'string' && UUID_REGEX.test(parsed)) {
          return { conversationId: parsed };
        }
      } catch {
        // ignore parse fallback
      }
      return { conversationId: null, error: 'Could not resolve conversation thread.' };
    }

    const errJson = await rpcRes.json().catch(() => ({}));
    return {
      conversationId: null,
      error: parseSupabaseError(errJson, 'Direct messaging is restricted by user privacy settings.')
    };
  } catch (err: any) {
    return {
      conversationId: null,
      error: err?.message || 'Network error opening direct conversation.'
    };
  }
}

export async function fetchConversationsForUser(myUserId: string): Promise<{
  conversations: Conversation[];
  error?: string;
}> {
  if (!UUID_REGEX.test(myUserId)) return { conversations: [] };

  try {
    const myMembershipsRes = await authedFetch(
      `${SUPABASE_URL}/rest/v1/conversation_members?user_id=eq.${encodeURIComponent(myUserId)}&select=conversation_id,is_muted,is_hidden,chat_theme,conversations(id,is_group,group_name,created_at,updated_at)`
    );

    if (!myMembershipsRes.ok) {
      const err = await myMembershipsRes.json().catch(() => ({}));
      return {
        conversations: [],
        error: parseSupabaseError(err, `Failed to load inbox (${myMembershipsRes.status})`)
      };
    }

    const memberships = await myMembershipsRes.json();
    if (!Array.isArray(memberships) || memberships.length === 0) {
      return { conversations: [] };
    }

    const visibleMemberships = memberships.filter((m: any) => !m.is_hidden && UUID_REGEX.test(String(m.conversation_id || '')));
    const convIds = visibleMemberships.map((m: any) => m.conversation_id);
    if (convIds.length === 0) return { conversations: [] };

    const inList = `(${convIds.join(',')})`;
    const [peersRes, msgsRes] = await Promise.all([
      authedFetch(
        `${SUPABASE_URL}/rest/v1/conversation_members?conversation_id=in.${encodeURIComponent(inList)}&select=conversation_id,user_id,profiles:user_id(${PROFILE_SELECT_COLUMNS})`
      ),
      authedFetch(
        `${SUPABASE_URL}/rest/v1/messages?conversation_id=in.${encodeURIComponent(inList)}&is_deleted_for_everyone=not.is.true&select=id,conversation_id,sender_id,content,media_url,media_type,reply_to_message_id,created_at&order=created_at.desc&limit=250`
      )
    ]);

    const peerRows = peersRes.ok ? await peersRes.json() : [];
    const msgRows = msgsRes.ok ? await msgsRes.json() : [];

    // Fetch read receipts for incoming messages
    const incomingMsgIds = Array.isArray(msgRows)
      ? msgRows
          .filter((m: any) => m.sender_id !== myUserId && UUID_REGEX.test(String(m.id || '')))
          .slice(0, 120)
          .map((m: any) => m.id)
      : [];

    const readMsgIdSet = new Set<string>();
    if (incomingMsgIds.length > 0) {
      const receiptsRes = await authedFetch(
        `${SUPABASE_URL}/rest/v1/message_receipts?user_id=eq.${encodeURIComponent(myUserId)}&message_id=in.${encodeURIComponent(`(${incomingMsgIds.join(',')})`)}&select=message_id,read_at`
      ).catch(() => null);
      if (receiptsRes && receiptsRes.ok) {
        const recs = await receiptsRes.json().catch(() => []);
        if (Array.isArray(recs)) {
          for (const r of recs) {
            if (r?.read_at && r?.message_id) readMsgIdSet.add(r.message_id);
          }
        }
      }
    }

    const peerByConvId = new Map<string, User>();
    if (Array.isArray(peerRows)) {
      for (const pr of peerRows) {
        if (pr.user_id && pr.user_id !== myUserId && pr.profiles) {
          peerByConvId.set(pr.conversation_id, mapSupabaseProfileToUser(pr.profiles));
        }
      }
    }

    const msgsByConvId = new Map<string, DirectMessage[]>();
    if (Array.isArray(msgRows)) {
      const resolvedUrls = await Promise.all(
        msgRows.map((m: any) => (m.media_url ? signChatMediaUrlIfNeeded(m.media_url) : Promise.resolve('')))
      );
      for (let i = 0; i < msgRows.length; i++) {
        const m = msgRows[i];
        const cid = m.conversation_id;
        if (!cid) continue;
        const list = msgsByConvId.get(cid) || [];
        const isMe = String(m.sender_id || '').toLowerCase() === myUserId.toLowerCase();
        list.push({
          id: m.id,
          senderId: m.sender_id,
          text: m.content || '',
          timestamp: formatChatClock(m.created_at),
          createdAtIso: m.created_at,
          isMe,
          mediaUrl: resolvedUrls[i] || undefined,
          mediaType: (String(m.media_type || 'TEXT').toUpperCase() as any),
          replyToId: m.reply_to_message_id || undefined,
          status: 'SENT'
        });
        msgsByConvId.set(cid, list);
      }
    }

    const conversations: Conversation[] = [];
    for (const mem of visibleMemberships) {
      const cid = mem.conversation_id;
      const convMeta = mem.conversations || {};
      const peer = peerByConvId.get(cid);
      if (!peer && !convMeta.is_group) continue;

      const resolvedPeer = peer || buildFallbackMinimalUser(cid, convMeta.group_name || 'Group Chat');
      const messages = (msgsByConvId.get(cid) || []).slice().reverse();
      const latest = messages[messages.length - 1];
      const unreadCount = messages.filter(m => !m.isMe && !readMsgIdSet.has(m.id)).length;

      const themeUpper = String(mem.chat_theme || '').toUpperCase();
      const chatTheme =
        themeUpper === 'SUNSET' || themeUpper === 'NEON_VIOLET' || themeUpper === 'EMERALD'
          ? themeUpper
          : 'CYBER_LUXE';

      conversations.push({
        id: cid,
        peerUser: resolvedPeer,
        lastMessage: latest?.text || (latest?.mediaUrl ? 'Sent an attachment' : ''),
        lastMessageTimestamp: latest?.createdAtIso
          ? formatRelativeTime(latest.createdAtIso)
          : convMeta.updated_at
            ? formatRelativeTime(convMeta.updated_at)
            : 'Active',
        lastMessageCreatedAtIso: latest?.createdAtIso || convMeta.updated_at || convMeta.created_at,
        unreadCount,
        messages,
        chatTheme,
        isMuted: Boolean(mem.is_muted),
        isGroup: Boolean(convMeta.is_group),
        groupName: convMeta.group_name || undefined
      });
    }

    conversations.sort((a, b) => {
      const ta = a.lastMessageCreatedAtIso ? Date.parse(a.lastMessageCreatedAtIso) : 0;
      const tb = b.lastMessageCreatedAtIso ? Date.parse(b.lastMessageCreatedAtIso) : 0;
      return tb - ta;
    });

    return { conversations };
  } catch (err: any) {
    return { conversations: [], error: err?.message || 'Unable to load direct messages.' };
  }
}

export async function fetchMessagesForConversation(
  conversationId: string,
  myUserId: string
): Promise<DirectMessage[]> {
  if (!UUID_REGEX.test(conversationId) || !UUID_REGEX.test(myUserId)) return [];
  try {
    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/messages?conversation_id=eq.${encodeURIComponent(conversationId)}&is_deleted_for_everyone=not.is.true&select=id,conversation_id,sender_id,content,media_url,media_type,reply_to_message_id,created_at&order=created_at.asc&limit=150`
    );
    if (!res.ok) return [];
    const rows = await res.json();
    if (!Array.isArray(rows)) return [];
    const resolvedUrls = await Promise.all(
      rows.map((m: any) => (m.media_url ? signChatMediaUrlIfNeeded(m.media_url) : Promise.resolve('')))
    );
    return rows.map((m: any, i: number) => ({
      id: m.id,
      senderId: m.sender_id,
      text: m.content || '',
      timestamp: formatChatClock(m.created_at),
      createdAtIso: m.created_at,
      isMe: String(m.sender_id || '').toLowerCase() === myUserId.toLowerCase(),
      mediaUrl: resolvedUrls[i] || undefined,
      mediaType: (String(m.media_type || 'TEXT').toUpperCase() as any),
      replyToId: m.reply_to_message_id || undefined,
      status: 'SENT'
    }));
  } catch {
    return [];
  }
}

export async function markConversationReadInSupabase(
  myUserId: string,
  messageIds: string[]
): Promise<void> {
  if (!UUID_REGEX.test(myUserId)) return;
  const validIds = messageIds.filter(id => UUID_REGEX.test(id));
  if (validIds.length === 0) return;

  const nowIso = new Date().toISOString();
  try {
    await authedFetch(
      `${SUPABASE_URL}/rest/v1/message_receipts?on_conflict=message_id,user_id`,
      {
        method: 'POST',
        body: JSON.stringify(
          validIds.slice(-50).map(id => ({
            message_id: id,
            user_id: myUserId,
            is_delivered: true,
            is_read: true,
            read_at: nowIso
          }))
        )
      },
      { 'Prefer': 'resolution=merge-duplicates,return=minimal' }
    );
  } catch {
    // ignore read receipt failure
  }
}

export async function broadcastUserPushEvent(params: {
  recipientId: string;
  actorId: string;
  type: 'DM' | 'TYPING' | 'ACTIVITY';
  title: string;
  body: string;
  conversationId?: string;
  targetId?: string;
  actorUsername?: string;
  actorDisplayName?: string;
  actorAvatarUrl?: string;
  mediaUrl?: string;
}): Promise<void> {
  if (!UUID_REGEX.test(params.recipientId) || params.recipientId === params.actorId) return;
  const token = await getValidWebAccessToken();
  if (!token) return;

  const eventKey = `${params.type.toLowerCase()}:${params.targetId || params.conversationId || Date.now()}:${params.actorId}`;
  const payloadObj: Record<string, any> = {
    recipient_id: params.recipientId,
    actor_id: params.actorId,
    type: params.type,
    title: params.title,
    body: params.body,
    destination: params.type === 'DM' ? 'CHAT' : 'NOTIFICATIONS',
    event_key: eventKey
  };
  if (params.conversationId) payloadObj.conversation_id = params.conversationId;
  if (params.targetId) payloadObj.target_id = params.targetId;
  if (params.actorUsername) payloadObj.actor_username = params.actorUsername;
  if (params.actorDisplayName) payloadObj.actor_display_name = params.actorDisplayName;
  if (params.actorAvatarUrl) payloadObj.actor_avatar_url = params.actorAvatarUrl;
  if (params.mediaUrl) payloadObj.media_url = params.mediaUrl;

  try {
    await fetch(`${SUPABASE_URL}/realtime/v1/api/broadcast`, {
      method: 'POST',
      headers: {
        'apikey': SUPABASE_ANON_KEY,
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        messages: [
          {
            topic: `realtime:vybe_user_push:${params.recipientId}`,
            event: 'push_event',
            payload: payloadObj
          },
          {
            topic: `vybe_user_push:${params.recipientId}`,
            event: 'push_event',
            payload: payloadObj
          }
        ]
      })
    });
  } catch {
    // non-blocking push fan-out
  }
}

export async function sendLiveDirectMessage(params: {
  conversationId: string;
  senderId: string;
  peerUserId: string;
  content: string;
  mediaUrl?: string;
  mediaType?: 'TEXT' | 'IMAGE';
  actorUsername?: string;
  actorDisplayName?: string;
  actorAvatarUrl?: string;
}): Promise<{
  ok: boolean;
  conversationId?: string;
  messageId?: string;
  createdAt?: string;
  error?: string;
}> {
  if (!UUID_REGEX.test(params.senderId) || !UUID_REGEX.test(params.peerUserId)) {
    return { ok: false, error: 'Please sign in to send direct messages.' };
  }

  try {
    let resolvedConvId = params.conversationId;
    if (!UUID_REGEX.test(resolvedConvId)) {
      const convResult = await resolveOrCreateWebDirectConversation(params.senderId, params.peerUserId);
      if (!convResult.conversationId) {
        return {
          ok: false,
          error: convResult.error || 'Unable to start conversation with this user.'
        };
      }
      resolvedConvId = convResult.conversationId;
    }

    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/messages`,
      {
        method: 'POST',
        body: JSON.stringify({
          conversation_id: resolvedConvId,
          sender_id: params.senderId,
          content: params.content,
          media_url: params.mediaUrl || null,
          media_type: params.mediaType || (params.mediaUrl ? 'IMAGE' : 'TEXT')
        })
      },
      { 'Prefer': 'return=representation' }
    );

    if (!res.ok) {
      const errJson = await res.json().catch(() => ({}));
      return {
        ok: false,
        conversationId: resolvedConvId,
        error: parseSupabaseError(errJson, `Message delivery failed (${res.status}).`)
      };
    }

    const rows = await res.json();
    const row = Array.isArray(rows) ? rows[0] : rows;
    if (!row?.id) {
      return { ok: false, conversationId: resolvedConvId, error: 'Message was not confirmed by server.' };
    }

    // Fan-out instant push to Android/Web peer channel
    broadcastUserPushEvent({
      recipientId: params.peerUserId,
      actorId: params.senderId,
      type: 'DM',
      title: params.actorDisplayName || params.actorUsername || 'New message',
      body: params.content || (params.mediaUrl ? 'Sent an image' : 'New message'),
      conversationId: resolvedConvId,
      targetId: row.id,
      actorUsername: params.actorUsername,
      actorDisplayName: params.actorDisplayName,
      actorAvatarUrl: params.actorAvatarUrl,
      mediaUrl: params.mediaUrl
    });

    return {
      ok: true,
      conversationId: resolvedConvId,
      messageId: row.id,
      createdAt: row.created_at
    };
  } catch (err: any) {
    return {
      ok: false,
      error: err?.message || 'Network error sending message.'
    };
  }
}

// -------------------------------------------------------------
// 7. Notifications, Communities, Rooms, Crossed Paths, Capsules, Shops, Intentions
// -------------------------------------------------------------
export async function fetchLiveNotifications(userId: string): Promise<NotificationItem[]> {
  if (!UUID_REGEX.test(userId)) return [];
  try {
    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/notifications?recipient_id=eq.${encodeURIComponent(userId)}&select=id,recipient_id,actor_id,type,title,message,target_id,is_read,created_at,actor:actor_id(${STORY_PROFILE_COLUMNS})&order=created_at.desc&limit=50`
    );
    if (!res.ok) return [];
    const rows = await res.json();
    if (!Array.isArray(rows)) return [];
    return rows.map((r: any) => ({
      id: r.id,
      recipientId: r.recipient_id,
      actorId: r.actor_id || undefined,
      actorUsername: r.actor?.username || undefined,
      actorDisplayName: r.actor?.display_name || r.actor?.username || undefined,
      actorAvatarUrl: r.actor?.avatar_url ? resolveMediaUrl(r.actor.avatar_url) : undefined,
      type: String(r.type || 'ACTIVITY').toUpperCase(),
      title: r.title || 'VYBE Notification',
      body: r.message || '',
      targetId: r.target_id || undefined,
      isRead: Boolean(r.is_read),
      createdAtIso: r.created_at || new Date().toISOString(),
      timestamp: formatRelativeTime(r.created_at)
    }));
  } catch {
    return [];
  }
}

export async function markNotificationReadInSupabase(notificationId: string): Promise<void> {
  if (!UUID_REGEX.test(notificationId)) return;
  try {
    await authedFetch(`${SUPABASE_URL}/rest/v1/notifications?id=eq.${encodeURIComponent(notificationId)}`, {
      method: 'PATCH',
      body: JSON.stringify({ is_read: true })
    });
  } catch {
    // ignore
  }
}

export async function fetchLiveCommunities(currentUserId?: string): Promise<Community[]> {
  try {
    const hdrs = await getOptionalRestHeaders(false);
    const [commsRes, memRes] = await Promise.all([
      fetch(`${SUPABASE_URL}/rest/v1/communities?select=id,creator_id,name,description,category,avatar_url,banner_url,members_count&order=members_count.desc&limit=40`, { headers: hdrs }),
      currentUserId && UUID_REGEX.test(currentUserId)
        ? fetch(`${SUPABASE_URL}/rest/v1/community_members?user_id=eq.${encodeURIComponent(currentUserId)}&select=community_id`, { headers: hdrs })
        : Promise.resolve(null)
    ]);

    if (!commsRes.ok) return [];
    const rows = await commsRes.json();
    const myMemRows = memRes && memRes.ok ? await memRes.json() : [];
    const joinedSet = new Set<string>(
      Array.isArray(myMemRows) ? myMemRows.map((m: any) => m.community_id).filter(Boolean) : []
    );

    if (!Array.isArray(rows)) return [];
    return rows.map((c: any) => ({
      id: c.id,
      name: c.name || 'VYBE Community',
      description: c.description || '',
      category: c.category || 'General',
      avatarUrl: resolveMediaUrl(c.avatar_url || ''),
      bannerUrl: resolveMediaUrl(c.banner_url || ''),
      membersCount: Number(c.members_count || 1),
      isJoined: joinedSet.has(c.id),
      tags: c.category ? [String(c.category)] : []
    }));
  } catch {
    return [];
  }
}

export async function toggleJoinCommunityInSupabase(params: {
  communityId: string;
  userId: string;
  shouldJoin: boolean;
}): Promise<boolean> {
  if (!UUID_REGEX.test(params.communityId) || !UUID_REGEX.test(params.userId)) return false;
  try {
    if (params.shouldJoin) {
      const res = await authedFetch(
        `${SUPABASE_URL}/rest/v1/community_members?on_conflict=community_id,user_id`,
        {
          method: 'POST',
          body: JSON.stringify({
            community_id: params.communityId,
            user_id: params.userId,
            role: 'MEMBER',
            status: 'ACCEPTED'
          })
        },
        { 'Prefer': 'resolution=ignore-duplicates,return=minimal' }
      );
      return res.ok;
    } else {
      const res = await authedFetch(
        `${SUPABASE_URL}/rest/v1/community_members?community_id=eq.${encodeURIComponent(params.communityId)}&user_id=eq.${encodeURIComponent(params.userId)}`,
        { method: 'DELETE' }
      );
      return res.ok;
    }
  } catch {
    return false;
  }
}

export async function fetchLiveUnloadRooms(): Promise<UnloadRoom[]> {
  try {
    const hdrs = await getOptionalRestHeaders(false);
    const res = await fetch(
      `${SUPABASE_URL}/rest/v1/rooms?is_active=eq.true&select=id,room_code,host_id,title,description,category,mode,max_participants,joiner_message,profiles:host_id(id,username,display_name)&order=created_at.desc&limit=25`,
      { headers: hdrs }
    );
    if (!res.ok) return [];
    const rows = await res.json();
    if (!Array.isArray(rows)) return [];
    return rows.map((r: any) => ({
      id: r.id,
      roomCode: r.room_code || String(r.id).slice(0, 6).toUpperCase(),
      title: r.title || 'Safe Unload Room',
      description: r.description || '',
      topicTag: r.category || 'Support',
      mode:
        r.mode === 'VOICE_ONLY'
          ? 'Voice Only'
          : r.mode === 'TEXT_ONLY'
            ? 'Text Only'
            : 'Voice & Text',
      activeListenersCount: 1,
      isHostOnline: true,
      recentMessages: [],
      hostName: r.profiles?.display_name || r.profiles?.username || 'Host',
      safetyNotice: r.joiner_message || 'Zero-recording anonymous support space. Be kind and respectful.'
    }));
  } catch {
    return [];
  }
}

export async function fetchLiveCrossedPaths(): Promise<CrossedPathEncounter[]> {
  try {
    const hdrs = await getOptionalRestHeaders(false);
    const res = await fetch(
      `${SUPABASE_URL}/rest/v1/crossed_paths?select=id,creator_id,neighborhood_or_venue,description,time_window,encounter_type,title,broad_location,venue_name,photo_url,expires_at,created_at,profiles:creator_id(${STORY_PROFILE_COLUMNS})&order=created_at.desc&limit=30`,
      { headers: hdrs }
    );
    if (!res.ok) return [];
    const rows = await res.json();
    if (!Array.isArray(rows)) return [];
    return rows.map((r: any) => ({
      id: r.id,
      type: (r.encounter_type || 'MISSED_CONNECTION') as any,
      title: r.title || r.neighborhood_or_venue || 'Crossed Path',
      description: r.description || '',
      broadLocation: r.broad_location || r.neighborhood_or_venue || 'Nearby',
      venueOrEventName: r.venue_name || undefined,
      encounterDate: r.time_window || formatRelativeTime(r.created_at),
      poster: r.profiles ? mapSupabaseProfileToUser(r.profiles) : buildFallbackMinimalUser(r.creator_id),
      isAnonymous: false,
      expiresHoursRemaining: 48,
      potentialMatchesCount: 1,
      hasSentRequest: false,
      requestStatus: 'PENDING',
      tags: [],
      photoUrl: r.photo_url ? resolveMediaUrl(r.photo_url) : undefined
    }));
  } catch {
    return [];
  }
}

export async function fetchLiveTimeCapsules(userId?: string): Promise<TimeCapsule[]> {
  if (!userId || !UUID_REGEX.test(userId)) return [];
  try {
    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/time_capsules?creator_id=eq.${encodeURIComponent(userId)}&select=id,creator_id,title,recipient_summary,sealed_message,media_url,unlock_date,is_unlocked,created_at,profiles:creator_id(${STORY_PROFILE_COLUMNS})&order=created_at.desc&limit=30`
    );
    if (!res.ok) return [];
    const rows = await res.json();
    if (!Array.isArray(rows)) return [];
    return rows.map((c: any) => {
      const unlockMs = c.unlock_date ? Date.parse(c.unlock_date) : Date.now();
      return {
        id: c.id,
        title: c.title || 'Time Capsule',
        sender: c.profiles ? mapSupabaseProfileToUser(c.profiles) : buildFallbackMinimalUser(c.creator_id),
        recipientSummary: c.recipient_summary || 'Personal Note',
        sealedMessage: c.sealed_message || '',
        mediaAttachmentUrl: c.media_url ? resolveMediaUrl(c.media_url) : undefined,
        unlockDateFormatted: c.unlock_date ? new Date(c.unlock_date).toLocaleDateString() : 'Future',
        unlockTimestampMs: Number.isNaN(unlockMs) ? Date.now() : unlockMs,
        isUnlocked: Boolean(c.is_unlocked || (!Number.isNaN(unlockMs) && unlockMs <= Date.now())),
        createdAtFormatted: formatRelativeTime(c.created_at),
        categoryTheme: 'Reflection'
      };
    });
  } catch {
    return [];
  }
}

export async function fetchLiveShops(): Promise<VybeShop[]> {
  try {
    const hdrs = await getOptionalRestHeaders(false);
    const res = await fetch(
      `${SUPABASE_URL}/rest/v1/business_profiles?select=id,owner_id,business_name,category,description,logo_url,cover_image_url,city,full_address,verification_status,created_at&order=created_at.desc&limit=30`,
      { headers: hdrs }
    );
    if (!res.ok) return [];
    const rows = await res.json();
    if (!Array.isArray(rows)) return [];
    return rows.map((b: any) => ({
      id: b.id,
      businessName: b.business_name || 'VYBE Business',
      category: b.category || 'Creator Shop',
      description: b.description || '',
      logoUrl: resolveMediaUrl(b.logo_url || ''),
      coverUrl: resolveMediaUrl(b.cover_image_url || ''),
      rating: 0,
      reviewsCount: 0,
      isVerifiedBusiness: String(b.verification_status || '').toUpperCase() === 'APPROVED',
      openingHours: 'Open',
      locationAddress: b.full_address || b.city || '',
      contactPhone: '',
      contactEmail: '',
      catalog: [],
      reviews: [],
      followersCount: 0
    }));
  } catch {
    return [];
  }
}

export async function fetchLiveSpiritualIntentions(userId: string): Promise<SpiritualIntention[]> {
  if (!UUID_REGEX.test(userId)) return [];
  try {
    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/spiritual_intentions?user_id=eq.${encodeURIComponent(userId)}&select=id,intention_text,is_fulfilled,created_at&order=created_at.desc&limit=30`
    );
    if (!res.ok) return [];
    const rows = await res.json();
    if (!Array.isArray(rows)) return [];
    return rows.map((r: any) => ({
      id: r.id,
      text: r.intention_text || '',
      timestamp: formatRelativeTime(r.created_at),
      isFulfilled: Boolean(r.is_fulfilled)
    }));
  } catch {
    return [];
  }
}

export async function addSpiritualIntentionInSupabase(
  userId: string,
  text: string
): Promise<SpiritualIntention | null> {
  if (!UUID_REGEX.test(userId) || !text.trim()) return null;
  try {
    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/spiritual_intentions`,
      {
        method: 'POST',
        body: JSON.stringify({
          user_id: userId,
          intention_text: text.trim(),
          is_fulfilled: false
        })
      },
      { 'Prefer': 'return=representation' }
    );
    if (!res.ok) return null;
    const rows = await res.json();
    const row = Array.isArray(rows) ? rows[0] : rows;
    if (!row?.id) return null;
    return {
      id: row.id,
      text: row.intention_text || text.trim(),
      timestamp: formatRelativeTime(row.created_at),
      isFulfilled: Boolean(row.is_fulfilled)
    };
  } catch {
    return null;
  }
}

export async function toggleSpiritualIntentionInSupabase(
  intentionId: string,
  isFulfilled: boolean
): Promise<void> {
  if (!UUID_REGEX.test(intentionId)) return;
  try {
    await authedFetch(`${SUPABASE_URL}/rest/v1/spiritual_intentions?id=eq.${encodeURIComponent(intentionId)}`, {
      method: 'PATCH',
      body: JSON.stringify({ is_fulfilled: isFulfilled })
    });
  } catch {
    // ignore
  }
}

export async function submitVerificationRequestToSupabase(params: {
  userId: string;
  requestType: 'VERIFIED' | 'VIP' | 'SHOP' | 'WORKER';
  fullName: string;
  category: string;
  statement: string;
  supportingLink?: string;
}): Promise<{ ok: boolean; error?: string }> {
  if (!UUID_REGEX.test(params.userId)) {
    return { ok: false, error: 'Please sign in before submitting a verification request.' };
  }
  try {
    const res = await authedFetch(
      `${SUPABASE_URL}/rest/v1/verification_requests`,
      {
        method: 'POST',
        body: JSON.stringify({
          user_id: params.userId,
          request_type: params.requestType,
          full_name: params.fullName,
          category: params.category,
          statement: params.statement,
          supporting_links: params.supportingLink ? [params.supportingLink] : [],
          status: 'PENDING'
        })
      },
      { 'Prefer': 'return=representation' }
    );
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      return { ok: false, error: parseSupabaseError(err, `Failed to submit verification (${res.status}).`) };
    }
    return { ok: true };
  } catch (err: any) {
    return { ok: false, error: err?.message || 'Network error submitting verification request.' };
  }
}

// -------------------------------------------------------------
// Media URL Resolution & Storage Upload
// -------------------------------------------------------------
const chatMediaSignedUrlCache = new Map<string, { url: string; expiresAtMs: number }>();
const CHAT_MEDIA_CACHE_TTL_MS = 6 * 3600 * 1000;

export async function signChatMediaUrlIfNeeded(rawUrl?: string | null): Promise<string> {
  const trimmed = String(rawUrl || '').trim();
  if (!trimmed) return '';
  if (trimmed.startsWith('data:') || trimmed.startsWith('blob:')) return trimmed;
  if (trimmed.includes('/storage/v1/object/sign/') && trimmed.includes('token=')) {
    return trimmed;
  }

  let objectPath = '';
  if (trimmed.startsWith('chat-media/')) {
    objectPath = trimmed.slice('chat-media/'.length);
  } else {
    const match = trimmed.match(/\/storage\/v1\/object\/(?:public\/|authenticated\/|sign\/)?chat-media\/([^?#]+)/i);
    if (match?.[1]) {
      objectPath = decodeURIComponent(match[1]);
    }
  }

  if (!objectPath) {
    return resolveMediaUrl(trimmed);
  }

  const cached = chatMediaSignedUrlCache.get(objectPath);
  if (cached && cached.expiresAtMs > Date.now()) {
    return cached.url;
  }

  const token = await getValidWebAccessToken();
  if (!token) {
    return resolveMediaUrl(trimmed);
  }

  try {
    const res = await fetch(`${SUPABASE_URL}/storage/v1/object/sign/chat-media/${objectPath}`, {
      method: 'POST',
      headers: {
        'apikey': SUPABASE_ANON_KEY,
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ expiresIn: 604800 })
    });
    if (res.ok) {
      const json = await res.json();
      const signedPath = json?.signedURL || json?.signedUrl || '';
      if (signedPath) {
        const fullSigned = signedPath.startsWith('http')
          ? signedPath
          : signedPath.startsWith('/storage/v1')
            ? `${SUPABASE_URL}${signedPath}`
            : `${SUPABASE_URL}/storage/v1${signedPath}`;
        chatMediaSignedUrlCache.set(objectPath, {
          url: fullSigned,
          expiresAtMs: Date.now() + CHAT_MEDIA_CACHE_TTL_MS
        });
        return fullSigned;
      }
    }
  } catch {
    // ignore sign failure
  }

  return resolveMediaUrl(trimmed);
}

export function resolveMediaUrl(rawUrl: string): string {
  if (!rawUrl) return '';
  if (rawUrl.startsWith('data:') || rawUrl.startsWith('blob:')) return rawUrl;
  if (rawUrl.startsWith('http://') || rawUrl.startsWith('https://')) return rawUrl;
  if (
    rawUrl.startsWith('posts/') ||
    rawUrl.startsWith('avatars/') ||
    rawUrl.startsWith('banners/') ||
    rawUrl.startsWith('stories/')
  ) {
    return `${SUPABASE_URL}/storage/v1/object/public/${rawUrl}`;
  }
  if (rawUrl.startsWith('chat-media/')) {
    return `${SUPABASE_URL}/storage/v1/object/authenticated/${rawUrl}`;
  }
  return rawUrl;
}

export interface UploadStorageOptions {
  isPrivate?: boolean;
  cacheControl?: string;
}

export async function uploadMediaBlobToStorage(
  blob: Blob,
  bucket: 'posts' | 'avatars' | 'banners' | 'stories' | 'chat-media',
  fileName: string = 'media',
  originalSizeBytes?: number,
  options: UploadStorageOptions = {}
): Promise<{ publicUrl: string; storagePath: string } | null> {
  const token = (await getValidWebAccessToken()) || localStorage.getItem('vybe_admin_jwt') || '';
  if (!token || !token.startsWith('eyJ')) {
    console.warn('[Supabase Storage] Upload aborted: authenticated user JWT required.');
    return null;
  }

  const uid = extractUserIdFromJwt(token);
  if (!uid) {
    console.warn('[Supabase Storage] Upload aborted: unable to resolve authenticated user UID from JWT.');
    return null;
  }

  const mime = (blob.type || 'image/webp').toLowerCase();
  const cleanExt = mime.includes('mp4')
    ? 'mp4'
    : mime.includes('webm')
      ? 'webm'
      : mime.includes('webp')
        ? 'webp'
        : mime.includes('png')
          ? 'png'
          : 'jpg';
  const baseLeaf = (fileName || 'media').replace(/[^a-zA-Z0-9._-]/g, '_').replace(/\.[^/.]+$/, '');
  const uniqueName = `${Date.now()}_${baseLeaf.slice(0, 24)}_${Math.random().toString(36).slice(2, 6)}.${cleanExt}`;
  const storagePath = `${uid}/${uniqueName}`;
  const targetUrl = `${SUPABASE_URL}/storage/v1/object/${bucket}/${storagePath}`;

  const isRestricted = Boolean(options.isPrivate || bucket === 'chat-media');
  const cacheControlHeader =
    options.cacheControl ||
    (isRestricted ? 'private, max-age=0, must-revalidate' : 'public, max-age=31536000, immutable');

  const uploadHeaders: Record<string, string> = {
    'apikey': SUPABASE_ANON_KEY,
    'Authorization': `Bearer ${token}`,
    'Content-Type': blob.type || 'image/webp',
    'cache-control': cacheControlHeader,
    'x-upsert': 'true'
  };

  const startTime = Date.now();
  try {
    const res = await fetch(targetUrl, {
      method: 'POST',
      headers: uploadHeaders,
      body: blob
    });

    const duration = Date.now() - startTime;
    if (res.ok) {
      vybeDiagnostics.recordNetworkRequest(targetUrl, duration, res.status, blob.size, 'POST');
      vybeDiagnostics.recordStorageUpload(bucket, blob.size, originalSizeBytes || blob.size);
      const publicUrl = `${SUPABASE_URL}/storage/v1/object/public/${bucket}/${storagePath}`;
      if (isRestricted) {
        try {
          const signRes = await fetch(`${SUPABASE_URL}/storage/v1/object/sign/${bucket}/${storagePath}`, {
            method: 'POST',
            headers: {
              'apikey': SUPABASE_ANON_KEY,
              'Authorization': `Bearer ${token}`,
              'Content-Type': 'application/json'
            },
            body: JSON.stringify({ expiresIn: 604800 })
          });
          if (signRes.ok) {
            const signJson = await signRes.json();
            const signedPath = signJson?.signedURL || signJson?.signedUrl || '';
            if (signedPath) {
              const fullSigned = signedPath.startsWith('http')
                ? signedPath
                : signedPath.startsWith('/storage/v1')
                  ? `${SUPABASE_URL}${signedPath}`
                  : `${SUPABASE_URL}/storage/v1${signedPath}`;
              return { publicUrl: fullSigned, storagePath: `${bucket}/${storagePath}` };
            }
          }
        } catch {
          // ignore sign fallback
        }
      }
      return { publicUrl, storagePath: `${bucket}/${storagePath}` };
    }

    if (res.status === 402) {
      vybeDiagnostics.recordQuotaViolation('Supabase Storage restricted due to exceed_cached_egress_quota.');
    }
  } catch (err) {
    console.warn('[Supabase Storage] Upload error:', err);
  }

  return null;
}

// -------------------------------------------------------------
// Secure Admin Authentication & Column-Scoped Admin Moderation Helpers
// -------------------------------------------------------------
export async function adminSignInWithPassword(
  email: string,
  password: string
): Promise<{ success: boolean; token?: string; user?: any; error?: string }> {
  try {
    const authUrl = `${SUPABASE_URL}/auth/v1/token?grant_type=password`;
    const res = await fetch(authUrl, {
      method: 'POST',
      headers: {
        'apikey': SUPABASE_ANON_KEY,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ email: email.trim().toLowerCase(), password })
    });

    if (!res.ok) {
      if (res.status === 402) {
        vybeDiagnostics.recordQuotaViolation('Supabase Auth restricted by cached egress quota.');
        return {
          success: false,
          error: 'Supabase Quota Restriction (402): Egress limit reached on Supabase backend.'
        };
      }
      const errData = await res.json().catch(() => ({}));
      return {
        success: false,
        error: errData.error_description || errData.message || 'Invalid administrator email or password.'
      };
    }

    const data = await res.json();
    const token = data.access_token;
    const user = data.user;

    const adminRoles = new Set(['ADMIN', 'SUPER_ADMIN', 'OWNER_ADMIN', 'MODERATOR', 'SUPPORT_ADMIN', 'ANALYST']);
    let roleCheckSucceeded = false;
    let isAuthorizedAdmin = false;

    try {
      const adminUserRes = await fetch(
        `${SUPABASE_URL}/rest/v1/admin_users?user_id=eq.${encodeURIComponent(user.id)}&select=user_id,role,is_active&limit=1`,
        {
          headers: {
            'apikey': SUPABASE_ANON_KEY,
            'Authorization': `Bearer ${token}`
          }
        }
      );

      if (adminUserRes.ok) {
        roleCheckSucceeded = true;
        const adminRows = await adminUserRes.json();
        const adminRec = Array.isArray(adminRows) ? adminRows[0] : adminRows;
        const adminRole = String(adminRec?.role || '').toUpperCase().trim();
        const matchesUser =
          !adminRec?.user_id || String(adminRec.user_id).toLowerCase() === String(user.id).toLowerCase();
        if (adminRec && adminRec.is_active === true && matchesUser && adminRoles.has(adminRole)) {
          isAuthorizedAdmin = true;
        }
      }
    } catch {
      roleCheckSucceeded = false;
    }

    if (!roleCheckSucceeded) {
      return {
        success: false,
        error: 'Unable to verify administrator privileges right now. Sign-in aborted.'
      };
    }

    if (!isAuthorizedAdmin) {
      return {
        success: false,
        error: 'Forbidden: Active administrator record required.'
      };
    }

    localStorage.setItem('vybe_admin_jwt', token);
    localStorage.setItem('vybe_admin_email', email.trim().toLowerCase());
    localStorage.setItem('vybe_admin_user_id', user.id);

    return { success: true, token, user };
  } catch (err: any) {
    return { success: false, error: err.message || 'Network error during authentication.' };
  }
}

export function getStoredAdminSession(): { token: string | null; email: string | null } {
  return {
    token: localStorage.getItem('vybe_admin_jwt'),
    email: localStorage.getItem('vybe_admin_email')
  };
}

export function adminSignOut(): void {
  localStorage.removeItem('vybe_admin_jwt');
  localStorage.removeItem('vybe_admin_email');
  localStorage.removeItem('vybe_admin_user_id');
}

export function isVerifiedAdminSession(_user?: User | null): boolean {
  try {
    const adminJwt = typeof localStorage !== 'undefined' ? localStorage.getItem('vybe_admin_jwt') || '' : '';
    return adminJwt.startsWith('eyJ');
  } catch {
    return false;
  }
}

function getAdminAuthHeaders(): { headers: Record<string, string>; token: string; adminId: string; adminEmail: string } | null {
  try {
    const token = localStorage.getItem('vybe_admin_jwt') || '';
    if (!token || !token.startsWith('eyJ')) return null;
    const adminId = localStorage.getItem('vybe_admin_user_id') || extractUserIdFromJwt(token) || '';
    const adminEmail = localStorage.getItem('vybe_admin_email') || 'admin';
    return {
      token,
      adminId,
      adminEmail,
      headers: {
        'apikey': SUPABASE_ANON_KEY,
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    };
  } catch {
    return null;
  }
}

export interface LiveVerificationRequestItem {
  id: string;
  userId: string;
  username: string;
  displayName: string;
  avatarUrl: string;
  requestType: string;
  badge: OfficialVybeBadge;
  category: string;
  proof: string;
  status: string;
  submittedAt: string;
}

export interface LiveReportItem {
  id: string;
  reporterId: string;
  reporterUsername: string;
  targetType: string;
  targetId: string;
  reason: string;
  status: string;
  adminNotes: string;
  createdAt: string;
}

export interface LiveAuditLogItem {
  id: string;
  adminId: string;
  adminEmail: string;
  action: string;
  targetType: string;
  targetId: string;
  details: string;
  createdAt: string;
}

export function mapRequestTypeToBadge(requestType: string): OfficialVybeBadge {
  const upper = String(requestType || '').toUpperCase();
  if (upper.includes('WORKER')) return 'WORKER_GREEN';
  if (upper.includes('VIP')) return 'VIP_GOLD';
  if (upper.includes('SHOP') || upper.includes('BUSINESS')) return 'SHOP_GOLD';
  return 'VERIFIED_BLUE';
}

export async function fetchLiveVerificationRequests(): Promise<LiveVerificationRequestItem[]> {
  const auth = getAdminAuthHeaders();
  if (!auth) return [];
  try {
    const selectCols =
      'id,user_id,request_type,full_name,category,identity_document_url,supporting_links,status,rejection_reason,created_at,profiles:user_id(id,username,display_name,avatar_url)';
    const resp = await fetch(
      `${SUPABASE_URL}/rest/v1/verification_requests?select=${encodeURIComponent(selectCols)}&order=created_at.desc&limit=100`,
      { headers: auth.headers }
    );
    if (!resp.ok) return [];
    const rows = await resp.json();
    if (!Array.isArray(rows)) return [];
    return rows.map((r: any) => {
      const p = r.profiles || {};
      const links = Array.isArray(r.supporting_links) ? r.supporting_links.filter(Boolean).join(', ') : '';
      const proofParts = [links, r.identity_document_url ? `Document: ${r.identity_document_url}` : ''].filter(Boolean);
      return {
        id: r.id,
        userId: r.user_id,
        username: p.username || 'vybe_member',
        displayName: r.full_name || p.display_name || p.username || 'VYBE Member',
        avatarUrl: resolveMediaUrl(p.avatar_url || ''),
        requestType: r.request_type || 'VERIFIED',
        badge: mapRequestTypeToBadge(r.request_type),
        category: r.category || 'Creator',
        proof: proofParts.join(' • ') || 'Submitted application details',
        status: String(r.status || 'PENDING').toUpperCase(),
        submittedAt: r.created_at ? new Date(r.created_at).toLocaleString() : 'Recently'
      };
    });
  } catch (err) {
    console.warn('[Admin] fetchLiveVerificationRequests notice:', err);
    return [];
  }
}

export async function reviewVerificationRequestInSupabase(params: {
  requestId: string;
  decision: 'APPROVED' | 'REJECTED' | 'MORE_INFO_REQUIRED';
  notes?: string;
  vipExpiresAt?: string | null;
}): Promise<{ ok: boolean; error?: string }> {
  const auth = getAdminAuthHeaders();
  if (!auth) return { ok: false, error: 'Administrator session required.' };
  if (!UUID_REGEX.test(params.requestId)) return { ok: false, error: 'Invalid verification request ID.' };

  try {
    const resp = await fetch(`${SUPABASE_URL}/rest/v1/rpc/admin_review_verification_request`, {
      method: 'POST',
      headers: auth.headers,
      body: JSON.stringify({
        p_request_id: params.requestId,
        p_decision: params.decision,
        p_notes: params.notes || null,
        p_admin_id: UUID_REGEX.test(auth.adminId) ? auth.adminId : null,
        p_vip_expires_at: params.vipExpiresAt || null
      })
    });
    if (!resp.ok) {
      const errData = await resp.json().catch(() => ({}));
      return { ok: false, error: errData?.message || errData?.error || `RPC rejected (${resp.status}).` };
    }
    return { ok: true };
  } catch (err: any) {
    return { ok: false, error: err?.message || 'Network error reviewing verification request.' };
  }
}

export async function fetchLiveReports(): Promise<LiveReportItem[]> {
  const auth = getAdminAuthHeaders();
  if (!auth) return [];
  try {
    const selectCols =
      'id,reporter_id,target_type,target_id,reason,status,admin_notes,created_at,reporter:reporter_id(id,username,display_name)';
    const resp = await fetch(
      `${SUPABASE_URL}/rest/v1/reports?select=${encodeURIComponent(selectCols)}&order=created_at.desc&limit=100`,
      { headers: auth.headers }
    );
    if (!resp.ok) return [];
    const rows = await resp.json();
    if (!Array.isArray(rows)) return [];
    return rows.map((r: any) => ({
      id: r.id,
      reporterId: r.reporter_id,
      reporterUsername: r.reporter?.username ? `@${r.reporter.username}` : r.reporter_id || 'member',
      targetType: String(r.target_type || 'POST').toUpperCase(),
      targetId: String(r.target_id || ''),
      reason: r.reason || 'Policy violation report',
      status: String(r.status || 'OPEN').toUpperCase(),
      adminNotes: r.admin_notes || '',
      createdAt: r.created_at ? new Date(r.created_at).toLocaleString() : 'Recently'
    }));
  } catch (err) {
    console.warn('[Admin] fetchLiveReports notice:', err);
    return [];
  }
}

export async function updateReportStatusInSupabase(params: {
  reportId: string;
  status: 'OPEN' | 'INVESTIGATING' | 'RESOLVED' | 'DISMISSED';
  adminNotes?: string;
}): Promise<{ ok: boolean; error?: string }> {
  const auth = getAdminAuthHeaders();
  if (!auth) return { ok: false, error: 'Administrator session required.' };
  if (!UUID_REGEX.test(params.reportId)) return { ok: false, error: 'Invalid report ID.' };

  try {
    const resp = await fetch(`${SUPABASE_URL}/rest/v1/reports?id=eq.${encodeURIComponent(params.reportId)}`, {
      method: 'PATCH',
      headers: { ...auth.headers, 'Prefer': 'return=representation' },
      body: JSON.stringify({
        status: params.status,
        ...(params.adminNotes !== undefined ? { admin_notes: params.adminNotes } : {})
      })
    });
    if (!resp.ok) {
      const errData = await resp.json().catch(() => ({}));
      return { ok: false, error: errData?.message || `Report update failed (${resp.status}).` };
    }
    await recordAdminAuditLogToSupabase({
      action: `REPORT_${params.status}`,
      targetType: 'reports',
      targetId: params.reportId,
      details: { status: params.status, adminNotes: params.adminNotes || null }
    });
    return { ok: true };
  } catch (err: any) {
    return { ok: false, error: err?.message || 'Network error updating report.' };
  }
}

export async function moderatePostInSupabase(params: {
  postId: string;
  action: 'DELETE' | 'ARCHIVE';
}): Promise<{ ok: boolean; error?: string }> {
  const auth = getAdminAuthHeaders();
  if (!auth) return { ok: false, error: 'Administrator session required.' };
  if (!UUID_REGEX.test(params.postId)) return { ok: false, error: 'Invalid post UUID.' };

  try {
    const isDelete = params.action === 'DELETE';
    const resp = await fetch(`${SUPABASE_URL}/rest/v1/posts?id=eq.${encodeURIComponent(params.postId)}`, {
      method: isDelete ? 'DELETE' : 'PATCH',
      headers: { ...auth.headers, 'Prefer': 'return=minimal' },
      ...(isDelete ? {} : { body: JSON.stringify({ is_archived: true }) })
    });
    if (!resp.ok) {
      const errData = await resp.json().catch(() => ({}));
      return { ok: false, error: errData?.message || `Post moderation failed (${resp.status}).` };
    }
    await recordAdminAuditLogToSupabase({
      action: `POST_${params.action}`,
      targetType: 'posts',
      targetId: params.postId,
      details: { action: params.action }
    });
    return { ok: true };
  } catch (err: any) {
    return { ok: false, error: err?.message || 'Network error moderating post.' };
  }
}

export async function suspendUserInSupabase(params: {
  userId: string;
  action: 'SUSPEND' | 'BAN' | 'RESTORE';
  reason?: string;
  durationHours?: number;
}): Promise<{ ok: boolean; error?: string }> {
  const auth = getAdminAuthHeaders();
  if (!auth) return { ok: false, error: 'Administrator session required.' };
  if (!UUID_REGEX.test(params.userId)) return { ok: false, error: 'Invalid user UUID.' };
  if (auth.adminId && auth.adminId.toLowerCase() === params.userId.toLowerCase()) {
    return { ok: false, error: 'Security Policy: Administrators cannot moderate their own account.' };
  }

  const hours = Math.max(1, Math.min(8760, params.durationHours || 72));
  const until = new Date(Date.now() + hours * 3600 * 1000).toISOString();
  const patch: Record<string, any> =
    params.action === 'BAN'
      ? { is_banned: true, ban_reason: params.reason || 'Safety moderation enforcement' }
      : params.action === 'SUSPEND'
        ? { is_suspended: true, suspension_reason: params.reason || `${hours}h suspension`, suspended_until: until }
        : { is_banned: false, is_suspended: false, ban_reason: null, suspension_reason: null, suspended_until: null };

  try {
    const resp = await fetch(`${SUPABASE_URL}/rest/v1/profiles?id=eq.${encodeURIComponent(params.userId)}`, {
      method: 'PATCH',
      headers: { ...auth.headers, 'Prefer': 'return=representation' },
      body: JSON.stringify(patch)
    });
    if (!resp.ok) {
      const errData = await resp.json().catch(() => ({}));
      return { ok: false, error: errData?.message || `Account moderation failed (${resp.status}).` };
    }
    await recordAdminAuditLogToSupabase({
      action: `USER_${params.action}`,
      targetType: 'profiles',
      targetId: params.userId,
      details: { action: params.action, reason: params.reason || null }
    });
    return { ok: true };
  } catch (err: any) {
    return { ok: false, error: err?.message || 'Network error moderating user account.' };
  }
}

export async function recordAdminAuditLogToSupabase(params: {
  action: string;
  targetType: string;
  targetId: string;
  details?: Record<string, any>;
}): Promise<boolean> {
  const auth = getAdminAuthHeaders();
  if (!auth) return false;
  try {
    const resp = await fetch(`${SUPABASE_URL}/rest/v1/admin_audit_logs`, {
      method: 'POST',
      headers: { ...auth.headers, 'Prefer': 'return=minimal' },
      body: JSON.stringify({
        admin_id: UUID_REGEX.test(auth.adminId) ? auth.adminId : null,
        admin_email: auth.adminEmail,
        action: params.action.slice(0, 100),
        target_type: params.targetType.slice(0, 50),
        target_id: params.targetId.slice(0, 100),
        details: params.details || {}
      })
    });
    return resp.ok;
  } catch {
    return false;
  }
}

export async function fetchLiveAuditLogs(): Promise<LiveAuditLogItem[]> {
  const auth = getAdminAuthHeaders();
  if (!auth) return [];
  try {
    const selectCols = 'id,admin_id,admin_email,action,target_type,target_id,details,created_at';
    const resp = await fetch(
      `${SUPABASE_URL}/rest/v1/admin_audit_logs?select=${encodeURIComponent(selectCols)}&order=created_at.desc&limit=100`,
      { headers: auth.headers }
    );
    if (!resp.ok) return [];
    const rows = await resp.json();
    if (!Array.isArray(rows)) return [];
    return rows.map((r: any) => ({
      id: r.id,
      adminId: r.admin_id || '',
      adminEmail: r.admin_email || 'admin',
      action: r.action || 'ACTION',
      targetType: r.target_type || 'SYSTEM',
      targetId: r.target_id || '',
      details: typeof r.details === 'string' ? r.details : JSON.stringify(r.details || {}),
      createdAt: r.created_at ? new Date(r.created_at).toLocaleString() : 'Recently'
    }));
  } catch (err) {
    console.warn('[Admin] fetchLiveAuditLogs notice:', err);
    return [];
  }
}
