package dev.masmld.riscv

import dev.masmld.riscv.symbols.*
import org.junit.Assert.*
import org.junit.Test

class SymbolNamesTest {
    @Test fun `plain Itanium and Rust legacy paths decode without guessing templates`() {
        assertEquals("main", SymbolNames.sourceName("_Z4mainv"))
        assertEquals("app::run", SymbolNames.sourceName("_ZN3app3runEv"))
        assertEquals("app::run", SymbolNames.sourceName("_ZN3app3run17h0123456789abcdefE"))
        assertNull(SymbolNames.sourceName("_ZN3app3runIiEEv"))
        assertNull(SymbolNames.sourceName("_RcomplexRustV0"))
        assertNull(SymbolNames.sourceName("_Z99999broken"))
    }
    @Test fun `Rust export aliases include unsafe attributes but not comments`() {
        val source = "// #[export_name = \"ignored\"] fn bad() {}\n#[unsafe(export_name = \"entry\")] pub unsafe extern \"C\" fn boot() {}"
        val aliases = SourceAliases.analyze(source, true, false)
        assertEquals(listOf("entry"), aliases.map { it.external })
        assertEquals("boot", aliases.single().source)
        assertEquals("boot", source.substring(aliases.single().offset).substringBefore('('))
    }
    @Test fun `C asm and GNU ld aliases retain exact source names`() {
        val c = SourceAliases.analyze("extern void source_fn(void) __asm__(\"entry\");", false, false)
        assertEquals("source_fn", c.single().source)
        val ld = SourceAliases.analyze("PROVIDE(entry = rust_main);", false, true)
        assertEquals(SourceAlias("entry", "rust_main", 8), ld.single())
    }
}
