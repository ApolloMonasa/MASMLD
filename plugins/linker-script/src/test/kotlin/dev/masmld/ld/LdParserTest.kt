package dev.masmld.ld

import com.intellij.lang.LighterASTNode
import com.intellij.lang.impl.PsiBuilderFactoryImpl
import com.intellij.openapi.util.Ref
import dev.masmld.ld.parser.LdElements
import dev.masmld.ld.parser.LdParserDefinition
import org.junit.Assert.*
import org.junit.Test

class LdParserTest {
    private fun inspect(source: String): Pair<Int, Int> {
        val definition = LdParserDefinition()
        val builder = PsiBuilderFactoryImpl().createBuilder(definition, definition.createLexer(null), source)
        definition.createParser(null).parseLight(definition.fileNodeType, builder)
        val tree = builder.lightTree
        assertEquals(0, tree.root.startOffset)
        assertEquals(source.length, tree.root.endOffset)
        var blocks = 0
        var clauses = 0
        fun visit(node: LighterASTNode) {
            if (node.tokenType == LdElements.BLOCK) blocks++
            if (node.tokenType == LdElements.STATEMENT) clauses++
            val children = Ref<Array<LighterASTNode>>()
            val count = tree.getChildren(node, children)
            if (count == 0) return
            try { children.get().take(count).forEach { visit(it) } }
            finally { tree.disposeChildren(children.get(), count) }
        }
        visit(tree.root)
        return blocks to clauses
    }
    @Test fun `rcore linker preserves nested output sections`() {
        val source = javaClass.getResource("/fixtures/rcore-linker.ld")!!.readText()
        val (blocks, clauses) = inspect(source)
        assertEquals(6, blocks)
        assertTrue(clauses > 20)
    }
    @Test fun `MEMORY and SECTIONS parse independently of architecture`() {
        val (blocks, clauses) = inspect("MEMORY { RAM: ORIGIN=0, LENGTH=4K }\nSECTIONS { .text : { *(.text) } > RAM }")
        assertEquals(3, blocks)
        assertTrue(clauses >= 5)
    }
    @Test fun `unfinished blocks and operands remain recoverable`() {
        val (blocks, clauses) = inspect("SECTIONS {\n.text : { *(.text)\n. = ALIGN(\nnext = 42;\n")
        assertEquals(2, blocks)
        assertTrue(clauses >= 5)
        assertEquals(0 to 0, inspect("/* comment */\n"))
        assertEquals(0 to 0, inspect(""))
    }
    @Test fun `comment and string braces do not create blocks`() {
        assertEquals(1, inspect("SECTIONS { ASSERT(1, \"{;}\"); /* } */ }").first)
        assertTrue(inspect("} } x = 1;").second > 0)
    }
}
