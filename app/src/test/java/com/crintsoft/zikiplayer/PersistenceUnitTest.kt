package com.crintsoft.zikiplayer

import com.crintsoft.zikiplayer.lyrics.DefaultLyrics
import com.crintsoft.zikiplayer.lyrics.LrcParser
import com.crintsoft.zikiplayer.model.LyricType
import com.crintsoft.zikiplayer.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistenceUnitTest {

    @Test
    fun testLrcParserSyncAndTiming() {
        val lrc = DefaultLyrics.TRACK_STARLIGHT_LRC
        val detected = LrcParser.detectLyricType(lrc)
        assertEquals(LyricType.LRC, detected)

        val parsed = LrcParser.parse(lrc)
        assertEquals(LyricType.LRC, parsed.type)
        assertTrue(parsed.isSynced)
        assertTrue("Lines count should be > 5", parsed.lines.size > 5)

        // Check line timing
        val firstLine = parsed.lines[0]
        assertEquals(0L, firstLine.timestampMs)
        assertTrue(firstLine.text.contains("Floating through the cosmic sea"))

        // Check second line
        val secondLine = parsed.lines[1]
        assertEquals(3500L, secondLine.timestampMs)
        assertTrue(secondLine.text.contains("Whispers in the quiet night"))
    }

    @Test
    fun testTxtFallbackDetection() {
        val plainText = """
            Verse 1
            This is plain text lyrics without timestamps
            Another line of text
        """.trimIndent()

        val detected = LrcParser.detectLyricType(plainText)
        assertEquals(LyricType.TXT, detected)

        val parsed = LrcParser.parse(plainText)
        assertEquals(LyricType.TXT, parsed.type)
        assertFalse(parsed.isSynced)
        assertTrue(parsed.isTxt)
        assertEquals(3, parsed.rawText.lines().size)
    }

    @Test
    fun testTrackLyricsPriority() {
        val lrcContent = "[00:01.00]Synchronized line"
        val txtContent = "Plain text fallback"

        // When both exist, LRC takes priority
        val trackWithBoth = Track(
            id = "t1",
            title = "Test Song",
            artist = "Test Artist",
            album = "Test Album",
            durationMs = 180000L,
            lrcContent = lrcContent,
            txtContent = txtContent
        )
        val effective = trackWithBoth.getEffectiveLyrics()
        assertNotNull(effective)
        assertEquals(LyricType.LRC, effective?.first)
        assertEquals(lrcContent, effective?.second)

        // When only TXT exists, TXT is used
        val trackWithTxtOnly = Track(
            id = "t2",
            title = "Plain Song",
            artist = "Author",
            album = "Album",
            durationMs = 120000L,
            lrcContent = null,
            txtContent = txtContent
        )
        val effectiveTxt = trackWithTxtOnly.getEffectiveLyrics()
        assertNotNull(effectiveTxt)
        assertEquals(LyricType.TXT, effectiveTxt?.first)
        assertEquals(txtContent, effectiveTxt?.second)

        // When neither exists
        val trackEmpty = Track(
            id = "t3",
            title = "No Lyrics",
            artist = "Author",
            album = "Album",
            durationMs = 120000L
        )
        assertNull(trackEmpty.getEffectiveLyrics())
    }

    @Test
    fun testSongKeyNormalization() {
        fun normalizeKey(artist: String, title: String): String {
            val cleanArtist = artist.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
            val cleanTitle = title.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
            return "${cleanArtist}:::${cleanTitle}"
        }

        val key1 = normalizeKey("Luna Eclipse", "Starlight Reverie")
        val key2 = normalizeKey("luna eclipse  ", "STARLIGHT REVERIE!")
        val key3 = normalizeKey("Luna  Eclipse-", "Starlight (Reverie)")

        assertEquals(key1, key2)
        assertEquals(key1, key3)
        assertEquals("lunaeclipse:::starlightreverie", key1)
    }

    @Test
    fun testPlaylistExactOrderingPreservation() {
        // Test custom playlist reordering:
        // Original: [Song A, Song B, Song C, Song D]
        val originalList = mutableListOf("Song A", "Song B", "Song C", "Song D")

        // User moves "Song C" (index 2) to position 0:
        val itemC = originalList.removeAt(2)
        originalList.add(0, itemC)

        // Expected: [Song C, Song A, Song B, Song D]
        assertEquals(listOf("Song C", "Song A", "Song B", "Song D"), originalList)

        // User moves "Song B" (index 2) to position 3 (down):
        val itemB = originalList.removeAt(2)
        originalList.add(3, itemB)

        // Expected: [Song C, Song A, Song D, Song B]
        assertEquals(listOf("Song C", "Song A", "Song D", "Song B"), originalList)

        // Verify indexing is strictly sequential 0..3
        val mappedIndices = originalList.mapIndexed { index, title -> index to title }
        assertEquals(0, mappedIndices[0].first)
        assertEquals("Song C", mappedIndices[0].second)
        assertEquals(1, mappedIndices[1].first)
        assertEquals("Song A", mappedIndices[1].second)
        assertEquals(2, mappedIndices[2].first)
        assertEquals("Song D", mappedIndices[2].second)
        assertEquals(3, mappedIndices[3].first)
        assertEquals("Song B", mappedIndices[3].second)
    }

    @Test
    fun testSongEntityConversionAndMetadataIntegrity() {
        val originalTrack = Track(
            id = "test_song_1",
            title = "Midnight Odyssey",
            artist = "Aether",
            album = "Cosmic Waves",
            albumArtist = "Aether Collective",
            genre = "Synthwave",
            releaseYear = "2024",
            trackNumber = "04",
            discNumber = "01",
            composer = "A. Composer",
            comment = "Master copy",
            durationMs = 240000L,
            audioUri = "content://media/external/audio/123",
            artworkUri = "/data/user/0/app/files/MiniLyrics/Artwork/art_test.jpg",
            isDemo = false
        )

        // Convert to Room entity
        val entity = com.crintsoft.zikiplayer.data.room.entities.SongEntity.fromTrack(originalTrack)
        assertEquals("test_song_1", entity.id)
        assertEquals("Midnight Odyssey", entity.title)
        assertEquals("Aether", entity.artist)
        assertEquals("Cosmic Waves", entity.album)
        assertEquals("Aether Collective", entity.albumArtist)
        assertEquals("Synthwave", entity.genre)
        assertEquals("2024", entity.releaseYear)
        assertEquals("04", entity.trackNumber)
        assertEquals("01", entity.discNumber)
        assertEquals("A. Composer", entity.composer)
        assertEquals("Master copy", entity.comment)
        assertEquals("/data/user/0/app/files/MiniLyrics/Artwork/art_test.jpg", entity.artworkUri)

        // Convert back to Track
        val converted = entity.toTrack()
        assertEquals(originalTrack.id, converted.id)
        assertEquals(originalTrack.title, converted.title)
        assertEquals(originalTrack.artist, converted.artist)
        assertEquals(originalTrack.album, converted.album)
        assertEquals(originalTrack.albumArtist, converted.albumArtist)
        assertEquals(originalTrack.genre, converted.genre)
        assertEquals(originalTrack.releaseYear, converted.releaseYear)
        assertEquals(originalTrack.trackNumber, converted.trackNumber)
        assertEquals(originalTrack.discNumber, converted.discNumber)
        assertEquals(originalTrack.composer, converted.composer)
        assertEquals(originalTrack.comment, converted.comment)
        assertEquals(originalTrack.artworkUri, converted.artworkUri)
        assertTrue(converted.hasArtwork)
    }

    @Test
    fun testMetadataEditingUpdate() {
        val original = Track(
            id = "song_edit",
            title = "Old Title",
            artist = "Old Artist",
            album = "Old Album",
            durationMs = 150000L
        )

        val updated = original.copy(
            title = "New Title",
            artist = "New Artist",
            album = "New Album",
            genre = "Electronic",
            releaseYear = "2025",
            artworkUri = "/files/art.jpg"
        )

        val entity = com.crintsoft.zikiplayer.data.room.entities.SongEntity.fromTrack(updated)
        assertEquals("New Title", entity.title)
        assertEquals("New Artist", entity.artist)
        assertEquals("New Album", entity.album)
        assertEquals("Electronic", entity.genre)
        assertEquals("2025", entity.releaseYear)
        assertEquals("/files/art.jpg", entity.artworkUri)
    }
}

