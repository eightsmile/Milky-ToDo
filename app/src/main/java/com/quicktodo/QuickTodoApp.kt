package com.quicktodo

import android.app.Application
import com.quicktodo.data.SettingsDataStore
import com.quicktodo.sync.ObsidianSyncer

class QuickTodoApp : Application() {

    lateinit var settingsDataStore: SettingsDataStore
        private set
    lateinit var obsidianSyncer: ObsidianSyncer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settingsDataStore = SettingsDataStore(this)
        obsidianSyncer = ObsidianSyncer(this)
    }

    companion object {
        lateinit var instance: QuickTodoApp
            private set
    }
}
