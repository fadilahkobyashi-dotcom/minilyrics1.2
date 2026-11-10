package com.crintsoft.zikiplayer.data.repository

import com.crintsoft.zikiplayer.data.room.MiniLyricsRoomDatabase
import com.crintsoft.zikiplayer.data.room.entities.ArtworkEntity
import com.crintsoft.zikiplayer.data.room.entities.ExcludedSongEntity
import com.crintsoft.zikiplayer.data.room.entities.LyricsEntity
import com.crintsoft.zikiplayer.data.room.entities.PlaylistEntity
import com.crintsoft.zikiplayer.data.room.entities.PlaylistSongEntity
import com.crintsoft.zikiplayer.data.room.entities.SongEntity
import com.crintsoft.zikiplayer.model.Track
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class MiniLyricsRepository(private val database: MiniLyricsRoomDatabase) {

    private val songDao = database.songDao()
    private val playlistDao = database.playlistDao()
    private val lyricsDao = database.lyricsDao()
    private val artworkDao = database.artworkDao()
    private val excludedSongDao = database.excludedSongDao()

    fun normalizeKey(artist: String, title: String): String {
        val cleanArtist = artist.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
        val cleanTitle = title.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
        return "${cleanArtist}:::${cleanTitle}"
    }

    // =========================================================================
    // SONGS
    // =========================================================================

    val allSongs: Flow<List<SongEntity>> = songDao.getAllSongs()

    suspend fun getAllSongsList(): List<SongEntity> = songDao.getAllSongsList()

    suspend fun getSongById(id: String): SongEntity? = songDao.getSongById(id)

    suspend fun insertOrUpdateSong(song: SongEntity) {
        songDao.insertSong(song)
    }

    suspend fun insertSongs(songs: List<SongEntity>) {
        songDao.insertSongs(songs)
    }

    suspend fun updateSongMetadata(song: SongEntity) {
        val editedSong = song.copy(isEdited = true, updatedAt = System.currentTimeMillis())
        val rows = songDao.updateSong(editedSong)
        if (rows == 0) {
            songDao.insertSong(editedSong)
        }
    }

    /**
     * Merges newly discovered audio files from MediaStore with persistent Room library.
     * Strictly enforces metadata priority:
     * 1. If song was manually edited (isEdited == true), keep ALL saved metadata!
     * 2. If song already has custom saved artwork, preserve the artwork!
     * 3. Truly new songs are inserted with isEdited = false.
     * 4. A library refresh or rescan will NEVER overwrite manually edited info.
     */
    suspend fun mergeScannedSongs(scannedSongs: List<SongEntity>) {
        if (scannedSongs.isEmpty()) return

        val existingList = songDao.getAllSongsList()
        val existingById = existingList.associateBy { it.id }
        val existingByUri = existingList.filter { !it.audioUri.isNullOrBlank() }.associateBy { it.audioUri!! }
        val existingByKey = existingList.associateBy { normalizeKey(it.artist, it.title) }

        val toInsert = mutableListOf<SongEntity>()
        val toUpdate = mutableListOf<SongEntity>()

        for (scanned in scannedSongs) {
            val key = normalizeKey(scanned.artist, scanned.title)
            val existing = existingById[scanned.id]
                ?: scanned.audioUri?.let { existingByUri[it] }
                ?: existingByKey[key]

            if (existing == null) {
                toInsert.add(scanned.copy(isEdited = false))
            } else {
                if (existing.isEdited) {
                    // CRITICAL: Manually edited metadata MUST NOT be overwritten by raw audio tags!
                    // Only update audioUri if changed/missing, and keep all edited fields intact.
                    var updated = existing
                    if (existing.audioUri.isNullOrBlank() && !scanned.audioUri.isNullOrBlank()) {
                        updated = updated.copy(audioUri = scanned.audioUri)
                    }
                    if (existing.durationMs <= 0L && scanned.durationMs > 0L) {
                        updated = updated.copy(durationMs = scanned.durationMs)
                    }
                    if (updated != existing) {
                        toUpdate.add(updated)
                    }
                } else {
                    // Unedited track: update metadata from MediaStore while preserving any custom saved artwork
                    val merged = scanned.copy(
                        id = existing.id,
                        artworkUri = existing.artworkUri ?: scanned.artworkUri,
                        createdAt = existing.createdAt,
                        isEdited = false
                    )
                    toUpdate.add(merged)
                }
            }
        }

        if (toInsert.isNotEmpty()) {
            songDao.insertSongs(toInsert)
        }
        for (item in toUpdate) {
            songDao.updateSong(item)
        }
    }

    suspend fun updateSongArtwork(songId: String, artworkUri: String?) {
        songDao.updateSongArtwork(songId, artworkUri)
    }

    suspend fun deleteSong(
        songId: String,
        audioUri: String? = null,
        title: String? = null,
        artist: String? = null,
        reason: String = "USER_REMOVED"
    ) {
        // 1. Record persistent exclusion so scanner and seed logic never re-add this song
        if (!title.isNullOrBlank() && !artist.isNullOrBlank()) {
            excludeSong(songId, audioUri, title, artist, reason)
        } else {
            // If title and artist were not supplied, attempt to look up from database before deleting
            val existing = songDao.getSongById(songId)
            if (existing != null) {
                excludeSong(songId, existing.audioUri, existing.title, existing.artist, reason)
            }
        }

        // 2. Remove from active library tables
        songDao.deleteSongById(songId)
        playlistDao.deletePlaylistSongsForSong(songId)
        lyricsDao.deleteLyricsBySongId(songId)
        artworkDao.deleteArtworkForSong(songId)
    }

    suspend fun ensureInitialTracks(defaultTracks: List<Track>) {
        val excluded = excludedSongDao.getAllExcludedSongs()
        val excludedIds = excluded.map { it.id }.toSet()
        val excludedKeys = excluded.map { it.stableKey }.toSet()

        val currentCount = songDao.count()
        if (currentCount == 0) {
            // Filter out any default tracks that were intentionally removed/excluded by user
            val allowed = defaultTracks.filter { track ->
                !excludedIds.contains(track.id) &&
                        !excludedKeys.contains(normalizeKey(track.artist, track.title))
            }
            if (allowed.isNotEmpty()) {
                val entities = allowed.map { SongEntity.fromTrack(it) }
                songDao.insertSongs(entities)
            }
        }
    }

    // =========================================================================
    // EXCLUSION MECHANISM (PREVENTS ACCIDENTAL RESTORATION ON RESCAN / REBOOT)
    // =========================================================================

    suspend fun excludeSong(
        songId: String,
        audioUri: String?,
        title: String,
        artist: String,
        reason: String = "USER_REMOVED"
    ) {
        val stableKey = normalizeKey(artist, title)
        excludedSongDao.insertExcludedSong(
            ExcludedSongEntity(
                id = songId,
                audioUri = audioUri,
                stableKey = stableKey,
                title = title,
                artist = artist,
                reason = reason,
                excludedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun isSongExcluded(songId: String, title: String, artist: String, audioUri: String?): Boolean {
        val stableKey = normalizeKey(artist, title)
        return excludedSongDao.isExcluded(id = songId, stableKey = stableKey, audioUri = audioUri)
    }

    suspend fun getAllExcludedSongs(): List<ExcludedSongEntity> {
        return excludedSongDao.getAllExcludedSongs()
    }

    suspend fun removeSongFromExclusion(songId: String, artist: String, title: String) {
        excludedSongDao.removeExcludedSong(songId)
        val stableKey = normalizeKey(artist, title)
        excludedSongDao.removeExcludedByStableKey(stableKey)
    }

    // =========================================================================
    // PLAYLISTS
    // =========================================================================

    val allPlaylists: Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()

    suspend fun getAllPlaylistsList(): List<PlaylistEntity> = playlistDao.getAllPlaylistsList()

    suspend fun getPlaylistById(id: String): PlaylistEntity? = playlistDao.getPlaylistById(id)

    fun getPlaylistSongsFlow(playlistId: String): Flow<List<PlaylistSongEntity>> =
        playlistDao.getPlaylistSongsFlow(playlistId)

    suspend fun getPlaylistSongs(playlistId: String): List<PlaylistSongEntity> =
        playlistDao.getPlaylistSongs(playlistId)

    suspend fun createPlaylist(name: String): String {
        val id = UUID.randomUUID().toString()
        val playlist = PlaylistEntity(
            id = id,
            name = name,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        playlistDao.insertPlaylist(playlist)
        return id
    }

    suspend fun renamePlaylist(id: String, newName: String) {
        playlistDao.updatePlaylistName(id, newName)
    }

    suspend fun deletePlaylist(id: String) {
        playlistDao.deletePlaylistSongsForPlaylist(id)
        playlistDao.deletePlaylist(id)
    }

    suspend fun addSongToPlaylist(playlistId: String, songId: String) {
        // Prevent duplicate addition to same playlist
        val existing = playlistDao.getPlaylistSongs(playlistId)
        if (existing.none { it.songId == songId }) {
            playlistDao.addSongToEndOfPlaylist(playlistId, songId)
        }
    }

    suspend fun removeSongFromPlaylist(playlistId: String, songId: String) {
        playlistDao.removeSongAndCompact(playlistId, songId)
    }

    suspend fun moveSongInPlaylist(playlistId: String, fromIndex: Int, toIndex: Int): Boolean {
        return playlistDao.moveSong(playlistId, fromIndex, toIndex)
    }

    suspend fun reorderPlaylist(playlistId: String, orderedSongIds: List<String>) {
        playlistDao.reorderPlaylistSongs(playlistId, orderedSongIds)
    }

    // =========================================================================
    // LYRICS
    // =========================================================================

    suspend fun saveLyrics(lyrics: LyricsEntity): Long {
        return lyricsDao.insertOrReplaceLyrics(lyrics)
    }

    suspend fun findLyrics(songId: String, title: String, artist: String, musicFileRef: String = ""): LyricsEntity? {
        val stableKey = normalizeKey(artist, title)
        return lyricsDao.findLyrics(songId = songId, stableKey = stableKey, musicFileRef = musicFileRef)
    }

    suspend fun getLyricsBySongId(songId: String): LyricsEntity? {
        return lyricsDao.getLyricsBySongId(songId)
    }

    suspend fun deleteLyrics(songId: String) {
        lyricsDao.deleteLyricsBySongId(songId)
    }

    // =========================================================================
    // ARTWORKS
    // =========================================================================

    suspend fun getArtworkForSong(songId: String): ArtworkEntity? {
        return artworkDao.getArtworkForSong(songId)
    }

    suspend fun saveArtwork(songId: String, artworkFilePath: String) {
        artworkDao.insertOrReplaceArtwork(
            ArtworkEntity(
                songId = songId,
                artworkFilePath = artworkFilePath,
                updatedAt = System.currentTimeMillis()
            )
        )
        songDao.updateSongArtwork(songId, artworkFilePath)
    }

    suspend fun deleteArtwork(songId: String) {
        artworkDao.deleteArtworkForSong(songId)
        songDao.updateSongArtwork(songId, null)
    }
}
