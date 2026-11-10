package com.crintsoft.zikiplayer.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.crintsoft.zikiplayer.data.room.entities.PlaylistEntity
import com.crintsoft.zikiplayer.data.room.entities.PlaylistSongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY createdAt ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY createdAt ASC")
    suspend fun getAllPlaylistsList(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun getPlaylistById(id: String): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePlaylistName(id: String, name: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: String): Int

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    suspend fun getPlaylistSongs(playlistId: String): List<PlaylistSongEntity>

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    fun getPlaylistSongsFlow(playlistId: String): Flow<List<PlaylistSongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSong(playlistSong: PlaylistSongEntity)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removePlaylistSong(playlistId: String, songId: String): Int

    @Query("UPDATE playlist_songs SET orderIndex = :orderIndex WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun updatePlaylistSongOrder(playlistId: String, songId: String, orderIndex: Int): Int

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun deletePlaylistSongsForPlaylist(playlistId: String): Int

    @Query("DELETE FROM playlist_songs WHERE songId = :songId")
    suspend fun deletePlaylistSongsForSong(songId: String): Int

    @Query("SELECT COALESCE(MAX(orderIndex), -1) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun getMaxOrderIndex(playlistId: String): Int

    @Transaction
    suspend fun reorderPlaylistSongs(playlistId: String, orderedSongIds: List<String>) {
        orderedSongIds.forEachIndexed { index, songId ->
            updatePlaylistSongOrder(playlistId, songId, index)
        }
    }

    @Transaction
    suspend fun addSongToEndOfPlaylist(playlistId: String, songId: String) {
        val maxOrder = getMaxOrderIndex(playlistId)
        insertPlaylistSong(PlaylistSongEntity(playlistId = playlistId, songId = songId, orderIndex = maxOrder + 1))
    }

    @Transaction
    suspend fun removeSongAndCompact(playlistId: String, songId: String) {
        removePlaylistSong(playlistId, songId)
        val remaining = getPlaylistSongs(playlistId)
        remaining.forEachIndexed { index, item ->
            if (item.orderIndex != index) {
                updatePlaylistSongOrder(playlistId, item.songId, index)
            }
        }
    }

    @Transaction
    suspend fun moveSong(playlistId: String, fromIndex: Int, toIndex: Int): Boolean {
        val songs = getPlaylistSongs(playlistId).toMutableList()
        if (fromIndex in songs.indices && toIndex in songs.indices && fromIndex != toIndex) {
            val moved = songs.removeAt(fromIndex)
            songs.add(toIndex, moved)
            songs.forEachIndexed { index, item ->
                updatePlaylistSongOrder(playlistId, item.songId, index)
            }
            return true
        }
        return false
    }
}
