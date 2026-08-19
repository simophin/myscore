package dev.fanchao.myscore.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso.pressBack
import dev.fanchao.myscore.LibraryUiState
import dev.fanchao.myscore.data.ScoreDocument
import dev.fanchao.myscore.data.DownloadedScore
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
                    onOpenDownloadedScore = {},
                    onRefresh = {},
                    onOpenScore = { selected = it },
                    onOpenDirectory = {},
                    onNavigateUp = {},
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
                    onOpenDownloadedScore = {},
                    onRefresh = {},
                    onOpenScore = {},
                    onOpenDirectory = {},
                    onNavigateUp = {},
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
    fun completedDownloadOffersToOpenTheSavedScore() {
        val score = ScoreDocument("content://library/new-score.pdf", "New score", 2_000, 0)
        var opened: ScoreDocument? = null
        composeRule.setContent {
            MyScoreTheme {
                MyScoreApp(
                    libraryUri = android.net.Uri.parse("content://library"),
                    libraryState = LibraryUiState(
                        message = "New score.pdf downloaded",
                        downloadedScore = DownloadedScore("New score.pdf", score),
                    ),
                    onChooseFolder = {},
                    onImportPdf = {},
                    onDownloadPdf = { _, _, _, _, _ -> },
                    onOpenDownloadedScore = { opened = it },
                    onRefresh = {},
                    onOpenScore = {},
                    onOpenDirectory = {},
                    onNavigateUp = {},
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

        composeRule.onNodeWithText("Find").performClick()
        composeRule.onNodeWithText("New score.pdf downloaded").assertIsDisplayed()
        composeRule.onNodeWithText("Open").performClick()
        composeRule.runOnIdle { assertEquals(score, opened) }
    }

    @Test
    fun backFromTopLevelDestinationReturnsToScores() {
        composeRule.setContent {
            MyScoreTheme {
                MyScoreApp(
                    libraryUri = null,
                    libraryState = LibraryUiState(),
                    onChooseFolder = {},
                    onImportPdf = {},
                    onDownloadPdf = { _, _, _, _, _ -> },
                    onOpenDownloadedScore = {},
                    onRefresh = {},
                    onOpenScore = {},
                    onOpenDirectory = {},
                    onNavigateUp = {},
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
        pressBack()
        composeRule.onNodeWithText("Choose your score folder").assertIsDisplayed()
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
                    onOpenDownloadedScore = {},
                    onRefresh = {},
                    onOpenScore = {},
                    onOpenDirectory = {},
                    onNavigateUp = {},
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

        composeRule.onNodeWithContentDescription("Score actions").performClick()
        composeRule.onNodeWithText("New folder").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("Concertos")
        composeRule.onNodeWithText("Create").performClick()
        composeRule.runOnIdle { assertEquals("Concertos", createdFolder) }

        composeRule.onNodeWithContentDescription("Actions for Bach.pdf").performClick()
        composeRule.onNodeWithText("Rename").performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Prelude.pdf")
        composeRule.onNodeWithText("Rename").performClick()
        composeRule.runOnIdle { assertEquals(score to "Prelude.pdf", renamedEntry) }
    }

    @Test
    fun scoreBrowserMovesFolderAndRefreshStateIntoTheAppBar() {
        var refreshes = 0
        composeRule.setContent {
            MyScoreTheme {
                MyScoreApp(
                    libraryUri = android.net.Uri.parse("content://library"),
                    libraryState = LibraryUiState(
                        initialized = true,
                        path = listOf(dev.fanchao.myscore.FolderLocation("content://library/concertos", "Concertos")),
                    ),
                    onChooseFolder = {},
                    onImportPdf = {},
                    onDownloadPdf = { _, _, _, _, _ -> },
                    onOpenDownloadedScore = {},
                    onRefresh = { refreshes++ },
                    onOpenScore = {},
                    onOpenDirectory = {},
                    onNavigateUp = {},
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

        composeRule.onNodeWithText("Concertos").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Refresh scores").performClick()
        composeRule.runOnIdle { assertEquals(1, refreshes) }
    }

    @Test
    fun findSearchOpensAsAnAppBarDialog() {
        composeRule.setContent {
            MyScoreTheme {
                MyScoreApp(
                    libraryUri = null,
                    libraryState = LibraryUiState(),
                    onChooseFolder = {},
                    onImportPdf = {},
                    onDownloadPdf = { _, _, _, _, _ -> },
                    onOpenDownloadedScore = {},
                    onRefresh = {},
                    onOpenScore = {},
                    onOpenDirectory = {},
                    onNavigateUp = {},
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

        composeRule.onNodeWithText("Find").performClick()
        composeRule.onNodeWithContentDescription("Search IMSLP").performClick()
        composeRule.onNodeWithText("Search IMSLP").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).performTextInput("Bach cello suites")
        composeRule.onNodeWithText("Search").performClick()
    }
}
