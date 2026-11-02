package dev.masmld.riscv.highlighting

import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import dev.masmld.riscv.lexer.RiscVLexer
import dev.masmld.riscv.lexer.RiscVTokens as T

class RiscVSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer() = RiscVLexer()
    override fun getTokenHighlights(tokenType: IElementType) = pack(when (tokenType) {
        T.NUMBER, T.CHARACTER, T.LOCAL_REFERENCE -> RiscVColors.NUMBER
        T.STRING -> RiscVColors.STRING
        T.LINE_COMMENT -> RiscVColors.LINE_COMMENT
        T.BLOCK_COMMENT -> RiscVColors.BLOCK_COMMENT
        T.OPERATOR, T.COLON, T.SEPARATOR -> RiscVColors.OPERATOR
        T.LPAREN, T.RPAREN -> RiscVColors.PARENTHESES
        T.COMMA -> RiscVColors.COMMA
        T.MACRO_PARAMETER -> RiscVColors.MACRO_PARAMETER
        TokenType.BAD_CHARACTER -> HighlighterColors.BAD_CHARACTER
        else -> null
    })
}

class RiscVSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?) = RiscVSyntaxHighlighter()
}
