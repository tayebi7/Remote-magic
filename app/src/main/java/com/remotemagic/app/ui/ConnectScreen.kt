package com.remotemagic.app.ui

import android.Manifest
import android.app.Activity
import com.remotemagic.app.domain.DiscoveredDevice
import com.remotemagic.app.domain.Protocol
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.remotemagic.app.R
import com.remotemagic.app.RemoteMagicApp

/** [initialTab]: 0 Wi-Fi, 1 Bluetooth, 2 manual IP. */
@Composable
fun ConnectScreen(model: ConnectionViewModel, initialTab: Int) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var ip by rememberSaveable { mutableStateOf("") }
    val found by model.found.collectAsStateWithLifecycle()
    val scanning by model.scanning.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scanner = (context.applicationContext as RemoteMagicApp).bluetooth

    val enable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (scanner.isEnabled) model.scanBluetooth()
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        when {
            result.values.any { !it } -> { model.message.value = R.string.permission_denied }
            !scanner.isEnabled -> enable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            else -> model.scanBluetooth()
        }
        Unit
    }
    // Lets the TV's "add accessory" screen find this phone.
    val visible = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_CANCELED) {
            model.connect(DiscoveredDevice("Bluetooth TV", Protocol.BLUETOOTH_HID, ""))
        }
    }
    val advertise = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { !it }) {
            model.message.value = R.string.permission_denied
        } else {
            visible.launch(
                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                    .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
            )
        }
        Unit
    }
    DisposableEffect(Unit) { onDispose { model.stopScan() } }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            listOf(R.string.wifi, R.string.bluetooth, R.string.manual).forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index; model.stopScan(); model.found.value = emptyList() },
                    text = { Text(stringResource(title)) })
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ConnectionCard(model)
            if (tab == 2) {
                OutlinedTextField(
                    value = ip, onValueChange = { ip = it }, singleLine = true,
                    label = { Text(stringResource(R.string.ip_address)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(stringResource(if (tab == 0) R.string.wifi_hint else R.string.bt_hint))
            }
            if (tab == 1) {
                Button(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    onClick = {
                        model.stopScan()
                        advertise.launch(
                            if (Build.VERSION.SDK_INT >= 31)
                                arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
                            else emptyArray()
                        )
                    }
                ) { Text(stringResource(R.string.bt_visible)) }
            }
            Button(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                onClick = {
                    when {
                        scanning -> model.stopScan()
                        tab == 0 -> model.scanWifi()
                        tab == 2 -> model.probe(ip)
                        else -> permissions.launch(
                            if (Build.VERSION.SDK_INT >= 31)
                                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                            else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                        )
                    }
                }
            ) {
                Icon(if (scanning) Icons.Filled.Close else Icons.Filled.Search, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (scanning) R.string.stop else R.string.search))
            }
            if (scanning) {
                Text(stringResource(R.string.searching))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else if (found.isEmpty()) {
                Text(stringResource(R.string.no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn {
                items(found, key = { it.id }) { device ->
                    ListItem(
                        modifier = Modifier.clickable { model.connect(device) },
                        leadingContent = { Icon(if (device.protocol.wifi) Icons.Filled.Tv else Icons.Filled.Bluetooth, null) },
                        headlineContent = { Text(device.name) },
                        supportingContent = {
                            val state = when {
                                !device.supported -> " • " + stringResource(R.string.unsupported_short)
                                device.paired -> " • " + stringResource(R.string.paired)
                                else -> ""
                            }
                            val maker = if (device.manufacturer.isEmpty()) "" else " • ${device.manufacturer}"
                            Text("${device.protocol.label} • ${device.address}$maker$state")
                        },
                        trailingContent = { Text(stringResource(R.string.connect), color = MaterialTheme.colorScheme.primary) }
                    )
                }
            }
        }
    }
}
