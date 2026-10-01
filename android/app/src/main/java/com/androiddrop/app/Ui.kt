package com.androiddrop.app

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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

private val Success = Color(0xFF3F8F5A)
private val Warning = Color(0xFFB7791F)

@Composable
fun DropTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

/** A background and the colour drawn on it, for light and dark. */
private class Tint(val lightBack: Long, val lightInk: Long, val darkBack: Long, val darkInk: Long) {
    fun back(dark: Boolean) = Color(if (dark) darkBack else lightBack)
    fun ink(dark: Boolean) = Color(if (dark) darkInk else lightInk)
}

/** A soft pastel per device (always the same for one device), so each is recognisable at a glance. */
private val PALETTE = listOf(
    Tint(0xFFFFD8C2, 0xFF8A4A24, 0xFF4A3024, 0xFFFFC9A8),
    Tint(0xFFD7E8FF, 0xFF2A4F86, 0xFF22324A, 0xFFB9D4FF),
    Tint(0xFFCDEBD8, 0xFF2F6B47, 0xFF213A2B, 0xFFA9DCBB),
    Tint(0xFFE3DCFF, 0xFF4B3D8F, 0xFF2E2848, 0xFFCFC3FF),
    Tint(0xFFFBE8B0, 0xFF7A5A12, 0xFF3D3218, 0xFFF2D98A),
    Tint(0xFFFBD3DC, 0xFF8A2E48, 0xFF45222C, 0xFFF5B8C8),
)
private val HOST_TINT = Tint(0xFFE7E0D4, 0xFF4A423A, 0xFF3A342D, 0xFFD8CFC1)

private class Section(val label: String, val hint: String, val icon: ImageVector, val tint: Tint)

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
private val SettingsIcon = strokeIcon(
    "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
    "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z",
)
private val ClipIcon = strokeIcon("m21.44 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l8.57-8.57A4 4 0 1 1 18 8.84l-8.59 8.57a2 2 0 0 1-2.83-2.83l8.49-8.48")
private val ArrowUpIcon = strokeIcon("M12 19V5M5 12l7-7 7 7")
private val FileIcon = strokeIcon("M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7z", "M14 2v4a2 2 0 0 0 2 2h4")
private val FolderIcon = strokeIcon("M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2z")

private val SECTIONS = listOf(
    Section("Nearby", "Tap a device to choose files for it.", NearbyIcon, Tint(0xFFD6EFDF, 0xFF2F6B47, 0xFF243D2D, 0xFFA9DCBB)),
    Section("Received", "Accepted files are saved on this phone.", ReceivedIcon, Tint(0xFFDBE9FD, 0xFF2A4F86, 0xFF26385A, 0xFFB9D4FF)),
    Section("Sent", "Everything you've shared, and whether it arrived.", SentIcon, Tint(0xFFFDE0CF, 0xFF8A4A24, 0xFF4D3325, 0xFFFFC9A8)),
)

private val CARD = RoundedCornerShape(20.dp)

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

