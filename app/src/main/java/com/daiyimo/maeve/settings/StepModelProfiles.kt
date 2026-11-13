package com.daiyimo.maeve.settings

/** Official model capabilities used only when the catalogue omits them.
 * https://platform.stepfun.com/docs/zh/guides/developer/reasoning
 */
object StepModelProfiles {
    const val API = "https://api.stepfun.com/v1"
    const val PLAN = "https://api.stepfun.com/step_plan/v1"
    val efforts = listOf("low", "medium", "high")
    fun isEndpoint(url: String) = url.trimEnd('/') in setOf(API, PLAN)
    fun isTaskModel(model: String): Boolean {
        val id = model.lowercase()
        return !id.startsWith("stepaudio-") || listOf("-tts", "-asr", "-realtime").none { it in id }
    }
    data class Profile(val vision: Boolean, val context: Int, val reasoning: Boolean = true)
    fun of(model: String): Profile? = when (model.lowercase()) {
        "step-5-preview" -> Profile(true, 1_000_000)
        "step-3.7-flash" -> Profile(true, 262_144)
        "step-3.5-flash", "step-3.5-flash-2603" -> Profile(false, 262_144)
        else -> null
    }
    fun levels(model: String): List<String> = when (model) {
        "step-5-preview", "step-3.7-flash" -> efforts
        "step-3.5-flash-2603" -> listOf("low", "high")
        else -> emptyList()
    }
}
