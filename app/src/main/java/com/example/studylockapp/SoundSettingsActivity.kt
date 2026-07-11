package com.example.studylockapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.studylockapp.data.AppSettings
import com.google.android.material.button.MaterialButton
import kotlin.math.roundToInt

/**
 * サウンド設定画面のActivity
 * TTS（発音）の速度、ピッチ、音量、およびSEの音量を設定します。
 */
class SoundSettingsActivity : AppCompatActivity() {

    private lateinit var settings: AppSettings

    private lateinit var textTtsSpeed: TextView
    private lateinit var seekTtsSpeed: SeekBar
    private lateinit var textTtsPitch: TextView
    private lateinit var seekTtsPitch: SeekBar
    private lateinit var textTtsVolume: TextView
    private lateinit var seekTtsVolume: SeekBar

    private lateinit var textSeCorrect: TextView
    private lateinit var seekSeCorrect: SeekBar
    private lateinit var textSeWrong: TextView
    private lateinit var seekSeWrong: SeekBar

    private lateinit var resetTtsSpeed: ImageButton
    private lateinit var resetTtsPitch: ImageButton
    private lateinit var resetTtsVolume: ImageButton
    private lateinit var resetSeCorrect: ImageButton
    private lateinit var resetSeWrong: ImageButton

    private lateinit var textTargetCount: TextView
    private lateinit var btnTargetMinus: MaterialButton
    private lateinit var btnTargetPlus: MaterialButton
    private lateinit var textSimTotalQuestions: TextView
    private lateinit var textSimTotalTime: TextView

    private lateinit var btnSave: MaterialButton

    private val DEFAULT_SPEED = 1.0f
    private val DEFAULT_PITCH = 1.0f
    private val DEFAULT_VOL = 1.0f

    private fun speedToProgress(v: Float): Int = ((v - 0.5f) / 0.05f).roundToInt().coerceIn(0, 20)
    private fun pitchToProgress(v: Float): Int = ((v - 0.5f) / 0.05f).roundToInt().coerceIn(0, 20)
    private fun volToProgress(v: Float): Int = (v * 20f).roundToInt().coerceIn(0, 20)

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
            textTtsVolume = findViewById(R.id.text_tts_volume)
            seekTtsVolume = findViewById(R.id.seek_tts_volume)

            textSeCorrect = findViewById(R.id.text_se_correct)
            seekSeCorrect = findViewById(R.id.seek_se_correct)
            textSeWrong = findViewById(R.id.text_se_wrong)
            seekSeWrong = findViewById(R.id.seek_se_wrong)

            resetTtsSpeed = findViewById(R.id.reset_tts_speed)
            resetTtsPitch = findViewById(R.id.reset_tts_pitch)
            resetTtsVolume = findViewById(R.id.reset_tts_volume)
            resetSeCorrect = findViewById(R.id.reset_se_correct)
            resetSeWrong = findViewById(R.id.reset_se_wrong)

            textTargetCount = findViewById(R.id.text_target_count)
            btnTargetMinus = findViewById(R.id.btn_target_minus)
            btnTargetPlus = findViewById(R.id.btn_target_plus)
            textSimTotalQuestions = findViewById(R.id.text_sim_total_questions)
            textSimTotalTime = findViewById(R.id.text_sim_total_time)

            btnSave = findViewById(R.id.btn_save_sound)

