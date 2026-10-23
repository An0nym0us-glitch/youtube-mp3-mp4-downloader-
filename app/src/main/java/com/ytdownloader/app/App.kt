package com.ytdownloader.app

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        Cookies.init(this)
        Notifications.createChannels(this)
        Engine.start(this)
    }
}
