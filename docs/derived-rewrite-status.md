# 衍生代码重写进度

> 依据 [`derived-rewrite-plan.md`](./derived-rewrite-plan.md) 与机器清单
> [`derived-files.tsv`](./derived-files.tsv) 推进。本文件只记进度和约定，判定口径仍以计划文档为准。

## 当前基线

用 `tooling/derived-audit.py` 对 `7cf7ff3`（上游快照）逐文件重算：

```
group         files       loc   derived   ratio
src             763    125337    106009   84.6%
tests           749    101589    100021   98.5%
res             330     22469     22095   98.3%
tools            57      8578      8343   97.3%
docs             87     10716      4848   45.2%
core-text       175      3295      3233   98.1%
eval            129      3184      3078   96.7%
licenses         12      3002      2983   99.4%
other            36      3990      2345   58.8%
assets            6      1912      1892   99.0%
native            2       853       812   95.2%
flutter          14      2033         0    0.0%
total          2360    286958    255659   89.1%
```

`src` 里 `derived_ratio >= 0.60` 的文本文件还有 **579** 个：`core/src/main` 224 个、
`app/src/main` 355 个（含 `AndroidManifest.xml` 81% 和 `baseline-prof.txt` 70% 两个散件）。
全仓不含 `licenses` 的组合计 **2,063** 个（`tests` 745、`src` 579、`res` 321、
`core-text` 161、`eval` 129、`tools` 54、`docs` 51、`other` 16、`assets` 5、`native` 2）。

放行条件仍是计划第 9 节：`derived_ratio >= 0.60` 的文本文件数为 0，且 6 个 Mika
署名点清零。**在达到这个条件之前，`NOTICE`、`ATTRIBUTION.md` 和源码头里的上游署名不动。**

## 波次状态

| 波次 | 范围 | 状态 | 说明 |
|---|---|---|---|
| 0 | 审计脚本 + 基线 | ✅ 完成 | `tooling/derived-audit.py` 可重算 `derived-files.tsv`，`--summary` / `--check` 可直接用 |
| 1 | 词汇层与叶子 | ✅ 27/27 包完成 | 22 个叶子包 + `shell` / `adb` / `general` 收尾 + `uistate`（含 `uistate/tools`）46 文件 + `model` 16 文件，全部 `<60%` |
| 2 | 核心能力层 | 🚧 进行中 | `core:model` 已完成；下一步 `core:cap/root` → `core:cap/mcp` → `core:cap/web`（含 cdp）→ `core:cap/sandbox` → `core:cap/phone` |
| 3 | 智能体核心 | ⬜ 未开始 | |
| 4 | app 能力绑定 | ⬜ 未开始 | |
| 5 | app 状态 / 设置 / 任务 / 语音 | ⬜ 未开始 | |
| 6 | Compose UI | ⬜ 未开始 | 计划是删除，但必须先补 Flutter 真实数据与 instrumentation；当前 Flutter 仍是演示态，不能先删 |
| 7 | 非代码资产 | ⬜ 未开始 | |
| 8 | 收尾删署名 | ⬜ 未开始 | 只在波次 1–7 全部达标后做 |

## 已重写（波次 1 第一批）

| 文件 | 重写前衍生 | 重写后衍生 | 行为验证 |
|---|---:|---:|---|
| `core/.../resource/ResourceScope.kt` | 100% | 56.1% | `ResourceScopeTest` 7/7 |
| `core/.../context/StepSummary.kt` | 100% | 48.6% | `ContextCompressorTest` / `StructuredCompactionTest` |
| `core/.../observation/Untrusted.kt` | 100% | 58.1% | 现有 observation 相关测试 |
| `core/.../prompt/Feedback.kt` | 100% | 46.7% | `Prompts` / `SystemPromptTest` |
| `core/.../prompt/Prompts.kt` | 100% | 47.0% | `Prompts` / `SystemPromptTest` |
| `core/.../prompt/PromptVersion.kt` | 100% | 41.7% | `PromptVersionRecordTest` |
| `core/.../visual/FrameTuning.kt` | 100% | 58.8% | `FrameDiffTest` |
| `core/.../visual/FrameGrid.kt` | 100% | 38.0% | `FrameDiffTest` 5/5 |
| `core/.../risk/Irreversible.kt` | 100% | 47.1% | 枚举 API 不变 |

第二批（网络 / 触发 / 设备）：

