import React, { useState, useEffect } from 'react';
import {
  Settings, X, Moon, Sun, Bell, Shield,
  RefreshCw, CheckCircle2, RotateCcw, AlertTriangle,
  Sparkles, HardDrive, Trash2, Smartphone, ExternalLink
} from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import {
  DEFAULT_GITHUB_REPO, INSTALLED_VERSION, INSTALLED_VERSION_CODE
} from '../services/githubUpdater';
import { isVerifiedAdminSession } from '../services/supabaseService';
import {
  WebCacheStats,
  getWebCacheStats,
  clearWebImageCache,
  clearWebVideoCache,
  clearWebTempCache,
  clearAllWebDisposableCache,
  formatWebBytes
} from '../utils/cacheManager';

export const SettingsModal: React.FC = () => {
  const {
    closeModal, currentUser, githubRepo, setGithubRepo,
    updateInfo, isCheckingUpdates, updateError, checkForUpdates,
    resetGithubRepo, openModal, themeMode, setThemeMode
  } = useVybe();

  const [inputRepo, setInputRepo] = useState(githubRepo);
  const [notificationsEnabled, setNotificationsEnabled] = useState(true);
  const [cacheStats, setCacheStats] = useState<WebCacheStats | null>(null);
  const [cacheStatusMessage, setCacheStatusMessage] = useState<string | null>(null);

  useEffect(() => {
    getWebCacheStats().then(setCacheStats).catch(() => {});
  }, []);

  const handleUpdateCheck = () => {
    if (inputRepo.trim()) {
      setGithubRepo(inputRepo.trim());
      checkForUpdates(inputRepo.trim());
    }
  };

  const handleResetRepo = () => {
    setInputRepo(DEFAULT_GITHUB_REPO);
    resetGithubRepo();
    checkForUpdates(DEFAULT_GITHUB_REPO);
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-fade-in"
      onClick={closeModal}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label="VYBE Settings"
        onClick={(e) => e.stopPropagation()}
        className="relative w-full max-w-xl max-h-[90vh] overflow-y-auto bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-3xl shadow-2xl p-6 text-[var(--vybe-text)] no-scrollbar space-y-5"
      >
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-[var(--vybe-border)]">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[var(--vybe-text)]">
              <Settings className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold brand-font text-[var(--vybe-text)]">VYBE Settings</h2>
              <p className="text-xs text-[var(--vybe-text-secondary)]">
                Appearance, cache storage, updates & account verification
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={closeModal}
            aria-label="Close settings"
            className="p-2 text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)] hover:bg-[var(--vybe-elevated)] rounded-lg"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Appearance / Theme Mode */}
        <div className="p-4 rounded-2xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] space-y-3">
          <h4 className="text-xs font-bold text-[var(--vybe-text-secondary)] uppercase tracking-wider">
            Appearance
          </h4>
          <div className="grid grid-cols-2 gap-2.5">
            <button
              type="button"
              onClick={() => setThemeMode('dark')}
              className={`flex items-center justify-center gap-2 py-2.5 px-3 rounded-xl border text-xs font-bold transition-all ${
                themeMode === 'dark'
                  ? 'bg-[var(--vybe-accent-soft)] border-[var(--vybe-accent)] text-[var(--vybe-accent)]'
                  : 'bg-[var(--vybe-surface)] border-[var(--vybe-border)] text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)]'
              }`}
            >
              <Moon className="w-4 h-4" />
              <span>Dark Mode</span>
            </button>
            <button
              type="button"
              onClick={() => setThemeMode('light')}
              className={`flex items-center justify-center gap-2 py-2.5 px-3 rounded-xl border text-xs font-bold transition-all ${
                themeMode === 'light'
                  ? 'bg-[var(--vybe-accent-soft)] border-[var(--vybe-accent)] text-[var(--vybe-accent)]'
                  : 'bg-[var(--vybe-surface)] border-[var(--vybe-border)] text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)]'
              }`}
            >
              <Sun className="w-4 h-4" />
              <span>Light Mode</span>
            </button>
          </div>
        </div>

        {/* GitHub Update Section Card */}
        <div className="p-4 sm:p-5 rounded-2xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] space-y-4">
          <div className="flex items-center justify-between gap-2">
            <div className="flex items-center gap-3">
              <div className="p-2.5 rounded-xl bg-[var(--vybe-accent-soft)] text-[var(--vybe-accent)]">
                <RefreshCw className={`w-5 h-5 ${isCheckingUpdates ? 'animate-spin' : ''}`} />
              </div>
              <div>
                <h3 className="font-bold text-sm text-[var(--vybe-text)]">VYBE Software Update</h3>
                <p className="text-xs text-[var(--vybe-text-secondary)]">
                  Installed: <span className="text-[var(--vybe-accent)] font-semibold">v{INSTALLED_VERSION}</span>{' '}
                  (build {INSTALLED_VERSION_CODE})
                </p>
              </div>
            </div>
            <span className="text-[10px] uppercase tracking-wider px-2.5 py-0.5 rounded-full bg-emerald-500/12 border border-emerald-500/30 text-emerald-600 dark:text-emerald-400 font-bold">
              GitHub Sync
            </span>
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-[var(--vybe-text-secondary)]">
              GitHub Release Repository
            </label>
            <div className="flex items-center gap-2">
              <input
                type="text"
                value={inputRepo}
                onChange={(e) => setInputRepo(e.target.value)}
                placeholder={DEFAULT_GITHUB_REPO}
                className="flex-1 px-3.5 py-2 bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-xl text-xs text-[var(--vybe-text)] focus:outline-none focus:border-[var(--vybe-accent)]"
              />
              {inputRepo !== DEFAULT_GITHUB_REPO && (
                <button
                  type="button"
                  onClick={handleResetRepo}
                  title="Reset to official repo"
                  className="p-2 bg-[var(--vybe-surface)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] rounded-xl text-[var(--vybe-text-secondary)] transition-colors"
                >
                  <RotateCcw className="w-3.5 h-3.5" />
                </button>
              )}
              <button
                type="button"
                onClick={handleUpdateCheck}
                disabled={isCheckingUpdates || !inputRepo.trim()}
                className="px-4 py-2 bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold rounded-xl shadow-sm disabled:opacity-50 hover:opacity-95"
              >
                {isCheckingUpdates ? 'Checking...' : 'Check Updates'}
              </button>
            </div>
          </div>

          {updateError && (
            <div className="p-3 rounded-xl bg-red-500/10 border border-red-500/30 text-red-500 text-xs flex items-center gap-2">
              <AlertTriangle className="w-4 h-4 shrink-0" />
              <span>{updateError}</span>
            </div>
          )}

          {updateInfo && !isCheckingUpdates && (
            <div className="p-3.5 rounded-xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] space-y-2.5">
              <div className="flex items-center justify-between text-xs">
                <span className="font-bold text-[var(--vybe-text)]">
                  {updateInfo.isNewerThanInstalled ? 'New Version Found' : 'Latest GitHub Release'}
                </span>
                <span className="font-mono text-[var(--vybe-accent)] font-bold">{updateInfo.tagName}</span>
              </div>
              <p className="text-[11px] text-[var(--vybe-text-secondary)] line-clamp-2">{updateInfo.body}</p>

              <div className="flex items-center gap-2 pt-1">
                <a
                  href={updateInfo.htmlUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex-1 py-2 px-3 bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-[var(--vybe-text)] text-xs font-semibold rounded-lg text-center hover:border-[var(--vybe-accent)] transition-colors"
                >
                  Release Notes
                </a>
                {updateInfo.downloadUrl && (
                  <a
                    href={updateInfo.downloadUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex-1 py-2 px-3 bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold rounded-lg text-center shadow-sm hover:opacity-95"
                  >
                    Download APK / Asset
                  </a>
                )}
              </div>
            </div>
          )}
        </div>

        {/* Notification Settings */}
        <div className="p-4 rounded-2xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] space-y-3">
          <h4 className="text-xs font-bold text-[var(--vybe-text-secondary)] uppercase tracking-wider">
            Notifications
          </h4>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <Bell className="w-4 h-4 text-[var(--vybe-accent)]" />
              <div>
                <p className="text-xs font-semibold text-[var(--vybe-text)]">In-App Notification Banners</p>
                <p className="text-[10px] text-[var(--vybe-text-secondary)]">
                  Direct messages, reactions & release alerts
                </p>
              </div>
            </div>
            <input
              type="checkbox"
              checked={notificationsEnabled}
              onChange={(e) => setNotificationsEnabled(e.target.checked)}
              className="w-4 h-4 accent-[var(--vybe-accent)]"
            />
          </div>
        </div>

        {/* Storage & Cache Reset Manager */}
        <div className="p-4 rounded-2xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] space-y-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <HardDrive className="w-4 h-4 text-[var(--vybe-accent)]" />
              <div>
                <h4 className="text-xs font-bold text-[var(--vybe-text)] uppercase tracking-wider">
                  Storage & Cache Manager
                </h4>
                <p className="text-[10px] text-[var(--vybe-text-secondary)]">
                  Disposable browser caches only — Supabase data & Auth stay safe
                </p>
              </div>
            </div>
            <span className="text-xs font-bold text-[var(--vybe-accent)]">
              {cacheStats ? formatWebBytes(cacheStats.totalDisposableBytes) : '...'}
            </span>
          </div>

          {cacheStats && (
            <div className="grid grid-cols-3 gap-2 text-center">
              <div className="p-2 rounded-xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)]">
                <span className="block text-[10px] text-[var(--vybe-text-secondary)]">
                  Images ({cacheStats.imageCachedCount})
                </span>
                <span className="text-xs font-bold text-[var(--vybe-text)]">
                  {formatWebBytes(cacheStats.imageCacheBytes)}
                </span>
              </div>
              <div className="p-2 rounded-xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)]">
                <span className="block text-[10px] text-[var(--vybe-text-secondary)]">
                  Videos ({cacheStats.videoCachedCount})
                </span>
                <span className="text-xs font-bold text-[var(--vybe-text)]">
                  {formatWebBytes(cacheStats.videoCacheBytes)}
                </span>
              </div>
              <div className="p-2 rounded-xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)]">
                <span className="block text-[10px] text-[var(--vybe-text-secondary)]">
                  Temp / DB ({cacheStats.indexedDbKeysCount})
                </span>
                <span className="text-xs font-bold text-[var(--vybe-text)]">
                  {formatWebBytes(cacheStats.tempCacheBytes)}
                </span>
              </div>
            </div>
          )}

          <div className="grid grid-cols-2 gap-2">
            <button
              type="button"
              onClick={async () => {
                const next = await clearWebImageCache();
                setCacheStats(next);
                setCacheStatusMessage('Browser image cache cleared.');
              }}
              className="py-2 px-3 rounded-xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] text-xs font-semibold text-[var(--vybe-text)] transition-colors"
            >
              Clear Image Cache
            </button>
            <button
              type="button"
              onClick={async () => {
                const next = await clearWebVideoCache();
                setCacheStats(next);
                setCacheStatusMessage('Video cache cleared.');
              }}
              className="py-2 px-3 rounded-xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] text-xs font-semibold text-[var(--vybe-text)] transition-colors"
            >
              Clear Video Cache
            </button>
            <button
              type="button"
              onClick={async () => {
                const next = await clearWebTempCache();
                setCacheStats(next);
                setCacheStatusMessage('Temporary query & preview caches reset.');
              }}
              className="py-2 px-3 rounded-xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] text-xs font-semibold text-[var(--vybe-text)] transition-colors"
            >
              Clear Temp Cache
            </button>
            <button
              type="button"
              onClick={async () => {
                const next = await clearAllWebDisposableCache();
                setCacheStats(next);
                setCacheStatusMessage('All disposable caches cleared & reinitialized.');
              }}
              className="py-2 px-3 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold flex items-center justify-center gap-1.5 hover:opacity-95"
            >
              <Trash2 className="w-3.5 h-3.5" />
              <span>Clear All Cache</span>
            </button>
          </div>

          {cacheStatusMessage && (
            <div className="p-2.5 rounded-xl bg-emerald-500/12 border border-emerald-500/30 text-emerald-600 dark:text-emerald-400 text-xs flex items-center gap-2">
              <CheckCircle2 className="w-4 h-4 shrink-0" />
              <span>{cacheStatusMessage}</span>
            </div>
          )}
        </div>

        {/* Verification & Badges Shortcut */}
        <div className="p-4 rounded-2xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] flex items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <Shield className="w-4 h-4 text-amber-500 shrink-0" />
            <div>
              <p className="text-xs font-semibold text-[var(--vybe-text)]">Official Verification Badges</p>
              <p className="text-[10px] text-[var(--vybe-text-secondary)]">
                Blue Verified, VIP Gold, Shop Gold & Worker
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={() => {
              closeModal();
              openModal('VERIFICATION_CENTER');
            }}
            className="px-3.5 py-1.5 rounded-xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] text-xs font-bold text-[var(--vybe-text)] transition-colors shrink-0"
          >
            Apply
          </button>
        </div>

        {/* Android App Download Link */}
        <div className="p-4 rounded-2xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] flex items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <Smartphone className="w-4 h-4 text-[var(--vybe-accent)] shrink-0" />
            <div>
              <p className="text-xs font-semibold text-[var(--vybe-text)]">VYBE Android App</p>
              <p className="text-[10px] text-[var(--vybe-text-secondary)]">
                Download the latest signed Android APK release
              </p>
            </div>
          </div>
          <a
            href="/download"
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] text-xs font-bold text-[var(--vybe-text)] transition-colors shrink-0"
          >
            <span>Get APK</span>
            <ExternalLink className="w-3 h-3" />
          </a>
        </div>

        {/* Admin Console Shortcut (Verified Admin Session Only) */}
        {isVerifiedAdminSession(currentUser) && (
          <div className="p-4 rounded-2xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] flex items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <Sparkles className="w-4 h-4 text-[var(--vybe-accent)] shrink-0" />
              <div>
                <p className="text-xs font-semibold text-[var(--vybe-text)]">Admin Command Center</p>
                <p className="text-[10px] text-[var(--vybe-text-secondary)]">
                  Review verifications, users, content & reports
                </p>
              </div>
            </div>
            <div className="flex items-center gap-2 shrink-0">
              <button
                type="button"
                onClick={() => {
                  closeModal();
                  openModal('ADMIN_CONSOLE');
                }}
                className="px-3 py-1.5 rounded-xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] text-xs font-bold text-[var(--vybe-text)] transition-colors"
              >
                Quick View
              </button>
              <a
                href="/admin"
                className="px-3 py-1.5 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold hover:opacity-95"
              >
                Full Portal
              </a>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
