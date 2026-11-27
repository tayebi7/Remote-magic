package com.remotemagic.app.remote

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

    /** Power button: sends the key when connected, otherwise tries Wake-on-LAN. */
    fun power() {
        if (controller.client != null) press(RemoteKey.POWER) else wake()
    }

    private fun wake() = viewModelScope.launch {
        val device = controller.state.value.device?.let { dao.device(it.id) } ?: dao.lastDevice()
        message.value = if (device != null && WakeOnLan.wake(device.mac, device.address)) R.string.wake_sent
        else R.string.not_connected
    }

    fun open(tvApp: TvApp) = viewModelScope.launch {
        val protocol = controller.state.value.device?.protocol
        val custom = shortcuts.value.firstOrNull {
            it.protocol == protocol?.name && it.label.equals(tvApp.title, ignoreCase = true)
        }
        report(if (custom != null) controller.launchCustom(custom.target) else controller.launch(tvApp))
    }

    fun open(shortcut: Shortcut) = viewModelScope.launch {
        if (shortcut.protocol != controller.state.value.device?.protocol?.name) report(SendResult.UNSUPPORTED)
        else report(controller.launchCustom(shortcut.target))
    }

    fun save(shortcut: Shortcut) = viewModelScope.launch { dao.saveShortcut(shortcut) }
    fun delete(shortcut: Shortcut) = viewModelScope.launch { dao.deleteShortcut(shortcut.id) }

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
