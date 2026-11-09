# 衍生代码清单与 clean-room 重写计划

> 目标：把 Maeve 里仍然属于 Mika（Apache-2.0, Copyright 2026 wangzan101）表达的作者原创代码
> 全部换成自己的实现，然后删掉 `NOTICE` 里的 Mika 署名块与 `ATTRIBUTION.md` 的溯源章节。
>
> 本文只做判定与排序，不改任何代码。逐文件机器可读清单见 [`derived-files.tsv`](./derived-files.tsv)。

## 0. 结论先说

- 仓库受版本控制文件 **2,354 个**（其中 26 个二进制、2,285 个文本文件，约 **27.3 万行**）。
- 其中 **衍生行 259,154 行，占全部文本行的 95%**——也就是说改名 + 视觉系统重写之后，
  树里绝大部分文字仍然是上游的表达。
- 但按"还需投入"拆开看，真正必须重写的代码只占 **35%**：

| 衍生量归属 | 行数 | 占比 | 处置 |
|---|---:|---:|---|
| 核心 + app 源码（`:core` / `:app` 的 Kotlin、Java） | 90,858 | 35.1% | **必须重写** |
| 测试（单元 / androidTest / 快照） | 99,709 | 38.5% | 当行为规格用，随模块一起重写 |
| Compose UI（`app:ui` 全家） | 22,643 | 8.7% | **建议直接删**，Flutter 已接管 |
| 资源 / 提示词 / eval / 脚本 / 原生 | 41,096 | 15.9% | 大部分是文本与数据，批量重表达 |
| 文档 | 4,848 | 1.9% | 已基本干净，补差 |

- 单文件衍生度最高的是测试而不是产品代码：`ChatViewModelTest.kt`（2,198 行，99% 衍生）。
- **最大的省钱点**：`app:ui` 的 106 个 Compose 文件里 60 个 >=90% 衍生（15,584 行），
  而 `flutter/` 已经是 100% 自有代码。按 `FLUTTER_MIGRATION.md` 的路线，
  这批应当**删除而不是重写**，一次性省掉约 2.3 万行。

## 1. 判定口径

基线取 root commit `7cf7ff3`（"Baseline: Maeve-old snapshot"），它本身就是 Mika 改名后的快照，
所以"与基线相同"就等于"与上游相同"。

```
derived_ratio = (当前行数 - 相对基线新增行数) / 当前行数
derived_loc   = 当前行数 - 相对基线新增行数
```

档位：

| 档 | 含义 |
|---|---|
| A | 与基线逐字节相同（`git diff` 无该文件） |
| B | >=90% 的行沿自基线（通常只是包名 / 标识符机械替换） |
| C | 60%–90% |
| D | 30%–60% |
| E | 10%–30% |
| F | <10%，实质已是新代码 |

口径说明与已知偏差：

- **纯改名会被算成衍生。** `git diff` 把 `import com.mika.x.Y` 改成 `import com.daiyimo.maeve.x.Y`
  记为一增一删，所以 B 档里混着"只换了名字"的文件。它们要删署名同样必须重打，成本按全量计。
- **小文件会被高估。** 一个 20 行文件整体改名就是 20 增 20 删，ratio 归零；
  反过来一个 500 行文件只动 5 行 import 也会落在 B 档。看绝对行数比看百分比可靠。
- **`docs/media` 的图片 / `rootfs.tar.xz` / `libproot.so` 等二进制不参与行数统计**，
  在 `derived-files.tsv` 里 `binary=yes`、行数记 0。
- **TSV 的每组行数比本报告多几个**：TSV 按"该组全部受版本控制文件"计（含二进制），
  报告的"文本文件"列剔除了二进制。例：`tests` TSV 746 行 = 报告 745 文本 + 1 二进制。
- 第三方许可证文本（`licenses/*.txt`、`app/src/main/assets/licenses/*`）虽被算成衍生，
  但它们属于 Apache/FSF/PRoot，不属于 Mika，**必须保留**，不在重写范围。

## 2. 全仓衍生度总览

