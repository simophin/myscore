package dev.fanchao.myscore

import android.app.Application
import dev.fanchao.myscore.data.AndroidScoreLibraryRepository
import dev.fanchao.myscore.data.MyScoreDatabase
import dev.fanchao.myscore.data.RoomUserSettingsRepository
import dev.fanchao.myscore.data.ScoreLibraryRepository
import dev.fanchao.myscore.data.UserSettingsRepository

class MyScoreApplication : Application() {
    private val database: MyScoreDatabase by lazy { MyScoreDatabase.create(this) }
    val libraryRepository: ScoreLibraryRepository by lazy { AndroidScoreLibraryRepository(this) }
    val settingsRepository: UserSettingsRepository by lazy { RoomUserSettingsRepository(database.settingsDao()) }
}
