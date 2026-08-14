package com.bysel.trader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.bysel.trader.BuildConfig
import com.bysel.trader.data.auth.AuthSessionManager
import com.bysel.trader.data.models.AuthSessionItem
import com.bysel.trader.data.repository.AuthRepository
import com.bysel.trader.data.repository.Result
import com.bysel.trader.security.BiometricAuthManager
import com.bysel.trader.security.BiometricStatus
import com.bysel.trader.security.getMessage
import com.bysel.trader.ui.theme.DEFAULT_THEME_ID
import com.bysel.trader.ui.theme.allThemes
import com.bysel.trader.ui.theme.getTheme
import com.bysel.trader.ui.theme.isDynamicThemeId
import com.bysel.trader.ui.theme.isLightThemeId
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.ui.theme.byselCardBorder
import com.bysel.trader.ui.theme.byselCardColors
import com.bysel.trader.ui.theme.byselCardElevation
import com.bysel.trader.ui.theme.normalizeThemeId
import com.bysel.trader.alerts.AlertsManager
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

private const val PREF_NOTIF_PERMISSION_ASKED = "notif_permission_requested"

@Composable
fun SettingsScreen(
    onThemeChange: (String) -> Unit = {},
    currentTheme: String = "Default",
    biometricAuthManager: BiometricAuthManager? = null,
    onLogout: () -> Unit = {},
    onLogoutAllDevices: () -> Unit = {},
    onOpenPriceAlerts: () -> Unit = {},
    heatmapInterval: Int = 5000,
    onHeatmapIntervalChange: (Int) -> Unit = {},
    liveQuotesEnabled: Boolean = true,
    onLiveQuotesChange: (Boolean) -> Unit = {},
) {
    val authRepository = remember { AuthRepository() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("bysel_settings", Context.MODE_PRIVATE) }
    val alertsManager = remember { AlertsManager(context) }

    var selectedTheme by remember(currentTheme) { mutableStateOf(normalizeThemeId(currentTheme)) }
    val liveTheme = LocalAppTheme.current
    // Dynamic follows system light/dark — use live luminance, not only the "Light" id.
    var darkMode by remember(selectedTheme, liveTheme.isLight) {
        mutableStateOf(!liveTheme.isLight)
    }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var notificationStatusTick by remember { mutableIntStateOf(0) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var legalDocument by remember { mutableStateOf<com.bysel.trader.ui.components.LegalDocument?>(null) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showSecurityDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showLogoutAllDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var deleteAccountPassword by remember { mutableStateOf("") }
    var deleteAccountError by remember { mutableStateOf<String?>(null) }
    var deleteAccountLoading by remember { mutableStateOf(false) }
    var openWebsite by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }
    var localHeatmapInterval by remember { mutableStateOf(heatmapInterval) }
    var showSessionsDialog by remember { mutableStateOf(false) }
    var sessionsLoading by remember { mutableStateOf(false) }
    var sessionsError by remember { mutableStateOf<String?>(null) }
    var activeSessions by remember { mutableStateOf<List<AuthSessionItem>>(emptyList()) }

    fun loadSessions() {
        scope.launch {
            sessionsLoading = true
            sessionsError = null
            when (val result = authRepository.getActiveSessions()) {
                is Result.Success -> activeSessions = result.data
                is Result.Error -> sessionsError = result.message
                else -> Unit
            }
            sessionsLoading = false
        }
    }

    if (showIntervalDialog) {
        IntervalSelectionDialog(
            selectedInterval = localHeatmapInterval,
            onIntervalSelected = { interval ->
                localHeatmapInterval = interval
                onHeatmapIntervalChange(interval)
                showIntervalDialog = false
            },
            onDismiss = { showIntervalDialog = false }
        )
    }
    if (showThemeDialog) {
        ThemeSelectionDialog(
            selectedTheme = selectedTheme,
            onThemeSelected = { theme ->
                val normalized = normalizeThemeId(theme)
                selectedTheme = normalized
                darkMode = !isLightThemeId(normalized)
                onThemeChange(normalized)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        prefs.edit().putBoolean(PREF_NOTIF_PERMISSION_ASKED, true).apply()
        notificationStatusTick++
        Toast.makeText(
            context,
            if (granted) {
                "Notification permission granted"
            } else {
                "Alerts still work in-app. Enable notifications in Settings for banners."
            },
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun openAppNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra("android.provider.extra.APP_PACKAGE", context.packageName)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
            )
        }
    }

    if (showNotificationsDialog) {
        // Re-read status whenever permission / toggles change.
        notificationStatusTick
        NotificationsSettingsDialog(
            alertsManager = alertsManager,
            onDismiss = { showNotificationsDialog = false },
            onManageAlerts = {
                showNotificationsDialog = false
                onOpenPriceAlerts()
            },
            onRequestPermission = {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    notificationStatusTick++
                    return@NotificationsSettingsDialog
                }
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    notificationStatusTick++
                    return@NotificationsSettingsDialog
                }
                val activity = context as? Activity
                val askedBefore = prefs.getBoolean(PREF_NOTIF_PERMISSION_ASKED, false)
                val showRationale = activity != null &&
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        Manifest.permission.POST_NOTIFICATIONS,
                    )
                // After permanent deny, the system ignores re-requests — send user to Settings.
                if (askedBefore && !showRationale) {
                    Toast.makeText(
                        context,
                        "Enable notifications in system settings",
                        Toast.LENGTH_SHORT,
                    ).show()
                    openAppNotificationSettings()
                } else {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onOpenSystemSettings = {
                openAppNotificationSettings()
                notificationStatusTick++
            },
            onStatusChanged = { notificationStatusTick++ },
        )
    }
    if (showAboutDialog) {
        AboutDialog(
            onDismiss = { showAboutDialog = false },
            onOpenLegal = { doc ->
                showAboutDialog = false
                legalDocument = doc
            }
        )
    }
    legalDocument?.let { doc ->
        com.bysel.trader.ui.components.LegalDocumentDialog(
            document = doc,
            onDismiss = { legalDocument = null }
        )
    }
    if (showProfileDialog) {
        ProfileDialog(
            authRepository = authRepository,
            onDismiss = { showProfileDialog = false }
        )
    }
    if (showSecurityDialog) {
        SecurityDialog(
            authRepository = authRepository,
            onDismiss = { showSecurityDialog = false }
        )
    }
    if (showFeedbackDialog) {
        FeedbackDialog(onDismiss = { showFeedbackDialog = false })
    }
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = LocalAppTheme.current.card,
            title = {
                Text(
                    text = "Logout",
                    color = LocalAppTheme.current.text,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to logout?",
                    color = LocalAppTheme.current.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text("Logout", color = LocalAppTheme.current.negative)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = LocalAppTheme.current.textSecondary)
                }
            }
        )
    }
    if (showLogoutAllDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutAllDialog = false },
            containerColor = LocalAppTheme.current.card,
            title = {
                Text(
                    text = "Logout All Devices",
                    color = LocalAppTheme.current.text,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will end your sessions on all devices. Continue?",
                    color = LocalAppTheme.current.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutAllDialog = false
                        onLogoutAllDevices()
                    }
                ) {
                    Text("Logout All", color = LocalAppTheme.current.negative)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutAllDialog = false }) {
                    Text("Cancel", color = LocalAppTheme.current.textSecondary)
                }
            }
        )
    }
    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!deleteAccountLoading) {
                    showDeleteAccountDialog = false
                    deleteAccountPassword = ""
                    deleteAccountError = null
                }
            },
            containerColor = LocalAppTheme.current.card,
            title = {
                Text(
                    text = "Delete Account",
                    color = LocalAppTheme.current.negative,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "This will permanently delete your account and all associated data. This action cannot be undone.",
                        color = LocalAppTheme.current.textSecondary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = deleteAccountPassword,
                        onValueChange = { deleteAccountPassword = it; deleteAccountError = null },
                        label = { Text("Enter your password to confirm") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = deleteAccountError != null
                    )
                    if (deleteAccountError != null) {
                        Text(
                            text = deleteAccountError!!,
                            color = LocalAppTheme.current.negative,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (deleteAccountPassword.isBlank()) {
                            deleteAccountError = "Password is required"
                            return@TextButton
                        }
                        deleteAccountLoading = true
                        scope.launch {
                            when (val result = authRepository.deleteAccount(deleteAccountPassword)) {
                                is Result.Success -> {
                                    showDeleteAccountDialog = false
                                    deleteAccountPassword = ""
                                    onLogout()
                                }
                                is Result.Error -> {
                                    deleteAccountError = result.message
                                    deleteAccountLoading = false
                                }
                                else -> {}
                            }
                        }
                    },
                    enabled = !deleteAccountLoading
                ) {
                    if (deleteAccountLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Delete Permanently", color = LocalAppTheme.current.negative)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteAccountDialog = false
                        deleteAccountPassword = ""
                        deleteAccountError = null
                    },
                    enabled = !deleteAccountLoading
                ) {
                    Text("Cancel", color = LocalAppTheme.current.textSecondary)
                }
            }
        )
    }
    if (openWebsite) {
        WebsiteDialog(onDismiss = { openWebsite = false })
    }
    if (showSessionsDialog) {
        ManageSessionsDialog(
            sessions = activeSessions,
            isLoading = sessionsLoading,
            error = sessionsError,
            onRefresh = { loadSessions() },
            onRevokeSession = { sessionId ->
                scope.launch {
                    sessionsLoading = true
                    sessionsError = null
                    when (val result = authRepository.revokeSession(sessionId)) {
                        is Result.Success -> loadSessions()
                        is Result.Error -> {
                            sessionsError = result.message
                            sessionsLoading = false
                        }
                        else -> sessionsLoading = false
                    }
                }
            },
            onDismiss = { showSessionsDialog = false }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppTheme.current.surface)
            .padding(16.dp)
    ) {
        item {
            Text(
                text = "Settings",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = LocalAppTheme.current.text,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
        item {
            SettingsSection(title = "Display")
            Spacer(modifier = Modifier.height(12.dp))
        }
        item {
            SettingItem(
                icon = Icons.Filled.Brightness4,
                title = "Dark Mode",
                subtitle = when {
                    isDynamicThemeId(selectedTheme) && darkMode -> "Dynamic · system dark"
                    isDynamicThemeId(selectedTheme) -> "Dynamic · system light"
                    darkMode -> "Enabled"
                    else -> "Disabled (Light)"
                },
                value = darkMode,
                onValueChange = { enabled ->
                    darkMode = enabled
                    if (!enabled) {
                        if (!isLightThemeId(selectedTheme)) {
                            prefs.edit().putString("lastDarkTheme", selectedTheme).apply()
                        }
                        selectedTheme = "Light"
                        onThemeChange("Light")
                    } else {
                        val restored = normalizeThemeId(
                            prefs.getString("lastDarkTheme", DEFAULT_THEME_ID)
                        ).let {
                            when {
                                isLightThemeId(it) -> DEFAULT_THEME_ID
                                else -> it
                            }
                        }
                        selectedTheme = restored
                        onThemeChange(restored)
                    }
                }
            )
        }
        item {
            SettingClickItem(
                icon = Icons.Filled.Palette,
                title = "App Theme",
                subtitle = selectedTheme,
                onClick = { showThemeDialog = true }
            )
        }
        item {
            Spacer(modifier = Modifier.height(20.dp))
            SettingsSection(title = "Notifications")
            Spacer(modifier = Modifier.height(12.dp))
        }
        item {
            // Force subtitle refresh when permission/toggle changes.
            notificationStatusTick
            SettingClickItem(
                icon = Icons.Filled.Notifications,
                title = "Notifications",
                subtitle = alertsManager.notificationStatusLabel(),
                onClick = { showNotificationsDialog = true }
            )
        }
        item {
            Spacer(modifier = Modifier.height(20.dp))
            SettingsSection(title = "Account")
            Spacer(modifier = Modifier.height(12.dp))
        }
        item {
            val identity = AuthSessionManager.getCachedIdentity()
            SettingClickItem(
                icon = Icons.Filled.Person,
                title = "Profile",
                subtitle = identity.displayLabel(),
                onClick = { showProfileDialog = true }
            )
        }
        
        // Biometric Authentication Toggle
        if (biometricAuthManager != null) {
            val biometricStatus = biometricAuthManager.isBiometricAvailable()
            val biometricEnabled = biometricAuthManager.isBiometricEnabled()
            
            item {
                SettingItem(
                    icon = Icons.Filled.Fingerprint,
                    title = "Biometric Lock",
                    subtitle = when (biometricStatus) {
                        BiometricStatus.AVAILABLE -> if (biometricEnabled) "Enabled" else "Disabled"
                        else -> biometricStatus.getMessage()
                    },
                    value = biometricEnabled,
                    onValueChange = { enabled ->
                        if (biometricStatus == BiometricStatus.AVAILABLE) {
                            biometricAuthManager.setBiometricEnabled(enabled)
                        }
                    },
                    enabled = biometricStatus == BiometricStatus.AVAILABLE
                )
            }
        }
        
        item {
            SettingClickItem(
                icon = Icons.Filled.Lock,
                title = "Security",
                subtitle = "Change account password",
                onClick = { showSecurityDialog = true }
            )
        }
        item {
            SettingClickItem(
                icon = Icons.Filled.Devices,
                title = "Manage Sessions",
                subtitle = "View and revoke active sessions",
                onClick = {
                    showSessionsDialog = true
                    loadSessions()
                }
            )
        }
        item {
            SettingClickItem(
                icon = Icons.AutoMirrored.Filled.Logout,
                title = "Logout All Devices",
                subtitle = "Sign out from all devices",
                onClick = { showLogoutAllDialog = true }
            )
        }
        item {
            SettingClickItem(
                icon = Icons.Filled.DeleteForever,
                title = "Delete Account",
                subtitle = "Permanently remove your account and data",
                onClick = { showDeleteAccountDialog = true },
                tintColor = LocalAppTheme.current.negative
            )
        }
        item {
            Spacer(modifier = Modifier.height(20.dp))
            SettingsSection(title = "About")
            Spacer(modifier = Modifier.height(12.dp))
        }
        item {
            SettingClickItem(
                icon = Icons.Filled.Info,
                title = "About BYSEL",
                subtitle = "Version ${BuildConfig.VERSION_NAME}",
                onClick = { showAboutDialog = true }
            )
        }
        item {
            SettingClickItem(
                icon = Icons.Filled.PrivacyTip,
                title = "Privacy Policy",
                subtitle = "How we handle your data",
                onClick = { legalDocument = com.bysel.trader.ui.components.LegalDocument.Privacy }
            )
        }
        item {
            SettingClickItem(
                icon = Icons.Filled.Gavel,
                title = "Terms of Service",
                subtitle = "Rules for using BYSEL",
                onClick = { legalDocument = com.bysel.trader.ui.components.LegalDocument.Terms }
            )
        }
        item {
            SettingClickItem(
                icon = Icons.Filled.Description,
                title = "Open Source Licenses",
                subtitle = "Third-party attributions",
                onClick = { legalDocument = com.bysel.trader.ui.components.LegalDocument.Licenses }
            )
        }
        item {
            SettingClickItem(
                icon = Icons.Filled.Public,
                title = "Visit Website",
                subtitle = "Open official website",
                onClick = { openWebsite = true }
            )
        }
        item {
            SettingClickItem(
                icon = Icons.Filled.Feedback,
                title = "Send Feedback",
                subtitle = "Help us improve",
                onClick = { showFeedbackDialog = true }
            )
        }
        item {
            SettingClickItem(
                icon = Icons.Filled.Tune,
                title = "Heatmap Refresh Interval",
                subtitle = "${localHeatmapInterval / 1000}s",
                onClick = { showIntervalDialog = true }
            )
        }
        item {
            SettingItem(
                icon = Icons.Filled.ShowChart,
                title = "Live quote stream",
                subtitle = if (liveQuotesEnabled) {
                    "WebSocket ticks while Trade or Detail is open"
                } else {
                    "Off — REST snapshots only (prices can lag ~20s)"
                },
                value = liveQuotesEnabled,
                onValueChange = onLiveQuotesChange,
            )
        }
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { showLogoutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LocalAppTheme.current.negative),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Logout", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun ManageSessionsDialog(
    sessions: List<AuthSessionItem>,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onRevokeSession: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalAppTheme.current.card,
        title = {
            Text(
                text = "Manage Sessions",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = LocalAppTheme.current.text
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = LocalAppTheme.current.primary)
                    }
                } else if (!error.isNullOrBlank()) {
                    Text(
                        text = error,
                        color = LocalAppTheme.current.negative,
                        fontSize = 12.sp
                    )
                } else if (sessions.isEmpty()) {
                    Text(
                        text = "No active sessions found.",
                        color = LocalAppTheme.current.textSecondary,
                        fontSize = 12.sp
                    )
                } else {
                    sessions.forEach { session ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.surface),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Session #${session.session_id}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = LocalAppTheme.current.text
                                )
                                Text(
                                    text = "Created: ${session.created_at}",
                                    fontSize = 11.sp,
                                    color = LocalAppTheme.current.textSecondary
                                )
                                if (!session.last_used_at.isNullOrBlank()) {
                                    Text(
                                        text = "Last used: ${session.last_used_at}",
                                        fontSize = 11.sp,
                                        color = LocalAppTheme.current.textSecondary
                                    )
                                }
                                Text(
                                    text = "Expires: ${session.expires_at}",
                                    fontSize = 11.sp,
                                    color = LocalAppTheme.current.textSecondary
                                )
                                if (!session.client_ip.isNullOrBlank()) {
                                    Text(
                                        text = "IP: ${session.client_ip}",
                                        fontSize = 11.sp,
                                        color = LocalAppTheme.current.textSecondary
                                    )
                                }
                                if (!session.device_info.isNullOrBlank()) {
                                    Text(
                                        text = "Device: ${session.device_info}",
                                        fontSize = 11.sp,
                                        color = LocalAppTheme.current.textSecondary
                                    )
                                }
                                TextButton(
                                    onClick = { onRevokeSession(session.session_id) },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Revoke", color = LocalAppTheme.current.negative)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRefresh, enabled = !isLoading) {
                Text("Refresh", color = LocalAppTheme.current.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = LocalAppTheme.current.textSecondary)
            }
        }
    )
}

