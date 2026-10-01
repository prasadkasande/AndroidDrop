import { api, ApiError, uploadFile, type Transfer } from './api';

interface Job {
  transferId: string;
  fileId: string;
  file: File;
  /** Retried by hand: continue from what the server kept rather than starting over. */
  resume?: boolean;
}

/** How long to keep reconnecting without getting any further before giving up (the user can still retry). */
const RECONNECT_FOR_MS = 3 * 60_000;
/** Worth trying again: the connection dropped, or the server hasn't noticed the old one did. */
const TRANSIENT = new Set([0, 408, 416, 423, 502, 503, 504]);

const delay = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms));

/**
 * Uploads announced files one at a time. When Wi-Fi drops mid-file it waits, asks the server how much arrived,
 * and sends only the rest, so a 25 GB video doesn't start over at 90%. The files are kept until their transfer is
 * finished so a failed upload can also be retried by hand. Progress comes from the server (see ProgressStore).
 */
export class UploadQueue {
  private queue: Job[] = [];
  private files = new Map<string, Job>();
  private active: { job: Job; abort: () => void } | null = null;
  /** Bumped on every change, so views that show what's queued or reconnecting know to re-render. */
  private revision = 0;
  private readonly listeners = new Set<() => void>();
  private readonly onUploaded: (transfer: Transfer) => void;
  private readonly onError: (message: string) => void;

  constructor(onUploaded: (transfer: Transfer) => void, onError: (message: string) => void) {
    this.onUploaded = onUploaded;
    this.onError = onError;
  }

  subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  /** Changes whenever what's uploading or queued changes. */
  version = () => this.revision;

  /** Whether anything is uploading or queued (used to warn before leaving the page). */
  isBusy = () => this.active !== null || this.queue.length > 0;

  /** Queues every file of a newly announced transfer; `files` are in the same order as `transfer.files`. */
  enqueue(transfer: Transfer, files: File[]) {
    transfer.files.forEach((announced, index) => {
      const file = files[index];
      if (!file || announced.state !== 'UPLOADING') return;
      const job = { transferId: transfer.id, fileId: announced.id, file };
      this.files.set(announced.id, job);
      this.queue.push(job);
    });
    this.changed();
    void this.pump();
  }

  /** Uploading or waiting its turn; a file that shows as failed meanwhile is only reconnecting. */
  isUploading = (fileId: string) => this.active?.job.fileId === fileId || this.queue.some((job) => job.fileId === fileId);

  canRetry(fileId: string): boolean {
    return this.files.has(fileId) && !this.isUploading(fileId);
  }

  retry(fileId: string) {
    const job = this.files.get(fileId);
    if (!job || !this.canRetry(fileId)) return;
    this.queue.push({ ...job, resume: true });
    this.changed();
    void this.pump();
  }

  /** Stops uploading a transfer that was declined, cancelled or removed. */
  drop(transferId: string) {
    const known = [...this.files.values()].some((job) => job.transferId === transferId);
    if (!known) return;
    this.queue = this.queue.filter((job) => job.transferId !== transferId);
    for (const [fileId, job] of this.files) if (job.transferId === transferId) this.files.delete(fileId);
    if (this.active?.job.transferId === transferId) this.active.abort();
    this.changed();
  }

  /** Releases the File objects of a transfer that no longer needs them. */
  release(transferId: string) {
    if (this.active?.job.transferId === transferId || this.queue.some((job) => job.transferId === transferId)) return;
    let released = false;
    for (const [fileId, job] of this.files) {
      if (job.transferId === transferId) released = this.files.delete(fileId) || released;
    }
    if (released) this.changed();
  }

  private async pump() {
    if (this.active) return;
    const job = this.queue.shift();
    if (!job) return;
    this.active = { job, abort: () => {} };
    this.changed();
    try {
      this.onUploaded(await this.upload(job));
    } catch (error) {
      // Cancelled by us, or the transfer was declined/cancelled/removed (409/404): the transfer event explains it.
      const quiet = error instanceof ApiError && (error.status === -1 || error.status === 409 || error.status === 404);
      if (!quiet) this.onError(`${job.file.name}: ${error instanceof Error ? error.message : String(error)}`);
    } finally {
      this.active = null;
      this.changed();
      void this.pump();
    }
  }

  /** Sends one file, continuing from where the server got to whenever the connection drops. */
  private async upload(job: Job): Promise<Transfer> {
    let offset = job.resume ? await api.received(job.transferId, job.fileId).catch(() => 0) : 0;
    let lastProgress = Date.now();
    let pause = 1000;
    for (;;) {
      const handle = uploadFile(job.transferId, job.fileId, job.file, offset);
      this.active = { job, abort: handle.abort };
      try {
        return await handle.promise;
      } catch (error) {
        const transient = error instanceof ApiError && TRANSIENT.has(error.status);
        if (!transient || Date.now() - lastProgress > RECONNECT_FOR_MS) throw error;
      }
      await delay(pause);
      if (!this.files.has(job.fileId)) throw new ApiError(-1, 'Cancelled');
      const received = await api.received(job.transferId, job.fileId).catch(() => offset);
      if (received > offset) {
        lastProgress = Date.now();
        pause = 1000;
      } else {
        pause = Math.min(pause * 2, 10_000);
      }
      offset = received;
    }
  }

  private changed() {
    this.revision++;
    for (const listener of this.listeners) listener();
  }
}
