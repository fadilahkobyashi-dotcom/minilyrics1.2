package com.crintsoft.zikiplayer.ui

import android.app.Application
import android.content.ContentUris
import android.content.IntentSender
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.crintsoft.zikiplayer.data.db.MiniLyricsDbHelper
import com.crintsoft.zikiplayer.data.model.StorageLocationType
import com.crintsoft.zikiplayer.data.repository.MiniLyricsRepository
import com.crintsoft.zikiplayer.data.room.MiniLyricsRoomDatabase
import com.crintsoft.zikiplayer.data.room.RoomMigrationHelper
import com.crintsoft.zikiplayer.data.room.entities.SongEntity
import com.crintsoft.zikiplayer.lyrics.DefaultLyrics
import com.crintsoft.zikiplayer.lyrics.LrcParser
import com.crintsoft.zikiplayer.lyrics.LyricsSearchResult
import com.crintsoft.zikiplayer.lyrics.LyricsSearchService
import com.crintsoft.zikiplayer.lyrics.LyricsStorageManager
import com.crintsoft.zikiplayer.lyrics.StorageDirectoryStatus
import com.crintsoft.zikiplayer.media.AudioFileDeletionHelper
import com.crintsoft.zikiplayer.media.ArtworkStorageManager
import com.crintsoft.zikiplayer.media.DeletionResult
import com.crintsoft.zikiplayer.model.LyricType
import com.crintsoft.zikiplayer.model.Playlist
import com.crintsoft.zikiplayer.model.Track
import com.crintsoft.zikiplayer.player.MusicPlaybackManager
import com.crintsoft.zikiplayer.ui.theme.DarkBackground
import com.crintsoft.zikiplayer.ui.theme.HighlightGold
import com.crintsoft.zikiplayer.ui.theme.LowlightGray
import com.crintsoft.zikiplayer.ui.theme.LyricsDisplayPreferences
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MainDestination {
    LIBRARY,
    NOW_PLAYING_LYRICS,
    ONLINE_LYRICS_SEARCH,
    PREFERENCES
}

enum class LibraryTab {
    ARTISTS,
    ALBUMS,
    SONGS,
    PLAYLISTS
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "MainViewModel"

    // Room Database & Architecture Components
    val roomDatabase = MiniLyricsRoomDatabase.getInstance(application)
    val repository = MiniLyricsRepository(roomDatabase)
    val artworkStorageManager = ArtworkStorageManager(application)
    val audioDeletionHelper = AudioFileDeletionHelper(application)

    // Legacy helper kept for backward compatibility and app settings
    val dbHelper = MiniLyricsDbHelper(application)
    val lyricsStorageManager = LyricsStorageManager(application, dbHelper)
    val playbackManager = MusicPlaybackManager(application)

    private val _currentDestination = MutableStateFlow(MainDestination.LIBRARY)
    val currentDestination: StateFlow<MainDestination> = _currentDestination.asStateFlow()

    private val _currentLibraryTab = MutableStateFlow(LibraryTab.SONGS)
    val currentLibraryTab: StateFlow<LibraryTab> = _currentLibraryTab.asStateFlow()

    private val _allTracks = MutableStateFlow<List<Track>>(emptyList())
    val allTracks: StateFlow<List<Track>> = _allTracks.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _librarySearchQuery = MutableStateFlow("")
    val librarySearchQuery: StateFlow<String> = _librarySearchQuery.asStateFlow()

    private val _lyricsPreferences = MutableStateFlow(LyricsDisplayPreferences())
    val lyricsPreferences: StateFlow<LyricsDisplayPreferences> = _lyricsPreferences.asStateFlow()

    // Storage Status
    private val _storageStatus = MutableStateFlow(lyricsStorageManager.getStorageStatus())
    val storageStatus: StateFlow<StorageDirectoryStatus> = _storageStatus.asStateFlow()

    private val _lastSavedMessage = MutableStateFlow<String?>(null)
    val lastSavedMessage: StateFlow<String?> = _lastSavedMessage.asStateFlow()

    // Online lyrics search state
    private val _onlineSearchQuery = MutableStateFlow("")
    val onlineSearchQuery: StateFlow<String> = _onlineSearchQuery.asStateFlow()

