# Maeve 架构

这份文档按代码现状描述 Maeve 的整体结构,方便找代码。还没做的、做了一半的写在 [`roadmap.md`](roadmap.md)。

路径缩写:`core/…` = `core/src/main/kotlin/com/daiyimo/maeve/core/`,`app/…` = `app/src/main/java/com/daiyimo/maeve/`。

## 1. 模块与分层

仓库有两个 Gradle 模块:

- `:core`:纯 Kotlin(JVM),不依赖安卓。主循环、工具、能力的平台无关部分、上下文、核查、记忆、知识、触发器、运行记录都在这里,可以在电脑上用单测和假模型跑。提示词、内置知识与技能是它的资源(`core/src/main/resources/`)。
- `:app`:安卓宿主。界面、前台服务、虚拟屏、无障碍、内置手机助手、WebView、沙箱、文件存储,以及 `:core` 里各个接口的安卓实现。

另有可选的独立 Go 网关 `tools/relay/`,用于单用户、单设备的公网远程 CLI / MCP。它不进入 APK,手机通过出站 WSS 接入;部署和手机鉴权见 [remote-mcp.md](remote-mcp.md)。

```
 来源   对话界面 │ 定时/条件触发 │ 父任务(子任务)│ 电脑(CLI / MCP)
          └──────────┬──────────┘
          TaskService(前台服务,安卓宿主)
          TaskCenter(每个对话一个 Run、并行上限、排队)· RunStore · Outbox
                     │
          TaskRun ── 一次运行:装能力、建 AgentLoop、收尾
                     │
          AgentLoop(只调度)← RunInbox(插话、回答、确认、交回、取消)
            ModelCaller · ContextBuilder/ContextCompressor · TurnPolicy/StopRules
            ToolDispatcher → ActionExecutor(唯一执行入口)
                     │  CapabilityProvider(核心不认识具体能力)
          phone      web       sandbox      (mcp 客户端)
```

`:core` 里包之间的依赖方向(`PackageDependencyTest` 检查):

- 能力(`core/cap/<能力>/`)依赖核心,核心不引用任何能力,能力之间也互不引用;只有装配层(`core/assembly/`)同时认识几种能力。App 里 `cap/` 下各能力的目录同样互不引用。
- 工具引擎(`core/tool/`:`ToolDispatcher`、`ToolContext`、`AgentTool` 等)不引用界面状态(`core/uistate/`)。引擎只经 `SurfaceLayer`(`core/tool/SurfaceLayer.kt`)用到有界面的那一层,接口里只有屏的 id、工具、参数和结果;实现是 `Interfaces`,由装配层开局造好、放进工具上下文,有界面的工具经 `core/uistate/ScreenContext.kt` 里的扩展从上下文取读屏状态。

## 2. 任务与运行

### Run

一次运行由 `RunSpec`(`core/run/RunContracts.kt`)描述:`id`、`goal`、`threadId`(对话)、`priorTurns`、`provenance`、能力子集 `capabilities`、`readOnly`、`parent`、`stepBudget`。

- `RunProvenance`(`core/run/RunProvenance.kt`)记这次运行从哪来、用户在不在:`RunOrigin` = Ui / Channel / Trigger / Mcp / Child;`Attendance` = Present / Reachable / Unattended。
- 运行阶段 `RunPhase`:Queued、Preparing、Running、WaitingUser、Parked、Paused、Finishing、Aftercare、Ended、Interrupted。
- 「这一步谁发起」是另一回事,见 §4 的 `Origin`。

### 任务中心

`TaskCenter`(`app/task/TaskRegistry.kt`,由 `TaskService` 持有)管运行的启动、排队与收尾:

- 每个对话同时只有一个 Run。同一对话里任务进行中用户又发消息,不另开任务,而是作为插话交给正在跑的 Run(`TaskService.begin`)。
- 不同对话的任务可以并行,上限见 §8;超过上限的进先进先出队列,有 Run 结束时依次补上(跳过对话里已有 Run 的)。并行开关关着(默认)时,任务进行中界面不能切换对话(`ChatViewModel.canSwitch`、侧栏 `SessionsDrawer` 里别的对话不可点并有提示),交给服务的另一个对话的任务(对话页、悬浮胶囊、浏览器页、定时、电脑)一律进队列,不丢(`TaskService.begin`)。对话页先等服务空下来再交(这时那条消息下面写「等前一个任务做完再开始」,`ChatViewModel.begin`),防的是同一个对话里上一个任务还在收尾时,新的一句被当成插话;查完到交到之间别处先开始了一个任务,它照样排上,那个任务收尾后开始。这时点停止只停这个对话的任务(`TaskActions.cancelIn`),不碰正在跑的那个。
- 定时任务到点时,如果原对话正有任务在跑,就在这个对话里排队等它结束(`queueInConversation`),不当成插话,也不丢。
- 运行状态写进 `runs-v3.json`(`app/task/FileRunStateStore.kt`,原子替换写入)。
- `app/task/TaskCenterState.kt` 只是界面要显示的状态(运行列表、并行上限、插话状态等)。

