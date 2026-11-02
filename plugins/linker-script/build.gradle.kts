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
        val localIde = providers.gradleProperty("localIdePath").orNull
        if (localIde != null) local(localIde)
        else rustRover(providers.gradleProperty("platformVersion"))
        localPlugin(project(":plugins:core"))
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

tasks.test { useJUnit() }
