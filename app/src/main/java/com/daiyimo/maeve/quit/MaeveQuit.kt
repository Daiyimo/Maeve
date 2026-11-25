package com.daiyimo.maeve.quit

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Process
import com.daiyimo.maeve.MaeveApp
import com.daiyimo.maeve.cap.sandbox.RuntimePreparer
import com.daiyimo.maeve.core.cap.web.KeptPageClose
import com.daiyimo.maeve.mcp.McpService
import com.daiyimo.maeve.sandbox.SandboxService
import com.daiyimo.maeve.settings.ChatGptSignInService
import com.daiyimo.maeve.settings.FeatureFlags
import com.daiyimo.maeve.task.TaskService
import com.daiyimo.maeve.task.TriggerRuntime
import com.daiyimo.maeve.wechat.WechatService

/**
 * 完全退出 Maeve:用户滑掉界面、或进程被系统回收时,无障碍服务还开着,悬浮胶囊和后台任务会一直在。
 * 这里由用户明确点「退出」来做一次真正的退出:
 * 记下退出标记([FeatureFlags.quitRequested]),收起浮层,停掉任务、电脑连接和几个前台服务,收掉通知,最后结束进程。
 * 无障碍服务本身不关(那是用户自己的系统设置,下次打开 Maeve 还要用);系统把它重新绑起来时,进程按退出标记安静地待着,
 * 不画胶囊、不补起电脑连接、也不替用户恢复,直到用户自己打开 Maeve([resume])。
 */
object MaeveQuit {
    private val handler = Handler(Looper.getMainLooper())
    private var exitController: HandlerQuitExit? = null

    /**
     * 从这里退出。界面在前台时传 Activity(顺带关掉这一屏);悬浮胶囊等没有界面的地方传应用上下文。
     * 先立刻收起胶囊、停服务,过 [EXIT_DELAY_MS] 再清一遍通知并结束进程,留一点时间让服务停下来的善后做完
     * (任务服务销毁时会标中断、关掉留在后台的那一页)。
     */
    fun quit(context: Context) {
        val app = context.applicationContext as MaeveApp
        exitController?.cancel()
        val controller = HandlerQuitExit(handler, app)
        exitController = controller
        QuitCoordinator(
            FlagsQuitState(FeatureFlags.get(app)),
            AndroidQuitEffects(app, context as? Activity),
            controller,
        ).quit()
    }

    /**
     * 用户又打开了 Maeve:撤掉还没执行的退出,清掉退出标记,让胶囊和启动时略过的事(没结束的运行的善后、
     * 运行环境准备)照常。进程已经被结束、系统重新拉起来时走的也是这里。
     */
    fun resume(context: Context) {
        val app = context.applicationContext as MaeveApp
        val controller = exitController ?: HandlerQuitExit(handler, app)
        exitController = null
        QuitCoordinator(
            FlagsQuitState(FeatureFlags.get(app)),
            AndroidQuitEffects(app, null),
            controller,
        ).resume()
    }

    private class FlagsQuitState(private val flags: FeatureFlags) : QuitState {
        override fun quitRequested(): Boolean = flags.quitRequested()
        override fun setQuitRequested(on: Boolean) = flags.setQuitRequested(on)
    }

    private class AndroidQuitEffects(
        private val app: MaeveApp,
        private val activity: Activity?,
    ) : QuitEffects {
        override fun stopCapsule() {
            // 浮层挂在无障碍服务上,进程结束前也可能还活着:先当场拆掉,别等进程
            runCatching { app.capsule.stopNow() }
        }

        override fun stopBackgroundWork() {
            // 先撤掉系统闹钟和进程内的长任务,再停前台服务;服务被系统重交启动请求时也会按退出标记拒绝工作
            runCatching { TriggerRuntime.suspendAlarms(app) }
            runCatching { RuntimePreparer.stop(app) }
            runCatching { app.pageKeeper.close(KeptPageClose.AppExit) }
            runCatching { app.wechat.stop() }
            runCatching { app.mcp.stopServer() }
            runCatching { app.chatGptSignIn.cancel() }
        }

        override fun stopServices() {
            listOf(
                TaskService::class.java,
                McpService::class.java,
                WechatService::class.java,
                SandboxService::class.java,
                ChatGptSignInService::class.java,
            ).forEach { service -> runCatching { app.stopService(Intent(app, service)) } }
        }

        override fun finishActivity() {
            activity?.finishAndRemoveTask()
        }

        override fun clearNotifications() {
            runCatching { app.getSystemService(NotificationManager::class.java)?.cancelAll() }
        }

        override fun resumeCapsule() {
            app.capsule.resume()
        }

        override fun resumeServices() {
            app.resumeAfterQuit()
        }
    }

    private class HandlerQuitExit(
        private val handler: Handler,
        private val app: MaeveApp,
    ) : QuitExit {
        private var exit: Runnable? = null

        override fun cancel() {
            exit?.let(handler::removeCallbacks)
            exit = null
        }

        override fun schedule() {
            cancel()
            val r = Runnable {
                exit = null
                // 服务停下来时可能又发了一条「任务已中断」;收干净再走
                app.getSystemService(NotificationManager::class.java)?.cancelAll()
                Process.killProcess(Process.myPid())
            }
            exit = r
            handler.postDelayed(r, EXIT_DELAY_MS)
        }
    }

    /** 给停服务、写对话留一点时间;用户看上去是点了就退,不会有明显停顿。 */
    private const val EXIT_DELAY_MS = 800L
}
