plugins {
    kotlin("jvm") version "2.4.20" apply false
    id("org.jetbrains.intellij.platform") version "2.19.0" apply false
}
allprojects {
    group = "dev.masmld"
    version = providers.gradleProperty("pluginVersion").get()
}

val pluginModules = listOf("core", "arch-riscv", "linker-script")
val releaseVersion = version.toString()
val pluginBuildDirectories = pluginModules.associateWith { project(":plugins:$it").layout.buildDirectory }
val verifierConfiguration = providers.provider { project(":plugins:core").configurations.getByName("intellijPluginVerifier") }
val verifierReportsDirectory = layout.buildDirectory.dir("reports/pluginVerifier")
val verifierListFile = layout.buildDirectory.file("verifier/plugins.txt")
val verifierLocalIde = providers.gradleProperty("localIdePath")
val additionalVerificationIde = providers.gradleProperty("additionalVerificationIdePath")

// Validate locally built plugins together so Core is available as a dependency.
tasks.register<JavaExec>("verifyAllPlugins") {
    group = "verification"
    description = "Verify all local plugin ZIPs against the local IDE without Marketplace downloads"
    dependsOn(pluginModules.map { ":plugins:$it:buildPlugin" })
    classpath(verifierConfiguration)
    mainClass = "com.jetbrains.pluginverifier.PluginVerifierMain"
    doFirst {
        val ide = verifierLocalIde.orNull?.let(::file)
            ?: throw GradleException("verifyAllPlugins requires -PlocalIdePath=<IDE installation root>")
        require(ide.resolve("product-info.json").isFile) { "localIdePath must contain product-info.json" }
        val ideTargets = listOfNotNull(ide, additionalVerificationIde.orNull?.let(::file)).distinct()
        ideTargets.forEach { require(it.resolve("product-info.json").isFile) { "Verification IDE must contain product-info.json: $it" } }
        val reports = verifierReportsDirectory.get().asFile
        reports.deleteRecursively()
        reports.mkdirs()
        val list = verifierListFile.get().asFile
        list.parentFile.mkdirs()
        list.writeText(pluginModules.joinToString("\n", postfix = "\n") {
            pluginBuildDirectories.getValue(it).file("distributions/$it-$releaseVersion.zip").get().asFile.absolutePath
        })
        args(listOf("check-plugin", "@${list.absolutePath}") + ideTargets.map { it.absolutePath } + listOf(
            "-offline", "-runtime-dir", ide.resolve("jbr").absolutePath,
            "-verification-reports-dir", verifierReportsDirectory.get().asFile.absolutePath))
    }
    doLast {
        val reports = verifierReportsDirectory.get().asFile
        val ids = listOf("dev.masmld.core", "dev.masmld.riscv", "dev.masmld.linker")
        for (id in ids) {
            val verdicts = reports.walkTopDown().filter {
                it.name == "verification-verdict.txt" && it.parentFile.name == releaseVersion && it.parentFile.parentFile.name == id
            }.toList()
            require(verdicts.isNotEmpty()) { "Verifier produced no verdict for $id" }
            for (verdict in verdicts) {
                require(verdict.readText().trim().startsWith("Compatible")) { "$id: ${verdict.readText().trim()}" }
                for (name in listOf("compatibility-problems.txt", "internal-api-usages.txt", "override-only-usages.txt", "invalid-plugin.txt")) {
                    val problem = verdict.parentFile.resolve(name)
                    require(!problem.isFile || problem.readText().isBlank()) { "$id: see ${problem.absolutePath}" }
                }
            }
        }
    }
}

// Produces a reviewable local bundle. It neither signs nor publishes plugins.
tasks.register<Exec>("releaseBundle") {
    group = "distribution"
    description = "Test, verify and package independent plugin ZIPs with checksums and evidence"
    dependsOn("verifyAllPlugins")
    dependsOn(pluginModules.flatMap { module -> listOf(
        ":plugins:$module:test", ":plugins:$module:verifyPluginStructure", ":plugins:$module:verifyPluginProjectConfiguration",
    ) })
    commandLine("python3", layout.projectDirectory.file("tools/release/assemble.py").asFile.absolutePath)
}
