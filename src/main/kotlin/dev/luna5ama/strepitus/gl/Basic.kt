package dev.luna5ama.strepitus.gl

import dev.luna5ama.glwrapper.*
import dev.luna5ama.glwrapper.base.GL_TRIANGLES
import dev.luna5ama.glwrapper.base.glDrawArrays
import dev.luna5ama.glwrapper.enums.GLDataType
import dev.luna5ama.glwrapper.objects.BufferObject
import dev.luna5ama.glwrapper.objects.VertexArrayObject
import dev.luna5ama.kmogus.MemoryStack

class Basic : IGLObjContainer by IGLObjContainer.Impl() {
    val blitVao = register(VertexArrayObject()).apply {
        val vbo = register(BufferObject.Immutable())
        MemoryStack {
            val arr = calloc(6 * 4)
            arr.ptr.setByteInc(-1)
                .setByteInc(1)
                .plus(2L)
                .setByteInc(-1)
                .setByteInc(-1)
                .plus(2L)
                .setByteInc(1)
                .setByteInc(1)
                .plus(2L)

                .setByteInc(1)
                .setByteInc(-1)
                .plus(2L)
                .setByteInc(1)
                .setByteInc(1)
                .plus(2L)
                .setByteInc(-1)
                .setByteInc(-1)
                .plus(2L)

            vbo.allocate(arr.len, arr.ptr, 0)
        }

        attachVbo(vbo, buildAttribute(4) {
            float(0, 2, GLDataType.GL_BYTE, false)
        })
    }

    fun drawQuad() {
        blitVao.bind()
        glDrawArrays(GL_TRIANGLES, 0, 6)
    }
}