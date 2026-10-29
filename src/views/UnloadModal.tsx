import React, { useState } from 'react';
import { Shield, Users, Mic, MicOff, Send, X, Volume2, Sparkles } from 'lucide-react';
import { useVybe } from '../context/VybeContext';

export const UnloadModal: React.FC = () => {
  const { selectedUnloadRoom, sendUnloadMessage, closeModal, unloadRooms, joinUnloadRoom } = useVybe();

  const [messageText, setMessageText] = useState('');
  const [isAnonymous, setIsAnonymous] = useState(true);
  const [isMicMuted, setIsMicMuted] = useState(true);

  const room = selectedUnloadRoom || unloadRooms[0];

  const handleSend = (e: React.FormEvent) => {
    e.preventDefault();
    if (messageText.trim() && room) {
      sendUnloadMessage(room.id, messageText, isAnonymous);
      setMessageText('');
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/85 backdrop-blur-xl animate-fade-in">
      <div className="relative w-full max-w-xl h-[85vh] flex flex-col bg-[#0a0f1d] border border-[#262E4A] rounded-3xl shadow-2xl overflow-hidden text-white">
        
        {/* Header */}
        <div className="p-4 bg-[#060714] border-b border-[#262E4A] flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-xl bg-purple-500/10 text-purple-400 border border-purple-500/20">
              <Users className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="font-bold text-sm text-white">{room.title}</h3>
                <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-white/10 text-gray-300">{room.roomCode}</span>
              </div>
              <p className="text-[11px] text-gray-400">
                {room.activeListenersCount} active listeners • {room.mode}
              </p>
            </div>
          </div>
          <button onClick={closeModal} className="p-2 text-gray-400 hover:text-white rounded-lg">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Zero-Recording Safety Banner */}
        <div className="p-2.5 px-4 bg-emerald-500/10 border-b border-emerald-500/20 text-emerald-300 text-[11px] flex items-center gap-2">
          <Shield className="w-4 h-4 shrink-0 text-emerald-400" />
          <span>{room.safetyNotice}</span>
        </div>

        {/* Room Switcher Pills */}
        <div className="p-2 px-4 bg-[#060714]/60 flex items-center gap-2 overflow-x-auto no-scrollbar border-b border-[#262E4A]/40">
          {unloadRooms.map(r => (
            <button
              key={r.id}
              onClick={() => joinUnloadRoom(r)}
              className={`px-3 py-1 rounded-full text-[11px] font-semibold whitespace-nowrap transition-colors ${
                r.id === room.id 
                  ? 'bg-gradient-to-r from-[#8B5CF6] to-[#00C6FF] text-white' 
                  : 'bg-[#1A1F36] text-gray-400 hover:text-white'
              }`}
            >
              #{r.topicTag}
            </button>
          ))}
        </div>

        {/* Message Stream */}
        <div className="flex-1 overflow-y-auto p-4 space-y-3 bg-[#060714]/30 no-scrollbar">
          {room.recentMessages.length === 0 ? (
            <div className="text-center py-12 text-gray-400 text-xs space-y-2">
              <Sparkles className="w-6 h-6 mx-auto text-[#00C6FF] opacity-60" />
              <p>The space is quiet. Speak your truth or listen in silence.</p>
            </div>
          ) : (
            room.recentMessages.map(msg => (
              <div key={msg.id} className={`flex flex-col ${msg.isMe ? 'items-end' : 'items-start'}`}>
                <div className={`max-w-[85%] p-3 rounded-2xl text-xs ${
                  msg.isMe 
                    ? 'bg-gradient-to-r from-[#8B5CF6] to-[#00C6FF] text-white rounded-br-xs' 
                    : 'bg-[#101322] border border-[#262E4A] text-gray-200 rounded-bl-xs'
                }`}>
                  <div className="flex items-center justify-between gap-4 mb-1 text-[10px] opacity-75">
                    <span className="font-bold">{msg.senderName}</span>
                    <span>{msg.timestamp}</span>
                  </div>
                  <p className="leading-relaxed">{msg.text}</p>
                </div>
              </div>
            ))
          )}
        </div>

        {/* Bottom Voice & Text Bar */}
        <div className="p-3 bg-[#060714] border-t border-[#262E4A] space-y-2">
          <div className="flex items-center justify-between text-xs px-1">
            <label className="flex items-center gap-2 cursor-pointer text-gray-300">
              <input 
                type="checkbox" 
                checked={isAnonymous} 
                onChange={(e) => setIsAnonymous(e.target.checked)}
                className="rounded accent-[#8B5CF6]" 
              />
              <span className="text-[11px]">Send anonymously</span>
            </label>
            <button
              onClick={() => setIsMicMuted(!isMicMuted)}
              className={`flex items-center gap-1.5 px-3 py-1 rounded-full text-[11px] font-semibold border transition-all ${
                isMicMuted 
                  ? 'bg-red-500/10 border-red-500/30 text-red-300' 
                  : 'bg-emerald-500/20 border-emerald-500/50 text-emerald-300 animate-pulse'
              }`}
            >
              {isMicMuted ? <MicOff className="w-3.5 h-3.5" /> : <Mic className="w-3.5 h-3.5" />}
              <span>{isMicMuted ? 'Mic Muted' : 'Speaking Live'}</span>
            </button>
          </div>

          <form onSubmit={handleSend} className="flex items-center gap-2">
            <input 
              type="text"
              value={messageText}
              onChange={(e) => setMessageText(e.target.value)}
              placeholder="Unload whatever is on your chest safely..."
              className="flex-1 px-4 py-2.5 bg-[#101322] border border-[#262E4A] rounded-xl text-xs text-white focus:outline-none focus:border-[#8B5CF6]"
            />
            <button
              type="submit"
              disabled={!messageText.trim()}
              className="p-2.5 bg-gradient-to-r from-[#00C6FF] to-[#8B5CF6] disabled:opacity-40 text-white rounded-xl shadow"
            >
              <Send className="w-4 h-4" />
            </button>
          </form>
        </div>

      </div>
    </div>
  );
};
