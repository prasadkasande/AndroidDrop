import { useState, type ReactNode } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import type { PairingInfo } from '../lib/api';
import { cn } from '../lib/cn';
import { useNow } from '../lib/useNow';
import { LOGO_URL } from './Brand';
import { PairingPending } from './LazyPairingPanel';
import { Callout } from './ui/badge';
import { Button } from './ui/button';
import { Spinner } from './ui/field';

function countdown(ms: number): string {
  const seconds = Math.ceil(ms / 1000);
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}

interface Props {
  info: PairingInfo | null;
  hostName: string;
  onRotate: () => void;
}

/** QR code, 6-digit code and instructions for pairing a phone. Host only. */
export function PairingPanel({ info, hostName, onRotate }: Props) {
  const now = useNow(1000);
  const [urlIndex, setUrlIndex] = useState(0);

  if (!info) return <PairingPending />;

  if (!info.urls.length) {
    return (
      <Callout tone="warning">
        {hostName} isn’t connected to Wi-Fi or Ethernet, so phones can’t reach it. Connect it to the same network as your phone.
      </Callout>
    );
  }

  const base = info.urls[Math.min(urlIndex, info.urls.length - 1)];
  const remaining = Math.max(0, Date.parse(info.expiresAt) - now);

  return (
    <div className="grid items-start gap-6 sm:grid-cols-[auto_1fr]">
      <div className="mx-auto rounded-xl border border-border bg-white p-3.5">
        <QRCodeSVG
          value={`${base}/?code=${info.code}`}
          size={168}
          level="Q"
          marginSize={0}
          bgColor="#ffffff"
          fgColor="#09090b"
          // Error correction level Q leaves room for the logo in the middle.
          imageSettings={{ src: LOGO_URL, width: 32, height: 32, excavate: true }}
          title="Scan to pair"
        />
      </div>

      <div className="min-w-0 space-y-4">
        <ol className="space-y-2.5 text-[13.5px] text-fg-muted">
          <Step n={1}>
            Connect your phone to the same Wi-Fi as <span className="text-fg">{hostName}</span>.
          </Step>
          <Step n={2}>Scan the code with the phone’s camera.</Step>
          <Step n={3}>
            Or open <span className="font-mono text-[12.5px] break-all text-fg">{base.replace(/^http:\/\//, '')}</span> and enter this code:
          </Step>
        </ol>

        <div className="flex gap-1.5" aria-label={`Pairing code ${info.code.split('').join(' ')}`} role="img">
          {info.code.split('').map((digit, index) => (
            <span
              key={index}
              className={cn('grid h-11 w-10 place-items-center rounded-lg border border-border bg-bg-subtle font-mono text-[20px] font-medium tabular-nums', index === 3 && 'ml-2')}
            >
              {digit}
            </span>
          ))}
        </div>

        <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-[12.5px] text-fg-muted tabular-nums">
          <span className="flex items-center gap-1.5">
            <Spinner className="size-3" /> Waiting for a device · expires in {countdown(remaining)}
          </span>
          <Button variant="ghost" size="xs" onClick={onRotate} className="-ml-1">
            New code
          </Button>
        </div>

        {info.urls.length > 1 && (
          <label className="flex items-center gap-2 text-[12.5px] text-fg-muted">
            Network
            <select
              value={urlIndex}
              onChange={(event) => setUrlIndex(Number(event.target.value))}
              className="h-8 min-w-0 flex-1 rounded-md border border-border bg-bg px-2 text-[12.5px] text-fg outline-none focus:border-accent"
            >
              {info.urls.map((url, index) => (
                <option key={url} value={index}>
                  {url.replace(/^http:\/\//, '')}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>
    </div>
  );
}

function Step({ n, children }: { n: number; children: ReactNode }) {
  return (
    <li className="flex gap-2.5">
      <span className="grid size-5 shrink-0 place-items-center rounded-full border border-border text-[11px] font-medium text-fg-muted tabular-nums">{n}</span>
      <span>{children}</span>
    </li>
  );
}
