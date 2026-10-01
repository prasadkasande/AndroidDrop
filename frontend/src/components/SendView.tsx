import { useEffect, useLayoutEffect, useMemo, useReducer, useRef, useState, type FormEvent, type KeyboardEvent, type ReactNode } from 'react';
import { ArrowUp, Check, Copy, Paperclip, Plus, X } from 'lucide-react';
import type { Device, PairingInfo, Session, Transfer } from '../lib/api';
import { cn } from '../lib/cn';
import { dayLabel, formatDuration, formatSpeed, fullDate } from '../lib/format';
import { useDownloadedBytes, useUploadedBytes, type ProgressStore } from '../lib/progress';
import { keepsInApp, readyToSave } from '../lib/phoneSave';
import { isComplete, phaseOf, savedFolderName, singleLink, summarize, totalSize } from '../lib/transfers';
import { useDropTarget } from '../lib/useDropTarget';
import { useNow } from '../lib/useNow';
import { DeviceAvatar, type AvatarDevice } from './DeviceAvatar';
import { Page, PageBody, PageHeader } from './Page';
import { LazyPairingPanel } from './LazyPairingPanel';
import { Status } from './ui/badge';
import { Button } from './ui/button';
import { ProgressBar, Spinner } from './ui/field';
import { Tip } from './ui/tooltip';

interface Props {
  session: Session;
  devices: Device[];
  transfers: Transfer[];
  progressStore: ProgressStore;
  peerOf: (transfer: Transfer) => AvatarDevice;
  pairing: PairingInfo | null;
  onboarding: boolean;
  compact: boolean;
  onChooseFiles: () => void;
  onPickFor: (deviceId: string) => void;
  onDropFor: (deviceId: string, files: File[], skippedFolders: number) => void;
  onSendText: (text: string) => Promise<boolean>;
  onCancel: (transfer: Transfer) => void;
  /** This device is still uploading the file, or reconnecting to continue it. */
  isUploading: (fileId: string) => boolean;
  onAccept: (transfer: Transfer) => Promise<void>;
  onDecline: (transfer: Transfer) => Promise<void>;
  onCopy: (text: string) => Promise<boolean>;
  onPair: () => void;
  onRotatePairing: () => void;
}

export function SendView(props: Props) {
  const { session, devices, onboarding, compact } = props;
  const peers = devices.filter((device) => device.id !== session.device.id);
  const online = peers.filter((device) => device.online).length;

  return (
    <Page>
      <PageHeader
        title="Nearby"
        compact={compact}
        description={
          onboarding
            ? 'Connect a phone to start sharing.'
            : compact
              ? 'Tap a device to choose files for it.'
              : 'Drop files on a device to send them, or click it to choose files.'
        }
        actions={
          !onboarding && (
            <Button variant="primary" size={compact ? 'md' : 'sm'} onClick={props.onChooseFiles} className={cn(compact && 'w-full')}>
              <Paperclip /> Choose files
            </Button>
          )
        }
      />
      {onboarding ? (
        <PageBody compact={compact}>
          <div className="rounded-2xl border border-border bg-bg p-6">
            <h2 className="text-[15px] font-semibold">Connect your first device</h2>
            <p className="mt-1 mb-6 max-w-lg text-[13.5px] text-fg-muted">
              Open the camera on your phone and scan this code. It pairs once and reconnects on its own after that. There’s nothing to install.
            </p>
            <LazyPairingPanel info={props.pairing} hostName={session.serverName} onRotate={props.onRotatePairing} />
          </div>
          <LiveTransfers {...props} />
        </PageBody>
      ) : (
        // The stage takes whatever room the window has; notes and live transfers sit beside it on wide screens.
        <PageBody compact={compact} className={cn(compact ? 'gap-6' : 'gap-4 xl:flex-row')}>
          <section
            aria-label="Devices"
            className={cn('relative flex flex-1 flex-col overflow-hidden rounded-2xl border border-border bg-bg', compact ? 'min-h-[48dvh]' : 'min-h-[340px] xl:min-h-[420px]')}
          >
            <div className={cn('relative flex flex-1 items-center justify-center', compact ? 'px-3 py-8' : 'px-8 py-12')}>
              <Rings compact={compact} />
              <div className={cn('relative flex flex-wrap justify-center', compact ? 'gap-x-2 gap-y-6' : 'gap-x-14 gap-y-10')}>
                {peers.map((device) => (
                  <Peer
                    key={device.id}
                    device={device}
                    compact={compact}
                    onPick={() => props.onPickFor(device.id)}
                    onDrop={(files, folders) => props.onDropFor(device.id, files, folders)}
                  />
                ))}
                {session.host && <AddPeer compact={compact} onClick={props.onPair} />}
              </div>
            </div>
            <p className="relative px-6 pb-5 text-center text-[12.5px] text-fg-subtle touch:text-[14px]">
              {online === 0 ? 'No devices are open right now. They’ll get your files when they open AndroidDrop.' : `${online} of ${peers.length} ${peers.length === 1 ? 'device is' : 'devices are'} nearby`}
            </p>
          </section>

          <div className={cn('flex flex-col', compact ? 'gap-6' : 'gap-4 xl:w-[340px] xl:shrink-0')}>
            <LiveTransfers {...props} />
            <Messages {...props} grow={!compact} />
          </div>
        </PageBody>
      )}
    </Page>
  );
}

