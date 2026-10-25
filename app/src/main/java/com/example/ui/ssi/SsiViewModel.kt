package com.example.ui.ssi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.util.SettingsManager
import com.example.util.SsiHostInfo
import com.example.util.SsiHostServer
import com.example.util.SsiImage
import com.example.util.SsiManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SsiUiState(
    val images: List<SsiImage> = emptyList(),
    val isLoading: Boolean = false,
    val isSlideshowActive: Boolean = false,
    val isSlideshowPaused: Boolean = false,
    val currentSlideshowIndex: Int = 0,
    val showClearDialog: Boolean = false,
    val userMessage: String? = null,
    val selectedImage: SsiImage? = null,
    val isRearrangeMode: Boolean = false,
    val selectedRearrangeIndex: Int? = null,
    val showHostPinDialog: Boolean = false,
    val showHostInfoDialog: Boolean = false
)

class SsiViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SsiUiState())
    val uiState: StateFlow<SsiUiState> = _uiState.asStateFlow()

    val hostState: StateFlow<SsiHostInfo> = SsiHostServer.hostState

    private var slideshowJob: Job? = null

    init {
        SsiManager.init(application)
        viewModelScope.launch {
            SsiManager.importedImages.collect { list ->
                _uiState.update { current ->
                    val safeIdx = if (list.isEmpty()) 0 else current.currentSlideshowIndex.coerceIn(0, list.size - 1)
                    current.copy(images = list, currentSlideshowIndex = safeIdx)
                }
            }
        }
    }

    fun refresh() {
        SsiManager.refresh(getApplication())
    }

    fun startSlideshow(startIndex: Int = 0) {
        val list = _uiState.value.images
        if (list.isEmpty()) {
            _uiState.update { it.copy(userMessage = "No imported images to play") }
            return
        }
        val safeIndex = startIndex.coerceIn(0, list.size - 1)
        stopSlideshow()
        _uiState.update {
            it.copy(
                isSlideshowActive = true,
                isSlideshowPaused = false,
                currentSlideshowIndex = safeIndex
            )
        }
        startTimer()
    }

    fun togglePlayPause() {
        if (!_uiState.value.isSlideshowActive) {
            startSlideshow(_uiState.value.currentSlideshowIndex)
            return
        }
        if (_uiState.value.isSlideshowPaused) {
            resumeSlideshow()
        } else {
            pauseSlideshow()
        }
    }

    fun pauseSlideshow() {
        slideshowJob?.cancel()
        slideshowJob = null
        _uiState.update { it.copy(isSlideshowPaused = true) }
    }

    fun resumeSlideshow() {
        _uiState.update { it.copy(isSlideshowPaused = false) }
        startTimer()
    }

    fun stopSlideshow() {
        slideshowJob?.cancel()
        slideshowJob = null
        _uiState.update { it.copy(isSlideshowActive = false, isSlideshowPaused = false) }
    }

    fun nextImage() {
        val total = _uiState.value.images.size
        if (total == 0) return
        _uiState.update { state ->
            val nextIdx = (state.currentSlideshowIndex + 1) % total
            state.copy(currentSlideshowIndex = nextIdx)
        }
    }

    fun prevImage() {
        val total = _uiState.value.images.size
        if (total == 0) return
        _uiState.update { state ->
            val prevIdx = (state.currentSlideshowIndex - 1 + total) % total
            state.copy(currentSlideshowIndex = prevIdx)
        }
    }

    fun goToIndex(index: Int) {
        val total = _uiState.value.images.size
        if (total == 0) return
        _uiState.update { it.copy(currentSlideshowIndex = index.coerceIn(0, total - 1)) }
    }

    private fun startTimer() {
        slideshowJob?.cancel()
        slideshowJob = viewModelScope.launch {
            while (isActive) {
                val intervalSec = SettingsManager.settings.value.ssiSlideshowIntervalSeconds.coerceIn(1.0f, 5.0f)
                val delayMs = (intervalSec * 1000L).toLong()
                delay(delayMs)

                val list = _uiState.value.images
                if (list.isEmpty()) {
                    stopSlideshow()
                    break
                }
                val currentIndex = _uiState.value.currentSlideshowIndex
                val isLoop = SettingsManager.settings.value.ssiAutoLoop

                if (currentIndex >= list.size - 1 && !isLoop) {
                    stopSlideshow()
                    break
                } else {
                    nextImage()
                }
            }
        }
    }

    fun requestClearAll() {
        _uiState.update { it.copy(showClearDialog = true) }
    }

    fun dismissClearDialog() {
        _uiState.update { it.copy(showClearDialog = false) }
    }

    fun confirmClearAll() {
        stopSlideshow()
        _uiState.update { it.copy(showClearDialog = false, isLoading = true) }
        viewModelScope.launch {
            val count = SsiManager.clearAll(getApplication())
            _uiState.update {
                it.copy(
                    isLoading = false,
                    currentSlideshowIndex = 0,
                    userMessage = "Cleared $count image(s) from media_imported"
                )
            }
        }
    }

    fun deleteImage(item: SsiImage) {
        viewModelScope.launch {
            val ok = SsiManager.deleteImage(getApplication(), item.file)
            if (ok) {
                _uiState.update { it.copy(userMessage = "Deleted ${item.name}") }
            }
        }
    }

    fun selectImage(item: SsiImage?) {
        _uiState.update { it.copy(selectedImage = item) }
    }

    fun enterRearrangeMode(initialIndex: Int? = null) {
        val list = _uiState.value.images
        val safeIndex = if (list.isEmpty()) null else (initialIndex ?: 0).coerceIn(0, list.size - 1)
        _uiState.update {
            it.copy(
                isRearrangeMode = true,
                selectedRearrangeIndex = safeIndex,
                userMessage = "Rearrange Mode: Tap any position to move, or use arrows to reorder."
            )
        }
    }

    fun exitRearrangeMode() {
        _uiState.update {
            it.copy(
                isRearrangeMode = false,
                selectedRearrangeIndex = null,
                userMessage = "Rearrangement saved"
            )
        }
    }

    fun selectRearrangeIndex(index: Int) {
        val current = _uiState.value
        val fromIndex = current.selectedRearrangeIndex
        if (fromIndex != null && fromIndex != index) {
            // Move item to this tapped position
            moveImage(fromIndex, index)
        } else {
            _uiState.update { it.copy(selectedRearrangeIndex = index) }
        }
    }

    fun moveImage(fromIndex: Int, toIndex: Int) {
        val ok = SsiManager.moveImage(getApplication(), fromIndex, toIndex)
        if (ok) {
            _uiState.update { it.copy(selectedRearrangeIndex = toIndex) }
        }
    }

    fun swapImages(index1: Int, index2: Int) {
        val ok = SsiManager.swapImages(getApplication(), index1, index2)
        if (ok) {
            _uiState.update { it.copy(selectedRearrangeIndex = index2) }
        }
    }

    fun moveSelectedLeft() {
        val current = _uiState.value
        val sel = current.selectedRearrangeIndex ?: return
        if (sel > 0) {
            moveImage(sel, sel - 1)
        }
    }

    fun moveSelectedRight() {
        val current = _uiState.value
        val sel = current.selectedRearrangeIndex ?: return
        if (sel < current.images.size - 1) {
            moveImage(sel, sel + 1)
        }
    }

    fun moveSelectedToStart() {
        val current = _uiState.value
        val sel = current.selectedRearrangeIndex ?: return
        if (sel > 0) {
            moveImage(sel, 0)
        }
    }

    fun moveSelectedToEnd() {
        val current = _uiState.value
        val sel = current.selectedRearrangeIndex ?: return
        if (sel < current.images.size - 1) {
            moveImage(sel, current.images.size - 1)
        }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    fun openHostPinDialog() {
        _uiState.update { it.copy(showHostPinDialog = true) }
    }

    fun dismissHostPinDialog() {
        _uiState.update { it.copy(showHostPinDialog = false) }
    }

    fun confirmStartHost(pin: String) {
        val cleanPin = pin.trim()
        if (cleanPin.length != 4 || !cleanPin.all { it.isDigit() }) {
            _uiState.update { it.copy(userMessage = "PIN must be exactly 4 digits") }
            return
        }
        val ok = SsiHostServer.start(getApplication(), cleanPin)
        if (ok) {
            _uiState.update {
                it.copy(
                    showHostPinDialog = false,
                    showHostInfoDialog = true,
                    userMessage = "Slideshow Host started! PIN set to $cleanPin"
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    showHostPinDialog = false,
                    userMessage = "Failed to start host server. Check network connection."
                )
            }
        }
    }

    fun stopHost() {
        SsiHostServer.stop()
        _uiState.update {
            it.copy(
                showHostInfoDialog = false,
                userMessage = "Host stopped. All connected devices disconnected."
            )
        }
    }

    fun openHostInfoDialog() {
        _uiState.update { it.copy(showHostInfoDialog = true) }
    }

    fun dismissHostInfoDialog() {
        _uiState.update { it.copy(showHostInfoDialog = false) }
    }

    override fun onCleared() {
        super.onCleared()
        stopSlideshow()
        SsiHostServer.stop()
    }
}
