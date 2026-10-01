import { useSyncExternalStore } from 'react';
import { fileUrl, type Transfer, type TransferFile } from './api';

/**
 * How a phone keeps what it receives. Over HTTPS, accepted files are fetched quietly into the app's private storage
 * while they arrive (the app shows the progress, the browser shows nothing). Tapping Save then puts every file in
 * the phone's Downloads folder, always the same way, so there's one place to look.
 *
 * Files bigger than {@link IN_APP_LIMIT} (a whole movie) skip the copy in the app: Save hands them straight to the
 * browser, which downloads them from the computer into Downloads, needs room for only one copy, and resumes if
 * Wi-Fi drops. Without HTTPS there's no private storage, so every file downloads that way as soon as it's accepted.
 */
export const keepsInApp =
  /Android|iPhone|iPad/i.test(navigator.userAgent) &&
  window.isSecureContext &&
  typeof navigator.storage?.getDirectory === 'function' &&
  typeof FileSystemFileHandle !== 'undefined' &&
  'createWritable' in FileSystemFileHandle.prototype;

/** Above this, a file downloads straight into Downloads when saved: copying it out of the app would need twice its size free. */
const IN_APP_LIMIT = 2 * 1024 ** 3;

/** Whether this phone keeps `file` in the app until it's saved, or downloads it straight from the computer. */
export const keptInApp = (file: TransferFile) => keepsInApp && file.size <= IN_APP_LIMIT;

/**
 * A transfer this phone accepted that has all it gets before Save: its small files are in the app, and its big ones
 * wait on the computer for Save to download them (none of them downloading yet). It then reads as received.
 */
export function readyToSave(transfer: Transfer, downloaded: ReadonlyMap<string, number>): boolean {
  return (
    keepsInApp &&
    transfer.status === 'ACCEPTED' &&
    transfer.files.some((file) => file.state === 'READY') &&
    transfer.files.every((file) => file.state !== 'UPLOADING' && (file.state !== 'READY' || (!keptInApp(file) && !downloaded.get(file.id))))
  );
}

const DIR = 'received';
const SAVED_KEY = 'androiddrop.savedFiles';
const RUN_KEY = 'androiddrop.serverRun';
/** Long enough for the browser to copy a saved file into Downloads before the copy here is deleted. */
const SAVED_COPY_MS = 5 * 60_000;
/** Browsers throttle bursts of downloads, so they go out a little apart. */
const DOWNLOAD_GAP_MS = 450;

/** Set while copies from a previous server run are being deleted; new files wait for it. */
let clearing: Promise<void> = Promise.resolve();

/** Fetches a file into the app. Resolves once the whole file is stored. */
export async function fetchIntoApp(transfer: Transfer, file: TransferFile): Promise<void> {
  await clearing;
  const response = await fetch(fileUrl(transfer, file, true), { credentials: 'same-origin' });
  if (!response.ok || !response.body) throw new Error(`Couldn't download ${file.name}`);
  const dir = await folder();
  const handle = await dir.getFileHandle(file.id, { create: true });
  try {
    await response.body.pipeTo(await handle.createWritable());
  } catch (error) {
    void dir.removeEntry(file.id).catch(() => {});
    throw error;
  }
  setKept(new Set(kept).add(file.id));
}

/** The browser's own download, straight from the host. Used when the app can't keep files itself. */
export function downloadFromHost(transfer: Transfer, file: TransferFile) {
  handOver(fileUrl(transfer, file, true), file.name);
}

/**
 * Saves `files` to the phone's Downloads folder, one after another. Files already in the app are handed over
 * straight away; any that aren't (cleared, or opened in another browser) are fetched from the host first. Big
 * ones download from the host by the browser instead: the result says how many, since those are still on their way.
 */
export async function saveToPhone(transfer: Transfer, files: TransferFile[]): Promise<{ downloading: number }> {
  let downloading = 0;
  for (const [index, file] of files.entries()) {
    if (index > 0) await new Promise((resolve) => window.setTimeout(resolve, DOWNLOAD_GAP_MS));
    if (!keptInApp(file)) {
      downloadFromHost(transfer, file);
      markSaved([file]);
      downloading++;
      continue;
    }
    let copy = await keptCopy(file);
    if (!copy) {
      await fetchIntoApp(transfer, file);
      copy = await keptCopy(file);
    }
    if (!copy) throw new Error(`Couldn't keep ${file.name}`);
    const url = URL.createObjectURL(copy);
    handOver(url, file.name);
    markSaved([file]);
    // In Downloads now, so the copy here only takes up space. The host still has it for "Save again".
    window.setTimeout(() => {
      URL.revokeObjectURL(url);
      void forgetCopies([file]);
    }, SAVED_COPY_MS);
  }
  return { downloading };
}

