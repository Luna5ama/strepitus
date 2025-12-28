group = "dev.luna5ama"
version = "0.0.1"

plugins {
    kotlin("jvm") version libs.versions.kotlin
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.hotReload)
    alias(libs.plugins.jarOptimizer)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        @Suppress("UnstableApiUsage")
        vendor.set(JvmVendorSpec.JETBRAINS)
    }
}

val mainClassName = "dev.luna5ama.strepitus.MainKt"
compose.desktop {
    application {
        mainClass = mainClassName
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
    implementation("org.lwjgl", "lwjgl-nfd")
    val platforms = listOf("linux", "windows")
    platforms.forEach {
        runtimeOnly("org.lwjgl", "lwjgl", classifier = "natives-$it")
        runtimeOnly("org.lwjgl", "lwjgl-glfw", classifier = "natives-$it")
        runtimeOnly("org.lwjgl", "lwjgl-opengl", classifier = "natives-$it")
        runtimeOnly("org.lwjgl", "lwjgl-stb", classifier = "natives-$it")
        runtimeOnly("org.lwjgl", "lwjgl-nfd", classifier = "natives-$it")

        runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-$it-x64:0.9.22.2")
    }

    implementation(libs.kotlinxSerializationCore)
    implementation(libs.kotlinxSerializationJson)

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

afterEvaluate {
    val runDir = File(rootDir, "run")
    runDir.mkdir()
    tasks.withType<JavaExec>().configureEach {
        workingDir(runDir)
    }
}
tasks {
    val fatJar by registering(Jar::class) {
        group = "build"

        from(jar.get().archiveFile.map { zipTree(it) })
        from(configurations.runtimeClasspath.get().elements.map { set ->
            set.map {
                if (it.asFile.isDirectory) it else zipTree(
                    it
                )
            }
        })

        manifest {
            attributes["Main-Class"] = mainClassName
        }

        duplicatesStrategy = DuplicatesStrategy.INCLUDE

        archiveClassifier.set("fatjar")
    }

    val optimizeFatJar = jarOptimizer.register(
        fatJar,
        "dev.luna5ama.strepitus",
        "org.jetbrains.skia",
        "kotlin.reflect"
    )

    artifacts {
        archives(optimizeFatJar)
    }
}