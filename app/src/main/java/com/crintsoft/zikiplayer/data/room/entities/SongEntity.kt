package com.crintsoft.zikiplayer.data.room.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.crintsoft.zikiplayer.model.Track

@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["artist"]),
        Index(value = ["album"]),
        Index(value = ["title"])
    ]
)
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String = "",
    val genre: String = "",
    val releaseYear: String = "",
    val trackNumber: String = "",
    val discNumber: String = "",
    val composer: String = "",
    val comment: String = "",
    val durationMs: Long = 0L,
    val audioUri: String? = null,
    val artworkUri: String? = null, // Local persistent file path or content URI
    val rawResId: Int? = null,
    val isDemo: Boolean = false,
    val isEdited: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toTrack(lrcContent: String? = null, txtContent: String? = null): Track {
        return Track(
            id = id,
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            genre = genre,
            releaseYear = releaseYear,
            trackNumber = trackNumber,
            discNumber = discNumber,
            composer = composer,
            comment = comment,
            durationMs = durationMs,
            audioUri = audioUri,
            artworkUri = artworkUri,
            rawResId = rawResId,
            lrcContent = lrcContent,
            txtContent = txtContent,
            isDemo = isDemo,
            isEdited = isEdited
        )
    }

    companion object {
        fun fromTrack(track: Track): SongEntity {
            return SongEntity(
                id = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                albumArtist = track.albumArtist,
                genre = track.genre,
                releaseYear = track.releaseYear,
                trackNumber = track.trackNumber,
                discNumber = track.discNumber,
                composer = track.composer,
                comment = track.comment,
                durationMs = track.durationMs,
                audioUri = track.audioUri,
                artworkUri = track.artworkUri,
                rawResId = track.rawResId,
                isDemo = track.isDemo,
                isEdited = track.isEdited
            )
        }
    }
}
