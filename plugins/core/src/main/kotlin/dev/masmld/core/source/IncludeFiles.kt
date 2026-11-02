package dev.masmld.core.source

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.*
import com.intellij.psi.impl.FakePsiElement
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import dev.masmld.core.settings.AssemblySettings

object IncludeFiles {
    fun resolve(file: PsiFile, name: String): PsiFile? {
        if (name.isBlank() || name.contains('\u0000')) return null
        if (!java.io.File(name).isAbsolute) {
            file.virtualFile?.parent?.findFileByRelativePath(name)?.takeIf { !it.isDirectory && it.length <= 1_000_000 }?.let {
                return PsiManager.getInstance(file.project).findFile(it)
            }
        }
        val project = file.project
        val paths = listOfNotNull(file.virtualFile?.parent?.path, project.basePath) + AssemblySettings.getInstance(project).state.includePaths.map {
            if (java.io.File(it).isAbsolute) it else "${project.basePath}/$it"
        }
        val candidates = if (java.io.File(name).isAbsolute) listOf(name) else paths.map { "$it/$name" }
        for (path in candidates) {
            val normalized = try { java.nio.file.Path.of(path).normalize().toString().replace('\\', '/') } catch (_: java.nio.file.InvalidPathException) { continue }
            val vf = LocalFileSystem.getInstance().findFileByPath(normalized) ?: continue
            if (!vf.isDirectory && vf.length <= 1_000_000) return PsiManager.getInstance(project).findFile(vf)
        }
        return null
    }
    fun expand(file: PsiFile, assembly: Boolean): Pair<ExpandedSource, Map<String, PsiFile>> {
        val files = linkedMapOf<String, PsiFile>()
        fun unit(psi: PsiFile): SourceUnit {
            val id = psi.virtualFile?.url ?: "memory:${System.identityHashCode(psi)}"
            files[id] = psi
            return SourceUnit(id, psi.text)
        }
        val root = unit(file)
        val expanded = SourceGraph.expand(root, assembly) { parent, path ->
            ProgressManager.checkCanceled()
            files[parent.id]?.let { resolve(it, path) }?.let(::unit)
        }
        return expanded to files
    }
    fun references(element: PsiElement, assembly: Boolean): List<PsiReference> {
        val base = element.textRange.startOffset
        return SourceGraph.includes(element.containingFile.text, assembly).filter {
            it.start >= base && it.end <= element.textRange.endOffset
        }.map { include -> object : PsiReferenceBase<PsiElement>(element, TextRange(include.start - base, include.end - base), true) {
            override fun resolve(): PsiElement? = resolve(element.containingFile, include.path)
            override fun getVariants(): Array<Any> = emptyArray()
            override fun handleElementRename(newElementName: String): PsiElement {
                if (newElementName.isBlank() || newElementName.any { it in "/\\\u0000" }) throw com.intellij.util.IncorrectOperationException("Invalid include filename")
                val path = include.path.substring(0, include.path.lastIndexOfAny(charArrayOf('/', '\\')) + 1) + newElementName
                val file = element.containingFile
                val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: throw com.intellij.util.IncorrectOperationException("Include file is not editable")
                val start = element.textRange.startOffset + rangeInElement.startOffset
                val quoted = start > 0 && document.charsSequence[start - 1] in "\"'"
                val replacement = if (!quoted && path.any(Char::isWhitespace)) "\"$path\"" else path
                document.replaceString(start, element.textRange.startOffset + rangeInElement.endOffset, replacement)
                PsiDocumentManager.getInstance(file.project).commitDocument(document)
                return file.findElementAt(start)?.parent ?: file
            }
        } }
    }
    fun target(file: PsiFile, offset: Int, name: String): PsiElement = OffsetTarget(file, offset, name)
}
private class OffsetTarget(private val file: PsiFile, private val offset: Int, private val symbol: String) : FakePsiElement() {
    override fun getParent(): PsiElement = file
    override fun getContainingFile() = file
    override fun getName() = symbol
    override fun getTextOffset() = offset
    override fun getTextRange() = TextRange(offset, offset + symbol.length)
    override fun canNavigate() = file.virtualFile != null
    override fun canNavigateToSource() = canNavigate()
    override fun navigate(requestFocus: Boolean) { file.virtualFile?.let { OpenFileDescriptor(file.project, it, offset).navigate(requestFocus) } }
    override fun isValid() = file.isValid
    override fun equals(other: Any?) = other is OffsetTarget && other.file == file && other.offset == offset
    override fun hashCode() = 31 * file.hashCode() + offset
}
