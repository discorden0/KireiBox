package com.kireibox.ui

import com.yuki.yukihub.model.Game
import com.yuki.yukihub.settings.DashboardLayouts
import com.yuki.yukihub.settings.ImmersiveGalleryAutoSource
import com.yuki.yukihub.settings.ImmersiveGallerySettingsPolicy
import com.yuki.yukihub.settings.SpotlightSelectionMode
import com.yuki.yukihub.settings.SpotlightSource
import com.yuki.yukihub.settings.SpotlightStrategy

internal enum class ImmersiveGalleryEmptyState {
    BUILD_LIBRARY,
    SELECT_GAMES,
    NO_PLAY_HISTORY,
    NO_FAVORITES,
}

/** Pure draft/validation contract for the immersive gallery (spotlight) editor. */
object ImmersiveGalleryEditorPolicy {
    /**
     * Orders the gallery for the canonical source/selection contract. Empty manual selections
     * stay empty so the UI can ask the user to select games; automatic RECENT and FAVORITES
     * never substitute the whole library. ALL is the only source that exposes every game.
     */
    fun orderGames(
        games: List<Game>,
        mode: SpotlightSelectionMode,
        manualIds: List<Long>,
        source: SpotlightSource,
    ): List<Game> = orderGamesForSource(games, mode, manualIds, source)

    /** Compatibility overload for the pre-source immersive settings API. */
    fun orderGames(
        games: List<Game>,
        mode: SpotlightSelectionMode,
        manualIds: List<Long>,
        autoSource: ImmersiveGalleryAutoSource = ImmersiveGalleryAutoSource.RECENT_FIVE,
    ): List<Game> {
        val source =
            when (autoSource) {
                ImmersiveGalleryAutoSource.RECENT_FIVE,
                ImmersiveGalleryAutoSource.RECENT_ALL,
                -> SpotlightSource.RECENT
                ImmersiveGalleryAutoSource.FAVORITES -> SpotlightSource.FAVORITES
            }
        val ordered = orderGamesForSource(games, mode, manualIds, source)
        return if (autoSource == ImmersiveGalleryAutoSource.RECENT_FIVE) ordered.take(5) else ordered
    }

    /** Classifies an empty spotlight result without inventing an add-directory action. */
    internal fun emptyState(
        games: List<Game>,
        mode: SpotlightSelectionMode,
        manualIds: List<Long>,
        source: SpotlightSource,
    ): ImmersiveGalleryEmptyState {
        if (games.isEmpty()) return ImmersiveGalleryEmptyState.BUILD_LIBRARY
        if (mode == SpotlightSelectionMode.MANUAL || manualIds.isEmpty() && source == SpotlightSource.FIXED) {
            return ImmersiveGalleryEmptyState.SELECT_GAMES
        }
        return when (source) {
            SpotlightSource.RECENT -> ImmersiveGalleryEmptyState.NO_PLAY_HISTORY
            SpotlightSource.FAVORITES -> ImmersiveGalleryEmptyState.NO_FAVORITES
            // ALL cannot be empty while the library is non-empty; keep the defensive result
            // action-free if a stale caller violates that invariant.
            SpotlightSource.ALL,
            SpotlightSource.FIXED,
            -> ImmersiveGalleryEmptyState.SELECT_GAMES
        }
    }

    private fun orderGamesForSource(
        games: List<Game>,
        mode: SpotlightSelectionMode,
        manualIds: List<Long>,
        source: SpotlightSource,
    ): List<Game> {
        if (mode == SpotlightSelectionMode.MANUAL) {
            val byId = games.associateBy { it.id }
            return manualIds.mapNotNull(byId::get)
        }

        val playedSorted =
            games
                .filter { it.lastPlayedAt > 0L }
                .sortedByDescending { it.lastPlayedAt }
                .distinctBy { it.id }
        return when (source) {
            SpotlightSource.RECENT -> playedSorted
            SpotlightSource.FAVORITES ->
                games
                    .filter { it.favorite }
                    .sortedByDescending { it.lastPlayedAt }
                    .distinctBy { it.id }
            SpotlightSource.ALL -> games
            SpotlightSource.FIXED -> emptyList()
        }
    }

    fun maxPagesCoerced(maxPages: Int): Int = maxPages.coerceIn(1, DashboardLayouts.MAX_SPOTLIGHT_PAGES)

    /** Manual mode cannot save an empty list; the empty state stays editable instead. */
    fun canSaveManual(gameIds: List<Long>): Boolean = gameIds.isNotEmpty()

    /** Toggles an id in/out of the ordered manual list, keeping at most five entries. */
    fun toggleManualId(
        gameIds: List<Long>,
        gameId: Long
    ): List<Long> = ImmersiveGallerySettingsPolicy.toggleManualId(gameIds, gameId)

    fun moveManualId(
        gameIds: List<Long>,
        from: Int,
        to: Int
    ): List<Long> = ImmersiveGallerySettingsPolicy.moveManualId(gameIds, from, to)

    /** True when the manual list has no entry that still exists in the current library. */
    fun manualSelectionMissing(
        spotlightGameIds: List<Long>,
        validGameIds: Set<Long>
    ): Boolean = spotlightGameIds.none { it in validGameIds }

    fun automaticSourceLabel(source: SpotlightSource): String =
        when (source) {
            SpotlightSource.RECENT -> "Recently opened"
            SpotlightSource.FAVORITES -> "collect"
            SpotlightSource.ALL -> "All games"
            SpotlightSource.FIXED -> "All games"
        }

    fun autoSourceLabel(source: ImmersiveGalleryAutoSource): String =
        when (source) {
            ImmersiveGalleryAutoSource.RECENT_FIVE -> "Recently played 5"
            ImmersiveGalleryAutoSource.RECENT_ALL -> "All recently played"
            ImmersiveGalleryAutoSource.FAVORITES -> "Collection first"
        }

    fun autoSourceDescription(source: ImmersiveGalleryAutoSource): String =
        when (source) {
            ImmersiveGalleryAutoSource.RECENT_FIVE -> "Default: Display up to 5 games by last play time"
            ImmersiveGalleryAutoSource.RECENT_ALL -> "Display all games by last play time"
            ImmersiveGalleryAutoSource.FAVORITES -> "Select only from favorite games"
        }

    fun automaticSourceDescription(source: SpotlightSource): String =
        when (source) {
            SpotlightSource.RECENT -> "Prioritize games that have been actually launched recently"
            SpotlightSource.FAVORITES -> "Select only from favorite games"
            SpotlightSource.ALL -> "Select from current game library"
            SpotlightSource.FIXED -> "Select from current game library"
        }

    fun strategyLabel(strategy: SpotlightStrategy): String =
        when (strategy) {
            SpotlightStrategy.FIRST -> "In order"
            SpotlightStrategy.RANDOM_EACH_LAUNCH -> "Randomly starts every time"
            SpotlightStrategy.DAILY_RANDOM -> "Daily random"
        }
}
