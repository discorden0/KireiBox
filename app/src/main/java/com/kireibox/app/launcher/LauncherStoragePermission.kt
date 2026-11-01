package com.kireibox.app.launcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.kireibox.app.LauncherActivity
import com.kireibox.ui.AddGamePick
import com.kireibox.ui.BuiltinFileBrowserRequest
import com.kireibox.ui.KireiPrompt
import com.yuki.yukihub.engine.EngineStorageProbe
import com.yuki.yukihub.settings.YukiSettingsStore

internal fun LauncherActivity.chooseStorage(pick: AddGamePick? = null) {
    val useBuiltin =
        YukiSettingsStore(this)
            .snapshot().useBuiltinFileChooser
    if (!useBuiltin) {
        openSystemPicker(pick)
        return
    }
    if (hasNativeFileAccess()) {
        openBuiltinBrowser(pick)
    } else {
        pendingBrowserOpen = true
        pendingBrowserPick = pick
        showBuiltinFileAccessDialog(pick)
    }
}

internal fun LauncherActivity.openBuiltinBrowser(pick: AddGamePick? = null) {
    val internal = Environment.getExternalStorageDirectory()
    val appDirectory = getExternalFilesDir(null) ?: filesDir
    fileBrowserRequest.value =
        BuiltinFileBrowserRequest(
            startPath = internal.absolutePath,
            appPath = appDirectory.absolutePath,
            pick = pick
        )
}

internal fun LauncherActivity.openSystemPicker(pick: AddGamePick? = null) {
    if (pick == null) {
        folderPicker.launch(null)
    } else {
        pendingAddPick = pick
        addGameFolderPicker.launch(null)
    }
}

internal fun LauncherActivity.showBuiltinFileAccessDialog(pick: AddGamePick? = null) {
    if (isFinishing || isDestroyed) return
    showPrompt(
        KireiPrompt(
            title = "Allow KireiBox to browse files",
            message =
                buildString {
                    append("Android 11 and above require \"All File Access\" to use the built-in browser. After authorization, the previous operation will be automatically continued; this version of KireiBox only browses and selects, and does not copy, move, rename or delete files.")
                    if (EngineStorageProbe.isHuaweiFamily()) {
                        append("Huawei/HarmonyOS path: Settings → Application → KireiBox → Permission Management → Storage → Allow; HarmonyOS 3 and above also need to turn on \"All File Access\".")
                    }
                },
            confirmLabel = "Go to Authorization",
            dismissLabel = "Using System SAF",
            onConfirm = {
                val manageApp =
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                runCatching { startActivity(manageApp) }
                    .recoverCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                    .recoverCatching {
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:$packageName")
                            }
                        )
                    }
            },
            onDismiss = {
                pendingBrowserOpen = false
                pendingBrowserPick = null
                openSystemPicker(pick)
            }
        )
    )
}

/** Permission guide text; Huawei/HarmonyOS gets its own path (权限管理 → 存储). */
internal fun LauncherActivity.storagePermissionGuide(android11Plus: Boolean): String =
    if (EngineStorageProbe.isHuaweiFamily()) {
        buildString {
            append("Huawei/HarmonyOS path: Settings → Application → KireiBox → Permission Management → Storage → Allow.")
            if (android11Plus) append("HarmonyOS 3 and above also need to turn on \"All File Access\".")
            append("It has been opened but still cannot be written. Please move the game directory to a regular location such as Download and try again.")
        }
    } else if (android11Plus) {
        "Android 11 and above requires \"All File Access\": Settings → Apps → KireiBox → Permissions → All File Access → Allow. Returning after authorization will automatically continue; if still unable to write, please move the game directory to a regular location in internal storage and try again."
    } else {
        "For Android 10 and below, please grant KireiBox \"Storage\" permission: Settings → Apps → KireiBox → Permissions → Storage → Allow. It has been opened but still cannot be written. Please move the game directory to a regular location such as Download and try again."
    }

/**
 * Android 11+ requires MANAGE_EXTERNAL_STORAGE (all-files access) because
 * scoped storage is mandatory. Android 6-10 use the legacy
 * WRITE_EXTERNAL_STORAGE runtime permission: with targetSdk 29 and
 * requestLegacyExternalStorage it grants full physical access, which is
 * what the KRKR native engine needs to write <game>/savedata.
 */
internal fun LauncherActivity.hasNativeFileAccess(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }

/** Requests the permission model matching this Android version; onResume continues the pending action. */
internal fun LauncherActivity.requestNativeFileAccess() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        showNativeFileAccessDialog()
    } else {
        krkrStoragePermission.launch(
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ),
        )
    }
}

internal fun LauncherActivity.showNativeFileAccessDialog(hint: String? = null) {
    if (isFinishing || isDestroyed) return
    val android11Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    val permissionName = if (android11Plus) "All file access" else "Storage"
    showPrompt(
        KireiPrompt(
            title = "Allow KRKR to read games",
            message =
                buildString {
                    append("Directory authorization is used for scanning and management; KRKR native kernel also requires \"")
                    append(permissionName)
                    append("” permission to directly read XP3 and save the progress in the savedata of the game directory. KireiBox will not modify XP3.")
                    if (!hint.isNullOrBlank()) {
                        append("Reason for last failure:")
                        append(hint)
                    }
                    append("\n\n")
                    append(storagePermissionGuide(android11Plus))
                },
            confirmLabel = "Go to Authorization",
            dismissLabel = "Later",
            onConfirm = {
                val details =
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                if (android11Plus) {
                    val manageApp =
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:$packageName")
                        }
                    runCatching { startActivity(manageApp) }
                        .recoverCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                        .recoverCatching { startActivity(details) }
                        .onFailure { showMessage("Can't open settings", "Please manually allow KireiBox's \"All File Access\" in the system settings and try again.") }
                } else {
                    runCatching { startActivity(details) }
                        .onFailure { showMessage("Can't open settings", "Please manually allow KireiBox's \"Storage\" permission in the system settings and try again.") }
                }
            }
        )
    )
}

internal fun LauncherActivity.checkPermissions() {
    val roots = YukiSettingsStore(this).scanRoots()
    val persisted = contentResolver.persistedUriPermissions.associateBy { it.uri.toString() }
    val details =
        buildString {
            append("KRKR native file access:")
            append(if (hasNativeFileAccess()) "Normal" else "Authorization required")
            append('\n')
            if (roots.isEmpty()) append("No scan directories have been added yet.")
            roots.forEachIndexed { index, root ->
                val grant = persisted[root]
                val doc = runCatching { DocumentFile.fromTreeUri(this@checkPermissions, Uri.parse(root)) }.getOrNull()
                append("Directory ${index + 1}:")
                append(if (grant?.isReadPermission == true && doc?.canRead() == true) "Readable" else "Requires reauthorization")
                append(if (grant?.isWritePermission == true && doc?.canWrite() == true) "/ writable" else "/readonly")
                append('\n')
            }
            append("SAF is responsible for scanning, cover and directory management; KRKR native kernel uses physical file paths.")
            append(storagePermissionGuide(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R))
        }
    showMessage("Permission check", details)
}

internal fun LauncherActivity.repairPermission(root: String?) {
    pendingPermissionRoot = root
    permissionRepairPicker.launch(root?.let(Uri::parse))
}