/** Faint static rings behind the devices, the way AirDrop draws its range. */
function Rings({ compact }: { compact: boolean }) {
  const sizes = compact ? [190, 330, 470] : [300, 540, 780];
  return (
    <div aria-hidden="true" className="pointer-events-none absolute inset-0">
      {sizes.map((size, index) => (
        <span
          key={size}
          className="absolute top-1/2 left-1/2 -translate-1/2 rounded-full border border-border"
          style={{ width: size, height: size, opacity: 1 - index * 0.28 }}
        />
      ))}
    </div>
  );
}

function Peer({ device, compact, onPick, onDrop }: { device: Device; compact: boolean; onPick: () => void; onDrop: (files: File[], folders: number) => void }) {
  const { over, handlers } = useDropTarget(onDrop);
  return (
    <button
      type="button"
      onClick={onPick}
      {...handlers}
      aria-label={`Send files to ${device.name}`}
      className={cn('group flex flex-col items-center rounded-2xl p-2 text-center outline-none', compact ? 'w-[116px] gap-2.5' : 'w-36 gap-3', 'focus-visible:ring-[3px] focus-visible:ring-accent/25')}
    >
      <span
        className={cn(
          'rounded-full ring-offset-4 ring-offset-bg transition-[box-shadow,filter] duration-150 group-hover:brightness-[0.97]',
          over && 'ring-[3px] ring-accent',
        )}
      >
        <DeviceAvatar device={device} size={compact ? 'xl' : '2xl'} status ringClassName="ring-bg" className={cn(!device.online && 'opacity-55')} />
      </span>
      <span className="w-full min-w-0">
        <span className={cn('line-clamp-2 leading-snug font-medium break-words', compact ? 'text-[15.5px]' : 'text-[14.5px]')}>{device.name}</span>
        <span className={cn('block text-[12.5px] touch:text-[14px]', over ? 'font-medium text-fg' : device.online ? 'text-success' : 'text-fg-subtle')}>
          {over ? 'Drop to send' : device.online ? 'Nearby' : 'Away'}
        </span>
      </span>
    </button>
  );
}

function AddPeer({ compact, onClick }: { compact: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn('group flex flex-col items-center rounded-2xl p-2 text-center outline-none focus-visible:ring-[3px] focus-visible:ring-accent/25', compact ? 'w-[116px] gap-2.5' : 'w-36 gap-3')}
    >
      <span
        className={cn(
          'grid place-items-center rounded-full border-[1.5px] border-dashed border-border-strong bg-bg text-fg-subtle transition-colors group-hover:border-fg-subtle group-hover:text-fg',
          compact ? 'size-[72px]' : 'size-24',
        )}
      >
        <Plus className={compact ? 'size-6' : 'size-8'} strokeWidth={1.5} />
      </span>
      <span className="w-full">
        <span className={cn('block font-medium text-fg-muted group-hover:text-fg', compact ? 'text-[15.5px]' : 'text-[14.5px]')}>Add device</span>
        <span className="block text-[12.5px] text-fg-subtle">Pair a phone</span>
      </span>
    </button>
  );
}

