// Supabase Edge Function: music-catalog
// Secure server-side proxy for licensed music providers (Jamendo App Sync / Custom Licensed Regional API).
// Keeps provider credentials strictly in Supabase Edge Function Secrets (never exposed to Android clients).
// Enforces explicit commercial/app sync license approval before returning playable stream URLs.

declare const Deno: {
  env: { get(key: string): string | undefined };
  serve(handler: (req: Request) => Promise<Response> | Response): void;
};

interface LicensedTrackWire {
  id: string;
  provider: string;
  title: string;
  artist: string;
  albumTitle?: string | null;
  artworkUrl?: string | null;
  previewStreamUrl?: string | null;
  durationMs: number;
  genre?: string | null;
  mood?: string | null;
  language?: string | null;
  isSyncLicensed: boolean;
  isAvailable: boolean;
  licenseSummary?: string | null;
  attributionUrl?: string | null;
}

interface MusicCatalogWireResponse {
  status: 'OK' | 'PROVIDER_NOT_CONFIGURED' | 'LICENSE_APPROVAL_REQUIRED' | 'UNAVAILABLE';
  provider: string;
  licenseVerified: boolean;
  message?: string | null;
  availableCategories: string[];
  tracks: LicensedTrackWire[];
}

const SUPPORTED_CATEGORIES = [
  'TRENDING',
  'HINDI',
  'BOLLYWOOD',
  'PUNJABI',
  'INTERNATIONAL',
  'INSTRUMENTAL',
  'CHILL',
  'UPBEAT',
  'ROMANTIC',
  'ENERGETIC'
];

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Content-Type': 'application/json'
};

function isNonPlaceholder(val: string | undefined): boolean {
  if (!val) return false;
  const trimmed = val.trim();
  return trimmed.length > 0 && trimmed.toLowerCase() !== 'placeholder';
}

function mapCategoryTags(category: string): string {
  switch (category.toUpperCase()) {
    case 'HINDI':
      return 'indian+hindi+world';
    case 'BOLLYWOOD':
      return 'indian+cinematic+pop';
    case 'PUNJABI':
      return 'bhangra+punjabi+beats';
    case 'INSTRUMENTAL':
      return 'instrumental+acoustic+ambient';
    case 'CHILL':
      return 'chillout+lofi+relaxing';
    case 'UPBEAT':
      return 'happy+upbeat+dance';
    case 'ROMANTIC':
      return 'romantic+love+soul';
    case 'ENERGETIC':
      return 'energetic+workout+electronic';
    case 'INTERNATIONAL':
      return 'pop+indie+electronic';
    case 'TRENDING':
    default:
      return '';
  }
}

