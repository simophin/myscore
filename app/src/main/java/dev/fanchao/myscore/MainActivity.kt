package dev.fanchao.myscore

import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.fanchao.myscore.ui.MyScoreApp
import dev.fanchao.myscore.ui.PdfViewer
import dev.fanchao.myscore.ui.theme.MyScoreTheme

class MainActivity : ComponentActivity() {
    private val intentScore = androidx.compose.runtime.mutableStateOf<dev.fanchao.myscore.data.ScoreDocument?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intentScore.value = scoreFromIntent(intent)
        enableEdgeToEdge()
        setContent {
            MyScoreTheme {
                val app = application as MyScoreApplication
                val factory = androidx.compose.runtime.remember {
                    MainViewModelFactory(app.settingsRepository, app.libraryRepository)
                }
                val viewModel: MainViewModel = viewModel(factory = factory)
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val libraryUri = uiState.libraryUri
                val libraryState = uiState.library
                val lastScoreUri = uiState.lastScoreUri
                var selectedScore by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<dev.fanchao.myscore.data.ScoreDocument?>(null) }
                var restoredLastScore by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
                val openScore = intentScore.value ?: selectedScore

                val folderPicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocumentTree(),
                ) { uri ->
                    if (uri != null) {
                        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        runCatching { contentResolver.takePersistableUriPermission(uri, flags) }
                        viewModel.setLibraryFolder(uri.toString())
                    }
                }
                val pdfPicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument(),
                ) { uri -> if (uri != null) viewModel.importPdf(uri.toString()) }

                LaunchedEffect(libraryUri) {
                    if (libraryUri != null && libraryState.scores.isEmpty()) viewModel.refresh(libraryUri)
                }
                ObserveLibraryChanges(libraryUri?.let(Uri::parse)) { viewModel.refresh() }
                LaunchedEffect(lastScoreUri, libraryState.initialized, libraryState.scores) {
                    if (!restoredLastScore && lastScoreUri != null && libraryState.initialized) {
                        selectedScore = libraryState.scores.firstOrNull { it.uri == lastScoreUri }
                        restoredLastScore = true
                    }
                }

                Box(Modifier.fillMaxSize()) {
                    val score = openScore
                    if (score == null) {
                        MyScoreApp(
                            libraryUri = libraryUri?.let(Uri::parse),
                            libraryState = libraryState,
                            onChooseFolder = { folderPicker.launch(libraryUri?.let(Uri::parse)) },
                            onImportPdf = { pdfPicker.launch(arrayOf("application/pdf")) },
                            onDownloadPdf = viewModel::downloadPdf,
                            onRefresh = viewModel::refresh,
                            onOpenScore = { selectedScore = it },
                            onOpenDirectory = viewModel::openDirectory,
                            onNavigateUp = viewModel::navigateUp,
                            onNavigateToPath = viewModel::navigateToPath,
                            onCopy = viewModel::stageCopy,
                            onMove = viewModel::stageMove,
                            onPaste = viewModel::paste,
                            onClearClipboard = viewModel::clearClipboard,
                            onDelete = viewModel::delete,
                        )
                    } else {
                        LaunchedEffect(score.uri) { viewModel.recordOpenedScore(score.uri) }
                        val rememberedPage by viewModel.readerPage(score.uri)
                            .collectAsStateWithLifecycle(initialValue = -1)
                        val pageLayout by viewModel.readerLayout(score.uri)
                            .collectAsStateWithLifecycle(initialValue = null)
                        if (rememberedPage < 0 || pageLayout == null) {
                            androidx.compose.material3.CircularProgressIndicator(
                                Modifier.align(androidx.compose.ui.Alignment.Center),
                            )
                        } else {
                            PdfViewer(
                                score = score,
                                initialPage = rememberedPage,
                                layoutPreference = requireNotNull(pageLayout),
                                onPageChanged = { viewModel.saveReaderPage(score.uri, it) },
                                onLayoutPreferenceChanged = { viewModel.saveReaderLayout(score.uri, it) },
                                onBack = {
                                    intentScore.value = null
                                    selectedScore = null
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentScore.value = scoreFromIntent(intent)
    }

    private fun scoreFromIntent(intent: Intent?): dev.fanchao.myscore.data.ScoreDocument? {
        val uri = when {
            intent?.action == Intent.ACTION_VIEW && intent.type == "application/pdf" -> intent.data
            intent?.data?.scheme == "myscore" -> intent.data?.getQueryParameter("uri")?.let(Uri::parse)
            else -> null
        } ?: return null
        val name = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
        }.getOrNull()
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayName = cursor.getString(0) ?: "Shared score.pdf"
                    displayName to (if (cursor.isNull(1)) 0L else cursor.getLong(1))
                } else null
            } ?: ((uri.lastPathSegment ?: "Shared score.pdf") to 0L)
        return dev.fanchao.myscore.data.ScoreDocument(
            uri = uri.toString(),
            title = name.first.removeSuffix(".pdf").removeSuffix(".PDF"),
            sizeBytes = name.second,
            modifiedAtMillis = 0,
        )
    }
}

@androidx.compose.runtime.Composable
private fun ObserveLibraryChanges(uri: Uri?, onChanged: () -> Unit) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val context = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(uri, lifecycleOwner) {
        if (uri == null) return@DisposableEffect onDispose { }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChanged()
        }
        context.contentResolver.registerContentObserver(uri, true, observer)
        val lifecycleObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) onChanged()
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }
}
