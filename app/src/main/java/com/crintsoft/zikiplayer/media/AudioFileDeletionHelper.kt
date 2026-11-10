package com.crintsoft.zikiplayer.media

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class DeletionResult {
    object Success : DeletionResult()
    data class RequiresUserConsent(val intentSender: IntentSender) : DeletionResult()
    data class Error(val message: String) : DeletionResult()
}

class AudioFileDeletionHelper(private val context: Context) {

    private val tag = "AudioFileDeletionHelper"

    /**
     * Attempts to permanently delete the physical audio file corresponding to the track's audioUri.
     * Verifies that the file is actually deleted before returning Success.
     */
    suspend fun deletePhysicalAudio(audioUriString: String?): DeletionResult = withContext(Dispatchers.IO) {
        if (audioUriString.isNullOrBlank()) {
            return@withContext DeletionResult.Error("No audio file location specified for this song")
        }

        try {
            val uri = Uri.parse(audioUriString)

            // Case A: Direct file:// URI or direct filesystem path
            if (audioUriString.startsWith("file://") || audioUriString.startsWith("/")) {
                val filePath = if (audioUriString.startsWith("file://")) uri.path ?: "" else audioUriString
                val file = File(filePath)
                if (!file.exists()) {
                    // File is already deleted or not present
                    return@withContext DeletionResult.Success
                }

                val deleted = file.delete()
                return@withContext if (deleted || !file.exists()) {
                    DeletionResult.Success
                } else {
                    DeletionResult.Error("File could not be removed from filesystem (permission or lock error)")
                }
            }

            // Case B: DocumentsContract / SAF Document URI
            if (audioUriString.startsWith("content://") && isDocumentUri(uri)) {
                return@withContext try {
                    val deleted = DocumentsContract.deleteDocument(context.contentResolver, uri)
                    if (deleted) {
                        DeletionResult.Success
                    } else {
                        // Double check if document still exists
                        val exists = checkUriExists(uri)
                        if (!exists) DeletionResult.Success else DeletionResult.Error("SAF Document delete returned false")
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error deleting document via SAF: ${e.message}", e)
                    DeletionResult.Error(e.message ?: "Failed to delete document via SAF")
                }
            }

            // Case C: MediaStore Audio Content URI
            if (audioUriString.startsWith("content://")) {
                try {
                    val rows = context.contentResolver.delete(uri, null, null)
                    if (rows > 0) {
                        return@withContext DeletionResult.Success
                    } else {
                        // Check if file still exists in MediaStore
                        val exists = checkUriExists(uri)
                        if (!exists) {
                            return@withContext DeletionResult.Success
                        }
                    }
                } catch (securityEx: SecurityException) {
                    Log.w(tag, "SecurityException while deleting MediaStore item: ${securityEx.message}")

                    // Android 11+ (API 30+) scoped storage delete consent request
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        return@withContext try {
                            val deleteRequest = MediaStore.createDeleteRequest(
                                context.contentResolver,
                                listOf(uri)
                            )
                            DeletionResult.RequiresUserConsent(deleteRequest.intentSender)
                        } catch (e: Exception) {
                            Log.e(tag, "Failed to create delete request for MediaStore", e)
                            DeletionResult.Error("Deletion requires system permission: ${e.message}")
                        }
                    }

                    // Android 10 (API 29) RecoverableSecurityException
                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && securityEx is RecoverableSecurityException) {
                        return@withContext DeletionResult.RequiresUserConsent(
                            securityEx.userAction.actionIntent.intentSender
                        )
                    }

                    return@withContext DeletionResult.Error("Permission denied deleting media file from storage")
                } catch (e: Exception) {
                    Log.e(tag, "Error during contentResolver.delete: ${e.message}", e)
                    return@withContext DeletionResult.Error(e.message ?: "Failed to delete audio file")
                }

                // If rows == 0 and file still exists:
                // Attempt to check if underlying file path can be deleted directly
                val filePath = resolveFilePathFromUri(uri)
                if (!filePath.isNullOrBlank()) {
                    val file = File(filePath)
                    if (file.exists() && file.delete()) {
                        try {
                            context.contentResolver.delete(uri, null, null)
                        } catch (_: Exception) {}
                        return@withContext DeletionResult.Success
                    }
                }

                return@withContext DeletionResult.Error("Could not delete audio file from device storage")
            }

            DeletionResult.Error("Unsupported audio storage location format: $audioUriString")
        } catch (e: Exception) {
            Log.e(tag, "Unexpected error deleting audio file: ${e.message}", e)
            DeletionResult.Error(e.message ?: "Unknown error while deleting audio file")
        }
    }

    private fun isDocumentUri(uri: Uri): Boolean {
        return try {
            DocumentsContract.isDocumentUri(context, uri)
        } catch (_: Exception) {
            false
        }
    }

    private fun checkUriExists(uri: Uri): Boolean {
        return try {
            val cursor = context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
            val exists = cursor?.use { it.moveToFirst() } ?: false
            exists
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveFilePathFromUri(uri: Uri): String? {
        return try {
            val projection = arrayOf(MediaStore.Audio.Media.DATA)
            val cursor = context.contentResolver.query(uri, projection, null, null, null)
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(MediaStore.Audio.Media.DATA)
                    if (idx != -1) c.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
