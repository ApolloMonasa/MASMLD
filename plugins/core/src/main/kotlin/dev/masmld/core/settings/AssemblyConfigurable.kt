package dev.masmld.core.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.util.FileContentUtil
import dev.masmld.core.registry.ArchitectureRegistry
import java.awt.BorderLayout
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class AssemblyConfigurable(private val project: Project) : Configurable {
    private data class Choice(val id: String, val label: String) {
        override fun toString() = label
    }
    private var choices: JComboBox<Choice>? = null
    override fun getDisplayName() = "MASMLD Assembly"
    override fun createComponent(): JComponent {
        val settings = AssemblySettings.getInstance(project)
        val entries = mutableListOf(Choice("", "Generic assembly"))
        entries += ArchitectureRegistry.available().map { Choice(it.id, it.displayName) }
        if (entries.none { it.id == settings.architectureId }) {
            entries += Choice(settings.architectureId, "Unavailable: ${settings.architectureId}")
        }
        val combo = JComboBox(entries.toTypedArray())
        choices = combo
        reset()
        return JPanel(BorderLayout(8, 8)).apply {
            add(JLabel("Project assembly architecture:"), BorderLayout.NORTH)
            add(combo, BorderLayout.CENTER)
            add(JLabel("Applies to MASMLD assembly files. Missing modules fall back to generic assembly."), BorderLayout.SOUTH)
        }
    }
    private fun selectedId() = (choices?.selectedItem as? Choice)?.id
    override fun isModified() = selectedId()?.let { it != AssemblySettings.getInstance(project).architectureId } ?: false
    override fun apply() {
        val id = selectedId() ?: return
        if (!isModified) return
        AssemblySettings.getInstance(project).selectArchitecture(id)
        FileContentUtil.reparseFiles(project, emptyList(), true)
    }
    override fun reset() {
        val combo = choices ?: return
        val id = AssemblySettings.getInstance(project).architectureId
        combo.selectedItem = (0 until combo.itemCount).map { combo.getItemAt(it) }.firstOrNull { it.id == id }
    }
    override fun disposeUIResources() { choices = null }
}
