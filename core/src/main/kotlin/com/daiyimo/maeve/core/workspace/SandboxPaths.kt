package com.daiyimo.maeve.core.workspace

import java.io.File
import java.nio.file.Files

/**
 * 模型眼里只有沙箱内的路径。这里负责把路径拉直,并判断它有没有越出允许的根。
 *
 * 允许两个根:可写的工作区 [WORKSPACE],只读的技能目录 [SKILLS]。
 */
object SandboxPaths {
    const val WORKSPACE = "/workspace"
    const val SKILLS = "/opt/maeve/skills"

    /**
     * 把 [path] 拉直成绝对沙箱路径:相对路径接在 [cwd] 后面,`.` 与空段丢掉,`..` 回退一段。
     * 回退到根之外、含空字节,或结果不在 [WORKSPACE]/[SKILLS] 之内时返回 null。
     */
    fun normalize(path: String, cwd: String = WORKSPACE): String? {
        if (path.isEmpty() || path.contains('\u0000')) return null
        val absolute = if (path[0] == '/') path else "$cwd/$path"
        val stack = ArrayDeque<String>()
        for (segment in absolute.split('/')) {
            when (segment) {
                "", "." -> continue
                ".." -> stack.removeLastOrNull() ?: return null
                else -> stack.addLast(segment)
            }
        }
        val collapsed = "/" + stack.joinToString("/")
        return collapsed.takeIf { fitsUnder(it, WORKSPACE) || fitsUnder(it, SKILLS) }
    }

    /** 只有工作区下的路径可写;技能目录是只读的。 */
    fun isWritable(normalized: String): Boolean = fitsUnder(normalized, WORKSPACE)

    /**
     * 把沙箱相对路径 [sandboxRel] 落到宿主目录 [root] 下。
     *
     * 逐段走:遇到符号链接就解析成真实路径,目标不存在(看不出来写下去会落到哪里)或解析后
     * 出了 [root] 都返回 null;最后再确认结果仍在 [root] 内。`..` 直接拒绝。
     */
    fun resolveHost(root: File, sandboxRel: String): File? {
        val anchor = root.canonicalFile
        var current = anchor
        for (segment in sandboxRel.split('/')) {
            when (segment) {
                "", "." -> continue
                ".." -> return null
            }
            current = File(current, segment)
            val asPath = current.toPath()
            if (!Files.isSymbolicLink(asPath)) continue
            if (!Files.exists(asPath)) return null
            current = current.canonicalFile
            if (!staysInside(current, anchor)) return null
        }
        return current.canonicalFile.takeIf { staysInside(it, anchor) }
    }

    private fun fitsUnder(path: String, root: String): Boolean =
        path == root || path.startsWith("$root/")

    private fun staysInside(candidate: File, anchor: File): Boolean =
        candidate == anchor || candidate.path.startsWith(anchor.path + File.separator)
}
