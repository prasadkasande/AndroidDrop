import { Laptop, Monitor, Smartphone, Tablet } from 'lucide-react';
import type { Platform } from '../lib/api';
import { cn } from '../lib/cn';

/** Soft pastel per device (stable across sessions), so each phone is recognisable at a glance. */
const PALETTE = [
  'bg-[#ffd8c2] text-[#8a4a24] dark:bg-[#4a3024] dark:text-[#ffc9a8]',
  'bg-[#d7e8ff] text-[#2a4f86] dark:bg-[#22324a] dark:text-[#b9d4ff]',
  'bg-[#cdebd8] text-[#2f6b47] dark:bg-[#213a2b] dark:text-[#a9dcbb]',
  'bg-[#e3dcff] text-[#4b3d8f] dark:bg-[#2e2848] dark:text-[#cfc3ff]',
  'bg-[#fbe8b0] text-[#7a5a12] dark:bg-[#3d3218] dark:text-[#f2d98a]',
  'bg-[#fbd3dc] text-[#8a2e48] dark:bg-[#45222c] dark:text-[#f5b8c8]',
];
const HOST = 'bg-[#e7e0d4] text-[#4a423a] dark:bg-[#3a342d] dark:text-[#d8cfc1]';

const SIZES = {
  sm: { box: 'size-7 touch:size-9', icon: 'size-3.5 touch:size-[18px]', dot: 'right-0 bottom-0 size-2 ring-2' },
  md: { box: 'size-9 touch:size-11', icon: 'size-[18px] touch:size-[22px]', dot: 'right-0 bottom-0 size-2.5 ring-2' },
  lg: { box: 'size-12', icon: 'size-6', dot: 'right-0 bottom-0 size-3 ring-2' },
  xl: { box: 'size-[72px]', icon: 'size-8', dot: 'right-0.5 bottom-0.5 size-3.5 ring-[3px]' },
  '2xl': { box: 'size-24', icon: 'size-10', dot: 'right-1 bottom-1 size-4 ring-[3px]' },
};

export interface AvatarDevice {
  id: string;
  name: string;
  platform: Platform;
  host?: boolean;
  online?: boolean;
}

function colourFor(device: AvatarDevice): string {
  if (device.host) return HOST;
  let hash = 0;
  for (const char of device.id) hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
  return PALETTE[hash % PALETTE.length];
}

function PlatformIcon({ device, className }: { device: Pick<AvatarDevice, 'name' | 'platform'>; className?: string }) {
  if (device.platform === 'MAC') return <Laptop className={className} strokeWidth={1.6} />;
  if (device.platform === 'ANDROID' || device.platform === 'IOS') {
    return /tablet|ipad|\btab\b/i.test(device.name) ? <Tablet className={className} strokeWidth={1.6} /> : <Smartphone className={className} strokeWidth={1.6} />;
  }
  return <Monitor className={className} strokeWidth={1.6} />;
}

interface Props {
  device: AvatarDevice;
  size?: keyof typeof SIZES;
  /** Show the online/offline dot. */
  status?: boolean;
  /** Colour of the gap around the dot; match the surface the avatar sits on. */
  ringClassName?: string;
  className?: string;
}

/** A pastel circle with the device's platform icon. */
export function DeviceAvatar({ device, size = 'md', status = false, ringClassName = 'ring-bg', className }: Props) {
  const s = SIZES[size];
  return (
    <span className={cn('relative inline-flex shrink-0', className)} aria-hidden="true">
      <span className={cn('grid place-items-center rounded-full', colourFor(device), s.box)}>
        <PlatformIcon device={device} className={s.icon} />
      </span>
      {status && <span className={cn('absolute rounded-full', s.dot, ringClassName, device.online ? 'bg-success' : 'bg-fg-subtle')} />}
    </span>
  );
}
