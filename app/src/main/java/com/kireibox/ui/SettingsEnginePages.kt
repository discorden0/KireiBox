package com.kireibox.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.components.KireiListGroup
import com.kireibox.designsystem.components.KireiListRow
import com.kireibox.designsystem.components.KireiListRowSpec
import com.kireibox.designsystem.components.KireiRowTrailing
import com.kireibox.designsystem.components.KireiSegment
import com.kireibox.designsystem.components.KireiSliderSpec
import com.kireibox.ons.OnsGlobalSettings
import com.yuki.yukihub.launcherbridge.LauncherEngineOptions
import com.yuki.yukihub.model.EngineType
import kotlin.math.roundToInt

// 全局引擎设置: the list of the engines, and the global defaults of ONS, Artemis and Tyrano
// (KRKR's page is SettingsKrkrPages.kt). A single game's own options stay in its sheet.

/** The engine pages in the order the list shows them. */
private val EnginePages =
    listOf(
        KireiSettingsPage.KRKR to EngineType.KIRIKIRI,
        KireiSettingsPage.ONS to EngineType.ONS,
        KireiSettingsPage.ARTEMIS to EngineType.ARTEMIS,
        KireiSettingsPage.TYRANO to EngineType.TYRANO,
        KireiSettingsPage.WINLATOR to EngineType.WINDOWS,
    )

private const val ENGINE_PAGE_SUFFIX = "Engine settings"

@Composable
internal fun EngineHubSettings(
    colors: KireiColors,
    viewModel: KireiViewModel,
) {
    KireiListGroup(colors, SettingsGroupSpacing) {
        EnginePages.forEach { (page, engine) ->
            item {
                KireiListRow(
                    KireiListRowSpec(
                        // The page is already 全局引擎设置: each row is just the engine (KRKR, ONS, …).
                        title = settingsPageTitle(page).removeSuffix(ENGINE_PAGE_SUFFIX),
                        icon = libraryEngineIcon(engine),
                        tile = addGameEngineTile(engine),
                        trailing = KireiRowTrailing.Chevron,
                    ),
                    colors,
                    onClick = { viewModel.openSettingsPage(page) },
                )
            }
        }
    }
}

/** 游戏界面屏幕方向 (Tyranor's three options), read by every built-in engine at launch. */
@Composable
internal fun EngineOrientationRow(colors: KireiColors) {
    val context = LocalContext.current
    var orientation by remember { mutableStateOf(LauncherEngineOptions.orientation(context)) }
    SettingsSegmentedRow(
        SettingsSegmentedRowSpec(
            "Game screen orientation",
            LauncherEngineOptions.ORIENTATIONS.map { KireiSegment(it.toString(), engineOrientationLabel(it)) },
            orientation.toString(),
        ),
        colors,
    ) {
        orientation = it.toInt()
        LauncherEngineOptions.setOrientation(context, orientation)
    }
}

private fun engineOrientationLabel(value: Int): String =
    when (value) {
        LauncherEngineOptions.ORIENTATION_LEFT_LANDSCAPE -> "Left horizontal screen"
        LauncherEngineOptions.ORIENTATION_RIGHT_LANDSCAPE -> "Right horizontal screen"
        else -> "Sensor"
    }

private const val SAVE_DIR_FOOTER = "After closing, the archive is written to the game directory."

