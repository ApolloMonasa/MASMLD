package dev.masmld.core.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.util.FileContentUtil
import dev.masmld.core.registry.ArchitectureRegistry
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.*

class AssemblyConfigurable(private val project: Project) : Configurable {
    private data class Choice(val id: String, val label: String) { override fun toString() = label }
    private var choices: JComboBox<Choice>? = null
    private val profile = JTextField()
    private val target = JTextField()
    private val rules = JTextArea(8, 60)
    private val paths = JTextArea(3, 60)
    override fun getDisplayName() = "MASMLD Assembly"
    override fun createComponent(): JComponent {
        val settings = AssemblySettings.getInstance(project)
        val entries = mutableListOf(Choice("", "Generic assembly"))
        entries += ArchitectureRegistry.available().map { Choice(it.id, it.displayName) }
        if (entries.none { it.id == settings.architectureId }) entries += Choice(settings.architectureId, "Unavailable: ${settings.architectureId}")
        choices = JComboBox(entries.toTypedArray()).apply { addActionListener { profile.text = ""; target.text = "" } }
        return JPanel(BorderLayout(8, 8)).apply {
            add(JPanel(GridLayout(0, 2, 8, 8)).apply {
                add(JLabel("Project architecture:")); add(choices)
                add(JLabel("Profile ID (optional):")); add(profile)
                add(JLabel("Target options (architecture-specific):")); add(target)
                add(JLabel("Dialect:")); add(JLabel("GNU assembler / GNU ld"))
            }, BorderLayout.NORTH)
            add(JPanel(GridLayout(0, 1, 4, 4)).apply {
                add(JLabel("File/directory overrides: path | architecture ID | profile ID | dialect | target"))
                add(JScrollPane(rules))
                add(JLabel("Use dir/ or globs such as **/*.S; last match wins. Empty architecture selects Generic."))
                add(JLabel("Include search directories (one per line; relative to project or absolute):"))
                add(JScrollPane(paths))
            }, BorderLayout.CENTER)
            reset()
        }
    }
    private fun value(): AssemblySettings.State {
        val entries = rules.text.lineSequence().filter { it.isNotBlank() }.map { line ->
            val fields = line.split('|').map(String::trim)
            if (fields.size !in 2..5) throw ConfigurationException("Override must have 2–5 fields: path | architecture | profile | dialect | target")
            val dialect = fields.getOrNull(3).orEmpty().ifBlank { "gnu" }
            if (dialect != "gnu") throw ConfigurationException("Only the GNU dialect is currently installed")
            AssemblyRule(fields[0], fields[1], fields.getOrNull(2).orEmpty(), dialect, fields.getOrNull(4).orEmpty())
        }.toMutableList()
        return AssemblySettings.State((choices?.selectedItem as? Choice)?.id.orEmpty(), profile.text.trim(), "gnu", target.text.trim(), entries,
            paths.text.lineSequence().map(String::trim).filter(String::isNotEmpty).toMutableList())
    }
    override fun isModified(): Boolean = choices != null && try { value() != AssemblySettings.getInstance(project).state } catch (_: ConfigurationException) { true }
    override fun apply() {
        if (choices == null || !isModified) return
        val state = value()
        val selections = listOf(AssemblySelection(state.architectureId, state.profileId, state.dialect, state.target)) + state.rules.map {
            AssemblySelection(it.architecture, it.profile.ifBlank { if (it.architecture == state.architectureId) state.profileId else "" }, it.dialect, it.target.ifBlank { if (it.architecture == state.architectureId) state.target else "" })
        }
        for (selection in selections) {
            val validator = ArchitectureRegistry.find(selection.architecture) as? dev.masmld.core.api.ArchitectureOptionsValidator
            validator?.validateOptions(selection.profile, selection.dialect, selection.target)?.let { throw ConfigurationException(it) }
        }
        AssemblySettings.getInstance(project).loadState(state)
        FileContentUtil.reparseFiles(project, emptyList(), true)
    }
    override fun reset() {
        val combo = choices ?: return
        val state = AssemblySettings.getInstance(project).state
        combo.selectedItem = (0 until combo.itemCount).map { combo.getItemAt(it) }.firstOrNull { it.id == state.architectureId }
        profile.text = state.profileId; target.text = state.target
        rules.text = state.rules.joinToString("\n") { "${it.pattern} | ${it.architecture} | ${it.profile} | ${it.dialect} | ${it.target}" }
        paths.text = state.includePaths.joinToString("\n")
    }
    override fun disposeUIResources() { choices = null }
}
