package com.kireibox.app.launcher

import android.content.Intent
import androidx.lifecycle.lifecycleScope
import com.kireibox.app.LauncherActivity
import com.kireibox.external.shortcut.ExternalShortcutLaunchPolicy
import com.kireibox.external.shortcut.ShortcutServiceStatus
import com.kireibox.shortcut.DesktopGameShortcutFactory
import com.kireibox.ui.LauncherMemoryCoordinator
import com.yuki.yukihub.data.GameRepository
import com.yuki.yukihub.launcher.EmulatorLauncher
import com.yuki.yukihub.launcherbridge.ExternalPlaySessionTimer
import com.yuki.yukihub.launcherbridge.LauncherGameLaunchBridge
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.util.DevLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun LauncherActivity.launchGame(game: Game) {
    if (ExternalShortcutLaunchPolicy.requiresShizuku(game)) {
        when (shortcutGateway.status()) {
            ShortcutServiceStatus.READY -> Unit
            ShortcutServiceStatus.PERMISSION_REQUIRED -> {
                pendingShizukuLaunch = game
                shortcutGateway.requestPermission().onFailure {
                    pendingShizukuLaunch = null
                    showMessage("Unable to authorize Shizuku", it.message ?: "Shizuku permission request failed.")
                }
                return
            }
            else -> {
                val message =
                    shortcutGateway.requestPermission().exceptionOrNull()?.message
                        ?: "Shizuku shortcut service is unavailable."
                showMessage("Can't launch shortcut", message)
                return
            }
        }
    }
    if (game.engine == EngineType.KIRIKIRI && !hasNativeFileAccess()) {
        DevLogger.event("krkr launch gate: native file access missing -> prompt")
        pendingNativeLaunch = game
        requestNativeFileAccess()
        return
    }
    launchPreparedGame(game)
}

internal fun LauncherActivity.consumeDesktopGameIntent(source: Intent?) {
    if (source?.action != DesktopGameShortcutFactory.ACTION_LAUNCH_GAME) return
    val gameId = source.getLongExtra(DesktopGameShortcutFactory.EXTRA_GAME_ID, -1L)
    source.action = Intent.ACTION_MAIN
    source.removeExtra(DesktopGameShortcutFactory.EXTRA_GAME_ID)
    if (gameId <= 0L) {
        showMessage("Shortcut no longer works", "Desktop shortcut does not have a valid game ID.")
        return
    }
    lifecycleScope.launch(Dispatchers.IO) {
        val game = GameRepository(applicationContext).findById(gameId)
        withContext(Dispatchers.Main) {
            when {
                game == null -> showMessage("Shortcut no longer works", "The game is no longer in the KireiBox game library.")
                game.hidden -> showMessage("Shortcut no longer works", "This game has been hidden from the KireiBox game library.")
                else -> launchGame(game)
            }
        }
    }
}

internal fun LauncherActivity.pinGameShortcut(game: Game) {
    val result = DesktopGameShortcutFactory.requestPin(this, game)
    if (result.isFailure) {
        showMessage("Can't add to desktop", result.exceptionOrNull()?.message ?: "Desktop shortcut creation failed.")
    } else if (result.getOrNull() == DesktopGameShortcutFactory.PinOutcome.UPDATED) {
        viewModel.showNotice("Desktop shortcut updated")
    } else {
        viewModel.showNotice("Please confirm in the system pop-up window to add to the desktop")
    }
}

internal fun LauncherActivity.launchPreparedGame(game: Game) {
    LauncherGameLaunchBridge.launchAsync(
        this,
        game,
        object : LauncherGameLaunchBridge.LaunchCallback {
            override fun onResult(result: LauncherGameLaunchBridge.LaunchResult) {
                if (result.success) {
                    val externalHandoff =
                        result.handoff == LauncherGameLaunchBridge.LaunchHandoff.EXTERNAL_APP ||
                            result.handoff == LauncherGameLaunchBridge.LaunchHandoff.SHORTCUT
                    runningSessionId = result.sessionId
                    engineWasOpened = result.handoff == LauncherGameLaunchBridge.LaunchHandoff.INTERNAL_ENGINE
                    runningSessionJournaled = engineWasOpened
                    externalPlayActive = externalHandoff
                    externalPlayTimer =
                        if (externalHandoff) {
                            ExternalPlaySessionTimer(applicationContext).also { it.start() }
                        } else {
                            externalPlayTimer?.cleanup()
                            null
                        }
                    launcherHandoffActive = result.handoff != LauncherGameLaunchBridge.LaunchHandoff.NONE
                    viewModel.suspendForEngineHandoff()
                    LauncherMemoryCoordinator.onEngineHandoff()
                    viewModel.reload()
                } else if (!hasNativeFileAccess() && isPermissionLaunchFailure(result.message)) {
                    DevLogger.event("launch permission failure routed to grant dialog: ${result.message}")
                    pendingNativeLaunch = game
                    showNativeFileAccessDialog(hint = result.message)
                } else {
                    showMessage("Unable to start", result.message)
                }
            }
        }
    )
}

internal fun LauncherActivity.launchKrkrNative() {
    if (!hasNativeFileAccess()) {
        pendingNativeShell = true
        requestNativeFileAccess()
        return
    }
    openKrkrNativeShell()
}

internal fun LauncherActivity.openKrkrNativeShell() {
    val result =
        runCatching {
            startActivity(
                EmulatorLauncher.buildInternalKrkrIntent(
                    this,
                    null,
                    null,
                    true,
                    "auto",
                    false,
                ),
            )
        }
    if (result.isFailure) {
        showMessage("Unable to start", result.exceptionOrNull()?.message ?: "KRKR native engine failed to start")
    }
}

internal fun LauncherActivity.isPermissionLaunchFailure(message: String?): Boolean {
    if (message.isNullOrBlank()) return false
    return message.contains("EACCES", ignoreCase = true) ||
        message.contains("Permission denied", ignoreCase = true) ||
        message.contains("All file access", ignoreCase = true) ||
        message.contains("Unable to create savedata", ignoreCase = true) ||
        message.contains("Savedata cannot be written", ignoreCase = true) ||
        message.contains("Read-only directory", ignoreCase = true) ||
        message.contains("Write check failed", ignoreCase = true)
}
