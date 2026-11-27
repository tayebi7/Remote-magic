package com.remotemagic.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.remotemagic.app.R
import com.remotemagic.app.domain.ConnectionState
import com.remotemagic.app.domain.ConnectionStatus
import com.remotemagic.app.domain.PowerState
import com.remotemagic.app.domain.RemoteKey
import com.remotemagic.app.domain.TvApp
import com.remotemagic.app.remote.RemoteViewModel
import com.remotemagic.app.settings.AppSettings
import com.remotemagic.app.settings.RemoteSection
import com.remotemagic.app.utils.Feedback

/** Opens the system screen-mirroring page. Returns false when the phone has none. */
fun openScreenMirroring(context: Context): Boolean =
    listOf(Settings.ACTION_CAST_SETTINGS, "android.settings.WIFI_DISPLAY_SETTINGS").any {
        runCatching { context.startActivity(Intent(it)) }.isSuccess
    }

private fun appIcon(app: TvApp): Pair<ImageVector, Color> = when (app) {
    TvApp.YOUTUBE -> Icons.Filled.SmartDisplay to Color(0xFFFF4D4D)
    TvApp.IPTV -> Icons.Filled.LiveTv to Color(0xFF6EA8FF)
    TvApp.RADIO -> Icons.Filled.Radio to Color(0xFFE7C173)
}

