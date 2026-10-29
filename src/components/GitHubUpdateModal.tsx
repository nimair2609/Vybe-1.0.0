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
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-fade-in">
      <div className="relative w-full max-w-xl max-h-[90vh] overflow-y-auto bg-[#101322] border border-[#262E4A] rounded-2xl shadow-2xl p-6 text-white no-scrollbar">
        
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-[#262E4A]">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-xl bg-gradient-to-tr from-[#8B5CF6]/20 to-[#00C6FF]/20 border border-[#8B5CF6]/30 text-[#00C6FF]">
              <Sparkles className="w-6 h-6" />
            </div>
            <div>
              <h2 className="text-lg font-bold brand-font">VYBE Software Update</h2>
              <p className="text-xs text-gray-400">
                Installed: <span className="text-[#00C6FF] font-semibold">v{INSTALLED_VERSION}</span> (build {INSTALLED_VERSION_CODE}) • GitHub In-App Updater
              </p>
            </div>
          </div>
          <button 
            onClick={closeModal}
            className="p-2 text-gray-400 hover:text-white rounded-lg hover:bg-[#1A1F36] transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Repository Input Section */}
        <div className="mt-5 space-y-3">
          <label className="text-xs font-semibold text-gray-300">
            GitHub Release Repository (owner/repo)
          </label>
          <div className="flex items-center gap-2">
            <input 
              type="text" 
              value={inputRepo}
              onChange={(e) => setInputRepo(e.target.value)}
              placeholder={DEFAULT_GITHUB_REPO}
              className="flex-1 px-4 py-2.5 bg-[#060714] border border-[#262E4A] rounded-xl text-sm focus:outline-none focus:border-[#8B5CF6] transition-colors"
            />
            {inputRepo !== DEFAULT_GITHUB_REPO && (
              <button
                onClick={handleReset}
                title="Reset to official repo"
                className="p-2.5 bg-[#1A1F36] hover:bg-[#262E4A] rounded-xl text-gray-300 hover:text-white transition-colors"
              >
                <RotateCcw className="w-4 h-4" />
              </button>
            )}
            <button
              onClick={handleCheck}
              disabled={isCheckingUpdates || !inputRepo.trim()}
              className="flex items-center gap-2 px-4 py-2.5 bg-gradient-to-r from-[#8B5CF6] to-[#00C6FF] hover:opacity-95 disabled:opacity-50 text-white text-sm font-semibold rounded-xl shadow-lg transition-all"
            >
              <RefreshCw className={`w-4 h-4 ${isCheckingUpdates ? 'animate-spin' : ''}`} />
              <span>{isCheckingUpdates ? 'Checking...' : 'Check'}</span>
            </button>
          </div>
        </div>

        {/* Error State */}
        {updateError && (
          <div className="mt-4 p-4 rounded-xl bg-red-500/10 border border-red-500/30 text-red-200 text-xs flex items-start gap-2.5">
            <AlertTriangle className="w-4 h-4 text-red-400 shrink-0 mt-0.5" />
            <div>
              <p className="font-semibold">Update Check Notice</p>
              <p className="mt-0.5 opacity-90">{updateError}</p>
            </div>
          </div>
        )}

        {/* Release Status Result */}
        {updateInfo && !isCheckingUpdates && (
          <div className="mt-5 space-y-4">
            <div className={`p-4 rounded-xl border ${
              updateInfo.isNewerThanInstalled 
                ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-200' 
                : updateInfo.isOlderThanInstalled
                  ? 'bg-purple-500/10 border-purple-500/30 text-purple-200'
                  : 'bg-cyan-500/10 border-cyan-500/30 text-cyan-200'
            }`}>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <CheckCircle2 className="w-5 h-5 shrink-0" />
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
                <span className="text-[11px] px-2 py-0.5 rounded-full bg-white/10 font-mono">
                  {updateInfo.tagName}
                </span>
              </div>

              {/* Commit & Build Metadata */}
              {(updateInfo.latestCommitShortSha || updateInfo.latestActionRunNumber) && (
                <div className="flex flex-wrap items-center gap-3 mt-3 pt-2.5 border-t border-white/10 text-[11px] text-gray-300">
                  {updateInfo.latestCommitShortSha && (
                    <span className="flex items-center gap-1">
                      <GitCommit className="w-3.5 h-3.5 text-[#00C6FF]" />
                      Commit: <code className="text-white font-mono bg-[#060714] px-1.5 py-0.5 rounded">{updateInfo.latestCommitShortSha}</code>
                    </span>
                  )}
                  {updateInfo.latestActionRunNumber && (
                    <span className="flex items-center gap-1">
                      <PlayCircle className="w-3.5 h-3.5 text-[#22C55E]" />
                      GitHub Actions: <strong className="text-white">Build #{updateInfo.latestActionRunNumber}</strong>
                    </span>
                  )}
                </div>
              )}
            </div>

            {/* APK Asset details if present */}
            {updateInfo.apkFileName && (
              <div className="p-3.5 rounded-xl bg-[#060714] border border-[#262E4A] flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="p-2 rounded-lg bg-[#8B5CF6]/20 text-[#8B5CF6]">
                    <ShieldCheck className="w-5 h-5" />
                  </div>
                  <div>
                    <p className="text-xs font-semibold text-white">{updateInfo.apkFileName}</p>
                    <p className="text-[11px] text-gray-400">
                      Signed Release • {formatFileSize(updateInfo.apkFileSize)}
                    </p>
                  </div>
                </div>
                {updateInfo.downloadUrl && (
                  <a
                    href={updateInfo.downloadUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center gap-1.5 px-3 py-1.5 bg-[#8B5CF6] hover:bg-[#7C3AED] text-white text-xs font-semibold rounded-lg shadow transition-colors"
                  >
                    <Download className="w-3.5 h-3.5" />
                    <span>Download</span>
                  </a>
                )}
              </div>
            )}

            {/* Release Notes */}
            <div className="space-y-1.5">
              <h4 className="text-xs font-semibold text-gray-300">Release Notes</h4>
              <div className="p-3.5 bg-[#060714] border border-[#262E4A] rounded-xl text-xs text-gray-300 leading-relaxed max-h-48 overflow-y-auto whitespace-pre-wrap font-sans">
                {updateInfo.body}
              </div>
            </div>

            {/* Action buttons */}
            <div className="flex items-center gap-3 pt-2">
              <a
                href={updateInfo.htmlUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="flex-1 flex items-center justify-center gap-2 py-2.5 bg-[#1A1F36] hover:bg-[#262E4A] text-white text-xs font-semibold rounded-xl transition-colors"
              >
                <ExternalLink className="w-3.5 h-3.5" />
                <span>View on GitHub</span>
              </a>
              {updateInfo.downloadUrl && (
                <a
                  href={updateInfo.downloadUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex-1 flex items-center justify-center gap-2 py-2.5 bg-gradient-to-r from-[#8B5CF6] to-[#00C6FF] text-white text-xs font-bold rounded-xl shadow-lg hover:opacity-95 transition-opacity"
                >
                  <Download className="w-4 h-4" />
                  <span>{updateInfo.isNewerThanInstalled ? 'Download Update' : 'Get Latest Build'}</span>
                </a>
              )}
            </div>
          </div>
        )}

        {/* Workflow Info Footer */}
        <div className="mt-6 pt-4 border-t border-[#262E4A] text-center text-[11px] text-gray-400">
          <p>
            Builds are automatically compiled and published by GitHub Actions workflow <code className="text-[#00C6FF]">build-apk.yml</code> on push.
          </p>
        </div>

      </div>
    </div>
  );
};