| 组 | 文本文件 | 文本 LOC | 衍生 LOC | 衍生% | 二进制 |
|---|---:|---:|---:|---:|---:|
| `src`（Kotlin / Java 产品码） | 758 | 120,154 | 113,501 | 94% | 0 |
| `tests`（单元 + androidTest + 快照） | 745 | 101,240 | 99,709 | 98% | 1 |
| `res`（10 语言字符串 / drawable / xml） | 330 | 22,469 | 22,095 | 98% | 0 |
| `tools`（relay / maeve-cli / sandbox / i18n） | 56 | 8,578 | 8,454 | 99% | 1 |
| `docs` | 64 | 7,709 | 4,848 | 63% | 20 |
| `assets`（web/page.js、selfcheck、licenses、rootfs） | 12 | 3,394 | 3,380 | 100% | 1 |
| `core-text`（prompts / knowledge / skills） | 175 | 3,295 | 3,236 | 98% | 0 |
| `eval`（任务 JSON） | 129 | 3,184 | 3,078 | 97% | 0 |
| `native`（`cpp/maeverun`、`aidl`） | 2 | 853 | 853 | 100% | 3 |
| `flutter` | 14 | 2,033 | 0 | 0% | 0 |
| **合计** | **2,285** | **272,909** | **259,154** | **95%** | **26** |

结论很直白：**`flutter/` 是唯一干净的新代码；`docs` 干净了六成；其余九成未动。**

## 3. 源码分包清单（重写主表）

按衍生 LOC 降序。`fan-in` = 被同模块其它包 import 的次数，`fan-out` = 它 import 别人的次数。
测试列是对应的单元 / androidTest 文件数（按同名包路径匹配，是"重写安全度"的代理指标）。