@Composable
fun IntervalSelectionDialog(selectedInterval: Int, onIntervalSelected: (Int) -> Unit, onDismiss: () -> Unit) {
    val intervals = listOf(5_000, 10_000, 15_000, 30_000)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalAppTheme.current.card,
        title = {
            Text("Select Heatmap Refresh Interval", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = LocalAppTheme.current.text)
        },
        text = {
            Column {
                intervals.forEach { interval ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onIntervalSelected(interval) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedInterval == interval,
                            onClick = { onIntervalSelected(interval) }
                        )
                        Text("${interval / 1000}s", fontSize = 14.sp, color = LocalAppTheme.current.text, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = LocalAppTheme.current.primary)
            }
        }
    )
}

@Composable
fun WebsiteDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalAppTheme.current.card,
        title = {
            Text(
                text = "Visit Website",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = LocalAppTheme.current.text
            )
        },
        text = {
            Column {
                Text("Official BYSEL website:", fontSize = 14.sp, color = LocalAppTheme.current.text)
                Text("https://www.byseltrader.com", fontSize = 12.sp, color = LocalAppTheme.current.textSecondary)
                Text("Tap 'Open' to visit in a secure in-app browser tab.", fontSize = 12.sp, color = LocalAppTheme.current.textSecondary)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                com.bysel.trader.util.CustomTabsLauncher.open(context, "https://www.byseltrader.com")
                onDismiss()
            }) {
                Text("Open", color = LocalAppTheme.current.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = LocalAppTheme.current.textSecondary)
            }
        }
    )
}

