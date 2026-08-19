package dev.fanchao.myscore.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import dev.fanchao.myscore.LibraryUiState
import dev.fanchao.myscore.data.ScoreDocument
import dev.fanchao.myscore.data.LibraryEntry
import dev.fanchao.myscore.ui.theme.MyScoreTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MyScoreAppTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun galleryDisplaysScoresAndForwardsSelection() {
        val score = ScoreDocument("content://library/bach.pdf", "Bach Prelude", 1_500_000, 0)
        var selected: ScoreDocument? = null

        composeRule.setContent {
            MyScoreTheme {
                MyScoreApp(
                    libraryUri = android.net.Uri.parse("content://library"),
                    libraryState = LibraryUiState(
                        initialized = true,
                        scores = listOf(score),
                        entries = listOf(
                            LibraryEntry("content://library/bach.pdf", "Bach Prelude.pdf", false, 1_500_000, 0),
                        ),
                        path = listOf(dev.fanchao.myscore.FolderLocation("content://library", "Scores")),
                    ),
                    onChooseFolder = {},
                    onImportPdf = {},
                    onDownloadPdf = { _, _, _, _, _ -> },
                    onRefresh = {},
                    onOpenScore = { selected = it },
                    onOpenDirectory = {},
                    onNavigateUp = {},
                    onNavigateToPath = {},
                    onCopy = {},
                    onMove = {},
                    onPaste = {},
                    onClearClipboard = {},
                    onCreateFolder = {},
                    onRename = { _, _ -> },
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithText("Bach Prelude.pdf").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(score, selected) }
    }

    @Test
    fun bottomNavigationShowsSettingsAndFolderAction() {
        composeRule.setContent {
            MyScoreTheme {
                MyScoreApp(
                    libraryUri = null,
                    libraryState = LibraryUiState(),
                    onChooseFolder = {},
                    onImportPdf = {},
                    onDownloadPdf = { _, _, _, _, _ -> },
                    onRefresh = {},
                    onOpenScore = {},
                    onOpenDirectory = {},
                    onNavigateUp = {},
                    onNavigateToPath = {},
                    onCopy = {},
                    onMove = {},
                    onPaste = {},
                    onClearClipboard = {},
                    onCreateFolder = {},
                    onRename = { _, _ -> },
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Score library").assertIsDisplayed()
        composeRule.onNodeWithText("Choose folder").assertIsDisplayed()
        composeRule.onNodeWithText("Import an existing PDF").assertIsDisplayed()
    }

    @Test
    fun fileBrowserCreatesFoldersAndRenamesEntries() {
        val score = LibraryEntry("content://library/bach.pdf", "Bach.pdf", false, 1_500_000, 0)
        var createdFolder: String? = null
        var renamedEntry: Pair<LibraryEntry, String>? = null
        composeRule.setContent {
            MyScoreTheme {
                MyScoreApp(
                    libraryUri = android.net.Uri.parse("content://library"),
                    libraryState = LibraryUiState(
                        initialized = true,
                        entries = listOf(score),
                        path = listOf(dev.fanchao.myscore.FolderLocation("content://library", "Scores")),
                    ),
                    onChooseFolder = {},
                    onImportPdf = {},
                    onDownloadPdf = { _, _, _, _, _ -> },
                    onRefresh = {},
                    onOpenScore = {},
                    onOpenDirectory = {},
                    onNavigateUp = {},
                    onNavigateToPath = {},
                    onCopy = {},
                    onMove = {},
                    onPaste = {},
                    onClearClipboard = {},
                    onCreateFolder = { createdFolder = it },
                    onRename = { entry, name -> renamedEntry = entry to name },
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithText("New folder").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("Concertos")
        composeRule.onNodeWithText("Create").performClick()
        composeRule.runOnIdle { assertEquals("Concertos", createdFolder) }

        composeRule.onNodeWithText("⋮").performClick()
        composeRule.onNodeWithText("Rename").performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Prelude.pdf")
        composeRule.onNodeWithText("Rename").performClick()
        composeRule.runOnIdle { assertEquals(score to "Prelude.pdf", renamedEntry) }
    }
}
