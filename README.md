# Maeve

**She runs the loop. You keep your screen.**

An AI agent for Android that gets things done on a screen you never see — so your phone stays yours.

*A fork of [Mika](https://github.com/wangzan101/Mika) by wangzan101, licensed under Apache-2.0.
See [FORK.md](FORK.md) for the change log and [NOTICE](NOTICE) for attribution.*

*Maeve is not affiliated with or endorsed by HBO, Warner Bros., or Westworld.
The name is used as a common given name, referring to the show's setting.*

> *"Surviving is just another loop."* — Maeve Millay, *Westworld* S1E7

Release codename: **Dolores** · `v0.1.0` · [SPEC.md](SPEC.md)

---

## What it does

Tell Maeve what you need. It opens a hidden background display, reads the screen, and works
your apps and the web until the job is done. Your main screen is never taken over.

- **You keep your phone.** Work runs on a second virtual display. Keep scrolling, watching, typing.
- **Interrupt or take over, anytime.** Steer a running task mid-step, or grab the controls yourself.
- **It asks before anything irreversible.** Payments, deletes and sends pass through a confirmation gate.
- **It remembers.** Preferences and repeatable flows settle into memory and skills for next time.

## Two ways in

Maeve has one execution kernel and two ways of receiving a task. Both end up in the same
agent loop and the same guard chain — a remote task is not a second, weaker implementation.

| Route | Entry | Typical use |
|---|---|---|
| **R1 · On-device** | Maeve's own chat UI | Ask directly on the phone; accessibility + background virtual display do the work. |
| **R2 · Driven by another agent** | Inbound MCP over `/mcp`, or the on-device file bridge used by certain system-level agent runtimes | Let a desktop assistant or an on-device agent delegate a task to the phone, then read back the result. |

R1 needs no companion device. R2 is optional and off by default except for the file-bridge
capability layer, which is configured once and then stays out of the way. The two remote
transports are **not** interchangeable: the MCP endpoint is a network service, while the
file bridge exists because some sandboxed agent runtimes cannot reach the phone's loopback
port at all.

## What you need

- Android 11 (API 30) or newer. The background display works best on Android 14+.
- **One-time ADB or wireless-debugging authorization.** The virtual display is created by a
  helper process running as `shell`, and screen access goes through an accessibility service.
  There is no zero-permission setup.
- **Your own LLM provider.** Maeve executes on the device but planning needs a model; you
  configure the endpoint and key in Settings. No model weights are bundled.
- Optional for R2: a computer on the same network, or USB `adb forward`.

Plaintext HTTP is currently permitted by the network config — use a trusted endpoint and a
network you control. Do not read the confirmation gate as a blanket privacy guarantee.

## Build from source

```bash
git clone https://github.com/Daiyimo/Maeve.git
cd Maeve
./gradlew :app:assembleDebug
```

The project has two modules: `:core` is pure JVM Kotlin, `:app` is Android + Compose.
Release signing, R8 and CI are being set up as part of [M4](SPEC.md#10-里程碑与验收).

## Status

This repository is at **M2 (interaction layer)**: the published history starts from a single
Apache-2.0-attributed baseline snapshot, the package is `com.daiyimo.maeve`, and the
compliance/attribution layer is in place. See [FORK.md](FORK.md) for lineage.
The full M2 visual system, the connection hub and bridge v3 (M3), and release hardening
(M4) are not finished yet. Nothing here is a released binary.

| Where | What |
|---|---|
| [SPEC.md](SPEC.md) | Product definition, architecture, migration, compliance, milestones |
| `docs/spec/` | Visual tokens, haptics, bridge v3 protocol, migration notes |
| `docs/adr/` | Architecture decision records |
| `docs/reference/` | Archived analysis of the upstream Mika source (unchanged) |
| [FORK.md](FORK.md) | Upstream lineage, what changed, Apache-2.0 §4 statements |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). By contributing you agree that your contribution is
licensed under Apache-2.0 (inbound = outbound, §5); the upstream project has no CLA or DCO.

## License & attribution

Apache-2.0 — see [LICENSE](LICENSE). `NOTICE` retains the original
`Mika / Copyright 2026 wangzan101` verbatim. Third-party and GPL/LGPL components are listed
in [licenses/THIRD_PARTY_NOTICES.md](licenses/THIRD_PARTY_NOTICES.md) and mirrored into the APK.

Suggested topics: `android` `ai-agent` `llm-agent` `accessibility-service` `virtual-display`
`kotlin` `jetpack-compose` `apache-2.0`.
