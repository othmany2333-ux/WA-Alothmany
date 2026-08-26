package com.alothmany.wa.core.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.alothmany.wa.core.model.AppLanguage

object AppLocaleController {
    fun apply(language: AppLanguage) {
        val tags = when (language) {
            AppLanguage.ARABIC -> "ar"
            AppLanguage.ENGLISH -> "en"
            AppLanguage.SYSTEM -> ""
        }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags))
    }
}
