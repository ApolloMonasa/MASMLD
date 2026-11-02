package dev.masmld.core.registry

import com.intellij.lang.Language
import dev.masmld.core.api.ArchitectureProfile
import dev.masmld.core.api.ArchitectureProvider
import dev.masmld.core.language.AssemblyLanguage

internal data class ArchitectureValidation(
    val providers: List<ArchitectureProvider>,
    val problems: List<String>,
)

/** Validates metadata without keeping extension instances alive between lookups. */
internal object ArchitectureValidator {
    private val stableId = Regex("[a-z][a-z0-9._-]*")
    private data class Snapshot(
        override val id: String,
        override val displayName: String,
        override val language: Language,
        override val profiles: List<ArchitectureProfile>,
    ) : ArchitectureProvider

    fun validate(candidates: List<ArchitectureProvider>): ArchitectureValidation {
        val problems = mutableListOf<String>()
        val snapshots = candidates.mapNotNull { provider ->
            try {
                Snapshot(provider.id, provider.displayName, provider.language, provider.profiles.map { ArchitectureProfile(it.id, it.displayName) })
            } catch (error: com.intellij.openapi.progress.ProcessCanceledException) {
                throw error
            } catch (error: java.util.concurrent.CancellationException) {
                throw error
            } catch (error: Exception) {
                problems += "${provider.javaClass.name}: failed to read architecture metadata (${error.javaClass.simpleName})"
                null
            } catch (error: LinkageError) {
                problems += "${provider.javaClass.name}: incompatible architecture metadata (${error.javaClass.simpleName})"
                null
            }
        }
        val duplicateIds = snapshots.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        val duplicateLanguages = snapshots.groupingBy { it.language.id }.eachCount().filterValues { it > 1 }.keys
        val accepted = snapshots.filter { provider ->
            val errors = mutableListOf<String>()
            if (!stableId.matches(provider.id)) errors += "ID must match [a-z][a-z0-9._-]*"
            if (provider.displayName.isBlank()) errors += "display name is blank"
            if (provider.id in duplicateIds) errors += "duplicate architecture ID"
            if (provider.language.id in duplicateLanguages) errors += "language is shared by multiple architectures"
            if (provider.language == AssemblyLanguage || !provider.language.isKindOf(AssemblyLanguage)) {
                errors += "language must be a distinct dialect of MASMLD Assembly"
            }
            if (provider.profiles.any { !stableId.matches(it.id) || it.displayName.isBlank() }) errors += "invalid profile metadata"
            if (provider.profiles.map { it.id }.distinct().size != provider.profiles.size) errors += "duplicate profile ID"
            if (errors.isNotEmpty()) problems += "${provider.id.ifBlank { "<empty ID>" }}: ${errors.joinToString("; ")}"
            errors.isEmpty()
        }
        return ArchitectureValidation(accepted.sortedBy { it.id }, problems.distinct().sorted())
    }
}
