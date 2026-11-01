package com.kireibox.ui

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.kireibox.external.shortcut.ExternalShortcutDirectLaunch
import com.kireibox.external.shortcut.ExternalShortcutIdentity
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.storage.StorageRootIdentity
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Presentation-only formatter. Raw persisted URIs remain untouched. */
object GameLocationLabel {
    fun forGame(
        context: Context,
        game: Game
    ): String {
        if (game.engine == EngineType.ANDROID_APP) {
            ExternalShortcutIdentity.fromGame(game)?.let {
                return "Application shortcuts / ${it.packageName} / ${it.shortcutId}"
            }
            return listOfNotNull(
                game.emulatorPackage?.trim()?.takeIf(String::isNotEmpty),
                game.launchTarget?.trim()?.takeIf(String::isNotEmpty)
            ).joinToString(" / ").ifBlank { "Android App" }
        }
        val root = format(context, game.rootUri)
        val entry = game.entryKey.trim().takeIf(String::isNotEmpty)
        return listOfNotNull(root.takeIf(String::isNotEmpty), entry?.let(::formatSegments))
            .joinToString(" / ")
            .ifBlank { "Game directory" }
    }

    fun launchSubtitle(game: Game): String =
        when {
            ExternalShortcutDirectLaunch.fromGame(game) != null -> "Launch application shortcut directly"
            ExternalShortcutIdentity.isExternalShortcut(game) -> "Started by Shizuku"
            game.engine == EngineType.KIRIKIRI -> "Launch using KRKR"
            game.engine == EngineType.ANDROID_APP -> "Launch Android apps directly"
            game.engine == EngineType.WINDOWS -> "Run via external Winlator"
            else -> "Start with current engine"
        }

    fun exeLaunchSubtitle(game: Game): String = launchFileName(game, "Automatically select startup programs")

    fun xp3LaunchSubtitle(game: Game): String = launchFileName(game, "Automatically select the right XP3")

    private fun launchFileName(
        game: Game,
        fallback: String,
    ): String =
        game.launchTarget
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.replace('\\', '/')
            ?.substringAfterLast('/')
            ?.let(::decode)
            ?.takeIf(String::isNotBlank)
            ?: fallback

    fun format(
        context: Context,
        raw: String?
    ): String {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return "Directory not set"
        if (value.startsWith("file://", ignoreCase = true)) {
            return formatStorageIdentity(StorageRootIdentity.key(value))
        }
        if (!value.startsWith("content://", ignoreCase = true)) {
            return Uri.decode(value).replace('\\', '/').let(::formatSegments)
        }
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return Uri.decode(value)
        val documentId =
            runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
                ?: runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
        if (!documentId.isNullOrBlank()) return formatDocumentId(Uri.decode(documentId))
        val name =
            runCatching {
                DocumentFile.fromTreeUri(context, uri)?.name
                    ?: DocumentFile.fromSingleUri(context, uri)?.name
            }.getOrNull()
        return name?.takeIf(String::isNotBlank) ?: Uri.decode(value)
    }

    fun formatDocumentId(documentId: String): String {
        val decoded = decode(documentId).trim().replace('\\', '/')
        val volume = decoded.substringBefore(':', missingDelimiterValue = "")
        val relative = decoded.substringAfter(':', missingDelimiterValue = decoded).trim('/')
        val root =
            when {
                volume.equals("primary", ignoreCase = true) -> "Internal storage"
                volume.equals("home", ignoreCase = true) -> "Internal Storage/Documents"
                volume.isNotEmpty() -> "Memory card $volume"
                else -> ""
            }
        return listOfNotNull(root.takeIf(String::isNotEmpty), relative.takeIf(String::isNotEmpty)?.let(::formatSegments))
            .joinToString(" / ")
            .ifBlank { formatSegments(decoded) }
    }

    private fun formatStorageIdentity(identity: String): String {
        val value = identity.trim()
        return when {
            value.startsWith("primary:/") ->
                listOf(
                    "Internal storage",
                    value.removePrefix("primary:/").takeIf(String::isNotBlank)?.let(::formatSegments),
                ).filterNotNull().joinToString(" / ")
            value.startsWith("volume:") -> {
                val volumeAndPath = value.removePrefix("volume:")
                val volume = volumeAndPath.substringBefore(":/")
                val relative = volumeAndPath.substringAfter(":/", "")
                listOf(
                    "Memory card $volume",
                    relative.takeIf(String::isNotBlank)?.let(::formatSegments),
                ).filterNotNull().joinToString(" / ")
            }
            value.startsWith("file:") -> formatSegments(value.removePrefix("file:"))
            else -> formatSegments(value)
        }
    }

    private fun formatSegments(value: String): String =
        value
            .split('/')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString(" / ")

    private fun decode(value: String): String =
        runCatching {
            URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
        }.getOrDefault(value)
}
