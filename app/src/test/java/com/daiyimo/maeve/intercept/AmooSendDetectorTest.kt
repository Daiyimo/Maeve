package com.daiyimo.maeve.intercept

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmooSendDetectorTest {

    private var clock = 1_000L
    private val detector = AmooSendDetector { clock }

    private fun inAmooChat() =
        detector.onWindowStateChanged(AmooSendDetector.AMOO_PACKAGE, "com.stepos.ailauncher.chat.activity.ChatActivity")

    @Test fun nothingPendingWithoutDraft() {
        inAmooChat()
        assertNull(detector.pendingDraft())
    }

    @Test fun draftAppearsAfterTyping() {
        inAmooChat()
        detector.noteInputText("打开设置看型号")
        assertEquals("打开设置看型号", detector.pendingDraft())
    }

    @Test fun clearedInputKeepsDraft() {
        inAmooChat()
        detector.noteInputText("打开设置看型号")
        detector.noteInputText("") // 发送后输入框被清空,草稿要留着认领气泡
        assertEquals("打开设置看型号", detector.pendingDraft())
    }

    @Test fun otherAppInputIgnored() {
        detector.onWindowStateChanged("com.android.settings", "com.android.settings.SettingsActivity")
        detector.noteInputText("别处的输入框")
        assertNull(detector.pendingDraft())
    }

    @Test fun nonChatAmooPageIgnored() {
        detector.onWindowStateChanged(AmooSendDetector.AMOO_PACKAGE, "com.stepos.ailauncher.settings.SettingsActivity")
        assertFalse(detector.inAmooChat)
        detector.noteInputText("amoo 设置页里搜点什么")
        assertNull(detector.pendingDraft())
    }

    @Test fun staleDraftDrops() {
        inAmooChat()
        detector.noteInputText("很久以前打的字")
        clock += AmooSendDetector.MAX_DRAFT_AGE_MS + 1
        assertNull(detector.pendingDraft())
    }

    @Test fun freshDraftWithinAgeSurvives() {
        inAmooChat()
        detector.noteInputText("刚打的字")
        clock += AmooSendDetector.MAX_DRAFT_AGE_MS - 1
        assertEquals("刚打的字", detector.pendingDraft())
    }

    @Test fun sameTextNotInterceptedTwice() {
        inAmooChat()
        detector.noteInputText("1+1 等于几")
        detector.markIntercepted("1+1 等于几")
        assertNull(detector.pendingDraft())
    }

    @Test fun sameTextWithinDedupWindowSkippedEvenAfterRetype() {
        inAmooChat()
        detector.noteInputText("再试一次")
        detector.markIntercepted("再试一次")
        // 用户删掉重打同样的字:输入框文本经历别的中间态,最终回到同一文本——
        // 去重窗口内照样跳过(分不清「旧气泡还在」和「真的又发了一遍」,宁可漏一条也不误建)
        detector.noteInputText("再试一")
        detector.noteInputText("再试一次")
        assertNull(detector.pendingDraft())
    }

    @Test fun sameTextRetypePastWindowStillStale() {
        inAmooChat()
        detector.noteInputText("1+1 等于几")
        detector.markIntercepted("1+1 等于几")
        clock += AmooSendDetector.DEDUP_WINDOW_MS + 1
        // 去重窗口过了,但草稿也过了寿命:没有新的输入事件刷新它,旧气泡不该把任务再拉起来
        assertNull(detector.pendingDraft())
        // 真的重新打一遍(中间态不同)才会刷新草稿年龄,这时才允许再截获
        detector.noteInputText("1+1 等于")
        detector.noteInputText("1+1 等于几")
        assertEquals("1+1 等于几", detector.pendingDraft())
    }

    @Test fun differentTextStillWorksAfterIntercept() {
        inAmooChat()
        detector.noteInputText("第一件事")
        detector.markIntercepted("第一件事")
        detector.noteInputText("第二件事")
        assertEquals("第二件事", detector.pendingDraft())
    }

    @Test fun resetClearsEverything() {
        inAmooChat()
        detector.noteInputText("打到一半")
        detector.markIntercepted("打到一半")
        detector.reset()
        assertFalse(detector.inAmooChat)
        assertNull(detector.pendingDraft())
    }

    @Test fun wasInterceptedTracksTime() {
        detector.markIntercepted("甲")
        assertTrue(detector.wasIntercepted("甲"))
        assertFalse(detector.wasIntercepted("乙"))
        clock += AmooSendDetector.DEDUP_WINDOW_MS + 1
        assertFalse(detector.wasIntercepted("甲"))
    }

    @Test fun isAmooChatAcceptsNullClassName() {
        assertTrue(AmooSendDetector.isAmooChat(AmooSendDetector.Foreground(AmooSendDetector.AMOO_PACKAGE, null)))
        assertFalse(AmooSendDetector.isAmooChat(AmooSendDetector.Foreground("com.tencent.mm", "com.tencent.mm.ui.LauncherUI")))
        assertFalse(AmooSendDetector.isAmooChat(null))
    }

    @Test fun noteAmooForegroundMarksChatWithoutClassName() {
        // 服务重连后只拿得到包名:className 为空也要认,否则断开期间的窗口切换白丢了
        assertFalse(detector.inAmooChat)
        detector.noteAmooForeground()
        assertTrue(detector.inAmooChat)
        detector.noteInputText("重连后打的字")
        assertEquals("重连后打的字", detector.pendingDraft())
    }

    @Test fun noteAmooForegroundOverridesOtherApp() {
        detector.onWindowStateChanged("com.tencent.mm", "com.tencent.mm.ui.LauncherUI")
        detector.noteAmooForeground()
        assertTrue(detector.inAmooChat)
    }

    @Test fun noteAmooForegroundKeepsKnownNonChatPage() {
        // 已经知道是 amoo 的设置页/搜索页时,扫描补记不能把前台改成「包名兜底」,
        // 否则 isAmooChat 只看包名,闸门 1 名存实亡
        detector.onWindowStateChanged(AmooSendDetector.AMOO_PACKAGE, "com.stepos.ailauncher.settings.SettingsActivity")
        detector.noteAmooForeground()
        assertFalse(detector.inAmooChat)
        detector.noteInputText("设置页搜索框里打的字")
        assertNull(detector.pendingDraft())
    }

    @Test fun noteAmooForegroundKeepsKnownChatPage() {
        inAmooChat()
        detector.noteAmooForeground()
        assertTrue(detector.inAmooChat)
    }
}
