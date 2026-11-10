package com.crintsoft.zikiplayer.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.crintsoft.zikiplayer.data.model.LyricsAssociation
import com.crintsoft.zikiplayer.model.Playlist
import com.crintsoft.zikiplayer.model.Track

class MiniLyricsDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val TAG = "MiniLyricsDbHelper"
        const val DATABASE_NAME = "minilyrics.db"
        const val DATABASE_VERSION = 1

        // Table: Lyrics Associations
        const val TABLE_LYRICS = "lyrics_associations"
        const val COL_LYRICS_ID = "id"
        const val COL_LYRICS_SONG_ID = "song_id"
        const val COL_LYRICS_STABLE_KEY = "stable_key"
        const val COL_LYRICS_TITLE = "title"
        const val COL_LYRICS_ARTIST = "artist"
        const val COL_LYRICS_ALBUM = "album"
        const val COL_LYRICS_MUSIC_FILE_REF = "music_file_ref"
        const val COL_LYRICS_FORMAT = "lyrics_format"
        const val COL_LYRICS_FILE_PATH = "lyrics_file_path"
        const val COL_LYRICS_STORAGE_TYPE = "storage_type"
        const val COL_LYRICS_SOURCE = "lyrics_source"
        const val COL_LYRICS_FILE_SIZE = "file_size_bytes"
        const val COL_LYRICS_CREATED_AT = "created_at"
        const val COL_LYRICS_UPDATED_AT = "updated_at"

        // Table: Playlists
        const val TABLE_PLAYLISTS = "playlists"
        const val COL_PL_ID = "id"
        const val COL_PL_NAME = "name"
        const val COL_PL_CREATED_AT = "created_at"
        const val COL_PL_UPDATED_AT = "updated_at"

        // Table: Playlist Tracks (Preserves exact ordering)
        const val TABLE_PLAYLIST_TRACKS = "playlist_tracks"
        const val COL_PT_ID = "id"
        const val COL_PT_PLAYLIST_ID = "playlist_id"
        const val COL_PT_TRACK_ID = "track_id"
        const val COL_PT_TITLE = "track_title"
        const val COL_PT_ARTIST = "track_artist"
        const val COL_PT_ALBUM = "track_album"
        const val COL_PT_AUDIO_URI = "track_audio_uri"
        const val COL_PT_DURATION = "track_duration_ms"
        const val COL_PT_ORDER = "order_index"

        // Table: Settings
        const val TABLE_SETTINGS = "app_settings"
        const val COL_SETTING_KEY = "setting_key"
        const val COL_SETTING_VALUE = "setting_value"
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Create Lyrics Associations Table
        db.execSQL(
            """
            CREATE TABLE $TABLE_LYRICS (
                $COL_LYRICS_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_LYRICS_SONG_ID TEXT NOT NULL,
                $COL_LYRICS_STABLE_KEY TEXT NOT NULL UNIQUE,
                $COL_LYRICS_TITLE TEXT NOT NULL,
                $COL_LYRICS_ARTIST TEXT NOT NULL,
                $COL_LYRICS_ALBUM TEXT,
                $COL_LYRICS_MUSIC_FILE_REF TEXT,
                $COL_LYRICS_FORMAT TEXT NOT NULL,
                $COL_LYRICS_FILE_PATH TEXT NOT NULL,
                $COL_LYRICS_STORAGE_TYPE TEXT NOT NULL,
                $COL_LYRICS_SOURCE TEXT,
                $COL_LYRICS_FILE_SIZE INTEGER NOT NULL DEFAULT 0,
                $COL_LYRICS_CREATED_AT INTEGER NOT NULL,
                $COL_LYRICS_UPDATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_lyrics_song_id ON $TABLE_LYRICS($COL_LYRICS_SONG_ID)")
        db.execSQL("CREATE INDEX idx_lyrics_stable_key ON $TABLE_LYRICS($COL_LYRICS_STABLE_KEY)")

        // Create Playlists Table
        db.execSQL(
            """
            CREATE TABLE $TABLE_PLAYLISTS (
                $COL_PL_ID TEXT PRIMARY KEY NOT NULL,
                $COL_PL_NAME TEXT NOT NULL,
                $COL_PL_CREATED_AT INTEGER NOT NULL,
                $COL_PL_UPDATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // Create Playlist Tracks Table with explicit foreign key and order_index
        db.execSQL(
            """
            CREATE TABLE $TABLE_PLAYLIST_TRACKS (
                $COL_PT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PT_PLAYLIST_ID TEXT NOT NULL,
                $COL_PT_TRACK_ID TEXT NOT NULL,
                $COL_PT_TITLE TEXT NOT NULL,
                $COL_PT_ARTIST TEXT NOT NULL,
                $COL_PT_ALBUM TEXT,
                $COL_PT_AUDIO_URI TEXT,
                $COL_PT_DURATION INTEGER NOT NULL DEFAULT 0,
                $COL_PT_ORDER INTEGER NOT NULL,
                FOREIGN KEY ($COL_PT_PLAYLIST_ID) REFERENCES $TABLE_PLAYLISTS($COL_PL_ID) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_playlist_tracks_order ON $TABLE_PLAYLIST_TRACKS($COL_PT_PLAYLIST_ID, $COL_PT_ORDER)")

        // Create Settings Table
        db.execSQL(
            """
            CREATE TABLE $TABLE_SETTINGS (
                $COL_SETTING_KEY TEXT PRIMARY KEY NOT NULL,
                $COL_SETTING_VALUE TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Safe migration without data loss
    }

    // =========================================================================
    // LYRICS ASSOCIATIONS
    // =========================================================================

    fun saveLyricsAssociation(assoc: LyricsAssociation): Boolean {
        return try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COL_LYRICS_SONG_ID, assoc.songId)
                put(COL_LYRICS_STABLE_KEY, assoc.stableKey)
                put(COL_LYRICS_TITLE, assoc.title)
                put(COL_LYRICS_ARTIST, assoc.artist)
                put(COL_LYRICS_ALBUM, assoc.album)
                put(COL_LYRICS_MUSIC_FILE_REF, assoc.musicFileRef)
                put(COL_LYRICS_FORMAT, assoc.lyricsFormat)
                put(COL_LYRICS_FILE_PATH, assoc.lyricsFilePath)
                put(COL_LYRICS_STORAGE_TYPE, assoc.storageType)
                put(COL_LYRICS_SOURCE, assoc.lyricsSource)
                put(COL_LYRICS_FILE_SIZE, assoc.fileSizeBytes)
                put(COL_LYRICS_CREATED_AT, assoc.createdAt)
                put(COL_LYRICS_UPDATED_AT, assoc.updatedAt)
            }
            val id = db.insertWithOnConflict(
                TABLE_LYRICS,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
            id != -1L
        } catch (e: Exception) {
            Log.e(TAG, "Error saving lyrics association for ${assoc.title}", e)
            false
        }
    }

    fun findLyricsAssociation(songId: String, title: String, artist: String, audioUri: String? = null): LyricsAssociation? {
        val db = readableDatabase
        val stableKey = normalizeKey(artist, title)

        // Query by stable key first, fallback to song_id, fallback to music_file_ref
        val selection = "$COL_LYRICS_STABLE_KEY = ? OR $COL_LYRICS_SONG_ID = ?" +
                if (!audioUri.isNullOrBlank()) " OR $COL_LYRICS_MUSIC_FILE_REF = ?" else ""
        val args = if (!audioUri.isNullOrBlank()) arrayOf(stableKey, songId, audioUri) else arrayOf(stableKey, songId)

        val cursor: Cursor = db.query(
            TABLE_LYRICS,
            null,
            selection,
            args,
            null,
            null,
            "$COL_LYRICS_UPDATED_AT DESC",
            "1"
        )

        cursor.use { c ->
            if (c.moveToFirst()) {
                return parseLyricsAssociation(c)
            }
        }
        return null
    }

    fun getAllLyricsAssociations(): List<LyricsAssociation> {
        val db = readableDatabase
        val list = mutableListOf<LyricsAssociation>()
        val cursor = db.query(TABLE_LYRICS, null, null, null, null, null, "$COL_LYRICS_UPDATED_AT DESC")
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(parseLyricsAssociation(c))
            }
        }
        return list
    }

    fun getSavedLyricsCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_LYRICS", null)
        cursor.use { c ->
            if (c.moveToFirst()) {
                return c.getInt(0)
            }
        }
        return 0
    }

    fun deleteLyricsAssociation(songId: String, title: String, artist: String): Boolean {
        val db = writableDatabase
        val stableKey = normalizeKey(artist, title)
        val count = db.delete(
            TABLE_LYRICS,
            "$COL_LYRICS_STABLE_KEY = ? OR $COL_LYRICS_SONG_ID = ?",
            arrayOf(stableKey, songId)
        )
        return count > 0
    }

    private fun parseLyricsAssociation(c: Cursor): LyricsAssociation {
        return LyricsAssociation(
            id = c.getLong(c.getColumnIndexOrThrow(COL_LYRICS_ID)),
            songId = c.getString(c.getColumnIndexOrThrow(COL_LYRICS_SONG_ID)),
            stableKey = c.getString(c.getColumnIndexOrThrow(COL_LYRICS_STABLE_KEY)),
            title = c.getString(c.getColumnIndexOrThrow(COL_LYRICS_TITLE)),
            artist = c.getString(c.getColumnIndexOrThrow(COL_LYRICS_ARTIST)),
            album = c.getString(c.getColumnIndexOrThrow(COL_LYRICS_ALBUM)) ?: "",
            musicFileRef = c.getString(c.getColumnIndexOrThrow(COL_LYRICS_MUSIC_FILE_REF)) ?: "",
            lyricsFormat = c.getString(c.getColumnIndexOrThrow(COL_LYRICS_FORMAT)),
            lyricsFilePath = c.getString(c.getColumnIndexOrThrow(COL_LYRICS_FILE_PATH)),
            storageType = c.getString(c.getColumnIndexOrThrow(COL_LYRICS_STORAGE_TYPE)),
            lyricsSource = c.getString(c.getColumnIndexOrThrow(COL_LYRICS_SOURCE)) ?: "",
            fileSizeBytes = c.getLong(c.getColumnIndexOrThrow(COL_LYRICS_FILE_SIZE)),
            createdAt = c.getLong(c.getColumnIndexOrThrow(COL_LYRICS_CREATED_AT)),
            updatedAt = c.getLong(c.getColumnIndexOrThrow(COL_LYRICS_UPDATED_AT))
        )
    }

    // =========================================================================
    // PLAYLISTS (EXACT SEQUENCE ORDERING PRESERVED)
    // =========================================================================

    fun getAllPlaylists(): List<Playlist> {
        val db = readableDatabase
        val playlists = mutableListOf<Playlist>()

        val cursor = db.query(TABLE_PLAYLISTS, null, null, null, null, null, "$COL_PL_CREATED_AT ASC")
        cursor.use { c ->
            val idCol = c.getColumnIndexOrThrow(COL_PL_ID)
            val nameCol = c.getColumnIndexOrThrow(COL_PL_NAME)

            while (c.moveToNext()) {
                val plId = c.getString(idCol)
                val plName = c.getString(nameCol)
                val trackIds = getTrackIdsForPlaylist(plId)
                playlists.add(Playlist(id = plId, name = plName, trackIds = trackIds))
            }
        }
        return playlists
    }

    fun getTrackIdsForPlaylist(playlistId: String): List<String> {
        val db = readableDatabase
        val trackIds = mutableListOf<String>()
        val cursor = db.query(
            TABLE_PLAYLIST_TRACKS,
            arrayOf(COL_PT_TRACK_ID),
            "$COL_PT_PLAYLIST_ID = ?",
            arrayOf(playlistId),
            null,
            null,
            "$COL_PT_ORDER ASC"
        )
        cursor.use { c ->
            val trackIdCol = c.getColumnIndexOrThrow(COL_PT_TRACK_ID)
            while (c.moveToNext()) {
                trackIds.add(c.getString(trackIdCol))
            }
        }
        return trackIds
    }

    fun createPlaylist(id: String, name: String): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_PL_ID, id)
            put(COL_PL_NAME, name)
            put(COL_PL_CREATED_AT, System.currentTimeMillis())
            put(COL_PL_UPDATED_AT, System.currentTimeMillis())
        }
        val res = db.insertWithOnConflict(TABLE_PLAYLISTS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        return res != -1L
    }

    fun renamePlaylist(id: String, newName: String): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_PL_NAME, newName)
            put(COL_PL_UPDATED_AT, System.currentTimeMillis())
        }
        val res = db.update(TABLE_PLAYLISTS, values, "$COL_PL_ID = ?", arrayOf(id))
        return res > 0
    }

    fun deletePlaylist(id: String): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            db.delete(TABLE_PLAYLIST_TRACKS, "$COL_PT_PLAYLIST_ID = ?", arrayOf(id))
            val res = db.delete(TABLE_PLAYLISTS, "$COL_PL_ID = ?", arrayOf(id))
            db.setTransactionSuccessful()
            res > 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete playlist $id", e)
            false
        } finally {
            db.endTransaction()
        }
    }

    fun addTrackToPlaylist(playlistId: String, track: Track): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            // Check if track is already in playlist
            val checkCursor = db.rawQuery(
                "SELECT COUNT(*) FROM $TABLE_PLAYLIST_TRACKS WHERE $COL_PT_PLAYLIST_ID = ? AND $COL_PT_TRACK_ID = ?",
                arrayOf(playlistId, track.id)
            )
            val alreadyExists = checkCursor.use { c ->
                c.moveToFirst() && c.getInt(0) > 0
            }
            if (alreadyExists) {
                db.setTransactionSuccessful()
                return true
            }

            // Get next order index
            val maxCursor = db.rawQuery(
                "SELECT COALESCE(MAX($COL_PT_ORDER), -1) FROM $TABLE_PLAYLIST_TRACKS WHERE $COL_PT_PLAYLIST_ID = ?",
                arrayOf(playlistId)
            )
            val nextOrder = maxCursor.use { c ->
                if (c.moveToFirst()) c.getInt(0) + 1 else 0
            }

            val values = ContentValues().apply {
                put(COL_PT_PLAYLIST_ID, playlistId)
                put(COL_PT_TRACK_ID, track.id)
                put(COL_PT_TITLE, track.title)
                put(COL_PT_ARTIST, track.artist)
                put(COL_PT_ALBUM, track.album)
                put(COL_PT_AUDIO_URI, track.audioUri)
                put(COL_PT_DURATION, track.durationMs)
                put(COL_PT_ORDER, nextOrder)
            }
            db.insert(TABLE_PLAYLIST_TRACKS, null, values)

            // Update playlist timestamp
            val plValues = ContentValues().apply {
                put(COL_PL_UPDATED_AT, System.currentTimeMillis())
            }
            db.update(TABLE_PLAYLISTS, plValues, "$COL_PL_ID = ?", arrayOf(playlistId))

            db.setTransactionSuccessful()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add track ${track.title} to playlist $playlistId", e)
            false
        } finally {
            db.endTransaction()
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            db.delete(
                TABLE_PLAYLIST_TRACKS,
                "$COL_PT_PLAYLIST_ID = ? AND $COL_PT_TRACK_ID = ?",
                arrayOf(playlistId, trackId)
            )

            // Compact ordering
            val currentTracks = mutableListOf<String>()
            val c = db.query(
                TABLE_PLAYLIST_TRACKS,
                arrayOf(COL_PT_TRACK_ID),
                "$COL_PT_PLAYLIST_ID = ?",
                arrayOf(playlistId),
                null,
                null,
                "$COL_PT_ORDER ASC"
            )
            c.use {
                val col = it.getColumnIndexOrThrow(COL_PT_TRACK_ID)
                while (it.moveToNext()) {
                    currentTracks.add(it.getString(col))
                }
            }

            currentTracks.forEachIndexed { index, tid ->
                val v = ContentValues().apply { put(COL_PT_ORDER, index) }
                db.update(
                    TABLE_PLAYLIST_TRACKS,
                    v,
                    "$COL_PT_PLAYLIST_ID = ? AND $COL_PT_TRACK_ID = ?",
                    arrayOf(playlistId, tid)
                )
            }

            db.setTransactionSuccessful()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove track $trackId from playlist $playlistId", e)
            false
        } finally {
            db.endTransaction()
        }
    }

    fun moveTrackInPlaylist(playlistId: String, fromIndex: Int, toIndex: Int): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val trackIds = mutableListOf<String>()
            val cursor = db.query(
                TABLE_PLAYLIST_TRACKS,
                arrayOf(COL_PT_TRACK_ID),
                "$COL_PT_PLAYLIST_ID = ?",
                arrayOf(playlistId),
                null,
                null,
                "$COL_PT_ORDER ASC"
            )
            cursor.use { c ->
                val col = c.getColumnIndexOrThrow(COL_PT_TRACK_ID)
                while (c.moveToNext()) {
                    trackIds.add(c.getString(col))
                }
            }

            if (fromIndex in trackIds.indices && toIndex in trackIds.indices && fromIndex != toIndex) {
                val item = trackIds.removeAt(fromIndex)
                trackIds.add(toIndex, item)

                trackIds.forEachIndexed { index, tid ->
                    val v = ContentValues().apply { put(COL_PT_ORDER, index) }
                    db.update(
                        TABLE_PLAYLIST_TRACKS,
                        v,
                        "$COL_PT_PLAYLIST_ID = ? AND $COL_PT_TRACK_ID = ?",
                        arrayOf(playlistId, tid)
                    )
                }

                // Update playlist timestamp
                val plValues = ContentValues().apply {
                    put(COL_PL_UPDATED_AT, System.currentTimeMillis())
                }
                db.update(TABLE_PLAYLISTS, plValues, "$COL_PL_ID = ?", arrayOf(playlistId))

                db.setTransactionSuccessful()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error moving track from $fromIndex to $toIndex in playlist $playlistId", e)
            false
        } finally {
            db.endTransaction()
        }
    }

    // =========================================================================
    // APP SETTINGS
    // =========================================================================

    fun getSetting(key: String, defaultValue: String): String {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_SETTINGS,
            arrayOf(COL_SETTING_VALUE),
            "$COL_SETTING_KEY = ?",
            arrayOf(key),
            null,
            null,
            null
        )
        cursor.use { c ->
            if (c.moveToFirst()) {
                return c.getString(c.getColumnIndexOrThrow(COL_SETTING_VALUE))
            }
        }
        return defaultValue
    }

    fun setSetting(key: String, value: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_SETTING_KEY, key)
            put(COL_SETTING_VALUE, value)
        }
        db.insertWithOnConflict(TABLE_SETTINGS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun isPlaylistsInitialized(): Boolean {
        return getSetting("playlists_initialized", "false") == "true"
    }

    fun markPlaylistsInitialized() {
        setSetting("playlists_initialized", "true")
    }

    fun normalizeKey(artist: String, title: String): String {
        val cleanArtist = artist.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
        val cleanTitle = title.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
        return "${cleanArtist}:::${cleanTitle}"
    }
}
