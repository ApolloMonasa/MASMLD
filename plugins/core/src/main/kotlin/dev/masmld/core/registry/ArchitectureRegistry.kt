package dev.masmld.core.registry

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import dev.masmld.core.api.ArchitectureProvider

object ArchitectureRegistry {
    private val extensions = ExtensionPointName.create<ArchitectureProvider>("dev.masmld.core.architecture")
    private val logger = Logger.getInstance(ArchitectureRegistry::class.java)
    // Only diagnostic strings are retained, never optional-plugin objects or classloaders.
    private var previousProblems: Set<String> = emptySet()

    @Synchronized
    private fun snapshot(): ArchitectureValidation {
        val result = ArchitectureValidator.validate(extensions.extensionList)
        val problems = result.problems.toSet()
        (problems - previousProblems).forEach { logger.warn("Ignoring invalid architecture extension: $it") }
        previousProblems = problems
        return result
    }
    fun available(): List<ArchitectureProvider> = snapshot().providers
    fun find(id: String?): ArchitectureProvider? = available().firstOrNull { it.id == id }
    fun problems(): List<String> = snapshot().problems
}
