import { memo, useState } from 'react';
import { Check, Copy, Download, ExternalLink, Eye, FolderOpen, Link2, MoreHorizontal, RotateCcw, Trash2, X } from 'lucide-react';
import { previewImageUrl, type SaveFolder, type Transfer, type TransferFile } from '../lib/api';
import { cn } from '../lib/cn';
import { formatBytes, fullDate, hostnameOf, previewKindOf, timeAgo } from '../lib/format';
import { useDownloadedBytes, useFileProgress, useUploadedBytes, type ProgressStore } from '../lib/progress';
import { keepsInApp, keptInApp, readyToSave, savedOf, useSavedFiles } from '../lib/phoneSave';
import { describe, isStored, phaseOf, savableFiles, savedFolderName, singleLink, splitMedia, summarize, totalSize, type Phase } from '../lib/transfers';
import { DeviceAvatar, type AvatarDevice } from './DeviceAvatar';
import { FileTile } from './FileTile';
import { FolderPicker, type FolderChoices } from './FolderPicker';
import { Status } from './ui/badge';
import { Button, buttonClasses } from './ui/button';
import { ProgressBar } from './ui/field';
import { Menu, MenuContent, MenuItem, MenuSeparator, MenuTrigger } from './ui/menu';
import { Tip } from './ui/tooltip';

export interface TransferActions {
  /** `folder`: where the host saves the files, if it picked one. */
  onAccept: (transfer: Transfer, folder?: string) => Promise<void>;
  onDecline: (transfer: Transfer) => Promise<void>;
  onCancel: (transfer: Transfer) => void;
  onRemove: (transfer: Transfer) => void;
  onReveal: (transfer: Transfer, file: TransferFile) => void;
  onSave: (transfer: Transfer, file: TransferFile) => void;
  /** Phone: every received file at once, into Downloads. */
  onSaveAll: (transfer: Transfer) => void;
  onPreview: (transfer: Transfer, file: TransferFile) => void;
  onCopy: (text: string) => Promise<boolean>;
  onRetry: (fileId: string) => void;
  canRetry: (fileId: string) => boolean;
  /** This device is still uploading the file, or reconnecting to continue it. */
  isUploading: (fileId: string) => boolean;
}

interface Props extends TransferActions {
  transfer: Transfer;
  direction: 'in' | 'out';
  /** The device at the other end. */
  peer: AvatarDevice;
  isHost: boolean;
  fileManager: string;
  progressStore: ProgressStore;
  now: number;
  /** Phone layout: tighter rows and smaller previews. */
  compact: boolean;
  /** Folders the host can save into; null on a phone. */
  folders: FolderChoices | null;
}

const THUMB_LIMIT = 8;

/** "A link to Pixel" rather than "a link to Pixel" when the row starts with it. */
const capitalize = (text: string) => text.charAt(0).toUpperCase() + text.slice(1);

