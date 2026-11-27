package com.remotemagic.app.remote

import com.remotemagic.app.domain.Protocol
import com.remotemagic.app.domain.TextResult
import com.remotemagic.app.protocols.Credentials
import com.remotemagic.app.protocols.PairingUi
import com.remotemagic.app.protocols.PointerInput
import com.remotemagic.app.protocols.TextInput
import com.remotemagic.app.ui.toDiscovered
import kotlinx.coroutines.delay
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.remotemagic.app.R
import com.remotemagic.app.RemoteMagicApp
import com.remotemagic.app.data.Shortcut
import com.remotemagic.app.domain.RemoteKey
import com.remotemagic.app.domain.SendResult
import com.remotemagic.app.domain.TvApp
import com.remotemagic.app.voice.VoiceCommand
import com.remotemagic.app.voice.VoiceCommandParser
import com.remotemagic.app.wifi.WakeOnLan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private object SilentPairing : PairingUi {
    override suspend fun requestCode(): String? = null
    override fun showPromptOnTv() {}
}

class RemoteViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as RemoteMagicApp
    private val controller = app.controller
    private val dao = app.database.dao()
    val voice = app.voice
    val shortcuts = dao.shortcuts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val message = MutableStateFlow<Int?>(null)

    private fun report(result: SendResult) {
        message.value = when (result) {
            SendResult.OK -> null
            SendResult.NOT_CONNECTED -> R.string.not_connected
            SendResult.UNSUPPORTED -> R.string.unsupported
            SendResult.FAILED -> R.string.send_failed
        }
    }

    fun supports(key: RemoteKey): Boolean = controller.client?.supportedKeys?.contains(key) == true

    fun press(key: RemoteKey) = viewModelScope.launch { report(controller.send(key)) }

    /** Power button: sends the key when connected, otherwise tries to wake the TV. */
    fun power() {
        if (controller.client != null) press(RemoteKey.POWER) else wake()
    }

    private fun wake() = viewModelScope.launch {
        val device = controller.state.value.device?.let { dao.device(it.id) } ?: dao.lastDevice()
        if (device == null) {
            message.value = R.string.not_connected
        } else if (device.protocol == Protocol.BLUETOOTH_HID.name) {
            // Works only if the TV keeps its Bluetooth receiver on in standby.
            val connected = controller.connect(device.toDiscovered(), Credentials(), SilentPairing) != null
            message.value = if (connected && controller.send(RemoteKey.POWER) == SendResult.OK) null
            else R.string.bt_wake_failed
        } else {
            message.value =
                if (WakeOnLan.wake(device.mac, device.address)) R.string.wake_sent else R.string.wake_unavailable
        }
    }

    fun open(tvApp: TvApp) = viewModelScope.launch {
        val protocol = controller.state.value.device?.protocol
        if (protocol == null || controller.client == null) {
            report(SendResult.NOT_CONNECTED)
            return@launch
        }
        val custom = shortcuts.value.firstOrNull { it.protocol == protocol.name && it.label == tvApp.title }
        if (custom == null && controller.client?.supportedApps?.contains(tvApp) != true) {
            message.value =
                if (protocol == Protocol.BLUETOOTH_HID) R.string.unsupported else R.string.app_not_configured
            return@launch
        }
        val result = if (custom != null) controller.launchCustom(custom.target) else controller.launch(tvApp)
        message.value = when {
            result == SendResult.FAILED -> R.string.app_not_installed
            // Android TV gives no answer about whether the app exists.
            result == SendResult.OK && protocol == Protocol.ANDROID_TV -> R.string.app_unverified
            result == SendResult.OK -> null
            else -> R.string.unsupported
        }
    }

    /** Saves or clears the app id used by a shortcut for one protocol. */
    fun setAppId(app: TvApp, protocol: Protocol, target: String) = viewModelScope.launch {
        val old = shortcuts.value.firstOrNull { it.protocol == protocol.name && it.label == app.title }
        when {
            target.isNotBlank() -> dao.saveShortcut(
                Shortcut(old?.id ?: 0, app.title, target.trim(), protocol.name)
            )
            old != null -> dao.deleteShortcut(old.id)
        }
        message.value = R.string.saved
    }

    val canPoint: Boolean get() = controller.client?.pointer != null
    val canScroll: Boolean get() = controller.client?.pointer?.canScroll == true
    val canLongPress: Boolean get() = controller.client?.pointer?.canLongPress == true
    val canType: Boolean get() = controller.client?.textInput != null

    private var pendingX = 0
    private var pendingY = 0
    private var moving = false

    /** Coalesces sensor and touch movement so the link is not flooded. */
    fun move(dx: Int, dy: Int) {
        pendingX += dx
        pendingY += dy
        if (moving) return
        moving = true
        viewModelScope.launch {
            try {
                while (pendingX != 0 || pendingY != 0) {
                    val pointer = controller.client?.pointer ?: break
                    val x = pendingX.coerceIn(-127, 127)
                    val y = pendingY.coerceIn(-127, 127)
                    pendingX -= x
                    pendingY -= y
                    if (!pointer.move(x, y)) break
                    delay(16)
                }
            } finally {
                pendingX = 0
                pendingY = 0
                moving = false
            }
        }
    }

    private fun pointerAction(action: suspend (PointerInput) -> Boolean) = viewModelScope.launch {
        val pointer = controller.client?.pointer
        message.value = when {
            pointer == null -> R.string.no_pointer
            action(pointer) -> null
            else -> R.string.send_failed
        }
    }

    fun click() = pointerAction { it.click() }
    fun doubleClick() = pointerAction { it.click(); delay(80); it.click() }
    fun longClick() = pointerAction { if (it.canLongPress) it.longClick() else false }
    fun scroll(steps: Int) = pointerAction { it.scroll(steps) }

    private fun textAction(action: suspend (TextInput) -> TextResult, done: () -> Unit = {}) = viewModelScope.launch {
        val input = controller.client?.textInput
        if (input == null) {
            message.value = R.string.no_text
            return@launch
        }
        val result = action(input)
        message.value = when (result) {
            TextResult.CONFIRMED -> R.string.text_confirmed
            TextResult.SENT -> R.string.text_sent
            TextResult.NO_FIELD -> R.string.text_no_field
            TextResult.BAD_CHARACTERS -> R.string.text_bad_characters
            TextResult.FAILED -> R.string.send_failed
        }
        if (result == TextResult.CONFIRMED || result == TextResult.SENT) done()
    }

    fun type(text: String, done: () -> Unit) = textAction({ it.type(text) }, done)

    private fun textKey(action: suspend (TextInput) -> Boolean) = viewModelScope.launch {
        val input = controller.client?.textInput
        message.value = when {
            input == null -> R.string.no_text
            action(input) -> null
            else -> R.string.unsupported
        }
    }

    fun backspace() = textKey { it.backspace() }
    fun enter() = textKey { it.enter() }
    fun escape() = press(if (supports(RemoteKey.EXIT)) RemoteKey.EXIT else RemoteKey.BACK)

    fun listen(languageTag: String) = voice.start(languageTag) { sentences ->
        when (val command = sentences.firstNotNullOfOrNull(VoiceCommandParser::parse)) {
            is VoiceCommand.Key -> press(command.key)
            is VoiceCommand.Open -> open(command.app)
            VoiceCommand.PowerOn -> wake()
            null -> { message.value = R.string.voice_unknown }
        }
        Unit
    }

    override fun onCleared() = voice.stop()
}
