import type { Community, Post, User } from '../types';

export const MAX_DISPLAY_NAME_LENGTH = 50;
export const MAX_BIO_LENGTH = 240;
export const MAX_LOCATION_LENGTH = 80;
export const MAX_WEBSITE_LENGTH = 200;
export const MAX_INTEREST_COUNT = 10;
export const MAX_INTEREST_TAG_LENGTH = 28;
export const MAX_PROFILE_MEDIA_BYTES = 10 * 1024 * 1024;

const ALLOWED_IMAGE_MIMES = new Set([
  'image/jpeg',
  'image/jpg',
  'image/png',
  'image/webp',
  'image/heic',
  'image/heif'
]);

const DOMAIN_REGEX =
  /^[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9-]{1,63})+(?::\d{1,5})?(?:\/[^\s]*)?$/;

export function safeExternalUrl(raw?: string | null): string | null {
  const trimmed = (raw || '').trim();
  if (!trimmed) return null;
  const lower = trimmed.toLowerCase();
  if (
    lower.startsWith('javascript:') ||
    lower.startsWith('data:') ||
    lower.startsWith('vbscript:') ||
    lower.startsWith('file:') ||
    lower.startsWith('intent:')
  ) {
    return null;
  }

  let candidate: string;
  if (lower.startsWith('https://') || lower.startsWith('http://')) {
    candidate = trimmed;
  } else if (trimmed.includes('://')) {
    return null;
  } else if (DOMAIN_REGEX.test(trimmed)) {
    candidate = `https://${trimmed}`;
  } else {
    return null;
  }

  if (candidate.length > MAX_WEBSITE_LENGTH) return null;

  try {
    const parsed = new URL(candidate);
    if ((parsed.protocol === 'https:' || parsed.protocol === 'http:') && parsed.hostname.includes('.')) {
      return candidate;
    }
    return null;
  } catch {
    return null;
  }
}

export interface ProfileEditValidationResult {
  valid: boolean;
  displayName: string;
  bio: string;
  location: string;
  website: string;
  interests: string[];
  error?: string;
}

