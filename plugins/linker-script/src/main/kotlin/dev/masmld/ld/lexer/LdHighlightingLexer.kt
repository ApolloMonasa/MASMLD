package dev.masmld.ld.lexer

import com.intellij.lexer.LexerBase
import dev.masmld.core.documentation.DocumentationKind as K
import dev.masmld.ld.documentation.LdDocumentationCatalog as Catalog
import dev.masmld.ld.lexer.LdTokens as T

class LdHighlightingLexer : LexerBase() {
    private val raw = LdLexer()
    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) = raw.start(buffer, startOffset, endOffset, initialState)
    override fun getState() = raw.state
    override fun getTokenType(): com.intellij.psi.tree.IElementType? {
        val token = raw.tokenType ?: return null
        // Color reserved vocabulary without trailing lookahead, so suffix edits cannot
        // leave a cached earlier token with the wrong lexical attributes.
        if (token == T.DISCARD) return T.COMMAND
        if (token != T.IDENTIFIER) return token
        val name = raw.tokenText
        return when {
            Catalog.find(K.BUILTIN_FUNCTION, name) != null -> T.FUNCTION
            Catalog.find(K.LINKER_COMMAND, name) != null -> T.COMMAND
            name.startsWith('.') -> T.SECTION_NAME
            else -> token
        }
    }
    override fun getTokenStart() = raw.tokenStart
    override fun getTokenEnd() = raw.tokenEnd
    override fun getBufferSequence() = raw.bufferSequence
    override fun getBufferEnd() = raw.bufferEnd
    override fun advance() = raw.advance()
}
