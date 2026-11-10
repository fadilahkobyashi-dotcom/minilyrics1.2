package com.crintsoft.zikiplayer.data.room.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "excluded_songs",
    indices = [
        Index(value = ["audioUri"]),
        Index(value = ["stableKey"])
    ]
)
data class ExcludedSongEntity(
    @PrimaryKey val id: String, // songId or stableKey
    val audioUri: String? = null,
    val stableKey: String, // normalized "artist:::title"
    val title: String,
    val artist: String,
    val reason: String = "USER_REMOVED", // "USER_REMOVED" or "PERMANENTLY_DELETED"
    val excludedAt: Long = System.currentTimeMillis()
)
