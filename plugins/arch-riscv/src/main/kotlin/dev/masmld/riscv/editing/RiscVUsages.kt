package dev.masmld.riscv.editing

import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.psi.*
import com.intellij.psi.tree.TokenSet
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.util.containers.MultiMap
import dev.masmld.riscv.psi.RiscVSymbolDefinition
import dev.masmld.riscv.lexer.RiscVLexer
import dev.masmld.riscv.lexer.RiscVTokens as T

class RiscVUsages : FindUsagesProvider {
    override fun getWordsScanner() = DefaultWordsScanner(RiscVLexer(), TokenSet.create(T.IDENTIFIER), TokenSet.create(T.LINE_COMMENT, T.BLOCK_COMMENT), TokenSet.create(T.STRING))
    override fun canFindUsagesFor(element: PsiElement) = element is RiscVSymbolDefinition
    override fun getHelpId(element: PsiElement): String? = null
    override fun getType(element: PsiElement) = "symbol"
    override fun getDescriptiveName(element: PsiElement) = (element as? PsiNamedElement)?.name.orEmpty()
    override fun getNodeText(element: PsiElement, useFullName: Boolean) = getDescriptiveName(element)
}
class RiscVRename : RenamePsiElementProcessor() {
    override fun canProcessElement(element: PsiElement) = element is RiscVSymbolDefinition
    override fun renameElement(element: PsiElement, newName: String, usages: Array<com.intellij.usageView.UsageInfo>, listener: com.intellij.refactoring.listeners.RefactoringElementListener?) {
        if ((element as RiscVSymbolDefinition).name?.all(Char::isDigit) == true) throw com.intellij.util.IncorrectOperationException("Numeric local labels cannot be renamed")
        val own = dev.masmld.riscv.symbols.RiscVSymbolReferences.symbols(element.containingFile)
            .definitions.firstOrNull { it.start == element.textOffset }
        if (own?.macro == true && usages.any { usage ->
                (usage.reference as? PsiPolyVariantReference)?.multiResolve(false)?.size?.let { it > 1 } == true
            }) {
            throw com.intellij.util.IncorrectOperationException("宏调用可能绑定到多个条件分支，无法安全重命名")
        }
        super.renameElement(element, newName, usages, listener)
    }
    override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>) {
        val model = dev.masmld.riscv.symbols.RiscVSymbols.analyze(element.containingFile.text)
        val own = model.definitions.firstOrNull { it.start == element.textOffset } ?: return
        if (!own.macro) model.definitions.filter { !it.macro && it.name == own.name }.forEach { def ->
            element.containingFile.findElementAt(def.start)?.parent?.takeIf { it is RiscVSymbolDefinition }?.let { allRenames[it] = newName }
        }
    }
    override fun findExistingNameConflicts(element: PsiElement, newName: String, conflicts: MultiMap<PsiElement, String>) {
        val named = element as RiscVSymbolDefinition
        if (newName == named.name) return
        val definitions = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(element.containingFile, RiscVSymbolDefinition::class.java)
        definitions.filter { it != element && it.name == newName }.forEach { conflicts.putValue(it, "该名称已有定义：$newName") }
        val own = dev.masmld.riscv.symbols.RiscVSymbols.analyze(element.containingFile.text).definitions.firstOrNull { it.start == element.textOffset }
        if (own?.exported == true && !com.intellij.openapi.project.DumbService.isDumb(element.project)) {
            val scope = com.intellij.psi.search.GlobalSearchScope.projectScope(element.project)
            com.intellij.util.indexing.FileBasedIndex.getInstance().getContainingFiles(dev.masmld.riscv.symbols.RiscVExportIndex.ID, newName, scope).filter { it != element.containingFile.virtualFile }.forEach { file ->
                com.intellij.psi.PsiManager.getInstance(element.project).findFile(file)?.let { conflicts.putValue(it, "项目中已有导出符号：$newName") }
            }
        }
        if (newName.matches(Regex("[0-9]+"))) conflicts.putValue(element, "不支持将符号重命名为数字局部标签")
    }
    override fun forcesShowPreview() = true
}
