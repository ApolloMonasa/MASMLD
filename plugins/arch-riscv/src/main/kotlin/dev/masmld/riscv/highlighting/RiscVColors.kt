package dev.masmld.riscv.highlighting

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as D
import com.intellij.openapi.editor.colors.TextAttributesKey

object RiscVColors {
    private fun key(name: String, fallback: TextAttributesKey) = TextAttributesKey.createTextAttributesKey("MASMLD_RISCV_$name", fallback)
    val INSTRUCTION = key("INSTRUCTION", D.KEYWORD)
    val PSEUDO_INSTRUCTION = key("PSEUDO_INSTRUCTION", D.FUNCTION_CALL)
    val DIRECTIVE = key("DIRECTIVE", D.METADATA)
    val REGISTER = key("REGISTER", D.PREDEFINED_SYMBOL)
    val LABEL = key("LABEL", D.LABEL)
    val NUMBER = key("NUMBER", D.NUMBER)
    val STRING = key("STRING", D.STRING)
    val LINE_COMMENT = key("LINE_COMMENT", D.LINE_COMMENT)
    val BLOCK_COMMENT = key("BLOCK_COMMENT", D.BLOCK_COMMENT)
    val OPERATOR = key("OPERATOR", D.OPERATION_SIGN)
    val PARENTHESES = key("PARENTHESES", D.PARENTHESES)
    val COMMA = key("COMMA", D.COMMA)
    val MACRO_PARAMETER = key("MACRO_PARAMETER", D.PARAMETER)
    fun forRole(role: RiscVRole): TextAttributesKey = when (role) {
        RiscVRole.INSTRUCTION -> INSTRUCTION
        RiscVRole.PSEUDO_INSTRUCTION -> PSEUDO_INSTRUCTION
        RiscVRole.DIRECTIVE -> DIRECTIVE
        RiscVRole.REGISTER -> REGISTER
        RiscVRole.LABEL -> LABEL
    }
}
