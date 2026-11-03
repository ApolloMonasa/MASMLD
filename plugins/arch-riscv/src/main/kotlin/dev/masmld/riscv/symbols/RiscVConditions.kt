package dev.masmld.riscv.symbols

import dev.masmld.core.source.SourceText
import dev.masmld.core.source.SourceToken

/** Tracks the possible assembly states at each conditional boundary. */
internal class RiscVConditions(source: CharSequence, private val unresolvedIncludes: Set<Int>) {
    private data class State(
        val constants: MutableMap<String, Long?> = hashMapOf(),
        val macros: MutableMap<String, Set<Int>> = hashMapOf(),
    ) {
        fun snapshot() = State(HashMap(constants), HashMap(macros))
    }

    private class Branch(val parent: Boolean, val entry: State, val outputs: MutableList<State> = mutableListOf()) {
        var remaining = true
        var uncertain = false
    }

    private val branches = ArrayDeque<Branch>()
    private var state = State()
    private var active = true
    private val preprocessed = Regex("""(?m)^\h*#\h*(?:if\w*|elif\w*|else|endif|include|define|undef)\b""").containsMatchIn(source)
    private val macroHistory = mutableMapOf<String, MutableList<Pair<Int, Set<Int>>>>()
    val conditional: Boolean get() = branches.any { it.uncertain }

    fun statement(name: String, argument: String, start: Int, offset: Int): Boolean {
        when {
            name in openers -> {
                val branch = Branch(active, state.snapshot())
                branches.addLast(branch)
                enter(branch, if (active) condition(name, argument) else false)
            }
            name == ".elseif" || name == ".else" -> {
                val branch = branches.lastOrNull() ?: return false
                if (active) branch.outputs += state
                restore(branch.entry.snapshot(), offset)
                enter(branch, if (name == ".else") true else condition(".if", argument))
            }
            name == ".endif" -> {
                val branch = branches.removeLastOrNull() ?: return false
                if (active) branch.outputs += state
                if (branch.parent && branch.remaining) branch.outputs += branch.entry
                restore(merge(branch), offset)
                active = branch.parent
            }
            else -> {
                if (active && name == ".include" && start in unresolvedIncludes) invalidateValues()
                return active
            }
        }
        return false
    }

    fun define(name: String, expression: String? = null) {
        state.constants[name] = expression?.let(::evaluate)
    }

    fun invalidateValues() {
        state.constants.replaceAll { _, _ -> null }
    }

    fun macros(name: String): Set<Int> = state.macros[name].orEmpty()

    fun setMacro(name: String, targets: Set<Int>, offset: Int) {
        if (targets == macros(name)) return
        if (targets.isEmpty()) state.macros.remove(name) else state.macros[name] = targets
        macroHistory.getOrPut(name, ::mutableListOf) += offset to targets
    }

    fun macrosAt(offset: Int): Set<Int> = macroHistory.values.flatMapTo(hashSetOf()) { changes ->
        val index = changes.binarySearchBy(offset) { it.first }
        changes.getOrNull(if (index >= 0) index else -index - 2)?.second.orEmpty()
    }

    private fun enter(branch: Branch, result: Boolean?) {
        active = branch.parent && branch.remaining && result != false
        if (active && result == null) branch.uncertain = true
        if (result == true) branch.remaining = false
    }

    private fun restore(next: State, offset: Int) {
        for (name in state.macros.keys + next.macros.keys) {
            val targets = next.macros[name].orEmpty()
            if (targets != macros(name)) macroHistory.getOrPut(name, ::mutableListOf) += offset to targets
        }
        state = next
    }

    private fun merge(branch: Branch): State {
        val states = branch.outputs
        val first = states.firstOrNull() ?: return branch.entry
        if (states.size == 1) return first
        val result = State()
        for ((name, value) in first.constants) {
            if (states.all { name in it.constants }) {
                result.constants[name] = value.takeIf { states.all { it.constants[name] == value } }
            }
        }
        for (snapshot in states) {
            for ((name, targets) in snapshot.macros) result.macros[name] = result.macros[name].orEmpty() + targets
        }
        return result
    }

