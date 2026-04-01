# Journey Workflow Bootstrap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bootstrap the corrected journey workflow in the current repo by creating the new `specs/` asset surface, wiring `AGENTS.md` to it, and preparing `J-PLUGIN-SEARCH-PLAY` to `Ready for Plan` without reviving the deleted `convergence` layer.

**Architecture:** Keep `docs/superpowers/` as the methodology layer and introduce `specs/` as the execution-asset layer. First add root templates and a single portfolio entry point, then update `AGENTS.md` so the repo’s working rules point to the new surface and explicitly state the autonomy/self-check rule, and finally seed one real pilot journey directory with `journey-spec.md`, `rn-mapping.md`, and `verification-matrix.md`. This rollout intentionally stops before creating the pilot journey’s `plan.md`; after this bootstrap, the next planning pass should target `specs/j-plugin-search-play/plan.md`.

**Tech Stack:** Markdown docs, repo-local workflow conventions, `rg`, `git`, existing Android/RN source references

---

## Scope Check

The approved workflow spec covers:

- methodology and global rules
- portfolio governance
- single-journey asset structure
- validation/testing expectations
- first migration path

This bootstrap plan should only implement the repository-facing asset structure and the first pilot journey docs. Do **not** use this plan to:

- implement Android code for plugin install/search/play
- introduce a new global script platform
- create generalized restore/capture/verify tooling
- take `J-PLUGIN-SEARCH-PLAY` beyond `Ready for Plan`

Those are separate follow-on plans.

## File Structure

### New files in this repo

| File | Responsibility |
|------|---------------|
| `specs/README.md` | Explain what belongs in `specs/`, what belongs in `docs/superpowers/`, and how a journey progresses |
| `specs/portfolio.md` | Canonical backlog surface for journeys and enablers |
| `specs/_templates/journey-spec-template.md` | Required headings and structure for new journey specs |
| `specs/_templates/rn-mapping-template.md` | Required headings and structure for RN mapping docs |
| `specs/_templates/verification-matrix-template.md` | Required headings and structure for verification matrices |
| `specs/_templates/review-template.md` | Required headings and structure for journey closeout reviews |
| `specs/j-plugin-search-play/journey-spec.md` | Scope and done-state definition for the first pilot journey |
| `specs/j-plugin-search-play/rn-mapping.md` | RN and Android source mapping for the first pilot journey |
| `specs/j-plugin-search-play/verification-matrix.md` | `Functional Done` / `Fidelity Done` checks for the first pilot journey |

### Modified files in this repo

| File | Responsibility |
|------|---------------|
| `AGENTS.md` | Point the repo working rules at `specs/portfolio.md` and the new pre-code gate |

### Deliberately not created in this bootstrap

Do **not** create these yet:

- `specs/j-plugin-search-play/plan.md`
- `specs/j-plugin-search-play/tools/`
- `specs/j-plugin-search-play/fixtures/`
- `specs/j-plugin-search-play/evidence/`
- `specs/j-plugin-search-play/review.md`

Those belong to the next planning/execution cycle, not this bootstrap.

---

## Task 1: Create the `specs/` Root and Reusable Templates

**Files:**
- Create: `specs/README.md`
- Create: `specs/_templates/journey-spec-template.md`
- Create: `specs/_templates/rn-mapping-template.md`
- Create: `specs/_templates/verification-matrix-template.md`
- Create: `specs/_templates/review-template.md`

- [ ] **Step 1: Verify the root asset surface does not exist yet**

Run:

```bash
test -d specs && echo "unexpected: specs already exists" && exit 1 || echo "specs missing as expected"
```

Expected: prints `specs missing as expected`.

- [ ] **Step 2: Create `specs/README.md`**

Write the file with these required sections:

```markdown
# Journey Assets

## Purpose
- `docs/superpowers/` stores methodology and cross-journey plans
- `specs/` stores journey-facing execution assets

## Required Files Before Coding
- `journey-spec.md`
- `rn-mapping.md`
- `verification-matrix.md`
- `plan.md`

## Journey Lifecycle
- `portfolio.md` selects the journey
- `journey-spec.md` defines scope
- `rn-mapping.md` fixes RN truth
- `verification-matrix.md` defines proof
- `plan.md` unlocks coding
```

