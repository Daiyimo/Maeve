# Maeve iOS 开发标准

> 目标平台：iOS 26 及后续版本
> 实现优先级：SwiftUI 原生实现 > Apple 系统框架 > 必要时桥接 UIKit
> 适用范围：Maeve 原生 iOS 工程、功能模块、界面重构、代码审查和发布验收

## 1. 目标与边界

Maeve iOS 版应当是一个内容优先、交互克制、遵循系统行为的 AI Agent 应用。界面不复制 Android Compose、Web 控制台或运营活动页；品牌辨识度来自信息结构、日食视觉符号、内容层级和恰当的动效，而不是手写一套伪系统控件。

Android 版的对话、模型配置、流式响应、工具调用、审批、记忆和运行记录属于迁移范围。Android 无障碍服务、跨 App 控制、ADB/root、虚拟屏、全局悬浮窗和 APK 自更新不属于可直接移植的 iOS 能力，必须采用明确的替代方案或标记为不支持，不得在 UI 中承诺不存在的能力。

## 2. 工程与架构

### 2.1 目录建议

```text
MaeveIOS/
  App/
    MaeveApp.swift
    AppEnvironment.swift
    AppRouter.swift
  Features/
    Chat/
    Runs/
    Memory/
    Settings/
  Domain/
    Chat/
    Agent/
    Memory/
    Providers/
  Services/
    Networking/
    Persistence/
    Security/
    Web/
    Speech/
    Notifications/
  Shared/
    Components/
    DesignSystem/
    Extensions/
  Resources/
```

按业务能力组织代码，不按 View、Model、Utils 等模糊类别堆放文件。功能模块应尽量拥有自己的状态、视图、服务协议和测试。

### 2.2 状态与并发

- 使用 Swift Concurrency：`async/await`、`Task`、`AsyncSequence` 和结构化并发。
- UI 状态使用 Observation 或 SwiftUI 推荐的可观察模型；避免通过全局单例直接改变界面。
- 所有网络任务、流式响应和 Agent 运行都必须支持取消。
- 长任务必须有明确的状态：排队、运行中、等待审批、成功、失败、已取消、需要恢复。
- `@MainActor` 仅用于 UI 状态和需要主线程的系统 API；网络、解析和持久化不得无理由占用主线程。
- 服务通过协议注入，Preview 和测试使用 Fake/Stub，不在 View 内直接创建真实网络客户端。

### 2.3 模块边界

- `Domain` 不依赖 SwiftUI、UIKit 和具体网络库。
- `Features` 只通过服务协议访问网络、持久化、Keychain 和系统能力。
- `Services` 负责平台适配和外部协议，不把平台细节泄漏到业务 View。
- 供应商协议统一抽象为模型能力、流式事件、工具调用和用量事件，供应商差异集中在适配器中。
- 不为了共享 Android 代码而强行引入 Kotlin Multiplatform。若未来共享核心，必须先以独立协议和行为测试证明收益。

## 3. iOS 26 原生界面标准

### 3.1 应用结构

- 多个一级栏目必须优先使用原生 `TabView`，建议保持不超过五个主要栏目。
- 每个一级栏目拥有独立的 `NavigationStack`，保留系统返回按钮和侧滑返回手势。
- iPad 或宽屏场景需要多栏层级时使用 `NavigationSplitView`，不要用固定宽度的自绘侧栏替代。
- 顶部操作统一放入 `toolbar`；禁止手写假的导航栏、返回按钮和状态栏。
- 使用系统提供的导航、工具栏、Tab bar、sheet 和菜单，让 iOS 26 自动处理 Liquid Glass、滚动边缘和可访问性适配。

### 3.2 页面职责

| 页面 | 结构要求 |
| --- | --- |
| 对话首页 | 会话列表、搜索、置顶和新建操作；使用 `List`、`searchable`、toolbar |
| 对话详情 | 消息内容优先；输入区固定在安全区域内；工具状态和审批使用系统 sheet/dialog |
| 运行记录 | 使用分组列表展示状态、时间和结果；详情页展示事件时间线 |
| 记忆 | 使用 `List`、`Section`、编辑 sheet；支持搜索、删除确认和空状态 |
| 设置 | 使用 `Form`、`Section`、`NavigationLink`、`Toggle`、`Picker`、`Stepper` 和 `DatePicker` |
| 模型配置 | 表单分组展示服务商、Endpoint、模型、能力和密钥状态；密钥本身不可回显 |
| 网页任务 | 使用 `WKWebView` 作为受控内容容器，明确展示当前站点和用户控制边界 |

