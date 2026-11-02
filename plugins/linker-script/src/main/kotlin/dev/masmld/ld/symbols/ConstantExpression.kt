package dev.masmld.ld.symbols

/** Conservative checked 64-bit evaluation: unknown link-time values and overflow stay unknown. */
object ConstantExpression {
    fun evaluate(source: String, symbol: (String) -> Long? = { null }, call: (String, List<String>) -> Long? = { _, _ -> null }): Long? {
        if (source.length > 16384) return null
        val tokens = Regex("0[xX][0-9a-fA-F]+|[0-9]+[kKmM]?|[A-Za-z_.$][A-Za-z0-9_.$-]*|<<|>>|<=|>=|==|!=|&&|\\|\\||[^\\s]").findAll(source).map { it.value }.toList()
        if (tokens.size > 256) return null
        var position = 0
        val precedence = mapOf("||" to 1, "&&" to 2, "|" to 3, "^" to 4, "&" to 5, "==" to 6, "!=" to 6, "<" to 7, ">" to 7, "<=" to 7, ">=" to 7, "<<" to 8, ">>" to 8, "+" to 9, "-" to 9, "*" to 10, "/" to 10, "%" to 10)
        fun number(token: String): Long? {
            val suffix = token.lastOrNull()?.uppercaseChar()
            val factor = when (suffix) { 'K' -> 1024L; 'M' -> 1024L * 1024; else -> 1L }
            val value = if (factor != 1L) token.dropLast(1) else token
            val parsed = if (value.startsWith("0x", true)) value.drop(2).toLongOrNull(16) else if (value.length > 1 && value.startsWith('0')) value.toLongOrNull(8) else value.toLongOrNull()
            return parsed?.let { Math.multiplyExact(it, factor) }
        }
        fun apply(operator: String, left: Long, right: Long): Long? = when (operator) {
            "+" -> Math.addExact(left, right); "-" -> Math.subtractExact(left, right); "*" -> Math.multiplyExact(left, right)
            "/" -> if (right == 0L || left == Long.MIN_VALUE && right == -1L) null else left / right
            "%" -> if (right == 0L) null else left % right
            "<<" -> if (right !in 0..63 || right > 0 && (left shl right.toInt()) shr right.toInt() != left) null else left shl right.toInt()
            ">>" -> if (right !in 0..63) null else left shr right.toInt()
            "&" -> left and right; "|" -> left or right; "^" -> left xor right
            "==" -> if (left == right) 1 else 0; "!=" -> if (left != right) 1 else 0
            "<" -> if (left < right) 1 else 0; ">" -> if (left > right) 1 else 0
            "<=" -> if (left <= right) 1 else 0; ">=" -> if (left >= right) 1 else 0
            "&&" -> if (left != 0L && right != 0L) 1 else 0; "||" -> if (left != 0L || right != 0L) 1 else 0
            else -> null
        }
        lateinit var expression: (Int) -> Long?
        fun atom(): Long? {
            val token = tokens.getOrNull(position++) ?: return null
            if (token in setOf("+", "-", "~", "!")) {
                val value = atom() ?: return null
                return when (token) { "-" -> Math.negateExact(value); "~" -> value.inv(); "!" -> if (value == 0L) 1 else 0; else -> value }
            }
            if (token == "(") {
                val value = expression(1)
                if (tokens.getOrNull(position++) != ")") return null
                return value
            }
            if (tokens.getOrNull(position) == "(") {
                position++
                val args = mutableListOf<String>(); var start = position; var depth = 0
                while (position < tokens.size) {
                    val next = tokens[position]
                    if (next == ")" && depth == 0) {
                        if (position > start) args += tokens.subList(start, position).joinToString(" ")
                        position++
                        return call(token, args)
                    }
                    if (next == "," && depth == 0) { args += tokens.subList(start, position).joinToString(" "); start = position + 1 }
                    if (next == "(") depth++
                    if (next == ")") depth--
                    position++
                }
                return null
            }
            return number(token) ?: symbol(token)
        }
        expression = fun(minimum: Int): Long? {
            var left = atom() ?: return null
            while (true) {
                val operator = tokens.getOrNull(position) ?: break
                val level = precedence[operator] ?: break
                if (level < minimum) break
                position++
                val right = expression(level + 1) ?: return null
                left = apply(operator, left, right) ?: return null
            }
            return left
        }
        return try { val value = expression(1); value?.takeIf { position == tokens.size } } catch (_: ArithmeticException) { null } catch (_: StackOverflowError) { null }
    }
}
