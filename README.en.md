<!-- translated-from: README.md @ 82f1ddd36909 -->
<p align="center">
  <a href="README.md">简体中文</a> · <b>English</b>
</p>

<div align="center">

<img src="docs/media/logo.png" alt="Maeve eclipse mark" width="112" />

# Maeve

**She runs the loop. You keep your screen.**

An AI agent for Android: say what you need, and it works your apps and the web on a hidden background screen until the job is done. Your phone stays yours.

<a href="https://github.com/Daiyimo/Maeve"><img alt="Android 11+" src="https://img.shields.io/badge/Android-11%2B-7C5CFF?style=flat-square&logo=android&logoColor=white&labelColor=2B2440"></a>
<a href="LICENSE"><img alt="License Apache-2.0" src="https://img.shields.io/badge/License-Apache--2.0-7C5CFF?style=flat-square&labelColor=2B2440"></a>
<a href="https://github.com/Daiyimo/Maeve/stargazers"><img alt="Star" src="https://img.shields.io/github/stars/Daiyimo/Maeve?style=flat-square&logo=github&label=Star&color=7C5CFF&labelColor=2B2440"></a>
<img alt="Status M2" src="https://img.shields.io/badge/Status-M2%20interaction-7C5CFF?style=flat-square&labelColor=2B2440">

<br/>

<img src="docs/media/hero-side.png" alt="Say one sentence and Maeve gets it done in the background: Maeve chat on the left, a verifiable task-completion record on the right" width="680" />

<sub>Maeve's chat on the left; a verifiable task-completion record on the right.</sub>

</div>

> *"Surviving is just another loop."* — Maeve Millay, *Westworld* S1E7

Release codename **Dolores** · `v0.1.0` · [SPEC.md](SPEC.md). Maeve is an independently maintained Apache-2.0 project.

## Get things done with one sentence

<table>
<tr>
<td align="center" width="33%"><img src="docs/media/tasks/delta-force-codes.png" width="230" alt="Maeve reads today's Delta Force special-ops codes and manufacturing picks in a WeChat mini-program" /><br/><b>"Search for the Delta Force mini-program in WeChat and check today's codes and picks"</b><br/><sub>Reads across pages and organizes the key facts</sub></td>
<td align="center" width="33%"><img src="docs/media/tasks/xiaohongshu-codes.png" width="230" alt="Maeve tries to find and decode Xiaohongshu access codes and clearly reports when it does not finish" /><br/><b>"Search Xiaohongshu for Guizang and decode the two access codes"</b><br/><sub>Says clearly when it does not finish instead of pretending</sub></td>
<td align="center" width="33%"><img src="docs/media/tasks/amoo-check-in.png" width="230" alt="Maeve checks the Amoo growth-plan check-in and produces a verifiable record" /><br/><b>"Open the Amoo growth plan from STEPX and check today's check-in"</b><br/><sub>Finishes with verifiable facts and an answer</sub></td>
</tr>
<tr>
<td align="center" width="33%"><img src="docs/media/tasks/amoo-device-check.png" width="230" alt="Maeve opens My Devices from Settings account management and checks the Amoo growth-plan check-in" /><br/><b>"Open My Devices from Settings account management and check Amoo check-in"</b><br/><sub>Follows the path into the account page and verifies the state</sub></td>
<td align="center" width="33%"><img src="docs/media/tasks/wechat-clawbot.jpg" width="230" alt="Maeve completes access-code decoding and an Amoo check-in query in one WeChat ClawBot conversation" /><br/><b>"Decode the note in WeChat ClawBot, then check Amoo check-in"</b><br/><sub>Completes several things in one conversation</sub></td>
<td align="center" width="33%"><img src="docs/media/tasks/amoo-task-demo.gif" width="230" alt="Maeve runs an Amoo task in the background while the main screen stays in WeChat" /><br/><b>"Start a new Amoo growth-plan task for me"</b><br/><sub>Runs in the background while you keep chatting</sub></td>
</tr>
</table>

<sub>Access codes and account values in the screenshots are redacted; unfinished tasks are labelled honestly.</sub>

<sub>The demos use apps popular in China, but Maeve isn't tied to them: most apps and websites work if it can see them; payment, banking and brokerage apps, plus password or verification-code pages, remain protected by safety rules.</sub>

## Why Maeve

| | |
|---|---|
| 📱 **Leaves your screen alone** | Opens apps, taps, types and scrolls on a separate background virtual screen; keep using your phone, tap the live view whenever you want to watch |
| 🧩 **Works with most apps** | Looks at the screen and finds buttons the way a person does: no app APIs, no per-app integration; self-drawn UIs via bundled OCR and a vision model |
| 🌐 **Ask right on the page** | The built-in browser opens any site; say what you want on the page you're reading and it works right there; touch the page and it stops |
| 🧠 **Gets better with use** | Remembers the preferences you tell it and turns finished tasks into skills, so next time is faster and steadier |
| 🔒 **Your data stays on your phone** | There is no Maeve server; use your own model key, or sign in with a ChatGPT / xAI / OpenRouter account |
| 🆓 **Open source and free** | Apache-2.0, all code public |

