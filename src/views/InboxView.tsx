import React, { useState } from 'react';
import { 
  MessageCircle, Phone, Video, Send, Search, 
  ArrowLeft, Paperclip, Smile, MoreVertical, CheckCheck 
} from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { OfficialBadge } from '../components/Badges';
import { Conversation, DirectMessage } from '../types';

export const InboxView: React.FC = () => {
  const { 
    conversations, selectedConversation, selectConversation, 
    sendMessage, startCall, currentUser 
  } = useVybe();

  const [searchQuery, setSearchQuery] = useState('');
  const [chatInput, setChatInput] = useState('');

  const filteredConversations = conversations.filter(c => 
    c.peerUser.displayName.toLowerCase().includes(searchQuery.toLowerCase()) ||
    c.peerUser.username.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const handleSendMessage = (e: React.FormEvent) => {
    e.preventDefault();
    if (chatInput.trim() && selectedConversation) {
      sendMessage(selectedConversation.id, chatInput);
      setChatInput('');
    }
  };

  // If a conversation is selected, show the full chat detail
  if (selectedConversation) {
    return (
      <div className="flex flex-col h-[calc(100vh-130px)] max-w-2xl mx-auto bg-[#101322] border border-[#262E4A] rounded-2xl shadow-2xl overflow-hidden my-3">
        
        {/* Chat Header */}
        <div className="flex items-center justify-between p-3.5 bg-[#060714] border-b border-[#262E4A]">
          <div className="flex items-center gap-3">
            <button
              onClick={() => selectConversation(null)}
              className="p-1.5 rounded-lg text-gray-400 hover:text-white hover:bg-[#1A1F36] transition-colors"
            >
              <ArrowLeft className="w-5 h-5" />
            </button>
            <div className="relative">
              <img 
                src={selectedConversation.peerUser.avatarUrl} 
                alt={selectedConversation.peerUser.displayName}
                className="w-10 h-10 rounded-full object-cover border border-[#262E4A]"
              />
              {selectedConversation.peerUser.isOnline && (
                <span className="absolute bottom-0 right-0 w-2.5 h-2.5 bg-[#22C55E] border-2 border-[#060714] rounded-full" />
              )}
            </div>
            <div>
              <div className="flex items-center gap-1.5">
                <span className="font-bold text-sm text-white">{selectedConversation.peerUser.displayName}</span>
                {selectedConversation.peerUser.badges?.map(b => (
                  <OfficialBadge key={b} badge={b} size="sm" />
                ))}
              </div>
              <span className="text-[11px] text-gray-400 font-mono">
                {selectedConversation.peerUser.isOnline ? 'Online now' : 'Last seen recently'}
              </span>
            </div>
          </div>

          {/* Call Controls */}
          <div className="flex items-center gap-1.5">
            <button
              onClick={() => startCall(selectedConversation.peerUser, false)}
              title="Voice Call"
              className="p-2.5 rounded-xl bg-[#1A1F36] hover:bg-[#262E4A] text-[#00C6FF] transition-colors"
            >
              <Phone className="w-4 h-4" />
            </button>
            <button
              onClick={() => startCall(selectedConversation.peerUser, true)}
              title="Video Call"
              className="p-2.5 rounded-xl bg-[#1A1F36] hover:bg-[#262E4A] text-[#D946EF] transition-colors"
            >
              <Video className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* Messages Stream */}
        <div className="flex-1 overflow-y-auto p-4 space-y-3 bg-[#060714]/40 no-scrollbar">
          {selectedConversation.messages.map(msg => (
            <div 
              key={msg.id}
              className={`flex flex-col ${msg.isMe ? 'items-end' : 'items-start'}`}
            >
              <div className={`max-w-[80%] rounded-2xl px-4 py-2.5 text-xs shadow ${
                msg.isMe 
                  ? 'bg-gradient-to-r from-[#8B5CF6] to-[#00C6FF] text-white rounded-br-xs' 
                  : 'bg-[#1A1F36] border border-[#262E4A] text-gray-200 rounded-bl-xs'
              }`}>
                <p className="leading-relaxed">{msg.text}</p>
                <div className={`flex items-center gap-1 mt-1 text-[9px] ${
                  msg.isMe ? 'text-white/70 justify-end' : 'text-gray-400'
                }`}>
                  <span>{msg.timestamp}</span>
                  {msg.isMe && <CheckCheck className="w-3 h-3 text-[#00C6FF]" />}
                </div>
              </div>

              {/* Message reactions if any */}
              {msg.reactions && msg.reactions.length > 0 && (
                <div className="flex items-center gap-1 -mt-1.5 px-2 py-0.5 rounded-full bg-[#101322] border border-[#262E4A] text-[11px] shadow">
                  {msg.reactions.map((r, i) => (
                    <span key={i}>{r}</span>
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>

        {/* Input Bar */}
        <form onSubmit={handleSendMessage} className="p-3 bg-[#060714] border-t border-[#262E4A] flex items-center gap-2">
          <input 
            type="text"
            value={chatInput}
            onChange={(e) => setChatInput(e.target.value)}
            placeholder={`Message ${selectedConversation.peerUser.displayName}...`}
            className="flex-1 px-4 py-2.5 bg-[#101322] border border-[#262E4A] rounded-xl text-xs text-white focus:outline-none focus:border-[#8B5CF6]"
          />
          <button
            type="submit"
            disabled={!chatInput.trim()}
            className="p-2.5 bg-gradient-to-r from-[#00C6FF] to-[#8B5CF6] disabled:opacity-40 text-white rounded-xl shadow transition-all"
          >
            <Send className="w-4 h-4" />
          </button>
        </form>

      </div>
    );
  }

  // Conversation List Screen
  return (
    <div className="w-full max-w-2xl mx-auto px-4 py-6 pb-28 space-y-4">
      
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-black brand-font text-white flex items-center gap-2">
            <MessageCircle className="w-5 h-5 text-[#00C6FF]" />
            Direct Messages
          </h1>
          <p className="text-xs text-gray-400 mt-0.5">Encrypted conversations & voice rooms</p>
        </div>
      </div>

      {/* Search Input */}
      <div className="relative">
        <Search className="w-4 h-4 text-gray-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
        <input 
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Search by name or @username..."
          className="w-full pl-10 pr-4 py-2.5 bg-[#101322] border border-[#262E4A] rounded-xl text-xs text-white placeholder-gray-400 focus:outline-none focus:border-[#8B5CF6]"
        />
      </div>

      {/* Conversations List */}
      <div className="space-y-2">
        {filteredConversations.map(conv => (
          <div
            key={conv.id}
            onClick={() => selectConversation(conv)}
            className="flex items-center justify-between p-3.5 rounded-2xl bg-[#101322] border border-[#262E4A] hover:border-[#8B5CF6]/50 cursor-pointer transition-all"
          >
            <div className="flex items-center gap-3">
              <div className="relative">
                <img 
                  src={conv.peerUser.avatarUrl} 
                  alt={conv.peerUser.displayName}
                  className="w-12 h-12 rounded-full object-cover border border-[#262E4A]"
                />
                {conv.peerUser.isOnline && (
                  <span className="absolute bottom-0 right-0 w-3 h-3 bg-[#22C55E] border-2 border-[#101322] rounded-full" />
                )}
              </div>
              <div>
                <div className="flex items-center gap-1.5">
                  <span className="font-bold text-sm text-white">{conv.peerUser.displayName}</span>
                  {conv.peerUser.badges?.map(b => (
                    <OfficialBadge key={b} badge={b} size="sm" />
                  ))}
                </div>
                <p className="text-xs text-gray-300 line-clamp-1 mt-0.5">
                  {conv.lastMessage}
                </p>
              </div>
            </div>

            <div className="flex flex-col items-end gap-1 shrink-0">
              <span className="text-[10px] text-gray-400">{conv.lastMessageTimestamp}</span>
              {conv.unreadCount > 0 && (
                <span className="px-2 py-0.5 rounded-full bg-[#D946EF] text-[10px] font-bold text-white shadow">
                  {conv.unreadCount}
                </span>
              )}
            </div>
          </div>
        ))}
      </div>

    </div>
  );
};
