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
import dev.luna5ama.strepitus.gl.GlfwCoroutineDispatcher
import dev.luna5ama.strepitus.params.*
import io.github.composefluent.*
import io.github.composefluent.component.*
import io.github.composefluent.component.rememberScrollbarAdapter
import io.github.composefluent.icons.*
import io.github.composefluent.icons.regular.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import org.lwjgl.glfw.GLFW.*
import java.math.MathContext
import java.math.RoundingMode
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.*

val roundingMode = MathContext(4, RoundingMode.FLOOR)

class AppState(
    val windowHandle: Long,
    val dispatcher: GlfwCoroutineDispatcher,
    val scope: CoroutineScope,
) {
    var mainParameters by mutableStateOf(MainParameters())
    val noiseLayers = mutableStateListOf(
        NoiseLayerParameters(
            baseSeed = NoiseLayerParameters.generateBaseSeed(0)
        )
    )
    var outputParameters by mutableStateOf(OutputParameters())
    var viewerParameters by mutableStateOf(ViewerParameters())

    var systemParameters by mutableStateOf(SystemParameters())

    var persistentStates by mutableStateOf(PersistentStates())

    val errorPrompts = mutableStateListOf<String>()

    private val requestedClose0 = mutableStateOf(false)
    var requestedClose
        get() = requestedClose0.value
        set(value) {
            glfwSetWindowShouldClose(windowHandle, value)
        }

    init {
        glfwSetWindowCloseCallback(windowHandle) { _ ->
            requestedClose0.value = true
        }
    }

    private val lastSaved = AtomicInteger(Int.MIN_VALUE)
    private val changeCounter = AtomicInteger(Int.MIN_VALUE)

    val hasUnsavedChanges: Boolean
        get() = lastSaved.get() != changeCounter.get()

    private fun onChange() {
        changeCounter.incrementAndGet()
    }

    init {
        scope.launch {
            snapshotFlow { mainParameters }
                .collect {
                    onChange()
                }
        }
        scope.launch {
            snapshotFlow { noiseLayers.toList() }
                .collect {
                    onChange()
                }
        }
        scope.launch {
            snapshotFlow { outputParameters }
                .collect {
                    onChange()
                }
        }
        scope.launch {
            snapshotFlow { viewerParameters }
                .collect {
                    onChange()
                }
        }


        scope.launch {
            snapshotFlow { persistentStates }
                .collect {
                    val title = it.openedFile?.let { "${it.name} - Strepitus" } ?: "Strepitus"
                    glfwSetWindowTitle(windowHandle, title)
                }
        }
    }

    fun exitApp() {
        dispatcher.stop()
    }

    fun load() {
        loadNoise(NOISE_CONFIG_PATH)
        loadSystem(SYSTEM_CONFIG_PATH)
    }

    fun save() {
        saveNoise(NOISE_CONFIG_PATH)
        saveSystem(SYSTEM_CONFIG_PATH)
    }

    fun loadNoise(path: java.nio.file.Path) {
        runCatching {
            path.inputStream().use {
                val config = JSON.decodeFromString<NoiseConfig>(path.readText())
                mainParameters = config.mainParameters
                outputParameters = config.outputParameters
                viewerParameters = config.viewerParameters
                noiseLayers.clear()
                noiseLayers.addAll(config.noiseLayers)
            }
        }.onFailure {
            it.printStackTrace()
            errorPrompts += "Failed to load noise config: ${it.message}"
        }
    }

    fun saveNoise(path: java.nio.file.Path) {
        runCatching {
            lastSaved.set(changeCounter.get())
            val config = NoiseConfig(
                mainParameters = mainParameters,
                noiseLayers = noiseLayers.toList(),
                outputParameters = outputParameters,
                viewerParameters = viewerParameters
            )
            path.writeText(JSON.encodeToString(config))
        }.onFailure {
            it.printStackTrace()
            errorPrompts += "Failed to save noise config: ${it.message}"
        }
    }

    fun resetNoise() {
        persistentStates = persistentStates.copy(openedFile = null)
        mainParameters = MainParameters()
        outputParameters = OutputParameters()
        viewerParameters = ViewerParameters()
        noiseLayers.clear()
        noiseLayers.add(
            NoiseLayerParameters(
                baseSeed = NoiseLayerParameters.generateBaseSeed(0)
            )
        )
    }

    fun saveSystem(path: java.nio.file.Path) {
        runCatching {
            val config = SystemConfig(
                persistentStates = persistentStates,
                systemParameters = systemParameters
            )
            path.writeText(JSON.encodeToString(config))
        }.onFailure {
            it.printStackTrace()
            errorPrompts += "Failed to save system config: ${it.message}"
        }
    }

    fun loadSystem(path: java.nio.file.Path) {
        runCatching {
            path.inputStream().use {
                val config = JSON.decodeFromString<SystemConfig>(path.readText())
                persistentStates = config.persistentStates
                systemParameters = config.systemParameters
            }
        }.onFailure {
            it.printStackTrace()
            errorPrompts += "Failed to load system config: ${it.message}"
        }.onSuccess {
            persistentStates.openedFile?.let {
                loadNoise(it)
            }
        }
    }

    @Serializable
    data class PersistentStates(
        @Serializable(with = PathSerilizer::class)
        val openedFile: java.nio.file.Path? = null
    ) {
        companion object PathSerilizer : KSerializer<java.nio.file.Path> {
            override val descriptor: SerialDescriptor
                get() = PrimitiveSerialDescriptor("java.nio.file.Path", PrimitiveKind.STRING)

            override fun serialize(encoder: Encoder, value: java.nio.file.Path) {
                encoder.encodeString(value.toString())
            }

            override fun deserialize(decoder: Decoder): java.nio.file.Path {
                return Path(decoder.decodeString())
            }
        }
    }

    @Serializable
    private data class SystemConfig(
        val persistentStates: PersistentStates,
        val systemParameters: SystemParameters
    )

    @Serializable
    private data class NoiseConfig(
        val mainParameters: MainParameters,
        val noiseLayers: List<NoiseLayerParameters>,
        val outputParameters: OutputParameters,
        val viewerParameters: ViewerParameters
    )

    companion object {
        val SYSTEM_CONFIG_PATH = Path("system.json")
        val NOISE_CONFIG_PATH = Path("noise.json")

        @OptIn(ExperimentalSerializationApi::class)
        val JSON = Json {
            prettyPrint = true
            encodeDefaults = true
            prettyPrintIndent = "    "
            namingStrategy = JsonNamingStrategy.SnakeCase
        }
    }
}

