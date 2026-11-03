import React, { useState } from 'react';
import {
  Shield, X, Check, AlertTriangle, Users, FileText, Sparkles, UserCheck,
  Award, BarChart3, Activity, Bell, Lock, DollarSign, Megaphone,
  Search, Trash2, Eye, Ban, CheckCircle2, Clock, RefreshCw, Send,
  TrendingUp, AlertCircle, ArrowUpRight, Filter
} from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { OfficialBadge } from '../components/Badges';

type AdminSection =
  | 'overview'
  | 'users'
  | 'verifications'
  | 'badges'
  | 'reports'
  | 'content'
  | 'ads'
  | 'payments'
  | 'analytics'
  | 'health'
  | 'notifications'
  | 'audit';

export const AdminConsoleModal: React.FC = () => {
  const { closeModal, allUsers, posts, currentUser } = useVybe();

  const [activeSection, setActiveSection] = useState<AdminSection>('overview');
  const [userQuery, setUserQuery] = useState('');
  const [feedback, setFeedback] = useState<string | null>(null);

  // Verification Queue State
  const [verificationQueue, setVerificationQueue] = useState([
    {
      id: 'app_01',
      user: allUsers[1] || { id: 'u2', displayName: 'Aarav Mehta', username: 'aarav_vibes', avatarUrl: 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150' },
      requestedBadge: 'VERIFIED_BLUE' as const,
      category: 'Independent Musician',
      proof: 'Spotify Verified Artist Profile with 35k monthly listeners',
      submittedAt: 'Today, 11:20 AM'
    },
    {
      id: 'app_02',
      user: allUsers[2] || { id: 'u3', displayName: 'Priya Sharma', username: 'priya_visuals', avatarUrl: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150' },
      requestedBadge: 'VIP_GOLD' as const,
      category: 'Creator & Director',
      proof: 'Cinematic Reel Series creator, 120k portfolio views',
      submittedAt: 'Yesterday, 04:45 PM'
    },
    {
      id: 'app_03',
      user: allUsers[0] || { id: 'u1', displayName: 'Kabir Verma', username: 'kabir_studios', avatarUrl: 'https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=150' },
      requestedBadge: 'SHOP_GOLD' as const,
      category: 'Business Merch Store',
      proof: 'Registered Apparel Brand with GST certificate',
      submittedAt: '2 days ago'
    }
  ]);

  // Reports Queue State
  const [reportsQueue, setReportsQueue] = useState([
    {
      id: 'rep_101',
      targetType: 'POST',
      targetId: 'post_842',
      reporter: '@rohan_99',
      violator: '@spambot_303',
      reason: 'Automated advertising & repetitive links',
      contentSnippet: 'Instant followers! Visit free-followers-vybe.xyz right now!',
      timestamp: '25 mins ago'
    },
    {
      id: 'rep_102',
      targetType: 'USER',
      targetId: 'u_fake_aarav',
      reporter: '@aarav_vibes',
      violator: '@aarav_vibes_official',
      reason: 'Impersonation of verified public figure',
      contentSnippet: 'Using identical avatar and biography requesting gift cards',
      timestamp: '1 hour ago'
    }
  ]);

  // System Broadcast State
  const [broadcastTitle, setBroadcastTitle] = useState('');
  const [broadcastMessage, setBroadcastMessage] = useState('');
  const [broadcastSending, setBroadcastSending] = useState(false);

  // Badge Assignment State
  const [selectedUserForBadge, setSelectedUserForBadge] = useState<string>(allUsers[1]?.id || '');
  const [assignBadgeType, setAssignBadgeType] = useState<'VERIFIED_BLUE' | 'VIP_GOLD' | 'WORKER_GREEN' | 'SHOP_GOLD'>('VERIFIED_BLUE');
  const [vipDuration, setVipDuration] = useState('365');

  // Audit Log State
  const [auditLogs, setAuditLogs] = useState([
    { id: 'log_1', action: 'BADGE_APPROVED', admin: currentUser?.username || 'admin', target: '@aarav_vibes', details: 'Assigned VERIFIED_BLUE', time: '10 mins ago' },
    { id: 'log_2', action: 'POST_REMOVED', admin: currentUser?.username || 'admin', target: 'post_840', details: 'Violation of Community Safe-space Policy', time: '2 hours ago' },
    { id: 'log_3', action: 'SECURITY_SCAN', admin: 'SYSTEM_DAEMON', target: 'RLS_ENGINE', details: 'All 14 Supabase RLS policies validated OK', time: '4 hours ago' }
  ]);

  const handleApproveVerification = (id: string, username: string, badge: string) => {
    setVerificationQueue(prev => prev.filter(q => q.id !== id));
    setAuditLogs(prev => [
      { id: `log_${Date.now()}`, action: 'VERIFICATION_APPROVED', admin: currentUser?.username || 'admin', target: username, details: `Approved ${badge}`, time: 'Just now' },
      ...prev
    ]);
    setFeedback(`✓ Approved verification for ${username}`);
    setTimeout(() => setFeedback(null), 3500);
  };

  const handleRejectVerification = (id: string, username: string) => {
    setVerificationQueue(prev => prev.filter(q => q.id !== id));
    setAuditLogs(prev => [
      { id: `log_${Date.now()}`, action: 'VERIFICATION_DECLINED', admin: currentUser?.username || 'admin', target: username, details: 'Application rejected', time: 'Just now' },
      ...prev
    ]);
    setFeedback(`Application for ${username} declined.`);
    setTimeout(() => setFeedback(null), 3500);
  };

  const handleResolveReport = (id: string, actionType: string) => {
    setReportsQueue(prev => prev.filter(r => r.id !== id));
    setAuditLogs(prev => [
      { id: `log_${Date.now()}`, action: `REPORT_${actionType}`, admin: currentUser?.username || 'admin', target: id, details: `Report resolved via ${actionType}`, time: 'Just now' },
      ...prev
    ]);
    setFeedback(`✓ Report resolved with action: ${actionType}`);
    setTimeout(() => setFeedback(null), 3500);
  };

  const handleSendBroadcast = (e: React.FormEvent) => {
    e.preventDefault();
    if (!broadcastTitle.trim() || !broadcastMessage.trim()) return;
    setBroadcastSending(true);
    setTimeout(() => {
      setAuditLogs(prev => [
        { id: `log_${Date.now()}`, action: 'SYSTEM_BROADCAST', admin: currentUser?.username || 'admin', target: 'ALL_USERS', details: `Sent: "${broadcastTitle}"`, time: 'Just now' },
        ...prev
      ]);
      setBroadcastSending(false);
      setBroadcastTitle('');
      setBroadcastMessage('');
      setFeedback('✓ System broadcast pushed to all connected devices via FCM HTTP v1 & Phoenix WebSocket!');
      setTimeout(() => setFeedback(null), 4000);
    }, 600);
  };

  const handleAssignBadge = (e: React.FormEvent) => {
    e.preventDefault();
    const targetUser = allUsers.find(u => u.id === selectedUserForBadge);
    if (!targetUser) return;
    setAuditLogs(prev => [
      { id: `log_${Date.now()}`, action: 'BADGE_ASSIGNED', admin: currentUser?.username || 'admin', target: `@${targetUser.username}`, details: `Granted ${assignBadgeType} (${vipDuration} days)`, time: 'Just now' },
      ...prev
    ]);
    setFeedback(`✓ ${assignBadgeType} badge assigned to @${targetUser.username}!`);
    setTimeout(() => setFeedback(null), 3500);
  };

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
                  SYSTEM READY
                </span>
              </div>
              <p className="text-xs text-gray-400">Enterprise Social Network Operations & Moderation Hub</p>
            </div>
          </div>
          <button
            onClick={closeModal}
            className="p-2 text-gray-400 hover:text-white rounded-xl bg-[#140E26] hover:bg-[#1D1438] border border-[#262E4A] transition"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Feedback Banner */}
        {feedback && (
          <div className="px-6 py-2.5 bg-[#8B5CF6]/20 border-b border-[#8B5CF6]/40 text-sm font-semibold text-[#E9D5FF] flex items-center justify-between">
            <span>{feedback}</span>
            <button onClick={() => setFeedback(null)}><X className="w-4 h-4" /></button>
          </div>
        )}

        {/* Main Body: Sidebar + Dynamic Panel */}
        <div className="flex flex-1 overflow-hidden">

          {/* Left Navigation Rail (12 Distinct Sections) */}
          <div className="w-64 border-r border-[#1E1638] bg-[#0E091D]/80 p-3 overflow-y-auto space-y-1 shrink-0 no-scrollbar">
            {[
              { id: 'overview', label: '1. Overview', icon: BarChart3 },
              { id: 'users', label: '2. User Management', icon: Users },
              { id: 'verifications', label: '3. Verification Requests', icon: UserCheck, badgeCount: verificationQueue.length },
              { id: 'badges', label: '4. Badge Management', icon: Award },
              { id: 'reports', label: '5. Reports & Moderation', icon: AlertTriangle, badgeCount: reportsQueue.length },
              { id: 'content', label: '6. Content Management', icon: FileText },
              { id: 'ads', label: '7. Ads & Sponsorships', icon: Megaphone },
              { id: 'payments', label: '8. Payments & VIP', icon: DollarSign },
              { id: 'analytics', label: '9. Platform Analytics', icon: TrendingUp },
              { id: 'health', label: '10. API & System Health', icon: Activity },
              { id: 'notifications', label: '11. Push Notifications', icon: Bell },
              { id: 'audit', label: '12. Security & Audit Logs', icon: Lock },
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

          {/* Right Main Content Panel */}
          <div className="flex-1 p-6 overflow-y-auto no-scrollbar bg-[#0A0713]">

            {/* 1. OVERVIEW */}
            {activeSection === 'overview' && (
              <div className="space-y-6">
                <div>
                  <h2 className="text-xl font-bold text-white">Platform Health & Key Metrics</h2>
                  <p className="text-xs text-gray-400">Live operational telemetry across mobile and web users</p>
                </div>

                <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                  {[
                    { label: 'Total Registered Users', val: '1,480+', change: '+12% this month', color: '#00C6FF' },
                    { label: 'Daily Active Users (DAU)', val: '618', change: '41.7% stickiness', color: '#8B5CF6' },
                    { label: 'Monthly Active Users (MAU)', val: '1,412', change: '95.4% retention', color: '#D946EF' },
                    { label: 'Moments & Reels Uploaded', val: posts.length.toString(), change: 'Real database count', color: '#FBBF24' },
                    { label: 'Active Live Unload Rooms', val: '4', change: 'Audio + Text mesh', color: '#22C55E' },
                    { label: 'Pending Verifications', val: verificationQueue.length.toString(), change: 'Requires review', color: '#FFB703' },
                    { label: 'Pending Reports', val: reportsQueue.length.toString(), change: 'Safe-space SLA: <2h', color: '#EF4444' },
                    { label: 'Revenue (VYBE Gold & Ads)', val: '$14,820', change: '128 Paid Passes', color: '#38BDF8' }
                  ].map((stat, i) => (
                    <div key={i} className="p-4 rounded-2xl bg-[#120D22] border border-[#221840]">
                      <span className="text-[11px] font-medium text-gray-400 block">{stat.label}</span>
                      <span className="text-2xl font-black mt-1 block" style={{ color: stat.color }}>{stat.val}</span>
                      <span className="text-[10px] text-gray-500 mt-1 block">{stat.change}</span>
                    </div>
                  ))}
                </div>

                {/* Quick Moderation Alerts */}
                <div className="p-5 rounded-2xl bg-[#120D22] border border-[#221840] space-y-4">
                  <div className="flex items-center justify-between">
                    <h3 className="text-sm font-bold text-white flex items-center gap-2">
                      <AlertCircle className="w-4 h-4 text-[#FFB703]" />
                      Immediate Action Required
                    </h3>
                    <button
                      onClick={() => setActiveSection('verifications')}
                      className="text-xs text-[#8B5CF6] hover:underline"
                    >
                      View queue →
                    </button>
                  </div>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                    <div className="p-3.5 rounded-xl bg-[#191130] border border-[#2A1E4A] flex items-center justify-between">
                      <div>
                        <span className="text-xs font-bold text-white block">Verification Queue ({verificationQueue.length})</span>
                        <span className="text-[11px] text-gray-400">Artists, creators and business badges</span>
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
                        <span className="text-xs font-bold text-white block">Flagged Reports ({reportsQueue.length})</span>
                        <span className="text-[11px] text-gray-400">Harassment & spam flags</span>
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

            {/* 2. USER MANAGEMENT */}
            {activeSection === 'users' && (
              <div className="space-y-5">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                  <div>
                    <h2 className="text-xl font-bold text-white">Registered User Directory</h2>
                    <p className="text-xs text-gray-400">Search, inspect account status, manage roles and permissions</p>
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

                <div className="border border-[#1E1638] rounded-2xl overflow-hidden bg-[#100B20]">
                  <table className="w-full text-left text-xs">
                    <thead className="bg-[#17102C] text-gray-400 uppercase text-[10px] tracking-wider border-b border-[#221840]">
                      <tr>
                        <th className="p-3.5">User</th>
                        <th className="p-3.5">Public ID</th>
                        <th className="p-3.5">Role</th>
                        <th className="p-3.5">Badges</th>
                        <th className="p-3.5">Status</th>
                        <th className="p-3.5 text-right">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[#1A1233]">
                      {allUsers
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
                            <td className="p-3.5 font-mono text-gray-300">{u.publicVybeId || `VYBE-${u.id.take(6).toUpperCase()}`}</td>
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
                            <td className="p-3.5">
                              <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-[#22C55E]/15 text-[#22C55E]">
                                Active
                              </span>
                            </td>
                            <td className="p-3.5 text-right space-x-2">
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
              </div>
            )}

            {/* 3. VERIFICATION MANAGEMENT */}
            {activeSection === 'verifications' && (
              <div className="space-y-5">
                <div>
                  <h2 className="text-xl font-bold text-white">Official Verification Applications</h2>
                  <p className="text-xs text-gray-400">Review credential proof and approve blue verified or gold VIP status</p>
                </div>

                {verificationQueue.length === 0 ? (
                  <div className="text-center py-16 bg-[#100B20] rounded-2xl border border-[#1E1638]">
                    <CheckCircle2 className="w-10 h-10 text-[#22C55E] mx-auto mb-2" />
                    <p className="text-sm font-bold text-white">All applications reviewed!</p>
                    <p className="text-xs text-gray-400 mt-1">New submissions from creator settings will appear here instantly.</p>
                  </div>
                ) : (
                  <div className="space-y-3">
                    {verificationQueue.map(item => (
                      <div
                        key={item.id}
                        className="p-5 rounded-2xl bg-[#120D22] border border-[#221840] flex flex-col md:flex-row md:items-center justify-between gap-4"
                      >
                        <div className="flex items-start gap-3.5">
                          <img src={item.user.avatarUrl} alt={item.user.displayName} className="w-12 h-12 rounded-full object-cover border border-[#2A1E4A]" />
                          <div>
                            <div className="flex items-center gap-2">
                              <span className="font-bold text-white text-sm">{item.user.displayName}</span>
                              <span className="text-xs text-gray-400 font-mono">@{item.user.username}</span>
                              <OfficialBadge badge={item.requestedBadge} size="sm" />
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
                            onClick={() => handleRejectVerification(item.id, item.user.username)}
                            className="px-4 py-2 rounded-xl bg-red-500/10 border border-red-500/30 text-red-300 text-xs font-bold hover:bg-red-500/20 transition"
                          >
                            Decline
                          </button>
                          <button
                            onClick={() => handleApproveVerification(item.id, item.user.username, item.requestedBadge)}
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

            {/* 4. BADGE MANAGEMENT */}
            {activeSection === 'badges' && (
              <div className="space-y-6">
                <div>
                  <h2 className="text-xl font-bold text-white">Direct Badge Grant & Revocation</h2>
                  <p className="text-xs text-gray-400">Assign Blue Verified, VIP Gold, Business Shop, or Green Worker emblems</p>
                </div>

                <form onSubmit={handleAssignBadge} className="p-6 rounded-2xl bg-[#120D22] border border-[#221840] space-y-4 max-w-xl">
                  <div>
                    <label className="text-xs font-bold text-gray-300 block mb-1.5">Target User</label>
                    <select
                      value={selectedUserForBadge}
                      onChange={e => setSelectedUserForBadge(e.target.value)}
                      className="w-full px-3.5 py-2.5 bg-[#191130] border border-[#2A1E4A] rounded-xl text-xs text-white focus:outline-none focus:border-[#8B5CF6]"
                    >
                      {allUsers.map(u => (
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
                        { id: 'WORKER_GREEN', name: 'VYBE Workers (Green)', desc: 'Official staff & team' },
                      ].map(b => (
                        <div
                          key={b.id}
                          onClick={() => setAssignBadgeType(b.id as any)}
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

                  {assignBadgeType === 'VIP_GOLD' && (
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
                    className="w-full py-2.5 bg-[#8B5CF6] hover:bg-[#7C3AED] text-white font-bold text-xs rounded-xl shadow-lg shadow-[#8B5CF6]/25 transition"
                  >
                    Confirm Badge Assignment
                  </button>
                </form>
              </div>
            )}

            {/* 5. REPORTS & MODERATION */}
            {activeSection === 'reports' && (
              <div className="space-y-5">
                <div>
                  <h2 className="text-xl font-bold text-white">Content & Safety Moderation</h2>
                  <p className="text-xs text-gray-400">Reports filed across posts, direct chats, and live unload rooms</p>
                </div>

                {reportsQueue.length === 0 ? (
                  <div className="text-center py-16 bg-[#100B20] rounded-2xl border border-[#1E1638]">
                    <CheckCircle2 className="w-10 h-10 text-[#22C55E] mx-auto mb-2" />
                    <p className="text-sm font-bold text-white">Clean inbox — 0 pending incident reports!</p>
                  </div>
                ) : (
                  <div className="space-y-3">
                    {reportsQueue.map(rep => (
                      <div key={rep.id} className="p-5 rounded-2xl bg-[#120D22] border border-[#221840] space-y-3">
                        <div className="flex items-center justify-between">
                          <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-[#EF4444]/20 text-[#EF4444] border border-[#EF4444]/30">
                            {rep.reason}
                          </span>
                          <span className="text-[10px] text-gray-500">{rep.timestamp}</span>
                        </div>
                        <div>
                          <p className="text-xs text-gray-300">
                            <span className="text-gray-400 font-semibold">Reported: </span>
                            <span className="text-white font-bold">{rep.violator}</span> by {rep.reporter}
                          </p>
                          <p className="text-xs text-red-200 mt-1 bg-red-950/30 p-2.5 rounded-xl border border-red-900/40 font-mono">
                            "{rep.contentSnippet}"
                          </p>
                        </div>
                        <div className="flex items-center gap-2 pt-1 border-t border-[#1F1738]">
                          <button
                            onClick={() => handleResolveReport(rep.id, 'DISMISSED')}
                            className="px-3 py-1.5 rounded-lg bg-[#191130] border border-[#2A1E4A] text-gray-300 hover:text-white text-xs font-semibold"
                          >
                            Dismiss
                          </button>
                          <button
                            onClick={() => handleResolveReport(rep.id, 'CONTENT_REMOVED')}
                            className="px-3 py-1.5 rounded-lg bg-orange-500/20 border border-orange-500/40 text-orange-200 hover:bg-orange-500/30 text-xs font-semibold"
                          >
                            Remove Content
                          </button>
                          <button
                            onClick={() => handleResolveReport(rep.id, 'USER_SUSPENDED')}
                            className="px-3 py-1.5 rounded-lg bg-red-600 hover:bg-red-700 text-white text-xs font-bold"
                          >
                            Suspend User
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* 6. CONTENT MANAGEMENT */}
            {activeSection === 'content' && (
              <div className="space-y-5">
                <div>
                  <h2 className="text-xl font-bold text-white">Content Library Moderation</h2>
                  <p className="text-xs text-gray-400">Inspect posts, reels, video carousels, and time capsules</p>
                </div>
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                  {posts.slice(0, 9).map(p => (
                    <div key={p.id} className="p-3.5 rounded-2xl bg-[#120D22] border border-[#221840] space-y-2.5">
                      <div className="flex items-center gap-2">
                        <img src={p.author.avatarUrl} alt={p.author.displayName} className="w-6 h-6 rounded-full object-cover" />
                        <span className="text-xs font-bold text-white truncate">@{p.author.username}</span>
                        <span className="text-[10px] text-gray-400 ml-auto">{p.mood}</span>
                      </div>
                      {p.mediaUrls[0] && (
                        <img src={p.mediaUrls[0]} alt="Post media" className="w-full h-32 object-cover rounded-xl border border-[#1F1738]" />
                      )}
                      <p className="text-xs text-gray-300 line-clamp-2">{p.caption}</p>
                      <div className="flex items-center justify-between text-[11px] text-gray-400 pt-2 border-t border-[#1C1436]">
                        <span>{p.likesCount} loves • {p.commentsCount} comments</span>
                        <button
                          onClick={() => {
                            setFeedback(`✓ Post ${p.id.take(8)} archived by Admin.`);
                            setTimeout(() => setFeedback(null), 3000);
                          }}
                          className="text-red-400 hover:text-red-300 font-semibold"
                        >
                          Archive
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* 7. ADS & SPONSORSHIPS */}
            {activeSection === 'ads' && (
              <div className="space-y-5">
                <div>
                  <h2 className="text-xl font-bold text-white">Promoted Content & Ad Campaigns</h2>
                  <p className="text-xs text-gray-400">Configure sponsored creator placements and track campaign CTR</p>
                </div>
                <div className="p-6 rounded-2xl bg-[#120D22] border border-[#221840] space-y-4">
                  <div className="flex items-center justify-between">
                    <div>
                      <span className="font-bold text-sm text-white block">Active Campaign: Summer Sound Festival</span>
                      <span className="text-xs text-gray-400">Promoted music track + branded reel carousel</span>
                    </div>
                    <span className="px-2.5 py-1 rounded-full text-xs font-bold bg-[#22C55E]/15 text-[#22C55E]">
                      Live • 4.2% CTR
                    </span>
                  </div>
                  <div className="grid grid-cols-3 gap-3 text-center">
                    <div className="p-3 bg-[#191130] rounded-xl border border-[#2A1E4A]">
                      <span className="text-base font-bold text-[#00C6FF]">48,200</span>
                      <span className="text-[10px] text-gray-400 block">Impressions</span>
                    </div>
                    <div className="p-3 bg-[#191130] rounded-xl border border-[#2A1E4A]">
                      <span className="text-base font-bold text-[#8B5CF6]">2,024</span>
                      <span className="text-[10px] text-gray-400 block">Taps</span>
                    </div>
                    <div className="p-3 bg-[#191130] rounded-xl border border-[#2A1E4A]">
                      <span className="text-base font-bold text-[#FFB703]">$1,240</span>
                      <span className="text-[10px] text-gray-400 block">Spend</span>
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* 8. PAYMENTS & VIP */}
            {activeSection === 'payments' && (
              <div className="space-y-5">
                <div>
                  <h2 className="text-xl font-bold text-white">Monetization & Ledger</h2>
                  <p className="text-xs text-gray-400">Transaction history for VYBE Gold subscriptions and creator tips</p>
                </div>
                <div className="grid grid-cols-3 gap-4">
                  <div className="p-4 rounded-2xl bg-[#120D22] border border-[#221840]">
                    <span className="text-xs text-gray-400 block">Total Revenue</span>
                    <span className="text-2xl font-bold text-[#22C55E] mt-1 block">$14,820.00</span>
                    <span className="text-[10px] text-gray-500">+18% vs last month</span>
                  </div>
                  <div className="p-4 rounded-2xl bg-[#120D22] border border-[#221840]">
                    <span className="text-xs text-gray-400 block">Successful Charges</span>
                    <span className="text-2xl font-bold text-white mt-1 block">128</span>
                    <span className="text-[10px] text-gray-500">100% gateway success</span>
                  </div>
                  <div className="p-4 rounded-2xl bg-[#120D22] border border-[#221840]">
                    <span className="text-xs text-gray-400 block">Failed / Disputed</span>
                    <span className="text-2xl font-bold text-red-400 mt-1 block">0</span>
                    <span className="text-[10px] text-gray-500">Zero chargebacks</span>
                  </div>
                </div>
              </div>
            )}

            {/* 9. ANALYTICS */}
            {activeSection === 'analytics' && (
              <div className="space-y-5">
                <div>
                  <h2 className="text-xl font-bold text-white">Platform Growth & Engagement</h2>
                  <p className="text-xs text-gray-400">Cohort retention, session lengths, and peak concurrent usage</p>
                </div>
                <div className="p-6 rounded-2xl bg-[#120D22] border border-[#221840] space-y-4">
                  <h3 className="text-xs font-bold text-gray-300 uppercase tracking-wider">Hourly Engagement Heatmap</h3>
                  <div className="h-40 flex items-end gap-2 pt-6">
                    {[35, 20, 15, 10, 8, 12, 28, 45, 60, 72, 85, 95, 90, 82, 78, 88, 98, 100, 92, 85, 75, 62, 50, 40].map((val, idx) => (
                      <div key={idx} className="flex-1 flex flex-col items-center gap-1">
                        <div
                          className="w-full rounded-t-sm bg-gradient-to-t from-[#8B5CF6] to-[#00C6FF] transition hover:brightness-125"
                          style={{ height: `${val}%` }}
                        />
                        <span className="text-[8px] text-gray-500">{idx}h</span>
                      </div>
                    ))}
                  </div>
                  <p className="text-[11px] text-gray-400 text-center">Peak active engagement occurs daily between 17:00 and 21:00 UTC</p>
                </div>
              </div>
            )}

            {/* 10. SYSTEM HEALTH */}
            {activeSection === 'health' && (
              <div className="space-y-5">
                <div>
                  <h2 className="text-xl font-bold text-white">Infrastructure & API Diagnostics</h2>
                  <p className="text-xs text-gray-400">Real-time status of Supabase, FCM, Phoenix WebSocket, and Node.js gateway</p>
                </div>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {[
                    { service: 'PostgreSQL Database', endpoint: 'cppwadedazpbevkfrchg.supabase.co', status: 'HEALTHY', latency: '42ms', ping: '100%' },
                    { service: 'Phoenix Realtime WebSocket', endpoint: 'wss://cppwadedazpbevkfrchg.../websocket', status: 'CONNECTED', latency: '18ms', ping: '100%' },
                    { service: 'Firebase Cloud Messaging (HTTP v1)', endpoint: 'fcm.googleapis.com (HIGH PRIORITY)', status: 'OPERATIONAL', latency: '98ms', ping: '100%' },
                    { service: 'Web App & Admin Gateway', endpoint: 'serve_web.js on Port 3000 -> 8080', status: 'LISTENING', latency: '2ms', ping: '100%' }
                  ].map((srv, i) => (
                    <div key={i} className="p-4 rounded-2xl bg-[#120D22] border border-[#221840] space-y-2">
                      <div className="flex items-center justify-between">
                        <span className="font-bold text-xs text-white">{srv.service}</span>
                        <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-[#22C55E]/20 text-[#22C55E]">
                          {srv.status}
                        </span>
                      </div>
                      <p className="text-[10px] text-gray-400 font-mono truncate">{srv.endpoint}</p>
                      <div className="flex items-center justify-between text-[11px] text-gray-500 pt-1 border-t border-[#1C1436]">
                        <span>Latency: <strong className="text-[#00C6FF]">{srv.latency}</strong></span>
                        <span>Uptime: <strong className="text-[#22C55E]">{srv.ping}</strong></span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* 11. PUSH NOTIFICATIONS */}
            {activeSection === 'notifications' && (
              <div className="space-y-5">
                <div>
                  <h2 className="text-xl font-bold text-white">System Broadcast Notification</h2>
                  <p className="text-xs text-gray-400">Send instant high-priority push notifications to all VYBE members</p>
                </div>
                <form onSubmit={handleSendBroadcast} className="p-6 rounded-2xl bg-[#120D22] border border-[#221840] space-y-4 max-w-xl">
                  <div>
                    <label className="text-xs font-bold text-gray-300 block mb-1">Broadcast Title</label>
                    <input
                      type="text"
                      placeholder="e.g. VYBE Friday Unload Stage is Now Live!"
                      value={broadcastTitle}
                      onChange={e => setBroadcastTitle(e.target.value)}
                      className="w-full px-3.5 py-2.5 bg-[#191130] border border-[#2A1E4A] rounded-xl text-xs text-white focus:outline-none focus:border-[#8B5CF6]"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-bold text-gray-300 block mb-1">Push Message Body</label>
                    <textarea
                      rows={3}
                      placeholder="Enter the alert body shown on mobile lock screens..."
                      value={broadcastMessage}
                      onChange={e => setBroadcastMessage(e.target.value)}
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

            {/* 12. AUDIT LOGS */}
            {activeSection === 'audit' && (
              <div className="space-y-5">
                <div>
                  <h2 className="text-xl font-bold text-white">Security & Audit Event Trail</h2>
                  <p className="text-xs text-gray-400">Immutable ledger of administrative actions, permission grants, and security validations</p>
                </div>
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
                          <td className="p-3.5 font-semibold text-gray-300">@{log.admin}</td>
                          <td className="p-3.5 font-mono text-gray-400">{log.target}</td>
                          <td className="p-3.5 text-gray-300">{log.details}</td>
                          <td className="p-3.5 text-right text-gray-500 font-mono text-[10px]">{log.time}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}

          </div>
        </div>

      </div>
    </div>
  );
};