@Composable
private fun NotificationsSettingsDialog(
    alertsManager: AlertsManager,
    onDismiss: () -> Unit,
    onManageAlerts: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onStatusChanged: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val context = LocalContext.current
    var priceAlertsEnabled by remember {
        mutableStateOf(alertsManager.arePriceAlertNotificationsEnabled())
    }
    val permissionGranted = alertsManager.hasPostNotificationPermission()
    val systemEnabled = alertsManager.areSystemNotificationsEnabled()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.card,
        title = {
            Text(
                "Notifications",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = theme.text,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = alertsManager.notificationStatusLabel(),
                    fontSize = 13.sp,
                    color = if (alertsManager.canDeliverNotifications()) theme.positive else theme.negative,
                )
                Text(
                    text = "BYSEL uses notifications only for price alerts you create. " +
                        "We request permission when you turn banners on — alerts still save " +
                        "and show in the app if you deny. On-device checks do not require server push.",
                    fontSize = 12.sp,
                    color = theme.textSecondary,
                )

                SettingItem(
                    icon = Icons.Filled.NotificationsActive,
                    title = "Price alert banners",
                    subtitle = if (priceAlertsEnabled) "Enabled" else "Disabled",
                    value = priceAlertsEnabled,
                    onValueChange = { enabled ->
                        priceAlertsEnabled = enabled
                        alertsManager.setPriceAlertNotificationsEnabled(enabled)
                        onStatusChanged()
                    },
                )

                if (!permissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Button(
                        onClick = onRequestPermission,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                    ) {
                        Text("Allow notification permission")
                    }
                }

                if (permissionGranted && !systemEnabled) {
                    Button(
                        onClick = onOpenSystemSettings,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                    ) {
                        Text("Open system notification settings")
                    }
                }

                OutlinedButton(
                    onClick = {
                        val ok = alertsManager.sendTestNotification()
                        Toast.makeText(
                            context,
                            if (ok) "Test notification sent" else "Enable notification permission first",
                            Toast.LENGTH_SHORT,
                        ).show()
                        onStatusChanged()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Send test notification", color = theme.primary)
                }

                Button(
                    onClick = onManageAlerts,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                ) {
                    Text("Manage price alerts")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = theme.primary)
            }
        },
    )
}

