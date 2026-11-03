package dev.masmld.riscv.symbols

import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import dev.masmld.core.source.IncludeFiles
import dev.masmld.core.source.ExpandedSource
import dev.masmld.riscv.psi.RiscVSymbolDefinition

class RiscVProgram(val expanded: ExpandedSource, val files: Map<String, PsiFile>) {
    private val conditions = RiscVConditions(expanded.text, expanded.unresolvedIncludes)
    val symbols = RiscVSymbols.analyze(expanded.text, conditions)
    fun macroTargetsAt(file: PsiFile, offset: Int): Set<Int> {
        val id = files.entries.firstOrNull { it.value == file }?.key ?: return emptySet()
        val mapped = if (offset > 0) expanded.expandedOffset(id, offset - 1)?.plus(1) else expanded.expandedOffset(id, offset)
        return conditions.macrosAt(mapped ?: 0)
    }
    internal val macros by lazy { dev.masmld.riscv.editing.RiscVMacros(this) }
    private val scopes by lazy { dev.masmld.riscv.editing.RiscVTargetScopes.build(expanded.text, dev.masmld.riscv.editing.RiscVIsa.target(files.values.first())) }
    fun targetAt(file: PsiFile, offset: Int): dev.masmld.riscv.editing.IsaTarget? {
        val id = files.entries.firstOrNull { it.value == file }?.key ?: return dev.masmld.riscv.editing.RiscVIsa.target(file)
        val mapped = expanded.expandedOffset(id, offset) ?: expanded.expandedOffset(id, (offset - 1).coerceAtLeast(0))?.plus(1) ?: 0
        return dev.masmld.riscv.editing.RiscVTargetScopes.at(scopes, mapped)
    }
    fun references(file: PsiFile, start: Int, end: Int): List<Pair<AssemblySymbolReference, Int>> = symbols.references.mapNotNull { ref ->
        val site = expanded.site(ref.start) ?: return@mapNotNull null
        if (files[site.unit.id] != file || site.offset < start || site.offset + ref.end - ref.start > end) null else ref to site.offset
    }.distinctBy { it.second }
    fun definition(def: AssemblySymbolDefinition): PsiElement? {
        val site = expanded.site(def.start) ?: return null
        val file = files[site.unit.id] ?: return null
        return file.findElementAt(site.offset)?.parent?.takeIf { it is RiscVSymbolDefinition }
            ?: IncludeFiles.target(file, site.offset, def.name)
    }
    companion object {
        fun get(file: PsiFile): RiscVProgram = CachedValuesManager.getCachedValue(file) {
            val (expanded, files) = IncludeFiles.expand(file, true)
            CachedValueProvider.Result.create(RiscVProgram(expanded, files), PsiModificationTracker.MODIFICATION_COUNT,
                VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS, dev.masmld.core.settings.AssemblySettings.getInstance(file.project))
        }
    }
}
