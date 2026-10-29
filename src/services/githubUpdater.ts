import { GitHubReleaseInfo } from '../types';

export const DEFAULT_GITHUB_REPO = 'nimair2609/Vybe-1.0.0';
export const INSTALLED_VERSION = '1.2.62';
export const INSTALLED_VERSION_CODE = 162;

export interface GitHubUpdateStatus {
  isChecking: boolean;
  repo: string;
  releaseInfo: GitHubReleaseInfo | null;
  error: string | null;
  lastCheckedTime: string | null;
  hasUpdate: boolean;
}

export function cleanRepoSlug(raw: string): string {
  return raw
    .trim()
    .replace(/^https?:\/\/github\.com\//, '')
    .replace(/^github\.com\//, '')
    .replace(/\.git$/, '')
    .split('/releases')[0]
    .split('/actions')[0]
    .split('/tree/')[0]
    .replace(/^\/+|\/+$/g, '');
}

export function compareVersions(v1: string, v2: string): number {
  const clean1 = v1.trim().replace(/^[vV]/, '').split('-')[0].split('+')[0];
  const clean2 = v2.trim().replace(/^[vV]/, '').split('-')[0].split('+')[0];
  if (clean1.toLowerCase() === clean2.toLowerCase()) return 0;

  const parts1 = clean1.split('.').map(p => parseInt(p.replace(/\D/g, ''), 10) || 0);
  const parts2 = clean2.split('.').map(p => parseInt(p.replace(/\D/g, ''), 10) || 0);
  const maxLen = Math.max(parts1.length, parts2.length);

  for (let i = 0; i < maxLen; i++) {
    const p1 = parts1[i] ?? 0;
    const p2 = parts2[i] ?? 0;
    if (p1 !== p2) return p1 > p2 ? 1 : -1;
  }
  return 0;
}

export function formatFileSize(bytes: number): string {
  if (bytes <= 0) return '';
  const mb = bytes / (1024 * 1024);
  return `${mb.toFixed(1)} MB`;
}

export async function fetchGitHubUpdates(repoSlug: string = DEFAULT_GITHUB_REPO): Promise<GitHubReleaseInfo> {
  const cleanRepo = cleanRepoSlug(repoSlug);
  if (!cleanRepo || !cleanRepo.includes('/')) {
    throw new Error(`Invalid repository format. Please enter as 'owner/repo' (e.g. ${DEFAULT_GITHUB_REPO})`);
  }

  // Fetch Releases, latest commit, and workflow runs in parallel
  const [releasesRes, commitsRes, runsRes] = await Promise.allSettled([
    fetch(`https://api.github.com/repos/${cleanRepo}/releases?per_page=10`, {
      headers: { Accept: 'application/vnd.github+json' }
    }),
    fetch(`https://api.github.com/repos/${cleanRepo}/commits?per_page=1`, {
      headers: { Accept: 'application/vnd.github+json' }
    }),
    fetch(`https://api.github.com/repos/${cleanRepo}/actions/runs?per_page=5`, {
      headers: { Accept: 'application/vnd.github+json' }
    })
  ]);

  let latestCommitSha: string | undefined;
  if (commitsRes.status === 'fulfilled' && commitsRes.value.ok) {
    try {
      const commits = await commitsRes.value.json();
      if (Array.isArray(commits) && commits.length > 0) {
        latestCommitSha = commits[0]?.sha?.substring(0, 7);
      }
    } catch {
      // ignore
    }
  }

  let latestActionRunNumber: number | undefined;
  if (runsRes.status === 'fulfilled' && runsRes.value.ok) {
    try {
      const runsData = await runsRes.value.json();
      const successfulRun = runsData?.workflow_runs?.find(
        (r: any) => r.status === 'completed' && r.conclusion === 'success'
      );
      if (successfulRun) {
        latestActionRunNumber = successfulRun.run_number;
      }
    } catch {
      // ignore
    }
  }

  if (releasesRes.status !== 'fulfilled' || !releasesRes.value.ok) {
    const status = releasesRes.status === 'fulfilled' ? releasesRes.value.status : 500;
    if (status === 404) {
      throw new Error(`Repository '${cleanRepo}' was not found on GitHub or is private.`);
    } else if (status === 403) {
      throw new Error('GitHub API rate limit exceeded. Please try again shortly.');
    }
    throw new Error(`Unable to reach GitHub API for repository '${cleanRepo}' (HTTP ${status}).`);
  }

  const releases = await releasesRes.value.json();
  if (!Array.isArray(releases) || releases.length === 0) {
    throw new Error(`No published releases found in repository '${cleanRepo}'.`);
  }

  const published = releases.filter((r: any) => !r.draft);
  if (published.length === 0) {
    throw new Error(`No published releases available yet in '${cleanRepo}'.`);
  }

  // Find release with apk or latest
  const bestRelease = published[0];
  const tagName = bestRelease.tag_name || 'latest';
  const apkAsset = bestRelease.assets?.find((a: any) => a.name?.endsWith('.apk'));

  const isNewer = compareVersions(tagName, INSTALLED_VERSION) > 0;
  const isOlder = compareVersions(tagName, INSTALLED_VERSION) < 0;

  return {
    tagName,
    name: bestRelease.name || tagName,
    body: bestRelease.body || 'No release notes provided for this version.',
    htmlUrl: bestRelease.html_url || `https://github.com/${cleanRepo}/releases`,
    downloadUrl: apkAsset?.browser_download_url || bestRelease.html_url,
    apkFileName: apkAsset?.name,
    apkFileSize: apkAsset?.size || 0,
    isNewerThanInstalled: isNewer,
    isOlderThanInstalled: isOlder,
    publishedAt: bestRelease.published_at || bestRelease.created_at,
    latestCommitShortSha: latestCommitSha,
    latestActionRunNumber
  };
}
