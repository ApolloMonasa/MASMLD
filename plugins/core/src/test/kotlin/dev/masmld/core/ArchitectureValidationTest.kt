package dev.masmld.core

import com.intellij.lang.Language
import dev.masmld.core.api.ArchitectureProfile
import dev.masmld.core.api.ArchitectureProvider
import dev.masmld.core.language.AssemblyLanguage
import dev.masmld.core.registry.ArchitectureValidator
import org.junit.Assert.*
import org.junit.Test

class ArchitectureValidationTest {
    private object TestLanguageA : Language(AssemblyLanguage, "MASMLD Test A")
    private object TestLanguageB : Language(AssemblyLanguage, "MASMLD Test B")
    private object TestLanguageC : Language(AssemblyLanguage, "MASMLD Test C")
    private object OtherLanguage : Language("Other Test")
    private data class Provider(
        override val id: String,
        override val displayName: String = "Test architecture",
        override val language: Language = TestLanguageA,
        override val profiles: List<ArchitectureProfile> = listOf(ArchitectureProfile("base", "Base")),
    ) : ArchitectureProvider
    @Test fun `new architectures are accepted without core dispatch changes`() {
        val result = ArchitectureValidator.validate(listOf(Provider("example.arm"), Provider("example.x86", language = TestLanguageB)))
        assertTrue(result.problems.isEmpty())
        assertEquals(listOf("example.arm", "example.x86"), result.providers.map { it.id })
    }
    @Test fun `optional modules can all be absent`() {
        val result = ArchitectureValidator.validate(emptyList())
        assertTrue(result.providers.isEmpty())
        assertTrue(result.problems.isEmpty())
    }
    @Test fun `all duplicate IDs are excluded without depending on registration order`() {
        val first = Provider("duplicate")
        val second = Provider("duplicate", language = TestLanguageB)
        for (providers in listOf(listOf(first, second), listOf(second, first))) {
            val result = ArchitectureValidator.validate(providers + Provider("valid", language = TestLanguageC))
            assertEquals(listOf("valid"), result.providers.map { it.id })
            assertTrue(result.problems.all { it.contains("duplicate architecture ID") })
        }
    }
    @Test fun `shared languages cannot silently select another architecture`() {
        val language = TestLanguageA
        val result = ArchitectureValidator.validate(listOf(Provider("a", language = language), Provider("b", language = language)))
        assertTrue(result.providers.isEmpty())
        assertTrue(result.problems.all { it.contains("language is shared") })
    }
    @Test fun `blank or unstable IDs and empty names are rejected`() {
        for (provider in listOf(Provider(""), Provider(" Has Spaces"), Provider("valid", displayName = " "))) {
            val result = ArchitectureValidator.validate(listOf(provider))
            assertTrue(result.providers.isEmpty())
            assertTrue(result.problems.isNotEmpty())
        }
    }
    @Test fun `generic and unrelated languages are rejected`() {
        for (language in listOf(AssemblyLanguage, OtherLanguage)) {
            val result = ArchitectureValidator.validate(listOf(Provider("a", language = language)))
            assertTrue(result.providers.isEmpty())
            assertTrue(result.problems.single().contains("distinct dialect"))
        }
    }
    @Test fun `profile IDs are unique and metadata is validated`() {
        for (profiles in listOf(
            listOf(ArchitectureProfile("base", "A"), ArchitectureProfile("base", "B")),
            listOf(ArchitectureProfile("", "A")), listOf(ArchitectureProfile("base", "")),
        )) assertTrue(ArchitectureValidator.validate(listOf(Provider("a", profiles = profiles))).providers.isEmpty())
        assertEquals(1, ArchitectureValidator.validate(listOf(Provider("a", profiles = emptyList()))).providers.size)
    }
    @Test fun `provider exceptions do not remove other valid extensions`() {
        val bad = object : ArchitectureProvider {
            override val id: String get() = error("broken provider")
            override val displayName = "bad"
            override val language: Language = TestLanguageA
            override val profiles = emptyList<ArchitectureProfile>()
        }
        val result = ArchitectureValidator.validate(listOf(bad, Provider("valid")))
        assertEquals(listOf("valid"), result.providers.map { it.id })
        assertEquals(1, result.problems.size)
    }
    @Test fun `validated metadata is detached from mutable provider lists`() {
        val profiles = mutableListOf(ArchitectureProfile("base", "Base"))
        val result = ArchitectureValidator.validate(listOf(Provider("valid", profiles = profiles)))
        profiles.clear()
        assertEquals(1, result.providers.single().profiles.size)
    }

    @Test fun `linkage errors isolate an incompatible extension`() {
        val bad = object : ArchitectureProvider {
            override val id: String get() = throw NoClassDefFoundError("old optional API")
            override val displayName = "old"
            override val language: Language = TestLanguageA
            override val profiles = emptyList<ArchitectureProfile>()
        }
        val result = ArchitectureValidator.validate(listOf(bad, Provider("valid")))
        assertEquals(listOf("valid"), result.providers.map { it.id })
        assertTrue(result.problems.single().contains("incompatible"))
    }
    @Test fun `cancellation is propagated rather than reported as invalid metadata`() {
        for (error in listOf(java.util.concurrent.CancellationException(), com.intellij.openapi.progress.ProcessCanceledException())) {
            val cancelled = object : ArchitectureProvider {
                override val id: String get() = throw error
                override val displayName = "cancelled"
                override val language: Language = TestLanguageA
                override val profiles = emptyList<ArchitectureProfile>()
            }
            assertThrows(error.javaClass) { ArchitectureValidator.validate(listOf(cancelled)) }
        }
    }
    @Test fun `malformed Java profile lists do not escape metadata isolation`() {
        @Suppress("UNCHECKED_CAST")
        val profiles = listOf<ArchitectureProfile?>(null) as List<ArchitectureProfile>
        val result = ArchitectureValidator.validate(listOf(Provider("bad", profiles = profiles), Provider("valid", language = TestLanguageB)))
        assertEquals(listOf("valid"), result.providers.map { it.id })
        assertEquals(1, result.problems.size)
    }

}
