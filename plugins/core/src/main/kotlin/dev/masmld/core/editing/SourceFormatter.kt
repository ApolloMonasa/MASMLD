package dev.masmld.core.editing

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.TokenType
import com.intellij.psi.formatter.common.AbstractBlock

/** Only token gaps are formatted. Strings, comments, directives and token spelling remain untouched. */
object SourceSpacing {
    fun spaces(left: String, right: String): Int = when {
        right in setOf(",", ":", ";", ")", "]") -> 0
        left in setOf("(", "[", "%", "@", "\\") -> 0
        left == "," -> 1
        left == ":" -> 1
        left in setOf("=", "+=", "-=", "==", "!=", "<", ">", "<<", ">>") || right in setOf("=", "+=", "-=", "==", "!=", "<", ">", "<<", ">>") -> 1
        else -> -1 // Preserve other gaps: GNU identifiers, relocations and expressions can be whitespace-sensitive.
    }
}
class SourceFormatter(private val assembly: Boolean) : FormattingModelBuilder {
    override fun createModel(context: FormattingContext): FormattingModel = FormattingModelProvider.createFormattingModelForPsiFile(
        context.containingFile, CodeBlock(context.node, assembly, true), context.codeStyleSettings)
    private class CodeBlock(node: ASTNode, private val assembly: Boolean, private val root: Boolean = false) : AbstractBlock(node, null, null) {
        override fun buildChildren(): List<Block> = generateSequence(myNode.firstChildNode) { it.treeNext }
            .filter { it.elementType != TokenType.WHITE_SPACE && it.textLength > 0 && !it.text.all(Char::isWhitespace) }
            .map { CodeBlock(it, assembly) }.toList()
        override fun getIndent(): Indent {
            if (root) return Indent.getNoneIndent()
            if (!assembly) return if (myNode.treeParent?.elementType?.toString() == "LD_BLOCK" && myNode.text !in setOf("{", "}")) Indent.getNormalIndent() else Indent.getNoneIndent()
            val text = myNode.text.trimStart()
            if (myNode.elementType.toString() != "RISCV_STATEMENT") return Indent.getNoneIndent()
            val before = myNode.treeParent?.text.orEmpty().take(myNode.startOffset - (myNode.treeParent?.startOffset ?: 0))
            val macroDepth = Regex("(?m)^\\s*\\.macro\\b").findAll(before).count() - Regex("(?m)^\\s*\\.endm\\b").findAll(before).count()
            val levels = if (macroDepth > 0 && !text.startsWith(".endm")) 1 else if (text.startsWith('.') || Regex("^[\\w.$]+:").containsMatchIn(text)) 0 else 1
            return if (levels == 0) Indent.getNoneIndent() else Indent.getNormalIndent()
        }
        override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
            if (assembly && root) {
                val previous = (subBlocks.getOrNull(newChildIndex - 1) as? CodeBlock)?.node
                when (previous?.firstChildNode?.text) {
                    ".macro" -> return ChildAttributes(Indent.getNormalIndent(), null)
                    ".endm" -> return ChildAttributes(Indent.getNoneIndent(), null)
                }
            }
            return super.getChildAttributes(newChildIndex)
        }
        override fun getSpacing(child1: Block?, child2: Block): Spacing? {
            if (child1 == null) return null
            val first = (child1 as CodeBlock).node
            val second = (child2 as CodeBlock).node
            val source = myNode.psi.containingFile.text
            val gap = source.substring(first.textRange.endOffset, second.startOffset)
            val lines = gap.count { it == '\n' }
            if (lines > 0) return Spacing.createSpacing(0, 0, lines.coerceAtMost(2), true, 2)
            val spaces = SourceSpacing.spaces(first.text, second.text)
            return if (spaces < 0) null else Spacing.createSpacing(spaces, spaces, 0, true, 2)
        }
        override fun isLeaf() = myNode.firstChildNode == null
    }
}
