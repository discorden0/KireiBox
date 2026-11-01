package com.kireibox.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.unit.Dp
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.LocalResolvedMotionMode
import com.yuki.yukihub.settings.MotionMode
import dev.chrisbanes.haze.HazeState

// Settings navigation: the root page, every sub-page and the transition between them. Each page
// keeps its own saveable state (scroll position, expanded lists) while the user moves around.

// The shell hands every settings page the same window metrics plus the 游玩记录 opener.
@Suppress("LongParameterList")
@Composable
internal fun SettingsPage(
    state: KireiUiState,
    colors: KireiColors,
    haze: HazeState?,
    viewModel: KireiViewModel,
    actions: KireiActions,
    layoutMode: KireiLayoutMode,
    safeDrawingPadding: PaddingValues,
    bottomContentPadding: Dp,
    adaptiveMetrics: AdaptiveUiMetrics,
    onOpenPlayTimeOverview: () -> Unit = {},
) {
    val reducedMotion = LocalResolvedMotionMode.current == MotionMode.REDUCED
    val stateHolder = rememberSaveableStateHolder()
    val reveal = remember { SettingsRevealState() }
    val context = SettingsPageContext(state, colors, haze, viewModel, actions, adaptiveMetrics, reveal, onOpenPlayTimeOverview)
    AnimatedContent(
        targetState = state.settingsPage,
        transitionSpec = {
            if (reducedMotion) {
                fadeIn(tween(120)) togetherWith fadeOut(tween(100))
            } else {
                val direction =
                    if (
                        KireiBackPolicy.settingsDepth(targetState) >= KireiBackPolicy.settingsDepth(initialState)
                    ) {
                        1
                    } else {
                        -1
                    }
                (fadeIn(tween(200)) + slideInHorizontally(tween(200)) { width -> direction * (width / 10) }) togetherWith
                    (fadeOut(tween(150)) + slideOutHorizontally(tween(180)) { width -> -direction * (width / 12) })
            }
        },
        label = "settingsNavigation",
    ) { page ->
        stateHolder.SaveableStateProvider(page.name) {
            if (page == KireiSettingsPage.ROOT) {
                SettingsRootPage(context)
            } else {
                SettingsSubPage(page, context)
            }
        }
    }
}

/** Every sub-page: the large-title scaffold with a back button, then the page's own groups. */
@Suppress("CyclomaticComplexMethod") // one branch per page: the exhaustive dispatch is the point
@Composable
internal fun SettingsSubPage(
    page: KireiSettingsPage,
    context: SettingsPageContext,
) {
    val state = context.state
    val colors = context.colors
    val viewModel = context.viewModel
    val actions = context.actions
    val scroll = rememberScrollState()
    SettingsPageScaffold(SettingsHeaderSpec(settingsPageTitle(page), onBack = viewModel::navigateUpSettings, page = page), context, scroll) {
        when (page) {
            KireiSettingsPage.APPEARANCE -> AppearanceSettings(state, colors, viewModel)
            KireiSettingsPage.LIBRARY_HUB -> LibraryHubSettings(state, colors, viewModel)
            KireiSettingsPage.ENGINES -> EngineHubSettings(colors, viewModel)
            KireiSettingsPage.KRKR -> KrkrSettings(state, colors, viewModel, actions)
            KireiSettingsPage.ONS -> OnsSettingsPage(colors)
            KireiSettingsPage.ARTEMIS -> ArtemisSettingsPage(state, colors, viewModel)
            KireiSettingsPage.TYRANO -> TyranoSettingsPage(state, colors, viewModel)
            KireiSettingsPage.WINLATOR -> WinlatorSettingsPage(colors, viewModel)
            KireiSettingsPage.COVERS -> CoverSettings(context, scroll)
            KireiSettingsPage.COVER_ACCOUNTS -> CoverAccountSettings(state, colors, viewModel)
            KireiSettingsPage.PLAYTIME_SYNC -> PlaytimeSyncSettings(context)
            KireiSettingsPage.LIBRARY -> LibrarySettings(state, colors, viewModel, actions)
            KireiSettingsPage.INTERFACE -> InterfaceSettings(state, colors, viewModel)
            KireiSettingsPage.PERMISSIONS -> PermissionSettings(state, colors, actions)
            KireiSettingsPage.DIAGNOSTICS -> DiagnosticSettings(state, colors, viewModel, actions)
            KireiSettingsPage.ABOUT -> AboutSettings(state, colors, viewModel)
            KireiSettingsPage.ROOT -> Unit
        }
    }
}

@Suppress("CyclomaticComplexMethod") // one title per page
internal fun settingsPageTitle(page: KireiSettingsPage): String =
    when (page) {
        KireiSettingsPage.ROOT -> "Settings"
        KireiSettingsPage.APPEARANCE -> "Appearance"
        KireiSettingsPage.LIBRARY_HUB -> "Game library"
        KireiSettingsPage.LIBRARY -> "Scan directory"
        KireiSettingsPage.COVERS -> "Cover and metadata"
        KireiSettingsPage.COVER_ACCOUNTS -> "Cover source account"
        KireiSettingsPage.PLAYTIME_SYNC -> PLAYTIME_SYNC_TITLE
        KireiSettingsPage.ENGINES -> "Global engine settings"
        KireiSettingsPage.KRKR -> "KRKR engine settings"
        KireiSettingsPage.ONS -> "ONS engine settings"
        KireiSettingsPage.ARTEMIS -> "Artemis engine settings"
        KireiSettingsPage.TYRANO -> "Tyrano engine settings"
        KireiSettingsPage.WINLATOR -> "Winlator engine settings"
        // r4 S1: the 首页 page (style, shown games and immersive effects); the id stays INTERFACE.
        KireiSettingsPage.INTERFACE -> "front page"
        KireiSettingsPage.PERMISSIONS -> "Storage and permissions"
        KireiSettingsPage.DIAGNOSTICS -> "Logging and Diagnostics"
        KireiSettingsPage.ABOUT -> "About"
    }
