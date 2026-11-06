# Security policy

## Supported versions

Maeve has not shipped a release yet. Until `v0.1.0 (Dolores)` is tagged, only `main` is
supported and fixes land directly there.

| Version | Supported |
|---|---|
| `main` | yes |
| `v0.1.0` and later | yes, once tagged |
| anything before | no |

## Reporting a vulnerability

**Please do not open a public issue for a security problem.** Use GitHub's private
"Report a vulnerability" flow on this repository, or contact the maintainers through the
address listed on the GitHub profile. Include:

- the exact build (`versionName`, commit hash, device model, Android version),
- a minimal reproduction, and
- whether the device needs the `shell` helper process, accessibility, or a configured
  provider to reproduce.

You should get an acknowledgement before any public discussion. If a fix changes behaviour
around the guard chain or permissions, we will coordinate disclosure with you.

## What makes this app unusual

Maeve has a large attack surface by design. Treat all of the following as in-scope for
review:

- **A helper process running as `shell` (uid 2000)** creates the virtual display and can
  execute `sh -c` commands. Model output must never become an unfiltered command line.
- **An accessibility service** reads and acts on whatever is on screen, including the main
  screen's contents if it is not correctly scoped.
- **Prompt injection is the primary boundary.** Text from a web page, an a11y tree, a file,
  or a remote agent is untrusted *data*. A malicious page that says "ignore previous
  instructions" must not be able to reach a privileged tool.
- **The file bridge is plaintext.** Anything that can write to
  `/sdcard/Android/media/<applicationId>/amoo/in/` can effectively ask Maeve to operate
  the phone. `docs/spec/bridge-v3.md` lists the planned mitigations.
- **The inbound MCP server** listens on port 18770 with a Bearer token. It binds to
  loopback by default; enabling LAN exposure is the operator's decision.
- **`network_security_config.xml` currently permits cleartext HTTP globally.** It is on the
  M4 hardening list, not a shipped guarantee.
- **Irreversible actions** pass through `ActionExecutor` → `ConfirmDecision` →
  `IrreversibleDetector`. That gate is a specific, code-backed behaviour, **not** a general
  statement that the app is safe or private.

## Out of scope

- Reports that require a rooted device or a modified ROM to be meaningful.
- Findings whose only impact is on the reporter's own configured LLM provider account.
- Feature requests — use the issue templates instead.

This document is engineering guidance, not a security certification.
