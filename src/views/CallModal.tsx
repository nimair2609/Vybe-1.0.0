import React from 'react';
import { Phone, PhoneOff, Mic, MicOff, Video, VideoOff, Volume2, VolumeX } from 'lucide-react';
import { useVybe } from '../context/VybeContext';

export const CallModal: React.FC = () => {
  const { callState, endCall, toggleCallMute, toggleCallCamera } = useVybe();

  if (!callState.isActive || !callState.peerUser) return null;

  const formatDuration = (sec: number) => {
    const mins = Math.floor(sec / 60);
    const s = sec % 60;
    return `${mins.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/95 backdrop-blur-2xl animate-fade-in">
      <div className="relative w-full max-w-sm h-[600px] flex flex-col justify-between items-center bg-[#060714] border border-[#262E4A] rounded-3xl shadow-2xl p-8 text-white overflow-hidden">
        
        {/* Top Info */}
        <div className="text-center space-y-1 z-10">
          <span className="text-[11px] font-bold uppercase tracking-widest text-[#00C6FF]">
            {callState.isVideo ? 'VYBE Video Call' : 'VYBE Voice Call'}
          </span>
          <h2 className="text-2xl font-bold brand-font text-white">{callState.peerUser.displayName}</h2>
          <p className="text-xs text-gray-400 font-mono">@{callState.peerUser.username}</p>
          <div className="pt-2">
            <span className="px-3 py-1 rounded-full bg-[#101322] border border-[#262E4A] text-xs font-mono font-semibold text-emerald-400">
              {formatDuration(callState.durationSeconds)}
            </span>
          </div>
        </div>

        {/* Center Pulsing Avatar */}
        <div className="relative flex items-center justify-center my-auto">
          <div className="w-40 h-40 rounded-full bg-gradient-to-tr from-[#00C6FF]/30 to-[#8B5CF6]/30 animate-ping absolute inset-0 opacity-40" />
          <div className="w-36 h-36 rounded-full bg-gradient-to-tr from-[#00C6FF] to-[#D946EF] p-1 shadow-2xl z-10">
            <img 
              src={callState.peerUser.avatarUrl} 
              alt={callState.peerUser.displayName}
              className="w-full h-full object-cover rounded-full"
            />
          </div>
        </div>

        {/* Bottom Interactive Controls */}
        <div className="w-full flex items-center justify-around z-10 pt-4 border-t border-[#262E4A]/60">
          
          {/* Mute */}
          <button
            onClick={toggleCallMute}
            className={`p-4 rounded-full transition-all ${
              callState.isMuted 
                ? 'bg-red-500/20 text-red-400 border border-red-500/40' 
                : 'bg-[#1A1F36] text-white hover:bg-[#262E4A]'
            }`}
          >
            {callState.isMuted ? <MicOff className="w-5 h-5" /> : <Mic className="w-5 h-5" />}
          </button>

          {/* Camera Toggle */}
          {callState.isVideo && (
            <button
              onClick={toggleCallCamera}
              className={`p-4 rounded-full transition-all ${
                callState.isCameraOff 
                  ? 'bg-red-500/20 text-red-400 border border-red-500/40' 
                  : 'bg-[#1A1F36] text-white hover:bg-[#262E4A]'
              }`}
            >
              {callState.isCameraOff ? <VideoOff className="w-5 h-5" /> : <Video className="w-5 h-5" />}
            </button>
          )}

          {/* End Call Button */}
          <button
            onClick={endCall}
            className="p-4 rounded-full bg-red-600 hover:bg-red-700 text-white shadow-xl shadow-red-600/40 transition-transform active:scale-90"
          >
            <PhoneOff className="w-6 h-6 stroke-[2.5]" />
          </button>

        </div>

      </div>
    </div>
  );
};
