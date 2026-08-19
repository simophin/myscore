package dev.fanchao.myscore

import dev.fanchao.myscore.data.ScoreDocument
import dev.fanchao.myscore.data.ScoreLibraryRepository
import dev.fanchao.myscore.data.DirectoryListing
import dev.fanchao.myscore.data.LibraryEntry
import dev.fanchao.myscore.data.UserSettingsRepository
import dev.fanchao.myscore.data.PageLayoutPreference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val settings = FakeSettingsRepository()
    private val library = FakeScoreLibraryRepository()

    @Test
    fun `choosing a folder persists it and loads scores`() = runTest {
        val viewModel = observedViewModel()
        val score = ScoreDocument("content://scores/bach.pdf", "Bach", 100, 0)
        library.scores = listOf(score)

        viewModel.setLibraryFolder("content://scores")
        advanceUntilIdle()

        assertEquals("content://scores", settings.libraryUri.value)
        assertEquals(listOf(score), viewModel.uiState.value.library.scores)
        assertTrue(viewModel.uiState.value.library.initialized)
    }

    @Test
    fun `refresh exposes repository errors in immutable ui state`() = runTest {
        val viewModel = observedViewModel()
        library.failure = IllegalStateException("Folder unavailable")

        viewModel.refresh("content://missing")
        advanceUntilIdle()

        assertEquals("Folder unavailable", viewModel.uiState.value.library.message)
        assertFalse(viewModel.uiState.value.library.loading)
    }

    @Test
    fun `download without a configured folder updates state without touching network`() = runTest {
        val viewModel = observedViewModel()

        viewModel.downloadPdf("https://imslp.org/file.pdf", null, null, null, null)
        advanceUntilIdle()

        assertEquals("Choose a score folder before downloading", viewModel.uiState.value.library.message)
        assertEquals(0, library.downloadCount)
    }

    @Test
    fun `reader page and last score are delegated to settings repository`() = runTest {
        val viewModel = observedViewModel()

        viewModel.saveReaderPage("content://scores/bach.pdf", 7)
        viewModel.recordOpenedScore("content://scores/bach.pdf")
        advanceUntilIdle()

        assertEquals(7, settings.pages["content://scores/bach.pdf"]?.value)
        assertEquals("content://scores/bach.pdf", settings.lastScoreUri.value)
    }

    @Test
    fun `reader layout is persisted independently for each score`() = runTest {
        val viewModel = observedViewModel()

        viewModel.saveReaderLayout("content://scores/bach.pdf", PageLayoutPreference.Single)
        viewModel.saveReaderLayout("content://scores/mozart.pdf", PageLayoutPreference.Two)
        advanceUntilIdle()

        assertEquals(PageLayoutPreference.Single, settings.layouts["content://scores/bach.pdf"]?.value)
        assertEquals(PageLayoutPreference.Two, settings.layouts["content://scores/mozart.pdf"]?.value)
    }

    @Test
    fun `folder navigation remains in repository-provided tree and supports back`() = runTest {
        val viewModel = observedViewModel()
        val folder = LibraryEntry("content://scores/bach", "Bach", true, 0, 0)
        library.listings["content://scores"] = DirectoryListing("content://scores", "Scores", listOf(folder))
        library.listings[folder.uri] = DirectoryListing(folder.uri, folder.name, emptyList())

        viewModel.setLibraryFolder("content://scores")
        advanceUntilIdle()
        viewModel.openDirectory(folder)
        advanceUntilIdle()

        assertEquals(listOf("Scores", "Bach"), viewModel.uiState.value.library.path.map { it.name })
        viewModel.navigateUp()
        advanceUntilIdle()
        assertEquals(listOf("Scores"), viewModel.uiState.value.library.path.map { it.name })
    }

    @Test
    fun `copy clipboard pastes into current folder`() = runTest {
        val viewModel = observedViewModel()
        val score = LibraryEntry("content://scores/bach.pdf", "Bach.pdf", false, 100, 0)
        library.listings["content://scores"] = DirectoryListing("content://scores", "Scores", listOf(score))

        viewModel.setLibraryFolder("content://scores")
        advanceUntilIdle()
        viewModel.stageCopy(score)
        viewModel.paste()
        advanceUntilIdle()

        assertEquals(Triple("content://scores", score.uri, "content://scores"), library.lastCopy)
        assertEquals(FileTransferMode.Copy, viewModel.uiState.value.library.clipboard?.mode)
    }

    @Test
    fun `create folder targets current directory`() = runTest {
        val viewModel = observedViewModel()
        val folder = LibraryEntry("content://scores/bach", "Bach", true, 0, 0)
        library.listings["content://scores"] = DirectoryListing("content://scores", "Scores", listOf(folder))
        library.listings[folder.uri] = DirectoryListing(folder.uri, folder.name, emptyList())

        viewModel.setLibraryFolder("content://scores")
        advanceUntilIdle()
        viewModel.openDirectory(folder)
        advanceUntilIdle()
        viewModel.createFolder("Concertos")
        advanceUntilIdle()

        assertEquals(Triple("content://scores", folder.uri, "Concertos"), library.lastCreateDirectory)
    }

    @Test
    fun `rename delegates entry and clears matching clipboard`() = runTest {
        val viewModel = observedViewModel()
        val score = LibraryEntry("content://scores/bach.pdf", "Bach.pdf", false, 100, 0)
        library.listings["content://scores"] = DirectoryListing("content://scores", "Scores", listOf(score))

        viewModel.setLibraryFolder("content://scores")
        advanceUntilIdle()
        viewModel.stageMove(score)
        viewModel.rename(score, "Prelude.pdf")
        advanceUntilIdle()

        assertEquals(Triple("content://scores", score.uri, "Prelude.pdf"), library.lastRename)
        assertEquals(null, viewModel.uiState.value.library.clipboard)
    }

    private fun kotlinx.coroutines.test.TestScope.observedViewModel(): MainViewModel {
        val viewModel = MainViewModel(settings, library)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }
}

