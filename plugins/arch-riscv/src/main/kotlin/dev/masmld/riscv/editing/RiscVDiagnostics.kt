package dev.masmld.riscv.editing

import dev.masmld.core.documentation.DocumentationKind
import dev.masmld.core.source.SourceText
import dev.masmld.core.source.SourceToken
import dev.masmld.riscv.documentation.RiscVDocumentationCatalog
import dev.masmld.riscv.symbols.GasExpression

data class AssemblyProblem(val start: Int, val end: Int, val message: String, val requiredTarget: String? = null)
object RiscVDiagnostics {
    private data class Operand(val text: String, val start: Int, val end: Int) {
        fun slice(from: Int, to: Int): Operand {
            val value = text.substring(from, to)
            return Operand(value.trim(), start + from + value.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0),
                start + from + value.trimEnd().length)
        }
    }

    fun operands(text: String): List<String> = operandRanges(text, SourceText.tokens(text), 0).map { it.text }

    private fun operandRanges(text: String, tokens: List<SourceToken>, start: Int): List<Operand> {
        if (tokens.isEmpty()) return emptyList()
        val result = mutableListOf<Operand>()
        var first = start
        var depth = 0
        val line = Operand(text, 0, text.length)
        for (token in tokens) {
            if (token.text == "(") depth++
            if (token.text == ")") depth--
            if (token.text == "," && depth == 0) {
                result += line.slice(first, token.start)
                first = token.end
            }
        }
        result += line.slice(first, tokens.last().end)
        return result
    }

    fun signature(name: String): String? = RiscVDocumentationCatalog.entries.firstOrNull {
        it.name == name && it.kind in setOf(DocumentationKind.INSTRUCTION, DocumentationKind.PSEUDO_INSTRUCTION)
    }?.syntax
    fun analyze(source: String, target: IsaTarget?, macroCalls: Set<Int> = dev.masmld.riscv.symbols.RiscVSymbols.analyze(source).references.filter { it.macroTargets.isNotEmpty() }.map { it.start }.toSet()): List<AssemblyProblem> {
        val result = mutableListOf<AssemblyProblem>()
        var macroDepth = 0
        val scopes = RiscVTargetScopes.build(source, target)
        var base = 0
        val boundaries = SourceText.tokens(source).filter { it.kind == SourceToken.Kind.NEWLINE || it.text == ";" }.map { it.start } + source.length
        for (end in boundaries) {
            val line = source.substring(base, end)
            val tokens = SourceText.tokens(line).filter { it.kind != SourceToken.Kind.NEWLINE }
            var head = 0
            while (tokens.getOrNull(head + 1)?.text == ":") head += 2
            val command = tokens.getOrNull(head)
            if (command?.text == ".macro") macroDepth++
            if (command?.text == ".endm") macroDepth = (macroDepth - 1).coerceAtLeast(0)
            if (command != null && macroDepth == 0 && base + command.start !in macroCalls) {
                val activeTarget = RiscVTargetScopes.at(scopes, base)
                val name = command.text
                val entry = RiscVDocumentationCatalog.entries.firstOrNull { it.name == name && it.kind in setOf(DocumentationKind.INSTRUCTION, DocumentationKind.PSEUDO_INSTRUCTION) }
                fun problem(message: String, replacement: String? = null, operand: Operand? = null) {
                    val range = operand?.takeIf { it.start < it.end } ?: Operand(command.text, command.start, command.end)
                    result += AssemblyProblem(base + range.start, base + range.end, message, replacement)
                }
                if (entry != null) {
                    if (!RiscVIsa.supports(name, activeTarget)) {
                        val spec = RiscVIsa.instructions.firstOrNull { it.name == name }
                        val ext = activeTarget!!.extensions + spec?.extensions.orEmpty() + (if (name in RiscVIsa.csrAliases) setOf("zicsr") else emptySet()) + "i"
                        val width = spec?.xlen?.takeIf { it != 0 } ?: if (name in setOf("ld", "sd", "lwu") || name.endsWith('w') || name.endsWith("iw")) 64 else activeTarget.xlen
                        val replacement = "rv${width}" + "imafdc".filter { it.toString() in ext } + ext.filter { it.length > 1 }.sorted().joinToString("") { "_$it" }
                        problem("${name} 不适用于当前 XLEN/ISA 扩展配置", replacement)
                    }
                    val args = operandRanges(line, tokens.drop(head + 1), command.end)
                    val spec = RiscVIsa.instructions.firstOrNull { it.name == name }?.operands
                        ?: entry.syntax.removePrefix(name).trim().takeIf { !it.contains(';') && !it.contains('；') }
                    if (spec != null && !spec.contains("[rd,") && !spec.contains('|')) {
                        val expected = if (spec.isBlank()) emptyList() else spec.split(',').map(String::trim)
                        val minimum = expected.count { !it.startsWith('[') }
                        // Some GNU pseudo instructions have multiple accepted forms.
                        if (name !in setOf("jal", "jalr", "call", "fence") && args.size !in minimum..expected.size) problem("${name} 需要 $minimum–${expected.size} 个操作数，当前 ${args.size} 个")
                        for ((index, shape) in expected.withIndex()) {
                            val operand = args.getOrNull(index) ?: continue
                            val arg = operand.text
                            val registerKind = when (shape) {
                                "rd", "rs", "rs1", "rs2" -> RiscVIsa.integerRegisters
                                "fd", "fs1", "fs2", "fs3" -> RiscVIsa.floatingRegisters
                                else -> null
                            }
                            if (registerKind != null && arg !in registerKind && identifier.matches(arg)) {
                                val kind = if (shape.startsWith('f')) "浮点" else "整数"
                                problem("操作数 ${index + 1} 需要${kind}寄存器：$arg", operand = operand)
                            }
                            if (shape == "[rm]" && identifier.matches(arg) && arg !in RiscVIsa.roundingModes &&
                                arg !in roundingEncodings) {
                                problem("无效的浮点舍入模式：$arg", operand = operand)
                            }
                            val constraint = when (shape) {
                                "imm12" -> signed12
                                "imm20" -> unsigned20
                                "uimm" -> if (name in csrImmediateInstructions) unsigned5 else null
                                "csr" -> unsigned12
                                "shamt" -> shiftConstraint(name, activeTarget)
                                "imm" -> compressedImmediate(name, activeTarget)
                                else -> null
                            }
                            if (constraint != null) checkImmediate(operand, constraint, activeTarget) { location, message ->
                                problem(message, operand = location)
                            }
                            if (shape.contains("(rs1)")) checkAddress(name, operand, activeTarget) { location, message ->
                                problem(message, operand = location)
                            }
                        }
                    }
                    if (name == "jalr") {
                        if (args.size == 3) {
                            for (operand in args.take(2)) {
                                if (identifier.matches(operand.text) && operand.text !in RiscVIsa.integerRegisters) {
                                    problem("需要整数寄存器：${operand.text}", operand = operand)
                                }
                            }
                            checkImmediate(args[2], signed12, activeTarget) { operand, message -> problem(message, operand = operand) }
                        } else args.lastOrNull()?.let { operand ->
                            checkAddress(name, operand, activeTarget) { location, message -> problem(message, operand = location) }
                        }
                    }
                }
            }
            base = end + 1
        }
        return result
    }

    private data class Constraint(val range: LongRange, val alignment: Int = 1, val nonzero: Boolean = false)

    private val identifier = Regex("[A-Za-z0-9_]+")
    private val relocation = Regex("""%([A-Za-z_][A-Za-z0-9_]*)\s*\(.*\)""")
    private val lowRelocations = setOf("lo", "pcrel_lo", "tprel_lo")
    private val highRelocations = setOf("hi", "pcrel_hi", "tprel_hi", "got_pcrel_hi", "tls_ie_pcrel_hi", "tls_gd_pcrel_hi")
    private val roundingEncodings = setOf("0", "1", "2", "3", "4", "7")
    private val csrImmediateInstructions = setOf("csrrwi", "csrrsi", "csrrci", "csrwi", "csrsi", "csrci")
    private val signed12 = Constraint(-2048L..2047L)
    private val unsigned20 = Constraint(0L..1048575L)
    private val unsigned12 = Constraint(0L..4095L)
    private val unsigned5 = Constraint(0L..31L)
    private val compressedWordAddresses = setOf("c.lw", "c.sw", "c.flw", "c.fsw", "c.lwsp", "c.swsp", "c.flwsp", "c.fswsp")
    private val compressedDoubleAddresses = setOf("c.ld", "c.sd", "c.fld", "c.fsd", "c.ldsp", "c.sdsp", "c.fldsp", "c.fsdsp")
    private val symbolicLoads = setOf("lb", "lbu", "lh", "lhu", "lw", "lwu", "ld")
    private val baseAddresses = symbolicLoads + setOf("sb", "sh", "sw", "sd", "flw", "fld", "fsw", "fsd", "jalr")

    private fun shiftConstraint(name: String, target: IsaTarget?) =
        Constraint(0L..if (name.endsWith('w') || target?.xlen == 32) 31L else 63L)

    private fun compressedImmediate(name: String, target: IsaTarget?): Constraint? = when (name) {
        "c.addi", "c.addiw", "c.andi", "c.li" -> Constraint(-32L..31L)
        "c.slli", "c.srli", "c.srai" -> shiftConstraint(name, target)
        "c.addi16sp" -> Constraint(-512L..496L, 16, nonzero = true)
        "c.addi4spn" -> Constraint(4L..1020L, 4)
        else -> null
    }

    private fun checkImmediate(operand: Operand, constraint: Constraint, target: IsaTarget?, report: (Operand, String) -> Unit) {
        val modifier = relocation.matchEntire(operand.text)?.groupValues?.get(1)
        if (modifier != null) {
            if ((constraint == signed12 && modifier in highRelocations) || (constraint == unsigned20 && modifier in lowRelocations)) {
                report(operand, "此立即数不接受 %$modifier 重定位")
            }
            return
        }
        val raw = GasExpression.evaluate(operand.text) { null } ?: return
        val value = if (constraint == signed12 && raw in 0x80000000L..0xffffffffL) {
            when (target?.xlen) {
                32 -> raw.toInt().toLong()
                64 -> raw
                else -> return
            }
        } else raw
        when {
            value !in constraint.range -> report(operand, "立即数应在 ${constraint.range.first}..${constraint.range.last} 范围内")
            value % constraint.alignment != 0L -> report(operand, "立即数必须是 ${constraint.alignment} 的倍数")
            constraint.nonzero && value == 0L -> report(operand, "立即数不能为 0")
        }
    }

    private fun checkAddress(name: String, operand: Operand, target: IsaTarget?, report: (Operand, String) -> Unit) {
        val opening = operand.text.lastIndexOf('(')
        if (opening < 0 || !operand.text.endsWith(')')) return
        val prefix = operand.slice(0, opening)
        if (prefix.text.startsWith('%') && !prefix.text.endsWith(')')) return // A relocation alone has no base register.
        val base = operand.slice(opening + 1, operand.text.lastIndex)
        if (prefix.text.isEmpty() && name in symbolicLoads && base.text !in RiscVIsa.integerRegisters) return
        if (identifier.matches(base.text) && base.text !in RiscVIsa.integerRegisters) {
            report(base, "内存基址需要整数寄存器：${base.text}")
        } else if (name.startsWith("c.") && name.endsWith("sp") && base.text in RiscVIsa.integerRegisters && base.text !in setOf("sp", "x2")) {
            report(base, "此压缩指令的内存基址必须为 sp（x2）")
        }
        val width = when (name) {
            in compressedWordAddresses -> 4
            in compressedDoubleAddresses -> 8
            else -> null
        }
        val constraint = if (width != null) {
            Constraint(0L..((if (name.endsWith("sp")) 63 else 31) * width).toLong(), width)
        } else if (name in baseAddresses) signed12 else return
        if (prefix.text.isNotEmpty()) checkImmediate(prefix, constraint, target, report)
    }

}
