package com.kireibox.ui

import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.settings.AccentChoice
import com.yuki.yukihub.settings.ImmersiveAmbient
import com.yuki.yukihub.settings.ImmersiveBackdropStyle
import com.yuki.yukihub.settings.ImmersiveCardSwitch
import com.yuki.yukihub.settings.ImmersiveFxPreset
import com.yuki.yukihub.settings.ThemeMode

// Settings search (r4 · S2): one entry per user-visible setting, data only. Each entry names the
// page it lives on, the path a result shows (「外观 › 显示」), the anchor its row registers with
// SettingsAnchor (the row title unless that changes at runtime) and synonyms people actually type:
// Chinese colloquialisms, English and abbreviations. SettingsSearchIndexContractTest keeps this list
// and the pages in step: every row title on a settings page is here (or excluded with a reason), and
// every anchor here is registered on its page.

/** When an entry's row exists on its page. */
internal enum class SettingsSearchScope {
    /** Always shown. */
    ALWAYS,

    /** Only while the home style is 沉浸式 (首页 › 展示内容 / 视觉效果). */
    IMMERSIVE,

    /** Immersive only, inside the collapsed 「高级选项」, which opens on arrival. */
    IMMERSIVE_ADVANCED,
}

/** Where an entry lives: its page, the path its result shows and when its row exists. */
internal data class SettingsSearchLocation(
    val page: KireiSettingsPage,
    val breadcrumb: String,
    val scope: SettingsSearchScope = SettingsSearchScope.ALWAYS,
)

/**
 * One searchable setting. [anchor] is the key its row registers with SettingsAnchor; null opens the
 * page itself (page entries). [subtitle], when set, replaces the path under the result.
 */
internal data class SettingsSearchEntry(
    val title: String,
    val location: SettingsSearchLocation,
    val keywords: List<String> = emptyList(),
    val anchor: String? = title,
    val subtitle: String? = null,
) {
    val page: KireiSettingsPage get() = location.page

    /** Stable, unique key of the result row. */
    val id: String get() = "${location.page.name}:${anchor ?: "page"}"

    /** The compact forms matching compares against, computed once per entry instead of per keystroke. */
    internal val forms: SettingsSearchForms by lazy { SettingsSearchForms(this) }
}

internal object SettingsSearchIndex {
    /** The 快速设置 entry opens the guide sheet on the root instead of a page row. */
    const val QUICK_SETUP_ANCHOR = "quick-setup"

    /** The 存储与权限 native-access row: its title depends on the API level, so it has a fixed key. */
    const val NATIVE_ACCESS_ANCHOR = "native-access"

    /** 首页样式: where immersive-only results lead while the home uses 组件模式. */
    const val HOME_STYLE_ANCHOR = "Home page style"

    const val SEPARATOR = " › "

    /** Shown when nothing matches: one word per area, each known to find something. */
    val suggestions = listOf("Dark", "Accent color", "Cover", "KRKR", "Log")

    val entries: List<SettingsSearchEntry> by lazy {
        pageEntries() + appearanceEntries() + homeEntries() + effectEntries() + libraryEntries() + coverEntries() + engineEntries() + systemEntries() + aboutEntries()
    }

    private val appearance = settingsPageTitle(KireiSettingsPage.APPEARANCE)
    private val home = settingsPageTitle(KireiSettingsPage.INTERFACE)
    private val library = settingsPageTitle(KireiSettingsPage.LIBRARY_HUB)
    private val scanRoots = settingsPageTitle(KireiSettingsPage.LIBRARY)
    private val covers = settingsPageTitle(KireiSettingsPage.COVERS)
    private val engines = settingsPageTitle(KireiSettingsPage.ENGINES)
    private val krkr = settingsPageTitle(KireiSettingsPage.KRKR)
    private val ons = settingsPageTitle(KireiSettingsPage.ONS)
    private val artemis = settingsPageTitle(KireiSettingsPage.ARTEMIS)
    private val tyrano = settingsPageTitle(KireiSettingsPage.TYRANO)
    private val winlator = settingsPageTitle(KireiSettingsPage.WINLATOR)
    private val permissions = settingsPageTitle(KireiSettingsPage.PERMISSIONS)
    private val diagnostics = settingsPageTitle(KireiSettingsPage.DIAGNOSTICS)
    private val about = settingsPageTitle(KireiSettingsPage.ABOUT)
    private val root = settingsPageTitle(KireiSettingsPage.ROOT)

