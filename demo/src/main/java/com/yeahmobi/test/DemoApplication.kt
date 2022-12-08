package com.yeahmobi.test

import android.app.Application

class DemoApplication:Application() {

    override fun onCreate() {
        super.onCreate()

        Track.init(this)
    }
}