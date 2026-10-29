import React from 'react';
import { Sparkles, Filter, Plus, Flame, Clock, RefreshCw } from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { StoryCarousel } from '../components/StoryCarousel';
import { PostCard } from '../components/PostCard';
import { MOOD_DEFINITIONS, MoodType } from '../types';

export const FeedView: React.FC = () => {
  const { 
    posts, selectedMoodFilter, setSelectedMoodFilter, 
    openModal, hasAvailableUpdate, inAppNotificationBanner, 
    dismissBanner 
  } = useVybe();

  const filteredPosts = posts.filter(post => {
    if (selectedMoodFilter === 'ALL') return true;
    if (selectedMoodFilter === 'GENERAL') {
      return !post.moodZone || post.moodZone === 'GENERAL';
    }
    return post.moodZone === selectedMoodFilter;
  });

  return (
    <div className="w-full pb-24">
      
      {/* Update Announcement Banner if present */}
      {inAppNotificationBanner && (
        <div className="bg-gradient-to-r from-[#8B5CF6]/90 to-[#00C6FF]/90 text-white px-4 py-2.5 flex items-center justify-between text-xs shadow-lg animate-fade-in">
          <div className="flex items-center gap-2">
            <Sparkles className="w-4 h-4 shrink-0" />
            <span className="font-semibold">{inAppNotificationBanner}</span>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => openModal('GITHUB_UPDATES')}
              className="px-2.5 py-1 bg-black/40 hover:bg-black/60 rounded-lg text-white font-bold text-[11px] transition-colors"
            >
              View Update
            </button>
            <button
              onClick={dismissBanner}
              className="p-1 hover:bg-black/20 rounded-md text-white/80"
            >
              ✕
            </button>
          </div>
        </div>
      )}

      {/* Stories Carousel */}
      <StoryCarousel />

      {/* Mood Filter Pill Bar */}
      <div className="py-2.5 px-4 bg-[#060714]/80 backdrop-blur sticky top-[57px] z-30 border-b border-[#262E4A]/40 overflow-x-auto no-scrollbar">
        <div className="flex items-center gap-2 min-w-max">
          <button
            onClick={() => setSelectedMoodFilter('ALL')}
            className={`px-3 py-1.5 rounded-full text-xs font-semibold transition-all ${
              selectedMoodFilter === 'ALL'
                ? 'bg-gradient-to-r from-[#00C6FF] to-[#8B5CF6] text-white shadow-md shadow-[#00C6FF]/20'
                : 'bg-[#101322] border border-[#262E4A] text-gray-400 hover:text-white'
            }`}
          >
            All Vibes
          </button>

          {(Object.keys(MOOD_DEFINITIONS) as MoodType[]).map(m => {
            const config = MOOD_DEFINITIONS[m];
            const isSelected = selectedMoodFilter === m;
            return (
              <button
                key={m}
                onClick={() => setSelectedMoodFilter(m)}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold transition-all ${
                  isSelected
                    ? `bg-gradient-to-r ${config.gradient} text-white shadow-md`
                    : 'bg-[#101322] border border-[#262E4A] text-gray-400 hover:text-white'
                }`}
              >
                <span>{config.emoji}</span>
                <span>{config.title}</span>
              </button>
            );
          })}
        </div>
      </div>

      {/* Main Stream Container */}
      <main className="max-w-xl mx-auto px-4 py-4 space-y-5">
        {filteredPosts.length === 0 ? (
          <div className="p-8 text-center bg-[#101322] border border-[#262E4A] rounded-2xl">
            <Sparkles className="w-8 h-8 text-[#00C6FF] mx-auto mb-2 opacity-80" />
            <h3 className="font-bold text-white text-base">No posts in this mood yet</h3>
            <p className="text-xs text-gray-400 mt-1 max-w-xs mx-auto">
              Be the first to share your thoughts, photos or acoustic music in the {selectedMoodFilter} zone!
            </p>
            <button
              onClick={() => openModal('CREATE_POST')}
              className="mt-4 px-4 py-2 bg-gradient-to-r from-[#00C6FF] to-[#8B5CF6] text-white font-bold text-xs rounded-xl shadow-lg"
            >
              Post a Vibe
            </button>
          </div>
        ) : (
          filteredPosts.map(post => (
            <PostCard key={post.id} post={post} />
          ))
        )}
      </main>

    </div>
  );
};
