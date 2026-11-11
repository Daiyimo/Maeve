# Maeve 代码深度 Review · 2026-11-09

> 范围：`:core` 与 `:app` 全量生产代码。方法：四个专项并行通读（agent 核心并发与异常、
> 沙箱与工具分发安全、clean-room 重写包的行为等价性、app 模块生命周期与 ANR 风险），
> 逐条核实行号后汇总。只读审查，未改动任何代码。
>
> 相关：[project-review.md](project-review.md)（工具链与构建）、
> [../derived-rewrite-status.md](../derived-rewrite-status.md)（重写进度）。

## 1. 结论

| 严重度 | 数量 | 主题 |
|---|---:|---|
| HIGH | 6 | 同步磁盘 IO（含 fsync）落在主线程：启动恢复、`onStartCommand`、对话读写、抽屉、开机广播 |
| MEDIUM | 7 | 无界增长的投递账本、MCP 结果无上限落盘、下载预留绕过沙箱锁、主线程加密读写、通知 ID 撞号 |
| LOW | 8 | 锁粒度、过宽捕获、标志位竞态、错误码映射、占位符语义漂移 |

**总体判断：可以发布，但 HIGH 的六条应在真机验证前修掉**——它们不改变功能正确性，
只把磁盘等待搬到主线程，表现为冷启动卡顿、抽屉打开卡顿、点击后粘滞，低端机上可能 ANR。
沙箱隔离层与重写包的质量很高（见第 6 节），没有发现逃逸类缺陷。

## 2. 审查范围与方法

| 专项 | 覆盖 |
|---|---|
| agent 核心 | `core/.../agent/AgentLoop.kt` 全文通读、`trace/`、`run/`、各 Store |
| 安全层 | `workspace/`（`WorkspaceFileGate.kt`）、`sandbox/`、`tool/` 分发器 |
| 重写包 | 相对基线 `7cf7ff3` 逐文件比对已重写的 146 个文件：压缩 diff + 全枚举序与 `@SerialName` 两树比对 + 公开声明名两树比对 |
| app 模块 | `task/`、`ui/`、`settings/`、`capsule/`、`wechat/`、`AndroidManifest.xml` |

## 3. HIGH：主线程磁盘 IO 与 ANR 风险

共同形态：`FileRunStateStore.save` / `FileOutboxStore.save` 一类实现是「读全量 JSON → 重写 → `fd.sync()`」，
调用点在主线程同步等待。慢存储或低端机上单次几十到几百毫秒。

### 3.1 `MaeveApp.kt:369` — 冷启动主线程恢复

```kotlin
runRecovery.recover().let { if (it.isNotEmpty()) Log.w("MaeveTask", "${it.size} runs interrupted by process restart") }
```

`RunStore.save` 对每个中断运行做一次全量重写 + fsync。同一段里
`taskRecovery.recover()`（上一行）和 `TriggerRuntime`（下面几行）都已经包了
`scope.launch`，唯独这条是裸调用。

**建议**：`scope.launch { runRecovery.recover() }.let { ... }` 之外包一层，
即 `scope.launch { val it = runRecovery.recover(); if (it.isNotEmpty()) Log.w(...) }`。

### 3.2 `TaskService.kt:203` — onCreate 同步恢复

```kotlin
(application as MaeveApp).runRecovery.recover(center.runs().map { RunId(it.key) }.toSet())
```

同上，且 `center.runs()` 本身也是一次全量读。服务创建是主线程路径。

**建议**：包进已有的后台 scope；注释已说明「进程里只做一次」，用 `AtomicBoolean` 之类保证。

### 3.3 `TaskService.kt:213` / `:259` — `onStartCommand` 同步开始任务

`onStartCommand` → `begin()` → `center.tryStart()`（`@Synchronized`，内含 `store.save`），
`begin()` 内还有两处 `center.store.get/save`。对话页发送、定时闹钟、微信渠道消息、
MCP 远程指令全部经 `startForegroundService` 走到这里。

