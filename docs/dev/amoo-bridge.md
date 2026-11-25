# amoo 文件桥

> 存档：`tag amoo-bridge-v1`（功能代码在 `bcedaa2a`，本行所在提交只是存档说明）。相关提交按顺序是 `6bad75d3`（文件桥）、
> `f033fab7`（两个真机坑）、`5f9f05b6`/`d7bab743`（一键接入）、`5b12e6d5`（页面只剩两张卡）、
> `bcedaa2a`（技能按版本覆盖升级）。分发用 perf 变体，产物是 assemblePerf 生成的 app-arm64-v8a-perf.apk / app-universal-perf.apk（在 app/build/outputs/apk/perf 下，不进仓库）。
> 只用在同一套 system app（`com.stepos.ailauncher` + `com.stepos.stepclaw`）的机器上。

Maeve 与 amoo 之间不靠网络，靠一个双方都读得到的目录传话。

## 为什么不用网络

amoo 跑在 stepclaw 的 OpenClaw 沙箱里，和手机上的 App 不是一个网络命名空间：

- 沙箱里 `curl http://127.0.0.1:18770/api/v1` → `exit 7`（连不上），手机上同一地址是 401/200；
- 手机 `wlan0` 的局域网地址（例如 `10.73.194.51:18770`）在沙箱里是 `exit 28`（超时，路由丢包）；
- 手机侧能看到的 `127.0.0.1:8765 / 18789 / 18791` 是 ClawX 从手机**单向前向**代理进沙箱的端口，没有反向通道；
- 网关又被 `[ConfigGuard]` 强制 `gateway.bind=loopback`，所以「让 Maeve 主动连网关」也绕不开设备身份：客户端的握手要 Ed25519 设备密钥 + nonce 挑战 + `device.pair.approve`，不是发个 token 就行。

## 通道

`/sdcard/Android/media/<包名>/`：amoo 有媒体/全盘权限读得到；Maeve 是目录属主，读写自己的 Android/media 不需要额外权限。
（`/sdcard/Android/data/<包名>/` 不行——scoped storage 会把别的 App 的这一层隐藏成 `E_NOT_FOUND`。）

```
/sdcard/Android/media/com.daiyimo.maeve/amoo/
├── in/<id>.json    amoo 写 → Maeve 领任务
└── out/<id>.json   Maeve 写 → amoo 读结果
```

- `<id>`：`[A-Za-z0-9_-]`，1–64 字符，建议用时间戳。
- 请求：`{"goal": "要做的事"}`（goal ≤ 4000 字符，超出拒绝；完整请求 ≤ 16 KiB）。
- 结果：`{"id":"…","task_id":"…","status":"running|done|failed|cancelled","answer":…,"note":…}`。
- Maeve 每 0.2 秒扫一次 `in/`，同时最多 4 个请求在跑；认领一个请求会先把它改名成 `<id>.claimed`，避免重复认领。
- 一个任务最多等 15 分钟；超时就写一条 `status":"running"` + note 让 amoo 稍后再问。
- 结果文件的 `status` 用的是 Maeve `/api/v1` 任务接口同一套词（`done/failed/cancelled`），amoo 那边不用学第二套。
- 等待手机上的单次确认时，文件仍返回 `status: "running"`，并带 `waiting_for: "confirmation"` 和本机处理提示。既有技能继续轮询同一编号，处理后再读取终态。
- 普通界面操作遵循手机上的「操作确认方式」。自主执行时连续操作，询问模式下通过本机通知或任务卡片确认。单次批准绑定这次调用和当时的页面，页面变化需要重新确认。文件桥没有提交批准的接口。
- 微信交给系统 Amoo 的手机任务使用本轮分配的文件编号。微信回复可以按这个编号找回 Maeve 的终态，应用恢复时沿用原请求，避免重复派发。

## 给 amoo 的话

一键接入时，Maeve 会通过无障碍把下面这段话打进 amoo 聊天框（内容来自 `AmooSetup.setupInstruction`）：

