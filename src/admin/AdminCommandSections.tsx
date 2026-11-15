import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ShieldCheck, UserCog, Users, Sparkles, Music, Trash2, Archive, RotateCcw,
  Plus, Upload, ExternalLink, AlertTriangle, CheckCircle2, Lock
} from 'lucide-react';
import { AdminStaffMember, AudioTrack, User } from '../types';
import {
  deleteAdminAudioTrack,
  fetchAdminAudioTracks,
  fetchAdminCirclesModeration,
  fetchAdminGrowModeration,
  fetchAdminStaff,
  moderateCircleAdmin,
  moderateGrowEntityAdmin,
  setUserPlatformRole,
  upsertAdminAudioTrack
} from '../services/adminExtendedService';
import { uploadMediaBlobToStorage } from '../services/supabaseService';
import { VybeAvatar } from '../components/OptimizedImage';
import {
  VybeButton,
  VybeCard,
  VybeEmptyState,
  VybeSelect,
  VybeStatusChip
} from '../components/VybeDesignSystem';
import { AdminConfirmRequest, AdminSectionId, AdminToastState } from './types';

interface AdminCommandSectionsProps {
  section: Extract<AdminSectionId, 'staff' | 'circles' | 'grow' | 'audio'>;
  searchQuery: string;
  users: User[];
  isOwnerAdmin: boolean;
  adminRole: string;
  onShowToast: (msg: string, tone?: AdminToastState['tone']) => void;
  onRecordAudit: (action: string, target: string, details: string) => void;
  requestConfirm: (req: AdminConfirmRequest) => void;
}

type RoleOption = 'USER' | 'MODERATOR' | 'SUPPORT_ADMIN' | 'ANALYST' | 'ADMIN' | 'SUPER_ADMIN';

