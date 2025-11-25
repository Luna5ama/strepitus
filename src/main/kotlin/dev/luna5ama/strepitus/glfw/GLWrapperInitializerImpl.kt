package dev.luna5ama.strepitus.glfw

import dev.luna5ama.glwrapper.base.*
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists

class GLWrapperInitializerImpl : GLWrapperInitializer by GLWrapperInitializerLWJGL3() {
    override val priority: Int = 999

    override fun createPathResolver(): ShaderPathResolver {
        return if (System.getenv("strepitus.devenv").toBoolean()) {
            println("Using development environment ShaderPathResolver")
            PathResolverImpl()
        } else {
            super.createPathResolver()
        }
    }

    class PathResolverImpl : ShaderPathResolver() {
        private val root: Path

        init {
            val firstPath = Path("../src/main/resources")
            root = if (firstPath.exists()) {
                firstPath.absolute()
            } else {
                Path("src/main/resources").absolute()
            }
        }

        override fun resolve0(path: String): Path {
            return root.resolve(path)
        }
    }
}