> 建一个叫 maeve-bridge 的技能：需要手机干活时，把 {"goal":"要做的事"} 写进
> /sdcard/Android/media/<包名>/amoo/in/<id>.json（id 用时间戳），
> 然后每 2 秒读一次 /sdcard/Android/media/<包名>/amoo/out/<id>.json，
> status 变成 done、failed 或 cancelled 就把 answer 原样告诉我，最多等 15 分钟。

实测（2026-11-05，设备 ZYPZ261A01M68700159）：amoo 接到这句话后会自己写文件、按 2 秒轮询、
读到结果后原样汇报；失败时也能把 `note` 念出来（例如「模型停止了操作,没有给出回答。」）。

## 两个真机踩过的坑

1. **技能在会话启动时加载。** 装完 `maeve-bridge` 后，当前这轮对话看不到它（amoo 会直说「当前会话的技能清单是在会话启动时…」），
   要开一个新会话才会带上。实测：新会话里说「打开微信搜《三角洲行动》小程序看今天口令」，amoo 没再回「GUI 自动化暂未开放」，
   而是自己写了 `in/1793868300.json`（goal 就是那句中文任务）并开始轮询。
2. **轮询预算要给足。** 第一次它 4 分钟就放弃并反问用户，而 Maeve 跑完微信小程序用了约 6 分钟，结果晚到。
   技能的说明里写死「每 2 秒读一次、最多等 15 分钟、中途不要放弃」之后，同一轮就能把结果原样念出来。

## 开关

页面上不再给开关：点「开始接入」时会顺手把文件桥打开（`FeatureFlags.setBridgeAmoo(true)`）；关着的时候 Maeve 不碰这个目录。

## 注意

- 目录里的文件是明文的，任何能读 `/sdcard/Android/media` 的 App 都能看；不要往里放密钥。
- amoo 写进来的 goal 会当成 Maeve 的正常任务执行，等于把「让它动手机」的权限给了 amoo——
  只在自己信任的 amoo 会话里用。

## 一键接入（Amoo 页最上面那张卡）

技能只能由 amoo 自己写进它的沙箱（`/root/.openclaw/workspace/skills/`），Maeve 没有那条路；
但 Maeve 有无障碍服务，可以把那段说明「打字 + 发送」进 amoo 聊天框。于是：

1. 用户点「开始接入」，`AmooSetup` 进入 `Waiting`；
2. 用户切到 amoo 聊天（我们没法从 App 侧拉起未导出的聊天页（`com.stepos.ailauncher.chat.activity` 下那个））；
3. `AmooIntercept` 每次扫到 amoo 聊天页就转给 `AmooSetup.onAmooChat(root)`：
   - 用 `com.stepos.ailauncher:id/et_input_bar` 上的 `ACTION_SET_TEXT` 填入说明；
4. 说明里让 amoo 装 `maeve-bridge` 技能并**立刻跑一次**试任务（`{"goal":"只回答三个字:你好呀"}`），同时覆盖写 `skill.json`；
5. 三分钟内 `out/` 出现新的结果文件 ⇒ 卡片显示「amoo 已经会用 Maeve 干活了」；若 `skill.json` 里的版本不是当前版本，直接报失败（说明跑的还是旧技能）。盯 `out/` 而不是 `in/`：`in/` 的请求文件被桥领走后会删掉，经常看不到。

两个实现细节：

- 接入期间即使「截获 amoo 聊天任务」关着也要收无障碍事件，否则没人替我们填输入框（`AmooSetup.active`）；点开始时也会把文件桥打开（`FeatureFlags.setBridgeAmoo(true)`），页面上不再单独给开关。
- 我们在输入框里填的那段话会被自己看到，所以 `AmooIntercept.handle()` 在 `onAmooChat()` 返回 true 时直接跳过截获。

## 覆盖升级

