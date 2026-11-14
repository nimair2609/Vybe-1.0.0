import React from 'react';
import { Plus } from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { VybeAvatar } from './OptimizedImage';

export const StoryCarousel: React.FC = () => {
  const { currentUser, stories, openStoryViewer, openModal } = useVybe();

  return (
    <div className="py-3 px-4 border-b border-[var(--vybe-border)] bg-[var(--vybe-bg)] overflow-x-auto no-scrollbar transition-colors">
      <div className="max-w-xl mx-auto flex items-center gap-3.5 min-w-max">
        {/* Your Story */}
        <button
          type="button"
          onClick={() => openModal('CREATE_STORY')}
          className="flex flex-col items-center gap-1.5 cursor-pointer group"
        >
          <div className="relative">
            <div className="w-[60px] h-[60px] rounded-full p-[2px] border border-[var(--vybe-border)] group-hover:border-[var(--vybe-accent)] transition-colors flex items-center justify-center">
              <VybeAvatar
                src={currentUser?.avatarUrl}
                name={currentUser?.displayName || 'Your Story'}
                size={54}
                className="w-[54px] h-[54px]"
              />
            </div>
            <div className="absolute bottom-0 right-0 p-1 rounded-full bg-[var(--vybe-accent)] text-white keep-white shadow-sm border-2 border-[var(--vybe-bg)]">
              <Plus className="w-3 h-3 stroke-[3]" />
            </div>
          </div>
          <span className="text-[11px] text-[var(--vybe-text-secondary)] font-medium truncate max-w-[64px]">
            Your Story
          </span>
        </button>

        {/* Peer Stories */}
        {stories.map((story, idx) => (
          <button
            type="button"
            key={story.id}
            onClick={() => openStoryViewer(story.author.id, idx)}
            className="flex flex-col items-center gap-1.5 cursor-pointer group"
          >
            <div
              className={`w-[60px] h-[60px] rounded-full p-[2px] transition-transform group-hover:scale-[1.03] ${
                story.isViewed
                  ? 'bg-[var(--vybe-border)]'
                  : 'bg-gradient-to-tr from-[#7C3AED] via-[#8B5CF6] to-[#2563EB]'
              }`}
            >
              <div className="w-full h-full p-[1.5px] bg-[var(--vybe-bg)] rounded-full flex items-center justify-center">
                <VybeAvatar
                  src={story.author.avatarUrl}
                  name={story.author.displayName || story.author.username}
                  size={52}
                  className="w-[52px] h-[52px]"
                />
              </div>
            </div>
            <span className="text-[11px] text-[var(--vybe-text)] font-medium truncate max-w-[64px]">
              {story.author.displayName.split(' ')[0] || story.author.username}
            </span>
          </button>
        ))}
      </div>
    </div>
  );
};