const LINGER_MS = 6000;
const FAILED_MS = 10 * 60 * 1000;

/** File transfers in flight in either direction, plus ones that just finished or were declined. Notes and links show in Messages. */
function LiveTransfers({ session, transfers, progressStore, peerOf, onCancel, isUploading }: Props) {
  const [, tick] = useReducer((count: number) => count + 1, 0);
  const now = Date.now();
  const me = session.device.id;
  const recent = (iso: string | null) => iso !== null && now - Date.parse(iso) < LINGER_MS;
  const live = transfers.filter((transfer) => {
    const outgoing = transfer.senderId === me;
    if (transfer.text !== null) return false;
    if (transfer.status === 'PENDING') return outgoing;
    if (transfer.status === 'DECLINED') return outgoing && recent(transfer.respondedAt);
    if (transfer.status !== 'ACCEPTED') return false;
    // A broken upload is only actionable by its sender, and only while they're likely to retry it.
    if (phaseOf(transfer) === 'failed') return outgoing && now - Date.parse(transfer.createdAt) < FAILED_MS;
    // A phone's big files wait on the computer for Save: as far as this card goes, they've arrived.
    if (!isComplete(transfer) && (outgoing || !readyToSave(transfer, progressStore.bytes('download')))) return true;
    const finished = transfer.files.reduce((latest, file) => Math.max(latest, Date.parse(file.finishedAt ?? '') || 0), 0);
    return now - finished < LINGER_MS;
  });
  // Finished transfers linger a few seconds, so the list is looked at again every second, but only while it shows any.
  const showing = live.length > 0;
  useEffect(() => {
    if (!showing) return;
    const timer = window.setInterval(tick, 1000);
    return () => window.clearInterval(timer);
  }, [showing]);
  if (!live.length) return null;
  return (
    <Panel title="In progress">
      <ul className="divide-y divide-border border-t border-border">
        {live.map((transfer) => (
          <LiveRow
            key={transfer.id}
            transfer={transfer}
            outgoing={transfer.senderId === me}
            peer={peerOf(transfer)}
            isHost={session.host}
            progressStore={progressStore}
            onCancel={onCancel}
            reconnecting={transfer.files.some((file) => file.state === 'FAILED' && isUploading(file.id))}
          />
        ))}
      </ul>
    </Panel>
  );
}

