package dev.masmld.riscv.editing

import com.intellij.openapi.progress.ProgressManager
import dev.masmld.core.source.SourceText
import dev.masmld.core.source.SourceToken
import dev.masmld.riscv.symbols.AssemblySymbolReference
import dev.masmld.riscv.symbols.RiscVProgram

internal class RiscVMacros(private val program: RiscVProgram) {
    private data class Statement(val start: Int, val end: Int, val tokens: List<SourceToken>) {
        val head = tokens.indices.firstOrNull { it % 2 == 0 && tokens.getOrNull(it + 1)?.text != ":" }
        val command = head?.let(tokens::get)
    }
    private data class Definition(val header: Statement, val body: List<Statement>)
    private data class Parameter(val name: String, val qualifier: String, val default: String)
    internal data class Call(
        val reference: AssemblySymbolReference,
        val statementEnd: Int,
        val bindings: Map<String, String>,
        val problems: List<String>,
        val previewNotes: List<String>,
        val expansion: () -> String,
    )

    private val source = program.expanded.text
    private val statements = if (program.symbols.references.any { it.macroTarget != null }) statements(source) else emptyList()
    private val references = program.symbols.references.filter { it.macroTarget != null }.associateBy { it.start }
    private val macroNames = program.symbols.definitions.filter { it.macro }.mapTo(hashSetOf()) { it.name }
    private val definitions = mutableMapOf<Int, Definition>()
    private val repeatedCalls = hashSetOf<Int>()
    private val callSites = mutableMapOf<Int, Pair<Statement, Boolean>>()

    init {
        val cpp = Regex("""(?m)^\h*#\h*(?:if|ifdef|ifndef|elif|else|endif|include|define|undef)\b""")
        val uncertainAfter = cpp.find(source)?.range?.first ?: source.length
        if (program.expanded.issues.isEmpty()) {
            var alternate = false
            var depth = 0
            var headerIndex = 0
            var repeatEnd = -1
            for ((index, statement) in statements.withIndex()) {
                ProgressManager.checkCanceled()
                val command = statement.command ?: continue
                if (command.start >= uncertainAfter) break
                if (depth == 0 && index > repeatEnd && command.text in repeatStarts) {
                    repeatEnd = safeRepeatEnd(index) ?: break
                }
                if (depth == 0 && index <= repeatEnd && command.text in repeatMarkers) continue
                when (command.text) {
                    ".macro" -> if (depth++ == 0) headerIndex = index
                    ".endm" -> if (depth > 0 && --depth == 0) {
                        val header = statements[headerIndex]
                        header.tokens.getOrNull(1)?.let {
                            definitions[it.start] = Definition(header, statements.subList(headerIndex + 1, index))
                        }
                    }
                    else -> if (depth == 0) {
                        // The static symbol graph cannot determine which subsequent definitions are active.
                        if (isControl(command.text)) break
                        when (command.text) {
                            ".altmacro" -> alternate = true
                            ".noaltmacro" -> alternate = false
                            else -> if (command.start in references) {
                                callSites[command.start] = statement to alternate
                                if (index <= repeatEnd) repeatedCalls += command.start
                            }
                        }
                    }
                }
            }
        }
    }

    private fun safeRepeatEnd(start: Int): Int? {
        var depth = 0
        for (index in start until statements.size) {
            ProgressManager.checkCanceled()
            val name = statements[index].command?.text ?: continue
            when {
                name in repeatStarts -> depth++
                name == ".endr" -> if (--depth == 0) return index
                name in expansionStops || isControl(name) -> return null
            }
        }
        return null
    }

    fun callAt(file: com.intellij.psi.PsiFile, offset: Int): Call? {
        val document = file.viewProvider.document ?: return null
        val line = document.getLineNumber(offset)
        val lineStart = document.getLineStartOffset(line)
        return program.references(file, lineStart, document.getLineEndOffset(line))
            .asSequence()
            .filter { (reference, start) -> reference.macroTarget != null && start <= offset }
            .mapNotNull { (reference, start) ->
                callAt(reference.start)?.takeIf { offset <= start + it.statementEnd - reference.start }
            }
            .firstOrNull()
    }

