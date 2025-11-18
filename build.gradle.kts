import org.jetbrains.compose.reload.gradle.ComposeHotRun

group = "dev.luna5ama"
version = "0.0.1-SNAPSHOT"

plugins {
    kotlin("jvm") version libs.versions.kotlin
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.hotReload)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        @Suppress("UnstableApiUsage")
        vendor.set(JvmVendorSpec.JETBRAINS)
    }
}

compose.desktop {
    application {
        mainClass = "dev.luna5ama.strepitus.MainKt"
    }
}

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven("https://central.sonatype.com/repository/maven-snapshots/")
    maven("https://maven.luna5ama.dev")
}

dependencies {
    implementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
    }

    implementation(platform("org.lwjgl:lwjgl-bom:${libs.versions.lwjgl.get()}"))

    implementation("org.lwjgl", "lwjgl")
    implementation("org.lwjgl", "lwjgl-glfw")
    implementation("org.lwjgl", "lwjgl-opengl")
    implementation("org.lwjgl", "lwjgl-stb")
    implementation("org.lwjgl", "lwjgl-jawt")
    runtimeOnly("org.lwjgl", "lwjgl", classifier = "natives-windows")
    runtimeOnly("org.lwjgl", "lwjgl-glfw", classifier = "natives-windows")
    runtimeOnly("org.lwjgl", "lwjgl-opengl", classifier = "natives-windows")
    runtimeOnly("org.lwjgl", "lwjgl-stb", classifier = "natives-windows")

    implementation(libs.fastutil)
    implementation(libs.apache.commons.rng.simple)

    implementation(libs.joml)
    implementation(libs.collectionJvm)
    implementation(libs.compose.fluent)
    implementation(libs.compose.fluent.icons)

    implementation(libs.bundles.kotlinEcosystem)
    implementation(libs.bundles.glWrapper)
    implementation(libs.bundles.kmogus)
}

tasks.withType<ComposeHotRun>().configureEach {
    systemProperties["strepitus.devenv"] = true
}