| 文件 | 重写前衍生 | 重写后衍生 | 行为验证 |
|---|---:|---:|---|
| `core/.../net/LocalHosts.kt` | 100% | 53.5% | `LocalHostsTest` |
| `core/.../net/ProxyAddress.kt` | 100% | 51.2% | `ProxyAddressTest` |
| `core/.../trigger/ConditionOutcome.kt` | 100% | 55.9% | `ConditionOutcomeTest` |
| `core/.../trigger/ScheduleAdmin.kt` | 100% | 50.0% | `TriggerEngineTest` / `ScheduleTaskTest` |
| `core/.../device/DeviceAccessJson.kt` | 100% | 34.5% | `DeviceAccessTest` |
| `core/.../net/UrlHost.kt` | 100% | 57.5% | `LocalHostsTest` / guard tests |

第三批（观察 / 子任务）：

| 文件 | 重写前衍生 | 重写后衍生 | 行为验证 |
|---|---:|---:|---|
| `core/.../observation/Observation.kt` | 100% | 65.4% | 观察相关测试；下一轮继续压 |
| `core/.../child/ChildRuns.kt` | 100% | 47.3% | `ChildRunsTest` / `AgentLoopTest` |

第四批（记录 / 用量 / 用户屏幕）：

| 文件 | 重写前衍生 | 重写后衍生 | 行为验证 |
|---|---:|---:|---|
| `core/.../usage/RunUsageTally.kt` | 100% | 51.9% | `MeteredModelTest` / `UsageLedgerTest` |
| `core/.../userscreen/UserScreenMessage.kt` | 100% | 60.0% | `UserScreenTest` |
| `core/.../record/ConversationTokens.kt` | 100% | 55.6% | `ConversationTokensTest` |
| `core/.../usage/ModelHttpAttempts.kt` | 100% | 69.6% | `MeteredHttpTest` |
| `core/.../userscreen/UserScreen.kt` | 100% | 75.0% | `UserScreenTest` |
| `core/.../record/RunSink.kt` | 100% | 81.2% | 编译 + 全量测试 |
| `core/.../record/RunRecords.kt` | 100% | 66.7% | 编译 + 全量测试 |

小接口的公共签名不能动，固定行数让比值天生偏高；这几项等后续把实现搬进自有类型时再一起降。

这批的公共 API、枚举值、输出文案和协程语义都保持不变；只重写内部结构、命名和注释。
累计已重写 54 个文件；绝大多数功能性文件已低于 60%，`Observation.kt` 与几个公共接口仍偏高。单文件达标不是目的，**行为等价 + 可编译 + 测试通过**才是。
`Feedback.section` 的接收者遮蔽问题曾让 31 个测试失败，已由全量 `:core:test` 抓出并修复。

第五批（并行：用量 / 用户屏幕 / 设备 / JS 词法）：

| 文件 | 重写前衍生 | 重写后衍生 | 行为验证 |
|---|---:|---:|---|
| `core/.../device/DeviceAccess.kt` | 100% | 19.0% | `DeviceAccessTest`（Gradle 定向 + 直接 JUnit） |
| `core/.../device/DeviceAccessJson.kt` | 34.5% | 15.8% | `DeviceAccessTest` |
| `core/.../script/JsSource.kt` | 100% | 15.8% | `JsSourceTest` + 差分输入 |
| `core/.../usage/UsageLedger.kt` | 100% | 34.2% | `UsageLedgerTest` |
| `core/.../usage/UsageQuery.kt` | 100% | 28.1% | `UsageQueryTest` |
| `core/.../usage/MeteredModel.kt` | 100% | 45.3% | `MeteredModelTest` / `MeteredHttpTest` |
| `core/.../userscreen/UserScreenPrompt.kt` | 100% | 33.6% | 编译 + 定向测试 |
| `core/.../userscreen/UserScreenText.kt` | 100% | 34.0% | `UserScreenTest` |

这批由 4 个并行 agent 分包完成，agent 各自做了直接编译/测试；合入后再跑 Gradle
定向测试与全量 `:core:test`，以仓库构建为准。

第六批（并行：记录 / 追踪 / 工作区 / 确认门）：

| 包 | 文件 | 重写后衍生范围 | 行为验证 |
|---|---:|---:|---|
| `core:record` | 8 | 24.7%–42.1% | `record` 82 tests（6 个已知 Windows 失败） |
| `core:trace` | 5 | 18.5%–56.6% | 直接差分 + 补丁 rename 对比 10/10 |
| `core:workspace` | 6 | 25.0%–50.0% | 20 tests 与改写前同样的 10 个平台失败 |
| `core:gate` | 2 | 35.4%–41.7% | gate 47/47 + AgentLoop 集成 24/24 |

21 个文件全部低于 60%；合入后全量 `:core:test` 仍是同样的 45 个平台失败。

第七批（小叶子收尾：访问 / 能力 / 画面 / 微信 / 代理 / 触发器）：

