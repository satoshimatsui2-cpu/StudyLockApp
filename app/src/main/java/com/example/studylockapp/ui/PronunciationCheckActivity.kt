package com.example.studylockapp.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.studylockapp.R
import com.example.studylockapp.data.AppDatabase
import com.example.studylockapp.databinding.ActivityPronunciationCheckBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class PronunciationCheckActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityPronunciationCheckBinding
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private var wordId: Long = -1L
    private var wordText: String = ""
    private var wordMeaning: String = ""

    // 効果音再生用
    private lateinit var soundPool: SoundPool
    private var soundSuccess: Int = 0
    private var soundFailure: Int = 0

    enum class UIState {
        IDLE,       // 待機中
        RECORDING,  // 録音中
        SUCCESS,    // 判定OK
        FAILURE     // 判定NG
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startListening()
        } else {
            Toast.makeText(this, "録音権限が必要です", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPronunciationCheckBinding.inflate(layoutInflater)
        setContentView(binding.root)

        wordId = intent.getLongExtra("WORD_ID", -1L)
        wordText = intent.getStringExtra("WORD_TEXT") ?: ""
        wordMeaning = intent.getStringExtra("WORD_MEANING") ?: ""

        if (wordId == -1L || wordText.isBlank()) {
            Toast.makeText(this, "データが正しくありません", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.textWord.text = wordText
        binding.textMeaning.text = wordMeaning

        setupSoundPool()
        tts = TextToSpeech(this, this)

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "音声認識が利用できません", Toast.LENGTH_SHORT).show()
            binding.buttonStartPronounce.isEnabled = false
        } else {
            setupSpeechRecognizer()
        }

        binding.buttonListen.setOnClickListener {
            speakMaster()
        }

        binding.buttonStartPronounce.setOnClickListener {
            checkPermissionAndStart()
        }

        binding.buttonRetry.setOnClickListener {
            checkPermissionAndStart()
        }

        binding.buttonClose.setOnClickListener {
            finish()
        }

        updateUI(UIState.IDLE)
    }

    private fun setupSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(audioAttributes)
            .build()
        
        // res/raw にファイルがあることを想定
        soundSuccess = soundPool.load(this, R.raw.se_correct, 1)
        soundFailure = soundPool.load(this, R.raw.se_wrong, 1)
    }

    private fun playSound(isSuccess: Boolean) {
        val soundId = if (isSuccess) soundSuccess else soundFailure
        if (soundId != 0) {
            soundPool.play(soundId, 1f, 1f, 0, 0, 1f)
        }
    }

    private fun updateUI(state: UIState, recognizedText: String = "") {
        when (state) {
            UIState.IDLE -> {
                binding.layoutActionButtons.visibility = View.VISIBLE
                binding.buttonStartPronounce.isEnabled = true
                binding.buttonStartPronounce.text = "発音する"
                binding.buttonListen.isEnabled = ttsReady
                
                binding.textInstruction.visibility = View.VISIBLE
                binding.textInstruction.text = "ボタンを押して発音してください"
                binding.resultCard.visibility = View.GONE
            }
            UIState.RECORDING -> {
                binding.layoutActionButtons.visibility = View.VISIBLE
                binding.buttonStartPronounce.isEnabled = false
                binding.buttonStartPronounce.text = "聞き取り中..."
                binding.buttonListen.isEnabled = false
                
                binding.textInstruction.visibility = View.VISIBLE
                binding.textInstruction.text = "\"$wordText\" と発音してください"
                binding.resultCard.visibility = View.GONE
            }
            UIState.SUCCESS -> {
                binding.layoutActionButtons.visibility = View.GONE
                binding.textInstruction.visibility = View.GONE
                binding.resultCard.visibility = View.VISIBLE
                binding.textResultStatus.text = "✅ 発音できました！"
                binding.textResultStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                binding.textRecognizedValue.text = recognizedText
                binding.textResultMessage.text = "🎉 発音OKバッジを獲得しました"
                
                playSound(true)
            }
            UIState.FAILURE -> {
                binding.layoutActionButtons.visibility = View.GONE
                binding.textInstruction.visibility = View.GONE
                binding.resultCard.visibility = View.VISIBLE
                binding.textResultStatus.text = "😅 もう少し！"
                binding.textResultStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                binding.textRecognizedValue.text = if (recognizedText.isEmpty()) "(聞き取れませんでした)" else recognizedText
                binding.textResultMessage.text = "お手本を聞いて、もう一度チャレンジしてみよう"
                
                playSound(false)
            }
        }
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    updateUI(UIState.RECORDING)
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    val message = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "聞き取れませんでした"
                        SpeechRecognizer.ERROR_AUDIO -> "オーディオエラー"
                        SpeechRecognizer.ERROR_NETWORK -> "ネットワークエラー"
                        else -> "エラーが発生しました ($error)"
                    }
                    updateUI(UIState.FAILURE, message)
                    recordResult(false, 0f)
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                    
                    val matchedText = matches.find { checkPronunciation(it, wordText) }
                    val displayText = matchedText ?: matches.firstOrNull().orEmpty()
                    val isSuccess = matchedText != null
                    val confidence = confidences?.firstOrNull() ?: 0f
                    
                    if (isSuccess) {
                        updateUI(UIState.SUCCESS, displayText)
                    } else {
                        updateUI(UIState.FAILURE, displayText)
                    }
                    recordResult(isSuccess, confidence)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun checkPermissionAndStart() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
                startListening()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun startListening() {
        updateUI(UIState.RECORDING)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        }
        speechRecognizer?.startListening(intent)
    }

    private fun speakMaster() {
        tts?.speak(wordText, TextToSpeech.QUEUE_FLUSH, null, "master")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                ttsReady = true
                runOnUiThread {
                    if (binding.resultCard.visibility != View.VISIBLE) {
                        binding.buttonListen.isEnabled = true
                    }
                }
            }
        }
    }

    private fun checkPronunciation(input: String, target: String): Boolean {
        val normalizedInput = input.lowercase().replace(Regex("[^a-z ]"), "").trim()
        val normalizedTarget = target.lowercase().replace(Regex("[^a-z ]"), "").trim()
        
        if (normalizedInput == normalizedTarget) return true
        if (normalizedInput == "${normalizedTarget}s") return true
        if (normalizedInput == "${normalizedTarget}es") return true
        
        return false
    }

    private fun recordResult(isSuccess: Boolean, confidence: Float) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(this@PronunciationCheckActivity).voiceCheckDao()
                    .recordResult(wordId, isSuccess, confidence)
            }
        }
    }

    override fun onDestroy() {
        speechRecognizer?.apply {
            cancel()
            destroy()
        }
        tts?.apply {
            stop()
            shutdown()
        }
        if (::soundPool.isInitialized) {
            soundPool.release()
        }
        super.onDestroy()
    }
}
