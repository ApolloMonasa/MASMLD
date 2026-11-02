package dev.masmld.riscv.editing

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import dev.masmld.core.settings.AssemblySettings

class EnableIsaFix(private val target: String) : BaseIntentionAction() {
    override fun getText() = "设置当前文件的目标为 $target"
    override fun getFamilyName() = "MASMLD RISC-V ISA"
    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = file != null && RiscVIsa.parse(target) != null
    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        file ?: return
        val settings = AssemblySettings.getInstance(project)
        val state = settings.state
        val path = file.virtualFile?.path?.removePrefix(project.basePath.orEmpty() + "/") ?: return
        state.rules += dev.masmld.core.settings.AssemblyRule(path, "riscv", dialect = "gnu", target = target)
        settings.loadState(state)
        DaemonCodeAnalyzer.getInstance(project).restart(file, this)
    }
}
