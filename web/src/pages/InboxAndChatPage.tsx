import React, { useState, useEffect, useRef } from 'react';
import {
  MessageSquare,
  Phone,
  Video,
  Send,
  Image as ImageIcon,
  Paperclip,
  Check,
  CheckCheck,
  Search,
  Plus,
  X,
  Lock,
  ArrowLeft,
  Trash2
} from 'lucide-react';
import { VybeAvatar, VerifiedBadge } from '../components/VybeCoreComponents';
import { supabase, isSupabaseConfigured } from '../lib/supabase';
import {
  fetchConversations,
  getOrCreateDirectConversation,
  sendDirectMessagePersisted,
  deleteMessageForEveryone,
  uploadMediaToBucket,
  searchUsersAndPosts
} from '../lib/vybeApi';
import { formatMessageTimestamp, isRecentlyOnline } from '../lib/utils';
import type { Conversation, DirectMessage, VybeUser } from '../types/vybe';

export const InboxAndChatPage: React.FC<{
  currentUser: VybeUser;
  onStartCall: (peerUser: VybeUser, isVideo: boolean) => void;
  initialPeerUser?: VybeUser | null;
}> = ({ currentUser, onStartCall, initialPeerUser }) => {
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [activeConvId, setActiveConvId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [messageInput, setMessageInput] = useState('');
  const [sending, setSending] = useState(false);
  const [showNewChatModal, setShowNewChatModal] = useState(false);
  const [userSearchQuery, setUserSearchQuery] = useState('');
  const [searchedUsers, setSearchedUsers] = useState<VybeUser[]>([]);
  const [isMobileChatOpen, setIsMobileChatOpen] = useState(false);

  const messagesEndRef = useRef<HTMLDivElement | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const loadConversations = async () => {
    try {
      const convs = await fetchConversations(currentUser.id);
      setConversations(convs);
      if (initialPeerUser) {
        const found = convs.find((c) => c.peerUser?.id === initialPeerUser.id);
        if (found) {
          setActiveConvId(found.id);
          setIsMobileChatOpen(true);
        } else {
          // Create new conversation with peer
          const newId = await getOrCreateDirectConversation(currentUser.id, initialPeerUser.id);
          const newConv: Conversation = {
            id: newId,
            isGroup: false,
            groupName: null,
            peerUser: initialPeerUser,
            members: [currentUser, initialPeerUser],
            messages: [],
            unreadCount: 0,
            updatedAtIso: new Date().toISOString()
          };
          setConversations([newConv, ...convs]);
          setActiveConvId(newId);
          setIsMobileChatOpen(true);
        }
      } else if (!activeConvId && convs.length > 0) {
        setActiveConvId(convs[0].id);
      }
    } catch {
      // Fallback
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadConversations();
  }, [currentUser.id]);

  // Subscribe to realtime messages for active conversation
  useEffect(() => {
    if (!activeConvId || !isSupabaseConfigured) return;

    const channel = supabase
      .channel(`chat_${activeConvId}`)
      .on(
        'postgres_changes',
        {
          event: 'INSERT',
          schema: 'public',
          table: 'messages',
          filter: `conversation_id=eq.${activeConvId}`
        },
        (payload) => {
          const newRow = payload.new as {
            id: string;
            conversation_id: string;
            sender_id: string;
            content: string;
            media_url?: string;
            media_type?: string;
            is_deleted_for_everyone?: boolean;
            created_at: string;
          };
          if (newRow.is_deleted_for_everyone) return;

          const newMsg: DirectMessage = {
            id: String(newRow.id),
            conversationId: String(newRow.conversation_id),
            senderId: String(newRow.sender_id),
            text: newRow.content || '',
            mediaUrl: newRow.media_url || null,
            mediaType: newRow.media_type || 'TEXT',
            isDeletedForEveryone: false,
            isEdited: false,
            isRead: true,
            reactions: [],
            createdAtIso: newRow.created_at || new Date().toISOString(),
            status: 'SENT'
          };

          setConversations((prev) =>
            prev.map((c) => {
              if (c.id === activeConvId) {
                const exists = c.messages.some((m) => m.id === newMsg.id);
                return exists ? c : { ...c, messages: [...c.messages, newMsg] };
              }
              return c;
            })
          );
        }
      )
      .on(
        'postgres_changes',
        {
          event: 'UPDATE',
          schema: 'public',
          table: 'messages',
          filter: `conversation_id=eq.${activeConvId}`
        },
        (payload) => {
          const updated = payload.new as {
            id: string;
            content?: string;
            is_edited?: boolean;
            is_deleted_for_everyone?: boolean;
          };
          setConversations((prev) =>
            prev.map((c) => {
              if (c.id !== activeConvId) return c;
              if (updated.is_deleted_for_everyone) {
                return { ...c, messages: c.messages.filter((m) => m.id !== String(updated.id)) };
              }
              return {
                ...c,
                messages: c.messages.map((m) =>
                  m.id === String(updated.id)
                    ? { ...m, text: String(updated.content ?? m.text), isEdited: Boolean(updated.is_edited) }
                    : m
                )
              };
            })
          );
        }
      )
      .on(
        'postgres_changes',
        {
          event: 'DELETE',
          schema: 'public',
          table: 'messages'
        },
        (payload) => {
          const oldId = String((payload.old as { id?: string })?.id || '');
          if (!oldId) return;
          setConversations((prev) =>
            prev.map((c) => ({
              ...c,
              messages: c.messages.filter((m) => m.id !== oldId)
            }))
          );
        }
      )
      .subscribe();

    return () => {
      supabase.removeChannel(channel);
    };
  }, [activeConvId]);

  // Scroll to bottom on message updates
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [conversations, activeConvId]);

  const activeConversation = conversations.find((c) => c.id === activeConvId);

  const handleSendMessage = async (e?: React.FormEvent) => {
    e?.preventDefault();
    if (!activeConvId || !messageInput.trim() || sending) return;

    const textToSend = messageInput.trim();
    setMessageInput('');
    setSending(true);

    const tempId = `temp_${Date.now()}`;
    const optimisticMsg: DirectMessage = {
      id: tempId,
      conversationId: activeConvId,
      senderId: currentUser.id,
      text: textToSend,
      mediaUrl: null,
      mediaType: 'TEXT',
      isDeletedForEveryone: false,
      isEdited: false,
      isRead: true,
      reactions: [],
      createdAtIso: new Date().toISOString(),
      status: 'SENDING'
    };

    setConversations((prev) =>
      prev.map((c) =>
        c.id === activeConvId ? { ...c, messages: [...c.messages, optimisticMsg] } : c
      )
    );

    try {
      const realId = await sendDirectMessagePersisted({
        conversationId: activeConvId,
        senderId: currentUser.id,
        text: textToSend,
        mediaType: 'TEXT'
      });
      setConversations((prev) =>
        prev.map((c) =>
          c.id === activeConvId
            ? {
                ...c,
                messages: c.messages.map((m) =>
                  m.id === tempId ? { ...m, id: realId, status: 'SENT' } : m
                )
              }
            : c
        )
      );
    } catch {
      setConversations((prev) =>
        prev.map((c) =>
          c.id === activeConvId
            ? {
                ...c,
                messages: c.messages.map((m) =>
                  m.id === tempId ? { ...m, status: 'FAILED' } : m
                )
              }
            : c
        )
      );
    } finally {
      setSending(false);
    }
  };

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file || !activeConvId) return;

    const isVideo = file.type.startsWith('video/');
    const mediaType = isVideo ? 'VIDEO' : 'IMAGE';

    setSending(true);
    try {
      const uploaded = await uploadMediaToBucket(
        'chat-media',
        currentUser.id,
        file,
        () => undefined
      );
      await sendDirectMessagePersisted({
        conversationId: activeConvId,
        senderId: currentUser.id,
        text: file.name,
        mediaUrl: uploaded.publicUrl,
        mediaType
      });
      await loadConversations();
    } catch {
      // Handle error
    } finally {
      setSending(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const handleSearchUsers = async (q: string) => {
    setUserSearchQuery(q);
    if (q.trim().length < 2) {
      setSearchedUsers([]);
      return;
    }
    const res = await searchUsersAndPosts(q);
    setSearchedUsers(res.users.filter((u) => u.id !== currentUser.id));
  };

  const handleSelectUserForChat = async (user: VybeUser) => {
    setShowNewChatModal(false);
    setUserSearchQuery('');
    const existing = conversations.find((c) => c.peerUser?.id === user.id);
    if (existing) {
      setActiveConvId(existing.id);
      setIsMobileChatOpen(true);
      return;
    }
    try {
      const convId = await getOrCreateDirectConversation(currentUser.id, user.id);
      const newConv: Conversation = {
        id: convId,
        isGroup: false,
        groupName: null,
        peerUser: user,
        members: [currentUser, user],
        messages: [],
        unreadCount: 0,
        updatedAtIso: new Date().toISOString()
      };
      setConversations([newConv, ...conversations]);
      setActiveConvId(convId);
      setIsMobileChatOpen(true);
    } catch {
      // Fallback
    }
  };

  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: isMobileChatOpen ? '0px 1fr' : '340px 1fr',
        height: 'calc(100dvh - 20px)',
        background: 'var(--bg-surface)',
        borderRadius: 'var(--radius-md)',
        border: '1px solid var(--border-subtle)',
        overflow: 'hidden',
        margin: '10px'
      }}
      className="vybe-inbox-grid"
    >
      {/* Left Sidebar: Conversations List */}
      <div
        style={{
          borderRight: '1px solid var(--border-subtle)',
          display: 'flex',
          flexDirection: 'column',
          background: 'var(--bg-obsidian)',
          overflow: 'hidden'
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '16px 18px', borderBottom: '1px solid var(--border-subtle)' }}>
          <h3 className="vybe-brand-font" style={{ margin: 0, fontSize: '1.2rem', fontWeight: 800 }}>
            Messages
          </h3>
          <button
            onClick={() => setShowNewChatModal(true)}
            className="vybe-btn vybe-btn-primary"
            style={{ padding: '6px 12px', fontSize: '0.8rem' }}
          >
            <Plus size={16} />
            <span>New Chat</span>
          </button>
        </div>

        <div style={{ flex: 1, overflowY: 'auto' }}>
          {loading ? (
            <div style={{ padding: '24px', textAlign: 'center', color: 'var(--text-muted)' }}>
              Loading messages...
            </div>
          ) : conversations.length === 0 ? (
            <div style={{ padding: '32px 18px', textAlign: 'center', color: 'var(--text-secondary)' }}>
              <MessageSquare size={36} color="var(--vybe-violet)" style={{ marginBottom: '10px' }} />
              <p style={{ fontSize: '0.9rem', marginBottom: '16px' }}>No conversations yet.</p>
              <button onClick={() => setShowNewChatModal(true)} className="vybe-btn vybe-btn-primary" style={{ fontSize: '0.85rem' }}>
                Start a Message
              </button>
            </div>
          ) : (
            conversations.map((conv) => {
              const isActive = conv.id === activeConvId;
              const lastMsg = conv.messages[conv.messages.length - 1];
              const isOnline = conv.peerUser?.isOnline ?? isRecentlyOnline(conv.peerUser?.lastSeenAtIso);

              return (
                <div
                  key={conv.id}
                  onClick={() => {
                    setActiveConvId(conv.id);
                    setIsMobileChatOpen(true);
                  }}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '12px',
                    padding: '14px 16px',
                    cursor: 'pointer',
                    background: isActive ? 'var(--bg-elevated)' : 'transparent',
                    borderLeft: isActive ? '3px solid var(--vybe-violet)' : '3px solid transparent',
                    borderBottom: '1px solid var(--border-subtle)',
                    transition: 'background 0.15s ease'
                  }}
                >
                  <div style={{ position: 'relative' }}>
                    <VybeAvatar user={conv.peerUser} size={42} />
                    {isOnline && (
                      <span
                        style={{
                          position: 'absolute',
                          bottom: '1px',
                          right: '1px',
                          width: '10px',
                          height: '10px',
                          background: 'var(--vybe-green)',
                          borderRadius: '50%',
                          border: '2px solid var(--bg-obsidian)'
                        }}
                      />
                    )}
                  </div>

                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '3px' }}>
                      <span style={{ fontWeight: 700, fontSize: '0.9rem', color: 'var(--text-primary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {conv.groupName || conv.peerUser?.displayName}
                      </span>
                      {lastMsg && (
                        <span style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
                          {formatMessageTimestamp(lastMsg.createdAtIso).split('•')[0].trim()}
                        </span>
                      )}
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: '180px' }}>
                        {lastMsg
                          ? lastMsg.mediaType === 'CALL_LOG'
                            ? `📞 ${lastMsg.text || 'Call'}`
                            : lastMsg.text || 'Media attachment'
                          : 'No messages yet'}
                      </span>
                      {conv.unreadCount > 0 && (
                        <span
                          style={{
                            background: 'var(--vybe-magenta)',
                            color: '#fff',
                            fontSize: '0.7rem',
                            fontWeight: 700,
                            padding: '2px 6px',
                            borderRadius: 'var(--radius-pill)'
                          }}
                        >
                          {conv.unreadCount}
                        </span>
                      )}
                    </div>
                  </div>
                </div>
              );
            })
          )}
        </div>
      </div>

      {/* Right Area: Active Chat */}
      {activeConversation ? (
        <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: 'var(--bg-surface)' }}>
          {/* Chat Header */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '12px 18px',
              borderBottom: '1px solid var(--border-subtle)',
              background: 'var(--bg-glass)',
              backdropFilter: 'blur(12px)'
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <button
                onClick={() => setIsMobileChatOpen(false)}
                className="vybe-btn vybe-mobile-back-btn"
                style={{ padding: '6px', background: 'none', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer' }}
              >
                <ArrowLeft size={20} />
              </button>

              <VybeAvatar user={activeConversation.peerUser} size={40} />
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <span style={{ fontWeight: 700, fontSize: '0.95rem' }}>
                    {activeConversation.groupName || activeConversation.peerUser?.displayName}
                  </span>
                  <VerifiedBadge user={activeConversation.peerUser} />
                </div>
                <span style={{ fontSize: '0.78rem', color: activeConversation.peerUser?.isOnline ? 'var(--vybe-green)' : 'var(--text-muted)' }}>
                  {activeConversation.peerUser?.isOnline ? 'Active now' : `@${activeConversation.peerUser?.username}`}
                </span>
              </div>
            </div>

            {/* Calling Buttons */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <button
                onClick={() => onStartCall(activeConversation.peerUser, false)}
                className="vybe-btn"
                style={{ padding: '8px', background: 'var(--bg-elevated)', borderRadius: '50%' }}
                title="Voice Call"
              >
                <Phone size={18} color="var(--vybe-cyan)" />
              </button>
              <button
                onClick={() => onStartCall(activeConversation.peerUser, true)}
                className="vybe-btn"
                style={{ padding: '8px', background: 'var(--bg-elevated)', borderRadius: '50%' }}
                title="Video Call"
              >
                <Video size={18} color="var(--vybe-violet)" />
              </button>
            </div>
          </div>

          {/* Messages Stream */}
          <div style={{ flex: 1, overflowY: 'auto', padding: '18px 20px', display: 'flex', flexDirection: 'column', gap: '12px' }}>
            {activeConversation.messages.length === 0 ? (
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', color: 'var(--text-muted)' }}>
                <Lock size={32} color="var(--vybe-violet)" style={{ marginBottom: '8px' }} />
                <p style={{ fontSize: '0.9rem', fontWeight: 600 }}>End-to-End Isolated Message Stream</p>
                <p style={{ fontSize: '0.8rem', maxWidth: '300px', textAlign: 'center' }}>
                  Messages are only readable by authorized conversation participants.
                </p>
              </div>
            ) : (
              activeConversation.messages.map((msg) => {
                const isMe = msg.senderId === currentUser.id;

                if (msg.mediaType === 'CALL_LOG' && !msg.isDeletedForEveryone) {
                  const meta = msg.mediaUrl || '';
                  const isVideoLog = /type=video/i.test(meta) || /video call/i.test(msg.text);
                  const statusMatch = meta.match(/status=([^&]+)/i);
                  const durMatch = meta.match(/duration=(\d+)/i);
                  const durSecs = durMatch ? Number(durMatch[1]) : 0;
                  const formattedDur =
                    durSecs > 0
                      ? `${String(Math.floor(durSecs / 60)).padStart(2, '0')}:${String(durSecs % 60).padStart(2, '0')}`
                      : null;
                  const statusStr = (statusMatch?.[1] || '').toUpperCase();
                  const isMissed =
                    statusStr === 'MISSED' ||
                    statusStr === 'DECLINED' ||
                    /missed|declined/i.test(msg.text);

                  return (
                    <div
                      key={msg.id}
                      style={{
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: isMe ? 'flex-end' : 'flex-start',
                        width: '100%'
                      }}
                    >
                      <div
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: '12px',
                          padding: '10px 14px',
                          borderRadius: 'var(--radius-md)',
                          background: 'var(--bg-elevated)',
                          border: isMissed
                            ? '1px solid rgba(255, 77, 109, 0.45)'
                            : '1px solid rgba(0, 229, 255, 0.35)',
                          minWidth: '230px',
                          maxWidth: '320px'
                        }}
                      >
                        <div
                          style={{
                            width: 36,
                            height: 36,
                            borderRadius: '50%',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            background: isMissed
                              ? 'rgba(255, 77, 109, 0.16)'
                              : 'rgba(124, 77, 255, 0.18)'
                          }}
                        >
                          {isVideoLog ? (
                            <Video size={18} color={isMissed ? '#FF4D6D' : 'var(--vybe-cyan)'} />
                          ) : (
                            <Phone size={18} color={isMissed ? '#FF4D6D' : 'var(--vybe-cyan)'} />
                          )}
                        </div>
                        <div style={{ flex: 1, minWidth: 0 }}>
                          <div style={{ fontWeight: 700, fontSize: '0.88rem', color: 'var(--text-primary)' }}>
                            {msg.text || (isVideoLog ? 'Video call' : 'Voice call')}
                          </div>
                          <div style={{ fontSize: '0.74rem', color: isMissed ? '#FF4D6D' : 'var(--text-muted)' }}>
                            {formattedDur ? `Duration ${formattedDur} • ` : ''}
                            {formatMessageTimestamp(msg.createdAtIso)}
                          </div>
                        </div>
                        {!activeConversation.isGroup && (
                          <button
                            type="button"
                            className="vybe-btn"
                            onClick={() => onStartCall(activeConversation.peerUser, isVideoLog)}
                            style={{
                              padding: '6px 10px',
                              fontSize: '0.75rem',
                              background: 'rgba(0, 229, 255, 0.14)',
                              color: 'var(--vybe-cyan)'
                            }}
                          >
                            Call
                          </button>
                        )}
                      </div>
                    </div>
                  );
                }

                return (
                  <div
                    key={msg.id}
                    style={{
                      display: 'flex',
                      flexDirection: 'column',
                      alignItems: isMe ? 'flex-end' : 'flex-start',
                      width: '100%'
                    }}
                  >
                    <div
                      style={{
                        maxWidth: '70%',
                        padding: msg.mediaUrl ? '6px' : '10px 16px',
                        borderRadius: 'var(--radius-md)',
                        background: isMe ? 'var(--vybe-gradient)' : 'var(--bg-elevated)',
                        color: isMe ? '#fff' : 'var(--text-primary)',
                        borderTopRightRadius: isMe ? '4px' : 'var(--radius-md)',
                        borderTopLeftRadius: isMe ? 'var(--radius-md)' : '4px',
                        boxShadow: '0 2px 8px rgba(0,0,0,0.18)'
                      }}
                    >
                      {msg.mediaUrl && (
                        <div style={{ borderRadius: 'var(--radius-sm)', overflow: 'hidden', marginBottom: msg.text ? '6px' : '0' }}>
                          {msg.mediaType === 'VIDEO' ? (
                            <video src={msg.mediaUrl} controls style={{ maxWidth: '100%', maxHeight: '280px', borderRadius: 'var(--radius-sm)' }} />
                          ) : (
                            <img src={msg.mediaUrl} alt="Chat media" style={{ maxWidth: '100%', maxHeight: '280px', objectFit: 'cover', borderRadius: 'var(--radius-sm)' }} />
                          )}
                        </div>
                      )}
                      {msg.text && (
                        <p style={{ margin: 0, fontSize: '0.92rem', lineHeight: '1.4', wordBreak: 'break-word', padding: msg.mediaUrl ? '4px 6px' : 0 }}>
                          {msg.text}
                        </p>
                      )}
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginTop: '3px', fontSize: '0.7rem', color: 'var(--text-muted)' }}>
                      <span>{formatMessageTimestamp(msg.createdAtIso)}</span>
                      {isMe && (
                        <>
                          {msg.status === 'SENDING' ? (
                            <span>• Sending...</span>
                          ) : msg.status === 'FAILED' ? (
                            <span style={{ color: 'var(--vybe-danger)' }}>• Failed</span>
                          ) : (
                            <CheckCheck size={14} color="var(--vybe-cyan)" />
                          )}
                          <button
                            type="button"
                            title="Delete for Everyone"
                            onClick={async () => {
                              setConversations((prev) =>
                                prev.map((c) =>
                                  c.id === activeConversation.id
                                    ? { ...c, messages: c.messages.filter((m) => m.id !== msg.id) }
                                    : c
                                )
                              );
                              await deleteMessageForEveryone(msg.id);
                            }}
                            style={{
                              background: 'transparent',
                              border: 'none',
                              padding: '2px',
                              cursor: 'pointer',
                              color: 'var(--text-muted)',
                              display: 'inline-flex',
                              alignItems: 'center'
                            }}
                          >
                            <Trash2 size={12} />
                          </button>
                        </>
                      )}
                    </div>
                  </div>
                );
              })
            )}
            <div ref={messagesEndRef} />
          </div>

          {/* Message Input Bar */}
          <form
            onSubmit={handleSendMessage}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '10px',
              padding: '12px 18px',
              borderTop: '1px solid var(--border-subtle)',
              background: 'var(--bg-elevated)'
            }}
          >
            <input
              type="file"
              ref={fileInputRef}
              onChange={handleFileUpload}
              accept="image/*,video/mp4,video/webm"
              style={{ display: 'none' }}
            />
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              className="vybe-btn"
              style={{ padding: '8px', background: 'var(--bg-surface)', borderRadius: '50%' }}
              title="Attach Photo or Video"
            >
              <Paperclip size={18} color="var(--text-secondary)" />
            </button>

            <input
              type="text"
              placeholder="Send a direct message..."
              value={messageInput}
              onChange={(e) => setMessageInput(e.target.value)}
              style={{
                flex: 1,
                background: 'var(--bg-surface)',
                border: '1px solid var(--border-subtle)',
                borderRadius: 'var(--radius-pill)',
                padding: '10px 18px',
                color: 'var(--text-primary)',
                fontSize: '0.9rem',
                outline: 'none'
              }}
            />

            <button
              type="submit"
              disabled={sending || !messageInput.trim()}
              className="vybe-btn vybe-btn-primary"
              style={{ padding: '10px 16px', borderRadius: 'var(--radius-pill)' }}
            >
              <Send size={16} />
            </button>
          </form>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', color: 'var(--text-muted)' }}>
          <MessageSquare size={48} color="var(--vybe-violet)" style={{ marginBottom: '12px' }} />
          <h3 style={{ color: 'var(--text-primary)', marginBottom: '6px' }}>Your Direct Messages</h3>
          <p style={{ fontSize: '0.9rem' }}>Select a conversation from the left to start chatting.</p>
        </div>
      )}

      {/* New Chat Search Modal */}
      {showNewChatModal && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0,0,0,0.7)',
            zIndex: 90,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '16px'
          }}
          onClick={() => setShowNewChatModal(false)}
        >
          <div
            style={{
              width: '100%',
              maxWidth: '440px',
              background: 'var(--bg-surface)',
              borderRadius: 'var(--radius-lg)',
              border: '1px solid var(--border-subtle)',
              padding: '20px',
              display: 'flex',
              flexDirection: 'column',
              gap: '14px',
              maxHeight: '80vh'
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h4 style={{ margin: 0, fontSize: '1.1rem', fontWeight: 800 }}>New Message</h4>
              <button onClick={() => setShowNewChatModal(false)} style={{ background: 'none', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer' }}>
                <X size={18} />
              </button>
            </div>

            <div style={{ position: 'relative' }}>
              <Search size={16} color="var(--text-muted)" style={{ position: 'absolute', left: '14px', top: '50%', transform: 'translateY(-50%)' }} />
              <input
                type="text"
                placeholder="Search username or name..."
                value={userSearchQuery}
                onChange={(e) => handleSearchUsers(e.target.value)}
                autoFocus
                style={{
                  width: '100%',
                  background: 'var(--bg-elevated)',
                  border: '1px solid var(--border-subtle)',
                  borderRadius: 'var(--radius-pill)',
                  padding: '10px 16px 10px 38px',
                  color: 'var(--text-primary)',
                  fontSize: '0.9rem',
                  outline: 'none'
                }}
              />
            </div>

            <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '8px' }}>
              {searchedUsers.length === 0 ? (
                <p style={{ textAlign: 'center', color: 'var(--text-muted)', fontSize: '0.85rem', padding: '24px 0' }}>
                  {userSearchQuery ? 'No users found' : 'Type at least 2 characters to search users'}
                </p>
              ) : (
                searchedUsers.map((u) => (
                  <div
                    key={u.id}
                    onClick={() => handleSelectUserForChat(u)}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '12px',
                      padding: '10px 14px',
                      borderRadius: 'var(--radius-sm)',
                      background: 'var(--bg-elevated)',
                      cursor: 'pointer'
                    }}
                  >
                    <VybeAvatar user={u} size={36} />
                    <div style={{ flex: 1 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                        <span style={{ fontWeight: 700, fontSize: '0.9rem' }}>{u.displayName}</span>
                        <VerifiedBadge user={u} />
                      </div>
                      <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>@{u.username}</span>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
