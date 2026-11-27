package com.remotemagic.app.ui

import android.Manifest
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.remotemagic.app.R
import com.remotemagic.app.domain.TvApp
import com.remotemagic.app.domain.ConnectionState
import com.remotemagic.app.domain.Protocol
import com.remotemagic.app.remote.RemoteViewModel
import com.remotemagic.app.settings.RemoteSection
import com.remotemagic.app.settings.SettingsViewModel
import com.remotemagic.app.settings.ThemeMode

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp), content = content
)

@Composable
fun VoiceScreen(model: RemoteViewModel) {
    val state by model.voice.state.collectAsStateWithLifecycle()
    val language = LocalConfiguration.current.locales[0].toLanguageTag()
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            model.listen(language)
        } else {
            model.message.value = R.string.permission_denied
        }
        Unit
    }
    Page {
        FilledIconButton(
            onClick = { microphone.launch(Manifest.permission.RECORD_AUDIO) },
            modifier = Modifier.size(120.dp).align(Alignment.CenterHorizontally)
        ) { Icon(Icons.Filled.Mic, stringResource(R.string.voice), Modifier.size(64.dp)) }
        Text(stringResource(if (state.listening) R.string.listening else R.string.tap_mic))
        if (state.listening) LinearProgressIndicator(progress = { state.level }, modifier = Modifier.fillMaxWidth())
        if (state.heard.isNotEmpty()) Text("“${state.heard}”", style = MaterialTheme.typography.titleLarge)
        if (state.errorCode != null) {
            Text(
                stringResource(if (model.voice.available) R.string.voice_error else R.string.voice_unavailable),
                color = MaterialTheme.colorScheme.error
            )
        }
        Text(stringResource(R.string.voice_examples))
    }
}

@Composable
fun AppsScreen(model: RemoteViewModel, connection: ConnectionState) {
    val shortcuts by model.shortcuts.collectAsStateWithLifecycle()
    Page {
        AppRow { model.open(it) }
        val protocol = connection.device?.protocol
        if (protocol == null || protocol == Protocol.BLUETOOTH_HID) {
            Text(stringResource(if (protocol == null) R.string.not_connected else R.string.unsupported))
            return@Page
        }
        Text(stringResource(R.string.shortcut_note), style = MaterialTheme.typography.bodySmall)
        TvApp.entries.forEach { app ->
            val current = shortcuts.firstOrNull { it.protocol == protocol.name && it.label == app.title }?.target.orEmpty()
            var target by remember(app, protocol, current) { mutableStateOf(current) }
            OutlinedTextField(
                target, { target = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("${app.title} – ${stringResource(R.string.shortcut_target)}") }
            )
            TextButton(onClick = { model.setAppId(app, protocol, target) }) { Text(stringResource(R.string.save)) }
        }
    }
}

@Composable
fun LanguageScreen() = Page {
    listOf("ar" to "العربية", "fr" to "Français", "en" to "English").forEach { (tag, name) ->
        OutlinedButton(
            onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text(name) }
    }
}

@Composable
fun ThemeScreen(model: SettingsViewModel) {
    val settings by model.settings.collectAsStateWithLifecycle()
    Page {
        listOf(ThemeMode.DARK to R.string.dark, ThemeMode.LIGHT to R.string.light, ThemeMode.SYSTEM to R.string.system)
            .forEach { (mode, title) ->
                Row(Modifier.fillMaxWidth().clickable { model.setTheme(mode) }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(settings.theme == mode, onClick = { model.setTheme(mode) })
                    Text(stringResource(title))
                }
            }
    }
}

@Composable
fun SettingsScreen(
    model: SettingsViewModel,
    connection: ConnectionViewModel,
    onLanguage: () -> Unit,
    onTheme: () -> Unit,
    onDevices: () -> Unit
) {
    val settings by model.settings.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<(() -> Unit)?>(null) }

    @Composable
    fun Toggle(title: Int, value: Boolean, change: (Boolean) -> Unit) =
        ListItem(headlineContent = { Text(stringResource(title)) }, trailingContent = { Switch(value, change) })

    Page {
        OutlinedButton(onClick = onLanguage, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.language)) }
        OutlinedButton(onClick = onTheme, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.theme)) }
        Toggle(R.string.vibration, settings.vibrate) { model.setVibrate(it) }
        Toggle(R.string.sounds, settings.sounds) { model.setSounds(it) }
        Toggle(R.string.auto_connect, settings.autoConnect) { model.setAutoConnect(it) }
        Text(stringResource(R.string.mouse_speed))
        Slider(settings.mouseSpeed, { model.setMouseSpeed(it) }, valueRange = 0.3f..3f)
        Toggle(R.string.clear_after_send, settings.clearAfterSend) { model.setClearAfterSend(it) }
        val context = LocalContext.current
        OutlinedButton(onClick = onDevices, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.devices)) }
        OutlinedButton(
            onClick = { runCatching { context.startActivity(Intent(AndroidSettings.ACTION_BLUETOOTH_SETTINGS)) } },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.bt_settings)) }
        Text(stringResource(R.string.button_order), style = MaterialTheme.typography.titleMedium)
        settings.order.forEach { section ->
            val title = when (section) {
                RemoteSection.APPS -> R.string.section_apps
                RemoteSection.TOP -> R.string.section_navigation
                RemoteSection.TOOLS -> R.string.section_tools
                RemoteSection.VOLUME -> R.string.section_volume
                RemoteSection.DPAD -> R.string.section_dpad
                RemoteSection.NUMBERS -> R.string.section_numbers
                RemoteSection.MEDIA -> R.string.section_media
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(title), Modifier.weight(1f))
                IconButton(onClick = { model.move(section, -1) }) { Icon(Icons.Filled.KeyboardArrowUp, "Up") }
                IconButton(onClick = { model.move(section, 1) }) { Icon(Icons.Filled.KeyboardArrowDown, "Down") }
            }
        }
        OutlinedButton(onClick = { confirm = { connection.deleteAll() } }) { Text(stringResource(R.string.delete_devices)) }
        OutlinedButton(onClick = { confirm = { model.reset() } }) { Text(stringResource(R.string.reset_app)) }
    }
    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.confirm)) },
            confirmButton = { TextButton(onClick = { action(); confirm = null }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
fun TextScreen(body: Int) = Page {
    Text("REMOTE MAGIC 1.1.0", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
    Text(stringResource(body))
}
