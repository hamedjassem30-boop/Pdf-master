package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.engine.PdfThumbnailRenderer
import com.example.model.RedactionRect
import com.example.ui.theme.Crimson600
import java.util.UUID

@Composable
fun RedactionCanvas(
    filePath: String,
    pageCount: Int,
    activePage: Int,
    redactions: List<RedactionRect>,
    onSelectPage: (Int) -> Unit,
    onAddRedaction: (RedactionRect) -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pageBitmap by remember(filePath, activePage) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(filePath, activePage) {
        pageBitmap = PdfThumbnailRenderer.renderPage(filePath, activePage, 950)
    }

    var boxSize by remember { mutableStateOf(IntSize(1, 1)) }
    var dragStart by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var dragEnd by remember { mutableStateOf<Pair<Float, Float>?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { if (activePage > 0) onSelectPage(activePage - 1) },
                    enabled = activePage > 0,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Previous page")
                }
                Text("Page ${activePage + 1} / ${pageCount.coerceAtLeast(1)}", style = MaterialTheme.typography.titleSmall)
                IconButton(
                    onClick = { if (activePage < pageCount - 1) onSelectPage(activePage + 1) },
                    enabled = activePage < pageCount - 1,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Next page")
                }
            }
            IconButton(onClick = onUndo, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo redaction")
            }
        }

        Text(
            text = "Drag over sensitive text or images to mark permanent redaction areas (${redactions.size} total). Underlying text on redacted pages is permanently destroyed.",
            style = MaterialTheme.typography.bodySmall,
            color = Crimson600
        )

        val aspect = pageBitmap?.let { it.width.toFloat() / it.height.coerceAtLeast(1).toFloat() } ?: 0.707f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect.coerceIn(0.5f, 1.8f))
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                .onSizeChanged { boxSize = it }
                .pointerInput(activePage) {
                    detectDragGestures(
                        onDragStart = { start ->
                            val w = boxSize.width.coerceAtLeast(1).toFloat()
                            val h = boxSize.height.coerceAtLeast(1).toFloat()
                            val nx = (start.x / w).coerceIn(0f, 1f)
                            val ny = (start.y / h).coerceIn(0f, 1f)
                            dragStart = nx to ny
                            dragEnd = nx to ny
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val w = boxSize.width.coerceAtLeast(1).toFloat()
                            val h = boxSize.height.coerceAtLeast(1).toFloat()
                            dragEnd = (change.position.x / w).coerceIn(0f, 1f) to
                                (change.position.y / h).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            val s = dragStart
                            val e = dragEnd
                            if (s != null && e != null) {
                                onAddRedaction(
                                    RedactionRect(
                                        id = UUID.randomUUID().toString(),
                                        pageIndex = activePage,
                                        normLeft = s.first,
                                        normTop = s.second,
                                        normRight = e.first,
                                        normBottom = e.second
                                    )
                                )
                            }
                            dragStart = null
                            dragEnd = null
                        }
                    )
                }
        ) {
            val bmp = pageBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Page to redact",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            val pageRects = redactions.filter { it.pageIndex == activePage }
            Canvas(modifier = Modifier.fillMaxSize()) {
                pageRects.forEach { r ->
                    val l = minOf(r.normLeft, r.normRight) * size.width
                    val t = minOf(r.normTop, r.normBottom) * size.height
                    val w = (maxOf(r.normLeft, r.normRight) - minOf(r.normLeft, r.normRight)) * size.width
                    val h = (maxOf(r.normTop, r.normBottom) - minOf(r.normTop, r.normBottom)) * size.height
                    drawRect(Color.Black.copy(alpha = 0.88f), Offset(l, t), Size(w, h))
                }
                val s = dragStart
                val e = dragEnd
                if (s != null && e != null) {
                    val l = minOf(s.first, e.first) * size.width
                    val t = minOf(s.second, e.second) * size.height
                    val w = (maxOf(s.first, e.first) - minOf(s.first, e.first)) * size.width
                    val h = (maxOf(s.second, e.second) - minOf(s.second, e.second)) * size.height
                    drawRect(Crimson600.copy(alpha = 0.45f), Offset(l, t), Size(w, h))
                }
            }
        }
    }
}
