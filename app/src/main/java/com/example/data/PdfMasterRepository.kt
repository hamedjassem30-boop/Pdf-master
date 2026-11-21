package com.example.data

import android.content.Context
import com.example.data.local.FavoriteToolEntity
import com.example.data.local.PdfMasterDao
import com.example.data.local.ProcessedRecordEntity
import com.example.data.local.ToolUsageEntity
import com.example.model.OutputFileArtifact
import com.example.model.PdfToolId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

data class DashboardMetrics(
    val totalProcessedCount: Int = 0,
    val totalBytesSaved: Long = 0L,
    val localStorageUsedBytes: Long = 0L
)

class PdfMasterRepository(
    private val context: Context,
    private val dao: PdfMasterDao
) {
    val processedRecords: Flow<List<ProcessedRecordEntity>> = dao.observeProcessedRecords()

    val favoriteToolIds: Flow<Set<PdfToolId>> = dao.observeFavorites().map { list ->
        list.mapNotNull { PdfToolId.fromId(it.toolId) }.toSet()
    }

    val frequentToolIds: Flow<List<ToolUsageEntity>> = dao.observeToolUsage()

    val dashboardMetrics: Flow<DashboardMetrics> = processedRecords.map { records ->
        val validRecords = records.filter { File(it.filePath).exists() }
        val saved = validRecords
            .filter { it.originalSizeBytes > it.outputSizeBytes && it.toolId == PdfToolId.COMPRESS_PDF.name }
            .sumOf { (it.originalSizeBytes - it.outputSizeBytes).coerceAtLeast(0L) }
        val processedDir = File(context.filesDir, "processed")
        val storageUsed = processedDir.listFiles()?.sumOf { it.length() } ?: 0L
        DashboardMetrics(
            totalProcessedCount = records.size,
            totalBytesSaved = saved,
            localStorageUsedBytes = storageUsed
        )
    }

    suspend fun toggleFavorite(toolId: PdfToolId) {
        if (dao.isFavorite(toolId.name)) {
            dao.removeFavorite(toolId.name)
        } else {
            dao.addFavorite(FavoriteToolEntity(toolId.name))
        }
    }

    suspend fun recordToolProcessed(
        toolId: PdfToolId,
        output: OutputFileArtifact,
        originalSizeBytes: Long
    ) {
        dao.insertRecord(
            ProcessedRecordEntity(
                fileName = output.name,
                filePath = output.path,
                mimeType = output.mimeType,
                toolId = toolId.name,
                originalSizeBytes = originalSizeBytes,
                outputSizeBytes = output.sizeBytes,
                pageCount = output.pageCount
            )
        )
        val existing = dao.getToolUsage(toolId.name)
        dao.upsertToolUsage(
            ToolUsageEntity(
                toolId = toolId.name,
                useCount = (existing?.useCount ?: 0) + 1,
                lastUsedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteRecord(record: ProcessedRecordEntity) {
        withContext(Dispatchers.IO) {
            runCatching { File(record.filePath).delete() }
        }
        dao.deleteRecordById(record.id)
    }

    suspend fun cleanupTemporaryFiles(): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        val dirs = listOfNotNull(
            File(context.cacheDir, "inputs"),
            File(context.cacheDir, "renders"),
            File(context.cacheDir, "temp_out")
        )
        dirs.forEach { dir ->
            if (dir.exists()) {
                dir.walkBottomUp().forEach { f ->
                    if (f.isFile) {
                        freed += f.length()
                        f.delete()
                    }
                }
            }
        }
        freed
    }
}