<br/>

<h3 align="center">Works in the background, your screen stays yours</h3>

<p align="center">Jobs run on a separate virtual screen. With several tasks at once, the "Background screens" page lists which app each task is using and how far it got; tap for the live view, tap again to go full screen.</p>

<h3 align="center">A floating capsule; double-tap to send your screen</h3>

<p align="center">While you're in another app, a capsule at the screen edge shows progress, things needing you, and results. Double-tap it to send the screen you're looking at to Maeve: "how should I reply?" or "note this down".</p>

<h3 align="center">Steer or stop anytime</h3>

<p align="center">Add a word or change the request mid-task; it reads it before the next step. After you stop, it reports what it did and what's left, so "continue" knows where to pick up.</p>

<h3 align="center">It asks before spending money</h3>

<p align="center">Autonomous mode by default: ordinary reversible actions continue, while outward or irreversible actions such as sending, submitting, deleting or placing an order still stop for one-time approval, and payment is always completed by you. Switch to Ask to also confirm every WeChat write and blind taps on self-drawn screens. Login and captchas hand over to you in both modes.</p>

<h3 align="center">Memory and skills, smoother with use</h3>

<p align="center">Remembers preferences and ways of doing things you explicitly stated; view, edit, delete them. Passwords, codes and card numbers are never stored; memory is encrypted on the phone. Finished flows become skills for next time.</p>

<h3 align="center">Ask right on the page in the browser</h3>

<p align="center">Opens any site and keeps you signed in. Say what you want on the page you're reading and it works right there; paging is paced, and it recognizes anti-bot blocks and hands them to you rather than forcing through.</p>

## Get started in three steps

1. **Install**: install Maeve on an Android device.
2. **Connect a model**: Settings › Model, pick a provider and enter a key, or sign in with an account ([models and sign-in](docs/models.en.md)).
3. **Authorize once**: follow the guide to authorize ADB / wireless debugging and accessibility so the background screen can start ([setup guide](docs/setup-guide.en.md)).

Then just say what you need. First time, see the [setup guide](docs/setup-guide.en.md) and [what it can do](docs/features.en.md).

## Two ways in

One execution kernel, two ways to receive a task, both through the same agent loop and guard chain — a remote task is not a weaker second implementation.

| Route | Entry | Typical use |
|---|---|---|
| **R1 · On-device** | Maeve's own chat UI | Ask directly on the phone; accessibility + background virtual display do the work |
| **R2 · Driven by another agent** | Inbound MCP over `/mcp`; the Amoo-only file bridge | Let a desktop assistant delegate a task to the phone, then read back the result ([computer](docs/computer.en.md) · [MCP](docs/mcp.en.md)) |

R1 needs no companion device. R2 is optional and off by default. The two remote transports are not interchangeable: MCP is the network service used by computer clients; the Amoo file bridge only serves Amoo's `/amoo/in|out` mailbox and is not a general sandbox channel.

## Architecture

<p align="center">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.4-7F52FF?style=flat-square&logo=kotlin&logoColor=white&labelColor=2B2440">
  <img alt="Flutter" src="https://img.shields.io/badge/Flutter-3.47-02569B?style=flat-square&logo=flutter&logoColor=white&labelColor=2B2440">
  <img alt="Gradle" src="https://img.shields.io/badge/Gradle-9.3-02303A?style=flat-square&logo=gradle&logoColor=white&labelColor=2B2440">
  <img alt="JDK 17" src="https://img.shields.io/badge/JDK-17-ED8B00?style=flat-square&logo=openjdk&logoColor=white&labelColor=2B2440">
  <img alt="Android 11+" src="https://img.shields.io/badge/Android-11%2B-3DDC84?style=flat-square&logo=android&logoColor=white&labelColor=2B2440">
</p>

One execution kernel, two shells: a **platform-free Kotlin kernel** does the thinking and deciding, the **Android host** sees the screen and acts, and the **Flutter shell** provides the UI. The kernel has no Android dependency, so the whole loop runs in JVM unit tests with a fake model.

<details>
<summary>Click to expand the architecture diagram</summary>

```mermaid
flowchart TD
    UI["Flutter shell<br/>chat · settings · capsule"] -->|MethodChannel / EventChannel| APP
    R2["Desktop CLI / MCP · file bridge"] --> APP
    subgraph APP[":app — Android host (Kotlin)"]
        A11Y["Accessibility reader"]
        VD["Background virtual display<br/>shell helper"]
        WEB["WebView browser"]
        SBX["Sandbox"]
    end
    APP --> CORE
    subgraph CORE[":core — platform-free kernel (Kotlin/JVM)"]
        LOOP["AgentLoop · scheduling"]
        EXEC["ActionExecutor · single entry"]
        GUARD["Guard chain · confirm gate · effect ledger"]
        KSM["Knowledge · skills · memory"]
        LOOP --> EXEC --> GUARD
    end
    CORE -->|OpenAI-compatible / Anthropic / Gemini / Responses| LLM["Model providers"]
```

