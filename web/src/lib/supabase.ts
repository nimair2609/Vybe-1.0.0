import { createClient, type SupabaseClient } from '@supabase/supabase-js';

declare const __APP_VERSION__: string;
declare const __APP_BUILT_AT__: string;

const LIVE_SUPABASE_URL = 'https://cppwadedazpbevkfrchg.supabase.co';
const LIVE_SUPABASE_ANON_KEY = 'sb_publishable_MI-UdOqfccHVZIHjQq9vrw_IjEzeR7H';

const env = import.meta.env as Record<string, string | undefined>;

const rawUrl = (env.VITE_SUPABASE_URL || env.SUPABASE_URL || '').trim().replace(/\/+$/, '');
const rawAnonKey = (env.VITE_SUPABASE_ANON_KEY || env.SUPABASE_ANON_KEY || '').trim();

// Security guard: never allow a service_role key to initialize in browser code
export const hasForbiddenServiceRoleKey = /service_role/i.test(rawAnonKey);

const isValidEnvUrl =
  Boolean(rawUrl) &&
  rawUrl.startsWith('https://') &&
  !rawUrl.includes('placeholder') &&
  !rawUrl.includes('your-project-ref');

const isValidEnvKey =
  Boolean(rawAnonKey) &&
  !hasForbiddenServiceRoleKey &&
  !rawAnonKey.includes('placeholder') &&
  !rawAnonKey.includes('your-supabase-public-anon');

export const supabaseUrl = isValidEnvUrl ? rawUrl : LIVE_SUPABASE_URL;
export const supabaseAnonKey = hasForbiddenServiceRoleKey
  ? ''
  : isValidEnvKey
    ? rawAnonKey
    : LIVE_SUPABASE_ANON_KEY;

export const isSupabaseConfigured = Boolean(supabaseUrl) && Boolean(supabaseAnonKey);

export const APP_VERSION = typeof __APP_VERSION__ !== 'undefined' ? __APP_VERSION__ : '1.0.0';
export const APP_BUILT_AT = typeof __APP_BUILT_AT__ !== 'undefined' ? __APP_BUILT_AT__ : '';

export const supabase: SupabaseClient = createClient(
  isSupabaseConfigured ? supabaseUrl : 'https://placeholder-unconfigured.supabase.co',
  isSupabaseConfigured ? supabaseAnonKey : 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.e30.placeholder',
  {
    auth: {
      persistSession: true,
      autoRefreshToken: true,
      detectSessionInUrl: true,
      storageKey: 'vybe.web.auth.token'
    }
  }
);

export function getWebRtcIceServers(): RTCIceServer[] {
  const rawStun = (env.VITE_STUN_SERVER_URL || env.STUN_SERVER_URL || '').trim();
  const stunUrl =
    rawStun && !rawStun.toLowerCase().includes('placeholder')
      ? rawStun
      : 'stun:stun.l.google.com:19302';
  const rawTurn = (env.VITE_TURN_SERVER_URL || env.TURN_SERVER_URL || '').trim();
  const turnUrl = rawTurn && !rawTurn.toLowerCase().includes('placeholder') ? rawTurn : '';
  const turnUsername = (env.VITE_TURN_USERNAME || env.TURN_USERNAME || '').trim();
  const turnCredential = (env.VITE_TURN_CREDENTIAL || env.TURN_CREDENTIAL || '').trim();

  const servers: RTCIceServer[] = [
    { urls: stunUrl },
    { urls: 'stun:stun1.l.google.com:19302' }
  ];
  if (turnUrl) {
    servers.push({
      urls: turnUrl,
      ...(turnUsername && !turnUsername.toLowerCase().includes('placeholder') ? { username: turnUsername } : {}),
      ...(turnCredential && !turnCredential.toLowerCase().includes('placeholder') ? { credential: turnCredential } : {})
    });
  }
  return servers;
}
