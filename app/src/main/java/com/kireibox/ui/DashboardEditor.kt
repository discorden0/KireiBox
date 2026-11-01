package com.kireibox.ui
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiElevation
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.LocalResolvedMotionMode
import com.kireibox.designsystem.components.KireiButton
import com.kireibox.designsystem.components.KireiButtonOptions
import com.kireibox.designsystem.components.KireiButtonSize
import com.kireibox.designsystem.components.KireiButtonStyle
import com.kireibox.designsystem.components.KireiDialog
import com.kireibox.designsystem.components.KireiDialogAction
import com.kireibox.designsystem.components.KireiDialogSpec
import com.kireibox.designsystem.components.KireiListRow
import com.kireibox.designsystem.components.KireiListRowSpec
import com.kireibox.designsystem.components.KireiRowTrailing
import com.kireibox.designsystem.components.KireiSegment
import com.kireibox.designsystem.components.KireiSegmented
import com.kireibox.designsystem.components.KireiSegmentedSpec
import com.kireibox.designsystem.dangerSolid
import com.yuki.yukihub.data.PlayTrendPoint
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.settings.DashboardLayout
import com.yuki.yukihub.settings.DashboardLayouts
import com.yuki.yukihub.settings.DashboardProfile
import com.yuki.yukihub.settings.DashboardStyles
import com.yuki.yukihub.settings.DashboardWidgetSpec
import com.yuki.yukihub.settings.DashboardWidgetType
import com.yuki.yukihub.settings.MotionMode
import com.yuki.yukihub.settings.SpotlightSelectionMode
import com.yuki.yukihub.settings.SpotlightSource
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// Dashboard editor page, widget gestures and picker sheets.

