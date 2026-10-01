import { QrCode, Trash2 } from 'lucide-react';
import type { Device } from '../lib/api';
import { platformLabel } from '../lib/format';
import { DeviceAvatar } from './DeviceAvatar';
import { Status } from './ui/badge';
import { Button } from './ui/button';
import { Modal } from './ui/modal';
import { Tip } from './ui/tooltip';

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  devices: Device[];
  onPair: () => void;
  onRevoke: (device: Device) => void;
}

/** The host's list of paired devices, from Settings. */
export function DevicesDialog({ open, onOpenChange, devices, onPair, onRevoke }: Props) {
  const paired = (iso: string) => new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });

  return (
    <Modal
      open={open}
      onOpenChange={onOpenChange}
      title="Devices"
      description="Devices paired with this computer."
      footer={
        <Button variant="primary" size="sm" onClick={onPair}>
          <QrCode /> Pair a device
        </Button>
      }
    >
      <ul className="-mx-1 divide-y divide-border">
        {devices.map((device) => (
          <li key={device.id} className="flex items-center gap-3 px-1 py-3">
            <DeviceAvatar device={device} size="md" />
            <div className="min-w-0 flex-1">
              <p className="flex items-center gap-2 text-[14px] touch:text-[16px]">
                <span className="truncate font-medium">{device.name}</span>
                {device.host && <span className="text-[12.5px] text-fg-subtle touch:text-[14px]">This computer</span>}
              </p>
              <p className="truncate text-[12.5px] text-fg-muted touch:text-[14px]">
                {platformLabel(device.platform)} · {device.host ? 'Runs AndroidDrop' : `Paired ${paired(device.pairedAt)}`}
              </p>
            </div>
            <Status tone={device.online ? 'success' : 'neutral'}>{device.online ? 'Nearby' : 'Away'}</Status>
            {!device.host && (
              <Tip label="Remove device">
                <Button variant="danger-ghost" size="icon-xs" aria-label={`Remove ${device.name}`} onClick={() => onRevoke(device)}>
                  <Trash2 />
                </Button>
              </Tip>
            )}
          </li>
        ))}
      </ul>
    </Modal>
  );
}
