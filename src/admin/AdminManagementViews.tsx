import React, { useMemo, useState, useEffect } from 'react';
import {
  Users, Award, FileText, AlertTriangle, Search, CheckCircle2, Check, X,
  Trash2, Archive, Eye, Ban, ShieldAlert, RotateCcw, ChevronLeft, ChevronRight,
  MapPin, Calendar
} from 'lucide-react';
import { OfficialVybeBadge, Post, User } from '../types';
import { LiveReportItem } from '../services/supabaseService';
import { OfficialBadge } from '../components/Badges';
import {
  VybeButton, VybeCard, VybeEmptyState, VybeModal, VybeSelect, VybeStatusChip
} from '../components/VybeDesignSystem';
import { AdminConfirmRequest, AdminSectionId, VerificationApp } from './types';

interface AdminManagementViewsProps {
  section: Extract<AdminSectionId, 'users' | 'verifications' | 'content' | 'reports'>;
  searchQuery: string;
  onSearchChange: (q: string) => void;
  users: User[];
  posts: Post[];
  verificationQueue: VerificationApp[];
  reports: LiveReportItem[];
  onToggleBadge: (userId: string, badge: OfficialVybeBadge) => Promise<void> | void;
  onModerateUser: (user: User, action: 'SUSPEND' | 'BAN' | 'RESTORE') => Promise<void> | void;
  onInspectUser: (user: User) => void;
  onApproveVerification: (app: VerificationApp) => Promise<void> | void;
  onRejectVerification: (app: VerificationApp) => Promise<void> | void;
  onModeratePost: (postId: string, action: 'ARCHIVE' | 'DELETE') => Promise<void> | void;
  onDismissReport: (rep: LiveReportItem) => Promise<void> | void;
  onResolveReportRemovePost: (rep: LiveReportItem) => Promise<void> | void;
  onResolveReportSuspendUser: (rep: LiveReportItem) => Promise<void> | void;
  requestConfirm: (req: AdminConfirmRequest) => void;
}