**建议**：`begin()` 整体改为 `scope.launch { ... }`；`keepForeground()` / `startInForeground`
必须留在主线程（前台服务时限），只把 store 读写挪进 `withContext(Dispatchers.IO)`。

### 3.4 `ChatViewModel.kt:884` — 主线程全量重读对话

```kotlin
private fun saved(conversationId: String): List<Exchange> =
    runCatching { store.records(conversationId) }.getOrDefault(emptyList())...
```

该 ViewModel 的 scope 是 `Dispatchers.Main.immediate`，`store.changes.collect` 也在主线程。
屏幕上这个对话每次被写入一轮（后台任务跑完、胶囊发话、定时任务），主线程就全量重读重解析一次。

**建议**：读盘挪进 `withContext(Dispatchers.IO)`，`store.turns(id)` 同处理。

### 3.5 `ChatDrawer.kt:661` — 组合期遍历全部对话

```kotlin
val all = remember(open, revision) { if (open) conversations() else emptyList() }
```

`conversations()` → `ConversationStore.shared(context).list()`，对每个对话读 turns/title/pin/page/branch。
每次拉开侧栏、以及开着期间存储每次变动，都在主线程做这件事。

**建议**：改 `produceState(emptyList(), open, revision) { if (open) value = withContext(Dispatchers.IO) { conversations() } }`，
抽屉先渲染空态。

### 3.6 `BootRestoreReceiver.kt:15` — 开机广播主线程读盘 + binder

```kotlin
if (...) TriggerRuntime.rearm(context)
if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) TriggerRuntime.syncTimeZone(context)
```

两者 `@Synchronized`，内部新建 `FileTriggerStore` 读 `triggers-v3.json` 并发 AlarmManager binder 调用。
广播只有 10 秒预算，开机时系统繁忙。同项目的 `TriggerAlarmReceiver` 已经用了
`goAsync()` + `Dispatchers.IO` 的正确写法，这里没有。

**建议**：`val pending = goAsync()`，`scope.launch(Dispatchers.IO) { try { ... } finally { pending.finish() } }`。

## 4. MEDIUM

### 4.1 `core/run/Outbox.kt:11` — 投递账本无淘汰入口

`OutboxStore` 接口只有 `get` / `save` / `all`，没有任何删除或淘汰方法。
`MemoryOutboxStore` 的 `records` 是 `linkedMapOf`，只增不减；`deliver()` 把每种终态
（`Delivered` / `Failed` / `Uncertain`）都 `save` 进账本后永久驻留。
`DeliveryRecord` 内含完整 `Delivery`（消息正文）。

放大效应在生产实现 `FileOutboxStore.kt:55`：每次 `save` 全量序列化整个 `records`、
写临时文件、`fd.sync()`、rename，且同样永不清理。记录越多，每次发通知越慢。

**建议**：接口加 `remove(id)` 或 `prune(窗口)`；终态为 `Delivered` / `Failed` 的记录在保留期后删除，
只留 `Pending` / `Attempting` / `Uncertain` 供恢复——`pendingRecovery()` 与 `uncertain()`
的语义正好只依赖这三态。`FileOutboxStore` 改为增量追加或限制保留条数。

### 4.2 `mcp/ConversationMcpResultStore.kt:22` — MCP 结果无上限落盘

```kotlin
dirs.gate.createNew(ws, rel).use { it.write(content.toByteArray(Charsets.UTF_8)) }
```

外部 MCP 服务器的完整结果原样写进对话工作区：没有字节上限、没有工作区容量检查，
且完全不在 `dirs.tree.shared` 许可内。MCP 内容是设计上的不可信数据
（`McpClient.kt` 对预览做了 `Untrusted.wrap`），一个有 bug 或恶意的服务器返回巨量结果就会被整份落盘。
同仓其他写入方都有上限且持锁（`writeFile`、`WebDownloads`、`UploadStaging`、`saveOutput`）。

