<!-- deep-ptc:project-cards v1 next=R3,F8,Q8,T5 -->
# Project cards

> Maintained by Deep PTC in DeepSeek Harness: edit in a Deep PTC conversation, view 特点卡片 / Project cards.
> User-approved settings guide this project. Features and requirements are records that can be checked and corrected.

## Amoo 自动签到
<!-- card id=amoo kind=setting category=product rev=1 updated=2026-11-25T02:38:32.518Z -->

- **R1** StepX 账号页 → Amoo 成长计划页的自动签到实例每天 09:00（Asia/Shanghai）执行。
- **R2** Amoo 自动签到优先使用已配置且可用的 API 视觉；API 视觉不可用时回退到 UI 签到。


## Amoo 自动签到
<!-- card id=requirements kind=requirements rev=3 updated=2026-11-25T03:58:22.114Z -->

- **Q1** [done] StepX 账号页 → Amoo 成长计划页的自动签到实例每天 09:00（Asia/Shanghai）执行。
- **Q2** [done] Amoo 自动签到优先使用已配置且可用的 API 视觉；API 视觉不可用时回退到 UI 签到。
- **Q3** [done] 从定时任务页点击立即执行后，自动打开该计划所属会话任务页。
- **Q4** [done] StepX 进入 Amoo 成长计划后的动作画面必须保留在操作回放中。
- **Q5** [done] 操作回放按调用发生顺序排列，StepX 在前、Amoo 成长计划在后，包含进入后的关键画面。
- **Q6** [done] 分享图支持选择帧，默认使用当前回放页面对应帧。
- **Q7** [done] Amoo 任务通过 UI 树读回确认时，结算显示证据已核实。


## Amoo 自动签到
<!-- card id=features kind=features rev=3 updated=2026-11-25T03:58:22.122Z -->

- **F1** 内置 Amoo 定时实例：TriggerPreset.builtInSchedule 定义稳定 ID/线程、每日 09:00 Asia/Shanghai 与 fixedZone；TriggerRuntime.ensureBuiltInSchedules 在应用启动两条路径只播种一次，示例创建复用同一计划。验证：TriggerPresetTest、FileTriggerStoreTest、Flutter settings_test.dart。
- **F2** 固定签到执行：AmooCheckIn 从 StepX 账号页导航到成长计划，视觉定位优先且支持无树/未验证点，视觉失败回退精确 UI 树；已签到不点击，签到后读回，防重复点击。TaskRun 允许无主模型执行。验证：AmooCheckInTest 9 项、core 全量 3222 项、Flutter 27 项、Android compileDebugKotlin。
- **F3** 定时执行跳转：FlutterScheduleSettings 快照返回 threadId；ScheduledTasksPage 在 runNow 后按计划 ID 找线程，弹出设置页并调用 MaeveController.openThread。验证：Flutter settings_test.dart 与设备 UI 点击验证。
- **F4** Amoo 回放帧：仅 amoo_check_in 单命令退出 textOnly；ToolTools 对 Amoo 预设强制 inlineScreen，ActionRecorder 保存进入后画面。验证：AmooCheckInTest 精确断言 phone_tap.screenshotId 与 Media；设备运行 20261125-113118-xgsher 保存 3 帧，回放页显示第 3/3 帧。
- **F5** 回放排序：TaskReportViewModel.playbackSteps 按 callId/媒体在 events 中的首次出现顺序排序，不再按截图编号排序。验证：TaskReportViewModelTest 与旧设备记录 20261125-113118-xgsher。
- **F6** 分享选帧：ExecutionPanel 持有当前回放帧索引并传给 CompletedExecutionPanel；TaskSharePage 默认接收该索引并提供缩略图选帧，poster 文案随帧更新。验证：execution_workspace_test 与设备 UI。
- **F7** Amoo 核查：AmooCheckIn 返回 readback=ui_tree/vision；AgentLoop 对 UI 树读回记录 LocalReview(Verified, 1/1)。验证：AmooCheckInTest、core 3224 项、App 全量单测、Flutter 43 项，设备新任务显示证据已核实。


## Completed tasks / 已完成任务
<!-- card id=tasks kind=tasks rev=4 updated=2026-11-25T03:58:22.140Z -->

- **T1** [done] 在项目现有定时任务中新增一个实例 case：进入 StepX 账号页，再到 Amoo 成长计划页面完成自动签到。 — 已新增每日 09:00 的 Amoo 内置定时实例，完成视觉优先/UI 兜底、无主模型运行与回归验证；真机仪器测试因 Maeve Dev 助手未配对而受阻。 (11/25)
- **T2** [done] 将本次 Amoo 修改直接覆盖安装到当前 Maeve 包，不新建 Dev 版本。 — 已用相同 Debug 签名将新 APK 覆盖安装到当前 com.daiyimo.maeve，保留数据；Dev 包不存在，应用启动验证 Status: ok。 (11/25)
- **T3** [done] 点击定时任务的“立即执行”后，自动跳转到本次任务页面。 — 已修复定时执行自动跳转与 StepX→Amoo 回放缺帧；core 3223、Flutter 27 全通过，覆盖安装当前 Maeve 并设备实测回放 3 帧。 (11/25)
- **T4** [done] 修复新执行任务的回放帧顺序与缺失问题，确保 StepX 在前、Amoo 成长计划在后，不回跳。 — 已修复回放倒序/缺帧、分享图当前帧选择与 UI 树读回核查状态；core 3224、App 全量、Flutter 43 通过，覆盖安装当前 Maeve 并完成设备验收。 (11/25)
