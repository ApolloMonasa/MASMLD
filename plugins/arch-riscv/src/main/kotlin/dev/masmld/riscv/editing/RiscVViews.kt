package dev.masmld.riscv.editing

import com.intellij.lang.PsiStructureViewFactory
import com.intellij.lang.folding.*
import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import dev.masmld.core.editing.*
import dev.masmld.riscv.symbols.RiscVSymbols

class RiscVStructure : PsiStructureViewFactory {
    override fun getStructureViewBuilder(file: PsiFile) = SourceOutline.builder(file) {
        RiscVSymbols.analyze(file.text).definitions.sortedBy { it.start }.map { OutlineEntry(it.name, it.start, if (it.macro) "macro" else "symbol") }
    }
}
class RiscVFolding : FoldingBuilderEx() {
    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean) = SourceFolding.ranges(root.text, true).map { FoldingDescriptor(root.node, TextRange(it.start, it.end), null, it.placeholder) }.toTypedArray()
    override fun getPlaceholderText(node: ASTNode) = "…"
    override fun isCollapsedByDefault(node: ASTNode) = false
}
class RiscVFormatter : com.intellij.formatting.FormattingModelBuilder {
    override fun createModel(context: com.intellij.formatting.FormattingContext) = SourceFormatter(true).createModel(context)
}