### 收件箱与插话

运行中从外面进来的东西都经 `RunInbox`(`core/run/RunInbox.kt`),种类是 `RunInput`:Steer(插话)、Redirect、Answer、Confirm、HandBack、Cancel。

- **插话是补充**:做过的一概保留。每句插话都撤掉此刻还在等的模型请求(`AgentLoop.routedOrRedirect`),接在已有历史后面重问,发过的消息一字不改,请求前缀照样只增不变。已经进了执行入口的工具调用照常做完、结果照记,下一次问模型之前再接上插话(`AgentLoop.appendSteers`)。Steer 和 Redirect 走同一条路。
- **打断时已经显示的字留下**:撤掉的那次请求如果已经流式显示出一段字(界面上的 `TaskState.Running.preview`),`AgentLoop.keepInterrupted` 把这段字作为一条只有文字的回复记进历史,接在插话前面,模型重问时看到的和用户看到的一样;思考内容和写了一半的工具调用本来就不显示,不记。没显示出字(没开流式显示、还没出字、重试时清空了)或这一批没有插话时什么也不记。界面上的实时文字当场清掉,撤掉之后网络线程迟到的字不再显示。这段字还发一个运行事件 `reply_interrupted`(截断、脱敏后),`TaskRun` 把它挂到紧接着交给模型的那句插话上(`SteerNote.interrupted`),对话页在这句插话上方用淡色显示,下面一行小字「被你的新消息打断」;随这一轮存进对话(`Turn.interruptedReplies`,和 `steers` 一一对应,没打断的是空串),重开对话时照样显示。它只给界面,不进以后几轮给模型的上文。
- **插话之前已经说完的回复留下**:不带调用的回复已经显示出来、记进了历史,这时才取到插话(回复刚好先说完、插话随后才到,或交出前整理来源时到的插话),`AgentLoop.keepFinished` 把界面上的那段字发成运行事件 `reply_finished_before_steer`(脱敏后,不截短:界面要显示全文),当场清掉实时文字,再接上插话。`TaskRun` 同样挂到这一批的第一句插话上(`SteerNote.finished`),对话页在插话上方照回答的样子显示(不标「打断」),随这一轮存下(`Turn.finishedReplies`,和 `steers` 一一对应);没显示过字时什么也不挂。对话页上还没交给模型的插话排在正在长出来的回答后面(`steersAroundDraft`):那段回答是插话到之前就在写的,写完或被打断时留在原处,不会从插话下面跳到上面。
- **插话什么时候进上下文**:`AgentLoop.appendSteers` 在每一步调模型之前取收件箱;模型给出不带工具的回答时先看一眼,有新插话就继续干。回答交出之前(核对、整理来源之后)用 `RunInbox.closeUnlessSteered` 在同一把锁里看和关:还有插话就不收尾、带着它接着做,没有才关上收件箱,此后的话才成为下一件事。模型自己调 end_not_done 时刚到的插话同样让它接着做;守卫叫停、出错照样结束。
- **等待提前结束**:收件箱给出「有插话还没交给模型」的信号(`core/run/SteerSignal.kt`),装在整个运行的协程上下文里。撤掉也什么都不丢的调用经 `waitOrSteer` 包起来,插话一到就提前结束:纯等待和只问看图模型的工具(声明 `AgentTool.yieldsToSteer`:wait、phone_wait、phone_event、web_wait_for、describe、find_visually,由分发器统一包)、`phone_run` 里的等字、`do_steps` 按条件等界面的每一段、网页换页之前的停留(`PageDwell`)、同一网站或 App 里两次对外动作之间的间隔(`ActionExecutor`)。被截断的调用报 `skipped_for_new_message`、这次什么也没做;在 `do_steps` 里这一步和后面的子步一起跳过。动作做完之后等界面安静、判效果的那一下不截断(它给的是动作的结果)。模型调用的重试等待本来就在模型请求里,随它一起撤掉。
- **节奏保护不丢**:停留和间隔被截断时,动作本身不做(工具结果说明为什么、要的话再调一次)。两者都按时刻算(间隔从上一次对外动作做完算起,停留从这一页来的时刻算起),没停够的部分不算停过,下一次换页或对外动作之前照样补足余下的时间;插话只是让模型现在就能想,不会让下一次动作来得更快。
- **一次多步途中**:`do_steps` 在两步之间发现有插话,就停在那里,结果写明「为处理新消息而跳过」(`skipped_for_new_message`),不算失败(`core/agent/BatchRunner.kt`)。某一步做完正在按条件等界面时有插话,等待也马上停下(那一步的 `wait.stopped` 是 `new_message`),后面的子步照样跳过。
- **界面上马上有回音**:`TaskRun.steer` 送进收件箱成功就把这句话标成「已收到，下一步处理」(`SteerState.Received`),交给模型时改成「已加入当前任务」;对话页和悬浮胶囊的对话卡片都在这句话下面写这行小字(`steerLabel`)。
- **撤回与编辑插话**:默认仍直接插给当前任务。对话页长按尚未交给模型的消息,可以撤回或取回编辑;`RunInbox.withdrawSteer` 与 `takeSteers`、收尾共用一把锁,已交给模型或已收尾时返回失败。成功后标为 `SteerState.Withdrawn`,对话页和胶囊不再显示,也不计入已加入的插话。任务尚未交给服务时从 `ChatViewModel.held` 撤回。编辑恢复正文与引用,输入框已有草稿、引用或附件时不提供编辑;悬浮胶囊带着屏幕、浏览器页带着页面说明插的话不提供编辑(`editableSteer`):取回来时输入框开头是屏幕标记或几行页面说明原文,删掉就丢了屏幕和页面,改坏就认不出页面。电脑追加的插话(`SteerNote.remote`)也显示在对话里,但不提供撤回和编辑,收件箱也拒绝撤回(编辑会把只归当前任务的话变成手机插话、可能转成下一件事;撤不撤由电脑定);`remoteSteers` 把 `Withdrawn` 按 `not_applied` 报,不多出对外状态。
- **没来得及处理的插话**:运行结束时还没消费的插话,如果用户没按停止,就成为这个对话的下一个任务;按了停止的丢掉(`TaskRun.handoffUnconsumed` 只管收尾时未消费的)。运行已关闭后才到的插话,由 `TaskRun.steer` 收不进收件箱时 `deferMessage`,或 `TaskService.begin` 发现任务已收尾时排成下一个任务。
- **停止**:Cancel 是控制信号,不在插话后面排队。`TaskRun.cancel` 同时取消子任务;接管中就结束接管;排队中的直接移出队列。
- **停止后的交代**:界面当场显示已停止,输入框空出来;随后补上几行交代当作这一轮的回答(做过什么、看到了什么、成批的事发出了几次和是哪些、哪些没做完),结局照旧是取消(评测、结果分类、学习都按取消算)。`TaskService` 收尾时先把运行编号登记进 `TaskCenterState.summarizing`(界面据此在「已停止」下面写「正在整理已做的事…」),记下「已取消」那一轮,再在进程级作用域里由 `TaskRun.stopSummary` 写,写好用 `ConversationStore.updateTurn` 改进那一轮,写好或写不成都拿掉登记;不挡着收尾、下一件事和再点停止。写法在 `StopSummary`(`core/agent/StopSummary.kt`):只读这次的运行记录(活动、那一步的用意、最近读到的两段、停下时写了一半的字,有上限,不带画面),请一次模型(轻量模型优先,出错换主模型,不给工具,总共 12 秒),没模型、出错、超时就按记录直接拼(`StopSummary.plain`)。对外动作(发送、提交)按活动上的 `outward`(`ActionExecutor` 照不可逆判定标上)数。什么也没做、也没写出字的只说已停止。下一轮的上文里写成「你中途停止了这次任务,停止前:…」(`Conversation.context`),「继续」知道从哪接着做。之前几轮读到的原文不进上文,要用时模型自己查(`history_read`,见「之前几轮的记录」)。

