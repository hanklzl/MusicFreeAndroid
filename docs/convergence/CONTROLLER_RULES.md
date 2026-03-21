# MusicFree Convergence Controller Rules

Last updated: 2026-03-21

This file records controller-level rules from the user so future convergence loops can continue without re-discovering the same process constraints.

## Analysis Rules

- Do not rely only on screenshot comparison.
- Always analyze both:
  - original RN code
  - current Android code
- Use screenshots as visual anchors, not as the only source of truth.
- Use code-side gap analysis to identify:
  - missing reusable abstractions
  - false backlog items caused by stale docs
  - subsystem prerequisites hidden behind “missing pages”

## Visual Reference Rule

- User-provided original screenshots are important reference material and should inform convergence priorities.
- As of 2026-03-21, the user provided original screenshots covering:
  - splash
  - drawer/settings shell
  - player
  - local music list
  - home
  - playlist detail
  - search result list
  - history
- These images show that the current gap is large in both UI fidelity and capability density.

## Controller Behavior Rule

- While sub-agents are working, the controller must not only dispatch and wait.
- The controller must simultaneously:
  - analyze the next iteration
  - prepare the next task split
  - identify write-set conflicts
  - refine verification paths
  - keep the backlog and plan current

## Parallel Development Rule

- Parallel development is allowed only when tasks are truly independent.
- Independence is defined by disjoint write sets and shared-hotspot analysis, not by task names.
- Safe parallelism must be judged by file ownership and subsystem coupling.

### Allowed Pattern

- Multiple agents can work in parallel on different, non-overlapping task packets.
- Branch-based development is acceptable.
- Worktrees are optional tooling, not a mandatory workflow requirement.

### Disallowed Pattern

- Do not parallelize tasks that both change shared hot spots such as:
  - `core/navigation/Routes.kt`
  - `app/navigation/AppNavHost.kt`
  - `feature/home/HomeScreen.kt`
  - shared repositories/DAOs/controllers
  - `docs/convergence/STATUS.md`
  - the same iteration analysis/verification docs

## Acceptance Rule

- Compile, unit tests, integration/connected tests, emulator real-click validation, and final review must be centralized.
- Final acceptance should be performed by the same controller/reviewer lane.
- Individual implementation agents may complete code, but they do not decide that work is fully accepted.
- Runtime/emulator validation has priority over code-review optimism.

## Convergence Priority Rule

- Do not prioritize work by raw page count alone.
- Prioritize by:
  - real user journeys
  - capability leverage
  - reusable foundations
  - confidence gaps in already-landed flows

### Current prioritization lens

- Shared collection tooling is high leverage:
  - `searchMusicList`
  - `musicListEditor`
  - `fileSelector`
- `downloading` depends on downloader core; page work should not come first.
- `setCustomTheme` depends on theme/runtime/storage infrastructure; it is not just a UI page.
- Existing plugin-backed flows require repeated real-data validation, not only compile proof.

## Process Lesson From Iteration 13

- A feature can pass compile, unit tests, spec review, and code review, and still fail final acceptance at runtime.
- This happened with `searchMusicList`, where emulator validation exposed a startup crash caused by Navigation route typing.
- Future iterations must preserve a single final runtime acceptance gate.

