package dev.masmld.core.language

import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import dev.masmld.core.registry.ArchitectureRegistry
import dev.masmld.core.settings.AssemblySettings

/** Routes the shared file type to an optional architecture's lexical highlighter. */
class AssemblySyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter {
        if (project == null) return PlainSyntaxHighlighter()
        val provider = ArchitectureRegistry.find(AssemblySettings.getInstance(project).architectureId)
            ?: return PlainSyntaxHighlighter()
        if (provider.language == AssemblyLanguage) return PlainSyntaxHighlighter()
        val factory = getLanguageFactory().forLanguage(provider.language)
        // A new architecture may not have registered a highlighter yet.
        if (factory is AssemblySyntaxHighlighterFactory) return PlainSyntaxHighlighter()
        return factory.getSyntaxHighlighter(project, virtualFile)
    }
}
