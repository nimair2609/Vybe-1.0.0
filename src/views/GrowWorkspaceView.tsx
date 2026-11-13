import React, { useMemo, useState } from 'react';
import {
  BookOpen, Users, FolderKanban, Sparkles, ArrowRight,
  Search, PenSquare, Compass, CheckCircle2, Calendar,
  MessageSquare, Heart, Share2, Bookmark, ShieldCheck
} from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import {
  VybeProgressRing,
  VybeActivityMiniBars,
  VybeUnderlineTabs,
  VybeEmptyState
} from '../components/VybeDesignSystem';
import { ProfileView } from './ProfileView';

export const GrowWorkspaceView: React.FC = () => {
  const {
    currentUser,
    growTab,
    setGrowTab,
    communities,
    toggleJoinCommunity,
    allUsers,
    posts,
    toggleLikePost,
    openUserProfile,
    shops,
    openShopDetail,
    openModal,
    toggleFollowUser,
    followingIds
  } = useVybe();

  const [circleQuery, setCircleQuery] = useState('');
  const [circleFilter, setCircleFilter] = useState('ALL');
  const [completedTrackIds, setCompletedTrackIds] = useState<string[]>(() => {
    try {
      const saved = localStorage.getItem('vybe_grow_completed_tracks');
      return saved ? JSON.parse(saved) : [];
    } catch {
      return [];
    }
  });

  const toggleTrackComplete = (id: string) => {
    setCompletedTrackIds(prev => {
      const next = prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id];
      try {
        localStorage.setItem('vybe_grow_completed_tracks', JSON.stringify(next));
      } catch {}
      return next;
    });
  };

  if (growTab === 'PROFILE') {
    return <ProfileView />;
  }

  // Learning tracks backed by user's joined communities & platform growth journeys
  const joinedCommunitiesCount = communities.filter(c => c.isJoined).length;
  const progressNumerator = joinedCommunitiesCount + completedTrackIds.length;
  const progressDenominator = Math.max(communities.length, 4);
  const progressPct = Math.min(100, Math.round((progressNumerator / progressDenominator) * 100));

  const activityBars = useMemo(() => {
    const counts = posts.slice(0, 10).map(p => Math.min(1, Math.max(0.18, (p.likesCount + p.comments.length * 2) / 30)));
    while (counts.length < 10) counts.push(0.2);
    return counts;
  }, [posts]);

  const filteredPosts = useMemo(() => {
    const q = circleQuery.trim().toLowerCase();
    return posts.filter(p => {
      if (!q) return true;
      return (
        p.caption.toLowerCase().includes(q) ||
        p.author.displayName.toLowerCase().includes(q) ||
        p.author.username.toLowerCase().includes(q)
      );
    });
  }, [posts, circleQuery]);

  const filteredMembers = useMemo(() => {
    const q = circleQuery.trim().toLowerCase();
    return allUsers.filter(u => {
      if (!q) return true;
      return (
        u.displayName.toLowerCase().includes(q) ||
        u.username.toLowerCase().includes(q) ||
        (u.bio || '').toLowerCase().includes(q)
      );
    });
  }, [allUsers, circleQuery]);

  // Tab 1: GROW HOME (Matches Screen 3 of Reference Sheet)
  if (growTab === 'HOME') {
    return (
      <div className="max-w-2xl mx-auto px-4 pt-4 pb-28 space-y-5">
        {/* Soft Lavender-Blue Welcome Hero Banner */}
        <section
          className="rounded-3xl p-5 border border-[var(--vybe-border)] shadow-sm relative overflow-hidden"
          style={{
            background: 'linear-gradient(135deg, var(--vybe-hero-start), var(--vybe-hero-end))'
          }}
        >
          <div className="relative z-10 max-w-sm">
            <h1 className="text-xl sm:text-2xl font-extrabold text-[var(--vybe-text)] tracking-tight">
              Welcome back, {currentUser.displayName.split(' ')[0]}
            </h1>
            <p className="text-xs sm:text-sm text-[var(--vybe-text-secondary)] mt-1">
              Keep learning. Keep growing.
            </p>
            <button
              onClick={() => setGrowTab('PROJECTS')}
              className="mt-4 inline-flex items-center gap-2 px-4 py-2 rounded-full bg-[#0F172A] text-white keep-white text-xs font-bold hover:opacity-90 transition-opacity"
            >
              <span>Continue Learning</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </button>
          </div>
        </section>

        {/* Your Progress Card */}
        <section className="space-y-2.5">
          <div className="flex items-center justify-between px-0.5">
            <h2 className="text-sm font-bold text-[var(--vybe-text)]">Your Progress</h2>
            <button
              onClick={() => setGrowTab('PROJECTS')}
              className="text-xs font-semibold text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-accent)] transition-colors"
            >
              View all &rsaquo;
            </button>
          </div>

          <div className="rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] p-4 flex items-center gap-4">
            <VybeProgressRing percent={progressPct} size={72} />
            <div className="flex-1 min-w-0">
              <div className="flex items-center justify-between">
                <p className="text-sm font-bold text-[var(--vybe-text)]">Skill Development</p>
                <span className="text-[11px] font-semibold text-[var(--vybe-accent)]">
                  {joinedCommunitiesCount} Circles
                </span>
              </div>
              <p className="text-xs text-[var(--vybe-text-secondary)] mt-0.5">
                {progressNumerator} of {progressDenominator} milestones active
              </p>
              <div className="mt-2">
                <VybeActivityMiniBars values={activityBars} />
              </div>
            </div>
          </div>
        </section>

        {/* Quick Access 4-Tile Row */}
        <section className="space-y-2.5">
          <h2 className="text-sm font-bold text-[var(--vybe-text)] px-0.5">Quick Access</h2>
          <div className="grid grid-cols-4 gap-2.5">
            {[
              { id: 'LEARNING', label: 'Learning', icon: BookOpen, onClick: () => setGrowTab('PROJECTS') },
              { id: 'CIRCLE', label: 'Circle', icon: Users, onClick: () => setGrowTab('CIRCLE') },
              { id: 'PROJECTS', label: 'Projects', icon: FolderKanban, onClick: () => setGrowTab('PROJECTS') },
              { id: 'OPPORTUNITIES', label: 'Opportunities', icon: Sparkles, onClick: () => setGrowTab('OPPORTUNITIES') },
            ].map(tile => {
              const Icon = tile.icon;
              return (
                <button
                  key={tile.id}
                  onClick={tile.onClick}
                  className="flex flex-col items-center justify-center gap-2 py-3.5 px-2 rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] transition-colors"
                >
                  <div className="w-10 h-10 rounded-full bg-[var(--vybe-accent-soft)] text-[var(--vybe-accent)] flex items-center justify-center">
                    <Icon className="w-5 h-5" />
                  </div>
                  <span className="text-[11px] font-semibold text-[var(--vybe-text)] truncate max-w-full">
                    {tile.label}
                  </span>
                </button>
              );
            })}
          </div>
        </section>

        {/* Featured Card */}
        <section className="space-y-2.5">
          <h2 className="text-sm font-bold text-[var(--vybe-text)] px-0.5">Featured</h2>
          <div
            className="rounded-3xl p-5 border border-[var(--vybe-border)] flex items-center justify-between gap-4"
            style={{
              background: 'linear-gradient(135deg, var(--vybe-hero-start), var(--vybe-hero-end))'
            }}
          >
            <div className="space-y-1.5 max-w-xs">
              <h3 className="text-base font-extrabold text-[var(--vybe-text)]">Build Your Future</h3>
              <p className="text-xs text-[var(--vybe-text-secondary)] leading-relaxed">
                Join your builder community and unlock creator collaborations across VYBE Grow.
              </p>
              <button
                onClick={() => setGrowTab('CIRCLE')}
                className="mt-2 inline-flex items-center gap-1.5 px-3.5 py-2 rounded-full bg-[#0F172A] text-white keep-white text-xs font-bold hover:opacity-90"
              >
                <span>Explore</span>
                <ArrowRight className="w-3.5 h-3.5" />
              </button>
            </div>
            <div className="w-20 h-20 rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] flex items-center justify-center shrink-0">
              <Compass className="w-9 h-9 text-[var(--vybe-accent)]" />
            </div>
          </div>
        </section>
      </div>
    );
  }

  // Tab 2: GROW CIRCLE (Matches Screen 4 of Reference Sheet)
  if (growTab === 'CIRCLE') {
    return (
      <div className="max-w-2xl mx-auto px-4 pt-4 pb-28 space-y-4 relative">
        <div>
          <h1 className="text-2xl font-extrabold text-[var(--vybe-text)] tracking-tight">Circle</h1>
          <p className="text-xs text-[var(--vybe-text-secondary)] mt-0.5">
            Connect with like-minded builders & peers
          </p>
        </div>

        <div className="relative">
          <Search className="w-4 h-4 text-[var(--vybe-text-muted)] absolute left-3.5 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={circleQuery}
            onChange={e => setCircleQuery(e.target.value)}
            placeholder="Search Circle..."
            className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-sm text-[var(--vybe-text)] placeholder-[var(--vybe-text-muted)] focus:outline-none focus:border-[var(--vybe-accent)]"
          />
        </div>

        <VybeUnderlineTabs
          tabs={[
            { id: 'ALL', label: 'All' },
            { id: 'DISCUSSIONS', label: 'Discussions' },
            { id: 'MEMBERS', label: 'Members' },
            { id: 'COMMUNITIES', label: 'Communities' }
          ]}
          activeTab={circleFilter}
          onSelect={setCircleFilter}
        />

        {(circleFilter === 'ALL' || circleFilter === 'DISCUSSIONS') && (
          <div className="space-y-3">
            {filteredPosts.length === 0 ? (
              <VybeEmptyState
                icon={MessageSquare}
                title="No discussions found"
                subtitle="Share an idea or project update to start a Circle discussion."
                actionLabel="Start Discussion"
                onAction={() => openModal('CREATE_POST')}
              />
            ) : (
              filteredPosts.map(post => (
                <article
                  key={post.id}
                  className="rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] p-4 space-y-3"
                >
                  <div className="flex items-center gap-3">
                    <button onClick={() => openUserProfile(post.author)} className="shrink-0">
                      <img
                        src={post.author.avatarUrl}
                        alt={post.author.displayName}
                        className="w-10 h-10 rounded-full object-cover border border-[var(--vybe-border)]"
                      />
                    </button>
                    <div className="min-w-0 flex-1">
                      <button
                        onClick={() => openUserProfile(post.author)}
                        className="text-sm font-bold text-[var(--vybe-text)] truncate block hover:underline"
                      >
                        {post.author.displayName}
                      </button>
                      <p className="text-[11px] text-[var(--vybe-text-muted)]">{post.timestampLabel}</p>
                    </div>
                  </div>

                  {post.caption && (
                    <p className="text-sm text-[var(--vybe-text)] leading-relaxed">{post.caption}</p>
                  )}

                  {post.mediaUrl && (
                    <img
                      src={post.mediaUrl}
                      alt={post.caption || 'Discussion media'}
                      className="w-full max-h-64 object-cover rounded-xl border border-[var(--vybe-border)]"
                    />
                  )}

                  <div className="flex items-center justify-between pt-2 border-t border-[var(--vybe-border)] text-xs text-[var(--vybe-text-secondary)]">
                    <div className="flex items-center gap-5">
                      <button
                        onClick={() => toggleLikePost(post.id)}
                        className={`flex items-center gap-1.5 font-semibold ${
                          post.isLikedByMe ? 'text-rose-500' : 'hover:text-[var(--vybe-text)]'
                        }`}
                      >
                        <Heart className={`w-4 h-4 ${post.isLikedByMe ? 'fill-current' : ''}`} />
                        <span>{post.likesCount}</span>
                      </button>
                      <span className="flex items-center gap-1.5 font-semibold">
                        <MessageSquare className="w-4 h-4" />
                        <span>{post.comments.length}</span>
                      </span>
                      <span className="flex items-center gap-1.5 font-semibold">
                        <Share2 className="w-4 h-4" />
                        <span>{post.sharesCount}</span>
                      </span>
                    </div>
                    <Bookmark className="w-4 h-4" />
                  </div>
                </article>
              ))
            )}
          </div>
        )}

        {circleFilter === 'MEMBERS' && (
          <div className="space-y-2.5">
            {filteredMembers.length === 0 ? (
              <VybeEmptyState
                icon={Users}
                title="No matching members"
                subtitle="Try another name or clear your search filter."
              />
            ) : (
              filteredMembers.map(member => {
                const isFollowing = followingIds.includes(member.id);
                return (
                  <div
                    key={member.id}
                    className="rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] p-3.5 flex items-center gap-3"
                  >
                    <button onClick={() => openUserProfile(member)} className="shrink-0">
                      <img
                        src={member.avatarUrl}
                        alt={member.displayName}
                        className="w-11 h-11 rounded-full object-cover border border-[var(--vybe-border)]"
                      />
                    </button>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-1">
                        <button
                          onClick={() => openUserProfile(member)}
                          className="text-sm font-bold text-[var(--vybe-text)] truncate hover:underline"
                        >
                          {member.displayName}
                        </button>
                        {member.isVerified && <ShieldCheck className="w-3.5 h-3.5 text-[var(--vybe-accent)] shrink-0" />}
                      </div>
                      <p className="text-xs text-[var(--vybe-text-secondary)] truncate">@{member.username}</p>
                    </div>
                    {member.id !== currentUser.id && (
                      <button
                        onClick={() => toggleFollowUser(member.id)}
                        className={`px-3.5 py-1.5 rounded-xl text-xs font-bold transition-colors ${
                          isFollowing
                            ? 'bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[var(--vybe-text)]'
                            : 'bg-[var(--vybe-accent)] text-white keep-white'
                        }`}
                      >
                        {isFollowing ? 'Connected' : 'Connect'}
                      </button>
                    )}
                  </div>
                );
              })
            )}
          </div>
        )}

        {circleFilter === 'COMMUNITIES' && (
          <div className="space-y-3">
            {communities.length === 0 ? (
              <VybeEmptyState
                icon={Users}
                title="No circles available"
                subtitle="Circles synced from the backend will appear here."
              />
            ) : (
              communities.map(comm => (
                <div
                  key={comm.id}
                  className="rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] p-4 flex items-center justify-between gap-3"
                >
                  <div className="min-w-0">
                    <h3 className="text-sm font-bold text-[var(--vybe-text)]">{comm.name}</h3>
                    <p className="text-xs text-[var(--vybe-text-secondary)] mt-0.5 line-clamp-2">{comm.description}</p>
                    <p className="text-[11px] font-semibold text-[var(--vybe-accent)] mt-1">
                      {comm.membersCount} members · {comm.category}
                    </p>
                  </div>
                  <button
                    onClick={() => toggleJoinCommunity(comm.id)}
                    className={`px-4 py-2 rounded-xl text-xs font-bold shrink-0 ${
                      comm.isJoined
                        ? 'bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[var(--vybe-text)]'
                        : 'bg-[var(--vybe-accent)] text-white keep-white'
                    }`}
                  >
                    {comm.isJoined ? 'Joined' : 'Join'}
                  </button>
                </div>
              ))
            )}
          </div>
        )}

        {/* Compose Floating Action Button matching Screen 4 */}
        <button
          onClick={() => openModal('CREATE_POST')}
          title="Post in Circle"
          className="fixed bottom-20 right-5 w-13 h-13 p-3.5 rounded-full bg-[var(--vybe-accent)] text-white keep-white shadow-lg hover:opacity-95 transition-opacity z-30"
        >
          <PenSquare className="w-5 h-5" />
        </button>
      </div>
    );
  }

  // Tab 3: PROJECTS & LEARNING TRACKS
  if (growTab === 'PROJECTS') {
    return (
      <div className="max-w-2xl mx-auto px-4 pt-4 pb-28 space-y-4">
        <div>
          <h1 className="text-2xl font-extrabold text-[var(--vybe-text)] tracking-tight">Projects & Tracks</h1>
          <p className="text-xs text-[var(--vybe-text-secondary)] mt-0.5">
            Track your active VYBE community initiatives
          </p>
        </div>

        {communities.length === 0 ? (
          <VybeEmptyState
            icon={FolderKanban}
            title="No active tracks yet"
            subtitle="Join a community circle to start collaborating on group initiatives."
            actionLabel="Browse Circle"
            onAction={() => setGrowTab('CIRCLE')}
          />
        ) : (
          <div className="space-y-3">
            {communities.map(comm => {
              const isDone = completedTrackIds.includes(comm.id);
              return (
                <div
                  key={comm.id}
                  className="rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] p-4 flex items-start justify-between gap-3"
                >
                  <div className="space-y-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="px-2 py-0.5 rounded-md bg-[var(--vybe-accent-soft)] text-[var(--vybe-accent)] text-[10px] font-bold">
                        {comm.category}
                      </span>
                      <span className="text-[11px] text-[var(--vybe-text-muted)]">
                        {comm.membersCount} builders
                      </span>
                    </div>
                    <h3 className="text-sm font-bold text-[var(--vybe-text)]">{comm.name}</h3>
                    <p className="text-xs text-[var(--vybe-text-secondary)] leading-relaxed">{comm.description}</p>
                  </div>
                  <button
                    onClick={() => toggleTrackComplete(comm.id)}
                    className={`px-3 py-2 rounded-xl text-xs font-bold flex items-center gap-1.5 shrink-0 ${
                      isDone
                        ? 'bg-emerald-500/15 text-emerald-500 border border-emerald-500/30'
                        : 'bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[var(--vybe-text)]'
                    }`}
                  >
                    <CheckCircle2 className="w-3.5 h-3.5" />
                    <span>{isDone ? 'Active' : 'Track'}</span>
                  </button>
                </div>
              );
            })}
          </div>
        )}
      </div>
    );
  }

  // Tab 4: OPPORTUNITIES (Creator Shops, Collaborations & Verification)
  return (
    <div className="max-w-2xl mx-auto px-4 pt-4 pb-28 space-y-4">
      <div>
        <h1 className="text-2xl font-extrabold text-[var(--vybe-text)] tracking-tight">Opportunities</h1>
        <p className="text-xs text-[var(--vybe-text-secondary)] mt-0.5">
          Creator hubs, verification & curated workspaces
        </p>
      </div>

      <div className="rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] p-4 flex items-center justify-between gap-3">
        <div>
          <h3 className="text-sm font-bold text-[var(--vybe-text)]">Creator Verification Center</h3>
          <p className="text-xs text-[var(--vybe-text-secondary)] mt-0.5">
            Apply for verified builder badges and showcase privileges.
          </p>
        </div>
        <button
          onClick={() => openModal('VERIFICATION_CENTER')}
          className="px-4 py-2 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold shrink-0"
        >
          Open
        </button>
      </div>

      {shops.length === 0 ? (
        <VybeEmptyState
          icon={Calendar}
          title="No creator storefronts listed yet"
          subtitle="Verified creator storefronts and local opportunities will appear here once published."
        />
      ) : (
        <div className="space-y-3">
          {shops.map(shop => (
            <div
              key={shop.id}
              className="rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] p-4 flex items-center justify-between gap-3"
            >
              <div className="flex items-center gap-3 min-w-0">
                <img
                  src={shop.logoUrl}
                  alt={shop.name}
                  className="w-12 h-12 rounded-xl object-cover border border-[var(--vybe-border)] shrink-0"
                />
                <div className="min-w-0">
                  <h3 className="text-sm font-bold text-[var(--vybe-text)] truncate">{shop.name}</h3>
                  <p className="text-xs text-[var(--vybe-text-secondary)] truncate">{shop.category} · {shop.city}</p>
                </div>
              </div>
              <button
                onClick={() => openShopDetail(shop)}
                className="px-3.5 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs font-bold text-[var(--vybe-text)] hover:border-[var(--vybe-accent)] shrink-0"
              >
                Explore
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