/** Card button with an icon and a one-line label that never gets cut mid-word. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(
    icon: ImageVector,
    label: String,
    modifier: Modifier,
    tint: Color = MaterialTheme.colorScheme.secondary,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
        Text(label, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun AppRow(onOpen: (TvApp) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TvApp.entries.forEach { app ->
            val (icon, tint) = appIcon(app)
            Tile(icon, app.title, Modifier.weight(1f), tint) { onOpen(app) }
        }
    }
}

@Composable
fun RemoteScreen(
    model: RemoteViewModel,
    connection: ConnectionState,
    settings: AppSettings,
    onSearch: () -> Unit,
    onMouse: () -> Unit,
    onKeyboard: () -> Unit,
    onShowNumbers: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val feedback = remember { Feedback(context) }
    DisposableEffect(Unit) { onDispose { feedback.release() } }
    val voice by model.voice.state.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val language = configuration.locales[0].toLanguageTag()
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            model.listen(language)
        } else {
            model.message.value = R.string.permission_denied
        }
        Unit
    }
    val connected = connection.status == ConnectionStatus.CONNECTED
    val rtl = LocalLayoutDirection.current

    fun click() = feedback.press(settings.vibrate, settings.sounds)

    @Composable
    fun Key(key: RemoteKey, modifier: Modifier, content: @Composable () -> Unit) {
        // Buttons the connected protocol cannot send are dimmed.
        val enabled = !connected || model.supports(key)
        Box(
            modifier.alpha(if (enabled) 1f else 0.3f).clickable { click(); model.press(key) },
            contentAlignment = Alignment.Center
        ) { content() }
    }

    @Composable
    fun CardKey(key: RemoteKey, modifier: Modifier, icon: ImageVector? = null, label: String? = null) {
        Key(key, modifier.height(52.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (icon != null) Icon(icon, key.name, tint = MaterialTheme.colorScheme.secondary)
            else Text(label.orEmpty(), fontSize = 17.sp, maxLines = 1)
        }
    }

    @Composable
    fun Line(content: @Composable RowScope.() -> Unit) =
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)

    @Composable
    fun Header() {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // The search / connection button.
            AssistChip(
                onClick = { click(); onSearch() },
                label = {
                    Text(
                        stringResource(if (connected) R.string.connected else R.string.search),
                        color = if (connected) Color(0xFF4ADE80) else Electric, fontSize = 12.sp, maxLines = 1
                    )
                }
            )
            Spacer(Modifier.weight(1f))
            Text("REMOTE MAGIC", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Black, fontSize = 20.sp, maxLines = 1)
        }
        connection.device?.let { device ->
            val state = when {
                connection.power == PowerState.STANDBY -> R.string.standby
                else -> statusText(connection.status)
            }
            Text(
                "${device.name} · ${if (device.protocol.wifi) "Wi-Fi" else "Bluetooth"} · ${stringResource(state)}",
                fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (voice.listening) {
            Text(stringResource(R.string.listening), color = Electric)
            LinearProgressIndicator(progress = { voice.level }, modifier = Modifier.fillMaxWidth())
        }
    }

    @Composable
    fun DPad(size: Dp) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                val arrow = size / 3.4f
                val tint = MaterialTheme.colorScheme.onSurface
                Key(RemoteKey.UP, Modifier.align(Alignment.TopCenter).size(arrow)) { Icon(Icons.Filled.KeyboardArrowUp, "Up", tint = tint) }
                Key(RemoteKey.DOWN, Modifier.align(Alignment.BottomCenter).size(arrow)) { Icon(Icons.Filled.KeyboardArrowDown, "Down", tint = tint) }
                Key(RemoteKey.LEFT, Modifier.align(Alignment.CenterStart).size(arrow)) { Icon(Icons.Filled.KeyboardArrowLeft, "Left", tint = tint) }
                Key(RemoteKey.RIGHT, Modifier.align(Alignment.CenterEnd).size(arrow)) { Icon(Icons.Filled.KeyboardArrowRight, "Right", tint = tint) }
                Key(RemoteKey.OK, Modifier.align(Alignment.Center).size(size / 3.2f).clip(CircleShape).background(Gold)) {
                    Text("OK", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF14213D))
                }
            }
        }
    }

    @Composable
    fun Section(section: RemoteSection, width: Dp) {
        when (section) {
            RemoteSection.TOP -> Line {
                Tile(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), Modifier.weight(1f)) { click(); model.press(RemoteKey.BACK) }
                Tile(Icons.Filled.Home, stringResource(R.string.home), Modifier.weight(1f)) { click(); model.press(RemoteKey.HOME) }
                Tile(Icons.Filled.PowerSettingsNew, stringResource(R.string.power), Modifier.weight(1f)) { click(); model.power() }
            }
            RemoteSection.DPAD -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DPad(min(width * 0.68f, 250.dp))
            }
            RemoteSection.TOOLS -> Line {
                Tile(Icons.Filled.ScreenShare, stringResource(R.string.share_screen), Modifier.weight(1f)) {
                    click()
                    if (!openScreenMirroring(context)) model.message.value = R.string.no_mirroring
                }
                // One button: tap for a voice command, hold to turn the phone into an air mouse.
                Tile(
                    Icons.Filled.Mic, stringResource(R.string.voice_mouse), Modifier.weight(1f),
                    onLongClick = { click(); onMouse() }
                ) { click(); microphone.launch(Manifest.permission.RECORD_AUDIO) }
                Tile(Icons.Filled.Keyboard, stringResource(R.string.keyboard), Modifier.weight(1f)) { click(); onKeyboard() }
            }
            RemoteSection.VOLUME -> CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Line {
                        CardKey(RemoteKey.VOLUME_DOWN, Modifier.weight(1f), Icons.Filled.VolumeDown)
                        CardKey(RemoteKey.MUTE, Modifier.weight(1f), Icons.Filled.VolumeOff)
                        CardKey(RemoteKey.VOLUME_UP, Modifier.weight(1f), Icons.Filled.VolumeUp)
                    }
                    Line {
                        CardKey(RemoteKey.CHANNEL_DOWN, Modifier.weight(1f), label = "CH −")
                        CardKey(RemoteKey.CHANNEL_UP, Modifier.weight(1f), label = "CH +")
                    }
                    Line {
                        CardKey(RemoteKey.MENU, Modifier.weight(1f), Icons.Filled.Menu)
                        CardKey(RemoteKey.SETTINGS, Modifier.weight(1f), Icons.Filled.Settings)
                        CardKey(RemoteKey.SOURCE, Modifier.weight(1f), Icons.AutoMirrored.Filled.Input)
                        CardKey(RemoteKey.EXIT, Modifier.weight(1f), Icons.Filled.Close)
                    }
                }
            }
            RemoteSection.MEDIA -> CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Line {
                    CardKey(RemoteKey.REWIND, Modifier.weight(1f), Icons.Filled.FastRewind)
                    CardKey(RemoteKey.PLAY_PAUSE, Modifier.weight(1f), label = "▶ ❚❚")
                    CardKey(RemoteKey.STOP, Modifier.weight(1f), Icons.Filled.Stop)
                    CardKey(RemoteKey.FAST_FORWARD, Modifier.weight(1f), Icons.Filled.FastForward)
                }
            }
            RemoteSection.NUMBERS -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onShowNumbers(!settings.showNumbers) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (settings.showNumbers) R.string.hide_numbers else R.string.show_numbers))
                }
                if (settings.showNumbers) CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
                            Line { row.forEach { CardKey(RemoteKey.digit(it), Modifier.weight(1f), label = "$it") } }
                        }
                        Line {
                            CardKey(RemoteKey.STAR, Modifier.weight(1f), label = "*")
                            CardKey(RemoteKey.NUM_0, Modifier.weight(1f), label = "0")
                            CardKey(RemoteKey.HASH, Modifier.weight(1f), label = "#")
                        }
                        Line { CardKey(RemoteKey.ENTER, Modifier.weight(1f), label = "Enter") }
                    }
                }
            }
            RemoteSection.APPS -> AppRow { click(); model.open(it) }
        }
    }

    @Composable
    fun SectionColumn(sections: List<RemoteSection>, modifier: Modifier, header: Boolean) {
        BoxWithConstraints(modifier) {
            val width = maxWidth
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (header) CompositionLocalProvider(LocalLayoutDirection provides rtl) { Header() }
                sections.forEach { Section(it, width) }
            }
        }
    }

    // Landscape shows two scrollable columns; portrait one, kept narrow enough for one hand.
    if (configuration.screenWidthDp > configuration.screenHeightDp) {
        val half = (settings.order.size + 1) / 2
        Row(Modifier.fillMaxSize()) {
            SectionColumn(settings.order.take(half), Modifier.weight(1f), true)
            SectionColumn(settings.order.drop(half), Modifier.weight(1f), false)
        }
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            SectionColumn(settings.order, Modifier.widthIn(max = 480.dp), true)
        }
    }
}
