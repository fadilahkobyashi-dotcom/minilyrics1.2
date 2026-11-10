package com.crintsoft.zikiplayer.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.crintsoft.zikiplayer.data.room.dao.ArtworkDao
import com.crintsoft.zikiplayer.data.room.dao.ExcludedSongDao
import com.crintsoft.zikiplayer.data.room.dao.LyricsDao
import com.crintsoft.zikiplayer.data.room.dao.PlaylistDao
import com.crintsoft.zikiplayer.data.room.dao.SongDao
import com.crintsoft.zikiplayer.data.room.entities.ArtworkEntity
import com.crintsoft.zikiplayer.data.room.entities.ExcludedSongEntity
import com.crintsoft.zikiplayer.data.room.entities.LyricsEntity
import com.crintsoft.zikiplayer.data.room.entities.PlaylistEntity
import com.crintsoft.zikiplayer.data.room.entities.PlaylistSongEntity
import com.crintsoft.zikiplayer.data.room.entities.SongEntity

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        LyricsEntity::class,
        ArtworkEntity::class,
        ExcludedSongEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class MiniLyricsRoomDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun lyricsDao(): LyricsDao
    abstract fun artworkDao(): ArtworkDao
    abstract fun excludedSongDao(): ExcludedSongDao

    companion object {
        private const val DATABASE_NAME = "minilyrics_room.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `excluded_songs` (
                        `id` TEXT NOT NULL,
                        `audioUri` TEXT,
                        `stableKey` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `reason` TEXT NOT NULL,
                        `excludedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_excluded_songs_audioUri` ON `excluded_songs` (`audioUri`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_excluded_songs_stableKey` ON `excluded_songs` (`stableKey`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `songs` ADD COLUMN `isEdited` INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var INSTANCE: MiniLyricsRoomDatabase? = null

        fun getInstance(context: Context): MiniLyricsRoomDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MiniLyricsRoomDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
