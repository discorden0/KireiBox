package com.kireibox.app.launcher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.kireibox.app.LauncherActivity
import com.kireibox.app.diagnostics.KireiDiagnosticLogs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun LauncherActivity.copyDiagnosticLogs() {
    lifecycleScope.launch {
        val result =
            runCatching {
                withContext(Dispatchers.IO) { KireiDiagnosticLogs.buildText(this@copyDiagnosticLogs) }
            }
        val text = result.getOrNull()
        if (text == null) {
            showMessage("Copy failed", result.exceptionOrNull()?.message ?: "Unable to read diagnostic log")
            return@launch
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("KireiBox Log", text))
        showMessage("Log copied", "Device information and recent logs have been copied, and common tokens have been automatically covered.")
    }
}

internal fun LauncherActivity.exportDiagnosticLogs() {
    lifecycleScope.launch {
        val result =
            runCatching {
                withContext(Dispatchers.IO) {
                    val text = KireiDiagnosticLogs.buildText(this@exportDiagnosticLogs)
                    KireiDiagnosticLogs.writeExport(this@exportDiagnosticLogs, text)
                }
            }
        val file = result.getOrNull()
        if (file == null) {
            showMessage("Export failed", result.exceptionOrNull()?.message ?: "Unable to generate diagnostic log")
            return@launch
        }
        val uri =
            FileProvider.getUriForFile(
                this@exportDiagnosticLogs,
                "$packageName.fileprovider",
                file,
            )
        val share =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "KireiBox diagnostic log")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        runCatching { startActivity(Intent.createChooser(share, "Export log")) }
            .onFailure { showMessage("Export failed", it.message ?: "No sharing apps available") }
    }
}
