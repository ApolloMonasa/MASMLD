package dev.masmld.ld

import dev.masmld.ld.symbols.*
import dev.masmld.ld.editing.LdDiagnostics
import org.junit.Assert.*
import org.junit.Test

class LdSymbolsTest {
    @Test fun `memory sections symbols and their namespaces are distinct`() {
        val source = "MEMORY { RAM (rwx) : ORIGIN = 0x80000000, LENGTH = 128K }\nBASE = ORIGIN(RAM);\nSECTIONS { .text : { start = .; *(.text) } > RAM }\nENTRY(start)"
        val symbols = LdSymbolAnalysis.analyze(source)
        assertEquals(listOf("RAM", "BASE", ".text", "start"), symbols.definitions.map { it.name })
        assertEquals(0x80000000L, symbols.value("BASE"))
        assertTrue(symbols.references.all { symbols.resolve(it).isNotEmpty() })
        assertEquals(131072L, symbols.evaluate("LENGTH(RAM)"))
        for (def in symbols.definitions) assertEquals(def.name, source.substring(def.start, def.end))
    }
    @Test fun `constant evaluation follows precedence suffixes and safe unknown propagation`() {
        assertEquals(21L, ConstantExpression.evaluate("(1 + 2) * 7"))
        assertEquals(1024L, ConstantExpression.evaluate("1K"))
        assertEquals(16L, ConstantExpression.evaluate("020"))
        assertEquals(0L, ConstantExpression.evaluate("3 > 5"))
        assertNull(ConstantExpression.evaluate("9223372036854775807 + 1"))
        assertNull(ConstantExpression.evaluate("1 / 0"))
        assertNull(ConstantExpression.evaluate("1 << 64"))
        assertNull(ConstantExpression.evaluate("SIZEOF(.text)"))
        val symbols = LdSymbolAnalysis.analyze("A = B; B = A;")
        assertNull(symbols.value("A"))
        assertEquals(16L, symbols.evaluate("ALIGN(13, 8)"))
        assertEquals(15L, symbols.evaluate("ALIGN(13, 3)"))
        assertEquals(13L, symbols.evaluate("ALIGN(13, 0)"))
        assertNull(symbols.evaluate("ALIGN(8)"))
    }
    @Test fun `region diagnostics report known overlap and undefined targets without guessing section sizes`() {
        val source = "MEMORY { A : ORIGIN = 0, LENGTH = 1K\nB : ORIGIN = 512, LENGTH = 1K }\nSECTIONS { .text : { *(.text) } > MISSING }"
        val symbols = LdSymbolAnalysis.analyze(source)
        assertEquals(2, symbols.regions.size)
        val messages = LdDiagnostics.analyze(symbols).map { it.message }
        assertTrue(messages.any { it.contains("重叠") })
        assertTrue(messages.any { it.contains("MISSING") })
        assertTrue(LdDiagnostics.analyze(LdSymbolAnalysis.analyze("SIZE = .; SIZE = 4;")).isEmpty())
    }
    @Test fun `comments filenames attributes and input wildcards are not global symbol references`() {
        val symbols = LdSymbolAnalysis.analyze("INCLUDE \"base.ld\"\n/* BAD = unknown; */\nOUTPUT_ARCH(riscv)\nSECTIONS { .text : { *(.text .text.*) } }")
        assertEquals(listOf(".text"), symbols.definitions.map { it.name })
        assertTrue(symbols.references.isEmpty())
    }
    @Test fun `region aliases navigate and evaluate while comparisons remain symbol expressions`() {
        val symbols = LdSymbolAnalysis.analyze("MEMORY { RAM : ORIGIN = 0x8000, LENGTH = 16K }\nREGION_ALIAS(\"TEXT\", RAM);\nBASE = ORIGIN(TEXT);\nASSERT(BASE > 0, \"positive\")\nSECTIONS { .text : { *(.text) } > TEXT }")
        assertEquals(0x8000L, symbols.value("BASE"))
        assertEquals(2, symbols.references.count { it.name == "TEXT" })
        assertTrue(LdDiagnostics.analyze(symbols).isEmpty())
    }

    @Test fun `fixed output section addresses and alignment are checked against known regions`() {
        val model = LdSymbolAnalysis.analyze("MEMORY { RAM : ORIGIN = 0x8000, LENGTH = 16K }\nSECTIONS { .text 0x8003 : ALIGN(4) { *(.text) } > RAM\n.data 0x18000 : { *(.data) } > RAM }")
        assertEquals(listOf(".text", ".data"), model.sections.map { it.definition.name })
        val messages = LdDiagnostics.analyze(model).map { it.message }
        assertTrue(messages.any { it.contains("ALIGN") })
        assertTrue(messages.any { it.contains("不在 MEMORY") })
    }

}
