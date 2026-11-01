package com.kireibox.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.KireiTileColor
import com.kireibox.designsystem.LocalResolvedMotionMode
import com.kireibox.designsystem.components.KireiButtonStyle
import com.kireibox.designsystem.components.KireiDialog
import com.kireibox.designsystem.components.KireiDialogAction
import com.kireibox.designsystem.components.KireiDialogSpec
import com.kireibox.designsystem.components.KireiListGroup
import com.kireibox.designsystem.components.KireiListRow
import com.kireibox.designsystem.components.KireiListRowSpec
import com.kireibox.designsystem.components.KireiRowTone
import com.kireibox.designsystem.components.KireiRowTrailing
import com.kireibox.designsystem.components.KireiSegment
import com.yuki.yukihub.settings.FxLevel
import com.yuki.yukihub.settings.ImmersiveAmbient
import com.yuki.yukihub.settings.ImmersiveBackdropStyle
import com.yuki.yukihub.settings.ImmersiveCardSwitch
import com.yuki.yukihub.settings.ImmersiveFxPreset
import com.yuki.yukihub.settings.ImmersiveVisualSettings
import com.yuki.yukihub.settings.MotionMode

/** Named presets offered by the 快速设置 guide; CUSTOM is only a card on the 首页 page. */
internal val ImmersivePresetSegments = ImmersiveFxPreset.entries.filter { it != ImmersiveFxPreset.CUSTOM }

/** 3D depth labels follow the plan wording: 平 / 标准 / 强. */
private fun cardDepthLabel(level: FxLevel): String =
    when (level) {
        FxLevel.LOW -> "Flat"
        FxLevel.MEDIUM -> "standard"
        FxLevel.HIGH -> "Powerful"
    }

/** Footer of the 视觉效果 group: only a low-RAM device has one, as effects are capped at 标准 there (plan §6.4). */
internal fun immersivePresetFooter(lowRamDevice: Boolean): String? = if (lowRamDevice) "The device has small memory and the upper limit of the effect is \"Standard\"." else null

/** Backdrops that 减少透明度 swaps for the 氛围色 at runtime (plan §6.4). */
private val TranslucentBackdrops = setOf(ImmersiveBackdropStyle.COVER_ZOOM, ImmersiveBackdropStyle.BLUR_COVER, ImmersiveBackdropStyle.AURORA)

/** What a preset card tap writes: a named preset's canonical effects, or 自定义 keeping today's details; 切换方式 is kept either way. */
internal fun immersivePresetSelection(
    preset: ImmersiveFxPreset,
    current: ImmersiveVisualSettings,
): ImmersiveVisualSettings = if (preset == ImmersiveFxPreset.CUSTOM) current.copy(preset = ImmersiveFxPreset.CUSTOM) else current.withPreset(preset)

/**
 * 首页 → 视觉效果 (plan §6.2/§5e, r4 S1): global-setting hints, the preset cards with 推荐 on
 * 标准, then 「高级选项」 (collapsed by default; tapping 自定义 opens it) holding every detail and
 * 恢复默认效果. Every control writes one complete snapshot through
 * KireiViewModel.setImmersiveVisual. 卡片倒影 and 启动过场 are persisted by presets but not shown
 * until their renderers exist, so no switch here is inert.
 */
