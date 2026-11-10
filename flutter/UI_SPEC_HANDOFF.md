# Maeve Flutter UI 复刻 · 交接执行规格（HANDOFF）

> ⛔ **本规格已作废（2026-11-10）**：它产出了 dsh-go 的视觉复刻。用户已决定重写为 Maeve 自己的
> 设计语言，执行规格见 [`REWRITE_SPEC.md`](REWRITE_SPEC.md)。本文件仅作历史记录保留，
> **不要照它施工**；其中的色值 / token 数值 / 屏幕骨架全部作废。

---

> 本文件是**自包含执行规格**：交给的 agent 只需要「Maeve 项目路径 + 本文件」即可从零上下文启动，
> 按第 5 节执行计划**全量执行到满足第 9 节验收**为止。做完一页划掉一项，未划完不停。
>
> 上游参考：`dsh-go`（AGPL-3.0，只读调研）。**本规格已内嵌全部关键设计数值**（圆角/间距/动效/四层玻璃/六套配色色值/OKLCH/字体/触感/Flutter API 对照），**完全自包含，不依赖任何仓库外文件**。
> 它与上游源码解耦：只记录设计决策 / 数值 / 枚举 / 交互语义，按它在 Flutter 里重写，不复制任何上游代码。可选的交叉参考（同工作区才有）：`E:/project/daiyimo/dsh-go-UI-SPEC.md`。

---

## 0. 一句话目标与终局

**把 Maeve 的 Flutter 壳（`flutter/`）做成和 dsh-go 视觉与交互一致的自绘界面，并且最终只保留 Flutter 自绘页、退役所有 Kotlin/Compose 原始页面。**

- 允许范围内“一模一样”：同一套设计 token、圆角、四层玻璃、动效曲线、逐页骨架、组件行为。
- 禁止：复制 dsh-go 的任何源码 / 资源 / 字体 / 品牌素材（见第 1 节红线）。
- 终局：Flutter 是唯一 UI 层；Compose 页面在 Flutter 达成功能对等 + 真机稳定后**逐屏退役**（第 10 节）。

---

## 1. 不可逾越的红线（每次改动前默读）

| 事实 | 后果 / 纪律 |
|---|---|
| dsh-go 仓库整体 **AGPL-3.0**；名称 / Logo / Deepy 形象**不在授权内** | 复制它的 Dart/资源进 Maeve（Apache-2.0）= 创作衍生作品，AGPL 会要求整个组合作品以 AGPL 发布，与 Maeve 的 Apache-2.0 目标冲突 |
| 部分文件头写 "Portions derived from PiPilot (MIT)" | 只说明其中一部分血统，**不能据此认定整文件可自由复制**；安全用法是逐行确认 MIT 血统，实务不可靠 |

**纪律（三重兜底，缺一不可）：**
1. 只借鉴**设计决策、数值、枚举、交互语义**，按本规格在 Flutter 里**重写**。数值/档位/交互语义不受版权保护，重写得到同样观感且 Maeve 保持 Apache-2.0。
2. 要对照上游找感觉时，用**笼子**：把上游副本放仓库外或进 `.git/info/exclude` 的 `_ref/`，**永不提交**，保留原版权头，一行不改。
3. **提交前扫描**：`git ls-files` 里出现上游痕迹就拒绝提交。把下面脚本存为 `tools/check_no_upstream_copy.py`（只扫 git 跟踪文件，`_ref/` 进了 exclude 就永远扫不到），提交前跑一遍，非 0 即拦：
   ```python
   #!/usr/bin/env python3
   """阻止上游 AGPL 代码被提交。只看 git 跟踪的文件。"""
   import pathlib, subprocess, sys
   NEEDLES = ("Portions derived from PiPilot", "ccch1mneyyy", "package:dsh_client")
   ALLOW = {"tools/check_no_upstream_copy.py"}
   EXTS = {".dart", ".kt", ".kts", ".md", ".xml", ".yaml", ".yml", ".sh", ".py"}
   bad = []
   for rel in subprocess.check_output(["git", "ls-files"], text=True).splitlines():
       if rel in ALLOW or pathlib.Path(rel).suffix not in EXTS:
           continue
       try:
           text = pathlib.Path(rel).read_text(encoding="utf-8", errors="ignore")
       except OSError:
           continue
       if any(n in text for n in NEEDLES):
           bad.append(rel)
   print("上游代码被跟踪:\n  " + "\n  ".join(bad) if bad else "OK：无上游代码被跟踪")
   sys.exit(1 if bad else 0)
   ```

**绝不做**：整体改 AGPL、把上游代码“整理版权”后并入、提交后再删。公开历史出现过一次即算分发过，不可撤销。

---

## 2. 环境与验证（每个 Phase 结束必跑）

- 模块：`flutter/`（Flutter module，不是独立 app；`:app` 消费它构建出的 debug AAR）。
- 工具链：Flutter **3.47.7** / Dart **3.13.5** / JDK **17**（本机在 `E:/project/daiyimo/_toolchains/`，`_env/env.sh` 自动 source）。
- **验证命令（仓库根目录）**：
  ```bash
  bash flutter/tool/verify.sh
  ```
  依次跑 `dart format` → `flutter analyze` → `flutter test`，任一步失败即停。format 失败=没格式化，不是坏。
