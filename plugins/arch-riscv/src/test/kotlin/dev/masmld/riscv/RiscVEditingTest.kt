package dev.masmld.riscv

import dev.masmld.riscv.editing.*
import dev.masmld.riscv.symbols.RiscVSymbols
import dev.masmld.riscv.highlighting.*
import dev.masmld.core.source.*
import org.junit.Assert.*
import org.junit.Test

class RiscVEditingTest {
    @Test fun `ISA selection respects XLEN dependencies and G shorthand`() {
        assertFalse(RiscVIsa.supports("ld", RiscVIsa.parse("rv32i")))
        assertFalse(RiscVIsa.supports("mul", RiscVIsa.parse("rv64i")))
        assertTrue(RiscVIsa.supports("mul", RiscVIsa.parse("rv64im")))
        assertTrue(RiscVIsa.supports("csrrw", RiscVIsa.parse("rv64gc")))
        assertFalse(RiscVIsa.supports("c.jal", RiscVIsa.parse("rv64gc")))
        assertTrue(RiscVIsa.supports("c.fld", RiscVIsa.parse("rv32idc")))
        assertNull(RiscVIsa.parse("rv128garbage"))
    }
    @Test fun `extension mnemonics and floating register roles have exact ranges`() {
        val text = "fadd.s fa0,fa1,fa2\namoadd.w.aq a0,a1,(a2)"
        val roles = RiscVClassifier.classify(text)
        assertEquals(2, roles.count { it.role == RiscVRole.INSTRUCTION })
        assertEquals(6, roles.count { it.role == RiscVRole.REGISTER })
        assertTrue(RiscVSymbols.analyze(text).references.isEmpty())
        assertTrue(RiscVSymbols.analyze("fadd.s fa0,fa1,fa2,rne\nfence rw,rw\ncsrr a0,mstatus").references.isEmpty())
        assertFalse(RiscVIsa.supports("csrr", RiscVIsa.parse("rv64i")))
    }
    @Test fun `diagnostics check only known forms and ignore macro placeholders`() {
        val problems = RiscVDiagnostics.analyze("add a0,a1\naddi a0,x32,4096\nmul a0,a1,a2\n.macro m reg\nadd \\reg,sp,a0\n.endm", RiscVIsa.parse("rv32i"))
        assertTrue(problems.any { it.message.contains("操作数") })
        assertTrue(problems.any { it.message.contains("x32") })
        assertTrue(problems.any { it.message.contains("-2048") })
        assertTrue(problems.any { it.requiredTarget == "rv32im" })
        assertEquals(4, problems.size)
    }
    @Test fun `completion distinguishes mnemonic operands and inert text`() {
        assertEquals(true, RiscVCompletionContext.head("entry: ad", 9))
        assertEquals(false, RiscVCompletionContext.head("add a", 5))
        assertNull(RiscVCompletionContext.head("/* add ", 7))
        assertNull(RiscVCompletionContext.head("# add ", 6))
    }
    @Test fun `include expansion binds macro calls and purgem to the correct origin`() {
        val root = SourceUnit("main", ".include \"macros.inc\"\nsave ra\n.purgem save\nsave a0\n")
        val macro = SourceUnit("macros.inc", ".macro save reg\nnop\n.endm\n")
        val graph = SourceGraph.expand(root, true) { _, name -> if (name == macro.id) macro else null }
        val symbols = RiscVSymbols.analyze(graph.text)
        val refs = symbols.references.filter { it.name == "save" }
        assertEquals(2, refs.size)
        assertEquals("macros.inc", graph.site(symbols.resolve(refs.first()).single().start)!!.unit.id)
    }
    @Test fun `macro completion observes purge and target options reject unsupported configurations`() {
        val source = ".macro save\n.endm\n.purgem save\n"
        assertTrue(RiscVCompletionContext.activeMacros(source, source.length).isEmpty())
        assertTrue(RiscVDiagnostics.analyze("nop # comment\nret /* comment */", null).isEmpty())
        assertNotNull(RiscVArchitectureProvider().validateOptions("rv64i", "gnu", "rv64garbage"))
        assertNull(RiscVArchitectureProvider().validateOptions("rv64i", "gnu", "rv64gc"))
    }

    @Test fun `option architecture scopes and semicolon statements are respected`() {
        val text = ".option push; .option arch,+m\nmul a0,a1,a2\n.option pop\nmul a0,a1,a2"
        val issues = RiscVDiagnostics.analyze(text, RiscVIsa.parse("rv64i"))
        assertEquals(1, issues.size)
        assertEquals(text.lastIndexOf("mul"), issues.single().start)
        val scopes = RiscVTargetScopes.build(text, RiscVIsa.parse("rv64i"))
        assertTrue(RiscVIsa.supports("mul", RiscVTargetScopes.at(scopes, text.indexOf("mul"))))
        assertFalse(RiscVIsa.supports("mul", RiscVTargetScopes.at(scopes, text.lastIndexOf("mul"))))
        assertTrue(RiscVDiagnostics.analyze(".macro add reg\n.endm\nadd a0", null).isEmpty())
    }

}
