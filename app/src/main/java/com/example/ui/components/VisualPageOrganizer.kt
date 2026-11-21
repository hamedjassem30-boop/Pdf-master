package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.engine.PdfThumbnailRenderer
import com.example.model.PageItemState
import com.example.model.PdfToolId
import com.example.ui.theme.Crimson600
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SuccessGreen

@Composable
fun VisualPageOrganizerGrid(
    filePath: String,
    toolId: PdfToolId,
    pageItems: List<PageItemState>,
    columns: Int,
    onMovePage: (fromIndex: Int, toIndex: Int) -> Unit,
    onRotatePage: (id: String) -> Unit,
    onDuplicatePage: (id: String) -> Unit,
    onDeletePage: (id: String) -> Unit,
    onToggleSelect: (id: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var zoomPageIndex by remember { mutableStateOf<Int?>(null) }

    val showCheckboxes = toolId == PdfToolId.EXTRACT_PAGES || toolId == PdfToolId.DELETE_PAGES
    val isDeleteMode = toolId == PdfToolId.DELETE_PAGES

    val rows = remember(pageItems, columns) {
        pageItems.chunked(columns.coerceAtLeast(1))
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = when (toolId) {
                PdfToolId.DELETE_PAGES -> "Tap pages to mark for deletion (highlighted in red)."
                PdfToolId.EXTRACT_PAGES -> "Select pages to include in the extracted PDF."
                else -> "Long-press or use arrows to reorder, rotate, duplicate, or remove pages."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        var globalIndex = 0
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { item ->
                    val currentPos = globalIndex
                    globalIndex++
                    PageThumbnailCard(
                        filePath = filePath,
                        item = item,
                        positionIndex = currentPos,
                        totalItems = pageItems.size,
                        showCheckbox = showCheckboxes,
                        isDeleteMode = isDeleteMode,
                        onMoveLeft = { if (currentPos > 0) onMovePage(currentPos, currentPos - 1) },
                        onMoveRight = { if (currentPos < pageItems.lastIndex) onMovePage(currentPos, currentPos + 1) },
                        onDragReorderDelta = { deltaSteps ->
                            val target = (currentPos + deltaSteps).coerceIn(0, pageItems.lastIndex)
                            if (target != currentPos) onMovePage(currentPos, target)
                        },
                        onRotate = { onRotatePage(item.id) },
                        onDuplicate = { onDuplicatePage(item.id) },
                        onDelete = { onDeletePage(item.id) },
                        onToggleSelect = { onToggleSelect(item.id) },
                        onZoomClick = { zoomPageIndex = item.originalPageIndex },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }

    zoomPageIndex?.let { pIdx ->
        PinchZoomPageDialog(
            filePath = filePath,
            pageIndex = pIdx,
            onDismiss = { zoomPageIndex = null }
        )
    }
}

@Composable
private fun PageThumbnailCard(
    filePath: String,
    item: PageItemState,
    positionIndex: Int,
    totalItems: Int,
    showCheckbox: Boolean,
    isDeleteMode: Boolean,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onDragReorderDelta: (Int) -> Unit,
    onRotate: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onToggleSelect: () -> Unit,
    onZoomClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(filePath, item.originalPageIndex) { mutableStateOf<Bitmap?>(null) }
    var dragAccumX by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(filePath, item.originalPageIndex) {
        bitmap = PdfThumbnailRenderer.renderPage(filePath, item.originalPageIndex, 340)
    }

    val borderColor = when {
        showCheckbox && isDeleteMode && item.isSelected -> Crimson600
        showCheckbox && !isDeleteMode && item.isSelected -> SuccessGreen
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .pointerInput(positionIndex, totalItems) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragAccumX = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumX += dragAmount.x
                        if (dragAccumX > 110f) {
                            onDragReorderDelta(1)
                            dragAccumX = 0f
                        } else if (dragAccumX < -110f) {
                            onDragReorderDelta(-1)
                            dragAccumX = 0f
                        }
                    }
                )
            }
            .clickable {
                if (showCheckbox) onToggleSelect() else onZoomClick()
            }
            .testTag("page_thumb_${item.originalPageIndex}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(if (showCheckbox && item.isSelected) 2.dp else 1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.74f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                val bmp = bitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Page ${item.originalPageIndex + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationZ = item.rotationDegrees.toFloat() }
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }

                // Page number pill
                Surface(
                    color = Color.Black.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                ) {
                    Text(
                        text = "#${item.originalPageIndex + 1}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Zoom / Checkbox badge
                if (showCheckbox) {
                    Icon(
                        imageVector = if (item.isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = "Select page",
                        tint = if (item.isSelected) {
                            if (isDeleteMode) Crimson600 else SuccessGreen
                        } else Color.Gray,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(24.dp)
                    )
                } else {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(28.dp)
                            .clickable(onClick = onZoomClick)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.ZoomIn,
                                contentDescription = "Zoom page",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onMoveLeft,
                    enabled = positionIndex > 0,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Move left", modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onRotate, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.RotateRight, contentDescription = "Rotate 90", modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDuplicate, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Duplicate", modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = onDelete,
                    enabled = totalItems > 1,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete page", tint = Crimson600, modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = onMoveRight,
                    enabled = positionIndex < totalItems - 1,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Move right", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun PinchZoomPageDialog(
    filePath: String,
    pageIndex: Int,
    onDismiss: () -> Unit
) {
    var hiResBitmap by remember(filePath, pageIndex) { mutableStateOf<Bitmap?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(filePath, pageIndex) {
        hiResBitmap = PdfThumbnailRenderer.renderPage(filePath, pageIndex, 1200)
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        offset = if (scale > 1f) offset + panChange else Offset.Zero
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Page ${pageIndex + 1} (Pinch to zoom)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close preview")
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F172A))
                        .transformable(state = transformState),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = hiResBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Zoomed page ${pageIndex + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offset.x
                                    translationY = offset.y
                                }
                        )
                    } else {
                        CircularProgressIndicator(color = Crimson600)
                    }
                }
            }
        }
    }
}