- **三个环境坑（`env.sh` 已处理，别重复踩）**：
  1. 系统默认 JDK 是 24，AGP 会挂；必须 `JAVA_HOME` 指 JDK 17。
  2. 机器常有 HTTP 代理，会打断 `flutter test` 的 loopback WebSocket；保持 `NO_PROXY=127.0.0.1,localhost,::1`。
  3. Git Bash 只认 POSIX 风格 PATH；`env.sh` 会 `cygpath` 转换 `FLUTTER_ROOT`。
- **真机验证**：玻璃模糊必须在真机/模拟器上看（见第 11 节风险）。`cd flutter && flutter run`。
- 通过标准：`dart format` 干净、`flutter analyze` **No issues found**、`flutter test` **全绿**、改动页在真机截图符合第 9 节对应项。

---

## 3. 现状审计（动工前必读，避免重做已完成部分）

> 结论先行：**玻璃引擎已忠于 spec 且能编译**；缺的是 token 层精度、组件库、以及逐页骨架。B4 顶栏已按 spec 重做完成。

### 3.1 文件地图（`flutter/lib/`）
| 文件 | 作用 | 状态 |
|---|---|---|
| `main.dart` | 壳：响应式 rail/drawer 导航、对话、composer、运行/记忆/设置、入口 | 布局是“通用工作台”，逐页待按 B 区重排 |
| `design/maeve_surfaces.dart` | `MaeveSurfaces`(ThemeExtension) + 四层玻璃 `MaeveGlassStyle` + `MaeveBackdrop`(渐变+双光晕) | ✅ 忠于 spec A3/A4 数值 |
| `design/maeve_tokens.dart` | `MaeveTokens` 颜色/间距/圆角/动效 + `maeveTheme()` | ⚠️ 单套配色、圆角 10/16/24(非 6/10/14/20/28)、无动效系统、默认字体 |
| `widgets/glass_surface.dart` | `MaeveGlassSurface`(四层玻璃统一入口) + `MaeveGlassButton` | ✅ 忠于 spec E1（squircle/有界模糊/BackdropGroup/饱和度/发丝/顶光/外阴影/降级） |
| `widgets/maeve_mark.dart` | `MaeveMark` 品牌标识 + painter | ✅ 自绘，非上游素材 |
| `models/conversation.dart` | `Conversation` / `Message`（内存态） | 原型：仅进程内存 |
| `bridge/maeve_bridge.dart` | `maeve/ui`(method) + `maeve/ui/events`(event) + open_settings/status | 原型：只接了这两个 |

### 3.2 玻璃数值基线（`maeve_surfaces.dart`，已对齐 spec，**别改坏**）
- chrome：fill 白 α0.70(浅)/烟灰 α0.72(深)、blurSigma **18**、saturation **1.3**(浅)/**1.2**(深)、发丝+顶边 45% 高光、双层外阴影。
- sheet：fill 白 α0.82 / sheetDark α0.86、blurSigma **24**、saturation **1.4**/1.2。
- card / pill：**不模糊**，仅半透明填充 + 发丝 + 顶光（背后是静态背景，再模糊白烧 GPU）。
- 背景：竖向渐变 + 右上主色光晕 + 左下邻近色光晕；降级 `blurEnabled==false`/高对比度 → 不建 `BackdropFilter`，用 `fallbackFill`。
- `MaeveGlassSurface(tier, circle, stadium, borderRadius, grouped, settled, blur, tint, onTap, padding)`；`grouped:true` 走 `BackdropFilter.grouped`，外层包 `BackdropGroup` 即共享一次采样。

### 3.3 原型限制（对接桥接前一直成立）
- 对话回复是本地延时 demo；对话/记忆仅进程内存，不落盘；运行页是视觉就绪态；附件未接 picker。
- 桥只接 `open_settings` / `status`；chat、模型调用、持久化、agent 执行**未接**。
- 因此：**视觉先行**——先把 theme + 组件 + 设置/关于页做出来（不依赖引擎），再接桥，避免接业务时返工。

---

## 4. 设计 Token 目标值（Part A，落进 `maeve_tokens.dart` + `maeve_surfaces.dart`）

> 玻璃数值已在 3.2 落地；本节是**待补/待校准**的部分。命名用 Maeve 自己的（雾紫默认 `reverie`/`mist` 由你定），色值按表重列，**不搬上游命名**。

### 4.1 圆角（连续曲率 Squircle，非圆角矩形）——六档，用途固定不随页变
| token | 值 | 固定用途 |
|---|---|---|
| xs | 6 | 行内代码、角标、进度条端头 |
| sm | 10 | Chip、工具图标块、小按钮、菜单项高亮 |
| md | 14 | 卡片、代码井、输入框、菜单、主按钮 |
| lg | 20 | 用户气泡、审批卡、设置分组卡 |
| xl | 28 | 输入条(composer)、底部弹层、对话框 |
| pill | — | 顶栏胶囊、模型胶囊、发送键、思考胶囊（`StadiumBorder`，半径=高一半） |

- 派生：`card`=Squircle(md)、`bubble`=Squircle(lg)、`dialog`=Squircle(xl)、`sheet`=仅顶两角 xl、`outlined(color, md, 1)`。
- **当前漂移**：chrome/sheet 现为 24（应 28）、card 现为 16（应 14）。composer 必须 28。
- 裁切与形状必须同一个 `RoundedSuperellipseBorder` 实例来源，否则模糊边界与填充边界差 1px。

### 4.2 间距（4 的倍数为主）
`xxs/xs/sm/md/lg/xl/xxl/xxxl = 2/4/8/12/16/20/24/32`；`gutter`=16(手机左右留白)、`inTurn`=8(同轮内)、`betweenTurns`=24(换轮)、`readingWidth`=720(正文最大阅读宽，平板/横屏居中)、`floatInset`=12、`floatingWidth`=696(=720−2×12)。**当前断点 900 应改 840**（见 4.7）。

