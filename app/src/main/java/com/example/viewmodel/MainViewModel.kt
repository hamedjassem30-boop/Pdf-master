package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PdfMasterApp
import com.example.data.DashboardMetrics
import com.example.data.local.ProcessedRecordEntity
import com.example.data.local.ToolUsageEntity
import com.example.engine.FileHelper
import com.example.model.AppLanguage
import com.example.model.PdfToolId
import com.example.model.PickedFile
import com.example.model.ThemeMode
import com.example.model.ToolCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PdfMasterApp
    private val prefs = app.preferencesRepository
    private val repo = app.pdfMasterRepository

    val language: StateFlow<AppLanguage> = prefs.languageFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppLanguage.EN)

    val themeMode: StateFlow<ThemeMode> = prefs.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val favoriteToolIds: StateFlow<Set<PdfToolId>> = repo.favoriteToolIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val frequentUsage: StateFlow<List<ToolUsageEntity>> = repo.frequentToolIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentRecords: StateFlow<List<ProcessedRecordEntity>> = repo.processedRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dashboardMetrics: StateFlow<DashboardMetrics> = repo.dashboardMetrics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardMetrics())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<ToolCategory?>(null)
    val selectedCategory: StateFlow<ToolCategory?> = _selectedCategory.asStateFlow()

    private val _stagedHomeFiles = MutableStateFlow<List<PickedFile>>(emptyList())
    val stagedHomeFiles: StateFlow<List<PickedFile>> = _stagedHomeFiles.asStateFlow()

    private val _homeBannerMessage = MutableStateFlow<String?>(null)
    val homeBannerMessage: StateFlow<String?> = _homeBannerMessage.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: ToolCategory?) {
        _selectedCategory.value = category
    }

    fun toggleLanguage() {
        viewModelScope.launch {
            val next = if (language.value == AppLanguage.EN) AppLanguage.AR else AppLanguage.EN
            prefs.setLanguage(next)
        }
    }

    fun cycleThemeMode() {
        viewModelScope.launch {
            val next = when (themeMode.value) {
                ThemeMode.SYSTEM -> ThemeMode.LIGHT
                ThemeMode.LIGHT -> ThemeMode.DARK
                ThemeMode.DARK -> ThemeMode.SYSTEM
            }
            prefs.setThemeMode(next)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun toggleFavorite(toolId: PdfToolId) {
        viewModelScope.launch { repo.toggleFavorite(toolId) }
    }

    fun stageFilesFromHome(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val imported = mutableListOf<PickedFile>()
            val existingKeys = _stagedHomeFiles.value.map { "${it.name}_${it.sizeBytes}" }.toMutableSet()
            var lastError: String? = null
            for (uri in uris) {
                FileHelper.importFromUri(app, uri, requirePdf = false).fold(
                    onSuccess = { picked ->
                        val key = "${picked.name}_${picked.sizeBytes}"
                        if (existingKeys.add(key)) {
                            imported.add(picked)
                        }
                    },
                    onFailure = { e -> lastError = e.message }
                )
            }
            if (imported.isNotEmpty()) {
                _stagedHomeFiles.value = _stagedHomeFiles.value + imported
            }
            if (lastError != null && imported.isEmpty()) {
                _homeBannerMessage.value = lastError
            }
        }
    }

    fun clearStagedHomeFiles() {
        _stagedHomeFiles.value = emptyList()
    }

    fun consumeStagedHomeFilesForTool(toolId: PdfToolId): List<PickedFile> {
        val current = _stagedHomeFiles.value
        if (current.isEmpty()) return emptyList()
        val matching = current.filter { file ->
            toolId.acceptedMimeTypes.any { mime ->
                file.mimeType.equals(mime, ignoreCase = true) ||
                    (mime == "application/pdf" && file.name.endsWith(".pdf", ignoreCase = true)) ||
                    (mime.startsWith("image/") && file.mimeType.startsWith("image/"))
            }
        }
        return if (toolId.allowMultipleFiles) matching else matching.take(1)
    }

    fun clearBannerMessage() {
        _homeBannerMessage.value = null
    }

    fun deleteRecord(record: ProcessedRecordEntity) {
        viewModelScope.launch { repo.deleteRecord(record) }
    }

    fun cleanTemporaryCache(onFreed: (Long) -> Unit) {
        viewModelScope.launch {
            val freed = repo.cleanupTemporaryFiles()
            onFreed(freed)
        }
    }
}
