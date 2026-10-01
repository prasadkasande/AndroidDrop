export type Platform = 'MAC' | 'ANDROID' | 'IOS' | 'WINDOWS' | 'LINUX' | 'OTHER';

export interface Device {
  id: string;
  name: string;
  platform: Platform;
  host: boolean;
  online: boolean;
  pairedAt: string;
}

export interface Session {
  device: Device;
  host: boolean;
  /** Transfers sent to this device skip the Accept/Decline prompt. */
  autoAccept: boolean;
  serverName: string;
  /** Changes whenever the server restarts, which forgets every transfer. */
  serverRun: string;
  /** Only sent to the host. */
  storageDir: string | null;
  /** Free space in the storage folder; only sent to the host. */
  freeBytes: number | null;
}

type TransferStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'CANCELLED' | 'EXPIRED';

/**
 * UPLOADING → READY (staged on the host) → SAVED (moved into the host's folder) or DELIVERED (downloaded by a
 * phone). FAILED uploads can be retried; REMOVED files were discarded or cleaned up.
 */
export type FileState = 'UPLOADING' | 'READY' | 'SAVED' | 'DELIVERED' | 'FAILED' | 'REMOVED';

export interface TransferFile {
  id: string;
  name: string;
  size: number;
  mimeType: string;
  state: FileState;
  /** False once a stored file was moved or deleted outside the app (e.g. in Finder). */
  available: boolean;
  /** Whether /thumbnail can serve a small preview. */
  thumbnail: boolean;
  finishedAt: string | null;
}

export interface Transfer {
  id: string;
  senderId: string;
  senderName: string;
  senderPlatform: Platform;
  recipientId: string;
  recipientName: string;
  recipientPlatform: Platform;
  status: TransferStatus;
  /** Set for text/link transfers, which have no files. */
  text: string | null;
  files: TransferFile[];
  createdAt: string;
  respondedAt: string | null;
  /** The folder the host picked to save into when accepting; null means the storage folder. */
  folder: string | null;
}

/** A folder the host can save accepted files into. */
export interface SaveFolder {
  path: string;
  name: string;
  /** The storage folder (~/AndroidDrop). */
  isDefault: boolean;
}

export interface PairingInfo {
  code: string;
  issuedAt: string;
  expiresAt: string;
  /** Base URLs a phone on the LAN can use, best guess first. */
  urls: string[];
}

export class ApiError extends Error {
  /** HTTP status; 0 = network failure, -1 = cancelled. */
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

/** Required on every state-changing request; see DeviceAuthFilter. */
const CSRF_HEADER = { 'X-AndroidDrop': '1' };

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, {
      method,
      headers: body === undefined ? CSRF_HEADER : { ...CSRF_HEADER, 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, "Can't reach the server. Is it running, and are you on the same Wi-Fi?");
  }
  if (!response.ok) {
    throw new ApiError(response.status, problemDetail(await response.text(), response.status));
  }
  return response.status === 204 ? (undefined as T) : ((await response.json()) as T);
}

function problemDetail(body: string, status: number): string {
  try {
    const problem = JSON.parse(body) as { detail?: string; title?: string };
    return problem.detail || problem.title || `Request failed (${status})`;
  } catch {
    return `Request failed (${status})`;
  }
}

const transferPath = (id: string) => `/api/transfers/${encodeURIComponent(id)}`;
const filePath = (transfer: Transfer, file: TransferFile) => `${transferPath(transfer.id)}/files/${encodeURIComponent(file.id)}`;

