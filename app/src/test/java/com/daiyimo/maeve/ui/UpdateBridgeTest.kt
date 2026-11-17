package com.daiyimo.maeve.ui

import com.daiyimo.maeve.about.ApkDownloadState
import com.daiyimo.maeve.about.ApkRejected
import com.daiyimo.maeve.about.CheckProblem
import com.daiyimo.maeve.about.CheckStatus
import com.daiyimo.maeve.about.DownloadPhase
import com.daiyimo.maeve.about.MaeveLinks
import com.daiyimo.maeve.about.ReleaseInfo
import com.daiyimo.maeve.about.UpdateState
import com.daiyimo.maeve.i18n.ChineseTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 更新桥只做搬运与文案:检查/选包/校验的规则在 about 包里另有单测。
 * 这里盯的是「界面拿到的那份状态是不是如实的」——尤其是"能不能下""能不能装"。
 */
class UpdateBridgeTest {
    @get:Rule val texts = ChineseTexts()

    private val apk = "${MaeveLinks.RELEASES}/download/v0.2.0/Maeve-v0.2.0.apk"
    private val digest = "sha256:" + "a".repeat(64)
    private val release = ReleaseInfo(
        "0.2.0", "Maeve 0.2.0", "- 更快", "2026-11-20",
        "${MaeveLinks.RELEASES}/tag/v0.2.0", apk, 12_345_678L, digest,
    )

    @Test fun snapshotCarriesBothVersionsAndDownloadProgress() {
        val state = UpdateState(latest = release, update = release, status = CheckStatus.Done, manualRounds = 2)
        val map = UpdateBridge.snapshot(state, ApkDownloadState(DownloadPhase.Downloading, 6_000_000, 12_000_000), "0.1.0", canInstall = false)
        assertEquals(true, map["available"])
        assertEquals("0.1.0", map["currentVersion"])
        assertEquals("done", map["status"])
        assertEquals(2, map["manualRounds"])
        assertEquals(true, map["hasUpdate"])
        assertEquals(false, map["canInstall"])
        assertEquals(MaeveLinks.RELEASES, map["releasesUrl"])
        @Suppress("UNCHECKED_CAST")
        val download = map["download"] as Map<String, Any?>
        assertEquals("downloading", download["phase"])
        assertEquals(50, download["percent"])
        @Suppress("UNCHECKED_CAST")
        val latest = map["latest"] as Map<String, Any?>
        assertEquals("0.2.0", latest["version"])
        assertEquals("- 更快", latest["notes"])
        assertEquals(true, latest["canDownload"])
    }

    /** 从没查过时不能假装有结果:页面据此显示「尚未检查」而不是「已是最新」。 */
    @Test fun emptyStateIsNotAvailable() {
        val map = UpdateBridge.snapshot(UpdateState(), ApkDownloadState(), "0.1.0", canInstall = true)
        assertEquals(false, map["available"])
        assertEquals(false, map["hasUpdate"])
        assertNull(map["latest"])
        assertNull(map["update"])
        assertEquals("idle", map["status"])
        assertEquals(0, map["manualRounds"])
        assertNull(map["problem"])
    }

    /**
     * 「没有已发布的版本」和「没连上」必须分开:前者网络是通的,用户不该跑去调网络
     * (发布第一个版本之前,检查更新一直是这样)。
     */
    @Test fun noReleaseAndUnreachableAreDistinct() {
        val none = UpdateBridge.snapshot(
            UpdateState(status = CheckStatus.Failed, problem = CheckProblem.NoRelease),
            ApkDownloadState(), "0.1.0", canInstall = true,
        )
        // 线上名字和 Flutter 侧 UpdateProblem 的枚举名逐一对应(大小写也算):
        // 写成下划线就会静默落成默认值,页面上「没有发布」和「连不上」就看出来了
        assertEquals("noRelease", none["problem"])
        val lost = UpdateBridge.snapshot(
            UpdateState(status = CheckStatus.Failed, problem = CheckProblem.Unreachable),
            ApkDownloadState(), "0.1.0", canInstall = true,
        )
        assertEquals("unreachable", lost["problem"])
    }

