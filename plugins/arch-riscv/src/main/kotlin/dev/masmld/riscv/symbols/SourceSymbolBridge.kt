package dev.masmld.riscv.symbols

import com.intellij.navigation.ChooseByNameContributor
import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.ChooseByNameContributorEx2
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import com.intellij.util.indexing.FindSymbolParameters
import dev.masmld.core.language.AssemblyLanguage

/** Uses the host's public symbol APIs; no Rust, C/C++ or Java plugin classes are referenced. */
object SourceSymbolBridge {
    fun find(origin: PsiElement, name: String): List<PsiElement> = find(origin, name, emptySet())
    private fun find(origin: PsiElement, name: String, visited: Set<String>): List<PsiElement> {
        if (name in visited || visited.size > 8) return emptyList()
        dev.masmld.core.binary.BinarySymbols.get(origin.project).target(origin, name)?.let { return listOf(it) }
        val project = origin.project
        if (DumbService.isDumb(project)) return emptyList()
        val scope = GlobalSearchScope.projectScope(project)
        val parameters = FindSymbolParameters(name, name, scope)
        val results = mutableListOf<PsiElement>()
        val processor = Processor<NavigationItem> { item ->
            ProgressManager.checkCanceled()
            if (item is PsiElement) {
                val file = item.containingFile
                val virtualFile = file?.virtualFile
                if (file == null || virtualFile == null || !scope.contains(virtualFile) || file.language.isKindOf(AssemblyLanguage)) return@Processor true
            }
            adapt(origin, name, item)?.let { results += it }
            true
        }
        for (contributor in ChooseByNameContributor.SYMBOL_EP_NAME.extensionList) {
            ProgressManager.checkCanceled()
            try {
                if (contributor is ChooseByNameContributorEx) query(contributor, name, parameters, processor)
                else contributor.getItemsByName(name, name, project, false).forEach { processor.process(it) }
            } catch (_: IndexNotReadyException) {
                // Indexing may begin during a query. Existing local assembly targets remain usable.
            }
        }
        if (results.isEmpty()) {
            val word = Regex("[A-Za-z0-9_]+").findAll(name).maxByOrNull { it.value.length }?.value
            if (word != null) com.intellij.psi.search.PsiSearchHelper.getInstance(project).processCandidateFilesForText(scope, com.intellij.psi.search.UsageSearchContext.ANY, true, word) { vf ->
                ProgressManager.checkCanceled()
                if (vf.length <= 1_000_000 && vf.extension?.lowercase() in setOf("rs", "c", "h", "cpp", "cc", "cxx", "hpp", "ld")) {
                    val file = com.intellij.psi.PsiManager.getInstance(project).findFile(vf)
                    if (file != null) SourceAliases.analyze(file.text, vf.extension == "rs", vf.extension == "ld").filter { it.external == name }.forEach { alias ->
                        if (vf.extension == "ld") {
                            if (dev.masmld.core.source.SourceText.tokens(file.text).none { it.kind == dev.masmld.core.source.SourceToken.Kind.STRING && alias.offset in it.start until it.end }) results += find(origin, alias.source, visited + name)
                        } else if (file.findElementAt(alias.offset)?.text == alias.source) results += dev.masmld.core.source.IncludeFiles.target(file, alias.offset, alias.source)
                    }
                }
                true
            }
        }
        if (results.isEmpty()) SymbolNames.sourceName(name)?.substringAfterLast("::")?.let { decoded ->
            if (decoded != name) results += find(origin, decoded, visited + name)
        }
        return results.distinct()
    }

    internal fun query(contributor: ChooseByNameContributorEx, name: String, parameters: FindSymbolParameters, processor: Processor<NavigationItem>) {
        // CLion's clangd Ex2 contributor fills its name/result cache in this first phase.
        // Direct indexed contributors, such as Rust's, do not need a full name enumeration.
        if (contributor is ChooseByNameContributorEx2) {
            val indexedNames = linkedSetOf<String>()
            contributor.processNames(Processor { indexedName ->
                if (matchesName(name, indexedName)) indexedNames += indexedName
                true
            }, parameters)
            indexedNames.forEach { contributor.processElementsWithName(it, processor, parameters) }
        } else contributor.processElementsWithName(name, processor, parameters)
    }
    internal fun matchesName(requested: String, indexed: String?): Boolean =
        indexed?.substringBefore('(')?.substringAfterLast("::")?.trim() == requested

    internal fun adapt(origin: PsiElement, name: String, item: NavigationItem): PsiElement? {
        if (!matchesName(name, item.name) || !item.canNavigate()) return null
        if (item is PsiElement) return if (item.isValid) item.navigationElement else null
        // CLion can return a backend navigation item rather than a PSI declaration.
        return SourceNavigationTarget(origin, name, item)
    }
}

private class SourceNavigationTarget(private val origin: PsiElement, private val symbolName: String, private val item: NavigationItem) : FakePsiElement() {
    override fun getParent(): PsiElement = origin
    override fun getContainingFile(): PsiFile? = null
    override fun getName() = symbolName
    override fun getPresentableText() = item.presentation?.presentableText ?: symbolName
    override fun getLocationString(): String? = item.presentation?.locationString
    override fun getIcon(open: Boolean) = item.presentation?.getIcon(open)
    override fun isValid() = origin.isValid && item.canNavigate()
    override fun canNavigate() = item.canNavigate()
    override fun canNavigateToSource() = item.canNavigateToSource()
    override fun navigate(requestFocus: Boolean) = item.navigate(requestFocus)
    override fun equals(other: Any?) = other is SourceNavigationTarget && item == other.item
    override fun hashCode() = item.hashCode()
}
