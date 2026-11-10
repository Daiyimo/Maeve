# Maeve Flutter 壳重写规格（REWRITE SPEC）

> 本文件是**自包含执行规格**：交给的 agent 只需要「Maeve 仓库路径 + 本文件」即可从零上下文启动，
> 按第 6 节执行计划**全量执行到满足第 8 节验收**为止。做完一页划掉一项，未划完不停。
>
> 上游参考：dsh-go（AGPL-3.0，只读调研）。**本规格不内嵌任何上游设计数值**——风格由执行 agent
> 按第 2 节的方法论从 Maeve 项目自身推导。它与上游源码解耦：只借设计决策的推导方法，
> 按 Maeve 自己的设计系统在 Flutter 里重建，不复制任何上游代码 / 资源 / 字体 / 命名。

---

## 0. 一句话目标与终局

**把 `flutter/` 从「dsh-go 的视觉复刻」重写为「Maeve 自己的设计语言」：功能契约一字不改，视觉与交互全部按 Maeve 项目自身的设计系统重建。**

- 允许范围内"对"：与 Maeve 自己的设计契约（`docs/spec/visual-tokens.md`）和 Kotlin 侧参考实现（`app/src/main/java/com/daiyimo/maeve/ui/theme/`）**对等**——同一个产品、同一套语言，两个技术栈各自实现。
- 禁止：保留 dsh-go 的色值 / 配色命名 / token 数值 / 界面配方；复制 dsh-go 的任何源码 / 资源 / 字体 / 品牌素材。
- 终局不变：Flutter 是唯一 UI 层；Compose 页面在功能对等 + 真机稳定后逐屏退役（第 9 节，另案，不在本规格内）。退役之前，两套 UI 必须看起来是同一个产品。

---

## 1. 不可逾越的红线（每次改动前默读）

| 事实 | 后果 / 纪律 |
|---|---|
| dsh-go 仓库整体 **AGPL-3.0**；名称 / Logo / 形象不在授权内 | 复制它的 Dart/资源进 Maeve（Apache-2.0）= 创作衍生作品，与 Maeve 的 Apache-2.0 目标冲突 |
| 当前 Flutter 壳就是按它复刻的：六套配色色值与命名、圆角分档、动效数值、四层玻璃配方、逐屏骨架 | 重写 = 把这些**全部换掉**，不是加一层"自定义主题" |
| `tools/check_no_upstream_copy.py` 在提交前跑，非 0 即拦 | 扫描只认 git 跟踪文件；红线文档（SPEC.md / THIRD_PARTY_NOTICES / UI_SPEC_HANDOFF.md）在 ALLOW 里，别把上游痕迹带进新文件 |

**纪律（三重兜底，缺一不可）：**

1. 只借鉴**设计决策的推导方法**（色彩推导规则、对比度守卫、动效分档思想），按 Maeve 自己的设计系统在 Flutter 里**重写**。
2. 要对照上游找感觉时，用**笼子**：上游副本放仓库外或进 `.git/info/exclude` 的 `_ref/`，**永不提交**，保留原版权头，一行不改。
3. **提交前扫描**：`python tools/check_no_upstream_copy.py` 必须 OK。

**绝不做**：整体改 AGPL、把上游代码"整理版权"后并入、提交后再删。公开历史出现过一次即算分发过，不可撤销。

---

## 2. 风格源头与推导方法论（本规格的核心：不写死风格）

执行 agent **不得**向本规格回填任何色值 / 圆角 / 动效数值再照抄；按下面的顺序自行推导，
并把推导结果落盘。风格不是"随便设计"，是**从 Maeve 项目自身已有的设计系统长出来**。

### 2.1 按顺序读完这四个源（一个都不能跳）

1. **`docs/spec/visual-tokens.md`** —— Maeve 的设计契约（M2 design contract）。设计决策的权威：
   色彩推导规则（OKLCH、明度带、色域回退、对比度守卫）、预设体系、表面 / 形状 / 动效 token、
   图标母题、验收标准。**它说什么，Flutter 侧就实现什么。**
