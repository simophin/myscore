package dev.fanchao.myscore.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalWindowInfo
import dev.fanchao.myscore.LibraryUiState
import dev.fanchao.myscore.FileTransferMode
import dev.fanchao.myscore.data.ScoreDocument
import dev.fanchao.myscore.data.LibraryEntry
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private enum class AppTab(val label: String, val symbol: String) {
    Scores("Scores", "♫"),
    Find("Find", "⌕"),
    Settings("Settings", "⚙"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyScoreApp(
    libraryUri: Uri?,
    libraryState: LibraryUiState,
    onChooseFolder: () -> Unit,
    onImportPdf: () -> Unit,
    onDownloadPdf: (String, String?, String?, String?, String?) -> Unit,
    onRefresh: () -> Unit,
    onOpenScore: (ScoreDocument) -> Unit,
    onOpenDirectory: (LibraryEntry) -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateToPath: (Int) -> Unit,
    onCopy: (LibraryEntry) -> Unit,
    onMove: (LibraryEntry) -> Unit,
    onPaste: () -> Unit,
    onClearClipboard: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onRename: (LibraryEntry, String) -> Unit,
    onDelete: (LibraryEntry) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(AppTab.Scores) }
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
                        icon = { Text(tab.symbol, style = MaterialTheme.typography.titleLarge) },
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
                                selectedTab.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
                                icon = { Text(tab.symbol, style = MaterialTheme.typography.titleLarge) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            when (selectedTab) {
                AppTab.Scores -> ScoresScreen(
                    modifier = Modifier.padding(padding),
                    libraryUri = libraryUri,
                    state = libraryState,
                    onChooseFolder = onChooseFolder,
                    onRefresh = onRefresh,
                    onOpenScore = onOpenScore,
                    onOpenDirectory = onOpenDirectory,
                    onNavigateUp = onNavigateUp,
                    onNavigateToPath = onNavigateToPath,
                    onCopy = onCopy,
                    onMove = onMove,
                    onPaste = onPaste,
                    onClearClipboard = onClearClipboard,
                    onCreateFolder = onCreateFolder,
                    onRename = onRename,
                    onDelete = onDelete,
                )
                AppTab.Find -> FindScreen(
                    modifier = Modifier.padding(padding),
                    hasLibrary = libraryUri != null,
                    state = libraryState,
                    onDownloadPdf = onDownloadPdf,
                )
                AppTab.Settings -> SettingsScreen(
                    modifier = Modifier.padding(padding),
                    libraryUri = libraryUri,
                    onChooseFolder = onChooseFolder,
                    onImportPdf = onImportPdf,
                )
            }
        }
    }
}

