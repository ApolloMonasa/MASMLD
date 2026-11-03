package dev.masmld.riscv.editing

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import dev.masmld.riscv.RiscVLanguage
import dev.masmld.riscv.lexer.RiscVTokens

class RiscVEndMacroTypedHandler : TypedHandlerDelegate() {
    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (c != 'm' || !file.language.isKindOf(RiscVLanguage)) return Result.CONTINUE

        val document = editor.document
        val caret = editor.caretModel.offset
        val lineStart = document.getLineStartOffset(document.getLineNumber(caret))
        val text = document.charsSequence
        var directiveStart = lineStart
        while (directiveStart < caret && (text[directiveStart] == ' ' || text[directiveStart] == '\t')) {
            directiveStart++
        }
        if (directiveStart == lineStart || text.subSequence(directiveStart, caret).toString() != ".endm") {
            return Result.CONTINUE
        }

        PsiDocumentManager.getInstance(project).commitDocument(document)
        val token = file.findElementAt(directiveStart)
        if (token?.node?.elementType == RiscVTokens.IDENTIFIER && token.text == ".endm") {
            document.deleteString(lineStart, directiveStart)
        }
        return Result.CONTINUE
    }
}
