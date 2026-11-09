package com.daiyimo.maeve.core.workspace

import java.io.Closeable
import java.io.File
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.util.UUID

/**
 * 宿主侧进出沙箱目录(工作区、技能目录、根文件系统)的文件都要过这道门。
 *
 * 沙箱里的命令和 Maeve 是同一个用户,随时能把文件或目录换成符号链接、硬链接或 FIFO,所以「先按路径检查、
 * 再按路径使用」挡不住。这里每一段都相对上一段已经打开的目录句柄打开、不跟随链接;最后的文件只打开一次,
 * 再在句柄上核实(实际位置仍在根目录里、是普通文件、只有一个硬链接),之后的读写都落在同一句柄上。删除也先这样核实。
 *
 * [rel] 是根目录下的相对路径(用 `/` 分隔);`..` 和空字节一律算越界。所有错误都抛 [SandboxError],
 * 消息里不带宿主路径。
 */
class WorkspaceFileGate(private val sys: FdSys) {

    /** 读:必须是普通文件、只有一个硬链接;给了 [under] 时还必须正好在 `<root>/<under>/` 里。 */
    fun openRead(root: File, rel: String, under: String? = null): GatedFile =
        openVerified(root, rel, under, OpenHow.READ, createDirs = false)

    /** 新建(同名已存在就报 file_exists,从不覆盖);缺的目录逐段补上。 */
    fun createNew(root: File, rel: String, under: String? = null): GatedFile =
        openVerified(root, rel, under, OpenHow.CREATE_NEW, createDirs = true)

    /** 写:没有就建;已有的核实之后才截断([append] 时接着末尾写),所以截断不到链进来的别处文件。 */
    fun openWrite(root: File, rel: String, append: Boolean): GatedFile {
        val handle = openVerified(root, rel, null, if (append) OpenHow.APPEND else OpenHow.WRITE, createDirs = true)
        if (append) return handle
        try {
            handle.truncate()
        } catch (e: IOException) {
            handle.close()
            throw e
        }
        return handle
    }

    /** 在 `<root>/<dir>/` 下新建 [name];重名就依次试「name (2).ext」……,最多 [max] 个。 */
    fun createNumbered(root: File, dir: String, name: String, max: Int = 999): Pair<String, GatedFile> {
        for (attempt in 1..max) {
            val candidate = numbered(name, attempt)
            try {
                return candidate to createNew(root, "$dir/$candidate", under = dir)
            } catch (e: SandboxError) {
                if (e.code != FILE_EXISTS) throw e
            }
        }
        throw SandboxError(FILE_EXISTS, "Too many files with this name.", recoverable = true)
    }

    /** 核实是普通文件之后才删。 */
    fun delete(root: File, rel: String, under: String? = null) {
        openVerified(root, rel, under, OpenHow.READ, createDirs = false).use { it.delete() }
    }

    /** 逐段建目录,全程不跟随链接。 */
    fun mkdirs(root: File, rel: String) {
        withAnchored(root, segments(rel), create = true) { _, _ -> }
    }

    /** 列目录里的名字;目录不存在时返回空表。 */
    fun list(root: File, rel: String): List<String> = try {
        withAnchored(root, segments(rel), create = false) { _, dir -> sys.list(dir) }
    } catch (e: SandboxError) {
        if (e.code == FILE_NOT_FOUND) emptyList() else throw e
    }

    /**
     * 整个替换 [rel]:先在同一目录里写好一份新的,再改名盖上去。原来那一项无论是链接、FIFO 还是硬链接,
     * 都只是被换掉,不会被写穿到它指向的东西。
     */
    fun replace(root: File, rel: String, bytes: ByteArray, mode: Int = 420 /* 0644 */) {
        val parts = segments(rel)
        if (parts.isEmpty()) throw outsideWorkspace()
        withAnchored(root, parts.dropLast(1), create = true) { rootReal, parent ->
            val staging = ".maeve-new-" + UUID.randomUUID()
            val temp = openChild(parent, staging, OpenHow.CREATE_NEW)
            try {
                temp.use { fd ->
                    verifyHandle(fd, rootReal, parent, staging)
                    writeFully(fd, bytes)
                    sys.chmod(fd, mode)
                }
                sys.rename(parent, staging, parts.last())
            } catch (e: Exception) {
                runCatching { sys.remove(parent, staging) }
                throw e
            }
        }
    }

