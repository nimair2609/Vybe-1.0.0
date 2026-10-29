import React from 'react';
import { Bell, X, Heart, MessageCircle, Sparkles, CheckCircle2, UserPlus } from 'lucide-react';
import { useVybe } from '../context/VybeContext';

export const NotificationsModal: React.FC = () => {
  const { closeModal, openModal } = useVybe();

  const notifications = [
    {
      id: 'n_01',
      icon: Heart,
      iconColor: 'text-[#EC4899]',
      bgColor: 'bg-[#EC4899]/10',
      title: 'Priya Kapoor reacted to your vibe',
      subtitle: 'Sent a Finger Heart 🫰 on Bandra sea link post',
      time: '12m ago'
    },
    {
      id: 'n_02',
      icon: MessageCircle,
      iconColor: 'text-[#00C6FF]',
      bgColor: 'bg-[#00C6FF]/10',
      title: 'New Comment from Rohan Verma',
      subtitle: '"The color grading is absolutely immaculate!"',
      time: '45m ago'
    },
    {
      id: 'n_03',
      icon: Sparkles,
      iconColor: 'text-[#FFB703]',
      bgColor: 'bg-[#FFB703]/10',
      title: 'Official VIP Badge Verified',
      subtitle: 'Your VIP status is now active on your public profile profile card.',
      time: '2h ago'
    },
    {
      id: 'n_04',
      icon: UserPlus,
      iconColor: 'text-[#22C55E]',
      bgColor: 'bg-[#22C55E]/10',
      title: 'Crossed Path Match Found!',
      subtitle: 'Someone replied to your Bandra Blue Tokai missed connection.',
      time: '5h ago'
    }
  ];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/85 backdrop-blur-xl animate-fade-in">
      <div className="relative w-full max-w-md max-h-[85vh] overflow-y-auto bg-[#0a0f1d] border border-[#262E4A] rounded-3xl shadow-2xl p-6 text-white no-scrollbar space-y-4">
        
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-[#262E4A]">
          <div className="flex items-center gap-2">
            <Bell className="w-5 h-5 text-[#00C6FF]" />
            <h2 className="text-base font-bold brand-font text-white">Notifications</h2>
          </div>
          <button onClick={closeModal} className="p-1.5 text-gray-400 hover:text-white rounded-lg">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Notifications List */}
        <div className="space-y-2.5">
          {notifications.map(n => {
            const Icon = n.icon;
            return (
              <div 
                key={n.id}
                className="p-3.5 rounded-2xl bg-[#101322] border border-[#262E4A] flex items-start gap-3 hover:border-[#00C6FF]/40 transition-colors"
              >
                <div className={`p-2.5 rounded-xl ${n.bgColor} ${n.iconColor} shrink-0 mt-0.5`}>
                  <Icon className="w-4 h-4" />
                </div>
                <div className="flex-1">
                  <div className="flex items-center justify-between">
                    <h4 className="text-xs font-bold text-white">{n.title}</h4>
                    <span className="text-[10px] text-gray-400">{n.time}</span>
                  </div>
                  <p className="text-[11px] text-gray-300 mt-0.5 leading-relaxed">{n.subtitle}</p>
                </div>
              </div>
            );
          })}
        </div>

      </div>
    </div>
  );
};