### 4.3 动效（安静、短、跟手）
| 类 | token:值 | 用途 |
|---|---|---|
| 反馈 ≤160ms | press 90 / quick 160 | 按压缩放、水波起始 / 淡入淡出、图标切换、复制对勾 |
| 结构 240–340 | standard 240 / collapse 280 / emphasized 340 | 列表项进出 / 思考块折叠 / 胶囊变形、抽屉落位 |
| 弹层 | sheetIn 380 / sheetOut 260 / glassIn 120 | 底部弹层进/出 / 停稳后模糊透出 |
| 页面 | page 300 / push 300 / slow 480 | 步骤切换 / 二级页 iOS 式水平推入 |
| 环境 | breath 2400 / stagger 30(前8项) / rise 8 | 呼吸 / 首现逐项错开 / 上移距离 |

- 曲线：`std`=standard、`enter`=emphasizedDecelerate、`exit`=emphasizedAccelerate、`collapseCurve`=easeInOutCubic、`glide`=Cubic(.32,.72,0,1)(iOS 长尾)、`sheetRise`=Cubic(.2,.9,.25,1)、`rubberBand` limit=48。
- 弹簧：pressSpring 150/bounce0、layoutSpring 350/bounce0、sheetSpring 500/bounce0、flingSpring 500/bounce0.15。
- `pressScale`=0.97（按下缩到 0.97，不越过）。**减少动画时所有时长归 0，循环动画停在静止帧**（`AppMotion.of(context,d)` + `reduced()`）。
- **当前缺失**：只有 140/220/320 三个平整 duration，无曲线/弹簧/Pressable 压缩。

### 4.4 六套配色（完整色值，直接落进 token；按 Maeve 命名重列，不搬上游命名）

顺序即设置页展示顺序，默认第 1 套（雾紫，Maeve 品牌默认色）。每套 15 色 + 中性色色相/彩度倍率 + 两团光晕。

| 角色 | ① 雾紫(默认) | ② 黛青 | ③ 暮霞 | ④  turquoise 松绿 | ⑤ 琥珀 | ⑥ 石墨 |
|---|---|---|---|---|---|---|
| seed（浅色主色） | `#4B67B4` | `#236185` | `#726291` | `#34766F` | `#8D6636` | `#2C2E32` |
| pressed | `#3D569D` | `#145071` | `#61517D` | `#296760` | `#7A5528` | `#1D1F23` |
| container | `#DEE8FF` | `#DBEAF5` | `#EAE5F4` | `#DBECE9` | `#F2E6D9` | `#E3E6EC` |
| onContainer | `#213262` | `#123950` | `#392F4C` | `#123D39` | `#483011` | `#2C2E32` |
| bubble（用户气泡） | `#D9E4FD` | `#D6E7F3` | `#E6E1F1` | `#D6E8E6` | `#EEE2D3` | `#E0E3E8` |
| glow（右上光晕） | `#ABC3FC` | `#A2CAE5` | `#C8BDE1` | `#A3CEC8` | `#DCBE9D` | `#BBBEC3` |
| seedDark | `#97B1F0` | `#8CB9D7` | `#B8ABD2` | `#8DBEB7` | `#CCAC87` | `#E5E8ED` |
| pressedDark | `#859EDC` | `#79A6C4` | `#A598BF` | `#7BABA4` | `#B99975` | `#CED1D6` |
| onSeedDark | `#121C34` | `#0D1F2B` | `#1F1A29` | `#0D211F` | `#271A0C` | `#191B1E` |
| containerDark | `#2E3C5F` | `#274152` | `#40394F` | `#274340` | `#4C3A25` | `#3B3D41` |
| onContainerDark | `#D8E1F6` | `#D5E3ED` | `#E2DEEC` | `#D6E5E2` | `#EADFD3` | `#DEE1E7` |
| bubbleDark | `#293144` | `#26333C` | `#332F3B` | `#263533` | `#392F24` | `#2E3034` |
| glowDark | `#51679D` | `#466E88` | `#6D6284` | `#47726D` | `#7F6343` | `#616368` |
| haze（左下光晕） | `#D5CBEB` | `#C7D0EC` | `#E6C7D7` | `#B9D7E3` | `#EAC8C1` | `#CED1D6` |
| hazeDark | `#514765` | `#444C66` | `#5F4353` | `#34525E` | `#64443D` | `#4A4D52` |
| tintHue（中性色色相） | 雾蓝基色(随 seed) | 238 | 295 | 190 | 75 | 262 |
| tintChroma（中性色彩度倍率） | 1 | 0.9 | 0.85 | 0.7 | 0.75 | 0.25 |

- **中性色规则**：中性色阶原样转到 `tintHue`、彩度乘 `tintChroma`、**明度不动**——所以各级文字对比度不随配色变化。
- 背景/光晕/scrim 接第 3.2 节的 `MaeveSurfaces`；换配色时 `backdropTop/Bottom`、`glowPrimary/Secondary`、`userBubble/onUserBubble`、`scrim` 一并换（浅色 scrim 墨 α0.32 / 深色黑 α0.48）。
- 用色比例 70/25/5（中性/文字/强调）；`surfaceTint=transparent`、`elevation=0`。
- **每套预设写对比度单测**（`test/theme/palette_contrast_test.dart`），逐条验第 6 节 C1。

