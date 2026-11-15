# 优化 spec v2 实施记录

对照基线：`7a29a8c0cddd3dfad533947c9f230d743afa0d53`。本批按 2026-11-14 版 spec 增量修改；下面状态是实现范围，不表示整份 spec 或公测门禁已完成。

| 项目 | 本批源码修改 | 尚需完成的验收/实现 |
| --- | --- | --- |
| S01 桥接 | bridgeVersion/capabilities/build；持久 UI 提交回执；摘要 IO；分域事件 revision/导航代次；主/子任务动作回执 | 精确 revision 快照/增量补差与未覆盖 mutation 的统一契约、完整后端/屏幕/intent 授权绑定和未知动作的独立证据对账仍需补齐 |
| S02 删除 | 完整取消并等终态后清理工作区、remote、草稿和动作回执；失败 pending/partial；持久 tombstone 与定期重试 | 磁盘故障/删除中杀进程的真机验收与 tombstone 压缩 |
| S03 发送/草稿 | accepted 后清对应草稿；按会话恢复正文/引用/原 requestId；私有原子持久化、版本 CAS、防迟到覆盖；unknown 先查回执；正在停止等原生终态 | 冷启动/权限修复/steer/answer 的完整真机矩阵；有界草稿数量满额的管理交互 |
| S04 构建 | debug/profile/release AAR 与输出仓库分离；perf 独立包名；产物比对脚本；工具链文档同步 | 发行签名、versionCode 门禁、同 hash 全新安装/升级、R8、helper epoch 和多设备验收仍未关闭 |
| S05 审批 | 外发/不可逆单次本地确认；远程/无人值守拒绝；动态撤销；60 秒/3 步；发送逐字比对，不截断授权；未知裸坐标拒绝，命名视觉目标需定位与复看 | 付款保留亲手接管；完整 screen/backend/intent 绑定、执行前撤销竞态和可信外部效果证明仍需补齐 |
| S06 应用/模型 | 真实应用访问页及 fail-closed；统一 DNS/route/HTTP 明示同意；模型/服务商删除、共享 key 提示；配置快照恢复入口 | Android 真路由/代理/TLS 及恢复权限的同任务设备验收；凭据缓存清理矩阵 |
| S07 配置/预算 | Missing/Valid/Corrupt/Unavailable；provider/roles/key journal 事务、发布在提交后、完整快照恢复；共享 60 步/10 分钟/120 逻辑与 HTTP 请求上限、可得 token 限额 | 配置全部故障注入/迁移/大文件限制；预算任务集校准；D05 密码加密备份未实现 |
| S08 文件桥/HTTP | claimed 恢复、稳定私有回执、严格有界 JSON、结果 TTL/字节限额；HTTP 总读取期限、TE/CL/trailer/来源并发限制；模型 SSE 行/事件/总响应限额 | 旧 ID 去重回执的长期生命周期及外部写入者堆积共享目录的边界仍需完善 |
| S09 诊断与性能 | 默认本地白名单诊断预览与复制；完整构建信息、更新/反馈入口；更新器摘要/大小/截止时间/ABI 门禁；标题搜索和惰性列表 | 完整操作自检入口、摘要索引/分页/增量、完整可访问性/截图谱系和 profile 性能数据尚未完成 |


## UI 提交回执契约

`submit(requestId, conversationId, text, quote, quoteFromUser)` 的 accepted 只表示 ChatViewModel 接收，不表示任务执行成功或对话已持久化。调用前将内容摘要、会话和 unknown 写入私有 `chat-submissions.json`，之后才调用宿主；完成后原子提交 accepted/rejected。进程在两者之间中断时保留 unknown，不能自动重发。Dart 查询 `sendReceipt`，未确认时保留草稿和同一个 requestId。

