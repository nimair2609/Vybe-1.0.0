import React, { useState, useRef, useEffect } from 'react';
import {
  MessageCircle, Phone, Video, Send, Search,
  ArrowLeft, CheckCheck, Plus, X, ArrowDown
} from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { OfficialBadge } from '../components/Badges';

export const InboxView: React.FC = () => {
  const {
    conversations,
    selectedConversation,
    selectConversation,
    startConversationWithUser,
    sendMessage,
    startCall,
    allUsers,
    currentUser
  } = useVybe();

  const [searchQuery, setSearchQuery] = useState('');
  const [chatInput, setChatInput] = useState('');
  const [showNewChatModal, setShowNewChatModal] = useState(false);
  const [memberSearch, setMemberSearch] = useState('');

  // Smart scroll state — prevents unwanted autoscrolling when reading older messages
  const messagesContainerRef = useRef<HTMLDivElement | null>(null);
  const isNearBottomRef = useRef<boolean>(true);
  const prevThreadPeerRef = useRef<string | null>(null);
  const prevLastMessageIdRef = useRef<string | null>(null);
  const [unreadBelowCount, setUnreadBelowCount] = useState(0);

  const filteredConversations = conversations.filter(c =>
    c.peerUser.displayName.toLowerCase().includes(searchQuery.toLowerCase()) ||
    c.peerUser.username.toLowerCase().includes(searchQuery.toLowerCase()) ||
    c.lastMessage.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const candidateMembers = allUsers.filter(u => {
    if (u.id === currentUser.id) return false;
    if (!memberSearch.trim()) return true;
    const q = memberSearch.trim().toLowerCase();
    return (
      u.displayName.toLowerCase().includes(q) ||
      u.username.toLowerCase().includes(q)
    );
  });

  const checkIsNearBottom = () => {
    const el = messagesContainerRef.current;
    if (!el) return true;
    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    return distanceFromBottom <= 110;
  };

  const handleMessagesScroll = () => {
    const near = checkIsNearBottom();
    isNearBottomRef.current = near;
    if (near && unreadBelowCount > 0) {
      setUnreadBelowCount(0);
    }
  };

  const scrollToLatest = (behavior: ScrollBehavior = 'smooth') => {
    const el = messagesContainerRef.current;
    if (!el) return;
    el.scrollTo({ top: el.scrollHeight, behavior });
    isNearBottomRef.current = true;
    setUnreadBelowCount(0);
  };

  useEffect(() => {
    if (!selectedConversation) {
      prevThreadPeerRef.current = null;
      prevLastMessageIdRef.current = null;
      setUnreadBelowCount(0);
      return;
    }

    const threadKey = selectedConversation.peerUser.id;
    const msgs = selectedConversation.messages;
    const newestMsg = msgs.length > 0 ? msgs[msgs.length - 1] : null;
    const newestId = newestMsg?.id || null;

    // 1. Initial open of a conversation: jump directly to bottom once
    if (prevThreadPeerRef.current !== threadKey) {
      prevThreadPeerRef.current = threadKey;
      prevLastMessageIdRef.current = newestId;
      setUnreadBelowCount(0);
      requestAnimationFrame(() => scrollToLatest('auto'));
      return;
    }

    // 2. Only react when a genuinely new message arrives (never on idle 15s background sync)
    if (newestId && newestId !== prevLastMessageIdRef.current) {
      prevLastMessageIdRef.current = newestId;
      if (newestMsg?.isMe) {
        requestAnimationFrame(() => scrollToLatest('smooth'));
      } else if (isNearBottomRef.current) {
        requestAnimationFrame(() => scrollToLatest('smooth'));
      } else {
        setUnreadBelowCount(prev => prev + 1);
      }
    }
  }, [selectedConversation]);

  const handleSendMessage = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = chatInput.trim();
    if (trimmed && selectedConversation) {
      sendMessage(selectedConversation.id, trimmed);
      setChatInput('');
    }
  };

  // If a conversation is selected, show the full chat detail
  if (selectedConversation) {
    return (
      <div className="flex flex-col h-[calc(100vh-130px)] max-w-2xl mx-auto bg-[#101322] border border-[#262E4A] rounded-2xl shadow-2xl overflow-hidden my-3 relative">

        {/* Chat Header */}
        <div className="flex items-center justify-between p-3.5 bg-[#060714] border-b border-[#262E4A]">
          <div className="flex items-center gap-3">
            <button
              onClick={() => selectConversation(null)}
              className="p-1.5 rounded-lg text-gray-400 hover:text-white hover:bg-[#1A1F36] transition-colors"
              aria-label="Back to inbox"
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
                {selectedConversation.peerUser.isOnline ? 'Online now' : `@${selectedConversation.peerUser.username}`}
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
        <div
          ref={messagesContainerRef}
          onScroll={handleMessagesScroll}
          className="flex-1 overflow-y-auto p-4 space-y-3 bg-[#060714]/40 no-scrollbar relative"
        >
          {selectedConversation.messages.length === 0 ? (
            <div className="h-full flex items-center justify-center text-center text-xs text-gray-400 py-12">
              No messages yet. Say hello to {selectedConversation.peerUser.displayName}!
            </div>
          ) : (
            selectedConversation.messages.map(msg => (
              <div
                key={msg.id}
                className={`flex flex-col ${msg.isMe ? 'items-end' : 'items-start'}`}
              >
                <div className={`max-w-[80%] rounded-2xl px-4 py-2.5 text-xs shadow ${
                  msg.isMe
                    ? 'bg-gradient-to-r from-[#8B5CF6] to-[#00C6FF] text-white rounded-br-xs'
                    : 'bg-[#1A1F36] border border-[#262E4A] text-gray-200 rounded-bl-xs'
                }`}>
                  <p className="leading-relaxed whitespace-pre-wrap break-words">{msg.text}</p>
                  <div className={`flex items-center gap-1 mt-1 text-[9px] ${
                    msg.isMe ? 'text-white/75 justify-end' : 'text-gray-400'
                  }`}>
                    <span>{msg.timestamp}</span>
                    {msg.isMe && <CheckCheck className="w-3 h-3 text-white" />}
                  </div>
                </div>

                {msg.reactions && msg.reactions.length > 0 && (
                  <div className="flex items-center gap-1 -mt-1.5 px-2 py-0.5 rounded-full bg-[#101322] border border-[#262E4A] text-[11px] shadow">
                    {msg.reactions.map((r, i) => (
                      <span key={i}>{r}</span>
                    ))}
                  </div>
                )}
              </div>
            ))
          )}
        </div>

        {/* Non-intrusive Jump to New Messages pill when scrolled up */}
        {unreadBelowCount > 0 && (
          <button
            type="button"
            onClick={() => scrollToLatest('smooth')}
            className="absolute bottom-16 left-1/2 -translate-x-1/2 px-3.5 py-1.5 rounded-full bg-[#8B5CF6] text-white text-xs font-bold shadow-lg flex items-center gap-1.5 hover:bg-[#7C3AED] transition-colors"
          >
            <ArrowDown className="w-3.5 h-3.5" />
            {unreadBelowCount === 1 ? '1 new message' : `${unreadBelowCount} new messages`}
          </button>
        )}

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
            aria-label="Send message"
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

        <button
          type="button"
          onClick={() => {
            setMemberSearch('');
            setShowNewChatModal(true);
          }}
          className="px-3.5 py-2 rounded-xl bg-gradient-to-r from-[#8B5CF6] to-[#00C6FF] text-white text-xs font-bold flex items-center gap-1.5 shadow hover:opacity-95 transition-opacity"
        >
          <Plus className="w-4 h-4" />
          New Message
        </button>
      </div>

      {/* Search Input */}
      <div className="relative">
        <Search className="w-4 h-4 text-gray-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Search by name, @username, or message..."
          className="w-full pl-10 pr-4 py-2.5 bg-[#101322] border border-[#262E4A] rounded-xl text-xs text-white placeholder-gray-400 focus:outline-none focus:border-[#8B5CF6]"
        />
      </div>

      {/* Conversations List */}
      <div className="space-y-2">
        {filteredConversations.map(conv => (
          <div
            key={`dm_${conv.peerUser.id}`}
            onClick={() => selectConversation(conv)}
            className="flex items-center justify-between p-3.5 rounded-2xl bg-[#101322] border border-[#262E4A] hover:border-[#8B5CF6]/50 cursor-pointer transition-all"
          >
            <div className="flex items-center gap-3 min-w-0">
              <div className="relative shrink-0">
                <img
                  src={conv.peerUser.avatarUrl}
                  alt={conv.peerUser.displayName}
                  className="w-12 h-12 rounded-full object-cover border border-[#262E4A]"
                />
                {conv.peerUser.isOnline && (
                  <span className="absolute bottom-0 right-0 w-3 h-3 bg-[#22C55E] border-2 border-[#101322] rounded-full" />
                )}
              </div>
              <div className="min-w-0">
                <div className="flex items-center gap-1.5">
                  <span className="font-bold text-sm text-white truncate">{conv.peerUser.displayName}</span>
                  {conv.peerUser.badges?.map(b => (
                    <OfficialBadge key={b} badge={b} size="sm" />
                  ))}
                </div>
                <p className="text-xs text-gray-300 line-clamp-1 mt-0.5">
                  {conv.lastMessage || 'Tap to send a message'}
                </p>
              </div>
            </div>

            <div className="flex flex-col items-end gap-1 shrink-0 ml-3">
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

      {/* New Message Modal */}
      {showNewChatModal && (
        <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="w-full max-w-md bg-[#101322] border border-[#262E4A] rounded-2xl shadow-2xl overflow-hidden">
            <div className="flex items-center justify-between p-4 border-b border-[#262E4A]">
              <h2 className="text-sm font-bold text-white">New Direct Message</h2>
              <button
                type="button"
                onClick={() => setShowNewChatModal(false)}
                className="p-1 rounded-lg text-gray-400 hover:text-white hover:bg-[#1A1F36]"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="p-4 space-y-3">
              <div className="relative">
                <Search className="w-4 h-4 text-gray-400 absolute left-3 top-1/2 -translate-y-1/2" />
                <input
                  type="text"
                  value={memberSearch}
                  onChange={(e) => setMemberSearch(e.target.value)}
                  placeholder="Search VYBE people by name or @username..."
                  className="w-full pl-9 pr-3 py-2 bg-[#060714] border border-[#262E4A] rounded-xl text-xs text-white focus:outline-none focus:border-[#8B5CF6]"
                  autoFocus
                />
              </div>

              <div className="max-h-72 overflow-y-auto space-y-1.5 no-scrollbar">
                {candidateMembers.length === 0 ? (
                  <p className="text-center text-xs text-gray-400 py-6">No matching VYBE members found.</p>
                ) : (
                  candidateMembers.map(member => (
                    <button
                      key={member.id}
                      type="button"
                      onClick={() => {
                        setShowNewChatModal(false);
                        startConversationWithUser(member);
                      }}
                      className="w-full flex items-center justify-between p-2.5 rounded-xl bg-[#060714]/60 hover:bg-[#1A1F36] border border-transparent hover:border-[#8B5CF6]/40 transition-all text-left"
                    >
                      <div className="flex items-center gap-2.5 min-w-0">
                        <img
                          src={member.avatarUrl}
                          alt={member.displayName}
                          className="w-9 h-9 rounded-full object-cover border border-[#262E4A]"
                        />
                        <div className="min-w-0">
                          <div className="flex items-center gap-1">
                            <span className="text-xs font-bold text-white truncate">{member.displayName}</span>
                            {member.badges?.map(b => (
                              <OfficialBadge key={b} badge={b} size="sm" />
                            ))}
                          </div>
                          <span className="text-[11px] text-gray-400">@{member.username}</span>
                        </div>
                      </div>
                      <span className="text-[11px] font-semibold text-[#00C6FF] shrink-0">Message</span>
                    </button>
                  ))
                )}
              </div>
            </div>
          </div>
        </div>
      )}

    </div>
  );
};
