package com.example.studylockapp

import android.os.Bundle
import android.util.Log
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.studylockapp.data.AppSettings
import com.google.android.material.button.MaterialButton
import kotlin.math.roundToInt

class SoundSettingsActivity : AppCompatActivity() {

    private lateinit var settings: AppSettings

    private lateinit var textTtsSpeed: TextView
    private lateinit var seekTtsSpeed: SeekBar
    private lateinit var textTtsPitch: TextView
    private lateinit var seekTtsPitch: SeekBar

    private lateinit var textSeCorrect: TextView
    private lateinit var seekSeCorrect: SeekBar
    private lateinit var textSeWrong: TextView
    private lateinit var seekSeWrong: SeekBar
    private lateinit var textTtsVolume: TextView
    private lateinit var seekTtsVolume: SeekBar
    private lateinit var textAdVolume: TextView
    private lateinit var seekAdVolume: SeekBar
    private lateinit var btnToggleAdMute: MaterialButton
    private lateinit var btnSave: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            Log.d("SoundSettingsActivity", "onCreate start")
            setContentView(R.layout.activity_sound_settings)
            settings = AppSettings(this)

            textTtsSpeed = findViewById(R.id.text_tts_speed)
            seekTtsSpeed = findViewById(R.id.seek_tts_speed)
            textTtsPitch = findViewById(R.id.text_tts_pitch)
            seekTtsPitch = findViewById(R.id.seek_tts_pitch)

            textSeCorrect = findViewById(R.id.text_se_correct)
            seekSeCorrect = findViewById(R.id.seek_se_correct)
            textSeWrong = findViewById(R.id.text_se_wrong)
            seekSeWrong = findViewById(R.id.seek_se_wrong)
            textTtsVolume = findViewById(R.id.text_tts_volume)
            seekTtsVolume = findViewById(R.id.seek_tts_volume)
            textAdVolume = findViewById(R.id.text_ad_volume)
            seekAdVolume = findViewById(R.id.seek_ad_volume)
            btnToggleAdMute = findViewById(R.id.btn_toggle_ad_mute)
            btnSave = findViewById(R.id.btn_save_sound)

            // 初期値セット（内部は 0..1 なので 0..100 に変換）
            val speed = settings.getTtsSpeed()            // 0.5..1.5
            val pitch = settings.getTtsPitch()            // 0.5..1.5
            val seCorrect = (settings.seCorrectVolume * 100f).roundToInt().coerceIn(0, 100)
            val seWrong = (settings.seWrongVolume * 100f).roundToInt().coerceIn(0, 100)
            val ttsVol = (settings.ttsVolume * 100f).roundToInt().coerceIn(0, 100)
            val adVol = (settings.adVolume * 100f).roundToInt().coerceIn(0, 100)
            val adMute = settings.adMuted

            seekTtsSpeed.progress = ((speed - 0.5f) * 200f).roundToInt().coerceIn(0, 200)
            seekTtsPitch.progress = ((pitch - 0.5f) * 200f).roundToInt().coerceIn(0, 200)
            seekSeCorrect.progress = seCorrect
            seekSeWrong.progress = seWrong
            seekTtsVolume.progress = ttsVol
            seekAdVolume.progress = adVol
            btnToggleAdMute.text = if (adMute) "広告ミュート: ON" else "広告ミュート: OFF"

            updateLabels()

            // リスナ設定
            seekTtsSpeed.setOnSeekBarChangeListener(simpleSeekListener { p ->
                val v = 0.5f + (p / 200f)
                textTtsSpeed.text = "TTS再生スピード: %.2fx".format(v)
            })
            seekTtsPitch.setOnSeekBarChangeListener(simpleSeekListener { p ->
                val v = 0.5f + (p / 200f)
                textTtsPitch.text = "TTSピッチ: %.2f".format(v)
            })
            seekSeCorrect.setOnSeekBarChangeListener(simpleSeekListener { p ->
                textSeCorrect.text = "正解SE音量: ${p}%"
            })
            seekSeWrong.setOnSeekBarChangeListener(simpleSeekListener { p ->
                textSeWrong.text = "不正解SE音量: ${p}%"
            })
            seekTtsVolume.setOnSeekBarChangeListener(simpleSeekListener { p ->
                textTtsVolume.text = "TTS音量: ${p}%"
            })
            seekAdVolume.setOnSeekBarChangeListener(simpleSeekListener { p ->
                textAdVolume.text = "広告音量: ${p}%"
            })

            btnToggleAdMute.setOnClickListener {
                val newMute = !settings.adMuted
                settings.adMuted = newMute
                btnToggleAdMute.text = if (newMute) "広告ミュート: ON" else "広告ミュート: OFF"
            }

            btnSave.setOnClickListener {
                val speedVal = 0.5f + (seekTtsSpeed.progress / 200f)
                val pitchVal = 0.5f + (seekTtsPitch.progress / 200f)

                settings.setTtsSpeed(speedVal)
                settings.setTtsPitch(pitchVal)
                settings.seCorrectVolume = seekSeCorrect.progress / 100f
                settings.seWrongVolume = seekSeWrong.progress / 100f
                settings.ttsVolume = seekTtsVolume.progress / 100f
                settings.adVolume = seekAdVolume.progress / 100f
                // ミュートはトグル時に反映済み

                finish() // そのまま閉じる
            }

            Log.d("SoundSettingsActivity", "onCreate end OK")
        } catch (e: Exception) {
            Log.e("SoundSettingsActivity", "onCreate failed", e)
            Toast.makeText(this, "画面初期化に失敗: ${e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun updateLabels() {
        val speedVal = 0.5f + (seekTtsSpeed.progress / 200f)
        val pitchVal = 0.5f + (seekTtsPitch.progress / 200f)
        textTtsSpeed.text = "TTS再生スピード: %.2fx".format(speedVal)
        textTtsPitch.text = "TTSピッチ: %.2f".format(pitchVal)
        textSeCorrect.text = "正解SE音量: ${seekSeCorrect.progress}%"
        textSeWrong.text = "不正解SE音量: ${seekSeWrong.progress}%"
        textTtsVolume.text = "TTS音量: ${seekTtsVolume.progress}%"
        textAdVolume.text = "広告音量: ${seekAdVolume.progress}%"
    }

    private fun simpleSeekListener(onProgress: (Int) -> Unit) =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                onProgress(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
}