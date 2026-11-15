package com.daiyimo.maeve.cap.phone.shell

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.AttributionSource
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.util.Log
import com.daiyimo.maeve.core.shell.HelperHandoff
import com.daiyimo.maeve.core.shell.HelperRestorePolicy
import com.daiyimo.maeve.core.shell.PermissionCommands
import com.daiyimo.maeve.core.shell.RestoreCommands
import com.daiyimo.maeve.core.shell.RestoreWish
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.system.exitProcess

/**
 * 内置助手进程的入口。Maeve 经本机无线调试(adbd 的 exec: 服务)以 shell 身份启动它:
 * `CLASSPATH=<base.apk> app_process /system/bin --nice-name=<包名>:helper com.daiyimo.maeve.shell.HelperMain <包名> <Maeve 的 uid> <签名指纹>`,
 * 一次性口令从标准输入读一行(见 [HelperHandoff.command])。电脑经 USB 调试跑「用电脑启动」脚本时起的也是它,命令和参数相同
 * (见 [HelperHandoff.usbScript]);已 root 的手机上经 `su 2000 -c` 以 shell 身份起的也是它(见 [HelperHandoff.rootCommand])。
 *
 * 特权逻辑全在 [ShellService];这里只负责把它的 Binder 交给 Maeve,并且在 Maeve 进程结束后不退出:
 * 撤掉 Maeve 留下的改动,等 Maeve 再起来时重新交接。Maeve 死后一会儿还没回来、而它交来的心愿([RestoreWish])要拉起来时,
 * 把它的无障碍服务写回系统列表,再经交接用的 provider 把它的进程拉起来(见 [HelperRestorePolicy])。
 */
