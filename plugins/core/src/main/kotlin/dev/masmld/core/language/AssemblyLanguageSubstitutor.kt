package dev.masmld.core.language

import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.LanguageSubstitutor
import dev.masmld.core.registry.ArchitectureRegistry
import dev.masmld.core.settings.AssemblySettings

class AssemblyLanguageSubstitutor : LanguageSubstitutor() {
    override fun getLanguage(file: VirtualFile, project: Project): Language? =
        ArchitectureRegistry.find(AssemblySettings.getInstance(project).architectureId)?.language
}