export function validateProfileEditInput(input: {
  displayName: string;
  bio: string;
  location: string;
  website: string;
  interests: string[];
}): ProfileEditValidationResult {
  const cleanDisplay = (input.displayName || '').trim().replace(/\s+/g, ' ');
  if (!cleanDisplay) {
    return {
      valid: false,
      displayName: '',
      bio: '',
      location: '',
      website: '',
      interests: [],
      error: 'Display name cannot be empty.'
    };
  }
  if (cleanDisplay.length > MAX_DISPLAY_NAME_LENGTH) {
    return {
      valid: false,
      displayName: cleanDisplay,
      bio: '',
      location: '',
      website: '',
      interests: [],
      error: `Display name must be ${MAX_DISPLAY_NAME_LENGTH} characters or fewer.`
    };
  }

  const cleanBio = (input.bio || '').trim();
  if (cleanBio.length > MAX_BIO_LENGTH) {
    return {
      valid: false,
      displayName: cleanDisplay,
      bio: cleanBio,
      location: '',
      website: '',
      interests: [],
      error: `Bio must be ${MAX_BIO_LENGTH} characters or fewer.`
    };
  }

  const cleanLocation = (input.location || '').trim().replace(/\s+/g, ' ');
  if (cleanLocation.length > MAX_LOCATION_LENGTH) {
    return {
      valid: false,
      displayName: cleanDisplay,
      bio: cleanBio,
      location: cleanLocation,
      website: '',
      interests: [],
      error: `Location must be ${MAX_LOCATION_LENGTH} characters or fewer.`
    };
  }

  const rawWebsite = (input.website || '').trim();
  let cleanWebsite = '';
  if (rawWebsite) {
    const validated = safeExternalUrl(rawWebsite);
    if (!validated) {
      return {
        valid: false,
        displayName: cleanDisplay,
        bio: cleanBio,
        location: cleanLocation,
        website: rawWebsite,
        interests: [],
        error: 'Enter a valid website URL (e.g. https://example.com).'
      };
    }
    cleanWebsite = validated;
  }

  const seen = new Set<string>();
  const cleanInterests: string[] = [];
  for (const raw of input.interests || []) {
    const tag = raw.trim().replace(/^#/, '').trim();
    if (!tag) continue;
    if (tag.length > MAX_INTEREST_TAG_LENGTH) {
      return {
        valid: false,
        displayName: cleanDisplay,
        bio: cleanBio,
        location: cleanLocation,
        website: cleanWebsite,
        interests: cleanInterests,
        error: `Each interest tag must be ${MAX_INTEREST_TAG_LENGTH} characters or fewer.`
      };
    }
    const lower = tag.toLowerCase();
    if (!seen.has(lower)) {
      seen.add(lower);
      cleanInterests.push(tag);
    }
    if (cleanInterests.length > MAX_INTEREST_COUNT) {
      return {
        valid: false,
        displayName: cleanDisplay,
        bio: cleanBio,
        location: cleanLocation,
        website: cleanWebsite,
        interests: cleanInterests,
        error: `You can add up to ${MAX_INTEREST_COUNT} interest tags.`
      };
    }
  }

  return {
    valid: true,
    displayName: cleanDisplay,
    bio: cleanBio,
    location: cleanLocation,
    website: cleanWebsite,
    interests: cleanInterests
  };
}

export function validateProfileMediaFile(file: { type?: string; size?: number } | null | undefined): {
  valid: boolean;
  error?: string;
} {
  if (!file || typeof file.size !== 'number' || file.size <= 0) {
    return { valid: false, error: 'Selected image is empty or unreadable.' };
  }
  if (file.size > MAX_PROFILE_MEDIA_BYTES) {
    return { valid: false, error: 'Image must be 10 MB or smaller.' };
  }
  const mime = (file.type || '').trim().toLowerCase().split(';')[0];
  if (mime && !ALLOWED_IMAGE_MIMES.has(mime)) {
    return { valid: false, error: 'Only JPEG, PNG, WebP, or HEIC images are supported.' };
  }
  return { valid: true };
}

export function sanitizePostgrestSearchTerm(raw: string): string {
  return (raw || '')
    .trim()
    .replace(/^[@#]+/, '')
    .replace(/[%(),*\\"';]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 64);
}

export type ProfileTabKey = 'POSTS' | 'UNFILTERED' | 'TAGGED' | 'HIGHLIGHTS' | 'SAVED';

export function filterProfileTabPosts(params: {
  tab: ProfileTabKey;
  userPosts: Post[];
  savedPosts: Post[];
  allPosts: Post[];
  pinnedPostIds: string[];
  username: string;
  userId: string;
}): Post[] {
  const { tab, userPosts, savedPosts, allPosts, pinnedPostIds, username, userId } = params;
  switch (tab) {
    case 'UNFILTERED':
      return userPosts.filter(p => Boolean(p.isUnfiltered));
    case 'TAGGED': {
      const cleanHandle = (username || '').trim().replace(/^@/, '').toLowerCase();
      if (!cleanHandle) return [];
      return allPosts.filter(
        p =>
          p.author.id.toLowerCase() !== userId.toLowerCase() &&
          (p.caption.toLowerCase().includes(`@${cleanHandle}`) ||
            (p.tags || []).some(t => t.replace(/^[@#]/, '').toLowerCase() === cleanHandle))
      );
    }
    case 'HIGHLIGHTS':
      if (!pinnedPostIds || pinnedPostIds.length === 0) return [];
      return pinnedPostIds
        .map(id => userPosts.find(p => p.id === id))
        .filter((p): p is Post => Boolean(p));
    case 'SAVED':
      return savedPosts;
    case 'POSTS':
    default:
      return userPosts.filter(p => !p.isUnfiltered);
  }
}

export function filterExploreCommunities(communities: Community[], query: string): Community[] {
  const clean = (query || '').trim().toLowerCase();
  if (!clean) return communities;
  return communities.filter(
    c =>
      c.name.toLowerCase().includes(clean) ||
      (c.description || '').toLowerCase().includes(clean) ||
      (c.category || '').toLowerCase().includes(clean)
  );
}

export function isUserActiveInExplore(user: User, blockedIds: string[]): boolean {
  return !blockedIds.includes(user.id);
}
