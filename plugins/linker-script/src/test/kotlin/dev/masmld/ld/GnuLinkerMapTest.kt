package dev.masmld.ld

import dev.masmld.ld.map.GnuLinkerMap
import dev.masmld.ld.map.MapKind
import org.junit.Assert.*
import org.junit.Test

class GnuLinkerMapTest {
    @Test fun `reads actual GNU RISC-V linker addresses and object provenance`() {
        val text = requireNotNull(javaClass.getResource("/fixtures/gnu-ld.map")).readText()
        val map = requireNotNull(GnuLinkerMap.parse(text))
        val output = map.entries.single { it.kind == MapKind.OUTPUT && it.name == ".text" }
        assertEquals(0x80000000uL, output.address)
        assertEquals(0x1000uL, output.loadAddress)
        assertEquals(8uL, output.size)
        val entry = map.entries.single { it.name == "member_entry" }
        assertEquals(0x80000004uL, entry.address)
        assertEquals("lib sample.a(member file.o)", entry.objectFile)
        assertEquals(".text.very_long_member_section", entry.inputSection)
        assertEquals(output, map.output(entry))
        assertEquals(setOf("entry.o", "lib sample.a(member file.o)"),
            map.inputs(output).filter { it.size != 0uL }.mapNotNull { it.objectFile }.toSet())
        val end = map.entries.single { it.name == "__text_end" }
        assertEquals(0x80000008uL, end.address)
        assertNull(end.objectFile)
        assertFalse(map.entries.any { it.name == "omitted" })
        assertEquals(0x80000008uL, map.entries.single { it.name == "emitted" }.address)
        val wrapped = map.entries.single { it.name == ".very_long_output_section_name" }
        assertEquals(wrapped.name, text.lineSequence().elementAt(wrapped.line))
    }

    @Test fun `ignores discarded sections and unsupported map formats`() {
        val text = """
            Discarded input sections

             .dead          0x0 0x10 old.o

            Linker script and memory map
            .live           0xfffffffffffffff0 0x8
             .live          0xfffffffffffffff0 0x8 live.o
                            0xfffffffffffffff7 live
            Cross Reference Table
             .wrong         0x0 0x8 old.o
        """.trimIndent()
        val map = requireNotNull(GnuLinkerMap.parse(text))
        assertEquals(listOf(".live", ".live", "live"), map.entries.map { it.name })
        assertEquals("live.o", map.entries.last().objectFile)
        assertNull(GnuLinkerMap.parse("VMA LMA Size Align Out In Symbol"))
    }
}