- 技能名固定 `maeve-bridge`，路径固定 `~/.openclaw/workspace/skills/maeve-bridge/SKILL.md`；说明里明确要求**整份覆盖**，禁止 `maeve-bridge-2`、`maeve-bridge-v2` 这类同名变体，并要求 `rm -rf` 清掉旧残留，最后只留一个目录。
- `AmooSetup.SKILL_VERSION` 是这份说明的契约版本：改了技能口径或桥协议就 +1；每次一键接入都把它写进 SKILL.md 的 frontmatter（`version: <N>`）和 `amoo/skill.json`（`{"skill":"maeve-bridge","version":<N>}`）。
- Maeve 侧读 `amoo/skill.json` 校验版本，版本对不上就报失败，不会假装接好——老用户换新 APK 后再点一次「开始接入」，能确认真的换成了新版，不用手动去沙箱里删技能。
- 只改了技能文案（版本没动）也照样是整份覆盖，不会堆出第二份。

**只对同一套 system app 成立**：输入框/发送按钮的 view id 是按 `com.stepos.ailauncher` 写死的，
同型号（StepOS 这套系统应用）可以直接用；换 ROM 要重新核对那两个 id。

## 分发说明

别人的手机装同一个 APK 后：设置 → 常用 → Amoo → 「开始接入」→ 切到 amoo 聊天。
前提只有两个：无障碍服务开着、`com.stepos.ailauncher` 是同一版本（同一个 amoo 聊天界面）。
以后 Maeve 升版只需再点一次「开始接入」，技能会被覆盖成新版本。
## 连接状态那张卡

看的是**文件桥**，不是电脑连接：

- 文件桥没开 ⇒ 「文件桥没开，点上面的「开始接入」」；
- 开着但 `out/` 里还没有结果文件 ⇒ 「文件桥已开，还没收到 amoo 的请求」；
- 有结果文件 ⇒ 「文件桥已开，最近一次请求 HH:mm:ss」（取 `out/` 里最新文件的时间）。

之前这张卡显示的是 Maeve 的 HTTP/MCP 服务（`127.0.0.1:18770`）。那条路对 amoo 是死的（见上面「为什么不用网络」），
留着只会让人以为"没连上"，所以改成文件桥状态；`127.0.0.1:18770` 那套仍然给电脑用，入口在「设置 › 高级 › 连接电脑」。

## 中断恢复与配额

- 先把提交摘要和稳定 task_id 写入私有账本，再启动原有 RemoteTaskService；不增加执行器。
- `.claimed` 在重启后重新扫描，只查询原 task_id。已登记但找不到宿主回执时输出 `receipt_unknown`，不重新发起任务。重试必须使用原编号、原 goal；编号内容冲突直接拒绝。
- 关闭开关时收到的请求会持久记为拒绝，重新开启不执行这些旧请求；关闭不会代替取消已开始的任务。
- 支持平铺 JSON 的 `schemaVersion: 1`、`requestId`、`createdAt`、`expiresAt`（毫秒）。v1 最长一天有效；过期请求不能新启动，已有回执仍可对账。旧 `{goal}` 格式兼容。
- 重复字段（含 Unicode 转义同名）、嵌套值、未知字段和超限报文均拒绝。每轮最多检查 100 条目录项，轮转扫描避免前排 claimed 饿死后续请求，最多 4 个处理协程；结果最多 64 KiB，保留七天并设 16 MiB 总字节上限。
- 私有去重账本最多 4096 项；为兼容没有时间绑定的旧编号，满额时拒绝新请求，不能自动删除旧 unknown 后重复执行。去重记录的长期生命周期仍需完善；不受信任的其他写入者仍能向共享目录堆文件，应用只保证有界扫描与自己的准入/输出配额。
- 等待十五分钟只表示 deferred，原 task_id 仍可查询；后台继续对账最终结果，不伪装成取消。

- 私有待处理请求上限为 100，运行中/延期/unknown 均占用名额；完成结果持久化后才释放。超额返回持久的 queue_full 拒绝，容量恢复后也不会执行被拒的旧 ID。输出写入由同一互斥锁执行，避免并发检查超出 16MiB；输出目录检查最多 4097 项，达到 4096 项上限时拒绝新增结果，过期临时文件会清理。
