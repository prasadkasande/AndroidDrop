# AndroidDrop

AirDrop for an Android phone and a Mac, over your own Wi-Fi. Pick a device and send; the other side is asked to
accept, and accepted files save themselves. No cloud, no accounts, and nothing to install on the phone.

- **Spring Boot 4** server runs on the Mac and saves received files in `~/AndroidDrop`. Transfer history lives in
  memory only: once the server stops, the only thing it keeps is the list of paired devices.
- **React** UI is served by that same server, so the Mac opens `http://localhost:8080` and the phone opens
  `http://<mac-ip>:8080` in Chrome. It is built with Tailwind CSS and Radix UI, set in Plus Jakarta Sans (bundled, so it
  works without internet) with a warm light/dark palette. On the Mac it's a Finder-style sidebar app; on a phone
  it has a bottom tab bar.
- The phone pairs by scanning a QR code shown on the Mac. Updates arrive live on both sides via Server-Sent Events.

```
   Android (Chrome)                          Mac
 ┌──────────────────┐   Wi-Fi / LAN    ┌───────────────────────────────┐
 │  React UI        │ ───────────────▶ │  Spring Boot  :8080           │
 │  (paired device, │  HTTP + SSE      │   ├─ /            React UI     │
 │   cookie token)  │ ◀─────────────── │   ├─ /api/**      REST + SSE   │
 └──────────────────┘                  │   └─ ~/AndroidDrop  files      │
                                       │                                │
                        localhost ───▶ │  React UI in the Mac's browser │
                                       │  (trusted as the host)         │
                                       └───────────────────────────────┘
```

## Quick start

Requirements: JDK 21+ and Node 20+. The Maven wrapper is included, so Maven itself isn't needed.

```bash
make run
```

This builds the UI into the jar and starts the server with a small memory cap (see `JAVA_OPTS` in the Makefile).
The console prints something like:

```
AndroidDrop is running
  On this computer : http://localhost:8080
  On your phone    : http://192.168.1.20:8080
  Pairing code     : 482913
  Files folder     : /Users/you/AndroidDrop
```

1. Open **http://localhost:8080** on the Mac.
2. The first time, the *Nearby* page shows a QR code (later: **Add device** on that page, or **Settings → Pair**).
3. Scan it with the Android camera. The phone must be on the **same Wi-Fi**. Chrome opens with the code filled in, so
   just tap **Connect**.
4. Share in either direction:
   - **Send:** on the *Nearby* page, click a device and pick files, or drag files onto it. *Choose files*, a drop
     anywhere else, or ⌘V asks who to send to. Notes and links go
     through *Messages* beside the devices, a simple chat of every note and link sent or received. Messages are kept
     in memory only, so they're gone once the server stops.
   - **Receive:** the other device shows *"Pixel would like to share 3 photos"* with **Decline** / **Accept**.
     On the Mac, accepted files are saved into `~/AndroidDrop`, or into any folder you pick with **Save to** before
     accepting. On the phone they download into AndroidDrop, and one tap on **Save** puts them in Downloads.
     An accepted link opens; an accepted note is copied to the clipboard.

How the handshake works:

```
sender announces files ─▶ recipient is asked ─▶ Accept ─▶ Mac: moved into ~/AndroidDrop
        │  (uploads to hidden staging meanwhile)          Phone: downloaded into the app, Save → Downloads
        │                                  └─▶ Decline ─▶ staged copy deleted
        └─▶ sender can cancel any time before delivery; unanswered requests expire after an hour
```

Files upload while the recipient is deciding, so accepting is instant and the phone can lock its screen as soon as
its upload is done. Nothing reaches your folders until you accept. To skip the prompt between your own devices,
turn on **Accept automatically** in **Settings** (the gear at the top of the sidebar on the Mac, top right on a phone).
It's a per-device setting.

