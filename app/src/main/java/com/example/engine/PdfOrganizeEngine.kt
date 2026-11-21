package com.example.engine

import android.content.Context
import com.example.model.OutputFileArtifact
import com.example.model.PageItemState
import com.example.model.PickedFile
import com.example.model.SplitMode
import com.example.model.WorkspaceSuccessResult
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.multipdf.Splitter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

object PdfOrganizeEngine {

    suspend fun mergePdfs(
        context: Context,
        files: List<PickedFile>,
        onProgress: (Float, String) -> Unit
    ): WorkspaceSuccessResult = withContext(Dispatchers.IO) {
        require(files.size >= 2) { "Select at least 2 PDF files to merge." }
        onProgress(0.1f, "Validating ${files.size} PDF documents...")

        val outFile = FileHelper.createProcessedFile(context, "merged_document.pdf")
        val merger = PDFMergerUtility()
        merger.destinationFileName = outFile.absolutePath

        files.forEachIndexed { idx, file ->
            coroutineContext.ensureActive()
            val f = File(file.localPath)
            if (!f.exists()) throw IllegalStateException("Missing file: ${file.name}")
            merger.addSource(f)
            onProgress(0.15f + 0.45f * (idx + 1) / files.size, "Queuing ${file.name}...")
        }

        onProgress(0.7f, "Merging PDF page trees...")
        merger.mergeDocuments(com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly())

        val pageCount = PDDocument.load(outFile).use { it.numberOfPages }
        onProgress(1f, "Merge complete ($pageCount pages)")

        val totalOriginal = files.sumOf { it.sizeBytes }
        val artifact = OutputFileArtifact(
            name = outFile.name,
            path = outFile.absolutePath,
            mimeType = "application/pdf",
            sizeBytes = outFile.length(),
            pageCount = pageCount
        )
        WorkspaceSuccessResult(
            primaryFile = artifact,
            originalSizeBytes = totalOriginal,
            summaryNote = "Merged ${files.size} documents into $pageCount pages."
        )
    }

    suspend fun splitPdf(
        context: Context,
        file: PickedFile,
        mode: SplitMode,
        customRanges: String,
        fixedInterval: Int,
        onProgress: (Float, String) -> Unit
    ): WorkspaceSuccessResult = withContext(Dispatchers.IO) {
        onProgress(0.1f, "Loading ${file.name}...")
        val sourceFile = File(file.localPath)
        val baseName = file.name.substringBeforeLast('.')
        val outputs = mutableListOf<OutputFileArtifact>()

        PDDocument.load(sourceFile).use { doc ->
            val totalPages = doc.numberOfPages
            if (totalPages == 0) throw IllegalStateException("PDF contains 0 pages.")

            when (mode) {
                SplitMode.ALL_PAGES -> {
                    val splitter = Splitter()
                    splitter.setSplitAtPage(1)
                    val splitDocs = splitter.split(doc)
                    splitDocs.forEachIndexed { idx, partDoc ->
                        coroutineContext.ensureActive()
                        partDoc.use {
                            val outFile = FileHelper.createProcessedFile(context, "${baseName}_page_${idx + 1}.pdf")
                            it.save(outFile)
                            outputs.add(
                                OutputFileArtifact(
                                    name = outFile.name,
                                    path = outFile.absolutePath,
                                    mimeType = "application/pdf",
                                    sizeBytes = outFile.length(),
                                    pageCount = it.numberOfPages
                                )
                            )
                        }
                        onProgress(0.2f + 0.65f * (idx + 1) / splitDocs.size, "Splitting page ${idx + 1} of $totalPages...")
                    }
                }

                SplitMode.FIXED_INTERVAL -> {
                    val step = fixedInterval.coerceIn(1, totalPages)
                    val splitter = Splitter()
                    splitter.setSplitAtPage(step)
                    val splitDocs = splitter.split(doc)
                    splitDocs.forEachIndexed { idx, partDoc ->
                        coroutineContext.ensureActive()
                        partDoc.use {
                            val outFile = FileHelper.createProcessedFile(context, "${baseName}_part_${idx + 1}.pdf")
                            it.save(outFile)
                            outputs.add(
                                OutputFileArtifact(
                                    name = outFile.name,
                                    path = outFile.absolutePath,
                                    mimeType = "application/pdf",
                                    sizeBytes = outFile.length(),
                                    pageCount = it.numberOfPages
                                )
                            )
                        }
                        onProgress(0.2f + 0.65f * (idx + 1) / splitDocs.size, "Creating part ${idx + 1}...")
                    }
                }

                SplitMode.CUSTOM_RANGES -> {
                    val groups = FileHelper.parseSplitGroups(customRanges, totalPages)
                    if (groups.isEmpty()) throw IllegalArgumentException("Enter valid page ranges such as 1-3, 4-6.")
                    groups.forEachIndexed { gIdx, pageIndices ->
                        coroutineContext.ensureActive()
                        PDDocument().use { partDoc ->
                            pageIndices.forEach { pIdx ->
                                partDoc.importPage(doc.getPage(pIdx))
                            }
                            val outFile = FileHelper.createProcessedFile(context, "${baseName}_range_${gIdx + 1}.pdf")
                            partDoc.save(outFile)
                            outputs.add(
                                OutputFileArtifact(
                                    name = outFile.name,
                                    path = outFile.absolutePath,
                                    mimeType = "application/pdf",
                                    sizeBytes = outFile.length(),
                                    pageCount = partDoc.numberOfPages
                                )
                            )
                        }
                        onProgress(0.2f + 0.65f * (gIdx + 1) / groups.size, "Exporting range ${gIdx + 1}...")
                    }
                }
            }
        }

        if (outputs.isEmpty()) throw IllegalStateException("No split documents generated.")

        onProgress(0.9f, "Packaging ZIP archive...")
        val zipArtifact = if (outputs.size > 1) {
            FileHelper.createZipArchive(context, outputs, "${baseName}_split.zip")
        } else null

        onProgress(1f, "Split complete (${outputs.size} files)")
        WorkspaceSuccessResult(
            primaryFile = outputs.first(),
            additionalFiles = outputs.drop(1),
            zipArchive = zipArtifact,
            originalSizeBytes = file.sizeBytes,
            summaryNote = "Created ${outputs.size} PDF file(s) from ${file.name}."
        )
    }

