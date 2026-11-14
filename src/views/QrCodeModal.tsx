import React, { useState, useEffect } from 'react';
import QRCode from 'qrcode';
import { X, Copy, Check, Share2, Download, Lock, ExternalLink } from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { VybeAvatar } from '../components/OptimizedImage';
import { OfficialBadge } from '../components/Badges';

const CANONICAL_WEB_ORIGIN = 'https://vybeonline.netlify.app';

export function buildCanonicalWebProfileUrl(user: { id: string; username?: string }): string {
  const cleanUser = (user.username || '').trim().replace(/^@/, '').replace(/[^a-zA-Z0-9._]/g, '');
  if (cleanUser) {
    return `${CANONICAL_WEB_ORIGIN}/u/${encodeURIComponent(cleanUser)}`;
  }
  return `${CANONICAL_WEB_ORIGIN}/profile/${encodeURIComponent(user.id)}`;
}

export const QrCodeModal: React.FC = () => {
  const { currentUser, viewedProfileUser, closeModal } = useVybe();
  const targetUser = viewedProfileUser || currentUser;

  const [qrDataUrl, setQrDataUrl] = useState<string>('');
  const [qrError, setQrError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const profileUrl = targetUser ? buildCanonicalWebProfileUrl(targetUser) : CANONICAL_WEB_ORIGIN;

  useEffect(() => {
    let cancelled = false;
    if (!targetUser) return;
    setQrError(null);
    QRCode.toDataURL(profileUrl, {
      errorCorrectionLevel: 'H',
      margin: 2,
      width: 320,
      color: {
        dark: '#0A0F1D',
        light: '#FFFFFF'
      }
    })
      .then((dataUrl) => {
        if (!cancelled) setQrDataUrl(dataUrl);
      })
      .catch(() => {
        if (!cancelled) setQrError('Could not render scannable QR code.');
      });
    return () => {
      cancelled = true;
    };
  }, [profileUrl, targetUser]);

  if (!targetUser) return null;

  const handleCopy = async () => {
    try {
      await navigator.clipboard?.writeText(profileUrl);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // ignore clipboard denial
    }
  };

  const handleNativeShare = async () => {
    if (navigator.share) {
      try {
        await navigator.share({
          title: `${targetUser.displayName} (@${targetUser.username}) on VYBE`,
          text: `Connect with @${targetUser.username} on VYBE`,
          url: profileUrl
        });
        return;
      } catch {
        // user cancelled native share
      }
    }
    await handleCopy();
  };

  const handleDownloadPng = () => {
    if (!qrDataUrl) return;
    const link = document.createElement('a');
    link.href = qrDataUrl;
    link.download = `vybe-${targetUser.username || targetUser.id}-qr.png`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-xl animate-fade-in"
      onClick={closeModal}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Share Profile QR Code"
        onClick={(e) => e.stopPropagation()}
        className="relative w-full max-w-sm bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-3xl shadow-2xl p-6 text-[var(--vybe-text)] text-center space-y-4"
      >
        <div className="flex items-center justify-between pb-2 border-b border-[var(--vybe-border)]">
          <h3 className="font-bold text-sm text-[var(--vybe-text)]">Share Profile QR</h3>
          <button
            type="button"
            onClick={closeModal}
            aria-label="Close QR dialog"
            className="p-1.5 rounded-lg text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)] hover:bg-[var(--vybe-elevated)]"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* User identity strip */}
        <div className="flex items-center justify-center gap-2.5">
          <VybeAvatar
            src={targetUser.avatarUrl}
            name={targetUser.displayName}
            username={targetUser.username}
            className="w-10 h-10 rounded-full"
          />
          <div className="text-left min-w-0">
            <div className="flex items-center gap-1">
              <h4 className="font-bold text-sm text-[var(--vybe-text)] truncate">{targetUser.displayName}</h4>
              {targetUser.badges?.map(b => (
                <OfficialBadge key={b} badge={b} size="sm" />
              ))}
            </div>
            <p className="text-xs text-[var(--vybe-text-secondary)] truncate">@{targetUser.username}</p>
          </div>
        </div>

        {/* Real Scannable QR Container */}
        <div className="p-4 bg-white rounded-3xl shadow-xl flex flex-col items-center justify-center mx-auto w-60 h-60 border-4 border-[var(--vybe-accent)]">
          {qrError ? (
            <p className="text-xs text-red-600 font-semibold px-3">{qrError}</p>
          ) : qrDataUrl ? (
            <img
              src={qrDataUrl}
              alt={`Scannable QR code for @${targetUser.username}`}
              className="w-52 h-52 object-contain"
            />
          ) : (
            <div className="w-52 h-52 flex items-center justify-center text-xs text-slate-500">
              Generating QR...
            </div>
          )}
        </div>

        <div className="space-y-1.5">
          <a
            href={profileUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-1 text-[11px] font-mono text-[var(--vybe-accent)] hover:underline break-all"
          >
            <span>{profileUrl}</span>
            <ExternalLink className="w-3 h-3 shrink-0" />
          </a>
          {targetUser.isPrivate && (
            <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-amber-500/10 border border-amber-500/30 text-[10px] font-semibold text-amber-500">
              <Lock className="w-3 h-3" />
              <span>Private account: visitors can view bio, approved followers see posts</span>
            </div>
          )}
        </div>

        <div className="flex items-center gap-2 pt-1">
          <button
            type="button"
            onClick={handleCopy}
            className="flex-1 py-2.5 rounded-xl bg-[var(--vybe-elevated)] hover:border-[var(--vybe-accent)] border border-[var(--vybe-border)] text-xs font-bold text-[var(--vybe-text)] flex items-center justify-center gap-1.5 transition-colors"
          >
            {copied ? <Check className="w-4 h-4 text-emerald-500" /> : <Copy className="w-4 h-4" />}
            <span>{copied ? 'Copied!' : 'Copy Link'}</span>
          </button>
          <button
            type="button"
            onClick={handleDownloadPng}
            disabled={!qrDataUrl}
            aria-label="Download QR PNG"
            title="Download QR PNG"
            className="p-2.5 rounded-xl bg-[var(--vybe-elevated)] hover:border-[var(--vybe-accent)] border border-[var(--vybe-border)] text-[var(--vybe-text)] disabled:opacity-40 transition-colors"
          >
            <Download className="w-4 h-4" />
          </button>
          <button
            type="button"
            onClick={handleNativeShare}
            aria-label="Share Profile"
            title="Share Profile"
            className="p-2.5 rounded-xl bg-[var(--vybe-accent)] text-white keep-white shadow hover:opacity-95 transition-opacity"
          >
            <Share2 className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
};
