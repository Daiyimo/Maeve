package com.daiyimo.maeve.wechat

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException

internal fun writeWechatMailbox(file: File, text: String) {
    check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs()) { "无法创建中转目录" }
    val temp = File(file.parentFile, file.name + ".tmp")
    FileOutputStream(temp).use { it.write(text.toByteArray(Charsets.UTF_8)); it.fd.sync() }
    try {
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
