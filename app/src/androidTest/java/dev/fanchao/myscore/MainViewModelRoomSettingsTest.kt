package dev.fanchao.myscore

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.fanchao.myscore.data.DirectoryListing
import dev.fanchao.myscore.data.DownloadedScore
import dev.fanchao.myscore.data.MyScoreDatabase
import dev.fanchao.myscore.data.RoomUserSettingsRepository
import dev.fanchao.myscore.data.ScoreDocument
import dev.fanchao.myscore.data.ScoreLibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainViewModelRoomSettingsTest {
    private lateinit var database: MyScoreDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MyScoreDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun choosingFolderUpdatesUiFromRoomWhenObservedBeforeFirstInsert() = runTest {
        val viewModel = withContext(Dispatchers.Main) {
            MainViewModel(RoomUserSettingsRepository(database.settingsDao()), EmptyScoreLibraryRepository)
        }
        val observedUri = async {
            viewModel.uiState.first { it.libraryUri == "content://scores" }.libraryUri
        }

        withContext(Dispatchers.Main) {
            viewModel.setLibraryFolder("content://scores")
        }

        assertEquals("content://scores", observedUri.await())
    }
}

private object EmptyScoreLibraryRepository : ScoreLibraryRepository {
    override suspend fun findScores(treeUri: String): List<ScoreDocument> = emptyList()

    override suspend fun listDirectory(treeUri: String, directoryUri: String?): DirectoryListing =
        DirectoryListing(treeUri, "Scores", emptyList())

    override suspend fun deleteEntry(treeUri: String, entryUri: String): Result<Unit> = Result.success(Unit)

    override suspend fun createDirectory(treeUri: String, parentDirectoryUri: String, name: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun renameEntry(treeUri: String, entryUri: String, name: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun copyEntry(treeUri: String, entryUri: String, destinationDirectoryUri: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun moveEntry(treeUri: String, entryUri: String, destinationDirectoryUri: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun importPdf(source: String, treeUri: String): Result<Unit> = Result.success(Unit)

    override suspend fun downloadPdf(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        cookies: String?,
        treeUri: String,
    ): Result<DownloadedScore> = error("Downloads are not used in this test")
}
