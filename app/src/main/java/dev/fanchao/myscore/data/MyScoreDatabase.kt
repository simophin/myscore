package dev.fanchao.myscore.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "configs")
data class ConfigEntity(
    @PrimaryKey val name: String,
    val value: String,
)

@Entity(tableName = "uri_preferences")
data class UriPreferenceEntity(
    @PrimaryKey val uri: String,
    val page: Int = 0,
    val layout: PageLayoutPreference = PageLayoutPreference.Auto,
)

class PreferenceConverters {
    @TypeConverter
    fun pageLayoutFromStoredValue(value: String): PageLayoutPreference =
        PageLayoutPreference.fromStoredValue(value)

    @TypeConverter
    fun pageLayoutToStoredValue(value: PageLayoutPreference): String = value.storedValue
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM configs WHERE name = :name")
    fun observeConfig(name: String): Flow<ConfigEntity?>

    @Upsert
    suspend fun upsertConfig(config: ConfigEntity)

    @Query("SELECT * FROM uri_preferences WHERE uri = :uri")
    fun observeUriPreference(uri: String): Flow<UriPreferenceEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUriPreference(preference: UriPreferenceEntity)

    @Query("UPDATE uri_preferences SET page = :page WHERE uri = :uri")
    suspend fun updatePage(uri: String, page: Int)

    @Query("UPDATE uri_preferences SET layout = :layout WHERE uri = :uri")
    suspend fun updateLayout(uri: String, layout: PageLayoutPreference)

    @Transaction
    suspend fun setPage(uri: String, page: Int) {
        insertUriPreference(UriPreferenceEntity(uri = uri))
        updatePage(uri, page)
    }

    @Transaction
    suspend fun setLayout(uri: String, layout: PageLayoutPreference) {
        insertUriPreference(UriPreferenceEntity(uri = uri))
        updateLayout(uri, layout)
    }
}

@Database(
    entities = [ConfigEntity::class, UriPreferenceEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(PreferenceConverters::class)
abstract class MyScoreDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao

    companion object {
        fun create(context: Context): MyScoreDatabase = Room.databaseBuilder(
            context.applicationContext,
            MyScoreDatabase::class.java,
            "myscore.db",
        ).build()
    }
}