@Composable
internal fun ImmersiveVisualSettingsPanel(
    state: KireiUiState,
    colors: KireiColors,
    viewModel: KireiViewModel,
) {
    val visual = state.settings?.immersiveVisual ?: ImmersiveVisualSettings()
    val write: (ImmersiveVisualSettings) -> Unit = { viewModel.setImmersiveVisual(it.normalized()) }
    val groupSpacing = SettingsGroupSpacing
    val lowRam = LocalImmersiveFx.current.environment.lowRamDevice
    val reducedMotion = LocalResolvedMotionMode.current == MotionMode.REDUCED
    var advancedOpen by rememberSaveable { mutableStateOf(false) }
    // r4 S2: a search result for a detail opens 「高级选项」 so its row exists to scroll to.
    val searchTarget = LocalSettingsAnchorHost.current?.reveal?.searchTarget
    LaunchedEffect(searchTarget) {
        if (searchTarget?.page == KireiSettingsPage.INTERFACE && searchTarget.expandAdvanced) advancedOpen = true
    }
    ImmersiveGlobalHints(state, visual, colors, viewModel)
    KireiListGroup(colors, groupSpacing, title = "Visual effects", footer = immersivePresetFooter(lowRam)) {
        item {
            SettingsAnchor("Visual effects") {
                ImmersivePresetCards(
                    ImmersivePresetCardsSpec(visual),
                    colors,
                    Modifier.padding(start = KireiSpacing.sm, end = KireiSpacing.sm, top = KireiSpacing.sm, bottom = KireiSpacing.md),
                ) { preset ->
                    write(immersivePresetSelection(preset, visual))
                    if (preset == ImmersiveFxPreset.CUSTOM) advancedOpen = true
                }
            }
        }
        item {
            SettingsSegmentedRow(
                SettingsSegmentedRowSpec("Switch mode", ImmersiveCardSwitch.entries.map { KireiSegment(it, it.displayName) }, visual.cardSwitch, subtitle = "Portrait screen only"),
                colors,
            ) { write(visual.copy(cardSwitch = it)) }
        }
        item { ImmersiveAdvancedRow(advancedOpen, colors) { advancedOpen = !advancedOpen } }
    }
    // Reduced motion (spec §7) swaps the details in place instead of expanding them.
    AnimatedVisibility(
        visible = advancedOpen,
        enter = if (reducedMotion) EnterTransition.None else fadeIn() + expandVertically(),
        exit = if (reducedMotion) ExitTransition.None else fadeOut() + shrinkVertically(),
    ) {
        Column {
            ImmersiveBackdropSettingsGroup(visual, colors, groupSpacing, write)
            ImmersiveCardSettingsGroup(visual, colors, groupSpacing, write)
            ImmersiveAmbientSettingsGroup(visual, colors, groupSpacing, write)
            ImmersiveRestoreDefaultsGroup(visual, colors, write)
        }
    }
}

/**
 * Effects a global 外观 setting overrides, said up front with a link-style row to 外观 instead of a
 * second copy of that switch: 减少透明度 swaps 封面氛围 / 封面虚化 / 流光极光 for the 氛围色. The
 * stored effects stay unchanged. 精简 动画效果 no longer shows a "paused" hint here: an explicit
 * choice (including 完整) is always honoured, and 自动 alone may resolve to 精简 on a constrained
 * device.
 */
@Composable
private fun ImmersiveGlobalHints(
    state: KireiUiState,
    visual: ImmersiveVisualSettings,
    colors: KireiColors,
    viewModel: KireiViewModel,
) {
    val opaque = state.settings?.reducedTransparency == true && visual.backdrop in TranslucentBackdrops
    if (!opaque) return
    val openAppearance = { viewModel.openSettingsPage(KireiSettingsPage.APPEARANCE) }
    KireiListGroup(colors, SettingsGroupSpacing) {
        item {
            KireiListRow(
                KireiListRowSpec(
                    title = "The background has been changed to an ambient color",
                    subtitle = "\"Reduce Transparency\" is turned on",
                    icon = Icons.Rounded.Opacity,
                    tile = KireiTileColor.SLATE,
                    trailing = KireiRowTrailing.Value("Appearance"),
                ),
                colors,
                onClick = openAppearance,
            )
        }
    }
}

/** 「高级选项」: a disclosure row whose chevron turns when the details are open. */
@Composable
private fun ImmersiveAdvancedRow(
    open: Boolean,
    colors: KireiColors,
    onToggle: () -> Unit,
) {
    val reducedMotion = LocalResolvedMotionMode.current == MotionMode.REDUCED
    val turn by animateFloatAsState(if (open) 180f else 0f, selectionSpec(reducedMotion), label = "immersiveAdvancedChevron")
    SettingsAnchor("Advanced options") {
        KireiListRow(
            KireiListRowSpec(
                title = "Advanced options",
                trailing = KireiRowTrailing.Custom { Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = colors.secondary, modifier = Modifier.rotate(turn)) },
            ),
            colors,
            Modifier.semantics { stateDescription = if (open) "Expanded" else "Collected" },
            onClick = onToggle,
        )
    }
}