    /** 让 [rel] 指向 [target](已经是就不动);原来那一项整个换掉。 */
    fun symlink(root: File, rel: String, target: String) {
        val parts = segments(rel)
        if (parts.isEmpty()) throw outsideWorkspace()
        withAnchored(root, parts.dropLast(1), create = true) { _, parent ->
            if (sys.readlink(parent, parts.last()) == target) return@withAnchored
            val staging = ".maeve-link-" + UUID.randomUUID()
            sys.symlink(parent, staging, target)
            try {
                sys.rename(parent, staging, parts.last())
            } catch (e: Exception) {
                runCatching { sys.remove(parent, staging) }
                throw e
            }
        }
    }

    /**
     * 删掉 `<root>/<rel>` 整棵树:每一层都相对上一层的句柄打开、不跟随链接,链接只删链接本身;
     * 删的过程中被换成链接的目录不会被跟进去。只读目录先改成可写。
     */
    fun deleteTree(root: File, rel: String) {
        val parts = segments(rel)
        if (parts.isEmpty()) throw outsideWorkspace()
        try {
            withAnchored(root, parts.dropLast(1), create = false) { rootReal, parent ->
                eraseEntry(parent, parts.last(), rootReal, 0)
            }
        } catch (e: SandboxError) {
            if (e.code != FILE_NOT_FOUND) throw e
        }
    }

    /**
     * 根目录句柄逐段下到目标目录,再在句柄上把最后一项核一遍。所有中间句柄都在出错时关掉,
     * 返回的 [GatedFile] 自己负责关目标目录句柄。
     */
    private fun openVerified(root: File, rel: String, under: String?, how: OpenHow, createDirs: Boolean): GatedFile {
        val parts = segments(rel)
        if (parts.isEmpty()) throw notARegularFile()
        if (under != null && parts.dropLast(1) != segments(under)) throw outsideWorkspace()

        val rootHandle = openRoot(root)
        val rootReal: String
        val parent: OpenFd
        try {
            rootReal = rootHandle.realPath()
            parent = descend(rootHandle, parts.dropLast(1), createDirs)
        } catch (e: Exception) {
            rootHandle.close()
            throw e
        }
        // parent 就是根目录本身时,根句柄归 GatedFile 关;否则这里先关
        if (parent !== rootHandle) rootHandle.close()
        try {
            if (under != null && parent.realPath() != anchoredUnder(rootReal, under)) throw outsideWorkspace()
            val fd = openChild(parent, parts.last(), how)
            try {
                return GatedFile(sys, parent, fd, parts.last(), verifyHandle(fd, rootReal, parent, parts.last()))
            } catch (e: Exception) {
                fd.close()
                throw e
            }
        } catch (e: Exception) {
            parent.close()
            throw e
        }
    }

    /** 从根句柄逐段走到 [parts] 指的目录;中途的句柄用完就关,返回的那个由调用方关。 */
    private fun descend(rootHandle: OpenFd, parts: List<String>, create: Boolean): OpenFd {
        var current = rootHandle
        for (part in parts) {
            val next = try {
                sys.openDir(current, part, create)
            } catch (e: FdSysException) {
                if (current !== rootHandle) current.close()
                throw translate(e, folderStep = true)
            }
            if (current !== rootHandle) current.close()
            current = next
        }
        return current
    }

