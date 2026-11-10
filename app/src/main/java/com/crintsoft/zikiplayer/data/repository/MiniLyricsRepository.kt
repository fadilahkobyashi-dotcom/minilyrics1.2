package com.crintsoft.zikiplayer.data.repository

import com.crintsoft.zikiplayer.data.room.MiniLyricsRoomDatabase
import com.crintsoft.zikiplayer.data.room.entities.ArtworkEntity
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
        songDao.updateSong(song)
    }

    suspend fun updateSongArtwork(songId: String, artworkUri: String?) {
        songDao.updateSongArtwork(songId, artworkUri)
    }

    suspend fun deleteSong(songId: String) {
        songDao.deleteSongById(songId)
        playlistDao.deletePlaylistSongsForSong(songId)
        lyricsDao.deleteLyricsBySongId(songId)
        artworkDao.deleteArtworkForSong(songId)
    }

    suspend fun ensureInitialTracks(defaultTracks: List<Track>) {
        val currentCount = songDao.count()
        if (currentCount == 0) {
            val entities = defaultTracks.map { SongEntity.fromTrack(it) }
            songDao.insertSongs(entities)
        }
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
        val cleanArtist = artist.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
        val cleanTitle = title.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
        val stableKey = "${cleanArtist}:::${cleanTitle}"
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
