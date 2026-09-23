package com.example.ui.settings

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.util.AppStorageHelper
import com.example.util.SettingsManager
import java.io.File
import java.io.FileOutputStream

data class DisguisePreset(
    val name: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun AppLogoDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val settings by SettingsManager.settings.collectAsState()

    var currentLogoBitmap by remember(settings.customAppLogoTimestamp) {
        mutableStateOf<Bitmap?>(run {
            val file = AppStorageHelper.getAppLogoFile(context)
            if (file.exists() && file.length() > 0) {
                try {
                    BitmapFactory.decodeFile(file.absolutePath)
                } catch (_: Throwable) {
                    null
                }
            } else null
        })
    }

    val disguisePresets = remember {
        listOf(
            DisguisePreset("Excel", "Spreadsheets & Tables", Icons.Default.GridOn, Color(0xFF107C41)),
            DisguisePreset("Calculator", "Scientific Math Tool", Icons.Default.Calculate, Color(0xFFE65100)),
            DisguisePreset("Notes", "Private Memo Pad", Icons.Default.NoteAlt, Color(0xFFF57F17)),
            DisguisePreset("Weather", "Forecast & Radar", Icons.Default.Cloud, Color(0xFF0288D1)),
            DisguisePreset("Clock", "Alarm & Timer", Icons.Default.Schedule, Color(0xFF5E35B1)),
            DisguisePreset("Music", "Audio Player", Icons.Default.MusicNote, Color(0xFFD81B60))
        )
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val targetFile = AppStorageHelper.getAppLogoFile(context)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bmp = BitmapFactory.decodeStream(input)
                    if (bmp != null) {
                        FileOutputStream(targetFile).use { out ->
                            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                        currentLogoBitmap = bmp
                        SettingsManager.setCustomAppLogoTimestamp(System.currentTimeMillis())
                        SettingsManager.setLauncherDisguise("Custom")
                        SettingsManager.setLauncherCustomIconUri(uri.toString())
                        Toast.makeText(context, "Custom icon image set successfully!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Throwable) {
                Toast.makeText(context, "Failed to set custom icon: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(22.dp)),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
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
                                    Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "Launcher Icon Disguise",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Disguise or set custom icon image",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("btn_close_logo_dialog")) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

                // Current Icon Preview
                Text(
                    "Active Launcher Icon Preview",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.size(90.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (currentLogoBitmap != null) {
                            Image(
                                bitmap = currentLogoBitmap!!.asImageBitmap(),
                                contentDescription = "Custom Icon Image",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(20.dp))
                            )
                        } else {
                            val activePreset = disguisePresets.find { it.name == settings.launcherDisguiseName }
                                ?: disguisePresets.first()
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = activePreset.icon,
                                    contentDescription = null,
                                    tint = activePreset.color,
                                    modifier = Modifier.size(38.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    activePreset.name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (currentLogoBitmap != null) "Current: Custom Photo Icon" else "Current: ${settings.launcherDisguiseName} Disguise",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Custom Icon Image Set Option (User Request)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Custom Icon Image Set Option",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Select any photo or picture from your phone gallery to use as your custom launcher & app icon image.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_set_custom_icon_image")
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Set Custom Icon Image", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Preset Disguises Section
                Text(
                    "Or Choose Icon Disguise Preset",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    disguisePresets.forEach { preset ->
                        val isSelected = currentLogoBitmap == null && settings.launcherDisguiseName == preset.name
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    try {
                                        val file = AppStorageHelper.getAppLogoFile(context)
                                        if (file.exists()) file.delete()
                                        currentLogoBitmap = null
                                        SettingsManager.setCustomAppLogoTimestamp(System.currentTimeMillis())
                                        SettingsManager.setLauncherDisguise(preset.name)
                                        SettingsManager.setLauncherCustomIconUri("")
                                        Toast.makeText(context, "Disguise set to ${preset.name}", Toast.LENGTH_SHORT).show()
                                    } catch (e: Throwable) {
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .testTag("disguise_preset_${preset.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = preset.color.copy(alpha = 0.15f),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = preset.icon,
                                            contentDescription = null,
                                            tint = preset.color,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(preset.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(preset.subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Reset Button
                if (currentLogoBitmap != null || settings.launcherDisguiseName != "Excel") {
                    OutlinedButton(
                        onClick = {
                            try {
                                val file = AppStorageHelper.getAppLogoFile(context)
                                if (file.exists()) file.delete()
                                currentLogoBitmap = null
                                SettingsManager.setCustomAppLogoTimestamp(System.currentTimeMillis())
                                SettingsManager.setLauncherDisguise("Excel")
                                SettingsManager.setLauncherCustomIconUri("")
                                Toast.makeText(context, "Restored default Excel disguise", Toast.LENGTH_SHORT).show()
                            } catch (e: Throwable) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().testTag("btn_reset_default_disguise")
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset to Default Excel Disguise")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}
