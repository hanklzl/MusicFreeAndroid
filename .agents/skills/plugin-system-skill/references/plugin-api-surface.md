# PluginApi Surface

当前 14 个核心能力（按 `../MusicFree/src/types/plugin.d.ts` 与 `:plugin/src/main/.../PluginApi.kt` 校对）：

1. `search`
2. `getMediaSource`
3. `getLyric`
4. `getMusicSheetInfo`
5. `getRecommendSheetsByTag`
6. `getMusicComments`
7. `getAlbumInfo`
8. `getArtistWorks`
9. `getTopLists`
10. `getTopListDetail`
11. `importMusicSheet`
12. `getMusicInfo`
13. `userVariables`（读写存储）
14. `subscription`（订阅源能力）

实际能力以 `:plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/api/PluginApi.kt` 为准；本表是 RN 对齐参考，不覆盖代码。

require shim 支持：`cheerio`、`crypto-js`、`dayjs`、`axios`、`qs`、`he`、`big-integer`。
