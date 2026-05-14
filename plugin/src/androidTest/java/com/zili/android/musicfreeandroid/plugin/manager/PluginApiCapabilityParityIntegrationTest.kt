package com.zili.android.musicfreeandroid.plugin.manager

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.plugin.api.AlbumItemBase
import com.zili.android.musicfreeandroid.plugin.api.ArtistItemBase
import com.zili.android.musicfreeandroid.plugin.api.MusicSheetItemBase
import com.zili.android.musicfreeandroid.plugin.api.PluginSearchItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Local QuickJS parity probe for the full RN PluginApi surface.
 *
 * RN oracle tests keep the static method list aligned; this test proves Android
 * can actually invoke and parse representative payloads from all 14 methods.
 * It is intentionally local-only so the default instrumentation channel remains
 * deterministic while live-network tests focus on upstream plugin behavior.
 */
@RunWith(AndroidJUnit4::class)
class PluginApiCapabilityParityIntegrationTest {

    private lateinit var pluginManager: PluginManager
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        cacheDir = appContext.cacheDir
        pluginManager = makeTestManager(prefix = "plugin-api-parity-it", appContext = appContext)
    }

    @After
    fun tearDown() {
        runBlocking { pluginManager.uninstallAllPlugins() }
    }

    @Test
    fun localProbe_exercisesAllRnPluginApiMethods() = runBlocking {
        val pluginFile = File.createTempFile("plugin-api-parity-", ".js", cacheDir)
        pluginFile.writeText(PROBE_PLUGIN_SCRIPT)

        val plugin = pluginManager.installFromFile(pluginFile)
        assertNotNull("probe plugin should install", plugin)
        plugin ?: return@runBlocking

        assertEquals(EXPECTED_RN_METHODS, plugin.info.supportedMethods)

        val search = plugin.search(query = "needle", page = 2, type = "music")
        assertFalse(search.isEnd)
        val searchItem = (search.data.single() as PluginSearchItem.Music).item
        assertEquals("needle-2-music", searchItem.id)
        assertEquals("rn-parity-probe", searchItem.platform)
        assertEquals(123_000L, searchItem.duration)

        val mediaSource = plugin.getMediaSource(searchItem, quality = "standard")
        assertNotNull(mediaSource)
        assertEquals("https://media.example/needle-2-music/standard.mp3", mediaSource!!.url)
        assertEquals("probe-agent", mediaSource.userAgent)
        assertEquals("https://example.com", mediaSource.headers?.get("Referer"))
        assertEquals(PlayQuality.STANDARD, mediaSource.quality)

        val musicInfo = plugin.getMusicInfo(searchItem)
        assertNotNull(musicInfo)
        assertEquals(searchItem.id, musicInfo!!.id)
        assertEquals("rn-parity-probe", musicInfo.platform)
        assertEquals("Detailed Song", musicInfo.title)
        assertEquals(456_000L, musicInfo.duration)

        val lyric = plugin.getLyric(searchItem)
        assertNotNull(lyric)
        assertEquals("[00:01.00]Hello", lyric!!.rawLrc)
        assertEquals("[00:01.00]Ni hao", lyric.translation)
        assertEquals(1_000L, lyric.lines.single().timeMs)

        val albumInfo = plugin.getAlbumInfo(albumItem(), page = 3)
        assertNotNull(albumInfo)
        assertFalse(albumInfo!!.isEnd)
        assertEquals("album-base", albumInfo.albumItem?.id)
        assertEquals("rn-parity-probe", albumInfo.albumItem?.platform)
        assertEquals(listOf("album-song-3"), albumInfo.musicList.map { it.id })

        val artistWorks = plugin.getArtistWorks(artistItem(), page = 4, type = "music")
        assertNotNull(artistWorks)
        assertFalse(artistWorks!!.isEnd)
        assertEquals("music", artistWorks.type)
        assertEquals(listOf("artist-song-4"), artistWorks.musicList.map { it.id })
        assertEquals(1, artistWorks.rawData.size)

        val importedSheet = plugin.importMusicSheet("https://example.com/sheet")
        assertNotNull(importedSheet)
        assertEquals(listOf("imported-1", "imported-2"), importedSheet!!.map { it.id })
        assertEquals(listOf("rn-parity-probe", "rn-parity-probe"), importedSheet.map { it.platform })

        val importedItem = plugin.importMusicItem("https://example.com/song")
        assertNotNull(importedItem)
        assertEquals("imported-item", importedItem!!.id)
        assertEquals("rn-parity-probe", importedItem.platform)

        val topLists = plugin.getTopLists()
        assertEquals("Top Group", topLists.single().title)
        assertEquals("top-list-1", topLists.single().data.single().id)
        assertEquals("rn-parity-probe", topLists.single().data.single().platform)

        val topListDetail = plugin.getTopListDetail(sheetItem("top-list-1"), page = 5)
        assertNotNull(topListDetail)
        assertFalse(topListDetail!!.isEnd)
        assertEquals("top-list-1", topListDetail.topListItem?.id)
        assertEquals("rn-parity-probe", topListDetail.topListItem?.platform)
        assertEquals(listOf("top-song-5"), topListDetail.musicList.map { it.id })

        val sheetInfo = plugin.getMusicSheetInfo(sheetItem("sheet-base"), page = 6)
        assertNotNull(sheetInfo)
        assertFalse(sheetInfo!!.isEnd)
        assertEquals("sheet-base", sheetInfo.sheetItem?.id)
        assertEquals("rn-parity-probe", sheetInfo.sheetItem?.platform)
        assertEquals(listOf("sheet-song-6"), sheetInfo.musicList.map { it.id })

        val recommendTags = plugin.getRecommendSheetTags()
        assertNotNull(recommendTags)
        assertEquals("tag-pinned", recommendTags!!.pinned.single().id)
        assertEquals("rn-parity-probe", recommendTags.pinned.single().platform)
        assertEquals("tag-group", recommendTags.data.single().data.single().id)
        assertEquals("rn-parity-probe", recommendTags.data.single().data.single().platform)

        val recommendedSheets = plugin.getRecommendSheetsByTag(
            tag = mapOf("id" to "tag-pinned", "title" to "Pinned"),
            page = 7,
        )
        assertNotNull(recommendedSheets)
        assertFalse(recommendedSheets!!.isEnd)
        assertEquals(listOf("recommend-sheet-7"), recommendedSheets.data.map { it.id })
        assertEquals(listOf("rn-parity-probe"), recommendedSheets.data.map { it.platform })

        val comments = plugin.getMusicComments(searchItem, page = 8)
        assertNotNull(comments)
        assertFalse(comments!!.isEnd)
        assertEquals("comment-8", comments.data.single().id)
        assertEquals("Probe User", comments.data.single().nickName)
        assertEquals("reply-8", comments.data.single().replies.single().id)
    }

    private fun albumItem(): AlbumItemBase = AlbumItemBase(
        id = "album-base",
        platform = "rn-parity-probe",
        title = "Album Base",
        date = null,
        artist = "Probe Artist",
        description = null,
        artwork = null,
        worksNum = null,
        raw = emptyMap(),
    )

    private fun artistItem(): ArtistItemBase = ArtistItemBase(
        id = "artist-base",
        platform = "rn-parity-probe",
        name = "Probe Artist",
        avatar = null,
        fans = null,
        description = null,
        worksNum = null,
        raw = emptyMap(),
    )

    private fun sheetItem(id: String): MusicSheetItemBase = MusicSheetItemBase(
        id = id,
        platform = "rn-parity-probe",
        title = "Sheet Base",
        artist = null,
        description = null,
        coverImg = null,
        artwork = null,
        worksNum = null,
        raw = emptyMap(),
    )

    private companion object {
        val EXPECTED_RN_METHODS = setOf(
            "search",
            "getMediaSource",
            "getMusicInfo",
            "getLyric",
            "getAlbumInfo",
            "getMusicSheetInfo",
            "getArtistWorks",
            "importMusicSheet",
            "importMusicItem",
            "getTopLists",
            "getTopListDetail",
            "getRecommendSheetTags",
            "getRecommendSheetsByTag",
            "getMusicComments",
        )

        val PROBE_PLUGIN_SCRIPT = """
            function music(id, title) {
              return {
                id,
                platform: '',
                title,
                artist: 'Probe Artist',
                album: 'Probe Album',
                duration: 123
              };
            }

            function sheet(id, title) {
              return {
                id,
                platform: '',
                title,
                description: 'Probe Sheet',
                cover: 'https://img.example/' + id + '.jpg',
                worksNum: 11
              };
            }

            module.exports = {
              platform: 'rn-parity-probe',
              version: '1.0.0',
              supportedSearchType: ['music', 'album', 'artist', 'sheet', 'lyric'],
              cacheControl: 'no-cache',
              async search(query, page, type) {
                return {
                  isEnd: false,
                  data: [music(query + '-' + page + '-' + type, 'Search Song')]
                };
              },
              async getMediaSource(musicItem, quality) {
                return {
                  url: 'https://media.example/' + musicItem.id + '/' + quality + '.mp3',
                  headers: { Referer: 'https://example.com' },
                  userAgent: 'probe-agent',
                  quality
                };
              },
              async getMusicInfo(musicItem) {
                return {
                  id: musicItem.id,
                  platform: '',
                  title: 'Detailed Song',
                  artist: 'Detailed Artist',
                  duration: 456
                };
              },
              async getLyric(musicItem) {
                return {
                  rawLrc: '[00:01.00]Hello',
                  rawLrcTxt: 'Hello',
                  translation: '[00:01.00]Ni hao'
                };
              },
              async getAlbumInfo(albumItem, page) {
                return {
                  isEnd: false,
                  albumItem: {
                    id: albumItem.id,
                    platform: '',
                    title: albumItem.title,
                    artist: albumItem.artist,
                    worksNum: 1
                  },
                  musicList: [music('album-song-' + page, 'Album Song')]
                };
              },
              async getMusicSheetInfo(sheetItem, page) {
                return {
                  isEnd: false,
                  sheetItem: sheet(sheetItem.id, sheetItem.title),
                  musicList: [music('sheet-song-' + page, 'Sheet Song')]
                };
              },
              async getArtistWorks(artistItem, page, type) {
                return {
                  isEnd: false,
                  data: [music('artist-song-' + page, 'Artist Song')]
                };
              },
              async importMusicSheet(urlLike) {
                return [
                  music('imported-1', 'Imported One'),
                  music('imported-2', 'Imported Two')
                ];
              },
              async importMusicItem(urlLike) {
                return music('imported-item', 'Imported Item');
              },
              async getTopLists() {
                return [{
                  title: 'Top Group',
                  data: [sheet('top-list-1', 'Top List')]
                }];
              },
              async getTopListDetail(topListItem, page) {
                return {
                  isEnd: false,
                  topListItem: sheet(topListItem.id, topListItem.title),
                  musicList: [music('top-song-' + page, 'Top Song')]
                };
              },
              async getRecommendSheetTags() {
                return {
                  pinned: [sheet('tag-pinned', 'Pinned')],
                  data: [{
                    title: 'Group',
                    data: [sheet('tag-group', 'Grouped')]
                  }]
                };
              },
              async getRecommendSheetsByTag(tag, page) {
                return {
                  isEnd: false,
                  data: [sheet('recommend-sheet-' + page, tag.title || 'Recommended')]
                };
              },
              async getMusicComments(musicItem, page) {
                return {
                  isEnd: false,
                  data: [{
                    id: 'comment-' + page,
                    nickName: 'Probe User',
                    comment: 'Nice',
                    like: 9,
                    createAt: 1000,
                    location: 'Earth',
                    replies: [{
                      id: 'reply-' + page,
                      nickName: 'Reply User',
                      comment: 'Agree'
                    }]
                  }]
                };
              }
            };
        """.trimIndent()
    }
}
