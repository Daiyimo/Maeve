# 金铲铲 run 点击异常与耗时诊断（2026-11-26）

## 样本

- Run：`20261126-113254-qm9iy2`
- 目标：打开金铲铲、进入游戏、关闭开屏弹窗、查看魔典收集度
- 模型：`step-3.7-flash`
- 结果：`succeeded`，26 步，30 次模型调用
- 总耗时：约 239 秒（含收尾事件约 243 秒）
- 最终答案：魔典收集度 6%；但 `localReview=Insufficient`，说明数字只来自截图，没有文本证据锚点

## 耗时分布

| 类别 | 耗时 |
|---|---:|
| 模型延迟合计 | 114.5s |
| 工具执行合计 | 93.1s |
| 其中 `toolBody` | 71.1s |
| 其中视觉模型 | 33.1s |
| 其中目标解析 | 22.0s |
| 其中动作后读屏 | 13.3s |
| 其中动作前读屏 | 8.7s |
| 其中策略/审批 | 8.3s |
| 其中显式 `wait` | 19.0s |

## 点击异常真相

本次共有 13 次 `phone_tap` 调用：

- 7 次真正下发，全部 `changed=true`，没有确认到“点下去但点错地方”的成功动作。
- 4 次 `element_not_found`，每次约 5 秒：step 6、9、15、17。
- 1 次 `tap_text_mismatch`：step 10 估成 `y=630`，实际“开始游戏”中心是 `y=832`；守卫拦下，没有下发。
- 1 次 `invalid_arguments`：step 21 同时传了 `element` 和 `x`，没有下发。

因此用户看到的“很多点击异常”主要是**失败的目标解析和黑屏/旧图后的坐标猜测**，不是已执行的乱点。

## 根因

### 1. 视觉优先模式仍接受 `element`，并触发昂贵的第二遍视觉定位

金铲铲是视觉优先 App：`treeQuality=unusable`、`elements=[]`。但 `phone_tap` 的 schema 仍提供 `element`，描述也把它写成正常目标方式。模型因此 4 次传 `element`；运行时先做 OCR，未命中后再调用视觉模型，最终撞到 5 秒超时。

这 4 次共浪费约 20 秒工具时间，连同对应模型延迟约 38 秒。

### 2. `phone_screenshot` 直接取原始帧，会把过渡黑屏当成功截图

截图样本中多次出现全黑或几乎不可读的过渡帧（如 `0006.jpg`、`0008.jpg`、`0010.jpg`、`0012.jpg`）。`phone_screenshot` 走的是 `screenshotPng()`，没有等待画面稳定和非黑帧；模型收到黑屏后只能继续等、再截图或猜坐标。

### 3. 主模型已经看图时，`phone_describe` 又调用一次独立视觉模型

step 13 的 `phone_describe` 单次耗时 **25.8 秒**，其中视觉模型 24.7 秒。视觉优先模式下，主模型已经从 `phone_observe`/`phone_screenshot` 收到同一张图，再问第二个视觉模型属于重复劳动。

### 4. 坐标点击没有绑定截图 freshness

模型拿旧图坐标点新页面时，只有文本目标会被 `tap_text_mismatch` 保护；图标或描述目标没有通用保护。本次 step 10 的错坐标正好被文本守卫拦下，但这不是稳定保证。

## 修复

- 视觉优先模式下，`element` 只做 OCR 精确匹配；未命中立即返回 `visual_first`，明确引导使用 `x+y + target` 或 `phone_find_visually`，不再调用第二遍视觉模型。
- `ScreenPort` 新增 `screenshotPngStable()`；Android 实现等待画面稳定且非黑，截图工具和无 OCR 的视觉取帧统一走稳定链路。
- 主模型能看图且处于视觉优先模式时，`phone_describe` 直接快速失败，避免重复视觉调用。
- 更新视觉优先提示词和 `phone_tap.element` 参数说明，明确视觉优先页不要传 `element`。
- `AppAccessScreenPort` 显式转发新增端口方法，保持访问边界测试完整。

## 回归覆盖

- 视觉优先 `element` 未命中：零视觉模型调用、零点击、快速返回 `visual_first`。
- 视觉优先 OCR 命中：仍可解析并执行，不调用视觉模型。
- `phone_screenshot`：必须走稳定帧接口，不读无障碍树。
- 视觉优先 `phone_describe`：主模型能看图时不触发第二遍视觉模型。
- 全量 core：3273 项通过，52 skipped。
- `:app:compileDebugKotlin`：通过。
- 源码体量门禁：1657 个文件通过。

## 复测结果

### 第一轮复测：`20261126-123520-rg9u5s`

- 总耗时：约 135 秒（含收尾事件约 150 秒），原始 run 为约 239 秒。
- 步数：20。
- `element_not_found`：0。
- `phone_describe`：0。
- `target_unresolved`：0。
- 视觉优先 `element` 误用：2 次，均在约 1.7 秒内快速返回 `visual_first`，不再消耗 5 秒视觉超时。
- 最终停在登录页，未能继续查看魔典；这是账号状态限制，不是点击工具异常。

### 第二轮发现

第一轮复测后继续检查发现三个遗漏：

1. `phone_find_visually` 的 `Locator` 仍直接取原始帧，没有走稳定截图链路，仍可能在新手引导/转场时撞到 8 秒视觉超时。
2. 自主模式下，如果视觉优先页 OCR 暂时不可用，`ConfirmDecision` 会提前返回 `target_unresolved`，即使坐标来自主模型刚看到的截图也会被拦下。
3. `checkTextPoint` 在已经有新鲜 OCR 时仍做第二次读取；第二次读不到就会放过矛盾坐标。另缺少中文“页签”的 OCR 快捷匹配，导致“召唤页签”这类目标仍会调用视觉模型。

### 追加修复

- `Locator.capture()` 在视觉优先模式改用 `screenshotPngStable()`。
- 自主模式下 OCR 不可用不再阻断普通坐标点击；不可逆动作和硬规则仍先拦截。
- `checkTextPoint` 优先复用动作前刚读到的 OCR，只有为空时才重新读取。
- OCR 快捷角色补充“页签/标签”，避免“召唤页签”这类文本目标走 8 秒视觉定位。
- 对应回归测试已加入。

### 最终复测与剩余问题

最终 run `20261126-124921-dew53n` 在进入“赛事中心/荣誉殿堂”后，模型连续使用无效的返回/关闭坐标，陷入导航循环，已在 35 步、约 277 秒时手动停止。

这次没有出现：

- `element_not_found`
- `phone_describe`
- `target_unresolved`

剩余失败均为执行前拦截：

- `ref_expired`
- 模型把像素坐标 `1335` 当成 0–999 归一化坐标
- `tap_text_mismatch`（错误 y 坐标被 OCR 守卫拦下）

说明工具契约、稳定截图和超时路径已经修复；当前主要瓶颈变成**复杂游戏活动页的模型导航能力**：模型会把 `small_change` 当成进展，反复尝试不同的返回/关闭坐标，而不是识别当前页面后换路线。这是下一轮需要从任务规划/页面状态识别角度处理的问题，不应再靠放宽点击守卫来解决。

## 最终验证

- 全量 core：3277 项通过，52 skipped。
- Android 单测：1701 项通过，62 skipped。
- `:app:compileDebugKotlin`：通过。
- Debug APK 构建并覆盖安装到 adb 设备：通过。
- 源码体量门禁：1658 个文件通过。
- `git diff --check`：通过。
