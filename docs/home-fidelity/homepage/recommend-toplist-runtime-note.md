# 推荐歌单与榜单运行态验收记录

> 文档状态：当前参考
> 适用范围：推荐歌单与榜单 RN 对齐实现后的本地运行态验收记录。
> 最后校验：2026-05-09

## 结果

运行态详情链路验收未完成。

## 已验证

- Debug APK 已安装到 `emulator-5554`。
- 应用可启动到首页。
- `首页 -> 推荐歌单` 可进入推荐歌单页面。
- `首页 -> 榜单` 可进入榜单页面。

## 阻塞原因

本地设备当前没有已安装插件数据，推荐歌单与榜单页面均显示“暂无已安装插件，请先在设置中安装插件”，因此无法验证真实插件数据下的歌单或榜单点击进入详情链路。

## 已完成静态闸门

- `./gradlew :feature:home:testDebugUnitTest --tests com.hank.musicfree.feature.home.pluginfeature.PluginCapabilityUiTest --tests com.hank.musicfree.feature.home.recommendsheets.RecommendSheetsViewModelTest --tests com.hank.musicfree.feature.home.toplist.TopListViewModelTest --tests com.hank.musicfree.feature.home.toplist.TopListDisplayTextTest --tests com.hank.musicfree.feature.home.pluginsheet.navigation.PluginSheetSeedStoreTest --tests com.hank.musicfree.feature.home.pluginsheet.navigation.PluginSheetRouteSeedTest --tests com.hank.musicfree.feature.home.pluginsheet.navigation.PluginSheetRouteSeedResolverTest`
- `./gradlew :app:testDebugUnitTest --tests com.hank.musicfree.RoutesTest`
- `./gradlew :app:assembleDebug`
