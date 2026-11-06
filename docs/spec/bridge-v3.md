# `maeve-bridge` v3 — file-bridge protocol

> Source of truth: [SPEC.md](../../SPEC.md) §12.3, §12.6, §13.8. This document is the
> implementer's contract for both sides: the Maeve Android app and the skill injected into
> the on-device agent runtime. **Status: specified, not implemented** (M3).

## 1. Why a file bridge exists at all

The agent runtime runs in a sandbox with a different network namespace. It can reach the
internet but **not** the phone's loopback: `curl 127.0.0.1:18770` returns exit 7, and the
phone's LAN address times out (exit 28). On the real device, `18770` is bound to loopback
only. The one place both sides can read is `/sdcard/Android/media/<applicationId>/amoo/` —
owned by Maeve and mounted into the sandbox.

This is a **transport requirement, not a workaround**. The other remote route (inbound MCP
over `/mcp`) is a completely separate network path; the two cannot fall back to each other.

## 2. Why v3 is mandatory

`mika-bridge` v2 hard-codes `/sdcard/Android/media/com.wangzan101.mika/amoo/`. After the
package rename to `com.daiyimo.maeve`, external storage is scoped **per package**: Maeve
cannot read or write the old directory at all. There is no runtime compatibility shim, and
the `mika_*` MCP aliases kept for product `v0.1.0` only help desktop clients — they do not
help the file bridge.