    fun problems(): List<AssemblyProblem> = callSites.keys.flatMap { offset ->
        if (offset in repeatedCalls) return@flatMap emptyList()
        val call = callAt(offset) ?: return@flatMap emptyList()
        call.problems.map { AssemblyProblem(call.reference.start, call.reference.end, it) } +
            call.previewNotes.map { AssemblyProblem(call.reference.start, call.reference.end, it) }
    }

    fun callAt(offset: Int): Call? {
        val reference = references[offset] ?: return null
        val definition = definitions[reference.macroTarget] ?: return null
        val (statement, alternate) = callSites[offset] ?: return null
        val name = definition.header.tokens.getOrNull(1) ?: return null
        val parameters = fields(definition.header, name.end)?.map { field ->
            val match = parameter.matchEntire(field) ?: return null
            Parameter(match.groupValues[1], match.groupValues[2], match.groupValues[3].unquote())
        } ?: return null
        if (parameters.map { it.name }.distinct().size != parameters.size || parameters.any { it.qualifier == "vararg" }) return null
        val actuals = fields(statement, reference.end, alternate) ?: return null
        val alternateArguments = alternate && actuals.any { it == "%" || it.startsWith('<') || it.startsWith('\'') }
        val values = linkedMapOf<String, String>()
        val errors = mutableListOf<String>()
        val notes = mutableListOf<String>()
        var position = 0
        var seenKeyword = false
        for (actual in actuals) {
            val keyword = named.matchEntire(actual)
            if (keyword != null && !alternate) {
                seenKeyword = true
                val (key, value) = keyword.destructured
                when {
                    parameters.none { it.name == key } -> errors += "未知的宏参数：$key"
                    values.putIfAbsent(key, value.unquote()) != null -> errors += "宏参数重复传入：$key"
                }
            } else {
                if (keyword != null && alternate) notes += ".altmacro 仅支持位置传参，$actual 将作为字面值"
                if (seenKeyword) {
                    errors += "命名参数后不能使用位置参数"
                    break
                }
                val formal = parameters.getOrNull(position++)
                if (formal == null) {
                    errors += "宏位置参数过多"
                    break
                }
                values[formal.name] = if (alternate && actual.startsWith('%') && actual.length > 1) {
                    val expression = actual.drop(1)
                    integerLiteral(expression)?.toString() ?: "⟨$expression⟩"
                } else actual.unquote()
            }
        }
        val bindings = parameters.associate { formal ->
            val value = values[formal.name].orEmpty()
            if (formal.qualifier == "req" && value.isEmpty()) errors += "缺少必填宏参数：${formal.name}"
            formal.name to value.ifEmpty { formal.default }
        }
        return Call(reference, statement.end, bindings, errors, notes.distinct()) {
            if (alternateArguments) "⟪预览在此停止⟫ 调用使用了替代宏模式的特殊参数语法"
            else expand(definition, bindings, alternate)
        }
    }

    private fun expand(definition: Definition, bindings: Map<String, String>, alternate: Boolean): String = buildString {
        val pattern = if (alternate) alternateSubstitution else substitution
        for (statement in definition.body) {
            ProgressManager.checkCanceled()
            val raw = source.substring(statement.start, statement.end)
            val command = statement.command?.text.orEmpty()
            val unsupported = isControl(command) || command in expansionStops ||
                unsupportedSubstitution.containsMatchIn(raw) ||
                alternate && (raw.contains('%') || raw.contains('<') || raw.contains('!'))
            val expanded = if (unsupported) null else substitute(raw, bindings, pattern, 100_000 - length)
            val expandedCommand = expanded?.let { Statement(0, it.length, SourceText.tokens(it)).command?.text }
            if (expanded == null || expandedCommand in macroNames || expandedCommand in expansionStops ||
                expandedCommand?.let(::isControl) == true) {
                appendLine("⟪预览在此停止⟫ ${raw.trim().take(160)}")
                break
            }
            appendLine(expanded)
        }
    }

    private fun substitute(raw: String, bindings: Map<String, String>, pattern: Regex, limit: Int): String? {
        val result = StringBuilder()
        var start = 0
        for (match in pattern.findAll(raw)) {
            val escaped = match.groupValues[1]
            val name = escaped.ifEmpty { match.groupValues[2] }
            val value = bindings[name] ?: if (escaped.isNotEmpty()) return null else match.value
            if (result.length + match.range.first - start + value.length >= limit) return null
            result.append(raw, start, match.range.first).append(value)
            start = match.range.last + 1
        }
        if (result.length + raw.length - start >= limit) return null
        return result.append(raw, start, raw.length).toString()
    }

