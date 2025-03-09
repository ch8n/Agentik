import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    kotlin("plugin.serialization") version "1.9.10"
}

group = "dev.ch8n"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    // Note, if you develop a library, you should use compose.desktop.common.
    // compose.desktop.currentOs should be used in launcher-sourceSet
    // (in a separate module for demo project and in testMain).
    // With compose.desktop.common you will also lose @Preview functionality
    implementation(compose.desktop.currentOs)
    implementation("dev.langchain4j:langchain4j:0.36.2")
    implementation("dev.langchain4j:langchain4j-ollama:0.36.2")
    implementation("org.jsoup:jsoup:1.16.1")
    implementation("com.microsoft.playwright:playwright:1.40.0")
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.7.0.202309050840-r")
    implementation("org.testcontainers:testcontainers:1.20.4")

    implementation("org.jetbrains.kotlin:kotlin-scripting-jsr223:1.9.20")

    implementation("org.xerial:sqlite-jdbc:3.36.0.3")


    val ktor_version = "3.1.0"
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    // Logging
    implementation("io.github.microutils:kotlin-logging:2.0.11")
    implementation("org.slf4j:slf4j-simple:1.7.32")

    implementation("org.neo4j.driver:neo4j-java-driver:5.9.0")
    implementation("com.kuzudb:kuzu:0.8.0")
    implementation("org.jgrapht:jgrapht-core:1.5.1") // For graph manipulation, can be used as MutableGraph
    implementation("nl.cwts:networkanalysis:1.3.0")

    implementation(kotlin("test"))

    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
    implementation("net.bramp.ffmpeg:ffmpeg:0.8.0")

    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.9.0") // Use the latest Kotlin Compiler version
    implementation("org.jetbrains.intellij.deps:trove4j:1.0.20181211") // Required for PSI Parsing
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Agentik"
            packageVersion = "1.0.0"
        }
    }
}