    private val _isSearchingOnline = MutableStateFlow(false)
    val isSearchingOnline: StateFlow<Boolean> = _isSearchingOnline.asStateFlow()

    private val _onlineResults = MutableStateFlow<List<LyricsSearchResult>>(emptyList())
    val onlineResults: StateFlow<List<LyricsSearchResult>> = _onlineResults.asStateFlow()

    private val _onlineError = MutableStateFlow<String?>(null)
    val onlineError: StateFlow<String?> = _onlineError.asStateFlow()

    // Dialog state for Song Metadata Editor and Song Deletion
    private val _editingTrack = MutableStateFlow<Track?>(null)
    val editingTrack: StateFlow<Track?> = _editingTrack.asStateFlow()

    private val _deletingTrack = MutableStateFlow<Track?>(null)
    val deletingTrack: StateFlow<Track?> = _deletingTrack.asStateFlow()

    // Scoped storage deletion authorization state
    private val _deleteIntentSenderRequest = MutableStateFlow<IntentSender?>(null)
    val deleteIntentSenderRequest: StateFlow<IntentSender?> = _deleteIntentSenderRequest.asStateFlow()
    private var pendingDeletedTrack: Track? = null

    init {
        // Wire lyricsResolver to check permanent disk storage & DB association
        playbackManager.lyricsResolver = { track ->
            lyricsStorageManager.loadSavedLyricsForTrack(track)
        }

        // Link allTracks provider to playback manager so Next Track never gets stuck
        playbackManager.allTracksProvider = { _allTracks.value }

        // Load persistent preferences
        loadSavedPreferences()

        // Initialize Room data flow and initial tracks
        initializeDatabaseAndObservations()

        // Scan device audio with exclusion filtering
        scanDeviceAudio()
    }

    private fun initializeDatabaseAndObservations() {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Migrate legacy SQLite if needed
            RoomMigrationHelper.migrateFromSqliteIfNeeded(getApplication(), roomDatabase)

            // 2. Ensure default demo tracks exist in Room (respecting any user deletions)
            val defaultTracks = DefaultLyrics.getDefaultTracks()
            repository.ensureInitialTracks(defaultTracks)

            // 3. Ensure initial playlists exist in Room
            val existingPls = repository.getAllPlaylistsList()
            if (existingPls.isEmpty()) {
                val pl1Id = repository.createPlaylist("Favorites")
                val pl2Id = repository.createPlaylist("Recently Added")
                val trackMap = defaultTracks.associateBy { it.id }

                listOf("demo_starlight", "demo_cyberpulse").forEach { id ->
                    trackMap[id]?.let { repository.addSongToPlaylist(pl1Id, it.id) }
                }
                listOf("demo_starlight", "demo_cosmic_lullaby", "demo_cyberpulse", "demo_txt_horizon").forEach { id ->
                    trackMap[id]?.let { repository.addSongToPlaylist(pl2Id, it.id) }
                }
            }
        }

        // 4. Observe Room allSongs
        viewModelScope.launch {
            repository.allSongs.collectLatest { songEntities ->
                withContext(Dispatchers.IO) {
                    val tracks = songEntities.map { entity ->
                        var track = entity.toTrack()
                        track = attachSavedLyricsIfPresent(track)
                        track
                    }
                    withContext(Dispatchers.Main) {
                        _allTracks.value = tracks
                        if (playbackManager.currentTrack.value == null && tracks.isNotEmpty()) {
                            playbackManager.setTrackWithoutPlaying(tracks[0])
                        }
                    }
                }
            }
        }

