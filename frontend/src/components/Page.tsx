import type { ReactNode } from 'react';
import { cn } from '../lib/cn';

/**
 * Title row of a view. On desktop the sidebar already names the view, so only the actions show, in a bar as tall as
 * the sidebar's logo row: the content below then starts level with the sidebar's first box.
 */
export function PageHeader({ title, description, actions, compact }: { title: string; description?: ReactNode; actions?: ReactNode; compact?: boolean }) {
  if (!compact) {
    return (
      <header className="sticky top-0 z-10 flex h-[64px] shrink-0 items-center justify-end gap-2 bg-canvas px-5">
        <h1 className="sr-only">{title}</h1>
        {actions}
      </header>
    );
  }
  return (
    <header className="flex flex-wrap items-end justify-between gap-x-6 gap-y-3 bg-canvas px-4 pt-3 pb-4">
      <div className="min-w-0">
        <h1 className="text-[28px] leading-9 font-bold tracking-[-0.022em]">{title}</h1>
        {description && <p className="mt-1 text-[15px] leading-snug text-fg-muted">{description}</p>}
      </div>
      {actions && <div className="flex w-full items-center gap-2 pt-1">{actions}</div>}
    </header>
  );
}

/** A view that is at least as tall as the window, so its body can stretch to fill it. */
export function Page({ children }: { children: ReactNode }) {
  return <div className="flex min-h-full flex-col">{children}</div>;
}

export function PageBody({ children, compact, className }: { children: ReactNode; compact?: boolean; className?: string }) {
  return <div className={cn('flex flex-1 flex-col gap-7', compact ? 'px-4 pt-2 pb-6' : 'px-5 pt-0 pb-3', className)}>{children}</div>;
}

export function Section({ title, meta, action, className, children }: { title: string; meta?: ReactNode; action?: ReactNode; className?: string; children: ReactNode }) {
  return (
    <section aria-label={title} className={className}>
      <div className="mb-2.5 flex min-h-7 items-center justify-between gap-3">
        <h2 className="pl-1 text-[13px] font-semibold text-fg-muted touch:text-[15px]">
          {title}
          {meta !== undefined && <span className="ml-2 font-normal text-fg-subtle">{meta}</span>}
        </h2>
        {action}
      </div>
      {children}
    </section>
  );
}

export function EmptyState({ icon, title, children }: { icon: ReactNode; title: string; children?: ReactNode }) {
  return (
    <div className="flex flex-1 flex-col items-center justify-center px-6 py-16 text-center">
      <span className="grid size-10 place-items-center rounded-full border border-border bg-bg-subtle text-fg-subtle [&_svg]:size-[18px] touch:size-14 touch:[&_svg]:size-6">{icon}</span>
      <p className="mt-4 text-[14px] font-medium touch:text-[17px]">{title}</p>
      {children && <p className="mt-1 max-w-sm text-[13px] text-fg-muted touch:mt-1.5 touch:text-[15px]">{children}</p>}
    </div>
  );
}
