import { useState } from 'react';
import { Download, ExternalLink, FolderOpen } from 'lucide-react';
import { fileUrl, type Transfer, type TransferFile } from '../lib/api';
import { formatBytes, fullDate, previewKindOf } from '../lib/format';
import { buttonClasses, Button } from './ui/button';
import { Modal } from './ui/modal';

export interface PreviewTarget {
  transfer: Transfer;
  file: TransferFile;
}

interface Props {
  target: PreviewTarget | null;
  onClose: () => void;
  isHost: boolean;
  fileManager: string;
  describeSender: (transfer: Transfer) => string;
  onReveal: (transfer: Transfer, file: TransferFile) => void;
  onSave: (transfer: Transfer, file: TransferFile) => void;
}

/** In-app viewer for photos, video and audio. */
export function PreviewDialog({ target, onClose, isHost, fileManager, describeSender, onReveal, onSave }: Props) {
  // Keep showing the last file while the close animation plays.
  const [shown, setShown] = useState(target);
  if (target && target !== shown) setShown(target);
  if (!shown) return null;

  const { transfer, file } = shown;
  const kind = previewKindOf(file.mimeType);
  const url = fileUrl(transfer, file);

  return (
    <Modal
      open={target !== null}
      onOpenChange={(open) => !open && onClose()}
      title={file.name}
      description={`${formatBytes(file.size)} · ${describeSender(transfer)} · ${fullDate(transfer.createdAt)}`}
      className="sm:max-w-3xl"
      footer={
        <>
          <a href={url} target="_blank" rel="noreferrer" className={buttonClasses({ variant: 'ghost', size: 'sm', className: 'sm:mr-auto' })}>
            <ExternalLink /> Open in new tab
          </a>
          {isHost && file.state === 'SAVED' && (
            <Button variant="primary" size="sm" onClick={() => onReveal(transfer, file)}>
              <FolderOpen /> Show in {fileManager}
            </Button>
          )}
          {!isHost && (
            <Button variant="primary" size="sm" onClick={() => onSave(transfer, file)}>
              <Download /> Save
            </Button>
          )}
        </>
      }
    >
      <div className="grid min-h-40 place-items-center overflow-hidden rounded-lg border border-border bg-bg-subtle">
        {kind === 'image' && <img src={url} alt={file.name} className="max-h-[62dvh] w-auto max-w-full object-contain" />}
        {kind === 'video' && <video src={url} controls autoPlay playsInline className="max-h-[62dvh] w-full bg-black" />}
        {kind === 'audio' && <audio src={url} controls autoPlay className="w-full p-6" />}
      </div>
    </Modal>
  );
}
