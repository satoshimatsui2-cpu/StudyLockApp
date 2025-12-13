package com.example.studylockapp

import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.studylockapp.ads.AdAudioManager
import com.example.studylockapp.data.AppSettings

class AdminSettingsActivity : AppCompatActivity() {

    private lateinit var settings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_settings)

        settings = AppSettings(this)

        val textInterval = findViewById<TextView>(R.id.text_interval)
        val seekInterval = findViewById<SeekBar>(R.id.seek_interval)

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

        fun intervalMsToProgress(ms: Long): Int {
            val clamped = ms.coerceIn(500L, 10_000L)
            return ((clamped - 500L) / 100L).toInt() // 0..95
        }

        fun progressToIntervalMs(progress: Int): Long {
            return 500L + (progress.coerceIn(0, 95) * 100L)
        }

        fun volToProgress(vol: Float) = (vol.coerceIn(0f, 1f) * 100f).toInt()
        fun progressToVol(p: Int) = (p.coerceIn(0, 100) / 100f)

        // 初期値反映
        seekInterval.progress = intervalMsToProgress(settings.answerIntervalMs)
        seekSeCorrect.progress = volToProgress(settings.seCorrectVolume)
        seekSeWrong.progress = volToProgress(settings.seWrongVolume)
        seekTts.progress = volToProgress(settings.ttsVolume)
        seekAd.progress = volToProgress(settings.adVolume)

        fun refreshLabels() {
            val sec = progressToIntervalMs(seekInterval.progress) / 1000f
            textInterval.text = "回答間隔: %.1f 秒".format(sec)

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
            settings.seCorrectVolume = progressToVol(seekSeCorrect.progress)
            settings.seWrongVolume = progressToVol(seekSeWrong.progress)
            settings.ttsVolume = progressToVol(seekTts.progress)
            settings.adVolume = progressToVol(seekAd.progress)

            AdAudioManager.apply(settings)
            finish()
        }
    }
}