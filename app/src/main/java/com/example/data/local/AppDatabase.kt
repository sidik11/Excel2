package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ExcelDao {
    @Query("SELECT * FROM excel_rows ORDER BY id ASC")
    fun getAllRows(): Flow<List<ExcelRowEntity>>

    @Query("SELECT * FROM excel_rows ORDER BY id ASC")
    suspend fun getAllRowsList(): List<ExcelRowEntity>

    @Query("SELECT DISTINCT name FROM excel_rows ORDER BY name ASC")
    fun getDistinctNames(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<ExcelRowEntity>)

    @Query("DELETE FROM excel_rows")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM excel_rows")
    suspend fun getRowCount(): Int
}

@Dao
interface ImageDao {
    @Query("SELECT * FROM cached_images ORDER BY fileName ASC")
    fun getAllImages(): Flow<List<CachedImageEntity>>

    @Query("SELECT * FROM cached_images ORDER BY fileName ASC")
    suspend fun getAllImagesList(): List<CachedImageEntity>

    @Query("SELECT * FROM cached_images WHERE normalizedCode = :normalizedCode")
    suspend fun getImagesByCode(normalizedCode: String): List<CachedImageEntity>

    @Query("SELECT * FROM cached_images WHERE normalizedCode IN (:normalizedCodes)")
    suspend fun getImagesByCodes(normalizedCodes: List<String>): List<CachedImageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(images: List<CachedImageEntity>)

    @Query("DELETE FROM cached_images")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM cached_images")
    suspend fun getImageCount(): Int
}

@Dao
interface ConfigDao {
    @Query("SELECT value FROM app_config WHERE key = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM app_config WHERE key = :key LIMIT 1")
    fun getFlow(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(config: AppConfigEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(configs: List<AppConfigEntity>)

    @Query("SELECT * FROM app_config")
    suspend fun getAllList(): List<AppConfigEntity>

    @Query("DELETE FROM app_config WHERE key = :key")
    suspend fun remove(key: String)

    @Query("DELETE FROM app_config")
    suspend fun clearAll()
}

@Database(
    entities = [
        ExcelRowEntity::class,
        CachedImageEntity::class,
        AppConfigEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun excelDao(): ExcelDao
    abstract fun imageDao(): ImageDao
    abstract fun configDao(): ConfigDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "excel_image_viewer.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