object HelperProcess {
    private const val TAG = "MaeveHelper"

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size < 3) {
            Log.e(TAG, "usage: HelperMain <package> <uid> <cert-sha256> (nonce on stdin)")
            exitProcess(2)
        }
        val packageName = args[0]
        val appUid = args[1].toIntOrNull() ?: exitProcess(2)
        val certPin = args[2]
        // 读完就关:标准输入只用来递口令
        val launchNonce = runCatching { System.`in`.bufferedReader().readLine()?.trim() }.getOrNull()
        runCatching { System.`in`.close() }
        if (launchNonce.isNullOrEmpty()) {
            Log.e(TAG, "no nonce on stdin")
            exitProcess(2)
        }
        // ShellContext 自建 ActivityThread 时要有主线程 Looper
        Looper.prepareMainLooper()
        val service = ShellService(allowedUid = appUid)
        // 上次在静音中被杀、没来得及恢复的 App,先把声音还回去
        service.recoverMuted()
        val helper = Helper(service, packageName, appUid, certPin)
        helper.start(launchNonce)
        Looper.loop()
    }

    private class Helper(
        private val service: ShellService,
        private val packageName: String,
        private val appUid: Int,
        private val certPin: String,
    ) {
        private val context: Context = ShellContext.create()
        private val authority = HelperHandoff.authority(packageName)
        private val userId = appUid / PER_USER_RANGE
        /** 交接、Maeve 死亡处理都排在这一条线程上,彼此不交错,也不堵主线程。 */
        private val worker = Handler(HandlerThread("helper-handoff").apply { start() }.looper)
        /** 下次交接用的口令(Maeve 上次收下时换发的)。 */
        private var nonce: String? = null
        /** Maeve 进程交给我们的令牌;为 null 表示 Maeve 现在不在。 */
        private var appToken: IBinder? = null
        private var retrying = false
        /** Maeve 最后交来的心愿;助手刚起来时什么都不做。 */
        private var wish = RestoreWish.NONE
        private val restorePolicy = HelperRestorePolicy()
        /** 每次 Maeve 死去加一:等的这一会儿里 Maeve 回来又死了,前一次的等待作废。 */
        private var deaths = 0

        init {
            service.onRestoreWish = { flags ->
                worker.post {
                    val next = RestoreWish.fromFlags(flags)
                    if (next != wish) Log.i(TAG, "restore wish: $next")
                    wish = next
                }
            }
        }

        fun start(launchNonce: String) = worker.post {
            when (deliver(launchNonce)) {
                Outcome.Accepted -> {
                    // Maeve 收下了这一个:之前起的(或残留的)助手实例都该走了
                    killOtherInstances()
                    watchUid()
                }
                Outcome.Rejected -> exitProcess(0)
                Outcome.Failed -> exitProcess(1)
            }
        }

        private enum class Outcome { Accepted, Rejected, Failed }

        /** 经 ActivityManager 拿 Maeve 的 provider(同 `content call` 的做法),把 Binder 和自报的 uid/pid 交过去。 */
        @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
        private fun deliver(n: String?): Outcome {
            if (n == null) return Outcome.Rejected
            // 包名和 uid 可能被重装的别的 App 占用:签名对不上就不交 Binder,撤掉改动退出
            if (!signedAsPinned()) {
                Log.e(TAG, "$packageName is missing or not signed with the pinned certificate; exiting")
                runCatching { service.releaseAll() }
                exitProcess(0)
            }
            val token = Binder()
            var handedOff = false
            val holder = try {
                getProvider.invoke(amService, authority, userId, token, TAG)
            } catch (t: Throwable) {
                Log.w(TAG, "getContentProviderExternal failed", t)
                return Outcome.Failed
            } ?: return Outcome.Failed
            try {
                // 同名 authority 只可能属于一个包;还是核对一下,不把 Binder 交给 Maeve 以外的 App
                val info = holder.javaClass.getField("info").get(holder) as? ProviderInfo
                if (info == null || info.packageName != packageName || info.applicationInfo?.uid != appUid) {
                    Log.e(TAG, "provider $authority does not belong to $packageName/$appUid")
                    return Outcome.Rejected
                }
                val extras = Bundle().apply {
                    putString(HelperHandoff.KEY_NONCE, n)
                    putBinder(HelperHandoff.KEY_BINDER, service)
                    putInt(HelperHandoff.KEY_UID, Process.myUid())
                    putInt(HelperHandoff.KEY_PID, Process.myPid())
                    putString(HelperHandoff.KEY_APK, System.getProperty("java.class.path"))
                    restorePolicy.report(System.currentTimeMillis()).takeUnless { it.empty }?.let { r ->
                        putLong(HelperHandoff.KEY_RESTORED_AT, r.lastRestoredAtMs)
                        putInt(HelperHandoff.KEY_RESTORES, r.recent)
                        putLong(HelperHandoff.KEY_GAVE_UP_AT, r.gaveUpAtMs)
                    }
                }
                val result = invokeProvider(holder, HelperHandoff.METHOD_ATTACH, extras)
                if (result?.getBoolean(HelperHandoff.KEY_ACCEPTED) != true) {
                    Log.w(TAG, "handoff rejected: ${result?.getString(HelperHandoff.KEY_REASON)}")
                    return Outcome.Rejected
                }
                val appBinder = result.getBinder(HelperHandoff.KEY_TOKEN) ?: return Outcome.Rejected
                nonce = result.getString(HelperHandoff.KEY_NEXT)
                appToken = appBinder
                try {
                    appBinder.linkToDeath({ worker.post { onAppDied(appBinder) } }, 0)
                } catch (t: Throwable) {
                    worker.post { onAppDied(appBinder) }
                }
                Log.i(TAG, "handed off to $packageName")
                handedOff = true
                startHeartbeat()
                return Outcome.Accepted
            } catch (t: Throwable) {
                Log.w(TAG, "handoff failed", t)
                return Outcome.Failed
            } finally {
                // 交接成功就不释放:助手活着就替 Maeve 占着「provider 有客户端在用」,见 [holdProvider]。
                if (handedOff) holdProvider(token)
                else releaseProvider(token)
            }
        }

        /** ActivityManager 的 provider 三件套;助手进程里只反射一次。 */
        private val amService: Any by lazy {
            @SuppressLint("PrivateApi")
            Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null)!!
        }
        private val getProvider: Method by lazy {
            amService.javaClass.getMethod(
                "getContentProviderExternal", String::class.java, Int::class.javaPrimitiveType,
                IBinder::class.java, String::class.java,
            )
        }
        private val removeProvider: Method by lazy {
            amService.javaClass.getMethod(
                "removeContentProviderExternalAsUser", String::class.java, IBinder::class.java, Int::class.javaPrimitiveType,
            )
        }

        /** 在已取得的 provider 上发起一次调用(attach 和保活戳都走它)。 */
        private fun invokeProvider(holder: Any, method: String, extras: Bundle?): Bundle? {
            val provider = holder.javaClass.getField("provider").get(holder)!!
            val call = provider.javaClass.methods.first {
                it.name == "call" && it.parameterTypes.size == 5 && it.parameterTypes[0] == AttributionSource::class.java
            }
            val caller = AttributionSource.Builder(Process.myUid()).setPackageName(ShellContext.SHELL_PACKAGE).build()
            return call.invoke(provider, caller, authority, method, null, extras) as Bundle?
        }

        /** 保活戳:经 provider 给 Maeve 发一次不需要口令的调用,让系统看到「provider 有客户端在用」。 */
        private fun pingProvider(): Boolean {
            val token = Binder()
            val holder = runCatching { getProvider.invoke(amService, authority, userId, token, TAG) }.getOrNull() ?: return false
            return try {
                invokeProvider(holder, HelperHandoff.METHOD_PING, null) != null
            } catch (t: Throwable) {
                Log.w(TAG, "keep-alive ping failed", t)
                false
            } finally {
                releaseProvider(token)
            }
        }

        /**
         * 定期戳 Maeve(见 [pingProvider])。
         *
         * StepOS 的冷冻器(日志 tag StepOSFreeze)会在 Maeve 退到后台后把它冻住:电池白名单、前台服务都拦不住,
         * 用户回来时输入没人消费,等 5 秒就弹「Maeve无响应」。它自己的 BLOCK_FREEZE 日志表明,provider 有调用流过时
         * (flag FLAG_USING_PROVIDER)冷冻被取消(cancel_pending)——光持有客户端不够,那个 flag 只认活跃调用,几十秒就衰退。
         * 助手以 shell 身份常驻,每 [PING_INTERVAL_MS] 戳一次,Maeve 就不被冻:这正是助手存在的配置
         * (一次性无线调试授权)下应有的行为——它得随时能动手机。代价是 Maeve 不再沉睡、轮询循环全程在跑;
         * 对「随叫随到的手机 agent」这是应有的取舍。
         */
        private val heartbeat = object : Runnable {
            override fun run() {
                if (appToken == null) return
                pingProvider()
                worker.postDelayed(this, PING_INTERVAL_MS)
            }
        }

        private fun startHeartbeat() {
            worker.removeCallbacks(heartbeat)
            worker.post(heartbeat)
        }

        private fun stopHeartbeat() = worker.removeCallbacks(heartbeat)

        /** 交接成功后从 Maeve 进程取得的 provider 释放令牌;持有期间系统把 Maeve 当作「provider 有客户端在用」。 */
        private var heldProviderToken: IBinder? = null

        private fun holdProvider(token: IBinder) {
            releaseHeldProvider()
            heldProviderToken = token
        }

        private fun releaseHeldProvider() {
            heldProviderToken?.let { releaseProvider(it) }
            heldProviderToken = null
        }

        private fun releaseProvider(token: IBinder) {
            runCatching { removeProvider.invoke(amService, authority, token, userId) }
        }

        /** Maeve 进程没了:它建的屏、静音、剪贴板改动跟着撤掉(和服务结束时一样),助手本身留着。 */
        private fun onAppDied(token: IBinder) {
            if (appToken !== token) return
            appToken = null
            // Maeve 没了,它进程里的 provider 跟着没,客户端没有持有价值;下次交接会重新拿。
            stopHeartbeat()
            releaseHeldProvider()
            runCatching { service.releaseAll() }.onFailure { Log.w(TAG, "cleanup failed", it) }
            if (!installed()) {
                Log.i(TAG, "$packageName is gone; exiting")
                exitProcess(0)
            }
            Log.i(TAG, "$packageName died; waiting for it to come back")
            val death = ++deaths
            worker.postDelayed({ if (death == deaths) maybeRestore() }, HelperRestorePolicy.GRACE_MS)
        }

        /**
         * Maeve 死了一会儿还没回来:按它最后交来的心愿决定拉不拉。拉的话先写回无障碍服务(被强行停止时系统会把它拿掉),
         * 再重新交接:拿 Maeve 的 provider 时系统会把它的进程起来(provider 不受后台启动限制,也不显示界面),
         * Application 照常创建,胶囊和别的服务跟着起来。shell 能拿任意 App 的 provider(`content` 命令用的同一条路)。
         */
        private fun maybeRestore() {
            val now = System.currentTimeMillis()
            when (val d = restorePolicy.decide(wish, appBack = appToken != null || retrying, now)) {
                HelperRestorePolicy.Decision.Restore -> Unit
                HelperRestorePolicy.Decision.AppBack -> return
                else -> {
                    Log.i(TAG, "not restoring $packageName: $d")
                    return
                }
            }
            Log.i(TAG, "restoring $packageName (${restorePolicy.report(now).recent} in the last window)")
            if (wish.accessibility) restoreAccessibility()
            retrying = true
            redeliver(0)
        }

        /** 把 Maeve 的无障碍服务追加回系统列表、打开总开关;别的服务不动。失败只记日志,照样去拉 Maeve(它起来后自己还会修)。 */
        private fun restoreAccessibility() {
            val me = PermissionCommands.accessibilityService(packageName)
            val current = service.runCommand(PermissionCommands.READ_ACCESSIBILITY).getString("output")
            for (cmd in RestoreCommands.accessibility(current, me)) {
                val r = service.runCommand(cmd)
                if (r.getInt("exitCode", -1) != 0) {
                    Log.w(TAG, "restore accessibility: '$cmd' failed: ${r.getString("output")?.trim()}")
                    return
                }
            }
            Log.i(TAG, "accessibility service of $packageName written back")
        }

        @SuppressLint("DiscouragedPrivateApi")
        private fun installed(): Boolean = runCatching {
            context.packageManager.javaClass.getMethod("getPackageUidAsUser", String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(context.packageManager, packageName, 0, userId)
            true
        }.getOrElse { t ->
            // 包不在了才退出;反射不可用时看不清,当作还在
            val cause = (t as? java.lang.reflect.InvocationTargetException)?.targetException ?: t
            cause !is android.content.pm.PackageManager.NameNotFoundException
        }

        /**
         * 盯着 Maeve 的 uid:进程重新起来时(从不存在变成任一状态)系统会报一次,这时把 Binder 再交过去。
         * 用的是 ActivityManager 的 uid 重要性监听(要 PACKAGE_USAGE_STATS,shell 有),回调在 binder 线程。
         * 注册不上时由 Maeve 那边兜底:等不到交接就经 adbd 另起一个助手,新的收下后会结束这个。
         */
        @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
        private fun watchUid() {
            runCatching {
                // 用 shell 包名的上下文建 ActivityManager:注册时系统按调用方包名核对权限
                val manager = ActivityManager::class.java.getDeclaredConstructor(Context::class.java, Handler::class.java)
                    .apply { isAccessible = true }.newInstance(context, worker)
                val listenerClass = Class.forName("android.app.ActivityManager\$OnUidImportanceListener")
                val listener = Proxy.newProxyInstance(HelperProcess::class.java.classLoader, arrayOf(listenerClass)) { proxy, method, a ->
                    when (method.name) {
                        "onUidImportance" -> { onUidImportance(a[0] as Int, a[1] as Int); null }
                        "hashCode" -> System.identityHashCode(proxy)
                        "equals" -> proxy === a?.getOrNull(0)
                        "toString" -> "MaeveHelperUidListener"
                        else -> null
                    }
                }
                ActivityManager::class.java.getMethod("addOnUidImportanceListener", listenerClass, Int::class.javaPrimitiveType)
                    .invoke(manager, listener, ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED)
            }.onFailure { Log.w(TAG, "cannot watch $packageName; relying on the app to restart the helper", it) }
        }

        private fun onUidImportance(uid: Int, importance: Int) {
            if (uid != appUid || importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE) return
            worker.post { if (appToken == null && !retrying) redeliver(0) }
        }

        /** 重新交接;provider 暂时不可用时隔一会儿再试几次,还不行就等下一次 Maeve 起来。 */
        private fun redeliver(attempt: Int) {
            if (appToken != null) return
            when (deliver(nonce)) {
                Outcome.Accepted -> retrying = false
                // Maeve 不认这个助手了(重装过、解除了配对、或者已经换了新的):退出
                Outcome.Rejected -> exitProcess(0)
                Outcome.Failed -> {
                    retrying = attempt + 1 < RETRY_DELAYS_MS.size
                    if (retrying) worker.postDelayed({ redeliver(attempt + 1) }, RETRY_DELAYS_MS[attempt + 1])
                }
            }
        }

        /** 现在装着的 [packageName] 仍是这个 uid、签名证书指纹与启动时 Maeve 给的一致。看不清一律当不一致。 */
        @SuppressLint("DiscouragedPrivateApi")
        private fun signedAsPinned(): Boolean = runCatching {
            val pm = context.packageManager
            val flags = PackageManager.GET_SIGNING_CERTIFICATES
            val info = if (userId == 0) pm.getPackageInfo(packageName, flags)
            else pm.javaClass.getMethod("getPackageInfoAsUser", String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(pm, packageName, flags, userId) as PackageInfo
            val signers = info.signingInfo?.apkContentsSigners?.map { it.toByteArray() }
            info.applicationInfo?.uid == appUid && HelperHandoff.certPin(signers) == certPin
        }.getOrElse { t ->
            Log.w(TAG, "cannot read signing info of $packageName", t)
            false
        }

        /** 只认进程名完全相同的同 uid 进程:别的 app_process 不能误伤。 */
        private fun killOtherInstances() {
            val me = Process.myPid()
            val name = HelperHandoff.processName(packageName)
            File("/proc").listFiles()?.forEach { dir ->
                val pid = dir.name.toIntOrNull() ?: return@forEach
                if (pid == me) return@forEach
                val cmdline = runCatching { File(dir, "cmdline").readBytes() }.getOrNull() ?: return@forEach
                val argv0 = String(cmdline.takeWhile { it != 0.toByte() }.toByteArray())
                if (argv0 == name) {
                    Log.i(TAG, "stopping older helper $pid")
                    Process.killProcess(pid)
                }
            }
        }

        companion object {
            private const val PER_USER_RANGE = 100_000
            private val RETRY_DELAYS_MS = longArrayOf(0, 1_000, 3_000, 10_000)

            /** 保活戳的间隔;冷冻器的评估周期约一分钟,2 秒足够在任何评估点上都不缺 flag。 */
            private const val PING_INTERVAL_MS = 2_000L
        }
    }
}
