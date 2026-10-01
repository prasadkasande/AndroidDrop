import { Check } from 'lucide-react';
import type { Device, PairingInfo } from '../lib/api';
import { LazyPairingPanel } from './LazyPairingPanel';
import { Modal } from './ui/modal';

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  info: PairingInfo | null;
  hostName: string;
  onRotate: () => void;
  /** Set once a phone pairs while the dialog is open; shows a confirmation before closing. */
  paired: Device | null;
}

export function PairDialog({ open, onOpenChange, info, hostName, onRotate, paired }: Props) {
  return (
    <Modal
      open={open}
      onOpenChange={onOpenChange}
      title="Pair a device"
      description="A device only needs to pair once. After that it reconnects on its own."
      className="sm:max-w-[580px]"
    >
      {paired ? (
        <div className="flex flex-col items-center py-10 text-center" role="status">
          <span className="grid size-10 place-items-center rounded-full bg-success-subtle text-success">
            <Check className="size-5" strokeWidth={2.25} />
          </span>
          <p className="mt-4 text-[14px] font-medium">{paired.name} is connected</p>
          <p className="mt-1 text-[13px] text-fg-muted">You can send files to it now.</p>
        </div>
      ) : (
        <LazyPairingPanel info={info} hostName={hostName} onRotate={onRotate} />
      )}
    </Modal>
  );
}
