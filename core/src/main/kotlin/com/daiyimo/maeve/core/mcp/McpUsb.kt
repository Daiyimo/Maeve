package com.daiyimo.maeve.core.mcp

import com.daiyimo.maeve.core.remote.RemoteTaskApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * 走 USB 的电脑连接:手机侧只在回环地址上监听,电脑先 `adb forward`,再从本机连进来。
 * 这里集中放双方约定好的东西:连接信息文件的内容,以及电脑上要跑的那段脚本。
 * 脚本只使用 Android 自带的 sh 与 toybox 工具,不依赖任何第三方程序。
 */
object McpUsb {
    /** 连接信息文件名。放在 App 的外部文件目录里:Android 11 起别的 App 读不到,电脑却能凭 shell 身份读。 */
    const val INFO_FILE = "mcp-usb.json"

    /** 电脑上执行的脚本名,和「用电脑启动」的辅助脚本放在同一个目录。 */
    const val SCRIPT = "connect-computer.sh"

    private val pretty = Json { prettyPrint = true }

    /**
     * 给电脑上的 MCP 客户端准备的连接信息:地址、请求头,以及先要做的端口转发。
     * [boot] 是本次开机的编号(等同 shell 里 `settings get global boot_count`),
     * 脚本靠它判断读到的文件是不是上次开机留下的。
     */
    fun info(port: Int, token: String, boot: String): String {
        val config = buildJsonObject {
            put("api_version", 1)
            put("api_url", "http://127.0.0.1:$port${RemoteTaskApi.ROOT}")
            put("transport", "streamable-http")
            put("url", "http://127.0.0.1:$port${McpRpc.PATH}")
            putJsonObject("headers") { put("Authorization", "Bearer $token") }
            put("token", token)
            put("port", port)
            put("forward", forward(port))
            put("boot", boot)
        }
        return pretty.encodeToString(JsonObject.serializer(), config) + "\n"
    }

    /** 电脑上把本机端口转到手机回环端口的命令;数据线拔插之后要重新执行。 */
    fun forward(port: Int) = "adb forward tcp:$port tcp:$port"

    /**
     * 电脑通过 USB 调试执行的脚本(`adb shell sh <scriptPath>`,关闭时在末尾加 `off`)。脚本本身不含口令或令牌,可以一直留着。
     *
     * 打开:先跑一次「用电脑启动」的 [helperScript](手机助手不在就拉起,已在就提示一句),
     * 再用 shell 身份拉起 Maeve 的前台服务 [component],动作是 [onAction],
     * 等本次开机的连接信息文件 [infoPath] 出现后打印出来。
     *
     * 关闭:拉起同一个服务,动作换成 [offAction],等连接信息文件被删掉,令牌随之作废。
     *
     * 调用者不是 shell 身份时直接拒绝:该服务只接受拥有 shell 级权限的调用方,普通 App 起不动它。
     */
    fun script(
        scriptPath: String,
        helperScript: String,
        infoPath: String,
        component: String,
        onAction: String,
        offAction: String,
        port: Int,
    ): String {
        val again = "adb shell sh $scriptPath"
        val quotedHelper = quote(helperScript)
        val quotedInfo = quote(infoPath)
        val quotedComponent = quote(component)
        val quotedOn = quote(onAction)
        val quotedOff = quote(offAction)
        val forwardCommand = forward(port)

        return listOf(
            "#!/system/bin/sh",
            "# Written by Maeve. Lets AI tools on a computer use Maeve over USB: $again   (turn off: $again off)",
            "if [ \"${'$'}(id -u)\" != \"2000\" ]; then",
            "  echo \"Run this from a computer over USB: $again\"",
            "  exit 1",
            "fi",
            "info=$quotedInfo",
            "if [ \"${'$'}1\" = \"off\" ]; then",
            "  out=${'$'}(am start-foreground-service -n $quotedComponent -a $quotedOff 2>&1)",
            "  case \"${'$'}out\" in *rror*|*xception*) echo \"${'$'}out\"; exit 1 ;; esac",
            "  i=0",
            "  while [ -f \"${'$'}info\" ]; do",
            "    i=${'$'}((i + 1))",
            "    if [ ${'$'}i -gt 10 ]; then echo \"Maeve did not confirm. Open Maeve > Settings > Computer access (MCP) and turn off USB there.\"; exit 1; fi",
            "    sleep 1",
            "  done",
            "  echo \"Computer access over USB is off. The old token no longer works.\"",
            "  exit 0",
            "fi",
            "if [ -f $quotedHelper ]; then",
            "  sh $quotedHelper || echo \"Phone control is not ready yet; tasks that operate apps wait until it is.\"",
            "fi",
            "boot=${'$'}(settings get global boot_count 2>/dev/null)",
            "out=${'$'}(am start-foreground-service -n $quotedComponent -a $quotedOn 2>&1)",
            "case \"${'$'}out\" in *rror*|*xception*) echo \"${'$'}out\"; echo \"Is Maeve installed and up to date?\"; exit 1 ;; esac",
            "i=0",
            "until grep -q \"\\\"boot\\\": *\\\"${'$'}boot\\\"\" \"${'$'}info\" 2>/dev/null; do",
            "  i=${'$'}((i + 1))",
            "  if [ ${'$'}i -gt 15 ]; then echo \"Maeve did not open the connection. Open Maeve > Settings > Computer access (MCP) to see why, then run this again.\"; exit 1; fi",
            "  sleep 1",
            "done",
            "echo \"Computer access over USB is on. On the computer, run: $forwardCommand\"",
            "echo \"Then add this MCP server (Streamable HTTP) to your AI tool:\"",
            "cat \"${'$'}info\"",
        ).joinToString("\n") + "\n"
    }

    /** 把任意路径包进单引号,内部单引号按 shell 的写法转义。 */
    private fun quote(s: String) = "'" + s.replace("'", "'\\''") + "'"
}
