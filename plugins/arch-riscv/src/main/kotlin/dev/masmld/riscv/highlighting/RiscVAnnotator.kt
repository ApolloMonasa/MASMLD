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
        for (highlight in RiscVClassifier.classify(element.text)) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(TextRange(offset + highlight.start, offset + highlight.end))
                .textAttributes(RiscVColors.forRole(highlight.role))
                .create()
        }
    }
}
