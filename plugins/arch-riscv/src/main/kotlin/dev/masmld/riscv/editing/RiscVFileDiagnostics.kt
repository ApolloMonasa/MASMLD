package dev.masmld.riscv.editing

import com.intellij.psi.PsiFile
import com.intellij.psi.util.*
import com.intellij.openapi.util.Key
import dev.masmld.core.settings.AssemblySettings

object RiscVFileDiagnostics {
    private val key = Key.create<CachedValue<List<AssemblyProblem>>>("masmld.riscv.diagnostics")
    fun get(file: PsiFile): List<AssemblyProblem> = CachedValuesManager.getCachedValue(file, key) {
        val program = dev.masmld.riscv.symbols.RiscVProgram.get(file)
        val instructionProblems = RiscVDiagnostics.analyze(program.expanded.text, RiscVIsa.target(file), program.symbols.references.filter { it.macroTargets.isNotEmpty() }.map { it.start }.toSet())
        val problems = (instructionProblems + program.macros.problems()).mapNotNull { problem ->
            val site = program.expanded.site(problem.start) ?: return@mapNotNull null
            if (program.files[site.unit.id] != file) null else problem.copy(start = site.offset, end = site.offset + problem.end - problem.start)
        }
        CachedValueProvider.Result.create(problems, PsiModificationTracker.MODIFICATION_COUNT, com.intellij.openapi.vfs.VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS, AssemblySettings.getInstance(file.project))
    }
}