/** One live transfer. Subscribes to its own progress, so an upload only re-renders its row. */
function LiveRow({
  transfer,
  outgoing,
  peer,
  isHost,
  progressStore,
  onCancel,
  reconnecting,
}: {
  transfer: Transfer;
  outgoing: boolean;
  peer: AvatarDevice;
  isHost: boolean;
  progressStore: ProgressStore;
  onCancel: (transfer: Transfer) => void;
  /** Wi-Fi dropped mid-upload and this device is about to continue it. */
  reconnecting: boolean;
}) {
  const rawPhase = phaseOf(transfer);
  const phase = rawPhase === 'saving' && !outgoing && !isHost && readyToSave(transfer, progressStore.bytes('download')) ? 'done' : rawPhase;
  const total = totalSize(transfer);
  const sent = useUploadedBytes(progressStore, transfer);
  const downloaded = useDownloadedBytes(progressStore, transfer);
  const percent = total ? (sent / total) * 100 : 100;
  // "Saving": a phone recipient is downloading what's already on the server.
  const downloadPercent = total ? (downloaded / total) * 100 : 100;
  const speed = progressStore.speedOf(transfer.id, phase === 'saving' ? 'download' : 'upload');
  const detail = (() => {
    switch (phase) {
      case 'waiting':
        if (peer.online === false) return { tone: 'warning' as const, text: `${peer.name} is away. They’ll be asked when they open AndroidDrop.` };
        return { tone: 'warning' as const, text: percent < 100 ? `Waiting for ${peer.name} · uploading ${Math.floor(percent)}%` : `Waiting for ${peer.name} to accept` };
      case 'transferring':
        return { tone: 'accent' as const, text: speed > 0 ? `${formatSpeed(speed)} · ${formatDuration((total - sent) / speed)} left` : `${Math.floor(percent)}%` };
      case 'saving': {
        if (!downloaded) return { tone: 'accent' as const, text: outgoing ? `Waiting for ${peer.name} to download` : 'Starting download…' };
        const verb = outgoing ? 'Sending' : 'Receiving';
        return {
          tone: 'accent' as const,
          text: speed > 0 ? `${verb} · ${formatSpeed(speed)} · ${formatDuration((total - downloaded) / speed)} left` : `${verb} · ${Math.floor(downloadPercent)}%`,
        };
      }
      case 'done':
        return { tone: 'success' as const, text: outgoing ? 'Delivered' : isHost ? `Saved to ${savedFolderName(transfer)}` : keepsInApp ? 'Received · Tap Save in Received' : 'Saved to Downloads' };
      case 'declined':
        return { tone: 'danger' as const, text: `${peer.name} declined` };
      case 'failed':
        return reconnecting
          ? { tone: 'accent' as const, text: 'Connection dropped · reconnecting to continue…' }
          : { tone: 'danger' as const, text: outgoing ? 'Some files failed to upload' : 'Interrupted' };
      default:
        return { tone: 'neutral' as const, text: '' };
    }
  })();
  const showBar = phase === 'transferring' || phase === 'saving' || (phase === 'waiting' && percent < 100);
  return (
    <li className="flex items-center gap-3 px-4 py-3 touch:py-3.5">
      <DeviceAvatar device={peer} size="md" />
      <div className="min-w-0 flex-1">
        <p className="truncate text-[13.5px] touch:text-[15.5px]">
          <span className="font-medium">{summarize(transfer)}</span>
          <span className="text-fg-muted">
            {outgoing ? ' to ' : ' from '}
            {peer.name}
          </span>
        </p>
        <div className="mt-1 flex items-center gap-3">
          <Status tone={detail.tone} className="min-w-0 truncate tabular-nums">
            {detail.text}
          </Status>
          {showBar && <ProgressBar value={phase === 'saving' ? downloadPercent : percent} className="ml-auto w-28 shrink-0 sm:w-40" />}
        </div>
      </div>
      {outgoing && (phase === 'waiting' || phase === 'transferring' || phase === 'saving') && (
        <Button variant="ghost" size="icon-xs" aria-label={`Cancel sending ${summarize(transfer)}`} onClick={() => onCancel(transfer)}>
          <X />
        </Button>
      )}
    </li>
  );
}

/** A card with its title inside, so cards side by side share the same top and bottom edges as the device stage. */
function Panel({ title, className, children }: { title: string; className?: string; children: ReactNode }) {
  return (
    <section aria-label={title} className={cn('flex flex-col overflow-hidden rounded-2xl border border-border bg-bg', className)}>
      <h2 className="px-4 pt-3.5 pb-2.5 text-[13px] font-semibold touch:pt-4 touch:pb-3 touch:text-[16px]">{title}</h2>
      {children}
    </section>
  );
}