### 送达

结果、提问、通知经 `Outbox`(`core/run/Outbox.kt`)送出:先记下要发的,再发。状态 Pending → Attempting → Delivered / Failed / Uncertain;同一个送达编号只送一次;发到一半结果不明的标为 Uncertain,不自动重发。记录在 `outbox-v3.json`(`app/task/FileOutboxStore.kt`)。

渠道任务只发一次受理确认,不转发运行阶段或步数。任务持久化登记后,以 `accepted:<运行编号>` 的 `Notice` 进入 `Outbox`,发送不阻塞任务执行;同一渠道消息编号的重投不建任务也不再确认,不同编号即使文字相同也分别登记,并在同一对话里按先后顺序执行。提问、确认与接管仍只走 `ChannelInteraction`。结果链路另记主循环完成到送出、Outbox 加发送、微信 HTTP 各段毫秒数,不记消息内容。

本机渠道是 `LocalChannel`(`app/task/LocalChannel.kt`):本机界面加系统通知。`WechatChannel`(`app/wechat/`)是第二渠道:单账号、本人私聊文本,凭证加密保存在本机,独立前台服务长轮询,不建中继服务器,也不另建任务状态机。入站消息先写待处理账本再推进同步游标,`TaskCenter` 按渠道消息编号持久化去重后才确认;进程重启会先补交未登记消息。任务记录保存来源 `ChannelPeer`,提问、结果与 `RunRecovery` 都按来源路由。渠道接口 `Channel`、`ChannelGateway`(主人身份、提问编号对应、付款确认只能在能确认的渠道上做,`Channel.confirms`)在 `core/run/`;微信 `confirms=false`,付款确认与手机接管仍走手机。

