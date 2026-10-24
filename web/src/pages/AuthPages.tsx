import React, { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { ShieldCheck, Mail, Smartphone, KeyRound, Eye, EyeOff, CheckCircle2, AlertCircle } from 'lucide-react';
import { isSupabaseConfigured, supabase } from '../lib/supabase';
import { checkUsernameAvailability, resolveLoginIdentifier } from '../lib/vybeApi';
import { normalizePhoneE164, validateRegistrationFields } from '../lib/utils';

export const AuthScreenWeb: React.FC<{ onAuthSuccess: () => Promise<void> }> = ({
  onAuthSuccess
}) => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const [mode, setMode] = useState<'LOGIN' | 'REGISTER' | 'OTP' | 'FORGOT'>('LOGIN');
  const [identifier, setIdentifier] = useState(searchParams.get('email') || '');
  const [loginPassword, setLoginPassword] = useState('');
  const [showPass, setShowPass] = useState(false);

  // Registration mandatory fields (Date of Birth is intentionally blank by default)
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [username, setUsername] = useState('');
  const [usernameStatus, setUsernameStatus] = useState<'IDLE' | 'CHECKING' | 'AVAILABLE' | 'TAKEN'>('IDLE');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [dateOfBirth, setDateOfBirth] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  // Dual OTP verification state
  const [emailOtp, setEmailOtp] = useState('');
  const [phoneOtp, setPhoneOtp] = useState('');
  const [emailVerified, setEmailVerified] = useState(false);
  const [phoneVerified, setPhoneVerified] = useState(false);
  const [emailCooldown, setEmailCooldown] = useState(0);
  const [phoneCooldown, setPhoneCooldown] = useState(0);

  // Forgot password state
  const [recoveryEmail, setRecoveryEmail] = useState('');

  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [infoMsg, setInfoMsg] = useState<string | null>(null);

  // Debounced live username availability check
  useEffect(() => {
    if (mode !== 'REGISTER') return;
    const clean = username.trim().replace(/^@/, '').toLowerCase();
    if (clean.length < 3 || !/^[a-z0-9_.]+$/.test(clean) || clean === 'null') {
      setUsernameStatus('IDLE');
      return;
    }
    setUsernameStatus('CHECKING');
    const timer = window.setTimeout(async () => {
      const available = await checkUsernameAvailability(clean);
      setUsernameStatus(available ? 'AVAILABLE' : 'TAKEN');
    }, 420);
    return () => window.clearTimeout(timer);
  }, [username, mode]);

  // Resend cooldown timers
  useEffect(() => {
    if (emailCooldown <= 0 && phoneCooldown <= 0) return;
    const id = window.setInterval(() => {
      setEmailCooldown((c) => Math.max(0, c - 1));
      setPhoneCooldown((c) => Math.max(0, c - 1));
    }, 1000);
    return () => window.clearInterval(id);
  }, [emailCooldown, phoneCooldown]);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    setInfoMsg(null);

    if (!identifier.trim() || !loginPassword) {
      setErrorMsg('Enter your email, phone number, or @username and password.');
      return;
    }
    if (!isSupabaseConfigured) {
      setErrorMsg('Supabase environment variables (VITE_SUPABASE_URL & VITE_SUPABASE_ANON_KEY) are not configured.');
      return;
    }

    setLoading(true);
    try {
      const resolved = await resolveLoginIdentifier(identifier);
      if (!resolved.found) {
        setErrorMsg('Invalid credentials. Check your identifier and password.');
        setLoading(false);
        return;
      }

      const authRes =
        resolved.mode === 'phone' && resolved.phone
          ? await supabase.auth.signInWithPassword({
              phone: resolved.phone,
              password: loginPassword
            })
          : await supabase.auth.signInWithPassword({
              email: resolved.email || identifier.trim(),
              password: loginPassword
            });

      if (authRes.error) {
        setErrorMsg(authRes.error.message || 'Authentication failed.');
      } else {
        await onAuthSuccess();
        navigate('/');
      }
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Unable to sign in.');
    } finally {
      setLoading(false);
    }
  };

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    setInfoMsg(null);

    const errors = validateRegistrationFields({
      firstName,
      lastName,
      username,
      email,
      phone,
      dateOfBirth,
      password,
      confirmPassword
    });

    if (usernameStatus === 'TAKEN') {
      errors.username = 'This username is already taken. Choose another handle.';
    }
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    if (!isSupabaseConfigured) {
      setErrorMsg('Supabase environment variables are not configured.');
      return;
    }

    setLoading(true);
    try {
      const cleanUsername = username.trim().replace(/^@/, '').toLowerCase();
      const stillFree = await checkUsernameAvailability(cleanUsername);
      if (!stillFree) {
        setFieldErrors({ username: 'Username was just claimed. Pick a unique handle.' });
        setLoading(false);
        return;
      }

      const normalizedPhone = normalizePhoneE164(phone);
      const displayName = `${firstName.trim()} ${lastName.trim()}`.trim();

      const { data, error } = await supabase.auth.signUp({
        email: email.trim().toLowerCase(),
        password,
        options: {
          emailRedirectTo: `${window.location.origin}/`,
          data: {
            first_name: firstName.trim(),
            last_name: lastName.trim(),
            username: cleanUsername,
            display_name: displayName,
            phone: normalizedPhone,
            date_of_birth: dateOfBirth
          }
        }
      });

      if (error) {
        setErrorMsg(error.message);
        setLoading(false);
        return;
      }

      // Trigger Phone SMS OTP if Supabase Phone provider is enabled
      const smsRes = await supabase.auth.signInWithOtp({ phone: normalizedPhone });
      if (smsRes.error) {
        setInfoMsg(
          `Account created. Email verification sent to ${email.trim()}. SMS OTP note: ${smsRes.error.message}`
        );
      } else {
        setInfoMsg(`Verification codes sent to ${email.trim()} and ${normalizedPhone}.`);
      }

      setEmailCooldown(60);
      setPhoneCooldown(60);
      setEmailVerified(Boolean(data.user?.email_confirmed_at));
      setPhoneVerified(Boolean(data.user?.phone_confirmed_at));
      setMode('OTP');
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Registration failed.');
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyEmailOtp = async () => {
    if (!emailOtp.trim()) return;
    setLoading(true);
    setErrorMsg(null);
    const { data, error } = await supabase.auth.verifyOtp({
      email: email.trim().toLowerCase(),
      token: emailOtp.trim(),
      type: 'signup'
    });
    setLoading(false);
    if (error) {
      setErrorMsg(`Email OTP error: ${error.message}`);
      return;
    }
    if (data.user) {
      await supabase.from('profiles').update({ is_email_verified: true }).eq('id', data.user.id);
    }
    setEmailVerified(true);
    setInfoMsg('Email verified.');
  };

  const handleVerifyPhoneOtp = async () => {
    if (!phoneOtp.trim()) return;
    setLoading(true);
    setErrorMsg(null);
    const normalizedPhone = normalizePhoneE164(phone);
    const { data, error } = await supabase.auth.verifyOtp({
      phone: normalizedPhone,
      token: phoneOtp.trim(),
      type: 'sms'
    });
    setLoading(false);
    if (error) {
      setErrorMsg(`Phone SMS OTP error: ${error.message}`);
      return;
    }
    if (data.user) {
      await supabase.from('profiles').update({ is_phone_verified: true }).eq('id', data.user.id);
    }
    setPhoneVerified(true);
    setInfoMsg('Phone number verified.');
  };

  const handleResendEmailOtp = async () => {
    if (emailCooldown > 0) return;
    const { error } = await supabase.auth.resend({
      type: 'signup',
      email: email.trim().toLowerCase()
    });
    if (error) setErrorMsg(error.message);
    else {
      setEmailCooldown(60);
      setInfoMsg('New email verification code sent.');
    }
  };

  const handleResendPhoneOtp = async () => {
    if (phoneCooldown > 0) return;
    const { error } = await supabase.auth.signInWithOtp({
      phone: normalizePhoneE164(phone)
    });
    if (error) setErrorMsg(error.message);
    else {
      setPhoneCooldown(60);
      setInfoMsg('New SMS verification code sent.');
    }
  };

  const handleForgotPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    setInfoMsg(null);
    if (!recoveryEmail.trim()) {
      setErrorMsg('Enter your registered email address.');
      return;
    }
    setLoading(true);
    const { error } = await supabase.auth.resetPasswordForEmail(recoveryEmail.trim().toLowerCase(), {
      redirectTo: `${window.location.origin}/reset-password`
    });
    setLoading(false);
    if (error) {
      setErrorMsg(error.message);
    } else {
      setInfoMsg('Password reset link sent! Open the link in your email to set a new password.');
    }
  };

  return (
    <div
      style={{
        minHeight: '100dvh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '24px 16px',
        background: 'radial-gradient(circle at top, #181331 0%, #09090e 65%)'
      }}
    >
      <div className="vybe-card" style={{ width: '100%', maxWidth: 480, padding: 28 }}>
        <div style={{ textAlign: 'center', marginBottom: 22 }}>
          <img
            src="/icons/icon-192.png"
            alt="VYBE Logo"
            style={{ width: 62, height: 62, borderRadius: 16, marginBottom: 10 }}
          />
          <h1 className="vybe-brand-font vybe-gradient-text" style={{ fontSize: '2rem', fontWeight: 800 }}>
            VYBE
          </h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.88rem', marginTop: 4 }}>
            Back to Real. Expressive Moods, Stories, Rooms & Live Connection.
          </p>
        </div>

        {(mode === 'LOGIN' || mode === 'REGISTER') && (
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, marginBottom: 18 }}>
            <button
              type="button"
              className={`vybe-btn ${mode === 'LOGIN' ? 'vybe-btn-primary' : 'vybe-btn-outline'}`}
              onClick={() => {
                setMode('LOGIN');
                setErrorMsg(null);
              }}
            >
              Sign In
            </button>
            <button
              type="button"
              className={`vybe-btn ${mode === 'REGISTER' ? 'vybe-btn-primary' : 'vybe-btn-outline'}`}
              onClick={() => {
                setMode('REGISTER');
                setErrorMsg(null);
              }}
            >
              Create Account
            </button>
          </div>
        )}

        {errorMsg && (
          <div
            role="alert"
            style={{
              background: 'rgba(255, 77, 109, 0.14)',
              border: '1px solid rgba(255, 77, 109, 0.45)',
              color: '#ff8da1',
              borderRadius: 12,
              padding: '10px 12px',
              fontSize: '0.84rem',
              marginBottom: 14,
              display: 'flex',
              gap: 8,
              alignItems: 'center'
            }}
          >
            <AlertCircle size={16} /> <span>{errorMsg}</span>
          </div>
        )}

        {infoMsg && (
          <div
            style={{
              background: 'rgba(0, 229, 255, 0.12)',
              border: '1px solid rgba(0, 229, 255, 0.4)',
              color: '#9beeff',
              borderRadius: 12,
              padding: '10px 12px',
              fontSize: '0.84rem',
              marginBottom: 14
            }}
          >
            {infoMsg}
          </div>
        )}

        {mode === 'LOGIN' && (
          <form onSubmit={handleLogin} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <div>
              <label style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', display: 'block', marginBottom: 4 }}>
                Email, Phone Number, or @username
              </label>
              <input
                className="vybe-input"
                type="text"
                placeholder="you@domain.com, +14155552671, or @handle"
                value={identifier}
                onChange={(e) => setIdentifier(e.target.value)}
                autoComplete="username"
              />
            </div>

            <div>
              <label style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', display: 'block', marginBottom: 4 }}>
                Password
              </label>
              <div style={{ position: 'relative' }}>
                <input
                  className="vybe-input"
                  type={showPass ? 'text' : 'password'}
                  placeholder="Enter password"
                  value={loginPassword}
                  onChange={(e) => setLoginPassword(e.target.value)}
                  autoComplete="current-password"
                />
                <button
                  type="button"
                  onClick={() => setShowPass(!showPass)}
                  aria-label="Toggle password visibility"
                  style={{
                    position: 'absolute',
                    right: 12,
                    top: '50%',
                    transform: 'translateY(-50%)',
                    background: 'none',
                    border: 'none',
                    color: 'var(--text-secondary)',
                    cursor: 'pointer'
                  }}
                >
                  {showPass ? <EyeOff size={17} /> : <Eye size={17} />}
                </button>
              </div>
            </div>

            <button type="submit" className="vybe-btn vybe-btn-primary" disabled={loading}>
              {loading ? 'Signing in…' : 'Sign In to VYBE'}
            </button>

            <button
              type="button"
              onClick={() => {
                setRecoveryEmail(identifier.includes('@') && !identifier.startsWith('@') ? identifier : '');
                setMode('FORGOT');
                setErrorMsg(null);
                setInfoMsg(null);
              }}
              style={{
                background: 'none',
                border: 'none',
                color: 'var(--vybe-cyan)',
                fontSize: '0.84rem',
                cursor: 'pointer',
                marginTop: 4
              }}
            >
              Forgot password?
            </button>
          </form>
        )}

        {mode === 'REGISTER' && (
          <form onSubmit={handleRegister} style={{ display: 'flex', flexDirection: 'column', gap: 11 }}>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
              <div>
                <label style={{ fontSize: '0.78rem', color: 'var(--text-secondary)' }}>First Name *</label>
                <input
                  className="vybe-input"
                  value={firstName}
                  onChange={(e) => setFirstName(e.target.value)}
                  placeholder="First name"
                />
                {fieldErrors.firstName && (
                  <small style={{ color: 'var(--vybe-danger)' }}>{fieldErrors.firstName}</small>
                )}
              </div>
              <div>
                <label style={{ fontSize: '0.78rem', color: 'var(--text-secondary)' }}>Last Name *</label>
                <input
                  className="vybe-input"
                  value={lastName}
                  onChange={(e) => setLastName(e.target.value)}
                  placeholder="Last name"
                />
                {fieldErrors.lastName && (
                  <small style={{ color: 'var(--vybe-danger)' }}>{fieldErrors.lastName}</small>
                )}
              </div>
            </div>

            <div>
              <label style={{ fontSize: '0.78rem', color: 'var(--text-secondary)' }}>Unique Username *</label>
              <input
                className="vybe-input"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="your_vybe_handle"
              />
              {usernameStatus === 'CHECKING' && (
                <small style={{ color: 'var(--text-secondary)' }}>Checking availability…</small>
              )}
              {usernameStatus === 'AVAILABLE' && (
                <small style={{ color: 'var(--vybe-green)' }}>@{username.replace(/^@/, '').toLowerCase()} is available</small>
              )}
              {(fieldErrors.username || usernameStatus === 'TAKEN') && (
                <small style={{ color: 'var(--vybe-danger)', display: 'block' }}>
                  {fieldErrors.username || 'Username is already taken.'}
                </small>
              )}
            </div>

            <div>
              <label style={{ fontSize: '0.78rem', color: 'var(--text-secondary)' }}>Email Address *</label>
              <input
                className="vybe-input"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@example.com"
              />
              {fieldErrors.email && <small style={{ color: 'var(--vybe-danger)' }}>{fieldErrors.email}</small>}
            </div>

            <div>
              <label style={{ fontSize: '0.78rem', color: 'var(--text-secondary)' }}>
                Phone Number (with country code) *
              </label>
              <input
                className="vybe-input"
                type="tel"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                placeholder="+14155552671"
              />
              {fieldErrors.phone && <small style={{ color: 'var(--vybe-danger)' }}>{fieldErrors.phone}</small>}
            </div>

            <div>
              <label style={{ fontSize: '0.78rem', color: 'var(--text-secondary)' }}>
                Date of Birth (18+ required for initial launch) *
              </label>
              <input
                className="vybe-input"
                type="date"
                value={dateOfBirth}
                max={new Date().toISOString().split('T')[0]}
                onChange={(e) => setDateOfBirth(e.target.value)}
              />
              <p style={{ fontSize: '0.72rem', color: 'var(--text-muted)', marginTop: 4, lineHeight: 1.3 }}>
                Initial launch is restricted to individuals aged 18 and above. Note: Self-declared date of birth is an onboarding attestation and does not guarantee sovereign cryptographic identity verification.
              </p>
              {fieldErrors.dateOfBirth && (
                <small style={{ color: 'var(--vybe-danger)' }}>{fieldErrors.dateOfBirth}</small>
              )}
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
              <div>
                <label style={{ fontSize: '0.78rem', color: 'var(--text-secondary)' }}>Password *</label>
                <input
                  className="vybe-input"
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Min 8 chars"
                />
                {fieldErrors.password && (
                  <small style={{ color: 'var(--vybe-danger)' }}>{fieldErrors.password}</small>
                )}
              </div>
              <div>
                <label style={{ fontSize: '0.78rem', color: 'var(--text-secondary)' }}>Confirm Password *</label>
                <input
                  className="vybe-input"
                  type="password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  placeholder="Repeat password"
                />
                {fieldErrors.confirmPassword && (
                  <small style={{ color: 'var(--vybe-danger)' }}>{fieldErrors.confirmPassword}</small>
                )}
              </div>
            </div>

            <button type="submit" className="vybe-btn vybe-btn-primary" disabled={loading}>
              {loading ? 'Creating VYBE Account…' : 'Create Account & Verify'}
            </button>
          </form>
        )}

        {mode === 'OTP' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <ShieldCheck size={20} color="#00E5FF" />
              <strong>Dual Account Verification</strong>
            </div>

            {/* Email OTP Box */}
            <div className="vybe-card" style={{ background: 'var(--bg-elevated)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
                <span style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: '0.86rem', fontWeight: 700 }}>
                  <Mail size={15} /> Email OTP
                </span>
                <span
                  className="vybe-badge"
                  style={{
                    background: emailVerified ? 'rgba(0,230,118,0.18)' : 'rgba(255,213,79,0.18)',
                    color: emailVerified ? '#00E676' : '#FFD54F'
                  }}
                >
                  {emailVerified ? 'Verified' : 'Pending'}
                </span>
              </div>
              {!emailVerified && (
                <>
                  <div style={{ display: 'flex', gap: 8 }}>
                    <input
                      className="vybe-input"
                      placeholder="6-digit email code"
                      value={emailOtp}
                      onChange={(e) => setEmailOtp(e.target.value)}
                    />
                    <button
                      type="button"
                      className="vybe-btn vybe-btn-violet"
                      onClick={() => void handleVerifyEmailOtp()}
                      disabled={loading}
                    >
                      Verify
                    </button>
                  </div>
                  <button
                    type="button"
                    onClick={() => void handleResendEmailOtp()}
                    disabled={emailCooldown > 0}
                    style={{
                      marginTop: 8,
                      background: 'none',
                      border: 'none',
                      color: emailCooldown > 0 ? 'var(--text-muted)' : 'var(--vybe-cyan)',
                      fontSize: '0.78rem',
                      cursor: emailCooldown > 0 ? 'default' : 'pointer'
                    }}
                  >
                    {emailCooldown > 0 ? `Resend email code in ${emailCooldown}s` : 'Resend email code'}
                  </button>
                </>
              )}
            </div>

            {/* Phone SMS OTP Box */}
            <div className="vybe-card" style={{ background: 'var(--bg-elevated)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
                <span style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: '0.86rem', fontWeight: 700 }}>
                  <Smartphone size={15} /> Phone SMS OTP
                </span>
                <span
                  className="vybe-badge"
                  style={{
                    background: phoneVerified ? 'rgba(0,230,118,0.18)' : 'rgba(255,213,79,0.18)',
                    color: phoneVerified ? '#00E676' : '#FFD54F'
                  }}
                >
                  {phoneVerified ? 'Verified' : 'Pending'}
                </span>
              </div>
              {!phoneVerified && (
                <>
                  <div style={{ display: 'flex', gap: 8 }}>
                    <input
                      className="vybe-input"
                      placeholder="6-digit SMS code"
                      value={phoneOtp}
                      onChange={(e) => setPhoneOtp(e.target.value)}
                    />
                    <button
                      type="button"
                      className="vybe-btn vybe-btn-violet"
                      onClick={() => void handleVerifyPhoneOtp()}
                      disabled={loading}
                    >
                      Verify
                    </button>
                  </div>
                  <button
                    type="button"
                    onClick={() => void handleResendPhoneOtp()}
                    disabled={phoneCooldown > 0}
                    style={{
                      marginTop: 8,
                      background: 'none',
                      border: 'none',
                      color: phoneCooldown > 0 ? 'var(--text-muted)' : 'var(--vybe-cyan)',
                      fontSize: '0.78rem',
                      cursor: phoneCooldown > 0 ? 'default' : 'pointer'
                    }}
                  >
                    {phoneCooldown > 0 ? `Resend SMS code in ${phoneCooldown}s` : 'Resend SMS code'}
                  </button>
                </>
              )}
            </div>

            {emailVerified && phoneVerified ? (
              <button
                type="button"
                className="vybe-btn vybe-btn-primary"
                onClick={async () => {
                  await onAuthSuccess();
                  navigate('/');
                }}
              >
                <CheckCircle2 size={16} /> Continue to VYBE (Fully Verified)
              </button>
            ) : (
              <button
                type="button"
                className="vybe-btn vybe-btn-outline"
                onClick={() => {
                  setIdentifier(email);
                  setMode('LOGIN');
                }}
              >
                Back to Sign In
              </button>
            )}
          </div>
        )}

        {mode === 'FORGOT' && (
          <form onSubmit={handleForgotPassword} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <KeyRound size={18} color="#00E5FF" />
              <strong>Reset Your VYBE Password</strong>
            </div>
            <input
              className="vybe-input"
              type="email"
              placeholder="Enter your account email"
              value={recoveryEmail}
              onChange={(e) => setRecoveryEmail(e.target.value)}
            />
            <button type="submit" className="vybe-btn vybe-btn-primary" disabled={loading}>
              {loading ? 'Sending Reset Link…' : 'Send Recovery Link'}
            </button>
            <button
              type="button"
              className="vybe-btn vybe-btn-outline"
              onClick={() => {
                setMode('LOGIN');
                setErrorMsg(null);
              }}
            >
              Back to Login
            </button>
          </form>
        )}
      </div>
    </div>
  );
};

