# J-PLUGIN-SEARCH-PLAY Verification Matrix

## Functional Done
| Check | Evidence | Status |
|---|---|---|
| Default subscription install completes | settings result text + logs | Planned |
| Searchable plugin exists | search plugin selector or state | Planned |
| Controlled query returns non-empty result | search result row visible | Planned |
| Result opens player | player screen visible | Planned |
| Pause / resume can be observed | player control state + logs | Planned |

## Fidelity Done
| Check | Evidence | Status |
|---|---|---|
| Search page structure aligned with RN | screenshot + dump | Deferred |
| Result row structure aligned with RN | screenshot + dump | Deferred |
| Player controls aligned with RN | screenshot + dump | Deferred |

## Minimum Integration Tests
| Test | Target Path | Status |
|---|---|---|
| Happy path: install -> search -> open result -> player -> pause | `app/src/androidTest/.../journeys/PluginSearchPlayJourneyTest.kt` | Planned |
| Failure path: no searchable plugins -> search empty-state | `app/src/androidTest/.../journeys/PluginSearchPlayJourneyTest.kt` | Planned |

## Data Strategy
| Need | Source Type | Notes |
|---|---|---|
| Default subscription baseline | Controlled Live | See `specs/j-plugin-search-play/fixtures/controlled-live.md` — subscription source and install path are pinned there |
| Search queries | Controlled Live | See `specs/j-plugin-search-play/fixtures/controlled-live.md` — query set (`in the end`, `In The End Linkin Park`, `linkin park`) is pinned there |
| Pause / resume verification | Offline Deterministic or observable runtime state | Prefer state assertions over ranking assumptions |

## Evidence Requirements
| Evidence Type | Required For | Notes |
|---|---|---|
| Log markers | Functional Done | Must prove install, search, and playback state transitions |
| Screenshot | Fidelity Done | Deferred in this pilot |
| `uiautomator dump` | Fidelity Done | Deferred in this pilot |
| Test result summary | Functional Done | Must record passing journey test names |

## Gate Split
| Gate | Required Checks |
|---|---|
| Dev Gate | affected build + minimum journey test + logs not broken |
| Functional Gate | all Functional Done checks + minimum integration tests + evidence summary |
| Fidelity Gate | all Fidelity Done checks + screenshot + dump + diff review |

Note:
- `Functional Done` is the target of the first pilot.
- `Fidelity Done` is intentionally deferred to a later plan.
- Coding must not start until a dedicated `specs/j-plugin-search-play/plan.md` exists.
- Once that plan exists, execution should use internal self-check gates rather than repeated user confirmations.
