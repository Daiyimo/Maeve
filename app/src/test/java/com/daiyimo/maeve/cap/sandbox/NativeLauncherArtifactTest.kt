package com.daiyimo.maeve.cap.sandbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** The packaged launcher must be executable, not merely present in jniLibs. */
class NativeLauncherArtifactTest {
    @Test fun launcherEntryPointsIntoExecutableLoadSegment() {
        val bytes = File("src/main/jniLibs/arm64-v8a/libmaeverun.so").readBytes()
        assertTrue("not an ELF file", bytes.size >= 64 && bytes.sliceArray(0..3).contentEquals(byteArrayOf(0x7f.toByte(), 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())))
        assertEquals("ELF64", 2, bytes[4].toInt())
        assertEquals("little endian", 1, bytes[5].toInt())

        val elf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val entry = elf.getLong(24)
        assertTrue("launcher entry point is zero; the kernel would jump to address 0", entry > 0)

        val phoff = elf.getLong(32)
        val phentsize = elf.getShort(54).toInt() and 0xffff
        val phnum = elf.getShort(56).toInt() and 0xffff
        assertTrue("invalid program header table", phoff >= 0 && phentsize >= 56 && phnum > 0)
        val executable = (0 until phnum).any { i ->
            val at = (phoff + i.toLong() * phentsize).toInt()
            at >= 0 && at + 56 <= bytes.size &&
                elf.getInt(at) == 1 && // PT_LOAD
                (elf.getInt(at + 4) and 1) != 0 && // PF_X
                entry >= elf.getLong(at + 16) &&
                entry < elf.getLong(at + 16) + elf.getLong(at + 32)
        }
        assertTrue("entry point 0x${entry.toString(16)} is outside every executable PT_LOAD segment", executable)
    }
}
