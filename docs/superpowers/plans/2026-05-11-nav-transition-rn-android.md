# Android 页面切换动画 RN Android 对齐 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把普通页面切换动画从旧 `100ms` 调整到 RN Android 实际 `slide_from_right` medium animation 口径，并禁用 predictive back。

**Architecture:** 保持 `MusicFreeNavTransitions.kt` 作为唯一普通页面 transition helper；Manifest application 节点负责 predictive back opt-out；Dev Harness 文档与 contract tests 同步更新，避免规则漂移。

**Tech Stack:** Kotlin, Jetpack Compose Navigation, Android Manifest, JUnit4, Dev Harness grep checks.

---

### Task 1: 更新 contract tests

**Files:**
- Modify: `app/src/test/java/com/zili/android/musicfreeandroid/navigation/MusicFreeNavTransitionsTest.kt`
- Modify: `app/src/test/java/com/zili/android/musicfreeandroid/harness/contracts/UiNavAnimationDurationContractTest.kt`
- Modify: `app/src/test/java/com/zili/android/musicfreeandroid/SplashScreenResourceContractTest.kt`

- [ ] **Step 1: Write failing navigation duration tests**

Update both navigation duration tests to assert `400`:

```kotlin
assertEquals(400, MusicFreeScreenTransitionDurationMillis)
```

Use test names/comments that describe RN Android medium animation, not RN JS `animationDuration: 100`.

- [ ] **Step 2: Write failing predictive back manifest test**

Add this test to `SplashScreenResourceContractTest`:

```kotlin
@Test
fun `manifest disables predictive back at application level`() {
    val manifest = parseXml(appMain.resolve("AndroidManifest.xml"))
    val application = manifest.firstElement("application")

    assertAndroidAttribute(application, "enableOnBackInvokedCallback", "false")
}
```

- [ ] **Step 3: Run tests and verify RED**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*MusicFreeNavTransitionsTest' --tests '*UiNavAnimationDurationContractTest' --tests '*SplashScreenResourceContractTest' --no-daemon
```

Expected: FAIL because production duration is still `100` and Manifest does not yet declare
`android:enableOnBackInvokedCallback="false"`.

### Task 2: 更新实现

**Files:**
- Modify: `app/src/main/java/com/zili/android/musicfreeandroid/navigation/MusicFreeNavTransitions.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Change transition duration constant**

Update:

```kotlin
internal const val MusicFreeScreenTransitionDurationMillis = 400
```

Keep all four transition helpers using the shared `musicFreeScreenTransitionSpec`.

- [ ] **Step 2: Disable predictive back**

Add this attribute on the `application` node:

```xml
android:enableOnBackInvokedCallback="false"
```

Do not add Activity-level back handling.

- [ ] **Step 3: Run tests and verify GREEN**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*MusicFreeNavTransitionsTest' --tests '*UiNavAnimationDurationContractTest' --tests '*SplashScreenResourceContractTest' --no-daemon
```

Expected: BUILD SUCCESSFUL.

### Task 3: 更新 Dev Harness 文档

**Files:**
- Modify: `docs/dev-harness/ui/rules.md`
- Modify: `docs/dev-harness/ui/incidents.md`
- Modify: `docs/dev-harness/incidents/index.md`
- Modify: `docs/DOCS_STATUS.md`
- Modify: `.agents/skills/ui-harness-skill/references/navigation-animation.md`
- Modify: `.agents/skills/ui-harness-skill/SKILL.md`

- [ ] **Step 1: Update UI rule wording**

Change the nav animation rule anchor from the old `100ms` framing to an RN Android medium animation framing.
The rule must say:

```markdown
- 普通页面默认动画时长 MUST 为 `400ms`，按 RN Android 实际生效链路对齐 `slide_from_right` 的 `@android:integer/config_mediumAnimTime` 口径。
```

- [ ] **Step 2: Update incident wording**

Revise INC-2026-0006 so it records the corrected lesson:

```markdown
不要只读取 RN JS `animationDuration` 字面值；Android 上 `transitionDuration` setter 是 no-op，`slide_from_right` 使用系统 medium animation 资源。
```

- [ ] **Step 3: Update UI harness skill reference**

Change the skill reference from `100ms` to `400ms` and keep the instruction that `AppNavHost` must use
`MusicFreeNavTransitions.kt`.

- [ ] **Step 4: Run harness grep check**

Run:

```bash
python3 scripts/dev-harness/grep-check.py
```

Expected: all checks pass.

### Task 4: 收尾验证

**Files:**
- Verify all changed files.

- [ ] **Step 1: Run targeted unit tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*MusicFreeNavTransitionsTest' --tests '*UiNavAnimationDurationContractTest' --tests '*SplashScreenResourceContractTest' --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run debug build**

Run:

```bash
./gradlew :app:assembleDebug --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Inspect diff**

Run:

```bash
git diff -- app/src/main/java/com/zili/android/musicfreeandroid/navigation/MusicFreeNavTransitions.kt app/src/main/AndroidManifest.xml app/src/test/java/com/zili/android/musicfreeandroid/navigation/MusicFreeNavTransitionsTest.kt app/src/test/java/com/zili/android/musicfreeandroid/harness/contracts/UiNavAnimationDurationContractTest.kt app/src/test/java/com/zili/android/musicfreeandroid/SplashScreenResourceContractTest.kt docs/DOCS_STATUS.md docs/dev-harness/ui/rules.md docs/dev-harness/ui/incidents.md docs/dev-harness/incidents/index.md .agents/skills/ui-harness-skill/references/navigation-animation.md .agents/skills/ui-harness-skill/SKILL.md
```

Expected: only duration, predictive back, tests, and matching docs changed.
