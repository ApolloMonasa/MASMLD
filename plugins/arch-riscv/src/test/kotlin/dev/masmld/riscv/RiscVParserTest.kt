package dev.masmld.riscv

import com.intellij.lang.LighterASTNode
import com.intellij.lang.impl.PsiBuilderFactoryImpl
import com.intellij.openapi.util.Ref
import dev.masmld.riscv.parser.RiscVParserDefinition
import dev.masmld.riscv.parser.RiscVElements
import org.junit.Assert.*
import org.junit.Test

class RiscVParserTest {
    private fun statements(source: String): List<String> {
        val definition = RiscVParserDefinition()
        val builder = PsiBuilderFactoryImpl().createBuilder(definition, definition.createLexer(null), source)
        definition.createParser(null).parseLight(definition.fileNodeType, builder)
        val tree = builder.lightTree
        assertEquals(0, tree.root.startOffset)
        assertEquals(source.length, tree.root.endOffset)
        val children = Ref<Array<LighterASTNode>>()
        val count = tree.getChildren(tree.root, children)
        try {
            return children.get().take(count).filter { it.tokenType == RiscVElements.STATEMENT }
                .map { source.substring(it.startOffset, it.endOffset) }
        } finally {
            tree.disposeChildren(children.get(), count)
        }
    }
    @Test fun `unfinished statements recover at line and semicolon`() {
        val source = ".text\n1: lw a0,(\nret; custom_macro \\param\n.asciz \"unterminated\nadd a0,a1,a2\n"
        val statements = statements(source)
        assertEquals(6, statements.size)
        assertTrue(statements.any { it.trim() == "ret" })
        assertTrue(statements.last().trim().startsWith("add"))
    }
    @Test fun `empty and comment-only input preserves full file range`() {
        assertTrue(statements("").isEmpty())
        assertTrue(statements("# comment\n/* block\ncomment */\n").isEmpty())
    }
    @Test fun `comment string and relocation punctuation do not split statements`() {
        assertEquals(2, statements("addi a0,/* ; */a1,1; .asciz \"x;y\"\n").size)
        assertEquals(1, statements("lw a0,%lo(symbol)(sp)\n").size)
    }
    @Test fun `definition nodes identify labels and constant names precisely`() {
        val source = "start: la a0, end\n.equ SIZE, 16\nend:\n1: j 1b\n.macro save reg:req\n.endm\nsave a0"
        val definition = RiscVParserDefinition()
        val builder = PsiBuilderFactoryImpl().createBuilder(definition, definition.createLexer(null), source)
        definition.createParser(null).parseLight(definition.fileNodeType, builder)
        val tree = builder.lightTree
        val names = mutableListOf<String>()
        fun visit(node: LighterASTNode) {
            if (node.tokenType == RiscVElements.SYMBOL_DEFINITION) names += source.substring(node.startOffset, node.endOffset).trim()
            val children = Ref<Array<LighterASTNode>>()
            val count = tree.getChildren(node, children)
            if (count == 0) return
            try { children.get().take(count).forEach { visit(it) } }
            finally { tree.disposeChildren(children.get(), count) }
        }
        visit(tree.root)
        assertEquals(listOf("start", "SIZE", "end", "1", "save"), names)
    }

}
