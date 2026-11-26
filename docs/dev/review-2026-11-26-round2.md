# Maeve 项目第二轮深度 Review（2026-11-26）

## 范围

本轮补查第一轮未深入的模型适配、Agent 循环、工具分发、审批硬规则、定时任务、Android 生命周期、文件查看器与持久化失败路径。业务代码未修改；为验证权限弹窗问题临时添加过一条回归测试，确认后已删除。

## 结论

本轮确认 **2 个高严重度缺陷**、**3 个中严重度缺陷**，另有若干低严重度数据与生命周期问题。最需要先修的是系统权限弹窗硬规则绕过，以及条件定时任务停止重排。

## 已确认缺陷

### 1. 高：旧快照 + 当前权限弹窗时，系统授权硬规则可被绕过

- `core/src/main/kotlin/com/daiyimo/maeve/core/action/ConfirmDecision.kt:104-112` 只在初始 `aim.aimed` 上检查 `systemPermissionPage`；`:153-156` 复看当前屏幕时漏掉了同一检查。
- `core/src/main/kotlin/com/daiyimo/maeve/core/action/ActionExecutor.kt:187-208` 在审批、对外动作间隔、页面停留和锁屏等待后，只复检锁屏代次，没有重新复检系统权限弹窗。
- 最小回归测试构造：模型手里是 `com.chat` 的旧快照，当前 `peek()` 已是 `com.android.permissioncontroller`。期望返回 `system_permission` 且不点击；实际返回 `Ran(ok=true, delivery=sent)`，权限弹窗被点击。
- 影响：违反“Android 运行时权限/系统授权弹窗必须交还用户”的硬规则，可能代替用户授权。
- 建议：把 `systemPermissionPage` 纳入每次 fresh screen 复检，并在真正投递前再做一次；同时补旧快照/等待后弹窗的回归测试。

### 2. 高：条件定时任务在“条件不成立/检查失败”后不再重排

- `app/src/main/java/com/daiyimo/maeve/task/TriggerRuntime.kt:189-213`：
  - `ConditionOutcome.Error` 标记 Failed 后直接 return；
  - `!outcome.met || wasMet` 标记 Skipped 后直接 return。
- 两条路径都没有调用 `engine(context, store).rearm(...)`。
- `app/src/main/java/com/daiyimo/maeve/task/TaskService.kt:460-473` 在条件检查结束后直接 `onRunEnded` 并返回，不会走普通任务的 `TriggerRuntime.finishTask` 重排路径。
- 影响：条件计划第一次检查为 false、状态未翻转或检查失败后，当前闹钟已消费，下一次永远不会再排，条件定时功能静默停止。
- 建议：所有条件检查终态都统一重排；Error 也应安排下一次检查，而不是永久停摆。

### 3. 中：亮屏/网络恢复的全局 5 秒节流会吞掉有效恢复事件

- `app/src/main/java/com/daiyimo/maeve/task/TriggerRuntime.kt:50-60` 对 `ACTION_USER_PRESENT` 和网络 validated 回调共用 `lastReadinessFireAt`，5 秒内任何事件都被丢弃。
- 场景：亮屏时离线，`fire()` 因 offline 重排到 60 秒后；1 秒后网络恢复，但恢复事件被节流吞掉，任务仍等待最长 60 秒。
- 影响：Doze 补跑修复在“先亮屏、后联网”的常见顺序下仍会明显延迟。
- 建议：按事件类型分别节流，或节流期间合并成一次延迟补跑，不要直接丢弃。

### 4. 中：HTML 文件查看器对不可信内容启用脚本，且没有隔离导航

- `app/src/main/java/com/daiyimo/maeve/ui/viewer/FileViewerScreen.kt:227-246` 对 HTML 启用 `javaScriptEnabled` 和 `domStorageEnabled`，使用 `loadDataWithBaseURL(null, ...)`。
- 没有 `WebViewClient`/`shouldOverrideUrlLoading`，与注释“点链接交给系统处理”不一致；也没有显式关闭 `allowFileAccess`、`allowContentAccess`。
- 同文件 `:163-165` 对 HTML 使用无上限 `readBytes()`，大文件可造成内存压力。
- 影响：不可信 HTML 可在应用进程内执行脚本并在 WebView 内导航，扩大本地文件、ContentProvider 与 WebView 攻击面。
- 建议：不需要脚本就关闭 JavaScript；至少设置安全 WebViewClient、关闭 file/content 访问，并给 HTML 增加大小上限。

### 5. 中：关键本地账本在 rename 失败回退中先删除旧文件

