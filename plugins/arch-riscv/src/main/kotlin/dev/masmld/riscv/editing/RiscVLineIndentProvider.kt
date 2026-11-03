package dev.masmld.riscv.editing

import com.intellij.application.options.CodeStyle
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.lineIndent.LineIndentProvider
import dev.masmld.riscv.RiscVLanguage

class RiscVLineIndentProvider : LineIndentProvider {
    override fun isSuitableFor(language: Language?): Boolean =
        language?.isKindOf(RiscVLanguage) == true

    override fun getLineIndent(project: Project, editor: Editor, language: Language?, offset: Int): String? {
        if (!isSuitableFor(language)) return null

        val document = editor.document
        val line = document.getLineNumber(offset)
        if (line == 0) return ""

        val previousLine = document.charsSequence.subSequence(
            document.getLineStartOffset(line - 1),
            document.getLineEndOffset(line - 1)
        ).toString()
        val currentIndent = previousLine.takeWhile { it == ' ' || it == '\t' }
        val directive = previousLine.drop(currentIndent.length).takeWhile { !it.isWhitespace() && it != '#' }
        return when (directive) {
            ".macro" -> {
                val options = CodeStyle.getIndentOptions(project, document)
                val indentSize = options.INDENT_SIZE.coerceAtLeast(0)
                val tabSize = options.TAB_SIZE.coerceAtLeast(1)
                if (options.USE_TAB_CHARACTER) {
                    val column = currentIndent.fold(0) { width, character ->
                        if (character == '\t') width + tabSize - width % tabSize else width + 1
                    } + indentSize
                    "\t".repeat(column / tabSize) + " ".repeat(column % tabSize)
                } else {
                    currentIndent + " ".repeat(indentSize)
                }
            }
            ".endm" -> ""
            else -> currentIndent
        }
    }
}
