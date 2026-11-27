package com.remotemagic.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.remotemagic.app.R
import com.remotemagic.app.data.SavedDevice
import com.remotemagic.app.domain.ConnectionStatus
import com.remotemagic.app.sharing.Sharing
import com.remotemagic.app.sharing.TvCard
import com.remotemagic.app.wifi.WakeOnLan
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

fun statusText(status: ConnectionStatus) = when (status) {
    ConnectionStatus.CONNECTED -> R.string.connected
    ConnectionStatus.CONNECTING -> R.string.connecting
    ConnectionStatus.ERROR -> R.string.error
    ConnectionStatus.DISCONNECTED -> R.string.disconnected
}

@Composable
fun ConnectionCard(model: ConnectionViewModel) {
    val state by model.connection.collectAsStateWithLifecycle()
    val saved by model.saved.collectAsStateWithLifecycle()
    val device = state.device
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Tv, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(device?.name ?: stringResource(R.string.not_connected), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(statusText(state.status)), color = MaterialTheme.colorScheme.secondary)
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (device != null) {
                val unknown = stringResource(R.string.unknown)
                Text("${stringResource(R.string.manufacturer)}: ${device.manufacturer.ifEmpty { unknown }}")
                Text("IP / MAC: ${device.address}")
                Text("${stringResource(R.string.connection_type)}: ${if (device.protocol.wifi) "Wi-Fi" else "Bluetooth"}")
                Text("${stringResource(R.string.protocol)}: ${device.protocol.label}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.status == ConnectionStatus.CONNECTED || state.status == ConnectionStatus.CONNECTING) {
                        OutlinedButton(onClick = model::disconnect) { Text(stringResource(R.string.disconnect)) }
                    } else {
                        Button(onClick = { model.connect(device) }) { Text(stringResource(R.string.reconnect)) }
                    }
                    // Wake is only offered when a MAC address is known.
                    saved.firstOrNull { it.id == device.id && device.protocol.wifi && WakeOnLan.parseMac(it.mac) != null }
                        ?.let { OutlinedButton(onClick = { model.wake(it) }) { Text(stringResource(R.string.wake)) } }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(model: ConnectionViewModel, onRemote: () -> Unit, onSearch: () -> Unit) {
    val state by model.connection.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status = stringResource(statusText(state.status))
    val device = state.device
    val card = device?.let {
        TvCard(it.name, it.manufacturer.ifEmpty { it.protocol.label }, if (it.protocol.wifi) "Wi-Fi" else "Bluetooth", status)
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("REMOTE MAGIC", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.secondary)
        ConnectionCard(model)
        val wide = Modifier.fillMaxWidth().height(56.dp)
        Button(onClick = onSearch, modifier = wide) { Text(stringResource(R.string.search)) }
        Button(onClick = onRemote, modifier = wide) { Text(stringResource(R.string.remote)) }
        if (card != null && state.status == ConnectionStatus.CONNECTED) {
            OutlinedButton(modifier = wide, onClick = {
                Sharing.shareImage(context, Sharing.png(Sharing.renderCard(card)), "tv-card.png", "image/png")
            }) { Text(stringResource(R.string.share_card)) }
            OutlinedButton(modifier = wide, onClick = {
                model.message.value =
                    if (Sharing.saveToGallery(context, Sharing.renderCard(card))) R.string.saved_gallery else R.string.send_failed
            }) { Text(stringResource(R.string.save_card)) }
            if (model.canScreenshot) {
                OutlinedButton(modifier = wide, onClick = {
                    scope.launch {
                        val bytes = model.screenshot()
                        if (bytes == null) model.message.value = R.string.screenshot_failed
                        else Sharing.shareImage(context, bytes, "tv-screen.jpg", "image/jpeg")
                    }
                }) { Text(stringResource(R.string.screenshot)) }
            }
        }
        Text(stringResource(R.string.wake_note), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun SavedDevicesScreen(model: ConnectionViewModel, favoritesOnly: Boolean) {
    val all by model.saved.collectAsStateWithLifecycle()
    val devices = if (favoritesOnly) all.filter { it.favorite } else all
    var editing by remember { mutableStateOf<SavedDevice?>(null) }
    if (devices.isEmpty()) Text(stringResource(R.string.empty), Modifier.padding(24.dp))
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(devices, key = { it.id }) { device ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(device.name, style = MaterialTheme.typography.titleMedium)
                    Text("${device.toDiscovered().protocol.label} • ${device.address}")
                    Row {
                        TextButton(onClick = { model.connect(device.toDiscovered()) }) { Text(stringResource(R.string.connect)) }
                        if (WakeOnLan.parseMac(device.mac) != null) {
                            TextButton(onClick = { model.wake(device) }) { Text(stringResource(R.string.wake)) }
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { model.setFavorite(device, !device.favorite) }) {
                            Icon(if (device.favorite) Icons.Filled.Star else Icons.Filled.StarBorder, stringResource(R.string.favorite))
                        }
                        IconButton(onClick = { editing = device }) { Icon(Icons.Filled.Edit, stringResource(R.string.edit)) }
                        IconButton(onClick = { model.delete(device) }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                    }
                    if (device.id == model.connection.collectAsStateWithLifecycle().value.device?.id) ConnectionCard(model)
                }
            }
        }
    }
    editing?.let { device ->
        var mac by remember(device.id) { mutableStateOf(device.mac) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(device.name) },
            text = { OutlinedTextField(mac, { mac = it }, label = { Text(stringResource(R.string.mac)) }) },
            confirmButton = { TextButton(onClick = { model.setMac(device, mac); editing = null }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
fun HistoryScreen(model: ConnectionViewModel) {
    val history by model.history.collectAsStateWithLifecycle()
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = model::clearHistory) { Text(stringResource(R.string.clear_history)) }
        if (history.isEmpty()) Text(stringResource(R.string.empty), Modifier.padding(24.dp))
        LazyColumn {
            items(history, key = { it.id }) { entry ->
                ListItem(
                    leadingContent = { Icon(if (entry.success) Icons.Filled.CheckCircle else Icons.Filled.Error, null) },
                    headlineContent = { Text(entry.name) },
                    supportingContent = {
                        Text("${entry.protocol} • ${format.format(Date(entry.time))}" +
                            if (entry.detail.isEmpty()) "" else "\n${entry.detail}")
                    }
                )
            }
        }
    }
}
