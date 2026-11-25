<!-- deep-ptc:project-cards v1 next=R3,F11,Q11,T10 -->
# Project cards

> Maintained by Deep PTC in DeepSeek Harness: edit in a Deep PTC conversation, view 特点卡片 / Project cards.
> User-approved settings guide this project. Features and requirements are records that can be checked and corrected.

## Amoo 自动签到
<!-- card id=amoo kind=setting category=product rev=1 updated=2026-11-25T02:38:32.518Z -->

- **R1** StepX 账号页 → Amoo 成长计划页的自动签到实例每天 09:00（Asia/Shanghai）执行。
- **R2** Amoo 自动签到优先使用已配置且可用的 API 视觉；API 视觉不可用时回退到 UI 签到。


## Amoo 自动签到
<!-- card id=requirements kind=requirements rev=10 updated=2026-11-25T06:18:56.487Z -->

- **Q1** [done] StepX 账号页 → Amoo 成长计划页的自动签到实例每天 09:00（Asia/Shanghai）执行。
- **Q2** [done] Amoo 自动签到优先使用已配置且可用的 API 视觉；API 视觉不可用时回退到 UI 签到。
- **Q3** [done] 从定时任务页点击立即执行后，自动打开该计划所属会话任务页。
- **Q4** [done] StepX 进入 Amoo 成长计划后的动作画面必须保留在操作回放中。
- **Q5** [done] 操作回放按调用发生顺序排列，StepX 在前、Amoo 成长计划在后，包含进入后的关键画面。
- **Q6** [done] 分享图支持选择帧，默认使用当前回放页面对应帧。
- **Q7** [done] Amoo 任务通过 UI 树读回确认时，结算显示证据已核实。
- **Q8** [done] 新任务提交前，Flutter 空会话壳必须与原生当前会话完成同步；不得仅因本地 activeId 为空就跳过宿主新建握手。
- **Q9** [done] 任务分享图左侧必须展示精简提炼后的回答，优先完整保留实际体验码并清理多余空格，显示约 6 行，同时保留结算状态、执行步数、任务耗时和现有选帧能力。
- **Q10** [done] 应用必须提供易于找到的隐私声明，准确说明对话、屏幕内容和截图的数据去向、本机保存与主动接入的第三方通道。


## Amoo 自动签到
<!-- card id=features kind=features rev=8 updated=2026-11-25T06:18:45.627Z -->

- **F1** 内置 Amoo 定时实例：TriggerPreset.builtInSchedule 定义稳定 ID/线程、每日 09:00 Asia/Shanghai 与 fixedZone；TriggerRuntime.ensureBuiltInSchedules 在应用启动两条路径只播种一次，示例创建复用同一计划。验证：TriggerPresetTest、FileTriggerStoreTest、Flutter settings_test.dart。
- **F2** 固定签到执行：AmooCheckIn 从 StepX 账号页导航到成长计划，视觉定位优先且支持无树/未验证点，视觉失败回退精确 UI 树；已签到不点击，签到后读回，防重复点击。TaskRun 允许无主模型执行。验证：AmooCheckInTest 9 项、core 全量 3222 项、Flutter 27 项、Android compileDebugKotlin。
- **F3** 定时执行跳转：FlutterScheduleSettings 快照返回 threadId；ScheduledTasksPage 在 runNow 后按计划 ID 找线程，弹出设置页并调用 MaeveController.openThread。验证：Flutter settings_test.dart 与设备 UI 点击验证。
- **F4** Amoo 回放帧：仅 amoo_check_in 单命令退出 textOnly；ToolTools 对 Amoo 预设强制 inlineScreen，ActionRecorder 保存进入后画面。验证：AmooCheckInTest 精确断言 phone_tap.screenshotId 与 Media；设备运行 20261125-113118-xgsher 保存 3 帧，回放页显示第 3/3 帧。
- **F5** 回放排序：TaskReportViewModel.playbackSteps 按 callId/媒体在 events 中的首次出现顺序排序，不再按截图编号排序。验证：TaskReportViewModelTest 与旧设备记录 20261125-113118-xgsher。
- **F6** 分享选帧：ExecutionPanel 持有当前回放帧索引并传给 CompletedExecutionPanel；TaskSharePage 默认接收该索引并提供缩略图选帧，poster 文案随帧更新。验证：execution_workspace_test 与设备 UI。
- **F7** Amoo 核查：AmooCheckIn 返回 readback=ui_tree/vision；AgentLoop 对 UI 树读回记录 LocalReview(Verified, 1/1)。验证：AmooCheckInTest、core 3224 项、App 全量单测、Flutter 43 项，设备新任务显示证据已核实。
- **F8** 新会话提交同步：Flutter 空壳仍持有旧宿主会话 ID 时，create() 必须调用宿主新建握手；仅在宿主也为空时直接早退，避免本地/原生会话 ID 不一致导致提交被拒。验证：chat_refresh_regression_test 回归用例、会话与提交相关 Flutter 22 项、assembleDebug、当前 com.daiyimo.maeve 真机新消息被接收并完成任务。
- **F9** 分享图回答摘要：TaskSharePage 左侧新增“回答”，shareAnswerExcerpt 清理 Markdown/多余空格，识别两个及以上体验码并逐行保留实际码值；其他回答保留首个有效结论并按句界截断，显示最多 6 行。验证：execution_workspace_test 19 项、flutter analyze、assembleDebug，设备“归藏”case 显示 7HGJ5V 与 537ZMW 且海报无溢出。
- **F10** 隐私声明：新增独立 PrivacyPage，统一说明模型处理时对话/屏幕内容/截图只发往用户配置的模型服务商、Maeve 不收集/出售/用于广告、记录与截图默认本机保存，以及主动使用 Amoo/微信等第三方通道的例外。设置、模型配置与新手引导均提供直达入口。验证：settings_test 与 model_config_revision_test 共 35 项、flutter analyze、assembleDebug、当前包真机页面。


