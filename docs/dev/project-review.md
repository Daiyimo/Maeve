# Maeve 工程 Review · 2026-11-09

> 基线：HEAD `71767f7`。本文记录两轮工作：第一轮 review 发现问题，第二轮按建议修复并验证。
> 规范结论在 [project-standards.md](project-standards.md)，工具链在
> [tooling/toolchain/README.md](../../tooling/toolchain/README.md)。

## 1. 结论

| 首轮问题 | 现在 |
| --- | --- |
| 干净 clone 无法汇编 `:app`：依赖未跟踪的 Flutter AAR | 已修：`flutterBuildAar` 任务在 AAR 缺失/源码变化时先构建；CI 分开构建 AAR 后 `-PskipFlutterAar` 汇编 |
| 文档门禁在 HEAD 上为红：生成段落、译文、计数 | 已修：文档子集 14/14 |
| 入口文档写「两个模块、只有 Compose」 | 已修：三块工程表与 Flutter 构建顺序 |
| 文档索引缺项、链接指向不存在的 `.github/` | 已修：索引补全，链接改到 `CONTRIBUTING.md`；issue 模板已补 |
| `flutter/` 的 README 与 pubspec 还是模板 | 已修：写清目录、命令、契约与限制 |
| `verify.sh` 与迁移文档写死机器路径 | 已修：环境变量驱动，`cygpath`、`NO_PROXY`、缺工具可读报错 |
| `FORK.md` / `SPEC.md` 引用不存在的历史提交 | 已修：改为当前根提交 `7cf7ff3` 与可复现命令 |
| 文档测试把上游存档也拿来对照 | 已修：存档已移除 |
| 当前文档仍有旧类名、旧路径 | 已修：architecture、install、amoo 等改到现存符号 |
| `.github` 缺 CI | 已修：core / flutter / cli / android 四个 job |
| `docs/dev/` 堆积调试产物 | 已修：删除 uiautomator dump，截图移到 `docs/media/` 并在文档引用 |
| `main.dart` 单文件过大 | 已修：拆出 models、bridge、mark widget；1014 → 915 行，页面仍在主文件 |
| 品牌与上游 URL 未全量替换 | 已修：文档、prompt、eval、relay UI、自助页面、CLI/沙箱/MCP 命名；NOTICE/LICENSE 中的上游署名已按所有者决定移除 |

## 2. 工具链已部署并验证

位置：`E:\project\daiyimo\_toolchains`，缓存与 env 文件在 `E:\project\daiyimo\_env`。

| 组件 | 实际值 |
| --- | --- |
| JDK | Temurin 17.0.20.1 |
| Gradle | 8.11.1 |
| AGP | 8.7.3 |
| Kotlin | 2.1.20 |
| Flutter | 3.47.7 stable |
| Dart | 3.13.5 |
| Android SDK | platform 36 / build-tools 36.0.0 / NDK 25.1.8937393 |

`tooling/toolchain/verify.ps1` 全项通过。安装、自检、环境变量和跨项目复用写在
[tooling/toolchain/README.md](../../tooling/toolchain/README.md)；版本清单是
[tooling/toolchain/versions.json](../../tooling/toolchain/versions.json)。

工具链固定的是宿主 `:app` 的一套。`flutter build aar` 生成的 `.android` 工程用 Flutter
模板自带的 Gradle 9.3.1 / AGP 9.1.0，两者以 AAR 为边界；这是两套构建，不要强行统一。

## 3. 构建链

`app/build.gradle.kts` 新增 `flutterBuildAar`：从 `FLUTTER_ROOT` 或 PATH 找 Flutter，输入是
`flutter/lib`、`pubspec.yaml`、`pubspec.lock`，输出是 debug AAR；`preBuild` 依赖它。
`-PskipFlutterAar` 用于 CI 或已预构建 AAR 的场合。

实测：

```text
flutter build aar --debug                     -> flutter-debug.aar, 17,676,886 bytes
./gradlew :app:assembleDebug -PskipFlutterAar -> app-debug.apk, 584,036,227 bytes, 3m15s
删除 AAR 后 ./gradlew :app:preBuild            -> 自动重新构建 AAR, BUILD SUCCESSFUL, 1m11s
```

`flutter/tool/verify.sh` 在 Git Bash 下完整通过：format、analyze、14 个 widget test 全过。

## 4. 文档门禁与漂移

- README 的两段生成内容由 `./gradlew :core:updateReadme` 写入；5 份译文按新 README 重写，
  `##` 数量、本地图片集合和 `translated-from` 指纹都由测试守住。
- `DocsConsistencyTest` 现在认 `fun` 声明、用正确包名
  `com.daiyimo.maeve` 判断外部导入。