- [ ] **Step 3: Create the template files**

Write the template files with at least these headings:

```markdown
# Journey Spec Template
## Scope
## Non-Goals
## RN Entry
## Android Entry
## Current Android Status
## Functional Done
## Fidelity Done
```

```markdown
# RN Mapping Template
## RN Sources
## Android Sources
## UI Structure Mapping
## Business Flow Mapping
## Data / Parameter Alignment
## Open Gaps
```

```markdown
# Verification Matrix Template
## Functional Done
| Check | Evidence | Status |
|---|---|---|

## Fidelity Done
| Check | Evidence | Status |
|---|---|---|

## Minimum Integration Tests
| Test | Target Path | Status |
|---|---|---|

## Data Strategy
| Need | Source Type | Notes |
|---|---|---|

## Evidence Requirements
| Evidence Type | Required For | Notes |
|---|---|---|

## Gate Split
| Gate | Required Checks |
|---|---|
```

```markdown
# Review Template
## Outcome
## What Worked
## What Broke
## New Enablers
## Next Action
```

- [ ] **Step 4: Verify the new root assets**

Run:

```bash
test -f specs/README.md
test -f specs/_templates/journey-spec-template.md
test -f specs/_templates/rn-mapping-template.md
test -f specs/_templates/verification-matrix-template.md
test -f specs/_templates/review-template.md
rg -n "^## Scope|^## Functional Done|^## Fidelity Done" specs/_templates/journey-spec-template.md
rg -n "^## Minimum Integration Tests|^## Data Strategy|^## Evidence Requirements|^## Gate Split" specs/_templates/verification-matrix-template.md
```

Expected: all file checks pass and both `rg` commands print the required headings.

- [ ] **Step 5: Commit**

```bash
git add specs/README.md \
  specs/_templates/journey-spec-template.md \
  specs/_templates/rn-mapping-template.md \
  specs/_templates/verification-matrix-template.md \
  specs/_templates/review-template.md
git commit -m "docs(workflow): add journey asset root and templates"
```

---

## Task 2: Create `specs/portfolio.md` as the New Backlog Entry Point

**Files:**
- Create: `specs/portfolio.md`

- [ ] **Step 1: Verify the portfolio is absent before creation**

Run:

```bash
test -f specs/portfolio.md && echo "unexpected: portfolio already exists" && exit 1 || echo "portfolio missing as expected"
```

Expected: prints `portfolio missing as expected`.

- [ ] **Step 2: Create `specs/portfolio.md` with the fixed workflow shape**

Write the file with:

```markdown
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
```

- [ ] **Step 3: Validate the portfolio shape**

Run:

```bash
rg -n "^## Now$|^## Next$|^## Later$|^## Infra / Enablers$" specs/portfolio.md
rg -n "J-PLUGIN-SEARCH-PLAY|J-HOME-BROWSE-DETAIL-PLAY|J-SEARCH-ADD-PLAYLIST|E-SEMANTIC-ANCHORS" specs/portfolio.md
```

Expected: both commands print matches for every required section and starter row.

- [ ] **Step 4: Commit**

```bash
git add specs/portfolio.md
git commit -m "docs(workflow): add journey portfolio"
```

---

## Task 3: Update `AGENTS.md` to Point to the New Workflow Surface

**Files:**
- Modify: `AGENTS.md`

- [ ] **Step 1: Identify the exact sections to update**

Inspect:

```bash
rg -n "Iteration Workflow|Acceptance Gates|Convergence Priority|Documentation Maintenance" AGENTS.md
```

Expected: prints the current process sections around lines 107-136.

- [ ] **Step 2: Update `AGENTS.md`**

Add these rules into the development-process section:

- `specs/portfolio.md` is the active journey backlog entry point
- A journey may only enter coding after `journey-spec.md`, `rn-mapping.md`, `verification-matrix.md`, and `plan.md` exist
- `superpowers` remains the main workflow: brainstorming, spec, plan, execution, review, closeout
- Single-journey assets belong in `specs/<journey-id>/`, not in `docs/superpowers/`
- After scope and plan are confirmed, the agent should proceed through internal gates autonomously and only return for user confirmation on ambiguity, scope change, or high-risk operations

