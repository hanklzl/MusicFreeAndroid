# Journey Portfolio

## Update Rules
- Update when a journey enters brainstorming
- Update when a journey enters `In Progress`
- Update when a journey reaches `Functional Done`
- Update when a journey reaches `Fidelity Done`
- Update when an `Enabler` is added, split, or closed

## Now
| Journey ID | Journey Name | User Value | Dependencies | Functional Status | Fidelity Status | Current Gap | Next Action | Asset Path |
|---|---|---|---|---|---|---|---|---|
| J-PLUGIN-SEARCH-PLAY | Plugin install to playback | Core plugin playback path | E-REAL-DATA-BASELINE, E-PLAYER-STATE-OBSERVABILITY | Ready for Plan | Not Started | Journey-specific `plan.md` missing | Create specs/j-plugin-search-play/plan.md | specs/j-plugin-search-play/ |

## Next
| Journey ID | Journey Name | User Value | Dependencies | Functional Status | Fidelity Status | Current Gap | Next Action | Asset Path |
|---|---|---|---|---|---|---|---|---|
| J-HOME-BROWSE-DETAIL-PLAY | Home browse to playback | Connect home fidelity with real playback | E-SEMANTIC-ANCHORS | In Brainstorming | In Brainstorming | Homepage root scope and acceptance baseline were not yet written down | Review `2026-04-10-home-root-alignment-design.md` and turn it into journey assets | specs/j-home-browse-detail-play/ |

## Later
| Journey ID | Journey Name | User Value | Dependencies | Functional Status | Fidelity Status | Current Gap | Next Action | Asset Path |
|---|---|---|---|---|---|---|---|---|
| J-SEARCH-ADD-PLAYLIST | Search result into playlist | Validates search to persistence loop | E-JOURNEY-INTEGRATION-HARNESS | Not Started | Not Started | Not decomposed | Keep in backlog until pilot stabilizes | specs/j-search-add-playlist/ |

## Infra / Enablers
| ID | Name | Supports | Current Gap | Next Action |
|---|---|---|---|---|
| E-SEMANTIC-ANCHORS | Stable semantic anchor coverage | Home, search, player, settings journeys | Coverage inconsistent outside current screens | Expand only when a journey is blocked |
| E-REAL-DATA-BASELINE | Controlled live + frozen real definitions | Plugin/search/play journeys | Controlled-live baseline still needs a dedicated fixture asset set and evidence pack | Seed the next planning pass with controlled-live fixture scope |
| E-PLAYER-STATE-OBSERVABILITY | Playback state logs and assertions | Any playback journey | Player proof not yet normalized per journey | Define pilot verification checks |
| E-JOURNEY-INTEGRATION-HARNESS | Minimum journey-level integration test shape | All `Functional Done` journeys | Needs a dedicated journey-specific plan and first executable harness pass | Define the first pilot journey harness in `plan.md` |
