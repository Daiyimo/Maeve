package com.daiyimo.maeve.ui

import com.daiyimo.maeve.R
import com.daiyimo.maeve.i18n.tr
import com.daiyimo.maeve.core.agent.SpeedFlags

data class DevSwitch(
    val id: String, val title: String, val subtitle: String, val checked: Boolean, val change: (Boolean) -> SpeedFlags,
)


object DeveloperSwitches {
    fun speed(flags: SpeedFlags): List<DevSwitch> = listOf(
        DevSwitch("adaptive-vision", tr(R.string.dev_adaptive), tr(R.string.dev_adaptive_sub), flags.adaptiveVision) { flags.copy(adaptiveVision = it) },
        DevSwitch("inline-screen", tr(R.string.dev_inline), tr(R.string.dev_inline_sub), flags.inlineScreen) { flags.copy(inlineScreen = it) },
        DevSwitch("batch-steps", tr(R.string.dev_batch), tr(R.string.dev_batch_sub), flags.batchSteps) { flags.copy(batchSteps = it) },
        DevSwitch("deep-links", tr(R.string.dev_deep_links), tr(R.string.dev_deep_links_sub), flags.deepLinks) { flags.copy(deepLinks = it) },
        DevSwitch("light-steps", tr(R.string.dev_light), tr(R.string.dev_light_sub), flags.lightSteps) { flags.copy(lightSteps = it) },
    )

    fun experimental(flags: SpeedFlags): List<DevSwitch> = listOf(
        DevSwitch("replay", tr(R.string.dev_replay), tr(R.string.dev_replay_sub), flags.replay) { flags.copy(replay = it) },
        DevSwitch("parallel-tasks", tr(R.string.dev_parallel), tr(R.string.dev_parallel_sub), flags.parallelTasks) { flags.copy(parallelTasks = it) },
        DevSwitch("structured-compaction", tr(R.string.dev_compaction), tr(R.string.dev_compaction_sub), flags.structuredCompaction) { flags.copy(structuredCompaction = it) },
    )
}


object AutonomyCopy {
    val GROUP: String get() = tr(R.string.dev_group)
    val TITLE: String get() = tr(R.string.dev_autonomy)
    val SUBTITLE: String get() = tr(R.string.dev_autonomy_sub)
}


val DEVELOPER_NOTE: String get() = tr(R.string.dev_note)


data class GroundingSetting(val model: String, val verified: Boolean)


object GroundingCopy {
    val GROUP: String get() = tr(R.string.cap_vision)
    val TITLE: String get() = tr(R.string.dev_grounding)

    fun subtitle(model: String) = tr(R.string.dev_grounding_sub, model)
}
