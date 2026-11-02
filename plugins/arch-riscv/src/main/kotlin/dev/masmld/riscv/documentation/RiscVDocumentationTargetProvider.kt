package dev.masmld.riscv.documentation

import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil
import dev.masmld.core.documentation.DocumentationEntry
import dev.masmld.core.documentation.DocumentationRenderer
import dev.masmld.riscv.RiscVLanguage
import dev.masmld.riscv.psi.RiscVStatement

class RiscVDocumentationTargetProvider : DocumentationTargetProvider {
    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        if (!file.language.isKindOf(RiscVLanguage) || offset !in 0 until file.textLength) return emptyList()
        return listOfNotNull(targetForElement(file.findElementAt(offset)))
    }
}

class RiscVPsiDocumentationTargetProvider : PsiDocumentationTargetProvider {
    override fun documentationTarget(element: PsiElement, originalElement: PsiElement?): DocumentationTarget? =
        targetForElement(originalElement ?: element)
}

private fun targetForElement(element: PsiElement?): DocumentationTarget? {
    if (element == null || !element.isValid) return null
    val file = element.containingFile ?: return null
    if (!file.language.isKindOf(RiscVLanguage)) return null
    val statement = PsiTreeUtil.getParentOfType(element, RiscVStatement::class.java) ?: return null
    val entry = RiscVDocumentationLookup.at(statement.text, element.textRange.startOffset - statement.textRange.startOffset)
        ?: return null
    if (!dev.masmld.riscv.editing.RiscVIsa.supports(entry.name, dev.masmld.riscv.symbols.RiscVProgram.get(file).targetAt(file, element.textOffset))) return null
    return RiscVDocumentationTarget(element, entry)
}

private class RiscVDocumentationTarget(private val element: PsiElement, private val entry: DocumentationEntry) : DocumentationTarget {
    override fun createPointer(): Pointer<out DocumentationTarget> {
        val pointer = SmartPointerManager.createPointer(element)
        // Resolve the token's role again: after edits a former mnemonic may be a label.
        return Pointer { targetForElement(pointer.element) }
    }
    override fun computePresentation(): TargetPresentation = TargetPresentation.builder(entry.name).presentation()
    override fun computeDocumentation(): DocumentationResult = DocumentationResult.documentation(DocumentationRenderer.render(entry))
    override fun computeDocumentationHint(): String = DocumentationRenderer.render(entry)
}
