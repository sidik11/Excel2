package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.local.AppConfigEntity
import com.example.data.local.AppDatabase
import com.example.data.local.CachedImageEntity
import com.example.data.local.ExcelRowEntity
import com.example.util.ExcelParser
import com.example.util.ParsedExcelRow
import com.example.util.ScannedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class DisplayImageItem(
    val fileName: String,
    val fileUri: String,
    val code: String,
    val name: String,
    val colour: String
)

data class NameCount(
    val name: String,
    val count: Int
)

class CatalogRepository(
    private val database: AppDatabase,
    private val context: Context
) {
    private val excelDao = database.excelDao()
    private val imageDao = database.imageDao()
    private val configDao = database.configDao()

    val allRowsFlow: Flow<List<ExcelRowEntity>> = excelDao.getAllRows()
    val allImagesFlow: Flow<List<CachedImageEntity>> = imageDao.getAllImages()

    suspend fun saveExcelCatalog(rows: List<ParsedExcelRow>, fileUri: String?, fileName: String) =
        withContext(Dispatchers.IO) {
            excelDao.clearAll()
            val entities = rows.map {
                ExcelRowEntity(
                    code = it.code,
                    normalizedCode = ExcelParser.normalizeCode(it.code),
                    name = it.name,
                    colour = it.colour,
                    rowNumber = it.rowNumber
                )
            }
            excelDao.insertAll(entities)

            if (fileUri != null) {
                configDao.set(AppConfigEntity("excel_uri", fileUri))
            }
            configDao.set(AppConfigEntity("excel_name", fileName))
            configDao.set(AppConfigEntity("last_sync", System.currentTimeMillis().toString()))
        }

    suspend fun saveCachedImages(images: List<ScannedImage>, folderUri: String?, folderName: String) =
        withContext(Dispatchers.IO) {
            imageDao.clearAll()
            val entities = images.map {
                CachedImageEntity(
                    normalizedCode = it.normalizedCode,
                    code = it.code,
                    fileName = it.fileName,
                    fileUri = it.fileUri,
                    relativePath = it.relativePath,
                    lastModified = it.lastModified
                )
            }
            imageDao.insertAll(entities)

            if (folderUri != null) {
                configDao.set(AppConfigEntity("folder_uri", folderUri))
            }
            configDao.set(AppConfigEntity("folder_name", folderName))
            configDao.set(AppConfigEntity("last_sync", System.currentTimeMillis().toString()))
        }

    suspend fun getSavedConfig(key: String): String? = withContext(Dispatchers.IO) {
        configDao.get(key)
    }

    suspend fun clearConfig(key: String) = withContext(Dispatchers.IO) {
        configDao.remove(key)
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        excelDao.clearAll()
        imageDao.clearAll()
        configDao.clearAll()
    }

    suspend fun getMatchingImages(
        nameFilter: String,
        colourFilter: String
    ): List<DisplayImageItem> = withContext(Dispatchers.IO) {
        val rows = excelDao.getAllRowsList()
        val allImages = imageDao.getAllImagesList()

        if (rows.isEmpty() || allImages.isEmpty()) {
            return@withContext emptyList()
        }

        // Map normalizedCode -> List<CachedImageEntity>
        val imageMap = allImages.groupBy { it.normalizedCode }

        // Filter rows by Name and Colour
        val filteredRows = rows.filter { row ->
            val nameMatch = nameFilter.isEmpty() || nameFilter == "__ALL__" || row.name.equals(nameFilter, ignoreCase = true)
            val colourMatch = colourFilter.isEmpty() || colourFilter == "__ALL__" ||
                ExcelParser.splitColours(row.colour).contains(ExcelParser.normalizeColour(colourFilter))
            nameMatch && colourMatch
        }

        val rowByCode = filteredRows.associateBy { ExcelParser.normalizeCode(it.code) }
        val targetCodes = filteredRows.map { it.code }.distinct()

        val results = mutableListOf<DisplayImageItem>()
        val seenUris = mutableSetOf<String>()

        for (code in targetCodes) {
            val normCode = ExcelParser.normalizeCode(code)
            val matchedImages = imageMap[normCode] ?: emptyList()
            val rowInfo = rowByCode[normCode]

            for (img in matchedImages) {
                if (!seenUris.contains(img.fileUri)) {
                    seenUris.add(img.fileUri)
                    results.add(
                        DisplayImageItem(
                            fileName = img.fileName,
                            fileUri = img.fileUri,
                            code = code,
                            name = rowInfo?.name ?: "",
                            colour = rowInfo?.colour ?: ""
                        )
                    )
                }
            }
        }

        results
    }
}
