package com.example.ui.components

import android.content.ClipData
import android.content.ClipDescription
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.engine.FileHelper
import com.example.i18n.AppStrings
import com.example.model.AppLanguage
import com.example.model.DiffLineType
import com.example.model.PdfToolId
import com.example.model.PickedFile
import com.example.model.ProcessingProgress
import com.example.model.WorkspaceSuccessResult
import com.example.ui.theme.Crimson600
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfDropZoneCard(
    title: String,
    subtitle: String,
    buttonLabel: String,
    onPickClick: () -> Unit,
    onFilesDropped: (List<Uri>) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "upload_dropzone"
) {
    var isDraggingOver by remember { mutableStateOf(false) }

    val dndTarget = remember(onFilesDropped) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                isDraggingOver = true
            }

            override fun onExited(event: DragAndDropEvent) {
                isDraggingOver = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                isDraggingOver = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                isDraggingOver = false
                val clipData: ClipData = event.toAndroidDragEvent().clipData ?: return false
                val uris = mutableListOf<Uri>()
                for (i in 0 until clipData.itemCount) {
                    clipData.getItemAt(i)?.uri?.let { uris.add(it) }
                }
                if (uris.isNotEmpty()) {
                    onFilesDropped(uris)
                    return true
                }
                return false
            }
        }
    }

    val borderColor = if (isDraggingOver) Crimson600 else MaterialTheme.colorScheme.outline
    val bgColor = if (isDraggingOver) {
        Crimson600.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(bgColor)
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event ->
                    event.toAndroidDragEvent().clipDescription?.let { desc ->
                        desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST) ||
                            desc.mimeTypeCount > 0
                    } ?: true
                },
                target = dndTarget
            )
            .clickable(onClick = onPickClick)
            .testTag(testTag)
            .padding(4.dp)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRoundRect(
                color = borderColor,
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f), 0f)
                ),
                cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx())
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Crimson600.copy(alpha = 0.12f),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.CloudUpload,
                        contentDescription = title,
                        tint = Crimson600,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Button(
                onClick = onPickClick,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Crimson600),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("${testTag}_button")
            ) {
                Text(text = buttonLabel, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun ToolCard(
    tool: PdfToolId,
    lang: AppLanguage,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val text = AppStrings.toolText(tool, lang)
    val accent = tool.category.color

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("tool_card_${tool.name}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accent.copy(alpha = 0.12f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = text.title,
                            tint = accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("fav_${tool.name}")
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove favorite" else "Add favorite",
                        tint = if (isFavorite) Crimson600 else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(
                text = text.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = text.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SelectedFileRow(
    file: PickedFile,
    index: Int,
    totalCount: Int,
    lang: AppLanguage,
    canReorder: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Crimson600.copy(alpha = 0.1f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (file.name.endsWith(".pdf", true)) {
                            Icons.Rounded.PictureAsPdf
                        } else Icons.Rounded.InsertDriveFile,
                        contentDescription = file.name,
                        tint = Crimson600,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = FileHelper.formatBytes(file.sizeBytes),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (file.pageCount > 0) {
                        Text(
                            text = "• ${AppStrings.pagesLabel(lang, file.pageCount)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (file.isPasswordProtected) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Protected",
                            tint = WarningAmber,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            if (canReorder && totalCount > 1) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = index > 0,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = index < totalCount - 1,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                }
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("remove_file_${file.id}")
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Remove file", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun ProcessingStatusCard(
    progress: ProcessingProgress,
    lang: AppLanguage,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(targetValue = progress.fraction, label = "progress_anim")
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = AppStrings.processingTitle(lang),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${(animatedProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = Crimson600
            )
            Text(
                text = progress.statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .align(Alignment.End)
                    .minimumInteractiveComponentSize()
                    .testTag("cancel_processing_button")
            ) {
                Text(AppStrings.cancelBtn(lang))
            }
        }
    }
}

@Composable
fun SuccessResultCard(
    result: WorkspaceSuccessResult,
    lang: AppLanguage,
    showCompressionStats: Boolean,
    onPreviewFile: (path: String, title: String, mime: String) -> Unit,
    onSaveToDevice: (path: String, fileName: String, mime: String) -> Unit,
    onShareFile: (path: String, mime: String) -> Unit,
    onProcessAnother: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    var copiedBanner by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, SuccessGreen.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(30.dp)
                )
                Column {
                    Text(
                        text = AppStrings.successHeader(lang),
                        style = MaterialTheme.typography.titleLarge,
                        color = SuccessGreen
                    )
                    Text(
                        text = result.primaryFile.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (result.summaryNote.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = result.summaryNote,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Compression metrics (Real bytes only)
            if (showCompressionStats && result.originalSizeBytes > 0) {
                val newSize = result.primaryFile.sizeBytes
                val savedPct = (((result.originalSizeBytes - newSize).toDouble() / result.originalSizeBytes) * 100.0)
                    .coerceAtLeast(0.0)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricPill(
                        label = AppStrings.originalSizeLabel(lang),
                        value = FileHelper.formatBytes(result.originalSizeBytes),
                        modifier = Modifier.weight(1f)
                    )
                    MetricPill(
                        label = AppStrings.compressedSizeLabel(lang),
                        value = FileHelper.formatBytes(newSize),
                        modifier = Modifier.weight(1f)
                    )
                    MetricPill(
                        label = AppStrings.reductionLabel(lang),
                        value = "${String.format(java.util.Locale.US, "%.1f", savedPct)}%",
                        highlight = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Primary action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onSaveToDevice(
                            result.primaryFile.path,
                            result.primaryFile.name,
                            result.primaryFile.mimeType
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Crimson600),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("save_result_button")
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(AppStrings.saveToDeviceBtn(lang), maxLines = 1)
                }

                OutlinedButton(
                    onClick = {
                        onShareFile(result.primaryFile.path, result.primaryFile.mimeType)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("share_result_button")
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(AppStrings.shareBtn(lang), maxLines = 1)
                }
            }

            if (result.primaryFile.mimeType == "application/pdf" || result.primaryFile.mimeType == "text/plain") {
                OutlinedButton(
                    onClick = {
                        onPreviewFile(
                            result.primaryFile.path,
                            result.primaryFile.name,
                            result.primaryFile.mimeType
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("preview_result_button")
                ) {
                    Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(AppStrings.previewResultBtn(lang))
                }
            }

            // ZIP export if multiple outputs
            result.zipArchive?.let { zip ->
                Button(
                    onClick = { onSaveToDevice(zip.path, zip.name, zip.mimeType) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("download_zip_button")
                ) {
                    Icon(Icons.Rounded.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${AppStrings.downloadZipBtn(lang)} (${FileHelper.formatBytes(zip.sizeBytes)})")
                }
            }

            // Extracted text preview & copy
            if (!result.extractedText.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (copiedBanner) AppStrings.copiedMsg(lang) else "Extracted Output",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (copiedBanner) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(result.extractedText))
                                    copiedBanner = true
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("copy_extracted_text_button")
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(AppStrings.copyTextBtn(lang))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = result.extractedText,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily)
                            )
                        }
                    }
                }
            }

            // Search matches
            if (result.searchMatches.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    result.searchMatches.take(25).forEach { match ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Page ${match.pageNumber1Based} • ${match.matchCount} match(es)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Crimson600
                                )
                                Text(
                                    text = match.snippet,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            // Compare diffs
            if (result.pageComparisons.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    result.pageComparisons.filter { it.hasChanges }.take(10).forEach { cmp ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Page ${cmp.pageNumber1Based}", style = MaterialTheme.typography.labelLarge)
                                cmp.diffLines.filter { it.type != DiffLineType.UNCHANGED }.take(12).forEach { line ->
                                    val color = if (line.type == DiffLineType.ADDED) SuccessGreen else Crimson600
                                    val prefix = if (line.type == DiffLineType.ADDED) "+ " else "- "
                                    Text(
                                        text = "$prefix${line.text}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                                        color = color
                                    )
                                }
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onProcessAnother,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("process_another_button")
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(AppStrings.processAnotherBtn(lang))
            }
        }
    }
}

@Composable
private fun MetricPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (highlight) SuccessGreen.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily),
                color = if (highlight) SuccessGreen else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ErrorResultCard(
    message: String,
    lang: AppLanguage,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(
                    text = AppStrings.errorHeader(lang),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("error_retry_button")
            ) {
                Text(AppStrings.processAnotherBtn(lang))
            }
        }
    }
}
