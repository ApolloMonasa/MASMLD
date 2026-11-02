package dev.masmld.core.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

/** Paths are project-relative with / separators; last matching rule wins. */
data class AssemblyRule(var pattern: String = "", var architecture: String = "", var profile: String = "", var dialect: String = "gnu", var target: String = "")
data class AssemblySelection(val architecture: String, val profile: String, val dialect: String, val target: String)

@State(name = "MASMLDAssemblySettings", storages = [Storage("masmld.xml")])
class AssemblySettings : PersistentStateComponent<AssemblySettings.State>, com.intellij.openapi.util.ModificationTracker {
    private val tracker = com.intellij.openapi.util.SimpleModificationTracker()
    override fun getModificationCount() = tracker.modificationCount
    data class State(var architectureId: String = "", var profileId: String = "", var dialect: String = "gnu",
        var target: String = "", var rules: MutableList<AssemblyRule> = mutableListOf(), var includePaths: MutableList<String> = mutableListOf())
    private var current = State()
    val architectureId: String get() = current.architectureId
    fun selectArchitecture(id: String) { current = current.copy(architectureId = id); tracker.incModificationCount() }
    fun selection(path: String): AssemblySelection = select(current, path)
    override fun getState(): State = current.copy(rules = current.rules.map { it.copy() }.toMutableList(), includePaths = current.includePaths.toMutableList())
    override fun loadState(state: State) { current = state.copy(rules = state.rules.map { it.copy() }.toMutableList(), includePaths = state.includePaths.toMutableList()); tracker.incModificationCount() }
    companion object {
        fun getInstance(project: Project): AssemblySettings = project.getService(AssemblySettings::class.java)
        fun select(state: State, path: String): AssemblySelection {
            val rule = state.rules.lastOrNull { matches(it.pattern, path) }
            return rule?.let { AssemblySelection(it.architecture, it.profile.ifBlank { if (it.architecture == state.architectureId) state.profileId else "" }, it.dialect, it.target.ifBlank { if (it.architecture == state.architectureId) state.target else "" }) }
                ?: AssemblySelection(state.architectureId, state.profileId, state.dialect, state.target)
        }
        fun matches(pattern: String, path: String): Boolean {
            if (pattern.isBlank()) return false
            val normalized = pattern.replace('\\', '/').removePrefix("./")
            val name = path.replace('\\', '/').removePrefix("./")
            if (normalized.endsWith('/')) return name.startsWith(normalized)
            val regex = StringBuilder("^")
            var i = 0
            while (i < normalized.length) {
                when {
                    normalized.startsWith("**/", i) -> { regex.append("(?:.*/)?"); i += 3 }
                    normalized.startsWith("**", i) -> { regex.append(".*"); i += 2 }
                    normalized[i] == '*' -> { regex.append("[^/]*"); i++ }
                    normalized[i] == '?' -> { regex.append("[^/]"); i++ }
                    else -> { regex.append(Regex.escape(normalized[i].toString())); i++ }
                }
            }
            return Regex(regex.append('$').toString()).matches(name)
        }
    }
}
