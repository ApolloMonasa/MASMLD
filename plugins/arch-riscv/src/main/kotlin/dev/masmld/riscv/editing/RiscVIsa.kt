package dev.masmld.riscv.editing

import dev.masmld.core.documentation.*

data class IsaTarget(val xlen: Int, val extensions: Set<String>)
data class IsaInstruction(val name: String, val operands: String, val extensions: Set<String>, val summary: String, val xlen: Int, val example: String)
object RiscVIsa {
    val csrAliases = mapOf("csrr" to "rd, csr", "csrw" to "csr, rs1", "csrs" to "csr, rs1", "csrc" to "csr, rs1", "csrwi" to "csr, uimm", "csrsi" to "csr, uimm", "csrci" to "csr, uimm")
    val csrNames = setOf("mstatus", "sstatus", "mie", "sie", "mip", "sip", "mtvec", "stvec", "mepc", "sepc", "mcause", "scause", "mtval", "stval", "satp", "mscratch", "sscratch", "mhartid", "cycle", "time", "instret", "fcsr", "frm", "fflags")
    val roundingModes = setOf("rne", "rtz", "rdn", "rup", "rmm", "dyn")
    val instructions: List<IsaInstruction> by lazy {
        checkNotNull(javaClass.getResourceAsStream("/catalog/riscv/extensions.tsv")).bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith('#') }.map { line ->
                val row = line.split('\t'); require(row.size == 6)
                IsaInstruction(row[0], row[1], row[2].split('+').toSet(), row[3], row[4].toInt(), row[5])
            }.toList()
        }
    }
    fun target(file: com.intellij.psi.PsiFile): IsaTarget? {
        val options = dev.masmld.core.settings.AssemblySettings.getInstance(file.project).selection(file.virtualFile?.path?.removePrefix(file.project.basePath.orEmpty() + "/").orEmpty())
        return parse(options.target.ifBlank { options.profile })
    }
    val floatingRegisters = (0..31).map { "f$it" }.toSet() + (0..11).map { "ft$it" } + (0..11).map { "fs$it" } + (0..7).map { "fa$it" }
    val integerRegisters = (0..31).map { "x$it" }.toSet() + listOf("zero", "ra", "sp", "gp", "tp", "fp") + (0..6).map { "t$it" } + (0..11).map { "s$it" } + (0..7).map { "a$it" }
    private val rv64 = setOf("ld", "sd", "lwu", "addiw", "slliw", "srliw", "sraiw", "addw", "subw", "sllw", "srlw", "sraw", "negw", "sext.w")
    fun parse(value: String): IsaTarget? {
        if (value.isBlank()) return null
        val match = Regex("rv(32|64)([imafdcg]+)((?:_[a-z][a-z0-9]*)*)").matchEntire(value.lowercase()) ?: return null
        val extensions = match.groupValues[2].map(Char::toString).toMutableSet()
        extensions += match.groupValues[3].split('_').filter(String::isNotBlank)
        if ("g" in extensions) extensions += setOf("i", "m", "a", "f", "d", "zicsr", "zifencei")
        if ("d" in extensions) extensions += "f"
        if ("i" !in extensions) return null
        return IsaTarget(match.groupValues[1].toInt(), extensions)
    }
    fun supports(name: String, target: IsaTarget?): Boolean {
        if (target == null) return true
        if (name in csrAliases && "zicsr" !in target.extensions) return false
        if (name in rv64 && target.xlen != 64) return false
        if (name in floatingRegisters && target.extensions.none { it in setOf("f", "d") }) return false
        val entry = instructions.firstOrNull { it.name == name } ?: return true
        return (entry.xlen == 0 || entry.xlen == target.xlen) && target.extensions.containsAll(entry.extensions)
    }
    fun documents(): List<DocumentationEntry> = instructions.map { entry -> DocumentationEntry(entry.name, DocumentationKind.INSTRUCTION,
        "${entry.name} ${entry.operands}".trim(), entry.summary,
        "本插件检查扩展、XLEN、基本操作数形状；不替代汇编器的完整立即数、特权和压缩编码校验。",
        entry.example,
        (if (entry.xlen == 0) "RV32 / RV64" else "RV${entry.xlen}") + "; " + entry.extensions.joinToString("+"), listOf(DocumentationSource(
            "riscv-unpriv", "RISC-V Unprivileged ISA", "https://docs.riscv.org/reference/isa/unpriv/", "2026-11-03 查阅", "事实参考；中文摘要由 MASMLD 原创"))) }
}