| 包 | 文件 | 总 LOC | 衍生 LOC | 衍生% | 测试 | fan-in | fan-out |
|---|---:|---:|---:|---:|---:|---:|---:|
| `app:ui` | 77 | 22,824 | 20,209 | 89% | 60 | 42 | 936 |
| `app:cap/web` | 23 | 5,369 | 5,345 | 100% | 43 | 36 | 12 |
| `app:task` | 28 | 4,992 | 4,912 | 98% | 36 | 48 | 83 |
| `core:agent` | 37 | 4,788 | 4,756 | 99% | 49 | 0 | 242 |
| `core:cap/web` | 28 | 4,804 | 4,733 | 99% | 43 | 0 | 195 |
| `app:cap/phone/a11y` | 18 | 4,199 | 4,128 | 98% | 36 | 27 | 15 |
| `core:uistate` | 38 | 4,054 | 4,039 | 100% | 21 | 0 | 83 |
| `app:capsule` | 12 | 4,254 | 4,034 | 95% | 12 | 24 | 99 |
| `app:settings` | 24 | 3,642 | 3,619 | 99% | 27 | 129 | 15 |
| `app:cap/phone/shell` | 24 | 3,481 | 3,353 | 96% | 26 | 20 | 5 |
| `core:model` | 16 | 2,751 | 2,730 | 99% | 14 | 0 | 9 |
| `core:cap/phone` | 17 | 2,682 | 2,677 | 100% | 14 | 0 | 165 |
| `core:shell` | 14 | 2,222 | 2,150 | 97% | 16 | 0 | 4 |
| `core:cap/mcp` | 7 | 2,059 | 2,059 | 100% | 5 | 0 | 17 |
| `app:cap/sandbox` | 17 | 2,101 | 2,047 | 97% | 29 | 28 | 8 |
| `app:voice` | 15 | 1,947 | 1,946 | 100% | 14 | 27 | 12 |
| `core:adb` | 11 | 1,652 | 1,651 | 100% | 8 | 0 | 0 |
| `app:cap/phone/setup` | 13 | 1,566 | 1,538 | 98% | 2 | 15 | 18 |
| `core:tool` | 18 | 1,530 | 1,528 | 100% | 21 | 0 | 59 |
| `core:cap/web/cdp` | 8 | 1,516 | 1,516 | 100% | 10 | 0 | 10 |
| `app:cap/web/cdp` | 8 | 1,463 | 1,446 | 99% | 10 | 7 | 6 |
| `app:browser` | 11 | 1,432 | 1,400 | 98% | 10 | 20 | 14 |
| `core:general` | 15 | 1,262 | 1,255 | 99% | 1 | 0 | 237 |
| `app:about` | 10 | 1,231 | 1,210 | 98% | 7 | 22 | 18 |
| `core:cap/sandbox` | 10 | 1,212 | 1,202 | 99% | 29 | 0 | 54 |
| `core:remote` | 9 | 1,174 | 1,169 | 100% | 8 | 0 | 8 |
| `core:eval` | 8 | 1,166 | 1,164 | 100% | 16 | 0 | 21 |
| `core:knowledge` | 11 | 1,162 | 1,151 | 99% | 12 | 0 | 26 |
| `core:action` | 10 | 1,120 | 1,112 | 99% | 13 | 0 | 72 |
| `app:ui/components` | 12 | 2,002 | 1,084 | 54% | 0 | 459 | 54 |
| `core:verify` | 4 | 1,069 | 1,068 | 100% | 11 | 0 | 16 |
| `core:record` | 8 | 950 | 949 | 100% | 13 | 0 | 10 |
| `core:gate` | 2 | 871 | 871 | 100% | 4 | 0 | 18 |
| `core:trace` | 5 | 890 | 853 | 96% | 2 | 0 | 15 |
| `core:memory` | 9 | 819 | 817 | 100% | 11 | 0 | 10 |
| `app:selfcheck` | 4 | 834 | 813 | 97% | 5 | 1 | 23 |
| `core:oauth` | 6 | 814 | 810 | 100% | 2 | 0 | 9 |
| `app:intercept` | 4 | 834 | 806 | 97% | 3 | 2 | 4 |
| `app:chat` | 7 | 794 | 790 | 99% | 4 | 33 | 2 |
| `core:uistate/tools` | 8 | 750 | 750 | 100% | 0 | 0 | 155 |
| `core:mcp` | 5 | 683 | 654 | 96% | 8 | 0 | 32 |
| `core:workspace` | 6 | 637 | 631 | 99% | 3 | 0 | 0 |
| `core:replay` | 5 | 607 | 605 | 100% | 7 | 0 | 28 |
| `core:skills` | 5 | 599 | 593 | 99% | 8 | 0 | 23 |
| `core:run` | 11 | 551 | 550 | 100% | 6 | 0 | 10 |
| `core:vision` | 6 | 550 | 549 | 100% | 6 | 0 | 17 |
| `core:usage` | 5 | 544 | 544 | 100% | 4 | 0 | 10 |
| `app:ui/viewer` | 3 | 568 | 534 | 94% | 2 | 4 | 20 |
| `app:ui/theme` | 12 | 2,456 | 533 | 22% | 1 | 283 | 8 |
| `app:record` | 3 | 469 | 468 | 100% | 13 | 12 | 0 |
| `app:wechat` | 4 | 470 | 463 | 99% | 3 | 3 | 4 |
| `core:guard` | 4 | 454 | 454 | 100% | 2 | 0 | 3 |
| `core:chatgpt` | 2 | 451 | 451 | 100% | 1 | 0 | 6 |
| `app:takeover` | 3 | 416 | 416 | 100% | 6 | 16 | 1 |
| `app:mcp` | 5 | 420 | 411 | 98% | 8 | 3 | 10 |
| `app:MaeveApp.kt` | 1 | 392 | 392 | 100% | 0 | 0 | 48 |
| `core:selfcheck` | 3 | 396 | 391 | 99% | 5 | 0 | 0 |
| `core:cap/root` | 3 | 376 | 375 | 100% | 3 | 0 | 28 |
| `core:net` | 4 | 347 | 344 | 99% | 3 | 0 | 0 |
| `core:trigger` | 3 | 296 | 296 | 100% | 3 | 0 | 0 |
| `app:ui/haptic` | 2 | 289 | 283 | 98% | 1 | 5 | 3 |
| `app:cap/phone/display` | 5 | 283 | 281 | 99% | 7 | 9 | 1 |
| `app:memory` | 4 | 269 | 267 | 99% | 11 | 7 | 9 |
| `core:assembly` | 3 | 271 | 265 | 98% | 0 | 0 | 62 |
| `core:userscreen` | 4 | 247 | 247 | 100% | 1 | 0 | 9 |
| `core:wechat` | 1 | 196 | 196 | 100% | 3 | 0 | 0 |
| `app:skills` | 1 | 194 | 194 | 100% | 8 | 1 | 0 |
| `app:i18n` | 2 | 193 | 193 | 100% | 2 | 109 | 0 |
| `core:device` | 2 | 171 | 171 | 100% | 2 | 0 | 0 |
| `core:script` | 1 | 155 | 155 | 100% | 1 | 0 | 0 |
| `app:cap/phone/shell/adb` | 2 | 151 | 149 | 99% | 0 | 3 | 0 |
| `app:device` | 2 | 152 | 147 | 97% | 2 | 1 | 4 |
| `app:confirm` | 1 | 134 | 133 | 99% | 2 | 15 | 2 |
| `core:capability` | 1 | 129 | 129 | 100% | 1 | 0 | 9 |
| `core:observation` | 2 | 127 | 127 | 100% | 0 | 0 | 0 |
| `core:visual` | 3 | 127 | 127 | 100% | 1 | 0 | 4 |
| `app:knowledge` | 1 | 115 | 115 | 100% | 12 | 1 | 0 |
| `app:vision` | 1 | 114 | 114 | 100% | 6 | 2 | 0 |
| `app:replay` | 1 | 112 | 112 | 100% | 7 | 3 | 0 |
| `core:access` | 2 | 106 | 106 | 100% | 1 | 0 | 2 |
| `core:prompt` | 3 | 97 | 97 | 100% | 3 | 0 | 1 |
| `app:cap/root` | 1 | 96 | 94 | 98% | 3 | 1 | 0 |
| `core:settings` | 1 | 93 | 88 | 95% | 27 | 0 | 12 |
| `core:child` | 1 | 85 | 85 | 100% | 1 | 0 | 5 |
| `core:context` | 1 | 69 | 69 | 100% | 0 | 0 | 0 |
| `app:cap/phone` | 1 | 55 | 54 | 98% | 14 | 12 | 8 |
| `core:resource` | 1 | 50 | 50 | 100% | 1 | 0 | 0 |
| `app:web` | 2 | 29 | 29 | 100% | 0 | 1 | 1 |
| `app:setup` | 2 | 27 | 26 | 96% | 0 | 1 | 3 |
| `app:a11y` | 1 | 22 | 22 | 100% | 1 | 1 | 1 |
| `app:shell` | 2 | 15 | 15 | 100% | 16 | 12 | 2 |
| `core:risk` | 1 | 11 | 11 | 100% | 0 | 0 | 0 |
| `app:sandbox` | 1 | 6 | 6 | 100% | 0 | 1 | 1 |
| **合计** | **758** | **120,154** | **113,501** | **94%** | | | |