    private fun condition(name: String, argument: String): Boolean? {
        if (preprocessed) return null
        return when (name) {
            ".ifdef", ".ifndef", ".ifnotdef" -> {
                // An absent symbol may be supplied by the build via --defsym.
                if (argument !in state.constants) null else name == ".ifdef"
            }
            ".ifc", ".ifnc", ".ifeqs", ".ifnes" -> {
                val pair = stringPair.matchEntire(argument) ?: return null
                val left = pair.groupValues[1]
                val right = pair.groupValues[2]
                if (name.endsWith('s') && (!left.startsWith('"') || !right.startsWith('"'))) return null
                if (!name.endsWith('s') && (left.startsWith('"') || right.startsWith('"'))) return null
                fun unquote(value: String) = if (value.first() == '"' || value.first() == '\'') value.substring(1, value.lastIndex) else value
                val equal = unquote(left) == unquote(right)
                if (name == ".ifc" || name == ".ifeqs") equal else !equal
            }
            ".ifb", ".ifnb" -> if (name == ".ifb") argument.isBlank() else argument.isNotBlank()
            ".if", ".ifne", ".ifeq", ".iflt", ".ifle", ".ifgt", ".ifge" -> evaluate(argument)?.let {
                when (name) {
                    ".ifeq" -> it == 0L
                    ".iflt" -> it < 0L
                    ".ifle" -> it <= 0L
                    ".ifgt" -> it > 0L
                    ".ifge" -> it >= 0L
                    else -> it != 0L
                }
            }
            else -> null
        }
    }

    private fun evaluate(expression: String): Long? = GasExpression.evaluate(expression) {
        if (!preprocessed) state.constants[it] else null
    }

    companion object {
        private const val STRING = """(?:"[^"\\]*"|'[^'\\]*'|[\w.$+-]+)"""
        private val stringPair = Regex("""($STRING)\s*,\s*($STRING)""")
        private val openers = setOf(".if", ".ifne", ".ifeq", ".iflt", ".ifle", ".ifgt", ".ifge", ".ifdef", ".ifndef",
            ".ifnotdef", ".ifb", ".ifnb", ".ifc", ".ifnc", ".ifeqs", ".ifnes")
    }
}

/** Only absolute integers. GNU as precedence differs from C and GNU ld. */
internal object GasExpression {
    private val precedence = mapOf("||" to 1, "&&" to 2, "==" to 3, "!=" to 3, "<" to 3, ">" to 3, "<=" to 3,
        ">=" to 3, "+" to 4, "-" to 4, "|" to 5, "&" to 5, "^" to 5, "!" to 5,
        "*" to 6, "/" to 6, "%" to 6, "<<" to 6, ">>" to 6)

    fun evaluate(expression: String, constant: (String) -> Long?): Long? {
        if (expression.length > 4096) return null
        val tokens = SourceText.tokens(expression)
        if (tokens.size > 128) return null
        var index = 0
        lateinit var parse: (Int) -> Long?
        fun atom(): Long? {
            val token = tokens.getOrNull(index++) ?: return null
            return when (token.text) {
                "(" -> parse(1)?.takeIf { tokens.getOrNull(index++)?.text == ")" }
                "-" -> atom()?.let(Math::negateExact)
                "~" -> atom()?.inv()
                else -> when (token.kind) {
                    SourceToken.Kind.NUMBER -> integer(token.text)
                    SourceToken.Kind.WORD -> constant(token.text)
                    else -> null
                }
            }
        }
        parse = fun(minimum: Int): Long? {
            var left = atom() ?: return null
            while (true) {
                val operator = tokens.getOrNull(index)?.text ?: break
                val priority = precedence[operator] ?: break
                if (priority < minimum) break
                index++
                val right = parse(priority + 1) ?: return null
                left = apply(operator, left, right) ?: return null
            }
            return left
        }
        return try {
            parse(1)?.takeIf { index == tokens.size }
        } catch (_: ArithmeticException) {
            null
        }
    }

    private fun integer(value: String): Long? = when {
        value.startsWith("0x", true) -> value.drop(2).toLongOrNull(16)
        value.startsWith("0b", true) -> value.drop(2).toLongOrNull(2)
        value.startsWith('0') -> value.toLongOrNull(8)
        else -> value.toLongOrNull()
    }

    private fun truth(result: Boolean) = if (result) -1L else 0L

    private fun apply(operator: String, left: Long, right: Long): Long? = when (operator) {
        "+" -> Math.addExact(left, right)
        "-" -> Math.subtractExact(left, right)
        "*" -> Math.multiplyExact(left, right)
        "/" -> if (right == 0L || left == Long.MIN_VALUE && right == -1L) null else left / right
        "%" -> if (right == 0L) null else left % right
        "<<" -> if (right in 0..63 && (left shl right.toInt()) shr right.toInt() == left) left shl right.toInt() else null
        ">>" -> if (left >= 0 && right in 0..63) left shr right.toInt() else null
        "|" -> left or right
        "&" -> left and right
        "^" -> left xor right
        "!" -> left or right.inv()
        "==" -> truth(left == right)
        "!=" -> truth(left != right)
        "<" -> truth(left < right)
        ">" -> truth(left > right)
        "<=" -> truth(left <= right)
        ">=" -> truth(left >= right)
        "&&" -> if (left != 0L && right != 0L) 1L else 0L
        "||" -> if (left != 0L || right != 0L) 1L else 0L
        else -> null
    }
}
