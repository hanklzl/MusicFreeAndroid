# RN Anchor Map (Home Fidelity)

This manifest maps canonical home-fidelity anchors to RN nodes in `/Users/zili/code/android/MusicFree`.

| Canonical key | RN file path | Node type | Page state required for capture |
| --- | --- | --- | --- |
| `home.navBar.root` | `src/pages/home/components/navBar.tsx` | `testID` | `home-top`: home screen visible, nav bar in viewport |
| `home.navBar.menu` | `src/pages/home/components/navBar.tsx` | `testID` | `home-top`: home screen visible, nav bar in viewport |
| `home.navBar.search` | `src/pages/home/components/navBar.tsx` | `testID` | `home-top`: home screen visible, nav bar in viewport |
| `home.operations.root` | `src/pages/home/components/homeBody/operations.tsx` | `testID` | `home-top`: home operations row visible |
| `home.operations.recommendSheets` | `src/pages/home/components/homeBody/operations.tsx` | `testID` | `home-top`: home operations row visible |
| `home.operations.topList` | `src/pages/home/components/homeBody/operations.tsx` | `testID` | `home-top`: home operations row visible |
| `home.operations.history` | `src/pages/home/components/homeBody/operations.tsx` | `testID` | `home-top`: home operations row visible |
| `home.operations.localMusic` | `src/pages/home/components/homeBody/operations.tsx` | `testID` | `home-top`: home operations row visible |
| `home.sheets.root` | `src/pages/home/components/homeBody/sheets.tsx` | `testID` | `home-sheets`: scroll to playlists section |
| `home.sheets.tab.mine` | `src/pages/home/components/homeBody/sheets.tsx` | `testID` | `home-sheets`: playlists section visible |
| `home.sheets.tab.starred` | `src/pages/home/components/homeBody/sheets.tsx` | `testID` | `home-sheets`: playlists section visible |
| `home.sheets.action.create` | `src/pages/home/components/homeBody/sheets.tsx` | `testID` | `home-sheets`: playlists section visible |
| `home.sheets.action.import` | `src/pages/home/components/homeBody/sheets.tsx` | `testID` | `home-sheets`: playlists section visible |
| `home.drawer.root` | `src/pages/home/components/drawer/index.tsx` | `testID` | `drawer-open`: drawer panel visible from home screen |
| `home.drawer.settings` | `src/pages/home/components/drawer/index.tsx` | `testID` | `drawer-open`: drawer panel visible from home screen |
| `home.drawer.pluginManagement` | `src/pages/home/components/drawer/index.tsx` | `testID` | `drawer-open`: drawer panel visible from home screen |
| `home.drawer.permissions` | `src/pages/home/components/drawer/index.tsx` | `testID` | `drawer-open`: drawer panel visible from home screen |
| `screen.search.root` | `src/pages/searchPage/index.tsx` | `testID` | navigate from `home.navBar.search` |
| `screen.recommendSheets.root` | `src/pages/recommendSheets/index.tsx` | `testID` | navigate from `home.operations.recommendSheets` |
| `screen.topList.root` | `src/pages/topList/index.tsx` | `testID` | navigate from `home.operations.topList` |
| `screen.history.root` | `src/pages/history/index.tsx` | `testID` | navigate from `home.operations.history` |
| `screen.settings.root` | `src/pages/setting/index.tsx` | `testID` | navigate from `home.drawer.settings` or `home.drawer.pluginManagement` |
| `screen.local.root` | `src/pages/localMusic/index.tsx` | `testID` | navigate from `home.operations.localMusic` |
