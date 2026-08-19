package dev.fanchao.myscore.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.fanchao.myscore.LibraryUiState
import dev.fanchao.myscore.FileTransferMode
import dev.fanchao.myscore.data.ScoreDocument
import dev.fanchao.myscore.data.LibraryEntry
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    var findSearchVisible by rememberSaveable { mutableStateOf(false) }
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
                                if (selectedTab == AppTab.Scores) {
                                    libraryState.path.lastOrNull()?.name ?: selectedTab.label
                                } else {
                                    selectedTab.label
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        if (selectedTab == AppTab.Scores && libraryState.path.size > 1) {
                            IconButton(onClick = onNavigateUp) {
                                Text(
                                    "‹",
                                    modifier = Modifier.semantics { contentDescription = "Back to parent folder" },
                                    style = MaterialTheme.typography.headlineMedium,
                                )
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
                                        Text(
                                            "↻",
                                            modifier = Modifier.semantics { contentDescription = "Refresh scores" },
                                            style = MaterialTheme.typography.titleLarge,
                                        )
                                    }
                                }
                                Box {
                                    IconButton(onClick = { scoreActionsExpanded = true }) {
                                        Text(
                                            "⋮",
                                            modifier = Modifier.semantics { contentDescription = "Score actions" },
                                            style = MaterialTheme.typography.titleLarge,
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = scoreActionsExpanded,
                                        onDismissRequest = { scoreActionsExpanded = false },
                                    ) {
                                        DropdownMenuItem(
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
                            AppTab.Find -> IconButton(onClick = { findSearchVisible = true }) {
                                Text(
                                    "⌕",
                                    modifier = Modifier.semantics { contentDescription = "Search IMSLP" },
                                    style = MaterialTheme.typography.titleLarge,
                                )
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
                    hasLibrary = libraryUri != null,
                    state = libraryState,
                    onDownloadPdf = onDownloadPdf,
                    onOpenDownloadedScore = onOpenDownloadedScore,
                    searchVisible = findSearchVisible,
                    onDismissSearch = { findSearchVisible = false },
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
private fun ScoresScreen(
    modifier: Modifier,
    libraryUri: Uri?,
    state: LibraryUiState,
    onChooseFolder: () -> Unit,
    onOpenScore: (ScoreDocument) -> Unit,
    onOpenDirectory: (LibraryEntry) -> Unit,
    onNavigateUp: () -> Unit,
    onCopy: (LibraryEntry) -> Unit,
    onMove: (LibraryEntry) -> Unit,
    onPaste: () -> Unit,
    onClearClipboard: () -> Unit,
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
        else -> FileBrowser(
            modifier = modifier,
            state = state,
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
    }
}

@Composable
private fun FileBrowser(
    modifier: Modifier,
    state: LibraryUiState,
    onOpenScore: (ScoreDocument) -> Unit,
    onOpenDirectory: (LibraryEntry) -> Unit,
    onNavigateUp: () -> Unit,
    onCopy: (LibraryEntry) -> Unit,
    onMove: (LibraryEntry) -> Unit,
    onPaste: () -> Unit,
    onClearClipboard: () -> Unit,
    onRename: (LibraryEntry, String) -> Unit,
    onDelete: (LibraryEntry) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<LibraryEntry?>(null) }
    var pendingRename by remember { mutableStateOf<LibraryEntry?>(null) }
    var pendingDetails by remember { mutableStateOf<LibraryEntry?>(null) }
    BackHandler(enabled = state.path.size > 1, onBack = onNavigateUp)
    Column(modifier.fillMaxSize()) {
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
                            onShowDetails = { pendingDetails = entry },
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
    pendingDetails?.let { entry ->
        FileDetailsDialog(entry = entry, onDismiss = { pendingDetails = null })
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
    onShowDetails: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onOpen,
                onLongClickLabel = "Show details for ${entry.name}",
                onLongClick = onShowDetails,
            ),
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
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.semantics { contentDescription = "Actions for ${entry.name}" },
                ) {
                    Text("⋮", style = MaterialTheme.typography.titleLarge)
                }
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

private data class PdfProperties(
    val pageCount: Int,
    val firstPageWidth: Int?,
    val firstPageHeight: Int?,
)

@Composable
private fun FileDetailsDialog(entry: LibraryEntry, onDismiss: () -> Unit) {
    val contentResolver = LocalContext.current.contentResolver
    var pdfProperties by remember(entry.uri) { mutableStateOf<Result<PdfProperties>?>(null) }

    LaunchedEffect(entry.uri, entry.isDirectory) {
        if (!entry.isDirectory) {
            pdfProperties = withContext(Dispatchers.IO) {
                runCatching {
                    val descriptor = requireNotNull(contentResolver.openFileDescriptor(Uri.parse(entry.uri), "r"))
                    descriptor.use {
                        PdfRenderer(it).use { renderer ->
                            if (renderer.pageCount == 0) {
                                PdfProperties(0, null, null)
                            } else {
                                renderer.openPage(0).use { page ->
                                    PdfProperties(renderer.pageCount, page.width, page.height)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("File details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailRow("Name", entry.name)
                DetailRow("Type", if (entry.isDirectory) "Folder" else "PDF document")
                if (!entry.isDirectory) DetailRow("Size", formatFileSize(entry.sizeBytes))
                DetailRow("Last modified", formatModifiedDate(entry.modifiedAtMillis))
                if (!entry.isDirectory) {
                    Text("PDF properties", style = MaterialTheme.typography.titleSmall)
                    when {
                        pdfProperties == null -> Text("Reading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        pdfProperties?.isFailure == true -> Text(
                            "Unavailable",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> pdfProperties?.getOrNull()?.let { properties ->
                            DetailRow("Pages", properties.pageCount.toString())
                            if (properties.firstPageWidth != null && properties.firstPageHeight != null) {
                                DetailRow(
                                    "First page size",
                                    "${properties.firstPageWidth} × ${properties.firstPageHeight} pt",
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
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
    onOpenDownloadedScore: (ScoreDocument) -> Unit,
    searchVisible: Boolean,
    onDismissSearch: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var webView by remember { mutableStateOf<android.webkit.WebView?>(null) }
    var canNavigateBack by remember { mutableStateOf(false) }
    BackHandler(enabled = canNavigateBack) {
        webView?.let { view ->
            if (view.canGoBack()) view.goBack()
            canNavigateBack = view.canGoBack()
        }
    }
    fun submitSearch() {
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.toString())
        webView?.loadUrl("https://imslp.org/index.php?search=$encoded&title=Special%3ASearch&go=Go")
        onDismissSearch()
    }
    Column(modifier = modifier.fillMaxSize()) {
        if (state.message != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    state.message,
                    modifier = Modifier.weight(1f),
                    color = if (state.message.contains("failed", true)) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
                state.downloadedScore
                    ?.takeIf { state.message == "${it.fileName} downloaded" }
                    ?.let { downloaded ->
                        TextButton(onClick = { onOpenDownloadedScore(downloaded.document) }) {
                            Text("Open")
                        }
                    }
            }
        }
        if (!hasLibrary) {
            Text(
                "Choose a score folder in Settings before downloading.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }
        ImslpWebView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            onReady = {
                webView = it
                canNavigateBack = it.canGoBack()
            },
            onHistoryChanged = { canNavigateBack = it },
            onDownload = onDownloadPdf,
        )
    }
    if (searchVisible) {
        AlertDialog(
            onDismissRequest = onDismissSearch,
            title = { Text("Search IMSLP") },
            text = {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Composer, work, or catalogue number") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (query.isNotBlank()) submitSearch()
                    }),
                )
            },
            confirmButton = {
                TextButton(onClick = ::submitSearch, enabled = query.isNotBlank()) { Text("Search") }
            },
            dismissButton = { TextButton(onClick = onDismissSearch) { Text("Cancel") } },
        )
    }
}

@Composable
@SuppressLint("SetJavaScriptEnabled") // IMSLP's interactive download flow requires JavaScript.
private fun ImslpWebView(
    modifier: Modifier,
    onReady: (android.webkit.WebView) -> Unit,
    onHistoryChanged: (canGoBack: Boolean) -> Unit,
    onDownload: (String, String?, String?, String?, String?) -> Unit,
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
                    override fun doUpdateVisitedHistory(
                        view: android.webkit.WebView,
                        url: String?,
                        isReload: Boolean,
                    ) {
                        super.doUpdateVisitedHistory(view, url, isReload)
                        onHistoryChanged(view.canGoBack())
                    }

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

private fun formatModifiedDate(millis: Long): String = if (millis > 0) {
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
} else {
    "Unknown"
}
