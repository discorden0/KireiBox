package com.kireibox.external.shortcut

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import com.yuki.yukihub.BuildConfig
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ShizukuShortcutGateway(context: Context) {
    private val app = context.applicationContext
    private val bindLock = Any()

    @Volatile private var activeConnection: ServiceConnection? = null

    @Volatile private var activeService: IShortcutShellService? = null

    @Volatile private var binding = false

    @Volatile private var connectionLost = false

    fun status(): ShortcutServiceStatus {
        val installed = isManagerInstalled()
        val running = installed && runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        return ShortcutServiceStatusPolicy.resolve(
            installed = installed,
            running = running,
            supportedVersion =
                running &&
                    runCatching {
                        !Shizuku.isPreV11() && Shizuku.getVersion() >= MIN_SHIZUKU_API
                    }.getOrDefault(false),
            permissionGranted =
                running &&
                    runCatching {
                        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                    }.getOrDefault(false),
            rationale = running && runCatching { Shizuku.shouldShowRequestPermissionRationale() }.getOrDefault(false),
            uid = if (running) runCatching { Shizuku.getUid() }.getOrDefault(-1) else -1,
            binding = binding,
            connectionLost = connectionLost,
        )
    }

    fun requestPermission(requestCode: Int = PERMISSION_REQUEST_CODE): Result<Unit> =
        runCatching {
            when (status()) {
                ShortcutServiceStatus.READY -> Unit
                ShortcutServiceStatus.PERMISSION_REQUIRED -> Shizuku.requestPermission(requestCode)
                ShortcutServiceStatus.PERMISSION_DENIED -> error("KireiBox has been denied by Shizuku, please re-allow it in Shizuku's authorized application.")
                ShortcutServiceStatus.NOT_INSTALLED -> error("Please install Shizuku first.")
                ShortcutServiceStatus.NOT_RUNNING -> error("Please complete wireless debugging pairing and start the service in Shizuku first.")
                ShortcutServiceStatus.UNSUPPORTED_VERSION -> error("Requires Shizuku 13.1.5 or newer.")
                ShortcutServiceStatus.UNSUPPORTED_IDENTITY -> error("The shortcut function only supports Shizuku enabled by wireless debugging (shell mode).")
                else -> error("Shizuku shortcut service is temporarily unavailable.")
            }
        }

    fun bind(): Result<Unit> =
        runCatching {
            ensureReady()
            check(service().canReadPinnedShortcuts(currentUserId())) {
                "The current system does not grant the shell permission to read desktop shortcuts."
            }
        }

    fun scan(userId: Int = currentUserId()): Result<List<ExternalShortcutDescriptor>> =
        withRemote { remote ->
            if (!remote.canReadPinnedShortcuts(userId)) {
                error("The current system does not grant the shell permission to read desktop shortcuts.")
            }
            val values =
                remote.scan(userId).mapNotNull { bundle ->
                    val packageName = bundle.getString(ShortcutShellUserService.KEY_PACKAGE)?.trim().orEmpty()
                    val shortcutId = bundle.getString(ShortcutShellUserService.KEY_ID).orEmpty()
                    if (packageName.isEmpty() || shortcutId.isEmpty()) return@mapNotNull null
                    ExternalShortcutDescriptor(
                        userId = bundle.getInt(ShortcutShellUserService.KEY_USER_ID, userId),
                        packageName = packageName,
                        appName =
                            bundle.getString(ShortcutShellUserService.KEY_APP_NAME)?.trim().orEmpty()
                                .ifEmpty { packageName },
                        shortcutId = shortcutId,
                        title =
                            bundle.getString(ShortcutShellUserService.KEY_TITLE)?.trim().orEmpty()
                                .ifEmpty { shortcutId },
                        activityName =
                            bundle.getString(ShortcutShellUserService.KEY_ACTIVITY)?.trim()
                                ?.takeIf(String::isNotEmpty),
                        pinned = bundle.getBoolean(ShortcutShellUserService.KEY_PINNED),
                        dynamic = bundle.getBoolean(ShortcutShellUserService.KEY_DYNAMIC),
                        directLaunchTarget =
                            bundle.getString(ShortcutShellUserService.KEY_DIRECT_LAUNCH_TARGET)
                                ?.trim()?.takeIf(String::isNotEmpty),
                    )
                }
            ExternalShortcutPolicy.descriptors(values, app.packageName)
        }

    fun exists(identity: ExternalShortcutIdentity.Parsed): Boolean =
        withRemote { remote ->
            remote.canReadPinnedShortcuts(identity.userId) &&
                remote.exists(identity.userId, identity.packageName, identity.shortcutId)
        }.getOrDefault(false)

    fun launch(identity: ExternalShortcutIdentity.Parsed): Result<Unit> =
        withRemote { remote ->
            if (!remote.canReadPinnedShortcuts(identity.userId)) {
                error("The current system does not grant the shell permission to launch desktop shortcuts.")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val launchIntent =
                    remote.launchIntent(
                        identity.userId,
                        identity.packageName,
                        identity.shortcutId,
                    ) ?: error("The shortcut is invalid, please scan again.")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val options =
                        ActivityOptions.makeBasic().apply {
                            pendingIntentBackgroundActivityStartMode =
                                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        }.toBundle()
                    launchIntent.send(app, 0, null, null, null, null, options)
                } else {
                    launchIntent.send()
                }
            } else {
                check(remote.launch(identity.userId, identity.packageName, identity.shortcutId)) {
                    "The shortcut is invalid, please scan again."
                }
            }
        }.recoverCatching { error ->
            if (error is PendingIntent.CanceledException) {
                throw IllegalStateException("The shortcut is invalid, please scan again.", error)
            }
            throw error
        }

    fun release() {
        val connection =
            synchronized(bindLock) {
                val value = activeConnection
                activeConnection = null
                activeService = null
                connectionLost = false
                binding = false
                value
            } ?: return
        runCatching { Shizuku.unbindUserService(SERVICE_ARGS, connection, true) }
    }

    private fun <T> withRemote(block: (IShortcutShellService) -> T): Result<T> =
        runCatching {
            ensureReady()
            block(service())
        }.also { release() }

    private fun service(): IShortcutShellService {
        activeService?.let { if (it.asBinder().pingBinder()) return it }
        synchronized(bindLock) {
            activeService?.let { if (it.asBinder().pingBinder()) return it }
            val latch = CountDownLatch(1)
            var failure: Throwable? = null
            binding = true
            connectionLost = false
            val connection =
                object : ServiceConnection {
                    override fun onServiceConnected(
                        name: ComponentName?,
                        binder: IBinder?
                    ) {
                        activeService = binder?.let(IShortcutShellService.Stub::asInterface)
                        binding = false
                        latch.countDown()
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        activeService = null
                        binding = false
                        connectionLost = true
                        failure = IllegalStateException("Shizuku shortcut connection has been disconnected.")
                        latch.countDown()
                    }
                }
            activeConnection = connection
            Shizuku.bindUserService(SERVICE_ARGS, connection)
            if (!latch.await(BIND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                binding = false
                release()
                error("Connection to Shizuku timed out.")
            }
            failure?.let { throw it }
            return activeService ?: error("Shizuku did not return the shortcut service.")
        }
    }

    private fun ensureReady() {
        when (status()) {
            ShortcutServiceStatus.READY -> Unit
            ShortcutServiceStatus.NOT_INSTALLED -> error("Please install Shizuku first.")
            ShortcutServiceStatus.NOT_RUNNING -> error("Shizuku is not running, please complete wireless debugging pairing first.")
            ShortcutServiceStatus.PERMISSION_REQUIRED -> error("You need to authorize KireiBox to use Shizuku first.")
            ShortcutServiceStatus.PERMISSION_DENIED -> error("Shizuku Permission denied.")
            ShortcutServiceStatus.UNSUPPORTED_VERSION -> error("Requires Shizuku 13.1.5 or newer.")
            ShortcutServiceStatus.UNSUPPORTED_IDENTITY -> error("Only Shizuku shell mode is supported, Root/Sui mode is not supported.")
            ShortcutServiceStatus.SYSTEM_PERMISSION_MISSING -> error("The system does not open shortcut permissions to the shell.")
            ShortcutServiceStatus.BINDING -> error("Connecting to Shizuku.")
            ShortcutServiceStatus.CONNECTION_LOST -> error("Shizuku connection has expired.")
        }
    }

    private fun isManagerInstalled(): Boolean =
        runCatching {
            app.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        }.isSuccess

    private fun currentUserId(): Int = android.os.UserHandle.getUserHandleForUid(Process.myUid()).hashCode()

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val PERMISSION_REQUEST_CODE = 0x4b53
        private const val MIN_SHIZUKU_API = 13
        private const val BIND_TIMEOUT_SECONDS = 10L
        private val SERVICE_ARGS =
            Shizuku.UserServiceArgs(
                ComponentName(BuildConfig.APPLICATION_ID, ShortcutShellUserService::class.java.name),
            )
                .daemon(false)
                .processNameSuffix("shortcut_shell")
                .tag("kireibox-shortcut-shell-v1")
                .version(BuildConfig.VERSION_CODE)
                .debuggable(BuildConfig.DEBUG)
    }
}