export const TransferRow = memo(function TransferRow(props: Props) {
  const { transfer, direction, peer, isHost, fileManager, progressStore, now, compact } = props;
  const [showAll, setShowAll] = useState(false);
  const [copied, setCopied] = useState(false);
  const [folder, setFolder] = useState<SaveFolder | null>(null);
  const incoming = direction === 'in';
  // A phone's big files wait on the computer until Save downloads them; until then the transfer reads as received.
  const rawPhase = phaseOf(transfer);
  const phase = rawPhase === 'saving' && incoming && !isHost && readyToSave(transfer, progressStore.bytes('download')) ? 'done' : rawPhase;
  const accepted = transfer.status === 'ACCEPTED';
  const total = totalSize(transfer);
  const uploaded = useUploadedBytes(progressStore, transfer);
  const downloaded = useDownloadedBytes(progressStore, transfer);
  // While "saving", a phone recipient is downloading; before that, the sender is uploading.
  const percent = total ? Math.floor(((phase === 'saving' ? downloaded : uploaded) / total) * 100) : 100;
  const link = transfer.text !== null ? singleLink(transfer.text) : null;
  // The recipient can open files only after accepting; the sender always can.
  const canOpen = !incoming || accepted;
  const { media, other } = splitMedia(transfer.files);
  const thumbs = showAll ? media : media.slice(0, THUMB_LIMIT);
  const firstSaved = transfer.files.find((file) => file.state === 'SAVED' && file.available);
  const allGone = transfer.files.length > 0 && transfer.files.every((file) => isStored(file.state) && !file.available);
  const inactive = phase === 'declined' || phase === 'cancelled' || phase === 'expired';
  const cancellable = !incoming && (phase === 'waiting' || phase === 'transferring' || phase === 'saving' || phase === 'failed');
  // Phone: received files wait in the app until the user saves them.
  const savedFiles = useSavedFiles();
  const savable = keepsInApp && !isHost && incoming && phase === 'done' ? savableFiles(transfer) : [];
  const savedCount = savedOf(savedFiles, savable).length;
  const saved = savable.length > 0 && savedCount === savable.length;
  const reconnecting = !incoming && transfer.files.some((file) => file.state === 'FAILED' && props.isUploading(file.id));
  const status =
    allGone && phase === 'done'
      ? ({ tone: 'neutral', label: 'Moved or deleted' } as const)
      : phase === 'failed' && reconnecting
        ? ({ tone: 'accent', label: 'Reconnecting…' } as const)
        : statusOf(phase, incoming, percent, peer, isHost, transfer, savedCount, savable.length);
  const target = folder ?? props.folders?.folders[0] ?? null;

  const copy = async () => {
    if (transfer.text !== null && (await props.onCopy(transfer.text))) {
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
    }
  };

  return (
    <li className={cn('group flex', compact ? 'gap-3 px-4 py-4' : 'gap-4 px-5 py-4', phase === 'waiting' && incoming && 'bg-accent-subtle/60')}>
      <DeviceAvatar device={peer} size="lg" className={cn(inactive && 'opacity-60')} />
      <div className="min-w-0 flex-1">
        <div className="flex items-start gap-2">
          <p className={cn('min-w-0 flex-1', compact ? 'line-clamp-2 text-[16px] leading-[22px]' : 'truncate text-[14.5px] leading-5', inactive && 'text-fg-muted')}>
            {incoming ? (
              <>
                <span className="font-medium">{peer.name}</span>
                <span className="text-fg-muted">{phase === 'waiting' ? ' wants to share ' : ' sent '}</span>
                <span className="font-medium">{describe(transfer)}</span>
              </>
            ) : (
              <>
                <span className="font-medium">{capitalize(describe(transfer))}</span>
                <span className="text-fg-muted"> to </span>
                <span className="font-medium">{peer.name}</span>
              </>
            )}
          </p>
          <time dateTime={transfer.createdAt} title={fullDate(transfer.createdAt)} className="shrink-0 pt-px text-[12.5px] text-fg-subtle tabular-nums touch:text-[13.5px]">
            {timeAgo(transfer.createdAt, now)}
          </time>
          <Menu>
            <MenuTrigger asChild>
              <Button variant="ghost" size="icon-xs" aria-label={`More actions for ${summarize(transfer)}`} className="-my-1 -mr-1.5 reveal-on-hover data-[state=open]:bg-bg-muted data-[state=open]:opacity-100">
                <MoreHorizontal />
              </Button>
            </MenuTrigger>
            <MenuContent className="min-w-48">
              {isHost && firstSaved && (
                <MenuItem onSelect={() => props.onReveal(transfer, firstSaved)}>
                  <FolderOpen /> Show in {fileManager}
                </MenuItem>
              )}
              {transfer.text !== null && canOpen && (
                <MenuItem onSelect={() => void copy()}>
                  <Copy /> {link ? 'Copy link' : 'Copy text'}
                </MenuItem>
              )}
              {cancellable && (
                <MenuItem onSelect={() => props.onCancel(transfer)}>
                  <X /> Cancel transfer
                </MenuItem>
              )}
              {((isHost && firstSaved) || (transfer.text !== null && canOpen) || cancellable) && <MenuSeparator />}
              <MenuItem destructive onSelect={() => props.onRemove(transfer)}>
                <Trash2 /> Remove from history
              </MenuItem>
            </MenuContent>
          </Menu>
        </div>

        <div className="mt-0.5 flex items-center gap-2 text-[12.5px] text-fg-muted touch:mt-1 touch:text-[14px]">
          <Status tone={status.tone} className="tabular-nums">
            {status.label}
          </Status>
          {transfer.files.length > 0 && (
            <>
              <span className="text-fg-subtle" aria-hidden="true">
                ·
              </span>
              <span className="tabular-nums">{formatBytes(total)}</span>
            </>
          )}
        </div>

        {(phase === 'transferring' || phase === 'saving' || (phase === 'waiting' && percent < 100 && transfer.files.length > 0)) && (
          <ProgressBar value={percent} className="mt-2.5 max-w-md" />
        )}

        {phase === 'waiting' && incoming && (
          <div className={cn('mt-3', compact ? 'grid grid-cols-2 gap-2' : 'flex flex-wrap items-center gap-2')}>
            <Button size="sm" onClick={() => void props.onDecline(transfer)}>
              Decline
            </Button>
            <Button variant="primary" size="sm" onClick={() => void props.onAccept(transfer, target && !target.isDefault ? target.path : undefined)}>
              Accept
            </Button>
            {props.folders && target && transfer.files.length > 0 && (
              <span className="flex min-w-0 items-center gap-1.5 text-[12.5px] text-fg-muted">
                into <FolderPicker choices={props.folders} value={target} onChange={setFolder} align="start" />
              </span>
            )}
          </div>
        )}
        {savable.length > 0 && (
          <div className={cn('mt-3', compact ? 'flex flex-col gap-1.5' : 'flex flex-wrap items-center gap-x-3 gap-y-2')}>
            <Button variant={saved ? 'secondary' : 'primary'} size={compact ? 'md' : 'sm'} onClick={() => props.onSaveAll(transfer)} className={cn(compact && 'w-full')}>
              <Download /> {saved ? 'Save again' : savedCount ? `Save ${savable.length - savedCount} more` : savable.length === 1 ? 'Save' : `Save all ${savable.length}`}
            </Button>
            {!saved && <span className={cn('text-[12.5px] text-fg-muted', compact && 'text-center text-[13.5px]')}>to Downloads</span>}
          </div>
        )}
        {phase === 'waiting' && !incoming && (
          <div className="mt-2 flex items-center gap-3 text-[12.5px] text-fg-muted touch:text-[14px]">
            <span>{peer.online === false ? `${peer.name} is away. They’ll be asked when they open AndroidDrop.` : `Waiting for ${peer.name} to respond.`}</span>
            <button type="button" onClick={() => props.onCancel(transfer)} className="font-medium text-fg hover:underline touch:-my-2 touch:py-2">
              Cancel
            </button>
          </div>
        )}

        {transfer.text !== null && (
          <div className="mt-3 max-w-3xl rounded-lg border border-border bg-bg-subtle touch:rounded-xl">
            {link ? (
              <a href={canOpen ? link : undefined} target="_blank" rel="noreferrer" className="flex min-w-0 items-center gap-3 px-3 py-2.5">
                <Link2 className="size-4 shrink-0 text-fg-subtle" />
                <span className="min-w-0">
                  <span className="block truncate text-[13.5px] font-medium touch:text-[15px]">{hostnameOf(link)}</span>
                  <span className="block truncate text-[12.5px] text-fg-muted touch:text-[14px]">{link}</span>
                </span>
              </a>
            ) : (
              <p className={cn('px-3 py-2.5 text-[13.5px] break-words whitespace-pre-wrap touch:text-[15.5px]', !canOpen && 'line-clamp-2')}>{transfer.text}</p>
            )}
            {canOpen && (
              <div className="flex gap-1 border-t border-border px-1.5 py-1">
                <Button variant="ghost" size="xs" onClick={() => void copy()}>
                  {copied ? <Check /> : <Copy />} {copied ? 'Copied' : 'Copy'}
                </Button>
                {link && (
                  <a href={link} target="_blank" rel="noreferrer" className={buttonClasses({ variant: 'ghost', size: 'xs' })}>
                    <ExternalLink /> Open
                  </a>
                )}
              </div>
            )}
          </div>
        )}

        {thumbs.length > 0 && (
          <div className={cn('mt-3', compact ? 'grid grid-cols-4 gap-1.5' : 'flex flex-wrap gap-2')}>
            {thumbs.map((file) => (
              <Thumb key={file.id} transfer={transfer} file={file} canOpen={canOpen} progressStore={progressStore} compact={compact} onPreview={props.onPreview} />
            ))}
            {!showAll && media.length > THUMB_LIMIT && (
              <button
                type="button"
                onClick={() => setShowAll(true)}
                className={cn(
                  'grid place-items-center rounded-lg border border-border bg-bg-subtle text-[13px] font-medium text-fg-muted hover:bg-bg-muted hover:text-fg',
                  compact ? 'aspect-square w-full text-[16px]' : 'size-24',
                )}
              >
                +{media.length - THUMB_LIMIT}
              </button>
            )}
          </div>
        )}

        {other.length > 0 && (
          <ul className={cn('mt-3 grid gap-2', other.length > 1 && 'sm:grid-cols-2 2xl:grid-cols-3')}>
            {other.map((file) => (
              <FileRow key={file.id} file={file} canOpen={canOpen} {...props} />
            ))}
          </ul>
        )}
      </div>
    </li>
  );
});

