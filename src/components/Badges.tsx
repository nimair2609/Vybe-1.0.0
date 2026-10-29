import React from 'react';
import { Check, Star, ShoppingBag, ShieldCheck } from 'lucide-react';
import { OfficialVybeBadge } from '../types';

interface BadgeProps {
  badge: OfficialVybeBadge;
  size?: 'sm' | 'md' | 'lg';
}

export const OfficialBadge: React.FC<BadgeProps> = ({ badge, size = 'sm' }) => {
  const sizeClasses = {
    sm: 'w-4 h-4 p-0.5',
    md: 'w-5 h-5 p-1',
    lg: 'w-6 h-6 p-1',
  }[size];

  switch (badge) {
    case 'VERIFIED_BLUE':
      return (
        <span 
          title="VYBE Verified — Authentic public profile confirmed by VYBE review"
          className={`inline-flex items-center justify-center rounded-full bg-[#00C6FF] text-[#060714] font-bold ${sizeClasses} shadow-sm shadow-[#00C6FF]/40`}
        >
          <Check className="w-full h-full stroke-[3]" />
        </span>
      );
    case 'VIP_GOLD':
      return (
        <span 
          title="VYBE VIP — Recognized VIP creator"
          className={`inline-flex items-center justify-center rounded-full bg-gradient-to-r from-[#FFD700] to-[#FFA500] text-[#060714] font-bold ${sizeClasses} shadow-sm shadow-[#FFD700]/50`}
        >
          <Star className="w-full h-full fill-current stroke-none" />
        </span>
      );
    case 'SHOP_GOLD':
      return (
        <span 
          title="VYBE Verified Shop — Verified business storefront"
          className={`inline-flex items-center justify-center rounded-full bg-[#FFB703] text-[#060714] font-bold ${sizeClasses} shadow-sm shadow-[#FFB703]/40`}
        >
          <ShoppingBag className="w-full h-full stroke-[2.5]" />
        </span>
      );
    case 'WORKER_GREEN':
      return (
        <span 
          title="VYBE Worker — Authorized staff & platform team member"
          className={`inline-flex items-center justify-center rounded-full bg-[#22C55E] text-[#060714] font-bold ${sizeClasses} shadow-sm shadow-[#22C55E]/40`}
        >
          <ShieldCheck className="w-full h-full stroke-[2.5]" />
        </span>
      );
    default:
      return null;
  }
};
