package dev.masmld.riscv.editing

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.SmartPointerManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.PsiNavigateUtil
import dev.masmld.riscv.RiscVLanguage
import dev.masmld.riscv.symbols.RiscVProgram
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JPanel

class RiscVMacroPreviewAction : AnAction(), DumbAware {
    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible =
            event.getData(CommonDataKeys.PSI_FILE)?.language == RiscVLanguage && event.getData(CommonDataKeys.EDITOR) != null
    }

    override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.BGT

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val file = event.getData(CommonDataKeys.PSI_FILE) ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitDocument(editor.document)
        val program = RiscVProgram.get(file)
        val call = program.macros.callAt(file, editor.caretModel.offset)
        val text = if (call == null) {
            "此处无法预览宏调用。请在宏调用所在行操作；如果该行已有宏调用，请检查定义、包含文件和条件汇编。"
        } else buildString {
            for ((name, value) in call.bindings) appendLine("$name = $value")
            if (call.bindings.values.any { it.contains('⟨') }) appendLine("⟨名称⟩ 表示由汇编器在当前迭代中求值的表达式。")
            for (message in call.problems + call.previewNotes) appendLine(message)
            if (call.problems.isEmpty()) {
                appendLine()
                append(call.expansion())
            }
        }
        val target = call?.let { program.symbols.resolve(it.reference).singleOrNull() }?.let(program::definition)
        val pointer = target?.let { SmartPointerManager.createPointer(it) }
        object : DialogWrapper(project) {
            init {
                title = "MASMLD: GNU as 宏展开预览"
                setOKButtonText("关闭")
                init()
            }

            override fun createActions() = arrayOf(okAction)

            override fun createCenterPanel() = JPanel(BorderLayout()).apply {
                add(JBScrollPane(JBTextArea(text, 16, 72).apply {
                    isEditable = false
                    caretPosition = 0
                }), BorderLayout.CENTER)
                if (pointer != null) add(JButton("跳到宏定义").apply {
                    addActionListener {
                        close(OK_EXIT_CODE)
                        pointer.element?.let(PsiNavigateUtil::navigate)
                    }
                }, BorderLayout.SOUTH)
            }
        }.show()
    }
}