    /** 在 [root] 下打开 [parts] 指的目录后执行 [action],句柄由这里负责关。 */
    private fun <R> withAnchored(root: File, parts: List<String>, create: Boolean, action: (String, OpenFd) -> R): R {
        val rootHandle = openRoot(root)
        try {
            val rootReal = rootHandle.realPath()
            val target = descend(rootHandle, parts, create)
            try {
                if (!inside(target.realPath(), rootReal) && target !== rootHandle) throw outsideWorkspace()
                return action(rootReal, target)
            } finally {
                if (target !== rootHandle) target.close()
            }
        } finally {
            rootHandle.close()
        }
    }

    private fun eraseEntry(parent: OpenFd, name: String, rootReal: String, depth: Int) {
        val entry = sys.lstat(parent, name) ?: return
        if (entry.kind == FdStat.Kind.DIR) {
            if (depth > MAX_DEPTH) throw IOException("directory tree too deep to delete")
            val child = try {
                sys.openDir(parent, name, create = false)
            } catch (e: FdSysException) {
                null
            }
            if (child != null) child.use {
                // 删除途中被挪到根目录外面的,不跟着删
                if (!inside(it.realPath(), rootReal)) return
                runCatching { sys.chmod(it, 448 /* 0700 */) }
                for (nested in sys.list(it)) eraseEntry(it, nested, rootReal, depth + 1)
            }
        }
        try {
            sys.remove(parent, name)
        } catch (e: FdSysException) {
            if (e.kind != FdSysException.Kind.NOT_FOUND) throw e
        }
    }

    private fun openRoot(root: File): OpenFd = try {
        sys.openRoot(root)
    } catch (e: FdSysException) {
        throw translate(e, folderStep = true)
    }

    private fun openChild(parent: OpenFd, name: String, how: OpenHow): OpenFd = try {
        sys.openFile(parent, name, how)
    } catch (e: FdSysException) {
        throw translate(e, folderStep = false)
    }

    /** 只信句柄:真实位置就是打开它的那个目录里的这个名字、且在根目录内;普通文件;硬链接数为 1。 */
    private fun verifyHandle(fd: OpenFd, rootReal: String, parent: OpenFd, name: String): FdStat {
        val actual = fd.realPath()
        if (!inside(actual, rootReal)) throw outsideWorkspace()
        if (actual != parent.realPath().trimEnd('/') + "/" + name) throw outsideWorkspace()
        val info = fd.stat()
        if (info.kind != FdStat.Kind.FILE) throw notARegularFile()
        if (info.nlink != 1L) {
            throw SandboxError(
                LINKED_FILE,
                "The file has more than one hard link, so Maeve does not use it; copy it (cp) and use the copy.",
                recoverable = true,
            )
        }
        return info
    }

    private fun translate(e: FdSysException, folderStep: Boolean): SandboxError = when (e.kind) {
        FdSysException.Kind.NOT_FOUND ->
            SandboxError(FILE_NOT_FOUND, "The file does not exist.", recoverable = true)
        FdSysException.Kind.EXISTS ->
            SandboxError(FILE_EXISTS, "A file with this name already exists.", recoverable = true)
        FdSysException.Kind.NOT_DIR ->
            SandboxError(FILE_NOT_FOUND, "A folder on this path is not a folder.", recoverable = true)
        FdSysException.Kind.LINK ->
            if (folderStep) {
                SandboxError(
                    NOT_A_FILE,
                    "A folder on this path is a link; Maeve's file tools do not follow links. Use the real path.",
                    recoverable = true,
                )
            } else {
                notARegularFile()
            }
        FdSysException.Kind.NOT_FILE -> notARegularFile()
    }

    private fun outsideWorkspace() =
        SandboxError(OUTSIDE, "The file is outside the allowed folder.", recoverable = true)

    private fun notARegularFile() =
        SandboxError(NOT_A_FILE, "Not a regular file (a directory, link or special file).", recoverable = true)

    /** `<root>/<under>/` 的期望真实路径。 */
    private fun anchoredUnder(rootReal: String, under: String): String =
        rootReal.trimEnd('/') + "/" + segments(under).joinToString("/")

