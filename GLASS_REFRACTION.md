# Maeve 液态玻璃：强折射修复

## 交付范围

- 本目录是最终实施目录；已验证的强折射方案已从验证副本同步到这里。
- 主界面当前由 Flutter 承载；Compose 端保留原生页面、浮层与未来壳层能力。
- 本次同时修复两端，而不是只把 Flutter 的观感“调白”。

## 根因

| 问题 | 原因 | 修复 |
| --- | --- | --- |
| Flutter 顶栏采样被挡死 | `TopBar` 在 `BackdropFilter` 之外先画了不透明 `colors.bg` | 删除实色父底，只保留安全区 padding |
| 输入栏模糊被压掉 | `_ComposerDock` 在玻璃下方铺了 `bg α=.92 → 1` 的整宽渐隐 | 删除渐隐，让玻璃直接采样聊天列表 |
| 聊天抽屉不是玻璃 | 抽屉本体是不透明 `DecoratedBox`，只有内部小按钮复用玻璃样式 | 抽屉本体改用 Sheet 档折射玻璃 |
| 两端参数漂移 | 两份手写参数表在 fill、fallback、sheen、阴影和能力门控上不一致 | 新增 `shared/glass_tokens.json`，两端镜像并由测试锁定 |
| 只有磨砂、没有折射 | Flutter 只有高斯模糊 + 颜色矩阵；Compose 只有 `BlurEffect` | Flutter Impeller FragmentShader；Compose API 33+ RuntimeShader |

## 新光学模型

玻璃不再是“提高模糊强度”，而是：

1. **低磨砂底座**：Chrome σ=3dp，Sheet σ=4dp，保留背景细节。
2. **边缘透镜**：圆角 SDF 法线驱动 UV 位移，Chrome 14dp、Sheet 18dp。
3. **色散**：R/B 通道沿法线反向偏移，Chrome 0.65dp、Sheet 0.85dp。
4. **Fresnel + 镜面高光**：左上受光，右下衰减，强调边缘厚度。
5. **低 alpha 染色**：Chrome 10%/14%（浅/深），Sheet 14%/18%。
6. **清晰前景**：折射只作用于背景采样层，文字和控件不扭曲。

浓度档位继续可用：

| 档位 | tint | blur | saturation | light | refraction |
| --- | ---: | ---: | ---: | ---: | ---: |
| 清透 | .45 | .70 | .90 | .60 | .85 |
| 均衡 | 1 | 1 | 1 | 1 | 1 |
| 浓郁 | 1.45 | 1.35 | 1.10 | 1.40 | 1.15 |

## Flutter 管线

```text
MaeveBackdrop + 滚动内容
  → BackdropGroup（共享真实背景输入）
  → bounded blur（低 σ）
  → ImageFilter.shader（边缘位移 / 色散 / 高光 / tone）
  → clip（精确 Flutter shape）
  → 半透明 fill + bevel + sheen + 内容 + edge
```

- Shader：`flutter/shaders/liquid_glass.frag`
- 组件：`flutter/lib/widgets/glass_surface.dart`
- 参数：`flutter/lib/design/maeve_surfaces.dart`
- 顶栏：`flutter/lib/ui/common/app_page.dart`
- 输入栏：`flutter/lib/app/chat_page.dart`
- 抽屉：`flutter/lib/app/sessions_drawer.dart`

## Compose 管线

```text
内容节点 glassSource
  → GraphicsLayer.record（扩大 bleed）
  → BlurEffect
  → RuntimeShaderEffect（AGSL 边缘透镜）
  → drawLayer
  → fill + rim + bevel + hairline + 前景
```

- AGSL：`app/src/main/res/raw/glass_refraction.agsl`
- 运行时：`app/src/main/java/com/daiyimo/maeve/ui/components/Surfaces.kt`
- 参数：`app/src/main/java/com/daiyimo/maeve/ui/theme/MaeveSurfaces.kt`
- 顶栏：`app/src/main/java/com/daiyimo/maeve/ui/components/Controls.kt`

## 回退矩阵

| 环境 | 行为 |
| --- | --- |
| Flutter + Impeller + shader 加载成功 | 真实背景折射 |
| Flutter + Skia / shader 失败 | 原生低磨砂 + tone，不伪造折射 |
| Android API 33+ | RuntimeShader 折射 |
| Android API 31–32 | BlurEffect + ColorFilter |
| Android API 30 | 不透明复合 fallback + 边光 |
| 高对比度 / 关闭玻璃 | 不透明复合 fallback，文字稳定 |

## 验证

本轮在 Android API 36 x86_64 模拟器上完成：

- Flutter 静态检查：通过，无 issue。
- Flutter 主机测试：296/296 通过（仅关闭运行时 shader，模糊回退仍真实执行）。
- Flutter 集成测试：3/3 通过，确认 `ui.ImageFilter.isShaderFilterSupported == true`。
- Android JVM：1651 个测试，0 failure / 0 error，62 skipped。
- Android Debug APK：构建通过；Flutter Debug AAR 在最终 shader 修正后重新构建。

复现命令：

- Flutter：`flutter test --no-pub --dart-define=MAEVE_GLASS_SHADER=false --concurrency=2`
  （主机 widget tester 仅禁用运行时 shader，仍执行真实模糊回退；shader 仍参与编译。模拟器/设备默认开启折射。）
- Flutter 静态检查：`flutter analyze --no-pub --fatal-infos`
- Android JVM：`./gradlew :app:testDebugUnitTest -PskipFlutterAar`
- Android 构建：`./gradlew :app:assembleDebug`
- 参数对齐：`flutter/test/theme/glass_tokens_parity_test.dart` 与 `GlassStyleTest.kt` 都读取共享 JSON。

## 无真机截图

- Flutter 集成测试：`flutter/integration_test/glass_gallery_test.dart`
- Compose debug Gallery：`app/src/debug/java/com/daiyimo/maeve/ui/GlassGalleryActivity.kt`
- 总览拼图：`tooling/glass/build_overview.py`
- 最终图：`G:\vibe-coding\daiyimo\maeve-glass-artifacts\glass\liquid-glass-overview.png`
- 浅色主题原版/修改版 10 页对比：`flutter/integration_test/light_theme_compare_test.dart`
- 浅色对比拼图：`tooling/glass/build_light_comparison.py`
- 浅色对比最终图：`G:\vibe-coding\daiyimo\maeve-glass-artifacts\glass\light-compare/liquid-glass-light-comparison.png`

截图只在 Android 模拟器上生成；不会安装到已连接的真机。浅色对比使用同一组假数据与页面路径，左侧为原版、右侧为修改版。

## 已知边界

- Compose 当前没有主聊天抽屉 UI；本次展示其 Sheet 档原生玻璃，Flutter 抽屉是用户实际看到的聊天抽屉。
- Flutter 的连续曲率 clip 是精确的，Shader SDF 用圆角矩形近似边缘法线；16/28dp 半径下视觉差异很小。
- API 30 与 Skia 回退不具备色散和 UV 扭曲，这是能力边界，不用假高光冒充折射。
