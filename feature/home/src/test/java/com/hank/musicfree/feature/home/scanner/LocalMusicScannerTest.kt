package com.hank.musicfree.feature.home.scanner

import android.content.ContentResolver
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import com.hank.musicfree.core.model.MusicItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalMusicScannerTest {

    private val contentResolver: ContentResolver = mock()
    private val documentMapper: LocalMusicDocumentMapper = mock()

    @Test
    fun `scan recursively reads configured tree and returns audio files only`() = runTest {
        val treeUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AMusicFree")
        val rootChildrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        val nestedChildrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            "primary:MusicFree/Albums",
        )
        val rootCursor = MatrixCursor(documentProjection()).apply {
            addRow(arrayOf("primary:MusicFree/Albums", "Albums", DocumentsContract.Document.MIME_TYPE_DIR))
            addRow(arrayOf("primary:MusicFree/track-one.mp3", "track-one.mp3", "audio/mpeg"))
            addRow(arrayOf("primary:MusicFree/cover.jpg", "cover.jpg", "image/jpeg"))
        }
        val nestedCursor = MatrixCursor(documentProjection()).apply {
            addRow(arrayOf("primary:MusicFree/Albums/track-two.flac", "track-two.flac", "audio/flac"))
        }
        whenever(contentResolver.query(eq(rootChildrenUri), any(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(rootCursor)
        whenever(contentResolver.query(eq(nestedChildrenUri), any(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(nestedCursor)

        val trackOneUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, "primary:MusicFree/track-one.mp3")
        val trackTwoUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, "primary:MusicFree/Albums/track-two.flac")
        whenever(documentMapper.map(trackOneUri, "track-one.mp3")).thenReturn(
            MusicItem(
                id = "one",
                platform = "local",
                title = "Track One",
                artist = "Artist One",
                album = null,
                duration = 180_000L,
                url = trackOneUri.toString(),
                artwork = null,
                qualities = null,
            )
        )
        whenever(documentMapper.map(trackTwoUri, "track-two.flac")).thenReturn(
            MusicItem(
                id = "two",
                platform = "local",
                title = "Track Two",
                artist = "Artist Two",
                album = null,
                duration = 200_000L,
                url = trackTwoUri.toString(),
                artwork = null,
                qualities = null,
            )
        )

        val items = LocalMusicScanner(contentResolver, documentMapper).scan(treeUri.toString()).first()

        assertEquals(listOf("Track One", "Track Two"), items.map { it.title })
    }

    private fun documentProjection(): Array<String> = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
    )
}
