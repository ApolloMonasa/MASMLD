package dev.masmld.ld.lexer

import com.intellij.psi.tree.IElementType
import dev.masmld.ld.LdLanguage

object LdTokens {
    val IDENTIFIER = token("IDENTIFIER")
    val NUMBER = token("NUMBER")
    val STRING = token("STRING")
    val COMMENT = token("COMMENT")
    val HASH_LINE = token("HASH_LINE")
    val NEWLINE = token("NEWLINE")
    val DOT = token("DOT")
    val DISCARD = token("DISCARD")
    val LBRACE = token("LBRACE")
    val RBRACE = token("RBRACE")
    val LPAREN = token("LPAREN")
    val RPAREN = token("RPAREN")
    val SEMICOLON = token("SEMICOLON")
    val COMMA = token("COMMA")
    val COLON = token("COLON")
    val OPERATOR = token("OPERATOR")
    val COMMAND = token("COMMAND")
    val FUNCTION = token("FUNCTION")
    val SECTION_NAME = token("SECTION_NAME")
    private fun token(name: String) = IElementType("LD_$name", LdLanguage)
}
