import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  Search,
  Compass,
  Sparkles,
  Play,
  Radio,
  BarChart2,
  Plus,
  Send,
  Eye,
  X
} from 'lucide-react';
import { VybeAvatar, VerifiedBadge, VybeResonanceIcon } from '../components/VybeCoreComponents';
import { supabase, isSupabaseConfigured } from '../lib/supabase';
import { MOOD_OPTIONS, matchesMoodFilter } from '../lib/utils';
import {
  searchUsersAndPosts,
  fetchFeedPosts,
  fetchLiveStreams,
  startLiveStreamWeb,
  endLiveStreamWeb,
  pushLiveStreamFrameWeb,
  fetchLiveStreamComments,
  postLiveStreamComment,
  fetchAudiencePolls,
  createAudiencePollWeb,
  voteAudiencePollWeb
} from '../lib/vybeApi';
import type {
  AudiencePoll,
  LiveStreamComment,
  LiveStreamSession,
  MoodZone,
  VybePost,
  VybeUser
} from '../types/vybe';

export const ExplorePage: React.FC<{
  currentUser: VybeUser;
  onOpenUserProfile?: (user: VybeUser) => void;
}> = ({ currentUser, onOpenUserProfile }) => {
  const isGuest = currentUser.id === 'guest_viewer';
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedMood, setSelectedMood] = useState<MoodZone | null>(null);
  const [allPosts, setAllPosts] = useState<VybePost[]>([]);
  const [liveStreams, setLiveStreams] = useState<LiveStreamSession[]>([]);
  const [polls, setPolls] = useState<AudiencePoll[]>([]);
  const [searchResults, setSearchResults] = useState<{ users: VybeUser[]; posts: VybePost[] } | null>(null);
  const [loading, setLoading] = useState(true);
  const [selectedPost, setSelectedPost] = useState<VybePost | null>(null);

  // Live Zone Modal (Host or Viewer)
  const [activeStream, setActiveStream] = useState<LiveStreamSession | null>(null);
  const [streamComments, setStreamComments] = useState<LiveStreamComment[]>([]);
  const [commentText, setCommentText] = useState('');
  const [showGoLiveModal, setShowGoLiveModal] = useState(false);
  const [liveTitle, setLiveTitle] = useState('');
  const [liveCategory, setLiveCategory] = useState('LiveZone');
  const [startingLive, setStartingLive] = useState(false);
  const hostVideoRef = useRef<HTMLVideoElement | null>(null);
  const hostStreamRef = useRef<MediaStream | null>(null);
  const hostFrameTimerRef = useRef<number | null>(null);

  // Poll Creator Modal
  const [showCreatePoll, setShowCreatePoll] = useState(false);
  const [pollQuestion, setPollQuestion] = useState('');
  const [pollOptA, setPollOptA] = useState('');
  const [pollOptB, setPollOptB] = useState('');
  const [creatingPoll, setCreatingPoll] = useState(false);

  const loadExploreAll = useCallback(async () => {
    try {
      const [postRes, streamRes, pollRes] = await Promise.all([
        fetchFeedPosts(currentUser.id, 0, 60),
        fetchLiveStreams(),
        fetchAudiencePolls(currentUser.id)
      ]);
      setAllPosts(postRes);
      setLiveStreams(streamRes);
      setPolls(pollRes);
    } catch {
      // Non-blocking fallback
    } finally {
      setLoading(false);
    }
  }, [currentUser.id]);

  useEffect(() => {
    setLoading(true);
    void loadExploreAll();
    const interval = window.setInterval(() => {
      void loadExploreAll();
    }, 12_000);

    if (!isSupabaseConfigured) {
      return () => window.clearInterval(interval);
    }

    const ch = supabase
      .channel('vybe-explore-live-sync')
      .on('postgres_changes', { event: '*', schema: 'public', table: 'live_streams' }, () => {
        void fetchLiveStreams().then(setLiveStreams);
      })
      .on('postgres_changes', { event: '*', schema: 'public', table: 'audience_pools' }, () => {
        void fetchAudiencePolls(currentUser.id).then(setPolls);
      })
      .on('postgres_changes', { event: '*', schema: 'public', table: 'audience_pool_votes' }, () => {
        void fetchAudiencePolls(currentUser.id).then(setPolls);
      })
      .subscribe();

    return () => {
      window.clearInterval(interval);
      void supabase.removeChannel(ch);
    };
  }, [currentUser.id, loadExploreAll]);

  // Active Live Stream viewer/host sync
  useEffect(() => {
    if (!activeStream) return;
    let mounted = true;
    const syncActiveStream = async () => {
      const [streamsNow, commentsNow] = await Promise.all([
        fetchLiveStreams(),
        fetchLiveStreamComments(activeStream.id)
      ]);
      if (!mounted) return;
      setStreamComments(commentsNow);
      const updated = streamsNow.find((s) => s.id === activeStream.id);
      if (updated) {
        setActiveStream(updated);
      }
    };
    void syncActiveStream();
    const timer = window.setInterval(() => void syncActiveStream(), 1200);
    return () => {
      mounted = false;
      window.clearInterval(timer);
    };
  }, [activeStream?.id]);

  const stopHostCamera = () => {
    if (hostFrameTimerRef.current) {
      window.clearInterval(hostFrameTimerRef.current);
      hostFrameTimerRef.current = null;
    }
    hostStreamRef.current?.getTracks().forEach((t) => t.stop());
    hostStreamRef.current = null;
  };

  const handleStartLive = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isGuest || startingLive) return;
    setStartingLive(true);
    try {
      const media = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'user', width: { ideal: 640 }, height: { ideal: 480 } },
        audio: true
      });
      hostStreamRef.current = media;
      const created = await startLiveStreamWeb({
        host: currentUser,
        title: liveTitle.trim() || `${currentUser.displayName} Live`,
        category: liveCategory
      });
      setShowGoLiveModal(false);
      setLiveTitle('');
      setActiveStream(created);
      await loadExploreAll();

      window.setTimeout(() => {
        if (hostVideoRef.current && hostStreamRef.current) {
          hostVideoRef.current.srcObject = hostStreamRef.current;
          void hostVideoRef.current.play().catch(() => {});
        }
      }, 100);

      const canvas = document.createElement('canvas');
      hostFrameTimerRef.current = window.setInterval(() => {
        const v = hostVideoRef.current;
        if (!v || !v.videoWidth || !v.videoHeight) return;
        const scale = Math.min(1, 260 / Math.max(v.videoWidth, v.videoHeight));
        canvas.width = Math.max(1, Math.round(v.videoWidth * scale));
        canvas.height = Math.max(1, Math.round(v.videoHeight * scale));
        const ctx = canvas.getContext('2d');
        if (!ctx) return;
        ctx.drawImage(v, 0, 0, canvas.width, canvas.height);
        const dataUrl = canvas.toDataURL('image/jpeg', 0.45);
        const raw = dataUrl.includes('base64,') ? dataUrl.split('base64,')[1] : dataUrl;
        void pushLiveStreamFrameWeb(created.id, raw);
      }, 950);
    } catch (err: unknown) {
      stopHostCamera();
      alert(err instanceof Error ? err.message : 'Unable to start live stream.');
    } finally {
      setStartingLive(false);
    }
  };

  const handleCloseLiveModal = async () => {
    if (activeStream && activeStream.host.id === currentUser.id) {
      await endLiveStreamWeb(activeStream.id);
    }
    stopHostCamera();
    setActiveStream(null);
    await loadExploreAll();
  };

  const handleSendLiveComment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!activeStream || !commentText.trim() || isGuest) return;
    const body = commentText.trim();
    setCommentText('');
    await postLiveStreamComment({
      streamId: activeStream.id,
      authorId: currentUser.id,
      authorName: currentUser.displayName,
      content: body
    });
    const refreshed = await fetchLiveStreamComments(activeStream.id);
    setStreamComments(refreshed);
  };

  const handleCreatePoll = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isGuest || !pollQuestion.trim() || creatingPoll) return;
    setCreatingPoll(true);
    try {
      await createAudiencePollWeb({
        creatorId: currentUser.id,
        question: pollQuestion.trim(),
        category: 'WhatNext',
        options: [pollOptA.trim() || 'Option A', pollOptB.trim() || 'Option B']
      });
      setShowCreatePoll(false);
      setPollQuestion('');
      setPollOptA('');
      setPollOptB('');
      setPolls(await fetchAudiencePolls(currentUser.id));
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Unable to create poll');
    } finally {
      setCreatingPoll(false);
    }
  };

  const handleVotePoll = async (pollId: string, optionId: string) => {
    if (isGuest) return;
    await voteAudiencePollWeb(pollId, optionId, currentUser.id);
    setPolls(await fetchAudiencePolls(currentUser.id));
  };

  // Live search debounce
  useEffect(() => {
    const clean = searchQuery.trim();
    if (clean.length < 2) {
      setSearchResults(null);
      return;
    }
    const timer = setTimeout(async () => {
      try {
        const results = await searchUsersAndPosts(clean);
        setSearchResults(results);
      } catch {
        // Fallback
      }
    }, 320);
    return () => clearTimeout(timer);
  }, [searchQuery]);

  const displayedPosts = searchResults
    ? searchResults.posts
    : selectedMood
      ? allPosts.filter((p) => matchesMoodFilter(p, selectedMood))
      : allPosts;

  return (
    <div style={{ maxWidth: '960px', margin: '0 auto', padding: '24px 16px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
      {/* Header & Search */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '10px', flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <Compass size={28} color="var(--vybe-cyan)" />
            <h2 className="vybe-brand-font" style={{ fontSize: '1.6rem', fontWeight: 800, margin: 0 }}>
              Explore & Live Zone
            </h2>
          </div>
          {!isGuest && (
            <div style={{ display: 'flex', gap: '8px' }}>
              <button
                type="button"
                className="vybe-btn"
                onClick={() => setShowCreatePoll(true)}
                style={{ background: 'var(--bg-elevated)', border: '1px solid var(--border-subtle)', fontSize: '0.82rem', padding: '8px 14px' }}
              >
                <BarChart2 size={15} color="var(--vybe-cyan)" />
                <span>New Poll</span>
              </button>
              <button
                type="button"
                className="vybe-btn"
                onClick={() => setShowGoLiveModal(true)}
                style={{ background: '#FF4D6D', color: '#fff', fontSize: '0.82rem', padding: '8px 14px' }}
              >
                <Radio size={15} />
                <span>Go Live</span>
              </button>
            </div>
          )}
        </div>

        {/* Search Bar */}
        <div style={{ position: 'relative', width: '100%' }}>
          <Search size={18} color="var(--text-muted)" style={{ position: 'absolute', left: '16px', top: '50%', transform: 'translateY(-50%)' }} />
          <input
            type="text"
            placeholder="Search creators, @username, #hashtags, or moods..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            style={{
              width: '100%',
              background: 'var(--bg-surface)',
              border: '1px solid var(--border-subtle)',
              borderRadius: 'var(--radius-pill)',
              padding: '12px 18px 12px 44px',
              color: 'var(--text-primary)',
              fontSize: '0.95rem',
              outline: 'none'
            }}
          />
          {searchQuery && (
            <button
              onClick={() => setSearchQuery('')}
              style={{ position: 'absolute', right: '14px', top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer' }}
            >
              <X size={16} />
            </button>
          )}
        </div>

        {/* Mood Zone Chips */}
        <div style={{ display: 'flex', gap: '8px', overflowX: 'auto', paddingBottom: '4px', scrollbarWidth: 'none' }}>
          <button
            onClick={() => setSelectedMood(null)}
            className="vybe-btn"
            style={{
              padding: '6px 14px',
              fontSize: '0.82rem',
              background: selectedMood === null ? 'var(--vybe-gradient)' : 'var(--bg-surface)',
              color: selectedMood === null ? '#fff' : 'var(--text-secondary)',
              border: '1px solid var(--border-subtle)'
            }}
          >
            All Moods
          </button>
          {MOOD_OPTIONS.map((mood) => {
            const isSelected = selectedMood === mood.key;
            return (
              <button
                key={mood.key}
                onClick={() => setSelectedMood(isSelected ? null : mood.key)}
                className="vybe-btn"
                style={{
                  padding: '6px 14px',
                  fontSize: '0.82rem',
                  background: isSelected ? 'var(--bg-elevated)' : 'var(--bg-surface)',
                  color: isSelected ? mood.color : 'var(--text-secondary)',
                  border: isSelected ? `1px solid ${mood.color}` : '1px solid var(--border-subtle)',
                  whiteSpace: 'nowrap'
                }}
              >
                <span>{mood.emoji}</span>
                <span>{mood.label}</span>
              </button>
            );
          })}
        </div>
      </div>

      {/* Live Zone Strip (Synced Across Android & Web) */}
      {!searchResults && (
        <div
          className="vybe-card"
          style={{
            border: '1px solid rgba(255, 77, 109, 0.38)',
            display: 'flex',
            flexDirection: 'column',
            gap: '12px'
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <span style={{ width: 9, height: 9, borderRadius: '50%', background: '#FF4D6D', display: 'inline-block' }} />
              <span style={{ fontWeight: 800, fontSize: '0.95rem' }}>
                Live Zone ({liveStreams.length} on air)
              </span>
            </div>
            {!isGuest && (
              <button
                type="button"
                className="vybe-btn"
                onClick={() => setShowGoLiveModal(true)}
                style={{ padding: '5px 12px', fontSize: '0.78rem', background: 'rgba(255,77,109,0.16)', color: '#FF4D6D' }}
              >
                <Plus size={14} /> Start Broadcast
              </button>
            )}
          </div>

          {liveStreams.length === 0 ? (
            <p style={{ margin: 0, fontSize: '0.84rem', color: 'var(--text-muted)' }}>
              No creators are live right now. Start a live broadcast to stream across VYBE Android and Web.
            </p>
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: '10px' }}>
              {liveStreams.map((stream) => (
                <div
                  key={stream.id}
                  onClick={() => setActiveStream(stream)}
                  style={{
                    padding: '12px',
                    borderRadius: 'var(--radius-sm)',
                    background: 'var(--bg-elevated)',
                    border: '1px solid rgba(255,77,109,0.35)',
                    cursor: 'pointer',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '8px'
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '8px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', minWidth: 0 }}>
                      <VybeAvatar user={stream.host} size={32} />
                      <div style={{ minWidth: 0 }}>
                        <div style={{ fontWeight: 700, fontSize: '0.85rem', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                          {stream.host.displayName}
                        </div>
                        <div style={{ fontSize: '0.74rem', color: 'var(--text-muted)' }}>@{stream.host.username}</div>
                      </div>
                    </div>
                    <span style={{ background: '#FF4D6D', color: '#fff', fontSize: '0.68rem', fontWeight: 800, padding: '2px 7px', borderRadius: 6 }}>
                      LIVE • {stream.viewerCount}
                    </span>
                  </div>
                  <div style={{ fontWeight: 600, fontSize: '0.86rem' }}>{stream.title}</div>
                  <div style={{ fontSize: '0.74rem', color: 'var(--vybe-cyan)' }}>{stream.category}</div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Synced Community Polls (What Next) */}
      {!searchResults && polls.length > 0 && (
        <div className="vybe-card" style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <BarChart2 size={18} color="var(--vybe-cyan)" />
            <span style={{ fontWeight: 800, fontSize: '0.95rem' }}>What Next • Live Community Polls</span>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(270px, 1fr))', gap: '12px' }}>
            {polls.slice(0, 6).map((poll) => {
              const totalVotes = poll.options.reduce((acc, o) => acc + o.votesCount, 0);
              return (
                <div
                  key={poll.id}
                  style={{
                    padding: '14px',
                    borderRadius: 'var(--radius-sm)',
                    background: 'var(--bg-elevated)',
                    border: '1px solid var(--border-subtle)',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '8px'
                  }}
                >
                  <div style={{ fontSize: '0.76rem', color: 'var(--text-muted)' }}>
                    @{poll.creator.username} • {totalVotes} {totalVotes === 1 ? 'vote' : 'votes'}
                  </div>
                  <div style={{ fontWeight: 700, fontSize: '0.9rem' }}>{poll.question}</div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                    {poll.options.map((opt) => {
                      const pct = totalVotes > 0 ? Math.round((opt.votesCount * 100) / totalVotes) : 0;
                      const isVoted = poll.votedOptionId === opt.id;
                      return (
                        <button
                          key={opt.id}
                          type="button"
                          onClick={() => void handleVotePoll(poll.id, opt.id)}
                          style={{
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center',
                            padding: '8px 10px',
                            borderRadius: 8,
                            border: isVoted ? '1px solid var(--vybe-cyan)' : '1px solid var(--border-subtle)',
                            background: isVoted ? 'rgba(0,229,255,0.14)' : 'var(--bg-surface)',
                            color: 'var(--text-primary)',
                            cursor: isGuest ? 'default' : 'pointer',
                            fontSize: '0.82rem'
                          }}
                        >
                          <span>{opt.text}</span>
                          <span style={{ fontWeight: 700, color: 'var(--vybe-cyan)' }}>{pct}%</span>
                        </button>
                      );
                    })}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Search Results - Creators preview */}
      {searchResults && searchResults.users.length > 0 && (
        <div className="vybe-card" style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          <h4 style={{ margin: 0, fontSize: '0.95rem', color: 'var(--text-secondary)' }}>Matching People</h4>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: '12px' }}>
            {searchResults.users.map((u) => (
              <div
                key={u.id}
                onClick={() => onOpenUserProfile?.(u)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '12px',
                  padding: '10px 14px',
                  background: 'var(--bg-elevated)',
                  borderRadius: 'var(--radius-sm)',
                  cursor: 'pointer'
                }}
              >
                <VybeAvatar user={u} size={40} />
                <div style={{ minWidth: 0, flex: 1 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                    <span style={{ fontSize: '0.9rem', fontWeight: 700, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {u.displayName}
                    </span>
                    <VerifiedBadge user={u} />
                  </div>
                  <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>@{u.username}</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Discovery Posts Grid */}
      {loading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: '60px 0', color: 'var(--text-secondary)' }}>
          <p>Discovering VYBE moments...</p>
        </div>
      ) : displayedPosts.length === 0 ? (
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '60px 20px', color: 'var(--text-secondary)', textAlign: 'center' }}>
          <Sparkles size={40} color="var(--vybe-violet)" style={{ marginBottom: '12px' }} />
          <h3 style={{ color: 'var(--text-primary)', marginBottom: '6px' }}>No Content Found</h3>
          <p style={{ fontSize: '0.9rem', maxWidth: '320px' }}>
            {searchQuery ? `No posts matched "${searchQuery}". Try different keywords.` : 'No posts in this mood zone yet.'}
          </p>
        </div>
      ) : (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))',
            gap: '14px'
          }}
        >
          {displayedPosts.map((post) => {
            const mediaUrl = post.mediaUrls[0];
            const isVideo = post.mediaType === 'VIDEO' || post.isVybesReel;

            return (
              <div
                key={post.id}
                onClick={() => setSelectedPost(post)}
                style={{
                  position: 'relative',
                  aspectRatio: isVideo ? '9/16' : '1/1',
                  borderRadius: 'var(--radius-md)',
                  overflow: 'hidden',
                  background: 'var(--bg-surface)',
                  border: '1px solid var(--border-subtle)',
                  cursor: 'pointer'
                }}
              >
                {mediaUrl ? (
                  isVideo ? (
                    <video src={mediaUrl} style={{ width: '100%', height: '100%', objectFit: 'cover' }} muted playsInline />
                  ) : (
                    <img src={mediaUrl} alt={post.caption || 'Post image'} style={{ width: '100%', height: '100%', objectFit: 'cover' }} loading="lazy" />
                  )
                ) : (
                  <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '20px', background: 'var(--bg-elevated)', textAlign: 'center' }}>
                    <p style={{ fontSize: '0.9rem', color: 'var(--text-primary)' }}>{post.caption}</p>
                  </div>
                )}

                {isVideo && (
                  <div style={{ position: 'absolute', top: '10px', right: '10px', background: 'rgba(0,0,0,0.6)', padding: '4px 8px', borderRadius: 'var(--radius-pill)', display: 'flex', alignItems: 'center', gap: '4px', fontSize: '0.75rem', color: '#fff' }}>
                    <Play size={12} fill="#fff" />
                    <span>Reel</span>
                  </div>
                )}

                <div
                  style={{
                    position: 'absolute',
                    bottom: 0,
                    left: 0,
                    right: 0,
                    padding: '12px 14px',
                    background: 'linear-gradient(to top, rgba(0,0,0,0.85) 0%, transparent 100%)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    color: '#fff'
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <VybeAvatar user={post.author} size={26} />
                    <span style={{ fontSize: '0.8rem', fontWeight: 600 }}>@{post.author.username}</span>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '0.8rem' }}>
                    <VybeResonanceIcon active={post.isLiked} size={16} />
                    <span>{post.likesCount}</span>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Go Live Setup Modal */}
      {showGoLiveModal && (
        <div
          style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.75)', zIndex: 95, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16 }}
          onClick={() => setShowGoLiveModal(false)}
        >
          <form
            onSubmit={handleStartLive}
            onClick={(e) => e.stopPropagation()}
            style={{ width: '100%', maxWidth: 420, background: 'var(--bg-surface)', borderRadius: 'var(--radius-lg)', border: '1px solid var(--border-subtle)', padding: 20, display: 'flex', flexDirection: 'column', gap: 14 }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h4 style={{ margin: 0, fontWeight: 800 }}>Start Live Broadcast</h4>
              <button type="button" onClick={() => setShowGoLiveModal(false)} style={{ background: 'none', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer' }}>
                <X size={18} />
              </button>
            </div>
            <input
              type="text"
              placeholder="Broadcast title..."
              value={liveTitle}
              onChange={(e) => setLiveTitle(e.target.value)}
              style={{ background: 'var(--bg-elevated)', border: '1px solid var(--border-subtle)', borderRadius: 10, padding: '10px 14px', color: 'var(--text-primary)' }}
            />
            <select
              value={liveCategory}
              onChange={(e) => setLiveCategory(e.target.value)}
              style={{ background: 'var(--bg-elevated)', border: '1px solid var(--border-subtle)', borderRadius: 10, padding: '10px 14px', color: 'var(--text-primary)' }}
            >
              <option value="LiveZone">LiveZone</option>
              <option value="Music & Jam">Music & Jam</option>
              <option value="Talk & Q&A">Talk & Q&A</option>
              <option value="Creator Lab">Creator Lab</option>
            </select>
            <button type="submit" disabled={startingLive} className="vybe-btn" style={{ background: '#FF4D6D', color: '#fff', padding: '10px 16px' }}>
              {startingLive ? 'Starting…' : 'Go Live Now'}
            </button>
          </form>
        </div>
      )}

      {/* New Poll Modal */}
      {showCreatePoll && (
        <div
          style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.75)', zIndex: 95, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16 }}
          onClick={() => setShowCreatePoll(false)}
        >
          <form
            onSubmit={handleCreatePoll}
            onClick={(e) => e.stopPropagation()}
            style={{ width: '100%', maxWidth: 420, background: 'var(--bg-surface)', borderRadius: 'var(--radius-lg)', border: '1px solid var(--border-subtle)', padding: 20, display: 'flex', flexDirection: 'column', gap: 12 }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h4 style={{ margin: 0, fontWeight: 800 }}>Create Community Poll</h4>
              <button type="button" onClick={() => setShowCreatePoll(false)} style={{ background: 'none', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer' }}>
                <X size={18} />
              </button>
            </div>
            <input
              type="text"
              placeholder="Ask the VYBE community..."
              value={pollQuestion}
              onChange={(e) => setPollQuestion(e.target.value)}
              required
              style={{ background: 'var(--bg-elevated)', border: '1px solid var(--border-subtle)', borderRadius: 10, padding: '10px 14px', color: 'var(--text-primary)' }}
            />
            <input
              type="text"
              placeholder="Option 1"
              value={pollOptA}
              onChange={(e) => setPollOptA(e.target.value)}
              required
              style={{ background: 'var(--bg-elevated)', border: '1px solid var(--border-subtle)', borderRadius: 10, padding: '10px 14px', color: 'var(--text-primary)' }}
            />
            <input
              type="text"
              placeholder="Option 2"
              value={pollOptB}
              onChange={(e) => setPollOptB(e.target.value)}
              required
              style={{ background: 'var(--bg-elevated)', border: '1px solid var(--border-subtle)', borderRadius: 10, padding: '10px 14px', color: 'var(--text-primary)' }}
            />
            <button type="submit" disabled={creatingPoll} className="vybe-btn vybe-btn-primary" style={{ padding: '10px 16px' }}>
              {creatingPoll ? 'Publishing…' : 'Publish Poll'}
            </button>
          </form>
        </div>
      )}

      {/* Active Live Broadcast Viewer / Host Modal */}
      {activeStream && (
        <div
          style={{ position: 'fixed', inset: 0, background: 'rgba(6,8,15,0.92)', zIndex: 105, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16 }}
        >
          <div style={{ width: '100%', maxWidth: 780, background: 'var(--bg-surface)', borderRadius: 'var(--radius-lg)', border: '1px solid rgba(255,77,109,0.45)', overflow: 'hidden', display: 'grid', gridTemplateColumns: '1.2fr 1fr', maxHeight: '88vh' }}>
            <div style={{ background: '#05070d', position: 'relative', minHeight: 360, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              {activeStream.host.id === currentUser.id ? (
                <video ref={hostVideoRef} autoPlay muted playsInline style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
              ) : activeStream.latestFrameBase64 ? (
                <img
                  src={activeStream.latestFrameBase64.startsWith('data:image') ? activeStream.latestFrameBase64 : `data:image/jpeg;base64,${activeStream.latestFrameBase64}`}
                  alt={activeStream.title}
                  style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                />
              ) : (
                <div style={{ textAlign: 'center', color: '#fff', padding: 24 }}>
                  <VybeAvatar user={activeStream.host} size={72} />
                  <p style={{ marginTop: 12 }}>Connecting live stream…</p>
                </div>
              )}
              <div style={{ position: 'absolute', top: 12, left: 12, display: 'flex', gap: 8, alignItems: 'center' }}>
                <span style={{ background: '#FF4D6D', color: '#fff', fontWeight: 800, fontSize: '0.75rem', padding: '4px 10px', borderRadius: 8 }}>
                  LIVE
                </span>
                <span style={{ background: 'rgba(0,0,0,0.65)', color: '#fff', fontSize: '0.75rem', padding: '4px 10px', borderRadius: 8, display: 'flex', alignItems: 'center', gap: 4 }}>
                  <Eye size={13} /> {activeStream.viewerCount}
                </span>
              </div>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', maxHeight: '88vh' }}>
              <div style={{ padding: '14px 16px', borderBottom: '1px solid var(--border-subtle)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <div style={{ fontWeight: 800, fontSize: '0.95rem' }}>{activeStream.title}</div>
                  <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>@{activeStream.host.username}</div>
                </div>
                <button
                  type="button"
                  onClick={() => void handleCloseLiveModal()}
                  className="vybe-btn"
                  style={{ background: '#FF4D6D', color: '#fff', padding: '6px 12px', fontSize: '0.78rem' }}
                >
                  {activeStream.host.id === currentUser.id ? 'End Live' : 'Leave'}
                </button>
              </div>

              <div style={{ flex: 1, overflowY: 'auto', padding: '12px 16px', display: 'flex', flexDirection: 'column', gap: 8 }}>
                {streamComments.length === 0 ? (
                  <p style={{ fontSize: '0.82rem', color: 'var(--text-muted)' }}>Live chat is open. Say hi!</p>
                ) : (
                  streamComments.map((c) => (
                    <div key={c.id} style={{ fontSize: '0.84rem' }}>
                      <strong style={{ color: 'var(--vybe-cyan)' }}>{c.authorName}: </strong>
                      <span>{c.content}</span>
                    </div>
                  ))
                )}
              </div>

              {!isGuest && (
                <form onSubmit={handleSendLiveComment} style={{ padding: 12, borderTop: '1px solid var(--border-subtle)', display: 'flex', gap: 8 }}>
                  <input
                    type="text"
                    placeholder="Comment live..."
                    value={commentText}
                    onChange={(e) => setCommentText(e.target.value)}
                    style={{ flex: 1, background: 'var(--bg-elevated)', border: '1px solid var(--border-subtle)', borderRadius: 20, padding: '8px 14px', color: 'var(--text-primary)', fontSize: '0.85rem' }}
                  />
                  <button type="submit" className="vybe-btn vybe-btn-primary" style={{ padding: '8px 12px' }}>
                    <Send size={15} />
                  </button>
                </form>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Selected Post Modal */}
      {selectedPost && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0,0,0,0.75)',
            zIndex: 90,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '16px'
          }}
          onClick={() => setSelectedPost(null)}
        >
          <div
            style={{
              width: '100%',
              maxWidth: '680px',
              background: 'var(--bg-surface)',
              borderRadius: 'var(--radius-lg)',
              border: '1px solid var(--border-subtle)',
              overflow: 'hidden',
              display: 'flex',
              flexDirection: 'column',
              maxHeight: '90vh'
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '16px 20px', borderBottom: '1px solid var(--border-subtle)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <VybeAvatar user={selectedPost.author} size={36} />
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                    <span style={{ fontWeight: 700, fontSize: '0.95rem' }}>{selectedPost.author.displayName}</span>
                    <VerifiedBadge user={selectedPost.author} />
                  </div>
                  <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>@{selectedPost.author.username}</span>
                </div>
              </div>
              <button onClick={() => setSelectedPost(null)} style={{ background: 'none', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer' }}>
                <X size={20} />
              </button>
            </div>

            <div style={{ flex: 1, overflowY: 'auto' }}>
              {selectedPost.mediaUrls[0] && (
                selectedPost.mediaType === 'VIDEO' || selectedPost.isVybesReel ? (
                  <video src={selectedPost.mediaUrls[0]} controls style={{ width: '100%', maxHeight: '420px', background: '#000' }} />
                ) : (
                  <img src={selectedPost.mediaUrls[0]} alt={selectedPost.caption} style={{ width: '100%', maxHeight: '420px', objectFit: 'contain', background: '#000' }} />
                )
              )}
              {selectedPost.caption && (
                <div style={{ padding: '16px 20px' }}>
                  <p style={{ fontSize: '0.95rem', lineHeight: '1.5', margin: 0 }}>{selectedPost.caption}</p>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