### 进程重启后

- `RunRecovery`(`app/task/RunRecovery.kt`)是运行善后的单一入口,对象是「中断了、还没告知过用户」的运行。两个时机:进程重启后,`MaeveApp.onCreate` 一起来就调 `recover`(`TaskService.onCreate` 也调,但进程里只有第一次生效,后到的什么都不做);任务服务被销毁而进程还在时,`TaskService.onDestroy` 标中断后当场调 `settleInterrupted`,不等下次启动(善后用 App 级作用域,服务没了也做完;启动时的扫描再接住当场没做完就被杀的)。标记在调用里同步做完(`RunStore.interruptUnfinished()` 把没结束的运行标为 Interrupted,此刻在跑的运行经 `live` 排除),善后在后台做:回复已存但没送达的补送一次(注明是恢复的);送达状态不明的不重发,只提示「上次结果可能已送达」;没有回复的,发「运行中断,可在对话里重新发起」的系统通知,并往对话记一轮失败状态的记录「任务中断了,可以重新发起」(和其他失败结果同一套,界面上显示为未完成的一轮)。**「已告知」不另设字段,看送达账本**:有 `interrupted:<运行>`、`uncertain:<送达>`、`failed:<送达>` 任一条提示记录就不再处理;回复送达了的运行转为 Ended。所以重复调用、两个时机撞上都只告知一次。**从不从中间续跑**:屏幕状态回不去。
- `TaskRecovery`(`app/task/TaskRecovery.kt`,App 启动时运行)恢复的是系统状态而不是运行:任务期间静音的 App 恢复声音,任务借用过的剪贴板还给用户。
- 子任务记录在加载时把 Running 改为 Unknown,不重跑(`app/task/FileChildRunStore.kt`)。
- 定时任务窗口的处理见 §10。

## 3. 主循环

`AgentLoop`(`core/agent/AgentLoop.kt`)只做调度,具体判断各在一个类里:

| 类 | 做什么 |
|---|---|
| `ModelCaller` | 组请求、调模型、流式输出;压缩与补回、临时错误重试、上下文超长重试、大小模型分流 |
| `RetryPolicy` | 429、5xx、断网有限重试(最多 3 次,指数退避,每个任务总等待有上限) |
| `ToolDispatcher` | 按名找工具、持有证据账本、拼各能力的提示词段落 |
| `TurnPolicy` | 进度检查(仅有步数上限时)、空回复或被截断时提醒一次、工具调用漏成文字时退回一次、该跟进时提醒一次、回答换了语言时退回一次、最后一轮只给 `end_not_done`(仅有步数上限时) |
| `StopRules` | 什么时候结束:放弃、守卫叫停(重复同一动作、没有进展、反复读、失败太多、沙箱打转)、模型失败、步数用完(仅有步数上限时) |
| `BatchRunner` | `do_steps`:一次给 1–20 个已知动作,逐个执行、逐个核对;一步可带等待条件(`expect` 出现、`expect_gone` 消失、`expect_app` 前台 App 或页面地址、`wait_stable: true` 界面稳定,`timeout_s` 默认 8 秒、最多 20 秒),做完由代码读屏等到成立再做下一步,不再为「还在加载」问模型;弹框、别的包的窗口盖上来、换了 App 时提前停,结果里写 `wait`(`met`、`stopped`、`waited_ms`)。等待本身在 `core/uistate/UntilWait.kt`,手机、网页共用,经端口的 `awaitChange`(无障碍事件 / 页面变动)醒来;等的时间在运行小结里算等待,不算操作 |
| `IntentGate` | 对话界面发起的任务第一次要用非查阅类工具时,判断用户是在聊天、要查信息还是要办事;判为聊天就不带工具重答。判断不出(调用失败,或回复不是三个词之一)时照主模型原来的决定执行;每次运行只判断一次(`AgentLoop.assessIntent`) |
| `StuckAdvice` | 卡住时在工具结果里加建议;第二次卡住提示可以放弃 |
| `NoticePolicy` | 看完一个界面后决定要不要补给模型对应 App/网站的知识 |
| `GuideRecommender` | 开局的索引:按任务文字挑出最多 3 条 App/网站知识,另列出全部技能(内置的和在用、试用的个人技能),全文由模型用 `skill_read` 自己读 |
| `RouteReplay` | 打开「照着上次的做法做」时,在第一次调模型前回放匹配的成功路线(见 §9) |
| `UserExchange` | 追问、确认、接管这三种要用户参与的交互 |