读表要点：

- **只有两块真正被重写过**：`app:ui/theme`（22% 衍生）、`app:ui/components`（54%）。
  这正是 `887ff89 M2 design system` 之后的结果。其余 74 个包全部 93%–100% 衍生。
- **`app:ui` 是最大单一衍生块**（含子包 22,643 行），且 `fan-out = 936`——
  它 import 别人的次数是全仓第一，说明它是消费者而不是地基。删它不会牵连上游。
- **`core:general`（1,255 行）和 `core:uistate/tools`（750 行）`fan-out` 高达 237 / 155**，
  是全员都在用的工具层；它们本身几乎不被 import，是真正的叶子，重写优先级应提前。
- **`app:settings`、`app:i18n`、`app:ui/components`、`app:ui/theme` 的 `fan-in` 最高**
  （129 / 109 / 459 / 283）——改它们的签名会波及全仓，但它们也是最容易重写的纯展示 / 常量层。
- **测试覆盖不均**：`core:agent` 49 个测试、`app:ui` 60 个、`app:cap/web` 43 个；
  而 `app:ui/components`、`core:uistate/tools`、`core:assembly`、`app:MaeveApp.kt`、`core:observation` 是 0。
  0 测试的包重写前必须先补规格，否则无法验证等价性。

## 4. 非源码衍生资产

这些不是代码，但同样是上游表达，删署名前必须处理。

| 资产 | 文件 | 文本 LOC | 衍生 LOC | 衍生% | 说明与处置 |
|---|---:|---:|---:|---:|---|
| `app/src/main/res` | 330 | 22,469 | 22,095 | 98% | 10 语言 × 约 31 个 `strings_*.xml`。**纯文本、单条独立、可批处理**；建议跟着每个界面一起逐文件重写，不要单独开一轮 |
| `tools/relay` | 27 | ~4,000 | ~3,900 | ~99% | Go 模块（`gateway.go`/`main.go`/`web.go` + 测试 + Dockerfile/Caddyfile）。NOTICE 里还留着 `Mika / Copyright 2026 wangzan101` 两行 |
| `tools/maeve-cli` | 13 | ~1,900 | ~1,800 | ~99% | Python CLI |
| `tools/sandbox` | 11 | ~1,700 | ~1,600 | ~99% | `build-native.sh` / `build-runtime.py` / `runtime-sources.py` |
| `tools/i18n` | 3 | ~1,000 | ~1,000 | 100% | `add_locale_strings.py` |
| `core/src/main/resources` | 175 | 3,295 | 3,236 | 98% | `prompts/`（120 个）、`knowledge/`（46 个）、`skills/`（9 个）。**这是 agent 的"大脑内容"，改它直接改行为**，必须与对应能力同批重写 |
| `eval/tasks` | 129 | 3,184 | 3,078 | 97% | 任务 JSON（`read` 43 / `web` 22 / `skills` 20 / `write` 11 / `memory` 10 / `irreversible` 9 / `impossible` 8 / `barrier` 5）。是行为规格，**建议保留并扩充**，不必重打 |
| `app/src/main/assets` | 12 | 3,394 | 3,380 | 100% | 见下方拆分 |
| `docs` | 64 | 7,709 | 4,848 | 63% | `docs/dev`（21）与 `docs/media`（20）是主要残留；`docs/spec`、`docs/adr`、`docs/readme` 已是自有内容 |
| `app/src/main/cpp/maeverun/maeverun.c` | 1 | 734 | 734 | 100% | C 沙箱启动器（seccomp / rlimit），`mikarun.c` 纯改名而来 |
| `app/src/main/aidl/.../IMaeveShell.aidl` | 1 | 23 | 23 | 100% | `IMaeveShell.aidl` 纯改名而来 |

