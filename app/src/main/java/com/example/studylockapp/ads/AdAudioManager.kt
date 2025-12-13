package com.example.studylockapp.ads

import android.content.Context
import android.util.Log
import com.example.studylockapp.data.AppSettings

object AdAudioManager {

    fun apply(context: Context) {
        val settings = AppSettings(context)
        apply(settings)
    }

    fun apply(settings: AppSettings) {
        try {
            val clazz = Class.forName("com.google.android.gms.ads.MobileAds")
            val setAppVolume = clazz.getMethod("setAppVolume", Float::class.javaPrimitiveType)
            val setAppMuted = clazz.getMethod("setAppMuted", Boolean::class.javaPrimitiveType)

            setAppVolume.invoke(null, settings.adVolume)
            setAppMuted.invoke(null, settings.adMuted)

            Log.d("AdAudioManager", "Applied adVolume=${settings.adVolume}, adMuted=${settings.adMuted}")
        } catch (e: ClassNotFoundException) {
            // 広告SDK未導入なら何もしない（正常系）
            Log.d("AdAudioManager", "MobileAds not found. (Ads SDK not added yet)")
        } catch (t: Throwable) {
            Log.w("AdAudioManager", "Failed to apply ad audio", t)
        }
    }
}