步数上限默认不设:设置 › 任务运行 › 每个任务的步数上限(`FeatureFlags.taskStepLimit`,1–500,存 0 即不限制)。`TaskRun` 开工时读一次,放进 `RunSpec.stepBudget`,再作为 `AgentLoop` 的 `maxSteps`(评测的 `EvalHooks.maxSteps` 优先;都没有时是 `AgentLoop.UNLIMITED_STEPS`);中途改设置只管之后开工的任务,任务被打断后不会接着跑,也就不会换上新值。有上限时:守卫按它分级(`OperationGuards(maxSteps)`,回到同一状态、兜圈、一直没有新状态这三种叫停的门槛按上限的一部分算,见 `GuardRules.lateStop`;不限制时这几级叫停不会到),走到一半和四分之三时提醒进度(上限不少于 10 步时),步数用完后再给最后一轮收尾(只给 `end_not_done`,记作第上限加一步),收尾也没答出就以 `FailKind.StepLimit` 结束。开始事件记下这次的上限(`RunEvent.TaskStarted.stepLimit`,不限制不写,概要里也有),运行详情页据此显示「用了几步 / 上限」(收尾那一轮不算,不超过上限);不限制时不显示这一行。

设置现在管的范围(都经 `TaskRun.execute`):对话界面(含悬浮胶囊、浏览器页上问)、定时触发的任务、电脑经 CLI / MCP 交来的任务都按设置;定时的条件核查(只读的那一次检查)不受它管,一直不限制;子任务不看设置,步数由 `spawn_agent` 自己定(默认 15 步,模型可要到 40 步,`SpawnAgent`),也不算进父任务的步数。

### 结果判定

- **没有「完成」工具**:模型不带任何工具调用的回复就是回答;带工具调用时附带的文字当作过程说明显示。被插话打断时记下的那段字不是回答,只是让模型知道用户已经看到了什么(见「收件箱与插话」)。
- **`end_not_done`**(`core/general/OutcomeTools.kt`):做不成或只能做一部分时用它结束,`message` 写清已确认做了什么、卡在哪、现在界面上是什么;`subtype` 是 `blocked`、`login_incomplete`、`captcha_incomplete`、`route_failed`、`device_unavailable`、`interaction_failed` 之一。
- **交回答之前**:空回复或被截断提醒一次;有的推理服务偶尔没把模型的工具调用解析出来,调用原样成了正文(`LeakedToolCall`,`core/agent/LeakedToolCall.kt`:常见的调用标记后面跟着 Maeve 的工具名),这不是回答:退回一次让模型经工具接口重新调用,运行记录里记 `tool_call_as_text`;紧接着又漏就以模型出错结束(`StopRules.toolCallAsText`),不把原样的标记交给用户,正常调过工具后重新算;用户刚回答了什么而 Agent 什么也没做,提醒一次;最后一次改变界面的动作没读回结果,提醒一次。回答的正文明显不是用户最近那句话的语言(`ReplyLanguage` 按字母所属的文字数,汉字按字、其余按词,引号括号里的原文和只列名字的行不算;用户那句只算他这次打的字,带的屏幕、浏览器页的页面说明、引的那一段、附件行和插话标记不算(`UserWords.typed`);开局只发了屏幕、只选了字或只带了附件时不查,插话只发了屏幕或只选了字时按上一句),退回一次让模型整段改写成用户的语言,改写后还不对照样交出。
- **来源由系统生成**:`AnswerSources`(`core/verify/AnswerSources.kt`)从证据账本 `EvidenceLedger` 里挑出支持回答的观察(最多 6 条),作为回答下面的「来源」;不由模型自己写。
- **后台结果分类**:回答送达后,`TaskRun.settleOutcome` 在后台用 `OutcomeJudge`(`core/agent/OutcomeJudge.kt`,优先用轻量模型,30 秒超时)把这次运行分成 `RunOutcome` 的 Done / Partial / NotPossible / Unknown。它不改回答,结果写进运行记录和对话记录;定时任务据此标记这一次是否失败。学习与回放只认 Done:准入只有一处判断 `LearnGate.done`(`core/agent/LearnGate.kt`):交出了回答、判下来是 Done、没有退回一次后仍没对上的值;核查跑过的还要核实通过(跑过没通过照样挡住,没跑不挡)。个人技能和 App 知识的起草、试用转正式、经验路线的记录和成功计数都只问它(见 §9、§11);记不记路线由 `LearnGate.shouldSaveRoute` 一处判断(开着路线、办成、有运行编号、目标不带用户屏幕)。
- **独立核查**:`Grounding`(回答里的数字、编号、引文在读过的内容里有没有,算式对不对)和 `Verifier`(独立的核查模型)都在 `core/verify/`,由 `reviewFinalAnswer` 开关控制。产品目前关着(`TaskRun` 里 `reviewFinalAnswer = false`),所以回答都记为「未核实」,见 roadmap「已知问题」;学习看的是结局判定,不受它影响。

