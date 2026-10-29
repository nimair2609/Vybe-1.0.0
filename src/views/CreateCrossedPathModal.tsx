import React, { useState } from 'react';
import { Compass, X, MapPin, Send, Shield } from 'lucide-react';
import { useVybe } from '../context/VybeContext';

export const CreateCrossedPathModal: React.FC = () => {
  const { closeModal, createCrossedPath } = useVybe();

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [broadLocation, setBroadLocation] = useState('Bandra West, Mumbai');
  const [venue, setVenue] = useState('');
  const [type, setType] = useState<'MISSED_CONNECTION' | 'VENUE_EVENT' | 'BROAD_NEARBY'>('MISSED_CONNECTION');
  const [isAnonymous, setIsAnonymous] = useState(true);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !description.trim()) return;

    createCrossedPath({
      title: title.trim(),
      description: description.trim(),
      broadLocation: broadLocation.trim(),
      venueOrEventName: venue.trim() || undefined,
      type,
      isAnonymous,
      tags: ['missedconnection', 'mumbai']
    });
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/85 backdrop-blur-xl animate-fade-in">
      <div className="relative w-full max-w-lg max-h-[90vh] overflow-y-auto bg-[#0a0f1d] border border-[#262E4A] rounded-3xl shadow-2xl p-6 text-white no-scrollbar space-y-5">
        
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-[#262E4A]">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-xl bg-[#00C6FF]/10 text-[#00C6FF] border border-[#00C6FF]/30">
              <Compass className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold brand-font text-white">Post Crossed Path Encounter</h2>
              <p className="text-xs text-gray-400">Describe a moment or missed connection safely</p>
            </div>
          </div>
          <button onClick={closeModal} className="p-2 text-gray-400 hover:text-white rounded-lg">
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          
          <div>
            <label className="text-xs font-semibold text-gray-300 block mb-1">Encounter Type</label>
            <div className="grid grid-cols-3 gap-2">
              {[
                { id: 'MISSED_CONNECTION', label: 'Missed Connection' },
                { id: 'VENUE_EVENT', label: 'Venue / Gig' },
                { id: 'BROAD_NEARBY', label: 'Broad Nearby' }
              ].map(t => (
                <button
                  key={t.id}
                  type="button"
                  onClick={() => setType(t.id as any)}
                  className={`p-2 rounded-xl text-xs font-semibold transition-all border ${
                    type === t.id 
                      ? 'bg-gradient-to-r from-[#00C6FF] to-[#8B5CF6] text-white border-transparent' 
                      : 'bg-[#060714] border-[#262E4A] text-gray-300'
                  }`}
                >
                  {t.label}
                </button>
              ))}
            </div>
          </div>

          <div>
            <label className="text-xs font-semibold text-gray-300 block mb-1">Catchy Title</label>
            <input 
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. Green jacket reading at Blue Tokai"
              className="w-full px-3.5 py-2.5 rounded-xl bg-[#060714] border border-[#262E4A] text-xs text-white focus:outline-none focus:border-[#00C6FF]"
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-xs font-semibold text-gray-300 block mb-1">Broad Neighborhood</label>
              <input 
                type="text"
                value={broadLocation}
                onChange={(e) => setBroadLocation(e.target.value)}
                placeholder="e.g. Indiranagar, Bengaluru"
                className="w-full px-3 py-2 rounded-xl bg-[#060714] border border-[#262E4A] text-xs text-white focus:outline-none focus:border-[#00C6FF]"
              />
            </div>
            <div>
              <label className="text-xs font-semibold text-gray-300 block mb-1">Venue / Cafe Name (Optional)</label>
              <input 
                type="text"
                value={venue}
                onChange={(e) => setVenue(e.target.value)}
                placeholder="e.g. Third Wave Coffee"
                className="w-full px-3 py-2 rounded-xl bg-[#060714] border border-[#262E4A] text-xs text-white focus:outline-none focus:border-[#00C6FF]"
              />
            </div>
          </div>

          <div>
            <label className="text-xs font-semibold text-gray-300 block mb-1">Description of the Encounter</label>
            <textarea 
              rows={4}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Describe the context, what you noticed, or shared a smile about..."
              className="w-full p-3.5 rounded-xl bg-[#060714] border border-[#262E4A] text-xs text-white focus:outline-none focus:border-[#00C6FF] resize-none"
            />
          </div>

          <div className="flex items-center justify-between p-3 rounded-xl bg-[#060714] border border-[#262E4A]">
            <span className="text-xs text-gray-300">Post anonymously (hides your handle)</span>
            <input 
              type="checkbox"
              checked={isAnonymous}
              onChange={(e) => setIsAnonymous(e.target.checked)}
              className="w-4 h-4 accent-[#00C6FF]"
            />
          </div>

          <button
            type="submit"
            disabled={!title.trim() || !description.trim()}
            className="w-full py-3 bg-gradient-to-r from-[#00C6FF] to-[#8B5CF6] text-white font-bold text-xs rounded-xl shadow-lg flex items-center justify-center gap-2"
          >
            <Send className="w-4 h-4" />
            <span>Publish Crossed Path (Expires in 72h)</span>
          </button>

        </form>

      </div>
    </div>
  );
};
