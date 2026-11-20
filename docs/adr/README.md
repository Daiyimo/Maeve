<!-- docs-check: spans-off — ADR index; type names are proposals -->
# Architecture decision records

Short, immutable records of decisions taken while turning Maeve into Maeve. One file per
decision; a superseded ADR is marked **Superseded** and links to its replacement rather than
being edited in place.

| ADR | Title | Status | Spec |
|---|---|---|---|
| [0001](0001-decision-backend.md) | Reserve a `DecisionBackend` seam, ship no local model | Proposed | §13.4 |
| [0002](0002-mechanical-action-outcome.md) | Mechanical action outcomes; do not copy byte-exact screenshot diff | Proposed | §13.5 |
| [0003](0003-sensitive-app-policy.md) | Sensitive-app policy with mandatory `ASK_USER` | Proposed | §13.6 |
| [0004](0004-selfcheck-harness.md) | Extend `SelfCheck` into the official acceptance harness | Proposed | §13.7 |
| [0005](0005-bridge-v3.md) | `maeve-bridge` v3 with heartbeat, codes and idempotency | Superseded by current v7 implementation | Historical: §13.8 |

ADR-0001 through ADR-0004 remain **Proposed**. ADR-0005 is **Superseded** by the current
v7 / schemaVersion 1 file-bridge implementation; its replacement ADR is still pending.

Template for new records: Context → Decision → Consequences → Acceptance → References.
