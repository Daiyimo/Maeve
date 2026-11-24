package com.daiyimo.maeve.core.uistate

import com.daiyimo.maeve.core.tool.ActionDelivery

/** 端口失败发生在投递之前还是之后;没有证明时保持 unknown,不能盲猜 not_sent 或 sent。 */
internal object ActionDeliveryPolicy {
    fun of(reason: String): ActionDelivery = when (reason) {
        in NOT_SENT -> ActionDelivery.NotSent
        in SENT -> ActionDelivery.Sent
        else -> ActionDelivery.Unknown
    }

    private val NOT_SENT = setOf(
        "ref_expired", "unsupported", "double_tap_unsupported", "not_supported",
        "no_app_open", "invalid_gesture", "phone_unavailable", "no_tab_open", "tab_crashed",
        "handed_over", "dialog_open", "no_history", "key_not_allowed", "devtools_unavailable",
        "not_a_select", "select_disabled", "option_disabled", "no_such_option", "no_dialog",
        "not_editable", "no_focus", "upload_unavailable", "upload_not_opened", "offscreen", "obscured",
        "app_in_use_by_user", APP_IN_USE_BY_OTHER_TASK, "app_not_found", "copy_not_found",
        "copy_not_opened", "cannot_share", "link_unresolved", "invalid_link", "own_page_not_allowed",
        "launch_failed", "paste_unavailable",
    )

    private val SENT = setOf(
        "app_not_in_front", "opened_elsewhere", "keyboard_still_up",
        "point_not_typed", "select_not_applied",
    )
}
