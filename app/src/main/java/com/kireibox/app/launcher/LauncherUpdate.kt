package com.kireibox.app.launcher

import com.kireibox.app.LauncherActivity
import java.io.File

internal fun LauncherActivity.installUpdate(apkPath: String) {
    val updateRoot = runCatching { File(cacheDir, "updates").canonicalFile }.getOrNull()
    val apk = runCatching { File(apkPath).canonicalFile }.getOrNull()
    if (updateRoot == null || apk == null || !apk.isFile || apk.parentFile != updateRoot) {
        showMessage("Unable to install updates", "The update file location is invalid, please download again.")
        return
    }
    if (!updateInstaller.canRequestPackageInstalls()) {
        pendingUpdateApkPath = apk.absolutePath
        runCatching { startActivity(updateInstaller.unknownSourcesSettingsIntent()) }
            .onFailure {
                pendingUpdateApkPath = null
                showMessage("Unable to open authorization page", it.message ?: "Please allow KireiBox to install unknown applications in system settings.")
            }
        return
    }
    pendingUpdateApkPath = null
    runCatching { updateInstallLauncher.launch(updateInstaller.installIntent(apk)) }
        .onFailure { showMessage("Unable to start installer", it.message ?: "System installer is not available.") }
}
