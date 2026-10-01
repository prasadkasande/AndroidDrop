import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react';
import { api, ApiError, type Device, type PairingInfo, type SaveFolder, type Session, type Transfer, type TransferFile } from '../lib/api';
import { useSystemNotifications } from '../lib/attention';
import { copyText, fileManagerName, tildePath } from '../lib/format';
import { ProgressStore } from '../lib/progress';
import { isComplete, isOpen, savableFiles, singleLink, sortNewestFirst, summarize } from '../lib/transfers';
import { UploadQueue } from '../lib/uploads';
import { forgetCopies, forgetIfServerRestarted, keepsInApp, saveToPhone, unsavedOf } from '../lib/phoneSave';
import { useAutoSave } from '../lib/useAutoSave';
import { takeParam } from '../lib/secure';
import { takeShare } from '../lib/shareInbox';
import { toast } from '../lib/toast';
import { useEventStream } from '../lib/useEventStream';
import { useInstallPrompt } from '../lib/useInstallPrompt';
import { useMediaQuery } from '../lib/useMediaQuery';
import { useWakeLock } from '../lib/useWakeLock';
import { useWindowFileDrop } from '../lib/useWindowFileDrop';
import { showFaviconBadge } from './Brand';
import { ConnectionBanner } from './ConnectionBanner';
import { DevicesDialog } from './DevicesDialog';
import type { FolderChoices } from './FolderPicker';
import { IncomingRequest } from './IncomingRequest';
import { MobileHeader, MobileTabBar } from './MobileChrome';
import { PairDialog } from './PairDialog';
import { PhoneNotice } from './PhoneNotice';
import { PreviewDialog, type PreviewTarget } from './PreviewDialog';
import { RecipientSheet } from './RecipientSheet';
import { RenameDialog } from './RenameDialog';
import { SecureSetupDialog } from './SecureSetupDialog';
import { SettingsDialog } from './SettingsDialog';
import { SendView } from './SendView';
import { Sidebar, type View } from './Sidebar';
import type { TransferActions } from './TransferRow';
import { peerOf, TransfersView } from './TransfersView';
import { useConfirm } from './ui/confirm';

interface Props {
  session: Session;
  /** A phone on plain HTTP that could move to this HTTPS address once it trusts the certificate. */
  secureSetup: string | null;
  onSessionChange: (session: Session) => void;
  onUnpaired: (notice: string) => void;
}

type DialogName = 'settings' | 'pair' | 'devices' | 'rename' | 'secure';

