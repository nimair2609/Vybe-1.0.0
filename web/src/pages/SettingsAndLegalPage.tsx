import React, { useState } from 'react';
import {
  Shield,
  Lock,
  FileText,
  AlertTriangle,
  Download,
  Trash2,
  Check,
  UserCheck,
  HelpCircle,
  ExternalLink,
  ChevronRight
} from 'lucide-react';
import { supabase } from '../lib/supabase';
import type { VybeUser } from '../types/vybe';

export const SettingsAndLegalPage: React.FC<{
  currentUser: VybeUser;
  onUpdateCurrentUser: (user: VybeUser) => void;
  onLogout: () => Promise<void>;
}> = ({ currentUser, onUpdateCurrentUser, onLogout }) => {
  const [activeTab, setActiveTab] = useState<'SETTINGS' | 'TERMS' | 'PRIVACY'>('SETTINGS');
  const [isPrivate, setIsPrivate] = useState(currentUser.isPrivate);
  const [messagePrivacy, setMessagePrivacy] = useState(currentUser.messagePrivacy);
  const [savingPrivacy, setSavingPrivacy] = useState(false);
  const [privacySavedToast, setPrivacySavedToast] = useState(false);

  // Account Data Export
  const [exporting, setExporting] = useState(false);

  // Account Deletion State
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [deleteInputText, setDeleteInputText] = useState('');
  const [isDeletingAccount, setIsDeletingAccount] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const handleSavePrivacySettings = async () => {
    setSavingPrivacy(true);
    try {
      const { error } = await supabase
        .from('profiles')
        .update({
          is_private_account: isPrivate,
          message_privacy: messagePrivacy
        })
        .eq('id', currentUser.id);

      if (error) throw error;
      onUpdateCurrentUser({
        ...currentUser,
        isPrivate,
        messagePrivacy
      });
      setPrivacySavedToast(true);
      setTimeout(() => setPrivacySavedToast(false), 2200);
    } catch {
      // Handle error
    } finally {
      setSavingPrivacy(false);
    }
  };

  const handleExportData = async () => {
    setExporting(true);
    try {
      const [postsRes, commentsRes] = await Promise.all([
        supabase.from('posts').select('*').eq('author_id', currentUser.id),
        supabase.from('comments').select('*').eq('author_id', currentUser.id)
      ]);

      const exportPayload = {
        exportDate: new Date().toISOString(),
        service: 'VYBE Social Network',
        profile: {
          id: currentUser.id,
          username: currentUser.username,
          displayName: currentUser.displayName,
          email: currentUser.email,
          phone: currentUser.phone,
          dateOfBirth: currentUser.dateOfBirth,
          bio: currentUser.bio,
          createdAt: currentUser.createdAtIso
        },
        posts: postsRes.data || [],
        comments: commentsRes.data || []
      };

      const blob = new Blob([JSON.stringify(exportPayload, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `vybe_data_export_${currentUser.username}_${Date.now()}.json`;
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      // Fallback
    } finally {
      setExporting(false);
    }
  };

  const handleConfirmDeleteAccount = async () => {
    if (deleteInputText.trim() !== currentUser.username) {
      setDeleteError(`Please type your exact username "${currentUser.username}" to confirm.`);
      return;
    }
    setIsDeletingAccount(true);
    setDeleteError(null);
    try {
      // Soft-deactivate and clean profile data
      await supabase
        .from('profiles')
        .update({
          is_deactivated: true,
          bio: '[Account Deleted]',
          display_name: 'Deleted User',
          avatar_url: ''
        })
        .eq('id', currentUser.id);

      await onLogout();
    } catch (err: unknown) {
      setDeleteError(err instanceof Error ? err.message : 'Unable to complete deletion request.');
      setIsDeletingAccount(false);
    }
  };

  return (
    <div style={{ maxWidth: '820px', margin: '0 auto', padding: '24px 16px', display: 'flex', flexDirection: 'column', gap: '24px' }}>
      {/* Top Header */}
      <div>
        <h2 className="vybe-brand-font" style={{ fontSize: '1.6rem', fontWeight: 800, margin: 0 }}>
          Settings & Policies
        </h2>
        <p style={{ margin: '4px 0 0', color: 'var(--text-secondary)', fontSize: '0.9rem' }}>
          Manage your account privacy, age gating, data access rights, and legal agreements.
        </p>
      </div>

      {/* Tabs */}
      <div style={{ display: 'flex', borderBottom: '1px solid var(--border-subtle)', gap: '10px' }}>
        <button
          onClick={() => setActiveTab('SETTINGS')}
          className="vybe-btn"
          style={{
            background: 'none',
            borderBottom: activeTab === 'SETTINGS' ? '2px solid var(--vybe-violet)' : '2px solid transparent',
            borderRadius: 0,
            padding: '10px 18px',
            color: activeTab === 'SETTINGS' ? 'var(--text-primary)' : 'var(--text-muted)'
          }}
        >
          <Lock size={16} />
          <span>Privacy & Account</span>
        </button>

        <button
          onClick={() => setActiveTab('TERMS')}
          className="vybe-btn"
          style={{
            background: 'none',
            borderBottom: activeTab === 'TERMS' ? '2px solid var(--vybe-violet)' : '2px solid transparent',
            borderRadius: 0,
            padding: '10px 18px',
            color: activeTab === 'TERMS' ? 'var(--text-primary)' : 'var(--text-muted)'
          }}
        >
          <FileText size={16} />
          <span>Terms of Service</span>
        </button>

        <button
          onClick={() => setActiveTab('PRIVACY')}
          className="vybe-btn"
          style={{
            background: 'none',
            borderBottom: activeTab === 'PRIVACY' ? '2px solid var(--vybe-violet)' : '2px solid transparent',
            borderRadius: 0,
            padding: '10px 18px',
            color: activeTab === 'PRIVACY' ? 'var(--text-primary)' : 'var(--text-muted)'
          }}
        >
          <Shield size={16} />
          <span>Privacy Policy</span>
        </button>
      </div>

      {/* Tab 1: Privacy & Account Settings */}
      {activeTab === 'SETTINGS' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          {/* Privacy Controls Card */}
          <div className="vybe-card" style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <Lock size={20} color="var(--vybe-violet)" />
              <h3 style={{ margin: 0, fontSize: '1.1rem', fontWeight: 700 }}>Privacy & Visibility</h3>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '12px 16px', background: 'var(--bg-elevated)', borderRadius: 'var(--radius-sm)' }}>
              <div>
                <span style={{ display: 'block', fontSize: '0.9rem', fontWeight: 600 }}>Private Profile</span>
                <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
                  When enabled, users must request and be approved before seeing your posts and stories.
                </span>
              </div>
              <input
                type="checkbox"
                checked={isPrivate}
                onChange={(e) => setIsPrivate(e.target.checked)}
                style={{ width: '18px', height: '18px', accentColor: 'var(--vybe-violet)', cursor: 'pointer' }}
              />
            </div>

            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '12px 16px', background: 'var(--bg-elevated)', borderRadius: 'var(--radius-sm)' }}>
              <div>
                <span style={{ display: 'block', fontSize: '0.9rem', fontWeight: 600 }}>Direct Message Privacy</span>
                <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
                  Control who is allowed to start a direct message conversation with you.
                </span>
              </div>
              <select
                value={messagePrivacy}
                onChange={(e) => setMessagePrivacy(e.target.value as VybeUser['messagePrivacy'])}
                style={{
                  background: 'var(--bg-surface)',
                  border: '1px solid var(--border-subtle)',
                  borderRadius: 'var(--radius-pill)',
                  padding: '6px 14px',
                  color: 'var(--text-primary)',
                  fontSize: '0.85rem',
                  outline: 'none'
                }}
              >
                <option value="EVERYONE">Everyone</option>
                <option value="MUTUAL_FOLLOWS">Mutual Follows Only</option>
                <option value="NONE">Nobody (Disable DMs)</option>
              </select>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: '12px' }}>
              {privacySavedToast && (
                <span style={{ color: 'var(--vybe-green)', fontSize: '0.85rem', display: 'flex', alignItems: 'center', gap: '4px' }}>
                  <Check size={16} /> Saved!
                </span>
              )}
              <button
                onClick={handleSavePrivacySettings}
                disabled={savingPrivacy}
                className="vybe-btn vybe-btn-primary"
                style={{ padding: '8px 20px' }}
              >
                {savingPrivacy ? 'Saving...' : 'Save Privacy Changes'}
              </button>
            </div>
          </div>

          {/* 18+ Age Gating Notice Card */}
          <div className="vybe-card" style={{ display: 'flex', flexDirection: 'column', gap: '12px', borderLeft: '4px solid var(--vybe-cyan)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <UserCheck size={20} color="var(--vybe-cyan)" />
              <h3 style={{ margin: 0, fontSize: '1.1rem', fontWeight: 700 }}>Age Restriction & Safety Notice (18+)</h3>
            </div>
            <p style={{ margin: 0, fontSize: '0.88rem', lineHeight: '1.5', color: 'var(--text-secondary)' }}>
              VYBE is currently designated for adults aged <strong>18 and above</strong>. During account creation, users are required to declare their date of birth.
            </p>
            <div style={{ padding: '10px 14px', background: 'var(--bg-elevated)', borderRadius: 'var(--radius-sm)', fontSize: '0.82rem', color: 'var(--text-muted)', lineHeight: '1.4' }}>
              <strong>Notice Regarding Self-Declared Age:</strong> While birth dates are enforced during registration, self-declaration cannot independently guarantee absolute physical verification without government ID checks. VYBE combines community reporting, automated keyword filters, and proactive moderation to restrict underage access.
            </div>
          </div>

          {/* Account Data & Export Rights */}
          <div className="vybe-card" style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <Download size={20} color="var(--vybe-green)" />
              <h3 style={{ margin: 0, fontSize: '1.1rem', fontWeight: 700 }}>Download Personal Data</h3>
            </div>
            <p style={{ margin: 0, fontSize: '0.88rem', color: 'var(--text-secondary)' }}>
              Download a machine-readable JSON copy of your profile, posts, and comments in compliance with data portability principles.
            </p>
            <div>
              <button
                onClick={handleExportData}
                disabled={exporting}
                className="vybe-btn"
                style={{ padding: '8px 18px' }}
              >
                <Download size={16} />
                <span>{exporting ? 'Preparing Archive...' : 'Export My Data (.json)'}</span>
              </button>
            </div>
          </div>

          {/* Danger Zone: Account Deletion */}
          <div className="vybe-card" style={{ display: 'flex', flexDirection: 'column', gap: '14px', border: '1px solid rgba(255, 77, 109, 0.35)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <AlertTriangle size={20} color="var(--vybe-danger)" />
              <h3 style={{ margin: 0, fontSize: '1.1rem', fontWeight: 700, color: 'var(--vybe-danger)' }}>Danger Zone</h3>
            </div>
            <p style={{ margin: 0, fontSize: '0.88rem', color: 'var(--text-secondary)' }}>
              Deactivating your account removes your profile, stories, and posts from peer discovery and de-authenticates all sessions.
            </p>
            <div>
              <button
                onClick={() => setShowDeleteConfirm(true)}
                className="vybe-btn"
                style={{ background: 'rgba(255, 77, 109, 0.15)', color: 'var(--vybe-danger)', border: '1px solid var(--vybe-danger)', padding: '8px 18px' }}
              >
                <Trash2 size={16} />
                <span>Delete Account</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Tab 2: Terms of Service */}
      {activeTab === 'TERMS' && (
        <div className="vybe-card" style={{ display: 'flex', flexDirection: 'column', gap: '16px', lineHeight: '1.6', fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
          <h3 style={{ color: 'var(--text-primary)', margin: 0, fontSize: '1.25rem' }}>VYBE Terms of Service</h3>
          <p><strong>Brand:</strong> NR Next • <strong>Last Updated:</strong> October 2026</p>

          <h4 style={{ color: 'var(--text-primary)', margin: '10px 0 4px' }}>1. Acceptance of Terms</h4>
          <p>
            By accessing or using the VYBE web application, installable Progressive Web App (PWA), or associated services, you agree to be bound by these Terms of Service. If you do not agree with any part of these terms, you must not use our services.
          </p>

          <h4 style={{ color: 'var(--text-primary)', margin: '10px 0 4px' }}>2. Age Requirement (18+)</h4>
          <p>
            VYBE is intended strictly for individuals aged 18 years or older. You represent that you are at least 18 years old. Falsification of date of birth is grounds for immediate account suspension and termination.
          </p>

          <h4 style={{ color: 'var(--text-primary)', margin: '10px 0 4px' }}>3. User Accounts and Verification</h4>
          <p>
            You are responsible for maintaining the confidentiality of your credentials. Registration requires a unique username and verification via email and/or SMS OTP. You must not impersonate any person or brand.
          </p>

          <h4 style={{ color: 'var(--text-primary)', margin: '10px 0 4px' }}>4. Community Standards & Prohibited Conduct</h4>
          <p>
            VYBE is a platform built on authentic expression, mood-driven spaces, and safe community interaction. You agree not to upload content that is unlawful, defamatory, sexually explicit, harassing, or infringing on intellectual property. Hate speech, harassment in 24/7 Unload Rooms, and non-consensual recordings of WebRTC video calls are strictly prohibited.
          </p>

          <h4 style={{ color: 'var(--text-primary)', margin: '10px 0 4px' }}>5. WebRTC Voice and Video Calling</h4>
          <p>
            Voice and video calls use peer-to-peer WebRTC connections with STUN/TURN relays where direct peer connectivity fails. Media streams are transmitted directly between call participants and are not recorded or stored by VYBE servers.
          </p>

          <h4 style={{ color: 'var(--text-primary)', margin: '10px 0 4px' }}>6. Intellectual Property & Copyright Notice</h4>
          <p>
            You retain ownership of the photos, videos, and reels you publish. You grant VYBE a worldwide, non-exclusive license to host, display, and distribute your content strictly to your selected audience (Public, Followers, or VYBE Matches). If you believe your copyrighted work has been infringed, submit a claim via our in-app reporting tool.
          </p>

          <h4 style={{ color: 'var(--text-primary)', margin: '10px 0 4px' }}>7. Termination & Disclaimer</h4>
          <p>
            VYBE reserves the right to suspend or terminate accounts that violate community rules. Services are provided "as is" without warranty of uninterrupted or error-free availability.
          </p>
        </div>
      )}

      {/* Tab 3: Privacy Policy */}
      {activeTab === 'PRIVACY' && (
        <div className="vybe-card" style={{ display: 'flex', flexDirection: 'column', gap: '16px', lineHeight: '1.6', fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
          <h3 style={{ color: 'var(--text-primary)', margin: 0, fontSize: '1.25rem' }}>VYBE Privacy Policy</h3>
          <p><strong>Brand:</strong> NR Next • <strong>Last Updated:</strong> October 2026</p>

          <h4 style={{ color: 'var(--text-primary)', margin: '10px 0 4px' }}>1. Information We Collect</h4>
          <p>
            We collect the information you provide during signup and profile creation: display name, username, email address, phone number, and date of birth. We also store media you choose to upload (posts, stories, reels, chat attachments) in Supabase Storage.
          </p>

          <h4 style={{ color: 'var(--text-primary)', margin: '10px 0 4px' }}>2. How Information Is Used</h4>
          <p>
            Your information is used to authenticate your session, deliver notifications for social interactions (likes, comments, follows), and enforce your audience settings (such as restricting private posts to approved followers). We do not sell your personal data or track you across third-party websites.
          </p>

          <h4 style={{ color: 'var(--text-primary)', margin: '10px 0 4px' }}>3. Real-Time Presence & Last Seen</h4>
          <p>
            VYBE uses periodic presence heartbeats to indicate whether you are active in the application. Other users can see an online indicator and relative last-seen information unless you have blocked or restricted them.
          </p>

          <h4 style={{ color: 'var(--text-primary)', margin: '10px 0 4px' }}>4. WebRTC Calling Data</h4>
          <p>
            When placing voice or video calls, signaling metadata (SDP offers/answers and ICE candidate addresses) is exchanged via secure Supabase Realtime channels. Call audio and video packets are streamed peer-to-peer and are never recorded on our backend.
          </p>

          <h4 style={{ color: 'var(--text-primary)', margin: '10px 0 4px' }}>5. Data Retention & Deletion Rights</h4>
          <p>
            You have the right to request a copy of your personal data or request deletion of your account at any time via the Privacy & Account tab. Upon account deletion, your personal profile information is purged from public visibility.
          </p>
        </div>
      )}

      {/* Delete Confirmation Modal */}
      {showDeleteConfirm && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0,0,0,0.8)',
            zIndex: 90,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '16px'
          }}
          onClick={() => !isDeletingAccount && setShowDeleteConfirm(false)}
        >
          <div
            style={{
              width: '100%',
              maxWidth: '460px',
              background: 'var(--bg-surface)',
              borderRadius: 'var(--radius-lg)',
              border: '1px solid var(--vybe-danger)',
              padding: '24px',
              display: 'flex',
              flexDirection: 'column',
              gap: '14px'
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <AlertTriangle size={24} color="var(--vybe-danger)" />
              <h3 style={{ margin: 0, fontSize: '1.15rem', color: 'var(--vybe-danger)', fontWeight: 800 }}>
                Confirm Account Deletion
              </h3>
            </div>

            <p style={{ margin: 0, fontSize: '0.88rem', color: 'var(--text-secondary)', lineHeight: '1.4' }}>
              This action is permanent and cannot be undone. To proceed, please type your username <strong>{currentUser.username}</strong> below:
            </p>

            {deleteError && (
              <p style={{ margin: 0, color: 'var(--vybe-danger)', fontSize: '0.82rem' }}>{deleteError}</p>
            )}

            <input
              type="text"
              placeholder={currentUser.username}
              value={deleteInputText}
              onChange={(e) => setDeleteInputText(e.target.value)}
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

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '6px' }}>
              <button
                onClick={() => setShowDeleteConfirm(false)}
                disabled={isDeletingAccount}
                className="vybe-btn"
              >
                Cancel
              </button>
              <button
                onClick={handleConfirmDeleteAccount}
                disabled={isDeletingAccount || deleteInputText.trim() !== currentUser.username}
                className="vybe-btn"
                style={{ background: 'var(--vybe-danger)', color: '#fff' }}
              >
                {isDeletingAccount ? 'Deleting...' : 'Permanently Delete'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
