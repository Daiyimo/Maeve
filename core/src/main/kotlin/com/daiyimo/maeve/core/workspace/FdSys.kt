package com.daiyimo.maeve.core.workspace

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermissions

/** 一次 fstat/lstat 拿到的文件类型与身份。 */
data class FdStat(val kind: Kind, val nlink: Long, val size: Long, val dev: Long, val ino: Long) {
    enum class Kind { FILE, DIR, LINK, OTHER }
}

/** 打开一次的句柄。到底是哪个文件只认句柄本身(安卓上读 `/proc/self/fd/N`),不再按路径重走一遍。 */
interface OpenFd : Closeable {
    fun realPath(): String
    fun stat(): FdStat

    /** 文件句柄上的通道(读还是写取决于打开方式);目录没有。 */
    fun channel(): FileChannel
    fun truncate(size: Long)
}

enum class OpenHow { READ, CREATE_NEW, WRITE, APPEND }

/** [FdSys] 报上来的系统调用失败;[WorkspaceFileGate] 负责把它换成工具层的错误码。 */
class FdSysException(val kind: Kind, cause: Throwable? = null) : IOException(kind.name, cause) {
    enum class Kind { NOT_FOUND, EXISTS, LINK, NOT_DIR, NOT_FILE }
}

/**
 * 系统调用这一层。带 `dir` 的调用都相对那个已经打开的目录执行,`name` 是单段名字,最后一段不跟随链接。
 *
 * 安卓实现在 app 模块(open 带 O_NOFOLLOW,下一段经 `/proc/self/fd/N` 走);[NioFdSys] 是按路径的 JVM 近似,
 * 只供 JVM 单测使用。
 */
interface FdSys {
    /** 打开根目录本身(调用方给的、沙箱碰不到的路径);最后一段是链接时报 LINK。 */
    fun openRoot(path: File): OpenFd

    fun openDir(dir: OpenFd, name: String, create: Boolean): OpenFd
    fun openFile(dir: OpenFd, name: String, how: OpenHow): OpenFd

    /** 不跟随链接;不存在返回 null。 */
    fun lstat(dir: OpenFd, name: String): FdStat?
    fun readlink(dir: OpenFd, name: String): String?
    fun list(dir: OpenFd): List<String>

    /** 删掉这一项本身(普通文件、链接或空目录),不跟随链接。 */
    fun remove(dir: OpenFd, name: String)

    /** 同一目录内改名;目标已存在(文件或链接)时整个替换。 */
    fun rename(dir: OpenFd, from: String, to: String)
    fun symlink(dir: OpenFd, name: String, target: String)
    fun chmod(fd: OpenFd, mode: Int)
}

/**
 * JVM 上的近似实现:每一步都按路径重新解析,检查与真正使用之间有空档。只用于 JVM 单测
 * (macOS 上没有 `/proc/self/fd`);真机一律走 app 里的安卓实现。
 */
object NioFdSys : FdSys {
    private class PathFd(val path: Path, private val fileChannel: FileChannel?) : OpenFd {
        private val absolute: String = path.toRealPath().toString()

        override fun realPath(): String = absolute
        override fun stat(): FdStat = unixStat(path) ?: throw FdSysException(FdSysException.Kind.NOT_FOUND)
        override fun channel(): FileChannel = fileChannel ?: throw FdSysException(FdSysException.Kind.NOT_FILE)
        override fun truncate(size: Long) { channel().truncate(size) }
        override fun close() { fileChannel?.close() }
    }

    private fun pathFd(handle: OpenFd): PathFd = handle as PathFd

