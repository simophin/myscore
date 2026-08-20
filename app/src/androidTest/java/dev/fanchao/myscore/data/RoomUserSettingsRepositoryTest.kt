package dev.fanchao.myscore.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomUserSettingsRepositoryTest {
    private lateinit var database: MyScoreDatabase
    private lateinit var repository: RoomUserSettingsRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MyScoreDatabase::class.java,
        ).build()
        repository = RoomUserSettingsRepository(database.settingsDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun genericConfigsAreStoredByName() = runTest {
        repository.setLibraryUri("content://scores")
        repository.setLastScoreUri("content://scores/bach.pdf")

        assertEquals("content://scores", repository.libraryUri.first())
        assertEquals("content://scores/bach.pdf", repository.lastScoreUri.first())
    }

    @Test
    fun genericConfigsEmitWhenInsertedAfterObservationStarts() = runTest {
        assertEquals(null, repository.libraryUri.first())

        val observedUri = async { repository.libraryUri.first { it == "content://scores" } }

        repository.setLibraryUri("content://scores")

        assertEquals("content://scores", observedUri.await())
    }

    @Test
    fun uriPreferencesAreIndependentAndHaveDefaults() = runTest {
        val bach = "content://scores/bach.pdf"
        val mozart = "content://scores/mozart.pdf"

        assertEquals(0, repository.readerPage(bach).first())
        assertEquals(PageLayoutPreference.Auto, repository.readerLayout(bach).first())
        assertEquals(false, repository.paperModeEnabled.first())

        repository.setReaderPage(bach, 7)
        repository.setReaderLayout(bach, PageLayoutPreference.Two)
        repository.setPaperModeEnabled(true)
        repository.setReaderLayout(mozart, PageLayoutPreference.Single)

        assertEquals(7, repository.readerPage(bach).first())
        assertEquals(PageLayoutPreference.Two, repository.readerLayout(bach).first())
        assertEquals(true, repository.paperModeEnabled.first())
        assertEquals(0, repository.readerPage(mozart).first())
        assertEquals(PageLayoutPreference.Single, repository.readerLayout(mozart).first())
    }

    @Test
    fun negativeReaderPagesAreClampedWithoutOverwritingLayout() = runTest {
        val uri = "content://scores/bach.pdf"
        repository.setReaderLayout(uri, PageLayoutPreference.Single)
        repository.setPaperModeEnabled(true)

        repository.setReaderPage(uri, -4)

        assertEquals(0, repository.readerPage(uri).first())
        assertEquals(PageLayoutPreference.Single, repository.readerLayout(uri).first())
        assertEquals(true, repository.paperModeEnabled.first())
    }
}
