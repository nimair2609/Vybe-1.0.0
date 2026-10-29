import React from 'react';
import { Plus } from 'lucide-react';
import { useVybe } from '../context/VybeContext';

export const StoryCarousel: React.FC = () => {
  const { currentUser, stories, openStoryViewer, openModal } = useVybe();

  return (
    <div className="py-3 px-4 border-b border-[#262E4A]/50 bg-[#060714]/60 overflow-x-auto no-scrollbar">
      <div className="flex items-center gap-3.5 min-w-max">
        
        {/* Current user Add Story item */}
        <div 
          onClick={() => openModal('CREATE_STORY')}
          className="flex flex-col items-center gap-1.5 cursor-pointer group"
        >
          <div className="relative">
            <div className="w-16 h-16 rounded-full p-[2px] bg-gradient-to-tr from-[#262E4A] to-[#1A1F36] group-hover:from-[#00C6FF] group-hover:to-[#D946EF] transition-all">
              <img 
                src={currentUser.avatarUrl} 
                alt={currentUser.displayName}
                className="w-full h-full object-cover rounded-full"
              />
            </div>
            <div className="absolute bottom-0 right-0 p-1 rounded-full bg-gradient-to-r from-[#00C6FF] to-[#8B5CF6] text-white shadow-md border-2 border-[#060714]">
              <Plus className="w-3 h-3 stroke-[3]" />
            </div>
          </div>
          <span className="text-[11px] text-gray-300 font-medium truncate max-w-[64px]">
            Your Story
          </span>
        </div>

        {/* Stories list */}
        {stories.map((story, idx) => (
          <div
            key={story.id}
            onClick={() => openStoryViewer(story.author.id, idx)}
            className="flex flex-col items-center gap-1.5 cursor-pointer group"
          >
            <div className={`w-16 h-16 rounded-full p-[2.5px] transition-transform group-hover:scale-105 ${
              story.isViewed 
                ? 'bg-gray-600' 
                : 'bg-gradient-to-tr from-[#00C6FF] via-[#8B5CF6] to-[#D946EF] shadow-md shadow-[#8B5CF6]/30'
            }`}>
              <div className="w-full h-full p-[1.5px] bg-[#060714] rounded-full">
                <img 
                  src={story.author.avatarUrl} 
                  alt={story.author.displayName}
                  className="w-full h-full object-cover rounded-full"
                />
              </div>
            </div>
            <span className="text-[11px] text-gray-300 font-medium truncate max-w-[64px]">
              {story.author.username}
            </span>
          </div>
        ))}

      </div>
    </div>
  );
};
