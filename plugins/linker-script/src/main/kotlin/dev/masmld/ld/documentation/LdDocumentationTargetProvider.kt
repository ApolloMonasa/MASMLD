package dev.masmld.ld.documentation

import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import dev.masmld.core.documentation.DocumentationEntry
import dev.masmld.core.documentation.DocumentationRenderer
import dev.masmld.ld.LdLanguage

class LdDocumentationTargetProvider : DocumentationTargetProvider {
    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        if (file.language != LdLanguage || offset !in 0 until file.textLength) return emptyList()
        return listOfNotNull(targetForElement(file.findElementAt(offset)))
    }
}
class LdPsiDocumentationTargetProvider : PsiDocumentationTargetProvider {
    override fun documentationTarget(element: PsiElement, originalElement: PsiElement?): DocumentationTarget? = targetForElement(originalElement ?: element)
}
private fun targetForElement(element: PsiElement?): DocumentationTarget? {
    if (element == null || !element.isValid) return null
    val file = element.containingFile ?: return null
    if (file.language != LdLanguage) return null
    val entry = LdDocumentationLookup.at(file.text, element.textRange.startOffset) ?: return null
    return LdDocumentationTarget(element, entry)
}
private class LdDocumentationTarget(private val element: PsiElement, private val entry: DocumentationEntry) : DocumentationTarget {
    override fun createPointer(): Pointer<out DocumentationTarget> {
        val pointer = SmartPointerManager.createPointer(element)
        return Pointer { targetForElement(pointer.element) }
    }
    override fun computePresentation(): TargetPresentation = TargetPresentation.builder(entry.name).presentation()
    override fun computeDocumentation(): DocumentationResult = DocumentationResult.documentation(DocumentationRenderer.render(entry))
    override fun computeDocumentationHint(): String = DocumentationRenderer.render(entry)
}
