import { Request, Response } from 'express';

export interface BackendLicensedTrack {
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

export interface BackendMusicCatalogResponse {
  status: 'OK' | 'PROVIDER_NOT_CONFIGURED' | 'LICENSE_APPROVAL_REQUIRED' | 'UNAVAILABLE';
  provider: string;
  licenseVerified: boolean;
  message?: string | null;
  availableCategories: string[];
  tracks: BackendLicensedTrack[];
}

export interface MusicCatalogQuery {
  query?: string;
  category?: string;
  genre?: string;
  mood?: string;
  trackId?: string;
  limit?: number;
}

export interface ServerMusicProviderAdapter {
  readonly id: string;
  isConfigured(): boolean;
  isLicenseApproved(): boolean;
  fetchCatalog(params: MusicCatalogQuery): Promise<BackendMusicCatalogResponse>;
}

const DEFAULT_CATEGORIES = [
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

function isValidSecret(value: string | undefined): boolean {
  if (!value) return false;
  const trimmed = value.trim();
  return trimmed.length > 0 && trimmed.toLowerCase() !== 'placeholder';
}

function isApprovedFlag(value: string | undefined): boolean {
  return (value || '').trim().toLowerCase() === 'true';
}

class CustomLicensedRestAdapter implements ServerMusicProviderAdapter {
  readonly id: string;

  constructor(providerName: string) {
    this.id = providerName || 'custom_licensed';
  }

  isConfigured(): boolean {
    return (
      isValidSecret(process.env.MUSIC_PROVIDER_BASE_URL) &&
      isValidSecret(process.env.MUSIC_PROVIDER_API_KEY)
    );
  }

  isLicenseApproved(): boolean {
    return (
      isApprovedFlag(process.env.MUSIC_LICENSE_APPROVED) &&
      isApprovedFlag(process.env.MUSIC_COMMERCIAL_SYNC_VERIFIED)
    );
  }

  async fetchCatalog(params: MusicCatalogQuery): Promise<BackendMusicCatalogResponse> {
    if (!this.isConfigured()) {
      return {
        status: 'PROVIDER_NOT_CONFIGURED',
        provider: this.id,
        licenseVerified: false,
        message: 'Licensed music provider credentials are not configured on the backend server.',
        availableCategories: DEFAULT_CATEGORIES,
        tracks: []
      };
    }

    if (!this.isLicenseApproved()) {
      return {
        status: 'LICENSE_APPROVAL_REQUIRED',
        provider: this.id,
        licenseVerified: false,
        message:
          'Commercial Story/Reel sync license verification flags (MUSIC_LICENSE_APPROVED=true, MUSIC_COMMERCIAL_SYNC_VERIFIED=true) are required before serving audio streams.',
        availableCategories: DEFAULT_CATEGORIES,
        tracks: []
      };
    }

    const baseUrl = process.env.MUSIC_PROVIDER_BASE_URL!.trim().replace(/\/$/, '');
    const apiKey = process.env.MUSIC_PROVIDER_API_KEY!.trim();
    const target = new URL(`${baseUrl}/v1/tracks`);
    if (params.trackId) target.searchParams.set('trackId', params.trackId);
    if (params.query) target.searchParams.set('q', params.query);
    if (params.category) target.searchParams.set('category', params.category);
    if (params.genre) target.searchParams.set('genre', params.genre);
    if (params.mood) target.searchParams.set('mood', params.mood);
    target.searchParams.set('limit', String(params.limit || 25));

    const resp = await fetch(target.toString(), {
      headers: {
        Authorization: `Bearer ${apiKey}`,
        Accept: 'application/json'
      }
    });

    if (!resp.ok) {
      return {
        status: 'UNAVAILABLE',
        provider: this.id,
        licenseVerified: true,
        message: `Licensed music provider responded with HTTP ${resp.status}.`,
        availableCategories: DEFAULT_CATEGORIES,
        tracks: []
      };
    }

    const data: any = await resp.json();
    const rawTracks: any[] = Array.isArray(data?.tracks) ? data.tracks : [];
    const tracks: BackendLicensedTrack[] = rawTracks
      .filter((t) => Boolean(t && t.id && t.title && t.syncLicensed === true))
      .map((t) => ({
        id: `${this.id}:${String(t.id)}`,
        provider: this.id,
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

    return {
      status: 'OK',
      provider: this.id,
      licenseVerified: true,
      availableCategories: Array.isArray(data?.availableCategories)
        ? data.availableCategories
        : DEFAULT_CATEGORIES,
      tracks
    };
  }
}

class JamendoLicensedServerAdapter implements ServerMusicProviderAdapter {
  readonly id = 'jamendo';

  isConfigured(): boolean {
    return isValidSecret(process.env.JAMENDO_CLIENT_ID);
  }

  isLicenseApproved(): boolean {
    return (
      isApprovedFlag(process.env.MUSIC_LICENSE_APPROVED) &&
      isApprovedFlag(process.env.MUSIC_COMMERCIAL_SYNC_VERIFIED)
    );
  }

  private mapCategoryTag(category: string): string {
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
      default:
        return '';
    }
  }

  async fetchCatalog(params: MusicCatalogQuery): Promise<BackendMusicCatalogResponse> {
    if (!this.isConfigured()) {
      return {
        status: 'PROVIDER_NOT_CONFIGURED',
        provider: this.id,
        licenseVerified: false,
        message: 'JAMENDO_CLIENT_ID is not configured in server secrets.',
        availableCategories: DEFAULT_CATEGORIES,
        tracks: []
      };
    }

    if (!this.isLicenseApproved()) {
      return {
        status: 'LICENSE_APPROVAL_REQUIRED',
        provider: this.id,
        licenseVerified: false,
        message:
          'Sync licensing flags (MUSIC_LICENSE_APPROVED=true, MUSIC_COMMERCIAL_SYNC_VERIFIED=true) must be enabled after verifying commercial app sync rights.',
        availableCategories: DEFAULT_CATEGORIES,
        tracks: []
      };
    }

    const url = new URL('https://api.jamendo.com/v3.0/tracks/');
    url.searchParams.set('client_id', process.env.JAMENDO_CLIENT_ID!.trim());
    url.searchParams.set('format', 'json');
    url.searchParams.set('limit', String(params.limit || 25));
    url.searchParams.set('include', 'musicinfo+licenses');
    url.searchParams.set('audioformat', 'mp32');

    if (params.trackId) {
      const cleanId = params.trackId.includes(':') ? params.trackId.split(':')[1] : params.trackId;
      url.searchParams.set('id', cleanId);
    } else {
      if (params.query) {
        url.searchParams.set('namesearch', params.query);
      }
      const tags = [
        this.mapCategoryTag(params.category || 'TRENDING'),
        (params.genre || '').toLowerCase(),
        (params.mood || '').toLowerCase()
      ]
        .filter(Boolean)
        .join('+');
      if (tags) {
        url.searchParams.set('tags', tags);
      }
      if ((params.category || '').toUpperCase() === 'INSTRUMENTAL') {
        url.searchParams.set('vocalinstrumental', 'instrumental');
      }
      url.searchParams.set('order', 'popularity_week');
    }

    const resp = await fetch(url.toString());
    if (!resp.ok) {
      return {
        status: 'UNAVAILABLE',
        provider: this.id,
        licenseVerified: true,
        message: `Jamendo API returned HTTP ${resp.status}.`,
        availableCategories: DEFAULT_CATEGORIES,
        tracks: []
      };
    }

    const payload: any = await resp.json();
    const results: any[] = Array.isArray(payload?.results) ? payload.results : [];
    const tracks: BackendLicensedTrack[] = results
      .filter((r) => Boolean(r?.id && r?.name && r?.audio && r?.audiodownload_allowed !== false))
      .map((r) => ({
        id: `jamendo:${String(r.id)}`,
        provider: 'jamendo',
        title: String(r.name),
        artist: String(r.artist_name || 'Independent Artist'),
        albumTitle: r.album_name || null,
        artworkUrl: r.album_image || r.image || null,
        previewStreamUrl: String(r.audio),
        durationMs: Math.max(Number(r.duration || 30) * 1000, 1000),
        genre: r?.musicinfo?.tags?.genres?.[0] || null,
        mood: r?.musicinfo?.tags?.vartags?.[0] || null,
        language: r?.musicinfo?.lang || null,
        isSyncLicensed: true,
        isAvailable: Boolean(r.audio),
        licenseSummary: r.license_ccurl || 'Jamendo App Sync Verified',
        attributionUrl: r.shareurl || null
      }));

    return {
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
    };
  }
}

export function resolveActiveServerMusicAdapter(): ServerMusicProviderAdapter | null {
  const configured = (process.env.MUSIC_PROVIDER || '').trim().toLowerCase();
  const custom = new CustomLicensedRestAdapter(
    configured && configured !== 'placeholder' ? configured : 'custom_licensed'
  );
  const jamendo = new JamendoLicensedServerAdapter();

  if (configured === 'jamendo') return jamendo;
  if (custom.isConfigured()) return custom;
  if (jamendo.isConfigured()) return jamendo;
  return null;
}

export async function handleMusicCatalogRequest(req: Request, res: Response): Promise<void> {
  try {
    const adapter = resolveActiveServerMusicAdapter();
    if (!adapter) {
      const unconfigured: BackendMusicCatalogResponse = {
        status: 'PROVIDER_NOT_CONFIGURED',
        provider: 'unconfigured',
        licenseVerified: false,
        message:
          'Licensed music provider API credentials are not configured on the server. Configure MUSIC_PROVIDER_API_KEY / JAMENDO_CLIENT_ID and MUSIC_LICENSE_APPROVED=true in server secrets.',
        availableCategories: DEFAULT_CATEGORIES,
        tracks: []
      };
      res.status(200).json(unconfigured);
      return;
    }

    const limit = Math.min(Math.max(Number(req.query.limit || '25'), 1), 50);
    const response = await adapter.fetchCatalog({
      query: typeof req.query.q === 'string' ? req.query.q.trim() : undefined,
      category: typeof req.query.category === 'string' ? req.query.category.trim() : 'TRENDING',
      genre: typeof req.query.genre === 'string' ? req.query.genre.trim() : undefined,
      mood: typeof req.query.mood === 'string' ? req.query.mood.trim() : undefined,
      trackId:
        typeof req.params?.trackId === 'string'
          ? req.params.trackId.trim()
          : typeof req.query.trackId === 'string'
            ? req.query.trackId.trim()
            : undefined,
      limit
    });
    res.status(200).json(response);
  } catch (err: any) {
    res.status(200).json({
      status: 'UNAVAILABLE',
      provider: 'error',
      licenseVerified: false,
      message: err?.message || 'Licensed music catalog temporarily unavailable.',
      availableCategories: DEFAULT_CATEGORIES,
      tracks: []
    } satisfies BackendMusicCatalogResponse);
  }
}
