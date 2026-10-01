import { useEffect, useRef, useState } from 'react';
import type { Transfer, TransferFile } from './api';
import { downloadFromHost, fetchIntoApp, keepsInApp, keptInApp } from './phoneSave';

interface Handlers {
  /** A transfer's files started coming in (only reported when they download the browser's way). */
  onStarted: (transfer: Transfer, count: number) => void;
  /** All of a transfer's files are in the app. */
  onReceived: (transfer: Transfer) => void;
  onFailed: (name: string) => void;
}

/**
 * On a phone, accepted files are fetched as soon as they are ready, so the user never has to tap "Download": into
 * the app when it can keep them (see phoneSave.ts), otherwise as browser downloads. Files too big to keep in the app
 * stay on the computer until Save. (The host doesn't need this: the server moves files into its folder.) One file
 * at a time; browser downloads are spaced out because browsers throttle bursts of them.
 *
 * Returns whether it's fetching, which needs the page kept open.
 */
export function useAutoSave(transfers: Transfer[], meId: string, enabled: boolean, handlers: Handlers): boolean {
  const [fetching, setFetching] = useState(false);
  const started = useRef(new Set<string>());
  const queue = useRef<{ transferId: string; fileId: string; last: boolean }[]>([]);
  const busy = useRef(false);
  const timer = useRef<number | undefined>(undefined);
  const latest = useRef({ transfers, handlers });

  useEffect(() => {
    latest.current = { transfers, handlers };
  });

  useEffect(() => {
    if (!enabled) return;
    for (const transfer of transfers) {
      if (transfer.recipientId !== meId || transfer.status !== 'ACCEPTED') continue;
      const fresh = transfer.files.filter((file) => file.state === 'READY' && !started.current.has(file.id));
      if (!fresh.length) continue;
      fresh.forEach((file) => started.current.add(file.id));
      // Files still uploading will come in a later batch; only the batch that finishes the transfer ends it.
      const last = !transfer.files.some((file) => file.state === 'UPLOADING' || (file.state === 'READY' && !started.current.has(file.id)));
      fresh.forEach((file, index) => queue.current.push({ transferId: transfer.id, fileId: file.id, last: last && index === fresh.length - 1 }));
      if (!keepsInApp) latest.current.handlers.onStarted(transfer, fresh.length);
    }

    /** The transfer and file as they are now: null once removed, cancelled or already fetched elsewhere. */
    const current = (transferId: string, fileId: string): [Transfer, TransferFile] | null => {
      const transfer = latest.current.transfers.find((candidate) => candidate.id === transferId);
      const file = transfer?.status === 'ACCEPTED' ? transfer.files.find((candidate) => candidate.id === fileId) : undefined;
      return transfer && file && file.state !== 'REMOVED' ? [transfer, file] : null;
    };

    const drain = async () => {
      const next = queue.current.shift();
      if (!next) {
        busy.current = false;
        setFetching(false);
        return;
      }
      busy.current = true;
      setFetching(true);
      const found = current(next.transferId, next.fileId);
      if (!found) {
        void drain();
        return;
      }
      const [transfer, file] = found;
      if (!keepsInApp) {
        downloadFromHost(transfer, file);
        timer.current = window.setTimeout(() => void drain(), 450);
        return;
      }
      try {
        // A file too big to keep in the app waits on the computer; Save downloads it from there.
        if (keptInApp(file)) await fetchIntoApp(transfer, file);
        if (next.last) latest.current.handlers.onReceived(latest.current.transfers.find((candidate) => candidate.id === transfer.id) ?? transfer);
      } catch {
        // Removed or cancelled while it downloaded: nothing went wrong from the user's point of view.
        if (current(next.transferId, next.fileId)) latest.current.handlers.onFailed(file.name);
      }
      void drain();
    };
    if (!busy.current && queue.current.length) void drain();
  }, [transfers, meId, enabled]);

  useEffect(() => () => window.clearTimeout(timer.current), []);
  return fetching;
}
