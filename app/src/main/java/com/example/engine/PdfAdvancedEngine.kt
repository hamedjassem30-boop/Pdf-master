package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.model.BookmarkInput
import com.example.model.DiffLine
import com.example.model.DiffLineType
import com.example.model.OutputFileArtifact
import com.example.model.PageComparison
import com.example.model.PdfMetadataInfo
import com.example.model.PickedFile
import com.example.model.SearchMatch
import com.example.model.WorkspaceSuccessResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDMetadata
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.roundToInt

object PdfAdvancedEngine {

    suspend fun ocrPdf(
        context: Context,
        file: PickedFile,
        onProgress: (Float, String) -> Unit
    ): WorkspaceSuccessResult = withContext(Dispatchers.IO) {
        onProgress(0.1f, "Initializing on-device ML Kit OCR...")
        val srcFile = File(file.localPath)
        val baseName = file.name.substringBeforeLast('.')
        val outFile = FileHelper.createProcessedFile(context, "${baseName}_ocr.txt")

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val recognizedBuilder = StringBuilder()
        var pageCount = 0

        try {
            ParcelFileDescriptor.open(srcFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    pageCount = renderer.pageCount
                    if (pageCount == 0) throw IllegalStateException("PDF contains 0 pages.")

                    for (i in 0 until pageCount) {
                        coroutineContext.ensureActive()
                        renderer.openPage(i).use { page ->
                            val w = 1400
                            val ratio = if (page.width > 0) page.height.toFloat() / page.width.toFloat() else 1.414f
                            val h = max(200, (w * ratio).roundToInt())
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            Canvas(bmp).drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val inputImage = InputImage.fromBitmap(bmp, 0)
                            val pageText = suspendCancellableCoroutine<String> { cont ->
                                recognizer.process(inputImage)
                                    .addOnSuccessListener { visionText ->
                                        if (cont.isActive) cont.resume(visionText.text)
                                    }
                                    .addOnFailureListener { e ->
                                        if (cont.isActive) cont.resumeWithException(e)
                                    }
                            }
                            bmp.recycle()
                            recognizedBuilder.append("=== Page ${i + 1} (OCR) ===\n")
                            recognizedBuilder.append(pageText.ifBlank { "(No text recognized)" }).append("\n\n")
                        }
                        onProgress(0.15f + 0.78f * (i + 1) / pageCount, "OCR scanning page ${i + 1} of $pageCount...")
                    }
                }
            }
        } finally {
            recognizer.close()
        }

        val fullText = recognizedBuilder.toString().trim()
        outFile.writeText(fullText, Charsets.UTF_8)
        onProgress(1f, "OCR complete")

