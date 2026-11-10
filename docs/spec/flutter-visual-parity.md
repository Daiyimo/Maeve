# Flutter visual parity — Maeve 自己的设计语言

> 这份文档是 `flutter/` 视觉层的**决策记录**：每个 token 一条 rationale，说明它从哪个源头推导而来、
> 为什么是这个值、以及和 Compose 参考实现怎么对上。
>
> 适用规格：[`REWRITE_SPEC.md`](../../flutter/REWRITE_SPEC.md)。上一轮复刻规格
> [`UI_SPEC_HANDOFF.md`](../../flutter/UI_SPEC_HANDOFF.md) 已作废，只作历史记录。
>
> 状态：Phase 0 完成（源摘要 + 命名表 + 硬护栏），Phase 1 起逐项补数值与理由。

---

## 1. 四个源头读到了什么

### 1.1 设计契约 `docs/spec/visual-tokens.md`

- **不许** `ColorScheme.fromSeed` / `dynamicColorScheme`，scheme 手写。
- 色彩走 OKLCH：色相转动时**明度不变**；最大彩度 `0.13`；明度带 `0.30–0.60`（深色 `+0.234`）；
  越界时**只压彩度**，绝不改明度。
- 对比度守卫：非文本 ≥ `3:1`、文本 ≥ `4.5:1`；用色比例 70 中性 / 25 文字 / 5 强调；
  `surfaceTint = transparent`、`elevation = 0`（深度靠发丝线与外阴影）。
- 六套预设（id 固定）：`reverie`（默认）、`dolores`、`maze`、`pearl`、`sublime`、`graphite`。
  `Preset | Custom | Wallpaper` 三条来源走**同一个** `derive()`，没有单独代码路径。
- 形状/间距/动效的档位表；`readingWidth 720`；`hairline 0.5px`。
- 图标母题：两个偏移的环 + 一个偏心光点（外环=主屏，内环=隐藏显示，点=正在跑的那一步）。

**契约与 Compose 参考实现的差异**（记录，不装作没看见）：契约 §2 的 seed 值与 §3 的表面 alpha
是 2026-11-09 那一版；Compose 侧在 11-09/11-10 重做过一轮（`MaeveColors.kt` 的类注释写明
"上一版暖纸底 + 三条语义轨 → 现在中性偏冷 + 单一彩色"），seed 改成了梅子/黄铜/沙原/珍珠/夜靛/石墨，
表面 alpha 也从 0.70/0.82/0.62/0.55 改成 0.62/0.74/0.58/0.62。**本重写以 Compose 实现为准**
（理由：两套 UI 必须看起来是同一个产品，而 Compose 是当前在服务的那一套；契约的结构性不变量
——OKLCH 推导、明度带、彩度上限、对比度守卫、六套预设 id、形状/动效档位——一条不落）。

### 1.2 Compose 参考实现 `app/.../ui/theme/`

