# amoo 任务截获（Amoo Intercept）Spec

> 状态：已实现并真机验证通过（2026-11-05），同日完成代码审查与修复（闸门 1、线程、单次遍历、日志、误建防护）；并入上游 v0.1.36 后无需改动即适配（编译、装机包均通过）
> 相关提交：`a6402f19`（功能实现）、`df595845`（真机不触发修复）、`d3ebc904`（代码审查修复：闸门 1 复原、主线程限定、单次遍历、日志去噪、误建防护）、`3a3165f7`（合并 upstream/main，唯一冲突在 MaeveApp.kt，两侧并存）
> 本文档目的：自包含说明「做了什么、为什么、怎么验证」，供交叉验证 agent 独立复现与审查。

---

## 1. 背景与问题

amoo（`com.stepos.ailauncher`，StepOS 系统签名应用）里用户发送的消息，路由实锤如下：

- amoo 端侧有 SLA 路由层（`com.stepos.ailauncher.sla`）：`onTextSend` 时端侧模型输出 ability 标签（如 `<request_cloud_assist>`）。
- `sla/w.smali`：`slaUnsupported = {request_cloud_assist, request_gui_assist}` —— 这两个标签端侧 SLA **不执行**（code=280003 丢弃）→ "无可执行动作 -> fallback" → "原通路胜出 -> 放行后端" → `chat.send` → AgentBus → **云端**。
- 结果：amoo 里发的任务消息全部走云端，本机的 Maeve（定位"本机操作手"）被绕过，接到不 amoo 的任务。

约束：无 root 改不了 amoo/AgentBus 的路由；amoo 是系统签名应用无法替换。唯一可行方向是 **Maeve 侧接**。

## 2. 方案

Maeve 的无障碍服务持续观察 amoo 聊天页：用户每发出一条消息（输入框草稿 + 同文本气泡双重认定），就按 MCP `mika_run_task` 的同一底层路径 `RemoteTaskService.start(goal, null, requestId)` 在 Maeve 里新建一个任务，胶囊照常执行、任务页可见可取消。

- amoo 发往云端的消息**不受影响**，照常发送——Maeve 是在本机并行做一遍，不是拦路。
- 默认关闭。**界面上已经不再暴露这个开关**（Amoo 页现在只留「一键接入」和「连接状态」），要打开只能改 `feature_flags` 里的 `intercept_amoo`（`FeatureFlags.setInterceptAmoo`，改开关即时生效：FeatureFlags Flow）。连接电脑页也不再有 amoo 相关条目。
- amoo 每发一条消息就在 Maeve 里新建一个**独立对话**（`conversation=null` → `newChat`，conversation 形如 `mcp-<uuid>`），多条消息各自成任务、可并行。
- 四层闸门，任一不过就不算一次可截获的发送（宁可漏，不可误建）：
  1. 前台是 amoo 聊天页：pkg 必须是 `com.stepos.ailauncher`，且 className 含 `chat`；不知道 className（服务重连后只知道包名）时按包名认，但**已知是 amoo 的其他页面（设置页、搜索页）时不认**；
  2. 输入框出现过非空草稿且 < 10s 新鲜（`MAX_DRAFT_AGE_MS`）；
  3. 这段输入开始打之前，聊天记录里**没有**同文本的气泡（防重打一句历史里说过的话——如「继续」——立刻误建任务）；
  4. 同文本 60s 去重（`DEDUP_WINDOW_MS`，防同一条气泡触发几十次窗口事件时一锅端）。

## 3. 代码变更清单

### 3.1 新增

| 文件 | 职责 |
|---|---|
| `app/src/main/java/com/daiyimo/maeve/intercept/AmooSendDetector.kt` | 纯 Kotlin 状态机（无 Android 依赖）：前台状态、草稿寿命、去重队列；`isAmooChat` / `noteInputText` / `pendingDraft` / `markIntercepted` / `noteAmooForeground` / `reset` |
| `app/src/main/java/com/daiyimo/maeve/intercept/AmooIntercept.kt` | Android glue：订阅开关 Flow、处理无障碍事件、读无障碍树、命中后起任务 |
| `app/src/test/java/com/daiyimo/maeve/intercept/AmooSendDetectorTest.kt` | 18 个单测（草稿生命周期、非 amoo 忽略、去重窗口、retype 语义、前台同步、闸门 1 不被补记冲掉等） |
| `tools/add_locale_strings.py` | 10 个 strings_mcp.xml 缺失键补齐脚本（记录字符串来源） |

### 3.2 修改

