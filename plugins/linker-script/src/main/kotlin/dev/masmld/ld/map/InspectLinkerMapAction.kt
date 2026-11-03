package dev.masmld.ld.map

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.PsiNavigateUtil
import dev.masmld.ld.LdLanguage
import dev.masmld.ld.symbols.LdProgram
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.io.IOException
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.RowFilter
import javax.swing.event.DocumentEvent
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableRowSorter

class InspectLinkerMapAction : AnAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val file = event.getData(CommonDataKeys.PSI_FILE)
        val virtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE)
        event.presentation.isEnabledAndVisible = file?.language == LdLanguage || virtualFile?.let(::isMapFile) == true
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val script = event.getData(CommonDataKeys.PSI_FILE)?.takeIf { it.language == LdLanguage }
        val current = event.getData(CommonDataKeys.VIRTUAL_FILE)
        val mapFile = if (script != null) chooseFile(project, current, "选择 GNU ld map 文件", ::isMapFile) ?: return else current ?: return
        if (mapFile.length > GnuLinkerMap.MAX_BYTES) {
            Messages.showWarningDialog(project, "当前支持读取不超过 16 MiB 的 GNU ld map 文件。", "MASMLD")
            return
        }
        val openText = FileDocumentManager.getInstance().getCachedDocument(mapFile)?.immutableCharSequence
        var result: GnuLinkerMap? = null
        val finished = ProgressManager.getInstance().runProcessWithProgressSynchronously(
            Runnable {
                result = try {
                    GnuLinkerMap.parse(openText ?: VfsUtilCore.loadText(mapFile))
                } catch (_: IOException) {
                    null
                }
            }, "读取 GNU ld map", true, project
        )
        if (!finished) return
        val parsed = result
        if (parsed == null) {
            Messages.showWarningDialog(project, "无法读取 GNU ld map。请使用 GNU ld 的 -Map 选项生成文本 map。", "MASMLD")
            return
        }
        val editor = event.getData(CommonDataKeys.EDITOR)
        val offset = editor?.caretModel?.offset
        val name = if (script != null && offset != null) {
            PsiDocumentManager.getInstance(project).commitDocument(editor.document)
            scriptNameAt(script, offset)
        } else null
        val line = if (script == null && offset != null) editor.document.getLineNumber(offset) else null
        LinkerMapDialog(project, mapFile, parsed, script, name, line).show()
    }

    private fun scriptNameAt(script: PsiFile, offset: Int): String? {
        val leaf = script.findElementAt(offset)?.takeIf { it.text.isNotBlank() }
            ?: (if (offset > 0) script.findElementAt(offset - 1) else null)?.takeIf { it.text.isNotBlank() } ?: return null
        return (leaf.parent as? dev.masmld.ld.symbols.LdSymbolDefinition)?.name
            ?: script.findReferenceAt(offset.coerceIn(leaf.textRange.startOffset, leaf.textRange.endOffset - 1))?.canonicalText
            ?: leaf.text.takeIf { it.isNotBlank() }
    }
}

private fun isMapFile(file: VirtualFile) = file.extension.equals("map", true) || file.name.endsWith(".map.txt", true)

private fun chooseFile(project: Project, near: VirtualFile?, title: String, matches: (VirtualFile) -> Boolean): VirtualFile? {
    val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
        .withTitle(title).withFileFilter(matches)
    return FileChooser.chooseFile(descriptor, project, near)
}