2. **`app/src/main/java/com/daiyimo/maeve/ui/theme/`** —— 同一设计语言在 Kotlin/Compose 侧的参考实现：
   `MaeveColors`（五级表面阶梯 + 四级文字 + 唯一强调色，零投影）、`MaeveAccent`/`MaevePalette`（预设与推导）、
   `MaeveShapes`（连续曲率）、`MaeveSurfaces`、`MaeveType`、`MaeveMotion`、`MaeveMotifs`（图标母题）、
   `MaeveOklch`、`MaeveSemantic`、`MaeveTheme`。Flutter 侧要与它**对等**：同一个语言的第二个实现，不是翻译腔。
3. **`flutter/lib/widgets/maeve_mark.dart`** —— Maeve 自绘品牌标（环 + 实心核 + 镂空；armed 态开口 + 刻度）。
   它是品牌锚点：形状母题、图标气质、动效性格从它出发。
4. **`README.md` / `SPEC.md`** 的品牌叙事 —— 产品名字与定位决定设计语言的性格（安静 / 克制 / 宿主感）。

### 2.2 推导产出（必须落盘，验收项）

- **`flutter/lib/design/` token 层重建**：颜色 / 形状 / 间距 / 字阶 / 动效 / 表面，命名用 Maeve 自己的
  （不得沿用 dsh-go 的命名与色名，例如"黛青 / 暮霞 / 松绿 / 琥珀"这类上游命名）。
- **`docs/spec/flutter-visual-parity.md`**（新建）：记录 Flutter 侧每个 token 与设计契约 / Kotlin 参考实现
  的对应关系与决策理由，**一个 token 一条 rationale**。这份文档是"风格不是随手设计的"证据。

### 2.3 明确禁止

- 沿用 dsh-go 的色值、配色命名、圆角数值、动效数值、四层玻璃配方、逐屏骨架。
- 从 dsh-go 仓库复制任何源码 / 资源 / 字体 / 命名；`git ls-files` 里出现上游痕迹即拒绝提交。
- 用 `ColorScheme.fromSeed` 或 `dynamicColorScheme` 生成主题（设计契约明确要求手写 scheme）。
- 引入新的第三方依赖来做主题 / 状态管理 / 动效（现有依赖见 `flutter/pubspec.yaml`，够用）。

---

## 3. 功能契约（重写中必须保持不变的）

视觉全换，**行为一字不改**。以下每一条都是验收项：

### 3.1 宿主与桥接（Kotlin 侧不动）

- 入口：`com.daiyimo.maeve/.ui.FlutterShellActivity`（`adb shell am start -n com.daiyimo.maeve/.ui.FlutterShellActivity`）。
- 方法通道 `maeve/ui`：`open_settings` / `status`；事件通道 `maeve/ui/events`：status 流。
  Flutter 侧 `lib/bridge/maeve_bridge.dart` 与 Kotlin 侧 `FlutterShellActivity.kt` 的契约不动。
- AAR 流程：`flutter build aar --debug` → `:app` 以 `files(...)` 消费；
  `:app:assembleDebug -PskipFlutterAar` 构建 host APK。模块结构（`flutter/` 是 module 不是 app）不变。

### 3.2 原型限制（保持，不要"顺手接真实"）

- 对话 / 记忆 / 设置只存进程内存，不落盘；对话回复是本地延时 demo；没有模型 API、流式、
  Kotlin Agent 执行、工具调用 / 审批流；附件未接 picker；系统分享待桥。
- 这些是**有意的原型边界**，重写不接真，也不要把 demo 行为改没了。

### 3.3 功能面清单（重写后必须仍在，逐条过）

- **聊天（B3）**：发送 / 停止当前轮 / 生成中插话排队（可"立即发送"）/ 长按消息复制与引用
  （引用落成输入条上方 chip 随下一条发出）/ 空状态可点建议 / 已完成轮次折叠成一行 / 思考 pill。
- **主壳（B2）**：会话抽屉从左、快捷面板从右，**互斥**；单一进度值跟手；拉过头橡皮筋；
  甩出弹簧；拉开先 unfocus；返回键只关浮层不退出；宽窄断点自适应。
- **会话抽屉（B6）**：搜索 / 工作区行 / 状态筛选 / 置顶→文件夹→按天分组 / 未读徽标 /
  归档提示与恢复 / 多选归档删除 / 底部身份条。
- **快捷面板（B7）**：右侧面板，入口图标网格，⚙ 长按进设置搜索。
- **设置（B8/B9）**：搜索胶囊 + 一览卡 + 分组卡 + 底部品牌标；子页全套（外观 / 界面 / 性能 /
  语音 / 通知 / 回复偏好 / 隐私[长按清除] / 系统权限 / 后台指南 / 诊断 / 关于 / 账号 / 引擎 /
  远程 / 插件）；hub 上不放任何放宽保护或删数据的东西。
