package com.example.studylockapp

import android.content.Intent
import android.os.Bundle
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.studylockapp.ads.AdAudioManager
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.ui.applock.AppLockSettingsActivity
import com.example.studylockapp.ui.setup.TimeZoneSetupActivity
import com.google.android.material.button.MaterialButton

class AdminSettingsActivity : AppCompatActivity() {

    private lateinit var settings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_settings)
        settings = AppSettings(this)

        // SeekBar / TextView 群
        val textInterval = findViewById<TextView>(R.id.text_interval)
        val seekInterval = findViewById<SeekBar>(R.id.seek_interval)

        val textWrongRetry = findViewById<TextView>(R.id.text_wrong_retry)
        val seekWrongRetry = findViewById<SeekBar>(R.id.seek_wrong_retry)

        val textLevel1Retry = findViewById<TextView>(R.id.text_level1_retry)
        val seekLevel1Retry = findViewById<SeekBar>(R.id.seek_level1_retry)

        val textSeCorrect = findViewById<TextView>(R.id.text_se_correct)
        val seekSeCorrect = findViewById<SeekBar>(R.id.seek_se_correct)

        val textSeWrong = findViewById<TextView>(R.id.text_se_wrong)
        val seekSeWrong = findViewById<SeekBar>(R.id.seek_se_wrong)

        val textTts = findViewById<TextView>(R.id.text_tts)
        val seekTts = findViewById<SeekBar>(R.id.seek_tts)

        val textAd = findViewById<TextView>(R.id.text_ad)
        val seekAd = findViewById<SeekBar>(R.id.seek_ad)

        val btnToggleAdMute = findViewById<MaterialButton>(R.id.btn_toggle_ad_mute)
        val btnSave = findViewById<MaterialButton>(R.id.btn_save)

        // タイムゾーン設定へ
        findViewById<MaterialButton>(R.id.button_open_timezone_setup)?.setOnClickListener {
            startActivity(Intent(this, TimeZoneSetupActivity::class.java))
        }

        // アプリロック設定へ
        findViewById<MaterialButton>(R.id.button_app_lock_settings)?.setOnClickListener {
            startActivity(Intent(this, AppLockSettingsActivity::class.java))
        }

        fun intervalMsToProgress(ms: Long): Int {
            val clamped = ms.coerceIn(500L, 10_000L)
            return ((clamped - 500L) / 100L).toInt() // 0..95
        }
        fun progressToIntervalMs(progress: Int): Long =
            500L + (progress.coerceIn(0, 95) * 100L)

        fun volToProgress(vol: Float) = (vol.coerceIn(0f, 1f) * 100f).toInt()
        fun progressToVol(p: Int) = (p.coerceIn(0, 100) / 100f)

        // リトライ秒（10..600）
        fun secToProgress(sec: Long): Int {
            val clamped = sec.coerceIn(10L, 600L)
            return (clamped - 10L).toInt() // 0..590
        }
        fun progressToSec(progress: Int): Long =
            (progress.coerceIn(0, 590) + 10).toLong() // 10..600

        // 初期値反映
        seekInterval.progress = intervalMsToProgress(settings.answerIntervalMs)
        seekWrongRetry.progress = secToProgress(settings.wrongRetrySec)
        seekLevel1Retry.progress = secToProgress(settings.level1RetrySec)
        seekSeCorrect.progress = volToProgress(settings.seCorrectVolume)
        seekSeWrong.progress = volToProgress(settings.seWrongVolume)
        seekTts.progress = volToProgress(settings.ttsVolume)
        seekAd.progress = volToProgress(settings.adVolume)

        fun refreshLabels() {
            val sec = progressToIntervalMs(seekInterval.progress) / 1000f
            textInterval.text = "回答間隔: %.1f 秒".format(sec)

            textWrongRetry.text = "誤答リトライ: ${progressToSec(seekWrongRetry.progress)} 秒"
            textLevel1Retry.text = "Lv1リトライ: ${progressToSec(seekLevel1Retry.progress)} 秒"

            textSeCorrect.text = "正解SE音量: ${seekSeCorrect.progress}%"
            textSeWrong.text = "不正解SE音量: ${seekSeWrong.progress}%"
            textTts.text = "単語発音(TTS)音量: ${seekTts.progress}%"
            textAd.text = "広告音量: ${seekAd.progress}%"

            btnToggleAdMute.text = "広告ミュート: " + if (settings.adMuted) "ON" else "OFF"
        }
        refreshLabels()

        val commonListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                refreshLabels()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        // リスナー共通設定
        listOf(
            seekInterval,
            seekWrongRetry,
            seekLevel1Retry,
            seekSeCorrect,
            seekSeWrong,
            seekTts,
            seekAd
        ).forEach { it.setOnSeekBarChangeListener(commonListener) }

        btnToggleAdMute.setOnClickListener {
            settings.adMuted = !settings.adMuted
            refreshLabels()
            AdAudioManager.apply(settings) // Ads SDK があれば即反映、なくても安全
        }

        btnSave.setOnClickListener {
            settings.answerIntervalMs = progressToIntervalMs(seekInterval.progress)
            settings.wrongRetrySec = progressToSec(seekWrongRetry.progress)
            settings.level1RetrySec = progressToSec(seekLevel1Retry.progress)

            settings.seCorrectVolume = progressToVol(seekSeCorrect.progress)
            settings.seWrongVolume = progressToVol(seekSeWrong.progress)
            settings.ttsVolume = progressToVol(seekTts.progress)
            settings.adVolume = progressToVol(seekAd.progress)

            AdAudioManager.apply(settings)
            finish()
        }
    }
}