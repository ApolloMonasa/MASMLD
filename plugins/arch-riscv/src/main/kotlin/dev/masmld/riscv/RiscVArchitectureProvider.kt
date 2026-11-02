package dev.masmld.riscv

import com.intellij.lang.Language
import dev.masmld.core.api.ArchitectureProfile
import dev.masmld.core.api.ArchitectureProvider
import dev.masmld.core.language.AssemblyLanguage

object RiscVLanguage : Language(AssemblyLanguage, "MASMLD RISC-V")

class RiscVArchitectureProvider : ArchitectureProvider {
    override val id = "riscv"
    override val displayName = "RISC-V (GNU assembler)"
    override val language: Language = RiscVLanguage
    override val profiles = listOf(
        ArchitectureProfile("rv32i", "RV32I"),
        ArchitectureProfile("rv64i", "RV64I"),
    )
}
