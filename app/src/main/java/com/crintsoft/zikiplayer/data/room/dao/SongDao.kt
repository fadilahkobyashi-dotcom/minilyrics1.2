package com.crintsoft.zikiplayer.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.crintsoft.zikiplayer.data.room.entities.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY title ASC")
    suspend fun getAllSongsList(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    suspend fun getSongById(id: String): SongEntity?

    @Query("SELECT * FROM songs WHERE LOWER(artist) = LOWER(:artist) AND LOWER(title) = LOWER(:title) LIMIT 1")
    suspend fun findSongByTitleAndArtist(title: String, artist: String): SongEntity?

    @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY title ASC")
    fun getSongsByArtist(artist: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE album = :album ORDER BY trackNumber ASC, title ASC")
    fun getSongsByAlbum(album: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%' ORDER BY title ASC")
    fun searchSongs(query: String): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>)

    @Query("SELECT * FROM songs WHERE isEdited = 1")
    suspend fun getEditedSongs(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE audioUri = :audioUri LIMIT 1")
    suspend fun getSongByAudioUri(audioUri: String): SongEntity?

    @Query("UPDATE songs SET isEdited = 1, updatedAt = :updatedAt WHERE id = :songId")
    suspend fun markAsEdited(songId: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Update
    suspend fun updateSong(song: SongEntity): Int

    @Query("UPDATE songs SET artworkUri = :artworkUri, updatedAt = :updatedAt WHERE id = :songId")
    suspend fun updateSongArtwork(songId: String, artworkUri: String?, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun deleteSongById(id: String): Int

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun count(): Int
}