- `app/src/main/java/com/daiyimo/maeve/wechat/WechatStore.kt:98-105`：`temp.renameTo(file)` 失败时先 `file.delete()`，再重试；第二次失败会丢失原凭证/状态。
- `core/src/main/kotlin/com/daiyimo/maeve/core/usage/UsageLedger.kt:281-286`：同样先删除旧账本再 rename。
- `app/src/main/java/com/daiyimo/maeve/cap/phone/setup/UsbStartScript.kt:58-64` 也有 delete-before-rename 回退。
- 影响：文件系统异常时，本来可保留的旧凭据、用量账本或脚本会被主动毁掉。
- 建议：统一使用 `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`；不支持原子移动时保留旧文件并报错，不先删除。

## 代码路径风险

### 6. 中：`onRunEnded` 在释放队列任务后若抛异常，可能把任务永久卡在 running

- `app/src/main/java/com/daiyimo/maeve/task/TaskService.kt:534-589` 先调用 `center.onEnded(run)`，后者会把排队任务移入 running 并返回 `started`。
- 后续 `startInForeground`、通知构建或 `launch` 一旦抛异常，外层 catch 只记日志；已移入 running 的任务可能再也没有 `launch` 机会。
- 影响：前台服务启动限制、通知异常等情况下，任务队列可能静默卡住。
- 建议：把“移出队列”和“真正启动”放在同一个失败可回滚的结构里；或 catch 到异常时对未启动任务做补偿。

## 低严重度问题

### 7. 删除对话的 `.removed` 墓碑永不清理

- `app/src/main/java/com/daiyimo/maeve/chat/ConversationStore.kt:124-133` 用 `.removed` 永久阻止旧 ID 复用，但没有清理策略。
- 长期删除对话会累积小文件；若未来需要恢复 ID 或减小私有目录，会被永久阻塞。
- 建议：给墓碑设置保留期，或改用有界布隆过滤器/ tombstone 表。

### 8. `RunApproval.gates` 随子任务数量增长

- `app/src/main/java/com/daiyimo/maeve/settings/ApprovalControl.kt:12,25` 每次 `gate()` 都加入列表，子循环结束后没有移除入口。
- 长父任务创建大量子任务时会持续保留 `ConfirmationGate` 对象。
- 建议：提供 `closeGate(gate)`，在 AgentLoop 关闭时移除；或使用弱引用集合。

### 9. 分享页缓存失败的帧请求，无法在同一页面重试

- `flutter/lib/widgets/task_share_page.dart:189-197` 使用 `putIfAbsent` 缓存 `Future`，失败后不移除。
- 一次瞬时 `runScreen` 失败后，再次选择该帧仍直接复用失败 Future，只能关闭重开页面。
- 建议：失败时从 `_frames` 移除该条目，允许重试。

### 10. `LocalHttpProxy.close()` 不取消内部协程作用域

- `app/src/main/java/com/daiyimo/maeve/cap/web/LocalHttpProxy.kt:40,84-92` 只关闭 ServerSocket 和 Socket，没有 `scope.cancel()`。
- 当前 socket 关闭后大多数字程会退出，但作用域与潜在子协程没有明确生命周期。
- 建议：close 时取消 scope，并等待或清理残留任务。

## 测试盲区

- 现有测试覆盖了“当前屏就是权限弹窗”，但没有覆盖“旧快照 + 当前弹窗”和“等待后弹窗出现”。
- 条件定时任务没有覆盖 false/unchanged/error 后下一次闹钟是否仍存在。
- HTML 查看器没有 WebView 安全配置测试。
- 持久化 rename 失败回退没有故障注入测试。
- 本轮临时回归测试已验证缺陷 1；业务代码未修，因此测试以预期失败结束，临时文件已删除。

## 上轮问题状态

第一轮报告中的 CI 源码体量门禁、运行/送达账本无限增长、脚本动作超时被 `NonCancellable` 抵消、闹钟缓存早写、操作回执 4096 条上限、`GlobalScope`、分享图 MediaStore 更新结果未检查等问题，本轮未修改业务代码，仍然存在。

## 本轮验证

- 临时回归测试确认缺陷 1：期望 `system_permission`，实际 `Ran(ok=true, delivery=sent)`；测试文件已删除。
- `DocsConsistencyTest`：通过（4 项）。
- i18n：通过，10 种语言、2060 条资源。
- compliance：通过。
- CI `pr-gate` 拓扑：通过。
- 源码体量门禁：仍失败，119 处超限（118 个历史文件增长、1 个新文件超过 800 行）。
- 构建缓存已再次清理；未修改业务代码。
