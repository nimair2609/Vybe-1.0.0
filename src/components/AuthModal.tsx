import React, { useState, useEffect } from 'react';
import {
  X,
  Lock,
  Mail,
  User as UserIcon,
  AtSign,
  Eye,
  EyeOff,
  Loader2,
  AlertCircle,
  CheckCircle2,
  ArrowRight
} from 'lucide-react';
import { useVybe } from '../context/VybeContext';

export const AuthModal: React.FC = () => {
  const {
    showAuthModal,
    authModalPrompt,
    authModalInitialMode,
    closeAuthModal,
    signIn,
    signUp,
    requestPasswordReset
  } = useVybe();

  const [mode, setMode] = useState<'LOGIN' | 'SIGNUP' | 'RESET'>(authModalInitialMode || 'LOGIN');
  const [identifier, setIdentifier] = useState('');
  const [email, setEmail] = useState('');
  const [username, setUsername] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  useEffect(() => {
    if (showAuthModal) {
      setMode(authModalInitialMode || 'LOGIN');
      setErrorMsg(null);
      setSuccessMsg(null);
    }
  }, [showAuthModal, authModalInitialMode]);

  if (!showAuthModal) return null;

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    setSuccessMsg(null);
    setIsSubmitting(true);
    const res = await signIn(identifier, password);
    setIsSubmitting(false);
    if (!res.ok) {
      setErrorMsg(res.error || 'Could not sign in.');
    }
  };

  const handleSignup = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    setSuccessMsg(null);
    setIsSubmitting(true);
    const res = await signUp({ email, password, username, displayName });
    setIsSubmitting(false);
    if (!res.ok) {
      setErrorMsg(res.error || 'Could not create account.');
      return;
    }
    if (res.needsEmailConfirmation) {
      setSuccessMsg('Account created! Check your email to confirm your address, then sign in.');
      setMode('LOGIN');
      setIdentifier(email);
    }
  };

  const handleReset = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    setSuccessMsg(null);
    setIsSubmitting(true);
    const res = await requestPasswordReset(email || identifier);
    setIsSubmitting(false);
    if (!res.ok) {
      setErrorMsg(res.error || 'Failed to send reset instructions.');
      return;
    }
    setSuccessMsg('Password recovery link sent! Check your email inbox.');
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-md animate-fade-in"
      onClick={closeAuthModal}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label="VYBE Account Authentication"
        onClick={(e) => e.stopPropagation()}
        className="relative w-full max-w-md bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-3xl shadow-2xl p-6 text-[var(--vybe-text)] space-y-5"
      >
        <div className="flex items-start justify-between gap-3">
          <div>
            <div className="inline-flex items-center gap-2 px-2.5 py-1 rounded-full bg-[var(--vybe-accent-soft)] text-[var(--vybe-accent)] text-[11px] font-bold mb-2">
              <Lock className="w-3 h-3" />
              <span>VYBE Account</span>
            </div>
            <h2 className="text-xl font-extrabold brand-font text-[var(--vybe-text)]">
              {mode === 'LOGIN'
                ? 'Sign in to VYBE'
                : mode === 'SIGNUP'
                  ? 'Create your VYBE account'
                  : 'Reset password'}
            </h2>
            <p className="text-xs text-[var(--vybe-text-secondary)] mt-1">
              {authModalPrompt ||
                (mode === 'LOGIN'
                  ? 'Use the same email or @username as the VYBE Android app.'
                  : mode === 'SIGNUP'
                    ? 'Join once and sync seamlessly across Web and Android.'
                    : 'Enter your registered email to receive a password reset link.')}
            </p>
          </div>
          <button
            type="button"
            onClick={closeAuthModal}
            aria-label="Close authentication modal"
            className="p-2 rounded-xl text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)] hover:bg-[var(--vybe-elevated)]"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Mode Switcher */}
        {mode !== 'RESET' && (
          <div className="grid grid-cols-2 p-1 rounded-2xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)]">
            <button
              type="button"
              onClick={() => {
                setMode('LOGIN');
                setErrorMsg(null);
              }}
              className={`py-2 rounded-xl text-xs font-bold transition-colors ${
                mode === 'LOGIN'
                  ? 'bg-[var(--vybe-accent)] text-white keep-white shadow-sm'
                  : 'text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)]'
              }`}
            >
              Sign In
            </button>
            <button
              type="button"
              onClick={() => {
                setMode('SIGNUP');
                setErrorMsg(null);
              }}
              className={`py-2 rounded-xl text-xs font-bold transition-colors ${
                mode === 'SIGNUP'
                  ? 'bg-[var(--vybe-accent)] text-white keep-white shadow-sm'
                  : 'text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)]'
              }`}
            >
              Create Account
            </button>
          </div>
        )}

        {errorMsg && (
          <div className="p-3 rounded-2xl bg-red-500/10 border border-red-500/30 text-red-500 text-xs flex items-start gap-2">
            <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
            <span>{errorMsg}</span>
          </div>
        )}

        {successMsg && (
          <div className="p-3 rounded-2xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-500 text-xs flex items-start gap-2">
            <CheckCircle2 className="w-4 h-4 shrink-0 mt-0.5" />
            <span>{successMsg}</span>
          </div>
        )}

        {mode === 'LOGIN' && (
          <form onSubmit={handleLogin} className="space-y-3.5">
            <div>
              <label className="text-[11px] font-semibold text-[var(--vybe-text-secondary)] block mb-1">
                Email or @username
              </label>
              <div className="relative">
                <AtSign className="w-4 h-4 text-[var(--vybe-text-muted)] absolute left-3.5 top-1/2 -translate-y-1/2" />
                <input
                  type="text"
                  value={identifier}
                  onChange={(e) => setIdentifier(e.target.value)}
                  placeholder="name@example.com or @username"
                  autoComplete="username"
                  required
                  className="w-full pl-10 pr-3.5 py-2.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs text-[var(--vybe-text)] placeholder-[var(--vybe-text-muted)] focus:outline-none focus:border-[var(--vybe-accent)]"
                />
              </div>
            </div>

            <div>
              <div className="flex items-center justify-between mb-1">
                <label className="text-[11px] font-semibold text-[var(--vybe-text-secondary)]">
                  Password
                </label>
                <button
                  type="button"
                  onClick={() => {
                    setMode('RESET');
                    setErrorMsg(null);
                  }}
                  className="text-[11px] font-semibold text-[var(--vybe-accent)] hover:underline"
                >
                  Forgot password?
                </button>
              </div>
              <div className="relative">
                <Lock className="w-4 h-4 text-[var(--vybe-text-muted)] absolute left-3.5 top-1/2 -translate-y-1/2" />
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Enter your password"
                  autoComplete="current-password"
                  required
                  className="w-full pl-10 pr-10 py-2.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs text-[var(--vybe-text)] placeholder-[var(--vybe-text-muted)] focus:outline-none focus:border-[var(--vybe-accent)]"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(p => !p)}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-[var(--vybe-text-muted)] hover:text-[var(--vybe-text)]"
                >
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              disabled={isSubmitting || !identifier.trim() || !password}
              className="w-full py-3 rounded-xl bg-[var(--vybe-accent)] disabled:opacity-45 text-white keep-white text-xs font-bold flex items-center justify-center gap-2 hover:opacity-95 transition-opacity"
            >
              {isSubmitting ? <Loader2 className="w-4 h-4 animate-spin" /> : <ArrowRight className="w-4 h-4" />}
              <span>{isSubmitting ? 'Signing in...' : 'Sign In'}</span>
            </button>
          </form>
        )}

        {mode === 'SIGNUP' && (
          <form onSubmit={handleSignup} className="space-y-3">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="text-[11px] font-semibold text-[var(--vybe-text-secondary)] block mb-1">
                  Display Name
                </label>
                <div className="relative">
                  <UserIcon className="w-4 h-4 text-[var(--vybe-text-muted)] absolute left-3 top-1/2 -translate-y-1/2" />
                  <input
                    type="text"
                    value={displayName}
                    onChange={(e) => setDisplayName(e.target.value)}
                    placeholder="Your name"
                    required
                    className="w-full pl-9 pr-3 py-2.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs text-[var(--vybe-text)] placeholder-[var(--vybe-text-muted)] focus:outline-none focus:border-[var(--vybe-accent)]"
                  />
                </div>
              </div>
              <div>
                <label className="text-[11px] font-semibold text-[var(--vybe-text-secondary)] block mb-1">
                  Username
                </label>
                <div className="relative">
                  <AtSign className="w-4 h-4 text-[var(--vybe-text-muted)] absolute left-3 top-1/2 -translate-y-1/2" />
                  <input
                    type="text"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    placeholder="handle"
                    required
                    className="w-full pl-9 pr-3 py-2.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs text-[var(--vybe-text)] placeholder-[var(--vybe-text-muted)] focus:outline-none focus:border-[var(--vybe-accent)]"
                  />
                </div>
              </div>
            </div>

            <div>
              <label className="text-[11px] font-semibold text-[var(--vybe-text-secondary)] block mb-1">
                Email Address
              </label>
              <div className="relative">
                <Mail className="w-4 h-4 text-[var(--vybe-text-muted)] absolute left-3.5 top-1/2 -translate-y-1/2" />
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="you@example.com"
                  autoComplete="email"
                  required
                  className="w-full pl-10 pr-3.5 py-2.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs text-[var(--vybe-text)] placeholder-[var(--vybe-text-muted)] focus:outline-none focus:border-[var(--vybe-accent)]"
                />
              </div>
            </div>

            <div>
              <label className="text-[11px] font-semibold text-[var(--vybe-text-secondary)] block mb-1">
                Password (min 6 characters)
              </label>
              <div className="relative">
                <Lock className="w-4 h-4 text-[var(--vybe-text-muted)] absolute left-3.5 top-1/2 -translate-y-1/2" />
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Create a password"
                  autoComplete="new-password"
                  minLength={6}
                  required
                  className="w-full pl-10 pr-10 py-2.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs text-[var(--vybe-text)] placeholder-[var(--vybe-text-muted)] focus:outline-none focus:border-[var(--vybe-accent)]"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(p => !p)}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-[var(--vybe-text-muted)] hover:text-[var(--vybe-text)]"
                >
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              disabled={isSubmitting}
              className="w-full py-3 rounded-xl bg-[var(--vybe-accent)] disabled:opacity-45 text-white keep-white text-xs font-bold flex items-center justify-center gap-2 hover:opacity-95 transition-opacity"
            >
              {isSubmitting ? <Loader2 className="w-4 h-4 animate-spin" /> : <ArrowRight className="w-4 h-4" />}
              <span>{isSubmitting ? 'Creating account...' : 'Create Account'}</span>
            </button>
          </form>
        )}

        {mode === 'RESET' && (
          <form onSubmit={handleReset} className="space-y-3.5">
            <div>
              <label className="text-[11px] font-semibold text-[var(--vybe-text-secondary)] block mb-1">
                Registered Email
              </label>
              <div className="relative">
                <Mail className="w-4 h-4 text-[var(--vybe-text-muted)] absolute left-3.5 top-1/2 -translate-y-1/2" />
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="you@example.com"
                  required
                  className="w-full pl-10 pr-3.5 py-2.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs text-[var(--vybe-text)] placeholder-[var(--vybe-text-muted)] focus:outline-none focus:border-[var(--vybe-accent)]"
                />
              </div>
            </div>
            <button
              type="submit"
              disabled={isSubmitting || !email.trim()}
              className="w-full py-3 rounded-xl bg-[var(--vybe-accent)] disabled:opacity-45 text-white keep-white text-xs font-bold flex items-center justify-center gap-2"
            >
              {isSubmitting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Mail className="w-4 h-4" />}
              <span>{isSubmitting ? 'Sending...' : 'Send Recovery Link'}</span>
            </button>
            <button
              type="button"
              onClick={() => setMode('LOGIN')}
              className="w-full py-2 text-xs font-semibold text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)]"
            >
              Back to Sign In
            </button>
          </form>
        )}
      </div>
    </div>
  );
};