@Composable
fun ThemeSelectionDialog(
    selectedTheme: String,
    onThemeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalAppTheme.current.card,
        title = {
            Text(
                text = "Select Theme",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = LocalAppTheme.current.text
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp)
            ) {
                allThemes.forEach { themeName ->
                    val theme = getTheme(themeName)
                    val isSelected = selectedTheme.equals(themeName, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .clickable { onThemeSelected(themeName) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .background(theme.surface, RoundedCornerShape(4.dp))
                                )
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .background(theme.card, RoundedCornerShape(4.dp))
                                )
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .background(theme.primary, RoundedCornerShape(4.dp))
                                )
                            }
                            Column(
                                modifier = Modifier.padding(start = 12.dp)
                            ) {
                                Text(
                                    text = themeName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = LocalAppTheme.current.text
                                )
                                Text(
                                    text = when (themeName) {
                                        "Dynamic" -> "Material You · wallpaper colors (Android 12+)"
                                        "Light" -> "Daytime · softer surfaces"
                                        "Aurora" -> "Teal comfort · long sessions"
                                        "Slate" -> "Blue-gray · reading-friendly"
                                        "Ocean", "Forest" -> "Calm dark · balanced contrast"
                                        "Cyberpunk", "Sunset", "Royal" -> "Accent-forward · short sessions"
                                        "Amoled" -> "Near-black · OLED friendly"
                                        else -> if (theme.isLight) "Light comfort" else "Dark comfort"
                                    },
                                    fontSize = 11.sp,
                                    color = LocalAppTheme.current.textSecondary
                                )
                                Row(
                                    modifier = Modifier.padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .height(4.dp)
                                            .fillMaxWidth(0.18f)
                                            .background(theme.positive, RoundedCornerShape(2.dp))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .height(4.dp)
                                            .fillMaxWidth(0.12f)
                                            .background(theme.negative, RoundedCornerShape(2.dp))
                                    )
                                }
                            }
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = LocalAppTheme.current.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = LocalAppTheme.current.primary)
            }
        }
    )
}

