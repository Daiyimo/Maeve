package com.daiyimo.maeve.intercept

import java.io.Closeable
import java.io.File
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/** 有界、轮转扫描。延期 claimed 不能永远占住目录的前一百项，阻塞后续请求。 */
internal class MailboxScanner : Closeable {
    private var root: Path? = null
    private var stream: DirectoryStream<Path>? = null
    private var iterator: Iterator<Path>? = null

    fun batch(directory: File, inspectedLimit: Int = 100): List<File> {
        require(inspectedLimit > 0)
        val path = directory.toPath()
        if (root != path) close()
        if (stream == null) {
            root = path
            stream = Files.newDirectoryStream(path)
            iterator = stream!!.iterator()
        }
        val rows = mutableListOf<File>()
        try {
            repeat(inspectedLimit) {
                if (!iterator!!.hasNext()) { close(); return rows }
                val file = iterator!!.next()
                val name = file.fileName.toString()
                if ((name.endsWith(".json") || name.endsWith(".claimed")) && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) rows += file.toFile()
            }
        } catch (failure: Exception) { close(); throw failure }
        return rows
    }

    override fun close() {
        try { stream?.close() } finally { stream = null; iterator = null; root = null }
    }
}
