package dev.luna5ama.strepitus

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.unit.*
import dev.luna5ama.strepitus.params.*
import io.github.composefluent.*
import io.github.composefluent.component.*
import io.github.composefluent.component.rememberScrollbarAdapter
import io.github.composefluent.icons.*
import io.github.composefluent.icons.regular.*
import java.math.MathContext
import java.math.RoundingMode

val roundingMode = MathContext(4, RoundingMode.FLOOR)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun App(renderer: NoiseGeneratorRenderer) {
    var mainParameters by remember { mutableStateOf(MainParameters()) }
    var outputParameters by remember { mutableStateOf(OutputParameters()) }
    var viewerParameters by remember { mutableStateOf(ViewerParameters()) }
    var systemParameters by remember { mutableStateOf(SystemParameters()) }

    val noiseLayers = remember {
        mutableStateListOf(
            NoiseLayerParameters(
                baseSeed = NoiseLayerParameters.generateBaseSeed(0)
            )
        )
    }

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
                .background(color = Color.Transparent)
        ) {
            Row(
                modifier = Modifier
                    .background(color = FluentTheme.colors.background.mica.base)
            ) {
                var sideNavItem by remember { mutableStateOf(SideNavItem.Main) }
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
                            .width(480.dp)
                            .fillMaxHeight()
                            .padding(8.dp)
                            .verticalScroll(scrollState)
                    ) {
                        Text(
                            sideNavItem.name,
                            style = FluentTheme.typography.title.copy(color = FluentTheme.colors.text.text.primary),
                            modifier = Modifier.padding(8.dp, vertical = 12.dp)
                        )
                        when (sideNavItem) {
                            SideNavItem.Main -> {
                                ParameterEditor(
                                    mainParameters,
                                    { mainParameters = it }
                                )
                            }

                            SideNavItem.Output -> {
                                ParameterEditor(
                                    outputParameters,
                                    { outputParameters = it }
                                )
                            }

                            SideNavItem.Viewer -> {
                                ParameterEditor(
                                    viewerParameters,
                                    { viewerParameters = it }
                                )
                            }

                            SideNavItem.Noise -> {
                                NoiseLayerEditor(noiseLayers)
                            }

                            SideNavItem.Setting -> {
                                ParameterEditor(
                                    systemParameters,
                                    { systemParameters = it }
                                )
                                CardExpanderItem(heading = { }, icon = null) {
                                    Button(
                                        onClick = { renderer.reloadShaders() },
                                        buttonColors = ButtonDefaults.accentButtonColors()
                                    ) {
                                        Text("Reload Shaders")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged {
                        renderer.frameWidth = it.width
                        renderer.frameHeight = it.height
                    }
                    .scrollable(
                        orientation = Orientation.Vertical,
                        state = rememberScrollableState { delta ->
                            viewerParameters = viewerParameters.copy(
                                zoom = (viewerParameters.zoom + (delta / 1000.0).toBigDecimal()).round(roundingMode)
                            )
                            delta
                        }
                    )
                    .onDrag {
                        viewerParameters = viewerParameters.copy(
                            centerX = (viewerParameters.centerX - it.x.toBigDecimal()).round(roundingMode),
                            centerY = (viewerParameters.centerY - it.y.toBigDecimal()).round(roundingMode)
                        )
                    }
            ) {}
        }
    }

    renderer.mainParametersProvider = { mainParameters }
    renderer.outputParametersProvider = { outputParameters }
    renderer.viewerParametersProvider = { viewerParameters }
    renderer.noiseLayersProvider = { noiseLayers.toList() }
}

enum class SideNavItem(val icon: ImageVector) {
    Main(Icons.Default.Image),
    Noise(Icons.Default.GridDots),
    Output(Icons.Default.Filter),
    Viewer(Icons.Default.Eye),
    Setting(Icons.Default.Settings),
}