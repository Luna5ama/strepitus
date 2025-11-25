package dev.luna5ama.strepitus.util

import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.nfd.NFDFilterItem
import org.lwjgl.util.nfd.NativeFileDialog
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString


data class DialogFilter(
    val typeName: String,
    val extensions: List<String>
)

sealed interface DialogResult {
    data class Success(val filePath: Path) : DialogResult
    data class Error(val message: String) : DialogResult
    object Canceled : DialogResult
}

private fun List<DialogFilter>.toFilterN(
    stack: MemoryStack
): NFDFilterItem.Buffer {
    val filtersBuffer = NFDFilterItem.calloc(size, stack)
    forEachIndexed { i, it ->
        filtersBuffer[i]
            .name(stack.UTF8(it.typeName))
            .spec(stack.UTF8(it.extensions.joinToString(",")))
    }
    return filtersBuffer
}

private fun handleNFDResult(result: Int, outPathPtr: PointerBuffer): DialogResult {
    when (result) {
        NativeFileDialog.NFD_OKAY -> {
            val pathStr = outPathPtr.getStringUTF8(0)
            NativeFileDialog.NFD_FreePath(outPathPtr.get())
            return DialogResult.Success(Path(pathStr))
        }

        NativeFileDialog.NFD_CANCEL -> {
            return DialogResult.Canceled
        }

        NativeFileDialog.NFD_ERROR -> {
            val err = NativeFileDialog.NFD_GetError() ?: "Unknown error"
            return DialogResult.Error(err)
        }

        else -> {
            return DialogResult.Error("Unknown result $result")
        }
    }
}

fun showSaveDialog(filters: List<DialogFilter>, defaultName: String? = null, defaultPath: Path? = null): DialogResult {
    MemoryStack.stackPush().use { stack ->
        val filtersBuffer = filters.toFilterN(stack)
        val defaultPathN = defaultPath?.let { stack.UTF8(it.absolutePathString()) }
        val defaultNameN = defaultName?.let { stack.UTF8(it) }

        val outPathPtr = stack.mallocPointer(1)
        val result = NativeFileDialog.NFD_SaveDialog(outPathPtr, filtersBuffer, defaultPathN, defaultNameN)
        return handleNFDResult(result, outPathPtr)
    }
}

fun showOpenDialog(filters: List<DialogFilter>, defaultPath: Path? = null): DialogResult {
    MemoryStack.stackPush().use { stack ->
        val filtersBuffer = filters.toFilterN(stack)
        val defaultPathN = defaultPath?.let { stack.UTF8(it.absolutePathString()) }

        val outPathPtr = stack.mallocPointer(1)
        val result = NativeFileDialog.NFD_OpenDialog(outPathPtr, filtersBuffer, defaultPathN)
        return handleNFDResult(result, outPathPtr)
    }
}