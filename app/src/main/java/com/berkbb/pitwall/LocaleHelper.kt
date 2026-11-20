package com.berkbb.pitwall

import java.util.Locale

object LocaleHelper {

    val SUPPORTED_LANGUAGES = listOf("en", "tr", "ro")

    fun resolveInitialLanguage(savedLang: String?, systemLang: String?): String {
        if (!savedLang.isNullOrBlank() && savedLang in SUPPORTED_LANGUAGES) {
            return savedLang
        }
        val cleanSystem = systemLang?.lowercase(Locale.ROOT) ?: "en"
        return when (cleanSystem) {
            "tr" -> "tr"
            "ro" -> "ro"
            else -> "en" // English is the default fallback for all other languages
        }
    }

    fun getNextLanguage(currentLanguage: String): String {
        return when (currentLanguage) {
            "en" -> "tr"
            "tr" -> "ro"
            else -> "en"
        }
    }
}
