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
            setContentView(R.layout.activity_sound_settings)
            settings = AppSettings(this)

            // Viewの初期化
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

            // 初期値の設定
            setupInitialValues()

            // リスナーの設定
            setupListeners()

        } catch (e: Exception) {
            Log.e("SoundSettingsActivity", "onCreate failed", e)
            Toast.makeText(this, getString(R.string.sound_settings_init_failed, e.javaClass.simpleName), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setupInitialValues() {
        // TTS Speed (0.5-1.5) -> SeekBar (0-20)
        val speed = settings.getTtsSpeed()
        seekTtsSpeed.progress = ((speed - 0.5f) / 0.05f).roundToInt()

        // TTS Pitch (0.5-1.5) -> SeekBar (0-20)
        val pitch = settings.getTtsPitch()
        seekTtsPitch.progress = ((pitch - 0.5f) / 0.05f).roundToInt()

        // Volumes (0-100%) -> SeekBar (0-20)
        seekTtsVolume.progress = (settings.ttsVolume * 20f).roundToInt()
        seekSeCorrect.progress = (settings.seCorrectVolume * 20f).roundToInt()
        seekSeWrong.progress = (settings.seWrongVolume * 20f).roundToInt()
        seekAdVolume.progress = (settings.adVolume * 20f).roundToInt()

        btnToggleAdMute.text = if (settings.adMuted) getString(R.string.sound_settings_ad_mute_on) else getString(R.string.sound_settings_ad_mute_off)
        
        updateAllLabels()
    }

    private fun setupListeners() {
        seekTtsSpeed.setOnSeekBarChangeListener(simpleSeekListener { updateAllLabels() })
        seekTtsPitch.setOnSeekBarChangeListener(simpleSeekListener { updateAllLabels() })
        seekTtsVolume.setOnSeekBarChangeListener(simpleSeekListener { updateAllLabels() })
        seekSeCorrect.setOnSeekBarChangeListener(simpleSeekListener { updateAllLabels() })
        seekSeWrong.setOnSeekBarChangeListener(simpleSeekListener { updateAllLabels() })
        seekAdVolume.setOnSeekBarChangeListener(simpleSeekListener { updateAllLabels() })

        btnToggleAdMute.setOnClickListener {
            val newMute = !settings.adMuted
            settings.adMuted = newMute
            btnToggleAdMute.text = if (newMute) getString(R.string.sound_settings_ad_mute_on) else getString(R.string.sound_settings_ad_mute_off)
        }

        btnSave.setOnClickListener {
            // TTS Speed/Pitch
            settings.setTtsSpeed(0.5f + (seekTtsSpeed.progress * 0.05f))
            settings.setTtsPitch(0.5f + (seekTtsPitch.progress * 0.05f))

            // Volumes
            settings.ttsVolume = seekTtsVolume.progress * 0.05f
            settings.seCorrectVolume = seekSeCorrect.progress * 0.05f
            settings.seWrongVolume = seekSeWrong.progress * 0.05f
            settings.adVolume = seekAdVolume.progress * 0.05f

            finish()
        }
    }

    private fun updateAllLabels() {
        // TTS Speed/Pitch
        val speedVal = 0.5f + (seekTtsSpeed.progress * 0.05f)
        val pitchVal = 0.5f + (seekTtsPitch.progress * 0.05f)
        textTtsSpeed.text = getString(R.string.sound_settings_tts_speed, speedVal)
        textTtsPitch.text = getString(R.string.sound_settings_tts_pitch, pitchVal)

        // Volumes
        textTtsVolume.text = getString(R.string.sound_settings_tts_volume, seekTtsVolume.progress * 5)
        textSeCorrect.text = getString(R.string.sound_settings_se_correct, seekSeCorrect.progress * 5)
        textSeWrong.text = getString(R.string.sound_settings_se_wrong, seekSeWrong.progress * 5)
        textAdVolume.text = getString(R.string.sound_settings_ad_volume, seekAdVolume.progress * 5)
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