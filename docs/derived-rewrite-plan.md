# 衍生代码清单与 clean-room 重写计划

> 目标：把 Maeve 里仍然属于 Mika（Apache-2.0, Copyright 2026 wangzan101）表达的作者原创代码
> 全部换成自己的实现，然后删掉 `NOTICE` 里的 Mika 署名块与 `ATTRIBUTION.md` 的溯源章节。
>
> 本文只做判定与排序，不改任何代码。逐文件机器可读清单见 [`derived-files.tsv`](./derived-files.tsv)，
> 执行进度见 [`derived-rewrite-status.md`](./derived-rewrite-status.md)。
>
> 构建框架固定为 **JDK 17 / Gradle 8.11.1 / AGP 8.7.3**（wrapper 与 `gradle.properties` 锁定），
> 重写不换工具链；`android.suppressUnsupportedCompileSdk=36` 是 AGP 8.7.3 配 compileSdk 36 的
> 临时开口，升级 AGP 后删除该行。

## 0. 结论先说

当前状态（2026-11-09 全量重算，基线仍是 root commit `7cf7ff3`）：

- 仓库受版本控制文件 **2,358 个**（其中 27 个二进制、2,331 个文本文件，约 **28.6 万行**）。
- 其中 **衍生行 256,693 行，占全部文本行的 89.7%**。
- 按"还需投入"拆开，真正必须重写的只剩 **src 里 595 个文件 / 96,501 行**：

| 衍生量归属 | 文件 | 衍生 LOC | 占比 | 处置 |
|---|---:|---:|---:|---|
| 核心 + app 源码（`:core` / `:app` 的 Kotlin、Java，`ratio>=60%`） | 595 | 96,501 | 39.8% | **继续重写** |
| 测试（单元 / androidTest / 快照，`ratio>=60%`） | 745 | 100,020 | 41.2% | 当行为规格用，随模块一起重写 |
| `res` 字符串 / drawable / xml | 321 | 22,082 | 9.1% | 跟着每个界面逐文件重写 |
| `tools`（relay / maeve-cli / sandbox / i18n） | 54 | 8,343 | 3.4% | 独立小模块，最后处理 |
| `docs` + 根目录散落文件 | 67 | 6,750 | 2.8% | 大部分是文本，批量重表达 |
| `core-text`（prompts / knowledge / skills） | 161 | 3,232 | 1.3% | **改它直接改 agent 行为**，与对应能力同批重写 |
| `eval` 任务 JSON | 129 | 3,078 | 1.3% | 行为规格，**保留并扩充**，不重打 |
| `assets` / `native`（page.js、selfcheck、maeverun.c、aidl） | 7 | 2,704 | 1.1% | 沙箱与桥接层，随能力层走 |

- 已经重写完（全部文件 `ratio<60%`）的 **26 个 `core` 包**：`record` `trace` `gate` `mcp`
  `workspace` `usage` `net` `trigger` `wechat` `userscreen` `observation` `capability`
  `visual` `prompt` `settings` `device` `child` `access` `context` `script` `resource` `risk`
  `shell` `adb` `general` `uistate`（含 `uistate/tools`）。**Wave 1 词汇层至此清零。**
- **最大的省钱点**仍是 `app:ui` 全家 106 个 Compose 文件（22,542 衍生行）：`flutter/` 已是
  100% 自有代码，按 `FLUTTER_MIGRATION.md` 的路线这批发**删除而不是重写**。但 Flutter 目前还是
  演示态（无模型调用、无持久化、无 agent 执行），删除的前置条件没变，见第 8 节。

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
- **`docs/media` 的图片 / GIF、`rootfs.tar.xz`、`jniLibs/*.so`、`gradle-wrapper.jar` 等
  27 个二进制不参与行数统计**，在 `derived-files.tsv` 里 `binary=yes`、行数记 0。
- **`jniLibs` 的三个 `.so` 被脚本归进 `src` 组**（路径在 `app/src/main/` 下），它们是 PRoot 与
  `maeverun.c` 的预编译产物，**永远不在重写范围**，读表时请把 `src` 组的 3 个二进制剔掉再看。
- 第三方许可证文本（`licenses/*.txt`、`app/src/main/assets/licenses/*`）虽被算成衍生，
  但它们属于 Apache/FSF/PRoot，不属于 Mika，**必须保留**，不在重写范围（`--check` 已排除该组）。

## 2. 全仓衍生度总览