@Composable
private fun ScoresScreen(
    modifier: Modifier,
    libraryUri: Uri?,
    state: LibraryUiState,
    onChooseFolder: () -> Unit,
    onRefresh: () -> Unit,
    onOpenScore: (ScoreDocument) -> Unit,
    onOpenDirectory: (LibraryEntry) -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateToPath: (Int) -> Unit,
    onCopy: (LibraryEntry) -> Unit,
    onMove: (LibraryEntry) -> Unit,
    onPaste: () -> Unit,
    onClearClipboard: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onRename: (LibraryEntry, String) -> Unit,
    onDelete: (LibraryEntry) -> Unit,
) {
    when {
        libraryUri == null -> EmptyState(
            modifier = modifier,
            title = "Choose your score folder",
            body = "MyScore will show every PDF in this folder and its subfolders.",
            action = "Choose folder",
            onAction = onChooseFolder,
        )
        state.loading && !state.initialized -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        else -> FileBrowser(
            modifier = modifier,
            state = state,
            onRefresh = onRefresh,
            onOpenScore = onOpenScore,
            onOpenDirectory = onOpenDirectory,
            onNavigateUp = onNavigateUp,
            onNavigateToPath = onNavigateToPath,
            onCopy = onCopy,
            onMove = onMove,
            onPaste = onPaste,
            onClearClipboard = onClearClipboard,
            onCreateFolder = onCreateFolder,
            onRename = onRename,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun FileBrowser(
    modifier: Modifier,
    state: LibraryUiState,
    onRefresh: () -> Unit,
    onOpenScore: (ScoreDocument) -> Unit,
    onOpenDirectory: (LibraryEntry) -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateToPath: (Int) -> Unit,
    onCopy: (LibraryEntry) -> Unit,
    onMove: (LibraryEntry) -> Unit,
    onPaste: () -> Unit,
    onClearClipboard: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onRename: (LibraryEntry, String) -> Unit,
    onDelete: (LibraryEntry) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<LibraryEntry?>(null) }
    var pendingRename by remember { mutableStateOf<LibraryEntry?>(null) }
    var creatingFolder by remember { mutableStateOf(false) }
    BackHandler(enabled = state.path.size > 1, onBack = onNavigateUp)
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.path.size > 1) {
                IconButton(onClick = onNavigateUp) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
            }
            Row(
                Modifier.weight(1f).horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.path.forEachIndexed { index, folder ->
                    if (index > 0) Text("  /  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { onNavigateToPath(index) }, enabled = index != state.path.lastIndex) {
                        Text(folder.name, maxLines = 1)
                    }
                }
            }
            TextButton(onClick = { creatingFolder = true }, enabled = !state.loading) { Text("New folder") }
            IconButton(onClick = onRefresh) { Text("↻", style = MaterialTheme.typography.titleLarge) }
        }
        state.clipboard?.let { clipboard ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "${if (clipboard.mode == FileTransferMode.Copy) "Copy" else "Move"}: ${clipboard.entry.name}",
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                )
                TextButton(onClick = onClearClipboard) { Text("Cancel") }
                Button(onClick = onPaste, enabled = !state.loading) { Text("Paste here") }
            }
        }
        if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        state.message?.let { message ->
            Text(
                message,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = if (message.contains("could not", true) || message.contains("outside", true)) {
                    MaterialTheme.colorScheme.error
                } else MaterialTheme.colorScheme.primary,
            )
        }
        if (state.entries.isEmpty() && !state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("This folder is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val columnCount = when {
                    maxWidth >= 1200.dp -> 3
                    maxWidth >= 700.dp -> 2
                    else -> 1
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columnCount),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.entries, key = { it.uri }) { entry ->
                        FileRow(
                            entry = entry,
                            onOpen = {
                                if (entry.isDirectory) onOpenDirectory(entry)
                                else onOpenScore(entry.toScoreDocument())
                            },
                            onCopy = { onCopy(entry) },
                            onMove = { onMove(entry) },
                            onRename = { pendingRename = entry },
                            onDelete = { pendingDelete = entry },
                        )
                    }
                }
            }
        }
    }
    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${entry.name}?") },
            text = {
                Text(if (entry.isDirectory) "This deletes the folder and everything inside it." else "This action cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDelete(entry)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
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
    pendingRename?.let { entry ->
        NameDialog(
            title = "Rename ${entry.name}",
            initialName = entry.name,
            confirmLabel = "Rename",
            onDismiss = { pendingRename = null },
            onConfirm = { name ->
                pendingRename = null
                onRename(entry, name)
            },
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (name.isNotBlank()) onConfirm(name)
                }),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FileRow(
    entry: LibraryEntry,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (entry.isDirectory) "▰" else "♬", style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(entry.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (entry.isDirectory) "Folder" else formatFileSize(entry.sizeBytes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Copy") }, onClick = { menuExpanded = false; onCopy() })
                    DropdownMenuItem(text = { Text("Move") }, onClick = { menuExpanded = false; onMove() })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menuExpanded = false; onRename() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
    }
}

private fun LibraryEntry.toScoreDocument() = ScoreDocument(
    uri = uri,
    title = name.removeSuffix(".pdf").removeSuffix(".PDF"),
    sizeBytes = sizeBytes,
    modifiedAtMillis = modifiedAtMillis,
)

@Composable
private fun FindScreen(
    modifier: Modifier,
    hasLibrary: Boolean,
    state: LibraryUiState,
    onDownloadPdf: (String, String?, String?, String?, String?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var webView by remember { mutableStateOf<android.webkit.WebView?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    val searchUrl = {
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.toString())
        "https://imslp.org/index.php?search=$encoded&title=Special%3ASearch&go=Go"
    }
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        AnimatedVisibility(
            visible = controlsVisible,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Search IMSLP") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            if (query.isNotBlank()) webView?.loadUrl(searchUrl())
                        }),
                    )
                    Button(onClick = { webView?.loadUrl(searchUrl()) }, enabled = query.isNotBlank()) { Text("Go") }
                }
                if (state.message != null) {
                    Text(
                        state.message,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        color = if (state.message.contains("failed", true)) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                if (!hasLibrary) {
                    Text(
                        "Choose a score folder in Settings before downloading.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        ImslpWebView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            onReady = { webView = it },
            onDownload = onDownloadPdf,
            onScrollDirectionChanged = { scrollingDown -> controlsVisible = !scrollingDown },
        )
    }
}

@Composable
@SuppressLint("SetJavaScriptEnabled") // IMSLP's interactive download flow requires JavaScript.
private fun ImslpWebView(
    modifier: Modifier,
    onReady: (android.webkit.WebView) -> Unit,
    onDownload: (String, String?, String?, String?, String?) -> Unit,
    onScrollDirectionChanged: (scrollingDown: Boolean) -> Unit,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            android.webkit.WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.setSupportMultipleWindows(false)
                settings.javaScriptCanOpenWindowsAutomatically = false
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: android.webkit.WebView,
                        request: android.webkit.WebResourceRequest,
                    ): Boolean {
                        return when (request.url.scheme?.lowercase()) {
                            "https" -> false
                            "http" -> {
                                view.loadUrl(request.url.buildUpon().scheme("https").build().toString())
                                true
                            }
                            else -> {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                                true
                            }
                        }
                    }
                }
                setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                    when {
                        scrollY == 0 -> onScrollDirectionChanged(false)
                        scrollY - oldScrollY > 8 -> onScrollDirectionChanged(true)
                        oldScrollY - scrollY > 8 -> onScrollDirectionChanged(false)
                    }
                }
                setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                    val cookies = android.webkit.CookieManager.getInstance().getCookie(url)
                    onDownload(url, userAgent, contentDisposition, mimeType, cookies)
                }
                loadUrl("https://imslp.org/")
                onReady(this)
            }
        },
        onRelease = { view ->
            view.stopLoading()
            view.setDownloadListener(null)
            view.destroy()
        },
    )
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    libraryUri: Uri?,
    onChooseFolder: () -> Unit,
    onImportPdf: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Score library", style = MaterialTheme.typography.headlineSmall)
        Text(
            libraryUri?.lastPathSegment ?: "No folder selected",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Button(onClick = onChooseFolder) {
            Text(if (libraryUri == null) "Choose folder" else "Change folder")
        }
        OutlinedButton(onClick = onImportPdf, enabled = libraryUri != null) {
            Text("Import an existing PDF")
        }
        Spacer(Modifier.height(16.dp))
        Text("Storage", style = MaterialTheme.typography.titleMedium)
        Text(
            "Folder access is granted through Android's system picker. MyScore does not request access to all files on your device.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier,
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("♬", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAction) { Text(action) }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}
