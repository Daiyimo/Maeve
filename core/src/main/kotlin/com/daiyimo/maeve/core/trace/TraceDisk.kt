package com.daiyimo.maeve.core.trace

import java.io.File
import java.io.IOException

internal class TraceDiskFull(val block: String) : IOException("Trace storage limit")

/**
 * Tracks how many bytes all local traces occupy.
 *
 * Opening a run reserves room for its root checkpoint up front, so the final write that explains why a
 * capture stopped still succeeds after the quota has been reached. Rotating an old run is only allowed
 * while it is neither recording nor being read by an export.
 */
internal class TraceDisk(private val directory: File, private var limit: Long) {
    private val recordingDirs = mutableSetOf<File>()
    private val readerCounts = mutableMapOf<File, Int>()
    private var available = 0L

    @Synchronized fun open(dir: File): Boolean {
        if (dir.exists()) return false
        if (available < ROOT_RESERVE) reclaim(ROOT_RESERVE)
        if (available < ROOT_RESERVE) return false
        directory.mkdirs()
        if (!dir.mkdir()) return false
        recordingDirs += dir
        available -= ROOT_RESERVE
        return true
    }

    @Synchronized fun close(dir: File) {
        if (recordingDirs.remove(dir)) {
            available += reserveHeldFor(dir)
            // An initialization that failed before the root existed leaves only empty directories.
            if (!File(dir, ROOT_NAME).isFile) dir.deleteRecursively()
        }
        if (available < 0) reclaim(0)
    }

    @Synchronized fun setLimit(bytes: Long) {
        require(bytes > 0)
        limit = bytes
        reclaim(0)
    }

    // An export pins the run until its last block has been read; the caller's network I/O happens after release.
    fun <T> read(dir: File, block: () -> T): T {
        synchronized(this) {
            if (!dir.isDirectory) throw IOException("Trace no longer available")
            readerCounts[dir] = (readerCounts[dir] ?: 0) + 1
        }
        try {
            return block()
        } finally {
            synchronized(this) {
                val left = readerCounts.getValue(dir) - 1
                if (left == 0) readerCounts.remove(dir) else readerCounts[dir] = left
                if (available < 0) reclaim(0)
            }
        }
    }

    @Synchronized fun write(dir: File, file: File, data: ByteArray, block: String) {
        val floor = if (file == File(dir, ROOT_NAME)) ROOT_RESERVE else 0L
        val growth = maxOf(floor, data.size.toLong()) - maxOf(floor, file.length())
        if (growth > 0 && growth > available) {
            reclaim(growth)
            if (growth > available) throw TraceDiskFull(block)
        }
        val staged = File(file.parentFile, file.name + ".tmp")
        try {
            staged.writeBytes(data)
            if (!staged.renameTo(file)) throw IOException("Trace write failed")
            available -= growth
        } finally {
            staged.delete()
        }
    }

    private fun reserveHeldFor(dir: File) = (ROOT_RESERVE - File(dir, ROOT_NAME).length()).coerceAtLeast(0L)

    // Rescan after startup, a quota change, or pressure; ordinary writes only account for their own growth.
    private fun reclaim(needed: Long) {
        val runs = directory.listFiles().orEmpty().filter { it.isDirectory && RUN_ID.matches(it.name) }
        val usage = runs.associateWith { run -> run.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
        available = limit - usage.values.sum() - recordingDirs.sumOf(::reserveHeldFor)
        if (available >= needed) return
        for (run in runs.filter { it !in recordingDirs && it !in readerCounts }.sortedBy { it.lastModified() }) {
            if (run.deleteRecursively()) available += usage.getValue(run)
            if (available >= needed) break
        }
    }

    companion object {
        val RUN_ID = Regex("[a-zA-Z0-9_-]{1,80}")
        private const val ROOT_RESERVE = 1024L
        private const val ROOT_NAME = "root.pb"
    }
}
