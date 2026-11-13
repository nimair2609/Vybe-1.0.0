import React, { useState, useEffect, useCallback } from 'react';
import {
  Shield, X, Check, AlertTriangle, Users, FileText, UserCheck,
  Award, BarChart3, Activity, Bell, Lock, Megaphone,
  Search, Trash2, CheckCircle2, RefreshCw, Send, LogOut, AlertCircle
} from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { OfficialBadge } from '../components/Badges';
import {
  SUPABASE_URL,
  SUPABASE_ANON_KEY,
  adminSignInWithPassword,
  getStoredAdminSession,
  adminSignOut,
  fetchLiveProfiles,
  fetchLivePosts,
  fetchLiveVerificationRequests,
  reviewVerificationRequestInSupabase,
  fetchLiveReports,
  updateReportStatusInSupabase,
  moderatePostInSupabase,
  suspendUserInSupabase,
  fetchLiveAuditLogs,
  LiveVerificationRequestItem,
  LiveReportItem,
  LiveAuditLogItem
} from '../services/supabaseService';
import { User, Post, OfficialVybeBadge } from '../types';

type AdminSection =
  | 'overview'
  | 'users'
  | 'verifications'
  | 'badges'
  | 'reports'
  | 'content'
  | 'health'
  | 'notifications'
  | 'audit';