`assets` 内部要分开看：

| 文件 | 处置 |
|---|---|
| `app/src/main/assets/licenses/Apache-2.0.txt`、`app/src/main/assets/licenses/GPL-*.txt`、`app/src/main/assets/licenses/LGPL-3.0.txt`、`app/src/main/assets/licenses/Protocol-Buffers.txt`、`app/src/main/assets/licenses/NOTICE.txt`、`app/src/main/assets/licenses/SOURCE_OFFER.md` | **第三方许可证，必须保留**，与 Mika 无关 |
| `runtime/rootfs.tar.xz` | PRoot 根文件系统二进制（GPL-2.0-or-later），**必须保留**，义务在 `licenses/` |
| `jniLibs/arm64-v8a/libproot.so`、`libproot_loader.so` | PRoot 预编译产物，**必须保留** |
| `jniLibs/arm64-v8a/libmaeverun.so` | `maeverun.c` 的编译产物，随 Wave 4 重编 |
| `assets/sandbox/maeve` | `mika` 二进制改名而来，需重编或重命名 |
| `assets/web/page.js` | **1,189 行、100% 衍生**，注入 WebView 的桥接脚本，需重写 |
| `assets/selfcheck/page.html`、`page-ar.html` | 自检页，约 100 行，需重写 |
| `assets/runtime/packages.json` | 包清单，需重写 |

## 5. 单文件大鱼 TOP 25

按衍生 LOC 降序（完整 2,311 行见 `derived-files.tsv`）。

| 衍生 LOC | 总 LOC | 衍生% | 文件 |
|---:|---:|---:|---|
| 2,183 | 2,198 | 99% | `app/src/test/java/com/daiyimo/maeve/ui/ChatViewModelTest.kt` |
| 1,926 | 1,974 | 98% | `app/src/main/java/com/daiyimo/maeve/cap/phone/a11y/AndroidScreen.kt` |
| 1,634 | 1,691 | 97% | `app/src/main/java/com/daiyimo/maeve/cap/phone/shell/ShellService.kt` |
| 1,514 | 1,515 | 100% | `app/src/main/java/com/daiyimo/maeve/ui/ChatViewModel.kt` |
| 1,483 | 1,486 | 100% | `app/src/main/java/com/daiyimo/maeve/cap/web/WebScreen.kt` |
| 1,336 | 1,372 | 97% | `app/src/main/java/com/daiyimo/maeve/capsule/CapsuleController.kt` |
| 1,291 | 1,312 | 98% | `app/src/main/java/com/daiyimo/maeve/task/TaskRun.kt` |
| 1,230 | 1,597 | 77% | `app/src/main/java/com/daiyimo/maeve/ui/ChatScreen.kt` |
| 1,189 | 1,189 | 100% | `app/src/main/assets/web/page.js` |
| 1,169 | 1,172 | 100% | `app/src/main/java/com/daiyimo/maeve/settings/ProviderStore.kt` |
| 1,126 | 1,130 | 100% | `app/src/main/java/com/daiyimo/maeve/ui/ModelSetupViewModel.kt` |
| 1,047 | 1,067 | 98% | `app/src/main/java/com/daiyimo/maeve/task/TaskService.kt` |
| 1,044 | 1,044 | 100% | `app/src/test/java/com/daiyimo/maeve/ui/ModelSetupViewModelTest.kt` |
| 1,027 | 1,039 | 99% | `core/src/main/kotlin/com/daiyimo/maeve/core/agent/AgentLoop.kt` |
| 888 | 898 | 99% | `app/src/test/java/com/daiyimo/maeve/settings/ProviderStoreTest.kt` |
| 826 | 890 | 93% | `app/src/main/java/com/daiyimo/maeve/capsule/CapsuleCardContent.kt` |
| 821 | 822 | 100% | `core/src/test/kotlin/com/daiyimo/maeve/core/agent/AgentLoopTest.kt` |
| 819 | 907 | 90% | `app/src/main/java/com/daiyimo/maeve/ui/ModelsScreen.kt` |
| 765 | 801 | 96% | `core/src/test/resources/prompt-surface/layers/all.txt` |
| 760 | 760 | 100% | `core/src/test/kotlin/com/daiyimo/maeve/core/tool/ToolDispatcherWebTest.kt` |
| 751 | 753 | 100% | `core/src/test/kotlin/com/daiyimo/maeve/core/shell/BuiltinShellEngineTest.kt` |
| 734 | 734 | 100% | `app/src/main/cpp/maeverun/maeverun.c` |
| 676 | 722 | 94% | `app/src/main/java/com/daiyimo/maeve/ui/ModelSetupScreen.kt` |
| 672 | 710 | 95% | `app/src/main/java/com/daiyimo/maeve/ui/UsageScreen.kt` |
| 649 | 719 | 90% | `app/src/main/java/com/daiyimo/maeve/ui/BrowserPageAsk.kt` |