## 4. 唯一执行入口与工具

- **唯一入口**:所有改变外部世界的动作都经 `ActionExecutor`(`core/action/ActionExecutor.kt`)。`ToolDispatcher.execute` 要一个 `ActionPass`(`core/tool/ActionPass.kt`),它的构造函数是 internal,只有 `ActionExecutor` 能造,从编译层面杜绝绕过;只读调用走 `executeRead`,不是只读的会被拒绝。执行顺序固定:守卫 → 锁屏检查 → 不可逆动作检查与同意 → 执行 → 动作后核对 → 记录。
- **对外动作之间的间隔**(`core/action/OutwardPacing.kt` 纯逻辑,`ActionExecutor` 照它等):不可逆判定认出的发送、提交(含一点就联系别人、投申请的按钮,如「立即沟通」「打招呼」「投递」「Say hi」「Easy Apply」)在同一网站(按可注册域名)或 App(按包名)里连着做时,第一次不等,之后和上一次至少隔 8–20 秒随机的一段,最近 30 分钟里做得越多上下限各加 0.5 / 1 秒,至多 30 秒;从上一次做完算起,中间读页面、想下一步的时间算在内,换不换页都算(换页的停留另由网页的节奏管)。手机、网页同一个判定、同一套间隔;自主执行时判定照样跑来认对外动作(按模型手里那一屏,不另读屏),只是不拦。等在判定之后、动作开始之前,任务还能取消;等了多久记进这次调用的 `waitMs`,不算进操作用时。只由 App 的任务装上(`AgentLoop(outwardPacing = …)`),核心测试默认不等。
- **这一步谁发起**:`Origin`(`core/action/Origin.kt`)= Model / Batch / Script / Replay / McpTool,各有策略(能否用用户的同意放行付款、遇到守卫警告是否停)。复合工具(一次多步、沙箱里的 `maeve phone`、网页脚本)内部的每个动作经 `SubActions`(`core/tool/SubActions.kt`)回到同一个入口,和单步工具同一条执行、记录路径。
- **工具声明**:每个工具一个类,实现 `AgentTool`(`core/tool/AgentTool.kt`),声明自己的动词、风险(Read / Write / External)、所属能力、能否进一次多步、能否进经验路线、并发方式等;代码按这些属性办事,不写死工具名。`ToolRegistry` 按任务筛出可用工具,一个任务内工具定义冻结不变。
- **工具分两层,用到才展开**(`core/tool/ToolGroups.kt`、`ToolDispatcher.offeredNow`):一直都在的是通用工具(问用户、没办成就结束、等待、知识与技能、记忆、定时、子任务……)和不收的能力(MCP);手机、网页、沙箱各是一组(`Capability.folds`),开局只给入口工具(`AgentTool.entry`),用 `prompts/entries/<工具名>.md` 的短说明:`phone_launch_app`、`web_navigate`、`run_command` 调用后展开它那一组(`Entry.Opens`),`phone_list_apps`、`web_read_page`(读一个网址的正文)单用就够,不展开(`Entry.Alone`)。接管、报障碍、回看画面、一次多步只在有屏的组展开后才给(`AgentTool.withScreens`,算作 `screens` 一组)。展开后下一次请求起给整组全文,入口换回全文,能力那一段说明(`capabilities/<能力>.md`)也拼进系统提示词,本任务里一直留着;结果里的 `moreTools` 说一句。模型凭记忆调了收着的工具照常执行,同样展开它那一组(参数不对也展开,下一次看得到全文再改)。顺序固定:一直都在的 → `screens` → 手机 → 网页 → 沙箱,展开一组只改从它那一组起的部分,请求前缀因展开最多失效一次。开局预先展开:上一轮真的用过的组(`Turn.groups`,`AgentLoop.usedGroups` 记下、会话文件里存着;单用就够的入口不算用过),和能力按这次的话认定一定用得上的(带着工作区里的文件时的沙箱,`Capability.preload`)。只读的任务里跑命令不给,沙箱一组没有入口,就一开始展开。子任务、定时触发和别的来源都一样。运行记录里展开记 `tools_opened`、预先展开记 `tools_preloaded`;`PromptVersion` 按每组都展开的样子算,同一版提示词不因这次展开了哪几组而不同。
- **审批**:`ApprovalPolicy`(`core/action/Approval.kt`)只问一种动作:花钱的那一步(`Risk.payment`:不可逆判定认出的付款、下单,或屏上有带币种(¥、元、$)的应付金额时命中判定的对外、不可逆动作,见 `ClassifiedRisk`;繁体界面的字先换成简体,按同一套词表判,`core/uistate/Hant.kt`)。别的一律直接做:发送、发帖、提交、删除、联系别人、改账号或安全设置、往密码框里打字、看图找到却看不出是什么的点、读不出目标的点击,都不问。花钱的一步只有手机上发起、用户在场时问(`Ask`);渠道、定时、MCP 发起的运行、子任务和无人在场的运行问不到人,一律拒绝(`approval_unavailable`)。同意只有一种:只放行紧接着的那一个匹配操作(`ConfirmationGate`:同一 App、同类动作、对象在屏上、申报的金额在屏上,3 步内、中间没做别的写操作)。付款本身(判定是 pay)不经同意,交给用户亲手付(`hand_to_user`,reason=payment)。审批结论都记进运行记录(`RunEvent.Approval`,对外的、花钱的和没放行的),核查据此知道任务做过对外的事。没有审批档位,也没有任务内、长期授权。
- **询问 / 自主**:全局一个开关,在设置 › 任务运行最前面的「做事方式」一组(「自主模式」,测试标签 `work-autonomous`),默认关即询问;值存在 `ApprovalStore` 的 JSON 里(`autonomous`)。`ApprovalControl`(`settings/ApprovalControl.kt`)在每个任务开工时给一份 `RunApproval`(自主与否取全局的),`TaskRun` 据此装:询问时 `confirmations = ConfirmationGate()`、`autonomous = false`,花钱的一步被拦成 `confirmation_required`,下单一类由模型调 `request_confirmation`(必须写出屏上的金额,写不出金额的申请直接退回、不交给用户),用户在对话页的卡片、悬浮胶囊的卡片或通知上回答(只同意这一次 / 不同意),回答经 `TaskService` → `TaskRun.confirm` → `RunInbox` 交给循环;自主时 `confirmations = null`、`autonomous = true`、不提供 `request_confirmation`,判定照跑(对外动作的间隔照样认)但连付款也不拦。换开关(`ApprovalControl.setAutonomous`)存下来管之后的任务,正在跑的任务都跟着换:开工时是询问的任务中途换成自主,下一步起不再拦(已经弹出的确认照常等回答);开工时是自主的任务中途换成询问,留给下一个任务(工具和提示词一个任务内不变)。悬浮胶囊里发起、接着聊的任务、子任务、定时触发都按这个开关;电脑经 CLI / MCP 交来的任务例外:`ApprovalControl.start(remote = true)` 一律按询问开工、不跟开关变,付款这一步在这类任务里永远不做(见「电脑连接」一节);子任务本来就只读,询问时它过关卡、花钱的一步一律拒绝。定时任务这类无人在场的运行在询问模式下遇到花钱的一步不暂停等人:执行入口直接拒绝(`approval_unavailable`),模型照实说没做成哪一步,别的照常做完,结果照常通知。自主模式下 `ConfirmationGate` 不存在,`ConfirmDecision.judge` 里自主的提前返回排在「不对 Maeve 自己动手」(`ownApp`)检查之前,所以这层保护在自主模式下不生效;询问时 Maeve 不操作自己。
- **改名后的旧工具名**:`LegacyNames`(`core/tool/LegacyNames.kt`)把旧名对应到现在的工具,只用来读旧的路线、运行记录和个人技能;模型调用旧名时只提示新名,不替它执行。