| 文件 | 读到的决策 |
|---|---|
| `MaeveColors.kt` | **五级表面阶梯**（surfaceLowest → bg → surface → surfaceHigh → surfaceHighest，相邻只差 4–6 明度点）+ **四级文字**（ink/ink2/ink3/ink4）+ **唯一彩色**（accent 一族）。中性色相**固定 250**（偏冷），不随强调色转——"配色一换整张纸的色相跟着跑"是上一版的教训。状态不靠色相，靠图形/字重/表面层级。全站零投影（除浮件）。 |
| `MaeveAccent.kt` | 六套预设（Westworld 意象）+ `deriveAccent` 的 OKLCH 阶梯（浅：seed L0.53 / pressed −0.06 / container 0.93×0.26 / bubble 0.918×0.29 / glow 0.819×0.68；深：seed 0.764×0.76 / onSeed 0.231×0.39 / container 0.361×0.51 / bubble 0.314×0.29 / glow 0.52×0.72）+ `guardSeed` 明度二分（最多 12 步）。 |
| `MaeveSurfaces.kt` | 四档玻璃：chrome σ24 / sheet σ30 / card、pill 不取样。**card/pill 不模糊的理由**：它们长在被录制的滚动内容里，取样会变成"玻璃套玻璃"的无限反射。玻璃五层构造：模糊 → 半透明填充 → 顶边内高光 → 内描边 → 外阴影。背景 = 中性竖渐变 + **三团极光**（给玻璃折射提供原料）。降级：`fallbackFill` 接近不透明，不建模糊层。 |
| `MaeveShapes.kt` | 连续曲率（超椭圆 n=4.2）；半径 6/10/14/20/28；卡片/按钮/图块都走 squircle，气泡用**带尾角的圆角**（用户右下 xs、回答左下 xs）。 |
| `MaeveType.kt` | 两套字三种声部：等宽说机器话、无衬线说人话；负字距是大字"精密感"的来源；字重只用 400/500/600；`Script` 行高 1.58 给回答；等宽读数 tabular；**微标签不再宽字距大写**（"到处都是重点=没有重点"）。 |
| `MaeveMotion.kt` | 90/160/240/280/340/380/120/2400 + page 220 / switch 200；`pressScale 0.97`；glide 曲线 = `Cubic(0.32,0.72,0,1)`；`reduceMotion` 一律 snap。 |
| `MaeveMotifs.kt` | 四个母题：**迷宫环**（螺线，替代所有转圈）、**钢琴卷**（替代"正在思考…"）、**台词轨**（第几轮 + 幕号印章）、**校准角**（仪表刻度）。 |
| `MaeveSemantic.kt` | 状态色与强调色无关：ok 150 / warn 70 / bad 25 / info 245，低饱和；工具类别 8 个色相；身份色 8 槽由 FNV-1a 稳定映射。 |

界面骨架（从 `ChatScreen.kt` / `PageParts.kt` / `Rows.kt` / `Controls.kt` 读到的配方）：

- **顶栏**：整条玻璃（chrome 档）压在滚动内容上，高 56（带品牌标 74），标题**相对整条顶栏居中**，
  上方可有一行"点 + 等宽小字"的眉标，下方一行状态副标题；左右各一个 40dp 圆钮。
- **输入条**：**圆角 16 的纸条**（原文："胶囊读作聊天软件，纸条读作便签"），全宽减 12 边距，
  高 52，左 ＋ 40 圆钮、右发送 40 圆钮（强调色实心；不可发时强调色 12% 底），底部一段
  从页面底色到透明的渐变垫在下面。
- **消息**：用户气泡 = **高一级表面 + 一道描边**（"整站只有一个彩色，不留给自己的每一句话"），
  右对齐、最大 80% 宽、14×10 内边距、尾角 xs；回答 = **左侧一条 1dp 竖线**（剧本边注）+ 全宽正文，
  没有填充卡片；每一轮前面有 26dp 台词轨（两位数字 + 3dp 方块 + 竖线）。
- **设置行**：最小高 52、左右 16、图标块 30（圆角 7，中性底！只有状态才上色）、文字列距图标 14、
  上发丝线从 60 起画；右栏值是 `RowValue`（无中文自动等宽 13/18、`ink3`、单行、× 与箭头 8dp）。
- **分组标题**：12.5 `Label` + `ink3`，上 22 / 下 8；组下注释 12 `Hint` + `ink3`。

### 1.3 品牌锚 `flutter/lib/widgets/maeve_mark.dart`

环 + 实心核 + 镂空中心；armed 态环开口成弧 + 开口中央一道刻度。**保留不动**（它是 Maeve 自绘的）。
它给出三条母题约束，整套 Flutter 视觉必须与它同源：

1. **环**是基本形：所有"进度/状态"优先用环或螺线，不用方块进度条。
2. **开口**表示"可用/在跑"：状态标记用"环开口 + 刻度"而不是换颜色。
3. **实心核 + 镂空**：层级靠"挖掉"而不是靠加深，和"发丝线代替阴影"是同一个思路。