@Composable
fun MenuFlyoutScope.MenuFlyoutButton(
    onClick: () -> Unit,
    icon: ImageVector? = null,
    text: String,
    trailingText: String? = null,
    enabled: Boolean = true,
) {
    MenuFlyoutItem(
        onClick = onClick,
        icon = icon?.let { { Icon(imageVector = it, contentDescription = null) } },
        text = { Text(text) },
        trailing = trailingText?.let {
            {
                Spacer(Modifier.width(16.dp))
                Text(it, style = FluentTheme.typography.caption)
            }
        },
        enabled = enabled,
    )
}


@Composable
fun App(renderer: NoiseGeneratorRenderer, appState: AppState) {
    var systemParameters by appState::systemParameters

    val darkMode = when (systemParameters.darkMode) {
        DarkModeOption.Auto -> isSystemInDarkTheme()
        DarkModeOption.Light -> false
        DarkModeOption.Dark -> true
    }

    FluentTheme(
        colors = if (darkMode) darkColors() else lightColors(),
    ) {
        appState.errorPrompts.firstOrNull()?.let { message ->
            ContentDialog(
                title = "Error",
                visible = true,
                primaryButtonText = "OK",
                onButtonClick = {
                    appState.errorPrompts.removeFirstOrNull()
                },
                content = { Text(message) }
            )
        }

        Column(
            modifier = Modifier
                .background(color = Color.Transparent)
        ) {
            AppMenuBar(renderer, appState)
            Row {
                Box(
                    modifier = Modifier
                        .background(color = FluentTheme.colors.background.mica.base)
                ) {
                    SideEditor(renderer, appState)
                }
                NoiseViewer(renderer, appState)
            }
        }
    }

}