**OKLCH 推导（自定义色 / 壁纸取色共用；预设已烘焙好色值，仅在做“自定义/取色”时用）**：
- 常量：`maxChroma=0.13`、`lightSeedL=0.53`、`darkLift=0.234`、自定义色明度限 0.30–0.60。
- 浅色主色白字、深色主色同色相深墨字。

| 角色 | 明度 L | 彩度（× 强调彩度） |
|---|---|---|
| seed / pressed | 0.53 / 0.47 | 1 / 0.95 |
| container / onContainer | 0.93 / 0.33 | 0.26 / 0.70 |
| bubble / glow | 0.918 / 0.819 | 0.29 / 0.68 |
| seedDark / pressedDark | 0.764 / 0.704 | 0.76 |
| onSeedDark | 0.231 | 0.39 |
| containerDark / onContainerDark | 0.361 / 0.909 | 0.51 / 0.24 |
| bubbleDark / glowDark | 0.314 / 0.52 | 0.29 / 0.72 |

- **对比度守卫**：二分 24 步调到达标——非文本 ≥ `3:1`(WCAG 1.4.11)、文本 ≥ `4.5:1`(1.4.3)、container 上 onContainer ≥ `7:1`、用户气泡内主/次文字 ≥ `5.8`。

### 4.5 字体
- **不打包中文字体**，跟随系统（Roboto + 系统中文字体）；国产 ROM 已带 MiSans/OPPO Sans 等。
- 字重只用 **400/600** 两档（大号展示字可用 300）；**不用衬线标题**（国产 ROM 缺 Noto Serif CJK）。
- 等宽：打包 **JetBrains Mono**（OFL，Regular+Medium+Bold ~0.8MB），关 `calt`/`liga`；回退链 `monospace → Noto Sans Mono CJK SC → Noto Sans CJK SC`；**业务代码不写 `fontFamily` 字面量，统一走一个出口**；版本号/统计用等宽数字(tabular)。
- `MaterialApp` 必须 `supportedLocales: [Locale('zh','CN'), …]` 并接 `GlobalMaterialLocalizations`，否则非中文系统语言取到日文字形；英文 UI 也传 `zh_CN` glyph locale。**当前未声明 supportedLocales，要补。**

### 4.6 交互组件规格（A7）
- `Pressable`：按下缩 `pressScale 0.97`(pressSpring 不越过)、水波起始 90ms、**滚动中延迟 90ms 显现**防误触。
- `HoldToConfirm`：按住 **1500ms** 环形进度；down 一次 Tick、完成一次 Heavy；**用原始指针监听，不走手势识别器**。
- 触感五级：Tick→Light→Medium→Heavy→Reject；先查 `areEffectsSupported`，集中一个入口，设置里可关。
- 状态必须**色 + 图标/文字双编码**，不靠颜色单独表状态；顶栏状态行给 screen reader 单独文案。
- 当前缺失：无 Pressable / HoldToConfirm / 触感入口。

### 4.7 自适应
- 断点 **840dp**：≥840 列表常驻 rail/sidebar，对话详情是旁列，`≡` 变折叠/展开；<840 才是拉开式抽屉。**当前用 900，改 840。**
- 横屏/平板正文 `readingWidth 720` 居中；切 tab/页背景一页一张不闪。

---

## 5. 执行计划（按序全量执行，做完一页划掉，未划完不停）

> 原则：**先 theme 层 → 再通用组件 → 再逐屏替换**。先做三张“一眼定生死”的面（B4 顶栏 / B5 输入条 / B8 设置 hub），
> 用户对“是不是那味”的判断主要来自这三处。每完成一屏跑第 2 节验证 + 第 9 节对应验收 + 真机截图。

### Phase 1 · theme 层（落进 `lib/design/`，补对比度单测）
- [x] 圆角改六档 6/10/14/20/28 + pill；chrome/sheet→28、card→14；派生形状 card/bubble/dialog/sheet。
      （`MaeveRadius` / `MaeveShapes`；`glass_surface.dart` 默认圆角已改 28/14）
- [x] 六套配色（Maeve 命名重列 A4 色值）+ 中性色 hue/chroma 规则 + 两团光晕 + scrim。
      （`MaevePalette`：雾紫/黛青/暮霞/松绿/琥珀/石墨；中性色只动色相与彩度，明度固定）
- [x] 动效系统：时长/曲线/弹簧 token + `AppMotion.of` + `reduced()`；接进主题。
      （`maeve_motion.dart`；减少动画时所有时长归零、按压不缩放、循环动画停静止帧）
- [x] 字体：JetBrains Mono 打包 + 统一等宽出口(关 liga/calt) + `supportedLocales: zh_CN` + localizations。
      （`MaeveFonts.mono` + `flutter/assets/fonts/` + `GlobalMaterialLocalizations`，locale 固定 zh_CN）
- [x] `test/theme/palette_contrast_test.dart`：每套配色逐条验对比度。
      （12 组 × 8 项，含气泡 5.8、container 7.0、"换配色对比度不变"的漂移上限）
- **完成判据**：第 9 节 C1/C4/C5/C7 通过；`flutter/design/` 数值与第 4 节一致。