    private fun at(
        page: KireiSettingsPage,
        vararg path: String,
        scope: SettingsSearchScope = SettingsSearchScope.ALWAYS,
    ): SettingsSearchLocation = SettingsSearchLocation(page, path.joinToString(SEPARATOR), scope)

    private fun words(vararg words: String): List<String> = words.toList()

    /** Every page, opened as a whole; the path names the root group the page sits in. */
    private fun pageEntries(): List<SettingsSearchEntry> =
        listOf(
            SettingsSearchEntry(
                QUICK_SETUP_TITLE,
                at(KireiSettingsPage.ROOT, root),
                words("Guide", "Wizard", "Newbie", "Initial setup", "One-click settings", "Getting Started", "setup", "wizard", "guide", "onboarding"),
                anchor = QUICK_SETUP_ANCHOR,
            ),
            page(KireiSettingsPage.APPEARANCE, "Personalization", words("Interface", "Style", "Skin", "Appearance settings", "appearance", "ui", "look")),
            page(KireiSettingsPage.INTERFACE, "Personalization", words("Home", "Desktop", "Home and immersive", "Immersive homepage", "home", "homepage")),
            page(KireiSettingsPage.LIBRARY_HUB, "Games", words("Games", "Library", "library", "games")),
            SettingsSearchEntry(
                scanRoots,
                at(KireiSettingsPage.LIBRARY, library, "Table of contents"),
                words("Table of contents", "Folder", "Path", "Import", "folder", "directory", "scan", "path"),
                anchor = null,
            ),
            page(KireiSettingsPage.COVERS, "Games", words("Metadata", "Material", "Poster", "Image", "cover", "covers", "metadata", "artwork", "poster", "vndb", "bangumi", "bgm")),
            page(
                KireiSettingsPage.COVER_ACCOUNTS,
                "Games",
                CoverAccountSources.joinToString("、") { it.label },
                words("Account", "Account", "Log in", "Exit", "Token", "Cover", "Source", "token", "account", "login", "cover", "covers", "source", "nextmoe", "hikarinagi", "bangumi"),
            ),
            page(KireiSettingsPage.PLAYTIME_SYNC, "Games", words("Sync", "Duration", "Play time", "Upload", "nextmoe", "sync", "playtime")),
            page(KireiSettingsPage.ENGINES, "Games", words("Engine", "Emulator", "Overall situation", "default", "engine", "engines", "emulator", "global")),
            // Per-game renderer / FPS / font settings live on a game's own KRKR page (opened from its
            // sheet); this page's footer says where, so those words lead here.
            page(
                KireiSettingsPage.KRKR,
                "Games",
                words("krkr2", "kirikiri", "kirikiroid", "kirikiroid2", "Giri giri", "Engine", "Emulator", "engine", "emulator", "Rendering", "fps", "Frame rate", "Font", "Font size", "Enlarge", "Cursor"),
            ),
            page(KireiSettingsPage.ONS, "Games", words("onscripter", "onsyuri", "ons2", "Engine", "Emulator", "engine", "emulator", "Coding", "Sharpen", "Bangs")),
            page(KireiSettingsPage.ARTEMIS, "Games", words("Engine", "Emulator", "engine", "emulator", "Save data")),
            page(KireiSettingsPage.TYRANO, "Games", words("tyranor", "tyrano script", "Engine", "Emulator", "engine", "emulator", "Save data")),
            page(KireiSettingsPage.WINLATOR, "Games", words("winlator", "winlatorcn", "winlator-cn", "windows", "exe", "pc", "Computer", "Engine", "Emulator", "engine", "emulator")),
            page(KireiSettingsPage.PERMISSIONS, "System", words("Permissions", "Storage", "Authorize", "Access", "All files", "saf", "permission", "permissions", "storage", "access")),
            page(KireiSettingsPage.DIAGNOSTICS, "System", words("Log", "Diagnosis", "Collapse", "Crash", "Report an error", "Feedback", "bug", "log", "logs", "debug", "crash", "diagnostics")),
            SettingsSearchEntry(
                "About KireiBox",
                at(KireiSettingsPage.ABOUT, root, about),
                words(about, "Version", "Open source", "kireibox", "about", "version"),
                anchor = null,
            ),
        )

