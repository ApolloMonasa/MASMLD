package dev.masmld.core.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@State(name = "MASMLDAssemblySettings", storages = [Storage("masmld.xml")])
class AssemblySettings : PersistentStateComponent<AssemblySettings.State> {
    data class State(var architectureId: String = "")
    private var current = State()
    val architectureId: String get() = current.architectureId
    fun selectArchitecture(id: String) { current = State(id) }
    override fun getState(): State = current.copy()
    override fun loadState(state: State) { current = state.copy() }
    companion object {
        fun getInstance(project: Project): AssemblySettings = project.getService(AssemblySettings::class.java)
    }
}
