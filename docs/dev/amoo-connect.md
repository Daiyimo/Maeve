# amoo 快速连接页

设置 → 常用 → **Amoo**（「手机操作」与「微信」之间；`SettingsPage.Amoo` → `app/src/main/java/com/daiyimo/maeve/ui/AmooScreen.kt`）。
它解决的是「把 Maeve 的地址发进 amoo 聊天，amoo 连不上」这件事。

## 为什么发地址、发命令都没用

amoo 是跑在本机 OpenClaw（stepclaw）网关里的助手：它的工具来自网关配置和手机端的「原子能力」白名单，
不是通用 MCP 客户端。聊天里贴 URL 只会被当文本；贴命令行会被当 shell 尝试执行，再被 Maeve 的无障碍截获成任务
（见 `docs/dev/amoo-intercept.md`）。**它不会替人把聊天里的命令跑完再把结果贴回来**，所以这一页不再给命令。

## 页面上的三件事

1. **连接状态 + 现在自检**：按钮在进程内真发一次 `GET http://127.0.0.1:18770/api/v1`（3 秒超时），
   200 就把回包里的 `server_version` 显示成「通了 · Maeve 0.1.36」，否则显示「不通 · 原因」。
   网络访问开着用网关令牌，只开 USB 时用 USB 令牌（回环口两套令牌都认，见
   `core/src/main/kotlin/com/daiyimo/maeve/core/remote/RemoteAccess.kt:48-53`）。
2. **复制网关配置**：`McpRuntime.clientConfig(McpRuntime.url("127.0.0.1", port), token)` 的 JSON，
   加到 stepclaw 的 `mcp.servers.mika` 后重启网关，amoo 的工具里才会有 `mika_run_task` / `mika_wait_task`
   这些（`core/src/main/kotlin/com/daiyimo/maeve/core/mcp/McpTaskTools.kt:176-182`）。
3. **复制那句话**：`用 mika_run_task 让我的手机执行：<要做的事>，完成后用 mika_wait_task 等结果，把答案原样告诉我。`
   网关没配好时，直接在 amoo 里说要做的事，靠下面的截获开关也能用。

## 前提与退路

- 需要先打开「电脑连接」（Wi-Fi/VPN 或 USB），否则 18770 没有监听，页面会先给打开开关的按钮。
- amoo 的 VM 与手机共享 `127.0.0.1`（stepclaw 自己的 MCP 在 8765，就是同一条回环），所以本机地址可用。
- 网关侧若日志里出现 `[bundle-mcp] failed to start server "mika" … fetch failed`，重启 stepclaw 让它重新拉配置。
- 网关配置里带着令牌，别转发到公开的地方。

## 文案

`amoo_page_title`、`amoo_page_sub`、`amoo_status_title`、`amoo_status_listening`、`amoo_status_off`、
`amoo_self_check`、`amoo_self_check_ok`、`amoo_self_check_fail`、`amoo_gateway_step`、`amoo_gateway_sub`、
`amoo_copy_gateway`、`amoo_prompt_step`、`amoo_prompt_sub`、`amoo_copy_prompt`、`amoo_prompt_text`、
`amoo_connect_enable`、`amoo_connect_enable_sub`、`amoo_connect_note`，10 个语言目录齐全。
连接电脑页不再有 amoo 相关条目，分段标题沿用 `mcp_choose_client`（「电脑端用法」）。