    /** 缺摘要或缺字节数就不给应用内下载:校验做不了,只能去发布页。 */
    @Test fun downloadNeedsSizeAndDigest() {
        assertTrue(UpdateBridge.release(release)["canDownload"] == true)
        assertFalse(UpdateBridge.release(release.copy(apkDigest = null))["canDownload"] == true)
        assertFalse(UpdateBridge.release(release.copy(apkSize = null))["canDownload"] == true)
        assertFalse(UpdateBridge.release(release.copy(apkUrl = null))["canDownload"] == true)
        // 没有直链时「从 GitHub 下载」退回发布页,不是空链接。
        assertEquals(release.pageUrl, UpdateBridge.release(release.copy(apkUrl = null))["downloadUrl"])
    }

    @Test fun primaryActionFollowsDownloadPhase() {
        // total = -1 表示还不知道总大小(没有百分比);给了 percent 就按它折算已下字节。
        fun action(phase: DownloadPhase, percent: Int? = null, busy: Boolean = false, canDownload: Boolean = true) =
            UpdateBridge.action(
                if (percent == null) ApkDownloadState(phase) else ApkDownloadState(phase, percent.toLong(), 100),
                busy, canDownload,
            )

        assertEquals(UpdateBridge.Action.Kind.Download, action(DownloadPhase.Idle).kind)
        assertEquals(AboutCopy.UPDATE_DOWNLOAD_IN_APP, action(DownloadPhase.Idle).label)
        assertEquals(UpdateBridge.Action.Kind.Download, action(DownloadPhase.Failed).kind)
        assertEquals(AboutCopy.UPDATE_RETRY, action(DownloadPhase.Failed).label)
        assertEquals(UpdateBridge.Action.Kind.Install, action(DownloadPhase.Ready).kind)
        // 下载中/暂停:按钮只是进度文字,不可点(要停就点删除下载)。
        assertFalse(action(DownloadPhase.Downloading, percent = 40).enabled)
        assertEquals(AboutCopy.updateProgress(40), action(DownloadPhase.Downloading, percent = 40).label)
        assertEquals(AboutCopy.UPDATE_DOWNLOADING, action(DownloadPhase.Downloading).label)
        assertFalse(action(DownloadPhase.Paused).enabled)
        // 正在处理时压过一切,避免连点。
        assertFalse(action(DownloadPhase.Idle, busy = true).enabled)
        assertEquals(AboutCopy.UPDATE_WORKING, action(DownloadPhase.Idle, busy = true).label)
        // 这个发布没有可下的包:不给下载动作。
        assertEquals(UpdateBridge.Action.Kind.None, action(DownloadPhase.Idle, canDownload = false).kind)
    }

    @Test fun cancelOnlyAppearsWhenThereIsSomethingToDelete() {
        assertFalse(UpdateBridge.cancellable(ApkDownloadState()))
        assertFalse(UpdateBridge.cancellable(ApkDownloadState(DownloadPhase.Failed)))
        assertTrue(UpdateBridge.cancellable(ApkDownloadState(DownloadPhase.Downloading)))
        assertTrue(UpdateBridge.cancellable(ApkDownloadState(DownloadPhase.Paused)))
        assertTrue(UpdateBridge.cancellable(ApkDownloadState(DownloadPhase.Ready)))
    }

    /** 三种拒绝各说各的原因,不能都糊成"下载失败"。 */
    @Test fun rejectionReasonsAreDistinct() {
        val messages = ApkRejected.Reason.entries.map { UpdateBridge.rejection(ApkRejected(it)) }
        assertEquals(3, messages.toSet().size)
        assertEquals(AboutCopy.UPDATE_BAD_FILE, UpdateBridge.rejection(ApkRejected(ApkRejected.Reason.Corrupt)))
        assertEquals(AboutCopy.UPDATE_NOT_NEWER, UpdateBridge.rejection(ApkRejected(ApkRejected.Reason.NotNewer)))
        assertEquals(AboutCopy.UPDATE_BAD_SIGNER, UpdateBridge.rejection(ApkRejected(ApkRejected.Reason.Signer)))
    }
}
