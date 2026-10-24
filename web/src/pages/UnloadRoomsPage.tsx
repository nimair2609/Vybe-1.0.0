import React, { useState, useEffect, useRef } from 'react';
import {
  Radio,
  Plus,
  Users,
  Clock,
  Send,
  Volume2,
  Lock,
  Sparkles,
  ArrowRight,
  X,
  Shield,
  EyeOff
} from 'lucide-react';
import { VybeAvatar } from '../components/VybeCoreComponents';
import { supabase, isSupabaseConfigured } from '../lib/supabase';
import {
  fetchRooms,
  createLiveRoom,
  lookupAndJoinRoomByCode,
  fetchRoomMessages,
  postRoomMessage
} from '../lib/vybeApi';
import { formatRelativeTimestamp, normalizeRoomCode } from '../lib/utils';
import type { UnloadRoom, UnloadRoomMessage, UnloadRoomMode, VybeUser } from '../types/vybe';

export const UnloadRoomsPage: React.FC<{
  currentUser: VybeUser;
}> = ({ currentUser }) => {
  const [rooms, setRooms] = useState<UnloadRoom[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeRoom, setActiveRoom] = useState<UnloadRoom | null>(null);
  const [roomMessages, setRoomMessages] = useState<UnloadRoomMessage[]>([]);
  const [messageText, setMessageText] = useState('');
  const [isAnonymousMessage, setIsAnonymousMessage] = useState(false);
  const [sendingMessage, setSendingMessage] = useState(false);

  // Join by code state
  const [joinCodeInput, setJoinCodeInput] = useState('');
  const [joinError, setJoinError] = useState<string | null>(null);
  const [joining, setJoining] = useState(false);

  // Create room modal state
  const [showCreateRoom, setShowCreateRoom] = useState(false);
  const [newTitle, setNewTitle] = useState('');
  const [newDescription, setNewDescription] = useState('');
  const [newTopicTag, setNewTopicTag] = useState('Late Night Reflection');
  const [newCategory, setNewCategory] = useState('Chill');
  const [newMode, setNewMode] = useState<UnloadRoomMode>('VOICE_AND_TEXT');
  const [newDuration, setNewDuration] = useState(45);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  const messagesEndRef = useRef<HTMLDivElement | null>(null);

  const loadAllRooms = async () => {
    try {
      const data = await fetchRooms();
      setRooms(data);
    } catch {
      // Fallback
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadAllRooms();
  }, []);

  // Subscribe to room messages when inside an active room
  useEffect(() => {
    if (!activeRoom) return;

    let isMounted = true;
    const fetchMsgs = async () => {
      try {
        const msgs = await fetchRoomMessages(activeRoom.id);
        if (isMounted) setRoomMessages(msgs);
      } catch {
        // Fallback
      }
    };
    void fetchMsgs();

    if (!isSupabaseConfigured) return;

    const channel = supabase
      .channel(`room_${activeRoom.id}`)
      .on(
        'postgres_changes',
        {
          event: 'INSERT',
          schema: 'public',
          table: 'room_messages',
          filter: `room_id=eq.${activeRoom.id}`
        },
        (payload) => {
          const row = payload.new as {
            id: string;
            room_id: string;
            sender_id: string;
            sender_name?: string;
            is_anonymous?: boolean;
            content: string;
            created_at: string;
          };
          const newMsg: UnloadRoomMessage = {
            id: String(row.id),
            roomId: String(row.room_id),
            senderId: String(row.sender_id),
            senderName: row.sender_name || 'VYBE Member',
            isAnonymous: Boolean(row.is_anonymous),
            content: row.content || '',
            createdAtIso: row.created_at || new Date().toISOString()
          };
          setRoomMessages((prev) => {
            const exists = prev.some((m) => m.id === newMsg.id);
            return exists ? prev : [...prev, newMsg];
          });
        }
      )
      .subscribe();

    return () => {
      isMounted = false;
      supabase.removeChannel(channel);
    };
  }, [activeRoom?.id]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [roomMessages]);

  const handleJoinByCode = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!joinCodeInput.trim()) return;
    setJoining(true);
    setJoinError(null);
    try {
      const room = await lookupAndJoinRoomByCode(joinCodeInput, currentUser.id);
      setActiveRoom(room);
      setJoinCodeInput('');
    } catch (err: unknown) {
      setJoinError(err instanceof Error ? err.message : 'Invalid room code');
    } finally {
      setJoining(false);
    }
  };

  const handleCreateRoom = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTitle.trim()) return;
    setCreating(true);
    setCreateError(null);
    try {
      const created = await createLiveRoom({
        hostId: currentUser.id,
        hostName: currentUser.displayName,
        title: newTitle.trim(),
        description: newDescription.trim(),
        category: newCategory,
        topicTag: newTopicTag,
        mode: newMode,
        durationMinutes: newDuration
      });
      setShowCreateRoom(false);
      setNewTitle('');
      setNewDescription('');
      setActiveRoom(created);
      await loadAllRooms();
    } catch (err: unknown) {
      setCreateError(err instanceof Error ? err.message : 'Unable to create room');
    } finally {
      setCreating(false);
    }
  };

  const handleSendMessage = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!activeRoom || !messageText.trim() || sendingMessage) return;
    const text = messageText.trim();
    setMessageText('');
    setSendingMessage(true);

    try {
      await postRoomMessage({
        roomId: activeRoom.id,
        senderId: currentUser.id,
        senderName: currentUser.displayName,
        content: text,
        isAnonymous: isAnonymousMessage
      });
    } catch {
      // Fallback
    } finally {
      setSendingMessage(false);
    }
  };

  return (
    <div style={{ maxWidth: '960px', margin: '0 auto', padding: '24px 16px', display: 'flex', flexDirection: 'column', gap: '24px' }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '14px' }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <Radio size={28} color="var(--vybe-magenta)" />
            <h2 className="vybe-brand-font" style={{ fontSize: '1.6rem', fontWeight: 800, margin: 0 }}>
              Unload 24/7 Live Rooms
            </h2>
          </div>
          <p style={{ margin: '4px 0 0', color: 'var(--text-secondary)', fontSize: '0.9rem' }}>
            Persistent listening rooms for open conversations, quiet reflection, and community healing.
          </p>
        </div>

        <button onClick={() => setShowCreateRoom(true)} className="vybe-btn vybe-btn-primary">
          <Plus size={16} />
          <span>Host a Room</span>
        </button>
      </div>

      {/* Join Room by Code Quick Card */}
      <div className="vybe-card" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '14px', background: 'var(--bg-elevated)' }}>
        <div>
          <h4 style={{ margin: 0, fontSize: '0.95rem', fontWeight: 700, color: 'var(--text-primary)' }}>
            Have an invite code?
          </h4>
          <p style={{ margin: '2px 0 0', fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
            Enter a room code (e.g. VYB-LOFI-2401) to join directly.
          </p>
        </div>

        <form onSubmit={handleJoinByCode} style={{ display: 'flex', gap: '8px', minWidth: '280px' }}>
          <input
            type="text"
            placeholder="VYB-XXXX-XXXX"
            value={joinCodeInput}
            onChange={(e) => setJoinCodeInput(e.target.value.toUpperCase())}
            style={{
              flex: 1,
              background: 'var(--bg-surface)',
              border: '1px solid var(--border-subtle)',
              borderRadius: 'var(--radius-pill)',
              padding: '8px 16px',
              color: 'var(--text-primary)',
              fontSize: '0.85rem',
              letterSpacing: '0.05em',
              fontWeight: 600,
              outline: 'none'
            }}
          />
          <button type="submit" disabled={joining || !joinCodeInput.trim()} className="vybe-btn vybe-btn-primary" style={{ padding: '8px 16px' }}>
            {joining ? 'Joining...' : 'Join'}
          </button>
        </form>
        {joinError && <p style={{ width: '100%', margin: '4px 0 0', color: 'var(--vybe-danger)', fontSize: '0.8rem' }}>{joinError}</p>}
      </div>

      {/* If inside an Active Room */}
      {activeRoom ? (
        <div className="vybe-card" style={{ display: 'flex', flexDirection: 'column', height: '620px', padding: 0, overflow: 'hidden' }}>
          {/* Room Top Bar */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '16px 20px', borderBottom: '1px solid var(--border-subtle)', background: 'var(--bg-glass)' }}>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <span style={{ fontSize: '0.75rem', background: 'rgba(255, 46, 147, 0.2)', color: 'var(--vybe-magenta)', padding: '2px 8px', borderRadius: 'var(--radius-pill)', fontWeight: 700 }}>
                  {activeRoom.roomCode}
                </span>
                <h3 style={{ margin: 0, fontSize: '1.1rem', fontWeight: 800 }}>{activeRoom.title}</h3>
              </div>
              <p style={{ margin: '4px 0 0', fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
                Hosted by {activeRoom.hostName} • {activeRoom.category || activeRoom.topicTag || 'Live Space'} • {activeRoom.activeMembersCount} active
              </p>
            </div>

            <button onClick={() => setActiveRoom(null)} className="vybe-btn" style={{ fontSize: '0.8rem', padding: '6px 14px' }}>
              Leave Space
            </button>
          </div>

          {/* Room Messages Feed */}
          <div style={{ flex: 1, overflowY: 'auto', padding: '16px 20px', display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <div style={{ padding: '12px', background: 'var(--bg-elevated)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)', textAlign: 'center', fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
              <Shield size={16} color="var(--vybe-cyan)" style={{ verticalAlign: 'middle', marginRight: '6px' }} />
              {activeRoom.joinerMessage || 'Welcome to this listening room. Share openly and respectfully.'}
            </div>

            {roomMessages.map((msg) => {
              const isMe = msg.senderId === currentUser.id;
              return (
                <div key={msg.id} style={{ display: 'flex', flexDirection: 'column', alignItems: isMe ? 'flex-end' : 'flex-start' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '2px', fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                    {msg.isAnonymous ? (
                      <span style={{ display: 'flex', alignItems: 'center', gap: '4px', color: 'var(--vybe-cyan)', fontWeight: 600 }}>
                        <EyeOff size={12} />
                        Anonymous Listener
                      </span>
                    ) : (
                      <span style={{ fontWeight: 600, color: 'var(--text-secondary)' }}>{msg.senderName}</span>
                    )}
                    <span>• {formatRelativeTimestamp(msg.createdAtIso)}</span>
                  </div>
                  <div
                    style={{
                      maxWidth: '75%',
                      padding: '10px 16px',
                      borderRadius: 'var(--radius-md)',
                      background: isMe ? 'var(--vybe-gradient)' : 'var(--bg-elevated)',
                      color: isMe ? '#fff' : 'var(--text-primary)',
                      fontSize: '0.9rem',
                      lineHeight: '1.4'
                    }}
                  >
                    {msg.content}
                  </div>
                </div>
              );
            })}
            <div ref={messagesEndRef} />
          </div>

          {/* Room Input Form */}
          <form onSubmit={handleSendMessage} style={{ display: 'flex', alignItems: 'center', gap: '10px', padding: '12px 18px', borderTop: '1px solid var(--border-subtle)', background: 'var(--bg-elevated)' }}>
            <button
              type="button"
              onClick={() => setIsAnonymousMessage(!isAnonymousMessage)}
              style={{
                background: isAnonymousMessage ? 'rgba(0, 229, 255, 0.2)' : 'transparent',
                border: isAnonymousMessage ? '1px solid var(--vybe-cyan)' : '1px solid var(--border-subtle)',
                color: isAnonymousMessage ? 'var(--vybe-cyan)' : 'var(--text-secondary)',
                borderRadius: 'var(--radius-pill)',
                padding: '6px 12px',
                fontSize: '0.75rem',
                fontWeight: 600,
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: '4px'
              }}
            >
              <EyeOff size={14} />
              <span>{isAnonymousMessage ? 'Anonymous On' : 'Post Anonymously'}</span>
            </button>

            <input
              type="text"
              placeholder="Share your thoughts with the room..."
              value={messageText}
              onChange={(e) => setMessageText(e.target.value)}
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

            <button type="submit" disabled={sendingMessage || !messageText.trim()} className="vybe-btn vybe-btn-primary" style={{ padding: '10px 18px' }}>
              <Send size={16} />
            </button>
          </form>
        </div>
      ) : (
        /* Active Rooms Grid */
        <div>
          <h3 style={{ fontSize: '1.2rem', fontWeight: 800, marginBottom: '14px' }}>Active Community Rooms</h3>

          {loading ? (
            <div style={{ padding: '40px 0', textAlign: 'center', color: 'var(--text-secondary)' }}>
              Loading rooms...
            </div>
          ) : rooms.length === 0 ? (
            <div style={{ padding: '40px 20px', textAlign: 'center', color: 'var(--text-secondary)', background: 'var(--bg-surface)', borderRadius: 'var(--radius-md)' }}>
              <Radio size={36} color="var(--vybe-violet)" style={{ marginBottom: '10px' }} />
              <h4 style={{ color: 'var(--text-primary)', marginBottom: '6px' }}>No Active Rooms Currently</h4>
              <p style={{ fontSize: '0.9rem', marginBottom: '16px' }}>Host a new room to start a community space.</p>
              <button onClick={() => setShowCreateRoom(true)} className="vybe-btn vybe-btn-primary">
                Host a Room
              </button>
            </div>
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: '16px' }}>
              {rooms.map((room) => (
                <div
                  key={room.id}
                  className="vybe-card"
                  style={{
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'space-between',
                    gap: '14px',
                    transition: 'border-color 0.2s ease',
                    cursor: 'pointer'
                  }}
                  onClick={() => setActiveRoom(room)}
                >
                  <div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                      <span style={{ fontSize: '0.72rem', background: 'rgba(124, 77, 255, 0.16)', color: 'var(--vybe-violet)', padding: '2px 8px', borderRadius: 'var(--radius-pill)', fontWeight: 700 }}>
                        {room.roomCode}
                      </span>
                      <span style={{ fontSize: '0.75rem', color: 'var(--vybe-green)', fontWeight: 600 }}>● Live Now</span>
                    </div>

                    <h4 style={{ margin: 0, fontSize: '1.05rem', fontWeight: 700, color: 'var(--text-primary)', lineHeight: '1.3' }}>
                      {room.title}
                    </h4>

                    {room.description && (
                      <p style={{ margin: '6px 0 0', fontSize: '0.85rem', color: 'var(--text-secondary)', lineHeight: '1.4' }}>
                        {room.description}
                      </p>
                    )}
                  </div>

                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingTop: '12px', borderTop: '1px solid var(--border-subtle)', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                      <Users size={14} />
                      <span>{room.activeMembersCount} listening</span>
                    </div>
                    <button className="vybe-btn vybe-btn-violet" style={{ padding: '6px 14px', fontSize: '0.8rem' }}>
                      Join Space
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Host a Room Modal */}
      {showCreateRoom && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0,0,0,0.75)',
            zIndex: 90,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '16px'
          }}
          onClick={() => setShowCreateRoom(false)}
        >
          <div
            style={{
              width: '100%',
              maxWidth: '480px',
              background: 'var(--bg-surface)',
              borderRadius: 'var(--radius-lg)',
              border: '1px solid var(--border-subtle)',
              padding: '24px',
              display: 'flex',
              flexDirection: 'column',
              gap: '16px',
              maxHeight: '90vh',
              overflowY: 'auto'
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h3 style={{ margin: 0, fontSize: '1.2rem', fontWeight: 800 }}>Host an Unload Space</h3>
              <button onClick={() => setShowCreateRoom(false)} style={{ background: 'none', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer' }}>
                <X size={20} />
              </button>
            </div>

            {createError && (
              <div style={{ padding: '10px 14px', background: 'rgba(255, 77, 109, 0.15)', border: '1px solid var(--vybe-danger)', borderRadius: 'var(--radius-sm)', color: 'var(--vybe-danger)', fontSize: '0.85rem' }}>
                {createError}
              </div>
            )}

            <form onSubmit={handleCreateRoom} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
              <div>
                <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '4px' }}>
                  Room Title
                </label>
                <input
                  type="text"
                  placeholder="e.g. Late Night Overthinking & Reset"
                  value={newTitle}
                  onChange={(e) => setNewTitle(e.target.value)}
                  required
                  style={{
                    width: '100%',
                    background: 'var(--bg-elevated)',
                    border: '1px solid var(--border-subtle)',
                    borderRadius: 'var(--radius-sm)',
                    padding: '10px 14px',
                    color: 'var(--text-primary)',
                    fontSize: '0.9rem',
                    outline: 'none'
                  }}
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '4px' }}>
                  Description / Welcome Prompt
                </label>
                <textarea
                  rows={2}
                  placeholder="A quiet listening space to share what is on your mind."
                  value={newDescription}
                  onChange={(e) => setNewDescription(e.target.value)}
                  style={{
                    width: '100%',
                    background: 'var(--bg-elevated)',
                    border: '1px solid var(--border-subtle)',
                    borderRadius: 'var(--radius-sm)',
                    padding: '10px 14px',
                    color: 'var(--text-primary)',
                    fontSize: '0.9rem',
                    resize: 'none',
                    outline: 'none'
                  }}
                />
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '4px' }}>
                    Topic Tag
                  </label>
                  <input
                    type="text"
                    value={newTopicTag}
                    onChange={(e) => setNewTopicTag(e.target.value)}
                    style={{
                      width: '100%',
                      background: 'var(--bg-elevated)',
                      border: '1px solid var(--border-subtle)',
                      borderRadius: 'var(--radius-sm)',
                      padding: '8px 12px',
                      color: 'var(--text-primary)',
                      fontSize: '0.85rem',
                      outline: 'none'
                    }}
                  />
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '4px' }}>
                    Duration (Minutes)
                  </label>
                  <select
                    value={newDuration}
                    onChange={(e) => setNewDuration(Number(e.target.value))}
                    style={{
                      width: '100%',
                      background: 'var(--bg-elevated)',
                      border: '1px solid var(--border-subtle)',
                      borderRadius: 'var(--radius-sm)',
                      padding: '8px 12px',
                      color: 'var(--text-primary)',
                      fontSize: '0.85rem',
                      outline: 'none'
                    }}
                  >
                    <option value={30}>30 minutes</option>
                    <option value={45}>45 minutes</option>
                    <option value={60}>60 minutes</option>
                    <option value={90}>90 minutes</option>
                  </select>
                </div>
              </div>

              <button type="submit" disabled={creating || !newTitle.trim()} className="vybe-btn vybe-btn-primary" style={{ padding: '12px', marginTop: '8px' }}>
                {creating ? 'Creating Space...' : 'Launch Live Room'}
              </button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
