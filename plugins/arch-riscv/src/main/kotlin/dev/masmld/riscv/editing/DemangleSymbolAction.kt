package dev.masmld.riscv.editing

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.*
import com.intellij.psi.PsiDocumentManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.util.PsiNavigateUtil
import dev.masmld.core.binary.*
import dev.masmld.riscv.symbols.SourceSymbolBridge

/** Explicit background action keeps external demanglers off the reference-resolution/read-action path. */
class DemangleSymbolAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return
        val token = file.findElementAt(editor.caretModel.offset) ?: return
        val name = token.text
        if (!name.matches(Regex("[A-Za-z_.$][A-Za-z0-9_.$]*"))) return
        object : Task.Backgroundable(project, "Demangle $name", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val decoded = BinarySymbols.run(listOf(BinarySettings.get(project).state.demangler, name), 5000).trim()
                    require(decoded != name && decoded.isNotEmpty()) { "The configured tool could not demangle this symbol" }
                    val lookup = decoded.substringBefore('(').substringAfterLast("::").trim()
                    val targets = ApplicationManager.getApplication().runReadAction<List<com.intellij.psi.PsiElement>> { SourceSymbolBridge.find(token, lookup) }
                    ApplicationManager.getApplication().invokeLater {
                        if (!project.isDisposed && !editor.isDisposed) {
                            if (targets.isEmpty()) com.intellij.openapi.ui.Messages.showInfoMessage(project, "Demangled: $decoded\nNo indexed source target found", "MASMLD")
                            else JBPopupFactory.getInstance().createPopupChooserBuilder(targets).setTitle(decoded).setItemChosenCallback { PsiNavigateUtil.navigate(it) }.createPopup().showInBestPositionFor(editor)
                        }
                    }
                } catch (cancel: ProcessCanceledException) { throw cancel }
                catch (failure: Exception) { BinarySymbols.showFailure(project, failure) }
            }
        }.queue()
    }
}