export const api = {
  /** `httpsPort`: where the server also speaks HTTPS, if it does. */
  server: () => request<{ name: string; httpsPort: number | null }>('GET', '/api/server'),
  session: () => request<Session>('GET', '/api/session'),
  /** `deviceId`: sign this browser in as that already paired device, instead of pairing a new one. */
  pair: (code: string, name: string, deviceId?: string) => request<Session>('POST', '/api/pair', { code, name, deviceId }),
  /** Devices already paired, which a new browser could sign in as. Needs the current code; doesn't use it up. */
  pairOptions: (code: string) => request<{ id: string; name: string; platform: Platform }[]>('POST', '/api/pair/devices', { code }),
  rename: (name: string) => request<Session>('PATCH', '/api/session', { name }),
  setAutoAccept: (autoAccept: boolean) => request<Session>('PATCH', '/api/session/preferences', { autoAccept }),
  forget: () => request<void>('POST', '/api/session/forget'),
  /** A one-time code that carries this phone's pairing to the HTTPS address. */
  startHandoff: () => request<{ code: string }>('POST', '/api/session/handoff'),
  finishHandoff: (code: string) => request<Session>('POST', '/api/pair/handoff', { code }),
  devices: () => request<Device[]>('GET', '/api/devices'),
  revokeDevice: (id: string) => request<void>('DELETE', `/api/devices/${encodeURIComponent(id)}`),
  transfers: () => request<Transfer[]>('GET', '/api/transfers'),
  sendFiles: (recipientId: string, files: File[]) =>
    request<Transfer>('POST', '/api/transfers', {
      recipientId,
      files: files.map((file) => ({ name: file.name, size: file.size, mimeType: file.type || null })),
    }),
  sendText: (recipientId: string, text: string) => request<Transfer>('POST', '/api/transfers', { recipientId, text }),
  /** `folder` is where the host saves the files; leave it out for the storage folder (and always on a phone). */
  accept: (id: string, folder?: string) => request<Transfer>('POST', `${transferPath(id)}/accept`, folder ? { folder } : undefined),
  decline: (id: string) => request<Transfer>('POST', `${transferPath(id)}/decline`),
  cancel: (id: string) => request<Transfer>('POST', `${transferPath(id)}/cancel`),
  remove: (id: string) => request<void>('DELETE', transferPath(id)),
  /** How much of a broken-off upload the server kept: where to continue it from. */
  received: (transferId: string, fileId: string) =>
    request<{ received: number }>('GET', `${transferPath(transferId)}/files/${encodeURIComponent(fileId)}/received`).then((result) => result.received),
  reveal: (transfer: Transfer, file: TransferFile) => request<void>('POST', `${filePath(transfer, file)}/reveal`),
  openFolder: () => request<void>('POST', '/api/host/open-folder'),
  folders: () => request<{ folders: SaveFolder[]; canChoose: boolean }>('GET', '/api/host/folders'),
  /** Where accepted files go unless another folder is picked when accepting. */
  setDefaultFolder: (path: string) => request<{ folders: SaveFolder[]; canChoose: boolean }>('PUT', '/api/host/folders/default', { path }),
  /** Opens the folder picker on the host's screen; undefined if it was cancelled. */
  chooseFolder: () => request<SaveFolder | undefined>('POST', '/api/host/folders/choose'),
  pairing: () => request<PairingInfo>('GET', '/api/host/pairing'),
  rotatePairing: () => request<PairingInfo>('POST', '/api/host/pairing/rotate'),
};

export function fileUrl(transfer: Transfer, file: TransferFile, download = false): string {
  return `${filePath(transfer, file)}/content${download ? '?download=true' : ''}`;
}

/** Best preview image: a server thumbnail, the original for formats browsers decode, else null. */
export function previewImageUrl(transfer: Transfer, file: TransferFile): string | null {
  if (file.thumbnail) return `${filePath(transfer, file)}/thumbnail`;
  if (file.available && /^image\/(webp|gif|avif)$/.test(file.mimeType)) return fileUrl(transfer, file);
  return null;
}

interface UploadHandle {
  promise: Promise<Transfer>;
  abort: () => void;
}

/**
 * Uploads one announced file as the raw request body, so the server can stream it straight to disk. XHR rather
 * than fetch, because only XHR can be aborted mid-upload everywhere. Progress comes from the server's events.
 * `offset` continues an upload that broke off, sending only the rest of the file.
 */
export function uploadFile(transferId: string, fileId: string, file: File, offset = 0): UploadHandle {
  const xhr = new XMLHttpRequest();
  const promise = new Promise<Transfer>((resolve, reject) => {
    xhr.open('PUT', `${transferPath(transferId)}/files/${encodeURIComponent(fileId)}${offset ? `?offset=${offset}` : ''}`);
    xhr.setRequestHeader('X-AndroidDrop', '1');
    xhr.setRequestHeader('Content-Type', 'application/octet-stream');
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) resolve(JSON.parse(xhr.responseText) as Transfer);
      else reject(new ApiError(xhr.status, problemDetail(xhr.responseText, xhr.status)));
    };
    xhr.onerror = () => reject(new ApiError(0, 'Connection lost during upload.'));
    xhr.onabort = () => reject(new ApiError(-1, 'Cancelled'));
    xhr.send(offset ? file.slice(offset) : file);
  });
  return { promise, abort: () => xhr.abort() };
}
