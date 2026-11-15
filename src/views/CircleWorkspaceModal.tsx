import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  X, Users, MessageCircle, Pin, PinOff, BarChart3, Calendar, BookOpen,
  Shield, Flag, LogOut, Check, UserCheck, UserX, ArrowUpCircle, ArrowDownCircle,
  Loader2, Send, Plus, Upload, ExternalLink, AlertTriangle
} from 'lucide-react';
import {
  CircleEvent,
  CircleMember,
  CirclePoll,
  CirclePost,
  CircleResource,
  Community
} from '../types';
import { useVybe } from '../context/VybeContext';
import { VybeAvatar } from '../components/OptimizedImage';
import { VybeConfirmDialog } from '../components/VybeDesignSystem';
import { uploadMediaBlobToStorage } from '../services/supabaseService';
import {
  addCircleResource,
  createCircleEvent,
  createCirclePoll,
  createCirclePost,
  fetchCircleWorkspaceData,
  removeCircleMember,
  reviewCircleMemberRequestRpc,
  rsvpCircleEvent,
  setCircleMemberRoleRpc,
  submitCircleReport,
  toggleCirclePostPinRpc,
  voteCirclePoll
} from '../services/storiesCirclesService';

interface CircleWorkspaceModalProps {
  circle: Community;
  onClose: () => void;
}

type WorkspaceTab = 'FEED' | 'POLLS' | 'EVENTS' | 'RESOURCES' | 'MEMBERS';

