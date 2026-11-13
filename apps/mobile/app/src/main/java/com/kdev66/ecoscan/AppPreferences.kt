package com.kdev66.ecoscan

import android.content.Context

/** Réglages simples de l'application, gardés sur le téléphone. */
class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("ecoscan", Context.MODE_PRIVATE)

    /** L'avertissement « des personnes peuvent apparaître sur la photo » a été lu (PRD § 6). */
    var privacyNoticeShown: Boolean
        get() = prefs.getBoolean(KEY_PRIVACY_NOTICE, false)
        set(value) = prefs.edit().putBoolean(KEY_PRIVACY_NOTICE, value).apply()

    private companion object {
        const val KEY_PRIVACY_NOTICE = "privacy_notice_shown"
    }
}
