package com.yuki.yukihub.settings

enum class HomePresentationMode(val storedValue: String, val displayName: String) {
    IMMERSIVE("immersive", "Immersive"),
    COMPONENTS("components", "Component pattern"),
    ;

    companion object {
        fun fromStored(value: String?): HomePresentationMode {
            if (value.isNullOrBlank()) return IMMERSIVE
            return when (value.lowercase().trim()) {
                "immersive" -> IMMERSIVE
                "components" -> COMPONENTS
                "immersive_cover", "focus_poster" -> IMMERSIVE
                else -> IMMERSIVE
            }
        }
    }
}
