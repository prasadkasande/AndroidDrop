package com.androiddrop.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------- Look: the same warm cream and ink as the computer's side ----------

private val Light = lightColorScheme(
    primary = Color(0xFF2B2622), onPrimary = Color(0xFFFBF8F3),
    secondaryContainer = Color(0xFFEBE5DA), onSecondaryContainer = Color(0xFF2B2622),
    background = Color(0xFFF7F4EE), onBackground = Color(0xFF2B2622),
    surface = Color(0xFFFFFDF9), onSurface = Color(0xFF2B2622),
    surfaceVariant = Color(0xFFEBE5DA), onSurfaceVariant = Color(0xFF6F655B),
    surfaceContainerLowest = Color(0xFFFFFDF9), surfaceContainerLow = Color(0xFFFFFDF9), surfaceContainer = Color(0xFFF7F4EE),
    surfaceContainerHigh = Color(0xFFFFFDF9), surfaceContainerHighest = Color(0xFFEBE5DA),
    outline = Color(0xFFD6CCBD), outlineVariant = Color(0xFFE6DFD3),
    error = Color(0xFFC2412D), onError = Color.White,
)

private val Dark = darkColorScheme(
    primary = Color(0xFFF3EDE4), onPrimary = Color(0xFF1B1916),
    secondaryContainer = Color(0xFF332E28), onSecondaryContainer = Color(0xFFF3EDE4),
    background = Color(0xFF1B1916), onBackground = Color(0xFFF3EDE4),
    surface = Color(0xFF26221E), onSurface = Color(0xFFF3EDE4),
    surfaceVariant = Color(0xFF332E28), onSurfaceVariant = Color(0xFFB8AD9F),
    surfaceContainerLowest = Color(0xFF26221E), surfaceContainerLow = Color(0xFF26221E), surfaceContainer = Color(0xFF1B1916),
    surfaceContainerHigh = Color(0xFF26221E), surfaceContainerHighest = Color(0xFF332E28),
    outline = Color(0xFF4A4239), outlineVariant = Color(0xFF37312A),
    error = Color(0xFFEA8A78), onError = Color(0xFF1B1916),
)

@Composable
fun DropTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

/** A section's own colour, worn by its tab while it's open: the pill behind the icon, and the icon. */
private class SectionColour(val halo: Color, val ink: Color)

private class Section(val label: String, val icon: ImageVector, val light: SectionColour, val dark: SectionColour)

