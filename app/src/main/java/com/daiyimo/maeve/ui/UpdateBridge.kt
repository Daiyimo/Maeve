package com.daiyimo.maeve.ui

import com.daiyimo.maeve.about.ApkDownloadState
import com.daiyimo.maeve.about.ApkRejected
import com.daiyimo.maeve.about.CheckProblem
import com.daiyimo.maeve.about.DownloadPhase
import com.daiyimo.maeve.about.MaeveLinks
import com.daiyimo.maeve.about.ReleaseInfo
import com.daiyimo.maeve.about.UpdateState

/**
 * 「获取更新」的桥接面:把宿主已有的检查更新([com.daiyimo.maeve.about.UpdateChecker])与
 * 应用内下载安装([com.daiyimo.maeve.about.ApkUpdater])翻成 Flutter 认得的 map。
 *
 * **为什么在这里而不是各写一套:** 检查节流、GitHub 解析、ABI 选包、大小/摘要/签名校验
 * 都已经在 about 包里实现并有单测。界面换成 Flutter 之后这些规则一行都不该重写,
 * 否则两套规则会漂移(尤其是"什么包才允许装")。这里只做数据搬运与文案。
 *
 * 文案走 [AboutCopy](资源已在 10 份语言里),不新增字符串键。
 */
internal object UpdateBridge {
    /** 一次「获取更新」页面要的全部状态。[available] 为 false 表示还没查到任何发布。 */
    fun snapshot(state: UpdateState, download: ApkDownloadState, currentVersion: String, canInstall: Boolean,
                 accel: Map<String, Any?> = emptyMap()): Map<String, Any?> = mapOf(
        "available" to (state.latest != null),
        "currentVersion" to currentVersion,
        "status" to state.status.name.lowercase(java.util.Locale.ROOT),
        "manualRounds" to state.manualRounds,
        "hasUpdate" to state.hasUpdate,
        // 手动检查没查到时的原因:noRelease(仓库可达但没有发布)/ unreachable(没连上);null 表示没失败。
        // 名字和 Flutter 侧 UpdateProblem 的枚举名逐一对应(大小写也算),别改成下划线写法。
        "problem" to when (state.problem) {
            CheckProblem.NoRelease -> "noRelease"
            CheckProblem.Unreachable -> "unreachable"
            null -> null
        },
        "latest" to state.latest?.let(::release),
        "update" to state.update?.let(::release),
        "download" to mapOf(
            "phase" to download.phase.name.lowercase(java.util.Locale.ROOT),
            "bytes" to download.bytes,
            "total" to download.total,
            "percent" to download.percent,
        ),
        // 系统「安装未知应用」授权;没授权时安装按钮先带用户去开。
        "canInstall" to canInstall,
        "releasesUrl" to MaeveLinks.RELEASES,
        // GitHub 加速(见 com.daiyimo.maeve.settings.GitHubAccelStore)
        "accel" to accel,
    )

    fun release(r: ReleaseInfo): Map<String, Any?> = mapOf(
        "version" to r.version,
        "name" to r.name,
        "notes" to r.notes,
        "date" to r.date,
        "pageUrl" to r.pageUrl,
        "apkUrl" to r.apkUrl,
        "apkSize" to r.apkSize,
        "apkDigest" to r.apkDigest,
        // 有附件直链才谈得上应用内下载;否则只能去发布页。
        "canDownload" to (r.apkUrl != null && r.apkSize != null && r.apkDigest != null),
        "downloadUrl" to r.downloadUrl,
    )

    /**
     * 主按钮该写什么、该干什么。
     *
     * 下载中/暂停时按钮是禁用的(只能取消),所以 [enabled] 为 false;
     * 下好了就变「安装更新」,失败了变「重新下载」。
     */
    data class Action(val label: String, val kind: Kind, val enabled: Boolean) {
        enum class Kind { Download, Install, None }
    }

    fun action(download: ApkDownloadState, busy: Boolean, canDownload: Boolean): Action = when {
        busy -> Action(AboutCopy.UPDATE_WORKING, Action.Kind.None, false)
        download.phase == DownloadPhase.Ready -> Action(AboutCopy.UPDATE_INSTALL, Action.Kind.Install, true)
        download.phase == DownloadPhase.Downloading -> Action(
            download.percent?.let { AboutCopy.updateProgress(it) } ?: AboutCopy.UPDATE_DOWNLOADING,
            Action.Kind.None, false,
        )
        download.phase == DownloadPhase.Paused -> Action(AboutCopy.UPDATE_PAUSED, Action.Kind.None, false)
        !canDownload -> Action(AboutCopy.UPDATE_DOWNLOAD, Action.Kind.None, false)
        download.phase == DownloadPhase.Failed -> Action(AboutCopy.UPDATE_RETRY, Action.Kind.Download, true)
        else -> Action(AboutCopy.UPDATE_DOWNLOAD_IN_APP, Action.Kind.Download, true)
    }

    /** 下载中、暂停、已下好时才给「删除下载」——没东西可删时不该出现这个按钮。 */
    fun cancellable(download: ApkDownloadState): Boolean =
        download.phase in listOf(DownloadPhase.Downloading, DownloadPhase.Paused, DownloadPhase.Ready)

    /** 校验没过的原因说明;[ApkUpdater] 已经把文件删了,这里只负责把话说清楚。 */
    fun rejection(e: ApkRejected): String = when (e.reason) {
        ApkRejected.Reason.Corrupt -> AboutCopy.UPDATE_BAD_FILE
        ApkRejected.Reason.NotNewer -> AboutCopy.UPDATE_NOT_NEWER
        ApkRejected.Reason.Signer -> AboutCopy.UPDATE_BAD_SIGNER
    }
}
