# Maeve UI/UX 重构说明

> 这次重构把 Maeve-old 的界面整套换成了「西部世界」主题的新设计系统。
> 设计契约在 [visual-tokens.md](../spec/visual-tokens.md)(M2,本次未改);
> 本文讲**实现**:新系统长什么样、代码在哪、怎么改、怎么验。

## 0. 一句话

80+ 个旧文件**零改动**自动换上新设计——靠的是「新 `Maeve*` 正式 API + `MikaCompat.kt`
一层 deprecated 别名」。真正重写的只有组件底、欢迎页、对话页空状态、外观页和桌面图标。

## 1. 文件地图

```
ui/theme/
  MaeveOklch.kt      OKLCH 色彩科学( Ottosson 变换、对比度、gamut 压缩)
  MaeveAccent.kt     六套预设 + 推导 + 存储(MaeveAccentStore,StateFlow 广播)
  MaeveColors.kt     全部颜色;中性色相随配色走、明度恒定
  MaeveSemantic.kt   语义色(状态/工具色调/身份色),与强调色无关
  MaeveSurfaces.kt   四档玻璃 + 页面背景(渐变+双光晕)
  MaeveType.kt       字阶/间距/尺寸 MaeveType / MaeveSpace / MaeveSize
  MaeveShapes.kt     五档连续圆角(6/10/14/20/28)
  MaeveMotion.kt     动效阶梯(press 90 → sheet 380 → breath 2400)
  MaeveMark.kt       品牌标志:双错位环 + 偏离中心的光点(几何=启动图标)
  MaeveTheme.kt      MaeveTheme / surfaces / semantic / haptics 入口
  MikaCompat.kt      deprecated 别名层(MikaColors=MaeveColors …),旧代码零改动
ui/components/
  Surfaces.kt        PageBackground→渐变背景;cardSurface→玻璃卡;GlassBar→chrome 档
  Logo.kt            MikaLogo→MaeveMarkTile(跟配色走的矢量标志)
ui/
  AppearanceSection.kt  外观页:六预设配色选择器 + 桌面图标选择器
  LauncherIcon.kt       桌面图标四变体 + 控制器(先启新的再关旧的)
  WelcomeScreen.kt      MaeveMarkTile 呼吸 hero
  ChatScreen.kt         空状态呼吸标志;用户气泡用专用气泡色位
  SettingsSubPages.kt   「语言与外观」页接入配色与图标选择
res/
  mipmap-anydpi-v26/   ic_launcher{,_pearl,_night,_mono}.xml
  drawable/            maeve_launcher_foreground.xml(矢量,替掉低清 PNG)
                       maeve_launcher_bg_{pearl,night,mono}.xml
```

## 2. 设计系统怎么工作

### 2.1 配色:一颗种子推出整套

每套预设只定**两颗种子色**(浅/深)和中性色的色调强度,其余 20+ 个色位按
OKLCH 明度/彩度阶梯现推(`deriveAccent`)。推导完过两道守卫:

- `guardSeed`:浅种子上的白字、深种子上的深墨字 ≥ 4.5:1,不够就挪明度(色相不动);
- `guardText`:各级文字对页面/卡片 ≥ 4.5(正文)/ 3(辅助)。

换预设时**中性色只转色相、明度不动**——所以 ink/bg 的对比度永远不漂移,
这一点有测试守着(`neutral surfaces keep constant lightness across presets`)。

六套预设取名来自《西部世界》:雾紫(默认,梦游)、晨金(牧场晨光)、沙褐(迷宫)、
珠白(珍珠)、深靛(甜水镇夜)、石墨(Cradle 的金属)。

### 2.2 气泡色位(重构中修掉的一个坑)

第一版把 `accentBubble` 定成 L0.918 的粉彩,配白字对比度不够。现在:

| 主题 | 气泡 | 气泡上的字 |
|---|---|---|
| 浅色 | 种子色(和主按钮一致) | 白 |
| 深色 | 深色调 L0.314 | 近白 L0.93 |

`onAccentBubble/accentBubble ≥ 4.5` 已加进对比度测试,六预设 × 浅深全过。

### 2.3 玻璃与背景

- 页面底:**竖向渐变 + 两团极淡的光**(右上主色、左下暮色)。同一主题内所有页共用
  一张背景,切页时背景不动。光晕是给玻璃「可透的颜色」——纯平背景上的玻璃只是白块。
- 四档玻璃(`GlassTier`):chrome(顶栏/输入条,真模糊)/ sheet(弹层)/ card(设置卡,
  半透明填充+发丝线+顶边 rim)/ pill(胶囊)。铁律:**层次靠材质,不靠阴影**。
- 旧 `cardSurface` 的调用点全部自动获得玻璃卡:半透明填充 + 0.5dp 发丝线 + 顶边高光;
  浅色下保留一道极淡阴影帮助分层。

### 2.4 标志与「她醒着」

`MaeveMark` 的几何与启动图标同一套 24 网格(外环=你的主屏、内环=她的隐藏屏、
光点=此刻正在跑的那一步),所以 App 内标志和桌面图标永远不会各长各的。
欢迎页、对话页空状态、关于页 hero 三处标志带**呼吸动画**(光点 2.4s 一呼一吸),
系统关动画时自动静止。

## 3. 用户能摸到的变化

| 位置 | 变化 |
|---|---|
| 设置 › 语言与外观 | 新增「配色」:六预设 + 跟随壁纸(API 31+,只借色相) |
| 设置 › 语言与外观 | 新增「桌面图标」:雾紫/珠白/夜色/单色,当场切换 |
| 欢迎页 | 呼吸的标志方块 hero |
| 对话页空状态 | 呼吸的标志 + 按时段问候 + 示例卡 |
| 所有页面 | 渐变背景 + 玻璃卡;用户气泡换专用色位 |
| 桌面 | 四套图标变体,矢量绘制(不再是一张低清 PNG) |

换配色/图标**不重建界面**:`MaeveAccentStore` 存盘时广播,`MaeveTheme` 收广播,
页面和滚动位置都不丢。

## 4. 怎么改

**加一套预设**:`AccentPreset` 里加一行(两颗种子 + tintChroma),测试会自动覆盖它
(`all presets light and dark pass contrast guards` 遍历 `entries`)。

**调玻璃**:`buildSurfaces` 里四档的填充透明度/模糊 σ/饱和度一处改,全局生效。

**换标志动画**:`MaeveMark(breathing = true)`;节奏在 `MaeveMotion.BreathMs`。

## 5. 验证

```
gradle :app:assembleDebug --offline          # 构建
gradle :app:testDebugUnitTest --offline      # 全量单测
```

- `MaevePaletteContrastTest`:六预设 × 浅深的对比度守卫(含气泡色位)、OKLCH 往返、
  超 gamut 只压彩度、identityIndex 稳定性——**全过**。
- 全量单测失败集与重构前**逐条一致**(61 个环境相关失败:进程派生/沙箱文件系统
  在本机跑不了,与 UI 无关),**零新增失败**。
- 本机没有模拟器(无系统镜像),真机视觉验证留给装机后;APK 已产出在
  `app/build/outputs/apk/debug/app-debug.apk`。

## 6. 兼容策略(给后续改代码的人)

- 新代码一律用 `MaeveColors` / `MaeveTheme` / `MaeveMotion` / `MaeveRadius`。
- `MikaCompat.kt` 的别名是** deprecated 的过渡层**,不是新 API;旧文件慢慢改,
  改一个删一个别名,全部清完把兼容层删掉。
- `MikaLogo` 这类保留旧名的 composable 内部已走新实现,行为已经是新的。