@Composable
internal fun DashboardEditorPage(
    profile: DashboardProfile,
    state: KireiUiState,
    colors: KireiColors,
    haze: HazeState?,
    viewModel: KireiViewModel,
    onClose: () -> Unit
) {
    val recent = LibraryOverviewPolicy.sorted(state.games, LibraryOverviewSort.RECENT)
    val stored = if (profile == DashboardProfile.COMPACT) state.dashboardCompact else state.dashboardWide
    val initial = remember(profile, stored) { DashboardLayouts.normalize(stored, profile) }
    var draft by remember(profile, initial) { mutableStateOf(initial) }
    var showAdd by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var rebindInstanceId by remember { mutableStateOf<String?>(null) }
    var styleInstanceId by remember { mutableStateOf<String?>(null) }
    var trendConfigInstanceId by remember { mutableStateOf<String?>(null) }
    val dirty = draft != DashboardLayouts.normalize(initial, profile)
    val closeOrConfirm: () -> Unit = { if (dirty) showDiscard = true else onClose() }
    BackHandler(onBack = closeOrConfirm)

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxSize().widthIn(max = 1180.dp)
                .statusBarsPadding().navigationBarsPadding().padding(horizontal = KireiSpacing.lg, vertical = KireiSpacing.sm)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                KireiButton(
                    label = "Cancel",
                    colors = colors,
                    options = KireiButtonOptions(style = KireiButtonStyle.TEXT, size = KireiButtonSize.MEDIUM),
                    onClick = closeOrConfirm,
                )
                Column(Modifier.weight(1f).padding(horizontal = KireiSpacing.xs), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Edit home page", color = colors.text, style = AppleTypography.headlineMedium)
                    Text(
                        if (profile == DashboardProfile.COMPACT) "Mobile · 2 column layout" else "Widescreen · 4-column layout",
                        color = colors.secondary,
                        style = AppleTypography.labelLarge
                    )
                }
                KireiButton(
                    label = "Save",
                    colors = colors,
                    options = KireiButtonOptions(style = KireiButtonStyle.PRIMARY, size = KireiButtonSize.MEDIUM, enabled = dirty),
                    onClick = {
                        viewModel.saveDashboardLayout(profile, draft)
                        onClose()
                    },
                )
            }
            Text(
                "Press and hold the component to drag, drag the lower right corner to resize",
                color = colors.secondary,
                style = AppleTypography.bodySmall,
                modifier = Modifier.padding(top = KireiSpacing.sm, bottom = KireiSpacing.sm)
            )
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(top = EDITOR_GRID_TOP_ROOM)) {
                if (draft.widgets.isEmpty()) {
                    GlassSurface(colors, haze, Modifier.fillMaxWidth().height(192.dp), KireiShapes.lg) {
                        Column(Modifier.fillMaxSize().padding(KireiSpacing.xl), verticalArrangement = Arrangement.Center) {
                            Text("Home page is empty", color = colors.text, style = AppleTypography.headlineMedium)
                            Text("Click the plus sign in the lower right corner to add the first component.", color = colors.secondary, style = AppleTypography.bodyMedium)
                        }
                    }
                } else {
                    DashboardGrid(
                        draft,
                        profile,
                        DashboardDensity.STANDARD,
                        Modifier.fillMaxWidth(),
                    ) { spec ->
                        DashboardEditorWidget(
                            spec = spec,
                            profile = profile,
                            games = state.games,
                            recent = recent,
                            playTrend = state.playTrend,
                            colors = colors,
                            haze = haze,
                            autoFill = draft.autoFill,
                            onRemove = { draft = DashboardLayouts.remove(draft, profile, spec.instanceId) },
                            onMove = { direction ->
                                val current = draft.widgets.indexOfFirst { it.instanceId == spec.instanceId }
                                draft = DashboardLayouts.move(draft, profile, spec.instanceId, current + direction)
                            },
                            onPlaceDelta = { columnDelta, rowDelta ->
                                draft.widgets.firstOrNull { it.instanceId == spec.instanceId }?.let { current ->
                                    draft =
                                        DashboardLayouts.placeAt(
                                            draft, profile, spec.instanceId,
                                            current.column.coerceAtLeast(0) + columnDelta,
                                            current.row.coerceAtLeast(0) + rowDelta
                                        )
                                }
                            },
                            onResizeWidth = { columns ->
                                draft = DashboardLayouts.resizeWidth(draft, profile, spec.instanceId, columns)
                            },
                            onResizeHeight = { heightSteps ->
                                draft = DashboardLayouts.resizeHeight(draft, profile, spec.instanceId, heightSteps)
                            },
                            onStyle = { styleInstanceId = spec.instanceId },
                            onTrendConfig = { trendConfigInstanceId = spec.instanceId },
                            onRebind = { rebindInstanceId = spec.instanceId }
                        )
                    }
                }
                Spacer(Modifier.height(96.dp))
            }
        }
        GlassSurface(
            colors,
            haze,
            Modifier.align(Alignment.BottomStart).navigationBarsPadding()
                .padding(start = KireiSpacing.lg, bottom = KireiSpacing.md).width(ARRANGE_BAR_WIDTH),
            KireiShapes.lg,
            KireiGlassRole.ICON,
        ) {
            KireiSegmented(
                spec =
                    KireiSegmentedSpec(
                        label = "Arrangement",
                        segments = listOf(KireiSegment(true, "Automatic arrangement"), KireiSegment(false, "Free placement")),
                        selected = draft.autoFill,
                    ),
                onSelect = { draft = DashboardLayouts.setAutoFill(draft, profile, it) },
                colors = colors,
                modifier = Modifier.padding(KireiSpacing.xs),
            )
        }
        GlassSurface(
            colors,
            haze,
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = KireiSpacing.lg, bottom = KireiSpacing.md).size(56.dp),
            KireiShapes.full,
            KireiGlassRole.ICON,
            onClick = { showAdd = true }
        ) {
            Box(Modifier.fillMaxSize().background(colors.accent, KireiShapes.full), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, "Add component", tint = colors.onAccent, modifier = Modifier.size(28.dp))
            }
        }
        if (showAdd) {
            DashboardAddSheet(
                profile = profile,
                layout = draft,
                games = state.games,
                colors = colors,
                onDismiss = { showAdd = false },
                onAdd = { type, gameId ->
                    draft = DashboardLayouts.add(draft, profile, type, gameId)
                    showAdd = false
                }
            )
        }
        rebindInstanceId?.let { instanceId ->
            val spec = draft.widgets.firstOrNull { it.instanceId == instanceId }
            if (spec != null) {
                SpotlightConfigSheet(
                    spec = spec,
                    games = state.games,
                    colors = colors,
                    onDismiss = { rebindInstanceId = null },
                    onSettings = { next ->
                        val settings = next.normalized()
                        if (settings.mode == SpotlightSelectionMode.MANUAL) {
                            val manualIds =
                                settings.manualGameIds.ifEmpty {
                                    if (spec.spotlightSelectionMode == SpotlightSelectionMode.AUTOMATIC) {
                                        listOfNotNull(spec.gameId)
                                    } else {
                                        emptyList()
                                    }
                                }
                            draft =
                                DashboardLayouts.setSpotlightManual(
                                    draft,
                                    profile,
                                    instanceId,
                                    manualIds,
                                    settings.count,
                                )
                        } else {
                            draft =
                                DashboardLayouts.setSpotlightAutomatic(
                                    draft,
                                    profile,
                                    instanceId,
                                    settings.source,
                                    settings.strategy,
                                    settings.count,
                                )
                        }
                    },
                )
            }
        }
        styleInstanceId?.let { instanceId ->
            val spec = draft.widgets.firstOrNull { it.instanceId == instanceId }
            if (spec != null) {
                KireiActionSheet("Select component style", colors, { styleInstanceId = null }) {
                    DashboardStyles.allowed(spec.type).forEach { style ->
                        SheetChoiceRow(dashboardStyleLabel(style), null, spec.styleKey == style, colors) {
                            draft = DashboardLayouts.setStyle(draft, profile, instanceId, style)
                            styleInstanceId = null
                        }
                    }
                }
            }
        }
        trendConfigInstanceId?.let { instanceId ->
            draft.widgets.firstOrNull { it.instanceId == instanceId && it.type == DashboardWidgetType.PLAY_TREND }?.let { spec ->
                TrendConfigSheet(
                    spec = spec,
                    points = state.playTrend,
                    colors = colors,
                    haze = haze,
                    onDismiss = { trendConfigInstanceId = null },
                    onComplete = { config ->
                        draft = DashboardTrendEditorPolicy.complete(draft, profile, instanceId, config)
                        trendConfigInstanceId = null
                    },
                )
            }
        }
        if (showDiscard) {
            KireiDialog(
                spec =
                    KireiDialogSpec(
                        title = "Give up on homepage changes?",
                        message = "Unsaved component order, size, additions and deletions will be lost.",
                        confirm =
                            KireiDialogAction("Abandon changes", {
                                showDiscard = false
                                onClose()
                            }, style = KireiButtonStyle.DESTRUCTIVE),
                        dismiss = KireiDialogAction("Continue editing", { showDiscard = false }, style = KireiButtonStyle.PLAIN),
                    ),
                colors = colors,
                onDismissRequest = { showDiscard = false },
            )
        }
    }
}

