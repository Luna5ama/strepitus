package dev.luna5ama.strepitus

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.awt.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.unit.*
import io.github.composefluent.*
import io.github.composefluent.component.*
import io.github.composefluent.component.rememberScrollbarAdapter
import io.github.composefluent.icons.*
import io.github.composefluent.icons.regular.*
import javax.swing.BoxLayout
import javax.swing.JPanel

@Composable
fun App() {
    var mainParameters by remember { mutableStateOf(MainParameters()) }
    var outputProcessingParameters by remember { mutableStateOf(OutputProcessingParameters()) }
    var viewerParameters by remember { mutableStateOf(ViewerParameters()) }
    var systemParameters by remember { mutableStateOf(SystemParameters()) }

    val darkMode = when (systemParameters.darkMode) {
        DarkModeOption.Auto -> isSystemInDarkTheme()
        DarkModeOption.Light -> false
        DarkModeOption.Dark -> true
    }

    FluentTheme(
        colors = if (darkMode) darkColors() else lightColors(),
    ) {
        Row(
            modifier = Modifier
                .background(color = FluentTheme.colors.background.mica.base)
        ) {
            var sideNavItem by remember { mutableStateOf(SideNavItem.General) }
            var sideNavExpanded by remember { mutableStateOf(false) }
            SideNav(
                expanded = sideNavExpanded,
                onExpandStateChange = { sideNavExpanded = it },
            ) {
                SideNavItem.entries.forEach { item ->
                    if (item == SideNavItem.Setting) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    SideNavItem(
                        selected = sideNavItem == item,
                        onClick = { if (it) sideNavItem = item },
                        icon = {
                            Icon(imageVector = item.icon, contentDescription = "")
                        },
                    ) {
                        Text(item.name)
                    }
                }
            }

            val scrollState = rememberScrollState()
            ScrollbarContainer(
                adapter = rememberScrollbarAdapter(scrollState)
            ) {
                Column(
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight()
                        .padding(8.dp)
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        sideNavItem.name,
                        style = FluentTheme.typography.title.copy(color = FluentTheme.colors.text.text.primary),
                        modifier = Modifier.padding(8.dp, bottom = 16.dp)
                    )
                    when (sideNavItem) {
                        SideNavItem.General -> {
                            ParameterEditor(
                                mainParameters,
                                { mainParameters = it }
                            )
                            ParameterEditor(
                                outputProcessingParameters,
                                { outputProcessingParameters = it }
                            )
                            ParameterEditor(
                                viewerParameters,
                                { viewerParameters = it }
                            )
                        }

                        SideNavItem.Noise -> {

                        }

                        SideNavItem.Setting -> {
                            ParameterEditor(
                                systemParameters,
                                { systemParameters = it }
                            )
                        }
                    }
                }
            }
            NoiseGeneratorPanel(
                mainParameters,
                outputProcessingParameters,
                viewerParameters,
                onScroll = {
                    viewerParameters =
                        viewerParameters.copy(zoom = viewerParameters.zoom - it.toBigDecimal() * 0.1.toBigDecimal())
                },
            )
        }
    }
}

enum class SideNavItem(val icon: ImageVector) {
    General(Icons.Default.Home),
    Noise(Icons.Default.GridDots),
    Setting(Icons.Default.Settings),
}

@Composable
fun NoiseGeneratorPanel(
    mainParameters: MainParameters,
    outputProcessingParameters: OutputProcessingParameters,
    viewerParameters: ViewerParameters,
    onScroll: (wheelRotation: Int) -> Unit,
) {
    val noiseGenerator by remember {
        mutableStateOf(
            LWJGLCanvas {
                NoiseGeneratorRenderer(
                    mainParameters,
                    outputProcessingParameters,
                    viewerParameters,
                )
            }
        )
    }
    if (noiseGenerator.mouseWheelListeners.isEmpty()) {
        noiseGenerator.addMouseWheelListener { onScroll(it.wheelRotation) }
    } else {
        noiseGenerator.mouseWheelListeners[0] = { onScroll(it.wheelRotation) }
    }
    SwingPanel(
        modifier = Modifier.fillMaxSize(),
        factory = {
            JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(noiseGenerator)
            }
        }
    )

    LaunchedEffect(Unit) {
        noiseGenerator.redraw()
    }

    noiseGenerator.update {
        it.mainParameters = mainParameters
        it.outputProcessingParameters = outputProcessingParameters
        it.viewerParameters = viewerParameters
    }

    DisposableEffect(Unit) {
        object : DisposableEffectResult {
            override fun dispose() {
                noiseGenerator.destroy()
            }
        }
    }
}