另外该写入与 `exclusive`（重置 / `clearWorkspaces` / 换根文件系统）无互斥，
可在刚删掉的工作区目录上重建目录，或让 `deleteTree` 以 ENOTEMPTY 半途失败。

**建议**：落盘前按字节数拒绝或截断；函数体包进 `dirs.tree.shared { withContext(Dispatchers.IO) { ... } }`。

### 4.3 `cap/web/WebDownloads.kt:319` — 下载预留绕过沙箱锁

`reserve()` 里的 `workspaceDir.mkdirs()` 与 `gate.createNumbered(...)` 在 `tree.shared` 之外执行。
该类构造参数注释明确「下载落盘在它的许可里写」，`withPageName` / `fetch` / `fallBack` / `startFrom`
也都取了 `tree.shared`，唯独 `reserve()`（被三个入口调用，`start()` 在启动协程前同步调用它）不加锁。

与进行中的 `exclusive` 删除竞争时：`mkdirs()` 可在重置删掉 `work/<ws>/` 后重建整个目录；
向已被删除的 inode 写入会静默丢数据但状态记为 DONE——模型被告知 `/workspace/downloads/x` 存在，实际没有。

**建议**：把 `gate.createNumbered` 移入 `tree.shared { ... }`（`reserve` / `save` 改 suspend 并在内部取锁）。

### 4.4 `ui/MemoryViewModel.kt:99` — 主线程读技能目录

`combine(...).stateIn(scope, SharingStarted.Eagerly, ...)` 的 transform 跑在传入的 scope 上，
而该 scope 是 `rememberCoroutineScope()`（主线程）；transform 内 `skillsUi()` 调
`PersonalSkillFiles.list()` / `drafts()`，都是同步文件读。

**建议**：`stateIn` 前插 `.flowOn(Dispatchers.IO)`。

### 4.5 `ui/MemoryViewModel.kt:107` — 主线程加密磁盘读写

`edit` / `delete` / `approve` / `add` / `setPinned` 由 Compose 点击回调（主线程）直接调
`store.update` / `forget` / `approve` / `add`；`MemoryStore` 底层是加密文件的读-改-写 + 密钥操作，
类注释明确「别在主线程上调」。

**建议**：每个动作包 `scope.launch { withContext(Dispatchers.IO) { ... } }`。

### 4.6 `settings/SystemStatusMonitor.kt:39` — 主线程同步 binder

构造时 `MutableStateFlow(read())` 跑在主线程（`MainActivity` onCreate），
`ContentObserver(main).onChange` → `refresh()` → `read()` 每次约 10 次同步 binder：
`Settings.Secure.getString` 多次、`NotificationManager.getNotificationChannel` 多次、
`areNotificationsEnabled`、`AppOpsManager.unsafeCheckOpNoThrow`。
无障碍开关、开发者选项、USB/无线调试任一 URI 变化都触发全量重读。

**建议**：`read()` 移到单线程 executor 或 `Dispatchers.IO`，完成后回主线程赋
`MutableStateFlow`（赋值本身线程安全）。

### 4.7 通知 ID 撞号（两个前台服务）

`wechat/WechatService.kt:59` 与 `settings/ChatGptSignInService.kt:84` 都用
`NOTIFICATION_ID = 30_002`，且发在同一个频道。后者 `notify` / `startForeground`
会顶掉前者的常驻通知：ChatGPT 登录守护期间微信监听的前台通知被替换（或反之），
一个服务停止取消通知时另一个的前台通知也一并消失。

**建议**：把其中一个常量改成 `30_003`。一行改动，零风险。

## 5. LOW

