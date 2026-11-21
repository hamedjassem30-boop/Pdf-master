package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.model.CompressionLevel
import com.example.model.OutputFileArtifact
import com.example.model.PickedFile
import com.example.model.WorkspaceSuccessResult
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt

object PdfOptimizeEngine {

    suspend fun compressPdf(
        context: Context,
        file: PickedFile,
        level: CompressionLevel,
        rasterizeForMaxSavings: Boolean,
        onProgress: (Float, String) -> Unit
    ): WorkspaceSuccessResult = withContext(Dispatchers.IO) {
        val srcFile = File(file.localPath)
        val originalBytes = srcFile.length()
        val baseName = file.name.substringBeforeLast('.')
        val outFile = FileHelper.createProcessedFile(context, "${baseName}_compressed.pdf")

        val jpegQuality = when (level) {
            CompressionLevel.LOW -> 0.82f
            CompressionLevel.MEDIUM -> 0.60f
            CompressionLevel.HIGH -> 0.40f
        }
        val maxImageDim = when (level) {
            CompressionLevel.LOW -> 1800
            CompressionLevel.MEDIUM -> 1300
            CompressionLevel.HIGH -> 920
        }

        onProgress(0.1f, "Analyzing embedded images & streams...")

        var pageCount = 0
        var recompressedImages = 0

        if (rasterizeForMaxSavings) {
            // Maximum compression via controlled page rendering & JPEG stream encoding
            ParcelFileDescriptor.open(srcFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    pageCount = renderer.pageCount
                    PDDocument().use { outDoc ->
                        val targetWidth = when (level) {
                            CompressionLevel.LOW -> 1600
                            CompressionLevel.MEDIUM -> 1240
                            CompressionLevel.HIGH -> 950
                        }
                        for (i in 0 until pageCount) {
                            coroutineContext.ensureActive()
                            renderer.openPage(i).use { page ->
                                val ptW = page.width.toFloat().coerceAtLeast(100f)
                                val ptH = page.height.toFloat().coerceAtLeast(100f)
                                val ratio = ptH / ptW
                                val bmpW = targetWidth
                                val bmpH = max(100, (bmpW * ratio).roundToInt())
                                val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                                Canvas(bmp).drawColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                val pdfPage = PDPage(PDRectangle(ptW, ptH))
                                outDoc.addPage(pdfPage)
                                val imgXObj = JPEGFactory.createFromImage(outDoc, bmp, jpegQuality)
                                bmp.recycle()
                                PDPageContentStream(outDoc, pdfPage).use { cs ->
                                    cs.drawImage(imgXObj, 0f, 0f, ptW, ptH)
                                }
                                recompressedImages++
                            }
                            onProgress(0.15f + 0.75f * (i + 1) / max(1, pageCount), "Optimizing page ${i + 1} of $pageCount...")
                        }
                        outDoc.documentCatalog.metadata = null
                        outDoc.save(outFile)
                    }
                }
            }
        } else {
            // Lossless vector preservation + in-place embedded bitmap optimization
            PDDocument.load(srcFile).use { doc ->
                pageCount = doc.numberOfPages
                for (i in 0 until pageCount) {
                    coroutineContext.ensureActive()
                    val page = doc.getPage(i)
                    val resources = page.resources
                    if (resources != null) {
                        for (name in resources.xObjectNames) {
                            val xObj = runCatching { resources.getXObject(name) }.getOrNull()
                            if (xObj is PDImageXObject) {
                                runCatching {
                                    val bmp = xObj.image
                                    if (bmp != null) {
                                        val scaled = scaleDownIfNeeded(bmp, maxImageDim)
                                        val optimized = JPEGFactory.createFromImage(doc, scaled, jpegQuality)
                                        if (scaled !== bmp) scaled.recycle()
                                        bmp.recycle()
                                        resources.put(name, optimized)
                                        recompressedImages++
                                    }
                                }
                            }
                        }
                    }
                    onProgress(0.15f + 0.7f * (i + 1) / max(1, pageCount), "Compressing page ${i + 1} of $pageCount...")
                }
                doc.documentCatalog.metadata = null
                doc.save(outFile)
            }
        }

        // Never inflate a file accidentally if vector-only PDF was already minimal
        val compressedBytes = outFile.length()
        val finalSize = if (compressedBytes > originalBytes && !rasterizeForMaxSavings) {
            srcFile.copyTo(outFile, overwrite = true)
            originalBytes
        } else {
            compressedBytes
        }

        val reductionPct = if (originalBytes > 0) {
            (((originalBytes - finalSize).toDouble() / originalBytes.toDouble()) * 100.0).coerceAtLeast(0.0)
        } else 0.0

        onProgress(1f, "Optimization complete")
        val artifact = OutputFileArtifact(
            name = outFile.name,
            path = outFile.absolutePath,
            mimeType = "application/pdf",
            sizeBytes = finalSize,
            pageCount = pageCount
        )
        WorkspaceSuccessResult(
            primaryFile = artifact,
            originalSizeBytes = originalBytes,
            summaryNote = String.format(
                Locale.US,
                "Optimized %d image stream(s). Reduced from %s to %s (%.1f%% saved).",
                recompressedImages,
                FileHelper.formatBytes(originalBytes),
                FileHelper.formatBytes(finalSize),
                reductionPct
            )
        )
    }

    private fun scaleDownIfNeeded(bmp: Bitmap, maxDim: Int): Bitmap {
        val w = bmp.width
        val h = bmp.height
        if (w <= maxDim && h <= maxDim) return bmp
        val scale = maxDim.toFloat() / max(w, h).toFloat()
        val newW = max(1, (w * scale).roundToInt())
        val newH = max(1, (h * scale).roundToInt())
        return Bitmap.createScaledBitmap(bmp, newW, newH, true)
    }
}