function Thumb({
  transfer,
  file,
  canOpen,
  progressStore,
  compact,
  onPreview,
}: {
  transfer: Transfer;
  file: TransferFile;
  canOpen: boolean;
  progressStore: ProgressStore;
  compact: boolean;
  onPreview: (transfer: Transfer, file: TransferFile) => void;
}) {
  const [broken, setBroken] = useState(false);
  const url = broken ? null : previewImageUrl(transfer, file);
  const missing = isStored(file.state) && !file.available;
  const openable = canOpen && file.available;
  const received = useFileProgress(progressStore, file.id, 'upload');
  const percent = file.size ? (received / file.size) * 100 : 0;
  return (
    <button
      type="button"
      disabled={!openable}
      onClick={() => onPreview(transfer, file)}
      aria-label={`Preview ${file.name}`}
      title={file.name}
      className={cn(
        'relative overflow-hidden rounded-lg border border-border bg-bg-subtle outline-none focus-visible:ring-[3px] focus-visible:ring-accent/30 enabled:hover:opacity-90 disabled:cursor-default',
        compact ? 'aspect-square w-full' : 'size-24',
      )}
    >
      {url ? (
        <img src={url} alt="" loading="lazy" decoding="async" className="size-full object-cover" onError={() => setBroken(true)} />
      ) : (
        <FileTile name={file.name} mimeType={file.mimeType} size="md" muted={missing} className="m-auto border-0 bg-transparent" />
      )}
      {file.state === 'UPLOADING' && (
        <span className="absolute inset-0 grid place-items-center bg-bg/75 text-[11px] font-medium text-fg tabular-nums touch:text-[13px]">{Math.floor(percent)}%</span>
      )}
      {(file.state === 'REMOVED' || file.state === 'FAILED' || missing) && (
        <span className="absolute inset-x-0 bottom-0 bg-bg/90 py-0.5 text-center text-[10.5px] font-medium text-fg-muted touch:text-[12px]">
          {file.state === 'FAILED' ? 'Failed' : missing ? 'Moved' : 'Removed'}
        </span>
      )}
    </button>
  );
}

