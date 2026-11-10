package com.crintsoft.zikiplayer.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.crintsoft.zikiplayer.data.room.entities.ArtworkEntity

@Dao
interface ArtworkDao {

    @Query("SELECT * FROM artworks WHERE songId = :songId LIMIT 1")
    suspend fun getArtworkForSong(songId: String): ArtworkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceArtwork(artwork: ArtworkEntity): Long

    @Query("DELETE FROM artworks WHERE songId = :songId")
    suspend fun deleteArtworkForSong(songId: String): Int
}