private class LinkerMapDialog(
    private val project: Project,
    private val mapFile: VirtualFile,
    private val map: GnuLinkerMap,
    private var script: PsiFile?,
    selectedName: String?,
    selectedLine: Int?,
) : DialogWrapper(project) {
    private val model = object : AbstractTableModel() {
        private val columns = listOf("名称", "类型", "地址", "大小", "来源目标文件")
        override fun getColumnCount() = columns.size
        override fun getColumnName(column: Int) = columns[column]
        override fun getRowCount() = map.entries.size
        override fun getValueAt(row: Int, column: Int): String {
            val entry = map.entries[row]
            return when (column) {
                0 -> entry.name
                1 -> entry.kind.label
                2 -> entry.address.mapHex()
                3 -> entry.size?.mapHex().orEmpty()
                else -> entry.objectFile.orEmpty()
            }
        }
    }
    private val sorter = TableRowSorter(model).apply {
        val hexOrder = Comparator<String> { left, right ->
            compareValues(left.removePrefix("0x").toULongOrNull(16), right.removePrefix("0x").toULongOrNull(16))
        }
        setComparator(2, hexOrder)
        setComparator(3, hexOrder)
    }
    private val table = JBTable(model).apply {
        rowSorter = sorter
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        emptyText.text = "没有匹配的 map 条目"
        columnModel.getColumn(4).preferredWidth = 320
    }
    private val filter = JBTextField().apply {
        emptyText.text = "筛选符号、段或目标文件"
    }
    private val details = JBTextArea(7, 72).apply { isEditable = false }
    private val jump = JButton("跳到脚本定义").apply { addActionListener { navigateToScript() } }
    private val openMap = JButton("跳到 map 条目").apply {
        addActionListener {
            selected()?.let {
                close(OK_EXIT_CODE)
                OpenFileDescriptor(project, mapFile, it.line, 0).navigate(true)
            }
        }
    }

    init {
        title = "MASMLD: GNU ld Map — ${mapFile.name}"
        setOKButtonText("关闭")
        init()
        table.selectionModel.addListSelectionListener { if (!it.valueIsAdjusting) refreshDetails() }
        filter.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                val query = filter.text.trim()
                sorter.rowFilter = if (query.isEmpty()) null else object : RowFilter<AbstractTableModel, Int>() {
                    override fun include(entry: Entry<out AbstractTableModel, out Int>): Boolean =
                        listOf(0, 4).any { entry.getStringValue(it).contains(query, ignoreCase = true) }
                }
                if (table.rowCount > 0 && table.selectedRow < 0) select(0)
                refreshDetails()
            }
        })
        if (selectedName != null && map.entries.any { it.name == selectedName }) filter.text = selectedName
        val initial = when {
            selectedLine != null -> map.entries.indexOfLast { it.line <= selectedLine }
            selectedName != null -> map.entries.indexOfFirst { it.name == selectedName }
            else -> 0
        }
        if (initial >= 0) select(table.convertRowIndexToView(initial))
        if (table.selectedRow < 0 && table.rowCount > 0) select(0)
        refreshDetails()
    }

    override fun createActions() = arrayOf(okAction)

    override fun createCenterPanel() = JPanel(BorderLayout(0, 8)).apply {
        preferredSize = Dimension(900, 510)
        add(filter, BorderLayout.NORTH)
        add(JBScrollPane(table), BorderLayout.CENTER)
        add(JPanel(BorderLayout()).apply {
            add(JBScrollPane(details), BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.LEADING)).apply {
                add(openMap)
                add(jump)
            }, BorderLayout.SOUTH)
        }, BorderLayout.SOUTH)
    }

    private fun select(row: Int) {
        if (row !in 0 until table.rowCount) return
        table.setRowSelectionInterval(row, row)
        table.scrollRectToVisible(table.getCellRect(row, 0, true))
    }

    private fun selected(): MapEntry? = table.selectedRow.takeIf { it >= 0 }?.let {
        map.entries.getOrNull(table.convertRowIndexToModel(it))
    }

    private fun output(entry: MapEntry) = map.output(entry)

    private fun refreshDetails() {
        val entry = selected()
        openMap.isEnabled = entry != null
        jump.isEnabled = entry != null && (entry.kind.scriptKind != null || entry.outputLine != null)
        details.text = if (entry == null) "" else buildString {
            appendLine("${entry.kind.label} ${entry.name}    地址 ${entry.address.mapHex()}")
            entry.size?.let { appendLine("大小 ${it.mapHex()}") }
            entry.loadAddress?.let { appendLine("加载地址 ${it.mapHex()}") }
            output(entry)?.let { appendLine("所属输出段 ${it.name}") }
            entry.inputSection?.let { appendLine("所属输入段 $it") }
            entry.objectFile?.let { appendLine("来源 $it") }
            if (entry.kind == MapKind.OUTPUT) {
                val inputs = map.inputs(entry).filter { it.size != 0uL }
                if (inputs.isNotEmpty()) appendLine("有内容的输入段")
                for (input in inputs.take(200)) {
                    appendLine("  ${input.name}  ${input.address.mapHex()}  ${input.objectFile}")
                }
                if (inputs.size > 200) appendLine("  还有 ${inputs.size - 200} 个输入段，可在表格中筛选。")
            }
        }
        details.caretPosition = 0
    }

    private fun navigateToScript() {
        val entry = selected() ?: return
        val targetEntry = if (entry.kind == MapKind.INPUT) output(entry) ?: return else entry
        if (targetEntry.kind.scriptKind == null) return
        val file = script?.takeIf { it.isValid } ?: run {
            val selected = chooseFile(project, mapFile, "选择生成此 map 的 GNU ld 脚本") { it.extension.equals("ld", true) } ?: return
            PsiManager.getInstance(project).findFile(selected)?.takeIf { it.language == LdLanguage } ?: return
        }
        script = file
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val targets = scriptTargets(file, targetEntry)
        when (targets.size) {
            0 -> Messages.showInfoMessage(project, "${file.name} 及其包含文件没有定义 ${targetEntry.name}。", "MASMLD")
            1 -> {
                close(OK_EXIT_CODE)
                PsiNavigateUtil.navigate(targets.single())
            }
            else -> JBPopupFactory.getInstance().createPopupChooserBuilder(targets)
                .setTitle("选择 ${targetEntry.name} 的脚本定义")
                .setItemChosenCallback {
                    close(OK_EXIT_CODE)
                    PsiNavigateUtil.navigate(it)
                }.createPopup().showUnderneathOf(jump)
        }
    }
}

internal fun scriptTargets(file: PsiFile, entry: MapEntry): List<com.intellij.psi.PsiElement> {
    val kind = entry.kind.scriptKind ?: return emptyList()
    val program = LdProgram.get(file)
    return program.symbols.definitions.filter { it.kind == kind && it.name == entry.name }
        .mapNotNull(program::target).distinct()
}