    private fun page(
        page: KireiSettingsPage,
        group: String,
        keywords: List<String>,
    ): SettingsSearchEntry = SettingsSearchEntry(settingsPageTitle(page), at(page, root, group), keywords, anchor = null)

    private fun page(
        page: KireiSettingsPage,
        group: String,
        subtitle: String,
        keywords: List<String>,
    ): SettingsSearchEntry = SettingsSearchEntry(settingsPageTitle(page), at(page, root, group), keywords, anchor = null, subtitle = subtitle)

    private fun appearanceEntries(): List<SettingsSearchEntry> {
        val display = at(KireiSettingsPage.APPEARANCE, appearance, "Show")
        return listOf(
            SettingsSearchEntry(
                "Theme",
                at(KireiSettingsPage.APPEARANCE, appearance, "Theme"),
                ThemeMode.entries.map { it.displayName } +
                    words("Dark mode", "At night", "Night mode", "Dark color", "Dark mode", "Dark mode", "Black", "Bright color", "Daytime", "Daytime", "Theme mode", "dark", "light", "night", "darkmode", "theme"),
            ),
            SettingsSearchEntry(
                "Accent color",
                at(KireiSettingsPage.APPEARANCE, appearance, "Accent color"),
                AccentChoice.entries.map { it.displayName } + words("Color", "Color matching", "Theme color", "Color", "Pink", "Blue", "Orange color", "Green", "accent", "color", "colour"),
            ),
            SettingsSearchEntry("Animation effects", display, words("Animation", "Animation", "Transition", "Reduce dynamics", "Streamline", "Save power", "Stuck and stopped", "animation", "animations", "motion")),
            SettingsSearchEntry("Screen orientation", display, words("Rotate", "Auto-rotate", "Horizontal screen", "Vertical screen", "Direction", "Lock direction", "rotation", "rotate", "orientation", "landscape", "portrait")),
            SettingsSearchEntry("Reduce transparency", display, words("Vague", "Frosted glass", "Transparent", "Glass", "Translucent", "Solid color background", "blur", "glass", "frosted", "transparency")),
            SettingsSearchEntry("Enhance contrast", display, words("Contrast", "High contrast", "Can't see clearly", "Text is too light", "Readability", "Accessibility", "contrast", "accessibility", "a11y")),
        )
    }

