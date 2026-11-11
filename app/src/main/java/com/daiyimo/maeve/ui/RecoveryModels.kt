package com.daiyimo.maeve.ui

import com.daiyimo.maeve.R
import com.daiyimo.maeve.i18n.tr
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.daiyimo.maeve.core.shell.AutoOnBlock
import com.daiyimo.maeve.core.shell.HelperRestart
import com.daiyimo.maeve.core.shell.PermissionResult
import com.daiyimo.maeve.core.shell.RecoveryCapability
import com.daiyimo.maeve.core.shell.RecoveryConsent
import com.daiyimo.maeve.core.shell.RepairOutcome
import com.daiyimo.maeve.core.shell.RestoreOutcome
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.daiyimo.maeve.ui.theme.MaeveIcons

object RecoveryCopy {
    data class SwitchCopy(val title: String, val subtitle: String, val explain: String)

    fun switch(c: RecoveryCapability): SwitchCopy = when (c) {
        RecoveryCapability.Accessibility -> SwitchCopy(
            tr(R.string.rec_a11y),
            tr(R.string.rec_default_on),
            tr(R.string.rec_a11y_body),
        )
        RecoveryCapability.BatteryWhitelist -> SwitchCopy(
            tr(R.string.rec_battery),
            tr(R.string.rec_default_on),
            tr(R.string.rec_battery_body),
        )
        RecoveryCapability.RestoreAfterReboot -> SwitchCopy(
            tr(R.string.rec_boot),
            tr(R.string.rec_default_on),
            tr(R.string.rec_boot_body),
        )
        RecoveryCapability.AutoWirelessDebugging -> SwitchCopy(
            tr(R.string.rec_auto_wireless),
            tr(R.string.rec_default_on),
            tr(R.string.rec_auto_wireless_body),
        )
    }

    val NEED_SETUP: String get() = tr(R.string.rec_need_setup)
    val FAILED: String get() = tr(R.string.rec_failed)
    val WORKING: String get() = tr(R.string.rec_working)

    /** 开关拨动后的一行反馈;成功时不多说。 */
    fun result(r: PermissionResult): String? = when (r) {
        PermissionResult.Done -> null
        PermissionResult.NoRunner -> NEED_SETUP
        is PermissionResult.Failed -> FAILED
    }

    /** 要靠配对过的无线调试才起作用的几项:只用 USB 启动命令起过助手时用不上。 */
    private val NEEDS_PAIRING = setOf(RecoveryCapability.RestoreAfterReboot, RecoveryCapability.AutoWirelessDebugging)

    /**
     * 默认打开、还没生效的那一项下面说一句:没配好手机操作就提示先完成设置(配好后自动打开),
     * 自动打开没成功就照实说;生效了或用户关掉了不多说。
     * 助手只用 USB 启动命令起过([HelperRestart.UsbCommand])时,靠无线调试重起助手的两项说清用不上、重启后要再运行命令。
     */
    fun pending(
        c: RecoveryCapability, consent: RecoveryConsent, paired: Boolean, autoFailed: Set<RecoveryCapability>,
        restart: HelperRestart = HelperRestart.None,
    ): String? = when {
        !consent.wants(c) -> null
        restart == HelperRestart.UsbCommand && c in NEEDS_PAIRING -> tr(R.string.rec_needs_pairing)
        consent.isApplied(c) -> null
        !paired -> NEED_SETUP
        c in autoFailed -> FAILED
        else -> null
    }

    /** 「替我打开无障碍」下面,上次自动重启无障碍服务成功的时间;没成功时不说,界面顶上的提示照旧给手动处理的办法。 */
    fun restarted(o: RepairOutcome?, zone: ZoneId = ZoneId.systemDefault()): String? =
        o?.takeIf { it.restarted }?.let { tr(R.string.rec_restarted_at, HH_MM.format(Instant.ofEpochMilli(it.atMillis).atZone(zone))) }

    private val HH_MM = DateTimeFormatter.ofPattern("HH:mm")

    /** 「自动打开无线调试」下面,替你打开没成功、先停下的原因;没停下不多说。 */
    fun autoOnBlocked(b: AutoOnBlock?): String? = when (b) {
        null -> null
        AutoOnBlock.NotAllowedHere -> tr(R.string.rec_not_allowed_here)
        AutoOnBlock.Refused -> tr(R.string.rec_refused)
        AutoOnBlock.GaveUp -> tr(R.string.rec_gave_up)
    }

    /** 「重启后自动恢复」下面,上次开机的结果;正常恢复了不多说。 */
    fun lastRestore(o: RestoreOutcome?): String? = when (o) {
        null, RestoreOutcome.Restored -> null
        RestoreOutcome.PermissionMissing -> tr(R.string.rec_permission_missing)
        RestoreOutcome.SettingUnavailable -> tr(R.string.rec_setting_unavailable)
        RestoreOutcome.NotRecovered -> tr(R.string.rec_not_recovered)
    }
}


private fun iconOf(c: RecoveryCapability): ImageVector = when (c) {
    RecoveryCapability.Accessibility -> MaeveIcons.Hand
    RecoveryCapability.BatteryWhitelist -> MaeveIcons.Sun
    RecoveryCapability.RestoreAfterReboot -> MaeveIcons.Wrench
    RecoveryCapability.AutoWirelessDebugging -> MaeveIcons.Refresh
}