| 组 | 文本文件 | 文本 LOC | 衍生 LOC | 衍生% | 二进制 |
|---|---:|---:|---:|---:|---:|
| `src`（Kotlin / Java 产品码，含 3 个 `.so` 二进制） | 763 | 124,761 | 107,043 | 86% | 3 |
| `tests`（单元 + androidTest + 快照） | 749 | 101,589 | 100,021 | 98% | 1 |
| `res`（10 语言字符串 / drawable / xml） | 330 | 22,469 | 22,095 | 98% | 0 |
| `tools`（relay / maeve-cli / sandbox / i18n） | 57 | 8,578 | 8,343 | 97% | 1 |
| `docs`（含 `docs/media` 20 个截图） | 87 | 10,663 | 4,848 | 45% | 20 |
| `core-text`（prompts / knowledge / skills） | 175 | 3,295 | 3,233 | 98% | 0 |
| `eval`（任务 JSON） | 129 | 3,184 | 3,078 | 97% | 0 |
| `licenses`（第三方许可证，**不动**） | 12 | 3,002 | 2,983 | 99% | 0 |
| `other`（根目录散落文件 + `gradle-wrapper.jar`） | 34 | 3,857 | 2,345 | 61% | 1 |
| `assets`（web/page.js、selfcheck、licenses、rootfs） | 6 | 1,912 | 1,892 | 99% | 1 |
| `native`（`cpp/maeverun`、`aidl`） | 2 | 853 | 812 | 95% | 0 |
| `flutter` | 14 | 2,033 | 0 | 0% | 0 |
| **合计** | **2,331** | **286,196** | **256,693** | **89.7%** | **27** |

结论：**`flutter/` 是唯一整体干净的新代码；`docs` 干净了 45%；`src` 从 94% 降到 86%——
降下来的 8 个百分点全部来自波次 1 已完成的 26 个 `core` 包。**

## 3. 源码分包清单（重写主表）

按衍生 LOC 降序。`测试` = 对应的单元 / androidTest 文件数（按同名包路径匹配）。
`状态` 列：`done` = 该包全部文件 `ratio<60%`；`N hot` = 还有 N 个文件 `>=60%`。

