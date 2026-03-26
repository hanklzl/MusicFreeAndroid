# home-top / nav-bar

- State: `home-top`
- Fragment: `nav-bar`
- Conclusion: `not closed`

## Canonical Anchors

- `screen.home.root`
- `home.navBar.root`
- `home.navBar.menu`
- `home.navBar.search`

## RN Evidence

- Source: `rn/raw/home-top-nav-bar.png`
- Crop: `rn/cropped/home-top-nav-bar.png`
- Dump: `rn/dumps/home-top-nav-bar.xml`

## Android Evidence

- Source: `android/raw/home-top-nav-bar.png`
- Crop: `android/cropped/home-top-nav-bar.png`
- Dump: `android/dumps/home-top-nav-bar.xml`

## Comparison

| Field | RN | Android | Evidence source | Status |
| --- | --- | --- | --- | --- |
| Canonical anchor order | `screen.home.root -> home.navBar.root -> home.navBar.menu -> home.navBar.search` | same | paired dumps | match |
| Visible text | search placeholder `点击这里开始搜索`; menu accessibility text `打开侧边栏` | search placeholder `点击这里开始搜索`; menu accessibility text `菜单` | paired dumps | drift |
| Clickability | `home.navBar.search=true`; `home.navBar.menu=false` in `uiautomator dump` | `home.navBar.search=true`; `home.navBar.menu=true` | paired dumps | drift |
| Selected/open state | search closed; drawer closed | search closed; drawer closed | no `home.drawer.root` in successful top capture dumps | match |
| Size | menu `[35,96][95,157]`; search `[130,81][1045,172]` | menu `[1,64][127,190]`; search `[128,64][1045,190]` | paired dumps | drift |
| Spacing | search starts `35px` to the right of menu's right edge; nav content sits lower inside the crop | search starts `1px` to the right of menu's right edge; nav content starts closer to the top inset | paired dumps | drift |
| Radius | not measured in this run | rounded search pill visible in Android crop | crops are the only available radius evidence for this fragment | blocked |
| Font | placeholder visible, but family/weight not derivable from dump | placeholder visible, but family/weight not derivable from dump | dumps show text only; no source-token review was done in this task | blocked |
| Color | not measured in this run | light neutral search background visible in Android crop | crops are the only available color evidence for this fragment | blocked |
| Icon or static-asset provenance | menu anchor is a `com.horcrux.svg.SvgView` with content-desc `打开侧边栏` | menu anchor is an `android.view.View` containing a composed icon with content-desc `菜单` | paired dumps | drift |

## Notes

- The RN top capture also contains runtime contamination below the fragment: `我的歌单 (1)`, a visible row `我喜欢`, and the debug warning overlay `Open debugger to view warnings.` in the same dump.
- The Android top capture reflects bootstrap data: `我的歌单 (0)`, `收藏歌单 (0)`, and visible empty-state text `暂无歌单`.
