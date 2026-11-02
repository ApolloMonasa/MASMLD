package dev.masmld.riscv.symbols

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.PsiReference
import com.intellij.psi.ResolveResult
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import dev.masmld.core.language.AssemblyFileType
import dev.masmld.riscv.RiscVLanguage
import dev.masmld.riscv.psi.RiscVStatement
import dev.masmld.riscv.psi.RiscVSymbolDefinition

object RiscVSymbolReferences {
    private val cache = Key.create<CachedValue<AssemblySymbols>>("masmld.riscv.symbols")
    fun symbols(file: PsiFile): AssemblySymbols = CachedValuesManager.getCachedValue(file, cache) {
        CachedValueProvider.Result.create(RiscVSymbols.analyze(file.text), file)
    }
    fun references(statement: RiscVStatement): Array<PsiReference> {
        val range = statement.textRange
        return (RiscVProgram.get(statement.containingFile).references(statement.containingFile, range.startOffset, range.endOffset)
            .map { (reference, offset) -> SymbolReference(statement, reference, offset) } + dev.masmld.core.source.IncludeFiles.references(statement, true)).toTypedArray()
    }
    private fun declaration(file: PsiFile, definition: AssemblySymbolDefinition): RiscVSymbolDefinition? {
        val leaf = file.findElementAt(definition.start) ?: return null
        return leaf.parent as? RiscVSymbolDefinition
    }
    private class SymbolReference(statement: RiscVStatement, private val use: AssemblySymbolReference, private val offset: Int) :
        PsiPolyVariantReferenceBase<RiscVStatement>(statement,
            TextRange(offset - statement.textRange.startOffset, offset + use.end - use.start - statement.textRange.startOffset), true) {
        override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
            val file = element.containingFile
            val program = RiscVProgram.get(file)
            val local = program.symbols.resolve(use).mapNotNull(program::definition)
            if (use.macroTarget != null || local.isNotEmpty() || use.name.matches(Regex("[0-9]+[fb]")) || use.name.startsWith(".L")) {
                return local.map { PsiElementResolveResult(it) }.toTypedArray()
            }
            val project = element.project
            if (DumbService.isDumb(project)) return ResolveResult.EMPTY_ARRAY
            val targets = mutableListOf<PsiElement>()
            val scope = GlobalSearchScope.projectScope(project)
            try {
                // Exact exported-name index, including ordinary .inc files; never scan the entire project.
                for (virtualFile in com.intellij.util.indexing.FileBasedIndex.getInstance().getContainingFiles(RiscVExportIndex.ID, use.name, scope)) {
                    ProgressManager.checkCanceled()
                    if (virtualFile != file.virtualFile && virtualFile.length <= 1_000_000 && (virtualFile.fileType == AssemblyFileType || virtualFile.extension?.lowercase() == "inc")) {
                        val candidate = PsiManager.getInstance(project).findFile(virtualFile)
                        if (candidate != null && (candidate.language.isKindOf(RiscVLanguage) || virtualFile.extension?.lowercase() == "inc")) {
                            RiscVProgram.get(candidate).let { other -> other.symbols.definitions.filter { it.exported && it.name == use.name }
                                .mapNotNullTo(targets, other::definition) }
                        }
                    }
                }
            } catch (_: IndexNotReadyException) {
                return ResolveResult.EMPTY_ARRAY
            }
            // Prefer actual assembly definitions; otherwise ask the host IDE for source-level symbols.
            if (targets.isEmpty()) targets += SourceSymbolBridge.find(element, use.name)
            return targets.distinct().sortedWith(compareBy({ it.containingFile?.virtualFile?.url }, { it.textOffset }))
                .map { PsiElementResolveResult(it) }.toTypedArray()
        }
        override fun getVariants(): Array<Any> = emptyArray()
        override fun handleElementRename(newElementName: String): PsiElement {
            val leaf = element.containingFile.findElementAt(element.textRange.startOffset + rangeInElement.startOffset)
                ?: return element
            RiscVSymbolDefinition.renameToken(leaf, newElementName)
            return element
        }
    }
}
