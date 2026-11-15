import React, { useState, useEffect, useCallback, useMemo } from 'react';
import {
  Search, Users, Briefcase, Eye, Check, X, UserPlus, Sparkles, Loader2, ShieldCheck
} from 'lucide-react';
import { useVybe } from '../../context/VybeContext';
import { VybeAvatar } from '../../components/OptimizedImage';
import { VybeEmptyState, VybeUnderlineTabs } from '../../components/VybeDesignSystem';
import { Community, FieldVisibility, GrowProfile, GrowRequest, User } from '../../types';
import {
  fetchGrowRequests,
  fetchVisibleGrowProfileRpc,
  respondGrowRequest,
  sendGrowRequest,
  upsertGrowProfile
} from '../../services/growService';
import { CircleWorkspaceModal } from '../CircleWorkspaceModal';

export const GrowNetworkTab: React.FC = () => {
  const { currentUser, allUsers, communities, toggleJoinCommunity, openUserProfile, requireAuth } = useVybe();

  const [subTab, setSubTab] = useState<'PEOPLE' | 'REQUESTS' | 'CIRCLES' | 'MY_GROW_PROFILE'>('PEOPLE');
  const [query, setQuery] = useState('');
  const [requests, setRequests] = useState<GrowRequest[]>([]);
  const [loadingReq, setLoadingReq] = useState(false);
  const [activeCircle, setActiveCircle] = useState<Community | null>(null);
  const [peerGrowModal, setPeerGrowModal] = useState<{ user: User; profile: GrowProfile | null } | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);

  // My GROW Profile state with per-section visibility
  const [myProfile, setMyProfile] = useState<GrowProfile>({
    userId: currentUser?.id || '',
    headline: '',
    currentRole: '',
    companyOrOrg: '',
    industry: '',
    location: '',
    bioProfessional: '',
    skills: [],
    interests: [],
    careerGoals: '',
    experience: [],
    education: [],
    certifications: [],
    languages: [],
    achievements: [],
    portfolioLinks: [],
    availabilityStatus: 'OPEN_TO_NETWORK',
    availabilityModes: ['COLLABORATE'],
    visibilityRole: 'PUBLIC',
    visibilityCompany: 'PUBLIC',
    visibilityLocation: 'PUBLIC',
    visibilityEducation: 'PUBLIC',
    visibilityCertifications: 'PUBLIC',
    visibilityPortfolio: 'PUBLIC',
    visibilityExperience: 'PUBLIC',
    visibilityGoals: 'CONNECTIONS'
  });
  const [skillsDraft, setSkillsDraft] = useState('');
  const [savingProfile, setSavingProfile] = useState(false);

  const loadNetworkData = useCallback(async () => {
    if (!currentUser?.id) return;
    setLoadingReq(true);
    const [reqs, mine] = await Promise.all([
      fetchGrowRequests(currentUser.id),
      fetchVisibleGrowProfileRpc(currentUser.id)
    ]);
    setRequests(reqs);
    if (mine) {
      setMyProfile(mine);
      setSkillsDraft(mine.skills.join(', '));
    }
    setLoadingReq(false);
  }, [currentUser?.id]);

  useEffect(() => {
    loadNetworkData();
  }, [loadNetworkData]);

  const filteredUsers = useMemo(() => {
    const q = query.trim().toLowerCase();
    return allUsers.filter(u => {
      if (u.id === currentUser?.id) return false;
      if (!q) return true;
      return (
        u.displayName.toLowerCase().includes(q) ||
        u.username.toLowerCase().includes(q) ||
        (u.bio || '').toLowerCase().includes(q)
      );
    });
  }, [allUsers, currentUser?.id, query]);

  const handleInspectGrowProfile = async (peer: User) => {
    const gp = await fetchVisibleGrowProfileRpc(peer.id);
    setPeerGrowModal({ user: peer, profile: gp });
  };

  const handleSendGrowReq = async (peerId: string, requestType: 'CONNECT' | 'MENTORSHIP') => {
    if (!requireAuth('Sign in to send GROW requests.') || !currentUser) return;
    const res = await sendGrowRequest({
      senderId: currentUser.id,
      recipientId: peerId,
      requestType,
      message: requestType === 'MENTORSHIP' ? 'Would love mentorship guidance on VYBE GROW.' : 'Let us connect on VYBE GROW!'
    });
    setFeedback(res.ok ? `${requestType === 'MENTORSHIP' ? 'Mentorship' : 'Connection'} request sent.` : res.error || 'Failed.');
    if (res.ok) await loadNetworkData();
  };

  const handleSaveMyGrowProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!requireAuth('Sign in to save your GROW profile.') || !currentUser) return;
    setSavingProfile(true);
    const res = await upsertGrowProfile({
      ...myProfile,
      userId: currentUser.id,
      skills: skillsDraft.split(',').map(s => s.trim()).filter(Boolean)
    });
    setSavingProfile(false);
    setFeedback(res.ok ? 'Professional GROW profile saved.' : res.error || 'Could not save GROW profile.');
  };

  const inboundPending = requests.filter(r => r.recipientId === currentUser?.id && r.status === 'PENDING');

  return (
    <div className="max-w-2xl mx-auto px-4 pt-4 pb-28 space-y-4">
      <div>
        <h1 className="text-2xl font-extrabold text-[var(--vybe-text)] tracking-tight">GROW Network & Circles</h1>
        <p className="text-xs text-[var(--vybe-text-secondary)] mt-0.5">
          Mentors, builders, Growth Circles & privacy-controlled GROW profiles
        </p>
      </div>

      {feedback && (
        <div className="p-2.5 rounded-xl bg-[var(--vybe-accent-soft)] border border-[var(--vybe-accent)]/30 text-xs flex items-center justify-between gap-2">
          <span>{feedback}</span>
          <button type="button" onClick={() => setFeedback(null)} className="font-bold">Dismiss</button>
        </div>
      )}

      <VybeUnderlineTabs
        tabs={[
          { id: 'PEOPLE', label: 'Builders' },
          { id: 'REQUESTS', label: `Requests (${inboundPending.length})` },
          { id: 'CIRCLES', label: 'Circles' },
          { id: 'MY_GROW_PROFILE', label: 'My GROW Card' }
        ]}
        activeTab={subTab}
        onSelect={id => setSubTab(id as any)}
      />

      {subTab === 'PEOPLE' && (
        <div className="space-y-3">
          <div className="relative">
            <Search className="w-4 h-4 text-[var(--vybe-text-muted)] absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={query}
              onChange={e => setQuery(e.target.value)}
              placeholder="Search builders, mentors, or skills..."
              className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs"
            />
          </div>

          {filteredUsers.length === 0 ? (
            <VybeEmptyState icon={Users} title="No matching members" subtitle="Adjust your search query to discover builders." />
          ) : (
            filteredUsers.map(member => (
              <div key={member.id} className="p-3.5 rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-3 min-w-0 cursor-pointer" onClick={() => openUserProfile(member)}>
                  <VybeAvatar src={member.avatarUrl} name={member.displayName} size={42} className="w-10 h-10 shrink-0" />
                  <div className="min-w-0">
                    <div className="flex items-center gap-1">
                      <span className="text-xs font-bold truncate">{member.displayName}</span>
                      {member.isVerified && <ShieldCheck className="w-3.5 h-3.5 text-[var(--vybe-accent)] shrink-0" />}
                    </div>
                    <p className="text-[11px] text-[var(--vybe-text-secondary)] truncate">@{member.username}</p>
                  </div>
                </div>
                <div className="flex items-center gap-1.5">
                  <button
                    type="button"
                    onClick={() => handleInspectGrowProfile(member)}
                    className="px-2.5 py-1.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[11px] font-semibold inline-flex items-center gap-1"
                  >
                    <Eye className="w-3 h-3" /> Card
                  </button>
                  <button
                    type="button"
                    onClick={() => handleSendGrowReq(member.id, 'CONNECT')}
                    className="px-2.5 py-1.5 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-[11px] font-bold inline-flex items-center gap-1"
                  >
                    <UserPlus className="w-3 h-3" /> Connect
                  </button>
                  <button
                    type="button"
                    onClick={() => handleSendGrowReq(member.id, 'MENTORSHIP')}
                    className="px-2.5 py-1.5 rounded-xl bg-amber-500/15 border border-amber-500/40 text-amber-500 text-[11px] font-bold"
                  >
                    Mentor
                  </button>
                </div>
              </div>
            ))
          )}
        </div>
      )}

      {subTab === 'REQUESTS' && (
        <div className="space-y-2.5">
          {loadingReq ? (
            <div className="py-8 flex items-center justify-center gap-2 text-xs"><Loader2 className="w-4 h-4 animate-spin" /> Loading requests...</div>
          ) : requests.length === 0 ? (
            <VybeEmptyState icon={Sparkles} title="No GROW requests yet" subtitle="Connection and mentorship invitations appear here." />
          ) : (
            requests.map(req => {
              const isIncoming = req.recipientId === currentUser?.id;
              const peer = isIncoming ? req.sender : req.recipient;
              return (
                <div key={req.id} className="p-3.5 rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] flex items-center justify-between gap-3">
                  <div className="flex items-center gap-2.5 min-w-0">
                    <VybeAvatar src={peer.avatarUrl} name={peer.displayName} size={36} className="w-9 h-9 shrink-0" />
                    <div className="min-w-0">
                      <p className="text-xs font-bold truncate">
                        {isIncoming ? `From ${peer.displayName}` : `To ${peer.displayName}`} · {req.requestType}
                      </p>
                      <p className="text-[11px] text-[var(--vybe-text-secondary)] truncate">{req.message}</p>
                    </div>
                  </div>
                  {isIncoming && req.status === 'PENDING' ? (
                    <div className="flex items-center gap-1.5 shrink-0">
                      <button
                        type="button"
                        onClick={async () => {
                          await respondGrowRequest(req.id, 'ACCEPTED');
                          await loadNetworkData();
                        }}
                        className="p-1.5 rounded-lg bg-emerald-600 text-white keep-white"
                        aria-label="Accept GROW request"
                      >
                        <Check className="w-3.5 h-3.5" />
                      </button>
                      <button
                        type="button"
                        onClick={async () => {
                          await respondGrowRequest(req.id, 'DECLINED');
                          await loadNetworkData();
                        }}
                        className="p-1.5 rounded-lg bg-red-600 text-white keep-white"
                        aria-label="Decline GROW request"
                      >
                        <X className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  ) : (
                    <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-[var(--vybe-elevated)] border border-[var(--vybe-border)]">
                      {req.status}
                    </span>
                  )}
                </div>
              );
            })
          )}
        </div>
      )}

      {subTab === 'CIRCLES' && (
        <div className="space-y-3">
          {communities.map(comm => (
            <div
              key={comm.id}
              onClick={() => setActiveCircle(comm)}
              className="rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] p-4 flex items-center justify-between gap-3 cursor-pointer hover:border-[var(--vybe-accent)]"
            >
              <div className="min-w-0">
                <h3 className="text-sm font-bold">{comm.name}</h3>
                <p className="text-xs text-[var(--vybe-text-secondary)] line-clamp-1">{comm.description}</p>
                <p className="text-[11px] text-[var(--vybe-accent)] font-semibold mt-0.5">{comm.membersCount} members · {comm.category}</p>
              </div>
              <button
                type="button"
                onClick={e => {
                  e.stopPropagation();
                  toggleJoinCommunity(comm.id);
                }}
                className={`px-3.5 py-1.5 rounded-xl text-xs font-bold shrink-0 ${
                  comm.isJoined ? 'bg-[var(--vybe-elevated)] border border-[var(--vybe-border)]' : 'bg-[var(--vybe-accent)] text-white keep-white'
                }`}
              >
                {comm.isJoined ? 'Open' : comm.isPending ? 'Pending' : 'Join'}
              </button>
            </div>
          ))}
        </div>
      )}

      {subTab === 'MY_GROW_PROFILE' && (
        <form onSubmit={handleSaveMyGrowProfile} className="rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] p-4 space-y-3">
          <h3 className="text-sm font-bold flex items-center gap-1.5">
            <Briefcase className="w-4 h-4 text-[var(--vybe-accent)]" /> Professional GROW Profile & Privacy
          </h3>
          <input type="text" value={myProfile.headline} onChange={e => setMyProfile(p => ({ ...p, headline: e.target.value }))} placeholder="Headline (e.g. Full-Stack Android & Web Builder)" className="w-full px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs" />
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
            <input type="text" value={myProfile.currentRole} onChange={e => setMyProfile(p => ({ ...p, currentRole: e.target.value }))} placeholder="Current Role" className="px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs" />
            <input type="text" value={myProfile.companyOrOrg} onChange={e => setMyProfile(p => ({ ...p, companyOrOrg: e.target.value }))} placeholder="Company / Organization" className="px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs" />
          </div>
          <input type="text" value={skillsDraft} onChange={e => setSkillsDraft(e.target.value)} placeholder="Skills (comma separated)" className="w-full px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs" />
          <textarea rows={2} value={myProfile.careerGoals} onChange={e => setMyProfile(p => ({ ...p, careerGoals: e.target.value }))} placeholder="Current career & learning goals..." className="w-full px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs" />

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 pt-1">
            {([
              { key: 'visibilityRole', label: 'Role Visibility' },
              { key: 'visibilityCompany', label: 'Company' },
              { key: 'visibilityPortfolio', label: 'Portfolio' },
              { key: 'visibilityGoals', label: 'Goals' }
            ] as const).map(item => (
              <label key={item.key} className="text-[10px] space-y-1">
                <span className="text-[var(--vybe-text-secondary)] font-semibold block">{item.label}</span>
                <select
                  value={(myProfile[item.key] as FieldVisibility) || 'PUBLIC'}
                  onChange={e => setMyProfile(p => ({ ...p, [item.key]: e.target.value as FieldVisibility }))}
                  className="w-full px-2 py-1.5 rounded-lg bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[11px]"
                >
                  <option value="PUBLIC">Public</option>
                  <option value="CONNECTIONS">LINKED Only</option>
                  <option value="PRIVATE">Private</option>
                </select>
              </label>
            ))}
          </div>

          <button type="submit" disabled={savingProfile} className="px-4 py-2 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold disabled:opacity-50">
            {savingProfile ? 'Saving...' : 'Save GROW Profile'}
          </button>
        </form>
      )}

      {activeCircle && <CircleWorkspaceModal circle={activeCircle} onClose={() => setActiveCircle(null)} />}

      {peerGrowModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm" onClick={() => setPeerGrowModal(null)}>
          <div onClick={e => e.stopPropagation()} className="w-full max-w-md bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-3xl p-5 space-y-3">
            <div className="flex items-center justify-between pb-2 border-b border-[var(--vybe-border)]">
              <div className="flex items-center gap-2.5">
                <VybeAvatar src={peerGrowModal.user.avatarUrl} name={peerGrowModal.user.displayName} size={38} className="w-9 h-9" />
                <div>
                  <h4 className="text-sm font-bold">{peerGrowModal.user.displayName}</h4>
                  <p className="text-[11px] text-[var(--vybe-text-secondary)]">@{peerGrowModal.user.username}</p>
                </div>
              </div>
              <button type="button" onClick={() => setPeerGrowModal(null)} aria-label="Close"><X className="w-4 h-4" /></button>
            </div>
            {peerGrowModal.profile ? (
              <div className="space-y-2 text-xs">
                {peerGrowModal.profile.headline && <p className="font-bold">{peerGrowModal.profile.headline}</p>}
                {(peerGrowModal.profile.currentRole || peerGrowModal.profile.companyOrOrg) && (
                  <p className="text-[var(--vybe-text-secondary)]">
                    {[peerGrowModal.profile.currentRole, peerGrowModal.profile.companyOrOrg].filter(Boolean).join(' at ')}
                  </p>
                )}
                {peerGrowModal.profile.skills.length > 0 && (
                  <div className="flex flex-wrap gap-1.5">
                    {peerGrowModal.profile.skills.map(s => (
                      <span key={s} className="px-2 py-0.5 rounded bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[10px]">
                        {s}
                      </span>
                    ))}
                  </div>
                )}
                {peerGrowModal.profile.careerGoals && (
                  <p className="text-[var(--vybe-text-secondary)]">Goals: {peerGrowModal.profile.careerGoals}</p>
                )}
              </div>
            ) : (
              <p className="text-xs text-[var(--vybe-text-secondary)] py-3">
                This member has not published public GROW profile details yet.
              </p>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
