package dev.masmld.riscv

import dev.masmld.core.documentation.DocumentationEntry
import dev.masmld.core.documentation.DocumentationKind as K
import dev.masmld.core.documentation.DocumentationRenderer
import dev.masmld.core.documentation.DocumentationSource
import dev.masmld.riscv.documentation.RiscVDocumentationCatalog as Catalog
import dev.masmld.riscv.documentation.RiscVDocumentationLookup as Lookup
import org.junit.Assert.*
import org.junit.Test

class RiscVDocumentationTest {
    @Test fun `entry asm tokens resolve while labels remain symbols`() {
        val source = javaClass.getResource("/fixtures/entry.asm")!!.readText()
        for ((word, kind) in listOf(".section" to K.DIRECTIVE, ".globl" to K.DIRECTIVE,
            "la" to K.PSEUDO_INSTRUCTION, "sp" to K.REGISTER, "call" to K.PSEUDO_INSTRUCTION, ".space" to K.DIRECTIVE)) {
            val start = source.indexOf(word)
            for (offset in start until start + word.length) {
                assertEquals(word, Lookup.at(source, offset)?.name)
                assertEquals(kind, Lookup.at(source, offset)?.kind)
            }
        }
        for (name in listOf("_start", "boot_stack_top", "rust_main")) {
            assertNull("$name is a symbol", Lookup.at(source, source.indexOf(name)))
        }
    }
    @Test fun `labels comments strings whitespace and unknown names have no ISA docs`() {
        for ((source, offset) in listOf(
            "add: ret" to 1, "sp: ret" to 0, "add = 42" to 0,
            ".globl sp" to 7, "j a0" to 2, "beq a0,a1,sp" to 10,
            "addi a0,a1,sp" to 11, "# la sp, target" to 2,
            ".asciz \"la sp\"" to 8, "/* add a0 */" to 3,
            "custom_macro a0" to 0, "add x32,a1,a2" to 4,
            "la sp, target" to 2, "la sp, target" to -1, "la sp, target" to 13,
        )) assertNull("$source at $offset", Lookup.at(source, offset))
    }
    @Test fun `relocations do not turn symbol names into registers`() {
        val source = "lw a0,%lo(sp)(a1)"
        assertNull(Lookup.at(source, source.indexOf("sp")))
        assertEquals("a1", Lookup.at(source, source.indexOf("a1"))?.name)
    }
    @Test fun `all catalog entries are reachable through contextual lookup`() {
        assertEquals(189 + dev.masmld.riscv.editing.RiscVIsa.instructions.size + dev.masmld.riscv.editing.RiscVIsa.floatingRegisters.size, Catalog.entries.size)
        for (entry in Catalog.entries) {
            val source = if (entry.kind == K.REGISTER) if (entry.name in dev.masmld.riscv.editing.RiscVIsa.floatingRegisters) "fadd.s ${entry.name},fa0,fa1" else "add ${entry.name},zero,zero" else entry.name
            val offset = if (entry.kind == K.REGISTER) source.indexOf(entry.name) else 0
            assertEquals("${entry.kind}: ${entry.name}", entry, Lookup.at(source, offset))
            assertFalse(entry.summary.isBlank())
            assertFalse(entry.example.isBlank())
            assertTrue(entry.sources.isNotEmpty())
            assertTrue(entry.sources.all { it.url.startsWith("https://") && it.revision.isNotBlank() && it.notice.isNotBlank() })
        }
    }
    @Test fun `register aliases preserve numeric identity and ABI meaning`() {
        for (name in listOf("sp", "x2")) assertTrue(Catalog.find(K.REGISTER, name)!!.syntax.contains("x2 (sp)"))
        for (name in listOf("s0", "fp", "x8")) assertTrue(Catalog.find(K.REGISTER, name)!!.syntax.contains("x8 (s0/fp)"))
        assertTrue(Catalog.find(K.REGISTER, "a0")!!.summary.contains("返回值"))
        assertTrue(Catalog.find(K.REGISTER, "zero")!!.summary.contains("写入的结果被丢弃"))
        assertNull(Catalog.find(K.REGISTER, "pc"))
    }
    @Test fun `pseudo expansions and RV64 restrictions are explicit`() {
        for (name in listOf("li", "la", "call", "ret")) assertNotNull(Catalog.find(K.PSEUDO_INSTRUCTION, name))
        assertTrue(Catalog.find(K.PSEUDO_INSTRUCTION, "la")!!.notes.contains("PIC"))
        assertTrue(Catalog.find(K.PSEUDO_INSTRUCTION, "call")!!.notes.contains("链接松弛"))
        assertTrue(Catalog.find(K.PSEUDO_INSTRUCTION, "li")!!.notes.contains("常量"))
        for (name in listOf("ld", "sd", "lwu", "addiw", "sraw")) {
            assertEquals("RV64I", Catalog.find(K.INSTRUCTION, name)!!.applicability)
        }
        assertTrue(Catalog.find(K.INSTRUCTION, "sltiu")!!.summary.contains("先符号扩展"))
        assertTrue(Catalog.find(K.DIRECTIVE, ".word")!!.summary.contains("32"))
    }
    @Test fun `renderer escapes text and rejects executable link schemes`() {
        val source = DocumentationSource("x", "<source>", "javascript:alert(1)", "\"rev\"", "A&B")
        val entry = DocumentationEntry("fake", K.INSTRUCTION, "fake <rd>", "<script>alert('x')</script>", "a&b", "a < b", "test", listOf(source))
        val html = DocumentationRenderer.render(entry)
        assertFalse(html.contains("<script>"))
        assertFalse(html.contains("href="))
        assertTrue(html.contains("&lt;rd&gt;"))
        assertTrue(html.contains("a&amp;b"))
        assertTrue(html.contains("&lt;source&gt;"))
    }
    @Test fun `rendered offline documents include kind example and source metadata`() {
        val entry = Catalog.find(K.PSEUDO_INSTRUCTION, "la")!!
        val html = DocumentationRenderer.render(entry)
        assertTrue(html.contains("伪指令"))
        assertTrue(html.contains("boot_stack_top"))
        assertTrue(html.contains("href=\"https://"))
        assertTrue(html.contains(entry.sources.first().revision))
    }
}
