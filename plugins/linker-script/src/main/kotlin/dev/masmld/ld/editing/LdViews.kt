package dev.masmld.ld.editing

import com.intellij.lang.PsiStructureViewFactory
import com.intellij.lang.folding.*
import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import dev.masmld.core.editing.*
import dev.masmld.ld.symbols.LdSymbolAnalysis

class LdStructure : PsiStructureViewFactory {
    override fun getStructureViewBuilder(file: PsiFile) = SourceOutline.builder(file) {
        LdSymbolAnalysis.analyze(file.text).definitions.sortedBy { it.start }.map { OutlineEntry(it.name, it.start, it.kind.name.lowercase()) }
    }
}
class LdFolding : FoldingBuilderEx() {
    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean) = SourceFolding.ranges(root.text, false).map { FoldingDescriptor(root.node, TextRange(it.start, it.end), null, it.placeholder) }.toTypedArray()
    override fun getPlaceholderText(node: ASTNode) = "{ … }"
    override fun isCollapsedByDefault(node: ASTNode) = false
}
class LdFormatter : com.intellij.formatting.FormattingModelBuilder {
    override fun createModel(context: com.intellij.formatting.FormattingContext) = SourceFormatter(false).createModel(context)
}
