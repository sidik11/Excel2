package com.example.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.repository.CatalogRepository
import com.example.data.repository.DisplayImageItem
import com.example.data.repository.NameCount
import com.example.util.ExcelParser
import com.example.util.NotificationHelper
import com.example.util.ParsedExcelRow
import com.example.util.StorageHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class UiState(
    val isLoading: Boolean = false,
    val loadingMessage: String = "",
    val excelName: String? = null,
    val excelUri: String? = null,
    val folderName: String? = null,
    val folderUri: String? = null,
    val selectedName: String = "",
    val selectedColour: String = "",
    val availableNames: List<NameCount> = emptyList(),
    val availableColours: List<String> = emptyList(),
    val displayedImages: List<DisplayImageItem> = emptyList(),
    val statusText: String = "Waiting for Excel file.",
    val activeModalIndex: Int? = null,
    val isSlideshowPlaying: Boolean = false,
    val isPermanentSaved: Boolean = false,
    val totalRowsInCache: Int = 0,
    val totalImagesInCache: Int = 0,
    val userMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val repository = CatalogRepository(database, application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var slideshowJob: Job? = null

    init {
        NotificationHelper.createNotificationChannel(application)
        restorePersistedSession()
    }

    fun reloadPersistedData() {
        restorePersistedSession()
    }

    private fun restorePersistedSession() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingMessage = "Loading offline cache…") }

            val savedExcelUri = repository.getSavedConfig("excel_uri")
            val savedExcelName = repository.getSavedConfig("excel_name")
            val savedFolderUri = repository.getSavedConfig("folder_uri")
            val savedFolderName = repository.getSavedConfig("folder_name")

            val context = getApplication<Application>()
            val hasExcelPerm = savedExcelUri?.let { StorageHelper.hasPersistedPermission(context, Uri.parse(it)) } ?: false
            val hasFolderPerm = savedFolderUri?.let { StorageHelper.hasPersistedPermission(context, Uri.parse(it)) } ?: false

            val rows = database.excelDao().getAllRowsList()
            val images = database.imageDao().getAllImagesList()

            if (rows.isNotEmpty()) {
                val groups = rows.groupBy { it.name }
                val names = groups.map { NameCount(it.key, it.value.size) }
                    .sortedBy { it.name }

                val colors = rows.flatMap { ExcelParser.splitColours(it.colour) }
                    .distinct()
                    .sorted()

                val status = if (images.isNotEmpty()) {
                    "Cached ${rows.size} rows & ${images.size} images loaded permanently. Ready."
                } else {
                    "Loaded ${rows.size} rows from cache. Select an image folder."
                }

                _uiState.update {
                    it.copy(
                        excelName = savedExcelName,
                        excelUri = savedExcelUri,
                        folderName = savedFolderName,
                        folderUri = savedFolderUri,
                        availableNames = names,
                        availableColours = colors,
                        statusText = status,
                        totalRowsInCache = rows.size,
                        totalImagesInCache = images.size,
                        isPermanentSaved = hasExcelPerm || hasFolderPerm || savedExcelName != null,
                        isLoading = false
                    )
                }

                // If images also exist, load all by default
                if (images.isNotEmpty()) {
                    refreshDisplayedImages()
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusText = "Waiting for Excel file. Tap 'Select Excel' or 'Sample Catalog'."
                    )
                }
            }
        }
    }

    fun onExcelSelected(uri: Uri) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingMessage = "Reading Excel file…") }

            // Take persistable permission for permanent access
            StorageHelper.takePersistablePermission(context, uri)

            var displayName = "catalog.xlsx"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    displayName = cursor.getString(nameIndex) ?: displayName
                }
            }

            val parsedRows = ExcelParser.parse(context, uri)
            if (parsedRows.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusText = "Excel parsing failed: No valid data found with CODE and NAME columns."
                    )
                }
                return@launch
            }

            // Save to Room cache
            repository.saveExcelCatalog(parsedRows, uri.toString(), displayName)

            val groups = parsedRows.groupBy { it.name }
            val names = groups.map { NameCount(it.key, it.value.size) }.sortedBy { it.name }
            val colors = parsedRows.flatMap { ExcelParser.splitColours(it.colour) }.distinct().sorted()

            _uiState.update {
                it.copy(
                    excelName = displayName,
                    excelUri = uri.toString(),
                    availableNames = names,
                    availableColours = colors,
                    selectedName = "",
                    selectedColour = "",
                    totalRowsInCache = parsedRows.size,
                    isPermanentSaved = true,
                    statusText = "Loaded ${parsedRows.size} rows • ${names.size} names • ${colors.size} colors from $displayName.",
                    isLoading = false
                )
            }

            // Send Push / Local Notification
            NotificationHelper.showNotification(
                context = context,
                title = "Excel Catalog Loaded",
                message = "Successfully cached ${parsedRows.size} items from $displayName permanently.",
                notificationId = NotificationHelper.NOTIFICATION_EXCEL_LOADED
            )

            refreshDisplayedImages()
        }
    }

    fun onFolderSelected(treeUri: Uri) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingMessage = "Indexing folder images…") }

            // Take persistable permission for permanent access
            StorageHelper.takePersistablePermission(context, treeUri)

            val folderName = treeUri.lastPathSegment?.substringAfterLast(':') ?: "Image Folder"

            val scannedImages = StorageHelper.scanTreeUri(context, treeUri) { scanned, found ->
                _uiState.update {
                    it.copy(loadingMessage = "Indexing folder… Scanned $scanned files, found $found images")
                }
            }

            // Save to Room cache
            repository.saveCachedImages(scannedImages, treeUri.toString(), folderName)

            _uiState.update {
                it.copy(
                    folderName = folderName,
                    folderUri = treeUri.toString(),
                    totalImagesInCache = scannedImages.size,
                    isPermanentSaved = true,
                    isLoading = false,
                    statusText = "Image folder indexed: ${scannedImages.size} images saved to offline cache."
                )
            }

            // Send Push / Local Notification
            NotificationHelper.showNotification(
                context = context,
                title = "Image Library Cached",
                message = "Permanently indexed ${scannedImages.size} images from $folderName for instant viewing.",
                notificationId = NotificationHelper.NOTIFICATION_IMAGES_INDEXED
            )

            refreshDisplayedImages()
        }
    }

    fun loadSampleData() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingMessage = "Preparing sample catalog & images…") }

            val sampleCatalog = ExcelParser.getSampleCatalog()
            repository.saveExcelCatalog(sampleCatalog, null, "sample_apparel_catalog.xlsx")

            val sampleImages = StorageHelper.createSampleImages(context)
            repository.saveCachedImages(sampleImages, null, "sample_images")

            val groups = sampleCatalog.groupBy { it.name }
            val names = groups.map { NameCount(it.key, it.value.size) }.sortedBy { it.name }
            val colors = sampleCatalog.flatMap { ExcelParser.splitColours(it.colour) }.distinct().sorted()

            _uiState.update {
                it.copy(
                    excelName = "sample_apparel_catalog.xlsx",
                    excelUri = null,
                    folderName = "sample_images",
                    folderUri = null,
                    availableNames = names,
                    availableColours = colors,
                    selectedName = "__ALL__",
                    selectedColour = "__ALL__",
                    totalRowsInCache = sampleCatalog.size,
                    totalImagesInCache = sampleImages.size,
                    isPermanentSaved = true,
                    statusText = "Loaded sample catalog: ${sampleCatalog.size} items • ${sampleImages.size} images ready.",
                    isLoading = false
                )
            }

            NotificationHelper.showNotification(
                context = context,
                title = "Sample Catalog Ready",
                message = "Loaded sample catalog with ${sampleCatalog.size} items and full offline caching.",
                notificationId = NotificationHelper.NOTIFICATION_EXCEL_LOADED
            )

            refreshDisplayedImages()
        }
    }

    fun onNameFilterChanged(name: String) {
        _uiState.update { it.copy(selectedName = name) }
        refreshDisplayedImages()
    }

    fun onColourFilterChanged(colour: String) {
        _uiState.update { it.copy(selectedColour = colour) }
        refreshDisplayedImages()
    }

    private fun refreshDisplayedImages() {
        viewModelScope.launch {
            val state = _uiState.value
            val name = state.selectedName
            val colour = state.selectedColour

            if (state.totalRowsInCache == 0) {
                _uiState.update { it.copy(displayedImages = emptyList()) }
                return@launch
            }

            val matches = repository.getMatchingImages(name, colour)

            val nameLabel = if (name.isEmpty() || name == "__ALL__") "All" else name
            val colourLabel = if (colour.isEmpty() || colour == "__ALL__") "All" else colour
            val status = "Name: $nameLabel | Colour: $colourLabel: ${matches.size} image${if (matches.size == 1) "" else "s"} found (Offline Cached)."

            _uiState.update {
                it.copy(
                    displayedImages = matches,
                    statusText = status
                )
            }
        }
    }

    fun openModal(index: Int) {
        _uiState.update {
            it.copy(
                activeModalIndex = index,
                isSlideshowPlaying = false
            )
        }
    }

    fun closeModal() {
        stopSlideshow()
        _uiState.update {
            it.copy(
                activeModalIndex = null,
                isSlideshowPlaying = false
            )
        }
    }

    fun nextImage() {
        _uiState.update { state ->
            val total = state.displayedImages.size
            if (total == 0 || state.activeModalIndex == null) return@update state
            val nextIdx = (state.activeModalIndex + 1) % total
            state.copy(activeModalIndex = nextIdx)
        }
    }

    fun previousImage() {
        _uiState.update { state ->
            val total = state.displayedImages.size
            if (total == 0 || state.activeModalIndex == null) return@update state
            val prevIdx = (state.activeModalIndex - 1 + total) % total
            state.copy(activeModalIndex = prevIdx)
        }
    }

    fun toggleSlideshow() {
        val currentlyPlaying = _uiState.value.isSlideshowPlaying
        if (currentlyPlaying) {
            stopSlideshow()
        } else {
            startSlideshow()
        }
    }

    private fun startSlideshow() {
        stopSlideshow()
        _uiState.update { it.copy(isSlideshowPlaying = true) }
        slideshowJob = viewModelScope.launch {
            while (isActive) {
                delay(2000)
                nextImage()
            }
        }
    }

    private fun stopSlideshow() {
        slideshowJob?.cancel()
        slideshowJob = null
        _uiState.update { it.copy(isSlideshowPlaying = false) }
    }

    fun triggerTestNotification() {
        val context = getApplication<Application>()
        NotificationHelper.showNotification(
            context = context,
            title = "Push Notifications Active",
            message = "Excel Image Viewer offline cache is primed with ${_uiState.value.totalRowsInCache} items and ${_uiState.value.totalImagesInCache} images.",
            notificationId = NotificationHelper.NOTIFICATION_TEST
        )
        _uiState.update { it.copy(userMessage = "Test push notification dispatched!") }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    fun clearAllCache() {
        stopSlideshow()
        viewModelScope.launch {
            repository.clearAllData()
            _uiState.update {
                UiState(
                    statusText = "Cache cleared. Waiting for Excel file."
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopSlideshow()
    }
}