- **手机操控（B10）**：总开关默认关 + 首次显著披露 + **按住 1.5s 才开启**；权限两行；
  读屏测试（不发模型）；悬浮条三态预览；确认方式两档 + 30s 不选按拒绝；应用名单三档
  （支付/银行/券商不碰、微信每步确认、其余照常）；解除限制三项默认全关 + 长按生效；
  最近操作只记动作不记屏幕内容。
- **首启引导（B1）**：欢迎 → 运行时 → 外观 → 权限 → 密钥 → 完成；步骤条只数要回答的问题；
  可"稍后"。
- **其余壳页（B11）**：网页版 chrome、终端（自有深色 + 等宽）、社区、更新、关于、账号、引擎、
  远程、插件、运行、记忆——都要有页且可导航。
- **触感**：单一入口、五级语义、可整体关闭、系统不支持时静默降级。

### 3.4 测试契约

- `bash flutter/tool/verify.sh` 必须全绿（`dart format` → `flutter analyze` → `flutter test`）。
- **行为断言必须保留**（发送出现气泡与回复、抽屉互斥、遮罩关闭、宽屏折叠、长按不误开、
  搜索过滤、减少透明不再建 BackdropFilter 等）。**视觉断言随新设计更新**，但每个改动的断言
  要写理由；不允许把测试改绿了事。

---

## 4. 现状盘点（动工前必读，避免重做已完成部分）

### 4.1 文件地图（`flutter/lib/`）

| 目录 | 内容 | 重写处置 |
|---|---|---|
| `design/` | token 层（dsh-go 复刻：六配色 / 四层玻璃 / OKLCH 中性色 / 动效 token） | **整体重建**（第 2 节） |
| `widgets/` | `glass_surface.dart`（复刻玻璃入口）、`maeve_mark.dart`（**Maeve 自绘品牌标，保留**） | 玻璃入口随 token 重建；品牌标不动 |
| `ui/common/` | Pressable / HoldToConfirm / AppPage / AppSheet / status 组件 / haptics | 按新 token 重建，行为不变 |
| `ui/settings/` `ui/phone/` `ui/memory/` `ui/runs/` `ui/more/` | 各屏幕（复刻骨架） | 逐屏重绘 |
| `app/` | MaeveApp / MaeveShell / ChatPage / SessionsDrawer / QuickPanel / Onboarding / controller | 逐屏重绘，controller 行为不变 |
| `models/` `bridge/` | 数据模型与桥接契约 | **不动** |
| `main.dart` | 入口（当前 923 行的旧壳已裁到很薄） | 随壳重建 |

### 4.2 已验证可用的部分（真机 2026-11-10，Android 17，1080×2400@450dpi）

- 安装 / 启动 / 导航 / 全部屏幕可走通；玻璃模糊真机渲染稳定（无 RenderThread / GPU 错误）。
- 发送 → 思考 → 回复、抽屉、设置 hub、外观换色（整树生效）、手机操控披露 + 长按 1.5s 开启、
  减少透明效果降级（不透明填充）、宽屏（≥840dp）常驻侧栏 + ≡ 折叠、浅色主题——全部符合预期。
- 截图留档：`flutter/tool/screenshots/device-2026-11-10/`。

### 4.3 已知缺陷（重写必须消除，不要继承）

1. **快捷面板窄屏不可达（功能缺陷）**：唯一入口是模型胶囊长按，而模型胶囊只在宽度 ≥520dp 时渲染；
   右边缘手势区（最外 28px）在真机系统手势导航下被系统占用，应用收不到事件。411dp 手机上
   快捷面板打不开。**新设计必须给窄屏一个可达入口**（可见按钮 / 顶栏入口均可，自己定）。
2. **隐私页长按清除的并发修改崩溃（代码缺陷，本规格提交时已修）**：`for (final c in list) delete(c.id)`
   边遍历边删抛 `ConcurrentModificationError`，只删掉一条就中断、提示不出现。修复方式：
   遍历 `list.toList()` 拷贝。重写时警惕同类模式（边遍历边改集合）。
