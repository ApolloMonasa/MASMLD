package dev.masmld.ld.documentation

import dev.masmld.core.documentation.DocumentationEntry
import dev.masmld.ld.highlighting.LdTokenRoles
import dev.masmld.ld.lexer.LdLexer

object LdDocumentationLookup {
    fun at(source: CharSequence, offset: Int): DocumentationEntry? {
        if (offset !in 0 until source.length) return null
        val lexer = LdLexer()
        lexer.start(source)
        while (lexer.tokenType != null) {
            if (offset >= lexer.tokenStart && offset < lexer.tokenEnd) {
                val kind = LdTokenRoles.documentationKind(source, lexer.tokenStart, lexer.tokenEnd, lexer.tokenType!!) ?: return null
                return LdDocumentationCatalog.find(kind, source.subSequence(lexer.tokenStart, lexer.tokenEnd).toString())
            }
            lexer.advance()
        }
        return null
    }
}
