
package com.bysel.trader.ui.screens
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.BuildConfig
import com.bysel.trader.ui.theme.allThemes
import com.bysel.trader.ui.theme.getTheme
import com.bysel.trader.ui.theme.LocalAppTheme

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
fun SettingsScreen(
    onThemeChange: (String) -> Unit = {},
    currentTheme: String = "Default"
) {
    var darkMode by remember { mutableStateOf(true) }
    var enableNotifications by remember { mutableStateOf(true) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var selectedTheme by remember { mutableStateOf(currentTheme) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showSecurityDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var openWebsite by remember { mutableStateOf(false) }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            selectedTheme = selectedTheme,
            onThemeSelected = { theme ->
                selectedTheme = theme
                onThemeChange(theme)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showAboutDialog) {
        AboutDialog { showAboutDialog = false }
    }
    if (showProfileDialog) {
        SimpleDialog(title = "Profile", message = "Profile screen coming soon.", onDismiss = { showProfileDialog = false })
    }
    if (showSecurityDialog) {
        SimpleDialog(title = "Security", message = "Security settings coming soon.", onDismiss = { showSecurityDialog = false })
    }
    if (showFeedbackDialog) {
        SimpleDialog(title = "Feedback", message = "Feedback form coming soon.", onDismiss = { showFeedbackDialog = false })
    }
    if (showLogoutDialog) {
        SimpleDialog(title = "Logout", message = "Logout functionality coming soon.", onDismiss = { showLogoutDialog = false })
    }
    if (openWebsite) {
        // TODO: Implement actual website opening logic
        SimpleDialog(title = "Visit Website", message = "Opening website...", onDismiss = { openWebsite = false })
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
                subtitle = if (darkMode) "Enabled" else "Disabled",
                value = darkMode,
                onValueChange = { darkMode = it }
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
            SettingItem(
                icon = Icons.Filled.Notifications,
                title = "Push Notifications",
                subtitle = "Get alerts for price changes",
                value = enableNotifications,
                onValueChange = { enableNotifications = it }
            )
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
            SettingsSection(title = "Account")
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            SettingClickItem(
                icon = Icons.Filled.Person,
                title = "Profile",
                subtitle = "View and edit profile",
                onClick = { showProfileDialog = true }
            )
        }

        item {
            SettingClickItem(
                icon = Icons.Filled.Lock,
                title = "Security",
                subtitle = "Manage password and privacy",
                onClick = { showSecurityDialog = true }
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
    @Composable
    fun SimpleDialog(title: String, message: String, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = LocalAppTheme.current.card,
            title = {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppTheme.current.text
                )
            },
            text = {
                Text(
                    text = message,
                    fontSize = 14.sp,
                    color = LocalAppTheme.current.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Close", color = LocalAppTheme.current.primary)
                }
            }
        )
    }
    }
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
                    .padding(vertical = 16.dp)
            ) {
                allThemes.forEach { themeName ->
                    val theme = getTheme(themeName.lowercase())
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(theme.primary, RoundedCornerShape(4.dp))
                            )
                            Column(
                                modifier = Modifier.padding(start = 12.dp)
                            ) {
                                Text(
                                    text = theme.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = LocalAppTheme.current.text
                                )
                                Box(
                                    modifier = Modifier
                                        .height(4.dp)
                                        .fillMaxWidth(0.3f)
                                        .background(theme.positive, RoundedCornerShape(2.dp))
                                        .padding(top = 4.dp)
                                )
                            }
                        }
                        if (selectedTheme == themeName) {
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
    onValueChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
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
                    tint = LocalAppTheme.current.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LocalAppTheme.current.text
                    )
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = LocalAppTheme.current.textSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Switch(
                checked = value,
                onCheckedChange = onValueChange,
                modifier = Modifier.size(48.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF00B050),
                    checkedTrackColor = Color(0xFF1B5E20)
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
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = LocalAppTheme.current.card),
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
                    tint = LocalAppTheme.current.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LocalAppTheme.current.text
                    )
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = LocalAppTheme.current.textSecondary,
                        modifier = Modifier.padding(top = 4.dp)
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
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalAppTheme.current.card,
        title = {
            Text(
                text = "About BYSEL",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = LocalAppTheme.current.text
            )
        },
        text = {
            Column {
                Text(
                    text = "BYSEL - Stock Trading Simulator",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalAppTheme.current.text,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME}",
                    fontSize = 12.sp,
                    color = LocalAppTheme.current.textSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = "BYSEL is a modern stock trading simulator that helps you learn and practice stock trading with real market data.",
                    fontSize = 12.sp,
                    color = LocalAppTheme.current.textSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = "© 2026 BYSEL. All rights reserved.",
                    fontSize = 11.sp,
                    color = LocalAppTheme.current.textSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = LocalAppTheme.current.primary)
            }
        }
    )
}
