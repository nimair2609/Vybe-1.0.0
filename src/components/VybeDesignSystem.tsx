import React, { useEffect } from 'react';
import { LucideIcon, X, Loader2, CheckCircle2, AlertTriangle, Info } from 'lucide-react';
import { VybeAppMode } from '../context/VybeContext';

export const VybeBrandMark: React.FC<{
  isGrow?: boolean;
  subtitle?: string;
  showIcon?: boolean;
  onClick?: () => void;
}> = ({ isGrow = false, subtitle, showIcon = true, onClick }) => {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex items-center gap-2.5 text-left focus:outline-none group min-h-[40px]"
    >
      {showIcon && (
        <img
          src="/brand/vybe_app_icon_clean.png"
          alt="VYBE Logo"
          className="w-8 h-8 rounded-lg object-cover shrink-0 border border-[var(--vybe-border)] shadow-sm"
        />
      )}
      <div className="leading-none">
        <span className="text-[18px] font-extrabold tracking-tight brand-font text-[var(--vybe-text)] flex items-baseline gap-1">
          <span>VYBE</span>
        </span>
        {subtitle && (
          <span className="block text-[10px] font-semibold tracking-wider uppercase text-[var(--vybe-text-secondary)] mt-0.5">
            {subtitle}
          </span>
        )}
      </div>
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

export type VybeButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'success';
export type VybeButtonSize = 'sm' | 'md' | 'lg';

export interface VybeButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: VybeButtonVariant;
  size?: VybeButtonSize;
  loading?: boolean;
  icon?: LucideIcon;
}

export const VybeButton: React.FC<VybeButtonProps> = ({
  variant = 'primary',
  size = 'md',
  loading = false,
  icon: Icon,
  className = '',
  children,
  disabled,
  type = 'button',
  ...rest
}) => {
  const variantClasses: Record<VybeButtonVariant, string> = {
    primary: 'bg-[var(--vybe-accent)] hover:opacity-95 text-white keep-white shadow-sm',
    secondary: 'bg-[var(--vybe-elevated)] hover:border-[var(--vybe-accent)] text-[var(--vybe-text)] border border-[var(--vybe-border)]',
    ghost: 'bg-transparent hover:bg-[var(--vybe-elevated)] text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)]',
    danger: 'bg-red-600 hover:bg-red-700 text-white keep-white shadow-sm',
    success: 'bg-emerald-600 hover:bg-emerald-700 text-white keep-white shadow-sm',
  };

  const sizeClasses: Record<VybeButtonSize, string> = {
    sm: 'min-h-[34px] px-3 py-1.5 text-xs rounded-lg gap-1.5',
    md: 'min-h-[40px] px-4 py-2 text-xs rounded-xl gap-2',
    lg: 'min-h-[44px] px-5 py-2.5 text-sm rounded-xl gap-2',
  };

  return (
    <button
      type={type}
      disabled={disabled || loading}
      className={`inline-flex items-center justify-center font-semibold transition-all disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer ${variantClasses[variant]} ${sizeClasses[size]} ${className}`}
      {...rest}
    >
      {loading ? (
        <Loader2 className="w-3.5 h-3.5 animate-spin shrink-0" />
      ) : Icon ? (
        <Icon className="w-3.5 h-3.5 shrink-0" />
      ) : null}
      {children}
    </button>
  );
};

export const VybeCard: React.FC<{
  children: React.ReactNode;
  className?: string;
  padding?: 'none' | 'sm' | 'md' | 'lg';
}> = ({ children, className = '', padding = 'md' }) => {
  const padClass = {
    none: '',
    sm: 'p-3.5',
    md: 'p-5',
    lg: 'p-6',
  }[padding];

  return (
    <div
      className={`rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-[var(--vybe-text)] transition-colors ${padClass} ${className}`}
    >
      {children}
    </div>
  );
};

export type VybeChipTone = 'neutral' | 'accent' | 'success' | 'warning' | 'danger' | 'info';

