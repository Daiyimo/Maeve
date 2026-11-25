package com.daiyimo.maeve.core.action

/** A direct user instruction to open an app. Uses the same checks as a recorded substep. */
internal data object ExplicitLaunchOrigin : Origin {
    override val step = 1
    override val policy = Origin.Batch(step).policy.copy(source = "explicit_launch", recordsRefusal = true)
}
