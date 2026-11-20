package com.rawpos.scanner.core.settings

enum class LanguageMode(val storageValue: String, val languageTag: String?) {
    SYSTEM("system", null),
    ARABIC("ar", "ar"),
    ENGLISH("en", "en"),
    ;

    companion object {
        fun fromStorage(value: String?): LanguageMode =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}
