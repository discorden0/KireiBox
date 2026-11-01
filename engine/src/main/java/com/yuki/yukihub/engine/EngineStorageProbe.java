package com.yuki.yukihub.engine;

import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;

/**
 * Storage write probe executed inside each dedicated engine process.
 *
 * HarmonyOS 2.0 / Huawei devices have a known defect: the storage grant looks
 * correct in the main process, but a dedicated engine process (e.g. :kirikiri2,
 * :ons, :tyrano, :artemis) can still receive EACCES when writing the game's
 * savedata. Engine activities therefore probe the real save directory before
 * starting native code, so the failure is shown as an actionable page instead
 * of a native crash or an endless grant dialog.
 */
public final class EngineStorageProbe {
    private static final String TAG = "EngineStorageProbe";

    private EngineStorageProbe() {
    }

    public static final class Result {
        public final boolean ok;
        public final String reason;
        public final String detail;

        Result(boolean ok, String reason, String detail) {
            this.ok = ok;
            this.reason = reason;
            this.detail = detail;
        }

        public String userMessage() {
            return reason + "\n\n" + hint();
        }

        public String logMessage() {
            return reason + " (" + detail + ")";
        }
    }

    /** Probes the given directory with a real write/rename/delete cycle. */
    public static Result probeDirectory(File directory) {
        if (directory == null) {
            return new Result(false, "Archive directory is empty", "null save directory");
        }
        File pending = null;
        File committed = null;
        try {
            if (!directory.exists() && !directory.mkdirs()) {
                return new Result(false, "Unable to create archive directory:" + directory.getAbsolutePath(), "mkdirs failed");
            }
            if (!directory.isDirectory()) {
                return new Result(false, "Archive path is not a directory:" + directory.getAbsolutePath(), "not a directory");
            }
            String suffix = android.os.Process.myPid() + "-" + System.nanoTime();
            pending = new File(directory, ".kireibox-write-test-" + suffix + ".tmp");
            committed = new File(directory, ".kireibox-write-test-" + suffix + ".ok");
            try (FileOutputStream output = new FileOutputStream(pending)) {
                output.write(new byte[]{0x4B, 0x42});
                output.flush();
                output.getFD().sync();
            }
            if (!pending.renameTo(committed)) {
                return new Result(false, "The archive directory cannot complete write detection:" + directory.getAbsolutePath(), "rename failed");
            }
            if (!committed.delete()) {
                return new Result(false, "Archive directory cannot delete write detection files:" + directory.getAbsolutePath(), "delete failed");
            }
            return new Result(true, "", "");
        } catch (Throwable error) {
            String detail = error.getMessage() == null
                    ? error.getClass().getSimpleName()
                    : error.getMessage();
            Log.e(TAG, "probe failed dir=" + directory.getAbsolutePath() + " error=" + detail);
            return new Result(false, "The archive directory is not writable:" + directory.getAbsolutePath(), detail);
        } finally {
            if (pending != null && pending.exists()) {
                //noinspection ResultOfMethodCallIgnored
                pending.delete();
            }
            if (committed != null && committed.exists()) {
                //noinspection ResultOfMethodCallIgnored
                committed.delete();
            }
        }
    }

    /** KRKR keeps saves in <game>/savedata. */
    public static Result probeKrkrGameSaveDirectory(String gamePath) {
        if (gamePath == null || gamePath.trim().isEmpty()) {
            return new Result(false, "Game directory is empty", "empty game path");
        }
        String path = gamePath;
        if (path.startsWith("file://")) path = path.substring("file://".length());
        File root = new File(path);
        if (root.isFile()) root = root.getParentFile();
        if (root == null) {
            return new Result(false, "Unable to parse game directory", "null game root");
        }
        return probeDirectory(new File(root, "savedata"));
    }

    public static boolean isHuaweiFamily() {
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        String brand = Build.BRAND == null ? "" : Build.BRAND.toLowerCase(Locale.ROOT);
        String model = Build.MODEL == null ? "" : Build.MODEL.toLowerCase(Locale.ROOT);
        return manufacturer.contains("huawei") || manufacturer.contains("honor")
                || brand.contains("huawei") || brand.contains("honor")
                || model.contains("huawei");
    }

    /** User-facing permission guide adapted to HarmonyOS/Huawei and Android version. */
    public static String hint() {
        boolean android11Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
        if (isHuaweiFamily()) {
            return "Huawei/HarmonyOS Tip: Please enable the \"Storage\" permission in \"Settings → Application → KireiBox → Permission Management → Storage\" and return"
                    + (android11Plus
                    ? "; HarmonyOS 3 and above also need to turn on \"All File Access\"."
                    : "。")
                    + "It has been opened but still cannot be written. Please move the game directory to a regular location such as Download and try again.";
        }
        if (android11Plus) {
            return "Please grant KireiBox \"All file access\": Settings → Apps → KireiBox → Permissions → All file access → Allow;"
                    + "Return and try again after authorization. If it still cannot be written, please move the game directory to a regular location on the internal storage.";
        }
        return "Please grant \"Storage\" permission to KireiBox: Settings → Applications → KireiBox → Permissions → Storage → Allow;"
                + "It has been opened but still cannot be written. Please move the game directory to a regular location such as Download and try again.";
    }
}