3. **设置行右侧间距不一致（视觉缺陷，本规格提交时已修）**：value/chip 与箭头之间只有 2px，
   开关行用 8px；已统一为 8px 并给长 value 加省略号。重写时右栏间距要有一套明确规则。
4. **`flutter/UI_SPEC_HANDOFF.md` 是上一轮复刻规格，已被本规格取代**：保留作历史记录，
   **不要照它施工**；其中的色值 / token 数值 / 屏幕骨架全部作废。

---

## 5. 环境与验证（每个 Phase 结束必跑）

- 模块：`flutter/`（Flutter module；`:app` 消费它构建出的 debug AAR）。
- **验证命令（仓库根目录）**：
  ```bash
  bash flutter/tool/verify.sh        # dart format → flutter analyze → flutter test
  python tools/check_no_upstream_copy.py   # 上游痕迹扫描，非 0 即拦
  ```
- `verify.sh` 自动按 `MAEVE_ENV_FILE` → `../_env/env.sh` → `PATH` 的顺序找工具链，并处理好
  三个环境坑（JDK 17、loopback 不走代理、Git Bash POSIX PATH）。format 失败 = 没格式化，不是坏。
- 通过标准：`dart format` 干净、`flutter analyze` **No issues found**、`flutter test` **全绿**、
  上游扫描 **OK**。
- 需要构建 host APK 时（可选）：`flutter build aar --debug`，再
  `./gradlew :app:assembleDebug -PskipFlutterAar`。**真机验证不在本规格范围内**（见第 9 节）。

---

## 6. 执行计划（按序全量执行，做完一页划掉，未划完不停）

> 原则：**先读源与定契约 → 再 token 层 → 再组件 → 再逐屏**。每完成一屏跑第 5 节验证 +
> 第 8 节对应验收项。token 层没定稿之前不动屏幕。

### Phase 0 · 调研与设计理由（落盘，不写代码）

- [x] 按 2.1 顺序读完四个风格源头，写读书笔记进 `docs/spec/flutter-visual-parity.md`：
      设计契约怎么要求、Kotlin 侧怎么实现、品牌标给出什么母题、产品叙事是什么性格。
- [x] 定 Flutter 侧 token 命名表（Maeve 自己的命名，列出每个 token 的名字与职责，先不填数值）。
- [x] 明确"哪些是设计决策（可随推导调整）、哪些是硬护栏（第 8 节，不可动）"。
- **完成判据**：`docs/spec/flutter-visual-parity.md` 有骨架 + 命名表 + 源头摘要。

### Phase 1 · token 层（重建 `flutter/lib/design/`）

- [x] 按设计契约 + Kotlin 参考实现重建：颜色（预设体系 + 中性色规则 + 表面阶梯）、
      形状（连续曲率分档）、间距、字阶（字重档位）、动效（分档 + 曲线 + 弹簧 + 减少动效归零）、
      表面（填充 / 模糊 / 发丝 / 外阴影 / 降级填充）。
      （`maeve_oklch / maeve_accent / maeve_colors / maeve_semantic / maeve_surfaces /
      maeve_shapes / maeve_type / maeve_motion / maeve_motifs / maeve_theme`）
- [x] 中性色规则落地：换预设时各级文字对比度不变（明度不动，只动色相 / 彩度）。
      （中性色相固定 250、彩度固定；单测断言六套之间的漂移 < 1e-9）
- [x] 等宽字体出口唯一（版本号 / 统计 / 路径 / 代码走它，关连字，tabular 数字）；
      不打包中文字体，跟随系统；`supportedLocales` 声明 zh_CN 优先。
      （`MaeveType.mono`，系统 `monospace`——与 Compose 同字形，删掉了上一轮打包的 JetBrains Mono）
- [x] 对比度单测：每个预设 × 明暗，**计算对比度不靠眼**（文本 ≥4.5:1、非文本 ≥3:1、
      契约里更严的档位按契约来），并覆盖"换预设对比度不变"的漂移上限。
      （`test/theme/palette_contrast_test.dart` 76 项 + `tokens_test.dart` 20 项）
- **完成判据**：第 8 节 A1/A2 通过；`design/` 数值与契约一致且有 rationale 文档。

### Phase 2 · 通用组件（重建 `flutter/lib/ui/common/`）

- [x] 按压组件（按压缩放 + 水波 + 滚动中延迟显现防误触）、长按确认（1500ms 环形 +
      原始指针监听 + 起手 / 完成触感 + 移动超阈值取消）。
