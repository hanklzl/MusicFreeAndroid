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
| J-PLUGIN-SEARCH-PLAY | Plugin install to playback | Core plugin playback path | E-REAL-DATA-BASELINE, E-PLAYER-STATE-OBSERVABILITY | Ready for Plan | Not Started | Journey assets missing | Create pilot journey asset bundle | specs/j-plugin-search-play/ |

## Next
| Journey ID | Journey Name | User Value | Dependencies | Functional Status | Fidelity Status | Current Gap | Next Action | Asset Path |
|---|---|---|---|---|---|---|---|---|
| J-HOME-BROWSE-DETAIL-PLAY | Home browse to playback | Connect home fidelity with real playback | E-SEMANTIC-ANCHORS | In Discovery | In Analysis | Journey not decomposed yet | Complete journey spec after pilot | specs/j-home-browse-detail-play/ |

## Later
| Journey ID | Journey Name | User Value | Dependencies | Functional Status | Fidelity Status | Current Gap | Next Action | Asset Path |
|---|---|---|---|---|---|---|---|---|
| J-SEARCH-ADD-PLAYLIST | Search result into playlist | Validates search to persistence loop | E-JOURNEY-INTEGRATION-HARNESS | Not Started | Not Started | Not decomposed | Keep in backlog until pilot stabilizes | specs/j-search-add-playlist/ |

## Infra / Enablers
| ID | Name | Supports | Current Gap | Next Action |
|---|---|---|---|---|
| E-SEMANTIC-ANCHORS | Stable semantic anchor coverage | Home, search, player, settings journeys | Coverage inconsistent outside current screens | Expand only when a journey is blocked |
| E-REAL-DATA-BASELINE | Controlled live + frozen real definitions | Plugin/search/play journeys | No checked-in journey asset bundle yet | Seed pilot journey baseline docs |
| E-PLAYER-STATE-OBSERVABILITY | Playback state logs and assertions | Any playback journey | Player proof not yet normalized per journey | Define pilot verification checks |
| E-JOURNEY-INTEGRATION-HARNESS | Minimum journey-level integration test shape | All `Functional Done` journeys | First real pilot still missing | Bootstrap pilot journey docs first |
