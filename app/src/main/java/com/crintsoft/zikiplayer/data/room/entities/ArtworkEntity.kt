package com.crintsoft.zikiplayer.data.room.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "artworks",
    indices = [
        Index(value = ["songId"], unique = true)
    ]
)
data class ArtworkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val artworkFilePath: String, // Absolute filesystem path to saved artwork image
    val mimeType: String = "image/jpeg",
    val updatedAt: Long = System.currentTimeMillis()
)
