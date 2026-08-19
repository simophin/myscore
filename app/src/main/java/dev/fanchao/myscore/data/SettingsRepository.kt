package dev.fanchao.myscore.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

interface UserSettingsRepository {
    val libraryUri: Flow<String?>
    val lastScoreUri: Flow<String?>
    suspend fun setLibraryUri(uri: String)
    suspend fun setLastScoreUri(uri: String)
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

class DataStoreUserSettingsRepository(private val context: Context) : UserSettingsRepository {
    private val libraryUriKey = stringPreferencesKey("library_uri")
    private val lastScoreUriKey = stringPreferencesKey("last_score_uri")

    override val libraryUri: Flow<String?> = context.settingsDataStore.data.map { it[libraryUriKey] }

    override val lastScoreUri: Flow<String?> = context.settingsDataStore.data.map { it[lastScoreUriKey] }

    override suspend fun setLibraryUri(uri: String) {
        context.settingsDataStore.edit { it[libraryUriKey] = uri }
    }

    override suspend fun setLastScoreUri(uri: String) {
        context.settingsDataStore.edit { it[lastScoreUriKey] = uri }
    }

    override fun readerPage(uri: String): Flow<Int> {
        val key = stringPreferencesKey("reader_page_${uri.stableKey()}")
        return context.settingsDataStore.data.map { it[key]?.toIntOrNull() ?: 0 }
    }

    override suspend fun setReaderPage(uri: String, page: Int) {
        val key = stringPreferencesKey("reader_page_${uri.stableKey()}")
        context.settingsDataStore.edit { it[key] = page.coerceAtLeast(0).toString() }
    }

    override fun readerLayout(uri: String): Flow<PageLayoutPreference> {
        val key = stringPreferencesKey("reader_layout_${uri.stableKey()}")
        return context.settingsDataStore.data.map { PageLayoutPreference.fromStoredValue(it[key]) }
    }

    override suspend fun setReaderLayout(uri: String, preference: PageLayoutPreference) {
        val key = stringPreferencesKey("reader_layout_${uri.stableKey()}")
        context.settingsDataStore.edit { it[key] = preference.storedValue }
    }

    private fun String.stableKey(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray())
        .take(12)
        .joinToString("") { "%02x".format(it) }
}
