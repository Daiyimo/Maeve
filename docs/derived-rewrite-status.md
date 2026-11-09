# 衍生代码重写进度

> 依据 [`derived-rewrite-plan.md`](./derived-rewrite-plan.md) 与机器清单
> [`derived-files.tsv`](./derived-files.tsv) 推进。本文件只记进度和约定，判定口径仍以计划文档为准。

## 当前基线

用 `tooling/derived-audit.py` 对 `7cf7ff3`（上游快照）逐文件重算：

```
group         files       loc   derived   ratio
src             763    120626    113518   94.1%
tests           749    101589    100021   98.5%
res             330     22469     22095   98.3%
tools            57      8578      8343   97.3%
docs             84      7709      4848   62.9%
core-text       175      3295      3233   98.1%
eval            129      3184      3078   96.7%
licenses         12      3002      2983   99.4%
other            33      3638      2345   64.5%
assets            6      1912      1892   99.0%
native            2       853       812   95.2%
flutter          14      2033         0    0.0%
total          2358    282827    262470   92.8%
```

放行条件仍是计划第 9 节：`derived_ratio >= 0.60` 的文本文件数为 0，且 6 个 Mika
署名点清零。**在达到这个条件之前，`NOTICE`、`ATTRIBUTION.md` 和源码头里的上游署名不动。**

## 波次状态

| 波次 | 范围 | 状态 | 说明 |
|---|---|---|---|
| 0 | 审计脚本 + 基线 | ✅ 完成 | `tooling/derived-audit.py` 可重算 `derived-files.tsv`，`--summary` / `--check` 可直接用 |
| 1 | 词汇层与叶子 | 🚧 进行中 | 先做 `core` 下无状态、接口窄的小包 |
| 2 | 核心能力层 | ⬜ 未开始 | |
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
累计已重写 33 个文件；绝大多数功能性文件已低于 60%，`Observation.kt` 与几个公共接口仍偏高。单文件达标不是目的，**行为等价 + 可编译 + 测试通过**才是。
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

## 每批的硬约束

1. 不改公共 API（类名、函数签名、枚举值、序列化名），除非同批改完所有调用方和测试。
2. 每批只动一个包族；测试跟着同批改，不先删测试。
3. 编译与目标测试必须通过；全量 `:core:test` 的 Windows 平台性失败不计入回归，但失败类不能新增。
4. `licenses/`、`assets/licenses/`、`rootfs.tar.xz`、`libproot*.so` 是第三方义务，永远不在重写范围。
5. 不为了降 `derived_ratio` 拆行、改空白或写无意义变体；只做可解释的重写。
6. 每批单独提交，带 `Verified:` 行。

## 下一批

Wave 1 剩余叶子包，按依赖顺序：

- `core:prompt` 收尾（`Prompts.kt` / `StepSummary.kt`）
- `core:access`（`AppAccess.kt`、`AppClassifier.kt`）
- `core:observation`（`Observation.kt` 数据类与工厂）
- `core:visual`（`FrameGrid.kt`、`Shots.kt`）
- `core:device`、`core:script`、`core:child`、`core:capability`、`core:wechat`
- `core:net`、`core:trigger`、`core:workspace`、`core:userscreen`、`core:usage`
- `core:record`、`core:trace`、`core:gate`、`core:model`
- `core:uistate` / `core:uistate/tools`
- `core:adb`、`core:shell`、`core:general`

每批完成后更新本文件的表格和基线数字。