| 文件 | 改动 |
|---|---|
| `MaeveApp.kt` | 持有 `amooIntercept` 单例 |
| `a11y/MaeveAccessibilityService.kt` | `onServiceConnected` 转发给截获模块做前台同步；`onAccessibilityEvent` 转发事件 |
| `settings/FeatureFlags.kt` | `intercept_amoo` 开关（默认 false）+ `interceptAmooChanges(): Flow<Boolean>` |
| `ui/AmooScreen.kt` | 设置 → 常用 → Amoo：只剩「一键接入」（`AmooSetup`）与「连接状态」两张卡；截获开关已从界面撤掉 |
| `app/src/main/res/values*/strings_mcp.xml`（10 个语言） | `amoo_intercept_title/sub/note` 三键（含中文），顺带补齐遗留缺失的 `stepclaw_*` 9 键 |

## 4. 关键实现细节（踩过的坑，交叉验证重点）

1. **StepOSFreeze 每 ~20 秒断开重绑一次无障碍服务**（logcat `MaeveA11y: accessibility service unbound/connected` 成对出现）。断开期间的窗口切换事件全部丢失。若前台状态只靠 `TYPE_WINDOW_STATE_CHANGED` 推进，amoo 早在服务重连前就是前台，之后永远发现不了 → **`onServiceConnected` 必须主动读 `rootInActiveWindow` 补记**（`noteAmooForeground`），scan 内 root 包名是 amoo 时也自愈补记。`isAmooChat` 允许 className 为空按包名兜底，但补记只在「完全不知道前台」或「前台还是别的 App」时生效——已知是 amoo 的设置页/搜索页时补记不改写前台，否则闸门 1 退化成只看包名（代码审查中发现并修复）。
2. **输入法窗口事件会冲掉前台**：本机输入法为搜狗 `com.sohu.inputmethod.sogou.oem`，窗口 className 是 `android.inputmethodservice.SoftInputWindow`——**不含输入法常见字样**。guard 需同时匹配类名里带 softinput 的窗口与包名含 inputmethod 的窗口。Maeve 自己的浮层窗口事件（`pkg=com.daiyimo.maeve`）同样冲前台，一并忽略（scan 的 root 包名检查兜底防误判）。
3. **amoo 输入框无障碍特性**：class 是 `EditText` 但 `editable` 属性缺失 → 取草稿需类名兜底（`looksLikeInput`）；**空态时 `text` 属性返回 hint**（"发消息或按住说话"）→ 取草稿必须过滤 `hintText`；气泡认定必须排除 `looksLikeInput` + `isFocused` 节点，否则打字过程中输入框文本==草稿会误触。
4. **scan 节流 300ms**（`SCAN_INTERVAL_MS`）；root 包名是此刻真前台的唯一可信来源（窗口状态事件可能是旧的）。窗口状态事件在记完前台后**顺带扫一次**，切进 amoo 聊天页不用等下一次内容变化。
5. **整棵树只走一遍**：`descendants(root)` 一次深度优先收集节点，输入框文本、可编辑节点回退、气泡认定共用这份列表；`descendants` 不含 root，取出来的节点和 `rootInActiveWindow` 都由取的人 recycle（minSdk 30 不回收会漏原生资源；recycle 到 API 33 才废弃）。
6. **线程**：detector 的状态（含双端队列）只在主线程读写——无障碍回调本就在主线程，开关 Flow 与重连补记也回到 `Dispatchers.Main.immediate`；起任务切 `Dispatchers.IO`。早期实现用 `Dispatchers.Default` 收开关，与主线程的 scan 并发改同一个双端队列。
7. **误建防护（基线）**：每换一段输入，先记下「这段文字在开始打之前是否已在聊天记录里」；是的话这一轮不认领。否则重打一句历史里出现过的话（「继续」「好」）会在点发送之前就建任务。发送时输入框清空（`noteInputText` 忽略空串）→ 草稿与基线都保留 → 气泡从无到有才认领。
8. **日志去噪**：`scan:`（D 级）只在输入/草稿**长度变化**时各打一行，不写用户输入原文；命中时打 `bubble matched, intercepting <n> chars`、`amoo task intercepted: <n> chars`，任务 id 见 `intercepted task started: <uuid>`。

## 5. 验证记录（真机 ZYPZ261A01M68700159）

### 5.1 自动化验证

- `./gradlew :app:testDebugUnitTest --tests "com.daiyimo.maeve.intercept.*"` → 18/18 通过。
- `./gradlew :app:assembleDebug` → 通过；`adb install -r app-debug.apk` → Success。

### 5.2 E2E 实锤（两次，均成功）

流程：amoo 聊天页输入 ASCII 测试文本 → 点 `btn_text_send` → logcat 抓 `AmooIntercept` → MCP `mika_list_tasks` 验证。

日志（`adb logcat -v time` 过滤 `AmooIntercept:I`）：

