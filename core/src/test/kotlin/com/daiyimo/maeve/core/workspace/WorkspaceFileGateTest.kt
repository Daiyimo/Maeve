package com.daiyimo.maeve.core.workspace

import org.junit.Before
import java.io.File
import java.nio.file.Files

class WorkspaceFileGateTest : WorkspaceFileGateContract() {
    override val sys: FdSys = NioFdSys
    override fun tempDir(): File = Files.createTempDirectory("gate").toFile()

    // 契约里的符号链接/硬链接/权限用例只在 Linux 宿主上成立,Windows 开发机整类跳过
    @Before fun linuxOnly() = PlatformAssumes.assumeLinux()
}
