package dev.fanchao.myscore.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface UserSettingsRepository {
    val libraryUri: Flow<String?>
    val lastScoreUri: Flow<String?>
    val paperModeEnabled: Flow<Boolean>
    suspend fun setLibraryUri(uri: String)
    suspend fun setLastScoreUri(uri: String)
    suspend fun setPaperModeEnabled(enabled: Boolean)
    fun readerPage(uri: String): Flow<Int>
    suspend fun setReaderPage(uri: String, page: Int)
    fun readerLayout(uri: String): Flow<PageLayoutPreference>
    suspend fun setReaderLayout(uri: String, preference: PageLayoutPreference)
}

enum class PageLayoutPreference(val storedValue: String) {
    Auto("auto"),
    Single("single"),
    Two("two");

    companion object {
        fun fromStoredValue(value: String?): PageLayoutPreference =
            entries.firstOrNull { it.storedValue == value } ?: Auto
    }
}

class RoomUserSettingsRepository(private val dao: SettingsDao) : UserSettingsRepository {
    override val libraryUri: Flow<String?> = dao.observeConfig(LIBRARY_URI).map { it?.value }

    override val lastScoreUri: Flow<String?> = dao.observeConfig(LAST_SCORE_URI).map { it?.value }

    override val paperModeEnabled: Flow<Boolean> =
        dao.observeConfig(PAPER_MODE_ENABLED).map { it?.value == "true" }

    override suspend fun setLibraryUri(uri: String) {
        dao.upsertConfig(ConfigEntity(LIBRARY_URI, uri))
    }

    override suspend fun setLastScoreUri(uri: String) {
        dao.upsertConfig(ConfigEntity(LAST_SCORE_URI, uri))
    }

    override suspend fun setPaperModeEnabled(enabled: Boolean) {
        dao.upsertConfig(ConfigEntity(PAPER_MODE_ENABLED, enabled.toString()))
    }

    override fun readerPage(uri: String): Flow<Int> =
        dao.observeUriPreference(uri).map { it?.page ?: 0 }

    override suspend fun setReaderPage(uri: String, page: Int) {
        dao.setPage(uri, page.coerceAtLeast(0))
    }

    override fun readerLayout(uri: String): Flow<PageLayoutPreference> =
        dao.observeUriPreference(uri).map { it?.layout ?: PageLayoutPreference.Auto }

    override suspend fun setReaderLayout(uri: String, preference: PageLayoutPreference) {
        dao.setLayout(uri, preference)
    }

    private companion object {
        const val LIBRARY_URI = "library_uri"
        const val LAST_SCORE_URI = "last_score_uri"
        const val PAPER_MODE_ENABLED = "paper_mode_enabled"
    }
}
