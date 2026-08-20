package dev.fanchao.myscore.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsScreen(
    modifier: Modifier,
    libraryUri: Uri?,
    paperModeEnabled: Boolean,
    appVersionName: String,
    onChooseFolder: () -> Unit,
    onImportPdf: () -> Unit,
    onPaperModeChanged: (Boolean) -> Unit,
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
        Text("Reading", style = MaterialTheme.typography.titleMedium)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Paper mode", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = paperModeEnabled,
                    onCheckedChange = onPaperModeChanged,
                )
            }
            Text(
                "Use a warmer paper-like page background across the app.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("Storage", style = MaterialTheme.typography.titleMedium)
        Text(
            "Folder access is granted through Android's system picker. MyScore does not request access to all files on your device.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text("App", style = MaterialTheme.typography.titleMedium)
        Text(
            "Version $appVersionName",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
