package com.kireibox.ui

import com.yuki.yukihub.data.ScanImportSummary

internal object DirectoryAutoRefreshPolicy {
    fun enabledRoots(
        roots: List<String>,
        enabled: List<Boolean>
    ): List<String> = roots.filterIndexed { index, _ -> enabled.getOrElse(index) { true } }

    fun notice(
        summary: ScanImportSummary,
        failedDirectoryCount: Int,
        prunedCount: Int = 0
    ): String? {
        val added = summary.addedCount
        if (added == 0 && failedDirectoryCount == 0 && prunedCount == 0) return null
        val parts = ArrayList<String>(4)
        when {
            added == 1 -> {
                val title = summary.addedGames.first().title?.takeIf { it.isNotBlank() } ?: "Unnamed game"
                parts += "Found and added 1 new game: $title"
            }
            added > 1 -> parts += "Added $added new games"
        }
        if (prunedCount > 0) parts += "Cleaned $prunedCount expired cards"
        if (failedDirectoryCount > 0) parts += "$failedDirectoryCount directory scan failed"
        return "Automatic updates:" + parts.joinToString("，")
    }

    /** Tone of [notice]: a refresh that also changed the library is a warning when some directory failed. */
    fun noticeTone(
        summary: ScanImportSummary,
        failedDirectoryCount: Int,
        prunedCount: Int = 0,
    ): NoticeTone =
        when {
            failedDirectoryCount == 0 -> NoticeTone.SUCCESS
            summary.addedCount > 0 || prunedCount > 0 -> NoticeTone.WARNING
            else -> NoticeTone.ERROR
        }
}

internal class ForegroundRefreshGate {
    private var running = false

    fun tryStart(): Boolean {
        if (running) return false
        running = true
        return true
    }

    fun finish() {
        running = false
    }
}