export const AdminConsoleModal: React.FC = () => {
  const { closeModal } = useVybe();

  const [adminToken, setAdminToken] = useState<string | null>(() => {
    const s = getStoredAdminSession();
    return s.token && s.token.startsWith('eyJ') ? s.token : null;
  });
  const [adminEmail, setAdminEmail] = useState<string>(() => getStoredAdminSession().email || '');
  const [passwordInput, setPasswordInput] = useState('');
  const [authError, setAuthError] = useState<string | null>(null);
  const [isSigningIn, setIsSigningIn] = useState(false);

  const [activeSection, setActiveSection] = useState<AdminSection>('overview');
  const [userQuery, setUserQuery] = useState('');
  const [feedback, setFeedback] = useState<string | null>(null);
  const [isLoadingData, setIsLoadingData] = useState(false);

  const [users, setUsers] = useState<User[]>([]);
  const [posts, setPosts] = useState<Post[]>([]);
  const [verificationQueue, setVerificationQueue] = useState<LiveVerificationRequestItem[]>([]);
  const [reportsQueue, setReportsQueue] = useState<LiveReportItem[]>([]);
  const [auditLogs, setAuditLogs] = useState<LiveAuditLogItem[]>([]);
  const [pushStats, setPushStats] = useState<{ activeTokensCount: number; fcmConfigured: boolean; realtimeConfigured: boolean } | null>(null);

  const [broadcastTitle, setBroadcastTitle] = useState('');
  const [broadcastMessage, setBroadcastMessage] = useState('');
  const [broadcastSending, setBroadcastSending] = useState(false);

  const [selectedUserForBadge, setSelectedUserForBadge] = useState<string>('');
  const [assignBadgeType, setAssignBadgeType] = useState<OfficialVybeBadge>('VERIFIED_BLUE');
  const [badgeGrantMode, setBadgeGrantMode] = useState<boolean>(true);
  const [vipDuration, setVipDuration] = useState('365');
  const [isSubmittingBadge, setIsSubmittingBadge] = useState(false);

  const showToast = (msg: string) => {
    setFeedback(msg);
    setTimeout(() => setFeedback(null), 4000);
  };

  const loadLiveAdminData = useCallback(async (forceRefresh = false) => {
    if (!adminToken) return;
    setIsLoadingData(true);
    try {
      const [liveUsers, livePosts, liveVerifs, liveReports, liveAudits, statsResp] = await Promise.all([
        fetchLiveProfiles({ forceRefresh }),
        fetchLivePosts(undefined, { limit: 50, forceRefresh }),
        fetchLiveVerificationRequests(),
        fetchLiveReports(),
        fetchLiveAuditLogs(),
        fetch('/api/v1/admin/push-stats', {
          headers: { Authorization: `Bearer ${adminToken}` }
        }).then(r => (r.ok ? r.json() : null)).catch(() => null)
      ]);
      setUsers(liveUsers || []);
      if (liveUsers && liveUsers.length > 0 && !selectedUserForBadge) {
        setSelectedUserForBadge(liveUsers[0].id);
      }
      setPosts(livePosts || []);
      setVerificationQueue((liveVerifs || []).filter(v => v.status === 'PENDING' || v.status === 'MORE_INFO_REQUIRED'));
      setReportsQueue((liveReports || []).filter(r => r.status === 'OPEN' || r.status === 'INVESTIGATING'));
      setAuditLogs(liveAudits || []);
      if (statsResp) {
        setPushStats({
          activeTokensCount: Number(statsResp.activeTokensCount || 0),
          fcmConfigured: Boolean(statsResp.fcmConfigured),
          realtimeConfigured: Boolean(statsResp.realtimeConfigured)
        });
      }
    } catch (err) {
      console.warn('[AdminConsole] Load notice:', err);
    } finally {
      setIsLoadingData(false);
    }
  }, [adminToken, selectedUserForBadge]);

  useEffect(() => {
    if (adminToken) {
      loadLiveAdminData(true);
    }
  }, [adminToken, loadLiveAdminData]);

  const handleAdminSignIn = async (e: React.FormEvent) => {
    e.preventDefault();
    setAuthError(null);
    if (!adminEmail.trim() || !passwordInput) {
      setAuthError('Administrator email and password are required.');
      return;
    }
    setIsSigningIn(true);
    try {
      const res = await adminSignInWithPassword(adminEmail.trim(), passwordInput);
      if (!res.success || !res.token) {
        setAuthError(res.error || 'Administrator authentication failed.');
        return;
      }
      setAdminToken(res.token);
      setPasswordInput('');
      showToast('Verified Supabase Administrator session active.');
    } catch (err: any) {
      setAuthError(err?.message || 'Error verifying administrator credentials.');
    } finally {
      setIsSigningIn(false);
    }
  };

  const handleAdminSignOut = () => {
    adminSignOut();
    setAdminToken(null);
    setUsers([]);
    setPosts([]);
    setVerificationQueue([]);
    setReportsQueue([]);
    setAuditLogs([]);
  };

  const handleReviewVerification = async (
    item: LiveVerificationRequestItem,
    decision: 'APPROVED' | 'REJECTED'
  ) => {
    const res = await reviewVerificationRequestInSupabase({
      requestId: item.id,
      decision,
      notes: decision === 'APPROVED' ? `Approved ${item.badge} via Admin Console` : 'Declined via Admin Console'
    });
    if (!res.ok) {
      showToast(res.error || 'Verification review failed.');
      return;
    }
    setVerificationQueue(prev => prev.filter(q => q.id !== item.id));
    showToast(`${decision === 'APPROVED' ? 'Approved' : 'Declined'} application for @${item.username}.`);
    loadLiveAdminData(true);
  };

  const handleResolveReport = async (
    rep: LiveReportItem,
    actionType: 'DISMISSED' | 'CONTENT_REMOVED' | 'USER_SUSPENDED'
  ) => {
    if (actionType === 'CONTENT_REMOVED') {
      const mod = await moderatePostInSupabase({ postId: rep.targetId, action: 'DELETE' });
      if (!mod.ok) {
        showToast(mod.error || 'Failed removing reported content.');
        return;
      }
    } else if (actionType === 'USER_SUSPENDED') {
      const sus = await suspendUserInSupabase({
        userId: rep.targetId,
        action: 'SUSPEND',
        reason: rep.reason,
        durationHours: 72
      });
      if (!sus.ok) {
        showToast(sus.error || 'Failed suspending reported user.');
        return;
      }
    }

    const repRes = await updateReportStatusInSupabase({
      reportId: rep.id,
      status: actionType === 'DISMISSED' ? 'DISMISSED' : 'RESOLVED',
      adminNotes: `Action taken: ${actionType}`
    });
    if (!repRes.ok) {
      showToast(repRes.error || 'Failed updating report status.');
      return;
    }
    setReportsQueue(prev => prev.filter(r => r.id !== rep.id));
    showToast(`Report ${actionType === 'DISMISSED' ? 'dismissed' : 'resolved'}.`);
    loadLiveAdminData(true);
  };

  const handleArchivePost = async (postId: string) => {
    const res = await moderatePostInSupabase({ postId, action: 'ARCHIVE' });
    if (!res.ok) {
      showToast(res.error || 'Failed archiving post.');
      return;
    }
    setPosts(prev => prev.filter(p => p.id !== postId));
    showToast('Post archived and recorded in audit log.');
    loadLiveAdminData(true);
  };

  const handleSendBroadcast = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!adminToken || !broadcastTitle.trim() || !broadcastMessage.trim()) return;
    setBroadcastSending(true);
    try {
      const resp = await fetch('/api/v1/admin/broadcast-push', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${adminToken}`
        },
        body: JSON.stringify({
          title: broadcastTitle.trim(),
          body: broadcastMessage.trim()
        })
      });
      const data = await resp.json().catch(() => ({}));
      if (!resp.ok) {
        showToast(data?.error || `Broadcast rejected (${resp.status}).`);
        return;
      }
      setBroadcastTitle('');
      setBroadcastMessage('');
      showToast(`System broadcast sent (${data.totalDevices ?? 0} active target(s)).`);
      loadLiveAdminData(true);
    } catch (err: any) {
      showToast(err?.message || 'Error dispatching system broadcast.');
    } finally {
      setBroadcastSending(false);
    }
  };

  const handleAssignBadge = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!adminToken || !selectedUserForBadge) return;
    const adminUserId = localStorage.getItem('vybe_admin_user_id') || '';
    if (adminUserId && adminUserId.toLowerCase() === selectedUserForBadge.toLowerCase()) {
      showToast('Security Policy: Self-assignment or self-revocation of badges is prohibited.');
      return;
    }

    const categoryMap: Record<OfficialVybeBadge, string> = {
      VERIFIED_BLUE: 'VERIFIED',
      VIP_GOLD: 'VIP',
      WORKER_GREEN: 'WORKER',
      SHOP_GOLD: 'SHOP'
    };
    const rpcCategory = categoryMap[assignBadgeType];
    const vipDays = Number(vipDuration);
    const vipExpiresAt =
      assignBadgeType === 'VIP_GOLD' && badgeGrantMode && vipDays > 0
        ? new Date(Date.now() + vipDays * 86400 * 1000).toISOString()
        : null;

    setIsSubmittingBadge(true);
    try {
      const resp = await fetch(`${SUPABASE_URL}/rest/v1/rpc/admin_assign_badge`, {
        method: 'POST',
        headers: {
          apikey: SUPABASE_ANON_KEY,
          Authorization: `Bearer ${adminToken}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          p_target_user_id: selectedUserForBadge,
          p_badge_category: rpcCategory,
          p_is_grant: badgeGrantMode,
          p_vip_expires_at: vipExpiresAt,
          p_reason: 'Admin Console badge management',
          p_require_acceptance: false,
          p_worker_mandatory: rpcCategory === 'WORKER'
        })
      });
      if (!resp.ok) {
        const errData = await resp.json().catch(() => ({}));
        showToast(errData?.message || errData?.error || `Badge RPC rejected (${resp.status}).`);
        return;
      }
      showToast(`${assignBadgeType} ${badgeGrantMode ? 'assigned' : 'revoked'} cleanly.`);
      loadLiveAdminData(true);
    } catch (err: any) {
      showToast(err?.message || 'Network error updating badge.');
    } finally {
      setIsSubmittingBadge(false);
    }
  };

  if (!adminToken) {
    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/90 backdrop-blur-2xl animate-fade-in">
        <div className="w-full max-w-md bg-[#0E091D] border border-[#262E4A] rounded-3xl p-7 text-white shadow-2xl space-y-5">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="p-2.5 rounded-2xl bg-[#8B5CF6]/20 border border-[#8B5CF6]/30">
                <Shield className="w-6 h-6 text-[#C77DFF]" />
              </div>
              <div>
                <h2 className="text-base font-black tracking-wide">ADMIN SIGN IN</h2>
                <p className="text-[11px] text-gray-400">Verified Supabase Admin JWT Required</p>
              </div>
            </div>
            <button onClick={closeModal} className="p-2 text-gray-400 hover:text-white rounded-xl">
              <X className="w-5 h-5" />
            </button>
          </div>

          {authError && (
            <div className="p-3 rounded-xl bg-red-500/10 border border-red-500/30 text-red-300 text-xs flex items-center gap-2">
              <AlertTriangle className="w-4 h-4 shrink-0" />
              <span>{authError}</span>
            </div>
          )}

          <form onSubmit={handleAdminSignIn} className="space-y-4">
            <div>
              <label className="text-xs font-semibold text-gray-300 block mb-1">Administrator Email</label>
              <input
                type="email"
                value={adminEmail}
                onChange={e => setAdminEmail(e.target.value)}
                required
                placeholder="admin@vybe.app"
                className="w-full px-3.5 py-2.5 bg-[#0A0713] border border-[#262E4A] rounded-xl text-xs text-white focus:outline-none focus:border-[#8B5CF6]"
              />
            </div>
            <div>
              <label className="text-xs font-semibold text-gray-300 block mb-1">Password</label>
              <input
                type="password"
                value={passwordInput}
                onChange={e => setPasswordInput(e.target.value)}
                required
                placeholder="••••••••••••"
                className="w-full px-3.5 py-2.5 bg-[#0A0713] border border-[#262E4A] rounded-xl text-xs text-white focus:outline-none focus:border-[#8B5CF6]"
              />
            </div>
            <button
              type="submit"
              disabled={isSigningIn}
              className="w-full py-2.5 bg-gradient-to-r from-[#8B5CF6] to-[#00C6FF] text-white font-bold text-xs rounded-xl shadow-lg disabled:opacity-50"
            >
              {isSigningIn ? 'Verifying Admin Role...' : 'Sign In as Administrator'}
            </button>
          </form>
        </div>
      </div>
    );
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-2 sm:p-4 bg-black/90 backdrop-blur-2xl animate-fade-in">
      <div className="relative w-full max-w-6xl h-[92vh] flex flex-col bg-[#0A0713] border border-[#262E4A] rounded-3xl shadow-2xl overflow-hidden text-white">

        {/* Top Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-[#1E1638] bg-[#0E091D]">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-2xl bg-gradient-to-tr from-[#8B5CF6]/20 to-[#00C6FF]/20 border border-[#8B5CF6]/30 text-[#8B5CF6]">
              <Shield className="w-6 h-6 text-[#C77DFF]" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-lg font-black tracking-wider text-white">VYBE CENTRAL ADMIN</h1>
                <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-[#22C55E]/15 text-[#22C55E] border border-[#22C55E]/30">
                  LIVE SUPABASE JWT
                </span>
              </div>
              <p className="text-xs text-gray-400">Authenticated: {adminEmail || 'Administrator'}</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => loadLiveAdminData(true)}
              disabled={isLoadingData}
              className="px-3 py-2 rounded-xl bg-[#140E26] hover:bg-[#1D1438] border border-[#262E4A] text-xs font-semibold flex items-center gap-1.5"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isLoadingData ? 'animate-spin text-[#00C6FF]' : ''}`} />
              <span>Sync</span>
            </button>
            <button
              onClick={handleAdminSignOut}
              title="Sign Out"
              className="p-2 text-red-400 hover:text-red-300 rounded-xl bg-red-500/10 border border-red-500/20"
            >
              <LogOut className="w-4 h-4" />
            </button>
            <button
              onClick={closeModal}
              className="p-2 text-gray-400 hover:text-white rounded-xl bg-[#140E26] hover:bg-[#1D1438] border border-[#262E4A] transition"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* Feedback Banner */}
        {feedback && (
          <div className="px-6 py-2.5 bg-[#8B5CF6]/20 border-b border-[#8B5CF6]/40 text-sm font-semibold text-[#E9D5FF] flex items-center justify-between">
            <span>{feedback}</span>
            <button onClick={() => setFeedback(null)}><X className="w-4 h-4" /></button>
          </div>
        )}

        {/* Main Body */}
        <div className="flex flex-1 overflow-hidden">

          {/* Navigation Rail */}
          <div className="w-64 border-r border-[#1E1638] bg-[#0E091D]/80 p-3 overflow-y-auto space-y-1 shrink-0 no-scrollbar">
            {[
              { id: 'overview', label: '1. Overview', icon: BarChart3 },
              { id: 'users', label: '2. User Management', icon: Users },
              { id: 'verifications', label: '3. Verification Requests', icon: UserCheck, badgeCount: verificationQueue.length },
              { id: 'badges', label: '4. Badge Management', icon: Award },
              { id: 'reports', label: '5. Reports & Moderation', icon: AlertTriangle, badgeCount: reportsQueue.length },
              { id: 'content', label: '6. Content Management', icon: FileText },
              { id: 'health', label: '7. API & Push Health', icon: Activity },
              { id: 'notifications', label: '8. Push Broadcasts', icon: Bell },
              { id: 'audit', label: '9. Security & Audit Logs', icon: Lock }
            ].map(tab => {
              const IconComp = tab.icon;
              const isActive = activeSection === tab.id;
              return (
                <button
                  key={tab.id}
                  onClick={() => setActiveSection(tab.id as AdminSection)}
                  className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-semibold transition ${
                    isActive
                      ? 'bg-[#8B5CF6] text-white shadow-lg shadow-[#8B5CF6]/25'
                      : 'text-gray-400 hover:text-gray-200 hover:bg-[#16102C]'
                  }`}
                >
                  <div className="flex items-center gap-2.5">
                    <IconComp className="w-4 h-4 shrink-0" />
                    <span>{tab.label}</span>
                  </div>
                  {tab.badgeCount !== undefined && tab.badgeCount > 0 && (
                    <span className="px-1.5 py-0.5 rounded-full text-[10px] font-bold bg-[#EF4444] text-white">
                      {tab.badgeCount}
                    </span>
                  )}
                </button>
              );
            })}
          </div>

          {/* Content Panel */}
          <div className="flex-1 p-6 overflow-y-auto no-scrollbar bg-[#0A0713]">

            {/* OVERVIEW */}
            {activeSection === 'overview' && (
              <div className="space-y-6">
                <div>
                  <h2 className="text-xl font-bold text-white">Platform Health & Key Metrics</h2>
                  <p className="text-xs text-gray-400">Live counts fetched directly from Supabase</p>
                </div>

                <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                  {[
                    { label: 'Fetched Profiles', val: String(users.length), change: 'Live Supabase rows', color: '#00C6FF' },
                    { label: 'Fetched Posts & Reels', val: String(posts.length), change: 'Live database feed', color: '#FBBF24' },
                    { label: 'Pending Verifications', val: String(verificationQueue.length), change: 'Awaiting review', color: '#FFB703' },
                    { label: 'Open Safety Reports', val: String(reportsQueue.length), change: 'Active queue', color: '#EF4444' },
                    { label: 'Active Push Devices', val: String(pushStats?.activeTokensCount ?? 0), change: 'Registered FCM tokens', color: '#22C55E' },
                    { label: 'Audit Trail Records', val: String(auditLogs.length), change: 'Immutable admin logs', color: '#8B5CF6' }
                  ].map((stat, i) => (
                    <div key={i} className="p-4 rounded-2xl bg-[#120D22] border border-[#221840]">
                      <span className="text-[11px] font-medium text-gray-400 block">{stat.label}</span>
                      <span className="text-2xl font-black mt-1 block" style={{ color: stat.color }}>{stat.val}</span>
                      <span className="text-[10px] text-gray-500 mt-1 block">{stat.change}</span>
                    </div>
                  ))}
                </div>

                <div className="p-5 rounded-2xl bg-[#120D22] border border-[#221840] space-y-4">
                  <div className="flex items-center justify-between">
                    <h3 className="text-sm font-bold text-white flex items-center gap-2">
                      <AlertCircle className="w-4 h-4 text-[#FFB703]" />
                      Moderation Queues
                    </h3>
                  </div>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                    <div className="p-3.5 rounded-xl bg-[#191130] border border-[#2A1E4A] flex items-center justify-between">
                      <div>
                        <span className="text-xs font-bold text-white block">Verification Queue ({verificationQueue.length})</span>
                        <span className="text-[11px] text-gray-400">Creator & business badge applications</span>
                      </div>
                      <button
                        onClick={() => setActiveSection('verifications')}
                        className="px-3 py-1 rounded-lg bg-[#8B5CF6] text-white text-xs font-bold"
                      >
                        Review
                      </button>
                    </div>
                    <div className="p-3.5 rounded-xl bg-[#191130] border border-[#2A1E4A] flex items-center justify-between">
                      <div>
                        <span className="text-xs font-bold text-white block">Safety Reports ({reportsQueue.length})</span>
                        <span className="text-[11px] text-gray-400">Open user & content reports</span>
                      </div>
                      <button
                        onClick={() => setActiveSection('reports')}
                        className="px-3 py-1 rounded-lg bg-[#EF4444] text-white text-xs font-bold"
                      >
                        Inspect
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* USER MANAGEMENT */}
            {activeSection === 'users' && (
              <div className="space-y-5">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                  <div>
                    <h2 className="text-xl font-bold text-white">Registered User Directory</h2>
                    <p className="text-xs text-gray-400">Live Supabase profiles ({users.length})</p>
                  </div>
                  <div className="relative w-72">
                    <Search className="w-4 h-4 absolute left-3 top-3 text-gray-400" />
                    <input
                      type="text"
                      placeholder="Search by username or ID..."
                      value={userQuery}
                      onChange={e => setUserQuery(e.target.value)}
                      className="w-full pl-9 pr-3 py-2 bg-[#120D22] border border-[#221840] rounded-xl text-xs text-white placeholder-gray-500 focus:outline-none focus:border-[#8B5CF6]"
                    />
                  </div>
                </div>

                {users.length === 0 ? (
                  <div className="text-center py-12 bg-[#100B20] rounded-2xl border border-[#1E1638] text-xs text-gray-400">
                    No profiles loaded yet.
                  </div>
                ) : (
                  <div className="border border-[#1E1638] rounded-2xl overflow-hidden bg-[#100B20]">
                    <table className="w-full text-left text-xs">
                      <thead className="bg-[#17102C] text-gray-400 uppercase text-[10px] tracking-wider border-b border-[#221840]">
                        <tr>
                          <th className="p-3.5">User</th>
                          <th className="p-3.5">Public ID</th>
                          <th className="p-3.5">Role</th>
                          <th className="p-3.5">Badges</th>
                          <th className="p-3.5 text-right">Actions</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-[#1A1233]">
                        {users
                          .filter(u =>
                            !userQuery ||
                            u.username.toLowerCase().includes(userQuery.toLowerCase()) ||
                            u.displayName.toLowerCase().includes(userQuery.toLowerCase())
                          )
                          .map(u => (
                            <tr key={u.id} className="hover:bg-[#160F2C] transition">
                              <td className="p-3.5 flex items-center gap-2.5">
                                <img src={u.avatarUrl} alt={u.displayName} className="w-8 h-8 rounded-full object-cover border border-[#262E4A]" />
                                <div>
                                  <span className="font-bold text-white block">{u.displayName}</span>
                                  <span className="text-[11px] text-gray-400">@{u.username}</span>
                                </div>
                              </td>
                              <td className="p-3.5 font-mono text-gray-300">{u.publicVybeId}</td>
                              <td className="p-3.5">
                                <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                                  u.role === 'ADMIN' ? 'bg-[#FFB703]/20 text-[#FFB703]' : 'bg-gray-800 text-gray-300'
                                }`}>
                                  {u.role || 'USER'}
                                </span>
                              </td>
                              <td className="p-3.5">
                                <div className="flex items-center gap-1">
                                  {u.isVerified && <OfficialBadge badge="VERIFIED_BLUE" size="sm" />}
                                  {u.isVip && <OfficialBadge badge="VIP_GOLD" size="sm" />}
                                  {u.isWorker && <OfficialBadge badge="WORKER_GREEN" size="sm" />}
                                  {!u.isVerified && !u.isVip && !u.isWorker && <span className="text-gray-500">—</span>}
                                </div>
                              </td>
                              <td className="p-3.5 text-right">
                                <button
                                  onClick={() => {
                                    setSelectedUserForBadge(u.id);
                                    setActiveSection('badges');
                                  }}
                                  className="px-2.5 py-1 rounded-lg bg-[#8B5CF6]/20 border border-[#8B5CF6]/40 text-[#C77DFF] hover:bg-[#8B5CF6]/30 font-semibold"
                                >
                                  Edit Badges
                                </button>
                              </td>
                            </tr>
                          ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            )}

            {/* VERIFICATIONS */}
            {activeSection === 'verifications' && (
              <div className="space-y-5">
                <div>
                  <h2 className="text-xl font-bold text-white">Official Verification Applications</h2>
                  <p className="text-xs text-gray-400">Backed by Supabase admin_review_verification_request RPC</p>
                </div>

                {verificationQueue.length === 0 ? (
                  <div className="text-center py-16 bg-[#100B20] rounded-2xl border border-[#1E1638]">
                    <CheckCircle2 className="w-10 h-10 text-[#22C55E] mx-auto mb-2" />
                    <p className="text-sm font-bold text-white">No pending applications</p>
                    <p className="text-xs text-gray-400 mt-1">All submitted verification requests have been reviewed.</p>
                  </div>
                ) : (
                  <div className="space-y-3">
                    {verificationQueue.map(item => (
                      <div
                        key={item.id}
                        className="p-5 rounded-2xl bg-[#120D22] border border-[#221840] flex flex-col md:flex-row md:items-center justify-between gap-4"
                      >
                        <div className="flex items-start gap-3.5">
                          <img src={item.avatarUrl} alt={item.displayName} className="w-12 h-12 rounded-full object-cover border border-[#2A1E4A]" />
                          <div>
                            <div className="flex items-center gap-2">
                              <span className="font-bold text-white text-sm">{item.displayName}</span>
                              <span className="text-xs text-gray-400 font-mono">@{item.username}</span>
                              <OfficialBadge badge={item.badge} size="sm" />
                            </div>
                            <span className="text-xs font-semibold text-[#00C6FF] block mt-0.5">{item.category}</span>
                            <p className="text-xs text-gray-300 mt-1 bg-[#191130] p-2 rounded-lg border border-[#261B42]">
                              <span className="text-gray-400 font-semibold">Proof: </span>
                              {item.proof}
                            </p>
                            <span className="text-[10px] text-gray-500 block mt-1">Submitted: {item.submittedAt}</span>
                          </div>
                        </div>

                        <div className="flex items-center gap-2 shrink-0">
                          <button
                            onClick={() => handleReviewVerification(item, 'REJECTED')}
                            className="px-4 py-2 rounded-xl bg-red-500/10 border border-red-500/30 text-red-300 text-xs font-bold hover:bg-red-500/20 transition"
                          >
                            Decline
                          </button>
                          <button
                            onClick={() => handleReviewVerification(item, 'APPROVED')}
                            className="px-4 py-2 rounded-xl bg-[#22C55E] text-white text-xs font-bold hover:bg-[#16A34A] shadow-lg shadow-[#22C55E]/20 transition flex items-center gap-1.5"
                          >
                            <Check className="w-4 h-4" />
                            Approve Badge
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* BADGES */}
            {activeSection === 'badges' && (
              <div className="space-y-6">
                <div>
                  <h2 className="text-xl font-bold text-white">Direct Badge Grant & Revocation</h2>
                  <p className="text-xs text-gray-400">Audited via Supabase admin_assign_badge RPC</p>
                </div>

                <form onSubmit={handleAssignBadge} className="p-6 rounded-2xl bg-[#120D22] border border-[#221840] space-y-4 max-w-xl">
                  <div>
                    <label className="text-xs font-bold text-gray-300 block mb-1.5">Target User</label>
                    <select
                      value={selectedUserForBadge}
                      onChange={e => setSelectedUserForBadge(e.target.value)}
                      className="w-full px-3.5 py-2.5 bg-[#191130] border border-[#2A1E4A] rounded-xl text-xs text-white focus:outline-none focus:border-[#8B5CF6]"
                    >
                      {users.map(u => (
                        <option key={u.id} value={u.id}>{u.displayName} (@{u.username})</option>
                      ))}
                    </select>
                  </div>

                  <div>
                    <label className="text-xs font-bold text-gray-300 block mb-1.5">Official Badge Tier</label>
                    <div className="grid grid-cols-2 gap-2.5">
                      {[
                        { id: 'VERIFIED_BLUE', name: 'VYBE Verified (Blue)', desc: 'Notable figures & artists' },
                        { id: 'VIP_GOLD', name: 'VYBE Gold (Crown)', desc: 'Premium subscriber' },
                        { id: 'SHOP_GOLD', name: 'VYBE Shop (Orange)', desc: 'Registered business' },
                        { id: 'WORKER_GREEN', name: 'VYBE Workers (Green)', desc: 'Super Admin staff badge' }
                      ].map(b => (
                        <div
                          key={b.id}
                          onClick={() => setAssignBadgeType(b.id as OfficialVybeBadge)}
                          className={`p-3 rounded-xl border cursor-pointer transition ${
                            assignBadgeType === b.id
                              ? 'bg-[#8B5CF6]/20 border-[#8B5CF6] text-white'
                              : 'bg-[#191130] border-[#2A1E4A] text-gray-400 hover:text-white'
                          }`}
                        >
                          <span className="font-bold text-xs block">{b.name}</span>
                          <span className="text-[10px] text-gray-500 block">{b.desc}</span>
                        </div>
                      ))}
                    </div>
                  </div>

                  <div className="flex items-center gap-3">
                    <button
                      type="button"
                      onClick={() => setBadgeGrantMode(true)}
                      className={`flex-1 py-2 rounded-xl text-xs font-bold border ${
                        badgeGrantMode ? 'bg-emerald-500/20 border-emerald-500/40 text-emerald-300' : 'bg-[#191130] border-[#2A1E4A] text-gray-400'
                      }`}
                    >
                      Grant Badge
                    </button>
                    <button
                      type="button"
                      onClick={() => setBadgeGrantMode(false)}
                      className={`flex-1 py-2 rounded-xl text-xs font-bold border ${
                        !badgeGrantMode ? 'bg-red-500/20 border-red-500/40 text-red-300' : 'bg-[#191130] border-[#2A1E4A] text-gray-400'
                      }`}
                    >
                      Revoke Badge
                    </button>
                  </div>

                  {assignBadgeType === 'VIP_GOLD' && badgeGrantMode && (
                    <div>
                      <label className="text-xs font-bold text-gray-300 block mb-1.5">VIP Expiration Period</label>
                      <select
                        value={vipDuration}
                        onChange={e => setVipDuration(e.target.value)}
                        className="w-full px-3.5 py-2.5 bg-[#191130] border border-[#2A1E4A] rounded-xl text-xs text-white focus:outline-none focus:border-[#8B5CF6]"
                      >
                        <option value="30">30 Days</option>
                        <option value="90">90 Days</option>
                        <option value="365">365 Days (1 Year)</option>
                        <option value="0">Lifetime / Indefinite</option>
                      </select>
                    </div>
                  )}

                  <button
                    type="submit"
                    disabled={isSubmittingBadge || !selectedUserForBadge}
                    className="w-full py-2.5 bg-[#8B5CF6] hover:bg-[#7C3AED] disabled:opacity-50 text-white font-bold text-xs rounded-xl shadow-lg shadow-[#8B5CF6]/25 transition"
                  >
                    {isSubmittingBadge ? 'Submitting RPC...' : 'Confirm Badge Action'}
                  </button>
                </form>
              </div>
            )}

            {/* REPORTS */}
            {activeSection === 'reports' && (
              <div className="space-y-5">
                <div>
                  <h2 className="text-xl font-bold text-white">Content & Safety Moderation</h2>
                  <p className="text-xs text-gray-400">Live reports queue from Supabase public.reports</p>
                </div>

                {reportsQueue.length === 0 ? (
                  <div className="text-center py-16 bg-[#100B20] rounded-2xl border border-[#1E1638]">
                    <CheckCircle2 className="w-10 h-10 text-[#22C55E] mx-auto mb-2" />
                    <p className="text-sm font-bold text-white">Clean inbox — 0 pending incident reports</p>
                  </div>
                ) : (
                  <div className="space-y-3">
                    {reportsQueue.map(rep => (
                      <div key={rep.id} className="p-5 rounded-2xl bg-[#120D22] border border-[#221840] space-y-3">
                        <div className="flex items-center justify-between">
                          <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-[#EF4444]/20 text-[#EF4444] border border-[#EF4444]/30">
                            {rep.targetType} • {rep.status}
                          </span>
                          <span className="text-[10px] text-gray-500">{rep.createdAt}</span>
                        </div>
                        <div>
                          <p className="text-xs text-gray-300">
                            <span className="text-gray-400 font-semibold">Target: </span>
                            <span className="text-white font-mono">{rep.targetId}</span> • Reported by {rep.reporterUsername}
                          </p>
                          <p className="text-xs text-red-200 mt-1 bg-red-950/30 p-2.5 rounded-xl border border-red-900/40">
                            {rep.reason}
                          </p>
                        </div>
                        <div className="flex flex-wrap items-center gap-2 pt-1 border-t border-[#1F1738]">
                          <button
                            onClick={() => handleResolveReport(rep, 'DISMISSED')}
                            className="px-3 py-1.5 rounded-lg bg-[#191130] border border-[#2A1E4A] text-gray-300 hover:text-white text-xs font-semibold"
                          >
                            Dismiss
                          </button>
                          {rep.targetType === 'POST' && (
                            <button
                              onClick={() => handleResolveReport(rep, 'CONTENT_REMOVED')}
                              className="px-3 py-1.5 rounded-lg bg-orange-500/20 border border-orange-500/40 text-orange-200 hover:bg-orange-500/30 text-xs font-semibold"
                            >
                              Remove Content
                            </button>
                          )}
                          {rep.targetType === 'USER' && (
                            <button
                              onClick={() => handleResolveReport(rep, 'USER_SUSPENDED')}
                              className="px-3 py-1.5 rounded-lg bg-red-600 hover:bg-red-700 text-white text-xs font-bold"
                            >
                              Suspend User
                            </button>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* CONTENT */}
            {activeSection === 'content' && (
              <div className="space-y-5">
                <div>
                  <h2 className="text-xl font-bold text-white">Content Library Moderation</h2>
                  <p className="text-xs text-gray-400">Live posts and reels fetched from Supabase ({posts.length})</p>
                </div>
                {posts.length === 0 ? (
                  <div className="text-center py-12 bg-[#100B20] rounded-2xl border border-[#1E1638] text-xs text-gray-400">
                    No posts loaded yet.
                  </div>
                ) : (
                  <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                    {posts.slice(0, 12).map(p => (
                      <div key={p.id} className="p-3.5 rounded-2xl bg-[#120D22] border border-[#221840] space-y-2.5">
                        <div className="flex items-center gap-2">
                          <img src={p.author.avatarUrl} alt={p.author.displayName} className="w-6 h-6 rounded-full object-cover" />
                          <span className="text-xs font-bold text-white truncate">@{p.author.username}</span>
                          <span className="text-[10px] text-gray-400 ml-auto">{p.moodZone || 'GENERAL'}</span>
                        </div>
                        {p.mediaUrls[0] && (
                          <img src={p.mediaUrls[0]} alt="Post media" className="w-full h-32 object-cover rounded-xl border border-[#1F1738]" />
                        )}
                        <p className="text-xs text-gray-300 line-clamp-2">{p.caption}</p>
                        <div className="flex items-center justify-between text-[11px] text-gray-400 pt-2 border-t border-[#1C1436]">
                          <span>{p.likesCount} loves • {p.commentsCount} comments</span>
                          <button
                            onClick={() => handleArchivePost(p.id)}
                            className="text-red-400 hover:text-red-300 font-semibold flex items-center gap-1"
                          >
                            <Trash2 className="w-3 h-3" />
                            Archive
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* API & PUSH HEALTH */}
            {activeSection === 'health' && (
              <div className="space-y-5">
                <div>
                  <h2 className="text-xl font-bold text-white">Infrastructure & Push Diagnostics</h2>
                  <p className="text-xs text-gray-400">Live status queried from /api/v1/admin/push-stats & Supabase endpoint</p>
                </div>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div className="p-4 rounded-2xl bg-[#120D22] border border-[#221840] space-y-2">
                    <div className="flex items-center justify-between">
                      <span className="font-bold text-xs text-white">Supabase REST & Auth</span>
                      <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-[#22C55E]/20 text-[#22C55E]">
                        CONNECTED
                      </span>
                    </div>
                    <p className="text-[10px] text-gray-400 font-mono truncate">{SUPABASE_URL}</p>
                  </div>
                  <div className="p-4 rounded-2xl bg-[#120D22] border border-[#221840] space-y-2">
                    <div className="flex items-center justify-between">
                      <span className="font-bold text-xs text-white">Push Notification Gateway</span>
                      <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-[#22C55E]/20 text-[#22C55E]">
                        {pushStats?.fcmConfigured ? 'FCM CONFIGURED' : 'REALTIME STANDBY'}
                      </span>
                    </div>
                    <p className="text-[11px] text-gray-400">
                      Registered active device tokens: <strong className="text-[#00C6FF]">{pushStats?.activeTokensCount ?? 0}</strong>
                    </p>
                  </div>
                </div>
              </div>
            )}

            {/* PUSH BROADCASTS */}
            {activeSection === 'notifications' && (
              <div className="space-y-5">
                <div>
                  <h2 className="text-xl font-bold text-white">System Broadcast Notification</h2>
                  <p className="text-xs text-gray-400">Authenticated dispatch via /api/v1/admin/broadcast-push</p>
                </div>
                <form onSubmit={handleSendBroadcast} className="p-6 rounded-2xl bg-[#120D22] border border-[#221840] space-y-4 max-w-xl">
                  <div>
                    <label className="text-xs font-bold text-gray-300 block mb-1">Broadcast Title</label>
                    <input
                      type="text"
                      placeholder="e.g. VYBE Platform Announcement"
                      value={broadcastTitle}
                      onChange={e => setBroadcastTitle(e.target.value)}
                      required
                      className="w-full px-3.5 py-2.5 bg-[#191130] border border-[#2A1E4A] rounded-xl text-xs text-white focus:outline-none focus:border-[#8B5CF6]"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-bold text-gray-300 block mb-1">Push Message Body</label>
                    <textarea
                      rows={3}
                      placeholder="Enter the announcement body..."
                      value={broadcastMessage}
                      onChange={e => setBroadcastMessage(e.target.value)}
                      required
                      className="w-full px-3.5 py-2.5 bg-[#191130] border border-[#2A1E4A] rounded-xl text-xs text-white focus:outline-none focus:border-[#8B5CF6]"
                    />
                  </div>
                  <button
                    type="submit"
                    disabled={broadcastSending}
                    className="w-full py-2.5 bg-[#8B5CF6] hover:bg-[#7C3AED] disabled:opacity-50 text-white font-bold text-xs rounded-xl shadow-lg shadow-[#8B5CF6]/25 transition flex items-center justify-center gap-2"
                  >
                    <Send className="w-4 h-4" />
                    {broadcastSending ? 'Dispatching Push...' : 'Send Global Broadcast Push'}
                  </button>
                </form>
              </div>
            )}

            {/* AUDIT LOGS */}
            {activeSection === 'audit' && (
              <div className="space-y-5">
                <div>
                  <h2 className="text-xl font-bold text-white">Security & Audit Event Trail</h2>
                  <p className="text-xs text-gray-400">Live records from Supabase public.admin_audit_logs</p>
                </div>
                {auditLogs.length === 0 ? (
                  <div className="text-center py-12 bg-[#100B20] rounded-2xl border border-[#1E1638] text-xs text-gray-400">
                    No audit log entries recorded yet.
                  </div>
                ) : (
                  <div className="border border-[#1E1638] rounded-2xl overflow-hidden bg-[#100B20]">
                    <table className="w-full text-left text-xs">
                      <thead className="bg-[#17102C] text-gray-400 uppercase text-[10px] tracking-wider border-b border-[#221840]">
                        <tr>
                          <th className="p-3.5">Action</th>
                          <th className="p-3.5">Admin</th>
                          <th className="p-3.5">Target</th>
                          <th className="p-3.5">Details</th>
                          <th className="p-3.5 text-right">Timestamp</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-[#1A1233]">
                        {auditLogs.map(log => (
                          <tr key={log.id} className="hover:bg-[#160F2C]">
                            <td className="p-3.5 font-mono font-bold text-[#8B5CF6]">{log.action}</td>
                            <td className="p-3.5 font-semibold text-gray-300">{log.adminEmail}</td>
                            <td className="p-3.5 font-mono text-gray-400">{log.targetType}:{log.targetId}</td>
                            <td className="p-3.5 text-gray-300">{log.details}</td>
                            <td className="p-3.5 text-right text-gray-500 font-mono text-[10px]">{log.createdAt}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            )}

          </div>
        </div>

      </div>
    </div>
  );
};