```
11-05 11:58:00.977 I/AmooIntercept: scan: bubble matched, intercepting e2eprobe999888
11-05 11:58:00.977 I/AmooIntercept: amoo task intercepted: e2eprobe999888
11-05 11:58:00.990 I/AmooIntercept: intercepted task started: 0e9b4abb-8233-488a-af4d-cdae91fdd27a
```

MCP 验证（`mika_list_tasks` 首条）：

```json
{"task_id":"0e9b4abb-8233-488a-af4d-cdae91fdd27a","status":"done",
 "goal":"e2eprobe999888","conversation":"mcp-e34fec94-...","started_at_ms":1793851080983}
```

task_id 与日志一致；goal 为 amoo 中发送的原文；`started_at_ms` 与日志时间吻合。
第二次回归（`regression000111`，12:01:13）同样成功，task_id `5e612a21-61de-44e2-a38b-33b2e4adbac6`。

### 5.3 前置条件

- Maeve 已安装且无障碍服务已开启（系统设置 → 无障碍 → Maeve）；
- 截获开关已打开：`adb shell run-as com.daiyimo.maeve cat shared_prefs/feature_flags.xml` 应含 `<boolean name="intercept_amoo" value="true" />`；
- amoo 聊天页在前台。

## 6. 交叉验证步骤（另一个 agent 照做即可）

```bash
# 1. 代码审查（两个提交的完整 diff）
cd /path/to/Maeve
git show a6402f19 --stat
git show df595845 --stat

# 2. 单元测试
./gradlew :app:testDebugUnitTest --tests "com.daiyimo.maeve.intercept.*"

# 3. 装机
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 4. 确认开关与 a11y 服务
adb shell run-as com.daiyimo.maeve cat shared_prefs/feature_flags.xml | grep intercept_amoo
adb shell dumpsys accessibility | grep "label=Maeve"   # Bound services 里应有

# 5. 拉起 amoo 聊天页（ChatActivity not exported，用 ChatMainActivity）
adb shell am start -n com.stepos.ailauncher/.chat.activity.ChatMainActivity

# 6. E2E：输入 ASCII 文本（注意：adb input text 空格后内容会丢，用无空格文本）
adb shell input tap <输入框坐标>        # 坐标用 uiautomator dump 现取
adb shell input text "verify000222"
adb shell uiautomator dump /sdcard/ui.xml   # 取 btn_text_send 坐标（输入后才出现）
adb shell input tap <发送按钮坐标>

# 7. 看日志（应出现 intercepted task started）
adb logcat -d -v time | grep AmooIntercept

# 8. MCP 验证任务在列（需先触发 USB 监听）
adb shell am start-foreground-service -n com.daiyimo.maeve/.mcp.McpService \
  -a com.daiyimo.maeve.mcp.USB_ON
adb forward tcp:18770 tcp:18770
# 然后走 MCP 协议调 mika_list_tasks（JSON-RPC over TCP，Content-Length 帧）
```

预期：日志出现 `amoo task intercepted: <字符数> chars` 与 `intercepted task started: <uuid>`；MCP 列表出现同 goal、同 task_id、status 为 `done` 的任务。

## 7. 已知限制与后续

1. **云端仍会并行执行一遍**：amoo 发后端无法阻止（系统签名 + 无 root）。截获是"本机并行做"，不是"改路由"。
2. **freeze 循环漏事件风险**：a11y 服务每 ~20s 断重连，断期间的窗口事件丢失。已用 connected 主动同步 + scan 自愈缓解；极端情况下（恰好断开时发送）可能漏截一次——下次窗口事件到来时草稿可能已超 10s 寿命。可接受（宁可漏不可误建）。
3. **去重窗口 60s**：同一文本 60s 内重发不会重复建任务（同一条气泡会触发大量窗口事件，必须去重；用户重发同样的话需等窗口过去）。
4. **诊断日志**：`win-state` / `foreground synced` / `baseline` / `bubble matched` / `intercepted task started` 为 I 级、低频；`scan:`（D 级）只在输入或草稿长度变化时打一行，且不含用户输入原文。
5. **未 push**：main 领先 origin/main 27 个提交（本功能 6 个 + 上游 v0.1.36 的 21 个），尚未 push 到 origin。
6. **每条消息一个独立对话**：`RemoteTaskService.start(goal, null, …)` 让 `RemoteTaskHub` 走 `newChat`，所以 Maeve 里是一串 `mcp-<uuid>` 对话，不会接在同一个对话里；多条消息会并行成多个任务。
7. **气泡文本必须完全相等**：amoo 把长消息拆成多个 TextView、或带富文本/表情时可能认不出来 → 漏截（只漏，不误建）。
8. **离开 amoo 不清空状态**：`reset()` 只在关掉开关时调用；离开 amoo 后残留的草稿靠 10s 寿命 + 基线规则过期，不再额外 reset（避免 amoo 内部弹窗把状态清掉反而漏截）。
