package com.daiyimo.maeve.ui

import android.content.Intent
import android.content.res.AssetManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.daiyimo.maeve.BuildConfig
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

/**
 * 宿主状态与应用设置入口:MethodChannel `maeve/ui` + EventChannel `maeve/ui/events`。
 *
 * 随 Flutter 引擎常驻(见 [MainActivity.provideFlutterEngine]),不随 Activity 重建:
 * 壳从最近任务划掉再回来、配置变更,状态流的订阅都不用重连。Activity detach 只解绑,
 * 不注销通道;下次 attach 自动接上。
 *
 * open_settings 需要一个前台 Activity 才能跳转;没有就报错,不会静默失败。
 */
class MaeveUiPlugin : FlutterPlugin, ActivityAware, MethodChannel.MethodCallHandler, EventChannel.StreamHandler {
    private lateinit var methods: MethodChannel
    private lateinit var events: EventChannel
    private var activity: ActivityPluginBinding? = null
    private var sink: EventChannel.EventSink? = null
    private lateinit var assets: AssetManager
    private lateinit var context: android.content.Context
    private val scope = MainScope()

    /** 检查更新与下载安装都跟进程走(见 [com.daiyimo.maeve.MaeveApp.updates]),不随 Activity 重建。 */
    private val updates get() = (context.applicationContext as com.daiyimo.maeve.MaeveApp).updates
    private val updater by lazy { com.daiyimo.maeve.about.ApkUpdater(context) }

