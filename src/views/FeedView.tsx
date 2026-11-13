import React from 'react';
import { Sparkles, PlaySquare, RefreshCw } from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { StoryCarousel } from '../components/StoryCarousel';
import { PostCard } from '../components/PostCard';
import { FindYourVybeSection } from '../components/FindYourVybeSection';
import { VybeEmptyState } from '../components/VybeDesignSystem';
import { MOOD_DEFINITIONS, MoodType } from '../types';

export const FeedView: React.FC = () => {
  const {
    posts,
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
    <div className="w-full pb-28">
      {/* Update Banner */}
      {inAppNotificationBanner && (
        <div className="bg-[var(--vybe-accent)] text-white keep-white px-4 py-2.5 flex items-center justify-between text-xs shadow-sm">
          <div className="flex items-center gap-2">
            <Sparkles className="w-4 h-4 shrink-0" />
            <span className="font-semibold">{inAppNotificationBanner}</span>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => openModal('GITHUB_UPDATES')}
              className="px-2.5 py-1 bg-black/25 hover:bg-black/40 rounded-lg text-white keep-white font-bold text-[11px] transition-colors"
            >
              View
            </button>
            <button
              onClick={dismissBanner}
              className="p-1 hover:bg-black/20 rounded-md text-white/85 keep-white"
            >
              ✕
            </button>
          </div>
        </div>
      )}

      {/* Stories Carousel */}
      <StoryCarousel />

      {/* Mood + Reels Filter Pill Bar */}
      <div className="py-2.5 px-4 bg-[var(--vybe-nav-bg)] backdrop-blur-md sticky top-[57px] z-30 border-b border-[var(--vybe-border)] overflow-x-auto no-scrollbar transition-colors">
        <div className="max-w-xl mx-auto flex items-center gap-2 min-w-max">
          <button
            onClick={() => setSelectedMoodFilter('ALL')}
            className={`px-3.5 py-1.5 rounded-full text-xs font-bold transition-colors ${
              selectedMoodFilter === 'ALL'
                ? 'bg-[var(--vybe-accent)] text-white keep-white'
                : 'bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)]'
            }`}
          >
            For You
          </button>

          <button
            onClick={() => setSelectedTab('VYBES')}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[var(--vybe-text)] hover:border-[var(--vybe-accent)] transition-colors"
          >
            <PlaySquare className="w-3.5 h-3.5 text-[var(--vybe-accent)]" />
            <span>Reels</span>
          </button>

          {(Object.keys(MOOD_DEFINITIONS) as MoodType[]).map(m => {
            const config = MOOD_DEFINITIONS[m];
            const isSelected = selectedMoodFilter === m;
            return (
              <button
                key={m}
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

      {/* Main Feed Column */}
      <main className="max-w-xl mx-auto px-4 py-4 space-y-4">
        <FindYourVybeSection />

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
    </div>
  );
};
