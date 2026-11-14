import React, { useState, useRef, useEffect } from 'react';
import {
  MessageCircle, Phone, Video, Send, Search,
  ArrowLeft, CheckCheck, Plus, X, ArrowDown
} from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { OfficialBadge } from '../components/Badges';
import { VybeEmptyState } from '../components/VybeDesignSystem';

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

    if (prevThreadPeerRef.current !== threadKey) {
      prevThreadPeerRef.current = threadKey;
      prevLastMessageIdRef.current = newestId;
      setUnreadBelowCount(0);
      requestAnimationFrame(() => scrollToLatest('auto'));
      return;
    }

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

  const conversationListPane = (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="p-4 border-b border-[var(--vybe-border)] flex items-center justify-between gap-2">
        <div>
          <h1 className="text-lg font-extrabold brand-font text-[var(--vybe-text)] flex items-center gap-2">
            <MessageCircle className="w-5 h-5 text-[var(--vybe-accent)]" />
            <span>Direct Messages</span>
          </h1>
          <p className="text-[11px] text-[var(--vybe-text-secondary)] mt-0.5">
            {conversations.length} active conversation{conversations.length === 1 ? '' : 's'}
          </p>
        </div>

        <button
          type="button"
          onClick={() => {
            setMemberSearch('');
            setShowNewChatModal(true);
          }}
          className="px-3 py-2 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold flex items-center gap-1.5 shadow-sm hover:opacity-95 transition-opacity shrink-0"
        >
          <Plus className="w-4 h-4" />
          <span>New</span>
        </button>
      </div>

      {/* Search Input */}
      <div className="p-3 border-b border-[var(--vybe-border)]">
        <div className="relative">
          <Search className="w-4 h-4 text-[var(--vybe-text-muted)] absolute left-3.5 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search conversations..."
            className="w-full pl-9 pr-3 py-2 bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] rounded-xl text-xs text-[var(--vybe-text)] placeholder-[var(--vybe-text-muted)] focus:outline-none focus:border-[var(--vybe-accent)]"
          />
        </div>
      </div>

      {/* Conversations List */}
      <div className="flex-1 overflow-y-auto p-2.5 space-y-1.5 no-scrollbar">
        {filteredConversations.length === 0 ? (
          <div className="p-6 text-center text-xs text-[var(--vybe-text-secondary)]">
            No matching conversations found.
          </div>
        ) : (
          filteredConversations.map(conv => {
            const isSelected = selectedConversation?.peerUser.id === conv.peerUser.id;
            return (
              <div
                key={`dm_${conv.peerUser.id}`}
                onClick={() => selectConversation(conv)}
                className={`flex items-center justify-between p-3 rounded-2xl border cursor-pointer transition-colors ${
                  isSelected
                    ? 'bg-[var(--vybe-accent-soft)] border-[var(--vybe-accent)]/50'
                    : 'bg-[var(--vybe-surface)] border-transparent hover:bg-[var(--vybe-elevated)] hover:border-[var(--vybe-border)]'
                }`}
              >
                <div className="flex items-center gap-3 min-w-0">
                  <div className="relative shrink-0">
                    <img
                      src={conv.peerUser.avatarUrl}
                      alt={conv.peerUser.displayName}
                      className="w-11 h-11 rounded-full object-cover border border-[var(--vybe-border)]"
                    />
                    {conv.peerUser.isOnline && (
                      <span className="absolute bottom-0 right-0 w-2.5 h-2.5 bg-emerald-500 border-2 border-[var(--vybe-surface)] rounded-full" />
                    )}
                  </div>
                  <div className="min-w-0">
                    <div className="flex items-center gap-1">
                      <span className="font-bold text-xs text-[var(--vybe-text)] truncate">
                        {conv.peerUser.displayName}
                      </span>
                      {conv.peerUser.badges?.map(b => (
                        <OfficialBadge key={b} badge={b} size="sm" />
                      ))}
                    </div>
                    <p className="text-[11px] text-[var(--vybe-text-secondary)] line-clamp-1 mt-0.5">
                      {conv.lastMessage || 'Tap to send a message'}
                    </p>
                  </div>
                </div>

                <div className="flex flex-col items-end gap-1 shrink-0 ml-2">
                  <span className="text-[10px] text-[var(--vybe-text-muted)]">{conv.lastMessageTimestamp}</span>
                  {conv.unreadCount > 0 && (
                    <span className="px-2 py-0.5 rounded-full bg-[var(--vybe-accent)] text-[10px] font-bold text-white keep-white">
                      {conv.unreadCount}
                    </span>
                  )}
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );

  const activeThreadPane = selectedConversation ? (
    <div className="flex flex-col h-full relative">
      {/* Chat Header */}
      <div className="flex items-center justify-between p-3.5 bg-[var(--vybe-surface)] border-b border-[var(--vybe-border)]">
        <div className="flex items-center gap-3 min-w-0">
          <button
            type="button"
            onClick={() => selectConversation(null)}
            className="lg:hidden p-1.5 rounded-lg text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)] hover:bg-[var(--vybe-elevated)] transition-colors"
            aria-label="Back to inbox"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>
          <div className="relative shrink-0">
            <img
              src={selectedConversation.peerUser.avatarUrl}
              alt={selectedConversation.peerUser.displayName}
              className="w-10 h-10 rounded-full object-cover border border-[var(--vybe-border)]"
            />
            {selectedConversation.peerUser.isOnline && (
              <span className="absolute bottom-0 right-0 w-2.5 h-2.5 bg-emerald-500 border-2 border-[var(--vybe-surface)] rounded-full" />
            )}
          </div>
          <div className="min-w-0">
            <div className="flex items-center gap-1.5">
              <span className="font-bold text-sm text-[var(--vybe-text)] truncate">
                {selectedConversation.peerUser.displayName}
              </span>
              {selectedConversation.peerUser.badges?.map(b => (
                <OfficialBadge key={b} badge={b} size="sm" />
              ))}
            </div>
            <span className="text-[11px] text-[var(--vybe-text-secondary)] font-mono">
              {selectedConversation.peerUser.isOnline
                ? 'Online now'
                : `@${selectedConversation.peerUser.username}`}
            </span>
          </div>
        </div>

        {/* Call Controls */}
        <div className="flex items-center gap-1.5 shrink-0">
          <button
            type="button"
            onClick={() => startCall(selectedConversation.peerUser, false)}
            title="Voice Call"
            aria-label="Voice Call"
            className="p-2.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] text-[var(--vybe-accent)] transition-colors"
          >
            <Phone className="w-4 h-4" />
          </button>
          <button
            type="button"
            onClick={() => startCall(selectedConversation.peerUser, true)}
            title="Video Call"
            aria-label="Video Call"
            className="p-2.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] text-[var(--vybe-accent-secondary)] transition-colors"
          >
            <Video className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Messages Stream */}
      <div
        ref={messagesContainerRef}
        onScroll={handleMessagesScroll}
        className="flex-1 overflow-y-auto p-4 space-y-3 bg-[var(--vybe-bg)]/50 no-scrollbar relative"
      >
        {selectedConversation.messages.length === 0 ? (
          <div className="h-full flex items-center justify-center text-center text-xs text-[var(--vybe-text-secondary)] py-12">
            No messages yet. Say hello to {selectedConversation.peerUser.displayName}!
          </div>
        ) : (
          selectedConversation.messages.map(msg => (
            <div
              key={msg.id}
              className={`flex flex-col ${msg.isMe ? 'items-end' : 'items-start'}`}
            >
              <div
                className={`max-w-[80%] rounded-2xl px-4 py-2.5 text-xs shadow-sm ${
                  msg.isMe
                    ? 'bg-[var(--vybe-accent)] text-white keep-white rounded-br-sm'
                    : 'bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-[var(--vybe-text)] rounded-bl-sm'
                }`}
              >
                <p className="leading-relaxed whitespace-pre-wrap break-words">{msg.text}</p>
                <div
                  className={`flex items-center gap-1 mt-1 text-[9px] ${
                    msg.isMe ? 'text-white/75 keep-white justify-end' : 'text-[var(--vybe-text-muted)]'
                  }`}
                >
                  <span>{msg.timestamp}</span>
                  {msg.isMe && <CheckCheck className="w-3 h-3 text-white keep-white" />}
                </div>
              </div>

              {msg.reactions && msg.reactions.length > 0 && (
                <div className="flex items-center gap-1 -mt-1.5 px-2 py-0.5 rounded-full bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-[11px] shadow-sm">
                  {msg.reactions.map((r, i) => (
                    <span key={i}>{r}</span>
                  ))}
                </div>
              )}
            </div>
          ))
        )}
      </div>

      {/* Jump to New Messages pill when scrolled up */}
      {unreadBelowCount > 0 && (
        <button
          type="button"
          onClick={() => scrollToLatest('smooth')}
          className="absolute bottom-16 left-1/2 -translate-x-1/2 px-3.5 py-1.5 rounded-full bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold shadow-lg flex items-center gap-1.5 hover:opacity-95 transition-opacity"
        >
          <ArrowDown className="w-3.5 h-3.5" />
          {unreadBelowCount === 1 ? '1 new message' : `${unreadBelowCount} new messages`}
        </button>
      )}

      {/* Input Bar */}
      <form
        onSubmit={handleSendMessage}
        className="p-3 bg-[var(--vybe-surface)] border-t border-[var(--vybe-border)] flex items-center gap-2"
      >
        <input
          type="text"
          value={chatInput}
          onChange={(e) => setChatInput(e.target.value)}
          placeholder={`Message ${selectedConversation.peerUser.displayName}...`}
          className="flex-1 px-4 py-2.5 bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] rounded-xl text-xs text-[var(--vybe-text)] placeholder-[var(--vybe-text-muted)] focus:outline-none focus:border-[var(--vybe-accent)]"
        />
        <button
          type="submit"
          disabled={!chatInput.trim()}
          className="p-2.5 bg-[var(--vybe-accent)] disabled:opacity-40 text-white keep-white rounded-xl shadow-sm hover:opacity-95 transition-opacity"
          aria-label="Send message"
        >
          <Send className="w-4 h-4" />
        </button>
      </form>
    </div>
  ) : (
    <div className="h-full flex items-center justify-center p-8">
      <VybeEmptyState
        icon={MessageCircle}
        title="Select a conversation"
        subtitle="Pick a thread on the left or start a new direct message with someone on VYBE."
        actionLabel="New Direct Message"
        onAction={() => {
          setMemberSearch('');
          setShowNewChatModal(true);
        }}
      />
    </div>
  );

  return (
    <div className="w-full max-w-6xl mx-auto px-3 sm:px-4 py-4 pb-24 lg:pb-6">
      <div className="h-[calc(100dvh-148px)] lg:h-[calc(100dvh-96px)] bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-2xl overflow-hidden shadow-sm grid grid-cols-1 lg:grid-cols-12">
        {/* Conversation List Column */}
        <div
          className={`lg:col-span-4 xl:col-span-4 lg:border-r border-[var(--vybe-border)] h-full overflow-hidden ${
            selectedConversation ? 'hidden lg:block' : 'block'
          }`}
        >
          {conversationListPane}
        </div>

        {/* Active Thread Column */}
        <div
          className={`lg:col-span-8 xl:col-span-8 h-full overflow-hidden ${
            selectedConversation ? 'block' : 'hidden lg:block'
          }`}
        >
          {activeThreadPane}
        </div>
      </div>

      {/* New Message Modal */}
      {showNewChatModal && (
        <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="w-full max-w-md bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-2xl shadow-2xl overflow-hidden">
            <div className="flex items-center justify-between p-4 border-b border-[var(--vybe-border)]">
              <h2 className="text-sm font-bold text-[var(--vybe-text)]">New Direct Message</h2>
              <button
                type="button"
                onClick={() => setShowNewChatModal(false)}
                aria-label="Close new message dialog"
                className="p-1 rounded-lg text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)] hover:bg-[var(--vybe-elevated)]"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="p-4 space-y-3">
              <div className="relative">
                <Search className="w-4 h-4 text-[var(--vybe-text-muted)] absolute left-3 top-1/2 -translate-y-1/2" />
                <input
                  type="text"
                  value={memberSearch}
                  onChange={(e) => setMemberSearch(e.target.value)}
                  placeholder="Search VYBE people by name or @username..."
                  className="w-full pl-9 pr-3 py-2 bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] rounded-xl text-xs text-[var(--vybe-text)] placeholder-[var(--vybe-text-muted)] focus:outline-none focus:border-[var(--vybe-accent)]"
                  autoFocus
                />
              </div>

              <div className="max-h-72 overflow-y-auto space-y-1.5 no-scrollbar">
                {candidateMembers.length === 0 ? (
                  <p className="text-center text-xs text-[var(--vybe-text-secondary)] py-6">
                    No matching VYBE members found.
                  </p>
                ) : (
                  candidateMembers.map(member => (
                    <button
                      key={member.id}
                      type="button"
                      onClick={() => {
                        setShowNewChatModal(false);
                        startConversationWithUser(member);
                      }}
                      className="w-full flex items-center justify-between p-2.5 rounded-xl bg-[var(--vybe-elevated)]/60 hover:bg-[var(--vybe-elevated)] border border-transparent hover:border-[var(--vybe-accent)]/40 transition-all text-left"
                    >
                      <div className="flex items-center gap-2.5 min-w-0">
                        <img
                          src={member.avatarUrl}
                          alt={member.displayName}
                          className="w-9 h-9 rounded-full object-cover border border-[var(--vybe-border)]"
                        />
                        <div className="min-w-0">
                          <div className="flex items-center gap-1">
                            <span className="text-xs font-bold text-[var(--vybe-text)] truncate">
                              {member.displayName}
                            </span>
                            {member.badges?.map(b => (
                              <OfficialBadge key={b} badge={b} size="sm" />
                            ))}
                          </div>
                          <span className="text-[11px] text-[var(--vybe-text-secondary)]">@{member.username}</span>
                        </div>
                      </div>
                      <span className="text-[11px] font-semibold text-[var(--vybe-accent)] shrink-0">Message</span>
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
