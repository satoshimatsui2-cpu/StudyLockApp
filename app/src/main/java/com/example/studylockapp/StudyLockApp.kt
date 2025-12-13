package com.example.studylockapp

import android.app.Application
import com.example.studylockapp.ads.AdAudioManager

class StudyLockApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AdAudioManager.apply(this)
    }
}