/** Notes and links in both directions, oldest first like a chat. The server keeps them only until it stops. */
function Messages({ session, transfers, peerOf, grow, onSendText, onAccept, onDecline, onCopy }: Props & { grow: boolean }) {
  const now = useNow(30_000);
  const list = useRef<HTMLDivElement>(null);
  const me = session.device.id;
  const messages = useMemo(
    () =>
      transfers
        .filter((transfer) => transfer.text !== null && isVisibleMessage(transfer, me))
        .sort((a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt)),
    [transfers, me],
  );
  const lastSent = messages.findLast((transfer) => transfer.senderId === me);

  // Keep the newest message in view, as a chat does.
  useLayoutEffect(() => {
    list.current?.scrollTo({ top: list.current.scrollHeight });
  }, [messages.length]);

  return (
    <Panel title="Messages" className={cn(grow && 'flex-1')}>
      {/* When the panel fills its column, the list scrolls inside it instead of stretching the page. */}
      <div className={cn('border-t border-border', grow && 'relative min-h-44 flex-1')}>
        <div ref={list} className={cn('overflow-y-auto overscroll-contain px-4 py-4', grow ? 'absolute inset-0' : 'max-h-80')}>
          {messages.length === 0 ? (
            <div className="grid h-full min-h-28 place-content-center text-center">
              <p className="text-[13px] font-medium text-fg-muted touch:text-[15px]">No messages yet</p>
              <p className="mt-0.5 text-[12px] text-fg-subtle touch:text-[13.5px]">Cleared when AndroidDrop stops.</p>
            </div>
          ) : (
            <ol>
              {messages.map((transfer, index) => {
                const previous = messages[index - 1];
                const peer = peerOf(transfer);
                // A quiet heading when the conversation moves to another device or picks up after a pause.
                const heading =
                  !previous ||
                  peerOf(previous).id !== peer.id ||
                  Date.parse(transfer.createdAt) - Date.parse(previous.createdAt) > 15 * 60_000;
                const grouped = !heading && previous.senderId === transfer.senderId;
                return (
                  <li key={transfer.id} className={cn(index > 0 && (grouped ? 'mt-1' : 'mt-3'))}>
                    {heading && (
                      <p className={cn('mb-2 text-center text-[11px] text-fg-subtle touch:text-[12.5px]', index > 0 && 'mt-5')}>
                        <span className="font-medium text-fg-muted">{peer.name}</span> · {dayLabel(transfer.createdAt, now)}, {clockTime(transfer.createdAt)}
                      </p>
                    )}
                    <Message
                      transfer={transfer}
                      outgoing={transfer.senderId === me}
                      showDelivered={transfer === lastSent}
                      onAccept={onAccept}
                      onDecline={onDecline}
                      onCopy={onCopy}
                    />
                  </li>
                );
              })}
            </ol>
          )}
        </div>
      </div>
      <NoteComposer onSend={onSendText} />
    </Panel>
  );
}

/** Withdrawn and expired messages never happened as far as the chat is concerned; declined ones only matter to their sender. */
function isVisibleMessage(transfer: Transfer, me: string): boolean {
  if (transfer.status === 'CANCELLED' || transfer.status === 'EXPIRED') return false;
  return transfer.status !== 'DECLINED' || transfer.senderId === me;
}

const clockTime = (iso: string) => new Date(iso).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });

const LONG_TEXT = 480;

