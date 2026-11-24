package com.daiyimo.maeve.core.action

import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** An action has its own clock. Vision time is included in the phase that called it. */
internal class ActionTiming(private val now: () -> Long) : AbstractCoroutineContextElement(Key) {
    internal companion object Key : CoroutineContext.Key<ActionTiming>

    private val started = now()
    private val times = linkedMapOf<String, Long>()

    fun elapsed(): Long = (now() - started).coerceAtLeast(0)
    fun phases(): Map<String, Long> = times.toMap()

    suspend fun <T> measure(phase: String, work: suspend () -> T): T {
        val start = now()
        try {
            return work()
        } finally {
            times[phase] = (times[phase] ?: 0) + (now() - start).coerceAtLeast(0)
        }
    }
}

suspend fun <T> timedActionPhase(phase: String, work: suspend () -> T): T {
    val timing = currentCoroutineContext()[ActionTiming] ?: return work()
    return timing.measure(phase, work)
}