/** The two overlapping tiles of the logo. */
@Composable
private fun Logo(size: Dp) {
    Box(modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.24f)).background(Color(0xFF2B2622))) {
        Box(Modifier.padding(start = size * 0.17f, top = size * 0.17f).size(size * 0.42f).clip(RoundedCornerShape(size * 0.125f)).background(Color(0xFFFFB68C)))
        Box(Modifier.padding(start = size * 0.41f, top = size * 0.41f).size(size * 0.42f).clip(RoundedCornerShape(size * 0.125f)).background(Color(0xFFA9CCFF)))
    }
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
            Spacer(Modifier.height(16.dp))
            Logo(44.dp)
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
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(6) },
                label = { Text("6-digit code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text("This phone's name") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
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

/** What the next tap on "send" is for: picked files, a typed note, or something shared in from another app. */
private sealed class Outgoing {
    data object PickFiles : Outgoing()
    class Note(val text: String) : Outgoing()
    class Shared(val share: Repo.Share) : Outgoing()
}

@Composable
private fun Home(repo: Repo, snackbar: SnackbarHostState) {
    val me by repo.me.collectAsStateWithLifecycle()
    val devices by repo.devices.collectAsStateWithLifecycle()
    val transfers by repo.transfers.collectAsStateWithLifecycle()
    val connected by repo.connected.collectAsStateWithLifecycle()
    val serverName by repo.serverName.collectAsStateWithLifecycle()
    val uploaded by repo.uploaded.collectAsStateWithLifecycle()
    val downloaded by repo.downloaded.collectAsStateWithLifecycle()
    val saved by repo.savedFiles.collectAsStateWithLifecycle()
    val share by repo.pendingShare.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var settings by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<String?>(null) }
    var choosing by remember { mutableStateOf<Outgoing?>(null) }
    var later by remember { mutableStateOf(setOf<String>()) }
    val dark = isSystemInDarkTheme()

    val meId = me?.id
    val peers = devices.filter { it.id != meId }
    val received = transfers.filter { it.recipientId == meId && it.isOpen }
    val sent = transfers.filter { it.senderId == meId }
    val waiting = received.filter { it.status == "PENDING" }
    val request = waiting.lastOrNull { it.id !in later }
    val card = TransferContext(repo, meId, devices, uploaded, downloaded, saved, dark)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val to = target
        if (to != null && uris.isNotEmpty()) {
            repo.sendFiles(to, uris)
            tab = 2
        }
    }
    val deliver: (String, Outgoing) -> Unit = { deviceId, what ->
        when (what) {
            Outgoing.PickFiles -> {
                target = deviceId
                picker.launch(arrayOf("*/*"))
            }
            is Outgoing.Note -> repo.sendText(deviceId, what.text)
            is Outgoing.Shared -> {
                if (what.share.text != null) repo.sendText(deviceId, what.share.text) else repo.sendFiles(deviceId, what.share.uris)
                repo.pendingShare.value = null
                tab = 2
            }
        }
        choosing = null
    }
    /** Straight to the only other device, or ask which one. */
    val sendTo: (Outgoing) -> Unit = { what ->
        when (peers.size) {
            0 -> Unit
            1 -> deliver(peers[0].id, what)
            else -> choosing = what
        }
    }

    // Something shared in from another app, once the devices are known.
    LaunchedEffect(share, peers.size) {
        val pending = share
        if (pending != null && peers.isNotEmpty()) sendTo(Outgoing.Shared(pending))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().height(60.dp).padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Logo(32.dp)
                Spacer(Modifier.width(10.dp))
                Text("AndroidDrop", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = { settings = true }) {
                    Icon(SettingsIcon, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                SECTIONS.forEachIndexed { index, section ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = {
                            BadgedBox(badge = { if (index == 1 && waiting.isNotEmpty()) Badge { Text("${waiting.size}") } }) {
                                Icon(section.icon, contentDescription = null)
                            }
                        },
                        label = { Text(section.label, fontSize = 13.sp, fontWeight = FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = section.tint.back(dark),
                            selectedIconColor = section.tint.ink(dark),
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
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    Text(SECTIONS[tab].label, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            !connected -> "Reconnecting to $serverName…"
                            tab == 1 -> "Accepted files are saved to ${repo.folderName(repo.saveFolder.collectAsStateWithLifecycle().value)}."
                            else -> SECTIONS[tab].hint
                        },
                        fontSize = 15.sp,
                        color = if (connected) MaterialTheme.colorScheme.onSurfaceVariant else Warning,
                    )
                }
            }
            when (tab) {
                0 -> {
                    item {
                        Button(onClick = { sendTo(Outgoing.PickFiles) }, enabled = peers.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Icon(ClipIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Choose files", fontSize = 16.sp)
                        }
                    }
                    item { Stage(peers, dark) { deliver(it.id, Outgoing.PickFiles) } }
                    val live = transfers.filter { it.isOpen && it.text == null && phaseOf(it) != "done" }
                    if (live.isNotEmpty()) {
                        item {
                            Panel("In progress") {
                                live.forEachIndexed { index, transfer ->
                                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    TransferRow(transfer, card)
                                }
                            }
                        }
                    }
                    item { Messages(transfers.filter { it.text != null && it.status != "CANCELLED" && it.status != "EXPIRED" }, card, peers.isNotEmpty()) { sendTo(Outgoing.Note(it)) } }
                }
                1 -> transferList(received, card, "Nothing received yet", "When a device shares something with you, you'll be asked to accept it.")
                else -> transferList(sent, card, "Nothing sent yet", "Pick a device on Nearby, or share to AndroidDrop from another app.")
            }
        }
    }

    request?.let { transfer ->
        RequestDialog(transfer, card, waiting.size, onLater = {
            later = later + transfer.id
            tab = 1
        })
    }

    choosing?.let { what ->
        AlertDialog(
            onDismissRequest = {
                choosing = null
                repo.pendingShare.value = null
            },
            title = { Text("Send to") },
            text = {
                Column {
                    peers.forEach { device ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { deliver(device.id, what) }.padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(device.id, device.host, device.platform, 44.dp, dark)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(device.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Text(if (device.online) "Nearby" else "Away", fontSize = 14.sp, color = if (device.online) Success else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    choosing = null
                    repo.pendingShare.value = null
                }) { Text("Cancel") }
            },
        )
    }

    if (settings) SettingsSheet(repo, me, serverName, connected) { settings = false }
}

/** What every transfer row needs to know. */
private class TransferContext(
    val repo: Repo,
    val meId: String?,
    val devices: List<Device>,
    val uploaded: Map<String, Long>,
    val downloaded: Map<String, Long>,
    val saved: Map<String, Uri>,
    val dark: Boolean,
)

private fun androidx.compose.foundation.lazy.LazyListScope.transferList(list: List<Transfer>, context: TransferContext, emptyTitle: String, emptyDetail: String) {
    if (list.isEmpty()) {
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(emptyTitle, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text(emptyDetail, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        return
    }
    items(list, key = { it.id }) { transfer ->
        Surface(shape = CARD, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
            TransferRow(transfer, context)
        }
    }
}

/** A card with its title inside, like the panels on the computer. */
@Composable
private fun Panel(title: String, content: @Composable () -> Unit) {
    Surface(shape = CARD, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

@Composable
private fun Avatar(id: String, host: Boolean, platform: String, size: Dp, dark: Boolean, online: Boolean? = null) {
    val tint = if (host) HOST_TINT else PALETTE[(id.fold(0) { hash, char -> hash * 31 + char.code } and 0x7FFFFFFF) % PALETTE.size]
    Box(modifier = Modifier.size(size)) {
        Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(tint.back(dark)), contentAlignment = Alignment.Center) {
            Icon(if (host || platform == "MAC") LaptopIcon else PhoneIcon, contentDescription = null, tint = tint.ink(dark), modifier = Modifier.size(size * 0.46f))
        }
        if (online != null) {
            Box(
                modifier = Modifier.align(Alignment.BottomEnd).size(size * 0.24f).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface).padding(2.5.dp).clip(CircleShape)
                    .background(if (online) Success else MaterialTheme.colorScheme.outline),
            )
        }
    }
}

/** The devices in range as large round avatars, like AirDrop. Tap one to send it files. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Stage(peers: List<Device>, dark: Boolean, onPick: (Device) -> Unit) {
    Surface(shape = CARD, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 28.dp, horizontal = 12.dp)) {
            if (peers.isEmpty()) {
                Text("No other devices yet", fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text("Devices paired with your computer show up here.", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    peers.forEach { device ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(120.dp).clip(RoundedCornerShape(18.dp)).clickable { onPick(device) }.padding(8.dp),
                        ) {
                            Avatar(device.id, device.host, device.platform, 76.dp, dark, device.online)
                            Spacer(Modifier.height(10.dp))
                            Text(device.name, fontSize = 15.5.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
                            Text(if (device.online) "Nearby" else "Away", fontSize = 14.sp, color = if (device.online) Success else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                val online = peers.count { it.online }
                Text(
                    if (online == 0) "No devices are open right now. They'll get your files when they open AndroidDrop." else "$online of ${peers.size} ${if (peers.size == 1) "device is" else "devices are"} nearby",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Notes and links in both directions, oldest first like a chat, with a box to write one. */
@Composable
private fun Messages(notes: List<Transfer>, context: TransferContext, canSend: Boolean, onSend: (String) -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val links = LocalUriHandler.current
    Panel("Messages") {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (notes.isEmpty()) {
                Text("No messages yet", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), textAlign = TextAlign.Center)
            }
            notes.sortedBy { it.createdAt }.forEach { note ->
                val mine = note.senderId == context.meId
                val text = note.text.orEmpty()
                val pending = note.status == "PENDING"
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                    Text(
                        text,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(20.dp))
                            .background(if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { if (isLink(text)) runCatching { links.openUri(text.trim()) } else clipboard.setText(AnnotatedString(text)) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                    if (mine) {
                        Text(if (pending) "Waiting" else if (note.status == "DECLINED") "Declined" else "Delivered", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp, end = 6.dp))
                    } else if (pending) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                            Button(onClick = { acceptNote(note, context.repo, clipboard, links) }, modifier = Modifier.height(40.dp)) { Text(if (isLink(text)) "Open" else "Accept") }
                            TextButton(onClick = { context.repo.decline(note) }, modifier = Modifier.height(40.dp)) { Text("Decline") }
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Write a note or paste a link") },
                shape = RoundedCornerShape(22.dp),
                maxLines = 5,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    onSend(draft.trim())
                    draft = ""
                },
                enabled = canSend && draft.isNotBlank(),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.padding(bottom = 4.dp).size(48.dp),
            ) {
                Icon(ArrowUpIcon, contentDescription = "Send", modifier = Modifier.size(22.dp))
            }
        }
    }
}

private fun isLink(text: String) = Regex("^https?://\\S+$").matches(text.trim())

/** Like AirDrop: an accepted link opens, an accepted note is copied. */
private fun acceptNote(note: Transfer, repo: Repo, clipboard: androidx.compose.ui.platform.ClipboardManager, links: androidx.compose.ui.platform.UriHandler) {
    val text = note.text ?: return
    if (isLink(text)) runCatching { links.openUri(text.trim()) } else clipboard.setText(AnnotatedString(text))
    repo.accept(note)
}

/** "Pixel would like to share 3 photos", with Decline and Accept. Closing it leaves the request in Received. */
@Composable
private fun RequestDialog(transfer: Transfer, context: TransferContext, waiting: Int, onLater: () -> Unit) {
    val sender = context.devices.firstOrNull { it.id == transfer.senderId }
    val clipboard = LocalClipboardManager.current
    val links = LocalUriHandler.current
    Dialog(onDismissRequest = onLater) {
        Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(22.dp)) {
                Avatar(transfer.senderId, sender?.host ?: false, sender?.platform ?: "OTHER", 64.dp, context.dark)
                Spacer(Modifier.height(12.dp))
                Text(sender?.name ?: transfer.senderName, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Text("would like to share ${summarize(transfer)}", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(horizontal = 14.dp, vertical = 6.dp)) {
                    transfer.text?.let { Text(it, fontSize = 15.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 8.dp)) }
                    transfer.files.take(3).forEach { file ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(file.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(10.dp))
                            Text(formatBytes(file.size), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (transfer.files.size > 3) Text("and ${transfer.files.size - 3} more", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (transfer.text != null) (if (isLink(transfer.text)) "Accepting opens the link." else "Accepting copies the note.")
                    else "${formatBytes(transfer.totalSize)} · Saved to ${context.repo.folderName(context.repo.saveFolder.value)}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = { context.repo.decline(transfer) }, modifier = Modifier.weight(1f).height(50.dp)) { Text("Decline", fontSize = 16.sp) }
                    Button(
                        onClick = { if (transfer.text != null) acceptNote(transfer, context.repo, clipboard, links) else context.repo.accept(transfer) },
                        modifier = Modifier.weight(1f).height(50.dp),
                    ) { Text("Accept", fontSize = 16.sp) }
                }
                if (waiting > 1) Text("${waiting - 1} more waiting", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, dot: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TransferRow(transfer: Transfer, context: TransferContext) {
    val repo = context.repo
    val incoming = transfer.recipientId == context.meId
    val peerId = if (incoming) transfer.senderId else transfer.recipientId
    val peer = context.devices.firstOrNull { it.id == peerId }
    val peerName = peer?.name ?: if (incoming) transfer.senderName else transfer.recipientName
    val phase = phaseOf(transfer)
    val total = transfer.totalSize.coerceAtLeast(1)
    val sentBytes = transfer.files.sumOf { file ->
        when (file.state) {
            "UPLOADING" -> minOf(file.size, context.uploaded[file.id] ?: 0L)
            "FAILED", "REMOVED" -> 0L
            else -> file.size
        }
    }
    val savedBytes = transfer.files.sumOf { file ->
        when (file.state) {
            "DELIVERED" -> file.size
            "READY" -> minOf(file.size, context.downloaded[file.id] ?: 0L)
            else -> 0L
        }
    }
    val fraction = (if (phase == "saving") savedBytes else sentBytes).toFloat() / total
    val percent = (fraction * 100).toInt()
    val accent = MaterialTheme.colorScheme.primary
    val (status, dot) = when (phase) {
        "waiting" -> if (incoming) "Awaiting your response" to accent else "Waiting for $peerName" to Warning
        "transferring" -> "${if (incoming) "Receiving" else "Sending"} · $percent%" to accent
        "saving" -> (if (incoming) "Saving · $percent%" else "$peerName is downloading · $percent%") to accent
        "done" -> when {
            transfer.text != null -> (if (incoming) "Accepted" else "Delivered") to Success
            incoming -> "Saved to ${repo.folderName(repo.saveFolder.value)}" to Success
            else -> "Delivered" to Success
        }
        "failed" -> if (incoming) "Interrupted" to Warning else "Upload failed" to MaterialTheme.colorScheme.error
        "declined" -> "Declined" to MaterialTheme.colorScheme.error
        "cancelled" -> "Cancelled" to MaterialTheme.colorScheme.outline
        else -> "Expired" to MaterialTheme.colorScheme.outline
    }
    val what = summarize(transfer)
    val clipboard = LocalClipboardManager.current
    val links = LocalUriHandler.current
    val android = LocalContext.current

    Row(modifier = Modifier.padding(16.dp)) {
        Avatar(peerId, peer?.host ?: false, peer?.platform ?: "OTHER", 46.dp, context.dark)
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    if (incoming) "$peerName ${if (phase == "waiting") "wants to share" else "sent"} $what" else "${what.replaceFirstChar(Char::uppercase)} to $peerName",
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusLine(if (transfer.files.isEmpty()) status else "$status · ${formatBytes(transfer.totalSize)}", dot)
            }
            if (phase == "transferring" || phase == "saving") {
                LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
            }
            transfer.text?.let { text ->
                Text(
                    text,
                    fontSize = 15.5.sp,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(12.dp),
                )
            }

            // Photos as a grid of previews, everything else as rows. Tap a saved file to open it.
            val photos = transfer.files.filter { it.thumbnail }
            val others = transfer.files.filter { !it.thumbnail }
            val open: (TransferFile) -> Unit = { file ->
                context.saved[file.id]?.let { uri ->
                    val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, file.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching { android.startActivity(view) }.onFailure { repo.notices.tryEmit("No app on this phone can open ${file.name}.") }
                }
            }
            photos.take(8).chunked(4).forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    line.forEach { file ->
                        Preview(repo, transfer.id, file.id, Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp)).clickable { open(file) })
                    }
                    repeat(4 - line.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (photos.size > 8) Text("and ${photos.size - 8} more photos", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            others.take(4).forEach { file ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                        .clickable(enabled = context.saved.containsKey(file.id)) { open(file) }.padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Icon(FileIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(file.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${formatBytes(file.size)} · ${fileState(file, incoming, context.saved.containsKey(file.id))}",
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (others.size > 4) Text("and ${others.size - 4} more", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                when {
                    phase == "waiting" && incoming -> {
                        FilledTonalButton(onClick = { repo.decline(transfer) }, modifier = Modifier.weight(1f).height(48.dp)) { Text("Decline", fontSize = 15.sp) }
                        Button(
                            onClick = { if (transfer.text != null) acceptNote(transfer, repo, clipboard, links) else repo.accept(transfer) },
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) { Text("Accept", fontSize = 15.sp) }
                    }
                    !incoming && (phase == "waiting" || phase == "transferring" || phase == "saving" || phase == "failed") ->
                        FilledTonalButton(onClick = { repo.cancel(transfer) }, modifier = Modifier.height(44.dp)) { Text("Cancel") }
                    else -> TextButton(onClick = { repo.remove(transfer) }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text("Remove from history", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun fileState(file: TransferFile, incoming: Boolean, saved: Boolean): String = when (file.state) {
    "UPLOADING" -> if (incoming) "Receiving" else "Sending"
    "READY" -> if (incoming) "Saving…" else "Uploaded"
    "SAVED" -> "Saved"
    "DELIVERED" -> if (!incoming) "Delivered" else if (saved) "Saved · Tap to open" else "Saved"
    "FAILED" -> if (incoming) "Interrupted" else "Upload failed"
    else -> "No longer available"
}

/** A photo's small preview from the computer. */
@Composable
private fun Preview(repo: Repo, transferId: String, fileId: String, modifier: Modifier) {
    val image by produceState<ImageBitmap?>(initialValue = null, fileId) {
        value = withContext(Dispatchers.IO) { runCatching { repo.preview(transferId, fileId) }.getOrNull() }
    }
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        image?.let { Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

// ---------- Settings ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(repo: Repo, me: Device?, serverName: String, connected: Boolean, onClose: () -> Unit) {
    val autoAccept by repo.autoAccept.collectAsStateWithLifecycle()
    val folder by repo.saveFolder.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf(false) }
    var unpairing by remember { mutableStateOf(false) }
    val dark = isSystemInDarkTheme()
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) repo.setSaveFolder(uri) }

    ModalBottomSheet(onDismissRequest = onClose, containerColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold)

            Group("This device") {
                SettingRow(
                    title = me?.name ?: "This phone",
                    detail = "Android · Connected to $serverName",
                    leading = { Avatar(me?.id.orEmpty(), false, "ANDROID", 44.dp, dark) },
                ) { FilledTonalButton(onClick = { renaming = true }, modifier = Modifier.height(40.dp)) { Text("Rename") } }
            }

            Group("Receiving") {
                SettingRow(title = "Accept automatically", detail = "Skip the Accept / Decline prompt for your paired devices.") {
                    Switch(checked = autoAccept, onCheckedChange = { repo.setAutoAccept(it) })
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(
                    title = "Save files to",
                    detail = repo.folderName(folder),
                    leading = { Icon(FolderIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp)) },
                ) { FilledTonalButton(onClick = { folderPicker.launch(null) }, modifier = Modifier.height(40.dp)) { Text("Change") } }
                if (folder != null) {
                    TextButton(onClick = { repo.setSaveFolder(null) }, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)) { Text("Use Downloads/AndroidDrop again") }
                }
            }

            Group("Connection") {
                SettingRow(title = if (connected) "Connected" else "Reconnecting…", detail = "$serverName · ${repo.lastAddress}") {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (connected) Success else Warning))
                }
            }

            TextButton(onClick = { unpairing = true }, modifier = Modifier.height(48.dp)) { Text("Unpair this device", color = MaterialTheme.colorScheme.error, fontSize = 15.sp) }
        }
    }

    if (renaming) {
        var name by remember { mutableStateOf(me?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename this device") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, singleLine = true, shape = RoundedCornerShape(14.dp), label = { Text("The name other devices see") }) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    repo.rename(name)
                    renaming = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
    if (unpairing) {
        AlertDialog(
            onDismissRequest = { unpairing = false },
            title = { Text("Unpair this device?") },
            text = { Text("You'll need a new code from $serverName to connect again.") },
            confirmButton = {
                TextButton(onClick = {
                    unpairing = false
                    onClose()
                    repo.unpair()
                }) { Text("Unpair", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { unpairing = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingRow(title: String, detail: String, leading: (@Composable () -> Unit)? = null, trailing: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, fontSize = 14.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        trailing()
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
    transfer.text != null -> if (isLink(transfer.text)) "a link" else "a note"
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
