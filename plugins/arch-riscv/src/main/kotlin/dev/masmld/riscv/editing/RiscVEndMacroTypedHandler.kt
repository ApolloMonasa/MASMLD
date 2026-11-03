package dev.masmld.riscv.editing

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.actionSystem.ActionPlan
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import com.intellij.openapi.editor.actionSystem.TypedActionHandlerEx
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import dev.masmld.riscv.RiscVLanguage
import dev.masmld.riscv.lexer.RiscVTokens

class RiscVEndMacroTypedHandler(private val original: TypedActionHandler) : TypedActionHandlerEx {
    override fun beforeExecute(editor: Editor, c: Char, context: DataContext, plan: ActionPlan) {
        (original as? TypedActionHandlerEx)?.beforeExecute(editor, c, context, plan)
    }

    override fun execute(editor: Editor, c: Char, context: DataContext) {
        original.execute(editor, c, context)
        if (c != 'm') return
        val project = editor.project ?: return
        val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return
        dedentEndMacro(editor, file, editor.caretModel.offset)
    }
}

internal fun dedentEndMacro(editor: Editor, file: PsiFile, caret: Int) {
    if (!file.language.isKindOf(RiscVLanguage)) return
    val project = file.project
    val document = editor.document
    val lineStart = document.getLineStartOffset(document.getLineNumber(caret))
    val text = document.charsSequence
    var directiveStart = lineStart
    while (directiveStart < caret && (text[directiveStart] == ' ' || text[directiveStart] == '\t')) {
        directiveStart++
    }
    if (directiveStart == lineStart || text.subSequence(directiveStart, caret).toString() != ".endm") {
        return
    }

    PsiDocumentManager.getInstance(project).commitDocument(document)
    val token = file.findElementAt(directiveStart)
    if (token?.node?.elementType == RiscVTokens.IDENTIFIER && token.text == ".endm") {
        runWriteAction { document.deleteString(lineStart, directiveStart) }
    }
}