@Composable
private fun ImmersiveBackdropSettingsGroup(
    visual: ImmersiveVisualSettings,
    colors: KireiColors,
    modifier: Modifier,
    write: (ImmersiveVisualSettings) -> Unit,
) {
    KireiListGroup(colors, modifier, title = "Background") {
        item {
            SettingsSegmentedRow(
                SettingsSegmentedRowSpec(
                    title = "Background style",
                    segments = ImmersiveBackdropStyle.entries.map { KireiSegment(it, it.displayName) },
                    selected = visual.backdrop,
                ),
                colors,
            ) { write(visual.copy(backdrop = it)) }
        }
        item {
            SettingsSegmentedRow(
                SettingsSegmentedRowSpec("Background intensity", FxLevel.entries.map { KireiSegment(it, it.displayName) }, visual.backdropIntensity),
                colors,
            ) { write(visual.copy(backdropIntensity = it)) }
        }
        item {
            SettingsToggleRow(
                "Cover slowly pushed and pulled",
                visual.kenBurns,
                colors,
                subtitle = if (visual.backdrop == ImmersiveBackdropStyle.SOLID) "Invalid on solid color background" else null,
            ) { write(visual.copy(kenBurns = it)) }
        }
        item { SettingsToggleRow("Film grain", visual.grain, colors) { write(visual.copy(grain = it)) } }
        item { SettingsToggleRow("Vignetting", visual.vignette, colors) { write(visual.copy(vignette = it)) } }
    }
}

@Composable
private fun ImmersiveCardSettingsGroup(
    visual: ImmersiveVisualSettings,
    colors: KireiColors,
    modifier: Modifier,
    write: (ImmersiveVisualSettings) -> Unit,
) {
    KireiListGroup(colors, modifier, title = "Card") {
        item {
            SettingsSegmentedRow(
                SettingsSegmentedRowSpec("3D depth", FxLevel.entries.map { KireiSegment(it, cardDepthLabel(it)) }, visual.cardDepth),
                colors,
            ) { write(visual.copy(cardDepth = it)) }
        }
        item { SettingsToggleRow("Center card halo", visual.glow, colors) { write(visual.copy(glow = it)) } }
        item { SettingsToggleRow("Rim light", visual.rimLight, colors) { write(visual.copy(rimLight = it)) } }
        item { SettingsToggleRow("Highlight sweep", visual.sheen, colors) { write(visual.copy(sheen = it)) } }
        item { SettingsToggleRow("Dynamic color selection", visual.dynamicTint, colors) { write(visual.copy(dynamicTint = it)) } }
    }
}

@Composable
private fun ImmersiveAmbientSettingsGroup(
    visual: ImmersiveVisualSettings,
    colors: KireiColors,
    modifier: Modifier,
    write: (ImmersiveVisualSettings) -> Unit,
) {
    KireiListGroup(colors, modifier, title = "Atmosphere") {
        item {
            SettingsSegmentedRow(
                SettingsSegmentedRowSpec("Atmospheric particles", ImmersiveAmbient.entries.map { KireiSegment(it, it.displayName) }, visual.ambient),
                colors,
            ) { write(visual.copy(ambient = it)) }
        }
        item {
            SettingsToggleRow("Gyroscope Parallax", visual.gyroParallax, colors) { write(visual.copy(gyroParallax = it)) }
        }
    }
}

/** 恢复默认效果: back to the recommended 标准 preset, always behind a confirmation. */
@Composable
private fun ImmersiveRestoreDefaultsGroup(
    visual: ImmersiveVisualSettings,
    colors: KireiColors,
    write: (ImmersiveVisualSettings) -> Unit,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    val atDefault = visual.preset == RecommendedImmersivePreset
    KireiListGroup(colors, SettingsGroupSpacing, footer = "It is currently the default effect.".takeIf { atDefault }) {
        item {
            SettingsAnchor("Restore default effect") {
                KireiListRow(
                    KireiListRowSpec(title = "Restore default effect", tone = KireiRowTone.ACTION, enabled = !atDefault),
                    colors,
                    onClick = { confirming = true },
                )
            }
        }
    }
    if (confirming) {
        val close = { confirming = false }
        KireiDialog(
            spec =
                KireiDialogSpec(
                    title = "Restore default effect?",
                    message = "Will revert to the \"standard\" effect, and the current custom combination will not be retained.",
                    confirm =
                        KireiDialogAction("Restore default", {
                            close()
                            write(visual.withPreset(RecommendedImmersivePreset))
                        }),
                    dismiss = KireiDialogAction("Cancel", close, style = KireiButtonStyle.PLAIN),
                ),
            colors = colors,
            onDismissRequest = close,
        )
    }
}
