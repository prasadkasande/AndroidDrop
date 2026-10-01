// AndroidDrop's service worker. It exists so the installed app can receive things shared from other apps: Android
// posts them to /share-target, and this parks them in IndexedDB and opens the app, which sends them on
// (see src/lib/shareInbox.ts). Every other request goes to the network as usual.

self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()));

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);
  if (event.request.method === 'POST' && url.pathname === '/share-target') {
    event.respondWith(receiveShare(event.request));
  }
});

async function receiveShare(request) {
  try {
    const form = await request.formData();
    const files = form.getAll('files').filter((file) => file instanceof File && file.name);
    const text = String(form.get('text') ?? '').trim();
    const link = String(form.get('url') ?? '').trim();
    // Apps put a shared link in "text", "url" or both.
    const note = [text, link && !text.includes(link) ? link : ''].filter(Boolean).join('\n') || String(form.get('title') ?? '').trim();
    if (!files.length && !note) return Response.redirect('/?share=failed', 303);
    const id = Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
    await park({ id, files, text: files.length ? '' : note });
    return Response.redirect(`/?share=${id}`, 303);
  } catch {
    return Response.redirect('/?share=failed', 303);
  }
}

function park(share) {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open('androiddrop', 1);
    request.onupgradeneeded = () => request.result.createObjectStore('shares', { keyPath: 'id' });
    request.onerror = () => reject(request.error);
    request.onsuccess = () => {
      const db = request.result;
      const transaction = db.transaction('shares', 'readwrite');
      transaction.objectStore('shares').put(share);
      transaction.oncomplete = () => {
        db.close();
        resolve();
      };
      transaction.onerror = () => reject(transaction.error);
    };
  });
}