### Phase 2 · 通用组件（落进 `lib/ui/common/`、`lib/ui/settings/`）
- [x] `Pressable`（压缩放 + 水波 + 滚动延迟显现）——`ui/common/pressable.dart`
- [x] `HoldToConfirm`（1500ms 环形 + 触感 + 原始指针）——`ui/common/hold_to_confirm.dart`
- [x] `AppPage.list`（二级页骨架：标题栏 56 + 列表 + 顶渐隐 + iOS 推入 + 返回 chrome 圆钮 44）
- [x] `SettingsSection` / `SettingsTile` / `SettingsSwitchTile` / `SettingsSegmentTile` / `SettingsOverview` / `StatusChip` / `SearchCapsuleButton` / `FeatureTile` / `ListPageRow`
- [x] `AppSheet` / `SheetTitleBar`（sheet 玻璃 + 标题栏 + 抓手）
- [x] `SquircleBorder`（连续曲率，复用/封装 `RoundedSuperellipseBorder`）——`MaeveShapes`
- [x] `BackdropGroup` 用法固化（顶栏多件共享一次采样）——B4 顶栏一处 group 罩四件
- [x] `EmptyView` / `StatusDot` / `HeaderStatus` / `BriefNotice` / `ActivityStrip` / `ThinkingPill` / `PermissionPill` / `AutoApprovePill` / `SmoothSize` / `ExpandReveal`
- **已有可复用**：`MaeveGlassSurface` / `MaeveGlassButton` / `MaeveBackdrop` / `MaeveMark`。
- **完成判据**：第 9 节 C2/C3/C8/C11 通过。

### Phase 3 · 逐屏替换（优先级从高到低，每屏过第 9 节全表 + 真机截图）
- [x] **B4 聊天顶栏**：浮动胶囊头（≡ 圆钮 + 标题胶囊[标题+状态行] + 模型胶囊 `✦ <模型> · 高 ⌄` + ＋ 圆钮），共用一个 `BackdropGroup`，无整条色带，浮在滚动内容上，内容滚到栏下才出页面色渐隐。**已完成**（窄屏让掉模型胶囊；点按给占位提示）。
- [x] **B5 输入条 composer**：chrome 玻璃 + 圆角 **28**；单行高 56，1–6 行自动增高；两颗 40 圆键(＋附件 / 发送或停止)贴边同心；**浮在阅读列上**(宽 floatingWidth，离列 floatInset 12)，非 docked。实现完成，**真机截图待补**(设备 adb offline)。
      （语音四态未做：需要 Android 语音桥接，属于 B11；当前空草稿时发送键为禁用态箭头）
- [x] **B8 设置 hub**：搜索胶囊 + `SettingsOverview`(账号/余额/引擎一卡) + **六组卡**(AI/能力/外观/通用/主机/帮助与关于) + `SettingsTile` 行(icon+tint+spot+右 value) + 底部居中小 BrandMark；hub 上不放任何放宽保护/删数据的东西。实现完成，**真机截图待补**(设备 adb offline)。
- [x] **B2 主壳 HomeShell**：聊天页在下；会话抽屉从左拉开盖住它；QuickPanel 从右拉开；两者互斥；单一 `AnimationController` 跟手驱动；落位 emphasized+glide；甩出 flingSpring；抽屉宽 `min(sidebarWidth+左安全区, 屏宽−56)`；拉开先 unfocus。断点 840。
      （手势挂在整棵树外面，遮罩之上；边缘优先，右侧拉开时左侧自动让位）
- [x] **B3 聊天页**：状态行(状态点+一句人话，不走秒)；消息列表 inTurn 8 / betweenTurns 24，readingWidth 720 居中，已完成轮次可折叠成一行；空状态可点建议；插话/打断；底部活动条/自动同意 pill。
      （待办/引用 chip/附件条：组件位已留，接桥后再接真实数据）
- [x] **B6 会话抽屉 SessionsDrawer**：品牌行 + 新对话主按钮；搜索；工作区切换；状态筛选条；列表(置顶→文件夹→按天分组，未读徽标，当前会话主色 9%/18% 软填充)；归档提示；底部 identity bar(本机/远程名、网页版、设置)；多选模式。
- [x] **B7 QuickPanel**：与抽屉同一套跟手/落位；快捷入口(web/remote/plugins/engine/settings/… 用户自选)；⚙ 长按进设置搜索。
- [x] **B1 首启引导 onboarding**：welcome→runtime(本机/远程)→appearance→access→apiKey→finishing；appearance/access/apiKey 可"稍后"；步骤条只数要回答的问题(4)。
      （"并行解压"未做：runtime 包体还在 Android 侧，Flutter 引导不参与解压）
- [x] **B9 设置子页**：AppearancePage(六配色/图标/主题模式 + 实时预览)、InterfacePage、PerformancePage(减少透明效果/低端机)、Voice/Notification/ReplyPrefs/Privacy/SystemAccess/BackgroundGuide/Diagnostics/About/Account/Providers/Plugins/Engine/Remote/EngineSettingsHub。
- [x] **B10 手机操控页 PhoneControlScreen**：总开关 + 首次显著披露(按住 1.5s 才开)；两行状态 + 去开启；读屏测试(不发模型)；悬浮条三态(胶囊→卡片→小球，按住 1s 停止)；确认方式(询问[默认]/自主，确认卡 30s 不选按拒绝)；应用名单三档(支付/银行/券商不碰·硬、微信每步确认、其余照常)；解除限制三项(默认全关，长按 1.5s 生效，任务中禁用)；最近操作(不含屏幕内容)；进阶。语义对齐 `AppTier.kt` / `SensitiveText.kt` / `ConfirmDecision.kt`。
- [x] **B11 其余(壳)**：WebView 网页版(地址栏+内容区壳)、终端(独立深色主题+等宽)、分享(复制全文/Markdown，系统分享待接桥)、
      社区、更新、关于、账号、引擎、远程、插件、运行、记忆 全部有页；能力页入口在设置「能力」组。
      **仍缺(必须平台桥接)**：WebView 真实内容、桌宠、插件原生界面、语音四态、系统分享面板。

