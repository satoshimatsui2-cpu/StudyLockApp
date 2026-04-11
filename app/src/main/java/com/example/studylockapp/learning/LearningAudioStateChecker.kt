package com.example.studylockapp.learning

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager

/**
 * デバイスの音声状態（音量、マナーモード、DND）をチェックするクラス。
 */
class LearningAudioStateChecker(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * 音が聞こえないリスク（音量0、マナーモード、おやすみモード等）がある場合に true を返す。
     */
    fun isSilenceRisk(): Boolean {
        // 1. メディア音量が0
        val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (volume == 0) return true

        // 2. マナーモード（サイレントまたはバイブレーション）
        if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) return true

        // 3. おやすみモード (DND) のチェック
        val filter = notificationManager.currentInterruptionFilter
        if (filter != NotificationManager.INTERRUPTION_FILTER_ALL) {
            return true
        }

        return false
    }
}