| 位置 | 问题 | 建议 |
|---|---|---|
| `core/trace/RunTrace.kt:325` | `end()` 在 `@Synchronized` 锁内执行 `replyText` lambda（逐 part 脱敏）+ `archive.writeReply` 落盘；同文件 `begin()` 明确把重活挪出锁外并注释「the stop lock must not be held meanwhile」，`end()` 违背同一约束。`event()` 被主循环对每个 `RunEvent` 调用、`httpStatus()` 在 HTTP 线程，都争同一把锁 | 仿照 `begin()` 拆分：进锁前算好 `CapturedReply` 与字节数，锁内只记账与落盘 |
| `core/run/Outbox.kt:49` | `deliver()` 发送后的终态 `save` 在 try/catch 之外。`channel.send` 异常被兜住转 `Uncertain`，但 `FileOutboxStore.save` 抛异常时消息其实已发出、异常却逃出 `deliver()`，且内存已更新而磁盘写入失败——恰好一次投递被破坏，重启后可能重复通知 | 把终态 `save` 纳入 try/catch，保存失败也按「结果不明不自动重发」处理并向上返回记录 |
| `core/trace/RunTrace.kt:53` | `TraceStore.start()` 外层 `catch (_: Throwable) { return null }` 吞掉一切含 `Error`，无任何日志 | 收窄为 `catch (e: Exception)` 并记一次日志，让 `Error` 传播 |
| `core/agent/RouteReplay.kt:43` | `private var replayed` 无 `@Volatile`：写在任务协程，读在 `noteResult()`（经 `TaskRun.settleOutcome` 的 `RunLearning.launchAfter` 事后协程），跨协程无同步 | 改 `@Volatile private var replayed`，成本极低 |
| `core/agent/AgentLoop.kt:232` | `runInternal` 入口无条件 `cancelRequested = false`。`cancel()` 在 `job == null` 的空窗期调用时标志位被置 true 却无人消费，紧接着被新 run 清掉——这次取消被静默吞掉 | 改「消费式」重置：入口取旧值再清零，若为 true 直接以 `Cancelled` 收尾 |
| `cap/sandbox/SandboxUsage.kt:56` | `clearAll()` 用 `runCatching { dirs.deleteWorkDir(name) }` 吞掉每个工作区删除的失败，半删被报告为已清空；同文件单删 `clear()` 是抛异常的，行为不一致 | 至少 `Log.w` 并统计失败名单返回给调用方 |
| `core/workspace/WorkspaceFileGate.kt:221` | `eraseEntry` 对非 NOT_FOUND 的删除失败原样抛 `IOException` 而非带错误码的 `SandboxError`；`AndroidFdSys.map` 未映射 EACCES / ENOTEMPTY / EFILE，调用方只能给出 `operation_failed` + 裸 errno | remove 处捕获 `IOException` 包装为 `SandboxError`，或补 errno 映射 |
| `cap/web/UploadStaging.kt:82` | `codeOf` 把 `NOT_A_FILE` 一律映射为 `file_not_found`，路径实际是目录或链接时模型被告知「文件不存在」而重试 | 显式映射 `OUTSIDE` / `LINKED_FILE` / `NOT_A_FILE` / `FILE_NOT_FOUND` / `FILE_EXISTS` |
| `core/prompt/Prompts.kt:56` | `fill()` 占位符扫描器在键形状校验失败时推进两个字符，原版正则可差一个字符重新匹配。`{{{key}}}` 场景下新版原样保留文本且不抛错。当前不可达（模板与源码里都没有三连括号），但是公开函数的潜在语义漂移 | 改为只推进一个字符，或直接沿用原版正则 |

## 6. 已核实无问题的方面

逐条读过代码确认，非推测：

- **路径穿越面**：`segments()` 拒绝 `..` / NUL、逐段 `O_NOFOLLOW` 下降、句柄上复核真实路径
  （`inside(rootReal)` + 精确 `parent/name` 匹配 + `kind==FILE` + `nlink==1`）、截断发生在核实之后、
  `replace` 经暂存文件 + 原子改名、`delete` 按 dev/ino 复核、`deleteTree` 每层重查真实路径且有深度上限。
  canonicalization 顺序正确，无 TOCTOU，无大小写 / 8.3 绕过（Android 内部存储大小写敏感）。
