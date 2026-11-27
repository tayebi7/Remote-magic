package com.remotemagic.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.remotemagic.app.R
import com.remotemagic.app.domain.ConnectionState
import com.remotemagic.app.domain.ConnectionStatus
import com.remotemagic.app.domain.RemoteKey
import com.remotemagic.app.domain.TvApp
import com.remotemagic.app.remote.RemoteViewModel
import com.remotemagic.app.settings.AppSettings
import com.remotemagic.app.settings.RemoteSection
import com.remotemagic.app.utils.Feedback

private fun appIcon(app: TvApp): Pair<ImageVector, Color> = when (app) {
    TvApp.YOUTUBE -> Icons.Filled.SmartDisplay to Color(0xFFFF4D4D)
    TvApp.NETFLIX -> Icons.Filled.Theaters to Color(0xFFE5484D)
    TvApp.PRIME -> Icons.Filled.VideoLibrary to Color(0xFF35B7FF)
    TvApp.DISNEY -> Icons.Filled.AutoAwesome to Color(0xFF8AA8FF)
    TvApp.IPTV -> Icons.Filled.LiveTv to Color(0xFF4C8DFF)
    TvApp.MOVIES -> Icons.Filled.Movie to Color(0xFFFF7A59)
    TvApp.MUSIC -> Icons.Filled.MusicNote to Color(0xFFFF5C8A)
    TvApp.BROWSER -> Icons.Filled.Public to Color(0xFF5BC0EB)
}

