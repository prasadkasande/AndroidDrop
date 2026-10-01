import { useState } from 'react';
import { Dialog } from 'radix-ui';
import { Link2, X } from 'lucide-react';
import { previewImageUrl, type SaveFolder, type Transfer } from '../lib/api';
import { formatBytes, hostnameOf } from '../lib/format';
import { useUploadedBytes, type ProgressStore } from '../lib/progress';
import { singleLink, splitMedia, summarize, totalSize } from '../lib/transfers';
import { DeviceAvatar, type AvatarDevice } from './DeviceAvatar';
import { FileTile } from './FileTile';
import { FolderPicker, type FolderChoices } from './FolderPicker';
import { Button } from './ui/button';
import { Spinner } from './ui/field';

interface Props {
  /** The oldest request still waiting for an answer, or null. */
  transfer: Transfer | null;
  sender: AvatarDevice | null;
  waiting: number;
  progressStore: ProgressStore;
  /** Where files are saved, for a phone (it can't choose). */
  destination: string;
  /** Folders the host can save into; null on a phone. */
  folders: FolderChoices | null;
  onAccept: (transfer: Transfer, folder?: string) => Promise<void>;
  onDecline: (transfer: Transfer) => Promise<void>;
  /** Closed without answering: it stays in Received with Accept/Decline buttons. */
  onLater: (transfer: Transfer) => void;
}