规律：**TOP 25 里 9 个是测试、7 个是 `app:ui`**。产品代码里最难啃的是
`AndroidScreen.kt`（无障碍取树，1,926 行）、`ShellService.kt`（shell 服务，1,634 行）、
`AgentLoop.kt`（agent 主循环，1,027 行）这三个。

## 6. 重写成本模型

口径：只统计 `derived_ratio >= 60%` 的文件（低于此值的视为已完成）。

| 归属 | 文件 | 需重表达 LOC |
|---|---:|---:|
| 核心 + app 源码 | 741 | 113,021 |
| 测试 | 742 | 99,708 |
| `res` 字符串 | 321 | 22,082 |
| `tools` | 55 | 8,454 |
| `docs` | 51 | 4,551 |
| `assets` | 12 | 3,380 |
| `core-text` | 161 | 3,235 |
| `eval` | 129 | 3,078 |
| `native` / `aidl` | 2 | 853 |
| **合计** | **2,214** | **258,362** |

按重写速率折算（**假设值，可自行替换**）：

| 速率（LOC/人日） | 人日 | 1 人 | 3 人 | 6 人 |
|---:|---:|---:|---:|---:|
| 150（保守，无 AI 辅助） | 1,722 | 7.8 年 | 31 个月 | 16 个月 |
| 300（中性，有测试做规格 + AI 辅助） | 861 | 3.9 年 | 16 个月 | 8 个月 |
| 600（激进，模板化批处理） | 431 | 2.0 年 | 8 个月 | 4 个月 |

按第 0 节的结构拆，真正"必须手写"的只有 90,858 行（35%）；其余 65% 是测试、可删的 Compose、
以及可批处理的文本资源。**所以有效工作量接近上表的一半。**

成本分档（按包）：

| 难度 | 包 | 理由 |
|---|---|---|
| 低（纯数据 / 常量 / 展示） | `core:observation` `core:risk` `core:context` `core:resource` `core:prompt` `core:access` `core:visual` `core:device` `core:script` `core:child` `core:capability` `core:wechat` `core:net` `core:trigger` `core:usage` `core:userscreen` `app:i18n` `app:ui/theme` `app:ui/components` `app:about` `app:setup` `app:a11y` `app:web` `app:shell` `app:sandbox` | 无状态或纯函数，测试少但正确性容易自证 |
| 中（有明确接口边界） | `core:model` `core:uistate` `core:tool` `core:record` `core:trace` `core:gate` `core:memory` `core:replay` `core:skills` `core:knowledge` `core:eval` `core:verify` `core:action` `core:remote` `core:oauth` `core:vision` `core:run` `core:mcp` `core:guard` `core:assembly` `core:chatgpt` `core:selfcheck` `core:settings` `app:settings` `app:task` `app:capsule` `app:voice` `app:chat` `app:record` `app:replay` `app:memory` `app:takeover` `app:mcp` | 有测试兜底，按测试反推实现 |
| 高（系统 / 特权 / 协议） | `core:cap/*`（CDP、无障碍、rootfs、MCP）、`app:cap/*`、`core:agent`、`core:shell`、`core:adb`、`core:general`、`app:intercept`、`app:selfcheck`、`maeverun.c`、`assets/web/page.js` | 依赖 Android 私有 API、CDP 协议、seccomp；等价性只能靠设备验证 |

## 7. 重写顺序

排序原则：**先地基后消费者**（`fan-out` 大的叶子先做，`fan-in` 大的后做），
**先有测试兜底的后做**，**每波只动一个包族并把它的测试一起换掉**。