        // 5. Observe Room allPlaylists
        viewModelScope.launch {
            repository.allPlaylists.collectLatest { playlistEntities ->
                withContext(Dispatchers.IO) {
                    val list = playlistEntities.map { entity ->
                        val songs = repository.getPlaylistSongs(entity.id)
                        Playlist(
                            id = entity.id,
                            name = entity.name,
                            trackIds = songs.map { it.songId }
                        )
                    }
                    withContext(Dispatchers.Main) {
                        _playlists.value = list
                    }
                }
            }
        }
    }

    fun navigateTo(dest: MainDestination) {
        _currentDestination.value = dest
    }

    fun setLibraryTab(tab: LibraryTab) {
        _currentLibraryTab.value = tab
    }

    fun setLibrarySearchQuery(query: String) {
        _librarySearchQuery.value = query
    }

    // =========================================================================
    // SONG METADATA & ALBUM ARTWORK EDITOR (ROOM PERMANENT STORAGE)
    // =========================================================================

    fun startEditingSong(track: Track) {
        _editingTrack.value = track
    }

    fun dismissEditingSong() {
        _editingTrack.value = null
    }

    fun startDeletingSong(track: Track) {
        _deletingTrack.value = track
    }

    fun dismissDeletingSong() {
        _deletingTrack.value = null
    }

    fun clearDeleteIntentSenderRequest() {
        _deleteIntentSenderRequest.value = null
    }

    /**
     * Updates song metadata and optionally replaces or removes album artwork.
     * Permanently commits changes to Room database and filesystem.
     */
    fun updateSongMetadata(
        updatedTrack: Track,
        newArtworkUri: Uri? = null,
        removeArtwork: Boolean = false
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var finalArtworkPath: String? = updatedTrack.artworkUri

                if (removeArtwork) {
                    artworkStorageManager.deleteArtworkForSong(updatedTrack.id)
                    repository.deleteArtwork(updatedTrack.id)
                    finalArtworkPath = null
                } else if (newArtworkUri != null) {
                    val saved = artworkStorageManager.saveArtworkFromUri(updatedTrack.id, newArtworkUri)
                    if (saved != null) {
                        repository.saveArtwork(updatedTrack.id, saved)
                        finalArtworkPath = saved
                    }
                }

                val finalTrack = updatedTrack.copy(artworkUri = finalArtworkPath)
                val entity = SongEntity.fromTrack(finalTrack)
                repository.updateSongMetadata(entity)

                withContext(Dispatchers.Main) {
                    // Update currently playing track if active
                    val current = playbackManager.currentTrack.value
                    if (current != null && current.id == finalTrack.id) {
                        playbackManager.updateMetadataForCurrentTrack(finalTrack)
                    }
                    _lastSavedMessage.value = "Updated metadata for \"${finalTrack.title}\""
                    dismissEditingSong()
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to update song metadata", e)
                withContext(Dispatchers.Main) {
                    _onlineError.value = "Failed to save metadata: ${e.message}"
                }
            }
        }
    }

    /**
     * Deletes a song permanently from MiniLyrics.
     *
     * Option A (deletePhysicalFile = false): "Remove from MiniLyrics"
     *   - Removes song and its records from Room database.
     *   - Adds persistent exclusion record so future scans never re-add it.
     *   - Leaves actual audio file intact in storage.
     *
     * Option B (deletePhysicalFile = true): "Permanently Delete from Internal Storage"
     *   - Physically deletes audio file using Android Storage APIs (with Scoped Storage consent if required).
     *   - Verifies physical deletion occurred before updating records.
     *   - Cleans up Room database and associations.
     *   - Adds persistent exclusion record so it can never reappear.
     */
    fun deleteSong(track: Track, deletePhysicalFile: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // If currently playing, skip to next song
                withContext(Dispatchers.Main) {
                    if (playbackManager.currentTrack.value?.id == track.id) {
                        playbackManager.nextTrack()
                    }
                }

                if (!deletePhysicalFile) {
                    // =========================================================
                    // OPTION A: REMOVE FROM MINILYRICS ONLY
                    // =========================================================
                    // 1. Delete from Room library and register persistent exclusion
                    repository.deleteSong(
                        songId = track.id,
                        audioUri = track.audioUri,
                        title = track.title,
                        artist = track.artist,
                        reason = "USER_REMOVED"
                    )

                    // 2. Clean up local lyrics and artwork associations
                    lyricsStorageManager.deleteSavedLyricsForTrack(track)
                    artworkStorageManager.deleteArtworkForSong(track.id)

                    withContext(Dispatchers.Main) {
                        _lastSavedMessage.value = "Removed \"${track.title}\" from MiniLyrics"
                        dismissDeletingSong()
                    }
                } else {
                    // =========================================================
                    // OPTION B: PERMANENTLY DELETE FROM INTERNAL STORAGE
                    // =========================================================
                    if (track.isDemo) {
                        // Demo tracks are read-only APK resources
                        repository.deleteSong(
                            songId = track.id,
                            audioUri = track.audioUri,
                            title = track.title,
                            artist = track.artist,
                            reason = "USER_REMOVED"
                        )
                        withContext(Dispatchers.Main) {
                            _lastSavedMessage.value = "Demo track removed from MiniLyrics library"
                            dismissDeletingSong()
                        }
                        return@launch
                    }

                    // Attempt physical deletion with verification
                    val deletionResult = audioDeletionHelper.deletePhysicalAudio(track.audioUri)

                    when (deletionResult) {
                        is DeletionResult.Success -> {
                            // File confirmed deleted from disk
                            repository.deleteSong(
                                songId = track.id,
                                audioUri = track.audioUri,
                                title = track.title,
                                artist = track.artist,
                                reason = "PERMANENTLY_DELETED"
                            )
                            lyricsStorageManager.deleteSavedLyricsForTrack(track)
                            artworkStorageManager.deleteArtworkForSong(track.id)

                            withContext(Dispatchers.Main) {
                                _lastSavedMessage.value = "Permanently deleted \"${track.title}\" from internal storage"
                                dismissDeletingSong()
                            }
                        }
                        is DeletionResult.RequiresUserConsent -> {
                            // Android Scoped Storage requires system consent popup
                            pendingDeletedTrack = track
                            withContext(Dispatchers.Main) {
                                _deleteIntentSenderRequest.value = deletionResult.intentSender
                                dismissDeletingSong()
                            }
                        }
                        is DeletionResult.Error -> {
                            // Deletion failed: do NOT falsely report success or remove from library
                            Log.e(tag, "Physical deletion error: ${deletionResult.message}")
                            withContext(Dispatchers.Main) {
                                _onlineError.value = "Deletion failed: ${deletionResult.message}"
                                dismissDeletingSong()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to process song deletion", e)
                withContext(Dispatchers.Main) {
                    _onlineError.value = "Failed to delete song: ${e.message}"
                    dismissDeletingSong()
                }
            }
        }
    }

    /**
     * Called when the user confirms Android system Scoped Storage deletion request.
     */
    fun onPermanentDeleteConsentGranted() {
        val track = pendingDeletedTrack ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSong(
                songId = track.id,
                audioUri = track.audioUri,
                title = track.title,
                artist = track.artist,
                reason = "PERMANENTLY_DELETED"
            )
            lyricsStorageManager.deleteSavedLyricsForTrack(track)
            artworkStorageManager.deleteArtworkForSong(track.id)
            pendingDeletedTrack = null

            withContext(Dispatchers.Main) {
                _lastSavedMessage.value = "Permanently deleted \"${track.title}\" from internal storage"
            }
        }
    }

    /**
     * Called when the user denies Android system Scoped Storage deletion request.
     */
    fun onPermanentDeleteConsentDenied() {
        pendingDeletedTrack = null
        _onlineError.value = "Permanent deletion cancelled: Storage permission not granted"
    }

    // =========================================================================
    // PREFERENCES PERSISTENCE
    // =========================================================================

    private fun loadSavedPreferences() {
        val keepScreenOn = dbHelper.getSetting("pref_keep_screen_on", "true").toBoolean()
        val fontSizeSp = dbHelper.getSetting("pref_font_size", "18.0").toFloatOrNull() ?: 18f
        val alignName = dbHelper.getSetting("pref_text_align", "Center")
        val textAlign = when (alignName) {
            "Start" -> TextAlign.Start
            "End" -> TextAlign.End
            else -> TextAlign.Center
        }
        val highlightColorLong = dbHelper.getSetting("pref_highlight_color", HighlightGold.value.toString()).toULongOrNull()
        val lowlightColorLong = dbHelper.getSetting("pref_lowlight_color", LowlightGray.value.toString()).toULongOrNull()
        val bgColorLong = dbHelper.getSetting("pref_bg_color", DarkBackground.value.toString()).toULongOrNull()

        _lyricsPreferences.value = LyricsDisplayPreferences(
            fontSize = fontSizeSp.sp,
            textAlign = textAlign,
            highlightColor = if (highlightColorLong != null) Color(highlightColorLong) else HighlightGold,
            lowlightColor = if (lowlightColorLong != null) Color(lowlightColorLong) else LowlightGray,
            backgroundColor = if (bgColorLong != null) Color(bgColorLong) else DarkBackground,
            keepScreenOn = keepScreenOn
        )
    }

    fun updatePreferences(transform: (LyricsDisplayPreferences) -> LyricsDisplayPreferences) {
        val updated = transform(_lyricsPreferences.value)
        _lyricsPreferences.value = updated

        // Save immediately
        viewModelScope.launch(Dispatchers.IO) {
            dbHelper.setSetting("pref_keep_screen_on", updated.keepScreenOn.toString())
            dbHelper.setSetting("pref_font_size", updated.fontSize.value.toString())
            dbHelper.setSetting("pref_text_align", when (updated.textAlign) {
                TextAlign.Start -> "Start"
                TextAlign.End -> "End"
                else -> "Center"
            })
            dbHelper.setSetting("pref_highlight_color", updated.highlightColor.value.toString())
            dbHelper.setSetting("pref_lowlight_color", updated.lowlightColor.value.toString())
            dbHelper.setSetting("pref_bg_color", updated.backgroundColor.value.toString())
        }
    }

    // =========================================================================
    // STORAGE LOCATION SELECTION (SAF & INTERNAL)
    // =========================================================================

    fun setStorageLocationType(type: StorageLocationType) {
        lyricsStorageManager.setStorageLocationType(type)
        refreshStorageStatus()
    }

    fun setCustomStorageUri(uri: Uri) {
        val success = lyricsStorageManager.setCustomStorageUri(uri)
        if (success) {
            _lastSavedMessage.value = "Custom lyrics directory verified & configured"
        } else {
            _onlineError.value = "Selected folder is not writable. Kept internal storage."
        }
        refreshStorageStatus()
    }

    fun refreshStorageStatus() {
        _storageStatus.value = lyricsStorageManager.getStorageStatus()
    }

    fun clearLastSavedMessage() {
        _lastSavedMessage.value = null
    }

    // =========================================================================
    // ROOM PLAYLIST MANAGEMENT (EXACT SEQUENTIAL ORDER PRESERVED)
    // =========================================================================

    fun createPlaylist(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            repository.createPlaylist(trimmed)
            dbHelper.createPlaylist("pl_${System.currentTimeMillis()}", trimmed)
        }
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            repository.renamePlaylist(playlistId, trimmed)
            dbHelper.renamePlaylist(playlistId, trimmed)
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deletePlaylist(playlistId)
            dbHelper.deletePlaylist(playlistId)
        }
    }

    fun addTrackToPlaylist(playlistId: String, track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addSongToPlaylist(playlistId, track.id)
            dbHelper.addTrackToPlaylist(playlistId, track)
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeSongFromPlaylist(playlistId, trackId)
            dbHelper.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    /**
     * Preserves exact customized order when reordering tracks within a playlist.
     */
    fun moveTrackInPlaylist(playlistId: String, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.moveSongInPlaylist(playlistId, fromIndex, toIndex)
            dbHelper.moveTrackInPlaylist(playlistId, fromIndex, toIndex)
        }
    }

    // =========================================================================
    // TRACKS AND PERMANENT LYRICS RESTORATION
    // =========================================================================

    private fun attachSavedLyricsIfPresent(track: Track): Track {
        val saved = lyricsStorageManager.loadSavedLyricsForTrack(track)
        return if (saved != null) {
            if (saved.first == LyricType.LRC) {
                track.copy(lrcContent = saved.second, txtContent = null)
            } else {
                track.copy(txtContent = saved.second, lrcContent = null)
            }
        } else {
            track
        }
    }

    /**
     * Plays a track with an explicit queue context (Songs, Artists, Albums, or Playlists).
     */
    fun playTrack(track: Track, currentQueue: List<Track>? = _allTracks.value) {
        val resolvedTrack = attachSavedLyricsIfPresent(track)
        val q = currentQueue ?: _allTracks.value
        playbackManager.playTrack(resolvedTrack, q)
        navigateTo(MainDestination.NOW_PLAYING_LYRICS)
    }

    fun playArtist(artistTracks: List<Track>, startIndex: Int = 0) {
        if (artistTracks.isEmpty()) return
        val resolved = artistTracks.map { attachSavedLyricsIfPresent(it) }
        playbackManager.setQueue(resolved, startIndex)
        navigateTo(MainDestination.NOW_PLAYING_LYRICS)
    }

    fun playAlbum(albumTracks: List<Track>, startIndex: Int = 0) {
        if (albumTracks.isEmpty()) return
        val resolved = albumTracks.map { attachSavedLyricsIfPresent(it) }
        playbackManager.setQueue(resolved, startIndex)
        navigateTo(MainDestination.NOW_PLAYING_LYRICS)
    }

    /**
     * Explicitly adds an audio file into the library.
     * If the song was previously marked as excluded, the explicit user import clears the exclusion.
     */
    fun addCustomTrack(uri: Uri, title: String, artist: String, durationMs: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val songId = "user_${System.currentTimeMillis()}"
            val cleanTitle = title.ifBlank { "Custom Track" }
            val cleanArtist = artist.ifBlank { "Unknown Artist" }

            // Clear any prior exclusion for this explicitly imported song
            repository.removeSongFromExclusion(songId, cleanArtist, cleanTitle)

            var newTrack = Track(
                id = songId,
                title = cleanTitle,
                artist = cleanArtist,
                album = "Local Storage",
                durationMs = durationMs,
                audioUri = uri.toString(),
                lrcContent = null,
                txtContent = null,
                isDemo = false
            )
            newTrack = attachSavedLyricsIfPresent(newTrack)
            val entity = SongEntity.fromTrack(newTrack)
            repository.insertOrUpdateSong(entity)

            withContext(Dispatchers.Main) {
                playTrack(newTrack)
            }
        }
    }

    /**
     * Scans device MediaStore audio files.
     * Strictly verifies newly discovered files against persistent exclusion records.
     * Songs that were previously removed or permanently deleted by the user will NEVER be re-added.
     */
    fun scanDeviceAudio() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Load all persistent exclusions
                val excludedList = repository.getAllExcludedSongs()
                val excludedUris = excludedList.mapNotNull { it.audioUri }.toSet()
                val excludedKeys = excludedList.map { it.stableKey }.toSet()
                val excludedIds = excludedList.map { it.id }.toSet()

                val context = getApplication<Application>()
                val resolver = context.contentResolver
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION
                )
                val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
                val cursor = resolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    null,
                    "${MediaStore.Audio.Media.TITLE} ASC"
                )

                val scannedEntities = mutableListOf<SongEntity>()
                cursor?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        val title = c.getString(titleCol) ?: "Unknown"
                        val artist = c.getString(artistCol) ?: "Unknown Artist"
                        val album = c.getString(albumCol) ?: "Unknown Album"
                        val duration = c.getLong(durCol)
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id
                        )

                        val songId = "ms_$id"
                        val contentUriStr = contentUri.toString()
                        val stableKey = repository.normalizeKey(artist, title)

                        // 2. Persistent Exclusion Check: Skip if intentionally removed or deleted!
                        if (excludedIds.contains(songId) ||
                            excludedUris.contains(contentUriStr) ||
                            excludedKeys.contains(stableKey)
                        ) {
                            continue
                        }

                        scannedEntities.add(
                            SongEntity(
                                id = songId,
                                title = title,
                                artist = artist,
                                album = album,
                                durationMs = duration,
                                audioUri = contentUriStr,
                                isDemo = false
                            )
                        )
                    }
                }

                if (scannedEntities.isNotEmpty()) {
                    repository.insertSongs(scannedEntities)
                }
            } catch (e: Exception) {
                // MediaStore permission might not be granted yet
                Log.w(tag, "Audio scan encountered notice: ${e.message}")
            }
        }
    }

    // =========================================================================
    // SAVING LYRICS (PHYSICAL FILE CREATION & VERIFICATION)
    // =========================================================================

    fun searchOnlineLyrics(query: String) {
        _onlineSearchQuery.value = query
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return

        _isSearchingOnline.value = true
        _onlineError.value = null
        _onlineResults.value = emptyList()

        viewModelScope.launch {
            val result = LyricsSearchService.searchLyrics(trimmed)
            _isSearchingOnline.value = false
            result.onSuccess { list ->
                _onlineResults.value = list
                if (list.isEmpty()) {
                    _onlineError.value = "No lyrics found for \"$trimmed\""
                }
            }.onFailure { err ->
                _onlineError.value = "Network error: ${err.message ?: "Unable to reach server"}"
            }
        }
    }

    /**
     * Downloads/applies lyrics result, writes physical file to storage,
     * verifies file exists and is readable, and updates Room and SQLite DB associations.
     */
    fun applyLyricsResult(result: LyricsSearchResult) {
        val curTrack = playbackManager.currentTrack.value ?: return
        val text = result.syncedLyrics ?: result.plainLyrics ?: return
        val format = if (result.isSynced) LyricType.LRC else LyricType.TXT

        viewModelScope.launch(Dispatchers.IO) {
            val saveResult = lyricsStorageManager.saveLyricsFile(
                track = curTrack,
                content = text,
                format = format,
                source = "ONLINE_SEARCH"
            )

            withContext(Dispatchers.Main) {
                saveResult.onSuccess { saved ->
                    playbackManager.updateLyricsForCurrentTrack(text)
                    _lastSavedMessage.value = "Saved ${saved.fileName} (${saved.storageType})"
                    refreshStorageStatus()
                    navigateTo(MainDestination.NOW_PLAYING_LYRICS)
                }.onFailure { err ->
                    _onlineError.value = "Failed to save lyrics file: ${err.message}"
                }
            }
        }
    }

    /**
     * Imports a .lrc or .txt file, copies/saves it to active storage, and updates DB association.
     */
    fun importLyricsFile(uri: Uri) {
        val curTrack = playbackManager.currentTrack.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                val content = reader.readText()
                reader.close()
                inputStream.close()

                if (content.isNotBlank()) {
                    val format = LrcParser.detectLyricType(content)
                    val saveResult = lyricsStorageManager.saveLyricsFile(
                        track = curTrack,
                        content = content,
                        format = format,
                        source = "MANUAL_IMPORT"
                    )

                    withContext(Dispatchers.Main) {
                        saveResult.onSuccess { saved ->
                            playbackManager.updateLyricsForCurrentTrack(content)
                            _lastSavedMessage.value = "Imported & saved ${saved.fileName} to ${saved.storageType}"
                            refreshStorageStatus()
                        }.onFailure { err ->
                            _onlineError.value = "Failed to write imported lyrics: ${err.message}"
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _onlineError.value = "Failed to read lyrics file: ${e.message}"
                }
            }
        }
    }

    /**
     * Applies manual/pasted lyrics, writes file to disk, and updates DB association.
     */
    fun applyManualLyrics(content: String) {
        val curTrack = playbackManager.currentTrack.value ?: return
        if (content.isBlank()) return
        val format = LrcParser.detectLyricType(content)

        viewModelScope.launch(Dispatchers.IO) {
            val saveResult = lyricsStorageManager.saveLyricsFile(
                track = curTrack,
                content = content,
                format = format,
                source = "PASTED"
            )

            withContext(Dispatchers.Main) {
                saveResult.onSuccess { saved ->
                    playbackManager.updateLyricsForCurrentTrack(content)
                    _lastSavedMessage.value = "Lyrics saved as ${saved.fileName} (${saved.storageType})"
                    refreshStorageStatus()
                    navigateTo(MainDestination.NOW_PLAYING_LYRICS)
                }.onFailure { err ->
                    _onlineError.value = "Failed to write lyrics: ${err.message}"
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playbackManager.release()
        dbHelper.close()
    }
}