    private fun basicAttrs(target: Path): BasicFileAttributes? = try {
        Files.readAttributes(target, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
    } catch (e: IOException) {
        null
    }

    private fun unixStat(target: Path): FdStat? {
        val attrs = try {
            Files.readAttributes(target, "unix:mode,nlink,size,dev,ino", LinkOption.NOFOLLOW_LINKS)
        } catch (e: IOException) {
            return null
        }
        val kind = when ((attrs["mode"] as Int) and 0xF000) {
            0x8000 -> FdStat.Kind.FILE
            0x4000 -> FdStat.Kind.DIR
            0xA000 -> FdStat.Kind.LINK
            else -> FdStat.Kind.OTHER
        }
        return FdStat(
            kind,
            (attrs["nlink"] as Number).toLong(),
            (attrs["size"] as Number).toLong(),
            (attrs["dev"] as Number).toLong(),
            (attrs["ino"] as Number).toLong(),
        )
    }

    private fun directoryAt(target: Path): PathFd {
        val attrs = basicAttrs(target) ?: throw FdSysException(FdSysException.Kind.NOT_FOUND)
        if (attrs.isSymbolicLink) throw FdSysException(FdSysException.Kind.LINK)
        if (!attrs.isDirectory) throw FdSysException(FdSysException.Kind.NOT_DIR)
        return PathFd(target, null)
    }

    override fun openRoot(path: File): OpenFd = directoryAt(path.toPath().toAbsolutePath())

    override fun openDir(dir: OpenFd, name: String, create: Boolean): OpenFd {
        val child = pathFd(dir).path.resolve(name)
        if (create && basicAttrs(child) == null) runCatching { Files.createDirectory(child) }
        return directoryAt(child)
    }

    override fun openFile(dir: OpenFd, name: String, how: OpenHow): OpenFd {
        val target = pathFd(dir).path.resolve(name)
        val existing = basicAttrs(target)
        if (existing?.isSymbolicLink == true) {
            throw FdSysException(if (how == OpenHow.CREATE_NEW) FdSysException.Kind.EXISTS else FdSysException.Kind.LINK)
        }
        // NIO 带不了 O_NONBLOCK:FIFO 这类先按类型拒掉,免得读写时卡住
        if (existing != null && !existing.isRegularFile) {
            throw FdSysException(if (how == OpenHow.CREATE_NEW) FdSysException.Kind.EXISTS else FdSysException.Kind.NOT_FILE)
        }
        val channel = try {
            FileChannel.open(target, openOptions(how) + LinkOption.NOFOLLOW_LINKS)
        } catch (e: FileAlreadyExistsException) {
            throw FdSysException(FdSysException.Kind.EXISTS, e)
        } catch (e: NoSuchFileException) {
            throw FdSysException(FdSysException.Kind.NOT_FOUND, e)
        }
        return PathFd(target, channel)
    }

    private fun openOptions(how: OpenHow): Set<StandardOpenOption> = when (how) {
        OpenHow.READ -> setOf(StandardOpenOption.READ)
        OpenHow.CREATE_NEW -> setOf(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)
        OpenHow.WRITE -> setOf(StandardOpenOption.WRITE, StandardOpenOption.CREATE)
        OpenHow.APPEND -> setOf(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }

    override fun lstat(dir: OpenFd, name: String): FdStat? = unixStat(pathFd(dir).path.resolve(name))

    override fun readlink(dir: OpenFd, name: String): String? =
        runCatching { Files.readSymbolicLink(pathFd(dir).path.resolve(name)).toString() }.getOrNull()

    override fun list(dir: OpenFd): List<String> =
        Files.list(pathFd(dir).path).use { entries -> entries.map { it.fileName.toString() }.toList() }

    override fun remove(dir: OpenFd, name: String) {
        try {
            Files.delete(pathFd(dir).path.resolve(name))
        } catch (e: NoSuchFileException) {
            throw FdSysException(FdSysException.Kind.NOT_FOUND, e)
        }
    }

    override fun rename(dir: OpenFd, from: String, to: String) {
        val parent = pathFd(dir).path
        Files.move(
            parent.resolve(from),
            parent.resolve(to),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    override fun symlink(dir: OpenFd, name: String, target: String) {
        Files.createSymbolicLink(pathFd(dir).path.resolve(name), Paths.get(target))
    }

    override fun chmod(fd: OpenFd, mode: Int) {
        Files.setPosixFilePermissions(pathFd(fd).path, PosixFilePermissions.fromString(permissionText(mode)))
    }

    /** 把低 9 位的权限位转成 `rwxrwxrwx` 形式。 */
    private fun permissionText(mode: Int): String = buildString(9) {
        for (bit in 0..8) {
            val mask = 1 shl (8 - bit)
            append(if ((mode and mask) != 0) "rwx"[bit % 3] else '-')
        }
    }
}
