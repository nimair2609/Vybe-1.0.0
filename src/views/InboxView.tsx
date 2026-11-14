import React, { useState, useEffect, useRef } from 'react';
import {
  Search,
  Send,
  Phone,
  Video,
  ArrowLeft,
  CheckCheck,
  Clock,
  AlertCircle,
  RotateCcw,
  Plus,
  X,
  MessageCircle,
  ArrowDown,
  Lock,
  Image as ImageIcon,
  RefreshCw,
  Wifi,
  WifiOff
} from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { OfficialBadge } from '../components/Badges';
import { VybeEmptyState } from '../components/VybeDesignSystem';
import { VybeAvatar, OptimizedImage } from '../components/OptimizedImage';
import { compressImageToBlob } from '../utils/imageCompressor';

export const InboxView: React.FC = () => {
  const {
    currentUser,
    allUsers,
    conversations,
    isLoadingConversations,
    dmError,
    dismissDmError,
    isRealtimeConnected,
    selectedConversation,
    selectConversation,
    startConversationWithUser,
    sendMessage,
    retryMessage,
    refreshConversations,
    startCall,
    openUserProfile,
    openAuthModal
  } = useVybe();

  const [chatInput, setChatInput] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [showNewChatModal, setShowNewChatModal] = useState(false);
  const [memberSearch, setMemberSearch] = useState('');
  const [modalError, setModalError] = useState<string | null>(null);
  const [startingPeerId, setStartingPeerId] = useState<string | null>(null);
  const [pendingBlob, setPendingBlob] = useState<Blob | null>(null);
  const [pendingPreviewUrl, setPendingPreviewUrl] = useState<string | null>(null);
  const [isSending, setIsSending] = useState(false);

  const messagesContainerRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
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
    if (currentUser && u.id === currentUser.id) return false;
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
    return el.scrollHeight - el.scrollTop - el.clientHeight <= 110;
  };

  const handleMessagesScroll = () => {
    const near = checkIsNearBottom();
    isNearBottomRef.current = near;
    if (near && unreadBelowCount > 0) setUnreadBelowCount(0);
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
      if (newestMsg?.isMe || isNearBottomRef.current) {
        requestAnimationFrame(() => scrollToLatest('smooth'));
      } else {
        setUnreadBelowCount(prev => prev + 1);
      }
    }
  }, [selectedConversation]);

  const handleSelectImage = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      const compressed = await compressImageToBlob(file, {
        maxWidth: 1280,
        maxHeight: 1280,
        quality: 0.82,
        format: 'image/webp'
      });
      if (pendingPreviewUrl) URL.revokeObjectURL(pendingPreviewUrl);
      setPendingBlob(compressed.blob);
      setPendingPreviewUrl(URL.createObjectURL(compressed.blob));
    } catch {
      // ignore invalid image
    } finally {
      e.target.value = '';
    }
  };

  const clearPendingAttachment = () => {
    if (pendingPreviewUrl) URL.revokeObjectURL(pendingPreviewUrl);
    setPendingBlob(null);
    setPendingPreviewUrl(null);
  };

  const handleSendMessage = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = chatInput.trim();
    if ((!trimmed && !pendingBlob) || !selectedConversation || isSending) return;

    const blobToSend = pendingBlob || undefined;
    setChatInput('');
    clearPendingAttachment();
    setIsSending(true);
    try {
      await sendMessage(selectedConversation.id, trimmed, blobToSend);
    } finally {
      setIsSending(false);
    }
  };

  const handleStartWithMember = async (member: typeof allUsers[number]) => {
    setModalError(null);
    setStartingPeerId(member.id);
    const res = await startConversationWithUser(member);
    setStartingPeerId(null);
    if (!res.ok) {
      setModalError(res.error || 'Unable to message this user due to privacy settings.');
      return;
    }
    setShowNewChatModal(false);
  };

  if (!currentUser) {
    return (
      <div className="w-full max-w-3xl mx-auto px-4 py-12 pb-28">
        <div className="p-8 rounded-3xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-center space-y-4 shadow-sm">
          <div className="w-14 h-14 rounded-2xl bg-[var(--vybe-accent-soft)] text-[var(--vybe-accent)] flex items-center justify-center mx-auto">
            <Lock className="w-7 h-7" />
          </div>
          <h1 className="text-xl font-extrabold brand-font text-[var(--vybe-text)]">
            Sign in for Direct Messages
          </h1>
          <p className="text-xs text-[var(--vybe-text-secondary)] max-w-md mx-auto leading-relaxed">
            Your conversations sync in real time with the VYBE Android app through Supabase. Sign in with your VYBE account to view and send messages.
          </p>
          <button
            type="button"
            onClick={() => openAuthModal('Sign in to access your VYBE Direct Messages.')}
            className="px-6 py-2.5 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold hover:opacity-95 transition-opacity"
          >
            Sign In to Inbox
          </button>
        </div>
      </div>
    );
  }

  const conversationListPane = (
    <div className="flex flex-col h-full">
      <div className="p-4 border-b border-[var(--vybe-border)] flex items-center justify-between gap-2">
        <div>
          <h1 className="text-lg font-extrabold brand-font text-[var(--vybe-text)] flex items-center gap-2">
            <MessageCircle className="w-5 h-5 text-[var(--vybe-accent)]" />
            <span>Direct Messages</span>
          </h1>
          <div className="flex items-center gap-2 mt-0.5">
            <span className="text-[11px] text-[var(--vybe-text-secondary)]">
              {conversations.length} thread{conversations.length === 1 ? '' : 's'}
            </span>
            <span
              className={`inline-flex items-center gap-1 text-[10px] font-semibold px-1.5 py-0.5 rounded-full ${
                isRealtimeConnected
                  ? 'bg-emerald-500/12 text-emerald-500'
                  : 'bg-amber-500/12 text-amber-500'
              }`}
            >
              {isRealtimeConnected ? <Wifi className="w-2.5 h-2.5" /> : <WifiOff className="w-2.5 h-2.5" />}
              <span>{isRealtimeConnected ? 'Live' : 'Connecting'}</span>
            </span>
          </div>
        </div>
        <div className="flex items-center gap-1.5 shrink-0">
          <button
            type="button"
            onClick={() => refreshConversations()}
            disabled={isLoadingConversations}
            aria-label="Refresh conversations"
            className="p-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)]"
          >
            <RefreshCw className={`w-4 h-4 ${isLoadingConversations ? 'animate-spin' : ''}`} />
          </button>
          <button
            type="button"
            onClick={() => {
              setMemberSearch('');
              setModalError(null);
              setShowNewChatModal(true);
            }}
            className="px-3 py-2 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold flex items-center gap-1.5 shadow-sm hover:opacity-95 transition-opacity"
          >
            <Plus className="w-4 h-4" />
            <span>New</span>
          </button>
        </div>
      </div>

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

      <div className="flex-1 overflow-y-auto p-2.5 space-y-1.5 no-scrollbar">
        {isLoadingConversations && conversations.length === 0 ? (
          <div className="p-8 text-center text-xs text-[var(--vybe-text-secondary)]">
            Loading your live conversations...
          </div>
        ) : filteredConversations.length === 0 ? (
          <div className="p-6 text-center text-xs text-[var(--vybe-text-secondary)] space-y-2">
            <p>{searchQuery ? 'No matching conversations found.' : 'No conversations yet.'}</p>
            {!searchQuery && (
              <button
                type="button"
                onClick={() => {
                  setMemberSearch('');
                  setModalError(null);
                  setShowNewChatModal(true);
                }}
                className="text-[var(--vybe-accent)] font-bold hover:underline"
              >
                Start a direct message
              </button>
            )}
          </div>
        ) : (
          filteredConversations.map(conv => {
            const isSelected = selectedConversation?.peerUser.id === conv.peerUser.id;
            return (
              <div
                key={`dm_${conv.id}_${conv.peerUser.id}`}
                onClick={() => selectConversation(conv)}
                className={`flex items-center justify-between p-3 rounded-2xl border cursor-pointer transition-colors ${
                  isSelected
                    ? 'bg-[var(--vybe-accent-soft)] border-[var(--vybe-accent)]/50'
                    : 'bg-[var(--vybe-surface)] border-transparent hover:bg-[var(--vybe-elevated)] hover:border-[var(--vybe-border)]'
                }`}
              >
                <div className="flex items-center gap-3 min-w-0">
                  <VybeAvatar
                    src={conv.peerUser.avatarUrl}
                    name={conv.peerUser.displayName}
                    username={conv.peerUser.username}
                    isOnline={conv.peerUser.isOnline}
                    className="w-11 h-11 rounded-full"
                  />
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
        <div
          onClick={() => openUserProfile(selectedConversation.peerUser)}
          className="flex items-center gap-3 min-w-0 cursor-pointer"
        >
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              selectConversation(null);
            }}
            className="lg:hidden p-1.5 rounded-lg text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)] hover:bg-[var(--vybe-elevated)] transition-colors"
            aria-label="Back to inbox"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>
          <VybeAvatar
            src={selectedConversation.peerUser.avatarUrl}
            name={selectedConversation.peerUser.displayName}
            username={selectedConversation.peerUser.username}
            isOnline={selectedConversation.peerUser.isOnline}
            className="w-9 h-9 rounded-full"
          />
          <div className="min-w-0">
            <div className="flex items-center gap-1.5">
              <span className="font-bold text-sm text-[var(--vybe-text)] truncate hover:underline">
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

      {/* DM Error Banner */}
      {dmError && (
        <div className="px-4 py-2 bg-red-500/12 border-b border-red-500/30 text-red-500 text-xs flex items-center justify-between gap-2">
          <div className="flex items-center gap-2 min-w-0">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span className="truncate">{dmError}</span>
          </div>
          <button type="button" onClick={dismissDmError} className="font-bold hover:underline shrink-0">
            Dismiss
          </button>
        </div>
      )}

      {/* Messages Stream */}
      <div
        ref={messagesContainerRef}
        onScroll={handleMessagesScroll}
        className="flex-1 overflow-y-auto p-4 space-y-3 bg-[var(--vybe-bg)]/50 no-scrollbar relative"
      >
        {selectedConversation.messages.length === 0 ? (
          <div className="text-center py-12 text-[var(--vybe-text-secondary)] text-xs">
            Start a real-time conversation with {selectedConversation.peerUser.displayName}.
          </div>
        ) : (
          selectedConversation.messages.map(msg => {
            const isFailed = msg.status === 'FAILED';
            const isSendingMsg = msg.status === 'SENDING';
            return (
              <div
                key={msg.id}
                className={`flex flex-col ${msg.isMe ? 'items-end' : 'items-start'}`}
              >
                <div
                  className={`max-w-[80%] rounded-2xl px-4 py-2.5 text-xs shadow-sm ${
                    isFailed
                      ? 'bg-red-600/90 text-white keep-white rounded-br-sm border border-red-400'
                      : msg.isMe
                        ? 'bg-[var(--vybe-accent)] text-white keep-white rounded-br-sm'
                        : 'bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-[var(--vybe-text)] rounded-bl-sm'
                  }`}
                >
                  {msg.mediaUrl && (
                    <div className="mb-2 rounded-xl overflow-hidden max-w-xs">
                      <OptimizedImage
                        src={msg.mediaUrl}
                        alt="Chat attachment"
                        className="w-full max-h-60 object-cover rounded-xl"
                      />
                    </div>
                  )}
                  {msg.text && (
                    <p className="leading-relaxed whitespace-pre-wrap break-words">{msg.text}</p>
                  )}
                  <div
                    className={`flex items-center gap-1.5 mt-1 text-[9px] ${
                      msg.isMe ? 'text-white/80 keep-white justify-end' : 'text-[var(--vybe-text-muted)]'
                    }`}
                  >
                    <span>{msg.timestamp}</span>
                    {msg.isMe && isSendingMsg && <Clock className="w-3 h-3 text-white/80" />}
                    {msg.isMe && !isSendingMsg && !isFailed && (
                      <CheckCheck className="w-3 h-3 text-white keep-white" />
                    )}
                    {msg.isMe && isFailed && <AlertCircle className="w-3 h-3 text-white" />}
                  </div>
                </div>
                {isFailed && (
                  <button
                    type="button"
                    onClick={() => retryMessage(selectedConversation.id, msg.id)}
                    className="mt-1 inline-flex items-center gap-1 text-[10px] font-bold text-red-500 hover:underline"
                  >
                    <RotateCcw className="w-3 h-3" />
                    <span>{msg.errorMessage ? `${msg.errorMessage} · Retry` : 'Failed to send · Retry'}</span>
                  </button>
                )}
              </div>
            );
          })
        )}
      </div>

      {unreadBelowCount > 0 && (
        <button
          type="button"
          onClick={() => scrollToLatest('smooth')}
          className="absolute bottom-16 left-1/2 -translate-x-1/2 px-3.5 py-1.5 rounded-full bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold shadow-lg flex items-center gap-1.5"
        >
          <ArrowDown className="w-3.5 h-3.5" />
          <span>{unreadBelowCount === 1 ? '1 new message' : `${unreadBelowCount} new messages`}</span>
        </button>
      )}

      {/* Pending image attachment bar */}
      {pendingPreviewUrl && (
        <div className="px-3 py-2 bg-[var(--vybe-elevated)] border-t border-[var(--vybe-border)] flex items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <img src={pendingPreviewUrl} alt="Attachment preview" className="w-10 h-10 rounded-lg object-cover" />
            <span className="text-[11px] font-semibold text-[var(--vybe-text)]">Ready to send compressed image</span>
          </div>
          <button
            type="button"
            onClick={clearPendingAttachment}
            className="p-1.5 rounded-lg text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)]"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

      {/* Input Bar */}
      <form
        onSubmit={handleSendMessage}
        className="p-3 bg-[var(--vybe-surface)] border-t border-[var(--vybe-border)] flex items-center gap-2"
      >
        <input
          ref={fileInputRef}
          type="file"
          accept="image/*"
          onChange={handleSelectImage}
          className="hidden"
        />
        <button
          type="button"
          onClick={() => fileInputRef.current?.click()}
          aria-label="Attach image"
          title="Attach image"
          className="p-2.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-accent)] transition-colors"
        >
          <ImageIcon className="w-4 h-4" />
        </button>
        <input
          type="text"
          value={chatInput}
          onChange={(e) => setChatInput(e.target.value)}
          placeholder={`Message ${selectedConversation.peerUser.displayName}...`}
          className="flex-1 px-4 py-2.5 bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] rounded-xl text-xs text-[var(--vybe-text)] placeholder-[var(--vybe-text-muted)] focus:outline-none focus:border-[var(--vybe-accent)]"
        />
        <button
          type="submit"
          disabled={(!chatInput.trim() && !pendingBlob) || isSending}
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
          setModalError(null);
          setShowNewChatModal(true);
        }}
      />
    </div>
  );

  return (
    <div className="w-full max-w-6xl mx-auto px-3 sm:px-4 py-4 pb-24 lg:pb-6">
      {dmError && !selectedConversation && (
        <div className="mb-3 px-4 py-2.5 rounded-2xl bg-red-500/10 border border-red-500/30 text-red-500 text-xs flex items-center justify-between gap-2">
          <span>{dmError}</span>
          <button type="button" onClick={dismissDmError} className="font-bold hover:underline">
            Dismiss
          </button>
        </div>
      )}

      <div className="h-[calc(100dvh-148px)] lg:h-[calc(100dvh-96px)] bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-2xl overflow-hidden shadow-sm grid grid-cols-1 lg:grid-cols-12">
        <div
          className={`lg:col-span-4 xl:col-span-4 lg:border-r border-[var(--vybe-border)] h-full overflow-hidden ${
            selectedConversation ? 'hidden lg:block' : 'block'
          }`}
        >
          {conversationListPane}
        </div>
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
              {modalError && (
                <div className="p-3 rounded-xl bg-red-500/10 border border-red-500/30 text-red-500 text-xs flex items-start gap-2">
                  <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                  <span>{modalError}</span>
                </div>
              )}
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
                      disabled={startingPeerId === member.id}
                      onClick={() => handleStartWithMember(member)}
                      className="w-full flex items-center justify-between p-2.5 rounded-xl bg-[var(--vybe-elevated)]/60 hover:bg-[var(--vybe-elevated)] border border-transparent hover:border-[var(--vybe-accent)]/40 transition-all text-left disabled:opacity-60"
                    >
                      <div className="flex items-center gap-2.5 min-w-0">
                        <VybeAvatar
                          src={member.avatarUrl}
                          name={member.displayName}
                          username={member.username}
                          className="w-9 h-9 rounded-full"
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
                      <span className="text-[11px] font-semibold text-[var(--vybe-accent)] shrink-0">
                        {startingPeerId === member.id ? 'Opening...' : 'Message'}
                      </span>
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
