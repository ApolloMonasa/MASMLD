package dev.masmld.riscv.editing

import dev.masmld.core.documentation.DocumentationKind
import dev.masmld.core.source.SourceText
import dev.masmld.core.source.SourceToken
import dev.masmld.riscv.documentation.RiscVDocumentationCatalog

data class AssemblyProblem(val start: Int, val end: Int, val message: String, val requiredTarget: String? = null)
object RiscVDiagnostics {
    fun operands(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val tokens = SourceText.tokens(text)
        if (tokens.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        var start = 0; var depth = 0
        for (token in tokens) {
            if (token.text == "(") depth++
            if (token.text == ")") depth--
            if (token.text == "," && depth == 0) { result += text.substring(start, token.start).trim(); start = token.end }
        }
        result += text.substring(start).substringBefore('#').substringBefore("//").trim()
        return result
    }
    fun signature(name: String): String? = RiscVDocumentationCatalog.entries.firstOrNull {
        it.name == name && it.kind in setOf(DocumentationKind.INSTRUCTION, DocumentationKind.PSEUDO_INSTRUCTION)
    }?.syntax
    fun analyze(source: String, target: IsaTarget?, macroCalls: Set<Int> = dev.masmld.riscv.symbols.RiscVSymbols.analyze(source).references.filter { it.macroTarget != null }.map { it.start }.toSet()): List<AssemblyProblem> {
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
                fun problem(message: String, replacement: String? = null) { result += AssemblyProblem(base + command.start, base + command.end, message, replacement) }
                if (entry != null) {
                    if (!RiscVIsa.supports(name, activeTarget)) {
                        val spec = RiscVIsa.instructions.firstOrNull { it.name == name }
                        val ext = activeTarget!!.extensions + spec?.extensions.orEmpty() + (if (name in RiscVIsa.csrAliases) setOf("zicsr") else emptySet()) + "i"
                        val width = spec?.xlen?.takeIf { it != 0 } ?: if (name in setOf("ld", "sd", "lwu") || name.endsWith('w') || name.endsWith("iw")) 64 else activeTarget.xlen
                        val replacement = "rv${width}" + "imafdc".filter { it.toString() in ext } + ext.filter { it.length > 1 }.sorted().joinToString("") { "_$it" }
                        problem("${name} 不适用于当前 XLEN/ISA 扩展配置", replacement)
                    }
                    val args = operands(line.substring(command.end))
                    val spec = RiscVIsa.instructions.firstOrNull { it.name == name }?.operands
                        ?: entry.syntax.removePrefix(name).trim().takeIf { !it.contains(';') && !it.contains('；') }
                    if (spec != null && !spec.contains("[rd,") && !spec.contains('|')) {
                        val expected = if (spec.isBlank()) emptyList() else spec.split(',').map(String::trim)
                        val minimum = expected.count { !it.startsWith('[') }
                        // Some GNU pseudo instructions have multiple accepted forms.
                        if (name !in setOf("jal", "jalr", "call", "fence") && args.size !in minimum..expected.size) problem("${name} 需要 $minimum–${expected.size} 个操作数，当前 ${args.size} 个")
                        for ((index, shape) in expected.withIndex()) {
                            val arg = args.getOrNull(index) ?: continue
                            val registerKind = when (shape) { "rd", "rs1", "rs2" -> "integer"; "fd", "fs1", "fs2", "fs3" -> "float"; else -> null }
                            if (registerKind != null && !arg.contains('\\') && !arg.contains("/*")) {
                                val names = if (registerKind == "integer") RiscVIsa.integerRegisters else RiscVIsa.floatingRegisters
                                if (arg !in names && arg.matches(Regex("[A-Za-z0-9_]+"))) problem("操作数 ${index + 1} 需要${if (registerKind == "integer") "整数" else "浮点"}寄存器：$arg")
                            }
                            if (shape.contains("(rs1)") && arg.endsWith(')')) {
                                val register = arg.substringAfterLast('(').dropLast(1).trim()
                                if (register !in RiscVIsa.integerRegisters && !register.contains('\\')) problem("内存基址需要整数寄存器：$register")
                            }
                            if (shape == "[rm]" && arg !in setOf("rne", "rtz", "rdn", "rup", "rmm", "dyn", "0", "1", "2", "3", "4", "7")) problem("无效的浮点舍入模式：$arg")
                        }
                    }
                    if (name in setOf("addi", "slti", "sltiu", "xori", "ori", "andi", "addiw")) args.getOrNull(2)?.toLongOrNull()?.let {
                        if (it !in -2048L..2047L) problem("12 位有符号立即数应在 -2048..2047 范围内")
                    }
                }
            }
            base = end + 1
        }
        return result
    }
}
