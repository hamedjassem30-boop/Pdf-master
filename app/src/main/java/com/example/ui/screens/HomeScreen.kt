package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ProcessedRecordEntity
import com.example.data.local.ToolUsageEntity
import com.example.engine.FileHelper
import com.example.i18n.AppStrings
import com.example.model.AppLanguage
import com.example.model.PdfToolId
import com.example.model.PickedFile
import com.example.model.ToolCategory
import com.example.ui.components.PdfDropZoneCard
import com.example.ui.components.ToolCard
import com.example.ui.theme.Crimson600
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.Navy900
import com.example.ui.theme.Navy950
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    lang: AppLanguage,
    searchQuery: String,
    selectedCategory: ToolCategory?,
    favoriteToolIds: Set<PdfToolId>,
    frequentUsage: List<ToolUsageEntity>,
    recentRecords: List<ProcessedRecordEntity>,
    stagedFiles: List<PickedFile>,
    bannerMessage: String?,
    onSearchChange: (String) -> Unit,
    onSelectCategory: (ToolCategory?) -> Unit,
    onStageFiles: (List<android.net.Uri>) -> Unit,
    onClearStagedFiles: () -> Unit,
    onOpenTool: (PdfToolId) -> Unit,
    onToggleFavorite: (PdfToolId) -> Unit,
    onPreviewRecord: (ProcessedRecordEntity) -> Unit,
    onDismissBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) onStageFiles(uris)
    }

    val filteredTools = remember(searchQuery, selectedCategory, lang) {
        PdfToolId.entries.filter { tool ->
            val matchesCat = selectedCategory == null || tool.category == selectedCategory
            if (!matchesCat) return@filter false
            if (searchQuery.isBlank()) true else {
                val enText = AppStrings.toolText(tool, AppLanguage.EN)
                val arText = AppStrings.toolText(tool, AppLanguage.AR)
                enText.title.contains(searchQuery, ignoreCase = true) ||
                    enText.description.contains(searchQuery, ignoreCase = true) ||
                    arText.title.contains(searchQuery, ignoreCase = true) ||
                    arText.description.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val frequentOrPopularTools = remember(frequentUsage) {
        val used = frequentUsage.mapNotNull { PdfToolId.fromId(it.toolId) }.take(6)
        used.ifEmpty {
            listOf(
                PdfToolId.MERGE_PDF,
                PdfToolId.COMPRESS_PDF,
                PdfToolId.SPLIT_PDF,
                PdfToolId.EDIT_PDF,
                PdfToolId.PDF_TO_WORD,
                PdfToolId.OCR_PDF
            )
        }
    }

    val suggestedToolsForStaged = remember(stagedFiles) {
        if (stagedFiles.isEmpty()) emptyList()
        else {
            val hasPdf = stagedFiles.any { it.name.endsWith(".pdf", true) || it.mimeType.contains("pdf", true) }
            val hasImg = stagedFiles.any { it.mimeType.startsWith("image/") }
            when {
                hasPdf && stagedFiles.size > 1 -> listOf(PdfToolId.MERGE_PDF, PdfToolId.COMPARE_PDF, PdfToolId.COMPRESS_PDF)
                hasPdf -> listOf(
                    PdfToolId.COMPRESS_PDF, PdfToolId.SPLIT_PDF, PdfToolId.VISUAL_ORGANIZER,
                    PdfToolId.EDIT_PDF, PdfToolId.PDF_TO_JPG, PdfToolId.OCR_PDF, PdfToolId.PROTECT_PDF
                )
                hasImg -> listOf(PdfToolId.JPG_TO_PDF, PdfToolId.PNG_TO_PDF, PdfToolId.WEBP_TO_PDF)
                else -> listOf(PdfToolId.WORD_TO_PDF, PdfToolId.EXCEL_TO_PDF, PdfToolId.PPT_TO_PDF, PdfToolId.TXT_TO_PDF, PdfToolId.HTML_TO_PDF)
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val columns = when {
            maxWidth >= 900.dp -> 4
            maxWidth >= 600.dp -> 3
            else -> 2
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Hero Section
            item {
                Card(
                    modifier = Modifier
                        .widthIn(max = 1040.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Navy900)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_hero_pdf_suite_1795276636420),
                            contentDescription = "PDF Master Pro workspace illustration",
                            contentScale = ContentScale.Crop,
                            alpha = 0.36f,
                            modifier = Modifier.matchParentSize()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Navy950.copy(alpha = 0.92f), Navy900.copy(alpha = 0.65f))
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier.padding(22.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Crimson600.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, Crimson600.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Rounded.Shield, contentDescription = null, tint = Color(0xFFFDA4AF), modifier = Modifier.size(14.dp))
                                    Text(
                                        text = AppStrings.heroBadge(lang),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFFFE4E6)
                                    )
                                }
                            }
                            Text(
                                text = AppStrings.heroHeadline(lang),
                                style = MaterialTheme.typography.headlineLarge,
                                color = Color.White
                            )
                            Text(
                                text = AppStrings.heroSubtitle(lang),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            }

            // 2. Drag & Drop Upload Area
            item {
                Column(
                    modifier = Modifier
                        .widthIn(max = 1040.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PdfDropZoneCard(
                        title = AppStrings.uploadDropTitle(lang),
                        subtitle = AppStrings.uploadDropSubtitle(lang),
                        buttonLabel = AppStrings.selectFilesBtn(lang),
                        onPickClick = { filePicker.launch(arrayOf("*/*")) },
                        onFilesDropped = onStageFiles,
                        testTag = "home_upload_dropzone"
                    )

                    if (!bannerMessage.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(bannerMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                IconButton(onClick = onDismissBanner) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Dismiss")
                                }
                            }
                        }
                    }

                    if (stagedFiles.isNotEmpty()) {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, Crimson600.copy(alpha = 0.4f))
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
                                    Text(
                                        text = "${AppStrings.quickLaunchTitle(lang)} (${stagedFiles.size})",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    TextButton(onClick = onClearStagedFiles) {
                                        Text(AppStrings.clearAllBtn(lang))
                                    }
                                }
                                stagedFiles.forEach { sf ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, tint = Crimson600, modifier = Modifier.size(18.dp))
                                        Text(
                                            text = "${sf.name} (${FileHelper.formatBytes(sf.sizeBytes)})",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    suggestedToolsForStaged.forEach { t ->
                                        AssistChip(
                                            onClick = { onOpenTool(t) },
                                            label = { Text(AppStrings.toolText(t, lang).title) },
                                            leadingIcon = { Icon(t.icon, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text(AppStrings.searchPlaceholder(lang)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .widthIn(max = 1040.dp)
                        .fillMaxWidth()
                        .testTag("home_search_input")
                )
            }

            // 4. Frequently Used / Popular Tools
            if (searchQuery.isBlank() && selectedCategory == null) {
                item {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 1040.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Crimson600, modifier = Modifier.size(20.dp))
                            Text(
                                text = if (frequentUsage.isNotEmpty()) AppStrings.frequentlyUsedTitle(lang)
                                else AppStrings.popularToolsTitle(lang),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        val chunkedPopular = frequentOrPopularTools.chunked(columns)
                        chunkedPopular.forEach { rowTools ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowTools.forEach { tool ->
                                    ToolCard(
                                        tool = tool,
                                        lang = lang,
                                        isFavorite = tool in favoriteToolIds,
                                        onClick = { onOpenTool(tool) },
                                        onToggleFavorite = { onToggleFavorite(tool) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(columns - rowTools.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // 5. Categories Filter + Grid
            item {
                Column(
                    modifier = Modifier
                        .widthIn(max = 1040.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = AppStrings.categoriesTitle(lang),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { onSelectCategory(null) },
                            label = { Text("${AppStrings.allToolsChip(lang)} (${PdfToolId.entries.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Crimson600,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("cat_chip_all")
                        )
                        ToolCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { onSelectCategory(if (selectedCategory == cat) null else cat) },
                                label = { Text(AppStrings.categoryName(cat, lang)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = cat.color,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.testTag("cat_chip_${cat.name}")
                            )
                        }
                    }
                }
            }

            val rows = filteredTools.chunked(columns)
            items(rows.size) { rowIdx ->
                val rowTools = rows[rowIdx]
                Row(
                    modifier = Modifier
                        .widthIn(max = 1040.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowTools.forEach { tool ->
                        ToolCard(
                            tool = tool,
                            lang = lang,
                            isFavorite = tool in favoriteToolIds,
                            onClick = { onOpenTool(tool) },
                            onToggleFavorite = { onToggleFavorite(tool) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(columns - rowTools.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // 6. Recent Files Section (only real files)
            if (recentRecords.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 1040.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = AppStrings.recentFilesTitle(lang),
                            style = MaterialTheme.typography.titleLarge
                        )
                        recentRecords.take(5).forEach { rec ->
                            val exists = File(rec.filePath).exists()
                            if (exists) {
                                RecentFileRow(
                                    record = rec,
                                    lang = lang,
                                    onClick = { onPreviewRecord(rec) }
                                )
                            }
                        }
                    }
                }
            }

            // 7. Footer
            item {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 1040.dp)
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 12.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = AppStrings.footerTitle(lang),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = AppStrings.footerDesc(lang),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecentFileRow(
    record: ProcessedRecordEntity,
    lang: AppLanguage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFmt = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.US) }
    val toolTitle = PdfToolId.fromId(record.toolId)?.let { AppStrings.toolText(it, lang).title } ?: record.toolId
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Rounded.Description, contentDescription = null, tint = Crimson600, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(record.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "$toolTitle • ${FileHelper.formatBytes(record.outputSizeBytes)} • ${dateFmt.format(Date(record.timestamp))}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