data class DiscardConfirmState(
    val onSave: () -> Unit,
    val onDiscard: () -> Unit,
    val onCancel: () -> Unit
)

private fun saveProjectAs(appState: AppState): Boolean {
    val filters = listOf(
        DialogFilter("Project File", listOf("json"))
    )
    return when (val result = showSaveDialog(filters)) {
        is DialogResult.Success -> {
            runCatching {
                appState.saveNoise(result.filePath)
            }.onSuccess {
                appState.persistentStates = appState.persistentStates.copy(openedFile = result.filePath)
            }.onFailure {
                it.printStackTrace()
                appState.errorPrompts += "Failed to export image: ${it.message}"
            }.isSuccess
        }

        is DialogResult.Canceled -> {
            // User cancelled
            false
        }

        is DialogResult.Error -> {
            appState.errorPrompts += result.message
            false
        }
    }
}

private fun saveProject(appState: AppState): Boolean {
    return appState.persistentStates.openedFile?.let { path ->
        runCatching {
            appState.saveNoise(path)
        }.onFailure {
            it.printStackTrace()
            appState.errorPrompts += "Failed to export image: ${it.message}"
        }.isSuccess
    } ?: saveProjectAs(appState)
}

private fun openProject(appState: AppState) {
    val filters = listOf(
        DialogFilter("Project File", listOf("json"))
    )
    when (val result = showOpenDialog(filters)) {
        is DialogResult.Success -> {
            runCatching {
                appState.loadNoise(result.filePath)
            }.onSuccess {
                appState.persistentStates = appState.persistentStates.copy(openedFile = result.filePath)
            }.onFailure {
                it.printStackTrace()
                appState.errorPrompts += "Failed to export image: ${it.message}"
            }
        }

        is DialogResult.Canceled -> {
            // User cancelled
        }

        is DialogResult.Error -> {
            appState.errorPrompts += result.message
        }
    }
}

