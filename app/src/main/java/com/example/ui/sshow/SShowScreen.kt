package com.example.ui.sshow

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.DarkBorder
import com.example.util.SShowImage
import java.io.File

@Composable
fun SShowScreen(
    viewModel: SShowViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Activity Result Launchers
    val encryptImagesPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onImagesSelectedForEncrypt(uris)
        }
    }

    val decryptFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onSecureFileSelected(uri)
        }
    }

    var pendingAddSecureUri by remember { mutableStateOf<Uri?>(null) }
    val addSecureFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingAddSecureUri = uri
        }
    }

    val addImagesPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty() && pendingAddSecureUri != null) {
            viewModel.onAddImagesSelected(pendingAddSecureUri, uris)
        }
    }

    val shareImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.onShareImageSelected(uri)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 80.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🛡️ Secure Image Slideshow (SShow)",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (state.storedCount > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "💾 ${state.storedCount} stored",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Loading / Progress bar
            if (state.isLoading) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = state.loadingMessage,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { state.loadingProgress },
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Status message
            state.statusMessage?.let { status ->
                Surface(
                    color = if (status.startsWith("❌")) Color(0xFF4A1010) else Color(0xFF103A1A),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (status.startsWith("❌")) Color.Red else Color(0xFF00FF41)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = status,
                            fontSize = 13.sp,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearStatus() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // SECTION 1: 🔐 Encrypt Images
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🔐 Encrypt Images to .secure",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Select multiple images. Encrypted with AES-256 and generates a 3-digit PIN.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { encryptImagesPicker.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("sshow_encrypt_button")
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pick Images & Encrypt (.secure)", fontWeight = FontWeight.Bold)
                    }

                    // Display last password if available
                    state.lastGeneratedPin?.let { pin ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = Color.Black,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📌 YOUR 3-DIGIT PASSWORD", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                Text(pin, fontSize = 28.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 4.sp)
                                state.lastGeneratedFileName?.let {
                                    Text(it, fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // SECTION 2: 📂 Decrypt & View
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📂 Decrypt & View (.secure)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Open an encrypted .secure file and enter your 3-digit PIN.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { decryptFilePicker.launch(arrayOf("*/*")) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("sshow_decrypt_button")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select .secure File", fontWeight = FontWeight.Bold)
                    }

                    // Action buttons when decrypted images exist
                    if (state.images.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("🔓 ${state.images.size} Decrypted Images Active", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.showPreviewGrid() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.weight(1f).testTag("sshow_preview_btn")
                            ) {
                                Text("👁 Preview", fontSize = 12.sp)
                            }
                            Button(
                                onClick = { viewModel.startSlideshow() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00CEC9)),
                                modifier = Modifier.weight(1f).testTag("sshow_sux_btn")
                            ) {
                                Text("▶ SUX (Play)", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.storeInStorage() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("💾 Store", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Button(
                                onClick = { viewModel.mergeToStorage() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🔄 Merge", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Button(
                                onClick = { viewModel.exportStoredToEncrypted() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("📤 Export", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // SECTION 3: ➕ Add Images to Existing Encrypted File
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "➕ Add Images to Encrypted File",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Step 1: Choose .secure file\nStep 2: Choose new images to merge and re-encrypt.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { addSecureFilePicker.launch(arrayOf("*/*")) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (pendingAddSecureUri != null) Color(0xFF10B981) else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (pendingAddSecureUri != null) "✓ File Chosen" else "1. Choose File", fontSize = 12.sp)
                        }
                        Button(
                            onClick = { addImagesPicker.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("2. Add Photos", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Floating Action Buttons (Refresh, Load Storage, Fast Forward, Image Share)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Fast Forward button (>> toggle)
            if (state.isSlideshowActive) {
                FloatingActionButton(
                    onClick = { viewModel.toggleFastForward() },
                    containerColor = if (state.isFastForward) Color(0xFFFF9900) else Color(0xFFFFFF00),
                    contentColor = Color.Black,
                    modifier = Modifier.size(48.dp).testTag("sshow_fast_forward_btn")
                ) {
                    Text(">>", fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
            }

            // Image Share Button (IMG SH)
            FloatingActionButton(
                onClick = { viewModel.showShareOptions() },
                containerColor = Color(0xFFFF9900),
                contentColor = Color.Black,
                modifier = Modifier.size(48.dp).testTag("sshow_img_share_btn")
            ) {
                Text("SH", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            // Load from storage button
            if (state.storedCount > 0) {
                FloatingActionButton(
                    onClick = { viewModel.loadFromStorage() },
                    containerColor = Color(0xFF00CEC9),
                    contentColor = Color.Black,
                    modifier = Modifier.size(48.dp).testTag("sshow_load_storage_btn")
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Load Storage")
                }
            }

            // Refresh button (deletes all details in media sshow folder)
            FloatingActionButton(
                onClick = {
                    viewModel.clearSShowMediaFolder()
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(48.dp).testTag("sshow_refresh_btn")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh & Clear Media SShow Details")
            }
        }

        // DIALOGS & MODALS
        when (val dialog = state.dialog) {
            is SShowDialog.EncryptFilename -> {
                var filename by remember { mutableStateOf("MySecureImages") }
                AlertDialog(
                    onDismissRequest = { viewModel.closeDialog() },
                    title = { Text("📁 Enter File Name") },
                    text = {
                        Column {
                            Text("Name for encrypted .secure file (${dialog.pendingImageUris.size} images):", fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = filename,
                                onValueChange = { filename = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = { viewModel.onConfirmEncrypt(filename, dialog.pendingImageUris) }) {
                            Text("Encrypt & Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.closeDialog() }) { Text("Cancel") }
                    }
                )
            }

            is SShowDialog.DecryptPassword -> {
                var pin by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { viewModel.closeDialog() },
                    title = { Text("🔑 Enter 3-Digit Password") },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Enter password to decrypt file:", fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = pin,
                                onValueChange = { if (it.length <= 6) pin = it },
                                placeholder = { Text("3-digit code") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.primary,
                                    unfocusedTextColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = { viewModel.onConfirmDecrypt(dialog.secureUri, pin) }) {
                            Text("Confirm")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.closeDialog() }) { Text("Cancel") }
                    }
                )
            }

            is SShowDialog.AddImagesPassword -> {
                var pin by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { viewModel.closeDialog() },
                    title = { Text("🔑 Password for Re-Encryption") },
                    text = {
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { if (it.length <= 6) pin = it },
                            placeholder = { Text("3-digit password") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(onClick = { viewModel.onConfirmAddImages(dialog.secureUri, dialog.imageUris, pin) }) {
                            Text("Add & Re-Encrypt")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.closeDialog() }) { Text("Cancel") }
                    }
                )
            }

            is SShowDialog.ShareOptions -> {
                AlertDialog(
                    onDismissRequest = { viewModel.closeDialog() },
                    title = { Text("📤 Image Share Options") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    viewModel.closeDialog()
                                    shareImagePicker.launch("image/*")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9900)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("📤 Share Image (Get 5-Digit Code)", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    viewModel.showViewSharedInput()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A8FF)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("👁 View Shared Image with Code", color = Color.White)
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { viewModel.closeDialog() }) { Text("Close") }
                    }
                )
            }

            is SShowDialog.ViewSharedInput -> {
                var codeInput by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { viewModel.closeDialog() },
                    title = { Text("🔑 Enter 5-Digit Share Code") },
                    text = {
                        Column {
                            Text("Enter the 5-digit code for the shared photo:", fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = codeInput,
                                onValueChange = { if (it.length <= 8) codeInput = it.uppercase() },
                                placeholder = { Text("e.g. 7K9F2") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (codeInput.isNotBlank()) {
                                    viewModel.onViewSharedSubmit(codeInput.trim())
                                }
                            }
                        ) {
                            Text("View Image")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.closeDialog() }) { Text("Cancel") }
                    }
                )
            }

            is SShowDialog.SharePasswordDisplay -> {
                AlertDialog(
                    onDismissRequest = { viewModel.closeDialog() },
                    title = { Text("🔑 Image Share Code") },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text("Share this 5-digit code with others to view your photo:", fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                color = Color.Black,
                                border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = dialog.code,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                    letterSpacing = 6.sp,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = { viewModel.closeDialog() }) { Text("Done") }
                    }
                )
            }

            is SShowDialog.SharedImageView -> {
                Dialog(onDismissRequest = { viewModel.closeDialog() }) {
                    Surface(
                        color = Color(0xFF111111),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🖼️ Shared Image (${dialog.code})", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(10.dp))
                            AsyncImage(
                                model = dialog.imageFile,
                                contentDescription = "Shared image",
                                modifier = Modifier.fillMaxWidth().height(300.dp),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { viewModel.closeDialog() }, modifier = Modifier.fillMaxWidth()) {
                                Text("Close")
                            }
                        }
                    }
                }
            }

            is SShowDialog.PreviewGrid -> {
                Dialog(
                    onDismissRequest = { viewModel.closeDialog() },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.96f),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("👁 Decrypted Images Preview (${state.images.size})", color = Color.White, fontWeight = FontWeight.Bold)
                                IconButton(onClick = { viewModel.closeDialog() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                contentPadding = PaddingValues(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(state.images) { idx, img ->
                                    Surface(
                                        color = Color(0xFF1A1A1A),
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333)),
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { viewModel.openPreviewFullScreen(idx) }
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(img.file)
                                                .size(200, 200)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = img.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            else -> {}
        }

        // FULLSCREEN PREVIEW WITH SWIPE (SWEEP)
        state.previewFullScreenIndex?.let { previewIdx ->
            val previewImg = state.images.getOrNull(previewIdx)
            if (previewImg != null) {
                var dragOffset by remember { mutableFloatStateOf(0f) }
                Dialog(
                    onDismissRequest = { viewModel.closePreviewFullScreen() },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .pointerInput(previewIdx) {
                                detectHorizontalDragGestures(
                                    onDragStart = { dragOffset = 0f },
                                    onDragEnd = {
                                        if (dragOffset < -50f) {
                                            viewModel.nextPreviewFullScreen()
                                        } else if (dragOffset > 50f) {
                                            viewModel.prevPreviewFullScreen()
                                        }
                                        dragOffset = 0f
                                    },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount
                                    }
                                )
                            }
                    ) {
                        // Image displayed with Fit
                        AsyncImage(
                            model = previewImg.file,
                            contentDescription = previewImg.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 60.dp, horizontal = 8.dp)
                        )

                        // Top bar with name, counter and close button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .background(Color.Black.copy(alpha = 0.75f))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = previewImg.name,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = "${previewIdx + 1} / ${state.images.size} • Swipe left / right",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp
                                )
                            }
                            IconButton(onClick = { viewModel.closePreviewFullScreen() }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }

                        // Bottom navigation bar with Prev and Next arrows
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(Color.Black.copy(alpha = 0.75f))
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.prevPreviewFullScreen() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Previous",
                                    tint = Color.White
                                )
                            }

                            Text(
                                text = "◀  Swipe to Sweep  ▶",
                                color = Color.LightGray,
                                fontSize = 13.sp
                            )

                            IconButton(
                                onClick = { viewModel.nextPreviewFullScreen() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Next",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // FULLSCREEN SLIDESHOW (SUX)
        if (state.isSlideshowActive && state.images.isNotEmpty()) {
            val currentImg = state.images.getOrNull(state.currentSlideIndex)
            Dialog(
                onDismissRequest = { viewModel.stopSlideshow() },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { viewModel.toggleSlideshowPause() },
                                onDoubleTap = { viewModel.stopSlideshow() }
                            )
                        }
                        .pointerInput(Unit) {
                            var totalDrag = 0f
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (totalDrag > 60) viewModel.previousSlide()
                                    else if (totalDrag < -60) viewModel.nextSlide()
                                    totalDrag = 0f
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    totalDrag += dragAmount
                                }
                            )
                        }
                ) {
                    if (currentImg != null) {
                        AsyncImage(
                            model = currentImg.file,
                            contentDescription = currentImg.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Top Bar: Slide Index, Pause status, Fast-Forward Indicator, Close Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${state.currentSlideIndex + 1} / ${state.images.size}",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (state.isSlideshowPaused) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("⏸ PAUSED", color = Color.Yellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                if (state.isFastForward) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("⚡ FAST", color = Color(0xFFFF9900), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Fast Forward Toggle Button (>>)
                            Surface(
                                color = if (state.isFastForward) Color(0xFFFF9900) else Color.Black.copy(alpha = 0.7f),
                                shape = CircleShape,
                                modifier = Modifier.size(36.dp).clickable { viewModel.toggleFastForward() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(">>", color = if (state.isFastForward) Color.Black else Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                }
                            }

                            // Close Button (×)
                            Surface(
                                color = Color(0xFFFF3333),
                                shape = CircleShape,
                                modifier = Modifier.size(36.dp).clickable { viewModel.stopSlideshow() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
