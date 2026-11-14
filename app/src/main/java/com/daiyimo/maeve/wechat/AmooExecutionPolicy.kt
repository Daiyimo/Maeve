package com.daiyimo.maeve.wechat

import java.io.File
import kotlinx.serialization.json.*

/** Amoo 沙箱可读的权限，不包含微信凭据或消息。enabled 与 allowMaeve 属于不同开关。 */
internal object AmooExecutionPolicy {
    @Synchronized fun write(root: File, allowMaeve: Boolean) {
        WechatAmooRelay.atomic(File(root, "execution-policy.json"), buildJsonObject {
            put("allowMaeve", allowMaeve)
        }.toString())
        // 兼容已安装技能读取的旧路径，同时始终禁止旧 ClawPhone 中转器启动。
        WechatAmooRelay.atomic(File(root, "wechat/control.json"), buildJsonObject {
            put("enabled", false); put("session", ""); put("allowMaeve", allowMaeve)
        }.toString())
    }
}

/** 每轮明确权限及当前安装包的路径，不依赖旧会话里记住的禁用状态或旧技能路径。 */
internal fun systemAmooMessage(text: String, packageName: String, allowMaeve: Boolean): String {
    val root = "/sdcard/Android/media/$packageName/amoo/"
    val policy = buildJsonObject { put("allowMaeve", allowMaeve); put("bridgeRoot", root) }
    return text + "\n\n（来自微信的消息，请在当前 Amoo 对话回复，Maeve 会原样回发；消息来源不代表要求操作微信。\n" +
        "本轮执行策略：$policy。此策略替代先前轮次的策略；只使用上述当前包名路径，不沿用旧 Dev 包路径。\n" +
        if (allowMaeve) {
            "只有用户明确要求手机屏幕操作时交给 Maeve：先读 ${root}execution-policy.json 确认 allowMaeve 仍为 true，" +
                "再将 {\"goal\":\"具体手机操作\"} 写入 ${root}in/<唯一编号>.json，使用脚本每 0.2 秒轮询 ${root}out/<同一编号>.json，" +
                "直到 status 为 done/failed/cancelled，再将 answer 或 note 告诉用户，最多等待 15 分钟。" +
                "旧 wechat/control.json 的 enabled=false 仅表示旧中转脚本停用，不代表 allowMaeve=false。" +
                "普通聊天、问候、写作和云端查询由 Amoo 自行回答，不创建 Maeve 任务。）"
        } else {
            "当前禁止调用 Maeve 文件桥；请用 Amoo 自己的能力回答。不要沿用历史轮次的执行许可。）"
        }
}