## 5. 观察、动作、地方

- **观察**是核心消费的唯一单位:`Observation`(`core/observation/Observation.kt`)带种类 `ObsKind`(Read / Output / Reply / UserSaid / Attachment / Fact)、出处(能力 id)、地方、标题、文字、画面、可信度、步数、时间和编号。证据账本、来源、核查、运行记录都读它。
- **不可信内容**(网页、子任务结果等)在构建观察时一次性包进 `<untrusted>…</untrusted>`(`core/observation/Untrusted.kt`)。
- **地方** `Place`(同一文件):App、Site、Page、Command、File。知识按地方补给模型(§11)。
- **界面状态**(`core/uistate/`):控件树、元素编号与过期检测、等界面稳定、动作效果(`ActionEffect`,每个改变界面的动作报回实际效果、输入读回核对;手机上点、手势看不出变化而这一屏的树读不出那一处——树不能用,或缺了一块而点不在任何元素上——种类照旧是 `no_effect`,另标 `treeBlind`,提示改说「看不出变化不等于没点上」,不说多半没点上;按种类做的事(连着没效果的提醒、多步提前停、统计)不变;动作之后的界面里新出现「到上限了」的短文字——次数已用完、已达上限、明天再来、操作过于频繁、limit reached 一类,提示条、弹窗、页面弹框的话,动作前就在屏上的不算(`LimitNotice`)——结果里写 `limitReached` 和停下的提示,一次多步停在这一步)。手机和网页共用这一套。分发器每次调用前后经 `SurfaceLayer` 让它做界面上的准备与收尾(动作前看一眼、比出效果、附上动作之后的界面),工具引擎本身不认识它(见 §1)。执行入口的不可逆判定与审批、回放按读屏判定时直接用它(`ActionTarget`、按描述找目标)。
- **可视化**(`core/visual/`):画面帧编号、`view_past_screens` 回看、画面差异。画面只是让用户看到 Maeve 在做什么、以及文字读不出时的补充观察;办事靠的是界面状态。