function FileRow({ file, transfer, direction, isHost, fileManager, progressStore, canOpen, onReveal, onSave, onRetry, canRetry, isUploading, onPreview }: Props & { file: TransferFile; canOpen: boolean }) {
  const incoming = direction === 'in';
  const inDownloads = useSavedFiles().has(file.id);
  const received = useFileProgress(progressStore, file.id, 'upload');
  const downloaded = useFileProgress(progressStore, file.id, 'download');
  const percent = file.size ? Math.floor((received / file.size) * 100) : 0;
  const missing = isStored(file.state) && !file.available;
  const downloading = transfer.status === 'ACCEPTED' && downloaded > 0;
  const state = missing ? 'Moved or deleted' : {
    UPLOADING: `${incoming ? 'Receiving' : 'Sending'} ${percent}%`,
    READY: downloading
      ? `${incoming ? 'Receiving' : 'Sending'} ${Math.floor((downloaded / Math.max(file.size, 1)) * 100)}%`
      : !incoming
        ? 'Uploaded'
        : !canOpen
          ? 'Ready'
          : isHost
            ? 'Saving…'
            : keepsInApp && !keptInApp(file)
              ? inDownloads
                ? 'Starting download…'
                : 'Not saved yet'
              : 'Receiving…',
    SAVED: 'Saved',
    DELIVERED: incoming ? (keepsInApp && !inDownloads ? 'Not saved yet' : 'In Downloads') : 'Delivered',
    // The sender picks a broken-off upload up again where it stopped.
    FAILED: incoming ? 'Interrupted' : isUploading(file.id) ? 'Reconnecting…' : 'Upload failed',
    REMOVED: 'No longer available',
  }[file.state];

  return (
    <li className="flex min-w-0 items-center gap-3 rounded-lg border border-border bg-bg px-3 py-2.5 touch:rounded-xl touch:py-2 touch:pr-1.5">
      <FileTile name={file.name} mimeType={file.mimeType} size="md" muted={file.state === 'REMOVED' || missing} />
      <div className="min-w-0 flex-1">
        <p className="truncate text-[13px] font-medium touch:text-[15px]" title={file.name}>
          {file.name}
        </p>
        <p className={cn('text-[12px] text-fg-subtle tabular-nums touch:text-[13.5px]', file.state === 'FAILED' && 'text-danger')}>
          {formatBytes(file.size)} · {state}
        </p>
      </div>
      <div className="flex items-center">
        {canOpen && file.available && previewKindOf(file.mimeType) && (
          <Tip label="Preview">
            <Button variant="ghost" size="icon-xs" aria-label={`Preview ${file.name}`} onClick={() => onPreview(transfer, file)}>
              <Eye />
            </Button>
          </Tip>
        )}
        {isHost && file.state === 'SAVED' && file.available && (
          <Tip label={`Show in ${fileManager}`}>
            <Button variant="ghost" size="icon-xs" aria-label={`Show ${file.name} in ${fileManager}`} onClick={() => onReveal(transfer, file)}>
              <FolderOpen />
            </Button>
          </Tip>
        )}
        {!isHost && incoming && canOpen && file.available && (file.state === 'READY' || file.state === 'DELIVERED') && (
          <Tip label="Save to Downloads">
            <Button variant="ghost" size="icon-xs" aria-label={`Save ${file.name}`} onClick={() => onSave(transfer, file)}>
              <Download />
            </Button>
          </Tip>
        )}
        {!incoming && file.state === 'FAILED' && canRetry(file.id) && (
          <Tip label="Try again">
            <Button variant="ghost" size="icon-xs" aria-label={`Retry ${file.name}`} onClick={() => onRetry(file.id)}>
              <RotateCcw />
            </Button>
          </Tip>
        )}
      </div>
    </li>
  );
}

