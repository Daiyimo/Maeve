package com.daiyimo.maeve.cap.phone.a11y

import android.os.Bundle
import com.daiyimo.maeve.cap.phone.shell.MainScreenWatch

internal fun parseArrivals(b: Bundle): List<MainScreenSpill.Arrival> {
    val tasks = b.getIntArray(MainScreenWatch.KEY_TASK) ?: return emptyList()
    val packages = b.getStringArray(MainScreenWatch.KEY_PACKAGE)
    val users = b.getIntArray(MainScreenWatch.KEY_USER)
    val at = b.getLongArray(MainScreenWatch.KEY_AT)
    val from = b.getIntArray(MainScreenWatch.KEY_FROM)
    val same = b.getBooleanArray(MainScreenWatch.KEY_SAME_TASK)
    val standard = b.getBooleanArray(MainScreenWatch.KEY_STANDARD)
    val activity = b.getStringArray(MainScreenWatch.KEY_ACTIVITY)
    val now = b.getIntArray(MainScreenWatch.KEY_NOW)
    if (packages == null || users == null || at == null || from == null || same == null || standard == null || now == null) return emptyList()
    return tasks.indices.map { i ->
        MainScreenSpill.Arrival(tasks[i], packages[i], users[i], at[i], from[i], same[i], standard[i], now[i], activity?.getOrNull(i))
    }
}
