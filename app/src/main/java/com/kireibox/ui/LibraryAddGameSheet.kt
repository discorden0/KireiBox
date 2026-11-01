package com.kireibox.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.runtime.Composable
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiTileColor

// "添加游戏" action sheet opened from the library toolbar (both header layouts). A row closes the
// sheet first and then opens its flow page.

@Composable
internal fun LibraryAddGameSheet(
    colors: KireiColors,
    onBatch: () -> Unit,
    onSingle: () -> Unit,
    onDismiss: () -> Unit,
) {
    KireiActionSheet("add game", colors, onDismiss, grouped = true) {
        SheetRowGroup(colors) {
            item {
                SheetActionRow(
                    Icons.Rounded.CreateNewFolder,
                    "Add in batches",
                    "Select multiple folders and confirm the engine and startup files one by one",
                    colors,
                    tile = KireiTileColor.BLUE,
                ) {
                    onDismiss()
                    onBatch()
                }
            }
            item {
                SheetActionRow(
                    Icons.Rounded.SportsEsports,
                    "Add a single game",
                    "Identify the game directory, or select engine, application, shortcut",
                    colors,
                    tile = KireiTileColor.ORANGE,
                ) {
                    onDismiss()
                    onSingle()
                }
            }
        }
    }
}
