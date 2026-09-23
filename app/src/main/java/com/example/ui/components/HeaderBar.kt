package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.PrimaryBlue
import com.example.util.AppStorageHelper
import com.example.util.ProfileManager
import com.example.util.SettingsManager

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HeaderBar(
    excelName: String?,
    folderName: String?,
    isPermanentSaved: Boolean,
    onLoadSample: () -> Unit,
    onTestNotification: () -> Unit,
    onClearCache: () -> Unit,
    onOpenManual: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        val settings by SettingsManager.settings.collectAsState()
        val customLogoBitmap = remember(settings.customAppLogoTimestamp) {
            val file = AppStorageHelper.getAppLogoFile(context)
            if (file.exists() && file.length() > 0) {
                try {
                    android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                } catch (_: Throwable) {
                    null
                }
            } else null
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = AccentGreen.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (customLogoBitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = customLogoBitmap.asImageBitmap(),
                                    contentDescription = "App Logo",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            } else {
                                Text("X", color = AccentGreen, fontWeight = FontWeight.Black, fontSize = 16.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Excel & Image Vault",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Profile icon button (side of Notification icon)
                val userProfile = ProfileManager.userProfile.collectAsState().value
                val profileImageBase64 = userProfile?.profileImageBase64
                val profileBitmap: android.graphics.Bitmap? = remember(profileImageBase64) {
                    if (!profileImageBase64.isNullOrBlank()) {
                        ProfileManager.base64ToBitmap(profileImageBase64)
                    } else null
                }

                IconButton(
                    onClick = onOpenProfile,
                    modifier = Modifier.testTag("header_profile_button")
                ) {
                    if (profileBitmap != null) {
                        Surface(
                            shape = CircleShape,
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier.size(28.dp)
                        ) {
                            androidx.compose.foundation.Image(
                                bitmap = profileBitmap.asImageBitmap(),
                                contentDescription = "User Profile Picture",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "User Profile",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(
                    onClick = onTestNotification,
                    modifier = Modifier.testTag("notification_action_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Test Push Notification",
                        tint = PrimaryBlue
                    )
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("header_more_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Load Sample Demo Catalog") },
                            onClick = {
                                menuExpanded = false
                                onLoadSample()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Trigger Test Notification") },
                            onClick = {
                                menuExpanded = false
                                onTestNotification()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Clear Offline Cache") },
                            onClick = {
                                menuExpanded = false
                                onClearCache()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("👤 User Profile & Identity") },
                            onClick = {
                                menuExpanded = false
                                onOpenProfile()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("📖 User Manual (PDF)") },
                            onClick = {
                                menuExpanded = false
                                onOpenManual()
                            }
                        )
                    }
                }
            }
        }

        // Persistent Path & Offline Status Badges
        if (excelName != null || folderName != null) {
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (excelName != null) {
                    PermanentBadge(
                        icon = Icons.Default.TableChart,
                        label = "Excel: $excelName",
                        isPermanent = isPermanentSaved
                    )
                }
                if (folderName != null) {
                    PermanentBadge(
                        icon = Icons.Default.Folder,
                        label = "Folder: $folderName",
                        isPermanent = isPermanentSaved
                    )
                }
            }
        }
    }
}

@Composable
private fun PermanentBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isPermanent: Boolean
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isPermanent) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(AccentGreen, CircleShape)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Permanent",
                    fontSize = 10.sp,
                    color = AccentGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
