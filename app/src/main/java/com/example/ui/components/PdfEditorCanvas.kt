package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.FileHelper
import com.example.engine.PdfThumbnailRenderer
import com.example.model.EditOverlay
import com.example.model.EditorToolMode
import com.example.ui.theme.Crimson600
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun PdfEditorStudio(
    filePath: String,
    pageCount: Int,
    activePage: Int,
    overlays: List<EditOverlay>,
    savedSignaturePath: String?,
    onSelectPage: (Int) -> Unit,
    onAddOverlay: (EditOverlay) -> Unit,
    onUndo: () -> Unit,
    onClearPage: () -> Unit,
    onSaveSignature: (Bitmap, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var toolMode by remember { mutableStateOf(EditorToolMode.DRAW) }
    var selectedColor by remember { mutableStateOf(Color(0xFFE11D48)) }
    var fontSizeSp by remember { mutableFloatStateOf(18f) }

    var pendingTextTap by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var textDialogInput by remember { mutableStateOf("") }
    var showSignaturePad by remember { mutableStateOf(false) }

    var pageBitmap by remember(filePath, activePage) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(filePath, activePage) {
        pageBitmap = PdfThumbnailRenderer.renderPage(filePath, activePage, 950)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val outImg = FileHelper.createTempFile(context, "editor_img", "png")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(outImg).use { out -> input.copyTo(out) }
                }
                onAddOverlay(
                    EditOverlay.ImageOverlay(
                        id = UUID.randomUUID().toString(),
                        pageIndex = activePage,
                        imagePath = outImg.absolutePath,
                        normX = 0.25f,
                        normY = 0.25f,
                        normWidth = 0.38f,
                        normHeight = 0.22f
                    )
                )
            }
        }
    }

    val colorSwatches = listOf(
        Color(0xFF0F172A),
        Color(0xFFE11D48),
        Color(0xFF2563EB),
        Color(0xFF059669),
        Color(0xFFF59E0B),
        Color(0xFF7C3AED)
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Page Navigation Bar
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
                Text(
                    text = "Page ${activePage + 1} / ${pageCount.coerceAtLeast(1)}",
                    style = MaterialTheme.typography.titleSmall
                )
                IconButton(
                    onClick = { if (activePage < pageCount - 1) onSelectPage(activePage + 1) },
                    enabled = activePage < pageCount - 1,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Next page")
                }
            }
            Row {
                IconButton(onClick = onUndo, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo edit")
                }
                IconButton(onClick = onClearPage, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear page edits", tint = Crimson600)
                }
            }
        }

        // Tool Mode Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EditorModeChip(EditorToolMode.TEXT, toolMode, Icons.Rounded.TextFields, "Text") { toolMode = it }
            EditorModeChip(EditorToolMode.IMAGE, toolMode, Icons.Rounded.Image, "Image") {
                toolMode = it
                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            EditorModeChip(EditorToolMode.DRAW, toolMode, Icons.Rounded.Brush, "Draw") { toolMode = it }
            EditorModeChip(EditorToolMode.HIGHLIGHT, toolMode, Icons.Rounded.Highlight, "Highlight") { toolMode = it }
            EditorModeChip(EditorToolMode.SHAPE_RECT, toolMode, Icons.Rounded.CropSquare, "Rect") { toolMode = it }
            EditorModeChip(EditorToolMode.SHAPE_CIRCLE, toolMode, Icons.Rounded.Circle, "Circle") { toolMode = it }
            EditorModeChip(EditorToolMode.SIGNATURE, toolMode, Icons.Rounded.Draw, "Sign") {
                toolMode = it
                showSignaturePad = true
            }
            EditorModeChip(EditorToolMode.DATE, toolMode, Icons.Rounded.CalendarToday, "Date") {
                toolMode = it
            }
        }

        // Color swatches + font size
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                colorSwatches.forEach { swatch ->
                    val isSel = swatch == selectedColor
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .border(if (isSel) 3.dp else 1.dp, if (isSel) Color.White else Color.Gray, CircleShape)
                            .clickable { selectedColor = swatch }
                    )
                }
            }
            if (toolMode == EditorToolMode.TEXT || toolMode == EditorToolMode.DATE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${fontSizeSp.toInt()}sp", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = fontSizeSp,
                        onValueChange = { fontSizeSp = it },
                        valueRange = 12f..36f,
                        modifier = Modifier.width(100.dp)
                    )
                }
            }
        }

        val hintText = when (toolMode) {
            EditorToolMode.TEXT -> "Tap anywhere on the page to place custom text."
            EditorToolMode.DATE -> "Tap anywhere on the page to stamp today's date."
            EditorToolMode.DRAW -> "Drag across the page to draw freehand."
            EditorToolMode.HIGHLIGHT -> "Drag across text lines to highlight."
            EditorToolMode.SHAPE_RECT, EditorToolMode.SHAPE_CIRCLE -> "Drag across the page to draw shape."
            EditorToolMode.IMAGE -> "Tap 'Image' chip to pick a logo or photo from gallery."
            EditorToolMode.SIGNATURE -> "Draw your signature in the pad to stamp onto the page."
        }
        Text(
            text = hintText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Interactive WYSIWYG Canvas
        var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }
        val activeStrokePoints = remember { mutableStateListOf<Pair<Float, Float>>() }
        var dragStartNorm by remember { mutableStateOf<Pair<Float, Float>?>(null) }
        var dragEndNorm by remember { mutableStateOf<Pair<Float, Float>?>(null) }
        val textMeasurer = rememberTextMeasurer()

        val pageAspect = pageBitmap?.let { it.width.toFloat() / it.height.coerceAtLeast(1).toFloat() } ?: 0.707f

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(pageAspect.coerceIn(0.5f, 1.8f))
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                .onSizeChanged { canvasSize = it }
                .pointerInput(toolMode, activePage, selectedColor, fontSizeSp) {
                    detectTapGestures { offset ->
                        val w = canvasSize.width.coerceAtLeast(1).toFloat()
                        val h = canvasSize.height.coerceAtLeast(1).toFloat()
                        val nx = (offset.x / w).coerceIn(0.02f, 0.95f)
                        val ny = (offset.y / h).coerceIn(0.04f, 0.96f)
                        if (toolMode == EditorToolMode.TEXT) {
                            pendingTextTap = nx to ny
                            textDialogInput = ""
                        } else if (toolMode == EditorToolMode.DATE) {
                            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                            onAddOverlay(
                                EditOverlay.TextOverlay(
                                    id = UUID.randomUUID().toString(),
                                    pageIndex = activePage,
                                    text = dateStr,
                                    normX = nx,
                                    normY = ny,
                                    fontSizeSp = fontSizeSp,
                                    colorArgb = selectedColor.toArgb()
                                )
                            )
                        }
                    }
                }
                .pointerInput(toolMode, activePage, selectedColor) {
                    detectDragGestures(
                        onDragStart = { start ->
                            val w = canvasSize.width.coerceAtLeast(1).toFloat()
                            val h = canvasSize.height.coerceAtLeast(1).toFloat()
                            val nx = (start.x / w).coerceIn(0f, 1f)
                            val ny = (start.y / h).coerceIn(0f, 1f)
                            when (toolMode) {
                                EditorToolMode.DRAW, EditorToolMode.HIGHLIGHT -> {
                                    activeStrokePoints.clear()
                                    activeStrokePoints.add(nx to ny)
                                }
                                EditorToolMode.SHAPE_RECT, EditorToolMode.SHAPE_CIRCLE -> {
                                    dragStartNorm = nx to ny
                                    dragEndNorm = nx to ny
                                }
                                else -> Unit
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val w = canvasSize.width.coerceAtLeast(1).toFloat()
                            val h = canvasSize.height.coerceAtLeast(1).toFloat()
                            val nx = (change.position.x / w).coerceIn(0f, 1f)
                            val ny = (change.position.y / h).coerceIn(0f, 1f)
                            when (toolMode) {
                                EditorToolMode.DRAW, EditorToolMode.HIGHLIGHT -> activeStrokePoints.add(nx to ny)
                                EditorToolMode.SHAPE_RECT, EditorToolMode.SHAPE_CIRCLE -> dragEndNorm = nx to ny
                                else -> Unit
                            }
                        },
                        onDragEnd = {
                            when (toolMode) {
                                EditorToolMode.DRAW, EditorToolMode.HIGHLIGHT -> {
                                    if (activeStrokePoints.size >= 2) {
                                        onAddOverlay(
                                            EditOverlay.PathOverlay(
                                                id = UUID.randomUUID().toString(),
                                                pageIndex = activePage,
                                                points = activeStrokePoints.toList(),
                                                colorArgb = if (toolMode == EditorToolMode.HIGHLIGHT) {
                                                    Color(0xFFFACC15).toArgb()
                                                } else selectedColor.toArgb(),
                                                strokeWidthNorm = if (toolMode == EditorToolMode.HIGHLIGHT) 0.032f else 0.007f,
                                                isHighlighter = toolMode == EditorToolMode.HIGHLIGHT
                                            )
                                        )
                                    }
                                    activeStrokePoints.clear()
                                }
                                EditorToolMode.SHAPE_RECT, EditorToolMode.SHAPE_CIRCLE -> {
                                    val s = dragStartNorm
                                    val e = dragEndNorm
                                    if (s != null && e != null) {
                                        onAddOverlay(
                                            EditOverlay.ShapeOverlay(
                                                id = UUID.randomUUID().toString(),
                                                pageIndex = activePage,
                                                isCircle = toolMode == EditorToolMode.SHAPE_CIRCLE,
                                                normLeft = s.first,
                                                normTop = s.second,
                                                normRight = e.first,
                                                normBottom = e.second,
                                                colorArgb = selectedColor.toArgb(),
                                                strokeWidthNorm = 0.006f
                                            )
                                        )
                                    }
                                    dragStartNorm = null
                                    dragEndNorm = null
                                }
                                else -> Unit
                            }
                        }
                    )
                }
        ) {
            val bmp = pageBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Editable PDF Page",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            // Live overlay preview
            val pageOverlays = overlays.filter { it.pageIndex == activePage }
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                pageOverlays.forEach { ov ->
                    when (ov) {
                        is EditOverlay.PathOverlay -> {
                            if (ov.points.size >= 2) {
                                val path = Path()
                                ov.points.forEachIndexed { idx, p ->
                                    if (idx == 0) path.moveTo(p.first * w, p.second * h)
                                    else path.lineTo(p.first * w, p.second * h)
                                }
                                drawPath(
                                    path = path,
                                    color = Color(ov.colorArgb).copy(alpha = if (ov.isHighlighter) 0.42f else 1f),
                                    style = Stroke(
                                        width = (ov.strokeWidthNorm * w).coerceAtLeast(3f),
                                        cap = if (ov.isHighlighter) StrokeCap.Square else StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }

                        is EditOverlay.ShapeOverlay -> {
                            val left = minOf(ov.normLeft, ov.normRight) * w
                            val top = minOf(ov.normTop, ov.normBottom) * h
                            val right = maxOf(ov.normLeft, ov.normRight) * w
                            val bottom = maxOf(ov.normTop, ov.normBottom) * h
                            if (ov.isCircle) {
                                drawOval(
                                    color = Color(ov.colorArgb),
                                    topLeft = Offset(left, top),
                                    size = Size(right - left, bottom - top),
                                    style = Stroke((ov.strokeWidthNorm * w).coerceAtLeast(3f))
                                )
                            } else {
                                drawRect(
                                    color = Color(ov.colorArgb),
                                    topLeft = Offset(left, top),
                                    size = Size(right - left, bottom - top),
                                    style = Stroke((ov.strokeWidthNorm * w).coerceAtLeast(3f))
                                )
                            }
                        }

                        is EditOverlay.TextOverlay -> {
                            val measured = textMeasurer.measure(
                                text = ov.text,
                                style = TextStyle(
                                    color = Color(ov.colorArgb),
                                    fontSize = ov.fontSizeSp.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            drawText(
                                textLayoutResult = measured,
                                topLeft = Offset(ov.normX * w, (ov.normY * h - measured.size.height).coerceAtLeast(0f))
                            )
                        }

                        is EditOverlay.ImageOverlay -> {
                            val imgBmp = BitmapFactory.decodeFile(ov.imagePath)
                            if (imgBmp != null) {
                                drawImage(
                                    image = imgBmp.asImageBitmap(),
                                    dstOffset = IntOffset((ov.normX * w).roundToInt(), (ov.normY * h).roundToInt()),
                                    dstSize = IntSize((ov.normWidth * w).roundToInt(), (ov.normHeight * h).roundToInt())
                                )
                            }
                        }
                    }
                }

                // Active stroke in progress
                if (activeStrokePoints.size >= 2) {
                    val path = Path()
                    activeStrokePoints.forEachIndexed { idx, p ->
                        if (idx == 0) path.moveTo(p.first * w, p.second * h)
                        else path.lineTo(p.first * w, p.second * h)
                    }
                    val isHi = toolMode == EditorToolMode.HIGHLIGHT
                    drawPath(
                        path = path,
                        color = if (isHi) Color(0xFFFACC15).copy(alpha = 0.45f) else selectedColor,
                        style = Stroke(width = if (isHi) w * 0.032f else w * 0.007f, cap = StrokeCap.Round)
                    )
                }

                // Active shape preview
                val ds = dragStartNorm
                val de = dragEndNorm
                if (ds != null && de != null) {
                    val left = minOf(ds.first, de.first) * w
                    val top = minOf(ds.second, de.second) * h
                    val sw = (maxOf(ds.first, de.first) - minOf(ds.first, de.first)) * w
                    val sh = (maxOf(ds.second, de.second) - minOf(ds.second, de.second)) * h
                    if (toolMode == EditorToolMode.SHAPE_CIRCLE) {
                        drawOval(selectedColor, Offset(left, top), Size(sw, sh), style = Stroke(4f))
                    } else {
                        drawRect(selectedColor, Offset(left, top), Size(sw, sh), style = Stroke(4f))
                    }
                }
            }
        }

        if (savedSignaturePath != null && File(savedSignaturePath).exists()) {
            OutlinedButton(
                onClick = {
                    onAddOverlay(
                        EditOverlay.ImageOverlay(
                            id = UUID.randomUUID().toString(),
                            pageIndex = activePage,
                            imagePath = savedSignaturePath,
                            normX = 0.56f,
                            normY = 0.72f,
                            normWidth = 0.34f,
                            normHeight = 0.14f
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Draw, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Stamp Saved Signature onto Page ${activePage + 1}")
            }
        }
    }

    pendingTextTap?.let { coords ->
        AlertDialog(
            onDismissRequest = { pendingTextTap = null },
            title = { Text("Add Text to Page ${activePage + 1}") },
            text = {
                OutlinedTextField(
                    value = textDialogInput,
                    onValueChange = { textDialogInput = it },
                    label = { Text("Enter text (English or Arabic)") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (textDialogInput.isNotBlank()) {
                            onAddOverlay(
                                EditOverlay.TextOverlay(
                                    id = UUID.randomUUID().toString(),
                                    pageIndex = activePage,
                                    text = textDialogInput,
                                    normX = coords.first,
                                    normY = coords.second,
                                    fontSizeSp = fontSizeSp,
                                    colorArgb = selectedColor.toArgb()
                                )
                            )
                        }
                        pendingTextTap = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Crimson600)
                ) {
                    Text("Place Text")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingTextTap = null }) { Text("Cancel") }
            }
        )
    }

    if (showSignaturePad) {
        SignaturePadDialog(
            onDismiss = { showSignaturePad = false },
            onSaveSignature = { bmp ->
                onSaveSignature(bmp, true)
                showSignaturePad = false
            }
        )
    }
}

@Composable
private fun EditorModeChip(
    mode: EditorToolMode,
    current: EditorToolMode,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: (EditorToolMode) -> Unit
) {
    FilterChip(
        selected = mode == current,
        onClick = { onClick(mode) },
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp)) },
        modifier = Modifier.testTag("editor_mode_${mode.name}")
    )
}

@Composable
fun SignaturePadDialog(
    onDismiss: () -> Unit,
    onSaveSignature: (Bitmap) -> Unit
) {
    val strokes = remember { mutableStateListOf<List<Pair<Float, Float>>>() }
    val currentStroke = remember { mutableStateListOf<Pair<Float, Float>>() }
    var padSize by remember { mutableStateOf(IntSize(600, 260)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Draw Signature") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sign smoothly inside the box below:", style = MaterialTheme.typography.bodySmall)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(1.dp, Color.Gray, RoundedCornerShape(12.dp))
                        .onSizeChanged { padSize = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    val w = padSize.width.coerceAtLeast(1).toFloat()
                                    val h = padSize.height.coerceAtLeast(1).toFloat()
                                    currentStroke.clear()
                                    currentStroke.add((start.x / w) to (start.y / h))
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val w = padSize.width.coerceAtLeast(1).toFloat()
                                    val h = padSize.height.coerceAtLeast(1).toFloat()
                                    currentStroke.add(
                                        (change.position.x / w).coerceIn(0f, 1f) to
                                            (change.position.y / h).coerceIn(0f, 1f)
                                    )
                                },
                                onDragEnd = {
                                    if (currentStroke.size >= 2) {
                                        strokes.add(currentStroke.toList())
                                    }
                                    currentStroke.clear()
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val all = strokes + listOf(currentStroke.toList())
                        all.forEach { pts ->
                            if (pts.size >= 2) {
                                val path = Path()
                                pts.forEachIndexed { i, pt ->
                                    if (i == 0) path.moveTo(pt.first * size.width, pt.second * size.height)
                                    else path.lineTo(pt.first * size.width, pt.second * size.height)
                                }
                                drawPath(
                                    path = path,
                                    color = Color(0xFF0F172A),
                                    style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = {
                    strokes.clear()
                    currentStroke.clear()
                }) {
                    Text("Clear Signature")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val bmp = Bitmap.createBitmap(640, 260, Bitmap.Config.ARGB_8888)
                    val canvas = AndroidCanvas(bmp)
                    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.parseColor("#0F172A")
                        style = AndroidPaint.Style.STROKE
                        strokeWidth = 8f
                        strokeCap = AndroidPaint.Cap.ROUND
                        strokeJoin = AndroidPaint.Join.ROUND
                    }
                    strokes.forEach { pts ->
                        val p = AndroidPath()
                        pts.forEachIndexed { idx, pt ->
                            if (idx == 0) p.moveTo(pt.first * 640f, pt.second * 260f)
                            else p.lineTo(pt.first * 640f, pt.second * 260f)
                        }
                        canvas.drawPath(p, paint)
                    }
                    onSaveSignature(bmp)
                },
                enabled = strokes.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Crimson600)
            ) {
                Text("Save & Stamp")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
