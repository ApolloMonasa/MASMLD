package dev.masmld.core.editing

import dev.masmld.core.source.SourceText

data class FoldRange(val start: Int, val end: Int, val placeholder: String)
object SourceFolding {
    fun ranges(source: String, assembly: Boolean): List<FoldRange> {
        val result = mutableListOf<FoldRange>()
        val stack = ArrayDeque<Pair<String, Int>>()
        val tokens = SourceText.tokens(source, true)
        for (token in tokens) {
            val closing = when (token.text) { ".endm" -> ".macro"; ".endr" -> ".rept"; ".endif" -> ".if"; ".cfi_endproc" -> ".cfi_startproc"; "}" -> "{"; else -> null }
            val opening = when {
                !assembly && token.text == "{" -> "{"
                assembly && token.text in setOf(".macro", ".rept", ".irp", ".irpc", ".cfi_startproc") -> if (token.text in setOf(".irp", ".irpc")) ".rept" else token.text
                assembly && token.text in setOf(".if", ".ifdef", ".ifndef", ".ifeq", ".ifne") -> ".if"
                else -> null
            }
            if (opening != null) stack.addLast(opening to token.start)
            if (closing != null && stack.lastOrNull()?.first == closing) {
                val (_, start) = stack.removeLast()
                if (source.substring(start, token.end).contains('\n')) result += FoldRange(start, token.end, if (closing == "{") "{ … }" else "$closing … ${token.text}")
            }
        }
        tokens.filter { it.kind == dev.masmld.core.source.SourceToken.Kind.COMMENT && it.text.startsWith("/*") && it.text.endsWith("*/") && it.text.contains('\n') }.forEach {
            result += FoldRange(it.start, it.end, "/* … */")
        }
        return result.sortedBy { it.start }
    }
}
