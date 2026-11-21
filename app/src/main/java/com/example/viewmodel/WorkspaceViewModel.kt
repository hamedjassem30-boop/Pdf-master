package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PdfMasterApp
import com.example.engine.FileHelper
import com.example.engine.HeaderFooterOptions
import com.example.engine.ImageExportFormat
import com.example.engine.PageNumbersOptions
import com.example.engine.PdfAdvancedEngine
import com.example.engine.PdfConvertFromEngine
import com.example.engine.PdfConvertToEngine
import com.example.engine.PdfEditEngine
import com.example.engine.PdfOptimizeEngine
import com.example.engine.PdfOrganizeEngine
import com.example.engine.PdfSecurityEngine
import com.example.engine.WatermarkOptions
import com.example.model.BookmarkInput
import com.example.model.CompressionLevel
import com.example.model.EditOverlay
import com.example.model.PageItemState
import com.example.model.PageNumberPosition
import com.example.model.PdfMetadataInfo
import com.example.model.PdfToolId
import com.example.model.PickedFile
import com.example.model.ProcessingProgress
import com.example.model.RedactionRect
import com.example.model.SplitMode
import com.example.model.WorkspaceSuccessResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

sealed class WorkspacePhase {
    data object Idle : WorkspacePhase()
    data class Processing(val progress: ProcessingProgress) : WorkspacePhase()
    data class Success(val result: WorkspaceSuccessResult) : WorkspacePhase()
    data class Error(val message: String) : WorkspacePhase()
}

data class WorkspaceUiState(
    val toolId: PdfToolId = PdfToolId.MERGE_PDF,
    val files: List<PickedFile> = emptyList(),
    val pageItems: List<PageItemState> = emptyList(),
    val phase: WorkspacePhase = WorkspacePhase.Idle,
    val uploadWarning: String? = null,

    // Organize & Split
    val splitMode: SplitMode = SplitMode.CUSTOM_RANGES,
    val customPageRangeText: String = "1",
    val splitInterval: Int = 1,
    val defaultRotateAngle: Int = 90,

    // Optimize
    val compressionLevel: CompressionLevel = CompressionLevel.MEDIUM,
    val rasterizeForMaxSavings: Boolean = false,

    // Convert
    val fitImagesToA4: Boolean = true,
    val txtFontSize: Float = 12f,
    val imageExportWidth: Int = 1400,

    // Edit Studio + Page Numbers + Watermark + Header/Footer
    val activeEditorPage: Int = 0,
    val overlays: List<EditOverlay> = emptyList(),
    val savedSignaturePath: String? = null,
    val pageNumberOptions: PageNumbersOptions = PageNumbersOptions(),
    val watermarkOptions: WatermarkOptions = WatermarkOptions(),
    val headerFooterOptions: HeaderFooterOptions = HeaderFooterOptions(),

    // Security
    val userPassword: String = "",
    val ownerPassword: String = "",
    val allowPrint: Boolean = true,
    val allowCopy: Boolean = true,
    val allowModify: Boolean = false,
    val signerName: String = "",
    val signerReason: String = "Document Approval",
    val signerLocation: String = "",
    val activeRedactPage: Int = 0,
    val redactions: List<RedactionRect> = emptyList(),

    // Advanced
    val searchQuery: String = "",
    val bookmarks: List<BookmarkInput> = listOf(BookmarkInput(UUID.randomUUID().toString(), "Chapter 1", 1)),
    val metadataInfo: PdfMetadataInfo = PdfMetadataInfo(),
    val pdfaTitle: String = "",
    val pdfaAuthor: String = ""
)

class WorkspaceViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PdfMasterApp
    private val repo = app.pdfMasterRepository
    private val prefs = app.preferencesRepository

    private val _state = MutableStateFlow(WorkspaceUiState())
    val state: StateFlow<WorkspaceUiState> = _state.asStateFlow()

    private var activeJob: Job? = null

    fun initForTool(toolId: PdfToolId, preloadedFiles: List<PickedFile> = emptyList()) {
        if (_state.value.toolId == toolId && _state.value.files.isNotEmpty() && preloadedFiles.isEmpty()) {
            return
        }
        activeJob?.cancel()
        viewModelScope.launch {
            val sigPath = prefs.savedSignaturePathFlow.firstOrNull()
            _state.value = WorkspaceUiState(
                toolId = toolId,
                savedSignaturePath = sigPath?.takeIf { File(it).exists() },
                pageNumberOptions = PageNumbersOptions(enabled = toolId == PdfToolId.PAGE_NUMBERS),
                watermarkOptions = WatermarkOptions(enabled = toolId == PdfToolId.WATERMARK),
                headerFooterOptions = HeaderFooterOptions(enabled = toolId == PdfToolId.HEADER_FOOTER)
            )
            if (preloadedFiles.isNotEmpty()) {
                applyImportedFiles(preloadedFiles)
            }
        }
    }

    fun addFilesFromUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val tool = _state.value.toolId
        val requirePdf = tool.acceptedMimeTypes.size == 1 && tool.acceptedMimeTypes.first() == "application/pdf"

        viewModelScope.launch {
            val currentFiles = if (tool.allowMultipleFiles) _state.value.files else emptyList()
            val existingKeys = currentFiles.map { "${it.name}_${it.sizeBytes}" }.toMutableSet()
            val added = mutableListOf<PickedFile>()
            var warningMsg: String? = null

            for (uri in uris) {
                FileHelper.importFromUri(app, uri, requirePdf = requirePdf).fold(
                    onSuccess = { picked ->
                        val key = "${picked.name}_${picked.sizeBytes}"
                        if (!existingKeys.add(key)) {
                            warningMsg = "Skipped duplicate file: ${picked.name}"
                        } else {
                            added.add(picked)
                        }
                    },
                    onFailure = { e ->
                        warningMsg = e.message ?: "Unable to read selected file."
                    }
                )
                if (!tool.allowMultipleFiles && added.isNotEmpty()) break
            }

            if (added.isNotEmpty()) {
                val combined = if (tool.allowMultipleFiles) currentFiles + added else listOf(added.first())
                applyImportedFiles(combined, warningMsg)
            } else if (warningMsg != null) {
                _state.update { it.copy(uploadWarning = warningMsg) }
            }
        }
    }

    private suspend fun applyImportedFiles(files: List<PickedFile>, warning: String? = null) {
        val primary = files.firstOrNull()
        val pageItems = if (primary != null && primary.pageCount > 0) {
            (0 until primary.pageCount).map { idx ->
                PageItemState(
                    id = "${primary.id}_p$idx",
                    originalPageIndex = idx,
                    isSelected = _state.value.toolId != PdfToolId.DELETE_PAGES // For Delete Pages, click pages to mark for deletion
                )
            }
        } else emptyList()

        val loadedMeta = if (primary != null &&
            (_state.value.toolId == PdfToolId.METADATA_INSPECT ||
                _state.value.toolId == PdfToolId.EDIT_METADATA ||
                _state.value.toolId == PdfToolId.CREATE_PDFA)
        ) {
            runCatching { PdfAdvancedEngine.inspectMetadata(primary) }.getOrDefault(PdfMetadataInfo())
        } else _state.value.metadataInfo

        val defaultRange = if (primary != null && primary.pageCount > 1) {
            "1-${minOf(2, primary.pageCount)}"
        } else "1"

        _state.update {
            it.copy(
                files = files,
                pageItems = pageItems,
                phase = WorkspacePhase.Idle,
                uploadWarning = warning,
                customPageRangeText = defaultRange,
                activeEditorPage = 0,
                activeRedactPage = 0,
                overlays = emptyList(),
                redactions = emptyList(),
                metadataInfo = loadedMeta,
                pdfaTitle = loadedMeta.title.ifBlank { primary?.name?.substringBeforeLast('.') ?: "" },
                pdfaAuthor = loadedMeta.author
            )
        }
    }

    fun removeFile(fileId: String) {
        val remaining = _state.value.files.filterNot { it.id == fileId }
        viewModelScope.launch { applyImportedFiles(remaining) }
    }

    fun moveFile(fromIndex: Int, toIndex: Int) {
        val list = _state.value.files.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _state.update { it.copy(files = list) }
    }

    fun clearWarning() {
        _state.update { it.copy(uploadWarning = null) }
    }

    // Page organizer actions
    fun movePage(fromIdx: Int, toIdx: Int) {
        val items = _state.value.pageItems.toMutableList()
        if (fromIdx !in items.indices || toIdx !in items.indices) return
        val moved = items.removeAt(fromIdx)
        items.add(toIdx, moved)
        _state.update { it.copy(pageItems = items) }
    }

    fun rotatePage(pageItemId: String, deltaDegrees: Int = 90) {
        _state.update { s ->
            s.copy(
                pageItems = s.pageItems.map { item ->
                    if (item.id == pageItemId) {
                        item.copy(rotationDegrees = (item.rotationDegrees + deltaDegrees) % 360)
                    } else item
                }
            )
        }
    }

    fun rotateAllPages(deltaDegrees: Int) {
        _state.update { s ->
            s.copy(
                defaultRotateAngle = deltaDegrees,
                pageItems = s.pageItems.map { item ->
                    item.copy(rotationDegrees = (item.rotationDegrees + deltaDegrees) % 360)
                }
            )
        }
    }

    fun duplicatePage(pageItemId: String) {
        val list = _state.value.pageItems.toMutableList()
        val idx = list.indexOfFirst { it.id == pageItemId }
        if (idx >= 0) {
            val original = list[idx]
            list.add(
                idx + 1,
                original.copy(
                    id = "${original.id}_dup_${UUID.randomUUID().toString().take(4)}",
                    copyNumber = original.copyNumber + 1
                )
            )
            _state.update { it.copy(pageItems = list) }
        }
    }

    fun deletePageItem(pageItemId: String) {
        if (_state.value.pageItems.size <= 1) return
        _state.update { s -> s.copy(pageItems = s.pageItems.filterNot { it.id == pageItemId }) }
    }

    fun togglePageSelection(pageItemId: String) {
        _state.update { s ->
            s.copy(
                pageItems = s.pageItems.map { item ->
                    if (item.id == pageItemId) item.copy(isSelected = !item.isSelected) else item
                }
            )
        }
    }

    // Option setters
    fun setSplitMode(mode: SplitMode) = _state.update { it.copy(splitMode = mode) }
    fun setCustomRangeText(text: String) = _state.update { it.copy(customPageRangeText = text) }
    fun setSplitInterval(interval: Int) = _state.update { it.copy(splitInterval = interval.coerceAtLeast(1)) }
    fun setCompressionLevel(level: CompressionLevel) = _state.update { it.copy(compressionLevel = level) }
    fun setRasterizeCompress(enabled: Boolean) = _state.update { it.copy(rasterizeForMaxSavings = enabled) }
    fun setFitImagesToA4(fit: Boolean) = _state.update { it.copy(fitImagesToA4 = fit) }
    fun setTxtFontSize(size: Float) = _state.update { it.copy(txtFontSize = size.coerceIn(8f, 28f)) }
    fun setImageExportWidth(width: Int) = _state.update { it.copy(imageExportWidth = width) }

    // Edit Studio
    fun setActiveEditorPage(pageIdx: Int) = _state.update { it.copy(activeEditorPage = pageIdx) }
    fun addOverlay(overlay: EditOverlay) = _state.update { it.copy(overlays = it.overlays + overlay) }
    fun undoOverlay() = _state.update { s ->
        val cur = s.activeEditorPage
        val lastIdx = s.overlays.indexOfLast { it.pageIndex == cur }
        if (lastIdx >= 0) {
            val mut = s.overlays.toMutableList()
            mut.removeAt(lastIdx)
            s.copy(overlays = mut)
        } else s
    }

    fun clearPageOverlays() = _state.update { s ->
        s.copy(overlays = s.overlays.filterNot { it.pageIndex == s.activeEditorPage })
    }

    fun saveSignatureBitmap(bitmap: Bitmap, placeOnCurrentPage: Boolean = false) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                val sigDir = File(app.filesDir, "signatures").apply { mkdirs() }
                val sigFile = File(sigDir, "user_signature.png")
                FileOutputStream(sigFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                }
                sigFile.absolutePath
            }
            prefs.setSavedSignaturePath(path)
            _state.update { it.copy(savedSignaturePath = path) }
            if (placeOnCurrentPage) {
                addOverlay(
                    EditOverlay.ImageOverlay(
                        id = UUID.randomUUID().toString(),
                        pageIndex = _state.value.activeEditorPage,
                        imagePath = path,
                        normX = 0.56f,
                        normY = 0.72f,
                        normWidth = 0.34f,
                        normHeight = 0.14f
                    )
                )
            }
        }
    }

    fun updatePageNumberOptions(transform: (PageNumbersOptions) -> PageNumbersOptions) =
        _state.update { it.copy(pageNumberOptions = transform(it.pageNumberOptions)) }

    fun updateWatermarkOptions(transform: (WatermarkOptions) -> WatermarkOptions) =
        _state.update { it.copy(watermarkOptions = transform(it.watermarkOptions)) }

    fun updateHeaderFooterOptions(transform: (HeaderFooterOptions) -> HeaderFooterOptions) =
        _state.update { it.copy(headerFooterOptions = transform(it.headerFooterOptions)) }

    // Security setters
    fun setUserPassword(pw: String) = _state.update { it.copy(userPassword = pw) }
    fun setOwnerPassword(pw: String) = _state.update { it.copy(ownerPassword = pw) }
    fun setPermissions(print: Boolean, copy: Boolean, modify: Boolean) =
        _state.update { it.copy(allowPrint = print, allowCopy = copy, allowModify = modify) }

    fun setSignerDetails(name: String, reason: String, location: String) =
        _state.update { it.copy(signerName = name, signerReason = reason, signerLocation = location) }

    fun setActiveRedactPage(pageIdx: Int) = _state.update { it.copy(activeRedactPage = pageIdx) }
    fun addRedactionRect(rect: RedactionRect) = _state.update { it.copy(redactions = it.redactions + rect) }
    fun undoRedaction() = _state.update { s ->
        val cur = s.activeRedactPage
        val idx = s.redactions.indexOfLast { it.pageIndex == cur }
        if (idx >= 0) {
            val mut = s.redactions.toMutableList()
            mut.removeAt(idx)
            s.copy(redactions = mut)
        } else s
    }

    // Advanced setters
    fun setSearchQuery(q: String) = _state.update { it.copy(searchQuery = q) }
    fun addBookmarkRow() = _state.update {
        val nextPage = (it.bookmarks.lastOrNull()?.pageNumber1Based ?: 0) + 1
        it.copy(
            bookmarks = it.bookmarks + BookmarkInput(
                UUID.randomUUID().toString(),
                "Section ${it.bookmarks.size + 1}",
                nextPage.coerceAtMost(maxOf(1, it.files.firstOrNull()?.pageCount ?: 1))
            )
        )
    }

    fun updateBookmark(id: String, title: String, page1: Int) = _state.update { s ->
        s.copy(
            bookmarks = s.bookmarks.map { b ->
                if (b.id == id) b.copy(title = title, pageNumber1Based = page1.coerceAtLeast(1)) else b
            }
        )
    }

    fun removeBookmark(id: String) = _state.update { s ->
        if (s.bookmarks.size <= 1) s else s.copy(bookmarks = s.bookmarks.filterNot { it.id == id })
    }

    fun updateMetadataInfo(transform: (PdfMetadataInfo) -> PdfMetadataInfo) =
        _state.update { it.copy(metadataInfo = transform(it.metadataInfo)) }

    fun setPdfAFields(title: String, author: String) =
        _state.update { it.copy(pdfaTitle = title, pdfaAuthor = author) }

    fun resetForAnotherFile() {
        activeJob?.cancel()
        val tool = _state.value.toolId
        initForTool(tool)
    }

    fun cancelProcessing() {
        activeJob?.cancel()
        _state.update { it.copy(phase = WorkspacePhase.Idle) }
    }

    fun process() {
        val snapshot = _state.value
        if (snapshot.files.isEmpty()) {
            _state.update { it.copy(phase = WorkspacePhase.Error("Please select at least one file first.")) }
            return
        }
        if (snapshot.phase is WorkspacePhase.Processing) return

        activeJob = viewModelScope.launch {
            _state.update {
                it.copy(phase = WorkspacePhase.Processing(ProcessingProgress(0.05f, "Preparing workspace...")))
            }
            val progressCb: (Float, String) -> Unit = { pct, msg ->
                _state.update {
                    it.copy(phase = WorkspacePhase.Processing(ProcessingProgress(pct.coerceIn(0f, 1f), msg)))
                }
            }

            try {
                val primary = snapshot.files.first()
                val result: WorkspaceSuccessResult = when (snapshot.toolId) {
                    // A. ORGANIZE
                    PdfToolId.MERGE_PDF -> PdfOrganizeEngine.mergePdfs(app, snapshot.files, progressCb)

                    PdfToolId.SPLIT_PDF -> PdfOrganizeEngine.splitPdf(
                        app, primary, snapshot.splitMode, snapshot.customPageRangeText, snapshot.splitInterval, progressCb
                    )

                    PdfToolId.EXTRACT_PAGES -> {
                        val chosen = snapshot.pageItems.filter { it.isSelected }.ifEmpty {
                            val indices = FileHelper.parsePageSelection(snapshot.customPageRangeText, primary.pageCount)
                            indices.map { idx -> PageItemState("ext_$idx", idx) }
                        }
                        PdfOrganizeEngine.applyPageOrganization(app, primary, chosen, "extracted", progressCb)
                    }

                    PdfToolId.DELETE_PAGES -> {
                        // In Delete mode, toggled/selected pages are the ones marked for DELETION
                        val remaining = snapshot.pageItems.filterNot { it.isSelected }
                        PdfOrganizeEngine.applyPageOrganization(app, primary, remaining, "trimmed", progressCb)
                    }

                    PdfToolId.REORDER_PAGES,
                    PdfToolId.ROTATE_PAGES,
                    PdfToolId.DUPLICATE_PAGES,
                    PdfToolId.VISUAL_ORGANIZER -> PdfOrganizeEngine.applyPageOrganization(
                        app, primary, snapshot.pageItems, snapshot.toolId.name.lowercase(), progressCb
                    )

                    // B. OPTIMIZE
                    PdfToolId.COMPRESS_PDF -> PdfOptimizeEngine.compressPdf(
                        app, primary, snapshot.compressionLevel, snapshot.rasterizeForMaxSavings, progressCb
                    )

                    // C. CONVERT TO PDF
                    PdfToolId.JPG_TO_PDF,
                    PdfToolId.PNG_TO_PDF,
                    PdfToolId.WEBP_TO_PDF -> PdfConvertToEngine.imagesToPdf(
                        app, snapshot.files, snapshot.fitImagesToA4, if (snapshot.fitImagesToA4) 24f else 0f, progressCb
                    )

                    PdfToolId.HTML_TO_PDF -> PdfConvertToEngine.htmlToPdf(app, primary, progressCb)
                    PdfToolId.TXT_TO_PDF -> PdfConvertToEngine.textToPdf(app, primary, snapshot.txtFontSize, progressCb)
                    PdfToolId.WORD_TO_PDF -> PdfConvertToEngine.wordToPdf(app, primary, progressCb)
                    PdfToolId.EXCEL_TO_PDF -> PdfConvertToEngine.excelToPdf(app, primary, progressCb)
                    PdfToolId.PPT_TO_PDF -> PdfConvertToEngine.pptToPdf(app, primary, progressCb)

                    // D. CONVERT FROM PDF
                    PdfToolId.PDF_TO_JPG -> PdfConvertFromEngine.pdfToImages(
                        app, primary, ImageExportFormat.JPG, snapshot.imageExportWidth, progressCb
                    )
                    PdfToolId.PDF_TO_PNG -> PdfConvertFromEngine.pdfToImages(
                        app, primary, ImageExportFormat.PNG, snapshot.imageExportWidth, progressCb
                    )
                    PdfToolId.PDF_TO_WEBP -> PdfConvertFromEngine.pdfToImages(
                        app, primary, ImageExportFormat.WEBP, snapshot.imageExportWidth, progressCb
                    )
                    PdfToolId.PDF_TO_TEXT,
                    PdfToolId.EXTRACT_TEXT -> PdfConvertFromEngine.pdfToText(app, primary, progressCb)
                    PdfToolId.PDF_TO_WORD -> PdfConvertFromEngine.pdfToWordDocx(app, primary, progressCb)
                    PdfToolId.PDF_TO_EXCEL -> PdfConvertFromEngine.pdfToExcelXlsx(app, primary, progressCb)
                    PdfToolId.PDF_TO_PPT -> PdfConvertFromEngine.pdfToPptx(app, primary, progressCb)

                    // E. EDIT PDF
                    PdfToolId.EDIT_PDF,
                    PdfToolId.PAGE_NUMBERS,
                    PdfToolId.WATERMARK,
                    PdfToolId.HEADER_FOOTER -> PdfEditEngine.applyEdits(
                        context = app,
                        file = primary,
                        overlays = snapshot.overlays,
                        pageNumbers = snapshot.pageNumberOptions,
                        watermark = snapshot.watermarkOptions,
                        headerFooter = snapshot.headerFooterOptions,
                        outputSuffix = snapshot.toolId.name.lowercase(),
                        onProgress = progressCb
                    )

                    // F. SECURITY
                    PdfToolId.PROTECT_PDF,
                    PdfToolId.ENCRYPT_PDF -> PdfSecurityEngine.protectPdf(
                        app, primary, snapshot.userPassword, snapshot.ownerPassword,
                        snapshot.allowPrint, snapshot.allowCopy, snapshot.allowModify, progressCb
                    )

                    PdfToolId.UNLOCK_PDF,
                    PdfToolId.REMOVE_PASSWORD -> PdfSecurityEngine.unlockPdf(
                        app, primary, snapshot.userPassword, progressCb
                    )

                    PdfToolId.DIGITAL_SIGNATURE -> PdfSecurityEngine.applyDigitalSignatureSeal(
                        app, primary, snapshot.signerName, snapshot.signerReason,
                        snapshot.signerLocation, snapshot.savedSignaturePath, progressCb
                    )

                    PdfToolId.REDACT_PDF -> PdfSecurityEngine.trueRedactPdf(
                        app, primary, snapshot.redactions, progressCb
                    )

                    // G. ADVANCED
                    PdfToolId.OCR_PDF -> PdfAdvancedEngine.ocrPdf(app, primary, progressCb)
                    PdfToolId.SEARCH_PDF -> PdfAdvancedEngine.searchInsidePdf(app, primary, snapshot.searchQuery, progressCb)
                    PdfToolId.EXTRACT_IMAGES -> PdfAdvancedEngine.extractImages(app, primary, progressCb)
                    PdfToolId.COMPARE_PDF -> {
                        require(snapshot.files.size >= 2) { "Select 2 PDF files to compare." }
                        PdfAdvancedEngine.compareTwoPdfs(app, snapshot.files[0], snapshot.files[1], progressCb)
                    }
                    PdfToolId.ADD_BOOKMARKS -> PdfAdvancedEngine.addBookmarks(app, primary, snapshot.bookmarks, progressCb)
                    PdfToolId.REPAIR_PDF -> PdfAdvancedEngine.repairPdf(app, primary, progressCb)
                    PdfToolId.METADATA_INSPECT -> {
                        progressCb(0.5f, "Inspecting document metadata...")
                        val info = PdfAdvancedEngine.inspectMetadata(primary)
                        progressCb(1f, "Inspection complete")
                        val txtFile = FileHelper.createProcessedFile(app, "${primary.name.substringBeforeLast('.')}_metadata.txt")
                        val txtContent = buildString {
                            appendLine("File: ${primary.name}")
                            appendLine("Pages: ${info.pageCount}")
                            appendLine("Version: ${info.pdfVersion}")
                            appendLine("Encrypted: ${info.isEncrypted}")
                            appendLine("Title: ${info.title}")
                            appendLine("Author: ${info.author}")
                            appendLine("Subject: ${info.subject}")
                            appendLine("Keywords: ${info.keywords}")
                            appendLine("Creator: ${info.creator}")
                            appendLine("Producer: ${info.producer}")
                            appendLine("Created: ${info.creationDate}")
                            appendLine("Modified: ${info.modificationDate}")
                        }
                        txtFile.writeText(txtContent, Charsets.UTF_8)
                        WorkspaceSuccessResult(
                            primaryFile = com.example.model.OutputFileArtifact(
                                txtFile.name, txtFile.absolutePath, "text/plain", txtFile.length(), info.pageCount
                            ),
                            originalSizeBytes = primary.sizeBytes,
                            metadataInfo = info,
                            extractedText = txtContent,
                            summaryNote = "Inspected metadata for ${primary.name}."
                        )
                    }
                    PdfToolId.EDIT_METADATA -> PdfAdvancedEngine.editMetadata(app, primary, snapshot.metadataInfo, progressCb)
                    PdfToolId.CREATE_PDFA -> PdfAdvancedEngine.createPdfA(
                        app, primary, snapshot.pdfaTitle, snapshot.pdfaAuthor, progressCb
                    )
                }

                // Persist to Room for real Dashboard & Recent Files history
                repo.recordToolProcessed(
                    toolId = snapshot.toolId,
                    output = result.zipArchive ?: result.primaryFile,
                    originalSizeBytes = result.originalSizeBytes
                )

                _state.update { it.copy(phase = WorkspacePhase.Success(result)) }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _state.update {
                    it.copy(phase = WorkspacePhase.Error(e.message ?: "Unexpected error while processing file."))
                }
            }
        }
    }
}