| 包 | 文件 | 总 LOC | 衍生 LOC | 衍生% | 测试 | 状态 |
|---|---:|---:|---:|---:|---:|---|
| `app:ui` | 106 | 28,139 | 22,542 | 80% | 64 | 89 hot |
| `app:cap/phone` | 63 | 9,735 | 9,503 | 98% | 71 | 63 hot |
| `app:cap/web` | 31 | 6,832 | 6,791 | 99% | 29 | 31 hot |
| `core:cap/web` | 36 | 6,320 | 6,249 | 99% | 24 | 36 hot |
| `app:task` | 28 | 4,992 | 4,912 | 98% | 36 | 28 hot |
| `core:agent` | 37 | 4,788 | 4,756 | 99% | 49 | 37 hot |
| `app:capsule` | 12 | 4,254 | 4,034 | 95% | 12 | 12 hot |
| `app:settings` | 24 | 3,642 | 3,619 | 99% | 26 | 24 hot |
| `core:uistate` | 46 | 5,489 | 2,983 | 54% | 21 | done |
| `core:model` | 16 | 2,751 | 2,730 | 99% | 14 | 16 hot |
| `core:cap/phone` | 17 | 2,682 | 2,677 | 100% | 14 | 17 hot |
| `core:cap/mcp` | 7 | 2,059 | 2,059 | 100% | 5 | 7 hot |
| `app:cap/sandbox` | 17 | 2,101 | 2,047 | 97% | 24 | 17 hot |
| `app:voice` | 15 | 1,947 | 1,946 | 100% | 14 | 15 hot |
| `core:tool` | 18 | 1,530 | 1,528 | 100% | 21 | 18 hot |
| `app:browser` | 11 | 1,432 | 1,400 | 98% | 10 | 11 hot |
| `core:shell` | 14 | 2,481 | 1,383 | 56% | 14 | done |
| `app:about` | 10 | 1,231 | 1,207 | 98% | 7 | 10 hot |
| `core:cap/sandbox` | 10 | 1,212 | 1,202 | 99% | 6 | 10 hot |
| `core:remote` | 9 | 1,174 | 1,169 | 100% | 8 | 9 hot |
| `core:eval` | 8 | 1,166 | 1,164 | 100% | 14 | 8 hot |
| `core:knowledge` | 11 | 1,162 | 1,151 | 99% | 11 | 11 hot |
| `core:action` | 10 | 1,120 | 1,112 | 99% | 13 | 10 hot |
| `core:verify` | 4 | 1,069 | 1,068 | 100% | 11 | 4 hot |
| `core:adb` | 11 | 1,895 | 1,026 | 54% | 8 | done |
| `core:general` | 15 | 1,533 | 857 | 56% | 1 | done |
| `core:memory` | 9 | 819 | 817 | 100% | 9 | 9 hot |
| `app:selfcheck` | 4 | 834 | 813 | 97% | 3 | 4 hot |
| `core:oauth` | 6 | 814 | 810 | 100% | 2 | 6 hot |
| `app:intercept` | 4 | 834 | 806 | 97% | 3 | 4 hot |
| `app:chat` | 7 | 794 | 790 | 99% | 4 | 7 hot |
| `core:replay` | 5 | 607 | 605 | 100% | 6 | 5 hot |
| `core:skills` | 5 | 599 | 593 | 99% | 7 | 5 hot |
| `core:run` | 11 | 551 | 550 | 100% | 6 | 11 hot |
| `core:vision` | 6 | 550 | 549 | 100% | 4 | 6 hot |
| `core:record` | 8 | 1,334 | 501 | 38% | 11 | done |
| `app:record` | 3 | 469 | 468 | 100% | 2 | 3 hot |
| `app:wechat` | 4 | 470 | 463 | 99% | 2 | 4 hot |
| `core:trace` | 5 | 1,198 | 463 | 39% | 2 | done |
| `core:guard` | 4 | 454 | 454 | 100% | 2 | 4 hot |
| `core:chatgpt` | 2 | 451 | 451 | 100% | 1 | 2 hot |
| `app:takeover` | 3 | 416 | 416 | 100% | 6 | 3 hot |
| `app:mcp` | 5 | 420 | 411 | 98% | 0 | 5 hot |
| `core:gate` | 2 | 1,084 | 402 | 37% | 4 | done |
| `core:selfcheck` | 3 | 396 | 391 | 99% | 2 | 3 hot |
| `core:cap/root` | 3 | 376 | 375 | 100% | 2 | 3 hot |
| `core:mcp` | 5 | 957 | 369 | 39% | 8 | done |
| `app:MaeveApp.kt` | 1 | 392 | 360 | 92% | 0 | 1 hot |
| `core:workspace` | 6 | 812 | 347 | 43% | 4 | done |
| `core:usage` | 5 | 817 | 288 | 35% | 4 | done |
| `app:memory` | 4 | 269 | 267 | 99% | 2 | 4 hot |
| `core:assembly` | 3 | 271 | 265 | 98% | 0 | 3 hot |
| `core:net` | 4 | 438 | 241 | 55% | 3 | done |
| `core:trigger` | 3 | 475 | 208 | 44% | 3 | done |
| `app:skills` | 1 | 194 | 194 | 100% | 1 | 1 hot |
| `app:i18n` | 2 | 193 | 193 | 100% | 2 | 2 hot |
| `core:wechat` | 1 | 265 | 152 | 57% | 1 | done |
| `app:device` | 2 | 152 | 147 | 97% | 1 | 2 hot |
| `core:userscreen` | 4 | 379 | 138 | 36% | 1 | done |
| `app:confirm` | 1 | 134 | 133 | 99% | 2 | 1 hot |
| `app:knowledge` | 1 | 115 | 115 | 100% | 1 | 1 hot |
| `app:vision` | 1 | 114 | 114 | 100% | 2 | 1 hot |
| `app:replay` | 1 | 112 | 112 | 100% | 1 | 1 hot |
| `app:cap/root` | 1 | 96 | 94 | 98% | 1 | 1 hot |
| `core:capability` | 1 | 156 | 87 | 56% | 1 | done |
| `core:observation` | 2 | 180 | 87 | 48% | 0 | done |
| `core:visual` | 3 | 185 | 76 | 41% | 1 | done |
| `core:prompt` | 3 | 150 | 69 | 46% | 3 | done |
| `core:settings` | 1 | 120 | 68 | 57% | 1 | done |
| `core:device` | 2 | 332 | 62 | 19% | 1 | done |
| `core:child` | 1 | 129 | 61 | 47% | 1 | done |
| `core:access` | 2 | 201 | 54 | 27% | 1 | done |
| `core:context` | 1 | 105 | 51 | 49% | 0 | done |
| `core:script` | 1 | 310 | 49 | 16% | 1 | done |
| `core:resource` | 1 | 57 | 32 | 56% | 1 | done |
| `app:web` | 2 | 29 | 29 | 100% | 0 | 2 hot |
| `app:setup` | 2 | 27 | 26 | 96% | 0 | 2 hot |
| `app:a11y` | 1 | 22 | 17 | 77% | 1 | 1 hot |
| `app:shell` | 2 | 15 | 15 | 100% | 2 | 2 hot |
| `core:risk` | 1 | 17 | 8 | 47% | 0 | done |
| `app:sandbox` | 1 | 6 | 6 | 100% | 0 | 1 hot |
| **合计** | **763** | **124,761** | **107,043** | **86%** | | |

