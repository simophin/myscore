package dev.fanchao.myscore

import android.app.Application
import dev.fanchao.myscore.data.AndroidScoreLibraryRepository
import dev.fanchao.myscore.data.DataStoreUserSettingsRepository
import dev.fanchao.myscore.data.ScoreLibraryRepository
import dev.fanchao.myscore.data.UserSettingsRepository

class MyScoreApplication : Application() {
    val libraryRepository: ScoreLibraryRepository by lazy { AndroidScoreLibraryRepository(this) }
    val settingsRepository: UserSettingsRepository by lazy { DataStoreUserSettingsRepository(this) }
}