export const AdminManagementViews: React.FC<AdminManagementViewsProps> = ({
  section,
  searchQuery,
  onSearchChange,
  users,
  posts,
  verificationQueue,
  reports,
  onToggleBadge,
  onModerateUser,
  onInspectUser,
  onApproveVerification,
  onRejectVerification,
  onModeratePost,
  onDismissReport,
  onResolveReportRemovePost,
  onResolveReportSuspendUser,
  requestConfirm,
}) => {
  // Users Directory controls
  const [userFilter, setUserFilter] = useState<'ALL' | 'VERIFIED' | 'VIP' | 'WORKER' | 'ONLINE'>('ALL');
  const [userSort, setUserSort] = useState<'NAME' | 'FOLLOWERS' | 'POSTS'>('NAME');
  const [userPage, setUserPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [inspectedUser, setInspectedUser] = useState<User | null>(null);

  // Verification / Content / Reports filters
  const [verifBadgeFilter, setVerifBadgeFilter] = useState<string>('ALL');
  const [contentFilter, setContentFilter] = useState<'ALL' | 'IMAGE' | 'VIDEO' | 'REEL' | 'TEXT'>('ALL');
  const [reportFilter, setReportFilter] = useState<'ALL' | 'POST' | 'USER'>('ALL');

  const filteredUsers = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    const list = users.filter(u => {
      if (userFilter === 'VERIFIED' && !u.isVerified && !u.badges.includes('VERIFIED_BLUE')) return false;
      if (userFilter === 'VIP' && !u.isVip && !u.badges.includes('VIP_GOLD')) return false;
      if (userFilter === 'WORKER' && !u.isWorker && !u.badges.includes('WORKER_GREEN')) return false;
      if (userFilter === 'ONLINE' && !u.isOnline) return false;
      if (!q) return true;
      return (
        u.username.toLowerCase().includes(q) ||
        u.displayName.toLowerCase().includes(q) ||
        u.publicVybeId.toLowerCase().includes(q)
      );
    });
    return [...list].sort((a, b) => {
      if (userSort === 'FOLLOWERS') return b.followersCount - a.followersCount;
      if (userSort === 'POSTS') return b.postsCount - a.postsCount;
      return a.displayName.localeCompare(b.displayName);
    });
  }, [users, searchQuery, userFilter, userSort]);

  const totalUserPages = Math.max(1, Math.ceil(filteredUsers.length / pageSize));
  useEffect(() => {
    setUserPage(1);
  }, [searchQuery, userFilter, userSort, pageSize]);

  const pagedUsers = useMemo(() => {
    const start = (userPage - 1) * pageSize;
    return filteredUsers.slice(start, start + pageSize);
  }, [filteredUsers, userPage, pageSize]);

  const filteredVerifications = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    return verificationQueue.filter(app => {
      if (verifBadgeFilter !== 'ALL' && app.badge !== verifBadgeFilter) return false;
      if (!q) return true;
      return (
        app.username.toLowerCase().includes(q) ||
        app.displayName.toLowerCase().includes(q) ||
        app.category.toLowerCase().includes(q) ||
        app.proof.toLowerCase().includes(q)
      );
    });
  }, [verificationQueue, searchQuery, verifBadgeFilter]);

  const filteredPosts = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    return posts.filter(p => {
      if (contentFilter !== 'ALL' && p.mediaType !== contentFilter) return false;
      if (!q) return true;
      return (
        p.caption.toLowerCase().includes(q) ||
        p.author.username.toLowerCase().includes(q) ||
        p.author.displayName.toLowerCase().includes(q)
      );
    });
  }, [posts, searchQuery, contentFilter]);

  const filteredReports = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    return reports.filter(rep => {
      if (reportFilter !== 'ALL' && rep.targetType !== reportFilter) return false;
      if (!q) return true;
      return (
        rep.reason.toLowerCase().includes(q) ||
        rep.reporterUsername.toLowerCase().includes(q) ||
        rep.targetId.toLowerCase().includes(q)
      );
    });
  }, [reports, searchQuery, reportFilter]);

  // Keep inspected user fresh after badge toggles
  const activeInspectedUser = useMemo(() => {
    if (!inspectedUser) return null;
    return users.find(u => u.id === inspectedUser.id) || inspectedUser;
  }, [inspectedUser, users]);

  const badgeLabels: Record<OfficialVybeBadge, string> = {
    VERIFIED_BLUE: 'Verified Blue',
    VIP_GOLD: 'VIP Gold',
    WORKER_GREEN: 'Staff Green',
    SHOP_GOLD: 'Shop Gold',
  };

  if (section === 'users') {
    return (
      <div className="space-y-4 animate-fade-in">
        {/* Toolbar */}
        <VybeCard padding="sm" className="flex flex-col lg:flex-row lg:items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-1.5">
            {(['ALL', 'VERIFIED', 'VIP', 'WORKER', 'ONLINE'] as const).map(f => (
              <button
                key={f}
                type="button"
                onClick={() => setUserFilter(f)}
                className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-colors border ${
                  userFilter === f
                    ? 'bg-[var(--vybe-accent)] text-white keep-white border-transparent'
                    : 'bg-[var(--vybe-elevated)] border-[var(--vybe-border)] text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)]'
                }`}
              >
                {f === 'ALL' ? 'All Profiles' : f === 'ONLINE' ? 'Online' : f}
              </button>
            ))}
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <div className="relative md:hidden flex-1 min-w-[180px]">
              <Search className="w-3.5 h-3.5 text-[var(--vybe-text-muted)] absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => onSearchChange(e.target.value)}
                placeholder="Filter users..."
                className="w-full pl-8 pr-3 py-2 bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] rounded-xl text-xs text-[var(--vybe-text)] focus:outline-none focus:border-[var(--vybe-accent)]"
              />
            </div>

            <VybeSelect
              aria-label="Sort users"
              value={userSort}
              onChange={(e) => setUserSort(e.target.value as any)}
            >
              <option value="NAME">Sort: Name (A–Z)</option>
              <option value="FOLLOWERS">Sort: Followers</option>
              <option value="POSTS">Sort: Posts</option>
            </VybeSelect>

            <VybeSelect
              aria-label="Rows per page"
              value={String(pageSize)}
              onChange={(e) => setPageSize(Number(e.target.value))}
            >
              <option value="10">10 / page</option>
              <option value="20">20 / page</option>
              <option value="50">50 / page</option>
            </VybeSelect>
          </div>
        </VybeCard>

        {/* Table (Desktop) */}
        <VybeCard padding="none" className="overflow-hidden hidden md:block">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-[var(--vybe-elevated)] text-[var(--vybe-text-secondary)] font-semibold uppercase tracking-wider text-[10px] border-b border-[var(--vybe-border)]">
                <tr>
                  <th className="py-3 px-4">Profile</th>
                  <th className="py-3 px-4">VYBE ID</th>
                  <th className="py-3 px-4">Presence & Pulse</th>
                  <th className="py-3 px-4">Reach</th>
                  <th className="py-3 px-4">Official Badges</th>
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--vybe-border)]">
                {pagedUsers.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="py-10 px-4 text-center text-[var(--vybe-text-secondary)]">
                      No profiles match the current filters.
                    </td>
                  </tr>
                ) : (
                  pagedUsers.map(u => (
                    <tr
                      key={u.id}
                      className="hover:bg-[var(--vybe-elevated)]/60 transition-colors"
                    >
                      <td className="py-3 px-4">
                        <div className="flex items-center gap-3">
                          <img
                            src={u.avatarUrl}
                            alt={u.displayName}
                            className="w-9 h-9 rounded-full object-cover border border-[var(--vybe-border)] shrink-0"
                          />
                          <div className="min-w-0">
                            <div className="flex items-center gap-1 font-bold text-[var(--vybe-text)]">
                              <span className="truncate max-w-[140px]">{u.displayName}</span>
                              {u.badges?.map(b => (
                                <OfficialBadge key={b} badge={b} size="sm" />
                              ))}
                            </div>
                            <div className="text-[11px] text-[var(--vybe-text-secondary)] font-mono">
                              @{u.username}
                            </div>
                          </div>
                        </div>
                      </td>
                      <td className="py-3 px-4 font-mono text-[var(--vybe-accent)]">{u.publicVybeId}</td>
                      <td className="py-3 px-4">
                        <div className="text-[var(--vybe-text)] truncate max-w-[160px]">{u.livePulse}</div>
                        <VybeStatusChip tone={u.isOnline ? 'success' : 'neutral'} className="mt-1">
                          {u.isOnline ? 'Online' : 'Offline'}
                        </VybeStatusChip>
                      </td>
                      <td className="py-3 px-4 text-[var(--vybe-text-secondary)]">
                        <div>{u.followersCount.toLocaleString()} followers</div>
                        <div className="text-[10px] text-[var(--vybe-text-muted)]">{u.postsCount} posts</div>
                      </td>
                      <td className="py-3 px-4">
                        <div className="flex flex-wrap gap-1">
                          {(['VERIFIED_BLUE', 'VIP_GOLD', 'WORKER_GREEN', 'SHOP_GOLD'] as OfficialVybeBadge[]).map(b => {
                            const has = u.badges.includes(b);
                            return (
                              <button
                                key={b}
                                type="button"
                                onClick={() =>
                                  requestConfirm({
                                    title: `${has ? 'Revoke' : 'Grant'} ${badgeLabels[b]}`,
                                    description: `Are you sure you want to ${has ? 'revoke' : 'grant'} the ${badgeLabels[b]} badge for @${u.username}? This invokes the audited admin_assign_badge RPC.`,
                                    confirmLabel: has ? 'Revoke Badge' : 'Grant Badge',
                                    tone: has ? 'danger' : 'primary',
                                    onConfirm: () => onToggleBadge(u.id, b),
                                  })
                                }
                                className={`px-2 py-0.5 rounded-md text-[10px] font-mono font-bold transition-colors border ${
                                  has
                                    ? 'bg-[var(--vybe-accent-soft)] text-[var(--vybe-accent)] border-[var(--vybe-accent)]/40'
                                    : 'bg-[var(--vybe-elevated)] text-[var(--vybe-text-muted)] border-[var(--vybe-border)] hover:text-[var(--vybe-text)]'
                                }`}
                              >
                                {b.replace('_', ' ')}
                              </button>
                            );
                          })}
                        </div>
                      </td>
                      <td className="py-3 px-4 text-right">
                        <VybeButton
                          variant="secondary"
                          size="sm"
                          icon={Eye}
                          onClick={() => {
                            setInspectedUser(u);
                            onInspectUser(u);
                          }}
                        >
                          Inspect
                        </VybeButton>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </VybeCard>

        {/* Mobile Cards */}
        <div className="space-y-3 md:hidden">
          {pagedUsers.length === 0 ? (
            <VybeEmptyState
              icon={Users}
              title="No matching users"
              subtitle="Adjust search or role filter to view registered profiles."
            />
          ) : (
            pagedUsers.map(u => (
              <VybeCard key={`m_${u.id}`} padding="sm" className="space-y-3">
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2.5 min-w-0">
                    <img
                      src={u.avatarUrl}
                      alt={u.displayName}
                      className="w-10 h-10 rounded-full object-cover border border-[var(--vybe-border)]"
                    />
                    <div className="min-w-0">
                      <div className="flex items-center gap-1 font-bold text-xs text-[var(--vybe-text)]">
                        <span className="truncate">{u.displayName}</span>
                        {u.badges?.map(b => <OfficialBadge key={b} badge={b} size="sm" />)}
                      </div>
                      <span className="text-[11px] text-[var(--vybe-text-secondary)] font-mono">
                        @{u.username} · {u.publicVybeId}
                      </span>
                    </div>
                  </div>
                  <VybeButton
                    variant="secondary"
                    size="sm"
                    onClick={() => {
                      setInspectedUser(u);
                      onInspectUser(u);
                    }}
                  >
                    Inspect
                  </VybeButton>
                </div>
              </VybeCard>
            ))
          )}
        </div>

        {/* Pagination Footer */}
        <div className="flex items-center justify-between text-xs text-[var(--vybe-text-secondary)] px-1">
          <span>
            Showing {pagedUsers.length} of {filteredUsers.length} matching profiles ({users.length} total loaded)
          </span>
          <div className="flex items-center gap-2">
            <VybeButton
              variant="secondary"
              size="sm"
              disabled={userPage <= 1}
              onClick={() => setUserPage(p => Math.max(1, p - 1))}
              icon={ChevronLeft}
            >
              Prev
            </VybeButton>
            <span className="font-mono text-[11px] px-2">
              Page {userPage} / {totalUserPages}
            </span>
            <VybeButton
              variant="secondary"
              size="sm"
              disabled={userPage >= totalUserPages}
              onClick={() => setUserPage(p => Math.min(totalUserPages, p + 1))}
            >
              Next
              <ChevronRight className="w-3.5 h-3.5" />
            </VybeButton>
          </div>
        </div>

        {/* Profile Details Inspector Modal */}
        <VybeModal
          open={Boolean(activeInspectedUser)}
          onClose={() => setInspectedUser(null)}
          title={activeInspectedUser ? `${activeInspectedUser.displayName}` : 'Profile Details'}
          subtitle={activeInspectedUser ? `@${activeInspectedUser.username} · ${activeInspectedUser.publicVybeId}` : ''}
          maxWidthClass="max-w-lg"
        >
          {activeInspectedUser && (
            <div className="space-y-4 text-xs">
              <div className="flex items-center gap-3.5">
                <img
                  src={activeInspectedUser.avatarUrl}
                  alt={activeInspectedUser.displayName}
                  className="w-16 h-16 rounded-2xl object-cover border border-[var(--vybe-border)]"
                />
                <div className="space-y-1 min-w-0">
                  <div className="flex items-center gap-1.5 flex-wrap">
                    {activeInspectedUser.badges.length > 0 ? (
                      activeInspectedUser.badges.map(b => <OfficialBadge key={b} badge={b} size="md" />)
                    ) : (
                      <VybeStatusChip>Standard Member</VybeStatusChip>
                    )}
                    <VybeStatusChip tone={activeInspectedUser.isOnline ? 'success' : 'neutral'}>
                      {activeInspectedUser.isOnline ? 'Online' : 'Offline'}
                    </VybeStatusChip>
                  </div>
                  <p className="text-[var(--vybe-text-secondary)] font-mono text-[11px] break-all">
                    UUID: {activeInspectedUser.id}
                  </p>
                </div>
              </div>

              {activeInspectedUser.bio && (
                <div className="p-3 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[var(--vybe-text-secondary)]">
                  {activeInspectedUser.bio}
                </div>
              )}

              <div className="grid grid-cols-3 gap-2 text-center">
                <div className="p-2.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)]">
                  <div className="font-bold text-sm text-[var(--vybe-text)]">
                    {activeInspectedUser.followersCount.toLocaleString()}
                  </div>
                  <div className="text-[10px] text-[var(--vybe-text-secondary)]">Followers</div>
                </div>
                <div className="p-2.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)]">
                  <div className="font-bold text-sm text-[var(--vybe-text)]">
                    {activeInspectedUser.followingCount.toLocaleString()}
                  </div>
                  <div className="text-[10px] text-[var(--vybe-text-secondary)]">Following</div>
                </div>
                <div className="p-2.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)]">
                  <div className="font-bold text-sm text-[var(--vybe-text)]">{activeInspectedUser.postsCount}</div>
                  <div className="text-[10px] text-[var(--vybe-text-secondary)]">Posts</div>
                </div>
              </div>

              <div className="flex flex-wrap gap-3 text-[11px] text-[var(--vybe-text-secondary)]">
                {activeInspectedUser.location && (
                  <span className="inline-flex items-center gap-1">
                    <MapPin className="w-3.5 h-3.5 text-[var(--vybe-accent)]" />
                    {activeInspectedUser.location}
                  </span>
                )}
                <span className="inline-flex items-center gap-1">
                  <Calendar className="w-3.5 h-3.5" />
                  Joined {activeInspectedUser.joinedDate}
                </span>
              </div>

              <div className="pt-3 border-t border-[var(--vybe-border)] space-y-2">
                <span className="block text-[11px] font-bold uppercase tracking-wider text-[var(--vybe-text-muted)]">
                  Account Safety Enforcement
                </span>
                <div className="flex flex-wrap gap-2">
                  <VybeButton
                    variant="secondary"
                    size="sm"
                    icon={ShieldAlert}
                    onClick={() =>
                      requestConfirm({
                        title: `Suspend @${activeInspectedUser.username} (72h)`,
                        description: `Suspend this account for 72 hours? All actions are recorded in Supabase user_moderation_actions and admin_audit_logs.`,
                        confirmLabel: 'Suspend 72h',
                        tone: 'danger',
                        onConfirm: () => onModerateUser(activeInspectedUser, 'SUSPEND'),
                      })
                    }
                  >
                    Suspend 72h
                  </VybeButton>
                  <VybeButton
                    variant="danger"
                    size="sm"
                    icon={Ban}
                    onClick={() =>
                      requestConfirm({
                        title: `Ban @${activeInspectedUser.username}`,
                        description: `Permanently restrict access for @${activeInspectedUser.username}? You can restore access later if needed.`,
                        confirmLabel: 'Ban Account',
                        tone: 'danger',
                        onConfirm: () => onModerateUser(activeInspectedUser, 'BAN'),
                      })
                    }
                  >
                    Ban Account
                  </VybeButton>
                  <VybeButton
                    variant="success"
                    size="sm"
                    icon={RotateCcw}
                    onClick={() =>
                      requestConfirm({
                        title: `Restore @${activeInspectedUser.username}`,
                        description: `Clear any active ban or suspension on @${activeInspectedUser.username}?`,
                        confirmLabel: 'Restore Access',
                        tone: 'primary',
                        onConfirm: () => onModerateUser(activeInspectedUser, 'RESTORE'),
                      })
                    }
                  >
                    Restore Access
                  </VybeButton>
                </div>
              </div>
            </div>
          )}
        </VybeModal>
      </div>
    );
  }

  if (section === 'verifications') {
    return (
      <div className="space-y-4 animate-fade-in">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap gap-1.5">
            {['ALL', 'VERIFIED_BLUE', 'VIP_GOLD', 'SHOP_GOLD', 'WORKER_GREEN'].map(b => (
              <button
                key={b}
                type="button"
                onClick={() => setVerifBadgeFilter(b)}
                className={`px-3 py-1.5 rounded-xl text-xs font-semibold border transition-colors ${
                  verifBadgeFilter === b
                    ? 'bg-[var(--vybe-accent)] text-white keep-white border-transparent'
                    : 'bg-[var(--vybe-surface)] border-[var(--vybe-border)] text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)]'
                }`}
              >
                {b === 'ALL' ? 'All Applications' : b.replace('_', ' ')}
              </button>
            ))}
          </div>
          <span className="text-xs text-[var(--vybe-text-secondary)]">
            {filteredVerifications.length} pending request{filteredVerifications.length === 1 ? '' : 's'}
          </span>
        </div>

        {filteredVerifications.length === 0 ? (
          <VybeEmptyState
            icon={CheckCircle2}
            title="Verification Queue Clear"
            subtitle="All pending creator, VIP, and business storefront badge applications have been reviewed."
          />
        ) : (
          <div className="space-y-3">
            {filteredVerifications.map(app => (
              <VybeCard key={app.id} padding="md" className="space-y-3">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                  <div className="space-y-1">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="font-bold text-[var(--vybe-text)] text-sm">
                        {app.displayName} (@{app.username})
                      </span>
                      <VybeStatusChip tone="accent">Requesting {app.badge}</VybeStatusChip>
                    </div>
                    <span className="text-xs text-[var(--vybe-text-secondary)] block">
                      {app.category} · Submitted {app.submittedAt}
                    </span>
                  </div>

                  <div className="flex items-center gap-2 shrink-0 flex-wrap">
                    <VybeButton
                      variant="success"
                      size="sm"
                      icon={Check}
                      onClick={() =>
                        requestConfirm({
                          title: `Approve @${app.username}`,
                          description: `Approve this request and grant the ${app.badge} badge to @${app.username}?`,
                          confirmLabel: 'Approve & Grant',
                          tone: 'primary',
                          onConfirm: () => onApproveVerification(app),
                        })
                      }
                    >
                      Approve
                    </VybeButton>
                    <VybeButton
                      variant="danger"
                      size="sm"
                      icon={X}
                      onClick={() =>
                        requestConfirm({
                          title: `Decline @${app.username}`,
                          description: `Decline the verification application from @${app.username}?`,
                          confirmLabel: 'Decline Request',
                          tone: 'danger',
                          onConfirm: () => onRejectVerification(app),
                        })
                      }
                    >
                      Decline
                    </VybeButton>
                  </div>
                </div>

                <div className="p-3 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs text-[var(--vybe-text-secondary)]">
                  <span className="text-[var(--vybe-text-muted)] block text-[10px] uppercase font-mono mb-1">
                    Provided Proof / Portfolio
                  </span>
                  <span className="break-words text-[var(--vybe-text)]">{app.proof}</span>
                </div>
              </VybeCard>
            ))}
          </div>
        )}
      </div>
    );
  }

  if (section === 'content') {
    return (
      <div className="space-y-4 animate-fade-in">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap gap-1.5">
            {(['ALL', 'IMAGE', 'VIDEO', 'REEL', 'TEXT'] as const).map(t => (
              <button
                key={t}
                type="button"
                onClick={() => setContentFilter(t)}
                className={`px-3 py-1.5 rounded-xl text-xs font-semibold border transition-colors ${
                  contentFilter === t
                    ? 'bg-[var(--vybe-accent)] text-white keep-white border-transparent'
                    : 'bg-[var(--vybe-surface)] border-[var(--vybe-border)] text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)]'
                }`}
              >
                {t === 'ALL' ? 'All Media' : t}
              </button>
            ))}
          </div>
          <span className="text-xs text-[var(--vybe-text-secondary)]">
            Showing {filteredPosts.length} of {posts.length} posts
          </span>
        </div>

        {filteredPosts.length === 0 ? (
          <VybeEmptyState
            icon={FileText}
            title="No Matching Content"
            subtitle="No published posts match the active search or media type filter."
          />
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {filteredPosts.map(post => (
              <VybeCard key={post.id} padding="sm" className="flex flex-col justify-between gap-3">
                <div className="space-y-3">
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-2.5 min-w-0">
                      <img
                        src={post.author.avatarUrl}
                        alt={post.author.displayName}
                        className="w-8 h-8 rounded-full object-cover border border-[var(--vybe-border)] shrink-0"
                      />
                      <div className="min-w-0">
                        <div className="font-bold text-[var(--vybe-text)] text-xs truncate">
                          @{post.author.username}
                        </div>
                        <div className="text-[10px] text-[var(--vybe-text-muted)]">
                          {post.timestamp} · {post.mediaType}
                        </div>
                      </div>
                    </div>

                    <div className="flex items-center gap-1.5 shrink-0">
                      <button
                        type="button"
                        onClick={() =>
                          requestConfirm({
                            title: 'Archive Post',
                            description: `Archive this post by @${post.author.username}? It will be hidden from public feeds without permanently deleting the record.`,
                            confirmLabel: 'Archive Post',
                            tone: 'primary',
                            onConfirm: () => onModeratePost(post.id, 'ARCHIVE'),
                          })
                        }
                        title="Archive Post"
                        aria-label="Archive Post"
                        className="p-1.5 rounded-lg bg-amber-500/12 hover:bg-amber-500/20 text-amber-500 transition-colors"
                      >
                        <Archive className="w-4 h-4" />
                      </button>
                      <button
                        type="button"
                        onClick={() =>
                          requestConfirm({
                            title: 'Delete Post Permanently',
                            description: `Permanently delete this post by @${post.author.username}? This action cannot be undone.`,
                            confirmLabel: 'Delete Post',
                            tone: 'danger',
                            onConfirm: () => onModeratePost(post.id, 'DELETE'),
                          })
                        }
                        title="Delete Post"
                        aria-label="Delete Post"
                        className="p-1.5 rounded-lg bg-red-500/12 hover:bg-red-500/20 text-red-500 transition-colors"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </div>

                  <p className="text-xs text-[var(--vybe-text-secondary)] line-clamp-3 leading-relaxed">
                    {post.caption || '(Media-only post)'}
                  </p>

                  {post.mediaUrls && post.mediaUrls.length > 0 && (
                    <div className="rounded-xl overflow-hidden max-h-48 border border-[var(--vybe-border)] bg-[var(--vybe-elevated)]">
                      {post.mediaType === 'VIDEO' || post.mediaType === 'REEL' ? (
                        <video
                          src={post.mediaUrls[0]}
                          controls
                          preload="none"
                          className="w-full max-h-48 object-cover"
                        />
                      ) : (
                        <img
                          src={post.mediaUrls[0]}
                          alt="Post media"
                          loading="lazy"
                          className="w-full max-h-48 object-cover"
                        />
                      )}
                    </div>
                  )}
                </div>

                <div className="flex items-center justify-between text-[11px] text-[var(--vybe-text-muted)] pt-2 border-t border-[var(--vybe-border)]">
                  <span>Zone: {post.moodZone || 'GENERAL'}</span>
                  <span>
                    {post.likesCount} Loves · {post.commentsCount} Comments
                  </span>
                </div>
              </VybeCard>
            ))}
          </div>
        )}
      </div>
    );
  }

  // section === 'reports'
  return (
    <div className="space-y-4 animate-fade-in">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap gap-1.5">
          {(['ALL', 'POST', 'USER'] as const).map(f => (
            <button
              key={f}
              type="button"
              onClick={() => setReportFilter(f)}
              className={`px-3 py-1.5 rounded-xl text-xs font-semibold border transition-colors ${
                reportFilter === f
                  ? 'bg-[var(--vybe-accent)] text-white keep-white border-transparent'
                  : 'bg-[var(--vybe-surface)] border-[var(--vybe-border)] text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)]'
              }`}
            >
              {f === 'ALL' ? 'All Targets' : `${f} Reports`}
            </button>
          ))}
        </div>
        <span className="text-xs text-[var(--vybe-text-secondary)]">
          {filteredReports.length} open report{filteredReports.length === 1 ? '' : 's'}
        </span>
      </div>

      {filteredReports.length === 0 ? (
        <VybeEmptyState
          icon={CheckCircle2}
          title="Safety Queue Clear"
          subtitle="No open or investigating safety reports found in Supabase."
        />
      ) : (
        <div className="space-y-3">
          {filteredReports.map(rep => (
            <VybeCard key={rep.id} padding="md" className="space-y-3">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                <div className="flex items-center gap-2 flex-wrap">
                  <VybeStatusChip tone="danger">{rep.targetType} VIOLATION</VybeStatusChip>
                  <span className="text-xs font-bold text-[var(--vybe-text)] font-mono">
                    Target: {rep.targetId}
                  </span>
                </div>
                <span className="text-[11px] text-[var(--vybe-text-muted)] font-mono">
                  Reported by {rep.reporterUsername} · {rep.createdAt}
                </span>
              </div>

              <p className="text-xs text-[var(--vybe-text-secondary)]">
                <strong className="text-[var(--vybe-text)]">Reason:</strong> {rep.reason}
              </p>

              <div className="flex flex-wrap gap-2 pt-1">
                <VybeButton
                  variant="secondary"
                  size="sm"
                  onClick={() =>
                    requestConfirm({
                      title: 'Dismiss Report',
                      description: `Mark report ${rep.id} as dismissed?`,
                      confirmLabel: 'Dismiss Report',
                      tone: 'primary',
                      onConfirm: () => onDismissReport(rep),
                    })
                  }
                >
                  Dismiss Report
                </VybeButton>

                {rep.targetType === 'POST' && (
                  <VybeButton
                    variant="danger"
                    size="sm"
                    icon={Trash2}
                    onClick={() =>
                      requestConfirm({
                        title: 'Remove Reported Post',
                        description: `Delete post ${rep.targetId} and mark this safety report as resolved?`,
                        confirmLabel: 'Remove Post',
                        tone: 'danger',
                        onConfirm: () => onResolveReportRemovePost(rep),
                      })
                    }
                  >
                    Remove Reported Post
                  </VybeButton>
                )}

                {rep.targetType === 'USER' && (
                  <VybeButton
                    variant="danger"
                    size="sm"
                    icon={Ban}
                    onClick={() =>
                      requestConfirm({
                        title: 'Suspend Account (72h)',
                        description: `Suspend user ${rep.targetId} for 72 hours due to "${rep.reason}" and mark report resolved?`,
                        confirmLabel: 'Suspend Account',
                        tone: 'danger',
                        onConfirm: () => onResolveReportSuspendUser(rep),
                      })
                    }
                  >
                    Suspend Violator (72h)
                  </VybeButton>
                )}
              </div>
            </VybeCard>
          ))}
        </div>
      )}
    </div>
  );
};
