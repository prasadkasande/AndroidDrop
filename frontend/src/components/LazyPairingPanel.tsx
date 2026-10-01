import { lazy, Suspense, type ComponentProps } from 'react';
import { Spinner } from './ui/field';

const PairingPanel = lazy(() => import('./PairingPanel').then((module) => ({ default: module.PairingPanel })));

/** The pairing panel, loaded when first shown: it brings the QR code library, which phones never need. */
export function LazyPairingPanel(props: ComponentProps<typeof PairingPanel>) {
  return (
    <Suspense fallback={<PairingPending />}>
      <PairingPanel {...props} />
    </Suspense>
  );
}

export function PairingPending() {
  return (
    <div className="flex items-center gap-2 py-10 text-[13px] text-fg-muted" aria-busy="true">
      <Spinner className="size-3.5" /> Preparing a pairing code…
    </div>
  );
}
