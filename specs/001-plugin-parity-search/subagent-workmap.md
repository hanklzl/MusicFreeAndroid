# Sub-Agent Workmap

Feature: `001-plugin-parity-search`

## Ownership Rules

- Each sub-agent owns a disjoint write scope.
- No sub-agent may revert another sub-agent's changes.
- Main agent handles integration and conflict resolution.

## Work Split

| Agent | Scope | Primary Tasks | Write Paths |
|---|---|---|---|
| Main Agent | Orchestration + integration | T001-T004, cross-story integration, phase gates | `specs/001-plugin-parity-search/`, integration touch points |
| Sub-agent A | Plugin lifecycle core | T005-T008, T020-T021 | `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/` |
| Sub-agent B | Settings plugin UI | T022-T027 | `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/` |
| Sub-agent C | Search behavior parity | T031-T035 | `feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/` |
| Sub-agent D | Test and evidence | T017-T019, T029-T030, T037-T045 | `plugin/src/test/`, `plugin/src/androidTest/`, `feature/*/src/test/`, `docs/convergence/iteration-14/` |

## Integration Order

1. Merge Sub-agent A core lifecycle changes.
2. Merge Sub-agent B settings flow updates.
3. Merge Sub-agent C search behavior updates.
4. Merge Sub-agent D tests/evidence updates.
5. Main agent runs full validation and updates matrix statuses.

## Delivery Checklist (per sub-agent)

- [X] Changed file list included
- [X] Local validation commands listed
- [X] Risks/blockers listed
- [X] No unrelated files modified

## Delivery Verification

- Sub-agent A delivered plugin lifecycle core changes and update APIs; validated by `:plugin:testDebugUnitTest`.
- Sub-agent B delivered settings add/update flows and navigation wiring; validated by `SettingsViewModelTest`.
- Sub-agent C delivered search state/parity behavior; validated by `SearchViewModelTest`.
- Sub-agent D delivered test suites and convergence docs; validated by `connectedDebugAndroidTest` + gate scripts.
