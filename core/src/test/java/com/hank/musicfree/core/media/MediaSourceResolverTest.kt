package com.hank.musicfree.core.media

import com.hank.musicfree.core.model.MediaSourceResult
import com.hank.musicfree.core.model.MusicItem
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class MediaSourceResolverTest {
    @Test
    fun `empty resolver returns null`() = runSuspend {
        val result = EmptyMediaSourceResolver.resolve(item("1"))
        assertNull(result)
    }

    @Test
    fun `resolution preserves original identity and resolver metadata`() {
        val original = item("1")
        val source = MediaSourceResult(
            url = "https://cdn.example.com/1.mp3",
            headers = mapOf("referer" to "https://example.com"),
            userAgent = "ua",
            quality = null,
        )
        val resolution = MediaSourceResolution(
            item = original.copy(url = source.url),
            source = source,
            requestedPlatform = "source",
            resolverPlatform = "target",
            redirected = true,
            cachePolicy = MediaSourceCachePolicy.NoCache,
        )

        assertEquals("1", resolution.item.id)
        assertEquals("source", resolution.item.platform)
        assertEquals("target", resolution.resolverPlatform)
        assertFalse(resolution.item.platform == resolution.resolverPlatform)
    }

    private fun item(id: String) = MusicItem(
        id = id,
        platform = "source",
        title = "Song $id",
        artist = "Artist",
        album = null,
        duration = 0L,
        url = null,
        artwork = null,
        qualities = null,
    )

    private fun runSuspend(block: suspend () -> Unit) {
        var failure: Throwable? = null
        block.startCoroutine(
            Continuation(EmptyCoroutineContext) { result ->
                failure = result.exceptionOrNull()
            },
        )
        failure?.let { throw it }
    }
}
