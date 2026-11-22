package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

enum class VaultSubMode {
    HUB,
    IMG_VAULT,
    VID_VAULT
}

@Composable
fun VaultHubScreen(
    vaultViewModel: VaultViewModel,
    vidVaultViewModel: VidVaultViewModel,
    modifier: Modifier = Modifier
) {
    var subMode by rememberSaveable { mutableStateOf(VaultSubMode.HUB) }
    val imgState by vaultViewModel.uiState.collectAsStateWithLifecycle()
    val vidState by vidVaultViewModel.uiState.collectAsStateWithLifecycle()

    when (subMode) {
        VaultSubMode.HUB -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF6C5CE7), Color(0xFF00CEC9)))
                            )
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = "Encrypted Vaults",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Encrypted Vault Hub",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Choose IMG Vault for photos or VID Vault for videos",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Option 1: IMG Vault (unchanged engine & UI)
                VaultOptionCard(
                    title = "IMG Vault",
                    subtitle = "Encrypted Photo Vault • AES-256-GCM • Up to 10 GB",
                    details = "Photos, folders, batch import, fullscreen viewer, slideshow & SSI sync.",
                    badgeText = if (imgState.isUnlocked) "UNLOCKED" else "LOCKED",
                    badgeColor = if (imgState.isUnlocked) Color(0xFF00B894) else Color(0xFF6C5CE7),
                    containerCountText = "${imgState.availableVaultFiles.size} .vault file(s)",
                    gradientColors = listOf(Color(0xFF6C5CE7), Color(0xFF0984E3)),
                    icon = Icons.Default.Image,
                    isUnlocked = imgState.isUnlocked,
                    testTagName = "vault_option_img",
                    onClick = { subMode = VaultSubMode.IMG_VAULT }
                )

                // Option 2: VID Vault (30 GB Max, Ultra-Low RAM, Direct Disk Streaming)
                VaultOptionCard(
                    title = "VID Vault",
                    subtitle = "Encrypted Video Vault • Up to 30 GB • All Formats",
                    details = "MP4, MKV, MOV, AVI, WEBM & more • Direct zero-RAM disk player, folders & quick Share.",
                    badgeText = if (vidState.isUnlocked) "UNLOCKED" else "LOCKED",
                    badgeColor = if (vidState.isUnlocked) Color(0xFF00B894) else Color(0xFF00CEC9),
                    containerCountText = "${vidState.availableVaults.size} .vidvault file(s)",
                    gradientColors = listOf(Color(0xFF00CEC9), Color(0xFF6C5CE7)),
                    icon = Icons.Default.Movie,
                    isUnlocked = vidState.isUnlocked,
                    testTagName = "vault_option_vid",
                    onClick = { subMode = VaultSubMode.VID_VAULT }
                )

                // Shared Storage Info Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Shared Storage Directory (IMG Vault & VID Vault)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = vidState.vaultLocationPath,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        VaultSubMode.IMG_VAULT -> {
            BackHandler { subMode = VaultSubMode.HUB }
            Column(modifier = modifier.fillMaxSize()) {
                VaultSubModeHeaderBar(
                    activeMode = VaultSubMode.IMG_VAULT,
                    onBackToHub = { subMode = VaultSubMode.HUB },
                    onSwitchMode = { subMode = it }
                )
                // Untouched IMG Vault Screen
                VaultScreen(
                    viewModel = vaultViewModel,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        VaultSubMode.VID_VAULT -> {
            BackHandler { subMode = VaultSubMode.HUB }
            Column(modifier = modifier.fillMaxSize()) {
                VaultSubModeHeaderBar(
                    activeMode = VaultSubMode.VID_VAULT,
                    onBackToHub = { subMode = VaultSubMode.HUB },
                    onSwitchMode = { subMode = it }
                )
                VidVaultScreen(
                    viewModel = vidVaultViewModel,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun VaultOptionCard(
    title: String,
    subtitle: String,
    details: String,
    badgeText: String,
    badgeColor: Color,
    containerCountText: String,
    gradientColors: List<Color>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isUnlocked: Boolean,
    testTagName: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, gradientColors.first().copy(alpha = 0.65f)),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTagName)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(gradientColors))
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = badgeColor.copy(alpha = 0.18f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = if (isUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = badgeColor,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = badgeText,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeColor
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = gradientColors.first()
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Open $title",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = details,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ) {
                Text(
                    text = containerCountText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun VaultSubModeHeaderBar(
    activeMode: VaultSubMode,
    onBackToHub: () -> Unit,
    onSwitchMode: (VaultSubMode) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onBackToHub)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("vault_back_to_hub_btn")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Vault Hub",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Vault Hub",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val isImg = activeMode == VaultSubMode.IMG_VAULT
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isImg) Color(0xFF6C5CE7) else Color.Transparent,
                        border = if (isImg) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSwitchMode(VaultSubMode.IMG_VAULT) }
                            .testTag("vault_switch_img")
                    ) {
                        Text(
                            text = "🖼️ IMG Vault",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isImg) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    val isVid = activeMode == VaultSubMode.VID_VAULT
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isVid) Color(0xFF00CEC9) else Color.Transparent,
                        border = if (isVid) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSwitchMode(VaultSubMode.VID_VAULT) }
                            .testTag("vault_switch_vid")
                    ) {
                        Text(
                            text = "🎬 VID Vault",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isVid) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        }
    }
}
