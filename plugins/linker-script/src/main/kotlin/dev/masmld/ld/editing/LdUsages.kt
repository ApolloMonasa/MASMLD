package dev.masmld.ld.editing

import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.psi.*
import com.intellij.psi.tree.TokenSet
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.util.containers.MultiMap
import dev.masmld.ld.symbols.LdSymbolDefinition
import dev.masmld.ld.lexer.LdLexer
import dev.masmld.ld.lexer.LdTokens as T

class LdUsages : FindUsagesProvider {
    override fun getWordsScanner() = DefaultWordsScanner(LdLexer(), TokenSet.create(T.IDENTIFIER), TokenSet.create(T.COMMENT, T.HASH_LINE), TokenSet.create(T.STRING))
    override fun canFindUsagesFor(element: PsiElement) = element is LdSymbolDefinition && element.name?.all(Char::isDigit) != true
    override fun getHelpId(element: PsiElement): String? = null
    override fun getType(element: PsiElement) = "symbol"
    override fun getDescriptiveName(element: PsiElement) = (element as? PsiNamedElement)?.name.orEmpty()
    override fun getNodeText(element: PsiElement, useFullName: Boolean) = getDescriptiveName(element)
}
class LdRename : RenamePsiElementProcessor() {
    override fun canProcessElement(element: PsiElement) = element is LdSymbolDefinition
    override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>) {
        val definitions = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(element.containingFile, LdSymbolDefinition::class.java)
        val model = dev.masmld.ld.symbols.LdSymbolAnalysis.analyze(element.containingFile.text)
        val own = model.definitions.firstOrNull { it.start == element.textRange.startOffset } ?: return
        val sites = model.definitions.filter { it.name == own.name && it.kind == own.kind }.map { it.start }.toSet()
        definitions.filter { it.textRange.startOffset in sites }.forEach { allRenames[it] = newName }
    }
    override fun findExistingNameConflicts(element: PsiElement, newName: String, conflicts: MultiMap<PsiElement, String>) {
        val named = element as LdSymbolDefinition
        if (newName == named.name) return
        val definitions = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(element.containingFile, LdSymbolDefinition::class.java)
        definitions.filter { it != element && it.name == newName }.forEach { conflicts.putValue(it, "该名称已有定义：$newName") }
        if (newName.matches(Regex("[0-9]+"))) conflicts.putValue(element, "不支持将符号重命名为数字局部标签")
    }
    override fun forcesShowPreview() = true
}
