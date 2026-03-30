rootProject.name = "strepitus"

pluginManagement {
    repositories {
        maven("https://maven.luna5ama.dev")
        gradlePluginPortal()
    }

    plugins {
        id("dev.luna5ama.jar-optimizer") version "1.2-SNAPSHOT"
        id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    }
}