/**
 * When the server has restarted, it has forgotten every transfer, so this phone drops its copies and what it
 * remembered about them too.
 */
export async function forgetIfServerRestarted(run: string) {
  let previous: string | null = null;
  try {
    previous = localStorage.getItem(RUN_KEY);
    localStorage.setItem(RUN_KEY, run);
  } catch {
    // Without storage there's nothing to compare with.
  }
  if (previous === null || previous === run) return;
  saved = new Set();
  try {
    localStorage.removeItem(SAVED_KEY);
  } catch {
    // Nothing stored.
  }
  setKept(new Set());
  if (!keepsInApp) return;
  clearing = navigator.storage
    .getDirectory()
    .then((root) => root.removeEntry(DIR, { recursive: true }))
    .catch(() => {
      // Nothing kept.
    });
  await clearing;
}

/**
 * Takes stock of the copies in the app, once per visit. Ones already put in Downloads (the page was closed before
 * they were cleaned up) and ones whose transfer is gone are deleted. `received` is every file of the transfers this
 * phone accepted, with its size; the whole copies among them are what {@link useKeptFiles} then reports.
 */
export async function pruneCopies(received: ReadonlyMap<string, number>) {
  if (!keepsInApp) return;
  try {
    await clearing;
    const dir = await folder();
    const names: string[] = [];
    for await (const name of dir.keys()) names.push(name);
    const whole: string[] = [];
    await Promise.all(
      names.map(async (name) => {
        const size = received.get(name);
        if (size === undefined || saved.has(name)) {
          await dir.removeEntry(name).catch(() => {});
          return;
        }
        // A copy that's short is being fetched right now, or was cut off; fetching it again replaces it.
        const copy = await dir.getFileHandle(name).then((handle) => handle.getFile(), () => null);
        if (copy?.size === size) whole.push(name);
      }),
    );
    setKept(new Set([...kept, ...whole]));
  } catch {
    // Storage unavailable.
  }
}

/** Deletes this phone's copies of `files`, if it has any. */
export async function forgetCopies(files: TransferFile[]) {
  if (!keepsInApp) return;
  try {
    const dir = await folder();
    await Promise.all(files.map((file) => dir.removeEntry(file.id).catch(() => {})));
  } catch {
    // Storage unavailable.
  }
  if (files.some((file) => kept.has(file.id))) {
    const gone = new Set(files.map((file) => file.id));
    setKept(new Set([...kept].filter((id) => !gone.has(id))));
  }
}

async function keptCopy(file: TransferFile): Promise<File | null> {
  try {
    const copy = await (await (await folder()).getFileHandle(file.id)).getFile();
    return copy.size === file.size ? copy : null;
  } catch {
    return null;
  }
}

async function folder() {
  return (await navigator.storage.getDirectory()).getDirectoryHandle(DIR, { create: true });
}

function handOver(href: string, name: string) {
  const link = document.createElement('a');
  link.href = href;
  link.download = name;
  document.body.appendChild(link);
  link.click();
  link.remove();
}

// Which files the user has saved, so the list can say so, and which have a copy in the app. Only this browser
// needs to know.
const listeners = new Set<() => void>();
let saved: ReadonlySet<string> = readSaved();
let kept: ReadonlySet<string> = new Set();

function setKept(next: ReadonlySet<string>) {
  kept = next;
  listeners.forEach((listener) => listener());
}

const subscribe = (listener: () => void) => {
  listeners.add(listener);
  return () => listeners.delete(listener);
};

/** Files with a whole copy in the app. They can be saved even after the computer has deleted its own copy. */
export const keptFiles = () => kept;

export function useKeptFiles(): ReadonlySet<string> {
  return useSyncExternalStore(subscribe, keptFiles);
}

function readSaved(): ReadonlySet<string> {
  try {
    return new Set(JSON.parse(localStorage.getItem(SAVED_KEY) ?? '[]') as string[]);
  } catch {
    return new Set();
  }
}

function markSaved(files: TransferFile[]) {
  if (files.every((file) => saved.has(file.id))) return;
  saved = new Set([...saved, ...files.map((file) => file.id)]);
  try {
    localStorage.setItem(SAVED_KEY, JSON.stringify([...saved].slice(-2000)));
  } catch {
    // Private mode: it's only remembered until the page closes.
  }
  listeners.forEach((listener) => listener());
}

/** Which of `files` are saved. */
export function savedOf(saved: ReadonlySet<string>, files: TransferFile[]): TransferFile[] {
  return files.filter((file) => saved.has(file.id));
}

/** Which of `files` still need saving, as of now. */
export function unsavedOf(files: TransferFile[]): TransferFile[] {
  return files.filter((file) => !saved.has(file.id));
}

export function useSavedFiles(): ReadonlySet<string> {
  return useSyncExternalStore(subscribe, () => saved);
}
