import type { ComponentProps } from 'react';
import { cn } from '../../lib/cn';

export const inputClasses =
  'w-full min-w-0 rounded-lg border border-border bg-bg px-3 text-[14px] text-fg shadow-control outline-none transition-[border-color,box-shadow] duration-100 placeholder:text-fg-subtle touch:text-[16px] hover:border-border-strong focus:border-accent focus:ring-[3px] focus:ring-accent/20 disabled:opacity-50';

export function Input({ className, ...props }: ComponentProps<'input'>) {
  return <input className={cn(inputClasses, 'h-9 touch:h-12 touch:rounded-xl touch:px-4', className)} {...props} />;
}

export function Label({ className, ...props }: ComponentProps<'label'>) {
  return <label className={cn('text-[13px] font-medium text-fg touch:text-[15px]', className)} {...props} />;
}

export function Kbd({ className, ...props }: ComponentProps<'kbd'>) {
  return (
    <kbd
      className={cn(
        'inline-flex h-5 min-w-5 items-center justify-center rounded border border-border bg-bg-subtle px-1 font-sans text-[11px] font-medium text-fg-subtle',
        className,
      )}
      {...props}
    />
  );
}

export function Spinner({ className }: { className?: string }) {
  return (
    <svg className={cn('size-4 animate-spin', className)} viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="9" stroke="currentColor" strokeOpacity="0.2" strokeWidth="2.5" />
      <path d="M21 12a9 9 0 0 0-9-9" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" />
    </svg>
  );
}

export function ProgressBar({ value, className }: { value: number; className?: string }) {
  return (
    <div className={cn('h-1 overflow-hidden rounded-full bg-bg-emphasis touch:h-1.5', className)} role="presentation">
      <div className="h-full rounded-full bg-accent transition-[width] duration-300 ease-out" style={{ width: `${Math.max(0, Math.min(100, value))}%` }} />
    </div>
  );
}
