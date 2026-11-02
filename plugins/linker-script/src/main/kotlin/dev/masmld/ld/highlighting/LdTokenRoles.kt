package dev.masmld.ld.highlighting

import com.intellij.psi.tree.IElementType
import dev.masmld.core.documentation.DocumentationKind as K
import dev.masmld.ld.documentation.LdDocumentationCatalog as Catalog
import dev.masmld.ld.lexer.LdTokens as T

/** Classification is limited to supported GNU keywords; quoted symbols never enter this path. */
object LdTokenRoles {
    private val blockCommands = setOf("MEMORY", "SECTIONS", "PHDRS")
    private val attributes = setOf("NOLOAD", "READONLY")
    fun documentationKind(text: CharSequence, start: Int, end: Int, token: IElementType): K? {
        if (token == T.DOT || token == T.DISCARD) return K.LINKER_COMMAND
        if (token != T.IDENTIFIER) return null
        val name = text.subSequence(start, end).toString()
        val next = nextSignificant(text, end)
        if (name in setOf("ORIGIN", "LENGTH") && next == '=') return K.LINKER_COMMAND
        // A name followed by assignment or a section/region colon is not a command use.
        if (next == '=' || next == ':') return null
        if (Catalog.find(K.BUILTIN_FUNCTION, name) != null && (next == '(' || name == "SIZEOF_HEADERS")) return K.BUILTIN_FUNCTION
        if (Catalog.find(K.LINKER_COMMAND, name) == null) return null
        if (name in blockCommands) return if (next == '{') K.LINKER_COMMAND else null
        if (name in attributes || name == "INCLUDE" || (name == "AT" && next == '>') || next == '(') return K.LINKER_COMMAND
        return null
    }
    private fun nextSignificant(text: CharSequence, offset: Int): Char? {
        var pos = offset
        while (pos < text.length) {
            when {
                text[pos].isWhitespace() -> pos++
                text[pos] == '/' && pos + 1 < text.length && text[pos + 1] == '*' -> {
                    pos += 2
                    while (pos + 1 < text.length && !(text[pos] == '*' && text[pos + 1] == '/')) pos++
                    if (pos + 1 >= text.length) return null
                    pos += 2
                }
                text[pos] == '#' -> { while (pos < text.length && text[pos] !in "\r\n") pos++ }
                else -> return text[pos]
            }
        }
        return null
    }
}
