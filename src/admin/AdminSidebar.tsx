import React, { useState } from 'react';
import {
  Activity, Users, Award, FileText, AlertTriangle, Radio,
  Zap, ScrollText, Settings, ChevronDown, ChevronRight,
  LogOut, ExternalLink, X
} from 'lucide-react';
import { AdminSectionId } from './types';

interface NavGroup {
  id: string;
  label: string;
  items: {
    id: AdminSectionId;
    label: string;
    icon: React.ComponentType<{ className?: string }>;
    badge?: number;
    badgeTone?: 'accent' | 'warning';
  }[];
}

interface AdminSidebarProps {
  activeSection: AdminSectionId;
  onSelectSection: (section: AdminSectionId) => void;
  collapsed: boolean;
  mobileOpen: boolean;
  onCloseMobile: () => void;
  counts: {
    users: number;
    verifications: number;
    posts: number;
    reports: number;
  };
  adminEmail: string;
  onLogout: () => void;
}

export const AdminSidebar: React.FC<AdminSidebarProps> = ({
  activeSection,
  onSelectSection,
  collapsed,
  mobileOpen,
  onCloseMobile,
  counts,
  adminEmail,
  onLogout,
}) => {
  const [collapsedGroups, setCollapsedGroups] = useState<Record<string, boolean>>({});

  const toggleGroup = (groupId: string) => {
    setCollapsedGroups(prev => ({ ...prev, [groupId]: !prev[groupId] }));
  };

  const navGroups: NavGroup[] = [
    {
      id: 'overview',
      label: 'Overview',
      items: [
        { id: 'overview', label: 'Dashboard', icon: Activity },
        { id: 'diagnostics', label: 'Egress & Health', icon: Zap },
      ],
    },
    {
      id: 'users',
      label: 'User Management',
      items: [
        { id: 'users', label: 'Users Directory', icon: Users, badge: counts.users },
        {
          id: 'verifications',
          label: 'Verification Queue',
          icon: Award,
          badge: counts.verifications,
          badgeTone: counts.verifications > 0 ? 'warning' : 'accent',
        },
      ],
    },
    {
      id: 'safety',
      label: 'Content & Safety',
      items: [
        { id: 'content', label: 'Posts & Reels', icon: FileText, badge: counts.posts },
        {
          id: 'reports',
          label: 'Safety Reports',
          icon: AlertTriangle,
          badge: counts.reports,
          badgeTone: counts.reports > 0 ? 'warning' : 'accent',
        },
      ],
    },
    {
      id: 'communication',
      label: 'Communication',
      items: [{ id: 'broadcast', label: 'Push Broadcasts', icon: Radio }],
    },
    {
      id: 'system',
      label: 'System',
      items: [
        { id: 'audit', label: 'Audit Logs', icon: ScrollText },
        { id: 'settings', label: 'Platform Controls', icon: Settings },
      ],
    },
  ];

  const handleSelect = (id: AdminSectionId) => {
    onSelectSection(id);
    onCloseMobile();
  };

  const sidebarContent = (isMobileDrawer: boolean) => {
    const isNarrow = !isMobileDrawer && collapsed;
    return (
      <div className="flex flex-col h-full bg-[var(--vybe-surface)] border-r border-[var(--vybe-border)] text-[var(--vybe-text)]">
        {/* Brand Header */}
        <div
          className={`h-16 px-4 border-b border-[var(--vybe-border)] flex items-center ${
            isNarrow ? 'justify-center' : 'justify-between'
          } shrink-0`}
        >
          <div className="flex items-center gap-2.5 min-w-0">
            <img
              src="/brand/vybe_app_icon_clean.png"
              alt="VYBE Logo"
              className="w-8 h-8 rounded-lg object-cover border border-[var(--vybe-border)] shrink-0"
            />
            {!isNarrow && (
              <div className="min-w-0">
                <span className="block text-sm font-extrabold brand-font tracking-tight text-[var(--vybe-text)] truncate">
                  VYBE Admin
                </span>
                <span className="inline-flex items-center gap-1 text-[10px] font-medium text-emerald-500">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                  Command Center
                </span>
              </div>
            )}
          </div>

          {isMobileDrawer ? (
            <button
              type="button"
              onClick={onCloseMobile}
              aria-label="Close navigation drawer"
              className="p-1.5 rounded-lg text-[var(--vybe-text-secondary)] hover:bg-[var(--vybe-elevated)] hover:text-[var(--vybe-text)]"
            >
              <X className="w-4 h-4" />
            </button>
          ) : !isNarrow ? (
            <a
              href="/"
              title="Open Social Web App"
              aria-label="Open Social Web App"
              className="p-1.5 rounded-lg text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)] hover:bg-[var(--vybe-elevated)] transition-colors"
            >
              <ExternalLink className="w-4 h-4" />
            </a>
          ) : null}
        </div>

        {/* Grouped Navigation */}
        <nav aria-label="Admin sections" className="flex-1 overflow-y-auto p-3 space-y-4 no-scrollbar">
          {navGroups.map(group => {
            const isGroupCollapsed = Boolean(collapsedGroups[group.id]) && !isNarrow;
            return (
              <div key={group.id} className="space-y-1">
                {!isNarrow && (
                  <button
                    type="button"
                    onClick={() => toggleGroup(group.id)}
                    className="w-full flex items-center justify-between px-2.5 py-1 text-[10px] font-bold uppercase tracking-wider text-[var(--vybe-text-muted)] hover:text-[var(--vybe-text-secondary)]"
                  >
                    <span>{group.label}</span>
                    {isGroupCollapsed ? (
                      <ChevronRight className="w-3 h-3" />
                    ) : (
                      <ChevronDown className="w-3 h-3" />
                    )}
                  </button>
                )}

                {!isGroupCollapsed && (
                  <div className="space-y-0.5">
                    {group.items.map(item => {
                      const Icon = item.icon;
                      const isActive = activeSection === item.id;
                      return (
                        <button
                          key={item.id}
                          type="button"
                          onClick={() => handleSelect(item.id)}
                          title={isNarrow ? item.label : undefined}
                          className={`w-full flex items-center ${
                            isNarrow ? 'justify-center px-2' : 'justify-between px-3'
                          } py-2.5 rounded-xl text-xs font-semibold transition-colors ${
                            isActive
                              ? 'bg-[var(--vybe-accent-soft)] text-[var(--vybe-accent)] font-bold border border-[var(--vybe-accent)]/30'
                              : 'text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)] hover:bg-[var(--vybe-elevated)] border border-transparent'
                          }`}
                        >
                          <div className="flex items-center gap-2.5 min-w-0">
                            <Icon className="w-4 h-4 shrink-0" />
                            {!isNarrow && <span className="truncate">{item.label}</span>}
                          </div>

                          {!isNarrow && item.badge !== undefined && (
                            <span
                              className={`text-[10px] px-2 py-0.5 rounded-full font-mono shrink-0 ${
                                item.badgeTone === 'warning'
                                  ? 'bg-amber-500/15 text-amber-500'
                                  : isActive
                                    ? 'bg-[var(--vybe-accent)] text-white keep-white'
                                    : 'bg-[var(--vybe-elevated)] text-[var(--vybe-text-secondary)]'
                              }`}
                            >
                              {item.badge}
                            </span>
                          )}
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>
            );
          })}
        </nav>

        {/* Admin Session Footer */}
        <div className="p-3 border-t border-[var(--vybe-border)] bg-[var(--vybe-surface)] shrink-0">
          <div className={`flex items-center ${isNarrow ? 'justify-center' : 'justify-between gap-2'}`}>
            {!isNarrow && (
              <div className="min-w-0">
                <span className="block text-[10px] font-semibold uppercase tracking-wider text-[var(--vybe-text-muted)]">
                  Signed in
                </span>
                <span className="block text-xs font-mono text-[var(--vybe-text)] truncate">
                  {adminEmail || 'Administrator'}
                </span>
              </div>
            )}
            <button
              type="button"
              onClick={onLogout}
              title="Sign Out"
              aria-label="Sign Out"
              className="p-2 rounded-xl bg-red-500/10 hover:bg-red-500/20 text-red-500 transition-colors shrink-0"
            >
              <LogOut className="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>
    );
  };

  return (
    <>
      {/* Desktop Collapsible Rail */}
      <aside
        className={`hidden lg:block shrink-0 transition-all duration-200 ${
          collapsed ? 'w-[72px]' : 'w-64'
        } sticky top-0 h-screen`}
      >
        {sidebarContent(false)}
      </aside>

      {/* Mobile / Tablet Slide-Over Drawer */}
      {mobileOpen && (
        <div className="fixed inset-0 z-50 lg:hidden flex">
          <div
            className="fixed inset-0 bg-black/60 backdrop-blur-sm"
            onClick={onCloseMobile}
            aria-hidden="true"
          />
          <aside className="relative z-10 w-72 max-w-[85vw] h-full shadow-2xl">
            {sidebarContent(true)}
          </aside>
        </div>
      )}
    </>
  );
};
