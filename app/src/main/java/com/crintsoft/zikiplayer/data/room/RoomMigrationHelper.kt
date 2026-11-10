package com.crintsoft.zikiplayer.data.room

import android.content.Context
import android.util.Log
import com.crintsoft.zikiplayer.data.db.MiniLyricsDbHelper
import com.crintsoft.zikiplayer.data.room.entities.LyricsEntity
import com.crintsoft.zikiplayer.data.room.entities.PlaylistEntity
import com.crintsoft.zikiplayer.data.room.entities.PlaylistSongEntity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RoomMigrationHelper {
    private const val TAG = "RoomMigrationHelper"

    suspend fun migrateFromSqliteIfNeeded(context: Context, database: MiniLyricsRoomDatabase) = withContext(Dispatchers.IO) {
        val oldDbFile = context.getDatabasePath(MiniLyricsDbHelper.DATABASE_NAME)
        if (!oldDbFile.exists()) {
            return@withContext
        }

        try {
            val playlistDao = database.playlistDao()
            val lyricsDao = database.lyricsDao()

            // Check if Room already has playlists
            val existingPlaylists = playlistDao.getAllPlaylistsList()
            val oldDbHelper = MiniLyricsDbHelper(context)

            if (existingPlaylists.isEmpty()) {
                val oldPlaylists = oldDbHelper.getAllPlaylists()
                Log.d(TAG, "Migrating ${oldPlaylists.size} playlists from legacy SQLite to Room")
                for (oldPl in oldPlaylists) {
                    playlistDao.insertPlaylist(
                        PlaylistEntity(
                            id = oldPl.id,
                            name = oldPl.name,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    oldPl.trackIds.forEachIndexed { index, trackId ->
                        playlistDao.insertPlaylistSong(
                            PlaylistSongEntity(
                                playlistId = oldPl.id,
                                songId = trackId,
                                orderIndex = index
                            )
                        )
                    }
                }
            }

            // Check if Room already has lyrics records
            val lyricsCount = lyricsDao.count()
            if (lyricsCount == 0) {
                val oldLyrics = oldDbHelper.getAllLyricsAssociations()
                Log.d(TAG, "Migrating ${oldLyrics.size} lyrics associations from legacy SQLite to Room")
                for (assoc in oldLyrics) {
                    lyricsDao.insertOrReplaceLyrics(
                        LyricsEntity(
                            songId = assoc.songId,
                            stableKey = assoc.stableKey,
                            title = assoc.title,
                            artist = assoc.artist,
                            album = assoc.album,
                            musicFileRef = assoc.musicFileRef,
                            lyricsFormat = assoc.lyricsFormat,
                            lyricsFilePath = assoc.lyricsFilePath,
                            storageType = assoc.storageType,
                            lyricsSource = assoc.lyricsSource,
                            fileSizeBytes = assoc.fileSizeBytes,
                            createdAt = assoc.createdAt,
                            updatedAt = assoc.updatedAt
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Migration from legacy SQLite to Room failed gracefully", e)
        }
    }
}