### 3.3 控件替换规则

| 禁止或谨慎使用 | 优先替换为 |
| --- | --- |
| HStack/ZStack 手搓底部导航 | `TabView` |
| 自绘顶部导航栏 | `NavigationStack`、`navigationTitle`、`toolbar` |
| 自绘搜索框 | `.searchable` |
| 自绘单选/多选弹层 | `Picker`、`Menu`、系统 `sheet` |
| 自绘开关 | `Toggle` |
| 自绘数字增减器 | `Stepper` |
| 自绘确认弹窗 | `confirmationDialog`、`alert` |
| 自绘底部抽屉 | `sheet`，必要时配置 presentation detents |
| 自绘全屏遮罩 | `fullScreenCover` |
| 字符串或第三方功能图标 | SF Symbols |
| 所有内容都做成圆角卡片 | `List`、`Form`、`Section` 和自然滚动内容 |

自定义 View 只用于 Maeve 的消息内容、Agent 事件、代码块、日食品牌标志和确实需要产品表达的内容。自定义组件必须保留键盘、VoiceOver、动态字体、Reduce Motion 和 Reduce Transparency 下的可用性。

### 3.4 Liquid Glass 使用

- 优先使用系统 `NavigationStack`、`TabView`、toolbar、sheet、popover、Menu 和标准控件自动获得系统材质。
- 不给导航栏、Tab bar、toolbar 或系统控件覆盖自定义不透明背景。
- 自定义玻璃效果只用于少量重要的功能性控件，不用于装饰、不用于所有卡片，也不用于替代层级结构。
- 新增自定义材质前，先验证浅色、深色、增加对比度、减少透明度、减少动态效果和动态字体设置。
- 不把 Liquid Glass 当成品牌色；品牌色应服务于状态、焦点和 Maeve 标志。

### 3.5 图标、颜色与排版

- 功能图标优先使用 SF Symbols，保持同一语义下的 symbol variant、线重和尺寸一致。
- 不混用 Android 图标、Web 图标、第三方图标和拟物图标。
- 颜色优先使用语义色和自适应颜色，确保浅色/深色模式及高对比度可读。
- 不依赖颜色单独表达状态；状态必须同时有文本、图标或形状差异。
- 正文使用系统字体和 Dynamic Type，禁止用固定字号破坏辅助功能。
- 间距、圆角、分隔线和内容边距优先遵循系统组件默认值，只有内容层有明确理由时才定制。

## 4. Agent 与网络标准

### 4.1 流式模型协议

- 先实现一个 OpenAI-compatible 适配器，完成真实闭环后再扩展 Gemini、Anthropic 和 Responses 协议。
- 流式响应使用 `AsyncThrowingStream` 或等价的可取消异步序列。
- 必须处理分片边界、空事件、网络断开、服务端错误、解析错误、超时和用户取消。
- 原始响应不得直接拼接到 View；转换为领域事件后再交给界面。
- 记录请求耗时、首 token 时间、输入/输出 token 和错误分类，但不得记录 API 密钥或完整敏感提示词。

### 4.2 Agent 执行

Agent 循环应明确区分：模型请求、工具选择、参数校验、风险判定、用户审批、工具执行、结果回传和最终响应。

- 工具必须声明名称、描述、参数 schema、风险级别、是否需要审批和是否支持 iOS。
- 不支持的 Android 工具不得伪装成可用工具；应在能力清单中返回受限原因和替代方式。
- 文件、网络、网页、语音和通知能力均应经过最小权限设计。
- 高风险操作默认需要用户确认，确认内容必须包含目标、影响和可撤销性。
- 每次运行保存可恢复的事件日志，支持用户查看、取消和重试。
- 工具执行不得绕过系统权限、用户授权或 App 沙盒。

### 4.3 iOS 平台替代边界

| Android 能力 | iOS 策略 |
| --- | --- |
| 读取其他 App 无障碍树 | 不提供等价能力；支持用户主动分享截图、文件或内容 |
| 自动点击其他 App | 使用 App Intents、Shortcuts、Universal Links 或服务 API；无法覆盖时明确不支持 |
| 全局悬浮窗 | 应用内任务状态、通知或符合条件的 Live Activity |
| Android 虚拟屏和 shell | 受控的 App 内网页或服务端沙箱；不承诺本地 Linux 执行 |
| 精确闹钟和常驻服务 | 本地通知、系统后台任务或服务端调度；不承诺准点常驻执行 |
| APK 自更新 | App Store、TestFlight 或组织内合法分发机制 |