读表要点：

- **波次 1 已推平 26 个 `core` 包**（22 个叶子 + `shell` / `adb` / `general` + `uistate`）。
  `core` 的 `hot` 文件从基线时的 332 降到 **240**，`app` 一个都还没动（353 hot）。
- **`app:ui` 是最大单一衍生块**（22,542 行、80%）：它是消费者不是地基，删它不牵连上游，
  但必须等 Flutter 接通真实数据（见第 8 节）。
- **接下来最大的两块是 `core:agent`（37 文件、49 个测试）和 `core:cap/web`（36 文件）**，
  `fan-out` 都很高——上层全都建在这两层的类型上。`core:model`（16 文件）是两者共同的地基，
  放在波次 2 开头做。
- **测试覆盖不均**：`core:agent` 49 个、`app:cap/phone` 71 个、`app:ui` 64 个；
  而 `core:general` 只有 1 个、`app:mcp` / `core:assembly` / `core:observation` / `core:context`
  / `app:MaeveApp.kt` 是 0。0 测试的包重写前必须先补规格，否则无法验证等价性。
- `core:general` 已全部重写完但只有 1 个测试兜底——它的正确性目前主要靠编译 +
  全量 `:core:test` 的集成面，后续每动一个文件都要手动过一遍相关集成测试。

## 4. 非源码衍生资产

这些不是代码，但同样是上游表达，删署名前必须处理。

| 资产 | 文件 | 文本 LOC | 衍生 LOC | 衍生% | 说明与处置 |
|---|---:|---:|---:|---:|---|
| `app/src/main/res` | 330 | 22,469 | 22,095 | 98% | 10 语言 × 约 31 个 `strings_*.xml`。**纯文本、单条独立、可批处理**；跟着每个界面一起逐文件重写，不单独开一轮 |
| `tools/relay` | 27 | ~4,000 | ~3,900 | ~99% | Go 模块（`gateway.go`/`main.go`/`web.go` + 测试 + Dockerfile/Caddyfile）。NOTICE 里还留着 `Mika / Copyright 2026 wangzan101` 两行 |
| `tools/maeve-cli` | 13 | ~1,900 | ~1,800 | ~99% | Python CLI；`scripts/maeve_desk/desk.js`（1,221 衍生行）是全仓第 9 大鱼 |
| `tools/sandbox` | 11 | ~1,700 | ~1,600 | ~99% | `build-native.sh` / `build-runtime.py` / `runtime-sources.py` |
| `tools/i18n` | 3 | ~1,000 | ~1,000 | 100% | `add_locale_strings.py` |
| `core/src/main/resources` | 175 | 3,295 | 3,233 | 98% | `prompts/`（120 个）、`knowledge/`（46 个）、`skills/`（9 个）。**这是 agent 的"大脑内容"，改它直接改行为**，必须与对应能力同批重写 |
| `eval/tasks` | 129 | 3,184 | 3,078 | 97% | 任务 JSON（`read` 43 / `web` 22 / `skills` 20 / `write` 11 / `memory` 10 / `irreversible` 9 / `impossible` 8 / `barrier` 5）。是行为规格，**保留并扩充**，不必重打 |
| `app/src/main/assets` | 5 | 1,912 | 1,892 | 99% | 见下方拆分 |
| `docs` | 67 | 10,647 | 4,848 | 46% | `docs/dev`（21）与 `docs/media`（20）是主要残留；`docs/spec`、`docs/adr`、`docs/readme` 已是自有内容 |
| `app/src/main/cpp/maeverun/maeverun.c` | 1 | 734 | 716 | 98% | C 沙箱启动器（seccomp / rlimit），`mikarun.c` 纯改名而来 |
| `app/src/main/aidl/.../IMaeveShell.aidl` | 1 | 23 | 23 | 100% | `IMaeveShell.aidl` 纯改名而来 |
| 根目录散落文件 | 33 | 3,857 | 2,345 | 61% | `SPEC.md`（867 衍生行）最大，其余是 `gradlew`、`LICENSE`、`SECURITY.md`、构建脚本等；`LICENSE` 是 Apache-2.0 全文，**必须保留** |

