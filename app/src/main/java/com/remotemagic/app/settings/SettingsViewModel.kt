package com.remotemagic.app.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.remotemagic.app.RemoteMagicApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as RemoteMagicApp
    private val store = app.settings
    val settings = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun setTheme(value: ThemeMode) = viewModelScope.launch { store.setTheme(value) }
    fun setVibrate(value: Boolean) = viewModelScope.launch { store.setVibrate(value) }
    fun setSounds(value: Boolean) = viewModelScope.launch { store.setSounds(value) }
    fun setAutoConnect(value: Boolean) = viewModelScope.launch { store.setAutoConnect(value) }

    fun setMouseSpeed(value: Float) = viewModelScope.launch { store.setMouseSpeed(value) }
    fun setClearAfterSend(value: Boolean) = viewModelScope.launch { store.setClearAfterSend(value) }
    fun setShowNumbers(value: Boolean) = viewModelScope.launch { store.setShowNumbers(value) }

    fun move(section: RemoteSection, delta: Int) = viewModelScope.launch {
        val order = settings.value.order.toMutableList()
        val from = order.indexOf(section)
        val to = (from + delta).coerceIn(0, order.lastIndex)
        order.removeAt(from)
        order.add(to, section)
        store.setOrder(order)
    }

    fun reset() = viewModelScope.launch {
        app.controller.disconnect()
        app.database.dao().deleteDevices()
        app.database.dao().clearHistory()
        app.database.dao().deleteShortcuts()
        store.reset()
    }
}