## Completed tasks / 已完成任务
<!-- card id=tasks kind=tasks rev=9 updated=2026-11-25T06:18:45.653Z -->

- **T1** [done] 在项目现有定时任务中新增一个实例 case：进入 StepX 账号页，再到 Amoo 成长计划页面完成自动签到。 — 已新增每日 09:00 的 Amoo 内置定时实例，完成视觉优先/UI 兜底、无主模型运行与回归验证；真机仪器测试因 Maeve Dev 助手未配对而受阻。 (11/25)
- **T2** [done] 将本次 Amoo 修改直接覆盖安装到当前 Maeve 包，不新建 Dev 版本。 — 已用相同 Debug 签名将新 APK 覆盖安装到当前 com.daiyimo.maeve，保留数据；Dev 包不存在，应用启动验证 Status: ok。 (11/25)
- **T3** [done] 点击定时任务的“立即执行”后，自动跳转到本次任务页面。 — 已修复定时执行自动跳转与 StepX→Amoo 回放缺帧；core 3223、Flutter 27 全通过，覆盖安装当前 Maeve 并设备实测回放 3 帧。 (11/25)
- **T4** [done] 修复新执行任务的回放帧顺序与缺失问题，确保 StepX 在前、Amoo 成长计划在后，不回跳。 — 已修复回放倒序/缺帧、分享图当前帧选择与 UI 树读回核查状态；core 3224、App 全量、Flutter 43 通过，覆盖安装当前 Maeve 并完成设备验收。 (11/25)
- **T5** [done] 查清通过 ClawBot 打招呼时回复“暗号甲”的来源，判断是否来自 Maeve 与 Amoo 的桥接。 — 确认“暗号甲”不是 Maeve 桥接固定文案；Maeve 仅转发用户消息与权限说明，回复由系统 Amoo/ClawBot 模型或会话提示生成。 (11/25)
- **T6** [done] 定位新任务出现“宿主未接收，草稿已保留，请检查前置条件”的原因，并判断是否与 Amoo 桥接 ClawBot 后台执行未 — 定位为本地/宿主会话同步问题而非 Amoo 资源未释放；修复空壳新建同步、22 项测试通过并覆盖安装当前 Maeve，真机发送完成任务。 (11/25)
- **T7** [done] 在任务分享图左侧新增“回答”内容，对任务回答进行精简提炼，参考刚执行的“归藏”case 的分享效果。 — 分享图左侧新增清理 Markdown 并按句界截断的回答摘要；18 项测试、静态检查和构建通过，覆盖安装当前 Maeve 并用“归藏”case 真机验证。 (11/25)
- **T8** [done] 修正分享图回答摘要：必须保留并突出实际解出的两个体验码，清理多余空格，并比当前多显示两三行。 — 修正分享摘要为优先提取并逐行显示实际体验码、清理空格并扩展至 6 行；19 项测试、静态检查、构建及“归藏”真机预览通过。 (11/25)
- **T9** [done] 在 Maeve 中添加隐私声明：用户发给 Maeve 的对话、屏幕内容和截图只发往用户自己配置的模型服务商，Maeve — 新增独立隐私声明页及设置/模型配置/新手引导三个入口；35 项测试、静态检查、构建和当前包真机验证通过。 (11/25)
