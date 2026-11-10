package com.crintsoft.zikiplayer.data.model

data class LyricsAssociation(
    val id: Long = 0,
    val songId: String,
    val stableKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val musicFileRef: String,
    val lyricsFormat: String, // "LRC" or "TXT"
    val lyricsFilePath: String, // Absolute filesystem path or content URI
    val storageType: String, // "INTERNAL" or "CUSTOM_SAF"
    val lyricsSource: String, // "ONLINE_SEARCH", "MANUAL_IMPORT", "PASTED", "LOCAL_SAVED"
    val fileSizeBytes: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class SavedLyricsResult(
    val filePathOrUri: String,
    val fileName: String,
    val format: String,
    val storageType: String,
    val fileSizeBytes: Long,
    val isVerified: Boolean
)

enum class StorageLocationType {
    INTERNAL,
    CUSTOM_SAF
}
