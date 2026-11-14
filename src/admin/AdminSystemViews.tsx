import React, { useMemo, useState } from 'react';
import {
  Users, FileText, Zap, Database, AlertTriangle, HelpCircle, Send,
  Radio, ScrollText, Download, ShieldCheck, Activity, RotateCcw
} from 'lucide-react';
import { Post, User } from '../types';
import { DiagnosticsSummary } from '../utils/performanceDiagnostics';
import { LiveReportItem } from '../services/supabaseService';
import {
  VybeButton, VybeCard, VybeEmptyState, VybeStatusChip
} from '../components/VybeDesignSystem';
import { AdminConfirmRequest, AdminSectionId, AuditRecord, VerificationApp } from './types';

interface FeatureFlagsState {
  unloadRooms: boolean;
  crossedPaths: boolean;
  timeCapsules: boolean;
  spiritualMode: boolean;
  maintenanceMode: boolean;
}

interface AdminSystemViewsProps {
  section: Extract<AdminSectionId, 'overview' | 'broadcast' | 'diagnostics' | 'audit' | 'settings'>;
  searchQuery: string;
  users: User[];
  posts: Post[];
  verificationQueue: VerificationApp[];
  reports: LiveReportItem[];
  auditLogs: AuditRecord[];
  diagnostics: DiagnosticsSummary;
  apiHealthOk: boolean | null;
  configApiAvailable: boolean;
  featureFlags: FeatureFlagsState;
  adminEmail: string;
  onNavigate: (section: AdminSectionId) => void;
  onResetDiagnostics: () => void;
  onSendBroadcast: (title: string, message: string) => Promise<boolean>;
  onToggleFeatureFlag: (key: keyof FeatureFlagsState, label: string, nextValue: boolean) => Promise<void>;
  requestConfirm: (req: AdminConfirmRequest) => void;
}