- 历史与外部文档可以用 `<!-- docs-check: spans-off — 原因 -->` 整篇退出符号检查。当前用于
  ADR、迁移日志、haptic 契约、UI/UX 重构记录和 iOS 标准；当前状态文档不用这个标记。
- 当前文档里的旧符号已改：architecture 的旧 Maeve* 类名与主题文件、install 的服务名、
  amoo 的 `MaeveApp.kt`、visual-tokens 的 token 类。
- 文档子集 14/14 通过；上游存档按 FORK.md 原样保留。

品牌清扫覆盖：docs 的产品文案与仓库链接、README 生成段落、10 语言资源（`brand_strings.py`
已 0 残留）、prompt 43 行、eval 任务、relay Web UI、自检页面，以及 `Download/Maeve/`
导出路径。命名统一为：`maeve_*` MCP 工具名、CLI `maeve`、`Author.Maeve` 序列化名、`MAEVE_DISPLAY_ID`、
`/opt/maeve/skills`、上游 attribution、`NOTICE` 与历史 ADR/迁移记录。

## 5. 事实修正

`FORK.md` 与 `SPEC.md` 不再引用不存在的 `bccdcda6` / `0c92fc39`。当前根提交是 `7cf7ff3`，
分支 `master`，公开历史是从该快照开始的 Maeve 提交。复现方式改成：

```bash
BASE="$(git rev-list --max-parents=0 HEAD)"
git diff "$BASE"..HEAD
```

`docs/spec/migration.md` 保留 2026-11-06 当时的重写记录，并注明后来根提交已移动。
一次性迁移脚本已从仓库移除，历史记录保留在 `docs/spec/migration.md`。

## 6. 清理与拆分

- 删除 `docs/dev/ui_*.xml` 的 uiautomator dump（25 个）。
- Compose 验收截图移到 `docs/media/ui/`，在 UI/UX 记录里引用；Flutter 设备截图移到
  `docs/media/flutter/`，在迁移文档里引用。
- `flutter/lib/main.dart` 拆出：
  - `lib/models/conversation.dart`：`Conversation`、`Message`
  - `lib/bridge/maeve_bridge.dart`：`MaeveBridge`
  - `lib/widgets/maeve_mark.dart`：`MaeveMark`、`MarkState`、`MaeveMarkPainter`
- `flutter/test/widget_test.dart` 改为直接 import mark 文件。

## 7. 验证记录

| 命令 | 结果 |
| --- | --- |
| `tooling/toolchain/verify.ps1` | 全项 OK |
| `bash flutter/tool/verify.sh` | format clean、analyze clean、15/15 |
| `flutter build aar --debug` | debug AAR 生成 |
| `./gradlew :app:assembleDebug -PskipFlutterAar` | BUILD SUCCESSFUL，3m15s |
| 删除 AAR 后 `./gradlew :app:preBuild` | 自动重建 AAR，BUILD SUCCESSFUL，1m11s |
| `./gradlew :core:test`（全量） | 2954 tests，45 failed，3 skipped |
| 文档子集 | 14/14 |
| `python tools/i18n/translations.py check` | 2005 条 / 10 种语言通过 |
| `python -m unittest discover -s tools/i18n` | 10 tests OK |

全量 `:core:test` 的 45 个失败集中在 `RootfsExtractorTest`、`ReadScriptEnforcementTest`、
`WorkspaceFileGateTest`、`ToolDispatcherSandboxTest`、`RunMetricsStoreTest`、`RunTraceTest`、
`FakeSandboxContractTest`、`InferenceTraceTest` 和 `SandboxPathsTest`，属于
`docs/spec/migration.md` 记录的 Windows 平台基线（POSIX 属性、符号链接、路径分隔符和计时）。
本轮改了 prompt 文案、CLI/沙箱路径、MCP/桥接命名与 Flutter 文案；失败类仍是同一批平台类，没有新增。Linux CI 是最终判据。
本轮没有在 Linux 上跑。

没有验证的：真机安装与 RenderThread 日志。`flutter doctor` 显示设备未授权，未做设备验收。

## 8. 仍需注意

- 品牌清扫已完成；NOTICE/LICENSE 中的上游署名是法律声明，不是命名残留。
- 宿主 AGP 8.7.3 官方只测到 compileSdk 35；项目依赖需要 36，已在 `gradle.properties` 用
  `android.suppressUnsupportedCompileSdk=36` 显式接受，升级 AGP 是单独的版本决策。
- Flutter 侧仍是工作台：本地演示回复、内存会话；`maeve/ui` 只注册了 `open_settings` / `status`，聊天与模型仍未接通。
- Windows 上的核心测试基线要随测试数量更新；CI 必须跑 Linux。