    suspend fun applyPageOrganization(
        context: Context,
        file: PickedFile,
        pageItems: List<PageItemState>,
        outputSuffix: String,
        onProgress: (Float, String) -> Unit
    ): WorkspaceSuccessResult = withContext(Dispatchers.IO) {
        require(pageItems.isNotEmpty()) { "At least one page must remain in the output PDF." }
        onProgress(0.15f, "Loading ${file.name}...")

        val baseName = file.name.substringBeforeLast('.')
        val outFile = FileHelper.createProcessedFile(context, "${baseName}_$outputSuffix.pdf")

        var finalCount = 0
        PDDocument.load(File(file.localPath)).use { srcDoc ->
            PDDocument().use { outDoc ->
                pageItems.forEachIndexed { idx, item ->
                    coroutineContext.ensureActive()
                    if (item.originalPageIndex in 0 until srcDoc.numberOfPages) {
                        val srcPage = srcDoc.getPage(item.originalPageIndex)
                        val imported = outDoc.importPage(srcPage)
                        val newRotation = (imported.rotation + item.rotationDegrees) % 360
                        imported.rotation = if (newRotation < 0) newRotation + 360 else newRotation
                    }
                    onProgress(0.2f + 0.65f * (idx + 1) / pageItems.size, "Applying page ${idx + 1} of ${pageItems.size}...")
                }
                finalCount = outDoc.numberOfPages
                if (finalCount == 0) throw IllegalStateException("No valid pages selected.")
                onProgress(0.9f, "Writing organized PDF...")
                outDoc.save(outFile)
            }
        }

        onProgress(1f, "Completed ($finalCount pages)")
        val artifact = OutputFileArtifact(
            name = outFile.name,
            path = outFile.absolutePath,
            mimeType = "application/pdf",
            sizeBytes = outFile.length(),
            pageCount = finalCount
        )
        WorkspaceSuccessResult(
            primaryFile = artifact,
            originalSizeBytes = file.sizeBytes,
            summaryNote = "Saved organized PDF with $finalCount pages."
        )
    }
}