@Composable
fun SettingsSection(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = LocalAppTheme.current.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun SettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = byselCardColors(),
        elevation = byselCardElevation(),
        border = byselCardBorder(),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) LocalAppTheme.current.primary else LocalAppTheme.current.textSecondary,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (enabled) LocalAppTheme.current.text else LocalAppTheme.current.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = LocalAppTheme.current.textSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Switch(
                checked = value,
                onCheckedChange = if (enabled) onValueChange else { {} },
                enabled = enabled,
                modifier = Modifier.size(48.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = LocalAppTheme.current.onPrimary,
                    checkedTrackColor = LocalAppTheme.current.primary,
                    uncheckedThumbColor = LocalAppTheme.current.textSecondary,
                    uncheckedTrackColor = LocalAppTheme.current.mutedSurface,
                )
            )
        }
    }
}

@Composable
fun SettingClickItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit = {},
    tintColor: Color? = null
) {
    val color = tintColor ?: LocalAppTheme.current.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() },
        colors = byselCardColors(),
        elevation = byselCardElevation(),
        border = byselCardBorder(),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LocalAppTheme.current.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = LocalAppTheme.current.textSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = LocalAppTheme.current.textSecondary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun AboutDialog(
    onDismiss: () -> Unit,
    onOpenLegal: (com.bysel.trader.ui.components.LegalDocument) -> Unit,
) {
    val theme = LocalAppTheme.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val scroll = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = true),
        containerColor = theme.card,
        title = {
            Text(
                text = "About BYSEL",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = theme.text
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(scroll)
            ) {
                Text(
                    text = "BYSEL - Stock Trading Simulator",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.text,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME}",
                    fontSize = 12.sp,
                    color = theme.textSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "BYSEL is a modern stock trading simulator that helps you learn and practice stock trading with real market data. No real money is involved.",
                    fontSize = 12.sp,
                    color = theme.textSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "© 2026 BYSEL Services. All rights reserved.",
                    fontSize = 11.sp,
                    color = theme.textSecondary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = "Legal & Info",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                AboutLegalRow(
                    label = "Privacy Policy",
                    onClick = { onOpenLegal(com.bysel.trader.ui.components.LegalDocument.Privacy) }
                )
                AboutLegalRow(
                    label = "Terms of Service",
                    onClick = { onOpenLegal(com.bysel.trader.ui.components.LegalDocument.Terms) }
                )
                AboutLegalRow(
                    label = "Open Source Licenses",
                    onClick = { onOpenLegal(com.bysel.trader.ui.components.LegalDocument.Licenses) }
                )
                AboutLegalRow(
                    label = "Contact: bysel.trader@gmail.com",
                    onClick = { uriHandler.openUri("mailto:bysel.trader@gmail.com") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = theme.primary)
            }
        }
    )
}