ID 形如 `ui_<微秒时间戳>_<随机数>`，七天有效；ID 时间由宿主核验，允许两分钟未来时钟误差。最多保留 4096 条有效回执和 2 MiB 文件，满额拒绝新提交，不驱逐有效 unknown。过期 ID 即使记录已清理也拒绝。这里是提交去重，不是动作执行幂等保证。Dart 待查回执和草稿现保存于私有 chat-drafts.json，提交前必须落盘；普通输入合并 250ms 写入，离开前台主动刷新。存储版本 CAS 拒绝旧界面迟到写入；损坏保留原件且阻止发送，不将空状态写回。最多 128 草稿/待确认项、单段 16000 字符、总文件 1MiB；不把这些业务内容放入默认诊断。

## 验证边界

测试日志位于本地 `.git/device-checks/spec-*.log`，不提交设备业务数据。已通过 Flutter analyze 和 225 项 Flutter 测试；app 定向 JVM 用例 282 项通过；core 定向契约回归 378 项通过。debug/perf APK 与 release AAR 实际构建成功，debug/profile APK 已逐文件比对对应 AAR。Windows 全 app JVM 首轮存在 57 项失败，涉及 shell、符号链接、POSIX 文件模式等路径，不能视作全部通过，也没有改测试把它们跳过。

设备为已连接 PZ261A。保留数据覆盖安装，并运行 FlutterLaunchDeviceTest 两项；构建路径修正后重新安装最终 arm64 debug APK，再运行相同测试。不卸载用户主应用，不导出凭据，不自动发布、不上传诊断。

## 来源范围

本批为现有 Maeve 用例的增量实现，没有拷贝参考项目源码。参考目录的 Mika/dsh-Go HEAD 与 spec 固定版本不同；如后续移植源码，需先核对固定版本、许可和 notices。Droidance 本地 HEAD 与 spec 固定版本相同。不能以这里的提交回执替代 D01 的 action effect 设计。

## 最终构建记录

- 构建日志：`.git/device-checks/spec-artifacts-final.log`。
- debug arm64 APK SHA-256：`be104fdc5edfbfa6b9343581d8caeabd13b0db954a68f5ab8704873cca787662`。
- perf arm64 APK SHA-256：`c060755fc51a64ae3fbc10792b2fe7bcfb5fd10cd4135aa3d0c2702584cc7e14`。
- `tooling/verify-flutter-artifact.py <apk> --mode debug|profile|release` 比对 APK 与本地对应 AAR 的 Flutter assets/libapp.so，模式不符或有效载荷不同即失败；不能替代签名、Manifest、升级和完整发行门禁。
- 联合构建 perf/release 时 Android ABI splits 也作用于 debug，debug 文件名为 `app-arm64-v8a-debug.apk` / `app-universal-debug.apk`；单独 debug 构建仍是 `app-debug.apk`。
- 全 core 回归最后一次完整运行：2998 项、50 失败、3 skipped；其中旧审批假设已随后修正并定向复验。剩余失败涉及 POSIX/沙箱/文件系统与 trace 等，未关闭，不宣称全量通过。

## 后续界面需求与提交顺序（2026-11-15）

用户要求先完成当前 spec 余项并提交中文 commit，再完成以下界面需求并单独提交；不要把两个阶段混为“已完成”。

- 执行任务页：实时画面下方显示最新执行记录，记录向上滚动，用户无需持续下滑；手动查看旧记录时的跟随行为需验证。
- 悬浮胶囊：整体重新设计，体现 Maeve 自有视觉风格；用户授权自由设计，保留实际执行、停止、接管等功能。
- 设置/应用访问与侧边栏：应用/包名搜索、标题搜索都要有清晰的输入框边界。
- 新任务：开始执行时尽早生成总结标题，避免等执行结束才更新。
- 顶部横幅：任务完成后也不能透出下层执行记录，修正遮挡与安全区。
- Amoo 接入：一键接入成功应立即反馈时间/状态；实际结果时间只在真实回传发生时更新，不能以接入时间伪造任务结果。

## 第二批实施增量（进行中）

