import { useState, type ReactNode } from 'react';
import { Download } from 'lucide-react';
import { CERTIFICATE_URL, moveToSecure } from '../lib/secure';
import { Callout } from './ui/badge';
import { Button, buttonClasses } from './ui/button';
import { Spinner } from './ui/field';
import { Modal } from './ui/modal';

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** The HTTPS address to move to. */
  origin: string;
  serverName: string;
}

/** Walks a phone through installing AndroidDrop's certificate, then moves it to HTTPS. */
export function SecureSetupDialog({ open, onOpenChange, origin, serverName }: Props) {
  const [checking, setChecking] = useState(false);
  const [notYet, setNotYet] = useState(false);

  const check = async () => {
    setChecking(true);
    setNotYet(false);
    // On success the page moves to the HTTPS address, so there's nothing to reset.
    if (!(await moveToSecure(origin, true))) {
      setNotYet(true);
      setChecking(false);
    }
  };

  return (
    <Modal
      open={open}
      onOpenChange={onOpenChange}
      title="Set up a secure connection"
      description={`Once per phone. After that you can share to AndroidDrop from any app, and everything sent between this phone and ${serverName} is encrypted.`}
      footer={
        <Button variant="primary" size="sm" onClick={() => void check()} disabled={checking}>
          {checking && <Spinner className="size-3.5" />} I’ve installed it
        </Button>
      }
    >
      <ol className="space-y-4 text-[13.5px] touch:text-[15.5px]">
        <Step n={1} title="Download the certificate">
          <a href={CERTIFICATE_URL} download="AndroidDrop.crt" className={buttonClasses({ size: 'sm', className: 'mt-2' })}>
            <Download /> Download
          </a>
        </Step>
        <Step n={2} title="Install it in Settings">
          Open <b className="font-medium text-fg">Settings</b>, search for <b className="font-medium text-fg">CA certificate</b>, tap{' '}
          <b className="font-medium text-fg">Install anyway</b>, and choose <b className="font-medium text-fg">AndroidDrop.crt</b> from Downloads.
          Android may ask for your screen lock.
        </Step>
        <Step n={3} title="Come back here">
          Tap the button below. AndroidDrop switches to the secure address and keeps you paired.
        </Step>
      </ol>
      {notYet && (
        <Callout tone="warning" className="mt-4">
          This phone doesn’t trust the certificate yet. Check that you installed it as a <b className="font-medium">CA certificate</b> (not a VPN,
          app or Wi-Fi certificate), then try again.
        </Callout>
      )}
      <p className="mt-4 text-[12px] touch:text-[13.5px] text-fg-subtle">
        Android then shows a note that the network may be monitored. That’s its standard notice for any certificate you install yourself. The
        certificate’s private key never leaves {serverName}, and you can remove it any time in Settings.
      </p>
    </Modal>
  );
}

function Step({ n, title, children }: { n: number; title: string; children: ReactNode }) {
  return (
    <li className="flex gap-3">
      <span className="grid size-6 shrink-0 place-items-center rounded-full bg-bg-muted text-[12px] touch:text-[13.5px] font-semibold tabular-nums">{n}</span>
      <div className="min-w-0 pt-0.5">
        <p className="font-medium">{title}</p>
        <div className="mt-0.5 text-fg-muted">{children}</div>
      </div>
    </li>
  );
}