| 波次 | 范围 | 衍生 LOC（源码） | 同步测试 | 为什么在这个位置 |
|---|---|---:|---:|---|
| **0. 脚手架** | `git worktree` 固定 `7cf7ff3` 作为只读规格源；把 `docs/derived-files.tsv` 的生成脚本固化成 `tooling/derived-audit.py` 并接进 CI | 0 | 0 | 不碰产品码，但让"还剩多少衍生"变成每次提交可度量的数 |
| **1. 词汇层与叶子** | `core:observation` `core:risk` `core:context` `core:resource` `core:prompt` `core:access` `core:visual` `core:device` `core:script` `core:child` `core:capability` `core:wechat` `core:net` `core:trigger` `core:workspace` `core:userscreen` `core:usage` `core:record` `core:trace` `core:gate` `core:model` `core:uistate` `core:uistate/tools` `core:adb` `core:shell` `core:general` | ~18,600 | ~16,400 | 全部是数据类 / 枚举 / 无状态工具，`fan-out` 高而 `fan-in` 低。**先把词汇换成自己的，后面每一层才是写在自有类型上**，否则要改两遍 |
| **2. 核心能力层** | `core:cap/root` → `core:cap/mcp` → `core:cap/web/cdp` → `core:cap/sandbox` → `core:cap/web` → `core:cap/phone` | ~12,600 | ~11,100 | agent 的"手"。包内按"文件数少 + 测试多"排序（root 3 文件/3 测试 → phone 17 文件/14 测试） |
| **3. 智能体核心** | `core:agent` `core:tool` `core:action` `core:knowledge` `core:skills` `core:memory` `core:verify` `core:replay` `core:eval` `core:guard` `core:remote` `core:mcp` `core:run` `core:vision` `core:selfcheck` `core:settings` `core:assembly` `core:chatgpt` `core:oauth` | ~18,200 | ~16,000 | 行为风险最高的一层（`AgentLoop.kt` 1,039 行、49 个测试）。放在能力层之后，工具面已经稳定 |
| **4. app 能力绑定** | `app:cap/root` → `app:cap/web/cdp` → `app:cap/phone/shell/adb` → `app:cap/sandbox` → `app:cap/phone/setup` → `app:cap/phone/display` → `app:cap/phone/a11y` → `app:cap/phone/shell` → `app:cap/web` | ~18,400 | ~16,200 | **全仓最大的单一衍生块**（`app:cap` 112 文件 / 18,435 行 / 99%）。无障碍服务、悬浮窗、WebView 池、shell/proot 都在这 |
| **5. app 状态 / 设置 / 任务 / 语音** | `app:settings` → `app:task` → `app:capsule` → `app:voice` → `app:chat` `app:record` `app:replay` `app:memory` `app:takeover` `app:mcp` `app:selfcheck` `app:intercept` `app:wechat` `app:browser` `app:about` `app:knowledge` `app:skills` `app:vision` `app:device` `app:confirm` `app:i18n` `MaeveApp.kt` 及零碎包 | ~23,100 | ~20,300 | 依赖 1–4 层的产物；`app:settings`（fan-in 129）和 `app:i18n`（fan-in 109）先做，因为改它们签名波及面最大 |
| **6. Compose UI：删，不重写** | `app:ui` 全家 106 文件（`ui` 77 + `components` 12 + `theme` 12 + `viewer` 3 + `haptic` 2） | ~22,600 | ~6,000 | `flutter/` 已 100% 自有且接管了壳层。按 `FLUTTER_MIGRATION.md` 第 5 步"instrumentation coverage + feature parity 通过后逐屏退役"**直接删除**，不要重写 |
| **7. 非代码资产** | `res`（按界面分批）→ `core-text`（随对应能力）→ `eval`（保留扩充）→ `tools` → `assets/web/page.js` `selfcheck` `maeverun.c` `aidl` | ~41,100 | ~5,000（快照） | 文本可批处理；`prompts/` 与 `knowledge/` 改的是行为，必须与 Wave 2/3 同批做，不能拖到后面 |
| **8. 收尾** | 删 Mika 署名点；`ATTRIBUTION.md` / `NOTICE` 改写；CI 门禁改成"衍生行必须为 0" | 0 | 0 | 见下节 |

顺序上的两个硬约束：

1. **`core-text`（prompts / knowledge / skills）不能单独排到最后。** 它是 agent 的行为定义，
   跟 `core:agent` / `core:cap/*` 强耦合。正确做法是 Wave 2/3 里每重写一个能力，
   同时重写它对应的提示词与知识条目。