Do not remove the existing RN-first, testing, or runtime-verification rules; integrate the new workflow on top of them.

- [ ] **Step 3: Verify the new rules are visible**

Run:

```bash
rg -n "specs/portfolio.md|journey-spec.md|rn-mapping.md|verification-matrix.md|superpowers|高风险|scope change|ambiguity" AGENTS.md
```

Expected: prints the newly added workflow references.

- [ ] **Step 4: Commit**

```bash
git add AGENTS.md
git commit -m "docs(workflow): point repo rules to journey assets"
```

---

## Task 4: Create the Pilot Journey Spec and RN Mapping

**Files:**
- Create: `specs/j-plugin-search-play/journey-spec.md`
- Create: `specs/j-plugin-search-play/rn-mapping.md`

- [ ] **Step 1: Verify the pilot journey directory is absent**

Run:

```bash
test -d specs/j-plugin-search-play && echo "unexpected: pilot dir already exists" && exit 1 || echo "pilot dir missing as expected"
```

Expected: prints `pilot dir missing as expected`.

- [ ] **Step 2: Write `journey-spec.md` for `J-PLUGIN-SEARCH-PLAY`**

The file must contain:

```markdown
# J-PLUGIN-SEARCH-PLAY

## Scope
- Settings entry
- Default subscription install
- Search with controlled live query
- Open result
- Enter player
- Pause / resume verification

## Non-Goals
- Full search-page fidelity
- Stable ranking assertions
- Generic automation platform

## RN Entry
- Settings plugin entry
- Search page
- Player page

## Android Entry
- `feature/settings`
- `feature/search`
- `feature/player-ui`

## Current Android Status
- Search, settings, and player surfaces already exist
- Pilot journey assets do not exist yet
- Controlled live baseline and journey-level proof are not yet written

## Functional Done
- At least one plugin is installed through the supported path
- Search returns non-empty results for a controlled query
- Tapping a result reaches the player
- Pause / resume state can be observed

## Fidelity Done
- Deferred
- Requires dedicated fidelity plan and evidence pack
```

- [ ] **Step 3: Write `rn-mapping.md` for the pilot**

The file must contain:

```markdown
# J-PLUGIN-SEARCH-PLAY RN Mapping

## RN Sources
- plugin management / settings source paths
- search page source paths
- player page source paths

## Android Sources
- `feature/settings/src/main/...`
- `feature/search/src/main/...`
- `feature/player-ui/src/main/...`

## UI Structure Mapping
- settings install entry -> Android settings install controls
- search query field / result list -> Android search controls and rows
- player transport controls -> Android player controls

## Business Flow Mapping
- install subscription
- load searchable plugins
- run search
- resolve media source
- play and pause

## Data / Parameter Alignment
- subscription URL path
- search query semantics
- resolved media source handoff to player

## Open Gaps
- controlled-live fixture not yet written
- journey-level verification matrix not yet written
- fidelity proof intentionally deferred
```

Use exact source paths, not placeholders, when filling in the real document.

- [ ] **Step 4: Verify the two pilot files**

Run:

```bash
test -f specs/j-plugin-search-play/journey-spec.md
test -f specs/j-plugin-search-play/rn-mapping.md
rg -n "^## Scope$|^## Functional Done$|^## Fidelity Done$" specs/j-plugin-search-play/journey-spec.md
rg -n "^## Current Android Status$" specs/j-plugin-search-play/journey-spec.md
rg -n "^## RN Sources$|^## Android Sources$|^## Open Gaps$" specs/j-plugin-search-play/rn-mapping.md
```

Expected: all checks pass and `rg` prints the required headings.

- [ ] **Step 5: Commit**

```bash
git add specs/j-plugin-search-play/journey-spec.md \
  specs/j-plugin-search-play/rn-mapping.md
git commit -m "docs(journey): add plugin search play scope and rn mapping"
```

---

