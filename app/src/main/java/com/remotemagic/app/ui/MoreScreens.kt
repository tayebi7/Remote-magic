package com.remotemagic.app.ui

import android.Manifest
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
import com.remotemagic.app.data.Shortcut
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
    var editing by remember { mutableStateOf<Shortcut?>(null) }
    Page {
        AppGrid { model.open(it) }
        Text(stringResource(R.string.custom_shortcuts), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.shortcut_note), style = MaterialTheme.typography.bodySmall)
        shortcuts.forEach { item ->
            ListItem(
                modifier = Modifier.clickable { model.open(item) },
                headlineContent = { Text(item.label) },
                supportingContent = { Text("${item.protocol} • ${item.target}") },
                trailingContent = {
                    Row {
                        IconButton(onClick = { editing = item }) { Icon(Icons.Filled.Edit, stringResource(R.string.edit)) }
                        IconButton(onClick = { model.delete(item) }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                    }
                }
            )
        }
        val protocol = connection.device?.protocol
        // Bluetooth HID cannot launch applications.
        Button(
            enabled = protocol != null && protocol != Protocol.BLUETOOTH_HID,
            onClick = { editing = Shortcut(label = "", target = "", protocol = protocol!!.name) }
        ) { Text(stringResource(R.string.add)) }
    }
    editing?.let { item ->
        var label by remember(item) { mutableStateOf(item.label) }
        var target by remember(item) { mutableStateOf(item.target) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.custom_shortcuts)) },
            text = {
                Column {
                    OutlinedTextField(label, { label = it }, label = { Text(stringResource(R.string.shortcut_name)) })
                    OutlinedTextField(target, { target = it }, label = { Text(stringResource(R.string.shortcut_target)) })
                }
            },
            confirmButton = {
                TextButton(enabled = label.isNotBlank() && target.isNotBlank(), onClick = {
                    model.save(item.copy(label = label.trim(), target = target.trim()))
                    editing = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) } }
        )
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
    onTheme: () -> Unit
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
        Text(stringResource(R.string.button_order), style = MaterialTheme.typography.titleMedium)
        settings.order.forEach { section ->
            val title = when (section) {
                RemoteSection.APPS -> R.string.section_apps
                RemoteSection.NAVIGATION -> R.string.section_navigation
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
    Text("REMOTE MAGIC 1.0.0", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
    Text(stringResource(body))
}
