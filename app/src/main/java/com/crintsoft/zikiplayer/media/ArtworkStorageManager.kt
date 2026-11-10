package com.crintsoft.zikiplayer.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ArtworkStorageManager(private val context: Context) {

    companion object {
        private const val TAG = "ArtworkStorageManager"
        private const val ARTWORK_DIR = "MiniLyrics/Artwork"
    }

    private val artworkDirectory: File
        get() {
            val dir = File(context.filesDir, ARTWORK_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    /**
     * Copies and optimizes an image from a content URI to internal storage.
     * Returns the absolute path of the saved artwork file, or null if failed.
     */
    suspend fun saveArtworkFromUri(songId: String, sourceUri: Uri): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val cleanId = songId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            val targetFile = File(artworkDirectory, "art_${cleanId}_${System.currentTimeMillis()}.jpg")

            // Delete existing files for this song if any
            val existing = artworkDirectory.listFiles { _, name -> name.startsWith("art_${cleanId}_") }
            existing?.forEach { it.delete() }

            val inputStream: InputStream? = context.contentResolver.openInputStream(sourceUri)
            if (inputStream == null) {
                Log.e(TAG, "Cannot open input stream for URI: $sourceUri")
                return@withContext null
            }

            // Decode bitmap and resize if necessary to keep UI fast and memory safe
            inputStream.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                if (bitmap != null) {
                    val maxDimension = 800
                    val scaledBitmap = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                        val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                        val newWidth: Int
                        val newHeight: Int
                        if (ratio > 1f) {
                            newWidth = maxDimension
                            newHeight = (maxDimension / ratio).toInt()
                        } else {
                            newHeight = maxDimension
                            newWidth = (maxDimension * ratio).toInt()
                        }
                        Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                    } else {
                        bitmap
                    }

                    FileOutputStream(targetFile).use { out ->
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    if (scaledBitmap != bitmap) {
                        scaledBitmap.recycle()
                    }
                    bitmap.recycle()
                    targetFile.absolutePath
                } else {
                    // Fallback to direct stream copy
                    val rawStream = context.contentResolver.openInputStream(sourceUri)
                    rawStream?.use { raw ->
                        FileOutputStream(targetFile).use { out ->
                            raw.copyTo(out)
                        }
                    }
                    targetFile.absolutePath
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save artwork for song $songId from $sourceUri", e)
            null
        }
    }

    /**
     * Deletes the persistent artwork file for a song.
     */
    suspend fun deleteArtworkForSong(songId: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val cleanId = songId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            val existing = artworkDirectory.listFiles { _, name -> name.startsWith("art_${cleanId}_") }
            var allDeleted = true
            existing?.forEach { file ->
                if (!file.delete()) allDeleted = false
            }
            allDeleted
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting artwork for $songId", e)
            false
        }
    }

    /**
     * Checks if artwork exists for the song.
     */
    fun getArtworkFile(filePath: String?): File? {
        if (filePath.isNullOrBlank()) return null
        val file = File(filePath)
        return if (file.exists() && file.isFile && file.length() > 0) file else null
    }
}
