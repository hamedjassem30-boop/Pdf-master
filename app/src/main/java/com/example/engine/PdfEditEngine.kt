package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.model.EditOverlay
import com.example.model.OutputFileArtifact
import com.example.model.PageNumberPosition
import com.example.model.PickedFile
import com.example.model.WorkspaceSuccessResult
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt

data class PageNumbersOptions(
    val enabled: Boolean = false,
    val position: PageNumberPosition = PageNumberPosition.BOTTOM_CENTER,
    val startNumber: Int = 1,
    val formatTemplate: String = "Page {n} of {total}",
    val fontSizeSp: Float = 12f,
    val colorArgb: Int = android.graphics.Color.parseColor("#0F172A")
)

data class WatermarkOptions(
    val enabled: Boolean = false,
    val text: String = "CONFIDENTIAL",
    val opacity: Float = 0.28f,
    val angleDegrees: Float = -35f,
    val fontSizeSp: Float = 44f,
    val colorArgb: Int = android.graphics.Color.parseColor("#E11D48")
)

data class HeaderFooterOptions(
    val enabled: Boolean = false,
    val headerText: String = "",
    val footerText: String = "",
    val fontSizeSp: Float = 11f,
    val colorArgb: Int = android.graphics.Color.parseColor("#334155")
)

object PdfEditEngine {

    suspend fun applyEdits(
        context: Context,
        file: PickedFile,
        overlays: List<EditOverlay>,
        pageNumbers: PageNumbersOptions = PageNumbersOptions(),
        watermark: WatermarkOptions = WatermarkOptions(),
        headerFooter: HeaderFooterOptions = HeaderFooterOptions(),
        outputSuffix: String = "edited",
        onProgress: (Float, String) -> Unit
    ): WorkspaceSuccessResult = withContext(Dispatchers.IO) {
        onProgress(0.1f, "Loading ${file.name}...")
        val baseName = file.name.substringBeforeLast('.')
        val outFile = FileHelper.createProcessedFile(context, "${baseName}_$outputSuffix.pdf")
        val typeface = ResourcesCompat.getFont(context, R.font.cairo) ?: Typeface.SANS_SERIF

        var totalPages = 0
        PDDocument.load(File(file.localPath)).use { doc ->
            totalPages = doc.numberOfPages
            val overlaysByPage = overlays.groupBy { it.pageIndex }

            for (pageIdx in 0 until totalPages) {
                coroutineContext.ensureActive()
                val page = doc.getPage(pageIdx)
                val pageOverlays = overlaysByPage[pageIdx].orEmpty()
                val needsStamp = pageOverlays.isNotEmpty() ||
                    pageNumbers.enabled ||
                    (watermark.enabled && watermark.text.isNotBlank()) ||
                    (headerFooter.enabled && (headerFooter.headerText.isNotBlank() || headerFooter.footerText.isNotBlank()))

                if (needsStamp) {
                    stampPageOverlay(
                        doc = doc,
                        page = page,
                        pageIndex = pageIdx,
                        totalPages = totalPages,
                        overlays = pageOverlays,
                        pageNumbers = pageNumbers,
                        watermark = watermark,
                        headerFooter = headerFooter,
                        typeface = typeface
                    )
                }
                onProgress(0.15f + 0.72f * (pageIdx + 1) / max(1, totalPages), "Updating page ${pageIdx + 1} of $totalPages...")
            }

            onProgress(0.92f, "Saving edited PDF...")
            doc.save(outFile)
        }

        onProgress(1f, "Edits saved")
        val artifact = OutputFileArtifact(
            name = outFile.name,
            path = outFile.absolutePath,
            mimeType = "application/pdf",
            sizeBytes = outFile.length(),
            pageCount = totalPages
        )
        WorkspaceSuccessResult(
            primaryFile = artifact,
            originalSizeBytes = file.sizeBytes,
            summaryNote = "Applied edits cleanly across $totalPages page(s)."
        )
    }

