import React, { useState } from 'react';
import { 
  Settings, X, Moon, Sun, Bell, Shield, 
  RefreshCw, CheckCircle2, RotateCcw, ExternalLink, Download, 
  AlertTriangle, Lock, LogOut, Sparkles 
} from 'lucide-react';
import { useVybe } from '../context/VybeContext';
import { 
  DEFAULT_GITHUB_REPO, INSTALLED_VERSION, INSTALLED_VERSION_CODE, 
  formatFileSize 
} from '../services/githubUpdater';

export const SettingsModal: React.FC = () => {
  const { 
    closeModal, currentUser, githubRepo, setGithubRepo, 
    updateInfo, isCheckingUpdates, updateError, checkForUpdates, 
    resetGithubRepo, openModal 
  } = useVybe();

  const [inputRepo, setInputRepo] = useState(githubRepo);
  const [notificationsEnabled, setNotificationsEnabled] = useState(true);

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
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-fade-in">
      <div className="relative w-full max-w-xl max-h-[90vh] overflow-y-auto bg-[#101322] border border-[#262E4A] rounded-3xl shadow-2xl p-6 text-white no-scrollbar space-y-6">
        
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-[#262E4A]">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-xl bg-slate-800 text-gray-200">
              <Settings className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold brand-font text-white">VYBE Settings</h2>
              <p className="text-xs text-gray-400">Software update, notifications & privacy</p>
            </div>
          </div>
          <button onClick={closeModal} className="p-2 text-gray-400 hover:text-white rounded-lg">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* --- GITHUB UPDATE SECTION CARD (Preserving GitHubUpdateSectionCard) --- */}
        <div className="p-5 rounded-2xl bg-[#060714] border border-[#8B5CF6]/40 shadow-xl space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="p-2.5 rounded-xl bg-[#8B5CF6]/20 text-[#00C6FF]">
                <RefreshCw className={`w-5 h-5 ${isCheckingUpdates ? 'animate-spin' : ''}`} />
              </div>
              <div>
                <h3 className="font-bold text-sm text-white">VYBE Software Update</h3>
                <p className="text-xs text-gray-400">
                  Installed: <span className="text-[#00C6FF] font-semibold">v{INSTALLED_VERSION}</span> (build {INSTALLED_VERSION_CODE})
                </p>
              </div>
            </div>
            <span className="text-[10px] uppercase tracking-wider px-2 py-0.5 rounded-full bg-emerald-500/10 border border-emerald-500/30 text-emerald-300 font-bold">
              GitHub Sync
            </span>
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-gray-300">
              GitHub Release Repository
            </label>
            <div className="flex items-center gap-2">
              <input 
                type="text" 
                value={inputRepo}
                onChange={(e) => setInputRepo(e.target.value)}
                placeholder={DEFAULT_GITHUB_REPO}
                className="flex-1 px-3.5 py-2 bg-[#101322] border border-[#262E4A] rounded-xl text-xs text-white focus:outline-none focus:border-[#8B5CF6]"
              />
              {inputRepo !== DEFAULT_GITHUB_REPO && (
                <button
                  onClick={handleResetRepo}
                  title="Reset to official repo"
                  className="p-2 bg-[#1A1F36] hover:bg-[#262E4A] rounded-xl text-gray-300 transition-colors"
                >
                  <RotateCcw className="w-3.5 h-3.5" />
                </button>
              )}
              <button
                onClick={handleUpdateCheck}
                disabled={isCheckingUpdates || !inputRepo.trim()}
                className="px-4 py-2 bg-gradient-to-r from-[#8B5CF6] to-[#00C6FF] text-white text-xs font-bold rounded-xl shadow disabled:opacity-50"
              >
                {isCheckingUpdates ? 'Checking...' : 'Check Updates'}
              </button>
            </div>
          </div>

          {updateError && (
            <div className="p-3 rounded-xl bg-red-500/10 border border-red-500/30 text-red-200 text-xs flex items-center gap-2">
              <AlertTriangle className="w-4 h-4 shrink-0 text-red-400" />
              <span>{updateError}</span>
            </div>
          )}

          {updateInfo && !isCheckingUpdates && (
            <div className="p-3.5 rounded-xl bg-[#101322] border border-[#262E4A] space-y-2.5">
              <div className="flex items-center justify-between text-xs">
                <span className="font-bold text-white">
                  {updateInfo.isNewerThanInstalled ? '⚡ New Version Found!' : '✓ Latest GitHub Release'}
                </span>
                <span className="font-mono text-[#00C6FF] font-bold">{updateInfo.tagName}</span>
              </div>
              <p className="text-[11px] text-gray-300 line-clamp-2">{updateInfo.body}</p>
              
              <div className="flex items-center gap-2 pt-1">
                <a
                  href={updateInfo.htmlUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex-1 py-1.5 px-3 bg-[#1A1F36] hover:bg-[#262E4A] text-white text-xs font-semibold rounded-lg text-center transition-colors"
                >
                  View Release Notes
                </a>
                {updateInfo.downloadUrl && (
                  <a
                    href={updateInfo.downloadUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex-1 py-1.5 px-3 bg-gradient-to-r from-[#00C6FF] to-[#8B5CF6] text-white text-xs font-bold rounded-lg text-center shadow"
                  >
                    Download APK / Asset
                  </a>
                )}
              </div>
            </div>
          )}
        </div>

        {/* Notification Settings */}
        <div className="p-4 rounded-2xl bg-[#060714] border border-[#262E4A] space-y-3">
          <h4 className="text-xs font-bold text-gray-300 uppercase tracking-wider">Notifications</h4>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <Bell className="w-4 h-4 text-[#00C6FF]" />
              <div>
                <p className="text-xs font-semibold text-white">In-App Notification Banners</p>
                <p className="text-[10px] text-gray-400">Direct messages, reactions & release alerts</p>
              </div>
            </div>
            <input 
              type="checkbox" 
              checked={notificationsEnabled}
              onChange={(e) => setNotificationsEnabled(e.target.checked)}
              className="w-4 h-4 accent-[#00C6FF]" 
            />
          </div>
        </div>

        {/* Verification & Badges Shortcut */}
        <div className="p-4 rounded-2xl bg-[#060714] border border-[#262E4A] flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Shield className="w-4 h-4 text-[#FFB703]" />
            <div>
              <p className="text-xs font-semibold text-white">Official Verification Badges</p>
              <p className="text-[10px] text-gray-400">Blue Verified, VIP Gold, Shop Gold & Worker</p>
            </div>
          </div>
          <button
            onClick={() => {
              closeModal();
              openModal('VERIFICATION_CENTER');
            }}
            className="px-3 py-1.5 rounded-xl bg-[#1A1F36] hover:bg-[#262E4A] text-xs font-bold text-white transition-colors"
          >
            Apply
          </button>
        </div>

        {/* Admin Console Shortcut */}
        <div className="p-4 rounded-2xl bg-[#060714] border border-[#262E4A] flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Sparkles className="w-4 h-4 text-[#D946EF]" />
            <div>
              <p className="text-xs font-semibold text-white">Platform Admin Console</p>
              <p className="text-[10px] text-gray-400">Review verifications, metrics & reports</p>
            </div>
          </div>
          <button
            onClick={() => {
              closeModal();
              openModal('ADMIN_CONSOLE');
            }}
            className="px-3 py-1.5 rounded-xl bg-[#1A1F36] hover:bg-[#262E4A] text-xs font-bold text-white transition-colors"
          >
            Open Console
          </button>
        </div>

      </div>
    </div>
  );
};