function statusOf(
  phase: Phase,
  incoming: boolean,
  percent: number,
  peer: AvatarDevice,
  isHost: boolean,
  transfer: Transfer,
  savedCount: number,
  savableCount: number,
): { tone: 'neutral' | 'accent' | 'success' | 'warning' | 'danger'; label: string } {
  switch (phase) {
    case 'waiting':
      return incoming ? { tone: 'accent', label: 'Awaiting your response' } : { tone: 'warning', label: 'Waiting for response' };
    case 'transferring':
      return { tone: 'accent', label: `${incoming ? 'Receiving' : 'Sending'} · ${percent}%` };
    case 'saving':
      return { tone: 'accent', label: `${incoming ? 'Receiving' : 'Sending'} · ${percent}%` };
    case 'done':
      // Notes and links aren't saved anywhere; accepting copies or opens them.
      if (transfer.text !== null) return { tone: 'success', label: incoming ? 'Accepted' : 'Delivered' };
      if (incoming && isHost) return { tone: 'success', label: `Saved to ${savedFolderName(transfer)}` };
      if (incoming && keepsInApp && savableCount) {
        if (savedCount === savableCount) return { tone: 'success', label: 'Saved to Downloads' };
        return { tone: 'accent', label: savedCount ? `Saved ${savedCount} of ${savableCount}` : 'Received · Not saved yet' };
      }
      if (incoming) return { tone: 'success', label: 'Saved to Downloads' };
      return { tone: 'success', label: peer.host ? `Saved on ${peer.name}` : 'Delivered' };
    case 'failed':
      // The sender may still pick it up again where it stopped.
      return incoming ? { tone: 'warning', label: 'Interrupted' } : { tone: 'danger', label: 'Upload failed' };
    case 'declined':
      return { tone: 'danger', label: 'Declined' };
    case 'cancelled':
      return { tone: 'neutral', label: 'Cancelled' };
    case 'expired':
      return { tone: 'neutral', label: 'Expired' };
  }
}