    private fun stampPageOverlay(
        doc: PDDocument,
        page: PDPage,
        pageIndex: Int,
        totalPages: Int,
        overlays: List<EditOverlay>,
        pageNumbers: PageNumbersOptions,
        watermark: WatermarkOptions,
        headerFooter: HeaderFooterOptions,
        typeface: Typeface
    ) {
        val box = page.cropBox ?: page.mediaBox
        val ptW = box.width.coerceAtLeast(100f)
        val ptH = box.height.coerceAtLeast(100f)
        val scale = 2.0f // 2x crisp overlay resolution
        val bmpW = (ptW * scale).roundToInt().coerceAtLeast(200)
        val bmpH = (ptH * scale).roundToInt().coerceAtLeast(200)

        val overlayBmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(overlayBmp)

        // 1. Watermark
        if (watermark.enabled && watermark.text.isNotBlank()) {
            val wmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = watermark.colorArgb
                alpha = (watermark.opacity.coerceIn(0.05f, 1f) * 255).roundToInt()
                textSize = watermark.fontSizeSp * scale
                this.typeface = Typeface.create(typeface, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.save()
            canvas.translate(bmpW / 2f, bmpH / 2f)
            canvas.rotate(watermark.angleDegrees)
            canvas.drawText(watermark.text, 0f, 0f, wmPaint)
            canvas.restore()
        }

        // 2. Header & Footer
        if (headerFooter.enabled) {
            val hfPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = headerFooter.colorArgb
                textSize = headerFooter.fontSizeSp * scale
                this.typeface = typeface
                textAlign = Paint.Align.CENTER
            }
            if (headerFooter.headerText.isNotBlank()) {
                canvas.drawText(headerFooter.headerText, bmpW / 2f, 28f * scale, hfPaint)
            }
            if (headerFooter.footerText.isNotBlank()) {
                canvas.drawText(headerFooter.footerText, bmpW / 2f, bmpH - 18f * scale, hfPaint)
            }
        }

        // 3. Page Numbers
        if (pageNumbers.enabled) {
            val num = pageNumbers.startNumber + pageIndex
            val label = pageNumbers.formatTemplate
                .replace("{n}", num.toString())
                .replace("{total}", (pageNumbers.startNumber + totalPages - 1).toString())
            val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = pageNumbers.colorArgb
                textSize = pageNumbers.fontSizeSp * scale
                this.typeface = Typeface.create(typeface, Typeface.BOLD)
            }
            val padX = 28f * scale
            val topY = 26f * scale
            val botY = bmpH - 16f * scale
            when (pageNumbers.position) {
                PageNumberPosition.BOTTOM_LEFT -> {
                    numPaint.textAlign = Paint.Align.LEFT
                    canvas.drawText(label, padX, botY, numPaint)
                }
                PageNumberPosition.BOTTOM_CENTER -> {
                    numPaint.textAlign = Paint.Align.CENTER
                    canvas.drawText(label, bmpW / 2f, botY, numPaint)
                }
                PageNumberPosition.BOTTOM_RIGHT -> {
                    numPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(label, bmpW - padX, botY, numPaint)
                }
                PageNumberPosition.TOP_LEFT -> {
                    numPaint.textAlign = Paint.Align.LEFT
                    canvas.drawText(label, padX, topY, numPaint)
                }
                PageNumberPosition.TOP_CENTER -> {
                    numPaint.textAlign = Paint.Align.CENTER
                    canvas.drawText(label, bmpW / 2f, topY, numPaint)
                }
                PageNumberPosition.TOP_RIGHT -> {
                    numPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(label, bmpW - padX, topY, numPaint)
                }
            }
        }

        // 4. Visual Editor Overlays
        overlays.forEach { overlay ->
            when (overlay) {
                is EditOverlay.TextOverlay -> {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = overlay.colorArgb
                        textSize = overlay.fontSizeSp * scale
                        this.typeface = Typeface.create(typeface, Typeface.BOLD)
                    }
                    canvas.drawText(overlay.text, overlay.normX * bmpW, overlay.normY * bmpH, p)
                }

                is EditOverlay.ImageOverlay -> {
                    runCatching {
                        val imgBmp = BitmapFactory.decodeFile(overlay.imagePath)
                        if (imgBmp != null) {
                            val dst = RectF(
                                overlay.normX * bmpW,
                                overlay.normY * bmpH,
                                (overlay.normX + overlay.normWidth) * bmpW,
                                (overlay.normY + overlay.normHeight) * bmpH
                            )
                            canvas.drawBitmap(imgBmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
                            imgBmp.recycle()
                        }
                    }
                }

                is EditOverlay.PathOverlay -> {
                    if (overlay.points.size >= 2) {
                        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = overlay.colorArgb
                            alpha = if (overlay.isHighlighter) 105 else 255
                            style = Paint.Style.STROKE
                            strokeWidth = (overlay.strokeWidthNorm * bmpW).coerceAtLeast(3f)
                            strokeCap = if (overlay.isHighlighter) Paint.Cap.SQUARE else Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                        }
                        val path = Path()
                        overlay.points.forEachIndexed { idx, pt ->
                            val x = pt.first * bmpW
                            val y = pt.second * bmpH
                            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        canvas.drawPath(path, p)
                    }
                }

                is EditOverlay.ShapeOverlay -> {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = overlay.colorArgb
                        style = Paint.Style.STROKE
                        strokeWidth = (overlay.strokeWidthNorm * bmpW).coerceAtLeast(3f)
                    }
                    val rect = RectF(
                        minOf(overlay.normLeft, overlay.normRight) * bmpW,
                        minOf(overlay.normTop, overlay.normBottom) * bmpH,
                        maxOf(overlay.normLeft, overlay.normRight) * bmpW,
                        maxOf(overlay.normTop, overlay.normBottom) * bmpH
                    )
                    if (overlay.isCircle) {
                        canvas.drawOval(rect, p)
                    } else {
                        canvas.drawRect(rect, p)
                    }
                }
            }
        }

        // Account for page rotation so overlay aligns with visual display
        val rotatedBmp = when (page.rotation % 360) {
            90 -> rotateBitmap(overlayBmp, -90f)
            180 -> rotateBitmap(overlayBmp, 180f)
            270 -> rotateBitmap(overlayBmp, 90f)
            else -> overlayBmp
        }
        if (rotatedBmp !== overlayBmp) overlayBmp.recycle()

        val xObj = LosslessFactory.createFromImage(doc, rotatedBmp)
        rotatedBmp.recycle()

        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.drawImage(xObj, box.lowerLeftX, box.lowerLeftY, box.width, box.height)
        }
    }

    private fun rotateBitmap(src: Bitmap, degrees: Float): Bitmap {
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }
}