            setupInitialValues()
            setupListeners()

        } catch (e: Exception) {
            Log.e("SoundSettingsActivity", "onCreate failed", e)
            Toast.makeText(
                this,
                getString(R.string.sound_settings_init_failed, e.javaClass.simpleName),
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    private fun setupInitialValues() {
        seekTtsSpeed.progress = speedToProgress(settings.getTtsSpeed())
        seekTtsPitch.progress = pitchToProgress(settings.getTtsPitch())
        seekTtsVolume.progress = volToProgress(settings.ttsVolume)
        seekSeCorrect.progress = volToProgress(settings.seCorrectVolume)
        seekSeWrong.progress = volToProgress(settings.seWrongVolume)

        textTargetCount.text = settings.dailyNewWordTarget.toString()

        updateAllLabels()
        setupCharacterSelection()
    }

    private fun setupCharacterSelection() {
        findViewById<MaterialButton>(R.id.btn_select_character).setOnClickListener {
            startActivity(Intent(this, com.example.studylockapp.ui.CharacterSelectActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btn_friend_connection).setOnClickListener {
            startActivity(Intent(this, com.example.studylockapp.ui.FriendConnectionActivity::class.java))
        }
    }

    private fun setupListeners() {
        val commonListener = simpleSeekListener { updateAllLabels() }
        seekTtsSpeed.setOnSeekBarChangeListener(commonListener)
        seekTtsPitch.setOnSeekBarChangeListener(commonListener)
        seekTtsVolume.setOnSeekBarChangeListener(commonListener)
        seekSeCorrect.setOnSeekBarChangeListener(commonListener)
        seekSeWrong.setOnSeekBarChangeListener(commonListener)

        btnTargetMinus.setOnClickListener {
            val current = textTargetCount.text.toString().toIntOrNull() ?: 5
            if (current > 1) {
                val nextValue = current - 1
                textTargetCount.text = nextValue.toString()
                updateSimulationLabels()
            }
        }
        btnTargetPlus.setOnClickListener {
            val current = textTargetCount.text.toString().toIntOrNull() ?: 5
            if (current < 100) {
                val nextValue = current + 1
                textTargetCount.text = nextValue.toString()
                updateSimulationLabels()
            }
        }

        resetTtsSpeed.setOnClickListener {
            seekTtsSpeed.progress = speedToProgress(DEFAULT_SPEED)
            updateAllLabels()
        }
        resetTtsPitch.setOnClickListener {
            seekTtsPitch.progress = pitchToProgress(DEFAULT_PITCH)
            updateAllLabels()
        }
        resetTtsVolume.setOnClickListener {
            seekTtsVolume.progress = volToProgress(DEFAULT_VOL)
            updateAllLabels()
        }
        resetSeCorrect.setOnClickListener {
            seekSeCorrect.progress = volToProgress(DEFAULT_VOL)
            updateAllLabels()
        }
        resetSeWrong.setOnClickListener {
            seekSeWrong.progress = volToProgress(DEFAULT_VOL)
            updateAllLabels()
        }

        btnSave.setOnClickListener {
            settings.setTtsSpeed(0.5f + (seekTtsSpeed.progress * 0.05f))
            settings.setTtsPitch(0.5f + (seekTtsPitch.progress * 0.05f))
            settings.ttsVolume = seekTtsVolume.progress * 0.05f
            settings.seCorrectVolume = seekSeCorrect.progress * 0.05f
            settings.seWrongVolume = seekSeWrong.progress * 0.05f
            settings.dailyNewWordTarget = textTargetCount.text.toString().toIntOrNull() ?: 5

            finish()
        }
    }

    private fun updateAllLabels() {
        val speedVal = 0.5f + (seekTtsSpeed.progress * 0.05f)
        val pitchVal = 0.5f + (seekTtsPitch.progress * 0.05f)
        textTtsSpeed.text = getString(R.string.sound_settings_tts_speed, speedVal)
        textTtsPitch.text = getString(R.string.sound_settings_tts_pitch, pitchVal)
        textTtsVolume.text = getString(R.string.sound_settings_tts_volume, seekTtsVolume.progress * 5)
        textSeCorrect.text = getString(R.string.sound_settings_se_correct, seekSeCorrect.progress * 5)
        textSeWrong.text = getString(R.string.sound_settings_se_wrong, seekSeWrong.progress * 5)
        updateSimulationLabels()
    }

    private fun updateSimulationLabels() {
        val newWords = textTargetCount.text.toString().toIntOrNull() ?: 5
        val totalQuestions = newWords * 15
        val totalTimeMinutes = (totalQuestions * 15) / 60

        textSimTotalQuestions.text = getString(R.string.target_sim_total_questions, totalQuestions)
        textSimTotalTime.text = getString(R.string.target_sim_total_time, totalTimeMinutes)
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