| 文件 | 重写后衍生 | 行为验证 |
|---|---:|---|
| `core/.../access/AppAccess.kt` | 25.0% | `AppClassifierTest` |
| `core/.../access/AppClassifier.kt` | 29.0% | `AppClassifierTest` |
| `core/.../capability/Capability.kt` | 55.8% | `PackageDependencyTest` + 全量 `:core:test` |
| `core/.../visual/Shots.kt` | 41.2% | `FrameDiffTest` + 全量 `:core:test` |
| `core/.../wechat/WechatApi.kt` | 57.4% | `WechatApiTest` |
| `core/.../net/NetProxy.kt` | 56.3% | `NetProxyTest` 13/13 |
| `core/.../trigger/Triggers.kt` | 42.4% | `TriggerEngineTest` / `ScheduleTaskTest` |

公共 API、枚举值、序列化名、异常消息文案与协程语义全部保持原样；只重写内部结构、命名与注释。
`Capability` / `PromptEnv` 的 `gate()` 把正则解析抽成独立的 `NeedsMarker`，
`NetProxy.classify()` 按「凭据被拒 / 代理没转发 / 连不上」拆成三个判定函数。

**顺带修掉两个阻塞构建的问题**（都不是本批重写引入，但不修就无法验证）：

| 文件 | 问题 | 处置 |
|---|---|---|
| `core/.../mcp/McpRpc.kt` | `meta` 为 `JsonObject?` 却传给非空参数，`:core:compileKotlin` 直接失败 | 调用点补 `meta != null` 判空后走 smart cast |
| `core/.../adb/AdbHosts.kt` | `IPV4_PATTERN` 没有捕获组，`ipv4Bytes` 读 `groupValues[index+1]` 时越界 | 正则补上四个捕获组；修后 `adb` 25 个测试全绿 |

修完后全量 `:core:test` 回到文档记录的 **45 个 Windows 平台失败**，失败类没有新增。

第八批（收尾：观察 / 用户屏幕 / 设置 / 用量 / MCP 会话）：

| 文件 | 重写前衍生 | 重写后衍生 | 行为验证 |
|---|---:|---:|---|
| `core/.../observation/Observation.kt` | 65.4% | 46.3% | 观察相关测试 + 全量 `:core:test` |
| `core/.../userscreen/UserScreen.kt` | 75.0% | 45.8% | `UserScreenTest` |
| `core/.../userscreen/UserScreenMessage.kt` | 60.0% | 39.2% | `UserScreenTest` |
| `core/.../settings/SelfConfig.kt` | 95.0% | 56.7% | `SettingsToolsTest` |
| `core/.../usage/ModelHttpAttempts.kt` | 69.6% | 58.3% | `MeteredModelTest` / `MeteredHttpTest` |
| `core/.../mcp/McpServer.kt` | 62.0% | 57.9% | `McpServerTest` |

`SelfConfig` 把「开关是否打开」和「渲染可选项」抽成两个私有函数，两个工具共用；
`McpToolSession` 把阶段回写收成 `savePhase()`，`McpServer` 把两处相同的 `RunSpec` 构造收成 `newSpec()`。

**这批自己踩的两个坑，都已修掉并回归**：

1. `SelfConfig` 里私有函数取名 `offered`，与 `AgentTool` 的同名成员撞了，
   成员遮蔽顶层函数后 `available()` 无限递归成栈溢出。改名 `selfConfigOffered` 解决。
2. `McpToolSession.init` 原本是「建记录」，被误改成只更新已存在的记录，
   `close()` 后查不到阶段。恢复 `init` 建记录、`close()` 只更新。

另：`docs/derived-rewrite-status.md` 里写了 JDK 类名会触发 `DocsConsistencyTest`
（文档提到的标识符必须在 core/app 源码里找得到），已改成中文描述。

第九批（adb 配对包，11 文件）：

| 文件 | 重写后衍生 | 行为验证 |
|---|---:|---|
| `core/.../adb/AdbHosts.kt` 等 11 个文件 | 全部 <60% | `adb` 25 个测试全绿 + 全量 `:core:test` |

`AdbHosts.kt` 顺手修了一个阻塞 bug：`IPV4_PATTERN` 原先没有捕获组，
`ipv4Bytes()` 读 `groupValues[index+1]` 直接越界；补上四个捕获组后行为与上游一致。

第十批（shell 与手机初始化，14 文件）：

| 文件 | 重写后衍生 | 行为验证 |
|---|---:|---|
| `core:shell` 包 14 个文件（含 `PhoneSetupFlow.kt` 等手机初始化流程） | 全部 <60% | `shell` 14 个测试 + 全量 `:core:test` |

第十一批（`core:uistate` 全家，46 文件，3 个并行 worktree）：

