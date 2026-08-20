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
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
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
    val paperMode: Boolean = false,
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

    @Query("UPDATE uri_preferences SET paperMode = :enabled WHERE uri = :uri")
    suspend fun updatePaperMode(uri: String, enabled: Boolean)

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

    @Transaction
    suspend fun setPaperMode(uri: String, enabled: Boolean) {
        insertUriPreference(UriPreferenceEntity(uri = uri))
        updatePaperMode(uri, enabled)
    }
}

@Database(
    entities = [ConfigEntity::class, UriPreferenceEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(PreferenceConverters::class)
abstract class MyScoreDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE uri_preferences ADD COLUMN paperMode INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        fun create(context: Context): MyScoreDatabase = Room.databaseBuilder(
            context.applicationContext,
            MyScoreDatabase::class.java,
            "myscore.db",
        ).addMigrations(MIGRATION_1_2).build()
    }
}
