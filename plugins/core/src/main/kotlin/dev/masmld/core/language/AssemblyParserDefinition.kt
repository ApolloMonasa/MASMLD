package dev.masmld.core.language

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.lexer.LexerBase
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

/** Tolerant placeholder parser. Architecture syntax arrives in the next milestone. */
open class AssemblyParserDefinition(private val language: Language = AssemblyLanguage) : ParserDefinition {
    private val fileElement = IFileElementType(language)
    override fun createLexer(project: Project?): Lexer = AssemblyTextLexer()
    override fun createParser(project: Project?): PsiParser = PsiParser { root, builder ->
        val marker = builder.mark()
        while (!builder.eof()) builder.advanceLexer()
        marker.done(root)
        builder.treeBuilt
    }
    override fun getFileNodeType() = fileElement
    override fun getWhitespaceTokens(): TokenSet = TokenSet.EMPTY
    override fun getCommentTokens(): TokenSet = TokenSet.EMPTY
    override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY
    override fun createElement(node: ASTNode): PsiElement = ASTWrapperPsiElement(node)
    override fun createFile(viewProvider: FileViewProvider): PsiFile = object : PsiFileBase(viewProvider, language) {
        override fun getFileType() = AssemblyFileType
    }
}

/** Preserves all source text without claiming any architecture-specific syntax. */
private class AssemblyTextLexer : LexerBase() {
    private var text: CharSequence = ""
    private var start = 0
    private var end = 0
    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        text = buffer
        start = startOffset
        end = endOffset
    }
    override fun getState() = 0
    override fun getTokenType(): IElementType? = if (start < end) TEXT else null
    override fun getTokenStart() = start
    override fun getTokenEnd() = end
    override fun advance() { start = end }
    override fun getBufferSequence() = text
    override fun getBufferEnd() = end
    companion object { private val TEXT = IElementType("ASSEMBLY_TEXT", AssemblyLanguage) }
}