@Composable
fun AppMenuBar(renderer: NoiseGeneratorRenderer, appState: AppState) {
    var mainParameters by appState::mainParameters
    var outputParameters by appState::outputParameters

    var exportingFormat by remember { mutableStateOf<OutputFileFormat?>(null) }

    exportingFormat?.let { outputFormat ->
        exportingFormat = null
        val filters = listOf(
            DialogFilter("${outputFormat.fullName} File", outputFormat.extensions)
        )
        when (val result = showSaveDialog(filters)) {
            is DialogResult.Success -> {
                appState.scope.launch {
                    runCatching {
                        renderer.saveImage(result.filePath, outputFormat)
                    }.onFailure { ex ->
                        appState.errorPrompts += "Failed to export image: ${ex.message}"
                    }
                }
            }

            is DialogResult.Canceled -> {
                // User cancelled
            }

            is DialogResult.Error -> {
                appState.errorPrompts += result.message
            }
        }
    }

    var discardConfirmState by remember { mutableStateOf<DiscardConfirmState?>(null) }

    discardConfirmState?.let { state ->
        ContentDialog(
            title = "Confirm Discard Changes",
            visible = true,
            primaryButtonText = "Save",
            secondaryButtonText = "Discard",
            closeButtonText = "Cancel",
            onButtonClick = {
                when (it) {
                    ContentDialogButton.Primary -> {
                        if (saveProject(appState)) {
                            state.onSave()
                        } else {
                            state.onCancel()
                        }
                    }

                    ContentDialogButton.Secondary -> state.onDiscard()
                    ContentDialogButton.Close -> state.onCancel()
                }
                discardConfirmState = null
            },
            content = {
                Text("You have unsaved changes. Do you want to save them before proceeding?")
            }
        )
    }

    fun checkUnsavedChanges(proceed: () -> Unit) {
        if (appState.hasUnsavedChanges) {
            discardConfirmState = DiscardConfirmState(
                onSave = proceed,
                onDiscard = proceed,
                onCancel = {}
            )
        } else {
            proceed()
        }
    }

    if (appState.requestedClose) {
        appState.requestedClose = false
        checkUnsavedChanges(appState::exitApp)
    }

    MenuBar(
        modifier = Modifier
            .background(color = FluentTheme.colors.background.mica.base)
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 0.dp)
    ) {
        MenuBarItem(
            items = {
                MenuFlyoutButton(
                    onClick = {
                        checkUnsavedChanges(appState::resetNoise)
                        isFlyoutVisible = false
                    },
                    icon = Icons.Default.Document,
                    text = "New Project",
                    trailingText = "Ctrl+N"
                )
                MenuFlyoutButton(
                    onClick = {
                        checkUnsavedChanges {
                            openProject(appState)
                        }
                        isFlyoutVisible = false
                    },
                    icon = Icons.Default.FolderOpen,
                    text = "Open Project",
                    trailingText = "Ctrl+O"
                )
                MenuFlyoutButton(
                    onClick = {
                        saveProject(appState)
                        isFlyoutVisible = false
                    },
                    icon = Icons.Default.Save,
                    text = "Save Project",
                    trailingText = "Ctrl+S"
                )
                MenuFlyoutButton(
                    onClick = {
                        saveProjectAs(appState)
                        isFlyoutVisible = false
                    },
                    icon = Icons.Default.SaveEdit,
                    text = "Save Project As",
                    trailingText = "Ctrl+Shift+S"
                )
                MenuFlyoutSeparator()
                MenuFlyoutItem(
                    text = { Text("Export Texture") },
                    icon = { Icon(imageVector = Icons.Default.SaveArrowRight, contentDescription = null) },
                    items = {
                        fun isFormatEnabled(format: OutputFileFormat): Boolean {
                            if ((mainParameters.slices > 1) && format.only2D) {
                                return false
                            }
                            val outputSpec = outputParameters.format.outputSpec
                            if (!format.supportedChannelCount.contains(outputSpec.channels)) {
                                return false
                            }
                            if (!format.supportedPixelType.contains(outputSpec.pixelType)) {
                                return false
                            }
                            return true
                        }

                        OutputFileFormat.entries.forEach { format ->
                            MenuFlyoutButton(
                                onClick = {
                                    isFlyoutVisible = false
                                    exportingFormat = format
                                },
                                text = format.name,
                                enabled = isFormatEnabled(format)
                            )
                        }
                    }
                )
                MenuFlyoutSeparator()
                MenuFlyoutButton(
                    onClick = {
                        appState.requestedClose
                        isFlyoutVisible = false
                    },
                    icon = Icons.Default.Dismiss,
                    text = "Exit"
                )
            }
        ) {
            Text("File")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalFluentApi::class)
@Composable
fun SideEditor(renderer: NoiseGeneratorRenderer, appState: AppState) {
    var mainParameters by appState::mainParameters
    var outputParameters by appState::outputParameters
    var viewerParameters by appState::viewerParameters
    var systemParameters by appState::systemParameters
    val noiseLayers by appState::noiseLayers

    Row {
        var sideNavItem by remember { mutableStateOf(SideNavItem.Main) }
        var sideNavExpanded by remember { mutableStateOf(false) }

        @Composable
        fun AppSideNavItem(item: SideNavItem) {
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

        SideNav(
            expanded = sideNavExpanded,
            onExpandStateChange = { sideNavExpanded = it },
            footer = {
                val item = SideNavItem.Setting
                AppSideNavItem(item)
            }
        ) {
            SideNavItem.entries.dropLast(1).forEach { item ->
                AppSideNavItem(item)
            }
        }

        val scrollState = rememberScrollState()
        ScrollbarContainer(
            modifier = Modifier
                .background(color = FluentTheme.colors.background.layer.default),
            adapter = rememberScrollbarAdapter(scrollState)
        ) {
            Column(
                modifier = Modifier
                    .width(480.dp)
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp)
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

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoiseViewer(renderer: NoiseGeneratorRenderer, appState: AppState) {
    var viewerParameters by appState::viewerParameters
    Row {
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

enum class SideNavItem(val icon: ImageVector) {
    Main(Icons.Default.Image),
    Noise(Icons.Default.GridDots),
    Output(Icons.Default.Filter),
    Viewer(Icons.Default.Eye),
    Setting(Icons.Default.Settings),
}