export const CircleWorkspaceModal: React.FC<CircleWorkspaceModalProps> = ({ circle, onClose }) => {
  const { currentUser, requireAuth, toggleJoinCommunity, openCircleGroupChat, refreshCommunities } = useVybe();

  const [tab, setTab] = useState<WorkspaceTab>('FEED');
  const [loading, setLoading] = useState(true);
  const [posts, setPosts] = useState<CirclePost[]>([]);
  const [polls, setPolls] = useState<CirclePoll[]>([]);
  const [events, setEvents] = useState<CircleEvent[]>([]);
  const [resources, setResources] = useState<CircleResource[]>([]);
  const [members, setMembers] = useState<CircleMember[]>([]);
  const [feedback, setFeedback] = useState<string | null>(null);

  // Composers
  const [postText, setPostText] = useState('');
  const [postType, setPostType] = useState<'DISCUSSION' | 'ANNOUNCEMENT'>('DISCUSSION');
  const [submittingPost, setSubmittingPost] = useState(false);

  const [pollQuestion, setPollQuestion] = useState('');
  const [pollOptionsText, setPollOptionsText] = useState('Option A\nOption B');

  const [eventTitle, setEventTitle] = useState('');
  const [eventType, setEventType] = useState<'ONLINE' | 'IN_PERSON'>('ONLINE');
  const [eventLoc, setEventLoc] = useState('');
  const [eventDate, setEventDate] = useState(() => new Date(Date.now() + 86400_000).toISOString().slice(0, 16));

  const [resTitle, setResTitle] = useState('');
  const [resUrl, setResUrl] = useState('');
  const [resType, setResType] = useState<'LINK' | 'GUIDE' | 'FILE' | 'TOOL'>('LINK');
  const [uploadingRes, setUploadingRes] = useState(false);
  const resFileRef = useRef<HTMLInputElement | null>(null);

  const [showLeaveConfirm, setShowLeaveConfirm] = useState(false);
  const [showReportModal, setShowReportModal] = useState(false);
  const [reportReason, setReportReason] = useState('Spam or off-topic promotions');

  const loadWorkspace = useCallback(async () => {
    setLoading(true);
    const data = await fetchCircleWorkspaceData(circle.id, currentUser?.id, circle.creatorId);
    setPosts(data.posts);
    setPolls(data.polls);
    setEvents(data.events);
    setResources(data.resources);
    setMembers(data.members);
    setLoading(false);
  }, [circle.id, circle.creatorId, currentUser?.id]);

  useEffect(() => {
    loadWorkspace();
  }, [loadWorkspace]);

  const myMemberRecord = currentUser
    ? members.find(m => m.userId.toLowerCase() === currentUser.id.toLowerCase())
    : undefined;
  const isOwner = Boolean(
    currentUser &&
      ((circle.creatorId && circle.creatorId.toLowerCase() === currentUser.id.toLowerCase()) ||
        myMemberRecord?.role === 'OWNER')
  );
  const isModerator = isOwner || myMemberRecord?.role === 'MODERATOR';
  const isAcceptedMember = Boolean(circle.isJoined || myMemberRecord?.status === 'ACCEPTED' || isOwner);

  const handlePublishPost = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!requireAuth('Sign in to post inside a Circle.') || !currentUser) return;
    if (!postText.trim()) return;
    setSubmittingPost(true);
    const res = await createCirclePost({
      communityId: circle.id,
      authorId: currentUser.id,
      content: postText,
      postType: isModerator ? postType : 'DISCUSSION'
    });
    setSubmittingPost(false);
    if (res.ok) {
      setPostText('');
      await loadWorkspace();
    } else {
      setFeedback(res.error || 'Could not post.');
    }
  };

  const handleTogglePin = async (post: CirclePost) => {
    const ok = await toggleCirclePostPinRpc(post.id, !post.isPinned);
    if (ok) await loadWorkspace();
  };

  const handleCreatePoll = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!requireAuth('Sign in to create polls.') || !currentUser) return;
    const opts = pollOptionsText.split('\n').map(s => s.trim()).filter(Boolean);
    if (!pollQuestion.trim() || opts.length < 2) {
      setFeedback('Enter a question and at least 2 poll options.');
      return;
    }
    const res = await createCirclePoll({
      communityId: circle.id,
      creatorId: currentUser.id,
      question: pollQuestion,
      options: opts
    });
    if (res.ok) {
      setPollQuestion('');
      setPollOptionsText('');
      await loadWorkspace();
    } else {
      setFeedback(res.error || 'Could not create poll.');
    }
  };

  const handleVote = async (pollId: string, optionIdx: number) => {
    if (!requireAuth('Sign in to vote.') || !currentUser) return;
    const ok = await voteCirclePoll({ pollId, userId: currentUser.id, optionIndex: optionIdx });
    if (ok) await loadWorkspace();
  };

  const handleCreateEvent = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!requireAuth('Sign in to schedule events.') || !currentUser) return;
    if (!eventTitle.trim()) return;
    const res = await createCircleEvent({
      communityId: circle.id,
      creatorId: currentUser.id,
      title: eventTitle,
      description: `Hosted in ${circle.name}`,
      eventType,
      locationOrLink: eventLoc || 'VYBE Online Space',
      startsAtIso: new Date(eventDate).toISOString()
    });
    if (res.ok) {
      setEventTitle('');
      setEventLoc('');
      await loadWorkspace();
    } else {
      setFeedback(res.error || 'Could not schedule event.');
    }
  };

  const handleRsvp = async (eventId: string, status: 'GOING' | 'INTERESTED' | 'CANCELLED') => {
    if (!requireAuth('Sign in to RSVP.') || !currentUser) return;
    const ok = await rsvpCircleEvent({ eventId, userId: currentUser.id, status });
    if (ok) await loadWorkspace();
  };

  const handleUploadResourceFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setUploadingRes(true);
    const uploaded = await uploadMediaBlobToStorage(file, 'circle-resources', file.name, file.size);
    setUploadingRes(false);
    if (uploaded?.publicUrl) {
      setResUrl(uploaded.publicUrl);
      setResType('FILE');
      if (!resTitle.trim()) setResTitle(file.name);
    } else {
      setFeedback('Could not upload resource file.');
    }
  };

  const handleAddResource = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!requireAuth('Sign in to share resources.') || !currentUser) return;
    if (!resTitle.trim() || !resUrl.trim()) return;
    const res = await addCircleResource({
      communityId: circle.id,
      creatorId: currentUser.id,
      title: resTitle,
      description: '',
      resourceType: resType,
      resourceUrl: resUrl
    });
    if (res.ok) {
      setResTitle('');
      setResUrl('');
      await loadWorkspace();
    } else {
      setFeedback(res.error || 'Could not add resource.');
    }
  };

  const handleConfirmLeave = async () => {
    setShowLeaveConfirm(false);
    await toggleJoinCommunity(circle.id);
    await loadWorkspace();
  };

  const handleSendReport = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!requireAuth('Sign in to submit a report.') || !currentUser) return;
    const res = await submitCircleReport({
      communityId: circle.id,
      reporterId: currentUser.id,
      targetType: 'CIRCLE',
      targetId: circle.id,
      reason: reportReason
    });
    setShowReportModal(false);
    setFeedback(res.ok ? 'Circle report sent to moderators.' : res.error || 'Report failed.');
  };

  const pendingMembers = members.filter(m => m.status === 'PENDING');
  const acceptedMembers = members.filter(m => m.status === 'ACCEPTED');

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-2 sm:p-4 bg-black/75 backdrop-blur-sm animate-fade-in"
      onClick={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label={circle.name}
        onClick={e => e.stopPropagation()}
        className="w-full max-w-2xl h-[90dvh] flex flex-col bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-3xl shadow-2xl overflow-hidden text-[var(--vybe-text)]"
      >
        {/* Top Banner + Header */}
        <div className="relative h-28 shrink-0 bg-gradient-to-br from-[#1e1b4b] via-[#312e81] to-[#0c4a6e]">
          {circle.bannerUrl && (
            <img src={circle.bannerUrl} alt={circle.name} className="w-full h-full object-cover opacity-80" />
          )}
          <div className="absolute inset-0 bg-gradient-to-t from-[var(--vybe-surface)] via-black/30 to-black/20" />
          <div className="absolute top-3 right-3 flex items-center gap-1.5">
            <button
              type="button"
              onClick={() => setShowReportModal(true)}
              aria-label="Report Circle"
              className="p-2 rounded-full bg-black/55 text-white keep-white hover:bg-black/75"
            >
              <Flag className="w-4 h-4" />
            </button>
            <button
              type="button"
              onClick={onClose}
              aria-label="Close Circle workspace"
              className="p-2 rounded-full bg-black/55 text-white keep-white hover:bg-black/75"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>

        <div className="px-5 pb-3 -mt-7 relative shrink-0 border-b border-[var(--vybe-border)] space-y-2.5">
          <div className="flex items-end justify-between gap-3 flex-wrap">
            <div className="flex items-end gap-3 min-w-0">
              <VybeAvatar
                src={circle.avatarUrl}
                name={circle.name}
                size={56}
                className="w-14 h-14 rounded-2xl border-2 border-[var(--vybe-surface)] shadow-md shrink-0"
              />
              <div className="min-w-0">
                <div className="flex items-center gap-1.5 flex-wrap">
                  <h2 className="text-base font-extrabold truncate">{circle.name}</h2>
                  {circle.joinMode && (
                    <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-[var(--vybe-elevated)] border border-[var(--vybe-border)]">
                      {circle.joinMode}
                    </span>
                  )}
                </div>
                <p className="text-[11px] text-[var(--vybe-text-secondary)]">
                  {circle.category} · {circle.membersCount.toLocaleString()} members
                </p>
              </div>
            </div>

            <div className="flex items-center gap-2">
              {isAcceptedMember && (
                <button
                  type="button"
                  onClick={() => {
                    onClose();
                    openCircleGroupChat(circle.id);
                  }}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] text-xs font-bold"
                >
                  <MessageCircle className="w-3.5 h-3.5 text-[var(--vybe-accent)]" />
                  <span>Circle Chat</span>
                </button>
              )}

              {circle.isJoined ? (
                <button
                  type="button"
                  onClick={() => setShowLeaveConfirm(true)}
                  className="inline-flex items-center gap-1 px-3 py-1.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] hover:border-red-500/60 hover:text-red-500 text-xs font-bold"
                >
                  <LogOut className="w-3.5 h-3.5" />
                  <span>Leave</span>
                </button>
              ) : circle.isPending ? (
                <button
                  type="button"
                  onClick={() => toggleJoinCommunity(circle.id)}
                  className="px-3.5 py-1.5 rounded-xl bg-amber-500/15 border border-amber-500/40 text-amber-500 text-xs font-bold"
                >
                  Pending Request
                </button>
              ) : (
                <button
                  type="button"
                  onClick={() => toggleJoinCommunity(circle.id)}
                  className="px-3.5 py-1.5 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold"
                >
                  {circle.joinMode === 'APPROVAL' || circle.joinMode === 'PRIVATE' ? 'Request to Join' : 'Join Circle'}
                </button>
              )}
            </div>
          </div>

          {circle.rules && (
            <p className="text-[11px] text-[var(--vybe-text-secondary)] bg-[var(--vybe-elevated)] px-3 py-1.5 rounded-xl border border-[var(--vybe-border)]">
              <strong className="text-[var(--vybe-text)]">Rules:</strong> {circle.rules}
            </p>
          )}

          {/* Workspace Tabs */}
          <div className="flex items-center gap-1 overflow-x-auto no-scrollbar pt-1">
            {([
              { id: 'FEED', label: `Feed (${posts.length})`, icon: MessageCircle },
              { id: 'POLLS', label: `Polls (${polls.length})`, icon: BarChart3 },
              { id: 'EVENTS', label: `Events (${events.length})`, icon: Calendar },
              { id: 'RESOURCES', label: `Resources (${resources.length})`, icon: BookOpen },
              { id: 'MEMBERS', label: `Members (${acceptedMembers.length})`, icon: Users }
            ] as const).map(item => {
              const Icon = item.icon;
              const active = tab === item.id;
              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => setTab(item.id)}
                  className={`shrink-0 inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-bold transition-colors ${
                    active
                      ? 'bg-[var(--vybe-accent)] text-white keep-white'
                      : 'text-[var(--vybe-text-secondary)] hover:bg-[var(--vybe-elevated)]'
                  }`}
                >
                  <Icon className="w-3.5 h-3.5" />
                  <span>{item.label}</span>
                </button>
              );
            })}
          </div>
        </div>

        {/* Scrollable Workspace Content */}
        <div className="flex-1 overflow-y-auto p-4 sm:p-5 space-y-4 no-scrollbar">
          {feedback && (
            <div className="p-2.5 rounded-xl bg-[var(--vybe-accent-soft)] border border-[var(--vybe-accent)]/30 text-xs flex items-center justify-between gap-2">
              <span>{feedback}</span>
              <button type="button" onClick={() => setFeedback(null)} className="font-bold">Dismiss</button>
            </div>
          )}

          {loading ? (
            <div className="py-12 flex items-center justify-center gap-2 text-xs text-[var(--vybe-text-secondary)]">
              <Loader2 className="w-4 h-4 animate-spin text-[var(--vybe-accent)]" />
              <span>Loading Circle workspace...</span>
            </div>
          ) : (
            <>
              {/* TAB 1: FEED */}
              {tab === 'FEED' && (
                <div className="space-y-3">
                  {isAcceptedMember && (
                    <form onSubmit={handlePublishPost} className="p-3 rounded-2xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] space-y-2">
                      <textarea
                        rows={2}
                        value={postText}
                        onChange={e => setPostText(e.target.value)}
                        placeholder="Share an update, question, or win with this Circle..."
                        className="w-full bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-xl px-3 py-2 text-xs focus:outline-none focus:border-[var(--vybe-accent)]"
                      />
                      <div className="flex items-center justify-between">
                        {isModerator ? (
                          <select
                            value={postType}
                            onChange={e => setPostType(e.target.value as any)}
                            className="bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-lg px-2.5 py-1 text-[11px]"
                          >
                            <option value="DISCUSSION">Discussion</option>
                            <option value="ANNOUNCEMENT">Announcement</option>
                          </select>
                        ) : <span />}
                        <button
                          type="submit"
                          disabled={submittingPost || !postText.trim()}
                          className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold disabled:opacity-40"
                        >
                          <Send className="w-3.5 h-3.5" />
                          <span>{submittingPost ? 'Posting...' : 'Post'}</span>
                        </button>
                      </div>
                    </form>
                  )}

                  {posts.length === 0 ? (
                    <p className="text-xs text-[var(--vybe-text-secondary)] text-center py-8">
                      No Circle posts yet. Start the first discussion above!
                    </p>
                  ) : (
                    posts.map(p => (
                      <div key={p.id} className="p-3.5 rounded-2xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] space-y-2">
                        <div className="flex items-center justify-between gap-2">
                          <div className="flex items-center gap-2 min-w-0">
                            <VybeAvatar src={p.author.avatarUrl} name={p.author.displayName} size={30} className="w-7 h-7 shrink-0" />
                            <div className="min-w-0">
                              <span className="text-xs font-bold truncate block">{p.author.displayName}</span>
                              <span className="text-[10px] text-[var(--vybe-text-muted)]">{p.timestamp}</span>
                            </div>
                          </div>
                          <div className="flex items-center gap-1.5">
                            {p.isPinned && (
                              <span className="px-2 py-0.5 rounded-full bg-[var(--vybe-accent-soft)] text-[var(--vybe-accent)] text-[10px] font-bold">
                                Pinned
                              </span>
                            )}
                            {p.postType === 'ANNOUNCEMENT' && (
                              <span className="px-2 py-0.5 rounded-full bg-amber-500/15 text-amber-500 text-[10px] font-bold">
                                Announcement
                              </span>
                            )}
                            {isModerator && (
                              <button
                                type="button"
                                onClick={() => handleTogglePin(p)}
                                aria-label={p.isPinned ? 'Unpin post' : 'Pin post'}
                                className="p-1 rounded-lg hover:bg-[var(--vybe-surface)] text-[var(--vybe-text-secondary)]"
                              >
                                {p.isPinned ? <PinOff className="w-3.5 h-3.5" /> : <Pin className="w-3.5 h-3.5" />}
                              </button>
                            )}
                          </div>
                        </div>
                        <p className="text-xs leading-relaxed whitespace-pre-line">{p.content}</p>
                      </div>
                    ))
                  )}
                </div>
              )}

              {/* TAB 2: POLLS */}
              {tab === 'POLLS' && (
                <div className="space-y-3">
                  {isAcceptedMember && (
                    <form onSubmit={handleCreatePoll} className="p-3 rounded-2xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] space-y-2">
                      <input
                        type="text"
                        value={pollQuestion}
                        onChange={e => setPollQuestion(e.target.value)}
                        placeholder="Ask a poll question..."
                        className="w-full px-3 py-2 rounded-xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-xs"
                      />
                      <textarea
                        rows={2}
                        value={pollOptionsText}
                        onChange={e => setPollOptionsText(e.target.value)}
                        placeholder="One option per line..."
                        className="w-full px-3 py-2 rounded-xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-xs"
                      />
                      <button type="submit" className="px-3.5 py-1.5 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold inline-flex items-center gap-1">
                        <Plus className="w-3.5 h-3.5" /> Create Poll
                      </button>
                    </form>
                  )}
                  {polls.length === 0 ? (
                    <p className="text-xs text-[var(--vybe-text-secondary)] text-center py-8">No polls in this Circle yet.</p>
                  ) : (
                    polls.map(pl => {
                      const totalVotes = pl.options.reduce((sum, o) => sum + o.votesCount, 0) || 1;
                      return (
                        <div key={pl.id} className="p-3.5 rounded-2xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] space-y-2">
                          <h4 className="text-xs font-bold">{pl.question}</h4>
                          <div className="space-y-1.5">
                            {pl.options.map((opt, idx) => {
                              const picked = pl.mySelectedOptionIds.includes(opt.id);
                              const pct = Math.round((opt.votesCount / totalVotes) * 100);
                              return (
                                <button
                                  key={opt.id}
                                  type="button"
                                  onClick={() => handleVote(pl.id, idx)}
                                  className={`w-full text-left px-3 py-2 rounded-xl border text-xs flex items-center justify-between transition-colors ${
                                    picked
                                      ? 'border-[var(--vybe-accent)] bg-[var(--vybe-accent-soft)]'
                                      : 'border-[var(--vybe-border)] bg-[var(--vybe-surface)] hover:border-[var(--vybe-accent)]/50'
                                  }`}
                                >
                                  <span className="font-semibold flex items-center gap-1.5">
                                    {picked && <Check className="w-3.5 h-3.5 text-[var(--vybe-accent)]" />}
                                    {opt.label}
                                  </span>
                                  <span className="text-[11px] text-[var(--vybe-text-secondary)]">
                                    {opt.votesCount} ({pct}%)
                                  </span>
                                </button>
                              );
                            })}
                          </div>
                        </div>
                      );
                    })
                  )}
                </div>
              )}

              {/* TAB 3: EVENTS */}
              {tab === 'EVENTS' && (
                <div className="space-y-3">
                  {isAcceptedMember && (
                    <form onSubmit={handleCreateEvent} className="p-3 rounded-2xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] space-y-2">
                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                        <input
                          type="text"
                          value={eventTitle}
                          onChange={e => setEventTitle(e.target.value)}
                          placeholder="Event title (e.g. Weekly Builder Demo)"
                          className="px-3 py-2 rounded-xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-xs"
                        />
                        <select
                          value={eventType}
                          onChange={e => setEventType(e.target.value as any)}
                          className="px-3 py-2 rounded-xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-xs"
                        >
                          <option value="ONLINE">Online</option>
                          <option value="IN_PERSON">In Person</option>
                        </select>
                        <input
                          type="text"
                          value={eventLoc}
                          onChange={e => setEventLoc(e.target.value)}
                          placeholder="Meeting link or venue"
                          className="px-3 py-2 rounded-xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-xs"
                        />
                        <input
                          type="datetime-local"
                          value={eventDate}
                          onChange={e => setEventDate(e.target.value)}
                          className="px-3 py-2 rounded-xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-xs"
                        />
                      </div>
                      <button type="submit" className="px-3.5 py-1.5 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold inline-flex items-center gap-1">
                        <Calendar className="w-3.5 h-3.5" /> Schedule Event
                      </button>
                    </form>
                  )}
                  {events.length === 0 ? (
                    <p className="text-xs text-[var(--vybe-text-secondary)] text-center py-8">No upcoming Circle events.</p>
                  ) : (
                    events.map(ev => (
                      <div key={ev.id} className="p-3.5 rounded-2xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] flex flex-wrap items-center justify-between gap-3">
                        <div className="min-w-0 space-y-0.5">
                          <div className="flex items-center gap-2">
                            <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-[var(--vybe-accent-soft)] text-[var(--vybe-accent)]">
                              {ev.eventMode}
                            </span>
                            <span className="text-[11px] text-[var(--vybe-text-muted)]">
                              {new Date(ev.startsAtIso).toLocaleString()}
                            </span>
                          </div>
                          <h4 className="text-xs font-bold">{ev.title}</h4>
                          <p className="text-[11px] text-[var(--vybe-text-secondary)]">{ev.locationOrLink}</p>
                        </div>
                        <div className="flex items-center gap-1.5">
                          <button
                            type="button"
                            onClick={() => handleRsvp(ev.id, ev.myRsvp === 'GOING' ? 'CANCELLED' : 'GOING')}
                            className={`px-3 py-1.5 rounded-xl text-[11px] font-bold ${
                              ev.myRsvp === 'GOING'
                                ? 'bg-emerald-600 text-white keep-white'
                                : 'bg-[var(--vybe-surface)] border border-[var(--vybe-border)]'
                            }`}
                          >
                            Going ({ev.goingCount})
                          </button>
                          <button
                            type="button"
                            onClick={() => handleRsvp(ev.id, ev.myRsvp === 'INTERESTED' ? 'CANCELLED' : 'INTERESTED')}
                            className={`px-3 py-1.5 rounded-xl text-[11px] font-bold ${
                              ev.myRsvp === 'INTERESTED'
                                ? 'bg-[var(--vybe-accent)] text-white keep-white'
                                : 'bg-[var(--vybe-surface)] border border-[var(--vybe-border)]'
                            }`}
                          >
                            Interested ({ev.interestedCount})
                          </button>
                        </div>
                      </div>
                    ))
                  )}
                </div>
              )}

              {/* TAB 4: RESOURCES */}
              {tab === 'RESOURCES' && (
                <div className="space-y-3">
                  {isAcceptedMember && (
                    <form onSubmit={handleAddResource} className="p-3 rounded-2xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] space-y-2">
                      <input
                        type="text"
                        value={resTitle}
                        onChange={e => setResTitle(e.target.value)}
                        placeholder="Resource title..."
                        className="w-full px-3 py-2 rounded-xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-xs"
                      />
                      <div className="flex items-center gap-2">
                        <input
                          type="url"
                          value={resUrl}
                          onChange={e => setResUrl(e.target.value)}
                          placeholder="https://... or upload file"
                          className="flex-1 px-3 py-2 rounded-xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-xs"
                        />
                        <input ref={resFileRef} type="file" onChange={handleUploadResourceFile} className="hidden" />
                        <button
                          type="button"
                          disabled={uploadingRes}
                          onClick={() => resFileRef.current?.click()}
                          className="px-3 py-2 rounded-xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-xs font-semibold inline-flex items-center gap-1"
                        >
                          <Upload className="w-3.5 h-3.5" />
                          <span>{uploadingRes ? 'Uploading...' : 'File'}</span>
                        </button>
                      </div>
                      <button type="submit" className="px-3.5 py-1.5 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold inline-flex items-center gap-1">
                        <Plus className="w-3.5 h-3.5" /> Share Resource
                      </button>
                    </form>
                  )}
                  {resources.length === 0 ? (
                    <p className="text-xs text-[var(--vybe-text-secondary)] text-center py-8">No Circle resources shared yet.</p>
                  ) : (
                    resources.map(r => (
                      <a
                        key={r.id}
                        href={r.resourceUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="p-3.5 rounded-2xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] flex items-center justify-between gap-3"
                      >
                        <div className="min-w-0">
                          <span className="text-[10px] font-bold text-[var(--vybe-accent)] uppercase">{r.resourceType}</span>
                          <h4 className="text-xs font-bold truncate">{r.title}</h4>
                          <p className="text-[11px] text-[var(--vybe-text-secondary)] truncate">Shared by {r.authorName}</p>
                        </div>
                        <ExternalLink className="w-4 h-4 text-[var(--vybe-text-muted)] shrink-0" />
                      </a>
                    ))
                  )}
                </div>
              )}

              {/* TAB 5: MEMBERS & ROLES */}
              {tab === 'MEMBERS' && (
                <div className="space-y-4">
                  {isModerator && pendingMembers.length > 0 && (
                    <div className="space-y-2">
                      <h4 className="text-xs font-extrabold text-amber-500 uppercase tracking-wider">
                        Pending Join Requests ({pendingMembers.length})
                      </h4>
                      {pendingMembers.map(pm => (
                        <div key={pm.userId} className="p-3 rounded-2xl bg-amber-500/10 border border-amber-500/30 flex items-center justify-between gap-2">
                          <div className="flex items-center gap-2.5 min-w-0">
                            <VybeAvatar src={pm.user.avatarUrl} name={pm.user.displayName} size={34} className="w-8 h-8" />
                            <div className="min-w-0">
                              <p className="text-xs font-bold truncate">{pm.user.displayName}</p>
                              <p className="text-[10px] text-[var(--vybe-text-secondary)]">@{pm.user.username}</p>
                            </div>
                          </div>
                          <div className="flex items-center gap-1.5">
                            <button
                              type="button"
                              onClick={async () => {
                                await reviewCircleMemberRequestRpc({ communityId: circle.id, targetUserId: pm.userId, approve: true });
                                await refreshCommunities();
                                await loadWorkspace();
                              }}
                              className="p-1.5 rounded-lg bg-emerald-600 text-white keep-white"
                              aria-label={`Approve @${pm.user.username}`}
                            >
                              <UserCheck className="w-3.5 h-3.5" />
                            </button>
                            <button
                              type="button"
                              onClick={async () => {
                                await reviewCircleMemberRequestRpc({ communityId: circle.id, targetUserId: pm.userId, approve: false });
                                await loadWorkspace();
                              }}
                              className="p-1.5 rounded-lg bg-red-600 text-white keep-white"
                              aria-label={`Decline @${pm.user.username}`}
                            >
                              <UserX className="w-3.5 h-3.5" />
                            </button>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}

                  <div className="space-y-2">
                    {acceptedMembers.map(m => (
                      <div key={m.userId} className="p-3 rounded-2xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] flex items-center justify-between gap-2">
                        <div className="flex items-center gap-2.5 min-w-0">
                          <VybeAvatar src={m.user.avatarUrl} name={m.user.displayName} size={34} className="w-8 h-8 shrink-0" />
                          <div className="min-w-0">
                            <div className="flex items-center gap-1.5">
                              <p className="text-xs font-bold truncate">{m.user.displayName}</p>
                              {m.role !== 'MEMBER' && (
                                <span className="inline-flex items-center gap-0.5 text-[10px] font-bold px-1.5 py-0.5 rounded bg-[var(--vybe-accent-soft)] text-[var(--vybe-accent)]">
                                  <Shield className="w-2.5 h-2.5" /> {m.role}
                                </span>
                              )}
                            </div>
                            <p className="text-[10px] text-[var(--vybe-text-secondary)]">@{m.user.username}</p>
                          </div>
                        </div>

                        {isOwner && m.userId !== currentUser?.id && m.role !== 'OWNER' && (
                          <div className="flex items-center gap-1">
                            <button
                              type="button"
                              onClick={async () => {
                                await setCircleMemberRoleRpc({
                                  communityId: circle.id,
                                  targetUserId: m.userId,
                                  role: m.role === 'MODERATOR' ? 'MEMBER' : 'MODERATOR'
                                });
                                await loadWorkspace();
                              }}
                              title={m.role === 'MODERATOR' ? 'Demote to Member' : 'Promote to Moderator'}
                              className="p-1.5 rounded-lg bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-xs"
                            >
                              {m.role === 'MODERATOR' ? <ArrowDownCircle className="w-3.5 h-3.5" /> : <ArrowUpCircle className="w-3.5 h-3.5 text-[var(--vybe-accent)]" />}
                            </button>
                            <button
                              type="button"
                              onClick={async () => {
                                await removeCircleMember(circle.id, m.userId);
                                await loadWorkspace();
                              }}
                              aria-label={`Remove @${m.user.username}`}
                              className="p-1.5 rounded-lg bg-red-500/15 text-red-500"
                            >
                              <UserX className="w-3.5 h-3.5" />
                            </button>
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      </div>

      {/* Mandatory Leave Circle Confirmation Dialog */}
      <VybeConfirmDialog
        open={showLeaveConfirm}
        title={`Leave ${circle.name}?`}
        description="You will lose access to private Circle discussions, events, and group chat until you rejoin."
        confirmLabel="Leave Circle"
        tone="danger"
        onConfirm={handleConfirmLeave}
        onCancel={() => setShowLeaveConfirm(false)}
      />

      {/* Circle Report Modal */}
      {showReportModal && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center p-4 bg-black/70" onClick={() => setShowReportModal(false)}>
          <form
            onSubmit={handleSendReport}
            onClick={e => e.stopPropagation()}
            className="w-full max-w-sm bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-2xl p-5 space-y-3"
          >
            <h3 className="text-sm font-bold flex items-center gap-1.5">
              <AlertTriangle className="w-4 h-4 text-amber-500" /> Report {circle.name}
            </h3>
            <select
              value={reportReason}
              onChange={e => setReportReason(e.target.value)}
              className="w-full px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs"
            >
              <option>Spam or off-topic promotions</option>
              <option>Harassment or unsafe behavior</option>
              <option>Impersonation or misleading claims</option>
            </select>
            <div className="flex justify-end gap-2">
              <button type="button" onClick={() => setShowReportModal(false)} className="px-3 py-1.5 rounded-xl bg-[var(--vybe-elevated)] text-xs font-semibold">Cancel</button>
              <button type="submit" className="px-4 py-1.5 rounded-xl bg-red-600 text-white keep-white text-xs font-bold">Submit Report</button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};
