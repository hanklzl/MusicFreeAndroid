# Plugin Parity Matrix (Compose vs Original RN)

Feature: `001-plugin-parity-search`  
Default subscription baseline: `https://13413.kstore.vip/yuanli/yuanli.json`

## Status Legend

- `Aligned`: Compose behavior is consistent with Original RN behavior
- `Partially Aligned`: Compose supports part of the behavior
- `Not Aligned`: Compose behavior missing or significantly different

## Capability Matrix

| Capability ID | Stage | Capability | Original RN | Compose Current | Gap Status | Target Definition | Priority | Evidence Refs |
|---|---|---|---|---|---|---|---|---|
| plugin.add.url | add | Add plugin from URL | Supported | Supported | Aligned | URL install is stable and usable for search | P1 | `E-101` |
| plugin.add.local | add | Add plugin from local file | Supported | Supported | Aligned | User can complete local file install from settings UI | P1 | `E-102` |
| plugin.add.subscription | add | Add/update via subscription list | Supported | Supported（默认基线订阅） | Aligned | Subscription install/update behavior and result reporting align with RN baseline flow | P1 | `E-103` |
| plugin.update.single | update | Update single plugin | Supported | Supported | Aligned | Single-plugin update available with success/failure feedback | P1 | `E-104` |
| plugin.update.all | update | Update all plugins | Supported | Supported | Aligned | Batch update with per-plugin summary and recovery behavior | P1 | `E-105` |
| plugin.identity.unique | update | Keep plugin identity uniqueness | Supported | Supported | Aligned | Reinstall/update does not create duplicate plugin entries | P1 | `E-106` |
| plugin.search.selectable-set | search | Search plugin selectable set filtered by capability/state | Supported | Supported | Aligned | Only searchable and available plugins are selectable | P2 | `E-201` |
| plugin.search.query | search | Search songs with selected plugin | Supported | Supported | Aligned | Search returns success/empty/error states correctly | P2 | `E-202` |
| plugin.search.pagination | search | Search pagination behavior | Supported | Supported | Aligned | Load-more behavior keeps state consistency and non-destructive error handling | P2 | `E-203` |
| plugin.search.after-update | search | Search stability after plugin update | Supported | Supported | Aligned | Updated plugin is used by subsequent searches without reinstall | P2 | `E-204` |
| release.parity.gate | release | Release-level parity evidence gate | Supported by established process | Supported | Aligned | P1 capabilities must pass automated + manual evidence gate | P3 | `E-301` |

## Validation Rules

- Every `P1` row must include:
  - at least one automated verification reference
  - at least one manual comparison reference
- `Gap Status` can only be updated after evidence is linked in `Evidence Refs`.

## Update Workflow

1. Execute a capability scenario (Compose and Original RN with same steps).
2. Record evidence in `evidence-log.md`.
3. Update `Evidence Refs` in this matrix.
4. Re-evaluate `Gap Status`.