### 1.4 品牌叙事 `README.md` / `SPEC.md`

- 一句话：**"She runs the loop. You keep your screen."** —— 她在跑循环，你保住你的屏幕。
- 产品性格：安静、克制、宿主感（host）。任务在**看不见的**第二块屏上跑，主屏不被接管。
- 命名意象来自 Westworld：迷宫、循环、剧本、钢琴卷（player piano）、甜水镇。
- 这些直接决定了：界面不能喧哗（唯一彩色 / 零投影 / 低饱和状态色）、要有"仪表感"
  （等宽读数 / 校准角 / 台词轨）、进度要有方向（迷宫螺线而不是转圈）。

---

## 2. Flutter 侧 token 命名表（Maeve 自己的命名）

命名原则：**与 Compose 侧同名同义**，让"同一个语言的第二个实现"可对照；不复用上一轮复刻的
命名（`reverie/tide/dusk/pine/amber/graphite` 那套六色 + `MaeveTokens` 那套扁平常量全部作废）。

| 层 | Flutter | Compose 对应 | 职责 |
|---|---|---|---|
| 色彩科学 | `MaeveOklch` / `MaeveContrast` | `Oklch` / `contrastRatio` | OKLCH↔sRGB、对比度计算（单测共用） |
| 强调色 | `MaeveAccentPreset` / `MaeveAccent` / `deriveAccent` / `guardSeed` | `AccentPreset` / `AccentSpec` / `deriveAccent` / `guardSeed` | 六套预设 + 同一套推导 + 对比度守卫 |
| 颜色 | `MaeveColors`（`bg`/`surface*`/`ink*`/`accent*`/`hair`/`rule`/`edge`/`glass*`/`scrim`/`glow*`） | `MaeveColors` | 五级表面 + 四级文字 + 唯一彩色 + 玻璃填充 |
| 语义 | `MaeveSemantic`（ok/warn/bad/info + 工具/身份色） | `MaeveSemantic` | 与强调色无关的状态与类别色 |
| 表面 | `MaeveGlassTier` / `MaeveGlassStyle` / `MaeveSurfaces` / `MaeveBackdrop` | `GlassTier` / `GlassStyle` / `MaeveSurfaces` / `MaeveBackdrop` | 四档玻璃 + 极光背景 |
| 形状 | `MaeveRadius` / `MaeveShapes` | `MaeveRadius` / `MaeveShapes` | 连续曲率、气泡尾角、输入条 16 |
| 字阶 | `MaeveType` | `MaeveType` | 两套字三种声部 |
| 间距/尺寸 | `MaeveSpace` / `MaeveSize` | 同名 | 4 的倍数阶梯 + 控件尺寸 |
| 动效 | `MaeveMotion` / `AppMotion` | `MaeveMotion` | 分档 + 曲线 + 减少动效归零 |
| 母题 | `MazeRing` / `PianoRoll` / `CalibrationCorners` / `ScriptRail` | 同名 | 四个自绘母题 |
| 主题 | `maeveTheme()` / `MaeveThemeData` | `MaeveTheme` | 手写 scheme + 扩展装配 |

---

## 3. 硬护栏（不随推导改）

1. **功能契约**（REWRITE_SPEC §3）一字不改：桥接通道、AAR 流程、入口、全部屏幕行为、原型限制。
2. **对比度**：文本 ≥4.5、非文本 ≥3、容器上 on-色 ≥4.5（契约更严处按契约）；每个预设 × 明暗
   都要有**计算**出来的单测。
