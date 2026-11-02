package dev.masmld.riscv

import com.intellij.lang.Language
import dev.masmld.core.api.ArchitectureProfile
import dev.masmld.core.api.ArchitectureProvider
import dev.masmld.core.language.AssemblyLanguage

object RiscVLanguage : Language(AssemblyLanguage, "MASMLD RISC-V")

class RiscVArchitectureProvider : ArchitectureProvider, dev.masmld.core.api.ArchitectureOptionsValidator {
    override fun validateOptions(profileId: String, dialect: String, target: String): String? {
        if (dialect != "gnu") return "RISC-V 模块当前仅支持 GNU 方言"
        if (profileId.isNotBlank() && profiles.none { it.id == profileId }) return "未知的 RISC-V profile：$profileId"
        if (target.isNotBlank() && dev.masmld.riscv.editing.RiscVIsa.parse(target) == null) return "目标应为支持的 ISA 字符串，例如 rv64gc 或 rv32imc_zicsr"
        return null
    }
    override val id = "riscv"
    override val displayName = "RISC-V (GNU assembler)"
    override val language: Language = RiscVLanguage
    override val profiles = listOf(
        ArchitectureProfile("rv32i", "RV32I"),
        ArchitectureProfile("rv64i", "RV64I"),
    )
}
