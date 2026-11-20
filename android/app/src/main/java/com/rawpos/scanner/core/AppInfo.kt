package com.rawpos.scanner.core

import android.content.Context
import com.rawpos.scanner.BuildConfig

data class AppInfo(
    val packageName: String,
    val versionName: String,
    val buildType: String,
    val minSdk: Int,
    val targetSdk: Int,
    val phase: String = "Phase 1 — Foundation",
) {
    companion object {
        fun from(context: Context): AppInfo {
            val applicationInfo = context.applicationInfo
            return AppInfo(
                packageName = context.packageName,
                versionName = BuildConfig.VERSION_NAME,
                buildType = BuildConfig.BUILD_TYPE,
                minSdk = applicationInfo.minSdkVersion,
                targetSdk = applicationInfo.targetSdkVersion,
            )
        }
    }
}
