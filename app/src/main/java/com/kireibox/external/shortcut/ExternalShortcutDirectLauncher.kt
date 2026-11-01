package com.kireibox.external.shortcut

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/** Foreground direct handoff for a recipe that was validated and captured during import. */
object ExternalShortcutDirectLauncher {
    fun launch(
        context: Context,
        identity: ExternalShortcutIdentity.Parsed,
        encodedTarget: String?,
    ): Result<Unit>? {
        val recipe = ExternalShortcutDirectLaunch.parse(encodedTarget) ?: return null
        return runCatching {
            check(identity.packageName == ExternalShortcutDirectLaunch.GAME_HUB_PACKAGE) {
                "The shortcut direct launch information does not match the application, please scan again."
            }
            val spec = recipe.intentSpec()
            val component = ComponentName(spec.packageName, spec.activityName)
            val info =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getActivityInfo(component, 0)
                }
            check(info.applicationInfo.enabled && info.enabled && info.exported && info.permission.isNullOrBlank()) {
                "The direct access to the world is unavailable, please rescan or update the world."
            }
            context.startActivity(
                Intent(spec.action).apply {
                    this.component = component
                    flags = spec.flags
                    spec.extras.forEach { (key, value) -> putExtra(key, value) }
                }
            )
        }.recoverCatching { error ->
            if (error is PackageManager.NameNotFoundException) {
                throw IllegalStateException("No available direct access to the game was found. Please confirm that the original game 6.1.2 is installed or scan again.", error)
            }
            throw error
        }
    }
}
