package dev.masmld.riscv.editing

import dev.masmld.core.source.SourceText
import dev.masmld.core.source.SourceToken

data class TargetScope(val start: Int, val target: IsaTarget?)
object RiscVTargetScopes {
    fun build(source: String, initial: IsaTarget?): List<TargetScope> {
        val scopes = mutableListOf(TargetScope(0, initial))
        var current = initial; var macroDepth = 0
        val stack = mutableListOf<IsaTarget?>()
        val tokens = SourceText.tokens(source)
        var statement = mutableListOf<SourceToken>()
        fun finish(end: Int) {
            var head = 0
            while (statement.getOrNull(head + 1)?.text == ":") head += 2
            val command = statement.getOrNull(head)
            if (command?.text == ".macro") macroDepth++
            if (command?.text == ".endm") macroDepth = (macroDepth - 1).coerceAtLeast(0)
            if (command?.text == ".option" && macroDepth == 0) {
                when (statement.getOrNull(head + 1)?.text) {
                    "push" -> stack += current
                    "pop" -> if (stack.isNotEmpty()) current = stack.removeLast()
                    "rvc" -> current = current?.let { it.copy(extensions = it.extensions + "c") }
                    "norvc" -> current = current?.let { it.copy(extensions = it.extensions - "c") }
                    "arch" -> {
                        val start = statement[head + 1].end
                        val options = source.substring(start, end).substringBefore('#').trim().removePrefix(",").trim()
                        if (options.startsWith("rv")) current = RiscVIsa.parse(options)
                        else current = current?.let { previous ->
                            val extensions = previous.extensions.toMutableSet()
                            for (option in options.split(',')) {
                                val value = option.trim()
                                if (value.startsWith('+')) extensions += value.drop(1)
                                if (value.startsWith('-')) extensions -= value.drop(1)
                            }
                            if ("d" in extensions) extensions += "f"
                            previous.copy(extensions = extensions)
                        }
                    }
                }
                scopes += TargetScope(end, current)
            }
            statement = mutableListOf()
        }
        for (token in tokens) {
            if (token.kind == SourceToken.Kind.NEWLINE || token.text == ";") finish(token.start) else statement += token
        }
        finish(source.length)
        return scopes
    }
    fun at(scopes: List<TargetScope>, offset: Int): IsaTarget? = scopes.lastOrNull { it.start <= offset }?.target
}
