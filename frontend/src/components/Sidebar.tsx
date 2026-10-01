import { Settings } from 'lucide-react';
import type { Device, Session } from '../lib/api';
import { cn } from '../lib/cn';
import type { StreamStatus } from '../lib/useEventStream';
import { Wordmark } from './Brand';
import { SECTION_COLOURS, SectionIcon } from './SectionIcon';
import { Count } from './ui/badge';
import { Tip } from './ui/tooltip';

export type View = 'send' | 'received' | 'sent';

interface Props {
  session: Session;
  status: StreamStatus;
  devices: Device[];
  view: View;
  onView: (view: View) => void;
  waiting: number;
  receivedCount: number;
  sentCount: number;
  onSettings: () => void;
}

const NAV: { id: View; label: string }[] = [
  { id: 'send', label: 'Nearby' },
  { id: 'received', label: 'Received' },
  { id: 'sent', label: 'Sent' },
];

const count = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`;

/**
 * Desktop navigation: the three sections as boxes that share the sidebar's height, and settings at the top. Each box
 * has its own icon, centered; the one open takes on its section's colour, with an outline and a slight lift, while
 * the others stay in the app's neutral colours.
 */
export function Sidebar({ session, status, devices, view, onView, waiting, receivedCount, sentCount, onSettings }: Props) {
  const peers = devices.filter((device) => device.id !== session.device.id);
  const online = peers.filter((device) => device.online).length;
  const summaries: Record<View, string> = {
    send: peers.length ? `${online} of ${count(peers.length, 'device', 'devices')} nearby` : 'No devices yet',
    received: waiting ? `${count(receivedCount, 'item', 'items')} · ${waiting} waiting` : receivedCount ? count(receivedCount, 'item', 'items') : 'Nothing yet',
    sent: sentCount ? count(sentCount, 'transfer', 'transfers') : 'Nothing yet',
  };

  return (
    <aside className="flex w-[272px] shrink-0 flex-col border-r border-border bg-sidebar px-3 pb-3">
      <div className="flex h-[64px] shrink-0 items-center justify-between pr-0.5 pl-2">
        <Wordmark large />
        <Tip label="Settings" side="bottom">
          <button
            type="button"
            onClick={onSettings}
            aria-label="Settings"
            className="relative grid size-9 place-items-center rounded-xl text-fg-muted outline-none hover:bg-bg-muted hover:text-fg focus-visible:ring-[3px] focus-visible:ring-accent/25"
          >
            <Settings className="size-[19px]" strokeWidth={1.9} />
            {status !== 'live' && <span className="absolute top-1.5 right-1.5 size-2 rounded-full bg-warning ring-2 ring-sidebar" aria-hidden="true" />}
          </button>
        </Tip>
      </div>

      <nav className="flex min-h-0 flex-1 flex-col gap-2.5" aria-label="Views">
        {NAV.map(({ id, label }) => {
          const active = view === id;
          const colour = SECTION_COLOURS[id];
          return (
            <button
              key={id}
              type="button"
              onClick={() => onView(id)}
              aria-current={active ? 'page' : undefined}
              className={cn(
                'group relative flex min-h-[168px] w-full flex-1 flex-col items-center justify-center gap-4 rounded-[18px] border-[1.5px] p-4 text-center outline-none transition-[background-color,border-color,box-shadow] duration-150 focus-visible:ring-[3px] focus-visible:ring-accent/25',
                active ? cn(colour.card, colour.selected) : 'border-border bg-bg hover:border-border-strong',
              )}
            >
              {id === 'received' && waiting > 0 && (
                <Count tone="accent" className={cn('absolute top-3.5 right-3.5 h-6 min-w-6 px-2 text-[12px] font-semibold', active && colour.solid)}>
                  {waiting}
                </Count>
              )}
              <span
                className={cn(
                  'grid size-[92px] place-items-center rounded-full transition-colors duration-150',
                  active ? colour.halo : 'bg-bg-muted group-hover:bg-bg-emphasis',
                )}
              >
                <SectionIcon
                  section={id}
                  className={cn('size-[46px] transition-colors duration-150', active ? colour.ink : 'text-fg-muted group-hover:text-fg')}
                  fillClassName={active ? cn(colour.wash, 'opacity-40') : 'fill-current opacity-10'}
                  strokeWidth={1.6}
                />
              </span>
              <span className="block min-w-0">
                <span className={cn('block text-[18px] leading-6 font-semibold tracking-[-0.01em]', active ? colour.title : 'text-fg')}>{label}</span>
                <span className={cn('mt-px block truncate text-[13px] font-medium', active ? cn(colour.ink, 'opacity-80') : 'text-fg-muted')}>
                  {summaries[id]}
                </span>
              </span>
            </button>
          );
        })}
      </nav>
    </aside>
  );
}
