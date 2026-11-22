package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.view.TextureView
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.util.EncryptedVideoDataSource
import com.example.util.SettingsManager
import com.example.util.VaultVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
private val ASPECT_MODES = listOf("FIT", "FILL", "ZOOM")

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(UnstableApi::class)
@Composable
fun VidVaultPlayerModal(
    videos: List<VaultVideo>,
    currentIndex: Int,
    createDirectDataSource: (String) -> EncryptedVideoDataSource?,
    createExoDataSourceFactory: (String) -> DataSource.Factory? = { null },
    prepareFallbackStorageFile: suspend (String) -> File?,
    onShare: (VaultVideo) -> Unit,
    onClose: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    val video = videos.getOrNull(currentIndex) ?: return
    val context = LocalContext.current
    val settings by SettingsManager.settings.collectAsState()

    BackHandler(onBack = onClose)

    var showControls by remember { mutableStateOf(true) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var isPreparing by remember(video.id) { mutableStateOf(true) }
    var isPlaying by remember(video.id) { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var isLooping by remember { mutableStateOf(settings.vidVaultLoopPlayback) }
    var playbackSpeed by remember { mutableFloatStateOf(settings.vidVaultDefaultPlaybackSpeed) }
    var aspectMode by remember { mutableStateOf(settings.vidVaultDefaultAspectMode) }
    var surfaceMode by remember { mutableStateOf(settings.vidVaultVideoSurfaceMode) }
    var usingDirectStream by remember(video.id) { mutableStateOf(settings.vidVaultDirectDiskStream) }
    var reloadNonce by remember(video.id) { mutableIntStateOf(0) }

    var durationMs by remember(video.id) { mutableLongStateOf(video.durationMs.coerceAtLeast(0L)) }
    var positionMs by remember(video.id) { mutableLongStateOf(0L) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var seekSliderMs by remember(video.id) { mutableFloatStateOf(0f) }
    var videoWidth by remember(video.id) { mutableIntStateOf(16) }
    var videoHeight by remember(video.id) { mutableIntStateOf(9) }
    var hasRenderedFirstFrame by remember(video.id) { mutableStateOf(false) }
    var errorMessage by remember(video.id) { mutableStateOf<String?>(null) }

    // Keep screen awake while video modal is open (when enabled in settings)
    DisposableEffect(settings.vidVaultKeepScreenOn) {
        val window = context.findActivity()?.window
        if (settings.vidVaultKeepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Build low-RAM Media3 ExoPlayer with automatic software decoder fallback (eliminates Phone 2 black screens!)
    val exoPlayer = remember(settings.vidVaultSoftwareDecoderFallback) {
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(settings.vidVaultSoftwareDecoderFallback)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 2_500,
                /* maxBufferMs = */ 8_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 1_500
            )
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()

        ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .build()
    }

    // Attach ExoPlayer state & error listeners
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> isPreparing = true
                    Player.STATE_READY -> {
                        isPreparing = false
                        val dur = exoPlayer.duration
                        if (dur > 0L) durationMs = dur
                    }
                    Player.STATE_ENDED -> {
                        isPreparing = false
                        if (!isLooping) {
                            isPlaying = false
                            positionMs = durationMs
                            seekSliderMs = durationMs.toFloat()
                            showControls = true
                        }
                    }
                    Player.STATE_IDLE -> Unit
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoWidth = videoSize.width
                    videoHeight = videoSize.height
                }
            }

            override fun onRenderedFirstFrame() {
                hasRenderedFirstFrame = true
                isPreparing = false
            }

            override fun onPlayerError(error: PlaybackException) {
                // Automatic resilience:
                // 1) If direct encrypted stream failed -> auto-fallback to storage buffer file!
                // 2) If TextureView failed -> switch to SurfaceView!
                if (usingDirectStream) {
                    usingDirectStream = false
                } else {
                    isPreparing = false
                    isPlaying = false
                    errorMessage = "Playback failed (${error.errorCodeName}). Try switching Renderer (Surface/Texture) or Share to open externally."
                    showControls = true
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            try {
                exoPlayer.stop()
                exoPlayer.release()
            } catch (_: Throwable) {}
        }
    }

    // Keep player flags synced
    LaunchedEffect(exoPlayer, isLooping, isMuted, playbackSpeed) {
        exoPlayer.repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        exoPlayer.volume = if (isMuted) 0f else 1f
        exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
    }

    // Load video into ExoPlayer (either zero-RAM Direct Encrypted Disk DataSource or 64 KB Temp Storage File)
    LaunchedEffect(video.id, usingDirectStream, reloadNonce) {
        isPreparing = true
        isPlaying = false
        hasRenderedFirstFrame = false
        errorMessage = null
        positionMs = 0L
        seekSliderMs = 0f

        try {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()

            if (usingDirectStream) {
                val factory = withContext(Dispatchers.IO) { createExoDataSourceFactory(video.id) }
                if (factory != null) {
                    val mediaItem = MediaItem.Builder()
                        .setUri(Uri.parse("vidvault://encrypted/${video.id}/${Uri.encode(video.name)}"))
                        .setMimeType(video.mimeType.takeIf { it.startsWith("video/") && it != "video/*" })
                        .build()
                    val mediaSource = ProgressiveMediaSource.Factory(factory).createMediaSource(mediaItem)
                    exoPlayer.setMediaSource(mediaSource)
                    exoPlayer.playWhenReady = settings.vidVaultAutoPlay
                    exoPlayer.prepare()
                } else {
                    usingDirectStream = false
                }
            } else {
                val tempFile = prepareFallbackStorageFile(video.id)
                if (tempFile != null && tempFile.exists() && tempFile.length() > 0L) {
                    val mediaItem = MediaItem.fromUri(Uri.fromFile(tempFile))
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.playWhenReady = settings.vidVaultAutoPlay
                    exoPlayer.prepare()
                } else {
                    isPreparing = false
                    errorMessage = "Could not decrypt video file from vault storage."
                }
            }
        } catch (e: Throwable) {
            if (usingDirectStream) {
                usingDirectStream = false
            } else {
                isPreparing = false
                errorMessage = "Failed to start video playback: ${e.message}"
            }
        }
    }

    // Live playback progress ticker
    LaunchedEffect(exoPlayer, video.id) {
        while (true) {
            if (!isUserSeeking) {
                val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                val dur = exoPlayer.duration
                if (dur > 0L) durationMs = dur
                positionMs = pos
                seekSliderMs = pos.toFloat()
            }
            delay(250L)
        }
    }

    val extLabel = video.name.substringAfterLast('.', "VIDEO").uppercase(Locale.getDefault()).take(5)
    val sizeMb = video.sizeBytes / (1024.0 * 1024.0)
    val sizeText = if (sizeMb >= 1024.0) {
        String.format(Locale.getDefault(), "%.2f GB", sizeMb / 1024.0)
    } else {
        String.format(Locale.getDefault(), "%.1f MB", sizeMb)
    }

    val resizeModeInt = when (aspectMode) {
        "FILL" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        "ZOOM" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { showControls = !showControls }
            .testTag("vid_player_modal_root")
    ) {
        // Video Rendering Surface: Media3 PlayerView (native SurfaceView) OR TextureView compatibility mode
        key(surfaceMode) {
            if (surfaceMode == "TEXTURE_VIEW") {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val maxW = maxWidth
                    val maxH = maxHeight
                    val videoAspect = (videoWidth.toFloat() / videoHeight.coerceAtLeast(1).toFloat()).coerceIn(0.25f, 4.0f)
                    val containerAspect = (maxW.value / maxH.value.coerceAtLeast(1f)).coerceAtLeast(0.1f)
                    val (targetW, targetH) = when (aspectMode) {
                        "FILL" -> maxW to maxH
                        "ZOOM" -> if (videoAspect > containerAspect) (maxH * videoAspect) to maxH else maxW to (maxW / videoAspect)
                        else -> if (videoAspect > containerAspect) maxW to (maxW / videoAspect) else (maxH * videoAspect) to maxH
                    }

                    AndroidView(
                        factory = { ctx ->
                            TextureView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                exoPlayer.setVideoTextureView(this)
                            }
                        },
                        update = { tv ->
                            exoPlayer.setVideoTextureView(tv)
                        },
                        onRelease = { tv ->
                            exoPlayer.clearVideoTextureView(tv)
                        },
                        modifier = Modifier
                            .size(width = targetW, height = targetH)
                            .testTag("vid_player_surface")
                    )
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            useController = false
                            setKeepContentOnPlayerReset(false)
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                            resizeMode = resizeModeInt
                            player = exoPlayer
                        }
                    },
                    update = { pv ->
                        if (pv.player !== exoPlayer) {
                            pv.player = exoPlayer
                        }
                        if (pv.resizeMode != resizeModeInt) {
                            pv.resizeMode = resizeModeInt
                        }
                    },
                    onRelease = { pv ->
                        pv.player = null
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("vid_player_surface")
                )
            }
        }

        // Loading / Buffering Indicator
        if (isPreparing && errorMessage == null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.Center)
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF00CEC9),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (usingDirectStream) "Streaming encrypted disk (ExoPlayer)…" else "Decrypting storage buffer…",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp
                )
            }
        }

        // Error Card
        errorMessage?.let { err ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xEE1A1A2E),
                border = BorderStroke(1.dp, Color(0xFFFF7675)),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Unable to Play Video",
                        color = Color(0xFFFF7675),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = err,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        Button(
                            onClick = {
                                errorMessage = null
                                surfaceMode = if (surfaceMode == "SURFACE_VIEW") "TEXTURE_VIEW" else "SURFACE_VIEW"
                                reloadNonce++
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00CEC9))
                        ) {
                            Text("Switch Surface", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                errorMessage = null
                                usingDirectStream = !usingDirectStream
                                reloadNonce++
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0984E3))
                        ) {
                            Text("Retry", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Button(
                            onClick = { onShare(video) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7))
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("External App", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Screen Locked Overlay (only unlock button shown)
        if (isScreenLocked && showControls) {
            IconButton(
                onClick = { isScreenLocked = false },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(14.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC6C5CE7))
                    .testTag("vid_player_unlock_touch")
            ) {
                Icon(Icons.Default.Lock, contentDescription = "Unlock Player Controls", tint = Color.White)
            }
        }

        // Interactive Overlay Controls
        AnimatedVisibility(
            visible = showControls && !isScreenLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isCompact = maxWidth < 380.dp

                // Top Gradient Bar (Close, File Info, Surface Switch, Share)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.88f), Color.Transparent)
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = if (isCompact) 8.dp else 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .testTag("vid_player_close")
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close Video Player",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = video.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isCompact) 13.sp else 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF6C5CE7).copy(alpha = 0.75f)
                            ) {
                                Text(
                                    text = extLabel,
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                            Text(
                                text = "$sizeText • ${currentIndex + 1}/${videos.size}",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF00CEC9).copy(alpha = 0.25f),
                                border = BorderStroke(0.5.dp, Color(0xFF00CEC9)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        usingDirectStream = !usingDirectStream
                                        reloadNonce++
                                    }
                                    .testTag("vid_player_mode_chip")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Storage,
                                        contentDescription = null,
                                        tint = Color(0xFF00CEC9),
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (usingDirectStream) "Exo Disk" else "Buffer",
                                        color = Color(0xFF00CEC9),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Quick 1-tap Surface / Texture Switcher (in case any OEM ROM prefers TextureView)
                    IconButton(
                        onClick = {
                            surfaceMode = if (surfaceMode == "SURFACE_VIEW") "TEXTURE_VIEW" else "SURFACE_VIEW"
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .testTag("vid_player_surface_toggle")
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "Switch SurfaceView or TextureView",
                            tint = Color(0xFF00CEC9),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = { onShare(video) },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00CEC9).copy(alpha = 0.22f))
                            .testTag("vid_player_share")
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share or Open Video Externally",
                            tint = Color(0xFF00CEC9),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Center Transport Controls (Prev / -10s / Play-Pause / +10s / Next)
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (videos.size > 1) {
                        IconButton(
                            onClick = onPrevious,
                            modifier = Modifier
                                .size(if (isCompact) 42.dp else 46.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .testTag("vid_player_prev")
                        ) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous Video", tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                    }

                    IconButton(
                        onClick = {
                            val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(target)
                            positionMs = target
                            seekSliderMs = target.toFloat()
                        },
                        modifier = Modifier
                            .size(if (isCompact) 46.dp else 52.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .testTag("vid_player_rewind_10")
                    ) {
                        Icon(Icons.Default.Replay10, contentDescription = "Rewind 10 Seconds", tint = Color.White, modifier = Modifier.size(26.dp))
                    }

                    IconButton(
                        onClick = {
                            if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                            } else {
                                if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                    exoPlayer.seekTo(0L)
                                }
                                exoPlayer.play()
                            }
                        },
                        modifier = Modifier
                            .size(if (isCompact) 62.dp else 68.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF6C5CE7), Color(0xFF00CEC9))))
                            .testTag("vid_player_play_pause")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val maxDur = exoPlayer.duration.coerceAtLeast(durationMs).coerceAtLeast(0L)
                            val target = (exoPlayer.currentPosition + 10_000L).coerceAtMost(maxDur)
                            exoPlayer.seekTo(target)
                            positionMs = target
                            seekSliderMs = target.toFloat()
                        },
                        modifier = Modifier
                            .size(if (isCompact) 46.dp else 52.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .testTag("vid_player_forward_10")
                    ) {
                        Icon(Icons.Default.Forward10, contentDescription = "Forward 10 Seconds", tint = Color.White, modifier = Modifier.size(26.dp))
                    }

                    if (videos.size > 1) {
                        IconButton(
                            onClick = onNext,
                            modifier = Modifier
                                .size(if (isCompact) 42.dp else 46.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .testTag("vid_player_next")
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next Video", tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                    }
                }

                // Bottom Gradient Controls Bar (Seek Slider + Action Row + navigationBarsPadding)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))
                            )
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = if (isCompact) 10.dp else 16.dp, vertical = 12.dp)
                ) {
                    val sliderMax = durationMs.coerceAtLeast(1000L).toFloat()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDurationMs(seekSliderMs.toLong()),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = seekSliderMs.coerceIn(0f, sliderMax),
                            onValueChange = { value ->
                                isUserSeeking = true
                                seekSliderMs = value
                            },
                            onValueChangeFinished = {
                                val target = seekSliderMs.toLong().coerceIn(0L, durationMs.coerceAtLeast(0L))
                                exoPlayer.seekTo(target)
                                positionMs = target
                                isUserSeeking = false
                            },
                            valueRange = 0f..sliderMax,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00CEC9),
                                activeTrackColor = Color(0xFF00CEC9),
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                                .testTag("vid_player_seek_slider")
                        )
                        Text(
                            text = formatDurationMs(durationMs),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Scrollable responsive pill row so narrow 2nd phones NEVER overflow!
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mute
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isMuted) Color(0xFFFF7675).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isMuted = !isMuted }
                                .testTag("vid_player_mute")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = if (isMuted) "Unmute" else "Mute",
                                    tint = if (isMuted) Color(0xFFFF7675) else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isMuted) "Muted" else "Sound", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Loop
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isLooping) Color(0xFF00CEC9).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isLooping = !isLooping }
                                .testTag("vid_player_loop")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Icon(
                                    imageVector = if (isLooping) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                    contentDescription = "Toggle Loop",
                                    tint = if (isLooping) Color(0xFF00CEC9) else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isLooping) "Loop ON" else "Loop", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Playback Speed
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val idx = PLAYBACK_SPEEDS.indexOf(playbackSpeed)
                                    playbackSpeed = PLAYBACK_SPEEDS[(idx + 1) % PLAYBACK_SPEEDS.size]
                                }
                                .testTag("vid_player_speed")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Icon(Icons.Default.Speed, contentDescription = "Playback Speed", tint = Color(0xFF00CEC9), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${playbackSpeed}x", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Aspect Mode (Fit / Fill / Zoom)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val idx = ASPECT_MODES.indexOf(aspectMode)
                                    aspectMode = ASPECT_MODES[(idx + 1) % ASPECT_MODES.size]
                                }
                                .testTag("vid_player_aspect")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Icon(Icons.Default.AspectRatio, contentDescription = "Aspect Ratio", tint = Color(0xFF00CEC9), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(aspectMode, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Lock Touch Controls
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isScreenLocked = true }
                                .testTag("vid_player_lock_touch")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Icon(Icons.Default.LockOpen, contentDescription = "Lock Screen Controls", tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Lock", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Reload Stream
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { reloadNonce++ }
                                .testTag("vid_player_reload")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reload Video", tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reload", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatDurationMs(durationMs: Long): String {
    if (durationMs <= 0L) return "0:00"
    val totalSeconds = durationMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}
