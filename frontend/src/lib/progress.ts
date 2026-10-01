import { useSyncExternalStore } from 'react';
import type { Transfer } from './api';
import { downloadedBytes, uploadedBytes } from './transfers';

interface Sample {
  time: number;
  bytes: number;
}

/**
 * The two legs of a transfer: the sender's upload to the server, and (when a phone receives) the phone's download
 * from it. A file can be downloading while the next one still uploads.
 */
type Leg = 'upload' | 'download';

const bytesOf: Record<Leg, (transfer: Transfer, progress: ReadonlyMap<string, number>) => number> = {
  upload: uploadedBytes,
  download: downloadedBytes,
};

/**
 * Live progress from the server's `transfer-progress` (upload) and `transfer-delivery` (download) events, shared
 * by sender and recipient. It lives outside React state, and components read it through the hooks below, so
 * several updates a second only re-render what shows that particular transfer.
 */
export class ProgressStore {
  private files: Record<Leg, Map<string, number>> = { upload: new Map(), download: new Map() };
  private speeds = new Map<string, number>();
  private samples = new Map<string, Sample>();
  private readonly listeners = new Set<() => void>();

  subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  /** Bytes so far per file id, for one leg. */
  bytes(leg: Leg): ReadonlyMap<string, number> {
    return this.files[leg];
  }

  /** Smoothed bytes/second for one leg of a transfer, or 0 before there's enough data. */
  speedOf(transferId: string, leg: Leg): number {
    return this.speeds.get(`${leg}:${transferId}`) ?? 0;
  }

  /** Records how far one file of `transfer` has got. */
  update(leg: Leg, transfer: Transfer, fileId: string, bytes: number) {
    this.files = { ...this.files, [leg]: new Map(this.files[leg]).set(fileId, bytes) };
    const key = `${leg}:${transfer.id}`;
    const transferBytes = bytesOf[leg](transfer, this.files[leg]);
    const now = performance.now();
    const sample = this.samples.get(key);
    if (!sample || transferBytes < sample.bytes) {
      this.samples.set(key, { time: now, bytes: transferBytes });
    } else if (now - sample.time >= 400) {
      const instant = ((transferBytes - sample.bytes) / (now - sample.time)) * 1000;
      const previous = this.speeds.get(key);
      this.speeds.set(key, previous ? previous * 0.6 + instant * 0.4 : instant);
      this.samples.set(key, { time: now, bytes: transferBytes });
    }
    this.emit();
  }

  /** Forget one leg of a transfer once it has nothing left to move. */
  finish(leg: Leg, transferId: string, fileIds: string[]) {
    const key = `${leg}:${transferId}`;
    if (!this.samples.has(key) && fileIds.every((id) => !this.files[leg].has(id))) return;
    this.samples.delete(key);
    this.speeds.delete(key);
    const next = new Map(this.files[leg]);
    for (const id of fileIds) next.delete(id);
    this.files = { ...this.files, [leg]: next };
    this.emit();
  }

  private emit() {
    for (const listener of this.listeners) listener();
  }
}

/** Bytes of `transfer` on the server so far. Re-renders only when that number changes. */
export function useUploadedBytes(store: ProgressStore, transfer: Transfer | null): number {
  return useSyncExternalStore(store.subscribe, () => (transfer ? uploadedBytes(transfer, store.bytes('upload')) : 0));
}

/** Bytes of `transfer` a phone recipient has downloaded so far. */
export function useDownloadedBytes(store: ProgressStore, transfer: Transfer): number {
  return useSyncExternalStore(store.subscribe, () => downloadedBytes(transfer, store.bytes('download')));
}

/** Bytes of one file so far on one leg. */
export function useFileProgress(store: ProgressStore, fileId: string, leg: Leg): number {
  return useSyncExternalStore(store.subscribe, () => store.bytes(leg).get(fileId) ?? 0);
}
