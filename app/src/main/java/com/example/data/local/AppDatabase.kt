package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "processed_records")
data class ProcessedRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val filePath: String,
    val mimeType: String,
    val toolId: String,
    val originalSizeBytes: Long,
    val outputSizeBytes: Long,
    val pageCount: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorite_tools")
data class FavoriteToolEntity(
    @PrimaryKey val toolId: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tool_usage")
data class ToolUsageEntity(
    @PrimaryKey val toolId: String,
    val useCount: Int = 1,
    val lastUsedAt: Long = System.currentTimeMillis()
)

@Dao
interface PdfMasterDao {

    @Query("SELECT * FROM processed_records ORDER BY timestamp DESC")
    fun observeProcessedRecords(): Flow<List<ProcessedRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: ProcessedRecordEntity): Long

    @Query("DELETE FROM processed_records WHERE id = :id")
    suspend fun deleteRecordById(id: Long)

    @Query("DELETE FROM processed_records")
    suspend fun clearAllRecords()

    @Query("SELECT * FROM favorite_tools ORDER BY addedAt DESC")
    fun observeFavorites(): Flow<List<FavoriteToolEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteToolEntity)

    @Query("DELETE FROM favorite_tools WHERE toolId = :toolId")
    suspend fun removeFavorite(toolId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_tools WHERE toolId = :toolId)")
    suspend fun isFavorite(toolId: String): Boolean

    @Query("SELECT * FROM tool_usage ORDER BY useCount DESC, lastUsedAt DESC")
    fun observeToolUsage(): Flow<List<ToolUsageEntity>>

    @Query("SELECT * FROM tool_usage WHERE toolId = :toolId LIMIT 1")
    suspend fun getToolUsage(toolId: String): ToolUsageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertToolUsage(usage: ToolUsageEntity)
}

@Database(
    entities = [
        ProcessedRecordEntity::class,
        FavoriteToolEntity::class,
        ToolUsageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pdfMasterDao(): PdfMasterDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pdf_master_pro.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
