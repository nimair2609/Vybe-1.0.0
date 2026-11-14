import React from 'react';
import {
  Menu, PanelLeftClose, PanelLeftOpen, Search, RefreshCw,
  ArrowUpRight, Sun, Moon, AlertCircle, X
} from 'lucide-react';
import { ADMIN_SECTION_META, AdminSectionId, AdminThemeMode } from './types';

interface AdminTopBarProps {
  activeSection: AdminSectionId;
  sidebarCollapsed: boolean;
  onToggleCollapse: () => void;
  onOpenMobileDrawer: () => void;
  searchQuery: string;
  onSearchChange: (q: string) => void;
  isLoading: boolean;
  onSync: () => void;
  themeMode: AdminThemeMode;
  onToggleTheme: () => void;
  pendingCount: number;
  onJumpToPending: () => void;
}

export const AdminTopBar: React.FC<AdminTopBarProps> = ({
  activeSection,
  sidebarCollapsed,
  onToggleCollapse,
  onOpenMobileDrawer,
  searchQuery,
  onSearchChange,
  isLoading,
  onSync,
  themeMode,
  onToggleTheme,
  pendingCount,
  onJumpToPending,
}) => {
  const meta = ADMIN_SECTION_META[activeSection];

  return (
    <header className="sticky top-0 z-30 h-16 border-b border-[var(--vybe-border)] bg-[var(--vybe-nav-bg)] backdrop-blur-xl px-4 sm:px-6 flex items-center justify-between gap-3">
      {/* Left: Drawer Trigger / Collapse + Breadcrumb */}
      <div className="flex items-center gap-3 min-w-0">
        <button
          type="button"
          onClick={onOpenMobileDrawer}
          aria-label="Open navigation menu"
          className="lg:hidden p-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[var(--vybe-text)]"
        >
          <Menu className="w-4 h-4" />
        </button>

        <button
          type="button"
          onClick={onToggleCollapse}
          aria-label={sidebarCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          title={sidebarCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          className="hidden lg:inline-flex p-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)] transition-colors"
        >
          {sidebarCollapsed ? <PanelLeftOpen className="w-4 h-4" /> : <PanelLeftClose className="w-4 h-4" />}
        </button>

        <div className="min-w-0">
          <div className="flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-wider text-[var(--vybe-text-muted)]">
            <span>VYBE Admin</span>
            <span>/</span>
            <span className="truncate">{meta.group}</span>
          </div>
          <h1 className="text-sm sm:text-base font-extrabold brand-font text-[var(--vybe-text)] truncate leading-tight">
            {meta.title}
          </h1>
        </div>
      </div>

      {/* Center: Global Search Input */}
      <div className="hidden md:flex items-center relative flex-1 max-w-sm">
        <Search className="w-3.5 h-3.5 text-[var(--vybe-text-muted)] absolute left-3 pointer-events-none" />
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => onSearchChange(e.target.value)}
          placeholder="Search users, posts, verifications, logs..."
          className="w-full pl-9 pr-8 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs text-[var(--vybe-text)] placeholder-[var(--vybe-text-muted)] focus:outline-none focus:border-[var(--vybe-accent)]"
        />
        {searchQuery && (
          <button
            type="button"
            onClick={() => onSearchChange('')}
            aria-label="Clear global search"
            className="p-1 absolute right-2 text-[var(--vybe-text-muted)] hover:text-[var(--vybe-text)]"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        )}
      </div>

      {/* Right: Pending Queue Alert + Sync + Theme + Social Link */}
      <div className="flex items-center gap-2 shrink-0">
        {pendingCount > 0 && (
          <button
            type="button"
            onClick={onJumpToPending}
            title="Review pending moderation queues"
            className="hidden sm:inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-xl bg-amber-500/15 border border-amber-500/35 text-amber-500 text-xs font-bold hover:bg-amber-500/20 transition-colors"
          >
            <AlertCircle className="w-3.5 h-3.5" />
            <span>{pendingCount} Pending</span>
          </button>
        )}

        <button
          type="button"
          onClick={onSync}
          disabled={isLoading}
          className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] text-xs font-semibold text-[var(--vybe-text)] disabled:opacity-60 transition-colors"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin text-[var(--vybe-accent)]' : ''}`} />
          <span className="hidden sm:inline">Sync</span>
        </button>

        <button
          type="button"
          onClick={onToggleTheme}
          title={themeMode === 'dark' ? 'Switch to Light Mode' : 'Switch to Dark Mode'}
          aria-label="Toggle Admin Theme"
          className="p-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)] transition-colors"
        >
          {themeMode === 'dark' ? <Sun className="w-4 h-4 text-amber-400" /> : <Moon className="w-4 h-4" />}
        </button>

        <a
          href="/"
          className="inline-flex items-center gap-1 px-3 py-2 rounded-xl bg-[var(--vybe-accent)] text-white keep-white font-bold text-xs shadow-sm hover:opacity-95 transition-opacity"
        >
          <span className="hidden sm:inline">Social App</span>
          <ArrowUpRight className="w-3.5 h-3.5" />
        </a>
      </div>
    </header>
  );
};
