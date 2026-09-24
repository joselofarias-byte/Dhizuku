package com.rosan.dhizuku.ui.page.settings.admin_controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Adb
import androidx.compose.material.icons.twotone.AppBlocking
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rosan.dhizuku.R
import com.rosan.dhizuku.ui.theme.exclude
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminControlsPage(
    windowInsets: WindowInsets,
    navController: NavController
) {
    val context = LocalContext.current
    val manager = remember(context) { AdminControlManager(context.applicationContext) }

    var adbState by remember { mutableStateOf(manager.readAdbState()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var loadingApps by remember { mutableStateOf(true) }
    val apps = remember { mutableStateListOf<ManagedAppPolicy>() }

    suspend fun reloadApps() {
        loadingApps = true
        val fresh = withContext(Dispatchers.IO) {
            runCatching { manager.listUserApps() }
        }
        fresh.onSuccess {
            apps.clear()
            apps.addAll(it)
        }.onFailure {
            statusMessage = it.message ?: it.javaClass.simpleName
        }
        loadingApps = false
    }

    LaunchedEffect(Unit) {
        adbState = manager.readAdbState()
        reloadApps()
    }

    val filteredApps = remember(apps.toList(), search) {
        if (search.isBlank()) apps.toList()
        else apps.filter {
            it.label.contains(search, ignoreCase = true) ||
                it.packageName.contains(search, ignoreCase = true)
        }
    }

    Scaffold(
        modifier = Modifier
            .windowInsetsPadding(windowInsets.exclude(WindowInsetsSides.Bottom))
            .fillMaxSize(),
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Bottom),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                    }
                },
                title = { Text(stringResource(R.string.admin_controls_title)) },
                actions = {
                    IconButton(onClick = {
                        adbState = manager.readAdbState()
                        statusMessage = context.getString(R.string.admin_controls_refreshed)
                    }) {
                        Icon(Icons.TwoTone.Refresh, contentDescription = stringResource(R.string.retry))
                    }
                }
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item("owner") {
                StatusCard(
                    title = stringResource(R.string.admin_owner_status),
                    value = when {
                        adbState.isDeviceOwner -> stringResource(R.string.confirm_device_owner)
                        adbState.isProfileOwner -> stringResource(R.string.confirm_profile_owner)
                        else -> stringResource(R.string.home_status_owner_denied)
                    },
                    positive = adbState.isDeviceOwner || adbState.isProfileOwner
                )
            }

            item("adb") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.TwoTone.Adb, contentDescription = null)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.admin_adb_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    if (adbState.adbEnabled)
                                        stringResource(R.string.admin_adb_on)
                                    else stringResource(R.string.admin_adb_off),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Switch(
                                checked = adbState.adbEnabled,
                                enabled = adbState.isDeviceOwner,
                                onCheckedChange = { enabled ->
                                    manager.setAdbEnabled(enabled)
                                        .onSuccess { state ->
                                            adbState = state
                                            statusMessage = context.getString(
                                                if (enabled) R.string.admin_adb_enabled_ok
                                                else R.string.admin_adb_disabled_ok
                                            )
                                        }
                                        .onFailure {
                                            adbState = manager.readAdbState()
                                            statusMessage = it.message ?: it.javaClass.simpleName
                                        }
                                }
                            )
                        }

                        Text(
                            if (adbState.debuggingRestricted)
                                stringResource(R.string.admin_debug_restriction_on)
                            else stringResource(R.string.admin_debug_restriction_off),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            stringResource(R.string.admin_adb_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            statusMessage?.let { message ->
                item("status_message") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Text(
                            message,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item("apps_header") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.admin_apps_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.admin_apps_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.admin_apps_search)) }
                    )
                }
            }

            if (loadingApps) {
                item("loading") {
                    Text(stringResource(R.string.admin_apps_loading))
                }
            } else {
                items(filteredApps, key = { it.packageName }) { app ->
                    AppPolicyCard(
                        app = app,
                        onHiddenChange = { hidden ->
                            manager.setApplicationHidden(app.packageName, hidden)
                                .onSuccess { actual ->
                                    val index = apps.indexOfFirst { it.packageName == app.packageName }
                                    if (index >= 0) apps[index] = apps[index].copy(hidden = actual)
                                    statusMessage = context.getString(
                                        if (actual) R.string.admin_app_hidden_ok
                                        else R.string.admin_app_visible_ok,
                                        app.label
                                    )
                                }
                                .onFailure {
                                    statusMessage = it.message ?: it.javaClass.simpleName
                                }
                        },
                        onUninstallBlockedChange = { blocked ->
                            manager.setUninstallBlocked(app.packageName, blocked)
                                .onSuccess { actual ->
                                    val index = apps.indexOfFirst { it.packageName == app.packageName }
                                    if (index >= 0) apps[index] = apps[index].copy(uninstallBlocked = actual)
                                    statusMessage = context.getString(
                                        if (actual) R.string.admin_app_uninstall_blocked_ok
                                        else R.string.admin_app_uninstall_allowed_ok,
                                        app.label
                                    )
                                }
                                .onFailure {
                                    statusMessage = it.message ?: it.javaClass.simpleName
                                }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(title: String, value: String, positive: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (positive)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AppPolicyCard(
    app: ManagedAppPolicy,
    onHiddenChange: (Boolean) -> Unit,
    onUninstallBlockedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text(app.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.TwoTone.Visibility, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.admin_hide_app), modifier = Modifier.weight(1f))
                Switch(
                    checked = app.hidden,
                    onCheckedChange = onHiddenChange
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.TwoTone.AppBlocking, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.admin_block_uninstall), modifier = Modifier.weight(1f))
                Switch(
                    checked = app.uninstallBlocked,
                    onCheckedChange = onUninstallBlockedChange
                )
            }
        }
    }
}
