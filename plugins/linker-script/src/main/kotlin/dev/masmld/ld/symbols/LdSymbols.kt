package dev.masmld.ld.symbols

import com.intellij.psi.TokenType
import dev.masmld.ld.lexer.LdLexer
import dev.masmld.ld.lexer.LdTokens as T

enum class LdSymbolKind { SYMBOL, REGION, SECTION }
data class LdDefinition(val name: String, val start: Int, val end: Int, val kind: LdSymbolKind, val expression: String? = null, val provided: Boolean = false)
data class LdReference(val name: String, val start: Int, val end: Int, val kind: LdSymbolKind = LdSymbolKind.SYMBOL)
data class LdRegion(val definition: LdDefinition, val origin: String?, val length: String?)
data class LdSection(val definition: LdDefinition, val address: String?, val region: String?, val alignment: String?)
data class LdSymbols(val definitions: List<LdDefinition>, val references: List<LdReference>, val regions: List<LdRegion>, val sections: List<LdSection> = emptyList()) {
    fun resolve(reference: LdReference) = definitions.filter { it.name == reference.name && it.kind == reference.kind }
    fun value(name: String, stack: Set<String> = emptySet()): Long? {
        if (name in stack || stack.size >= 64) return null
        val matches = definitions.filter { it.kind == LdSymbolKind.SYMBOL && it.name == name }
        if (matches.size != 1) return null
        return matches.single().expression?.let { evaluate(it, stack + name) }
    }
    fun evaluate(expression: String, stack: Set<String> = emptySet()): Long? = ConstantExpression.evaluate(expression, { value(it, stack) }) { name, args ->
        fun arg(index: Int) = args.getOrNull(index)?.let { evaluate(it, stack) }
        when (name) {
            "ORIGIN", "LENGTH" -> {
                val region = regions.singleOrNull { it.definition.name == args.singleOrNull()?.trim() }
                val key = "$name:${args.singleOrNull()}"
                if (key in stack || stack.size >= 64) null else {
                    val alias = definitions.singleOrNull { it.kind == LdSymbolKind.REGION && it.name == args.singleOrNull()?.trim() }?.expression
                    if (alias != null) evaluate("$name($alias)", stack + key)
                    else (if (name == "ORIGIN") region?.origin else region?.length)?.let { evaluate(it, stack + key) }
                }
            }
            "ABSOLUTE" -> if (args.size == 1) arg(0) else null
            "MAX" -> if (args.size == 2) arg(0)?.let { a -> arg(1)?.let { maxOf(a, it) } } else null
            "MIN" -> if (args.size == 2) arg(0)?.let { a -> arg(1)?.let { minOf(a, it) } } else null
            "ALIGN" -> if (args.size == 2) arg(0)?.let { value -> arg(1)?.takeIf { it >= 0 }?.let { alignment ->
                if (alignment == 0L) value else try { val remainder = Math.floorMod(value, alignment); if (remainder == 0L) value else Math.addExact(value, alignment - remainder) } catch (_: ArithmeticException) { null }
            } } else null // One argument requires the link-time location counter.
            else -> null
        }
    }
}
object LdSymbolAnalysis {
    private data class Token(val text: String, val start: Int, val end: Int, val string: Boolean)
    fun analyze(source: String): LdSymbols {
        val lexer = LdLexer(); lexer.start(source)
        val tokens = mutableListOf<Token>()
        while (lexer.tokenType != null) {
            if (lexer.tokenType !in setOf(TokenType.WHITE_SPACE, T.COMMENT, T.HASH_LINE, T.NEWLINE)) tokens += Token(source.substring(lexer.tokenStart, lexer.tokenEnd), lexer.tokenStart, lexer.tokenEnd, lexer.tokenType == T.STRING)
            lexer.advance()
        }
        val definitions = mutableListOf<LdDefinition>()
        val references = mutableListOf<LdReference>()
        val regions = mutableListOf<LdRegion>()
        val expressionIndices = mutableSetOf<Int>()
        val sections = linkedMapOf<Int, LdSection>()
        fun word(t: Token) = !t.string && t.text != "." && t.text.matches(Regex("[\\p{L}_.$][\\p{L}\\p{N}_.$-]*"))
        fun close(start: Int, left: String, right: String): Int {
            var depth = 0
            for (i in start until tokens.size) {
                if (tokens[i].text == left) depth++
                if (tokens[i].text == right && --depth == 0) return i
            }
            return tokens.size
        }
        val memoryRanges = tokens.indices.filter { tokens[it].text == "MEMORY" && tokens.getOrNull(it + 1)?.text == "{" }.map { it + 1 until close(it + 1, "{", "}") }
        val sectionRanges = tokens.indices.filter { tokens[it].text == "SECTIONS" && tokens.getOrNull(it + 1)?.text == "{" }.map { it + 1 until close(it + 1, "{", "}") }
        val braceDepth = IntArray(tokens.size)
        val parenDepth = IntArray(tokens.size)
        var braces = 0; var parens = 0
        tokens.forEachIndexed { i, token ->
            braceDepth[i] = braces; parenDepth[i] = parens
            if (token.text == "{") braces++
            if (token.text == "}") braces--
            if (token.text == "(") parens++
            if (token.text == ")") parens--
        }
        val headerIndices = mutableSetOf<Int>()
        val literalIncludes = dev.masmld.core.source.SourceGraph.includes(source, false)
        tokens.indices.filter { i -> literalIncludes.any { tokens[i].start in it.directiveStart until it.directiveEnd } }.forEach { headerIndices += it }
        for (i in tokens.indices) {
            val token = tokens[i]
            if (!word(token) || i in headerIndices || tokens.getOrNull(i - 1)?.text == ">" || parenDepth[i] != 0 || token.text in setOf("ENTRY", "PROVIDE", "PROVIDE_HIDDEN", "ASSERT", "INCLUDE", "EXTERN")) continue
            if (sectionRanges.none { i in it && braceDepth[i] == braceDepth[it.first] + 1 }) continue
            var colon = i + 1
            while (colon < tokens.size && tokens[colon].text !in setOf("{", "}", ";", "=")) {
                if (tokens[colon].text == ":" && parenDepth[colon] == 0) break
                colon++
            }
            if (tokens.getOrNull(colon)?.text != ":") continue
            val body = (colon + 1 until tokens.size).firstOrNull { tokens[it].text in setOf("{", "}", ";") } ?: continue
            if (tokens[body].text != "{") continue
            val end = close(body, "{", "}")
            if (end >= tokens.size) continue
            val address = source.substring(token.end, tokens[colon].start).replace(Regex("\\(\\s*(?:NOLOAD|READONLY|DSECT|COPY|INFO|OVERLAY)\\s*\\)"), "").trim().ifBlank { null }
            val after = source.substring(tokens[end].end)
            val region = Regex("^\\s*>\\s*([A-Za-z_.$][A-Za-z0-9_.$-]*)").find(after)?.groupValues?.get(1)
            val alignment = Regex("\\bALIGN\\s*\\(([^()]*)\\)").find(source.substring(tokens[colon].end, tokens[body].start))?.groupValues?.get(1)
            sections[i] = LdSection(LdDefinition(token.text, token.start, token.end, LdSymbolKind.SECTION), address, region, alignment)
            headerIndices += i until body
        }
        fun expression(start: Int, stops: Set<String>): Pair<String, Int> {
            var end = start; var depth = 0
            while (end < tokens.size) {
                val token = tokens[end]
                if (depth == 0 && token.text in stops) break
                if (token.text == "(") depth++
                if (token.text == ")") { if (depth == 0) break; depth-- }
                // A new MEMORY region or assignment starts a new expression, even without a semicolon.
                if (depth == 0 && end > start && tokens[end - 1].end < token.start && source.substring(tokens[end - 1].end, token.start).contains('\n') && word(token)) {
                    val next = tokens.getOrNull(end + 1)?.text
                    if (next in setOf("=", ":", "(")) break
                }
                end++
            }
            return (if (end > start) source.substring(tokens[start].start, tokens[end - 1].end) else "") to end
        }
        fun refs(start: Int, end: Int) {
            expressionIndices += start until end
            var function = ""
            for (i in start until end) {
                val t = tokens[i]
                if (tokens.getOrNull(i + 1)?.text == "(") { function = t.text; continue }
                if (!word(t) || t.text in setOf("NOLOAD", "READONLY", "DSECT", "COPY", "INFO", "OVERLAY")) continue
                val kind = when { tokens.getOrNull(i - 1)?.text == "(" && function in setOf("ORIGIN", "LENGTH") -> LdSymbolKind.REGION
                    tokens.getOrNull(i - 1)?.text == "(" && function in setOf("ADDR", "LOADADDR", "SIZEOF", "ALIGNOF") -> LdSymbolKind.SECTION
                    else -> LdSymbolKind.SYMBOL }
                references += LdReference(t.text, t.start, t.end, kind)
            }
        }
        for (i in tokens.indices) {
            val token = tokens[i]
            sections[i]?.let {
                definitions += it.definition
                val colon = (i + 1 until tokens.size).firstOrNull { j -> tokens[j].text == ":" && parenDepth[j] == 0 }
                if (colon != null) refs(i + 1, colon)
            }
            if (word(token) && token.text !in setOf("ORIGIN", "org", "o", "LENGTH", "len", "l") && tokens.getOrNull(i + 1)?.text == "=") {
                val (text, end) = expression(i + 2, setOf(";", "}", ","))
                val provided = tokens.take(i).takeLast(3).any { it.text in setOf("PROVIDE", "PROVIDE_HIDDEN") }
                definitions += LdDefinition(token.text, token.start, token.end, LdSymbolKind.SYMBOL, text, provided)
                refs(i + 2, end)
            }
            if (token.text == "." && tokens.getOrNull(i + 1)?.text in setOf("=", "+=")) { val (_, end) = expression(i + 2, setOf(";", "}")); refs(i + 2, end) }
            if (word(token)) {
                var next = i + 1
                if (tokens.getOrNull(next)?.text == "(") next = close(next, "(", ")") + 1
                if (tokens.getOrNull(next)?.text == ":") {
                    if (memoryRanges.any { i in it }) {
                        val def = LdDefinition(token.text, token.start, token.end, LdSymbolKind.REGION)
                        definitions += def
                        var origin: String? = null; var length: String? = null
                        var cursor = next + 1
                        while (cursor < tokens.size && tokens[cursor].text in setOf("ORIGIN", "org", "o", "LENGTH", "len", "l")) {
                            val key = tokens[cursor].text
                            if (tokens.getOrNull(cursor + 1)?.text != "=") break
                            val (text, end) = expression(cursor + 2, setOf(",", ";", "}"))
                            if (key in setOf("ORIGIN", "org", "o")) origin = text else length = text
                            refs(cursor + 2, end)
                            cursor = end + if (tokens.getOrNull(end)?.text == ",") 1 else 0
                        }
                        regions += LdRegion(def, origin, length)
                    }
                }
            }
            if (i !in expressionIndices && token.text == ">" && tokens.getOrNull(i + 1)?.let(::word) == true) {
                val target = tokens[i + 1]; references += LdReference(target.text, target.start, target.end, LdSymbolKind.REGION)
            }
            if (token.text == "REGION_ALIAS" && tokens.getOrNull(i + 1)?.text == "(" && tokens.getOrNull(i + 2)?.string == true) {
                val alias = tokens[i + 2]; val target = tokens.getOrNull(i + 4)
                if (tokens.getOrNull(i + 3)?.text == "," && target != null && word(target)) {
                    definitions += LdDefinition(alias.text.trim('"'), alias.start, alias.end, LdSymbolKind.REGION, target.text)
                    references += LdReference(target.text, target.start, target.end, LdSymbolKind.REGION)
                }
            }
            if (token.text in setOf("ENTRY", "EXTERN", "ASSERT") && tokens.getOrNull(i + 1)?.text == "(") refs(i + 2, close(i + 1, "(", ")"))
        }
        return LdSymbols(definitions.distinctBy { it.start }, references.distinctBy { it.start }, regions, sections.values.toList())
    }
}
