package com.daiyimo.maeve.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.daiyimo.maeve.R
import com.daiyimo.maeve.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.daiyimo.maeve.core.model.ModelRole
import com.daiyimo.maeve.settings.ModelContextSettings
import com.daiyimo.maeve.settings.ModelOption
import com.daiyimo.maeve.settings.ModelOptions
import com.daiyimo.maeve.settings.ProviderConfig
import com.daiyimo.maeve.settings.RoleChoice
import com.daiyimo.maeve.ui.theme.MaeveType

class ModelSetupActions(
    val selectPreset: (String) -> Unit = {},
    val setKind: (ProviderConfig.Kind) -> Unit = {},
    val setBaseUrl: (String) -> Unit = {},
    val setApiKey: (String) -> Unit = {},
    val setModel: (String) -> Unit = {},
    /** 「手动填写模型名」的开和收。 */
    val setManual: (Boolean) -> Unit = {},
    val setName: (String) -> Unit = {},
    val testAndSave: () -> Unit = {},
    /** 不测试,直接保存(见 [ModelSetupViewModel.saveWithoutTest])。 */
    val saveUntested: () -> Unit = {},
    val openForm: () -> Unit = {},
    val closeForm: () -> Unit = {},
    val openProvider: (String) -> Unit = {},
    val pickRole: (ModelRole) -> Unit = {},
    /** 打开「语音转文字」的选择页;为 null 时不显示这张卡(上手流程)。 */
    val openSpeech: (() -> Unit)? = null,
    /** 打开 Token 用量;为 null 时不显示入口(上手流程)。 */
    val openUsage: (() -> Unit)? = null,
    val next: () -> Unit = {},
    /** 打开一家账号登录的说明面板。 */
    val openSignIn: (SignInKind) -> Unit = {},
    val setReasoningEffort: (ModelRole, String?) -> Unit = { _, _ -> },
    val setModelContext: (String, ModelContextSettings) -> Boolean = { _, _ -> false },
)


private class AndroidSignInBrowser(private val context: Context) : SignInBrowser {
    override fun open(url: String) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, tr(R.string.chatgpt_no_browser), Toast.LENGTH_SHORT).show()
        }
    }

    override fun returnToApp() {
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
        }
    }

    override fun page(failure: String?): String {
        val text = failure ?: tr(R.string.chatgpt_page_done)
        val back = "intent:#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;package=${context.packageName};end"
        return SignInPage.html(tr(R.string.chatgpt_page_title), text, tr(R.string.chatgpt_page_back), back)
    }
}


internal object SignInPage {
    fun html(title: String, text: String, back: String, backUrl: String): String {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        return """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">""" +
            """<title>${esc(title)}</title><style>body{font-family:sans-serif;margin:0;padding:48px 24px;text-align:center;color:#1d1d1f;background:#fff}""" +
            """p{font-size:17px;line-height:1.5}a{display:inline-block;margin-top:16px;padding:12px 28px;border-radius:999px;background:#1d1d1f;""" +
            """color:#fff;text-decoration:none;font-size:16px}</style></head><body><p>${esc(text)}</p><a href="${esc(backUrl)}">${esc(back)}</a></body></html>"""
    }
}


private fun hasModelContextControl(ui: ModelSetupUi, role: ModelRole): Boolean =
    ui.roles[role] is RoleChoice.Model && ui.configFor(role) != null


internal val ModelIdStyle = MaeveType.Body.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)


