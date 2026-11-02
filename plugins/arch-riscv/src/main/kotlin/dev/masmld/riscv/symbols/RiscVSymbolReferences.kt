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
        return symbols(statement.containingFile).references.filter { it.start >= range.startOffset && it.end <= range.endOffset }
            .map { SymbolReference(statement, it) }.toTypedArray()
    }
    private fun declaration(file: PsiFile, definition: AssemblySymbolDefinition): RiscVSymbolDefinition? {
        val leaf = file.findElementAt(definition.start) ?: return null
        return leaf.parent as? RiscVSymbolDefinition
    }
    private class SymbolReference(statement: RiscVStatement, private val use: AssemblySymbolReference) :
        PsiPolyVariantReferenceBase<RiscVStatement>(statement,
            TextRange(use.start - statement.textRange.startOffset, use.end - statement.textRange.startOffset), true) {
        override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
            val file = element.containingFile
            val local = symbols(file).resolve(use).mapNotNull { declaration(file, it) }
            if (use.macroTarget != null || local.isNotEmpty() || use.name.matches(Regex("[0-9]+[fb]")) || use.name.startsWith(".L")) {
                return local.map { PsiElementResolveResult(it) }.toTypedArray()
            }
            val project = element.project
            if (DumbService.isDumb(project)) return ResolveResult.EMPTY_ARRAY
            val word = Regex("[\\p{L}\\p{N}_]+").findAll(use.name).map { it.value }.maxByOrNull { it.length }
                ?: return ResolveResult.EMPTY_ARRAY
            val targets = mutableListOf<PsiElement>()
            val scope = GlobalSearchScope.getScopeRestrictedByFileTypes(GlobalSearchScope.projectScope(project), AssemblyFileType)
            try {
                // Use the word index to narrow candidates; do not scan every assembly file on hover.
                PsiSearchHelper.getInstance(project).processCandidateFilesForText(scope, UsageSearchContext.ANY, true, word) { virtualFile ->
                    ProgressManager.checkCanceled()
                    if (virtualFile != file.virtualFile) {
                        val candidate = PsiManager.getInstance(project).findFile(virtualFile)
                        if (candidate != null && candidate.language.isKindOf(RiscVLanguage)) {
                            symbols(candidate).definitions.filter { it.exported && it.name == use.name }
                                .mapNotNullTo(targets) { declaration(candidate, it) }
                        }
                    }
                    true
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
