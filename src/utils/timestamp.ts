export function parseIsoMillis(iso?: string | null): number | null {
  if (!iso || !iso.trim()) return null;
  const parsed = Date.parse(iso.trim());
  return Number.isNaN(parsed) ? null : parsed;
}

export function formatRelativeTimestamp(
  createdAtIso?: string | null,
  fallbackLabel: string = '',
  nowMs: number = Date.now()
): string {
  const epochMs = parseIsoMillis(createdAtIso);
  if (epochMs === null) {
    const trimmed = (fallbackLabel || '').trim();
    if (!trimmed) return '';
    if (/^\d{4}-\d{2}-\d{2}T/.test(trimmed)) {
      const retry = parseIsoMillis(trimmed);
      if (retry !== null) return formatRelativeFromEpoch(retry, nowMs);
    }
    return trimmed;
  }
  return formatRelativeFromEpoch(epochMs, nowMs);
}

export function formatRelativeFromEpoch(epochMs: number, nowMs: number = Date.now()): string {
  const rawDiff = nowMs - epochMs;
  if (rawDiff < 60_000) return 'Just now';

  const minutes = Math.floor(rawDiff / 60_000);
  if (minutes === 1) return '1 minute ago';
  if (minutes < 60) return `${minutes} minutes ago`;

  const hours = Math.floor(minutes / 60);
  const createdDate = new Date(epochMs);
  const nowDate = new Date(nowMs);
  const startOfToday = new Date(nowDate.getFullYear(), nowDate.getMonth(), nowDate.getDate()).getTime();
  const startOfYesterday = startOfToday - 86_400_000;

  if (hours === 1) return '1 hour ago';
  if (hours < 24 && epochMs >= startOfToday) return `${hours} hours ago`;
  if (epochMs >= startOfYesterday && epochMs < startOfToday) return 'Yesterday';
  if (hours < 24) return `${hours} hours ago`;

  const days = Math.floor(rawDiff / 86_400_000);
  if (days === 1) return 'Yesterday';
  if (days < 7) return `${days} days ago`;

  const sameYear = createdDate.getFullYear() === nowDate.getFullYear();
  return createdDate.toLocaleDateString(undefined, sameYear
    ? { month: 'short', day: 'numeric' }
    : { month: 'short', day: 'numeric', year: 'numeric' }
  );
}