    private fun homeEntries(): List<SettingsSearchEntry> {
        val content = at(KireiSettingsPage.INTERFACE, home, "Show content", scope = SettingsSearchScope.IMMERSIVE)
        val effects = at(KireiSettingsPage.INTERFACE, home, "Visual effects", scope = SettingsSearchScope.IMMERSIVE)
        return listOf(
            SettingsSearchEntry(
                HOME_STYLE_ANCHOR,
                at(KireiSettingsPage.INTERFACE, home),
                words("Immersive", "Component pattern", "Components", "Widget", "Dashboard", "Layout", "Gallery", "immersive", "widget", "widgets", "dashboard", "layout"),
            ),
            SettingsSearchEntry(
                "Shown games",
                content,
                words("Show content", "Carousel", "Display diagram", "Edit an immersive presentation", "Recently opened", "collect", "Random", "Manual selection", "carousel", "gallery", "showcase"),
            ),
            SettingsSearchEntry("Show play time", content, words("Play time", "Duration", "Game time", "Play time", "playtime", "play time")),
            SettingsSearchEntry(
                "Visual effects",
                effects,
                ImmersiveFxPreset.entries.map { it.displayName } + words("Special effects", "Effect", "Default", "Immersive effect", "effects", "fx", "preset"),
            ),
            SettingsSearchEntry(
                "Switch mode",
                at(KireiSettingsPage.INTERFACE, home, "Visual effects"),
                ImmersiveCardSwitch.entries.map { it.displayName } + words("Switch animation", "Card switching", "Slide", "Transition", "Shuffle", "Card animation", "transition", "shuffle"),
            ),
            SettingsSearchEntry(
                "Advanced options",
                at(KireiSettingsPage.INTERFACE, home, "Visual effects", scope = SettingsSearchScope.IMMERSIVE_ADVANCED),
                words("Details", "Custom effects", "Advanced", "advanced"),
            ),
        )
    }

    /** 首页 › 高级选项: every detail of the immersive effects. */
    private fun effectEntries(): List<SettingsSearchEntry> {
        val advanced = SettingsSearchScope.IMMERSIVE_ADVANCED
        val backdrop = at(KireiSettingsPage.INTERFACE, home, "Advanced options", "Background", scope = advanced)
        val card = at(KireiSettingsPage.INTERFACE, home, "Advanced options", "Card", scope = advanced)
        val ambient = at(KireiSettingsPage.INTERFACE, home, "Advanced options", "Atmosphere", scope = advanced)
        return listOf(
            SettingsSearchEntry(
                "Background style",
                backdrop,
                ImmersiveBackdropStyle.entries.map { it.displayName } +
                    words("Background", "Wallpaper", "Blur", "Blurred background", "Cover background", "Enlarge cover", "Aurora", "Streamer", "Pure black", "backdrop", "background", "wallpaper", "aurora", "blur", "cover"),
            ),
            SettingsSearchEntry("Background intensity", backdrop, words("Strength", "Shade", "Mask", "Background brightness", "intensity")),
            SettingsSearchEntry("Cover slowly pushed and pulled", backdrop, words("Push and pull", "Zoom", "Pan", "Ken Burns", "kenburns", "ken burns", "zoom", "cover")),
            SettingsSearchEntry("Film grain", backdrop, words("Particles", "Noise", "Film", "Texture", "grain", "film", "noise")),
            SettingsSearchEntry("Vignetting", backdrop, words("Vignette", "Darkened corners", "vignette")),
            SettingsSearchEntry("3D depth", card, words("Three-dimensional", "Depth", "Perspective", "Tilt", "3d", "depth", "tilt", "perspective")),
            SettingsSearchEntry("Center card halo", card, words("Halo", "Glow", "Glow", "glow")),
            SettingsSearchEntry("Rim light", card, words("Stroke", "Rim light", "Frame", "rim", "rim light")),
            SettingsSearchEntry("Highlight sweep", card, words("Highlights", "Sweep", "Flash", "Reflective", "sheen", "shine")),
            SettingsSearchEntry("Dynamic color selection", card, words("Pick color", "Color", "Cover color", "Follow cover", "Adaptive color", "dynamic color", "tint", "cover color")),
            SettingsSearchEntry(
                "Atmospheric particles",
                ambient,
                ImmersiveAmbient.entries.filter { it != ImmersiveAmbient.NONE }.map { it.displayName } + words("Particle", "Snow", "Snowflake", "Falling", "particles", "snow", "sakura", "dust"),
            ),
            SettingsSearchEntry("Gyroscope Parallax", ambient, words("Gyroscope", "Parallax", "Gravity sensing", "Somatosensory", "Tilt", "gyro", "gyroscope", "parallax")),
            SettingsSearchEntry("Restore default effect", at(KireiSettingsPage.INTERFACE, home, "Advanced options", scope = advanced), words("Reset", "Restore default", "Default effect", "reset", "default")),
        )
    }