private class FakeSettingsRepository : UserSettingsRepository {
    override val libraryUri = MutableStateFlow<String?>(null)
    override val lastScoreUri = MutableStateFlow<String?>(null)
    val pages = mutableMapOf<String, MutableStateFlow<Int>>()
    val layouts = mutableMapOf<String, MutableStateFlow<PageLayoutPreference>>()

    override suspend fun setLibraryUri(uri: String) { libraryUri.value = uri }
    override suspend fun setLastScoreUri(uri: String) { lastScoreUri.value = uri }
    override fun readerPage(uri: String): Flow<Int> = pages.getOrPut(uri) { MutableStateFlow(0) }
    override suspend fun setReaderPage(uri: String, page: Int) {
        pages.getOrPut(uri) { MutableStateFlow(0) }.value = page
    }
    override fun readerLayout(uri: String): Flow<PageLayoutPreference> =
        layouts.getOrPut(uri) { MutableStateFlow(PageLayoutPreference.Auto) }
    override suspend fun setReaderLayout(uri: String, preference: PageLayoutPreference) {
        layouts.getOrPut(uri) { MutableStateFlow(PageLayoutPreference.Auto) }.value = preference
    }
}

private class FakeScoreLibraryRepository : ScoreLibraryRepository {
    var scores: List<ScoreDocument> = emptyList()
    var failure: Throwable? = null
    var downloadCount = 0
    var listing = DirectoryListing("content://scores", "Scores", emptyList())
    val listings = mutableMapOf<String, DirectoryListing>()
    var lastCopy: Triple<String, String, String>? = null
    var lastCreateDirectory: Triple<String, String, String>? = null
    var lastRename: Triple<String, String, String>? = null

    override suspend fun findScores(treeUri: String): List<ScoreDocument> {
        failure?.let { throw it }
        return scores
    }

    override suspend fun listDirectory(treeUri: String, directoryUri: String?): DirectoryListing {
        failure?.let { throw it }
        return listings[directoryUri ?: treeUri] ?: listing
    }

    override suspend fun deleteEntry(treeUri: String, entryUri: String) = Result.success(Unit)

    override suspend fun createDirectory(treeUri: String, parentDirectoryUri: String, name: String): Result<Unit> {
        lastCreateDirectory = Triple(treeUri, parentDirectoryUri, name)
        return Result.success(Unit)
    }

    override suspend fun renameEntry(treeUri: String, entryUri: String, name: String): Result<Unit> {
        lastRename = Triple(treeUri, entryUri, name)
        return Result.success(Unit)
    }

    override suspend fun copyEntry(treeUri: String, entryUri: String, destinationDirectoryUri: String): Result<Unit> {
        lastCopy = Triple(treeUri, entryUri, destinationDirectoryUri)
        return Result.success(Unit)
    }

    override suspend fun moveEntry(treeUri: String, entryUri: String, destinationDirectoryUri: String) =
        Result.success(Unit)

    override suspend fun importPdf(source: String, treeUri: String) = Result.success(Unit)

    override suspend fun downloadPdf(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        cookies: String?,
        treeUri: String,
    ): Result<String> {
        downloadCount++
        return Result.success("score.pdf")
    }
}
