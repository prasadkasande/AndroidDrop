import { ChevronRight } from 'lucide-react';
import type { Device } from '../lib/api';
import { platformLabel } from '../lib/format';
import { DeviceAvatar } from './DeviceAvatar';
import { Status } from './ui/badge';
import { Modal } from './ui/modal';

interface Props {
  open: boolean;
  summary: string;
  devices: Device[];
  onChoose: (deviceId: string | null) => void;
}

/** "Send to…" picker, shown only when there's more than one device to choose from. */
export function RecipientSheet({ open, summary, devices, onChoose }: Props) {
  return (
    <Modal open={open} onOpenChange={(next) => !next && onChoose(null)} title="Send to" description={summary} className="sm:max-w-sm">
      <ul className="-mx-2 space-y-px">
        {devices.map((device) => (
          <li key={device.id}>
            <button
              type="button"
              onClick={() => onChoose(device.id)}
              className="group flex w-full items-center gap-3 rounded-lg px-2 py-2 text-left outline-none hover:bg-bg-muted focus-visible:bg-bg-muted touch:rounded-xl touch:py-3 active:bg-bg-muted"
            >
              <DeviceAvatar device={device} size="md" />
              <span className="min-w-0 flex-1">
                <span className="block truncate text-[14px] touch:text-[16px] font-medium">{device.name}</span>
                <span className="block text-[12.5px] touch:text-[14px] text-fg-muted">{platformLabel(device.platform)}</span>
              </span>
              <Status tone={device.online ? 'success' : 'neutral'}>{device.online ? 'Nearby' : 'Away'}</Status>
              <ChevronRight className="size-4 text-fg-subtle touch:size-5" />
            </button>
          </li>
        ))}
      </ul>
    </Modal>
  );
}
