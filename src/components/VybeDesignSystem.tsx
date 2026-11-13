import React from 'react';
import { LucideIcon } from 'lucide-react';
import { VybeAppMode } from '../context/VybeContext';

export const VybeBrandMark: React.FC<{
  isGrow?: boolean;
  showIcon?: boolean;
  onClick?: () => void;
}> = ({ isGrow = false, showIcon = true, onClick }) => {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex items-center gap-2 text-left focus:outline-none group"
    >
      {showIcon && (
        <img
          src="/brand/vybe_app_icon_clean.png"
          alt="VYBE Logo"
          className="w-8 h-8 rounded-lg object-cover shrink-0 border border-[var(--vybe-border)]"
        />
      )}
      <span className="text-[19px] font-extrabold tracking-tight brand-font text-[var(--vybe-text)] flex items-baseline gap-1">
        <span>VYBE</span>
        {isGrow && (
          <span className="font-bold text-[var(--vybe-accent)]">Grow</span>
        )}
      </span>
    </button>
  );
};

export const VybeModeSwitch: React.FC<{
  mode: VybeAppMode;
  onChange: (mode: VybeAppMode) => void;
  compact?: boolean;
}> = ({ mode, onChange, compact = false }) => {
  return (
    <div
      role="tablist"
      aria-label="VYBE Experience Mode"
      className="inline-flex items-center p-[3px] rounded-full border border-[var(--vybe-border)] bg-[var(--vybe-elevated)]"
    >
      <button
        type="button"
        role="tab"
        aria-selected={mode === 'SOCIAL'}
        onClick={() => onChange('SOCIAL')}
        className={`rounded-full font-bold transition-all duration-200 ${
          compact ? 'px-3 py-1 text-xs' : 'px-4 py-1.5 text-[13px]'
        } ${
          mode === 'SOCIAL'
            ? 'bg-[#7C3AED] text-white keep-white shadow-sm'
            : 'text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)]'
        }`}
      >
        Social
      </button>
      <button
        type="button"
        role="tab"
        aria-selected={mode === 'GROW'}
        onClick={() => onChange('GROW')}
        className={`rounded-full font-bold transition-all duration-200 ${
          compact ? 'px-3 py-1 text-xs' : 'px-4 py-1.5 text-[13px]'
        } ${
          mode === 'GROW'
            ? 'bg-[#2563EB] text-white keep-white shadow-sm'
            : 'text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)]'
        }`}
      >
        Grow
      </button>
    </div>
  );
};

export const VybeUnderlineTabs: React.FC<{
  tabs: { id: string; label: string }[];
  activeTab: string;
  onSelect: (id: string) => void;
}> = ({ tabs, activeTab, onSelect }) => {
  return (
    <div className="flex items-center border-b border-[var(--vybe-border)]">
      {tabs.map(t => {
        const isActive = t.id === activeTab;
        return (
          <button
            key={t.id}
            type="button"
            onClick={() => onSelect(t.id)}
            className={`flex-1 py-2.5 text-center text-[13px] font-semibold relative transition-colors ${
              isActive
                ? 'text-[var(--vybe-accent)] font-bold'
                : 'text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)]'
            }`}
          >
            {t.label}
            {isActive && (
              <span className="absolute bottom-0 inset-x-4 h-[2.5px] rounded-full bg-[var(--vybe-accent)]" />
            )}
          </button>
        );
      })}
    </div>
  );
};

export const VybeEmptyState: React.FC<{
  icon: LucideIcon;
  title: string;
  subtitle: string;
  actionLabel?: string;
  onAction?: () => void;
}> = ({ icon: Icon, title, subtitle, actionLabel, onAction }) => {
  return (
    <div className="p-8 rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-center flex flex-col items-center">
      <div className="w-12 h-12 rounded-full bg-[var(--vybe-accent-tint)] text-[var(--vybe-accent)] flex items-center justify-center mb-3">
        <Icon className="w-6 h-6" />
      </div>
      <h3 className="text-base font-bold text-[var(--vybe-text)]">{title}</h3>
      <p className="text-xs text-[var(--vybe-text-secondary)] mt-1 max-w-xs leading-relaxed">
        {subtitle}
      </p>
      {actionLabel && onAction && (
        <button
          type="button"
          onClick={onAction}
          className="mt-4 px-5 py-2.5 rounded-xl bg-[var(--vybe-accent)] text-white keep-white text-xs font-bold hover:opacity-95 transition-opacity"
        >
          {actionLabel}
        </button>
      )}
    </div>
  );
};

export const VybeProgressRing: React.FC<{
  percent: number;
  size?: number;
}> = ({ percent, size = 72 }) => {
  const clamped = Math.max(0, Math.min(100, Math.round(percent)));
  const stroke = 6;
  const radius = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference - (clamped / 100) * circumference;

  return (
    <div
      className="relative inline-flex items-center justify-center shrink-0"
      style={{ width: size, height: size }}
    >
      <svg width={size} height={size} className="-rotate-90">
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke="var(--vybe-border)"
          strokeWidth={stroke}
        />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke="var(--vybe-accent)"
          strokeWidth={stroke}
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          strokeLinecap="round"
        />
      </svg>
      <span className="absolute text-sm font-bold text-[var(--vybe-text)]">
        {clamped}%
      </span>
    </div>
  );
};

export const VybeActivityMiniBars: React.FC<{ values: number[] }> = ({ values }) => {
  const safe = values.length > 0 ? values : [0.2, 0.35, 0.25, 0.45, 0.3, 0.55, 0.4, 0.6, 0.75, 0.85];
  return (
    <div className="flex items-end gap-1.5 h-8 w-full pt-1">
      {safe.map((v, i) => {
        const pct = Math.max(16, Math.min(100, Math.round(v * 100)));
        const strong = v >= 0.65;
        return (
          <div
            key={i}
            className="flex-1 rounded-full transition-all"
            style={{
              height: `${pct}%`,
              backgroundColor: strong ? 'var(--vybe-accent)' : 'rgba(37, 99, 235, 0.28)'
            }}
          />
        );
      })}
    </div>
  );
};
