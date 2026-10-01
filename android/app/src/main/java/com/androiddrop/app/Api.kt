package com.androiddrop.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

data class Device(val id: String, val name: String, val platform: String, val host: Boolean, val online: Boolean)

/** UPLOADING → READY (on the Mac) → SAVED (in the Mac's folder) or DELIVERED (downloaded by a phone). */
data class TransferFile(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val state: String,
    val available: Boolean,
    /** The computer can serve a small preview of it. */
    val thumbnail: Boolean,
)

data class Transfer(
    val id: String,
    val senderId: String,
    val senderName: String,
    val recipientId: String,
    val recipientName: String,
    /** PENDING, ACCEPTED, DECLINED, CANCELLED or EXPIRED. */
    val status: String,
    /** Set for a note or link, which has no files. */
    val text: String?,
    val files: List<TransferFile>,
    val createdAt: String,
) {
    val isOpen get() = status == "PENDING" || status == "ACCEPTED"
    val totalSize get() = files.sumOf { it.size }
}

/** A request the server refused. 0 means it couldn't be reached. */
class ApiException(val status: Int, message: String) : IOException(message)

fun parseDevice(json: JSONObject) = Device(
    id = json.getString("id"),
    name = json.getString("name"),
    platform = json.optString("platform", "OTHER"),
    host = json.optBoolean("host"),
    online = json.optBoolean("online"),
)

fun parseTransfer(json: JSONObject): Transfer {
    val files = json.optJSONArray("files") ?: JSONArray()
    return Transfer(
        id = json.getString("id"),
        senderId = json.getString("senderId"),
        senderName = json.optString("senderName"),
        recipientId = json.getString("recipientId"),
        recipientName = json.optString("recipientName"),
        status = json.getString("status"),
        text = if (json.isNull("text")) null else json.getString("text"),
        files = (0 until files.length()).map { index ->
            val file = files.getJSONObject(index)
            TransferFile(
                id = file.getString("id"),
                name = file.getString("name"),
                size = file.getLong("size"),
                mimeType = file.optString("mimeType", "application/octet-stream"),
                state = file.getString("state"),
                available = file.optBoolean("available"),
                thumbnail = file.optBoolean("thumbnail"),
            )
        },
        createdAt = json.optString("createdAt"),
    )
}

/**
 * The AndroidDrop server's HTTP API, as a paired phone uses it. Calls block, so run them off the main thread.
 * The server tells phones from computers by the User-Agent, and wants X-AndroidDrop on anything that changes state.
 */