export const AdminCommandSections: React.FC<AdminCommandSectionsProps> = ({
  section,
  searchQuery,
  users,
  isOwnerAdmin,
  adminRole,
  onShowToast,
  onRecordAudit,
  requestConfirm,
}) => {
  const [loading, setLoading] = useState(false);

  // Staff Role Management state
  const [staffMembers, setStaffMembers] = useState<AdminStaffMember[]>([]);
  const [selectedUserId, setSelectedUserId] = useState('');
  const [selectedRole, setSelectedRole] = useState<RoleOption>('MODERATOR');
  const [submittingRole, setSubmittingRole] = useState(false);

  // Circles Moderation state
  const [circles, setCircles] = useState<any[]>([]);
  const [circleReports, setCircleReports] = useState<any[]>([]);
  const [circleTab, setCircleTab] = useState<'CIRCLES' | 'REPORTS'>('CIRCLES');

  // GROW 2.0 Moderation state
  const [programs, setPrograms] = useState<any[]>([]);
  const [projects, setProjects] = useState<any[]>([]);
  const [opportunities, setOpportunities] = useState<any[]>([]);
  const [oppReports, setOppReports] = useState<any[]>([]);
  const [growTab, setGrowTab] = useState<'PROGRAMS' | 'PROJECTS' | 'OPPORTUNITIES' | 'REPORTS'>('PROGRAMS');

  // Audio Catalog state
  const [tracks, setTracks] = useState<AudioTrack[]>([]);
  const [audioTitle, setAudioTitle] = useState('');
  const [audioArtist, setAudioArtist] = useState('');
  const [audioAlbum, setAudioAlbum] = useState('');
  const [audioGenre, setAudioGenre] = useState('Ambient');
  const [audioDuration, setAudioDuration] = useState('30');
  const [audioStreamUrl, setAudioStreamUrl] = useState('');
  const [audioCoverUrl, setAudioCoverUrl] = useState('');
  const [uploadingAudio, setUploadingAudio] = useState(false);
  const [savingAudio, setSavingAudio] = useState(false);

  const loadSectionData = useCallback(async () => {
    setLoading(true);
    try {
      if (section === 'staff') {
        const list = await fetchAdminStaff();
        setStaffMembers(list);
      } else if (section === 'circles') {
        const res = await fetchAdminCirclesModeration();
        setCircles(res.circles);
        setCircleReports(res.reports);
      } else if (section === 'grow') {
        const res = await fetchAdminGrowModeration();
        setPrograms(res.programs);
        setProjects(res.projects);
        setOpportunities(res.opportunities);
        setOppReports(res.opportunityReports);
      } else if (section === 'audio') {
        const list = await fetchAdminAudioTracks();
        setTracks(list);
      }
    } finally {
      setLoading(false);
    }
  }, [section]);

  useEffect(() => {
    loadSectionData();
  }, [loadSectionData]);

  const q = searchQuery.trim().toLowerCase();

  const filteredStaff = useMemo(
    () =>
      staffMembers.filter(
        s =>
          !q ||
          s.username.toLowerCase().includes(q) ||
          s.displayName.toLowerCase().includes(q) ||
          s.role.toLowerCase().includes(q)
      ),
    [staffMembers, q]
  );

  const filteredCircles = useMemo(
    () =>
      circles.filter(
        c =>
          !q ||
          String(c.name || '').toLowerCase().includes(q) ||
          String(c.category || '').toLowerCase().includes(q) ||
          String(c.join_mode || '').toLowerCase().includes(q)
      ),
    [circles, q]
  );

  const filteredTracks = useMemo(
    () =>
      tracks.filter(
        t =>
          !q ||
          t.title.toLowerCase().includes(q) ||
          t.artist.toLowerCase().includes(q) ||
          (t.genre || '').toLowerCase().includes(q)
      ),
    [tracks, q]
  );

  // -----------------------
  // 1. STAFF ROLES SECTION
  // -----------------------
  if (section === 'staff') {
    const handleAssignRole = async (e: React.FormEvent) => {
      e.preventDefault();
      if (!isOwnerAdmin) {
        onShowToast('Only OWNER_ADMIN / SUPER_ADMIN can modify staff roles.', 'error');
        return;
      }
      if (!selectedUserId) {
        onShowToast('Select a user first.', 'error');
        return;
      }
      setSubmittingRole(true);
      try {
        const res = await setUserPlatformRole({
          targetUserId: selectedUserId,
          role: selectedRole,
          isActive: selectedRole !== 'USER',
        });
        if (!res.ok) {
          onShowToast(res.error || 'Role update failed.', 'error');
          return;
        }
        onRecordAudit('STAFF_ROLE_SET', selectedUserId, `Assigned role ${selectedRole}`);
        onShowToast(`Updated platform role to ${selectedRole}.`, 'success');
        setSelectedUserId('');
        await loadSectionData();
      } finally {
        setSubmittingRole(false);
      }
    };

    return (
      <div className="space-y-5 animate-fade-in">
        {!isOwnerAdmin && (
          <div className="p-4 rounded-2xl bg-amber-500/12 border border-amber-500/35 text-amber-500 text-xs flex items-start gap-3">
            <Lock className="w-4 h-4 shrink-0 mt-0.5" />
            <div>
              <strong className="block">Owner-Only Privilege Boundary</strong>
              <span>
                You are signed in with role <code className="font-mono">{adminRole}</code>. Viewing staff rosters is allowed, but assigning or revoking platform roles requires verified <code className="font-mono">OWNER_ADMIN</code> or <code className="font-mono">SUPER_ADMIN</code> authority.
              </span>
            </div>
          </div>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">
          <VybeCard padding="md" className="lg:col-span-5 space-y-4 h-fit">
            <div className="flex items-center gap-2">
              <UserCog className="w-4 h-4 text-[var(--vybe-accent)]" />
              <div>
                <h2 className="text-sm font-bold text-[var(--vybe-text)]">Assign Staff Role</h2>
                <p className="text-[11px] text-[var(--vybe-text-secondary)]">
                  Server-verified via <code className="font-mono">/api/v1/admin/set-role</code>
                </p>
              </div>
            </div>

            <form onSubmit={handleAssignRole} className="space-y-3">
              <div>
                <label className="text-xs font-semibold text-[var(--vybe-text-secondary)] block mb-1">
                  Target User
                </label>
                <VybeSelect
                  aria-label="Target user for role assignment"
                  value={selectedUserId}
                  onChange={e => setSelectedUserId(e.target.value)}
                  disabled={!isOwnerAdmin}
                  className="w-full"
                >
                  <option value="">Select registered account...</option>
                  {users.map(u => (
                    <option key={u.id} value={u.id}>
                      {u.displayName} (@{u.username})
                    </option>
                  ))}
                </VybeSelect>
              </div>

              <div>
                <label className="text-xs font-semibold text-[var(--vybe-text-secondary)] block mb-1">
                  Platform Role
                </label>
                <VybeSelect
                  aria-label="Platform role tier"
                  value={selectedRole}
                  onChange={e => setSelectedRole(e.target.value as RoleOption)}
                  disabled={!isOwnerAdmin}
                  className="w-full"
                >
                  <option value="MODERATOR">MODERATOR (Content & Reports)</option>
                  <option value="SUPPORT_ADMIN">SUPPORT_ADMIN (Verifications & Appeals)</option>
                  <option value="ANALYST">ANALYST (Metrics & Read-Only)</option>
                  <option value="ADMIN">ADMIN (Full Operational Moderation)</option>
                  <option value="SUPER_ADMIN">SUPER_ADMIN (Senior Co-Admin)</option>
                  <option value="USER">USER (Revoke Staff Access)</option>
                </VybeSelect>
              </div>

              <VybeButton
                type="submit"
                variant="primary"
                size="md"
                loading={submittingRole}
                disabled={!isOwnerAdmin || !selectedUserId}
                icon={ShieldCheck}
                className="w-full"
              >
                Save Staff Role
              </VybeButton>
            </form>
          </VybeCard>

          <VybeCard padding="md" className="lg:col-span-7 space-y-3">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-bold text-[var(--vybe-text)]">
                Authorized Staff Roster ({filteredStaff.length})
              </h2>
              <VybeButton variant="ghost" size="sm" icon={RotateCcw} onClick={loadSectionData} loading={loading}>
                Refresh
              </VybeButton>
            </div>

            {filteredStaff.length === 0 ? (
              <VybeEmptyState
                icon={Users}
                title="No Staff Records Loaded"
                subtitle="Authorized administrators, moderators, and analysts from admin_users appear here."
              />
            ) : (
              <div className="space-y-2">
                {filteredStaff.map(member => {
                  const isOwnerRow = member.role === 'OWNER_ADMIN';
                  return (
                    <div
                      key={member.userId}
                      className="p-3 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] flex flex-col sm:flex-row sm:items-center justify-between gap-2 text-xs"
                    >
                      <div className="flex items-center gap-2.5 min-w-0">
                        <VybeAvatar
                          src={member.avatarUrl}
                          name={member.displayName}
                          username={member.username}
                          size={36}
                          className="w-9 h-9 border border-[var(--vybe-border)] shrink-0"
                        />
                        <div className="min-w-0">
                          <div className="font-bold text-[var(--vybe-text)] truncate">{member.displayName}</div>
                          <div className="text-[11px] font-mono text-[var(--vybe-text-secondary)] truncate">
                            @{member.username} · {member.userId.slice(0, 8)}...
                          </div>
                        </div>
                      </div>

                      <div className="flex items-center gap-2 shrink-0">
                        <VybeStatusChip tone={isOwnerRow ? 'accent' : member.isActive ? 'success' : 'neutral'}>
                          {member.role}
                        </VybeStatusChip>
                        {isOwnerAdmin && !isOwnerRow && member.isActive && (
                          <VybeButton
                            variant="danger"
                            size="sm"
                            onClick={() =>
                              requestConfirm({
                                title: `Revoke Staff Role for @${member.username}`,
                                description: `Downgrade @${member.username} from ${member.role} to standard USER?`,
                                confirmLabel: 'Revoke Role',
                                tone: 'danger',
                                onConfirm: async () => {
                                  const res = await setUserPlatformRole({
                                    targetUserId: member.userId,
                                    role: 'USER',
                                    isActive: false,
                                  });
                                  if (!res.ok) {
                                    onShowToast(res.error || 'Failed revoking role.', 'error');
                                    return;
                                  }
                                  onRecordAudit('STAFF_ROLE_REVOKED', member.userId, `Revoked ${member.role}`);
                                  onShowToast(`Revoked staff access for @${member.username}.`, 'success');
                                  await loadSectionData();
                                },
                              })
                            }
                          >
                            Revoke
                          </VybeButton>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </VybeCard>
        </div>
      </div>
    );
  }

  // -----------------------
  // 2. CIRCLES MODERATION SECTION
  // -----------------------
  if (section === 'circles') {
    const handleModerateCircle = async (circleId: string, name: string, action: 'ARCHIVE' | 'UNARCHIVE' | 'DELETE') => {
      const res = await moderateCircleAdmin({ circleId, action });
      if (!res.ok) {
        onShowToast(res.error || 'Circle moderation failed.', 'error');
        return;
      }
      onRecordAudit('CIRCLE_MODERATION', circleId, `${action} Circle "${name}"`);
      onShowToast(`${action} executed on "${name}".`, 'success');
      await loadSectionData();
    };

    return (
      <div className="space-y-4 animate-fade-in">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex gap-1.5">
            <button
              type="button"
              onClick={() => setCircleTab('CIRCLES')}
              className={`px-3 py-1.5 rounded-xl text-xs font-semibold border ${
                circleTab === 'CIRCLES'
                  ? 'bg-[var(--vybe-accent)] text-white keep-white border-transparent'
                  : 'bg-[var(--vybe-surface)] border-[var(--vybe-border)] text-[var(--vybe-text-secondary)]'
              }`}
            >
              All Circles ({filteredCircles.length})
            </button>
            <button
              type="button"
              onClick={() => setCircleTab('REPORTS')}
              className={`px-3 py-1.5 rounded-xl text-xs font-semibold border ${
                circleTab === 'REPORTS'
                  ? 'bg-[var(--vybe-accent)] text-white keep-white border-transparent'
                  : 'bg-[var(--vybe-surface)] border-[var(--vybe-border)] text-[var(--vybe-text-secondary)]'
              }`}
            >
              Circle Reports ({circleReports.length})
            </button>
          </div>
          <VybeButton variant="ghost" size="sm" icon={RotateCcw} onClick={loadSectionData} loading={loading}>
            Refresh
          </VybeButton>
        </div>

        {circleTab === 'CIRCLES' ? (
          filteredCircles.length === 0 ? (
            <VybeEmptyState
              icon={Users}
              title="No Growth Circles Found"
              subtitle="Created Growth Circles will appear here for privacy, archive, and safety oversight."
            />
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {filteredCircles.map(c => (
                <VybeCard key={c.id} padding="md" className="space-y-3">
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <h3 className="text-sm font-bold text-[var(--vybe-text)] truncate">{c.name}</h3>
                      <div className="text-[11px] text-[var(--vybe-text-secondary)]">
                        {c.category || 'General'} · {c.join_mode || 'PUBLIC'} · {c.members_count ?? 0} members
                      </div>
                    </div>
                    <VybeStatusChip tone={c.is_archived ? 'warning' : 'success'}>
                      {c.is_archived ? 'Archived' : 'Active'}
                    </VybeStatusChip>
                  </div>

                  {c.description && (
                    <p className="text-xs text-[var(--vybe-text-secondary)] line-clamp-2">{c.description}</p>
                  )}

                  <div className="flex flex-wrap gap-2 pt-2 border-t border-[var(--vybe-border)]">
                    <VybeButton
                      variant="secondary"
                      size="sm"
                      icon={Archive}
                      onClick={() =>
                        requestConfirm({
                          title: c.is_archived ? `Unarchive "${c.name}"` : `Archive "${c.name}"`,
                          description: `${c.is_archived ? 'Restore visibility for' : 'Hide from discovery for'} Circle "${c.name}"?`,
                          confirmLabel: c.is_archived ? 'Unarchive' : 'Archive',
                          tone: 'primary',
                          onConfirm: () => handleModerateCircle(c.id, c.name, c.is_archived ? 'UNARCHIVE' : 'ARCHIVE'),
                        })
                      }
                    >
                      {c.is_archived ? 'Unarchive' : 'Archive'}
                    </VybeButton>
                    <VybeButton
                      variant="danger"
                      size="sm"
                      icon={Trash2}
                      onClick={() =>
                        requestConfirm({
                          title: `Delete "${c.name}" Permanently`,
                          description: `Permanently delete Growth Circle "${c.name}" and all memberships? This cannot be undone.`,
                          confirmLabel: 'Delete Circle',
                          tone: 'danger',
                          onConfirm: () => handleModerateCircle(c.id, c.name, 'DELETE'),
                        })
                      }
                    >
                      Delete
                    </VybeButton>
                  </div>
                </VybeCard>
              ))}
            </div>
          )
        ) : circleReports.length === 0 ? (
          <VybeEmptyState
            icon={CheckCircle2}
            title="No Circle Reports"
            subtitle="Open reports submitted inside Growth Circles will appear here."
          />
        ) : (
          <div className="space-y-3">
            {circleReports.map(rep => (
              <VybeCard key={rep.id} padding="md" className="space-y-2 text-xs">
                <div className="flex items-center justify-between gap-2">
                  <VybeStatusChip tone="danger">CIRCLE REPORT · {rep.status || 'OPEN'}</VybeStatusChip>
                  <span className="font-mono text-[10px] text-[var(--vybe-text-muted)]">
                    Circle: {rep.community_id}
                  </span>
                </div>
                <p className="text-[var(--vybe-text)]">{rep.reason}</p>
              </VybeCard>
            ))}
          </div>
        )}
      </div>
    );
  }

  // -----------------------
  // 3. VYBE GROW 2.0 MODERATION SECTION
  // -----------------------
  if (section === 'grow') {
    const handleGrowMod = async (
      entityType: 'PROGRAM' | 'PROJECT' | 'OPPORTUNITY' | 'OPPORTUNITY_REPORT',
      entityId: string,
      action: string,
      label: string
    ) => {
      const res = await moderateGrowEntityAdmin({ entityType, entityId, action });
      if (!res.ok) {
        onShowToast(res.error || 'GROW moderation failed.', 'error');
        return;
      }
      onRecordAudit('GROW_MODERATION', `${entityType}:${entityId}`, `${action} on ${label}`);
      onShowToast(`Applied ${action} to ${label}.`, 'success');
      await loadSectionData();
    };

    return (
      <div className="space-y-4 animate-fade-in">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex flex-wrap gap-1.5">
            {(
              [
                { id: 'PROGRAMS', label: `Programs (${programs.length})` },
                { id: 'PROJECTS', label: `Projects (${projects.length})` },
                { id: 'OPPORTUNITIES', label: `Opportunities (${opportunities.length})` },
                { id: 'REPORTS', label: `Opp Reports (${oppReports.length})` },
              ] as const
            ).map(t => (
              <button
                key={t.id}
                type="button"
                onClick={() => setGrowTab(t.id)}
                className={`px-3 py-1.5 rounded-xl text-xs font-semibold border ${
                  growTab === t.id
                    ? 'bg-[var(--vybe-accent)] text-white keep-white border-transparent'
                    : 'bg-[var(--vybe-surface)] border-[var(--vybe-border)] text-[var(--vybe-text-secondary)]'
                }`}
              >
                {t.label}
              </button>
            ))}
          </div>
          <VybeButton variant="ghost" size="sm" icon={RotateCcw} onClick={loadSectionData} loading={loading}>
            Refresh
          </VybeButton>
        </div>

        {growTab === 'PROGRAMS' && (
          programs.length === 0 ? (
            <VybeEmptyState icon={Sparkles} title="No Growth Programs" subtitle="Published Growth Programs will appear here." />
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {programs.map(p => (
                <VybeCard key={p.id} padding="md" className="space-y-2 text-xs">
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-bold text-sm text-[var(--vybe-text)] truncate">{p.title}</span>
                    <VybeStatusChip tone={p.status === 'PUBLISHED' ? 'success' : 'warning'}>
                      {p.status || 'PUBLISHED'}
                    </VybeStatusChip>
                  </div>
                  <p className="text-[var(--vybe-text-secondary)] line-clamp-2">{p.description}</p>
                  <div className="flex gap-2 pt-2 border-t border-[var(--vybe-border)]">
                    <VybeButton
                      variant="secondary"
                      size="sm"
                      icon={Archive}
                      onClick={() => handleGrowMod('PROGRAM', p.id, 'ARCHIVE', p.title)}
                    >
                      Archive
                    </VybeButton>
                    <VybeButton
                      variant="danger"
                      size="sm"
                      icon={Trash2}
                      onClick={() =>
                        requestConfirm({
                          title: `Delete Program "${p.title}"`,
                          description: 'Remove this Growth Program permanently?',
                          confirmLabel: 'Delete',
                          tone: 'danger',
                          onConfirm: () => handleGrowMod('PROGRAM', p.id, 'DELETE', p.title),
                        })
                      }
                    >
                      Delete
                    </VybeButton>
                  </div>
                </VybeCard>
              ))}
            </div>
          )
        )}

        {growTab === 'PROJECTS' && (
          projects.length === 0 ? (
            <VybeEmptyState icon={Sparkles} title="No Showcase Projects" subtitle="Published builder projects will appear here." />
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {projects.map(pr => (
                <VybeCard key={pr.id} padding="md" className="space-y-2 text-xs">
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-bold text-sm text-[var(--vybe-text)] truncate">{pr.title}</span>
                    <VybeStatusChip tone="info">{pr.stage || 'LAUNCHED'}</VybeStatusChip>
                  </div>
                  <p className="text-[var(--vybe-text-secondary)] line-clamp-2">{pr.summary}</p>
                  <div className="flex gap-2 pt-2 border-t border-[var(--vybe-border)]">
                    <VybeButton
                      variant="danger"
                      size="sm"
                      icon={Trash2}
                      onClick={() =>
                        requestConfirm({
                          title: `Delete Project "${pr.title}"`,
                          description: 'Remove this showcased project from VYBE GROW?',
                          confirmLabel: 'Delete',
                          tone: 'danger',
                          onConfirm: () => handleGrowMod('PROJECT', pr.id, 'DELETE', pr.title),
                        })
                      }
                    >
                      Delete Project
                    </VybeButton>
                  </div>
                </VybeCard>
              ))}
            </div>
          )
        )}

        {growTab === 'OPPORTUNITIES' && (
          opportunities.length === 0 ? (
            <VybeEmptyState icon={Sparkles} title="No Opportunities Listed" subtitle="Active VYBE GROW opportunities will appear here." />
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {opportunities.map(o => (
                <VybeCard key={o.id} padding="md" className="space-y-2 text-xs">
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-bold text-sm text-[var(--vybe-text)] truncate">{o.title}</span>
                    <VybeStatusChip tone={o.is_closed ? 'warning' : 'success'}>
                      {o.is_closed ? 'Closed' : o.category || 'JOB'}
                    </VybeStatusChip>
                  </div>
                  <p className="text-[var(--vybe-text-secondary)]">
                    {o.organization} · {o.location || 'Remote'}
                  </p>
                  <div className="flex gap-2 pt-2 border-t border-[var(--vybe-border)]">
                    <VybeButton
                      variant="secondary"
                      size="sm"
                      onClick={() => handleGrowMod('OPPORTUNITY', o.id, 'CLOSE', o.title)}
                    >
                      Close Listing
                    </VybeButton>
                    <VybeButton
                      variant="danger"
                      size="sm"
                      icon={Trash2}
                      onClick={() =>
                        requestConfirm({
                          title: `Delete Opportunity "${o.title}"`,
                          description: 'Permanently remove this opportunity listing?',
                          confirmLabel: 'Delete',
                          tone: 'danger',
                          onConfirm: () => handleGrowMod('OPPORTUNITY', o.id, 'DELETE', o.title),
                        })
                      }
                    >
                      Delete
                    </VybeButton>
                  </div>
                </VybeCard>
              ))}
            </div>
          )
        )}

        {growTab === 'REPORTS' && (
          oppReports.length === 0 ? (
            <VybeEmptyState icon={AlertTriangle} title="No Opportunity Reports" subtitle="Reported opportunities from VYBE GROW appear here." />
          ) : (
            <div className="space-y-3">
              {oppReports.map(r => (
                <VybeCard key={r.id} padding="md" className="space-y-2 text-xs">
                  <div className="flex items-center justify-between gap-2">
                    <VybeStatusChip tone="danger">{r.status || 'OPEN'}</VybeStatusChip>
                    <span className="font-mono text-[10px] text-[var(--vybe-text-muted)]">
                      Opp ID: {r.opportunity_id}
                    </span>
                  </div>
                  <p className="text-[var(--vybe-text)]">{r.reason}</p>
                  <div className="flex gap-2 pt-2 border-t border-[var(--vybe-border)]">
                    <VybeButton
                      variant="secondary"
                      size="sm"
                      onClick={() => handleGrowMod('OPPORTUNITY_REPORT', r.id, 'DISMISS', r.id)}
                    >
                      Dismiss
                    </VybeButton>
                    <VybeButton
                      variant="danger"
                      size="sm"
                      onClick={() => handleGrowMod('OPPORTUNITY_REPORT', r.id, 'RESOLVE', r.id)}
                    >
                      Mark Resolved
                    </VybeButton>
                  </div>
                </VybeCard>
              ))}
            </div>
          )
        )}
      </div>
    );
  }

  // -----------------------
  // 4. AUDIO CATALOG SECTION
  // -----------------------
  const handleUploadAudioFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploadingAudio(true);
    try {
      const uploaded = await uploadMediaBlobToStorage(file, 'circle-resources', file.name, file.size);
      if (!uploaded?.publicUrl) {
        onShowToast('Audio upload failed. Ensure active admin JWT session.', 'error');
        return;
      }
      setAudioStreamUrl(uploaded.publicUrl);
      if (!audioTitle.trim()) {
        setAudioTitle(file.name.replace(/\.[^/.]+$/, ''));
      }
      onShowToast('Audio uploaded to Supabase Storage.', 'success');
    } finally {
      setUploadingAudio(false);
    }
  };

  const handleSaveTrack = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!audioTitle.trim() || !audioArtist.trim() || !audioStreamUrl.trim()) {
      onShowToast('Title, artist, and stream URL are required.', 'error');
      return;
    }
    setSavingAudio(true);
    try {
      const res = await upsertAdminAudioTrack({
        title: audioTitle,
        artist: audioArtist,
        album: audioAlbum,
        genre: audioGenre,
        durationSec: Number(audioDuration) || 30,
        streamUrl: audioStreamUrl,
        coverUrl: audioCoverUrl || undefined,
      });
      if (!res.ok) {
        onShowToast(res.error || 'Failed adding audio track.', 'error');
        return;
      }
      onRecordAudit('AUDIO_TRACK_UPSERT', audioTitle.trim(), `Added audio track by ${audioArtist.trim()}`);
      onShowToast('Audio track added to live Stories catalog.', 'success');
      setAudioTitle('');
      setAudioArtist('');
      setAudioAlbum('');
      setAudioStreamUrl('');
      setAudioCoverUrl('');
      await loadSectionData();
    } finally {
      setSavingAudio(false);
    }
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-12 gap-5 animate-fade-in">
      <VybeCard padding="md" className="lg:col-span-5 space-y-4 h-fit">
        <div className="flex items-center gap-2">
          <Music className="w-4 h-4 text-[var(--vybe-accent)]" />
          <div>
            <h2 className="text-sm font-bold text-[var(--vybe-text)]">Add Live Audio Track</h2>
            <p className="text-[11px] text-[var(--vybe-text-secondary)]">
              Powers Stories 2.0 audio picker across Web & Android
            </p>
          </div>
        </div>

        <form onSubmit={handleSaveTrack} className="space-y-3 text-xs">
          <div>
            <label className="font-semibold text-[var(--vybe-text-secondary)] block mb-1">Track Title *</label>
            <input
              type="text"
              required
              value={audioTitle}
              onChange={e => setAudioTitle(e.target.value)}
              placeholder="e.g. Morning Clarity"
              className="w-full px-3 py-2 bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] rounded-xl text-[var(--vybe-text)]"
            />
          </div>
          <div className="grid grid-cols-2 gap-2">
            <div>
              <label className="font-semibold text-[var(--vybe-text-secondary)] block mb-1">Artist *</label>
              <input
                type="text"
                required
                value={audioArtist}
                onChange={e => setAudioArtist(e.target.value)}
                placeholder="Artist name"
                className="w-full px-3 py-2 bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] rounded-xl text-[var(--vybe-text)]"
              />
            </div>
            <div>
              <label className="font-semibold text-[var(--vybe-text-secondary)] block mb-1">Genre</label>
              <input
                type="text"
                value={audioGenre}
                onChange={e => setAudioGenre(e.target.value)}
                placeholder="Ambient"
                className="w-full px-3 py-2 bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] rounded-xl text-[var(--vybe-text)]"
              />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-2">
            <div>
              <label className="font-semibold text-[var(--vybe-text-secondary)] block mb-1">Album</label>
              <input
                type="text"
                value={audioAlbum}
                onChange={e => setAudioAlbum(e.target.value)}
                placeholder="Optional"
                className="w-full px-3 py-2 bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] rounded-xl text-[var(--vybe-text)]"
              />
            </div>
            <div>
              <label className="font-semibold text-[var(--vybe-text-secondary)] block mb-1">Duration (sec)</label>
              <input
                type="number"
                min={5}
                max={600}
                value={audioDuration}
                onChange={e => setAudioDuration(e.target.value)}
                className="w-full px-3 py-2 bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] rounded-xl text-[var(--vybe-text)]"
              />
            </div>
          </div>
          <div>
            <div className="flex items-center justify-between mb-1">
              <label className="font-semibold text-[var(--vybe-text-secondary)]">Audio Stream URL *</label>
              <label className="inline-flex items-center gap-1 text-[11px] font-semibold text-[var(--vybe-accent)] cursor-pointer hover:underline">
                <Upload className="w-3 h-3" />
                <span>{uploadingAudio ? 'Uploading...' : 'Upload Audio File'}</span>
                <input type="file" accept="audio/*" className="hidden" onChange={handleUploadAudioFile} />
              </label>
            </div>
            <input
              type="url"
              required
              value={audioStreamUrl}
              onChange={e => setAudioStreamUrl(e.target.value)}
              placeholder="https://.../track.mp3"
              className="w-full px-3 py-2 bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] rounded-xl text-[var(--vybe-text)]"
            />
          </div>
          <div>
            <label className="font-semibold text-[var(--vybe-text-secondary)] block mb-1">Cover URL</label>
            <input
              type="url"
              value={audioCoverUrl}
              onChange={e => setAudioCoverUrl(e.target.value)}
              placeholder="https://.../cover.jpg (optional)"
              className="w-full px-3 py-2 bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] rounded-xl text-[var(--vybe-text)]"
            />
          </div>

          <VybeButton type="submit" variant="primary" size="md" loading={savingAudio} icon={Plus} className="w-full">
            Publish Audio Track
          </VybeButton>
        </form>
      </VybeCard>

      <VybeCard padding="md" className="lg:col-span-7 space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-bold text-[var(--vybe-text)]">
            Live Stories Audio Catalog ({filteredTracks.length})
          </h2>
          <VybeButton variant="ghost" size="sm" icon={RotateCcw} onClick={loadSectionData} loading={loading}>
            Refresh
          </VybeButton>
        </div>

        {filteredTracks.length === 0 ? (
          <VybeEmptyState
            icon={Music}
            title="Audio Catalog Empty"
            subtitle="Publish verified audio tracks here to populate Stories 2.0 on Web & Android."
          />
        ) : (
          <div className="space-y-2.5">
            {filteredTracks.map(track => (
              <div
                key={track.id}
                className="p-3 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] flex flex-col sm:flex-row sm:items-center justify-between gap-2 text-xs"
              >
                <div className="min-w-0 space-y-0.5">
                  <div className="font-bold text-[var(--vybe-text)] truncate">{track.title}</div>
                  <div className="text-[11px] text-[var(--vybe-text-secondary)]">
                    {track.artist} · {track.genre || 'General'} · {track.durationSec}s
                  </div>
                </div>
                <div className="flex items-center gap-2 shrink-0">
                  {track.streamUrl && (
                    <a
                      href={track.streamUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="p-1.5 rounded-lg bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)]"
                      title="Open Audio Stream"
                    >
                      <ExternalLink className="w-3.5 h-3.5" />
                    </a>
                  )}
                  <VybeButton
                    variant="danger"
                    size="sm"
                    icon={Trash2}
                    onClick={() =>
                      requestConfirm({
                        title: `Remove "${track.title}"`,
                        description: `Delete audio track "${track.title}" from the live catalog?`,
                        confirmLabel: 'Delete Track',
                        tone: 'danger',
                        onConfirm: async () => {
                          const ok = await deleteAdminAudioTrack(track.id);
                          if (!ok) {
                            onShowToast('Failed deleting audio track.', 'error');
                            return;
                          }
                          onRecordAudit('AUDIO_TRACK_DELETE', track.id, `Removed track "${track.title}"`);
                          onShowToast('Audio track removed.', 'success');
                          await loadSectionData();
                        },
                      })
                    }
                  >
                    Remove
                  </VybeButton>
                </div>
              </div>
            ))}
          </div>
        )}
      </VybeCard>
    </div>
  );
};
