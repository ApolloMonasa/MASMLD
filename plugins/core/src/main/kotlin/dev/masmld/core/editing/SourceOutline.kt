package dev.masmld.core.editing

import com.intellij.ide.structureView.*
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.psi.PsiFile
import javax.swing.Icon

data class OutlineEntry(val name: String, val offset: Int, val kind: String)
object SourceOutline {
    fun builder(file: PsiFile, entries: () -> List<OutlineEntry>): TreeBasedStructureViewBuilder = object : TreeBasedStructureViewBuilder() {
        override fun createStructureViewModel(editor: Editor?): StructureViewModel = StructureViewModelBase(file, editor, Node(file, null, entries))
    }
    private class Node(private val file: PsiFile, private val entry: OutlineEntry?, private val entries: () -> List<OutlineEntry>) : StructureViewTreeElement {
        override fun getValue(): Any = entry?.let { file.findElementAt(it.offset)?.parent } ?: file
        override fun getPresentation() = object : ItemPresentation {
            override fun getPresentableText() = entry?.name ?: file.name
            override fun getLocationString() = entry?.kind
            override fun getIcon(unused: Boolean): Icon? = file.fileType.icon
        }
        override fun getChildren(): Array<TreeElement> = if (entry != null) emptyArray() else entries().map { Node(file, it, entries) }.toTypedArray()
        override fun canNavigate() = file.virtualFile != null
        override fun canNavigateToSource() = canNavigate()
        override fun navigate(requestFocus: Boolean) { file.virtualFile?.let { OpenFileDescriptor(file.project, it, entry?.offset ?: 0).navigate(requestFocus) } }
    }
}