Deno.serve(async (req: Request): Promise<Response> => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  const url = new URL(req.url);
  const query = (url.searchParams.get('q') || '').trim();
  const category = (url.searchParams.get('category') || 'TRENDING').trim().toUpperCase();
  const mood = (url.searchParams.get('mood') || '').trim();
  const genre = (url.searchParams.get('genre') || '').trim();
  const trackId = (url.searchParams.get('trackId') || '').trim();
  const limit = Math.min(Math.max(Number(url.searchParams.get('limit') || '25'), 1), 50);

  const configuredProvider = (Deno.env.get('MUSIC_PROVIDER') || '').trim().toLowerCase();
  const customBaseUrl = Deno.env.get('MUSIC_PROVIDER_BASE_URL');
  const customApiKey = Deno.env.get('MUSIC_PROVIDER_API_KEY');
  const jamendoClientId = Deno.env.get('JAMENDO_CLIENT_ID');
  const licenseApproved = (Deno.env.get('MUSIC_LICENSE_APPROVED') || '').trim().toLowerCase() === 'true';
  const syncVerified = (Deno.env.get('MUSIC_COMMERCIAL_SYNC_VERIFIED') || '').trim().toLowerCase() === 'true';

  const hasCustomProvider = isNonPlaceholder(customBaseUrl) && isNonPlaceholder(customApiKey);
  const hasJamendoProvider = isNonPlaceholder(jamendoClientId);

  if (!hasCustomProvider && !hasJamendoProvider) {
    const body: MusicCatalogWireResponse = {
      status: 'PROVIDER_NOT_CONFIGURED',
      provider: 'unconfigured',
      licenseVerified: false,
      message:
        'Music catalog API credentials are not configured on the server. Set MUSIC_PROVIDER_API_KEY / JAMENDO_CLIENT_ID and MUSIC_LICENSE_APPROVED=true in Supabase Edge Function secrets.',
      availableCategories: SUPPORTED_CATEGORIES,
      tracks: []
    };
    return new Response(JSON.stringify(body), { status: 200, headers: corsHeaders });
  }

  if (!licenseApproved || !syncVerified) {
    const body: MusicCatalogWireResponse = {
      status: 'LICENSE_APPROVAL_REQUIRED',
      provider: hasCustomProvider ? configuredProvider || 'custom_licensed' : 'jamendo',
      licenseVerified: false,
      message:
        'Music provider credentials are present, but commercial Story/Reel sync licensing approval (MUSIC_LICENSE_APPROVED=true and MUSIC_COMMERCIAL_SYNC_VERIFIED=true) has not been enabled.',
      availableCategories: SUPPORTED_CATEGORIES,
      tracks: []
    };
    return new Response(JSON.stringify(body), { status: 200, headers: corsHeaders });
  }

  try {
    // Adapter 1: Custom Licensed Regional / Commercial Music Provider (Hindi, Bollywood, Punjabi, International, etc.)
    if (hasCustomProvider && configuredProvider !== 'jamendo') {
      const upstream = new URL('/v1/tracks', customBaseUrl!.replace(/\/$/, ''));
      if (trackId) upstream.searchParams.set('trackId', trackId);
      if (query) upstream.searchParams.set('q', query);
      if (category) upstream.searchParams.set('category', category);
      if (mood) upstream.searchParams.set('mood', mood);
      if (genre) upstream.searchParams.set('genre', genre);
      upstream.searchParams.set('limit', String(limit));

      const resp = await fetch(upstream.toString(), {
        headers: {
          Authorization: `Bearer ${customApiKey!.trim()}`,
          Accept: 'application/json'
        }
      });

      if (!resp.ok) {
        return new Response(
          JSON.stringify({
            status: 'UNAVAILABLE',
            provider: configuredProvider || 'custom_licensed',
            licenseVerified: true,
            message: `Upstream licensed music provider returned HTTP ${resp.status}.`,
            availableCategories: SUPPORTED_CATEGORIES,
            tracks: []
          } satisfies MusicCatalogWireResponse),
          { status: 200, headers: corsHeaders }
        );
      }

      const raw = await resp.json();
      const rawTracks: any[] = Array.isArray(raw?.tracks) ? raw.tracks : [];
      const mapped: LicensedTrackWire[] = rawTracks
        .filter((t) => Boolean(t && t.id && t.title && t.syncLicensed === true))
        .map((t) => ({
          id: `${configuredProvider || 'licensed'}:${String(t.id)}`,
          provider: configuredProvider || 'custom_licensed',
          title: String(t.title),
          artist: String(t.artist || 'Licensed Artist'),
          albumTitle: t.albumTitle || null,
          artworkUrl: t.artworkUrl || null,
          previewStreamUrl: t.streamUrl || t.previewUrl || null,
          durationMs: Math.max(Number(t.durationMs || 30000), 1000),
          genre: t.genre || null,
          mood: t.mood || null,
          language: t.language || null,
          isSyncLicensed: true,
          isAvailable: Boolean(t.streamUrl || t.previewUrl),
          licenseSummary: t.licenseSummary || 'Commercial Sync Licensed',
          attributionUrl: t.attributionUrl || null
        }));

      const categories: string[] = Array.isArray(raw?.availableCategories)
        ? raw.availableCategories
        : SUPPORTED_CATEGORIES;

      return new Response(
        JSON.stringify({
          status: 'OK',
          provider: configuredProvider || 'custom_licensed',
          licenseVerified: true,
          availableCategories: categories,
          tracks: mapped
        } satisfies MusicCatalogWireResponse),
        { status: 200, headers: corsHeaders }
      );
    }

    // Adapter 2: Jamendo Licensed Catalog API (with explicit app-sync verification)
    const jamendoUrl = new URL('https://api.jamendo.com/v3.0/tracks/');
    jamendoUrl.searchParams.set('client_id', jamendoClientId!.trim());
    jamendoUrl.searchParams.set('format', 'json');
    jamendoUrl.searchParams.set('limit', String(limit));
    jamendoUrl.searchParams.set('include', 'musicinfo+licenses');
    jamendoUrl.searchParams.set('audioformat', 'mp32');

    if (trackId) {
      const cleanId = trackId.includes(':') ? trackId.split(':')[1] : trackId;
      jamendoUrl.searchParams.set('id', cleanId);
    } else {
      if (query) {
        jamendoUrl.searchParams.set('namesearch', query);
      }
      const tags = [
        mapCategoryTags(category),
        genre.toLowerCase(),
        mood.toLowerCase()
      ]
        .filter(Boolean)
        .join('+');
      if (tags) {
        jamendoUrl.searchParams.set('tags', tags);
      }
      if (category === 'INSTRUMENTAL') {
        jamendoUrl.searchParams.set('vocalinstrumental', 'instrumental');
      }
      jamendoUrl.searchParams.set('order', 'popularity_week');
    }

    const jResp = await fetch(jamendoUrl.toString());
    if (!jResp.ok) {
      return new Response(
        JSON.stringify({
          status: 'UNAVAILABLE',
          provider: 'jamendo',
          licenseVerified: true,
          message: `Jamendo API returned HTTP ${jResp.status}.`,
          availableCategories: SUPPORTED_CATEGORIES,
          tracks: []
        } satisfies MusicCatalogWireResponse),
        { status: 200, headers: corsHeaders }
      );
    }

    const jJson = await jResp.json();
    const results: any[] = Array.isArray(jJson?.results) ? jJson.results : [];
    const tracks: LicensedTrackWire[] = results
      .filter((item) => Boolean(item?.id && item?.name && item?.audiodownload_allowed !== false && item?.audio))
      .map((item) => {
        const genres: string[] = item?.musicinfo?.tags?.genres || [];
        const vartags: string[] = item?.musicinfo?.tags?.vartags || [];
        return {
          id: `jamendo:${String(item.id)}`,
          provider: 'jamendo',
          title: String(item.name),
          artist: String(item.artist_name || 'Independent Artist'),
          albumTitle: item.album_name || null,
          artworkUrl: item.album_image || item.image || null,
          previewStreamUrl: String(item.audio),
          durationMs: Math.max(Number(item.duration || 30) * 1000, 1000),
          genre: genres[0] || null,
          mood: vartags[0] || null,
          language: item?.musicinfo?.lang || null,
          isSyncLicensed: true,
          isAvailable: Boolean(item.audio),
          licenseSummary: item.license_ccurl || 'Jamendo App Sync Verified',
          attributionUrl: item.shareurl || null
        };
      });

    return new Response(
      JSON.stringify({
        status: 'OK',
        provider: 'jamendo',
        licenseVerified: true,
        availableCategories: [
          'TRENDING',
          'INTERNATIONAL',
          'INSTRUMENTAL',
          'CHILL',
          'UPBEAT',
          'ROMANTIC',
          'ENERGETIC'
        ],
        tracks
      } satisfies MusicCatalogWireResponse),
      { status: 200, headers: corsHeaders }
    );
  } catch (err: any) {
    return new Response(
      JSON.stringify({
        status: 'UNAVAILABLE',
        provider: configuredProvider || 'unknown',
        licenseVerified: false,
        message: err?.message || 'Unable to query licensed music provider.',
        availableCategories: SUPPORTED_CATEGORIES,
        tracks: []
      } satisfies MusicCatalogWireResponse),
      { status: 200, headers: corsHeaders }
    );
  }
});
