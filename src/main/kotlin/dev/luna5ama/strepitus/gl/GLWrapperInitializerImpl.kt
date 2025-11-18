package dev.luna5ama.strepitus.gl

import dev.luna5ama.glwrapper.base.*
import java.io.File
import java.net.URI

class GLWrapperInitializerImpl : GLWrapperInitializer by GLWrapperInitializerLWJGL3() {
    override val priority: Int = 999

    override fun createPathResolver(): ShaderPathResolver {
        return if (System.getProperty("strepitus.devenv").toBoolean()) {
            println("Using development environment ShaderPathResolver")
            PathResolverImpl()
        } else {
            super.createPathResolver()
        }
    }

    class PathResolverImpl : ShaderPathResolver {
        private val root = PathImpl(File("../src/main/resources").toURI())

        override fun resolve(path: String): ShaderPathResolver.Path {
            return PathImpl(root.uri.resolve("./$path"))
        }

        private inner class PathImpl(val uri: URI) : ShaderPathResolver.Path {
            override val url = uri.toURL()
            override fun resolve(path: String): ShaderPathResolver.Path {
                if (path.startsWith('/')) {
                    return this@PathResolverImpl.resolve(path)
                }
                return PathImpl(uri.resolve(path))
            }
        }
    }
}