On the phone, accepted files download into AndroidDrop's own storage with its progress bar and no browser
notifications. **Save** then hands them all to Downloads at once (Chrome notes each finished file, which a web page
can't prevent). Without HTTPS, files download into Downloads the browser's way as soon as you accept. The page has
to be open to receive; saving in the background would need a native app (see *Next steps*).

The first time it runs, macOS may ask whether Java can accept incoming connections. Click **Allow**, or the phone
won't be able to connect. Guest Wi-Fi networks that isolate clients will also block the connection.

### Share from any app (HTTPS)

The server also speaks HTTPS on the standard port 443, which lets a phone install AndroidDrop as an app and put it
in Android's share menu. It has to be 443: on any other port Chrome only adds a home-screen shortcut, which doesn't
appear in the share menu. (If another program already uses 443, AndroidDrop falls back to 8443 and says so when it
starts.) It's set up once per phone:

1. On the phone, AndroidDrop shows **Share from any app → Set up** (or **Settings → Set up**). Tap **Download**.
2. In **Settings**, search for **CA certificate**, tap **Install anyway**, and pick `AndroidDrop.crt` from
   Downloads.
3. Back in AndroidDrop, tap **I've installed it**. It switches to `https://<mac-ip>` and stays paired.
4. Tap **Install** (or Chrome's *Install app*). AndroidDrop is now in the share menu of Photos, Files, Chrome and
   other apps: pick it, choose the Mac if you have several devices, and it's sent.

The certificate comes from a small certificate authority the server creates in `~/AndroidDrop/.androiddrop/tls`
the first time; its private key never leaves the Mac. The server certificate it signs covers `localhost` and the
Mac's current Wi-Fi addresses, and is reissued by itself on the next start if the address changes, so phones
never need to install anything again. Android shows a permanent "network may be monitored" note for any
certificate you install yourself; removing the certificate in Settings undoes the setup.

The Mac keeps using `http://localhost:8080`: browsers already treat `localhost` as secure. Phones that haven't set
up HTTPS keep working over plain HTTP.

## Features

| | |
|---|---|
| Nearby | Paired devices as large round avatars, AirDrop-style, each with its own colour and a *Nearby*/*Away* status. Click one to pick files, or drop files on it. |
| Accept / Decline | A request dialog with the sender, a summary ("3 photos · 4.2 MB") and photo previews. Closing it keeps the request at the top of *Received* with inline buttons. |
| Save to | On the Mac, pick where accepted files go: `~/AndroidDrop`, a recently used folder, or *Choose folder…* for the macOS folder picker. Phones save into Downloads: a web page can't choose a folder there. |
| Saves itself | On the Mac, accepted files are moved into `~/AndroidDrop`. On a phone, they download into the app on their own, and *Save* puts them in Downloads. The sender sees *Delivered* once the file has fully arrived. |
| Files of any size | Tested with 3 GB files; the server stays around 120 MB of memory whatever the size, since files stream straight to disk. If Wi-Fi drops, an upload reconnects and continues from what arrived, and a download to the phone resumes where it stopped. The Mac doesn't idle-sleep and the phone keeps its screen on while files move. Files over 2 GB go to a phone's Downloads straight from the Mac, so they need room for only one copy. *In progress* shows speed and time left, with cancel and retry. |
| Received / Sent | Views in the sidebar (tabs on phones). On the Mac only the main column scrolls, so long histories never push the page. Grouped by day and searchable (press `/`). Files moved or deleted in Finder are marked as such. |
| Photo thumbnails | Generated on the server (EXIF rotation applied), so a phone doesn't download full-size photos just to show the list. Shown in the request before accepting. |
| Private by default | A transfer is visible only to its two ends. Nothing lands in a folder without consent, unless you turned on auto-accept. |
| Live updates | Requests, answers, upload progress, and devices coming online or going offline are pushed over SSE. A banner explains a lost connection. |
| Pairing | First run shows the QR code right on the Nearby page. On the phone, the code is entered in six separate boxes (paste and OTP autofill work). The dialog shows a success state as soon as a phone connects. |
| Mac niceties | *Show in Finder*, *Open folder*, free-space readout, system notifications for incoming requests, and a pending count in the tab title. |
| Devices | The Mac can remove a device. A phone can rename or un-pair itself. Pairings survive restarts; history doesn't. |

## Security model

The server listens on your LAN, so it assumes other people on the network may be hostile.

- **Host = loopback.** A request counts as coming from the Mac only if it arrives over `127.0.0.1`/`::1` *and* its
  `Host` header is `localhost`. The second check defeats DNS-rebinding pages.
- **Everyone else must pair.** The 6-digit code is single use. It rotates after each pairing, after 5 wrong
  guesses, and every 10 minutes. Pairing issues a random 256-bit token in an `HttpOnly; SameSite=Strict` cookie.
  The server stores only its SHA-256 hash.
- **CSRF.** Every state-changing request needs an `X-AndroidDrop` header, which cross-origin pages can't send
  without a CORS preflight that is never granted. Requests marked `Sec-Fetch-Site: cross-site` are rejected.
- **Consent before saving.** Incoming files wait in a hidden staging folder until the recipient accepts. Declined,
  cancelled and expired transfers are deleted. A file sent to a phone is removed from the Mac an hour after the phone
  downloaded it (a day if it never does).
- **Nothing outlives the server but pairings.** History, notes and links live in memory, and staged files, uploads in
  progress and thumbnails are deleted when the server stops. `~/AndroidDrop/.androiddrop` then holds only
  `devices.json` (paired devices and the Mac's preferences) and `tls/` (the HTTPS certificate). A phone drops its own
  copies when it sees the server restarted.
- **Uploaded content can't attack the UI.** File names are sanitized: path parts and leading dots are stripped,
  `:` is replaced, the length is capped, and duplicates are numbered. Only images, audio, video and plain text are
  rendered inline. Everything else is forced to download, and all content is served with
  `Content-Security-Policy: sandbox` and `nosniff`.

- **HTTPS for phones.** Once a phone has set up the secure connection, its traffic is encrypted and its token
  cookie is marked `Secure`, so it's never sent over plain HTTP again. Moving from HTTP to HTTPS uses a
  one-time, one-minute code that replaces the phone's token.

A phone that hasn't set up HTTPS talks to the Mac over plain HTTP, so anyone on the same Wi-Fi who can sniff
packets could read its transfers. On networks you don't trust, set up HTTPS first.

## Configuration

Set these in `backend/src/main/resources/application.properties` or pass them as `--key=value` to the jar:

| Property | Default | |
|---|---|---|
| `server.port` | `8080` | |
| `drop.storage-dir` | `~/AndroidDrop` | Received files go here; server state goes in `.androiddrop/` |
| `drop.host-name` | computer name | Name shown on phones |
| `drop.pairing-code-ttl` | `10m` | |
| `drop.max-pairing-attempts` | `5` | Wrong guesses before the code rotates |
| `drop.max-text-length` | `100000` | |
| `drop.pending-transfer-ttl` | `1h` | How long a request waits for Accept/Decline before it expires |
| `drop.delivered-retention` | `1h` | How long a file a phone downloaded stays on the Mac, for *Save again* |
| `drop.https-port` | `443` | HTTPS for phones, next to HTTP on `server.port`; falls back to 8443 if taken; `0` turns it off |

## Development

```bash
make dev-server   # Spring Boot on :8080
make dev-web      # Vite with hot reload on :5173, proxying /api to :8080
make test         # backend tests + frontend type-check
```

The Vite proxy forwards the real client address (`X-Forwarded-For`), so a phone using the dev server still has to
pair. To test from a phone, use `make run`: the QR code points at port 8080.

With the Claude desktop app, `.claude/launch.json` defines `androiddrop` (the jar, using a local `.dev-storage/`
folder) and `androiddrop-web` (Vite).

To try the phone flow on the Mac itself, open `http://phone.localhost:8080`. It resolves to loopback, but its
`Host` isn't `localhost`, so the server treats that tab as an unpaired remote device.

### Layout

```
backend/                     Spring Boot 4 (Java 21), Maven wrapper
  src/main/java/com/androiddrop/
    security/DeviceAuthFilter   who is calling: host (loopback) or a paired device (cookie)
    pairing/                    pairing codes, /api/session, /api/pair
    device/                     paired devices and the host's preferences (devices.json)
    transfer/                   AirDrop-style transfers: announce, upload, accept/decline, deliver (in memory)
    events/                     SSE hub + relay of domain events to devices
    host/                       computer name, LAN addresses, Finder integration
    storage/                    folder layout and its cleanup, atomic JSON persistence
frontend/                    React 19 + TypeScript + Vite + Tailwind v4 + Radix UI
  src/components/ui/          design-system primitives: button, modal/bottom sheet, confirm, menu, tooltip...
  src/components/             app screens: sidebar, Send view, Received/Sent views, transfer rows, incoming request...
  src/lib/                    API client, upload queue, live progress store, phone saving, toasts, SSE hook, theme
  src/styles.css              design tokens (colours, shadows, motion) for light and dark themes
```

### API

All endpoints are under `/api`. Non-GET requests need `X-AndroidDrop: 1`.

| Method & path | Who | |
|---|---|---|
| `GET /server` | anyone | Host name (shown on the pairing screen) and HTTPS port |
| `POST /pair` `{code, name}` | anyone | Pair; sets the token cookie |
| `POST /session/handoff` → `{code}`, `POST /pair/handoff` `{code}` | phone, then anyone with the code | Carry a phone's pairing from HTTP to HTTPS |
| `GET /session` | paired | Who am I |
| `PATCH /session` `{name}` | phone | Rename this device |
| `PATCH /session/preferences` `{autoAccept}` | paired | Accept incoming transfers without asking |
| `POST /session/forget` | phone | Un-pair this device |
| `GET /devices` / `DELETE /devices/{id}` | paired / host | List / remove devices |
| `GET /transfers` | paired | Transfers you sent or received, newest first |
| `POST /transfers` `{recipientId, files: [{name, size, mimeType}]}` or `{recipientId, text}` | paired | Announce a transfer; the recipient is asked right away |
| `PUT /transfers/{id}/files/{fileId}[?offset=N]` | sender | Upload one announced file as the raw body; `offset` continues a broken-off upload |
| `GET /transfers/{id}/files/{fileId}/received` | sender | How much of a broken-off upload arrived: where to continue from |
| `POST /transfers/{id}/accept` `[{folder}]` · `/decline` | recipient | Answer. Accepting on the host moves files into `folder` (default: the storage folder) |
| `POST /transfers/{id}/cancel` | sender | Withdraw before delivery |
| `DELETE /transfers/{id}` | either end | Remove from history; staged copies are deleted, saved files stay |
| `GET /transfers/{id}/files/{fileId}/content[?download=true]` | sender, or recipient after accepting | File bytes (Range supported). A phone recipient's download can resume (`Range: bytes=N-`) and marks the file delivered once complete |
| `GET /transfers/{id}/files/{fileId}/thumbnail` | either end | Small cached JPEG preview (allowed before accepting) |
| `POST /transfers/{id}/files/{fileId}/reveal` | host | Show in Finder |
| `GET /events` | paired | SSE: `ready`, `transfer`, `transfer-removed`, `transfer-progress`, `transfer-delivery`, `devices`, `pairing` (host) |
| `GET /host/pairing`, `POST /host/pairing/rotate` | host | Code, expiry and LAN URLs for the QR |
| `POST /host/open-folder` | host | Open `~/AndroidDrop` in Finder |
| `GET /host/folders`, `PUT /host/folders/default` `{path}`, `POST /host/folders/choose` | host | Folders to save into; the default one; open the macOS folder picker |

Outside `/api`: `GET /androiddrop-ca.crt` (the certificate phones install) and `POST /share-target` (where Android
posts shares; the page's service worker normally takes them first).

## Next steps

- **Bonjour/mDNS** advertisement, so the Mac can be reached as `androiddrop.local` instead of an IP.
- **Folder transfer** by zipping on the fly.
- **Native Android companion app** for background receiving (saving without the page open, into a folder you
  choose).