/** "Pixel wants to share 3 photos" with Decline / Accept. */
export function IncomingRequest({ transfer, sender, waiting, progressStore, destination, folders, onAccept, onDecline, onLater }: Props) {
  // Keep showing the last request (and who sent it) while the dialog fades out.
  const [last, setLast] = useState(transfer && sender ? { transfer, sender } : null);
  const [busy, setBusy] = useState<'accept' | 'decline' | null>(null);
  const [folder, setFolder] = useState<SaveFolder | null>(null);
  if (transfer && sender && transfer !== last?.transfer) {
    setLast({ transfer, sender });
    // Each request starts in the default folder.
    if (transfer.id !== last?.transfer.id) setFolder(null);
  }
  const uploaded = useUploadedBytes(progressStore, last?.transfer ?? null);
  if (!last) return null;

  const { transfer: shown, sender: from } = last;
  const total = totalSize(shown);
  const stillSending = shown.files.some((file) => file.state === 'UPLOADING');
  const arrived = Math.floor((uploaded / Math.max(total, 1)) * 100);
  const target = shown.files.length > 0 ? (folder ?? folders?.folders[0] ?? null) : null;
  const hint =
    shown.text !== null ? (singleLink(shown.text) ? 'Accepting opens the link.' : 'Accepting copies the note to your clipboard.') : target ? null : destination;

  const act = async (kind: 'accept' | 'decline') => {
    setBusy(kind);
    try {
      await (kind === 'accept' ? onAccept(shown, target && !target.isDefault ? target.path : undefined) : onDecline(shown));
    } finally {
      setBusy(null);
    }
  };

  return (
    <Dialog.Root open={transfer !== null} onOpenChange={(open) => !open && onLater(shown)}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-overlay data-[state=closed]:animate-fade-out data-[state=open]:animate-fade-in" />
        <Dialog.Content className="fixed top-1/2 left-1/2 z-50 w-[calc(100%-2rem)] max-w-[360px] -translate-x-1/2 -translate-y-1/2 rounded-2xl border border-border bg-bg px-5 pt-6 pb-5 text-center shadow-dialog outline-none data-[state=closed]:animate-pop-out data-[state=open]:animate-pop-in">
          <Dialog.Close asChild>
            <Button variant="ghost" size="icon-xs" aria-label="Decide later" className="absolute top-3 right-3">
              <X />
            </Button>
          </Dialog.Close>

          <DeviceAvatar device={from} size="lg" className="mx-auto" />
          <Dialog.Title className="mt-3 text-[15px] touch:text-[17px] leading-5 font-semibold">{from.name}</Dialog.Title>
          <Dialog.Description className="mt-1 text-[13.5px] touch:text-[15.5px] text-fg-muted">
            would like to share <span className="font-medium text-fg">{summarize(shown)}</span>
          </Dialog.Description>

          <Preview transfer={shown} />

          <div className="mt-3 flex flex-wrap items-center justify-center gap-x-1.5 gap-y-2 text-[12px] touch:text-[13.5px] text-fg-subtle tabular-nums">
            {stillSending && <Spinner className="size-3" />}
            {shown.files.length > 0 && `${formatBytes(total)}${stillSending ? ` · ${arrived}% arrived` : ''}`}
            {shown.files.length > 0 && (hint || target) && ' · '}
            {hint}
            {folders && target && (
              <>
                Save to <FolderPicker choices={folders} value={target} onChange={setFolder} />
              </>
            )}
          </div>

          <div className="mt-5 grid grid-cols-2 gap-2">
            <Button size="md" disabled={busy !== null} onClick={() => void act('decline')}>
              {busy === 'decline' && <Spinner className="size-3.5" />} Decline
            </Button>
            <Button variant="primary" size="md" disabled={busy !== null} onClick={() => void act('accept')} autoFocus>
              {busy === 'accept' && <Spinner className="size-3.5" />} Accept
            </Button>
          </div>
          {waiting > 1 && <p className="mt-3 text-[12px] touch:text-[13.5px] text-fg-subtle">{waiting - 1} more waiting</p>}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

/** Photo thumbnails, a short file list, or the note/link itself. */
function Preview({ transfer }: { transfer: Transfer }) {
  if (transfer.text !== null) {
    const link = singleLink(transfer.text);
    return link ? (
      <div className="mt-4 flex min-w-0 items-center gap-3 rounded-xl bg-bg-subtle px-3 py-2.5 text-left">
        <Link2 className="size-4 shrink-0 text-fg-subtle" />
        <span className="min-w-0">
          <span className="block truncate text-[13.5px] touch:text-[15.5px] font-medium">{hostnameOf(link)}</span>
          <span className="block truncate text-[12.5px] touch:text-[14px] text-fg-muted">{link}</span>
        </span>
      </div>
    ) : (
      <p className="mt-4 line-clamp-4 rounded-xl bg-bg-subtle px-3 py-2.5 text-left text-[13.5px] touch:text-[15.5px] break-words whitespace-pre-wrap">{transfer.text}</p>
    );
  }

  const { media } = splitMedia(transfer.files);
  const images = media.map((file) => ({ file, url: previewImageUrl(transfer, file) })).filter((entry): entry is { file: typeof entry.file; url: string } => entry.url !== null);
  if (images.length) {
    const shown = images.slice(0, 4);
    const extra = transfer.files.length - shown.length;
    return (
      <div className="mt-4 flex justify-center gap-2">
        {shown.map(({ file, url }, index) => (
          <div key={file.id} className="relative size-[68px] overflow-hidden rounded-xl bg-bg-subtle">
            <img src={url} alt="" className="size-full object-cover" />
            {index === shown.length - 1 && extra > 0 && (
              <span className="absolute inset-0 grid place-items-center bg-black/55 text-[13px] touch:text-[15px] font-medium text-white">+{extra}</span>
            )}
          </div>
        ))}
      </div>
    );
  }

  const listed = transfer.files.slice(0, 3);
  return (
    <ul className="mt-4 divide-y divide-border overflow-hidden rounded-xl bg-bg-subtle text-left">
      {listed.map((file) => (
        <li key={file.id} className="flex items-center gap-3 px-3 py-2 touch:py-2.5">
          <FileTile name={file.name} mimeType={file.mimeType} size="sm" className="touch:size-10 touch:[&_svg]:size-[18px]" />
          <span className="min-w-0 flex-1 truncate text-[13px] touch:text-[15px] font-medium">{file.name}</span>
          <span className="text-[12px] touch:text-[13.5px] text-fg-subtle tabular-nums">{formatBytes(file.size)}</span>
        </li>
      ))}
      {transfer.files.length > listed.length && (
        <li className="px-3 py-2 text-[12.5px] touch:text-[14px] text-fg-muted">and {transfer.files.length - listed.length} more</li>
      )}
    </ul>
  );
}
