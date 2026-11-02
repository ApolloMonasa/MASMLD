package dev.masmld.riscv.parser

import com.intellij.lang.LightPsiParser
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType
import dev.masmld.riscv.symbols.RiscVSymbols
import dev.masmld.riscv.lexer.RiscVTokens as T

/** Statement-level recovery deliberately accepts unknown opcodes and unfinished operands. */
class RiscVParser : PsiParser, LightPsiParser {
    override fun parse(root: IElementType, builder: PsiBuilder): com.intellij.lang.ASTNode {
        parseLight(root, builder)
        return builder.treeBuilt
    }
    override fun parseLight(root: IElementType, builder: PsiBuilder) {
        val definitions = RiscVSymbols.analyze(builder.originalText).definitions.map { it.start }.toSet()
        val file = builder.mark()
        while (!builder.eof()) {
            if (builder.tokenType == T.NEWLINE || builder.tokenType == T.SEPARATOR) {
                builder.advanceLexer()
            } else {
                val statement = builder.mark()
                while (!builder.eof() && builder.tokenType != T.NEWLINE && builder.tokenType != T.SEPARATOR) {
                    if (builder.currentOffset in definitions) {
                        val symbol = builder.mark()
                        builder.advanceLexer()
                        symbol.done(RiscVElements.SYMBOL_DEFINITION)
                    } else builder.advanceLexer()
                }
                statement.done(RiscVElements.STATEMENT)
            }
        }
        file.done(root)
    }
}