function Message({
  transfer,
  outgoing,
  showDelivered,
  onAccept,
  onDecline,
  onCopy,
}: {
  transfer: Transfer;
  outgoing: boolean;
  /** Only the latest message you sent says "Delivered", as in Messages on a Mac. */
  showDelivered: boolean;
  onAccept: (transfer: Transfer) => Promise<void>;
  onDecline: (transfer: Transfer) => Promise<void>;
  onCopy: (text: string) => Promise<boolean>;
}) {
  const [copied, setCopied] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const text = transfer.text ?? '';
  const link = singleLink(text);
  const waiting = transfer.status === 'PENDING';
  const declined = transfer.status === 'DECLINED';
  const long = text.length > LONG_TEXT || text.split('\n').length > 8;
  const note = outgoing && (waiting ? 'Waiting' : declined ? 'Declined' : showDelivered ? 'Delivered' : null);

  const copy = async () => {
    if (await onCopy(text)) {
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
    }
  };

  return (
    <div className={cn('group flex flex-col', outgoing ? 'items-end' : 'items-start')}>
      <div className={cn('flex max-w-[88%] items-center gap-1', outgoing && 'flex-row-reverse')}>
        <div
          title={fullDate(transfer.createdAt)}
          className={cn(
            'min-w-0 rounded-[18px] px-3.5 py-2 text-[14px] leading-snug break-words whitespace-pre-wrap touch:rounded-[20px] touch:px-4 touch:py-2.5 touch:text-[16px]',
            outgoing ? 'bg-accent text-accent-fg' : 'bg-bg-muted text-fg',
            declined && 'opacity-45',
          )}
        >
          {link ? (
            <a href={link} target="_blank" rel="noreferrer" className="break-all underline decoration-current/30 underline-offset-2 hover:decoration-current">
              {link}
            </a>
          ) : (
            <span className={cn(long && !expanded && 'line-clamp-8')}>{text}</span>
          )}
          {long && !link && (
            <button type="button" onClick={() => setExpanded(!expanded)} className="mt-1 block text-[12.5px] font-medium opacity-60 hover:opacity-100 touch:py-1 touch:text-[14px]">
              {expanded ? 'Show less' : 'Show more'}
            </button>
          )}
        </div>
        <Tip label={copied ? 'Copied' : link ? 'Copy link' : 'Copy'}>
          <Button
            variant="ghost"
            size="icon-xs"
            aria-label={link ? 'Copy link' : 'Copy text'}
            onClick={() => void copy()}
            className={cn('text-fg-subtle', !copied && 'reveal-on-hover')}
          >
            {copied ? <Check className="text-success" /> : <Copy />}
          </Button>
        </Tip>
      </div>
      {note && <p className={cn('mt-1 px-1.5 text-[11px] text-fg-subtle touch:text-[12.5px]', declined && 'text-danger')}>{note}</p>}
      {waiting && !outgoing && (
        <div className="mt-1.5 flex items-center gap-1">
          <Button variant="primary" size="xs" onClick={() => void onAccept(transfer)}>
            {link ? 'Open' : 'Accept'}
          </Button>
          <Button variant="ghost" size="xs" onClick={() => void onDecline(transfer)}>
            Decline
          </Button>
        </div>
      )}
    </div>
  );
}

/** Enter sends on a computer; on a touch keyboard Enter is a new line and the button sends. */
const ENTER_SENDS = typeof window !== 'undefined' && window.matchMedia('(hover: hover) and (pointer: fine)').matches;

function NoteComposer({ onSend }: { onSend: (text: string) => Promise<boolean> }) {
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const input = useRef<HTMLTextAreaElement>(null);

  // Grow with the text, up to about six lines.
  useLayoutEffect(() => {
    const element = input.current;
    if (!element) return;
    element.style.height = 'auto';
    element.style.height = `${Math.min(element.scrollHeight, 150)}px`;
  }, [text]);

  const send = async (event?: FormEvent) => {
    event?.preventDefault();
    if (!text.trim() || sending) return;
    setSending(true);
    if (await onSend(text)) setText('');
    setSending(false);
  };

  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key !== 'Enter' || event.nativeEvent.isComposing) return;
    if (event.metaKey || event.ctrlKey || (ENTER_SENDS && !event.shiftKey)) {
      event.preventDefault();
      void send();
    }
  };

  return (
    <form onSubmit={send} className="flex items-end gap-2 border-t border-border p-2.5 touch:p-3">
      <textarea
        ref={input}
        value={text}
        onChange={(event) => setText(event.target.value)}
        onKeyDown={onKeyDown}
        rows={1}
        aria-label="Note or link to send"
        placeholder="Write a note or paste a link"
        className="block max-h-[150px] min-h-9 flex-1 resize-none rounded-[18px] border border-border bg-bg-subtle px-3.5 py-[7px] text-[14px] leading-5 outline-none transition-colors duration-100 placeholder:text-fg-subtle focus:border-border-strong focus:bg-bg touch:min-h-11 touch:rounded-[22px] touch:px-4 touch:py-[11px] touch:text-[16px] touch:leading-[22px]"
      />
      <Button type="submit" variant="primary" size="icon" aria-label="Send" className="rounded-full" disabled={!text.trim() || sending}>
        {sending ? <Spinner className="size-4" /> : <ArrowUp />}
      </Button>
    </form>
  );
}
