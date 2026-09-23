package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "excel_rows",
    indices = [
        Index(value = ["normalizedCode"]),
        Index(value = ["name"]),
        Index(value = ["colour"])
    ]
)
data class ExcelRowEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val code: String,
    val normalizedCode: String,
    val name: String,
    val colour: String,
    val rowNumber: Int
)

@Entity(
    tableName = "cached_images",
    indices = [
        Index(value = ["normalizedCode"]),
        Index(value = ["fileUri"], unique = true)
    ]
)
data class CachedImageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val normalizedCode: String,
    val code: String,
    val fileName: String,
    val fileUri: String,
    val relativePath: String,
    val lastModified: Long = 0L
)

@Entity(tableName = "app_config")
data class AppConfigEntity(
    @PrimaryKey
    val key: String,
    val value: String
)
