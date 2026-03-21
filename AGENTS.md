# Repository Guidelines

## Project Structure & Module Organization
This is a multi-module Android project (`Kotlin + Compose + Hilt`). Key modules:
- `app/`: application entry, navigation host, DI wiring.
- `core/`: shared models, theme, navigation routes, reusable UI utilities.
- `data/`: Room/DataStore persistence and data sources.
- `player/`: Media3 playback engine and queue/controller logic.
- `plugin/`: QuickJS-based plugin runtime, JS bridge, plugin APIs.
- `feature/*`: UI feature modules (`home`, `search`, `settings`, `player-ui`).

Tests are in each module under `src/test`. Iteration artifacts and UI comparisons live in `docs/convergence/` and `docs/convergence/screenshots/`.

## Build, Test, and Development Commands
Use Gradle wrapper from repo root:
- `./gradlew :app:assembleDebug` - build debug APK.
- `./gradlew :feature:home:compileDebugKotlin :app:compileDebugKotlin` - fast compile sanity check.
- `./gradlew :app:testDebugUnitTest` - run app JVM unit tests.
- `./gradlew :app:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.RoutesTest"` - run route serialization tests.
- `./gradlew :plugin:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.plugin.engine.JsBridgeTest"` - validate plugin bridge parsing.

## Coding Style & Naming Conventions
- Follow Kotlin defaults: 4-space indentation, clear null-safety, immutable data classes for UI state.
- Naming: `PascalCase` for classes/composables (`MusicDetailScreen`), `camelCase` for methods/properties, route types as `*Route`.
- Keep feature logic in `ViewModel + UiState + Screen` triples.
- Prefer focused module changes; avoid leaking feature logic into `app/`.

## Testing Guidelines
- Frameworks: JUnit4, kotlinx-coroutines-test, Mockito-Kotlin, Turbine (plus Robolectric in `player`).
- Add/update tests in the touched module (`<module>/src/test/...`).
- Test names should be descriptive (backtick style is common), e.g. `fun \`MusicDetailRoute is serializable\`()`. 
- For navigation changes, always run `RoutesTest`; for plugin parsing, run `JsBridgeTest`.

## Commit & Pull Request Guidelines
- Preferred commit style mirrors history:
  - `feat(convergence-N): ...`
  - `docs(convergence-N): ...`
  - merge commits: `merge: convergence iteration-N implementation`
- Keep commits scoped (code vs docs separated when practical).
- PRs should include: changed modules, verification commands run, and screenshots for UI-affecting changes.
- When working on convergence loops, update `docs/convergence/STATUS.md` and the current `iteration-N` docs in the same PR.

## Security & Configuration Tips
- Do not commit secrets or modify `local.properties` in PRs.
- Plugin execution loads external JS; treat plugin URLs/sources as untrusted input and validate before use.