- [x] 二级页骨架（标题栏 + 内容 + 返回圆钮 + 减速推入）、底部弹层（玻璃 + 标题栏 + 抓手）。
      （顶栏改成整条玻璃后**不需要**额外顶渐隐，见 parity 文档 §4.7-3）
- [x] 状态组件：状态点（形状 + 颜色双编码，不靠颜色单独表状态）、顶栏状态行（读屏单独文案）、
      空状态、活动条（钢琴卷）、思考 pill（迷宫环）、各类 pill。
- [x] 触感入口（单一出口、五级、可关、静默降级）。
- [x] 设置组件：分组卡（组色相槽）/ 行（图标块 + 两行文字 + 右栏）/ 开关行 / 分段行 /
      一览卡 / 搜索胶囊 / 功能块 / 列表行。**右栏间距规则写进组件（`RowMetrics.gap = 8`），全站一致**。
- **完成判据**：组件单测覆盖按压 / 长按 / 触感 / 顶渐隐 / 双编码 / 右栏间距规则。

### Phase 3 · 逐屏重绘（优先级从高到低，每屏过验收 + 更新测试）

- [x] **三张"一眼定生死"的面**：聊天顶栏（整条玻璃 + 品牌标 + 三行标题列）、输入条（16 纸条）、
      设置 hub（分组卡 + 右栏规则）。
- [x] 主壳（双抽屉互斥 + 跟手 + 断点自适应，含窄屏快捷面板可达入口：顶栏网格键）。
- [x] 聊天页（台词轨 / 气泡 / 竖线回答 / 折叠 / 插话排队 / 引用 chip / 空状态示例）。
- [x] 会话抽屉、快捷面板（图标网格）。
- [x] 设置 hub + 全部子页（外观带实时预览 / 界面 / 性能 / 语音 / 通知 / 回复偏好 / 隐私 /
      系统权限 / 后台 / 诊断 / 关于 / 账号 / 引擎 / 远程 / 插件）。
- [x] 手机操控页（披露 + 长按 + 三档应用名单 + 解除限制 + 最近操作）。
- [x] 首启引导。
- [x] 其余壳页（网页 / 终端 / 社区 / 更新 / 运行 / 记忆）。
- **每屏完成判据**：行为测试保留并通过；视觉断言已按新设计更新并写明理由；
  该屏过第 8 节对应验收项。

### Phase 4 · 收尾

- [x] `FLUTTER_MIGRATION.md`：更新"当前壳包含"的描述（新设计语言，不再是复刻那一套）。
- [x] `flutter/README.md`：同步模块说明。
- [x] `docs/spec/flutter-visual-parity.md` 补完：每个 token 一条 rationale 的最终版。
- [x] 全量验证：verify.sh 全绿 + 上游扫描 OK + 无新增无理由依赖（反而删了字体资源）。
- **完成判据**：第 8 节全部通过。

---

## 7. 总执行清单（agent 开工后在此打勾，未勾完不停）

```text
红线与环境
[x] 默读第 1 节红线；未取用上游副本；提交前扫描脚本 OK
[x] bash flutter/tool/verify.sh 能跑通（基线 143 → 重写后 143，format/analyze/test 全绿）
[x] python tools/check_no_upstream_copy.py OK

Phase 0 调研
[x] 四个风格源头读完并有笔记（parity 文档 §1）
[x] token 命名表落盘（parity 文档 §2）
[x] docs/spec/flutter-visual-parity.md 骨架建成

Phase 1 token
[x] 颜色（预设 + 中性色规则 + 表面阶梯）重建
[x] 形状 / 间距 / 字阶 / 动效 / 表面重建
[x] 等宽出口唯一 + zh_CN locale
[x] 对比度单测（预设 × 明暗，计算验证）

Phase 2 组件
[x] Pressable  [x] HoldToConfirm  [x] AppPage/TopBar  [x] AppSheet
[x] 状态组件（点/状态行/空态/活动条/思考 pill/pill 族）
[x] 触感入口
[x] 设置组件（分组/行/开关/分段/一览/搜索/功能块/列表行，右栏规则统一 RowMetrics）

Phase 3 逐屏（每屏：实现 → 验收 → 测试更新 → 打勾）
[x] 顶栏 / 输入条 / 设置 hub（一眼定生死三张）
[x] 主壳（含窄屏快捷面板入口：顶栏网格键）
[x] 聊天页  [x] 会话抽屉  [x] 快捷面板
[x] 设置子页全套  [x] 手机操控  [x] 首启引导  [x] 其余壳页

收尾
[x] FLUTTER_MIGRATION.md / flutter/README.md 同步
[x] parity 文档补完（每 token 一条 rationale）
[x] 第 8 节验收全部通过（A1–A10 见 parity 文档 §6）
[ ] 真机验证 —— 按第 9 节不做，留给项目所有者
```