3. **换预设对比度不变**：中性色只动色相/彩度，明度阶梯固定。
4. **减少动效**：所有时长归 0，循环动画停静止帧。
5. **降级**：关模糊 / 高对比度时**不建**模糊层，用近不透明填充，文字仍可读。
6. **触控**：可点目标 ≥44dp（Compose 用 40+padding，Flutter 侧取 44 的可点域）。
7. **窄屏可达**：快捷面板必须有**可见入口**（A7），不能只靠被系统占用的边缘手势。
8. **右栏规则**：value/chip/开关到箭头/边缘间距全站一致，长值省略号（A9）。
9. **零投影**（除浮件）、`surfaceTint=transparent`、`elevation=0`、无 `ColorScheme.fromSeed`。
10. 上游红线：不复制 dsh-go 任何源码/资源/字体/命名；提交前扫描必须 OK。

---

## 4. Token 逐条对照与理由

> 数值与 Compose 侧逐项一致（除 §4.7 明确记录的三处偏差）。每行左边是 Flutter 实现，
> 右边是它为什么是这个值。

### 4.1 颜色：五级表面 + 四级文字

| Flutter | 值（浅 / 深） | 理由 |
|---|---|---|
| `MaeveColors.surfaceLowest` | 白 α.30 / 白 α.028 | 阶梯最低一级：比画布更"薄"，只给分段控件底、凹陷区 |
| `MaeveColors.bg` | OKLCH(0.973, .0016) / (0.145, .0022)，色相固定 250 | 画布是中性偏冷灰，不随配色转 —— 转一次整张纸就"脏"一次 |
| `MaeveColors.surface` | 白 α.58 / 白 α.062 | 抬一级：卡片、行的底。0.58 是"看得出薄片、又没退回白纸"的边界 |
| `surfaceHigh` / `surfaceHighest` | 白 α.68 / .74；深 α.088 / .115 | 用户气泡、抬起态、弹层底：只差 4–6 个明度点，层级靠发丝线读 |
| `MaeveColors.ink` | OKLCH(0.208) / (0.962) | 正文；明度是四级文字的锚，其余按阶梯推 |
| `ink2` | 0.452 / 0.740，`guard(…, 4.5)` | 次级正文；不够 4.5 就**只降明度**补到够 |
| `ink3` | 0.585 / 0.580，`guard(…, 3.0)` | 说明、图标、未选中态；作为文字使用时仍配 `caption` 及以上字号 |
| `ink4` | 0.700 / 0.470，`guard(…, 2.2)` | 只有"极弱提示/占位"用它，不承担正文 |
| `MaeveColors.accent` | 预设种子（见 4.3） | 全站唯一彩色：品牌标、焦点、每屏一个主按钮 |
| `hair` / `rule` / `edge` | ink α.07 / .135 / .08 | 三道线：分隔、台词轨、卡片描边。零投影，层级全靠这三道 |
| `glass` / `glassSheet` / `glassCard` / `glassPill` | 见 4.5 | 四档玻璃的填充白度 |
| `glowPrimary/Secondary/Tertiary` | 强调色 α.26/.30、邻近色 α.18/.22、固定冷色 α.11/.20 | 三团极光：玻璃要有东西可透，纯色背景模糊完还是纯色 |

### 4.2 语义色

固定色相（成功 150 / 警告 70 / 危险 25 / 信息 245），明度带 0.45（浅）/ 0.78（深），
容器 0.92/0.30。状态**不靠色相**表达——靠图形（勾、缺口、方块）与字重；颜色只是辅助。

### 4.3 强调色：六套预设

| 预设 | 浅种子 | 深种子 |
|---|---|---|
| 梅子 `reverie`（默认） | `#6B3D57` | `#B98CA8` |
| 黄铜 `dolores` | `#7A5626` | `#D2AE74` |
| 沙原 `maze` | `#6E5740` | `#C6AE90` |
| 珍珠 `pearl` | `#4E5F6B` | `#AFC2CE` |
| 夜靛 `sublime` | `#3B4A72` | `#9CAEDD` |
| 石墨 `graphite` | `#34312E` | `#DED8D0` |