export const PasswordResetScreenWeb: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [sessionReady, setSessionReady] = useState(false);
  const [checkingLink, setCheckingLink] = useState(true);
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [updatedEmail, setUpdatedEmail] = useState('');
  const [success, setSuccess] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;
    const establishSession = async () => {
      try {
        const tokenHash = searchParams.get('token_hash');
        const type = searchParams.get('type');
        const code = searchParams.get('code');

        if (tokenHash && type === 'recovery') {
          const { data, error } = await supabase.auth.verifyOtp({
            token_hash: tokenHash,
            type: 'recovery'
          });
          if (!error && data.session && mounted) {
            setUpdatedEmail(data.user?.email || '');
            setSessionReady(true);
            setCheckingLink(false);
            return;
          }
        }

        if (code) {
          const { data, error } = await supabase.auth.exchangeCodeForSession(code);
          if (!error && data.session && mounted) {
            setUpdatedEmail(data.user?.email || '');
            setSessionReady(true);
            setCheckingLink(false);
            return;
          }
        }

        const { data } = await supabase.auth.getSession();
        if (mounted) {
          if (data.session) {
            setUpdatedEmail(data.session.user?.email || '');
            setSessionReady(true);
          } else {
            setErrorMsg('This password recovery link is expired or invalid. Request a new recovery email.');
          }
          setCheckingLink(false);
        }
      } catch {
        if (mounted) {
          setErrorMsg('Unable to validate recovery session.');
          setCheckingLink(false);
        }
      }
    };
    void establishSession();
    return () => {
      mounted = false;
    };
  }, [searchParams]);

  const handleUpdatePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    if (submitting) return;
    setErrorMsg(null);

    if (newPassword.length < 8) {
      setErrorMsg('New password must be at least 8 characters.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setErrorMsg('Passwords do not match.');
      return;
    }

    setSubmitting(true);
    const { data, error } = await supabase.auth.updateUser({ password: newPassword });
    setSubmitting(false);

    if (error || !data.user) {
      setErrorMsg(error?.message || 'Password update failed. Please request a fresh reset link.');
      return;
    }

    const emailForLogin = data.user.email || updatedEmail;
    await supabase.auth.signOut();
    setUpdatedEmail(emailForLogin);
    setSuccess(true);
  };

  return (
    <div
      style={{
        minHeight: '100dvh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 20
      }}
    >
      <div className="vybe-card" style={{ width: '100%', maxWidth: 440, padding: 26 }}>
        <h2 className="vybe-brand-font vybe-gradient-text" style={{ fontSize: '1.5rem', marginBottom: 8 }}>
          Update Password
        </h2>

        {checkingLink && <p style={{ color: 'var(--text-secondary)' }}>Validating recovery session…</p>}

        {errorMsg && (
          <div
            style={{
              background: 'rgba(255,77,109,0.14)',
              border: '1px solid rgba(255,77,109,0.45)',
              color: '#ff8da1',
              borderRadius: 12,
              padding: 12,
              fontSize: '0.85rem',
              marginBottom: 14
            }}
          >
            {errorMsg}
          </div>
        )}

        {success ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div
              style={{
                background: 'rgba(0,230,118,0.14)',
                border: '1px solid rgba(0,230,118,0.4)',
                color: '#00E676',
                borderRadius: 12,
                padding: 12,
                fontSize: '0.88rem'
              }}
            >
              Your VYBE password has been updated. You can sign in immediately with your new password.
            </div>
            <button
              type="button"
              className="vybe-btn vybe-btn-primary"
              onClick={() =>
                navigate(updatedEmail ? `/login?email=${encodeURIComponent(updatedEmail)}` : '/login')
              }
            >
              Go to Login
            </button>
          </div>
        ) : (
          !checkingLink && (
            <form onSubmit={handleUpdatePassword} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {sessionReady && (
                <>
                  <input
                    className="vybe-input"
                    type="password"
                    placeholder="New password (min 8 chars)"
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                  />
                  <input
                    className="vybe-input"
                    type="password"
                    placeholder="Confirm new password"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                  />
                  <button type="submit" className="vybe-btn vybe-btn-primary" disabled={submitting}>
                    {submitting ? 'Updating Password…' : 'Update Password'}
                  </button>
                </>
              )}
              <button
                type="button"
                className="vybe-btn vybe-btn-outline"
                onClick={() => navigate('/login')}
              >
                Login
              </button>
            </form>
          )
        )}
      </div>
    </div>
  );
};

export const ResetPasswordPageWeb = PasswordResetScreenWeb;
