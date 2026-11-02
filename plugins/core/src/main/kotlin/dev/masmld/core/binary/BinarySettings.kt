package dev.masmld.core.binary

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.options.Configurable
import java.awt.GridLayout
import javax.swing.*

@State(name = "MASMLDBinaryTools", storages = [Storage("masmld.xml")])
class BinarySettings : PersistentStateComponent<BinarySettings.State> {
    data class State(var artifact: String = "", var nm: String = "llvm-nm", var symbolizer: String = "llvm-symbolizer", var demangler: String = "llvm-cxxfilt")
    private var current = State()
    override fun getState() = current.copy()
    override fun loadState(state: State) { current = state.copy() }
    companion object { fun get(project: Project) = project.getService(BinarySettings::class.java) }
}
class BinaryConfigurable(private val project: Project) : Configurable {
    private val artifact = JTextField(); private val nm = JTextField(); private val symbolizer = JTextField(); private val demangler = JTextField()
    override fun getDisplayName() = "MASMLD Binary Symbols"
    override fun createComponent() = JPanel(GridLayout(0, 2, 8, 8)).apply {
        add(JLabel("Linked ELF/object file (optional):")); add(artifact)
        add(JLabel("nm executable (LLVM or GNU):")); add(nm)
        add(JLabel("Symbolizer executable (llvm-symbolizer or addr2line):")); add(symbolizer)
        add(JLabel("Demangler executable (llvm-cxxfilt or c++filt):")); add(demangler)
        add(JLabel("After building, run Tools → MASMLD: Refresh Binary Symbols.")); add(JLabel("Debug information is required for file/line navigation."))
        reset()
    }
    private fun value() = BinarySettings.State(artifact.text.trim(), nm.text.trim(), symbolizer.text.trim(), demangler.text.trim())
    override fun isModified() = value() != BinarySettings.get(project).state
    override fun apply() { BinarySettings.get(project).loadState(value()); BinarySymbols.get(project).clear() }
    override fun reset() { val s = BinarySettings.get(project).state; artifact.text = s.artifact; nm.text = s.nm; symbolizer.text = s.symbolizer; demangler.text = s.demangler }
}
