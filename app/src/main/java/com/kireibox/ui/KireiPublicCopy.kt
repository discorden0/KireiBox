package com.kireibox.ui

import com.yuki.yukihub.metadata.model.CoverSourceMode

/** User-facing project and cover-source copy kept separate from developer diagnostics. */
internal object KireiPublicCopy {
    /**
     * The project blurb (KireiPublicCopyTest pins it as user-facing copy). Since P3 the 关于 page
     * shows this information as rows (GitHub, 第三方声明), so no dialog prints it any more.
     */
    const val ABOUT_TEXT: String = """Rinne Mobile的分支版本 QwQ
开源地址:
https://github.com/Yukin0a/KireiBox"""

    fun coverSourceDescription(source: CoverSourceMode): String =
        when (source) {
            CoverSourceMode.NEXTMOE -> "Need to log in to NextMoe account"
            CoverSourceMode.VNDB -> "Public data, no login required"
            CoverSourceMode.BANGUMI -> "Anonymous search possible"
            CoverSourceMode.BANGUMI_MIRROR -> "Historical mirror, has stopped connecting to the Internet"
            CoverSourceMode.YMGAL -> "Historical data source, no longer connected to the Internet"
            CoverSourceMode.HIKARINAGI -> "Need to log in to Hikarinagi account"
        }

    /** Copy shared by the dashboard and settings UI; developer-log implementation details stay private. */
    fun dashboardAndLogLabels(): List<String> =
        listOf(
            "Play time",
            "Number of open games",
            "Last 7 days · Results",
            "Last 30 days · Results",
            "Diagnostic log",
            "Copy log",
        )
}
