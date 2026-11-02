package dev.masmld.riscv

import dev.masmld.riscv.highlighting.RiscVColors
import dev.masmld.riscv.highlighting.RiscVColorSettingsPage
import dev.masmld.riscv.highlighting.RiscVSyntaxHighlighter
import dev.masmld.riscv.lexer.RiscVTokens as T
import org.junit.Assert.*
import org.junit.Test

class RiscVHighlighterTest {
    @Test fun `lexical categories use distinct configurable attributes`() {
        val highlighter = RiscVSyntaxHighlighter()
        assertArrayEquals(arrayOf(RiscVColors.LINE_COMMENT), highlighter.getTokenHighlights(T.LINE_COMMENT))
        assertArrayEquals(arrayOf(RiscVColors.BLOCK_COMMENT), highlighter.getTokenHighlights(T.BLOCK_COMMENT))
        assertArrayEquals(arrayOf(RiscVColors.STRING), highlighter.getTokenHighlights(T.STRING))
        assertArrayEquals(arrayOf(RiscVColors.NUMBER), highlighter.getTokenHighlights(T.NUMBER))
        assertTrue(highlighter.getTokenHighlights(T.IDENTIFIER).isEmpty())
    }
    @Test fun `color preview exposes all contextual categories`() {
        val page = RiscVColorSettingsPage()
        val keys = page.attributeDescriptors.map { it.key }.toSet()
        assertTrue(keys.containsAll(page.additionalHighlightingTagToDescriptorMap.values))
        assertTrue(keys.containsAll(listOf(RiscVColors.INSTRUCTION, RiscVColors.PSEUDO_INSTRUCTION, RiscVColors.REGISTER, RiscVColors.LABEL, RiscVColors.DIRECTIVE)))
        for (tag in page.additionalHighlightingTagToDescriptorMap.keys) {
            assertTrue(page.demoText.contains("<$tag>"))
            assertTrue(page.demoText.contains("</$tag>"))
        }
    }
}