export const VybeStatusChip: React.FC<{
  tone?: VybeChipTone;
  children: React.ReactNode;
  className?: string;
}> = ({ tone = 'neutral', children, className = '' }) => {
  const toneClasses: Record<VybeChipTone, string> = {
    neutral: 'bg-[var(--vybe-elevated)] text-[var(--vybe-text-secondary)] border-[var(--vybe-border)]',
    accent: 'bg-[var(--vybe-accent-soft)] text-[var(--vybe-accent)] border-[var(--vybe-accent)]/30',
    success: 'bg-emerald-500/12 text-emerald-600 dark:text-emerald-400 border-emerald-500/30',
    warning: 'bg-amber-500/12 text-amber-600 dark:text-amber-400 border-amber-500/30',
    danger: 'bg-red-500/12 text-red-600 dark:text-red-400 border-red-500/30',
    info: 'bg-sky-500/12 text-sky-600 dark:text-sky-400 border-sky-500/30',
  };

  return (
    <span
      className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-semibold border whitespace-nowrap ${toneClasses[tone]} ${className}`}
    >
      {children}
    </span>
  );
};

export interface VybeInputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  leftIcon?: LucideIcon;
}

export const VybeInput: React.FC<VybeInputProps> = ({
  label,
  leftIcon: LeftIcon,
  className = '',
  id,
  ...rest
}) => {
  return (
    <div className="space-y-1.5 w-full">
      {label && (
        <label htmlFor={id} className="block text-xs font-semibold text-[var(--vybe-text-secondary)]">
          {label}
        </label>
      )}
      <div className="relative flex items-center">
        {LeftIcon && (
          <LeftIcon className="w-4 h-4 text-[var(--vybe-text-muted)] absolute left-3.5 pointer-events-none" />
        )}
        <input
          id={id}
          className={`w-full min-h-[40px] ${LeftIcon ? 'pl-10 pr-3.5' : 'px-3.5'} py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs text-[var(--vybe-text)] placeholder-[var(--vybe-text-muted)] focus:outline-none focus:border-[var(--vybe-accent)] transition-colors ${className}`}
          {...rest}
        />
      </div>
    </div>
  );
};

export interface VybeSelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
}

export const VybeSelect: React.FC<VybeSelectProps> = ({
  label,
  className = '',
  id,
  children,
  ...rest
}) => {
  return (
    <div className="space-y-1.5">
      {label && (
        <label htmlFor={id} className="block text-xs font-semibold text-[var(--vybe-text-secondary)]">
          {label}
        </label>
      )}
      <select
        id={id}
        className={`min-h-[40px] px-3 py-2 rounded-xl bg-[var(--vybe-elevated)] border border-[var(--vybe-border)] text-xs text-[var(--vybe-text)] focus:outline-none focus:border-[var(--vybe-accent)] transition-colors ${className}`}
        {...rest}
      >
        {children}
      </select>
    </div>
  );
};

export const VybeModal: React.FC<{
  open: boolean;
  onClose: () => void;
  title: string;
  subtitle?: string;
  maxWidthClass?: string;
  children: React.ReactNode;
}> = ({ open, onClose, title, subtitle, maxWidthClass = 'max-w-lg', children }) => {
  useEffect(() => {
    if (!open) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-fade-in"
      onClick={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onClick={e => e.stopPropagation()}
        className={`relative w-full ${maxWidthClass} max-h-[90vh] overflow-y-auto bg-[var(--vybe-surface)] border border-[var(--vybe-border)] rounded-2xl shadow-2xl p-6 text-[var(--vybe-text)] no-scrollbar`}
      >
        <div className="flex items-start justify-between gap-3 pb-3 mb-4 border-b border-[var(--vybe-border)]">
          <div>
            <h2 className="text-base font-bold brand-font text-[var(--vybe-text)]">{title}</h2>
            {subtitle && (
              <p className="text-xs text-[var(--vybe-text-secondary)] mt-0.5">{subtitle}</p>
            )}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close dialog"
            className="p-2 -mr-1 -mt-1 rounded-lg text-[var(--vybe-text-secondary)] hover:text-[var(--vybe-text)] hover:bg-[var(--vybe-elevated)] transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
};

export const VybeConfirmDialog: React.FC<{
  open: boolean;
  title: string;
  description: string;
  confirmLabel?: string;
  cancelLabel?: string;
  tone?: 'danger' | 'primary';
  loading?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}> = ({
  open,
  title,
  description,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  tone = 'danger',
  loading = false,
  onConfirm,
  onCancel,
}) => {
  return (
    <VybeModal open={open} onClose={onCancel} title={title} maxWidthClass="max-w-md">
      <div className="space-y-5">
        <p className="text-xs text-[var(--vybe-text-secondary)] leading-relaxed">{description}</p>
        <div className="flex items-center justify-end gap-2.5 pt-2">
          <VybeButton variant="secondary" size="md" onClick={onCancel} disabled={loading}>
            {cancelLabel}
          </VybeButton>
          <VybeButton
            variant={tone === 'danger' ? 'danger' : 'primary'}
            size="md"
            loading={loading}
            onClick={onConfirm}
          >
            {confirmLabel}
          </VybeButton>
        </div>
      </div>
    </VybeModal>
  );
};

export const VybeToast: React.FC<{
  message: string | null;
  tone?: 'success' | 'error' | 'info';
  onDismiss?: () => void;
}> = ({ message, tone = 'info', onDismiss }) => {
  if (!message) return null;

  const toneStyles = {
    success: 'border-emerald-500/40 text-emerald-500',
    error: 'border-red-500/40 text-red-500',
    info: 'border-[var(--vybe-accent)]/40 text-[var(--vybe-accent)]',
  }[tone];

  const Icon = tone === 'success' ? CheckCircle2 : tone === 'error' ? AlertTriangle : Info;

  return (
    <div
      role="status"
      aria-live="polite"
      className={`fixed bottom-5 right-5 z-50 max-w-sm bg-[var(--vybe-surface)] border ${toneStyles} px-4 py-3 rounded-2xl shadow-2xl flex items-center gap-2.5 text-xs animate-fade-in`}
    >
      <Icon className="w-4 h-4 shrink-0" />
      <span className="text-[var(--vybe-text)] font-medium flex-1">{message}</span>
      {onDismiss && (
        <button
          type="button"
          onClick={onDismiss}
          aria-label="Dismiss notification"
          className="p-1 rounded-md text-[var(--vybe-text-muted)] hover:text-[var(--vybe-text)]"
        >
          <X className="w-3.5 h-3.5" />
        </button>
      )}
    </div>
  );
};

export const VybeLoader: React.FC<{ label?: string }> = ({ label = 'Loading...' }) => (
  <div className="py-12 flex flex-col items-center justify-center gap-2.5 text-xs text-[var(--vybe-text-secondary)]">
    <Loader2 className="w-5 h-5 animate-spin text-[var(--vybe-accent)]" />
    <span>{label}</span>
  </div>
);

export const VybeEmptyState: React.FC<{
  icon: LucideIcon;
  title: string;
  subtitle: string;
  actionLabel?: string;
  onAction?: () => void;
}> = ({ icon: Icon, title, subtitle, actionLabel, onAction }) => {
  return (
    <div className="p-8 rounded-2xl bg-[var(--vybe-surface)] border border-[var(--vybe-border)] text-center flex flex-col items-center">
      <div className="w-12 h-12 rounded-full bg-[var(--vybe-accent-soft)] text-[var(--vybe-accent)] flex items-center justify-center mb-3">
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