    private fun libraryEntries(): List<SettingsSearchEntry> {
        val updates = at(KireiSettingsPage.LIBRARY, library, scanRoots, "Automatic updates")
        return listOf(
            SettingsSearchEntry(
                "Game library order",
                at(KireiSettingsPage.LIBRARY_HUB, library, "Game cards"),
                words("Sort", "Order", "Recently visited", "Add time", "Name", "Custom sorting", "Manual sorting", "sort", "order"),
            ),
            SettingsSearchEntry(
                "Add scan directory",
                at(KireiSettingsPage.LIBRARY, library, scanRoots, "Added directory"),
                words("Add directory", "Add folder", "Import game", "add game", "add folder", "import"),
            ),
            SettingsSearchEntry("Catalog automatic update", updates, words("Auto scan", "Refresh", "Automatically added", "Scan on startup", "auto scan", "refresh")),
            SettingsSearchEntry("The removed game update directory will not be added again", updates, words("hide", "Don't add anymore", "Shield", "hidden")),
            SettingsSearchEntry("Removed games", updates, words("Recover", "Remove", "Recycle bin", "Deleted", "Retrieve", "restore", "removed")),
            SettingsSearchEntry(
                "Use the built-in file browser",
                at(KireiSettingsPage.LIBRARY, library, scanRoots, "File selection"),
                words("File browser", "File selector", "System file selector", "file picker", "file browser"),
            ),
        )
    }

    private fun coverEntries(): List<SettingsSearchEntry> {
        val sources = at(KireiSettingsPage.COVERS, covers, "Source and match")
        val matching = at(KireiSettingsPage.COVERS, covers, "Automatic matching")
        val adult = at(KireiSettingsPage.COVERS, covers, "Adult content")
        val accounts = at(KireiSettingsPage.COVER_ACCOUNTS, settingsPageTitle(KireiSettingsPage.COVER_ACCOUNTS))
        return listOf(
            SettingsSearchEntry("Default source", sources, CoverSourceMode.active.map { it.label } + words("Source", "Data source", "Source", "bgm", "source")),
            SettingsSearchEntry("Bangumi mirror address", sources, words("Mirror", "Acting", "Address", "Network", "api", "bgm", "mirror", "proxy")),
            SettingsSearchEntry("Find multiple sources at the same time", matching, words("Multiple sources", "Mix and match", "Automatic matching", "hybrid")),
            SettingsSearchEntry("Wi-Fi only auto-match", matching, words("Flow", "Mobile data", "Save traffic", "Wireless", "Network", "wlan")),
            SettingsSearchEntry(COVER_STRICT_MATCH_TITLE, matching, words("Strict match", "Title match", "Exact match", "Automatically set cover", "strict", "cover match")),
            SettingsSearchEntry("Clear records with no results", matching, words("Clear", "No result", "Rematch", "Retry", "Cache", "clear", "retry")),
            SettingsSearchEntry("Blurred adult content cover", adult, words("Aldult", "r18", "nsfw", "18 banned", "Occlusion", "Privacy", "blur", "cover")),
            SettingsSearchEntry("Search results include adult works", adult, words("Aldult", "r18", "nsfw", "18 banned", "adult")),
            SettingsSearchEntry("Cover to be confirmed", at(KireiSettingsPage.COVERS, covers), words("To be confirmed", "To be selected", "Match failed", "Select cover", "Pick a cover", "review", "pending", "cover")),
            SettingsSearchEntry("Cover set", at(KireiSettingsPage.COVERS, covers), words("Already set", "Change cover", "Reselect cover", "Modify cover", "change cover")),
        ) +
            CoverAccountSources.map { source ->
                val token = if (source == CoverSourceMode.BANGUMI) words("Token", "token", "access token") else words("Password")
                SettingsSearchEntry(source.label, accounts, words("Account", "Log in", "Exit", "account", "login") + token)
            }
    }

