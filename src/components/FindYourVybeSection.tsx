import React, { useMemo, useState } from 'react';
import { Sparkles, Users, X, ArrowRight, Lock, Check, Search } from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { OfficialBadge } from './Badges';
import { User } from '../types';

type FyvTab = 'LINK_UP' | 'SUGGEST';

interface DiscoveryCandidate {
  user: User;
  mutualCount: number;
  reason: string;
  score: number;
}

export const FindYourVybeSection: React.FC = () => {
  const {
    currentUser,
    allUsers,
    posts,
    stories,
    followingIds,
    requestedFollowIds,
    dismissedSuggestionIds,
    toggleFollowUser,
    dismissSuggestedUser,
    restoreDismissedSuggestions,
    openUserProfile,
    themeMode,
  } = useVybe();

  const [activeTab, setActiveTab] = useState<FyvTab>('LINK_UP');
  const [showSeeAll, setShowSeeAll] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  const isLight = themeMode === 'light';

  const candidates = useMemo<DiscoveryCandidate[]>(() => {
    const byId = new Map<string, User>();
    allUsers.forEach((u) => {
      if (u?.id) byId.set(u.id, u);
    });
    posts.forEach((p) => {
      if (p.author?.id && !byId.has(p.author.id)) byId.set(p.author.id, p.author);
    });
    stories.forEach((s) => {
      if (s.author?.id && !byId.has(s.author.id)) byId.set(s.author.id, s.author);
    });

    const myFollowingSet = new Set(followingIds);
    const interactedByUser = new Map<string, Set<string>>();
    posts.forEach((p) => {
      (p.comments || []).forEach((c) => {
        if (c.author?.id && p.author?.id && c.author.id !== p.author.id) {
          if (!interactedByUser.has(c.author.id)) interactedByUser.set(c.author.id, new Set());
          if (!interactedByUser.has(p.author.id)) interactedByUser.set(p.author.id, new Set());
          interactedByUser.get(c.author.id)!.add(p.author.id);
          interactedByUser.get(p.author.id)!.add(c.author.id);
        }
      });
    });

    const myTags = new Set(
      posts
        .filter((p) => p.author?.id === currentUser.id)
        .flatMap((p) => p.tags || [])
        .map((t) => t.toLowerCase())
    );

    return Array.from(byId.values())
      .filter((u) => u.id && u.id !== currentUser.id && !u.isBlocked)
      .map((u) => {
        const peers = interactedByUser.get(u.id) || new Set<string>();
        let mutuals = 0;
        let firstMutualHandle = '';
        peers.forEach((pid) => {
          if (myFollowingSet.has(pid) && pid !== u.id) {
            mutuals += 1;
            if (!firstMutualHandle) {
              firstMutualHandle = byId.get(pid)?.username || '';
            }
          }
        });

        const uTags = posts
          .filter((p) => p.author?.id === u.id)
          .flatMap((p) => p.tags || [])
          .map((t) => t.toLowerCase());
        const sharedTag = uTags.find((t) => myTags.has(t));
        const sameMood = Boolean(currentUser.currentMood && u.currentMood === currentUser.currentMood);

        let score = 0;
        if (mutuals > 0) score += 40 + mutuals * 12;
        if (sameMood) score += 24;
        if (sharedTag) score += 18;
        if (u.isOnline) score += 10;

        const reason =
          mutuals > 1
            ? `${mutuals} mutual connections`
            : mutuals === 1
              ? `Mutual connection${firstMutualHandle ? ` • @${firstMutualHandle}` : ''}`
              : sameMood
                ? `Shares your ${u.currentMood} vybe`
                : sharedTag
                  ? `Shared interest • #${sharedTag}`
                  : u.livePulse && u.livePulse !== 'None'
                    ? u.livePulse
                    : 'Discover on VYBE';

        return { user: u, mutualCount: mutuals, reason, score };
      })
      .sort((a, b) => b.score - a.score || b.user.followersCount - a.user.followersCount);
  }, [allUsers, posts, stories, followingIds, currentUser]);

  const linkUpList = useMemo(() => {
    const unlinked = candidates.filter((c) => !followingIds.includes(c.user.id));
    const linked = candidates.filter((c) => followingIds.includes(c.user.id));
    return [...unlinked, ...linked];
  }, [candidates, followingIds]);

  const suggestList = useMemo(() => {
    return candidates.filter(
      (c) => !followingIds.includes(c.user.id) && !dismissedSuggestionIds.includes(c.user.id)
    );
  }, [candidates, followingIds, dismissedSuggestionIds]);

  const activeItems = activeTab === 'LINK_UP' ? linkUpList : suggestList;

  const renderFollowButton = (u: User) => {
    const isFollowing = followingIds.includes(u.id);
    const isRequested = requestedFollowIds.includes(u.id);
    return (
      <button
        type="button"
        onClick={(e) => {
          e.stopPropagation();
          toggleFollowUser(u.id);
        }}
        className={`w-full py-2 px-3 rounded-xl text-xs font-bold transition-all ${
          isFollowing
            ? 'bg-[var(--vybe-elevated)] text-[var(--vybe-text)] border border-[var(--vybe-border)]'
            : isRequested
              ? 'bg-amber-500/15 text-amber-500 border border-amber-500/40'
              : 'bg-[var(--vybe-accent)] text-white keep-white shadow-sm hover:opacity-95'
        }`}
      >
        {isFollowing ? (
          <span className="inline-flex items-center gap-1">
            <Check className="w-3.5 h-3.5" />
            LINKED
          </span>
        ) : isRequested ? (
          'LINKUP Requested'
        ) : (
          '+ LINK UP'
        )}
      </button>
    );
  };

  return (
    <>
      <section
        data-testid="find-your-vybe-section"
        className="rounded-2xl border border-[var(--vybe-border)] bg-[var(--vybe-surface)] p-4 text-[var(--vybe-text)] transition-colors"
      >
        {/* Header */}
        <div className="flex items-center justify-between gap-2 mb-3">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-xl bg-[var(--vybe-accent-soft)] flex items-center justify-center border border-[var(--vybe-border)]">
              <Sparkles className="w-4 h-4 text-[var(--vybe-accent)]" />
            </div>
            <div>
              <h2 className="text-sm font-extrabold tracking-tight text-[var(--vybe-text)]">Find Your Vybe</h2>
              <p className="text-[11px] text-[var(--vybe-text-secondary)]">
                {activeTab === 'LINK_UP'
                  ? 'Discover people & mutual connections'
                  : 'Personalized people recommendations'}
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={() => setShowSeeAll(true)}
            className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-xs font-bold text-[var(--vybe-accent)] hover:bg-[var(--vybe-accent-soft)] transition-colors"
          >
            <span>See All</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </button>
        </div>

        {/* Neo Segmented Tabs: Link Up & Suggest */}
        <div className="grid grid-cols-2 gap-1.5 p-1 rounded-xl border border-[var(--vybe-border)] bg-[var(--vybe-elevated)] mb-3.5">
          <button
            type="button"
            onClick={() => setActiveTab('LINK_UP')}
            className={`flex items-center justify-center gap-1.5 py-2 rounded-lg text-xs font-bold transition-colors ${
              activeTab === 'LINK_UP'
                ? 'bg-[var(--vybe-accent)] text-white keep-white'
                : 'text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)]'
            }`}
          >
            <Users className="w-3.5 h-3.5" />
            <span>Link Up</span>
            <span className="px-1.5 py-0.5 rounded-full text-[10px] bg-black/20">{linkUpList.length}</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('SUGGEST')}
            className={`flex items-center justify-center gap-1.5 py-2 rounded-lg text-xs font-bold transition-colors ${
              activeTab === 'SUGGEST'
                ? 'bg-[var(--vybe-accent)] text-white keep-white'
                : 'text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)]'
            }`}
          >
            <Sparkles className="w-3.5 h-3.5" />
            <span>Suggest</span>
            <span className="px-1.5 py-0.5 rounded-full text-[10px] bg-black/20">{suggestList.length}</span>
          </button>
        </div>

        {/* Horizontal Cards */}
        {activeItems.length === 0 ? (
          <div className="rounded-xl p-4 text-center border border-[var(--vybe-border)] bg-[var(--vybe-elevated)]">
            <p className="text-xs font-bold text-[var(--vybe-text)]">
              {activeTab === 'SUGGEST' && dismissedSuggestionIds.length > 0
                ? "You're all caught up on suggestions"
                : 'No people to show right now'}
            </p>
            {activeTab === 'SUGGEST' && dismissedSuggestionIds.length > 0 && (
              <button
                type="button"
                onClick={restoreDismissedSuggestions}
                className="mt-2 px-3 py-1.5 rounded-lg text-xs font-bold bg-[var(--vybe-accent-soft)] text-[var(--vybe-accent)] hover:opacity-90"
              >
                Reset Dismissed
              </button>
            )}
          </div>
        ) : (
          <div className="flex items-stretch gap-3 overflow-x-auto no-scrollbar pb-1 transition-all duration-300">
            {activeItems.slice(0, 10).map((item) => {
              const u = item.user;
              return (
                <div
                  key={`${activeTab}_${u.id}`}
                  onClick={() => openUserProfile(u)}
                  className="relative shrink-0 w-48 rounded-2xl border border-[var(--vybe-border)] bg-[var(--vybe-elevated)] hover:border-[var(--vybe-accent)]/60 p-3.5 flex flex-col items-center text-center cursor-pointer transition-all hover:-translate-y-0.5"
                >
                  {activeTab === 'SUGGEST' && (
                    <button
                      type="button"
                      aria-label={`Dismiss ${u.displayName}`}
                      onClick={(e) => {
                        e.stopPropagation();
                        dismissSuggestedUser(u.id);
                      }}
                      className="absolute top-2 right-2 p-1.5 rounded-full bg-black/25 hover:bg-black/45 text-gray-400 hover:text-white transition-colors"
                    >
                      <X className="w-3.5 h-3.5" />
                    </button>
                  )}

                  <div className="relative mb-2">
                    <img
                      src={u.avatarUrl}
                      alt={u.displayName}
                      className="w-14 h-14 rounded-full object-cover border-2 border-[#8B5CF6]/60"
                    />
                    {u.isOnline && (
                      <span className="absolute bottom-0 right-0 w-3 h-3 rounded-full bg-emerald-500 border-2 border-[#10162B]" />
                    )}
                  </div>

                  <div className="flex items-center justify-center gap-1 max-w-full">
                    <span className="text-xs font-bold truncate">{u.displayName}</span>
                    {u.badges?.map((b) => (
                      <OfficialBadge key={b} badge={b} size="sm" />
                    ))}
                    {u.isPrivate && <Lock className="w-3 h-3 text-gray-400 shrink-0" />}
                  </div>

                  <span className="text-[11px] text-[#00C6FF] font-mono truncate max-w-full">
                    @{u.username}
                  </span>

                  <p
                    className={`text-[11px] mt-1.5 line-clamp-2 min-h-[32px] ${
                      isLight ? 'text-slate-600' : 'text-slate-400'
                    }`}
                  >
                    {u.bio || (u.isPrivate ? 'Private VYBE profile' : 'On VYBE • Tap to view profile')}
                  </p>

                  <span className="mt-2 mb-2.5 px-2.5 py-0.5 rounded-full text-[10px] font-semibold bg-[#8B5CF6]/15 text-[#A78BFA] truncate max-w-full">
                    {item.reason}
                  </span>

                  <div className="mt-auto w-full">{renderFollowButton(u)}</div>
                </div>
              );
            })}
          </div>
        )}
      </section>

      {/* See All Modal */}
      {showSeeAll && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
          <div className="w-full max-w-lg max-h-[85vh] rounded-3xl border border-[var(--vybe-border)] bg-[var(--vybe-surface)] text-[var(--vybe-text)] flex flex-col overflow-hidden shadow-2xl">
            <div className="p-4 border-b border-[var(--vybe-border)] flex items-center justify-between">
              <div>
                <h3 className="text-base font-extrabold">Find Your Vybe</h3>
                <p className="text-xs text-[var(--vybe-text-secondary)]">Link Up & Personalized Suggestions</p>
              </div>
              <button
                type="button"
                onClick={() => setShowSeeAll(false)}
                aria-label="Close"
                className="p-2 rounded-full text-[var(--vybe-text-secondary)] hover:bg-[var(--vybe-elevated)] hover:text-[var(--vybe-text)]"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="p-4 space-y-3 border-b border-[var(--vybe-border)]">
              <div className="relative">
                <Search className="w-4 h-4 text-[var(--vybe-text-muted)] absolute left-3.5 top-1/2 -translate-y-1/2" />
                <input
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Filter by name, @username, or bio..."
                  className="w-full pl-9 pr-4 py-2 rounded-xl border border-[var(--vybe-border)] bg-[var(--vybe-elevated)] text-xs text-[var(--vybe-text)] focus:outline-none focus:border-[var(--vybe-accent)]"
                />
              </div>

              <div className="grid grid-cols-2 gap-2">
                {(['LINK_UP', 'SUGGEST'] as FyvTab[]).map((t) => (
                  <button
                    key={t}
                    type="button"
                    onClick={() => setActiveTab(t)}
                    className={`py-2 rounded-xl text-xs font-bold transition-colors ${
                      activeTab === t
                        ? 'bg-[var(--vybe-accent)] text-white keep-white'
                        : 'bg-[var(--vybe-elevated)] text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)]'
                    }`}
                  >
                    {t === 'LINK_UP' ? 'Link Up' : 'Suggest'}
                  </button>
                ))}
              </div>
            </div>

            <div className="flex-1 overflow-y-auto p-4 space-y-2.5">
              {(activeTab === 'LINK_UP' ? linkUpList : suggestList)
                .filter((item) => {
                  const q = searchQuery.trim().toLowerCase();
                  if (!q) return true;
                  return (
                    item.user.displayName.toLowerCase().includes(q) ||
                    item.user.username.toLowerCase().includes(q) ||
                    (item.user.bio || '').toLowerCase().includes(q)
                  );
                })
                .map((item) => {
                  const u = item.user;
                  return (
                    <div
                      key={`modal_${activeTab}_${u.id}`}
                      onClick={() => {
                        setShowSeeAll(false);
                        openUserProfile(u);
                      }}
                      className="flex items-center justify-between gap-3 p-3 rounded-2xl border border-[var(--vybe-border)] bg-[var(--vybe-elevated)] cursor-pointer hover:border-[var(--vybe-accent)]/50 transition-colors"
                    >
                      <div className="flex items-center gap-3 min-w-0">
                        <img
                          src={u.avatarUrl}
                          alt={u.displayName}
                          className="w-11 h-11 rounded-full object-cover shrink-0"
                        />
                        <div className="min-w-0">
                          <div className="flex items-center gap-1">
                            <span className="text-xs font-bold truncate">{u.displayName}</span>
                            {u.badges?.map((b) => (
                              <OfficialBadge key={b} badge={b} size="sm" />
                            ))}
                          </div>
                          <p className="text-[11px] text-[#00C6FF] font-mono truncate">@{u.username}</p>
                          {u.bio && <p className="text-[11px] text-gray-400 truncate">{u.bio}</p>}
                          <p className="text-[10px] text-[#A78BFA] font-semibold">{item.reason}</p>
                        </div>
                      </div>

                      <div className="flex items-center gap-1.5 shrink-0">
                        <div className="w-28">{renderFollowButton(u)}</div>
                        {activeTab === 'SUGGEST' && (
                          <button
                            type="button"
                            onClick={(e) => {
                              e.stopPropagation();
                              dismissSuggestedUser(u.id);
                            }}
                            className="p-2 rounded-full hover:bg-white/10 text-gray-400"
                          >
                            <X className="w-4 h-4" />
                          </button>
                        )}
                      </div>
                    </div>
                  );
                })}
            </div>
          </div>
        </div>
      )}
    </>
  );
};
