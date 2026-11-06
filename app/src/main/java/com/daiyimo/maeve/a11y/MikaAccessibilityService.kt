package com.daiyimo.maeve.a11y

import android.view.accessibility.AccessibilityEvent
import com.daiyimo.maeve.MikaApp
import com.daiyimo.maeve.cap.phone.a11y.ScreenAccessibility

/** 系统按这个全名找 Mika 的无障碍服务(清单、系统设置里存的就是它);实现在手机能力里。 */
class MikaAccessibilityService : ScreenAccessibility() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        // 服务重连后补一次 amoo 前台同步(断开期间的窗口事件丢了,见 AmooIntercept.onServiceConnected)
        (application as? MikaApp)?.amooIntercept?.onServiceConnected(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        super.onAccessibilityEvent(event)
        // amoo 任务截获:开关关着时 AmooIntercept 第一行就返回,不给聊天滚动添负担
        val app = application as? MikaApp ?: return
        if (ScreenAccessibility.instance.value !== this) return
        app.amooIntercept.onAccessibilityEvent(event, this)
    }
}