- **资源上限**：read 256KB / 图 5MB、write 1MB / 4GB、search 8MB / 文件、下载 / 上传 / 解包的字节数与条目数上限，
  全部在分配前强制。
- **锁**：`SandboxTree` 锁序 doorway → census → idle 无环，所有解锁在 `finally` / `withLock` 中。
- **clean-room 重写**：146 个文件相对基线行为等价，无公开 API 丢失。差异只有有意的品牌化改名
  （`MaeveAccessibilityService`、`Author.Maeve` 带 `@SerialName("Maeve")` 序号不变、`Slash`→`SlashRole` 等）。
- **core 并发原语**：`@Volatile` / `@Synchronized` / `Mutex` / 结构化并发基本到位，
  没有用全局协程作用域、无未关闭资源、无协程泄漏。
- **app 生命周期收尾**：wake lock 的 `finally` / `invokeOnCompletion`、FGS 双路径、
  `TriggerAlarmReceiver` 的 `goAsync`、ViewModel 单例只持 Application。
- **`runBlocking` 仅两处且都安全**：`PcmRecorder.stop()` 的调用方在 IO scope 内，
  `SandboxBridge.serve()` 跑在专用守护线程。

## 7. 修复优先级建议

1. 第 4.7 节通知 ID 撞号——一行改动，先做。
2. 第 3 节六条主线程 IO——直接对应用户可感知的启动速度与流畅度，属性能优化范围。
3. 第 4.1 节账本淘汰——长时间运行后唯一会实质劣化的问题（内存 + 磁盘 + 延迟）。
4. 第 4.2 / 4.3 节写入方持锁与上限——数据一致性，非逃逸。
5. 其余按需。

## 8. 步骤 4 真机验证追加发现（2026-11-09 夜间）

装机走查（冷启动、抽屉、记忆、定时任务、设置、关于、发送拦截）本身无崩溃无 ANR，但以下三条是验证过程中新暴露的，均已处理：

### 8.1 开机广播每次必崩（P0，已修复并重启实测）

`BootRestoreReceiver` 与父类 `BootRestoreHandler` 都调了 `goAsync()`。AOSP 语义是**取一次即清空**：
第二次调用返回 null，对 null 调 `finish()` 必 NPE。父类第 16 行已取走唯一的
PendingResult，子类 32 行再取得到 null。这不是偶发——**每次开机广播必崩**（安装 APK 后
ROM 补发 `BOOT_COMPLETED` 也触发，实测复现，进程被杀）。

修复：父类持有唯一 `goAsync()`/`finish()` 骨架，子类改挂 `accepts()` /
`restoreInBackground()` 两个钩子；恢复与重排由原来的两个协程并行改为同一协程串行，
全部完成后才 `finish()`，广播 10 秒预算同时覆盖两边。重装后 60 秒观察 + `adb reboot`
复验：boot_completed 20s，零 FATAL、零 ANR、零 MaeveTrigger 错误。

### 8.2 步骤 3 的挂起化改造引入 28 个测试回归（已修复）

3.4 节把 `openConversation`/`switchTo`/`refresh` 的读盘包进 `scope.launch` +
`withContext(Dispatchers.IO)`。`Dispatchers.IO` **逃逸测试调度器**：测试的
`runCurrent()` 等不到真实 IO 线程上的续体，28 个 ChatViewModelTest 失败（症状都是
「列表为空/消息不出现」）。基线实测：纯净树上该类 107/107 通过，回归由本次改动引入。

修复（不改变公开行为，只改内部结构）：
- 三个入口改 `suspend fun`，调用方在自己的协程里调——与既有的 `branchFrom`（也是
  suspend、UI 侧 `scope.launch` 包一层）模式一致；测试里直接调用即挂起等完成。
- 读盘一律走构造函数里**已存在**的 `io` 参数（原先只有分支复制在用），测试注入
  `StandardTestDispatcher(testScheduler)`，与 `ModelSetupViewModelTest` 的做法一致。
