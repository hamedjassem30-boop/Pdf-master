package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.i18n.AppStrings
import com.example.model.AppLanguage
import com.example.model.PdfToolId
import com.example.model.ToolCategory
import com.example.ui.components.ToolCard
import com.example.ui.theme.Crimson600

@Composable
fun ToolsDirectoryScreen(
    lang: AppLanguage,
    favoriteToolIds: Set<PdfToolId>,
    onOpenTool: (PdfToolId) -> Unit,
    onToggleFavorite: (PdfToolId) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val columns = when {
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
            item {
                Row(
                    modifier = Modifier
                        .widthIn(max = 1040.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(AppStrings.searchPlaceholder(lang)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("directory_search_input")
                    )
                    FilterChip(
                        selected = favoritesOnly,
                        onClick = { favoritesOnly = !favoritesOnly },
                        label = { Text(AppStrings.favoritesTitle(lang)) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Favorite,
                                contentDescription = null,
                                tint = if (favoritesOnly) Crimson600 else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("favorites_filter_chip")
                    )
                }
            }

            ToolCategory.entries.forEach { category ->
                val catTools = PdfToolId.entries.filter { tool ->
                    tool.category == category &&
                        (!favoritesOnly || tool in favoriteToolIds) &&
                        (query.isBlank() ||
                            AppStrings.toolText(tool, AppLanguage.EN).title.contains(query, true) ||
                            AppStrings.toolText(tool, AppLanguage.AR).title.contains(query, true) ||
                            AppStrings.toolText(tool, lang).description.contains(query, true))
                }
                if (catTools.isNotEmpty()) {
                    item(key = "header_${category.name}") {
                        Row(
                            modifier = Modifier
                                .widthIn(max = 1040.dp)
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = category.color,
                                modifier = Modifier.size(width = 6.dp, height = 22.dp)
                            ) {}
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "${AppStrings.categoryName(category, lang)} (${catTools.size})",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }

                    val rows = catTools.chunked(columns)
                    items(rows.size, key = { idx -> "${category.name}_row_$idx" }) { idx ->
                        val rowTools = rows[idx]
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
                }
            }
        }
    }
}
