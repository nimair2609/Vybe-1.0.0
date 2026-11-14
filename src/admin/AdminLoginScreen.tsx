import React, { useState } from 'react';
import {
  AlertTriangle, Lock, RefreshCw, ArrowUpRight, Sun, Moon, ShieldCheck
} from 'lucide-react';
import { AdminThemeMode } from './types';
import { VybeButton, VybeInput } from '../components/VybeDesignSystem';

interface AdminLoginScreenProps {
  initialEmail: string;
  themeMode: AdminThemeMode;
  onToggleTheme: () => void;
  onSignIn: (email: string, password: string) => Promise<{ success: boolean; error?: string }>;
}

export const AdminLoginScreen: React.FC<AdminLoginScreenProps> = ({
  initialEmail,
  themeMode,
  onToggleTheme,
  onSignIn,
}) => {
  const [email, setEmail] = useState(initialEmail);
  const [password, setPassword] = useState('');
  const [authError, setAuthError] = useState<string | null>(null);
  const [isLoggingIn, setIsLoggingIn] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setAuthError(null);

    const trimmedEmail = email.trim().toLowerCase();
    const trimmedPassword = password.trim();

    if (!trimmedEmail) {
      setAuthError('Please enter a valid administrator email.');
      return;
    }
    if (!trimmedPassword) {
      setAuthError('Please enter your administrator password.');
      return;
    }

    setIsLoggingIn(true);
    try {
      const res = await onSignIn(trimmedEmail, trimmedPassword);
      if (!res.success) {
        setAuthError(res.error || 'Invalid administrator credentials or missing admin_users role.');
      }
    } catch (err: any) {
      setAuthError(err?.message || 'Unable to verify credentials with Supabase Authentication.');
    } finally {
      setIsLoggingIn(false);
    }
  };

  return (
    <div className="vybe-app-shell min-h-screen flex flex-col items-center justify-center p-4 relative">
      <div className="absolute top-4 right-4 flex items-center gap-2">
        <button
          type="button"
          onClick={onToggleTheme}
          aria-label="Toggle Admin Theme"
          className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-xs font-semibold text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)] transition-colors"
        >
          {themeMode === 'dark' ? <Sun className="w-4 h-4 text-amber-400" /> : <Moon className="w-4 h-4" />}
          <span>{themeMode === 'dark' ? 'Light' : 'Dark'}</span>
        </button>
      </div>

      <div className="w-full max-w-md bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-3xl p-7 sm:p-8 shadow-2xl relative overflow-hidden">
        <div className="absolute top-0 inset-x-0 h-1 bg-gradient-to-r from-[var(--vybe-accent)] via-[var(--vybe-accent-secondary)] to-[var(--vybe-accent)]" />

        <div className="flex items-center gap-3.5 mb-5">
          <img
            src="/brand/vybe_app_icon_clean.png"
            alt="VYBE Logo"
            className="w-12 h-12 rounded-2xl object-cover border border-[var(--vybe-border)] shadow-sm shrink-0"
          />
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-lg font-extrabold brand-font text-[var(--vybe-text)] tracking-tight">
                VYBE Command Center
              </h1>
            </div>
            <span className="inline-flex items-center gap-1 text-[10px] font-semibold uppercase tracking-wider px-2 py-0.5 mt-1 rounded-full bg-[var(--vybe-accent-soft)] text-[var(--vybe-accent)] border border-[var(--vybe-border)]">
              <ShieldCheck className="w-3 h-3" />
              Supabase Admin Auth
            </span>
          </div>
        </div>

        <p className="text-xs text-[var(--vybe-text-secondary)] mb-6 leading-relaxed">
          Sign in with an authorized administrator account listed in <code className="text-[var(--vybe-text)] font-mono">admin_users</code> to manage verification queues, moderation, broadcasts, and egress diagnostics.
        </p>

        {authError && (
          <div className="mb-4 p-3 rounded-xl bg-red-500/10 border border-red-500/30 text-red-500 text-xs flex items-start gap-2">
            <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
            <span>{authError}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <VybeInput
            id="admin-email"
            type="email"
            label="Administrator Email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="admin@domain.com"
            autoComplete="email"
            required
          />

          <VybeInput
            id="admin-password"
            type="password"
            label="Password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="Enter password"
            autoComplete="current-password"
            required
          />

          <VybeButton
            type="submit"
            variant="primary"
            size="lg"
            loading={isLoggingIn}
            icon={isLoggingIn ? RefreshCw : Lock}
            className="w-full mt-2"
          >
            {isLoggingIn ? 'Verifying Session...' : 'Sign In to Command Center'}
          </VybeButton>
        </form>

        <div className="mt-6 pt-5 border-t border-[var(--vybe-border)] flex items-center justify-between text-[11px] text-[var(--vybe-text-muted)]">
          <span>Supabase RLS Enforced</span>
          <a
            href="/"
            className="text-[var(--vybe-accent)] font-semibold hover:underline inline-flex items-center gap-1"
          >
            <span>Back to VYBE Web</span>
            <ArrowUpRight className="w-3.5 h-3.5" />
          </a>
        </div>
      </div>
    </div>
  );
};