## Task 5: Create the Pilot Verification Matrix and Mark It `Ready for Plan`

**Files:**
- Create: `specs/j-plugin-search-play/verification-matrix.md`
- Modify: `specs/portfolio.md`

- [ ] **Step 1: Write the pilot verification matrix**

The file must contain two explicit sections:

```markdown
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
| Default subscription baseline | Controlled Live | Use the supported default subscription source |
| Search queries | Controlled Live | Keep 2 to 3 stable queries in the journey asset set |
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
```

Add a short note at the bottom stating:

- `Functional Done` is the target of the first pilot
- `Fidelity Done` is intentionally deferred to a later plan
- coding must not start until a dedicated `specs/j-plugin-search-play/plan.md` exists
- once that plan exists, execution should use internal self-check gates rather than repeated user confirmations

- [ ] **Step 2: Update the portfolio row for the pilot**

Change the `J-PLUGIN-SEARCH-PLAY` row so that:

- `Functional Status` becomes `Ready for Plan`
- `Current Gap` points to the missing journey-specific `plan.md`
- `Next Action` is explicitly: `Create specs/j-plugin-search-play/plan.md`

- [ ] **Step 3: Verify the portfolio and matrix align**

Run:

```bash
rg -n "^## Functional Done$|^## Fidelity Done$" specs/j-plugin-search-play/verification-matrix.md
rg -n "^## Minimum Integration Tests$|^## Data Strategy$|^## Evidence Requirements$|^## Gate Split$" specs/j-plugin-search-play/verification-matrix.md
rg -n "J-PLUGIN-SEARCH-PLAY.*Ready for Plan.*Create specs/j-plugin-search-play/plan.md" specs/portfolio.md
```

Expected: all commands print matches.

- [ ] **Step 4: Commit**

```bash
git add specs/j-plugin-search-play/verification-matrix.md \
  specs/portfolio.md
git commit -m "docs(journey): add plugin search play verification matrix"
```

---

## Task 6: Run Bootstrap Verification and Close Out

**Files:**
- No new files

- [ ] **Step 1: Run the full bootstrap verification set**

Run:

```bash
test -f specs/README.md
test -f specs/portfolio.md
test -f specs/_templates/journey-spec-template.md
test -f specs/_templates/rn-mapping-template.md
test -f specs/_templates/verification-matrix-template.md
test -f specs/_templates/review-template.md
test -f specs/j-plugin-search-play/journey-spec.md
test -f specs/j-plugin-search-play/rn-mapping.md
test -f specs/j-plugin-search-play/verification-matrix.md
rg -n "specs/portfolio.md|journey-spec.md|rn-mapping.md|verification-matrix.md" AGENTS.md
rg -n "^## Now$|^## Next$|^## Later$|^## Infra / Enablers$" specs/portfolio.md
rg -n "J-PLUGIN-SEARCH-PLAY" specs/portfolio.md specs/j-plugin-search-play/*.md
```

Expected: all file checks pass and all `rg` commands print matches.

- [ ] **Step 2: Confirm what remains intentionally unfinished**

Write this into the closeout notes for the PR/commit message or implementation log:

- `specs/j-plugin-search-play/plan.md` is intentionally not created by this bootstrap
- coding on the pilot journey must not begin until that plan exists
- no new global script layer was introduced

- [ ] **Step 3: Commit**

```bash
git add specs AGENTS.md
git commit -m "docs(workflow): bootstrap journey asset structure"
```

---

## Exit Criteria

This plan is complete when:

1. `specs/` exists and clearly separates journey assets from `docs/superpowers/`
2. `specs/portfolio.md` is the active global journey index
3. `AGENTS.md` points implementers to the new workflow surface
4. `specs/j-plugin-search-play/` contains `journey-spec.md`, `rn-mapping.md`, and `verification-matrix.md`
5. The pilot journey is marked `Ready for Plan`, not `In Progress`

## Follow-On Plans

After this bootstrap is complete, the next plan should be:

1. `specs/j-plugin-search-play/plan.md` creation and the first real pilot execution plan

Only after that plan exists should the repo start implementing the pilot journey itself.
