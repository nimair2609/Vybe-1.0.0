import { createClient, type SupabaseClient } from '@supabase/supabase-js';

declare const __APP_VERSION__: string;
declare const __APP_BUILT_AT__: string;

const env = import.meta.env as Record<string, string | undefined>;

const rawUrl = (env.VITE_SUPABASE_URL || env.SUPABASE_URL || '').trim().replace(/\/+$/, '');
const rawAnonKey = (env.VITE_SUPABASE_ANON_KEY || env.SUPABASE_ANON_KEY || '').trim();

// Security guard: never allow a service_role key to initialize in browser code
export const hasForbiddenServiceRoleKey = /service_role/i.test(rawAnonKey);

export const supabaseUrl = rawUrl;
export const supabaseAnonKey = hasForbiddenServiceRoleKey ? '' : rawAnonKey;

export const isSupabaseConfigured =
  Boolean(supabaseUrl) &&
  Boolean(supabaseAnonKey) &&
  !supabaseUrl.includes('your-project-ref') &&
  !supabaseAnonKey.includes('your-supabase-public-anon');

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
  const stunUrl = (env.VITE_STUN_SERVER_URL || env.STUN_SERVER_URL || 'stun:stun.l.google.com:19302').trim();
  const turnUrl = (env.VITE_TURN_SERVER_URL || env.TURN_SERVER_URL || '').trim();
  const turnUsername = (env.VITE_TURN_USERNAME || env.TURN_USERNAME || '').trim();
  const turnCredential = (env.VITE_TURN_CREDENTIAL || env.TURN_CREDENTIAL || '').trim();

  const servers: RTCIceServer[] = [{ urls: stunUrl }];
  if (turnUrl) {
    servers.push({
      urls: turnUrl,
      ...(turnUsername ? { username: turnUsername } : {}),
      ...(turnCredential ? { credential: turnCredential } : {})
    });
  }
  return servers;
}
