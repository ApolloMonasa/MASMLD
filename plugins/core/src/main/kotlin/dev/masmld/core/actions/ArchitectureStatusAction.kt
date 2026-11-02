package dev.masmld.core.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import dev.masmld.core.registry.ArchitectureRegistry
import dev.masmld.core.settings.AssemblySettings

class ArchitectureStatusAction : AnAction(), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        val available = ArchitectureRegistry.available()
        val selected = event.project?.let { AssemblySettings.getInstance(it).architectureId }.orEmpty()
        val effective = ArchitectureRegistry.find(selected)?.displayName ?: "Generic assembly"
        val modules = available.joinToString("\n") {
            "${it.displayName} (${it.id}): ${it.profiles.joinToString { p -> p.displayName }}"
        }
        Messages.showInfoMessage(event.project,
            "Active architecture: $effective\n\nInstalled architectures:\n${modules.ifEmpty { "None" }}\n\nSelect the project architecture in Settings > Languages & Frameworks > MASMLD Assembly.",
            "MASMLD Architecture Status")
    }
}
