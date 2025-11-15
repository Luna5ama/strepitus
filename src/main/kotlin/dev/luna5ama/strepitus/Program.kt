package dev.luna5ama.strepitus

import org.lwjgl.opengl.GL30.*

class Program {
    val id: Int by lazy {
        val vertexShaderSource = getTextResource(resName = "/dev/luna5ama/strepitus/shaders/myshader.vert")
        val fragmentShaderSource = getTextResource(resName = "/dev/luna5ama/strepitus/shaders/myshader.frag")
        val vertexShaderId = createShader(source = vertexShaderSource, type = GL_VERTEX_SHADER)
        val fragmentShaderId = createShader(source = fragmentShaderSource, type = GL_FRAGMENT_SHADER)
        createProgram(vertexShaderId = vertexShaderId, fragmentShaderId = fragmentShaderId)
    }

    val transformationMatrixUniformLocation: Int by lazy {
        glGetUniformLocation(id, TRANSFORMATION_MATRIX_UNIFORM_NAME)
    }

    fun bind() {
        glUseProgram(id)
        glEnableVertexAttribArray(POSITION_LOCATION)
    }

    fun unbind() {
        glDisableVertexAttribArray(POSITION_LOCATION)
        glUseProgram(0)
    }

    fun dispose() {
        unbind()
        glDeleteProgram(id)
    }

    private fun getTextResource(resName: String): String =
        javaClass.getResource(resName)?.readText() ?: throw IllegalArgumentException("Resource not found: '$resName'")

    companion object {
        const val POSITION_LOCATION = 0
        const val POSITION_VERTEX_SIZE = 2 //2D
        const val TRANSFORMATION_MATRIX_UNIFORM_NAME = "transformationMatrix"
    }
}

private fun createShader(source: String, type: Int): Int {
    val shaderId = glCreateShader(type)
    glShaderSource(shaderId, source)
    glCompileShader(shaderId)
    val status = glGetShaderi(shaderId, GL_COMPILE_STATUS)
    if (status == GL_FALSE) {
        val infoLogLength = glGetShaderi(shaderId, GL_INFO_LOG_LENGTH)
        val infoLog = glGetShaderInfoLog(shaderId, infoLogLength)
        System.err.printf("Compile failure in %s shader:\n%s\n", infoLog)
    }
    return shaderId
}

private fun createProgram(vertexShaderId: Int, fragmentShaderId: Int): Int {
    val program = glCreateProgram()
    glAttachShader(program, vertexShaderId)
    glAttachShader(program, fragmentShaderId)
    glLinkProgram(program)
    val status = glGetProgrami(program, GL_LINK_STATUS)
    if (status == GL_FALSE) {
        val infoLogLength = glGetProgrami(program, GL_INFO_LOG_LENGTH)
        val infoLog = glGetProgramInfoLog(program, infoLogLength)
        System.err.printf("Linker failure: %s\n", infoLog)
    }
    glDetachShader(program, vertexShaderId)
    glDeleteShader(vertexShaderId)
    glDetachShader(program, fragmentShaderId)
    glDeleteShader(fragmentShaderId)
    return program
}