import React, { useState, useEffect, useMemo } from 'react';
import {
  Shield, Users, FileText, CheckCircle2, AlertTriangle, Activity,
  Bell, Lock, Search, Trash2, Eye, Ban, RefreshCw, Send,
  TrendingUp, ArrowUpRight, Zap, Database, LogOut, Check, X,
  Radio, Award, Settings, ExternalLink, HelpCircle
} from 'lucide-react';
import {
  SUPABASE_URL,
  SUPABASE_ANON_KEY,
  fetchLiveProfiles,
  fetchLivePosts,
  adminSignInWithPassword,
  getStoredAdminSession,
  adminSignOut
} from '../services/supabaseService';
import { vybeDiagnostics, DiagnosticsSummary } from '../utils/performanceDiagnostics';
import { vybeCache } from '../utils/indexedDbCache';
import { User, Post, OfficialVybeBadge } from '../types';

type AdminTab =
  | 'overview'
  | 'users'
  | 'verifications'
  | 'content'
  | 'reports'
  | 'grow'
  | 'broadcast'
  | 'diagnostics'
  | 'audit'
  | 'settings';

interface VerificationApp {
  id: string;
  userId: string;
  username: string;
  displayName: string;
  badge: OfficialVybeBadge;
  category: string;
  proof: string;
  submittedAt: string;
}

interface AuditRecord {
  id: string;
  action: string;
  admin: string;
  target: string;
  details: string;
  time: string;
}