### Phase 4 · 退役 Compose（见第 10 节，放在 Flutter 功能对等 + 真机稳定之后）

---

## 6. 验收清单（Part C，每页/每次改材质都跑）

| # | 检查 | 通过标准 |
|---|---|---|
| C1 | 六套配色 × 浅/深 | 文本对比度 ≥4.5:1、非文本 ≥3:1、container/onContainer ≥7:1；**换配色时各级文字对比度不变**（中性色明度不动） |
| C2 | 玻璃层数 | 同屏真模糊 ≤3；顶栏多个互不重叠玻璃共用一个 BackdropGroup；列表里没有 card/pill 误用模糊 |
| C3 | 模糊质量 | 模糊有界(边缘不渗色)；玻璃内不发灰(阴影只画形状外)；顶边 45% 渐隐高光；0.5px 发丝描边两种亮度下都看得见 |
| C4 | 降级路径 | 开“减少透明效果”/系统高对比度 → 无 BackdropFilter、fallbackFill 接近不透明、文字仍可读；低端机关模糊 |
| C5 | 动效取值 | 按压 90ms/0.97、反馈 ≤160ms、结构 240–340、sheetIn 380、breath 2400；抽屉/弹层落位 iOS 长尾减速(glide)非 linear |
| C6 | 跟手 | 抽屉/右滑面板手指连续移动即连续位移，松手才落位；甩出有 0.15 回弹；拉过头橡皮筋 ≤48px |
| C7 | 圆角 | 六档用途不串（composer 永远 28、设置卡永远 14）；连续曲率非普通圆角矩形 |
| C8 | 触感 | 单一入口、五级语义、危险操作长按 1500ms、关闭后零反馈、系统不支持时静默降级 |
| C9 | 状态双编码 | 没有“只靠颜色”表达的状态；顶栏状态行有 screen reader 专用文案 |
| C10 | 自适应 | 840dp 断点：≥840 列表常驻+详情旁列；<840 抽屉拉开；横屏/平板阅读列 720 居中 |
| C11 | 顶渐隐 | 内容滚到栏下才出现渐隐，静止页不出现；切 tab/页背景不闪 |
| C12 | 危险操作摩擦 | 解除限制/放行付款必须长按 1.5s，不能一键通过；确认卡 30s 不选按拒绝 |
| C13 | 隐私动作 | 读屏测试不发给模型；操作记录不含屏幕内容；密码/验证码填写只记 •••• |
| C14 | 字体 | 只 400/600 两档(大号 300)；等宽只走统一出口且关 liga/calt；中文系统语言下取中文字形 |

---

## 7. 关键页面 spec 速查（动工前读对应行）

- **B4 顶栏**：高 56；无整条色带，只有浮动玻璃件共用一个 BackdropGroup；左 ≡ 圆钮 44×44；中标题胶囊(高 44，标题+状态行，点按展开会话详情)；右模型胶囊 `✦ <模型> · 高 ⌄`(44) + ＋ 圆钮(与 ≡ 镜像)；有进行中自动化任务时标题胶囊尾端同心时钟键；内容滚到栏下才出页面色渐隐。
- **B5 输入条**：chrome 玻璃 + 圆角 28；单行高 56，maxLines 6 自动增高；两颗 40 圆键(＋附件[淡底]/ 发送[主色实心]或停止)贴边同心，多行贴底；语音四态(空草稿=麦克风 / 听写中=取消+停止+音量条 / 四键同位置同尺寸 / 上滑进 Listening 卡)；tray 在上方(引文/待发附件，expand_reveal)；已连接才能发。
- **B8 设置 hub**：顶搜索胶囊；`SettingsOverview`；六组按“人做一件事”组织(AI/能力/外观/通用/主机/帮助与关于)，每组一个色相槽组内行共享；行组件 `SettingsTile`(icon+tint+spot+右 value) / `SettingsSwitchTile` / `SettingsSegmentTile`，插件可注入行；右 value=状态 chip/数字(tabular)/开关态文案/更新徽标；底部居中 BrandMark 18(textDisabled 色)；**hub 上不放任何会放宽保护或删数据的东西**。
- **B2 主壳**：聊天页在下；会话抽屉从左、QuickPanel 从右，互斥；单一 AnimationController 跟手；落位 emphasized(340)+glide，推回 standard(240)+glide，甩出 flingSpring(500,0.15)；拉开先 unfocus；PopScope 只在 0↔>0 边沿重建；断点 840。
- **B3 聊天**：状态行(点+人话，不走秒)；inTurn 8 / betweenTurns 24；readingWidth 720 居中；空状态几条可点建议(点击直接填入/发送)，没有会话时第一次发送才建会话(create→select→send)；生成中可插话，排队消息可“立即发送”，过程自动折叠成一行。
- **B10 手机操控**：三档确认(询问[默认]/自主；确认卡 30s 不选按拒绝；无人回答按不同意)；应用名单三档(支付/银行/券商=不碰[硬]，微信=每步确认[软]，其余照常)；解除限制三项默认全关、长按 1.5s 生效、任务中不能开；悬浮条三态；读屏测试不发模型；操作记录不含屏幕内容；号码打码只留末 4。

