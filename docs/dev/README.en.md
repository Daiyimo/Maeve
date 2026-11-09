# Developer docs

[简体中文](README.md) | English

For people who want to read or change the code, or add knowledge. For building and opening PRs, read the [contributing guide](../../CONTRIBUTING.md) first. User docs are [one level up](../README.en.md).

## Read first

| Doc | What it covers |
| --- | --- |
| [architecture.md](architecture.md) | The overall structure as the code stands, with class names and file paths: main loop, tools, phone control, browser, sandbox, computer access, knowledge and skills |
| [project-standards.md](project-standards.md) | Engineering standards: module boundaries, layout and naming, commit format, docs rules and verification gates (Chinese) |
| [project-review.md](project-review.md) | The 2026-11-09 deep review: findings, evidence, priority and disposition (Chinese) |
| [roadmap.md](roadmap.md) | Done, next, known issues and deferred items |

## Flutter shell

| Doc | What it covers |
| --- | --- |
| [FLUTTER_MIGRATION.md](../../FLUTTER_MIGRATION.md) | Migration direction, module build order, toolchain and caches, prototype limits and validation |
| [flutter/README.md](../../flutter/README.md) | Module layout, commands and bridge contracts |

## Writing knowledge and skills

| Doc | What it covers |
| --- | --- |
| [knowledge-format.en.md](knowledge-format.en.md) ([简体中文](knowledge-format.md)) | Format of built-in app knowledge (`APP.md`), website knowledge (`SITE.md`) and skills (`SKILL.md`) |
| [skill-scripts.md](skill-scripts.md) | Where scripts that come with knowledge and skills go, and how they run in the sandbox |

## UI and experience

| Doc | What it covers |
| --- | --- |
| [copy-rules.md](copy-rules.md) | UI copy rules: titles, status values, buttons, empty states and `InfoTip` |
| [maeve-uiux-refactor.md](maeve-uiux-refactor.md) | The Compose-era UI/UX refactor: file map, design system, glass layering and verification |
| [ios-development-standard.md](ios-development-standard.md) | iOS development standard; Android and Flutter behaviour differences are recorded here too |

## Topic notes and verification records

| Doc | What it covers |
| --- | --- |
| [app-access.md](app-access.md) | App access rules, share grants and the limits of the standard phone tools |
| [account-signin.md](account-signin.md) | Signing in to model providers with an account (no key), and what is deliberately left out |
| [chatgpt-signin-check.md](chatgpt-signin-check.md) | Walking through "Sign in with ChatGPT" on a computer with the app's own code |
| [doubao-asr.md](doubao-asr.md) | Doubao speech recognition integration and limits |
| [amoo-bridge.md](amoo-bridge.md) | The amoo file bridge: mailbox, protocol and device verification |
| [amoo-connect.md](amoo-connect.md) | The amoo one-time handshake and skill injection |
| [amoo-intercept.md](amoo-intercept.md) | The amoo intercept and takeover path |
| [remote-device-access-check.md](remote-device-access-check.md) | Verification record for remote wake and unlock |
| [remote-mcp.md](remote-mcp.md) | Remote CLI / MCP design (Chinese); [English deployment guide](../../tools/relay/README.md) |

## Developer docs elsewhere in the repo

- [`eval/README.md`](../../eval/README.md): on-device evaluation.
- [`tools/sandbox/README.md`](../../tools/sandbox/README.md): rebuilding the sandbox's native programs and bundled runtime.
- [`tools/relay/README.md`](../../tools/relay/README.md): the self-hosted remote MCP gateway.
- [`tools/i18n/README.md`](../../tools/i18n/README.md): translation checks for the 10 locales.
- [`tooling/toolchain/README.md`](../../tooling/toolchain/README.md): pinned JDK / Gradle / AGP / Flutter toolchain, setup, self-check and reuse in another project.

Apart from the knowledge format and the entry docs, the developer docs are in Chinese for now.