@Composable
private fun AboutLegalRow(label: String, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = theme.primary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = theme.primary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun SimpleDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}

@Composable
fun ProfileDialog(
    authRepository: AuthRepository,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    var username by remember { mutableStateOf(AuthSessionManager.getCachedUsername().orEmpty()) }
    var email by remember { mutableStateOf(AuthSessionManager.getCachedEmail().orEmpty()) }
    var mobileNumber by remember { mutableStateOf(AuthSessionManager.getCachedMobileNumber().orEmpty()) }
    var createdAt by remember { mutableStateOf("") }
    var userId by remember { mutableStateOf(AuthSessionManager.getUserId()) }

    fun loadProfile() {
        scope.launch {
            loading = true
            errorMessage = null
            successMessage = null
            // Keep last-known identity visible while refreshing.
            val cached = AuthSessionManager.getCachedIdentity()
            if (username.isBlank()) username = cached.username.orEmpty()
            if (email.isBlank()) email = cached.email.orEmpty()
            if (mobileNumber.isBlank()) mobileNumber = cached.mobileNumber.orEmpty()
            userId = cached.userId
            when (val result = authRepository.getProfile()) {
                is Result.Success -> {
                    val profile = result.data
                    username = profile.username
                    email = profile.email
                    mobileNumber = profile.mobileNumber.orEmpty()
                    createdAt = profile.createdAt.orEmpty()
                    userId = profile.user_id
                    errorMessage = null
                }
                is Result.Error -> {
                    val msg = result.message
                    errorMessage = when {
                        msg.contains("Token expired", ignoreCase = true) ||
                            msg.contains("Session expired", ignoreCase = true) ||
                            msg.contains("Invalid token", ignoreCase = true) ->
                            "Session expired. Sign out and sign in again to refresh your profile."
                        else -> msg
                    }
                }
                else -> Unit
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        loadProfile()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalAppTheme.current.card,
        title = { Text("Profile", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = LocalAppTheme.current.text) },
        text = {
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (userId != null) {
                        Text(
                            text = "Account ID #$userId",
                            color = LocalAppTheme.current.textSecondary,
                            fontSize = 12.sp,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (!errorMessage.isNullOrBlank() && username.isBlank() && email.isBlank()) {
                        Text(
                            text = errorMessage ?: "",
                            color = LocalAppTheme.current.negative,
                            fontSize = 12.sp,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { loadProfile() }) {
                            Text("Retry", color = LocalAppTheme.current.primary)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            errorMessage = null
                            successMessage = null
                        },
                        label = { Text("Username") },
                        singleLine = true,
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            errorMessage = null
                            successMessage = null
                        },
                        label = { Text("Email") },
                        singleLine = true,
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = mobileNumber,
                        onValueChange = {
                            mobileNumber = it
                            errorMessage = null
                            successMessage = null
                        },
                        label = { Text("Mobile Number") },
                        singleLine = true,
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (mobileNumber.isNotBlank() && email.contains("@bysel.com", ignoreCase = true)) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Signed in with mobile OTP. You can set a display username and email here.",
                            fontSize = 11.sp,
                            color = LocalAppTheme.current.textSecondary,
                        )
                    }
                    if (createdAt.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Member since: $createdAt",
                            fontSize = 12.sp,
                            color = LocalAppTheme.current.textSecondary,
                        )
                    }
                    if (!errorMessage.isNullOrBlank() && username.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = LocalAppTheme.current.negative,
                            fontSize = 12.sp,
                        )
                    }
                    if (!successMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = successMessage ?: "",
                            color = LocalAppTheme.current.positive,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !loading && !saving,
                onClick = {
                    val trimmedUsername = username.trim()
                    val trimmedEmail = email.trim()
                    val trimmedMobile = mobileNumber.trim()

                    if (trimmedUsername.isEmpty()) {
                        errorMessage = "Username is required"
                        return@TextButton
                    }

                    if (trimmedEmail.isEmpty()) {
                        errorMessage = "Email is required"
                        return@TextButton
                    }

                    saving = true
                    scope.launch {
                        when (
                            val result = authRepository.updateProfile(
                                username = trimmedUsername,
                                email = trimmedEmail,
                                mobileNumber = trimmedMobile.ifBlank { null },
                            )
                        ) {
                            is Result.Success -> {
                                val profile = result.data
                                username = profile.username
                                email = profile.email
                                mobileNumber = profile.mobileNumber.orEmpty()
                                createdAt = profile.createdAt.orEmpty()
                                errorMessage = null
                                successMessage = "Profile updated"
                            }
                            is Result.Error -> {
                                errorMessage = result.message
                                successMessage = null
                            }
                            else -> Unit
                        }
                        saving = false
                    }
                }
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save", color = LocalAppTheme.current.primary)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) {
                Text("Close", color = LocalAppTheme.current.textSecondary)
            }
        }
    )
}

