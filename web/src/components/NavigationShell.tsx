import React from 'react';
import { NavLink, useNavigate, useLocation } from 'react-router-dom';
import {
  Home,
  Film,
  Compass,
  Radio,
  MessageSquare,
  Bell,
  User,
  Settings,
  Plus,
  LogOut,
  Sparkles,
  TrendingUp,
  Search,
  Users
} from 'lucide-react';
import { VybeAvatar, VerifiedBadge } from './VybeCoreComponents';
import { MOOD_OPTIONS } from '../lib/utils';
import type { UnloadRoom, VybeUser } from '../types/vybe';

export const NavigationShell: React.FC<{
  currentUser: VybeUser;
  unreadMessagesCount: number;
  unreadNotificationsCount: number;
  suggestedUsers: VybeUser[];
  activeRooms: UnloadRoom[];
  onOpenCreatePost: () => void;
  onLogout: () => Promise<void>;
  children: React.ReactNode;
}> = ({
  currentUser,
  unreadMessagesCount,
  unreadNotificationsCount,
  suggestedUsers,
  activeRooms,
  onOpenCreatePost,
  onLogout,
  children
}) => {
  const navigate = useNavigate();
  const location = useLocation();

  const navItems = [
    { to: '/', label: 'Feed', icon: Home, badge: 0 },
    { to: '/reels', label: 'Reels', icon: Film, badge: 0 },
    { to: '/explore', label: 'Explore', icon: Compass, badge: 0 },
    { to: '/rooms', label: 'Live Rooms', icon: Radio, badge: 0 },
    { to: '/messages', label: 'Messages', icon: MessageSquare, badge: unreadMessagesCount },
    { to: '/notifications', label: 'Notifications', icon: Bell, badge: unreadNotificationsCount },
    { to: '/profile', label: 'Profile', icon: User, badge: 0 },
    { to: '/settings', label: 'Settings', icon: Settings, badge: 0 }
  ];

  return (
    <div className="vybe-app-shell">
      {/* 1. Desktop Left Sidebar */}
      <aside className="vybe-sidebar-left">
        <div>
          {/* Brand Header */}
          <div
            onClick={() => navigate('/')}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '12px',
              cursor: 'pointer',
              padding: '6px 8px 24px'
            }}
          >
            <div
              style={{
                width: '38px',
                height: '38px',
                borderRadius: '11px',
                background: 'var(--vybe-gradient)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                boxShadow: '0 4px 14px rgba(124, 77, 255, 0.4)'
              }}
            >
              <Sparkles size={20} color="#fff" />
            </div>
            <div className="vybe-brand-wordmark-desktop">
              <span className="vybe-brand-font vybe-gradient-text" style={{ fontSize: '1.45rem', fontWeight: 800 }}>
                VYBE
              </span>
              <span style={{ display: 'block', fontSize: '0.65rem', color: 'var(--text-muted)', letterSpacing: '0.12em', fontWeight: 600 }}>
                BY NR NEXT
              </span>
            </div>
          </div>

          {/* Navigation Items */}
          <nav style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
            {navItems.map((item) => {
              const IconComponent = item.icon;
              const isActive = location.pathname === item.to;

              return (
                <NavLink
                  key={item.to}
                  to={item.to}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'flex-start',
                    gap: '14px',
                    padding: '12px 14px',
                    borderRadius: 'var(--radius-pill)',
                    textDecoration: 'none',
                    color: isActive ? '#ffffff' : 'var(--text-secondary)',
                    background: isActive ? 'var(--bg-elevated)' : 'transparent',
                    fontWeight: isActive ? 700 : 500,
                    fontSize: '0.95rem',
                    position: 'relative',
                    transition: 'all 0.14s ease'
                  }}
                >
                  <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
                    <IconComponent size={22} color={isActive ? 'var(--vybe-violet)' : 'currentColor'} />
                    {item.badge > 0 && (
                      <span
                        style={{
                          position: 'absolute',
                          top: '-4px',
                          right: '-6px',
                          background: 'var(--vybe-magenta)',
                          color: '#fff',
                          fontSize: '0.65rem',
                          fontWeight: 700,
                          borderRadius: 'var(--radius-pill)',
                          padding: '1px 5px',
                          minWidth: '16px',
                          textAlign: 'center'
                        }}
                      >
                        {item.badge}
                      </span>
                    )}
                  </div>
                  <span className="vybe-nav-label">{item.label}</span>
                </NavLink>
              );
            })}
          </nav>

          {/* Create Post Button */}
          <button
            onClick={onOpenCreatePost}
            className="vybe-btn vybe-btn-primary"
            style={{
              width: '100%',
              marginTop: '18px',
              padding: '12px',
              fontSize: '0.95rem',
              boxShadow: '0 4px 16px rgba(124, 77, 255, 0.35)'
            }}
          >
            <Plus size={18} />
            <span className="vybe-nav-label">Create</span>
          </button>
        </div>

        {/* User Card at bottom of Left Sidebar */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '10px 12px',
            background: 'var(--bg-surface)',
            borderRadius: 'var(--radius-md)',
            border: '1px solid var(--border-subtle)',
            marginTop: '16px'
          }}
        >
          <div
            onClick={() => navigate('/profile')}
            style={{ display: 'flex', alignItems: 'center', gap: '10px', cursor: 'pointer', minWidth: 0, flex: 1 }}
          >
            <VybeAvatar user={currentUser} size={36} />
            <div className="vybe-nav-label" style={{ minWidth: 0, flex: 1 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                <span style={{ fontSize: '0.88rem', fontWeight: 700, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {currentUser.displayName}
                </span>
                <VerifiedBadge user={currentUser} />
              </div>
              <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>@{currentUser.username}</span>
            </div>
          </div>

          <button
            onClick={onLogout}
            style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', padding: '6px' }}
            title="Log Out"
          >
            <LogOut size={16} />
          </button>
        </div>
      </aside>

      {/* 2. Main Content Center Column */}
      <main className="vybe-main-column">
        {/* Mobile Top Bar */}
        <header className="vybe-mobile-topbar">
          <div onClick={() => navigate('/')} style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}>
            <div
              style={{
                width: '28px',
                height: '28px',
                borderRadius: '8px',
                background: 'var(--vybe-gradient)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}
            >
              <Sparkles size={16} color="#fff" />
            </div>
            <span className="vybe-brand-font vybe-gradient-text" style={{ fontSize: '1.25rem', fontWeight: 800 }}>
              VYBE
            </span>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <NavLink to="/notifications" style={{ position: 'relative', color: 'var(--text-primary)', padding: '6px' }}>
              <Bell size={22} />
              {unreadNotificationsCount > 0 && (
                <span
                  style={{
                    position: 'absolute',
                    top: '2px',
                    right: '2px',
                    background: 'var(--vybe-magenta)',
                    width: '8px',
                    height: '8px',
                    borderRadius: '50%'
                  }}
                />
              )}
            </NavLink>
            <NavLink to="/settings" style={{ color: 'var(--text-primary)', padding: '6px' }}>
              <Settings size={22} />
            </NavLink>
          </div>
        </header>

        {children}
      </main>

      {/* 3. Desktop Right Sidebar */}
      <aside className="vybe-sidebar-right">
        {/* Quick Search */}
        <div style={{ position: 'relative', width: '100%' }}>
          <Search size={16} color="var(--text-muted)" style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)' }} />
          <input
            type="text"
            placeholder="Search VYBE..."
            onFocus={() => navigate('/explore')}
            style={{
              width: '100%',
              background: 'var(--bg-surface)',
              border: '1px solid var(--border-subtle)',
              borderRadius: 'var(--radius-pill)',
              padding: '8px 14px 8px 36px',
              color: 'var(--text-primary)',
              fontSize: '0.85rem',
              outline: 'none'
            }}
          />
        </div>

        {/* Trending Moods */}
        <div className="vybe-card" style={{ padding: '14px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '10px', fontSize: '0.85rem', fontWeight: 700, color: 'var(--text-secondary)' }}>
            <TrendingUp size={16} color="var(--vybe-cyan)" />
            <span>Trending Moods</span>
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
            {MOOD_OPTIONS.slice(0, 5).map((m) => (
              <span
                key={m.key}
                onClick={() => navigate('/explore')}
                style={{
                  fontSize: '0.75rem',
                  padding: '4px 8px',
                  background: 'var(--bg-elevated)',
                  borderRadius: 'var(--radius-pill)',
                  border: '1px solid var(--border-subtle)',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '4px'
                }}
              >
                <span>{m.emoji}</span>
                <span>{m.label}</span>
              </span>
            ))}
          </div>
        </div>

        {/* Suggested Creators */}
        {suggestedUsers.length > 0 && (
          <div className="vybe-card" style={{ padding: '14px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '0.85rem', fontWeight: 700, color: 'var(--text-secondary)' }}>
              <Users size={16} color="var(--vybe-violet)" />
              <span>Suggested Creators</span>
            </div>
            {suggestedUsers.slice(0, 4).map((u) => (
              <div
                key={u.id}
                onClick={() => navigate(`/profile?user=${u.username}`)}
                style={{ display: 'flex', alignItems: 'center', gap: '10px', cursor: 'pointer', padding: '4px 0' }}
              >
                <VybeAvatar user={u} size={32} />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                    <span style={{ fontSize: '0.82rem', fontWeight: 700, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {u.displayName}
                    </span>
                    <VerifiedBadge user={u} />
                  </div>
                  <span style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>@{u.username}</span>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Active Live Rooms Preview */}
        {activeRooms.length > 0 && (
          <div className="vybe-card" style={{ padding: '14px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '0.85rem', fontWeight: 700, color: 'var(--text-secondary)' }}>
              <Radio size={16} color="var(--vybe-magenta)" />
              <span>Live Listening Rooms</span>
            </div>
            {activeRooms.slice(0, 3).map((r) => (
              <div
                key={r.id}
                onClick={() => navigate('/rooms')}
                style={{ padding: '8px 10px', background: 'var(--bg-elevated)', borderRadius: 'var(--radius-sm)', cursor: 'pointer' }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2px' }}>
                  <span style={{ fontSize: '0.78rem', fontWeight: 700, color: 'var(--text-primary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: '170px' }}>
                    {r.title}
                  </span>
                  <span style={{ fontSize: '0.68rem', color: 'var(--vybe-green)', fontWeight: 600 }}>● Live</span>
                </div>
                <span style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>{r.activeMembersCount} listening • {r.category || r.topicTag || 'Live Room'}</span>
              </div>
            ))}
          </div>
        )}

        {/* Footer */}
        <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', lineHeight: '1.6', marginTop: 'auto', paddingTop: '16px' }}>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
            <NavLink to="/settings" style={{ color: 'var(--text-muted)', textDecoration: 'none' }}>Terms of Service</NavLink>
            <span>•</span>
            <NavLink to="/settings" style={{ color: 'var(--text-muted)', textDecoration: 'none' }}>Privacy Policy</NavLink>
            <span>•</span>
            <span>Age 18+</span>
          </div>
          <p style={{ margin: '6px 0 0' }}>© 2026 VYBE by NR Next</p>
        </div>
      </aside>

      {/* 4. Mobile Bottom Navigation Bar */}
      <nav className="vybe-bottom-nav">
        <NavLink to="/" style={{ color: location.pathname === '/' ? 'var(--vybe-violet)' : 'var(--text-secondary)', display: 'flex', flexDirection: 'column', alignItems: 'center', padding: '6px' }}>
          <Home size={22} />
        </NavLink>
        <NavLink to="/reels" style={{ color: location.pathname === '/reels' ? 'var(--vybe-violet)' : 'var(--text-secondary)', display: 'flex', flexDirection: 'column', alignItems: 'center', padding: '6px' }}>
          <Film size={22} />
        </NavLink>
        <button
          onClick={onOpenCreatePost}
          style={{
            background: 'var(--vybe-gradient)',
            border: 'none',
            borderRadius: '50%',
            width: '40px',
            height: '40px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#fff',
            cursor: 'pointer',
            boxShadow: '0 3px 12px rgba(124, 77, 255, 0.4)'
          }}
        >
          <Plus size={20} />
        </button>
        <NavLink to="/messages" style={{ position: 'relative', color: location.pathname === '/messages' ? 'var(--vybe-violet)' : 'var(--text-secondary)', display: 'flex', flexDirection: 'column', alignItems: 'center', padding: '6px' }}>
          <MessageSquare size={22} />
          {unreadMessagesCount > 0 && (
            <span style={{ position: 'absolute', top: '2px', right: '4px', background: 'var(--vybe-magenta)', width: '8px', height: '8px', borderRadius: '50%' }} />
          )}
        </NavLink>
        <NavLink to="/profile" style={{ color: location.pathname === '/profile' ? 'var(--vybe-violet)' : 'var(--text-secondary)', display: 'flex', flexDirection: 'column', alignItems: 'center', padding: '6px' }}>
          <VybeAvatar user={currentUser} size={24} />
        </NavLink>
      </nav>
    </div>
  );
};
