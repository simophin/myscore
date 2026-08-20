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
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.fanchao.myscore.data.ScoreDocument
import dev.fanchao.myscore.ui.MyScoreNavigation
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
                val factory = remember {
                    MainViewModelFactory(app.settingsRepository, app.libraryRepository)
                }
                val viewModel: MainViewModel = viewModel(factory = factory)

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
        intentScore.value = scoreFromIntent(intent)
    }

    private fun scoreFromIntent(intent: Intent?): ScoreDocument? {
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
        return ScoreDocument(
            uri = uri.toString(),
            title = name.first.removeSuffix(".pdf").removeSuffix(".PDF"),
            sizeBytes = name.second,
            modifiedAtMillis = 0,
        )
    }
}