- `onState` 尾部的 `syncSaved` 保持嵌套 launch，依赖 `runCurrent()` 的传递排空
  （实测 1.10.2 语义：同虚拟时刻新入队的任务会被同一次 runCurrent 跑掉）。

修复后 ChatViewModelTest 107/107 通过；全量 `:app:test` 失败集合与纯净基线**名称级
一致**（60 个存量 Windows 平台失败：rootfs 解包、proc 启动、沙箱契约等，与 core 侧
45 个失败同源），零新增零消失。

### 8.3 MaeveMark 空闲 CPU（已优化，78% → 17%）

欢迎页/空对话页常驻的呼吸标志每帧重建 362 段 Path（181×2 圈）+ 最多 300 个独立
`drawCircle` 微粒，按刷新率全量重绘，空闲页烧掉约 78% 单核。改为抽帧驱动
（`produceState` + `withFrameNanos`，相位按墙钟取模）：呼吸 12fps、流动 30fps，
降到 17%。framestats 实测 10.6fps，采样点密度对 2400ms 周期的余弦起伏足够
（24 个采样点，线性插值误差 <1% 幅度）。

对照实验闭环：同一页面 `settings put global animator_duration_scale 0` 令
`reduceMotion=true`（相位恒 0）后复测，30s 空闲 CPU 仅 1.7%，与设置页（2%）、桌面
（2%）一致——**剩余燃烧 100% 来自该动画本身**，无其他常驻消耗。像素 diff 佐证：
动画开启时两帧差异集中在标志区域（y≈590-808），关闭时仅状态栏时钟变化。

### 8.4 本轮主动放弃的项（有记录，非遗漏）

- **Outbox 终态淘汰**（4.1）：淘汰会破坏「恰好一次」去重窗口，属行为变更，不在性能优化范围内。
- **APK 615MB**：全部来自 debug Flutter 引擎构建配置，与本次重写无关。
- **微粒合批 / alpha 量化**：会改变重叠微粒的合成像素，本机无法肉眼验证渲染细节，只做解析上可论证视觉等价的改动。

### 8.5 带用户消息的对话一开即崩：SubcomposeLayout 不接受固有测量（已修复）

装机走查只走了空对话与「发送被拦截」路径。新对话发出第一条消息后复验「你好」对话时
必崩：`IllegalStateException: Asking for intrinsic measurements of SubcomposeLayout
layouts is not supported`。完整堆栈自外向内还原出层级：

1. 聊天 `LazyColumn` 的 item → 原 Compose 的 ActLine
2. 原 Compose 的 ActLine = `Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min))` —— 对内容发起 minIntrinsicHeight 查询
3. 内容列 `Box(Modifier.weight(1f))` → 原 Compose 的 UserBubble 的 `BoxWithConstraints`（SubcomposeLayout）
4. `BoxWithConstraints` 的 measure policy 是 NoIntrinsicsMeasurePolicy（Compose 内部类，
   不出现在任何 import 里），被固有测量击中即抛

判定为**存量缺陷**而非本次改造引入：已退役的 ChatScreen.kt 不在本次会话改动清单，且此前设备上
从未打开过带用户消息的对话，该路径从未被触发。同文件另两处 `IntrinsicSize` 宿主
（`MarkdownText` 引言行、`StepItem`）子树里没有 SubcomposeLayout 组件，无风险。

修复：原 Compose 的 ActLine 改自定义 `Layout`（自己量、自己放，`layoutId` 分幕号列/竖轨/内容三个
slot），从根上不再对内容发起任何固有查询——竖轨高度改由「内容高度 − 幕号列高度」推导，
与原 `IntrinsicSize.Min` 的视觉效果一致。复验：「你好」对话完整渲染（你好/你好呀/
第 01 轮），上下滚动重测量、返回再进均正常，整场零 FATAL。
