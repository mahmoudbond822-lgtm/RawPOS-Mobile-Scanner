package com.rawpos.scanner.navigation

import androidx.annotation.StringRes
import com.rawpos.scanner.R

enum class RawPosDestination(
    val route: String,
    @param:StringRes val titleRes: Int,
) {
    CONNECTION("connection", R.string.nav_connection),
    SCANNER("scanner", R.string.nav_scanner),
    SETTINGS("settings", R.string.nav_settings),
    DIAGNOSTICS("diagnostics", R.string.nav_diagnostics),
    ;

    companion object {
        fun fromRoute(route: String?): RawPosDestination =
            entries.firstOrNull { it.route == route } ?: CONNECTION
    }
}