private fun strokeIcon(vararg paths: String): ImageVector =
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        paths.forEach { path ->
            addPath(
                pathData = PathParser().parsePathString(path).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

private val NearbyIcon = strokeIcon(
    "M8.4 8.4a5.1 5.1 0 0 0 0 7.2M15.6 8.4a5.1 5.1 0 0 1 0 7.2M5.6 5.6a9 9 0 0 0 0 12.8M18.4 5.6a9 9 0 0 1 0 12.8",
    "M12 9.4a2.6 2.6 0 1 0 0 5.2a2.6 2.6 0 1 0 0-5.2z",
)
private val ReceivedIcon = strokeIcon(
    "M3.8 13.6h4.4l1.6 2.6h4.4l1.6-2.6h4.4v4a2.4 2.4 0 0 1-2.4 2.4H6.2a2.4 2.4 0 0 1-2.4-2.4z",
    "M12 3.6v8.6M8.6 9l3.4 3.4L15.4 9",
)
private val SentIcon = strokeIcon("M20.4 3.6 3.9 10.4l6.7 2.9 2.9 6.7zM20.4 3.6l-9.8 9.7")
private val LaptopIcon = strokeIcon("M20 16V7a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v9m16 0H4m16 0 1.28 2.55a1 1 0 0 1-.9 1.45H3.62a1 1 0 0 1-.9-1.45L4 16")
private val PhoneIcon = strokeIcon("M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z", "M12 18h.01")

private val SECTIONS = listOf(
    Section("Nearby", NearbyIcon, SectionColour(Color(0xFFD6EFDF), Color(0xFF2F6B47)), SectionColour(Color(0xFF243D2D), Color(0xFFA9DCBB))),
    Section("Received", ReceivedIcon, SectionColour(Color(0xFFDBE9FD), Color(0xFF2A4F86)), SectionColour(Color(0xFF26385A), Color(0xFFB9D4FF))),
    Section("Sent", SentIcon, SectionColour(Color(0xFFFDE0CF), Color(0xFF8A4A24)), SectionColour(Color(0xFF4D3325), Color(0xFFFFC9A8))),
)

// ---------- App ----------

@Composable
fun App(repo: Repo) {
    val paired by repo.paired.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        repo.notices.collect { message ->
            // The newest message replaces the one on screen, so a run of them doesn't queue up for minutes.
            snackbar.currentSnackbarData?.dismiss()
            launch { snackbar.showSnackbar(message) }
        }
    }
    if (paired) Home(repo, snackbar) else PairScreen(repo, snackbar)
}

@Composable
private fun PairScreen(repo: Repo, snackbar: SnackbarHostState) {
    var address by rememberSaveable { mutableStateOf(repo.lastAddress) }
    var code by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf(Repo.deviceName) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text("Connect to your computer", fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
            Text(
                "On the computer, open AndroidDrop and choose Add device. Type the address and the 6-digit code it shows.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
            )
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Computer's address") },
                placeholder = { Text("192.168.1.20:8080") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(6) },
                label = { Text("6-digit code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text("This phone's name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 15.sp) }
            Button(
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        val problem = withContext(Dispatchers.IO) {
                            try {
                                repo.pair(address, code, name)
                                null
                            } catch (failure: ApiException) {
                                failure.message
                            } catch (failure: Exception) {
                                "That address doesn't look right. It should be like 192.168.1.20:8080."
                            }
                        }
                        error = problem
                        busy = false
                    }
                },
                enabled = !busy && address.isNotBlank() && code.length == 6 && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(if (busy) "Connecting…" else "Connect", fontSize = 16.sp)
            }
            Text("Both devices need to be on the same Wi-Fi.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Home(repo: Repo, snackbar: SnackbarHostState) {
    val me by repo.me.collectAsStateWithLifecycle()
    val devices by repo.devices.collectAsStateWithLifecycle()
    val transfers by repo.transfers.collectAsStateWithLifecycle()
    val connected by repo.connected.collectAsStateWithLifecycle()
    val serverName by repo.serverName.collectAsStateWithLifecycle()
    val uploaded by repo.uploaded.collectAsStateWithLifecycle()
    val downloaded by repo.downloaded.collectAsStateWithLifecycle()
    val share by repo.pendingShare.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var confirmUnpair by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<String?>(null) }
    val dark = isSystemInDarkTheme()

    val meId = me?.id
    val peers = devices.filter { it.id != meId }
    val received = transfers.filter { it.recipientId == meId && it.isOpen }
    val sent = transfers.filter { it.senderId == meId }
    val waiting = received.count { it.status == "PENDING" }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val to = target
        if (to != null && uris.isNotEmpty()) repo.sendFiles(to, uris)
    }
    val send: (String, Repo.Share) -> Unit = { deviceId, pending ->
        if (pending.text != null) repo.sendText(deviceId, pending.text) else repo.sendFiles(deviceId, pending.uris)
        repo.pendingShare.value = null
        tab = 2
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("AndroidDrop", fontWeight = FontWeight.SemiBold) },
                actions = { TextButton(onClick = { confirmUnpair = true }) { Text("Unpair") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                SECTIONS.forEachIndexed { index, section ->
                    val colour = if (dark) section.dark else section.light
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = {
                            BadgedBox(badge = { if (index == 1 && waiting > 0) Badge { Text("$waiting") } }) {
                                Icon(section.icon, contentDescription = null)
                            }
                        },
                        label = { Text(section.label, fontSize = 13.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = colour.halo,
                            selectedIconColor = colour.ink,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (tab) {
                0 -> {
                    item {
                        Text(
                            if (connected) "Connected to $serverName. Tap a device to send it files." else "Reconnecting to $serverName…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 15.sp,
                        )
                    }
                    items(peers, key = { it.id }) { device ->
                        DeviceCard(device) {
                            target = device.id
                            picker.launch(arrayOf("*/*"))
                        }
                    }
                    if (peers.isEmpty()) item { Empty("No other devices yet", "Devices paired with $serverName show up here.") }
                    val live = transfers.filter { it.isOpen && it.text == null && phaseOf(it) != "done" }
                    if (live.isNotEmpty()) item { Text("In progress", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp)) }
                    items(live, key = { it.id }) { TransferCard(it, meId, uploaded, downloaded, repo) }
                }
                1 -> {
                    if (received.isEmpty()) item { Empty("Nothing received yet", "When a device shares something with you, you'll be asked to accept it. Files are saved to Downloads.") }
                    items(received, key = { it.id }) { TransferCard(it, meId, uploaded, downloaded, repo) }
                }
                else -> {
                    if (sent.isEmpty()) item { Empty("Nothing sent yet", "Pick a device on Nearby, or share to AndroidDrop from another app.") }
                    items(sent, key = { it.id }) { TransferCard(it, meId, uploaded, downloaded, repo) }
                }
            }
        }
    }

    // Something shared in from another app: straight to the only other device, or ask which one.
    share?.let { pending ->
        if (peers.size == 1) {
            LaunchedEffect(pending) { send(peers[0].id, pending) }
        } else if (peers.size > 1) {
            AlertDialog(
                onDismissRequest = { repo.pendingShare.value = null },
                title = { Text("Send to") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        peers.forEach { device -> DeviceCard(device) { send(device.id, pending) } }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { repo.pendingShare.value = null }) { Text("Cancel") } },
            )
        }
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text("Unpair this phone?") },
            text = { Text("You'll need a new code from $serverName to connect again.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnpair = false
                    repo.unpair()
                }) { Text("Unpair", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DeviceCard(device: Device, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (device.host || device.platform == "MAC") LaptopIcon else PhoneIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(device.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (device.online) "Nearby" else "Away · gets it when it opens AndroidDrop",
                    fontSize = 14.sp,
                    color = if (device.online) Color(0xFF3F8F5A) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Empty(title: String, detail: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(detail, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun TransferCard(transfer: Transfer, meId: String?, uploaded: Map<String, Long>, downloaded: Map<String, Long>, repo: Repo) {
    val incoming = transfer.recipientId == meId
    val peer = if (incoming) transfer.senderName else transfer.recipientName
    val phase = phaseOf(transfer)
    val total = transfer.totalSize.coerceAtLeast(1)
    val sentBytes = transfer.files.sumOf { file ->
        when (file.state) {
            "UPLOADING" -> minOf(file.size, uploaded[file.id] ?: 0L)
            "FAILED", "REMOVED" -> 0L
            else -> file.size
        }
    }
    val savedBytes = transfer.files.sumOf { file ->
        when (file.state) {
            "DELIVERED" -> file.size
            "READY" -> minOf(file.size, downloaded[file.id] ?: 0L)
            else -> 0L
        }
    }
    val fraction = (if (phase == "saving") savedBytes else sentBytes).toFloat() / total
    val percent = (fraction * 100).toInt()
    val what = summarize(transfer)
    val status = when (phase) {
        "waiting" -> if (incoming) "Wants to share with you" else "Waiting for $peer to accept"
        "transferring" -> "${if (incoming) "Receiving" else "Sending"} · $percent%"
        "saving" -> if (incoming) "Saving to Downloads · $percent%" else "$peer is downloading · $percent%"
        "done" -> when {
            transfer.text != null -> if (incoming) "Accepted" else "Delivered"
            incoming -> "Saved to Downloads"
            else -> "Delivered"
        }
        "failed" -> if (incoming) "Interrupted" else "Upload failed"
        "declined" -> "Declined"
        "cancelled" -> "Cancelled"
        else -> "Expired"
    }
    val clipboard = LocalClipboardManager.current
    val links = LocalUriHandler.current

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (incoming) "$peer ${if (phase == "waiting") "wants to share" else "sent"} $what" else "${what.replaceFirstChar(Char::uppercase)} to $peer",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                if (transfer.files.isEmpty()) status else "$status · ${formatBytes(transfer.totalSize)}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (phase == "transferring" || phase == "saving") {
                LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }
            transfer.text?.let { Text(it, fontSize = 15.sp, maxLines = 6, overflow = TextOverflow.Ellipsis) }
            transfer.files.take(4).forEach { file ->
                Text("${file.name} · ${formatBytes(file.size)}", fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (transfer.files.size > 4) Text("and ${transfer.files.size - 4} more", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                when {
                    phase == "waiting" && incoming -> {
                        FilledTonalButton(onClick = { repo.decline(transfer) }, modifier = Modifier.weight(1f).height(48.dp)) { Text("Decline") }
                        Button(
                            onClick = {
                                // Like AirDrop: an accepted link opens, an accepted note is copied.
                                transfer.text?.let { text ->
                                    if (Regex("^https?://\\S+$").matches(text.trim())) runCatching { links.openUri(text.trim()) } else clipboard.setText(AnnotatedString(text))
                                }
                                repo.accept(transfer)
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) { Text("Accept") }
                    }
                    !incoming && (phase == "waiting" || phase == "transferring" || phase == "saving" || phase == "failed") ->
                        FilledTonalButton(onClick = { repo.cancel(transfer) }, modifier = Modifier.height(44.dp)) { Text("Cancel") }
                    else -> TextButton(onClick = { repo.remove(transfer) }) { Text("Remove") }
                }
            }
        }
    }
}

// ---------- Wording ----------

/** waiting, transferring, saving (a phone is downloading), done, failed, declined, cancelled or expired. */
private fun phaseOf(transfer: Transfer): String = when (transfer.status) {
    "PENDING" -> "waiting"
    "DECLINED" -> "declined"
    "CANCELLED" -> "cancelled"
    "EXPIRED" -> "expired"
    else -> when {
        transfer.files.all { it.state == "SAVED" || it.state == "DELIVERED" } -> "done"
        transfer.files.any { it.state == "UPLOADING" } -> "transferring"
        transfer.files.any { it.state == "FAILED" } -> "failed"
        transfer.files.any { it.state == "READY" } -> "saving"
        else -> "done"
    }
}

private fun summarize(transfer: Transfer): String = when {
    transfer.text != null -> if (Regex("^https?://\\S+$").matches(transfer.text.trim())) "a link" else "a note"
    transfer.files.size == 1 -> transfer.files[0].name
    transfer.files.all { it.mimeType.startsWith("image/") } -> "${transfer.files.size} photos"
    transfer.files.all { it.mimeType.startsWith("video/") } -> "${transfer.files.size} videos"
    else -> "${transfer.files.size} files"
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.size - 1) {
        value /= 1024
        unit++
    }
    return if (value < 10) "%.1f %s".format(value, units[unit]) else "%.0f %s".format(value, units[unit])
}
