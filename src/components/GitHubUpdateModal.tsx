import React, { useState } from 'react';
import {
  RefreshCw, Download, ExternalLink, CheckCircle2,
  AlertTriangle, RotateCcw, X, GitCommit, PlayCircle, ShieldCheck, Sparkles
} from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import {
  DEFAULT_GITHUB_REPO, INSTALLED_VERSION, INSTALLED_VERSION_CODE,
  formatFileSize
} from '../services/githubUpdater';

export const GitHubUpdateModal: React.FC = () => {
  const {
    githubRepo, setGithubRepo, updateInfo, isCheckingUpdates,
    updateError, checkForUpdates, resetGithubRepo, closeModal
  } = useVybe();

  const [inputRepo, setInputRepo] = useState(githubRepo);

  const handleCheck = () => {
    if (inputRepo.trim()) {
      setGithubRepo(inputRepo.trim());
      checkForUpdates(inputRepo.trim());
    }
  };

  const handleReset = () => {
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
        aria-label="VYBE Software Update"
        onClick={(e) => e.stopPropagation()}
        className="relative w-full max-w-xl max-h-[90vh] overflow-y-auto bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-2xl shadow-2xl p-6 text-[var(--vybe-text)] no-scrollbar"
      >
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-[var(--vybe-border)]">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-xl bg-[var(--vybe-accent-soft)] border border-[var(--vybe-border)] text-[var(--vybe-accent)]">
              <Sparkles className="w-6 h-6" />
            </div>
            <div>
              <h2 className="text-lg font-bold brand-font text-[var(--vybe-text)]">VYBE Software Update</h2>
              <p className="text-xs text-[var(--vybe-text-secondary)]">
                Installed: <span className="text-[var(--vybe-accent)] font-semibold">v{INSTALLED_VERSION}</span> (build {INSTALLED_VERSION_CODE})
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={closeModal}
            aria-label="Close updates modal"
            className="p-2 text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)] rounded-lg hover:bg-[var(--vybe-elevated)] transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Repository Input Section */}
        <div className="mt-5 space-y-2.5">
          <label className="text-xs font-semibold text-[var(--vybe-text-secondary)]">
            GitHub Release Repository (owner/repo)
          </label>
          <div className="flex items-center gap-2">
            <input
              type="text"
              value={inputRepo}
              onChange={(e) => setInputRepo(e.target.value)}
              placeholder={DEFAULT_GITHUB_REPO}
              className="flex-1 px-4 py-2.5 bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] rounded-xl text-sm text-[var(--vybe-text)] focus:outline-none focus:border-[var(--vybe-accent)] transition-colors"
            />
            {inputRepo !== DEFAULT_GITHUB_REPO && (
              <button
                type="button"
                onClick={handleReset}
                title="Reset to official repo"
                className="p-2.5 bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] rounded-xl text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)] transition-colors"
              >
                <RotateCcw className="w-4 h-4" />
              </button>
            )}
            <button
              type="button"
              onClick={handleCheck}
              disabled={isCheckingUpdates || !inputRepo.trim()}
              className="flex items-center gap-2 px-4 py-2.5 bg-[var(--vybe-accent)] hover:opacity-95 disabled:opacity-50 text-white keep-white text-sm font-semibold rounded-xl shadow-sm transition-all"
            >
              <RefreshCw className={`w-4 h-4 ${isCheckingUpdates ? 'animate-spin' : ''}`} />
              <span>{isCheckingUpdates ? 'Checking...' : 'Check'}</span>
            </button>
          </div>
        </div>

        {/* Error State */}
        {updateError && (
          <div className="mt-4 p-4 rounded-xl bg-red-500/10 border border-red-500/30 text-red-500 text-xs flex items-start gap-2.5">
            <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
            <div>
              <p className="font-semibold">Update Check Notice</p>
              <p className="mt-0.5 opacity-90">{updateError}</p>
            </div>
          </div>
        )}

        {/* Release Status Result */}
        {updateInfo && !isCheckingUpdates && (
          <div className="mt-5 space-y-4">
            <div
              className={`p-4 rounded-xl border ${
                updateInfo.isNewerThanInstalled
                  ? 'bg-emerald-500/12 border-emerald-500/30 text-emerald-600 dark:text-emerald-300'
                  : 'bg-[var(--vybe-accent-soft)] border-[var(--vybe-accent)]/30 text-[var(--vybe-text)]'
              }`}
            >
              <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <CheckCircle2 className="w-5 h-5 shrink-0 text-[var(--vybe-accent)]" />
                  <div>
                    <h3 className="font-bold text-sm">
                      {updateInfo.isNewerThanInstalled
                        ? `Update Available: ${updateInfo.tagName}`
                        : updateInfo.isOlderThanInstalled
                          ? `Installed Version is Newer (v${INSTALLED_VERSION} > ${updateInfo.tagName})`
                          : `VYBE is Up to Date (${updateInfo.tagName})`}
                    </h3>
                    <p className="text-xs opacity-80 mt-0.5">
                      {updateInfo.name !== updateInfo.tagName ? updateInfo.name : 'Latest verified release on GitHub'}
                    </p>
                  </div>
                </div>
                <span className="text-[11px] px-2 py-0.5 rounded-full bg-[var(--vybe-surface)] border border-[var(--vybe-border)] font-mono">
                  {updateInfo.tagName}
                </span>
              </div>

              {(updateInfo.latestCommitShortSha || updateInfo.latestActionRunNumber) && (
                <div className="flex flex-wrap items-center gap-3 mt-3 pt-2.5 border-t border-[var(--vybe-border)] text-[11px] text-[var(--vybe-text-secondary)]">
                  {updateInfo.latestCommitShortSha && (
                    <span className="flex items-center gap-1">
                      <GitCommit className="w-3.5 h-3.5 text-[var(--vybe-accent)]" />
                      Commit:{' '}
                      <code className="text-[var(--vybe-text)] font-mono bg-[var(--vybe-surface)] px-1.5 py-0.5 rounded">
                        {updateInfo.latestCommitShortSha}
                      </code>
                    </span>
                  )}
                  {updateInfo.latestActionRunNumber && (
                    <span className="flex items-center gap-1">
                      <PlayCircle className="w-3.5 h-3.5 text-emerald-500" />
                      GitHub Actions:{' '}
                      <strong className="text-[var(--vybe-text)]">Build #{updateInfo.latestActionRunNumber}</strong>
                    </span>
                  )}
                </div>
              )}
            </div>

            {updateInfo.apkFileName && (
              <div className="p-3.5 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] flex items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                  <div className="p-2 rounded-lg bg-[var(--vybe-accent-soft)] text-[var(--vybe-accent)]">
                    <ShieldCheck className="w-5 h-5" />
                  </div>
                  <div>
                    <p className="text-xs font-semibold text-[var(--vybe-text)]">{updateInfo.apkFileName}</p>
                    <p className="text-[11px] text-[var(--vybe-text-secondary)]">
                      Signed Release · {formatFileSize(updateInfo.apkFileSize)}
                    </p>
                  </div>
                </div>
                {updateInfo.downloadUrl && (
                  <a
                    href={updateInfo.downloadUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center gap-1.5 px-3 py-1.5 bg-[var(--vybe-accent)] text-white keep-white text-xs font-semibold rounded-lg shadow-sm hover:opacity-95"
                  >
                    <Download className="w-3.5 h-3.5" />
                    <span>Download</span>
                  </a>
                )}
              </div>
            )}

            <div className="space-y-1.5">
              <h4 className="text-xs font-semibold text-[var(--vybe-text-secondary)]">Release Notes</h4>
              <div className="p-3.5 bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] rounded-xl text-xs text-[var(--vybe-text-secondary)] leading-relaxed max-h-48 overflow-y-auto whitespace-pre-wrap font-sans">
                {updateInfo.body}
              </div>
            </div>

            <div className="flex items-center gap-3 pt-2">
              <a
                href={updateInfo.htmlUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="flex-1 flex items-center justify-center gap-2 py-2.5 bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] hover:border-[var(--vybe-accent)] text-[var(--vybe-text)] text-xs font-semibold rounded-xl transition-colors"
              >
                <ExternalLink className="w-3.5 h-3.5" />
                <span>View on GitHub</span>
              </a>
              {updateInfo.downloadUrl && (
                <a
                  href={updateInfo.downloadUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex-1 flex items-center justify-center gap-2 py-2.5 bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold rounded-xl shadow-sm hover:opacity-95 transition-opacity"
                >
                  <Download className="w-4 h-4" />
                  <span>{updateInfo.isNewerThanInstalled ? 'Download Update' : 'Get Latest Build'}</span>
                </a>
              )}
            </div>
          </div>
        )}

        {/* Workflow Info Footer */}
        <div className="mt-6 pt-4 border-t border-[var(--vybe-border)] text-center text-[11px] text-[var(--vybe-text-muted)]">
          <p>
            Builds are compiled by GitHub Actions workflow{' '}
            <code className="text-[var(--vybe-accent)]">build-apk.yml</code> on push.
          </p>
        </div>
      </div>
    </div>
  );
};