    private fun engineEntries(): List<SettingsSearchEntry> {
        val krkrDefaults = at(KireiSettingsPage.KRKR, engines, krkr, "Global default")
        val onsDisplay = at(KireiSettingsPage.ONS, engines, ons, "Show")
        val onsCompat = at(KireiSettingsPage.ONS, engines, ons, "compatible")
        val artemisDisplay = at(KireiSettingsPage.ARTEMIS, engines, artemis, "Show")
        val tyranoDisplay = at(KireiSettingsPage.TYRANO, engines, tyrano, "Show")
        val orientationWords = words("Direction", "Horizontal screen", "Left horizontal screen", "Right horizontal screen", "Rotate", "orientation", "landscape")
        val keepAwakeWords = words("Always on", "Breath screen", "Turn off the screen", "Hibernate", "keep awake", "screen on")
        val cutoutWords = words("Bangs", "Dig a hole", "Camera", "Special-shaped screen", "cutout", "notch")
        val saveWords = words("Save data", "Archive directory", "Archive location", "Save", "Data directory", "save", "savedata", "scoped")
        return listOf(
            SettingsSearchEntry("Start KRKR native engine", at(KireiSettingsPage.KRKR, engines, krkr, "start up"), words("start up", "Run", "Open the game", "Game files", "xp3", "launch", "run")),
            SettingsSearchEntry("Engine version", krkrDefaults, KrkrEngineVersions.filter { it != "auto" } + words("Version", "engine version")),
            SettingsSearchEntry("Compatibility mode", krkrDefaults, words("compatible", "Huaping", "Black screen", "Crash", "compat", "compatibility")),
            SettingsSearchEntry("Skip the opening information pop-up window", krkrDefaults, words("Pop-up window", "Jump over", "Prompt box", "Dialog box", "dialog", "popup", "skip")),
            SettingsSearchEntry("Game screen orientation", krkrDefaults, orientationWords),
            SettingsSearchEntry("Game screen orientation", onsDisplay, orientationWords),
            SettingsSearchEntry("Game screen orientation", artemisDisplay, orientationWords),
            SettingsSearchEntry("Game screen orientation", tyranoDisplay, orientationWords),
            SettingsSearchEntry("Keep the screen on", onsDisplay, keepAwakeWords),
            SettingsSearchEntry("Keep the screen on", tyranoDisplay, keepAwakeWords),
            SettingsSearchEntry("Ignore bangs", tyranoDisplay, cutoutWords),
            SettingsSearchEntry("Stretch full screen", onsDisplay, words("Fullscreen", "Stretch", "Covered", "Aspect ratio", "stretch", "fullscreen")),
            SettingsSearchEntry("Ignore bangs", onsDisplay, words("Bangs", "Dig a hole", "Camera", "Special-shaped screen", "cutout", "notch")),
            SettingsSearchEntry("Picture sharpening", onsDisplay, words("Sharpen", "Clear", "Vague", "sharp", "sharpness")),
            SettingsSearchEntry("Sharpening strength", onsDisplay, words("Sharpen", "Clarity", "sharpness", "strength")),
            SettingsSearchEntry("Text encoding", onsCompat, words("Coding", "Garbled characters", "gbk", "utf8", "utf-8", "sjis", "shift-jis", "encoding")),
            SettingsSearchEntry("Disable video", onsCompat, words("Video", "Cutscene", "Black screen", "Stuck", "video", "movie", "disable")),
            SettingsSearchEntry("Independent archive directory", at(KireiSettingsPage.ONS, engines, ons, "Save data"), saveWords),
            SettingsSearchEntry("Independent archive directory", at(KireiSettingsPage.ARTEMIS, engines, artemis, "Save data"), saveWords),
            SettingsSearchEntry("Independent archive directory", at(KireiSettingsPage.TYRANO, engines, tyrano, "Save data"), saveWords),
        ) + winlatorEntries()
    }

