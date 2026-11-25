package com.daiyimo.maeve.task

import com.daiyimo.maeve.core.run.RunPhase

internal fun encodeRunPhase(phase: RunPhase): String = when (phase) {
    RunPhase.Queued -> "queued"
    RunPhase.Preparing -> "preparing"
    RunPhase.Running -> "running"
    is RunPhase.WaitingUser -> "waiting_user"
    is RunPhase.Parked -> "parked"
    is RunPhase.Paused -> "paused"
    RunPhase.Finishing -> "finishing"
    RunPhase.Aftercare -> "aftercare"
    RunPhase.Ended -> "ended"
    RunPhase.Interrupted -> "interrupted"
}

internal fun decodeRunPhase(code: String): RunPhase = when (code) {
    "queued" -> RunPhase.Queued
    "preparing" -> RunPhase.Preparing
    "running" -> RunPhase.Running
    "waiting_user" -> RunPhase.WaitingUser()
    "parked" -> RunPhase.Parked("")
    "paused" -> RunPhase.Paused("")
    "finishing" -> RunPhase.Finishing
    "aftercare" -> RunPhase.Aftercare
    "ended" -> RunPhase.Ended
    else -> RunPhase.Interrupted
}
