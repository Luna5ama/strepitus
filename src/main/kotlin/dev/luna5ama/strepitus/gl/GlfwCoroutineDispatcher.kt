package dev.luna5ama.strepitus.gl

import kotlinx.coroutines.CoroutineDispatcher
import org.lwjgl.glfw.GLFW.glfwPollEvents
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext

class GlfwCoroutineDispatcher(private val windowHandle: Long) : CoroutineDispatcher() {
    private val tasks = ConcurrentLinkedQueue<Runnable>()
    private val tasksCopy = mutableListOf<Runnable>()
    private var isStopped = false

    fun runLoop() {
        while (!isStopped) {
            var runnable = tasks.poll()
            while (runnable != null) {
                tasksCopy.add(runnable)
                runnable = tasks.poll()
            }
            for (runnable in tasksCopy) {
                if (!isStopped) {
                    runnable.run()
                }
            }
            tasksCopy.clear()
            glfwPollEvents()
        }
    }

    fun stop() {
        isStopped = true
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        tasks.add(block)
    }
}