</details>

| Layer | Tech | Responsibility |
|---|---|---|
| `flutter` | Flutter / Dart | The only application UI: liquid-glass chat, settings and floating capsule; talks to the host over MethodChannel/EventChannel |
| `app` | Kotlin / Android | Accessibility reader, background virtual display (shell helper), WebView, sandbox, foreground service, permissions |
| `core` | Kotlin / JVM | Agent loop, tool engine, context compression, guard chain, confirm gate, effect ledger, knowledge/skills/memory |
| `tools/relay` | Go (optional) | Single-user single-device public remote CLI / MCP gateway, not bundled in the APK |

A few designs that hold up reliability:

- **Single execution entry**: every action that changes the outside world goes through `ActionExecutor`, in the same order: guards → gate → irreversible check → execute → verify → record.
- **Guard chain and effect ledger**: operation guards prevent stalls and repeats, the confirm gate blocks irreversible actions, and the effect ledger guarantees receipts are never replayed and stops for human review when an outcome is unknown.
- **Platform-free kernel**: `core` references no Android; capabilities are injected via interfaces, so unit tests run on a desktop with a fake model and fake screen.
- **Capabilities don't reference each other**: phone / web / sandbox each depend only on the kernel; the assembly layer wires them, so adding a capability doesn't touch old code.

Finer layering and dependency direction are in [docs/dev/architecture.md](docs/dev/architecture.md).

## What you need

- Android 11 (API 30) or newer; the background screen works best on Android 14+.
- **One-time ADB or wireless-debugging authorization**: the virtual display is created by a helper running as `shell`, screen access goes through accessibility; there is no zero-permission setup.
- **Your own model provider**: execution on device, planning in the cloud; configure the endpoint and key in Settings, no model weights bundled.
- Model endpoints use HTTPS by default. Plaintext HTTP is allowed only after you explicitly approve a local or LAN service, never for public endpoints or through a proxy; don't read the confirmation gate as a blanket privacy guarantee.

## Built-in knowledge and model providers

<details>
<summary>Built-in app and website knowledge (unlisted apps still work)</summary>

<!-- generated:knowledge:start -->
<!-- Generated by ./gradlew updateReadme; do not edit by hand -->
| Category | Built in |
|---|---|
| **Apps** | Amoo 成长计划, Android 设置, 百家号, 哔哩哔哩, BOSS直聘, Chrome, 得到, 抖音, 飞书, Gmail, 即刻, 快手, 领英, QQ邮箱, Telegram, 今日头条, Uber, Uber Eats, 微信, 微博, WhatsApp, 闲鱼, 小红书, 知乎 |
| **Websites** | Amazon, 哔哩哔哩, Google Calendar, 豆瓣, 抖音, Instagram, Gmail, Google Maps, Telegram, 百度贴吧, 微博, 搜狗微信, WhatsApp, 小红书, 小宇宙, YouTube, 知乎, BOSS直聘 |
| **How-to skills** | 改 Maeve 自己的设置, 把内容整理成文字, 处理 PDF 文档, 查清一件事, 消息与邮件, 写技能 |
<!-- generated:knowledge:end -->

</details>

<details>
<summary>Model provider presets</summary>

<!-- generated:providers:start -->
<!-- Generated by ./gradlew updateReadme; do not edit by hand -->
Presets for DeepSeek, 通义千问, Kimi, 智谱, 智谱 Coding Plan, 硅基流动, 火山方舟, MiniMax, 阶跃星辰, 腾讯混元, 百度千帆, OpenRouter, Requesty, OpenCode Go, Anthropic, Gemini, OpenAI, xAI, Mistral, Groq, Ollama, LM Studio and more; for any other provider pick Custom (自定义), which takes any OpenAI-compatible, Anthropic, Gemini or OpenAI Responses endpoint.
<!-- generated:providers:end -->

You can also sign in without a key: ChatGPT (Plus / Pro), xAI / Grok and OpenRouter accounts. Provider-specific notes are in [models and sign-in](docs/models.en.md).

</details>

## Status

The repository is at **M2 (interaction layer)**, and Flutter is now the only application UI: it starts directly, the chat bridge uses real Android conversation state, and the host registers MethodChannel/EventChannel handlers. The connection hub and M3 acceptance, and release hardening (M4) are not finished; **nothing here is a released binary**. See [ATTRIBUTION.md](ATTRIBUTION.md) and [SPEC.md](SPEC.md).

---

<sub>Apache-2.0 · Maeve is a derivative work of Wang Zan's **Mika** (Apache-2.0); upstream attribution is retained in [NOTICE](NOTICE) and [ATTRIBUTION.md](ATTRIBUTION.md). Third-party components are listed in [licenses/THIRD_PARTY_NOTICES.md](licenses/THIRD_PARTY_NOTICES.md).</sub>

<sub>Maeve is an independent project, not affiliated with or endorsed by HBO, Warner Bros., or Westworld. The name is used as a common given name, referring to the show's setting.</sub>
