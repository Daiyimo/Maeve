package com.daiyimo.maeve.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.Saver
import com.daiyimo.maeve.core.shell.PairingCode
import com.daiyimo.maeve.core.shell.SetupStep
import androidx.compose.runtime.setValue

private val StepSetSaver = Saver<Set<SetupStep>, String>(
    save = { set -> set.joinToString(",") { it.name } },
    restore = { s -> s.split(',').mapNotNull { n -> SetupStep.entries.firstOrNull { it.name == n } }.toSet() },
)


internal object ConnectPortInput {
    fun parse(text: String): Int? {
        val tail = text.substringAfterLast(':').substringAfterLast('：')
        val digits = tail.filterNot { it.isWhitespace() }.map { c -> if (c in '０'..'９') '0' + (c - '０') else c }.joinToString("")
        return PairingCode.port(digits)
    }
}
