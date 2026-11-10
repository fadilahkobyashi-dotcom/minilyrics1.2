package com.crintsoft.zikiplayer.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.crintsoft.zikiplayer.data.room.entities.LyricsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricsDao {

    @Query("SELECT * FROM lyrics_records WHERE songId = :songId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLyricsBySongId(songId: String): LyricsEntity?

    @Query("SELECT * FROM lyrics_records WHERE stableKey = :stableKey ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLyricsByStableKey(stableKey: String): LyricsEntity?

    @Query("SELECT * FROM lyrics_records WHERE stableKey = :stableKey OR songId = :songId OR musicFileRef = :musicFileRef ORDER BY updatedAt DESC LIMIT 1")
    suspend fun findLyrics(songId: String, stableKey: String, musicFileRef: String): LyricsEntity?

    @Query("SELECT * FROM lyrics_records ORDER BY updatedAt DESC")
    fun getAllLyricsFlow(): Flow<List<LyricsEntity>>

    @Query("SELECT * FROM lyrics_records ORDER BY updatedAt DESC")
    suspend fun getAllLyricsList(): List<LyricsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceLyrics(lyrics: LyricsEntity): Long

    @Query("DELETE FROM lyrics_records WHERE songId = :songId")
    suspend fun deleteLyricsBySongId(songId: String): Int

    @Query("SELECT COUNT(*) FROM lyrics_records")
    suspend fun count(): Int
}
