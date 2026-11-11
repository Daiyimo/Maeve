package com.daiyimo.maeve.ui

import android.content.Intent
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.PluginRegistry

/**
 * 入口事件:通知点击、任务深链接落在 [MainActivity] 上时,转成 `maeve/ui/entry`
 * 事件推给 Dart:`{kind: "open_conversation", threadId}`。Dart 侧见 MaeveBridge.entries。
 *
 * 结构对齐 dsh-go 的 ShortcutsPlugin:ActivityAware + NewIntentListener,冷启动的 launch
 * intent 和之后的 onNewIntent 都收;Dart 还没 listen(EventChannel 的 onListen 未到)时
 * 事件进 [pending],listen 到了再按序冲刷——等价于那边的 ready 握手。
 *
 * 两个防重放:launch intent 取过一次就打上 [EXTRA_TAKEN](配置变更会原样重发);
 * FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY 的直接跳过(那是从最近任务恢复,不是新入口)。
 *
 * 为什么不加 dsh-go 那样的进程 token:它的入口能带"预填文本"进 composer,需要证明 entry
 * 真的走过它的跳板 Activity。Maeve 的入口只带一个 threadId,壳这边的动作是切到某个对话
 * 占位页——没有特权操作、不读写数据、不泄露信息,外部直启的最坏结果就是看到这个占位页。
 * threadId 只当不透明字符串用,不进路径、不进命令、不进 SQL。
 */
class MaeveEntryPlugin : FlutterPlugin, ActivityAware, PluginRegistry.NewIntentListener {
    private lateinit var entry: EventChannel
    private var binding: ActivityPluginBinding? = null
    private var sink: EventChannel.EventSink? = null
    private val pending = ArrayList<Map<String, Any?>>()

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        entry = EventChannel(binding.binaryMessenger, CHANNEL).also { channel ->
            channel.setStreamHandler(
                object : EventChannel.StreamHandler {
                    override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
                        sink = events
                        if (pending.isNotEmpty()) {
                            val held = ArrayList(pending)
                            pending.clear()
                            for (event in held) events.success(event)
                        }
                    }

                    override fun onCancel(arguments: Any?) {
                        sink = null
                    }
                },
            )
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        entry.setStreamHandler(null)
        sink = null
        pending.clear()
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        this.binding = binding
        binding.addOnNewIntentListener(this)
        val launch = binding.activity.intent ?: return
        if (launch.getBooleanExtra(EXTRA_TAKEN, false)) return
        if (launch.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        if (take(launch)) launch.putExtra(EXTRA_TAKEN, true)
    }

    override fun onDetachedFromActivityForConfigChanges() = onDetachedFromActivity()

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) =
        onAttachedToActivity(binding)

    override fun onDetachedFromActivity() {
        binding?.removeOnNewIntentListener(this)
        binding = null
    }

    override fun onNewIntent(intent: Intent): Boolean = take(intent)

    /** 收下一个入口;intent 不是入口时返回 false。 */
    private fun take(intent: Intent): Boolean {
        val threadId = intent.getStringExtra(MainActivity.EXTRA_OPEN_CONVERSATION)
            ?.takeIf { it.isNotBlank() } ?: return false
        val event = mapOf("kind" to "open_conversation", "threadId" to threadId)
        val events = sink
        if (events != null) events.success(event) else pending += event
        return true
    }

    companion object {
        const val CHANNEL = "maeve/ui/entry"
        const val KIND_OPEN_CONVERSATION = "open_conversation"

        /** 打在 launch intent 上,防配置变更重放。 */
        private const val EXTRA_TAKEN = "com.daiyimo.maeve.ui.entry.TAKEN"
    }
}
