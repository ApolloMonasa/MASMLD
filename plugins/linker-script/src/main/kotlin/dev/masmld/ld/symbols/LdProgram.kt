package dev.masmld.ld.symbols

import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.util.*
import dev.masmld.core.source.*

class LdProgram(val expanded: ExpandedSource, val files: Map<String, PsiFile>) {
    val symbols = LdSymbolAnalysis.analyze(expanded.text)
    val problems by lazy { dev.masmld.ld.editing.LdDiagnostics.analyze(symbols) }
    fun target(def: LdDefinition): PsiElement? {
        val site = expanded.site(def.start) ?: return null
        val file = files[site.unit.id] ?: return null
        return file.findElementAt(site.offset)?.parent?.takeIf { it is LdSymbolDefinition } ?: IncludeFiles.target(file, site.offset, def.name)
    }
    companion object {
        fun get(file: PsiFile): LdProgram = CachedValuesManager.getCachedValue(file) {
            val (expanded, files) = IncludeFiles.expand(file, false)
            CachedValueProvider.Result.create(LdProgram(expanded, files), PsiModificationTracker.MODIFICATION_COUNT, VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS,
                dev.masmld.core.settings.AssemblySettings.getInstance(file.project))
        }
    }
}
class LdStatement(node: com.intellij.lang.ASTNode) : com.intellij.extapi.psi.ASTWrapperPsiElement(node) {
    override fun getReferences(): Array<PsiReference> {
        val file = containingFile; val program = LdProgram.get(file)
        val refs = program.symbols.references.mapNotNull { ref ->
            val site = program.expanded.site(ref.start) ?: return@mapNotNull null
            if (program.files[site.unit.id] != file || site.offset < textRange.startOffset || site.offset + ref.end - ref.start > textRange.endOffset) return@mapNotNull null
            object : PsiPolyVariantReferenceBase<LdStatement>(this, TextRange(site.offset - textRange.startOffset, site.offset + ref.end - ref.start - textRange.startOffset), true) {
                override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> = program.symbols.resolve(ref).mapNotNull(program::target).map { PsiElementResolveResult(it) }.toTypedArray()
                override fun getVariants(): Array<Any> = emptyArray()
                override fun handleElementRename(newElementName: String): PsiElement {
                    val leaf = file.findElementAt(element.textRange.startOffset + rangeInElement.startOffset) ?: return element
                    LdSymbolDefinition.renameToken(leaf, newElementName)
                    return element
                }
            }
        }.distinctBy { it.rangeInElement }
        return (refs + IncludeFiles.references(this, false)).toTypedArray()
    }
    override fun getReference() = references.singleOrNull()
}