---

## 8. 验收清单（Part C，每屏 / 每次改 token 都跑）

| # | 检查 | 通过标准 |
|---|---|---|
| A1 | token 层对等 | 与设计契约 + Kotlin 参考实现对等；命名 Maeve 自有；`docs/spec/flutter-visual-parity.md` 每个 token 有 rationale |
| A2 | 对比度 | 每个预设 × 明暗：文本 ≥4.5:1、非文本 ≥3:1、契约更严的档位按契约；**换预设时各级文字对比度不变**；单测计算验证 |
| A3 | 功能契约 | 第 3 节清单逐条仍在（桥接 / AAR / 入口 / 全部屏幕行为 / 原型限制） |
| A4 | 测试 | 行为断言保留且全绿；视觉断言已更新且有理由；没有"为了绿把断言删了" |
| A5 | 动效与降级 | 减少动效时所有时长归 0、循环动画停静止帧；关模糊 / 高对比度时不建模糊层、用近不透明填充、文字仍可读 |
| A6 | 无障碍与状态 | 触控目标 ≥44dp；状态色 + 图标/文字双编码；顶栏状态行有读屏专用文案；危险操作长按 1500ms、30s 不选按拒绝 |
| A7 | 窄屏可达 | 快捷面板在窄屏（<520dp）有可见入口，不依赖被系统占用的边缘手势 |
| A8 | 工程 | verify.sh 全绿；上游扫描 OK；无 `ColorScheme.fromSeed`；无新增无理由依赖 |
| A9 | 右栏规则 | 设置行右栏（value / chip / 开关 / 箭头）间距有一套规则且全站一致，长 value 省略号不溢出 |
| A10 | 文档 | FLUTTER_MIGRATION.md / flutter/README.md 与新壳一致；复刻规格已标注作废 |

---

## 9. 明确不做（范围纪律）

- **真机验证**：不在本规格范围内。执行 agent 在文档里写明"未做真机验证"即可；
  真机由项目所有者在 agent 完成后自行进行。
- **Kotlin/Compose 退役**：Phase 4 另案，用户方案未下达前**不删任何 Compose 代码**。
- **桥接真实能力**：模型调用、持久化、语音、系统分享面板、附件 picker——原型限制保持，不接真。
- **虚拟屏 / 悬浮窗功能**：Mika 式的隐藏 VirtualDisplay + 应用内悬浮小窗是另一个功能议题，
  不在本次重写范围；手机操控页的"悬浮条三态"壳保持现状即可。
- **顺手重构**：`models/` `bridge/` 与桥接契约不动；与重写无关的代码不顺手改。

---

## 10. 完成定义（Definition of Done）

整任务完成 = 以下**全部**满足：

1. Phase 0/1/2/3/4 全部勾完；每屏过第 8 节对应验收项。
2. `bash flutter/tool/verify.sh` 全绿；`python tools/check_no_upstream_copy.py` OK。
3. 对比度单测覆盖每个预设 × 明暗，计算验证。
4. `docs/spec/flutter-visual-parity.md` 每个 token 一条 rationale。
5. 功能契约（第 3 节）逐条仍在，行为测试保留并通过。
6. 窄屏快捷面板可达（A7）；右栏间距规则统一（A9）。
7. 文档同步完成；复刻规格（UI_SPEC_HANDOFF.md）已标注作废。

---

## 附：给执行 agent 的第一课

如果你读完本规格只记住一件事，记住这个：**这个壳曾经是别人的脸的翻版。**
用户要的不是"给复刻皮肤换套色"，是 Maeve 自己的脸。去读 `docs/spec/visual-tokens.md`，
去读 Kotlin 侧的 theme，去看 `maeve_mark.dart` 那个环——答案在 Maeve 项目里，不在上游仓库里。
