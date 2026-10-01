import type { FileState, Transfer, TransferFile } from './api';
import { kindOf } from './fileKinds';
import { singleLink } from './format';

export const isOpen = (transfer: Transfer) => transfer.status === 'PENDING' || transfer.status === 'ACCEPTED';
export const isStored = (state: FileState) => state === 'READY' || state === 'SAVED' || state === 'DELIVERED';
const isDelivered = (state: FileState) => state === 'SAVED' || state === 'DELIVERED';

export const totalSize = (transfer: Transfer) => transfer.files.reduce((sum, file) => sum + file.size, 0);

/** Accepted and every file reached the recipient. */
export const isComplete = (transfer: Transfer) =>
  transfer.status === 'ACCEPTED' && transfer.files.every((file) => isDelivered(file.state));

/** Bytes that have reached the server so far, using live progress for files still uploading. */
export function uploadedBytes(transfer: Transfer, progress: ReadonlyMap<string, number>): number {
  return transfer.files.reduce((sum, file) => {
    if (file.state === 'UPLOADING') return sum + Math.min(file.size, progress.get(file.id) ?? 0);
    if (file.state === 'FAILED' || file.state === 'REMOVED') return sum;
    return sum + file.size;
  }, 0);
}

/** Bytes a phone recipient has downloaded so far, using live progress for files it's still downloading. */
export function downloadedBytes(transfer: Transfer, progress: ReadonlyMap<string, number>): number {
  return transfer.files.reduce((sum, file) => {
    if (file.state === 'DELIVERED') return sum + file.size;
    if (file.state === 'READY') return sum + Math.min(file.size, progress.get(file.id) ?? 0);
    return sum;
  }, 0);
}

const SINGLE_KIND: Partial<Record<ReturnType<typeof kindOf>, string>> = {
  image: 'a photo',
  video: 'a video',
  audio: 'an audio file',
  pdf: 'a PDF',
  archive: 'an archive',
  document: 'a document',
  spreadsheet: 'a spreadsheet',
  presentation: 'a presentation',
  apk: 'an app',
};

/**
 * Like {@link summarize}, but a single file is described by its kind ("a PDF") rather than its name, for places
 * where the name is shown right below anyway.
 */
export function describe(transfer: Transfer): string {
  if (transfer.text === null && transfer.files.length === 1) {
    const file = transfer.files[0];
    return SINGLE_KIND[kindOf(file.name, file.mimeType)] ?? 'a file';
  }
  return summarize(transfer);
}

/** "3 photos", "a video", "Boarding pass.pdf", "a link", "a note". */
export function summarize(transfer: Transfer): string {
  if (transfer.text !== null) return singleLink(transfer.text) ? 'a link' : 'a note';
  const files = transfer.files;
  if (files.length === 1) return files[0].name;
  const kinds = new Set(files.map((file) => kindOf(file.name, file.mimeType)));
  if (kinds.size === 1) {
    const kind = [...kinds][0];
    if (kind === 'image') return `${files.length} photos`;
    if (kind === 'video') return `${files.length} videos`;
    if (kind === 'audio') return `${files.length} audio files`;
    if (kind === 'pdf') return `${files.length} PDFs`;
  }
  if ([...kinds].every((kind) => kind === 'image' || kind === 'video')) return `${files.length} photos & videos`;
  return `${files.length} files`;
}

/** Photos and videos that can be shown as a thumbnail grid, and everything else as rows. */
export function splitMedia(files: TransferFile[]): { media: TransferFile[]; other: TransferFile[] } {
  const media = files.filter((file) => file.thumbnail || /^image\/(webp|gif|avif)$/.test(file.mimeType));
  const ids = new Set(media.map((file) => file.id));
  return { media, other: files.filter((file) => !ids.has(file.id)) };
}

export type Phase =
  | 'waiting' // pending: the recipient hasn't answered
  | 'transferring' // accepted, files still uploading
  | 'saving' // accepted, all uploaded, a phone recipient is downloading
  | 'done'
  | 'failed'
  | 'declined'
  | 'cancelled'
  | 'expired';

export function phaseOf(transfer: Transfer): Phase {
  switch (transfer.status) {
    case 'PENDING':
      return 'waiting';
    case 'DECLINED':
      return 'declined';
    case 'CANCELLED':
      return 'cancelled';
    case 'EXPIRED':
      return 'expired';
  }
  if (isComplete(transfer)) return 'done';
  if (transfer.files.some((file) => file.state === 'UPLOADING')) return 'transferring';
  if (transfer.files.some((file) => file.state === 'FAILED')) return 'failed';
  if (transfer.files.some((file) => file.state === 'READY')) return 'saving';
  return 'done';
}

const NONE: ReadonlySet<string> = new Set();

/**
 * A phone recipient's files it can save: received and still on the host, or with a copy in the app (`kept`), which
 * outlasts the host's own.
 */
export function savableFiles(transfer: Transfer, kept: ReadonlySet<string> = NONE): TransferFile[] {
  return transfer.files.filter((file) => kept.has(file.id) || (file.available && (file.state === 'READY' || file.state === 'DELIVERED')));
}

/** The folder a host recipient saved into: the one picked when accepting, or AndroidDrop. */
export function savedFolderName(transfer: Transfer): string {
  return transfer.folder?.split('/').filter(Boolean).pop() ?? 'AndroidDrop';
}

export function sortNewestFirst(transfers: Transfer[]): Transfer[] {
  return [...transfers].sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt));
}

export { singleLink };