object ModelSetupCopy {
    val TITLE: String get() = tr(R.string.model_setup_title)
    val LEAD: String get() = tr(R.string.model_setup_lead)
    val ONBOARDING_NOTE: String get() = tr(R.string.model_setup_onboarding_note)
    val CANCEL: String get() = tr(R.string.cancel)
    val KIND: String get() = tr(R.string.model_kind)
    val BASE_URL: String get() = tr(R.string.model_base_url)
    val NAME: String get() = tr(R.string.model_provider_name)
    val NAME_HINT: String get() = tr(R.string.model_provider_name_hint)
    const val API_KEY = "API Key"
    val KEY_HINT: String get() = tr(R.string.model_key_hint)
    val KEY_OPTIONAL: String get() = tr(R.string.model_key_optional)
    val TEST_AND_SAVE: String get() = tr(R.string.model_test_save)
    val TESTING: String get() = tr(R.string.model_testing)
    val PICK_MAIN: String get() = tr(R.string.model_pick_main)
    val TEST_NOTE: String get() = tr(R.string.model_test_note)
    val SELF_HOSTED_NOTE: String get() = tr(R.string.model_self_hosted_note)
    val MODEL_NAME: String get() = tr(R.string.model_manual_name)
    val NO_LIST_CUSTOM: String get() = tr(R.string.model_no_list_custom)
    val NO_LIST_PRESET: String get() = tr(R.string.model_no_list_preset)
    val MANUAL_ON: String get() = tr(R.string.model_manual_on)
    val MANUAL_OFF: String get() = tr(R.string.model_manual_off)
    val MANUAL_NOTE: String get() = tr(R.string.model_manual_note)
    val SAVE_UNTESTED: String get() = tr(R.string.model_save_untested)
    val SAVE_UNTESTED_TITLE: String get() = tr(R.string.model_save_untested_title)
    val SAVE_UNTESTED_BODY: String get() = tr(R.string.model_save_untested_body)
    val SAVE_UNTESTED_CONFIRM: String get() = tr(R.string.model_save_untested_confirm)

    /** 手填模型名时举的例子,按接口类型(各家模型名的样子不同)。 */
    fun modelExample(kind: ProviderConfig.Kind): String = tr(
        when (kind) {
            ProviderConfig.Kind.Gemini -> R.string.model_example_gemini
            ProviderConfig.Kind.Anthropic -> R.string.model_example_anthropic
            ProviderConfig.Kind.OpenAiResponses, ProviderConfig.Kind.ChatGpt -> R.string.model_example_openai
            ProviderConfig.Kind.Xai -> R.string.model_example_grok
            ProviderConfig.Kind.OpenAiCompat -> R.string.model_example_compat
        },
    )

    /** 自定义地址是某家官方地址时的建议;接口类型和那家对不上时补一句。 */
    fun presetSuggestion(f: AddForm): String {
        val preset = f.suggestedPreset ?: return ""
        val match = tr(R.string.model_preset_match, preset.label)
        return if (!f.presetKindMismatch) match
        else match + tr(R.string.sentence_sep) + tr(R.string.model_preset_kind_mismatch, preset.label, ModelsCopy.kindLabel(preset.kind), ModelsCopy.kindLabel(f.kind))
    }

    fun usePreset(label: String) = tr(R.string.model_use_preset, label)
    val NEXT: String get() = tr(R.string.next)

    fun failed(reason: String) = tr(R.string.model_failed, reason)

    /** 模型的能力标签:(文字, 是否强调色)。接口没给、用户也没说、按预设推断的标「未确认」。只作参考,不拦选择。 */
    fun tags(o: ModelOption, forVision: Boolean = false): List<Pair<String, Boolean>> = buildList {
        val caps = o.capabilities
        val visionKnown = o.visionKnown || o.visionByUser != null
        if (o.typed) add(tr(R.string.cap_typed) to false)
        if (forVision) {
            add((if (visionKnown) tr(R.string.cap_vision) else tr(R.string.cap_vision_unconfirmed)) to false)
        } else {
            when {
                !caps.toolCalls -> add(tr(R.string.cap_no_tools) to false)
                o.toolCallsKnown -> add(tr(R.string.cap_tools) to true)
                else -> add(tr(R.string.cap_tools_unconfirmed) to false)
            }
            if (caps.vision && visionKnown) add(tr(R.string.cap_vision) to false)
        }
        if (caps.reasoning) add(tr(R.string.cap_reasoning) to false)
        if (o.contextKnown) add(ModelOptions.contextLabel(caps.contextTokens) to false)
    }
}
