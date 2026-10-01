import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { FolderOpen, Search, X } from 'lucide-react';
import type { Device, Session, Transfer } from '../lib/api';
import { cn } from '../lib/cn';
import { keepsInApp } from '../lib/phoneSave';
import type { ProgressStore } from '../lib/progress';
import { dayLabel, fileManagerName, formatBytes, tildePath } from '../lib/format';
import { summarize } from '../lib/transfers';
import { useNow } from '../lib/useNow';
import type { AvatarDevice } from './DeviceAvatar';
import type { FolderChoices } from './FolderPicker';
import { EmptyState, Page, PageBody, PageHeader, Section } from './Page';
import { SectionIcon } from './SectionIcon';
import { TransferRow, type TransferActions } from './TransferRow';
import { Button } from './ui/button';
import { inputClasses, Kbd } from './ui/field';

export type Direction = 'in' | 'out';

interface Props extends TransferActions {
  session: Session;
  direction: Direction;
  transfers: Transfer[];
  devices: ReadonlyMap<string, Device>;
  progressStore: ProgressStore;
  /** Folders the host can save into; null on a phone. */
  folders: FolderChoices | null;
  loaded: boolean;
  compact: boolean;
  onOpenFolder?: () => void;
}

/** Received or Sent: pending requests first, then everything else grouped by day. */
export function TransfersView({ session, direction, transfers, devices, progressStore, folders, loaded, compact, onOpenFolder, ...actions }: Props) {
  const [query, setQuery] = useState('');
  const search = useRef<HTMLInputElement>(null);
  const now = useNow(30_000);
  const incoming = direction === 'in';

  // "/" jumps to search.
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement;
      if (event.key !== '/' || target.closest('input, textarea, [contenteditable]')) return;
      event.preventDefault();
      search.current?.focus();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  const { requests, groups } = useMemo(() => {
    const needle = query.trim().toLowerCase();
    const matches = transfers.filter((transfer) => {
      if (!needle) return true;
      const peer = incoming ? transfer.senderName : transfer.recipientName;
      return [summarize(transfer), peer, transfer.text ?? '', ...transfer.files.map((file) => file.name)].some((field) => field.toLowerCase().includes(needle));
    });
    const pending: Transfer[] = [];
    const byDay = new Map<string, Transfer[]>();
    for (const transfer of matches) {
      if (incoming && transfer.status === 'PENDING') {
        pending.push(transfer);
        continue;
      }
      const label = dayLabel(transfer.createdAt, now);
      const day = byDay.get(label);
      if (day) day.push(transfer);
      else byDay.set(label, [transfer]);
    }
    return { requests: pending, groups: [...byDay.entries()] };
  }, [transfers, query, incoming, now]);

  const description = incoming
    ? session.host && session.storageDir
      ? `Accepted files go to ${tildePath(folders?.folders[0]?.path ?? session.storageDir)} unless you pick another folder${session.freeBytes !== null ? ` · ${formatBytes(session.freeBytes)} free` : ''}`
      : keepsInApp
        ? 'Accepted files come into AndroidDrop. Tap Save to put them in Downloads.'
        : 'Accepted files are saved to Downloads.'
    : 'Everything you’ve shared, and whether it arrived.';

  const rowProps = { isHost: session.host, fileManager: fileManagerName(session.device.platform), progressStore, folders, now, compact, ...actions };

  return (
    <Page>
      <PageHeader
        title={incoming ? 'Received' : 'Sent'}
        description={description}
        compact={compact}
        actions={
          <>
            {transfers.length > 0 && (
              <div className={cn('relative', compact ? 'w-full' : 'w-64')}>
                <Search className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-fg-subtle touch:left-3.5 touch:size-5" />
                <input
                  ref={search}
                  type="search"
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  onKeyDown={(event) => event.key === 'Escape' && setQuery('')}
                  placeholder="Search"
                  aria-label={`Search ${incoming ? 'received' : 'sent'} transfers`}
                  className={cn(inputClasses, 'h-8 pr-8 pl-8 text-[13px] [&::-webkit-search-cancel-button]:hidden touch:h-12 touch:rounded-full touch:pr-12 touch:pl-11 touch:text-[16px]')}
                />
                {query ? (
                  <button type="button" onClick={() => setQuery('')} aria-label="Clear search" className="absolute top-1/2 right-2 -translate-y-1/2 text-fg-subtle hover:text-fg touch:right-1 touch:grid touch:size-10 touch:place-items-center">
                    <X className="size-4 touch:size-5" />
                  </button>
                ) : (
                  !compact && <Kbd className="pointer-events-none absolute top-1/2 right-2 -translate-y-1/2">/</Kbd>
                )}
              </div>
            )}
            {incoming && onOpenFolder && !compact && (
              <Button size="sm" onClick={onOpenFolder}>
                <FolderOpen /> Open folder
              </Button>
            )}
          </>
        }
      />
      <PageBody compact={compact}>
        {!loaded ? (
          <div className="space-y-2" aria-busy="true">
            {[0, 1, 2].map((key) => (
              <div key={key} className="h-16 rounded-xl bg-bg-muted" />
            ))}
          </div>
        ) : transfers.length === 0 ? (
          incoming ? (
            <EmptyState icon={<SectionIcon section="received" />} title="Nothing received yet">
              When a device shares something with you, you’ll be asked to accept it. Accepted files are saved automatically.
            </EmptyState>
          ) : (
            <EmptyState icon={<SectionIcon section="sent" />} title="Nothing sent yet">
              Pick a device on Nearby, or drop files onto it.
            </EmptyState>
          )
        ) : requests.length === 0 && groups.length === 0 ? (
          <EmptyState icon={<Search />} title={`No results for “${query}”`} />
        ) : (
          <>
            {requests.length > 0 && (
              <Section title="Requests" meta={requests.length}>
                <List>
                  {requests.map((transfer) => (
                    <TransferRow key={transfer.id} transfer={transfer} direction={direction} peer={peerOf(transfer, direction, devices)} {...rowProps} />
                  ))}
                </List>
              </Section>
            )}
            {groups.map(([label, dayTransfers]) => (
              <Section key={label} title={label}>
                <List>
                  {dayTransfers.map((transfer) => (
                    <TransferRow key={transfer.id} transfer={transfer} direction={direction} peer={peerOf(transfer, direction, devices)} {...rowProps} />
                  ))}
                </List>
              </Section>
            ))}
          </>
        )}
      </PageBody>
    </Page>
  );
}

function List({ children }: { children: ReactNode }) {
  return <ul className="divide-y divide-border overflow-hidden rounded-xl border border-border bg-bg">{children}</ul>;
}

/** The device at the other end, with its live online state if it's still paired. */
export function peerOf(transfer: Transfer, direction: Direction, devices: ReadonlyMap<string, Device>): AvatarDevice {
  const id = direction === 'in' ? transfer.senderId : transfer.recipientId;
  const live = devices.get(id);
  if (live) return live;
  return direction === 'in'
    ? { id, name: transfer.senderName, platform: transfer.senderPlatform, online: false }
    : { id, name: transfer.recipientName, platform: transfer.recipientPlatform, online: false };
}
