package dev.fanchao.myscore.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Score library").assertIsDisplayed()
        composeRule.onNodeWithText("Choose folder").assertIsDisplayed()
        composeRule.onNodeWithText("Import an existing PDF").assertIsDisplayed()
    }
}
