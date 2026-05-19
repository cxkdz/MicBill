package com.billbook.app

import android.app.Application

class BillBookApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: BillBookApp
            private set
    }
}