@Composable
internal fun OnsSettingsPage(colors: KireiColors) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(OnsGlobalSettings.load(context)) }
    val update: (OnsGlobalSettings) -> Unit = {
        settings = it
        OnsGlobalSettings.save(context, it)
    }
    KireiListGroup(colors, SettingsGroupSpacing, title = "Show") {
        item { EngineOrientationRow(colors) }
        item { SwitchRow("Keep the screen on", settings.keepScreenOn, colors) { update(settings.copy(keepScreenOn = it)) } }
        item { SwitchRow("Stretch full screen", settings.stretchFull, colors) { update(settings.copy(stretchFull = it)) } }
        item { SwitchRow("Ignore bangs", settings.ignoreCutout, colors) { update(settings.copy(ignoreCutout = it)) } }
        item { SwitchRow("Picture sharpening", settings.sharpness, colors) { update(settings.copy(sharpness = it)) } }
        item {
            SettingsSliderRow(
                settings.sharpnessValue,
                KireiSliderSpec(
                    label = "Sharpening strength",
                    valueText = OnsGlobalSettings.formatSharpness(settings.sharpnessValue),
                    valueRange = OnsGlobalSettings.MIN_SHARPNESS..OnsGlobalSettings.MAX_SHARPNESS,
                    // 0.5 stops: TalkBack and arrow keys land on round values.
                    steps = ((OnsGlobalSettings.MAX_SHARPNESS - OnsGlobalSettings.MIN_SHARPNESS) / .5f).roundToInt() - 1,
                    enabled = settings.sharpness,
                ),
                colors,
            ) { update(settings.copy(sharpnessValue = (it * 2).roundToInt() / 2f)) }
        }
    }
    KireiListGroup(colors, SettingsGroupSpacing, title = "compatible") {
        item {
            SettingsSegmentedRow(
                SettingsSegmentedRowSpec("Text encoding", OnsGlobalSettings.ENCODINGS.map { KireiSegment(it, onsEncodingLabel(it)) }, settings.encoding),
                colors,
            ) { update(settings.copy(encoding = it)) }
        }
        item { SwitchRow("Disable video", settings.disableVideo, colors) { update(settings.copy(disableVideo = it)) } }
    }
    KireiListGroup(colors, SettingsGroupSpacing, title = "Save data", footer = SAVE_DIR_FOOTER) {
        item { SwitchRow("Independent archive directory", settings.scopedSaveDir, colors) { update(settings.copy(scopedSaveDir = it)) } }
    }
}

private fun onsEncodingLabel(value: String): String =
    when (value) {
        "utf8" -> "UTF-8"
        "sjis" -> "Shift-JIS"
        else -> "GBK"
    }

@Composable
internal fun ArtemisSettingsPage(
    state: KireiUiState,
    colors: KireiColors,
    viewModel: KireiViewModel,
) {
    KireiListGroup(colors, SettingsGroupSpacing, title = "Show") {
        item { EngineOrientationRow(colors) }
    }
    KireiListGroup(colors, SettingsGroupSpacing, title = "Save data", footer = SAVE_DIR_FOOTER) {
        item { SwitchRow("Independent archive directory", state.settings?.artemisScopedSaveDir != false, colors, viewModel::setArtemisScopedSaveDir) }
    }
}

@Composable
internal fun TyranoSettingsPage(
    state: KireiUiState,
    colors: KireiColors,
    viewModel: KireiViewModel,
) {
    val context = LocalContext.current
    var keepScreenOn by remember { mutableStateOf(LauncherEngineOptions.tyranoKeepScreenOn(context)) }
    var ignoreCutout by remember { mutableStateOf(LauncherEngineOptions.tyranoIgnoreCutout(context)) }
    KireiListGroup(colors, SettingsGroupSpacing, title = "Show") {
        item { EngineOrientationRow(colors) }
        item {
            SwitchRow("Keep the screen on", keepScreenOn, colors) {
                keepScreenOn = it
                LauncherEngineOptions.setTyranoKeepScreenOn(context, it)
            }
        }
        item {
            SwitchRow("Ignore bangs", ignoreCutout, colors) {
                ignoreCutout = it
                LauncherEngineOptions.setTyranoIgnoreCutout(context, it)
            }
        }
    }
    KireiListGroup(colors, SettingsGroupSpacing, title = "Save data", footer = SAVE_DIR_FOOTER) {
        item { SwitchRow("Independent archive directory", state.settings?.tyranoScopedSaveDir != false, colors, viewModel::setTyranoScopedSaveDir) }
    }
}
