import { useEffect, useState, useSyncExternalStore } from 'react';
import { CircleAlert, CircleCheck, X } from 'lucide-react';
import { cn } from '../../lib/cn';
import { toast, toastStore, type ToastItem } from '../../lib/toast';
import { buttonClasses } from './button';
import { Spinner } from './field';

/** Where toasts appear: bottom right on a computer, full width above the tab bar on a phone. */
export function Toaster() {
  const items = useSyncExternalStore(toastStore.subscribe, toastStore.snapshot);
  // Hovering holds every toast, so there's time to read one or reach its button.
  const [paused, setPaused] = useState(false);

  return (
    <section
      aria-label="Notifications"
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      className="pointer-events-none fixed inset-x-3 bottom-[calc(80px+env(safe-area-inset-bottom))] z-[80] flex flex-col gap-2 lg:inset-x-auto lg:right-5 lg:bottom-5 lg:w-[360px]"
    >
      {items.map((item) => (
        <Toast key={item.id} item={item} paused={paused} />
      ))}
    </section>
  );
}

function Toast({ item, paused }: { item: ToastItem; paused: boolean }) {
  const { id, title, tone, description, duration = Infinity, action, leaving } = item;

  // Restarts whenever the toast is replaced (a new item), and waits while paused.
  useEffect(() => {
    if (paused || leaving || !Number.isFinite(duration)) return;
    const timer = window.setTimeout(() => toast.dismiss(id), duration);
    return () => window.clearTimeout(timer);
  }, [item, paused, id, duration, leaving]);

  return (
    <div
      role={tone === 'error' ? 'alert' : 'status'}
      className={cn(
        'pointer-events-auto flex items-start gap-3 rounded-xl border border-border bg-bg py-3 pr-2 pl-3.5 shadow-popover touch:rounded-2xl touch:py-3.5 touch:pl-4',
        leaving ? 'animate-toast-out' : 'animate-toast-in',
      )}
    >
      {tone === 'success' && <CircleCheck className="mt-px size-[18px] shrink-0 text-success" />}
      {tone === 'error' && <CircleAlert className="mt-px size-[18px] shrink-0 text-danger" />}
      {tone === 'loading' && <Spinner className="mt-0.5 size-4 shrink-0 text-fg-muted" />}
      <div className="min-w-0 flex-1 py-px">
        <p className="text-[13.5px] leading-5 font-medium touch:text-[15.5px] touch:leading-[22px]">{title}</p>
        {description && <p className="mt-0.5 text-[12.5px] text-fg-muted touch:text-[14px]">{description}</p>}
      </div>
      {action && (
        <button
          type="button"
          onClick={(event) => {
            action.onClick(event);
            if (!event.defaultPrevented) toast.dismiss(id);
          }}
          className={buttonClasses({ variant: 'primary', size: 'xs', className: 'self-center' })}
        >
          {action.label}
        </button>
      )}
      <button
        type="button"
        onClick={() => toast.dismiss(id)}
        aria-label="Close"
        className="grid size-6 shrink-0 place-items-center rounded-md text-fg-subtle outline-none hover:bg-bg-muted hover:text-fg focus-visible:ring-[3px] focus-visible:ring-accent/25 touch:size-9 touch:rounded-lg"
      >
        <X className="size-3.5 touch:size-[18px]" />
      </button>
    </div>
  );
}