阶梯（`deriveAccent`，与 `MaeveAccent.kt` 同一组倍数）：浅 seed L0.53 → pressed −0.06 →
container 0.93×0.26 → bubble 0.918×0.29 → glow 0.819×0.68；深 seed 0.764×0.76 → pressed −0.06 →
onSeed 0.231×0.39 → container 0.361×0.51 → bubble 0.314×0.29 → glow 0.52×0.72；haze = 色相 +24°。
`guardSeed` 只挪明度（最多 12 步），保证种子上的字 ≥4.5。

**为什么不是契约 §2 的那组 seed**：契约文件的状态行停在 2026-11-09 的纸面版；Compose 侧在
11-09/11-10 把配色重做成"中性画布 + 单一彩色"，seed 也换成梅子/黄铜/沙原/珍珠/夜靛/石墨
（`MaeveAccent.kt` 的类注释记了这次转向）。两套 UI 必须看起来是同一个产品，所以以**在服务的那套**为准。

### 4.4 形状

| Flutter | 值 | 理由 |
|---|---|---|
| `MaeveRadius.xs…xl` | 6 / 10 / 14 / 20 / 28 | 六档连续曲率：角标 / 图块 / 按钮 / 卡片 / 弹层 |
| `MaeveRadius.composer` | 16 | **纸条，不是胶囊**："胶囊读作聊天软件，纸条读作便签"（Compose `ComposerShape`） |
| `MaeveShapes.userBubble` | 20/20/6/20 | 右下角尾角指向说话的人 |
| `MaeveShapes.answerBubble` | 20/20/20/6 | 回答的尾角在左下，和用户气泡对称 |
| `MaeveShapes.pill` | Stadium | 状态胶囊、未读徽标 |
| 连续曲率 | `RoundedSuperellipseBorder` | 曲率连续；裁切与形状同源，模糊边界不差 1px |

### 4.5 表面（四档玻璃）

| 档 | 填充（浅/深） | σ | 饱和度 | 外阴影 | 用在哪 |
|---|---|---|---|---|---|
| `chrome` | 白 α.62 / `_n(.215)` α.68 | 24 | 1.40 | 浮件影 14 | 顶栏、输入条 |
| `sheet` | 白 α.74 / `_n(.245)` α.82 | 30 | 1.55 | 22 | 抽屉、弹层 |
| `card` | 白 α.58 / `_n(.195)` α.50 | — | — | 4 | 设置卡、消息气泡 |
| `pill` | 白 α.62 / `_n(.215)` α.60 | — | — | — | 小胶囊 |

card / pill 不取样：它们长在滚动内容里，取样会"玻璃套玻璃"无限反射。发丝 0.75，
顶边高光按档位占高（chrome .42 / sheet .16 / card .34 / pill .50）。

### 4.6 字阶 / 间距 / 动效

| Flutter | 值 | 理由 |
|---|---|---|
| `display` | 30/37 w500 −0.66 | 展示字；负字距是大字"精密感"的唯一来源 |
| `largeTitle` | 24/30 w500 −0.48 | 页面级大标题 |
| `title` | 17/22 w500 −0.17 | 顶栏标题 |
| `body` | 15.5/23 w400 −0.05 | 正文 |
| `caption` / `hint` | 13/19、12/17 | 说明与页脚 |
| `script` | 15.5/24.5（1.58） | 回答正文：全 App 唯一需要"读"的文本 |
| `eyebrow` | mono 10.5/14（中文 11.5） | 机器声部；汉字 10.5 会把笔锋糊掉，所以含中文自动升号 |
| `mono` / `numeral` | mono 12/16 / 22/26，tabular | 版本、耗时、步数；数字跳动时不左右抖 |
| 字重 | 400 / 500 / 600 | AOSP 只带 Regular，w600+ 是合成加粗，中文会糊 |
| 等宽 | 系统 `monospace` | 与 Compose `FontFamily.Monospace` 同字形；**不打包字体**，包体少 0.8MB |
| `MaeveSpace` | 2/4/6/8/10/12/16/20/24/32/40；页边 18；阅读列 720 | 4 的倍数为主，6/10 给图标与行内间隙 |
| `MaeveSize` | 顶栏 56（带品牌 74）/ 圆钮 40 / 行 52 / 图块 30 / 台词轨 26 / 可点域 44 | 与 `MaeveSize.kt` 一致；44 是 A6 的下限 |
| `MaeveMotion` | 90/160/240/280/340/380/120/2400，page 220，hold 1500 | 四档语义，不跨档借用 |
| `glide` / `decelerate` | Cubic(.32,.72,0,1) / Cubic(0,0,.2,1) | 跟手后落位 / 页面推入 |
| 减少动效 | 所有时长 0，循环停静止帧，按压不缩放 | A5 |

