package dev.fanchao.myscore.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.fanchao.myscore.BuildConfig
import dev.fanchao.myscore.LibraryUiState
import dev.fanchao.myscore.R
import dev.fanchao.myscore.data.LibraryEntry
import dev.fanchao.myscore.data.ScoreDocument

private enum class AppTab(val label: String) {
    Scores("Scores"),
    Find("Find"),
    Settings("Settings"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyScoreApp(
    libraryUri: Uri?,
    libraryState: LibraryUiState,
    paperModeEnabled: Boolean = false,
    appVersionName: String = BuildConfig.VERSION_NAME,
    onChooseFolder: () -> Unit,
    onImportPdf: () -> Unit,
    onPaperModeChanged: (Boolean) -> Unit = {},
    onDownloadPdf: (String, String?, String?, String?, String?) -> Unit,
    onOpenDownloadedScore: (ScoreDocument) -> Unit,
    onRefresh: () -> Unit,
    onOpenScore: (ScoreDocument) -> Unit,
    onOpenDirectory: (LibraryEntry) -> Unit,
    onNavigateUp: () -> Unit,
    onCopy: (LibraryEntry) -> Unit,
    onMove: (LibraryEntry) -> Unit,
    onPaste: () -> Unit,
    onClearClipboard: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onRename: (LibraryEntry, String) -> Unit,
    onDelete: (LibraryEntry) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Scores) }
    var creatingFolder by rememberSaveable { mutableStateOf(false) }
    var scoreActionsExpanded by remember { mutableStateOf(false) }
    var scoreSortOrder by rememberSaveable { mutableStateOf(ScoreSortOrder.NameAscending) }
    var findSearchVisible by rememberSaveable { mutableStateOf(false) }
    var findWebViewState by remember { mutableStateOf(FindWebViewState()) }
    var findInitialPageLoaded by rememberSaveable { mutableStateOf(false) }
    val findWebViewHolder = remember { ImslpWebViewHolder() }
    DisposableEffect(findWebViewHolder) {
        onDispose(findWebViewHolder::destroy)
    }
    BackHandler(enabled = selectedTab != AppTab.Scores) {
        selectedTab = AppTab.Scores
    }
    val containerSize = LocalWindowInfo.current.containerSize
    val useRail = shouldUseNavigationRail(containerSize.width, containerSize.height)
    Row(Modifier.fillMaxSize()) {
        if (useRail) {
            NavigationRail(modifier = Modifier.fillMaxHeight()) {
                Text("MyScore", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.SemiBold)
                AppTab.entries.forEach { tab ->
                    NavigationRailItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { AppTabIcon(tab) },
                        label = { Text(tab.label) },
                    )
                }
            }
        }
        Scaffold(
            modifier = Modifier.weight(1f),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("MyScore", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (selectedTab == AppTab.Scores) {
                                    libraryState.path.lastOrNull()?.name ?: selectedTab.label
                                } else {
                                    selectedTab.label
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = {
                        if (selectedTab == AppTab.Scores && libraryState.path.size > 1) {
                            IconButton(onClick = onNavigateUp) {
                                AppBarIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back to parent folder")
                            }
                        }
                    },
                    actions = {
                        when (selectedTab) {
                            AppTab.Scores -> if (libraryUri != null) {
                                if (libraryState.loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .padding(horizontal = 12.dp)
                                            .size(24.dp)
                                            .semantics { contentDescription = "Loading scores" },
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    IconButton(onClick = onRefresh) {
                                        AppBarIcon(Icons.Filled.Refresh, "Refresh scores")
                                    }
                                }
                                Box {
                                    IconButton(onClick = { scoreActionsExpanded = true }) {
                                        AppBarIcon(Icons.Filled.MoreVert, "Score actions")
                                    }
                                    DropdownMenu(
                                        expanded = scoreActionsExpanded,
                                        onDismissRequest = { scoreActionsExpanded = false },
                                    ) {
                                        Text(
                                            "Sort by",
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        ScoreSortOrder.entries.forEach { sortOrder ->
                                            DropdownMenuItem(
                                                leadingIcon = {
                                                    if (sortOrder == scoreSortOrder) {
                                                        Icon(
                                                            imageVector = Icons.Filled.Check,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(18.dp),
                                                        )
                                                    } else {
                                                        Spacer(Modifier.size(18.dp))
                                                    }
                                                },
                                                text = {
                                                    Text(sortOrder.label)
                                                },
                                                onClick = {
                                                    scoreSortOrder = sortOrder
                                                    scoreActionsExpanded = false
                                                },
                                            )
                                        }
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Filled.Add,
                                                    contentDescription = null,
                                                )
                                            },
                                            text = { Text("New folder") },
                                            enabled = !libraryState.loading,
                                            onClick = {
                                                scoreActionsExpanded = false
                                                creatingFolder = true
                                            },
                                        )
                                    }
                                }
                            }
                            AppTab.Find -> {
                                IconButton(
                                    onClick = findWebViewHolder::goBack,
                                    enabled = findWebViewState.canGoBack,
                                ) {
                                    AppBarIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back in browser")
                                }
                                IconButton(
                                    onClick = findWebViewHolder::goForward,
                                    enabled = findWebViewState.canGoForward,
                                ) {
                                    AppBarIcon(Icons.AutoMirrored.Filled.ArrowForward, "Forward in browser")
                                }
                                if (findWebViewState.isLoading) {
                                    IconButton(onClick = {
                                        findWebViewHolder.stopLoading()
                                        findWebViewState = findWebViewState.copy(isLoading = false)
                                    }) {
                                        AppBarIcon(Icons.Filled.Close, "Stop loading page")
                                    }
                                } else {
                                    IconButton(onClick = findWebViewHolder::reload) {
                                        AppBarIcon(Icons.Filled.Refresh, "Refresh page")
                                    }
                                }
                                IconButton(onClick = { findSearchVisible = true }) {
                                    AppBarIcon(Icons.Filled.Search, "Search IMSLP")
                                }
                            }
                            AppTab.Settings -> Unit
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        AppTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                icon = { AppTabIcon(tab) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    if (targetState.ordinal > initialState.ordinal) {
                        slideInHorizontally(
                            animationSpec = spring(),
                            initialOffsetX = { fullWidth -> fullWidth },
                        ) togetherWith slideOutHorizontally(
                            animationSpec = spring(),
                            targetOffsetX = { fullWidth -> -fullWidth / 3 },
                        )
                    } else {
                        slideInHorizontally(
                            animationSpec = spring(),
                            initialOffsetX = { fullWidth -> -fullWidth },
                        ) togetherWith slideOutHorizontally(
                            animationSpec = spring(),
                            targetOffsetX = { fullWidth -> fullWidth / 3 },
                        )
                    }.using(SizeTransform(clip = false))
                },
                label = "top-level-tab-transition",
            ) { tab ->
                when (tab) {
                    AppTab.Scores -> ScoresScreen(
                        modifier = Modifier.padding(padding),
                        libraryUri = libraryUri,
                        state = libraryState,
                        sortOrder = scoreSortOrder,
                        onChooseFolder = onChooseFolder,
                        onOpenScore = onOpenScore,
                        onOpenDirectory = onOpenDirectory,
                        onNavigateUp = onNavigateUp,
                        onCopy = onCopy,
                        onMove = onMove,
                        onPaste = onPaste,
                        onClearClipboard = onClearClipboard,
                        onRename = onRename,
                        onDelete = onDelete,
                    )
                    AppTab.Find -> FindScreen(
                        modifier = Modifier.padding(padding),
                        webViewHolder = findWebViewHolder,
                        hasLibrary = libraryUri != null,
                        state = libraryState,
                        onDownloadPdf = onDownloadPdf,
                        onOpenDownloadedScore = onOpenDownloadedScore,
                        searchVisible = findSearchVisible,
                        onDismissSearch = { findSearchVisible = false },
                        onWebViewStateChanged = { findWebViewState = it },
                        initialPageLoaded = findInitialPageLoaded,
                        onInitialPageLoaded = { findInitialPageLoaded = true },
                    )
                    AppTab.Settings -> SettingsScreen(
                        modifier = Modifier.padding(padding),
                        libraryUri = libraryUri,
                        paperModeEnabled = paperModeEnabled,
                        appVersionName = appVersionName,
                        onChooseFolder = onChooseFolder,
                        onImportPdf = onImportPdf,
                        onPaperModeChanged = onPaperModeChanged,
                    )
                }
            }
        }
    }
    if (creatingFolder) {
        NameDialog(
            title = "Create folder",
            initialName = "",
            confirmLabel = "Create",
            onDismiss = { creatingFolder = false },
            onConfirm = { name ->
                creatingFolder = false
                onCreateFolder(name)
            },
        )
    }
}

@Composable
private fun AppTabIcon(tab: AppTab) {
    when (tab) {
        AppTab.Scores -> Icon(
            painter = painterResource(R.drawable.ic_library_music_24),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        AppTab.Find -> Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        AppTab.Settings -> Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun AppBarIcon(imageVector: ImageVector, description: String) {
    Icon(
        imageVector = imageVector,
        contentDescription = description,
        modifier = Modifier.size(24.dp),
    )
}
