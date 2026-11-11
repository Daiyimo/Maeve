package com.daiyimo.maeve.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.daiyimo.maeve.R
import com.daiyimo.maeve.about.Diagnostics
import com.daiyimo.maeve.core.selfcheck.Advice
import com.daiyimo.maeve.core.selfcheck.CheckResult
import com.daiyimo.maeve.core.selfcheck.CheckStatus
import com.daiyimo.maeve.core.selfcheck.SelfCheck
import com.daiyimo.maeve.core.selfcheck.SelfCheckReport
import com.daiyimo.maeve.core.selfcheck.SelfCheckState
import com.daiyimo.maeve.i18n.tr
import com.daiyimo.maeve.ui.components.TileTone
import com.daiyimo.maeve.ui.components.ValueTone
import com.daiyimo.maeve.ui.theme.MaeveIcons

object SelfCheckCopy {
    fun name(c: SelfCheck): String = tr(
        when (c) {
            SelfCheck.DEVICE -> R.string.selfcheck_check_device
            SelfCheck.PERMISSIONS -> R.string.selfcheck_check_permissions
            SelfCheck.HELPER -> R.string.selfcheck_check_helper
            SelfCheck.VIRTUAL_DISPLAY -> R.string.selfcheck_check_display
            SelfCheck.APP_LAUNCH -> R.string.selfcheck_check_launch
            SelfCheck.SCREEN_READING -> R.string.selfcheck_check_reading
            SelfCheck.ACTIONS -> R.string.selfcheck_check_actions
            SelfCheck.STAYS_ALIVE -> R.string.selfcheck_check_alive
            SelfCheck.MAIN_SCREEN -> R.string.selfcheck_check_main
            SelfCheck.BROWSER -> R.string.selfcheck_check_browser
            SelfCheck.SANDBOX -> R.string.selfcheck_check_sandbox
            SelfCheck.APP_COPIES -> R.string.selfcheck_check_copies
        },
    )

    fun status(s: CheckStatus): String = tr(
        when (s) {
            CheckStatus.Pending -> R.string.selfcheck_status_pending
            CheckStatus.Running -> R.string.selfcheck_status_running
            CheckStatus.Pass -> R.string.selfcheck_status_pass
            CheckStatus.Partial -> R.string.selfcheck_status_partial
            CheckStatus.Fail -> R.string.selfcheck_status_fail
            CheckStatus.Skipped -> R.string.selfcheck_status_skipped
        },
    )

    /** 一项下面的那一句:没通过时说原因和下一步,因为前一项没过而跳过时说缺哪一项。 */
    fun line(r: CheckResult): String {
        r.blockedBy?.let { return tr(R.string.selfcheck_line_needs, name(it)) }
        r.advice?.let { return advice(it) }
        return tr(
            when (r.status) {
                CheckStatus.Pending -> R.string.selfcheck_line_pending
                CheckStatus.Running -> R.string.selfcheck_line_running
                CheckStatus.Pass -> R.string.selfcheck_line_pass
                else -> R.string.selfcheck_line_other
            },
        )
    }

    fun advice(a: Advice): String = tr(
        when (a) {
            Advice.PhoneTooOld -> R.string.selfcheck_advice_too_old
            Advice.NoWirelessDebugging -> R.string.selfcheck_advice_no_wireless
            Advice.PhoneControlUnavailable -> R.string.selfcheck_advice_phone_unavailable
            Advice.TurnOnAccessibility -> R.string.selfcheck_advice_a11y_off
            Advice.RestartAccessibility -> R.string.selfcheck_advice_a11y_restart
            Advice.AllowNotifications -> R.string.selfcheck_advice_notifications
            Advice.AllowBattery -> R.string.selfcheck_advice_battery
            Advice.SetUpPhoneControl -> R.string.selfcheck_advice_setup
            Advice.DisplayFailed -> R.string.selfcheck_advice_display
            Advice.FocusShared -> R.string.selfcheck_advice_focus
            Advice.LaunchFailed -> R.string.selfcheck_advice_launch
            Advice.OpenedOnMainScreen -> R.string.selfcheck_advice_on_main
            Advice.SettingsInUse -> R.string.selfcheck_advice_settings_in_use
            Advice.ReadFailed -> R.string.selfcheck_advice_read
            Advice.BlankFrames -> R.string.selfcheck_advice_blank
            Advice.ActionFailed -> R.string.selfcheck_advice_action
            Advice.FramesStopped -> R.string.selfcheck_advice_frames
            Advice.MainScreenChanged -> R.string.selfcheck_advice_main
            Advice.NoWebView -> R.string.selfcheck_advice_no_webview
            Advice.WebFailed -> R.string.selfcheck_advice_web
            Advice.SandboxUnsupported -> R.string.selfcheck_advice_sandbox_unsupported
            Advice.SandboxNotPrepared -> R.string.selfcheck_advice_sandbox_not_prepared
            Advice.SandboxFailed -> R.string.selfcheck_advice_sandbox_failed
            Advice.TimedOut -> R.string.selfcheck_advice_timeout
            Advice.Error -> R.string.selfcheck_advice_error
            Advice.OutOfTime -> R.string.selfcheck_advice_out_of_time
            Advice.Cancelled -> R.string.selfcheck_advice_cancelled
        },
    )
}


private fun render(s: SelfCheckState) = SelfCheckReport.render(s, Diagnostics::sanitize)


private fun copyReport(context: Context, text: String, toast: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(SelfCheckReport.HEADER, text))
    Toast.makeText(context, toast, Toast.LENGTH_LONG).show()
}


private fun look(s: CheckStatus): Pair<ImageVector, TileTone> = when (s) {
    CheckStatus.Pending -> MaeveIcons.Clock to TileTone.Neutral
    CheckStatus.Running -> MaeveIcons.Refresh to TileTone.Accent
    CheckStatus.Pass -> MaeveIcons.Check to TileTone.Ok
    CheckStatus.Partial -> MaeveIcons.Exclaim to TileTone.Warn
    CheckStatus.Fail -> MaeveIcons.Close to TileTone.Bad
    CheckStatus.Skipped -> MaeveIcons.More to TileTone.Neutral
}


private fun valueTone(s: CheckStatus): ValueTone = when (s) {
    CheckStatus.Pass -> ValueTone.Ok
    CheckStatus.Partial -> ValueTone.Warn
    CheckStatus.Fail -> ValueTone.Bad
    else -> ValueTone.Plain
}
