pluginManagement {
    repositories {
        providers.gradleProperty("pluginRepository").orNull?.let { maven(it) }
        gradlePluginPortal()
        mavenCentral()
    }
}
rootProject.name = "MASMLD"
include(":plugins:core", ":plugins:arch-riscv", ":plugins:linker-script")

// Developer tutorial only; never included in the three-plugin release bundle.
if (providers.gradleProperty("withExampleArchitecture").map(String::toBoolean).getOrElse(false)) include(":plugins:arch-example")
