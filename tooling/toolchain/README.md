# 开发工具链标准（Android + Flutter）

这份目录是一套可复制的工具链基线：版本固定、路径由环境变量给出、装完能自检。Maeve 按它开发和验证；其他 Android/Flutter 项目可以整目录复制过去，只改 `versions.json` 里的版本。

## 1. 固定版本

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | Temurin 17.0.20.1 | AGP 8.7.3 要求 JDK 17；不要用系统默认的 JDK 24 |
| Gradle | 8.11.1 | AGP 8.7.3 要求 Gradle 8.9 及以上；用 wrapper 固定 |
| Android Gradle Plugin | 8.7.3 | `gradle/libs.versions.toml` 的 `agp` |
| Kotlin | 2.1.20 | 同上 |
| compileSdk / targetSdk / minSdk | 36 / 35 / 30 | `app/build.gradle.kts` |
| Android build-tools | 36.0.0 | 平台 36 对应版本 |
| NDK | 25.1.8937393 | 沙箱原生库使用的版本 |
| Flutter | 3.47.7 stable | Dart 3.13.5；`flutter/pubspec.yaml` 的约束是 `^3.13.5` |
| Python | 3.10+ | `tools/` 下的 CLI、i18n 和评测脚本 |

版本不是“最新即可”。升级顺序固定为 JDK → Gradle → AGP → Kotlin → compileSdk → Flutter，每一步都在本目录的 `verify.ps1` 和项目测试通过后再进下一步。

## 2. 目录约定

```text
<TOOLCHAINS>/
├── jdk-17/                      # JAVA_HOME
├── gradle-8.11.1/gradle-8.11.1/ # Gradle 发行版；也可以只用项目 wrapper
└── flutter/                     # FLUTTER_ROOT；zip 解压后就是 flutter/bin

<_ENV>/
├── gradle-home/                 # 可选的隔离 GRADLE_USER_HOME（默认复用 ~/.gradle）
├── pub-cache/                   # PUB_CACHE
└── env.ps1 / env.sh             # 本机实际使用的环境文件，不进 git
```

`<TOOLCHAINS>` 和 `<_ENV>` 都在仓库之外。仓库里只提交 `tooling/toolchain/` 的脚本和版本清单，不提交 SDK、缓存或 `local.properties`。

## 3. 安装

Windows PowerShell（5.1 或 PowerShell 7）：

```powershell
powershell -NoProfile -File tooling/toolchain/setup.ps1 -Toolchains 'D:\_toolchains' -EnvRoot 'D:\_env'
```

Git Bash：

```bash
TOOLCHAINS=/d/_toolchains ENV_ROOT=/d/_env bash tooling/toolchain/setup.sh
```

脚本是幂等的：已存在且版本正确的组件会跳过。Gradle 和 Flutter 的压缩包按 `versions.json` 里的 SHA-256 校验；JDK 从 Adoptium API 取最新的 17 GA，校验和随发布变化，脚本只校验版本。如果机器上已有 17，`-SkipJdk` / `SKIP_JDK=1` 可以复用。

装完后生成 `<ENV_ROOT>/env.ps1` 和 `<ENV_ROOT>/env.sh`，内容就是 `env.example.*` 里的变量。

## 4. 每次开工

```powershell
. <_ENV>\env.ps1
```

```bash
. <_ENV>/env.sh
```

环境文件负责：`JAVA_HOME`、`GRADLE_USER_HOME`、`PUB_CACHE`、`FLUTTER_ROOT`、`ANDROID_HOME`、`NO_PROXY`，并把各工具的 `bin` 放进 `PATH`。`GRADLE_USER_HOME` 默认复用已有的 `~/.gradle`；没有时才用 `<_ENV>/gradle-home`。不要在命令里临时写死某个盘符。

## 5. 自检

```powershell
powershell -NoProfile -File tooling/toolchain/verify.ps1
```

它检查 JDK、Gradle、Flutter、Dart、Android SDK、NDK，以及 `gradle/libs.versions.toml` 里的 AGP/Kotlin 是否与 `versions.json` 一致。任何一项不符都返回 1，并打印实际值与期望值。

## 6. 在别的项目复用

1. 复制整个 `tooling/toolchain/` 到目标项目。
2. 改 `versions.json`：只保留目标项目实际用到的组件，版本按目标项目的 `libs.versions.toml` / `pubspec.yaml` 反推。
3. 按第 3 节安装、第 4 节加载、第 5 节自检。
4. 把 `verify.ps1` 挂到开发文档的“提交前检查”，不要挂到每个本地构建上——它是环境门禁，不是编译步骤。

不要把这份目录当成跨项目共享的 SDK 安装器：它只固定版本和路径约定，真正的 SDK 在 `<TOOLCHAINS>` 里，可以被多个项目共用。

## 7. CI 对齐

` .github/workflows/ci.yml` 用同一组版本：

- `actions/setup-java` 固定 `temurin 17`
- `gradle/actions/setup-gradle` 使用 wrapper，Gradle 版本由 `gradle-wrapper.properties` 固定为 8.11.1
- `subosito/flutter-action` 固定 `3.47.7`
- Android job 先构建 Flutter AAR，再 `./gradlew :app:assembleDebug`

CI 不读本机的 `_env`，所以仓库里不能出现绝对路径；这正是 `env.example.*` 只写变量、不写盘符的原因。

## 8. 已知陷阱

- **JDK 24**：AGP 8.7.3 会报 `Could not create task of type 'OutgoingVariantsReportTask'`。必须把 `JAVA_HOME` 指到 17。
- **系统代理**：`flutter test` 走 loopback WebSocket，代理会把它打断。`NO_PROXY` 必须含 `127.0.0.1,localhost,::1`。
- **Git Bash 路径**：`PATH` 里要放 POSIX 形式（`/d/...`），`FLUTTER_ROOT` 用 `cygpath` 转一次。
- **盘符搬家**：Gradle 缓存从 `GRADLE_USER_HOME` 推导；不要在任何 `.gradle.kts` 里写死缓存路径。
- **compileSdk 36 + AGP 8.7.3**：AGP 8.7.3 官方只测到 35，项目用 `android.suppressUnsupportedCompileSdk=36` 显式接受这个组合；升级 AGP 是单独的版本决策。
- **两套 Gradle**：宿主 `:app` 用本仓库固定的 Gradle 8.11.1 / AGP 8.7.3；`flutter build aar` 生成的 `.android` 工程用 Flutter 模板自带的 Gradle 9.3.1 / AGP 9.1.0。AAR 是两者的边界，不要试图把两套版本强行统一。
- **pub 缓存搬家**：换 `PUB_CACHE` 后跑一次 `flutter pub get --offline`，否则 analyze 会报 `include_file_not_found`。
- **空格与中文路径**：Gradle、Flutter 和 NDK 都对路径敏感，工具链放在纯 ASCII、无空格的目录下。

## 9. 升级检查表

- [ ] `versions.json`、`gradle/libs.versions.toml`、`pubspec.yaml` 三处版本一致
- [ ] `verify.ps1` 通过
- [ ] `./gradlew :core:test` 通过
- [ ] `bash flutter/tool/verify.sh` 通过
- [ ] `./gradlew :app:assembleDebug` 通过
- [ ] CI 的版本号同步修改
