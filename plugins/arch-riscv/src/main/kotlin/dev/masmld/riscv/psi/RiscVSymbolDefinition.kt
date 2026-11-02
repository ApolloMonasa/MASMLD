package dev.masmld.riscv.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import dev.masmld.core.icons.AssemblyIcons
import dev.masmld.riscv.RiscVLanguage
import dev.masmld.riscv.lexer.RiscVTokens
import com.intellij.psi.tree.TokenSet

/** Exact identifier node, so navigation lands on the label rather than the whole statement. */
class RiscVSymbolDefinition(node: ASTNode) : ASTWrapperPsiElement(node), PsiNameIdentifierOwner {
    override fun getNameIdentifier(): PsiElement? = node.findChildByType(TokenSet.create(RiscVTokens.IDENTIFIER, RiscVTokens.NUMBER))?.psi
    override fun getName(): String? = nameIdentifier?.text
    override fun getTextOffset(): Int = nameIdentifier?.textRange?.startOffset ?: super.getTextOffset()
    override fun getUseScope(): com.intellij.psi.search.SearchScope = if (name?.all(Char::isDigit) == true) com.intellij.psi.search.LocalSearchScope(containingFile) else super.getUseScope()
    override fun getIcon(flags: Int) = AssemblyIcons.FILE
    override fun setName(name: String): PsiElement {
        val identifier = nameIdentifier ?: return this
        if (identifier.text.all(Char::isDigit)) throw IncorrectOperationException("Numeric local label rename is not supported")
        renameToken(identifier, name)
        return this
    }
    companion object {
        fun renameToken(token: PsiElement, name: String) {
            if (!name.matches(Regex("[\\p{L}_.$][\\p{L}\\p{N}_.$]*"))) throw IncorrectOperationException("Invalid assembly symbol name")
            val temporary = PsiFileFactory.getInstance(token.project).createFileFromText("symbol.s", RiscVLanguage, "$name:")
            val replacement = PsiTreeUtil.findChildOfType(temporary, RiscVSymbolDefinition::class.java)?.nameIdentifier
                ?: throw IncorrectOperationException("Cannot create assembly symbol")
            token.replace(replacement)
        }
    }
}
