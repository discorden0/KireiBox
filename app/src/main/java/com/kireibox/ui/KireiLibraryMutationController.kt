package com.kireibox.ui

import androidx.lifecycle.viewModelScope
import com.kireibox.external.shortcut.ExternalShortcutIdentity
import com.yuki.yukihub.data.GameLibraryOrdering
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class KireiLibraryMutationController(host: KireiViewModel) : KireiViewModelController(host) {
    fun renameGame(
        game: Game,
        title: String
    ) {
        val normalized = title.trim()
        if (normalized.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            game.title = normalized
            game.updatedAt = System.currentTimeMillis()
            repository.update(game)
            withContext(Dispatchers.Main) {
                mutableState.value = mutableState.value.copy(selectedGame = null, notice = "Game name has been updated")
            }
            reloadInternal()
        }
    }

    fun toggleFavorite(game: Game) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = game.copy(favorite = !game.favorite, updatedAt = System.currentTimeMillis())
            val result =
                runCatching {
                    check(repository.update(updated) == 1) { "Failed to save favorite status" }
                    if (mutableState.value.settings?.sortMode == com.yuki.yukihub.settings.YukiSettingsStore.SORT_MANUAL) {
                        check(repository.appendManualOrder(updated.id, updated.favorite)) { "Favorite sorting failed to save" }
                    }
                }
            withContext(Dispatchers.Main) {
                mutableState.value =
                    if (result.isSuccess) {
                        mutableState.value.copy(
                            selectedGame = null,
                            notice = if (updated.favorite) "Collected and pinned" else "Canceled favorites"
                        )
                    } else {
                        mutableState.value.withNotice(result.exceptionOrNull()?.message ?: "Failed to save favorite status", NoticeTone.ERROR)
                    }
            }
            if (result.isSuccess) reloadInternal()
        }
    }

    fun removeFromLibrary(game: Game) {
        val originalIndex = mutableState.value.games.indexOfFirst { it.id == game.id }.coerceAtLeast(0)
        viewModelScope.launch(Dispatchers.IO) {
            val hiddenGame = game.copy(hidden = true, updatedAt = System.currentTimeMillis())
            val result =
                runCatching {
                    check(repository.update(hiddenGame) == 1) { "Unable to move out of game library" }
                }
            withContext(Dispatchers.Main) {
                mutableState.value =
                    if (result.isSuccess) {
                        mutableState.value.copy(
                            games = mutableState.value.games.filterNot { it.id == game.id },
                            removedGames =
                                (mutableState.value.removedGames + hiddenGame)
                                    .distinctBy { it.id }
                                    .sortedByDescending { it.updatedAt },
                            externalShortcutPicker =
                                markShortcutImported(
                                    mutableState.value.externalShortcutPicker,
                                    game,
                                    imported = false,
                                ),
                            selectedGame = null,
                            undoRemoval = UndoRemoval(game.copy(hidden = false), originalIndex),
                            notice = null
                        )
                    } else {
                        mutableState.value.withNotice(result.exceptionOrNull()?.message ?: "Unable to move out of game library", NoticeTone.ERROR)
                    }
            }
        }
    }

    fun undoLastRemoval() {
        val undo = mutableState.value.undoRemoval ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val restoredGame = undo.game.copy(hidden = false, updatedAt = System.currentTimeMillis())
            val result = runCatching { check(repository.update(restoredGame) == 1) { "Recovery failed" } }
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    val restored =
                        mutableState.value.games.toMutableList().apply {
                            if (none { it.id == restoredGame.id }) add(undo.index.coerceIn(0, size), restoredGame)
                        }
                    mutableState.value =
                        mutableState.value.copy(
                            games = GameLibraryOrdering.sorted(restored, mutableState.value.settings?.sortMode.orEmpty()),
                            removedGames = mutableState.value.removedGames.filterNot { it.id == restoredGame.id },
                            externalShortcutPicker =
                                markShortcutImported(
                                    mutableState.value.externalShortcutPicker,
                                    restoredGame,
                                    imported = true,
                                ),
                            undoRemoval = null,
                            notice = "Restored ${restoredGame.title ?:"游戏"}"
                        )
                } else {
                    mutableState.value = mutableState.value.withNotice(result.exceptionOrNull()?.message ?: "Recovery failed", NoticeTone.ERROR)
                }
            }
        }
    }

    fun dismissUndoRemoval() {
        mutableState.value = mutableState.value.copy(undoRemoval = null)
    }

    fun moveManualGame(
        gameId: Long,
        delta: Int
    ) {
        if (delta == 0 || mutableState.value.settings?.sortMode != com.yuki.yukihub.settings.YukiSettingsStore.SORT_MANUAL) return
        val games = mutableState.value.games
        val moving = games.firstOrNull { it.id == gameId } ?: return
        val group = games.filter { it.favorite == moving.favorite }.toMutableList()
        val from = group.indexOfFirst { it.id == gameId }
        if (from < 0) return
        val to = (from + delta).coerceIn(0, group.lastIndex)
        if (to == from) return
        val item = group.removeAt(from)
        group.add(to, item)
        val orderedGroup = group.mapIndexed { index, game -> game.copy(manualOrder = (index + 1L) * 1024L) }
        val replacements = orderedGroup.associateBy(Game::id)
        val reordered = games.map { replacements[it.id] ?: it }
        mutableState.value =
            mutableState.value.copy(
                games =
                    GameLibraryOrdering.sorted(
                        reordered,
                        com.yuki.yukihub.settings.YukiSettingsStore.SORT_MANUAL,
                    ),
            )
    }

    fun commitManualOrder(favorite: Boolean) {
        val ids = mutableState.value.games.filter { it.favorite == favorite }.map { it.id }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { repository.reorderManual(favorite, ids) }
            if (result.isFailure) {
                withContext(Dispatchers.Main) {
                    mutableState.value =
                        mutableState.value.withNotice(result.exceptionOrNull()?.message ?: "Manual sorting and saving failed", NoticeTone.ERROR)
                }
                reloadInternal()
            }
        }
    }

    fun saveManualOrder(
        favoriteIds: List<Long>,
        regularIds: List<Long>
    ) {
        val before = mutableState.value.games
        val favoriteOrder = favoriteIds.withIndex().associate { it.value to (it.index + 1L) * 1024L }
        val regularOrder = regularIds.withIndex().associate { it.value to (it.index + 1L) * 1024L }
        val optimistic =
            before.map { game ->
                val order = if (game.favorite) favoriteOrder[game.id] else regularOrder[game.id]
                if (order == null) game else game.copy(manualOrder = order)
            }
        mutableState.value =
            mutableState.value.copy(
                games = GameLibraryOrdering.sorted(optimistic, com.yuki.yukihub.settings.YukiSettingsStore.SORT_MANUAL),
            )
        viewModelScope.launch(Dispatchers.IO) {
            val result =
                runCatching {
                    repository.reorderManualGroups(favoriteIds, regularIds)
                    core.settings.putString(
                        com.yuki.yukihub.settings.YukiSettingsStore.KEY_SORT_MODE,
                        com.yuki.yukihub.settings.YukiSettingsStore.SORT_MANUAL,
                    )
                }
            if (result.isSuccess) {
                reloadInternal()
            } else {
                withContext(Dispatchers.Main) {
                    mutableState.value =
                        mutableState.value.copy(games = before).withNotice(result.exceptionOrNull()?.message ?: "Custom sorting failed to save", NoticeTone.ERROR)
                }
            }
        }
    }

    fun setArtemisVersion(
        game: Game,
        version: com.yuki.yukihub.launcher.ArtemisVersion
    ) {
        if (game.id <= 0L || game.engine != EngineType.ARTEMIS) return
        viewModelScope.launch(Dispatchers.IO) {
            val updated =
                game.copy(
                    emulatorPackage = version.emulatorPackage,
                    updatedAt = System.currentTimeMillis(),
                )
            val result =
                runCatching {
                    check(repository.update(updated) == 1) { "Failed to save Artemis version" }
                }
            withContext(Dispatchers.Main) {
                mutableState.value =
                    if (result.isSuccess) {
                        mutableState.value.copy(
                            games = mutableState.value.games.map { if (it.id == updated.id) updated else it },
                            selectedGame = updated,
                            notice = "Artemis version has been set to ${version.displayName}",
                        )
                    } else {
                        mutableState.value.withNotice(result.exceptionOrNull()?.message ?: "Failed to save Artemis version", NoticeTone.ERROR)
                    }
            }
        }
    }

    private fun markShortcutImported(
        picker: ExternalShortcutPickerUi?,
        game: Game,
        imported: Boolean,
    ): ExternalShortcutPickerUi? {
        val identity = ExternalShortcutIdentity.fromGame(game) ?: return picker
        val key = "${identity.userId}/${identity.packageName}/${identity.shortcutId}"
        return picker?.copy(
            entries =
                picker.entries.map { entry ->
                    if (entry.stableKey == key) entry.copy(imported = imported) else entry
                },
            selectedKeys = if (imported) picker.selectedKeys - key else picker.selectedKeys,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Library entries (XP3 / Android app / external shortcut / KRKR)
// ---------------------------------------------------------------------------------------------
