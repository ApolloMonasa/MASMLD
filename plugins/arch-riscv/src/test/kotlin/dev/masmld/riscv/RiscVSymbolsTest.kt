package dev.masmld.riscv

import dev.masmld.riscv.symbols.RiscVSymbols
import org.junit.Assert.*
import org.junit.Test

class RiscVSymbolsTest {
    private fun resolve(source: String, name: String, occurrence: Int = 0) = RiscVSymbols.analyze(source).let { symbols ->
        val reference = symbols.references.filter { it.name == name }[occurrence]
        symbols.resolve(reference)
    }
    @Test fun `entry asm address reference resolves to label at EOF`() {
        val source = javaClass.getResource("/fixtures/entry.asm")!!.readText()
        val targets = resolve(source, "boot_stack_top")
        assertEquals(1, targets.size)
        assertEquals(source.lastIndexOf("boot_stack_top"), targets.single().start)
        assertEquals("boot_stack_top", source.substring(targets.single().start, targets.single().end))
        assertTrue(resolve(source, "rust_main").isEmpty()) // External target is handled by the Rust bridge.
    }
    @Test fun `numeric forward and backward labels choose nearest definition`() {
        val source = "1: nop\nbeq a0,zero,1f\nj 1b\n1: ret\nj 1b"
        val definitions = RiscVSymbols.analyze(source).definitions.filter { it.name == "1" }
        assertEquals(2, definitions.size)
        assertEquals(definitions[1], resolve(source, "1f").single())
        assertEquals(definitions[0], resolve(source, "1b", 0).single())
        assertEquals(definitions[1], resolve(source, "1b", 1).single())
        assertTrue(resolve("j 2f\n1: ret", "2f").isEmpty())
    }
    @Test fun `labels named like opcodes or registers resolve only in symbol positions`() {
        val source = "add: nop\nsp: ret\nj add\nj sp\naddi sp,sp,0\n.globl sp"
        val symbols = RiscVSymbols.analyze(source)
        assertEquals(listOf("add", "sp", "sp"), symbols.references.map { it.name })
        assertEquals("add", resolve(source, "add").single().name)
        assertEquals("sp", resolve(source, "sp").single().name)
    }
    @Test fun `relocation arguments are references but modifiers are not`() {
        val source = ".Ldata: .word 0\nla a0,.Ldata\nlw a0,%lo(.Ldata)(sp)\ncall rust_main@plt"
        val symbols = RiscVSymbols.analyze(source)
        assertEquals(listOf(".Ldata", ".Ldata", "rust_main"), symbols.references.map { it.name })
        assertEquals(".Ldata", symbols.resolve(symbols.references[1]).single().name)
    }
    @Test fun `constants and aliases resolve while definitions are not references`() {
        val source = "BASE = 4096\n.equ STACK_SIZE, BASE * 16\n.set TOP, STACK_SIZE\nli a0,TOP\n.space STACK_SIZE"
        val symbols = RiscVSymbols.analyze(source)
        assertEquals(listOf("BASE", "STACK_SIZE", "TOP"), symbols.definitions.map { it.name })
        assertEquals(listOf("BASE", "STACK_SIZE", "TOP", "STACK_SIZE"), symbols.references.map { it.name })
        assertTrue(symbols.references.all { symbols.resolve(it).size == 1 })
    }
    @Test fun `metadata section names comments and strings do not become references`() {
        val source = ".section .text\n.option norelax\n.type start,@function\nstart: ret\n# j imaginary\n.asciz \"start\"\n/* la a0,hidden */"
        val symbols = RiscVSymbols.analyze(source)
        assertEquals(listOf("start"), symbols.references.map { it.name })
        assertEquals(listOf("start"), symbols.definitions.map { it.name })
    }
    @Test fun `macro definitions and their local scopes are not treated as global labels`() {
        val source = ".macro example arg\ninside: j inside\n.endm\nouter: ret\nj outer"
        val symbols = RiscVSymbols.analyze(source)
        assertEquals(listOf("outer", "example"), symbols.definitions.map { it.name })
        assertEquals(listOf("outer"), symbols.references.map { it.name })
    }
    @Test fun `only declared global or weak definitions are exported across assembly files`() {
        val source = ".globl foo,.Linternal\n.weak weak_label\n.global local_label\n.local local_label\nfoo: ret\n.Linternal: ret\nweak_label: ret\nlocal_label: ret\nprivate_label: ret\n1: ret"
        val symbols = RiscVSymbols.analyze(source)
        assertEquals(listOf("foo", "weak_label"), symbols.definitions.filter { it.exported }.map { it.name })
    }
    @Test fun `duplicate ordinary definitions remain explicit navigation choices`() {
        val source = "target: ret\ntarget: nop\nj target"
        assertEquals(2, resolve(source, "target").size)
    }
    @Test fun `multiple labels expressions and unfinished instructions keep exact ranges`() {
        val source = "first: second: la a0,first+4\nlw a0,%lo(second)(\nlast: ret"
        val symbols = RiscVSymbols.analyze(source)
        assertEquals(listOf("first", "second", "last"), symbols.definitions.map { it.name })
        for (definition in symbols.definitions) assertEquals(definition.name, source.substring(definition.start, definition.end))
        for (reference in symbols.references) assertEquals(reference.name, source.substring(reference.start, reference.end))
    }
    @Test fun `macro invocation and purgem navigate to the active definition`() {
        val source = ".macro save reg:req, size=16\ninside: nop\n.endm\nsave a0\n.purgem save\nsave a1\n.macro save\n.endm\nentry: save\n"
        val symbols = RiscVSymbols.analyze(source)
        val definitions = symbols.definitions.filter { it.macro }
        val calls = symbols.references.filter { it.name == "save" }
        assertEquals(2, definitions.size)
        assertEquals(3, calls.size) // invocation, .purgem, invocation after redefinition
        assertEquals(listOf(definitions[0]), symbols.resolve(calls[0]))
        assertEquals(listOf(definitions[0]), symbols.resolve(calls[1]))
        assertEquals(listOf(definitions[1]), symbols.resolve(calls[2]))
        assertTrue(definitions.none { it.exported })
        assertTrue(symbols.definitions.none { it.name == "inside" })
        for (call in calls) assertEquals("save", source.substring(call.start, call.end))
    }
    @Test fun `macro and label namespaces remain separate`() {
        val source = ".globl save\n.macro save\n.endm\nsave: nop\nsave\nj save"
        val symbols = RiscVSymbols.analyze(source)
        assertTrue(resolve(source, "save", 1).single().macro)
        assertFalse(resolve(source, "save", 2).single().macro)
        assertEquals(1, symbols.definitions.count { it.exported })
    }
    @Test fun `recursive and nested macro calls skip expansion time declarations`() {
        val source = ".macro outer arg\nouter \\arg\n.macro inner\n.endm\n.endm\nouter a0\ninner\n"
        val symbols = RiscVSymbols.analyze(source)
        assertEquals(listOf("outer"), symbols.definitions.map { it.name })
        assertEquals(listOf("outer", "outer"), symbols.references.map { it.name })
        assertTrue(symbols.references.all { symbols.resolve(it).single().macro })
    }
    @Test fun `unknown and forward macros do not invent references`() {
        assertTrue(RiscVSymbols.analyze("unknown a0\n.macro unknown\n.endm").references.isEmpty())
        assertTrue(RiscVSymbols.analyze(".macro\n.endm\nunknown").definitions.isEmpty())
    }

}
