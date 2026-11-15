package com.daiyimo.maeve.core.io

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** 同目录原子替换；失败时保留旧文件，不采用先删除目标的回退。调用方负责串行化。 */
object AtomicFiles {
    fun write(file: File, bytes: ByteArray) {
        val parent = requireNotNull(file.absoluteFile.parentFile)
        check(parent.isDirectory || parent.mkdirs()) { "storage_unavailable" }
        val staged = File(parent, file.name + ".tmp")
        try {
            FileOutputStream(staged).use { it.write(bytes); it.fd.sync() }
            Files.move(staged.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            if (staged.isFile) staged.delete()
        }
    }
}