export const AdminSystemViews: React.FC<AdminSystemViewsProps> = ({
  section,
  searchQuery,
  users,
  posts,
  verificationQueue,
  reports,
  auditLogs,
  diagnostics,
  apiHealthOk,
  configApiAvailable,
  featureFlags,
  adminEmail,
  onNavigate,
  onResetDiagnostics,
  onSendBroadcast,
  onToggleFeatureFlag,
  requestConfirm,
}) => {
  const [broadcastTitle, setBroadcastTitle] = useState('');
  const [broadcastBody, setBroadcastBody] = useState('');
  const [isSendingBroadcast, setIsSendingBroadcast] = useState(false);

  const [streamFilter, setStreamFilter] = useState<'ALL' | 'CACHE' | 'DEDUP' | 'REST'>('ALL');
  const [auditActionFilter, setAuditActionFilter] = useState('ALL');

  const verifiedOrVipCount = useMemo(
    () => users.filter(u => u.isVerified || u.isVip || u.badges.length > 0).length,
    [users]
  );

  const filteredRequests = useMemo(() => {
    return diagnostics.recentRequests.filter(r => {
      if (streamFilter === 'CACHE') return r.fromCache;
      if (streamFilter === 'DEDUP') return r.deduplicated;
      if (streamFilter === 'REST') return !r.fromCache && !r.deduplicated;
      return true;
    });
  }, [diagnostics.recentRequests, streamFilter]);

  const auditActions = useMemo(() => {
    return Array.from(new Set(auditLogs.map(l => l.action))).slice(0, 12);
  }, [auditLogs]);

  const filteredAuditLogs = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    return auditLogs.filter(log => {
      if (auditActionFilter !== 'ALL' && log.action !== auditActionFilter) return false;
      if (!q) return true;
      return (
        log.action.toLowerCase().includes(q) ||
        log.target.toLowerCase().includes(q) ||
        log.details.toLowerCase().includes(q) ||
        log.admin.toLowerCase().includes(q)
      );
    });
  }, [auditLogs, auditActionFilter, searchQuery]);

  const handleExportAuditCsv = () => {
    const escape = (v: string) => `"${String(v || '').replace(/"/g, '""')}"`;
    const rows = [
      ['Action', 'Admin', 'Target', 'Details', 'Time'].join(','),
      ...filteredAuditLogs.map(l =>
        [escape(l.action), escape(l.admin), escape(l.target), escape(l.details), escape(l.time)].join(',')
      ),
    ];
    const blob = new Blob([rows.join('\n')], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `vybe-admin-audit-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  if (section === 'overview') {
    const pendingTotal = verificationQueue.length + reports.length;
    return (
      <div className="space-y-6 animate-fade-in">
        {/* Live KPI Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <VybeCard padding="md" className="space-y-1.5">
            <div className="flex items-center justify-between text-[var(--vybe-text-secondary)]">
              <span className="text-[11px] font-bold uppercase tracking-wider">Loaded Profiles</span>
              <Users className="w-4 h-4 text-[var(--vybe-accent)]" />
            </div>
            <div className="text-2xl font-extrabold brand-font text-[var(--vybe-text)]">
              {users.length.toLocaleString()}
            </div>
            <div className="text-[11px] text-emerald-500 font-medium">
              {verifiedOrVipCount} with official badges
            </div>
          </VybeCard>

          <VybeCard padding="md" className="space-y-1.5">
            <div className="flex items-center justify-between text-[var(--vybe-text-secondary)]">
              <span className="text-[11px] font-bold uppercase tracking-wider">Posts & Reels</span>
              <FileText className="w-4 h-4 text-[var(--vybe-accent-secondary)]" />
            </div>
            <div className="text-2xl font-extrabold brand-font text-[var(--vybe-text)]">
              {posts.length.toLocaleString()}
            </div>
            <div className="text-[11px] text-[var(--vybe-text-secondary)]">
              Recent Supabase batch (max 50)
            </div>
          </VybeCard>

          <VybeCard padding="md" className="space-y-1.5">
            <div className="flex items-center justify-between text-[var(--vybe-text-secondary)]">
              <span className="text-[11px] font-bold uppercase tracking-wider">Moderation Queues</span>
              <AlertTriangle className="w-4 h-4 text-amber-500" />
            </div>
            <div className="text-2xl font-extrabold brand-font text-[var(--vybe-text)]">{pendingTotal}</div>
            <div className="text-[11px] text-[var(--vybe-text-secondary)]">
              {verificationQueue.length} verifications · {reports.length} reports
            </div>
          </VybeCard>

          <VybeCard padding="md" className="space-y-1.5">
            <div className="flex items-center justify-between text-[var(--vybe-text-secondary)]">
              <span className="text-[11px] font-bold uppercase tracking-wider">Cache Hit Ratio</span>
              <Zap className="w-4 h-4 text-emerald-500" />
            </div>
            <div className="text-2xl font-extrabold brand-font text-emerald-500">
              {diagnostics.cacheHitRatio}%
            </div>
            <div className="text-[11px] text-[var(--vybe-text-secondary)]">
              {(diagnostics.estimatedBytesSaved / 1024).toFixed(1)} KB saved ({diagnostics.cacheHits} hits)
            </div>
          </VybeCard>
        </div>

        {/* System Architecture Status */}
        <VybeCard padding="md" className="space-y-3">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-bold text-[var(--vybe-text)] flex items-center gap-2">
              <Database className="w-4 h-4 text-[var(--vybe-accent)]" />
              <span>Infrastructure & Service Status</span>
            </h2>
            <VybeButton variant="ghost" size="sm" onClick={() => onNavigate('diagnostics')}>
              View Telemetry
            </VybeButton>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3 text-xs">
            <div className="p-3.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] space-y-1">
              <div className="text-[var(--vybe-text-secondary)]">Database & Auth</div>
              <div className="font-bold text-[var(--vybe-text)]">Supabase Cloud + RLS</div>
              <VybeStatusChip tone="success">Session Verified</VybeStatusChip>
            </div>
            <div className="p-3.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] space-y-1">
              <div className="text-[var(--vybe-text-secondary)]">Client Storage</div>
              <div className="font-bold text-[var(--vybe-text)]">IndexedDB SWR Cache</div>
              <VybeStatusChip tone="success">Active</VybeStatusChip>
            </div>
            <div className="p-3.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] space-y-1">
              <div className="text-[var(--vybe-text-secondary)]">Functions Health (/api/v1/health)</div>
              <div className="font-bold text-[var(--vybe-text)]">Netlify API Gateway</div>
              <VybeStatusChip tone={apiHealthOk === true ? 'success' : apiHealthOk === false ? 'warning' : 'neutral'}>
                {apiHealthOk === true ? 'Healthy' : apiHealthOk === false ? 'Unreachable' : 'Checking...'}
              </VybeStatusChip>
            </div>
          </div>
        </VybeCard>

        {/* Queues & Recent Activity */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
          <VybeCard padding="md" className="space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--vybe-text-secondary)]">
                Pending Verifications
              </h3>
              <button
                type="button"
                onClick={() => onNavigate('verifications')}
                className="text-xs font-semibold text-[var(--vybe-accent)] hover:underline"
              >
                Open Queue ({verificationQueue.length})
              </button>
            </div>
            {verificationQueue.length === 0 ? (
              <div className="p-4 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs text-[var(--vybe-text-secondary)] text-center">
                No pending verification applications in database.
              </div>
            ) : (
              <div className="space-y-2">
                {verificationQueue.slice(0, 4).map(app => (
                  <div
                    key={app.id}
                    className="p-3 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] flex items-center justify-between gap-2 text-xs"
                  >
                    <div className="min-w-0">
                      <div className="font-bold text-[var(--vybe-text)] truncate">@{app.username}</div>
                      <div className="text-[11px] text-[var(--vybe-text-secondary)] truncate">
                        {app.badge} · {app.category}
                      </div>
                    </div>
                    <VybeButton variant="secondary" size="sm" onClick={() => onNavigate('verifications')}>
                      Review
                    </VybeButton>
                  </div>
                ))}
              </div>
            )}
          </VybeCard>

          <VybeCard padding="md" className="space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--vybe-text-secondary)]">
                Recent Admin Activity
              </h3>
              <button
                type="button"
                onClick={() => onNavigate('audit')}
                className="text-xs font-semibold text-[var(--vybe-accent)] hover:underline"
              >
                Full Trail ({auditLogs.length})
              </button>
            </div>
            {auditLogs.length === 0 ? (
              <div className="p-4 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs text-[var(--vybe-text-secondary)] text-center">
                No admin audit logs recorded yet.
              </div>
            ) : (
              <div className="space-y-2">
                {auditLogs.slice(0, 4).map(log => (
                  <div
                    key={log.id}
                    className="p-3 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs"
                  >
                    <div className="flex items-center justify-between gap-2 mb-0.5">
                      <span className="font-bold text-[var(--vybe-accent)] font-mono truncate">{log.action}</span>
                      <span className="text-[10px] text-[var(--vybe-text-muted)] shrink-0">{log.time}</span>
                    </div>
                    <p className="text-[var(--vybe-text-secondary)] text-[11px] line-clamp-1">{log.details}</p>
                  </div>
                ))}
              </div>
            )}
          </VybeCard>
        </div>
      </div>
    );
  }

  if (section === 'broadcast') {
    const handleSubmit = (e: React.FormEvent) => {
      e.preventDefault();
      if (!broadcastTitle.trim() || !broadcastBody.trim()) return;
      requestConfirm({
        title: 'Dispatch System Broadcast',
        description: `Send push announcement "${broadcastTitle.trim()}" to all active VYBE sessions via /api/v1/admin/broadcast-push?`,
        confirmLabel: 'Send Broadcast',
        tone: 'primary',
        onConfirm: async () => {
          setIsSendingBroadcast(true);
          try {
            const ok = await onSendBroadcast(broadcastTitle.trim(), broadcastBody.trim());
            if (ok) {
              setBroadcastTitle('');
              setBroadcastBody('');
            }
          } finally {
            setIsSendingBroadcast(false);
          }
        },
      });
    };

    return (
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 animate-fade-in">
        <VybeCard padding="lg" className="lg:col-span-7 space-y-4">
          <div className="flex items-center gap-2.5">
            <Radio className="w-5 h-5 text-[var(--vybe-accent)]" />
            <div>
              <h2 className="text-sm font-bold text-[var(--vybe-text)]">System-Wide Broadcast Announcement</h2>
              <p className="text-xs text-[var(--vybe-text-secondary)]">
                Dispatches a signed server notification through Netlify Functions.
              </p>
            </div>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="text-xs font-semibold text-[var(--vybe-text-secondary)] block mb-1.5">
                Announcement Title
              </label>
              <input
                type="text"
                value={broadcastTitle}
                onChange={(e) => setBroadcastTitle(e.target.value)}
                placeholder="e.g. VYBE Platform Maintenance Notice"
                required
                className="w-full px-3.5 py-2.5 bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] rounded-xl text-xs text-[var(--vybe-text)] placeholder-[var(--vybe-text-muted)] focus:outline-none focus:border-[var(--vybe-accent)]"
              />
            </div>

            <div>
              <label className="text-xs font-semibold text-[var(--vybe-text-secondary)] block mb-1.5">
                Message Content
              </label>
              <textarea
                rows={4}
                value={broadcastBody}
                onChange={(e) => setBroadcastBody(e.target.value)}
                placeholder="Write a clear message for web and Android users..."
                required
                className="w-full p-3.5 bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] rounded-xl text-xs text-[var(--vybe-text)] placeholder-[var(--vybe-text-muted)] focus:outline-none focus:border-[var(--vybe-accent)] resize-none leading-relaxed"
              />
            </div>

            <VybeButton
              type="submit"
              variant="primary"
              size="lg"
              loading={isSendingBroadcast}
              disabled={!broadcastTitle.trim() || !broadcastBody.trim()}
              icon={Send}
            >
              Broadcast to Active Users
            </VybeButton>
          </form>
        </VybeCard>

        <VybeCard padding="md" className="lg:col-span-5 space-y-3 h-fit">
          <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--vybe-text-secondary)]">
            Banner Preview
          </h3>
          <div className="p-4 rounded-2xl bg-[var(--vybe-accent)] text-white keep-white space-y-1 shadow-md">
            <div className="text-xs font-extrabold">
              {broadcastTitle.trim() || 'Announcement Title'}
            </div>
            <p className="text-[11px] opacity-90 leading-relaxed">
              {broadcastBody.trim() || 'Your announcement message preview appears here before dispatch.'}
            </p>
          </div>
          <p className="text-[11px] text-[var(--vybe-text-muted)]">
            Every broadcast is logged in <code className="font-mono">admin_audit_logs</code> under{' '}
            <code className="font-mono">{adminEmail}</code>.
          </p>
        </VybeCard>
      </div>
    );
  }

  if (section === 'diagnostics') {
    return (
      <div className="space-y-6 animate-fade-in">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 className="text-sm font-bold text-[var(--vybe-text)]">
              Supabase Egress & Client Caching Engine
            </h2>
            <p className="text-xs text-[var(--vybe-text-secondary)]">
              Session telemetry tracking cache hits, request deduplication, and egress savings.
            </p>
          </div>
          <VybeButton
            variant="secondary"
            size="sm"
            icon={RotateCcw}
            onClick={() =>
              requestConfirm({
                title: 'Reset Diagnostics Counters',
                description: 'Reset client-side egress and cache hit telemetry counters for this browser session?',
                confirmLabel: 'Reset Counters',
                tone: 'primary',
                onConfirm: onResetDiagnostics,
              })
            }
          >
            Reset Counters
          </VybeButton>
        </div>

        {diagnostics.quotaRestricted && (
          <div className="p-4 rounded-2xl bg-amber-500/12 border border-amber-500/35 text-amber-500 text-xs flex items-start gap-3">
            <AlertTriangle className="w-5 h-5 shrink-0 mt-0.5" />
            <div>
              <h4 className="font-bold">Supabase Quota Notice</h4>
              <p className="mt-1 leading-relaxed opacity-90">
                {diagnostics.quotaRestrictionMessage ||
                  'Quota protection engaged. Client caching and compressed media paths remain active.'}
              </p>
            </div>
          </div>
        )}

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <VybeCard padding="sm">
            <div className="text-[11px] font-semibold text-emerald-500 uppercase tracking-wider">
              1. Cached Storage Egress
            </div>
            <div className="text-xl font-black font-mono text-[var(--vybe-text)] mt-1">
              {(diagnostics.cachedStorageEgressBytes / 1024).toFixed(1)} KB
            </div>
            <div className="text-[10px] text-[var(--vybe-text-muted)] mt-1">Served from client cache</div>
          </VybeCard>

          <VybeCard padding="sm">
            <div className="text-[11px] font-semibold text-amber-500 uppercase tracking-wider">
              2. Uncached Storage Egress
            </div>
            <div className="text-xl font-black font-mono text-[var(--vybe-text)] mt-1">
              {(diagnostics.uncachedStorageEgressBytes / 1024).toFixed(1)} KB
            </div>
            <div className="text-[10px] text-[var(--vybe-text-muted)] mt-1">Fetched from media buckets</div>
          </VybeCard>

          <VybeCard padding="sm">
            <div className="text-[11px] font-semibold text-[var(--vybe-accent-secondary)] uppercase tracking-wider">
              3. Database Egress
            </div>
            <div className="text-xl font-black font-mono text-[var(--vybe-text)] mt-1">
              {(diagnostics.databaseEgressBytes / 1024).toFixed(1)} KB
            </div>
            <div className="text-[10px] text-[var(--vybe-text-muted)] mt-1">Scoped REST column payloads</div>
          </VybeCard>

          <VybeCard padding="sm">
            <div className="text-[11px] font-semibold text-[var(--vybe-accent)] uppercase tracking-wider">
              4. Estimated Client Traffic
            </div>
            <div className="text-xl font-black font-mono text-[var(--vybe-text)] mt-1">
              {(diagnostics.estimatedClientTrafficBytes / 1024).toFixed(1)} KB
            </div>
            <div className="text-[10px] text-[var(--vybe-text-muted)] mt-1">Browser session total</div>
          </VybeCard>
        </div>

        <div className="p-3.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[11px] text-[var(--vybe-text-secondary)] flex items-center gap-2">
          <HelpCircle className="w-4 h-4 text-[var(--vybe-accent)] shrink-0" />
          <span>
            <strong className="text-[var(--vybe-text)]">Measurement Note:</strong> {diagnostics.disclaimer}
          </span>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <VybeCard padding="sm">
            <div className="text-xs text-[var(--vybe-text-secondary)]">Total Network Dispatches</div>
            <div className="text-xl font-black font-mono text-[var(--vybe-text)] mt-1">
              {diagnostics.totalRequests}
            </div>
          </VybeCard>
          <VybeCard padding="sm">
            <div className="text-xs text-[var(--vybe-text-secondary)]">Cache Hits (Requests Saved)</div>
            <div className="text-xl font-black font-mono text-emerald-500 mt-1">{diagnostics.cacheHits}</div>
          </VybeCard>
          <VybeCard padding="sm">
            <div className="text-xs text-[var(--vybe-text-secondary)]">Deduplicated In-Flight Calls</div>
            <div className="text-xl font-black font-mono text-[var(--vybe-accent)] mt-1">
              {diagnostics.deduplicatedRequests}
            </div>
          </VybeCard>
        </div>

        <VybeCard padding="md" className="space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--vybe-text-secondary)]">
              Live Request Stream ({filteredRequests.length})
            </h3>
            <div className="flex items-center gap-1.5">
              {(['ALL', 'CACHE', 'DEDUP', 'REST'] as const).map(f => (
                <button
                  key={f}
                  type="button"
                  onClick={() => setStreamFilter(f)}
                  className={`px-2.5 py-1 rounded-lg text-[11px] font-bold border ${
                    streamFilter === f
                      ? 'bg-[var(--vybe-accent)] text-white keep-white border-transparent'
                      : 'bg-[var(--vybe-elevated)] border-[var(--vybe-border)] text-[var(--vybe-text-secondary)]'
                  }`}
                >
                  {f}
                </button>
              ))}
            </div>
          </div>

          {filteredRequests.length === 0 ? (
            <p className="text-xs text-[var(--vybe-text-muted)] font-mono py-4 text-center">
              No requests recorded for this filter.
            </p>
          ) : (
            <div className="space-y-1.5 max-h-80 overflow-y-auto no-scrollbar">
              {filteredRequests.map((r, i) => (
                <div
                  key={i}
                  className="p-2.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] flex items-center justify-between gap-3 text-xs font-mono"
                >
                  <div className="flex items-center gap-2 min-w-0">
                    <VybeStatusChip
                      tone={r.fromCache ? 'success' : r.deduplicated ? 'accent' : 'info'}
                    >
                      {r.fromCache ? 'CACHE' : r.deduplicated ? 'DEDUP' : 'REST'}
                    </VybeStatusChip>
                    <span className="truncate text-[var(--vybe-text)]">{r.endpoint}</span>
                  </div>
                  <span className="text-[11px] text-[var(--vybe-text-muted)] shrink-0">{r.durationMs}ms</span>
                </div>
              ))}
            </div>
          )}
        </VybeCard>
      </div>
    );
  }

  if (section === 'audit') {
    return (
      <div className="space-y-4 animate-fade-in">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-1.5">
            <button
              type="button"
              onClick={() => setAuditActionFilter('ALL')}
              className={`px-3 py-1.5 rounded-xl text-xs font-semibold border ${
                auditActionFilter === 'ALL'
                  ? 'bg-[var(--vybe-accent)] text-white keep-white border-transparent'
                  : 'bg-[var(--vybe-surface)] border-[var(--vybe-border)] text-[var(--vybe-text-secondary)]'
              }`}
            >
              All Events
            </button>
            {auditActions.map(act => (
              <button
                key={act}
                type="button"
                onClick={() => setAuditActionFilter(act)}
                className={`px-3 py-1.5 rounded-xl text-xs font-mono border ${
                  auditActionFilter === act
                    ? 'bg-[var(--vybe-accent)] text-white keep-white border-transparent'
                    : 'bg-[var(--vybe-surface)] border-[var(--vybe-border)] text-[var(--vybe-text-secondary)]'
                }`}
              >
                {act}
              </button>
            ))}
          </div>

          <VybeButton
            variant="secondary"
            size="sm"
            icon={Download}
            disabled={filteredAuditLogs.length === 0}
            onClick={handleExportAuditCsv}
          >
            Export CSV
          </VybeButton>
        </div>

        {filteredAuditLogs.length === 0 ? (
          <VybeEmptyState
            icon={ScrollText}
            title="No Audit Events Found"
            subtitle="Administrator actions recorded in admin_audit_logs will appear here."
          />
        ) : (
          <VybeCard padding="none" className="divide-y divide-[var(--vybe-border)] overflow-hidden">
            {filteredAuditLogs.map(log => (
              <div key={log.id} className="p-4 flex flex-col sm:flex-row sm:items-start justify-between gap-2 text-xs">
                <div className="space-y-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <VybeStatusChip tone="info">{log.action}</VybeStatusChip>
                    <span className="text-[var(--vybe-text-secondary)] font-mono break-all">
                      Target: {log.target}
                    </span>
                  </div>
                  <p className="text-[var(--vybe-text)] break-words">{log.details}</p>
                  <span className="text-[10px] text-[var(--vybe-text-muted)] block">Actor: {log.admin}</span>
                </div>
                <span className="text-[10px] text-[var(--vybe-text-muted)] font-mono shrink-0">{log.time}</span>
              </div>
            ))}
          </VybeCard>
        )}
      </div>
    );
  }

  // section === 'settings'
  const featureItems: { key: keyof FeatureFlagsState; label: string; desc: string }[] = [
    { key: 'unloadRooms', label: 'UNLOAD Voice & Listening Lounges', desc: 'Zero-record voice vent lounges in Explore.' },
    { key: 'crossedPaths', label: 'Crossed Paths Discovery', desc: 'Mutual nearby encounter cards and connection requests.' },
    { key: 'timeCapsules', label: 'Time Capsules', desc: 'Date-locked future memory publishing.' },
    { key: 'spiritualMode', label: 'Spiritual Mode & Reflections', desc: 'Mindfulness intentions and daily calm reflections.' },
  ];

  return (
    <div className="max-w-2xl space-y-5 animate-fade-in">
      {!configApiAvailable && (
        <div className="p-4 rounded-2xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs text-[var(--vybe-text-secondary)] flex items-start gap-3">
          <Activity className="w-4 h-4 text-amber-500 shrink-0 mt-0.5" />
          <div>
            <strong className="text-[var(--vybe-text)] block">Serverless Runtime Note</strong>
            <span>
              Runtime experience flag endpoints (<code className="font-mono">/api/v1/admin/platform-config</code>) require the stateful Node backend server and are unavailable on static Netlify Functions deployments.
            </span>
          </div>
        </div>
      )}

      <VybeCard padding="md" className="space-y-4">
        <div className="flex items-center justify-between gap-2 pb-2 border-b border-[var(--vybe-border)]">
          <div>
            <h2 className="text-sm font-bold text-[var(--vybe-text)]">Experience Feature Flags</h2>
            <p className="text-xs text-[var(--vybe-text-secondary)]">
              Toggle platform modules across active sessions
            </p>
          </div>
          <VybeStatusChip tone={configApiAvailable ? 'success' : 'warning'}>
            {configApiAvailable ? 'Config API Connected' : 'Config API Unavailable'}
          </VybeStatusChip>
        </div>

        <div className="divide-y divide-[var(--vybe-border)]">
          {featureItems.map(item => {
            const isEnabled = Boolean(featureFlags[item.key]);
            return (
              <div
                key={item.key}
                className="flex items-center justify-between gap-4 py-3.5 first:pt-1 last:pb-1"
              >
                <div>
                  <div className="text-xs font-bold text-[var(--vybe-text)]">{item.label}</div>
                  <div className="text-[11px] text-[var(--vybe-text-secondary)]">{item.desc}</div>
                </div>
                <button
                  type="button"
                  role="switch"
                  aria-checked={isEnabled}
                  aria-label={item.label}
                  onClick={() => onToggleFeatureFlag(item.key, item.label, !isEnabled)}
                  className={`w-11 h-6 rounded-full transition-colors p-1 shrink-0 ${
                    isEnabled ? 'bg-[var(--vybe-accent)]' : 'bg-[var(--vybe-elevated)] border border-[var(--vybe-border)]'
                  }`}
                >
                  <div
                    className={`w-4 h-4 rounded-full bg-white transition-transform shadow-sm ${
                      isEnabled ? 'translate-x-5' : 'translate-x-0'
                    }`}
                  />
                </button>
              </div>
            );
          })}
        </div>
      </VybeCard>

      <VybeCard padding="md" className="space-y-2 text-xs">
        <div className="flex items-center gap-2 font-bold text-[var(--vybe-text)]">
          <ShieldCheck className="w-4 h-4 text-emerald-500" />
          <span>Active Administrator Session</span>
        </div>
        <p className="text-[var(--vybe-text-secondary)]">
          Authenticated via Supabase Auth JWT & <code className="font-mono">admin_users</code> RLS as{' '}
          <strong className="text-[var(--vybe-text)]">{adminEmail}</strong>.
        </p>
      </VybeCard>
    </div>
  );
};
