package com.remotemagic.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.remotemagic.app.R
import com.remotemagic.app.domain.ConnectionState
import com.remotemagic.app.domain.RemoteKey
import com.remotemagic.app.remote.AirMouseSensor
import com.remotemagic.app.remote.MotionMapper
import com.remotemagic.app.remote.RemoteViewModel
import com.remotemagic.app.settings.AppSettings

@Composable
private fun Scrollable(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp), content = content
)

@Composable
fun MouseScreen(
    model: RemoteViewModel,
    connection: ConnectionState,
    settings: AppSettings,
    onSpeed: (Float) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val mapper = remember { MotionMapper() }
    val sensor = remember { AirMouseSensor(context, mapper) { x, y -> model.move(x, y) } }
    // Opening this screen means the user asked for the mouse, so it starts switched on.
    var on by remember { mutableStateOf(true) }
    mapper.speed = settings.mouseSpeed
    // Reading the state makes this screen refresh when the connection changes.
    val available = connection.status.let { model.canPoint }
    DisposableEffect(on && available) {
        if (on && available) sensor.start()
        onDispose { sensor.stop() }
    }

    Scrollable {
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.remote)) }
        if (!available) {
            Text(stringResource(R.string.no_pointer), color = MaterialTheme.colorScheme.error)
            return@Scrollable
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("MAGIC AIR MOUSE", Modifier.weight(1f), color = MaterialTheme.colorScheme.secondary)
            Switch(on && sensor.available, { on = it }, enabled = sensor.available)
        }
        if (!sensor.available) Text(stringResource(R.string.no_gyroscope), color = MaterialTheme.colorScheme.error)
        if (sensor.available) {
            OutlinedButton(onClick = { mapper.startCalibration(); on = true }) { Text(stringResource(R.string.calibrate)) }
        }
        Text(stringResource(R.string.mouse_speed))
        Slider(settings.mouseSpeed, onSpeed, valueRange = 0.3f..3f)

        Box(
            Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { model.click() },
                        onDoubleTap = { model.doubleClick() },
                        onLongPress = { model.longClick() }
                    )
                }
                .pointerInput(settings.mouseSpeed) {
                    var restX = 0f
                    var restY = 0f
                    detectDragGestures { change, drag ->
                        change.consume()
                        restX += drag.x * settings.mouseSpeed
                        restY += drag.y * settings.mouseSpeed
                        val x = restX.toInt()
                        val y = restY.toInt()
                        restX -= x
                        restY -= y
                        model.move(x, y)
                    }
                },
            contentAlignment = Alignment.Center
        ) { Text("Touchpad") }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { model.click() }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.click)) }
            if (model.canLongPress) {
                Button(onClick = { model.longClick() }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.long_press)) }
            }
        }
        if (model.canScroll) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { model.scroll(3) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.scroll_up)) }
                OutlinedButton(onClick = { model.scroll(-3) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.scroll_down)) }
            }
        }
    }
}

/** Uses the phone keyboard, which already provides Arabic, French, English and symbols. */
@Composable
fun KeyboardScreen(model: RemoteViewModel, connection: ConnectionState, settings: AppSettings) {
    var text by rememberSaveable { mutableStateOf("") }
    val available = connection.status.let { model.canType }
    Scrollable {
        if (!available) {
            Text(stringResource(R.string.no_text), color = MaterialTheme.colorScheme.error)
            return@Scrollable
        }
        Text(stringResource(R.string.keyboard_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.keyboard)) })
        Button(
            enabled = text.isNotEmpty(), modifier = Modifier.fillMaxWidth(),
            onClick = { model.type(text) { if (settings.clearAfterSend) text = "" } }
        ) { Text(stringResource(R.string.send_text)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { model.type(" ") {} }, modifier = Modifier.weight(1f)) { Text("Space") }
            OutlinedButton(onClick = { model.backspace() }, modifier = Modifier.weight(1f)) { Text("⌫") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { model.enter() }, modifier = Modifier.weight(1f)) { Text("Enter") }
            OutlinedButton(onClick = { model.escape() }, modifier = Modifier.weight(1f)) { Text("Esc") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(RemoteKey.LEFT to "←", RemoteKey.UP to "↑", RemoteKey.DOWN to "↓", RemoteKey.RIGHT to "→").forEach { (key, label) ->
                OutlinedButton(onClick = { model.press(key) }, modifier = Modifier.weight(1f)) { Text(label) }
            }
        }
    }
}
