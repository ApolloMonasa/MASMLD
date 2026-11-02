package dev.masmld.ld.highlighting

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as D
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import dev.masmld.ld.lexer.LdHighlightingLexer
import dev.masmld.ld.lexer.LdTokens as T

object LdColors {
    private fun key(name: String, fallback: TextAttributesKey) = TextAttributesKey.createTextAttributesKey("MASMLD_LD_$name", fallback)
    val COMMAND = key("COMMAND", D.KEYWORD)
    val FUNCTION = key("FUNCTION", D.FUNCTION_CALL)
    val SECTION = key("SECTION", D.LABEL)
    val LOCATION = key("LOCATION", D.PREDEFINED_SYMBOL)
    val NUMBER = key("NUMBER", D.NUMBER)
    val STRING = key("STRING", D.STRING)
    val COMMENT = key("COMMENT", D.BLOCK_COMMENT)
    val HASH_LINE = key("HASH_LINE", D.LINE_COMMENT)
    val OPERATOR = key("OPERATOR", D.OPERATION_SIGN)
    val BRACES = key("BRACES", D.BRACES)
    val PARENTHESES = key("PARENTHESES", D.PARENTHESES)
    val PUNCTUATION = key("PUNCTUATION", D.COMMA)
}
class LdSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer() = LdHighlightingLexer()
    override fun getTokenHighlights(tokenType: IElementType) = pack(when (tokenType) {
        T.COMMAND -> LdColors.COMMAND
        T.FUNCTION -> LdColors.FUNCTION
        T.SECTION_NAME -> LdColors.SECTION
        T.DOT -> LdColors.LOCATION
        T.NUMBER -> LdColors.NUMBER
        T.STRING -> LdColors.STRING
        T.COMMENT -> LdColors.COMMENT
        T.HASH_LINE -> LdColors.HASH_LINE
        T.OPERATOR -> LdColors.OPERATOR
        T.LBRACE, T.RBRACE -> LdColors.BRACES
        T.LPAREN, T.RPAREN -> LdColors.PARENTHESES
        T.COMMA, T.COLON, T.SEMICOLON -> LdColors.PUNCTUATION
        TokenType.BAD_CHARACTER -> HighlighterColors.BAD_CHARACTER
        else -> null
    })
}
class LdSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?) = LdSyntaxHighlighter()
}
