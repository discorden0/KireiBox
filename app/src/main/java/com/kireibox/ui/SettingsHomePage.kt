package com.kireibox.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiTileColor
import com.kireibox.designsystem.components.KireiListGroup
import com.kireibox.designsystem.components.KireiListRow
import com.kireibox.designsystem.components.KireiListRowSpec
import com.kireibox.designsystem.components.KireiRowTrailing
import com.yuki.yukihub.settings.HomePresentationMode
import com.yuki.yukihub.settings.ImmersiveGallerySettings
import com.yuki.yukihub.settings.SpotlightSelectionMode
import com.yuki.yukihub.settings.SpotlightSource
import com.yuki.yukihub.settings.SpotlightStrategy

// 首页 → 展示内容 (r4 S1): which games the immersive gallery shows, summarized as one plain sentence
// and edited in the same 「编辑沉浸式展示图」 sheet the immersive home opens (HomeContentSettingsPanel),
// plus 显示游玩时长. The rest of the 首页 page is InterfaceSettings and ImmersiveVisualSettingsPanel.

/** Footer under 首页样式 in components mode: that layout is edited on the home itself (编辑首页). */
internal fun homeStyleFooter(mode: HomePresentationMode): String? = if (mode == HomePresentationMode.COMPONENTS) "Adjust the layout in \"Edit Home Page\" on the home page menu." else null

/** The immersive gallery selection as the settings page reads it from the UI state. */
internal fun KireiUiState.immersiveGallerySelection(): ImmersiveGallerySettings =
    ImmersiveGallerySettings(
        selectionMode = immersiveGalleryMode,
        manualIds = immersiveGalleryIds,
        source = immersiveGallerySource,
        strategy = immersiveGalleryStrategy,
        count = immersiveGalleryCount,
    )

/**
 * One sentence for the 展示内容 row: 「最近打开的 5 个游戏」, 「从收藏中随机 3 个，每天更换」,
 * 「手动挑选的 2 个游戏」… — the same choices the editor sheet offers, in plain words.
 */
internal fun homeContentSummary(settings: ImmersiveGallerySettings): String {
    if (settings.selectionMode == SpotlightSelectionMode.MANUAL) {
        val picked = settings.manualIds.distinct().size
        return if (picked == 0) "Manual selection, no game selected yet" else "$picked games hand picked"
    }
    val count = settings.count.coerceIn(ImmersiveGallerySettings.MIN_COUNT, ImmersiveGallerySettings.MAX_COUNT)
    val pool =
        when (settings.source) {
            SpotlightSource.FAVORITES -> "collect"
            SpotlightSource.ALL -> "All games"
            SpotlightSource.RECENT, SpotlightSource.FIXED -> "Recently opened"
        }
    return when (settings.strategy) {
        SpotlightStrategy.FIRST ->
            when (settings.source) {
                SpotlightSource.FAVORITES -> "Top $count games in collection"
                SpotlightSource.ALL -> "Top $count games in game library"
                SpotlightSource.RECENT, SpotlightSource.FIXED -> "Recently opened $count games"
            }
        SpotlightStrategy.RANDOM_EACH_LAUNCH -> "Random $count from ${pool}, replaced each time"
        SpotlightStrategy.DAILY_RANDOM -> "Random $count from ${pool}, changed every day"
    }
}

/** 展示内容: the selection summary (opens the shared editor sheet) and 显示游玩时长. */
@Composable
internal fun HomeContentSettingsGroup(
    state: KireiUiState,
    colors: KireiColors,
    viewModel: KireiViewModel,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val selection = state.immersiveGallerySelection()
    KireiListGroup(colors, SettingsGroupSpacing, title = "Show content") {
        item {
            SettingsAnchor("Shown games") {
                KireiListRow(
                    KireiListRowSpec(
                        title = "Shown games",
                        subtitle = homeContentSummary(selection),
                        icon = Icons.Rounded.ViewCarousel,
                        tile = KireiTileColor.PURPLE,
                        trailing = KireiRowTrailing.Chevron,
                    ),
                    colors,
                    onClick = { editing = true },
                )
            }
        }
        item {
            SettingsAnchor("Show play time") {
                KireiListRow(
                    KireiListRowSpec(
                        title = "Show play time",
                        reserveIconSpace = true,
                        trailing = KireiRowTrailing.Toggle(state.settings?.immersivePlayTimeVisible != false, viewModel::setImmersivePlayTimeVisible),
                    ),
                    colors,
                )
            }
        }
    }
    if (editing) {
        // The immersive home's own editor (same games order, same writes), so both entries agree.
        ImmersiveGalleryConfigSheet(
            games = LibraryOverviewPolicy.sorted(state.games, LibraryOverviewSort.RECENT),
            mode = selection.selectionMode,
            manualIds = selection.manualIds,
            source = selection.source,
            strategy = selection.strategy,
            count = selection.count,
            colors = colors,
            onDismiss = { editing = false },
            onMode = viewModel::setImmersiveGalleryMode,
            onManualIds = viewModel::setImmersiveGalleryIds,
            onSource = viewModel::setImmersiveGallerySource,
            onStrategy = viewModel::setImmersiveGalleryStrategy,
            onCount = viewModel::setImmersiveGalleryCount,
        )
    }
}
