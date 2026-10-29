import React, { useState } from 'react';
import { QrCode, X, Copy, Check, Share2 } from 'lucide-react';
import { useVybe } from '../context/VybeContext';

export const QrCodeModal: React.FC = () => {
  const { currentUser, closeModal } = useVybe();
  const [copied, setCopied] = useState(false);

  const profileUrl = `${window.location.origin}/#vybe_${currentUser.username}`;

  const handleCopy = () => {
    navigator.clipboard?.writeText(profileUrl);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/85 backdrop-blur-xl animate-fade-in">
      <div className="relative w-full max-w-sm bg-[#101322] border border-[#262E4A] rounded-3xl shadow-2xl p-6 text-white text-center space-y-4">
        
        <div className="flex items-center justify-between pb-2 border-b border-[#262E4A]">
          <h3 className="font-bold text-sm text-white">Share Profile QR</h3>
          <button onClick={closeModal} className="p-1 text-gray-400 hover:text-white">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* QR Frame Container */}
        <div className="p-6 bg-white rounded-3xl shadow-xl flex flex-col items-center justify-center space-y-3 mx-auto w-64 h-64 border-4 border-[#00C6FF]">
          {/* Simulated clean vector SVG QR Code */}
          <svg className="w-48 h-48" viewBox="0 0 100 100" fill="#060714">
            {/* Corner position markers */}
            <rect x="5" y="5" width="26" height="26" rx="4" fill="#060714" />
            <rect x="9" y="9" width="18" height="18" rx="2" fill="#FFFFFF" />
            <rect x="13" y="13" width="10" height="10" rx="1" fill="#060714" />

            <rect x="69" y="5" width="26" height="26" rx="4" fill="#060714" />
            <rect x="73" y="9" width="18" height="18" rx="2" fill="#FFFFFF" />
            <rect x="77" y="13" width="10" height="10" rx="1" fill="#060714" />

            <rect x="5" y="69" width="26" height="26" rx="4" fill="#060714" />
            <rect x="9" y="73" width="18" height="18" rx="2" fill="#FFFFFF" />
            <rect x="13" y="77" width="10" height="10" rx="1" fill="#060714" />

            {/* Simulated Data matrix points */}
            <rect x="36" y="8" width="6" height="6" />
            <rect x="46" y="14" width="6" height="6" />
            <rect x="56" y="8" width="6" height="6" />
            <rect x="38" y="24" width="6" height="6" />
            <rect x="48" y="32" width="6" height="6" />
            <rect x="10" y="40" width="6" height="6" />
            <rect x="22" y="44" width="6" height="6" />
            <rect x="36" y="44" width="14" height="14" rx="2" fill="#8B5CF6" />
            <rect x="56" y="40" width="6" height="6" />
            <rect x="68" y="44" width="6" height="6" />
            <rect x="80" y="38" width="6" height="6" />
            <rect x="38" y="62" width="6" height="6" />
            <rect x="48" y="70" width="6" height="6" />
            <rect x="58" y="60" width="6" height="6" />
            <rect x="70" y="72" width="6" height="6" />
            <rect x="82" y="82" width="6" height="6" />
            <rect x="62" y="84" width="6" height="6" />
            <rect x="44" y="84" width="6" height="6" />
          </svg>
        </div>

        <div>
          <h4 className="font-bold text-sm text-white">{currentUser.displayName}</h4>
          <p className="text-xs font-mono text-[#00C6FF] font-semibold">{currentUser.publicVybeId}</p>
        </div>

        <div className="flex items-center gap-2 pt-2">
          <button
            onClick={handleCopy}
            className="flex-1 py-2.5 rounded-xl bg-[#1A1F36] hover:bg-[#262E4A] text-xs font-bold text-white flex items-center justify-center gap-2 transition-colors"
          >
            {copied ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
            <span>{copied ? 'Copied Link!' : 'Copy Link'}</span>
          </button>
          <button
            onClick={handleCopy}
            className="p-2.5 rounded-xl bg-gradient-to-r from-[#00C6FF] to-[#8B5CF6] text-white shadow"
          >
            <Share2 className="w-4 h-4" />
          </button>
        </div>

      </div>
    </div>
  );
};
