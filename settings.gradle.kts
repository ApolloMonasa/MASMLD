pluginManagement {
    repositories {
        providers.gradleProperty("pluginRepository").orNull?.let { maven(it) }
        gradlePluginPortal()
        mavenCentral()
    }
}
rootProject.name = "MASMLD"
include(":plugins:core", ":plugins:arch-riscv", ":plugins:linker-script")