`assets` 内部要分开看：

| 文件 | 处置 |
|---|---|
| `app/src/main/assets/licenses/Apache-2.0.txt`、`GPL-*.txt`、`LGPL-3.0.txt`、`Protocol-Buffers.txt`、`NOTICE.txt`、`SOURCE_OFFER.md` | **第三方许可证，必须保留**，与 Mika 无关 |
| `runtime/rootfs.tar.xz` | PRoot 根文件系统二进制（GPL-2.0-or-later），**必须保留**，义务在 `licenses/` |
| `jniLibs/arm64-v8a/libproot.so`、`libproot_loader.so` | PRoot 预编译产物，**必须保留** |
| `jniLibs/arm64-v8a/libmaeverun.so` | `maeverun.c` 的编译产物，随 Wave 4 重编 |
| `assets/sandbox/maeve` | `mika` 二进制改名而来，需重编或重命名 |
| `assets/web/page.js` | **1,189 行、100% 衍生**，注入 WebView 的桥接脚本，需重写 |
| `assets/selfcheck/page.html`、`page-ar.html` | 自检页，约 100 行，需重写 |
| `assets/runtime/packages.json` | 包清单，需重写 |

## 5. 单文件大鱼 TOP 25

按衍生 LOC 降序（完整 2,331 行见 `derived-files.tsv`）。

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
| 1,221 | 1,237 | 99% | `tools/maeve-cli/scripts/maeve_desk/desk.js` |
| 1,189 | 1,189 | 100% | `app/src/main/assets/web/page.js` |
| 1,169 | 1,172 | 100% | `app/src/main/java/com/daiyimo/maeve/settings/ProviderStore.kt` |
| 1,126 | 1,130 | 100% | `app/src/main/java/com/daiyimo/maeve/ui/ModelSetupViewModel.kt` |
| 1,047 | 1,067 | 98% | `app/src/main/java/com/daiyimo/maeve/task/TaskService.kt` |
| 1,044 | 1,044 | 100% | `app/src/test/java/com/daiyimo/maeve/ui/ModelSetupViewModelTest.kt` |
| 1,027 | 1,039 | 99% | `core/src/main/kotlin/com/daiyimo/maeve/core/agent/AgentLoop.kt` |
| 888 | 898 | 99% | `app/src/test/java/com/daiyimo/maeve/settings/ProviderStoreTest.kt` |
| 867 | 936 | 93% | `SPEC.md` |
| 826 | 890 | 93% | `app/src/main/java/com/daiyimo/maeve/capsule/CapsuleCardContent.kt` |
| 821 | 822 | 100% | `core/src/test/kotlin/com/daiyimo/maeve/core/agent/AgentLoopTest.kt` |
| 819 | 907 | 90% | `app/src/main/java/com/daiyimo/maeve/ui/ModelsScreen.kt` |
| 765 | 801 | 96% | `core/src/test/resources/prompt-surface/layers/all.txt` |
| 760 | 760 | 100% | `core/src/test/kotlin/com/daiyimo/maeve/core/tool/ToolDispatcherWebTest.kt` |
| 751 | 753 | 100% | `core/src/test/kotlin/com/daiyimo/maeve/core/shell/BuiltinShellEngineTest.kt` |
| 716 | 734 | 98% | `app/src/main/cpp/maeverun/maeverun.c` |
| 676 | 722 | 94% | `app/src/main/java/com/daiyimo/maeve/ui/ModelSetupScreen.kt` |

规律：**TOP 25 里 9 个是测试、8 个是 `app:ui`、1 个是 `tools` 的 JS**。产品代码里最难啃的仍是
`AndroidScreen.kt`（无障碍取树，1,926 行）、`ShellService.kt`（shell 服务，1,634 行）、
`AgentLoop.kt`（agent 主循环，1,027 行）这三个，全部还没动。

