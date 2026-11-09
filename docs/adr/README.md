<!-- docs-check: spans-off — ADR index; type names are proposals -->
# Architecture decision records

Short, immutable records of decisions taken while turning Mika into Maeve. One file per
decision; a superseded ADR is marked **Superseded** and links to its replacement rather than
being edited in place.

| ADR | Title | Status | Spec |
|---|---|---|---|
| [0001](0001-decision-backend.md) | Reserve a `DecisionBackend` seam, ship no local model | Proposed | §13.4 |
| [0002](0002-mechanical-action-outcome.md) | Mechanical action outcomes; do not copy byte-exact screenshot diff | Proposed | §13.5 |
| [0003](0003-sensitive-app-policy.md) | Sensitive-app policy with mandatory `ASK_USER` | Proposed | §13.6 |
| [0004](0004-selfcheck-harness.md) | Extend `SelfCheck` into the official acceptance harness | Proposed | §13.7 |
| [0005](0005-bridge-v3.md) | `maeve-bridge` v3 with heartbeat, codes and idempotency | Proposed | §13.8 |

All five are **Proposed** until M2/M3/M4 implementation begins. None of them has been
applied to the source tree yet — the M1 cut is deliberately documentation + identity only,
so `git diff bbc8922a..HEAD` stays reviewable.

Template for new records: Context → Decision → Consequences → Acceptance → References.