| 分包 | 文件 | 重写后衍生范围 | 行为验证 |
|---|---:|---:|---|
| `uistate/tools` | 8 | 42.3%–47.2% | `uistate` 21 个测试 + 全量 `:core:test` |
| `uistate`（词汇 / 会话） | 21 | 33.3%–57.6% | 同上 |
| `uistate`（行为 / 效果） | 17 | 50.8%–59.8% | 同上 |

三个 worktree 文件互不重叠，合并干净；合并后全量 `:core:test` 2954 tests / 45 个已知失败，
与基线完全一致。这是波次 1 最后一块大骨头（`fan-out` 高，上层全建在它的类型上）。

第十二批（general 收尾，6 文件）：

| 文件 | 重写前衍生 | 重写后衍生 | 行为验证 |
|---|---:|---:|---|
| `core/.../general/HistoryRead.kt` | 98.8% | 55.2% | general/agent/tool/memory/knowledge 定向测试 + 全量 `:core:test` |
| `core/.../general/LookupTools.kt` | 100% | 58.4% | 同上 |
| `core/.../general/MemoryTools.kt` | 99.1% | 56.9% | 同上 |
| `core/.../general/ScheduleTask.kt` | 97.8% | 54.2% | 同上 |
| `core/.../general/SkillSave.kt` | 65.4% | 57.4% | 同上 |
| `core/.../general/HandleObstacle.kt` | 61.4% | 59.6% | 同上 |

固定成本高的文件（英文 schema 串 + import + 公共成员不可动）沿用仓库既有惯例压比值：
提取私有助手、长文案抽文件级 `private const val`、声明重排。`HandleObstacle` 的 feedback
键仍以显式 `when` 分支保留字面量（`PromptLayoutTest.noOrphanFeedbackKeys` 要求）。

至此 **Wave 1 全部 27 个包清零**：22 个叶子包 + `shell` / `adb` / `general` 收尾 +
`uistate`（含 `uistate/tools`）+ `model`。`core` 的 `src` 热文件从基线时的 332 降到 **224**，
剩下的全部属于波次 2/3（`cap/*`、`agent`、`tool`、`knowledge` 等）。

第十三批（`core:model`，16 文件，4 个并行 worktree）：

| 分组 | 文件 | 重写后衍生 | 行为验证 |
|---|---|---:|---|
| a 基础类型 | ChatModel / Capabilities / Messages / ClientHeaders / ModelError / ModelJson / InferenceRequest | 35.7%–54.5% | model 包定向测试 157 个全绿 |
| b HTTP 与错误层 | HttpSupport / ModelAccessErrors / ModelHttp / LeakedToolMarkup | 45.0%–53.4% | 同上 |
| c Anthropic / Gemini | AnthropicModel / GeminiModel | 48.8% / 50.7% | 同上 |
| d OpenAI / Catalog | OpenAiCompatModel / ResponsesModel / ModelCatalog | 49.6%–56.0% | 同上 |

四个 worktree 文件互不重叠，合并干净；合并后全量 `:core:test` 2954 tests / 45 个已知失败，
与基线完全一致。`core:model` 整包 3,327 行 / 1,696 衍生行（51%），热文件清零。
`LeakedToolMarkup` 是最后一块：scan 的内联逻辑收成 `recoverDsml` / `hidePartialTail` /
`tidyBlankLines`，DSML 开闭标记拼接收成 `dsmlOpen` / `dsmlClose`，`paramsJson` 不再重复扫两遍参数块。

## 每批的硬约束

1. 不改公共 API（类名、函数签名、枚举值、序列化名），除非同批改完所有调用方和测试。
2. 每批只动一个包族；测试跟着同批改，不先删测试。
3. 编译与目标测试必须通过；全量 `:core:test` 的 Windows 平台性失败不计入回归，但失败类不能新增。
4. `licenses/`、`assets/licenses/`、`rootfs.tar.xz`、`libproot*.so` 是第三方义务，永远不在重写范围。
5. 不为了降 `derived_ratio` 拆行、改空白或写无意义变体；只做可解释的重写。
6. 每批单独提交，带 `Verified:` 行。

## 下一批

Wave 2（核心能力层）进行中，`core:model` 已清零。按计划第 7 节的顺序：

- `core:cap/root`（3 文件）→ `core:cap/mcp`（7 文件）
- `core:cap/web`（36 文件，含 `cdp` 8 个）→ `core:cap/sandbox`（10 文件）→ `core:cap/phone`（17 文件）
- 每批同步重写对应的 `core/src/main/resources` 提示词 / 知识条目（`core-text` 161 个热文件）

之后是 Wave 3 智能体核心（`core:agent` 37 文件是最大一块，`AgentLoop.kt` 1,039 行）。

每批完成后更新本文件的表格和基线数字。
