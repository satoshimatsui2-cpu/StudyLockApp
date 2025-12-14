package com.example.studylockapp

import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.example.studylockapp.ads.AdAudioManager
import com.example.studylockapp.data.AppSettings
import com.example.studylockapp.ui.setup.TimeZoneSetupActivity

class AdminSettingsActivity : AppCompatActivity() {

    private lateinit var settings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_settings)

        settings = AppSettings(this)

        val textInterval = findViewById<TextView>(R.id.text_interval)
        val seekInterval = findViewById<SeekBar>(R.id.seek_interval)

        // ★追加：誤答リトライ / Lv1リトライ
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

        val btnToggleAdMute = findViewById<Button>(R.id.btn_toggle_ad_mute)
        val btnSave = findViewById<Button>(R.id.btn_save)

        val btn = findViewById<Button>(R.id.button_open_timezone_setup)
        btn.setOnClickListener {
            startActivity(Intent(this, TimeZoneSetupActivity::class.java))
        }

        fun intervalMsToProgress(ms: Long): Int {
            val clamped = ms.coerceIn(500L, 10_000L)
            return ((clamped - 500L) / 100L).toInt() // 0..95
        }

        fun progressToIntervalMs(progress: Int): Long {
            return 500L + (progress.coerceIn(0, 95) * 100L)
        }

        fun volToProgress(vol: Float) = (vol.coerceIn(0f, 1f) * 100f).toInt()
        fun progressToVol(p: Int) = (p.coerceIn(0, 100) / 100f)

        // ★追加：リトライ秒（10..600秒にしたい場合）
        fun secToProgress(sec: Long): Int {
            val clamped = sec.coerceIn(10L, 600L)
            return (clamped - 10L).toInt() // 0..590
        }

        fun progressToSec(progress: Int): Long {
            return (progress.coerceIn(0, 590) + 10).toLong() // 10..600
        }

        // 初期値反映
        seekInterval.progress = intervalMsToProgress(settings.answerIntervalMs)

        // ★追加：初期値反映
        seekWrongRetry.progress = secToProgress(settings.wrongRetrySec)
        seekLevel1Retry.progress = secToProgress(settings.level1RetrySec)

        seekSeCorrect.progress = volToProgress(settings.seCorrectVolume)
        seekSeWrong.progress = volToProgress(settings.seWrongVolume)
        seekTts.progress = volToProgress(settings.ttsVolume)
        seekAd.progress = volToProgress(settings.adVolume)

        fun refreshLabels() {
            val sec = progressToIntervalMs(seekInterval.progress) / 1000f
            textInterval.text = "回答間隔: %.1f 秒".format(sec)

            // ★追加：表示更新
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

        seekInterval.setOnSeekBarChangeListener(commonListener)

        // ★追加：リトライ秒のSeekBarも同じリスナーに
        seekWrongRetry.setOnSeekBarChangeListener(commonListener)
        seekLevel1Retry.setOnSeekBarChangeListener(commonListener)

        seekSeCorrect.setOnSeekBarChangeListener(commonListener)
        seekSeWrong.setOnSeekBarChangeListener(commonListener)
        seekTts.setOnSeekBarChangeListener(commonListener)
        seekAd.setOnSeekBarChangeListener(commonListener)

        btnToggleAdMute.setOnClickListener {
            settings.adMuted = !settings.adMuted
            refreshLabels()
            AdAudioManager.apply(settings) // Ads SDKがあれば即反映、なくても安全
        }

        btnSave.setOnClickListener {
            settings.answerIntervalMs = progressToIntervalMs(seekInterval.progress)

            // ★追加：保存
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