package com.daiyimo.maeve.core.workspace

import org.junit.Assume.assumeTrue

/**
 * 平台门禁:沙箱/文件桥的测试依赖 POSIX 权限、符号链接、/bin/sh、宿主 tar 与 mkfifo,
 * 这些只在 Linux 宿主上成立。Windows 开发机上跑会假红一片,掩盖真实回归,所以这些测试
 * 只在 Linux 宿主(含 CI 的 ubuntu-24.04)上执行,其余平台整类或按用例跳过。
 * 真机(androidTest)跑在 Android 上,Android 是 Linux,不受影响。
 */
object PlatformAssumes {
    val isLinux: Boolean get() = System.getProperty("os.name").lowercase().contains("linux")

    /** 在 @Before 里调用 = 整类跳过;在 @Test 第一行调用 = 单用例跳过。 */
    fun assumeLinux(reason: String = "requires a Linux host (POSIX permissions / symlinks / /bin/sh / tar)") =
        assumeTrue(reason, isLinux)
}
