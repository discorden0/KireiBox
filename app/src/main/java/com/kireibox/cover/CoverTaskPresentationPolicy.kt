package com.kireibox.cover

import com.kireibox.ui.KireiLayoutMode
import java.util.Locale

internal object CoverTaskPresentationPolicy {
    data class Section(val title: String, val tasks: List<CoverTaskUi>)

    data class TaskButtonState(
        val visible: Boolean,
        val aggregateProgress: Float,
        val completedCount: Int,
        val totalCount: Int,
        val pendingCount: Int,
        val hasFailure: Boolean,
        val showCompletionCheck: Boolean,
        val contentDescription: String,
    )

    data class AnchoredContainerMetrics(
        val widthDp: Float,
        val maxHeightDp: Float,
    )

    /**
     * Fraction of the queue whose cover has actually been set. Only [CoverTaskStatus.COMPLETED]
     * counts as done (a running download counts its byte fraction); failed, cancelled and
     * awaiting-review tasks are *not* finished work, so a batch that all failed reads 0% instead
     * of 100% (11 failures previously looked "complete"). The failure itself is surfaced by the
     * badge and the red ring, not by the progress figure.
     */
    fun aggregateProgress(state: CoverQueueUiState): Float {
        if (state.tasks.isEmpty()) return 0f
        val completed =
            state.tasks.sumOf { task ->
                when (task.status) {
                    CoverTaskStatus.COMPLETED -> 1.0
                    CoverTaskStatus.DOWNLOADING -> task.downloadFraction()
                    CoverTaskStatus.NEEDS_REVIEW, CoverTaskStatus.FAILED,
                    CoverTaskStatus.CANCELLED, CoverTaskStatus.QUEUED, CoverTaskStatus.SEARCHING -> 0.0
                }
            }
        return (completed / state.tasks.size).toFloat()
    }

    fun displayOrder(tasks: List<CoverTaskUi>): List<CoverTaskUi> =
        tasks.filter { it.status in ATTENTION_STATUSES } +
            tasks.filter { it.status in ACTIVE_STATUSES } +
            tasks.filter { it.status in TERMINAL_STATUSES }

    fun badge(tasks: List<CoverTaskUi>): CoverTaskBadge =
        CoverTaskBadge(
            pendingCount = tasks.count { it.status in ATTENTION_STATUSES },
            hasFailure = tasks.any { it.status == CoverTaskStatus.FAILED },
        )

    fun containerFor(layoutMode: KireiLayoutMode): CoverTaskContainer =
        when (layoutMode) {
            KireiLayoutMode.PHONE_PORTRAIT -> CoverTaskContainer.BOTTOM_SHEET
            KireiLayoutMode.WIDE -> CoverTaskContainer.ANCHORED_POPOVER
        }

    fun buttonState(tasks: List<CoverTaskUi>): TaskButtonState {
        val badge = badge(tasks)
        val completedCount = tasks.count { it.status == CoverTaskStatus.COMPLETED }
        val totalCount = tasks.size
        return TaskButtonState(
            visible = tasks.isNotEmpty(),
            aggregateProgress = aggregateProgress(CoverQueueUiState(tasks, CoverQueueStatus.RUNNING)),
            completedCount = completedCount,
            totalCount = totalCount,
            pendingCount = badge.pendingCount,
            hasFailure = badge.hasFailure,
            showCompletionCheck = shouldShowCompletionCheck(tasks),
            contentDescription = "Cover task, completed $completedCount / $totalCount, pending ${badge.pendingCount}",
        )
    }

    fun anchoredContainerMetrics(
        windowWidthDp: Float,
        windowHeightDp: Float,
        safeTopDp: Float,
        safeRightDp: Float,
        safeBottomDp: Float,
    ): AnchoredContainerMetrics =
        AnchoredContainerMetrics(
            widthDp =
                (windowWidthDp - safeRightDp - POPOVER_MARGIN_DP)
                    .coerceAtLeast(0f)
                    .coerceAtMost(POPOVER_WIDTH_DP),
            maxHeightDp =
                (windowHeightDp - safeTopDp - safeBottomDp - POPOVER_MARGIN_DP)
                    .coerceAtLeast(0f),
        )

    fun shouldShowTaskButton(tasks: List<CoverTaskUi>): Boolean = tasks.isNotEmpty()

    fun shouldDismissCompletedRows(
        tasks: List<CoverTaskUi>,
        panelOpen: Boolean
    ): Boolean = tasks.isNotEmpty() && tasks.all { it.status == CoverTaskStatus.COMPLETED }