- TraceDisk/RunMetricsStore 的覆盖写改为原子替换，原来七项 Trace 和六项统计失败已通过定向复验；同类修复覆盖 AudioMuteLedger、RunStore、FileChildRunStore、FileTriggerStore。
- 新增 ModelNetworkPolicy：默认 HTTPS；显式自托管 HTTP 只允许 LAN/loopback、全 DNS 候选校验与实际直连；拒绝元数据/链路本地/组播等目的地；接口不自动重定向。列表、探测、推理接入相同策略，旧 HTTP 配置不会先发送一次凭据再警告。自托管编辑页提供明确选择和持续提示。
- ProviderStore 外层事务将服务商、模型、角色和加密凭据统一写入私有 journal，原子替换作为提交点；之后才发布 StateFlow。主文件镜像失败保留完整 journal，重启对账；加入最近完整快照和用户主动恢复入口，保留损坏原件。新增回滚、嵌套失败、提交点故障、重启和坏快照测试。
- Amoo 文件桥加入稳定任务编号、持久摘要、claimed 恢复、unknown 不重发、严格有界 JSON、延迟对账及结果过期/字节限额。仍有生命周期/严格队列准入和 HTTP/SSE parser 任务，S08 不标记全完成。
- 更新器要求发布摘要/大小，统一新下载到有总 deadline/字节限制的进程下载器，校验后才形成 Ready 文件，安装前追加 minSdk/ABI 检查，保留签名轮换校验。正式签名升级验收未完成。
- 第二次全 core：3003 项，32 失败、3 跳过（原来 50 失败）；后续再以 round3 日志核对。新增网络安全规则需要本地 MockWebServer 测试显式授予自托管同意，不能靠自动识别 localhost 绕过规则。
- 当前这些增量尚未构建安装到手机；手机仍是上一批 debug APK。不能把源码定向测试当作新版真机验收。

## 第三批实施增量（2026-11-15，尚未提交）

- 新增 ActionEffectStore/ActionEffects，并接入真实主任务与子任务 ActionExecutor。任务 requestId 与动作 intentId/effectId 分开；写前记录 started，结果后结算 applied/not_applied/unknown，保留 run、摘要、后端装配身份、审批摘要、序列与证据摘要。unknown 跨新调用 ID、API/GUI 工具和新运行编号阻止同对话新写入；私有账本损坏/满额时执行前拒绝。每会话上限 4096/4MiB，不驱逐 unknown；完整删除会话时连同账本清理。
- applied 表示工具确认收到，不冒充已通过独立外部效果核验；executionSession 是装配身份，不等同于已完成 helper epoch/屏幕授权绑定。低层纯 core MCP 会话测试桩尚未接入持久 store，产品远程任务仍走同一 TaskRun。D01 继续保留未完边界。
- 本地“核对操作结果”要求用户勾选已在目标应用核对，再逐条确认已执行/未执行；宿主拒绝运行中的任务或过期回执。核对只记结论，不恢复旧任务、定时或批准。新增并发重复、进程中断、写前/结算失败、跨后端 unknown、人工核对和实际执行入口测试。
- 草稿增加正文/引用/待确认 requestId 冷启动恢复，写前持久化和版本 CAS；完整删除与持久 tombstone 防止迟到草稿复活。停止请求绑定对话，界面等待原生终态，并允许明确重试失败的停止请求。
- 发送审批正文不再按省略号前缀、忽略空白或大小写放行；完整正文最多 4000 字符，超长拒绝。未确认的裸坐标返回 target_unresolved，并作为真实工具拒绝回给模型；专门的盲点拒绝用例保留，帧/计数/视觉核查测试改用真实命名控件范围。
- HTTP 请求限制 16KiB 头、64 个头、256KiB 正文、4KiB trailer、每来源并发 4、每连接最多 100 请求；请求总读取期限默认 30s，不因滴入字节续命。拒绝 TE+CL、重复长度/认证/Host、非严格 chunked 与分块分隔符。模型响应单行 256KiB、事件 1MiB、总 8MiB，非流式也设总字节上限。Retry-After 支持秒和 HTTP-date，拒绝负值/非有限值，封顶 120s。
- 原有实际 HTTP 尝试统计继续保留；RunBudget 将适配器兼容重试也计入共享预算。可得 input+output token 默认上限 1000000，缺 usage 标记不完整，不能当费用估计，仍需任务集/设备校准。
- Flutter 全量 231 项与 analyze 已通过（随后增加人工核对交互测试，定向通过）；审批/回执/存储定向 core 192 项、app 102 项通过。
- 最新 Windows 全 app：1597 项，49 项 POSIX/沙箱失败；这 49 项所在的 10 个类直接在 Linux/JDK 上运行 73 项全部通过（spec-linux-app-jvm.log），并未改断言或跳过安全测试。core 的 Linux 全量正在最后复验；之前 32 项 POSIX 失败已在 Linux 通过。
- 新 debug APK 和 androidTest APK 构建通过，Flutter/AAR 内容比对通过。该版 debug SHA-256 为 d6c0d9a86d5fb048597854a00f72783453a319522a7794b918ead079ea5d9104；它只对应此时源码，后续源码变更需重建。当前 ADB 和 mDNS 均无在线设备，新版尚未安装/运行真机用例。独立缓存目录的 ReceiptStorageDeviceTest 已编写，待设备验证。