## 5. 数据、安全与隐私

- API 密钥、刷新令牌和加密密钥只存 Keychain；禁止写入 UserDefaults、日志、截图或普通 JSON。
- 会话、运行记录和设置使用 SwiftData、SQLite 或版本化文件存储；定义 schema version 和迁移策略。
- 附件与缓存存放在 App 沙盒，区分可清理缓存和用户数据。
- Android 数据导入必须通过用户主动导出的版本化文件完成，不直接读取 Android Keystore 数据。
- 导入前展示数据范围、敏感字段和失败恢复方案；导出文件默认加密或明确提醒风险。
- 使用最小权限声明：相册、相机、麦克风、语音识别、通知和网页访问均在实际使用前请求。
- 隐私说明、网络数据使用和第三方供应商处理范围必须与实际代码一致。
- 日志按级别控制，生产日志不得包含密钥、完整会话内容、Cookie、Authorization header 或私人附件。

## 6. 测试与质量门禁

### 6.1 必须覆盖

- ViewModel/状态：空状态、加载、流式更新、错误、取消、重试和恢复。
- 网络：SSE 分片、断线、超时、HTTP 错误、错误 JSON 和供应商协议差异。
- Agent：参数校验、审批、拒绝、工具失败、循环上限和取消传播。
- 数据：首次启动、升级迁移、损坏文件、导入导出和 Keychain 缺失。
- UI：浅色/深色、动态字体、VoiceOver、Reduce Motion、Reduce Transparency、横竖屏和 iPad 宽屏。
- 真机：低网络、前后台切换、锁屏、通知授权拒绝、麦克风/相册权限拒绝和低电量场景。

### 6.2 每个功能的验收标准

1. 有明确的成功、失败、空和加载状态。
2. 系统返回、侧滑返回、键盘收起和 sheet 关闭行为正常。
3. 能通过 VoiceOver 识别标题、按钮、输入框、状态和错误。
4. 不依赖颜色单独表达状态，动态字体放大后没有内容遮挡。
5. 网络和任务可取消，失败后用户知道下一步怎么做。
6. 没有使用手写导航栏、伪 Tab bar、伪系统弹窗或无理由的全局玻璃背景。
7. 关键路径有单元测试或 UI 测试，并在真实 iPhone 上验证。

### 6.3 构建门禁

提交前至少完成：

```text
swift-format / 项目格式检查
SwiftLint 或等价静态检查
单元测试
UI smoke test
Debug 真机构建
Release archive 构建
```

Windows 可以编辑源码和管理 Git，但 iOS SDK、Xcode 编译、签名、模拟器和 iPhone 调试必须在受支持的 macOS/Xcode 环境完成。任何未在 Mac 上验证的 SwiftUI 代码都必须标记为待验证，不得宣称可发布。

## 7. Git 与协作规范

- iOS 工程放在仓库的 `ios/` 或独立清晰的 iOS 工程目录，不覆盖 Android 和 Flutter 工程。
- 提交应按功能拆分，例如 `ios: add native chat shell`、`ios: implement streaming provider`。
- 不提交 DerivedData、模拟器数据、签名证书、Provisioning Profile、API 密钥和本地构建缓存。
- 代码、文档和资源变更必须说明是否已在 Mac/Xcode 和真机验证。
- Android、Flutter 和 iOS 的行为差异必须记录在迁移说明中，不能用静默降级掩盖能力缺失。

## 8. 版本化迁移路线

1. **P0 基线**：确认页面、数据契约、模型协议和 iOS 能力边界。
2. **P1 原生骨架**：完成 TabView、NavigationStack、对话、任务、记忆和设置页面。
3. **P2 真实对话**：完成一个供应商的配置、流式响应、持久化、错误和取消。
4. **P3 Agent 闭环**：完成工具注册、审批、执行、事件日志和恢复。
5. **P4 能力扩展**：接入记忆、附件、语音、受控网页和其他供应商协议。
6. **P5 发布验收**：完成数据迁移、隐私检查、无障碍、性能、真机和 TestFlight 验收。

每个阶段结束时必须更新迁移记录、已知限制和测试结果。未完成的能力不得以占位按钮或看似可用的状态展示给用户。
