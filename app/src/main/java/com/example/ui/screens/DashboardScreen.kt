package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.DashboardMetrics
import com.example.data.local.ProcessedRecordEntity
import com.example.data.local.ToolUsageEntity
import com.example.engine.FileHelper
import com.example.i18n.AppStrings
import com.example.model.AppLanguage
import com.example.model.PdfToolId
import com.example.ui.theme.CatOptimizeColor
import com.example.ui.theme.Crimson600
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SuccessGreen
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    lang: AppLanguage,
    metrics: DashboardMetrics,
    favoriteToolIds: Set<PdfToolId>,
    frequentUsage: List<ToolUsageEntity>,
    recentRecords: List<ProcessedRecordEntity>,
    onOpenTool: (PdfToolId) -> Unit,
    onPreviewRecord: (ProcessedRecordEntity) -> Unit,
    onShareRecord: (ProcessedRecordEntity) -> Unit,
    onDeleteRecord: (ProcessedRecordEntity) -> Unit,
    onCleanCache: ((Long) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    var cacheStatus by remember { mutableStateOf<String?>(null) }
    val dateFmt = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.US) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 1040.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(AppStrings.dashboardTitle(lang), style = MaterialTheme.typography.headlineMedium)

                // Real Metrics Row (no fake statistics)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashStatCard(
                        title = AppStrings.totalProcessedLabel(lang),
                        value = metrics.totalProcessedCount.toString(),
                        accent = Crimson600,
                        modifier = Modifier.weight(1f)
                    )
                    DashStatCard(
                        title = AppStrings.totalSavedLabel(lang),
                        value = FileHelper.formatBytes(metrics.totalBytesSaved),
                        accent = SuccessGreen,
                        modifier = Modifier.weight(1f)
                    )
                    DashStatCard(
                        title = AppStrings.favoritesCountLabel(lang),
                        value = favoriteToolIds.size.toString(),
                        accent = CatOptimizeColor,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Local storage usage + Cache cleanup
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text(
                                    text = "On-Device Output Storage: ${FileHelper.formatBytes(metrics.localStorageUsedBytes)}",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                if (cacheStatus != null) {
                                    Text(cacheStatus!!, style = MaterialTheme.typography.bodySmall, color = SuccessGreen)
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                onCleanCache { freed ->
                                    cacheStatus = "${AppStrings.cacheCleanedMsg(lang)} (${FileHelper.formatBytes(freed)})"
                                }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("clean_cache_button")
                        ) {
                            Icon(Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(AppStrings.cleanCacheBtn(lang))
                        }
                    }
                }
            }
        }

        // Quick Actions
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 1040.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(AppStrings.quickActionsTitle(lang), style = MaterialTheme.typography.titleLarge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        PdfToolId.MERGE_PDF,
                        PdfToolId.COMPRESS_PDF,
                        PdfToolId.SPLIT_PDF,
                        PdfToolId.EDIT_PDF,
                        PdfToolId.OCR_PDF,
                        PdfToolId.PROTECT_PDF
                    ).forEach { tool ->
                        AssistChip(
                            onClick = { onOpenTool(tool) },
                            label = { Text(AppStrings.toolText(tool, lang).title) },
                            leadingIcon = { Icon(tool.icon, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
            }
        }

        // Favorite Tools
        if (favoriteToolIds.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .widthIn(max = 1040.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(AppStrings.favoritesTitle(lang), style = MaterialTheme.typography.titleLarge)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        favoriteToolIds.forEach { tool ->
                            AssistChip(
                                onClick = { onOpenTool(tool) },
                                label = { Text(AppStrings.toolText(tool, lang).title) },
                                leadingIcon = { Icon(tool.icon, contentDescription = null, tint = Crimson600, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                }
            }
        }

        // Frequently used tools
        if (frequentUsage.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .widthIn(max = 1040.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(AppStrings.frequentlyUsedTitle(lang), style = MaterialTheme.typography.titleLarge)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        frequentUsage.take(6).forEach { usage ->
                            val tool = PdfToolId.fromId(usage.toolId) ?: return@forEach
                            AssistChip(
                                onClick = { onOpenTool(tool) },
                                label = { Text("${AppStrings.toolText(tool, lang).title} (${usage.useCount})") },
                                leadingIcon = { Icon(tool.icon, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                }
            }
        }

        // Recent Activity
        item {
            Text(
                text = AppStrings.recentActivityTitle(lang),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .widthIn(max = 1040.dp)
                    .fillMaxWidth()
            )
        }

        if (recentRecords.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 1040.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(AppStrings.noRecentFilesTitle(lang), style = MaterialTheme.typography.titleMedium)
                        Text(
                            AppStrings.noRecentFilesHint(lang),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(recentRecords.size, key = { recentRecords[it].id }) { idx ->
                val rec = recentRecords[idx]
                val fileExists = File(rec.filePath).exists()
                val toolTitle = PdfToolId.fromId(rec.toolId)?.let { AppStrings.toolText(it, lang).title } ?: rec.toolId
                Card(
                    modifier = Modifier
                        .widthIn(max = 1040.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rec.fileName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "$toolTitle • ${FileHelper.formatBytes(rec.outputSizeBytes)} • ${dateFmt.format(Date(rec.timestamp))}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!fileExists) {
                                Text("File removed from local storage", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        if (fileExists && (rec.mimeType == "application/pdf" || rec.mimeType == "text/plain")) {
                            OutlinedButton(
                                onClick = { onPreviewRecord(rec) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("Open")
                            }
                        }
                        if (fileExists) {
                            IconButton(
                                onClick = { onShareRecord(rec) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = "Share")
                            }
                        }
                        IconButton(
                            onClick = { onDeleteRecord(rec) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = Crimson600)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashStatCard(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = accent,
                modifier = Modifier
                    .width(28.dp)
                    .height(4.dp)
            ) {}
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = JetBrainsMonoFamily),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}
