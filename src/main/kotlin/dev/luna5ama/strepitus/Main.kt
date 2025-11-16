package dev.luna5ama.strepitus

import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import io.github.composefluent.*

fun main() {
    application {
        Window(
            onCloseRequest = ::exitApplication,
            icon = null,
            title = "Strepitus",
            state = rememberWindowState(width = 1920.dp, height = 1080.dp)
        ) {
            FluentTheme {
                App()
            }
        }
    }
}