---

## 8. 已知风险（执行中正面处理）

1. **玻璃模糊从未真机验证**：整套玻璃 11-10 重建，设备截图是 11-09 旧的，FLUTTER_MIGRATION.md 承认设备未授权。widget 测试无头跑，`BackdropFilter` 渲染个寂寞，测不出真机效果。**每个 Phase 3 的屏必须真机截图确认**；若 RenderThread 崩（当初回避模糊的原因），走“减少透明效果”/`fallbackFill` 降级，别硬刚。
2. **`FLUTTER_MIGRATION.md` 第 37 行过期**：还写“模糊被有意回避”，与现行代码矛盾；改 UI 时顺手更正，别被它误导。
3. **Kotlin 基线带 46(:core)+60(:app) 个红测**（沙箱/文件类，改动前就红）：Flutter 侧不受影响，但别把“全绿”预期套到 `:core`/`:app`。
4. **上游素材红线**：任何 `res/` 图标、字体文件、动图、Deepy/鲸娘一律不复制；品牌标识用 Maeve 自绘 `MaeveMark`。

---

## 9. 完成定义（Definition of Done）

整任务完成 = 以下**全部**满足：
1. Phase 1/2/3 全部勾完；每屏过第 6 节对应验收项 + 真机截图留档。
2. `bash flutter/tool/verify.sh` 全绿（format / analyze / test）。
3. 对比度单测覆盖六套配色 × 浅/深。
4. 三张“一眼定生死”的面(B4/B5/B8)在真机上与 dsh-go 观感一致（用户确认）。
5. 提交前上游扫描干净（第 1 节脚本 OK）。
6. Phase 4：Compose 页面在 Flutter 功能对等 + 真机稳定后逐屏退役（第 10 节），每退役一屏删对应 Compose 代码并回归。

---

## 10. 退役 Compose（用户已定的终局，Phase 4 执行）

- **用户明确要求：最终只保留 Flutter 自绘页，Kotlin/Compose 原始页面不保留。** 去留/时序方案由用户另出，agent 在被告知方案后执行；在方案下达前**不要删任何 Compose 代码**。
- 退役顺序对应 FLUTTER_MIGRATION.md 第 5 步：一屏只有在 Flutter 侧有真实行为 + 回归覆盖 + 真机稳定 + 功能对等四项都过，才删对应 Compose 屏。
- 每退役一屏：删 Compose 屏 → 跑 `:app`/`:core` 回归 → 更新 `docs/derived-files.tsv` 台账 → 更新 FLUTTER_MIGRATION.md 的迁移清单。

---

## 11. 总执行清单（agent 开工后在此打勾，未勾完不停）

```text
红线与环境
[x] 默读第 1 节红线；如需对照上游，先搭 _ref 笼子 + exclude + 扫描脚本
    （未取用上游副本；tools/check_no_upstream_copy.py 已建并跑过，OK）
[x] bash flutter/tool/verify.sh 能跑通（基线全绿 → 现为 format/analyze/test 全绿，135 项）

Phase 1 theme
[x] 圆角六档 6/10/14/20/28 + 派生形状；chrome/sheet→28、card→14
[x] 六套配色(Maeve 命名) + 中性色 hue/chroma + 光晕 + scrim
[x] 动效系统(时长/曲线/弹簧) + AppMotion + reduced
[x] JetBrains Mono + 统一等宽出口(关 liga/calt) + supportedLocales zh_CN
[x] test/theme/palette_contrast_test.dart 全绿（87 项）+ test/theme/tokens_test.dart（12 项）

Phase 2 组件
[x] Pressable  [x] HoldToConfirm  [x] AppPage.list
[x] SettingsSection/Tile/SwitchTile/SegmentTile/Overview/StatusChip/SearchCapsule/FeatureTile/ListPageRow
[x] AppSheet/SheetTitleBar  [x] SquircleBorder  [x] BackdropGroup 固化
[x] EmptyView/StatusDot/HeaderStatus/BriefNotice/ActivityStrip/ThinkingPill/PermissionPill/AutoApprovePill/SmoothSize/ExpandReveal
    （test/ui/components_test.dart 13 项守住按压/长按/触感/顶渐隐/组色相/双编码）

Phase 3 逐屏（每屏：实现 → 第 6 节验收 → 真机截图 → 打勾）
[x] B4 顶栏（真机确认：浮动胶囊、无整条色带、玻璃模糊可见）
[x] B5 输入条(28 圆角 + 浮在阅读列 + 两圆键)（实现+测试完成，真机截图待补）
[x] B8 设置 hub(六组卡)（实现+测试完成，真机截图待补）
[x] B2 主壳(双抽屉互斥 + 跟手 + 840 断点)
[x] B3 聊天页  [x] B6 会话抽屉  [x] B7 QuickPanel
[x] B1 onboarding  [x] B9 设置子页  [x] B10 手机操控  [x] B11 其余(壳)（真实 WebView/桌宠/插件原生界面待桥接）

收尾
[ ] 第 9 节完成定义全部满足（差：B4/B5/B8 真机截图与观感确认，明天做；桥接类页面另计）
[x] 上游扫描干净；(Phase 4)按用户方案退役 Compose —— 未删任何 Compose 代码，等用户方案
```

---

## 11.5 明日真机验收（设备恢复后按此逐条走）

设备当前 `adb offline`(Windows 侧 ADB 接口正常,是手机端 adbd 没应答)。恢复后：

