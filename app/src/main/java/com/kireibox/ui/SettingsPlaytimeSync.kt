package com.kireibox.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiTileColor
import com.kireibox.designsystem.components.KireiButton
import com.kireibox.designsystem.components.KireiButtonOptions
import com.kireibox.designsystem.components.KireiButtonSize
import com.kireibox.designsystem.components.KireiButtonStyle
import com.kireibox.designsystem.components.KireiListGroup
import com.kireibox.designsystem.components.KireiListRow
import com.kireibox.designsystem.components.KireiListRowSpec
import com.kireibox.designsystem.components.KireiRowTone
import com.kireibox.designsystem.components.KireiRowTrailing
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.sync.PlaytimeGame
import com.yuki.yukihub.sync.PlaytimeSyncProblem
import com.yuki.yukihub.sync.PlaytimeSyncStatus

// 同步游玩时长 (doc §5.6): one row on the NextMoe account card and the overview's 上次同步 tile lead
// here; the page holds the switch, 立即同步, and the games that wait for the user.

internal const val PLAYTIME_SYNC_TITLE = "Synchronized play time"

/** 「3 分钟前」 for the overview tile and the status line; 「未同步」 before the first sync. */
internal fun lastSyncLabel(
    lastSyncAt: Long,
    now: Long,
): String {
    val minutes = ((now - lastSyncAt).coerceAtLeast(0L) / 60_000L)
    return when {
        lastSyncAt <= 0L -> "Not synced"
        minutes < 1 -> "Just"
        minutes < 60 -> "$minutes minutes ago"
        minutes < 24 * 60 -> "${minutes / 60} hours ago"
        else -> "${minutes / (24 * 60)} days ago"
    }
}

/**
 * The single status line: what needs the user first, then what is happening, then the result.
 * Null while sync is off (the switch already says so).
 */
internal fun playtimeSyncLine(
    status: PlaytimeSyncStatus,
    connected: Boolean,
    now: Long,
): String? {
    val summary = status.summary
    return when {
        !status.enabled -> null
        !connected -> "Start syncing after logging into NextMoe"
        status.problem == PlaytimeSyncProblem.NEEDS_LOGIN -> "Need to log in to NextMoe again"
        summary.needsConfirm > 0 -> "${summary.needsConfirm} Hospitality Confirmation · Synchronized ${summary.synced}"
        summary.rejected > 0 -> "${summary.rejected} model failed to synchronize · ${summary.synced} model has been synchronized"
        status.problem == PlaytimeSyncProblem.QUOTA -> "The quota has been used up today and will continue tomorrow."
        status.problem == PlaytimeSyncProblem.RATE_LIMITED -> "Requests too frequent, continue later"
        status.problem == PlaytimeSyncProblem.NETWORK -> "Unable to connect at the moment, try again later"
        else -> playtimeSyncProgressLine(status, now)
    }
}

/** Nothing needs the user: what is happening, or how the last sync ended. */
private fun playtimeSyncProgressLine(
    status: PlaytimeSyncStatus,
    now: Long,
): String {
    val summary = status.summary
    return when {
        status.running -> "Syncing..."
        summary.waiting > 0 -> "${summary.waiting} is waiting to be uploaded"
        status.lastSyncAt <= 0 -> "Not synced yet"
        // Nothing played for a minute yet: there is nothing to count, only that NextMoe is current.
        summary.synced == 0 -> "Already the latest · ${lastSyncLabel(status.lastSyncAt, now)}"
        else -> "${summary.synced} style synchronized · ${lastSyncLabel(status.lastSyncAt, now)}"
    }
}

/** What the 同步数据 button's color says. */
internal enum class SyncButtonTone { OFF, IDLE, RUNNING, SYNCED, ATTENTION, FAILED }

internal fun syncButtonTone(
    status: PlaytimeSyncStatus,
    connected: Boolean,
): SyncButtonTone =
    when {
        !connected || !status.enabled || status.problem == PlaytimeSyncProblem.NOT_CONNECTED -> SyncButtonTone.OFF
        status.running -> SyncButtonTone.RUNNING
        status.problem != null -> SyncButtonTone.FAILED
        playtimeAttentionCount(status) > 0 -> SyncButtonTone.ATTENTION
        status.lastSyncAt > 0 && status.summary.waiting == 0 -> SyncButtonTone.SYNCED
        else -> SyncButtonTone.IDLE
    }

/** Games the user has to act on: a work to confirm or pick, or a refusal to resolve. */
internal fun playtimeAttentionCount(status: PlaytimeSyncStatus): Int = status.summary.needsConfirm + status.summary.rejected

internal fun KireiUiState.nextmoeConnected(): Boolean = coverAccounts[CoverSourceMode.NEXTMOE]?.let { it.connected && !it.needsReauth } == true

/** Why NextMoe refused a write, in words. */
internal fun playtimeRejectionReason(code: String): String =
    when (code) {
        "NOT_FOUND", "HTTP_404" -> "This work cannot be found on NextMoe"
        "VALIDATION_FAILED", "HTTP_422" -> "NextMoe rejected this duration"
        else -> "NextMoe refuses to write ($code)"
    }