## 6. Android 能力与平台组件

手机操作位于 app/cap/phone；内置助手、虚拟屏、无障碍、配对和端口发现继续由 Android 负责。浏览器能力位于 app/cap/web；Linux 沙箱位于 app/cap/sandbox。核心端口、任务循环、工具分发和授权边界不依赖 Flutter。

模型与服务商配置、账户登录、定时任务、远程任务、MCP、语音与通知仍由 Android 与 core 提供。原 Compose 页面里被业务引用的状态、文案和格式化函数已拆成独立模型文件，应用界面不再由它们绘制。

系统悬浮胶囊、原生画中画和文件查看器保留 Compose，属于平台组件。它们不注册应用启动入口，也不提供另一套应用导航。

## 7. Flutter 界面

所有启动器别名与通知入口统一回到 `MainActivity`，它是缓存 Flutter 引擎的宿主。默认即为 Flutter，冗余 Compose Activity 和应用页面已移除。引擎由宿主注册 UI、入口事件和聊天桥插件；Activity 重建不销毁缓存引擎。

- `flutter/lib/app/`：应用状态、聊天、会话抽屉与工作台。
- `flutter/lib/ui/`：设置、真实模型配置、系统授权、外观与关于页面。
- `flutter/lib/design/`：完整配色、对比度、字阶、动效和材质。
- `flutter/lib/bridge/`：平台通道与聊天事件契约。

聊天仍以 Android 的真实会话和执行事件为数据来源。Flutter 不另造一个任务循环。未接通原生能力的原型页面与控制已删除；模型、系统授权与外观使用 MaeveSettingsPlugin。详细范围见 [入口审计](flutter-capability-audit.md)。

当前设计是 [紫色的下一幕](../spec/visual-tokens.md)；宿主与 UI 的边界见 [迁移契约](../spec/flutter-visual-parity.md)。

## 8. 测试

core 的纯逻辑测试覆盖能力依赖、任务运行、工具与存储边界；Android JVM 测试覆盖 ViewModel、服务协调和平台适配逻辑。设备测试覆盖 Flutter 启动、平台服务、虚拟屏和原生悬浮层。旧 Compose 应用页面测试随页面退役，Flutter 行为与布局测试接替它们。

Flutter 页面布局矩阵覆盖 8 个设置页的顶部与末尾、窄屏、大字和宽屏；行为测试用假平台事件驱动真实组件，测试发送、停止、引用、会话管理和抽屉等。CI 编译设备测试 APK，避免设备夹具只在运行时才暴露编译问题。

## 历史详细说明

[迁移前详细架构](../reference/compose-before-flutter/architecture.md) 保存原有能力实现说明与历史 UI 描述。其 Compose 页面路径、入口和界面步骤已经退役，应以当前源码和上面的 Flutter 边界为准。
