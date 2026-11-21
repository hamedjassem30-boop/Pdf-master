package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.HtmlCompat
import com.example.R
import com.example.model.OutputFileArtifact
import com.example.model.PickedFile
import com.example.model.WorkspaceSuccessResult
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.StringReader
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.min

object PdfConvertToEngine {

    private const val A4_WIDTH = 595f
    private const val A4_HEIGHT = 842f
    private const val RENDER_SCALE = 2f

    suspend fun imagesToPdf(
        context: Context,
        files: List<PickedFile>,
        fitToA4: Boolean,
        marginPoints: Float,
        onProgress: (Float, String) -> Unit
    ): WorkspaceSuccessResult = withContext(Dispatchers.IO) {
        require(files.isNotEmpty()) { "Please select at least one image." }
        val baseName = files.first().name.substringBeforeLast('.')
        val outName = if (files.size == 1) "${baseName}.pdf" else "images_bundle.pdf"
        val outFile = FileHelper.createProcessedFile(context, outName)

        var totalPages = 0
        PDDocument().use { doc ->
            files.forEachIndexed { idx, file ->
                coroutineContext.ensureActive()
                onProgress(0.1f + 0.75f * idx / files.size, "Adding ${file.name}...")
                val bmp = decodeScaledBitmap(File(file.localPath), 2400)
                    ?: throw IllegalArgumentException("Could not decode image: ${file.name}")

                val pageRect = if (fitToA4) {
                    if (bmp.width > bmp.height) {
                        PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width)
                    } else {
                        PDRectangle.A4
                    }
                } else {
                    PDRectangle(bmp.width.toFloat(), bmp.height.toFloat())
                }

                val page = PDPage(pageRect)
                doc.addPage(page)

                val isPng = file.name.lowercase(Locale.US).endsWith(".png")
                val xObj = if (isPng) {
                    LosslessFactory.createFromImage(doc, bmp)
                } else {
                    JPEGFactory.createFromImage(doc, bmp, 0.90f)
                }

                val availW = (pageRect.width - 2f * marginPoints).coerceAtLeast(50f)
                val availH = (pageRect.height - 2f * marginPoints).coerceAtLeast(50f)
                val scale = min(availW / bmp.width.toFloat(), availH / bmp.height.toFloat())
                val drawW = bmp.width * scale
                val drawH = bmp.height * scale
                val drawX = (pageRect.width - drawW) / 2f
                val drawY = (pageRect.height - drawH) / 2f

                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(xObj, drawX, drawY, drawW, drawH)
                }
                bmp.recycle()
                totalPages++
            }
            onProgress(0.92f, "Writing PDF document...")
            doc.save(outFile)
        }

        onProgress(1f, "Converted $totalPages image(s) to PDF")
        val artifact = OutputFileArtifact(
            name = outFile.name,
            path = outFile.absolutePath,
            mimeType = "application/pdf",
            sizeBytes = outFile.length(),
            pageCount = totalPages
        )
        WorkspaceSuccessResult(
            primaryFile = artifact,
            originalSizeBytes = files.sumOf { it.sizeBytes },
            summaryNote = "Created $totalPages-page PDF from ${files.size} image(s)."
        )
    }

    suspend fun textToPdf(
        context: Context,
        file: PickedFile,
        fontSizePt: Float,
        onProgress: (Float, String) -> Unit
    ): WorkspaceSuccessResult = withContext(Dispatchers.IO) {
        onProgress(0.15f, "Reading text file...")
        val content = File(file.localPath).readText(Charsets.UTF_8)
        val outFile = FileHelper.createProcessedFile(context, "${file.name.substringBeforeLast('.')}.pdf")
        val typeface = runCatching { ResourcesCompat.getFont(context, R.font.cairo) }.getOrNull() ?: Typeface.SANS_SERIF
        val pages = renderCharSequenceToPdf(
            text = content,
            outFile = outFile,
            typeface = typeface,
            fontSize = fontSizePt,
            onProgress = onProgress
        )
        val artifact = OutputFileArtifact(
            name = outFile.name,
            path = outFile.absolutePath,
            mimeType = "application/pdf",
            sizeBytes = outFile.length(),
            pageCount = pages
        )
        WorkspaceSuccessResult(
            primaryFile = artifact,
            originalSizeBytes = file.sizeBytes,
            summaryNote = "Converted TXT (${content.length} chars) into $pages PDF page(s)."
        )
    }

    suspend fun htmlToPdf(
        context: Context,
        file: PickedFile,
        onProgress: (Float, String) -> Unit
    ): WorkspaceSuccessResult = withContext(Dispatchers.IO) {
        onProgress(0.15f, "Parsing HTML markup...")
        val rawHtml = File(file.localPath).readText(Charsets.UTF_8)
        val spanned = HtmlCompat.fromHtml(rawHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
        val outFile = FileHelper.createProcessedFile(context, "${file.name.substringBeforeLast('.')}.pdf")
        val typeface = runCatching { ResourcesCompat.getFont(context, R.font.cairo) }.getOrNull() ?: Typeface.SANS_SERIF
        val pages = renderCharSequenceToPdf(
            text = spanned,
            outFile = outFile,
            typeface = typeface,
            fontSize = 12f,
            onProgress = onProgress
        )
        val artifact = OutputFileArtifact(
            name = outFile.name,
            path = outFile.absolutePath,
            mimeType = "application/pdf",
            sizeBytes = outFile.length(),
            pageCount = pages
        )
        WorkspaceSuccessResult(
            primaryFile = artifact,
            originalSizeBytes = file.sizeBytes,
            summaryNote = "Rendered HTML into $pages formatted PDF page(s)."
        )
    }

    suspend fun wordToPdf(
        context: Context,
        file: PickedFile,
        onProgress: (Float, String) -> Unit
    ): WorkspaceSuccessResult = withContext(Dispatchers.IO) {
        val ext = file.name.substringAfterLast('.', "").lowercase(Locale.US)
        if (ext == "doc") {
            throw IllegalArgumentException(
                "Legacy binary .doc format requires server-side LibreOffice conversion. Please save as .docx for instant local conversion."
            )
        }
        onProgress(0.15f, "Reading DOCX package...")
        val extracted = parseDocxParagraphs(File(file.localPath))
        if (extracted.isBlank()) {
            throw IllegalArgumentException("No readable text content found in ${file.name}.")
        }
        val outFile = FileHelper.createProcessedFile(context, "${file.name.substringBeforeLast('.')}.pdf")
        val typeface = runCatching { ResourcesCompat.getFont(context, R.font.cairo) }.getOrNull() ?: Typeface.SANS_SERIF
        val pages = renderCharSequenceToPdf(
            text = extracted,
            outFile = outFile,
            typeface = typeface,
            fontSize = 12f,
            onProgress = onProgress
        )
        val artifact = OutputFileArtifact(
            name = outFile.name,
            path = outFile.absolutePath,
            mimeType = "application/pdf",
            sizeBytes = outFile.length(),
            pageCount = pages
        )
        WorkspaceSuccessResult(
            primaryFile = artifact,
            originalSizeBytes = file.sizeBytes,
            summaryNote = "Converted DOCX document into $pages PDF page(s) locally."
        )
    }

    suspend fun excelToPdf(
        context: Context,
        file: PickedFile,
        onProgress: (Float, String) -> Unit
    ): WorkspaceSuccessResult = withContext(Dispatchers.IO) {
        val ext = file.name.substringAfterLast('.', "").lowercase(Locale.US)
        onProgress(0.15f, "Reading spreadsheet data...")
        val rows: List<List<String>> = when (ext) {
            "csv" -> parseCsvRows(File(file.localPath))
            "xlsx" -> parseXlsxRows(File(file.localPath))
            else -> throw IllegalArgumentException("Please select an .xlsx or .csv spreadsheet.")
        }
        if (rows.isEmpty()) throw IllegalArgumentException("Spreadsheet contains no readable cells.")

        val outFile = FileHelper.createProcessedFile(context, "${file.name.substringBeforeLast('.')}.pdf")
        val typeface = runCatching { ResourcesCompat.getFont(context, R.font.cairo) }.getOrNull() ?: Typeface.SANS_SERIF
        val pageCount = renderTableRowsToLandscapePdf(rows, outFile, typeface, onProgress)

        val artifact = OutputFileArtifact(
            name = outFile.name,
            path = outFile.absolutePath,
            mimeType = "application/pdf",
            sizeBytes = outFile.length(),
            pageCount = pageCount
        )
        WorkspaceSuccessResult(
            primaryFile = artifact,
            originalSizeBytes = file.sizeBytes,
            summaryNote = "Rendered ${rows.size} row(s) across $pageCount landscape PDF page(s)."
        )
    }

    suspend fun pptToPdf(
        context: Context,
        file: PickedFile,
        onProgress: (Float, String) -> Unit
    ): WorkspaceSuccessResult = withContext(Dispatchers.IO) {
        val ext = file.name.substringAfterLast('.', "").lowercase(Locale.US)
        if (ext != "pptx") {
            throw IllegalArgumentException("Please select a .pptx PowerPoint presentation.")
        }
        onProgress(0.15f, "Reading PPTX slides...")
        val slides = parsePptxSlides(File(file.localPath))
        if (slides.isEmpty()) throw IllegalArgumentException("No slides found inside ${file.name}.")

        val outFile = FileHelper.createProcessedFile(context, "${file.name.substringBeforeLast('.')}.pdf")
        val typeface = runCatching { ResourcesCompat.getFont(context, R.font.cairo) }.getOrNull() ?: Typeface.SANS_SERIF

        val slideW = 960
        val slideH = 540
        val bmpW = (slideW * 1.5f).toInt()
        val bmpH = (slideH * 1.5f).toInt()

        PDDocument().use { doc ->
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                textSize = 24f * 1.5f
                this.typeface = Typeface.create(typeface, Typeface.BOLD)
            }
            val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#334155")
                textSize = 16f * 1.5f
                this.typeface = typeface
            }
            val barPaint = Paint().apply { color = Color.parseColor("#E11D48") }
            val bgPaint = Paint().apply { color = Color.parseColor("#F8FAFC") }

            slides.forEachIndexed { idx, slideLines ->
                coroutineContext.ensureActive()
                val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawRect(0f, 0f, bmpW.toFloat(), bmpH.toFloat(), bgPaint)
                canvas.drawRect(0f, 0f, bmpW.toFloat(), 12f, barPaint)

                val title = slideLines.firstOrNull() ?: "Slide ${idx + 1}"
                val titleLayout = StaticLayout.Builder
                    .obtain(title, 0, title.length, titlePaint, bmpW - 144)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .build()
                canvas.save()
                canvas.translate(72f, 64f)
                titleLayout.draw(canvas)
                canvas.restore()

                val bodyText = slideLines.drop(1).joinToString("\n\n") { "• $it" }
                if (bodyText.isNotEmpty()) {
                    val bodyLayout = StaticLayout.Builder
                        .obtain(bodyText, 0, bodyText.length, bodyPaint, bmpW - 144)
                        .setLineSpacing(8f, 1.15f)
                        .build()
                    canvas.save()
                    canvas.translate(72f, 96f + titleLayout.height)
                    bodyLayout.draw(canvas)
                    canvas.restore()
                }

                val pdfPage = PDPage(PDRectangle(slideW.toFloat(), slideH.toFloat()))
                doc.addPage(pdfPage)
                val xObj = JPEGFactory.createFromImage(doc, bmp, 0.90f)
                bmp.recycle()
                PDPageContentStream(doc, pdfPage).use { cs ->
                    cs.drawImage(xObj, 0f, 0f, slideW.toFloat(), slideH.toFloat())
                }
                onProgress(0.25f + 0.65f * (idx + 1) / slides.size, "Rendering slide ${idx + 1} of ${slides.size}...")
            }

            doc.save(outFile)
        }

        onProgress(1f, "Converted ${slides.size} slides")
        val artifact = OutputFileArtifact(
            name = outFile.name,
            path = outFile.absolutePath,
            mimeType = "application/pdf",
            sizeBytes = outFile.length(),
            pageCount = slides.size
        )
        WorkspaceSuccessResult(
            primaryFile = artifact,
            originalSizeBytes = file.sizeBytes,
            summaryNote = "Converted ${slides.size} PPTX slide(s) to PDF."
        )
    }

    private suspend fun renderCharSequenceToPdf(
        text: CharSequence,
        outFile: File,
        typeface: Typeface,
        fontSize: Float,
        onProgress: (Float, String) -> Unit
    ): Int {
        val margin = (48 * RENDER_SCALE).toInt()
        val bmpW = (A4_WIDTH * RENDER_SCALE).toInt()
        val bmpH = (A4_HEIGHT * RENDER_SCALE).toInt()
        val contentWidth = bmpW - margin * 2
        val contentHeight = bmpH - margin * 2

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = fontSize * RENDER_SCALE
            this.typeface = typeface
        }

        val safeText = if (text.isEmpty()) " " else text
        val fullLayout = StaticLayout.Builder
            .obtain(safeText, 0, safeText.length, textPaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(6f, 1.25f)
            .build()

        var pageCount = 0
        PDDocument().use { doc ->
            var lineIdx = 0
            val totalLines = fullLayout.lineCount
            while (lineIdx < totalLines) {
                coroutineContext.ensureActive()
                val pageStartY = fullLayout.getLineTop(lineIdx)
                var lastFitLine = lineIdx
                while (lastFitLine < totalLines) {
                    val lineBottom = fullLayout.getLineBottom(lastFitLine)
                    if (lineBottom - pageStartY > contentHeight && lastFitLine > lineIdx) {
                        break
                    }
                    lastFitLine++
                }
                val endLineExclusive = lastFitLine
                val pageEndY = fullLayout.getLineBottom(endLineExclusive - 1)

                pageCount++
                val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawColor(Color.WHITE)
                canvas.save()
                canvas.translate(margin.toFloat(), (margin - pageStartY).toFloat())
                canvas.clipRect(0, pageStartY, contentWidth, pageEndY)
                fullLayout.draw(canvas)
                canvas.restore()

                val page = PDPage(PDRectangle(A4_WIDTH, A4_HEIGHT))
                doc.addPage(page)
                val xObj = JPEGFactory.createFromImage(doc, bmp, 0.92f)
                bmp.recycle()
                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(xObj, 0f, 0f, A4_WIDTH, A4_HEIGHT)
                }

                lineIdx = endLineExclusive
                onProgress(0.25f + 0.65f * lineIdx / maxOf(1, totalLines), "Rendering page $pageCount...")
            }

            if (pageCount == 0) {
                pageCount = 1
                doc.addPage(PDPage(PDRectangle(A4_WIDTH, A4_HEIGHT)))
            }
            doc.save(outFile)
        }
        onProgress(1f, "Completed ($pageCount pages)")
        return pageCount
    }

    private suspend fun renderTableRowsToLandscapePdf(
        rows: List<List<String>>,
        outFile: File,
        typeface: Typeface,
        onProgress: (Float, String) -> Unit
    ): Int {
        val pageW = A4_HEIGHT // 842
        val pageH = A4_WIDTH  // 595
        val bmpW = (pageW * RENDER_SCALE).toInt()
        val bmpH = (pageH * RENDER_SCALE).toInt()
        val margin = 36f * RENDER_SCALE
        val maxCols = (rows.maxOfOrNull { it.size } ?: 1).coerceIn(1, 10)
        val colWidth = (bmpW - 2f * margin) / maxCols
        val rowHeight = 24f * RENDER_SCALE

        val headerBg = Paint().apply { color = Color.parseColor("#0F172A") }
        val altBg = Paint().apply { color = Color.parseColor("#F1F5F9") }
        val borderPaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 10f * RENDER_SCALE
            this.typeface = Typeface.create(typeface, Typeface.BOLD)
        }
        val cellTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            textSize = 9.5f * RENDER_SCALE
            this.typeface = typeface
        }

        var pageCount = 0
        PDDocument().use { doc ->
            val rowsPerPage = ((bmpH - 2f * margin) / rowHeight).toInt().coerceAtLeast(1)
            var rowIdx = 0
            while (rowIdx < rows.size) {
                coroutineContext.ensureActive()
                pageCount++
                val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawColor(Color.WHITE)

                var y = margin
                val endIdx = min(rows.size, rowIdx + rowsPerPage)
                for (r in rowIdx until endIdx) {
                    val row = rows[r]
                    val isHeader = r == 0
                    if (isHeader) {
                        canvas.drawRect(margin, y, bmpW - margin, y + rowHeight, headerBg)
                    } else if (r % 2 == 0) {
                        canvas.drawRect(margin, y, bmpW - margin, y + rowHeight, altBg)
                    }
                    for (c in 0 until maxCols) {
                        val x = margin + c * colWidth
                        canvas.drawRect(x, y, x + colWidth, y + rowHeight, borderPaint)
                        val cell = row.getOrNull(c).orEmpty()
                        val paint = if (isHeader) headerTextPaint else cellTextPaint
                        val clipped = ellipsizeText(cell, paint, colWidth - 12f)
                        canvas.drawText(clipped, x + 6f, y + rowHeight - 12f, paint)
                    }
                    y += rowHeight
                }

                val page = PDPage(PDRectangle(pageW, pageH))
                doc.addPage(page)
                val xObj = JPEGFactory.createFromImage(doc, bmp, 0.92f)
                bmp.recycle()
                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(xObj, 0f, 0f, pageW, pageH)
                }

                rowIdx = endIdx
                onProgress(0.25f + 0.65f * rowIdx / rows.size, "Rendering table page $pageCount...")
            }
            doc.save(outFile)
        }
        onProgress(1f, "Completed ($pageCount pages)")
        return pageCount
    }

    private fun ellipsizeText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) {
            end--
        }
        return text.substring(0, end) + "…"
    }

    private fun decodeScaledBitmap(file: File, maxDim: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        var sample = 1
        while (opts.outWidth / sample > maxDim || opts.outHeight / sample > maxDim) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
    }

    private fun parseDocxParagraphs(file: File): String {
        var docXml: String? = null
        ZipInputStream(FileInputStream(file)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    docXml = zis.bufferedReader(Charsets.UTF_8).readText()
                    break
                }
                entry = zis.nextEntry
            }
        }
        val xml = docXml ?: return ""
        val sb = SpannableStringBuilder()
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        parser.setInput(StringReader(xml))
        val currentPara = StringBuilder()
        var inText = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "t" -> inText = true
                        "br" -> currentPara.append('\n')
                        "tab" -> currentPara.append("    ")
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inText) currentPara.append(parser.text)
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "t" -> inText = false
                        "p" -> {
                            sb.append(currentPara.toString()).append("\n\n")
                            currentPara.clear()
                        }
                    }
                }
            }
            event = parser.next()
        }
        return sb.toString().trim()
    }

    private fun parseCsvRows(file: File): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        BufferedReader(InputStreamReader(FileInputStream(file), Charsets.UTF_8)).use { reader ->
            reader.forEachLine { line ->
                if (line.isNotBlank()) {
                    rows.add(line.split(',').map { it.trim().removeSurrounding("\"") })
                }
            }
        }
        return rows
    }

    private fun parseXlsxRows(file: File): List<List<String>> {
        val sharedStrings = mutableListOf<String>()
        var sheetXml: String? = null

        ZipInputStream(FileInputStream(file)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "xl/sharedStrings.xml") {
                    val xml = zis.bufferedReader(Charsets.UTF_8).readText()
                    sharedStrings.addAll(parseSharedStringsXml(xml))
                } else if (entry.name == "xl/worksheets/sheet1.xml" && sheetXml == null) {
                    sheetXml = zis.bufferedReader(Charsets.UTF_8).readText()
                }
                entry = zis.nextEntry
            }
        }
        val xml = sheetXml ?: return emptyList()
        val rows = mutableListOf<List<String>>()
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        parser.setInput(StringReader(xml))

        var currentRow = mutableListOf<String>()
        var cellType: String? = null
        var inVal = false
        val valBuilder = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> currentRow = mutableListOf()
                        "c" -> cellType = parser.getAttributeValue(null, "t")
                        "v", "t" -> {
                            inVal = true
                            valBuilder.clear()
                        }
                    }
                }
                XmlPullParser.TEXT -> if (inVal) valBuilder.append(parser.text)
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "v", "t" -> inVal = false
                        "c" -> {
                            val raw = valBuilder.toString()
                            val value = if (cellType == "s") {
                                raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: raw
                            } else raw
                            currentRow.add(value)
                            valBuilder.clear()
                        }
                        "row" -> if (currentRow.isNotEmpty()) rows.add(currentRow)
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

    private fun parseSharedStringsXml(xml: String): List<String> {
        val list = mutableListOf<String>()
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        parser.setInput(StringReader(xml))
        var inSi = false
        var inT = false
        val sb = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "si") {
                        inSi = true
                        sb.clear()
                    } else if (parser.name == "t" && inSi) {
                        inT = true
                    }
                }
                XmlPullParser.TEXT -> if (inT) sb.append(parser.text)
                XmlPullParser.END_TAG -> {
                    if (parser.name == "t") inT = false
                    else if (parser.name == "si") {
                        inSi = false
                        list.add(sb.toString())
                    }
                }
            }
            event = parser.next()
        }
        return list
    }

    private fun parsePptxSlides(file: File): List<List<String>> {
        val slideXmlMap = sortedMapOf<Int, String>()
        ZipInputStream(FileInputStream(file)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val match = Regex("ppt/slides/slide(\\d+)\\.xml").matchEntire(entry.name)
                if (match != null) {
                    val slideNum = match.groupValues[1].toIntOrNull() ?: 0
                    slideXmlMap[slideNum] = zis.bufferedReader(Charsets.UTF_8).readText()
                }
                entry = zis.nextEntry
            }
        }
        return slideXmlMap.values.map { xml ->
            val lines = mutableListOf<String>()
            val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
            parser.setInput(StringReader(xml))
            val current = StringBuilder()
            var inT = false
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> if (parser.name == "t") inT = true
                    XmlPullParser.TEXT -> if (inT) current.append(parser.text)
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "t") inT = false
                        else if (parser.name == "p") {
                            val line = current.toString().trim()
                            if (line.isNotEmpty()) lines.add(line)
                            current.clear()
                        }
                    }
                }
                event = parser.next()
            }
            if (lines.isEmpty()) listOf("(Blank Slide)") else lines
        }
    }
}
