package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.engine.FileHelper
import com.example.i18n.AppStrings
import com.example.model.AppLanguage
import com.example.model.PdfToolId
import com.example.model.ThemeMode
import com.example.ui.navigation.CapabilitiesRoute
import com.example.ui.navigation.DashboardRoute
import com.example.ui.navigation.HomeRoute
import com.example.ui.navigation.ToolsRoute
import com.example.ui.navigation.ViewerRoute
import com.example.ui.navigation.WorkspaceRoute
import com.example.ui.screens.CapabilitiesScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PdfViewerScreen
import com.example.ui.screens.ToolsDirectoryScreen
import com.example.ui.screens.WorkspaceScreen
import com.example.ui.theme.Crimson600
import com.example.ui.theme.PdfMasterTheme
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.WorkspaceViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val lang by mainViewModel.language.collectAsStateWithLifecycle()
            val themeMode by mainViewModel.themeMode.collectAsStateWithLifecycle()

            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            PdfMasterTheme(
                darkTheme = isDark,
                isArabic = lang == AppLanguage.AR
            ) {
                PdfMasterRoot(mainViewModel = mainViewModel, lang = lang, themeMode = themeMode)
            }
        }
    }
}

private data class TopLevelNavItem(
    val label: String,
    val icon: ImageVector,
    val route: Any,
    val testTag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfMasterRoot(
    mainViewModel: MainViewModel,
    lang: AppLanguage,
    themeMode: ThemeMode
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination

    val searchQuery by mainViewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by mainViewModel.selectedCategory.collectAsStateWithLifecycle()
    val favoriteToolIds by mainViewModel.favoriteToolIds.collectAsStateWithLifecycle()
    val frequentUsage by mainViewModel.frequentUsage.collectAsStateWithLifecycle()
    val recentRecords by mainViewModel.recentRecords.collectAsStateWithLifecycle()
    val metrics by mainViewModel.dashboardMetrics.collectAsStateWithLifecycle()
    val stagedFiles by mainViewModel.stagedHomeFiles.collectAsStateWithLifecycle()
    val bannerMsg by mainViewModel.homeBannerMessage.collectAsStateWithLifecycle()

    val isTopLevel = currentDest?.let { dest ->
        dest.hasRoute<HomeRoute>() ||
            dest.hasRoute<ToolsRoute>() ||
            dest.hasRoute<DashboardRoute>() ||
            dest.hasRoute<CapabilitiesRoute>()
    } ?: true

    val navItems = listOf(
        TopLevelNavItem(AppStrings.navHome(lang), Icons.Rounded.Home, HomeRoute, "nav_home"),
        TopLevelNavItem(AppStrings.navTools(lang), Icons.Rounded.Apps, ToolsRoute, "nav_tools"),
        TopLevelNavItem(AppStrings.navDashboard(lang), Icons.Rounded.Dashboard, DashboardRoute, "nav_dashboard"),
        TopLevelNavItem(AppStrings.navEngine(lang), Icons.Rounded.Info, CapabilitiesRoute, "nav_engine")
    )

    val openTool: (PdfToolId) -> Unit = { tool ->
        navController.navigate(WorkspaceRoute(tool.name))
    }

    val openViewer: (String, String, String) -> Unit = { path, title, mime ->
        navController.navigate(ViewerRoute(filePath = path, title = title, mimeType = mime))
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                if (isTopLevel) {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Crimson600,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Rounded.PictureAsPdf,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = AppStrings.appTitle(lang),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        actions = {
                            TextButton(
                                onClick = { mainViewModel.toggleLanguage() },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("language_toggle_button")
                            ) {
                                Icon(Icons.Rounded.Translate, contentDescription = "Switch language", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (lang == AppLanguage.EN) "AR" else "EN", fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { mainViewModel.cycleThemeMode() },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("theme_toggle_button")
                            ) {
                                Icon(
                                    imageVector = when (themeMode) {
                                        ThemeMode.DARK -> Icons.Rounded.DarkMode
                                        ThemeMode.LIGHT -> Icons.Rounded.LightMode
                                        ThemeMode.SYSTEM -> Icons.Rounded.LightMode
                                    },
                                    contentDescription = "Toggle theme mode"
                                )
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (isTopLevel && !useRail) {
                    NavigationBar {
                        navItems.forEach { item ->
                            val selected = when (item.route) {
                                is HomeRoute -> currentDest?.hasRoute<HomeRoute>() == true
                                is ToolsRoute -> currentDest?.hasRoute<ToolsRoute>() == true
                                is DashboardRoute -> currentDest?.hasRoute<DashboardRoute>() == true
                                is CapabilitiesRoute -> currentDest?.hasRoute<CapabilitiesRoute>() == true
                                else -> false
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(HomeRoute) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label, maxLines = 1) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTopLevel && useRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        navItems.forEach { item ->
                            val selected = when (item.route) {
                                is HomeRoute -> currentDest?.hasRoute<HomeRoute>() == true
                                is ToolsRoute -> currentDest?.hasRoute<ToolsRoute>() == true
                                is DashboardRoute -> currentDest?.hasRoute<DashboardRoute>() == true
                                is CapabilitiesRoute -> currentDest?.hasRoute<CapabilitiesRoute>() == true
                                else -> false
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(HomeRoute) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label, maxLines = 1) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = HomeRoute,
                    modifier = Modifier.weight(1f)
                ) {
                    composable<HomeRoute> {
                        HomeScreen(
                            lang = lang,
                            searchQuery = searchQuery,
                            selectedCategory = selectedCategory,
                            favoriteToolIds = favoriteToolIds,
                            frequentUsage = frequentUsage,
                            recentRecords = recentRecords,
                            stagedFiles = stagedFiles,
                            bannerMessage = bannerMsg,
                            onSearchChange = { mainViewModel.setSearchQuery(it) },
                            onSelectCategory = { mainViewModel.selectCategory(it) },
                            onStageFiles = { mainViewModel.stageFilesFromHome(it) },
                            onClearStagedFiles = { mainViewModel.clearStagedHomeFiles() },
                            onOpenTool = openTool,
                            onToggleFavorite = { mainViewModel.toggleFavorite(it) },
                            onPreviewRecord = { rec -> openViewer(rec.filePath, rec.fileName, rec.mimeType) },
                            onDismissBanner = { mainViewModel.clearBannerMessage() }
                        )
                    }

                    composable<ToolsRoute> {
                        ToolsDirectoryScreen(
                            lang = lang,
                            favoriteToolIds = favoriteToolIds,
                            onOpenTool = openTool,
                            onToggleFavorite = { mainViewModel.toggleFavorite(it) }
                        )
                    }

                    composable<DashboardRoute> {
                        DashboardScreen(
                            lang = lang,
                            metrics = metrics,
                            favoriteToolIds = favoriteToolIds,
                            frequentUsage = frequentUsage,
                            recentRecords = recentRecords,
                            onOpenTool = openTool,
                            onPreviewRecord = { rec -> openViewer(rec.filePath, rec.fileName, rec.mimeType) },
                            onShareRecord = { rec ->
                                val shareIntent = FileHelper.buildShareIntent(context, rec.filePath, rec.mimeType)
                                context.startActivity(Intent.createChooser(shareIntent, "Share Document"))
                            },
                            onDeleteRecord = { mainViewModel.deleteRecord(it) },
                            onCleanCache = { cb -> mainViewModel.cleanTemporaryCache(cb) }
                        )
                    }

                    composable<CapabilitiesRoute> {
                        CapabilitiesScreen(lang = lang)
                    }

                    composable<WorkspaceRoute> { entry ->
                        val route = entry.toRoute<WorkspaceRoute>()
                        val toolId = PdfToolId.fromId(route.toolId) ?: PdfToolId.MERGE_PDF
                        val workspaceViewModel: WorkspaceViewModel = viewModel()
                        val preloaded = mainViewModel.consumeStagedHomeFilesForTool(toolId)

                        WorkspaceScreen(
                            toolId = toolId,
                            preloadedFiles = preloaded,
                            lang = lang,
                            isFavorite = toolId in favoriteToolIds,
                            viewModel = workspaceViewModel,
                            onToggleFavorite = { mainViewModel.toggleFavorite(toolId) },
                            onBack = { navController.popBackStack() },
                            onPreviewFile = openViewer
                        )
                    }

                    composable<ViewerRoute> { entry ->
                        val route = entry.toRoute<ViewerRoute>()
                        PdfViewerScreen(
                            filePath = route.filePath,
                            title = route.title,
                            mimeType = route.mimeType,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