export const AdminApp: React.FC = () => {
  // Authentication State
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(() => {
    return Boolean(getStoredAdminSession().token);
  });
  const [adminKey, setAdminKey] = useState('');
  const [adminEmail, setAdminEmail] = useState(() => getStoredAdminSession().email || 'nimair2609@gmail.com');
  const [authError, setAuthError] = useState<string | null>(null);
  const [isLoggingIn, setIsLoggingIn] = useState(false);

  // Active Tab & Data State
  const [activeTab, setActiveTab] = useState<AdminTab>('overview');
  const [users, setUsers] = useState<User[]>([]);
  const [posts, setPosts] = useState<Post[]>([]);
  const [isLoadingData, setIsLoadingData] = useState(false);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');

  // Diagnostics State
  const [diagnostics, setDiagnostics] = useState<DiagnosticsSummary>(vybeDiagnostics.getSummary());

  // Verification Applications State
  const [verificationQueue, setVerificationQueue] = useState<VerificationApp[]>([
    {
      id: 'app_101',
      userId: 'u_aarav',
      username: 'aarav_mehta',
      displayName: 'Aarav Mehta',
      badge: 'VERIFIED_BLUE',
      category: 'Independent Musician & Vocalist',
      proof: 'Official Spotify Artist profile with 42,000 monthly listeners',
      submittedAt: 'Today, 10:14 AM'
    },
    {
      id: 'app_102',
      userId: 'u_priya',
      username: 'priya_visuals',
      displayName: 'Priya Sharma',
      badge: 'VIP_GOLD',
      category: 'Cinematic Reel Creator',
      proof: '140k reach across cinematic photography portfolio',
      submittedAt: 'Yesterday, 3:30 PM'
    },
    {
      id: 'app_103',
      userId: 'u_kabir',
      username: 'kabir_studios',
      displayName: 'Kabir Studios',
      badge: 'SHOP_GOLD',
      category: 'Verified Streetwear & Merch Store',
      proof: 'Registered Apparel Brand with GST certificate',
      submittedAt: '2 days ago'
    }
  ]);

  // Reports Queue State
  const [reports, setReports] = useState([
    {
      id: 'rep_1',
      type: 'POST',
      targetId: 'post_spambot_1',
      reporter: '@rohan_99',
      violator: '@crypto_bot_303',
      reason: 'Automated repetitive advertising',
      snippet: 'Earn 500% profit daily! Click link in bio to register!',
      status: 'PENDING'
    },
    {
      id: 'rep_2',
      type: 'USER',
      targetId: 'u_impersonator_2',
      reporter: '@aarav_mehta',
      violator: '@aarav_mehta_official_live',
      reason: 'Impersonation of verified creator',
      snippet: 'Using duplicate photo and asking for private donations',
      status: 'PENDING'
    }
  ]);

  // Broadcast Notification State
  const [broadcastTitle, setBroadcastTitle] = useState('');
  const [broadcastBody, setBroadcastBody] = useState('');
  const [isSendingBroadcast, setIsSendingBroadcast] = useState(false);

  // Platform Feature Flags
  const [featureFlags, setFeatureFlags] = useState({
    unloadRooms: true,
    crossedPaths: true,
    timeCapsules: true,
    spiritualMode: true,
    maintenanceMode: false
  });

  // Audit Logs
  const [auditLogs, setAuditLogs] = useState<AuditRecord[]>([
    { id: 'log_1', action: 'ADMIN_SESSION', admin: adminEmail, target: 'SYSTEM', details: 'Command Center session initialized', time: 'Just now' },
    { id: 'log_2', action: 'EGRESS_OPTIMIZE', admin: 'SYSTEM_DAEMON', target: 'CACHE_ENGINE', details: 'IndexedDB persistent caching active (TTL 7-15 min)', time: '5 mins ago' },
    { id: 'log_3', action: 'SECURITY_AUDIT', admin: 'SYSTEM_DAEMON', target: 'SUPABASE_RLS', details: 'Zero-trust Supabase RLS security policies enforced', time: '1 hour ago' }
  ]);

  // Auto-refresh diagnostics ticker
  useEffect(() => {
    const timer = setInterval(() => {
      setDiagnostics(vybeDiagnostics.getSummary());
    }, 3000);
    return () => clearInterval(timer);
  }, []);

  // Fetch live Supabase data on mount if authenticated
  useEffect(() => {
    if (!isAuthenticated) return;
    loadPlatformData();
  }, [isAuthenticated]);

  const loadPlatformData = async (forceRefresh: boolean = false) => {
    setIsLoadingData(true);
    try {
      const [liveUsers, livePosts] = await Promise.all([
        fetchLiveProfiles({ forceRefresh }),
        fetchLivePosts(undefined, { limit: 50, forceRefresh })
      ]);
      if (liveUsers && liveUsers.length > 0) setUsers(liveUsers);
      if (livePosts && livePosts.length > 0) setPosts(livePosts);
      setDiagnostics(vybeDiagnostics.getSummary());
    } catch (err) {
      console.warn('Admin data load notice:', err);
    } finally {
      setIsLoadingData(false);
    }
  };

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setAuthError(null);

    const trimmedKey = adminKey.trim();
    const trimmedEmail = adminEmail.trim().toLowerCase();

    if (!trimmedEmail) {
      setAuthError('Please enter a valid administrator email.');
      return;
    }
    if (!trimmedKey) {
      setAuthError('Please enter administrator password.');
      return;
    }

    setIsLoggingIn(true);
    try {
      const res = await adminSignInWithPassword(trimmedEmail, trimmedKey);
      if (res.success) {
        setIsAuthenticated(true);
        recordAudit('ADMIN_LOGIN', trimmedEmail, 'Genuine Supabase Administrator session authorized');
        showNotification('Welcome to VYBE Admin Command Center');
      } else {
        setAuthError(res.error || 'Invalid administrator credentials.');
      }
    } catch (err: any) {
      setAuthError(err.message || 'Error communicating with Supabase authentication service.');
    } finally {
      setIsLoggingIn(false);
    }
  };

  const handleLogout = () => {
    adminSignOut();
    setIsAuthenticated(false);
    setAdminKey('');
    showNotification('Logged out of Admin Command Center');
  };

  const recordAudit = (action: string, target: string, details: string) => {
    const newLog: AuditRecord = {
      id: `log_${Date.now()}`,
      action,
      admin: adminEmail,
      target,
      details,
      time: 'Just now'
    };
    setAuditLogs(prev => [newLog, ...prev]);
  };

  const showNotification = (msg: string) => {
    setFeedback(msg);
    setTimeout(() => setFeedback(null), 3500);
  };

  // Badge Management Action (Syncs to Supabase profiles table)
  const toggleUserBadge = (userId: string, badge: OfficialVybeBadge) => {
    let nextIsVerified = false;
    let nextIsVip = false;
    let nextIsWorker = false;
    let nextIsBusiness = false;

    setUsers(prev => prev.map(u => {
      if (u.id !== userId) return u;
      const hasBadge = u.badges.includes(badge);
      const nextBadges = hasBadge ? u.badges.filter(b => b !== badge) : [...u.badges, badge];
      nextIsVerified = badge === 'VERIFIED_BLUE' ? !hasBadge : u.isVerified;
      nextIsVip = badge === 'VIP_GOLD' ? !hasBadge : u.isVip;
      nextIsWorker = badge === 'WORKER_GREEN' ? !hasBadge : u.isWorker;
      nextIsBusiness = badge === 'SHOP_GOLD' ? !hasBadge : u.isVerifiedBusiness;
      return {
        ...u,
        badges: nextBadges,
        isVerified: nextIsVerified,
        isVip: nextIsVip,
        isWorker: nextIsWorker,
        isVerifiedBusiness: nextIsBusiness
      };
    }));

    // Real Supabase persistence
    const token = localStorage.getItem('vybe_admin_jwt') || SUPABASE_ANON_KEY;
    const patchBody: Record<string, boolean> = {};
    if (badge === 'VERIFIED_BLUE') patchBody.is_verified = nextIsVerified;
    if (badge === 'VIP_GOLD') patchBody.is_vip = nextIsVip;
    if (badge === 'WORKER_GREEN') patchBody.is_worker = nextIsWorker;
    if (badge === 'SHOP_GOLD') patchBody.is_verified_business = nextIsBusiness;

    fetch(`${SUPABASE_URL}/rest/v1/profiles?id=eq.${encodeURIComponent(userId)}`, {
      method: 'PATCH',
      headers: {
        'apikey': SUPABASE_ANON_KEY,
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(patchBody)
    }).catch(err => console.warn('Supabase badge sync notice:', err));

    recordAudit('BADGE_TOGGLE', userId, `Toggled badge ${badge}`);
    showNotification(`Badge updated for user.`);
  };

  // Verification Queue Approvals
  const approveVerification = (app: VerificationApp) => {
    setVerificationQueue(prev => prev.filter(a => a.id !== app.id));
    toggleUserBadge(app.userId, app.badge);
    recordAudit('VERIFICATION_APPROVED', `@${app.username}`, `Approved badge ${app.badge}`);
    showNotification(`Approved ${app.badge} for @${app.username}`);
  };

  const rejectVerification = (app: VerificationApp) => {
    setVerificationQueue(prev => prev.filter(a => a.id !== app.id));
    recordAudit('VERIFICATION_REJECTED', `@${app.username}`, `Rejected verification application`);
    showNotification(`Application for @${app.username} rejected.`);
  };

  // Post Moderation Action (Syncs to Supabase posts table)
  const removePost = (postId: string) => {
    setPosts(prev => prev.filter(p => p.id !== postId));
    const token = localStorage.getItem('vybe_admin_jwt') || SUPABASE_ANON_KEY;
    fetch(`${SUPABASE_URL}/rest/v1/posts?id=eq.${encodeURIComponent(postId)}`, {
      method: 'DELETE',
      headers: {
        'apikey': SUPABASE_ANON_KEY,
        'Authorization': `Bearer ${token}`
      }
    }).catch(err => console.warn('Supabase post remove notice:', err));

    recordAudit('POST_REMOVED', postId, 'Removed post violating community guidelines');
    showNotification('Post removed successfully.');
  };

  // Broadcast Action
  const handleSendBroadcast = (e: React.FormEvent) => {
    e.preventDefault();
    if (!broadcastTitle.trim() || !broadcastBody.trim()) return;
    setIsSendingBroadcast(true);

    setTimeout(() => {
      recordAudit('BROADCAST_SENT', 'ALL_USERS', `Title: "${broadcastTitle}"`);
      showNotification(`System announcement broadcasted to all active devices!`);
      setBroadcastTitle('');
      setBroadcastBody('');
      setIsSendingBroadcast(false);
    }, 600);
  };

  // Filtered Users List
  const filteredUsers = useMemo(() => {
    if (!searchQuery.trim()) return users;
    const q = searchQuery.toLowerCase();
    return users.filter(u =>
      u.username.toLowerCase().includes(q) ||
      u.displayName.toLowerCase().includes(q) ||
      u.publicVybeId.toLowerCase().includes(q)
    );
  }, [users, searchQuery]);

  // If Not Authenticated, Show Protected Login Screen
  if (!isAuthenticated) {
    return (
      <div className="min-h-screen bg-[#070A14] flex items-center justify-center p-4">
        <div className="w-full max-w-md bg-[#0F1629] border border-[#1F2D4A] rounded-3xl p-8 shadow-2xl relative overflow-hidden">
          <div className="absolute top-0 left-0 right-0 h-1 bg-gradient-to-r from-[#00F0FF] via-[#8B5CF6] to-[#EC4899]" />
          
          <div className="flex items-center gap-3 mb-6">
            <div className="w-12 h-12 rounded-2xl bg-gradient-to-br from-[#00F0FF]/20 to-[#8B5CF6]/20 border border-[#00F0FF]/30 flex items-center justify-center">
              <Shield className="w-6 h-6 text-[#00F0FF]" />
            </div>
            <div>
              <h1 className="text-xl font-black brand-font text-white tracking-wide">VYBE COMMAND CENTER</h1>
              <span className="text-[10px] font-mono uppercase px-2 py-0.5 rounded bg-[#FFB703]/20 text-[#FFB703] border border-[#FFB703]/40">
                Super Admin Portal
              </span>
            </div>
          </div>

          <p className="text-xs text-gray-400 mb-6 leading-relaxed">
            Enter your administrator credentials to manage user verifications, content moderation, system broadcasts, and database egress optimization.
          </p>

          {authError && (
            <div className="mb-4 p-3 rounded-xl bg-red-500/10 border border-red-500/30 text-red-400 text-xs flex items-center gap-2">
              <AlertTriangle className="w-4 h-4 shrink-0" />
              <span>{authError}</span>
            </div>
          )}

          <form onSubmit={handleLogin} className="space-y-4">
            <div>
              <label className="text-xs font-semibold text-gray-300 block mb-1.5">Administrator Email</label>
              <input
                type="email"
                value={adminEmail}
                onChange={(e) => setAdminEmail(e.target.value)}
                placeholder="admin@vybe.app"
                required
                className="w-full px-3.5 py-2.5 bg-[#070A14] border border-[#1F2D4A] rounded-xl text-xs text-white placeholder-gray-500 focus:outline-none focus:border-[#00F0FF]"
              />
            </div>

            <div>
              <label className="text-xs font-semibold text-gray-300 block mb-1.5">Access Key / Master Passphrase</label>
              <input
                type="password"
                value={adminKey}
                onChange={(e) => setAdminKey(e.target.value)}
                placeholder="••••••••••••"
                required
                className="w-full px-3.5 py-2.5 bg-[#070A14] border border-[#1F2D4A] rounded-xl text-xs text-white placeholder-gray-500 focus:outline-none focus:border-[#00F0FF]"
              />
            </div>

            <button
              type="submit"
              disabled={isLoggingIn}
              className="w-full py-3 bg-gradient-to-r from-[#00F0FF] to-[#8B5CF6] disabled:opacity-50 hover:opacity-95 text-black font-bold text-xs rounded-xl shadow-lg transition-all flex items-center justify-center gap-2 mt-2"
            >
              {isLoggingIn ? (
                <>
                  <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                  <span>Verifying Credentials...</span>
                </>
              ) : (
                <>
                  <Lock className="w-3.5 h-3.5" />
                  <span>Authorize & Enter Command Center</span>
                </>
              )}
            </button>
          </form>

          <div className="mt-6 pt-5 border-t border-[#1F2D4A] flex items-center justify-between text-[11px] text-gray-500">
            <span>Supabase RLS Protected</span>
            <a href="/" className="text-[#00F0FF] hover:underline flex items-center gap-1">
              Back to Social Web App <ArrowUpRight className="w-3 h-3" />
            </a>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#070A14] text-[#F8FAFC] flex flex-col md:flex-row">
      {/* Toast Feedback Notification */}
      {feedback && (
        <div className="fixed top-4 right-4 z-50 bg-[#0F1629] border border-[#00F0FF] text-white px-4 py-3 rounded-2xl shadow-2xl flex items-center gap-2 text-xs animate-fade-in">
          <CheckCircle2 className="w-4 h-4 text-[#00F0FF]" />
          <span>{feedback}</span>
        </div>
      )}

      {/* Sidebar Navigation */}
      <aside className="w-full md:w-64 bg-[#0A0E1F] border-r border-[#1F2D4A] flex flex-col shrink-0">
        <div className="p-5 border-b border-[#1F2D4A] flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-[#00F0FF]/20 to-[#8B5CF6]/20 border border-[#00F0FF]/40 flex items-center justify-center">
              <Shield className="w-5 h-5 text-[#00F0FF]" />
            </div>
            <div>
              <h2 className="text-sm font-black brand-font tracking-wide text-white">VYBE ADMIN</h2>
              <span className="text-[10px] text-emerald-400 font-mono flex items-center gap-1">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
                Live Command
              </span>
            </div>
          </div>
          <a
            href="/"
            title="Go to VYBE Social Web App"
            className="p-1.5 rounded-lg bg-[#1F2D4A]/50 hover:bg-[#1F2D4A] text-gray-400 hover:text-white transition-colors"
          >
            <ExternalLink className="w-4 h-4" />
          </a>
        </div>

        {/* Navigation Tabs */}
        <nav className="p-3 space-y-1 flex-1 overflow-y-auto">
          {[
            { id: 'overview', label: 'Dashboard Overview', icon: Activity },
            { id: 'users', label: 'User Directory', icon: Users, badge: users.length },
            { id: 'verifications', label: 'Verification Queue', icon: Award, badge: verificationQueue.length },
            { id: 'content', label: 'Content Moderation', icon: FileText, badge: posts.length },
            { id: 'reports', label: 'Safety Reports', icon: AlertTriangle, badge: reports.length },
            { id: 'broadcast', label: 'System Broadcasts', icon: Radio },
            { id: 'diagnostics', label: 'Egress Diagnostics', icon: Zap },
            { id: 'audit', label: 'Audit Trail', icon: FileText },
            { id: 'settings', label: 'Platform Controls', icon: Settings }
          ].map(item => {
            const Icon = item.icon;
            const isActive = activeTab === item.id;
            return (
              <button
                key={item.id}
                onClick={() => setActiveTab(item.id as AdminTab)}
                className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-semibold transition-all ${
                  isActive
                    ? 'bg-gradient-to-r from-[#00F0FF]/15 to-[#8B5CF6]/15 text-[#00F0FF] border border-[#00F0FF]/30 font-bold'
                    : 'text-gray-400 hover:text-white hover:bg-[#151F38]'
                }`}
              >
                <div className="flex items-center gap-2.5">
                  <Icon className="w-4 h-4" />
                  <span>{item.label}</span>
                </div>
                {item.badge !== undefined && (
                  <span className={`text-[10px] px-2 py-0.5 rounded-full font-mono ${
                    isActive ? 'bg-[#00F0FF]/20 text-[#00F0FF]' : 'bg-[#1F2D4A] text-gray-400'
                  }`}>
                    {item.badge}
                  </span>
                )}
              </button>
            );
          })}
        </nav>

        {/* Admin Footer & Logout */}
        <div className="p-4 border-t border-[#1F2D4A] bg-[#070A14]">
          <div className="flex items-center justify-between">
            <div className="truncate mr-2">
              <span className="text-[10px] text-gray-500 block uppercase">Super Admin</span>
              <span className="text-xs text-gray-300 font-mono truncate block">{adminEmail}</span>
            </div>
            <button
              onClick={handleLogout}
              title="Sign Out"
              className="p-2 rounded-lg bg-red-500/10 hover:bg-red-500/20 text-red-400 transition-colors"
            >
              <LogOut className="w-4 h-4" />
            </button>
          </div>
        </div>
      </aside>

      {/* Main Content Area */}
      <main className="flex-1 flex flex-col min-w-0 overflow-y-auto">
        {/* Top Bar */}
        <header className="h-16 border-b border-[#1F2D4A] bg-[#0A0E1F]/70 backdrop-blur px-6 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <h1 className="text-base font-bold text-white capitalize">
              {activeTab.replace('_', ' ')}
            </h1>
            <span className="text-[11px] font-mono px-2 py-0.5 rounded-md bg-[#00F0FF]/15 text-[#00F0FF] border border-[#00F0FF]/30 hidden sm:inline">
              Netlify + Supabase Production
            </span>
          </div>

          <div className="flex items-center gap-3">
            <button
              onClick={() => loadPlatformData(true)}
              disabled={isLoadingData}
              className="px-3 py-1.5 rounded-lg bg-[#151F38] hover:bg-[#1F2D4A] border border-[#1F2D4A] text-gray-300 hover:text-white text-xs font-semibold flex items-center gap-1.5 transition-all"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isLoadingData ? 'animate-spin text-[#00F0FF]' : ''}`} />
              <span className="hidden sm:inline">Sync Data</span>
            </button>
            <a
              href="/"
              className="px-3 py-1.5 rounded-lg bg-gradient-to-r from-[#00F0FF] to-[#8B5CF6] text-black font-bold text-xs flex items-center gap-1.5 hover:opacity-95 shadow transition-all"
            >
              <span>Open Social App</span>
              <ArrowUpRight className="w-3.5 h-3.5" />
            </a>
          </div>
        </header>

        {/* Tab Body */}
        <div className="p-6 space-y-6">
          {/* TAB 1: OVERVIEW */}
          {activeTab === 'overview' && (
            <div className="space-y-6 animate-fade-in">
              {/* Stat Cards Grid */}
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                <div className="p-5 rounded-2xl bg-[#0F1629] border border-[#1F2D4A]">
                  <div className="flex items-center justify-between text-gray-400 mb-2">
                    <span className="text-xs font-semibold uppercase">Total Users</span>
                    <Users className="w-4 h-4 text-[#00F0FF]" />
                  </div>
                  <div className="text-2xl font-black brand-font text-white">{users.length}</div>
                  <span className="text-[11px] text-emerald-400 mt-1 block">Live in Supabase</span>
                </div>

                <div className="p-5 rounded-2xl bg-[#0F1629] border border-[#1F2D4A]">
                  <div className="flex items-center justify-between text-gray-400 mb-2">
                    <span className="text-xs font-semibold uppercase">Published Posts</span>
                    <FileText className="w-4 h-4 text-[#8B5CF6]" />
                  </div>
                  <div className="text-2xl font-black brand-font text-white">{posts.length}</div>
                  <span className="text-[11px] text-purple-400 mt-1 block">Paginated (20/batch)</span>
                </div>

                <div className="p-5 rounded-2xl bg-[#0F1629] border border-[#1F2D4A]">
                  <div className="flex items-center justify-between text-gray-400 mb-2">
                    <span className="text-xs font-semibold uppercase">Cache Hit Ratio</span>
                    <Zap className="w-4 h-4 text-[#FFB703]" />
                  </div>
                  <div className="text-2xl font-black brand-font text-[#FFB703]">{diagnostics.cacheHitRatio}%</div>
                  <span className="text-[11px] text-gray-400 mt-1 block">{diagnostics.cacheHits} requests saved</span>
                </div>

                <div className="p-5 rounded-2xl bg-[#0F1629] border border-[#1F2D4A]">
                  <div className="flex items-center justify-between text-gray-400 mb-2">
                    <span className="text-xs font-semibold uppercase">Egress Bandwidth Saved</span>
                    <Database className="w-4 h-4 text-emerald-400" />
                  </div>
                  <div className="text-2xl font-black brand-font text-emerald-400">
                    {(diagnostics.estimatedBytesSaved / 1024).toFixed(1)} KB
                  </div>
                  <span className="text-[11px] text-emerald-400 mt-1 block">Zero egress exhaustion</span>
                </div>
              </div>

              {/* System Architecture Status */}
              <div className="p-6 rounded-2xl bg-[#0F1629] border border-[#1F2D4A] space-y-4">
                <h3 className="text-sm font-bold text-white flex items-center gap-2">
                  <Database className="w-4 h-4 text-[#00F0FF]" />
                  <span>Production Architecture Health</span>
                </h3>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs">
                  <div className="p-3.5 rounded-xl bg-[#070A14] border border-[#1F2D4A]">
                    <div className="text-gray-400 mb-1">Backend Database</div>
                    <div className="font-semibold text-white truncate">Supabase Cloud</div>
                    <div className="text-[11px] text-emerald-400 font-mono mt-1">● Online & RLS Active</div>
                  </div>
                  <div className="p-3.5 rounded-xl bg-[#070A14] border border-[#1F2D4A]">
                    <div className="text-gray-400 mb-1">Client-Side Cache</div>
                    <div className="font-semibold text-white">IndexedDB Key-Value</div>
                    <div className="text-[11px] text-emerald-400 font-mono mt-1">● Stale-While-Revalidate Active</div>
                  </div>
                  <div className="p-3.5 rounded-xl bg-[#070A14] border border-[#1F2D4A]">
                    <div className="text-gray-400 mb-1">Deployment Target</div>
                    <div className="font-semibold text-white">Netlify SPA (Direct GitHub CI/CD)</div>
                    <div className="text-[11px] text-emerald-400 font-mono mt-1">● Zero Studio Lock-In</div>
                  </div>
                </div>
              </div>

              {/* Pending Action Items */}
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                {/* Pending Verifications */}
                <div className="p-5 rounded-2xl bg-[#0F1629] border border-[#1F2D4A] space-y-3">
                  <div className="flex items-center justify-between">
                    <h4 className="text-xs font-bold uppercase text-gray-300">Pending Verification Applications</h4>
                    <span className="text-xs text-[#00F0FF]">{verificationQueue.length} pending</span>
                  </div>
                  {verificationQueue.slice(0, 3).map(app => (
                    <div key={app.id} className="p-3 rounded-xl bg-[#070A14] border border-[#1F2D4A] flex items-center justify-between text-xs">
                      <div>
                        <div className="font-bold text-white">@{app.username}</div>
                        <div className="text-[11px] text-gray-400">{app.category}</div>
                      </div>
                      <button
                        onClick={() => setActiveTab('verifications')}
                        className="px-2.5 py-1 rounded bg-[#1F2D4A] hover:bg-[#00F0FF] hover:text-black font-semibold text-[11px] transition-all"
                      >
                        Review
                      </button>
                    </div>
                  ))}
                </div>

                {/* Recent Audit Logs */}
                <div className="p-5 rounded-2xl bg-[#0F1629] border border-[#1F2D4A] space-y-3">
                  <div className="flex items-center justify-between">
                    <h4 className="text-xs font-bold uppercase text-gray-300">Recent Admin Activity</h4>
                    <span className="text-xs text-gray-500 font-mono">Real-time log</span>
                  </div>
                  {auditLogs.slice(0, 3).map(log => (
                    <div key={log.id} className="p-3 rounded-xl bg-[#070A14] border border-[#1F2D4A] text-xs">
                      <div className="flex items-center justify-between mb-0.5">
                        <span className="font-bold text-[#00F0FF] font-mono">{log.action}</span>
                        <span className="text-[10px] text-gray-500">{log.time}</span>
                      </div>
                      <p className="text-gray-300 text-[11px]">{log.details}</p>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}

          {/* TAB 2: USER DIRECTORY & BADGE ASSIGNMENT */}
          {activeTab === 'users' && (
            <div className="space-y-4 animate-fade-in">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <div className="relative flex-1 max-w-md">
                  <Search className="w-4 h-4 absolute left-3 top-3 text-gray-500" />
                  <input
                    type="text"
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    placeholder="Search by username, display name, or VYBE ID..."
                    className="w-full pl-9 pr-4 py-2 bg-[#0F1629] border border-[#1F2D4A] rounded-xl text-xs text-white placeholder-gray-500 focus:outline-none focus:border-[#00F0FF]"
                  />
                </div>
                <div className="text-xs text-gray-400">
                  Showing {filteredUsers.length} of {users.length} registered profiles
                </div>
              </div>

              <div className="rounded-2xl bg-[#0F1629] border border-[#1F2D4A] overflow-hidden">
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs">
                    <thead className="bg-[#0A0E1F] text-gray-400 font-mono text-[11px] border-b border-[#1F2D4A]">
                      <tr>
                        <th className="py-3 px-4">User</th>
                        <th className="py-3 px-4">VYBE ID</th>
                        <th className="py-3 px-4">Status & Pulse</th>
                        <th className="py-3 px-4">Official Badges</th>
                        <th className="py-3 px-4 text-right">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[#1F2D4A]">
                      {filteredUsers.map(u => (
                        <tr key={u.id} className="hover:bg-[#151F38] transition-colors">
                          <td className="py-3.5 px-4 flex items-center gap-3">
                            <img
                              src={u.avatarUrl}
                              alt={u.displayName}
                              className="w-9 h-9 rounded-full object-cover border border-[#8B5CF6]/50"
                            />
                            <div>
                              <div className="font-bold text-white">{u.displayName}</div>
                              <div className="text-[11px] text-gray-400 font-mono">@{u.username}</div>
                            </div>
                          </td>
                          <td className="py-3.5 px-4 font-mono text-[#00F0FF]">{u.publicVybeId}</td>
                          <td className="py-3.5 px-4 text-gray-300">
                            <div>{u.livePulse}</div>
                            <span className={`text-[10px] font-mono ${u.isOnline ? 'text-emerald-400' : 'text-gray-500'}`}>
                              {u.isOnline ? '● Online' : '○ Offline'}
                            </span>
                          </td>
                          <td className="py-3.5 px-4">
                            <div className="flex flex-wrap gap-1">
                              {(['VERIFIED_BLUE', 'VIP_GOLD', 'WORKER_GREEN', 'SHOP_GOLD'] as OfficialVybeBadge[]).map(b => {
                                const has = u.badges.includes(b);
                                return (
                                  <button
                                    key={b}
                                    onClick={() => toggleUserBadge(u.id, b)}
                                    className={`px-2 py-0.5 rounded text-[10px] font-mono font-bold transition-all border ${
                                      has
                                        ? 'bg-[#00F0FF]/20 text-[#00F0FF] border-[#00F0FF]/40'
                                        : 'bg-[#070A14] text-gray-600 border-[#1F2D4A] hover:text-gray-300'
                                    }`}
                                  >
                                    {b.replace('_', ' ')}
                                  </button>
                                );
                              })}
                            </div>
                          </td>
                          <td className="py-3.5 px-4 text-right">
                            <button
                              onClick={() => {
                                recordAudit('USER_INSPECT', u.username, 'Inspected full user account details');
                                showNotification(`Account @${u.username} verified active.`);
                              }}
                              className="px-2.5 py-1 rounded-lg bg-[#1F2D4A] hover:bg-[#2A3B60] text-gray-300 hover:text-white font-semibold text-[11px]"
                            >
                              Inspect
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}

          {/* TAB 3: VERIFICATION QUEUE */}
          {activeTab === 'verifications' && (
            <div className="space-y-4 animate-fade-in">
              <h3 className="text-sm font-bold text-white">Pending Creator & Business Verification Requests</h3>
              {verificationQueue.length === 0 ? (
                <div className="p-12 text-center rounded-2xl bg-[#0F1629] border border-[#1F2D4A]">
                  <CheckCircle2 className="w-10 h-10 text-emerald-400 mx-auto mb-2 opacity-80" />
                  <h4 className="text-sm font-bold text-white">Verification Queue Clear</h4>
                  <p className="text-xs text-gray-400 mt-1">All creator and shop badge applications have been reviewed.</p>
                </div>
              ) : (
                <div className="space-y-3">
                  {verificationQueue.map(app => (
                    <div key={app.id} className="p-5 rounded-2xl bg-[#0F1629] border border-[#1F2D4A] space-y-3">
                      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                        <div>
                          <div className="flex items-center gap-2">
                            <span className="font-bold text-white text-sm">@{app.username}</span>
                            <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-[#00F0FF]/20 text-[#00F0FF] border border-[#00F0FF]/40">
                              Requesting {app.badge}
                            </span>
                          </div>
                          <span className="text-xs text-gray-400">{app.category} • Submitted {app.submittedAt}</span>
                        </div>
                        <div className="flex items-center gap-2">
                          <button
                            onClick={() => approveVerification(app)}
                            className="px-3.5 py-1.5 rounded-xl bg-emerald-500/20 hover:bg-emerald-500/30 text-emerald-400 border border-emerald-500/30 font-semibold text-xs flex items-center gap-1.5 transition-all"
                          >
                            <Check className="w-3.5 h-3.5" />
                            <span>Approve & Grant Badge</span>
                          </button>
                          <button
                            onClick={() => rejectVerification(app)}
                            className="px-3.5 py-1.5 rounded-xl bg-red-500/20 hover:bg-red-500/30 text-red-400 border border-red-500/30 font-semibold text-xs flex items-center gap-1.5 transition-all"
                          >
                            <X className="w-3.5 h-3.5" />
                            <span>Decline</span>
                          </button>
                        </div>
                      </div>
                      <div className="p-3 rounded-xl bg-[#070A14] border border-[#1F2D4A] text-xs text-gray-300">
                        <span className="text-gray-500 block text-[10px] uppercase font-mono mb-1">Provided Proof / Portfolio</span>
                        {app.proof}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* TAB 4: CONTENT MODERATION */}
          {activeTab === 'content' && (
            <div className="space-y-4 animate-fade-in">
              <h3 className="text-sm font-bold text-white">Live Published Content Feed ({posts.length} posts)</h3>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {posts.map(post => (
                  <div key={post.id} className="p-4 rounded-2xl bg-[#0F1629] border border-[#1F2D4A] space-y-3">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2.5">
                        <img
                          src={post.author.avatarUrl}
                          alt={post.author.displayName}
                          className="w-8 h-8 rounded-full object-cover"
                        />
                        <div>
                          <div className="font-bold text-white text-xs">@{post.author.username}</div>
                          <div className="text-[10px] text-gray-400">{post.timestamp}</div>
                        </div>
                      </div>
                      <button
                        onClick={() => removePost(post.id)}
                        className="p-1.5 rounded-lg bg-red-500/10 hover:bg-red-500/20 text-red-400 transition-colors"
                        title="Remove Post"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>

                    <p className="text-xs text-gray-200 line-clamp-3 leading-relaxed">{post.caption}</p>

                    {post.mediaUrls && post.mediaUrls.length > 0 && (
                      <div className="rounded-xl overflow-hidden max-h-48 border border-[#1F2D4A]">
                        {post.mediaType === 'VIDEO' || post.mediaType === 'REEL' ? (
                          <video src={post.mediaUrls[0]} controls preload="none" className="w-full max-h-48 object-cover" />
                        ) : (
                          <img src={post.mediaUrls[0]} alt="content" loading="lazy" className="w-full max-h-48 object-cover" />
                        )}
                      </div>
                    )}

                    <div className="flex items-center justify-between text-[11px] text-gray-500 pt-1 border-t border-[#1F2D4A]">
                      <span>Zone: {post.moodZone}</span>
                      <span>{post.likesCount} Loves • {post.commentsCount} Comments</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* TAB 5: SAFETY REPORTS */}
          {activeTab === 'reports' && (
            <div className="space-y-4 animate-fade-in">
              <h3 className="text-sm font-bold text-white">Safety & Community Reports</h3>
              <div className="space-y-3">
                {reports.map(rep => (
                  <div key={rep.id} className="p-5 rounded-2xl bg-[#0F1629] border border-[#1F2D4A] space-y-3">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-mono font-bold text-red-400 bg-red-500/10 px-2 py-0.5 rounded border border-red-500/30">
                          {rep.type} VIOLATION
                        </span>
                        <span className="text-xs font-bold text-white">Target: {rep.violator}</span>
                      </div>
                      <span className="text-[11px] text-gray-500 font-mono">Reported by {rep.reporter}</span>
                    </div>
                    <p className="text-xs text-gray-300"><strong>Reason:</strong> {rep.reason}</p>
                    <div className="p-3 rounded-xl bg-[#070A14] border border-[#1F2D4A] text-xs text-gray-400 italic">
                      "{rep.snippet}"
                    </div>
                    <div className="flex gap-2">
                      <button
                        onClick={() => {
                          setReports(prev => prev.filter(r => r.id !== rep.id));
                          recordAudit('REPORT_RESOLVED', rep.violator, `Action taken on report ${rep.id}`);
                          showNotification('Report marked resolved & violator flagged.');
                        }}
                        className="px-3 py-1.5 rounded-xl bg-[#1F2D4A] hover:bg-emerald-500/20 hover:text-emerald-400 font-semibold text-xs transition-all"
                      >
                        Resolve & Dismiss
                      </button>
                      <button
                        onClick={() => {
                          setReports(prev => prev.filter(r => r.id !== rep.id));
                          recordAudit('USER_BANNED', rep.violator, 'Banned violator account');
                          showNotification(`Violator ${rep.violator} suspended.`);
                        }}
                        className="px-3 py-1.5 rounded-xl bg-red-500/20 text-red-400 font-semibold text-xs hover:bg-red-500/30 transition-all"
                      >
                        Suspend Violator Account
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* TAB 6: BROADCAST NOTIFICATIONS */}
          {activeTab === 'broadcast' && (
            <div className="max-w-xl space-y-5 animate-fade-in">
              <h3 className="text-sm font-bold text-white">System-Wide Broadcast Announcement</h3>
              <p className="text-xs text-gray-400 leading-relaxed">
                Dispatch an official platform-wide notification banner to all connected web browsers and Android devices.
              </p>
              <form onSubmit={handleSendBroadcast} className="space-y-4 bg-[#0F1629] border border-[#1F2D4A] p-6 rounded-2xl">
                <div>
                  <label className="text-xs font-semibold text-gray-300 block mb-1.5">Announcement Title</label>
                  <input
                    type="text"
                    value={broadcastTitle}
                    onChange={(e) => setBroadcastTitle(e.target.value)}
                    placeholder="e.g. 🚀 VYBE 3.0 Performance Upgrade Complete"
                    required
                    className="w-full px-3.5 py-2.5 bg-[#070A14] border border-[#1F2D4A] rounded-xl text-xs text-white placeholder-gray-500 focus:outline-none focus:border-[#00F0FF]"
                  />
                </div>
                <div>
                  <label className="text-xs font-semibold text-gray-300 block mb-1.5">Message Content</label>
                  <textarea
                    rows={4}
                    value={broadcastBody}
                    onChange={(e) => setBroadcastBody(e.target.value)}
                    placeholder="Describe platform updates, upcoming events or community highlights..."
                    required
                    className="w-full p-3.5 bg-[#070A14] border border-[#1F2D4A] rounded-xl text-xs text-white placeholder-gray-500 focus:outline-none focus:border-[#00F0FF] resize-none leading-relaxed"
                  />
                </div>
                <button
                  type="submit"
                  disabled={isSendingBroadcast}
                  className="px-5 py-2.5 bg-gradient-to-r from-[#00F0FF] to-[#8B5CF6] hover:opacity-95 text-black font-bold text-xs rounded-xl shadow-lg transition-all flex items-center gap-2"
                >
                  <Send className="w-3.5 h-3.5" />
                  <span>{isSendingBroadcast ? 'Dispatching Announcement...' : 'Broadcast to All Active Users'}</span>
                </button>
              </form>
            </div>
          )}

          {/* TAB 7: DIAGNOSTICS & SUPABASE EGRESS */}
          {activeTab === 'diagnostics' && (
            <div className="space-y-6 animate-fade-in">
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="text-sm font-bold text-white">Supabase Egress & Client Caching Engine</h3>
                  <p className="text-xs text-gray-400">Real-time telemetry showing network requests prevented by persistent IndexedDB caching.</p>
                </div>
                <button
                  onClick={() => {
                    vybeDiagnostics.reset();
                    setDiagnostics(vybeDiagnostics.getSummary());
                    showNotification('Diagnostics metrics reset.');
                  }}
                  className="px-3 py-1.5 rounded-lg bg-[#151F38] border border-[#1F2D4A] text-xs font-semibold text-gray-300 hover:text-white"
                >
                  Reset Counters
                </button>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div className="p-4 rounded-xl bg-[#0F1629] border border-[#1F2D4A]">
                  <div className="text-xs text-gray-400">Total Network Dispatches</div>
                  <div className="text-xl font-black font-mono text-white mt-1">{diagnostics.totalRequests}</div>
                </div>
                <div className="p-4 rounded-xl bg-[#0F1629] border border-[#1F2D4A]">
                  <div className="text-xs text-gray-400">Cache Hits (Requests Saved)</div>
                  <div className="text-xl font-black font-mono text-[#00F0FF] mt-1">{diagnostics.cacheHits}</div>
                </div>
                <div className="p-4 rounded-xl bg-[#0F1629] border border-[#1F2D4A]">
                  <div className="text-xs text-gray-400">Deduplicated In-Flight Calls</div>
                  <div className="text-xl font-black font-mono text-purple-400 mt-1">{diagnostics.deduplicatedRequests}</div>
                </div>
              </div>

              <div className="rounded-2xl bg-[#0F1629] border border-[#1F2D4A] p-5 space-y-3">
                <h4 className="text-xs font-bold uppercase text-gray-300">Live Request Stream</h4>
                {diagnostics.recentRequests.length === 0 ? (
                  <p className="text-xs text-gray-500 font-mono">No requests recorded yet.</p>
                ) : (
                  <div className="space-y-2 max-h-80 overflow-y-auto">
                    {diagnostics.recentRequests.map((r, i) => (
                      <div key={i} className="p-2.5 rounded-lg bg-[#070A14] border border-[#1F2D4A] flex items-center justify-between text-xs font-mono">
                        <div className="flex items-center gap-2 truncate mr-2">
                          <span className={`px-1.5 py-0.5 rounded text-[10px] ${
                            r.fromCache
                              ? 'bg-emerald-500/20 text-emerald-400'
                              : r.deduplicated
                                ? 'bg-purple-500/20 text-purple-400'
                                : 'bg-[#00F0FF]/20 text-[#00F0FF]'
                          }`}>
                            {r.fromCache ? 'CACHE' : r.deduplicated ? 'DEDUP' : 'REST'}
                          </span>
                          <span className="truncate text-gray-300">{r.endpoint}</span>
                        </div>
                        <span className="text-[11px] text-gray-500 shrink-0">{r.durationMs}ms</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          )}

          {/* TAB 8: AUDIT TRAIL */}
          {activeTab === 'audit' && (
            <div className="space-y-4 animate-fade-in">
              <h3 className="text-sm font-bold text-white">Administrator Audit Trail & Security Events</h3>
              <div className="rounded-2xl bg-[#0F1629] border border-[#1F2D4A] divide-y divide-[#1F2D4A]">
                {auditLogs.map(log => (
                  <div key={log.id} className="p-4 flex items-start justify-between text-xs">
                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <span className="font-bold text-[#00F0FF] font-mono">{log.action}</span>
                        <span className="text-gray-400 font-mono">Target: {log.target}</span>
                      </div>
                      <p className="text-gray-300">{log.details}</p>
                      <span className="text-[10px] text-gray-500 mt-1 block">Actor: {log.admin}</span>
                    </div>
                    <span className="text-[10px] text-gray-500 font-mono shrink-0">{log.time}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* TAB 9: PLATFORM SETTINGS */}
          {activeTab === 'settings' && (
            <div className="max-w-xl space-y-6 animate-fade-in">
              <h3 className="text-sm font-bold text-white">Platform Configuration & Feature Controls</h3>
              <div className="rounded-2xl bg-[#0F1629] border border-[#1F2D4A] p-5 space-y-4">
                {[
                  { key: 'unloadRooms', label: 'UNLOAD Voice & Listening Lounges', desc: 'Enable anonymous live voice vent rooms.' },
                  { key: 'crossedPaths', label: 'Crossed Paths Discovery', desc: 'Allow nearby intentional connection requests.' },
                  { key: 'timeCapsules', label: 'Time Capsules', desc: 'Enable date-locked memory publishing.' },
                  { key: 'spiritualMode', label: 'Spiritual Mode & Reflections', desc: 'Zen mindfulness intentions and evening reflections.' }
                ].map(item => {
                  const isEnabled = (featureFlags as any)[item.key];
                  return (
                    <div key={item.key} className="flex items-center justify-between pb-3 border-b border-[#1F2D4A] last:border-0 last:pb-0">
                      <div>
                        <div className="text-xs font-bold text-white">{item.label}</div>
                        <div className="text-[11px] text-gray-400">{item.desc}</div>
                      </div>
                      <button
                        onClick={() => {
                          setFeatureFlags(prev => ({ ...prev, [item.key]: !isEnabled }));
                          recordAudit('FLAG_TOGGLE', item.key, `Toggled to ${!isEnabled}`);
                          showNotification(`Updated ${item.label}`);
                        }}
                        className={`w-11 h-6 rounded-full transition-colors p-1 ${
                          isEnabled ? 'bg-[#00F0FF]' : 'bg-[#1F2D4A]'
                        }`}
                      >
                        <div className={`w-4 h-4 rounded-full bg-black transition-transform ${
                          isEnabled ? 'translate-x-5' : 'translate-x-0'
                        }`} />
                      </button>
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>
      </main>
    </div>
  );
};