        val artifact = OutputFileArtifact(
            name = outFile.name,
            path = outFile.absolutePath,
            mimeType = "text/plain",
            sizeBytes = outFile.length(),
            pageCount = pageCount
        )
        WorkspaceSuccessResult(
            primaryFile = artifact,
            originalSizeBytes = file.sizeBytes,
            extractedText = fullText,
            summaryNote = "Scanned $pageCount page(s) with on-device ML Kit OCR (Latin script)."
        )
    }

    suspend fun searchInsidePdf(
        context: Context,
        file: PickedFile,
        query: String,
        onProgress: (Float, String) -> Unit
    ): WorkspaceSuccessResult = withContext(Dispatchers.IO) {
        require(query.isNotBlank()) { "Enter a keyword or phrase to search." }
        onProgress(0.1f, "Searching across document pages...")
        val matches = mutableListOf<SearchMatch>()
        var pageCount = 0
        val report = StringBuilder("Search Report for \"$query\" in ${file.name}\n\n")

        PDDocument.load(File(file.localPath)).use { doc ->
            pageCount = doc.numberOfPages
            val stripper = PDFTextStripper()
            val regex = Regex(Regex.escape(query.trim()), RegexOption.IGNORE_CASE)

            for (p in 1..pageCount) {
                coroutineContext.ensureActive()
                stripper.startPage = p
                stripper.endPage = p
                val text = stripper.getText(doc)
                val found = regex.findAll(text).toList()
                if (found.isNotEmpty()) {
                    val firstRange = found.first().range
                    val snippetStart = (firstRange.first - 45).coerceAtLeast(0)
                    val snippetEnd = (firstRange.last + 65).coerceAtMost(text.length)
                    val snippet = text.substring(snippetStart, snippetEnd)
                        .replace('\n', ' ')
                        .replace(Regex("\\s+"), " ")
                        .trim()
                    matches.add(SearchMatch(p, found.size, "...$snippet..."))
                    report.append("Page $p (${found.size} match(es)): ...$snippet...\n")
                }
                onProgress(0.15f + 0.78f * p / max(1, pageCount), "Searching page $p of $pageCount...")
            }
        }

        val outFile = FileHelper.createProcessedFile(
            context,
            "${file.name.substringBeforeLast('.')}_search_report.txt"
        )
        outFile.writeText(report.toString(), Charsets.UTF_8)
        onProgress(1f, "Found ${matches.sumOf { it.matchCount }} matches")

        val artifact = OutputFileArtifact(
            name = outFile.name,
            path = outFile.absolutePath,
            mimeType = "text/plain",
            sizeBytes = outFile.length(),
            pageCount = pageCount
        )
        WorkspaceSuccessResult(
            primaryFile = artifact,
            originalSizeBytes = file.sizeBytes,
            searchMatches = matches,
            summaryNote = "Found ${matches.sumOf { it.matchCount }} occurrence(s) across ${matches.size} page(s)."
        )
    }

    suspend fun extractImages(
        context: Context,
        file: PickedFile,
        onProgress: (Float, String) -> Unit
    ): WorkspaceSuccessResult = withContext(Dispatchers.IO) {
        onProgress(0.1f, "Scanning embedded image XObjects...")
        val baseName = file.name.substringBeforeLast('.')
        val extractedArtifacts = mutableListOf<OutputFileArtifact>()
        var imgCounter = 0

        PDDocument.load(File(file.localPath)).use { doc ->
            val total = doc.numberOfPages
            for (i in 0 until total) {
                coroutineContext.ensureActive()
                val resources = doc.getPage(i).resources
                if (resources != null) {
                    for (name in resources.xObjectNames) {
                        val xObj = runCatching { resources.getXObject(name) }.getOrNull()
                        if (xObj is PDImageXObject) {
                            runCatching {
                                val bmp = xObj.image
                                if (bmp != null) {
                                    imgCounter++
                                    val outFile = FileHelper.createProcessedFile(
                                        context,
                                        "${baseName}_p${i + 1}_img$imgCounter.png"
                                    )
                                    FileOutputStream(outFile).use { fos ->
                                        bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
                                    }
                                    bmp.recycle()
                                    extractedArtifacts.add(
                                        OutputFileArtifact(
                                            name = outFile.name,
                                            path = outFile.absolutePath,
                                            mimeType = "image/png",
                                            sizeBytes = outFile.length(),
                                            pageCount = 1
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                onProgress(0.15f + 0.7f * (i + 1) / max(1, total), "Scanning page ${i + 1} of $total...")
            }
        }

        if (extractedArtifacts.isEmpty()) {
            throw IllegalStateException("No embedded raster images found inside ${file.name}.")
        }

        val zipArchive = if (extractedArtifacts.size > 1) {
            onProgress(0.92f, "Creating ZIP archive...")
            FileHelper.createZipArchive(context, extractedArtifacts, "${baseName}_extracted_images.zip")
        } else null

        onProgress(1f, "Extracted ${extractedArtifacts.size} images")
        WorkspaceSuccessResult(
            primaryFile = extractedArtifacts.first(),
            additionalFiles = extractedArtifacts.drop(1),
            zipArchive = zipArchive,
            originalSizeBytes = file.sizeBytes,
            summaryNote = "Extracted ${extractedArtifacts.size} embedded image(s)."
        )
    }

    suspend fun compareTwoPdfs(
        context: Context,
        fileA: PickedFile,
        fileB: PickedFile,
        onProgress: (Float, String) -> Unit
    ): WorkspaceSuccessResult = withContext(Dispatchers.IO) {
        onProgress(0.15f, "Comparing documents...")
        val pagesA = mutableListOf<String>()
        val pagesB = mutableListOf<String>()

        PDDocument.load(File(fileA.localPath)).use { docA ->
            val stripper = PDFTextStripper()
            for (p in 1..docA.numberOfPages) {
                coroutineContext.ensureActive()
                stripper.startPage = p
                stripper.endPage = p
                pagesA.add(stripper.getText(docA))
            }
        }
        onProgress(0.45f, "Reading second PDF...")
        PDDocument.load(File(fileB.localPath)).use { docB ->
            val stripper = PDFTextStripper()
            for (p in 1..docB.numberOfPages) {
                coroutineContext.ensureActive()
                stripper.startPage = p
                stripper.endPage = p
                pagesB.add(stripper.getText(docB))
            }
        }

        val maxPages = max(pagesA.size, pagesB.size)
        val comparisons = mutableListOf<PageComparison>()
        val report = StringBuilder("Comparison Report\nA: ${fileA.name} (${pagesA.size}p)\nB: ${fileB.name} (${pagesB.size}p)\n\n")

        for (i in 0 until maxPages) {
            coroutineContext.ensureActive()
            val linesA = pagesA.getOrNull(i)?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
            val linesB = pagesB.getOrNull(i)?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
            val diffs = computeSimpleLineDiff(linesA, linesB)
            val changed = diffs.any { it.type != DiffLineType.UNCHANGED }
            comparisons.add(PageComparison(i + 1, diffs, changed))

            if (changed) {
                report.append("--- Page ${i + 1} ---\n")
                diffs.filter { it.type != DiffLineType.UNCHANGED }.forEach { d ->
                    val prefix = if (d.type == DiffLineType.ADDED) "+ " else "- "
                    report.append(prefix).append(d.text).append('\n')
                }
                report.append('\n')
            }
            onProgress(0.5f + 0.42f * (i + 1) / max(1, maxPages), "Diffing page ${i + 1} of $maxPages...")
        }

        val outFile = FileHelper.createProcessedFile(context, "comparison_diff_report.txt")
        outFile.writeText(report.toString(), Charsets.UTF_8)
        onProgress(1f, "Comparison complete")

        val changedPagesCount = comparisons.count { it.hasChanges }
        val artifact = OutputFileArtifact(
            name = outFile.name,
            path = outFile.absolutePath,
            mimeType = "text/plain",
            sizeBytes = outFile.length(),
            pageCount = maxPages
        )
        WorkspaceSuccessResult(
            primaryFile = artifact,
            originalSizeBytes = fileA.sizeBytes + fileB.sizeBytes,
            pageComparisons = comparisons,
            summaryNote = "Compared ${pagesA.size} vs ${pagesB.size} pages. Found changes on $changedPagesCount page(s)."
        )
    }

    suspend fun addBookmarks(
        context: Context,
        file: PickedFile,
        bookmarks: List<BookmarkInput>,
        onProgress: (Float, String) -> Unit
    ): WorkspaceSuccessResult = withContext(Dispatchers.IO) {
        require(bookmarks.isNotEmpty()) { "Add at least one bookmark entry." }
        onProgress(0.2f, "Building PDF outline bookmarks...")
        val baseName = file.name.substringBeforeLast('.')
        val outFile = FileHelper.createProcessedFile(context, "${baseName}_bookmarked.pdf")

        var pageCount = 0
        PDDocument.load(File(file.localPath)).use { doc ->
            pageCount = doc.numberOfPages
            val outline = doc.documentCatalog.documentOutline ?: PDDocumentOutline().also {
                doc.documentCatalog.documentOutline = it
            }
            bookmarks.forEach { bm ->
                val pageIdx = (bm.pageNumber1Based - 1).coerceIn(0, max(0, pageCount - 1))
                val dest = PDPageFitDestination().apply {
                    page = doc.getPage(pageIdx)
                }
                val item = PDOutlineItem().apply {
                    title = bm.title.ifBlank { "Page ${pageIdx + 1}" }
                    destination = dest
                }
                outline.addLast(item)
            }
            outline.openNode()
            onProgress(0.8f, "Saving PDF with bookmarks...")
            doc.save(outFile)
        }

        onProgress(1f, "Added ${bookmarks.size} bookmark(s)")
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
            summaryNote = "Embedded ${bookmarks.size} outline bookmark(s)."
        )
    }

    suspend fun repairPdf(
        context: Context,
        file: PickedFile,
        onProgress: (Float, String) -> Unit
    ): WorkspaceSuccessResult = withContext(Dispatchers.IO) {
        onProgress(0.15f, "Diagnosing PDF structure...")
        val srcFile = File(file.localPath)
        val baseName = file.name.substringBeforeLast('.')
        val outFile = FileHelper.createProcessedFile(context, "${baseName}_repaired.pdf")
        var repairedPages = 0
        var methodUsed = "XRef & Page Tree Rebuild"

        val boxSuccess = runCatching {
            PDDocument.load(srcFile, MemoryUsageSetting.setupTempFileOnly()).use { srcDoc ->
                PDDocument().use { cleanDoc ->
                    for (i in 0 until srcDoc.numberOfPages) {
                        coroutineContext.ensureActive()
                        cleanDoc.importPage(srcDoc.getPage(i))
                        repairedPages++
                        onProgress(0.25f + 0.6f * (i + 1) / max(1, srcDoc.numberOfPages), "Rebuilding page ${i + 1}...")
                    }
                    cleanDoc.save(outFile)
                }
            }
        }

        if (boxSuccess.isFailure || repairedPages == 0) {
            methodUsed = "PdfRenderer Page Rescue"
            onProgress(0.4f, "Attempting visual page rescue...")
            try {
                ParcelFileDescriptor.open(srcFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        val count = renderer.pageCount
                        if (count == 0) throw IllegalStateException("File is too severely corrupted to recover any pages.")
                        PDDocument().use { rescueDoc ->
                            for (i in 0 until count) {
                                coroutineContext.ensureActive()
                                renderer.openPage(i).use { rPage ->
                                    val ptW = rPage.width.toFloat().coerceAtLeast(100f)
                                    val ptH = rPage.height.toFloat().coerceAtLeast(100f)
                                    val w = 1400
                                    val h = max(100, (w * (ptH / ptW)).roundToInt())
                                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                    Canvas(bmp).drawColor(Color.WHITE)
                                    rPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                    val p = PDPage(PDRectangle(ptW, ptH))
                                    rescueDoc.addPage(p)
                                    val xObj = LosslessFactory.createFromImage(rescueDoc, bmp)
                                    bmp.recycle()
                                    PDPageContentStream(rescueDoc, p).use { cs ->
                                        cs.drawImage(xObj, 0f, 0f, ptW, ptH)
                                    }
                                    repairedPages++
                                }
                                onProgress(0.45f + 0.45f * (i + 1) / count, "Rescuing page ${i + 1} of $count...")
                            }
                            rescueDoc.save(outFile)
                        }
                    }
                }
            } catch (t: Throwable) {
                throw IllegalStateException("File is too severely corrupted to recover any pages.", t)
            }
        }

        onProgress(1f, "Repair complete ($repairedPages pages)")
        val artifact = OutputFileArtifact(
            name = outFile.name,
            path = outFile.absolutePath,
            mimeType = "application/pdf",
            sizeBytes = outFile.length(),
            pageCount = repairedPages
        )
        WorkspaceSuccessResult(
            primaryFile = artifact,
            originalSizeBytes = file.sizeBytes,
            summaryNote = "Recovered $repairedPages page(s) using $methodUsed."
        )
    }

    suspend fun inspectMetadata(file: PickedFile): PdfMetadataInfo = withContext(Dispatchers.IO) {
        val srcFile = File(file.localPath)
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        PDDocument.load(srcFile).use { doc ->
            val info = doc.documentInformation
            PdfMetadataInfo(
                title = info.title.orEmpty(),
                author = info.author.orEmpty(),
                subject = info.subject.orEmpty(),
                keywords = info.keywords.orEmpty(),
                creator = info.creator.orEmpty(),
                producer = info.producer.orEmpty(),
                creationDate = info.creationDate?.time?.let { fmt.format(it) }.orEmpty(),
                modificationDate = info.modificationDate?.time?.let { fmt.format(it) }.orEmpty(),
                pageCount = doc.numberOfPages,
                pdfVersion = "PDF ${doc.version}",
                isEncrypted = doc.isEncrypted,
                fileSizeBytes = srcFile.length()
            )
        }
    }

    suspend fun editMetadata(
        context: Context,
        file: PickedFile,
        newInfo: PdfMetadataInfo,
        onProgress: (Float, String) -> Unit
    ): WorkspaceSuccessResult = withContext(Dispatchers.IO) {
        onProgress(0.25f, "Updating document metadata...")
        val baseName = file.name.substringBeforeLast('.')
        val outFile = FileHelper.createProcessedFile(context, "${baseName}_metadata.pdf")
        var pageCount = 0

        PDDocument.load(File(file.localPath)).use { doc ->
            pageCount = doc.numberOfPages
            val info = doc.documentInformation
            info.title = newInfo.title
            info.author = newInfo.author
            info.subject = newInfo.subject
            info.keywords = newInfo.keywords
            info.creator = newInfo.creator
            info.producer = newInfo.producer
            doc.documentInformation = info
            onProgress(0.8f, "Saving updated PDF...")
            doc.save(outFile)
        }

        onProgress(1f, "Metadata updated")
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
            metadataInfo = newInfo.copy(pageCount = pageCount, fileSizeBytes = outFile.length()),
            summaryNote = "Updated Title, Author, Subject, Keywords, Creator, and Producer."
        )
    }

    suspend fun createPdfA(
        context: Context,
        file: PickedFile,
        title: String,
        author: String,
        onProgress: (Float, String) -> Unit
    ): WorkspaceSuccessResult = withContext(Dispatchers.IO) {
        onProgress(0.2f, "Writing PDF/A-1b XMP archival metadata...")
        val baseName = file.name.substringBeforeLast('.')
        val outFile = FileHelper.createProcessedFile(context, "${baseName}_pdfa.pdf")
        val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
        var pageCount = 0

        PDDocument.load(File(file.localPath)).use { doc ->
            pageCount = doc.numberOfPages
            val info = doc.documentInformation
            val docTitle = title.ifBlank { info.title ?: baseName }
            val docAuthor = author.ifBlank { info.author ?: "PDF Master Pro" }
            info.title = docTitle
            info.author = docAuthor
            doc.documentInformation = info

            val xmp = """<?xpacket begin="" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about="" xmlns:pdfaid="http://www.aiim.org/pdfa/ns/id/">
      <pdfaid:part>1</pdfaid:part>
      <pdfaid:conformance>B</pdfaid:conformance>
    </rdf:Description>
    <rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/">
      <dc:title><rdf:Alt><rdf:li xml:lang="x-default">${docTitle.replace("&", "&amp;").replace("<", "&lt;")}</rdf:li></rdf:Alt></dc:title>
      <dc:creator><rdf:Seq><rdf:li>${docAuthor.replace("&", "&amp;").replace("<", "&lt;")}</rdf:li></rdf:Seq></dc:creator>
    </rdf:Description>
    <rdf:Description rdf:about="" xmlns:xmp="http://ns.adobe.com/xap/1.0/">
      <xmp:CreateDate>$isoDate</xmp:CreateDate>
      <xmp:ModifyDate>$isoDate</xmp:ModifyDate>
      <xmp:CreatorTool>PDF Master Pro</xmp:CreatorTool>
    </rdf:Description>
  </rdf:RDF>
</x:xmpmeta>
<?xpacket end="w"?>"""

            val metadata = PDMetadata(doc, ByteArrayInputStream(xmp.toByteArray(Charsets.UTF_8)))
            doc.documentCatalog.metadata = metadata
            doc.version = 1.4f
            onProgress(0.8f, "Saving PDF/A-1b document...")
            doc.save(outFile)
        }

        onProgress(1f, "PDF/A-1b archival metadata embedded")
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
            summaryNote = "Embedded PDF/A-1b XMP metadata & PDF 1.4 archival structure. Note: Strict ISO PDF/A font subsetting & ICC profile validation for external archival compliance may require server-side Ghostscript/veraPDF."
        )
    }

    private fun computeSimpleLineDiff(linesA: List<String>, linesB: List<String>): List<DiffLine> {
        val setA = linesA.toSet()
        val setB = linesB.toSet()
        val result = mutableListOf<DiffLine>()
        var idxA = 0
        var idxB = 0
        while (idxA < linesA.size || idxB < linesB.size) {
            val a = linesA.getOrNull(idxA)
            val b = linesB.getOrNull(idxB)
            when {
                a != null && b != null && a == b -> {
                    result.add(DiffLine(DiffLineType.UNCHANGED, a))
                    idxA++
                    idxB++
                }
                a != null && a !in setB -> {
                    result.add(DiffLine(DiffLineType.REMOVED, a))
                    idxA++
                }
                b != null && b !in setA -> {
                    result.add(DiffLine(DiffLineType.ADDED, b))
                    idxB++
                }
                a != null -> {
                    result.add(DiffLine(DiffLineType.REMOVED, a))
                    idxA++
                }
                b != null -> {
                    result.add(DiffLine(DiffLineType.ADDED, b))
                    idxB++
                }
            }
        }
        return result
    }
}