后续悬浮胶囊/实时记录等第二阶段界面需求单独实现与提交。整份 spec 仍不声明完成。

## 本轮收尾与后续批次边界

- Linux core 最后全量：3024 项、0 失败、2 跳过（外部评测数据未配置）；已安装并校验官方 Node 24.15.0 的 SHA-256，原来缺 Node 的脚本测试均通过。CI 从 versions.json 的 testNode.version 装配相同版本，YAML 解析和取版本脚本已本地验证。
- Windows app 的已知 49 项失败是同一组 POSIX/沙箱测试；所在十个类在 Linux 直接运行 73 项全通过。后续 round5 日志留在 .git/device-checks。不能把 Windows 失败数抹成零，也不能将跨平台组合验证说成实际手机验证。
- 关于页复用 AndroidSelfCheck 的设备事实/权限检查，明确 partial，不启动测试操作、虚拟屏或沙箱；完整手机操作自检仍需后续设备入口/验收。新增获取更新、问题反馈和打不开浏览器时的复制链接备用入口；不会替用户提交反馈。
- 文件桥轮转有界扫描、输出互斥硬配额、持久 100 待处理准入及 queue_full 拒绝已补齐；终态结果写出后释放名额，不释放 unknown。旧编号去重长期策略仍保守保持上限拒绝，不驱逐 unknown。
- bridge 能力检查缺失时明确 unsupported，发送不降级到无回执路径；重连重新监听并恢复原待查 ID，不重发。事件使用进程 epoch 和跨插件单调 revision，拒绝旧 epoch 迟到事件。新建/刷新/置顶加稳定错误回执，置顶只在宿主持久化确认后更新。
- ConversationStore 的会话/标题/置顶/来源/当前会话覆盖写改为同目录原子替换，不再在 rename 失败后截断旧文件；损坏会话归档失败会中止，不能覆盖原件。新增替换失败、旧值保留和状态不提前发布用例。
- Flutter 最近全量为 236 项通过；人工核对、基础自检、重连、缺能力拒绝和 epoch 防串台均有交互/契约回归。
- 用户已明确选择“先完成源码与构建”，本轮不等待 ADB，也不把未运行的 ReceiptStorageDeviceTest 当作通过。

当前实现批次可在源码回归与构建完成后单独提交；整份 SPEC 中的 D05 加密备份、精确桥接增量/长历史索引、完整授权后端绑定及发行/设备/性能专项仍保留上表，不能宣称整份 SPEC 完成。后续用户界面需求独立修改、独立提交，源码批次之间不混淆验证结论。

本批提交前最后复验：Flutter 236 项通过、analyze 无问题；Windows app 1603 项中仍为同一组 49 项 POSIX 失败（十个类 Linux 73 项通过），其余通过。Linux core 3024 项零失败、仅两个外部数据检查跳过。源码与 APK 构建先完成，手机验证按用户本轮选择延后。

本批最终构建日志：spec-batch-ready-build.log；debug APK SHA-256 为 237ac65e12825c6c1010f5b63d11e8b534b66d5ed74b473eadedb84876ad90bc；提交后重新构建会使用新的 Git build 标识。归属检查通过（26 notices、28 licence-screen entries）。
