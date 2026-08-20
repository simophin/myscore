package dev.fanchao.myscore

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.fanchao.myscore.data.ScoreDocument
import dev.fanchao.myscore.ui.MyScoreNavigation
import dev.fanchao.myscore.ui.theme.MyScoreTheme

class MainActivity : ComponentActivity() {
    private val intentScore = androidx.compose.runtime.mutableStateOf<ScoreDocument?>(null)
    private val pendingPdfImport = androidx.compose.runtime.mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            MyScoreTheme {
                val app = application as MyScoreApplication
                val factory = remember {
                    MainViewModelFactory(app.settingsRepository, app.libraryRepository)
                }
                val viewModel: MainViewModel = viewModel(factory = factory)
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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

                LaunchedEffect(pendingPdfImport.value, uiState.libraryUri) {
                    val source = pendingPdfImport.value
                    if (source != null && uiState.libraryUri != null) {
                        pendingPdfImport.value = null
                        consumeShareIntent()
                        viewModel.importPdf(source.toString())
                    }
                }

                MyScoreNavigation(
                    viewModel = viewModel,
                    intentScore = intentScore.value,
                    onIntentScoreClosed = { intentScore.value = null },
                    onChooseFolder = folderPicker::launch,
                    onImportPdf = { pdfPicker.launch(arrayOf("application/pdf")) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intentScore.value = viewScoreFromIntent(intent)
        sharedPdfFromIntent(intent)?.let { pendingPdfImport.value = it }
    }

    private fun consumeShareIntent() {
        if (intent?.action != Intent.ACTION_SEND) return
        intent.action = null
        intent.removeExtra(Intent.EXTRA_STREAM)
        intent.clipData = null
    }

    private fun viewScoreFromIntent(intent: Intent?): ScoreDocument? {
        val uri = intent
            ?.takeIf { it.action == Intent.ACTION_VIEW && it.type == PDF_MIME_TYPE }
            ?.data
            ?: return null
        val name = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
        }.getOrNull()
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayName = cursor.getString(0) ?: "Shared score.pdf"
                    displayName to (if (cursor.isNull(1)) 0L else cursor.getLong(1))
                } else null
            } ?: ((uri.lastPathSegment ?: "Shared score.pdf") to 0L)
        return ScoreDocument(
            uri = uri.toString(),
            title = name.first.removeSuffix(".pdf").removeSuffix(".PDF"),
            sizeBytes = name.second,
            modifiedAtMillis = 0,
        )
    }

    private fun sharedPdfFromIntent(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_SEND || intent.type != PDF_MIME_TYPE) return null
        return IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
    }

    private companion object {
        const val PDF_MIME_TYPE = "application/pdf"
    }
}
