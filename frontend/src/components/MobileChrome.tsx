import { Settings } from 'lucide-react';
import { cn } from '../lib/cn';
import { Wordmark } from './Brand';
import { SECTION_COLOURS, SectionIcon } from './SectionIcon';
import { Button } from './ui/button';
import type { View } from './Sidebar';

/** Phone header: brand on the left, settings on the right. */
export function MobileHeader({ onSettings }: { onSettings: () => void }) {
  return (
    <header className="sticky top-0 z-30 bg-canvas/90 pt-[env(safe-area-inset-top)] backdrop-blur">
      <div className="flex h-14 items-center justify-between pr-2 pl-4">
        <Wordmark large />
        <Button variant="ghost" size="icon" aria-label="Settings" onClick={onSettings}>
          <Settings className="!size-6" strokeWidth={1.9} />
        </Button>
      </div>
    </header>
  );
}

const TABS: { id: View; label: string }[] = [
  { id: 'send', label: 'Nearby' },
  { id: 'received', label: 'Received' },
  { id: 'sent', label: 'Sent' },
];

/** Phone tab bar. */
export function MobileTabBar({ view, onView, waiting }: { view: View; onView: (view: View) => void; waiting: number }) {
  return (
    <nav aria-label="Views" className="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-canvas/95 pb-[env(safe-area-inset-bottom)] backdrop-blur">
      <div className="mx-auto grid max-w-md grid-cols-3">
        {TABS.map(({ id, label }) => {
          const active = view === id;
          const colour = SECTION_COLOURS[id];
          return (
            <button
              key={id}
              type="button"
              onClick={() => onView(id)}
              aria-current={active ? 'page' : undefined}
              className={cn('relative flex h-16 flex-col items-center justify-center gap-1 text-[12.5px] font-medium outline-none', active ? 'text-fg' : 'text-fg-muted')}
            >
              {/* A pill in the section's own colour behind the selected tab's icon, as Android does. */}
              <span className={cn('relative grid h-8 w-16 place-items-center rounded-full transition-colors duration-150', active && colour.halo)}>
                <SectionIcon
                  section={id}
                  className={cn('size-6', active && colour.ink)}
                  fillClassName={active ? cn(colour.wash, 'opacity-40') : undefined}
                  strokeWidth={active ? 2 : 1.8}
                />
                {id === 'received' && waiting > 0 && (
                  <span className="absolute -top-1 right-2.5 grid h-[18px] min-w-[18px] place-items-center rounded-full bg-danger px-1 text-[11px] font-semibold text-white tabular-nums ring-2 ring-canvas">
                    {waiting}
                  </span>
                )}
              </span>
              {label}
            </button>
          );
        })}
      </div>
    </nav>
  );
}
