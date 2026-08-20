package dev.fanchao.myscore.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dev.fanchao.myscore.MainViewModel
import dev.fanchao.myscore.data.ScoreDocument
import kotlinx.serialization.Serializable

@Serializable
private data object ScoresRoute : NavKey

@Serializable
private data class ScoreViewerRoute(
    val uri: String,
    val title: String,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
) : NavKey {
    constructor(score: ScoreDocument) : this(
        uri = score.uri,
        title = score.title,
        sizeBytes = score.sizeBytes,
        modifiedAtMillis = score.modifiedAtMillis,
    )

    fun toScoreDocument() = ScoreDocument(uri, title, sizeBytes, modifiedAtMillis)
}

@Composable
internal fun MyScoreNavigation(
    viewModel: MainViewModel,
    intentScore: ScoreDocument?,
    onIntentScoreClosed: () -> Unit,
    onChooseFolder: (Uri?) -> Unit,
    onImportPdf: () -> Unit,
) {
    val uiStateState = viewModel.uiState.collectAsStateWithLifecycle()
    val uiState = uiStateState.value
    val libraryUri = uiState.libraryUri
    val libraryState = uiState.library
    val initialRoutes = remember {
        intentScore?.let { arrayOf<NavKey>(ScoresRoute, ScoreViewerRoute(it)) }
            ?: arrayOf<NavKey>(ScoresRoute)
    }
    val backStack = rememberNavBackStack(*initialRoutes)
    val navigateToScore: (ScoreDocument) -> Unit = { score ->
        if ((backStack.lastOrNull() as? ScoreViewerRoute)?.uri != score.uri) {
            backStack.add(ScoreViewerRoute(score))
        }
    }
    val navigateBack: () -> Unit = {
        val popped = backStack.removeLastOrNull()
        if (popped is ScoreViewerRoute && popped.uri == intentScore?.uri) {
            onIntentScoreClosed()
        }
    }

    LaunchedEffect(libraryUri) {
        if (libraryUri != null && libraryState.scores.isEmpty()) viewModel.refresh(libraryUri)
    }
    LibraryChangeEffect(libraryUri?.let(Uri::parse)) { viewModel.refresh() }
    LaunchedEffect(intentScore) {
        intentScore?.let(navigateToScore)
    }

    NavDisplay(
        backStack = backStack,
        modifier = Modifier.fillMaxSize(),
        onBack = navigateBack,
        entryProvider = { route ->
            when (route) {
                ScoresRoute -> NavEntry(route) {
                    val currentUiState = uiStateState.value
                    val currentLibraryUri = currentUiState.libraryUri
                    val currentLibraryState = currentUiState.library
                    MyScoreApp(
                        libraryUri = currentLibraryUri?.let(Uri::parse),
                        libraryState = currentLibraryState,
                        paperModeEnabled = currentUiState.paperModeEnabled,
                        onChooseFolder = { onChooseFolder(currentLibraryUri?.let(Uri::parse)) },
                        onImportPdf = onImportPdf,
                        onPaperModeChanged = viewModel::savePaperModeEnabled,
                        onDownloadPdf = viewModel::downloadPdf,
                        onOpenDownloadedScore = navigateToScore,
                        onRefresh = viewModel::refresh,
                        onOpenScore = navigateToScore,
                        onOpenDirectory = viewModel::openDirectory,
                        onNavigateUp = viewModel::navigateUp,
                        onCopy = viewModel::stageCopy,
                        onMove = viewModel::stageMove,
                        onPaste = viewModel::paste,
                        onClearClipboard = viewModel::clearClipboard,
                        onCreateFolder = viewModel::createFolder,
                        onRename = viewModel::rename,
                        onDelete = viewModel::delete,
                    )
                }
                is ScoreViewerRoute -> NavEntry(route) {
                    val score = route.toScoreDocument()
                    val rememberedPage by viewModel.readerPage(score.uri)
                        .collectAsStateWithLifecycle(initialValue = -1)
                    val pageLayout by viewModel.readerLayout(score.uri)
                        .collectAsStateWithLifecycle(initialValue = null)
                    val paperModeEnabled = uiStateState.value.paperModeEnabled
                    if (rememberedPage < 0 || pageLayout == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        PdfViewer(
                            score = score,
                            initialPage = rememberedPage,
                            layoutPreference = requireNotNull(pageLayout),
                            paperModeEnabled = paperModeEnabled,
                            onPageChanged = { viewModel.saveReaderPage(score.uri, it) },
                            onLayoutPreferenceChanged = { viewModel.saveReaderLayout(score.uri, it) },
                            onPaperModeChanged = viewModel::savePaperModeEnabled,
                            onBack = navigateBack,
                        )
                    }
                }
                else -> error("Unknown navigation route: $route")
            }
        },
    )
}