## 6. 重写成本模型

口径：只统计 `derived_ratio >= 60%` 的文件（低于此值的视为已完成），`licenses` 组除外。

| 归属 | 文件 | 需重表达 LOC |
|---|---:|---:|
| 核心 + app 源码 | 595 | 96,501 |
| 测试 | 745 | 100,020 |
| `res` 字符串 | 321 | 22,082 |
| `tools` | 54 | 8,343 |
| `docs` + 根目录 | 67 | 6,750 |
| `core-text` | 161 | 3,232 |
| `eval` | 129 | 3,078 |
| `assets` / `native` | 7 | 2,704 |
| **合计** | **2,079** | **242,710** |

对比首版计划（741 个源码文件 / 113,021 行）：源码热文件已减少 **146 个 / 16,520 行**。
按重写速率折算（**假设值，可自行替换**）：

| 速率（LOC/人日） | 人日 | 1 人 | 3 人 | 6 人 |
|---:|---:|---:|---:|---:|
| 150（保守，无 AI 辅助） | 1,663 | 7.6 年 | 30 个月 | 15 个月 |
| 300（中性，有测试做规格 + AI 辅助） | 831 | 3.8 年 | 15 个月 | 8 个月 |
| 600（激进，模板化批处理） | 416 | 1.9 年 | 8 个月 | 4 个月 |

按第 0 节的结构拆，真正"必须手写"的只剩 96,501 行（39.8%）；其余 60% 是测试、可删的
Compose、以及可批处理的文本资源。**所以有效工作量接近上表的一半。**

难度分档（按包，随进度更新）：

| 难度 | 包 | 理由 |
|---|---|---|
| 低（纯数据 / 常量 / 展示） | `core:observation` `core:risk` `core:context` `core:resource` `core:prompt` `core:access` `core:visual` `core:device` `core:script` `core:child` `core:capability` `core:wechat` `core:net` `core:trigger` `core:usage` `core:userscreen` `core:record` `core:trace` `core:gate` `core:mcp` `core:workspace` `app:i18n` `app:ui/theme` `app:ui/components` `app:about` `app:setup` `app:a11y` `app:web` `app:shell` `app:sandbox` | 无状态或纯函数，测试少但正确性容易自证（`core` 部分已完成） |
| 中（有明确接口边界） | `core:model` `core:uistate` `core:tool` `core:memory` `core:replay` `core:skills` `core:knowledge` `core:eval` `core:verify` `core:action` `core:remote` `core:oauth` `core:vision` `core:run` `core:guard` `core:assembly` `core:chatgpt` `core:selfcheck` `core:settings` `app:settings` `app:task` `app:capsule` `app:voice` `app:chat` `app:record` `app:replay` `app:memory` `app:takeover` `app:mcp` | 有测试兜底，按测试反推实现 |
| 高（系统 / 特权 / 协议） | `core:cap/*`（CDP、无障碍、rootfs、MCP）、`app:cap/*`、`core:agent`、`app:intercept`、`app:selfcheck`、`maeverun.c`、`assets/web/page.js` | 依赖 Android 私有 API、CDP 协议、seccomp；等价性只能靠设备验证 |

进度标注：低档里的 `core` 部分（observation ～ workspace 共 21 包）与中档里的 `core:uistate`、
高档里的 `core:shell` / `core:adb` / `core:general` **已全部完成**；中档剩余的
`core:model` `core:tool` `core:memory` `core:replay` `core:skills` `core:knowledge` `core:eval`
`core:verify` `core:action` `core:remote` `core:oauth` `core:vision` `core:run` `core:guard`
`core:assembly` `core:chatgpt` `core:selfcheck` 和高档的 `core:cap/*` `core:agent` 是接下来的主战场。

## 7. 波次与进度

排序原则：**先地基后消费者**（`fan-out` 大的叶子先做，`fan-in` 大的后做），
**先有测试兜底的后做**，**每波只动一个包族并把它的测试一起换掉**。

