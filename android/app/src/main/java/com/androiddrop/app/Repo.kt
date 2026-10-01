package com.androiddrop.app

import android.app.Activity
import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class DropApp : Application() {
    lateinit var repo: Repo
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repo(this)
        repo.start()
        // The connection to the computer follows the screen: there while the app is in view, gone soon after it isn't.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var started = 0

            override fun onActivityStarted(activity: Activity) {
                if (started++ == 0) repo.setForeground(true)
            }

            override fun onActivityStopped(activity: Activity) {
                // A rotating screen stops one activity and starts the next; that isn't leaving.
                if (--started == 0 && !activity.isChangingConfigurations) repo.setForeground(false)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}

/**
 * Everything the screens show, and the work behind it: the connection to the computer, live updates, the upload
 * queue and saving received files. Lives as long as the app's process, so a rotated screen doesn't interrupt a transfer.
 */
class Repo(private val app: Application) {

    private val prefs = app.getSharedPreferences("androiddrop", Context.MODE_PRIVATE)

    /** Short messages for the user. */
    val notices = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** Nothing that goes wrong in the background may take the app down: what isn't handled where it happens ends up here. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
        Log.w("AndroidDrop", "Unexpected failure", error)
        notices.tryEmit("Something went wrong. Try that again.")
    })

    val paired = MutableStateFlow(prefs.getString(KEY_TOKEN, null) != null)
    val me = MutableStateFlow<Device?>(null)
    val serverName = MutableStateFlow(prefs.getString(KEY_SERVER, "").orEmpty())
    val devices = MutableStateFlow<List<Device>>(emptyList())
    val transfers = MutableStateFlow<List<Transfer>>(emptyList())
    /** Bytes so far per file id, for files on the move: uploads to the computer, and downloads from it. */
    val uploaded = MutableStateFlow<Map<String, Long>>(emptyMap())
    val downloaded = MutableStateFlow<Map<String, Long>>(emptyMap())
    val connected = MutableStateFlow(false)
    /** Uploads and downloads under way. While there are any, the connection is kept and the screen stays on. */
    val working = MutableStateFlow(0)
    /** Files or text shared in from another app, waiting for a recipient to be picked. */
    val pendingShare = MutableStateFlow<Share?>(null)

    class Share(val uris: List<Uri>, val text: String?)

    /** This phone skips the Accept/Decline prompt. */
    val autoAccept = MutableStateFlow(false)
    /** Where received files go: a folder the user picked, or null for Downloads/AndroidDrop. */
    val saveFolder = MutableStateFlow(prefs.getString(KEY_FOLDER, null)?.let(Uri::parse))
    /** "system", "light" or "dark". */
    val theme = MutableStateFlow(prefs.getString(KEY_THEME, "system") ?: "system")
    /** Where each received file was saved, to open it from the list. Until the app closes. */
    val savedFiles = MutableStateFlow<Map<String, Uri>>(emptyMap())

    /** Photo previews, up to 8 MB of them. */
    private val previews = object : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val unsaved = ConcurrentHashMap<String, IntArray>()

    @Volatile
    private var api: ApiClient? = null
    private var stream: EventSource? = null
    /** Bumped whenever the connection is replaced, so an old stream's callbacks are ignored. */
    @Volatile
    private var generation = 0
    @Volatile
    private var foreground = false
    private var retryPause = RETRY_MS
    /** Changes that live events made while [sync] was fetching: they are newer than what it brings back. */
    private var syncing: SyncWindow? = null
    private val listLock = Any()
    private val uploads = Mutex()
    private val downloads = Mutex()
    private val fetching = ConcurrentHashMap.newKeySet<String>()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()

    private class SyncWindow {
        val changed = HashMap<String, Transfer>()
        val removed = HashSet<String>()
    }

    fun start() {
        // Copies of shared files left behind when the app was closed mid-upload.
        scope.launch { app.cacheDir.listFiles { file -> file.name.startsWith(SHARE_PREFIX) }?.forEach { it.delete() } }
        val token = prefs.getString(KEY_TOKEN, null) ?: return
        val address = prefs.getString(KEY_ADDRESS, null) ?: return
        api = ApiClient(address, token)
    }

    // ---------- Pairing ----------

    /** Pairs with the computer at [address] using the 6-digit [code] it shows. Throws ApiException with a message to show. */
    fun pair(address: String, code: String, name: String) {
        val base = normalize(address)
        val (token, session) = ApiClient(base, null).pair(code.trim(), name.trim())
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_ADDRESS, base)
            .putString(KEY_SERVER, session.optString("serverName"))
            .apply()
        serverName.value = session.optString("serverName")
        api = ApiClient(base, token)
        paired.value = true
        connect()
    }

    fun unpair() {
        val client = api
        scope.launch { runCatching { client?.forget() } }
        forgetPairing()
    }

    /** The computer no longer knows this phone. */
    private fun unpaired() {
        notices.tryEmit("This phone was removed from ${serverName.value}. Pair it again.")
        forgetPairing()
    }

    @Synchronized
    private fun forgetPairing() {
        generation++
        stream?.cancel()
        stream = null
        api = null
        prefs.edit().remove(KEY_TOKEN).apply()
        // Whatever is still uploading or downloading stops at its next step.
        transfers.value.forEach { cancelled.add(it.id) }
        connected.value = false
        transfers.value = emptyList()
        devices.value = emptyList()
        uploaded.value = emptyMap()
        downloaded.value = emptyMap()
        me.value = null
        paired.value = false
    }

    val lastAddress: String get() = prefs.getString(KEY_ADDRESS, "").orEmpty().removePrefix("http://")

    // ---------- Settings ----------

    fun rename(name: String) = request { client -> applySession(client.rename(name.trim())) }

    fun setAutoAccept(value: Boolean) = request { client -> applySession(client.setAutoAccept(value)) }

    /** Asks the computer for something off the main thread, telling the user if it refuses or can't be reached. */
    private fun request(ask: suspend (ApiClient) -> Unit) {
        val client = api ?: return
        scope.launch {
            try {
                ask(client)
            } catch (error: ApiException) {
                if (error.status == 401) unpaired() else notices.tryEmit(error.message.orEmpty())
            }
        }
    }

    private fun applySession(session: JSONObject) {
        me.value = parseDevice(session.getJSONObject("device"))
        serverName.value = session.optString("serverName")
        autoAccept.value = session.optBoolean("autoAccept")
    }

    fun setTheme(mode: String) {
        prefs.edit().putString(KEY_THEME, mode).apply()
        theme.value = mode
    }

    /** Saves received files into [folder] (picked with the system's folder chooser) from now on; null goes back to Downloads. */
    fun setSaveFolder(folder: Uri?) {
        val resolver = app.contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        saveFolder.value?.let { runCatching { resolver.releasePersistableUriPermission(it, flags) } }
        if (folder != null) runCatching { resolver.takePersistableUriPermission(folder, flags) }
        prefs.edit().putString(KEY_FOLDER, folder?.toString()).apply()
        saveFolder.value = folder
    }

    /** "Downloads/AndroidDrop", or the picked folder as "Movies/Trips". */
    fun folderName(folder: Uri?): String {
        if (folder == null) return "Downloads/AndroidDrop"
        val id = runCatching { DocumentsContract.getTreeDocumentId(folder) }.getOrNull() ?: return "the chosen folder"
        return id.substringAfter(':').ifEmpty { "Internal storage" }
    }

    /** A photo's preview if it was fetched before: there straight away, without leaving the main thread. */
    fun cachedPreview(fileId: String): ImageBitmap? = previews.get(fileId)

    /** A photo's small preview, fetched once and kept in memory. Blocks: call off the main thread. */
    fun preview(transferId: String, fileId: String): ImageBitmap? {
        previews.get(fileId)?.let { return it }
        val bytes = api?.thumbnail(transferId, fileId) ?: return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() ?: return null
        previews.put(fileId, bitmap)
        return bitmap
    }

    // ---------- Live connection ----------

    /**
     * The app came into view, or left it. Out of view, the connection is only kept while files are moving: a phone
     * that kept reaching for a computer it has walked away from would wear its battery down for nothing.
     */
    fun setForeground(value: Boolean) {
        foreground = value
        if (value) {
            wake()
        } else {
            // Not at once: an answer to something just tapped (an accepted transfer starting) may still be on its way.
            scope.launch {
                delay(LINGER_MS)
                rest()
            }
        }
    }

    @Synchronized
    private fun wake() {
        retryPause = RETRY_MS
        if (stream == null) connect()
    }

    /** Drops the connection if nothing needs it. */
    @Synchronized
    private fun rest() {
        if (foreground || working.value > 0 || stream == null) return
        generation++
        stream?.cancel()
        stream = null
        connected.value = false
    }

    @Synchronized
    private fun connect() {
        val client = api ?: return
        val current = ++generation
        stream?.cancel()
        val request = client.request("/api/events").header("Accept", "text/event-stream").build()
        stream = EventSources.createFactory(client.streaming).newEventSource(request, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (current != generation) return
                runCatching { handle(type, data) }
            }

            override fun onClosed(eventSource: EventSource) = lost(current)

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                if (response?.code == 401) {
                    if (current == generation) unpaired()
                } else {
                    lost(current)
                }
            }
        })
    }

    /** The stream ended. Tries again while there's a reason to, less and less often while the computer stays out of reach. */
    @Synchronized
    private fun lost(current: Int) {
        if (current != generation) return
        stream = null
        connected.value = false
        if (!foreground && working.value == 0) return
        val pause = retryPause
        retryPause = minOf(retryPause * 2, MAX_RETRY_MS)
        scope.launch {
            delay(pause)
            retry(current)
        }
    }

    @Synchronized
    private fun retry(current: Int) {
        if (current == generation && stream == null && (foreground || working.value > 0)) connect()
    }

    @Synchronized
    private fun reached() {
        retryPause = RETRY_MS
    }

    private fun handle(type: String?, data: String) {
        when (type) {
            "ready" -> {
                reached()
                connected.value = true
                scope.launch { sync() }
            }
            "transfer" -> upsert(parseTransfer(JSONObject(data)))
            "transfer-removed" -> removed(JSONObject(data).getString("id"))
            "transfer-progress" -> JSONObject(data).let { event -> uploaded.update { it + (event.getString("fileId") to event.getLong("received")) } }
            "transfer-delivery" -> JSONObject(data).let { event -> downloaded.update { it + (event.getString("fileId") to event.getLong("sent")) } }
            "devices" -> JSONArray(data).let { list -> devices.value = (0 until list.length()).map { parseDevice(list.getJSONObject(it)) } }
        }
    }

    private fun sync() {
        val client = api ?: return
        val window = SyncWindow()
        synchronized(listLock) { syncing = window }
        try {
            applySession(client.session())
            devices.value = client.devices()
            val fetched = client.transfers()
            val all = synchronized(listLock) {
                // An event that arrived while this was fetching is newer than the list, and must not be undone by it.
                val known = fetched.mapTo(HashSet()) { it.id }
                val merged = window.changed.values.filter { it.id !in known } +
                    fetched.filter { it.id !in window.removed }.map { window.changed[it.id] ?: it }
                transfers.value = merged
                merged
            }
            all.forEach(::settle)
            all.forEach(::fetchReady)
        } catch (error: ApiException) {
            // Out of reach again already: the stream notices too, and reconnecting syncs once more.
            if (error.status == 401) unpaired()
        } finally {
            synchronized(listLock) { if (syncing === window) syncing = null }
        }
    }

    private fun upsert(transfer: Transfer) {
        synchronized(listLock) {
            syncing?.let {
                it.removed.remove(transfer.id)
                it.changed[transfer.id] = transfer
            }
            transfers.update { list -> if (list.any { it.id == transfer.id }) list.map { if (it.id == transfer.id) transfer else it } else listOf(transfer) + list }
        }
        if (!transfer.isOpen) cancelled.add(transfer.id)
        settle(transfer)
        fetchReady(transfer)
    }

    private fun removed(id: String) {
        cancelled.add(id)
        val gone = synchronized(listLock) {
            syncing?.let {
                it.changed.remove(id)
                it.removed.add(id)
            }
            val found = transfers.value.firstOrNull { it.id == id }
            transfers.update { list -> list.filter { it.id != id } }
            found
        }
        val ids = gone?.files?.map { it.id } ?: return
        uploaded.update { it - ids }
        downloaded.update { it - ids }
    }

    /** Forgets the progress of files that have stopped moving, so those maps only ever hold what's under way. */
    private fun settle(transfer: Transfer) {
        val ids = transfer.files.map { it.id }
        if (transfer.files.none { it.state == "UPLOADING" } && ids.any { it in uploaded.value }) uploaded.update { it - ids }
        if (transfer.files.none { it.state == "READY" } && ids.any { it in downloaded.value }) downloaded.update { it - ids }
    }

    /** Counts [block] as work under way: see [working]. */
    private suspend fun <T> busy(block: suspend () -> T): T {
        working.update { it + 1 }
        try {
            return block()
        } finally {
            working.update { it - 1 }
            rest()
        }
    }

    // ---------- Answering ----------

    fun accept(transfer: Transfer) = act(transfer, "accept")

    fun decline(transfer: Transfer) = act(transfer, "decline")

    fun cancel(transfer: Transfer) {
        cancelled.add(transfer.id)
        act(transfer, "cancel")
    }

    private fun act(transfer: Transfer, action: String) = request { client -> upsert(client.act(transfer.id, action)) }

    fun remove(transfer: Transfer) {
        cancelled.add(transfer.id)
        request { client ->
            client.remove(transfer.id)
            removed(transfer.id)
        }
    }

    // ---------- Sending ----------

    fun sendText(recipientId: String, text: String) = request { client -> upsert(client.sendText(recipientId, text)) }

    fun sendFiles(recipientId: String, uris: List<Uri>) = request { client ->
        busy {
            val files = uris.mapNotNull(::describe)
            try {
                if (files.isEmpty()) {
                    notices.tryEmit("Those files couldn't be read.")
                } else {
                    val transfer = client.announce(recipientId, files)
                    upsert(transfer)
                    // One upload at a time, in the order they were picked.
                    uploads.withLock {
                        transfer.files.zip(files).forEach { (announced, file) ->
                            if (announced.state == "UPLOADING") upload(client, transfer.id, announced.id, file)
                        }
                    }
                }
            } finally {
                files.forEach { it.temporary?.delete() }
            }
        }
    }

    /** Sends one file, continuing from where the computer got to whenever the connection drops. */
    private suspend fun upload(client: ApiClient, transferId: String, fileId: String, file: OutgoingFile) {
        var offset = 0L
        var lastProgress = System.currentTimeMillis()
        var pause = 1000L
        while (true) {
            if (transferId in cancelled) return
            try {
                upsert(client.upload(transferId, fileId, file.size, offset, file.open))
                return
            } catch (error: ApiException) {
                val transient = error.status in TRANSIENT
                if (!transient || System.currentTimeMillis() - lastProgress > RECONNECT_FOR_MS) {
                    // 409/404: declined, cancelled or removed meanwhile, which the list already shows.
                    if (error.status != 409 && error.status != 404) notices.tryEmit("${file.name}: ${error.message}")
                    return
                }
            } catch (error: RuntimeException) {
                // The app that shared the file has taken back the permission to read it.
                notices.tryEmit("${file.name} can't be read any more. Share it again.")
                return
            }
            delay(pause)
            val received = runCatching { client.received(transferId, fileId) }.getOrDefault(offset)
            if (received > offset) {
                lastProgress = System.currentTimeMillis()
                pause = 1000
            } else {
                pause = minOf(pause * 2, 10_000)
            }
            offset = received
        }
    }

    /** Name, size and type of something another app or the picker handed us. Null if it can't be read. */
    private fun describe(uri: Uri): OutgoingFile? {
        val resolver = app.contentResolver
        var name = "file"
        var size = -1L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.let { name = it }
                    if (!cursor.isNull(1)) size = cursor.getLong(1)
                }
            }
        }
        val type = runCatching { resolver.getType(uri) }.getOrNull() ?: "application/octet-stream"
        if (size >= 0) {
            return OutgoingFile(name, size, type) { resolver.openInputStream(uri) ?: throw IOException("Can't open $name") }
        }
        // No size on offer (some apps stream what they share): copy it out once to learn it. Deleted once it's sent.
        val copy = runCatching { File.createTempFile(SHARE_PREFIX, null, app.cacheDir) }.getOrNull() ?: return null
        val copied = runCatching {
            val input = resolver.openInputStream(uri) ?: throw IOException("Can't open $name")
            input.use { source -> copy.outputStream().use { source.copyTo(it) } }
        }
        if (copied.isFailure) {
            copy.delete()
            return null
        }
        return OutgoingFile(name, copy.length(), type, copy) { copy.inputStream() }
    }

    // ---------- Receiving ----------

    /** Saves the files of a transfer this phone accepted, as soon as they are ready on the computer. */
    private fun fetchReady(transfer: Transfer) {
        val self = me.value ?: return
        if (transfer.recipientId != self.id || transfer.status != "ACCEPTED") return
        val ready = transfer.files.filter { it.state == "READY" && fetching.add(it.id) }
        if (ready.isEmpty()) return
        // [files still to save, files saved]: one "Saved" message per batch, not one per file.
        val tally = unsaved.getOrPut(transfer.id) { IntArray(2) }
        synchronized(tally) { tally[0] += ready.size }
        ready.forEach { file ->
            scope.launch {
                val saved = busy { downloads.withLock { save(transfer, file) } }
                val finished = synchronized(tally) {
                    tally[0]--
                    if (saved) tally[1]++
                    if (tally[0] == 0) tally[1].also { tally[1] = 0 } else -1
                }
                if (finished >= 0) unsaved.remove(transfer.id, tally)
                if (finished > 0) {
                    val what = if (finished == 1) file.name else "$finished files"
                    notices.tryEmit("Saved $what to ${folderName(saveFolder.value)}")
                }
            }
        }
    }

    /** A new, empty file for a received one: in the picked folder, or in Downloads/AndroidDrop. */
    private fun createTarget(file: TransferFile): Uri? {
        val resolver = app.contentResolver
        saveFolder.value?.let { folder ->
            val created = runCatching {
                val parent = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
                DocumentsContract.createDocument(resolver, parent, file.mimeType, file.name)
            }.getOrNull()
            if (created != null) return created
            notices.tryEmit("Can't save into ${folderName(folder)} any more, so it's going to Downloads.")
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, file.mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/AndroidDrop")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        return resolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
    }

    private fun isDownloadsEntry(target: Uri) = target.authority == MediaStore.AUTHORITY

    /** Saves one file to where received files go. True once it's fully there. */
    private suspend fun save(transfer: Transfer, file: TransferFile): Boolean {
        val client = api
        val resolver = app.contentResolver
        val target = if (client == null) null else runCatching { createTarget(file) }.getOrNull()
        if (client == null || target == null) {
            if (client != null) notices.tryEmit("Couldn't save ${file.name}.")
            fetching.remove(file.id)
            return false
        }
        val done = try {
            download(client, transfer, file, target)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // The folder was taken away mid-file, or the phone is out of space.
            false
        }
        if (done) {
            runCatching { if (isDownloadsEntry(target)) resolver.update(target, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null) }
            savedFiles.update { it + (file.id to target) }
        } else {
            runCatching { if (isDownloadsEntry(target)) resolver.delete(target, null, null) else DocumentsContract.deleteDocument(resolver, target) }
            fetching.remove(file.id)
            if (transfer.id !in cancelled) notices.tryEmit("Couldn't save ${file.name}. It'll try again when you reopen AndroidDrop.")
        }
        return done
    }

    /** Downloads [file] into [target], carrying on from what's written whenever the connection drops. True once it's all there. */
    private suspend fun download(client: ApiClient, transfer: Transfer, file: TransferFile, target: Uri): Boolean {
        val resolver = app.contentResolver
        var written = 0L
        var lastProgress = System.currentTimeMillis()
        var pause = 1000L
        while (true) {
            if (transfer.id in cancelled) return false
            val before = written
            try {
                client.download(transfer.id, file.id, written).use { response ->
                    // 200 means the computer is sending the whole file again, not continuing.
                    if (response.code != 206) written = 0
                    val output = resolver.openOutputStream(target, if (written > 0) "wa" else "wt") ?: throw IOException("Can't write ${file.name}")
                    output.use { sink ->
                        val source = response.body?.byteStream() ?: throw IOException("Empty response")
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            val read = source.read(buffer)
                            if (read == -1) break
                            sink.write(buffer, 0, read)
                            written += read
                        }
                    }
                }
                if (written >= file.size) return true
            } catch (error: ApiException) {
                // Gone on the computer, or no longer ours to fetch.
                if (error.status != 0) return false
            } catch (error: IOException) {
                // Wi-Fi dropped mid-file: carry on from what's written.
            }
            if (written > before) {
                lastProgress = System.currentTimeMillis()
                pause = 1000
            } else if (System.currentTimeMillis() - lastProgress > RECONNECT_FOR_MS) {
                return false
            } else {
                pause = minOf(pause * 2, 10_000)
            }
            delay(pause)
        }
    }

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_ADDRESS = "address"
        private const val KEY_SERVER = "serverName"
        private const val KEY_FOLDER = "saveFolder"
        private const val KEY_THEME = "theme"
        private const val SHARE_PREFIX = "androiddrop-share"
        /** How long to keep reconnecting without getting any further before giving up on a file. */
        private const val RECONNECT_FOR_MS = 3 * 60_000L
        /** The pause before reconnecting to the computer: this at first, doubling up to the longest. */
        private const val RETRY_MS = 3_000L
        private const val MAX_RETRY_MS = 30_000L
        /** How long the connection is kept after the app leaves the screen with nothing moving. */
        private const val LINGER_MS = 60_000L
        /** Worth trying again: the connection dropped, or the computer hasn't noticed the old one did. */
        private val TRANSIENT = setOf(0, 408, 416, 423, 502, 503, 504)

        val deviceName: String get() = Build.MODEL ?: "Android phone"

        /** "192.168.1.20" → "http://192.168.1.20:8080"; a full address is kept as typed. */
        fun normalize(address: String): String {
            var value = address.trim().trimEnd('/')
            if (!value.startsWith("http://") && !value.startsWith("https://")) value = "http://$value"
            val hostPart = value.substringAfter("://")
            if (value.startsWith("http://") && !hostPart.contains(':')) value += ":8080"
            return value
        }
    }
}
