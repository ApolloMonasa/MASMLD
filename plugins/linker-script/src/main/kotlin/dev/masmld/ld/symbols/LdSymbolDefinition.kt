package dev.masmld.ld.symbols

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import dev.masmld.ld.LdLanguage

class LdSymbolDefinition(node: ASTNode) : ASTWrapperPsiElement(node), PsiNameIdentifierOwner {
    override fun getNameIdentifier(): PsiElement? = firstChild
    override fun getName() = nameIdentifier?.text?.trim('"')
    override fun getTextOffset() = nameIdentifier?.let { it.textOffset + if (it.text.startsWith('"')) 1 else 0 } ?: super.getTextOffset()
    override fun getIcon(flags: Int) = containingFile.fileType.icon
    override fun setName(name: String): PsiElement { nameIdentifier?.let { renameToken(it, name) }; return this }
    companion object {
        fun renameToken(token: PsiElement, name: String) {
            if (name == "." || !name.matches(Regex("[\\p{L}_.$][\\p{L}\\p{N}_.$-]*"))) throw IncorrectOperationException("Invalid GNU ld name")
            val source = if (token.text.startsWith('"')) "REGION_ALIAS(\"$name\", RAM);" else "$name = 0;"
            val temporary = PsiFileFactory.getInstance(token.project).createFileFromText("symbol.ld", LdLanguage, source)
            val replacement = PsiTreeUtil.findChildOfType(temporary, LdSymbolDefinition::class.java)?.nameIdentifier ?: throw IncorrectOperationException("Cannot create symbol")
            token.replace(replacement)
        }
    }
}
