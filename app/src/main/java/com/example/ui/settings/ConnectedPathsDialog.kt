package com.example.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.util.AppSecurityManager
import com.example.util.AppStorageHelper
import java.io.File

data class PathItem(
    val title: String,
    val description: String,
    val path: String,
    val exists: Boolean,
    val fileCount: Int
)

@Composable
fun ConnectedPathsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val securityConfig by AppSecurityManager.securityConfig.collectAsState()

    var isVerified by remember { mutableStateOf(!securityConfig.isPinEnabled) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    val pathsList = remember(isVerified) {
        if (!isVerified) emptyList()
        else {
            val dedicated = AppStorageHelper.getDedicatedMediaDir(context)
            val exVault = AppStorageHelper.getExVaultDir(context)
            val vaultContainers = AppStorageHelper.getVaultStorageDir(context)
            val dualVault = AppStorageHelper.getDualVaultDir(context)
            val securityDir = AppStorageHelper.getSecurityDir(context)
            val fpDir = AppStorageHelper.getFingerprintDir(context)
            val sshowSecure = AppStorageHelper.getSShowSecureDir(context)
            val sshowStored = AppStorageHelper.getSShowStoredDir(context)
            val profileDir = AppStorageHelper.getProfileMediaDir(context)
            val dbFile = context.getDatabasePath("app_database")

            fun countFiles(f: File) = f.listFiles()?.count { it.isFile } ?: 0

            listOf(
                PathItem(
                    title = "Dedicated Media Root",
                    description = "Root media directory accessible without scoped storage limits",
                    path = dedicated.absolutePath,
                    exists = dedicated.exists(),
                    fileCount = dedicated.listFiles()?.size ?: 0
                ),
                PathItem(
                    title = "EX_Vault (Decrypted Images)",
                    description = "Active decrypted vault photos ready for viewing & catalog link",
                    path = exVault.absolutePath,
                    exists = exVault.exists(),
                    fileCount = countFiles(exVault)
                ),
                PathItem(
                    title = "Vault Containers (.dat)",
                    description = "Secure encrypted DAT container storage directory",
                    path = vaultContainers.absolutePath,
                    exists = vaultContainers.exists(),
                    fileCount = countFiles(vaultContainers)
                ),
                PathItem(
                    title = "Dual Combined Vault",
                    description = "Shared dual user combined vault media storage",
                    path = dualVault.absolutePath,
                    exists = dualVault.exists(),
                    fileCount = countFiles(dualVault)
                ),
                PathItem(
                    title = "Security & PIN Credentials",
                    description = "SHA-256 PIN salts, anti-screenshot flags and crypto hashes",
                    path = securityDir.absolutePath,
                    exists = securityDir.exists(),
                    fileCount = countFiles(securityDir)
                ),
                PathItem(
                    title = "Registered Fingerprint Path",
                    description = "Encrypted biometric token and emergency recovery key file",
                    path = fpDir.absolutePath,
                    exists = fpDir.exists(),
                    fileCount = countFiles(fpDir)
                ),
                PathItem(
                    title = "SShow Secure Storage",
                    description = "Encrypted slideshow presentation data",
                    path = sshowSecure.absolutePath,
                    exists = sshowSecure.exists(),
                    fileCount = countFiles(sshowSecure)
                ),
                PathItem(
                    title = "SShow Stored Images",
                    description = "Offline preserved slideshow image cache",
                    path = sshowStored.absolutePath,
                    exists = sshowStored.exists(),
                    fileCount = countFiles(sshowStored)
                ),
                PathItem(
                    title = "User Profile Media",
                    description = "Profile photo bitmap and account metadata",
                    path = profileDir.absolutePath,
                    exists = profileDir.exists(),
                    fileCount = countFiles(profileDir)
                ),
                PathItem(
                    title = "SQLite Catalog Database",
                    description = "Room database file storing Excel row items & indexed codes",
                    path = dbFile.absolutePath,
                    exists = dbFile.exists(),
                    fileCount = if (dbFile.exists()) 1 else 0
                ),
                PathItem(
                    title = "Activity Timeline Log",
                    description = "Offline audit logs at media/activity/activity.json",
                    path = File(dedicated, "activity/activity.json").absolutePath,
                    exists = File(dedicated, "activity/activity.json").exists(),
                    fileCount = if (File(dedicated, "activity/activity.json").exists()) 1 else 0
                )
            )
        }
    }

    androidx.activity.compose.BackHandler {
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FolderSpecial,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Connected Storage Paths",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isVerified) "Verified access to system & media folders" else "Authentication required",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("btn_close_paths_dialog")) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)

                if (!isVerified) {
                    // Lock verification prompt
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Enter 6-Digit App PIN",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Storage paths contain sensitive vault and database locations.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = {
                                if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                    pinInput = it
                                    pinError = null
                                    if (it.length == 6) {
                                        if (AppSecurityManager.verifyPin(it, context)) {
                                            isVerified = true
                                        } else {
                                            pinError = "Incorrect PIN"
                                        }
                                    }
                                }
                            },
                            label = { Text("6-Digit PIN") },
                            isError = pinError != null,
                            supportingText = { pinError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .testTag("input_paths_pin")
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (AppSecurityManager.verifyPin(pinInput, context)) {
                                    isVerified = true
                                } else {
                                    pinError = "Incorrect PIN"
                                }
                            },
                            enabled = pinInput.length == 6,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(0.7f).testTag("btn_verify_paths_pin")
                        ) {
                            Text("Verify & View Paths", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Paths List
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(pathsList) { item ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.title,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = item.description,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (item.exists) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                                modifier = Modifier.padding(end = 6.dp)
                                            ) {
                                                Text(
                                                    text = if (item.exists) "${item.fileCount} items" else "Missing",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (item.exists) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { copyToClipboard(item.title, item.path) },
                                                modifier = Modifier.size(32.dp).testTag("copy_path_${item.title}")
                                            ) {
                                                Icon(
                                                    Icons.Default.ContentCopy,
                                                    contentDescription = "Copy Path",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = item.path,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val allPaths = pathsList.joinToString("\n\n") { "${it.title}:\n${it.path}" }
                                copyToClipboard("All Storage Paths", allPaths)
                            },
                            modifier = Modifier.weight(1f).testTag("btn_copy_all_paths")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy All Paths", fontSize = 12.sp)
                        }

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).testTag("btn_done_paths")
                        ) {
                            Text("Done", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
