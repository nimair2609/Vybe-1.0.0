import React, { useState, useEffect, useCallback } from 'react';
import {
  SUPABASE_URL,
  SUPABASE_ANON_KEY,
  fetchLiveProfiles,
  fetchLivePosts,
  adminSignInWithPassword,
  getStoredAdminSession,
  adminSignOut,
  fetchLiveVerificationRequests,
  reviewVerificationRequestInSupabase,
  fetchLiveReports,
  updateReportStatusInSupabase,
  moderatePostInSupabase,
  suspendUserInSupabase,
  recordAdminAuditLogToSupabase,
  fetchLiveAuditLogs,
  LiveReportItem
} from '../services/supabaseService';
import { vybeDiagnostics, DiagnosticsSummary } from '../utils/performanceDiagnostics';
import { OfficialVybeBadge, Post, User } from '../types';
import { VybeConfirmDialog, VybeToast } from '../components/VybeDesignSystem';
import { AdminLoginScreen } from './AdminLoginScreen';
import { AdminSidebar } from './AdminSidebar';
import { AdminTopBar } from './AdminTopBar';
import { AdminManagementViews } from './AdminManagementViews';
import { AdminSystemViews } from './AdminSystemViews';
import {
  AdminConfirmRequest,
  AdminSectionId,
  AdminThemeMode,
  AdminToastState,
  AuditRecord,
  VerificationApp
} from './types';