    /** 订阅期间盯着更新状态与下载进度的两个循环;取消订阅就停。 */
    private var updateJob: Job? = null
    private var downloadJob: Job? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        assets = binding.applicationContext.assets
        methods = MethodChannel(binding.binaryMessenger, CHANNEL).also { it.setMethodCallHandler(this) }
        events = EventChannel(binding.binaryMessenger, EVENTS).also { it.setStreamHandler(this) }
        current = this
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methods.setMethodCallHandler(null)
        events.setStreamHandler(null)
        sink = null
        updateJob?.cancel()
        downloadJob?.cancel()
        updateJob = null
        downloadJob = null
        scope.cancel()
        if (current === this) current = null
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "open_url" -> {
                val host = activity?.activity
                val url = browserLink(call.argument<String>("url"))
                if (host == null || url == null) result.success(false)
                else result.success(runCatching {
                    host.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
                    true
                }.getOrDefault(false))
            }
            "open_settings" -> {
                val host = activity?.activity
                if (host == null) {
                    result.error("no_activity", "open_settings needs a foreground activity", null)
                } else {
                    host.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:${host.packageName}")),
                    )
                    result.success(null)
                }
            }
            "status" -> result.success(hostStatus())
            // Flutter 的触感统一交给原生引擎:同一份开关、同一套五级手感,
            // 不再走 Flutter 引擎自带的 HapticFeedback(那条路在部分 ROM 上不振)。
            "haptic" -> {
                val level = com.daiyimo.maeve.ui.haptic.hapticLevelOf(call.argument<String>("level"))
                if (level == null) result.error("bad_args", "Unknown haptic level", null)
                else {
                    com.daiyimo.maeve.ui.haptic.MaeveHaptics.get(context).emit(level)
                    result.success(null)
                }
            }
            // 报告导出走系统文件选择器:用户自己挑位置,应用不需要任何存储权限。
            // FlutterActivity 不是 ComponentActivity,结果只能经 ActivityPluginBinding 的
            // ActivityResultListener 回来(见 attach/detach),不能用 activityResultRegistry。
            "exportRunReport" -> exportReport(call, result)
            // 「获取更新」:检查、下载、安装都交给 about 包里已有的那套实现,
            // 这里只转发和推状态,不重写节流/选包/校验规则(见 UpdateBridge)。
            "updateState" -> scope.launch {
                try {
                    result.success(updateSnapshot())
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    result.error("update_unavailable", "无法读取更新状态", null)
                }
            }
            // 手动检查等这一次查完再回话:页面据此给出「已是最新」或「连不上」,
            // 不用靠事件流猜什么时候查完了(流没接上时也不会一直转圈)。
            "checkUpdate" -> scope.launch {
                try {
                    withContext(Dispatchers.IO) { updates.check(manual = true) }
                    result.success(updateSnapshot())
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    result.error("update_unavailable", AboutCopy.OFFLINE_TOAST, null)
                }
            }
            "downloadUpdate" -> scope.launch {
                try {
                    val release = withContext(Dispatchers.IO) { updates.load(); updates.state.value.update }
                    if (release?.apkUrl == null) {
                        result.error("no_apk", "这个发布没有可下载的安装包", null)
                        return@launch
                    }
                    try {
                        withContext(Dispatchers.IO) { updater.download(release) }
                    } catch (e: com.daiyimo.maeve.about.ApkRejected) {
                        result.error("apk_rejected", UpdateBridge.rejection(e), e.reason.name)
                        return@launch
                    }
                    result.success(updateSnapshot())
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    result.error("update_failed", AboutCopy.UPDATE_ERROR, null)
                }
            }
            // 「GitHub 加速」:检查更新与下载安装问 GitHub 时域名怎么解析(见 GitHubAccelStore)。
            "githubAccel" -> result.success(accelMap())
            "setGitHubAccel" -> scope.launch {
                try {
                    val mode = call.argument<String>("mode")
                    val parsed = com.daiyimo.maeve.core.net.GitHubDnsMode.entries
                        .firstOrNull { it.name.equals(mode, ignoreCase = true) }
                    if (parsed == null) {
                        result.error("bad_args", "Unknown GitHub acceleration mode", null)
                        return@launch
                    }
                    val ip = call.argument<String>("ip").orEmpty().trim()
                    // 自定义 IP 只收字面 IP:填域名等于又绕回 DNS,不如不填
                    if (parsed == com.daiyimo.maeve.core.net.GitHubDnsMode.CustomIp &&
                        com.daiyimo.maeve.core.net.GitHubDns.literalIp(ip) == null
                    ) {
                        result.error("bad_ip", "自定义 IP 需要一个 IP 地址", null)
                        return@launch
                    }
                    com.daiyimo.maeve.settings.GitHubAccelStore(context).set(parsed, ip)
                    result.success(updateSnapshot())
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    result.error("settings_failed", "无法保存 GitHub 加速设置", null)
                }
            }
            "cancelUpdate" -> scope.launch {
                try {
                    withContext(Dispatchers.IO) { updater.cancel() }
                    result.success(updateSnapshot())
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    result.error("update_failed", AboutCopy.UPDATE_ERROR, null)
                }
            }
            // 安装要前台 Activity;系统没给「安装未知应用」授权时先带去授权页,
            // 回来再点一次安装(和 Mika 一致:不在用户没看见的地方偷偷装)。
            "installUpdate" -> scope.launch {
                val host = activity?.activity
                if (host == null) {
                    result.error("no_activity", "installUpdate needs a foreground activity", null)
                    return@launch
                }
                try {
                    val release = withContext(Dispatchers.IO) { updates.state.value.update }
                    if (release == null) {
                        result.error("no_update", "没有可安装的新版本", null)
                        return@launch
                    }
                    if (!host.packageManager.canRequestPackageInstalls()) {
                        host.startActivity(
                            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                                .setData(Uri.parse("package:${host.packageName}")),
                        )
                        result.success(mapOf("state" to "permission"))
                        return@launch
                    }
                    val intent = withContext(Dispatchers.IO) { updater.installIntent(release) }
                    host.startActivity(intent)
                    result.success(mapOf("state" to "installing"))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: com.daiyimo.maeve.about.ApkRejected) {
                    result.error("apk_rejected", UpdateBridge.rejection(e), e.reason.name)
                } catch (_: Exception) {
                    result.error("update_failed", AboutCopy.UPDATE_ERROR, null)
                }
            }
            "legal_notices" -> scope.launch {
                try {
                    val data = withContext(Dispatchers.IO) {
                        fun read(name: String) = assets.open(name).bufferedReader().use { it.readText() }
                        val entries = Licenses.ENTRIES + Licenses.runtimePackages(read("runtime/packages.json"))
                        mapOf(
                            "entries" to entries.map { mapOf("name" to it.name, "license" to it.license, "note" to it.note) },
                            "sourceOffer" to Licenses.GPL_SOURCE_OFFER,
                            "documents" to listOf("NOTICE.txt", "Apache-2.0.txt", "GPL-2.0.txt", "GPL-3.0.txt", "LGPL-3.0.txt", "Protocol-Buffers.txt", "Flutter-Markdown-Plus.txt", "Dart-Markdown.txt", "Dart-Args.txt", "SOURCE_OFFER.md")
                                .map { mapOf("name" to it, "text" to read("licenses/$it")) },
                        )
                    }
                    result.success(data)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    result.error("legal_notices", "Unable to read bundled notices", e.message)
                }
            }
            else -> result.notImplemented()
        }
    }

    /** 等系统文件选择器回来的一次导出;同一时刻只允许一个。 */
    private class PendingExport(val runId: String, val format: TaskReportExport.Format, val result: MethodChannel.Result)

    private var pendingExport: PendingExport? = null

    private fun exportReport(call: MethodCall, result: MethodChannel.Result) {
        val host = activity?.activity
        val runId = call.argument<String>("runId")
        val format = TaskReportExport.Format.entries.firstOrNull { it.name.equals(call.argument<String>("format"), ignoreCase = true) }
        when {
            host == null -> result.error("no_activity", "exportRunReport needs a foreground activity", null)
            runId.isNullOrBlank() || format == null -> result.error("bad_args", "A run id and a known format are required", null)
            pendingExport != null -> result.error("busy", "Another export is still open", null)
            else -> {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType(format.mime)
                    .putExtra(Intent.EXTRA_TITLE, "Maeve-$runId.${format.extension}")
                val started = runCatching { host.startActivityForResult(intent, EXPORT_REQUEST) }.isSuccess
                if (!started) result.error("no_picker", "没有可用的文件保存入口", null)
                else pendingExport = PendingExport(runId, format, result)
            }
        }
    }

    private val exportResults = object : PluginRegistry.ActivityResultListener {
        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
            if (requestCode != EXPORT_REQUEST) return false
            val pending = pendingExport ?: return true
            pendingExport = null
            val uri = data?.data
            if (resultCode != android.app.Activity.RESULT_OK || uri == null) {
                pending.result.success(mapOf("state" to "cancelled"))
                return true
            }
            scope.launch {
                val state = try {
                    withContext(Dispatchers.IO) {
                        val report = TaskReportViewModel(com.daiyimo.maeve.record.RunStore(context), pending.runId).load()
                            ?: error("record_unavailable")
                        // "wt" 截断重写:选到已有文件时不残留旧内容。
                        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                            TaskReportExport.write(report, pending.format, out)
                        } ?: error("no_stream")
                    }
                    "saved"
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    "failed"
                }
                pending.result.success(mapOf("state" to state))
            }
            return true
        }
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
        sink = events
        events.success(hostStatus())
        watchUpdates()
    }

    override fun onCancel(arguments: Any?) {
        sink = null
        updateJob?.cancel()
        downloadJob?.cancel()
        updateJob = null
        downloadJob = null
    }

    /**
     * 更新状态变了就推一份;下载进度另起一个循环按秒问一次。
     *
     * **为什么下载要轮询:** 下载由系统下载服务/进程内下载器负责,它们不回调到这里,
     * 只有「现在下了多少」这个可查询的状态。不推的话页面进度条不会动。
     * 没人在听(sink 为空)或没有在下的包时不发,免得空转。
     */
    private fun watchUpdates() {
        if (updateJob != null) return
        updateJob = scope.launch {
            updates.state.collect { pushUpdate() }
        }
        downloadJob = scope.launch {
            while (isActive) {
                delay(1000)
                val release = updates.state.value.update ?: continue
                val phase = runCatching {
                    withContext(Dispatchers.IO) { updater.state(release) }
                }.getOrNull()?.phase ?: continue
                if (phase == com.daiyimo.maeve.about.DownloadPhase.Downloading ||
                    phase == com.daiyimo.maeve.about.DownloadPhase.Paused
                ) pushUpdate()
            }
        }
    }

    /** 更新事件带 kind=update,和宿主状态(kind=status)在同一条流里分开。 */
    private fun pushUpdate() {
        val events = sink ?: return
        scope.launch {
            runCatching { events.success(mapOf("kind" to "update", "update" to updateSnapshot())) }
        }
    }

    /** 页面一次要的全部更新状态。读盘、查下载状态都在 IO 上做。 */
    private suspend fun updateSnapshot(): Map<String, Any?> {
        val state = withContext(Dispatchers.IO) {
            updates.load()
            updates.state.value
        }
        val download = withContext(Dispatchers.IO) {
            state.update?.let { runCatching { updater.state(it) }.getOrNull() }
                ?: com.daiyimo.maeve.about.ApkDownloadState()
        }
        return UpdateBridge.snapshot(
            state, download, BuildConfig.VERSION_NAME,
            activity?.activity?.packageManager?.canRequestPackageInstalls() ?: false,
            accelMap(),
        )
    }

    /** 「GitHub 加速」此刻的设置(见 [com.daiyimo.maeve.settings.GitHubAccelStore])。 */
    private fun accelMap(): Map<String, Any?> {
        val store = com.daiyimo.maeve.settings.GitHubAccelStore(context)
        // 模式和 Flutter 侧 GitHubAccelMode 的枚举名逐一对应(aliDoh、customIp…),大小写也算
        return mapOf("mode" to store.mode().name.replaceFirstChar { it.lowercaseChar() }, "ip" to store.customIp())
    }

    /** 回前台刷一次状态(版本、构建、系统版本这类打开期间不会变,但设置里可能改过显示)。 */
    fun pushStatus() {
        sink?.success(hostStatus())
        // 从系统安装器/授权页回来时,下载与授权状态可能已经变了。
        pushUpdate()
    }

    private fun hostStatus(): Map<String, Any> = mapOf(
        "kind" to "status",
        "bridgeVersion" to 2,
        "hostBuild" to BuildConfig.GIT_HASH,
        "capabilities" to mapOf("submissionReceipts" to true, "draftRecovery" to true, "actionEffectReceipts" to true,
            "deleteImpact" to true, "awaitDeletion" to true, "titleSearchOnly" to true),
        "links" to mapOf("updates" to com.daiyimo.maeve.about.MaeveLinks.RELEASES, "feedback" to com.daiyimo.maeve.about.MaeveLinks.NEW_ISSUE),
        "versionCode" to BuildConfig.VERSION_CODE,
        "channel" to if (BuildConfig.DEBUG) "debug" else BuildConfig.BUILD_TYPE,
        "version" to BuildConfig.VERSION_NAME,
        "build" to BuildConfig.GIT_HASH,
        "android" to Build.VERSION.RELEASE,
        "sdk" to Build.VERSION.SDK_INT,
    )

    // ActivityAware:壳换实例(划掉重开、配置变更)时接着用同一个引擎里的这些通道

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding
        binding.addActivityResultListener(exportResults)
    }

    override fun onDetachedFromActivityForConfigChanges() = onDetachedFromActivity()

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding
        binding.addActivityResultListener(exportResults)
    }

    override fun onDetachedFromActivity() {
        activity?.removeActivityResultListener(exportResults)
        activity = null
        // 界面没了就没人能收结果;把还挂着的调用结掉,不让 Dart 那边一直等。
        pendingExport?.result?.success(mapOf("state" to "cancelled"))
        pendingExport = null
    }

    companion object {
        const val CHANNEL = "maeve/ui"
        const val EVENTS = "maeve/ui/events"

        /** 报告导出的 startActivityForResult 请求码;本插件只用这一个。 */
        private const val EXPORT_REQUEST = 0x4D45

        /**
         * 进程内单引擎,插件只有一个实例;[MainActivity.onResume] 靠它刷新状态流。
         * 不做事的话可以没有——留着是因为壳回前台时顶栏那行状态应该是最新的。
         */
        @Volatile
        var current: MaeveUiPlugin? = null
            private set
    }
}

/** Only user-tapped web links are handed to a browser. */
internal fun browserLink(raw: String?): String? = try {
    val uri = java.net.URI(raw.orEmpty())
    uri.takeIf { it.scheme?.lowercase(java.util.Locale.ROOT) in setOf("http", "https") && !it.host.isNullOrBlank() && it.userInfo == null }?.toASCIIString()
} catch (_: Exception) { null }
