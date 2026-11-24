package com.daiyimo.maeve.core.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationGuardsProgressTest {

    @Test fun unrelatedNoEffectActionsDoNotCountAsBlindEvidence() {
        val g = OperationGuards()
        repeat(5) { g.effect("no_effect") }
        val ordinary = g.effect("no_effect", treeBlind = true).single()
        assertEquals(Severity.Warn, ordinary.severity)
        assertFalse(ordinary.evidence.containsKey("treeBlind"))
        assertNull(g.stop)
    }

    @Test fun newScreenBreaksOldCycle() {
        val g = OperationGuards()
        g.frame("s0", 1L)
        val cycle = listOf(2L, 1L, 2L, 1L, 2L).mapIndexed { i, h -> g.noteAction(); g.frame("c$i", h) }.flatten()
        assertEquals(listOf("state_cycle"), cycle.map { it.code })
        g.noteAction()
        val changed = g.frame("new", 3L)
        assertTrue(changed.none { it.code == "state_cycle" })
        g.noteAction()
        assertTrue(g.frame("back", 2L).none { it.code == "state_cycle" })
    }
}
