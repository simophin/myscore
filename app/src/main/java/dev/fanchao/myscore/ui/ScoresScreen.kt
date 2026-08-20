package dev.fanchao.myscore.ui

import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.fanchao.myscore.LibraryUiState
import dev.fanchao.myscore.FileTransferMode
import dev.fanchao.myscore.data.LibraryEntry
import dev.fanchao.myscore.data.ScoreDocument
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ScoresScreen(
    modifier: Modifier,
    libraryUri: Uri?,
    state: LibraryUiState,
    sortOrder: ScoreSortOrder,
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
            sortOrder = sortOrder,
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
    sortOrder: ScoreSortOrder,
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
                    items(sortLibraryEntries(state.entries, sortOrder), key = { it.uri }) { entry ->
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

internal enum class ScoreSortOrder(val label: String) {
    NameAscending("Name (A–Z)"),
    NameDescending("Name (Z–A)"),
    NewestFirst("Newest first"),
    OldestFirst("Oldest first"),
}

internal fun sortLibraryEntries(
    entries: List<LibraryEntry>,
    sortOrder: ScoreSortOrder,
): List<LibraryEntry> {
    val selectedComparator = when (sortOrder) {
        ScoreSortOrder.NameAscending -> compareBy(String.CASE_INSENSITIVE_ORDER, LibraryEntry::name)
        ScoreSortOrder.NameDescending -> compareByDescending<LibraryEntry> { it.name.lowercase() }
            .thenByDescending { it.name }
        ScoreSortOrder.NewestFirst -> compareByDescending<LibraryEntry> { it.modifiedAtMillis }
            .thenBy(String.CASE_INSENSITIVE_ORDER, LibraryEntry::name)
        ScoreSortOrder.OldestFirst -> compareBy<LibraryEntry> { it.modifiedAtMillis }
            .thenBy(String.CASE_INSENSITIVE_ORDER, LibraryEntry::name)
    }
    return entries.sortedWith(
        compareByDescending<LibraryEntry> { it.isDirectory }.then(selectedComparator),
    )
}

@Composable
internal fun NameDialog(
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
            EntryTypeIcon(isDirectory = entry.isDirectory)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    entry.name,
                    fontWeight = FontWeight.Medium,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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

@Composable
private fun EntryTypeIcon(isDirectory: Boolean) {
    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
        if (isDirectory) {
            val color = MaterialTheme.colorScheme.primary
            Canvas(Modifier.size(28.dp)) {
                val tabTop = size.height * 0.24f
                val tabHeight = size.height * 0.22f
                val bodyTop = size.height * 0.36f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.08f, tabTop),
                    size = Size(size.width * 0.42f, tabHeight),
                    cornerRadius = CornerRadius(size.width * 0.08f, size.width * 0.08f),
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.08f, bodyTop),
                    size = Size(size.width * 0.84f, size.height * 0.42f),
                    cornerRadius = CornerRadius(size.width * 0.1f, size.width * 0.1f),
                )
            }
        } else {
            Text("♬", style = MaterialTheme.typography.headlineSmall)
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
