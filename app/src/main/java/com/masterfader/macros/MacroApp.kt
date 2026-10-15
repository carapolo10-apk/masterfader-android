package com.masterfader.macros

import android.app.Application
import com.masterfader.macros.data.ConfigRepository

class MacroApp : Application() {
    lateinit var repository: ConfigRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = ConfigRepository(this)
    }
}
