package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

object PdfThumbnailRenderer {

    private val renderMutex = Mutex()

    suspend fun getPageCount(filePath: String): Int = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            runCatching {
                val file = File(filePath)
                if (!file.exists()) return@withContext 0
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        renderer.pageCount
                    }
                }
            }.getOrDefault(0)
        }
    }

    suspend fun renderPage(
        filePath: String,
        pageIndex: Int,
        targetWidthPx: Int = 360
    ): Bitmap? = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            runCatching {
                val file = File(filePath)
                if (!file.exists()) return@withContext null
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null
                        renderer.openPage(pageIndex).use { page ->
                            val ratio = if (page.width > 0) page.height.toFloat() / page.width.toFloat() else 1.414f
                            val width = max(64, targetWidthPx)
                            val height = max(64, (width * ratio).roundToInt())
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            canvas.drawColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap
                        }
                    }
                }
            }.getOrNull()
        }
    }
}
