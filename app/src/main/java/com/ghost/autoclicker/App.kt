package com.ghost.autoclicker

import android.app.Application
import com.ghost.autoclicker.util.ClickLog

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ClickLog.init(this)
    }
}