```bash
# 1) 确认在线
adb devices -l                     # 需要出现 "device" 而不是 "offline"

# 2) 装最新包(debug,可热重载)
cd flutter && flutter run -d <serial>

# 3) 逐屏看这 8 处,截图存 flutter/tool/screenshots/
#    b4 顶栏:浮起胶囊、无整条色带、玻璃有模糊
#    b5 输入条:圆角 28、单行 56、两枚 40 圆键、离底 12
#    b8 设置 hub:搜索胶囊 + 一览卡 + 六组 + 底部 BrandMark
#    b9 外观:六套配色可切、预览卡跟着变
#    b10 手机操控:总开关要按住 1.5s、解除限制三项默认关
#    b6 抽屉:从左跟手拉出、置顶/文件夹/按天分组
#    b7 快捷面板:右边缘拉出、与抽屉互斥
#    b1 引导:关于 → 重新查看首次引导

# 4) 降级路径:设置 → 动效与性能 → 减少透明效果 → 全屏不应再有模糊
# 5) 840 断点:横屏/平板宽度下列表常驻,≡ 变成折叠
```

验收判定用第 6 节 C1–C14;通过后把本文件 §5/§11 的「真机截图待补」改成已留档。

## 12. 附：Flutter 玻璃实现 API 对照（Part E1）

> `MaeveGlassSurface`（`widgets/glass_surface.dart`）**已完整实现下表全部机制**——Phase 2/3 直接复用，不要重造。此处留档供扩展/排查对照。

| 目的 | API | 备注 |
|---|---|---|
| 连续曲率圆角 | `RoundedSuperellipseBorder(borderRadius:)` | 裁切 `ClipRSuperellipse`；圆形 `ClipOval`；胶囊 `ClipRRect(BorderRadius.all(Radius.circular(999)))` |
| 有界模糊 | `ImageFilterConfig.blur(sigmaX:σ, sigmaY:σ, bounded:true)` | `bounded:true` 采样不越出形状边界，边缘不被外面颜色“渗”入（iOS 同款） |
| 饱和度增益 | `ImageFilterConfig.compose(outer: ImageFilterConfig(ColorFilter.matrix(_saturate(s))), inner: blur)` | 保亮度饱和度矩阵，Rec.709 权重 r=0.2126 / g=0.7152 / b=0.0722；`s==1` 直接返回 blur 不套矩阵 |
| 共享一次采样 | 外层 `BackdropGroup` 包裹，子件 `BackdropFilter.grouped(filterConfig:, enabled:, child:)` | 一组只采一次背景；不共享的用普通 `BackdropFilter` |
| 停稳再采样 | `enabled: settled != 0` | `settled==0`（在动）时不采样、不建模糊 |
| 降级 | `reduce = !blurEnabled \|\| maybeHighContrastOf(context)`；`useBlur = !reduce && (blur ?? tier.blurs)` | `useBlur==false` 不创建 BackdropFilter，fill 取 `fallbackFill` |
| 外阴影（关键） | `CustomPaint`：`Path()..fillType=PathFillType.evenOdd..addRect(rect.inflate(extent+2))..addPath(shape.getOuterPath(rect, textDirection:))`，`canvas.clipPath(outside)` 后逐笔画 `BoxShadow` | `extent = max(blurRadius*2 + \|spreadRadius\| + offset.distance)`；普通 BoxShadow 会透过半透明玻璃又被模糊一次，中间发灰 |
| 填充插值 | `useBlur` 时 `fill = Color.lerp(fallbackFill, fill, settled ?? 1)` | 停稳过程从“磨砂纸”渐变回玻璃，模糊出现时不跳 |

**Widget 树顺序（从外到内）：**

```
[CustomPaint 外阴影]            ← evenOdd 裁剪，只画形状外
 └ Clip(形状)                   ← ClipRSuperellipse / ClipOval / ClipRRect(999)
    └ BackdropFilter(.grouped)  ← 仅 useBlur 时；enabled = settled != 0
       └ DecoratedBox(ShapeDecoration(shape, fill))
          └ child
```

**容易漏的默认值：**
- 层级本身不模糊（card/pill）但被 `blur: true` 强制开模糊时，sigma 兜底取 **14**（不是 18/24）。
- 同屏真模糊 ≤3；顶栏左圆钮 + 标题胶囊 + 右圆钮**互不重叠**才共用一个 `BackdropGroup`，重叠时必须分开。
- 裁切与形状必须是同一个 `RoundedSuperellipseBorder` 实例来源，否则模糊边界与填充边界差 1px。
- 饱和度矩阵（保亮度，Rec.709）已在 `glass_surface.dart` 的 `_saturate` 实现，勿改权重。

---

## 附：快速参考（本规格已完全自包含，无需仓库外文件）

- 验证：`bash flutter/tool/verify.sh`（仓库根）。
- 真机：`cd flutter && flutter run`。
- 设计数值（圆角/间距/动效/四层玻璃/六套配色色值/OKLCH/字体/触感/API 对照）**已全部内嵌于本规格第 3/4/6/7/12 节**；不再依赖任何仓库外文件。
- Kotlin 行为语义（B10 对齐用）：`core/src/main/kotlin/com/daiyimo/maeve/core/risk/AppTier.kt`、`.../SensitiveText.kt`、`.../gate/SelfAppGuard.kt`、`.../action/ConfirmDecision.kt`。
- 迁移方向/构建顺序：`FLUTTER_MIGRATION.md`（注意其第 37 行关于模糊的表述已过期，以本规格与现行代码为准）。
