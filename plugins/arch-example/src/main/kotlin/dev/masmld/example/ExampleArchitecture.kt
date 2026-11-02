package dev.masmld.example

import com.intellij.lang.Language
import dev.masmld.core.api.*
import dev.masmld.core.language.*

/** A tutorial registration, deliberately not an implementation of ARM/x86 or a real ISA. */
object ExampleLanguage : Language(AssemblyLanguage, "MASMLD Example")
class ExampleArchitecture : ArchitectureProvider {
    override val id = "example"
    override val displayName = "Example (developer tutorial)"
    override val language: Language = ExampleLanguage
    override val profiles = listOf(ArchitectureProfile("example-v1", "Example profile"))
}
class ExampleParser : AssemblyParserDefinition(ExampleLanguage)
