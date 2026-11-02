package dev.masmld.riscv.symbols

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import dev.masmld.riscv.highlighting.RiscVClassifier
import dev.masmld.riscv.highlighting.RiscVRole
import dev.masmld.riscv.lexer.RiscVLexer
import dev.masmld.riscv.lexer.RiscVTokens as T

data class AssemblySymbolDefinition(val name: String, val start: Int, val end: Int, val exported: Boolean, val macro: Boolean = false)
data class AssemblySymbolReference(val name: String, val start: Int, val end: Int, val macroTarget: Int? = null)
data class AssemblySymbols(val definitions: List<AssemblySymbolDefinition>, val references: List<AssemblySymbolReference>) {
    fun resolve(reference: AssemblySymbolReference): List<AssemblySymbolDefinition> {
        if (reference.macroTarget != null) return definitions.filter { it.macro && it.start == reference.macroTarget }
        if (reference.name.matches(Regex("[0-9]+[fb]"))) {
            val name = reference.name.dropLast(1)
            val matches = definitions.filter { it.name == name }
            val target = if (reference.name.last() == 'f') matches.filter { it.start > reference.start }.minByOrNull { it.start }
                else matches.filter { it.start < reference.start }.maxByOrNull { it.start }
            return listOfNotNull(target)
        }
        return definitions.filter { !it.macro && it.name == reference.name }
    }
}

/** Text-level scope model shared by reference resolution and parser definition nodes. */
object RiscVSymbols {
    private data class Token(val type: IElementType, val text: String, val start: Int, val end: Int)
    private val constantDirectives = setOf(".equ", ".equiv", ".set")
    private val declarationDirectives = setOf(".globl", ".global", ".weak", ".local", ".hidden")
    private val expressionDirectives = setOf(".size", ".byte", ".2byte", ".half", ".short", ".4byte", ".word", ".long", ".8byte", ".dword", ".quad", ".space", ".skip", ".zero", ".fill", ".align", ".balign", ".p2align", ".org", ".uleb128", ".sleb128")

    fun analyze(source: CharSequence): AssemblySymbols {
        val roles = RiscVClassifier.classify(source).associate { it.start to it.role }
        val definitions = mutableListOf<Token>()
        val references = mutableListOf<AssemblySymbolReference>()
        val macros = mutableListOf<Token>()
        val activeMacros = mutableMapOf<String, Token>()
        val exported = mutableSetOf<String>()
        val local = mutableSetOf<String>()
        var macroDepth = 0
        fun statement(tokens: List<Token>) {
            if (tokens.isEmpty()) return
            if (tokens.first().text == ".macro") {
                // Nested definitions are expansion-time constructs, not file-scope declarations.
                if (macroDepth == 0) tokens.getOrNull(1)?.takeIf { it.type == T.IDENTIFIER }?.let {
                    macros += it
                    activeMacros[it.text] = it
                }
                macroDepth++
                return
            }
            if (tokens.first().text == ".endm") { if (macroDepth > 0) macroDepth--; return }
            if (tokens.first().text == ".purgem") {
                if (macroDepth == 0) tokens.getOrNull(1)?.let { name ->
                    activeMacros.remove(name.text)?.let { definition ->
                        references += AssemblySymbolReference(name.text, name.start, name.end, definition.start)
                    }
                }
                return
            }
            if (macroDepth > 0) {
                // Allow calls to already declared macros (including recursion); exclude body labels/parameters.
                if (macroDepth == 1) activeMacros[tokens.first().text]?.let { definition ->
                    val call = tokens.first()
                    references += AssemblySymbolReference(call.text, call.start, call.end, definition.start)
                }
                return
            }
            var head = 0
            while (head + 1 < tokens.size && tokens[head].type in setOf(T.IDENTIFIER, T.NUMBER) && tokens[head + 1].type == T.COLON) {
                definitions += tokens[head]
                head += 2
            }
            if (head >= tokens.size) return
            val command = tokens[head]
            activeMacros[command.text]?.let { definition ->
                references += AssemblySymbolReference(command.text, command.start, command.end, definition.start)
                return
            }
            var operands = tokens.drop(head + 1)
            when {
                command.type == T.IDENTIFIER && operands.firstOrNull()?.text == "=" -> {
                    definitions += command
                    operands = operands.drop(1)
                }
                command.text in constantDirectives -> {
                    if (operands.firstOrNull()?.type != T.IDENTIFIER) return
                    definitions += operands.first()
                    operands = operands.drop(1)
                }
                command.text in declarationDirectives -> {
                    val names = operands.filter { it.type == T.IDENTIFIER }.map { it.text }
                    if (command.text in setOf(".globl", ".global", ".weak")) exported += names
                    if (command.text == ".local") local += names
                }
                command.text == ".type" -> operands = operands.take(1)
                command.text in expressionDirectives -> Unit
                roles[command.start] in setOf(RiscVRole.INSTRUCTION, RiscVRole.PSEUDO_INSTRUCTION) -> Unit
                else -> return // Section names, options, unknown opcodes and undefined macros are not symbol uses.
            }
            for ((index, token) in operands.withIndex()) {
                if (token.type != T.IDENTIFIER && token.type != T.LOCAL_REFERENCE) continue
                if (roles[token.start] == RiscVRole.REGISTER || token.text == ".") continue
                if (command.text == "fence" && token.text.all { it in "iorw" }) continue
                if (token.text in dev.masmld.riscv.editing.RiscVIsa.roundingModes && dev.masmld.riscv.editing.RiscVIsa.instructions.any { it.name == command.text && it.operands.contains("[rm]") }) continue
                val previous = operands.getOrNull(index - 1)?.text
                if (previous in setOf("%", "@")) continue // Relocation names and suffixes, not their arguments.
                references += AssemblySymbolReference(token.text, token.start, token.end)
            }
        }
        val tokens = mutableListOf<Token>()
        val lexer = RiscVLexer()
        lexer.start(source)
        while (lexer.tokenType != null) {
            val type = lexer.tokenType!!
            if (type == T.NEWLINE || type == T.SEPARATOR) { statement(tokens); tokens.clear() }
            else if (type != TokenType.WHITE_SPACE && type != T.LINE_COMMENT && type != T.BLOCK_COMMENT) {
                tokens += Token(type, source.subSequence(lexer.tokenStart, lexer.tokenEnd).toString(), lexer.tokenStart, lexer.tokenEnd)
            }
            lexer.advance()
        }
        statement(tokens)
        return AssemblySymbols(definitions.map {
            AssemblySymbolDefinition(it.text, it.start, it.end, it.text in exported && it.text !in local && !it.text.startsWith(".L") && !it.text.all(Char::isDigit))
        } + macros.map { AssemblySymbolDefinition(it.text, it.start, it.end, false, macro = true) }, references)
    }
}