    private fun winlatorEntries(): List<SettingsSearchEntry> {
        val container = at(KireiSettingsPage.WINLATOR, engines, winlator, "Container")
        val graphics = at(KireiSettingsPage.WINLATOR, engines, winlator, "Graphics")
        val locale = at(KireiSettingsPage.WINLATOR, engines, winlator, "Language and time zone")
        val advanced = at(KireiSettingsPage.WINLATOR, engines, winlator, "Advanced")
        return listOf(
            SettingsSearchEntry("Open Winlator", at(KireiSettingsPage.WINLATOR, engines, winlator), words("start up", "Install", "Not installed", "Version", "open", "install")),
            SettingsSearchEntry("Creating a container tutorial", at(KireiSettingsPage.WINLATOR, engines, winlator), words("Tutorial", "Guide", "First", "Newbie", "Container", "Create", "Unable to start", "guide", "tutorial", "container")),
            SettingsSearchEntry("Container name", container, words("Container", "Name", "container", "name")),
            SettingsSearchEntry("Container ID", container, words("Container", "Serial number", "id", "container")),
            SettingsSearchEntry("Graphics driver", graphics, words("Drive", "turnip", "vortek", "zink", "virgl", "gladio", "vulkan", "opengl", "gpu", "Black screen", "driver")),
            SettingsSearchEntry("Graphics acceleration", graphics, words("dxvk", "wined3d", "directx", "d3d", "Graphics card", "Convert")),
            SettingsSearchEntry("Resolution", graphics, words("Picture", "Screen", "Size", "resolution", "screen", "size")),
            SettingsSearchEntry("Locale", locale, words("Japanese", "Japanese", "Garbled characters", "Chinese", "lc_all", "locale", "language")),
            SettingsSearchEntry("Time zone", locale, words("Time", "Tokyo", "tz", "timezone")),
            SettingsSearchEntry("Box64 default", advanced, words("box64", "Performance", "Stablize", "compatible", "cpu", "preset")),
            SettingsSearchEntry("Environment variables", advanced, words("Variable", "wine", "winedlloverrides", "env", "environment")),
            SettingsSearchEntry("Write container configuration", advanced, words("Save", "Lasting", "Confirm", "Pop-up window", "save", "persist")),
        )
    }

    private fun systemEntries(): List<SettingsSearchEntry> =
        listOf(
            SettingsSearchEntry(
                StorageAccessPolicy.nativeAccessTitle(),
                at(KireiSettingsPage.PERMISSIONS, permissions, "KRKR native engine"),
                words("All file access", "Storage permissions", "File permissions", "Authorize", "manage storage", "all files access", "permission"),
                anchor = NATIVE_ACCESS_ANCHOR,
            ),
            SettingsSearchEntry("Check access rights", at(KireiSettingsPage.PERMISSIONS, permissions), words("Check permissions", "Permissions report", "Unable to read", "Read and write", "check permission")),
            SettingsSearchEntry("Record diagnostic log", at(KireiSettingsPage.DIAGNOSTICS, diagnostics, "Record"), words("Log", "Debug", "Detailed log", "debug", "log", "verbose")),
            SettingsSearchEntry("Export log", at(KireiSettingsPage.DIAGNOSTICS, diagnostics, "Share"), words("Share log", "Save log", "Send log", "Feedback", "export", "share")),
            SettingsSearchEntry("Copy log", at(KireiSettingsPage.DIAGNOSTICS, diagnostics, "Share"), words("Copy", "Clipboard", "copy", "clipboard")),
        )

    private fun aboutEntries(): List<SettingsSearchEntry> {
        val community = at(KireiSettingsPage.ABOUT, about, "Community and Licensing")
        return listOf(
            SettingsSearchEntry("Check for updates", at(KireiSettingsPage.ABOUT, about, "Version"), words("Update", "Upgrade", "New version", "Version", "Download", "update", "upgrade", "version")),
            SettingsSearchEntry("GitHub", community, words("Source code", "Open source", "Storehouse", "Code", "source", "repo")),
            SettingsSearchEntry("Third party statement", community, words("License", "Open source license", "Protocol", "Copyright", "license", "licenses", "notices")),
        )
    }
}
