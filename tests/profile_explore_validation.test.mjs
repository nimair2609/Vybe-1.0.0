import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  MAX_PROFILE_MEDIA_BYTES,
  filterExploreCommunities,
  filterProfileTabPosts,
  safeExternalUrl,
  sanitizePostgrestSearchTerm,
  validateProfileEditInput,
  validateProfileMediaFile
} from '../src/utils/profileValidation.ts';

function makeUser(overrides = {}) {
  return {
    id: 'user-1',
    username: 'aria_vybe',
    displayName: 'Aria Chen',
    publicVybeId: 'VYBE-1001',
    bio: 'Creator',
    avatarUrl: '',
    coverUrl: '',
    isVerified: false,
    isVip: false,
    isVerifiedBusiness: false,
    isWorker: false,
    followersCount: 10,
    followingCount: 5,
    postsCount: 2,
    livePulse: 'None',
    isOnline: true,
    badges: [],
    joinedDate: 'Jan 2025',
    ...overrides
  };
}

function makePost(id, overrides = {}) {
  return {
    id,
    author: makeUser(),
    caption: `Post ${id}`,
    mediaUrls: [],
    mediaType: 'IMAGE',
    timestamp: '1h',
    likesCount: 0,
    commentsCount: 0,
    savesCount: 0,
    sharesCount: 0,
    viewsCount: 0,
    isLiked: false,
    isSaved: false,
    tags: [],
    comments: [],
    reactionsCount: { LOVE: 0, FLAME: 0, LAUGH: 0, WOW: 0, SAD: 0 },
    ...overrides
  };
}