    /** Split at unquoted commas and blanks, retaining omitted comma-separated values. */
    private fun fields(statement: Statement, start: Int, alternate: Boolean = false): List<String>? {
        val tokens = statement.tokens.filter { it.start >= start }
        if (tokens.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        var fieldStart = tokens.first().start
        var previous: SourceToken? = null
        val brackets = ArrayDeque<String>()
        for (token in tokens) {
            val before = previous
            if (brackets.isEmpty()) {
                if (token.text == ",") {
                    result += source.substring(fieldStart, token.start).trim()
                    fieldStart = token.end
                    previous = token
                    continue
                }
                if (before != null && before.end < token.start && before.text !in fieldJoiners &&
                    token.text !in fieldJoiners) {
                    result += source.substring(fieldStart, before.end).trim()
                    fieldStart = token.start
                }
            }
            val close = when (token.text) {
                "(" -> ")"
                "[" -> "]"
                "{" -> "}"
                "<" -> if (alternate) ">" else null
                else -> null
            }
            if (close != null) brackets.addLast(close)
            if (token.text == brackets.lastOrNull()) brackets.removeLast()
            previous = token
        }
        if (brackets.isNotEmpty()) return null
        if (fieldStart < tokens.last().end) result += source.substring(fieldStart, tokens.last().end).trim()
        return result
    }

    private fun String.unquote(): String = if (length >= 2 && first() == '"' && last() == '"') substring(1, length - 1) else this

    companion object {
        private const val NAME = """[\p{L}_.$][\p{L}\p{Nd}_.$]*"""
        private val integer = Regex("""[+-]?(?:0[xX][0-9a-fA-F]+|0[bB][01]+|0[0-7]*|[1-9][0-9]*)""")

        private fun integerLiteral(expression: String): Long? {
            if (!integer.matches(expression)) return null
            val digits = expression.removePrefix("+").removePrefix("-")
            val sign = if (expression.startsWith('-')) "-" else ""
            val (number, radix) = when {
                digits.startsWith("0x", ignoreCase = true) -> digits.drop(2) to 16
                digits.startsWith("0b", ignoreCase = true) -> digits.drop(2) to 2
                digits.startsWith('0') -> digits to 8
                else -> digits to 10
            }
            return (sign + number).toLongOrNull(radix)
        }

        private val repeatStarts = setOf(".rept", ".rep")
        private val repeatMarkers = repeatStarts + ".endr"
        private val parameter = Regex("""($NAME)\s*(?::\s*(req|vararg))?\s*(?:=\s*(.+))?""")
        private val named = Regex("""($NAME)\s*=(?!=)\s*(.*)""")
        private val substitution = Regex("""\\($NAME)(?:\\\(\))?""")
        private val ampersand = Regex("""(?<![\p{L}\p{Nd}_.$])($NAME)&""")
        private val alternateSubstitution = Regex(substitution.pattern + """|(?<![\p{L}\p{Nd}_.$])($NAME)&?""")
        private val unsupportedSubstitution = Regex("""\\[@+]""")
        private val fieldJoiners = setOf("=", ":", ",")
        private val expansionStops = setOf(".macro", ".endm", ".exitm", ".purgem", ".include", ".altmacro", ".noaltmacro", "LOCAL")
        private fun isControl(name: String) = name.startsWith(".if") || name in setOf(".else", ".elseif", ".endif", ".rept", ".rep", ".irp", ".irpc", ".endr")
        private fun statements(source: String): List<Statement> = buildList {
            var start = 0
            var tokens = mutableListOf<SourceToken>()
            for (token in SourceText.tokens(source)) {
                if (token.kind == SourceToken.Kind.NEWLINE || token.kind == SourceToken.Kind.PUNCT && token.text == ";") {
                    add(Statement(start, token.start, tokens))
                    start = token.end
                    tokens = mutableListOf()
                } else tokens += token
            }
            if (start < source.length) add(Statement(start, source.length, tokens))
        }
    }
}