    fun rowActionFor(status: CoverTaskStatus): CoverTaskRowAction =
        when (status) {
            CoverTaskStatus.NEEDS_REVIEW -> CoverTaskRowAction.SELECT
            CoverTaskStatus.FAILED -> CoverTaskRowAction.RETRY
            CoverTaskStatus.QUEUED, CoverTaskStatus.SEARCHING, CoverTaskStatus.DOWNLOADING -> CoverTaskRowAction.CANCEL
            CoverTaskStatus.CANCELLED -> CoverTaskRowAction.UNDO
            CoverTaskStatus.COMPLETED -> CoverTaskRowAction.NONE
        }

    fun sections(tasks: List<CoverTaskUi>): List<Section> =
        listOf(
            Section("Need to be processed", tasks.filter { it.status in ATTENTION_STATUSES }),
            Section("Processing", tasks.filter { it.status in ACTIVE_STATUSES }),
            Section("Completed", tasks.filter { it.status in TERMINAL_STATUSES }),
        ).filter { it.tasks.isNotEmpty() }

    fun actionLabel(action: CoverTaskRowAction): String =
        when (action) {
            CoverTaskRowAction.SELECT -> "Select"
            CoverTaskRowAction.RETRY -> "Retry"
            CoverTaskRowAction.CANCEL -> "Cancel"
            CoverTaskRowAction.UNDO -> "Cancel"
            CoverTaskRowAction.NONE -> ""
        }

    fun phaseLabel(task: CoverTaskUi): String =
        when (task.status) {
            CoverTaskStatus.QUEUED -> "Waiting for processing"
            CoverTaskStatus.SEARCHING -> "Matching"
            CoverTaskStatus.DOWNLOADING -> task.totalBytes?.takeIf { it > 0L }?.let { "Downloading ${((task.bytesRead * 100) / it).coerceIn(0, 100)}%" } ?: "Downloading"
            CoverTaskStatus.NEEDS_REVIEW -> "Waiting for confirmation"
            CoverTaskStatus.FAILED -> task.error ?: "Match failed"
            CoverTaskStatus.COMPLETED -> "Completed"
            CoverTaskStatus.CANCELLED -> "Canceled"
        }

    fun shouldShowCompletionCheck(tasks: List<CoverTaskUi>): Boolean = tasks.isNotEmpty() && tasks.all { it.status in TERMINAL_STATUSES }

    /** State shown on a row's status chip: the phase, with the percentage of a running download; a failure's reason has its own line. */
    fun chipLabel(task: CoverTaskUi): String = if (task.status == CoverTaskStatus.FAILED) "Match failed" else phaseLabel(task)

    fun downloadBytesLabel(task: CoverTaskUi): String = task.totalBytes?.let { "${formatBytes(task.bytesRead)} / ${formatBytes(it)}" } ?: "Downloaded ${formatBytes(task.bytesRead)}"

    /** Fraction of a running download, or null while the length is unknown (an indeterminate bar). */
    fun downloadProgress(task: CoverTaskUi): Float? = task.totalBytes?.takeIf { it > 0L }?.let { task.downloadFraction().toFloat() }

    /** Whole bytes below 1 KB, then KB / MB / GB with one decimal (1024-based, like the file browser). */
    fun formatBytes(bytes: Long): String =
        when {
            bytes < BYTES_PER_KB -> "$bytes B"
            bytes < BYTES_PER_MB -> String.format(Locale.ROOT, "%.1f KB", bytes.toDouble() / BYTES_PER_KB)
            bytes < BYTES_PER_GB -> String.format(Locale.ROOT, "%.1f MB", bytes.toDouble() / BYTES_PER_MB)
            else -> String.format(Locale.ROOT, "%.1f GB", bytes.toDouble() / BYTES_PER_GB)
        }

    private fun CoverTaskUi.downloadFraction(): Double {
        val total = totalBytes ?: return 0.0
        if (total <= 0L) return 0.0
        return (bytesRead.toDouble() / total).coerceIn(0.0, 1.0)
    }

    private val ATTENTION_STATUSES = setOf(CoverTaskStatus.NEEDS_REVIEW, CoverTaskStatus.FAILED)
    private val ACTIVE_STATUSES = setOf(CoverTaskStatus.QUEUED, CoverTaskStatus.SEARCHING, CoverTaskStatus.DOWNLOADING)
    private val TERMINAL_STATUSES = setOf(CoverTaskStatus.COMPLETED, CoverTaskStatus.CANCELLED)
    private const val BYTES_PER_KB = 1024L
    private const val BYTES_PER_MB = 1024L * 1024L
    private const val BYTES_PER_GB = 1024L * 1024L * 1024L
    private const val POPOVER_WIDTH_DP = 360f
    private const val POPOVER_MARGIN_DP = 24f
}