### 4.7 与 Compose 的三处刻意偏差（都写在这里，不藏着）

1. **降级填充**：Compose 用 `surface.copy(alpha = 0.97)`，而 `surface` 本身是 6% 白 —— 深色下
   一关模糊，顶栏会变成 97% 白。Flutter 侧改成"玻璃色压在画布上取不透明"，语义仍是
   "近不透明、可读"，但不会变白。
2. **等宽字体**：Compose 用系统等宽；上一轮 Flutter 打包了 JetBrains Mono，这次删掉，回到系统等宽。
3. **顶栏渐隐**：复刻版在顶栏下方加了一层渐变遮罩，因为胶囊挡住了内容。新顶栏是**整条玻璃**，
   填充 + 发丝线已经保证可读，所以**删掉渐隐层**——这也是测试里删掉"顶渐隐"断言的原因。

## 5. 母题落地位置

| 母题 | Flutter | 用在哪 |
|---|---|---|
| 迷宫环 `MazeRing` | `design/maeve_motifs.dart` | 思考 pill 的不确定进度、`MaeveProgress`；**替代所有转圈** |
| 钢琴卷 `PianoRoll` | 同上 | 输入条上方的活动条（第几步 / 还剩几步） |
| 台词轨 `ScriptRail` | 同上 | 聊天每一轮左侧的幕号 + 印章 + 竖线 |
| 校准角 `CalibrationCorners` | 同上 | 运行页的"仪表块" |
| 细线网格 `HairlineLattice` | 同上 | 叶子面板（运行/诊断）的可选底纹 |

## 6. 验收对照（A1–A10）

| # | 证据 |
|---|---|
| A1 | 本文档 + `test/theme/tokens_test.dart`（20 项，含半径/字阶/动效/预设/表面） |
| A2 | `test/theme/palette_contrast_test.dart`（76 项，六预设 × 明暗，计算验证 + 漂移 < 1e-9） |
| A3 | 行为测试：`test/widget_test.dart` 17 项覆盖第 3 节清单；bridge / AAR / 入口未改 |
| A4 | 行为断言逐条保留；视觉断言改动见 §4.7 与各测试注释 |
| A5 | `tokens_test` 减少动效归零；`glass_surface_test` 关模糊不建 filter；`widget_test` 减少透明效果 |
| A6 | 可点域 44（`MaeveSize.touch`）；`StatusDotPainter` 形状差异测试；`HeaderStatus` 读屏文案；1500ms 长按 |
| A7 | 顶栏右侧「快捷面板」按钮（`widget_test` 窄屏用例） |
| A8 | `verify.sh` 全绿 + 扫描 OK + 无 `fromSeed` + 无新增依赖（反而删了字体资源） |
| A9 | `RowMetrics.gap = 8`，`components_test` 用几何断言 value→箭头、文案→开关都恰好 8 |
| A10 | `FLUTTER_MIGRATION.md` / `flutter/README.md` 已同步；`UI_SPEC_HANDOFF.md` 已标作废 |

## 7. 未做（范围纪律）

- **真机验证**：按 REWRITE_SPEC §9 不做；本文件与 README 都写明"未做真机验证"。
- Kotlin/Compose 退役：未删一行。
- 原型边界外的东西（模型调用、持久化、语音、系统分享、附件 picker）未接。
