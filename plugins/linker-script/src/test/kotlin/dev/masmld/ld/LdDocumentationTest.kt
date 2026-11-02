package dev.masmld.ld

import dev.masmld.core.documentation.DocumentationKind as K
import dev.masmld.core.documentation.DocumentationRenderer
import dev.masmld.ld.documentation.LdDocumentationCatalog as Catalog
import dev.masmld.ld.documentation.LdDocumentationLookup as Lookup
import dev.masmld.ld.highlighting.LdColorSettingsPage
import dev.masmld.ld.highlighting.LdColors
import dev.masmld.ld.highlighting.LdSyntaxHighlighter
import dev.masmld.ld.lexer.LdTokens as T
import org.junit.Assert.*
import org.junit.Test

class LdDocumentationTest {
    @Test fun `rcore commands functions and location counter resolve`() {
        val source = javaClass.getResource("/fixtures/rcore-linker.ld")!!.readText()
        for (name in listOf("OUTPUT_ARCH", "ENTRY", "SECTIONS", "ALIGN", "/DISCARD/")) {
            val offset = source.indexOf(name)
            for (i in offset until offset + name.length) assertEquals(name, Lookup.at(source, i)?.name)
        }
        assertEquals(".", Lookup.at(source, source.indexOf(". ="))?.name)
        assertNull(Lookup.at(source, source.indexOf("BASE_ADDRESS")))
        assertNull(Lookup.at(source, source.indexOf(".text.entry")))
    }
    @Test fun `MEMORY properties differ from query functions`() {
        val source = "MEMORY { RAM: ORIGIN = 0, LENGTH = 4K }\nSECTIONS { . = ORIGIN(RAM) + LENGTH(RAM); }"
        for (name in listOf("ORIGIN", "LENGTH")) {
            assertEquals(K.LINKER_COMMAND, Lookup.at(source, source.indexOf(name))?.kind)
            assertEquals(K.BUILTIN_FUNCTION, Lookup.at(source, source.lastIndexOf(name))?.kind)
        }
    }
    @Test fun `symbol names quoted keywords comments and strings have no docs`() {
        for ((source, offset) in listOf(
            "ALIGN = 1;" to 0, "ENTRY: { }" to 0, "\"ENTRY\" = 1;" to 1,
            "/* ALIGN(4K) */" to 3, "ASSERT(1, \"ENTRY(start)\");" to 11,
            "#define ALIGN(x) x" to 8, "custom_func(42)" to 0,
            "symbol-ALIGN = 1;" to 7, ".text.ALIGN : { }" to 6,
            "ENTRY(start)" to -1, "ENTRY(start)" to 12,
        )) assertNull("$source at $offset", Lookup.at(source, offset))
    }
    @Test fun `builtin lookahead skips comments and whitespace`() {
        assertEquals("ALIGN", Lookup.at("ALIGN /* note */\n(4K)", 1)?.name)
        assertNull(Lookup.at("ALIGN /* unfinished", 0))
        assertEquals("MEMORY", Lookup.at("MEMORY\n{ }", 0)?.name)
        assertEquals("AT", Lookup.at("AT>ROM", 0)?.name)
    }
    @Test fun `all offline catalog entries have reachable syntax and sources`() {
        assertEquals(48, Catalog.entries.size)
        for (entry in Catalog.entries) {
            val source = when (entry.name) {
                "MEMORY", "SECTIONS", "PHDRS" -> entry.name + " { }"
                "NOLOAD", "READONLY", "SIZEOF_HEADERS" -> entry.name
                "INCLUDE" -> "INCLUDE extra.ld"
                "/DISCARD/" -> "/DISCARD/ : { }"
                "." -> ". = 0;"
                "ORIGIN", "LENGTH" -> if (entry.kind == K.LINKER_COMMAND) entry.name + " = 0" else entry.name + "(RAM)"
                else -> entry.name + "(0)"
            }
            assertEquals("${entry.kind} ${entry.name}", entry, Lookup.at(source, 0))
            assertFalse(entry.summary.isBlank())
            assertFalse(entry.example.isBlank())
            assertTrue(entry.sources.all { it.url.startsWith("https://sourceware.org/") && it.revision.isNotBlank() })
            assertTrue(DocumentationRenderer.render(entry).contains("资料来源"))
        }
    }
    @Test fun `ALIGN and ABSOLUTE documentation avoid common semantic errors`() {
        assertTrue(Catalog.find(K.BUILTIN_FUNCTION, "ALIGN")!!.notes.contains("只计算"))
        assertTrue(Catalog.find(K.BUILTIN_FUNCTION, "ABSOLUTE")!!.notes.contains("不是数学"))
        assertEquals("SIZEOF_HEADERS", Catalog.find(K.BUILTIN_FUNCTION, "SIZEOF_HEADERS")!!.syntax)
        assertTrue(Catalog.find(K.LINKER_COMMAND, "MEMORY")!!.notes.contains("不配置 CPU"))
    }
    @Test fun `highlighter exposes independent ld palette and needs no riscv classes`() {
        val highlighter = LdSyntaxHighlighter()
        assertArrayEquals(arrayOf(LdColors.FUNCTION), highlighter.getTokenHighlights(T.FUNCTION))
        assertArrayEquals(arrayOf(LdColors.COMMAND), highlighter.getTokenHighlights(T.COMMAND))
        assertArrayEquals(arrayOf(LdColors.LOCATION), highlighter.getTokenHighlights(T.DOT))
        assertEquals(12, LdColorSettingsPage().attributeDescriptors.size)
        assertThrows(ClassNotFoundException::class.java) { Class.forName("dev.masmld.riscv.RiscVLanguage") }
    }
}
