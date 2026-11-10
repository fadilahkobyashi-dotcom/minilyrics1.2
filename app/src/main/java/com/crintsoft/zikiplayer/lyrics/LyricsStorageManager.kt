package com.crintsoft.zikiplayer.lyrics

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.crintsoft.zikiplayer.data.db.MiniLyricsDbHelper
import com.crintsoft.zikiplayer.data.model.LyricsAssociation
import com.crintsoft.zikiplayer.data.model.SavedLyricsResult
import com.crintsoft.zikiplayer.data.model.StorageLocationType
import com.crintsoft.zikiplayer.model.LyricType
import com.crintsoft.zikiplayer.model.Track
import java.io.File
import java.io.IOException

import com.crintsoft.zikiplayer.data.room.MiniLyricsRoomDatabase
import com.crintsoft.zikiplayer.data.room.entities.LyricsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

data class StorageDirectoryStatus(
    val type: StorageLocationType,
    val displayPath: String,
    val isWritable: Boolean,
    val isVerified: Boolean,
    val totalSavedFiles: Int,
    val errorMessage: String? = null
)

class LyricsStorageManager(
    private val context: Context,
    private val dbHelper: MiniLyricsDbHelper
) {
    private val roomDatabase by lazy { MiniLyricsRoomDatabase.getInstance(context) }
    private val roomLyricsDao by lazy { roomDatabase.lyricsDao() }

    companion object {
        private const val TAG = "LyricsStorageManager"
        private const val PREF_KEY_STORAGE_TYPE = "pref_lyrics_storage_type"
        private const val PREF_KEY_CUSTOM_URI = "pref_lyrics_custom_uri"
        private const val INTERNAL_DIR_NAME = "MiniLyrics/Lyrics"
    }

    private val internalLyricsDir: File by lazy {
        val dir = File(context.filesDir, INTERNAL_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    /**
     * Gets the current selected storage type (INTERNAL or CUSTOM_SAF).
     */
    fun getStorageLocationType(): StorageLocationType {
        val raw = dbHelper.getSetting(PREF_KEY_STORAGE_TYPE, StorageLocationType.INTERNAL.name)
        return try {
            StorageLocationType.valueOf(raw)
        } catch (_: Exception) {
            StorageLocationType.INTERNAL
        }
    }

    fun setStorageLocationType(type: StorageLocationType) {
        dbHelper.setSetting(PREF_KEY_STORAGE_TYPE, type.name)
    }

    fun getCustomStorageUri(): Uri? {
        val raw = dbHelper.getSetting(PREF_KEY_CUSTOM_URI, "")
        return if (raw.isNotBlank()) {
            try {
                Uri.parse(raw)
            } catch (_: Exception) {
                null
            }
        } else null
    }

    fun setCustomStorageUri(uri: Uri): Boolean {
        return try {
            // Take persistable permission if available
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                Log.w(TAG, "Notice: persistable permission grant result: ${e.message}")
            }

            if (isSafTreeAccessible(uri)) {
                dbHelper.setSetting(PREF_KEY_CUSTOM_URI, uri.toString())
                dbHelper.setSetting(PREF_KEY_STORAGE_TYPE, StorageLocationType.CUSTOM_SAF.name)
                true
            } else {
                Log.e(TAG, "Selected custom directory is not accessible via DocumentsContract")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring custom storage URI", e)
            false
        }
    }

    private fun isSafTreeAccessible(treeUri: Uri): Boolean {
        return try {
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
            val cursor = context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null,
                null,
                null
            )
            val accessible = cursor?.use { it.moveToFirst() } ?: false
            accessible
        } catch (e: Exception) {
            false
        }
    }

    private fun getSafTreeDisplayName(treeUri: Uri): String {
        return try {
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
            val cursor = context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (idx != -1) c.getString(idx) else null
                } else null
            } ?: (treeUri.lastPathSegment ?: "Custom Directory")
        } catch (_: Exception) {
            treeUri.lastPathSegment ?: "Custom Directory"
        }
    }

    /**
     * Obtains human-friendly display description of the current storage directory.
     */
    fun getStorageStatus(): StorageDirectoryStatus {
        val totalCount = dbHelper.getSavedLyricsCount()
        val type = getStorageLocationType()

        if (type == StorageLocationType.CUSTOM_SAF) {
            val customUri = getCustomStorageUri()
            if (customUri != null && isSafTreeAccessible(customUri)) {
                val displayName = getSafTreeDisplayName(customUri)
                return StorageDirectoryStatus(
                    type = StorageLocationType.CUSTOM_SAF,
                    displayPath = "Custom Folder: $displayName",
                    isWritable = true,
                    isVerified = true,
                    totalSavedFiles = totalCount
                )
            }
            // Fallback status if custom SAF fails
            return StorageDirectoryStatus(
                type = StorageLocationType.CUSTOM_SAF,
                displayPath = "Custom directory unavailable (falling back to Internal Storage)",
                isWritable = internalLyricsDir.canWrite(),
                isVerified = false,
                totalSavedFiles = totalCount,
                errorMessage = "Custom location inaccessible. Internal storage will be used safely."
            )
        }

        // Default Internal
        val path = internalLyricsDir.absolutePath.replace(context.applicationInfo.dataDir, "~")
        return StorageDirectoryStatus(
            type = StorageLocationType.INTERNAL,
            displayPath = "Internal Storage: $path",
            isWritable = internalLyricsDir.canWrite(),
            isVerified = true,
            totalSavedFiles = totalCount
        )
    }

    /**
     * Saves lyrics content to a real file in either the user-selected custom directory
     * or the default internal lyrics directory.
     * Verifies that the file was created, contains bytes, is readable,
     * and writes the permanent association to the database.
     */
    fun saveLyricsFile(
        track: Track,
        content: String,
        format: LyricType,
        source: String
    ): Result<SavedLyricsResult> {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Lyrics content cannot be empty"))
        }

        val extension = if (format == LyricType.LRC) "lrc" else "txt"
        val fileName = buildSanitizedFileName(track.artist, track.title, extension)
        val selectedType = getStorageLocationType()

        // 1. Try Custom SAF if chosen
        if (selectedType == StorageLocationType.CUSTOM_SAF) {
            val customUri = getCustomStorageUri()
            if (customUri != null && isSafTreeAccessible(customUri)) {
                val safResult = saveToCustomSaf(track, fileName, trimmed, format, source, customUri)
                if (safResult.isSuccess) {
                    return safResult
                }
                Log.w(TAG, "Custom SAF save failed, falling back to Internal Storage: ${safResult.exceptionOrNull()?.message}")
            }
        }

        // 2. Default Internal Storage Save
        return saveToInternalStorage(track, fileName, trimmed, format, source)
    }

    private fun saveToInternalStorage(
        track: Track,
        fileName: String,
        content: String,
        format: LyricType,
        source: String
    ): Result<SavedLyricsResult> {
        try {
            if (!internalLyricsDir.exists()) {
                internalLyricsDir.mkdirs()
            }
            val targetFile = File(internalLyricsDir, fileName)

            // Write actual file with UTF-8
            targetFile.writeText(content, Charsets.UTF_8)

            // VERIFICATION STEP (MANDATORY)
            if (!targetFile.exists()) {
                return Result.failure(IOException("Verification failed: Target lyrics file does not exist on disk"))
            }
            if (!targetFile.canRead()) {
                return Result.failure(IOException("Verification failed: Target lyrics file is not readable"))
            }
            val fileLength = targetFile.length()
            if (fileLength == 0L) {
                return Result.failure(IOException("Verification failed: Target lyrics file is empty (0 bytes)"))
            }

            // Save Association in Database
            val association = LyricsAssociation(
                songId = track.id,
                stableKey = dbHelper.normalizeKey(track.artist, track.title),
                title = track.title,
                artist = track.artist,
                album = track.album,
                musicFileRef = track.audioUri ?: track.id,
                lyricsFormat = if (format == LyricType.LRC) "LRC" else "TXT",
                lyricsFilePath = targetFile.absolutePath,
                storageType = "INTERNAL",
                lyricsSource = source,
                fileSizeBytes = fileLength,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            val dbSaved = dbHelper.saveLyricsAssociation(association)
            if (!dbSaved) {
                Log.w(TAG, "Notice: Database association write warning for ${track.title}")
            }

            // Also mirror permanently to Room database
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    roomLyricsDao.insertOrReplaceLyrics(
                        LyricsEntity(
                            songId = track.id,
                            stableKey = dbHelper.normalizeKey(track.artist, track.title),
                            title = track.title,
                            artist = track.artist,
                            album = track.album,
                            musicFileRef = track.audioUri ?: track.id,
                            lyricsFormat = if (format == LyricType.LRC) "LRC" else "TXT",
                            lyricsFilePath = targetFile.absolutePath,
                            storageType = "INTERNAL",
                            lyricsSource = source,
                            fileSizeBytes = fileLength,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Room lyrics mirror failed: ${e.message}")
                }
            }

            return Result.success(
                SavedLyricsResult(
                    filePathOrUri = targetFile.absolutePath,
                    fileName = fileName,
                    format = if (format == LyricType.LRC) "LRC" else "TXT",
                    storageType = "Internal Storage",
                    fileSizeBytes = fileLength,
                    isVerified = true
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error saving lyrics to internal storage", e)
            return Result.failure(e)
        }
    }

    private fun saveToCustomSaf(
        track: Track,
        fileName: String,
        content: String,
        format: LyricType,
        source: String,
        treeUri: Uri
    ): Result<SavedLyricsResult> {
        try {
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)

            // Find existing child document to overwrite or avoid duplicates
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
            var existingDocUri: Uri? = null
            try {
                val cursor = context.contentResolver.query(
                    childrenUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null,
                    null,
                    null
                )
                cursor?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    while (c.moveToNext()) {
                        if (c.getString(nameCol).equals(fileName, ignoreCase = true)) {
                            val childDocId = c.getString(idCol)
                            existingDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                            break
                        }
                    }
                }
            } catch (_: Exception) {}

            val targetDocUri = existingDocUri ?: DocumentsContract.createDocument(
                context.contentResolver,
                parentDocUri,
                "text/plain",
                fileName
            ) ?: return Result.failure(IOException("Failed to create document $fileName in custom directory"))

            context.contentResolver.openOutputStream(targetDocUri, "wt")?.use { outputStream ->
                outputStream.write(content.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            } ?: return Result.failure(IOException("Failed to open output stream for document $targetDocUri"))

            // VERIFICATION STEP
            var fileLength = 0L
            val sizeCursor = context.contentResolver.query(
                targetDocUri,
                arrayOf(DocumentsContract.Document.COLUMN_SIZE),
                null,
                null,
                null
            )
            sizeCursor?.use { c ->
                if (c.moveToFirst()) {
                    val sizeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    if (sizeIdx != -1) fileLength = c.getLong(sizeIdx)
                }
            }

            if (fileLength == 0L) {
                val bytes = context.contentResolver.openInputStream(targetDocUri)?.use { it.readBytes() }
                fileLength = bytes?.size?.toLong() ?: 0L
            }

            if (fileLength == 0L) {
                return Result.failure(IOException("Verification failed: Saved SAF file is empty (0 bytes)"))
            }

            // Save Association in Database
            val association = LyricsAssociation(
                songId = track.id,
                stableKey = dbHelper.normalizeKey(track.artist, track.title),
                title = track.title,
                artist = track.artist,
                album = track.album,
                musicFileRef = track.audioUri ?: track.id,
                lyricsFormat = if (format == LyricType.LRC) "LRC" else "TXT",
                lyricsFilePath = targetDocUri.toString(),
                storageType = "CUSTOM_SAF",
                lyricsSource = source,
                fileSizeBytes = fileLength,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            dbHelper.saveLyricsAssociation(association)

            // Also mirror permanently to Room database
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    roomLyricsDao.insertOrReplaceLyrics(
                        LyricsEntity(
                            songId = track.id,
                            stableKey = dbHelper.normalizeKey(track.artist, track.title),
                            title = track.title,
                            artist = track.artist,
                            album = track.album,
                            musicFileRef = track.audioUri ?: track.id,
                            lyricsFormat = if (format == LyricType.LRC) "LRC" else "TXT",
                            lyricsFilePath = targetDocUri.toString(),
                            storageType = "CUSTOM_SAF",
                            lyricsSource = source,
                            fileSizeBytes = fileLength,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Room SAF lyrics mirror failed: ${e.message}")
                }
            }

            return Result.success(
                SavedLyricsResult(
                    filePathOrUri = targetDocUri.toString(),
                    fileName = fileName,
                    format = if (format == LyricType.LRC) "LRC" else "TXT",
                    storageType = "Custom Directory",
                    fileSizeBytes = fileLength,
                    isVerified = true
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error saving lyrics to custom SAF directory", e)
            return Result.failure(e)
        }
    }

    /**
     * Loads the saved lyrics for a given track from the permanent database and physical file.
     * Restores timestamp sync for LRC or plain formatting for TXT.
     */
    fun loadSavedLyricsForTrack(track: Track): Pair<LyricType, String>? {
        // Query association
        val association = dbHelper.findLyricsAssociation(
            songId = track.id,
            title = track.title,
            artist = track.artist,
            audioUri = track.audioUri
        ) ?: return null

        val content = readLyricsFile(association.lyricsFilePath) ?: return null
        if (content.isBlank()) return null

        val format = if (association.lyricsFormat.equals("LRC", ignoreCase = true)) {
            LyricType.LRC
        } else if (association.lyricsFormat.equals("TXT", ignoreCase = true)) {
            LyricType.TXT
        } else {
            LrcParser.detectLyricType(content)
        }

        return Pair(format, content)
    }

    fun readLyricsFile(filePathOrUri: String): String? {
        return try {
            if (filePathOrUri.startsWith("content://")) {
                val uri = Uri.parse(filePathOrUri)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                }
            } else {
                val file = File(filePathOrUri)
                if (file.exists() && file.canRead()) {
                    file.readText(Charsets.UTF_8)
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read saved lyrics from $filePathOrUri: ${e.message}")
            null
        }
    }

    private fun buildSanitizedFileName(artist: String, title: String, extension: String): String {
        val safeArtist = artist.replace(Regex("[/\\\\?%*:|\"<>]"), "_").trim().ifBlank { "Unknown Artist" }
        val safeTitle = title.replace(Regex("[/\\\\?%*:|\"<>]"), "_").trim().ifBlank { "Song" }
        return "$safeArtist - $safeTitle.$extension"
    }

    /**
     * Deletes saved lyrics association and physical file if exists.
     */
    fun deleteSavedLyricsForTrack(track: Track) {
        val assoc = dbHelper.findLyricsAssociation(track.id, track.title, track.artist, track.audioUri)
        if (assoc != null) {
            try {
                if (assoc.lyricsFilePath.startsWith("content://")) {
                    val uri = Uri.parse(assoc.lyricsFilePath)
                    DocumentsContract.deleteDocument(context.contentResolver, uri)
                } else {
                    val f = File(assoc.lyricsFilePath)
                    if (f.exists()) f.delete()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete physical lyrics file: ${e.message}")
            }
        }
        dbHelper.deleteLyricsAssociation(track.id, track.title, track.artist)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                roomLyricsDao.deleteLyricsBySongId(track.id)
            } catch (_: Exception) {}
        }
    }
}

