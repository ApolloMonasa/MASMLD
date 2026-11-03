package dev.masmld.core.source

/** A lossless-offset scanner for source graph and lightweight expression services. */
data class SourceToken(val text: String, val start: Int, val end: Int, val kind: Kind) {
    enum class Kind { WORD, NUMBER, STRING, PUNCT, NEWLINE, COMMENT }
}
object SourceText {
    fun tokens(source: CharSequence, includeComments: Boolean = false): List<SourceToken> {
        val result = mutableListOf<SourceToken>()
        var i = 0
        while (i < source.length) {
            val start = i
            val c = source[i]
            when {
                c == '\n' -> { i++; result += SourceToken("\n", start, i, SourceToken.Kind.NEWLINE) }
                c.isWhitespace() -> i++
                c == '#' || (c == '/' && source.getOrNull(i + 1) == '/') -> { while (i < source.length && source[i] != '\n') i++; if (includeComments) result += SourceToken(source.subSequence(start, i).toString(), start, i, SourceToken.Kind.COMMENT) }
                c == '/' && source.getOrNull(i + 1) == '*' -> {
                    i += 2
                    while (i < source.length && !(source[i] == '*' && source.getOrNull(i + 1) == '/')) {
                        if (source[i] == '\n') result += SourceToken("\n", i, i + 1, SourceToken.Kind.NEWLINE)
                        i++
                    }
                    if (i < source.length) i += 2
                    if (includeComments) result += SourceToken(source.subSequence(start, i).toString(), start, i, SourceToken.Kind.COMMENT)
                }
                c == '"' || c == '\'' -> {
                    i++
                    while (i < source.length && source[i] != c && source[i] != '\n') {
                        if (source[i] == '\\' && i + 1 < source.length) i++
                        i++
                    }
                    if (i < source.length && source[i] == c) i++
                    result += SourceToken(source.subSequence(start, i).toString(), start, i, SourceToken.Kind.STRING)
                }
                c.isLetter() || c in "_.$" -> {
                    i++; while (i < source.length && (source[i].isLetterOrDigit() || source[i] in "_.$")) i++
                    result += SourceToken(source.subSequence(start, i).toString(), start, i, SourceToken.Kind.WORD)
                }
                c.isDigit() -> {
                    i++; while (i < source.length && (source[i].isLetterOrDigit() || source[i] == '_')) i++
                    result += SourceToken(source.subSequence(start, i).toString(), start, i, SourceToken.Kind.NUMBER)
                }
                else -> {
                    i++
                    if (source.subSequence(start, minOf(start + 2, source.length)).toString() in setOf("<<", ">>", "==", "!=", "<=", ">=", "&&", "||", "+=", "-=", "&=", "|=")) i++
                    result += SourceToken(source.subSequence(start, i).toString(), start, i, SourceToken.Kind.PUNCT)
                }
            }
        }
        return result
    }
}
data class IncludeDirective(val path: String, val start: Int, val end: Int, val directiveStart: Int, val directiveEnd: Int)
data class SourceUnit(val id: String, val text: String)
data class SourcePiece(val unit: SourceUnit, val sourceStart: Int, val expandedStart: Int, val length: Int)
data class SourceSite(val unit: SourceUnit, val offset: Int)
data class ExpandedSource(val text: String, val pieces: List<SourcePiece>, val issues: List<String>) {
    fun site(offset: Int): SourceSite? = pieces.lastOrNull { offset >= it.expandedStart && offset < it.expandedStart + it.length }
        ?.let { SourceSite(it.unit, it.sourceStart + offset - it.expandedStart) }
    fun expandedOffset(id: String, offset: Int): Int? = pieces.firstOrNull {
        it.unit.id == id && offset >= it.sourceStart && offset < it.sourceStart + it.length
    }?.let { it.expandedStart + offset - it.sourceStart }
}
object SourceGraph {
    fun includes(source: CharSequence, assembly: Boolean): List<IncludeDirective> {
        val tokens = SourceText.tokens(source)
        val result = mutableListOf<IncludeDirective>()
        var macroDepth = 0
        for ((index, token) in tokens.withIndex()) {
            if (assembly && token.text == ".macro") macroDepth++
            if (assembly && token.text == ".endm") macroDepth = (macroDepth - 1).coerceAtLeast(0)
            if (macroDepth > 0 || token.text != if (assembly) ".include" else "INCLUDE") continue
            val name = tokens.getOrNull(index + 1) ?: continue
            if (name.kind == SourceToken.Kind.NEWLINE) continue
            if (name.kind == SourceToken.Kind.STRING) {
                if (!name.text.endsWith(name.text.take(1)) || name.text.length < 2) continue
                result += IncludeDirective(name.text.substring(1, name.text.length - 1), name.start + 1, name.end - 1, token.start, name.end)
            } else {
                // Keep include resolution available while an assembly path is still unquoted.
                var end = name.end
                for (next in tokens.drop(index + 2)) {
                    if (next.start != end || next.kind == SourceToken.Kind.NEWLINE || next.text in setOf(";", "{", "}")) break
                    end = next.end
                }
                result += IncludeDirective(source.subSequence(name.start, end).toString(), name.start, end, token.start, end)
            }
        }
        return result
    }
    /** Literal includes only; macros, preprocessing and link-time evaluation are never executed. */
    fun expand(root: SourceUnit, assembly: Boolean, load: (SourceUnit, String) -> SourceUnit?): ExpandedSource {
        val text = StringBuilder()
        val pieces = mutableListOf<SourcePiece>()
        val issues = mutableListOf<String>()
        var count = 0
        fun visit(unit: SourceUnit, stack: Set<String>) {
            if (unit.id in stack || stack.size >= 32 || count++ >= 128 || text.length + unit.text.length > 8_000_000) {
                issues += "Include expansion limit or cycle: ${unit.id}"; return
            }
            fun append(start: Int, end: Int) {
                if (end > start) { pieces += SourcePiece(unit, start, text.length, end - start); text.append(unit.text, start, end) }
            }
            var start = 0
            for (include in includes(unit.text, assembly)) {
                append(start, include.directiveEnd)
                val child = load(unit, include.path)
                if (child == null) issues += "Include not found: ${include.path}"
                else { text.append('\n'); visit(child, stack + unit.id); text.append('\n') }
                start = include.directiveEnd
            }
            append(start, unit.text.length)
        }
        visit(root, emptySet())
        return ExpandedSource(text.toString(), pieces, issues)
    }
}
