package com.crintsoft.zikiplayer.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.crintsoft.zikiplayer.data.room.entities.ExcludedSongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExcludedSongDao {

    @Query("SELECT * FROM excluded_songs")
    suspend fun getAllExcludedSongs(): List<ExcludedSongEntity>

    @Query("SELECT * FROM excluded_songs")
    fun getAllExcludedSongsFlow(): Flow<List<ExcludedSongEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM excluded_songs WHERE id = :id OR stableKey = :stableKey OR (:audioUri IS NOT NULL AND audioUri = :audioUri) LIMIT 1)")
    suspend fun isExcluded(id: String, stableKey: String, audioUri: String?): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExcludedSong(excluded: ExcludedSongEntity)

    @Query("DELETE FROM excluded_songs WHERE id = :id")
    suspend fun removeExcludedSong(id: String): Int

    @Query("DELETE FROM excluded_songs WHERE stableKey = :stableKey")
    suspend fun removeExcludedByStableKey(stableKey: String): Int

    @Query("SELECT COUNT(*) FROM excluded_songs")
    suspend fun count(): Int
}