class ApiClient(val baseUrl: String, private val token: String?) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** For the event stream, which stays open and is quiet between events. */
    val streaming: OkHttpClient = http.newBuilder().readTimeout(0, TimeUnit.SECONDS).build()

    fun request(path: String): Request.Builder {
        val builder = Request.Builder()
            .url(baseUrl + path)
            .header("User-Agent", USER_AGENT)
            .header("X-AndroidDrop", "1")
        if (token != null) builder.header("Cookie", "$TOKEN_COOKIE=$token")
        return builder
    }

    private fun execute(request: Request): Response {
        val response = try {
            http.newCall(request).execute()
        } catch (error: IOException) {
            throw ApiException(0, "Can't reach the computer. Is AndroidDrop running, and are you on the same Wi-Fi?")
        }
        if (response.isSuccessful) return response
        val body = response.body?.string().orEmpty()
        response.close()
        val detail = runCatching { JSONObject(body).optString("detail") }.getOrNull().orEmpty()
        throw ApiException(response.code, detail.ifEmpty { "Request failed (${response.code})" })
    }

    private fun call(method: String, path: String, body: JSONObject? = null): String {
        val payload = body?.toString()?.toRequestBody(JSON) ?: if (method == "GET") null else ByteArray(0).toRequestBody(null)
        return execute(request(path).method(method, payload).build()).use { it.body?.string().orEmpty() }
    }

    fun serverName(): String = JSONObject(call("GET", "/api/server")).getString("name")

    /** Pairs this phone. Returns the token to keep, and who we now are. */
    fun pair(code: String, name: String): Pair<String, JSONObject> {
        val body = JSONObject().put("code", code).put("name", name)
        execute(request("/api/pair").post(body.toString().toRequestBody(JSON)).build()).use { response ->
            val cookie = response.headers("Set-Cookie").firstOrNull { it.startsWith("$TOKEN_COOKIE=") }
                ?: throw ApiException(500, "The computer didn't send a pairing token.")
            val token = cookie.substringAfter('=').substringBefore(';')
            return token to JSONObject(response.body?.string().orEmpty())
        }
    }

    fun session(): JSONObject = JSONObject(call("GET", "/api/session"))

    fun devices(): List<Device> = JSONArray(call("GET", "/api/devices")).let { list -> (0 until list.length()).map { parseDevice(list.getJSONObject(it)) } }

    fun transfers(): List<Transfer> = JSONArray(call("GET", "/api/transfers")).let { list -> (0 until list.length()).map { parseTransfer(list.getJSONObject(it)) } }

    fun rename(name: String): JSONObject = JSONObject(call("PATCH", "/api/session", JSONObject().put("name", name)))

    fun setAutoAccept(value: Boolean): JSONObject = JSONObject(call("PATCH", "/api/session/preferences", JSONObject().put("autoAccept", value)))

    /** A small JPEG preview of a photo, or null if there isn't one. */
    fun thumbnail(transferId: String, fileId: String): ByteArray? = try {
        execute(request("/api/transfers/$transferId/files/$fileId/thumbnail").get().build()).use { it.body?.bytes() }
    } catch (error: ApiException) {
        null
    }

    fun forget() {
        call("POST", "/api/session/forget")
    }

    fun announce(recipientId: String, files: List<OutgoingFile>): Transfer {
        val list = JSONArray()
        files.forEach { list.put(JSONObject().put("name", it.name).put("size", it.size).put("mimeType", it.mimeType)) }
        return parseTransfer(JSONObject(call("POST", "/api/transfers", JSONObject().put("recipientId", recipientId).put("files", list))))
    }

    fun sendText(recipientId: String, text: String): Transfer =
        parseTransfer(JSONObject(call("POST", "/api/transfers", JSONObject().put("recipientId", recipientId).put("text", text))))

    fun act(transferId: String, action: String): Transfer = parseTransfer(JSONObject(call("POST", "/api/transfers/$transferId/$action")))

    fun remove(transferId: String) {
        call("DELETE", "/api/transfers/$transferId")
    }

    /** How much of a broken-off upload the computer kept. */
    fun received(transferId: String, fileId: String): Long =
        JSONObject(call("GET", "/api/transfers/$transferId/files/$fileId/received")).getLong("received")

    /** Uploads a file from byte [offset] on, streaming it from [open]. */
    fun upload(transferId: String, fileId: String, size: Long, offset: Long, open: () -> InputStream): Transfer {
        val body = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = size - offset
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) {
                open().use { input ->
                    var toSkip = offset
                    while (toSkip > 0) {
                        val skipped = input.skip(toSkip)
                        if (skipped > 0) toSkip -= skipped else if (input.read() == -1) break else toSkip--
                    }
                    val buffer = ByteArray(128 * 1024)
                    var remaining = size - offset
                    while (remaining > 0) {
                        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (read == -1) break
                        sink.write(buffer, 0, read)
                        remaining -= read
                    }
                }
            }
        }
        val query = if (offset > 0) "?offset=$offset" else ""
        val request = request("/api/transfers/$transferId/files/$fileId$query").put(body).build()
        // No overall deadline: a big video takes as long as it takes. A stalled connection still times out.
        val response = try {
            http.newCall(request).execute()
        } catch (error: IOException) {
            throw ApiException(0, "Connection lost during upload.")
        }
        response.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val detail = runCatching { JSONObject(text).optString("detail") }.getOrNull().orEmpty()
                throw ApiException(it.code, detail.ifEmpty { "Upload failed (${it.code})" })
            }
            return parseTransfer(JSONObject(text))
        }
    }

    /** Opens a received file for download, from byte [from] on. The caller closes the response. */
    fun download(transferId: String, fileId: String, from: Long): Response {
        val builder = request("/api/transfers/$transferId/files/$fileId/content?download=true").get()
        if (from > 0) builder.header("Range", "bytes=$from-").header("If-Range", "\"$fileId\"")
        return execute(builder.build())
    }

    companion object {
        const val TOKEN_COOKIE = "adrop_token"
        const val USER_AGENT = "AndroidDrop/0.1 (Linux; Android)"
        private val JSON = "application/json".toMediaType()
    }
}

/** A file about to be sent, read from wherever the phone's picker or another app pointed. */
class OutgoingFile(val name: String, val size: Long, val mimeType: String, val open: () -> InputStream)