describe('VYBE Profile & Explore Validation Suite', () => {
  it('safeExternalUrl accepts https/http and bare domains while blocking dangerous schemes', () => {
    assert.equal(safeExternalUrl('https://vybeonline.netlify.app/u/aria'), 'https://vybeonline.netlify.app/u/aria');
    assert.equal(safeExternalUrl('http://example.org/work'), 'http://example.org/work');
    assert.equal(safeExternalUrl('vybe.studio/portfolio'), 'https://vybe.studio/portfolio');

    assert.equal(safeExternalUrl('javascript:alert(1)'), null);
    assert.equal(safeExternalUrl('data:text/html,<script>alert(1)</script>'), null);
    assert.equal(safeExternalUrl('vbscript:msgbox(1)'), null);
    assert.equal(safeExternalUrl('file:///etc/passwd'), null);
    assert.equal(safeExternalUrl('intent://scan/#Intent'), null);
    assert.equal(safeExternalUrl('not-a-domain'), null);
    assert.equal(safeExternalUrl('   '), null);
  });

  it('validateProfileEditInput enforces field bounds, deduplicates tags, and normalizes website URLs', () => {
    const ok = validateProfileEditInput({
      displayName: '  Aria   Chen ',
      bio: 'Building calm spaces',
      location: 'Bengaluru,  India',
      website: 'aria.design/projects',
      interests: ['#Design', 'Music', 'design', '  Photography  ']
    });
    assert.equal(ok.valid, true);
    assert.equal(ok.displayName, 'Aria Chen');
    assert.equal(ok.location, 'Bengaluru, India');
    assert.equal(ok.website, 'https://aria.design/projects');
    assert.deepEqual(ok.interests, ['Design', 'Music', 'Photography']);

    const blankName = validateProfileEditInput({
      displayName: '   ',
      bio: '',
      location: '',
      website: '',
      interests: []
    });
    assert.equal(blankName.valid, false);

    const longBio = validateProfileEditInput({
      displayName: 'Aria',
      bio: 'a'.repeat(241),
      location: '',
      website: '',
      interests: []
    });
    assert.equal(longBio.valid, false);

    const badUrl = validateProfileEditInput({
      displayName: 'Aria',
      bio: '',
      location: '',
      website: 'javascript:alert(1)',
      interests: []
    });
    assert.equal(badUrl.valid, false);

    const tooManyTags = validateProfileEditInput({
      displayName: 'Aria',
      bio: '',
      location: '',
      website: '',
      interests: Array.from({ length: 11 }, (_, i) => `tag${i}`)
    });
    assert.equal(tooManyTags.valid, false);
  });

  it('validateProfileMediaFile accepts supported images up to 10MB and rejects oversized or invalid MIME files', () => {
    assert.equal(validateProfileMediaFile({ type: 'image/jpeg', size: 1024 }).valid, true);
    assert.equal(validateProfileMediaFile({ type: 'image/png', size: MAX_PROFILE_MEDIA_BYTES }).valid, true);
    assert.equal(validateProfileMediaFile({ type: 'image/webp; charset=utf-8', size: 2048 }).valid, true);
    assert.equal(validateProfileMediaFile({ type: 'image/heic', size: 4096 }).valid, true);

    assert.equal(validateProfileMediaFile({ type: 'image/jpeg', size: 0 }).valid, false);
    assert.equal(validateProfileMediaFile({ type: 'image/png', size: MAX_PROFILE_MEDIA_BYTES + 1 }).valid, false);
    assert.equal(validateProfileMediaFile({ type: 'video/mp4', size: 1024 }).valid, false);
    assert.equal(validateProfileMediaFile({ type: 'image/svg+xml', size: 1024 }).valid, false);
  });

  it('sanitizePostgrestSearchTerm strips @/# prefixes and PostgREST filter injection operators', () => {
    assert.equal(sanitizePostgrestSearchTerm('  @aria_vybe '), 'aria_vybe');
    assert.equal(sanitizePostgrestSearchTerm('#MindfulLiving'), 'MindfulLiving');
    assert.equal(sanitizePostgrestSearchTerm('foo%(),*"\';bar'), 'foo bar');
    assert.equal(sanitizePostgrestSearchTerm('x'.repeat(100)).length, 64);
  });

  it('filterProfileTabPosts separates Posts, Vybe Reels, Unfiltered, Tagged, Highlights, and Saved without duplication', () => {
    const owner = makeUser({ id: 'u1', username: 'aria_vybe' });
    const other = makeUser({ id: 'u2', username: 'rohan_k' });

    const photoPost = makePost('p_photo', { author: owner, mediaType: 'IMAGE' });
    const reelPost = makePost('p_reel', { author: owner, mediaType: 'REEL', isVybesReel: true });
    const videoPost = makePost('p_video', { author: owner, mediaType: 'VIDEO' });
    const unfilteredPost = makePost('p_raw', { author: owner, isUnfiltered: true });
    const selfMention = makePost('p_self_tag', { author: owner, caption: 'Shoutout @aria_vybe' });
    const peerMention = makePost('p_peer_tag', { author: other, caption: 'Collab with @aria_vybe' });
    const savedPeer = makePost('p_saved', { author: other, isSaved: true });

    const userPosts = [photoPost, reelPost, videoPost, unfilteredPost, selfMention];
    const allPosts = [...userPosts, peerMention, savedPeer];
    const common = {
      userPosts,
      savedPosts: [savedPeer],
      allPosts,
      pinnedPostIds: ['p_raw', 'p_photo'],
      username: owner.username,
      userId: owner.id
    };

    assert.deepEqual(
      filterProfileTabPosts({ ...common, tab: 'POSTS' }).map(p => p.id),
      ['p_photo', 'p_self_tag']
    );
    assert.deepEqual(
      filterProfileTabPosts({ ...common, tab: 'REELS' }).map(p => p.id),
      ['p_reel', 'p_video']
    );
    assert.deepEqual(
      filterProfileTabPosts({ ...common, tab: 'UNFILTERED' }).map(p => p.id),
      ['p_raw']
    );
    assert.deepEqual(
      filterProfileTabPosts({ ...common, tab: 'TAGGED' }).map(p => p.id),
      ['p_peer_tag']
    );
    assert.deepEqual(
      filterProfileTabPosts({ ...common, tab: 'HIGHLIGHTS' }).map(p => p.id),
      ['p_raw', 'p_photo']
    );
    assert.deepEqual(
      filterProfileTabPosts({ ...common, tab: 'SAVED' }).map(p => p.id),
      ['p_saved']
    );
  });

  it('filterExploreCommunities matches by name, description, and category', () => {
    const communities = [
      { id: 'c1', name: 'Indie Sound Lab', description: 'Producers & vocalists', category: 'Music', avatarUrl: '', bannerUrl: '', membersCount: 42, isJoined: false, tags: [] },
      { id: 'c2', name: 'Bengaluru Runners', description: 'Morning 5k club', category: 'Fitness', avatarUrl: '', bannerUrl: '', membersCount: 19, isJoined: true, tags: [] }
    ];

    assert.equal(filterExploreCommunities(communities, '').length, 2);
    assert.deepEqual(filterExploreCommunities(communities, 'music').map(c => c.id), ['c1']);
    assert.deepEqual(filterExploreCommunities(communities, '5k').map(c => c.id), ['c2']);
    assert.deepEqual(filterExploreCommunities(communities, 'indie').map(c => c.id), ['c1']);
    assert.deepEqual(filterExploreCommunities(communities, 'robotics'), []);
  });
});