| 波次 | 范围 | 衍生 LOC（源码） | 状态 | 说明 |
|---|---|---:|---|---|
| **0. 脚手架** | `git worktree` 固定 `7cf7ff3` 作为只读规格源；`docs/derived-files.tsv` 的生成脚本固化成 `tooling/derived-audit.py`，逐包统计固化成 `tooling/package-audit.py` | 0 | ✅ 完成 | `--summary` / `--write` / `--check` 可直接用；本表数字全部由它重算 |
| **1. 词汇层与叶子** | `core` 下 26 个无状态 / 窄接口包 | ~18,600 | ✅ 完成 | `record` `trace` `gate` `mcp` `workspace` `usage` `net` `trigger` `wechat` `userscreen` `observation` `capability` `visual` `prompt` `settings` `device` `child` `access` `context` `script` `resource` `risk` `shell` `adb` `general` `uistate` 已全部 `<60%`；`core` 的 `src` 热文件 332 → **240** |
| **2. 核心能力层** | `core:model` → `core:cap/root` → `core:cap/mcp` → `core:cap/web`（含 cdp）→ `core:cap/sandbox` → `core:cap/phone` | ~12,600 | 🚧 进行中 | 包内按"文件数少 + 测试多"排序；`core:model` 是词汇层最后一块，也是所有 cap 的地基 |
| **3. 智能体核心** | `core:agent` `core:tool` `core:action` `core:knowledge` `core:skills` `core:memory` `core:verify` `core:replay` `core:eval` `core:guard` `core:remote` `core:mcp` `core:run` `core:vision` `core:selfcheck` `core:settings` `core:assembly` `core:chatgpt` `core:oauth` | ~18,200 | ⬜ 未开始 | 行为风险最高（`AgentLoop.kt` 1,039 行、49 个测试）。放在能力层之后 |
| **4. app 能力绑定** | `app:cap/root` → `app:cap/web/cdp` → `app:cap/phone/shell/adb` → `app:cap/sandbox` → `app:cap/phone/setup` → `app:cap/phone/display` → `app:cap/phone/a11y` → `app:cap/phone/shell` → `app:cap/web` | ~18,400 | ⬜ 未开始 | 全仓最大的单一衍生块（`app:cap` 112 文件 / 18,435 行 / 99%）。无障碍服务、悬浮窗、WebView 池、shell/proot 都在这 |
| **5. app 状态 / 设置 / 任务 / 语音** | `app:settings` → `app:task` → `app:capsule` → `app:voice` → `app:chat` `app:record` `app:replay` `app:memory` `app:takeover` `app:mcp` `app:selfcheck` `app:intercept` `app:wechat` `app:browser` `app:about` `app:knowledge` `app:skills` `app:vision` `app:device` `app:confirm` `app:i18n` `MaeveApp.kt` 及零碎包 | ~23,100 | ⬜ 未开始 | `app:settings`（fan-in 129）和 `app:i18n`（fan-in 109）先做 |
| **6. Compose UI：删，不重写** | `app:ui` 全家 106 文件 | ~22,600 | ⬜ 未开始（阻塞） | 计划是删除，但必须先补 Flutter 真实数据与 instrumentation；当前 Flutter 仍是演示态，不能先删 |
| **7. 非代码资产** | `res`（按界面分批）→ `core-text`（随对应能力）→ `eval`（保留扩充）→ `tools` → `assets/web/page.js` `selfcheck` `maeverun.c` `aidl` | ~41,100 | ⬜ 未开始 | 文本可批处理；`prompts/` 与 `knowledge/` 改的是行为，必须与 Wave 2/3 同批做 |
| **8. 收尾** | 删 Mika 署名点；`ATTRIBUTION.md` / `NOTICE` 改写；CI 门禁改成"衍生行必须为 0" | 0 | ⬜ 未开始 | 见第 9 节 |

顺序上的两个硬约束没变：

1. **`core-text`（prompts / knowledge / skills）不能单独排到最后。** 它是 agent 的行为定义，
   跟 `core:agent` / `core:cap/*` 强耦合。正确做法是 Wave 2/3 里每重写一个能力，
   同时重写它对应的提示词与知识条目。
2. **`app:ui` 必须在 Wave 5 之后才能删。** `flutter/` 目前还是演示态
   （`FLUTTER_MIGRATION.md` 明说 chat 是本地延迟回复、无模型调用、无持久化、无 agent 执行）。
   在 Flutter 接通真实数据之前删掉 Compose，会同时失去功能和回归网。

## 8. 能"删"就不"重写"的省钱点

按省钱量排序：

1. **`app:ui` Compose 全家：−22,542 行。** 89 个文件还 `>=60%` 衍生。Flutter 已存在，
   这是唯一一块"上游写了、你已经用别的方式实现了"的代码。删它不损失功能，只损失回归网，
   所以要先补 Flutter 侧的 instrumentation（`FLUTTER_MIGRATION.md` 迁移顺序第 5 步）。
