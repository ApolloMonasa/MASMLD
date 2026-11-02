package dev.masmld.riscv.editing

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiReference
import com.intellij.psi.search.PsiSearchScopeUtil
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.intellij.openapi.application.QueryExecutorBase
import dev.masmld.riscv.psi.*

/** The ordinary word index cannot search definition "1" using the spelled references "1f"/"1b". */
class RiscVNumericUsages : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {
    override fun processQuery(parameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
        val target = parameters.elementToSearch as? RiscVSymbolDefinition ?: return
        if (target.name?.all(Char::isDigit) != true) return
        for (statement in PsiTreeUtil.findChildrenOfType(target.containingFile, RiscVStatement::class.java)) {
            ProgressManager.checkCanceled()
            if (!PsiSearchScopeUtil.isInScope(parameters.effectiveSearchScope, statement)) continue
            for (reference in statement.references) if (reference.isReferenceTo(target) && !consumer.process(reference)) return
        }
    }
}