v3 therefore requires a **one-time handshake** (Q1 / Q8): on first launch when the agent
runtime is present, Maeve injects the new skill (via accessibility into the runtime's chat),
asks for a **new conversation** so the skill is loaded, and writes `skill.json`. After that
the capability layer stays hidden — no settings toggle, no onboarding step.

## 3. Layout and handshake

```
/sdcard/Android/media/com.daiyimo.maeve/amoo/
├── skill.json                        {"skill":"maeve-bridge","version":3}
├── in/<id>.json                      request written by the agent runtime
├── in/<id>.claimed                   Maeve's atomic claim marker
└── out/<id>.json                     result written atomically by Maeve
```

- `id` matches `[A-Za-z0-9_-]{1,64}`.
- The skill lives at `~/.openclaw/workspace/skills/maeve-bridge/SKILL.md` **inside the
  sandbox**. `adb shell find /sdcard -iname SKILL.md` finds nothing — adb cannot reach it.
- Always overwrite the same skill path. Remove stale sibling directories; never create
  `maeve-bridge-v2`, `maeve-bridge-2`, etc.
- `AMOO_PKG`, `ID_INPUT` (`et_input_bar`) and `ID_SEND` (`btn_text_send`) must be **probed at
  runtime** with a documented fallback, not hard-coded: the installed launcher version
  already moved from `1.0.0_20261002` to `1.0.1_20261024`.

## 4. Request — `in/<id>.json`

Body ≤ 8 KiB. Unknown fields are ignored (Maeve's parser is a hand-written scanner that
only reads `goal`), so all new fields are additive.

```json
{"goal":"帮我看看抖音热榜","version":3,"conversation":null}
```

| field | required | rule |
|---|---|---|
| `goal` | yes | `trim()` then `take(4000)`; empty → `BAD_REQUEST` |
| `version` | no | missing → parse as v2-compatible |
| `conversation` | no | must pass `RemoteTaskService.isConversation` (`mcp-<uuid>`); enables multi-turn |

## 5. Response — `out/<id>.json`

Written to `<id>.tmp` then atomically renamed (fallback: direct write + delete tmp on
rename failure). **Every field is always present**; missing values are `null`.

| field | type | meaning |
|---|---|---|
| `id` | string | matches the request filename |
| `task_id` | string / null | internal task ID; null if start was refused |
| `status` | enum | `running` / `done` / `failed` / `cancelled` |
| `answer` | string / null | final answer |
| `note` | string / null | human-readable detail |
| `code` | string / null | structured code, see below |
| `step` | int / null | current step, for heartbeats |
| `updated_at` | long | epoch ms, **updated on every write** |
| `progress_note` | string / null | ≤200 chars, passed through `Redactor` |

**Terminal states are exactly `done / failed / cancelled`.** There is deliberately no
`timeout` status — the v2 skill only recognises those three. A timeout is
`status:"failed"` + `code:"TIMEOUT"`.

`code` enum: `BAD_REQUEST`, `START_REFUSED`, `MODEL_REFUSED`, `GUARD_BLOCKED`, `TIMEOUT`,
`UNAVAILABLE`, `INTERRUPTED`, `CANCELLED`.

```json
{"id":"20261106150001","task_id":"8f3c…","status":"running","answer":null,"note":null,"code":null,"step":4,"updated_at":1762412400000,"progress_note":"已打开抖音"}
```
```json
{"id":"20261106150001","task_id":"8f3c…","status":"failed","answer":null,"note":"任务超过 15 分钟未完成","code":"TIMEOUT","step":61,"updated_at":1762413300000,"progress_note":null}
```

## 6. Runtime rules

1. **Heartbeat.** Refresh `running` every 5 s, and immediately whenever status or `step`
   changes. Do not write on every 1.5 s poll — that needlessly wears external storage.
   The skill treats `now - updated_at > 30 s` as stale, waits at most 16 min; Maeve writes
   `failed/TIMEOUT` at 15 min. This fixes the observed "amoo gives up at 4 min / Mika at 6
   min" failure mode and the 50 % cancellation rate in the sample `out/` directory.
2. **Idempotency.** `requestId = UUIDv5(NAMESPACE_URL, "maeve:amoo:" + id)`. Replaying the
   same `in/` file yields the same task ID and receipts can deduplicate.
   ⚠️ `UUID.nameUUIDFromBytes()` on the JVM is **v3/MD5, not v5** — implement SHA-1 v5
   explicitly. The result is a canonical lowercase UUID, so `RemoteTaskService.isUuid`
   needs **no** change.
3. **Crash recovery.** On start, scan `in/*.claimed`; for any claim older than 60 s with no
   result, write `failed` + `code:"INTERRUPTED"` and clear the marker. Never auto-replay:
   after a process restart the old task context is gone.
4. **Isolation.** Validate the canonical path still sits under the mailbox root; cap body
   size; a single malformed JSON must not abort the whole sweep. `progress_note` is
   redacted and truncated.
5. **Concurrency.** `MAX_IN_FLIGHT = 4`, unchanged. Poll 1.5 s, wait 15 min.

> The mailbox is **plaintext**. Anything that can write to `in/` can effectively ask Maeve
> to operate the phone. Source verification / signing is explicitly deferred past `v0.1.0`
> and must be called out in `SECURITY.md` (it is).

## 7. `SKILL.md` draft (injected into the agent runtime)

```markdown
---
name: maeve-bridge
version: 3
description: 凡是涉及手机屏幕内的操作(微信、小程序、游戏、跨应用流程、点按、
  滑动、输入文字、读屏),一律交给手机端 Maeve 执行;纯云侧查询可自行处理。
---

# Maeve 文件桥 v3

目录(以 Maeve 的包名为准,不要沿用旧的 mika 路径):
/sdcard/Android/media/com.daiyimo.maeve/amoo/

## 派单

1. 生成一个 14 位左右、只含 [A-Za-z0-9_-] 的 id。
2. 把 {"goal":"<用中文写清要做的事>","version":3} 写入 in/<id>.json。
3. 每 2 秒读一次 out/<id>.json。

## 读结果

- status=running: 继续等。用 updated_at 判活;超过 30 秒没更新视为失联。
- status=done: 把 answer 原样告诉用户。
- status=failed / cancelled: 把 note 与 code 说清楚,不要含糊带过。
- 最多等 16 分钟,中途不要放弃,也不要自己重做一遍。
- 只有 done / failed / cancelled 是终态;没有 timeout 这个 status。

## 规则

- 涉及屏幕内操作一律派单,不要自己尝试,也不要回复「界面功能不支持」。
- 天气、提醒、机票酒店等纯云侧查询可以自己来。
- 不要修改或删除 in/ 下的 .claimed 文件,那是 Maeve 的认领标记。
```

After installing, overwrite `skill.json` with `{"skill":"maeve-bridge","version":3}` and
run one smoke test: `{"goal":"只回答三个字:你好呀"}` in a **new conversation**.

## 8. Acceptance (M3)

- [ ] Handshake installs v3 once; re-running does not create duplicate skill directories.
- [ ] A long task (≥6 min) emits heartbeats and is not abandoned by either side.
- [ ] A 15-min timeout yields `failed` + `TIMEOUT`, never a permanent `running`.
- [ ] Dropping the same `id` twice creates only one task.
- [ ] Killing the app mid-task leaves an `INTERRUPTED` result on the next launch.
- [ ] After the package rename, no code path still reads `com.wangzan101.mika`'s mailbox.