export function Home({ session, secureSetup, onSessionChange, onUnpaired }: Props) {
  const me = session.device;
  const isHost = session.host;
  const desktop = useMediaQuery('(min-width: 1024px)');
  const confirm = useConfirm();

  const [transfers, setTransfers] = useState<Transfer[]>([]);
  const [devices, setDevices] = useState<Device[]>([me]);
  const [loaded, setLoaded] = useState(false);
  const [pairing, setPairing] = useState<PairingInfo | null>(null);
  const [dialog, setDialog] = useState<DialogName | null>(null);
  const [justPaired, setJustPaired] = useState<Device | null>(null);
  const [preview, setPreview] = useState<PreviewTarget | null>(null);
  const [view, setView] = useState<View>('send');
  const [deferred, setDeferred] = useState<ReadonlySet<string>>(() => new Set());
  const [chooser, setChooser] = useState<{ summary: string; resolve: (id: string | null) => void } | null>(null);
  // Not subscribed here: rows that show progress subscribe themselves, so uploads don't re-render the whole app.
  const [progressStore] = useState(() => new ProgressStore());
  const transfersRef = useRef(transfers);
  const knownDeviceIds = useRef<Set<string> | null>(null);
  const pickFor = useRef<string | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const notifications = useSystemNotifications();
  const install = useInstallPrompt();
  // Something shared into the installed app from another app, parked by the service worker (see shareInbox.ts).
  const [sharedId, setSharedId] = useState(() => takeParam('share'));

  useEffect(() => {
    transfersRef.current = transfers;
  });

  const upsert = useCallback((transfer: Transfer) => setTransfers((list) => withTransfer(list, transfer)), []);

  const [uploads] = useState(() => new UploadQueue(upsert, (message) => toast.error('Upload failed', { description: message })));
  const uploading = useSyncExternalStore(uploads.subscribe, uploads.isBusy);
  const uploadVersion = useSyncExternalStore(uploads.subscribe, uploads.version);

  const deviceMap = useMemo(() => new Map(devices.map((device) => [device.id, device])), [devices]);
  const peers = useMemo(() => devices.filter((device) => device.id !== me.id), [devices, me.id]);
  const needsOnboarding = isHost && loaded && peers.length === 0;

  const received = useMemo(
    () => sortNewestFirst(transfers.filter((t) => t.recipientId === me.id && (t.status === 'PENDING' || t.status === 'ACCEPTED'))),
    [transfers, me.id],
  );
  const sent = useMemo(() => sortNewestFirst(transfers.filter((t) => t.senderId === me.id)), [transfers, me.id]);
  const waitingForMe = useMemo(
    () => received.filter((t) => t.status === 'PENDING').sort((a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt)),
    [received],
  );
  const request = waitingForMe.find((t) => !deferred.has(t.id)) ?? null;

  const fail = useCallback(
    (error: unknown) => {
      if (error instanceof ApiError && error.status === 401) {
        onUnpaired(`This device was removed from ${session.serverName}. Pair it again to keep sharing.`);
      } else {
        toast.error(error instanceof Error ? error.message : String(error));
      }
    },
    [onUnpaired, session.serverName],
  );

  const applyDevices = useCallback(
    (next: Device[]) => {
      const known = knownDeviceIds.current;
      if (known && isHost) {
        for (const device of next.filter((candidate) => !known.has(candidate.id))) {
          toast.success(`${device.name} is paired`, { description: 'It can send and receive files now.' });
          setJustPaired(device);
        }
      }
      knownDeviceIds.current = new Set(next.map((device) => device.id));
      setDevices(next);
    },
    [isHost],
  );

  // Where the host can save accepted files. Refreshed on reconnect, since folders may have been deleted meanwhile.
  const [folderList, setFolderList] = useState<{ folders: SaveFolder[]; canChoose: boolean } | null>(null);
  const loadFolders = useCallback(() => {
    if (isHost) api.folders().then(setFolderList, () => undefined);
  }, [isHost]);
  const chooseFolder = useCallback(async () => {
    try {
      return (await api.chooseFolder()) ?? null;
    } catch (error) {
      fail(error);
      return null;
    }
  }, [fail]);
  const folders = useMemo<FolderChoices | null>(() => (folderList ? { ...folderList, choose: chooseFolder } : null), [folderList, chooseFolder]);

  const sync = useCallback(async () => {
    loadFolders();
    try {
      const [nextTransfers, nextDevices, nextSession] = await Promise.all([api.transfers(), api.devices(), api.session()]);
      setTransfers(nextTransfers);
      applyDevices(nextDevices);
      onSessionChange(nextSession);
      setLoaded(true);
    } catch (error) {
      fail(error);
    }
  }, [applyDevices, fail, loadFolders, onSessionChange]);

  const peerName = (transfer: Transfer) =>
    transfer.senderId === me.id ? (deviceMap.get(transfer.recipientId)?.name ?? transfer.recipientName) : (deviceMap.get(transfer.senderId)?.name ?? transfer.senderName);

  /** Toasts and notifications for what changed in a transfer. */
  const announce = (previous: Transfer | undefined, next: Transfer) => {
    const outgoing = next.senderId === me.id;
    const who = peerName(next);
    if (!outgoing && !previous && next.status === 'PENDING') {
      notifications.show(`${who} wants to share ${summarize(next)}`, 'Open AndroidDrop to accept or decline.');
    }
    if (!outgoing && !previous && next.status === 'ACCEPTED') {
      notifications.show(`${who} sent ${summarize(next)}`, 'Accepted automatically.');
      toast(`${who} sent ${summarize(next)}`, { description: 'Accepted automatically' });
    }
    if (outgoing && previous?.status === 'PENDING' && next.status === 'DECLINED') {
      toast.error(`${who} declined ${summarize(next)}`);
    }
    if (outgoing && previous?.status === 'PENDING' && next.status === 'EXPIRED') {
      toast(`${who} didn’t answer in time`, { description: `${summarize(next)} was not sent.` });
    }
    if (!outgoing && isHost && previous && !isComplete(previous) && isComplete(next) && next.files.length) {
      announceSaved(next);
    }
  };

  /** One toast per transfer (keyed by id), whether the accept response or the live event gets here first. */
  const announceSaved = useCallback(
    (transfer: Transfer) => {
      const first = transfer.files.find((file) => file.state === 'SAVED');
      toast.success(`Saved ${summarize(transfer)}`, {
        id: `saved-${transfer.id}`,
        description: `In ${tildePath(transfer.folder ?? session.storageDir ?? '')}`,
        action: first ? { label: 'Show', onClick: () => void api.reveal(transfer, first).catch(fail) } : undefined,
      });
    },
    [fail, session.storageDir],
  );

  const status = useEventStream({
    ready: () => void sync(),
    transfer: (transfer) => {
      const previous = transfersRef.current.find((existing) => existing.id === transfer.id);
      // Update the ref now, not after the next render: events for one transfer often arrive back to back, and each
      // must see the one before it, or both look new and are announced twice.
      transfersRef.current = withTransfer(transfersRef.current, transfer);
      upsert(transfer);
      announce(previous, transfer);
      // Accepted into a picked folder (maybe in another tab): it's one of the recent folders now.
      if (transfer.folder && transfer.folder !== previous?.folder) loadFolders();
      if (!isOpen(transfer)) uploads.drop(transfer.id);
      const fileIds = transfer.files.map((file) => file.id);
      if (!transfer.files.some((file) => file.state === 'UPLOADING')) {
        progressStore.finish('upload', transfer.id, fileIds);
        uploads.release(transfer.id);
      }
      if (!transfer.files.some((file) => file.state === 'READY')) progressStore.finish('download', transfer.id, fileIds);
    },
    'transfer-removed': ({ id }) => {
      uploads.drop(id);
      // Removed from history: a phone's copies of its files go too, and toasts about it.
      const removed = transfersRef.current.find((transfer) => transfer.id === id);
      if (removed && !isHost) void forgetCopies(removed.files);
      toast.dismiss(`save-${id}`);
      toast.dismiss(`saved-${id}`);
      setTransfers((list) => list.filter((transfer) => transfer.id !== id));
      setPreview((current) => (current?.transfer.id === id ? null : current));
    },
    'transfer-progress': ({ id, fileId, received: bytes }) => {
      const transfer = transfersRef.current.find((existing) => existing.id === id);
      if (transfer) progressStore.update('upload', transfer, fileId, bytes);
    },
    'transfer-delivery': ({ id, fileId, sent: bytes }) => {
      const transfer = transfersRef.current.find((existing) => existing.id === id);
      if (transfer) progressStore.update('download', transfer, fileId, bytes);
    },
    devices: applyDevices,
    pairing: setPairing,
    // Only a 401 matters here; while the server is down the banner already says so.
    closed: () =>
      void api.session().catch((error) => {
        if (error instanceof ApiError && error.status === 401) fail(error);
      }),
  });

  // Phone: puts received files in Downloads (see phoneSave.ts).
  const saveOnPhone = useCallback((transfer: Transfer, files: TransferFile[]) => {
    const id = `save-${transfer.id}`;
    const what = files.length === 1 ? files[0].name : `${files.length} files`;
    // Takes the place of the "Received" toast.
    toast.loading(`Saving ${what}`, { id, description: 'to Downloads' });
    void saveToPhone(transfer, files).then(
      ({ downloading }) =>
        downloading
          ? // Big files download from the computer in Chrome's own downloader, which shows their progress.
            toast.success(`Downloading ${what}`, { id, description: 'Chrome shows the progress and saves it to Downloads.' })
          : toast.success(`Saved ${what} to Downloads`, { id, description: 'Find them in the Files app, under Downloads.' }),
      () => toast.error(`Couldn’t save ${what}`, { id, description: 'Tap Save to try again.' }),
    );
  }, []);
  const saveAll = useCallback(
    (transfer: Transfer) => {
      const latest = transfersRef.current.find((existing) => existing.id === transfer.id) ?? transfer;
      const files = savableFiles(latest);
      const unsaved = unsavedOf(files);
      saveOnPhone(latest, unsaved.length ? unsaved : files);
    },
    [saveOnPhone],
  );

  // A restarted server has forgotten every transfer; so does this phone, and it frees the space its copies used.
  useEffect(() => {
    if (!isHost) void forgetIfServerRestarted(session.serverRun);
  }, [isHost, session.serverRun]);

  // Phones fetch accepted files as soon as they're ready.
  const fetching = useAutoSave(transfers, me.id, !isHost, {
    onStarted: (transfer, count) => {
      toast.success(count === 1 && transfer.files.length === 1 ? `Saving ${transfer.files[0].name}` : `Saving ${count} files`, {
        id: `save-${transfer.id}`,
        description: 'to Downloads',
      });
    },
    onReceived: (transfer) =>
      toast.success(`Received ${summarize(transfer)}`, {
        id: `save-${transfer.id}`,
        description: transfer.files.length === 1 ? 'Tap Save to put it in Downloads.' : 'Tap Save to put them in Downloads.',
        duration: 20_000,
        // Kept open when tapped: the "Saving" toast for this transfer replaces it.
        action: { label: 'Save', onClick: (event) => (event.preventDefault(), saveAll(transfer)) },
      }),
    onFailed: (name) => toast.error(`Couldn’t receive ${name}`, { description: 'Tap its download button in Received to try again.' }),
  });

  // Tab badge: how many requests are waiting for an answer, in the title and as a dot on the icon.
  useEffect(() => {
    document.title = waitingForMe.length ? `(${waitingForMe.length}) AndroidDrop` : 'AndroidDrop';
    showFaviconBadge(waitingForMe.length > 0);
  }, [waitingForMe.length]);

  // The service worker receives "Share → AndroidDrop" from other apps; only phones need it, and only over HTTPS.
  useEffect(() => {
    if (!isHost && window.isSecureContext && 'serviceWorker' in navigator) void navigator.serviceWorker.register('/sw.js').catch(() => undefined);
  }, [isHost]);

  // Send what was shared in, once the device list is known (it decides whether to ask who to send to).
  useEffect(() => {
    if (!loaded || !sharedId) return;
    setSharedId(null);
    if (sharedId === 'failed') {
      toast.error('Couldn’t receive what you shared', { description: 'Share it again from the other app.' });
      return;
    }
    takeShare(sharedId).then(
      (share) => {
        if (share?.files.length) void sendFiles(share.files);
        else if (share?.text) void sendText(share.text);
      },
      () => toast.error('Couldn’t receive what you shared', { description: 'Share it again from the other app.' }),
    );
  }, [loaded, sharedId]);

  // A big transfer takes a while: keep the phone's screen on so it doesn't lock and suspend the page meanwhile.
  useWakeLock(!isHost && (uploading || fetching));

  // Leaving the page would silently kill an upload.
  useEffect(() => {
    if (!uploading) return;
    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [uploading]);

  // Fetch a pairing code whenever one is on screen; the event stream keeps it fresh after that.
  const showingPairing = isHost && (dialog === 'pair' || needsOnboarding);
  useEffect(() => {
    if (showingPairing) api.pairing().then(setPairing, fail);
  }, [showingPairing, fail]);

  // A phone paired while the dialog was open: show the success state briefly, then close.
  useEffect(() => {
    if (!justPaired) return;
    const timer = window.setTimeout(() => (dialog === 'pair' ? setDialog(null) : setJustPaired(null)), dialog === 'pair' ? 1800 : 300);
    return () => window.clearTimeout(timer);
  }, [justPaired, dialog]);

  // ---------- Sending ----------

  /** Like a share sheet: ask who to send to, unless there's only one choice. */
  const chooseRecipient = (summary: string): Promise<string | null> => {
    if (peers.length === 0) {
      toast.error(isHost ? 'Pair a phone first' : 'No other devices yet');
      return Promise.resolve(null);
    }
    if (peers.length === 1) return Promise.resolve(peers[0].id);
    return new Promise((resolve) =>
      setChooser((current) => {
        // A second send while the picker is open replaces the first; don't leave that one waiting forever.
        current?.resolve(null);
        return { summary, resolve };
      }),
    );
  };

  const sendFiles = async (files: File[], skippedFolders = 0, recipientId?: string) => {
    if (skippedFolders) {
      toast.error(skippedFolders === 1 ? 'Folders can’t be sent' : `${skippedFolders} folders were skipped`, {
        description: 'Zip the folder first, or select the files inside it.',
      });
    }
    if (!files.length) return;
    const target = recipientId ?? (await chooseRecipient(files.length === 1 ? files[0].name : `${files.length} files`));
    if (!target) return;
    try {
      const transfer = await api.sendFiles(target, files);
      upsert(transfer);
      uploads.enqueue(transfer, files);
    } catch (error) {
      fail(error);
    }
  };

  const sendText = async (text: string) => {
    const target = await chooseRecipient(singleLink(text) ? 'a link' : 'a note');
    if (!target) return false;
    try {
      upsert(await api.sendText(target, text));
      return true;
    } catch (error) {
      fail(error);
      return false;
    }
  };

  /** Opens the file picker; with a device the files go straight to it, otherwise we ask who to send to. */
  const pickFilesFor = (deviceId: string | null) => {
    pickFor.current = deviceId;
    fileInput.current?.click();
  };

  const dragging = useWindowFileDrop((files, folders) => void sendFiles(files, folders));

  // ---------- Answering ----------

  const accept = useCallback(
    async (transfer: Transfer, folder?: string) => {
      // A shared link opens and a note is copied, like AirDrop. Do it now, while this still counts as the user's click.
      if (transfer.text !== null) {
        const link = singleLink(transfer.text);
        if (link) window.open(link, '_blank', 'noopener');
        else await copyText(transfer.text).catch(() => undefined);
      }
      try {
        const accepted = await api.accept(transfer.id, folder);
        upsert(accepted);
        if (folder) loadFolders(); // it's a recent folder now
        if (transfer.text !== null) toast.success(singleLink(transfer.text) ? 'Link opened' : 'Copied to clipboard');
        else if (isHost && isComplete(accepted)) announceSaved(accepted);
        else if (isHost) toast(`Accepted ${summarize(accepted)}`, { id: `saved-${accepted.id}`, description: 'Saving as it arrives…' });
      } catch (error) {
        fail(error);
      }
    },
    [announceSaved, fail, isHost, loadFolders, upsert],
  );

  const decline = useCallback(
    async (transfer: Transfer) => {
      try {
        upsert(await api.decline(transfer.id));
      } catch (error) {
        fail(error);
      }
    },
    [fail, upsert],
  );

  const later = (transfer: Transfer) => {
    setDeferred((set) => new Set(set).add(transfer.id));
    // It stays waiting in Received, with Accept and Decline on the row.
    setView('received');
  };

  // ---------- History actions (stable, for memoised cards) ----------

  const cancel = useCallback(
    (transfer: Transfer) => {
      uploads.drop(transfer.id);
      api.cancel(transfer.id).then(upsert, fail);
    },
    [uploads, upsert, fail],
  );

  const remove = useCallback(
    async (transfer: Transfer) => {
      const savedHere = isHost && transfer.files.some((file) => file.state === 'SAVED');
      const ok = await confirm({
        title: 'Remove from history?',
        description: savedHere
          ? `Files already saved stay in ${tildePath(transfer.folder ?? session.storageDir ?? '')}.`
          : 'It disappears for both devices. Anything not yet delivered is deleted.',
        confirmLabel: 'Remove',
        destructive: true,
      });
      if (!ok) return;
      try {
        uploads.drop(transfer.id);
        await api.remove(transfer.id);
        setTransfers((list) => list.filter((existing) => existing.id !== transfer.id));
      } catch (error) {
        fail(error);
      }
    },
    [confirm, fail, isHost, session.storageDir, uploads],
  );

  const removeFromHistory = useCallback((transfer: Transfer) => void remove(transfer), [remove]);
  const reveal = useCallback((transfer: Transfer, file: TransferFile) => void api.reveal(transfer, file).catch(fail), [fail]);
  const save = useCallback((transfer: Transfer, file: TransferFile) => saveOnPhone(transfer, [file]), [saveOnPhone]);
  const openPreview = useCallback((transfer: Transfer, file: TransferFile) => setPreview({ transfer, file }), []);
  const copy = useCallback(
    async (text: string) => {
      try {
        await copyText(text);
        return true;
      } catch (error) {
        fail(error);
        return false;
      }
    },
    [fail],
  );
  const retry = useCallback((fileId: string) => uploads.retry(fileId), [uploads]);
  // New functions whenever the queue changes, so memoised rows re-read what's reconnecting or can be retried.
  const canRetry = useCallback((fileId: string) => uploads.canRetry(fileId), [uploads, uploadVersion]);
  const isUploading = useCallback((fileId: string) => uploads.isUploading(fileId), [uploads, uploadVersion]);

  const actions: TransferActions = {
    onAccept: accept,
    onDecline: decline,
    onCancel: cancel,
    onRemove: removeFromHistory,
    onReveal: reveal,
    onSave: save,
    onSaveAll: saveAll,
    onPreview: openPreview,
    onCopy: copy,
    onRetry: retry,
    canRetry,
    isUploading,
  };

  // ---------- Settings ----------

  const setAutoAccept = async (value: boolean) => {
    try {
      onSessionChange(await api.setAutoAccept(value));
      toast.success(value ? 'Transfers will be accepted automatically' : 'You’ll be asked before anything is saved');
    } catch (error) {
      fail(error);
    }
  };

  const rename = async (name: string) => {
    try {
      onSessionChange(await api.rename(name));
      toast.success('Device renamed');
      return true;
    } catch (error) {
      fail(error);
      return false;
    }
  };

  const revoke = async (device: Device) => {
    const ok = await confirm({
      title: `Remove ${device.name}?`,
      description: 'It will be disconnected and will need a new pairing code to connect again.',
      confirmLabel: 'Remove',
      destructive: true,
    });
    if (!ok) return;
    try {
      await api.revokeDevice(device.id);
      toast(`${device.name} removed`);
    } catch (error) {
      fail(error);
    }
  };

  const forget = async () => {
    const ok = await confirm({
      title: 'Unpair this device?',
      description: `You’ll need a new code from ${session.serverName} to connect again.`,
      confirmLabel: 'Unpair',
      destructive: true,
    });
    if (!ok) return;
    try {
      await api.forget();
      onUnpaired('This device is no longer paired.');
    } catch (error) {
      fail(error);
    }
  };

  const openFolder = () => void api.openFolder().catch(fail);
  const rotatePairing = () => void api.rotatePairing().then(setPairing, fail);

  // ---------- Layout ----------

  const sender = request ? peerOf(request, 'in', deviceMap) : null;
  const destination = isHost
    ? `Files will be saved to ${tildePath(session.storageDir ?? '~/AndroidDrop')}.`
    : keepsInApp
      ? 'After accepting, tap Save to put them in Downloads.'
      : 'Files will be saved to Downloads.';
  const compact = !desktop;

  const onSecureSetup = secureSetup ? () => setDialog('secure') : undefined;
  const onInstall = install ? () => void install() : undefined;
  const openSettings = () => setDialog('settings');

  const setDefaultFolder = async (folder: SaveFolder) => {
    try {
      setFolderList(await api.setDefaultFolder(folder.path));
    } catch (error) {
      fail(error);
    }
  };

  const content =
    view === 'send' ? (
      <SendView
        session={session}
        devices={devices}
        transfers={transfers}
        progressStore={progressStore}
        peerOf={(transfer) => peerOf(transfer, transfer.senderId === me.id ? 'out' : 'in', deviceMap)}
        pairing={pairing}
        onboarding={needsOnboarding}
        compact={compact}
        onChooseFiles={() => pickFilesFor(null)}
        onPickFor={pickFilesFor}
        onDropFor={(deviceId, files, folders) => void sendFiles(files, folders, deviceId)}
        onSendText={sendText}
        onCancel={cancel}
        isUploading={isUploading}
        onAccept={accept}
        onDecline={decline}
        onCopy={copy}
        onPair={() => setDialog('pair')}
        onRotatePairing={rotatePairing}
      />
    ) : (
      <TransfersView
        key={view}
        session={session}
        direction={view === 'received' ? 'in' : 'out'}
        transfers={view === 'received' ? received : sent}
        devices={deviceMap}
        progressStore={progressStore}
        folders={folders}
        loaded={loaded}
        compact={compact}
        onOpenFolder={isHost ? openFolder : undefined}
        {...actions}
      />
    );

  return (
    <>
      {desktop ? (
        // App shell: the sidebar stays put and only the main column scrolls, so long lists never push the page.
        <div className="flex h-dvh overflow-hidden bg-canvas">
          <Sidebar
            session={session}
            status={status}
            devices={devices}
            view={view}
            onView={setView}
            waiting={waitingForMe.length}
            receivedCount={received.length}
            sentCount={sent.length}
            onSettings={openSettings}
          />
          <main className="flex min-w-0 flex-1 flex-col">
            <ConnectionBanner status={status} serverName={session.serverName} isHost={isHost} />
            <div className="min-h-0 flex-1 overflow-y-auto">{content}</div>
          </main>
        </div>
      ) : (
        <div className="min-h-dvh bg-canvas pb-[calc(72px+env(safe-area-inset-bottom))]">
          <MobileHeader onSettings={openSettings} />
          <ConnectionBanner status={status} serverName={session.serverName} isHost={isHost} />
          {view === 'send' && <PhoneNotice onSecureSetup={onSecureSetup ?? null} onInstall={onInstall ?? null} />}
          <main>{content}</main>
          <MobileTabBar view={view} onView={setView} waiting={waitingForMe.length} />
        </div>
      )}

      {dragging && (
        <div className="pointer-events-none fixed inset-0 z-[35] animate-fade-in" aria-hidden="true">
          <div className="absolute inset-1.5 rounded-xl border-2 border-dashed border-accent/60 bg-accent/[0.03]" />
          <div className="absolute bottom-6 left-1/2 -translate-x-1/2 rounded-lg bg-fg px-3.5 py-2 text-[13px] font-medium whitespace-nowrap text-bg shadow-popover">
            Drop on a device, or anywhere to choose one
          </div>
        </div>
      )}

      <input
        ref={fileInput}
        type="file"
        multiple
        hidden
        onChange={(event) => {
          const files = Array.from(event.currentTarget.files ?? []);
          event.currentTarget.value = '';
          if (files.length) void sendFiles(files, 0, pickFor.current ?? undefined);
        }}
      />

      <IncomingRequest
        transfer={request}
        sender={sender}
        waiting={waitingForMe.filter((t) => !deferred.has(t.id)).length}
        progressStore={progressStore}
        destination={destination}
        folders={folders}
        onAccept={accept}
        onDecline={decline}
        onLater={later}
      />
      <RecipientSheet
        open={chooser !== null}
        summary={chooser?.summary ?? ''}
        devices={peers}
        onChoose={(id) => {
          chooser?.resolve(id);
          setChooser(null);
        }}
      />
      <SettingsDialog
        open={dialog === 'settings'}
        onOpenChange={(open) => setDialog(open ? 'settings' : null)}
        session={session}
        onAutoAccept={(value) => void setAutoAccept(value)}
        notifications={{ ...notifications, toggle: () => void notifications.toggle() }}
        folders={folders}
        onDefaultFolder={(folder) => void setDefaultFolder(folder)}
        pairedCount={peers.length}
        onPair={() => setDialog('pair')}
        onDevices={() => setDialog('devices')}
        onRename={() => setDialog('rename')}
        onForget={() => void forget()}
        onSecureSetup={onSecureSetup}
        onInstall={onInstall}
      />
      <PairDialog
        open={dialog === 'pair'}
        onOpenChange={(open) => setDialog(open ? 'pair' : null)}
        info={pairing}
        hostName={session.serverName}
        onRotate={rotatePairing}
        paired={justPaired}
      />
      {isHost && (
        <DevicesDialog
          open={dialog === 'devices'}
          onOpenChange={(open) => setDialog(open ? 'devices' : null)}
          devices={devices}
          onPair={() => setDialog('pair')}
          onRevoke={(device) => void revoke(device)}
        />
      )}
      {secureSetup && (
        <SecureSetupDialog
          open={dialog === 'secure'}
          onOpenChange={(open) => setDialog(open ? 'secure' : null)}
          origin={secureSetup}
          serverName={session.serverName}
        />
      )}
      {!isHost && <RenameDialog open={dialog === 'rename'} onOpenChange={(open) => setDialog(open ? 'rename' : null)} currentName={me.name} onRename={rename} />}
      <PreviewDialog
        target={preview}
        onClose={() => setPreview(null)}
        isHost={isHost}
        fileManager={fileManagerName(me.platform)}
        describeSender={(transfer) => (transfer.senderId === me.id ? `to ${peerName(transfer)}` : `from ${peerName(transfer)}`)}
        onReveal={reveal}
        onSave={save}
      />
    </>
  );
}

/** `list` with `transfer` replacing its older copy, or added at the front if it's new. */
function withTransfer(list: Transfer[], transfer: Transfer): Transfer[] {
  const index = list.findIndex((existing) => existing.id === transfer.id);
  if (index === -1) return [transfer, ...list];
  const next = [...list];
  next[index] = transfer;
  return next;
}
