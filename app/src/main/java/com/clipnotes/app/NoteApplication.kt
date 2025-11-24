package com.clipnotes.app

import android.app.Application
import com.clipnotes.app.data.AppDatabase
import com.clipnotes.app.data.NoteRepository
import com.clipnotes.app.utils.PreferenceManager

class NoteApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val preferenceManager: PreferenceManager by lazy { PreferenceManager(this) }
    val repository: NoteRepository by lazy { 
        NoteRepository(database.noteDao(), database.pairedDeviceDao()) 
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: NoteApplication
            private set
    }
}
