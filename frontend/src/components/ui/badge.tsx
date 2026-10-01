import type { ReactNode } from 'react';
import { AlertCircle, AlertTriangle, Info } from 'lucide-react';
import { cn } from '../../lib/cn';

const DOTS = {
  neutral: 'bg-fg-subtle',
  accent: 'bg-accent',
  success: 'bg-success',
  warning: 'bg-warning',
  danger: 'bg-danger',
};

/** Small coloured dot and a label, for states like "Saved" or "Waiting". */
export function Status({ children, tone = 'neutral', className }: { children: ReactNode; tone?: keyof typeof DOTS; className?: string }) {
  return (
    <span className={cn('inline-flex items-center gap-1.5 text-[12.5px] whitespace-nowrap text-fg-muted touch:gap-2 touch:text-[14px]', className)}>
      <span className={cn('size-1.5 shrink-0 rounded-full touch:size-2', DOTS[tone])} aria-hidden="true" />
      {children}
    </span>
  );
}

/** Compact count, e.g. pending requests in the sidebar. */
export function Count({ children, tone = 'neutral', className }: { children: ReactNode; tone?: 'neutral' | 'accent'; className?: string }) {
  return (
    <span
      className={cn(
        'inline-flex h-[18px] min-w-[18px] items-center justify-center rounded-full px-1.5 text-[11px] font-medium tabular-nums touch:h-[22px] touch:min-w-[22px] touch:text-[12.5px]',
        tone === 'accent' ? 'bg-accent text-accent-fg' : 'bg-bg-emphasis text-fg-muted',
        className,
      )}
    >
      {children}
    </span>
  );
}

const CALLOUT = {
  info: { className: 'border-border bg-bg-subtle [&>svg]:text-fg-muted', icon: Info },
  warning: { className: 'border-warning/25 bg-warning-subtle [&>svg]:text-warning', icon: AlertTriangle },
  danger: { className: 'border-danger/25 bg-danger-subtle [&>svg]:text-danger', icon: AlertCircle },
};

export function Callout({ children, tone = 'info', className }: { children: ReactNode; tone?: keyof typeof CALLOUT; className?: string }) {
  const { className: toneClass, icon: Icon } = CALLOUT[tone];
  return (
    <div role={tone === 'danger' ? 'alert' : 'status'} className={cn('flex gap-2.5 rounded-lg border px-3 py-2.5 text-[13px] leading-snug text-fg touch:rounded-xl touch:px-3.5 touch:py-3 touch:text-[14.5px]', toneClass, className)}>
      <Icon className="mt-px size-4 shrink-0" />
      <div className="min-w-0">{children}</div>
    </div>
  );
}
