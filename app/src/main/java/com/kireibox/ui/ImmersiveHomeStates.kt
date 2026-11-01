package com.kireibox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.components.KireiSegment
import com.yuki.yukihub.settings.HomePresentationMode

@Composable
internal fun ImmersiveEmptyState(
    onChooseDirectory: () -> Unit,
    palette: ImmersivePalette
) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        KireiMascot(Modifier.size(112.dp).clip(KireiShapes.lg), contentDescription = null)
        Text("No games yet", color = palette.primary, style = AppleTypography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
        Text("Start your story after selecting the game directory", color = palette.secondary, style = AppleTypography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
        ImmersiveEmptyAction("Select directory", palette, onChooseDirectory)
    }
}

/** Primary pill action of an immersive empty state: accent fill, play ink, a real button node. */
@Composable
private fun ImmersiveEmptyAction(
    label: String,
    palette: ImmersivePalette,
    onClick: () -> Unit,
) {
    Text(
        label,
        color = palette.playInk,
        style = AppleTypography.bodyMediumEmphasis,
        modifier =
            Modifier.padding(top = 20.dp)
                .heightIn(min = 48.dp)
                .clip(CircleShape)
                .background(palette.accent)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 14.dp),
    )
}

/**
 * 最近游玩 with no history yet. The library may well have games, so the state offers the gallery
 * editor (change the source to 全部, or pick games) instead of a dead end.
 */
@Composable
internal fun ImmersiveNoPlayHistoryState(
    palette: ImmersivePalette,
    onOpenEditor: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        KireiMascot(Modifier.size(112.dp).clip(KireiShapes.lg), contentDescription = null)
        Text("No play records yet", color = palette.primary, style = AppleTypography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
        Text(
            "After playing any game, the games will be displayed here according to the most recently played games.",
            color = palette.secondary,
            style = AppleTypography.bodyMedium,
            modifier = Modifier.padding(top = 10.dp),
        )
        ImmersiveEmptyAction(IMMERSIVE_CHANGE_GALLERY_LABEL, palette, onOpenEditor)
    }
}

/** The empty states' way out: the 编辑沉浸式展示图 editor, where the source or the games change. */
internal const val IMMERSIVE_CHANGE_GALLERY_LABEL = "Change display content"

@Composable
internal fun ImmersiveSelectGamesState(
    palette: ImmersivePalette,
    onOpenEditor: () -> Unit
) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        KireiMascot(Modifier.size(112.dp).clip(KireiShapes.lg), contentDescription = null)
        Text("Please select a game", color = palette.primary, style = AppleTypography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
        // The action opens the gallery editor (编辑沉浸式展示图), not Settings: say so.
        Text(
            "Choose games to feature on the immersive homepage",
            color = palette.secondary,
            style = AppleTypography.bodyMedium,
            modifier = Modifier.padding(top = 10.dp),
        )
        ImmersiveEmptyAction("Select game", palette, onOpenEditor)
    }
}

/** 收藏 with no favorites: says how the gallery fills and offers the gallery editor. */
@Composable
internal fun ImmersiveNoFavoritesState(
    palette: ImmersivePalette,
    onOpenEditor: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        KireiMascot(Modifier.size(112.dp).clip(KireiShapes.lg), contentDescription = null)
        Text("No favorites yet", color = palette.primary, style = AppleTypography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
        Text(
            "After collecting games in the game library, your collection will be displayed here",
            color = palette.secondary,
            style = AppleTypography.bodyMedium,
            modifier = Modifier.padding(top = 10.dp),
        )
        ImmersiveEmptyAction(IMMERSIVE_CHANGE_GALLERY_LABEL, palette, onOpenEditor)
    }
}

/** 首页样式: two options, so an inline segmented control (spec §8) instead of a sheet. */
@Composable
internal fun HomePresentationModeRow(
    value: HomePresentationMode,
    colors: KireiColors,
    onChange: (HomePresentationMode) -> Unit,
) {
    Box(Modifier.testTag("home-mode-current")) {
        SettingsSegmentedRow(
            SettingsSegmentedRowSpec(
                title = "Home page style",
                segments = HomePresentationMode.entries.map { KireiSegment(it, it.displayName) },
                selected = value,
            ),
            colors,
            onChange,
        )
    }
}