2. **`app:ui` 必须在 Wave 5 之后才能删。** `flutter/` 目前还是演示态
   （`FLUTTER_MIGRATION.md` 明说 chat 是本地延迟回复、无模型调用、无持久化、无 agent 执行）。
   在 Flutter 接通真实数据之前删掉 Compose，会同时失去功能和回归网。

## 8. 能"删"就不"重写"的省钱点

按省钱量排序：

1. **`app:ui` Compose 全家：−22,643 行。** 60 个文件 >=90% 衍生。Flutter 已存在，
   这是唯一一块"上游写了、你已经用别的方式实现了"的代码。删它不损失功能，只损失回归网，
   所以要先补 Flutter 侧的 instrumentation。
2. **`eval/tasks` 129 个 JSON：−3,078 行。** 这是任务定义（"给 Instagram 账号加关注"之类），
   属于事实性描述而非创作性表达，且正是重写时需要的等价性规格。**保留并扩充**，
   不要重打。
3. **`licenses/` 与 `assets/licenses/`：不算工作量。** GPL/LGPL/Apache 文本属于第三方，
   与 Mika 无关，义务独立存在。
4. **`docs/media` 的 20 个截图 / GIF：不算工作量。** 二进制，且是 Maeve 自己设备的截图。
5. **`app:ui/theme`（533 行衍生）与 `app:ui/components`（1,084 行衍生）已经干净了 78% / 46%。**
   如果 Wave 6 选择删除，这部分直接归零；如果选择保留，只需补 1,600 行。

## 9. 收尾：什么时候能删 Mika 署名

需要清零的署名点：

| 位置 | 现状 | 动作 |
|---|---|---|
| `NOTICE` 第 1–2 行 | `Mika` / `Copyright 2026 wangzan101` | 删除该块 |
| `NOTICE` 第 9–14 行 | "Maeve contains code derived from Mika..." | 删除该说明段 |
| `ATTRIBUTION.md` | Provenance / §4 表 / "What changed since the baseline" / Naming boundary | 整章删除或改为"本项目无上游派生代码" |
| `tools/relay/NOTICE` | 全文只有 Mika 两行 | 删除文件 |
| `app/src/main/assets/licenses/NOTICE.txt` | 应用内许可证屏数据源，含 Mika 块 | 同步删除 |
| `licenses/THIRD_PARTY_NOTICES.md` | scrcpy 段指向 `ShellContext.kt` | scrcpy 是独立第三方，**保留**；仅当 `ShellContext.kt` 被重写时才更新路径 |

放行条件（建议写成 CI 断言）：

```
derived-files.tsv 中 derived_ratio >= 0.60 的文件数 == 0
且 上述 6 个 Mika 署名点全部清零
```

法律上的判定逻辑：Apache-2.0 §4 的义务只在分发 **Derivative Work** 时触发。
当树里不再有任何上游表达残留时，就不再是 Derivative Work，§4(b)/(c)/(d) 的署名与变更声明义务
随之消失。**但 `licenses/` 下的 GPL-2.0 / GPL-3.0 / LGPL-3.0 来自 PRoot，与 Mika 无关，
必须继续保留**——把 Mika 署名和 PRoot 的 GPL 义务一起删是错误的。

## 10. 顺带发现的三个问题

1. **`maeverun.c` 的版权行被改写了。** 上游 `mikarun.c` 头部是
   `Copyright (c) 2026 Mika authors`，现在被改成 `Copyright (c) 2026 Maeve authors`，
   而 `ATTRIBUTION.md` 声称 "All upstream notices are retained"。
   这与 Apache-2.0 §4(c)（保留版权声明）不符。重写该文件之前，建议先把上游版权行补回 NOTICE。
2. **`git` 历史洗不干净。** `ATTRIBUTION.md` 说明发布历史从单个基线提交 `7cf7ff3` 开始，
   而那一整棵树就是上游快照。clean-room 重写只能让 **工作树** 干净，`7cf7ff3` 及其后代
   永远包含上游表达。如果目标是彻底切断溯源，唯一办法是**新建空仓库、从零提交**
   （不继承现有历史）。这是需要你决策的点：继承历史保留 blame 与 review 记录，
   新开仓库换取干净的 provenance。
3. **`app:ui` 的 60 个测试是删除它时的最大阻力。** `ChatViewModelTest.kt` 2,198 行、
   `ModelSetupViewModelTest.kt` 1,044 行，是全仓衍生量最大的两个文件。
   删 Compose 前必须先把这些行为搬成 Flutter 侧的 instrumentation，否则会一次性失去
   对 chat / 模型设置两条主链的回归保护。

---

*本文档为工程台账，不构成法律意见。商用前请复核商标状态。*
