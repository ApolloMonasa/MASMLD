package dev.masmld.riscv.parser

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import dev.masmld.core.language.AssemblyFileType
import dev.masmld.riscv.RiscVLanguage
import dev.masmld.riscv.lexer.RiscVLexer
import dev.masmld.riscv.lexer.RiscVTokens as T
import dev.masmld.riscv.psi.RiscVStatement
import dev.masmld.riscv.psi.RiscVSymbolDefinition

object RiscVElements {
    val FILE = IFileElementType(RiscVLanguage)
    val SYMBOL_DEFINITION = IElementType("RISCV_SYMBOL_DEFINITION", RiscVLanguage)
    val STATEMENT = IElementType("RISCV_STATEMENT", RiscVLanguage)
}

class RiscVParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?) = RiscVLexer()
    override fun createParser(project: Project?) = RiscVParser()
    override fun getFileNodeType() = RiscVElements.FILE
    override fun getWhitespaceTokens() = TokenSet.create(TokenType.WHITE_SPACE)
    override fun getCommentTokens() = TokenSet.create(T.LINE_COMMENT, T.BLOCK_COMMENT)
    override fun getStringLiteralElements() = TokenSet.create(T.STRING)
    override fun createElement(node: ASTNode) = if (node.elementType == RiscVElements.SYMBOL_DEFINITION) RiscVSymbolDefinition(node) else RiscVStatement(node)
    override fun createFile(viewProvider: FileViewProvider) = object : PsiFileBase(viewProvider, RiscVLanguage) {
        override fun getFileType() = AssemblyFileType
    }
}