export const AdminApp: React.FC = () => {
  // Authentication State
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(() =>
    Boolean(getStoredAdminSession().token)
  );
  const [adminEmail, setAdminEmail] = useState<string>(
    () => getStoredAdminSession().email || ''
  );

  // Theme State (synced to <html data-mode="admin" data-theme="...">)
  const [themeMode, setThemeMode] = useState<AdminThemeMode>(() => {
    try {
      const saved = localStorage.getItem('vybe_admin_theme_mode');
      return saved === 'light' ? 'light' : 'dark';
    } catch {
      return 'dark';
    }
  });

  useEffect(() => {
    document.documentElement.setAttribute('data-mode', 'admin');
    document.documentElement.setAttribute('data-theme', themeMode);
    document.documentElement.classList.toggle('dark', themeMode === 'dark');
    try {
      localStorage.setItem('vybe_admin_theme_mode', themeMode);
    } catch {}
  }, [themeMode]);

  const toggleTheme = () => setThemeMode(prev => (prev === 'dark' ? 'light' : 'dark'));

  // Navigation & UI Chrome State
  const [activeSection, setActiveSection] = useState<AdminSectionId>('overview');
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [mobileDrawerOpen, setMobileDrawerOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  // Live Platform State
  const [users, setUsers] = useState<User[]>([]);
  const [posts, setPosts] = useState<Post[]>([]);
  const [verificationQueue, setVerificationQueue] = useState<VerificationApp[]>([]);
  const [reports, setReports] = useState<LiveReportItem[]>([]);
  const [auditLogs, setAuditLogs] = useState<AuditRecord[]>([]);
  const [isLoadingData, setIsLoadingData] = useState(false);
  const [diagnostics, setDiagnostics] = useState<DiagnosticsSummary>(vybeDiagnostics.getSummary());
  const [apiHealthOk, setApiHealthOk] = useState<boolean | null>(null);
  const [configApiAvailable, setConfigApiAvailable] = useState<boolean>(false);

  const [featureFlags, setFeatureFlags] = useState({
    unloadRooms: true,
    crossedPaths: true,
    timeCapsules: true,
    spiritualMode: true,
    maintenanceMode: false,
  });

  // Feedback Toast & Confirm Dialog State
  const [toast, setToast] = useState<AdminToastState | null>(null);
  const [confirmState, setConfirmState] = useState<AdminConfirmRequest | null>(null);
  const [confirmLoading, setConfirmLoading] = useState(false);

  const showToast = useCallback((message: string, tone: AdminToastState['tone'] = 'info') => {
    setToast({ message, tone });
  }, []);

  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 4000);
    return () => clearTimeout(t);
  }, [toast]);

  // Diagnostics polling
  useEffect(() => {
    const timer = setInterval(() => {
      setDiagnostics(vybeDiagnostics.getSummary());
    }, 3000);
    return () => clearInterval(timer);
  }, []);

  const recordAudit = useCallback(
    (action: string, target: string, details: string) => {
      const activeEmail = localStorage.getItem('vybe_admin_email') || adminEmail || 'admin';
      const newLog: AuditRecord = {
        id: `log_${Date.now()}`,
        action,
        admin: activeEmail,
        target,
        details,
        time: 'Just now',
      };
      setAuditLogs(prev => [newLog, ...prev]);
      recordAdminAuditLogToSupabase({
        action,
        targetType: 'ADMIN_PORTAL',
        targetId: target,
        details: { summary: details },
      }).catch(() => {});
    },
    [adminEmail]
  );

  const loadPlatformData = useCallback(async (forceRefresh: boolean = false) => {
    setIsLoadingData(true);
    try {
      const token = localStorage.getItem('vybe_admin_jwt') || '';
      const [liveUsers, livePosts, liveVerifs, liveReports, liveAudits, healthOk, configRes] =
        await Promise.all([
          fetchLiveProfiles({ forceRefresh }),
          fetchLivePosts(undefined, { limit: 50, forceRefresh }),
          fetchLiveVerificationRequests(),
          fetchLiveReports(),
          fetchLiveAuditLogs(),
          fetch('/api/v1/health')
            .then(r => r.ok)
            .catch(() => false),
          token
            ? fetch('/api/v1/admin/platform-config', {
                headers: { Authorization: `Bearer ${token}` },
              })
                .then(r => (r.ok ? r.json() : null))
                .catch(() => null)
            : Promise.resolve(null),
        ]);

      setUsers(liveUsers || []);
      setPosts(livePosts || []);
      setVerificationQueue(
        (liveVerifs || [])
          .filter(v => v.status === 'PENDING' || v.status === 'MORE_INFO_REQUIRED')
          .map(v => ({
            id: v.id,
            userId: v.userId,
            username: v.username,
            displayName: v.displayName,
            avatarUrl: v.avatarUrl,
            badge: v.badge,
            category: v.category,
            proof: v.proof,
            submittedAt: v.submittedAt,
          }))
      );
      setReports((liveReports || []).filter(r => r.status === 'OPEN' || r.status === 'INVESTIGATING'));
      setAuditLogs(
        (liveAudits || []).map(a => ({
          id: a.id,
          action: a.action,
          admin: a.adminEmail,
          target: `${a.targetType}:${a.targetId}`,
          details: a.details,
          time: a.createdAt,
        }))
      );
      setApiHealthOk(Boolean(healthOk));
      setConfigApiAvailable(Boolean(configRes?.featureFlags));
      if (configRes?.featureFlags) {
        const ff = configRes.featureFlags;
        setFeatureFlags(prev => ({
          ...prev,
          unloadRooms: ff.unloadRooms ?? ff.unload ?? prev.unloadRooms,
          crossedPaths: ff.crossedPaths ?? ff.crossed_paths ?? prev.crossedPaths,
          timeCapsules: ff.timeCapsules ?? ff.time_capsule ?? prev.timeCapsules,
          spiritualMode: ff.spiritualMode ?? prev.spiritualMode,
          maintenanceMode: configRes.config?.maintenanceMode ?? prev.maintenanceMode,
        }));
      }
      setDiagnostics(vybeDiagnostics.getSummary());
    } catch (err) {
      console.warn('Admin data load notice:', err);
      showToast('Some data streams failed to refresh.', 'error');
    } finally {
      setIsLoadingData(false);
    }
  }, [showToast]);

  useEffect(() => {
    if (!isAuthenticated) return;
    loadPlatformData();
  }, [isAuthenticated, loadPlatformData]);

  const handleSignIn = async (email: string, password: string) => {
    const res = await adminSignInWithPassword(email, password);
    if (res.success) {
      setAdminEmail(email);
      setIsAuthenticated(true);
      recordAudit('ADMIN_LOGIN', email, 'Supabase administrator session authorized');
      showToast('Signed in to VYBE Command Center', 'success');
    }
    return res;
  };

  const handleLogout = () => {
    adminSignOut();
    setIsAuthenticated(false);
    showToast('Signed out of Admin Command Center', 'info');
  };

  const executeConfirm = async () => {
    if (!confirmState) return;
    setConfirmLoading(true);
    try {
      await confirmState.onConfirm();
      setConfirmState(null);
    } finally {
      setConfirmLoading(false);
    }
  };

  const handleToggleBadge = async (userId: string, badge: OfficialVybeBadge) => {
    const token = localStorage.getItem('vybe_admin_jwt') || '';
    const adminUserId = localStorage.getItem('vybe_admin_user_id') || '';
    if (!token || !token.startsWith('eyJ')) {
      showToast('Administrator session expired. Please sign in again.', 'error');
      return;
    }
    if (adminUserId && adminUserId.toLowerCase() === userId.toLowerCase()) {
      showToast('Self-assignment or revocation of badges is prohibited.', 'error');
      return;
    }

    const targetUser = users.find(u => u.id === userId);
    if (!targetUser) return;
    const hasBadge = targetUser.badges.includes(badge);
    const nextGrant = !hasBadge;

    const categoryMap: Record<OfficialVybeBadge, string> = {
      VERIFIED_BLUE: 'VERIFIED',
      VIP_GOLD: 'VIP',
      WORKER_GREEN: 'WORKER',
      SHOP_GOLD: 'SHOP',
    };

    try {
      const resp = await fetch(`${SUPABASE_URL}/rest/v1/rpc/admin_assign_badge`, {
        method: 'POST',
        headers: {
          apikey: SUPABASE_ANON_KEY,
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          p_target_user_id: userId,
          p_badge_category: categoryMap[badge],
          p_is_grant: nextGrant,
          p_admin_id: adminUserId || null,
          p_vip_expires_at: null,
          p_reason: 'Admin portal badge update',
          p_require_acceptance: false,
          p_worker_mandatory: badge === 'WORKER_GREEN',
        }),
      });

      if (!resp.ok) {
        const errPayload = await resp.json().catch(() => ({}));
        showToast(errPayload.message || errPayload.error || `Badge update rejected (${resp.status}).`, 'error');
        return;
      }

      setUsers(prev =>
        prev.map(u => {
          if (u.id !== userId) return u;
          const nextBadges = nextGrant
            ? Array.from(new Set([...u.badges, badge]))
            : u.badges.filter(b => b !== badge);
          return {
            ...u,
            badges: nextBadges,
            isVerified: badge === 'VERIFIED_BLUE' ? nextGrant : u.isVerified,
            isVip: badge === 'VIP_GOLD' ? nextGrant : u.isVip,
            isWorker: badge === 'WORKER_GREEN' ? nextGrant : u.isWorker,
            isVerifiedBusiness: badge === 'SHOP_GOLD' ? nextGrant : u.isVerifiedBusiness,
          };
        })
      );

      recordAudit('BADGE_TOGGLE', userId, `${nextGrant ? 'Granted' : 'Revoked'} badge ${badge}`);
      showToast(`${nextGrant ? 'Granted' : 'Revoked'} ${badge} for @${targetUser.username}.`, 'success');
    } catch (err: any) {
      showToast(err?.message || 'Network error updating badge.', 'error');
    }
  };

  const handleModerateUser = async (user: User, action: 'SUSPEND' | 'BAN' | 'RESTORE') => {
    const res = await suspendUserInSupabase({
      userId: user.id,
      action,
      reason: `Moderation action (${action}) via Admin Command Center`,
      durationHours: action === 'SUSPEND' ? 72 : undefined,
    });
    if (!res.ok) {
      showToast(res.error || 'User moderation failed.', 'error');
      return;
    }
    showToast(`Executed ${action} on @${user.username}.`, 'success');
    loadPlatformData(true);
  };

  const handleApproveVerification = async (app: VerificationApp) => {
    const res = await reviewVerificationRequestInSupabase({
      requestId: app.id,
      decision: 'APPROVED',
      notes: `Approved ${app.badge} via VYBE Admin Command Center`,
    });
    if (!res.ok) {
      showToast(res.error || 'Failed approving verification request.', 'error');
      return;
    }
    setVerificationQueue(prev => prev.filter(a => a.id !== app.id));
    showToast(`Approved ${app.badge} for @${app.username}.`, 'success');
    loadPlatformData(true);
  };

  const handleRejectVerification = async (app: VerificationApp) => {
    const res = await reviewVerificationRequestInSupabase({
      requestId: app.id,
      decision: 'REJECTED',
      notes: 'Verification criteria not met',
    });
    if (!res.ok) {
      showToast(res.error || 'Failed declining verification request.', 'error');
      return;
    }
    setVerificationQueue(prev => prev.filter(a => a.id !== app.id));
    showToast(`Declined verification for @${app.username}.`, 'info');
    loadPlatformData(true);
  };

  const handleModeratePost = async (postId: string, action: 'ARCHIVE' | 'DELETE') => {
    const res = await moderatePostInSupabase({ postId, action });
    if (!res.ok) {
      showToast(res.error || `Failed to ${action.toLowerCase()} post.`, 'error');
      return;
    }
    setPosts(prev => prev.filter(p => p.id !== postId));
    showToast(action === 'ARCHIVE' ? 'Post archived.' : 'Post permanently deleted.', 'success');
  };

  const handleDismissReport = async (rep: LiveReportItem) => {
    const res = await updateReportStatusInSupabase({
      reportId: rep.id,
      status: 'DISMISSED',
      adminNotes: 'Dismissed via Command Center',
    });
    if (!res.ok) {
      showToast(res.error || 'Failed dismissing report.', 'error');
      return;
    }
    setReports(prev => prev.filter(r => r.id !== rep.id));
    showToast('Report dismissed.', 'success');
  };

  const handleResolveReportRemovePost = async (rep: LiveReportItem) => {
    const modRes = await moderatePostInSupabase({ postId: rep.targetId, action: 'DELETE' });
    if (!modRes.ok) {
      showToast(modRes.error || 'Failed removing reported post.', 'error');
      return;
    }
    const repRes = await updateReportStatusInSupabase({
      reportId: rep.id,
      status: 'RESOLVED',
      adminNotes: 'Post removed via moderation',
    });
    if (repRes.ok) {
      setReports(prev => prev.filter(r => r.id !== rep.id));
    }
    setPosts(prev => prev.filter(p => p.id !== rep.targetId));
    showToast('Reported post removed and report resolved.', 'success');
  };

  const handleResolveReportSuspendUser = async (rep: LiveReportItem) => {
    const susRes = await suspendUserInSupabase({
      userId: rep.targetId,
      action: 'SUSPEND',
      reason: rep.reason,
      durationHours: 72,
    });
    if (!susRes.ok) {
      showToast(susRes.error || 'Failed suspending account.', 'error');
      return;
    }
    const repRes = await updateReportStatusInSupabase({
      reportId: rep.id,
      status: 'RESOLVED',
      adminNotes: 'Account suspended 72h',
    });
    if (repRes.ok) {
      setReports(prev => prev.filter(r => r.id !== rep.id));
    }
    showToast(`Account ${rep.targetId} suspended for 72h.`, 'success');
  };

  const handleSendBroadcast = async (title: string, message: string): Promise<boolean> => {
    const token = localStorage.getItem('vybe_admin_jwt') || '';
    if (!token || !token.startsWith('eyJ')) {
      showToast('Administrator session expired. Please sign in again.', 'error');
      return false;
    }
    try {
      const resp = await fetch('/api/v1/admin/broadcast-push', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ title, message }),
      });
      const payload = await resp.json().catch(() => ({}));
      if (!resp.ok) {
        showToast(payload?.error || `Broadcast rejected (${resp.status}).`, 'error');
        return false;
      }
      recordAudit('BROADCAST_SENT', 'ALL_USERS', `Title: "${title}"`);
      showToast('System announcement dispatched.', 'success');
      return true;
    } catch (err: any) {
      showToast(err?.message || 'Failed dispatching system broadcast.', 'error');
      return false;
    }
  };

  const handleToggleFeatureFlag = async (
    key: keyof typeof featureFlags,
    label: string,
    nextEnabled: boolean
  ) => {
    const token = localStorage.getItem('vybe_admin_jwt') || '';
    if (!token || !token.startsWith('eyJ')) {
      showToast('Administrator session expired. Please sign in again.', 'error');
      return;
    }
    try {
      const resp = await fetch('/api/v1/admin/experiences/toggle', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ experienceKey: key, enabled: nextEnabled }),
      });
      if (!resp.ok) {
        const err = await resp.json().catch(() => ({}));
        showToast(
          err?.error ||
            (resp.status === 404
              ? 'Config toggle endpoint is unavailable on static Netlify Functions deploy.'
              : `Feature toggle rejected (${resp.status}).`),
          'error'
        );
        return;
      }
      setFeatureFlags(prev => ({ ...prev, [key]: nextEnabled }));
      recordAudit('FLAG_TOGGLE', key, `Toggled ${label} to ${nextEnabled}`);
      showToast(`Updated ${label}.`, 'success');
    } catch (err: any) {
      showToast(err?.message || 'Network error toggling feature flag.', 'error');
    }
  };

  if (!isAuthenticated) {
    return (
      <AdminLoginScreen
        initialEmail={adminEmail}
        themeMode={themeMode}
        onToggleTheme={toggleTheme}
        onSignIn={handleSignIn}
      />
    );
  }

  const pendingQueueCount = verificationQueue.length + reports.length;
  const isManagementSection =
    activeSection === 'users' ||
    activeSection === 'verifications' ||
    activeSection === 'content' ||
    activeSection === 'reports';

  return (
    <div className="vybe-app-shell min-h-screen flex">
      <AdminSidebar
        activeSection={activeSection}
        onSelectSection={setActiveSection}
        collapsed={sidebarCollapsed}
        mobileOpen={mobileDrawerOpen}
        onCloseMobile={() => setMobileDrawerOpen(false)}
        counts={{
          users: users.length,
          verifications: verificationQueue.length,
          posts: posts.length,
          reports: reports.length,
        }}
        adminEmail={adminEmail}
        onLogout={handleLogout}
      />

      <div className="flex-1 flex flex-col min-w-0">
        <AdminTopBar
          activeSection={activeSection}
          sidebarCollapsed={sidebarCollapsed}
          onToggleCollapse={() => setSidebarCollapsed(c => !c)}
          onOpenMobileDrawer={() => setMobileDrawerOpen(true)}
          searchQuery={searchQuery}
          onSearchChange={setSearchQuery}
          isLoading={isLoadingData}
          onSync={() => loadPlatformData(true)}
          themeMode={themeMode}
          onToggleTheme={toggleTheme}
          pendingCount={pendingQueueCount}
          onJumpToPending={() =>
            setActiveSection(verificationQueue.length > 0 ? 'verifications' : 'reports')
          }
        />

        <main className="flex-1 p-4 sm:p-6 max-w-7xl w-full mx-auto">
          {isManagementSection ? (
            <AdminManagementViews
              section={activeSection}
              searchQuery={searchQuery}
              onSearchChange={setSearchQuery}
              users={users}
              posts={posts}
              verificationQueue={verificationQueue}
              reports={reports}
              onToggleBadge={handleToggleBadge}
              onModerateUser={handleModerateUser}
              onInspectUser={(u) =>
                recordAudit('USER_INSPECT', u.username, 'Inspected profile details')
              }
              onApproveVerification={handleApproveVerification}
              onRejectVerification={handleRejectVerification}
              onModeratePost={handleModeratePost}
              onDismissReport={handleDismissReport}
              onResolveReportRemovePost={handleResolveReportRemovePost}
              onResolveReportSuspendUser={handleResolveReportSuspendUser}
              requestConfirm={setConfirmState}
            />
          ) : (
            <AdminSystemViews
              section={activeSection}
              searchQuery={searchQuery}
              users={users}
              posts={posts}
              verificationQueue={verificationQueue}
              reports={reports}
              auditLogs={auditLogs}
              diagnostics={diagnostics}
              apiHealthOk={apiHealthOk}
              configApiAvailable={configApiAvailable}
              featureFlags={featureFlags}
              adminEmail={adminEmail}
              onNavigate={setActiveSection}
              onResetDiagnostics={() => {
                vybeDiagnostics.reset();
                setDiagnostics(vybeDiagnostics.getSummary());
                showToast('Diagnostics counters reset.', 'info');
              }}
              onSendBroadcast={handleSendBroadcast}
              onToggleFeatureFlag={handleToggleFeatureFlag}
              requestConfirm={setConfirmState}
            />
          )}
        </main>
      </div>

      <VybeConfirmDialog
        open={Boolean(confirmState)}
        title={confirmState?.title || ''}
        description={confirmState?.description || ''}
        confirmLabel={confirmState?.confirmLabel || 'Confirm'}
        tone={confirmState?.tone || 'danger'}
        loading={confirmLoading}
        onConfirm={executeConfirm}
        onCancel={() => !confirmLoading && setConfirmState(null)}
      />

      <VybeToast
        message={toast?.message || null}
        tone={toast?.tone || 'info'}
        onDismiss={() => setToast(null)}
      />
    </div>
  );
};