/** Width of the 自动排列 / 自由摆放 control: two 4-character segments plus the container padding. */
private val ARRANGE_BAR_WIDTH = 232.dp

@Composable
internal fun DashboardEditorWidget(
    spec: DashboardWidgetSpec,
    profile: DashboardProfile,
    games: List<Game>,
    recent: List<Game>,
    playTrend: List<PlayTrendPoint>,
    colors: KireiColors,
    haze: HazeState?,
    autoFill: Boolean,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
    onPlaceDelta: (Int, Int) -> Unit,
    onResizeWidth: (Int) -> Unit,
    onResizeHeight: (Int) -> Unit,
    onStyle: () -> Unit,
    onTrendConfig: () -> Unit,
    onRebind: () -> Unit
) {
    val polished = LocalResolvedMotionMode.current == MotionMode.POLISHED
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val threshold = with(density) { 56.dp.toPx() }
    var dragging by remember { mutableStateOf(false) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var resizing by remember { mutableStateOf(false) }
    var resizeDistance by remember { mutableFloatStateOf(0f) }
    var resizeOvershoot by remember { mutableFloatStateOf(0f) }
    var resizeStartSteps by remember { mutableIntStateOf(spec.heightSteps) }
    val latestHeightSteps by rememberUpdatedState(spec.heightSteps)
    val latestResizeHeight by rememberUpdatedState(onResizeHeight)
    var removing by remember { mutableStateOf(false) }
    val removeProgress by animateFloatAsState(if (removing) 0f else 1f, tween(160), label = "dashboardRemove")
    LaunchedEffect(removing) {
        if (removing) {
            delay(160)
            onRemove()
        }
    }
    val wobbleTransition = rememberInfiniteTransition(label = "dashboardWobble")
    val wobbleDirection = if (spec.instanceId.hashCode() and 1 == 0) 1f else -1f
    val wobble by wobbleTransition.animateFloat(
        initialValue = -.55f * wobbleDirection,
        targetValue = .55f * wobbleDirection,
        animationSpec = infiniteRepeatable(tween(140), RepeatMode.Reverse),
        label = "dashboardWobbleAngle"
    )
    val missingGame =
        spec.type == DashboardWidgetType.GAME_SPOTLIGHT &&
            if (spec.spotlightSelectionMode == SpotlightSelectionMode.MANUAL) {
                spec.spotlightGameIds.isNotEmpty() && spec.spotlightGameIds.none { id -> games.any { it.id == id } }
            } else {
                spec.spotlightSource == SpotlightSource.FIXED &&
                    spec.gameId != null &&
                    games.none { it.id == spec.gameId }
            }
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            rotationZ = if (polished && !dragging && !removing) wobble else 0f
            alpha = removeProgress
            scaleX = removeProgress
            scaleY = removeProgress
            if (dragging && polished) {
                scaleX = 1.025f
                scaleY = 1.025f
                shadowElevation = 12.dp.toPx()
            }
        }
            // No animateContentSize (the grid animates the cell): its clip would cut the edge-riding controls.
            .pointerInput(spec.instanceId, autoFill) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragging = true
                        dragX = 0f
                        dragY = 0f
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragCancel = {
                        dragging = false
                        dragX = 0f
                        dragY = 0f
                    },
                    onDragEnd = {
                        dragging = false
                        dragX = 0f
                        dragY = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragX += amount.x
                        dragY += amount.y
                        if (autoFill) {
                            val primary = if (kotlin.math.abs(dragY) >= kotlin.math.abs(dragX)) dragY else dragX
                            if (kotlin.math.abs(primary) >= threshold) {
                                onMove(if (primary > 0f) 1 else -1)
                                dragX = 0f
                                dragY = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        } else {
                            if (kotlin.math.abs(dragX) >= threshold) {
                                onPlaceDelta(if (dragX > 0f) 1 else -1, 0)
                                dragX = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            if (kotlin.math.abs(dragY) >= threshold) {
                                onPlaceDelta(0, if (dragY > 0f) 1 else -1)
                                dragY = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
                )
            }
    ) {
        if (missingGame) {
            GlassSurface(colors, haze, Modifier.fillMaxSize(), KireiShapes.lg, KireiGlassRole.CONTENT, onRebind) {
                Column(Modifier.fillMaxSize().padding(KireiSpacing.md), verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Rounded.Gamepad, null, tint = colors.accent)
                    Text("Game has been moved", color = colors.text, style = AppleTypography.headlineSmall, modifier = Modifier.padding(top = KireiSpacing.xs))
                    Text("Tap to reselect game", color = colors.secondary, style = AppleTypography.bodySmall)
                }
            }
        } else {
            CompositionLocalProvider(LocalDashboardEditing provides true) {
                HomeDashboardWidget(
                    spec = spec,
                    profile = profile,
                    games = games,
                    recent = recent,
                    playTrend = playTrend,
                    colors = colors,
                    haze = haze,
                    interactiveTrend = false,
                    onOpenOverview = {},
                    onOpenLibrary = {},
                    onOpenGame = {},
                    onLaunchGame = {},
                    onChooseDirectory = {},
                )
            }
            Box(Modifier.fillMaxSize().background(colors.background.copy(alpha = if (colors.isDark) .10f else .04f), KireiShapes.lg))
        }

        // Every control sits in the top-trailing corner (config, then remove at the very corner), riding the
        // top edge so the circles stay clear of the widget's first line (a value such as 「14 部」); the resize
        // grip straddles the trailing edge just above the bottom corner, clear of a bottom-trailing value.
        Row(
            Modifier.align(Alignment.TopEnd).offset(y = -EDITOR_CONTROL_RISE),
            horizontalArrangement = Arrangement.spacedBy(-EDITOR_CONTROL_OVERLAP),
        ) {
            if (spec.type != DashboardWidgetType.PLAY_TREND && DashboardStyles.allowed(spec.type).size > 1) {
                EditorControl(EditorControlSpec(Icons.Rounded.Palette, "Change component style"), colors, onStyle)
            }
            if (spec.type == DashboardWidgetType.PLAY_TREND) {
                EditorControl(EditorControlSpec(Icons.Rounded.Tune, "Trend chart settings"), colors, onTrendConfig)
            }
            if (spec.type == DashboardWidgetType.GAME_SPOTLIGHT) {
                EditorControl(EditorControlSpec(Icons.Rounded.Tune, "Set header image source and display method"), colors, onRebind)
            }
            EditorControl(EditorControlSpec(Icons.Rounded.Remove, "Remove component", danger = true), colors) {
                if (!removing) removing = true
            }
        }

        val allowedWidths = DashboardLayouts.allowedWidths(spec.type, profile)
        Box(
            Modifier.align(Alignment.BottomEnd).offset(x = EDITOR_GRIP_SHIFT).size(EDITOR_TOUCH)
                .graphicsLayer { translationY = resizeOvershoot * .12f }
                .pointerInput(spec.instanceId, spec.type) {
                    detectDragGestures(
                        onDragStart = {
                            resizing = true
                            resizeDistance = 0f
                            resizeOvershoot = 0f
                            resizeStartSteps = latestHeightSteps
                        },
                        onDragCancel = {
                            resizing = false
                            resizeDistance = 0f
                            resizeOvershoot = 0f
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            resizeDistance += amount.y
                            val stepPx = 8.dp.toPx()
                            val requested = resizeStartSteps + (resizeDistance / stepPx).roundToInt()
                            val range = DashboardLayouts.heightRange(spec.type)
                            val bounded = requested.coerceIn(range)
                            resizeOvershoot =
                                if (requested == bounded) {
                                    0f
                                } else {
                                    (resizeOvershoot + amount.y * .35f).coerceIn(-48.dp.toPx(), 48.dp.toPx())
                                }
                            if (bounded != latestHeightSteps) latestResizeHeight(bounded)
                        },
                        onDragEnd = {
                            resizing = false
                            resizeDistance = 0f
                            resizeOvershoot = 0f
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    )
                }
        ) {
            EditorControl(EditorControlSpec(Icons.Rounded.ZoomOutMap, "Drag to adjust height, click to switch width", atBottom = true), colors) {
                if (allowedWidths.size > 1) {
                    val index = allowedWidths.indexOf(spec.size.columns).coerceAtLeast(0)
                    onResizeWidth(allowedWidths[(index + 1) % allowedWidths.size])
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        }
        if (resizing) {
            Text(
                "${latestHeightSteps * DashboardLayouts.HEIGHT_STEP_DP} dp",
                color = colors.background,
                style = AppleTypography.numericSmall,
                modifier =
                    Modifier.align(Alignment.BottomCenter).padding(bottom = KireiSpacing.sm)
                        .background(colors.text, KireiShapes.full)
                        .padding(horizontal = KireiSpacing.sm, vertical = KireiSpacing.xxs),
            )
        }
    }
}

/** One editor control: [danger] paints the remove badge; [atBottom] hugs the bottom-trailing corner instead of the top. */
private data class EditorControlSpec(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val description: String,
    val danger: Boolean = false,
    val atBottom: Boolean = false,
)

/** 28dp control circle (surface3, or the AA danger pair) inside a 48dp touch target that hugs its corner. */
@Composable
private fun EditorControl(
    spec: EditorControlSpec,
    colors: KireiColors,
    onClick: () -> Unit,
) {
    val container = if (spec.danger) colors.dangerSolid.accent else colors.surface3
    val ink = if (spec.danger) colors.dangerSolid.onAccent else colors.accent
    PressScaleBox(Modifier.size(EDITOR_TOUCH).semantics { role = Role.Button }, KireiShapes.full, onClick, pressedScale = .9f) {
        Box(
            Modifier.fillMaxSize().padding(EDITOR_CONTROL_INSET),
            contentAlignment = if (spec.atBottom) Alignment.BottomEnd else Alignment.TopEnd,
        ) {
            Box(
                Modifier.size(EDITOR_CONTROL).background(container, KireiShapes.full)
                    .border(KireiElevation.strokeWidth, colors.strokeStrong, KireiShapes.full),
                contentAlignment = Alignment.Center,
            ) {
                Icon(spec.icon, spec.description, tint = ink, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private val EDITOR_TOUCH = 48.dp
private val EDITOR_CONTROL = 28.dp
private val EDITOR_CONTROL_INSET = 4.dp
private val EDITOR_CONTROL_OVERLAP = 8.dp

/** How far the top cluster's touch boxes rise above the widget: the circles straddle the edge (10dp above, 18dp below). */
private val EDITOR_CONTROL_RISE = 14.dp

/** Centres the grip's circle on the trailing edge; the grid gets room above for the circles over the top edge. */
private val EDITOR_GRIP_SHIFT = EDITOR_CONTROL / 2 + EDITOR_CONTROL_INSET
private val EDITOR_GRID_TOP_ROOM = EDITOR_CONTROL_RISE - EDITOR_CONTROL_INSET

@Composable
internal fun DashboardAddSheet(
    profile: DashboardProfile,
    layout: DashboardLayout,
    games: List<Game>,
    colors: KireiColors,
    onDismiss: () -> Unit,
    onAdd: (DashboardWidgetType, Long?) -> Unit
) {
    val usedGames =
        HashSet<Long>().apply {
            layout.widgets.forEach { spec ->
                if (spec.type == DashboardWidgetType.GAME_SPOTLIGHT) {
                    if (spec.spotlightSelectionMode == SpotlightSelectionMode.MANUAL) {
                        addAll(spec.spotlightGameIds)
                    }
                    spec.gameId?.let(::add)
                }
            }
        }
    KireiActionSheet("Add home page component", colors, onDismiss, expanded = true) {
        if (layout.widgets.size >= DashboardLayouts.MAX_WIDGETS) {
            Text(
                "You can add up to ${DashboardLayouts.MAX_WIDGETS} components.",
                color = colors.secondary,
                style = AppleTypography.bodyMedium,
                modifier = Modifier.padding(KireiSpacing.xl),
            )
            return@KireiActionSheet
        }
        DashboardTrendEditorPolicy.availableTypes(layout).filter { it != DashboardWidgetType.GAME_SPOTLIGHT }.forEach { type ->
            SheetActionRow(dashboardWidgetIcon(type), dashboardWidgetLabel(type), dashboardWidgetDescription(type), colors) {
                onAdd(type, null)
            }
        }
        if (layout.widgets.none {
                it.type == DashboardWidgetType.GAME_SPOTLIGHT &&
                    it.spotlightSelectionMode != SpotlightSelectionMode.MANUAL
            }
        ) {
            SheetActionRow(
                Icons.Rounded.AutoFixHigh,
                "Dynamic header image",
                "The most recently opened display is displayed by default, and collection, all games, and random modes can be configured.",
                colors,
            ) {
                onAdd(DashboardWidgetType.GAME_SPOTLIGHT, null)
            }
        }
        Text(
            "Game show",
            color = colors.secondary,
            style = AppleTypography.labelLarge,
            modifier = Modifier.padding(horizontal = KireiSpacing.xl, vertical = KireiSpacing.sm),
        )
        val availableGames = games.filterNot { it.id in usedGames }
        if (availableGames.isEmpty()) {
            Text(
                "No games to add",
                color = colors.secondary,
                style = AppleTypography.bodyMedium,
                modifier = Modifier.padding(horizontal = KireiSpacing.xl, vertical = KireiSpacing.md),
            )
        } else {
            availableGames.forEach { game ->
                DashboardGamePickerRow(game, colors) { onAdd(DashboardWidgetType.GAME_SPOTLIGHT, game.id) }
            }
        }
    }
}

@Composable
internal fun SpotlightConfigSheet(
    spec: DashboardWidgetSpec,
    games: List<Game>,
    colors: KireiColors,
    onDismiss: () -> Unit,
    onSettings: (HomeContentSettings) -> Unit,
) {
    KireiActionSheet(
        "Set header image",
        colors,
        onDismiss,
        expanded = true,
        scrollableContent = false,
    ) {
        HomeContentSettingsPanel(
            settings = spec.toHomeContentSettings(),
            games = games,
            colors = colors,
            onSettingsChange = onSettings,
        )
    }
}

@Composable
internal fun DashboardGamePickerRow(
    game: Game,
    colors: KireiColors,
    onClick: () -> Unit
) {
    KireiListRow(
        spec =
            KireiListRowSpec(
                title = game.title.orEmpty(),
                subtitle = game.engine?.displayName,
                leading = { SettingsCoverThumbnail(game, colors) },
                tileSize = SettingsCoverThumbWidth,
                trailing = KireiRowTrailing.Glyph(Icons.Rounded.Add),
                horizontalPadding = KireiSpacing.xl,
                subtitleMaxLines = 1,
            ),
        colors = colors,
        modifier = Modifier.semantics { contentDescription = "Add ${game.title.orEmpty()}" },
        onClick = onClick,
    )
}

internal fun dashboardWidgetLabel(type: DashboardWidgetType): String =
    when (type) {
        DashboardWidgetType.GAME_SPOTLIGHT -> "Game show"
        DashboardWidgetType.LIBRARY_OVERVIEW -> "Game overview"
        DashboardWidgetType.RECENT_GAMES -> "Recently opened"
        DashboardWidgetType.FAVORITES -> "Collection overview"
        DashboardWidgetType.LIBRARY_SHORTCUT -> "Game library entrance"
        DashboardWidgetType.PLAY_TREND -> "Gaming trends"
    }

internal fun dashboardWidgetDescription(type: DashboardWidgetType): String =
    when (type) {
        DashboardWidgetType.GAME_SPOTLIGHT -> "Display cover and direct launch button"
        DashboardWidgetType.LIBRARY_OVERVIEW -> "Cumulative time, number of games and number of collections"
        DashboardWidgetType.RECENT_GAMES -> "Recently opened games"
        DashboardWidgetType.FAVORITES -> "Collection quantity and cover preview"
        DashboardWidgetType.LIBRARY_SHORTCUT -> "Quickly access the game library"
        DashboardWidgetType.PLAY_TREND -> "7-day or 30-day play line chart"
    }

internal fun dashboardStyleLabel(style: String): String =
    when (style) {
        DashboardStyles.SPOTLIGHT_IMMERSIVE -> "Immersion head map"
        DashboardStyles.SPOTLIGHT_POSTER -> "Poster details"
        DashboardStyles.SPOTLIGHT_HORIZONTAL -> "Laterally compact"
        DashboardStyles.OVERVIEW_MINIMAL -> "Minimalist numbers"
        DashboardStyles.OVERVIEW_SPLIT -> "Column statistics"
        DashboardStyles.OVERVIEW_RING -> "Ring statistics"
        DashboardStyles.OVERVIEW_TILES -> "Three grid data"
        DashboardStyles.OVERVIEW_FOCUS -> "Big numbers focus"
        DashboardStyles.RECENT_STRIP -> "Theme card strip"
        DashboardStyles.RECENT_GLASS -> "Glass list"
        DashboardStyles.RECENT_COVERS -> "Cover slides horizontally"
        DashboardStyles.FAVORITES_STACK -> "Stack covers"
        DashboardStyles.FAVORITES_MOSAIC -> "Cover puzzle"
        DashboardStyles.FAVORITES_FEATURED -> "Main cover"
        DashboardStyles.FAVORITES_COUNT -> "Pure digital compact"
        DashboardStyles.LIBRARY_ICON -> "Icon entrance"
        DashboardStyles.LIBRARY_PROGRESS -> "Progress summary"
        DashboardStyles.TREND_7_DAY -> "7-day line"
        DashboardStyles.TREND_30_DAY -> "30-day area line"
        DashboardStyles.TREND_LAUNCH_7_DAY -> "7-day activation amount"
        DashboardStyles.TREND_LAUNCH_30_DAY -> "Activation amount on 30th day"
        else -> "Default style"
    }

internal fun dashboardWidgetIcon(type: DashboardWidgetType): androidx.compose.ui.graphics.vector.ImageVector =
    when (type) {
        DashboardWidgetType.GAME_SPOTLIGHT -> Icons.Rounded.Gamepad
        DashboardWidgetType.LIBRARY_OVERVIEW -> Icons.Rounded.Timer
        DashboardWidgetType.RECENT_GAMES -> Icons.Rounded.Refresh
        DashboardWidgetType.FAVORITES -> Icons.Rounded.Favorite
        DashboardWidgetType.LIBRARY_SHORTCUT -> Icons.Rounded.GridView
        DashboardWidgetType.PLAY_TREND -> Icons.Rounded.ShowChart
    }