@Composable
fun SecurityDialog(
    authRepository: AuthRepository,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val theme = LocalAppTheme.current
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var currentPasswordVisible by remember { mutableStateOf(false) }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    fun resetMessage() {
        message = null
        isError = false
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = theme.text,
        unfocusedTextColor = theme.text,
        disabledTextColor = theme.textSecondary,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedBorderColor = theme.primary,
        unfocusedBorderColor = theme.textSecondary.copy(alpha = 0.6f),
        focusedLabelColor = theme.primary,
        unfocusedLabelColor = theme.textSecondary,
        cursorColor = theme.primary,
        focusedTrailingIconColor = theme.textSecondary,
        unfocusedTrailingIconColor = theme.textSecondary,
    )

    AlertDialog(
        onDismissRequest = {
            if (!loading) onDismiss()
        },
        containerColor = theme.card,
        title = {
            Text(
                "Change password",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = theme.text,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "Enter your current password, then choose a new one (min 6 characters). " +
                        "Other devices will be signed out; this device stays signed in.",
                    fontSize = 13.sp,
                    color = theme.textSecondary,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = {
                        currentPassword = it
                        resetMessage()
                    },
                    label = { Text("Current password") },
                    singleLine = true,
                    enabled = !loading,
                    visualTransformation = if (currentPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { currentPasswordVisible = !currentPasswordVisible }) {
                            Icon(
                                imageVector = if (currentPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (currentPasswordVisible) "Hide current password" else "Show current password",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        resetMessage()
                    },
                    label = { Text("New password") },
                    singleLine = true,
                    enabled = !loading,
                    visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                            Icon(
                                imageVector = if (newPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (newPasswordVisible) "Hide new password" else "Show new password",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        resetMessage()
                    },
                    label = { Text("Confirm new password") },
                    singleLine = true,
                    enabled = !loading,
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                            Icon(
                                imageVector = if (confirmPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (confirmPasswordVisible) "Hide confirmation password" else "Show confirmation password",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                )
                if (!message.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = message.orEmpty(),
                        fontSize = 12.sp,
                        color = if (isError) theme.negative else theme.positive,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (loading) return@Button
                    val current = currentPassword.trim()
                    val next = newPassword.trim()
                    val confirm = confirmPassword.trim()
                    when {
                        current.isBlank() -> {
                            isError = true
                            message = "Current password is required"
                        }
                        next.length < 6 -> {
                            isError = true
                            message = "New password must be at least 6 characters"
                        }
                        next.length > 72 -> {
                            isError = true
                            message = "New password must be 72 characters or less"
                        }
                        next != confirm -> {
                            isError = true
                            message = "New passwords do not match"
                        }
                        current == next -> {
                            isError = true
                            message = "New password must be different from current password"
                        }
                        else -> {
                            loading = true
                            resetMessage()
                            scope.launch {
                                when (val result = authRepository.changePassword(current, next)) {
                                    is Result.Success -> {
                                        isError = false
                                        message = "Password updated. You can keep using the app on this device."
                                        currentPassword = ""
                                        newPassword = ""
                                        confirmPassword = ""
                                    }
                                    is Result.Error -> {
                                        isError = true
                                        message = result.message
                                    }
                                    else -> Unit
                                }
                                loading = false
                            }
                        }
                    }
                },
                enabled = !loading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.primary,
                    contentColor = theme.onPrimary,
                ),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        color = theme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Text("Update password")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!loading) onDismiss() }) {
                Text("Close", color = theme.primary)
            }
        },
    )
}

@Composable
fun FeedbackDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalAppTheme.current.card,
        title = { Text("Feedback", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = LocalAppTheme.current.text) },
        text = { Column { Text("We value your feedback!", fontSize = 14.sp, color = LocalAppTheme.current.text); Text("Please email us at bysel.trader@gmail.com", fontSize = 12.sp, color = LocalAppTheme.current.textSecondary) } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = LocalAppTheme.current.primary) } }
    )
}
