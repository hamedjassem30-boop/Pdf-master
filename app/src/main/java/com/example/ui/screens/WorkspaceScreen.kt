package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.FileHelper
import com.example.i18n.AppStrings
import com.example.model.AppLanguage
import com.example.model.CompressionLevel
import com.example.model.PageNumberPosition
import com.example.model.PdfToolId
import com.example.model.PickedFile
import com.example.model.SplitMode
import com.example.ui.components.ErrorResultCard
import com.example.ui.components.PdfDropZoneCard
import com.example.ui.components.PdfEditorStudio
import com.example.ui.components.ProcessingStatusCard
import com.example.ui.components.RedactionCanvas
import com.example.ui.components.SelectedFileRow
import com.example.ui.components.SuccessResultCard
import com.example.ui.components.VisualPageOrganizerGrid
import com.example.ui.theme.Crimson600
import com.example.viewmodel.WorkspacePhase
import com.example.viewmodel.WorkspaceUiState
import com.example.viewmodel.WorkspaceViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    toolId: PdfToolId,
    preloadedFiles: List<PickedFile>,
    lang: AppLanguage,
    isFavorite: Boolean,
    viewModel: WorkspaceViewModel,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    onPreviewFile: (path: String, title: String, mime: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(toolId) {
        viewModel.initForTool(toolId, preloadedFiles)
    }

    // Export via SAF CreateDocument
    var pendingSaveFile by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destUri ->
        val pending = pendingSaveFile
        if (destUri != null && pending != null) {
            scope.launch {
                FileHelper.saveArtifactToUri(context, pending.first, destUri).fold(
                    onSuccess = { snackbarHostState.showSnackbar(AppStrings.savedSuccessMsg(lang)) },
                    onFailure = { e -> snackbarHostState.showSnackbar(e.message ?: "Failed to save file") }
                )
            }
        }
        pendingSaveFile = null
    }

    val isImageInputTool = toolId == PdfToolId.JPG_TO_PDF ||
        toolId == PdfToolId.PNG_TO_PDF ||
        toolId == PdfToolId.WEBP_TO_PDF

    // Zero-permission Photo Picker for image-to-PDF tools
    val pickMultiPhotos = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 40)
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addFilesFromUris(uris)
    }

    val pickMultipleDocs = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addFilesFromUris(uris)
    }

    val pickSingleDoc = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.addFilesFromUris(listOf(uri))
    }

    val launchPicker: () -> Unit = {
        when {
            isImageInputTool -> pickMultiPhotos.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
            toolId.allowMultipleFiles -> pickMultipleDocs.launch(toolId.acceptedMimeTypes.toTypedArray())
            else -> pickSingleDoc.launch(
                if (toolId == PdfToolId.WORD_TO_PDF || toolId == PdfToolId.EXCEL_TO_PDF || toolId == PdfToolId.PPT_TO_PDF || toolId == PdfToolId.HTML_TO_PDF) {
                    arrayOf("*/*")
                } else {
                    toolId.acceptedMimeTypes.toTypedArray()
                }
            )
        }
    }

    val toolText = AppStrings.toolText(toolId, lang)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(toolText.title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            AppStrings.categoryName(toolId.category, lang),
                            style = MaterialTheme.typography.labelSmall,
                            color = toolId.category.color
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("workspace_fav_button")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Favorite tool",
                            tint = if (isFavorite) Crimson600 else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val thumbCols = when {
                maxWidth >= 900.dp -> 4
                maxWidth >= 600.dp -> 3
                else -> 2
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Tool description header
                item {
                    Surface(
                        modifier = Modifier
                            .widthIn(max = 960.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = toolId.category.color.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, toolId.category.color.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(toolId.icon, contentDescription = null, tint = toolId.category.color, modifier = Modifier.size(28.dp))
                            Text(toolText.description, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                // Upload Area
                item {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 960.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (uiState.files.isEmpty() || toolId.allowMultipleFiles) {
                            PdfDropZoneCard(
                                title = AppStrings.uploadDropTitle(lang),
                                subtitle = if (toolId.allowMultipleFiles) "Select multiple files to combine or compare" else toolText.title,
                                buttonLabel = if (uiState.files.isEmpty()) AppStrings.selectFilesBtn(lang) else AppStrings.addMoreFilesBtn(lang),
                                onPickClick = launchPicker,
                                onFilesDropped = { viewModel.addFilesFromUris(it) },
                                testTag = "workspace_dropzone"
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(AppStrings.uploadedFilesHeader(lang), style = MaterialTheme.typography.titleMedium)
                                OutlinedButton(onClick = launchPicker) {
                                    Text(AppStrings.selectFilesBtn(lang))
                                }
                            }
                        }

                        if (!uiState.uploadWarning.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = uiState.uploadWarning!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        uiState.files.forEachIndexed { idx, file ->
                            SelectedFileRow(
                                file = file,
                                index = idx,
                                totalCount = uiState.files.size,
                                lang = lang,
                                canReorder = toolId == PdfToolId.MERGE_PDF || toolId == PdfToolId.JPG_TO_PDF || toolId == PdfToolId.PNG_TO_PDF || toolId == PdfToolId.WEBP_TO_PDF,
                                onMoveUp = { viewModel.moveFile(idx, idx - 1) },
                                onMoveDown = { viewModel.moveFile(idx, idx + 1) },
                                onRemove = { viewModel.removeFile(file.id) }
                            )
                        }
                    }
                }

                // Tool Options + Interactive Previews (visible when file(s) loaded)
                if (uiState.files.isNotEmpty() && uiState.phase !is WorkspacePhase.Success) {
                    val primaryFile = uiState.files.first()

                    item {
                        ToolOptionsPanel(
                            toolId = toolId,
                            uiState = uiState,
                            viewModel = viewModel,
                            modifier = Modifier
                                .widthIn(max = 960.dp)
                                .fillMaxWidth()
                        )
                    }

                    // Page Thumbnails & Organizer for page-based tools
                    if (toolId.isPageBased && primaryFile.pageCount > 0 &&
                        toolId != PdfToolId.EDIT_PDF && toolId != PdfToolId.REDACT_PDF
                    ) {
                        item {
                            Card(
                                modifier = Modifier
                                    .widthIn(max = 960.dp)
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "${AppStrings.pagePreviewsHeader(lang)} (${uiState.pageItems.size})",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    VisualPageOrganizerGrid(
                                        filePath = primaryFile.localPath,
                                        toolId = toolId,
                                        pageItems = uiState.pageItems,
                                        columns = thumbCols,
                                        onMovePage = { from, to -> viewModel.movePage(from, to) },
                                        onRotatePage = { id -> viewModel.rotatePage(id) },
                                        onDuplicatePage = { id -> viewModel.duplicatePage(id) },
                                        onDeletePage = { id -> viewModel.deletePageItem(id) },
                                        onToggleSelect = { id -> viewModel.togglePageSelection(id) }
                                    )
                                }
                            }
                        }
                    }

                    // Edit PDF Interactive Studio
                    if (toolId == PdfToolId.EDIT_PDF && primaryFile.pageCount > 0) {
                        item {
                            Card(
                                modifier = Modifier
                                    .widthIn(max = 960.dp)
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                PdfEditorStudio(
                                    filePath = primaryFile.localPath,
                                    pageCount = primaryFile.pageCount,
                                    activePage = uiState.activeEditorPage,
                                    overlays = uiState.overlays,
                                    savedSignaturePath = uiState.savedSignaturePath,
                                    onSelectPage = { viewModel.setActiveEditorPage(it) },
                                    onAddOverlay = { viewModel.addOverlay(it) },
                                    onUndo = { viewModel.undoOverlay() },
                                    onClearPage = { viewModel.clearPageOverlays() },
                                    onSaveSignature = { bmp, place -> viewModel.saveSignatureBitmap(bmp, place) },
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    }

                    // Redact PDF Interactive Canvas
                    if (toolId == PdfToolId.REDACT_PDF && primaryFile.pageCount > 0) {
                        item {
                            Card(
                                modifier = Modifier
                                    .widthIn(max = 960.dp)
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                RedactionCanvas(
                                    filePath = primaryFile.localPath,
                                    pageCount = primaryFile.pageCount,
                                    activePage = uiState.activeRedactPage,
                                    redactions = uiState.redactions,
                                    onSelectPage = { viewModel.setActiveRedactPage(it) },
                                    onAddRedaction = { viewModel.addRedactionRect(it) },
                                    onUndo = { viewModel.undoRedaction() },
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    }

                    // Action Button / Progress / Error
                    item {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 960.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            when (val phase = uiState.phase) {
                                is WorkspacePhase.Processing -> {
                                    ProcessingStatusCard(
                                        progress = phase.progress,
                                        lang = lang,
                                        onCancel = { viewModel.cancelProcessing() }
                                    )
                                }
                                is WorkspacePhase.Error -> {
                                    ErrorResultCard(
                                        message = phase.message,
                                        lang = lang,
                                        onRetry = { viewModel.resetForAnotherFile() }
                                    )
                                }
                                else -> {
                                    Button(
                                        onClick = { viewModel.process() },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Crimson600),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(54.dp)
                                            .testTag("process_action_button")
                                    ) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = AppStrings.processNowBtn(lang),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Success Card
                val successPhase = uiState.phase as? WorkspacePhase.Success
                if (successPhase != null) {
                    item {
                        SuccessResultCard(
                            result = successPhase.result,
                            lang = lang,
                            showCompressionStats = toolId == PdfToolId.COMPRESS_PDF,
                            onPreviewFile = onPreviewFile,
                            onSaveToDevice = { path, name, mime ->
                                pendingSaveFile = Triple(path, name, mime)
                                createDocLauncher.launch(name)
                            },
                            onShareFile = { path, mime ->
                                val shareIntent = FileHelper.buildShareIntent(context, path, mime)
                                context.startActivity(Intent.createChooser(shareIntent, "Share Document"))
                            },
                            onProcessAnother = { viewModel.resetForAnotherFile() },
                            modifier = Modifier
                                .widthIn(max = 960.dp)
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolOptionsPanel(
    toolId: PdfToolId,
    uiState: WorkspaceUiState,
    viewModel: WorkspaceViewModel,
    modifier: Modifier = Modifier
) {
    val hasOptions = toolId in setOf(
        PdfToolId.SPLIT_PDF, PdfToolId.EXTRACT_PAGES, PdfToolId.ROTATE_PAGES,
        PdfToolId.COMPRESS_PDF, PdfToolId.JPG_TO_PDF, PdfToolId.PNG_TO_PDF, PdfToolId.WEBP_TO_PDF,
        PdfToolId.TXT_TO_PDF, PdfToolId.PDF_TO_JPG, PdfToolId.PDF_TO_PNG, PdfToolId.PDF_TO_WEBP,
        PdfToolId.PAGE_NUMBERS, PdfToolId.WATERMARK, PdfToolId.HEADER_FOOTER,
        PdfToolId.PROTECT_PDF, PdfToolId.ENCRYPT_PDF, PdfToolId.UNLOCK_PDF, PdfToolId.REMOVE_PASSWORD,
        PdfToolId.DIGITAL_SIGNATURE, PdfToolId.SEARCH_PDF, PdfToolId.ADD_BOOKMARKS,
        PdfToolId.METADATA_INSPECT, PdfToolId.EDIT_METADATA, PdfToolId.CREATE_PDFA
    )
    if (!hasOptions) return

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Options", style = MaterialTheme.typography.titleMedium)

            when (toolId) {
                PdfToolId.SPLIT_PDF -> {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = uiState.splitMode == SplitMode.CUSTOM_RANGES,
                            onClick = { viewModel.setSplitMode(SplitMode.CUSTOM_RANGES) },
                            label = { Text("Custom Ranges") }
                        )
                        FilterChip(
                            selected = uiState.splitMode == SplitMode.FIXED_INTERVAL,
                            onClick = { viewModel.setSplitMode(SplitMode.FIXED_INTERVAL) },
                            label = { Text("Fixed Pages") }
                        )
                        FilterChip(
                            selected = uiState.splitMode == SplitMode.ALL_PAGES,
                            onClick = { viewModel.setSplitMode(SplitMode.ALL_PAGES) },
                            label = { Text("Every Page") }
                        )
                    }
                    if (uiState.splitMode == SplitMode.CUSTOM_RANGES) {
                        OutlinedTextField(
                            value = uiState.customPageRangeText,
                            onValueChange = { viewModel.setCustomRangeText(it) },
                            label = { Text("Ranges (e.g. 1-2, 3-5)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (uiState.splitMode == SplitMode.FIXED_INTERVAL) {
                        OutlinedTextField(
                            value = uiState.splitInterval.toString(),
                            onValueChange = { viewModel.setSplitInterval(it.toIntOrNull() ?: 1) },
                            label = { Text("Pages per split file") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                PdfToolId.EXTRACT_PAGES -> {
                    OutlinedTextField(
                        value = uiState.customPageRangeText,
                        onValueChange = { viewModel.setCustomRangeText(it) },
                        label = { Text("Or enter pages (e.g. 1, 3-4)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                PdfToolId.ROTATE_PAGES -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(90, 180, 270).forEach { deg ->
                            OutlinedButton(onClick = { viewModel.rotateAllPages(deg) }) {
                                Icon(Icons.Rounded.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("All +$deg°")
                            }
                        }
                    }
                }

                PdfToolId.COMPRESS_PDF -> {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CompressionLevel.entries.forEach { lvl ->
                            FilterChip(
                                selected = uiState.compressionLevel == lvl,
                                onClick = { viewModel.setCompressionLevel(lvl) },
                                label = {
                                    Text(
                                        when (lvl) {
                                            CompressionLevel.LOW -> "Low (High Quality)"
                                            CompressionLevel.MEDIUM -> "Medium (Balanced)"
                                            CompressionLevel.HIGH -> "High (Smallest)"
                                        }
                                    )
                                }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Maximum Rasterize Mode", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Re-renders pages at optimized DPI for maximum reduction on scanned/complex PDFs.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.rasterizeForMaxSavings,
                            onCheckedChange = { viewModel.setRasterizeCompress(it) }
                        )
                    }
                }

                PdfToolId.JPG_TO_PDF, PdfToolId.PNG_TO_PDF, PdfToolId.WEBP_TO_PDF -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Fit images cleanly onto A4 pages", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = uiState.fitImagesToA4, onCheckedChange = { viewModel.setFitImagesToA4(it) })
                    }
                }

                PdfToolId.TXT_TO_PDF -> {
                    Text("Font size: ${uiState.txtFontSize.toInt()} pt", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = uiState.txtFontSize,
                        onValueChange = { viewModel.setTxtFontSize(it) },
                        valueRange = 9f..22f
                    )
                }

                PdfToolId.PDF_TO_JPG, PdfToolId.PDF_TO_PNG, PdfToolId.PDF_TO_WEBP -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(900 to "Standard", 1400 to "HD", 2100 to "Ultra").forEach { (w, label) ->
                            FilterChip(
                                selected = uiState.imageExportWidth == w,
                                onClick = { viewModel.setImageExportWidth(w) },
                                label = { Text("$label (${w}px)") }
                            )
                        }
                    }
                }

                PdfToolId.PAGE_NUMBERS -> {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PageNumberPosition.entries.forEach { pos ->
                            FilterChip(
                                selected = uiState.pageNumberOptions.position == pos,
                                onClick = { viewModel.updatePageNumberOptions { it.copy(position = pos) } },
                                label = { Text(pos.name.replace('_', ' ')) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = uiState.pageNumberOptions.formatTemplate,
                        onValueChange = { txt -> viewModel.updatePageNumberOptions { it.copy(formatTemplate = txt) } },
                        label = { Text("Format (use {n} and {total})") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                PdfToolId.WATERMARK -> {
                    OutlinedTextField(
                        value = uiState.watermarkOptions.text,
                        onValueChange = { txt -> viewModel.updateWatermarkOptions { it.copy(text = txt) } },
                        label = { Text("Watermark text") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Opacity: ${(uiState.watermarkOptions.opacity * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = uiState.watermarkOptions.opacity,
                        onValueChange = { op -> viewModel.updateWatermarkOptions { it.copy(opacity = op) } },
                        valueRange = 0.1f..0.85f
                    )
                    Text("Angle: ${uiState.watermarkOptions.angleDegrees.toInt()}°", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = uiState.watermarkOptions.angleDegrees,
                        onValueChange = { ang -> viewModel.updateWatermarkOptions { it.copy(angleDegrees = ang) } },
                        valueRange = -90f..90f
                    )
                }

                PdfToolId.HEADER_FOOTER -> {
                    OutlinedTextField(
                        value = uiState.headerFooterOptions.headerText,
                        onValueChange = { txt -> viewModel.updateHeaderFooterOptions { it.copy(headerText = txt) } },
                        label = { Text("Top Header text") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = uiState.headerFooterOptions.footerText,
                        onValueChange = { txt -> viewModel.updateHeaderFooterOptions { it.copy(footerText = txt) } },
                        label = { Text("Bottom Footer text") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                PdfToolId.PROTECT_PDF, PdfToolId.ENCRYPT_PDF -> {
                    OutlinedTextField(
                        value = uiState.userPassword,
                        onValueChange = { viewModel.setUserPassword(it) },
                        label = { Text("Open Password") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("protect_user_password")
                    )
                    if (toolId == PdfToolId.ENCRYPT_PDF) {
                        OutlinedTextField(
                            value = uiState.ownerPassword,
                            onValueChange = { viewModel.setOwnerPassword(it) },
                            label = { Text("Owner / Permissions Password (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = uiState.allowPrint, onCheckedChange = {
                                viewModel.setPermissions(it, uiState.allowCopy, uiState.allowModify)
                            })
                            Text("Allow Printing")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = uiState.allowCopy, onCheckedChange = {
                                viewModel.setPermissions(uiState.allowPrint, it, uiState.allowModify)
                            })
                            Text("Allow Copy Text/Images")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = uiState.allowModify, onCheckedChange = {
                                viewModel.setPermissions(uiState.allowPrint, uiState.allowCopy, it)
                            })
                            Text("Allow Modifications")
                        }
                    }
                }

                PdfToolId.UNLOCK_PDF, PdfToolId.REMOVE_PASSWORD -> {
                    OutlinedTextField(
                        value = uiState.userPassword,
                        onValueChange = { viewModel.setUserPassword(it) },
                        label = { Text("Current PDF Password") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("unlock_password_input")
                    )
                }

                PdfToolId.DIGITAL_SIGNATURE -> {
                    OutlinedTextField(
                        value = uiState.signerName,
                        onValueChange = { viewModel.setSignerDetails(it, uiState.signerReason, uiState.signerLocation) },
                        label = { Text("Signer Full Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = uiState.signerReason,
                        onValueChange = { viewModel.setSignerDetails(uiState.signerName, it, uiState.signerLocation) },
                        label = { Text("Reason") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = uiState.signerLocation,
                        onValueChange = { viewModel.setSignerDetails(uiState.signerName, uiState.signerReason, it) },
                        label = { Text("Location (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                PdfToolId.SEARCH_PDF -> {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        label = { Text("Keyword or phrase to search") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pdf_search_query_input")
                    )
                }

                PdfToolId.ADD_BOOKMARKS -> {
                    uiState.bookmarks.forEach { bm ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = bm.title,
                                onValueChange = { viewModel.updateBookmark(bm.id, it, bm.pageNumber1Based) },
                                label = { Text("Bookmark Title") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = bm.pageNumber1Based.toString(),
                                onValueChange = { viewModel.updateBookmark(bm.id, bm.title, it.toIntOrNull() ?: 1) },
                                label = { Text("Page") },
                                singleLine = true,
                                modifier = Modifier.width(84.dp)
                            )
                            IconButton(onClick = { viewModel.removeBookmark(bm.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Remove bookmark", tint = Crimson600)
                            }
                        }
                    }
                    OutlinedButton(onClick = { viewModel.addBookmarkRow() }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Bookmark")
                    }
                }

                PdfToolId.METADATA_INSPECT -> {
                    val m = uiState.metadataInfo
                    Text("PDF Version: ${m.pdfVersion} • Pages: ${m.pageCount} • Encrypted: ${m.isEncrypted}", style = MaterialTheme.typography.bodySmall)
                    Text("Title: ${m.title.ifBlank { "(none)" }}", style = MaterialTheme.typography.bodySmall)
                    Text("Author: ${m.author.ifBlank { "(none)" }}", style = MaterialTheme.typography.bodySmall)
                    Text("Producer: ${m.producer.ifBlank { "(none)" }}", style = MaterialTheme.typography.bodySmall)
                    Text("Created: ${m.creationDate.ifBlank { "(unknown)" }}", style = MaterialTheme.typography.bodySmall)
                }

                PdfToolId.EDIT_METADATA -> {
                    val m = uiState.metadataInfo
                    OutlinedTextField(
                        value = m.title,
                        onValueChange = { v -> viewModel.updateMetadataInfo { it.copy(title = v) } },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = m.author,
                        onValueChange = { v -> viewModel.updateMetadataInfo { it.copy(author = v) } },
                        label = { Text("Author") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = m.subject,
                        onValueChange = { v -> viewModel.updateMetadataInfo { it.copy(subject = v) } },
                        label = { Text("Subject") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = m.keywords,
                        onValueChange = { v -> viewModel.updateMetadataInfo { it.copy(keywords = v) } },
                        label = { Text("Keywords") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                PdfToolId.CREATE_PDFA -> {
                    OutlinedTextField(
                        value = uiState.pdfaTitle,
                        onValueChange = { viewModel.setPdfAFields(it, uiState.pdfaAuthor) },
                        label = { Text("Archival Document Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = uiState.pdfaAuthor,
                        onValueChange = { viewModel.setPdfAFields(uiState.pdfaTitle, it) },
                        label = { Text("Archival Author") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                else -> Unit
            }
        }
    }
}
