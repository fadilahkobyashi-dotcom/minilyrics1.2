package com.crintsoft.zikiplayer.data.room.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lyrics_records",
    indices = [
        Index(value = ["songId"]),
        Index(value = ["stableKey"], unique = true)
    ]
)
data class LyricsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val stableKey: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val musicFileRef: String = "",
    val lyricsFormat: String, // "LRC" or "TXT"
    val lyricsFilePath: String, // Absolute filesystem path or content document URI
    val storageType: String, // "INTERNAL" or "CUSTOM_SAF"
    val lyricsSource: String = "",
    val fileSizeBytes: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
