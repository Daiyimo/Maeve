# Maeve 项目长期记忆

## 构建与真机安装（本机 PZ261A 有线 adb）

- **Flutter SDK 不在 PATH**：在 `E:/project/daiyimo/_toolchains/flutter`（3.47.7 stable），环境脚本 `E:/project/daiyimo/_env/env.sh`（设 JAVA_HOME/FLUTTER_ROOT/ANDROID_HOME/PUB_CACHE）。任何 gradle/flutter 命令前先 `. E:/project/daiyimo/_env/env.sh`。
- **真 app 是 `com.daiyimo.maeve`**（原生 `:app` 内嵌 Flutter AAR），不是 `flutter/` 模块的独立 host 包 `com.daiyimo.maeve_flutter.host`。
- **改 dart 代码**：走 `./gradlew :app:assembleDebug`，它自动用 `FLUTTER_ROOT` 重建 Flutter AAR（`:app:flutterBuildDebugAar`）再打进 APK。产物 `app/build/outputs/apk/debug/app-debug.apk`。
- **别直接 `flutter build apk`**：那装的是空壳 host 包，没有原生能力（无障碍/执行/通知），测不出真东西。
- **签名冲突**：设备上若装过 release 签名，debug 包会 INSTALL_FAILED_UPDATE_INCOMPATIBLE，需 `adb uninstall com.daiyimo.maeve` 后重装（清本地数据）。
- 启动：`adb shell monkey -p com.daiyimo.maeve -c android.intent.category.LAUNCHER 1`。
- 设备验证只用本机 adb 有线设备 PZ261A，不用 DevRun 云设备/模拟器。

## 设计系统要点（flutter/lib/design/）

- 液态玻璃四档：chrome/sheet/card/pill，浓度三档（清透/均衡/浓郁）见 `MaeveGlassDensity`。
- **洗层/背景色用 `MaeveColors.okSoft/warnSoft/badSoft`**（浅色 L≈.93 极淡），**别用 `MaeveSemantic.ok/warn/bad`**（那是文字色，浅色 L≈.45 深绿/深琥珀，拿来洗玻璃会读作深色块）。
- 页面取色一律走 `MaeveTheme.of(context)`，不写死色值。

## 任务完成后的提交规则

- 每次任务完成后，必须按逻辑主题拆成多个 commit，并使用中文提交信息。
- 只暂存本轮任务相关文件；提交前检查 `git status` 与 `git diff`，避免带入无关改动。
- 只执行 `git commit`，不执行 `git push`；除非用户明确要求推送。