2. **`eval/tasks` 129 个 JSON：−3,078 行。** 这是任务定义（"给 Instagram 账号加关注"之类），
   属于事实性描述而非创作性表达，且正是重写时需要的等价性规格。**保留并扩充**，不要重打。
3. **`licenses/` 与 `assets/licenses/`：不算工作量。** GPL/LGPL/Apache 文本属于第三方，
   与 Mika 无关，义务独立存在。
4. **`docs/media` 的 20 个截图 / GIF：不算工作量。** 二进制，且是 Maeve 自己设备的截图。
5. **`app:ui/theme`（432 行衍生）与 `app:ui/components`（1,084 行衍生）已经干净了 82% / 46%。**
   如果 Wave 6 选择删除，这部分直接归零；如果选择保留，只需补 1,500 行。

## 9. 收尾：什么时候能删 Mika 署名

需要清零的署名点（现状未动，全部保留着）：

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
derived-files.tsv 中 derived_ratio >= 0.60 的文件数 == 0（当前 2,079）
且 上述 6 个 Mika 署名点全部清零
```

法律上的判定逻辑：Apache-2.0 §4 的义务只在分发 **Derivative Work** 时触发。
当树里不再有任何上游表达残留时，就不再是 Derivative Work，§4(b)/(c)/(d) 的署名与变更声明义务
随之消失。**但 `licenses/` 下的 GPL-2.0 / GPL-3.0 / LGPL-3.0 来自 PRoot，与 Mika 无关，
必须继续保留**——把 Mika 署名和 PRoot 的 GPL 义务一起删是错误的。

## 10. 已知问题与坑（执行中记录）

1. **`maeverun.c` 的版权行被改写过。** 上游 `mikarun.c` 头部是
   `Copyright (c) 2026 Mika authors`，现在被改成 `Copyright (c) 2026 Maeve authors`，
   而 `ATTRIBUTION.md` 声称 "All upstream notices are retained"。
   这与 Apache-2.0 §4(c)（保留版权声明）不符。重写该文件之前，建议先把上游版权行补回 NOTICE。
2. **`git` 历史洗不干净。** `ATTRIBUTION.md` 说明发布历史从单个基线提交 `7cf7ff3` 开始，
   而那一整棵树就是上游快照。clean-room 重写只能让 **工作树** 干净，`7cf7ff3` 及其后代
   永远包含上游表达。如果目标是彻底切断溯源，唯一办法是**新建空仓库、从零提交**
   （不继承现有历史）。这是需要决策的点：继承历史保留 blame 与 review 记录，
   新开仓库换取干净的 provenance。
3. **`app:ui` 的 64 个测试是删除它时的最大阻力。** `ChatViewModelTest.kt` 2,198 行、
   `ModelSetupViewModelTest.kt` 1,044 行，是全仓衍生量最大的两个文件。
   删 Compose 前必须先把这些行为搬成 Flutter 侧的 instrumentation，否则会一次性失去
   对 chat / 模型设置两条主链的回归保护。
4. **重写时踩过的编译/测试坑（都已修掉并回归，写下来避免再踩）**：
   - `X509ExtendedKeyManager` 的两个服务端别名方法收的是**单个** `String`（`keyType`），
     不是数组；按客户端那套签名写会"overrides nothing"。
   - `const val` 只能引用编译期常量：把说明文案抽成顶层 `private const val` 时不能再用
     对象成员（如 `SpawnAgent.MAX_STEPS`）做字符串模板，要么写字面量，要么把常量放进对象里。
   - `PromptLayoutTest.noOrphanFeedbackKeys` 要求 feedback 目录里的每个键都在代码里以
     **字面量**出现。`HandleObstacle` 曾把 `Feedback["barrier.$kind"]` 改成模板取值，
     键变成孤儿导致测试失败——这类"按前缀整段取"的提示必须保留显式分支。
   - `DocsConsistencyTest` 会扫 `docs/` 里提到的标识符是否存在于源码；文档写 Java 标准库
     类名会触发失败（栈溢出那个异常的类名就是教训），描述这类现象时用中文。
   - 私有函数别和接口成员同名：`SelfConfig` 的 `offered()` 遮蔽 `AgentTool` 成员后
     `available()` 无限递归成栈溢出。

---

*本文档为工程台账，不构成法律意见。商用前请复核商标状态。*
