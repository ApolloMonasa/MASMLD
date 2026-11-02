plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
}
repositories {
    providers.gradleProperty("mavenRepository").orNull?.let { maven(it) }
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}
kotlin { jvmToolchain(25) }
dependencies {
    testImplementation("junit:junit:4.13.2")
    intellijPlatform {
        pluginVerifier("1.411")
        val localIde = providers.gradleProperty("localIdePath").orNull
        if (localIde != null) local(localIde)
        else rustRover(providers.gradleProperty("platformVersion"))
    }
}
intellijPlatform {
    // Kotlin-only preview: no Swing forms or Java nullability instrumentation.
    instrumentCode = false
    pluginVerification {
        ides { current() }
        if (providers.gradleProperty("verifierOffline").map(String::toBoolean).getOrElse(false)) freeArgs.add("-offline")
    }
    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("platformSinceBuild")
        }
    }
}

// Development-only composition. Release ZIPs keep the plugins independent.
intellijPlatformTesting.runIde.register("runIdeAll") {
    plugins {
        localPlugin(project.dependencies.project(mapOf("path" to ":plugins:arch-riscv")) as org.gradle.api.artifacts.ProjectDependency)
        localPlugin(project.dependencies.project(mapOf("path" to ":plugins:linker-script")) as org.gradle.api.artifacts.ProjectDependency)
    }
}

tasks.test { useJUnit() }
