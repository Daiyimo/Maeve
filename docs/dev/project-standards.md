# 工程规范

这份文档把仓库当前事实固定下来，作为改动前的检查表。事实来源是 HEAD `71767f7` 的目录、构建脚本和最近几条提交；与代码冲突时以代码为准，并回来改这份文档。

范围之外：产品定义在 [SPEC.md](../../SPEC.md)，Flutter 迁移在 [FLUTTER_MIGRATION.md](../../FLUTTER_MIGRATION.md)，当前问题清单在 [project-review.md](project-review.md)。

## 1. 仓库结构

| 路径 | 角色 | 边界 |
| --- | --- | --- |
| `:core` | 纯 JVM Kotlin | 不依赖 Android。Agent 循环、工具、上下文、守卫、知识与技能资源都在这里。 |
| `:app` | Android 宿主 | 源码根是 `app/src/main/java`。无障碍、虚拟屏、shell 助手、WebView、沙箱、权限，以及仍在使用的 Compose 界面。 |
| `flutter/` | Flutter 外壳 | 只做展示和桥接契约，不做平台能力。不是 Gradle 子项目，`settings.gradle.kts` 只 include `:core` 和 `:app`。 |
| `tools/` | 开发与运行工具 | relay 网关、sandbox 构建、i18n 校验、迁移脚本。不进 APK，除非明确作为资源打包。 |
| `eval/` | 真机评测任务集 | 作为测试 assets 打包，由真机评测跑器读取。 |
| `docs/` | 文档 | `spec/`、`adr/`、`dev/`、`reference/`。`reference/` 是上游分析存档，不改。 |

`:app` 通过本地 AAR 消费 Flutter 外壳。AAR 由 Flutter 模块构建写到 `flutter/` 下，生成目录不进仓库。**先构建 Flutter 模块，再汇编 `:app`**；干净 clone 直接跑 `:app:assembleDebug` 会失败。

## 2. 模块边界

- `:core` 不出现 Android import。平台差异通过接口和装配层注入，见 [architecture.md](architecture.md)。
- `:app` 不新增第二条任务执行链。所有会改变外部世界的动作都走既有的执行器与守卫链，见 [CONTRIBUTING.md](../../CONTRIBUTING.md) 的不变量。
- `flutter/` 只经由 `maeve/ui` 和 `maeve/ui/events` 与宿主通信。改通道名是桥接变更，不是界面改动。
- 新增用户可见界面优先放在 `flutter/`；只有确实需要平台 surface 时才留在 Compose，并在迁移文档里记录差异。

## 3. 构建与验证

| 改动 | 命令 |
| --- | --- |
| 工具链自检 | `pwsh tooling/toolchain/verify.ps1` 或 `bash tooling/toolchain/verify.sh` |
| `:core` | `./gradlew :core:test` |
| `:app`（需要 Flutter AAR） | `./gradlew :app:assembleDebug` |
| `flutter/` | `bash flutter/tool/verify.sh` |
| 文档与生成段落 | `./gradlew :core:updateReadme`，再跑 `:core:test` |

`verify.sh` 按 format、analyze、test 的顺序跑，遇到第一个失败就停。文档测试随 `:core:test` 一起跑，读根目录 README、`docs/`、`eval/README.md` 和 `tools/sandbox/README.md`。

```bash
# 只跑文档相关的测试
./gradlew :core:test --tests "com.daiyimo.maeve.core.DocsConsistencyTest" \
  --tests "com.daiyimo.maeve.core.docs.*"
```

## 4. 路径与工具链

