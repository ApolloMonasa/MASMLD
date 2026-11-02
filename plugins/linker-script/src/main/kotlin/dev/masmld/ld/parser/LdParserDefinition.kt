package dev.masmld.ld.parser

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.lang.LightPsiParser
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import dev.masmld.ld.LdLanguage
import dev.masmld.ld.LdFileType
import dev.masmld.ld.lexer.LdLexer
import dev.masmld.ld.lexer.LdTokens as T

object LdElements {
    val FILE = IFileElementType(LdLanguage)
    val BLOCK = IElementType("LD_BLOCK", LdLanguage)
    val STATEMENT = IElementType("LD_STATEMENT", LdLanguage)
}
class LdParser : PsiParser, LightPsiParser {
    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        parseLight(root, builder)
        return builder.treeBuilt
    }
    override fun parseLight(root: IElementType, builder: PsiBuilder) {
        val file = builder.mark()
        val blocks = ArrayDeque<PsiBuilder.Marker>()
        while (!builder.eof()) {
            when (builder.tokenType) {
                T.LBRACE -> { blocks.addLast(builder.mark()); builder.advanceLexer() }
                T.RBRACE -> {
                    builder.advanceLexer()
                    if (blocks.isNotEmpty()) blocks.removeLast().done(LdElements.BLOCK)
                }
                T.NEWLINE, T.SEMICOLON -> builder.advanceLexer()
                else -> {
                    val clause = builder.mark()
                    while (!builder.eof() && builder.tokenType !in boundaries) builder.advanceLexer()
                    clause.done(LdElements.STATEMENT)
                }
            }
        }
        // Incomplete blocks remain editable and do not hide later tokens behind syntax errors.
        while (blocks.isNotEmpty()) blocks.removeLast().done(LdElements.BLOCK)
        file.done(root)
    }
    companion object { private val boundaries = setOf(T.LBRACE, T.RBRACE, T.NEWLINE, T.SEMICOLON) }
}
class LdParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?) = LdLexer()
    override fun createParser(project: Project?) = LdParser()
    override fun getFileNodeType() = LdElements.FILE
    override fun getWhitespaceTokens() = TokenSet.create(TokenType.WHITE_SPACE)
    override fun getCommentTokens() = TokenSet.create(T.COMMENT, T.HASH_LINE)
    override fun getStringLiteralElements() = TokenSet.create(T.STRING)
    override fun createElement(node: ASTNode) = ASTWrapperPsiElement(node)
    override fun createFile(viewProvider: FileViewProvider) = object : PsiFileBase(viewProvider, LdLanguage) {
        override fun getFileType() = LdFileType
    }
}