@Composable
fun AppGrid(onOpen: (TvApp) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TvApp.entries.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { app ->
                    val (icon, tint) = appIcon(app)
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onOpen(app) }.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(icon, null, tint = tint, modifier = Modifier.size(32.dp))
                        Text(app.title, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun RemoteScreen(
    model: RemoteViewModel,
    connection: ConnectionState,
    settings: AppSettings,
    onSearch: () -> Unit
) {
    val context = LocalContext.current
    val feedback = remember { Feedback(context) }
    DisposableEffect(Unit) { onDispose { feedback.release() } }
    val voice by model.voice.state.collectAsStateWithLifecycle()
    val language = LocalConfiguration.current.locales[0].toLanguageTag()
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            model.listen(language)
        } else {
            model.message.value = R.string.permission_denied
        }
        Unit
    }
    val connected = connection.status == ConnectionStatus.CONNECTED

    fun click() = feedback.press(settings.vibrate, settings.sounds)

    @Composable
    fun Key(key: RemoteKey, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
        // Buttons the connected protocol cannot send are dimmed.
        val enabled = !connected || model.supports(key)
        Box(
            modifier.alpha(if (enabled) 1f else 0.3f).clickable { click(); model.press(key) },
            contentAlignment = Alignment.Center
        ) { content() }
    }

    @Composable
    fun RoundKey(key: RemoteKey, icon: ImageVector, size: Int = 46) = Key(
        key, Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
    ) { Icon(icon, key.name, tint = MaterialTheme.colorScheme.secondary) }

    @Composable
    fun Pill(top: RemoteKey, topText: String, bottom: RemoteKey, bottomText: String, middle: @Composable () -> Unit) {
        Column(
            Modifier.width(64.dp).height(210.dp).border(2.dp, Electric, RoundedCornerShape(32.dp))
                .clip(RoundedCornerShape(32.dp)).background(MaterialTheme.colorScheme.surface),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Key(top, Modifier.fillMaxWidth().height(70.dp)) { Text(topText, fontSize = 20.sp, color = MaterialTheme.colorScheme.secondary) }
            middle()
            Key(bottom, Modifier.fillMaxWidth().height(70.dp)) { Text(bottomText, fontSize = 20.sp, color = MaterialTheme.colorScheme.secondary) }
        }
    }

    // A remote control keeps the same physical layout in Arabic.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // The search / connection button.
                AssistChip(
                    onClick = { click(); onSearch() },
                    label = {
                        Text(
                            if (connected) "● " + stringResource(R.string.connected) + " • " +
                                (if (connection.device?.protocol?.wifi == true) "Wi-Fi" else "Bluetooth")
                            else stringResource(R.string.search),
                            color = if (connected) Color(0xFF4ADE80) else Electric, fontSize = 12.sp
                        )
                    },
                    leadingIcon = { if (!connected) Icon(Icons.Filled.Search, null, tint = Electric) }
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "REMOTE\nMAGIC", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center, lineHeight = 20.sp, fontSize = 20.sp
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(54.dp).clip(CircleShape).background(Color(0xFF8E1B24))
                        .clickable { click(); model.power() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.PowerSettingsNew, "Power", tint = Color.White) }
            }

            if (voice.listening) {
                Text(stringResource(R.string.listening), color = Electric)
                LinearProgressIndicator(progress = { voice.level }, modifier = Modifier.fillMaxWidth())
            } else if (voice.heard.isNotEmpty()) {
                Text("“${voice.heard}”", color = Electric)
            }

            settings.order.forEach { section ->
                when (section) {
                    RemoteSection.APPS -> AppGrid { click(); model.open(it) }
                    RemoteSection.NAVIGATION -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        RoundKey(RemoteKey.HOME, Icons.Filled.Home)
                        RoundKey(RemoteKey.BACK, Icons.AutoMirrored.Filled.ArrowBack)
                        RoundKey(RemoteKey.MENU, Icons.Filled.Menu)
                        RoundKey(RemoteKey.SETTINGS, Icons.Filled.Settings)
                        RoundKey(RemoteKey.SOURCE, Icons.AutoMirrored.Filled.Input)
                        RoundKey(RemoteKey.EXIT, Icons.Filled.Close)
                    }
                    RemoteSection.DPAD -> Row(
                        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Pill(RemoteKey.VOLUME_UP, "+", RemoteKey.VOLUME_DOWN, "−") {
                            Key(RemoteKey.MUTE, Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                Text(stringResource(R.string.mute), fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                        Box(
                            Modifier.size(200.dp).border(3.dp, Electric, CircleShape).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            val arrow = MaterialTheme.colorScheme.secondary
                            Key(RemoteKey.UP, Modifier.align(Alignment.TopCenter).size(64.dp)) { Icon(Icons.Filled.KeyboardArrowUp, "Up", tint = arrow) }
                            Key(RemoteKey.DOWN, Modifier.align(Alignment.BottomCenter).size(64.dp)) { Icon(Icons.Filled.KeyboardArrowDown, "Down", tint = arrow) }
                            Key(RemoteKey.LEFT, Modifier.align(Alignment.CenterStart).size(64.dp)) { Icon(Icons.Filled.KeyboardArrowLeft, "Left", tint = arrow) }
                            Key(RemoteKey.RIGHT, Modifier.align(Alignment.CenterEnd).size(64.dp)) { Icon(Icons.Filled.KeyboardArrowRight, "Right", tint = arrow) }
                            Key(RemoteKey.OK, Modifier.align(Alignment.Center).size(72.dp).clip(CircleShape).background(Gold)) {
                                Text("OK", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF3A2A00))
                            }
                        }
                        Pill(RemoteKey.CHANNEL_UP, "CH+", RemoteKey.CHANNEL_DOWN, "CH−") {}
                    }
                    RemoteSection.NUMBERS -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9), listOf(0)).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                                row.forEach { n ->
                                    Key(
                                        RemoteKey.digit(n),
                                        Modifier.width(100.dp).height(50.dp).border(1.dp, Gold, RoundedCornerShape(12.dp))
                                            .clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)
                                    ) { Text("$n", fontSize = 24.sp) }
                                }
                            }
                        }
                    }
                    RemoteSection.MEDIA -> Row(
                        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RoundKey(RemoteKey.REWIND, Icons.Filled.FastRewind, 54)
                        Box(
                            Modifier.size(76.dp).border(3.dp, Gold, CircleShape).clip(CircleShape).background(Color(0xFF0B76B7))
                                .clickable { click(); microphone.launch(Manifest.permission.RECORD_AUDIO) },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.Mic, "Microphone", tint = Gold, modifier = Modifier.size(38.dp)) }
                        RoundKey(RemoteKey.PLAY_PAUSE, Icons.Filled.PlayArrow, 54)
                        RoundKey(RemoteKey.FAST_FORWARD, Icons.Filled.FastForward, 54)
                    }
                }
            }
        }
    }
}
