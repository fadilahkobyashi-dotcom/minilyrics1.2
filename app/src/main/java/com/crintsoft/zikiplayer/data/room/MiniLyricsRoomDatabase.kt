package com.crintsoft.zikiplayer.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.crintsoft.zikiplayer.data.room.dao.ArtworkDao
import com.crintsoft.zikiplayer.data.room.dao.LyricsDao
import com.crintsoft.zikiplayer.data.room.dao.PlaylistDao
import com.crintsoft.zikiplayer.data.room.dao.SongDao
import com.crintsoft.zikiplayer.data.room.entities.ArtworkEntity
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
        ArtworkEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MiniLyricsRoomDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun lyricsDao(): LyricsDao
    abstract fun artworkDao(): ArtworkDao

    companion object {
        private const val DATABASE_NAME = "minilyrics_room.db"

        @Volatile
        private var INSTANCE: MiniLyricsRoomDatabase? = null

        fun getInstance(context: Context): MiniLyricsRoomDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MiniLyricsRoomDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
