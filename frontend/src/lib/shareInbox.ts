/**
 * Things shared into the installed app from other Android apps. The service worker (public/sw.js) receives them
 * and parks them in IndexedDB under an id, then opens the app with ?share=<id>; the page picks them up from here.
 */
export interface Share {
  files: File[];
  /** A note or link; empty when files were shared. */
  text: string;
}

const DB = 'androiddrop';
const STORE = 'shares';

function open(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB, 1);
    request.onupgradeneeded = () => request.result.createObjectStore(STORE, { keyPath: 'id' });
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

/** The share parked under `id`. Everything parked is cleared, so it's only sent once and nothing is left behind. */
export async function takeShare(id: string): Promise<Share | null> {
  const db = await open();
  try {
    return await new Promise((resolve, reject) => {
      const transaction = db.transaction(STORE, 'readwrite');
      const store = transaction.objectStore(STORE);
      const read = store.get(id);
      read.onsuccess = () => {
        const share = read.result as (Share & { id: string }) | undefined;
        store.clear();
        transaction.oncomplete = () => resolve(share ? { files: share.files, text: share.text } : null);
      };
      transaction.onerror = () => reject(transaction.error);
    });
  } finally {
    db.close();
  }
}
