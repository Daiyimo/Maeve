package com.daiyimo.maeve.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.daiyimo.maeve.R
import com.daiyimo.maeve.ui.theme.AccentPreset

val AccentPreset.labelRes: Int
    @StringRes get() = when (this) {
        AccentPreset.Reverie -> R.string.accent_reverie
        AccentPreset.Dolores -> R.string.accent_dolores
        AccentPreset.Maze -> R.string.accent_maze
        AccentPreset.Pearl -> R.string.accent_pearl
        AccentPreset.Sublime -> R.string.accent_sublime
        AccentPreset.Graphite -> R.string.accent_graphite
    }
