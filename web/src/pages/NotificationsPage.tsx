import React, { useState, useEffect } from 'react';
import {
  Bell,
  Heart,
  MessageCircle,
  UserPlus,
  Check,
  X,
  Trash2,
  CheckCheck,
  ShieldCheck,
  Sparkles
} from 'lucide-react';
import { VybeAvatar, VerifiedBadge, VybeResonanceIcon } from '../components/VybeCoreComponents';
import { fetchNotifications } from '../lib/vybeApi';
import { formatRelativeTimestamp } from '../lib/utils';
import { supabase } from '../lib/supabase';
import type { VybeNotificationItem, VybeUser } from '../types/vybe';

export const NotificationsPage: React.FC<{
  currentUser: VybeUser;
  onOpenUserProfile?: (user: VybeUser) => void;
  onOpenInbox?: () => void;
}> = ({ currentUser, onOpenUserProfile, onOpenInbox }) => {
  const [notifications, setNotifications] = useState<VybeNotificationItem[]>([]);
  const [loading, setLoading] = useState(true);

  const loadNotifs = async () => {
    try {
      const list = await fetchNotifications(currentUser.id);
      setNotifications(list);
    } catch {
      // Fallback
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadNotifs();
  }, [currentUser.id]);

  const handleMarkAllRead = async () => {
    setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })));
    try {
      await supabase
        .from('notifications')
        .update({ is_read: true })
        .eq('recipient_id', currentUser.id);
    } catch {
      // Fallback
    }
  };

  const handleDeleteNotification = async (id: string) => {
    setNotifications((prev) => prev.filter((n) => n.id !== id));
    try {
      await supabase.from('notifications').delete().eq('id', id);
    } catch {
      // Fallback
    }
  };

  const handleApproveFollow = async (notif: VybeNotificationItem) => {
    if (!notif.actor) return;
    try {
      await supabase
        .from('follows')
        .update({ status: 'ACCEPTED' })
        .eq('follower_id', notif.actor.id)
        .eq('followed_id', currentUser.id);

      setNotifications((prev) => prev.filter((n) => n.id !== notif.id));
    } catch {
      // Fallback
    }
  };

  const handleDeclineFollow = async (notif: VybeNotificationItem) => {
    if (!notif.actor) return;
    try {
      await supabase
        .from('follows')
        .delete()
        .eq('follower_id', notif.actor.id)
        .eq('followed_id', currentUser.id);

      setNotifications((prev) => prev.filter((n) => n.id !== notif.id));
    } catch {
      // Fallback
    }
  };

  const unreadCount = notifications.filter((n) => !n.isRead).length;

  return (
    <div style={{ maxWidth: '680px', margin: '0 auto', padding: '24px 16px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <Bell size={26} color="var(--vybe-magenta)" />
          <h2 className="vybe-brand-font" style={{ fontSize: '1.5rem', fontWeight: 800, margin: 0 }}>
            Notifications
          </h2>
          {unreadCount > 0 && (
            <span
              style={{
                background: 'var(--vybe-magenta)',
                color: '#fff',
                fontSize: '0.75rem',
                fontWeight: 700,
                padding: '2px 8px',
                borderRadius: 'var(--radius-pill)'
              }}
            >
              {unreadCount} new
            </span>
          )}
        </div>

        {unreadCount > 0 && (
          <button onClick={handleMarkAllRead} className="vybe-btn" style={{ padding: '6px 12px', fontSize: '0.8rem' }}>
            <CheckCheck size={16} />
            <span>Mark all read</span>
          </button>
        )}
      </div>

      {/* Notifications List */}
      {loading ? (
        <div style={{ padding: '40px 0', textAlign: 'center', color: 'var(--text-secondary)' }}>
          Loading notifications...
        </div>
      ) : notifications.length === 0 ? (
        <div style={{ padding: '60px 20px', textAlign: 'center', color: 'var(--text-muted)', background: 'var(--bg-surface)', borderRadius: 'var(--radius-md)' }}>
          <Sparkles size={36} color="var(--vybe-cyan)" style={{ marginBottom: '10px' }} />
          <h4 style={{ color: 'var(--text-primary)', marginBottom: '4px' }}>All Caught Up</h4>
          <p style={{ fontSize: '0.9rem' }}>You have no new notifications.</p>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {notifications.map((notif) => {
            const isUnread = !notif.isRead;
            const isFollowReq = notif.type === 'FOLLOW_REQUEST';

            return (
              <div
                key={notif.id}
                className="vybe-card"
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: '14px',
                  padding: '14px 18px',
                  background: isUnread ? 'var(--bg-elevated)' : 'var(--bg-surface)',
                  borderLeft: isUnread ? '3px solid var(--vybe-magenta)' : '1px solid var(--border-subtle)',
                  transition: 'background 0.15s ease'
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '14px', flex: 1, minWidth: 0 }}>
                  {notif.actor ? (
                    <div onClick={() => onOpenUserProfile?.(notif.actor!)} style={{ cursor: 'pointer' }}>
                      <VybeAvatar user={notif.actor} size={42} />
                    </div>
                  ) : (
                    <div style={{ width: '42px', height: '42px', borderRadius: '50%', background: 'var(--bg-elevated)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                      <Bell size={20} color="var(--vybe-cyan)" />
                    </div>
                  )}

                  <div style={{ flex: 1, minWidth: 0 }}>
                    <p style={{ margin: 0, fontSize: '0.9rem', color: 'var(--text-primary)', lineHeight: '1.4' }}>
                      {notif.actor && (
                        <strong
                          onClick={() => onOpenUserProfile?.(notif.actor!)}
                          style={{ cursor: 'pointer', marginRight: '4px' }}
                        >
                          {notif.actor.displayName}
                        </strong>
                      )}
                      <span>{notif.message}</span>
                    </p>
                    <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                      {formatRelativeTimestamp(notif.createdAtIso)}
                    </span>
                  </div>
                </div>

                {/* Follow request inline approve/decline */}
                {isFollowReq ? (
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <button
                      onClick={() => handleApproveFollow(notif)}
                      className="vybe-btn vybe-btn-primary"
                      style={{ padding: '6px 12px', fontSize: '0.78rem' }}
                    >
                      <Check size={14} />
                      <span>Accept</span>
                    </button>
                    <button
                      onClick={() => handleDeclineFollow(notif)}
                      className="vybe-btn"
                      style={{ padding: '6px 10px', fontSize: '0.78rem' }}
                    >
                      <X size={14} />
                    </button>
                  </div>
                ) : (
                  <button
                    onClick={() => handleDeleteNotification(notif.id)}
                    style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', padding: '6px' }}
                    title="Delete"
                  >
                    <Trash2 size={16} />
                  </button>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
