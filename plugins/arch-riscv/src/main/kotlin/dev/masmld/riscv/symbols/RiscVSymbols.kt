package dev.masmld.riscv.symbols

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import dev.masmld.riscv.highlighting.RiscVClassifier
import dev.masmld.riscv.highlighting.RiscVRole
import dev.masmld.riscv.lexer.RiscVLexer
import dev.masmld.riscv.lexer.RiscVTokens as T

data class AssemblySymbolDefinition(val name: String, val start: Int, val end: Int, val exported: Boolean, val macro: Boolean = false, val conditional: Boolean = false)
data class AssemblySymbolReference(val name: String, val start: Int, val end: Int, val macroTargets: Set<Int> = emptySet()) {
    val macroTarget: Int? get() = macroTargets.singleOrNull()
}
data class AssemblySymbols(val definitions: List<AssemblySymbolDefinition>, val references: List<AssemblySymbolReference>) {
    fun resolve(reference: AssemblySymbolReference): List<AssemblySymbolDefinition> {
        if (reference.macroTargets.isNotEmpty()) return definitions.filter { it.macro && it.start in reference.macroTargets }
        if (reference.name.matches(Regex("[0-9]+[fb]"))) {
            val name = reference.name.dropLast(1)
            val forward = reference.name.last() == 'f'
            val matches = definitions.filter {
                !it.macro && it.name == name && if (forward) it.start > reference.start else it.start < reference.start
            }.sortedBy { if (forward) it.start else -it.start }
            val definite = matches.indexOfFirst { !it.conditional }
            return if (definite < 0) matches else matches.take(definite + 1)
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

    fun analyze(source: CharSequence): AssemblySymbols = analyze(source, null)

    internal fun analyze(source: CharSequence, conditions: RiscVConditions?): AssemblySymbols {
        val roles = RiscVClassifier.classify(source).associate { it.start to it.role }
        val definitions = mutableListOf<Token>()
        val references = mutableListOf<AssemblySymbolReference>()
        val macros = mutableListOf<Token>()
        val activeMacros = mutableMapOf<String, Set<Int>>()
        val conditionalDefinitions = hashSetOf<Int>()
        val exported = mutableSetOf<String>()
        val local = mutableSetOf<String>()
        var macroDepth = 0
        var repeatDepth = 0
        var inactiveMacroDepth = 0
        fun macroTargets(name: String) = conditions?.macros(name) ?: activeMacros[name].orEmpty()
        fun setMacro(name: String, targets: Set<Int>, offset: Int) {
            if (conditions != null) conditions.setMacro(name, targets, offset)
            else if (targets.isEmpty()) activeMacros.remove(name) else activeMacros[name] = targets
        }
        fun macroReference(token: Token, targets: Set<Int>): Boolean {
            if (targets.isEmpty()) return false
            references += AssemblySymbolReference(token.text, token.start, token.end, targets)
            return true
        }
        fun addDefinition(token: Token) {
            definitions += token
            if (conditions != null && (conditions.conditional || repeatDepth > 0)) conditionalDefinitions += token.start
        }
        fun statement(tokens: List<Token>) {
            if (tokens.isEmpty()) return
            if (conditions != null) com.intellij.openapi.progress.ProgressManager.checkCanceled()
            var head = 0
            while (head + 1 < tokens.size && tokens[head].type in setOf(T.IDENTIFIER, T.NUMBER) && tokens[head + 1].type == T.COLON) head += 2
            val command = tokens.getOrNull(head)
            val name = command?.text.orEmpty()
            val argument = command?.let { source.subSequence(it.end, tokens.last().end).toString().trim() }.orEmpty()
            if (inactiveMacroDepth > 0) {
                if (name == ".macro") inactiveMacroDepth++
                if (name == ".endm") inactiveMacroDepth--
                return
            }
            if (macroDepth == 0 && conditions?.statement(name, argument, command?.start ?: tokens.first().start, tokens.last().end) == false) {
                if (name == ".macro") inactiveMacroDepth++
                return
            }
            if (name in setOf(".rept", ".rep", ".irp", ".irpc")) {
                if (macroDepth == 0) { repeatDepth++; conditions?.invalidateValues() }
                return
            }
            if (name == ".endr" && macroDepth == 0) {
                repeatDepth = (repeatDepth - 1).coerceAtLeast(0)
                conditions?.invalidateValues()
                return
            }
            if (tokens.first().text == ".macro") {
                // Nested definitions are expansion-time constructs, not file-scope declarations.
                if (macroDepth == 0) tokens.getOrNull(1)?.takeIf { it.type == T.IDENTIFIER }?.let {
                    macros += it
                    setMacro(it.text, if (repeatDepth > 0) macroTargets(it.text) + it.start else setOf(it.start), tokens.last().end)
                }
                macroDepth++
                return
            }
            if (tokens.first().text == ".endm") { if (macroDepth > 0) macroDepth--; return }
            if (tokens.first().text == ".purgem") {
                if (macroDepth == 0) tokens.getOrNull(1)?.let { name ->
                    macroReference(name, macroTargets(name.text))
                    if (repeatDepth == 0) setMacro(name.text, emptySet(), name.end)
                }
                return
            }
            if (macroDepth > 0) {
                // Allow calls to already declared macros (including recursion); exclude body labels/parameters.
                if (macroDepth == 1) macroReference(tokens.first(), macroTargets(tokens.first().text))
                return
            }
            for (index in 0 until head step 2) {
                val label = tokens[index]
                addDefinition(label)
                if (repeatDepth == 0) conditions?.define(label.text)
            }
            if (command == null) return
            if (macroReference(command, macroTargets(command.text))) {
                conditions?.invalidateValues()
                return
            }
            var operands = tokens.drop(head + 1)
            when {
                command.type == T.IDENTIFIER && operands.firstOrNull()?.text == "=" -> {
                    addDefinition(command)
                    if (repeatDepth == 0) conditions?.define(command.text, argument.substringAfter('='))
                    operands = operands.drop(1)
                }
                command.text in constantDirectives -> {
                    if (operands.firstOrNull()?.type != T.IDENTIFIER) return
                    addDefinition(operands.first())
                    if (repeatDepth == 0) {
                        val value = argument.substringAfter(',', "")
                        conditions?.define(operands.first().text, value)
                    }
                    operands = operands.drop(1)
                }
                command.text in declarationDirectives -> {
                    val names = operands.filter { it.type == T.IDENTIFIER }.map { it.text }
                    if (command.text in setOf(".globl", ".global", ".weak")) exported += names
                    if (command.text == ".local" && conditions?.conditional != true && repeatDepth == 0) local += names
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
            AssemblySymbolDefinition(it.text, it.start, it.end, it.text in exported && it.text !in local && !it.text.startsWith(".L") && !it.text.all(Char::isDigit), conditional = it.start in conditionalDefinitions)
        } + macros.map { AssemblySymbolDefinition(it.text, it.start, it.end, false, macro = true) }, references)
    }
}
