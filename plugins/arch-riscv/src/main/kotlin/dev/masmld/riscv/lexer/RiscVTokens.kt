package dev.masmld.riscv.lexer

import com.intellij.psi.tree.IElementType
import dev.masmld.riscv.RiscVLanguage

object RiscVTokens {
    val IDENTIFIER = token("IDENTIFIER")
    val NUMBER = token("NUMBER")
    val LOCAL_REFERENCE = token("LOCAL_REFERENCE")
    val STRING = token("STRING")
    val CHARACTER = token("CHARACTER")
    val LINE_COMMENT = token("LINE_COMMENT")
    val BLOCK_COMMENT = token("BLOCK_COMMENT")
    val NEWLINE = token("NEWLINE")
    val COLON = token("COLON")
    val COMMA = token("COMMA")
    val LPAREN = token("LPAREN")
    val RPAREN = token("RPAREN")
    val SEPARATOR = token("SEPARATOR")
    val OPERATOR = token("OPERATOR")
    val MACRO_PARAMETER = token("MACRO_PARAMETER")
    private fun token(name: String) = IElementType("RISCV_$name", RiscVLanguage)
}
