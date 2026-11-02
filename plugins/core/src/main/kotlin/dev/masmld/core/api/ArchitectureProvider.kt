package dev.masmld.core.api

import com.intellij.lang.Language

/**
 * Implemented by optional architecture plugins. Metadata must remain stable while loaded.
 * IDs use [a-z][a-z0-9._-]*. Each language must be a distinct AssemblyLanguage dialect.
 * Invalid metadata, duplicate architecture IDs and shared languages are rejected as a group.
 * Providers may omit profiles; profile IDs, when present, must be unique within the provider.
 */
interface ArchitectureProvider {
    val id: String
    val displayName: String
    val language: Language
    val profiles: List<ArchitectureProfile>
}

data class ArchitectureProfile(val id: String, val displayName: String)

/** Optional target/dialect validation. Existing providers need not implement this capability. */
interface ArchitectureOptionsValidator {
    /** Null means supported; a non-null message is shown before settings are saved. No side effects. */
    fun validateOptions(profileId: String, dialect: String, target: String): String?
}
