package com.rawpos.scanner

import android.app.Application
import com.rawpos.scanner.core.AppContainer

class RawPosScannerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