- 固定版本、安装、自检和跨项目复用见 [tooling/toolchain/README.md](../../tooling/toolchain/README.md)。JDK、Gradle、AGP、Flutter 的版本以 `tooling/toolchain/versions.json` 为准，改版本要同步 `gradle/libs.versions.toml`、`flutter/pubspec.yaml` 和 CI。
- 仓库里不写机器绝对路径。SDK、JDK、缓存位置从环境变量来：FLUTTER_ROOT、JAVA_HOME、ANDROID_HOME、GRADLE_USER_HOME、PUB_CACHE。
- `app/build.gradle.kts` 的缓存依赖从 GRADLE_USER_HOME 推导，缺失时报出具体的 group/name/version。这是 `71767f7` 定下的做法，不要退回绝对路径。
- 本机环境放在仓库外的 `_env/env.sh`；`verify.sh` 依次读 MAEVE_ENV_FILE、同级 `_env/env.sh`、PATH。
- 生成目录不进 git：根目录 build、.gradle、local.properties，以及 flutter 下的 .android 和 .dart_tool。

## 5. 命名与代码

- Kotlin 包名 `com.daiyimo.maeve`；模块内包按能力分，命名沿用现有类名，不做半程重命名。
- `:app` 的 Kotlin 源码根是 `app/src/main/java`，不要新建平行的 `kotlin` 目录。
- Dart 文件名用 snake_case，类型用 UpperCamelCase；设计 token 放 `flutter/lib/design/`，通用组件放 `flutter/lib/widgets/`。
- 允许保留的历史名称（MCP 工具名、Author 序列化名、CLI 命令）不要顺手改，见 [migration.md](../spec/migration.md)。
- 捕获 CancellationException 后必须重抛；不使用 GlobalScope；不在工具或 ViewModel 里加副作用捷径。

## 6. 提交格式

最近几条提交的格式就是标准：

```text
type(scope): imperative summary

Problem the change addresses, in one short paragraph.

- What changed
- Why it is safe

Verified: command / device / result
```

- type 用 feat、fix、build、docs、refactor、test、chore；scope 用 core、app、flutter、docs、tools。
- 一个提交一件事。机械重命名、行为改动和文档更新分开。
- 验证行写实际跑过的命令和结果；没跑的部分明说没跑。

## 7. 文档规则

- 入口文档：根目录 `README.md`、`CONTRIBUTING.md`、`FLUTTER_MIGRATION.md`、`SPEC.md`。
- 文档索引：`docs/README.md` 和 `docs/dev/README.md`。新增文档要同时进索引。
- 英文配对：用户文档用 `.en.md` 后缀；开发文档除知识格式外暂时只有中文。
- README 里会变的清单（内置知识、服务商预设）由 `./gradlew :core:updateReadme` 生成，不手改标记之间。
- `docs/readme/` 下的译文要跟 README 同结构：相同的 `##` 数量、相同的本地图片集合、开头的 `translated-from` 指纹。改 README 必须同一次改完译文。
- 文档里反引号包住的路径、类名、函数名、工具名必须真实存在；`DocsConsistencyTest` 会查。会变的个数不写死，确需写时在行尾加计数豁免注释。
- 描述历史状态或外部平台 API 的文档，可以在开头用 `<!-- docs-check: spans-off — 原因 -->` 整篇退出符号检查；原因必须写在同一行。当前适用：`docs/adr/`、`docs/spec/migration.md`、`docs/spec/haptic-matrix.md`、`docs/dev/maeve-uiux-refactor.md`、`docs/dev/ios-development-standard.md`。当前状态文档不要用这个标记。
- 品牌与上游 URL 的清扫已完成，历史记录与 attribution 保留原样；新增文案不要夹带品牌替换，改动范围见 [migration.md](../spec/migration.md)。

## 8. 提交前检查

- [ ] 改动的模块对应命令通过；Flutter 改动跑了 `verify.sh`
- [ ] 文档索引、生成段落、译文指纹同步更新
- [ ] 没有新增机器绝对路径或未跟踪的生成目录
- [ ] 提交信息符合上面的格式，验证行是真实结果
- [ ] 触碰桥接、守卫链或后台屏时，写明改动前后的可观察行为

## 9. 已知债务

见 [project-review.md](project-review.md)。当前需要知道的几件：Windows 上的核心测试有平台性失败，Linux CI 才是判据；宿主 AGP 8.7.3 用 `android.suppressUnsupportedCompileSdk=36` 接受 compileSdk 36；Flutter 外壳仍是工作台，聊天与模型尚未接通。用户可见文案默认中文。
