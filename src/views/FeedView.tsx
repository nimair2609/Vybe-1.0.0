import React from 'react';
import {
  Sparkles, RefreshCw, X, PlusCircle, Camera, Users, MapPin, Compass
} from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { StoryCarousel } from '../components/StoryCarousel';
import { PostCard } from '../components/PostCard';
import { FindYourVybeSection } from '../components/FindYourVybeSection';
import { VybeEmptyState, VybeCard } from '../components/VybeDesignSystem';
import { OfficialBadge } from '../components/Badges';
import { MOOD_DEFINITIONS, MoodType } from '../types';

export const FeedView: React.FC = () => {
  const {
    currentUser,
    posts,
    communities,
    selectedMoodFilter,
    setSelectedMoodFilter,
    setSelectedTab,
    openModal,
    inAppNotificationBanner,
    dismissBanner,
    hasMorePosts,
    loadMorePosts,
    isLoadingMorePosts
  } = useVybe();

  const filteredPosts = posts.filter(post => {
    if (selectedMoodFilter === 'ALL') return true;
    if (selectedMoodFilter === 'GENERAL') {
      return !post.moodZone || post.moodZone === 'GENERAL';
    }
    return post.moodZone === selectedMoodFilter;
  });

  return (
    <div className="w-full pb-24 lg:pb-12">
      {/* Update / In-App Banner */}
      {inAppNotificationBanner && (
        <div className="bg-[var(--vybe-accent)] text-white keep-white px-4 py-2.5 flex items-center justify-between text-xs shadow-sm">
          <div className="flex items-center gap-2 max-w-6xl mx-auto w-full justify-between">
            <div className="flex items-center gap-2">
              <Sparkles className="w-4 h-4 shrink-0" />
              <span className="font-semibold">{inAppNotificationBanner}</span>
            </div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => openModal('GITHUB_UPDATES')}
                className="px-2.5 py-1 bg-black/25 hover:bg-black/40 rounded-lg text-white keep-white font-bold text-[11px] transition-colors"
              >
                View
              </button>
              <button
                type="button"
                onClick={dismissBanner}
                aria-label="Dismiss banner"
                className="p-1 hover:bg-black/20 rounded-md text-white/90 keep-white"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Stories Carousel */}
      <StoryCarousel />

      {/* Mood Filter Bar */}
      <div className="py-2.5 px-4 bg-[var(--vybe-nav-bg)] backdrop-blur-md sticky top-[92px] lg:top-[56px] z-30 border-b border-[var(--vybe-border)] overflow-x-auto no-scrollbar transition-colors">
        <div className="max-w-6xl mx-auto flex items-center gap-2 min-w-max">
          <button
            type="button"
            onClick={() => setSelectedMoodFilter('ALL')}
            className={`px-3.5 py-1.5 rounded-full text-xs font-bold transition-colors ${
              selectedMoodFilter === 'ALL'
                ? 'bg-[var(--vybe-accent)] text-white keep-white'
                : 'bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)]'
            }`}
          >
            For You
          </button>

          {(Object.keys(MOOD_DEFINITIONS) as MoodType[]).map(m => {
            const config = MOOD_DEFINITIONS[m];
            const isSelected = selectedMoodFilter === m;
            return (
              <button
                key={m}
                type="button"
                onClick={() => setSelectedMoodFilter(m)}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold transition-colors ${
                  isSelected
                    ? 'bg-[var(--vybe-accent)] text-white keep-white'
                    : 'bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)]'
                }`}
              >
                <span>{config.emoji}</span>
                <span>{config.title}</span>
              </button>
            );
          })}
        </div>
      </div>

      {/* Responsive Content Container: Centered Single Column on Mobile, 2-Column Editorial Layout on Desktop */}
      <div className="max-w-6xl mx-auto px-4 pt-5 grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        {/* Main Feed Column */}
        <main className="lg:col-span-7 xl:col-span-7 max-w-xl w-full mx-auto lg:mx-0 lg:max-w-none space-y-4">
          {/* Quick Post Composer Card */}
          <VybeCard padding="sm" className="flex items-center gap-3">
            <img
              src={currentUser.avatarUrl}
              alt={currentUser.displayName}
              className="w-10 h-10 rounded-full object-cover border border-[var(--vybe-border)] shrink-0"
            />
            <button
              type="button"
              onClick={() => openModal('CREATE_POST')}
              className="flex-1 text-left px-4 py-2.5 rounded-full bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] text-xs text-[var(--vybe-text-secondary)] transition-colors"
            >
              Share a moment, photo, or mood vibe...
            </button>
            <button
              type="button"
              onClick={() => openModal('CREATE_STORY')}
              title="Add Story"
              className="p-2.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-accent)] transition-colors shrink-0"
            >
              <Camera className="w-4 h-4" />
            </button>
            <button
              type="button"
              onClick={() => openModal('CREATE_POST')}
              title="Create Post"
              className="hidden sm:inline-flex items-center gap-1.5 px-3.5 py-2.5 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold hover:opacity-95 transition-opacity shrink-0"
            >
              <PlusCircle className="w-4 h-4" />
              <span>Post</span>
            </button>
          </VybeCard>

          {/* Inline Find Your Vybe on Mobile/Tablet */}
          <div className="lg:hidden">
            <FindYourVybeSection />
          </div>

          {filteredPosts.length === 0 ? (
            <VybeEmptyState
              icon={Sparkles}
              title="No posts in this vibe yet"
              subtitle={`Share a photo, thought, or track in the ${selectedMoodFilter} feed.`}
              actionLabel="Create Post"
              onAction={() => openModal('CREATE_POST')}
            />
          ) : (
            <>
              {filteredPosts.map(post => (
                <PostCard key={post.id} post={post} />
              ))}

              {hasMorePosts ? (
                <div className="pt-3 pb-2 text-center">
                  <button
                    type="button"
                    onClick={() => loadMorePosts()}
                    disabled={isLoadingMorePosts}
                    className="px-6 py-2.5 rounded-full bg-[var(--vybe-surface)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] text-xs font-semibold text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)] transition-colors disabled:opacity-50 inline-flex items-center gap-2"
                  >
                    {isLoadingMorePosts ? (
                      <>
                        <RefreshCw className="w-3.5 h-3.5 animate-spin text-[var(--vybe-accent)]" />
                        <span>Loading...</span>
                      </>
                    ) : (
                      <span>Load More Posts</span>
                    )}
                  </button>
                </div>
              ) : (
                <div className="pt-4 pb-2 text-center text-xs text-[var(--vybe-text-muted)]">
                  You're all caught up
                </div>
              )}
            </>
          )}
        </main>

        {/* Desktop Sticky Right Sidebar */}
        <aside className="hidden lg:flex lg:col-span-5 xl:col-span-5 flex-col gap-4 sticky top-[120px]">
          {/* Compact Current User Card */}
          <VybeCard padding="sm" className="flex items-center justify-between gap-3">
            <div
              onClick={() => setSelectedTab('PROFILE')}
              className="flex items-center gap-3 min-w-0 cursor-pointer"
            >
              <img
                src={currentUser.avatarUrl}
                alt={currentUser.displayName}
                className="w-11 h-11 rounded-full object-cover border border-[var(--vybe-border)] shrink-0"
              />
              <div className="min-w-0">
                <div className="flex items-center gap-1">
                  <span className="text-sm font-bold text-[var(--vybe-text)] truncate hover:underline">
                    {currentUser.displayName}
                  </span>
                  {currentUser.badges?.map(b => (
                    <OfficialBadge key={b} badge={b} size="sm" />
                  ))}
                </div>
                <p className="text-xs text-[var(--vybe-text-secondary)] truncate">
                  @{currentUser.username} · {currentUser.followersCount.toLocaleString()} followers
                </p>
              </div>
            </div>
            <button
              type="button"
              onClick={() => setSelectedTab('PROFILE')}
              className="px-3 py-1.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] text-xs font-semibold text-[var(--vybe-text)] shrink-0 transition-colors"
            >
              Profile
            </button>
          </VybeCard>

          {/* Find Your Vybe Discovery */}
          <FindYourVybeSection />

          {/* Active Communities & Zones Shortcuts */}
          <VybeCard padding="sm" className="space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="text-xs font-extrabold uppercase tracking-wider text-[var(--vybe-text)]">
                Spaces & Zones
              </h3>
              <button
                type="button"
                onClick={() => setSelectedTab('EXPLORE')}
                className="text-[11px] font-semibold text-[var(--vybe-accent)] hover:underline"
              >
                Explore all
              </button>
            </div>
            <div className="grid grid-cols-2 gap-2">
              <button
                type="button"
                onClick={() => setSelectedTab('COMMUNITY')}
                className="flex items-center gap-2 p-2.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] text-left transition-colors"
              >
                <Users className="w-4 h-4 text-[var(--vybe-accent)] shrink-0" />
                <div className="min-w-0">
                  <p className="text-xs font-bold text-[var(--vybe-text)] truncate">Communities</p>
                  <p className="text-[10px] text-[var(--vybe-text-secondary)]">{communities.length} circles</p>
                </div>
              </button>
              <button
                type="button"
                onClick={() => setSelectedTab('CROSSED_PATHS')}
                className="flex items-center gap-2 p-2.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] text-left transition-colors"
              >
                <MapPin className="w-4 h-4 text-sky-500 shrink-0" />
                <div className="min-w-0">
                  <p className="text-xs font-bold text-[var(--vybe-text)] truncate">Crossed Paths</p>
                  <p className="text-[10px] text-[var(--vybe-text-secondary)]">Nearby radar</p>
                </div>
              </button>
              <button
                type="button"
                onClick={() => setSelectedTab('MOOD_ZONES')}
                className="flex items-center gap-2 p-2.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] text-left transition-colors"
              >
                <Sparkles className="w-4 h-4 text-amber-500 shrink-0" />
                <div className="min-w-0">
                  <p className="text-xs font-bold text-[var(--vybe-text)] truncate">Mood Zones</p>
                  <p className="text-[10px] text-[var(--vybe-text-secondary)]">6 sanctuaries</p>
                </div>
              </button>
              <button
                type="button"
                onClick={() => setSelectedTab('EXPLORE')}
                className="flex items-center gap-2 p-2.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] text-left transition-colors"
              >
                <Compass className="w-4 h-4 text-emerald-500 shrink-0" />
                <div className="min-w-0">
                  <p className="text-xs font-bold text-[var(--vybe-text)] truncate">Signature Hub</p>
                  <p className="text-[10px] text-[var(--vybe-text-secondary)]">Shops & live</p>
                </div>
              </button>
            </div>
          </VybeCard>

          {/* Subtle Platform Footer Links (Replaces Floating Widget) */}
          <div className="px-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-[var(--vybe-text-muted)]">
            <button type="button" onClick={() => openModal('SETTINGS')} className="hover:text-[var(--vybe-text)]">
              Settings
            </button>
            <span>·</span>
            <button type="button" onClick={() => openModal('GITHUB_UPDATES')} className="hover:text-[var(--vybe-text)]">
              Updates
            </button>
            <span>·</span>
            <a href="/download" target="_blank" rel="noopener noreferrer" className="hover:text-[var(--vybe-text)]">
              Android APK
            </a>
            <span>·</span>
            <a href="/admin" className="hover:text-[var(--vybe-text)]">
              Admin Portal
            </a>
          </div>
        </aside>
      </div>
    </div>
  );
};
