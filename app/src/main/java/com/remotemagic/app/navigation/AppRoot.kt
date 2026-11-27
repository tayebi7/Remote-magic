package com.remotemagic.app.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.remotemagic.app.R
import com.remotemagic.app.remote.RemoteViewModel
import com.remotemagic.app.settings.SettingsViewModel
import com.remotemagic.app.sharing.Sharing
import com.remotemagic.app.ui.*
import kotlinx.coroutines.launch

enum class Screen(val route: String, val title: Int) {
    HOME("home", R.string.home), REMOTE("remote", R.string.remote), DEVICES("devices", R.string.devices),
    BLUETOOTH("bluetooth", R.string.bluetooth), WIFI("wifi", R.string.wifi), VOICE("voice", R.string.voice),
    APPS("apps", R.string.apps), FAVORITES("favorites", R.string.favorites), HISTORY("history", R.string.history),
    SETTINGS("settings", R.string.settings), LANGUAGE("language", R.string.language), THEME("theme", R.string.theme),
    HELP("help", R.string.help), ABOUT("about", R.string.about)
}

private const val APP_URL = "https://github.com/tayebi7/Remote-magic/releases"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(settingsModel: SettingsViewModel) {
    val nav = rememberNavController()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val connectionModel: ConnectionViewModel = viewModel()
    val remoteModel: RemoteViewModel = viewModel()
    val settings by settingsModel.settings.collectAsStateWithLifecycle()
    val connection by connectionModel.connection.collectAsStateWithLifecycle()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val snackbar = remember { SnackbarHostState() }

    fun go(screen: Screen) = nav.navigate(screen.route) { launchSingleTop = true }

    val connectionMessage by connectionModel.message.collectAsStateWithLifecycle()
    val remoteMessage by remoteModel.message.collectAsStateWithLifecycle()
    (connectionMessage ?: remoteMessage)?.let { id ->
        val text = stringResource(id)
        LaunchedEffect(id) {
            snackbar.showSnackbar(text)
            connectionModel.message.value = null
            remoteModel.message.value = null
        }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        // Swiping would fight with the remote buttons; open the drawer from the menu icon.
        gesturesEnabled = drawer.isOpen,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                    Text("REMOTE MAGIC", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.headlineSmall)
                    Screen.entries.forEach { screen ->
                        NavigationDrawerItem(
                            label = { Text(stringResource(screen.title)) },
                            selected = route == screen.route,
                            onClick = { scope.launch { drawer.close() }; go(screen) }
                        )
                    }
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.share_app)) },
                        icon = { Icon(Icons.Filled.Share, null) },
                        selected = false,
                        onClick = { Sharing.shareText(context, "REMOTE MAGIC $APP_URL") }
                    )
                }
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Screen.entries.firstOrNull { it.route == route }?.title ?: R.string.remote)) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Filled.Menu, "Drawer") }
                    }
                )
            }
        ) { padding ->
            NavHost(nav, startDestination = Screen.REMOTE.route, modifier = Modifier.padding(padding)) {
                composable(Screen.HOME.route) { HomeScreen(connectionModel, { go(Screen.REMOTE) }, { go(Screen.WIFI) }) }
                composable(Screen.REMOTE.route) { RemoteScreen(remoteModel, connection, settings) { go(Screen.WIFI) } }
                composable(Screen.WIFI.route) { ConnectScreen(connectionModel, 0) }
                composable(Screen.BLUETOOTH.route) { ConnectScreen(connectionModel, 1) }
                composable(Screen.DEVICES.route) { SavedDevicesScreen(connectionModel, false) }
                composable(Screen.FAVORITES.route) { SavedDevicesScreen(connectionModel, true) }
                composable(Screen.HISTORY.route) { HistoryScreen(connectionModel) }
                composable(Screen.VOICE.route) { VoiceScreen(remoteModel) }
                composable(Screen.APPS.route) { AppsScreen(remoteModel, connection) }
                composable(Screen.SETTINGS.route) {
                    SettingsScreen(settingsModel, connectionModel, { go(Screen.LANGUAGE) }, { go(Screen.THEME) })
                }
                composable(Screen.LANGUAGE.route) { LanguageScreen() }
                composable(Screen.THEME.route) { ThemeScreen(settingsModel) }
                composable(Screen.HELP.route) { TextScreen(R.string.help_text) }
                composable(Screen.ABOUT.route) { TextScreen(R.string.about_text) }
            }
        }
    }
    PairingDialogs(connectionModel)
}

@Composable
private fun PairingDialogs(model: ConnectionViewModel) {
    when (val dialog = model.pairing.collectAsStateWithLifecycle().value) {
        is PairingDialog.Code -> {
            var code by remember(dialog) { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { dialog.answer.complete(null) },
                title = { Text(stringResource(R.string.pair_code)) },
                text = {
                    OutlinedTextField(
                        code, { code = it.uppercase().take(6) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                    )
                },
                confirmButton = {
                    TextButton(enabled = code.length == 6, onClick = { dialog.answer.complete(code) }) {
                        Text(stringResource(R.string.ok))
                    }
                },
                dismissButton = { TextButton(onClick = { dialog.answer.complete(null) }) { Text(stringResource(R.string.cancel)) } }
            )
        }
        PairingDialog.Prompt -> AlertDialog(
            onDismissRequest = { model.pairing.value = null },
            title = { Text(stringResource(R.string.pair_prompt)) },
            confirmButton = { TextButton(onClick = { model.pairing.value = null }) { Text(stringResource(R.string.ok)) } },
            dismissButton = {
                TextButton(onClick = { model.pairing.value = null; model.disconnect() }) { Text(stringResource(R.string.cancel)) }
            }
        )
        null -> {}
    }
}
