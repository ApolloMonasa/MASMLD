package dev.masmld.riscv.highlighting

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import dev.masmld.riscv.lexer.RiscVLexer
import dev.masmld.riscv.lexer.RiscVTokens as T

enum class RiscVRole { INSTRUCTION, PSEUDO_INSTRUCTION, DIRECTIVE, REGISTER, LABEL }
data class RiscVHighlight(val start: Int, val end: Int, val role: RiscVRole)

/** Context classification shared by the annotator and tests; no IDE services required. */
object RiscVClassifier {
    private data class Token(val type: IElementType, val text: String, val start: Int, val end: Int)
    private val registerNames = (0..31).map { "x$it" }.toSet() +
        setOf("zero", "ra", "sp", "gp", "tp", "fp") +
        (0..6).map { "t$it" } + (0..11).map { "s$it" } + (0..7).map { "a$it" }
    private fun words(text: String) = text.split(' ').toSet()
    private val threeRegisters = words("add sub sll slt sltu xor srl sra or and addw subw sllw srlw sraw")
    private val twoRegistersImmediate = words("addi slti sltiu xori ori andi slli srli srai addiw slliw srliw sraiw")
    private val branches = words("beq bne blt bge bltu bgeu")
    private val loads = words("lb lh lw lbu lhu lwu ld")
    private val stores = words("sb sh sw sd")
    private val upper = words("lui auipc")
    private val noRegisters = words("fence ecall ebreak")
    private val pseudoTwoRegisters = words("mv not neg negw sext.w seqz snez sltz sgtz")
    private val pseudoBranches = words("beqz bnez blez bgez bltz bgtz")
    private val pseudoSwappedBranches = words("bgt ble bgtu bleu")
    private val pseudoAddress = words("li la lla lga")
    private val pseudoNoRegisters = words("nop ret j tail")
    private val pseudo = pseudoTwoRegisters + pseudoBranches + pseudoSwappedBranches + pseudoAddress +
        pseudoNoRegisters + words("jr call")
    private val instructions = threeRegisters + twoRegistersImmediate + branches + loads + stores + upper +
        noRegisters + words("jal jalr")
    private val directives = words(".text .data .bss .rodata .section .pushsection .popsection .previous .subsection .globl .global .local .weak .hidden .type .size .align .balign .p2align .byte .2byte .half .short .4byte .word .long .8byte .dword .quad .ascii .asciz .string .zero .space .skip .fill .equ .equiv .set .comm .lcomm .file .loc .ident .include .incbin .macro .endm .exitm .rept .endr .irp .irpc .if .ifdef .ifndef .ifeq .ifne .else .elseif .endif .option .attribute .insn .variant_cc .dtprelword .dtpreldword .uleb128 .sleb128 .org .end .cfi_startproc .cfi_endproc .cfi_def_cfa .cfi_def_cfa_offset .cfi_offset .cfi_restore .cfi_adjust_cfa_offset")

    fun classify(source: CharSequence): List<RiscVHighlight> {
        val result = mutableListOf<RiscVHighlight>()
        val statement = mutableListOf<Token>()
        val lexer = RiscVLexer()
        lexer.start(source)
        while (lexer.tokenType != null) {
            val type = lexer.tokenType!!
            if (type == T.NEWLINE || type == T.SEPARATOR) {
                classifyStatement(statement, result)
                statement.clear()
            } else if (type != TokenType.WHITE_SPACE && type != T.LINE_COMMENT && type != T.BLOCK_COMMENT) {
                statement += Token(type, source.subSequence(lexer.tokenStart, lexer.tokenEnd).toString(), lexer.tokenStart, lexer.tokenEnd)
            }
            lexer.advance()
        }
        classifyStatement(statement, result)
        return result
    }

    private fun classifyStatement(tokens: List<Token>, result: MutableList<RiscVHighlight>) {
        fun mark(token: Token, role: RiscVRole) { result += RiscVHighlight(token.start, token.end, role) }
        var head = 0
        while (head + 1 < tokens.size && tokens[head].type in setOf(T.IDENTIFIER, T.NUMBER) && tokens[head + 1].type == T.COLON) {
            mark(tokens[head], RiscVRole.LABEL)
            head += 2
        }
        if (head >= tokens.size || tokens[head].type != T.IDENTIFIER) return
        val name = tokens[head].text
        if (tokens.getOrNull(head + 1)?.text == "=") {
            mark(tokens[head], RiscVRole.LABEL)
            return
        }
        if (name in directives) { mark(tokens[head], RiscVRole.DIRECTIVE); return }
        if (name !in instructions && name !in pseudo) return // Unknown macros/extensions remain ordinary identifiers.
        mark(tokens[head], if (name in pseudo) RiscVRole.PSEUDO_INSTRUCTION else RiscVRole.INSTRUCTION)
        val operands = mutableListOf<MutableList<Token>>(mutableListOf())
        var depth = 0
        for (token in tokens.drop(head + 1)) {
            if (token.type == T.COMMA && depth == 0) operands += mutableListOf<Token>()
            else {
                operands.last() += token
                if (token.type == T.LPAREN) depth++
                if (token.type == T.RPAREN && depth > 0) depth--
            }
        }
        fun register(index: Int) {
            val operand = operands.getOrNull(index) ?: return
            if (operand.size == 1 && operand[0].type == T.IDENTIFIER && operand[0].text in registerNames) {
                mark(operand[0], RiscVRole.REGISTER)
            }
        }
        fun baseRegister(index: Int) {
            val operand = operands.getOrNull(index) ?: return
            val pos = operand.size - 3
            if (pos < 0 || operand[pos].type != T.LPAREN || operand.last().type != T.RPAREN) return
            // %lo(symbol) is a relocation expression, not a base-register operand.
            if (pos >= 2 && operand[pos - 2].text == "%" && operand[pos - 1].type == T.IDENTIFIER) return
            val token = operand[pos + 1]
            if (token.type == T.IDENTIFIER && token.text in registerNames) mark(token, RiscVRole.REGISTER)
        }
        when (name) {
            in threeRegisters -> { register(0); register(1); register(2) }
            in twoRegistersImmediate, in pseudoTwoRegisters, in branches, in pseudoSwappedBranches -> { register(0); register(1) }
            in loads, in stores -> { register(0); baseRegister(1) }
            in upper, in pseudoAddress, in pseudoBranches, "jr" -> register(0)
            "jal" -> if (operands.size >= 2) register(0)
            "call" -> if (operands.size >= 2) register(0)
            "jalr" -> {
                register(0)
                if (operands.size >= 2) { register(1); baseRegister(1) }
            }
        }
    }
}
