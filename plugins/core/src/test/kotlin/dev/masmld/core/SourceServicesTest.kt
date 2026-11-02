package dev.masmld.core

import dev.masmld.core.settings.*
import dev.masmld.core.source.*
import dev.masmld.core.editing.*
import org.junit.Assert.*
import org.junit.Test

class SourceServicesTest {
    @Test fun `file directory and glob rules have deterministic precedence`() {
        val state = AssemblySettings.State("riscv", rules = mutableListOf(AssemblyRule("boot/", "arm"), AssemblyRule("boot/start.S", "riscv", "rv32i"), AssemblyRule("**/generic.asm", "")))
        assertEquals("arm", AssemblySettings.select(state, "boot/lib/a.s").architecture)
        state.profileId = "rv64i"; state.target = "rv64gc"
        assertEquals("", AssemblySettings.select(state, "boot/lib/a.s").target)
        assertEquals("rv32i", AssemblySettings.select(state, "boot/start.S").profile)
        assertEquals("riscv", AssemblySettings.select(state, "src/x.s").architecture)
        assertEquals("", AssemblySettings.select(state, "generic.asm").architecture)
        assertTrue(AssemblySettings.matches("**/*.S", "entry.S"))
        assertFalse(AssemblySettings.matches("src/*.S", "src/nested/entry.S"))
    }
    @Test fun `settings migration and snapshots do not lose or share rule state`() {
        val settings = AssemblySettings(); settings.loadState(AssemblySettings.State("riscv"))
        settings.selectArchitecture("other")
        assertEquals("other", settings.architectureId)
        val state = settings.state; state.rules += AssemblyRule("src/", "riscv")
        assertTrue(settings.state.rules.isEmpty())
        val count = settings.modificationCount; settings.loadState(state)
        assertTrue(settings.modificationCount > count)
        state.rules[0].architecture = "bad"
        assertEquals("riscv", settings.selection("src/a.asm").architecture)
    }
    @Test fun `literal includes exclude comments strings and macro expansion time directives`() {
        val source = "# .include \"bad\"\n/* .include \"bad2\" */\n.asciz \".include bad\"\n.macro m\n.include \"body\"\n.endm\n.include \"defs.inc\""
        assertEquals(listOf("defs.inc"), SourceGraph.includes(source, true).map { it.path })
        assertEquals(listOf("../layout/common.ld"), SourceGraph.includes("INCLUDE ../layout/common.ld\nSECTIONS {}", false).map { it.path })
    }
    @Test fun `include expansion preserves source coordinates and safely breaks cycles`() {
        val root = SourceUnit("root", ".include \"child\"\ncall target")
        val child = SourceUnit("child", "target: ret\n.include \"root\"")
        val expanded = SourceGraph.expand(root, true) { _, name -> mapOf("root" to root, "child" to child)[name] }
        assertEquals("child", expanded.site(expanded.text.indexOf("target:"))!!.unit.id)
        assertEquals(0, expanded.site(expanded.text.indexOf("target:"))!!.offset)
        assertEquals("root", expanded.site(expanded.text.indexOf("call"))!!.unit.id)
        assertTrue(expanded.issues.any { it.contains("cycle") })
    }
    @Test fun `folding does not use braces inside comments or strings`() {
        val ld = "SECTIONS {\n.text : {\n*(.text)\n}\n/* } */\n}\n"
        assertEquals(2, SourceFolding.ranges(ld, false).size)
        assertTrue(SourceFolding.ranges(".asciz \".macro\"\n", true).isEmpty())
        assertEquals(1, SourceFolding.ranges(".macro m\nnop\n.endm", true).size)
    }
    @Test fun `formatting changes only well understood gaps`() {
        assertEquals(0, SourceSpacing.spaces("a0", ","))
        assertEquals(1, SourceSpacing.spaces(",", "a1"))
        assertEquals(0, SourceSpacing.spaces("%", "lo"))
        assertEquals(-1, SourceSpacing.spaces("symbol", "-"))
    }
}
