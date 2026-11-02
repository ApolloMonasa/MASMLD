package dev.masmld.riscv

import dev.masmld.riscv.highlighting.RiscVClassifier
import dev.masmld.riscv.highlighting.RiscVRole as R
import org.junit.Assert.*
import org.junit.Test

class RiscVClassifierTest {
    private fun roles(source: String) = RiscVClassifier.classify(source).map { source.substring(it.start, it.end) to it.role }
    @Test fun `instruction names used as labels are not opcodes`() {
        assertEquals(listOf("add" to R.LABEL, "addi" to R.INSTRUCTION, "a0" to R.REGISTER, "x0" to R.REGISTER), roles("add: addi a0, x0, 1"))
        assertEquals(listOf("a0" to R.LABEL, "1" to R.LABEL, "ret" to R.PSEUDO_INSTRUCTION), roles("a0: 1: ret"))
        assertEquals(listOf("add" to R.LABEL), roles("add = 42"))
    }
    @Test fun `directives and jump targets do not color symbols as registers`() {
        assertEquals(listOf(".globl" to R.DIRECTIVE), roles(".globl a0"))
        assertEquals(listOf("j" to R.PSEUDO_INSTRUCTION), roles("j a0"))
        assertEquals(listOf("call" to R.PSEUDO_INSTRUCTION), roles("call ra"))
        assertEquals(listOf("jal" to R.INSTRUCTION), roles("jal a0"))
        assertEquals(listOf("beq" to R.INSTRUCTION, "a0" to R.REGISTER, "a1" to R.REGISTER), roles("beq a0,a1,a2"))
        assertEquals(listOf("addi" to R.INSTRUCTION, "a0" to R.REGISTER, "a1" to R.REGISTER), roles("addi a0,a1,a2"))
    }
    @Test fun `registers recognized only in correct operand positions`() {
        assertEquals(listOf("add" to R.INSTRUCTION, "x31" to R.REGISTER, "fp" to R.REGISTER, "zero" to R.REGISTER), roles("add x31,fp,zero"))
        assertEquals(listOf("add" to R.INSTRUCTION), roles("add x32,a8,t7"))
        assertEquals(listOf("custom_macro" to R.LABEL), roles("custom_macro: unknown_op a0,a1"))
    }
    @Test fun `memory bases and relocations preserve symbol roles`() {
        assertEquals(listOf("lw" to R.INSTRUCTION, "a0" to R.REGISTER, "sp" to R.REGISTER), roles("lw a0,%lo(a1)(sp)"))
        assertEquals(listOf("lw" to R.INSTRUCTION, "a0" to R.REGISTER), roles("lw a0,%lo(sp)"))
        assertEquals(listOf("sd" to R.INSTRUCTION, "ra" to R.REGISTER, "sp" to R.REGISTER), roles("sd ra,8(sp)"))
        assertEquals(listOf("la" to R.PSEUDO_INSTRUCTION, "a0" to R.REGISTER), roles("la a0,sp"))
    }
    @Test fun `comments strings and preprocessing are opaque`() {
        assertTrue(roles("# add a0,a1,a2\n/* ret */\n.asciz \"add a0\"").all { it.first == ".asciz" })
        assertTrue(roles("#define foo add a0,a1,a2").isEmpty())
        assertEquals(listOf("addi" to R.INSTRUCTION, "a0" to R.REGISTER, "a1" to R.REGISTER), roles("addi a0,/* a2, ret */a1,1"))
    }
    @Test fun `semicolons and incomplete operands recover at statement boundary`() {
        assertEquals(listOf("addi" to R.INSTRUCTION, "a0" to R.REGISTER, "ret" to R.PSEUDO_INSTRUCTION), roles("addi a0,; ret"))
        assertEquals(listOf("lw" to R.INSTRUCTION, "a0" to R.REGISTER, "ret" to R.PSEUDO_INSTRUCTION), roles("lw a0,(\nret"))
    }
    @Test fun `jalr supports register and memory spelling`() {
        assertEquals(listOf("jalr" to R.INSTRUCTION, "ra" to R.REGISTER, "sp" to R.REGISTER), roles("jalr ra,8(sp)"))
        assertEquals(listOf("jalr" to R.INSTRUCTION, "ra" to R.REGISTER, "sp" to R.REGISTER), roles("jalr ra,sp,0"))
        assertEquals(listOf("jalr" to R.INSTRUCTION, "ra" to R.REGISTER), roles("jalr ra"))
    }
}
