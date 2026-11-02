package dev.masmld.riscv.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import dev.masmld.riscv.psi.RiscVStatement

class RiscVAnnotator : Annotator, DumbAware {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is RiscVStatement) return
        val offset = element.textRange.startOffset
        val file = element.containingFile
        // Macro body parameters and generated labels are intentionally excluded from diagnostics.
        for (problem in dev.masmld.riscv.editing.RiscVFileDiagnostics.get(file).filter { it.start >= offset && it.end <= element.textRange.endOffset }) {
            val annotation = holder.newAnnotation(HighlightSeverity.WARNING, problem.message).range(TextRange(problem.start, problem.end))
            problem.requiredTarget?.let { annotation.withFix(dev.masmld.riscv.editing.EnableIsaFix(it)) }
            annotation.create()
        }
        if (!com.intellij.openapi.project.DumbService.isDumb(element.project)) {
            for (reference in element.references) {
                val targets = if (reference is com.intellij.psi.PsiPolyVariantReference) reference.multiResolve(false).map { it.element } else listOfNotNull(reference.resolve())
                if (targets.isEmpty() && !reference.canonicalText.matches(Regex("[xf][0-9]+")) && !element.text.trimStart().startsWith(".globl") && !element.text.trimStart().startsWith(".global") && !element.text.trimStart().startsWith(".weak") && !element.text.trimStart().startsWith("csr")) {
                    holder.newAnnotation(HighlightSeverity.WEAK_WARNING, "未找到可导航的定义：${reference.canonicalText}（可能由预处理器、外部目标文件或链接器提供）")
                        .range(reference.rangeInElement.shiftRight(offset)).create()
                }
            }
        }
        for (highlight in RiscVClassifier.classify(element.text)) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(TextRange(offset + highlight.start, offset + highlight.end))
                .textAttributes(RiscVColors.forRole(highlight.role))
                .create()
        }
    }
}