    private fun writeFully(fd: OpenFd, bytes: ByteArray) {
        val buffer = ByteBuffer.wrap(bytes)
        while (buffer.hasRemaining()) fd.channel().write(buffer)
    }

    companion object {
        const val FILE_NOT_FOUND = "file_not_found"
        const val FILE_EXISTS = "file_exists"
        const val NOT_A_FILE = "not_a_file"
        const val LINKED_FILE = "linked_file"
        const val OUTSIDE = "path_outside_workspace"
        private const val MAX_DEPTH = 256

        /** 把根目录下的相对路径拆成各段;`..` 或空字节都算越界。 */
        fun segments(rel: String): List<String> {
            if ('\u0000' in rel) throw rejectedPath()
            val parts = rel.split('/').filter { it.isNotEmpty() && it != "." }
            for (part in parts) {
                if (part == "..") throw rejectedPath()
            }
            return parts
        }

        /** 「a.csv」→「a (2).csv」;没有扩展名的直接把编号接在后面。 */
        fun numbered(name: String, n: Int): String {
            if (n <= 1) return name
            val dot = name.lastIndexOf('.')
            return if (dot > 0) "${name.substring(0, dot)} ($n)${name.substring(dot)}" else "$name ($n)"
        }

        internal fun inside(path: String, root: String): Boolean =
            path == root || path.startsWith(root.trimEnd('/') + "/")

        private fun rejectedPath() = SandboxError(OUTSIDE, "The path is not allowed.", recoverable = true)
    }
}

/** 经 [WorkspaceFileGate] 核实过的一个文件;读写都落在同一个句柄上,[stat] 是核实那一刻的。 */
class GatedFile internal constructor(
    private val sys: FdSys,
    private val dir: OpenFd,
    private val fd: OpenFd,
    val name: String,
    val stat: FdStat,
) : Closeable {
    val size: Long get() = stat.size

    fun channel() = fd.channel()

    /** 从当前位置读;关掉流不会关句柄。 */
    fun input(): InputStream = UnclosableInput(Channels.newInputStream(fd.channel()))

    /** 往当前位置写;关掉流只是冲刷,不关句柄。 */
    fun output(): OutputStream = FlushOnlyOutput(Channels.newOutputStream(fd.channel()))

    /** 从 [offset] 起读至多 [len] 字节,读到文件末尾为止。 */
    fun read(offset: Long, len: Int): ByteArray {
        val buffer = ByteBuffer.allocate(len)
        var position = offset
        while (buffer.hasRemaining()) {
            val count = fd.channel().read(buffer, position)
            if (count < 0) break
            position += count
        }
        return buffer.array().copyOf(buffer.position())
    }

    fun write(bytes: ByteArray) {
        val buffer = ByteBuffer.wrap(bytes)
        while (buffer.hasRemaining()) fd.channel().write(buffer)
    }

    fun currentSize(): Long = fd.channel().size()

    internal fun truncate() = fd.truncate(0)

    /** 删除这个文件:目录里那一项仍是同一个文件(设备号 + inode)才删,换成了别的就不动。 */
    fun delete() {
        val current = sys.lstat(dir, name) ?: return
        if (current.dev != stat.dev || current.ino != stat.ino || current.kind != FdStat.Kind.FILE) return
        try {
            sys.remove(dir, name)
        } catch (e: FdSysException) {
            if (e.kind != FdSysException.Kind.NOT_FOUND) throw e
        }
    }

    override fun close() {
        runCatching { fd.close() }
        runCatching { dir.close() }
    }

    /** 关流不关底层句柄:句柄的寿命归 [GatedFile]。 */
    private class UnclosableInput(source: InputStream) : FilterInputStream(source) {
        override fun close() = Unit
    }

    /** 关流只冲刷,不关底层句柄。 */
    private class FlushOnlyOutput(sink: OutputStream) : FilterOutputStream(sink) {
        override fun write(bytes: ByteArray, offset: Int, length: Int) = out.write(bytes, offset, length)
        override fun close() = flush()
    }
}
