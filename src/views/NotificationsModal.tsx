import React from 'react';
import { Bell, X, MessageCircle, Sparkles, CheckCircle2 } from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { VybeEmptyState } from '../components/VybeDesignSystem';

export const NotificationsModal: React.FC = () => {
  const {
    closeModal,
    conversations,
    selectConversation,
    setSelectedTab,
    updateInfo,
    openModal
  } = useVybe();

  const unreadConversations = conversations.filter(c => (c.unreadCount || 0) > 0);
  const hasUpdate = Boolean(updateInfo?.isNewerThanInstalled);

  const hasAnyNotifications = unreadConversations.length > 0 || hasUpdate;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-fade-in"
      onClick={closeModal}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Notifications"
        onClick={(e) => e.stopPropagation()}
        className="relative w-full max-w-md max-h-[85vh] overflow-y-auto bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-3xl shadow-2xl p-6 text-[var(--vybe-text)] no-scrollbar space-y-4"
      >
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-[var(--vybe-border)]">
          <div className="flex items-center gap-2">
            <Bell className="w-5 h-5 text-[var(--vybe-accent)]" />
            <h2 className="text-base font-bold brand-font text-[var(--vybe-text)]">Notifications</h2>
          </div>
          <button
            type="button"
            onClick={closeModal}
            aria-label="Close notifications"
            className="p-1.5 text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)] hover:bg-[var(--vybe-elevated)] rounded-lg"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Real Session Notifications List */}
        {!hasAnyNotifications ? (
          <VybeEmptyState
            icon={CheckCircle2}
            title="You're all caught up"
            subtitle="Unread direct messages and platform release alerts will appear here automatically."
          />
        ) : (
          <div className="space-y-2.5">
            {hasUpdate && updateInfo && (
              <button
                type="button"
                onClick={() => {
                  closeModal();
                  openModal('GITHUB_UPDATES');
                }}
                className="w-full text-left p-3.5 rounded-2xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] flex items-start gap-3 hover:border-[var(--vybe-accent)] transition-colors"
              >
                <div className="p-2.5 rounded-xl bg-[var(--vybe-accent-soft)] text-[var(--vybe-accent)] shrink-0 mt-0.5">
                  <Sparkles className="w-4 h-4" />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between gap-2">
                    <h4 className="text-xs font-bold text-[var(--vybe-text)] truncate">
                      New VYBE Release {updateInfo.tagName}
                    </h4>
                    <span className="text-[10px] text-[var(--vybe-accent)] font-bold shrink-0">Update</span>
                  </div>
                  <p className="text-[11px] text-[var(--vybe-text-secondary)] mt-0.5 line-clamp-2">
                    {updateInfo.name || 'Tap to view release notes and download APK.'}
                  </p>
                </div>
              </button>
            )}

            {unreadConversations.map(conv => (
              <button
                key={`notif_${conv.peerUser.id}`}
                type="button"
                onClick={() => {
                  selectConversation(conv);
                  setSelectedTab('INBOX');
                  closeModal();
                }}
                className="w-full text-left p-3.5 rounded-2xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] flex items-start gap-3 hover:border-[var(--vybe-accent)] transition-colors"
              >
                <div className="p-2.5 rounded-xl bg-sky-500/12 text-sky-500 shrink-0 mt-0.5">
                  <MessageCircle className="w-4 h-4" />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between gap-2">
                    <h4 className="text-xs font-bold text-[var(--vybe-text)] truncate">
                      {conv.unreadCount} unread from {conv.peerUser.displayName}
                    </h4>
                    <span className="text-[10px] text-[var(--vybe-text-muted)] shrink-0">
                      {conv.lastMessageTimestamp}
                    </span>
                  </div>
                  <p className="text-[11px] text-[var(--vybe-text-secondary)] mt-0.5 line-clamp-1">
                    {conv.lastMessage || 'New message received'}
                  </p>
                </div>
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