/** The row under the NextMoe account card: status as its subtitle, a badge while games wait. */
@Composable
internal fun NextmoePlaytimeSyncRow(
    state: KireiUiState,
    colors: KireiColors,
    onOpen: () -> Unit,
) {
    val status = state.playtimeSync
    val attention = playtimeAttentionCount(status)
    KireiListRow(
        KireiListRowSpec(
            title = PLAYTIME_SYNC_TITLE,
            subtitle = playtimeSyncLine(status, state.nextmoeConnected(), System.currentTimeMillis()),
            icon = Icons.Rounded.Sync,
            tile = KireiTileColor.BLUE,
            trailing = if (attention > 0) KireiRowTrailing.Badge(attention) else KireiRowTrailing.Value(if (status.enabled) "Already turned on" else "Closed"),
        ),
        colors,
        onClick = onOpen,
    )
}

@Composable
internal fun PlaytimeSyncSettings(context: SettingsPageContext) {
    val state = context.state
    val colors = context.colors
    val viewModel = context.viewModel
    val status = state.playtimeSync
    val connected = state.nextmoeConnected()
    LaunchedEffect(Unit) { viewModel.loadPlaytimeAttention() }
    // Coming back from the cover picker (选择作品): sync at once so the pick takes effect.
    var pickerWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(state.coverSearch == null) {
        if (state.coverSearch != null) {
            pickerWasOpen = true
        } else if (pickerWasOpen) {
            pickerWasOpen = false
            viewModel.syncPlaytimeNow()
        }
    }
    KireiListGroup(colors, SettingsGroupSpacing) {
        item {
            SettingsToggleRow(PLAYTIME_SYNC_TITLE, status.enabled, colors, subtitle = playtimeSyncLine(status, connected, System.currentTimeMillis())) {
                viewModel.setPlaytimeSyncEnabled(it)
            }
        }
        item {
            val title =
                when {
                    !connected -> "Login NextMoe"
                    status.running -> "Syncing..."
                    else -> "Sync now"
                }
            KireiListRow(
                KireiListRowSpec(title = title, tone = KireiRowTone.ACTION, enabled = !status.running && (status.enabled || !connected)),
                colors,
                onClick = { if (connected) viewModel.syncPlaytimeNow() else viewModel.openSettingsPage(KireiSettingsPage.COVER_ACCOUNTS) },
            )
        }
    }
    val games = state.playtimeAttention.filter { it.row?.rejected == null }
    val refused = state.playtimeAttention.filter { it.row?.rejected != null }
    if (games.isNotEmpty()) {
        KireiListGroup(colors, SettingsGroupSpacing, title = "To be confirmed (${games.size})") {
            games.forEach { game -> item(key = game.gameId) { PlaytimeConfirmRow(game, state, colors, viewModel) } }
        }
    }
    if (refused.isNotEmpty()) {
        KireiListGroup(colors, SettingsGroupSpacing, title = "Failed to sync (${refused.size})") {
            refused.forEach { game ->
                item(key = game.gameId) {
                    val row = PlaytimeRow(game, state, playtimeRejectionReason(game.row?.rejected.orEmpty()), KireiRowTrailing.Chevron)
                    PlaytimeGameRow(row, colors) { viewModel.pickPlaytimeWork(game.gameId) }
                }
            }
        }
    }
}

/** 「是这部」 binds the suggested work; tapping the row picks another one in the cover picker. */
@Composable
private fun PlaytimeConfirmRow(
    game: PlaytimeGame,
    state: KireiUiState,
    colors: KireiColors,
    viewModel: KireiViewModel,
) {
    val candidate = game.row?.candidateId
    val trailing =
        if (candidate == null) {
            KireiRowTrailing.Chevron
        } else {
            KireiRowTrailing.Custom {
                KireiButton(
                    "It's this one",
                    colors,
                    Modifier.semantics { contentDescription = "Confirm that ${game.title} is ${game.row?.candidateTitle.orEmpty()}" },
                    options = KireiButtonOptions(style = KireiButtonStyle.SECONDARY, size = KireiButtonSize.SMALL),
                ) { viewModel.confirmPlaytimeWork(game.gameId, candidate) }
            }
        }
    val subtitle = if (candidate == null) "No corresponding work found · Click to select" else "NextMoe：${game.row?.candidateTitle?.ifBlank { null } ?: "作品 $candidate"}"
    PlaytimeGameRow(PlaytimeRow(game, state, subtitle, trailing), colors) { viewModel.pickPlaytimeWork(game.gameId) }
}

/** One listed game: its library entry (for the cover thumbnail and current title), what to say and what to show trailing. */
private class PlaytimeRow(
    game: PlaytimeGame,
    state: KireiUiState,
    val subtitle: String,
    val trailing: KireiRowTrailing,
) {
    val libraryGame = state.games.firstOrNull { it.id == game.gameId }
    val title = libraryGame?.title ?: game.title
}

@Composable
private fun PlaytimeGameRow(
    row: PlaytimeRow,
    colors: KireiColors,
    onClick: () -> Unit,
) {
    val libraryGame = row.libraryGame
    KireiListRow(
        KireiListRowSpec(
            title = row.title,
            subtitle = row.subtitle,
            leading = libraryGame?.let { { SettingsCoverThumbnail(it, colors) } },
            reserveIconSpace = libraryGame == null,
            tileSize = SettingsCoverThumbWidth,
            trailing = row.trailing,
            subtitleMaxLines = 2,
        ),
        colors,
        onClick = onClick,
    )
}
